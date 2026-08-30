package com.kayanx.android.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.kayanx.android.native.LlamaEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore("kayan_engine_settings")

/**
 * Runtime-configurable engine parameters.
 * n_gpu_layers is owned by Auto Profiler but can be overridden manually.
 */
class EngineSettings(private val context: Context) {

    private val KEY_N_CTX = intPreferencesKey("n_ctx")
    private val KEY_N_THREADS = intPreferencesKey("n_threads")
    private val KEY_N_BATCH = intPreferencesKey("n_batch")
    private val KEY_N_GPU_LAYERS = intPreferencesKey("n_gpu_layers")
    private val KEY_PRESET = stringPreferencesKey("preset")
    private val KEY_GPU_OVERRIDE = intPreferencesKey("gpu_override") // -1 = use profiler value

    data class Values(
        val nCtx: Int = 4096,
        val nThreads: Int = 6,
        val nBatch: Int = 512,
        val nGpuLayers: Int = 0,
        val preset: String = "3B",
        val gpuOverride: Int = -1
    )

    val values: Flow<Values> = context.settingsStore.data.map { p ->
        Values(
            nCtx = p[KEY_N_CTX] ?: 4096,
            nThreads = p[KEY_N_THREADS] ?: 6,
            nBatch = p[KEY_N_BATCH] ?: 512,
            nGpuLayers = p[KEY_N_GPU_LAYERS] ?: 0,
            preset = p[KEY_PRESET] ?: "3B",
            gpuOverride = p[KEY_GPU_OVERRIDE] ?: -1
        )
    }

    suspend fun current(): Values = values.first()

    suspend fun update(block: (Values) -> Values) {
        val cur = current()
        val next = block(cur)
        context.settingsStore.edit { p ->
            p[KEY_N_CTX] = next.nCtx
            p[KEY_N_THREADS] = next.nThreads
            p[KEY_N_BATCH] = next.nBatch
            p[KEY_N_GPU_LAYERS] = next.nGpuLayers
            p[KEY_PRESET] = next.preset
            p[KEY_GPU_OVERRIDE] = next.gpuOverride
        }
    }

    suspend fun applyPreset(name: String) {
        // Presets are starting points only. Auto Profiler still decides real gpu layers.
        val (ctx, threads, batch) = when (name) {
            "1.5B" -> Triple(2048, 4, 256)
            "7B"   -> Triple(4096, 6, 512)
            else   -> Triple(4096, 6, 512) // 3B default
        }
        update {
            it.copy(preset = name, nCtx = ctx, nThreads = threads, nBatch = batch)
        }
    }

    suspend fun toEngineConfig(): LlamaEngine.Config {
        val v = current()
        val gpu = if (v.gpuOverride >= 0) v.gpuOverride else v.nGpuLayers
        return LlamaEngine.Config(
            nCtx = v.nCtx,
            nThreads = v.nThreads,
            nBatch = v.nBatch,
            nGpuLayers = gpu
        )
    }

    suspend fun saveProfilerResult(layers: Int) {
        update { it.copy(nGpuLayers = layers) }
    }
}
