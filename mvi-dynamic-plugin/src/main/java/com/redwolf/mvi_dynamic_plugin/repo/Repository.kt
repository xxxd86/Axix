package com.redwolf.mvi_dynamic_plugin.repo

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock


class Repository<K, V>(
    private val scope: CoroutineScope,
    private val fetcher: suspend (K) -> V,
    private val cache: MemoryCache<K, V> = MemoryCache()
) {
    private val inFlight = mutableMapOf<K, Deferred<V>>()
    private val mutex = Mutex()
    suspend fun get(key: K): V {
        cache.get(key)?.let { return it }
        val task = mutex.withLock { inFlight[key] ?: scope.async(Dispatchers.IO) { fetcher(key) }.also { inFlight[key] = it } }
        return try { val v = task.await().also { cache.put(key, it) }; v } finally { mutex.withLock { inFlight.remove(key) } }
    }
}