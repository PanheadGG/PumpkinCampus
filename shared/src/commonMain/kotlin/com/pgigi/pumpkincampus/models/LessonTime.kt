package com.pgigi.pumpkincampus.models

import kotlinx.serialization.Serializable

/** 一节课的时间区间，如 LessonTime("08:00", "08:45") */
@Serializable
data class LessonTime(
    val start: String = "",
    val end: String = ""
)
