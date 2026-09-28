package com.pgigi.pumpkincampus.models

/**
 * **来源时间表 → 本课表**：把「全局时间表」或「插件推荐时间表」里的表复制一份进当前课表。
 *
 * 「全局时间表」（设置 → 全局课表设置，含内置的「默认作息」）与「插件推荐时间表」
 * （插件包 `timetables.json`）在课表页「上课时间」里都只当**来源**展示：
 * 用户点「使用」才复制一份进本课表，复制品归本课表所有（`fromDefaults = false`），
 * 来源之后怎么改都不影响它；同一张表（同名 + 同节次时间）只会添加一次。
 */

/** 本课表里与 [candidate] 同名同节次的那张表（视为同一张）。 */
internal fun AppSettings.findTimetableLike(candidate: LessonTimetable): LessonTimetable? =
    timetables.firstOrNull { it.name == candidate.name && it.slots == candidate.slots }

/** 这张来源表是否已经在本课表里（同名 + 同节次时间即视为用过）。 */
internal fun AppSettings.hasTimetableLike(candidate: LessonTimetable): Boolean =
    findTimetableLike(candidate) != null

/**
 * 把来源表**复制**成本课表的一张时间表并立即启用。
 *
 * - 已经存在同名同节次的表：只切换启用，不重复添加；
 * - 复制品是本课表自建的（`fromDefaults = false`），来源后续改动不会影响它；
 * - id 冲突时自动加后缀，保证唯一。
 */
internal fun AppSettings.withCopiedTimetable(candidate: LessonTimetable): AppSettings {
    val existing = findTimetableLike(candidate)
    if (existing != null) return copy(activeTimetableId = existing.id)

    val base = candidate.id.ifBlank { "copy" }
    var id = base
    var suffix = 2
    while (timetables.any { it.id == id }) {
        id = "$base-$suffix"
        suffix++
    }
    return copy(
        timetables = timetables + candidate.copy(id = id, fromDefaults = false),
        activeTimetableId = id
    )
}
