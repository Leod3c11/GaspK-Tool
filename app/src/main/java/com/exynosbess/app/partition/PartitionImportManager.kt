package com.exynosbess.app.partition

import android.content.Context
import com.exynosbess.app.boot.ShellExecutor
import java.io.File
import java.security.MessageDigest

object PartitionImportManager {

    data class Result(
        val success: Boolean,
        val message: String,
        val originalPath: String? = null,
        val sha256: String? = null
    )

    fun import(
        context: Context,
        partition: PartitionInfo
    ): Result {

        val workspace =
            PartitionWorkspace.prepare(
                context,
                partition
            )

        /*
         * original.img representa exatamente o conteúdo
         * importado da partição.
         *
         * Não sobrescrevemos silenciosamente um original
         * já existente.
         */
        if (
            workspace.original.exists() &&
            workspace.original.length() > 0L
        ) {
            val hash =
                sha256(
                    workspace.original
                )

            return Result(
                success = true,
                message =
                    "Original já importado anteriormente.",
                originalPath =
                    workspace.original.absolutePath,
                sha256 = hash
            )
        }

        val temporary =
            File(
                workspace.root,
                "original.img.tmp"
            )

        temporary.delete()

        val source =
            shellQuote(
                partition.resolvedPath
            )

        val destination =
            shellQuote(
                temporary.absolutePath
            )

        val uid =
            android.os.Process.myUid()

        val command =
            """
            rm -f $destination &&
            dd if=$source of=$destination bs=1048576 2>/dev/null &&
            sync &&
            chown $uid:$uid $destination &&
            chmod 600 $destination
            """.trimIndent()

        val copy =
            ShellExecutor.root(
                command
            )

        if (!copy.success) {
            temporary.delete()

            return Result(
                success = false,
                message =
                    "Falha ao copiar partição.\n" +
                        copy.output
            )
        }

        if (
            !temporary.exists() ||
            temporary.length() <= 0L
        ) {
            temporary.delete()

            return Result(
                success = false,
                message =
                    "A cópia foi executada, mas o arquivo resultante é inválido."
            )
        }

        val expectedSize =
            partition.sizeBytes

        if (
            expectedSize != null &&
            expectedSize > 0L &&
            temporary.length() != expectedSize
        ) {
            val actual =
                temporary.length()

            temporary.delete()

            return Result(
                success = false,
                message =
                    "Tamanho da cópia não corresponde à partição.\n" +
                        "Esperado: $expectedSize bytes\n" +
                        "Obtido: $actual bytes"
            )
        }

        if (!temporary.renameTo(workspace.original)) {

            temporary.delete()

            return Result(
                success = false,
                message =
                    "Não foi possível finalizar original.img."
            )
        }

        val hash =
            sha256(
                workspace.original
            )

        if (hash == null) {
            workspace.original.delete()

            return Result(
                success = false,
                message =
                    "Falha ao calcular SHA-256 do arquivo importado."
            )
        }

        /*
         * Criamos também backup.img como segunda cópia
         * local imutável antes de qualquer edição.
         */
        if (!copyFile(
                workspace.original,
                workspace.backup
            )
        ) {
            return Result(
                success = false,
                message =
                    "Original importado, mas não foi possível criar backup.img."
            )
        }

        writeMetadata(
            workspace = workspace,
            partition = partition,
            hash = hash
        )

        return Result(
            success = true,
            message =
                "Partição importada com segurança.",
            originalPath =
                workspace.original.absolutePath,
            sha256 = hash
        )
    }

    private fun copyFile(
        source: File,
        destination: File
    ): Boolean {

        return try {

            source.inputStream().use {
                input ->

                destination.outputStream().use {
                    output ->

                    input.copyTo(
                        output
                    )
                }
            }

            source.length() ==
                destination.length()

        } catch (_: Exception) {
            false
        }
    }

    private fun writeMetadata(
        workspace: PartitionWorkspace.Workspace,
        partition: PartitionInfo,
        hash: String
    ) {

        workspace.metadata.writeText(
            buildString {

                appendLine(
                    "name=${partition.name}"
                )

                appendLine(
                    "type=${partition.type.name}"
                )

                appendLine(
                    "alias=${partition.aliasPath}"
                )

                appendLine(
                    "resolved=${partition.resolvedPath}"
                )

                appendLine(
                    "size=${partition.sizeBytes ?: -1L}"
                )

                appendLine(
                    "slot=${partition.slotSuffix ?: ""}"
                )

                appendLine(
                    "sha256=$hash"
                )

                partition.aliases.forEach {
                    appendLine(
                        "alias_entry=$it"
                    )
                }
            }
        )
    }

    private fun sha256(
        file: File
    ): String? {

        return try {

            val digest =
                MessageDigest.getInstance(
                    "SHA-256"
                )

            file.inputStream().use {
                input ->

                val buffer =
                    ByteArray(
                        1024 * 1024
                    )

                while (true) {

                    val count =
                        input.read(
                            buffer
                        )

                    if (count <= 0) {
                        break
                    }

                    digest.update(
                        buffer,
                        0,
                        count
                    )
                }
            }

            digest.digest()
                .joinToString("") {
                    "%02x".format(it)
                }

        } catch (_: Exception) {
            null
        }
    }

    private fun shellQuote(
        value: String
    ): String {

        return "'" +
            value.replace(
                "'",
                "'\"'\"'"
            ) +
            "'"
    }
}
