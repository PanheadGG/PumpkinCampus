package com.pgigi.pumpkincampus.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.Foundation.NSString
import platform.Foundation.create
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication

/**
 * iOS：`UIActivityViewController` 系统分享面板。
 * API 签名对照 Kotlin/Native 平台库 `platform.UIKit` klib 核实。
 */
@Composable
actual fun rememberTextSharer(): (String) -> Unit = remember {
    { text ->
        val activityItems = listOf(NSString.create(string = text))
        val controller = UIActivityViewController(activityItems, null)
        val rootViewController =
            UIApplication.sharedApplication.keyWindow?.rootViewController
        rootViewController?.presentViewController(controller, animated = true, completion = null)
    }
}
