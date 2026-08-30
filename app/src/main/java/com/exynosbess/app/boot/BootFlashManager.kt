package com.exynosbess.app.boot

import android.content.Context
import android.os.Process
import java.io.File

object BootFlashManager {

    data class FlashResult(
        val success: Boolean,
        val message: String,
        val backupPath: String? = null
    )

    fun flash(context: Context): FlashResult {

        try {

            val modifiedBoot =
                File(
                    context.filesDir,
                    "boot_output/boot_modified.img"
                )

            if (
                !modifiedBoot.exists() ||
                modifiedBoot.length() == 0L
            ) {
                return fail(
                    "boot_modified.img não encontrado"
                )
            }

            /*
             * Detectar novamente a partição boot.
             * Não reutilizamos caminho antigo.
             */
            val partition =
                BootPartitionResolver.detect()

            if (
                !partition.found ||
                partition.path == null
            ) {
                return fail(
                    "Partição boot não encontrada"
                )
            }

            val bootPath =
                partition.path

            /*
             * Obter tamanho real da partição.
             */
            val sizeResult =
                ShellExecutor.root(
                    "blockdev --getsize64 '$bootPath'"
                )

            if (!sizeResult.success) {
                return fail(
                    "Não foi possível obter o tamanho da partição boot:\n" +
                    sizeResult.output
                )
            }

            val partitionSize =
                sizeResult.output
                    .lineSequence()
                    .lastOrNull()
                    ?.trim()
                    ?.toLongOrNull()
                    ?: return fail(
                        "Tamanho inválido da partição boot"
                    )

            if (
                modifiedBoot.length() >
                partitionSize
            ) {
                return fail(
                    "FLASH BLOQUEADO.\n\n" +
                    "boot_modified.img é maior que a partição.\n\n" +
                    "Imagem: ${modifiedBoot.length()} bytes\n" +
                    "Partição: $partitionSize bytes"
                )
            }

            /*
             * Backup imediatamente antes do flash.
             */
            val backupDir =
                File(
                    context.filesDir,
                    "boot_backups"
                )

            backupDir.mkdirs()

            val preFlashBackup =
                File(
                    backupDir,
                    "boot_preflash.img"
                )

            if (preFlashBackup.exists()) {
                ShellExecutor.root(
                    "rm -f '${preFlashBackup.absolutePath}'"
                )
            }

            val backup =
                ShellExecutor.root(
                    "dd if='$bootPath' " +
                    "of='${preFlashBackup.absolutePath}' " +
                    "bs=4096 && sync"
                )

            if (
                !backup.success ||
                !preFlashBackup.exists() ||
                preFlashBackup.length() == 0L
            ) {
                return fail(
                    "Flash cancelado: backup pré-flash falhou.\n\n" +
                    backup.output
                )
            }

            if (
                preFlashBackup.length() !=
                partitionSize
            ) {
                return fail(
                    "Flash cancelado: tamanho do backup não corresponde " +
                    "à partição.\n\n" +
                    "Backup: ${preFlashBackup.length()} bytes\n" +
                    "Partição: $partitionSize bytes"
                )
            }

            /*
             * Entregar backup ao UID do app.
             */
            val uid =
                Process.myUid()

            val permissions =
                ShellExecutor.root(
                    "chown $uid:$uid '${preFlashBackup.absolutePath}' && " +
                    "chmod 600 '${preFlashBackup.absolutePath}'"
                )

            if (!permissions.success) {
                return fail(
                    "Flash cancelado: backup criado, mas não foi possível " +
                    "ajustar suas permissões."
                )
            }

            /*
             * FLASH.
             *
             * Não há reboot aqui.
             */
            val flash =
                ShellExecutor.root(
                    "dd if='${modifiedBoot.absolutePath}' " +
                    "of='$bootPath' " +
                    "bs=4096 && sync"
                )

            if (!flash.success) {
                return fail(
                    "Falha durante gravação da partição boot.\n\n" +
                    flash.output
                )
            }

            /*
             * Verificação simples pós-gravação:
             * comparar SHA-256 dos primeiros N bytes da
             * partição com a imagem que acabamos de gravar.
             */
            val imageHash =
                ShellExecutor.root(
                    "sha256sum '${modifiedBoot.absolutePath}' | " +
                    "awk '{print \$1}'"
                )

            val partitionHash =
                ShellExecutor.root(
                    "head -c ${modifiedBoot.length()} '$bootPath' | " +
                    "sha256sum | awk '{print \$1}'"
                )

            if (
                !imageHash.success ||
                !partitionHash.success
            ) {
                return fail(
                    "Boot foi gravado, mas não foi possível verificar SHA-256.\n\n" +
                    "NÃO REINICIE até verificar a situação."
                )
            }

            val expected =
                imageHash.output.trim()

            val actual =
                partitionHash.output.trim()

            if (
                expected.isBlank() ||
                actual.isBlank() ||
                expected != actual
            ) {
                return fail(
                    "ATENÇÃO: verificação pós-flash falhou.\n\n" +
                    "Esperado:\n$expected\n\n" +
                    "Lido da partição:\n$actual\n\n" +
                    "NÃO REINICIE O APARELHO."
                )
            }

            return FlashResult(
                success = true,
                backupPath =
                    preFlashBackup.absolutePath,
                message =
                    "Boot gravado e verificado com sucesso.\n\n" +
                    "Partição:\n$bootPath\n\n" +
                    "SHA-256:\n$expected\n\n" +
                    "Backup pré-flash:\n" +
                    preFlashBackup.absolutePath +
                    "\n\nNenhum reboot foi executado."
            )

        } catch (e: Exception) {

            return fail(
                e.stackTraceToString()
            )
        }
    }

    private fun fail(
        message: String
    ): FlashResult {

        return FlashResult(
            success = false,
            message = message
        )
    }
}
