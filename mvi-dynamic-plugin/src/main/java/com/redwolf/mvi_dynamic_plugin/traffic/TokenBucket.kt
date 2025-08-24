package com.redwolf.mvi_dynamic_plugin.traffic

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock


class TokenBucket(private val capacity: Int, private val refillPerSecond: Int) {
    private val mutex = Mutex()
    private var tokens = capacity
    private var lastRefillNanos = System.nanoTime()
    suspend fun tryConsume(n: Int = 1): Boolean = mutex.withLock { refill(); if (tokens >= n) { tokens -= n; true } else false }
    private fun refill() {
        val now = System.nanoTime()
        val elapsedSec = (now - lastRefillNanos) / 1_000_000_000.0
        if (elapsedSec > 0) {
            val add = (elapsedSec * refillPerSecond).toInt()
            if (add > 0) { tokens = (tokens + add).coerceAtMost(capacity); lastRefillNanos = now }
        }
    }
}