import Foundation

/// 教学周计算器（与 Kotlin 端 `WeekCalculator` / `weekCalculatorOf` 对齐）。
///
/// 锚点（第 1 周的第一天）在**构造时**按「当前时刻」确定一次，之后查询任意日期：
/// - 设置了 `termStart`：该日期即第 1 周起点，且**它的星期就是每周的第一天**
///   （选周三 → 每周从周三开始）
/// - 未设置：默认「今天所在周（周日起始）往前 3 周」，与应用的兜底规则一致
struct TeachingWeek {
    /// 第 1 周的第一天。
    let anchor: Date
    /// 每周的第一天：0=周日 ... 6=周六。
    let firstDay: Int
    let calendar: Calendar

    init(termStart: String?, now: Date, calendar: Calendar = .current) {
        self.calendar = calendar
        let today = calendar.startOfDay(for: now)
        let resolved: Date
        if let termStart = termStart, let parsed = WidgetHelper.parseDate(termStart) {
            resolved = calendar.startOfDay(for: parsed)
        } else {
            let backToSunday = WidgetHelper.dayIndexOf(today, calendar: calendar)
            let sunday = calendar.date(byAdding: .day, value: -backToSunday, to: today) ?? today
            resolved = calendar.date(byAdding: .day, value: -21, to: sunday) ?? sunday
        }
        anchor = resolved
        firstDay = WidgetHelper.dayIndexOf(resolved, calendar: calendar)
    }

    /// 指定日期所属教学周（可为 0 或负数：第 1 周之前）。
    func weekNumber(of date: Date) -> Int {
        let target = calendar.startOfDay(for: date)
        let offset = (WidgetHelper.dayIndexOf(target, calendar: calendar) - firstDay + 7) % 7
        guard let weekStart = calendar.date(byAdding: .day, value: -offset, to: target) else {
            return 0
        }
        let days = calendar.dateComponents([.day], from: anchor, to: weekStart).day ?? 0
        // 与 Kotlin 的 Int 除法一致（向零截断，而非向下取整）
        return days / 7 + 1
    }

    /// 假期判定：教学周不在 `1...totalWeek` 内（周次 ≤ 0 未开学，超出则学期已结束）。
    func isHoliday(weekNumber: Int, totalWeek: Int) -> Bool {
        return weekNumber <= 0 || (totalWeek > 0 && weekNumber > totalWeek)
    }
}

/// 小组件的数据读取与「今天/明天」计算。
///
/// **必须与 Kotlin 端 `WidgetDataHelper` 的逻辑保持一致**（应用内展示与小组件同源）：
/// 课程归属：普通课程看 `dayIndex` + `weekRanges`，固定课程只认绝对日期；
/// 已下课过滤只对「今天」生效。
enum WidgetHelper {

    /// App Group（与 Kotlin 端 `WidgetDataStore.ios.kt`、两端 entitlements 一致）。
    static let appGroup = "group.com.pgigi.pumpkincampus"
    static let widgetDataKey = "widget_data"
    static let widgetDataFile = "widget_data.json"

    static let weekDayText = ["周日", "周一", "周二", "周三", "周四", "周五", "周六"]

    private static let dayFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd"
        formatter.locale = Locale(identifier: "en_US_POSIX")
        return formatter
    }()

    private static let clockFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.dateFormat = "HH:mm"
        formatter.locale = Locale(identifier: "en_US_POSIX")
        return formatter
    }()

    // MARK: - 读取

    /// 读取 App Group 容器里的数据文件；读不到时回落到 `NSUserDefaults(suiteName:)`。
    static func loadWidgetData() -> WidgetData? {
        if let containerURL = FileManager.default
            .containerURL(forSecurityApplicationGroupIdentifier: appGroup) {
            let fileURL = containerURL.appendingPathComponent(widgetDataFile)
            if let jsonData = try? Data(contentsOf: fileURL),
               let data = try? JSONDecoder().decode(WidgetData.self, from: jsonData) {
                return data
            }
        }

        guard let defaults = UserDefaults(suiteName: appGroup),
              let jsonString = defaults.string(forKey: widgetDataKey),
              let jsonData = jsonString.data(using: .utf8) else {
            return nil
        }
        return try? JSONDecoder().decode(WidgetData.self, from: jsonData)
    }

    // MARK: - 日期

    /// 日期在一周中的序号：0=周日 ... 6=周六（与 Kotlin 端 `dayIndexOf` 一致）。
    static func dayIndexOf(_ date: Date, calendar: Calendar = .current) -> Int {
        // Calendar 的 weekday 恒为 1=周日 ... 7=周六（与 firstWeekday 设置无关）
        return calendar.component(.weekday, from: date) - 1
    }

    /// `yyyy-MM-dd` → 日期。
    static func parseDate(_ text: String) -> Date? {
        return dayFormatter.date(from: text.trimmingCharacters(in: .whitespaces))
    }

    /// 日期 → `yyyy-MM-dd`（与 Kotlin 端 `LocalDate.toString()` 一致）。
    static func dateText(_ date: Date) -> String {
        return dayFormatter.string(from: date)
    }

    // MARK: - 课程

    /// 「今天还没下课」的课程（`currentTime` 取自同一条时间线的时刻）。
    static func remainingCourses(data: WidgetData, week: TeachingWeek, date: Date, currentTime: String) -> [DisplayCourse] {
        return courses(data: data, week: week, on: date, isTomorrow: false, currentTime: currentTime)
    }

    /// 明天的课程。
    static func tomorrowCourses(data: WidgetData, week: TeachingWeek, today: Date) -> [DisplayCourse] {
        let calendar = Calendar.current
        guard let tomorrow = calendar.date(byAdding: .day, value: 1, to: today) else { return [] }
        return courses(data: data, week: week, on: tomorrow, isTomorrow: true, currentTime: nil)
    }

    /// 某一天要显示的课程：筛选 → 按开始时间排序 → 拼起止钟点 →（今天）过滤已下课。
    static func courses(
        data: WidgetData,
        week: TeachingWeek,
        on date: Date,
        isTomorrow: Bool,
        currentTime: String?
    ) -> [DisplayCourse] {
        let weekNumber = week.weekNumber(of: date)
        if weekNumber <= 0 { return [] }

        let dayIndex = dayIndexOf(date)
        let text = dateText(date)

        return data.courses
            .filter { occursOn($0, dateText: text, dayIndex: dayIndex, weekNumber: weekNumber) }
            .sorted { startMinute($0, lessonTimes: data.lessonTimes) < startMinute($1, lessonTimes: data.lessonTimes) }
            .compactMap {
                displayCourse($0, lessonTimes: data.lessonTimes, currentTime: currentTime, isTomorrow: isTomorrow)
            }
    }

    /// 这门课是否出现在指定日期：固定课程比绝对日期，普通课程比星期 + 教学周区间。
    static func occursOn(_ course: WidgetCourseItem, dateText: String, dayIndex: Int, weekNumber: Int) -> Bool {
        if let fixedDate = course.fixedDate {
            return fixedDate == dateText
        }
        guard course.dayIndex == dayIndex else { return false }
        return course.weekRanges.contains { $0.count >= 2 && $0[0] <= weekNumber && $0[1] >= weekNumber }
    }

    /// 当天排序用的开始分钟数：固定课程用真实钟点，普通课程查作息时间表。
    static func startMinute(_ course: WidgetCourseItem, lessonTimes: [WidgetScheduleTime]) -> Int {
        if course.fixedDate != nil {
            return course.fixedStartMinute ?? 0
        }
        let index = course.lessonStartIndex
        guard index >= 0, index < lessonTimes.count,
              let minutes = parseClockMinutes(lessonTimes[index].start) else {
            return max(0, index) * 100
        }
        return minutes
    }

    /// 转成展示项；已经下课（结束钟点早于 `currentTime`）返回 nil。
    static func displayCourse(
        _ course: WidgetCourseItem,
        lessonTimes: [WidgetScheduleTime],
        currentTime: String?,
        isTomorrow: Bool
    ) -> DisplayCourse? {
        let startText: String
        let endText: String

        if course.fixedDate != nil, let fixedStart = course.fixedStartMinute {
            startText = formatClockMinutes(fixedStart)
            endText = formatClockMinutes(fixedStart + (course.fixedDurationMinutes ?? 0))
        } else {
            guard !lessonTimes.isEmpty else { return nil }
            let startIndex = min(max(course.lessonStartIndex, 0), lessonTimes.count - 1)
            let endIndex = min(max(startIndex + course.lessonCount - 1, 0), lessonTimes.count - 1)
            startText = normalizeClock(lessonTimes[startIndex].start)
            endText = normalizeClock(lessonTimes[endIndex].end)
        }

        if let currentTime = currentTime, endText < currentTime { return nil }

        return DisplayCourse(
            name: course.name,
            classroom: course.classroom,
            teacher: course.teacher,
            startTime: startText,
            endTime: endText,
            isTomorrow: isTomorrow,
            // 「正在上课」= 已开始且未结束（结束时刻当分钟仍算进行中，与 Kotlin 端一致）
            isOngoing: currentTime.map { startText <= $0 && $0 <= endText } ?? false
        )
    }

    // MARK: - 钟点

    /// `HH:mm` 字符串 → 当天分钟数；非法返回 nil。
    static func parseClockMinutes(_ text: String) -> Int? {
        let parts = text.trimmingCharacters(in: .whitespaces).split(separator: ":")
        guard parts.count == 2,
              let hour = Int(parts[0]), let minute = Int(parts[1]),
              hour >= 0, hour <= 23, minute >= 0, minute <= 59 else {
            return nil
        }
        return hour * 60 + minute
    }

    /// 当天分钟数 → `HH:mm`（超出 24 小时夹到 23:59）。
    static func formatClockMinutes(_ minutes: Int) -> String {
        let value = min(max(minutes, 0), 1439)
        return String(format: "%02d:%02d", value / 60, value % 60)
    }

    /// 作息表里的钟点可能是 `8:00` 这类写法，统一成可比较的 `08:00`。
    static func normalizeClock(_ text: String) -> String {
        guard let minutes = parseClockMinutes(text) else { return text }
        return formatClockMinutes(minutes)
    }

    /// 当前钟点 `HH:mm`。
    static func currentTimeText(_ date: Date) -> String {
        return clockFormatter.string(from: date)
    }

    /// `HH:mm` 在某一天的时间点（推算出下一次需要刷新的时刻）。
    static func timeToDate(_ timeText: String, baseDate: Date) -> Date? {
        guard let minutes = parseClockMinutes(timeText) else { return nil }
        let calendar = Calendar.current
        let startOfDay = calendar.startOfDay(for: baseDate)
        return calendar.date(byAdding: .minute, value: minutes, to: startOfDay)
    }
}
