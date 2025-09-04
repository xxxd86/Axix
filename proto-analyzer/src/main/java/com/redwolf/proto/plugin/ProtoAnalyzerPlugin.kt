package com.redwolf.proto.plugin

import com.redwolf.proto.core.ModuleMapper
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.objectweb.asm.ClassReader
import java.io.File
import java.util.zip.ZipFile

class ProtoAnalyzerPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val root = project.rootProject
        val ext = root.extensions.create("protoAnalyzer", ProtoAnalyzerExtension::class.java)

        // 类级引用扫描任务
        val analyzerClassRefs = root.tasks.register("analyzerClassRefs") {
            group = "analysis"
            description = "ASM 扫描 .class/.jar，产出每个类的全引用（ClassRefs.pb）"

            doLast {
                val outDir = root.layout.projectDirectory.dir(ext.outputDir).asFile.also { it.mkdirs() }
                val pkgMap = root.layout.projectDirectory.file(ext.pkgMapPath).asFile
                require(pkgMap.exists()) { "找不到 pkgMap: ${pkgMap.absolutePath}，请先生成 module_package_map.json" }

                val mapper = ModuleMapper.fromJson(pkgMap.toPath())
                val indexer = ClassRefIndexer(mapper, ext.includeSelfRefs, ext.includeThirdParty)
                val scanner = ClassRefScanner()

                // 收集可扫描的 class 目录
                val classDirs = mutableListOf<File>()
                root.subprojects.forEach { p ->
                    listOf(
                        "build/intermediates/javac/debug/classes",
                        "build/intermediates/javac/release/classes",
                        "build/tmp/kotlin-classes/debug",
                        "build/tmp/kotlin-classes/release",
                        "build/classes/java/main",
                        "build/classes/kotlin/main"
                    ).map { p.layout.projectDirectory.dir(it).asFile }
                        .filter { it.exists() }
                        .forEach { classDirs += it }
                }

                // 可选：一起扫依赖 jar（通常用于 library）
                val jars = mutableListOf<File>()
                if (ext.includeJars) {
                    root.subprojects.forEach { p ->
                        val dir = p.layout.projectDirectory.dir("build/intermediates/compile_and_runtime_not_namespaced_r_class_jar/debug/R.jar").asFile
                        // 按需补充你工程里 jar 产物位置；或解析依赖配置拿 runtime jars
                        if (dir.exists()) jars += dir
                    }
                }

                // 扫描 class 目录
                var totalClasses = 0
                classDirs.forEach { dir ->
                    dir.walkTopDown()
                        .filter { it.isFile && it.extension == "class" }
                        .forEach { f ->
                            val bytes = f.readBytes()
                            val one = scanner.scanClass(bytes)
                            indexer.add(one)
                            totalClasses++
                        }
                }

                // 扫描 jar（可选）
                jars.forEach { jar ->
                    ZipFile(jar).use { zf ->
                        zf.entries().asSequence()
                            .filter { !it.isDirectory && it.name.endsWith(".class") }
                            .forEach { e ->
                                val bytes = zf.getInputStream(e).readAllBytes()
                                val one = scanner.scanClass(bytes)
                                indexer.add(one)
                                totalClasses++
                            }
                    }
                }

                val idx = indexer.build()
                val out = File(outDir, "ClassRefs.pb")
                PbWriter.writeClassRefs(out, idx)
                logger.lifecycle("[proto-analyzer] ClassRefs.pb -> ${out.absolutePath} (classes=$totalClasses)")
            }
        }

        // 尽可能让 analyzerClassRefs 依赖子模块 assemble/build（存在才挂）
        root.afterEvaluate {
            root.subprojects.forEach { sp ->
                sp.tasks.matching { it.name == "assemble" || it.name == "build" }
                    .configureEach { t -> analyzerClassRefs.configure { dependsOn(t) } }
            }
        }
    }
}
