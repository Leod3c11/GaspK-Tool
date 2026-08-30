package com.exynosbess.app.boot

import java.io.File
import java.io.RandomAccessFile

object DtbScanner {

    data class DtbEntry(
        val sourceFile: String,
        val offset: Long,
        val size: Long
    )

    data class ScanResult(
        val success: Boolean,
        val entries: List<DtbEntry>,
        val message: String
    )

    private const val FDT_MAGIC = 0xD00DFEEDL

    fun scan(workspacePath: String): ScanResult {

        val workspace = File(workspacePath)

        if (!workspace.exists()) {
            return ScanResult(
                false,
                emptyList(),
                "Workspace não encontrado"
            )
        }

        val targets = listOf(
            File(workspace, "kernel"),
            File(workspace, "extra"),
            File(workspace, "dtb"),
            File(workspace, "kernel_dtb")
        ).filter { it.exists() && it.isFile }

        if (targets.isEmpty()) {
            return ScanResult(
                false,
                emptyList(),
                "Nenhum arquivo compatível encontrado"
            )
        }

        val entries = mutableListOf<DtbEntry>()

        for (file in targets) {
            scanFile(file, entries)
        }

        return if (entries.isNotEmpty()) {
            ScanResult(
                true,
                entries,
                "${entries.size} DTB(s) válido(s) encontrado(s)"
            )
        } else {
            ScanResult(
                false,
                emptyList(),
                "Nenhum cabeçalho DTB válido encontrado"
            )
        }
    }

    private fun scanFile(
        file: File,
        results: MutableList<DtbEntry>
    ) {

        RandomAccessFile(file, "r").use { raf ->

            val length = raf.length()

            if (length < 8) return

            var offset = 0L

            while (offset <= length - 8) {

                raf.seek(offset)

                val magic =
                    raf.readInt().toLong() and 0xFFFFFFFFL

                if (magic == FDT_MAGIC) {

                    val totalSize =
                        raf.readInt().toLong() and 0xFFFFFFFFL

                    if (
                        totalSize >= 40 &&
                        offset + totalSize <= length
                    ) {

                        results.add(
                            DtbEntry(
                                sourceFile = file.name,
                                offset = offset,
                                size = totalSize
                            )
                        )

                        offset += totalSize
                        continue
                    }
                }

                offset++
            }
        }
    }
}
