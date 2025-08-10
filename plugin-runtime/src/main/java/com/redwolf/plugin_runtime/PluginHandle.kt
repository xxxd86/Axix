package com.redwolf.plugin_runtime

import android.content.res.Resources
import java.io.File

data class PluginHandle(
    val moduleName: String,
    val apk: File,
    val cl: ClassLoader,
    val res: Resources,
    val pkg: String
)