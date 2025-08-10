package com.redwolf.plugin_runtime

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES
import android.content.res.AssetManager
import android.content.res.Resources
import android.os.Build
import android.system.ErrnoException
import android.system.Os
import android.util.Log
import androidx.annotation.RequiresApi
import dalvik.system.DexClassLoader
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.zip.ZipFile

object PluginRuntime {
    private const val TAG = "PluginRuntime"
    private const val IO = 16 * 1024
    private const val CONNECT = 15_000
    private const val READ = 60_000
    private const val MODE_DIR_700  = 0x1C0  // 等价八进制 0700，十进制 448
    private const val MODE_FILE_444 = 0x124  // 等价八进制 0444，十进制 292
    @RequiresApi(Build.VERSION_CODES.P)
    @Throws(Exception::class)
    fun ensure(
        context: Context,
        module: String,
        version: String?,
        strategy: Strategy,
        remoteUrl: String?,
        sha256: String?,
        certSha256: String?,
        networkPolicy: NetworkPolicy
    ): PluginHandle {
        var url = remoteUrl; var ver = version; var sha = sha256; var cert = certSha256
        if (url == null || ver == null || sha == null || cert == null) {
            ModuleRegistry.get(module)?.let { d: ModuleDescriptor ->
                if (url == null) url = d.url
                if (ver == null) ver = d.version
                if (sha == null) sha = d.sha256
                if (cert == null) cert = d.certSha256
            }
        }

        val apk = ensureApkFile(context, module, ver ?: "unknown", strategy, url, sha, cert, networkPolicy)
            ?: throw IllegalStateException("插件不可用：$module")

        val dexOut = context.getDir("dex_remote", Context.MODE_PRIVATE)
        val libDir = File(context.filesDir, "libs/$module").apply { mkdirs() }
        extractLibs(apk, libDir)

        val cl = DexClassLoader(apk.absolutePath, dexOut.absolutePath, libDir.absolutePath, context.classLoader)

        val am = AssetManager::class.java.newInstance()
        assertNoDuplicateManifest(apk)
        val add = AssetManager::class.java.getMethod("addAssetPath", String::class.java)
        add.invoke(am, apk.absolutePath)
        val host = context.resources
        val res = Resources(am, host.displayMetrics, host.configuration)

        val pm = context.packageManager
        val pi = pm.getPackageArchiveInfo(apk.absolutePath, 0)
        val pkg = pi?.packageName ?: context.packageName

        return PluginHandle(module, apk, cl, res, pkg)
    }

    private fun assertNoDuplicateManifest(apk: File) {
        java.util.zip.ZipFile(apk).use { z ->
            var count = 0
            val e = z.entries()
            while (e.hasMoreElements()) {
                if (e.nextElement().name == "AndroidManifest.xml") count++
                if (count > 1) throw IllegalStateException("Duplicate AndroidManifest.xml in ${apk.name}")
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun ensureApkFile(
        ctx: Context,
        module: String,
        ver: String,
        strategy: Strategy,
        url: String?,
        sha256: String?,
        certSha: String?,
        net: NetworkPolicy
    ): File? {
        val dir = File(ctx.filesDir, "modules").apply { mkdirs() }
        val cache = File(dir, "$module-$ver.apk")

        fun valid(f: File?): Boolean = try {
            f != null && f.exists() && (sha256 == null || HashUtil.sha256Of(f).equals(sha256, ignoreCase = true)) &&
                    (certSha == null || verifyCert(ctx, f, certSha))
        } catch (_: Throwable) { false }

        return when (strategy) {
            Strategy.LOCAL_ONLY -> {
                if (valid(cache)) cache else copyAssetIfExists(ctx, "$module.apk",cache).takeIf { valid(it) }
            }
            Strategy.LOCAL_FIRST -> {
                if (valid(cache)) cache
                else copyAssetIfExists(ctx, "$module.apk",cache).takeIf { valid(it) }
                    ?: downloadIfNeeded(ctx, url, cache, sha256, certSha, net)
            }
            Strategy.REMOTE_FIRST -> {
                val r1 = downloadIfNeeded(ctx, url, cache, sha256, certSha, net)
                if (valid(r1)) r1 else copyAssetIfExists(ctx, "$module.apk",cache).takeIf { valid(it) }
            }
            Strategy.REMOTE_ONLY -> {
                downloadIfNeeded(ctx, url, cache, sha256, certSha, net)
            }
        }
    }
    private fun lockDir(dir: File) {
        try { Os.chmod(dir.absolutePath, MODE_DIR_700) } catch (_: Throwable) {
            // Fallback：某些文件系统不支持 chmod（或权限不足）时，用 Java API 兜底
            dir.setReadable(true,  true)
            dir.setWritable(true,  true)
            dir.setExecutable(true, true)
        }
    }

    private fun makeReadOnly(file: File) {
        try { Os.chmod(file.absolutePath, MODE_FILE_444) } catch (_: ErrnoException) {
            file.setReadable(true,  false) // 所有人可读
            file.setWritable(false, false)
            file.setExecutable(false, false)
        }
    }
    private fun copyAssetIfExists(ctx: Context, assetName: String, dest: File): File? = try {
        dest.parentFile?.mkdirs(); lockDir(dest.parentFile!!)
        ctx.assets.open(assetName).use { ins ->
            BufferedOutputStream(FileOutputStream(dest)).use { os ->
                val buf = ByteArray(16 * 1024)
                while (true) { val r = ins.read(buf); if (r == -1) break; os.write(buf, 0, r) }
            }
        }
        makeReadOnly(dest)  // ★★★ 关键：拷完就设成只读 0444
        dest
    } catch (_: Throwable) { null }

    @RequiresApi(Build.VERSION_CODES.P)
    @Throws(Exception::class)
    private fun downloadIfNeeded(
        ctx: Context,
        url: String?,
        to: File,
        sha256: String?,
        certSha: String?,
        net: NetworkPolicy
    ): File? {
        if (url == null || net == NetworkPolicy.OFF) return null
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
                while (true) { val r = ins.read(buf); if (r == -1) break; os.write(buf, 0, r) }
            }
        }

        if (sha256 != null) {
            val d = HashUtil.sha256Of(tmp)
            if (!sha256.equals(d, ignoreCase = true)) throw SecurityException("SHA256 mismatch")
        }
        if (certSha != null && !verifyCert(ctx, tmp, certSha)) throw SecurityException("Cert mismatch")

        if (to.exists()) to.delete()
        if (!tmp.renameTo(to)) throw IllegalStateException("rename fail")

        conn.getHeaderField("ETag")?.let { tag -> meta.writeText(tag) }
        return to
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun verifyCert(ctx: Context, apk: File, expected: String): Boolean = try {
        val pm = ctx.packageManager
        val pi = pm.getPackageArchiveInfo(apk.absolutePath, GET_SIGNING_CERTIFICATES) ?: return false
        val info = pi.signingInfo ?: return false
        val sigs = if (info.hasMultipleSigners()) info.apkContentsSigners else info.signingCertificateHistory
        sigs.any { s -> expected.equals(certSha256(s.toByteArray()), ignoreCase = true) }
    } catch (t: Throwable) {
        Log.w(TAG, "verifyCert fail", t); false
    }

    private fun certSha256(certBytes: ByteArray): String {
        val cf = CertificateFactory.getInstance("X.509")
        val cert = cf.generateCertificate(ByteArrayInputStream(certBytes)) as X509Certificate
        val md = MessageDigest.getInstance("SHA-256")
        val d = md.digest(cert.encoded)
        return d.joinToString("") { String.format(java.util.Locale.US, "%02x", it) }
    }
//    private fun makeReadOnly(file: File) {
//        try {
//            // 0444 = r--r--r--
//            Os.chmod(file.absolutePath, 0o444)
//        } catch (_: ErrnoException) {
//            // 兜底：尽可能去掉写/执行权限
//            file.setReadable(true, /*ownerOnly*/ true)
//            file.setWritable(false, /*ownerOnly*/ false)
//            file.setExecutable(false, /*ownerOnly*/ false)
//        }
//    }
//
//    private fun lockDir(dir: File) {
//        try {
//            // modules 目录建议 0700，避免目录本身被判定不安全
//            Os.chmod(dir.absolutePath, 0o700)
//        } catch (_: Throwable) { /* 忽略 */ }
//    }
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
                            while (true) { val r = ins.read(buf); if (r == -1) break; os.write(buf, 0, r) }
                        }
                    }
                }
            }
        } catch (_: Throwable) {}
    }
}