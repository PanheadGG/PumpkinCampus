package com.pgigi.pumpkincampus.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * 「今日课程」小组件的 AppWidget 宿主（移植自 Pumpkin-Toolkit 的同名 Receiver）。
 *
 * 应用侧写完 `widget_data.json` 后会向本 Receiver 广播 `APPWIDGET_UPDATE`
 * （见 `reloadWidgetTimelines`），Glance 收到后重新渲染；系统也会按
 * `today_schedule_widget_info.xml` 的 `updatePeriodMillis` 定时刷新。
 */
class TodayScheduleWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayScheduleWidget()
}
