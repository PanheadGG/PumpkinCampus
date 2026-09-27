package com.pgigi.pumpkincampus.schedule

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.moriafly.salt.ui.ItemOuterSpacer
import com.moriafly.salt.ui.ItemOuterTextButton
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.Text
import com.moriafly.salt.ui.UnstableSaltUiApi
import com.moriafly.salt.ui.dialog.YesNoDialog
import com.pgigi.pumpkincampus.DemoWeekCount
import com.pgigi.pumpkincampus.models.Course

/**
 * 「编辑课程」底部弹层：与 [AddCourseSheet] 相同的表单（预填原课程），
 * 底部为「保存修改」与「删除课程」两个按钮。
 *
 * 删除需要二次确认：点击「删除课程」弹出 Salt UI [YesNoDialog]，
 * 确认后才回调 [onDelete]。
 *
 * @param course 要编辑的原课程
 * @param onDismissRequest 关闭弹层
 * @param onSave 保存修改后回调（已通过校验的新课程）
 * @param onDelete 二次确认删除后回调
 * @param weekCount 学期周数（设置页「课表数据 → 学期周数」）
 * @param lessonCount 一天课程节数（设置页「课表数据 → 一天课程节数」）
 */
@OptIn(ExperimentalMaterial3Api::class, UnstableSaltUiApi::class)
@Composable
internal fun EditCourseSheet(
    course: Course,
    onDismissRequest: () -> Unit,
    onSave: (Course) -> Unit,
    onDelete: () -> Unit,
    weekCount: Int = DemoWeekCount,
    lessonCount: Int = DefaultLessonCount
) {
    val form = rememberCourseFormState(
        initial = course,
        weekCount = weekCount,
        lessonCount = lessonCount
    )
    var confirmDelete by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        containerColor = SaltTheme.colors.popup,
        shape = RoundedCornerShape(
            topStart = CourseSheetCornerRadius,
            topEnd = CourseSheetCornerRadius
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "编辑课程",
                fontSize = SaltTheme.textStyles.main.fontSize,
                fontWeight = FontWeight.Bold,
                color = SaltTheme.colors.text,
                modifier = Modifier.padding(
                    start = SaltTheme.dimens.padding,
                    top = SaltTheme.dimens.padding * 0.5f,
                    end = SaltTheme.dimens.padding
                )
            )

            CourseForm(form)

            ItemOuterSpacer()

            ItemOuterTextButton(
                text = "保存修改",
                onClick = {
                    form.buildCourse()?.let { updated ->
                        onSave(updated)
                        onDismissRequest()
                    }
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            ItemOuterTextButton(
                text = "删除课程",
                textColor = Color.White,
                backgroundColor = SaltTheme.colors.error,
                onClick = { confirmDelete = true }
            )

            Spacer(modifier = Modifier.height(SaltTheme.dimens.padding))
        }
    }

    // 删除二次确认
    if (confirmDelete) {
        YesNoDialog(
            onDismissRequest = { confirmDelete = false },
            onConfirm = {
                confirmDelete = false
                onDelete()
                onDismissRequest()
            },
            title = "删除课程",
            content = "确定要删除「${course.name.ifBlank { "未命名课程" }}」吗？删除后不可恢复。",
            cancelText = "取消",
            confirmText = "删除"
        )
    }
}
