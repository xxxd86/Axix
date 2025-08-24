package com.redwolf.mvi_dynamic_plugin.repo

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch


class SWRRepository<K, V>(
    private val mem: MemoryCache<K, V>,
    private val disk: DiskCache<K, V>,
    private val fetcher: suspend (K) -> V,
) {
    fun stream(key: K) = channelFlow<Result<V>> {
        mem.get(key)?.let { trySend(Result.success(it)) }
        val dv = disk.get(key)
        if (dv != null && mem.get(key) == null) trySend(Result.success(dv))
        launch(Dispatchers.IO) {
            val r = runCatching { fetcher(key) }
            r.onSuccess { v -> mem.put(key, v); disk.put(key, v) }
            trySend(r)
        }
        awaitClose { }
    }.flowOn(Dispatchers.IO)
}