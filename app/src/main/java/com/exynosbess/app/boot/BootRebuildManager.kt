package com.exynosbess.app.boot

import android.content.Context
import android.os.Process
import com.exynosbess.app.tools.ToolManager
import java.io.File
import java.io.RandomAccessFile

object BootRebuildManager {

    data class Result(
        val success: Boolean,
        val bootPath: String?,
        val message: String
    )

    fun rebuild(context: Context): Result {

        try {

            val workspace =
                File(context.filesDir, "workspace")

            val originalBoot =
                File(
                    context.filesDir,
                    "boot_backups/boot_original.img"
                )

            if (!workspace.exists()) {
                return fail("Workspace não encontrado")
            }

            if (!originalBoot.exists()) {
                return fail("boot_original.img não encontrado")
            }

            val originalExtra =
                File(workspace, "extra")

            if (!originalExtra.exists()) {
                return fail("Arquivo extra não encontrado")
            }

            /*
             * 1. Localizar novamente os DTBs dentro
             *    do extra ORIGINAL.
             */
            val scan =
                DtbScanner.scan(
                    workspace.absolutePath
                )

            if (!scan.success) {
                return fail(
                    "Falha ao localizar DTBs:\n${scan.message}"
                )
            }

            val extraEntries =
                scan.entries
                    .filter {
                        it.sourceFile == "extra"
                    }
                    .sortedBy {
                        it.offset
                    }

            if (extraEntries.isEmpty()) {
                return fail(
                    "Nenhum DTB localizado no extra"
                )
            }

            /*
             * Neste estágio do ExynosBess todos os
             * DTBs encontrados devem estar no extra.
             */
            if (extraEntries.size != scan.entries.size) {
                return fail(
                    "Foram encontrados DTBs fora do extra. " +
                    "Rebuild cancelado por segurança."
                )
            }

            /*
             * 2. Preparar dtc.
             */
            val dtc =
                ToolManager.installDtc(context)

            if (!dtc.success || dtc.path == null) {
                return fail(
                    "Falha ao preparar dtc:\n${dtc.message}"
                )
            }

            val rebuiltDtbDir =
                File(
                    workspace,
                    "rebuilt_dtbs"
                )

            if (rebuiltDtbDir.exists()) {
                rebuiltDtbDir.deleteRecursively()
            }

            rebuiltDtbDir.mkdirs()

            val replacements =
                mutableListOf<File>()

            /*
             * 3. Recompilar cada DTS atual.
             */
            extraEntries.forEachIndexed { index, _ ->

                val dts =
                    File(
                        workspace,
                        "dtbs/dtb_${index}.dts"
                    )

                if (!dts.exists()) {
                    return fail(
                        "dtb_${index}.dts não encontrado"
                    )
                }

                val rebuilt =
                    File(
                        rebuiltDtbDir,
                        "dtb_${index}.dtb"
                    )

                val command =
                    "'${dtc.path}' " +
                    "-I dts " +
                    "-O dtb " +
                    "-o '${rebuilt.absolutePath}' " +
                    "'${dts.absolutePath}'"

                val compile =
                    ShellExecutor.root(command)

                if (
                    !compile.success ||
                    !rebuilt.exists() ||
                    rebuilt.length() == 0L
                ) {

                    return fail(
                        "Falha recompilando DTS $index:\n\n" +
                        compile.output
                    )
                }

                /*
                 * Validação do magic.
                 */
                RandomAccessFile(
                    rebuilt,
                    "r"
                ).use { raf ->

                    val magic =
                        raf.readInt()
                            .toLong() and 0xFFFFFFFFL

                    if (magic != 0xD00DFEEDL) {
                        return fail(
                            "DTB recompilado $index possui magic inválido"
                        )
                    }
                }

                replacements.add(rebuilt)
            }

            /*
             * 4. Reconstruir EXTRA.
             *
             * IMPORTANTE:
             * Não fazemos simplesmente:
             *
             * dtb0 + dtb1
             *
             * Copiamos absolutamente todos os bytes
             * originais existentes antes, entre e
             * depois dos DTBs.
             */
            val modifiedExtra =
                File(
                    workspace,
                    "extra_modified"
                )

            if (modifiedExtra.exists()) {
                modifiedExtra.delete()
            }

            RandomAccessFile(
                originalExtra,
                "r"
            ).use { input ->

                modifiedExtra
                    .outputStream()
                    .buffered()
                    .use { output ->

                        var cursor = 0L

                        extraEntries.forEachIndexed {
                            index,
                            entry ->

                            /*
                             * Copiar região original que
                             * antecede este DTB.
                             */
                            copyRegion(
                                input,
                                output,
                                cursor,
                                entry.offset - cursor
                            )

                            /*
                             * Inserir novo DTB.
                             */
                            replacements[index]
                                .inputStream()
                                .buffered()
                                .use {
                                    replacementInput ->

                                    replacementInput
                                        .copyTo(output)
                                }

                            /*
                             * Pular o DTB antigo.
                             */
                            cursor =
                                entry.offset +
                                entry.size
                        }

                        /*
                         * Preservar todo o restante
                         * do EXTRA original.
                         */
                        if (
                            cursor <
                            originalExtra.length()
                        ) {

                            copyRegion(
                                input,
                                output,
                                cursor,
                                originalExtra.length() -
                                    cursor
                            )
                        }
                    }
            }

            if (
                !modifiedExtra.exists() ||
                modifiedExtra.length() == 0L
            ) {
                return fail(
                    "Falha reconstruindo extra"
                )
            }

            /*
             * 5. Criar diretório LIMPO exclusivamente
             *    para o magiskboot repack.
             */
            val repackDir =
                File(
                    context.filesDir,
                    "repack_workspace"
                )

            if (repackDir.exists()) {
                repackDir.deleteRecursively()
            }

            repackDir.mkdirs()

            /*
             * Componentes reconhecidos pelo magiskboot.
             */
            val components =
                listOf(
                    "kernel",
                    "ramdisk.cpio",
                    "second",
                    "dtb",
                    "kernel_dtb",
                    "recovery_dtbo",
                    "header"
                )

            components.forEach { name ->

                val source =
                    File(
                        workspace,
                        name
                    )

                if (source.exists()) {

                    source.copyTo(
                        File(
                            repackDir,
                            name
                        ),
                        overwrite = true
                    )
                }
            }

            /*
             * Usar EXTRA modificado no lugar do original.
             */
            modifiedExtra.copyTo(
                File(
                    repackDir,
                    "extra"
                ),
                overwrite = true
            )

            /*
             * 6. Preparar magiskboot.
             */
            val magiskboot =
                ToolManager.installMagiskBoot(
                    context
                )

            if (
                !magiskboot.success ||
                magiskboot.path == null
            ) {

                return fail(
                    "Falha preparando magiskboot:\n" +
                    magiskboot.message
                )
            }

            val outputDir =
                File(
                    context.filesDir,
                    "boot_output"
                )

            if (!outputDir.exists()) {
                outputDir.mkdirs()
            }

            val modifiedBoot =
                File(
                    outputDir,
                    "boot_modified.img"
                )

            if (modifiedBoot.exists()) {
                modifiedBoot.delete()
            }

            /*
             * 7. Repack.
             */
            val repackCommand =
                "cd '${repackDir.absolutePath}' && " +
                "'${magiskboot.path}' repack " +
                "'${originalBoot.absolutePath}' " +
                "'${modifiedBoot.absolutePath}'"

            val repack =
                ShellExecutor.root(
                    repackCommand
                )

            if (
                !repack.success ||
                !modifiedBoot.exists() ||
                modifiedBoot.length() == 0L
            ) {

                return fail(
                    "Falha no magiskboot repack:\n\n" +
                    repack.output
                )
            }

            /*
             * Arquivo foi criado como root.
             * Entregar propriedade ao aplicativo.
             */
            val uid =
                Process.myUid()

            val permissions =
                ShellExecutor.root(
                    "chown $uid:$uid '${modifiedBoot.absolutePath}' && " +
                    "chmod 600 '${modifiedBoot.absolutePath}'"
                )

            if (!permissions.success) {
                return fail(
                    "Boot criado, mas falhou ajuste de permissões:\n" +
                    permissions.output
                )
            }

            /*
             * 8. Teste final:
             *
             * tentar desempacotar o boot reconstruído.
             * Isso NÃO faz flash.
             */
            val verifyDir =
                File(
                    context.filesDir,
                    "verify_boot"
                )

            ShellExecutor.root(
                "rm -rf '${verifyDir.absolutePath}' && " +
                "mkdir -p '${verifyDir.absolutePath}'"
            )

            val verify =
                ShellExecutor.root(
                    "cd '${verifyDir.absolutePath}' && " +
                    "'${magiskboot.path}' unpack " +
                    "'${modifiedBoot.absolutePath}'"
                )

            if (!verify.success) {

                return fail(
                    "boot_modified.img foi criado, " +
                    "mas falhou na verificação:\n\n" +
                    verify.output
                )
            }

            return Result(
                true,
                modifiedBoot.absolutePath,
                "boot_modified.img criado e " +
                "descompactado novamente com sucesso.\n\n" +
                "DTBs substituídos: ${extraEntries.size}\n" +
                "Extra original: ${originalExtra.length()} bytes\n" +
                "Extra modificado: ${modifiedExtra.length()} bytes\n" +
                "Boot original: ${originalBoot.length()} bytes\n" +
                "Boot modificado: ${modifiedBoot.length()} bytes\n\n" +
                "magiskboot:\n${repack.output}"
            )

        } catch (e: Exception) {

            return fail(
                e.stackTraceToString()
            )
        }
    }

    private fun copyRegion(
        input: RandomAccessFile,
        output: java.io.OutputStream,
        offset: Long,
        length: Long
    ) {

        if (length <= 0) {
            return
        }

        input.seek(offset)

        var remaining =
            length

        val buffer =
            ByteArray(
                1024 * 1024
            )

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
                    "Fim inesperado ao reconstruir extra"
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
