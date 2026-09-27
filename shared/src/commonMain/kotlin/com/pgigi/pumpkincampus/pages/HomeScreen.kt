package com.pgigi.pumpkincampus.pages

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.moriafly.salt.ui.BottomBar
import com.moriafly.salt.ui.BottomBarItem
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.UnstableSaltUiApi
import com.pgigi.pumpkincampus.components.AgendaIcon
import com.pgigi.pumpkincampus.components.SettingsIcon
import com.pgigi.pumpkincampus.components.TimetableIcon
import com.pgigi.pumpkincampus.constants.FileName
import com.pgigi.pumpkincampus.models.AppSettings
import com.pgigi.pumpkincampus.models.Course
import com.pgigi.pumpkincampus.models.CourseBook
import com.pgigi.pumpkincampus.models.CourseSchedule
import com.pgigi.pumpkincampus.models.DefaultScheduleId
import com.pgigi.pumpkincampus.models.DefaultScheduleName
import com.pgigi.pumpkincampus.models.ImportedSchedule
import com.pgigi.pumpkincampus.models.ScheduleCache
import com.pgigi.pumpkincampus.models.ScheduleExport
import com.pgigi.pumpkincampus.schedule.ScheduleNameDialog
import com.pgigi.pumpkincampus.schedule.dayIndexOf
import com.pgigi.pumpkincampus.schedule.weekCalculatorOf
import com.pgigi.pumpkincampus.settings.DatePickerDialog
import com.pgigi.pumpkincampus.settings.SettingsPage
import com.pgigi.pumpkincampus.utils.FileStoreUtils
import com.pgigi.pumpkincampus.utils.JsonUtil
import com.pgigi.pumpkincampus.utils.rememberJsonFileSaver
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * 主页外壳：底部 [BottomBar] 选择页面，内容区用 [Crossfade] 淡出淡入切换。
 *
 * 按要求不使用 Pager（不做左右滑动翻页），页面完全由底部导航的选择决定：
 * - 0：日程（[AgendaPage]）
 * - 1：课表（[TimetablePage]）
 * - 2：设置（[SettingsPage]：导入课程、第一周的第一天、课表数据、课表外观）
 *
 * 这里统一持有**多课表档案**与应用设置，各页面共享同一份数据：
 * - 每套课表（[CourseSchedule]）持有自己的一组自定义课程与**专属设置**
 *   （第一周第一天、辅助线、课表外观、上课时间、节数、周数等），互不相通、只对本课表生效；
 *   设置页（settings.json 的 [AppSettings]）是**默认值**——新建课表时快照、
 *   课表未定制时回落
 * - 课表页 TopBar 的「⋯」按钮可切换 / 新建 / 重命名 / 删除课表、打开课表设置与快捷项
 *
 * 课表存储（参照 Pumpkin-Toolkit 的 FileStoreUtils + JsonUtil 方式）：
 * - `custom-schedule.json` —— 多课表档案（[CourseBook]：全部课表 + 当前课表），
 *   添加 / 修改 / 删除 / 导入课程、课表管理都写回这里；
 *   旧格式（[ScheduleCache]：`{updateTime, courses}`）自动迁移为单个默认课表
 * - `plugin-schedule.json` —— 预留的插件课表档案，首次启动创建空档案，
 *   后续接入与插件有关的课表数据源时填充（当前只读并入展示，恒为空）
 * - `schedule.json` —— 上一轮的整体存档，仅作为首次迁移来源，不再写入
 * - `settings.json` —— 应用设置（[AppSettings]：第一周第一天、课表数据、课表外观）
 *
 * @param onColorModeChange 颜色模式变化上报（加载 settings.json 后与每次修改时回调，
 * 取值 `system` 跟随系统 / `light` 浅色 / `dark` 深色），由 [App] 应用主题
 */
@OptIn(UnstableSaltUiApi::class, ExperimentalTime::class)
@Composable
internal fun HomeScreen(onColorModeChange: (String) -> Unit = {}) {
    var selectedPage by rememberSaveable { mutableStateOf(0) }

    // 多课表档案（custom-schedule.json）：全部课表 + 当前打开的课表
    var book by remember { mutableStateOf(CourseBook()) }
    // 插件课表（plugin-schedule.json，预留），只读并入展示
    val pluginCourses = remember { mutableStateListOf<Course>() }
    // 当前课表的自定义课程（不同课表互不相通）
    val customCourses = book.activeSchedule()?.courses ?: emptyList()
    val displayCourses = customCourses + pluginCourses
    // 应用设置（settings.json）= **默认值**：新建课表时快照、课表未定制时回落
    var settings by remember { mutableStateOf(AppSettings()) }
    // 当前课表的生效设置：该课表专属设置，未定制（null）时用默认值
    val scheduleSettings = book.activeSchedule()?.settings ?: settings
    // 每次**新建课表**后弹「第一周的第一天」选择器，提示用户为该课表设置开课日期
    var promptTermSetup by remember { mutableStateOf(false) }
    // 导入暂存：解析成功的课程与随附设置；「设置课表名称」确认后落库
    var pendingImport by remember { mutableStateOf<ImportedSchedule?>(null) }

    // 启动加载存档（课程 + 设置）
    LaunchedEffect(Unit) {
        val (loadedBook, needsPersist) = loadCourseBook()
        book = loadedBook
        if (needsPersist) {
            // 迁移（旧格式 / 首次启动种子）后立即落盘
            writeCourseBook(loadedBook)
        }

        // 预留插件课表档案：首次创建空档案，后续接入插件数据源时读取
        if (!FileStoreUtils.exists(FileName.PLUGIN_SCHEDULE)) {
            writeScheduleCache(FileName.PLUGIN_SCHEDULE, emptyList())
        }
        pluginCourses.addAll(
            readScheduleCache(FileName.PLUGIN_SCHEDULE)?.courses ?: emptyList()
        )

        // 应用设置
        settings = readSettings()
    }

    // 设置变更后写回 settings.json（含首次加载后的写入，值相同，无副作用）
    LaunchedEffect(settings) {
        writeSettings(settings)
    }

    // 颜色模式（跟随系统 / 浅色 / 深色）：加载后与每次修改时上报给 App 应用主题
    LaunchedEffect(settings.colorMode) {
        onColorModeChange(settings.colorMode)
    }

    /** 多课表档案变更后写回 custom-schedule.json */
    fun persistBook() {
        writeCourseBook(book)
    }

    /** 修改**当前课表**的自定义课程（其他课表不受影响），并写回档案。 */
    fun mutateActiveCourses(transform: (List<Course>) -> List<Course>) {
        book = book.copy(
            schedules = book.schedules.map { schedule ->
                if (schedule.id == book.activeScheduleId) {
                    schedule.copy(courses = transform(schedule.courses))
                } else {
                    schedule
                }
            }
        )
        persistBook()
    }

    /** 写回**当前课表的专属设置**（只对该课表生效；首次定制时从默认值实体化）。 */
    val updateScheduleSettings: (AppSettings) -> Unit = { newSettings ->
        book = book.copy(
            schedules = book.schedules.map { schedule ->
                if (schedule.id == book.activeScheduleId) {
                    schedule.copy(settings = newSettings)
                } else {
                    schedule
                }
            }
        )
        persistBook()
    }

    // 系统「保存文件」选择器（导出课表用，与导入对称）
    val saveJson = rememberJsonFileSaver()

    /**
     * 构建**当前课表**的导出信封 JSON（「以文件导出」与「在线分享课表」共用）：
     * 课表名称 + 自定义课程 + 生效设置（课表时间、课表开始日期、学期周数、一天课程节数等）。
     * 无当前课表时返回空串。
     */
    val buildExportJson: () -> String = {
        val active = book.activeSchedule()
        if (active == null) {
            ""
        } else {
            JsonUtil.toJson(
                ScheduleExport(
                    exportTime = Clock.System.now().toEpochMilliseconds(),
                    // 导出携带课表名称，导入时据此提示设置课表名称
                    name = active.name,
                    courses = active.courses,
                    settings = scheduleSettings
                ),
                ScheduleExport.serializer()
            )
        }
    }

    /**
     * 「以文件导出」：调起系统「保存 / 存储文件」选择器，
     * 文件名 `pumpkin_<时间戳>_schedule.json`，内容为导出信封 JSON。
     */
    val exportSchedule: () -> Unit = {
        val active = book.activeSchedule()
        if (active != null) {
            saveJson(
                "pumpkin_${Clock.System.now().toEpochMilliseconds()}_schedule.json",
                buildExportJson()
            )
        }
    }

    /**
     * 课表导入（设置页 / 课表页解析成功后统一进来）：弹「设置课表名称」对话框
     * （只有「取消导入」「确定导入」两个选项），确定后**新建一套课表**保存导入内容。
     */
    val onImportReady: (ImportedSchedule) -> Unit = { parsed ->
        pendingImport = parsed
    }

    /**
     * 导入落库：**新建课表保存**——以用户确认的名称新建一套课表放入导入课程并切过去；
     * 解析自导出信封的随附设置存在则用之，否则快照设置页默认值；现有课表不受影响。
     * 随附设置带「第一周的第一天」（termStart）时导入后不再弹日期选择器。
     */
    val importScheduleAsNew: (ImportedSchedule) -> Unit = { imported ->
        val id = "sc${Clock.System.now().toEpochMilliseconds()}"
        book = book.copy(
            schedules = book.schedules + CourseSchedule(
                id = id,
                // 课表名称：用户在名称对话框确认的值（空则回落自动编号）
                name = imported.name.ifBlank { "课表${book.schedules.size + 1}" },
                courses = imported.courses,
                // 随附设置存在则用之，否则快照设置页默认值
                settings = imported.settings ?: settings
            ),
            activeScheduleId = id
        )
        persistBook()
        // 导入信封自带「第一周的第一天」（settings.termStart，导出始终携带）时
        // 不再弹日期选择器；旧格式导入（无设置）或未设置开课日期时才提示
        if (imported.settings?.termStart.isNullOrBlank()) {
            promptTermSetup = true
        }
    }

    val addCourse: (Course) -> Unit = { course ->
        mutateActiveCourses { it + course }
    }

    val updateCourse: (Course, Course) -> Unit = { original, updated ->
        mutateActiveCourses { list ->
            val index = list.indexOf(original)
            if (index >= 0) {
                list.toMutableList().apply { set(index, updated) }
            } else {
                list + updated
            }
        }
    }

    val deleteCourse: (Course) -> Unit = { course ->
        mutateActiveCourses { list -> list - course }
    }

    // —— 课表管理（多课表） ——

    val switchSchedule: (String) -> Unit = { id ->
        if (id != book.activeScheduleId) {
            book = book.copy(activeScheduleId = id)
            persistBook()
        }
    }

    val createSchedule: (String) -> Unit = { name ->
        val id = "sc${Clock.System.now().toEpochMilliseconds()}"
        book = book.copy(
            schedules = book.schedules + CourseSchedule(
                id = id,
                name = name.ifBlank { "课表${book.schedules.size + 1}" },
                courses = emptyList(),
                // 新建课表默认使用的设置 = 设置页里的默认值（快照）
                settings = settings
            ),
            activeScheduleId = id
        )
        persistBook()
        // 新建课表：提示设置「第一周的第一天」
        promptTermSetup = true
    }

    val renameSchedule: (String, String) -> Unit = { id, newName ->
        if (newName.isNotBlank()) {
            book = book.copy(
                schedules = book.schedules.map { schedule ->
                    if (schedule.id == id) schedule.copy(name = newName) else schedule
                }
            )
            persistBook()
        }
    }

    val deleteSchedule: (String) -> Unit = { id ->
        val remaining = book.schedules.filterNot { it.id == id }
        // 兜底：不允许删到一套不剩（UI 在仅剩一套时隐藏删除入口）
        if (remaining.isNotEmpty()) {
            book = book.copy(
                schedules = remaining,
                activeScheduleId = if (book.activeScheduleId == id) {
                    remaining.first().id
                } else {
                    book.activeScheduleId
                }
            )
            persistBook()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            Crossfade(
                targetState = selectedPage,
                animationSpec = tween(durationMillis = 250),
                label = "homePage"
            ) { page ->
                when (page) {
                    0 -> AgendaPage(
                        courses = displayCourses,
                        settings = scheduleSettings,
                        onUpdateCourse = updateCourse,
                        onDeleteCourse = deleteCourse
                    )
                    1 -> TimetablePage(
                        courses = displayCourses,
                        settings = scheduleSettings,
                        schedules = book.schedules,
                        activeScheduleId = book.activeScheduleId,
                        onScheduleSettingsChange = updateScheduleSettings,
                        onSwitchSchedule = switchSchedule,
                        onCreateSchedule = createSchedule,
                        onRenameSchedule = renameSchedule,
                        onDeleteSchedule = deleteSchedule,
                        onExportSchedule = exportSchedule,
                        buildExportJson = buildExportJson,
                        onImportReady = onImportReady,
                        onAddCourse = addCourse,
                        onUpdateCourse = updateCourse,
                        onDeleteCourse = deleteCourse
                    )
                    else -> SettingsPage(
                        settings = settings,
                        courses = displayCourses,
                        onSettingsChange = { settings = it },
                        onImportReady = onImportReady
                    )
                }
            }
        }

        // 顶部分隔线
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(SaltTheme.colors.stroke)
        )

        BottomBar(modifier = Modifier.navigationBarsPadding()) {
            BottomBarItem(
                state = selectedPage == 0,
                onClick = { selectedPage = 0 },
                painter = AgendaIcon,
                text = "日程"
            )
            BottomBarItem(
                state = selectedPage == 1,
                onClick = { selectedPage = 1 },
                painter = TimetableIcon,
                text = "课表"
            )
            BottomBarItem(
                state = selectedPage == 2,
                onClick = { selectedPage = 2 },
                painter = SettingsIcon,
                text = "设置"
            )
        }
    }

    // 课表导入：解析成功 → 「设置课表名称」，只有「取消导入」「确定导入」两个选项
    // （确定 = 新建课表保存导入内容并切过去；取消 = 放弃本次导入）
    pendingImport?.let { imported ->
        // 文本提示 = 导出信封里的课表名称；文件没写名称时用自动编号「课表N」
        val hint = imported.name.ifBlank { "课表${book.schedules.size + 1}" }
        ScheduleNameDialog(
            title = "设置课表名称",
            initialName = imported.name,
            hint = hint,
            cancelText = "取消",
            confirmText = "导入",
            onConfirm = { name ->
                pendingImport = null
                // 输入为空点「确定导入」→ 使用文本提示里的名称
                importScheduleAsNew(imported.copy(name = name.ifBlank { hint }))
            },
            onDismissRequest = { pendingImport = null }
        )
    }

    // 每次新建课表：提示设置「第一周的第一天」
    // （确定才生效；取消可稍后在课表页「⋯ → 课表设置」里再改）
    if (promptTermSetup) {
        val effective = book.activeSchedule()?.settings ?: settings
        val anchor = weekCalculatorOf(effective.termStart).getWeekFirstDay(1)
        DatePickerDialog(
            selected = anchor,
            firstDayOfWeek = dayIndexOf(anchor),
            onPicked = { date ->
                updateScheduleSettings(effective.copy(termStart = date.toString()))
                promptTermSetup = false
            },
            onDismissRequest = { promptTermSetup = false }
        )
    }
}

/** 只探测字段形状用的 JSON（与 [JsonUtil] 同配置）。 */
private val ShapeProbeJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    encodeDefaults = true
}

/**
 * 读取多课表档案：
 * - 新格式（含 `schedules`）直接解析；
 * - 旧格式（[ScheduleCache]：`{updateTime, courses}`）迁移为单个默认课表；
 * - 无档案 / 解析失败时用 `schedule.json` 种子（没有则空课表）迁移。
 *
 * 返回归一化后的档案与是否需要立即写回（迁移时为 true）。
 */
private fun loadCourseBook(): Pair<CourseBook, Boolean> {
    val raw = FileStoreUtils.readString(FileName.CUSTOM_SCHEDULE)
    if (raw != null) {
        // 新格式：含 schedules 字段
        try {
            val element = ShapeProbeJson.parseToJsonElement(raw)
            if (element is JsonObject && "schedules" in element) {
                return ShapeProbeJson
                    .decodeFromJsonElement(CourseBook.serializer(), element)
                    .normalized() to false
            }
        } catch (_: Exception) {
            // 继续尝试旧格式
        }
        // 旧格式：{updateTime, courses} → 迁移为默认课表
        try {
            val old = JsonUtil.parseJson(raw, ScheduleCache.serializer())
            return CourseBook(
                activeScheduleId = DefaultScheduleId,
                schedules = listOf(
                    CourseSchedule(
                        id = DefaultScheduleId,
                        name = DefaultScheduleName,
                        courses = old.courses
                    )
                )
            ) to true
        } catch (_: Exception) {
            // 损坏档案 → 落到种子迁移
        }
    }

    // 首次启动：迁移上一轮整体存档（没有则空课表，不再使用演示数据）
    val seed = readScheduleCache(FileName.SCHEDULE)?.courses ?: emptyList()
    return CourseBook(
        activeScheduleId = DefaultScheduleId,
        schedules = listOf(
            CourseSchedule(
                id = DefaultScheduleId,
                name = DefaultScheduleName,
                courses = seed
            )
        )
    ) to true
}

/** 读取课表存档（旧 [ScheduleCache] 格式：schedule.json 种子 / plugin-schedule.json）。 */
private fun readScheduleCache(fileName: String): ScheduleCache? =
    FileStoreUtils.readString(fileName)?.let { raw ->
        try {
            JsonUtil.parseJson(raw, ScheduleCache.serializer())
        } catch (_: Exception) {
            null
        }
    }

/** 写入旧格式课表存档（仅用于 plugin-schedule.json）。 */
@OptIn(ExperimentalTime::class)
private fun writeScheduleCache(fileName: String, courses: List<Course>) {
    FileStoreUtils.writeString(
        fileName,
        JsonUtil.toJson(
            ScheduleCache(
                updateTime = Clock.System.now().toEpochMilliseconds(),
                courses = courses
            ),
            ScheduleCache.serializer()
        )
    )
}

/** 写入多课表档案（custom-schedule.json）。 */
private fun writeCourseBook(book: CourseBook) {
    FileStoreUtils.writeString(
        FileName.CUSTOM_SCHEDULE,
        JsonUtil.toJson(book, CourseBook.serializer())
    )
}

/** 读取应用设置；文件不存在或解析失败用默认值。 */
private fun readSettings(): AppSettings =
    FileStoreUtils.readString(FileName.SETTINGS)?.let { raw ->
        try {
            JsonUtil.parseJson(raw, AppSettings.serializer())
        } catch (_: Exception) {
            null
        }
    } ?: AppSettings()

/** 写入应用设置（settings.json）。 */
private fun writeSettings(settings: AppSettings) {
    FileStoreUtils.writeString(
        FileName.SETTINGS,
        JsonUtil.toJson(settings, AppSettings.serializer())
    )
}
