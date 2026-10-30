package com.rootslab.localllm.data

import android.content.Context
import androidx.datastore.core.DataStore
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

private val Context.preferencesStore: DataStore<Preferences> by preferencesDataStore(
    name = "localllm_prefs"
)

/**
 * Persistent application state: engine configuration, server state and the
 * onboarding flag. Backed by Jetpack DataStore (Preferences).
 */
class AppDataStore(context: Context) {

    private val store = context.applicationContext.preferencesStore

    val configFlow: Flow<ServerConfig> = store.data.map { it.toConfig() }

    val serverRunningFlow: Flow<Boolean> =
        store.data.map { it[Keys.SERVER_RUNNING] ?: false }

    val wakeLockFlow: Flow<Boolean> =
        store.data.map { it[Keys.WAKE_LOCK] ?: false }

    val onboardingCompletedFlow: Flow<Boolean> =
        store.data.map { it[Keys.ONBOARDING_COMPLETED] ?: false }

    suspend fun currentConfig(): ServerConfig = store.data.first().toConfig()

    suspend fun updateConfig(config: ServerConfig) {
        store.edit { prefs ->
            prefs[Keys.MODEL_PATH] = config.modelPath
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

    suspend fun setModelPath(path: String) {
        store.edit { it[Keys.MODEL_PATH] = path }
    }

    suspend fun setServerRunning(running: Boolean) {
        store.edit { it[Keys.SERVER_RUNNING] = running }
    }

    suspend fun setWakeLock(enabled: Boolean) {
        store.edit { it[Keys.WAKE_LOCK] = enabled }
    }

    suspend fun isWakeLockEnabled(): Boolean =
        store.data.first()[Keys.WAKE_LOCK] ?: false

    suspend fun setOnboardingCompleted(completed: Boolean) {
        store.edit { it[Keys.ONBOARDING_COMPLETED] = completed }
    }

    suspend fun isOnboardingCompleted(): Boolean =
        store.data.first()[Keys.ONBOARDING_COMPLETED] ?: false

    private fun Preferences.toConfig() = ServerConfig(
        modelPath = this[Keys.MODEL_PATH] ?: "",
        ngl = this[Keys.NGL] ?: ServerConfig.BALANCED.ngl,
        ctxSize = this[Keys.CTX_SIZE] ?: ServerConfig.BALANCED.ctxSize,
        threads = this[Keys.THREADS] ?: ServerConfig.BALANCED.threads,
        temperature = this[Keys.TEMPERATURE] ?: ServerConfig.BALANCED.temperature,
        topK = this[Keys.TOP_K] ?: ServerConfig.BALANCED.topK,
        topP = this[Keys.TOP_P] ?: ServerConfig.BALANCED.topP,
        repeatPenalty = this[Keys.REPEAT_PENALTY] ?: ServerConfig.BALANCED.repeatPenalty,
        seed = this[Keys.SEED] ?: ServerConfig.BALANCED.seed,
        maxTokens = this[Keys.MAX_TOKENS] ?: ServerConfig.BALANCED.maxTokens,
        port = this[Keys.PORT] ?: ServerConfig.DEFAULT_PORT
    )

    private object Keys {
        val MODEL_PATH = stringPreferencesKey("model_path")
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
        val SERVER_RUNNING = booleanPreferencesKey("server_running")
        val WAKE_LOCK = booleanPreferencesKey("wake_lock")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
    }
}
