package com.redwolf.mvi_dynamic_plugin.core

import com.redwolf.mvi_dynamic_plugin.traffic.ByteBudget
import com.redwolf.mvi_dynamic_plugin.traffic.Policy
import com.redwolf.mvi_dynamic_plugin.traffic.RejectedException
import com.redwolf.mvi_dynamic_plugin.traffic.Signals
import com.redwolf.mvi_dynamic_plugin.traffic.TrafficGate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object EdgeKit {
    lateinit var policy: Policy
    lateinit var signalsProvider: () -> Signals
    val budget = ByteBudget()

    fun init(policy: Policy, signals: () -> Signals) {
        this.policy = policy; this.signalsProvider = signals
    }

    /** 在任何网络调用外包一层；不改你现有 MVI/插件架构 */
    suspend fun <T> gate(module: String, estimateBytes: Int, block: suspend () -> T): T =
        withContext(Dispatchers.IO) {
            val s = signalsProvider()
            val cfg = policy.forModule(module, s)
            if (!budget.allow(module, estimateBytes, cfg.byteBudgetKB)) {
                throw RejectedException("byte budget exceeded for $module")
            }
            val gate = TrafficGate(cfg.traffic)
            gate.run { block() }
        }
}