package com.exynosbess.app.ai

import java.io.File

object GaspAiLocalEngine {

    private const val ERROR_PREFIX =
        "[GASP_AI_ERROR]"

    data class GenerateResult(
        val success: Boolean,
        val text: String,
        val error: String? = null
    )

    fun version():
        String {
        return GaspAiNative.nativeVersion()
    }

    fun loadModel(
        model: File
    ): Boolean {

        if (
            !model.exists() ||
            !model.isFile ||
            model.length() <= 0L
        ) {
            return false
        }

        return GaspAiNative.nativeLoadModel(
            model.absolutePath
        )
    }

    fun isModelLoaded():
        Boolean {
        return GaspAiNative
            .nativeIsModelLoaded()
    }

    fun unloadModel() {
        GaspAiNative
            .nativeUnloadModel()
    }

    fun generate(
        prompt: String,
        maxTokens: Int = 256
    ): GenerateResult {

        if (
            prompt.isBlank()
        ) {

            return GenerateResult(
                success = false,
                text = "",
                error = "Prompt vazio."
            )
        }

        if (
            !isModelLoaded()
        ) {

            return GenerateResult(
                success = false,
                text = "",
                error = "Modelo da Gasp AI não carregado."
            )
        }

        val result =
            GaspAiNative.nativeGenerate(
                prompt,
                maxTokens.coerceIn(
                    16,
                    512
                )
            )

        if (
            result.startsWith(
                ERROR_PREFIX
            )
        ) {

            return GenerateResult(
                success = false,
                text = "",
                error = result
                    .removePrefix(
                        ERROR_PREFIX
                    )
                    .trim()
            )
        }

        return GenerateResult(
            success = true,
            text = result.trim()
        )
    }
}
