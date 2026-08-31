package com.exynosbess.app.ai

import android.content.Context
import java.io.File

object GaspAiModelManager {

    const val MODEL_NAME =
        "SmolLM2-360M-Instruct Q4_K_M"

    const val MODEL_SHA256 =
        "2fa3f013dcdd7b99f9b237717fa0b12d75bbb89984cc1274be1471a465bac9c2"

    const val MODEL_URL =
        "https://huggingface.co/bartowski/SmolLM2-360M-Instruct-GGUF/resolve/main/SmolLM2-360M-Instruct-Q4_K_M.gguf?download=true"

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
            modelDirectory(context),
            MODEL_FILE
        )
    }

    fun temporaryFile(
        context: Context
    ): File {
        return File(
            modelDirectory(context),
            "$MODEL_FILE.part"
        )
    }

    fun isInstalled(
        context: Context
    ): Boolean {
        val file =
            modelFile(context)

        return file.exists() &&
            file.isFile &&
            file.length() > 240L * 1024L * 1024L
    }
}
