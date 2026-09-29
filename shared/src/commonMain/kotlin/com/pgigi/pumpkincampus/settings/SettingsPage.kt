package com.pgigi.pumpkincampus.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation3.runtime.NavKey
import com.moriafly.salt.ui.Button
import com.moriafly.salt.ui.ButtonAppearance
import com.moriafly.salt.ui.Icon
import com.moriafly.salt.ui.ItemDivider
import com.moriafly.salt.ui.ItemOuterTitle
import com.moriafly.salt.ui.ItemSwitcher
import com.moriafly.salt.ui.RoundedColumn
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.Text
import com.moriafly.salt.ui.UnstableSaltUiApi
import com.moriafly.salt.ui.dialog.BasicDialog
import com.moriafly.salt.ui.dialog.DialogTitle
import com.moriafly.salt.ui.icons.ChevronRight
import com.moriafly.salt.ui.icons.SaltIcons
import com.moriafly.salt.ui.popup.PopupMenu
import com.moriafly.salt.ui.popup.PopupMenuItem
import com.moriafly.salt.ui.screen.BasicScreen
import com.pgigi.pumpkincampus.components.AppDatePicker
import com.pgigi.pumpkincampus.models.AppSettings
import com.pgigi.pumpkincampus.models.Course
import com.pgigi.pumpkincampus.models.ImportedSchedule
import com.pgigi.pumpkincampus.models.OssLicense
import com.pgigi.pumpkincampus.models.ScheduleCache
import com.pgigi.pumpkincampus.models.ScheduleExport
import com.pgigi.pumpkincampus.plugin.PluginUiState
import com.pgigi.pumpkincampus.schedule.ImportErrorDialog
import com.pgigi.pumpkincampus.schedule.ShareImportDialog
import com.pgigi.pumpkincampus.utils.JsonUtil
import com.pgigi.pumpkincampus.utils.rememberJsonFilePicker
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlin.math.roundToInt

internal val SettingsWeekLabels = listOf("日", "一", "二", "三", "四", "五", "六")

/** 设置页根导航键。 */
@Serializable
internal data object SettingsRootKey : NavKey

/** 「课表外观」子页导航键。 */
@Serializable
internal data object AppearanceKey : NavKey

/** 「全局课表设置」子页导航键（新建课表的默认值）。 */
@Serializable
internal data object GlobalScheduleSettingsKey : NavKey

/** 「默认上课时间表」子页导航键（全局课表设置 → 上课时间表）。 */
@Serializable
internal data object DefaultLessonTimesKey : NavKey

/** 单个默认时间表编辑子页导航键（携带时间表 id）。 */
@Serializable
internal data class DefaultTimetableEditKey(val timetableId: String) : NavKey

/** 「教务系统插件」全局管理子页导航键。 */
@Serializable
internal data object PluginsKey : NavKey

/** 「关于」子页导航键。 */
@Serializable
internal data object AboutKey : NavKey

/** 「开放源代码许可」列表子页导航键。 */
@Serializable
internal data object OssLicenseListKey : NavKey

/** 单个开源项目详情子页导航键（携带该项目条目）。 */
@Serializable
internal data class OssLicenseDetailKey(val license: OssLicense) : NavKey

/**
 * 设置页：
 * - 导入课程（Popup 选择：从JSON文本导入 / 从文件导入，与课表页一致）
 * - **全局显示项**：颜色模式、子标题课表名、辅助线、课表外观（所有课表共用）
 * - **全局课表设置**：上课时间表、一天课程节数、学期周数、显示替换——
 *   只作为**新建课表**的默认值，改它不影响已建立的课表；导入/分享按信封自带的课表配置来
 * - 教务系统插件（全局安装/卸载）
 *
 * 「第一周的第一天」「上课时间」「一天课程节数」「学期周数」「显示替换」在**单个课表**里
 * 各自维护，在课表页「⋯ → 课表设置」里调整，只对当前课表生效。
 *
 * **子页不在本页建栈**：课表外观、全局课表设置、上课时间、时间表编辑、教务系统插件、
 * 关于、开源许可等子页统一压入 [com.pgigi.pumpkincampus.pages.HomeScreen] 里的
 * **唯一全局导航栈**（一个 SaltNavigator + 一个 NavDisplay），本页只回调 [onNavigate]；
 * 返回箭头 / 系统返回键 / 侧滑返回也都作用于那条全局栈。
 * 设置经 [onSettingsChange] 交由 HomeScreen 持久化到 settings.json。
 */
@OptIn(UnstableSaltUiApi::class)
@Composable
internal fun SettingsPage(
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
    onImportReady: (ImportedSchedule) -> Unit,
    pluginUi: PluginUiState? = null,
    onNavigate: (NavKey) -> Unit
) {
    var showImportMenu by remember { mutableStateOf(false) }
    var showTextImport by remember { mutableStateOf(false) }
    var showShareImport by remember { mutableStateOf(false) }
    var showColorModeMenu by remember { mutableStateOf(false) }
    // 解析失败提示
    var importError by remember { mutableStateOf<String?>(null) }

    // 「从文件导入」：系统文件选择器 → 以导出信封约定解析
    val pickJsonFile = rememberJsonFilePicker { raw ->
        if (raw != null) {
            val parsed = parseImportedSchedule(raw)
            if (parsed == null) {
                importError = "解析失败：格式不正确或不包含课程"
            } else {
                // 解析成功：交给 HomeScreen 统一弹「设置课表名称 → 确定导入」
                onImportReady(parsed)
            }
        }
    }

    BasicScreen(
        actionButton = null,
        title = "设置",
//        subtitle = "课程导入 · 新建课表默认设置"
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = contentPadding.calculateTopPadding())
                .padding(bottom = contentPadding.calculateBottomPadding() + 24.dp)
        ) {
            ItemOuterTitle(text = "外观")
            RoundedColumn {
                // 颜色模式：点按弹 Popup 三个选项，点选即生效（当前项带 ✓）
                Box {
                    NavRow(
                        title = "颜色模式",
                        value = ColorModeLabels[settings.colorMode]
                            ?: ColorModeLabels.getValue("system"),
                        onClick = { showColorModeMenu = true }
                    )
                    // 透明定位锚（行右端 1dp）：NavRow 是全宽行，直接当锚点会把
                    // 菜单顶到屏幕最左；补一个靠右的小锚点，菜单弹在行尾下方
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(1.dp)
                    ) {
                        PopupMenu(
                            expanded = showColorModeMenu,
                            onDismissRequest = { showColorModeMenu = false },
                            offset = DpOffset(16.dp, 8.dp)
                        ) {
                            ColorModeLabels.forEach { (mode, label) ->
                                PopupMenuItem(
                                    onClick = {
                                        showColorModeMenu = false
                                        onSettingsChange(settings.copy(colorMode = mode))
                                    },
                                    text = if (settings.colorMode == mode) {
                                        "✓ $label"
                                    } else {
                                        label
                                    }
                                )
                            }
                        }
                    }
                }
                ItemDivider()
                // 全局显示开关：课表页子标题（周次信息）后面是否追加当前课表名称。
                // 刻意放在全局设置里，不进课表专属设置（多课表切换时行为一致）
                ItemSwitcher(
                    state = settings.showScheduleNameInSubtitle,
                    onChange = { v ->
                        onSettingsChange(settings.copy(showScheduleNameInSubtitle = v))
                    },
                    text = "课表子标题显示当前课表名称"
                )
                ItemDivider()
                // 辅助线：全局显示项，所有课表共用（原先在「新建课表默认设置」里）
                ItemSwitcher(
                    state = settings.showGridLines,
                    onChange = { v -> onSettingsChange(settings.copy(showGridLines = v)) },
                    text = "打开课表辅助线"
                )
                ItemDivider()
                // 课表外观（单元格高度 / 老师 / 地点 / 「@」）：同样是全局显示项
                NavRow(
                    title = "课表外观",
                    value = "预览与详细参数",
                    onClick = { onNavigate(AppearanceKey) }
                )
            }

            ItemOuterTitle(text = "课表")
            RoundedColumn {
                // 全局课表设置：新建课表的默认值（上课时间表 / 节数 / 周数 / 显示替换）
                NavRow(
                    title = "全局课表设置",
                    value = "新建课表默认值",
                    onClick = { onNavigate(GlobalScheduleSettingsKey) }
                )
                ItemDivider()
                // 导入课程：与课表页一致，点按弹 Popup 选择导入来源
                Box {
                    NavRow(
                        title = "导入课程",
//                        value = "JSON",
                        onClick = { showImportMenu = true }
                    )
                    // 透明定位锚（行右端 1dp）：NavRow 是全宽行，直接当锚点会把
                    // 菜单顶到屏幕最左；补一个靠右的小锚点，菜单弹在行尾下方
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(1.dp)
                    ) {
                        PopupMenu(
                            expanded = showImportMenu,
                            onDismissRequest = { showImportMenu = false },
                            offset = DpOffset(16.dp, 8.dp)
                        ) {
                            PopupMenuItem(
                                onClick = {
                                    showImportMenu = false
                                    showTextImport = true
                                },
                                text = "从JSON文本导入"
                            )
                            PopupMenuItem(
                                onClick = {
                                    showImportMenu = false
                                    pickJsonFile()
                                },
                                text = "从文件导入"
                            )
                            PopupMenuItem(
                                onClick = {
                                    showImportMenu = false
                                    showShareImport = true
                                },
                                text = "从分享口令导入"
                            )
                        }
                    }
                }
            }

            // 教务系统插件：全局安装/卸载（每个课表在「课表设置」里单独选择与配置）
            ItemOuterTitle(text = "插件")
            RoundedColumn {
                NavRow(
                    title = "教务系统插件",
                    value = "已安装 ${pluginUi?.installed?.size ?: 0} 个",
                    onClick = { onNavigate(PluginsKey) }
                )
            }
            /*ItemTip(
                text = "从 zip 安装教务系统课表插件，安装/卸载对全部课表生效；" +
                    "具体某个课表用哪个插件、怎么配置，在课表页「⋯ → 课表设置 → 教务系统插件」中设置。"
            )
            ItemTip(
                text = "「打开课表辅助线」与「课表外观」是**全局设置**，所有课表共用；" +
                    "第一周的第一天、上课时间、节数、周数、显示替换是**课表专属**，" +
                    "在课表页「⋯ → 课表设置」里调整。"
            )*/

            ItemOuterTitle(text = "关于")
            RoundedColumn {
                NavRow(
                    title = "关于",
//                    value = appVersionLabel(),
                    onClick = { onNavigate(AboutKey) }
                )
            }
            /*ItemTip(
                text = "应用版本、开放源代码许可。"
            )*/
        }
    }

    // 从JSON文本导入：粘贴存储的 JSON → 解析成功后交给 HomeScreen 统一流程
    if (showTextImport) {
        ImportCoursesDialog(
            onDismissRequest = { showTextImport = false },
            onImport = { imported ->
                showTextImport = false
                onImportReady(imported)
            }
        )
    }

    // 从分享口令导入：粘贴消息 → 检测口令 → 拉取解析 → 统一导入流程
    if (showShareImport) {
        ShareImportDialog(
            onDismissRequest = { showShareImport = false },
            onImport = { imported ->
                showShareImport = false
                onImportReady(imported)
            }
        )
    }

    // 解析失败提示（可任意关闭）
    importError?.let { message ->
        ImportErrorDialog(
            message = message,
            onDismiss = { importError = null }
        )
    }
}

/** 整数选择弹层（一天课程节数 / 学期周数）：大数字 + 滑块 + 取消/确定。 */
@Composable
internal fun IntPickerDialog(
    title: String,
    value: Int,
    range: IntRange,
    unit: String,
    onPick: (Int) -> Unit,
    onDismissRequest: () -> Unit
) {
    var current by remember(value) { mutableStateOf(value.coerceIn(range)) }

    BasicDialog(onDismissRequest = onDismissRequest) {
        DialogTitle(text = title)
        Text(
            text = "${current} $unit",
            fontSize = SaltTheme.textStyles.largeTitle.fontSize,
            fontWeight = FontWeight.Bold,
            color = SaltTheme.colors.highlight,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        )
        Slider(
            value = current.toFloat(),
            onValueChange = { current = it.roundToInt().coerceIn(range) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = (range.last - range.first - 1).coerceAtLeast(0),
            colors = SliderDefaults.colors(
                thumbColor = SaltTheme.colors.highlight,
                activeTrackColor = SaltTheme.colors.highlight,
                inactiveTrackColor = SaltTheme.colors.stroke
            ),
            modifier = Modifier.padding(horizontal = SaltTheme.dimens.padding)
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
                onClick = { onPick(current) },
                text = "确定",
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * 有标题的弹层：DatePicker（一周的第一天可自定义）。
 *
 * 选中日期只做本地暂存，点「确定」后才回调 [onPicked] 生效；
 * 「取消」以低强调（置灰）样式呈现，点击关闭且不生效。
 */
@Composable
internal fun DatePickerDialog(
    selected: LocalDate,
    firstDayOfWeek: Int,
    onPicked: (LocalDate) -> Unit,
    onDismissRequest: () -> Unit
) {
    var picked by remember(selected) { mutableStateOf(selected) }

    BasicDialog(onDismissRequest = onDismissRequest) {
        DialogTitle(text = "第一周的第一天")
        Text(
            text = "选择第 1 教学周的第一天；它的星期就是每周的第一天，课表首列随之改变。",
            fontSize = SaltTheme.textStyles.sub.fontSize,
            color = SaltTheme.colors.subText,
            lineHeight = 16.sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SaltTheme.dimens.padding, vertical = 4.dp)
        )
        AppDatePicker(
            selected = picked,
            onSelected = { picked = it },
            firstDayOfWeek = firstDayOfWeek
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)
        ) {
            Button(
                onClick = onDismissRequest,
                text = "取消",
                // 置灰：低强调样式，点击仅关闭、不生效
                appearance = ButtonAppearance.Subtle,
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = { onPicked(picked) },
                text = "确定",
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/** 导入课程弹层：粘贴存储的 JSON → 按导出信封解析 → 回调。 */
@Composable
internal fun ImportCoursesDialog(
    onDismissRequest: () -> Unit,
    onImport: (ImportedSchedule) -> Unit
) {
    var raw by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    BasicDialog(onDismissRequest = onDismissRequest) {
        DialogTitle(text = "导入课程")
        Text(
            text = "粘贴存储的课程 JSON：支持导出信封（课表名称 + 课程 + 设置）、" +
                "课表存档（{\"courses\": [...]}）或课程数组 [...] 格式。" +
                "解析成功后将新建一套课表保存导入内容。",
            fontSize = SaltTheme.textStyles.sub.fontSize,
            color = SaltTheme.colors.subText,
            lineHeight = 16.sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SaltTheme.dimens.padding, vertical = 4.dp)
        )
        BasicTextField(
            value = raw,
            onValueChange = {
                raw = it
                error = null
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .padding(horizontal = SaltTheme.dimens.padding),
            textStyle = TextStyle(
                fontSize = SaltTheme.textStyles.sub.fontSize,
                color = SaltTheme.colors.text,
                lineHeight = 16.sp
            ),
            cursorBrush = SolidColor(SaltTheme.colors.highlight),
            decorationBox = { inner ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(SaltTheme.colors.subBackground, RoundedCornerShape(10.dp))
                        .padding(10.dp)
                ) {
                    if (raw.isEmpty()) {
                        Text(
                            text = "在此粘贴 JSON…",
                            fontSize = SaltTheme.textStyles.sub.fontSize,
                            color = SaltTheme.colors.subText
                        )
                    }
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
                    .padding(horizontal = SaltTheme.dimens.padding, vertical = 6.dp)
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
                // 取消类按钮统一低强调（非高亮色）
                appearance = ButtonAppearance.Subtle,
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = {
                    val parsed = parseImportedSchedule(raw)
                    if (parsed == null) {
                        error = "解析失败：格式不正确或不包含课程"
                    } else {
                        onImport(parsed)
                    }
                },
                text = "导入",
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/** 导入 JSON 的包装格式（`{"courses": [...]}`）。 */
@Serializable
private data class ImportCoursesFile(val courses: List<Course> = emptyList())

/**
 * 解析导入文本，**以导出信封 [ScheduleExport] 为基础**；
 * 无法解析或不含课程返回 null。兼容旧形态（均无设置、无名称字段）：
 * 1. 导出信封 `{type, version, exportTime, name, courses, settings}`
 * 2. 课表存档 `{"updateTime": ..., "courses": [...]}`
 * 3. 课程数组 `[{...}, {...}]`
 * 4. 包装对象 `{"courses": [...]}`
 *
 * 分享/导出已清除插件信息：插件课程在导出前就并入了 `courses`。
 * 早期信封里单独的 `pluginCourses` 字段（当时导入后是只读层）仍会被读取并**并入课程**，
 * 避免旧链接丢课；导入结果一律是可直接编辑的普通课表。
 *
 * 设置页导入、课表页「从JSON文本导入 / 从文件导入」共用此约定。
 */
internal fun parseImportedSchedule(raw: String): ImportedSchedule? {
    val text = raw.trim()
    if (text.isEmpty()) return null

    // 1) 导出信封：课表名称 + 课程 + 课表时间/开课日期/周数/节数等设置
    runCatching { JsonUtil.parseJson(text, ScheduleExport.serializer()) }
        .getOrNull()?.let { export ->
            val courses = (export.courses + legacyPluginCourses(text)).distinct()
            if (courses.isEmpty()) return null
            return ImportedSchedule(
                courses = courses,
                settings = export.settings,
                name = export.name
            )
        }

    // 2~4) 旧形态：只有课程，无设置
    val candidates = listOf(
        runCatching { JsonUtil.parseJson(text, ScheduleCache.serializer()) }
            .getOrNull()?.courses,
        runCatching { JsonUtil.parseListJson(text, Course.serializer()) }.getOrNull(),
        runCatching { JsonUtil.parseJson(text, ImportCoursesFile.serializer()) }
            .getOrNull()?.courses
    )
    val parsed = candidates.firstOrNull { it != null } ?: return null
    return parsed.takeIf { it.isNotEmpty() }?.let { ImportedSchedule(courses = it) }
}

/**
 * 兼容早期分享信封里单独的 `pluginCourses` 字段（当时导入后进只读层）。
 *
 * 现在导出不再写该字段，只在解析旧链接时读一次，读到就并入课程列表。
 * 解析失败（字段不存在 / 格式不对）返回空列表。
 */
private fun legacyPluginCourses(raw: String): List<Course> =
    runCatching {
        val element = JsonUtil.parseJson(raw, JsonElement.serializer())
        val field = (element as? JsonObject)?.get("pluginCourses") ?: return emptyList()
        JsonUtil.parseJson(field.toString(), ListSerializer(Course.serializer()))
    }.getOrDefault(emptyList())

/* ------------------------------------------------------------------ */
/* 设置页通用小部件                                                     */
/* ------------------------------------------------------------------ */

/** 导航行：标题 + 可选值 + 右箭头。 */
@Composable
internal fun NavRow(
    title: String,
    value: String? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = SaltTheme.dimens.padding, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            fontSize = SaltTheme.textStyles.main.fontSize,
            color = SaltTheme.colors.text,
            modifier = Modifier.weight(1f)
        )
        if (value != null) {
            Text(
                text = value,
                fontSize = SaltTheme.textStyles.sub.fontSize,
                color = SaltTheme.colors.subText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(end = 6.dp)
            )
        }
        Icon(
            imageVector = SaltIcons.ChevronRight,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = SaltTheme.colors.subText
        )
    }
}

/** 颜色模式取值（settings.json 的 `colorMode`）→ 展示名。 */
private val ColorModeLabels = mapOf(
    "system" to "跟随系统",
    "light" to "浅色模式",
    "dark" to "深色模式"
)
