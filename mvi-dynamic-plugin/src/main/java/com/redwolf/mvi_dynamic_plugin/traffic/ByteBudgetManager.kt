package com.redwolf.mvi_dynamic_plugin.traffic

class ByteBudgetManager(private val windowMillis: Long = 60_000) {
    private val used = mutableMapOf<String, Pair<Long, Int>>() // module -> (windowStart, usedKB)
    @Synchronized fun allow(module: String, bytes: Int, budgetKB: Int): Boolean {
        val now = System.currentTimeMillis()
        val (winStart, usedKB) = used[module] ?: (now to 0)
        val (ns, nu) = if (now - winStart >= windowMillis) (now to 0) else (winStart to usedKB)
        val next = nu + (bytes.coerceAtLeast(0) / 1024)
        return if (next <= budgetKB) { used[module] = ns to next; true } else false
    }
    @Synchronized fun snapshot(): Map<String, Int> = used.mapValues { it.value.second }
}