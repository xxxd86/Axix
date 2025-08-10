package com.redwolf.plugin_runtime

import java.util.concurrent.ConcurrentHashMap


object ModuleRegistry {
    private val map = mutableMapOf<String, ModuleDescriptor>()
    fun put(d: ModuleDescriptor) { map[d.name] = d }
    fun get(name: String): ModuleDescriptor? = map[name]
}