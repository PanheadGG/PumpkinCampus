package com.pgigi.pumpkincampus.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.stringWithContentsOfURL
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerMode
import platform.darwin.NSObject

/**
 * iOS：`UIDocumentPickerViewController`（public.json，Import 模式返回副本），
 * 读取所选 JSON 文件全文。
 */
@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun rememberJsonFilePicker(onResult: (String?) -> Unit): () -> Unit {
    // 回调走最新引用，避免 remember 的闭包捕获过期 lambda
    var onPicked by remember { mutableStateOf(onResult) }
    LaunchedEffect(onResult) { onPicked = onResult }

    return remember {
        {
            val rootVc = UIApplication.sharedApplication.keyWindow?.rootViewController
            if (rootVc == null) {
                onPicked(null)
            } else {
                // 取最上层可弹窗的控制器
                var top = rootVc
                while (true) {
                    val presented = top?.presentedViewController ?: break
                    top = presented
                }

                val picker = UIDocumentPickerViewController(
                    documentTypes = listOf("public.json"),
                    inMode = UIDocumentPickerMode.UIDocumentPickerModeImport
                )
                picker.delegate = object : NSObject(), UIDocumentPickerDelegateProtocol {
                    override fun documentPicker(
                        controller: UIDocumentPickerViewController,
                        didPickDocumentsAtURLs: List<*>
                    ) {
                        val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL
                        val text = url?.let {
                            runCatching {
                                NSString.stringWithContentsOfURL(it, NSUTF8StringEncoding, null)
                            }.getOrNull()
                        }
                        onPicked(text)
                    }

                    override fun documentPickerWasCancelled(
                        controller: UIDocumentPickerViewController
                    ) {
                        onPicked(null)
                    }
                }
                top.presentViewController(picker, animated = true, completion = null)
            }
        }
    }
}
