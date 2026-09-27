package com.pgigi.pumpkincampus.utils

import com.liftric.kvault.KVault

/**
 * iOS 平台：插件敏感配置用 Keychain 加密存储（serviceName 为应用标识，
 * 卸载 App 时由系统清理）。
 */
actual fun createPluginVault(): KVault =
    KVault(serviceName = "com.pgigi.pumpkincampus.plugins", accessGroup = null)
