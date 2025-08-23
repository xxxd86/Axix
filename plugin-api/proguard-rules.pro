# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile
# 保持对外 API 类与成员名（便于调试/文档）
#-keep class com.redwolf.plugin_api.core.PluginActivity { *; }
#-keep class com.redwolf.plugin_api.ProxyKeys { *; }
#-keep class com.redwolf.plugin_api.runtime.ModuleRegistry { *; }
#-keep class com.redwolf.plugin_api.core.PluginProxyActivity { *; }
#-keepclassmembers class com.redwolf.plugin_api.core.PluginProxyActivity {
#   public *;
#}
#-keep class com.redwolf.plugin_api.runtime.ModuleDescriptor
## 去掉调试信息可再省一点（会影响行号堆栈）
#-dontnote
#-dontwarn
#-keeppackagenames com.redwolf.plugin_api
#-renamesourcefileattribute Source
#-keepattributes *Annotation*  # 如果没有反射需求也可进一步删减 attributes