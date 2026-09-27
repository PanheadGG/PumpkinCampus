package com.pgigi.pumpkincampus.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moriafly.salt.ui.Button
import com.moriafly.salt.ui.ButtonAppearance
import com.moriafly.salt.ui.Icon
import com.moriafly.salt.ui.ItemDivider
import com.moriafly.salt.ui.ItemOuterTitle
import com.moriafly.salt.ui.ItemTip
import com.moriafly.salt.ui.RoundedColumn
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.Text
import com.moriafly.salt.ui.UnstableSaltUiApi
import com.moriafly.salt.ui.dialog.BasicDialog
import com.moriafly.salt.ui.dialog.DialogTitle
import com.moriafly.salt.ui.icons.Back
import com.moriafly.salt.ui.icons.SaltIcons
import com.moriafly.salt.ui.screen.BasicScreen
import com.moriafly.salt.ui.screen.TitleBarButton
import com.pgigi.pumpkincampus.plugin.InstalledPlugin
import com.pgigi.pumpkincampus.plugin.PluginUiState
import com.pgigi.pumpkincampus.utils.rememberBinaryFilePicker

/**
 * 「教务系统插件」**全局管理**页（设置 Tab 导航栈推送）：
 * 从 zip 安装插件、查看已安装列表与详情、卸载插件。
 *
 * 插件安装/卸载是**全局**的——卸载后所有课表对该插件的选择都会失效；
 * 每个课表**选择哪个插件、填了什么配置**在课表页「课表设置 → 教务系统插件」中维护。
 *
 * @param pluginUi 插件 UI 状态与回调（由 HomeScreen 构建）
 * @param onBack 弹栈
 */
@OptIn(UnstableSaltUiApi::class)
@Composable
internal fun PluginsPage(
    pluginUi: PluginUiState?,
    onBack: () -> Unit
) {
    val state = pluginUi ?: PluginUiState()
    var detail by remember { mutableStateOf<InstalledPlugin?>(null) }
    var confirmUninstall by remember { mutableStateOf<InstalledPlugin?>(null) }

    // 从 ZIP 安装：系统文件选择器读出字节 → HomeScreen 统一安装并弹结果
    val pickZip = rememberBinaryFilePicker { bytes ->
        if (bytes != null) state.onInstall(bytes)
    }

    BasicScreen(
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
        title = "教务系统插件",
        subtitle = "全局安装与卸载"
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = contentPadding.calculateTopPadding())
                .padding(bottom = contentPadding.calculateBottomPadding() + 24.dp)
        ) {
            ItemOuterTitle(text = "安装")
            RoundedColumn {
                NavRow(
                    title = "从文件安装插件",
                    value = if (state.installing) "安装中…" else null,
                    onClick = { if (!state.installing) pickZip() }
                )
            }
            /*ItemTip(
                text = "插件包为 zip：根目录放 manifest.json + index.js + README.md" +
                    "（整个文件夹打包也可以，宿主会自动剥掉外层目录）。" +
                    "同 id 再次安装即覆盖升级；安装与卸载对全部课表生效。"
            )*/

            ItemOuterTitle(text = "已安装（${state.installed.size}）")
            if (state.installed.isEmpty()) {
                ItemTip(text = "暂无已安装插件。安装后回到各课表的「课表设置 → 教务系统插件」中选择并配置。")
            } else {
                RoundedColumn {
                    state.installed.forEachIndexed { index, plugin ->
                        if (index > 0) ItemDivider()
                        NavRow(
                            title = plugin.name,
                            value = buildString {
                                append("v").append(plugin.manifest.version)
                                if (plugin.manifest.schoolName.isNotBlank()) {
                                    append(" · ").append(plugin.manifest.schoolName)
                                }
                                if (!plugin.compatible) append(" · 需更新 App")
                            },
                            onClick = { detail = plugin }
                        )
                    }
                }
            }

            ItemTip(
                text = "插件由社区提供，会以你的身份访问教务系统；请仅安装信任来源的插件包。" +
                    "插件的配置（账号密码等）与拉取的课表按课表隔离保存在本机，" +
                    "其中密码类配置使用系统加密存储（Android Keystore / iOS Keychain）保存。"
            )
        }
    }

    // 插件详情（含卸载入口）
    detail?.let { plugin ->
        BasicDialog(onDismissRequest = { detail = null }) {
            DialogTitle(text = plugin.name)
            val manifest = plugin.manifest
            Column(modifier = Modifier.fillMaxWidth()) {
                DialogInfoLine("ID", manifest.id)
                DialogInfoLine("版本", "v${manifest.version}")
                if (manifest.schoolName.isNotBlank()) DialogInfoLine("学校", manifest.schoolName)
                if (manifest.author.isNotBlank()) DialogInfoLine("作者", manifest.author)
                if (manifest.updateTime.isNotBlank()) DialogInfoLine("更新时间", manifest.updateTime)
                DialogInfoLine("最低宿主版本", "versionCode ≥ ${manifest.minHostVersionCode}")
                DialogInfoLine(
                    "当前宿主",
                    if (plugin.compatible) "兼容" else "版本不足，请升级 App"
                )
                if (manifest.description.isNotBlank()) {
                    Text(
                        text = manifest.description,
                        fontSize = SaltTheme.textStyles.sub.fontSize,
                        color = SaltTheme.colors.subText,
                        lineHeight = 16.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = SaltTheme.dimens.padding, vertical = 4.dp)
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)
            ) {
                Button(
                    onClick = { detail = null },
                    text = "关闭",
                    appearance = ButtonAppearance.Subtle,
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = {
                        confirmUninstall = plugin
                        detail = null
                    },
                    text = "卸载",
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }

    // 卸载二次确认（卸载会同时清除该插件的 KV 数据）
    confirmUninstall?.let { plugin ->
        BasicDialog(onDismissRequest = { confirmUninstall = null }) {
            DialogTitle(text = "卸载插件")
            Text(
                text = "确定卸载「${plugin.name}」？\n" +
                    "卸载后所有课表都无法再使用它，插件保存的数据（缓存/登录态）会一并清除；" +
                    "各课表已填写的配置会保留，重新安装后可继续使用。",
                fontSize = SaltTheme.textStyles.sub.fontSize,
                color = SaltTheme.colors.subText,
                lineHeight = 16.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SaltTheme.dimens.padding, vertical = 4.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)
            ) {
                Button(
                    onClick = { confirmUninstall = null },
                    text = "取消",
                    appearance = ButtonAppearance.Subtle,
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = {
                        state.onUninstall(plugin.id)
                        confirmUninstall = null
                    },
                    text = "卸载",
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/** 详情弹层里的「标签：值」行。 */
@Composable
private fun DialogInfoLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = SaltTheme.dimens.padding, vertical = 2.dp)) {
        Text(
            text = label,
            fontSize = SaltTheme.textStyles.sub.fontSize,
            color = SaltTheme.colors.subText,
            modifier = Modifier.padding(end = 12.dp)
        )
        Text(
            text = value,
            fontSize = SaltTheme.textStyles.sub.fontSize,
            color = SaltTheme.colors.text,
            fontWeight = FontWeight.Normal,
            modifier = Modifier.weight(1f)
        )
    }
}
