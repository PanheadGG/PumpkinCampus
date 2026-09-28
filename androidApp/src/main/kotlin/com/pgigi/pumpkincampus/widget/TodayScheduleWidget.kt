package com.pgigi.pumpkincampus.widget

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.action.clickable
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.FixedColorProvider
import com.pgigi.pumpkincampus.MainActivity
import com.pgigi.pumpkincampus.utils.DisplayCourse
import com.pgigi.pumpkincampus.utils.WidgetSnapshot
import com.pgigi.pumpkincampus.utils.loadWidgetSnapshot

/**
 * 「今日课程」桌面小组件（Glance，移植自 Pumpkin-Toolkit 的 `TodayScheduleWidget`）。
 *
 * 展示当前打开课表的**今天剩余课程**与**明天课程**：
 * - 尺寸自适应（与 Toolkit 同一套断点）：小 → 课程名/时间/地点竖排；
 *   中 → 时间/地点/老师同一行；大 → 与中同款、列表更长
 * - 课程**不加卡片底色**，直接排在小组件背景上（顶栏与课程行左对齐）
 * - **正在上课**的课程整行橙色强调；**明日课程**整体灰色；今天其它课程常规文字色 + 灰色次要信息
 * - 假期（教学周不在学期内）显示「假期中」；应用从未写过数据时提示「请打开应用刷新」
 * - 点击整块打开应用
 *
 * 数据在**每次渲染时**从 `widget_data.json` 现算（见 [loadWidgetSnapshot]）：课程下课、
 * 跨零点、系统按 [android:updatePeriodMillis] 定时刷新都会得到正确结果，
 * 应用不在前台也不影响。
 */
class TodayScheduleWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(
            DpSize(110.dp, 110.dp),
            DpSize(180.dp, 110.dp),
            DpSize(250.dp, 110.dp),
            DpSize(320.dp, 110.dp),
            DpSize(180.dp, 180.dp),
            DpSize(250.dp, 250.dp),
            DpSize(320.dp, 250.dp),
            DpSize(320.dp, 320.dp)
        )
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // filesDir 直读（小组件进程不走 MainActivity，不能用 AppContextHolder）
        val snapshot = loadWidgetSnapshot(context)
        provideContent {
            WidgetContent(snapshot)
        }
    }
}

/**
 * 小组件配色（浅色 / 深色两套，跟随系统深色模式）。
 *
 * 与 Pumpkin-Toolkit 的差异（按需求调整）：
 * - **去掉课程卡片底色**，课程直接排在小组件背景上
 * - **正在上课**的课程用 [ongoing]（橙色）强调
 * - **明日课程**整体降为 [secondary]（灰色），不再用橙色卡片
 */
private data class WidgetColors(
    val background: Color,
    val text: Color,
    val secondary: Color,
    val ongoing: Color
)

@Composable
private fun isDarkTheme(): Boolean {
    val context = LocalContext.current
    return (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
}

@Composable
private fun widgetColors(): WidgetColors {
    val dark = isDarkTheme()
    return WidgetColors(
        background = if (dark) Color(0xFF1B1B1B) else Color(0xFFF7F7F7),
        text = if (dark) Color(0xFFE0E0E0) else Color(0xFF1A1A1A),
        secondary = if (dark) Color(0xFF888888) else Color(0xFF666666),
        ongoing = if (dark) Color(0xFFFFB08A) else Color(0xFFCC4A1F)
    )
}

@Composable
private fun WidgetContent(snapshot: WidgetSnapshot?) {
    val colors = widgetColors()
    val size = LocalSize.current
    val context = LocalContext.current
    // 小组件点击 = 打开应用（不新建任务栈实例）
    val intent = remember {
        Intent(context, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
    }

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(colors.background)
            .cornerRadius(16.dp)
            .padding(all = 12.dp)
            .clickable(actionStartActivity(intent))
    ) {
        if (snapshot == null) {
            NoDataView(colors)
        } else if (snapshot.isHoliday) {
            HolidayView(snapshot.dayOfWeekText, colors)
        } else {
            when {
                size.width < 140.dp && size.height < 140.dp -> SmallWidgetView(snapshot, colors)
                size.height < 150.dp -> MediumWidgetView(snapshot, colors)
                else -> LargeWidgetView(snapshot, colors)
            }
        }
    }
}

@SuppressLint("RestrictedApi")
@Composable
private fun NoDataView(colors: WidgetColors) {
    Column(
        modifier = GlanceModifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "请打开应用刷新",
            style = TextStyle(
                color = FixedColorProvider(colors.secondary),
                fontSize = 14.sp
            )
        )
    }
}

@Composable
private fun HolidayView(dayOfWeekText: String, colors: WidgetColors) {
    Column(modifier = GlanceModifier.fillMaxSize()) {
        Header(0, dayOfWeekText, true, colors)
        Box(
            modifier = GlanceModifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "假期中",
                style = TextStyle(
                    color = FixedColorProvider(colors.secondary),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            )
        }
    }
}

/** 小尺寸：只列课程名 + 时间 + 地点（一行一门，可滚动）。 */
@Composable
private fun SmallWidgetView(state: WidgetSnapshot, colors: WidgetColors) {
    Column(modifier = GlanceModifier.fillMaxSize()) {
        Header(state.weekNumber, state.dayOfWeekText, state.isHoliday, colors)
        if (state.todayCourses.isEmpty() && state.tomorrowCourses.isEmpty()) {
            EmptyToday(colors, 15.sp)
        } else {
            CourseList(state, colors, small = true)
        }
    }
}

/** 中尺寸：课程名与时间/地点/老师同一行。 */
@Composable
private fun MediumWidgetView(state: WidgetSnapshot, colors: WidgetColors) {
    Column(modifier = GlanceModifier.fillMaxSize()) {
        Header(state.weekNumber, state.dayOfWeekText, state.isHoliday, colors)
        if (state.todayCourses.isEmpty() && state.tomorrowCourses.isEmpty()) {
            EmptyToday(colors, 15.sp)
        } else {
            CourseList(state, colors, small = false)
        }
    }
}

/** 大尺寸：与中尺寸同款卡片，列表更长。 */
@Composable
private fun LargeWidgetView(state: WidgetSnapshot, colors: WidgetColors) {
    Column(modifier = GlanceModifier.fillMaxSize()) {
        Header(state.weekNumber, state.dayOfWeekText, state.isHoliday, colors)
        if (state.todayCourses.isEmpty() && state.tomorrowCourses.isEmpty()) {
            Box(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "今日无课",
                    style = TextStyle(
                        color = FixedColorProvider(colors.secondary),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
            }
        } else {
            CourseList(state, colors, small = false)
        }
    }
}

@Composable
private fun EmptyToday(colors: WidgetColors, fontSize: TextUnit) {
    Box(
        modifier = GlanceModifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "今日无课",
            style = TextStyle(
                color = FixedColorProvider(colors.secondary),
                fontSize = fontSize,
                fontWeight = FontWeight.Medium
            )
        )
    }
}

/** 今天的剩余课程 + 「明日」分隔线 + 明天的课程。 */
@Composable
private fun CourseList(state: WidgetSnapshot, colors: WidgetColors, small: Boolean) {
    LazyColumn(modifier = GlanceModifier.fillMaxWidth()) {
        items(state.todayCourses) { course ->
            if (small) SmallCourseCard(course, colors) else CourseCard(course, colors)
        }
        if (state.tomorrowCourses.isNotEmpty()) {
            item { TomorrowSeparator(colors) }
            items(state.tomorrowCourses) { course ->
                if (small) SmallCourseCard(course, colors) else CourseCard(course, colors)
            }
        }
    }
}

/** 顶栏：左侧「第N周」（假期/未开学时不显示），右侧星期。 */
@Composable
private fun Header(
    weekNumber: Int,
    dayOfWeekText: String,
    isHoliday: Boolean,
    colors: WidgetColors
) {
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (!isHoliday && weekNumber > 0) {
            Text(
                "第${weekNumber}周",
                style = TextStyle(
                    color = FixedColorProvider(colors.text),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            )
        }
        Spacer(modifier = GlanceModifier.defaultWeight())
        Text(
            dayOfWeekText,
            style = TextStyle(
                color = FixedColorProvider(colors.text),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        )
    }
}

/** 「明日」分隔线：两侧细线 + 中间文字。 */
@Composable
private fun TomorrowSeparator(colors: WidgetColors) {
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = GlanceModifier
                .height(1.dp)
                .background(colors.secondary)
                .defaultWeight()
        ) {}
        Text(
            "明日",
            modifier = GlanceModifier.padding(horizontal = 8.dp),
            style = TextStyle(
                color = FixedColorProvider(colors.secondary),
                fontSize = 11.sp
            )
        )
        Box(
            modifier = GlanceModifier
                .height(1.dp)
                .background(colors.secondary)
                .defaultWeight()
        ) {}
    }
}

/**
 * 课程名配色：**正在进行 → 橙色**，明日 → 灰色，今天其它 → 常规文字色。
 */
private fun nameColorOf(course: DisplayCourse, colors: WidgetColors): Color = when {
    course.isOngoing -> colors.ongoing
    course.isTomorrow -> colors.secondary
    else -> colors.text
}

/** 时间/地点/老师配色：正在进行的课跟着橙色，其余一律灰色。 */
private fun detailColorOf(course: DisplayCourse, colors: WidgetColors): Color =
    if (course.isOngoing) colors.ongoing else colors.secondary

/** 小尺寸课程行：课程名 / 时间 / 地点 竖排（无卡片底色，与顶栏左对齐）。 */
@Composable
private fun SmallCourseCard(
    course: DisplayCourse,
    colors: WidgetColors
) {
    Column(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(
            course.name,
            style = TextStyle(
                color = FixedColorProvider(nameColorOf(course, colors)),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        )
        Spacer(modifier = GlanceModifier.height(3.dp))
        Text(
            "${course.startTime}-${course.endTime}",
            style = TextStyle(
                color = FixedColorProvider(detailColorOf(course, colors)),
                fontSize = 11.sp
            )
        )
        if (course.classroom.isNotEmpty()) {
            Spacer(modifier = GlanceModifier.height(3.dp))
            Text(
                course.classroom,
                style = TextStyle(
                    color = FixedColorProvider(detailColorOf(course, colors)),
                    fontSize = 11.sp
                )
            )
        }
    }
}

/** 中/大尺寸课程行：课程名一行，时间 / 地点 / 老师一行（无卡片底色）。 */
@Composable
private fun CourseCard(
    course: DisplayCourse,
    colors: WidgetColors
) {
    val nameColor = nameColorOf(course, colors)
    val detailColor = detailColorOf(course, colors)

    Column(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(
            course.name,
            style = TextStyle(
                color = FixedColorProvider(nameColor),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        )
        Spacer(modifier = GlanceModifier.height(2.dp))
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "${course.startTime}-${course.endTime}",
                style = TextStyle(
                    color = FixedColorProvider(detailColor),
                    fontSize = 11.sp
                )
            )
            Spacer(modifier = GlanceModifier.width(6.dp))
            if (course.classroom.isNotEmpty()) {
                Text(
                    course.classroom,
                    style = TextStyle(
                        color = FixedColorProvider(detailColor),
                        fontSize = 11.sp
                    )
                )
            }
            Spacer(modifier = GlanceModifier.defaultWeight())
            if (course.teacher.isNotEmpty()) {
                Text(
                    course.teacher,
                    style = TextStyle(
                        color = FixedColorProvider(detailColor),
                        fontSize = 11.sp
                    )
                )
            }
        }
    }
}
