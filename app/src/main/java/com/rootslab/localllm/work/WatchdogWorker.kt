package com.rootslab.localllm.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.rootslab.localllm.ApiServer
import com.rootslab.localllm.LlamaServerService
import com.rootslab.localllm.di.ServiceLocator
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Periodic watchdog.
 *
 * If the server was supposed to be running but the service process was killed
 * (typically by the low-memory killer), the worker brings it back up.
 */
class WatchdogWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        ServiceLocator.init(applicationContext)

        val shouldRun = ServiceLocator.dataStore.serverRunningFlow.first()

        if (!shouldRun) {
            Log.d(TAG, "Watchdog: server is not supposed to run - nothing to do")
            return Result.success()
        }

        if (LlamaServerService.isAlive && ApiServer.isRunning.value) {
            Log.d(TAG, "Watchdog: server is healthy")
            return Result.success()
        }

        Log.w(TAG, "Watchdog: server is down but should be running - restarting")
        LlamaServerService.start(applicationContext)

        // Give the service a moment to bind the port before the next check.
        delay(2_000)
        return Result.success()
    }

    companion object {
        private const val TAG = "WatchdogWorker"
        private const val WORK_NAME = "localllm_watchdog"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<WatchdogWorker>(5, TimeUnit.MINUTES)
                .setInitialDelay(5, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
