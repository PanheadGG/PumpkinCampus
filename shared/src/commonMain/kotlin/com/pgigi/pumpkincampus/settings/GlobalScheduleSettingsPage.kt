package com.pgigi.pumpkincampus.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moriafly.salt.ui.Icon
import com.moriafly.salt.ui.ItemDivider
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
import com.pgigi.pumpkincampus.models.ScheduleDefaults

/**
 * 「设置 → 全局课表设置」子页：
 * 上课时间表、一天课程节数、学期周数、显示替换——**只作为新建课表时的默认值**。
 *
 * 已建立的课表不受这里影响（各自在课表页「⋯ → 课表设置」里维护）；
 * 分享/导入也不读这里：信封自带分享方的课表配置时按信封来。
 *
 * @param settings 全局设置（[AppSettings.defaults] 即本页编辑的内容）
 * @param onSettingsChange 写回全局设置
 * @param onOpenLessonTimes push「默认上课时间表」子页
 * @param onBack 弹栈
 */
@OptIn(UnstableSaltUiApi::class)
@Composable
internal fun GlobalScheduleSettingsPage(
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
    onOpenLessonTimes: () -> Unit,
    onBack: () -> Unit
) {
    val defaults = settings.defaults
    var showLessonCountPicker by remember { mutableStateOf(false) }
    var showWeekCountPicker by remember { mutableStateOf(false) }

    /** 只改默认值部分，其余全局项原样带回。 */
    fun updateDefaults(updated: ScheduleDefaults) {
        onSettingsChange(settings.copy(defaults = updated))
    }

    val timetableLabel = defaults.timetables
        .firstOrNull { it.id == defaults.activeTimetableId }
        ?.let { "${it.name} · ${it.slots.size} 节" }
        ?: if (defaults.timetables.isEmpty()) "内置默认作息" else "未指定（用内置默认作息）"

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
        title = "全局课表设置",
        subtitle = "仅作为新建课表的默认值"
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = contentPadding.calculateTopPadding())
                .padding(bottom = contentPadding.calculateBottomPadding() + 24.dp)
        ) {
            ItemOuterTitle(text = "新建课表默认值")
            RoundedColumn {
                NavRow(
                    title = "上课时间表",
                    value = timetableLabel,
                    onClick = onOpenLessonTimes
                )
                ItemDivider()
                NavRow(
                    title = "一天课程节数",
                    value = "${defaults.lessonCount} 节",
                    onClick = { showLessonCountPicker = true }
                )
                ItemDivider()
                NavRow(
                    title = "学期周数",
                    value = "${defaults.semesterWeekCount} 周",
                    onClick = { showWeekCountPicker = true }
                )
            }

            ItemOuterTitle(text = "显示替换（新建课表的默认规则）")
            ReplaceRulesSection(
                replaces = defaults.replaces,
                onChange = { updateDefaults(defaults.copy(replaces = it)) }
            )
            Text(
                text = "替换只作用于课表格子与日程卡片的显示；课程详情、编辑表单始终显示完整信息。",
                fontSize = SaltTheme.textStyles.sub.fontSize,
                color = SaltTheme.colors.subText,
                lineHeight = 16.sp,
                modifier = Modifier.padding(
                    horizontal = SaltTheme.dimens.padding,
                    vertical = 6.dp
                )
            )

            ItemTip(
                text = "这里的设置只用于**新建课表**：新建时会把默认时间表、节数、周数、显示替换" +
                    "复制一份给新课表，之后两边互不影响；已经建好的课表不会因为改这里而变化，" +
                    "要改它们请到课表页「⋯ → 课表设置」。"
            )
            ItemTip(
                text = "导入/分享课表时按**对方课表自带的配置**来（信封里带着上课时间、节数、周数、" +
                    "显示替换），本页的默认值不会覆盖它们。"
            )
        }
    }

    if (showLessonCountPicker) {
        IntPickerDialog(
            title = "一天课程节数",
            value = defaults.lessonCount,
            range = 1..20,
            unit = "节",
            onPick = {
                updateDefaults(defaults.copy(lessonCount = it))
                showLessonCountPicker = false
            },
            onDismissRequest = { showLessonCountPicker = false }
        )
    }

    if (showWeekCountPicker) {
        IntPickerDialog(
            title = "学期周数",
            value = defaults.semesterWeekCount,
            range = 1..30,
            unit = "周",
            onPick = {
                updateDefaults(defaults.copy(semesterWeekCount = it))
                showWeekCountPicker = false
            },
            onDismissRequest = { showWeekCountPicker = false }
        )
    }
}
