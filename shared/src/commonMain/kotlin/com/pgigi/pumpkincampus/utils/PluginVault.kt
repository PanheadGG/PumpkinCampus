package com.pgigi.pumpkincampus.utils

import com.liftric.kvault.KVault

/**
 * 创建**插件敏感配置**专用的 KVault 实例（平台特定实现）：
 * - Android：`EncryptedSharedPreferences`，密钥由 Android Keystore 保管
 * - iOS：Keychain
 *
 * 只在 [com.pgigi.pumpkincampus.plugin.PluginSecretStore] 内部使用：
 * 插件配置里的密码、token 等敏感值存这里，不再明文写进 `custom-schedule.json`。
 */
expect fun createPluginVault(): KVault
