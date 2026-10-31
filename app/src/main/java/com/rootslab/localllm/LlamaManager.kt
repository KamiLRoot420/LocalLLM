package com.rootslab.localllm

import android.util.Log
import com.rootslab.localllm.models.ServerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Metadata reported by the native engine for the currently loaded model.
 */
data class ModelRuntimeInfo(
    val description: String = "",
    val nVocab: Int = 0,
    val nCtxTrain: Int = 0,
    val nEmbedd: Int = 0,
    val nLayer: Int = 0,
    val nParams: Long = 0,
    val sizeBytes: Long = 0,
    val nCtx: Int = 0
) {
    val parameterCountLabel: String
        get() = when {
            nParams >= 1_000_000_000L -> String.format("%.1fB", nParams / 1_000_000_000.0)
            nParams >= 1_000_000L -> String.format("%.1fM", nParams / 1_000_000.0)
            else -> nParams.toString()
        }
}

/**
 * Thin, thread-safe facade over the native llama.cpp engine.
 *
 * All JNI calls are funnelled through this object so that the native library
 * is loaded exactly once and the loading/unloading of models stays in a single
 * place (guaranteeing the llama_free flush before a new model is loaded).
 */
object LlamaManager {

    private const val TAG = "LlamaManager"

    @Volatile
    var isLoaded: Boolean = false
        private set

    @Volatile
    var lastError: String? = null
        private set

    @Volatile
    private var nativeLibLoaded = false

    init {
        loadNativeLibrary()
        if (nativeLibLoaded) {
            nativeInit()
        }
    }

    /**
     * Loads the native library with a guard against UnsatisfiedLinkError.
     * The library is loaded exactly once per process.
     */
    private fun loadNativeLibrary() {
        if (nativeLibLoaded) return
        try {
            System.loadLibrary("native-bridge")
            nativeLibLoaded = true
            Log.i(TAG, "native-bridge library loaded successfully")
        } catch (e: UnsatisfiedLinkError) {
            lastError = "Failed to load native library: ${e.message}. " +
                "Ensure the NDK build produced libnative-bridge.so for the device ABI."
            Log.e(TAG, lastError!!, e)
        } catch (e: Exception) {
            lastError = "Unexpected error loading native library: ${e.message}"
            Log.e(TAG, lastError!!, e)
        }
    }

    // --- native declarations ------------------------------------------------

    private external fun nativeInit(): Boolean
    private external fun nativeShutdown()
    private external fun nativeFree()
    private external fun nativeLoadModel(
        path: String,
        ngl: Int,
        ctxSize: Int,
        nThreads: Int,
        seed: Int
    ): Boolean
    private external fun nativeGetContextSize(): Int
    private external fun nativeGetTokenCount(): Int
    private external fun nativeGenerateStart(
        prompt: String,
        temperature: Float,
        topK: Int,
        topP: Float,
        repeatPenalty: Float
    ): Boolean
    private external fun nativeGenerateNext(
        temperature: Float,
        topK: Int,
        topP: Float,
        repeatPenalty: Float,
        maxTokens: Int
    ): String?
    private external fun nativeGenerateStop()
    private external fun nativeIsGenerating(): Boolean
    private external fun nativeIsLoaded(): Boolean
    private external fun nativeGetModelInfo(): String
    private external fun nativeGetLastError(): String?

    // --- public API ---------------------------------------------------------

    /**
     * Loads a GGUF model. Any previously loaded model is released first
     * (explicit llama_free / llama_free_model flush) to avoid VRAM/RAM leaks.
     */
    suspend fun loadModel(path: String, config: ServerConfig): Boolean =
        withContext(Dispatchers.IO) {
            if (!nativeLibLoaded) {
                lastError = "Native library not loaded"
                return@withContext false
            }
            if (path.isBlank()) {
                lastError = "No model selected"
                return@withContext false
            }

            val ctxSize = config.ctxSize.coerceIn(512, 131_072)
            val threads = config.threads.coerceIn(1, 32)

            Log.i(TAG, "Loading model path=$path ngl=${config.ngl} ctx=$ctxSize threads=$threads")

            val success = nativeLoadModel(path, config.ngl, ctxSize, threads, config.seed)
            isLoaded = success
            lastError = if (success) null else (nativeGetLastError() ?: "Failed to load model")

            if (success) {
                Log.i(TAG, "Model loaded: ${modelInfo().description}")
            } else {
                Log.e(TAG, "Model load failed: $lastError")
            }
            success
        }

    /** Releases the model and all associated buffers. */
    suspend fun unloadModel() = withContext(Dispatchers.IO) {
        if (nativeLibLoaded) {
            nativeFree()
        }
        isLoaded = false
        lastError = null
        Log.i(TAG, "Model unloaded")
    }

    fun contextSize(): Int = if (nativeLibLoaded) nativeGetContextSize() else 0

    fun tokenCount(): Int = if (nativeLibLoaded) nativeGetTokenCount() else 0

    fun isGenerating(): Boolean = if (nativeLibLoaded) nativeIsGenerating() else false

    fun stopGeneration() {
        if (nativeLibLoaded) {
            nativeGenerateStop()
        }
    }

    fun modelInfo(): ModelRuntimeInfo {
        if (!nativeLibLoaded) return ModelRuntimeInfo()
        val raw = nativeGetModelInfo()
        if (raw.isBlank() || raw == "{}") return ModelRuntimeInfo()
        return runCatching {
            val json = Json.parseToJsonElement(raw).jsonObject
            ModelRuntimeInfo(
                description = json["description"]?.jsonPrimitive?.content.orEmpty(),
                nVocab = json["n_vocab"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                nCtxTrain = json["n_ctx_train"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                nEmbedd = json["n_embd"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                nLayer = json["n_layer"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                nParams = json["n_params"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L,
                sizeBytes = json["size_bytes"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L,
                nCtx = json["n_ctx"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
            )
        }.getOrDefault(ModelRuntimeInfo())
    }

    /**
     * Streams the completion of [prompt] token by token.
     *
     * The flow is cold: collecting it starts a new generation. Cancelling the
     * collector stops the native generation as well.
     */
    fun generateStream(prompt: String, config: ServerConfig): Flow<String> = flow {
        if (!nativeLibLoaded) {
            lastError = "Native library not loaded"
            Log.e(TAG, "generateStream: $lastError")
            return@flow
        }
        val started = nativeGenerateStart(
            prompt,
            config.temperature,
            config.topK,
            config.topP,
            config.repeatPenalty
        )
        if (!started) {
            lastError = nativeGetLastError() ?: "Generation could not be started"
            Log.e(TAG, "generateStream failed: $lastError")
            return@flow
        }

        try {
            while (isActive) {
                val piece = nativeGenerateNext(
                    config.temperature,
                    config.topK,
                    config.topP,
                    config.repeatPenalty,
                    config.maxTokens
                ) ?: break
                if (piece.isNotEmpty()) {
                    emit(piece)
                }
            }
        } finally {
            nativeGenerateStop()
        }
    }
}