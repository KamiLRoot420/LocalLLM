package com.rootslab.localllm.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.rootslab.localllm.models.ChatMessage
import java.io.File

/**
 * Exports a conversation to a plain-text or Markdown file and shares it via
 * the system share sheet. The file is written to the app cache directory and
 * handed out through a FileProvider URI, so no storage permission is needed.
 */
object ConversationExporter {

    enum class Format(val extension: String, val mimeType: String) {
        TXT("txt", "text/plain"),
        MD("md", "text/markdown")
    }

    fun export(context: Context, messages: List<ChatMessage>, format: Format): Uri? {
        val content = render(messages, format)
        val file = File(context.cacheDir, "localllm-conversation-${System.currentTimeMillis()}.${format.extension}")
        return runCatching {
            file.writeText(content)
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
        }.getOrNull()
    }

    fun shareIntent(uri: Uri, format: Format): Intent =
        Intent(Intent.ACTION_SEND).apply {
            type = format.mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

    private fun render(messages: List<ChatMessage>, format: Format): String = buildString {
        append("# LocalLLM AI Server - Conversation\n")
        append("Exported: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}\n")
        append("Messages: ${messages.size}\n\n")

        messages.forEach { message ->
            val role = when (message.role) {
                ChatMessage.Role.USER -> "User"
                ChatMessage.Role.ASSISTANT -> "Assistant"
                ChatMessage.Role.SYSTEM -> "System"
            }
            when (format) {
                Format.TXT -> {
                    append("[$role]\n")
                    append(message.content)
                    append("\n\n")
                }

                Format.MD -> {
                    append("### $role\n\n")
                    append(message.content)
                    append("\n\n")
                    if (message.tokensPerSecond > 0f) {
                        append("_${message.tokensPerSecond.toInt()} tok/s_\n\n")
                    }
                }
            }
        }
    }
}
