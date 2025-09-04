package com.redwolf.proto.plugin

import com.google.gson.GsonBuilder
import com.redwolf.proto.core.AsmScanner
import com.redwolf.proto.core.DexScanner
import com.redwolf.proto.core.GraphBuilder
import com.redwolf.proto.core.ModuleMapper
import com.redwolf.proto.core.PbWriter
import org.gradle.api.*
import org.gradle.kotlin.dsl.*
import java.io.File
import java.nio.file.Files
import java.util.regex.Pattern
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ShellAnalyzerPlugin: Plugin<Project> {
    override fun apply(root: Project) {
        val ext = root.extensions.create("shellAnalyzer", ShellAnalyzerExtension::class.java)

        // 1) 导出包前缀映射（从 module.properties 读取）
        val exportPkgMap = root.tasks.register("exportModulePkgMap") {

            group = "analysis"; description = "导出 module->pkg 前缀映射"
            doLast {
                val ext = root.extensions.findByName("shellAnalyzer") as ShellAnalyzerExtension
                val inferMaxSegments = ext.inferMaxSegments
                val inferMinCount = ext.inferMinCountPerPrefix
                val scanLimit = ext.inferScanClassLimit

                fun isThirdPartyPrefix(s: String): Boolean =
                    s.startsWith("kotlin.") || s.startsWith("android.") || s.startsWith("androidx.") ||
                            s.startsWith("java.") || s.startsWith("javax.") || s.startsWith("sun.") ||
                            s.startsWith("com.google.") || s.startsWith("org.jetbrains.")

                fun cutToSegments(pkg: String): String {
                    val parts = pkg.split('.')
                    return if (parts.size >= inferMaxSegments) parts.take(inferMaxSegments).joinToString(".") else pkg
                }

                val manifestPkgRegex = Pattern.compile("package\\s*=\\s*\"([^\"]+)\"")
                val srcPkgRegex = Pattern.compile("^\\s*package\\s+([A-Za-z0-9_.]+)", Pattern.MULTILINE)

                val result = mutableListOf<Map<String, Any>>()

                root.subprojects.forEach { p ->
                    val prefixes = linkedSetOf<String>()

                    // L1: 读 AGP namespace（反射，避免直接依赖 AGP 类型）
                    p.extensions.findByName("android")?.let { androidExt ->
                        try {
                            val ns = androidExt.javaClass.methods.firstOrNull { it.name == "getNamespace" && it.parameterCount == 0 }
                                ?.invoke(androidExt) as? String
                            if (!ns.isNullOrBlank()) prefixes += ns
                        } catch (_: Exception) { /* ignore */ }
                    }

                    // L2: 读 AndroidManifest 的 package
                    listOf(
                        "src/main/AndroidManifest.xml",
                        "src/debug/AndroidManifest.xml",
                        "src/release/AndroidManifest.xml"
                    ).forEach { rel ->
                        val f = p.file(rel)
                        if (f.exists()) {
                            val m = manifestPkgRegex.matcher(f.readText())
                            if (m.find()) prefixes += m.group(1)
                        }
                    }

                    // L3: 扫描 .class（优先）
                    val classDirs = listOf(
                        "build/intermediates/javac/debug/classes",
                        "build/intermediates/javac/release/classes",
                        "build/tmp/kotlin-classes/debug",
                        "build/tmp/kotlin-classes/release",
                        "build/classes/java/main",
                        "build/classes/kotlin/main"
                    ).map { p.file(it) }.filter { it.exists() }

                    val counts = linkedMapOf<String, Int>()
                    var seen = 0
                    fun bumpBin(name: String) {
                        val pkgPath = name.substringBeforeLast('/', "")
                        if (pkgPath.isBlank()) return
                        val pkg = pkgPath.replace('/', '.')
                        if (isThirdPartyPrefix(pkg)) return
                        val seg = cutToSegments(pkg)
                        counts[seg] = (counts[seg] ?: 0) + 1
                    }
                    classDirs.forEach { dir ->
                        dir.walkTopDown().filter { it.isFile && it.extension == "class" }.forEach { f ->
                            val rel = f.relativeTo(dir).invariantSeparatorsPath.removeSuffix(".class")
                            bumpBin(rel)
                            seen++
                            if (seen >= scanLimit) return@forEach
                        }
                    }

                    // L4: 找不到 classes 时，回退扫描源码 package
                    if (counts.isEmpty()) {
                        val srcDirs = listOf("src/main/java", "src/main/kotlin").map { p.file(it) }.filter { it.exists() }
                        var srcSeen = 0
                        srcDirs.forEach { sd ->
                            sd.walkTopDown().filter { it.isFile && (it.extension == "kt" || it.extension == "java") }.forEach { f ->
                                val m = srcPkgRegex.matcher(f.readText())
                                if (m.find()) {
                                    val pkg = m.group(1)
                                    if (!isThirdPartyPrefix(pkg)) {
                                        val seg = cutToSegments(pkg)
                                        counts[seg] = (counts[seg] ?: 0) + 1
                                    }
                                }
                                srcSeen++
                                if (srcSeen >= 2000) return@forEach
                            }
                        }
                    }

                    // 选择前缀：出现次数达到阈值的前几名；若没有但 L1/L2 有值，则用 L1/L2
                    val selected = counts.entries
                        .sortedByDescending { it.value }
                        .filter { it.value >= inferMinCount }
                        .map { it.key }
                        .take(5)
                        .toMutableList()

                    if (selected.isEmpty() && prefixes.isNotEmpty()) {
                        selected += prefixes
                    }

                    // 去冗余：移除被更长前缀覆盖的项（保留更具体的）
                    val finalSet = linkedSetOf<String>()
                    selected.sortedBy { it.length }.forEach { s ->
                        if (finalSet.none { s.startsWith("$it.") }) finalSet += s
                    }

                    if (finalSet.isNotEmpty()) {
                        result += mapOf(
                            "module" to p.path,
                            "pkg_prefixes" to finalSet.toList()
                        )
                    }
                }

                val out = root.layout.buildDirectory.file("analysis/module_package_map.json").get().asFile
                out.parentFile.mkdirs()
                out.writeText(GsonBuilder().setPrettyPrinting().create().toJson(result))
                root.logger.lifecycle("[shell-analyzer] inferred ${result.size} modules -> $out")
            }


        }


        fun packBp(outputDir: File, variant: String, chain: String) {
            val ts = System.currentTimeMillis()
            val bp = File(outputDir, "analyzer-$chain-$variant-$ts.bp")
            ZipOutputStream(bp.outputStream()).use { zos ->
                fun put(name: String){ val f = File(outputDir, name); if (f.exists()) { zos.putNextEntry(ZipEntry(name)); f.inputStream().use { it.copyTo(zos) }; zos.closeEntry() } }
                // manifest
                val manifest = File.createTempFile("bp-manifest", ".json").apply {
                    writeText("""{"timestamp":$ts,"chain":"$chain","variant":"$variant"}""")
                }
                zos.putNextEntry(ZipEntry("manifest.json")); manifest.inputStream().use { it.copyTo(zos) }; zos.closeEntry(); manifest.delete()
                // artifacts
                put("ModuleGraph.pb"); put("UsedMethods.pb"); put("ClassRefs.pb"); put("SelectionResult.pb")
            }
        }

        // APK-only 任务
        fun Project.latestApk(variant: String): File {
            val dir = layout.buildDirectory.dir("outputs/apk/$variant").get().asFile
            return dir.walkTopDown().filter { it.isFile && it.extension == "apk" }.maxByOrNull { it.lastModified() }
                ?: throw GradleException("APK not found under $dir")
        }

        fun registerApkTask(app: Project, variant: String) = app.tasks.register("analyzerApk${variant.replaceFirstChar { it.uppercase() }}") {
            group = "analysis"; description = "Dexlib2 扫描 APK 产出 PB ($variant)"
            dependsOn(app.tasks.named("assemble${variant.replaceFirstChar { it.uppercase() }}"))
            dependsOn(exportPkgMap)
            doLast {
                val cfg = ext
                val outDir = root.layout.projectDirectory.dir(cfg.outputDir).asFile.also { it.mkdirs() }
                val pkgMap = root.layout.projectDirectory.file(cfg.pkgMapPath).asFile
                val mapper = ModuleMapper.fromJson(pkgMap.toPath())
                val G = GraphBuilder()
                val dex = DexScanner(mapper, G)
                val apk = app.latestApk(variant).toPath()
                dex.scanApk(apk)
                PbWriter.writeModuleGraph(File(outDir, "ModuleGraph.pb"), G, "APK_ONLY")
                PbWriter.writeUsed(File(outDir, "UsedMethods.pb"), G, "APK_ONLY")
                root.logger.lifecycle("[shell-analyzer] APK-only .pb -> $outDir")
                if (cfg.packBp) packBp(outDir, variant, "apk")
            }
        }

        // Source-aware 任务
        val analyzerSource = root.tasks.register("analyzerSource") {
            group = "analysis"; description = "ASM 扫描 .class/.jar 产出 PB"
            dependsOn(exportPkgMap);
            doLast {
                val cfg = ext
                val outDir = root.layout.projectDirectory.dir(cfg.outputDir).asFile.also { it.mkdirs() }
                val pkgMap = root.layout.projectDirectory.file(cfg.pkgMapPath).asFile
                val mapper = ModuleMapper.fromJson(pkgMap.toPath())
                val G = GraphBuilder()
                val asm = AsmScanner(mapper, G)
                val classes = mutableListOf<File>()
                root.subprojects.forEach { p ->
                    listOf(
                        "build/intermediates/javac/debug/classes",
                        "build/intermediates/javac/release/classes",
                        "build/tmp/kotlin-classes/debug",
                        "build/tmp/kotlin-classes/release",
                        "build/classes/java/main",
                        "build/classes/kotlin/main"
                    ).map { p.layout.projectDirectory.dir(it).asFile }.filter { it.exists() }.forEach { classes += it }
                }
                require(classes.isNotEmpty()) { "未发现 classes 目录，请确认 assemble 成功或补充路径" }
                asm.scanPaths(classes.map { it.toPath() })
                PbWriter.writeModuleGraph(File(outDir, "ModuleGraph.pb"), G, "SOURCE_AWARE")
                PbWriter.writeUsed(File(outDir, "UsedMethods.pb"), G, "SOURCE_AWARE")
                root.logger.lifecycle("[shell-analyzer] Source-aware .pb -> $outDir")
                if (ext.packBp) packBp(outDir, "all", "source")
            }
        }
        // 3) 运行前“尽可能”依赖各子模块的 assemble/build（存在才挂；没有就跳过）
        root.afterEvaluate {
            root.subprojects.forEach { sp ->
                // 依赖 assemble（如果存在），否则依赖 build（如果存在）
                sp.tasks.findByName("assemble")?.let {
                    analyzerSource.configure { dependsOn(sp.tasks.named("assemble")) }
                } ?: sp.tasks.findByName("build")?.let {
                    analyzerSource.configure { dependsOn(sp.tasks.named("build")) }
                }
            }
        }
        // 自动挂接 assemble（可选）
        root.afterEvaluate {
            val ext = root.extensions.findByName("shellAnalyzer") as ShellAnalyzerExtension
            val app = root.subprojects.firstOrNull { it.path == ext.appPath }

            if (ext.autoOnAssemble && app != null) {
                when (ext.chain) {
                    "apk" -> {
                        app.tasks.named("assembleDebug").configure {finalizedBy(app.tasks.named("analyzerApkDebug")) }
                        app.tasks.named("assembleRelease").configure {finalizedBy(app.tasks.named("analyzerApkRelease")) }
                    }
                    "source" -> {
                        // root 一般没有 assemble；若有就挂 root 的，否则挂 app 的 assemble*
                        val rootAssemble = root.tasks.findByName("assemble")
                        if (rootAssemble != null) {
                            root.tasks.named("assemble").configure {finalizedBy(analyzerSource) }
                        } else {
                            // fallback：把 app 的 assemble* 都挂上 analyzerSource
                            app.tasks.matching { it.name.startsWith("assemble") }.configureEach { finalizedBy(analyzerSource)
                            }
                        }
                    }
                    "both" -> {
                        // APK 链
                        app.tasks.named("assembleDebug").configure { finalizedBy(app.tasks.named("analyzerApkDebug")) }
                        app.tasks.named("assembleRelease").configure { finalizedBy(app.tasks.named("analyzerApkRelease")) }
                        // Source 链（同上 fallback）
//                        val rootAssemble = root.tasks.findByName("assemble")
//                        if (rootAssemble != null) {
//                            root.tasks.named("assemble").configure { finalizedBy(analyzerSource) }
//                        } else {
//                            app.tasks.matching { it.name.startsWith("assemble") }.configureEach {finalizedBy(analyzerSource)
//                            }
//                        }
                        val rootAssemble = root.tasks.findByName("assemble")
                        if (rootAssemble != null) {
                            root.tasks.named("assemble").configure { finalizedBy(analyzerSource) }
                        } else {
                            // root 没有 assemble，则把 :app 的 assemble* 都挂上 analyzerSource
                            app?.tasks?.matching { it.name.startsWith("assemble") }?.configureEach { finalizedBy(analyzerSource) }
                        }
                    }
                }
            }
        }
    }
}