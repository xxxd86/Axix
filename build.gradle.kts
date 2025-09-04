// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.jetbrains.kotlin.jvm) apply false

    id("com.example.shell.analyzer") version "0.1.3"
}
//shellAnalyzer {
//    autoOnAssemble = true         // 是否在 assemble 后自动触发
//    chain = "both"                 // "apk" | "source" | "both"
//    appPath = ":app"               // 你的 app 模块 path
//    outputDir = "build/analysis"   // 结果输出目录（相对工程根）
//    pkgMapPath = "build/analysis/module_package_map.json"
//    packBp = true                  // 产出 .bp（zip 封装）
//
//    // 自动推断“模块→包前缀”时的策略（可保持默认）
//    inferMaxSegments = 3           // 前缀最多取几段：com.foo.bar
//    inferMinCountPerPrefix = 5     // 前缀至少命中多少类/源码
//    inferScanClassLimit = 10_000   // 每模块最多统计多少个 .class
//}