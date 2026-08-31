package com.exynosbess.app.ai

import android.content.Context
import java.io.File

object GaspAiModelManager {

    const val MODEL_NAME =
        "Qwen2.5-Coder-0.5B-Instruct Q4_K_M"

    const val MODEL_SHA256 =
        "1d9614638d18024d0fbb36575a15f1302a3adf044df10345688ec4f6e1c4ff32"

    const val MODEL_URL =
        "https://huggingface.co/Qwen/Qwen2.5-Coder-0.5B-Instruct-GGUF/resolve/main/qwen2.5-coder-0.5b-instruct-q4_k_m.gguf?download=true"

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
            file.length() > 400L * 1024L * 1024L
    }
}
