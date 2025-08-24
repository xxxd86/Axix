package com.redwolf.mvi_dynamic_plugin.traffic

enum class NetType { WIFI, CELL_5G, CELL_4G, CELL_3G, CELL_2G, NONE }


interface EdgeSignals {
    val net: NetType
    val rttMs: Int
    val downKbps: Int
    val batteryPct: Int
    val charging: Boolean
    val scrollVelocity: Float
    val foreground: Boolean
    val hourOfDay: Int
}