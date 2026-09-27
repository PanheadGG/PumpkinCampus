package com.pgigi.pumpkincampus.utils

import com.liftric.kvault.KVault

/**
 * Android 平台：插件敏感配置用 `EncryptedSharedPreferences` 加密存储
 * （密钥由 Android Keystore 生成并保管，存档文件本身不可直接读取）。
 *
 * 依赖 [AppContextHolder]（`MainActivity.onCreate` 里初始化 applicationContext）。
 */
actual fun createPluginVault(): KVault =
    KVault(context = AppContextHolder.context, fileName = "plugin_secrets")
