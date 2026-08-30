package com.exynosbess.app.boot

object BootPartitionResolver {

    data class BootPartition(
        val found: Boolean,
        val path: String?,
        val details: String
    )

    private val candidates = listOf(
        "/dev/block/by-name/boot",
        "/dev/block/by-name/BOOT",
        "/dev/block/bootdevice/by-name/boot",
        "/dev/block/bootdevice/by-name/BOOT"
    )

    fun detect(): BootPartition {

        for (candidate in candidates) {

            val check = ShellExecutor.root(
                "if [ -e '$candidate' ]; then readlink -f '$candidate'; fi"
            )

            if (check.success && check.output.isNotBlank()) {

                return BootPartition(
                    found = true,
                    path = candidate,
                    details = check.output
                )
            }
        }

        // fallback: procurar em árvores platform
        val fallback = ShellExecutor.root(
            "find /dev/block -type l \\( -name boot -o -name BOOT \\) 2>/dev/null | head -n 20"
        )

        if (fallback.success && fallback.output.isNotBlank()) {

            val first = fallback.output
                .lineSequence()
                .firstOrNull()
                ?.trim()

            if (!first.isNullOrBlank()) {

                val real = ShellExecutor.root(
                    "readlink -f '$first'"
                )

                return BootPartition(
                    found = true,
                    path = first,
                    details = if (real.output.isNotBlank()) real.output else first
                )
            }
        }

        return BootPartition(
            found = false,
            path = null,
            details = "Partição boot não encontrada"
        )
    }
}
