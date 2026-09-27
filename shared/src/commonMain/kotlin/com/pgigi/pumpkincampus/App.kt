package com.pgigi.pumpkincampus

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.moriafly.salt.ui.SaltConfigs
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.UnstableSaltUiApi
import com.pgigi.pumpkincampus.pages.HomeScreen

/**
 * 应用入口，使用 Salt UI (io.github.moriafly:salt-ui:3.0.0-beta01) 的 [SaltTheme] 作为主题。
 *
 * 颜色模式由设置页「颜色模式」选择（settings.json 的 `colorMode`）：
 * - `system` 跟随系统（[isSystemInDarkTheme] 实时跟随）
 * - `light` 浅色模式（固定浅色）
 * - `dark` 深色模式（固定深色）
 * 值经 [HomeScreen] 加载设置后通过 `onColorModeChange` 回调上来。
 *
 * 注意：Salt UI 的 [com.moriafly.salt.ui.screen.BasicScreen] 只负责排版，不绘制窗口背景，
 * 因此这里必须用 [SaltTheme.colors.background] 铺满根容器，否则深色模式下背景仍是浅色。
 *
 * @param onDarkThemeChange 深色模式变化回调，平台侧可据此调整状态栏 / 导航栏图标颜色。
 */
@OptIn(UnstableSaltUiApi::class)
@Composable
@Preview
fun App(onDarkThemeChange: ((Boolean) -> Unit)? = null) {
    val systemDarkTheme = isSystemInDarkTheme()
    // 颜色模式（system / light / dark），来自设置页并持久化在 settings.json
    var colorMode by remember { mutableStateOf("system") }
    val isDarkTheme = when (colorMode) {
        "light" -> false
        "dark" -> true
        else -> systemDarkTheme
    }

    LaunchedEffect(isDarkTheme) {
        onDarkThemeChange?.invoke(isDarkTheme)
    }

    SaltTheme(
        configs = SaltConfigs.default(isDarkTheme = isDarkTheme)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(SaltTheme.colors.background)
        ) {
            // 主页：底部 BottomBar 切换「日程 / 课表」，页面间淡出淡入（不使用 Pager）
            HomeScreen(onColorModeChange = { colorMode = it })
        }
    }
}
