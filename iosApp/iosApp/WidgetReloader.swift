import Foundation
import WidgetKit

/// 监听 Kotlin 端（`reloadWidgetTimelines()`）发来的数据变化通知，刷新小组件时间线。
///
/// 通知名必须与 `WidgetDataStore.ios.kt` 的 `WIDGET_DATA_CHANGED_NOTIFICATION` 一致；
/// `widgetKind` 与 `TodayScheduleWidget.swift` 的 `kind` 一致。
@objc final class WidgetReloader: NSObject {

    static let shared = WidgetReloader()

    private let widgetKind = "TodayScheduleWidget"
    private let notificationName = Notification.Name("com.pgigi.pumpkincampus.widgetDataChanged")

    private override init() {
        super.init()
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(handleWidgetDataChanged),
            name: notificationName,
            object: nil
        )
    }

    deinit {
        NotificationCenter.default.removeObserver(self)
    }

    @objc private func handleWidgetDataChanged() {
        WidgetCenter.shared.reloadTimelines(ofKind: widgetKind)
    }
}
