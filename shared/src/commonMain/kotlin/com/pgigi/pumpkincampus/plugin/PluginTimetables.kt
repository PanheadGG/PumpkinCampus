package com.pgigi.pumpkincampus.plugin

import com.pgigi.pumpkincampus.models.LessonTime
import com.pgigi.pumpkincampus.models.LessonTimetable
import com.pgigi.pumpkincampus.schedule.parseClockMinutes
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * **插件推荐时间表**：插件包根目录放一个 `timetables.json`，宿主直接读取，
 * 供「课表设置 → 上课时间 → 插件推荐时间表」分组展示。
 *
 * 只是**纯数据**，不执行任何插件代码，也不需要先同步课表：
 * 选中插件后宿主就从 `plugins/<插件 id>/timetables.json` 读出来，
 * 用户点「使用」才复制成本课表的时间表（复制品归本课表所有，之后随便改）。
 *
 * 文件格式（顶层数组，或 `{ "timetables": [...] }`）：
 * ```json
 * [
 *   {
 *     "name": "三峡大学作息时间",
 *     "description": "本部校区",            // 可选，只在日志里出现
 *     "slots": [
 *       { "start": "08:00", "end": "08:45" },
 *       { "start": "08:55", "end": "09:40" }
 *     ]
 *   }
 * ]
 * ```
 */

/** 插件包内推荐时间表的文件名（包根目录）。 */
internal const val PLUGIN_TIMETABLE_FILE = "timetables.json"

/** 最多接受几张推荐时间表（多余的丢弃）。 */
private const val MAX_PLUGIN_TIMETABLES = 5

/** 一张推荐时间表最多接受几节（宿主的课表本身就支持任意节数，这里只防异常数据）。 */
private const val MAX_PLUGIN_TIMETABLE_SLOTS = 40

/** 解析结果：可用的推荐时间表 + 面向日志/开发者的告警。 */
internal data class PluginTimetableParseResult(
    val timetables: List<LessonTimetable>,
    val warnings: List<String>
)

private val TimetableJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

/** JSON 取值：宽松地当成字符串（数字/布尔也接受；null/缺失返回 null）。 */
private fun JsonElement?.textOrNull(): String? = (this as? JsonPrimitive)?.contentOrNull?.trim()

/** JSON 取值：字段名兼容多种写法（`slots` / `periods`）。 */
private fun JsonObject.firstArray(vararg keys: String): JsonArray? =
    keys.firstNotNullOfOrNull { key -> (this[key] as? JsonArray)?.takeIf { it.isNotEmpty() } }

/**
 * 解析插件包里的 `timetables.json`。
 *
 * 容错优先：能救的条目尽量救回来，救不回来的丢弃并写一条可读告警——
 * 推荐时间表只是**附加**数据，不该让插件不可用。
 *
 * @param raw 文件内容（null/空 = 插件没提供这个文件）
 * @param pluginId 当前插件 id（生成稳定的时间表 id 用）
 */
internal fun parsePluginTimetables(
    raw: String?,
    pluginId: String
): PluginTimetableParseResult {
    if (raw.isNullOrBlank()) return PluginTimetableParseResult(emptyList(), emptyList())

    val warnings = mutableListOf<String>()
    val element = runCatching { TimetableJson.parseToJsonElement(raw) }.getOrNull()
    // 顶层允许写数组，也允许包一层 { "timetables": [...] }（便于加注释性字段）
    val items: List<JsonElement> = when (element) {
        is JsonArray -> element.toList()
        is JsonObject -> (element["timetables"] as? JsonArray)?.toList() ?: listOf(element)
        else -> {
            warnings += "$PLUGIN_TIMETABLE_FILE 既不是数组也不是对象，已忽略"
            return PluginTimetableParseResult(emptyList(), warnings)
        }
    }

    val timetables = mutableListOf<LessonTimetable>()
    items.forEachIndexed { index, item ->
        if (timetables.size >= MAX_PLUGIN_TIMETABLES) {
            warnings += "推荐时间表超过 $MAX_PLUGIN_TIMETABLES 张，多余的已忽略"
            return@forEachIndexed
        }
        val obj = item as? JsonObject
        if (obj == null) {
            warnings += "推荐时间表第 ${index + 1} 项不是对象，已忽略"
            return@forEachIndexed
        }

        val name = obj["name"].textOrNull().orEmpty()
        val description = obj["description"].textOrNull().orEmpty()

        val rawSlots = obj.firstArray("slots", "periods")
        if (rawSlots == null) {
            warnings += "推荐时间表「${name.ifBlank { "第 ${index + 1} 张" }}」缺少 slots（节次时间数组），已忽略"
            return@forEachIndexed
        }

        val slots = mutableListOf<LessonTime>()
        var droppedSlots = 0
        rawSlots.forEach { slot ->
            val slotObj = slot as? JsonObject
            val start = slotObj?.get("start").textOrNull().orEmpty()
            val end = slotObj?.get("end").textOrNull().orEmpty()
            val startMinute = parseClockMinutes(start)
            val endMinute = parseClockMinutes(end)
            if (startMinute == null || endMinute == null || endMinute <= startMinute) {
                droppedSlots++
                return@forEach
            }
            if (slots.size < MAX_PLUGIN_TIMETABLE_SLOTS) {
                // 统一补零成 HH:mm（插件写 8:00 也能用）
                slots += LessonTime(start = padded(startMinute), end = padded(endMinute))
            }
        }
        if (slots.isEmpty()) {
            warnings += "推荐时间表「${name.ifBlank { "第 ${index + 1} 张" }}」没有合法的节次时间" +
                "（需要 { start: \"08:00\", end: \"08:45\" }），已忽略"
            return@forEachIndexed
        }
        if (droppedSlots > 0) {
            warnings += "推荐时间表「${name.ifBlank { "第 ${index + 1} 张" }}」有 $droppedSlots 节时间非法，已跳过"
        }
        if (rawSlots.size > MAX_PLUGIN_TIMETABLE_SLOTS) {
            warnings += "推荐时间表「${name.ifBlank { "第 ${index + 1} 张" }}」超过 " +
                "$MAX_PLUGIN_TIMETABLE_SLOTS 节，只保留前 $MAX_PLUGIN_TIMETABLE_SLOTS 节"
        }

        // 课表按节次顺序渲染，这里保证升序（文件里乱序时顺手排好并说明）
        val sorted = slots.sortedBy { parseClockMinutes(it.start) ?: 0 }
        if (sorted != slots) {
            warnings += "推荐时间表「${name.ifBlank { "第 ${index + 1} 张" }}」的节次时间不是升序，已自动排序"
        }
        if (name.isBlank()) {
            warnings += "推荐时间表第 ${index + 1} 张没有 name，已用默认名称"
        }

        timetables += LessonTimetable(
            id = pluginTimetableId(pluginId, index),
            name = name.ifBlank { "插件推荐时间表 ${index + 1}" },
            slots = sorted,
            fromDefaults = false
        )
        if (description.isNotEmpty()) {
            warnings += "推荐时间表「$name」说明：$description"
        }
    }

    return PluginTimetableParseResult(timetables, warnings)
}

/** 分钟数 → 补零的 `HH:mm`。 */
private fun padded(minutes: Int): String {
    val hour = minutes / 60
    val minute = minutes % 60
    return (if (hour < 10) "0$hour" else "$hour") + ":" + (if (minute < 10) "0$minute" else "$minute")
}

/**
 * 推荐时间表在宿主体内的 id：`plugin-<插件id>-<序号>`。
 *
 * 用插件 id 命名空间 + 序号，避免和用户自建时间表的 id（`tt<时间戳>`）冲突，
 * 也让同一张推荐表在多次读取之间保持稳定。
 */
internal fun pluginTimetableId(pluginId: String, index: Int): String = "plugin-$pluginId-$index"

/**
 * 读取已安装插件的推荐时间表（`plugins/<插件 id>/timetables.json`）。
 *
 * 阻塞 IO：调用方负责放进 `Dispatchers.Default`。文件不存在 / 格式不对都返回空列表
 * （插件没提供推荐时间表是正常情况）。
 */
internal fun readPluginTimetables(pluginId: String): List<LessonTimetable> =
    parsePluginTimetables(
        raw = PluginManager.readFile(pluginId, PLUGIN_TIMETABLE_FILE),
        pluginId = pluginId
    ).timetables

/* ------------------------------------------------------------------ */
/* 「使用」= 复制一份进本课表（见 models/TimetableCopy.kt）                */
/* ------------------------------------------------------------------ */

// 推荐表本身只是**来源**：点「使用」时用 withCopiedTimetable(candidate) 复制进本课表，
// 复制逻辑与「全局时间表」共用一份实现（models/TimetableCopy.kt）。
