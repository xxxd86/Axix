package com.redwolf.proto.plugin

open class ProtoAnalyzerExtension {
    var outputDir: String = "build/analysis"
    var pkgMapPath: String = "build/analysis/module_package_map.json"
    var includeJars: Boolean = false              // 是否连依赖 jar 一起扫
    var includeSelfRefs: Boolean = true           // 是否保留 “类→同模块类” 的引用
    var includeThirdParty: Boolean = false        // 是否保留对 JDK/Android/Kotlin 等第三方的引用
}