package com.rootslab.localllm.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Imports a model picked through the Storage Access Framework into the app's
 * private storage.
 *
 * llama.cpp memory-maps the model file, therefore it needs a real filesystem
 * path rather than a `content://` URI. Copying the file also guarantees that
 * the model stays readable after the user revokes the SAF grant.
 */
class ModelRepository(private val context: Context) {

    private val modelsDir: File
        get() = File(context.filesDir, "models").apply { if (!exists()) mkdirs() }

    suspend fun import(uri: Uri): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val displayName = queryDisplayName(uri) ?: "model.gguf"
            val safeName = sanitize(displayName)
            val target = File(modelsDir, safeName)

            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output, DEFAULT_BUFFER_SIZE) }
            } ?: error("Cannot open the selected file")

            target
        }
    }

    suspend fun delete(file: File): Boolean = withContext(Dispatchers.IO) {
        file.delete()
    }

    fun listModels(): List<File> = modelsDir.listFiles()?.sortedBy { it.name } ?: emptyList()

    private fun queryDisplayName(uri: Uri): String? {
        return context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null, null, null
        )?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }
    }

    private fun sanitize(name: String): String {
        val cleaned = name.replace(Regex("[^A-Za-z0-9._-]"), "_")
        return if (cleaned.endsWith(".gguf", ignoreCase = true)) cleaned else "$cleaned.gguf"
    }

    companion object {
        private const val DEFAULT_BUFFER_SIZE = 64 * 1024
    }
}
