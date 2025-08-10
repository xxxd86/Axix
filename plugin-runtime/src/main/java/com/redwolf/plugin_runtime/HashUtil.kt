package com.redwolf.plugin_runtime

import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.Locale

internal object HashUtil {
    fun sha256Of(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { fis ->
            val buf = ByteArray(16 * 1024)
            while (true) { val r = fis.read(buf); if (r == -1) break; md.update(buf, 0, r) }
        }
        return md.digest().joinToString("") { String.format(Locale.US, "%02x", it) }
    }
}