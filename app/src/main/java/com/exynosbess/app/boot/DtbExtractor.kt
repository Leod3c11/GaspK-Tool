package com.exynosbess.app.boot

import java.io.File
import java.io.RandomAccessFile

object DtbExtractor {

    data class ExtractedDtb(
        val index: Int,
        val sourceFile: String,
        val offset: Long,
        val size: Long,
        val outputPath: String
    )

    data class ExtractResult(
        val success: Boolean,
        val files: List<ExtractedDtb>,
        val message: String
    )

    fun extract(
        workspacePath: String,
        entries: List<DtbScanner.DtbEntry>
    ): ExtractResult {

        if (entries.isEmpty()) {
            return ExtractResult(
                false,
                emptyList(),
                "Nenhum DTB disponível para extração"
            )
        }

        val workspace = File(workspacePath)

        if (!workspace.exists()) {
            return ExtractResult(
                false,
                emptyList(),
                "Workspace não encontrado"
            )
        }

        val outputDir = File(
            workspace,
            "dtbs"
        )

        if (outputDir.exists()) {
            outputDir.deleteRecursively()
        }

        if (!outputDir.mkdirs()) {
            return ExtractResult(
                false,
                emptyList(),
                "Não foi possível criar o diretório dtbs"
            )
        }

        val extracted = mutableListOf<ExtractedDtb>()

        try {

            entries.forEachIndexed { index, entry ->

                val source = File(
                    workspace,
                    entry.sourceFile
                )

                if (!source.exists()) {
                    throw IllegalStateException(
                        "Arquivo ${entry.sourceFile} não encontrado"
                    )
                }

                if (
                    entry.offset < 0 ||
                    entry.size <= 0 ||
                    entry.offset + entry.size > source.length()
                ) {
                    throw IllegalStateException(
                        "Offset/tamanho inválido para DTB $index"
                    )
                }

                val output = File(
                    outputDir,
                    "dtb_$index.dtb"
                )

                RandomAccessFile(source, "r").use { input ->

                    output.outputStream().use { out ->

                        input.seek(entry.offset)

                        var remaining = entry.size

                        val buffer =
                            ByteArray(1024 * 1024)

                        while (remaining > 0) {

                            val wanted =
                                minOf(
                                    buffer.size.toLong(),
                                    remaining
                                ).toInt()

                            val read =
                                input.read(
                                    buffer,
                                    0,
                                    wanted
                                )

                            if (read <= 0) {
                                throw IllegalStateException(
                                    "Fim inesperado durante extração do DTB $index"
                                )
                            }

                            out.write(
                                buffer,
                                0,
                                read
                            )

                            remaining -= read
                        }
                    }
                }

                if (
                    !output.exists() ||
                    output.length() != entry.size
                ) {
                    throw IllegalStateException(
                        "Falha ao validar DTB $index"
                    )
                }

                /*
                 * Validação rápida do magic FDT:
                 * D0 0D FE ED
                 */
                RandomAccessFile(output, "r").use { check ->

                    val magic =
                        check.readInt()
                            .toLong() and 0xFFFFFFFFL

                    if (magic != 0xD00DFEEDL) {
                        throw IllegalStateException(
                            "DTB $index possui cabeçalho inválido"
                        )
                    }
                }

                extracted.add(
                    ExtractedDtb(
                        index = index,
                        sourceFile = entry.sourceFile,
                        offset = entry.offset,
                        size = entry.size,
                        outputPath = output.absolutePath
                    )
                )
            }

            /*
             * Persistir a origem de cada DTB.
             *
             * Formato:
             * index|sourceFile|offset|size
             */
            val sourceMap =
                File(
                    outputDir,
                    "dtb_sources.txt"
                )

            sourceMap.writeText(
                extracted.joinToString(
                    separator = "\n",
                    postfix = "\n"
                ) { dtb ->
                    "${dtb.index}|${dtb.sourceFile}|${dtb.offset}|${dtb.size}"
                }
            )

            return ExtractResult(
                true,
                extracted,
                "${extracted.size} DTB(s) extraído(s) com sucesso"
            )

        } catch (e: Exception) {

            return ExtractResult(
                false,
                extracted,
                e.message ?: "Erro desconhecido durante extração"
            )
        }
    }
}
