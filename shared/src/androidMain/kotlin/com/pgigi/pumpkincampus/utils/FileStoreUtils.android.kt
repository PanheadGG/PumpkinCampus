package com.pgigi.pumpkincampus.utils

import android.content.Context

/**
 * Android 全局上下文持有者（参照 Pumpkin-Toolkit 的 KVaultContextHolder）。
 *
 * 在 `MainActivity.onCreate` 中初始化：
 * ```
 * AppContextHolder.context = applicationContext
 * ```
 */
object AppContextHolder {
    lateinit var context: Context
}

/**
 * Android 平台应用数据目录
 * 对应 context.filesDir
 */
actual fun appDataDir(): String {
    return AppContextHolder.context.filesDir.absolutePath
}

/**
 * Android 平台应用缓存目录
 * 对应 context.cacheDir
 */
actual fun appCacheDir(): String {
    return AppContextHolder.context.cacheDir.absolutePath
}
