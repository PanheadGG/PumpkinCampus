package com.pgigi.pumpkincampus

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.moriafly.salt.ui.Button
import com.moriafly.salt.ui.ButtonAppearance
import com.moriafly.salt.ui.ButtonIntent
import com.moriafly.salt.ui.Icon
import com.moriafly.salt.ui.Item
import com.moriafly.salt.ui.ItemCheck
import com.moriafly.salt.ui.ItemContainer
import com.moriafly.salt.ui.ItemInfo
import com.moriafly.salt.ui.ItemInfoType
import com.moriafly.salt.ui.ItemLabelValueContainer
import com.moriafly.salt.ui.ItemOuterLargeTitle
import com.moriafly.salt.ui.ItemOuterTitle
import com.moriafly.salt.ui.ItemOuterTip
import com.moriafly.salt.ui.ItemSwitcher
import com.moriafly.salt.ui.LabelValue
import com.moriafly.salt.ui.Switcher
import com.moriafly.salt.ui.UnstableSaltUiApi
import com.moriafly.salt.ui.dialog.YesNoDialog
import com.moriafly.salt.ui.dialog.YesDialog
import com.moriafly.salt.ui.icons.SaltIcons
import com.moriafly.salt.ui.icons.Success
import com.moriafly.salt.ui.outerPadding
import com.moriafly.salt.ui.popup.rememberPopupState
import com.moriafly.salt.ui.rememberScrollState
import com.moriafly.salt.ui.screen.BasicScreen
import com.moriafly.salt.ui.screen.ScreenCard
import com.moriafly.salt.ui.verticalScroll

/**
 * Salt UI 3.0.0-beta01 组件演示页面。
 *
 * 覆盖：大标题、卡片分组、列表条目、开关、多选、信息条、键值对、按钮（外观 / 意图 / 禁用）、
 * 以及 YesDialog / YesNoDialog 对话框。
 */
@OptIn(UnstableSaltUiApi::class)
@Composable
fun SaltUiDemo(
    isDarkTheme: Boolean,
    onToggleDarkTheme: (Boolean) -> Unit,
) {
    var reminderEnabled by remember { mutableStateOf(true) }
    var standaloneSwitch by remember { mutableStateOf(false) }

    // Salt UI 对话框通过 popup state 驱动
    val savedPopup = rememberPopupState()
    val confirmPopup = rememberPopupState()

    BasicScreen(
        actionButton = null,
        title = "Salt UI Demo",
        subtitle = "3.0.0-beta01",
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(contentPadding.calculateTopPadding()))

            ItemOuterLargeTitle(
                text = "PumpkinCampus",
                sub = "基于 Salt UI 3.0.0-beta01 的组件演示，适用于 Compose Multiplatform",
            )

            // ---- 课程表 Pager 演示段落已移除（课程示例数据已删除）----

            // ---- 外观 ----
            ScreenCard(
                header = "外观",
                footer = "深色模式会立即应用到全部 Salt UI 组件",
            ) {
                ItemSwitcher(
                    state = isDarkTheme,
                    onChange = onToggleDarkTheme,
                    text = "深色模式",
                    sub = "在浅色与深色主题之间切换",
                )
            }

            // ---- 列表条目 ----
            ScreenCard(
                header = "列表条目",
            ) {
                Item(
                    onClick = { savedPopup.expend() },
                    text = "同步课表到云端",
                    sub = "点击打开提示对话框",
                )
                Item(
                    onClick = {},
                    text = "成绩查询",
                    sub = "本学期平均绩点 3.6",
                )
                ItemCheck(
                    state = reminderEnabled,
                    onChange = { reminderEnabled = it },
                    text = "上课提醒",
                    sub = "按课表时间推送提醒通知",
                )
                ItemInfo(
                    text = "课表已同步至云端",
                    infoType = ItemInfoType.Success,
                )
            }

            // ---- 独立开关 ----
            ScreenCard(
                header = "开关",
            ) {
                ItemContainer {
                    Switcher(
                        state = standaloneSwitch,
                    )
                }
                ItemSwitcher(
                    state = standaloneSwitch,
                    onChange = { standaloneSwitch = it },
                    text = "同步开关",
                    sub = "行内开关与独立开关共享同一状态",
                )
            }

            // ---- 键值对 ----
            ScreenCard(
                header = "设备信息",
            ) {
                ItemLabelValueContainer {
                    LabelValue(label = "应用版本", value = "1.0.0")
                    LabelValue(label = "Salt UI", value = "3.0.0-beta01")
                    LabelValue(label = "Compose Multiplatform", value = "1.12.1")
                    LabelValue(label = "Kotlin", value = "2.4.20")
                }
            }

            // ---- 按钮 ----
            ItemOuterTitle("按钮")
            DemoFlowRow {
                Button(onClick = {}, text = "Filled")
                Button(onClick = {}, text = "Subtle", appearance = ButtonAppearance.Subtle)
                Button(onClick = {}, text = "Plain", appearance = ButtonAppearance.Plain)
            }
            ItemOuterTip(
                "Filled 用于主要操作，Subtle 用于弱化操作，Plain 用于轻量操作",
            )

            ItemOuterTitle("按钮状态与意图")
            DemoFlowRow {
                Button(
                    onClick = { savedPopup.expend() },
                    text = "保存设置",
                )
                Button(
                    onClick = {},
                    text = "删除",
                    intent = ButtonIntent.Destructive,
                )
                Button(
                    onClick = {},
                    text = "禁用",
                    enabled = false,
                )
            }
            ItemOuterTip(
                "intent 用于表达操作性质，enabled=false 表示不可用状态",
            )

            // ---- 对话框 ----
            ScreenCard(
                header = "对话框",
                footer = "对话框由 rememberPopupState 驱动",
            ) {
                Item(
                    onClick = { savedPopup.expend() },
                    text = "提示对话框",
                    sub = "YesDialog · 单按钮确认",
                )
                Item(
                    onClick = { confirmPopup.expend() },
                    text = "确认对话框",
                    sub = "YesNoDialog · 取消 / 继续",
                )
            }

            Spacer(Modifier.height(contentPadding.calculateBottomPadding()))
        }
    }

    if (savedPopup.expend) {
        YesDialog(
            onDismissRequest = { savedPopup.dismiss() },
            title = "设置已保存",
            content = "你的偏好设置已成功保存，稍后将自动同步到云端。",
            confirmText = "完成",
        )
    }

    if (confirmPopup.expend) {
        YesNoDialog(
            onDismissRequest = { confirmPopup.dismiss() },
            onConfirm = { confirmPopup.dismiss() },
            title = "继续操作？",
            content = "是否继续执行该操作？",
            cancelText = "取消",
            confirmText = "继续",
        )
    }
}

@Composable
private fun DemoFlowRow(
    content: @Composable () -> Unit,
) {
    FlowRow(
        modifier = Modifier.outerPadding(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        content()
    }
}
