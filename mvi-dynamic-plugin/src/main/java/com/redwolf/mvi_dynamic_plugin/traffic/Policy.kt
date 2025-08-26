package com.redwolf.mvi_dynamic_plugin.traffic


enum class Net { WIFI, CELL_5G, CELL_4G, CELL_3G, CELL_2G, NONE }

data class Signals(
    val net: Net,
    val downKbps: Int,
    val batteryPct: Int,
    val charging: Boolean,
    val foreground: Boolean
)

data class ModuleCfg(
    val traffic: TrafficCfg = TrafficCfg(),
    val byteBudgetKB: Int = 2048,
    val pageSize: Int = 20,
    val imageQuality: Int = 80
)

interface Policy { fun forModule(module: String, s: Signals): ModuleCfg }

class HeuristicPolicy(private val dataSaver: () -> Boolean): Policy {
    override fun forModule(module: String, s: Signals): ModuleCfg {
        val saver = dataSaver()
        val slow = s.downKbps < 800 || s.net <= Net.CELL_3G
        return when (module) {
            "banner" -> ModuleCfg(TrafficCfg(1,1,2), byteBudgetKB = 128, pageSize = 5, imageQuality = 60)
            "feed"   -> ModuleCfg(
                traffic = TrafficCfg(2, if (saver) 3 else 6, 8),
                byteBudgetKB = if (saver) 1024 else 2048,
                pageSize = if (saver || slow) 10 else 20,
                imageQuality = if (saver || slow) 60 else 80
            )
            else     -> ModuleCfg()
        }
    }
}