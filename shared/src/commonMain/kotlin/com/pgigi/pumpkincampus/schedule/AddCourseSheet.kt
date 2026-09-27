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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.moriafly.salt.ui.ItemOuterSpacer
import com.moriafly.salt.ui.ItemOuterTextButton
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.Text
import com.moriafly.salt.ui.UnstableSaltUiApi
import com.pgigi.pumpkincampus.DemoWeekCount
import com.pgigi.pumpkincampus.models.Course

/**
 * 「添加课程」模态底部弹层：Material 3 [ModalBottomSheet]（与 [CourseBottomSheet] 一致）。
 *
 * 表单字段由 [CourseForm] 提供：
 * 课程名称 / 教师 / 教室、星期（方块单选）、周数（方块多选，选中蓝色/未选灰色）、
 * 节数（开始/结束滑动组块，结束不能小于开始）。
 *
 * @param onDismissRequest 关闭弹层
 * @param onAdd 校验通过后回调新增的 [Course]
 * @param weekCount 学期周数（设置页「课表数据 → 学期周数」）
 * @param lessonCount 一天课程节数（设置页「课表数据 → 一天课程节数」）
 */
@OptIn(ExperimentalMaterial3Api::class, UnstableSaltUiApi::class)
@Composable
internal fun AddCourseSheet(
    onDismissRequest: () -> Unit,
    onAdd: (Course) -> Unit,
    weekCount: Int = DemoWeekCount,
    lessonCount: Int = DefaultLessonCount
) {
    val form = rememberCourseFormState(
        initial = null,
        weekCount = weekCount,
        lessonCount = lessonCount
    )

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
                text = "添加课程",
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
                text = "添加课程",
                onClick = {
                    form.buildCourse()?.let { course ->
                        onAdd(course)
                        onDismissRequest()
                    }
                }
            )

            Spacer(modifier = Modifier.height(SaltTheme.dimens.padding))
        }
    }
}
