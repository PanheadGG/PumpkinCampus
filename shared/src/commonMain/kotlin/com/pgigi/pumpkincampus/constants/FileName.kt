package com.pgigi.pumpkincampus.constants

/**
 * 存档文件名（参照 Pumpkin-Toolkit 的 `FileName`）。
 */
object FileName {
    /** 整体课表存档（Pumpkin-Toolkit 同名；作为新安装时的迁移/种子来源，不再写入）。 */
    const val SCHEDULE = "schedule.json"

    /** 用户课表档案：添加、修改、删除课程都写入这里。 */
    const val CUSTOM_SCHEDULE = "custom-schedule.json"

    /** 预留：插件课表，后续接入与插件相关的课表数据源时使用。 */
    const val PLUGIN_SCHEDULE = "plugin-schedule.json"

    /** 应用设置（第一周第一天、课表辅助线、课表外观等）。 */
    const val SETTINGS = "settings.json"
}
