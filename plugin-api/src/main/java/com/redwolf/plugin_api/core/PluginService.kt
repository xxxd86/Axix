package com.redwolf.plugin_api.core

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.content.res.Resources
import android.os.IBinder

/** 插件 Service “轻量基类”：不直接继承系统 Service，由宿主 ProxyService 驱动 */
open class PluginService {
    protected lateinit var hostService: Service
    protected lateinit var pluginResources: Resources
    protected lateinit var pluginPackageName: String

    open fun attach(host: Service, res: Resources, pkg: String) {
        hostService = host; pluginResources = res; pluginPackageName = pkg
    }

    open fun onCreate() {}
    open fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int =
        Service.START_NOT_STICKY

    open fun onBind(intent: Intent?): IBinder? = null
    open fun onUnbind(intent: Intent?): Boolean = true
    open fun onRebind(intent: Intent?) {}
    open fun onDestroy() {}

    // 便捷：让插件能控制宿主前台/停止等
    fun startForeground(id: Int, n: Notification) = hostService.startForeground(id, n)
    fun stopForeground(removeNotification: Boolean) = hostService.stopForeground(removeNotification)
    fun stopSelf() = hostService.stopSelf()
}