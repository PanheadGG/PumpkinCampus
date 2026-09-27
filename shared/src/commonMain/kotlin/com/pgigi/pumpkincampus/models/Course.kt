package com.pgigi.pumpkincampus.models

import kotlinx.serialization.Serializable

@Serializable
data class Course(
    val name: String = "", // 课程名称
    val classroom: String = "", // 教室
    val teacher: String = "", // 授课教师
    val weekIndices: List<Int> = emptyList(), // 周索引数组，0=第1教学周，如 0,1,2,3,5,6,7,8 表示第1-4,6-9周
    val dayIndex: Int = -1, // 星期索引 0~6，分别对应周日、一、二、三、四、五、六
    val lessonStartIndex: Int = -1, // 起始课次，0为一天的第一节
    val lessonCount: Int = 2, // 占用课节数量
    // —— 固定课程（按绝对日期 + 钟表时间，不随教学周/作息时间表变化）——
    val fixedDate: String? = null, // 固定日期 "yyyy-MM-dd"；非 null 表示固定课程
    val fixedStartMinute: Int? = null, // 开始时间：当天 0 点起的分钟数（0..1439）
    val fixedDurationMinutes: Int? = null // 持续分钟数
)

/**
 * 是否为固定课程：指定绝对日期与钟表时间。
 *
 * 固定课程的行为：
 * - 只出现在其绝对日期所在的教学周，不按 [Course.weekIndices] 重复（[Course.weekIndices] 为空）
 * - `dayIndex` 由固定日期推导（0=周日 ... 6=周六），绘制在对应星期列
 * - 绘制位置按真实钟点在当天列中定位、高度按持续时长换算，
 *   不受作息时间表（课次时间）调整影响
 */
val Course.isFixedCourse: Boolean
    get() = fixedDate != null && fixedStartMinute != null && fixedDurationMinutes != null
