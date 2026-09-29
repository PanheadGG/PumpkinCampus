package com.pgigi.pumpkincampus.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSURL
import platform.Foundation.dataWithContentsOfURL
import platform.Foundation.length
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerMode
import platform.UIKit.UIDocumentPickerViewController
import platform.darwin.NSObject
import platform.posix.memcpy

/**
 * iOS：`UIDocumentPickerViewController`（`public.image`，Import 模式返回副本），
 * 读取所选图片（相册导出的 HEIC / JPEG / PNG 都能选）的字节。
 */
@Composable
actual fun rememberImageFilePicker(onResult: (ByteArray?) -> Unit): () -> Unit {
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
                    documentTypes = listOf("public.image"),
                    inMode = UIDocumentPickerMode.UIDocumentPickerModeImport
                )
                picker.delegate = object : NSObject(), UIDocumentPickerDelegateProtocol {
                    override fun documentPicker(
                        controller: UIDocumentPickerViewController,
                        didPickDocumentsAtURLs: List<*>
                    ) {
                        val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL
                        onPicked(url?.let { readImageBytes(it) })
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

/** 把选中文件的 NSData 拷成 Kotlin ByteArray（失败返回 null）。 */
@OptIn(ExperimentalForeignApi::class)
private fun readImageBytes(url: NSURL): ByteArray? {
    val data = runCatching { NSData.dataWithContentsOfURL(url) }.getOrNull() ?: return null
    val length = data.length.toInt()
    if (length <= 0) return ByteArray(0)
    return runCatching {
        ByteArray(length).also { array ->
            array.usePinned { pinned ->
                memcpy(pinned.addressOf(0), data.bytes, data.length)
            }
        }
    }.getOrNull()
}
