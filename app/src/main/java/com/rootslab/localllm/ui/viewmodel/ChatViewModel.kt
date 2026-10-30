package com.rootslab.localllm.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rootslab.localllm.LlamaManager
import com.rootslab.localllm.di.ServiceLocator
import com.rootslab.localllm.models.ChatMessage
import com.rootslab.localllm.models.ServerConfig
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class ChatViewModel : ViewModel() {

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _contextUsage = MutableStateFlow(0f)
    val contextUsage: StateFlow<Float> = _contextUsage.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var generationJob: Job? = null

    fun sendMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || _isGenerating.value) return

        _error.value = null
        _messages.value = _messages.value + ChatMessage(
            id = ChatMessage.nextId(),
            role = ChatMessage.Role.USER,
            content = trimmed
        )

        generationJob = viewModelScope.launch {
            _isGenerating.value = true

            val config = ServiceLocator.dataStore.currentConfig()
            val prompt = buildPrompt()
            val assistantId = ChatMessage.nextId()
            val startedAt = System.currentTimeMillis()

            var content = StringBuilder()
            var tokenCount = 0

            try {
                LlamaManager.generateStream(prompt, config).collect { piece ->
                    content.append(piece)
                    tokenCount++
                    upsertAssistantMessage(assistantId, content.toString(), isStreaming = true)
                    updateContextUsage()
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Generation failed"
            } finally {
                val elapsedMs = System.currentTimeMillis() - startedAt
                val seconds = elapsedMs / 1000f
                val tps = if (seconds > 0f) (tokenCount / seconds).roundToInt().toFloat() else 0f

                upsertAssistantMessage(
                    id = assistantId,
                    content = content.toString(),
                    isStreaming = false,
                    tokensPerSecond = tps,
                    promptEvalMs = elapsedMs
                )
                _isGenerating.value = false
            }
        }
    }

    fun stopGeneration() {
        generationJob?.cancel()
        generationJob = null
        LlamaManager.stopGeneration()
        _isGenerating.value = false
    }

    fun regenerateLast() {
        val lastUser = _messages.value.lastOrNull { it.role == ChatMessage.Role.USER } ?: return
        _messages.value = _messages.value.dropLastWhile { it.role == ChatMessage.Role.ASSISTANT }
        sendMessage(lastUser.content)
    }

    fun clearChat() {
        stopGeneration()
        _messages.value = emptyList()
        _contextUsage.value = 0f
    }

    fun consumeError() {
        _error.value = null
    }

    private fun upsertAssistantMessage(
        id: Long,
        content: String,
        isStreaming: Boolean,
        tokensPerSecond: Float = 0f,
        promptEvalMs: Long = 0L
    ) {
        val current = _messages.value.toMutableList()
        val index = current.indexOfFirst { it.id == id }
        val message = ChatMessage(
            id = id,
            role = ChatMessage.Role.ASSISTANT,
            content = content,
            tokensPerSecond = tokensPerSecond,
            promptEvalMs = promptEvalMs,
            isStreaming = isStreaming
        )
        if (index >= 0) {
            current[index] = message
        } else {
            current.add(message)
        }
        _messages.value = current
    }

    private fun updateContextUsage() {
        val total = LlamaManager.contextSize()
        val used = LlamaManager.tokenCount()
        _contextUsage.value = if (total > 0) (used.toFloat() / total).coerceIn(0f, 1f) else 0f
    }

    private fun buildPrompt(): String {
        val system = "You are a helpful assistant running fully offline on the user's device."
        val history = _messages.value
            .filter { it.role != ChatMessage.Role.SYSTEM }
            .joinToString("\n") { message ->
                val role = when (message.role) {
                    ChatMessage.Role.USER -> "user"
                    ChatMessage.Role.ASSISTANT -> "assistant"
                    ChatMessage.Role.SYSTEM -> "system"
                }
                "$role: ${message.content}"
            }
        return "$system\n$history\nassistant:"
    }

    override fun onCleared() {
        super.onCleared()
        LlamaManager.stopGeneration()
    }
}
