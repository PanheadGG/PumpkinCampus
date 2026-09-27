package com.pgigi.pumpkincampus.schedule

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.moriafly.salt.ui.SaltTheme
import com.pgigi.pumpkincampus.models.Course
import com.pgigi.pumpkincampus.models.LessonTime

/**
 * 课程详情底部弹层：直接复用 Material 3 的 [ModalBottomSheet]
 * （`org.jetbrains.compose.material3:material3`，Android / iOS 通用）。
 *
 * 遮罩、点击遮罩/返回键关闭、上下拖动收起以及入场/退场动画全部由 Material 3 提供；
 * 面板内容仍为 Salt UI 风格的 [CourseDetailSheetContent]（拖拽指示条由 M3 的
 * `BottomSheetDefaults.DragHandle()` 提供）。
 *
 * @param course 要展示的课程
 * @param onDismissRequest 用户关闭弹层时回调（用于移除弹层状态）
 * @param onEdit 非空时详情底部显示「编辑课程」按钮，回调要编辑的课程
 * @param lessonTimes 作息时间表：详情里「第几节 几点-几点」的钟点由此换算
 * @param conflictCourses 冲突课程组（多于 1 门时生效）：标题右侧显示横向滑动的
 *   课程名 Chip，选中的 Chip 高亮，点击切换当前展示的课程（代替旧的选择 Dialog）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CourseBottomSheet(
    course: Course,
    onDismissRequest: () -> Unit,
    onEdit: ((Course) -> Unit)? = null,
    lessonTimes: List<LessonTime> = emptyList(),
    conflictCourses: List<Course> = emptyList()
) {
    // 冲突组内当前选中的课程下标（Chip 高亮与详情展示同步切换）
    var selectedIndex by remember(conflictCourses) {
        mutableStateOf(conflictCourses.indexOf(course).coerceAtLeast(0))
    }
    val displayed = if (conflictCourses.size > 1) {
        conflictCourses.getOrElse(selectedIndex) { course }
    } else {
        course
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        containerColor = SaltTheme.colors.popup,
        shape = RoundedCornerShape(
            topStart = CourseSheetCornerRadius,
            topEnd = CourseSheetCornerRadius
        )
    ) {
        val editAction: (() -> Unit)? = onEdit?.let { callback -> { callback(displayed) } }
        CourseDetailSheetContent(
            course = displayed,
            onClose = onDismissRequest,
            onEdit = editAction,
            lessonTimes = lessonTimes,
            conflictCourses = conflictCourses,
            selectedConflictIndex = selectedIndex,
            onSelectConflict = { selectedIndex = it }
        )
    }
}
