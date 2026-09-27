package com.pgigi.pumpkincampus.models

import kotlinx.serialization.Serializable

/** 默认（迁移/兜底用）课表 id 与名称。 */
const val DefaultScheduleId: String = "default"
const val DefaultScheduleName: String = "默认课表"

/**
 * 课表**导出/导入信封**（导入解析以该结构为基础）。
 *
 * 除全部课程外，还携带**课表名称**（[name]）与该课表的**课表专属设置**
 * （第一周第一天、上课时间/作息、学期周数、一天课程节数、显示替换）。
 * 全局显示项（颜色模式、子标题课表名、辅助线、课表外观参数）**不进信封**：
 * 接收方导入后沿用自己 App 里的外观设置，见 [AppSettings.forScheduleExport]。
 *
 * **插件相关约定**：导出/分享时**清除全部插件信息**——插件同步到的课程在导出前
 * 已经并入 [courses]（成为普通自定义课程），信封里既没有插件 id / 配置，
 * 也没有单独的插件课程字段。接收方导入后就是一份可直接编辑的普通课表。
 *
 * 兼容旧形态（课程数组 / `{"courses": [...]}` / 课表存档
 * `{updateTime, courses}`——无设置、无名称，导入时保留目标课表设置）。
 */
@Serializable
data class ScheduleExport(
    val type: String = "pumpkincampus.schedule",
    val version: Int = 1,
    val exportTime: Long = 0L,
    /** 导出课表的名称；旧文件没有该字段时为空。 */
    val name: String = "",
    /** 全部课程：导出时已把插件课程并入（分享方看到的课表 = 接收方导入的课表）。 */
    val courses: List<Course> = emptyList(),
    val settings: AppSettings? = null
)

/**
 * 一次导入的解析结果：课程（必非空）+ 导出时携带的课表设置与名称（可空）。
 *
 * @property name 导出信封里的课表名称；旧格式没有名称时为 `""`
 */
data class ImportedSchedule(
    val courses: List<Course>,
    val settings: AppSettings? = null,
    val name: String = ""
)

/**
 * 一套独立课表（多课表之一）。
 *
 * 每套课表持有**自己的一组自定义课程**：课程只属于所属课表，
 * 不同课表之间互不相通（在 A 课表新增 / 修改 / 删除的课程不会影响 B 课表）。
 *
 * @property settings 该课表的**专属设置**（第一周第一天、上课时间、一天课程节数、
 * 学期周数、显示替换），只对该课表生效；`null` = 尚未定制，回落全局设置。
 * **全局显示项**（颜色模式、子标题课表名、辅助线、课表外观参数）不随课表保存，
 * 展示时统一由 [AppSettings.withGlobalDisplay] 覆盖为本机全局值
 * @property pluginId 该课表选用的教务系统插件 id；`null` = 不使用插件。
 *   插件本身**全局安装**，但每个课表只能选择一个插件，选择关系按课表保存
 * @property pluginConfig 该课表的**插件配置值**（key = manifest.configs 的 `key`（缺省为 `title`），
 *   value 为字符串形式的配置值）。与插件选择一样按课表隔离——不同课表可以为
 *   同一插件保存不同的配置（账号、学期等）；未保存过的项回落 manifest 里的 `default`。
 *   **不含敏感项的值**：密码、token 等只存在 [pluginSecretKeys] 记录的加密存储里
 *   （KVault：Android Keystore / iOS Keychain，见 PluginSecretStore）
 * @property pluginSecretKeys 该课表中**加密保存**的插件配置 key（密码、token 等）。
 *   值本身存在 KVault 里（按「插件 + 课表」隔离），这里只记录 key，
 *   这样即使插件已卸载也能正确展示「已设置」并在删除课表时清理密文
 * @property importedPluginCourses **旧版本遗留字段**：早期分享信封会携带插件课程快照，
 *   导入后存在这里并当作只读层展示。现在分享/导出不再携带插件信息，
 *   读取档案时由 [CourseBook.mergeLegacyImportedPluginCourses] 把这份快照并入
 *   [courses] 并清空该字段（新写入的档案恒为空）
 */
@Serializable
data class CourseSchedule(
    val id: String = "",
    val name: String = "",
    val courses: List<Course> = emptyList(),
    val settings: AppSettings? = null,
    val pluginId: String? = null,
    val pluginConfig: Map<String, String> = emptyMap(),
    val pluginSecretKeys: Set<String> = emptySet(),
    val importedPluginCourses: List<Course> = emptyList()
)

/**
 * `custom-schedule.json` 的多课表档案格式：全部课表 + 当前打开的课表。
 *
 * 旧格式（[ScheduleCache]：`{updateTime, courses}`）在读取时自动迁移为
 * 单个默认课表（[DefaultScheduleId] / [DefaultScheduleName]）。
 */
@Serializable
data class CourseBook(
    /** 当前打开（课表页展示）的课表 id。 */
    val activeScheduleId: String = DefaultScheduleId,
    /** 全部课表（归一化后至少一套）。 */
    val schedules: List<CourseSchedule> = emptyList()
) {

    /** 当前打开的课表；id 失效时回落到第一套。 */
    fun activeSchedule(): CourseSchedule? =
        schedules.firstOrNull { it.id == activeScheduleId } ?: schedules.firstOrNull()

    /** 归一化：保证至少一套课表、activeScheduleId 一定有效。 */
    fun normalized(): CourseBook {
        if (schedules.isEmpty()) {
            return CourseBook(
                activeScheduleId = DefaultScheduleId,
                schedules = listOf(
                    CourseSchedule(id = DefaultScheduleId, name = DefaultScheduleName)
                )
            )
        }
        val validId = activeScheduleId.takeIf { id -> schedules.any { it.id == id } }
            ?: schedules.first().id
        return if (validId == activeScheduleId) this else copy(activeScheduleId = validId)
    }

    /**
     * 迁移旧版本的「分享导入插件层」：早期分享/导出会把插件课程快照单独存进
     * [CourseSchedule.importedPluginCourses]（只读展示）；现在分享/导出直接清除插件信息，
     * 所以读取档案时把这份快照**并入自定义课程**（去重）并清空该字段。
     *
     * @return 迁移后的档案 + 是否发生了变化（`true` 时需要立即写回磁盘）
     */
    fun mergeLegacyImportedPluginCourses(): Pair<CourseBook, Boolean> {
        if (schedules.none { it.importedPluginCourses.isNotEmpty() }) return this to false
        return copy(
            schedules = schedules.map { schedule ->
                if (schedule.importedPluginCourses.isEmpty()) {
                    schedule
                } else {
                    schedule.copy(
                        courses = (schedule.courses + schedule.importedPluginCourses).distinct(),
                        importedPluginCourses = emptyList()
                    )
                }
            }
        ) to true
    }
}
