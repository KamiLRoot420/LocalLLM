package com.rootslab.localllm.util

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import com.rootslab.localllm.models.ServerConfig
import java.io.File
import kotlin.math.max
import kotlin.math.min

/**
 * Hardware probing and the out-of-memory guard.
 *
 * The guard derives a safe configuration from three inputs: the physical RAM
 * reported by the system, the currently available RAM and the KV-cache footprint
 * implied by the GGUF metadata of the selected model.
 */
object DeviceInfo {

    private const val BYTES_PER_MB = 1024L * 1024L

    fun memoryInfo(context: Context): ActivityManager.MemoryInfo {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return ActivityManager.MemoryInfo().also { manager.getMemoryInfo(it) }
    }

    fun totalRamBytes(context: Context): Long = memoryInfo(context).totalMem

    fun availableRamBytes(context: Context): Long = memoryInfo(context).availMem

    fun isLowMemory(context: Context): Boolean = memoryInfo(context).lowMemory

    fun deviceSummary(context: Context): String {
        val ramMb = totalRamBytes(context) / BYTES_PER_MB
        val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"
        return "Android ${Build.VERSION.RELEASE} • $abi • ${ramMb} MB RAM • ${Runtime.getRuntime().availableProcessors()} CPU"
    }

    /**
     * Builds a configuration that is safe for the current device and model.
     *
     * @param modelPath optional path to a GGUF file; when readable, its metadata
     *   is used to size the KV cache precisely.
     */
    fun recommendConfig(context: Context, modelPath: String?): ServerConfig {
        val info = memoryInfo(context)
        val totalRam = info.totalMem
        val availableRam = info.availMem
        val metadata = modelPath?.takeIf { it.isNotBlank() }?.let { GgufInspector.inspect(it) }

        // --- GPU offload -----------------------------------------------------
        // Vulkan offload needs headroom for the model weights. Keep at least
        // 2 GB of RAM free for the OS when the model is large.
        val modelSizeBytes = modelPath?.takeIf { it.isNotBlank() }
            ?.let { File(it).length() } ?: 0L
        val ngl = when {
            totalRam < 4L * BYTES_PER_MB * 1024 -> 0
            modelSizeBytes > 6L * BYTES_PER_MB * 1024 -> 0
            totalRam < 6L * BYTES_PER_MB * 1024 -> 16
            else -> 99
        }

        // --- Context window --------------------------------------------------
        val threads = Runtime.getRuntime().availableProcessors().coerceIn(2, 8)
        val ctxSize = recommendContextSize(availableRam, metadata)

        return ServerConfig(
            ngl = ngl,
            ctxSize = ctxSize,
            threads = threads,
            temperature = ServerConfig.BALANCED.temperature,
            topK = ServerConfig.BALANCED.topK,
            topP = ServerConfig.BALANCED.topP,
            repeatPenalty = ServerConfig.BALANCED.repeatPenalty,
            maxTokens = min(512, ctxSize / 4),
            port = ServerConfig.DEFAULT_PORT
        )
    }

    /**
     * Largest context window whose KV cache still fits into the memory budget.
     */
    fun recommendContextSize(availableRamBytes: Long, metadata: GgufMetadata?): Int {
        val budget = (availableRamBytes * 0.35).toLong()
        if (metadata == null || metadata.blockCount <= 0 || metadata.embeddingLength <= 0) {
            return when {
                availableRamBytes < 2L * BYTES_PER_MB * 1024 -> 1024
                availableRamBytes < 4L * BYTES_PER_MB * 1024 -> 2048
                else -> 4096
            }
        }

        val bytesPerToken = 2L * metadata.blockCount * metadata.embeddingLength * 2L
        if (bytesPerToken <= 0) return 2048

        val maxByBudget = (budget / bytesPerToken).toInt()
        val maxByModel = metadata.contextLength.takeIf { it > 0 } ?: maxByBudget
        val limit = min(maxByBudget, maxByModel)

        return floorToPowerOfTwo(limit.coerceIn(512, 32768))
    }

    private fun floorToPowerOfTwo(value: Int): Int {
        if (value <= 0) return 512
        var result = 1
        while (result * 2 <= value) result *= 2
        return max(result, 512)
    }
}
