package com.rootslab.localllm.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.rootslab.localllm.LlamaServerService
import com.rootslab.localllm.WidgetProvider

/**
 * Handles the START / STOP button on the home-screen widget.
 */
class WidgetReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_TOGGLE -> {
                LlamaServerService.toggle(context)
                WidgetProvider.updateAll(context)
            }
        }
    }

    private companion object {
        const val ACTION_TOGGLE = "com.rootslab.localllm.action.TOGGLE_SERVER"
    }
}
