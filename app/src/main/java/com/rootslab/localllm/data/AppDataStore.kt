package com.rootslab.localllm.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.rootslab.localllm.models.ServerConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.localllmStore by preferencesDataStore(name = "localllm_prefs")

/**
 * Single source of truth for persisted application state.
 *
 * All reads are exposed as [Flow]s so that the UI reacts to changes made by
 * the foreground service, the widget or the boot receiver.
 */
class AppDataStore(private val context: Context) {

    private object Keys {
        val MODEL_PATH = stringPreferencesKey("model_path")
        val MODEL_NAME = stringPreferencesKey("model_name")
        val NGL = intPreferencesKey("ngl")
        val CTX_SIZE = intPreferencesKey("ctx_size")
        val THREADS = intPreferencesKey("threads")
        val TEMPERATURE = floatPreferencesKey("temperature")
        val TOP_K = intPreferencesKey("top_k")
        val TOP_P = floatPreferencesKey("top_p")
        val REPEAT_PENALTY = floatPreferencesKey("repeat_penalty")
        val SEED = intPreferencesKey("seed")
        val MAX_TOKENS = intPreferencesKey("max_tokens")
        val PORT = intPreferencesKey("port")
        val WAKE_LOCK = booleanPreferencesKey("wake_lock")
        val SERVER_RUNNING = booleanPreferencesKey("server_running")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
    }

    val configFlow: Flow<ServerConfig> = context.localllmStore.data.map { it.toConfig() }

    val serverRunningFlow: Flow<Boolean> =
        context.localllmStore.data.map { it[Keys.SERVER_RUNNING] ?: false }

    val wakeLockFlow: Flow<Boolean> =
        context.localllmStore.data.map { it[Keys.WAKE_LOCK] ?: false }

    val onboardingDoneFlow: Flow<Boolean> =
        context.localllmStore.data.map { it[Keys.ONBOARDING_DONE] ?: false }

    suspend fun currentConfig(): ServerConfig = context.localllmStore.data.first().toConfig()

    suspend fun currentServerRunning(): Boolean =
        context.localllmStore.data.first()[Keys.SERVER_RUNNING] ?: false

    suspend fun currentWakeLock(): Boolean =
        context.localllmStore.data.first()[Keys.WAKE_LOCK] ?: false

    suspend fun isOnboardingDone(): Boolean =
        context.localllmStore.data.first()[Keys.ONBOARDING_DONE] ?: false

    suspend fun updateConfig(config: ServerConfig) {
        context.localllmStore.edit { prefs ->
            prefs[Keys.MODEL_PATH] = config.modelPath
            prefs[Keys.MODEL_NAME] = config.modelName
            prefs[Keys.NGL] = config.ngl
            prefs[Keys.CTX_SIZE] = config.ctxSize
            prefs[Keys.THREADS] = config.threads
            prefs[Keys.TEMPERATURE] = config.temperature
            prefs[Keys.TOP_K] = config.topK
            prefs[Keys.TOP_P] = config.topP
            prefs[Keys.REPEAT_PENALTY] = config.repeatPenalty
            prefs[Keys.SEED] = config.seed
            prefs[Keys.MAX_TOKENS] = config.maxTokens
            prefs[Keys.PORT] = config.port
        }
    }

    suspend fun setServerRunning(running: Boolean) {
        context.localllmStore.edit { it[Keys.SERVER_RUNNING] = running }
    }

    suspend fun setWakeLock(enabled: Boolean) {
        context.localllmStore.edit { it[Keys.WAKE_LOCK] = enabled }
    }

    suspend fun setOnboardingDone(done: Boolean) {
        context.localllmStore.edit { it[Keys.ONBOARDING_DONE] = done }
    }

    private fun Preferences.toConfig() = ServerConfig(
        modelPath = this[Keys.MODEL_PATH] ?: "",
        modelName = this[Keys.MODEL_NAME] ?: "",
        ngl = this[Keys.NGL] ?: 32,
        ctxSize = this[Keys.CTX_SIZE] ?: 4096,
        threads = this[Keys.THREADS] ?: 4,
        temperature = this[Keys.TEMPERATURE] ?: 0.7f,
        topK = this[Keys.TOP_K] ?: 40,
        topP = this[Keys.TOP_P] ?: 0.95f,
        repeatPenalty = this[Keys.REPEAT_PENALTY] ?: 1.1f,
        seed = this[Keys.SEED] ?: -1,
        maxTokens = this[Keys.MAX_TOKENS] ?: 512,
        port = this[Keys.PORT] ?: 8080,
        wakeLock = this[Keys.WAKE_LOCK] ?: false
    )
}
