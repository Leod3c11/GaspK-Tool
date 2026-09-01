package com.exynosbess.app.partition

import android.content.Context
import android.os.Process
import com.exynosbess.app.boot.ShellExecutor
import com.exynosbess.app.tools.ToolManager
import java.io.File

object PartitionDtsValidator {

    data class Result(
        val success: Boolean,
        val message: String,
        val count: Int = 0
    )

    fun validate(
        context: Context,
        partition: PartitionInfo
    ): Result {

        val workspace =
            PartitionWorkspace.get(
                context,
                partition
            )

        val dtbDir =
            File(
                workspace.extracted,
                "dtbs"
            )

        val dtsFiles =
            dtbDir.listFiles()
                ?.filter {
                    it.isFile &&
                        it.name.matches(
                            Regex(
                                """dtb_\d+\.dts"""
                            )
                        )
                }
                ?.sortedBy {
                    it.name
                }
                ?: emptyList()

        if (dtsFiles.isEmpty()) {
            return Result(
                false,
                "Nenhum DTS encontrado para validação."
            )
        }

        val tool =
            ToolManager.installDtc(
                context
            )

        if (
            !tool.success ||
            tool.path == null
        ) {
            return Result(
                false,
                "dtc indisponível: ${tool.message}"
            )
        }

        val validationDir =
            File(
                workspace.root,
                "validation"
            )

        if (validationDir.exists()) {
            validationDir.deleteRecursively()
        }

        if (!validationDir.mkdirs()) {
            return Result(
                false,
                "Não foi possível criar diretório de validação."
            )
        }

        for (file in dtsFiles) {

            val output =
                File(
                    validationDir,
                    file.nameWithoutExtension +
                        ".dtb"
                )

            val command =
                quote(tool.path) +
                    " -I dts" +
                    " -O dtb" +
                    " -o " +
                    quote(
                        output.absolutePath
                    ) +
                    " " +
                    quote(
                        file.absolutePath
                    )

            val result =
                ShellExecutor.root(
                    command
                )

            if (
                !result.success ||
                !output.exists() ||
                output.length() <= 0L
            ) {

                val error =
                    result.output
                        .lineSequence()
                        .firstOrNull {
                            it.isNotBlank()
                        }
                        ?.take(300)
                        ?: "Erro desconhecido"

                return Result(
                    false,
                    "DTS inválido: ${file.name}\n\n$error"
                )
            }
        }

        val uid =
            Process.myUid()

        ShellExecutor.root(
            "chown -R $uid:$uid " +
                quote(
                    validationDir.absolutePath
                ) +
                " && chmod -R u+rwX " +
                quote(
                    validationDir.absolutePath
                )
        )

        return Result(
            true,
            "${dtsFiles.size} DTS validado(s) com sucesso.",
            dtsFiles.size
        )
    }


    private fun quote(
        value: String
    ): String {

        return "'" +
            value.replace(
                "'",
                "'\\''"
            ) +
            "'"
    }
}
