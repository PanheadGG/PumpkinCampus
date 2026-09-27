package com.pgigi.pumpkincampus.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moriafly.salt.ui.Icon
import com.moriafly.salt.ui.Item
import com.moriafly.salt.ui.ItemArrowType
import com.moriafly.salt.ui.ItemDivider
import com.moriafly.salt.ui.ItemOuterTitle
import com.moriafly.salt.ui.ItemText
import com.moriafly.salt.ui.ItemTip
import com.moriafly.salt.ui.RoundedColumn
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.Text
import com.moriafly.salt.ui.UnstableSaltUiApi
import com.moriafly.salt.ui.icons.Back
import com.moriafly.salt.ui.icons.SaltIcons
import com.moriafly.salt.ui.screen.BasicScreen
import com.moriafly.salt.ui.screen.TitleBarButton
import com.pgigi.pumpkincampus.models.MIT_COPYRIGHT_PLACEHOLDER
import com.pgigi.pumpkincampus.models.OssLicense
import com.pgigi.pumpkincampus.models.getLicenseUrl
import com.pgigi.pumpkincampus.models.ossLicenseGroups
import com.pgigi.pumpkincampus.models.ossLicenses
import pumpkincampus.shared.generated.resources.Res

/**
 * 「开放源代码许可」列表页（关于 → 开放源代码许可）。
 *
 * 按 [OssLicense.group]（在本项目里的角色）分组，每行显示项目名 / 作者 / 许可证名，
 * 点按进入详情页看许可证全文。
 */
@OptIn(UnstableSaltUiApi::class)
@Composable
internal fun OssLicenseListPage(
    onBack: () -> Unit,
    onOpen: (OssLicense) -> Unit
) {
    BasicScreen(
        actionButton = { BackButton(onBack) },
        title = "开放源代码许可"
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize(),
//                .padding(top = contentPadding.calculateTopPadding())
//                .padding(bottom = contentPadding.calculateBottomPadding()),
            contentPadding = contentPadding
        ) {
            ossLicenseGroups.forEach { (group, libraries) ->
                item(key = "title-$group") {
                    ItemOuterTitle(text = group)
                }
                item(key = "group-$group") {
                    RoundedColumn {
                        libraries.forEachIndexed { index, library ->
                            Item(
                                onClick = { onOpen(library) },
                                text = library.title,
                                sub = library.author,
                                tag = library.licence
                            )
                            if (index != libraries.lastIndex) {
                                ItemDivider()
                            }
                        }
                    }
                }
            }
            /*item(key = "tip") {
                ItemTip(
                    text = "共 ${ossLicenses.size} 个开源项目：收录直接依赖，" +
                        "以及随安装包分发、对功能有实质影响的关键传递依赖。" +
                        "未单列的间接依赖（注解、日志门面等）同样遵循各自的许可证。"
                )
            }*/
        }
    }
}

/**
 * 单个开源项目的详情页：项目主页、许可证名（都可点开官方地址）+ 许可证全文。
 *
 * 全文来自 composeResources 的 `files/licenses` 目录（apache-2.0.txt / mit.txt）；
 * MIT 这类带版权占位行的许可证，用清单里的 [OssLicense.copyright] 替换
 * `Copyright (c) [year] [fullname]` 后再展示。
 */
@OptIn(UnstableSaltUiApi::class)
@Composable
internal fun OssLicenseDetailPage(
    license: OssLicense,
    onBack: () -> Unit
) {
    val uriHandler = LocalUriHandler.current
    val licenseText by produceState<String?>(initialValue = null, license) {
        value = loadLicenseText(license)
    }
    val licenseUrl = getLicenseUrl(license.licence)

    BasicScreen(
        actionButton = { BackButton(onBack) },
        title = license.title
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = contentPadding.calculateTopPadding())
                .padding(bottom = contentPadding.calculateBottomPadding() + 24.dp)
        ) {
            RoundedColumn {
                Item(
                    onClick = { uriHandler.openUri(license.link) },
                    text = "项目主页",
                    sub = license.link,
                    arrowType = ItemArrowType.Link
                )
                ItemDivider()
                Item(
                    onClick = { licenseUrl?.let { uriHandler.openUri(it) } },
                    text = "许可证",
                    sub = license.licence,
                    arrowType = ItemArrowType.Link
                )
                license.note?.let { note ->
                    ItemDivider()
//                    ItemText(text = note)
                    Item(
                        onClick = {   },
                        text = "备注",
                        sub = note,
                        arrowType = ItemArrowType.None
                    )
                }
            }

            ItemOuterTitle(text = "许可证全文")
            RoundedColumn {
                Text(
                    text = licenseText ?: "正在加载许可证全文…",
                    fontSize = SaltTheme.textStyles.sub.fontSize,
                    color = SaltTheme.colors.text,
                    lineHeight = 18.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(SaltTheme.dimens.padding)
                )
            }
        }
    }
}

/** 读取许可证全文；失败返回 null（页面显示加载失败文案）。 */
private suspend fun loadLicenseText(license: OssLicense): String? {
    val path = license.file ?: return null
    val raw = runCatching {
        Res.readBytes("files/$path").decodeToString()
    }.getOrNull() ?: return null
    // MIT 全文里是 `Copyright (c) [year] [fullname]` 模板行，换成该项目的版权归属
    return license.copyright?.let { raw.replace(MIT_COPYRIGHT_PLACEHOLDER, it) } ?: raw
}

/** 子页统一的返回按钮（标题左侧）。 */
@OptIn(UnstableSaltUiApi::class)
@Composable
private fun BackButton(onBack: () -> Unit) {
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
}
