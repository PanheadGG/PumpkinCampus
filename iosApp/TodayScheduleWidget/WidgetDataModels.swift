import Foundation

/// 小组件数据（`widget_data.json`，由 Kotlin 端 `WidgetDataHelper` 写入）。
///
/// 存的是「原料」：课表、作息、学期周数。今天/明天要显示什么，由 Widget 在
/// **每次生成时间线时**自己算（见 `WidgetHelper`），所以课程下课、跨零点都会自动更新。
///
/// 所有字段都用 `decodeIfPresent` 兜底：Kotlin 端将来加字段或旧数据缺字段都不会崩。
struct WidgetData: Codable {
    let version: Int
    let scheduleName: String
    let termStart: String?
    let totalWeek: Int
    let lessonTimes: [WidgetScheduleTime]
    let courses: [WidgetCourseItem]
    let updateTime: Int64

    enum CodingKeys: String, CodingKey {
        case version, scheduleName, termStart, totalWeek, lessonTimes, courses, updateTime
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        version = (try? container.decode(Int.self, forKey: .version)) ?? 1
        scheduleName = (try? container.decode(String.self, forKey: .scheduleName)) ?? ""
        termStart = (try? container.decodeIfPresent(String.self, forKey: .termStart)) ?? nil
        totalWeek = (try? container.decode(Int.self, forKey: .totalWeek)) ?? 0
        lessonTimes = (try? container.decode([WidgetScheduleTime].self, forKey: .lessonTimes)) ?? []
        courses = (try? container.decode([WidgetCourseItem].self, forKey: .courses)) ?? []
        updateTime = (try? container.decode(Int64.self, forKey: .updateTime)) ?? 0
    }

    func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encode(version, forKey: .version)
        try container.encode(scheduleName, forKey: .scheduleName)
        try container.encodeIfPresent(termStart, forKey: .termStart)
        try container.encode(totalWeek, forKey: .totalWeek)
        try container.encode(lessonTimes, forKey: .lessonTimes)
        try container.encode(courses, forKey: .courses)
        try container.encode(updateTime, forKey: .updateTime)
    }
}

/// 一节课的起止钟点。
struct WidgetScheduleTime: Codable {
    let start: String
    let end: String

    enum CodingKeys: String, CodingKey {
        case start, end
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        start = (try? container.decode(String.self, forKey: .start)) ?? ""
        end = (try? container.decode(String.self, forKey: .end)) ?? ""
    }
}

/// 小组件里的单门课程（Kotlin 端 `WidgetCourseItem`）。
///
/// - `weekRanges`：1 基教学周区间，如 `[[1,4],[6,9]]`
/// - `fixedDate` 非空 = 固定课程（绝对日期 + 真实钟点，不受作息时间表影响）
/// - `name` / `classroom` / `teacher` 已是展示用文本（显示替换与「@」前缀已套用）
struct WidgetCourseItem: Codable {
    let name: String
    let classroom: String
    let teacher: String
    let dayIndex: Int
    let lessonStartIndex: Int
    let lessonCount: Int
    let weekRanges: [[Int]]
    let fixedDate: String?
    let fixedStartMinute: Int?
    let fixedDurationMinutes: Int?

    enum CodingKeys: String, CodingKey {
        case name, classroom, teacher, dayIndex, lessonStartIndex, lessonCount
        case weekRanges, fixedDate, fixedStartMinute, fixedDurationMinutes
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        name = (try? container.decode(String.self, forKey: .name)) ?? ""
        classroom = (try? container.decode(String.self, forKey: .classroom)) ?? ""
        teacher = (try? container.decode(String.self, forKey: .teacher)) ?? ""
        dayIndex = (try? container.decode(Int.self, forKey: .dayIndex)) ?? -1
        lessonStartIndex = (try? container.decode(Int.self, forKey: .lessonStartIndex)) ?? -1
        lessonCount = (try? container.decode(Int.self, forKey: .lessonCount)) ?? 2
        weekRanges = (try? container.decode([[Int]].self, forKey: .weekRanges)) ?? []
        fixedDate = (try? container.decodeIfPresent(String.self, forKey: .fixedDate)) ?? nil
        fixedStartMinute = (try? container.decodeIfPresent(Int.self, forKey: .fixedStartMinute)) ?? nil
        fixedDurationMinutes = (try? container.decodeIfPresent(Int.self, forKey: .fixedDurationMinutes)) ?? nil
    }
}

/// 渲染用的课程条目（已算好起止钟点与「今天/明天」分组）。
///
/// 两个标记决定配色（与 Android 端同一套规则）：
/// - `isOngoing`：**正在上课** → 橙色强调
/// - `isTomorrow`：属于「明天」分组 → 整体降为灰色
struct DisplayCourse: Identifiable {
    let id = UUID()
    let name: String
    let classroom: String
    let teacher: String
    let startTime: String
    let endTime: String
    let isTomorrow: Bool
    let isOngoing: Bool
}
