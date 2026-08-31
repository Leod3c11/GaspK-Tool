package com.exynosbess.app.ai

object GaspAiNative {

    init {
        System.loadLibrary(
            "gasp_ai_native"
        )
    }

    external fun nativeVersion():
        String
}
