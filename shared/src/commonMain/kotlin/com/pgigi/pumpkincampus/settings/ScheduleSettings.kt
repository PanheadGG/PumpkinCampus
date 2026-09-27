package com.pgigi.pumpkincampus.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moriafly.salt.ui.Icon
import com.moriafly.salt.ui.ItemDivider
import com.moriafly.salt.ui.ItemOuterTextButton
import com.moriafly.salt.ui.ItemOuterTitle
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
import com.pgigi.pumpkincampus.models.DisplayReplace
import com.pgigi.pumpkincampus.plugin.PluginUiState
import com.pgigi.pumpkincampus.schedule.ScheduleNameDialog
import com.pgigi.pumpkincampus.schedule.dayIndexOf
import com.pgigi.pumpkincampus.schedule.weekCalculatorOf
import kotlinx.datetime.number

/**
 * 「课表设置」子页（课表页 [androidx.navigation3.ui.NavDisplay] 推送）：
 * 第一周的第一天、上课时间、一天课程节数、学期周数、显示替换——
 * **只对当前课表生效**（写入该课表的专属 [AppSettings]）。
 *
 * 「打开课表辅助线」与「课表外观」不在这里：它们是**全局显示项**（设置 Tab → 外观），
 * 所有课表共用，不随课表快照变化（见 [AppSettings.withGlobalDisplay]）。
 *
 * @param settings 当前课表的生效设置
 * @param onSettingsChange 写回当前课表的专属设置
 * @param scheduleName 当前课表名称（重命名行的展示值）
 * @param onRenameSchedule 重命名当前课表（新名称）
 * @param onOpenLessonTimes push「上课时间」子页
 * @param pluginUi 插件状态（当前课表绑定的教务系统插件展示值）
 * @param onOpenPlugin push「教务系统插件」子页（为当前课表选择插件/配置/同步）
 * @param onBack 弹栈
 */
@OptIn(UnstableSaltUiApi::class)
@Composable
internal fun ScheduleSettingsPage(
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
    scheduleName: String,
    onRenameSchedule: (String) -> Unit,
    onOpenLessonTimes: () -> Unit,
    pluginUi: PluginUiState? = null,
    onOpenPlugin: () -> Unit = {},
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
    // 当前启用的时间表；来自「全局课表设置」的默认时间表额外标注来源
    val timetableLabel = settings.activeTimetable()
        ?.let {
            "${it.name} · ${it.slots.size} 节" + if (it.fromDefaults) "（来自默认）" else ""
        } ?: "默认作息"

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
                ItemDivider()
                // 教务系统插件：一个课表只能选一个；选择/配置/同步都按课表隔离
                NavRow(
                    title = "教务系统插件",
                    value = when {
                        pluginUi?.selectedMissing == true -> "已卸载"
                        pluginUi?.selected != null -> pluginUi.selected!!.name
                        pluginUi != null -> "未选择"
                        else -> null
                    },
                    onClick = onOpenPlugin
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

            ItemOuterTitle(text = "显示替换")
            ReplaceRulesSection(
                replaces = settings.replaces,
                onChange = { onSettingsChange(settings.copy(replaces = it)) }
            )
            Text(
                text = "替换只作用于课表格子与日程卡片的显示；课程详情、编辑表单始终显示完整信息。" +
                    "替换规则只对当前课表生效。",
                fontSize = SaltTheme.textStyles.sub.fontSize,
                color = SaltTheme.colors.subText,
                lineHeight = 16.sp,
                modifier = Modifier.padding(
                    horizontal = SaltTheme.dimens.padding,
                    vertical = 6.dp
                )
            )

            ItemTip(
                text = "「第一周的第一天」所选日期决定第 1 教学周从哪天开始，" +
                    "其星期即每周的第一天：选周日则周日起始，选周三则课表首列改为周三。"
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

/**
 * 「显示替换」规则列表 + 「添加规则」按钮。
 *
 * 课表设置页与「设置 → 全局课表设置」共用（后者编辑的是新建课表的默认规则）。
 *
 * @param onChange 整份规则列表替换回调（增删改都走它）
 */
@Composable
internal fun ReplaceRulesSection(
    replaces: List<DisplayReplace>,
    onChange: (List<DisplayReplace>) -> Unit
) {
    RoundedColumn {
        if (replaces.isEmpty()) {
            Text(
                text = "暂无替换规则：可添加如「【雨母校区】」→「」隐藏校区",
                fontSize = SaltTheme.textStyles.sub.fontSize,
                color = SaltTheme.colors.subText,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = SaltTheme.dimens.padding,
                        vertical = 12.dp
                    )
            )
        } else {
            replaces.forEachIndexed { index, rule ->
                ReplaceRuleRow(
                    rule = rule,
                    onChange = { updated ->
                        onChange(replaces.mapIndexed { i, r -> if (i == index) updated else r })
                    },
                    onRemove = {
                        onChange(replaces.filterIndexed { i, _ -> i != index })
                    }
                )
                if (index != replaces.lastIndex) {
                    ItemDivider()
                }
            }
        }
    }
    ItemOuterTextButton(
        text = "添加规则",
        onClick = { onChange(replaces + DisplayReplace()) }
    )
}

/**
 * 单条替换规则：查找 → 替换为 + 删除。
 *
 * 「显示替换」是**课表专属**设置（原先在「课表外观」子页里，现在只在本页维护）。
 */
@Composable
private fun ReplaceRuleRow(
    rule: DisplayReplace,
    onChange: (DisplayReplace) -> Unit,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SaltTheme.dimens.padding, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RuleTextField(
            value = rule.from,
            hint = "查找",
            modifier = Modifier.weight(1f)
        ) {
            onChange(rule.copy(from = it))
        }
        Text(
            text = "→",
            fontSize = SaltTheme.textStyles.sub.fontSize,
            color = SaltTheme.colors.subText,
            modifier = Modifier.padding(horizontal = 6.dp)
        )
        RuleTextField(
            value = rule.to,
            hint = "替换为",
            modifier = Modifier.weight(1f)
        ) {
            onChange(rule.copy(to = it))
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = "删除",
            fontSize = SaltTheme.textStyles.sub.fontSize,
            color = SaltTheme.colors.error,
            modifier = Modifier
                .clickable(onClick = onRemove)
                .padding(4.dp)
        )
    }
}

/** 单行输入框（查找/替换文本）。 */
@Composable
private fun RuleTextField(
    value: String,
    hint: String,
    modifier: Modifier = Modifier,
    onChange: (String) -> Unit
) {
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        modifier = modifier,
        textStyle = TextStyle(
            fontSize = SaltTheme.textStyles.sub.fontSize,
            color = SaltTheme.colors.text
        ),
        cursorBrush = SolidColor(SaltTheme.colors.highlight),
        decorationBox = { inner ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SaltTheme.colors.popup, RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 8.dp)
            ) {
                if (value.isEmpty()) {
                    Text(
                        text = hint,
                        fontSize = SaltTheme.textStyles.sub.fontSize,
                        color = SaltTheme.colors.subText
                    )
                }
                inner()
            }
        }
    )
}
