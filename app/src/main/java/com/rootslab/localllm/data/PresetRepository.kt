package com.rootslab.localllm.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.rootslab.localllm.models.ServerConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val Context.presetStore by preferencesDataStore(name = "localllm_presets")

/**
 * Stores named inference profiles. Presets are persisted as a single JSON
 * document so that adding a new parameter to [ServerConfig] never breaks
 * previously saved profiles.
 */
class PresetRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val key = stringPreferencesKey("presets_json")

    val presetsFlow: Flow<Map<String, ServerConfig>> =
        context.presetStore.data.map { prefs ->
            val raw = prefs[key] ?: return@map emptyMap()
            runCatching { json.decodeFromString<Map<String, ServerConfig>>(raw) }.getOrDefault(emptyMap())
        }

    suspend fun save(name: String, config: ServerConfig) {
        context.presetStore.edit { prefs ->
            val current = prefs[key]
                ?.let { runCatching { json.decodeFromString<Map<String, ServerConfig>>(it) }.getOrDefault(emptyMap()) }
                ?: emptyMap()
            prefs[key] = json.encodeToString(current + (name to config))
        }
    }

    suspend fun delete(name: String) {
        context.presetStore.edit { prefs ->
            val current = prefs[key]
                ?.let { runCatching { json.decodeFromString<Map<String, ServerConfig>>(it) }.getOrDefault(emptyMap()) }
                ?: emptyMap()
            prefs[key] = json.encodeToString(current - name)
        }
    }

    companion object {
        val BUILT_IN: Map<String, ServerConfig> = mapOf(
            "Max Performance" to ServerConfig(
                ngl = 99, ctxSize = 8192, threads = 8,
                temperature = 0.7f, topK = 40, topP = 0.95f, repeatPenalty = 1.1f
            ),
            "Balanced" to ServerConfig(
                ngl = 32, ctxSize = 4096, threads = 4,
                temperature = 0.7f, topK = 40, topP = 0.95f, repeatPenalty = 1.1f
            ),
            "Battery Saver" to ServerConfig(
                ngl = 0, ctxSize = 2048, threads = 2,
                temperature = 0.7f, topK = 40, topP = 0.95f, repeatPenalty = 1.1f
            )
        )
    }
}
