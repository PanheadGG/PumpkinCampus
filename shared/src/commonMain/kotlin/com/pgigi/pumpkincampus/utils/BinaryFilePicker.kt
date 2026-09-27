package com.pgigi.pumpkincampus.utils

import androidx.compose.runtime.Composable

/**
 * 记忆一个「打开系统文件选择器，读取所选文件**字节**」的启动器（插件 zip 安装用）。
 *
 * - Android：`ActivityResultContracts.OpenDocument`（zip / octet-stream 等类型）
 * - iOS：`UIDocumentPickerViewController`（public.zip-archive）
 *
 * @param onResult 读取到的文件字节；用户取消、平台不支持或读取失败时回调 null
 * @return 启动系统文件选择器的函数
 */
@Composable
expect fun rememberBinaryFilePicker(onResult: (ByteArray?) -> Unit): () -> Unit
