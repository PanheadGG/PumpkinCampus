package com.pgigi.pumpkincampus.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.Foundation.NSString
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerViewController

/**
 * iOS：先把 JSON 写入临时目录，再用 `UIDocumentPickerViewController(forExportingURLs:asCopy:)`
 * 调起系统「存储到…」保存流程（文件名即传入的 fileName）。
 */
@Composable
actual fun rememberJsonFileSaver(): (fileName: String, content: String) -> Unit {
    return remember {
        { fileName, content ->
            val path = NSTemporaryDirectory() + fileName
            val written = NSString.create(string = content)
                .writeToFile(path, true, NSUTF8StringEncoding, null)
            if (written) {
                val fileUrl = NSURL.fileURLWithPath(path)
                val rootVc = UIApplication.sharedApplication.keyWindow?.rootViewController
                if (rootVc != null) {
                    // 取最上层可弹窗的控制器
                    var top = rootVc
                    while (true) {
                        val presented = top.presentedViewController ?: break
                        top = presented
                    }
                    val picker = UIDocumentPickerViewController(
                        forExportingURLs = listOf(fileUrl),
                        asCopy = true
                    )
                    top.presentViewController(picker, animated = true, completion = null)
                }
            }
        }
    }
}
