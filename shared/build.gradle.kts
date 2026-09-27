import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.serialization)
}

kotlin {
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }
    
    android {
       namespace = "com.pgigi.pumpkincampus.shared"
       compileSdk = libs.versions.android.compileSdk.get().toInt()
       minSdk = libs.versions.android.minSdk.get().toInt()
    
       compilerOptions {
           jvmTarget = JvmTarget.JVM_11
       }
       androidResources {
           enable = true
       }
       withHostTest {
           isIncludeAndroidResources = true
       }
       withDeviceTestBuilder {
           sourceSetTreeName = "test"
       }.configure {
           instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
       }
    }
    
    sourceSets {
        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.compose.uiTooling)
            // 系统文件选择器（ActivityResultContracts.OpenDocument）
            implementation(libs.androidx.activity.compose)
            // Ktor HTTP 引擎（在线分享课表）
            implementation(libs.ktor.client.android)
        }
        iosMain.dependencies {
            // Ktor HTTP 引擎（在线分享课表，Darwin/NSURLSession）
            implementation(libs.ktor.client.darwin)
        }
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)

            implementation(libs.salt.ui)
            implementation(libs.serialization.json)
            implementation(libs.datetime)
            implementation(libs.okio)
            implementation(libs.salt.ui.navigation)
            implementation(libs.navigation3.runtime)
            implementation(libs.navigation3.ui)
            // Ktor HTTP 客户端（在线分享课表；引擎由各平台提供）
            implementation(libs.ktor.client.core)

            // 插件密码类配置的加密存储（Android Keystore / iOS Keychain）
            implementation(libs.kvault)

            implementation(libs.quickjs.kt)
            implementation(libs.ksoup)
            implementation (libs.kzip)
            // 顶部 Toast（Compose Multiplatform：android / ios / desktop）
            implementation (libs.dhyantoast)


        }
        // 宿主单元测试（插件清单 / 课程解析的契约测试）
        getByName("androidHostTest").dependencies {
            implementation(kotlin("test"))
        }
    }
}

dependencies {
    androidRuntimeClasspath(libs.compose.uiTooling)
}