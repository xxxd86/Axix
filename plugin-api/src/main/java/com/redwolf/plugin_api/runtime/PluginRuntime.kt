package com.redwolf.plugin_api.runtime

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES
import android.content.pm.Signature
import android.content.res.AssetManager
import android.content.res.Resources
import android.os.Build
import android.system.ErrnoException
import android.system.Os
import android.util.Log
import androidx.annotation.RequiresApi
import com.redwolf.plugin_api.core.PluginProxyActivity
import com.redwolf.plugin_api.distribution.RollbackStore
import dalvik.system.DexClassLoader
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.lang.reflect.Method
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.Locale
import java.util.zip.ZipFile

object PluginRuntime {
    private const val TAG = "PluginRuntime"
    private const val IO = 16 * 1024
    private const val CONNECT = 15_000
    private const val READ = 60_000
    private const val MODE_DIR_700 = 0x1C0  // 等价八进制 0700，十进制 448
    private const val MODE_FILE_444 = 0x124  // 等价八进制 0444，十进制 292
    private fun cachedApk(ctx: Context, module: String, ver: String?) =
        File(
            File(ctx.filesDir, "modules").apply { mkdirs(); lockDir(this) },
            "${module}-${ver}.apk"
        )

    @RequiresApi(Build.VERSION_CODES.P)
    @Throws(Exception::class)
    fun ensure(
        context: Context,
        module: String,
        version: String?,
        pluginStrategy: PluginProxyActivity.PluginStrategy,
        remoteUrl: String?,
        sha256: String?,
        certSha256: String?,
        networkPolicy: PluginProxyActivity.NetworkPolicy
    ): PluginHandle {
        var url = remoteUrl;
        var ver = version;
        var sha = sha256;
        var cert = certSha256
        if (url == null || ver == null || sha == null || cert == null) {
            ModuleRegistry.get(module)?.let { d: ModuleDescriptor ->
                if (url == null) url = d.url
                if (ver == null) ver = d.version
                if (sha == null) sha = d.sha256
                if (cert == null) cert = d.certSha256
            }
        }
        val primary = try {
            ensureApkOrDex(
                context,
                module,
                ver ?: "unknown",
                pluginStrategy,
                url,
                sha,
                cert,
                networkPolicy
            )
                ?: throw IllegalStateException("no source for plugin")
        } catch (e: Throwable) {
            // 失败 → 回滚尝试
            val lkg = RollbackStore.get(context, module)
            if (lkg != null) {
                Log.w(TAG, "ensure fail, trying LKG ${'$'}{lkg.version} : ${'$'}{e.message}")
                val f = cachedApk(context, module, lkg.version)
                if (f.exists()) buildHandle(context, module, f).also {
                    return it
                }
            }
            throw e
        }
        val handle = buildHandle(context, module, primary)
        // 标记本次成功为 LKG
        RollbackStore.saveGood(context, module, RollbackStore.Lkg(ver, url, sha, cert))
        return handle
    }

    private fun buildHandle(ctx: Context, module: String, apk: File): PluginHandle {
        // Dex & so
        val dexOut = ctx.getDir("dex_remote", Context.MODE_PRIVATE)
        val libDir = File(ctx.filesDir, "libs/${'$'}module")
        extractLibs(apk, libDir)
        val cl = DexClassLoader(
            apk.absolutePath, dexOut.absolutePath, libDir.absolutePath, ctx.classLoader
        )

        // 资源
        val am = AssetManager::class.java.newInstance()
        assertNoDuplicateManifest(apk)
        val add: Method = AssetManager::class.java.getMethod("addAssetPath", String::class.java)
        add.invoke(am, apk.absolutePath)
        val res = Resources(am, ctx.resources.displayMetrics, ctx.resources.configuration)

        // 包名（解析失败不阻断）
        var pkg: String? = null
        try {
            val flags =
                if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else 0
            pkg = ctx.packageManager.getPackageArchiveInfo(apk.absolutePath, flags)?.packageName
        } catch (_: Throwable) {
        }

        return PluginHandle(module, apk, cl, res, pkg)
    }

    private fun assertNoDuplicateManifest(apk: File) {
        ZipFile(apk).use { z ->
            var count = 0
            val e = z.entries()
            while (e.hasMoreElements()) {
                if (e.nextElement().name == "AndroidManifest.xml") count++
                if (count > 1) throw IllegalStateException("Duplicate AndroidManifest.xml in ${apk.name}")
            }
        }
    }

    private fun ensureApkOrDex(
        ctx: Context,
        module: String,
        ver: String,
        pluginStrategy: PluginProxyActivity.PluginStrategy,
        url: String?,
        sha256: String?,
        certSha: String?,
        net: PluginProxyActivity.NetworkPolicy
    ): File? {
        val dir = File(ctx.filesDir, "modules").apply { mkdirs() }
        // 1) 计算候选后缀优先级（基于 URL / assets / 默认）
        val exts = resolveExtPriority(ctx, module, url)

        // 2) 在候选后缀集合里依次按策略尝试
        for (ext in exts) {
            val cache = File(dir, "$module-$ver.$ext")

            @RequiresApi(Build.VERSION_CODES.P)
            fun valid(f: File?): Boolean {
                return try {
                    if (f == null || !f.exists()) return false
                    if (sha256 != null && !HashUtil.sha256Of(f)
                            .equals(sha256, ignoreCase = true)
                    ) return false
                    // 仅 APK 需要做证书校验
                    if (ext == "apk" && certSha != null && !verifyCert(
                            ctx,
                            f,
                            certSha
                        )
                    ) return false
                    true
                } catch (_: Throwable) {
                    false
                }
            }

            fun tryAssets(): File? {
                // assets 文件名与后缀对应
                val assetNames = when (ext) {
                    "apk" -> listOf("$module.apk", "$module-plugin.apk")
                    "dex" -> listOf("$module.dex", "classes.dex")
                    "jar" -> listOf("$module.jar")
                    "zip" -> listOf("$module.zip")
                    else -> emptyList()
                }
                for (asset in assetNames) {
                    val out = File(dir, "$module-$ver.${asset.substringAfterLast('.', ext)}")
                    val f = copyAssetIfExists(ctx, asset, out)
                    if (valid(f)) return f
                }
                return null
            }

            @RequiresApi(Build.VERSION_CODES.P)
            fun tryRemote(): File? {
                // 若 URL 提供后缀，与当前 ext 不同，则本轮改写目标文件名以匹配 URL 后缀
                val target = run {
                    val uExt = url?.substringAfterLast('.', "")?.lowercase()
                    if (!uExt.isNullOrBlank() && uExt in setOf("apk", "dex", "jar", "zip"))
                        File(dir, "$module-$ver.$uExt")
                    else cache
                }
                // APK 才传 certSha；DEX/JAR/ZIP 不做证书校验
                val cert = if (target.extension == "apk") certSha else null
                return downloadIfNeeded(ctx, url, target, sha256, cert, net)?.takeIf { valid(it) }
            }

            val got = when (pluginStrategy) {
                PluginProxyActivity.PluginStrategy.LOCAL_ONLY -> {
                    if (valid(cache)) cache else tryAssets()
                }

                PluginProxyActivity.PluginStrategy.LOCAL_FIRST -> {
                    if (valid(cache)) cache
                    else tryAssets() ?: tryRemote()
                }

                PluginProxyActivity.PluginStrategy.REMOTE_FIRST -> {
                    val r = tryRemote()
                    if (valid(r)) r ?: tryAssets()
                    else tryAssets()
                }

                PluginProxyActivity.PluginStrategy.REMOTE_ONLY -> tryRemote()
            }

            if (valid(got)) return got
        }

        // 全部后缀均失败
        return null
    }

    /** 基于 URL/本地 assets 推断加载后缀优先级：返回如 ["apk","dex","jar"] */
    private fun resolveExtPriority(ctx: Context, module: String, url: String?): List<String> {
        fun hasAsset(name: String): Boolean = try {
            ctx.assets.open(name).close(); true
        } catch (_: Throwable) {
            false
        }

        // URL 明示后缀则置顶
        url?.substringAfterLast('.', "")?.lowercase()?.let { e ->
            if (e in setOf("apk", "dex", "jar", "zip")) {
                val base = listOf("apk", "dex", "jar", "zip")
                return listOf(e) + base.filterNot { it == e }
            }
        }

        // 其次看 assets 里是否有对应制品
        when {
            hasAsset("$module.apk") || hasAsset("$module-plugin.apk") ->
                return listOf("apk", "dex", "jar", "zip")

            hasAsset("$module.dex") || hasAsset("classes.dex") ->
                return listOf("dex", "jar", "apk", "zip")

            hasAsset("$module.jar") ->
                return listOf("jar", "dex", "apk", "zip")

            hasAsset("$module.zip") ->
                return listOf("zip", "apk", "dex", "jar")
        }

        // 默认优先 APK
        return listOf("apk", "dex", "jar", "zip")
    }

    private fun lockDir(dir: File) {
        try {
            Os.chmod(dir.absolutePath, MODE_DIR_700)
        } catch (_: Throwable) {
            // 某些文件系统不支持 chmod（或权限不足）时，用 Java API 兜底
            dir.setReadable(true, true)
            dir.setWritable(true, true)
            dir.setExecutable(true, true)
        }
    }

    private fun makeReadOnly(file: File) {
        try {
            Os.chmod(file.absolutePath, MODE_FILE_444)
        } catch (_: ErrnoException) {
            file.setReadable(true, false) // 所有人可读
            file.setWritable(false, false)
            file.setExecutable(false, false)
        }
    }

    private fun copyAssetIfExists(ctx: Context, assetName: String, dest: File): File? = try {
        dest.parentFile?.mkdirs(); lockDir(dest.parentFile!!)
        ctx.assets.open(assetName).use { ins ->
            BufferedOutputStream(FileOutputStream(dest)).use { os ->
                val buf = ByteArray(16 * 1024)
                while (true) {
                    val r = ins.read(buf); if (r == -1) break; os.write(buf, 0, r)
                }
            }
        }
        makeReadOnly(dest)  //拷完就设成只读 0444
        dest
    } catch (_: Throwable) {
        null
    }

    @RequiresApi(Build.VERSION_CODES.P)
    @Throws(Exception::class)
    private fun downloadIfNeeded(
        ctx: Context,
        url: String?,
        to: File,
        sha256: String?,
        certSha: String?,
        net: PluginProxyActivity.NetworkPolicy
    ): File? {
        if (url == null || net == PluginProxyActivity.NetworkPolicy.OFF) return null
        to.parentFile?.mkdirs()
        val tmp = File(to.parentFile, to.name + ".downloading")

        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT; readTimeout = READ; useCaches = false
            val meta = File(to.parentFile, to.name + ".meta")
            val et = if (meta.exists()) meta.readText().trim() else ""
            if (et.isNotEmpty()) setRequestProperty("If-None-Match", et)
            connect()
        }
        val code = conn.responseCode
        val meta = File(to.parentFile, to.name + ".meta")
        if (code == HttpURLConnection.HTTP_NOT_MODIFIED && to.exists()) return to
        if (code / 100 != 2) throw IllegalStateException("HTTP $code")

        BufferedInputStream(conn.inputStream).use { ins ->
            BufferedOutputStream(FileOutputStream(tmp)).use { os ->
                val buf = ByteArray(IO)
                while (true) {
                    val r = ins.read(buf); if (r == -1) break; os.write(buf, 0, r)
                }
            }
        }

        if (sha256 != null) {
            val d = HashUtil.sha256Of(tmp)
            if (!sha256.equals(d, ignoreCase = true)) throw SecurityException("SHA256 mismatch")
        }
        if (certSha != null && !verifyCert(
                ctx,
                tmp,
                certSha
            )
        ) throw SecurityException("Cert mismatch")

        if (to.exists()) to.delete()
        if (!tmp.renameTo(to)) throw IllegalStateException("rename fail")

        conn.getHeaderField("ETag")?.let { tag -> meta.writeText(tag) }
        return to
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun verifyCert(ctx: Context, apk: File, expected: String): Boolean = try {
        val pm = ctx.packageManager
        val pi =
            pm.getPackageArchiveInfo(apk.absolutePath, GET_SIGNING_CERTIFICATES) ?: return false
        val info = pi.signingInfo ?: return false
        val sigs =
            if (info.hasMultipleSigners()) info.apkContentsSigners else info.signingCertificateHistory
        sigs.any { s -> expected.equals(certSha256(s.toByteArray()), ignoreCase = true) }
    } catch (t: Throwable) {
        Log.w(TAG, "verifyCert fail", t); false
    }

    private fun certSha256(certBytes: ByteArray): String {
        val cf = CertificateFactory.getInstance("X.509")
        val cert = cf.generateCertificate(ByteArrayInputStream(certBytes)) as X509Certificate
        val md = MessageDigest.getInstance("SHA-256")
        val d = md.digest(cert.encoded)
        return d.joinToString("") { String.format(Locale.US, "%02x", it) }
    }

    private fun extractLibs(apk: File, outDir: File) {
        try {
            ZipFile(apk).use { zf ->
                val it = zf.entries()
                while (it.hasMoreElements()) {
                    val e = it.nextElement()
                    val name = e.name
                    if (!name.startsWith("lib/") || !name.endsWith(".so")) continue
                    val dest = File(outDir, name.removePrefix("lib/"))
                    dest.parentFile?.mkdirs()
                    zf.getInputStream(e).use { ins ->
                        BufferedOutputStream(FileOutputStream(dest)).use { os ->
                            val buf = ByteArray(IO)
                            while (true) {
                                val r = ins.read(buf); if (r == -1) break; os.write(buf, 0, r)
                            }
                        }
                    }
                }
            }
        } catch (_: Throwable) {
        }
    }


    private fun verifyCertIfNeeded(ctx: Context, apk: File, expected: String): Boolean {
        return try {
            val pm = ctx.packageManager
            if (Build.VERSION.SDK_INT >= 28) {
                val pi = pm.getPackageArchiveInfo(
                    apk.absolutePath,
                    PackageManager.GET_SIGNING_CERTIFICATES
                )
                    ?: return false
                val sigs = pi.signingInfo?.let { si ->
                    if (si.hasMultipleSigners()) si.apkContentsSigners else si.signingCertificateHistory
                } ?: return false
                sigs.any { s ->
                    expected.equals(
                        certSha256(s.toByteArray()),
                        ignoreCase = true
                    )
                }
            } else {
                @Suppress("DEPRECATION")
                val pi = pm.getPackageArchiveInfo(
                    apk.absolutePath,
                    PackageManager.GET_SIGNATURES
                )
                    ?: return false

                @Suppress("DEPRECATION")
                val sigs: Array<Signature> = pi.signatures ?: return false
                sigs.any { s ->
                    expected.equals(
                        certSha256(s.toByteArray()),
                        ignoreCase = true
                    )
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "verifyCert fail", t); false
        }
    }

}