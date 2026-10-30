package com.rootslab.localllm.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.rootslab.localllm.LlamaServerService
import com.rootslab.localllm.di.ServiceLocator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Restarts the server after a device reboot or an application update, but only
 * when the user had it running before.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) {
            return
        }

        val pendingResult = goAsync()
        ServiceLocator.init(context.applicationContext)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val shouldRun = ServiceLocator.dataStore.serverRunningFlow.first()
                if (shouldRun) {
                    Log.i(TAG, "Boot: restarting the local API server")
                    LlamaServerService.start(context.applicationContext)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private companion object {
        const val TAG = "BootReceiver"
    }
}
