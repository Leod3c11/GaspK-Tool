package com.exynosbess.app.partition

import android.content.Context
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

object PartitionVerificationStore {

    data class Check(
        val valid: Boolean,
        val message: String,
        val expectedHash: String? = null,
        val actualHash: String? = null
    )

    private const val FILE_NAME =
        "verified.txt"

    fun markVerified(
        context: Context,
        partition: PartitionInfo
    ): Check {

        return try {

            val workspace =
                PartitionWorkspace.prepare(
                    context,
                    partition
                )

            val rebuilt =
                workspace.rebuilt

            if (
                !rebuilt.exists() ||
                rebuilt.length() <= 0L
            ) {
                return Check(
                    false,
                    "rebuilt.img não existe."
                )
            }

            val hash =
                sha256(
                    rebuilt
                )

            if (hash.isBlank()) {
                return Check(
                    false,
                    "Falha calculando SHA-256 da imagem verificada."
                )
            }

            val file =
                File(
                    workspace.root,
                    FILE_NAME
                )

            val temporary =
                File(
                    workspace.root,
                    ".$FILE_NAME.tmp"
                )

            if (temporary.exists()) {
                temporary.delete()
            }

            temporary.writeText(
                buildString {

                    appendLine(
                        "type=${partition.type.name}"
                    )

                    appendLine(
                        "name=${partition.name}"
                    )

                    appendLine(
                        "resolvedPath=${partition.resolvedPath}"
                    )

                    appendLine(
                        "size=${rebuilt.length()}"
                    )

                    appendLine(
                        "sha256=$hash"
                    )
                }
            )

            if (file.exists()) {
                file.delete()
            }

            if (!temporary.renameTo(file)) {

                temporary.delete()

                return Check(
                    false,
                    "Não foi possível salvar o registro de verificação."
                )
            }

            Check(
                true,
                "Imagem registrada como verificada.",
                expectedHash = hash,
                actualHash = hash
            )

        } catch (e: Exception) {

            Check(
                false,
                e.message
                    ?: "Falha registrando verificação."
            )
        }
    }


    fun check(
        context: Context,
        partition: PartitionInfo
    ): Check {

        return try {

            val workspace =
                PartitionWorkspace.get(
                    context,
                    partition
                )

            val rebuilt =
                workspace.rebuilt

            val record =
                File(
                    workspace.root,
                    FILE_NAME
                )

            if (
                !rebuilt.exists() ||
                rebuilt.length() <= 0L
            ) {
                return Check(
                    false,
                    "Imagem reconstruída não encontrada."
                )
            }

            if (!record.exists()) {
                return Check(
                    false,
                    "A imagem atual não possui registro de verificação."
                )
            }

            val data =
                record
                    .readLines()
                    .mapNotNull { line ->

                        val position =
                            line.indexOf('=')

                        if (position <= 0) {
                            null
                        } else {
                            line.substring(
                                0,
                                position
                            ) to
                                line.substring(
                                    position + 1
                                )
                        }
                    }
                    .toMap()

            if (
                data["type"] !=
                partition.type.name
            ) {
                return Check(
                    false,
                    "Tipo da imagem verificada não corresponde à sessão."
                )
            }

            if (
                data["name"] !=
                partition.name
            ) {
                return Check(
                    false,
                    "Partição verificada não corresponde à sessão atual."
                )
            }

            if (
                data["resolvedPath"] !=
                partition.resolvedPath
            ) {
                return Check(
                    false,
                    "Destino verificado mudou desde a verificação."
                )
            }

            val expectedSize =
                data["size"]
                    ?.toLongOrNull()

            if (
                expectedSize == null ||
                expectedSize !=
                rebuilt.length()
            ) {
                return Check(
                    false,
                    "O tamanho de rebuilt.img mudou após a verificação."
                )
            }

            val expected =
                data["sha256"]
                    ?.trim()
                    .orEmpty()

            if (expected.isBlank()) {
                return Check(
                    false,
                    "Registro SHA-256 inválido."
                )
            }

            val actual =
                sha256(
                    rebuilt
                )

            if (
                actual.isBlank() ||
                actual != expected
            ) {
                return Check(
                    false,
                    "rebuilt.img foi alterado depois da verificação.",
                    expectedHash = expected,
                    actualHash = actual
                )
            }

            Check(
                true,
                "Imagem atual corresponde exatamente à imagem verificada.",
                expectedHash = expected,
                actualHash = actual
            )

        } catch (e: Exception) {

            Check(
                false,
                e.message
                    ?: "Falha conferindo registro de verificação."
            )
        }
    }


    fun invalidate(
        context: Context,
        partition: PartitionInfo
    ) {

        try {

            val workspace =
                PartitionWorkspace.get(
                    context,
                    partition
                )

            File(
                workspace.root,
                FILE_NAME
            ).delete()

            File(
                workspace.root,
                ".$FILE_NAME.tmp"
            ).delete()

        } catch (_: Exception) {
        }
    }


    private fun sha256(
        file: File
    ): String {

        val digest =
            MessageDigest.getInstance(
                "SHA-256"
            )

        FileInputStream(file).use { input ->

            val buffer =
                ByteArray(
                    1024 * 1024
                )

            while (true) {

                val read =
                    input.read(
                        buffer
                    )

                if (read <= 0) {
                    break
                }

                digest.update(
                    buffer,
                    0,
                    read
                )
            }
        }

        return digest
            .digest()
            .joinToString("") {
                "%02x".format(it)
            }
    }
}
