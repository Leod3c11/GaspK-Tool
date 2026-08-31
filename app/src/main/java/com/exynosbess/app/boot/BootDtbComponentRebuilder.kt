package com.exynosbess.app.boot

import android.content.Context
import com.exynosbess.app.tools.ToolManager
import java.io.File
import java.io.RandomAccessFile

object BootDtbComponentRebuilder {

    data class Result(
        val success: Boolean,
        val components: Map<String, File>,
        val message: String
    )

    private val supportedSources =
        setOf(
            "kernel",
            "extra",
            "dtb",
            "kernel_dtb"
        )

    fun rebuild(
        context: Context,
        workspace: File
    ): Result {

        try {

            val sourceEntries =
                DtbSourceMap
                    .bootEntries(
                        workspace.absolutePath
                    )

            if (sourceEntries.isEmpty()) {
                throw IllegalStateException(
                    "Nenhum DTB pertencente ao boot foi encontrado"
                )
            }

            val unsupported =
                sourceEntries
                    .map {
                        it.sourceFile
                    }
                    .distinct()
                    .filter {
                        it !in supportedSources
                    }

            if (unsupported.isNotEmpty()) {
                throw IllegalStateException(
                    "Origem DTB de boot não suportada: " +
                        unsupported.joinToString()
                )
            }

            val dtc =
                ToolManager.installDtc(
                    context
                )

            if (
                !dtc.success ||
                dtc.path == null
            ) {
                throw IllegalStateException(
                    "Falha preparando dtc:\n${dtc.message}"
                )
            }

            val outputDir =
                File(
                    workspace,
                    "rebuilt_boot_components"
                )

            if (outputDir.exists()) {
                outputDir.deleteRecursively()
            }

            outputDir.mkdirs()

            val result =
                mutableMapOf<String, File>()

            /*
             * Cada componente é reconstruído
             * independentemente.
             *
             * Exemplo:
             *
             * extra
             * dtb
             * kernel_dtb
             * kernel
             */
            sourceEntries
                .groupBy {
                    it.sourceFile
                }
                .forEach {
                    sourceName,
                    unsortedEntries ->

                    val original =
                        File(
                            workspace,
                            sourceName
                        )

                    if (
                        !original.exists() ||
                        original.length() == 0L
                    ) {
                        throw IllegalStateException(
                            "Componente $sourceName não encontrado"
                        )
                    }

                    val entries =
                        unsortedEntries
                            .sortedBy {
                                it.offset
                            }

                    /*
                     * Validar que os DTBs originais
                     * não se sobrepõem.
                     */
                    var lastEnd = 0L

                    entries.forEach { entry ->

                        if (
                            entry.offset < lastEnd ||
                            entry.offset < 0 ||
                            entry.size <= 0 ||
                            entry.offset +
                                entry.size >
                                original.length()
                        ) {
                            throw IllegalStateException(
                                "Mapa DTB inválido em $sourceName"
                            )
                        }

                        lastEnd =
                            entry.offset +
                                entry.size
                    }

                    val rebuiltDtbs =
                        mutableListOf<File>()

                    entries.forEach { entry ->

                        val dts =
                            File(
                                workspace,
                                "dtbs/dtb_${entry.index}.dts"
                            )

                        if (!dts.exists()) {
                            throw IllegalStateException(
                                "dtb_${entry.index}.dts não encontrado"
                            )
                        }

                        val dtb =
                            File(
                                outputDir,
                                "${sourceName}_dtb_${entry.index}.dtb"
                            )

                        val command =
                            "'${dtc.path}' " +
                                "-I dts " +
                                "-O dtb " +
                                "-o '${dtb.absolutePath}' " +
                                "'${dts.absolutePath}'"

                        val compile =
                            ShellExecutor.root(
                                command
                            )

                        if (
                            !compile.success ||
                            !dtb.exists() ||
                            dtb.length() == 0L
                        ) {
                            throw IllegalStateException(
                                "Falha recompilando dtb_${entry.index} " +
                                    "de $sourceName:\n${compile.output}"
                            )
                        }

                        RandomAccessFile(
                            dtb,
                            "r"
                        ).use { raf ->

                            val magic =
                                raf.readInt()
                                    .toLong() and
                                    0xFFFFFFFFL

                            if (
                                magic !=
                                0xD00DFEEDL
                            ) {
                                throw IllegalStateException(
                                    "dtb_${entry.index} possui magic inválido"
                                )
                            }
                        }

                        rebuiltDtbs.add(
                            dtb
                        )
                    }

                    val modified =
                        File(
                            outputDir,
                            sourceName
                        )

                    if (modified.exists()) {
                        modified.delete()
                    }

                    /*
                     * Reconstrução equivalente à antiga
                     * reconstrução do EXTRA:
                     *
                     * preserva todos os bytes antes,
                     * entre e depois dos DTBs.
                     *
                     * O DTB pode mudar de tamanho.
                     */
                    RandomAccessFile(
                        original,
                        "r"
                    ).use { input ->

                        modified
                            .outputStream()
                            .buffered()
                            .use { output ->

                                var cursor = 0L

                                entries
                                    .forEachIndexed {
                                        localIndex,
                                        entry ->

                                        copyRegion(
                                            input,
                                            output,
                                            cursor,
                                            entry.offset -
                                                cursor
                                        )

                                        rebuiltDtbs[
                                            localIndex
                                        ]
                                            .inputStream()
                                            .buffered()
                                            .use {
                                                replacement ->

                                                replacement
                                                    .copyTo(
                                                        output
                                                    )
                                            }

                                        cursor =
                                            entry.offset +
                                                entry.size
                                    }

                                if (
                                    cursor <
                                    original.length()
                                ) {
                                    copyRegion(
                                        input,
                                        output,
                                        cursor,
                                        original.length() -
                                            cursor
                                    )
                                }
                            }
                    }

                    if (
                        !modified.exists() ||
                        modified.length() == 0L
                    ) {
                        throw IllegalStateException(
                            "Falha reconstruindo $sourceName"
                        )
                    }

                    result[
                        sourceName
                    ] = modified
                }

            return Result(
                true,
                result,
                "${result.size} componente(s) DTB reconstruído(s)"
            )

        } catch (
            e: Exception
        ) {

            return Result(
                false,
                emptyMap(),
                e.message
                    ?: "Erro reconstruindo componentes DTB"
            )
        }
    }

    private fun copyRegion(
        input: RandomAccessFile,
        output: java.io.OutputStream,
        offset: Long,
        length: Long
    ) {

        if (length <= 0L) {
            return
        }

        input.seek(
            offset
        )

        var remaining =
            length

        val buffer =
            ByteArray(
                1024 * 1024
            )

        while (
            remaining > 0L
        ) {

            val wanted =
                minOf(
                    remaining,
                    buffer.size.toLong()
                ).toInt()

            val read =
                input.read(
                    buffer,
                    0,
                    wanted
                )

            if (read <= 0) {
                throw IllegalStateException(
                    "Fim inesperado reconstruindo componente"
                )
            }

            output.write(
                buffer,
                0,
                read
            )

            remaining -=
                read
        }
    }

    private fun fail(
        message: String
    ): Result {

        return Result(
            false,
            emptyMap(),
            message
        )
    }
}
