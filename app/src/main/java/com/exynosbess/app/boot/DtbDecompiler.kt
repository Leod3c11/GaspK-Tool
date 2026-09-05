package com.exynosbess.app.boot

import android.content.Context
import android.os.Process
import com.exynosbess.app.tools.ToolManager
import java.io.File

object DtbDecompiler {

    data class DecompiledFile(
        val index: Int,
        val dtbPath: String,
        val dtsPath: String,
        val dtsSize: Long
    )

    data class DecompileResult(
        val success: Boolean,
        val files: List<DecompiledFile>,
        val message: String
    )

    fun decompileAll(
        context: Context,
        workspacePath: String
    ): DecompileResult {

        val tool =
            ToolManager.installDtc(context)

        if (!tool.success || tool.path == null) {
            return DecompileResult(
                false,
                emptyList(),
                "Falha ao preparar dtc: ${tool.message}"
            )
        }

        val dtbDir =
            File(
                workspacePath,
                "dtbs"
            )

        if (!dtbDir.exists()) {
            return DecompileResult(
                false,
                emptyList(),
                "Diretório de DTBs não encontrado"
            )
        }

        val dtbs =
            dtbDir.listFiles()
                ?.filter {
                    it.isFile &&
                    it.name.endsWith(".dtb")
                }
                ?.sortedBy {
                    it.name
                }
                ?: emptyList()

        if (dtbs.isEmpty()) {
            return DecompileResult(
                false,
                emptyList(),
                "Nenhum DTB encontrado para converter"
            )
        }

        val outputFiles =
            mutableListOf<DecompiledFile>()

        val appUid = Process.myUid()

        for ((index, dtb) in dtbs.withIndex()) {

            val dts =
                File(
                    dtbDir,
                    dtb.nameWithoutExtension + ".dts"
                )

            if (dts.exists()) {
                ShellExecutor.root(
                    "rm -f '${dts.absolutePath}'"
                )
            }

            val command =
                "'${tool.path}' " +
                "-I dtb " +
                "-O dts " +
                "-o '${dts.absolutePath}' " +
                "'${dtb.absolutePath}'"

            val result =
                ShellExecutor.root(command)

            if (!result.success) {
                return DecompileResult(
                    false,
                    outputFiles,
                    "Falha ao converter ${dtb.name}\n\n${result.output}"
                )
            }

            if (!dts.exists() || dts.length() == 0L) {
                return DecompileResult(
                    false,
                    outputFiles,
                    "${dts.name} não foi criado"
                )
            }

            /*
             * O dtc foi executado como root.
             * Devolvemos o arquivo ao UID do aplicativo
             * para que o editor consiga salvá-lo normalmente.
             */
            val permissionResult =
                ShellExecutor.root(
                    "chown $appUid:$appUid '${dts.absolutePath}' && " +
                    "chmod 600 '${dts.absolutePath}'"
                )

            if (!permissionResult.success) {
                return DecompileResult(
                    false,
                    outputFiles,
                    "DTS criado, mas não foi possível ajustar permissões:\n" +
                    permissionResult.output
                )
            }

            val originalsDir =
                File(
                    dtbDir,
                    "originals"
                )

            if (
                !originalsDir.exists() &&
                !originalsDir.mkdirs()
            ) {
                return DecompileResult(
                    false,
                    outputFiles,
                    "Não foi possível criar o diretório de DTS originais"
                )
            }

            val originalDts =
                File(
                    originalsDir,
                    dts.name
                )

            if (!originalDts.exists()) {
                dts.copyTo(
                    originalDts,
                    overwrite = false
                )
            }

            outputFiles.add(
                DecompiledFile(
                    index = index,
                    dtbPath = dtb.absolutePath,
                    dtsPath = dts.absolutePath,
                    dtsSize = dts.length()
                )
            )
        }

        return DecompileResult(
            true,
            outputFiles,
            "${outputFiles.size} DTS(s) criado(s)"
        )
    }
}
