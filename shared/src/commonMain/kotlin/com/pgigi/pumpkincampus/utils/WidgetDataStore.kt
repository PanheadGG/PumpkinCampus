package com.pgigi.pumpkincampus.utils

import com.pgigi.pumpkincampus.models.WidgetData

/**
 * 桌面小组件数据存取（移植自 Pumpkin-Toolkit 的 `WidgetDataStore`，
 * 按本项目的习惯写成 `expect fun`，与 [FileStoreUtils]、[createPluginVault] 一致）。
 *
 * - **Android**：写到应用私有目录 `filesDir/widget_data.json`
 *   （Glance 小组件渲染时按 `filesDir` 直读，见 [loadWidgetData] 的 Context 重载）
 * - **iOS**：写到 App Group 容器 `group.com.pgigi.pumpkincampus` 下的同名文件 +
 *   `NSUserDefaults(suiteName:)` 双写兜底，供 Widget Extension 读取
 */
expect fun saveWidgetData(data: WidgetData)

/**
 * 读取小组件数据（应用进程内使用，依赖各平台的全局上下文/容器）；
 * 文件不存在或解析失败返回 null。
 *
 * 小组件渲染时不走这里（AppWidget 进程没有经过 `MainActivity`），
 * 用 Android 侧带 `Context` 的重载。
 */
expect fun loadWidgetData(): WidgetData?

/**
 * 通知小组件刷新（应用侧在 [saveWidgetData] 之后调用）。
 *
 * - Android：向 `TodayScheduleWidgetReceiver` 广播 `APPWIDGET_UPDATE`
 * - iOS：在 `NSNotificationCenter` 上发通知，由 App 侧的 `WidgetReloader`
 *   转成 `WidgetCenter.reloadTimelines(ofKind:)`
 */
expect fun reloadWidgetTimelines()
