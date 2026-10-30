package com.rootslab.localllm.models

import kotlinx.serialization.Serializable

// ---------------------------------------------------------------------------
// OpenAI-compatible wire format
// ---------------------------------------------------------------------------

@Serializable
data class ChatCompletionRequest(
    val model: String = "local-gguf",
    val messages: List<ChatCompletionMessage> = emptyList(),
    val temperature: Float? = null,
    val top_p: Float? = null,
    val top_k: Int? = null,
    val presence_penalty: Float? = null,
    val frequency_penalty: Float? = null,
    /** llama.cpp style repetition penalty (1.0 disables the penalty). */
    val repetition_penalty: Float? = null,
    val n: Int? = null,
    val stream: Boolean? = null,
    val max_tokens: Int? = null,
    val stop: List<String>? = null
)

@Serializable
data class ChatCompletionMessage(
    val role: String,
    val content: String
)

@Serializable
data class ChatCompletionChunk(
    val id: String,
    val `object`: String = "chat.completion.chunk",
    val created: Long,
    val model: String,
    val choices: List<ChunkChoice>
)

@Serializable
data class ChunkChoice(
    val index: Int = 0,
    val delta: ChunkDelta,
    val finish_reason: String? = null
)

@Serializable
data class ChunkDelta(
    val role: String? = null,
    val content: String? = null
)

@Serializable
data class ModelList(
    val object: String = "list",
    val data: List<ModelDescriptor>
)

@Serializable
data class ModelDescriptor(
    val id: String,
    val `object`: String = "model",
    val created: Long = 0,
    val owned_by: String = "rootslab"
)

@Serializable
data class ErrorResponse(
    val error: ErrorBody
)

@Serializable
data class ErrorBody(
    val message: String,
    val type: String = "invalid_request_error",
    val code: String? = null
)
