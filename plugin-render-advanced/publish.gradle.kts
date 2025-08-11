import java.io.File

fun Project.sdkPlatformJar(apiLevel: String = "30"): String {
    val sdk = System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT")
    ?: "C:/Users/28767/AppData/Local/Android/Sdk"
    val platforms = file("$sdk/platforms")
    // 优先用你指定的 apiLevel（比如 30），没有就挑最高可用
    val chosen = listOf(File(platforms, "android-$apiLevel"))
        .firstOrNull { it.exists() }
        ?: (platforms.listFiles()?.sorted()?.lastOrNull()
            ?: throw GradleException("未安装任何 Android 平台，请先 sdkmanager \"platforms;android-$apiLevel\""))
    val jar = File(chosen, "android.jar")
    if (!jar.exists()) throw GradleException("缺少 ${jar.absolutePath}，请安装对应平台")
    return jar.absolutePath
}
fun Project.pluginApiClassesJar(): File {
    val aar = project(":plugin-api").layout.buildDirectory
        .file("outputs/aar/plugin-api-release.aar").get().asFile
    if (!aar.exists()) throw GradleException("找不到 plugin-api AAR：${aar.absolutePath}，请先执行 :plugin-api:assembleRelease")
    val out = layout.buildDirectory.dir("tmp/plugin-api-jar").get().asFile
    out.mkdirs()
    copy { from(zipTree(aar)); into(out) }
    val jar = File(out, "classes.jar")
    if (!jar.exists()) throw GradleException("plugin-api AAR 内无 classes.jar")
    return jar
}
// 生成可安装的 APK：aapt2(compile/link) + d8 + zipalign + apksigner
// ✅ 已兼容 Windows（.bat/.exe）与 macOS/Linux，并避免直接使用已弃用的 buildDir 读取方式
fun Project.sdkTool(tool: String): String {
    val sdk = System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT")
    ?: "C:/Users/28767/AppData/Local/Android/Sdk"
    val isWin = org.gradle.internal.os.OperatingSystem.current().isWindows
    val mapped = when (tool) {
        "aapt2" -> if (isWin) "aapt2.exe" else "aapt2"
        "d8" -> if (isWin) "d8.bat" else "d8"
        "zipalign" -> if (isWin) "zipalign.exe" else "zipalign"
        "apksigner" -> if (isWin) "apksigner.bat" else "apksigner"
        else -> tool
    }
    val btDir = file("$sdk/build-tools")
    val ver = btDir.listFiles()?.maxOrNull()
        ?: throw GradleException("build-tools 未安装，运行 sdkmanager \"build-tools;34.0.0\" 等版本")
    return File(ver, mapped).absolutePath
}
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
// 轻量动态加载包（供宿主 DexClassLoader 使用）
/** 产出可供宿主加载的 plugin.apk（含二进制 manifest + resources.arsc + classes.dex） */
tasks.register("makePluginApk") {
    dependsOn(":plugin-api:assembleRelease", "assembleRelease")
    doLast {
        val work = layout.buildDirectory.dir("tmp/plugin/${project.name}").get().asFile
        work.deleteRecursively(); work.mkdirs()

        val aar = layout.buildDirectory.file("outputs/aar/${project.name}-release.aar").get().asFile
        copy { from(zipTree(aar)); into(work) }

        val aapt2 = sdkTool("aapt2")
        val d8 = sdkTool("d8")
        val androidJar = sdkPlatformJar("30")
        val apiJar = pluginApiJar()

        // aapt2 compile（资源 → *.flat）
        val compiled = File(work, "compiled").apply { mkdirs() }
        val resDir = File(work, "res")
        if (resDir.exists()) exec {
            commandLine(aapt2, "compile", "--dir", resDir.absolutePath, "-o", compiled.absolutePath)
        }

        // aapt2 link → unsigned-res.apk（★ 包含 resources.arsc + 二进制 AndroidManifest）
        val unsigned = File(work, "unsigned-res.apk")
        val flats = compiled.listFiles()?.map { it.absolutePath }?.toTypedArray() ?: emptyArray()
        exec {
            commandLine(
                aapt2, "link",
                "-o", unsigned.absolutePath,
                "-I", androidJar,
                "--manifest", File(work, "AndroidManifest.xml").absolutePath,
                "--min-sdk-version", "30",
                "--target-sdk-version", "30",
                "--auto-add-overlay",
                *flats
            )
        }

        // d8 → classes.dex（★ 指定 --min-api 并提供 android.jar + plugin-api classes.jar）
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

        // 追加 classes.dex 到 APK
        ant.withGroovyBuilder {
            "zip"("update" to true, "destfile" to unsigned.absolutePath) {
                "fileset"("file" to File(work, "classes.dex").absolutePath)
            }
        }

        // 输出 plugin.apk
        val out = layout.buildDirectory.dir("outputs/pluginApk").get().asFile.apply { mkdirs() }
        copy { from(unsigned); into(out); rename { "${project.name}-plugin.apk" } }
        println("plugin.apk 生成：${File(out, "${project.name}-plugin.apk").absolutePath}")
    }
}

/** 便捷任务：把生成的 plugin.apk 拷到宿主 assets（重命名为 render-advanced.apk） */
tasks.register("copyPluginToHostAssets") {
    dependsOn("makePluginApk")
    doLast {
        val src = layout.buildDirectory.file("outputs/pluginApk/${project.name}-plugin.apk").get().asFile
        val dstDir = project(":app").layout.projectDirectory.dir("src/main/assets").asFile
        dstDir.mkdirs()
        copy {
            from(src); into(dstDir); rename { "render-advanced.apk" } // ★ module 名即 render-advanced
        }
        println("已拷贝到宿主 assets：${File(dstDir, "render-advanced.apk").absolutePath}")
    }
}

// 可安装 APK（便于开发者单独安装/调试）
tasks.register("makePluginApkInstallable") {
    dependsOn("assembleRelease")
    doLast {
        val work = layout.buildDirectory.dir("tmp/plugin/${project.name}").get().asFile
        work.deleteRecursively(); work.mkdirs()

        val aar = layout.buildDirectory.file("outputs/aar/${project.name}-release.aar").get().asFile
        copy { from(zipTree(aar)); into(work) }

        val aapt2 = sdkTool("aapt2")
        val d8 = sdkTool("d8")
        val zipalign = sdkTool("zipalign")
        val apksigner = sdkTool("apksigner")
        val androidJar = sdkPlatformJar("30") // 或换成你项目的 targetSdk
        // 1) aapt2 compile res -> *.flat
        val compiled = File(work, "compiled").apply { mkdirs() }
        if (File(work, "res").exists()) {
            exec { commandLine(aapt2, "compile", "--dir", File(work, "res").absolutePath, "-o", compiled.absolutePath) }
        }

        // 2) aapt2 link -> unsigned-res.apk（包含 resources.arsc + AndroidManifest.xml）
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

        // 3) d8 -> classes.dex
        exec { commandLine(d8, "--release", "--output", work.absolutePath, File(work, "classes.jar").absolutePath) }

        // 4) 将 classes.dex 追加到 APK 中
        ant.withGroovyBuilder { "zip"("update" to true, "destfile" to unsignedResApk.absolutePath) { "fileset"("file" to File(work, "classes.dex").absolutePath) } }

        // 5) zipalign
        val outDir = layout.buildDirectory.dir("outputs/pluginApk").get().asFile.apply { mkdirs() }
        val aligned = File(outDir, "${project.name}-plugin-installable-aligned.apk")
        exec { commandLine(zipalign, "-f", "4", unsignedResApk.absolutePath, aligned.absolutePath) }

        // 6) apksigner（调试签名；正式请换成你项目的签名）
        val debugKs = File(System.getProperty("user.home"), ".android/debug.keystore")
        exec { commandLine(apksigner, "sign", "--ks", debugKs.absolutePath, "--ks-key-alias", "androiddebugkey", "--ks-pass", "pass:android", aligned.absolutePath) }

        println("可安装插件 APK：${aligned.absolutePath}")
    }
}