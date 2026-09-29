package com.pgigi.pumpkincampus

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import com.pgigi.pumpkincampus.utils.AppContextHolder

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        // 系统导航条（屏幕最下面那一行）始终透明，让应用自己画的底栏颜色直接显示到屏幕底边：
        // 默认 auto() 会在 API ≤28 盖一层系统浅/深色蒙层（scrim），3 键导航还会把
        // isNavigationBarContrastEnforced 开为 true 叠一层系统对比色，
        // 导致导航条那一行与状态栏 / 底栏颜色对不上。这里显式传 SystemBarStyle.light：
        //   scrim = TRANSPARENT → API 26~34 全透明、不盖任何系统蒙层；
        //   且 nightMode != MODE_NIGHT_AUTO → enableEdgeToEdge 会把
        //   isNavigationBarContrastEnforced 设为 false，不加系统对比层。
        // darkScrim 参数仅在 API ≤25 生效（该版本无法给系统导航按键换色），
        // 保留系统深色蒙层保证白色按键在浅色底上仍然可见。
        enableEdgeToEdge(
            navigationBarStyle = SystemBarStyle.light(
                Color.TRANSPARENT,
                Color.argb(0x80, 0x1b, 0x1b, 0x1b)
            )
        )
        super.onCreate(savedInstanceState)

        // 文件存储需要的应用上下文（FileStoreUtils 的 Android 数据目录）
        AppContextHolder.context = applicationContext

        setContent {
            App(onDarkThemeChange = ::applySystemBarAppearance)
        }
    }

    /**
     * 跟随 Salt UI 的深色模式调整状态栏 / 导航栏图标颜色，
     * 避免深色背景 + 深色图标导致看不清。
     */
    private fun applySystemBarAppearance(isDarkTheme: Boolean) {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !isDarkTheme
            isAppearanceLightNavigationBars = !isDarkTheme
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}
