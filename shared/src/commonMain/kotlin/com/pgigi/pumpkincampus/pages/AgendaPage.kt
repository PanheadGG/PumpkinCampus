package com.pgigi.pumpkincampus.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.Text
import com.moriafly.salt.ui.UnstableSaltUiApi
import com.moriafly.salt.ui.screen.BasicScreen
import com.pgigi.pumpkincampus.models.AppSettings
import com.pgigi.pumpkincampus.models.Course
import com.pgigi.pumpkincampus.models.LessonTime
import com.pgigi.pumpkincampus.models.isFixedCourse
import com.pgigi.pumpkincampus.schedule.CourseBottomSheet
import com.pgigi.pumpkincampus.schedule.EditCourseSheet
import com.pgigi.pumpkincampus.schedule.WeekCalculator
import com.pgigi.pumpkincampus.schedule.buildCourseListByWeek
import com.pgigi.pumpkincampus.schedule.currentLocalDate
import com.pgigi.pumpkincampus.schedule.dayIndexOf
import com.pgigi.pumpkincampus.schedule.formatClockMinutes
import com.pgigi.pumpkincampus.schedule.parseClockMinutes
import com.pgigi.pumpkincampus.schedule.weekCalculatorOf
import androidx.navigation3.runtime.NavKey
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.number
import kotlinx.datetime.plus
import kotlinx.serialization.Serializable

/**
 * 日程页根导航键（**全局导航**的 tab 根之一，另两个是 [TimetableRootKey] / `SettingsRootKey`）。
 *
 * 三个 tab 根是 `HomeScreen` 里唯一 [com.moriafly.salt.ui.navigation.SaltNavigator]
 * 的 `topLevelRoutes`：点底栏 = `navigate(根键)`，整条返回栈收敛回该 tab 根。
 */
@Serializable
internal data object AgendaRootKey : NavKey

/** 卡片左右分隔的红色竖线颜色。 */
private val AgendaAccentLine = Color(0xFFE53935)

/** 日程时间轴条目：日期标题 / 课程卡片。 */
private sealed interface AgendaItem {
    val key: String

    data class DayHeader(
        val date: LocalDate,
        val isToday: Boolean
    ) : AgendaItem {
        override val key: String get() = "day_$date"
    }

    data class CourseCard(
        val date: LocalDate,
        val course: Course,
        val startTime: String,
        val endTime: String,
        val classroom: String,
        val seq: Int
    ) : AgendaItem {
        override val key: String get() = "card_${date}_${course.name}_$seq"
    }
}

private val DayLabels = listOf("日", "一", "二", "三", "四", "五", "六")

private fun weekDayLabel(date: LocalDate): String = "周${DayLabels[dayIndexOf(date)]}"
/** 卡片排序用的当天开始分钟：固定课程用真实钟点，普通课程用课次开始时间。 */
private fun agendaStartMinute(course: Course, lessonTimes: List<LessonTime>): Int =
    if (course.isFixedCourse) {
        course.fixedStartMinute ?: 0
    } else {
        lessonTimes.getOrNull(course.lessonStartIndex)
            ?.let { parseClockMinutes(it.start) }
            ?: (course.lessonStartIndex.coerceAtLeast(0) * 100)
    }

/**
 * 生成 [from] 起 [dayCount] 天的日程条目；只输出有课的日期，
 * 每个日期下是按开课节次排序的课程卡片。
 */
private fun buildAgendaItems(
    courseListByWeek: List<List<Course>>,
    weekCalculator: WeekCalculator,
    lessonTimes: List<LessonTime>,
    from: LocalDate,
    dayCount: Int,
    today: LocalDate
): List<AgendaItem> {
    val items = mutableListOf<AgendaItem>()
    for (offset in 0 until dayCount) {
        val date = from.plus(offset, DateTimeUnit.DAY)
        val weekIndex = weekCalculator.getWeekNumber(date) - 1
        if (weekIndex !in courseListByWeek.indices) continue

        val dayCourses = courseListByWeek[weekIndex]
            .filter { it.dayIndex == dayIndexOf(date) }
            .sortedBy { agendaStartMinute(it, lessonTimes) }
        if (dayCourses.isEmpty()) continue

        items += AgendaItem.DayHeader(date = date, isToday = date == today)
        dayCourses.forEachIndexed { index, course ->
            // 固定课程用真实钟点区间；普通课程按课次对应的时间表
            val (startText, endText) = if (course.isFixedCourse) {
                val start = course.fixedStartMinute ?: 0
                formatClockMinutes(start) to
                    formatClockMinutes(start + (course.fixedDurationMinutes ?: 0))
            } else {
                val startIndex = course.lessonStartIndex.coerceIn(0, lessonTimes.lastIndex)
                val endIndex = (startIndex + course.lessonCount - 1)
                    .coerceIn(0, lessonTimes.lastIndex)
                lessonTimes[startIndex].start to lessonTimes[endIndex].end
            }
            items += AgendaItem.CourseCard(
                date = date,
                course = course,
                startTime = startText,
                endTime = endText,
                classroom = course.classroom,
                seq = index
            )
        }
    }
    return items
}

/**
 * 日程页：日期标题 + 课程卡片列表（参考 Pumpkin-Toolkit 的 TodayScreen 时间轴，
 * 卡片内部按需求自定义：左侧上开始时间 / 下结束时间，中间 5dp 间距 + 红色竖线，
 * 右侧上课程名 / 下小字老师名，最右侧教室）。
 *
 * 列表从今天一直显示到**本学期最后一天**（学期末按周数计算，超期日期自动跳过）。
 * 点击卡片复用 Material 3 `ModalBottomSheet` 展示课程详情；
 * 详情底部有「编辑课程」入口：[EditCourseSheet] 修改或（二次确认后）删除。
 *
 * @param courses 当前课表的**自定义课程**（由 [HomeScreen] 统一持有，可编辑）
 * @param pluginCourses 当前课表的**插件课程**（只读层，来自教务系统插件同步）：
 *   一并展示；点开详情是只读提示 +「转换为自定义课程」，不提供「编辑课程」
 *   详情只读、需「转换为自定义课程」后才能编辑
 * @param settings 应用设置：第一周第一天（教学周计算器）与展示层字符串替换
 * @param onUpdateCourse 修改课程回调（原课程, 新课程）
 * @param onDeleteCourse 删除课程回调（已二次确认）
 * @param onConvertPluginCourse 插件课程「转换为自定义课程」回调（HomeScreen 弹确认）
 */
@OptIn(UnstableSaltUiApi::class)
@Composable
internal fun AgendaPage(
    courses: List<Course>,
    pluginCourses: List<Course> = emptyList(),
    settings: AppSettings = AppSettings(),
    onUpdateCourse: (Course, Course) -> Unit = { _, _ -> },
    onDeleteCourse: (Course) -> Unit = {},
    onConvertPluginCourse: (Course) -> Unit = {}
) {
    // 展示层 = 自定义课程 + 插件课程；只读判定只认「插件里有、自定义里没有」的课程
    //（转换为自定义课程后两边一致，按可编辑的自定义课程处理）
    val allCourses = remember(courses, pluginCourses) { courses + pluginCourses }
    val isPluginCourse: (Course) -> Boolean = remember(courses, pluginCourses) {
        val custom = courses.toSet()
        val readOnly = pluginCourses.filterNot { it in custom }.toSet()
        // 显式命名局部变量：避免 `{ ... }` 被解析成上一行的尾随 lambda
        val predicate: (Course) -> Boolean = { course -> course in readOnly }
        predicate
    }
    // 教学周计算器：由「第一周的第一天」设置驱动
    val weekCalculator = remember(settings.termStart) { weekCalculatorOf(settings.termStart) }
    // courses 为 SnapshotStateList：以内容作 key（含就地编辑），变化后自动重建
    val courseListByWeek = remember(allCourses, weekCalculator, settings.semesterWeekCount) {
        buildCourseListByWeek(allCourses, settings.semesterWeekCount, weekCalculator)
    }
    val today = remember { currentLocalDate() }
    val agendaItems = remember(allCourses, weekCalculator, today, settings) {
        // 从今天一直排到**本学期最后一天**：从今天所在教学周起算剩余整周（7 天/周），
        // 超出学期范围的日期由 buildAgendaItems 按周次自动跳过
        val dayCount = (
            (settings.semesterWeekCount - weekCalculator.getWeekNumber(today) + 1) * 7
            ).coerceAtLeast(0)
        buildAgendaItems(
            courseListByWeek = courseListByWeek,
            weekCalculator = weekCalculator,
            lessonTimes = settings.activeLessonTimes(),
            from = today,
            dayCount = dayCount,
            today = today
        )
    }
    var selectedCourse by remember { mutableStateOf<Course?>(null) }
    var editingCourse by remember { mutableStateOf<Course?>(null) }

    BasicScreen(
        actionButton = null,
        title = "日程"
    ) { contentPadding ->
        if (agendaItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "近期暂无课程安排",
                    color = SaltTheme.colors.subText
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = SaltTheme.dimens.padding,
                    end = SaltTheme.dimens.padding,
                    top = contentPadding.calculateTopPadding() + 4.dp,
                    bottom = contentPadding.calculateBottomPadding() + 24.dp
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    count = agendaItems.size,
                    key = { agendaItems[it].key }
                ) { index ->
                    when (val item = agendaItems[index]) {
                        is AgendaItem.DayHeader -> AgendaDayHeader(item)
                        is AgendaItem.CourseCard -> AgendaCourseCard(item, settings) {
                            selectedCourse = item.course
                        }
                    }
                }
            }
        }
    }

    selectedCourse?.let { course ->
        CourseBottomSheet(
            course = course,
            onDismissRequest = { selectedCourse = null },
            // 插件课程不提供编辑入口（详情里改为「转换为自定义课程」）
            onEdit = { editingCourse = it },
            lessonTimes = settings.activeLessonTimes(),
            isPluginCourse = isPluginCourse,
            onConvert = { converted ->
                selectedCourse = null
                onConvertPluginCourse(converted)
            }
        )
    }

    // 编辑 / 删除课程（删除需二次确认，确认由 EditCourseSheet 内部处理）
    editingCourse?.let { original ->
        EditCourseSheet(
            course = original,
            onDismissRequest = { editingCourse = null },
            onSave = { updated -> onUpdateCourse(original, updated) },
            onDelete = { onDeleteCourse(original) },
            weekCount = settings.semesterWeekCount,
            lessonCount = settings.lessonCount
        )
    }
}

/** 日期标题：`9月26日 周五`，今天高亮并带「今天」徽标。 */
@Composable
private fun AgendaDayHeader(item: AgendaItem.DayHeader) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "${item.date.month.number}月${item.date.day}日",
            fontSize = SaltTheme.textStyles.main.fontSize,
            fontWeight = FontWeight.Bold,
            color = if (item.isToday) SaltTheme.colors.highlight else SaltTheme.colors.text
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = weekDayLabel(item.date),
            fontSize = SaltTheme.textStyles.sub.fontSize,
            color = SaltTheme.colors.subText
        )
        if (item.isToday) {
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(SaltTheme.colors.highlight)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "今天",
                    fontSize = SaltTheme.textStyles.sub.fontSize,
                    fontWeight = FontWeight.Medium,
                    color = Color.White
                )
            }
        }
        Spacer(modifier = Modifier.weight(1f))
    }
}

/**
 * 课程卡片：
 * ```
 * ┌───────────────────────────────────────────┐
 * │ 08:00   │ 高等数学              A101   │
 * │ 09:40   │ 张伟（小字）                       │
 * └───────────────────────────────────────────┘
 *  ↑左列          ↑5dp + 红竖线 + 5dp
 * ```
 */
@Composable
private fun AgendaCourseCard(
    item: AgendaItem.CourseCard,
    settings: AppSettings = AppSettings(),
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(12.dp))
            // 自定义背景下换半透明纱，背景透出来；跟随系统时仍是原来的不透明 popup
            .background(sceneCardColor())
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 左：上面开始时间，下面结束时间
        Column(
            modifier = Modifier.width(56.dp),
            horizontalAlignment = Alignment.End
        ) {
            Text(
                text = item.startTime,
                fontSize = SaltTheme.textStyles.sub.fontSize,
                fontWeight = FontWeight.Medium,
                color = SaltTheme.colors.text
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = item.endTime,
                fontSize = SaltTheme.textStyles.sub.fontSize,
                color = SaltTheme.colors.subText
            )
        }

        // 中：左右各 5dp 间距，中间红色竖线
        Box(
            modifier = Modifier
                .padding(horizontal = 5.dp)
                .width(4.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(2.dp))
                .background(AgendaAccentLine)
        )

        // 右：上面课程名，下面小字老师名（应用展示层字符串替换；详情页仍显示完整信息）
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = settings.display(item.course.name),
                fontSize = SaltTheme.textStyles.main.fontSize,
                fontWeight = FontWeight.Medium,
                color = SaltTheme.colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = settings.display(item.course.teacher),
                fontSize = SaltTheme.textStyles.sub.fontSize,
                color = SaltTheme.colors.subText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // 最右：教室
        Text(
            text = settings.display(item.classroom),
            fontSize = SaltTheme.textStyles.sub.fontSize,
            color = SaltTheme.colors.subText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
            modifier = Modifier
                .padding(start = 8.dp)
                .widthIn(max = 110.dp)
        )
    }
}
