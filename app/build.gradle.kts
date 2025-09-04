plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.redwolf.axix"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.redwolf.axix"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
    sourceSets {
        getByName("main") {
            assets {
                srcDirs("src\\main\\assets", "src\\main\\assets")
            }
        }
    }
}

////val copyTinyToAssets = tasks.register("copyTinyToAssets") {
////    dependsOn(":plugin-render-advanced:makeTinyDexBundleRenderAdvanced")
////    doLast {
////        val assets = layout.buildDirectory.dir("generated/fast_assets").get().asFile.apply { mkdirs() }
////        copy {
////            from(rootProject.project(":plugin-render-advanced").layout.buildDirectory.file("outputs/pluginApk/plugin-render-advanced-tiny.dex.jar"))
////            into(assets); rename { "render-advanced.dex.jar" }
////        }
////        println("assets prepared at: ${'$'}assets")
////    }
////}
//android.sourceSets.getByName("main").assets.srcDirs(files("${'$'}buildDir/generated/fast_assets"))
//tasks.named("preBuild").configure { dependsOn(copyTinyToAssets) }
dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.appcompat)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
    implementation(project(":plugin-api"))
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation(project(":mvi-dynamic-plugin"))
    //implementation(files("libs/plugin-api-classes.jar"))
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.4")
//    val lifecycle_version = "2.9.2"
//    val arch_version = "2.2.0"
//
//    // ViewModel
//    implementation("androidx.lifecycle:lifecycle-viewmodel:$lifecycle_version")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose")
    implementation("androidx.compose.material:material-icons-extended")
//    implementation(project(":proto-analyzer"))
}