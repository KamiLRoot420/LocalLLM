package com.rootslab.localllm.ui.viewmodel

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rootslab.localllm.ApiServer
import com.rootslab.localllm.LlamaManager
import com.rootslab.localllm.LlamaServerService
import com.rootslab.localllm.ModelRuntimeInfo
import com.rootslab.localllm.di.ServiceLocator
import com.rootslab.localllm.models.ServerConfig
import com.rootslab.localllm.ui.model.ServerStatus
import com.rootslab.localllm.util.DeviceInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DashboardViewModel : ViewModel() {

    private val _config = MutableStateFlow(ServerConfig())
    val config: StateFlow<ServerConfig> = _config.asStateFlow()

    private val _status = MutableStateFlow(ServerStatus.STOPPED)
    val status: StateFlow<ServerStatus> = _status.asStateFlow()

    private val _modelInfo = MutableStateFlow(ModelRuntimeInfo())
    val modelInfo: StateFlow<ModelRuntimeInfo> = _modelInfo.asStateFlow()

    private val _deviceSummary = MutableStateFlow("")
    val deviceSummary: StateFlow<String> = _deviceSummary.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        viewModelScope.launch {
            ServiceLocator.dataStore.configFlow().collect { _config.value = it }
        }
        viewModelScope.launch {
            ServiceLocator.dataStore.serverRunningFlow.collect { running ->
                _status.value = when {
                    running && ApiServer.isRunning.value -> ServerStatus.READY
                    running -> ServerStatus.LOADING
                    else -> ServerStatus.STOPPED
                }
            }
        }
        viewModelScope.launch {
            ApiServer.isRunning.collect { running ->
                if (running) {
                    _status.value = ServerStatus.READY
                    _modelInfo.value = LlamaManager.modelInfo()
                } else if (_status.value != ServerStatus.LOADING) {
                    _status.value = ServerStatus.STOPPED
                }
            }
        }
        viewModelScope.launch {
            _deviceSummary.value = DeviceInfo.deviceSummary(ServiceLocator.context)
        }
    }

    fun toggleServer() {
        val context = ServiceLocator.context
        if (ApiServer.isRunning.value) {
            LlamaServerService.stop(context)
            _status.value = ServerStatus.STOPPED
        } else {
            _status.value = ServerStatus.LOADING
            LlamaServerService.start(context)
        }
    }

    fun copyEndpoint() {
        val context = ServiceLocator.context
        val endpoint = "http://127.0.0.1:${_config.value.port}/v1"
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("API endpoint", endpoint))
        _message.value = "Copied: $endpoint"
    }

    fun consumeMessage() {
        _message.value = null
    }

    fun refreshModelInfo() {
        if (LlamaManager.isLoaded) {
            _modelInfo.value = LlamaManager.modelInfo()
        }
    }
}
