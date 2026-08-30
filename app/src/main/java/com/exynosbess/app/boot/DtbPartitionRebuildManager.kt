package com.exynosbess.app.boot

import android.content.Context
import com.exynosbess.app.tools.ToolManager
import java.io.File
import java.io.RandomAccessFile

object DtbPartitionRebuildManager {

    data class Result(
        val success: Boolean,
        val imagePath: String?,
        val message: String
    )


    fun rebuild(
        context: Context
    ): Result {

        try {

            val workspace =
                File(
                    context.filesDir,
                    "workspace"
                )

            val original =
                File(
                    workspace,
                    "dtb_partition"
                )

            if (!workspace.exists()) {
                return fail(
                    "Workspace não encontrado"
                )
            }

            if (!original.exists()) {
                return fail(
                    "Backup da partição DTB não encontrado"
                )
            }

            val sourceEntries =
                DtbSourceMap
                    .partitionEntries(
                        workspace.absolutePath
                    )
                    .sortedBy {
                        it.offset
                    }

            if (sourceEntries.isEmpty()) {
                return fail(
                    "Nenhum DTB pertence à partição DTB"
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
                return fail(
                    "Falha preparando dtc:\n${dtc.message}"
                )
            }

            val rebuiltDir =
                File(
                    workspace,
                    "rebuilt_partition_dtbs"
                )

            if (rebuiltDir.exists()) {
                rebuiltDir.deleteRecursively()
            }

            rebuiltDir.mkdirs()

            val replacements =
                mutableListOf<File>()

            /*
             * Recompilar os DTS que originalmente
             * vieram da partição DTB.
             */
            sourceEntries.forEach { entry ->

                val dts =
                    File(
                        workspace,
                        "dtbs/dtb_${entry.index}.dts"
                    )

                if (!dts.exists()) {
                    return fail(
                        "dtb_${entry.index}.dts não encontrado"
                    )
                }

                val rebuilt =
                    File(
                        rebuiltDir,
                        "dtb_${entry.index}.dtb"
                    )

                val command =
                    "'${dtc.path}' " +
                        "-I dts " +
                        "-O dtb " +
                        "-o '${rebuilt.absolutePath}' " +
                        "'${dts.absolutePath}'"

                val compile =
                    ShellExecutor.root(
                        command
                    )

                if (
                    !compile.success ||
                    !rebuilt.exists() ||
                    rebuilt.length() == 0L
                ) {
                    return fail(
                        "Falha recompilando DTB ${entry.index}:\n${compile.output}"
                    )
                }

                RandomAccessFile(
                    rebuilt,
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
                        return fail(
                            "DTB ${entry.index} recompilado possui magic inválido"
                        )
                    }
                }

                replacements.add(
                    rebuilt
                )
            }

            val outputDir =
                File(
                    context.filesDir,
                    "dtb_output"
                )

            if (!outputDir.exists()) {
                outputDir.mkdirs()
            }

            val modified =
                File(
                    outputDir,
                    "dtb_modified.img"
                )

            if (modified.exists()) {
                modified.delete()
            }

            /*
             * Reconstruir preservando TODOS os bytes
             * que não pertencem aos DTBs.
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

                        sourceEntries
                            .forEachIndexed {
                                replacementIndex,
                                entry ->

                                if (
                                    entry.offset <
                                    cursor
                                ) {
                                    return fail(
                                        "DTBs sobrepostos na partição"
                                    )
                                }

                                copyRegion(
                                    input,
                                    output,
                                    cursor,
                                    entry.offset -
                                        cursor
                                )

                                replacements[
                                    replacementIndex
                                ]
                                    .inputStream()
                                    .buffered()
                                    .use {
                                        replacementInput ->

                                        replacementInput
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
                return fail(
                    "Falha reconstruindo imagem DTB"
                )
            }

            /*
             * Não permitimos ultrapassar o tamanho
             * da partição original.
             */
            if (
                modified.length() >
                original.length()
            ) {
                modified.delete()

                return fail(
                    "Os novos DTBs ultrapassam o tamanho da partição DTB"
                )
            }

            return Result(
                true,
                modified.absolutePath,
                "Imagem DTB reconstruída"
            )

        } catch (
            e: Exception
        ) {

            return fail(
                e.message
                    ?: "Erro reconstruindo partição DTB"
            )
        }
    }


    private fun copyRegion(
        input: RandomAccessFile,
        output: java.io.OutputStream,
        offset: Long,
        length: Long
    ) {

        if (length <= 0)
            return

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
            remaining > 0
        ) {

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
                    "Fim inesperado ao reconstruir partição DTB"
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
            null,
            message
        )
    }
}
