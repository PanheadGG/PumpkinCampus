package com.pgigi.pumpkincampus.utils

import com.pgigi.pumpkincampus.models.AppSettings
import com.pgigi.pumpkincampus.models.Course
import com.pgigi.pumpkincampus.models.WidgetCourseItem
import com.pgigi.pumpkincampus.models.WidgetData
import com.pgigi.pumpkincampus.models.WidgetScheduleTime
import com.pgigi.pumpkincampus.schedule.currentLocalDate
import com.pgigi.pumpkincampus.schedule.dayIndexOf
import com.pgigi.pumpkincampus.schedule.formatClockMinutes
import com.pgigi.pumpkincampus.schedule.parseClockMinutes
import com.pgigi.pumpkincampus.schedule.weekCalculatorOf
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * 桌面小组件数据构建与解析（移植自 Pumpkin-Toolkit 的 `WidgetDataHelper`）。
 *
 * 分两步，应用与小组件各用一半：
 * 1. **构建**（应用侧）：[buildWidgetData] 把当前课表的课程 + 生效设置压成 [WidgetData] 落盘；
 * 2. **解析**（小组件侧）：[resolveSnapshot] 在**每次渲染时**按当前日期/时间算出
 *    「第几周 / 今天还剩哪些课 / 明天有哪些课」，见 [WidgetSnapshot]。
 *
 * 解析逻辑与应用内展示严格对齐：
 * - 教学周：[weekCalculatorOf]（「第一周的第一天」为第 1 周起点，该日期的星期即每周第一天）
 * - 课程归属：普通课程按 `weekIndices`（1 基周次落在 [WidgetCourseItem.weekRanges] 区间内），
 *   固定课程只认绝对日期
 * - 已下课过滤：只对「今天」生效，按结束钟点与当前时间比较（`HH:mm` 字符串比较即可）
 * - 记录里的时间是 `String`，与课程表左侧作息列同源
 */
@OptIn(ExperimentalTime::class)
object WidgetDataHelper {

    /** 星期文案，下标 0=周日 ... 6=周六（与 [com.pgigi.pumpkincampus.schedule.dayIndexOf] 对齐）。 */
    val DayOfWeekText: Array<String> = arrayOf("周日", "周一", "周二", "周三", "周四", "周五", "周六")

    /** 当前钟点 `HH:mm`（本地时区）。 */
    fun currentTime(): String {
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        return formatClockMinutes(now.hour * 60 + now.minute)
    }

    /**
     * 构建小组件数据（应用侧调用）。
     *
     * @param courses 当前课表的**展示层课程**（自定义 + 插件层），
     *   插件课程同样参与小组件展示——用户看到的课表就是小组件显示的课表
     * @param settings 当前课表的**生效设置**（课表专属项 + 全局显示项，见
     *   `AppSettings.withGlobalDisplay`）：作息、学期周数、第一周第一天、显示替换、外观
     * @param scheduleName 当前课表名称（仅作记录）
     */
    fun buildWidgetData(
        courses: List<Course>,
        settings: AppSettings,
        scheduleName: String = ""
    ): WidgetData {
        val lessonTimes = settings.activeLessonTimes()
        return WidgetData(
            scheduleName = scheduleName,
            termStart = settings.termStart,
            totalWeek = settings.semesterWeekCount,
            lessonTimes = lessonTimes.map { WidgetScheduleTime(it.start, it.end) },
            courses = courses.map { it.toWidgetCourseItem(settings) },
            updateTime = Clock.System.now().toEpochMilliseconds()
        )
    }

    /**
     * 把落盘的 [WidgetData] 解析成**可直接渲染**的快照；`data == null`（从未写过数据）返回 null，
     * 小组件据此显示「请打开应用刷新」。
     *
     * @param today 渲染基准日期（默认当前日期），iOS 时间线会传入未来时刻
     * @param currentTime 渲染基准钟点 `HH:mm`（默认当前时间）
     */
    fun resolveSnapshot(
        data: WidgetData?,
        today: LocalDate = currentLocalDate(),
        currentTime: String = currentTime()
    ): WidgetSnapshot? {
        if (data == null) return null

        val weekCalculator = weekCalculatorOf(data.termStart)
        val weekNumber = weekCalculator.getWeekNumber(today)
        val isHoliday = isHoliday(weekNumber, data.totalWeek)
        val dayIndex = dayIndexOf(today)

        val tomorrow = today.plus(1, DateTimeUnit.DAY)
        val tomorrowWeekNumber = weekCalculator.getWeekNumber(tomorrow)

        return WidgetSnapshot(
            scheduleName = data.scheduleName,
            weekNumber = weekNumber,
            totalWeek = data.totalWeek,
            isHoliday = isHoliday,
            dayOfWeekText = DayOfWeekText.getOrElse(dayIndex) { "" },
            todayCourses = if (isHoliday) {
                emptyList()
            } else {
                coursesOf(data, today, weekNumber, currentTime = currentTime, isTomorrow = false)
            },
            tomorrowCourses = if (isHoliday) {
                emptyList()
            } else {
                coursesOf(data, tomorrow, tomorrowWeekNumber, currentTime = null, isTomorrow = true)
            },
            updateTime = data.updateTime
        )
    }

    /** 假期判定：教学周不在 `1..totalWeek` 之内（周次 0/负 = 未开学，超出 = 学期已结束）。 */
    fun isHoliday(weekNumber: Int, totalWeek: Int): Boolean =
        weekNumber <= 0 || (totalWeek > 0 && weekNumber > totalWeek)

    /**
     * 某一天要显示的课程（按开始时间排序）。
     *
     * @param currentTime 非 null 时过滤掉**已经下课**的课程（只用于「今天」；
     *   明天的课程不存在这个问题）
     */
    private fun coursesOf(
        data: WidgetData,
        date: LocalDate,
        weekNumber: Int,
        currentTime: String?,
        isTomorrow: Boolean
    ): List<DisplayCourse> {
        if (weekNumber <= 0) return emptyList()
        val dayIndex = dayIndexOf(date)
        val dateText = date.toString()
        return data.courses
            .filter { it.occursOn(dateText, dayIndex, weekNumber) }
            .sortedBy { it.startMinuteOf(data.lessonTimes) }
            .mapNotNull { it.toDisplayCourse(data.lessonTimes, currentTime, isTomorrow) }
    }

    /** 这门课是否出现在指定日期：固定课程比绝对日期，普通课程比星期 + 教学周区间。 */
    private fun WidgetCourseItem.occursOn(dateText: String, dayIndex: Int, weekNumber: Int): Boolean {
        val fixed = fixedDate
        if (fixed != null) return fixed == dateText
        if (this.dayIndex != dayIndex) return false
        return weekRanges.any { range -> range.size >= 2 && range[0] <= weekNumber && range[1] >= weekNumber }
    }

    /** 当天排序用的开始分钟数：固定课程用真实钟点，普通课程查作息时间表。 */
    private fun WidgetCourseItem.startMinuteOf(lessonTimes: List<WidgetScheduleTime>): Int {
        if (fixedDate != null) return fixedStartMinute ?: 0
        val slot = lessonTimes.getOrNull(lessonStartIndex) ?: return lessonStartIndex.coerceAtLeast(0) * 100
        return parseClockMinutes(slot.start) ?: (lessonStartIndex.coerceAtLeast(0) * 100)
    }

    /**
     * 转成展示项；已经下课（结束钟点早于 [currentTime]）返回 null。
     *
     * 时间区间与应用内一致：固定课程 = `fixedStartMinute ~ +duration`，
     * 普通课程 = 起始课次的开始 ~ 结束课次的结束（越界时夹到时间表范围）。
     */
    private fun WidgetCourseItem.toDisplayCourse(
        lessonTimes: List<WidgetScheduleTime>,
        currentTime: String?,
        isTomorrow: Boolean
    ): DisplayCourse? {
        val startText: String
        val endText: String
        val fixedStart = fixedStartMinute
        if (fixedDate != null && fixedStart != null) {
            startText = formatClockMinutes(fixedStart)
            endText = formatClockMinutes(fixedStart + (fixedDurationMinutes ?: 0))
        } else {
            if (lessonTimes.isEmpty()) return null
            val startIndex = lessonStartIndex.coerceIn(0, lessonTimes.lastIndex)
            val endIndex = (startIndex + lessonCount - 1).coerceIn(0, lessonTimes.lastIndex)
            startText = normalizeClock(lessonTimes[startIndex].start)
            endText = normalizeClock(lessonTimes[endIndex].end)
        }
        if (currentTime != null && endText < currentTime) return null
        return DisplayCourse(
            name = name,
            classroom = classroom,
            teacher = teacher,
            startTime = startText,
            endTime = endText,
            isTomorrow = isTomorrow,
            // 「正在上课」= 已开始且未结束（结束时刻当分钟仍算进行中，与上面「已下课才隐藏」一致）
            isOngoing = currentTime != null && startText <= currentTime && currentTime <= endText
        )
    }

    /** 作息时间表里的钟点可能是 `8:00` 这类写法，统一成可比较的 `08:00`。 */
    private fun normalizeClock(text: String): String =
        parseClockMinutes(text)?.let { formatClockMinutes(it) } ?: text

    /**
     * 课表的展示层投影：套用显示替换（[AppSettings.display]）与地点「@」前缀
     * （与课程表格子里的展示规则一致，见 `SchedulePager` 的格子渲染）。
     */
    private fun Course.toWidgetCourseItem(settings: AppSettings): WidgetCourseItem {
        val visibleClassroom = when {
            classroom.isBlank() -> ""
            settings.classroomAtPrefix -> "@" + settings.display(classroom)
            else -> settings.display(classroom)
        }
        return WidgetCourseItem(
            name = settings.display(name),
            classroom = visibleClassroom,
            teacher = settings.display(teacher),
            dayIndex = dayIndex,
            lessonStartIndex = lessonStartIndex,
            lessonCount = lessonCount,
            weekRanges = weekIndicesToRanges(weekIndices),
            fixedDate = fixedDate,
            fixedStartMinute = fixedStartMinute,
            fixedDurationMinutes = fixedDurationMinutes
        )
    }

    /**
     * 0 基周索引数组 → 1 基教学周区间数组：
     * `[0,1,2,3,5,6,7,8]` → `[[1,4],[6,9]]`（连续段合并，乱序/重复自动规整）。
     */
    fun weekIndicesToRanges(weekIndices: List<Int>): List<List<Int>> {
        val sorted = weekIndices.filter { it >= 0 }.distinct().sorted()
        if (sorted.isEmpty()) return emptyList()
        val ranges = mutableListOf<List<Int>>()
        var start = sorted.first()
        var previous = start
        for (index in 1 until sorted.size) {
            val current = sorted[index]
            if (current == previous + 1) {
                previous = current
                continue
            }
            ranges += listOf(start + 1, previous + 1)
            start = current
            previous = current
        }
        ranges += listOf(start + 1, previous + 1)
        return ranges
    }
}

/**
 * 小组件当前要显示的内容（应用与小组件之间的展示契约，纯数据、可跨平台直接渲染）。
 *
 * @property isHoliday 假期中（教学周不在学期范围内）——小组件显示「假期中」
 * @property todayCourses **今天还没下课**的课程（按开始时间排序）
 * @property tomorrowCourses 明天的全部课程
 */
data class WidgetSnapshot(
    val scheduleName: String = "",
    val weekNumber: Int = 0,
    val totalWeek: Int = 0,
    val isHoliday: Boolean = false,
    val dayOfWeekText: String = "",
    val todayCourses: List<DisplayCourse> = emptyList(),
    val tomorrowCourses: List<DisplayCourse> = emptyList(),
    val updateTime: Long = 0L
)

/**
 * 小组件里的一门课（已算好起止钟点与展示文本）。
 *
 * 两个标记决定小组件的配色（Android / iOS 同一套规则）：
 * - [isOngoing]：**正在上课**（当前钟点在起止之间）→ 橙色强调
 * - [isTomorrow]：属于「明天」分组 → 整体降为灰色
 *
 * 两者互斥（明天的课不会算「正在进行」），今天其它课程用常规文字色 + 灰色次要信息。
 */
data class DisplayCourse(
    val name: String = "",
    val classroom: String = "",
    val teacher: String = "",
    val startTime: String = "",
    val endTime: String = "",
    val isTomorrow: Boolean = false,
    val isOngoing: Boolean = false
)
