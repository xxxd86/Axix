package com.redwolf.plugin_api.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import com.redwolf.plugin_api.runtime.PluginRuntime

/** 插件 Receiver 基类：可选，便于初始化（也可直接写普通 BroadcastReceiver） */
open class PluginBroadcastReceiver : BroadcastReceiver() {
    protected lateinit var pluginResources: Resources
    protected lateinit var pluginPackageName: String

    open fun attach(res: Resources, pkg: String) {
        pluginResources = res; pluginPackageName = pkg
    }

    override fun onReceive(context: Context, intent: Intent) {
        // 获取插件的 Receiver 类名
        val receiverClass =
            intent.getStringExtra("RECEIVER_CLASS") ?: "com.redwolf.plugin_name.DemoPluginReceiver"
        val pluginReceiver = PluginRuntime.loadPluginBroadcastReceiver(context, receiverClass)
        pluginReceiver?.onReceive(context, intent) // 使用安全调用，避免为 null 的情况
    }
}
