package com.redwolf.plugin_api.core

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.AssetManager
import android.content.res.Resources
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import com.redwolf.plugin_api.ProxyKeys

//非必要不要乱动，影响动态打包
open class PluginActivity : Activity() {
    protected lateinit var hostActivity: Activity
    protected lateinit var pluginResources: Resources
    protected lateinit var pluginPackageName: String

    open fun attach(host: Activity, res: Resources?, pkg: String?) {
        hostActivity = host
        if (res != null) pluginResources = res
        if (pkg != null) {
            pluginPackageName = pkg
        }
    }

    override fun getResources(): Resources =
        if (::pluginResources.isInitialized) pluginResources else super.getResources()

    override fun getAssets(): AssetManager = resources.assets
    override fun getPackageName(): String =
        if (::pluginPackageName.isInitialized) pluginPackageName else super.getPackageName()

    override fun setContentView(layoutResID: Int) {
        hostActivity.setContentView(layoutResID)
    }

    override fun setContentView(view: View?) {
        if (view != null) hostActivity.setContentView(view)
    }

    override fun setContentView(view: View?, params: ViewGroup.LayoutParams?) {
        if (view != null && params != null) hostActivity.setContentView(view, params)
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : View?> findViewById(id: Int): T = hostActivity.findViewById(id)

    // ---------- 安全的 no-op super，实现给子类调用 ----------
    @SuppressLint("MissingSuperCall")
    override fun onCreate(savedInstanceState: Bundle?) { /* no-op */
    }

    @SuppressLint("MissingSuperCall")
    override fun onStart() { /* no-op */
    }

    @SuppressLint("MissingSuperCall")
    override fun onResume() { /* no-op */
    }

    @SuppressLint("MissingSuperCall")
    override fun onPause() { /* no-op */
    }

    @SuppressLint("MissingSuperCall")
    override fun onStop() { /* no-op */
    }

    @SuppressLint("MissingSuperCall")
    override fun onDestroy() { /* no-op */
    }

    @SuppressLint("MissingSuperCall")
    override fun onNewIntent(intent: Intent) { /* no-op */
    }

    @SuppressLint("MissingSuperCall")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) { /* no-op */
    }

    @SuppressLint("MissingSuperCall")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) { /* no-op */
    }

    @SuppressLint("MissingSuperCall")
    override fun onSaveInstanceState(outState: Bundle) { /* no-op */
    }

    // ---------- 宿主可见的桥接 ----------
    fun performCreate(saved: Bundle?) {
        onCreate(saved)
    }

    fun performStart() {
        onStart()
    }

    fun performResume() {
        onResume()
    }

    fun performPause() {
        onPause()
    }

    fun performStop() {
        onStop()
    }

    fun performDestroy() {
        onDestroy()
    }

    fun performNewIntent(intent: Intent) {
        onNewIntent(intent)
    }

    fun performActivityResult(req: Int, res: Int, data: Intent?) {
        onActivityResult(req, res, data)
    }

    fun performRequestPermissionsResult(
        req: Int,
        perms: Array<out String>,
        grantResults: IntArray
    ) {
        onRequestPermissionsResult(req, perms, grantResults)
    }

    fun performSaveInstanceState(out: Bundle) {
        onSaveInstanceState(out)
    }

    /** 插件有机会消费返回；返回 true 表示已处理，不需要宿主再退栈 */
    open fun onBackPressedPlugin(): Boolean = false

    /** 宿主或插件主动触发返回。未消费则安全地 finish 宿主（不调用系统 Activity.onBackPressed）。 */
    fun performBackPressed(): Boolean {
        val handled = runCatching { onBackPressedPlugin() }.getOrDefault(false)
        if (!handled) hostActivity.finish()     // 不走 Activity.onBackPressed，避免 NPE/递归
        return handled
    }

    @Deprecated("插件请改覆写 onBackPressedPlugin()；此方法在伪 Activity 上不安全。")
    final override fun onBackPressed() { /* no-op，防误用 */
    }

    // 便捷启动其他插件页或宿主本地页（保持你现有实现即可）
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
        hostProxyFqcn: String = ProxyKeys.HOST_PLUGIN_PROXY_ACTIVITY
    ) {
        val ctx: Context = hostActivity
        val i = Intent().apply { setClassName(ctx, hostProxyFqcn) }
        i.putExtra(ProxyKeys.EXTRA_MODULE_NAME, module)
        i.putExtra(ProxyKeys.EXTRA_TARGET_CLASS, activityClass)
        version?.let { i.putExtra(ProxyKeys.EXTRA_VERSION, it) }
        url?.let { i.putExtra(ProxyKeys.EXTRA_REMOTE_URL, it) }
        sha?.let { i.putExtra(ProxyKeys.EXTRA_SHA256, it) }
        certSha256?.let { i.putExtra(ProxyKeys.EXTRA_CERT_SHA256, it) }
        i.putExtra(ProxyKeys.EXTRA_STRATEGY, strategy)
        i.putExtra(ProxyKeys.EXTRA_THEME_RES_ID, themeResId)
        i.putExtra(ProxyKeys.EXTRA_USE_FRAGMENT_FACTORY, true)
        i.putExtra(ProxyKeys.EXTRA_NETWORK_POLICY, netPolicy)
        ctx.startActivity(i)
    }
}