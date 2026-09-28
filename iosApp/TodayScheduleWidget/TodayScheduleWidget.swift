import WidgetKit
import SwiftUI

/// 「今日课程」Widget Extension 入口（移植自 Pumpkin-Toolkit 的同名 Widget）。
///
/// 支持小 / 中 / 大三档；数据来自 App Group 容器里的 `widget_data.json`
/// （由应用 `WidgetDataHelper` 写入，见 `WidgetHelper.loadWidgetData`）。
@main
struct TodayScheduleWidget: Widget {
    /// 与 App 侧 `WidgetReloader` 的 `reloadTimelines(ofKind:)` 必须一致。
    let kind: String = "TodayScheduleWidget"

    var body: some WidgetConfiguration {
        StaticConfiguration(kind: kind, provider: TodayScheduleProvider()) { entry in
            TodayScheduleWidgetView(entry: entry)
        }
        .configurationDisplayName("今日课程")
        .description("显示今日课程及明日课程")
        .supportedFamilies([.systemSmall, .systemMedium, .systemLarge])
    }
}
