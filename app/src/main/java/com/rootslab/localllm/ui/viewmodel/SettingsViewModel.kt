package com.rootslab.localllm.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rootslab.localllm.di.ServiceLocator
import com.rootslab.localllm.models.ServerConfig
import com.rootslab.localllm.util.DeviceInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel : ViewModel() {

    private val _config = MutableStateFlow(ServerConfig())
    val config: StateFlow<ServerConfig> = _config.asStateFlow()

    private val _wakeLock = MutableStateFlow(false)
    val wakeLock: StateFlow<Boolean> = _wakeLock.asStateFlow()

    private val _presets = MutableStateFlow<Map<String, ServerConfig>>(emptyMap())
    val presets: StateFlow<Map<String, ServerConfig>> = _presets.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    init {
        viewModelScope.launch {
            ServiceLocator.dataStore.configFlow().collect { _config.value = it }
        }
        viewModelScope.launch {
            ServiceLocator.dataStore.wakeLockFlow.collect { _wakeLock.value = it }
        }
        viewModelScope.launch {
            ServiceLocator.presetRepository.presets.collect { userPresets ->
                _presets.value = ServerConfig.BUILT_IN_PRESETS + userPresets
            }
        }
    }

    fun updateConfig(config: ServerConfig) {
        _config.value = config
        viewModelScope.launch {
            ServiceLocator.dataStore.updateConfig(config)
            _statusMessage.value = "Configuration saved"
        }
    }

    fun setWakeLock(enabled: Boolean) {
        _wakeLock.value = enabled
        viewModelScope.launch {
            ServiceLocator.dataStore.setWakeLock(enabled)
        }
    }

    fun applyPreset(name: String) {
        val preset = ServiceLocator.presetRepository.get(name) ?: return
        updateConfig(_config.value.copy(
            ngl = preset.ngl,
            ctxSize = preset.ctxSize,
            threads = preset.threads,
            temperature = preset.temperature,
            topK = preset.topK,
            topP = preset.topP,
            repeatPenalty = preset.repeatPenalty,
            maxTokens = preset.maxTokens
        ))
        _statusMessage.value = "Preset '$name' applied"
    }

    fun savePreset(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            _statusMessage.value = "Preset name must not be empty"
            return
        }
        ServiceLocator.presetRepository.save(trimmed, _config.value)
        _statusMessage.value = "Preset '$trimmed' saved"
    }

    fun deletePreset(name: String) {
        ServiceLocator.presetRepository.delete(name)
        _statusMessage.value = "Preset '$name' deleted"
    }

    /**
     * Recomputes a configuration that is safe for the current device and the
     * currently selected model.
     */
    fun autoDetect() {
        val recommended = DeviceInfo.recommendConfig(ServiceLocator.context, _config.value.modelPath)
        updateConfig(_config.value.copy(
            ngl = recommended.ngl,
            ctxSize = recommended.ctxSize,
            threads = recommended.threads,
            maxTokens = recommended.maxTokens
        ))
        _statusMessage.value = "Hardware auto-detection applied"
    }

    fun consumeStatusMessage() {
        _statusMessage.value = null
    }
}
