package com.rootslab.localllm.models

import kotlinx.serialization.Serializable

/** A single turn of the built-in offline chat. */
@Serializable
data class ChatMessage(
    val role: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val tokensPerSecond: Float = 0f,
    val promptEvalMs: Long = 0L,
    val promptTokens: Int = 0,
    val completionTokens: Int = 0
) {
    companion object {
        const val ROLE_USER = "user"
        const val ROLE_ASSISTANT = "assistant"
        const val ROLE_SYSTEM = "system"
    }
}
