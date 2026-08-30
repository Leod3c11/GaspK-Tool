package com.exynosbess.app.boot

import android.content.Context
import android.os.Process
import java.io.File

object DtbPartitionFlashManager {

    data class Result(
        val success: Boolean,
        val message: String,
        val backupPath: String? = null
    )

    fun flash(
        context: Context
    ): Result {

        try {

            val image =
                File(
                    context.filesDir,
                    "dtb_output/dtb_modified.img"
                )

            if (
                !image.exists() ||
                image.length() == 0L
            ) {
                return fail(
                    "dtb_modified.img não encontrado"
                )
            }

            /*
             * Verificação obrigatória antes do flash.
             */
            val roundTrip =
                DtbPartitionRoundTripVerifier.verify(
                    context
                )

            if (!roundTrip.success) {
                return fail(
                    "Imagem DTB não passou na verificação:\n${roundTrip.message}"
                )
            }

            val partition =
                DtbPartitionResolver.detect()

            if (
                !partition.found ||
                partition.path == null
            ) {
                return fail(
                    "Partição DTB não encontrada"
                )
            }

            val partitionPath =
                partition.path

            val sizeResult =
                ShellExecutor.root(
                    "blockdev --getsize64 '$partitionPath'"
                )

            if (!sizeResult.success) {
                return fail(
                    "Não foi possível obter tamanho da partição DTB:\n${sizeResult.output}"
                )
            }

            val partitionSize =
                sizeResult.output
                    .lineSequence()
                    .firstOrNull()
                    ?.trim()
                    ?.toLongOrNull()
                    ?: return fail(
                        "Tamanho inválido da partição DTB"
                    )

            /*
             * Exigimos imagem completa, não parcial.
             */
            if (
                image.length() !=
                partitionSize
            ) {
                return fail(
                    "Tamanho da imagem diferente da partição. " +
                        "Imagem=${image.length()}, " +
                        "partição=$partitionSize"
                )
            }

            val backupDir =
                File(
                    context.filesDir,
                    "dtb_backups"
                )

            if (!backupDir.exists()) {
                backupDir.mkdirs()
            }

            /*
             * Não sobrescrever backups anteriores.
             */
            val backup =
                File(
                    backupDir,
                    "dtb_preflash_${System.currentTimeMillis()}.img"
                )

            val backupResult =
                ShellExecutor.root(
                    "dd if='$partitionPath' " +
                        "of='${backup.absolutePath}' " +
                        "bs=4096"
                )

            if (!backupResult.success) {
                return fail(
                    "Falha criando backup preflash:\n${backupResult.output}"
                )
            }

            if (
                !backup.exists() ||
                backup.length() !=
                partitionSize
            ) {
                return fail(
                    "Backup da partição DTB possui tamanho inválido"
                )
            }

            val uid =
                Process.myUid()

            ShellExecutor.root(
                "chown $uid:$uid '${backup.absolutePath}' && " +
                    "chmod 600 '${backup.absolutePath}'"
            )

            val imageHashResult =
                ShellExecutor.root(
                    "sha256sum '${image.absolutePath}'"
                )

            if (!imageHashResult.success) {
                return Result(
                    false,
                    "Não foi possível calcular SHA-256 da imagem",
                    backup.absolutePath
                )
            }

            val expectedHash =
                firstHash(
                    imageHashResult.output
                )

            if (expectedHash == null) {
                return Result(
                    false,
                    "SHA-256 da imagem inválido",
                    backup.absolutePath
                )
            }

            /*
             * Flash.
             */
            val write =
                ShellExecutor.root(
                    "dd if='${image.absolutePath}' " +
                        "of='$partitionPath' " +
                        "bs=4096 && sync"
                )

            if (!write.success) {
                return Result(
                    false,
                    "Falha gravando partição DTB:\n${write.output}",
                    backup.absolutePath
                )
            }

            /*
             * SHA-256 direto da partição após o flash.
             */
            val partitionHashResult =
                ShellExecutor.root(
                    "sha256sum '$partitionPath'"
                )

            if (!partitionHashResult.success) {
                return Result(
                    false,
                    "DTB gravado, mas não foi possível verificar SHA-256 da partição",
                    backup.absolutePath
                )
            }

            val actualHash =
                firstHash(
                    partitionHashResult.output
                )

            if (
                actualHash == null ||
                actualHash !=
                expectedHash
            ) {
                return Result(
                    false,
                    "Falha na verificação SHA-256 após flash",
                    backup.absolutePath
                )
            }

            return Result(
                true,
                "Partição DTB gravada e verificada com sucesso",
                backup.absolutePath
            )

        } catch (
            e: Exception
        ) {

            return fail(
                e.message
                    ?: "Erro durante flash da partição DTB"
            )
        }
    }

    private fun firstHash(
        output: String
    ): String? {

        val value =
            output.trim()
                .split(
                    Regex("\\s+")
                )
                .firstOrNull()
                ?.lowercase()

        return if (
            value != null &&
            value.matches(
                Regex(
                    "[0-9a-f]{64}"
                )
            )
        ) {
            value
        } else {
            null
        }
    }

    private fun fail(
        message: String
    ): Result {

        return Result(
            false,
            message,
            null
        )
    }
}
