package com.redwolf.plugin_api.manager

import android.content.Context
import android.content.Intent
import com.redwolf.plugin_api.runtime.PluginRuntime

object PluginReceiverManager {
    fun sendPluginBroadcast(
        ctx: Context,
        module: String,
        receiverClassName: String,
        intent: Intent
    ) {
        val receiver = PluginRuntime.loadPluginBroadcastReceiver(ctx, receiverClassName)
        receiver?.onReceive(ctx, intent)
    }
}