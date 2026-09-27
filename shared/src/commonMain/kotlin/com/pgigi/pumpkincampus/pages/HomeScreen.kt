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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
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
import com.pgigi.pumpkincampus.plugin.InstalledPlugin
import com.pgigi.pumpkincampus.plugin.PluginManager
import com.pgigi.pumpkincampus.plugin.PluginRunRequest
import com.pgigi.pumpkincampus.plugin.PluginScheduleArchive
import com.pgigi.pumpkincampus.plugin.PluginScheduleStore
import com.pgigi.pumpkincampus.plugin.PluginUiState
import com.pgigi.pumpkincampus.plugin.SavedPluginConfig
import com.pgigi.pumpkincampus.plugin.buildPluginConfigJson
import com.pgigi.pumpkincampus.plugin.configTypeOf
import com.pgigi.pumpkincampus.plugin.deletePluginSecrets
import com.pgigi.pumpkincampus.plugin.loadPluginConfig
import com.pgigi.pumpkincampus.plugin.migratePlaintextPluginSecrets
import com.pgigi.pumpkincampus.plugin.runPlugin
import com.pgigi.pumpkincampus.plugin.savePluginConfig
import com.pgigi.pumpkincampus.plugin.withAttempt
import com.pgigi.pumpkincampus.plugin.withSyncFailure
import com.pgigi.pumpkincampus.plugin.withSyncSuccess
import com.pgigi.pumpkincampus.schedule.ConvertPluginCoursesDialog
import com.pgigi.pumpkincampus.schedule.ImportErrorDialog
import com.pgigi.pumpkincampus.schedule.ScheduleNameDialog
import com.pgigi.pumpkincampus.schedule.dayIndexOf
import com.pgigi.pumpkincampus.schedule.weekCalculatorOf
import com.pgigi.pumpkincampus.settings.DatePickerDialog
import com.pgigi.pumpkincampus.settings.SettingsPage
import com.pgigi.pumpkincampus.utils.FileStoreUtils
import com.pgigi.pumpkincampus.utils.JsonUtil
import com.pgigi.pumpkincampus.utils.rememberJsonFileSaver
import io.androidpoet.dhyantoast.ToastAlignment
import io.androidpoet.dhyantoast.ToastCategory
import io.androidpoet.dhyantoast.ToastHost
import io.androidpoet.dhyantoast.rememberToastHostState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * 主页外壳：底部 [BottomBar] 选择页面，内容区用 [Crossfade] 淡出淡入切换。
 *
 * 按要求不使用 Pager（不做左右滑动翻页），页面完全由底部导航的选择决定：
 * - 0：日程（[AgendaPage]）
 * - 1：课表（[TimetablePage]）
 * - 2：设置（[SettingsPage]：导入课程、颜色模式、辅助线、课表外观、教务系统插件）
 *
 * 这里统一持有**多课表档案**与应用设置，各页面共享同一份数据：
 * - 每套课表（[CourseSchedule]）持有自己的一组自定义课程与**专属设置**
 *   （第一周第一天、上课时间、节数、周数、显示替换），互不相通、只对本课表生效；
 *   未定制的课表回落到设置页（settings.json 的 [AppSettings]）
 * - **全局显示项**（颜色模式、子标题课表名、辅助线、课表外观参数）只读 settings.json，
 *   所有课表共用——展示时由 [AppSettings.withGlobalDisplay] 合并进当前课表设置
 * - 课表页 TopBar 的「⋯」按钮可切换 / 新建 / 重命名 / 删除课表、打开课表设置与快捷项
 *
 * **课程分两层，互不混写**：
 * - 自定义课程（`custom-schedule.json`）—— 用户自由增删改
 * - 插件课程（只读层）—— 插件同步结果 + 分享/导入信封带进来的快照；
 *   不能直接编辑，课程详情里只能「转换为自定义课程」（复制一份进自定义课程，
 *   插件层保留，因此插件之后更新课表可能出现重复课程，转换前会提示用户）
 * - 分享 / 导出会带上插件课程（接收方看到完整课表），但**不带插件配置**
 *
 * 课表存储（参照 Pumpkin-Toolkit 的 FileStoreUtils + JsonUtil 方式）：
 * - `custom-schedule.json` —— 多课表档案（[CourseBook]：全部课表 + 当前课表），
 *   添加 / 修改 / 删除 / 导入课程、课表管理都写回这里；
 *   旧格式（[ScheduleCache]：`{updateTime, courses}`）自动迁移为单个默认课表
 * - `plugin-schedule.json` —— 插件课表档案（v2：按课表保存插件同步结果；
 *   旧 `{updateTime, courses}` 格式自动迁移为跨课表叠加层），由
 *   `PluginScheduleStore` 读写，插件同步结果只读并入展示
 * - `schedule.json` —— 上一轮的整体存档，仅作为首次迁移来源，不再写入
 * - `settings.json` —— 应用设置（[AppSettings] 的**全局显示项**：颜色模式、子标题课表名、
 *   辅助线、课表外观参数；多课表共用，不随课表快照变化）
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
    // —— 教务系统插件：全局安装列表 + 按课表的同步档案（plugin-schedule.json v2） ——
    var installedPlugins by remember { mutableStateOf<List<InstalledPlugin>>(emptyList()) }
    var pluginArchive by remember { mutableStateOf(PluginScheduleArchive()) }
    // 正在同步的课表 id 集合（同一课表同一时间只跑一个插件）
    var pluginSyncingIds by remember { mutableStateOf(setOf<String>()) }
    // 正在从 zip 安装插件
    var pluginInstalling by remember { mutableStateOf(false) }
    // 安装/卸载结果提示（统一弹窗）
    var pluginMessage by remember { mutableStateOf<String?>(null) }
    val homeScope = rememberCoroutineScope()
    // 顶部 Toast（dhyantoast）：插件课表同步等结果提示，位置固定在屏幕上边
    val toastHostState = rememberToastHostState()

    /** 弹一条顶部 Toast：成功用绿色 ✓，失败用红色（[error] = true）。 */
    fun showToast(message: String, error: Boolean = false) {
        homeScope.launch {
            toastHostState.showToast(
                message = message,
                category = if (error) ToastCategory.Error else ToastCategory.Success
            )
        }
    }
    /*
     * 课程分两层存放，互不混写：
     * - **自定义课程**（CourseSchedule.courses）：用户自由增删改，写入 custom-schedule.json
     * - **插件课程**（只读层）：插件同步结果 + 旧存档迁移来的叠加层。
     *   插件数据不写进自定义课程、不能直接编辑，要修改必须先「转换为自定义课程」。
     *   分享/导出时会**清除插件信息**（把插件课程并入导出课程），见 [buildExportJson]
     */
    val customCourses = book.activeSchedule()?.courses ?: emptyList()
    val pluginCourses = (pluginArchive.schedules[book.activeScheduleId]?.courses ?: emptyList()) +
        (pluginArchive.legacy?.courses ?: emptyList())
    /** 展示用合并列表（自定义在前、插件在后）：设置页预览等只读展示场景使用。 */
    val displayCourses = customCourses + pluginCourses
    // 应用设置（settings.json）= **全局显示项**（颜色模式、子标题课表名、辅助线、课表外观）
    var settings by remember { mutableStateOf(AppSettings()) }
    // 当前课表的生效设置：课表专属设置（未定制时用全局设置兜底）
    // + 全局显示项（辅助线/课表外观等，所有课表共用，见 AppSettings.withGlobalDisplay）
    val scheduleSettings = (book.activeSchedule()?.settings ?: settings).withGlobalDisplay(settings)
    // 每次**新建课表**后弹「第一周的第一天」选择器，提示用户为该课表设置开课日期
    var promptTermSetup by remember { mutableStateOf(false) }
    // 导入暂存：解析成功的课程与随附设置；「设置课表名称」确认后落库
    var pendingImport by remember { mutableStateOf<ImportedSchedule?>(null) }

    // 启动加载存档（课程 + 设置）
    LaunchedEffect(Unit) {
        val (loadedBook, needsPersist) = loadCourseBook()

        // 预留插件课表档案 → v2 格式（按课表的插件同步结果）：缺失时创建空档案
        if (!FileStoreUtils.exists(FileName.PLUGIN_SCHEDULE)) {
            PluginScheduleStore.write(PluginScheduleArchive())
        }
        pluginArchive = PluginScheduleStore.read()

        // 全局已安装插件列表（扫描 plugins/*/manifest.json）
        installedPlugins = PluginManager.listInstalled()

        // 迁移历史版本的明文密码类配置 → KVault 加密存储（见 PluginSecretStore）
        val (migratedBook, secretsMigrated) = migratePlaintextPluginSecrets(loadedBook, installedPlugins)
        book = migratedBook
        if (needsPersist || secretsMigrated) {
            // 迁移（旧格式 / 首次启动种子 / 明文密码）后立即落盘
            writeCourseBook(migratedBook)
        }

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

    /**
     * 写回**当前课表的专属设置**（只对该课表生效；首次定制时从内置默认值实体化）。
     *
     * 传入的是**生效设置**（课表专属项 + 全局显示项），落盘前用
     * [AppSettings.forScheduleExport] 剥掉全局显示项——存档里只保留课表专属项，
     * 全局项永远以 settings.json 为准。
     */
    val updateScheduleSettings: (AppSettings) -> Unit = { newSettings ->
        book = book.copy(
            schedules = book.schedules.map { schedule ->
                if (schedule.id == book.activeScheduleId) {
                    schedule.copy(settings = newSettings.forScheduleExport())
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
     * 课表名称 + 自定义课程 + 插件课程快照 + 生效设置里的**课表专属项**
     * （第一周第一天、上课时间、学期周数、一天课程节数、显示替换）。
     *
     * 全局显示项（颜色模式、子标题课表名、辅助线、课表外观参数）**不进信封**——
     * 接收方导入后沿用自己 App 里的外观设置，不会被分享方的偏好覆盖。
     *
     * 分享/导出会**清除插件信息**：插件同步到的课程直接并入 [ScheduleExport.courses]
     * （接收方导入后就是一份普通自定义课表，可自由编辑），信封里没有插件 id、
     * 没有插件配置、也没有单独的插件课程字段——账号密码等一律不出本机。无当前课表时返回空串。
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
                    // 导出 = 用户看到的课表：自定义课程 + 插件课程（去重后一并作为普通课程）
                    courses = (active.courses + pluginCourses).distinct(),
                    settings = scheduleSettings.forScheduleExport()
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
     * 解析自导出信封的随附设置存在则用之，否则用内置默认值；现有课表不受影响。
     * 信封只带**课表专属项**，全局显示项（辅助线、课表外观）始终用本机的
     * （展示时由 [AppSettings.withGlobalDisplay] 兜住）。
     * 随附设置带「第一周的第一天」（termStart）时导入后不再弹日期选择器。
     */
    val importScheduleAsNew: (ImportedSchedule) -> Unit = { imported ->
        val id = "sc${Clock.System.now().toEpochMilliseconds()}"
        book = book.copy(
            schedules = book.schedules + CourseSchedule(
                id = id,
                // 课表名称：用户在名称对话框确认的值（空则回落自动编号）
                name = imported.name.ifBlank { "课表${book.schedules.size + 1}" },
                // 导入的课程一律是普通自定义课程（分享方已清除插件信息，
                // 旧信封里的 pluginCourses 也在解析时并入，见 parseImportedSchedule）
                courses = imported.courses,
                // 随附设置存在则用之（只取课表专属项，旧信封里的全局显示项一并剥掉），
                // 否则用内置默认值
                settings = imported.settings?.forScheduleExport() ?: AppSettings()
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

    /* ------------------------------------------------------------------ */
    /* 插件课程 → 自定义课程（转换）                                          */
    /* ------------------------------------------------------------------ */

    // 待确认的转换请求：单个课程 = 课程详情里的「转换为自定义课程」；
    // 整份列表 = 课表设置里的「全部转换为自定义课程」
    var pendingConvert by remember { mutableStateOf<List<Course>?>(null) }
    // 转换结果提示（HomeScreen 统一弹窗）
    var convertNotice by remember { mutableStateOf<String?>(null) }

    /**
     * 把插件课程**复制**为当前课表的自定义课程（插件只读层保持不变）。
     *
     * 已经存在完全相同课程时跳过，避免用户重复点击「转换」而产生无意义的重复。
     * 因为插件层仍然保留，插件之后同步更新课表时可能出现重复课程
     * （插件课程 + 已转换的自定义课程），这一点在转换前会提示用户。
     *
     * @return (新转换的课程数, 因已存在而跳过的课程数)
     */
    fun convertPluginCourses(source: List<Course>): Pair<Int, Int> {
        val existing = book.activeSchedule()?.courses ?: emptyList()
        val toAdd = source.distinct().filterNot { it in existing }
        if (toAdd.isNotEmpty()) {
            mutateActiveCourses { it + toAdd }
        }
        return toAdd.size to (source.size - toAdd.size)
    }

    /** 请求把一门插件课程转换为自定义课程（先弹确认，提示可能出现的重复课程）。 */
    val requestConvertPluginCourse: (Course) -> Unit = { course ->
        pendingConvert = listOf(course)
    }

    /** 请求把当前课表的**全部插件课程**转换为自定义课程。 */
    val requestConvertAllPluginCourses: () -> Unit = {
        if (pluginCourses.isNotEmpty()) pendingConvert = pluginCourses
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
                // 新建课表取「设置 → 全局课表设置」的默认值快照（默认时间表 / 节数 / 周数 /
                // 显示替换）；没配过默认值时与内置默认值一致。
                // 默认时间表会标记来源（fromDefaults），便于在单课表页与本课表自建的区分开。
                // 之后在该课表的「课表设置」里单独调整，改全局默认值不会再影响它；
                // 辅助线/课表外观是全局显示项，不用快照
                settings = settings.defaults.newScheduleSettings()
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
            // 清掉该课表插件配置的密文（明文随课表一起消失）
            book.schedules.firstOrNull { it.id == id }?.let { removed ->
                val configs = installedPlugins
                    .firstOrNull { it.id == removed.pluginId }
                    ?.manifest
                    ?.configs
                    ?: emptyList()
                deletePluginSecrets(
                    pluginId = removed.pluginId,
                    scheduleId = removed.id,
                    configs = configs,
                    stored = removed.pluginConfig,
                    secretKeys = removed.pluginSecretKeys
                )
            }
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

    /* ---------------------------------------------------------------- */
    /* 教务系统插件：全局安装/卸载 + 按课表选择/配置/同步                    */
    /* ---------------------------------------------------------------- */

    /**
     * 运行一次插件同步并把结果写入该课表的档案条目（失败保留旧课程）。
     *
     * @param notify 同步**结束**时回调 (是否成功, 提示文案)；下拉刷新用它弹 Toast，
     *   自动同步不传（避免每次打开课表都弹提示）。未选插件/未安装等提前返回的分支不回调
     *   （调用方先自行校验并给出提示）
     */
    fun syncSchedulePlugin(scheduleId: String, notify: ((Boolean, String) -> Unit)? = null) {
        if (scheduleId in pluginSyncingIds) return
        val schedule = book.schedules.firstOrNull { it.id == scheduleId } ?: return
        val pluginId = schedule.pluginId ?: return
        val installed = installedPlugins.firstOrNull { it.id == pluginId } ?: return

        pluginSyncingIds = pluginSyncingIds + scheduleId
        val attemptAt = Clock.System.now().toEpochMilliseconds()
        pluginArchive = pluginArchive.withAttempt(scheduleId, pluginId, attemptAt)
        PluginScheduleStore.write(pluginArchive)

        homeScope.launch {
            // 本次同步的结果：成功/失败 + 面向用户的提示文案（用于顶部 Toast）
            var outcome: Pair<Boolean, String>? = null
            try {
                if (!installed.compatible) {
                    val message = "插件要求更高的 App 版本" +
                        "（minHostVersionCode=${installed.manifest.minHostVersionCode}），请升级后重试"
                    pluginArchive = pluginArchive.withSyncFailure(
                        scheduleId, pluginId,
                        message,
                        emptyList(),
                        Clock.System.now().toEpochMilliseconds()
                    )
                    outcome = false to message
                } else {
                    val files = withContext(Dispatchers.Default) {
                        PluginManager.readFiles(pluginId)
                    }
                    if (files.isEmpty()) {
                        val message = "插件文件缺失，请在「设置 → 教务系统插件」中重新安装"
                        pluginArchive = pluginArchive.withSyncFailure(
                            scheduleId, pluginId,
                            message,
                            emptyList(),
                            Clock.System.now().toEpochMilliseconds()
                        )
                        outcome = false to message
                    } else {
                        val config = buildPluginConfigJson(
                            installed.manifest.configs,
                            // 敏感项（密码等）从 KVault 加密存储读回后合并，
                            // 明文存档里没有它们的值（见 PluginSecretStore）
                            loadPluginConfig(
                                pluginId = pluginId,
                                scheduleId = schedule.id,
                                configs = installed.manifest.configs,
                                stored = schedule.pluginConfig,
                                secretKeys = schedule.pluginSecretKeys
                            )
                        )
                        val result = runPlugin(
                            PluginRunRequest(
                                manifest = installed.manifest,
                                files = files,
                                config = config,
                                scheduleId = schedule.id,
                                scheduleName = schedule.name
                            )
                        )
                        val finishedAt = Clock.System.now().toEpochMilliseconds()
                        pluginArchive = if (result.success) {
                            pluginArchive.withSyncSuccess(
                                scheduleId, pluginId,
                                result.courses, result.logs, finishedAt
                            )
                        } else {
                            pluginArchive.withSyncFailure(
                                scheduleId, pluginId,
                                result.error ?: "同步失败",
                                result.logs, finishedAt
                            )
                        }
                        outcome = if (result.success) {
                            true to "插件课表已更新：共 ${result.courses.size} 门课程"
                        } else {
                            false to (result.error ?: "同步失败")
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val message = "同步出错：${e.message ?: e::class.simpleName}"
                pluginArchive = pluginArchive.withSyncFailure(
                    scheduleId, pluginId,
                    message,
                    emptyList(),
                    Clock.System.now().toEpochMilliseconds()
                )
                outcome = false to message
            } finally {
                PluginScheduleStore.write(pluginArchive)
                pluginSyncingIds = pluginSyncingIds - scheduleId
                outcome?.let { (success, message) -> notify?.invoke(success, message) }
            }
        }
    }

    /** 是否正在为**当前课表**同步插件课表（课表页下拉刷新的转圈状态）。 */
    val pluginRefreshing = book.activeScheduleId in pluginSyncingIds

    /**
     * 课表页下拉刷新：同步当前课表的插件课表，结果用顶部 Toast 提示
     * （成功「共 N 门课程」/ 失败给出具体原因；未选插件、未安装也会提示）。
     */
    val refreshPluginCourses: () -> Unit = {
        val schedule = book.activeSchedule()
        val pluginId = schedule?.pluginId
        val installed = pluginId?.let { id -> installedPlugins.firstOrNull { it.id == id } }
        when {
            schedule == null -> Unit
            pluginId.isNullOrBlank() ->
                showToast("当前课表还没有选择教务系统插件（课表设置 → 教务系统插件）", error = true)

            installed == null ->
                showToast("插件未安装或已被卸载，请到「设置 → 教务系统插件」重新安装", error = true)

            schedule.id in pluginSyncingIds ->
                showToast("正在同步中，请稍候…", error = true)

            else -> syncSchedulePlugin(schedule.id) { success, message ->
                showToast(message, error = !success)
            }
        }
    }

    /**
     * 自动同步当前课表的插件课表。
     *
     * 触发时机：**打开软件**（冷启动，以及从后台回到前台）与**切换课表**。
     * 不再要求数据「过期」——每次触发都重新拉一遍；只有以下情况跳过：
     * 当前课表没绑插件、插件没安装、该课表正在同步中，
     * 或距离上次尝试不足 [AUTO_SYNC_DEBOUNCE_MS]（防抖，避免来回切课表时反复请求）。
     *
     * 静默执行（不弹 Toast），进度体现在课表页顶部的下拉刷新转圈上；
     * 结果与日志写进同步档案，可在「课表设置 → 教务系统插件 → 查看同步日志」里看。
     */
    fun autoSyncActiveSchedule() {
        val schedule = book.activeSchedule() ?: return
        val pluginId = schedule.pluginId
        val entry = pluginArchive.schedules[schedule.id]
        val now = Clock.System.now().toEpochMilliseconds()
        val shouldSync = shouldAutoSyncPlugin(
            hasPlugin = !pluginId.isNullOrBlank() && installedPlugins.any { it.id == pluginId },
            syncing = schedule.id in pluginSyncingIds,
            lastAttempt = entry?.lastAttempt ?: 0L,
            now = now
        )
        if (shouldSync) syncSchedulePlugin(schedule.id)
    }

    // 打开软件 / 从后台回到前台 → 自动同步一次
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        autoSyncActiveSchedule()
    }

    // 切换课表（或插件安装状态变化）→ 自动同步一次
    LaunchedEffect(book.activeScheduleId, installedPlugins) {
        autoSyncActiveSchedule()
    }

    /** 从 zip 安装插件（全局）；完成后刷新列表并弹结果。 */
    val installPlugin: (ByteArray) -> Unit = { bytes ->
        if (!pluginInstalling) {
            pluginInstalling = true
            homeScope.launch {
                val result = try {
                    withContext(Dispatchers.Default) { PluginManager.install(bytes) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    com.pgigi.pumpkincampus.plugin.PluginInstallResult(
                        false, "安装失败：${e.message}"
                    )
                }
                installedPlugins = withContext(Dispatchers.Default) {
                    PluginManager.listInstalled()
                }
                pluginInstalling = false
                pluginMessage = result.message
            }
        }
    }

    /** 卸载插件（全局）：删除插件文件与 KV 数据，刷新列表并提示。 */
    val uninstallPlugin: (String) -> Unit = { id ->
        homeScope.launch {
            val name = installedPlugins.firstOrNull { it.id == id }?.name ?: id
            withContext(Dispatchers.Default) { PluginManager.uninstall(id) }
            installedPlugins = withContext(Dispatchers.Default) {
                PluginManager.listInstalled()
            }
            pluginMessage = "已卸载「$name」"
        }
    }

    /** 为**当前课表**选择/取消插件（写入 CourseSchedule.pluginId）。 */
    val selectSchedulePlugin: (String?) -> Unit = { pluginId ->
        book = book.copy(
            schedules = book.schedules.map { schedule ->
                if (schedule.id == book.activeScheduleId) {
                    schedule.copy(pluginId = pluginId)
                } else {
                    schedule
                }
            }
        )
        persistBook()
    }

    /**
     * 写回**当前课表**的插件配置。
     *
     * 敏感项（manifest 里 `type: "password"`，或 key 命中密码/token/secret 等词）
     * **加密保存到 KVault**（Android Keystore / iOS Keychain），
     * `custom-schedule.json` 里只记录这些 key（[CourseSchedule.pluginSecretKeys]）；
     * 其余项照旧明文保存，便于排查与迁移。
     */
    val updateSchedulePluginConfig: (Map<String, String>) -> Unit = { values ->
        val schedule = book.activeSchedule()
        val pluginId = schedule?.pluginId
        val configs = installedPlugins.firstOrNull { it.id == pluginId }?.manifest?.configs ?: emptyList()
        val saved = if (schedule != null && !pluginId.isNullOrEmpty()) {
            savePluginConfig(
                pluginId = pluginId,
                scheduleId = schedule.id,
                configs = configs,
                values = values,
                recorded = schedule.pluginSecretKeys
            )
        } else {
            // 没有绑定插件时不应有配置项，原样保存即可
            SavedPluginConfig(values, emptySet())
        }
        book = book.copy(
            schedules = book.schedules.map { s ->
                if (s.id == book.activeScheduleId) {
                    s.copy(pluginConfig = saved.plain, pluginSecretKeys = saved.secretKeys)
                } else {
                    s
                }
            }
        )
        persistBook()
    }

    /** 下发给课表设置 / 设置页的插件 UI 状态。 */
    val activeSchedule = book.activeSchedule()
    val activePluginConfigs = installedPlugins
        .firstOrNull { it.id == activeSchedule?.pluginId }
        ?.manifest
        ?.configs
        ?: emptyList()
    // 敏感项（密码等）从 KVault 解密读回后合并，界面上仍然显示「已设置 / ••••••」。
    // 用 remember 缓存：加密存储的读取不必每次重组都做一遍（配置变更时 key 变化会重算）
    val activeConfigValues = remember(
        activeSchedule?.pluginId,
        activeSchedule?.id,
        activeSchedule?.pluginConfig,
        activeSchedule?.pluginSecretKeys,
        activePluginConfigs
    ) {
        activeSchedule?.let { schedule ->
            loadPluginConfig(
                pluginId = schedule.pluginId,
                scheduleId = schedule.id,
                configs = activePluginConfigs,
                stored = schedule.pluginConfig,
                secretKeys = schedule.pluginSecretKeys
            )
        } ?: emptyMap()
    }
    val pluginUi = PluginUiState(
        installed = installedPlugins,
        selectedPluginId = book.activeSchedule()?.pluginId,
        configValues = activeConfigValues,
        scheduleId = book.activeScheduleId,
        syncing = book.activeScheduleId in pluginSyncingIds,
        entry = pluginArchive.schedules[book.activeScheduleId],
        installing = pluginInstalling,
        message = pluginMessage,
        pluginCourseCount = pluginCourses.size,
        onInstall = installPlugin,
        onUninstall = uninstallPlugin,
        onDismissMessage = { pluginMessage = null },
        onSelectPlugin = selectSchedulePlugin,
        onConfigChange = updateSchedulePluginConfig,
        onSyncNow = { syncSchedulePlugin(book.activeScheduleId) },
        onConvertAllPluginCourses = requestConvertAllPluginCourses
    )

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
                        courses = customCourses,
                        pluginCourses = pluginCourses,
                        settings = scheduleSettings,
                        onUpdateCourse = updateCourse,
                        onDeleteCourse = deleteCourse,
                        onConvertPluginCourse = requestConvertPluginCourse
                    )
                    1 -> TimetablePage(
                        courses = customCourses,
                        pluginCourses = pluginCourses,
                        settings = scheduleSettings,
                        schedules = book.schedules,
                        activeScheduleId = book.activeScheduleId,
                        // 子标题是否显示课表名称：全局显示开关（不用课表专属设置快照）
                        showScheduleNameInSubtitle = settings.showScheduleNameInSubtitle,
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
                        onDeleteCourse = deleteCourse,
                        onConvertPluginCourse = requestConvertPluginCourse,
                        pluginUi = pluginUi,
                        pluginRefreshing = pluginRefreshing,
                        onRefreshPluginCourses = refreshPluginCourses
                    )
                    else -> SettingsPage(
                        settings = settings,
                        courses = displayCourses,
                        onSettingsChange = { settings = it },
                        onImportReady = onImportReady,
                        pluginUi = pluginUi
                    )
                }
            }

            // 顶部 Toast 层（dhyantoast）：覆盖在页面之上、屏幕最上边，
            // 用于下拉刷新同步插件课表等结果提示（自动消失，可上滑关闭）
            ToastHost(
                hostState = toastHostState,
                alignment = ToastAlignment.Top,
                modifier = Modifier.fillMaxSize()
            )
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

    // 插件安装/卸载结果提示（可任意关闭）
    pluginMessage?.let { message ->
        ImportErrorDialog(
            message = message,
            onDismiss = { pluginMessage = null },
            title = "插件"
        )
    }

    // 插件课程 → 自定义课程：转换前提示（插件仍然保留，插件更新后可能出现重复课程）
    pendingConvert?.let { courses ->
        ConvertPluginCoursesDialog(
            courses = courses,
            onConfirm = {
                pendingConvert = null
                val (converted, skipped) = convertPluginCourses(courses)
                convertNotice = when {
                    converted == 0 ->
                        "这些课程已经是自定义课程，无需重复转换。"

                    skipped > 0 ->
                        "已转换 $converted 门课程为自定义课程（$skipped 门已存在，跳过）。" +
                            "插件课程仍然保留：插件下次同步更新课表后可能出现重复课程，" +
                            "届时可删除自定义课程或重新转换。"

                    else ->
                        "已转换 $converted 门课程为自定义课程。" +
                            "插件课程仍然保留：插件下次同步更新课表后可能出现重复课程，" +
                            "届时可删除自定义课程或重新转换。"
                }
            },
            onDismissRequest = { pendingConvert = null }
        )
    }

    // 转换结果提示（可任意关闭）
    convertNotice?.let { message ->
        ImportErrorDialog(
            message = message,
            onDismiss = { convertNotice = null },
            title = "转换为自定义课程"
        )
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
 * - 新格式（含 `schedules`）直接解析，并把旧版本分享导入留下的插件只读层并入自定义课程；
 * - 旧格式（[ScheduleCache]：`{updateTime, courses}`）迁移为单个默认课表；
 * - 无档案 / 解析失败时用 `schedule.json` 种子（没有则空课表）迁移。
 *
 * 返回归一化后的档案与是否需要立即写回（发生迁移时为 true）。
 */
private fun loadCourseBook(): Pair<CourseBook, Boolean> {
    val raw = FileStoreUtils.readString(FileName.CUSTOM_SCHEDULE)
    if (raw != null) {
        // 新格式：含 schedules 字段
        try {
            val element = ShapeProbeJson.parseToJsonElement(raw)
            if (element is JsonObject && "schedules" in element) {
                val decoded = ShapeProbeJson
                    .decodeFromJsonElement(CourseBook.serializer(), element)
                    .normalized()
                // 旧版本导入的插件只读层 → 并入自定义课程（分享不再携带插件信息）
                return decoded.mergeLegacyImportedPluginCourses()
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

/** 读取课表存档（旧 [ScheduleCache] 格式；当前仅用于 schedule.json 首启种子）。 */
private fun readScheduleCache(fileName: String): ScheduleCache? =
    FileStoreUtils.readString(fileName)?.let { raw ->
        try {
            JsonUtil.parseJson(raw, ScheduleCache.serializer())
        } catch (_: Exception) {
            null
        }
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

/**
 * 自动同步防抖：距上次**尝试**不足该时长就不再自动跑一次
 * （打开软件、切回同一张课表时不至于反复请求教务系统；
 * 手动「立即同步」与课表页下拉刷新不受此限制）。
 */
private const val AUTO_SYNC_DEBOUNCE_MS = 30_000L

/**
 * 「打开软件 / 切换课表」时是否要自动同步插件课表（纯函数，便于测试）。
 *
 * 规则：当前课表绑定了**已安装**的插件、该课表当前没有在同步，
 * 且距上次尝试已超过 [debounceMs]（从未尝试过则立即同步）。
 *
 * 注意这里**不判断数据是否新鲜**：每次打开软件、每次切到某张课表都会重新拉一遍，
 * 用户拿到的就是教务系统的最新课表。
 *
 * @param hasPlugin 当前课表是否绑定了已安装的插件
 * @param syncing 该课表是否正在同步中
 * @param lastAttempt 上次同步尝试的时间戳（毫秒，0 = 从未尝试）
 * @param now 当前时间戳（毫秒）
 * @param debounceMs 防抖时长
 */
internal fun shouldAutoSyncPlugin(
    hasPlugin: Boolean,
    syncing: Boolean,
    lastAttempt: Long,
    now: Long,
    debounceMs: Long = AUTO_SYNC_DEBOUNCE_MS
): Boolean {
    if (!hasPlugin) return false
    if (syncing) return false
    if (lastAttempt <= 0L) return true
    return now - lastAttempt >= debounceMs
}
