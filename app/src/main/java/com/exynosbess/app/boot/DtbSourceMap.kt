package com.exynosbess.app.boot

import java.io.File

object DtbSourceMap {

    data class Entry(
        val index: Int,
        val sourceFile: String,
        val offset: Long,
        val size: Long
    ) {

        val isBootDtb: Boolean
            get() =
                sourceFile != "dtb_partition"

        val isSeparateDtbPartition: Boolean
            get() =
                sourceFile == "dtb_partition"
    }


    fun read(
        workspacePath: String
    ): List<Entry> {

        val file =
            File(
                workspacePath,
                "dtbs/dtb_sources.txt"
            )

        if (!file.exists())
            return emptyList()

        return file
            .readLines()
            .mapNotNull { line ->

                val parts =
                    line.split('|')

                if (parts.size != 4) {
                    return@mapNotNull null
                }

                val index =
                    parts[0].toIntOrNull()
                        ?: return@mapNotNull null

                val source =
                    parts[1]

                val offset =
                    parts[2].toLongOrNull()
                        ?: return@mapNotNull null

                val size =
                    parts[3].toLongOrNull()
                        ?: return@mapNotNull null

                Entry(
                    index = index,
                    sourceFile = source,
                    offset = offset,
                    size = size
                )
            }
            .sortedBy {
                it.index
            }
    }


    fun bootEntries(
        workspacePath: String
    ): List<Entry> {

        return read(workspacePath)
            .filter {
                it.isBootDtb
            }
    }


    fun partitionEntries(
        workspacePath: String
    ): List<Entry> {

        return read(workspacePath)
            .filter {
                it.isSeparateDtbPartition
            }
    }
}
