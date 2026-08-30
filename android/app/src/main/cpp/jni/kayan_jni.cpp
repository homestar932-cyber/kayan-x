#include <jni.h>
#include <string>
#include "engine_wrapper.h"

static kayan::EngineWrapper* g_engine = nullptr;

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_kayanx_android_native_LlamaEngine_nativeInit(JNIEnv*, jobject) {
    if (g_engine) return JNI_TRUE;
    g_engine = new kayan::EngineWrapper();
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_kayanx_android_native_LlamaEngine_nativeShutdown(JNIEnv*, jobject) {
    if (g_engine) {
        delete g_engine;
        g_engine = nullptr;
    }
}

JNIEXPORT jboolean JNICALL
Java_com_kayanx_android_native_LlamaEngine_nativeLoadModel(
        JNIEnv* env, jobject,
        jstring jpath, jint n_ctx, jint n_threads, jint n_batch, jint n_gpu_layers) {
    if (!g_engine) return JNI_FALSE;
    const char* path = env->GetStringUTFChars(jpath, nullptr);
    kayan::EngineConfig cfg;
    cfg.n_ctx = n_ctx;
    cfg.n_threads = n_threads;
    cfg.n_batch = n_batch;
    cfg.n_gpu_layers = n_gpu_layers;
    bool ok = g_engine->loadModel(path, cfg);
    env->ReleaseStringUTFChars(jpath, path);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_kayanx_android_native_LlamaEngine_nativeUnload(JNIEnv*, jobject) {
    if (g_engine) g_engine->unload();
}

JNIEXPORT jboolean JNICALL
Java_com_kayanx_android_native_LlamaEngine_nativeIsLoaded(JNIEnv*, jobject) {
    return (g_engine && g_engine->isLoaded()) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jstring JNICALL
Java_com_kayanx_android_native_LlamaEngine_nativeComplete(
        JNIEnv* env, jobject, jstring jprompt, jint max_tokens, jfloat temperature) {
    if (!g_engine || !g_engine->isLoaded()) {
        return env->NewStringUTF("[engine not loaded]");
    }
    const char* prompt = env->GetStringUTFChars(jprompt, nullptr);
    std::string result = g_engine->complete(prompt, max_tokens, temperature);
    env->ReleaseStringUTFChars(jprompt, prompt);
    return env->NewStringUTF(result.c_str());
}

JNIEXPORT jint JNICALL
Java_com_kayanx_android_native_LlamaEngine_nativeProfileGpuLayers(
        JNIEnv* env, jobject, jstring jpath, jint max_layers) {
    if (!g_engine) return 0;
    const char* path = env->GetStringUTFChars(jpath, nullptr);
    int best = g_engine->profileGpuLayers(path, max_layers);
    env->ReleaseStringUTFChars(jpath, path);
    return best;
}

JNIEXPORT jdoubleArray JNICALL
Java_com_kayanx_android_native_LlamaEngine_nativeGetBenchmark(JNIEnv* env, jobject) {
    jdoubleArray arr = env->NewDoubleArray(4);
    if (!g_engine) return arr;
    auto b = g_engine->lastBenchmark();
    jdouble vals[4] = {b.model_load_ms, b.tokens_per_sec, b.first_token_latency_ms, (jdouble)b.memory_bytes};
    env->SetDoubleArrayRegion(arr, 0, 4, vals);
    return arr;
}

} // extern "C"
