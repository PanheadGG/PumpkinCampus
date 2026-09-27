package com.pgigi.pumpkincampus.settings

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
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
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import androidx.savedstate.serialization.SavedStateConfiguration
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
import com.moriafly.salt.ui.icons.ChevronRight
import com.moriafly.salt.ui.icons.SaltIcons
import com.moriafly.salt.ui.navigation.rememberSaltNavigator
import com.moriafly.salt.ui.popup.PopupMenu
import com.moriafly.salt.ui.popup.PopupMenuItem
import com.moriafly.salt.ui.screen.BasicScreen
import com.moriafly.salt.ui.screen.TitleBarButton
import com.pgigi.pumpkincampus.components.AppDatePicker
import com.pgigi.pumpkincampus.models.AppSettings
import com.pgigi.pumpkincampus.models.Course
import com.pgigi.pumpkincampus.models.ImportedSchedule
import com.pgigi.pumpkincampus.models.ScheduleCache
import com.pgigi.pumpkincampus.models.ScheduleExport
import com.pgigi.pumpkincampus.schedule.ImportErrorDialog
import com.pgigi.pumpkincampus.schedule.ShareImportDialog
import com.pgigi.pumpkincampus.schedule.dayIndexOf
import com.pgigi.pumpkincampus.schedule.weekCalculatorOf
import com.pgigi.pumpkincampus.utils.JsonUtil
import com.pgigi.pumpkincampus.utils.rememberJsonFilePicker
import kotlinx.datetime.LocalDate
import kotlinx.datetime.number
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import kotlin.math.roundToInt

internal val SettingsWeekLabels = listOf("日", "一", "二", "三", "四", "五", "六")

/**
 * 设置页：
 * - 导入课程（Popup 选择：从JSON文本导入 / 从文件导入，与课表页一致）
 * - 「新建课表默认设置」子页：辅助线、课表数据与外观（新建课表时快照的默认值）
 *
 * 「第一周的第一天」不再放在这里——每次新建课表后都会弹日期选择器提示设置。
 *
 * 子页统一由 salt-ui-navigation（[rememberSaltNavigator] + [NavDisplay]）推送。
 * 设置经 [onSettingsChange] 交由 HomeScreen 持久化到 settings.json。
 */
/** 设置页根导航键。 */
@Serializable
internal data object SettingsRootKey : NavKey

/** 「课表外观」子页导航键。 */
@Serializable
internal data object AppearanceKey : NavKey

/** 「上课时间」子页导航键。 */
@Serializable
internal data object LessonTimesKey : NavKey

/** 「时间表编辑」子页导航键。 */
@Serializable
internal data class TimetableEditKey(val timetableId: String) : NavKey

/** 「新建课表默认设置」子页导航键。 */
@Serializable
internal data object DefaultSettingsKey : NavKey

/**
 * 设置页导航宿主：子页统一用 Salt UI 的 **salt-ui-navigation**（navigation3）——
 * [rememberSaltNavigator] 持有可保存的导航栈，[NavDisplay] 负责渲染与转场，
 * 课表外观、上课时间、时间表编辑都压入同一栈，返回箭头/系统返回键弹栈。
 */
@OptIn(UnstableSaltUiApi::class)
@Composable
internal fun SettingsPage(
    settings: AppSettings,
    courses: List<Course>,
    onSettingsChange: (AppSettings) -> Unit,
    onImportReady: (ImportedSchedule) -> Unit
) {
    // salt-ui-navigation：可保存的导航栈（根 = 设置主页；子页 navigate 压栈、back 弹栈）
    val navigator = rememberSaltNavigator(
        configuration = SavedStateConfiguration {
            serializersModule = SerializersModule {
                polymorphic(baseClass = NavKey::class) {
                    subclass(serializer = SettingsRootKey.serializer())
                    subclass(serializer = AppearanceKey.serializer())
                    subclass(serializer = LessonTimesKey.serializer())
                    subclass(serializer = TimetableEditKey.serializer())
                    subclass(serializer = DefaultSettingsKey.serializer())
                }
            }
        },
        initRoute = SettingsRootKey,
        topLevelRoutes = setOf(SettingsRootKey)
    )

    NavDisplay(
        backStack = navigator.navBackStack,
        modifier = Modifier.fillMaxSize(),
        onBack = { navigator.back() },
        // 关掉返回预测动画：滑动返回跟手时不再把当前页缩小到 0.7 倍做预览，
        // 改为与普通返回（pop）一致的淡入淡出过渡
        predictivePopTransitionSpec = { _ ->
            ContentTransform(
                fadeIn(animationSpec = tween(700)),
                fadeOut(animationSpec = tween(700))
            )
        },
        entryProvider = entryProvider {
            entry<SettingsRootKey> {
                MainSettings(
                    settings = settings,
                    onSettingsChange = onSettingsChange,
                    onImportReady = onImportReady,
                    onNavigate = { navigator.navigate(it) }
                )
            }
            entry<DefaultSettingsKey> {
                DefaultSettingsPage(
                    settings = settings,
                    onSettingsChange = onSettingsChange,
                    onNavigate = { navigator.navigate(it) },
                    onBack = { navigator.back() }
                )
            }
            entry<AppearanceKey> {
                AppearanceSettingsPage(
                    settings = settings,
                    courses = courses,
                    onSettingsChange = onSettingsChange,
                    onBack = { navigator.back() }
                )
            }
            entry<LessonTimesKey> {
                LessonTimesPage(
                    settings = settings,
                    onSettingsChange = onSettingsChange,
                    onBack = { navigator.back() },
                    onOpenTimetable = { navigator.navigate(TimetableEditKey(it)) }
                )
            }
            entry<TimetableEditKey> { key ->
                TimetableEditPage(
                    timetableId = key.timetableId,
                    settings = settings,
                    onSettingsChange = onSettingsChange,
                    onBack = { navigator.back() }
                )
            }
        }
    )
}

@OptIn(UnstableSaltUiApi::class)
@Composable
private fun MainSettings(
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
    onImportReady: (ImportedSchedule) -> Unit,
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
            }

            ItemOuterTitle(text = "课程")
            RoundedColumn {
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

            ItemOuterTitle(text = "课表")
            RoundedColumn {
                NavRow(
                    title = "新建课表默认设置",
//                    value = "辅助线 · 时间 · 节数 · 周数",
                    onClick = { onNavigate(DefaultSettingsKey) }
                )
            }

//            ItemTip(
//                text = "这里是**新建课表时使用的默认设置**：新建课表会快照当前配置；" +
//                    "已有课表的独立设置在课表页「⋯ → 课表设置」中调整，只对该课表生效。"
//            )
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

/**
 * 「新建课表默认设置」子页（设置 Tab 导航栈推送）：
 * 打开课表辅助线、课表外观、上课时间、一天课程节数、学期周数——
 * 新建课表时快照为该课表的专属设置。
 *
 * 「第一周的第一天」不在这里：每次新建课表后由 HomeScreen 弹日期选择器提示设置。
 *
 * @param settings 设置页默认值（settings.json）
 * @param onSettingsChange 写回默认值
 * @param onNavigate push「课表外观」「上课时间」等更深层子页
 * @param onBack 弹栈
 */
@OptIn(UnstableSaltUiApi::class)
@Composable
private fun DefaultSettingsPage(
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
    onNavigate: (NavKey) -> Unit,
    onBack: () -> Unit
) {
    var showLessonCountPicker by remember { mutableStateOf(false) }
    var showWeekCountPicker by remember { mutableStateOf(false) }

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
        title = "新建课表默认设置",
        subtitle = "新建课表时快照当前配置"
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = contentPadding.calculateTopPadding())
                .padding(bottom = contentPadding.calculateBottomPadding() + 24.dp)
        ) {
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
                    onClick = { onNavigate(AppearanceKey) }
                )
            }

            ItemOuterTitle(text = "课表数据")
            RoundedColumn {
                NavRow(
                    title = "上课时间",
                    value = settings.activeTimetable()
                        ?.let { "${it.name} · ${it.slots.size} 节" } ?: "默认作息",
                    onClick = { onNavigate(LessonTimesKey) }
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

            ItemTip(
                text = "课表按「一天课程节数」显示行数；「上课时间」时间表中多余的节次会自动忽略。" +
                    "修改学期周数会影响教学周的总数。"
            )
        }
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
 * 设置页导入、课表页「从JSON文本导入 / 从文件导入」共用此约定。
 */
internal fun parseImportedSchedule(raw: String): ImportedSchedule? {
    val text = raw.trim()
    if (text.isEmpty()) return null

    // 1) 导出信封：课表名称 + 课程 + 课表时间/开课日期/周数/节数等设置
    runCatching { JsonUtil.parseJson(text, ScheduleExport.serializer()) }
        .getOrNull()?.let { export ->
            return export.courses.takeIf { it.isNotEmpty() }
                ?.let {
                    ImportedSchedule(
                        courses = it,
                        settings = export.settings,
                        name = export.name
                    )
                }
                ?: return null
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
