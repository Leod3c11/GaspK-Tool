package com.exynosbess.app.partition

import java.io.File

object PartitionDtbCatalog {

    fun load(
        workspacePath: String
    ): List<PartitionDtbEntry> {

        val sourceMap =
            File(
                workspacePath,
                "dtbs/dtb_sources.txt"
            )

        if (!sourceMap.exists()) {
            return emptyList()
        }

        return try {

            sourceMap
                .readLines()
                .mapNotNull {
                    parseLine(it)
                }
                .sortedBy {
                    it.index
                }

        } catch (_: Exception) {

            emptyList()
        }
    }

    private fun parseLine(
        line: String
    ): PartitionDtbEntry? {

        val parts =
            line.split('|')

        if (parts.size != 4) {
            return null
        }

        val index =
            parts[0]
                .trim()
                .toIntOrNull()
                ?: return null

        val source =
            parts[1]
                .trim()

        val offset =
            parts[2]
                .trim()
                .toLongOrNull()
                ?: return null

        val size =
            parts[3]
                .trim()
                .toLongOrNull()
                ?: return null

        return PartitionDtbEntry(
            index = index,
            sourceFile = source,
            offset = offset,
            size = size
        )
    }
}
