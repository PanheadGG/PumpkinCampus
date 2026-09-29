package com.pgigi.pumpkincampus.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.moriafly.salt.ui.Icon
import com.moriafly.salt.ui.ItemButton
import com.moriafly.salt.ui.ItemDivider
import com.moriafly.salt.ui.ItemEdit
import com.moriafly.salt.ui.ItemOuterTitle
import com.moriafly.salt.ui.ItemSlider
import com.moriafly.salt.ui.ItemSwitcher
import com.moriafly.salt.ui.ItemTip
import com.moriafly.salt.ui.RoundedColumn
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.Text
import com.moriafly.salt.ui.UnstableSaltUiApi
import com.moriafly.salt.ui.icons.Back
import com.moriafly.salt.ui.icons.SaltIcons
import com.moriafly.salt.ui.pager.rememberPagerState
import com.moriafly.salt.ui.popup.PopupMenu
import com.moriafly.salt.ui.popup.PopupMenuItem
import com.moriafly.salt.ui.screen.BasicScreen
import com.moriafly.salt.ui.screen.TitleBarButton
import com.pgigi.pumpkincampus.models.AppSettings
import com.pgigi.pumpkincampus.models.BackgroundConfig
import com.pgigi.pumpkincampus.models.Course
import com.pgigi.pumpkincampus.pages.SceneBackgroundContent
import com.pgigi.pumpkincampus.pages.TimetableRootKey
import com.pgigi.pumpkincampus.pages.resolveSceneBackground
import com.pgigi.pumpkincampus.pages.isValidBackgroundColor
import com.pgigi.pumpkincampus.pages.parseBackgroundColor
import com.pgigi.pumpkincampus.schedule.SchedulePager
import com.pgigi.pumpkincampus.schedule.buildCourseListByWeek
import com.pgigi.pumpkincampus.schedule.currentLocalDate
import com.pgigi.pumpkincampus.schedule.dayIndexOf
import com.pgigi.pumpkincampus.schedule.weekCalculatorOf
import com.pgigi.pumpkincampus.utils.deleteBackgroundImage
import com.pgigi.pumpkincampus.utils.loadBackgroundImage
import com.pgigi.pumpkincampus.utils.rememberImageFilePicker
import com.pgigi.pumpkincampus.utils.saveBackgroundImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * 课表外观设置子页（设置 Tab → 外观；**全局设置，所有课表共用**）：
 * - **上半屏**：实时预览（当前周课程表，随下方参数即时刷新；点击课程不弹详情）
 * - **下半屏**：可滚动参数
 *   - 显示网格辅助线开关
 *   - 单元格高度 50–100dp（步长 10）
 *   - 是否显示授课老师 / 上课地点 / 地点「@」前缀
 *
 * 所有参数只影响**展示**；课程详情与编辑表单始终显示完整信息。
 * 「显示替换」是课表专属设置，已移到课表页「⋯ → 课表设置」。
 *
 * 下半屏还带「**自定义背景**」：作用范围分全局 / 单课表 / 按页面三层，
 * 见 [BackgroundSection]。单课表背景读写当前课表的专属设置。
 *
 * @param scheduleName 当前课表名（「单课表」作用域要显示给用户看）
 * @param scheduleBackground 当前课表自己的背景配置（null = 没定制，回落全局）
 * @param onScheduleBackgroundChange 写回当前课表的专属设置（只对该课表生效）
 */
@OptIn(UnstableSaltUiApi::class)
@Composable
internal fun AppearanceSettingsPage(
    settings: AppSettings,
    courses: List<Course>,
    onSettingsChange: (AppSettings) -> Unit,
    scheduleName: String,
    scheduleBackground: BackgroundConfig?,
    onScheduleBackgroundChange: (BackgroundConfig?) -> Unit,
    onBack: () -> Unit
) {
    val weekCalculator = remember(settings.termStart) { weekCalculatorOf(settings.termStart) }
    val weekCount = settings.semesterWeekCount
    // 预览课程：真实课程优先；没有课程时用仅供预览的样例（不写入任何存档）
    val previewCourses = remember(courses) {
        courses.ifEmpty { previewSampleCourses(weekCount) }
    }
    val courseListByWeek = remember(previewCourses, weekCalculator, weekCount) {
        buildCourseListByWeek(previewCourses, weekCount, weekCalculator)
    }
    val currentWeekPage = remember(weekCalculator, weekCount) {
        weekCalculator.getWeekNumber(currentLocalDate())
            .coerceIn(1, weekCount) - 1
    }
    val pagerState = rememberPagerState(initialPage = currentWeekPage) {
        courseListByWeek.size
    }

    fun update(block: (AppSettings) -> AppSettings) = onSettingsChange(block(settings))

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
        title = "课表外观"
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = contentPadding.calculateTopPadding())
                .padding(bottom = contentPadding.calculateBottomPadding())
        ) {
            // 上半屏：实时预览。
            // 背景也套「课表页」真正生效的那一份——按页面/单课表背景各不相同时，
            // 预览里看到的才和切到课表 tab 后看到的一致（所见即所得）。
            val previewBackdrop = resolveSceneBackground(
                contentKey = TimetableRootKey,
                global = settings.background,
                schedule = scheduleBackground,
                pages = settings.pageBackgrounds
            )
            val previewBackdropImage = remember(previewBackdrop?.image) {
                previewBackdrop
                    ?.takeIf { it.type == BackgroundConfig.TYPE_IMAGE }
                    ?.image
                    ?.let { loadBackgroundImage(it) }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                SceneBackgroundContent(
                    config = previewBackdrop,
                    image = previewBackdropImage,
                    chromeOpacity = settings.chromeOpacity
                ) {
                    SchedulePager(
                        courseListByWeek = courseListByWeek,
                        lessonTimes = settings.activeLessonTimes(),
                        lessonCount = settings.lessonCount,
                        weekCalculator = weekCalculator,
                        pagerState = pagerState,
                        cellHeight = settings.cellHeightDp.dp,
                        showGridLines = settings.showGridLines,
                        settings = settings,
                        // 预览：点击不弹详情/冲突选择
                        onCourseClick = {},
                        onConflictClick = {},
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // 下半屏：参数
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(top = 4.dp, bottom = 24.dp)
            ) {
//                ItemOuterTitle(text = "显示")
                RoundedColumn {
                    ItemSwitcher(
                        state = settings.showGridLines,
                        onChange = { v -> update { s -> s.copy(showGridLines = v) } },
                        text = "显示网格辅助线"
                    )
                    ItemDivider()
                    // 单元格高度：50–100dp，步长 10（steps = 中间刻度数 = 4）
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = SaltTheme.dimens.padding,
                                vertical = 4.dp
                            )
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "单元格高度",
                                fontSize = SaltTheme.textStyles.main.fontSize,
                                color = SaltTheme.colors.text,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "${settings.cellHeightDp} dp",
                                fontSize = SaltTheme.textStyles.sub.fontSize,
                                color = SaltTheme.colors.subText
                            )
                        }
                        Slider(
                            value = settings.cellHeightDp.toFloat(),
                            onValueChange = { value ->
                                update {
                                    it.copy(
                                        cellHeightDp = value.roundToInt()
                                            .coerceIn(50, 100)
                                    )
                                }
                            },
                            valueRange = 50f..100f,
                            steps = 4,
                            colors = SliderDefaults.colors(
                                thumbColor = SaltTheme.colors.highlight,
                                activeTrackColor = SaltTheme.colors.highlight,
                                inactiveTrackColor = SaltTheme.colors.stroke
                            )
                        )
                    }
                    ItemDivider()
                    ItemSwitcher(
                        state = settings.showTeacher,
                        onChange = { v -> update { s -> s.copy(showTeacher = v) } },
                        text = "显示授课老师"
                    )
                    ItemDivider()
                    ItemSwitcher(
                        state = settings.showClassroom,
                        onChange = { v -> update { s -> s.copy(showClassroom = v) } },
                        text = "显示上课地点"
                    )
                    ItemDivider()
                    ItemSwitcher(
                        state = settings.classroomAtPrefix,
                        onChange = { v -> update { s -> s.copy(classroomAtPrefix = v) } },
                        enabled = settings.showClassroom,
                        text = "地点前加「@」"
                    )
                }

                ItemOuterTitle(text = "自定义背景")
                BackgroundSection(
                    globalBackground = settings.background,
                    pageBackgrounds = settings.pageBackgrounds,
                    scheduleName = scheduleName,
                    scheduleBackground = scheduleBackground,
                    chromeOpacity = settings.chromeOpacity,
                    onGlobalChange = { cfg -> update { it.copy(background = cfg) } },
                    onPageChange = { group, cfg ->
                        update { s ->
                            s.copy(
                                pageBackgrounds = if (cfg == null) {
                                    s.pageBackgrounds - group
                                } else {
                                    s.pageBackgrounds + (group to cfg)
                                }
                            )
                        }
                    },
                    onScheduleChange = onScheduleBackgroundChange
                )
                RoundedColumn {
                    // 0.25..1 当量程（最小步长 1%）：steps = 刻度间隔数 - 1 = 75 - 1，
                    // 每格正好 1%，读数永远是整数百分比
                    ItemSlider(
                        value = settings.chromeOpacity.coerceIn(0.25f, 1f) * 100f,
                        onValueChange = { v ->
                            update { it.copy(chromeOpacity = (v / 100f).coerceIn(0.25f, 1f)) }
                        },
                        text = "卡片 / 选项 / 底栏不透明度",
                        sub = "${(settings.chromeOpacity.coerceIn(0.25f, 1f) * 100).roundToInt()}%",
                        valueRange = 25f..100f,
                        steps = 74
                    )
                }
                ItemTip(
                    text = "三层覆盖，优先级从高到低：按页面 > 单课表（只影响课表相关页面，" +
                        "切换课表自动换） > 全局；都没有时跟随系统。「按页面」勾选的是整个页面及其子页。" +
                        "不透明度对所有范围的卡片、输入框与底栏生效，跟随系统时不生效；" +
                        "深色模式压在亮图上时别调太低，否则正文会看不清。"
                )

                /*ItemTip(
                    text = "课表外观是**全局设置**，所有课表共用。" +
                        "「显示替换」是课表专属的，在课表页「⋯ → 课表设置」里调整。"
                )*/
            }
        }
    }
}

/**
 * 仅供「课表外观」预览的样例课程：不进入任何存档，
 * 也不参与日程页/课表页数据（正式数据为空时预览才出现）。
 *
 * @param weekCount 学期周数（周次覆盖全部教学周，保证任意当前周都有内容）
 */
private fun previewSampleCourses(weekCount: Int): List<Course> {
    val today = currentLocalDate()
    val allWeeks = (0 until weekCount).toList()
    val todayIndex = dayIndexOf(today)
    return listOf(
        Course(
            name = "示例课程甲",
            classroom = "雨母校区 A101",
            teacher = "张老师",
            weekIndices = allWeeks,
            dayIndex = todayIndex,
            lessonStartIndex = 1,
            lessonCount = 2
        ),
        Course(
            name = "示例课程乙",
            classroom = "红湘校区 B203",
            teacher = "李老师",
            weekIndices = allWeeks,
            dayIndex = todayIndex,
            lessonStartIndex = 5,
            lessonCount = 2
        ),
        Course(
            name = "示例课程丙",
            classroom = "雨母校区 C105",
            teacher = "王老师",
            weekIndices = allWeeks,
            dayIndex = (todayIndex + 1) % 7,
            lessonStartIndex = 3,
            lessonCount = 2
        )
    )
}


/* ------------------------------------------------------------------ */
/* 自定义背景（作用范围：全局 / 单课表 / 按页面）                        */
/* ------------------------------------------------------------------ */

/** 三个作用域的 id（存进 rememberSaveable，页面来回切不丢）。 */
private const val BG_SCOPE_GLOBAL = "global"
private const val BG_SCOPE_SCHEDULE = "schedule"
private const val BG_SCOPE_PAGE = "page"

/** 色板：点一下就把当前作用域换成这个纯色（前五个是无彩色，后五个是强调色）。 */
private val BackgroundPalette = listOf(
    "#FFFFFF", "#F2F2F7", "#D1D1D6", "#1C1C1E", "#3A3A3C",
    "#0A84FF", "#34C759", "#FF9F0A", "#FF375F", "#BF5AF2"
)

private val ScopeLabels = mapOf(
    BG_SCOPE_GLOBAL to "全局",
    BG_SCOPE_SCHEDULE to "单课表",
    BG_SCOPE_PAGE to "按页面"
)

private val TypeLabels = mapOf(
    BackgroundConfig.TYPE_SYSTEM to "跟随系统",
    BackgroundConfig.TYPE_COLOR to "纯色",
    BackgroundConfig.TYPE_IMAGE to "图片"
)

private val GroupLabels = mapOf(
    BackgroundConfig.GROUP_AGENDA to "日程页",
    BackgroundConfig.GROUP_TIMETABLE to "课表页",
    BackgroundConfig.GROUP_SETTINGS to "设置页"
)

/**
 * 「自定义背景」配置块：实时预览条 + 作用范围 + 该范围内的背景参数。
 *
 * 三个作用域落盘在三个不同的地方，这里只负责「算出新配置往哪写」：
 *
 * - **全局** → [AppSettings.background]（`settings.json`）；
 * - **单课表** → 当前课表专属设置里的 `background`（`custom-schedule.json`），
 *   只对课表相关页面生效，切课表自动换；
 * - **按页面** → [AppSettings.pageBackgrounds]（`settings.json`，key = 页面分组，
 *   一个分组 = 一个 tab 及其全部子页），优先级最高。
 *
 * @param globalBackground 全局背景（null = 跟随系统）
 * @param pageBackgrounds 按页面覆盖表
 * @param scheduleName 当前课表名（作用范围里显示）
 * @param scheduleBackground 当前课表自己的背景（null = 没定制，回落全局）
 */
@OptIn(UnstableSaltUiApi::class)
@Composable
private fun BackgroundSection(
    globalBackground: BackgroundConfig?,
    pageBackgrounds: Map<String, BackgroundConfig>,
    scheduleName: String,
    scheduleBackground: BackgroundConfig?,
    chromeOpacity: Float,
    onGlobalChange: (BackgroundConfig?) -> Unit,
    onPageChange: (String, BackgroundConfig?) -> Unit,
    onScheduleChange: (BackgroundConfig?) -> Unit
) {
    var scope by rememberSaveable { mutableStateOf(BG_SCOPE_GLOBAL) }
    var editGroup by rememberSaveable { mutableStateOf(BackgroundConfig.GROUP_TIMETABLE) }

    /** 「按页面」还没启用时，这一页**继承到**的效果（开关初值 + 预览用）。 */
    fun inheritedConfig(group: String): BackgroundConfig? {
        pageBackgrounds[group]?.let { return it }
        if (group == BackgroundConfig.GROUP_TIMETABLE) scheduleBackground?.let { return it }
        return globalBackground
    }

    val pageEnabled = scope != BG_SCOPE_PAGE || pageBackgrounds.containsKey(editGroup)
    /** 当前作用域**自己**存的配置（null = 没定制）。清除/换图时只对它释放文件引用。 */
    val ownConfig = when (scope) {
        BG_SCOPE_SCHEDULE -> scheduleBackground
        BG_SCOPE_PAGE -> pageBackgrounds[editGroup]
        else -> globalBackground
    }
    /**
     * 编辑器显示的配置：「单课表」没定制时先显示**全局**那份——
     * 用户看到的就是当前实际生效的效果，改一下才真正落到这套课表上。
     */
    val currentConfig = if (scope == BG_SCOPE_SCHEDULE && ownConfig == null) {
        globalBackground
    } else {
        ownConfig
    }
    // 预览 = 这一屏实际生效的背景；「按页面」未启用时预览继承到的效果（开关前后观感一致）
    val previewConfig = if (scope == BG_SCOPE_PAGE && !pageEnabled) {
        inheritedConfig(editGroup)
    } else {
        currentConfig
    }

    fun applyConfig(config: BackgroundConfig?) {
        when (scope) {
            BG_SCOPE_SCHEDULE -> onScheduleChange(config)
            BG_SCOPE_PAGE -> onPageChange(editGroup, config)
            else -> onGlobalChange(config)
        }
    }

    fun updateConfig(block: (BackgroundConfig) -> BackgroundConfig) {
        applyConfig(block(currentConfig ?: BackgroundConfig()))
    }

    // 所有作用域引用到的图片文件：判断「替换 / 清除时旧文件还能不能删」
    val imageRefs = remember(globalBackground, scheduleBackground, pageBackgrounds) {
        listOfNotNull(globalBackground, scheduleBackground) + pageBackgrounds.values
    }.mapNotNull { it.image }

    /** 旧图已经没有别的作用域引用时删掉文件，省空间；共用时留着。 */
    fun releaseImage(fileName: String?) {
        if (fileName.isNullOrEmpty()) return
        if (imageRefs.count { it == fileName } <= 1) deleteBackgroundImage(fileName)
    }

    val uiScope = rememberCoroutineScope()
    var imageError by remember { mutableStateOf(false) }
    // 选图 → 后台降采样压缩成 JPEG → 写进应用私有目录 → 更新配置；用户取消则什么都不做
    val pickImage = rememberImageFilePicker { raw ->
        if (raw != null) {
            uiScope.launch {
                val saved = withContext(Dispatchers.Default) { saveBackgroundImage(raw) }
                if (saved == null) {
                    imageError = true
                } else {
                    imageError = false
                    releaseImage(ownConfig?.image)
                    applyConfig(
                        (currentConfig ?: BackgroundConfig())
                            .copy(type = BackgroundConfig.TYPE_IMAGE, image = saved)
                    )
                }
            }
        }
    }

    val previewImage = remember(previewConfig?.image) {
        previewConfig?.image?.let { loadBackgroundImage(it) }
    }

    val scopeValue = when (scope) {
        BG_SCOPE_SCHEDULE -> if (scheduleName.isEmpty()) "单课表" else "单课表 · $scheduleName"
        else -> ScopeLabels[scope] ?: "全局"
    }

    // —— 实时预览条：把当前这份背景（含蒙层 / 模糊）原样铺一遍 ——
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SaltTheme.dimens.padding, vertical = 6.dp)
            .height(84.dp)
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, SaltTheme.colors.stroke, RoundedCornerShape(14.dp))
    ) {
        SceneBackgroundContent(
            config = previewConfig,
            image = previewImage,
            chromeOpacity = chromeOpacity
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp),
                contentAlignment = Alignment.BottomStart
            ) {
                // 半透明深色小标签：白底黑底上都读得出来
                Box(
                    modifier = Modifier
                        .background(
                            color = Color.Black.copy(alpha = 0.45f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = when (previewConfig?.type) {
                            BackgroundConfig.TYPE_IMAGE -> "图片背景"
                            BackgroundConfig.TYPE_COLOR ->
                                "纯色 ${previewConfig.color.uppercase()}"
                            else -> "跟随系统"
                        },
                        color = Color.White,
                        fontSize = SaltTheme.textStyles.sub.fontSize
                    )
                }
            }
        }
    }

    RoundedColumn {
        MenuRow(
            title = "作用范围",
            value = scopeValue,
            options = ScopeLabels.map { it.key to it.value },
            selectedId = scope,
            onSelect = { scope = it }
        )

        if (scope == BG_SCOPE_PAGE) {
            ItemDivider()
            MenuRow(
                title = "作用页面",
                value = GroupLabels[editGroup] ?: "",
                options = GroupLabels.map { it.key to it.value },
                selectedId = editGroup,
                onSelect = { editGroup = it }
            )
            ItemDivider()
            // 打开时把「当前继承到的效果」复制成本页自己的配置：先观感不变，再自由改
            ItemSwitcher(
                state = pageEnabled,
                onChange = { want ->
                    if (want) {
                        onPageChange(editGroup, inheritedConfig(editGroup) ?: BackgroundConfig())
                    } else {
                        releaseImage(pageBackgrounds[editGroup]?.image)
                        onPageChange(editGroup, null)
                    }
                },
                text = "为「${GroupLabels[editGroup]}」启用自定义背景"
            )
        }

        if (pageEnabled) {
            val type = currentConfig?.type ?: BackgroundConfig.TYPE_SYSTEM
            ItemDivider()
            MenuRow(
                title = "背景类型",
                value = TypeLabels[type] ?: TypeLabels.getValue(BackgroundConfig.TYPE_SYSTEM),
                options = TypeLabels.map { it.key to it.value },
                selectedId = type,
                onSelect = { picked -> updateConfig { it.copy(type = picked) } }
            )

            when (type) {
                BackgroundConfig.TYPE_COLOR -> {
                    ItemDivider()
                    ColorPalette(
                        selected = currentConfig?.color ?: BackgroundConfig.DEFAULT_COLOR,
                        onSelect = { hex ->
                            updateConfig {
                                it.copy(type = BackgroundConfig.TYPE_COLOR, color = hex)
                            }
                        }
                    )
                    ItemDivider()
                    HexColorEdit(
                        color = currentConfig?.color ?: BackgroundConfig.DEFAULT_COLOR,
                        onCommit = { hex ->
                            updateConfig {
                                it.copy(type = BackgroundConfig.TYPE_COLOR, color = hex)
                            }
                        }
                    )
                }

                BackgroundConfig.TYPE_IMAGE -> {
                    ItemDivider()
                    ItemButton(
                        onClick = {
                            imageError = false
                            pickImage()
                        },
                        text = "从相册 / 文件选择图片"
                    )
                    if (imageError) {
                        ItemDivider()
                        ItemTip(
                            text = "这张图片读不出来，换一张试试（导入时会自动降采样并转成 JPEG）"
                        )
                    }
                    ItemDivider()
                    ItemSwitcher(
                        state = currentConfig?.blur == true,
                        onChange = { v -> updateConfig { it.copy(blur = v) } },
                        text = "高斯模糊"
                    )
                    ItemDivider()
                    // 用 0..85 当量程（最小步长 1%）：steps = 刻度间隔数 - 1 = 85 - 1，
                    // 每格正好 1%，读数永远是整数百分比
                    ItemSlider(
                        value = (currentConfig ?: BackgroundConfig()).clampedDim() * 100f,
                        onValueChange = { v -> updateConfig { it.copy(dim = v / 100f) } },
                        text = "暗色蒙层",
                        sub = "${((currentConfig ?: BackgroundConfig()).clampedDim() * 100).roundToInt()}%",
                        valueRange = 0f..85f,
                        steps = 84
                    )
                }

                else -> Unit // 跟随系统：没有可调项
            }

            ItemDivider()
            ItemButton(
                onClick = {
                    releaseImage(ownConfig?.image)
                    applyConfig(null)
                },
                text = "恢复默认（清除该范围的背景）",
                primary = false
            )
        }
    }
}

/**
 * 一行「点按弹选择菜单」的条目：复用设置页「颜色模式」那套锚点做法
 * （全宽行直接当锚点会把菜单顶到屏幕最左，所以在行尾放一个 1dp 的透明锚）。
 */
@OptIn(UnstableSaltUiApi::class)
@Composable
private fun MenuRow(
    title: String,
    value: String,
    options: List<Pair<String, String>>,
    selectedId: String,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        NavRow(title = title, value = value, onClick = { expanded = true })
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(1.dp)
        ) {
            PopupMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                offset = DpOffset(16.dp, 8.dp)
            ) {
                options.forEach { (id, label) ->
                    PopupMenuItem(
                        onClick = {
                            expanded = false
                            onSelect(id)
                        },
                        selected = id == selectedId,
                        text = label
                    )
                }
            }
        }
    }
}

/** 纯色色板：两行圆点，点一下直接换色，选中的带高亮描边。 */
@Composable
private fun ColorPalette(
    selected: String,
    onSelect: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SaltTheme.dimens.padding, vertical = 8.dp)
    ) {
        BackgroundPalette.chunked(5).forEach { rowColors ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                rowColors.forEach { hex ->
                    val isSelected = selected.equals(hex, ignoreCase = true)
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(parseBackgroundColor(hex))
                            .border(
                                width = if (isSelected) 3.dp else 1.dp,
                                color = if (isSelected) {
                                    SaltTheme.colors.highlight
                                } else {
                                    SaltTheme.colors.stroke
                                },
                                shape = CircleShape
                            )
                            .clickable { onSelect(hex) }
                    )
                }
            }
        }
    }
}

/**
 * 自定义色值输入（`#RGB` / `#RRGGBB` / `#AARRGGBB`）。
 *
 * 打字过程中会有半截的非法值（比如只输了 `#F`），所以本地记一份输入内容，
 * **只有合法才落库**——否则受控输入会把没输完的字符顶掉，根本打不出三位以上的色值。
 */
@OptIn(UnstableSaltUiApi::class)
@Composable
private fun HexColorEdit(
    color: String,
    onCommit: (String) -> Unit
) {
    var input by rememberSaveable(color) { mutableStateOf(color) }
    ItemEdit(
        text = input,
        onChange = { value ->
            input = value
            if (isValidBackgroundColor(value)) onCommit(value)
        },
        hint = "自定义色值 #RRGGBB"
    )
}
