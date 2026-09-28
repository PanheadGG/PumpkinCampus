package com.pgigi.pumpkincampus.models

import kotlinx.serialization.Serializable

/** 小组件数据格式版本（[WidgetData.version] 当前值）。 */
const val WidgetDataVersion: Int = 1

/**
 * 桌面小组件数据（`widget_data.json`，移植自 Pumpkin-Toolkit 的同名模型）。
 *
 * **存的是「原料」而不是算好的结果**：课表、作息、学期周数原样落盘，由小组件在
 * **每次渲染时**（Android Glance 渲染 / iOS 时间线生成）重新计算「今天还剩几节 / 明天几节」。
 * 这样小组件不必等应用启动就能自己翻页：课程结束、跨零点、系统定时刷新
 * （Android `updatePeriodMillis` / iOS Timeline）都会得到正确的展示。
 *
 * 应用侧只在课表或设置发生变化时重写这个文件（见 `HomeScreen` 的 `LaunchedEffect`）。
 *
 * @property scheduleName 写入时的课表名称（当前打开的课表），仅作信息记录
 * @property termStart 「第一周的第一天」；`null` = 未设置，小组件按应用同一套默认锚点动态推算
 * @property totalWeek 学期周数（[AppSettings.semesterWeekCount]）
 * @property lessonTimes 当前生效的作息时间（[AppSettings.activeLessonTimes]）
 * @property courses 当前课表的全部课程（自定义 + 插件层，展示层字符串替换已套用）
 * @property updateTime 写入时间戳（毫秒），便于排查数据新旧
 */
@Serializable
data class WidgetData(
    val version: Int = WidgetDataVersion,
    val scheduleName: String = "",
    val termStart: String? = null,
    val totalWeek: Int = 0,
    val lessonTimes: List<WidgetScheduleTime> = emptyList(),
    val courses: List<WidgetCourseItem> = emptyList(),
    val updateTime: Long = 0L
)

/**
 * 小组件里的单门课程（[Course] 的小组件投影）。
 *
 * 与 [Course] 的差异：
 * - [weekRanges] 把 [Course.weekIndices]（0 基、可能零散）压成**1 基教学周区间**（`[[1,4],[6,9]]`），
 *   便于 Android / iOS 两端用同一套判断逻辑
 * - [name] / [classroom] / [teacher] 已经是**展示用文本**（套用了显示替换与地点「@」前缀），
 *   小组件端不再需要理解 [AppSettings.replaces]
 * - 固定课程（[Course.fixedDate] 非空）原样带出，按绝对日期 + 真实钟点展示
 */
@Serializable
data class WidgetCourseItem(
    val name: String = "",
    val classroom: String = "",
    val teacher: String = "",
    /** 星期索引 0~6（0=周日 ... 6=周六），与 [Course.dayIndex] 一致。 */
    val dayIndex: Int = -1,
    /** 0 基起始课次，与 [Course.lessonStartIndex] 一致。 */
    val lessonStartIndex: Int = -1,
    /** 占用课节数量，与 [Course.lessonCount] 一致。 */
    val lessonCount: Int = 2,
    /** 出现的教学周区间（1 基，闭区间），如 `[[1,4],[6,9]]`。 */
    val weekRanges: List<List<Int>> = emptyList(),
    /** 固定课程：绝对日期 `yyyy-MM-dd`；非 null 表示固定课程。 */
    val fixedDate: String? = null,
    /** 固定课程：当天 0 点起的开始分钟数。 */
    val fixedStartMinute: Int? = null,
    /** 固定课程：持续分钟数。 */
    val fixedDurationMinutes: Int? = null
)

/** 一节课的起止钟点（小组件数据副本，与 [LessonTime] 结构一致）。 */
@Serializable
data class WidgetScheduleTime(
    val start: String = "",
    val end: String = ""
)
