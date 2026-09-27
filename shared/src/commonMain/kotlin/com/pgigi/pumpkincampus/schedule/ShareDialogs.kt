package com.pgigi.pumpkincampus.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moriafly.salt.ui.Button
import com.moriafly.salt.ui.ButtonAppearance
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.Text
import com.moriafly.salt.ui.dialog.BasicDialog
import com.moriafly.salt.ui.dialog.DialogTitle
import com.pgigi.pumpkincampus.models.ImportedSchedule
import com.pgigi.pumpkincampus.settings.parseImportedSchedule
import com.pgigi.pumpkincampus.utils.getShareContent
import kotlinx.coroutines.launch

/**
 * 「分享课表」对话框：展示带分享口令的推荐语。
 *
 * - 第一个按钮「分享」：调起系统分享面板（文案由调用方通过 [sharer] 提供）
 * - 第二个按钮「复制」：把整段推荐语写入剪贴板，点击后变为「已复制」
 *
 * @param message 含分享口令的完整推荐语
 * @param sharer 系统分享启动器（见 `rememberTextSharer`）
 */
@Composable
internal fun ShareCourseDialog(
    message: String,
    sharer: (String) -> Unit,
    onDismissRequest: () -> Unit
) {
    var copied by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current

    BasicDialog(onDismissRequest = onDismissRequest) {
        DialogTitle(text = "分享课表")
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SaltTheme.dimens.padding, vertical = 4.dp)
                .height(180.dp)
                .background(
                    SaltTheme.colors.subBackground,
                    RoundedCornerShape(10.dp)
                )
                .padding(10.dp)
        ) {
            Text(
                text = message,
                fontSize = SaltTheme.textStyles.sub.fontSize,
                color = SaltTheme.colors.subText,
                lineHeight = 20.sp,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)
        ) {
            Button(
                onClick = { sharer(message) },
                text = "分享",
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = {
                    clipboard.setText(AnnotatedString(message))
                    copied = true
                },
                text = if (copied) "已复制" else "复制",
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * 「从分享口令导入」对话框：粘贴朋友分享的整段推荐语（或单独的口令），
 * 自动从中检测口令 → `GET /get/{key}` 拉取课表内容 → 按导出信封解析
 * → 交由调用方走统一导入流程（设置课表名称 → 确定导入）。
 */
@Composable
internal fun ShareImportDialog(
    onDismissRequest: () -> Unit,
    onImport: (ImportedSchedule) -> Unit
) {
    var raw by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    BasicDialog(onDismissRequest = { if (!loading) onDismissRequest() }) {
        DialogTitle(text = "从分享口令导入")
        Text(
            text = "粘贴朋友发来的整段分享消息或分享口令；分享内容30分钟内有效，" +
                "失效后请朋友重新分享。",
            fontSize = SaltTheme.textStyles.sub.fontSize,
            color = SaltTheme.colors.subText,
            lineHeight = 16.sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SaltTheme.dimens.padding, vertical = 4.dp)
        )
        BasicTextField(
            value = raw,
            onValueChange = {
                raw = it
                error = null
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .padding(horizontal = SaltTheme.dimens.padding),
            textStyle = TextStyle(
                fontSize = SaltTheme.textStyles.sub.fontSize,
                color = SaltTheme.colors.text,
                lineHeight = 18.sp
            ),
            cursorBrush = SolidColor(SaltTheme.colors.highlight),
            decorationBox = { inner ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(SaltTheme.colors.subBackground, RoundedCornerShape(10.dp))
                        .padding(10.dp)
                ) {
                    if (raw.isEmpty()) {
                        Text(
                            text = "在此粘贴分享消息或口令…",
                            fontSize = SaltTheme.textStyles.sub.fontSize,
                            color = SaltTheme.colors.subText
                        )
                    }
                    inner()
                }
            }
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SaltTheme.dimens.padding, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.height(16.dp),
                    color = SaltTheme.colors.highlight,
                    strokeWidth = 2.dp
                )
                Text(
                    text = "正在获取分享内容…",
                    fontSize = SaltTheme.textStyles.sub.fontSize,
                    color = SaltTheme.colors.subText,
                    modifier = Modifier.padding(start = 8.dp)
                )
            } else {
                error?.let {
                    Text(
                        text = it,
                        fontSize = SaltTheme.textStyles.sub.fontSize,
                        color = SaltTheme.colors.error
                    )
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)
        ) {
            Button(
                onClick = onDismissRequest,
                text = "取消",
                // 取消类按钮统一低强调（非高亮色）
                appearance = ButtonAppearance.Subtle,
                enabled = !loading,
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = {
                    val key = com.pgigi.pumpkincampus.utils.extractShareKey(raw)
                    if (key == null) {
                        error = "未找到分享口令：请粘贴完整的分享消息"
                    } else {
                        loading = true
                        error = null
                        scope.launch {
                            val content = getShareContent(key)
                            val parsed = content?.let { parseImportedSchedule(it) }
                            loading = false
                            when {
                                content == null ->
                                    error = "分享口令不存在或已过期（30分钟）"

                                parsed == null ->
                                    error = "分享内容无法解析"

                                else -> onImport(parsed)
                            }
                        }
                    }
                },
                text = if (loading) "获取中…" else "导入",
                enabled = !loading,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
