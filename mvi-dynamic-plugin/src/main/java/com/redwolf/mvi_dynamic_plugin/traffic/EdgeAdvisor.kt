package com.redwolf.mvi_dynamic_plugin.traffic

// 可从 Settings 切换 profile（如 省流/默认）
object AppSettingsState { @Volatile var dataSaver: Boolean = false }


data class EdgeDecision(
    val allow: Boolean = true,
    val byteBudgetKB: Int? = null,
    val pageSize: Int? = null,
    val imageQuality: Int? = null,
)


interface EdgeAdvisor { suspend fun decide(module: String, action: String, s: EdgeSignals): EdgeDecision }


class HeuristicEdgeAdvisor : EdgeAdvisor {
    override suspend fun decide(module: String, action: String, s: EdgeSignals): EdgeDecision {
        val saver = AppSettingsState.dataSaver
        val slow = s.downKbps < 800 || s.net == NetType.CELL_3G || s.net == NetType.CELL_2G || s.net == NetType.NONE
        val pageSize = if (saver || slow) 10 else 20
        val q = if (saver || slow) 60 else 80
        val budget = when (module) { "banner" -> 128; "feed" -> if (saver) 1024 else 2048; else -> 512 }
        return EdgeDecision(allow = true, byteBudgetKB = budget, pageSize = pageSize, imageQuality = q)
    }
}