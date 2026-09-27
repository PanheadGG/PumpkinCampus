package com.pgigi.pumpkincampus.plugin

import com.pgigi.pumpkincampus.constants.FileName
import com.pgigi.pumpkincampus.models.Course
import com.pgigi.pumpkincampus.models.ScheduleCache
import com.pgigi.pumpkincampus.utils.FileStoreUtils
import com.pgigi.pumpkincampus.utils.JsonUtil
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * 一个课表的**插件同步结果**（持久化在 `plugin-schedule.json`，按课表 id 隔离）。
 *
 * @property pluginId 最近一次同步所用的插件 id（切换插件后旧结果仍保留，
 *   直到下一次成功同步覆盖）
 * @property updateTime 最近一次**成功**同步的时间戳（0 = 从未成功）
 * @property lastAttempt 最近一次尝试同步的时间戳（成功或失败都会更新，用于节流）
 * @property courses 插件拉回并合并进课表展示的课程（只读，不可在课表里编辑）
 * @property lastError 最近一次同步的失败原因；成功时为 null
 * @property lastLogs 最近一次同步捕获的 console 输出（开发者调试用，最多若干条）
 */
@Serializable
data class PluginScheduleEntry(
    val pluginId: String = "",
    val updateTime: Long = 0L,
    val lastAttempt: Long = 0L,
    val courses: List<Course> = emptyList(),
    val lastError: String? = null,
    val lastLogs: List<String> = emptyList()
) {
    /** 是否携带可用的课程/错误信息（从未同步过为 false）。 */
    val hasSynced: Boolean get() = lastAttempt > 0L
}

/**
 * `plugin-schedule.json`（v2 格式）：全部课表的插件同步结果。
 *
 * @property legacy 旧格式（`{updateTime, courses}`）迁移过来的数据——旧文件是
 *   全局共享的预留档案，迁移后作为**跨课表叠加层**继续参与展示，避免丢数据
 * @property schedules 按课表 id 的同步结果（每个课表最多绑定一个插件）
 */
@Serializable
data class PluginScheduleArchive(
    val version: Int = 2,
    val legacy: PluginScheduleEntry? = null,
    val schedules: Map<String, PluginScheduleEntry> = emptyMap()
)

/** 插件课表档案读写（`plugin-schedule.json`）。 */
object PluginScheduleStore {

    private val ProbeJson = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    /** 读取档案；文件不存在 / 损坏时返回空档案。自动把旧格式迁移为 [PluginScheduleArchive]。 */
    fun read(): PluginScheduleArchive {
        val raw = FileStoreUtils.readString(FileName.PLUGIN_SCHEDULE)
            ?: return PluginScheduleArchive()
        return try {
            val element = ProbeJson.parseToJsonElement(raw)
            if (element is JsonObject && "schedules" in element) {
                ProbeJson.decodeFromJsonElement(PluginScheduleArchive.serializer(), element)
            } else {
                // 旧格式：{updateTime, courses}（预留的全局插件课表档案）
                val old = runCatching {
                    JsonUtil.parseJson(raw, ScheduleCache.serializer())
                }.getOrNull()
                PluginScheduleArchive(
                    legacy = old?.let {
                        PluginScheduleEntry(
                            pluginId = "",
                            updateTime = it.updateTime,
                            lastAttempt = it.updateTime,
                            courses = it.courses
                        )
                    }
                )
            }
        } catch (_: Exception) {
            PluginScheduleArchive()
        }
    }

    /** 写回档案。 */
    fun write(archive: PluginScheduleArchive) {
        FileStoreUtils.writeString(
            FileName.PLUGIN_SCHEDULE,
            JsonUtil.toJson(archive, PluginScheduleArchive.serializer())
        )
    }
}

/* ------------------------------------------------------------------ */
/* 档案更新（函数式拷贝，调用方负责写回）                                 */
/* ------------------------------------------------------------------ */

/** 取出（或创建）某课表的条目。 */
private fun PluginScheduleArchive.entryFor(scheduleId: String, pluginId: String): PluginScheduleEntry =
    schedules[scheduleId] ?: PluginScheduleEntry(pluginId = pluginId)

/** 记录一次**同步尝试**（更新 lastAttempt；用于打开课表时的自动同步节流）。 */
fun PluginScheduleArchive.withAttempt(
    scheduleId: String,
    pluginId: String,
    time: Long
): PluginScheduleArchive = copy(
    schedules = schedules +
        (scheduleId to entryFor(scheduleId, pluginId).copy(pluginId = pluginId, lastAttempt = time))
)

/** 同步**成功**：覆盖课程、清空错误、更新 updateTime/lastAttempt。 */
fun PluginScheduleArchive.withSyncSuccess(
    scheduleId: String,
    pluginId: String,
    courses: List<com.pgigi.pumpkincampus.models.Course>,
    logs: List<String>,
    time: Long
): PluginScheduleArchive = copy(
    schedules = schedules + (scheduleId to entryFor(scheduleId, pluginId).copy(
        pluginId = pluginId,
        updateTime = time,
        lastAttempt = time,
        courses = courses,
        lastError = null,
        lastLogs = logs
    ))
)

/** 同步**失败**：保留上一次成功的课程，只记录错误与日志。 */
fun PluginScheduleArchive.withSyncFailure(
    scheduleId: String,
    pluginId: String,
    error: String,
    logs: List<String>,
    time: Long
): PluginScheduleArchive = copy(
    schedules = schedules + (scheduleId to entryFor(scheduleId, pluginId).copy(
        pluginId = pluginId,
        lastAttempt = time,
        lastError = error,
        lastLogs = logs
    ))
)
