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
            detectActiveSlotSuffix()

        val command =
            """
            for d in \
                /dev/block/by-name \
                /dev/block/bootdevice/by-name \
                /dev/block/platform/*/by-name
            do
                [ -d "${'$'}d" ] || continue

                for p in "${'$'}d"/*
                do
                    [ -e "${'$'}p" ] || continue

                    n="${'$'}{p##*/}"

                    case "${'$'}n" in
                        boot|boot_*|dtb|dtb_*|dtbo|dtbo_*)
                            r="${'$'}(readlink -f "${'$'}p" 2>/dev/null)"

                            if [ -z "${'$'}r" ]; then
                                r="${'$'}p"
                            fi

                            printf '%s|%s|%s\n' \
                                "${'$'}n" \
                                "${'$'}p" \
                                "${'$'}r"
                            ;;
                    esac
                done
            done
            """.trimIndent()

        val result =
            ShellExecutor.root(command)

        if (!result.success) {
            return ScanResult(
                success = false,
                partitions = emptyList(),
                activeSlotSuffix = activeSlot,
                message =
                    "Falha ao enumerar partições: " +
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

        /*
         * O mesmo dispositivo pode aparecer em:
         *
         * /dev/block/by-name
         * /dev/block/bootdevice/by-name
         * /dev/block/platform/.../by-name
         *
         * Portanto agrupamos pelo caminho real.
         */
        val groups =
            candidates.groupBy {
                it.resolvedPath
            }

        val partitions =
            groups.values
                .mapNotNull {
                    buildPartitionInfo(it)
                }
                .sortedWith(
                    compareBy<PartitionInfo>(
                        { typeOrder(it.type) },
                        { it.name }
                    )
                )

        return ScanResult(
            success = true,
            partitions = partitions,
            activeSlotSuffix = activeSlot,
            message =
                "${partitions.size} partição(ões) encontrada(s)."
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

        if (partitionType(name) == null) {
            return null
        }

        return Candidate(
            name = name,
            aliasPath = alias,
            resolvedPath = resolved
        )
    }

    private fun buildPartitionInfo(
        candidates: List<Candidate>
    ): PartitionInfo? {

        if (candidates.isEmpty()) {
            return null
        }

        val primary =
            candidates.minWithOrNull(
                compareBy<Candidate>(
                    { aliasPriority(it.aliasPath) },
                    { it.aliasPath.length }
                )
            ) ?: return null

        val type =
            partitionType(
                primary.name
            ) ?: return null

        return PartitionInfo(
            name = primary.name,
            type = type,
            aliasPath = primary.aliasPath,
            resolvedPath = primary.resolvedPath,
            sizeBytes =
                detectPartitionSize(
                    primary.resolvedPath
                ),
            slotSuffix =
                slotSuffix(
                    primary.name
                ),
            aliases =
                candidates
                    .map {
                        it.aliasPath
                    }
                    .distinct()
                    .sorted()
        )
    }

    private fun partitionType(
        name: String
    ): PartitionType? {

        return when (
            name.lowercase()
        ) {

            "boot" ->
                PartitionType.BOOT

            "dtb" ->
                PartitionType.DTB

            "dtbo" ->
                PartitionType.DTBO

            else -> {
                when {
                    name.lowercase().startsWith("boot_") ->
                        PartitionType.BOOT

                    name.lowercase().startsWith("dtbo_") ->
                        PartitionType.DTBO

                    name.lowercase().startsWith("dtb_") ->
                        PartitionType.DTB

                    else ->
                        null
                }
            }
        }
    }

    private fun slotSuffix(
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

    private fun detectActiveSlotSuffix():
        String? {

        val result =
            ShellExecutor.root(
                "getprop ro.boot.slot_suffix"
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

    private fun detectPartitionSize(
        resolvedPath: String
    ): Long? {

        val path =
            shellQuote(
                resolvedPath
            )

        /*
         * Primeiro tenta blockdev.
         */
        val blockdev =
            ShellExecutor.root(
                "blockdev --getsize64 $path 2>/dev/null"
            )

        if (blockdev.success) {

            blockdev.output
                .trim()
                .toLongOrNull()
                ?.let {
                    if (it > 0L) {
                        return it
                    }
                }
        }

        /*
         * Fallback via sysfs.
         *
         * /sys/class/block/<device>/size
         * informa número de setores de 512 bytes.
         */
        val baseName =
            resolvedPath
                .substringAfterLast('/')

        if (
            baseName.isEmpty() ||
            !baseName.matches(
                Regex("[A-Za-z0-9._-]+")
            )
        ) {
            return null
        }

        val sysfs =
            ShellExecutor.root(
                "cat /sys/class/block/$baseName/size 2>/dev/null"
            )

        if (!sysfs.success) {
            return null
        }

        val sectors =
            sysfs.output
                .trim()
                .toLongOrNull()
                ?: return null

        if (sectors <= 0L) {
            return null
        }

        return try {
            Math.multiplyExact(
                sectors,
                512L
            )
        } catch (_: ArithmeticException) {
            null
        }
    }

    private fun shellQuote(
        value: String
    ): String {

        return "'" +
            value.replace(
                "'",
                "'\"'\"'"
            ) +
            "'"
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

            else ->
                2
        }
    }

    private fun typeOrder(
        type: PartitionType
    ): Int {

        return when (type) {
            PartitionType.BOOT -> 0
            PartitionType.DTB -> 1
            PartitionType.DTBO -> 2
        }
    }
}
