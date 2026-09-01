package com.exynosbess.app.partition

data class PartitionInfo(
    val name: String,
    val type: PartitionType,
    val aliasPath: String,
    val resolvedPath: String,
    val sizeBytes: Long?,
    val slotSuffix: String?,
    val aliases: List<String> = emptyList()
) {

    val sizeDisplay: String
        get() {
            val size = sizeBytes ?: return "Tamanho desconhecido"

            return when {
                size >= 1024L * 1024L * 1024L ->
                    "%.2f GB".format(
                        size.toDouble() /
                            (1024.0 * 1024.0 * 1024.0)
                    )

                size >= 1024L * 1024L ->
                    "%.2f MB".format(
                        size.toDouble() /
                            (1024.0 * 1024.0)
                    )

                size >= 1024L ->
                    "%.2f KB".format(
                        size.toDouble() / 1024.0
                    )

                else ->
                    "$size B"
            }
        }
}
