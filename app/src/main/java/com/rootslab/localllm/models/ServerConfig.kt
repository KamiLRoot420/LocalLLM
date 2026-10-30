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
    val port: Int = DEFAULT_PORT,
    val wakeLock: Boolean = false
) {
    val hasModel: Boolean get() = modelPath.isNotBlank()

    companion object {
        /** Default TCP port of the embedded OpenAI-compatible server. */
        const val DEFAULT_PORT = 8080

        /** Balanced profile: a safe default for mid-range devices. */
        val BALANCED = ServerConfig(
            ngl = 32,
            ctxSize = 4096,
            threads = 4,
            temperature = 0.7f,
            topK = 40,
            topP = 0.95f,
            repeatPenalty = 1.1f
        )

        /** Profiles shipped with the application; users can add their own. */
        val BUILT_IN_PRESETS: Map<String, ServerConfig> = mapOf(
            "Max Performance" to ServerConfig(
                ngl = 99, ctxSize = 8192, threads = 8,
                temperature = 0.7f, topK = 40, topP = 0.95f, repeatPenalty = 1.1f
            ),
            "Balanced" to BALANCED,
            "Battery Saver" to ServerConfig(
                ngl = 0, ctxSize = 2048, threads = 2,
                temperature = 0.7f, topK = 40, topP = 0.95f, repeatPenalty = 1.1f
            )
        )
    }
}
