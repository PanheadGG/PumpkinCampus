import WidgetKit
import SwiftUI

/// 一条时间线条目：某一时刻小组件要显示的内容（已算好的今天/明天课程）。
struct ScheduleEntry: TimelineEntry {
    let date: Date
    let weekNumber: Int
    let totalWeek: Int
    let isHoliday: Bool
    let dayOfWeekText: String
    let todayCourses: [DisplayCourse]
    let tomorrowCourses: [DisplayCourse]
    let hasData: Bool
}

/// 时间线提供者（移植自 Pumpkin-Toolkit 的 `TodayScheduleProvider`）。
///
/// 生成 **20 条**连续条目：每条都按自己的时刻重算「今天还剩哪些课」，
/// 下一次刷新的时刻取「最近一节下课时间」，没有课后取次日 0 点——
/// 这样课程一节节过去时小组件会自动少一行，不需要应用在前台。
struct TodayScheduleProvider: TimelineProvider {

    /// 小组件库里的预览内容（与 Pumpkin-Toolkit 同一份占位数据）。
    func placeholder(in context: Context) -> ScheduleEntry {
        ScheduleEntry(
            date: Date(),
            weekNumber: 1,
            totalWeek: 20,
            isHoliday: false,
            dayOfWeekText: "周一",
            todayCourses: [
                DisplayCourse(name: "高等数学", classroom: "教学楼A301", teacher: "张老师",
                              startTime: "08:00", endTime: "09:40", isTomorrow: false, isOngoing: true)
            ],
            tomorrowCourses: [],
            hasData: true
        )
    }

    func getSnapshot(in context: Context, completion: @escaping (ScheduleEntry) -> Void) {
        completion(buildEntry(date: Date()))
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<ScheduleEntry>) -> Void) {
        let now = Date()

        guard let data = WidgetHelper.loadWidgetData() else {
            let entry = ScheduleEntry(
                date: now, weekNumber: 0, totalWeek: 0, isHoliday: false,
                dayOfWeekText: "", todayCourses: [], tomorrowCourses: [], hasData: false
            )
            completion(Timeline(entries: [entry], policy: .after(now.addingTimeInterval(3600))))
            return
        }

        var entries: [ScheduleEntry] = []
        var currentDate = now
        let maxEntries = 20
        var count = 0

        while count < maxEntries {
            let entry = buildEntry(date: currentDate, data: data)
            entries.append(entry)

            // 下一次刷新 = 最近一节还没下课的课的下课时间；没有则次日 0 点
            let calendar = Calendar.current
            var nextDate: Date? = nil
            for course in entry.todayCourses {
                if let endTime = WidgetHelper.timeToDate(course.endTime, baseDate: currentDate),
                   endTime > currentDate {
                    if nextDate == nil || endTime < nextDate! {
                        nextDate = endTime
                    }
                }
            }
            if nextDate == nil,
               let tomorrow = calendar.date(byAdding: .day, value: 1, to: currentDate),
               let midnight = calendar.date(bySettingHour: 0, minute: 0, second: 0, of: tomorrow) {
                nextDate = midnight
            }

            guard let next = nextDate, next > currentDate else { break }
            currentDate = next
            count += 1
        }

        if entries.isEmpty {
            entries.append(buildEntry(date: now, data: data))
        }

        // 兜底：无论条目如何，1 小时后再整体重算一次
        completion(Timeline(entries: entries, policy: .after(now.addingTimeInterval(3600))))
    }

    /// 按 `date` 这一刻构建条目。
    private func buildEntry(date: Date, data: WidgetData? = nil) -> ScheduleEntry {
        guard let data = data ?? WidgetHelper.loadWidgetData() else {
            return ScheduleEntry(
                date: date, weekNumber: 0, totalWeek: 0, isHoliday: false,
                dayOfWeekText: "", todayCourses: [], tomorrowCourses: [], hasData: false
            )
        }

        // 锚点按这条时间线的时刻确定一次，与 Kotlin 端「渲染时构造 WeekCalculator」一致
        let week = TeachingWeek(termStart: data.termStart, now: date)
        let weekNumber = week.weekNumber(of: date)
        let isHoliday = week.isHoliday(weekNumber: weekNumber, totalWeek: data.totalWeek)
        let dayIndex = WidgetHelper.dayIndexOf(date)
        let dayOfWeekText = (dayIndex >= 0 && dayIndex <= 6) ? WidgetHelper.weekDayText[dayIndex] : ""

        let todayCourses: [DisplayCourse]
        let tomorrowCourses: [DisplayCourse]
        if isHoliday {
            todayCourses = []
            tomorrowCourses = []
        } else {
            todayCourses = WidgetHelper.remainingCourses(
                data: data, week: week, date: date,
                currentTime: WidgetHelper.currentTimeText(date)
            )
            tomorrowCourses = WidgetHelper.tomorrowCourses(data: data, week: week, today: date)
        }

        return ScheduleEntry(
            date: date, weekNumber: weekNumber, totalWeek: data.totalWeek,
            isHoliday: isHoliday, dayOfWeekText: dayOfWeekText,
            todayCourses: todayCourses, tomorrowCourses: tomorrowCourses,
            hasData: true
        )
    }
}
