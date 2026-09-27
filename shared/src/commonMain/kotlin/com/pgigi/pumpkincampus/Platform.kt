package com.pgigi.pumpkincampus

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform

/**
 * 宿主应用的 versionCode（插件 manifest 的 `minHostVersionCode` 与它比较）。
 *
 * - Android：`PackageInfo.versionCode` / `longVersionCode`
 * - iOS：Info.plist 的 `CFBundleVersion`
 */
expect fun appVersionCode(): Int

/**
 * 宿主应用的**版本名**（「关于」页展示的 `版本名 (versionCode)`）。
 *
 * - Android：`PackageInfo.versionName`（构建脚本里的 `versionName`）
 * - iOS：Info.plist 的 `CFBundleShortVersionString`
 *
 * 读取失败（未安装信息 / 字段缺失）返回 `"1.0"`，不抛异常。
 */
expect fun appVersionName(): String