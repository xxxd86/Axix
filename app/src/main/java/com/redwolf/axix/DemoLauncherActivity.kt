package com.redwolf.axix

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.redwolf.plugin_api.ProxyKeys
import com.redwolf.plugin_runtime.ModuleDescriptor
import com.redwolf.plugin_runtime.ModuleRegistry
import com.redwolf.plugin_runtime.NetworkPolicy
import com.redwolf.plugin_runtime.PluginRuntime
import com.redwolf.plugin_runtime.Strategy

@RequiresApi(Build.VERSION_CODES.P)
class DemoLauncherActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 可选：注册一条远端分发（实际应由 modules.json 同步器完成）
        ModuleRegistry.put(
            ModuleDescriptor(
                name = "render-advanced",
                version = "1.5.0",
                url = "http://192.168.0.101:8080/plugins/render-advanced/1.5.0/render-advanced-1.5.0-plugin.apk",
                sha256 = null, // 可从 mock 的 /modules.json 拿到后再覆盖
                certSha256 = null
            )
        )

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        addBtn(
            root,
            "① 本地内置（assets 命中）"
        ) { v: View? -> DemoLocalAssets.run(this) }
        addBtn(
            root,
            "② 本地缓存命中（已预热）"
        ) { v: View? -> DemoCached.run(this) }
        addBtn(root, "③ 远端下载并加载") { v: View? -> DemoRemote.run(this) }
        addBtn(root, "④ 宿主本地类（无需 APK）") { DemoHostLocal.run(this) }
        addBtn(root, "④ 宿主本地 LocalHelloActivity (__host__) ") {
            val intent = PluginProxyActivity.createIntent(
                ctx = this,
                moduleName = ProxyKeys.HOST_LOCAL_MODULE,
                targetActivityClass = "com.redwolf.axix.LocalHelloActivity",
                version = null, remoteUrl = null, sha256 = null, certSha256 = null,
                strategy = Strategy.LOCAL_ONLY,
                themeResId = 0, useFragmentFactory = true,
                networkPolicy = NetworkPolicy.OFF
            )
            startActivity(intent)
        }
        setContentView(root)
    }

    // ① 本地 assets
    object DemoLocalAssets {
        fun run(ctx: Context) {
            val i = PluginProxyActivity.createIntent(
                ctx,
                "render-advanced",
                "com.redwolf.plugin_render_advanced.PostEnhancedActivity",
                "1.0.0",
                null,
                null,
                null,
                Strategy.LOCAL_FIRST,
                0,
                true,
                NetworkPolicy.OFF
            )
            ctx.startActivity(i)
        }
    }

    // ② 本地缓存（先预热，再打开）
    object DemoCached {
        @RequiresApi(Build.VERSION_CODES.P)
        fun run(ctx: Context) {
            Thread {
                try {
                    PluginRuntime.ensure(
                        ctx, "render-advanced", "1.5.0",
                        Strategy.REMOTE_FIRST, null, null, null,
                        NetworkPolicy.UNMETERED
                    )
                } catch (ignored: Throwable) {
                }
            }.start()

            val i = PluginProxyActivity.createIntent(
                ctx,
                "render-advanced",
                "com.redwolf.plugin_render_advanced.PostEnhancedActivity",
                "1.5.0",
                null, null, null,
                Strategy.LOCAL_FIRST,
                0, true,
                NetworkPolicy.ANY
            )
            ctx.startActivity(i)
        }
    }

    // ③ 远端下载
    object DemoRemote {
        fun run(ctx: Context) {
            val d = ModuleRegistry.get("render-advanced")
            val i = PluginProxyActivity.createIntent(
                ctx,
                d?.name,
                "com.redwolf.plugin_render_advanced.PostEnhancedActivity",
                d?.version,
                d?.url,
                d?.sha256,
                d?.certSha256,
                Strategy.REMOTE_FIRST,
                0, true,
                NetworkPolicy.ANY
            )
            ctx.startActivity(i)
        }
    }

    object DemoHostLocal {
        fun run(ctx: Context) {
            val intent = PluginProxyActivity.createIntent(
                ctx = ctx,
                moduleName = HOST_LOCAL_MODULE, // 特殊值
                targetActivityClass = "com.redwolf.axix.LocalHelloActivity",
                version = null,
                remoteUrl = null,
                sha256 = null,
                certSha256 = null,
                strategy = Strategy.LOCAL_ONLY,
                themeResId = 0,
                useFragmentFactory = true,
                networkPolicy = NetworkPolicy.OFF
            )
            ctx.startActivity(intent)
        }
    }

    companion object {
        const val HOST_LOCAL_MODULE = "__host__"
        private fun addBtn(parent: ViewGroup, text: String?, l: View.OnClickListener?) {
            val b = Button(parent.context)
            b.text = text
            b.setOnClickListener(l)
            parent.addView(b)
        }
    }
}
