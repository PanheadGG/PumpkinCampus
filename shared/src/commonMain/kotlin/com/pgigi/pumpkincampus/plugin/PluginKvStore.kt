package com.pgigi.pumpkincampus.plugin

import com.pgigi.pumpkincampus.constants.FileName
import com.pgigi.pumpkincampus.utils.FileStoreUtils
import com.pgigi.pumpkincampus.utils.JsonUtil
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray

/**
 * 插件 KV 数据存储：**按 (插件, 课表) 二维隔离**。
 *
 * 文件布局：`plugin-kv/<插件id>/<课表id>.json`，内容为 `{"值键": "JSON 文本"}`。
 * 插件运行时拿到的 `ctx.kv` 已经绑定好当前插件 id 与课表 id——
 * A 课表写入的数据，B 课表读不到；卸载插件时随 [PluginManager.uninstall] 一起清除。
 *
 * 值统一以 JSON 文本保存（对象/数组/数字/布尔原样还原），因此不同课表之间
 * 既不共享 key，也不会互相覆盖。
 */
class PluginKvStore(
    private val pluginId: String,
    private val scheduleId: String
) {

    @Serializable
    data class KvFile(val values: Map<String, String> = emptyMap())

    private val fileKey: String
        get() = "${FileName.PLUGIN_KV_DIR}/$pluginId/$scheduleId.json"

    private var cache: MutableMap<String, String>? = null

    private fun load(): MutableMap<String, String> {
        cache?.let { return it }
        val parsed = FileStoreUtils.readString(fileKey)?.let { raw ->
            runCatching { JsonUtil.parseJson(raw, KvFile.serializer()) }.getOrNull()
        }
        val map = parsed?.values?.toMutableMap() ?: mutableMapOf()
        cache = map
        return map
    }

    private fun persist() {
        val map = cache ?: return
        FileStoreUtils.writeString(fileKey, JsonUtil.toJson(KvFile(map), KvFile.serializer()))
    }

    /** 读取值的 JSON 文本；不存在返回 null。 */
    fun get(key: String): String? = load()[key]

    /** 写入值的 JSON 文本（value 已由 JS 侧 `JSON.stringify` 序列化）。 */
    fun set(key: String, valueJson: String) {
        load()[key] = valueJson
        persist()
    }

    /** 删除一个 key。 */
    fun remove(key: String) {
        if (load().remove(key) != null) persist()
    }

    /** 是否存在该 key。 */
    fun has(key: String): Boolean = load().containsKey(key)

    /** 全部 key（JSON 数组文本）。 */
    fun keysJson(): String = buildJsonArray {
        load().keys.sorted().forEach { add(JsonPrimitive(it)) }
    }.toString()

    /** 清空当前 (插件, 课表) 的全部 KV 数据。 */
    fun clear() {
        cache = mutableMapOf()
        FileStoreUtils.delete(fileKey)
    }
}
