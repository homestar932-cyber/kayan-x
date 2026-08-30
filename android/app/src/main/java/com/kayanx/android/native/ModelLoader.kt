package com.kayanx.android.native

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Loads a GGUF model from a SAF content URI into a path the native engine can open.
 *
 * Strategy:
 * 1. Prefer opening a ParcelFileDescriptor and (when possible) using the real path.
 * 2. Fallback: copy into app's internal files dir (models/) so llama.cpp can mmap it.
 *
 * The model is NEVER packaged inside the APK.
 */
class ModelLoader(
    private val context: Context,
    private val engine: LlamaEngine
) {
    companion object {
        private const val TAG = "ModelLoader"
        private const val MODELS_DIR = "models"
    }

    data class LoadResult(
        val success: Boolean,
        val localPath: String?,
        val message: String,
        val usedCopy: Boolean
    )

    suspend fun loadFromUri(
        uri: Uri,
        config: LlamaEngine.Config,
        displayName: String? = null
    ): LoadResult = withContext(Dispatchers.IO) {
        try {
            val name = displayName ?: queryDisplayName(uri) ?: "model.gguf"
            val safeName = name.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            if (!safeName.endsWith(".gguf", ignoreCase = true)) {
                return@withContext LoadResult(false, null, "الملف ليس GGUF", false)
            }

            // Always copy to internal storage for reliable mmap by llama.cpp.
            // (Direct fd passing can be added later; copy is the reliable baseline.)
            val modelsDir = File(context.filesDir, MODELS_DIR).apply { mkdirs() }
            val target = File(modelsDir, safeName)

            // Skip re-copy if same size already present
            val size = querySize(uri)
            if (target.exists() && size > 0 && target.length() == size) {
                Log.i(TAG, "Reusing cached model: ${target.absolutePath}")
            } else {
                Log.i(TAG, "Copying model to ${target.absolutePath}")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(target).use { output ->
                        input.copyTo(output, bufferSize = 1024 * 1024)
                    }
                } ?: return@withContext LoadResult(false, null, "تعذر فتح الملف", false)
            }

            val ok = engine.loadModel(target.absolutePath, config)
            if (ok) {
                LoadResult(true, target.absolutePath, "تم التحميل بنجاح", true)
            } else {
                LoadResult(false, target.absolutePath, "فشل native loadModel", true)
            }
        } catch (e: Exception) {
            Log.e(TAG, "loadFromUri failed", e)
            LoadResult(false, null, e.message ?: "خطأ غير معروف", false)
        }
    }

    suspend fun profileAndLoad(
        uri: Uri,
        baseConfig: LlamaEngine.Config,
        maxLayers: Int = 99
    ): LoadResult = withContext(Dispatchers.IO) {
        // First ensure file is local
        val prep = loadFromUri(uri, baseConfig.copy(nGpuLayers = 0))
        if (!prep.success || prep.localPath == null) return@withContext prep

        // Run auto profiler
        val bestLayers = engine.profileGpuLayers(prep.localPath, maxLayers)
        val finalConfig = baseConfig.copy(nGpuLayers = bestLayers)
        val ok = engine.loadModel(prep.localPath, finalConfig)
        LoadResult(
            success = ok,
            localPath = prep.localPath,
            message = if (ok) "تم التحميل مع n_gpu_layers=$bestLayers (Auto Profiler)" else "فشل بعد الـProfiler",
            usedCopy = true
        )
    }

    fun clearCachedModels() {
        File(context.filesDir, MODELS_DIR).listFiles()?.forEach { it.delete() }
    }

    private fun queryDisplayName(uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        } catch (_: Exception) { null }
    }

    private fun querySize(uri: Uri): Long {
        return try {
            context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.SIZE), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getLong(0) else -1L } ?: -1L
        } catch (_: Exception) { -1L }
    }
}
