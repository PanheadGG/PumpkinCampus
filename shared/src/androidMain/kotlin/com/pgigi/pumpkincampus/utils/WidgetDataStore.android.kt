package com.pgigi.pumpkincampus.utils

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.pgigi.pumpkincampus.constants.FileName
import com.pgigi.pumpkincampus.models.WidgetData
import java.io.File

/** Android 侧小组件数据文件名（与 [FileName.WIDGET_DATA] 同值）。 */
private const val WIDGET_DATA_FILE = FileName.WIDGET_DATA

/** 桌面小组件 Receiver 的全限定类名（`androidApp` 模块，见 `TodayScheduleWidgetReceiver`）。 */
private const val WIDGET_RECEIVER_CLASS = "com.pgigi.pumpkincampus.widget.TodayScheduleWidgetReceiver"

actual fun saveWidgetData(data: WidgetData) {
    FileStoreUtils.writeString(
        WIDGET_DATA_FILE,
        JsonUtil.toJson(data, WidgetData.serializer())
    )
}

actual fun loadWidgetData(): WidgetData? =
    FileStoreUtils.readString(WIDGET_DATA_FILE)?.let { parseWidgetData(it) }

/**
 * 按小组件自己的 [Context] 读取数据。
 *
 * 小组件进程（`AppWidgetProvider` 广播拉起）**不会经过 `MainActivity`**，
 * 因此不能用依赖 [AppContextHolder] 的 [loadWidgetData]，必须直接用
 * Glance 传进来的 Context 读 `filesDir`（两者指向同一份文件）。
 */
fun loadWidgetData(context: Context): WidgetData? {
    return try {
        val file = File(context.filesDir, WIDGET_DATA_FILE)
        if (file.exists()) parseWidgetData(file.readText()) else null
    } catch (_: Exception) {
        null
    }
}

private fun parseWidgetData(raw: String): WidgetData? = try {
    JsonUtil.parseJson(raw, WidgetData.serializer())
} catch (_: Exception) {
    null
}

/**
 * 小组件渲染入口：读盘 + 按当前日期/时间算出可直接渲染的快照。
 *
 * 返回 null = 应用从未写过数据（小组件显示「请打开应用刷新」）。
 */
fun loadWidgetSnapshot(context: Context): WidgetSnapshot? =
    WidgetDataHelper.resolveSnapshot(loadWidgetData(context))

/**
 * 通知所有「今日课程」小组件刷新：给 Receiver 发一条显式广播。
 *
 * 与 Pumpkin-Toolkit 同样的做法（Glance 的 `GlanceAppWidgetReceiver` 收到广播后
 * 会重新调用 `provideGlance`）；应用还没初始化 Context 或没有小组件实例时静默跳过。
 */
actual fun reloadWidgetTimelines() {
    try {
        val context = AppContextHolder.context
        val manager = AppWidgetManager.getInstance(context)
        val component = ComponentName(context.packageName, WIDGET_RECEIVER_CLASS)
        val ids = manager.getAppWidgetIds(component)
        if (ids.isEmpty()) return
        // 显式广播（带 Component）：不依赖接收方的隐式广播注册
        val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE).apply {
            this.component = component
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
        }
        context.sendBroadcast(intent)
    } catch (_: Exception) {
        // 无小组件实例：无需刷新
    }
}
