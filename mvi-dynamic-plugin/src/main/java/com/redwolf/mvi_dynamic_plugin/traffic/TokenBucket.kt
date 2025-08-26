package com.redwolf.mvi_dynamic_plugin.traffic

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit


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

data class TrafficCfg(val maxConcurrent: Int = 2, val tokensPerSec: Int = 4, val capacity: Int = 8)

class TrafficGate(private val cfg: TrafficCfg) {
    private val bucket = TokenBucket(cfg.capacity, cfg.tokensPerSec)
    private val sem = Semaphore(cfg.maxConcurrent)
    suspend fun <T> run(block: suspend () -> T): T {
        if (!bucket.tryConsume()) throw RejectedException("rate limited")
        return sem.withPermit { block() }
    }
}

class RejectedException(msg: String): RuntimeException(msg)