package com.pgigi.pumpkincampus.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.moriafly.salt.ui.Icon
import com.moriafly.salt.ui.ItemTip
import com.moriafly.salt.ui.RoundedColumn
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.Text
import com.moriafly.salt.ui.UnstableSaltUiApi
import com.moriafly.salt.ui.icons.Back
import com.moriafly.salt.ui.icons.SaltIcons
import com.moriafly.salt.ui.screen.BasicScreen
import com.moriafly.salt.ui.screen.TitleBarButton
import com.pgigi.pumpkincampus.appVersionCode
import com.pgigi.pumpkincampus.appVersionName
import com.pgigi.pumpkincampus.models.ossLicenses
import org.jetbrains.compose.resources.painterResource
import pumpkincampus.shared.generated.resources.Res
import pumpkincampus.shared.generated.resources.ic_launcher_foreground

/** 应用图标底色：与启动图标背景（`androidApp/res/values/ic_launcher_background.xml`）一致。 */
private val AppIconBackground = Color(0xFFFCA016)

/** 应用图标边长（「关于」页展示用）。 */
private val AppIconSize = 104.dp

/** 「版本名 (versionCode)」文案，「关于」页与设置页入口共用。 */
internal fun appVersionLabel(): String = "${appVersionName()} (${appVersionCode()})"

/**
 * 「关于」页（设置 Tab → 关于）。
 *
 * 布局：
 * - **中间**：应用图标（圆角方形 + 品牌底色 + 白色校徽，与启动图标同源）
 * - 图标下方：应用名、`版本名 (versionCode)`
 * - **底部**：「开放源代码许可」入口（进入列表页，可逐个查看许可证全文）
 *
 * 版本号来自平台包信息（Android `PackageInfo` / iOS `Info.plist`），
 * 不在代码里写死，发版时随构建脚本自动更新。
 */
@OptIn(UnstableSaltUiApi::class)
@Composable
internal fun AboutPage(
    onBack: () -> Unit,
    onOpenLicenses: () -> Unit
) {
    val version = remember { appVersionLabel() }

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
        title = "关于"
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = contentPadding.calculateTopPadding())
                .padding(bottom = contentPadding.calculateBottomPadding())
        ) {
            // 图标区：占满剩余高度并居中 → 图标落在页面大致中间的位置
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    AppIcon()
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = "南瓜校园",
                        fontSize = SaltTheme.textStyles.largeTitle.fontSize,
                        color = SaltTheme.colors.text
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = version,
                        fontSize = SaltTheme.textStyles.sub.fontSize,
                        color = SaltTheme.colors.subText
                    )
                }
            }

            // 底部：开源许可入口
            RoundedColumn {
                NavRow(
                    title = "开放源代码许可",
//                    value = "${ossLicenses.size} 个开源项目",
                    onClick = onOpenLicenses
                )
            }
            /*ItemTip(
                text = "本 App 基于 Kotlin 与 Compose Multiplatform 构建，" +
                    "课表插件依赖 QuickJS 引擎运行。点击上方条目可查看各项目的许可证全文。"
            )*/
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

/**
 * 应用图标：品牌色圆角方块 + 白色校徽。
 *
 * 图形取自启动图标的前景矢量（composeResources 里同一份 `ic_launcher_foreground.xml`），
 * 保证「关于」页与桌面图标一致；圆角比例按自适应图标的取整规则取 22%。
 */
@Composable
private fun AppIcon() {
    Box(
        modifier = Modifier
            .size(80.dp)
            .clip(RoundedCornerShape(AppIconSize * 0.22f))
            .background(AppIconBackground),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(Res.drawable.ic_launcher_foreground),
            contentDescription = "南瓜校园图标",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.FillBounds
        )
    }
}
