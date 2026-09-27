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
 *
 * @property fromDefaults 是否**来自「设置 → 全局课表设置」的默认时间表**：
 *   新建课表时默认时间表会被快照进该课表并打上此标记，用于在单课表页把
 *   「默认时间表」与本课表自建的区分开。标记只是来源说明，之后两边互不影响。
 */
@Serializable
data class LessonTimetable(
    val id: String = "",
    val name: String = "",
    val slots: List<LessonTime> = emptyList(),
    val fromDefaults: Boolean = false
)

/**
 * 「设置 → 全局课表设置」里的**新建课表默认值**。
 *
 * 只是新建课表时的**初值快照**：改这里不会影响任何已建立的课表，
 * 已建立的课表在课表页「⋯ → 课表设置」里各自维护（见 [newScheduleSettings]）。
 *
 * 导入/分享**不读**这里——信封自带分享方的课表专属配置时按信封来。
 */
@Serializable
data class ScheduleDefaults(
    /** 默认上课时间表（可以有多张，最少保留一张）。 */
    val timetables: List<LessonTimetable> = emptyList(),
    /** 默认启用哪张时间表；null = 新建课表先用内置默认作息。 */
    val activeTimetableId: String? = null,
    /** 默认一天课程节数。 */
    val lessonCount: Int = 10,
    /** 默认学期周数。 */
    val semesterWeekCount: Int = 18,
    /** 默认显示替换规则。 */
    val replaces: List<DisplayReplace> = emptyList()
) {

    /**
     * 新建课表的初始设置：默认时间表**快照**进新课表并标记 [LessonTimetable.fromDefaults]，
     * 节数/周数/显示替换照抄。
     *
     * 全局显示项（颜色模式、辅助线、课表外观）不在这里——它们始终读全局值。
     */
    fun newScheduleSettings(): AppSettings = AppSettings(
        timetables = timetables.map { it.copy(fromDefaults = true) },
        activeTimetableId = activeTimetableId,
        lessonCount = lessonCount,
        semesterWeekCount = semesterWeekCount,
        replaces = replaces
    )

    /**
     * 交给「上课时间」页面编辑用的视图。
     *
     * 该页面按 [AppSettings] 组织（课表专属字段），这里只填课表专属字段，
     * 编辑完用 [from] 取回默认值部分。
     */
    fun asEditableSettings(): AppSettings = AppSettings(
        timetables = timetables,
        activeTimetableId = activeTimetableId,
        lessonCount = lessonCount,
        semesterWeekCount = semesterWeekCount,
        replaces = replaces
    )

    companion object {
        /** 从页面改回来的设置里取回默认值字段（只取课表专属部分）。 */
        fun from(settings: AppSettings): ScheduleDefaults = ScheduleDefaults(
            timetables = settings.timetables,
            activeTimetableId = settings.activeTimetableId,
            lessonCount = settings.lessonCount,
            semesterWeekCount = settings.semesterWeekCount,
            replaces = settings.replaces
        )
    }
}

/**
 * 应用设置（`settings.json`，由 HomeScreen 用 FileStoreUtils + JsonUtil 持久化）。
 *
 * **两类字段，作用域不同**（见 [withGlobalDisplay] / [forScheduleExport]）：
 * - **全局显示项**：颜色模式、子标题课表名、辅助线、课表外观参数（单元格高度 / 老师 /
 *   地点 / 「@」）——只读 `settings.json` 的值，**所有课表共用**，不随课表快照变化；
 *   在「设置 → 外观」里调整，导出/分享时被剥离。
 * - **课表专属项**：第一周第一天、上课时间（时间表）、一天课程节数、学期周数、
 *   显示替换——按课表保存（[com.pgigi.pumpkincampus.models.CourseSchedule.settings]），
 *   随导出信封一起分享。
 */
@Serializable
data class AppSettings(
    /** 颜色模式：`system` 跟随系统（默认） / `light` 浅色模式 / `dark` 深色模式。**全局**。 */
    val colorMode: String = "system",
    /**
     * 课表页**子标题**（周次信息，如「第3周 周三」）后面是否追加当前课表名称。
     *
     * **全局**显示开关（设置 → 外观）：始终读取全局值，不随课表专属设置快照变化，
     * 多课表切换时保持一致。
     */
    val showScheduleNameInSubtitle: Boolean = true,
    /** 第一周第一天（学期起始日，yyyy-MM-dd，必须是所选每周第一天）；null 用默认锚点。**课表专属**。 */
    val termStart: String? = null,
    /** 课表单元格网格辅助线。**全局**（设置 → 外观，所有课表共用）。 */
    val showGridLines: Boolean = false,
    /** 课表单元格高度（dp，50..100，步长 10）。**全局**（课表外观页）。 */
    val cellHeightDp: Int = 70,
    /** 课表格子内是否显示授课老师。**全局**（课表外观页）。 */
    val showTeacher: Boolean = true,
    /** 课表格子内是否显示上课地点。**全局**（课表外观页）。 */
    val showClassroom: Boolean = true,
    /** 地点前是否加「@」。**全局**（课表外观页）。 */
    val classroomAtPrefix: Boolean = true,
    /** 展示层字符串替换规则（「显示替换」）。**课表专属**：不同课表可以有不同的替换。 */
    val replaces: List<DisplayReplace> = emptyList(),
    /** 候选时间表；为空时使用内置默认作息（当前课表时间）。**课表专属**。 */
    val timetables: List<LessonTimetable> = emptyList(),
    /** 当前启用的时间表 id；null = 使用内置默认作息。**课表专属**。 */
    val activeTimetableId: String? = null,
    /** 一天课程节数（课表显示的行数；时间表多余节次忽略）。**课表专属**。 */
    val lessonCount: Int = 10,
    /** 学期周数（教学周总数）。**课表专属**。 */
    val semesterWeekCount: Int = 18,
    /**
     * 「设置 → 全局课表设置」的**新建课表默认值**。
     *
     * **全局**：只作为新建课表时的初值，改它不影响已建立的课表；
     * 导出/分享时被剥离（[forScheduleExport]），导入方不会拿到别人的默认值。
     */
    val defaults: ScheduleDefaults = ScheduleDefaults()
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

    /**
     * 套用**全局显示项**：本设置（课表专属设置或导入信封里的设置）里的全局字段
     * （颜色模式、子标题课表名、辅助线、课表外观参数）一律以 [global] 为准。
     *
     * 展示课表时统一走这一步——多课表共用同一套外观，切课表不会跳变。
     */
    fun withGlobalDisplay(global: AppSettings): AppSettings = copy(
        colorMode = global.colorMode,
        showScheduleNameInSubtitle = global.showScheduleNameInSubtitle,
        showGridLines = global.showGridLines,
        cellHeightDp = global.cellHeightDp,
        showTeacher = global.showTeacher,
        showClassroom = global.showClassroom,
        classroomAtPrefix = global.classroomAtPrefix
    )

    /**
     * 只保留**课表专属项**（导出/分享用）：全局显示项回落内置默认值，
     * 接收方导入后沿用**自己的**辅助线与课表外观，不会被分享方的设置覆盖。
     *
     * 课表专属项（第一周第一天、上课时间、一天课程节数、学期周数、显示替换）原样带走；
     * 全局的新建课表默认值（[defaults]）一并剥离。
     */
    fun forScheduleExport(): AppSettings =
        withGlobalDisplay(AppSettings()).copy(defaults = ScheduleDefaults())
}
