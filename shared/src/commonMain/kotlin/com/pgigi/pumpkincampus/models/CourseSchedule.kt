package com.pgigi.pumpkincampus.models

import kotlinx.serialization.Serializable

/** 默认（迁移/兜底用）课表 id 与名称。 */
const val DefaultScheduleId: String = "default"
const val DefaultScheduleName: String = "默认课表"

/**
 * 课表**导出/导入信封**（导入解析以该结构为基础）。
 *
 * 除全部自定义课程外，还携带**课表名称**（[name]）与该课表的设置：
 * 课表时间（上课时间/作息）、课表开始日期（第一周的第一天）、
 * 学期周数、一天课程节数等（[AppSettings]）。
 *
 * 兼容旧形态（课程数组 / `{"courses": [...]}` / 课表存档
 * `{updateTime, courses}`——无设置、无名称，导入时保留目标课表设置）。
 */
@Serializable
data class ScheduleExport(
    val type: String = "pumpkincampus.schedule",
    val version: Int = 1,
    val exportTime: Long = 0L,
    /** 导出课表的名称；旧文件没有该字段时为空。 */
    val name: String = "",
    val courses: List<Course> = emptyList(),
    val settings: AppSettings? = null
)

/**
 * 一次导入的解析结果：课程（必非空）+ 导出时携带的课表设置与名称（可空）。
 *
 * @property name 导出信封里的课表名称；旧格式没有名称时为 `""`
 */
data class ImportedSchedule(
    val courses: List<Course>,
    val settings: AppSettings? = null,
    val name: String = ""
)

/**
 * 一套独立课表（多课表之一）。
 *
 * 每套课表持有**自己的一组自定义课程**：课程只属于所属课表，
 * 不同课表之间互不相通（在 A 课表新增 / 修改 / 删除的课程不会影响 B 课表）。
 *
 * @property settings 该课表的**专属设置**（第一周第一天、辅助线、课表外观、上课时间、
 * 一天课程节数、学期周数等），只对该课表生效；`null` = 尚未定制，
 * 使用设置页里的默认值（[AppSettings]，即新建课表时的默认设置）
 */
@Serializable
data class CourseSchedule(
    val id: String = "",
    val name: String = "",
    val courses: List<Course> = emptyList(),
    val settings: AppSettings? = null
)

/**
 * `custom-schedule.json` 的多课表档案格式：全部课表 + 当前打开的课表。
 *
 * 旧格式（[ScheduleCache]：`{updateTime, courses}`）在读取时自动迁移为
 * 单个默认课表（[DefaultScheduleId] / [DefaultScheduleName]）。
 */
@Serializable
data class CourseBook(
    /** 当前打开（课表页展示）的课表 id。 */
    val activeScheduleId: String = DefaultScheduleId,
    /** 全部课表（归一化后至少一套）。 */
    val schedules: List<CourseSchedule> = emptyList()
) {

    /** 当前打开的课表；id 失效时回落到第一套。 */
    fun activeSchedule(): CourseSchedule? =
        schedules.firstOrNull { it.id == activeScheduleId } ?: schedules.firstOrNull()

    /** 归一化：保证至少一套课表、activeScheduleId 一定有效。 */
    fun normalized(): CourseBook {
        if (schedules.isEmpty()) {
            return CourseBook(
                activeScheduleId = DefaultScheduleId,
                schedules = listOf(
                    CourseSchedule(id = DefaultScheduleId, name = DefaultScheduleName)
                )
            )
        }
        val validId = activeScheduleId.takeIf { id -> schedules.any { it.id == id } }
            ?: schedules.first().id
        return if (validId == activeScheduleId) this else copy(activeScheduleId = validId)
    }
}
