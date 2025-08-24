package com.redwolf.mvi_dynamic_plugin.repo

class MemoryCache<K, V>(private val capacity: Int = 100) {
    private val map = LinkedHashMap<K, V>(capacity, 0.75f, true)
    fun get(key: K): V? = synchronized(map) { map[key] }
    fun put(key: K, value: V) = synchronized(map) {
        map[key] = value
        if (map.size > capacity) map.entries.iterator().let { if (it.hasNext()) { it.next(); it.remove() } }
    }
}