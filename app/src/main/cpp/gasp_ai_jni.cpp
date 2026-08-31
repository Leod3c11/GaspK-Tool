#include <jni.h>
#include <string>

#include "llama.h"

extern "C"
JNIEXPORT jstring JNICALL
Java_com_exynosbess_app_ai_GaspAiNative_nativeVersion(
        JNIEnv *env,
        jobject /* thiz */) {

    std::string version =
        "llama.cpp native bridge ready";

    return env->NewStringUTF(
        version.c_str()
    );
}
