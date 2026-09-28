package com.pgigi.pumpkincampus.pages

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.moriafly.salt.ui.Icon
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.Text
import com.moriafly.salt.ui.UnstableSaltUiApi
import com.moriafly.salt.ui.pager.PagerState
import com.moriafly.salt.ui.pager.rememberPagerState
import com.moriafly.salt.ui.popup.PopupMenu
import com.moriafly.salt.ui.popup.PopupMenuItem
import com.moriafly.salt.ui.screen.BasicScreen
import com.moriafly.salt.ui.screen.TitleBarButton
import com.pgigi.pumpkincampus.components.AddIcon
import com.pgigi.pumpkincampus.icons.MaterialIcons
import com.pgigi.pumpkincampus.icons.material.Download2
import com.pgigi.pumpkincampus.icons.material.MoreHorizontal
import com.pgigi.pumpkincampus.icons.material.Upload2
import com.pgigi.pumpkincampus.models.AppSettings
import com.pgigi.pumpkincampus.models.Course
import com.pgigi.pumpkincampus.models.CourseSchedule
import com.pgigi.pumpkincampus.models.ImportedSchedule
import com.pgigi.pumpkincampus.models.LessonTimetable
import com.pgigi.pumpkincampus.plugin.PluginUiState
import com.pgigi.pumpkincampus.schedule.AddCourseSheet
import com.pgigi.pumpkincampus.schedule.EditCourseSheet
import com.pgigi.pumpkincampus.schedule.ImportErrorDialog
import com.pgigi.pumpkincampus.schedule.SchedulePager
import com.pgigi.pumpkincampus.schedule.ShareCourseDialog
import com.pgigi.pumpkincampus.schedule.ShareImportDialog
import com.pgigi.pumpkincampus.schedule.TimetableMenuSheet
import com.pgigi.pumpkincampus.schedule.WeekCalculator
import com.pgigi.pumpkincampus.schedule.buildCourseListByWeek
import com.pgigi.pumpkincampus.schedule.currentLocalDate
import com.pgigi.pumpkincampus.schedule.dayIndexOf
import com.pgigi.pumpkincampus.schedule.weekCalculatorOf
import com.pgigi.pumpkincampus.settings.ImportCoursesDialog
import com.pgigi.pumpkincampus.settings.LessonTimesPage
import com.pgigi.pumpkincampus.settings.SchedulePluginPage
import com.pgigi.pumpkincampus.settings.ScheduleSettingsPage
import com.pgigi.pumpkincampus.settings.TimetableEditPage
import com.pgigi.pumpkincampus.settings.parseImportedSchedule
import com.pgigi.pumpkincampus.utils.buildShareMessage
import com.pgigi.pumpkincampus.utils.postShareContent
import com.pgigi.pumpkincampus.utils.rememberJsonFilePicker
import com.pgigi.pumpkincampus.utils.rememberTextSharer
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

private val WeekDayLabels = listOf("日", "一", "二", "三", "四", "五", "六")

/** 课表页根导航键。 */
@Serializable
internal data object TimetableRootKey : NavKey

/** 「课表设置」子页导航键（仅对当前课表生效的设置）。 */
@Serializable
internal data object ScheduleSettingsKey : NavKey

/** 「课表设置 → 教务系统插件」子页导航键（当前课表的插件选择/配置/同步）。 */
@Serializable
internal data object SchedulePluginKey : NavKey

/** 课表页「上课时间」子页导航键。 */
@Serializable
internal data object TimetableLessonTimesKey : NavKey

/** 课表页「时间表编辑」子页导航键。 */
@Serializable
internal data class TimetableLessonEditKey(val timetableId: String) : NavKey

/**
 * 课表页（navigation3 宿主）：
 * - 左右顶住屏幕边缘（不用卡片包裹）
 * - 页面不滚动：TopBar 固定，日期行固定在 TopBar 下方并保持不动，
 *   只有表格体在内部上下滚动（滚动时行从日期行下面经过）
 * - TopBar 下面那行字显示周次信息；点击标题区域回到当前周
 * - TopBar 右侧「+」按钮弹出 [AddCourseSheet] 添加课程
 * - 「+」与「⋯」之间是「导入课表」（MaterialIcons.Download2）与「导出课表」
 *   （MaterialIcons.Upload2）按钮，两者都点按弹 Salt UI [PopupMenu]：
 *   导入 = 从JSON文本导入 / 从文件导入 / 从分享口令导入（检测粘贴文本中的
 *   分享口令 → 拉取分享内容）；导出 = 以文件导出（系统「保存文件」选择器，
 *   `pumpkin_<时间戳>_schedule.json`）/ 在线分享课表（POST 分享服务拿口令 →
 *   弹「分享课表」对话框，可调起系统分享或复制含口令的推荐语）。
 *   两种文件导入的 JSON 解析以**导出信封 [com.pgigi.pumpkincampus.models.ScheduleExport]**
 *   为基础（含课表名称、课表时间、课表开始日期、周数、节数等设置），解析成功交由
 *   [HomeScreen] 弹「设置课表名称」对话框（只有「取消导入」「确定导入」两个选项），
 *   确定后**新建课表保存**导入内容；
 * - 「⋯」按钮（MaterialIcons.MoreHorizontal）弹出 [TimetableMenuSheet]：
 *   快速跳转周数滑块 + 多课表管理（Chip 切换 / 新建 / 重命名 / 删除 / 课表设置）
 *   + 快捷按钮（第一周的第一天、上课时间）
 * - 子页统一由 navigation3 [NavDisplay] 推送：[ScheduleSettingsPage]（课表设置，
 *   仅对当前课表生效）、[LessonTimesPage]、[TimetableEditPage]
 * - 课程详情底部有「编辑课程」入口：[EditCourseSheet] 修改或（二次确认后）删除
 *
 * @param courses 当前课表的**自定义课程**（由 [HomeScreen] 统一持有；可编辑）
 * @param pluginCourses 当前课表的**插件课程**（只读层，来自教务系统插件同步）：
 *   与自定义课程一起展示（外观一致），详情只读、需「转换为自定义课程」
 * @param settings **当前课表的生效设置**（课表专属设置 + 全局显示项，见
 *   [com.pgigi.pumpkincampus.models.AppSettings.withGlobalDisplay]）
 * @param schedules 全部课表（多课表管理，课程按课表隔离、互不相通）
 * @param activeScheduleId 当前课表 id
 * @param onScheduleSettingsChange 写回**当前课表的专属设置**（课表设置/上课时间/显示替换等）
 * @param onSwitchSchedule 切换当前课表
 * @param onCreateSchedule 新建课表（名称，初始设置 = 设置页默认值）
 * @param onRenameSchedule 重命名课表（id, 新名称）
 * @param onDeleteSchedule 删除课表（含其内课程，二次确认在弹层内完成）
 * @param onExportSchedule 「以文件导出」：调起系统「保存文件」选择器
 * （`pumpkin_<时间戳>_schedule.json`，自定义课程 + 生效设置）
 * @param buildExportJson 构建当前课表的导出信封 JSON（「以文件导出」与
 * 「在线分享课表」共用；无当前课表返回空串）
 * @param onImportReady 解析成功（文本 / 文件 / 分享口令）后回调，由 HomeScreen 统一弹
 * 「设置课表名称 → 确定导入」对话框，[ImportedSchedule] 为解析出的课程、随附设置与课表名称
 * @param onAddCourse 新增课程回调，由外壳写入**当前课表**并落盘 custom-schedule.json
 * @param onUpdateCourse 修改课程回调（原课程, 新课程）
 * @param onDeleteCourse 删除课程回调（已二次确认）
 * @param onConvertPluginCourse 插件课程「转换为自定义课程」回调（由 HomeScreen 弹确认，
 *   提示插件更新课表后可能出现的重复课程）
 * @param pluginUi 插件状态与回调（课表设置 → 教务系统插件子页使用；null = 未接入）
 * @param showScheduleNameInSubtitle 课表页子标题是否追加当前课表名称
 *   （**全局显示开关**：由 HomeScreen 传全局设置，不用课表专属设置的快照值）
 * @param pluginRefreshing 是否正在同步插件课表（下拉刷新的转圈状态）
 * @param onRefreshPluginCourses 下拉刷新：同步当前课表的插件课表
 *   （结果由 HomeScreen 用顶部 Toast 提示；未选插件/未安装时也会给提示）
 */
@OptIn(UnstableSaltUiApi::class)
@Composable
internal fun TimetablePage(
    courses: List<Course>,
    pluginCourses: List<Course> = emptyList(),
    settings: AppSettings = AppSettings(),
    schedules: List<CourseSchedule> = emptyList(),
    activeScheduleId: String = "",
    onScheduleSettingsChange: (AppSettings) -> Unit = {},
    onSwitchSchedule: (String) -> Unit = {},
    onCreateSchedule: (String) -> Unit = {},
    onRenameSchedule: (String, String) -> Unit = { _, _ -> },
    onDeleteSchedule: (String) -> Unit = {},
    onExportSchedule: () -> Unit = {},
    buildExportJson: () -> String = { "" },
    onImportReady: (ImportedSchedule) -> Unit = {},
    onAddCourse: (Course) -> Unit = {},
    onUpdateCourse: (Course, Course) -> Unit = { _, _ -> },
    onDeleteCourse: (Course) -> Unit = {},
    onConvertPluginCourse: (Course) -> Unit = {},
    pluginUi: PluginUiState? = null,
    showScheduleNameInSubtitle: Boolean = true,
    globalTimetables: List<LessonTimetable> = emptyList(),
    pluginRefreshing: Boolean = false,
    onRefreshPluginCourses: () -> Unit = {}
) {
    val backStack = remember { NavBackStack<NavKey>(TimetableRootKey) }

    fun push(key: NavKey) {
        if (backStack.lastOrNull() != key) backStack.add(key)
    }

    fun pop() {
        if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
    }

    // 展示层 = 自定义课程 + 插件课程；但**只读判定**只认「插件里有、自定义里没有」的课程：
    // 用户把插件课程转成自定义课程后两边内容一致，此时按自定义课程处理，
    // 这样转换后的副本还能继续编辑（否则会被只读层挡住编辑入口）
    val allCourses = remember(courses, pluginCourses) { courses + pluginCourses }
    val isPluginCourse: (Course) -> Boolean = remember(courses, pluginCourses) {
        val custom = courses.toSet()
        val readOnly = pluginCourses.filterNot { it in custom }.toSet()
        // 显式命名局部变量：避免 `{ ... }` 被解析成上一行的尾随 lambda
        val predicate: (Course) -> Boolean = { course -> course in readOnly }
        predicate
    }

    // 周状态提到导航宿主层：压栈 / 弹栈（root 场景卸载重挂）期间保持浏览周不变
    val weekCalculator = remember(settings.termStart) { weekCalculatorOf(settings.termStart) }
    val courseListByWeek = remember(allCourses, weekCalculator, settings.semesterWeekCount) {
        buildCourseListByWeek(allCourses, settings.semesterWeekCount, weekCalculator)
    }
    val currentWeekPage = remember(weekCalculator, settings.semesterWeekCount) {
        weekCalculator.getWeekNumber(currentLocalDate())
            .coerceIn(1, settings.semesterWeekCount) - 1
    }
    val pagerState = rememberPagerState(initialPage = currentWeekPage) {
        courseListByWeek.size
    }

    NavDisplay(
        backStack = backStack,
        modifier = Modifier.fillMaxSize(),
        onBack = { pop() },
        // 关掉返回预测动画：滑动返回跟手时不再把当前页缩小到 0.7 倍做预览，
        // 改为与普通返回（pop）一致的淡入淡出过渡
        predictivePopTransitionSpec = { _ ->
            ContentTransform(
                fadeIn(animationSpec = tween(700)),
                fadeOut(animationSpec = tween(700))
            )
        },
        entryProvider = entryProvider {
            entry<TimetableRootKey> {
                TimetableRootContent(
                    courses = allCourses,
                    isPluginCourse = isPluginCourse,
                    settings = settings,
                    schedules = schedules,
                    activeScheduleId = activeScheduleId,
                    weekCalculator = weekCalculator,
                    courseListByWeek = courseListByWeek,
                    currentWeekPage = currentWeekPage,
                    pagerState = pagerState,
                    onScheduleSettingsChange = onScheduleSettingsChange,
                    onOpenScheduleSettings = { push(ScheduleSettingsKey) },
                    onOpenLessonTimes = { push(TimetableLessonTimesKey) },
                    onSwitchSchedule = onSwitchSchedule,
                    onCreateSchedule = onCreateSchedule,
                    onRenameSchedule = onRenameSchedule,
                    onDeleteSchedule = onDeleteSchedule,
                    onExportSchedule = onExportSchedule,
                    buildExportJson = buildExportJson,
                    onImportReady = onImportReady,
                    onAddCourse = onAddCourse,
                    onUpdateCourse = onUpdateCourse,
                    onDeleteCourse = onDeleteCourse,
                    onConvertPluginCourse = onConvertPluginCourse,
                    showScheduleNameInSubtitle = showScheduleNameInSubtitle,
                    pluginRefreshing = pluginRefreshing,
                    onRefreshPluginCourses = onRefreshPluginCourses
                )
            }
            entry<ScheduleSettingsKey> {
                ScheduleSettingsPage(
                    settings = settings,
                    onSettingsChange = onScheduleSettingsChange,
                    scheduleName = schedules.firstOrNull { it.id == activeScheduleId }
                        ?.name.orEmpty(),
                    onRenameSchedule = { name ->
                        onRenameSchedule(activeScheduleId, name)
                    },
                    onOpenLessonTimes = { push(TimetableLessonTimesKey) },
                    pluginUi = pluginUi,
                    onOpenPlugin = { push(SchedulePluginKey) },
                    onBack = { pop() }
                )
            }
            entry<SchedulePluginKey> {
                SchedulePluginPage(
                    pluginUi = pluginUi,
                    scheduleName = schedules.firstOrNull { it.id == activeScheduleId }
                        ?.name.orEmpty(),
                    onBack = { pop() }
                )
            }
            entry<TimetableLessonTimesKey> {
                LessonTimesPage(
                    settings = settings,
                    onSettingsChange = onScheduleSettingsChange,
                    onBack = { pop() },
                    onOpenTimetable = { push(TimetableLessonEditKey(it)) },
                    // 全局时间表（内置「默认作息」+ 设置 → 全局课表设置里的默认时间表）：只当来源
                    globalTimetables = globalTimetables,
                    // 插件推荐时间表（读自插件包内的 timetables.json）
                    pluginSection = pluginUi?.timetableSection,
                    onOpenPluginSettings = { push(SchedulePluginKey) }
                )
            }
            entry<TimetableLessonEditKey> { key ->
                TimetableEditPage(
                    timetableId = key.timetableId,
                    settings = settings,
                    onSettingsChange = onScheduleSettingsChange,
                    onBack = { pop() }
                )
            }
        }
    )
}

/**
 * 课表页子标题：周次信息 + （可选）当前课表名称，如「第3周 周三 · 我的课表」。
 *
 * @param showScheduleName 全局显示开关（设置 → 外观 → 课表子标题显示当前课表名称）
 * @param scheduleName 当前课表名称；为空时即使开关打开也不追加（避免出现孤立的「·」）
 */
internal fun timetableSubtitle(
    weekInfo: String,
    scheduleName: String,
    showScheduleName: Boolean
): String = if (showScheduleName && scheduleName.isNotBlank()) {
    "$weekInfo · $scheduleName"
} else {
    weekInfo
}

/** 课表页根场景内容（[NavDisplay] 的 root entry；周状态由宿主提供以跨子页保持）。 */
@OptIn(UnstableSaltUiApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun TimetableRootContent(
    courses: List<Course>,
    isPluginCourse: (Course) -> Boolean,
    settings: AppSettings,
    schedules: List<CourseSchedule>,
    activeScheduleId: String,
    showScheduleNameInSubtitle: Boolean,
    pluginRefreshing: Boolean,
    onRefreshPluginCourses: () -> Unit,
    weekCalculator: WeekCalculator,
    courseListByWeek: List<List<Course>>,
    currentWeekPage: Int,
    pagerState: PagerState,
    onScheduleSettingsChange: (AppSettings) -> Unit,
    onOpenScheduleSettings: () -> Unit,
    onOpenLessonTimes: () -> Unit,
    onSwitchSchedule: (String) -> Unit,
    onCreateSchedule: (String) -> Unit,
    onRenameSchedule: (String, String) -> Unit,
    onDeleteSchedule: (String) -> Unit,
    onExportSchedule: () -> Unit,
    buildExportJson: () -> String,
    onImportReady: (ImportedSchedule) -> Unit,
    onAddCourse: (Course) -> Unit,
    onUpdateCourse: (Course, Course) -> Unit,
    onDeleteCourse: (Course) -> Unit,
    onConvertPluginCourse: (Course) -> Unit
) {
    val scope = rememberCoroutineScope()
    // 下拉刷新指示器状态（与 isRefreshing 一起决定箭头/转圈与位置）
    val pullToRefreshState = rememberPullToRefreshState()
    val todayLabel = remember {
        "周${WeekDayLabels[dayIndexOf(currentLocalDate())]}"
    }
    var showAddSheet by remember { mutableStateOf(false) }
    var showMoreSheet by remember { mutableStateOf(false) }
    var showImportMenu by remember { mutableStateOf(false) }
    var showExportMenu by remember { mutableStateOf(false) }
    var showTextImport by remember { mutableStateOf(false) }
    var showShareImport by remember { mutableStateOf(false) }
    // 在线分享：生成口令中 / 含口令的分享文案 / 分享失败提示
    var shareLoading by remember { mutableStateOf(false) }
    var shareMessage by remember { mutableStateOf<String?>(null) }
    var shareError by remember { mutableStateOf<String?>(null) }
    // 解析失败提示
    var importError by remember { mutableStateOf<String?>(null) }
    var editingCourse by remember { mutableStateOf<Course?>(null) }

    // 「从文件导入」：系统文件选择器读到文本后以导出信封约定解析
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

    val textSharer = rememberTextSharer()

    // 「在线分享课表」：上传导出信封 JSON → 服务端返回分享口令（30 分钟有效）
    // → 弹含推荐语的「分享课表」对话框（分享 / 复制）
    fun startShare() {
        val json = buildExportJson()
        if (json.isEmpty()) return
        shareLoading = true
        scope.launch {
            val key = postShareContent(json)
            shareLoading = false
            if (key == null) {
                shareError = "网络异常或分享服务不可用，请稍后重试"
            } else {
                shareMessage = buildShareMessage(key)
            }
        }
    }

    val viewedWeek = pagerState.currentPage + 1
    val weekInfo = if (viewedWeek == currentWeekPage + 1) {
        // 当前周：第几周 + 今天周几
        "第${viewedWeek}周 ${todayLabel}"
    } else {
        // 其他周：第几周 + 当前第几周
        "第${viewedWeek}周 当前第${currentWeekPage + 1}周"
    }
    // 子标题 = 周次信息 [+ 当前课表名称]（全局开关控制）
    val subtitleText = timetableSubtitle(
        weekInfo = weekInfo,
        scheduleName = schedules.firstOrNull { it.id == activeScheduleId }?.name.orEmpty(),
        showScheduleName = showScheduleNameInSubtitle
    )

    fun backToCurrentWeek() {
        scope.launch { pagerState.animateScrollToPage(currentWeekPage) }
    }

    BasicScreen(
        actionButton = null,
        title = "课表",
        // 课表 TopBar 下面那行字：周次信息（+ 当前课表名称），点击标题回到当前周
        subtitle = subtitleText,
        // TopBar 右侧：添加课程按钮 + 其右边的「导入课表」按钮（弹 Popup）+「⋯」课表工具按钮
        toolButtons = {
            TitleBarButton(
                onClick = { showAddSheet = true },
                icon = {
                    Icon(
                        painter = AddIcon,
                        contentDescription = "添加课程",
                        tint = SaltTheme.colors.text
                    )
                }
            )
            // 导入课表：Salt UI Popup（从JSON文本导入 / 从文件导入 / 从分享口令导入）
            Box {
                TitleBarButton(
                    onClick = { showImportMenu = true },
                    icon = {
                        Icon(
                            imageVector = MaterialIcons.Download2,
                            contentDescription = "导入课表",
                            tint = SaltTheme.colors.text
                        )
                    }
                )
                PopupMenu(
                    expanded = showImportMenu,
                    onDismissRequest = { showImportMenu = false },
                    offset = DpOffset(0.dp, 8.dp)
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
            // 导出当前课表：Salt UI Popup（以文件导出 / 在线分享课表）
            Box {
                TitleBarButton(
                    onClick = { showExportMenu = true },
                    icon = {
                        Icon(
                            imageVector = MaterialIcons.Upload2,
                            contentDescription = "导出课表",
                            tint = SaltTheme.colors.text
                        )
                    }
                )
                PopupMenu(
                    expanded = showExportMenu,
                    onDismissRequest = { showExportMenu = false },
                    offset = DpOffset(0.dp, 8.dp)
                ) {
                    PopupMenuItem(
                        onClick = {
                            showExportMenu = false
                            onExportSchedule()
                        },
                        text = "以文件导出"
                    )
                    PopupMenuItem(
                        onClick = {
                            showExportMenu = false
                            startShare()
                        },
                        text = "在线分享课表"
                    )
                }
            }
            TitleBarButton(
                onClick = { showMoreSheet = true },
                icon = {
                    Icon(
                        imageVector = MaterialIcons.MoreHorizontal,
                        contentDescription = "课表工具",
                        tint = SaltTheme.colors.text
                    )
                }
            )
        },
        overlay = { padding ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(padding.calculateTopPadding())
                    .align(Alignment.TopStart)
                    // 右侧留出四个 toolButtons 区域，避免拦掉右上角按钮的点击
                    .padding(end = 224.dp)
                    // indication = null：去掉默认水波纹，点击标题回到本周时不再出现灰色块
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { backToCurrentWeek() }
                    )
            )
        }
    ) { contentPadding ->
        // 不叠加 contentPadding 的底部安全区：本页位于自带 navigationBarsPadding 的
        // BottomBar 上方，再垫一层安全区会在课表与底部导航栏之间多出一条固定空白。
        // 去掉后课表区域一直延伸到导航栏：行高不足时下方留白（该多高就多高），
        // 行高调大到放不下时表格内部滚动，内容始终排到导航栏、不产生额外空隙
        //
        // 下拉刷新（PullToRefreshBox）：下拉同步当前课表的插件课表；
        // 手势由课表内部的纵向滚动（SchedulePager 的 verticalScroll）分发给嵌套滚动
        //
        // 为什么用自定义指示器（不用 PullToRefreshDefaults.Indicator）：
        // 1. Salt 的 BasicScreen 在「标题栏高度」处裁剪页面内容，标题栏还叠在内容之上；
        // 2. 官方指示器的绘制被裁剪在自身 40dp 槽位内，靠「从槽位顶边滑出」来隐藏自己
        //    （源码：绘制位置 = 布局位置 + 下拉进度 × maxDistance − 40dp）。
        // 两条合起来的结果：槽位上方只要有东西——标题栏的裁剪线、或者课表的日期行
        // （ScheduleHeaderHeight = 44dp，正好在内容顶部）——图标就会被切掉一半，
        // 看起来像「被标题栏/日期行挡住」。
        // 所以这里自己摆一个固定位置的指示器：紧贴标题栏下沿，永远完整显示，
        // 下拉时按进度画弧、刷新时转圈，并随下拉轻微下移（8dp）给一点跟随手感。
        // 它不消费触摸事件，不影响下拉手势与日期行。
        val topInset = contentPadding.calculateTopPadding()
        PullToRefreshBox(
            isRefreshing = pluginRefreshing,
            onRefresh = onRefreshPluginCourses,
            // 必须把 state 传进去！否则 PullToRefreshBox 内部会自建一个 state，
            // 下面指示器读到的 distanceFraction 永远是 0（下拉过程中毫无反馈，
            // 只有 isRefreshing 变 true 时图标才「突然出现」）
            state = pullToRefreshState,
            modifier = Modifier
                .fillMaxSize()
                .padding(top = topInset),
            indicator = {
                val pulledFraction = pullToRefreshState.distanceFraction
                // 静止（未下拉、未刷新）时不显示；下拉或刷新中才出现
                if (pluginRefreshing || pulledFraction > 0f) {
                    // 刷新中按拉满算：设置页/自动同步触发的刷新也要能看见
                    val progress = if (pluginRefreshing) 1f else pulledFraction.coerceIn(0f, 1f)
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 6.dp)
                            .offset(y = (progress * 8).dp)
                            .size(40.dp)
                            // 随下拉进度淡入 + 轻微放大，避免刚下拉时「突然冒出来」
                            .graphicsLayer {
                                alpha = (progress * 3f).coerceIn(0f, 1f)
                                val scale = 0.9f + 0.1f * progress
                                scaleX = scale
                                scaleY = scale
                            }
                            .shadow(2.dp, CircleShape)
                            .background(SaltTheme.colors.popup, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (pluginRefreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = SaltTheme.colors.highlight,
                                strokeWidth = 2.dp
                            )
                        } else {
                            // 下拉进度弧：拉过阈值即触发刷新
                            CircularProgressIndicator(
                                progress = { pulledFraction },
                                modifier = Modifier.size(20.dp),
                                color = SaltTheme.colors.highlight,
                                strokeWidth = 2.dp,
                                trackColor = SaltTheme.colors.stroke
                            )
                        }
                    }
                }
            }
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // 课表撑满 TopBar 到底部之间的整个区域，左右不留边距
                SchedulePager(
                    courseListByWeek = courseListByWeek,
                    lessonTimes = settings.activeLessonTimes(),
                    lessonCount = settings.lessonCount,
                    weekCalculator = weekCalculator,
                    pagerState = pagerState,
                    onEditCourse = { editingCourse = it },
                    isPluginCourse = isPluginCourse,
                    onConvertCourse = onConvertPluginCourse,
                    cellHeight = settings.cellHeightDp.dp,
                    showGridLines = settings.showGridLines,
                    settings = settings,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
            }
        }
    }

    if (showAddSheet) {
        AddCourseSheet(
            onDismissRequest = { showAddSheet = false },
            onAdd = onAddCourse,
            weekCount = settings.semesterWeekCount,
            lessonCount = settings.lessonCount
        )
    }

    // 从JSON文本导入：粘贴存储的 JSON → 解析成功后交给 HomeScreen 统一流程
    if (showTextImport) {
        ImportCoursesDialog(
            onDismissRequest = { showTextImport = false },
            onImport = { parsed ->
                showTextImport = false
                onImportReady(parsed)
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

    // 「从分享口令导入」：粘贴消息 → 检测口令 → 拉取解析 → 统一导入流程
    if (showShareImport) {
        ShareImportDialog(
            onDismissRequest = { showShareImport = false },
            onImport = { parsed ->
                showShareImport = false
                onImportReady(parsed)
            }
        )
    }

    // 在线分享：正在生成分享口令
    if (shareLoading) {
        AlertDialog(
            onDismissRequest = {},
            containerColor = SaltTheme.colors.popup,
            title = {
                Text(
                    text = "分享课表",
                    fontSize = SaltTheme.textStyles.main.fontSize,
                    color = SaltTheme.colors.text
                )
            },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = SaltTheme.colors.highlight,
                        strokeWidth = 2.dp
                    )
                    Text(
                        text = "正在生成分享口令…",
                        fontSize = SaltTheme.textStyles.main.fontSize,
                        color = SaltTheme.colors.subText,
                        modifier = Modifier.padding(start = 10.dp)
                    )
                }
            },
            confirmButton = {}
        )
    }

    // 在线分享成功：含口令的推荐语（分享 / 复制）
    shareMessage?.let { message ->
        ShareCourseDialog(
            message = message,
            sharer = textSharer,
            onDismissRequest = { shareMessage = null }
        )
    }

    // 分享失败提示（可任意关闭）
    shareError?.let { message ->
        ImportErrorDialog(
            message = message,
            onDismiss = { shareError = null },
            title = "分享失败"
        )
    }

    // 「⋯」课表工具：快速跳转周数 + 多课表管理 + 快捷按钮 + 课表设置入口
    if (showMoreSheet) {
        TimetableMenuSheet(
            pagerState = pagerState,
            weekCount = settings.semesterWeekCount,
            weekCalculator = weekCalculator,
            schedules = schedules,
            activeScheduleId = activeScheduleId,
            settings = settings,
            onSettingsChange = onScheduleSettingsChange,
            onSwitchSchedule = onSwitchSchedule,
            onCreateSchedule = onCreateSchedule,
            onRenameSchedule = onRenameSchedule,
            onDeleteSchedule = onDeleteSchedule,
            onOpenScheduleSettings = {
                showMoreSheet = false
                onOpenScheduleSettings()
            },
            onOpenLessonTimes = {
                showMoreSheet = false
                onOpenLessonTimes()
            },
            onDismissRequest = { showMoreSheet = false }
        )
    }

    // 编辑 / 删除课程（删除需二次确认，确认由 EditCourseSheet 内部处理）
    editingCourse?.let { original ->
        EditCourseSheet(
            course = original,
            onDismissRequest = { editingCourse = null },
            onSave = { updated -> onUpdateCourse(original, updated) },
            onDelete = { onDeleteCourse(original) },
            weekCount = settings.semesterWeekCount,
            lessonCount = settings.lessonCount
        )
    }
}
