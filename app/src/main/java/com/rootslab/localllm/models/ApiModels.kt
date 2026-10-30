package com.rootslab.localllm.models

import kotlinx.serialization.Serializable

// ---------------------------------------------------------------------------
// OpenAI-compatible wire format
// ---------------------------------------------------------------------------

@Serializable
data class ChatCompletionRequest(
    val model: String = "local-gguf",
    val messages: List<Message> = emptyList(),
    val temperature: Float? = null,
    val top_p: Float? = null,
    val top_k: Int? = null,
    val repetition_penalty: Float? = null,
    val n: Int? = null,
    val stream: Boolean? = null,
    val max_tokens: Int? = null,
    val stop: List<String>? = null
) {
    @Serializable
    data class Message(
        val role: String,
        val content: String
    )
}

@Serializable
data class ChatCompletionResponse(
    val id: String,
    val `object`: String = "chat.completion.chunk",
    val created: Long,
    val model: String,
    val choices: List<Choice>
) {
    @Serializable
    data class Choice(
        val index: Int,
        val delta: Delta,
        val finish_reason: String? = null
    ) {
        @Serializable
        data class Delta(
            val role: String? = null,
            val content: String? = null
        )
    }
}

@Serializable
data class ModelInfo(
    val id: String,
    val `object`: String = "model",
    val created: Long = 0L,
    val owned_by: String = "rootslab"
)

@Serializable
data class ModelsList(
    val data: List<ModelInfo>
)

@Serializable
data class ApiError(
    val error: ErrorBody
) {
    @Serializable
    data class ErrorBody(
        val message: String,
        val type: String = "invalid_request_error",
        val code: String? = null
    )
}
