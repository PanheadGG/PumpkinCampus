package com.pgigi.pumpkincampus.models

import kotlinx.serialization.Serializable

/**
 * 「关于 → 开放源代码许可」里的一条开源项目记录。
 *
 * @property title 项目名（列表主标题）
 * @property author 作者 / 组织（列表右侧、详情页展示）
 * @property link 项目主页（详情页可点开）
 * @property licence 许可证名（[APACHE_LICENSE] / [MIT_LICENSE]）
 * @property copyright MIT 这类带版权行的许可证：展示时替换掉文本模板里的
 *   `Copyright (c) [year] [fullname]` 占位行
 * @property note 在本项目里的用途（详情页补充说明，可为空）
 * @property file composeResources 里的许可证全文路径（见 [APACHE_LICENSE_FILE]）
 * @property group 列表分组标题（按「在本项目里的角色」分组）
 */
@Serializable
data class OssLicense(
    val title: String,
    val author: String,
    val link: String,
    val licence: String,
    val copyright: String? = null,
    val note: String? = null,
    val file: String? = null,
    val group: String? = null
)

/** Apache License 2.0（[APACHE_LICENSE_FILE] 里有全文）。 */
const val APACHE_LICENSE = "Apache License 2.0"

/** MIT License（[MIT_LICENSE_FILE] 里有全文）。 */
const val MIT_LICENSE = "MIT License"

/** Apache-2.0 全文在 composeResources 里的路径。 */
const val APACHE_LICENSE_FILE = "licenses/apache-2.0.txt"

/** MIT 全文在 composeResources 里的路径（含 `[year] [fullname]` 占位行）。 */
const val MIT_LICENSE_FILE = "licenses/mit.txt"

/** MIT 全文里的版权占位行：展示时用 [OssLicense.copyright] 替换。 */
const val MIT_COPYRIGHT_PLACEHOLDER = "Copyright (c) [year] [fullname]"

/** 许可证全文的官方地址（详情页点许可证名跳转）；未知许可证返回 null。 */
fun getLicenseUrl(licenseName: String): String? = when {
    licenseName.contains("Apache", ignoreCase = true) ->
        "https://www.apache.org/licenses/LICENSE-2.0"
    licenseName.contains("MIT", ignoreCase = true) ->
        "https://opensource.org/licenses/MIT"
    else -> null
}
