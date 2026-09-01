package com.exynosbess.app.partition

import android.content.Context
import android.os.Process
import com.exynosbess.app.boot.ShellExecutor
import com.exynosbess.app.tools.ToolManager
import java.io.File

object PartitionPipeline {

    data class Result(
        val success: Boolean,
        val message: String,
        val newState: PartitionSessionState? = null
    )

    fun extract(
        context: Context,
        partition: PartitionInfo
    ): Result {

        return when (partition.type) {

            PartitionType.BOOT ->
                extractBoot(
                    context,
                    partition
                )

            PartitionType.DTB ->
                Result(
                    success = false,
                    message =
                        "Pipeline de extração DTB ainda não conectado."
                )

            PartitionType.DTBO ->
                Result(
                    success = false,
                    message =
                        "Pipeline de extração DTBO ainda não conectado."
                )
        }
    }

    private fun extractBoot(
        context: Context,
        partition: PartitionInfo
    ): Result {

        val workspace =
            PartitionWorkspace.prepare(
                context,
                partition
            )

        val original =
            workspace.original

        if (
            !original.exists() ||
            original.length() <= 0L
        ) {
            return Result(
                success = false,
                message =
                    "original.img não encontrado. Importe a partição primeiro."
            )
        }

        val magiskboot =
            ToolManager.installMagiskBoot(
                context
            )

        if (
            !magiskboot.success ||
            magiskboot.path == null
        ) {
            return Result(
                success = false,
                message =
                    "Falha preparando magiskboot:\n" +
                        magiskboot.message
            )
        }

        val extracted =
            workspace.extracted

        /*
         * extracted/ é uma área derivada.
         * Pode ser recriada sem tocar em original.img
         * ou backup.img.
         */
        val clean =
            ShellExecutor.root(
                "rm -rf ${shellQuote(extracted.absolutePath)} && " +
                    "mkdir -p ${shellQuote(extracted.absolutePath)}"
            )

        if (!clean.success) {
            return Result(
                success = false,
                message =
                    "Falha preparando diretório de extração:\n" +
                        clean.output
            )
        }

        val command =
            "cd ${shellQuote(extracted.absolutePath)} && " +
                "${shellQuote(magiskboot.path)} unpack " +
                shellQuote(original.absolutePath)

        val unpack =
            ShellExecutor.root(
                command
            )

        if (!unpack.success) {
            return Result(
                success = false,
                message =
                    "magiskboot não conseguiu extrair ${partition.name}:\n\n" +
                        unpack.output
            )
        }

        val uid =
            Process.myUid()

        val ownership =
            ShellExecutor.root(
                "chown -R $uid:$uid " +
                    shellQuote(extracted.absolutePath) +
                    " && chmod -R u+rwX,go-rwx " +
                    shellQuote(extracted.absolutePath)
            )

        if (!ownership.success) {
            return Result(
                success = false,
                message =
                    "Boot extraído, mas falhou o ajuste de permissões:\n" +
                        ownership.output
            )
        }

        val files =
            extracted
                .listFiles()
                ?.filter {
                    it.isFile &&
                        it.length() > 0L
                }
                ?.sortedBy {
                    it.name
                }
                ?: emptyList()

        if (files.isEmpty()) {
            return Result(
                success = false,
                message =
                    "magiskboot terminou, mas nenhum componente válido foi encontrado."
            )
        }

        val componentNames =
            files.map {
                it.name
            }

        /*
         * Não exigimos extra, dtb ou kernel_dtb.
         * Registramos somente o que realmente existe.
         */
        val interesting =
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
                .filter {
                    File(
                        extracted,
                        it
                    ).exists()
                }

        return Result(
            success = true,
            newState =
                PartitionSessionState.EXTRACTED,
            message =
                buildString {

                    appendLine(
                        "Boot extraído com sucesso."
                    )

                    appendLine()
                    appendLine(
                        "Workspace:"
                    )

                    appendLine(
                        extracted.absolutePath
                    )

                    appendLine()
                    appendLine(
                        "Componentes reconhecidos:"
                    )

                    if (interesting.isEmpty()) {
                        appendLine(
                            "Nenhum componente padrão identificado."
                        )
                    } else {
                        interesting.forEach {
                            appendLine(
                                "• $it"
                            )
                        }
                    }

                    val others =
                        componentNames.filterNot {
                            it in interesting
                        }

                    if (others.isNotEmpty()) {

                        appendLine()
                        appendLine(
                            "Outros arquivos:"
                        )

                        others.forEach {
                            appendLine(
                                "• $it"
                            )
                        }
                    }

                    if (unpack.output.isNotBlank()) {

                        appendLine()
                        appendLine(
                            "magiskboot:"
                        )

                        append(
                            unpack.output
                        )
                    }
                }
        )
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
