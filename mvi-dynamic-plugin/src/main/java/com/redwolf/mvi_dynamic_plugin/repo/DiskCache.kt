package com.redwolf.mvi_dynamic_plugin.repo

// 简化磁盘缓存：示例用内存 Map 代替，实际可替换 Room/文件
class SimpleDiskCache<K, V> : DiskCache<K, V> {
    private val map = LinkedHashMap<K, V>()
    override suspend fun get(key: K): V? = map[key]
    override suspend fun put(key: K, value: V) { map[key] = value }
}


interface DiskCache<K, V> { suspend fun get(key: K): V?; suspend fun put(key: K, value: V) }