package com.exynosbess.app.tools

import android.content.Context
import java.io.File

object ToolManager {

    data class ToolResult(
        val success: Boolean,
        val path: String?,
        val message: String
    )

    fun installMagiskBoot(
        context: Context
    ): ToolResult {
        return installTool(
            context,
            "magiskboot"
        )
    }

    fun installDtc(
        context: Context
    ): ToolResult {
        return installTool(
            context,
            "dtc"
        )
    }

    private fun installTool(
        context: Context,
        toolName: String
    ): ToolResult {

        return try {

            val toolsDir =
                File(
                    context.filesDir,
                    "tools"
                )

            if (!toolsDir.exists()) {
                toolsDir.mkdirs()
            }

            val output =
                File(
                    toolsDir,
                    toolName
                )

            context.assets
                .open("tools/$toolName")
                .use { input ->

                    output
                        .outputStream()
                        .use { out ->

                            input.copyTo(out)
                        }
                }

            if (
                !output.exists() ||
                output.length() == 0L
            ) {

                return ToolResult(
                    false,
                    null,
                    "$toolName não pôde ser instalado"
                )
            }

            output.setReadable(
                true,
                false
            )

            output.setExecutable(
                true,
                false
            )

            ToolResult(
                true,
                output.absolutePath,
                "$toolName instalado"
            )

        } catch (e: Exception) {

            ToolResult(
                false,
                null,
                e.message
                    ?: "Erro ao instalar $toolName"
            )
        }
    }
}
