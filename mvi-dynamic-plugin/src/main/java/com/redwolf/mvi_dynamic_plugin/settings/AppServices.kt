package com.redwolf.mvi_dynamic_plugin.settings

import com.redwolf.mvi_dynamic_plugin.traffic.AppSettingsState
import com.redwolf.mvi_dynamic_plugin.traffic.ByteBudgetManager
import com.redwolf.mvi_dynamic_plugin.traffic.EdgeAdvisor
import com.redwolf.mvi_dynamic_plugin.traffic.EdgeDecision
import com.redwolf.mvi_dynamic_plugin.traffic.EdgeSignals
import com.redwolf.mvi_dynamic_plugin.traffic.HeuristicEdgeAdvisor
import com.redwolf.mvi_dynamic_plugin.traffic.NetType
import kotlin.math.max


object AppServices {
    // 可被 Settings 修改的信号
    var signals: EdgeSignals = object : EdgeSignals {
        override val net: NetType = NetType.WIFI
        override val rttMs: Int = 50
        override val downKbps: Int = 5000
        override val batteryPct: Int = 80
        override val charging: Boolean = true
        override val scrollVelocity: Float = 0f
        override val foreground: Boolean = true
        override val hourOfDay: Int = 12
    }


    val edgeAdvisor: EdgeAdvisor = HeuristicEdgeAdvisor()
    val byteBudget = ByteBudgetManager()


    fun edgeDecision(): EdgeDecision = run {
// 简化：同步计算一次（不会阻塞网络）
        val adv = (edgeAdvisor as HeuristicEdgeAdvisor)
        EdgeDecision(allow = true, byteBudgetKB = 2048, pageSize = if (AppSettingsState.dataSaver) 10 else 20, imageQuality = if (AppSettingsState.dataSaver) 60 else 80)
    }
}