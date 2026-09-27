package com.pgigi.pumpkincampus.models

import kotlinx.serialization.Serializable

/**
 * 课表存档（参照 Pumpkin-Toolkit 的 `ScheduleCache`）：
 * `updateTime` 为最近一次写入的毫秒时间戳，`courses` 为完整课程列表。
 *
 * 通过 [com.pgigi.pumpkincampus.utils.FileStoreUtils] +
 * [com.pgigi.pumpkincampus.utils.JsonUtil] 读写为 `schedule.json`。
 */
@Serializable
data class ScheduleCache(
    val updateTime: Long,
    val courses: List<Course>
)
