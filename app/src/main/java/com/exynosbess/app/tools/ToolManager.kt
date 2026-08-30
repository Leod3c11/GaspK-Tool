package com.exynosbess.app.tools

import android.content.Context
import java.io.File

object ToolManager {

    data class ToolResult(
        val success: Boolean,
        val path: String?,
        val message: String
    )

    fun installMagiskBoot(context: Context): ToolResult {

        return try {

            val toolsDir = File(
                context.filesDir,
                "tools"
            )

            if (!toolsDir.exists()) {
                toolsDir.mkdirs()
            }

            val output = File(
                toolsDir,
                "magiskboot"
            )

            context.assets
                .open("tools/magiskboot")
                .use { input ->

                    output.outputStream().use { out ->
                        input.copyTo(out)
                    }
                }

            if (!output.exists() || output.length() == 0L) {
                return ToolResult(
                    false,
                    null,
                    "Falha ao copiar magiskboot"
                )
            }

            output.setExecutable(true, false)
            output.setReadable(true, false)
            output.setWritable(true, true)

            ToolResult(
                true,
                output.absolutePath,
                "magiskboot instalado"
            )

        } catch (e: Exception) {

            ToolResult(
                false,
                null,
                e.message ?: "Erro ao instalar magiskboot"
            )
        }
    }
}
