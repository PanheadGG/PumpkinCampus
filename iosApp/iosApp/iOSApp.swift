import SwiftUI

@main
struct iOSApp: App {

    // 初始化 WidgetReloader，监听 Kotlin 端数据变化通知并刷新「今日课程」小组件
    private let widgetReloader = WidgetReloader.shared

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}