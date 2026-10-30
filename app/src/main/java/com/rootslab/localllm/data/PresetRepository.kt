package com.rootslab.localllm.data

import android.content.Context
import com.rootslab.localllm.models.ServerConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json

/**
 * Stores user-defined sampling presets. Built-in presets live in
 * [ServerConfig.BUILT_IN_PRESETS] and are always available; user presets are
 * persisted as a JSON blob in SharedPreferences.
 */
class PresetRepository(context: Context) {

    private val appContext = context.applicationContext
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val _presets = MutableStateFlow<Map<String, ServerConfig>>(emptyMap())
    val presets: StateFlow<Map<String, ServerConfig>> = _presets.asStateFlow()

    init {
        load()
    }

    /** All presets, built-in ones first. */
    val allPresets: Map<String, ServerConfig>
        get() = ServerConfig.BUILT_IN_PRESETS + _presets.value

    fun save(name: String, config: ServerConfig) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        val updated = _presets.value + (trimmed to config)
        _presets.value = updated
        persist(updated)
    }

    fun delete(name: String) {
        val updated = _presets.value - name
        _presets.value = updated
        persist(updated)
    }

    fun get(name: String): ServerConfig? =
        _presets.value[name] ?: ServerConfig.BUILT_IN_PRESETS[name]

    private fun load() {
        val raw = appContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_PRESETS, null)
            ?: return
        runCatching {
            _presets.value = json.decodeFromString<Map<String, ServerConfig>>(raw)
        }.onFailure {
            _presets.value = emptyMap()
        }
    }

    private fun persist(presets: Map<String, ServerConfig>) {
        appContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PRESETS, json.encodeToString(presets))
            .apply()
    }

    private companion object {
        const val PREFS_NAME = "localllm_presets"
        const val KEY_PRESETS = "presets_json"
    }
}
