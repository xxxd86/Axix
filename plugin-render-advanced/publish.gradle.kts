import java.io.File
import java.util.zip.ZipOutputStream
import java.util.zip.ZipEntry

// ----------------- SDK 工具定位（跨平台） -----------------
fun sdkRoot(): String =
    System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT")
    ?: throw GradleException("请配置 ANDROID_HOME 或 ANDROID_SDK_ROOT")

fun sdkTool(name: String): String {
    val isWin = org.gradle.internal.os.OperatingSystem.current().isWindows
    val mapped = when (name) {
        "aapt2"     -> if (isWin) "aapt2.exe" else "aapt2"
        "d8"        -> if (isWin) "d8.bat" else "d8"
        "zipalign"  -> if (isWin) "zipalign.exe" else "zipalign"
        "apksigner" -> if (isWin) "apksigner.bat" else "apksigner"
        else        -> name
    }
    val btDir = File(sdkRoot(), "build-tools")
    val ver = btDir.listFiles()?.sorted()?.lastOrNull()
        ?: throw GradleException("未找到 build-tools，请用 sdkmanager 安装（如 34.0.0）")
    return File(ver, mapped).absolutePath
}

fun sdkPlatformJar(api: String = "30"): String {
    val jar = File(sdkRoot(), "platforms/android-$api/android.jar")
    if (!jar.exists()) throw GradleException("缺少 $jar，请安装 platforms;android-$api")
    return jar.absolutePath
}

// 供 d8 作为 classpath，避免“找不到 PluginActivity”
fun pluginApiJar(): File {
    val aar = project(":plugin-api").layout.buildDirectory
        .file("outputs/aar/plugin-api-release.aar").get().asFile
    if (!aar.exists()) throw GradleException("请先执行 :plugin-api:assembleRelease")
    val out = layout.buildDirectory.dir("tmp/plugin-api-jar").get().asFile.apply { mkdirs() }
    copy { from(zipTree(aar)); into(out) }
    val jar = File(out, "classes.jar")
    if (!jar.exists()) throw GradleException("plugin-api AAR 内无 classes.jar")
    return jar
}

// ----------------- 生成轻量“运行包”：*-plugin.apk -----------------
tasks.register("makePluginApk") {
    group = "plugin"
    description = "从 AAR 生成可由宿主加载的插件 APK（含 resources.arsc + classes.dex）"
    dependsOn(":plugin-api:assembleRelease", "assembleRelease")

    doLast {
        val aapt2 = sdkTool("aapt2")
        val d8 = sdkTool("d8")
        val androidJar = sdkPlatformJar("30")
        val apiJar = pluginApiJar()

        val work = layout.buildDirectory.dir("tmp/plugin/${project.name}").get().asFile
        work.deleteRecursively(); work.mkdirs()

        val aar = layout.buildDirectory.file("outputs/aar/${project.name}-release.aar").get().asFile
        copy { from(zipTree(aar)); into(work) }

        // aapt2 compile
        val compiled = File(work, "compiled").apply { mkdirs() }
        val resDir = File(work, "res")
        if (resDir.exists()) {
            exec {
                commandLine(aapt2, "compile", "--dir", resDir.absolutePath, "-o", compiled.absolutePath)
            }
        }

        // aapt2 link → 生成带二进制清单 & resources.arsc 的 APK（暂不含 dex）
        val unsignedResApk = File(work, "unsigned-res.apk")
        val flats = compiled.listFiles()?.map { it.absolutePath }?.toTypedArray() ?: emptyArray()
        exec {
            commandLine(
                aapt2, "link",
                "-o", unsignedResApk.absolutePath,
                "-I", androidJar,
                "--manifest", File(work, "AndroidManifest.xml").absolutePath,
                "--min-sdk-version", "30",
                "--target-sdk-version", "30",
                "--auto-add-overlay",
                *flats
            )
        }

        // d8 → classes.dex（提供 android.jar + plugin-api classes.jar）
        exec {
            commandLine(
                d8,
                "--release",
                "--min-api", "30",
                "--lib", androidJar,
                "--classpath", apiJar.absolutePath,
                "--output", work.absolutePath,
                File(work, "classes.jar").absolutePath
            )
        }

        // 追加 classes.dex，保持已有条目压缩方式（特别是 resources.arsc 必须 Stored）
        ant.withGroovyBuilder {
            "zip"(*arrayOf(
                "update" to true,
                "keepcompression" to true,                   // ★ 不改变已有条目压缩方式（保住 resources.arsc 的 Stored）
                "destfile" to unsignedResApk.absolutePath
            )) {
                "fileset"(*arrayOf("file" to File(work, "classes.dex").absolutePath))
            }
        }

        // 输出到 build/outputs/pluginApk
        val outDir = layout.buildDirectory.dir("outputs/pluginApk").get().asFile.apply { mkdirs() }
        copy {
            from(unsignedResApk)
            into(outDir)
            rename { "${project.name}-plugin.apk" }
        }
        println("plugin.apk 生成：${File(outDir, "${project.name}-plugin.apk").absolutePath}")
    }
}

// ----------------- 生成“可安装测试包”：zipalign + 签名 -----------------
fun jdkTool(name: String): String {
    val javaHome = System.getenv("JAVA_HOME") ?: System.getProperty("java.home")
    val isWin = org.gradle.internal.os.OperatingSystem.current().isWindows
    val bin = File(javaHome, "bin/${if (isWin) "$name.exe" else name}")
    return if (bin.exists()) bin.absolutePath else name
}

tasks.register("makePluginApkInstallable") {
    group = "plugin"
    description = "对插件 APK 进行 zipalign 和签名，便于 adb 安装做 UI 单测"
    dependsOn("makePluginApk")

    doLast {
        val zipalign = sdkTool("zipalign")
        val apksigner = sdkTool("apksigner")

        val outDir = layout.buildDirectory.dir("outputs/pluginApk").get().asFile
        val inApk = File(outDir, "${project.name}-plugin.apk")
        require(inApk.exists()) { "未找到输入 APK：$inApk，请先执行 :${project.path}:makePluginApk" }

        val aligned = File(outDir, "${project.name}-plugin-installable-aligned.apk")
        val signed  = File(outDir, "${project.name}-plugin-installable-signed.apk")
        aligned.delete(); signed.delete()

        // zipalign （R+ 要求 resources.arsc 不压缩且 4 字节对齐）
        exec { commandLine(zipalign, "-p", "4", inApk.absolutePath, aligned.absolutePath) }

        // debug keystore（不存在则生成）
        val debugKs = File(System.getProperty("user.home"), ".android/debug.keystore")
        if (!debugKs.exists()) {
            debugKs.parentFile.mkdirs()
            exec {
                commandLine(
                    jdkTool("keytool"),
                    "-genkeypair", "-v",
                    "-keystore", debugKs.absolutePath,
                    "-storepass", "android",
                    "-keypass", "android",
                    "-alias", "androiddebugkey",
                    "-keyalg", "RSA",
                    "-keysize", "2048",
                    "-validity", "10000",
                    "-dname", "CN=Android Debug,O=Android,C=US"
                )
            }
        }

        // apksigner（zipalign 之后再签名）
        exec {
            commandLine(
                apksigner, "sign",
                "--ks", debugKs.absolutePath,
                "--ks-key-alias", "androiddebugkey",
                "--ks-pass", "pass:android",
                "--key-pass", "pass:android",
                "--out", signed.absolutePath,
                aligned.absolutePath
            )
        }

        println("✅ Installable signed APK: ${signed.absolutePath}")
        println("   安装示例：adb install -r \"${signed.absolutePath}\"")
    }
}

fun d8Tool() = sdkTool("d8") // 复用上文工具函数

tasks.register("makeTinyDexBundle") {
    group = "plugin"; description = "生成 dex-only 胶囊（.dex.jar）"
    dependsOn("assembleRelease", ":plugin-api:assembleRelease")
    doLast {
        val androidJar = sdkPlatformJar("30")
        val d8 = d8Tool()
        val apiJar = pluginApiJar()

        val work = layout.buildDirectory.dir("tmp/tiny_dex").get().asFile.apply { deleteRecursively(); mkdirs() }
        // 从 AAR 解 classes.jar
        val aar = layout.buildDirectory.file("outputs/aar/${project.name}-release.aar").get().asFile
        val jarDir = File(work, "jar").apply { mkdirs() }
        copy { from(zipTree(aar)); into(jarDir) }
        val clsJar = File(jarDir, "classes.jar")

        // d8 → classes.dex
        exec {
            commandLine(
                d8, "--release",
                "--no-desugaring",         // 可选：明确不做核心库 desugar
                "--min-api", "30",
                "--lib", androidJar,       // 仅引用，不会打包
                "--classpath", apiJar.absolutePath, // 仅引用，不会打包
                "--output", work.absolutePath,
                clsJar.absolutePath        // ★ 唯一的程序输入
            )
        }

        // 打成 .dex.jar（供 DexClassLoader 使用）
        val outDir = layout.buildDirectory.dir("outputs/pluginApk").get().asFile.apply { mkdirs() }
        val dexJar = File(outDir, "${project.name}-tiny.dex.jar")
        ZipOutputStream(dexJar.outputStream()).use { zos ->
            val dex = File(work, "classes.dex")
            zos.putNextEntry(ZipEntry("classes.dex"))
            dex.inputStream().use { it.copyTo(zos) }
            zos.closeEntry()
        }
        println("Dex-only → ${dexJar.absolutePath}")
    }
}

tasks.register("makeTinyDexBundleRenderAdvanced") {
    group = "plugin"; description = "生成 render-advanced 的 dex-only 胶囊 (.dex.jar)"
    dependsOn("assembleRelease", ":plugin-api:assembleRelease")
    doLast {
        val androidJar = sdkPlatformJar("30"); val d8 = sdkTool("d8"); val apiJar = pluginApiJar()
        val work = layout.buildDirectory.dir("tmp/tiny_dex").get().asFile.apply { deleteRecursively(); mkdirs() }
        val aar = layout.buildDirectory.file("outputs/aar/${project.name}-release.aar").get().asFile
        val jarDir = File(work, "jar").apply { mkdirs() }
        copy { from(zipTree(aar)); into(jarDir) }
        val clsJar = File(jarDir, "classes.jar")
        exec {
            commandLine(
                d8, "--release",
                "--no-desugaring",         // 可选：明确不做核心库 desugar
                "--min-api", "30",
                "--lib", androidJar,       // 仅引用，不会打包
                "--classpath", apiJar.absolutePath, // 仅引用，不会打包
                "--output", work.absolutePath,
                clsJar.absolutePath        // ★ 唯一的程序输入
            )
        }
        val outDir = layout.buildDirectory.dir("outputs/pluginApk").get().asFile.apply { mkdirs() }
        val dexJar = File(outDir, "plugin-render-advanced-tiny.dex.jar")
        ZipOutputStream(dexJar.outputStream()).use { zos ->
            val dex = File(work, "classes.dex"); zos.putNextEntry(ZipEntry("classes.dex")); dex.inputStream().use { it.copyTo(zos) }; zos.closeEntry()
        }
        println("Dex-only → ${dexJar.absolutePath}")
    }
}
