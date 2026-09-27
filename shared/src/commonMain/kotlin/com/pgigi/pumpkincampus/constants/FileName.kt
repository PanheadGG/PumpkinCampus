package com.pgigi.pumpkincampus.constants

/**
 * 存档文件名（参照 Pumpkin-Toolkit 的 `FileName`）。
 */
object FileName {
    /** 整体课表存档（Pumpkin-Toolkit 同名；作为新安装时的迁移/种子来源，不再写入）。 */
    const val SCHEDULE = "schedule.json"

    /** 用户课表档案：添加、修改、删除课程都写入这里。 */
    const val CUSTOM_SCHEDULE = "custom-schedule.json"

    /** 插件课表档案：按课表保存的插件同步结果（v2 格式，见 PluginScheduleStore）。 */
    const val PLUGIN_SCHEDULE = "plugin-schedule.json"

    /** 应用设置（全局显示项：颜色模式、子标题课表名、辅助线、课表外观等）。 */
    const val SETTINGS = "settings.json"

    /** 已安装插件的根目录（相对数据目录）：`plugins/<插件id>/...`。 */
    const val PLUGIN_DIR = "plugins"

    /** 插件 KV 数据根目录（相对数据目录）：`plugin-kv/<插件id>/<课表id>.json`，按课表隔离。 */
    const val PLUGIN_KV_DIR = "plugin-kv"
}
