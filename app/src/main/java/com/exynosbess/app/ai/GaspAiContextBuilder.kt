package com.exynosbess.app.ai

import java.io.File

object GaspAiContextBuilder {

    fun build(
        dtsFile: File,
        nodeContent: String,
        userQuestion: String? = null
    ): GaspAiContext {

        val index =
            Regex("""dtb_(\d+)\.dts""")
                .find(dtsFile.name)
                ?.groupValues
                ?.getOrNull(1)
                ?.toIntOrNull()
                ?: -1

        val nodeName =
            nodeContent
                .lineSequence()
                .firstOrNull {
                    it.trim().isNotEmpty()
                }
                ?.substringBefore("{")
                ?.trim()
                ?: "GPU"

        return GaspAiContext(
            dtsFileName = dtsFile.name,
            dtbIndex = index,
            nodeName = nodeName,
            nodeContent = nodeContent,
            userQuestion =
                userQuestion
                    ?.trim()
                    ?.takeIf {
                        it.isNotEmpty()
                    }
        )
    }
}
