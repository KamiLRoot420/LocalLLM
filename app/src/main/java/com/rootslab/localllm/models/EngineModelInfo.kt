package com.rootslab.localllm.models

import kotlinx.serialization.Serializable

/**
 * Metadata reported by the native engine for the currently loaded model.
 * Mirrors the JSON document produced by `nativeGetModelInfo()`.
 */
@Serializable
data class EngineModelInfo(
    val description: String = "",
    val n_vocab: Int = 0,
    val n_ctx_train: Int = 0,
    val n_embd: Int = 0,
    val n_layer: Int = 0,
    val n_params: Long = 0,
    val size_bytes: Long = 0,
    val n_ctx: Int = 0
) {
    val parameterCountHuman: String
        get() = when {
            n_params >= 1_000_000_000L -> String.format("%.2f B", n_params / 1_000_000_000.0)
            n_params >= 1_000_000L -> String.format("%.1f M", n_params / 1_000_000.0)
            else -> n_params.toString()
        }

    val sizeHuman: String
        get() {
            val gb = size_bytes / (1024.0 * 1024.0 * 1024.0)
            return if (gb >= 1.0) String.format("%.2f GB", gb) else String.format("%.0f MB", size_bytes / (1024.0 * 1024.0))
        }
}
