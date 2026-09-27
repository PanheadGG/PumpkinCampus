package com.pgigi.pumpkincampus.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moriafly.salt.ui.Button
import com.moriafly.salt.ui.ButtonAppearance
import com.moriafly.salt.ui.Icon
import com.moriafly.salt.ui.ItemDivider
import com.moriafly.salt.ui.ItemOuterTitle
import com.moriafly.salt.ui.ItemSwitcher
import com.moriafly.salt.ui.ItemTip
import com.moriafly.salt.ui.RoundedColumn
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.Text
import com.moriafly.salt.ui.UnstableSaltUiApi
import com.moriafly.salt.ui.dialog.BasicDialog
import com.moriafly.salt.ui.dialog.DialogTitle
import com.moriafly.salt.ui.icons.Back
import com.moriafly.salt.ui.icons.SaltIcons
import com.moriafly.salt.ui.screen.BasicScreen
import com.moriafly.salt.ui.screen.TitleBarButton
import com.pgigi.pumpkincampus.plugin.PluginConfigItem
import com.pgigi.pumpkincampus.plugin.PluginConfigType
import com.pgigi.pumpkincampus.plugin.PluginUiState
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * 「课表设置 → 教务系统插件」子页（课表页 navigation3 推送）：
 * **为当前课表选择一个教务系统插件**、编辑该课表的插件配置、触发课表数据同步。
 *
 * 选择、配置与同步结果都按课表隔离：
 * - 选择（[PluginUiState.onSelectPlugin]）写入 `CourseSchedule.pluginId`
 * - 配置（[PluginUiState.onConfigChange]）写入 `CourseSchedule.pluginConfig`；
 *   其中密码、token 等**敏感项加密保存**在 KVault（Android Keystore / iOS Keychain），
 *   存档里只记录 key（`CourseSchedule.pluginSecretKeys`）
 * - 同步结果持久化在 `plugin-schedule.json` 的对应课表条目里
 *
 * 插件的安装/卸载不在这里（全局，见 [PluginsPage]）。
 *
 * @param pluginUi 插件 UI 状态与回调（由 HomeScreen 构建）
 * @param scheduleName 当前课表名称（提示文案用）
 * @param onBack 弹栈
 */
@OptIn(UnstableSaltUiApi::class)
@Composable
internal fun SchedulePluginPage(
    pluginUi: PluginUiState?,
    scheduleName: String,
    onBack: () -> Unit
) {
    val state = pluginUi ?: PluginUiState()
    var editingConfig by remember { mutableStateOf<PluginConfigItem?>(null) }
    var showLogs by remember { mutableStateOf(false) }

    val selected = state.selected
    val configs = selected?.manifest?.configs ?: emptyList()
    val entry = state.entry

    BasicScreen(
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
        title = "教务系统插件",
        subtitle = "仅对当前课表「$scheduleName」生效"
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = contentPadding.calculateTopPadding())
                .padding(bottom = contentPadding.calculateBottomPadding() + 24.dp)
        ) {
            // —— 插件选择（一个课表只能选一个） ——
            ItemOuterTitle(text = "选择插件")
            RoundedColumn {
                NavRow(
                    title = "不使用插件",
                    value = if (state.selectedPluginId == null) "✓" else null,
                    onClick = { state.onSelectPlugin(null) }
                )
                if (state.installed.isNotEmpty()) ItemDivider()
                state.installed.forEachIndexed { index, plugin ->
                    if (index > 0) ItemDivider()
                    val isSelected = plugin.id == state.selectedPluginId
                    NavRow(
                        title = plugin.name,
                        value = when {
                            isSelected -> "✓"
                            else -> plugin.manifest.schoolName.ifBlank { "未选择" }
                        },
                        onClick = { state.onSelectPlugin(plugin.id) }
                    )
                }
            }
            if (state.installed.isEmpty()) {
                ItemTip(
                    text = "暂无已安装插件：请先到「设置 → 教务系统插件 → 从文件安装插件」安装。"
                )
            }
            if (state.selectedMissing) {
                ItemTip(
                    text = "当前课表选择的插件（${state.selectedPluginId}）已被卸载，请重新选择。"
                )
            }
            ItemTip(
                text = "每个课表只能绑定一个教务系统插件；插件全局安装，" +
                    "但选择关系、配置数据与同步结果都按课表隔离保存。"
            )

            // —— 插件配置（默认值来自 manifest，课表单独保存） ——
            if (selected != null && configs.isNotEmpty()) {
                ItemOuterTitle(text = "插件配置")
                RoundedColumn {
                    configs.forEachIndexed { index, item ->
                        if (index > 0) ItemDivider()
                        when (item.configType) {
                            PluginConfigType.BOOL -> {
                                val stored = state.configValues[item.configKey]
                                val value = (stored ?: defaultText(item))
                                    .toBooleanStrictOrNull()
                                    ?: false
                                ItemSwitcher(
                                    state = value,
                                    onChange = { on ->
                                        state.onConfigChange(
                                            state.configValues + (item.configKey to on.toString())
                                        )
                                    },
                                    text = item.title
                                )
                            }

                            else -> NavRow(
                                title = item.title,
                                value = displayConfigValue(item, state.configValues),
                                onClick = { editingConfig = item }
                            )
                        }
                    }
                }
                ItemTip(
                    text = "配置只保存在当前课表；没有保存过时使用插件自带的默认值。" +
                        "密码类配置使用系统加密存储保存" +
                        "（Android Keystore / iOS Keychain），不会明文写入课表存档。"
                )
            }

            // —— 数据同步 ——
            if (selected != null) {
                ItemOuterTitle(text = "课表数据")
                RoundedColumn {
                    NavRow(
                        title = "立即同步课表",
                        value = when {
                            state.syncing -> "同步中…"
                            entry != null && entry.updateTime > 0L ->
                                "上次 ${formatSyncTime(entry.updateTime)} · ${entry.courses.size} 门"
                            else -> "尚未同步"
                        },
                        onClick = { if (!state.syncing) state.onSyncNow() }
                    )
                    entry?.lastError?.let { error ->
                        ItemDivider()
                        Text(
                            text = "上次同步失败：$error",
                            fontSize = SaltTheme.textStyles.sub.fontSize,
                            color = SaltTheme.colors.error,
                            lineHeight = 16.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    horizontal = SaltTheme.dimens.padding,
                                    vertical = 10.dp
                                )
                        )
                    }
                    if (entry?.lastLogs?.isNotEmpty() == true) {
                        ItemDivider()
                        NavRow(
                            title = "查看同步日志",
                            value = "${entry.lastLogs.size} 条",
                            onClick = { showLogs = true }
                        )
                    }
                }
                ItemTip(
                    text = "同步会运行插件脚本访问教务系统（登录、拉取课表），" +
                        "结果作为 只读 的插件课程并入当前课表展示。" +
                        "打开软件 与 切换课表 时会自动同步一次（30 秒内不重复请求），" +
                        "课表页下拉可随时手动刷新。"
                )
            }

            // —— 插件课程（只读层）：转换为自定义课程后才能编辑 ——
            if (state.pluginCourseCount > 0) {
                ItemOuterTitle(text = "插件课程")
                RoundedColumn {
                    NavRow(
                        title = "转换为自定义课程",
                        value = "共 ${state.pluginCourseCount} 门",
                        onClick = state.onConvertAllPluginCourses
                    )
                }
                ItemTip(
                    text = "插件课程不能在课表里直接修改：点开课程详情只能「转换为自定义课程」，" +
                        "转换后会复制一份可编辑的自定义课程。" +
                        "插件课程本身仍然保留，所以插件下次同步更新课表后可能出现重复课程" +
                        "（插件课程 + 已转换的自定义课程），届时删除自定义课程即可。"
                )
            }
        }
    }

    // 配置编辑弹层（string / password / int 共用）
    editingConfig?.let { item ->
        PluginConfigEditDialog(
            item = item,
            initial = state.configValues[item.configKey] ?: defaultText(item),
            onConfirm = { value ->
                state.onConfigChange(state.configValues + (item.configKey to value))
                editingConfig = null
            },
            onDismissRequest = { editingConfig = null }
        )
    }

    // 同步日志（console 输出 + 宿主记录的原始请求/响应 + 课程校验告警）
    if (showLogs && entry != null) {
        var copied by remember { mutableStateOf(false) }
        val clipboard = LocalClipboardManager.current
        BasicDialog(onDismissRequest = { showLogs = false }) {
            DialogTitle(text = "同步日志")
            Text(
                text = "含原始请求与响应（方法 / URL / 请求头 / 请求体 / 状态码 / 响应头 / 响应体）；" +
                    "密码、token、Cookie 值已用 *** 隐藏，超长内容会截断。",
                fontSize = SaltTheme.textStyles.sub.fontSize,
                color = SaltTheme.colors.subText,
                lineHeight = 16.sp,
                modifier = Modifier.padding(
                    horizontal = SaltTheme.dimens.padding,
                    vertical = 4.dp
                )
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = SaltTheme.dimens.padding, vertical = 4.dp)
            ) {
                if (entry.lastLogs.isEmpty()) {
                    Text(
                        text = "本次没有日志",
                        fontSize = 11.sp,
                        color = SaltTheme.colors.subText
                    )
                }
                entry.lastLogs.forEach { line ->
                    Text(
                        text = line,
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        color = if (line.startsWith("[error]") || line.startsWith("[warn]")) {
                            SaltTheme.colors.error
                        } else {
                            SaltTheme.colors.subText
                        }
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)
            ) {
                Button(
                    onClick = {
                        clipboard.setText(AnnotatedString(entry.lastLogs.joinToString("\n")))
                        copied = true
                    },
                    text = if (copied) "已复制" else "复制日志",
                    appearance = ButtonAppearance.Subtle,
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = { showLogs = false },
                    text = "关闭",
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/* ------------------------------------------------------------------ */
/* 配置编辑弹层                                                         */
/* ------------------------------------------------------------------ */

/** 单个配置项的编辑弹层：按类型决定输入方式（密码掩码 / 整数过滤 / 普通文本）。 */
@OptIn(UnstableSaltUiApi::class, ExperimentalLayoutApi::class)
@Composable
internal fun PluginConfigEditDialog(
    item: PluginConfigItem,
    initial: String,
    onConfirm: (String) -> Unit,
    onDismissRequest: () -> Unit
) {
    var value by remember(item.configKey) { mutableStateOf(initial) }
    var error by remember { mutableStateOf<String?>(null) }
    val isPassword = item.configType == PluginConfigType.PASSWORD
    val isInt = item.configType == PluginConfigType.INT

    BasicDialog(onDismissRequest = onDismissRequest) {
        DialogTitle(text = item.title)
        item.description?.takeIf { it.isNotBlank() }?.let { desc ->
            Text(
                text = desc,
                fontSize = SaltTheme.textStyles.sub.fontSize,
                color = SaltTheme.colors.subText,
                lineHeight = 16.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SaltTheme.dimens.padding, vertical = 4.dp)
            )
        }
        // —— 候选值（manifest 的 configs[].options）：点一下填入，也可以自己输入 ——
        if (item.options.isNotEmpty()) {
            Text(
                text = "候选值（可直接点选，也可以自己在下面填写）",
                fontSize = SaltTheme.textStyles.sub.fontSize,
                color = SaltTheme.colors.subText,
                lineHeight = 16.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SaltTheme.dimens.padding, vertical = 4.dp)
            )
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SaltTheme.dimens.padding, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item.options.forEach { option ->
                    Button(
                        onClick = {
                            value = option
                            error = null
                        },
                        text = if (option == value) "✓ $option" else option,
                        appearance = if (option == value) {
                            ButtonAppearance.Filled
                        } else {
                            ButtonAppearance.Subtle
                        }
                    )
                }
            }
        }
        BasicTextField(
            value = value,
            onValueChange = { raw ->
                value = if (isInt) raw.filter { it.isDigit() } else raw
                error = null
            },
            singleLine = true,
            visualTransformation = if (isPassword) {
                PasswordVisualTransformation()
            } else {
                VisualTransformation.None
            },
            // 密码项必须声明成密码类型：Android 会把输入框的 inputType 设成
            // TYPE_TEXT_VARIATION_PASSWORD，系统/ROM 据此弹出「安全键盘」（小米安全键盘、
            // 华为安全输入等），同时关掉输入法的联想、自动纠错与个性化学习。
            // 只做 PasswordVisualTransformation（屏幕打码）是不够的：那只是显示层，
            // 输入法仍然按普通文本框处理。
            keyboardOptions = KeyboardOptions(
                keyboardType = when {
                    isPassword -> KeyboardType.Password
                    isInt -> KeyboardType.Number
                    else -> KeyboardType.Text
                },
                // 配置项都是账号/地址/密码这类内容，任何联想与自动纠错都是干扰
                autoCorrectEnabled = false,
                imeAction = ImeAction.Done
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SaltTheme.dimens.padding, vertical = 6.dp),
            textStyle = TextStyle(
                fontSize = SaltTheme.textStyles.main.fontSize,
                color = SaltTheme.colors.text
            ),
            cursorBrush = SolidColor(SaltTheme.colors.highlight),
            decorationBox = { inner ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                ) {
                    inner()
                }
            }
        )
        error?.let {
            Text(
                text = it,
                fontSize = SaltTheme.textStyles.sub.fontSize,
                color = SaltTheme.colors.error,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SaltTheme.dimens.padding, vertical = 4.dp)
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)
        ) {
            Button(
                onClick = onDismissRequest,
                text = "取消",
                appearance = ButtonAppearance.Subtle,
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = {
                    val trimmed = if (isPassword) value else value.trim()
                    if (isInt && trimmed.isNotEmpty() && trimmed.toIntOrNull() == null) {
                        error = "请输入整数"
                    } else {
                        onConfirm(trimmed)
                    }
                },
                text = "保存",
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/* ------------------------------------------------------------------ */
/* 小工具                                                               */
/* ------------------------------------------------------------------ */

/** 配置项的展示值（列表行右侧）。 */
private fun displayConfigValue(item: PluginConfigItem, values: Map<String, String>): String {
    val stored = values[item.configKey]
    return when (item.configType) {
        PluginConfigType.PASSWORD ->
            if (stored.isNullOrBlank()) "未设置" else "••••••"

        PluginConfigType.BOOL ->
            ((stored ?: defaultText(item)).toBooleanStrictOrNull() ?: false)
                .let { if (it) "开" else "关" }

        else -> stored?.takeIf { it.isNotBlank() }
            ?: defaultText(item).takeIf { it.isNotBlank() }
            ?: "未设置"
    }
}

/** manifest 里该项的默认值（字符串形式）。 */
private fun defaultText(item: PluginConfigItem): String =
    (item.default as? kotlinx.serialization.json.JsonPrimitive)?.content ?: ""

/** 时间戳 → `yyyy-MM-dd HH:mm`。 */
@OptIn(ExperimentalTime::class)
private fun formatSyncTime(ms: Long): String {
    val dt = Instant.fromEpochMilliseconds(ms).toLocalDateTime(TimeZone.currentSystemDefault())
    fun pad(n: Int) = if (n < 10) "0$n" else n.toString()
    return "${dt.year}-${pad(dt.month.number)}-${pad(dt.day)} ${pad(dt.hour)}:${pad(dt.minute)}"
}
