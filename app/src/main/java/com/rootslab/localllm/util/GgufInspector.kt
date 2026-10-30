package com.rootslab.localllm.util

import java.io.EOFException
import java.io.File
import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets

/**
 * Metadata extracted from the header of a GGUF file.
 */
data class GgufMetadata(
    val architecture: String,
    val contextLength: Int,
    val embeddingLength: Int,
    val blockCount: Int,
    val fileType: Int,
    val quantizationVersion: Int
) {
    val quantizationName: String
        get() = when (fileType) {
            0 -> "F32"
            1 -> "F16"
            2 -> "Q4_0"
            3 -> "Q4_1"
            7 -> "Q8_0"
            8 -> "Q5_0"
            9 -> "Q5_1"
            10 -> "Q2_K"
            11 -> "Q3_K"
            12 -> "Q4_K"
            13 -> "Q5_K"
            14 -> "Q6_K"
            15 -> "Q8_K"
            else -> "type_$fileType"
        }

    /**
     * Approximate size of the KV cache for a given context length.
     * Two tensors (K and V), fp16, per layer and per token.
     */
    fun estimateKvCacheBytes(contextLength: Int): Long {
        if (blockCount <= 0 || embeddingLength <= 0) return 0L
        return 2L * blockCount * contextLength * embeddingLength * 2L
    }
}

/**
 * Minimal GGUF header parser.
 *
 * Only the metadata key/value section is decoded; tensor payloads are never
 * touched, so parsing a multi-gigabyte model costs a few kilobytes of I/O.
 *
 * Reference: https://github.com/ggml-org/ggml/blob/master/docs/gguf.md
 */
object GgufInspector {

    private const val MAGIC = 0x46554747 // "GGUF" read as little-endian uint32

    private const val TYPE_UINT8 = 0
    private const val TYPE_INT8 = 1
    private const val TYPE_UINT16 = 2
    private const val TYPE_INT16 = 3
    private const val TYPE_UINT32 = 4
    private const val TYPE_INT32 = 5
    private const val TYPE_FLOAT32 = 6
    private const val TYPE_BOOL = 7
    private const val TYPE_STRING = 8
    private const val TYPE_ARRAY = 9
    private const val TYPE_UINT64 = 10
    private const val TYPE_INT64 = 11
    private const val TYPE_FLOAT64 = 12

    fun inspect(path: String): GgufMetadata? {
        val file = File(path)
        if (!file.isFile || file.length() < 24L) return null

        return runCatching {
            RandomAccessFile(file, "r").use { raf ->
                val reader = GgufReader(raf)

                if (reader.readU32() != MAGIC.toLong()) return null
                reader.readU32() // version
                reader.readU64() // tensor count
                val kvCount = reader.readU64()

                var architecture = ""
                var contextLength = 0
                var embeddingLength = 0
                var blockCount = 0
                var fileType = 0
                var quantizationVersion = 0

                repeat(kvCount.toInt().coerceAtMost(4096)) {
                    val key = reader.readString()
                    val type = reader.readU32().toInt()
                    val value = reader.readValue(type)

                    when {
                        key == "general.architecture" && value is String -> architecture = value
                        key == "general.file_type" && value is Long -> fileType = value.toInt()
                        key == "general.quantization_version" && value is Long ->
                            quantizationVersion = value.toInt()
                        architecture.isNotEmpty() && key == "$architecture.context_length" && value is Long ->
                            contextLength = value.toInt()
                        architecture.isNotEmpty() && key == "$architecture.embedding_length" && value is Long ->
                            embeddingLength = value.toInt()
                        architecture.isNotEmpty() && key == "$architecture.block_count" && value is Long ->
                            blockCount = value.toInt()
                    }
                }

                GgufMetadata(
                    architecture = architecture,
                    contextLength = contextLength,
                    embeddingLength = embeddingLength,
                    blockCount = blockCount,
                    fileType = fileType,
                    quantizationVersion = quantizationVersion
                )
            }
        }.getOrNull()
    }

    private class GgufReader(private val raf: RandomAccessFile) {

        fun readU8(): Int {
            val value = raf.read()
            if (value < 0) throw EOFException("unexpected end of GGUF file")
            return value
        }

        fun readU16(): Int {
            val buffer = ByteArray(2)
            raf.readFully(buffer)
            return (buffer[0].toInt() and 0xFF) or ((buffer[1].toInt() and 0xFF) shl 8)
        }

        fun readU32(): Long {
            val buffer = ByteArray(4)
            raf.readFully(buffer)
            return (buffer[0].toLong() and 0xFF) or
                ((buffer[1].toLong() and 0xFF) shl 8) or
                ((buffer[2].toLong() and 0xFF) shl 16) or
                ((buffer[3].toLong() and 0xFF) shl 24)
        }

        fun readU64(): Long {
            val buffer = ByteArray(8)
            raf.readFully(buffer)
            var value = 0L
            for (index in 7 downTo 0) {
                value = (value shl 8) or (buffer[index].toLong() and 0xFF)
            }
            return value
        }

        fun readString(): String {
            val length = readU64().toInt()
            if (length < 0 || length > 1 shl 20) throw EOFException("invalid GGUF string length")
            val buffer = ByteArray(length)
            raf.readFully(buffer)
            return String(buffer, StandardCharsets.UTF_8)
        }

        fun readValue(type: Int): Any? = when (type) {
            TYPE_UINT8 -> readU8().toLong()
            TYPE_INT8 -> raf.readByte().toLong()
            TYPE_UINT16 -> readU16().toLong()
            TYPE_INT16 -> readU16().toShort().toLong()
            TYPE_UINT32 -> readU32()
            TYPE_INT32 -> readU32().toInt().toLong()
            TYPE_FLOAT32 -> Float.fromBits(readU32().toInt()).toDouble()
            TYPE_BOOL -> readU8() != 0
            TYPE_STRING -> readString()
            TYPE_UINT64 -> readU64()
            TYPE_INT64 -> readU64()
            TYPE_FLOAT64 -> Double.fromBits(readU64())
            TYPE_ARRAY -> readArray()
            else -> throw IllegalStateException("unsupported GGUF value type: $type")
        }

        private fun readArray(): List<Any?> {
            val elementType = readU32().toInt()
            val count = readU64()
            if (count > 1_000_000L) throw EOFException("GGUF array too large")
            val values = ArrayList<Any?>(count.toInt())
            repeat(count.toInt()) {
                values.add(readValue(elementType))
            }
            return values
        }
    }
}
