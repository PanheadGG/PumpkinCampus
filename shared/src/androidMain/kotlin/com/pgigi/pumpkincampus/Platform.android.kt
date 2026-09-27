package com.pgigi.pumpkincampus

import android.os.Build
import com.pgigi.pumpkincampus.utils.AppContextHolder

class AndroidPlatform : Platform {
    override val name: String = "Android ${Build.VERSION.SDK_INT}"
}

actual fun getPlatform(): Platform = AndroidPlatform()

/**
 * 宿主 versionCode：`PackageInfo.versionCode`（API 28+ 取 `longVersionCode` 低 32 位）。
 * 供插件 manifest 的 `minHostVersionCode` 兼容性检查使用。
 */
actual fun appVersionCode(): Int = runCatching {
    val context = AppContextHolder.context
    val info = context.packageManager.getPackageInfo(context.packageName, 0)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        info.longVersionCode.toInt()
    } else {
        @Suppress("DEPRECATION")
        info.versionCode
    }
}.getOrDefault(1)

/**
 * 宿主 versionName：`PackageInfo.versionName`（构建脚本里的 `versionName`）。
 * 「关于」页展示 `版本名 (versionCode)` 用。
 */
actual fun appVersionName(): String = runCatching {
    val context = AppContextHolder.context
    val info = context.packageManager.getPackageInfo(context.packageName, 0)
    info.versionName?.takeIf { it.isNotBlank() } ?: "1.0"
}.getOrDefault("1.0")