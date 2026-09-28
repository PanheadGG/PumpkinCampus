package com.pgigi.pumpkincampus.plugin

import com.pgigi.pumpkincampus.appVersionCode
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull

/* ------------------------------------------------------------------ */
/* manifest.json 数据结构                                              */
/* ------------------------------------------------------------------ */

/** manifest 中 `configs` 数组的一项：一个可配置项的**声明**（不是值）。 */
@Serializable
data class PluginConfigItem(
    /** 配置项标题，同时作为**配置 key**（除非 [key] 显式指定）。列表内应保持唯一。 */
    val title: String = "",
    /** 显式配置 key；缺省时用 [title]。课表配置值与 KV 上下文都按这个 key 存取。 */
    val key: String? = null,
    /** 配置项描述，可为 null；展示在配置编辑弹层里。 */
    val description: String? = null,
    /** 取值类型：`string` / `password` / `int` / `bool` / `select`（其他值按 `string` 处理）。 */
    val type: String = "string",
    /** 默认值：null / 字符串 / 整数 / 布尔；课表未保存过该配置时使用。 */
    val default: JsonElement? = null,
    /**
     * 候选值列表（可选）。非空时配置弹层里会把这些值列成一排候选按钮。
     *
     * - `type: "select"`（别名 `option`）：**只能从候选值里选**，配置项不提供输入框，
     *   值是 [PluginConfigOption.value]；
     * - 其他类型：候选只是建议，点一下填入，**仍然可以自己手动输入**（宿主不校验）。
     *
     * 解析很宽松：对象取 `name` / `value`（缺 `name` 时用 `value` 当名称；
     * 字符串/数字/布尔按「名称 = 值」处理——兼容早期的纯字符串写法）；
     * `null`、数组与空值丢弃，整个字段不是数组时按空列表处理。
     */
    @Serializable(with = OptionListSerializer::class)
    val options: List<PluginConfigOption> = emptyList()
) {
    /** 该配置项实际使用的 key（[key] 优先，缺省回落 [title]）。 */
    val configKey: String
        get() = key?.takeIf { it.isNotBlank() } ?: title

    /** 规范化后的类型。 */
    val configType: PluginConfigType
        get() = PluginConfigType.of(type)
}

/**
 * `configs[].options` 里的一项候选值。
 *
 * @property name 展示名称（配置弹层里用户看到的文案）
 * @property value **实际值**：写进课表配置，插件在 `ctx.config` 里拿到的就是它
 */
@Serializable
data class PluginConfigOption(
    val name: String = "",
    val value: String = ""
)

/**
 * `configs[].options` 的宽松解析器。
 *
 * 规范写法是 `{ "name": "本地登录", "value": "local" }` 对象数组，但为了兼容与容错：
 *
 * - 对象：取 `name` / `value`；缺 `name` 时用 `value` 当名称，`value` 为空则丢弃；
 * - 字符串/数字/布尔：按「名称 = 值」处理（早期写法 `"2024-2025-1"` 仍然可用）；
 * - `null`、数组、空白串丢弃；整个字段不是数组时按空列表处理；
 * - 字段有笔误时不会让整个 manifest 解析失败。
 */
private object OptionListSerializer : KSerializer<List<PluginConfigOption>> {
    private val delegate = ListSerializer(PluginConfigOption.serializer())

    override val descriptor: SerialDescriptor = delegate.descriptor

    override fun deserialize(decoder: Decoder): List<PluginConfigOption> {
        val input = decoder as? JsonDecoder ?: return emptyList()
        val array = input.decodeJsonElement() as? JsonArray ?: return emptyList()
        return array.mapNotNull { element -> element.toConfigOption() }
    }

    override fun serialize(encoder: Encoder, value: List<PluginConfigOption>) =
        delegate.serialize(encoder, value)
}

/** 单个候选值元素 → [PluginConfigOption]；无法解析时返回 null（丢弃该项）。 */
private fun JsonElement.toConfigOption(): PluginConfigOption? = when (this) {
    is JsonObject -> {
        val value = (this["value"] as? JsonPrimitive)?.contentOrNullSafely()?.trim().orEmpty()
        val name = (this["name"] as? JsonPrimitive)?.contentOrNullSafely()?.trim().orEmpty()
        when {
            value.isNotEmpty() -> PluginConfigOption(name.ifEmpty { value }, value)
            // 只写了 name：把它当成值（name 与 value 相同，至少不会丢配置项）
            name.isNotEmpty() -> PluginConfigOption(name, name)
            else -> null
        }
    }

    is JsonPrimitive ->
        contentOrNullSafely()?.trim()?.takeIf { it.isNotEmpty() }
            ?.let { PluginConfigOption(it, it) }

    else -> null // null / 数组：丢弃
}

/** manifest `configs[].type` 的规范化枚举。 */
enum class PluginConfigType {
    STRING, PASSWORD, INT, BOOL,

    /**
     * 只能从 `options` 里选一个（值为 `PluginConfigOption.value`），
     * 配置弹层**不提供输入框**。
     */
    SELECT;

    companion object {
        /** 解析 manifest 里的 type 字符串；未知值一律按 [STRING] 处理（宽松前向兼容）。 */
        fun of(raw: String): PluginConfigType = when (raw.trim().lowercase()) {
            "password" -> PASSWORD
            "int", "integer", "number" -> INT
            "bool", "boolean" -> BOOL
            "select", "option", "options", "enum" -> SELECT
            else -> STRING
        }
    }
}

/**
 * 插件元数据（`manifest.json`）。
 *
 * 与宿主约定一一对应：`id` 全局唯一且是插件的安装目录名；`entry` 是包内
 * ES Module 入口（相对 manifest 所在目录）；`minHostVersionCode` 与宿主
 * [appVersionCode] 比较，宿主版本不足时插件不可安装/运行。
 */
@Serializable
data class PluginManifest(
    /** 插件 id（全局唯一），如 `com.example.university.course-plugin`。 */
    val id: String = "",
    /** 插件名称（展示用）。 */
    val name: String = "",
    /** 插件版本号（展示用；同 id 重装即升级/降级覆盖）。 */
    val version: String = "1.0.0",
    /** 入口 ES Module 文件（相对包根目录），默认 `index.js`。 */
    @SerialName("entry")
    val entry: String = "index.js",
    /** 需要的最小宿主 versionCode；宿主低于该值时拒绝安装与运行。 */
    val minHostVersionCode: Int = 1,
    /** 学校名称（教务系统适配插件展示用）。 */
    val schoolName: String = "",
    /** 插件描述。 */
    val description: String = "",
    /** 作者。 */
    val author: String = "",
    /** 更新时间（展示用，如 `2026-09-26`）。 */
    val updateTime: String = "",
    /** 配置项声明；每个课表保存一份自己的配置值。 */
    val configs: List<PluginConfigItem> = emptyList()
) {
    /** id 是否合法：字母/数字开头，仅允许 `[A-Za-z0-9._-]`，且不含路径分隔符。 */
    fun isIdValid(): Boolean = ID_REGEX.matches(id)

    /** 入口路径是否合法：包内相对路径，仅允许字母/数字/`_-.`/`/`（会被写进 import 语句）。 */
    fun isEntryValid(): Boolean =
        ENTRY_REGEX.matches(entry) && !entry.contains("..")

    /** 该 manifest 在当前宿主上是否兼容（[minHostVersionCode] <= 宿主 versionCode）。 */
    fun isCompatibleWithHost(): Boolean = minHostVersionCode <= appVersionCode()

    companion object {
        private val ID_REGEX = Regex("""[A-Za-z0-9][A-Za-z0-9._-]*""")
        private val ENTRY_REGEX = Regex("""[A-Za-z0-9_\-./]+""")

        /** 包内相对路径安全检查：非空、非绝对、不含 `..` 段、不含反斜杠盘符。 */
        fun isSafeRelativePath(path: String): Boolean {
            if (path.isBlank()) return false
            val norm = path.replace('\\', '/')
            if (norm.startsWith("/") || norm.contains("..")) return false
            if (norm.length >= 2 && norm[1] == ':') return false // Windows 盘符
            return true
        }
    }
}

/** 一次安装的结果（成功携带已解析的 manifest）。 */
data class PluginInstallResult(
    val success: Boolean,
    val message: String,
    val manifest: PluginManifest? = null
)

/**
 * 已安装插件（[manifest] + 宿主兼容性）。
 *
 * @property compatible `minHostVersionCode` 是否不高于当前宿主 versionCode；
 *   不兼容的插件仍会列出，但不会执行（同步时给出明确错误）
 */
data class InstalledPlugin(
    val manifest: PluginManifest,
    val compatible: Boolean = true
) {
    val id: String get() = manifest.id
    val name: String get() = manifest.name
}

/* ------------------------------------------------------------------ */
/* 解析与配置合并                                                       */
/* ------------------------------------------------------------------ */

/** manifest 解析用 Json（与 [com.pgigi.pumpkincampus.utils.JsonUtil] 同配置）。 */
private val ManifestJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    encodeDefaults = true
}

/**
 * 解析 manifest 文本（支持行注释与块注释，见 [JsonComments]）。
 *
 * @return 解析失败返回 null；调用方负责给出用户可读的错误
 */
fun parsePluginManifest(raw: String): PluginManifest? = runCatching {
    ManifestJson.decodeFromString(PluginManifest.serializer(), JsonComments.strip(raw))
}.getOrNull()

/**
 * 校验 manifest 必填项。
 *
 * @return null = 通过；否则为用户可读的错误文案
 */
fun validatePluginManifest(manifest: PluginManifest): String? = when {
    !manifest.isIdValid() -> "manifest.json 的 id 不合法：只能包含字母、数字、点、下划线和连字符"
    manifest.name.isBlank() -> "manifest.json 缺少 name（插件名称）"
    !manifest.isEntryValid() -> "manifest.json 的 entry 不是合法的包内相对路径"
    else -> null
}

/**
 * 把**某个课表保存的配置值**与 manifest 的**默认值**合并成插件看到的 `ctx.config`。
 *
 * 合并规则（对每个 config 项）：
 * 1. 课表保存的值（[values]，key = [PluginConfigItem.configKey]）优先；
 * 2. 否则用 manifest 的 `default`；
 * 3. 都没有时按类型给零值（`""` / `0` / `false`）。
 *
 * 输出按 [PluginConfigItem.configType] 转成 JSON 原生类型：
 * `string`/`password`/`select` → 字符串（`select` 取候选值的 `value`），`int` → 整数，`bool` → 布尔。
 */
fun buildPluginConfigJson(
    configs: List<PluginConfigItem>,
    values: Map<String, String>
): String {
    val obj = buildMap<String, JsonElement> {
        for (item in configs) {
            val key = item.configKey
            if (key.isEmpty()) continue
            val saved = values[key]?.takeIf { it.isNotEmpty() }
            val def = item.default
            put(key, when (item.configType) {
                PluginConfigType.INT -> JsonPrimitive(
                    (saved ?: def?.let { (it as? JsonPrimitive)?.content })
                        ?.toIntOrNull()
                        ?: (def as? JsonPrimitive)?.intOrNull
                        ?: 0
                )

                PluginConfigType.BOOL -> JsonPrimitive(
                    (saved ?: def?.let { (it as? JsonPrimitive)?.content })?.toBooleanValue()
                        ?: (def as? JsonPrimitive)?.booleanOrNull
                        ?: false
                )

                // STRING / PASSWORD / SELECT：值都是字符串（SELECT 存的是候选值的 value）
                else -> JsonPrimitive(
                    saved ?: (def as? JsonPrimitive)?.contentOrNullSafely() ?: ""
                )
            })
        }
    }
    return JsonObject(obj).toString()
}

/** 宽松布尔解析：`"true"/"1"/"yes"` 为 true，`"false"/"0"/"no"` 为 false，其他 null。 */
private fun String.toBooleanValue(): Boolean? = when (trim().lowercase()) {
    "true", "1", "yes", "on" -> true
    "false", "0", "no", "off" -> false
    else -> null
}

/** JsonPrimitive 的字符串内容；[JsonNull] 返回 null。 */
private fun JsonPrimitive.contentOrNullSafely(): String? =
    if (this is JsonNull) null else content

/**
 * 读取插件返回的课程 JSON 数组（`getCourses` 的返回值）。
 *
 * 元素必须是对象；支持两种周次写法：
 * - `weekIndices: [0,1,2]`：0 基（0 = 第 1 教学周，与 [com.pgigi.pumpkincampus.models.Course] 一致）
 * - `weeks: [1,2,3]`：1 基（教务系统里常见的写法），宿主自动减一转换
 *
 * @return Pair(解析出的课程, 面向开发者的告警日志)
 */
fun parsePluginCourses(raw: String?, limit: Int = MAX_PLUGIN_COURSES): Pair<List<com.pgigi.pumpkincampus.models.Course>, List<String>> {
    val warnings = mutableListOf<String>()
    if (raw.isNullOrBlank()) {
        warnings += "getCourses() 没有返回结果（需要返回课程数组）"
        return emptyList<com.pgigi.pumpkincampus.models.Course>() to warnings
    }
    val element = runCatching { Json.parseToJsonElement(raw) }.getOrElse {
        warnings += "返回值不是合法 JSON：${it.message}"
        return emptyList<com.pgigi.pumpkincampus.models.Course>() to warnings
    }
    val array = element as? JsonArray ?: run {
        warnings += "getCourses() 应返回课程数组 [...]，实际返回了 ${element::class.simpleName}"
        return emptyList<com.pgigi.pumpkincampus.models.Course>() to warnings
    }

    val courses = mutableListOf<com.pgigi.pumpkincampus.models.Course>()
    for ((index, item) in array.withIndex()) {
        if (courses.size >= limit) {
            warnings += "课程数量超过上限 $limit，多余的已忽略"
            break
        }
        val parsed = runCatching { normalizeCourseElement(index, item) }.getOrElse {
            warnings += "第 ${index + 1} 门课程解析失败：${it.message}"
            continue
        }
        val course = runCatching {
            ManifestJson.decodeFromJsonElement(
                com.pgigi.pumpkincampus.models.Course.serializer(),
                parsed
            )
        }.getOrElse {
            warnings += "第 ${index + 1} 门课程字段不合法：${it.message}"
            continue
        }
        val reason = validateCourse(course)
        if (reason != null) {
            warnings += "第 ${index + 1} 门课程「${course.name.ifBlank { "?" }}」被忽略：$reason"
            continue
        }
        courses += course
    }
    return courses to warnings
}

/** 单门课程的对象级归一化：`weeks`(1 基) → `weekIndices`(0 基)。 */
private fun normalizeCourseElement(index: Int, item: JsonElement): JsonElement {
    val obj = item as? JsonObject
        ?: throw IllegalArgumentException("第 ${index + 1} 项不是对象")
    val weeks = obj["weeks"] as? JsonArray
    if (weeks != null && obj["weekIndices"] == null) {
        val indices = weeks.map { el ->
            val n = (el as? JsonPrimitive)?.intOrNull
                ?: throw IllegalArgumentException("weeks 含非整数元素")
            n - 1 // 1 基 → 0 基
        }
        val merged = obj.toMutableMap()
        merged.remove("weeks")
        merged["weekIndices"] = JsonArray(indices.map { JsonPrimitive(it) })
        return JsonObject(merged)
    }
    return obj
}

/** 课程字段级校验；返回 null 表示通过，否则为忽略原因。 */
private fun validateCourse(course: com.pgigi.pumpkincampus.models.Course): String? {
    if (course.name.isBlank()) return "name 为空"
    // 固定课程（按绝对日期 + 钟表时间）：只校验日期与时间
    if (course.fixedDate != null) {
        if (course.fixedStartMinute == null || course.fixedDurationMinutes == null) {
            return "fixedDate 存在但缺少 fixedStartMinute / fixedDurationMinutes"
        }
        return null
    }
    if (course.dayIndex !in 0..6) return "dayIndex 需要在 0(周日)~6(周六) 之间"
    if (course.lessonStartIndex < 0) return "lessonStartIndex 不能为负（0 = 第一节）"
    if (course.lessonCount < 1) return "lessonCount 至少为 1"
    if (course.weekIndices.isEmpty()) return "weekIndices 为空（可用 weeks: [1,2,...] 1 基写法）"
    if (course.weekIndices.any { it < 0 }) return "weekIndices 不能为负（0 = 第 1 教学周）"
    return null
}

/** 单次同步最多接受的课程数（防御插件失控）。 */
const val MAX_PLUGIN_COURSES = 2000
