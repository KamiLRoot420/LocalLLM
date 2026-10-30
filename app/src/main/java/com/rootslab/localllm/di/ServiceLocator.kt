package com.rootslab.localllm.di

import android.content.Context
import com.rootslab.localllm.data.AppDataStore
import com.rootslab.localllm.data.ModelRepository
import com.rootslab.localllm.data.PresetRepository

/**
 * Minimal service locator. The project intentionally avoids a DI framework:
 * the dependency graph is small and fully static, so a plain object keeps the
 * build lightweight and the startup path free of reflection.
 */
object ServiceLocator {

    private lateinit var appContext: Context

    /** Application context, safe to use from any thread. */
    val context: Context
        get() = appContext

    val dataStore: AppDataStore by lazy { AppDataStore(appContext) }

    val presetRepository: PresetRepository by lazy { PresetRepository(appContext) }

    val modelRepository: ModelRepository by lazy { ModelRepository(appContext) }

    fun init(context: Context) {
        appContext = context.applicationContext
    }
}
