package com.kayanx.android.native

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Kotlin façade over the NDK llama.cpp engine.
 * Fully replaceable: any future backend implements the same contract.
 */
class LlamaEngine {

    companion object {
        private const val TAG = "LlamaEngine"
        init {
            try {
                System.loadLibrary("kayan_jni")
                Log.i(TAG, "Native library loaded")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Failed to load kayan_jni — native features unavailable", e)
            }
        }
    }

    data class Config(
        val nCtx: Int = 4096,
        val nThreads: Int = 6,
        val nBatch: Int = 512,
        val nGpuLayers: Int = 0   // 0 until Auto Profiler runs
    )

    data class Benchmark(
        val modelLoadMs: Double,
        val tokensPerSec: Double,
        val firstTokenLatencyMs: Double,
        val memoryBytes: Long
    )

    private var initialized = false

    fun init() {
        if (!initialized) {
            nativeInit()
            initialized = true
        }
    }

    fun shutdown() {
        if (initialized) {
            nativeShutdown()
            initialized = false
        }
    }

    suspend fun loadModel(path: String, config: Config): Boolean = withContext(Dispatchers.IO) {
        init()
        nativeLoadModel(path, config.nCtx, config.nThreads, config.nBatch, config.nGpuLayers)
    }

    fun unload() = nativeUnload()

    fun isLoaded(): Boolean = nativeIsLoaded()

    suspend fun complete(prompt: String, maxTokens: Int = 256, temperature: Float = 0.7f): String =
        withContext(Dispatchers.IO) {
            nativeComplete(prompt, maxTokens, temperature)
        }

    /**
     * Auto Backend Profiler.
     * Tests increasing GPU layers and returns the highest stable value.
     * Caller should persist the result.
     */
    suspend fun profileGpuLayers(modelPath: String, maxLayersToTry: Int = 99): Int =
        withContext(Dispatchers.IO) {
            init()
            nativeProfileGpuLayers(modelPath, maxLayersToTry)
        }

    fun getBenchmark(): Benchmark {
        val arr = nativeGetBenchmark()
        return Benchmark(
            modelLoadMs = arr.getOrElse(0) { 0.0 },
            tokensPerSec = arr.getOrElse(1) { 0.0 },
            firstTokenLatencyMs = arr.getOrElse(2) { 0.0 },
            memoryBytes = arr.getOrElse(3) { 0.0 }.toLong()
        )
    }

    // ─── JNI ────────────────────────────────────────────────────────────────
    private external fun nativeInit(): Boolean
    private external fun nativeShutdown()
    private external fun nativeLoadModel(path: String, nCtx: Int, nThreads: Int, nBatch: Int, nGpuLayers: Int): Boolean
    private external fun nativeUnload()
    private external fun nativeIsLoaded(): Boolean
    private external fun nativeComplete(prompt: String, maxTokens: Int, temperature: Float): String
    private external fun nativeProfileGpuLayers(path: String, maxLayers: Int): Int
    private external fun nativeGetBenchmark(): DoubleArray
}
