package com.pgigi.pumpkincampus.schedule

import com.pgigi.pumpkincampus.models.Course
import com.pgigi.pumpkincampus.models.LessonTime
import com.pgigi.pumpkincampus.models.isFixedCourse

/**
 * 把 0 基的 [Course.weekIndices] 转成人类可读的周次，例如 `1-4、6-9`。
 */
fun formatWeekIndices(weekIndices: List<Int>): String {
    if (weekIndices.isEmpty()) return "未知"
    val sorted = weekIndices.sorted()
    val parts = mutableListOf<String>()
    var start = sorted.first()
    var end = start
    for (week in sorted.drop(1)) {
        if (week == end + 1) {
            end = week
        } else {
            parts += if (start == end) "${start + 1}" else "${start + 1}-${end + 1}"
            start = week
            end = week
        }
    }
    parts += if (start == end) "${start + 1}" else "${start + 1}-${end + 1}"
    return parts.joinToString("、")
}

/**
 * 课程时间描述：
 * - 普通课程：「第 3-4 节 15:00-16:40」（星期几已并入周次行，这里只显示节次与钟点）
 * - 固定课程：「2026-10-01 14:30-16:00」
 *
 * [lessonTimes] 为空或课次越界时只显示节次，不带钟点。
 */
internal fun describeCourseTime(course: Course, lessonTimes: List<LessonTime>): String {
    // 固定课程：绝对日期 + 真实钟点区间（不受课次/作息时间表影响）
    if (course.isFixedCourse) {
        val start = course.fixedStartMinute ?: return "未知"
        val duration = course.fixedDurationMinutes ?: 0
        val date = course.fixedDate ?: return "未知"
        return "$date ${formatClockMinutes(start)}-${formatClockMinutes(start + duration)}"
    }
    if (course.dayIndex !in 0..6 || course.lessonStartIndex < 0) return "未知"
    val start = course.lessonStartIndex + 1
    val end = start + course.lessonCount - 1
    val lessonText = if (course.lessonCount <= 1) "第 $start 节" else "第 $start-$end 节"
    // 钟点：起始课次的开始时间 ~ 最后一课次的结束时间
    val startTime = lessonTimes.getOrNull(course.lessonStartIndex)?.start
    val endTime = lessonTimes.getOrNull(course.lessonStartIndex + course.lessonCount - 1)?.end
    return if (startTime != null && endTime != null) {
        "$lessonText $startTime-$endTime"
    } else {
        lessonText
    }
}
