package com.pgigi.pumpkincampus.utils

import androidx.compose.runtime.Composable

/**
 * 记忆一个「打开系统文件选择器，读取 JSON 文件文本」的启动器。
 *
 * - Android：`ActivityResultContracts.OpenDocument`（application/json / text 类型）
 * - iOS：`UIDocumentPickerViewController`（public.json）
 *
 * @param onResult 读取到的文件文本；用户取消、平台不支持或读取失败时回调 null
 * @return 启动系统文件选择器的函数
 */
@Composable
expect fun rememberJsonFilePicker(onResult: (String?) -> Unit): () -> Unit
