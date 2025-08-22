package com.redwolf.plugin_api.distribution

import android.content.Context
import androidx.core.content.edit


/** Last-Known-Good 存储：记录某模块最近成功运行的版本与校验信息 */
object RollbackStore {
    private const val SP = "plugin_lkg"


    data class Lkg(
        val version: String?,
        val url: String?,
        val sha256: String?,
        val certSha256: String?
    )


    fun saveGood(ctx: Context, module: String, lkg: Lkg) {
        ctx.getSharedPreferences(SP, Context.MODE_PRIVATE).edit {
            putString(key(module, "ver"), lkg.version)
            putString(key(module, "url"), lkg.url)
            putString(key(module, "sha"), lkg.sha256)
            putString(key(module, "cert"), lkg.certSha256)
        }
    }


    fun get(ctx: Context, module: String): Lkg? {
        val sp = ctx.getSharedPreferences(SP, Context.MODE_PRIVATE)
        val v = sp.getString(key(module, "ver"), null) ?: return null
        return Lkg(
            version = v,
            url = sp.getString(key(module, "url"), null),
            sha256 = sp.getString(key(module, "sha"), null),
            certSha256 = sp.getString(key(module, "cert"), null)
        )
    }


    private fun key(m: String, k: String) = "${m}_$k"
}