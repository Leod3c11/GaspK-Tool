package com.exynosbess.app.ai

import android.content.Context
import java.io.File

object GaspAiModelManager {

    private const val MODEL_DIR =
        "models"

    private const val MODEL_FILE =
        "gasp-ai.gguf"

    fun modelDirectory(
        context: Context
    ): File {

        return File(
            context.filesDir,
            MODEL_DIR
        ).apply {
            mkdirs()
        }
    }

    fun modelFile(
        context: Context
    ): File {

        return File(
            modelDirectory(
                context
            ),
            MODEL_FILE
        )
    }

    fun isInstalled(
        context: Context
    ): Boolean {

        val file =
            modelFile(
                context
            )

        return (
            file.exists() &&
            file.isFile &&
            file.length() >
                1024L * 1024L
        )
    }

    fun modelSizeBytes(
        context: Context
    ): Long {

        return modelFile(
            context
        ).takeIf {
            it.exists()
        }?.length()
            ?: 0L
    }
}
