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

    private const val BUFFER_SIZE = 1024 * 1024

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
            File(workspace, "kernel_dtb"),
            File(workspace, "dtb_partition")
        ).filter {
            it.exists() &&
            it.isFile &&
            it.length() >= 8
        }

        if (targets.isEmpty()) {
            return ScanResult(
                false,
                emptyList(),
                "Nenhum arquivo para analisar"
            )
        }

        val results = mutableListOf<DtbEntry>()

        targets.forEach { file ->
            scanFile(file, results)
        }

        return if (results.isNotEmpty()) {
            ScanResult(
                true,
                results,
                "${results.size} DTB(s) encontrado(s)"
            )
        } else {
            ScanResult(
                false,
                emptyList(),
                "Nenhum DTB encontrado em kernel/extra/dtb/dtb_partition"
            )
        }
    }

    private fun scanFile(
        file: File,
        results: MutableList<DtbEntry>
    ) {

        RandomAccessFile(file, "r").use { raf ->

            val fileLength = raf.length()

            val buffer = ByteArray(
                BUFFER_SIZE + 7
            )

            var absoluteOffset = 0L
            var carry = 0

            while (absoluteOffset < fileLength) {

                raf.seek(absoluteOffset)

                val wanted =
                    minOf(
                        BUFFER_SIZE,
                        (fileLength - absoluteOffset).toInt()
                    )

                val read =
                    raf.read(
                        buffer,
                        carry,
                        wanted
                    )

                if (read <= 0) {
                    break
                }

                val available =
                    carry + read

                var i = 0

                while (i <= available - 8) {

                    if (
                        (buffer[i].toInt() and 0xFF) == 0xD0 &&
                        (buffer[i + 1].toInt() and 0xFF) == 0x0D &&
                        (buffer[i + 2].toInt() and 0xFF) == 0xFE &&
                        (buffer[i + 3].toInt() and 0xFF) == 0xED
                    ) {

                        val totalSize =
                            ((buffer[i + 4].toLong() and 0xFF) shl 24) or
                            ((buffer[i + 5].toLong() and 0xFF) shl 16) or
                            ((buffer[i + 6].toLong() and 0xFF) shl 8) or
                            (buffer[i + 7].toLong() and 0xFF)

                        val realOffset =
                            absoluteOffset -
                            carry +
                            i

                        if (
                            totalSize >= 40 &&
                            realOffset >= 0 &&
                            realOffset + totalSize <= fileLength
                        ) {

                            results.add(
                                DtbEntry(
                                    sourceFile = file.name,
                                    offset = realOffset,
                                    size = totalSize
                                )
                            )
                        }
                    }

                    i++
                }

                /*
                 * Preserva os últimos 7 bytes para detectar
                 * um cabeçalho que atravesse dois buffers.
                 */
                carry = minOf(
                    7,
                    available
                )

                System.arraycopy(
                    buffer,
                    available - carry,
                    buffer,
                    0,
                    carry
                )

                absoluteOffset += read
            }
        }
    }
}
