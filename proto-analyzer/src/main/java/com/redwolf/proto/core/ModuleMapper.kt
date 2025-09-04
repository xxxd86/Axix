package com.redwolf.proto.core

import com.google.gson.JsonParser
import java.nio.file.Files
import java.nio.file.Path

class ModuleMapper private constructor(private val rules: Map<String, String>) {
    companion object {
        fun fromJson(path: Path): ModuleMapper {
            val txt = Files.readString(path)
            val arr = JsonParser.parseString(txt).asJsonArray
            val mp = mutableMapOf<String, String>()
            arr.forEach { el ->
                val o = el.asJsonObject
                val mod = o["module"].asString
                if (o.has("pkg_prefixes")) o["pkg_prefixes"].asJsonArray.forEach { px -> mp[px.asString] = mod }
            }
            return ModuleMapper(mp)
        }
    }
    fun moduleOf(fqcn: String): String {
        val name = fqcn.replace('/', '.')
        var bestKey: String? = null
        var bestVal: String? = null
        rules.forEach { (k, v) ->
            if (name.startsWith(k) && (bestKey == null || k.length > bestKey!!.length)) {
                bestKey = k; bestVal = v
            }
        }
        return bestVal ?: when {
            name.startsWith("android.") || name.startsWith("androidx.") -> ":androidx"
            name.startsWith("kotlin.") -> ":kotlin"
            else -> ":unknown"
        }
    }
}