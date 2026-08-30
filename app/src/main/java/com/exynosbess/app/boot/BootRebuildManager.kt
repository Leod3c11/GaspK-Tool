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

            /*
             * Reconstruir dinamicamente todos os componentes
             * do boot que realmente possuem DTBs.
             *
             * Nenhum componente é obrigatório:
             *
             * kernel
             * extra
             * dtb
             * kernel_dtb
             */
            val componentRebuild =
                BootDtbComponentRebuilder.rebuild(
                    context,
                    workspace
                )

            if (!componentRebuild.success) {
                return fail(
                    "Falha reconstruindo Device Trees do boot:\n" +
                        componentRebuild.message
                )
            }

            val rebuiltComponents =
                componentRebuild.components

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
             *
             * EXTRA agora faz parte da lista normal.
             */
            val components =
                listOf(
                    "kernel",
                    "ramdisk.cpio",
                    "second",
                    "dtb",
                    "kernel_dtb",
                    "extra",
                    "recovery_dtbo",
                    "header"
                )

            components.forEach { name ->

                /*
                 * Se este componente possuía DTBs,
                 * usamos a versão reconstruída.
                 *
                 * Caso contrário usamos exatamente o
                 * componente original produzido pelo
                 * magiskboot unpack.
                 */
                val source =
                    rebuiltComponents[
                        name
                    ] ?: File(
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
