package com.redwolf.mvi_dynamic_plugin.traffic


class ByteBudget(private val windowMillis: Long = 60_000) {
    private val used = mutableMapOf<String, Pair<Long, Int>>()
    @Synchronized fun allow(module: String, bytes: Int, limitKB: Int): Boolean {
        val now = System.currentTimeMillis()
        val (start, u) = used[module] ?: (now to 0)
        val (ns, nu) = if (now - start >= windowMillis) (now to 0) else (start to u)
        val next = nu + (bytes.coerceAtLeast(0) / 1024)
        return if (next <= limitKB) { used[module] = ns to next; true } else false
    }
    @Synchronized fun snapshot(): Map<String, Int> = used.mapValues { it.value.second }
}