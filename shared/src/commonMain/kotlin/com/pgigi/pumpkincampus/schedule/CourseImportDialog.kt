package com.pgigi.pumpkincampus.schedule

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.moriafly.salt.ui.Button
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.Text

/** 导入解析失败提示（可任意关闭）。 */
@Composable
internal fun ImportErrorDialog(
    message: String,
    onDismiss: () -> Unit,
    title: String = "导入失败"
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SaltTheme.colors.popup,
        title = {
            Text(
                text = title,
                fontSize = SaltTheme.textStyles.main.fontSize,
                color = SaltTheme.colors.text
            )
        },
        text = {
            Text(
                text = message,
                fontSize = SaltTheme.textStyles.main.fontSize,
                color = SaltTheme.colors.subText
            )
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                text = "确定",
                modifier = Modifier.fillMaxWidth()
            )
        }
    )
}
