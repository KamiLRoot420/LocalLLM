package com.rootslab.localllm.models

import kotlinx.serialization.Serializable
import java.util.concurrent.atomic.AtomicLong

/**
 * A single turn of the built-in offline chat.
 *
 * [id] is a monotonically increasing identifier that is used as the stable
 * list key in the chat UI, so that streaming updates never reorder the
 * conversation.
 */
@Serializable
data class ChatMessage(
    val id: Long = nextId(),
    val role: Role,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val tokensPerSecond: Float = 0f,
    val promptEvalMs: Long = 0L,
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val isStreaming: Boolean = false
) {
    /** Author of a chat turn. */
    enum class Role {
        USER,
        ASSISTANT,
        SYSTEM
    }

    companion object {
        private val idCounter = AtomicLong(0L)

        /** Returns a process-unique identifier for a new chat turn. */
        fun nextId(): Long = idCounter.incrementAndGet()
    }
}
