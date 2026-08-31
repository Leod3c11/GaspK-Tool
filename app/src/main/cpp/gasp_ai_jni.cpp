#include <jni.h>
#include <android/log.h>

#include <algorithm>
#include <mutex>
#include <string>
#include <vector>

#include "llama.h"

#define LOG_TAG "GaspAiNative"

#define LOGI(...) \
    __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

#define LOGE(...) \
    __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

std::mutex g_mutex;

llama_model * g_model = nullptr;

bool g_backend_initialized = false;

constexpr uint32_t GASP_CONTEXT_SIZE = 2048;
constexpr uint32_t GASP_BATCH_SIZE = 512;

void ensureBackend() {

    if (
        !g_backend_initialized
    ) {

        llama_backend_init();

        g_backend_initialized =
            true;

        LOGI(
            "llama backend initialized"
        );
    }
}

void freeModelInternal() {

    if (
        g_model != nullptr
    ) {

        llama_model_free(
            g_model
        );

        g_model =
            nullptr;

        LOGI(
            "model unloaded"
        );
    }
}

std::string tokenToPiece(
    const llama_vocab * vocab,
    llama_token token
) {

    std::vector<char> buffer(
        256
    );

    int size =
        llama_token_to_piece(
            vocab,
            token,
            buffer.data(),
            static_cast<int>(
                buffer.size()
            ),
            0,
            true
        );

    if (
        size < 0
    ) {

        buffer.resize(
            static_cast<size_t>(
                -size
            )
        );

        size =
            llama_token_to_piece(
                vocab,
                token,
                buffer.data(),
                static_cast<int>(
                    buffer.size()
                ),
                0,
                true
            );
    }

    if (
        size <= 0
    ) {
        return {};
    }

    return std::string(
        buffer.data(),
        static_cast<size_t>(
            size
        )
    );
}

} // namespace

extern "C"
JNIEXPORT jstring JNICALL
Java_com_exynosbess_app_ai_GaspAiNative_nativeVersion(
        JNIEnv * env,
        jobject /* thiz */) {

    return env->NewStringUTF(
        "llama.cpp b10516 / Gasp AI native"
    );
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_exynosbess_app_ai_GaspAiNative_nativeLoadModel(
        JNIEnv * env,
        jobject /* thiz */,
        jstring modelPath) {

    std::lock_guard<std::mutex>
        lock(
            g_mutex
        );

    if (
        modelPath == nullptr
    ) {
        return JNI_FALSE;
    }

    const char * rawPath =
        env->GetStringUTFChars(
            modelPath,
            nullptr
        );

    if (
        rawPath == nullptr
    ) {
        return JNI_FALSE;
    }

    std::string path(
        rawPath
    );

    env->ReleaseStringUTFChars(
        modelPath,
        rawPath
    );

    ensureBackend();

    freeModelInternal();

    llama_model_params params =
        llama_model_default_params();

    /*
     * CPU-only.
     * Nenhum layer será offloadado para GPU.
     */
    params.n_gpu_layers =
        0;

    LOGI(
        "loading model: %s",
        path.c_str()
    );

    g_model =
        llama_model_load_from_file(
            path.c_str(),
            params
        );

    if (
        g_model == nullptr
    ) {

        LOGE(
            "failed to load model"
        );

        return JNI_FALSE;
    }

    LOGI(
        "model loaded successfully"
    );

    return JNI_TRUE;
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_exynosbess_app_ai_GaspAiNative_nativeIsModelLoaded(
        JNIEnv * /* env */,
        jobject /* thiz */) {

    std::lock_guard<std::mutex>
        lock(
            g_mutex
        );

    return
        g_model != nullptr
            ? JNI_TRUE
            : JNI_FALSE;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_exynosbess_app_ai_GaspAiNative_nativeUnloadModel(
        JNIEnv * /* env */,
        jobject /* thiz */) {

    std::lock_guard<std::mutex>
        lock(
            g_mutex
        );

    freeModelInternal();
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_exynosbess_app_ai_GaspAiNative_nativeGenerate(
        JNIEnv * env,
        jobject /* thiz */,
        jstring promptValue,
        jint maxTokensValue) {

    std::lock_guard<std::mutex>
        lock(
            g_mutex
        );

    if (
        g_model == nullptr
    ) {

        return env->NewStringUTF(
            "[GASP_AI_ERROR] model_not_loaded"
        );
    }

    if (
        promptValue == nullptr
    ) {

        return env->NewStringUTF(
            "[GASP_AI_ERROR] empty_prompt"
        );
    }

    const char * rawPrompt =
        env->GetStringUTFChars(
            promptValue,
            nullptr
        );

    if (
        rawPrompt == nullptr
    ) {

        return env->NewStringUTF(
            "[GASP_AI_ERROR] prompt_conversion_failed"
        );
    }

    std::string prompt(
        rawPrompt
    );

    env->ReleaseStringUTFChars(
        promptValue,
        rawPrompt
    );

    const int maxTokens =
        std::clamp(
            static_cast<int>(
                maxTokensValue
            ),
            16,
            512
        );

    const llama_vocab * vocab =
        llama_model_get_vocab(
            g_model
        );

    if (
        vocab == nullptr
    ) {

        return env->NewStringUTF(
            "[GASP_AI_ERROR] vocab_unavailable"
        );
    }

    /*
     * Descobre primeiro a quantidade necessária
     * de tokens.
     */
    const int required =
        -llama_tokenize(
            vocab,
            prompt.c_str(),
            prompt.size(),
            nullptr,
            0,
            true,
            true
        );

    if (
        required <= 0
    ) {

        return env->NewStringUTF(
            "[GASP_AI_ERROR] tokenization_failed"
        );
    }

    /*
     * Mantemos espaço no contexto para geração.
     */
    if (
        required + maxTokens >=
        static_cast<int>(
            GASP_CONTEXT_SIZE
        )
    ) {

        return env->NewStringUTF(
            "[GASP_AI_ERROR] context_too_large"
        );
    }

    std::vector<llama_token>
        promptTokens(
            static_cast<size_t>(
                required
            )
        );

    const int tokenized =
        llama_tokenize(
            vocab,
            prompt.c_str(),
            prompt.size(),
            promptTokens.data(),
            static_cast<int32_t>(
                promptTokens.size()
            ),
            true,
            true
        );

    if (
        tokenized < 0
    ) {

        return env->NewStringUTF(
            "[GASP_AI_ERROR] tokenization_failed"
        );
    }

    llama_context_params ctxParams =
        llama_context_default_params();

    ctxParams.n_ctx =
        GASP_CONTEXT_SIZE;

    ctxParams.n_batch =
        std::min<uint32_t>(
            GASP_BATCH_SIZE,
            GASP_CONTEXT_SIZE
        );

    ctxParams.n_ubatch =
        ctxParams.n_batch;

    ctxParams.n_threads =
        4;

    ctxParams.n_threads_batch =
        4;

    ctxParams.no_perf =
        true;

    llama_context * ctx =
        llama_init_from_model(
            g_model,
            ctxParams
        );

    if (
        ctx == nullptr
    ) {

        return env->NewStringUTF(
            "[GASP_AI_ERROR] context_creation_failed"
        );
    }

    auto samplerParams =
        llama_sampler_chain_default_params();

    samplerParams.no_perf =
        true;

    llama_sampler * sampler =
        llama_sampler_chain_init(
            samplerParams
        );

    if (
        sampler == nullptr
    ) {

        llama_free(
            ctx
        );

        return env->NewStringUTF(
            "[GASP_AI_ERROR] sampler_creation_failed"
        );
    }

    /*
     * Conservador para respostas técnicas.
     */
    llama_sampler_chain_add(
        sampler,
        llama_sampler_init_min_p(
            0.05f,
            1
        )
    );

    llama_sampler_chain_add(
        sampler,
        llama_sampler_init_temp(
            0.35f
        )
    );

    llama_sampler_chain_add(
        sampler,
        llama_sampler_init_dist(
            0x47415350
        )
    );

    llama_batch batch =
        llama_batch_get_one(
            promptTokens.data(),
            static_cast<int32_t>(
                promptTokens.size()
            )
        );

    if (
        llama_decode(
            ctx,
            batch
        ) != 0
    ) {

        llama_sampler_free(
            sampler
        );

        llama_free(
            ctx
        );

        return env->NewStringUTF(
            "[GASP_AI_ERROR] prompt_decode_failed"
        );
    }

    std::string output;

    for (
        int generated = 0;
        generated < maxTokens;
        generated++
    ) {

        llama_token token =
            llama_sampler_sample(
                sampler,
                ctx,
                -1
            );

        if (
            llama_vocab_is_eog(
                vocab,
                token
            )
        ) {
            break;
        }

        const std::string piece =
            tokenToPiece(
                vocab,
                token
            );

        output +=
            piece;

        llama_batch nextBatch =
            llama_batch_get_one(
                &token,
                1
            );

        if (
            llama_decode(
                ctx,
                nextBatch
            ) != 0
        ) {

            LOGE(
                "decode failed after %d generated tokens",
                generated
            );

            break;
        }
    }

    llama_sampler_free(
        sampler
    );

    llama_free(
        ctx
    );

    if (
        output.empty()
    ) {

        return env->NewStringUTF(
            "[GASP_AI_ERROR] empty_generation"
        );
    }

    return env->NewStringUTF(
        output.c_str()
    );
}
