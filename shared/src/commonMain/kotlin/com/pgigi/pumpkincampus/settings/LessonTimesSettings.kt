package com.pgigi.pumpkincampus.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moriafly.salt.ui.Button
import com.moriafly.salt.ui.ButtonAppearance
import com.moriafly.salt.ui.Icon
import com.moriafly.salt.ui.ItemDivider
import com.moriafly.salt.ui.ItemEdit
import com.moriafly.salt.ui.ItemOuterTextButton
import com.moriafly.salt.ui.ItemOuterTitle
import com.moriafly.salt.ui.ItemTip
import com.moriafly.salt.ui.RoundedColumn
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.Text
import com.moriafly.salt.ui.UnstableSaltUiApi
import com.moriafly.salt.ui.dialog.BasicDialog
import com.moriafly.salt.ui.dialog.DialogTitle
import com.moriafly.salt.ui.dialog.YesNoDialog
import com.moriafly.salt.ui.icons.Back
import com.moriafly.salt.ui.icons.ChevronRight
import com.moriafly.salt.ui.icons.SaltIcons
import com.moriafly.salt.ui.screen.BasicScreen
import com.moriafly.salt.ui.screen.TitleBarButton
import com.pgigi.pumpkincampus.SampleLessonTimes
import com.pgigi.pumpkincampus.components.AddIcon
import com.pgigi.pumpkincampus.components.WheelPickerColumn
import com.pgigi.pumpkincampus.models.AppSettings
import com.pgigi.pumpkincampus.models.LessonTime
import com.pgigi.pumpkincampus.models.LessonTimetable
import com.pgigi.pumpkincampus.schedule.formatClockMinutes
import com.pgigi.pumpkincampus.schedule.parseClockMinutes
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/** 时间点编辑目标：第几节 + 改开始还是结束。 */
private data class PickTarget(val slotIndex: Int, val isStart: Boolean)

/**
 * 「时间表」页面的两种用法：
 * - [Schedule]：某个课表的上课时间（候选时间表里「来自全局默认」与「本课表自建」分组展示）
 * - [Defaults]：「设置 → 全局课表设置 → 上课时间表」，编辑的是**新建课表的默认值**
 */
internal enum class TimetableScope { Schedule, Defaults }

/**
 * 「上课时间」子页（NavDisplay 推送）：
 * - 内置默认作息 + 候选时间表列表
 * - 点时间表 → [TimetableEditPage] 编辑；点「启用」切换当前作息
 * - 右上「+」新建时间表（复制当前作息为底稿）
 *
 * @param scope 见 [TimetableScope]：课表专属页会把「默认时间表」与「本课表自建」分开列
 */
@OptIn(UnstableSaltUiApi::class, ExperimentalTime::class)
@Composable
internal fun LessonTimesPage(
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
    onBack: () -> Unit,
    onOpenTimetable: (String) -> Unit,
    scope: TimetableScope = TimetableScope.Schedule
) {
    var showNewDialog by remember { mutableStateOf(false) }
    val isDefaults = scope == TimetableScope.Defaults

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
        title = if (isDefaults) "默认上课时间表" else "上课时间",
        subtitle = if (isDefaults) {
            "新建课表的默认作息"
        } else {
            "候选时间表 · 多余节次自动忽略"
        },
        toolButtons = {
            TitleBarButton(
                onClick = { showNewDialog = true },
                icon = {
                    Icon(
                        painter = AddIcon,
                        contentDescription = "新建时间表",
                        tint = SaltTheme.colors.text
                    )
                }
            )
        }
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = contentPadding.calculateTopPadding())
                .padding(bottom = contentPadding.calculateBottomPadding() + 24.dp)
        ) {
            ItemOuterTitle(text = "内置")
            RoundedColumn {
                TimetableRow(
                    name = "默认作息",
                    slotCount = SampleLessonTimes.size,
                    active = settings.activeTimetableId == null,
                    onClick = { onSettingsChange(settings.copy(activeTimetableId = null)) },
                    onActivate = { onSettingsChange(settings.copy(activeTimetableId = null)) }
                )
            }

            // 来自「设置 → 全局课表设置」的默认时间表（新建本课表时快照进来的）
            val fromDefaults = settings.timetables.filter { it.fromDefaults }
            val custom = settings.timetables.filterNot { it.fromDefaults }

            if (isDefaults) {
                if (settings.timetables.isNotEmpty()) {
                    ItemOuterTitle(text = "默认时间表")
                    RoundedColumn {
                        TimetableRows(
                            timetables = settings.timetables,
                            settings = settings,
                            onSettingsChange = onSettingsChange,
                            onOpenTimetable = onOpenTimetable,
                            isDefaults = true
                        )
                    }
                }
            } else {
                if (fromDefaults.isNotEmpty()) {
                    ItemOuterTitle(text = "默认时间表（来自全局课表设置）")
                    RoundedColumn {
                        TimetableRows(
                            timetables = fromDefaults,
                            settings = settings,
                            onSettingsChange = onSettingsChange,
                            onOpenTimetable = onOpenTimetable
                        )
                    }
                }
                if (custom.isNotEmpty()) {
                    ItemOuterTitle(text = "本课表自定义时间表")
                    RoundedColumn {
                        TimetableRows(
                            timetables = custom,
                            settings = settings,
                            onSettingsChange = onSettingsChange,
                            onOpenTimetable = onOpenTimetable
                        )
                    }
                }
            }

            ItemOuterTextButton(
                text = "新建时间表",
                onClick = { showNewDialog = true }
            )

            ItemTip(
                text = if (isDefaults) {
                    "这里的时间表是**新建课表**时的默认作息（最少保留一张）；" +
                        "已经建好的课表不会跟着变，要改它们请到课表页「⋯ → 课表设置 → 上课时间」。"
                } else {
                    "「默认时间表」是新建本课表时从「设置 → 全局课表设置」复制来的，" +
                        "在这里改名/改时间/删除都只影响本课表；「本课表自定义时间表」是本课表里新建的。" +
                        "点「启用」切换当前作息，课表只显示「一天课程节数」内的节次。"
                }
            )
        }
    }

    if (showNewDialog) {
        NewTimetableDialog(
            defaultName = "时间表${settings.timetables.size + 1}",
            onCreate = { name ->
                val id = "tt${Clock.System.now().toEpochMilliseconds()}"
                onSettingsChange(
                    settings.copy(
                        timetables = settings.timetables + LessonTimetable(
                            id = id,
                            name = name,
                            // 复制当前作息作为底稿，之后按需增删节次
                            slots = settings.activeLessonTimes(),
                            // 默认值页里新建的也是默认时间表；课表里新建的是本课表自建
                            fromDefaults = isDefaults
                        ),
                        activeTimetableId = id
                    )
                )
                showNewDialog = false
                onOpenTimetable(id)
            },
            onDismissRequest = { showNewDialog = false }
        )
    }
}

/** 时间表行列表（同一分组内带分隔线）。 */
@Composable
private fun TimetableRows(
    timetables: List<LessonTimetable>,
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
    onOpenTimetable: (String) -> Unit,
    isDefaults: Boolean = false
) {
    timetables.forEachIndexed { index, timetable ->
        TimetableRow(
            name = timetable.name,
            slotCount = timetable.slots.size,
            active = settings.activeTimetableId == timetable.id,
            onClick = { onOpenTimetable(timetable.id) },
            onActivate = {
                onSettingsChange(settings.copy(activeTimetableId = timetable.id))
            },
            // 全局默认值页里「启用」的含义是「新建课表默认用哪张」
            activeLabel = if (isDefaults) "✓ 新建课表默认" else "✓ 使用中",
            activateLabel = if (isDefaults) "设为默认" else "启用"
        )
        if (index != timetables.lastIndex) {
            ItemDivider()
        }
    }
}

/**
 * 时间表编辑子页（NavDisplay 推送）：
 * 改名、逐节调整起止时间（时间点弹层滚轮选择）、增删节次、设为当前、删除（二次确认）。
 *
 * @param canDelete 是否允许删除；全局默认值页在只剩一张时传 false（最少保留一张）
 */
@OptIn(UnstableSaltUiApi::class)
@Composable
internal fun TimetableEditPage(
    timetableId: String,
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
    onBack: () -> Unit,
    canDelete: Boolean = true
) {
    val index = settings.timetables.indexOfFirst { it.id == timetableId }
    val timetable = settings.timetables.getOrNull(index)

    var pickTarget by remember { mutableStateOf<PickTarget?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    // 时间表刚被删除时的占位（正常路径由删除按钮先弹栈）
    if (timetable == null) {
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
            title = "时间表"
        ) { contentPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = contentPadding.calculateTopPadding()),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "时间表不存在或已删除",
                    color = SaltTheme.colors.subText
                )
            }
        }
        return
    }

    val active = settings.activeTimetableId == timetable.id

    /** 用编辑后的整表替换列表中的当前项（可顺带切换启用状态）。 */
    fun replaceTimetable(
        newTimetable: LessonTimetable,
        newActiveId: String? = settings.activeTimetableId
    ) {
        onSettingsChange(
            settings.copy(
                timetables = settings.timetables.mapIndexed { i, t ->
                    if (i == index) newTimetable else t
                },
                activeTimetableId = newActiveId
            )
        )
    }

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
        title = timetable.name,
        subtitle = "${timetable.slots.size} 节" + if (active) " · 使用中" else ""
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = contentPadding.calculateTopPadding())
                .padding(bottom = contentPadding.calculateBottomPadding() + 24.dp)
        ) {
            ItemOuterTitle(text = "名称")
            RoundedColumn {
                ItemEdit(
                    text = timetable.name,
                    onChange = { newName ->
                        replaceTimetable(timetable.copy(name = newName))
                    },
                    hint = "时间表名称"
                )
            }

            ItemOuterTitle(text = "节次时间（共 ${timetable.slots.size} 节）")
            RoundedColumn {
                if (timetable.slots.isEmpty()) {
                    Text(
                        text = "暂无节次，点下方「添加一节」",
                        fontSize = SaltTheme.textStyles.sub.fontSize,
                        color = SaltTheme.colors.subText,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = SaltTheme.dimens.padding,
                                vertical = 12.dp
                            )
                    )
                }
                timetable.slots.forEachIndexed { i, slot ->
                    SlotRow(
                        index = i,
                        slot = slot,
                        onStartClick = { pickTarget = PickTarget(i, isStart = true) },
                        onEndClick = { pickTarget = PickTarget(i, isStart = false) },
                        onDelete = {
                            replaceTimetable(
                                timetable.copy(
                                    slots = timetable.slots.filterIndexed { si, _ -> si != i }
                                )
                            )
                        }
                    )
                    if (i != timetable.slots.lastIndex) {
                        ItemDivider()
                    }
                }
                // 添加一节：接在最后一节后面（+10 分钟课间、默认 45 分钟）
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val lastEnd = timetable.slots.lastOrNull()
                                ?.let { parseClockMinutes(it.end) }
                            val startMin = lastEnd?.plus(10) ?: (8 * 60)
                            val endMin = (startMin + 45).coerceAtMost(24 * 60 - 1)
                            replaceTimetable(
                                timetable.copy(
                                    slots = timetable.slots + LessonTime(
                                        start = formatClockMinutes(startMin),
                                        end = formatClockMinutes(endMin)
                                    )
                                )
                            )
                        }
                        .padding(
                            horizontal = SaltTheme.dimens.padding,
                            vertical = 14.dp
                        ),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "＋ 添加一节",
                        fontSize = SaltTheme.textStyles.main.fontSize,
                        color = SaltTheme.colors.highlight
                    )
                }
            }
            ItemTip(
                text = "课表按「一天课程节数」显示行数；此表多余的节次自动忽略，节数可任意增删。"
            )

            ItemOuterTitle(text = "启用")
            RoundedColumn {
                if (active) {
                    Text(
                        text = "✓ 当前正在使用此时间表",
                        fontSize = SaltTheme.textStyles.main.fontSize,
                        color = SaltTheme.colors.highlight,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = SaltTheme.dimens.padding,
                                vertical = 14.dp
                            )
                    )
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { replaceTimetable(timetable, newActiveId = timetable.id) }
                            .padding(
                                horizontal = SaltTheme.dimens.padding,
                                vertical = 14.dp
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "设为当前时间表",
                            fontSize = SaltTheme.textStyles.main.fontSize,
                            color = SaltTheme.colors.text,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = SaltIcons.ChevronRight,
                            contentDescription = null,
                            modifier = Modifier.width(16.dp),
                            tint = SaltTheme.colors.highlight
                        )
                    }
                }
            }

            ItemOuterTextButton(
                text = "删除时间表",
                textColor = if (canDelete) SaltTheme.colors.error else SaltTheme.colors.subText,
                onClick = { if (canDelete) confirmDelete = true }
            )
            if (!canDelete) {
                ItemTip(
                    text = "默认时间表至少要保留一张：先「新建时间表」，再删除这张。"
                )
            }
        }
    }

    // 时间点选择（开始/结束）
    pickTarget?.let { target ->
        val slot = timetable.slots.getOrNull(target.slotIndex)
        if (slot != null) {
            val startMinute = parseClockMinutes(slot.start) ?: 0
            val endMinute = parseClockMinutes(slot.end) ?: (startMinute + 45)
            val initialMinute = if (target.isStart) startMinute else endMinute
            // 约束：开始必须 < 结束，结束必须 > 开始
            val minMinute = if (target.isStart) 0 else startMinute + 1
            val maxMinute = if (target.isStart) (endMinute - 1).coerceAtLeast(0)
            else (24 * 60 - 1)

            SlotTimePickerDialog(
                title = "第 ${target.slotIndex + 1} 节 " +
                    if (target.isStart) "开始时间" else "结束时间",
                initialMinute = initialMinute,
                minMinute = minMinute,
                maxMinute = maxMinute,
                onPick = { minute ->
                    val newSlots = timetable.slots.mapIndexed { si, s ->
                        when {
                            si != target.slotIndex -> s
                            target.isStart -> s.copy(start = formatClockMinutes(minute))
                            else -> s.copy(end = formatClockMinutes(minute))
                        }
                    }
                    replaceTimetable(timetable.copy(slots = newSlots))
                    pickTarget = null
                },
                onDismissRequest = { pickTarget = null }
            )
        }
    }

    // 删除二次确认
    if (confirmDelete) {
        YesNoDialog(
            onDismissRequest = { confirmDelete = false },
            onConfirm = {
                confirmDelete = false
                onSettingsChange(
                    settings.copy(
                        timetables = settings.timetables.filterNot { it.id == timetable.id },
                        activeTimetableId = if (active) null
                        else settings.activeTimetableId
                    )
                )
                onBack()
            },
            title = "删除时间表",
            content = "确定要删除「${timetable.name}」吗？删除后不可恢复。",
            cancelText = "取消",
            confirmText = "删除"
        )
    }
}

/* ------------------------------------------------------------------ */
/* 小部件                                                              */
/* ------------------------------------------------------------------ */

/** 时间表行：名称 + 节数 + 「启用」/「✓ 使用中」；主区域点击进入编辑。 */
@Composable
private fun TimetableRow(
    name: String,
    slotCount: Int,
    active: Boolean,
    onClick: () -> Unit,
    onActivate: () -> Unit,
    activeLabel: String = "✓ 使用中",
    activateLabel: String = "启用"
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = SaltTheme.dimens.padding, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                fontSize = SaltTheme.textStyles.main.fontSize,
                color = SaltTheme.colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "$slotCount 节",
                fontSize = SaltTheme.textStyles.sub.fontSize,
                color = SaltTheme.colors.subText
            )
        }
        if (active) {
            Text(
                text = activeLabel,
                fontSize = SaltTheme.textStyles.sub.fontSize,
                fontWeight = FontWeight.Medium,
                color = SaltTheme.colors.highlight
            )
        } else {
            Text(
                text = activateLabel,
                fontSize = SaltTheme.textStyles.sub.fontSize,
                color = SaltTheme.colors.highlight,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(onClick = onActivate)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
    }
}

/** 节次行：第 N 节 + 开始时间块 + 结束时间块 + 删除。 */
@Composable
private fun SlotRow(
    index: Int,
    slot: LessonTime,
    onStartClick: () -> Unit,
    onEndClick: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SaltTheme.dimens.padding, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "第 ${index + 1} 节",
            fontSize = SaltTheme.textStyles.main.fontSize,
            color = SaltTheme.colors.text,
            modifier = Modifier.weight(1f)
        )
        TimeChip(text = slot.start, onClick = onStartClick)
        Text(
            text = " – ",
            fontSize = SaltTheme.textStyles.main.fontSize,
            color = SaltTheme.colors.subText
        )
        TimeChip(text = slot.end, onClick = onEndClick)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "删除",
            fontSize = SaltTheme.textStyles.sub.fontSize,
            color = SaltTheme.colors.error,
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .clickable(onClick = onDelete)
                .padding(6.dp)
        )
    }
}

/** 可点击时间块（点击弹出时间选择器）。 */
@Composable
private fun TimeChip(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(SaltTheme.colors.subBackground)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            fontSize = SaltTheme.textStyles.main.fontSize,
            fontWeight = FontWeight.Medium,
            color = SaltTheme.colors.highlight
        )
    }
}

/** 新建时间表弹层：名称输入（默认复制当前作息为底稿）。 */
@Composable
private fun NewTimetableDialog(
    defaultName: String,
    onCreate: (String) -> Unit,
    onDismissRequest: () -> Unit
) {
    var name by remember { mutableStateOf("") }

    BasicDialog(onDismissRequest = onDismissRequest) {
        DialogTitle(text = "新建时间表")
        Text(
            text = "以当前作息为底稿创建新的候选时间表，创建后进入编辑。",
            fontSize = SaltTheme.textStyles.sub.fontSize,
            color = SaltTheme.colors.subText,
            lineHeight = 16.sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SaltTheme.dimens.padding, vertical = 4.dp)
        )
        BasicTextField(
            value = name,
            onValueChange = { name = it },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SaltTheme.dimens.padding),
            textStyle = TextStyle(
                fontSize = SaltTheme.textStyles.main.fontSize,
                color = SaltTheme.colors.text
            ),
            cursorBrush = SolidColor(SaltTheme.colors.highlight),
            decorationBox = { inner ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SaltTheme.colors.subBackground, RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 10.dp)
                ) {
                    if (name.isEmpty()) {
                        Text(
                            text = defaultName,
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
                text = "取消",
                // 取消类按钮统一低强调（非高亮色）
                appearance = ButtonAppearance.Subtle,
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = { onCreate(name.ifBlank { defaultName }) },
                text = "创建",
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/** 允许的小时档位（由允许的分钟区间推出）。 */
internal fun pickerHourRange(minMinute: Int, maxMinute: Int): IntRange =
    (minMinute / 60)..(maxMinute / 60)

/**
 * 某小时下允许的分钟区间：首/末小时会被 [minMinute] / [maxMinute] 裁掉一部分。
 *
 * 例：允许 08:00–09:30 时，8 点可 0..59，9 点只能 0..30。
 * 区间永远非空（滚轮的候选列表不能为空）。
 */
internal fun pickerMinuteRange(minMinute: Int, maxMinute: Int, hour: Int): IntRange {
    val start = (minMinute - hour * 60).coerceAtLeast(0)
    val end = (maxMinute - hour * 60).coerceAtMost(59)
    return start..end.coerceAtLeast(start)
}

/**
 * 时间点选择弹层：大号 HH:mm 预览 + 小时/分钟两个**滚轮**（NumberPicker 风格）。
 *
 * 滚轮的候选区间按 [minMinute] / [maxMinute] 收紧（如「开始必须早于结束」）：
 * 小时只能选允许的那几档，分钟只列出当前小时下允许的值，所以滚不出非法时间。
 *
 * @param minMinute 最小分钟数（含，如「开始必须早于结束」时约束）
 * @param maxMinute 最大分钟数（含）
 */
@Composable
private fun SlotTimePickerDialog(
    title: String,
    initialMinute: Int,
    minMinute: Int,
    maxMinute: Int,
    onPick: (Int) -> Unit,
    onDismissRequest: () -> Unit
) {
    var minute by remember(initialMinute, minMinute, maxMinute) {
        mutableStateOf(initialMinute.coerceIn(minMinute, maxMinute))
    }
    val hour = minute / 60
    val minuteOfHour = minute % 60

    // 小时候选：minMinute 所在小时 ~ maxMinute 所在小时
    val hourRange = pickerHourRange(minMinute, maxMinute)
    val hours = remember(hourRange) {
        hourRange.map { it.toString().padStart(2, '0') }
    }
    // 分钟候选：随当前小时变化（首/末小时会被裁掉一部分）
    val minuteRange = pickerMinuteRange(minMinute, maxMinute, hour)
    val minutes = remember(minuteRange) {
        minuteRange.map { it.toString().padStart(2, '0') }
    }

    BasicDialog(onDismissRequest = onDismissRequest) {
        DialogTitle(text = title)
        Text(
            text = "${hour.toString().padStart(2, '0')}:" +
                minuteOfHour.toString().padStart(2, '0'),
            fontSize = SaltTheme.textStyles.largeTitle.fontSize,
            fontWeight = FontWeight.Bold,
            color = SaltTheme.colors.highlight,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SaltTheme.dimens.padding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            WheelPickerColumn(
                items = hours,
                selectedIndex = hour - hourRange.first,
                onSelectedIndexChange = { index ->
                    val picked = hourRange.first + index
                    minute = (picked * 60 + minuteOfHour).coerceIn(minMinute, maxMinute)
                },
                label = "小时",
                modifier = Modifier.weight(1f)
            )
            Text(
                text = ":",
                fontSize = SaltTheme.textStyles.largeTitle.fontSize,
                fontWeight = FontWeight.Bold,
                color = SaltTheme.colors.subText
            )
            WheelPickerColumn(
                items = minutes,
                selectedIndex = (minuteOfHour - minuteRange.first).coerceIn(0, minutes.lastIndex),
                onSelectedIndexChange = { index ->
                    minute = (hour * 60 + (minuteRange.first + index)).coerceIn(minMinute, maxMinute)
                },
                label = "分钟",
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)
        ) {
            Button(
                onClick = onDismissRequest,
                text = "取消",
                // 取消类按钮统一低强调（非高亮色）
                appearance = ButtonAppearance.Subtle,
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = { onPick(minute) },
                text = "确定",
                modifier = Modifier.weight(1f)
            )
        }
    }
}
