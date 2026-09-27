package com.pgigi.pumpkincampus.utils

import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask

/**
 * iOS 平台应用数据目录
 * 对应 NSDocumentDirectory（~/Documents）
 */
actual fun appDataDir(): String {
    return NSSearchPathForDirectoriesInDomains(
        NSDocumentDirectory, NSUserDomainMask, expandTilde = true
    ).firstOrNull() as? String ?: ""
}

/**
 * iOS 平台应用缓存目录
 * 对应 NSCachesDirectory（~/Library/Caches）
 */
actual fun appCacheDir(): String {
    return NSSearchPathForDirectoriesInDomains(
        NSCachesDirectory, NSUserDomainMask, expandTilde = true
    ).firstOrNull() as? String ?: ""
}
