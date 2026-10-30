package com.rootslab.localllm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.rootslab.localllm.di.ServiceLocator
import com.rootslab.localllm.work.WatchdogScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground service that owns the inference engine and the HTTP server.
 *
 * The service is started as a foreground service so that Android keeps the
 * process alive while the screen is off. `START_STICKY` plus the watchdog
 * worker provide the auto-resume behaviour after a low-memory kill.
 */
class LlamaServerService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isAlive = true
        ServiceLocator.init(applicationContext)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundCompat()

        when (intent?.action) {
            ACTION_STOP -> stopEverything()
            ACTION_TOGGLE -> scope.launch { if (isServerActive()) stopEverything() else startEverything() }
            else -> scope.launch { startEverything() }
        }

        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // The user swiped the app away: keep the server alive and make sure the
        // watchdog is armed in case the process is reclaimed.
        WatchdogScheduler.schedule(this)
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        isAlive = false
        releaseWakeLock()
        ApiServer.stop()
        scope.cancel()
        super.onDestroy()
    }

    // --- lifecycle ----------------------------------------------------------

    private suspend fun startEverything() {
        val dataStore = ServiceLocator.dataStore
        val config = dataStore.currentConfig()

        if (config.modelPath.isBlank()) {
            Log.w(TAG, "No model selected - server not started")
            dataStore.setServerRunning(false)
            updateNotification(state = State.ERROR, detail = "No model selected")
            return
        }

        updateNotification(state = State.LOADING, detail = "Loading model...")

        val loaded = LlamaManager.loadModel(config.modelPath, config)
        if (!loaded) {
            dataStore.setServerRunning(false)
            updateNotification(state = State.ERROR, detail = LlamaManager.lastError ?: "Model load failed")
            return
        }

        try {
            ApiServer.start(config.port, config)
        } catch (e: Exception) {
            dataStore.setServerRunning(false)
            updateNotification(state = State.ERROR, detail = "Port ${config.port} unavailable")
            Log.e(TAG, "Cannot start API server", e)
            return
        }

        dataStore.setServerRunning(true)
        if (dataStore.currentWakeLock()) {
            acquireWakeLock()
        }
        WatchdogScheduler.schedule(this)

        updateNotification(
            state = State.READY,
            detail = "Listening on 127.0.0.1:${config.port}"
        )
    }

    private suspend fun stopEverything() {
        ApiServer.stop()
        LlamaManager.unloadModel()
        releaseWakeLock()
        ServiceLocator.dataStore.setServerRunning(false)
        updateNotification(state = State.STOPPED, detail = "Server stopped")
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun isServerActive(): Boolean = ApiServer.isRunning.value

    // --- foreground / notification ------------------------------------------

    private fun startForegroundCompat() {
        val notification = buildNotification(State.LOADING, "Starting...")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                FOREGROUND_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(FOREGROUND_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "LocalLLM AI Server",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Keeps the local OpenAI-compatible API server alive"
            setShowBadge(false)
        }
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }

    private fun updateNotification(state: State, detail: String) {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(FOREGROUND_ID, buildNotification(state, detail))
    }

    private fun buildNotification(state: State, detail: String): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val toggleIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, LlamaServerService::class.java).setAction(ACTION_TOGGLE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("LocalLLM AI Server")
            .setContentText(detail)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Stop",
                toggleIntent
            )
            .build()
            .also { notification ->
                if (state == State.ERROR) {
                    Log.w(TAG, "Notification state: $state - $detail")
                }
            }
    }

    // --- wake lock ----------------------------------------------------------

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "LocalLLM:ServerWakeLock"
        ).apply {
            setReferenceCounted(false)
            acquire(WAKE_LOCK_TIMEOUT_MS)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    enum class State { READY, LOADING, STOPPED, ERROR }

    companion object {
        private const val TAG = "LlamaServerService"
        private const val FOREGROUND_ID = 1001
        private const val CHANNEL_ID = "com.rootslab.localllm.server"
        private const val WAKE_LOCK_TIMEOUT_MS = 10 * 60 * 1000L

        const val ACTION_START = "com.rootslab.localllm.action.START"
        const val ACTION_STOP = "com.rootslab.localllm.action.STOP"
        const val ACTION_TOGGLE = "com.rootslab.localllm.action.TOGGLE"

        /** True while the service instance is alive in the current process. */
        @Volatile
        var isAlive: Boolean = false
            private set

        fun start(context: Context) {
            val intent = Intent(context, LlamaServerService::class.java).setAction(ACTION_START)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, LlamaServerService::class.java).setAction(ACTION_STOP)
            context.startService(intent)
        }

        fun toggle(context: Context) {
            val intent = Intent(context, LlamaServerService::class.java).setAction(ACTION_TOGGLE)
            context.startForegroundService(intent)
        }
    }
}
