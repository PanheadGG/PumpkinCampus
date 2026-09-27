package com.pgigi.pumpkincampus.utils

import androidx.compose.runtime.Composable

/**
 * 记忆一个「调起系统分享面板，分享一段纯文本」的启动器。
 *
 * - Android：`Intent.ACTION_SEND`（text/plain）+ 系统 Chooser
 * - iOS：`UIActivityViewController`
 *
 * @return `(text) -> Unit`，传入要分享的完整文案
 */
@Composable
expect fun rememberTextSharer(): (String) -> Unit
