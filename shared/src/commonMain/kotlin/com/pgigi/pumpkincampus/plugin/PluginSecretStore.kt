package com.pgigi.pumpkincampus.plugin

import com.liftric.kvault.KVault
import com.pgigi.pumpkincampus.models.CourseBook
import com.pgigi.pumpkincampus.models.CourseSchedule
import com.pgigi.pumpkincampus.utils.createPluginVault

/* ------------------------------------------------------------------ */
/* 加密后端                                                            */
/* ------------------------------------------------------------------ */

/**
 * 敏感配置的加密读写后端（真实实现见 [KVaultPluginSecretVault]）。
 *
 * 抽出接口是为了让宿主单元测试能注入内存实现：KVault 依赖 Android Keystore /
 * iOS Keychain，在 JVM 宿主测试里无法实例化。
 */
internal interface PluginSecretVault {
    fun read(key: String): String?
    fun write(key: String, value: String): Boolean
    fun delete(key: String): Boolean
}

/**
 * KVault 实现：
 * - Android：`EncryptedSharedPreferences`（密钥由 Android Keystore 保管）
 * - iOS：Keychain
 *
 * 实例化或读写失败（例如加密存储不可用）时**不抛异常**：写入返回 `false`，
 * 调用方会退化成把该值留在明文配置里，避免用户的配置丢失。
 */
private class KVaultPluginSecretVault : PluginSecretVault {

    private val vault: KVault? by lazy { runCatching { createPluginVault() }.getOrNull() }

    override fun read(key: String): String? =
        runCatching { vault?.string(forKey = key) }.getOrNull()

    override fun write(key: String, value: String): Boolean =
        runCatching { vault?.set(key = key, stringValue = value) }.getOrNull() ?: false

    override fun delete(key: String): Boolean =
        runCatching { vault?.deleteObject(forKey = key) }.getOrNull() ?: false
}

/* ------------------------------------------------------------------ */
/* 加密存储入口                                                        */
/* ------------------------------------------------------------------ */

/**
 * 插件**敏感配置值**（密码、token 等）的加密存储。
 *
 * 存储后端是 **KVault**（Android = EncryptedSharedPreferences + Keystore，iOS = Keychain）。
 * key 形如 `pluginConfig.<插件id>.<课表id>.<配置key>`，与插件 KV 一样按
 * 「插件 + 课表」隔离：不同课表可以为同一插件保存不同账号/密码。
 *
 * 约定：
 * - **敏感项只存在加密存储里**，`custom-schedule.json` 的
 *   [CourseSchedule.pluginConfig] 不含它们的值，只用
 *   [CourseSchedule.pluginSecretKeys] 记住「哪些 key 是加密的」；
 * - 非敏感项（地址、学期、开关…）仍然明文保存，便于排查与迁移；
 * - 历史版本留下的明文密码由 [migratePlaintextPluginSecrets] 在启动时搬进加密存储。
 */
object PluginSecretStore {

    /**
     * 加密后端。宿主测试会替换成内存实现；**生产代码不要修改它**。
     */
    internal var vault: PluginSecretVault = KVaultPluginSecretVault()

    /** 加密存储里的 key：`pluginConfig.<插件id>.<课表id>.<配置key>`。 */
    fun storageKey(pluginId: String, scheduleId: String, configKey: String): String =
        "pluginConfig.$pluginId.$scheduleId.$configKey"

    /** 读取一个敏感配置值；不存在或加密存储不可用时返回 null。 */
    fun read(pluginId: String, scheduleId: String, configKey: String): String? {
        if (pluginId.isEmpty() || scheduleId.isEmpty() || configKey.isEmpty()) return null
        return vault.read(storageKey(pluginId, scheduleId, configKey))
    }

    /** 写入一个敏感配置值；返回是否**成功加密保存**（false 时调用方应回退明文）。 */
    fun write(pluginId: String, scheduleId: String, configKey: String, value: String): Boolean {
        if (pluginId.isEmpty() || scheduleId.isEmpty() || configKey.isEmpty()) return false
        return vault.write(storageKey(pluginId, scheduleId, configKey), value)
    }

    /** 删除一个敏感配置值（用户清空该项、或删除课表时调用）。 */
    fun delete(pluginId: String, scheduleId: String, configKey: String): Boolean {
        if (pluginId.isEmpty() || scheduleId.isEmpty() || configKey.isEmpty()) return false
        return vault.delete(storageKey(pluginId, scheduleId, configKey))
    }

    /** 批量读取（只读存在的项）。 */
    fun readAll(
        pluginId: String,
        scheduleId: String,
        configKeys: Collection<String>
    ): Map<String, String> = buildMap {
        for (key in configKeys) {
            read(pluginId, scheduleId, key)?.let { put(key, it) }
        }
    }

    /** 批量删除（删除课表时清理该课表的全部密文）。 */
    fun deleteAll(pluginId: String, scheduleId: String, configKeys: Collection<String>): Int =
        configKeys.count { delete(pluginId, scheduleId, it) }
}

/* ------------------------------------------------------------------ */
/* 规则：哪些配置项属于敏感值                                            */
/* ------------------------------------------------------------------ */

/**
 * 该配置项是否按**敏感值**处理：
 * - manifest 里声明为 `password` 类型；或
 * - key 命中敏感词（`password` / `token` / `secret` / `pwd` …，与日志脱敏同一套规则）。
 *
 * 命中敏感词的判断同时覆盖「插件已卸载、拿不到 manifest」的情况，
 * 保证历史明文密码在启动迁移时也能被识别。
 */
internal fun isPluginSecretConfig(
    key: String,
    type: PluginConfigType? = null
): Boolean = type == PluginConfigType.PASSWORD ||
    Regex(SENSITIVE_KEY_PATTERN, RegexOption.IGNORE_CASE).containsMatchIn(key)

/**
 * 某课表需要加密保存的配置 key 集合：
 * manifest 里的 `password` 项 ∪ 存档里记录过的加密项 ∪ 配置值里命中敏感词的项。
 */
internal fun pluginSecretKeys(
    configs: List<PluginConfigItem>,
    stored: Map<String, String> = emptyMap(),
    recorded: Set<String> = emptySet()
): Set<String> = buildSet {
    configs.forEach { item ->
        if (isPluginSecretConfig(item.configKey, item.configType)) add(item.configKey)
    }
    addAll(recorded)
    stored.keys.forEach { key ->
        val declared = configs.firstOrNull { it.configKey == key }?.configType
        if (isPluginSecretConfig(key, declared)) add(key)
    }
}

/* ------------------------------------------------------------------ */
/* 保存 / 读取 / 迁移                                                   */
/* ------------------------------------------------------------------ */

/**
 * 一次配置保存的结果。
 *
 * @property plain 需要写进 `custom-schedule.json` 的**明文项**（敏感项加密失败时会回退到这里）
 * @property secretKeys 已成功加密保存的配置 key（写进 [CourseSchedule.pluginSecretKeys]）
 */
internal data class SavedPluginConfig(
    val plain: Map<String, String>,
    val secretKeys: Set<String>
)

/**
 * 保存一个课表的插件配置：敏感项写进 KVault，其余项明文返回。
 *
 * - 敏感项的值为空 → 视为「用户清空了该项」，删除对应密文（回落 manifest 默认值）；
 * - 加密写入失败 → 该值退回明文（不丢配置），也不记录为加密项。
 *
 * @param values 界面上的**完整**配置表（明文项 + 已解密读出的敏感项）
 * @param recorded 该课表已记录的加密 key（[CourseSchedule.pluginSecretKeys]）
 */
internal fun savePluginConfig(
    pluginId: String,
    scheduleId: String,
    configs: List<PluginConfigItem>,
    values: Map<String, String>,
    recorded: Set<String> = emptySet()
): SavedPluginConfig {
    val secretKeys = pluginSecretKeys(configs, values, recorded)
    if (secretKeys.isEmpty()) return SavedPluginConfig(values, emptySet())

    val plain = values.toMutableMap()
    val encrypted = mutableSetOf<String>()
    for (key in secretKeys) {
        val value = values[key]
        when {
            value == null -> {
                // 该项不在本次提交里：保留原记录（值仍可能在加密存储里）
                if (PluginSecretStore.read(pluginId, scheduleId, key) != null) encrypted += key
            }

            value.isEmpty() -> {
                // 用户清空 → 删除密文与明文条目（回落 manifest 默认值），不再记录
                PluginSecretStore.delete(pluginId, scheduleId, key)
                plain.remove(key)
            }

            PluginSecretStore.write(pluginId, scheduleId, key, value) -> {
                plain.remove(key)
                encrypted += key
            }
            // 加密失败：值留在明文里（不丢配置），也不记录为加密项
        }
    }
    return SavedPluginConfig(plain, encrypted)
}

/**
 * 读取一个课表**生效的**插件配置：明文项 + 从加密存储取回的敏感项。
 *
 * 敏感项以加密存储为准（覆盖存档里可能残留的旧明文值）；
 * 加密存储里没有时保留明文值，保证升级过程不丢配置。
 */
internal fun loadPluginConfig(
    pluginId: String?,
    scheduleId: String,
    configs: List<PluginConfigItem>,
    stored: Map<String, String>,
    secretKeys: Set<String> = emptySet()
): Map<String, String> {
    if (pluginId.isNullOrEmpty()) return stored
    val keys = pluginSecretKeys(configs, stored, secretKeys)
    if (keys.isEmpty()) return stored
    return stored + PluginSecretStore.readAll(pluginId, scheduleId, keys)
}

/**
 * 删除某课表的加密配置（删除课表时调用；明文项随课表一起消失）。
 *
 * @return 实际删除的密文条数
 */
internal fun deletePluginSecrets(
    pluginId: String?,
    scheduleId: String,
    configs: List<PluginConfigItem>,
    stored: Map<String, String>,
    secretKeys: Set<String> = emptySet()
): Int {
    if (pluginId.isNullOrEmpty()) return 0
    return PluginSecretStore.deleteAll(
        pluginId,
        scheduleId,
        pluginSecretKeys(configs, stored, secretKeys)
    )
}

/**
 * 迁移历史版本留在存档里的**明文敏感配置**：把 [CourseSchedule.pluginConfig] 里
 * 命中敏感规则的值搬进 KVault，并从存档中删除明文（只记录 key）。
 *
 * 启动时调用一次即可：加密失败的值会原样留在明文里，下次启动再试。
 *
 * @param installed 已安装插件（用于按 manifest 的 `password` 类型识别敏感项）
 * @return 迁移后的档案 + 是否发生了变化（true 时需要立即写回磁盘）
 */
internal fun migratePlaintextPluginSecrets(
    book: CourseBook,
    installed: List<InstalledPlugin>
): Pair<CourseBook, Boolean> {
    var changed = false
    val schedules = book.schedules.map { schedule ->
        val pluginId = schedule.pluginId ?: return@map schedule
        val configs = installed.firstOrNull { it.id == pluginId }?.manifest?.configs ?: emptyList()
        val candidates = schedule.pluginConfig.filterKeys { key ->
            isPluginSecretConfig(key, configs.configTypeOf(key))
        }
        if (candidates.isEmpty()) return@map schedule

        val plain = schedule.pluginConfig.toMutableMap()
        val recorded = schedule.pluginSecretKeys.toMutableSet()
        var scheduleChanged = false
        for ((key, value) in candidates) {
            if (value.isEmpty()) {
                PluginSecretStore.delete(pluginId, schedule.id, key)
                plain.remove(key)
                scheduleChanged = true
            } else if (PluginSecretStore.write(pluginId, schedule.id, key, value)) {
                plain.remove(key)
                recorded += key
                scheduleChanged = true
            }
        }
        if (!scheduleChanged) return@map schedule
        changed = true
        schedule.copy(pluginConfig = plain, pluginSecretKeys = recorded)
    }
    return if (changed) book.copy(schedules = schedules) to true else book to false
}

/** 便于测试与调用方阅读：某配置项在 manifest 中的类型（找不到返回 null）。 */
internal fun List<PluginConfigItem>.configTypeOf(key: String): PluginConfigType? =
    firstOrNull { it.configKey == key }?.configType
