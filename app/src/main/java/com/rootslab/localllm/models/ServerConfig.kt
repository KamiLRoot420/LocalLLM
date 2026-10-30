package com.rootslab.localllm.models

import kotlinx.serialization.Serializable

/**
 * Complete runtime configuration of the local inference engine.
 *
 * The field names mirror the llama.cpp command line flags so that the mapping
 * stays obvious: [ngl] -> -ngl, [ctxSize] -> -c, [threads] -> -t.
 */
@Serializable
data class ServerConfig(
    val modelPath: String = "",
    val modelName: String = "",
    val ngl: Int = 32,
    val ctxSize: Int = 4096,
    val threads: Int = 4,
    val temperature: Float = 0.7f,
    val topK: Int = 40,
    val topP: Float = 0.95f,
    val repeatPenalty: Float = 1.1f,
    val seed: Int = -1,
    val maxTokens: Int = 512,
    val port: Int = 8080,
    val wakeLock: Boolean = false
) {
    val hasModel: Boolean get() = modelPath.isNotBlank()
}
