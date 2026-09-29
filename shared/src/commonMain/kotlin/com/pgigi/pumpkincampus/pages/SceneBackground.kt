package com.pgigi.pumpkincampus.pages

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.moriafly.salt.ui.SaltColors
import com.moriafly.salt.ui.SaltDynamicColors
import com.moriafly.salt.ui.SaltTheme
import com.pgigi.pumpkincampus.models.BackgroundConfig
import com.pgigi.pumpkincampus.models.BackgroundConfig.Companion.GROUP_AGENDA
import com.pgigi.pumpkincampus.models.BackgroundConfig.Companion.GROUP_SETTINGS
import com.pgigi.pumpkincampus.models.BackgroundConfig.Companion.GROUP_TIMETABLE
import com.pgigi.pumpkincampus.models.BackgroundConfig.Companion.TYPE_COLOR
import com.pgigi.pumpkincampus.models.BackgroundConfig.Companion.TYPE_IMAGE
import com.pgigi.pumpkincampus.models.BackgroundConfig.Companion.TYPE_SYSTEM
import com.pgigi.pumpkincampus.utils.loadBackgroundImage

/** 图片背景的高斯模糊半径。 */
private val ImageBlurRadius = 24.dp

/**
 * 当前场景是否铺了**自定义背景**（纯色或图片，而非「跟随系统」）。
 *
 * 底部导航栏这类 chrome 组件据此换半透明底，让背景透出来；
 * 跟随系统的场景一律 false，外观与加自定义背景之前完全一致。
 */
internal val LocalSceneBackdropCustom: ProvidableCompositionLocal<Boolean> =
    staticCompositionLocalOf { false }

/**
 * 自定义背景下 `subBackground`（卡片 / 输入框 / 底栏 / 表单分组的底色）换成的半透明纱：
 * 浅色主题是白纱、深色主题是深灰纱，**浓度由 [chromeOpacity] 控制**
 * （「设置 → 课表外观」里的滑块，0.25..1）。
 *
 * 深色主题压在亮图上时浓度别调太低，否则正文压不住背景。
 */
private fun sceneDynamicColors(chromeOpacity: Float): SaltDynamicColors {
    val alpha = chromeOpacity.coerceIn(0.25f, 1f)
    return SaltDynamicColors(
        light = SaltColors.defaultLight().copy(
            subBackground = Color.White.copy(alpha = alpha)
        ),
        dark = SaltColors.defaultDark().copy(
            subBackground = Color(0xFF1C1C1E).copy(alpha = alpha)
        )
    )
}

/**
 * 卡片 / 输入框 / 圆形浮标这类**内容底色**该取什么：
 *
 * - 铺了自定义背景 → 场景纱色（半透明，背景透出来）；
 * - 跟随系统 → 原来的 [SaltTheme.colors.popup] 不透明色，观感与加背景之前完全一致。
 *
 * 只给「卡片类」用；`ModalBottomSheet` / `Dialog` 这类模态容器保持不透明的 `popup`，
 * 模态层压在别人头上，透了会读不清。
 */
@Composable
internal fun sceneCardColor(): Color =
    if (LocalSceneBackdropCustom.current) SaltTheme.colors.subBackground
    else SaltTheme.colors.popup

/**
 * 导航条目 → 自定义背景的**页面分组**（一个分组 = 一个 tab 及其全部子页）。
 *
 * 入参是 [androidx.navigation3.runtime.NavEntry.contentKey]——`NavEntry.key` 是 private，
 * 装饰器里只能拿到它，而 contentKey 就是 key 的 `toString()`：
 * 对象键是**类全名**（`…pages.TimetableRootKey`），带参键多一段参数
 * （`…pages.TimetableLessonEditKey(timetableId=abc)`）。
 * 所以先按 `(` 截断、再取末段类名去比对，12 个对象键 + 3 个带参键全覆盖。
 *
 * 返回 null 表示该条目不参与「按页面」覆盖，只吃全局兜底；分组 id 见 [BackgroundConfig]。
 *
 * **注意**：这里按**类名**匹配，路由类改名时要同步（漏了不会崩，只是退回全局背景）。
 */
internal fun backgroundGroupOf(contentKey: Any): String? {
    val routeName = contentKey.toString().substringBefore('(').substringAfterLast('.')
    return when (routeName) {
        // 日程 tab（只有根页）
        "AgendaRootKey" -> GROUP_AGENDA

        // 课表 tab + 它的四个子页
        "TimetableRootKey",
        "ScheduleSettingsKey",
        "SchedulePluginKey",
        "TimetableLessonTimesKey",
        "TimetableLessonEditKey" -> GROUP_TIMETABLE

        // 设置 tab + 它的全部子页
        "SettingsRootKey",
        "AppearanceKey",
        "GlobalScheduleSettingsKey",
        "DefaultLessonTimesKey",
        "DefaultTimetableEditKey",
        "PluginsKey",
        "AboutKey",
        "OssLicenseListKey",
        "OssLicenseDetailKey" -> GROUP_SETTINGS

        else -> null
    }
}

/**
 * 解析某个场景最终用哪份背景配置——三层覆盖，**从高到低**：
 *
 * 1. [pages]（按页面覆盖，只认该场景所属分组）；
 * 2. [schedule]（单课表背景，**仅课表相关页面**；切课表即换）；
 * 3. [global]（全局兜底）；
 *
 * 全部没命中返回 null → 调用方回落跟随系统主题背景。
 */
internal fun resolveSceneBackground(
    contentKey: Any,
    global: BackgroundConfig?,
    schedule: BackgroundConfig?,
    pages: Map<String, BackgroundConfig>
): BackgroundConfig? {
    val group = backgroundGroupOf(contentKey)
    if (group != null) {
        pages[group]?.let { return it }
        // 单课表背景只作用在课表相关页面上
        if (group == GROUP_TIMETABLE) schedule?.let { return it }
    }
    return global
}

/**
 * 一次解析好的自定义背景：三份配置 + **预解码**的图片。
 *
 * 图片只在导航宿主那一层解码一次（最多 3 张：全局 / 单课表 / 页面覆盖），
 * 14 个场景共用同一份位图——否则每个 entry 各解一次，切页面时会白闪 + 占内存。
 *
 * @param images 文件名 → 已解码位图（文件缺失的不会出现在表里，走回落逻辑）
 * @param chromeOpacity 卡片/底栏等 chrome 底色的不透明度（全局设置，随场景一起下发）
 */
internal class SceneBackgroundResolver(
    private val global: BackgroundConfig?,
    private val schedule: BackgroundConfig?,
    private val pages: Map<String, BackgroundConfig>,
    private val images: Map<String, ImageBitmap>,
    val chromeOpacity: Float
) {

    /** 这个场景最终用的配置（null = 跟随系统）。 */
    fun resolve(contentKey: Any): BackgroundConfig? =
        resolveSceneBackground(
            contentKey = contentKey,
            global = global,
            schedule = schedule,
            pages = pages
        )

    /** 配置对应的图片位图；不是图片背景或图片读不出来时为 null。 */
    fun imageOf(config: BackgroundConfig?): ImageBitmap? =
        config?.takeIf { it.type == TYPE_IMAGE }?.image?.let(images::get)
}

/** 在导航宿主处记忆一份 [SceneBackgroundResolver]，配置或图片变化时自动重解。 */
@Composable
internal fun rememberSceneBackgroundResolver(
    global: BackgroundConfig?,
    schedule: BackgroundConfig?,
    pages: Map<String, BackgroundConfig>,
    chromeOpacity: Float
): SceneBackgroundResolver {
    // 只解真正会被引用的图片（去重）
    val fileNames = remember(global, schedule, pages) {
        (listOfNotNull(global, schedule) + pages.values)
            .filter { it.type == TYPE_IMAGE }
            .mapNotNull { it.image }
            .filter { it.isNotEmpty() }
            .distinct()
    }
    val images = remember(fileNames) {
        fileNames.mapNotNull { name -> loadBackgroundImage(name)?.let { name to it } }.toMap()
    }
    return remember(global, schedule, pages, images, chromeOpacity) {
        SceneBackgroundResolver(global, schedule, pages, images, chromeOpacity)
    }
}

/**
 * 某个场景的**不透明背景层 + 页面内容**（由 `NavEntryDecorator` 包在每个 entry 外面）。
 *
 * 页面本身（Salt 的 `BasicScreen`）是透明的，过去靠最外层 App 根容器垫底色；
 * 进入导航动画后这就不够了：iOS 默认前进转场把旧场景滑出 1/4 屏时，
 * 新场景没画底色的区域会直接露出最底层的底色，看起来像「背景坏了」。
 *
 * 铺一层不透明背景后：新场景滑到哪，背景就铺到哪，转场期间不会露底；
 * 静止时颜色与 App 根容器一致，视觉上没有任何变化。
 *
 * 铺的是**自定义**背景时，还会顺手把 Salt 的 `subBackground`（卡片 / 底栏 / 表单分组的底色）
 * 换成半透明纱并对外广播 [LocalSceneBackdropCustom]——否则日期行、选项卡这些 chrome
 * 会用不透明底把刚铺上去的背景挡得只剩边角。跟随系统时这两件事都不做，外观零变化。
 *
 * @param config 该场景的背景配置（null = 跟随系统）
 * @param image 图片背景的位图（[SceneBackgroundResolver.imageOf]）
 * @param chromeOpacity 卡片/底栏这类 chrome 底色的不透明度（跟随系统时不起作用）
 */
@Composable
internal fun SceneBackgroundContent(
    config: BackgroundConfig?,
    image: ImageBitmap?,
    chromeOpacity: Float = 0.6f,
    content: @Composable () -> Unit
) {
    // 背景读不出来时不算「自定义」，卡片保持不透明，否则会出现「系统底 + 半透明卡片」的错位感
    val custom = when (config?.type) {
        TYPE_COLOR -> true
        TYPE_IMAGE -> image != null
        else -> false
    }
    // 只造一次（不透明度变了才重造）：SaltColors 内部是 mutableState，换新实例会让整屏重组
    val dynamicColors = remember(chromeOpacity) { sceneDynamicColors(chromeOpacity) }

    CompositionLocalProvider(LocalSceneBackdropCustom provides custom) {
        if (custom) {
            // 自定义背景下换一套 subBackground：Salt 的 RoundedColumn / 底栏 / 分组卡片
            // 都读它，于是直接透出背景；其余颜色（含不透明的 popup）原样不动
            SaltTheme(configs = SaltTheme.configs, dynamicColors = dynamicColors) {
                SceneBackdropLayers(config, image, content)
            }
        } else {
            SceneBackdropLayers(config, image, content)
        }
    }
}

/** [SceneBackgroundContent] 的背景层 + 内容（把主题切换剥出来，层本身不关心主题）。 */
@Composable
private fun SceneBackdropLayers(
    config: BackgroundConfig?,
    image: ImageBitmap?,
    content: @Composable () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        when {
            config != null && config.type == TYPE_IMAGE && image != null -> {
                Image(
                    bitmap = image,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .then(if (config.blur) Modifier.blur(ImageBlurRadius) else Modifier)
                )
                val dim = config.clampedDim()
                if (dim > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = dim))
                    )
                }
            }

            config != null && config.type == TYPE_COLOR -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(parseBackgroundColor(config.color))
                )
            }

            else -> {
                // 跟随系统 / 图片读不出来：与 App 根容器同一颜色
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(SaltTheme.colors.background)
                )
            }
        }
        content()
    }
}

/**
 * 解析背景色：支持 `#RGB` / `#RRGGBB` / `#AARRGGBB`（可带 `#` 前缀，忽略大小写与空白）。
 *
 * 认不出来时回落 [BackgroundConfig.DEFAULT_COLOR]——配置文件被手改成乱码也不至于没背景。
 */
internal fun parseBackgroundColor(hex: String): Color {
    val cleaned = hex.trim().removePrefix("#").lowercase()
    val digits = when (cleaned.length) {
        // #RGB → RRGGBB
        3 -> cleaned.map { "$it$it" }.joinToString("")
        6 -> "ff$cleaned"
        8 -> cleaned
        else -> return Color(0xFFF2F2F7)
    }
    val value = digits.toLongOrNull(16) ?: return Color(0xFFF2F2F7)
    return Color(
        red = ((value ushr 16) and 0xFF) / 255f,
        green = ((value ushr 8) and 0xFF) / 255f,
        blue = (value and 0xFF) / 255f,
        alpha = ((value ushr 24) and 0xFF) / 255f
    )
}

/** 输入框用：是否是合法的背景色写法（`#RGB` / `#RRGGBB` / `#AARRGGBB`）。 */
internal fun isValidBackgroundColor(hex: String): Boolean {
    val cleaned = hex.trim().removePrefix("#")
    return cleaned.length in setOf(3, 6, 8) && cleaned.all { it in "0123456789abcdefABCDEF" }
}
