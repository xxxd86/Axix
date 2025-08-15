package com.redwolf.plugin_api

import android.annotation.SuppressLint
import android.content.Context
import android.content.ContextWrapper
import android.content.res.AssetManager
import android.content.res.Resources
import android.os.Bundle
import android.view.ViewGroup
import androidx.annotation.StyleRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner

/**
 * Compose 版本的插件页基类：
 * - 仍走 PluginActivity 的 attach/perform* 桥接；
 * - 提供 setContent{}，将 Compose 内容挂到宿主 Activity；
 * - 使用插件 Resources/Theme 的 Context 包装，确保资源读取正确。
 */
import android.app.Activity

import android.content.Intent

import androidx.activity.ComponentActivity


open class PluginComposeActivity : ComponentActivity() {
    protected lateinit var hostActivity: ComponentActivity
    protected lateinit var pluginResources: Resources
    protected lateinit var pluginPackageName: String

    open fun attach(host: Activity, res: Resources?, pkg: String) {
        require(host is ComponentActivity) { "Host must be ComponentActivity/AppCompatActivity" }
        hostActivity = host
        if (res != null) pluginResources = res
        pluginPackageName = pkg
    }

    override fun getResources(): Resources = if (::pluginResources.isInitialized) pluginResources else super.getResources()
    override fun getAssets(): AssetManager = resources.assets
    override fun getPackageName(): String = if (::pluginPackageName.isInitialized) pluginPackageName else super.getPackageName()

    /**
     * 将 Compose 内容挂到宿主的视图树。必须在主线程调用。
     */
    fun setContent(content: @Composable () -> Unit) {
        val cv = ComposeView(hostActivity).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent(content)
        }
        hostActivity.setContentView(cv, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    // —— 无害 super：由宿主通过 perform* 驱动生命周期 ——
    @SuppressLint("MissingSuperCall")
    override fun onCreate(savedInstanceState: Bundle?) { /* no-op */ }

    fun performCreate(saved: Bundle?) { onCreate(saved) }
    fun performStart() { onStart() }
    fun performResume() { onResume() }
    fun performPause() { onPause() }
    fun performStop() { onStop() }
    fun performDestroy() { onDestroy() }
    fun performNewIntent(intent: Intent) { onNewIntent(intent) }

    // —— 插件内便捷跳转（走宿主 Proxy）——
    fun startPlugin(
        module: String,
        activityClass: String,
        strategy: String = "LOCAL_FIRST",
        version: String? = null,
        url: String? = null,
        sha: String? = null,
        certSha256: String? = null,
        themeResId: Int = 0,
        netPolicy: String = "ANY",
        hostProxyFqcn: String = "com.redwolf.axix.PluginProxyActivity"
    ) {
        val ctx: Context = hostActivity
        val i = Intent().apply { setClassName(ctx, hostProxyFqcn) }
        i.putExtra(ProxyKeys.EXTRA_MODULE_NAME, module)
        i.putExtra(ProxyKeys.EXTRA_TARGET_CLASS, activityClass)
        version?.let { i.putExtra("module_version", it) }
        url?.let { i.putExtra("remote_url", it) }
        sha?.let { i.putExtra("sha256", it) }
        certSha256?.let { i.putExtra("cert_sha256", it) }
        i.putExtra("load_strategy", strategy)
        i.putExtra("theme_res_id", themeResId)
        i.putExtra("use_fragment_factory", true)
        i.putExtra("network_policy", netPolicy)
        ctx.startActivity(i)
    }
}