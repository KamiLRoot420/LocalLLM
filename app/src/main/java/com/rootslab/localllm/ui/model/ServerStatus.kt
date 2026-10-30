package com.rootslab.localllm.ui.model

/**
 * Lifecycle of the local server as presented on the dashboard.
 */
enum class ServerStatus {
    STOPPED,
    LOADING,
    READY,
    ERROR;

    val label: String
        get() = when (this) {
            STOPPED -> "Stopped"
            LOADING -> "Loading"
            READY -> "Ready"
            ERROR -> "Error"
        }
}
