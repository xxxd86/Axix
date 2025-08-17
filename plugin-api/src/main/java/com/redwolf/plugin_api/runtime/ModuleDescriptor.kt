package com.redwolf.plugin_api.runtime

data class ModuleDescriptor(
    val name: String,
    val version: String,
    val url: String?,
    val sha256: String?,
    val certSha256: String?
)