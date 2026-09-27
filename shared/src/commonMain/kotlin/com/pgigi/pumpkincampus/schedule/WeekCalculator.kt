package com.pgigi.pumpkincampus.schedule

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/**
 * 教学周计算器（一周的第一天可配置，默认周日）。
 *
 * 核心规则：
 * 1. [baseDate] 所在周（以 [firstDayOfWeek] 为该周第一天）为第 [baseWeek] 教学周
 * 2. 第 1 周之前的日期得到 0 或负数周次
 * 3. 每周的第一天 = 所选每周第一天（0=周日 ... 6=周六）：
 *    选周日则以第一个周日为起始，选周三则每周从周三开始（课表首列也随之改变）
 *
 * @param baseDate 基准日期（属于基准周；作为「第一周的第一天」时应直接传该起始日）
 * @param baseWeek 基准周对应的周次
 * @param firstDayOfWeek 一周的第一天（0=周日、1=周一 ... 6=周六）
 */
class WeekCalculator(
    baseDate: LocalDate = LocalDate(2026, 3, 1),
    baseWeek: Int = 1,
    firstDayOfWeek: Int = 0
) {
    private val baseStart: LocalDate = startOfWeek(baseDate, firstDayOfWeek)
    private val baseWeekNum: Int = baseWeek
    private val firstDay: Int = ((firstDayOfWeek % 7) + 7) % 7

    /** 计算 [date] 所属的教学周（可为 0 或负数）。 */
    fun getWeekNumber(date: LocalDate): Int =
        (startOfWeek(date, firstDay).toEpochDays() - baseStart.toEpochDays()).toInt() / 7 +
            baseWeekNum

    /** 查询第 [weekNum] 教学周的第一天（即所选每周第一天）。 */
    fun getWeekFirstDay(weekNum: Int): LocalDate =
        baseStart.plus((weekNum - baseWeekNum) * 7, DateTimeUnit.DAY)

    /** [date] 所在周、以 [firstDay] 为第一天的那一周的第一天。 */
    private fun startOfWeek(date: LocalDate, firstDay: Int): LocalDate {
        val offset = (dayIndexOf(date) - firstDay + 7) % 7
        return if (offset == 0) date else date.minus(offset, DateTimeUnit.DAY)
    }
}

/**
 * 依据设置构造教学周计算器：
 * - 设置了 [termStart]（第一周的第一天）：以其为第 1 周起点，
 *   且**该日期的星期就是每周的第一天**（选周日 → 周日起始；选周三 → 周三起始）
 * - 未设置：默认「今天所在周往前 3 周、周日起始」
 */
fun weekCalculatorOf(termStart: String?): WeekCalculator {
    val anchor = termStart?.let { parseFixedDate(it) }
        ?: defaultTermStart()
    return WeekCalculator(
        baseDate = anchor,
        baseWeek = 1,
        firstDayOfWeek = dayIndexOf(anchor)
    )
}

/** 默认起始日：今天所在周（周日起始）往前 3 周的周日。 */
internal fun defaultTermStart(): LocalDate {
    val today = currentLocalDate()
    return today.minus(dayIndexOf(today), DateTimeUnit.DAY)
        .minus(21, DateTimeUnit.DAY)
}

/**
 * 日期在一周中的序号：0=周日、1=周一 ... 6=周六。
 *
 * 基于 epoch day 计算（1970-01-01 为周四），不依赖 DayOfWeek 的平台差异。
 */
internal fun dayIndexOf(date: LocalDate): Int {
    val epoch = date.toEpochDays()
    // 周四对应 4（周日为 0）
    return (((epoch + 4) % 7 + 7) % 7).toInt()
}
