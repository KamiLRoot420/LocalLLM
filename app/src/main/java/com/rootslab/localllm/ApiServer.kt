package com.rootslab.localllm

import android.util.Log
import com.rootslab.localllm.models.ChatCompletionMessage
import com.rootslab.localllm.models.ChatCompletionRequest
import com.rootslab.localllm.models.ChatCompletionChunk
import com.rootslab.localllm.models.ChunkChoice
import com.rootslab.localllm.models.ChunkDelta
import com.rootslab.localllm.models.ErrorBody
import com.rootslab.localllm.models.ErrorResponse
import com.rootslab.localllm.models.ModelDescriptor
import com.rootslab.localllm.models.ModelList
import com.rootslab.localllm.models.ServerConfig
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationServer
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytesWriter
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.utils.io.writeStringUtf8
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.concurrent.atomic.AtomicLong

/**
 * Embedded OpenAI-compatible HTTP server.
 *
 * Bound to the loopback interface only: the server is never reachable from
 * outside the device, which keeps the "100% offline" promise intact.
 */
object ApiServer {

    private const val TAG = "ApiServer"
    private const val HOST = "127.0.0.1"

    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private val requestCounter = AtomicLong(0)

    private var server: ApplicationServer? = null

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    val port: Int
        get() = server?.engine?.environment?.connectors
            ?.firstOrNull()?.port ?: 0

    fun start(port: Int, config: ServerConfig) {
        if (_isRunning.value) {
            Log.w(TAG, "Server already running")
            return
        }

        return try {
            server = embeddedServer(CIO, host = HOST, port = port) {
                installModules(config)
            }.start(wait = false)

            _isRunning.value = true
            _lastError.value = null
            Log.i(TAG, "API server listening on http://$HOST:$port/v1")
        } catch (e: Exception) {
            _isRunning.value = false
            _lastError.value = e.message ?: "Failed to bind port $port"
            Log.e(TAG, "Failed to start API server on port $port", e)
            throw e
        }
    }

    fun stop() {
        server?.stop(gracePeriodMillis = 500, timeoutMillis = 2_000)
        server = null
        _isRunning.value = false
        Log.i(TAG, "API server stopped")
    }

    private fun Application.installModules(config: ServerConfig) {
        install(ContentNegotiation) {
            json(json)
        }

        // Desktop clients such as Cherry Studio run in an Electron shell and
        // therefore require permissive CORS headers.
        install(CORS) {
            anyHost()
            allowHeader(HttpHeaders.ContentType)
            allowHeader(HttpHeaders.Authorization)
            allowMethod(HttpMethod.Get)
            allowMethod(HttpMethod.Post)
            allowMethod(HttpMethod.Options)
        }

        install(StatusPages) {
            exception<Throwable> { call, cause ->
                Log.e(TAG, "Unhandled request error", cause)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ErrorResponse(ErrorBody(message = cause.message ?: "Internal error"))
                )
            }
        }

        routing {
            get("/health") {
                call.respondText("ok")
            }

            get("/v1/models") {
                call.respond(
                    ModelList(
                        data = listOf(
                            ModelDescriptor(
                                id = "local-gguf",
                                created = System.currentTimeMillis() / 1000,
                                owned_by = "rootslab"
                            )
                        )
                    )
                )
            }

            post("/v1/chat/completions") {
                handleChatCompletion(call, config)
            }
        }
    }

    private suspend fun io.ktor.server.application.ApplicationCall.handleChatCompletion(
        config: ServerConfig
    ) {
        val request = try {
            receive<ChatCompletionRequest>()
        } catch (e: Exception) {
            respond(
                HttpStatusCode.BadRequest,
                ErrorResponse(ErrorBody(message = "Malformed request body: ${e.message}"))
            )
            return
        }

        if (request.messages.isEmpty()) {
            respond(
                HttpStatusCode.BadRequest,
                ErrorResponse(ErrorBody(message = "'messages' must not be empty"))
            )
            return
        }

        val effectiveConfig = config.copy(
            temperature = request.temperature ?: config.temperature,
            topP = request.top_p ?: config.topP,
            topK = request.top_k ?: config.topK,
            repeatPenalty = request.repetition_penalty ?: config.repeatPenalty,
            maxTokens = request.max_tokens ?: config.maxTokens
        )

        val prompt = buildPrompt(request.messages)
        val completionId = "cmpl-${requestCounter.incrementAndGet()}-${System.currentTimeMillis()}"
        val created = System.currentTimeMillis() / 1000

        if (request.stream == true) {
            response.headers.append(HttpHeaders.CacheControl, "no-cache")
            response.headers.append(HttpHeaders.Connection, "keep-alive")

            respondBytesWriter(contentType = ContentType.Text.EventStream) {
                writeStringUtf8(": ${effectiveConfig.maxTokens} max tokens\n\n")

                try {
                    LlamaManager.generateStream(prompt, effectiveConfig).collect { piece ->
                        val chunk = ChatCompletionChunk(
                            id = completionId,
                            created = created,
                            model = request.model,
                            choices = listOf(
                                ChunkChoice(
                                    index = 0,
                                    delta = ChunkDelta(content = piece)
                                )
                            )
                        )
                        writeStringUtf8("data: ${json.encodeToString(chunk)}\n\n")
                        flush()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Streaming failed", e)
                    val error = ErrorResponse(ErrorBody(message = e.message ?: "Generation failed"))
                    writeStringUtf8("data: ${json.encodeToString(error)}\n\n")
                }

                writeStringUtf8("data: [DONE]\n\n")
                flush()
            }
        } else {
            val builder = StringBuilder()
            LlamaManager.generateStream(prompt, effectiveConfig).collect { piece ->
                builder.append(piece)
            }

            respond(
                ChatCompletionChunk(
                    id = completionId,
                    created = created,
                    model = request.model,
                    choices = listOf(
                        ChunkChoice(
                            index = 0,
                            delta = ChunkDelta(
                                role = "assistant",
                                content = builder.toString()
                            ),
                            finish_reason = "stop"
                        )
                    )
                )
            )
        }
    }

    private fun buildPrompt(messages: List<ChatCompletionMessage>): String =
        messages.joinToString("\n") { "${it.role}: ${it.content}" }
}
