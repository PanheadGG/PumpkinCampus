package com.pgigi.pumpkincampus.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.moriafly.salt.ui.screen.BasicScreen
import com.moriafly.salt.ui.screen.TitleBarButton
import com.pgigi.pumpkincampus.models.AppSettings
import com.pgigi.pumpkincampus.schedule.ScheduleNameDialog
import com.pgigi.pumpkincampus.schedule.dayIndexOf
import com.pgigi.pumpkincampus.schedule.weekCalculatorOf
import kotlinx.datetime.LocalDate
import kotlinx.datetime.number

/**
 * 「课表设置」子页（课表页 [androidx.navigation3.ui.NavDisplay] 推送）：
 * 第一周的第一天、辅助线、课表外观、上课时间、一天课程节数、学期周数——
 * **只对当前课表生效**（写入该课表的专属 [AppSettings]）。
 *
 * 设置页（设置 Tab）中的同名项是**默认值**：新建课表时快照为新课表的设置。
 *
 * @param settings 当前课表的生效设置
 * @param onSettingsChange 写回当前课表的专属设置
 * @param scheduleName 当前课表名称（重命名行的展示值）
 * @param onRenameSchedule 重命名当前课表（新名称）
 * @param onOpenAppearance push「课表外观」子页
 * @param onOpenLessonTimes push「上课时间」子页
 * @param onBack 弹栈
 */
@OptIn(UnstableSaltUiApi::class)
@Composable
internal fun ScheduleSettingsPage(
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
    scheduleName: String,
    onRenameSchedule: (String) -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenLessonTimes: () -> Unit,
    onBack: () -> Unit
) {
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showLessonCountPicker by remember { mutableStateOf(false) }
    var showWeekCountPicker by remember { mutableStateOf(false) }

    // 当前「第一周的第一天」锚点；其星期即每周的第一天
    val anchor = remember(settings.termStart) {
        weekCalculatorOf(settings.termStart).getWeekFirstDay(1)
    }
    val anchorLabel = "${anchor.year}年${anchor.month.number}月${anchor.day}日 " +
        "周${SettingsWeekLabels[dayIndexOf(anchor)]}"
    val timetableLabel = settings.activeTimetable()
        ?.let { "${it.name} · ${it.slots.size} 节" } ?: "默认作息"

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
        title = "课表设置",
        subtitle = "仅对当前课表生效"
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = contentPadding.calculateTopPadding())
                .padding(bottom = contentPadding.calculateBottomPadding() + 24.dp)
        ) {
            ItemOuterTitle(text = "课表")
            RoundedColumn {
                NavRow(
                    title = "重命名课表名称",
                    value = scheduleName,
                    onClick = { showRenameDialog = true }
                )
            }

            ItemOuterTitle(text = "周次与作息")
            RoundedColumn {
                NavRow(
                    title = "第一周的第一天",
                    value = anchorLabel,
                    onClick = { showDatePicker = true }
                )
                ItemDivider()
                NavRow(
                    title = "上课时间",
                    value = timetableLabel,
                    onClick = onOpenLessonTimes
                )
                ItemDivider()
                NavRow(
                    title = "一天课程节数",
                    value = "${settings.lessonCount} 节",
                    onClick = { showLessonCountPicker = true }
                )
                ItemDivider()
                NavRow(
                    title = "学期周数",
                    value = "${settings.semesterWeekCount} 周",
                    onClick = { showWeekCountPicker = true }
                )
            }

            ItemOuterTitle(text = "显示")
            RoundedColumn {
                ItemSwitcher(
                    state = settings.showGridLines,
                    onChange = { onSettingsChange(settings.copy(showGridLines = it)) },
                    text = "打开课表辅助线"
                )
                ItemDivider()
                NavRow(
                    title = "课表外观",
                    value = "预览与详细参数",
                    onClick = onOpenAppearance
                )
            }

            ItemTip(
                text = "「第一周的第一天」所选日期决定第 1 教学周从哪天开始，" +
                    "其星期即每周的第一天：选周日则周日起始，选周三则课表首列改为周三。"
            )
            ItemTip(
                text = "以上设置只对当前课表生效；设置页中的同名项为默认值，" +
                    "在新建课表时作为初始设置使用。"
            )
        }
    }

    // 重命名当前课表
    if (showRenameDialog) {
        ScheduleNameDialog(
            title = "重命名课表",
            initialName = scheduleName,
            hint = scheduleName,
            onConfirm = { name ->
                if (name.isNotBlank() && name != scheduleName) {
                    onRenameSchedule(name.trim())
                }
                showRenameDialog = false
            },
            onDismissRequest = { showRenameDialog = false }
        )
    }

    if (showDatePicker) {
        DatePickerDialog(
            selected = anchor,
            firstDayOfWeek = dayIndexOf(anchor),
            onPicked = { date ->
                onSettingsChange(settings.copy(termStart = date.toString()))
                showDatePicker = false
            },
            onDismissRequest = { showDatePicker = false }
        )
    }

    if (showLessonCountPicker) {
        IntPickerDialog(
            title = "一天课程节数",
            value = settings.lessonCount,
            range = 1..20,
            unit = "节",
            onPick = {
                onSettingsChange(settings.copy(lessonCount = it))
                showLessonCountPicker = false
            },
            onDismissRequest = { showLessonCountPicker = false }
        )
    }

    if (showWeekCountPicker) {
        IntPickerDialog(
            title = "学期周数",
            value = settings.semesterWeekCount,
            range = 1..30,
            unit = "周",
            onPick = {
                onSettingsChange(settings.copy(semesterWeekCount = it))
                showWeekCountPicker = false
            },
            onDismissRequest = { showWeekCountPicker = false }
        )
    }
}
