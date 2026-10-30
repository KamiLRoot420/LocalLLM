package com.rootslab.localllm

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

/**
 * Home-screen widget exposing a single master power button (START / STOP).
 */
class WidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { widgetId ->
            appWidgetManager.updateAppWidget(widgetId, buildViews(context, isRunning(context)))
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            updateAll(context)
        }
    }

    companion object {
        const val ACTION_REFRESH = "com.rootslab.localllm.action.REFRESH_WIDGET"

        private fun isRunning(context: Context): Boolean =
            com.rootslab.localllm.ApiServer.isRunning.value

        private fun buildViews(context: Context, running: Boolean): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_layout)

            views.setTextViewText(
                R.id.widgetStatus,
                if (running) "Running" else "Stopped"
            )
            views.setImageViewResource(
                R.id.widgetToggle,
                if (running) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
            )
            views.setTextColor(
                R.id.widgetStatus,
                if (running) 0xFF00C853.toInt() else 0xFFFF5252.toInt()
            )

            val pending = PendingIntent.getBroadcast(
                context,
                0,
                Intent(context, com.rootslab.localllm.receiver.WidgetReceiver::class.java)
                    .setAction("com.rootslab.localllm.action.TOGGLE_SERVER"),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widgetToggle, pending)

            val openIntent = PendingIntent.getActivity(
                context,
                1,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widgetRoot, openIntent)

            return views
        }

        /** Refreshes every placed widget instance. */
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, WidgetProvider::class.java)
            val widgetIds = manager.getAppWidgetIds(component)
            if (widgetIds.isEmpty()) return

            val running = isRunning(context)
            widgetIds.forEach { widgetId ->
                manager.updateAppWidget(widgetId, buildViews(context, running))
            }
        }
    }
}
