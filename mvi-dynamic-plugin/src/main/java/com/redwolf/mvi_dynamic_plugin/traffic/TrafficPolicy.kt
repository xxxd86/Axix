package com.redwolf.mvi_dynamic_plugin.traffic

enum class DropStrategy { DROP_OLDEST, DROP_LATEST, COALESCE }


data class TrafficConfig(
    val maxConcurrent: Int = 2,
    val tokenCapacity: Int = 8,
    val tokensPerSecond: Int = 4,
    val dropStrategy: DropStrategy = DropStrategy.COALESCE
)