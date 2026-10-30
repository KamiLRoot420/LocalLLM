package com.rootslab.localllm.models

/**
 * A single conversation turn rendered by the in-app chat.
 */
data class ChatMessage(
    val id: Long,
    val role: Role,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val tokensPerSecond: Float = 0f,
    val promptEvalMs: Long = 0L,
    val promptTokens: Int = 0,
    val isStreaming: Boolean = false
) {
    enum class Role { USER, ASSISTANT, SYSTEM }

    companion object {
        private var counter = 0L

        fun nextId(): Long = ++counter
    }
}
