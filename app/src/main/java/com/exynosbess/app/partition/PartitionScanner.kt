package com.exynosbess.app.partition

import com.exynosbess.app.boot.ShellExecutor

object PartitionScanner {

    data class ScanResult(
        val success: Boolean,
        val partitions: List<PartitionInfo>,
        val activeSlotSuffix: String?,
        val message: String
    )

    private data class Candidate(
        val name: String,
        val aliasPath: String,
        val resolvedPath: String
    )

    fun scan(): ScanResult {

        val activeSlot =
            detectActiveSlot()

        val command =
            """
            set +e

            scan_dir() {
                d="${'$'}1"

                [ -d "${'$'}d" ] || return 0

                for p in "${'$'}d"/*; do

                    [ -e "${'$'}p" ] || [ -L "${'$'}p" ] || continue

                    n="${'$'}{p##*/}"

                    lower="${'$'}(
                        printf '%s' "${'$'}n" |
                        tr '[:upper:]' '[:lower:]'
                    )"

                    case "${'$'}lower" in

                        boot|boot_a|boot_b|dtb|dtb_a|dtb_b|dtbo|dtbo_a|dtbo_b)

                            r="${'$'}(
                                readlink -f "${'$'}p" 2>/dev/null
                            )"

                            [ -n "${'$'}r" ] || r="${'$'}p"

                            printf '%s|%s|%s\n' \
                                "${'$'}n" \
                                "${'$'}p" \
                                "${'$'}r"
                            ;;
                    esac
                done
            }

            scan_dir /dev/block/by-name
            scan_dir /dev/block/bootdevice/by-name

            for d in /dev/block/platform/*/by-name; do
                scan_dir "${'$'}d"
            done

            for d in ${'$'}(
                find /dev/block \
                    -type d \
                    -name by-name \
                    2>/dev/null
            ); do
                scan_dir "${'$'}d"
            done

            for p in /dev/block/*; do

                [ -e "${'$'}p" ] || continue

                n="${'$'}{p##*/}"

                lower="${'$'}(
                    printf '%s' "${'$'}n" |
                    tr '[:upper:]' '[:lower:]'
                )"

                case "${'$'}lower" in

                    boot|boot_a|boot_b|dtb|dtb_a|dtb_b|dtbo|dtbo_a|dtbo_b)

                        r="${'$'}(
                            readlink -f "${'$'}p" 2>/dev/null
                        )"

                        [ -n "${'$'}r" ] || r="${'$'}p"

                        printf '%s|%s|%s\n' \
                            "${'$'}n" \
                            "${'$'}p" \
                            "${'$'}r"
                        ;;
                esac
            done
            """.trimIndent()

        val result =
            ShellExecutor.root(
                command
            )

        if (!result.success) {

            return ScanResult(
                success = false,
                partitions = emptyList(),
                activeSlotSuffix = activeSlot,
                message =
                    "Falha ao consultar partições.\n\n" +
                        result.output
            )
        }

        val candidates =
            result.output
                .lineSequence()
                .mapNotNull {
                    parseCandidate(it)
                }
                .toList()

        if (candidates.isEmpty()) {

            return ScanResult(
                success = true,
                partitions = emptyList(),
                activeSlotSuffix = activeSlot,
                message =
                    "Nenhuma partição boot, dtb ou dtbo encontrada."
            )
        }

        val groups =
            candidates.groupBy {
                it.resolvedPath
            }

        val partitions =
            groups.mapNotNull {
                (_, aliases) ->

                val preferred =
                    aliases.minByOrNull {
                        aliasPriority(
                            it.aliasPath
                        )
                    }
                        ?: return@mapNotNull null

                val type =
                    typeOf(
                        preferred.name
                    )
                        ?: return@mapNotNull null

                val slot =
                    slotOf(
                        preferred.name
                    )

                PartitionInfo(
                    name =
                        preferred.name,

                    type =
                        type,

                    aliasPath =
                        preferred.aliasPath,

                    resolvedPath =
                        preferred.resolvedPath,

                    sizeBytes =
                        readSize(
                            preferred.resolvedPath
                        ),

                    slotSuffix =
                        slot,

                    aliases =
                        aliases
                            .map {
                                it.aliasPath
                            }
                            .distinct()
                            .sorted()
                )
            }
                .sortedWith(
                    compareBy<PartitionInfo> {
                        when (it.type) {
                            PartitionType.BOOT -> 0
                            PartitionType.DTB -> 1
                            PartitionType.DTBO -> 2
                        }
                    }
                        .thenBy {
                            it.name.lowercase()
                        }
                )

        val counts =
            partitions
                .groupingBy {
                    it.type
                }
                .eachCount()

        return ScanResult(
            success = true,
            partitions = partitions,
            activeSlotSuffix = activeSlot,
            message =
                buildString {

                    append(
                        "${partitions.size} partição(ões) encontrada(s)"
                    )

                    append(
                        "\nBOOT: ${counts[PartitionType.BOOT] ?: 0}"
                    )

                    append(
                        "\nDTB: ${counts[PartitionType.DTB] ?: 0}"
                    )

                    append(
                        "\nDTBO: ${counts[PartitionType.DTBO] ?: 0}"
                    )

                    if (activeSlot != null) {

                        append(
                            "\nSlot ativo: $activeSlot"
                        )
                    }
                }
        )
    }


    private fun parseCandidate(
        line: String
    ): Candidate? {

        val parts =
            line.split(
                '|',
                limit = 3
            )

        if (parts.size != 3) {
            return null
        }

        val name =
            parts[0].trim()

        val alias =
            parts[1].trim()

        val resolved =
            parts[2].trim()

        if (
            name.isEmpty() ||
            alias.isEmpty() ||
            resolved.isEmpty()
        ) {
            return null
        }

        if (
            typeOf(name) == null
        ) {
            return null
        }

        return Candidate(
            name = name,
            aliasPath = alias,
            resolvedPath = resolved
        )
    }


    private fun typeOf(
        name: String
    ): PartitionType? {

        return when {

            name.matches(
                Regex(
                    """^boot(?:_[ab])?$""",
                    RegexOption.IGNORE_CASE
                )
            ) ->
                PartitionType.BOOT

            name.matches(
                Regex(
                    """^dtb(?:_[ab])?$""",
                    RegexOption.IGNORE_CASE
                )
            ) ->
                PartitionType.DTB

            name.matches(
                Regex(
                    """^dtbo(?:_[ab])?$""",
                    RegexOption.IGNORE_CASE
                )
            ) ->
                PartitionType.DTBO

            else ->
                null
        }
    }


    private fun slotOf(
        name: String
    ): String? {

        val lower =
            name.lowercase()

        return when {

            lower.endsWith("_a") ->
                "_a"

            lower.endsWith("_b") ->
                "_b"

            else ->
                null
        }
    }


    private fun detectActiveSlot():
        String? {

        val result =
            ShellExecutor.root(
                "getprop ro.boot.slot_suffix 2>/dev/null"
            )

        if (!result.success) {
            return null
        }

        val value =
            result.output.trim()

        return when (value) {

            "_a",
            "_b" ->
                value

            else ->
                null
        }
    }


    private fun readSize(
        path: String
    ): Long? {

        val quoted =
            quote(path)

        val command =
            """
            size="${'$'}(
                blockdev --getsize64 $quoted 2>/dev/null
            )"

            if [ -n "${'$'}size" ]; then
                echo "${'$'}size"
                exit 0
            fi

            b="${'$'}(
                basename $quoted
            )"

            sectors="${'$'}(
                cat "/sys/class/block/${'$'}b/size" 2>/dev/null
            )"

            if [ -n "${'$'}sectors" ]; then
                echo ${'$'}((sectors * 512))
            fi
            """.trimIndent()

        val result =
            ShellExecutor.root(
                command
            )

        if (!result.success) {
            return null
        }

        return result.output
            .lineSequence()
            .map {
                it.trim()
            }
            .firstOrNull {
                it.matches(
                    Regex("""\d+""")
                )
            }
            ?.toLongOrNull()
    }


    private fun aliasPriority(
        path: String
    ): Int {

        return when {

            path.startsWith(
                "/dev/block/by-name/"
            ) ->
                0

            path.startsWith(
                "/dev/block/bootdevice/by-name/"
            ) ->
                1

            "/by-name/" in path ->
                2

            else ->
                3
        }
    }


    private fun quote(
        value: String
    ): String {

        return "'" +
            value.replace(
                "'",
                "'\\''"
            ) +
            "'"
    }
}
