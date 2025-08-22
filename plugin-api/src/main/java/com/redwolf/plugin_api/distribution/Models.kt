package com.redwolf.plugin_api.distribution

import kotlinx.serialization.Serializable


@Serializable
data class ModulesJson(
    val schema: Int,
    val generatedAt: String,
    val minHostApi: Int = 1,
    val modules: List<ModuleEntry>,
    val sign: Sign? = null,
)


@Serializable
data class ModuleEntry(
    val name: String,
    val version: String,
    val url: String,
    val sha256: String,
    val certSha256: String? = null,
    val size: Long? = null,
    val minHostApi: Int = 1,
    val capabilities: List<String>? = null,
    val channels: Channels? = null,
    val notes: String? = null,
)


@Serializable
data class Channels(val stable: Boolean = true, val gray: Float = 0f)


@Serializable
data class Sign(val alg: String, val kid: String, val sig: String)