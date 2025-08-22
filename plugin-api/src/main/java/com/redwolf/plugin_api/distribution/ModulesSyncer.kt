package com.redwolf.plugin_api.distribution

import android.content.Context
import android.util.Log
import com.redwolf.plugin_api.runtime.ModuleDescriptor
import com.redwolf.plugin_api.runtime.ModuleRegistry
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import kotlin.random.Random


object ModulesSyncer {
    private const val TAG = "ModulesSyncer"
    private val json = Json { ignoreUnknownKeys = true }
    private val http by lazy {
        OkHttpClient.Builder()
            .callTimeout(30, TimeUnit.SECONDS)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }


    /** 从 base/modules.json 拉取 → 验签（可选）→ 选择稳定版本 → 写入 ModuleRegistry。 */
    suspend fun sync(ctx: Context, base: String): Result<Int> = runCatching {
        val url = base.trimEnd('/') + "/modules.json"
        val req = Request.Builder().url(url).build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("HTTP ${'$'}{resp.code}")
            val body = resp.body?.string() ?: error("empty body")
            val m = json.decodeFromString(serializer<ModulesJson>(), body)
// 验签（可选）
            SignatureVerifier.verifyOrNull(m)?.let { ok -> if (!ok) error("signature verify fail") }


            val userBucket = userBucket(ctx)
            var count = 0
            for (e in m.modules) {
                val ch = e.channels
// 选择：优先 stable；若非 stable 则按 gray 概率放量
                val chosen = when {
                    ch?.stable == true -> true
                    ch?.gray != null && ch.gray > 0f -> userBucket < ch.gray
                    else -> true
                }
                if (!chosen) continue
                ModuleRegistry.put(
                    ModuleDescriptor(
                        name = e.name,
                        version = e.version,
                        url = e.url,
                        sha256 = e.sha256,
                        certSha256 = e.certSha256
                    )
                )
                count++
            }
            Log.i(TAG, "synced ${'$'}count modules from ${'$'}url")
            count
        }
    }


    private fun userBucket(ctx: Context): Float {
        val sp = ctx.getSharedPreferences("modules_syncer_bucket", Context.MODE_PRIVATE)
        var bucket = sp.getFloat("bucket", -1f)
        if (bucket < 0) {
            bucket = Random.nextFloat()
            sp.edit().putFloat("bucket", bucket).apply()
        }
        return bucket
    }
}