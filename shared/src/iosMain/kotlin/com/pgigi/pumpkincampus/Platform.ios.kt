package com.pgigi.pumpkincampus

import platform.Foundation.NSBundle
import platform.UIKit.UIDevice

class IOSPlatform: Platform {
    override val name: String = UIDevice.currentDevice.systemName() + " " + UIDevice.currentDevice.systemVersion
}

actual fun getPlatform(): Platform = IOSPlatform()

/**
 * 宿主 versionCode：Info.plist 的 `CFBundleVersion`。
 * 供插件 manifest 的 `minHostVersionCode` 兼容性检查使用。
 */
actual fun appVersionCode(): Int = runCatching {
    val raw = NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleVersion") as? String
    raw?.toIntOrNull() ?: 1
}.getOrDefault(1)

/**
 * 宿主 versionName：Info.plist 的 `CFBundleShortVersionString`。
 * 「关于」页展示 `版本名 (versionCode)` 用。
 */
actual fun appVersionName(): String = runCatching {
    val raw = NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String
    raw?.takeIf { it.isNotBlank() } ?: "1.0"
}.getOrDefault("1.0")