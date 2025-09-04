plugins {
    // 必须：让 gradlePlugin{ } 扩展可用
    id("java-gradle-plugin")
    // Kotlin 插件继续沿用你的版本清单写法
    alias(libs.plugins.jetbrains.kotlin.jvm)
    // 生成 Protobuf Java 类（Kotlin 可直接调用）
    id("com.google.protobuf") version "0.9.4"
    `kotlin-dsl`
    `maven-publish`
}
dependencies {
    implementation(kotlin("stdlib"))

    // 扫描内核依赖（ASM + Dexlib2 + Gson）
    implementation("org.ow2.asm:asm:9.7")
    implementation("org.ow2.asm:asm-commons:9.7")
    implementation("org.smali:dexlib2:2.5.2")
    implementation("com.google.code.gson:gson:2.11.0")

    // Protobuf 运行库（生成的 Java 类用这个）
    api("com.google.protobuf:protobuf-java:3.25.3")
}

java {
    // 你的原始配置：Java 11
    withSourcesJar()
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}
// === 关键：把生成的 Java 源喂给 Kotlin 编译器 ===
val generatedJava = layout.buildDirectory.dir("generated/source/proto/main/java")

sourceSets {
    named("main") {
        // 让 Java 源集/IDE 识别生成目录
        java.srcDir(generatedJava)
    }
}
tasks.named<Jar>("sourcesJar") {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    // 可选：显式声明来源
    // from(sourceSets.main.allSource)
}
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    // 1) 先生成 proto 源码
    dependsOn("generateProto")
    // 2) 让 Kotlin 直接解析这些 Java 源
    kotlinOptions.freeCompilerArgs += "-Xjava-source-roots=${generatedJava.get().asFile.absolutePath}"
}
kotlin {
    // 你的原始配置：Kotlin 目标 11
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11
    }
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:3.25.3"
    }
    // 这段可以省略；若保留，请用 create("java")
    generateProtoTasks { all().configureEach { builtins { java {} } } }
}
group = "com.redwolf.plugin_api"
version = "0.1.3"
//// 关键：声明 Gradle 插件（务必用“函数式 DSL”写法，兼容更多 Gradle 版本）
gradlePlugin {
    plugins {
        create("shellAnalyzer").apply {
            id = "com.example.shell.analyzer" // 插件 ID（业务仓库 apply 用这个）
            implementationClass = "com.redwolf.proto.plugin.ShellAnalyzerPlugin"
            // 有些 Gradle 版本上 displayName/description 是只读，会触发 “Val cannot be reassigned”，所以不填更稳妥
            // displayName("Shell Analyzer")
            // description("APK/class cross-module analyzer producing PB & BP")
        }
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])               // 打包编译产物
            groupId = group.toString()
            artifactId = "proto-analyzer"
            version = version
        }
    }
    repositories {
        mavenLocal()                               // 发布到本地仓库
    }
}

