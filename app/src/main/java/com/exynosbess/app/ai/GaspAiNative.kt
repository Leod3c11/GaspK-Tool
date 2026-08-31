package com.exynosbess.app.ai

object GaspAiNative {

    init {
        System.loadLibrary(
            "gasp_ai_native"
        )
    }

    external fun nativeVersion():
        String

    external fun nativeLoadModel(
        modelPath: String
    ): Boolean

    external fun nativeIsModelLoaded():
        Boolean

    external fun nativeUnloadModel()

    external fun nativeGenerate(
        prompt: String,
        maxTokens: Int
    ): String
}
