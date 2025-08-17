package com.redwolf.plugin_api.manager

import android.content.Context
import android.content.Intent
import com.redwolf.plugin_api.core.PluginProxyService
import com.redwolf.plugin_api.core.PluginService
import com.redwolf.plugin_api.runtime.PluginRuntime


object PluginServiceManager {
    fun loadPluginService(ctx: Context, serviceClassName: String): PluginProxyService? {
        return try {
            val cls = ctx.classLoader.loadClass(serviceClassName)
            cls.asSubclass(PluginProxyService::class.java).getDeclaredConstructor().newInstance()
        } catch (e: Exception) {
            null
        }
    }
}