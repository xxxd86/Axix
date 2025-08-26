package com.redwolf.mvi_dynamic_plugin.android

import android.Manifest
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.PowerManager
import androidx.annotation.RequiresPermission
import com.redwolf.mvi_dynamic_plugin.traffic.Net
import com.redwolf.mvi_dynamic_plugin.traffic.Signals

class AndroidSignals(private val app: Context): () -> Signals {
    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    override fun invoke(): Signals {
        val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val net = cm.activeNetwork
        val cap = cm.getNetworkCapabilities(net)
        val n = when {
            cap == null -> Net.NONE
            cap.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> Net.WIFI
            cap.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> Net.CELL_4G // 简化映射
            else -> Net.CELL_4G
        }
        val bm = app.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val pm = app.getSystemService(Context.POWER_SERVICE) as PowerManager
        val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceIn(0,100)
        val charging = bm.isCharging
        val down = if (n==Net.WIFI) 5000 else 800 // 可替换为历史吞吐/测速
        val fg = pm.isInteractive
        return Signals(net = n, downKbps = down, batteryPct = level, charging = charging, foreground = fg)
    }
}