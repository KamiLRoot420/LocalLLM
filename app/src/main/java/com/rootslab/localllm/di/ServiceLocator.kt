package com.rootslab.localllm.di

import android.content.Context
import com.rootslab.localllm.data.AppDataStore
import com.rootslab.localllm.data.ModelRepository
import com.rootslab.localllm.data.PresetRepository

/**
 * Minimal manual dependency container.
 *
 * The application has a single process and a small number of collaborators, so
 * a full DI framework would add build time and indirection without buying
 * anything. Everything is created lazily and lives for the process lifetime.
 */
object ServiceLocator {

    private lateinit var appContext: Context

    val dataStore: AppDataStore by lazy { AppDataStore(appContext) }
    val presetRepository: PresetRepository by lazy { PresetRepository(appContext) }
    val modelRepository: ModelRepository by lazy { ModelRepository(appContext) }

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    val context: Context get() = appContext
}
