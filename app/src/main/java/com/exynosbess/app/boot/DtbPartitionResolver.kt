package com.exynosbess.app.boot

object DtbPartitionResolver {

    data class Result(
        val found: Boolean,
        val path: String?,
        val message: String
    )

    private val candidates =
        listOf(
            "/dev/block/by-name/dtb",
            "/dev/block/bootdevice/by-name/dtb",
            "/dev/block/platform/155a0000.ufs/by-name/dtb",
            "/dev/block/platform/11120000.ufs/by-name/dtb",
            "/dev/block/platform/13540000.dwmmc0/by-name/dtb"
        )

    fun detect(): Result {

        for (path in candidates) {

            val result =
                ShellExecutor.root(
                    "[ -e '$path' ] && readlink -f '$path'"
                )

            if (
                result.success &&
                result.output.isNotBlank()
            ) {
                return Result(
                    true,
                    result.output.lineSequence()
                        .first()
                        .trim(),
                    "Partição DTB encontrada"
                )
            }
        }

        val fallback =
            ShellExecutor.root(
                """
                find /dev/block \
                    -type l \
                    -iname dtb \
                    2>/dev/null \
                    | head -n 1
                """.trimIndent()
            )

        if (
            fallback.success &&
            fallback.output.isNotBlank()
        ) {

            val link =
                fallback.output
                    .lineSequence()
                    .first()
                    .trim()

            val resolved =
                ShellExecutor.root(
                    "readlink -f '$link'"
                )

            return Result(
                true,
                if (
                    resolved.success &&
                    resolved.output.isNotBlank()
                ) {
                    resolved.output.trim()
                } else {
                    link
                },
                "Partição DTB encontrada"
            )
        }

        return Result(
            false,
            null,
            "Partição DTB separada não encontrada"
        )
    }
}
