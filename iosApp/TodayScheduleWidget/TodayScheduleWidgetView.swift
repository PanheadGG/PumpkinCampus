import SwiftUI
import WidgetKit

/// 小组件界面（移植自 Pumpkin-Toolkit 的 `TodayScheduleWidgetView`）。
///
/// 三档布局，内容一律顶对齐：
/// - 小：今天第一节 + 底部提示（今天还剩几节 / 明天有几节）
/// - 中：最多 2 行课程（0 节今天 → 明日课程；1 节今天 → 今天 1 + 明日 1）
/// - 大：最多 6 行课程（先满足今天，明天至少留 1 行）
struct TodayScheduleWidgetView: View {
    let entry: ScheduleEntry
    @Environment(\.widgetFamily) var family
    @Environment(\.colorScheme) var colorScheme

    private var backgroundGradient: LinearGradient {
        LinearGradient(
            colors: colorScheme == .dark
                ? [Color(red: 0.10, green: 0.10, blue: 0.12), Color(red: 0.06, green: 0.06, blue: 0.08)]
                : [Color(red: 0.97, green: 0.97, blue: 0.98), Color(red: 0.92, green: 0.92, blue: 0.95)],
            startPoint: .topLeading,
            endPoint: .bottomTrailing
        )
    }

    var body: some View {
        contentView
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
            .containerBackground(for: .widget) { backgroundGradient }
    }

    @ViewBuilder
    private var contentView: some View {
        if !entry.hasData {
            NoDataView()
        } else if entry.isHoliday {
            HolidayView(dayOfWeekText: entry.dayOfWeekText)
        } else {
            switch family {
            case .systemSmall:
                SmallScheduleView(entry: entry)
            case .systemMedium:
                MediumScheduleView(entry: entry)
            default:
                LargeScheduleView(entry: entry)
            }
        }
    }
}

// MARK: - Header

private struct HeaderView: View {
    let weekNumber: Int
    let dayOfWeekText: String
    let isHoliday: Bool

    var body: some View {
        HStack {
            if !isHoliday && weekNumber > 0 {
                Text("第\(weekNumber)周")
                    .font(.system(size: 13, weight: .medium))
                    .foregroundStyle(.secondary)
            }
            Spacer()
            Text(dayOfWeekText)
                .font(.system(size: 13, weight: .medium))
                .foregroundStyle(.secondary)
        }
    }
}

// MARK: - Course Row

/// 课程名配色：**正在进行 → 橙色**，明日 → 灰色，今天其它 → 常规文字色。
private func nameColor(of course: DisplayCourse) -> Color {
    if course.isOngoing { return .orange }
    if course.isTomorrow { return .secondary }
    return .primary
}

/// 时间/地点/老师配色：正在进行的课跟着橙色，其余一律灰色。
private func detailColor(of course: DisplayCourse) -> Color {
    course.isOngoing ? .orange : .secondary
}

private struct CourseRowView: View {
    let course: DisplayCourse

    var body: some View {
        HStack(spacing: 0) {
            VStack(alignment: .leading, spacing: 2) {
                Text(course.name)
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(nameColor(of: course))
                    .lineLimit(1)
                    .minimumScaleFactor(0.8)
                HStack(spacing: 5) {
                    Text("\(course.startTime)-\(course.endTime)")
                    if !course.classroom.isEmpty {
                        Text("·")
                        Text(course.classroom)
                    }
                }
                .font(.system(size: 11))
                .foregroundStyle(detailColor(of: course))
                .lineLimit(1)
                .minimumScaleFactor(0.8)
            }
            Spacer(minLength: 8)
            if !course.teacher.isEmpty {
                Text(course.teacher)
                    .font(.system(size: 11))
                    .foregroundStyle(detailColor(of: course))
                    .lineLimit(1)
                    .minimumScaleFactor(0.8)
                    .frame(maxWidth: 70, alignment: .trailing)
            }
        }
        .padding(.vertical, 5)
    }
}

private struct SmallCourseRowView: View {
    let course: DisplayCourse

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(course.name)
                .font(.system(size: 14, weight: .semibold))
                .foregroundStyle(nameColor(of: course))
                .lineLimit(1)
                .minimumScaleFactor(0.8)
            Text("\(course.startTime)-\(course.endTime)")
                .font(.system(size: 11))
                .foregroundStyle(detailColor(of: course))
            if !course.classroom.isEmpty {
                Text(course.classroom)
                    .font(.system(size: 11))
                    .foregroundStyle(detailColor(of: course))
                    .lineLimit(1)
                    .minimumScaleFactor(0.8)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.vertical, 3)
    }
}

// MARK: - Separators

/// 「明日课程」分隔线：灰色（明日课程本身也降为灰色，不再用橙色）。
private struct TomorrowSeparatorView: View {
    var body: some View {
        HStack(spacing: 8) {
            Rectangle()
                .fill(Color.secondary.opacity(0.4))
                .frame(height: 1)
            Text("明日课程")
                .font(.system(size: 10, weight: .medium))
                .foregroundStyle(.secondary)
            Rectangle()
                .fill(Color.secondary.opacity(0.4))
                .frame(height: 1)
        }
        .padding(.vertical, 4)
    }
}

private struct CourseDivider: View {
    var body: some View {
        Divider()
            .opacity(0.25)
    }
}

// MARK: - Empty States

private struct NoDataView: View {
    var body: some View {
        VStack {
            Spacer()
            Text("请打开应用刷新")
                .font(.system(size: 14))
                .foregroundStyle(.tertiary)
            Spacer()
        }
        .frame(maxHeight: .infinity)
    }
}

private struct HolidayView: View {
    let dayOfWeekText: String
    var body: some View {
        VStack(spacing: 8) {
            HStack {
                Spacer()
                Text(dayOfWeekText)
                    .font(.system(size: 13, weight: .medium))
                    .foregroundStyle(.secondary)
            }
            Spacer()
            Text("假期中")
                .font(.system(size: 18, weight: .medium))
                .foregroundStyle(.tertiary)
            Spacer()
        }
        .frame(maxHeight: .infinity)
    }
}

// MARK: - Small Widget

private struct SmallScheduleView: View {
    let entry: ScheduleEntry

    var body: some View {
        VStack(spacing: 0) {
            HeaderView(weekNumber: entry.weekNumber, dayOfWeekText: entry.dayOfWeekText, isHoliday: entry.isHoliday)
                .padding(.bottom, 6)

            if entry.todayCourses.isEmpty && entry.tomorrowCourses.isEmpty {
                Spacer()
                Text("今日无课")
                    .font(.system(size: 15, weight: .medium))
                    .foregroundStyle(.tertiary)
                Spacer()
            } else if entry.todayCourses.isEmpty {
                // 今天没课：明日课程 + 第一节
                Text("明日课程")
                    .font(.system(size: 11, weight: .medium))
                    .foregroundStyle(.secondary)
                    .padding(.bottom, 4)
                if let first = entry.tomorrowCourses.first {
                    SmallCourseRowView(course: first)
                }
                Spacer(minLength: 0)
            } else {
                if let first = entry.todayCourses.first {
                    SmallCourseRowView(course: first)
                }
                Spacer(minLength: 0)
                // 底部提示：今天还剩几节 + 明天有几节
                let remainingToday = entry.todayCourses.count - 1
                VStack(alignment: .leading, spacing: 2) {
                    if remainingToday > 0 {
                        Label("今日剩 \(remainingToday) 节", systemImage: "chevron.down.circle.fill")
                            .font(.system(size: 10, weight: .medium))
                            .foregroundStyle(.secondary)
                    }
                    if !entry.tomorrowCourses.isEmpty {
                        Label("明日有 \(entry.tomorrowCourses.count) 节", systemImage: "sun.max.fill")
                            .font(.system(size: 10, weight: .medium))
                            .foregroundStyle(.secondary)
                    }
                }
                .padding(.top, 4)
            }
        }
        .frame(maxHeight: .infinity, alignment: .top)
    }
}

// MARK: - Medium Widget

private struct MediumScheduleView: View {
    let entry: ScheduleEntry

    var body: some View {
        VStack(spacing: 0) {
            HeaderView(weekNumber: entry.weekNumber, dayOfWeekText: entry.dayOfWeekText, isHoliday: entry.isHoliday)
                .padding(.bottom, 6)

            if entry.todayCourses.isEmpty && entry.tomorrowCourses.isEmpty {
                Spacer()
                Text("今日无课")
                    .font(.system(size: 15, weight: .medium))
                    .foregroundStyle(.tertiary)
                Spacer()
            } else if entry.todayCourses.isEmpty {
                TomorrowSeparatorView()
                let items = Array(entry.tomorrowCourses.prefix(2))
                ForEach(items) { course in
                    CourseRowView(course: course)
                    if course.id != items.last?.id {
                        CourseDivider()
                    }
                }
            } else if entry.todayCourses.count == 1 && !entry.tomorrowCourses.isEmpty {
                if let course = entry.todayCourses.first {
                    CourseRowView(course: course)
                }
                TomorrowSeparatorView()
                if let course = entry.tomorrowCourses.first {
                    CourseRowView(course: course)
                }
            } else {
                let items = Array(entry.todayCourses.prefix(2))
                ForEach(items) { course in
                    CourseRowView(course: course)
                    if course.id != items.last?.id {
                        CourseDivider()
                    }
                }
            }
        }
        .frame(maxHeight: .infinity, alignment: .top)
    }
}

// MARK: - Large Widget

private struct LargeScheduleView: View {
    let entry: ScheduleEntry

    /// 最多 6 行课程：先满足今天，明天至少留 1 行（两边都有课时）。
    private var courseAllocation: (today: Int, tomorrow: Int) {
        let todayCount = entry.todayCourses.count
        let tomorrowCount = entry.tomorrowCourses.count
        let maxTotal = 6
        if todayCount == 0 {
            return (0, min(tomorrowCount, maxTotal))
        }
        if tomorrowCount == 0 {
            return (min(todayCount, maxTotal), 0)
        }
        let todayShow = min(todayCount, maxTotal - 1)
        let tomorrowShow = min(tomorrowCount, maxTotal - todayShow)
        return (todayShow, tomorrowShow)
    }

    var body: some View {
        VStack(spacing: 0) {
            HeaderView(weekNumber: entry.weekNumber, dayOfWeekText: entry.dayOfWeekText, isHoliday: entry.isHoliday)
                .padding(.bottom, 6)

            if entry.todayCourses.isEmpty && entry.tomorrowCourses.isEmpty {
                Spacer()
                Text("今日无课")
                    .font(.system(size: 16, weight: .medium))
                    .foregroundStyle(.tertiary)
                Spacer()
            } else if entry.todayCourses.isEmpty {
                TomorrowSeparatorView()
                let items = Array(entry.tomorrowCourses.prefix(6))
                ForEach(items) { course in
                    CourseRowView(course: course)
                    if course.id != items.last?.id {
                        CourseDivider()
                    }
                }
            } else {
                let (todayShow, tomorrowShow) = courseAllocation

                if todayShow > 0 {
                    let todayItems = Array(entry.todayCourses.prefix(todayShow))
                    ForEach(todayItems) { course in
                        CourseRowView(course: course)
                        if course.id != todayItems.last?.id {
                            CourseDivider()
                        }
                    }
                }

                if tomorrowShow > 0 {
                    TomorrowSeparatorView()
                    let tomorrowItems = Array(entry.tomorrowCourses.prefix(tomorrowShow))
                    ForEach(tomorrowItems) { course in
                        CourseRowView(course: course)
                        if course.id != tomorrowItems.last?.id {
                            CourseDivider()
                        }
                    }
                }
            }
        }
        .frame(maxHeight: .infinity, alignment: .top)
    }
}
