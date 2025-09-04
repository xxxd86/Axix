package com.redwolf.proto.plugin

open class ShellAnalyzerExtension {
    /** assemble 后是否自动触发分析（finalizedBy） */
    var autoOnAssemble: Boolean = false
    /** 链路：apk | source | both */
    var chain: String = "apk"
    /** app 模块 path （用于 APK-only 找 APK）*/
    var appPath: String = ":app"
    /** 输出目录（相对根工程）*/
    var outputDir: String = "build/analysis"
    /** 包前缀映射文件（相对根工程）*/
    var pkgMapPath: String = "build/analysis/module_package_map.json"
    /** 是否额外打 .bp 包（zip 容器）*/
    var packBp: Boolean = true
    // ★ 自动推断的参数（可不改）
    var inferMaxSegments: Int = 3           // 前缀最多取几段，如 com.foo.bar
    var inferMinCountPerPrefix: Int = 5     // 一个前缀至少要出现多少次（class 或 package 声明）
    var inferScanClassLimit: Int = 10000    // 每模块最多扫描多少个 .class
}