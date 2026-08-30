package com.exynosbess.app.boot

import android.content.Context
import com.exynosbess.app.tools.ToolManager
import java.io.File

object BootUnpackManager {

    data class UnpackResult(
        val success: Boolean,
        val workspace: String?,
        val output: String,
        val files: List<String>
    )

    fun unpack(context: Context): UnpackResult {

        val backup = File(
            context.filesDir,
            "boot_backups/boot_original.img"
        )

        if (!backup.exists()) {
            return UnpackResult(
                false,
                null,
                "boot_original.img não encontrado",
                emptyList()
            )
        }

        val tool = ToolManager.installMagiskBoot(context)

        if (!tool.success || tool.path == null) {
            return UnpackResult(
                false,
                null,
                tool.message,
                emptyList()
            )
        }

        val workspace = File(
            context.filesDir,
            "workspace"
        )

        if (workspace.exists()) {
            workspace.deleteRecursively()
        }

        workspace.mkdirs()

        val command =
            "cd '${workspace.absolutePath}' && " +
            "'${tool.path}' unpack '${backup.absolutePath}'"

        val result = ShellExecutor.root(command)

        val extractedFiles =
            workspace.listFiles()
                ?.map { file ->
                    "${file.name} (${file.length()} bytes)"
                }
                ?.sorted()
                ?: emptyList()

        return UnpackResult(
            success = result.success && extractedFiles.isNotEmpty(),
            workspace = workspace.absolutePath,
            output = result.output,
            files = extractedFiles
        )
    }
}
