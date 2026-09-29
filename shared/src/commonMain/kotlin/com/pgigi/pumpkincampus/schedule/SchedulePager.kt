package com.pgigi.pumpkincampus.schedule

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.Text
import com.moriafly.salt.ui.pager.HorizontalPager
import com.moriafly.salt.ui.pager.PagerState
import com.moriafly.salt.ui.pager.rememberPagerState
import com.pgigi.pumpkincampus.models.AppSettings
import com.pgigi.pumpkincampus.models.Course
import com.pgigi.pumpkincampus.models.LessonTime
import com.pgigi.pumpkincampus.models.isFixedCourse
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.math.sqrt
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/** 每天默认课次数（早八到晚十）。 */
const val DefaultLessonCount: Int = 10

/** 默认单节课高度。 */
val DefaultCellHeight: Dp = 56.dp

/** 表头（星期 + 日期）高度。 */
val ScheduleHeaderHeight: Dp = 44.dp

private val TimeColumnWidth: Dp = 40.dp

private val DayLabels = listOf("日", "一", "二", "三", "四", "五", "六")

/** 课程表整表高度 = 表头 + 课次区，给 [SchedulePager] 设置高度时使用。 */
fun scheduleTableHeight(
    lessonCount: Int = DefaultLessonCount,
    cellHeight: Dp = DefaultCellHeight
): Dp = ScheduleHeaderHeight + cellHeight * lessonCount

/** 当前日期（本地时区）。 */
@OptIn(ExperimentalTime::class)
fun currentLocalDate(): LocalDate =
    Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

/**
 * 按教学周拆分课程：返回下标即 0 基周次的每周课程列表。
 *
 * - 普通课程按 [Course.weekIndices] 出现在对应教学周
 * - 固定课程（[isFixedCourse]）只出现在其**绝对日期所属的那一周**
 *   （需传入 [weekCalculator]，日期不在 1..weekCount 范围则不显示），
 *   不随教学周规则重复，也不受作息时间表影响
 */
fun buildCourseListByWeek(
    courses: List<Course>,
    weekCount: Int,
    weekCalculator: WeekCalculator? = null
): List<List<Course>> =
    List(weekCount) { week ->
        courses.filter { course ->
            if (course.isFixedCourse) {
                val date = parseFixedDate(course.fixedDate ?: "") ?: return@filter false
                weekCalculator != null &&
                    weekCalculator.getWeekNumber(date) - 1 == week
            } else {
                week in course.weekIndices
            }
        }
    }

private val ScheduleCellColors = listOf(
    Color(0xffff1644),
    Color(0xff2b78ff),
    Color(0xffa375ff),
    Color(0xffff9000),
    Color(0xff1ee8b5),
    Color(0xffff3d01),
    Color(0xff2097f4),
    Color(0xff005daf),
    Color(0xfffa6278)
)

private val courseColorIndices = mutableMapOf<String, Int>()

/** 同名课程在整个应用内保持同一颜色。 */
private fun courseColor(name: String): Color {
    val index = courseColorIndices.getOrPut(name) { courseColorIndices.size }
    return ScheduleCellColors[index % ScheduleCellColors.size]
}

/**
 * Salt UI 课程表 Pager：左右滑动切换教学周，内部为一天 0 基起始课次排布的周视图。
 *
 * 适配 [Course] 模型：`weekIndices`（0 基教学周）、`dayIndex`（0=周日 ... 6=周六）、
 * `lessonStartIndex`（0 基起始课次）、`lessonCount`（占用课节数）。
 *
 * 参考 Pumpkin-Toolkit 的 `SchedulePager`，但完全基于 Salt UI（无 miuix）实现：
 * - 切换教学周：Salt UI `HorizontalPager`
 * - 单课程详情：直接复用 Material 3 的 [CourseBottomSheet]（`ModalBottomSheet`，
 *   遮罩 / 点外部与返回键关闭 / 上下拖动收起，内容见 [CourseDetailSheetContent]）
 * - 课程冲突：同样从底部弹层展示，标题「课程详细」右侧有横向滑动的课程名 Chip 组，
 *   点击 Chip 切换展示的课程，选中 Chip 高亮（不再使用选择 Dialog）
 *
 * 布局为「**固定日期行 + 内部滚动的表格体**」：
 * - 日期行（[ScheduleHeaderHeight]）固定在组件顶部，始终顶住上方 TopBar；
 *   表格体上下滑动时从日期行下面经过（被日期行挡住），而不是整张表被 TopBar 挡住
 * - 纵向滚动发生在组件内部（所有周页共用同一滚动偏移），页面本身不需要滚动
 * - 组件左右应顶住容器边缘（不要额外加水平内边距）
 *
 * 调用方必须提供**有界高度**（`Modifier.weight(1f)`、`fillMaxSize()` 或固定 `height`），
 * 否则内部按权重分配的 Pager 拿不到高度；完整高度可参考 [scheduleTableHeight]。
 *
 * @param courseListByWeek 下标即 0 基教学周的课程列表，可用 [buildCourseListByWeek] 生成
 * @param lessonTimes 每节课的时间标签，为空则只显示课次序号
 * @param weekCalculator 传入则在表头展示每周日期，并高亮今天；null 只显示星期
 * @param pagerState 可外部持有以控制/监听当前周，注意 `pageCount` 需与 [courseListByWeek] 一致
 * @param onCourseClick 点击单门课程的回调，null 时从模态底部弹层展示内置详情
 * @param onConflictClick 点击冲突课程组的回调，null 时内置处理：直接从底部弹层打开
 *   该冲突组（标题右侧横向 Chip 组切换，选中高亮）
 * @param onEditCourse 点击内置详情里的「编辑课程」时回调（null 则详情不显示编辑入口）
 * @param isPluginCourse 判断一门课程是否来自**插件只读层**：详情里不提供「编辑课程」，
 *   改为只读提示 +「转换为自定义课程」
 * @param onConvertCourse 插件课程详情里「转换为自定义课程」的回调（null 则不显示入口）
 * @param showGridLines 是否显示网格辅助线：课次横线 **与 两天之间的竖线**（课表外观设置）
 * @param settings 课表外观设置（单元格高度除外由 [cellHeight] 控制）：老师/地点显示、
 *   「@」前缀与展示层字符串替换；null 用默认行为
 */
@Composable
fun SchedulePager(
    courseListByWeek: List<List<Course>>,
    modifier: Modifier = Modifier,
    lessonTimes: List<LessonTime> = emptyList(),
    lessonCount: Int = DefaultLessonCount,
    cellHeight: Dp = DefaultCellHeight,
    weekCalculator: WeekCalculator? = null,
    pagerState: PagerState = rememberPagerState(pageCount = { courseListByWeek.size }),
    onCourseClick: ((Course) -> Unit)? = null,
    onConflictClick: ((List<Course>) -> Unit)? = null,
    onEditCourse: ((Course) -> Unit)? = null,
    isPluginCourse: (Course) -> Boolean = { false },
    onConvertCourse: ((Course) -> Unit)? = null,
    showGridLines: Boolean = true,
    settings: AppSettings? = null
) {
    var selectedCourse by remember { mutableStateOf<Course?>(null) }
    var conflictCourses by remember { mutableStateOf<List<Course>?>(null) }

    // 所有周页共用一个纵向滚动状态：表格体滚动、日期行不动，且页间偏移保持一致
    val bodyScrollState = rememberScrollState()

    Column(modifier = modifier) {
        // 固定的日期行：顶住 TopBar，滚动的表格体从它下面经过
        val headerFirstDay = remember(weekCalculator, pagerState.currentPage) {
            weekCalculator?.getWeekFirstDay(pagerState.currentPage + 1)
        }
        ScheduleHeader(
            firstDay = headerFirstDay,
            today = remember { currentLocalDate() }
        )

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            // 页内容顶部对齐：课表紧贴上方日期行，而不是在剩余空间里垂直居中
            verticalAlignment = Alignment.Top
        ) { page ->
            val firstDay = remember(weekCalculator, page) {
                weekCalculator?.getWeekFirstDay(page + 1)
            }
            // 表格体：填满整页高度后再纵向滚动（不是整个页面滚动），行从顶部开始排
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(bodyScrollState)
            ) {
                ScheduleTable(
                    courses = courseListByWeek.getOrElse(page) { emptyList() },
                    lessonTimes = lessonTimes,
                    lessonCount = lessonCount,
                    cellHeight = cellHeight,
                    firstDay = firstDay,
                    showHeader = false,
                    showGridLines = showGridLines,
                    settings = settings,
                    onCourseClick = { course ->
                        if (onCourseClick != null) onCourseClick(course)
                        else selectedCourse = course
                    },
                    onConflictClick = { group ->
                        if (onConflictClick != null) onConflictClick(group)
                        else conflictCourses = group
                    }
                )
            }
        }
    }

    selectedCourse?.let { course ->
        CourseBottomSheet(
            course = course,
            onDismissRequest = { selectedCourse = null },
            onEdit = onEditCourse,
            lessonTimes = lessonTimes,
            isPluginCourse = isPluginCourse,
            onConvert = onConvertCourse
        )
    }

    // 冲突课程：不再用 Dialog 选择，直接在详情弹层「课程详细」标题右侧用横向滑动
    // 的课程名 Chip 组切换（选中 Chip 高亮）
    conflictCourses?.let { group ->
        CourseBottomSheet(
            course = group.first(),
            conflictCourses = group,
            onDismissRequest = { conflictCourses = null },
            onEdit = onEditCourse,
            lessonTimes = lessonTimes,
            isPluginCourse = isPluginCourse,
            onConvert = onConvertCourse
        )
    }
}

/**
 * 单周课程表：表头（月份 + 星期 + 日期）+ 左侧课次时间列 + 7 天课程格子。
 *
 * 当 [showHeader] 为 false 时只渲染表格体（日期行由 [SchedulePager] 固定在顶部）。
 *
 * @param firstDay 本周第一天（决定列顺序：首列 = 该天；null 时按周日开头）
 * @param showGridLines 是否绘制网格辅助线：课次横线 + 两天之间的竖线
 * @param settings 课表外观设置（老师/地点/「@」/展示层字符串替换），null 用默认行为
 */
@Composable
fun ScheduleTable(
    courses: List<Course>,
    modifier: Modifier = Modifier,
    lessonTimes: List<LessonTime> = emptyList(),
    lessonCount: Int = DefaultLessonCount,
    cellHeight: Dp = DefaultCellHeight,
    firstDay: LocalDate? = null,
    showHeader: Boolean = true,
    showGridLines: Boolean = true,
    settings: AppSettings? = null,
    onCourseClick: (Course) -> Unit = {},
    onConflictClick: (List<Course>) -> Unit = {}
) {
    val today = remember { currentLocalDate() }
    val dayCourses = remember(courses, lessonCount) {
        Array(7) { day ->
            val dayList = courses.filter { it.dayIndex == day }
            // 普通课程按课次合并展示；固定课程原样放入（由 DayColumn 按钟点定位）
            mergeCoursesForDisplay(
                dayList.filter {
                    !it.isFixedCourse && it.lessonStartIndex in 0 until lessonCount
                }
            ) + dayList.filter { it.isFixedCourse }
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        if (showHeader) {
            ScheduleHeader(firstDay = firstDay, today = today)
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(cellHeight * lessonCount)
        ) {
            TimeColumn(
                lessonTimes = lessonTimes,
                lessonCount = lessonCount,
                cellHeight = cellHeight
            )
            // 列顺序：本周第一天（firstDay）在最左，其后依次（支持周日/周三等任意一周起点）
            val firstWeekDay = firstDay?.let { dayIndexOf(it) } ?: 0
            for (i in 0 until 7) {
                val day = (firstWeekDay + i) % 7
                DayColumn(
                    courses = dayCourses[day],
                    lessonTimes = lessonTimes,
                    lessonCount = lessonCount,
                    cellHeight = cellHeight,
                    showStartDivider = i != 0,
                    showGridLines = showGridLines,
                    settings = settings,
                    modifier = Modifier.weight(1f),
                    onCourseClick = onCourseClick,
                    onConflictClick = onConflictClick
                )
            }
        }
    }
}

/**
 * 日期行：左上角月份 + 每列星期与日期（今天高亮）。
 *
 * 固定在 [SchedulePager] 顶部（顶住 TopBar）。**自身透明**——它下面是 [SchedulePager] 的
 * Column，日期行在上、表格体在下，两者不重叠，所以底色直接交给场景背景层：
 * 铺了自定义背景（图片/纯色）时这一行就透出背景，跟随系统时背景层是页面底色，观感不变。
 */
@Composable
private fun ScheduleHeader(firstDay: LocalDate?, today: LocalDate) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(ScheduleHeaderHeight)
    ) {
        Column(
            modifier = Modifier
                .width(TimeColumnWidth)
                .fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (firstDay != null) {
                Text(
                    text = "${firstDay.month.number}",
                    fontSize = 12.sp,
                    color = SaltTheme.colors.text
                )
                Text(
                    text = "月",
                    fontSize = 12.sp,
                    color = SaltTheme.colors.text
                )
            }
        }

        DayLabels.forEachIndexed { index, _ ->
            val date = firstDay?.plus(index, DateTimeUnit.DAY)
            // 一周的第一天可能不是周日：星期标签按真实日期取（0=周日 … 6=周六）
            val label = if (date != null) DayLabels[dayIndexOf(date)] else DayLabels[index]
            val isToday = date != null && date == today
            val color =
                if (isToday) SaltTheme.colors.highlight else SaltTheme.colors.text
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = label,
                    fontSize = 12.sp,
                    color = color
                )
                if (date != null) {
                    Text(
                        text = "${date.day}",
                        fontSize = 12.sp,
                        color = if (isToday) color else SaltTheme.colors.text
                    )
                }
            }
        }
    }
}

@Composable
private fun TimeColumn(
    lessonTimes: List<LessonTime>,
    lessonCount: Int,
    cellHeight: Dp,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(TimeColumnWidth)
            .height(cellHeight * lessonCount)
    ) {
        repeat(lessonCount) { index ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(cellHeight),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "${index + 1}",
                    fontSize = 12.sp,
                    color = SaltTheme.colors.text,
                    textAlign = TextAlign.Center
                )
                if (index < lessonTimes.size) {
                    val time = lessonTimes[index]
                    Text(
                        text = "${time.start}\n${time.end}",
                        fontSize = 7.sp,
                        lineHeight = 9.sp,
                        color = SaltTheme.colors.subText,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun DayColumn(
    courses: List<Course>,
    lessonTimes: List<LessonTime>,
    lessonCount: Int,
    cellHeight: Dp,
    showStartDivider: Boolean,
    showGridLines: Boolean = true,
    settings: AppSettings? = null,
    modifier: Modifier = Modifier,
    onCourseClick: (Course) -> Unit,
    onConflictClick: (List<Course>) -> Unit
) {
    // 普通课程（按课次分组、可能冲突）与固定课程（按钟点定位）分流
    val (fixedCourses, slotCourses) = remember(courses) {
        courses.partition { it.isFixedCourse }
    }
    val groups = remember(slotCourses) { groupConflictingCourses(slotCourses) }
    val stroke = SaltTheme.colors.stroke

    Box(
        modifier = modifier.height(cellHeight * lessonCount)
    ) {
        // 辅助线：课次横线 + 两天之间的竖线，统一由「辅助线」开关控制
        if (showGridLines) {
            Column(modifier = Modifier.matchParentSize()) {
                repeat(lessonCount) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .drawBehind {
                                val strokeWidth = 1.dp.toPx()
                                // 两天之间的竖线（列分隔线）
                                if (showStartDivider) {
                                    drawLine(
                                        color = stroke,
                                        start = Offset(0f, 0f),
                                        end = Offset(0f, size.height),
                                        strokeWidth = strokeWidth
                                    )
                                }
                                // 课次横线
                                drawLine(
                                    color = stroke,
                                    start = Offset(0f, size.height),
                                    end = Offset(size.width, size.height),
                                    strokeWidth = strokeWidth
                                )
                            }
                    )
                }
            }
        }

        groups.forEach { group ->
            val hasConflict = group.size > 1
            // 重叠课程全部渲染成图层：组内后面的课程后绘制（显示在前一个之上）；
            // 每个单元格都带右下角冲突角标，点击任意一个都弹出冲突课程选择
            group.forEach { course ->
                CourseCell(
                    course = course,
                    hasConflict = hasConflict,
                    settings = settings,
                    modifier = Modifier
                        .padding(horizontal = 1.5.dp)
                        .padding(top = cellHeight * course.lessonStartIndex + 1.dp)
                        .height(cellHeight * course.lessonCount - 2.dp)
                        .fillMaxWidth(),
                    onClick = {
                        if (hasConflict) onConflictClick(group) else onCourseClick(course)
                    }
                )
            }
        }

        // 固定课程：按真实钟点在当天列中定位，高度按持续时长换算
        //（不受作息时间表调整影响；后绘制 → 图层在普通课程之上）
        fixedCourses.forEach { course ->
            val startMinute = course.fixedStartMinute
            val duration = course.fixedDurationMinutes
            if (startMinute == null || duration == null) return@forEach

            val top = minutesToY(startMinute, lessonTimes, lessonCount, cellHeight)
            var height = minutesToY(
                startMinute + duration,
                lessonTimes,
                lessonCount,
                cellHeight
            ) - top
            if (height <= 0.dp) {
                // 完全落在课间空档等特殊情况：按分钟数折算，保证仍可见
                height = cellHeight * (duration / 60f)
            }
            height = height.coerceAtMost(cellHeight * lessonCount - top)
            if (height <= 0.dp) return@forEach

            CourseCell(
                course = course,
                hasConflict = false,
                settings = settings,
                modifier = Modifier
                    .padding(horizontal = 1.5.dp)
                    .padding(top = top)
                    .height(height)
                    .fillMaxWidth(),
                onClick = { onCourseClick(course) }
            )
        }
    }
}

/** 冲突标记（右下角白色三角斜面）的画布尺寸。 */
private val ConflictMarkerSize = 24.dp

/** 三个顶点与格子边缘的距离。 */
private val ConflictMarkerInset = 5.dp

/** 圆角切距：越大倒角越圆。 */
private val ConflictMarkerCut = 5.dp

@Composable
private fun CourseCell(
    course: Course,
    hasConflict: Boolean,
    onClick: () -> Unit,
    settings: AppSettings? = null,
    modifier: Modifier = Modifier
) {
    val baseColor = remember(course.name) { courseColor(course.name) }
    val classroom = remember(course.classroom) { course.classroom }
    // 课程名 / 老师 / 地点都走展示层替换（详情页不受影响，始终显示完整信息）
    val displayName = settings?.display(course.name) ?: course.name

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(baseColor.copy(alpha = 0.85f))
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 3.dp)
        ) {
            Text(
                text = displayName,
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 14.sp,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
            // 固定课程：在课程名下方标注真实时间区间
            if (course.isFixedCourse) {
                val fixedStart = course.fixedStartMinute
                val fixedDuration = course.fixedDurationMinutes
                if (fixedStart != null && fixedDuration != null) {
                    Text(
                        text = formatClockMinutes(fixedStart) + "-" +
                            formatClockMinutes(fixedStart + fixedDuration),
                        color = Color.White.copy(alpha = 0.92f),
                        fontSize = 9.sp,
                        lineHeight = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            // 上课地点（可关闭；「@」前缀可配置；同样应用展示层替换）
            val visibleClassroom = when {
                settings != null && !settings.showClassroom -> null
                classroom.isBlank() -> null
                else -> {
                    val prefix = if (settings == null || settings.classroomAtPrefix) "@" else ""
                    prefix + (settings?.display(classroom) ?: classroom)
                }
            }
            if (visibleClassroom != null) {
                Text(
                    text = visibleClassroom,
                    color = Color.White.copy(alpha = 0.92f),
                    fontSize = 10.sp,
                    lineHeight = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            // 授课老师（课表外观设置开启时显示）
            if (settings?.showTeacher == true && course.teacher.isNotBlank()) {
                Text(
                    text = settings.display(course.teacher),
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 9.sp,
                    lineHeight = 11.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // 课程重叠（冲突）：右下角画白色三角斜面，
        // 三个角均为圆角，且三个顶点都与格子边缘留 2dp
        if (hasConflict) {
            Canvas(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(ConflictMarkerSize)
            ) {
                val inset = ConflictMarkerInset.toPx()
                val cut = ConflictMarkerCut.toPx()

                // 直角在右下；三个顶点分别与最近的两条边留出 inset
                val bottomLeft = Offset(inset, size.height - inset)
                val topRight = Offset(size.width - inset, inset)
                val bottomRight = Offset(size.width - inset, size.height - inset)

                fun cutPoint(from: Offset, to: Offset): Offset {
                    val dx = to.x - from.x
                    val dy = to.y - from.y
                    val length = sqrt(dx * dx + dy * dy)
                    if (length <= 0f) return from
                    val t = (cut / length).coerceAtMost(0.5f)
                    return Offset(from.x + dx * t, from.y + dy * t)
                }

                // 三条边上的六个倒圆切点
                val bottomNearLeft = cutPoint(bottomLeft, bottomRight)
                val bottomNearRight = cutPoint(bottomRight, bottomLeft)
                val rightNearBottom = cutPoint(bottomRight, topRight)
                val rightNearTop = cutPoint(topRight, bottomRight)
                val hypotenuseNearTop = cutPoint(topRight, bottomLeft)
                val hypotenuseNearLeft = cutPoint(bottomLeft, topRight)

                val path = Path().apply {
                    moveTo(bottomNearLeft.x, bottomNearLeft.y)
                    lineTo(bottomNearRight.x, bottomNearRight.y)
                    // 右下直角倒圆
                    quadraticTo(
                        bottomRight.x, bottomRight.y,
                        rightNearBottom.x, rightNearBottom.y
                    )
                    lineTo(rightNearTop.x, rightNearTop.y)
                    // 右上角倒圆
                    quadraticTo(
                        topRight.x, topRight.y,
                        hypotenuseNearTop.x, hypotenuseNearTop.y
                    )
                    lineTo(hypotenuseNearLeft.x, hypotenuseNearLeft.y)
                    // 左下角倒圆后闭合
                    quadraticTo(
                        bottomLeft.x, bottomLeft.y,
                        bottomNearLeft.x, bottomNearLeft.y
                    )
                    close()
                }
                drawPath(path, color = Color.White)
            }
        }
    }
}

/**
 * 合并同一时段内可连续显示的相同课程（上午 1-4 节 / 下午 5-8 节两个区块内），
 * 与 Pumpkin-Toolkit 的 `mergeCoursesForDisplay` 逻辑一致（此处为 0 基课次）。
 */
internal fun mergeCoursesForDisplay(courses: List<Course>): List<Course> {
    val sorted = courses.sortedBy { it.lessonStartIndex }
    if (sorted.isEmpty()) return sorted

    val result = mutableListOf<Course>()
    val blocks = listOf(0..3, 4..7)

    blocks.forEach { block ->
        val blockCourses = sorted.filter { course ->
            val start = course.lessonStartIndex
            val end = course.lessonStartIndex + course.lessonCount - 1
            start in block && end in block
        }
        if (blockCourses.isEmpty()) return@forEach

        var current = blockCourses.first()
        for (next in blockCourses.drop(1)) {
            val currentEnd = current.lessonStartIndex + current.lessonCount - 1
            val isAdjacent = next.lessonStartIndex == currentEnd + 1
            if (isAdjacent && canMergeCourse(current, next)) {
                current = current.copy(lessonCount = current.lessonCount + next.lessonCount)
            } else {
                result.add(current)
                current = next
            }
        }
        result.add(current)
    }

    result.addAll(sorted.filter { course ->
        val start = course.lessonStartIndex
        val end = course.lessonStartIndex + course.lessonCount - 1
        !(start in 0..3 && end in 0..3) && !(start in 4..7 && end in 4..7)
    })

    return result.sortedBy { it.lessonStartIndex }
}

private fun canMergeCourse(a: Course, b: Course): Boolean =
    a.name == b.name &&
        a.classroom == b.classroom &&
        a.teacher == b.teacher &&
        a.weekIndices == b.weekIndices

/** 按时间重叠把同一列的课程分组，返回的每组内课程互相冲突。 */
internal fun groupConflictingCourses(courses: List<Course>): List<List<Course>> {
    val sorted = courses.sortedBy { it.lessonStartIndex }
    if (sorted.isEmpty()) return emptyList()

    val groups = mutableListOf<List<Course>>()
    var currentGroup = mutableListOf(sorted.first())
    var currentMaxEnd = sorted.first().lessonStartIndex + sorted.first().lessonCount - 1

    for (course in sorted.drop(1)) {
        if (course.lessonStartIndex <= currentMaxEnd) {
            currentGroup.add(course)
            currentMaxEnd = maxOf(currentMaxEnd, course.lessonStartIndex + course.lessonCount - 1)
        } else {
            groups.add(currentGroup)
            currentGroup = mutableListOf(course)
            currentMaxEnd = course.lessonStartIndex + course.lessonCount - 1
        }
    }
    groups.add(currentGroup)

    return groups
}
