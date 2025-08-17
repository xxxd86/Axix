package com.redwolf.plugin_api.core

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresApi
import com.redwolf.plugin_api.ProxyKeys
import com.redwolf.plugin_api.runtime.NetworkPolicy
import com.redwolf.plugin_api.runtime.PluginHandle
import com.redwolf.plugin_api.runtime.PluginRuntime
import com.redwolf.plugin_api.runtime.PluginStrategy
import java.io.File

open class PluginProxyService : Service() {
    private var pluginService: PluginService? = null

    override fun onCreate() {
        super.onCreate()
        val serviceClass = "com.redwolf.axix.DemoServiceOne"
        pluginService = PluginRuntime.loadPluginService(this, serviceClass)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        pluginService?.onStartCommand(intent, flags, startId)
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return pluginService?.onBind(intent)
    }
}