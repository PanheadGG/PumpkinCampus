package com.pgigi.pumpkincampus.plugin

/**
 * 课表设置 / 设置页里插件相关的 UI 状态与回调（由 HomeScreen 构建并下发）。
 *
 * 插件**安装/卸载是全局的**（设置页 → 教务系统插件）；
 * **选择与配置是按课表的**（课表设置 → 教务系统插件），同一份状态同时供给两处页面。
 *
 * @property installed 全局已安装插件（含宿主兼容性）
 * @property selectedPluginId 当前课表绑定的插件 id；null = 未选择
 * @property configValues 当前课表保存的配置值（key = manifest configs 的 key）
 * @property scheduleId 当前课表 id
 * @property syncing 当前课表是否正在同步
 * @property entry 当前课表最近一次插件同步结果（课程数 / 时间 / 错误 / 日志）
 * @property installing 是否正在从 zip 安装插件
 * @property message 最近一次安装/卸载结果提示（HomeScreen 统一弹窗）
 * @property pluginCourseCount 当前课表只读层里的插件课程数（插件同步 + 分享导入的快照）
 * @property onConvertAllPluginCourses 把当前课表的全部插件课程复制为自定义课程
 *   （先弹确认：插件课程保留，插件更新课表后可能出现重复课程）
 */
data class PluginUiState(
    val installed: List<InstalledPlugin> = emptyList(),
    val selectedPluginId: String? = null,
    val configValues: Map<String, String> = emptyMap(),
    val scheduleId: String = "",
    val syncing: Boolean = false,
    val entry: PluginScheduleEntry? = null,
    val installing: Boolean = false,
    val message: String? = null,
    val pluginCourseCount: Int = 0,
    val onInstall: (ByteArray) -> Unit = {},
    val onUninstall: (String) -> Unit = {},
    val onDismissMessage: () -> Unit = {},
    val onSelectPlugin: (String?) -> Unit = {},
    val onConfigChange: (Map<String, String>) -> Unit = {},
    val onSyncNow: () -> Unit = {},
    val onConvertAllPluginCourses: () -> Unit = {}
) {
    /** 当前课表选中的已安装插件；未安装/未选择返回 null。 */
    val selected: InstalledPlugin?
        get() = installed.firstOrNull { it.id == selectedPluginId }

    /** 当前选择是否指向一个**已卸载**的插件（需要提示用户重新选择）。 */
    val selectedMissing: Boolean
        get() = !selectedPluginId.isNullOrBlank() && selected == null
}
