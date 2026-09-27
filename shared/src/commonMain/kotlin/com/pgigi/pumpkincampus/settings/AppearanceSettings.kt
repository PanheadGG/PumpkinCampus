package com.pgigi.pumpkincampus.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.moriafly.salt.ui.Icon
import com.moriafly.salt.ui.ItemDivider
import com.moriafly.salt.ui.ItemOuterTitle
import com.moriafly.salt.ui.ItemSwitcher
import com.moriafly.salt.ui.ItemTip
import com.moriafly.salt.ui.RoundedColumn
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.Text
import com.moriafly.salt.ui.UnstableSaltUiApi
import com.moriafly.salt.ui.icons.Back
import com.moriafly.salt.ui.icons.SaltIcons
import com.moriafly.salt.ui.pager.rememberPagerState
import com.moriafly.salt.ui.screen.BasicScreen
import com.moriafly.salt.ui.screen.TitleBarButton
import com.pgigi.pumpkincampus.models.AppSettings
import com.pgigi.pumpkincampus.models.Course
import com.pgigi.pumpkincampus.schedule.SchedulePager
import com.pgigi.pumpkincampus.schedule.buildCourseListByWeek
import com.pgigi.pumpkincampus.schedule.currentLocalDate
import com.pgigi.pumpkincampus.schedule.dayIndexOf
import com.pgigi.pumpkincampus.schedule.weekCalculatorOf
import kotlin.math.roundToInt

/**
 * 课表外观设置子页（设置 Tab → 外观；**全局设置，所有课表共用**）：
 * - **上半屏**：实时预览（当前周课程表，随下方参数即时刷新；点击课程不弹详情）
 * - **下半屏**：可滚动参数
 *   - 显示网格辅助线开关
 *   - 单元格高度 50–100dp（步长 10）
 *   - 是否显示授课老师 / 上课地点 / 地点「@」前缀
 *
 * 所有参数只影响**展示**；课程详情与编辑表单始终显示完整信息。
 * 「显示替换」是课表专属设置，已移到课表页「⋯ → 课表设置」。
 */
@OptIn(UnstableSaltUiApi::class)
@Composable
internal fun AppearanceSettingsPage(
    settings: AppSettings,
    courses: List<Course>,
    onSettingsChange: (AppSettings) -> Unit,
    onBack: () -> Unit
) {
    val weekCalculator = remember(settings.termStart) { weekCalculatorOf(settings.termStart) }
    val weekCount = settings.semesterWeekCount
    // 预览课程：真实课程优先；没有课程时用仅供预览的样例（不写入任何存档）
    val previewCourses = remember(courses) {
        courses.ifEmpty { previewSampleCourses(weekCount) }
    }
    val courseListByWeek = remember(previewCourses, weekCalculator, weekCount) {
        buildCourseListByWeek(previewCourses, weekCount, weekCalculator)
    }
    val currentWeekPage = remember(weekCalculator, weekCount) {
        weekCalculator.getWeekNumber(currentLocalDate())
            .coerceIn(1, weekCount) - 1
    }
    val pagerState = rememberPagerState(initialPage = currentWeekPage) {
        courseListByWeek.size
    }

    fun update(block: (AppSettings) -> AppSettings) = onSettingsChange(block(settings))

    BasicScreen(
        // 返回按钮放最左边（标题左边）
        actionButton = {
            TitleBarButton(
                onClick = onBack,
                icon = {
                    Icon(
                        imageVector = SaltIcons.Back,
                        contentDescription = "返回",
                        tint = SaltTheme.colors.text
                    )
                }
            )
        },
        title = "课表外观"
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = contentPadding.calculateTopPadding())
                .padding(bottom = contentPadding.calculateBottomPadding())
        ) {
            // 上半屏：实时预览
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                SchedulePager(
                    courseListByWeek = courseListByWeek,
                    lessonTimes = settings.activeLessonTimes(),
                    lessonCount = settings.lessonCount,
                    weekCalculator = weekCalculator,
                    pagerState = pagerState,
                    cellHeight = settings.cellHeightDp.dp,
                    showGridLines = settings.showGridLines,
                    settings = settings,
                    // 预览：点击不弹详情/冲突选择
                    onCourseClick = {},
                    onConflictClick = {},
                    modifier = Modifier.fillMaxSize()
                )
            }

            // 下半屏：参数
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(top = 4.dp, bottom = 24.dp)
            ) {
//                ItemOuterTitle(text = "显示")
                RoundedColumn {
                    ItemSwitcher(
                        state = settings.showGridLines,
                        onChange = { v -> update { s -> s.copy(showGridLines = v) } },
                        text = "显示网格辅助线"
                    )
                    ItemDivider()
                    // 单元格高度：50–100dp，步长 10（steps = 中间刻度数 = 4）
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = SaltTheme.dimens.padding,
                                vertical = 4.dp
                            )
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "单元格高度",
                                fontSize = SaltTheme.textStyles.main.fontSize,
                                color = SaltTheme.colors.text,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "${settings.cellHeightDp} dp",
                                fontSize = SaltTheme.textStyles.sub.fontSize,
                                color = SaltTheme.colors.subText
                            )
                        }
                        Slider(
                            value = settings.cellHeightDp.toFloat(),
                            onValueChange = { value ->
                                update {
                                    it.copy(
                                        cellHeightDp = value.roundToInt()
                                            .coerceIn(50, 100)
                                    )
                                }
                            },
                            valueRange = 50f..100f,
                            steps = 4,
                            colors = SliderDefaults.colors(
                                thumbColor = SaltTheme.colors.highlight,
                                activeTrackColor = SaltTheme.colors.highlight,
                                inactiveTrackColor = SaltTheme.colors.stroke
                            )
                        )
                    }
                    ItemDivider()
                    ItemSwitcher(
                        state = settings.showTeacher,
                        onChange = { v -> update { s -> s.copy(showTeacher = v) } },
                        text = "显示授课老师"
                    )
                    ItemDivider()
                    ItemSwitcher(
                        state = settings.showClassroom,
                        onChange = { v -> update { s -> s.copy(showClassroom = v) } },
                        text = "显示上课地点"
                    )
                    ItemDivider()
                    ItemSwitcher(
                        state = settings.classroomAtPrefix,
                        onChange = { v -> update { s -> s.copy(classroomAtPrefix = v) } },
                        enabled = settings.showClassroom,
                        text = "地点前加「@」"
                    )
                }

                /*ItemTip(
                    text = "课表外观是**全局设置**，所有课表共用。" +
                        "「显示替换」是课表专属的，在课表页「⋯ → 课表设置」里调整。"
                )*/
            }
        }
    }
}

/**
 * 仅供「课表外观」预览的样例课程：不进入任何存档，
 * 也不参与日程页/课表页数据（正式数据为空时预览才出现）。
 *
 * @param weekCount 学期周数（周次覆盖全部教学周，保证任意当前周都有内容）
 */
private fun previewSampleCourses(weekCount: Int): List<Course> {
    val today = currentLocalDate()
    val allWeeks = (0 until weekCount).toList()
    val todayIndex = dayIndexOf(today)
    return listOf(
        Course(
            name = "示例课程甲",
            classroom = "雨母校区 A101",
            teacher = "张老师",
            weekIndices = allWeeks,
            dayIndex = todayIndex,
            lessonStartIndex = 1,
            lessonCount = 2
        ),
        Course(
            name = "示例课程乙",
            classroom = "红湘校区 B203",
            teacher = "李老师",
            weekIndices = allWeeks,
            dayIndex = todayIndex,
            lessonStartIndex = 5,
            lessonCount = 2
        ),
        Course(
            name = "示例课程丙",
            classroom = "雨母校区 C105",
            teacher = "王老师",
            weekIndices = allWeeks,
            dayIndex = (todayIndex + 1) % 7,
            lessonStartIndex = 3,
            lessonCount = 2
        )
    )
}
