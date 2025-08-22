package com.redwolf.plugin_api.core

import android.content.Context
import android.content.Intent
import android.content.res.AssetManager
import android.content.res.Resources
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.redwolf.plugin_api.ProxyKeys
import com.redwolf.plugin_api.runtime.NetworkPolicy
import com.redwolf.plugin_api.runtime.PluginHandle
import com.redwolf.plugin_api.runtime.PluginRuntime
import com.redwolf.plugin_api.runtime.PluginStrategy
import kotlinx.coroutines.launch
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.GeneralSecurityException

class PluginProxyActivity : AppCompatActivity() {
    private lateinit var moduleName: String
    private lateinit var targetClass: String
    private var version: String? = null
    private var remoteUrl: String? = null
    private var sha256: String? = null
    private var certSha256: String? = null
    private var pluginStrategy: PluginStrategy = PluginStrategy.LOCAL_FIRST
    private var themeResId: Int = 0
    private var useFragmentFactory: Boolean = true
    private var networkPolicy: NetworkPolicy = NetworkPolicy.ANY

    private var handle: PluginHandle? = null
    private var plugin: PluginActivity? = null
    private lateinit var progress: ProgressBar

    @Volatile
    private var destroyed = false

    @RequiresApi(Build.VERSION_CODES.P)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(null) // 交给插件自行保存/恢复
        parseExtras()
        if (themeResId != 0) setTheme(themeResId)
        attachLoading()
        // ★ 宿主本地类：不走 Runtime，直接用宿主的 CL + Resources
        if (moduleName == ProxyKeys.HOST_NAME) {
            runOnUiThread { launchHostLocal(savedInstanceState) }
            return
        }
        startLoad(savedInstanceState)
    }

    private fun launchHostLocal(saved: Bundle?) {
        try {
            val cls = Class.forName(targetClass)
            require(PluginActivity::class.java.isAssignableFrom(cls)) {
                "目标需继承 PluginActivity"
            }
            @Suppress("UNCHECKED_CAST")
            val inst = (cls as Class<out PluginActivity>)
                .getDeclaredConstructor().newInstance()
            // 宿主资源/包名直接注入
            inst.attach(this, resources, packageName)
            progress.visibility = View.GONE
            plugin = inst
            inst.performCreate(saved)
        } catch (e: Throwable) {
            postToastFail(e); finish()
        }
    }

    private fun postToastFail(t: Throwable) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            toastFail(t)
        } else {
            runOnUiThread { toastFail(t) }
        }
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun startLoad(saved: Bundle?) {
        if (moduleName == ProxyKeys.HOST_LOCAL_MODULE) {
            try {
                val cls = classLoader.loadClass(targetClass)
                require(PluginActivity::class.java.isAssignableFrom(cls)) { "目标需继承 PluginActivity" }
                @Suppress("UNCHECKED_CAST")
                val inst = (cls as Class<out PluginActivity>).getDeclaredConstructor().newInstance()
                inst.attach(this, resources, packageName)
                runOnUiThread {
                    progress.visibility = ProgressBar.GONE
                    plugin = inst
                    plugin?.performCreate(saved)
                }
            } catch (t: Throwable) {
                toastFail(t); finish()
            }
            return
        }
        lifecycleScope.launch {
            try {
                val h = PluginRuntime.ensure(
                    context = this@PluginProxyActivity,
                    module = moduleName,
                    version = version,
                    pluginStrategy = pluginStrategy,
                    remoteUrl = remoteUrl,
                    sha256 = sha256,
                    certSha256 = certSha256,
                    networkPolicy = networkPolicy
                )
                if (destroyed) return@launch
                runOnUiThread {
                    val cls = h.cl.loadClass(targetClass)
                    require(PluginActivity::class.java.isAssignableFrom(cls)) { "目标需继承 PluginActivity" }

                    @Suppress("UNCHECKED_CAST")
                    val cTor = (cls as Class<out PluginActivity>).getDeclaredConstructor()
                        .apply { isAccessible = true }
                    val inst = cTor.newInstance()

                    val loadedPkg = cls.`package`?.name ?: ""
                    val effectivePkg =
                        if (h.pkg.isBlank() || !loadedPkg.startsWith(h.pkg)) loadedPkg else h.pkg
                    // ★ 用 h.res，不要用 handle?.res；先赋 handle，再回到主线
                    inst.attach(this@PluginProxyActivity, h.res, effectivePkg)
                    handle = h
                    progress.visibility = ProgressBar.GONE
                    try {
                        plugin = inst
                        plugin?.performCreate(saved)
                    } catch (e: Throwable) {
                        toastFail(e); finish()
                    }
                }
            } catch (t: Throwable) {
                if (!destroyed) runOnUiThread { toastFail(t); finish() }
            }
        }
    }

    private fun toastFail(t: Throwable) {
        val root = generateSequence(t) { it.cause }.last()
        val msg = when (root) {
            is UnknownHostException -> "网络不可用/域名解析失败"
            is ConnectException -> "网络无法连接"
            is SocketTimeoutException -> "网络超时"
            is GeneralSecurityException -> root.message ?: "安全校验失败"
            is SecurityException -> root.message ?: "安全校验失败"
            is ClassNotFoundException -> "找不到类：$targetClass"
            else -> root.message ?: root::class.java.simpleName
        }
        Toast.makeText(this, "加载失败：$msg", Toast.LENGTH_LONG)
            .show()
    }

    override fun onStart() {
        super.onStart(); plugin?.performStart()
    }

    override fun onResume() {
        super.onResume(); plugin?.performResume()
    }

    override fun onPause() {
        plugin?.performPause(); super.onPause()
    }

    override fun onStop() {
        plugin?.performStop(); super.onStop()
    }

    override fun onDestroy() {
        destroyed = true
        plugin?.performDestroy(); plugin = null
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState); plugin?.performSaveInstanceState(outState)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent); setIntent(intent); plugin?.performNewIntent(intent)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data); plugin?.performActivityResult(
            requestCode,
            resultCode,
            data
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(
            requestCode,
            permissions,
            grantResults
        ); plugin?.performRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    override fun onBackPressed() {
        val handled = plugin?.performBackPressed() == true
        if (!handled && !isFinishing) super.onBackPressed()
    }

    override fun getClassLoader(): ClassLoader = handle?.cl ?: super.getClassLoader()
    override fun getResources(): Resources = handle?.res ?: super.getResources()
    override fun getAssets(): AssetManager = (handle?.res ?: super.getResources()).assets

    private fun attachLoading() {
        val root = FrameLayout(this)
        progress = ProgressBar(this)
        val size = (48 * resources.displayMetrics.density).toInt()
        val lp = FrameLayout.LayoutParams(size, size).apply { gravity = Gravity.CENTER }
        root.addView(progress, lp)
        setContentView(
            root,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
    }

    private fun parseExtras() {
        intent.extras?.let { e ->
            moduleName = e.getString(ProxyKeys.EXTRA_MODULE_NAME, "")
            targetClass = e.getString(ProxyKeys.EXTRA_TARGET_CLASS, "")
            version = e.getString(ProxyKeys.EXTRA_VERSION)
            remoteUrl = e.getString(ProxyKeys.EXTRA_REMOTE_URL)
            sha256 = e.getString(ProxyKeys.EXTRA_SHA256)
            certSha256 = e.getString(ProxyKeys.EXTRA_CERT_SHA256)
            pluginStrategy =
                e.getString(ProxyKeys.EXTRA_STRATEGY)?.let { PluginStrategy.valueOf(it) }
                    ?: PluginStrategy.LOCAL_FIRST
            themeResId = e.getInt(ProxyKeys.EXTRA_THEME_RES_ID, 0)
            useFragmentFactory = e.getBoolean(ProxyKeys.EXTRA_USE_FRAGMENT_FACTORY, true)
            networkPolicy =
                e.getString(ProxyKeys.EXTRA_NETWORK_POLICY)?.let { NetworkPolicy.valueOf(it) }
                    ?: NetworkPolicy.ANY
        }
        require(moduleName.isNotBlank() && targetClass.isNotBlank()) { "缺少必要参数" }
    }

    companion object {
        @JvmStatic
        fun createIntent(
            ctx: Context,
            moduleName: String?,
            targetActivityClass: String,
            version: String? = null,
            remoteUrl: String? = null,
            sha256: String? = null,
            certSha256: String? = null,
            pluginStrategy: PluginStrategy = PluginStrategy.LOCAL_FIRST,
            themeResId: Int = 0,
            useFragmentFactory: Boolean = true,
            networkPolicy: NetworkPolicy = NetworkPolicy.ANY
        ): Intent = Intent(ctx, PluginProxyActivity::class.java).apply {
            putExtra(ProxyKeys.EXTRA_MODULE_NAME, moduleName)
            putExtra(ProxyKeys.EXTRA_TARGET_CLASS, targetActivityClass)
            version?.let { putExtra(ProxyKeys.EXTRA_VERSION, it) }
            remoteUrl?.let { putExtra(ProxyKeys.EXTRA_REMOTE_URL, it) }
            sha256?.let { putExtra(ProxyKeys.EXTRA_SHA256, it) }
            certSha256?.let { putExtra(ProxyKeys.EXTRA_CERT_SHA256, it) }
            putExtra(ProxyKeys.EXTRA_STRATEGY, pluginStrategy.name)
            putExtra(ProxyKeys.EXTRA_THEME_RES_ID, themeResId)
            putExtra(ProxyKeys.EXTRA_USE_FRAGMENT_FACTORY, useFragmentFactory)
            putExtra(ProxyKeys.EXTRA_NETWORK_POLICY, networkPolicy.name)
        }
    }
}