package com.pgigi.pumpkincampus.utils

import androidx.compose.runtime.Composable

/**
 * 记忆一个「调起系统『保存 / 存储文件』选择器，把文本写成文件」的启动器。
 *
 * - Android：`ActivityResultContracts.CreateDocument`（application/json），
 *   用户选好位置后把内容写入返回的 Uri
 * - iOS：先写入临时文件，再用 `UIDocumentPickerViewController(forExportingURLs:asCopy:)`
 *   走系统「存储到…」流程
 *
 * @return `(fileName, content) -> Unit`，fileName 形如 `pumpkin_<时间戳>_schedule.json`
 */
@Composable
expect fun rememberJsonFileSaver(): (fileName: String, content: String) -> Unit
