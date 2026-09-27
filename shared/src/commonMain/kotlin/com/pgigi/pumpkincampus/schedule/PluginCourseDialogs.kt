package com.pgigi.pumpkincampus.schedule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moriafly.salt.ui.Button
import com.moriafly.salt.ui.ButtonAppearance
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.Text
import com.moriafly.salt.ui.dialog.BasicDialog
import com.moriafly.salt.ui.dialog.DialogTitle
import com.pgigi.pumpkincampus.models.Course

/**
 * 「转换为自定义课程」确认对话框。
 *
 * 插件课程是**只读**数据，要修改必须复制成自定义课程。转换前必须让用户知道
 * 关键后果：**插件课程不会被删除**，插件下次同步更新课表时可能出现重复课程
 * （插件课程 + 已转换的自定义课程）。
 *
 * @param courses 本次要转换的插件课程（1 门 = 课程详情入口；多门 = 全部转换）
 * @param onConfirm 用户确认转换
 * @param onDismissRequest 用户取消
 */
@Composable
internal fun ConvertPluginCoursesDialog(
    courses: List<Course>,
    onConfirm: () -> Unit,
    onDismissRequest: () -> Unit
) {
    val names = courses.map { it.name.ifBlank { "未命名课程" } }.distinct()
    val summary = when {
        courses.size == 1 -> "将把「${names.first()}」复制为可编辑的自定义课程。"
        names.size <= 3 -> "将把 ${courses.size} 门插件课程（${names.joinToString("、")}）" +
            "复制为可编辑的自定义课程。"

        else -> "将把 ${courses.size} 门插件课程复制为可编辑的自定义课程。"
    }

    BasicDialog(onDismissRequest = onDismissRequest) {
        DialogTitle(text = "转换为自定义课程")
        Text(
            text = summary,
            fontSize = SaltTheme.textStyles.main.fontSize,
            color = SaltTheme.colors.text,
            lineHeight = 20.sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SaltTheme.dimens.padding, vertical = 6.dp)
        )
        Text(
            text = "插件课程仍然保留（只读）。插件下次同步更新课表后，可能出现重复课程" +
                "（插件课程 + 已转换的自定义课程），届时删除自定义课程即可。",
            fontSize = SaltTheme.textStyles.sub.fontSize,
            color = SaltTheme.colors.subText,
            lineHeight = 18.sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SaltTheme.dimens.padding, vertical = 4.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)
        ) {
            Button(
                onClick = onDismissRequest,
                text = "取消",
                // 取消类按钮统一低强调（非高亮色）
                appearance = ButtonAppearance.Subtle,
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = onConfirm,
                text = "转换",
                modifier = Modifier.weight(1f)
            )
        }
    }
}
