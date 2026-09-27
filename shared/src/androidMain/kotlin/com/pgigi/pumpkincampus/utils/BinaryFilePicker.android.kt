package com.pgigi.pumpkincampus.utils

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/**
 * Android：系统文档选择器（`OpenDocument`），读取所选文件（插件 zip）的字节。
 * mime 列表带通配类型兜底——部分文件管理器把 zip 标成未知类型，避免选不出来。
 */
@Composable
actual fun rememberBinaryFilePicker(onResult: (ByteArray?) -> Unit): () -> Unit {
    val context = LocalContext.current

    // 回调走最新引用，避免 remember 的 launcher 捕获过期 lambda
    var onPicked by remember { mutableStateOf(onResult) }
    LaunchedEffect(onResult) { onPicked = onResult }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) {
            onPicked(null) // 用户取消
        } else {
            val bytes = runCatching {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    stream.readBytes()
                }
            }.getOrNull()
            onPicked(bytes)
        }
    }

    return remember {
        {
            launcher.launch(
                arrayOf(
                    "application/zip",
                    "application/x-zip-compressed",
                    "application/octet-stream",
                    "*/*"
                )
            )
        }
    }
}
