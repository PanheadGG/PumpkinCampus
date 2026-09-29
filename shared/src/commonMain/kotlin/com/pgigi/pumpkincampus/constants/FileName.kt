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

    /**
     * 自定义背景图片目录（相对数据目录）：`background/<时间戳>.jpg`。
     *
     * 图片在导入时降采样压缩，配置里只存文件名（见 `BackgroundConfig.image`）。
     */
    const val BACKGROUND_DIR = "background"

    /**
     * 桌面小组件数据（`widget_data.json`）：当前课表的课程 + 生效设置快照。
     *
     * Android 小组件按 `filesDir` 直读、iOS 小组件从 App Group 容器读取，两端都在
     * **渲染时**自行算「今天还剩几节 / 明天几节」，见 `WidgetDataHelper`。
     */
    const val WIDGET_DATA = "widget_data.json"
}
