package com.pgigi.pumpkincampus.models

import com.pgigi.pumpkincampus.SampleLessonTimes
import kotlinx.serialization.Serializable

/**
 * 展示层字符串替换规则：把 [from] 替换为 [to]（用于隐藏校区、简化课程名称等）。
 *
 * **仅作用于展示**（课表格子、日程卡片）；详情页与编辑表单必须显示完整信息，不应用替换。
 */
@Serializable
data class DisplayReplace(
    val from: String = "",
    val to: String = ""
)

/**
 * 候选上课时间表：一节对应一段起止钟点。
 *
 * 节数不受限制——要用多少节就配置多少节；
 * 课表实际显示行数由 [AppSettings.lessonCount]（一天课程节数）决定，多余的节次自动忽略。
 */
@Serializable
data class LessonTimetable(
    val id: String = "",
    val name: String = "",
    val slots: List<LessonTime> = emptyList()
)

/**
 * 应用设置（`settings.json`，由 HomeScreen 用 FileStoreUtils + JsonUtil 持久化）。
 */
@Serializable
data class AppSettings(
    /** 颜色模式：`system` 跟随系统（默认） / `light` 浅色模式 / `dark` 深色模式。 */
    val colorMode: String = "system",
    /** 第一周第一天（学期起始日，yyyy-MM-dd，必须是所选每周第一天）；null 用默认锚点。 */
    val termStart: String? = null,
    /** 课表单元格网格辅助线。 */
    val showGridLines: Boolean = false,
    /** 课表单元格高度（dp，50..100，步长 10）。 */
    val cellHeightDp: Int = 70,
    /** 课表格子内是否显示授课老师。 */
    val showTeacher: Boolean = true,
    /** 课表格子内是否显示上课地点。 */
    val showClassroom: Boolean = true,
    /** 地点前是否加「@」。 */
    val classroomAtPrefix: Boolean = true,
    /** 展示层字符串替换规则。 */
    val replaces: List<DisplayReplace> = emptyList(),
    /** 候选时间表；为空时使用内置默认作息（当前课表时间）。 */
    val timetables: List<LessonTimetable> = emptyList(),
    /** 当前启用的时间表 id；null = 使用内置默认作息。 */
    val activeTimetableId: String? = null,
    /** 一天课程节数（课表显示的行数；时间表多余节次忽略）。 */
    val lessonCount: Int = 10,
    /** 学期周数（教学周总数）。 */
    val semesterWeekCount: Int = 18
) {

    /** 当前启用的时间表；未启用任何时间表返回 null（用内置默认作息）。 */
    fun activeTimetable(): LessonTimetable? {
        val id = activeTimetableId ?: return null
        return timetables.firstOrNull { it.id == id } ?: timetables.firstOrNull()
    }

    /** 当前生效的上课时间：启用的时间表（非空）优先，否则用内置默认作息。 */
    fun activeLessonTimes(): List<LessonTime> =
        activeTimetable()?.slots?.takeIf { it.isNotEmpty() } ?: SampleLessonTimes

    /**
     * 只用于**展示**的字符串处理（课表格子 / 日程卡片）。
     *
     * 详情页、编辑表单等不要调用——那里必须展示完整信息。
     */
    fun display(text: String): String =
        replaces.fold(text) { acc, rule ->
            if (rule.from.isEmpty()) acc else acc.replace(rule.from, rule.to)
        }
}
