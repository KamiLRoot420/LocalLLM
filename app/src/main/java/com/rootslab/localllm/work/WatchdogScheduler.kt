package com.rootslab.localllm.work

import android.content.Context

/**
 * Thin indirection so that call sites do not depend on WorkManager directly.
 */
object WatchdogScheduler {
    fun schedule(context: Context) = WatchdogWorker.schedule(context)
    fun cancel(context: Context) = WatchdogWorker.cancel(context)
}
