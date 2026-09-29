package com.pgigi.pumpkincampus.models

import kotlinx.serialization.Serializable

/**
 * 自定义背景配置（「设置 → 课表外观 → 自定义背景」）。
 *
 * 同一份结构会出现在三个**作用域**里，优先级从高到低：
 *
 * 1. **按页面**（[AppSettings.pageBackgrounds]）：按 [GROUP_AGENDA] / [GROUP_TIMETABLE] /
 *    [GROUP_SETTINGS] 分组逐组覆盖，最高优先；
 * 2. **单课表**（[CourseSchedule.settings] 里的 [AppSettings.background]）：只对**课表相关页面**
 *    （课表 tab 及其子页）生效，切换课表自动换背景；
 * 3. **全局**（[AppSettings.background]，存 `settings.json`）：整个 App 的兜底背景。
 *
 * 三层都没有时回落跟随系统主题（[TYPE_SYSTEM]）。解析逻辑见
 * `com.pgigi.pumpkincampus.pages.resolveSceneBackground`。
 *
 * @property type 背景类型：[TYPE_SYSTEM] 跟随系统 / [TYPE_COLOR] 纯色 / [TYPE_IMAGE] 图片
 * @property color [TYPE_COLOR] 的颜色：`#RRGGBB` 或 `#AARRGGBB`
 * @property image [TYPE_IMAGE] 的图片文件名（相对应用数据目录，见 `FileStoreUtils`）。
 *   图片在**导入时**就被降采样压成 JPEG 存进应用私有目录，配置里只存文件名，
 *   换设备/清数据后文件不存在会自动回落跟随系统
 * @property dim 图片之上的暗色蒙层强度 `0f..0.85f`：背景太花时压暗，保证课程文字可读
 * @property blur 图片是否做高斯模糊（同样是为了文字可读）
 */
@Serializable
data class BackgroundConfig(
    val type: String = TYPE_SYSTEM,
    val color: String = DEFAULT_COLOR,
    val image: String? = null,
    val dim: Float = 0.35f,
    val blur: Boolean = false
) {

    /** 按类型取蒙层强度（归一化到 0..0.85，脏数据兜底）。 */
    fun clampedDim(): Float = dim.coerceIn(0f, 0.85f)

    companion object {
        /** 跟随应用主题背景（等于不自定义，但可用来在「按页面」层显式压过上层配置）。 */
        const val TYPE_SYSTEM = "system"

        /** 纯色背景，取 [color]。 */
        const val TYPE_COLOR = "color"

        /** 图片背景，取 [image]。 */
        const val TYPE_IMAGE = "image"

        /** 默认纯色：与系统浅色分组底色接近的浅灰。 */
        const val DEFAULT_COLOR = "#F2F2F7"

        /** 背景图片存放目录（相对应用数据目录）。 */
        const val IMAGE_DIR = "background"

        /* ------- 「按页面」覆盖的分组 id（一个分组 = 一个 tab 及其全部子页） ------- */

        /** 日程 tab（只有根页）。 */
        const val GROUP_AGENDA = "agenda"

        /** 课表 tab + 课表设置 / 教务插件 / 时间表 / 时间表编辑四个子页。 */
        const val GROUP_TIMETABLE = "timetable"

        /** 设置 tab + 外观 / 全局课表设置 / 时间表 / 插件 / 关于 / 开源许可等子页。 */
        const val GROUP_SETTINGS = "settings"
    }
}
