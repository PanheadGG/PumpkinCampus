package com.pgigi.pumpkincampus.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moriafly.salt.ui.Button
import com.moriafly.salt.ui.ButtonAppearance
import com.moriafly.salt.ui.Icon
import com.moriafly.salt.ui.ItemDivider
import com.moriafly.salt.ui.ItemTip
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.Text
import com.moriafly.salt.ui.UnstableSaltUiApi
import com.moriafly.salt.ui.dialog.BasicDialog
import com.moriafly.salt.ui.dialog.DialogTitle
import com.moriafly.salt.ui.dialog.YesNoDialog
import com.moriafly.salt.ui.pager.PagerState
import com.pgigi.pumpkincampus.icons.MaterialIcons
import com.pgigi.pumpkincampus.icons.material.CalendarMonth
import com.pgigi.pumpkincampus.icons.material.Schedule
import com.pgigi.pumpkincampus.models.AppSettings
import com.pgigi.pumpkincampus.models.CourseSchedule
import com.pgigi.pumpkincampus.settings.DatePickerDialog
import kotlinx.datetime.number
import kotlin.math.roundToInt

/**
 * 课表页「⋯」弹层：
 * - **第一行**：滑块快速跳转周数（拖动即让课表 pager 动画跳到对应教学周，
 *   右侧显示「第 N 周 · M月D日 起」）
 * - **第二行**：课表管理——Chip 列出全部课表并高亮当前课表，点选切换；
 *   行尾依次是重命名 / 删除（当前课表）与最右边的**课表设置**文字按钮；
 *   下方「＋ 新建课表」
 * - **快捷按钮（图标 + 文字）**：`开课日期`（CalendarMonth 图标，弹日期选择器，
 *   点「确定」才生效）、`课表时间`（Schedule 图标，直接 push 当前课表的上课时间子页）
 *
 * 自定义课程按课表隔离：切换课表后课表页只显示该课表自己的课程，互不相通。
 *
 * @param pagerState 课表周 pager 状态（快速跳转用）
 * @param weekCount 学期周数（滑块范围）
 * @param weekCalculator 教学周计算器（显示周起始日期，可空）
 * @param schedules 全部课表
 * @param activeScheduleId 当前课表 id（Chip 高亮）
 * @param settings 当前课表的生效设置（快捷按钮读写）
 * @param onSettingsChange 写回当前课表的专属设置
 * @param onOpenScheduleSettings push「课表设置」子页（调用方负责关闭本弹层）
 * @param onOpenLessonTimes push「上课时间」子页（调用方负责关闭本弹层）
 */
@OptIn(ExperimentalMaterial3Api::class, UnstableSaltUiApi::class)
@Composable
internal fun TimetableMenuSheet(
    pagerState: PagerState,
    weekCount: Int,
    weekCalculator: WeekCalculator?,
    schedules: List<CourseSchedule>,
    activeScheduleId: String,
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
    onSwitchSchedule: (String) -> Unit,
    onCreateSchedule: (String) -> Unit,
    onRenameSchedule: (String, String) -> Unit,
    onDeleteSchedule: (String) -> Unit,
    onOpenScheduleSettings: () -> Unit,
    onOpenLessonTimes: () -> Unit,
    onDismissRequest: () -> Unit
) {
    val activeSchedule = schedules.firstOrNull { it.id == activeScheduleId }

    var showCreateDialog by remember { mutableStateOf(false) }
    var showTermPicker by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<CourseSchedule?>(null) }
    var deleteTarget by remember { mutableStateOf<CourseSchedule?>(null) }

    // 当前课表的「第一周的第一天」锚点（快捷按钮显示与选择器用）
    val termAnchor = remember(settings.termStart) {
        weekCalculatorOf(settings.termStart).getWeekFirstDay(1)
    }

    // 滑块拖动中的目标周；null = 跟随 pager 当前页（左右滑动页时滑块同步）
    var jumpWeek by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(jumpWeek) {
        val target = jumpWeek ?: return@LaunchedEffect
        pagerState.animateScrollToPage(
            (target - 1).coerceIn(0, (weekCount - 1).coerceAtLeast(0))
        )
        jumpWeek = null
    }
    val safeWeekCount = weekCount.coerceAtLeast(1)
    val shownWeek = (jumpWeek ?: (pagerState.currentPage + 1)).coerceIn(1, safeWeekCount)

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        containerColor = SaltTheme.colors.popup,
        shape = RoundedCornerShape(
            topStart = CourseSheetCornerRadius,
            topEnd = CourseSheetCornerRadius
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            /* ————— 第一行：快速跳转周数 ————— */
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SaltTheme.dimens.padding, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "快速跳转周数",
                    fontSize = SaltTheme.textStyles.main.fontSize,
                    color = SaltTheme.colors.text,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "第 $shownWeek 周" + weekStartLabel(weekCalculator, shownWeek),
                    fontSize = SaltTheme.textStyles.sub.fontSize,
                    color = SaltTheme.colors.highlight
                )
            }
            Slider(
                value = shownWeek.toFloat(),
                onValueChange = { value ->
                    jumpWeek = value.roundToInt().coerceIn(1, safeWeekCount)
                },
                valueRange = 1f..safeWeekCount.toFloat(),
                steps = (safeWeekCount - 2).coerceAtLeast(0),
                colors = menuSliderColors(),
                modifier = Modifier.padding(horizontal = SaltTheme.dimens.padding)
            )

            ItemDivider()

            /* ————— 第二行：课表管理 ————— */
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SaltTheme.dimens.padding, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "课表管理",
                    fontSize = SaltTheme.textStyles.main.fontSize,
                    color = SaltTheme.colors.text,
                    modifier = Modifier.weight(1f)
                )
                if (activeSchedule != null) {
                    Text(
                        text = "重命名",
                        fontSize = SaltTheme.textStyles.sub.fontSize,
                        color = SaltTheme.colors.highlight,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { renameTarget = activeSchedule }
                            .padding(vertical = 4.dp)
                    )
                    // 仅剩一套课表时不允许删除（隐藏入口）
                    if (schedules.size > 1) {
                        Text(
                            text = "删除",
                            fontSize = SaltTheme.textStyles.sub.fontSize,
                            color = SaltTheme.colors.error,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .clickable { deleteTarget = activeSchedule }
                                .padding(vertical = 4.dp)
                        )
                    }
                }
                // 行最右边：课表设置（仅对当前课表生效的设置集合）
                Text(
                    text = "课表设置",
                    fontSize = SaltTheme.textStyles.sub.fontSize,
                    color = SaltTheme.colors.highlight,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable(onClick = onOpenScheduleSettings)
                        .padding(vertical = 4.dp)
                )
            }
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SaltTheme.dimens.padding),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                schedules.forEach { schedule ->
                    ScheduleChip(
                        name = schedule.name,
                        selected = schedule.id == activeScheduleId,
                        onClick = { onSwitchSchedule(schedule.id) }
                    )
                }
                // 新建课表（创建后自动切换过去）
                ScheduleChip(
                    name = "＋ 新建课表",
                    selected = false,
                    onClick = { showCreateDialog = true }
                )
            }

            /* ————— 快捷按钮（图标 + 文字）：开课日期 / 课表时间 ————— */
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SaltTheme.dimens.padding, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ScheduleChip(
                    name = "开课日期",
                    selected = false,
                    onClick = { showTermPicker = true },
                    icon = MaterialIcons.CalendarMonth
                )
                ScheduleChip(
                    name = "课表时间",
                    selected = false,
                    onClick = onOpenLessonTimes,
                    icon = MaterialIcons.Schedule
                )
            }

            ItemTip(
                text = "自定义课程只属于所属课表，不同课表之间互不相通；" +
                    "切换后课表页与日程页只显示该课表的课程。"
            )

            Spacer(modifier = Modifier.height(SaltTheme.dimens.padding))
        }
    }

    // 快捷：第一周的第一天（就地修改当前课表的 termStart）
    if (showTermPicker) {
        DatePickerDialog(
            selected = termAnchor,
            firstDayOfWeek = dayIndexOf(termAnchor),
            onPicked = { date ->
                onSettingsChange(settings.copy(termStart = date.toString()))
                showTermPicker = false
            },
            onDismissRequest = { showTermPicker = false }
        )
    }

    // 新建课表
    if (showCreateDialog) {
        ScheduleNameDialog(
            title = "新建课表",
            initialName = "",
            hint = "课表${schedules.size + 1}",
            onConfirm = { name ->
                onCreateSchedule(name)
                showCreateDialog = false
            },
            onDismissRequest = { showCreateDialog = false }
        )
    }

    // 重命名课表
    renameTarget?.let { target ->
        ScheduleNameDialog(
            title = "重命名课表",
            initialName = target.name,
            hint = target.name,
            onConfirm = { name ->
                onRenameSchedule(target.id, name)
                renameTarget = null
            },
            onDismissRequest = { renameTarget = null }
        )
    }

    // 删除课表（二次确认，连同其内课程一并删除）
    deleteTarget?.let { target ->
        YesNoDialog(
            onDismissRequest = { deleteTarget = null },
            onConfirm = {
                deleteTarget = null
                onDeleteSchedule(target.id)
            },
            title = "删除课表",
            content = "确定要删除「${target.name}」吗？其中的 ${target.courses.size} 门课程" +
                "将一并删除，且不同步到其他课表。删除后不可恢复。",
            cancelText = "取消",
            confirmText = "删除"
        )
    }
}

/** 周起始日期标签：` · 9月28日 起`（无计算器返回空串）。 */
private fun weekStartLabel(weekCalculator: WeekCalculator?, week: Int): String {
    val first = weekCalculator?.getWeekFirstDay(week) ?: return ""
    return " · ${first.month.number}月${first.day}日 起"
}

/** 课表管理 / 快捷操作 Chip：胶囊形，当前课表用高亮色，其余用弱背景；可选前置图标。 */
@Composable
private fun ScheduleChip(
    name: String,
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector? = null
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(
                if (selected) SaltTheme.colors.highlight
                else SaltTheme.colors.subBackground
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (selected) SaltTheme.colors.onHighlight
                    else SaltTheme.colors.subText,
                    modifier = Modifier.size(15.dp)
                )
            }
            Text(
                text = name,
                fontSize = SaltTheme.textStyles.sub.fontSize,
                color = if (selected) SaltTheme.colors.onHighlight
                else SaltTheme.colors.subText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 160.dp)
            )
        }
    }
}

/** 新建 / 重命名 / 导入课表的名称输入弹层（课表设置子页、导入流程也会复用）。 */
@Composable
internal fun ScheduleNameDialog(
    title: String,
    initialName: String,
    hint: String,
    cancelText: String = "取消",
    confirmText: String = "确定",
    onConfirm: (String) -> Unit,
    onDismissRequest: () -> Unit
) {
    var name by remember { mutableStateOf(initialName) }

    BasicDialog(onDismissRequest = onDismissRequest) {
        DialogTitle(text = title)
        BasicTextField(
            value = name,
            onValueChange = { name = it },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SaltTheme.dimens.padding, vertical = 4.dp),
            textStyle = TextStyle(
                fontSize = SaltTheme.textStyles.main.fontSize,
                color = SaltTheme.colors.text
            ),
            cursorBrush = SolidColor(SaltTheme.colors.highlight),
            decorationBox = { inner ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            SaltTheme.colors.subBackground,
                            RoundedCornerShape(10.dp)
                        )
                        .padding(horizontal = 10.dp, vertical = 10.dp)
                ) {
                    if (name.isEmpty()) {
                        Text(
                            text = hint,
                            fontSize = SaltTheme.textStyles.main.fontSize,
                            color = SaltTheme.colors.subText
                        )
                    }
                    inner()
                }
            }
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)
        ) {
            Button(
                onClick = onDismissRequest,
                text = cancelText,
                // 取消类按钮统一低强调（非高亮色）
                appearance = ButtonAppearance.Subtle,
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = { onConfirm(name) },
                text = confirmText,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun menuSliderColors() = SliderDefaults.colors(
    thumbColor = SaltTheme.colors.highlight,
    activeTrackColor = SaltTheme.colors.highlight,
    inactiveTrackColor = SaltTheme.colors.stroke
)
