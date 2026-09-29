package com.pgigi.pumpkincampus.utils

import androidx.compose.runtime.Composable

/**
 * 记忆一个「打开系统选择器，读取所选**图片字节**」的启动器（自定义背景用）。
 *
 * - Android：`ActivityResultContracts.OpenDocument`（类型通配 `image` + 星号）
 * - iOS：`UIDocumentPickerViewController`（`public.image`，Import 模式返回副本，
 *   HEIC 等格式由 [downscaleToJpeg] 落库前统一转成 JPEG）
 *
 * @param onResult 读到的图片字节；用户取消、平台不支持或读取失败时回调 null
 * @return 启动系统选择器的函数
 */
@Composable
expect fun rememberImageFilePicker(onResult: (ByteArray?) -> Unit): () -> Unit
