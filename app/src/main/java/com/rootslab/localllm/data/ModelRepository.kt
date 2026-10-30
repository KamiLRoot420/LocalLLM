package com.rootslab.localllm.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Manages the GGUF models available to the application.
 *
 * The Storage Access Framework returns a content:// URI which the native
 * engine cannot open. Every selected model is therefore copied into the
 * application's private storage, which gives the engine a stable, directly
 * readable path and keeps the model available after the URI permission
 * expires.
 */
class ModelRepository(context: Context) {

    private val appContext = context.applicationContext

    private val modelsDirectory: File by lazy {
        File(appContext.filesDir, "models").apply { mkdirs() }
    }

    /**
     * Copies the document behind [uri] into private storage.
     *
     * @return the absolute path of the imported file, or null on failure.
     */
    suspend fun importModel(uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val displayName = queryDisplayName(uri) ?: "model-${System.currentTimeMillis()}.gguf"
            val safeName = sanitize(displayName).let {
                if (it.endsWith(".gguf", ignoreCase = true)) it else "$it.gguf"
            }

            val target = uniqueTarget(safeName)
            appContext.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output ->
                    input.copyTo(output, DEFAULT_BUFFER_SIZE)
                }
            } ?: return@runCatching null

            target.absolutePath
        }.getOrNull()
    }

    /** All imported models, newest first. */
    fun listModels(): List<File> =
        modelsDirectory.listFiles()
            ?.filter { it.isFile && it.extension.equals("gguf", ignoreCase = true) }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()

    fun delete(model: File): Boolean = model.delete()

    fun sizeOf(model: File): Long = model.length()

    private fun uniqueTarget(name: String): File {
        var candidate = File(modelsDirectory, name)
        if (!candidate.exists()) return candidate

        val base = name.substringBeforeLast('.', name)
        val extension = name.substringAfterLast('.', "gguf")
        var index = 1
        while (candidate.exists()) {
            candidate = File(modelsDirectory, "$base-$index.$extension")
            index++
        }
        return candidate
    }

    private fun queryDisplayName(uri: Uri): String? =
        appContext.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }

    private fun sanitize(name: String): String =
        name.replace(Regex("[^A-Za-z0-9._-]"), "_").take(120)
}
