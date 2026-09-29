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
 * Android：系统文档选择器（`OpenDocument`，图片类型通配），读取所选图片字节。
 */
@Composable
actual fun rememberImageFilePicker(onResult: (ByteArray?) -> Unit): () -> Unit {
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
                context.contentResolver.openInputStream(uri)?.use { stream -> stream.readBytes() }
            }.getOrNull()
            onPicked(bytes)
        }
    }

    return remember {
        {
            launcher.launch(arrayOf("image/*"))
        }
    }
}
