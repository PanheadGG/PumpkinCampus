package com.pgigi.pumpkincampus.utils

import com.pgigi.pumpkincampus.constants.FileName
import com.pgigi.pumpkincampus.models.WidgetData
import okio.FileSystem
import okio.Path.Companion.toPath
import platform.Foundation.NSFileManager
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSUserDefaults

/** App Group：应用与 Widget Extension 共享的容器（两端 entitlements 都必须声明）。 */
private const val APP_GROUP = "group.com.pgigi.pumpkincampus"

/** `NSUserDefaults(suiteName:)` 里的兜底 key（容器不可用时仍能读到数据）。 */
private const val WIDGET_DATA_KEY = "widget_data"

private const val WIDGET_DATA_FILE = FileName.WIDGET_DATA

/**
 * Kotlin 端发的数据变化通知名；由 App 侧的 `WidgetReloader` 监听并转成
 * `WidgetCenter.reloadTimelines(ofKind:)`（Widget Extension 自身不链接 Kotlin 框架）。
 */
const val WIDGET_DATA_CHANGED_NOTIFICATION = "com.pgigi.pumpkincampus.widgetDataChanged"

/** App Group 容器目录；未配置 App Group（entitlements 缺失）时返回 null。 */
private fun widgetContainerPath(): String? =
    NSFileManager.defaultManager
        .containerURLForSecurityApplicationGroupIdentifier(APP_GROUP)
        ?.path

private fun parseWidgetData(json: String): WidgetData? = try {
    JsonUtil.parseJson(json, WidgetData.serializer())
} catch (_: Exception) {
    null
}

actual fun saveWidgetData(data: WidgetData) {
    val json = JsonUtil.toJson(data, WidgetData.serializer())

    // 双写：容器文件（Widget Extension 首选读取路径）+ NSUserDefaults（兜底）
    NSUserDefaults(suiteName = APP_GROUP).let { defaults ->
        defaults.setObject(json, forKey = WIDGET_DATA_KEY)
        defaults.synchronize()
    }

    val dir = widgetContainerPath()
    if (dir != null) {
        try {
            FileSystem.SYSTEM.write("$dir/$WIDGET_DATA_FILE".toPath()) {
                write(json.encodeToByteArray())
            }
        } catch (_: Exception) {
            // 容器不可写时只保留 NSUserDefaults 里的副本
        }
    }
}

actual fun loadWidgetData(): WidgetData? {
    val dir = widgetContainerPath()
    if (dir != null) {
        val path = "$dir/$WIDGET_DATA_FILE".toPath()
        if (FileSystem.SYSTEM.exists(path)) {
            val json = try {
                FileSystem.SYSTEM.read(path) { readUtf8() }
            } catch (_: Exception) {
                null
            }
            if (json != null) {
                parseWidgetData(json)?.let { return it }
            }
        }
    }

    val json = NSUserDefaults(suiteName = APP_GROUP).stringForKey(WIDGET_DATA_KEY) ?: return null
    return parseWidgetData(json)
}

actual fun reloadWidgetTimelines() {
    NSNotificationCenter.defaultCenter.postNotificationName(
        WIDGET_DATA_CHANGED_NOTIFICATION,
        `object` = null
    )
}
