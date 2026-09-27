package com.pgigi.pumpkincampus.schedule

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pgigi.pumpkincampus.models.LessonTime
import kotlinx.datetime.LocalDate

/**
 * 解析固定日期文本（`yyyy-MM-dd`，与 [LocalDate.toString] 一致）；非法返回 null。
 */
internal fun parseFixedDate(text: String): LocalDate? = try {
    LocalDate.parse(text.trim())
} catch (_: Exception) {
    null
}

/**
 * 解析钟表时间 `HH:mm` 为当天分钟数（0..1439）；非法返回 null。
 */
internal fun parseClockMinutes(text: String): Int? {
    val match = Regex("""^(\d{1,2}):(\d{2})$""").find(text.trim()) ?: return null
    val hour = match.groupValues[1].toInt()
    val minute = match.groupValues[2].toInt()
    if (hour !in 0..23 || minute !in 0..59) return null
    return hour * 60 + minute
}

/**
 * 当天分钟数 → `HH:mm`（超出 24 小时夹到 23:59）。
 */
internal fun formatClockMinutes(minutes: Int): String {
    val value = minutes.coerceIn(0, 1439)
    val hour = value / 60
    val minute = value % 60
    return (if (hour < 10) "0$hour" else "$hour") + ":" + (if (minute < 10) "0$minute" else "$minute")
}

/**
 * 把「当天分钟数」映射为课表格子里的纵向位置（Dp）——固定课程按时间点定位。
 *
 * 规则：
 * - 落在某节课内：在该课次行内按比例定位（与左侧作息时间表对齐；作息调整后仍对应真实钟点）
 * - 落在课间空档：收拢到相邻两行的行边界（行边界即上一节下沿 = 下一节上沿）
 * - 早于/晚于时间表范围：夹到表格顶/底
 * - [lessonTimes] 为空或无法解析时：按 00:00-24:00 线性铺满 [lessonCount] 行
 */
internal fun minutesToY(
    minutes: Int,
    lessonTimes: List<LessonTime>,
    lessonCount: Int,
    cellHeight: Dp
): Dp {
    val totalHeight = cellHeight * lessonCount
    val fallback = totalHeight * (minutes.coerceIn(0, 1440) / 1440f)

    val slots = lessonTimes.mapNotNull { slot ->
        val start = parseClockMinutes(slot.start)
        val end = parseClockMinutes(slot.end)
        if (start != null && end != null && end > start) start to end else null
    }
    if (slots.isEmpty()) return fallback

    if (minutes <= slots.first().first) return 0.dp
    if (minutes >= slots.last().second) return totalHeight

    slots.forEachIndexed { index, (start, end) ->
        if (minutes <= end) {
            return if (minutes >= start) {
                // 课次行内线性定位
                cellHeight * index + cellHeight * ((minutes - start) / (end - start).toFloat())
            } else {
                // 课间空档：收拢到本行顶部（= 上一节的下边界）
                cellHeight * index
            }
        }
    }
    return totalHeight
}
