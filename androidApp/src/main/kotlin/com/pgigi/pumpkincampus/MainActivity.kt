package com.pgigi.pumpkincampus

import android.os.Bundle
import androidx.activity.ComponentActivity
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
        enableEdgeToEdge()
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
