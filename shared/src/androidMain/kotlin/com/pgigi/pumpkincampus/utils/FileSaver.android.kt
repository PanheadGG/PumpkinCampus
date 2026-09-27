package com.pgigi.pumpkincampus.utils

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/**
 * Android：系统「新建文档」选择器（`CreateDocument`）——
 * 用户确认文件名与位置后，把 JSON 内容写入返回的 Uri。
 */
@Composable
actual fun rememberJsonFileSaver(): (fileName: String, content: String) -> Unit {
    val context = LocalContext.current

    // 待写入的内容：选择器返回 Uri 后落盘（文件名已在 launch 时给出）
    var pendingContent by remember { mutableStateOf<String?>(null) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val content = pendingContent
        if (uri != null && content != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(content.encodeToByteArray())
                }
            }
        }
        pendingContent = null
    }

    return remember {
        { fileName, content ->
            pendingContent = content
            launcher.launch(fileName)
        }
    }
}
