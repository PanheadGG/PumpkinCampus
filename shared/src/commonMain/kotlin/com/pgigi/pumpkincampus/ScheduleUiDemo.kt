package com.pgigi.pumpkincampus

import com.pgigi.pumpkincampus.models.LessonTime

/** 教学周总数（第 1~18 教学周）。 */
internal const val DemoWeekCount = 18

/** 夏秋作息时间表（课程表左侧时间列与详情页钟点换算都使用它）。 */
internal val SampleLessonTimes = listOf(
    LessonTime("08:00", "08:45"),
    LessonTime("08:55", "09:40"),
    LessonTime("10:00", "10:45"),
    LessonTime("10:55", "11:40"),
    LessonTime("15:00", "15:45"),
    LessonTime("15:55", "16:40"),
    LessonTime("17:00", "17:45"),
    LessonTime("17:55", "18:40"),
    LessonTime("20:00", "20:45"),
    LessonTime("20:55", "21:40")
)
