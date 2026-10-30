package com.rootslab.localllm.models

import kotlinx.serialization.Serializable

/**
 * Complete runtime configuration of the local inference engine.
 *
 * The same object drives the embedded HTTP server, the in-app chat and the
 * native bridge, so it is the single source of truth for every tunable.
 */
@Serializable
data class ServerConfig(
    val modelPath: String = "",
    val ngl: Int = 32,
    val ctxSize: Int = 4096,
    val threads: Int = 4,
    val temperature: Float = 0.7f,
    val topK: Int = 40,
    val topP: Float = 0.9f,
    val repeatPenalty: Float = 1.1f,
    val seed: Int = -1,
    val maxTokens: Int = 512,
    val port: Int = 8080
) {
    companion object {
        const val DEFAULT_PORT = 8080
        val FALLBACK_PORTS = listOf(8080, 8081, 8082, 8090, 11434)

        val MAX_PERFORMANCE = ServerConfig(
            ngl = 99,
            ctxSize = 8192,
            threads = 8,
            temperature = 0.7f,
            topK = 40,
            topP = 0.9f,
            repeatPenalty = 1.1f
        )

        val BALANCED = ServerConfig(
            ngl = 32,
            ctxSize = 4096,
            threads = 4,
            temperature = 0.7f,
            topK = 40,
            topP = 0.9f,
            repeatPenalty = 1.1f
        )

        val BATTERY_SAVER = ServerConfig(
            ngl = 0,
            ctxSize = 2048,
            threads = 2,
            temperature = 0.7f,
            topK = 40,
            topP = 0.9f,
            repeatPenalty = 1.1f
        )

        val BUILT_IN_PRESETS = linkedMapOf(
            "Max Performance" to MAX_PERFORMANCE,
            "Balanced" to BALANCED,
            "Battery Saver" to BATTERY_SAVER
        )
    }
}
