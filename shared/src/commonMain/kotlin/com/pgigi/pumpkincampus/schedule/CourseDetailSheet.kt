package com.pgigi.pumpkincampus.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moriafly.salt.ui.Icon
import com.moriafly.salt.ui.Item
import com.moriafly.salt.ui.ItemArrowType
import com.moriafly.salt.ui.ItemOuterSpacer
import com.moriafly.salt.ui.ItemOuterTextButton
import com.moriafly.salt.ui.ItemTip
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.Text
import com.moriafly.salt.ui.UnstableSaltUiApi
import com.moriafly.salt.ui.icons.ArrowBack
import com.moriafly.salt.ui.icons.Back
import com.moriafly.salt.ui.icons.ChevronRight
import com.moriafly.salt.ui.icons.Check
import com.moriafly.salt.ui.icons.SaltIcons
import com.moriafly.salt.ui.icons.Success
import com.pgigi.pumpkincampus.icons.MaterialIcons
import com.pgigi.pumpkincampus.icons.material.Book
import com.pgigi.pumpkincampus.icons.material.CalendarMonth
import com.pgigi.pumpkincampus.icons.material.KeyboardArrowDown
import com.pgigi.pumpkincampus.icons.material.MeetingRoom
import com.pgigi.pumpkincampus.icons.material.Person
import com.pgigi.pumpkincampus.icons.material.Schedule
import com.pgigi.pumpkincampus.models.Course
import com.pgigi.pumpkincampus.models.LessonTime
import com.pgigi.pumpkincampus.models.isFixedCourse

/** 底部弹层面板顶部圆角（Material 3 底部弹层规格 28dp）。 */
internal val CourseSheetCornerRadius = 28.dp

private val DetailDayLabels = listOf("日", "一", "二", "三", "四", "五", "六")

/** 「第 N 周 星期几」里的星期部分（dayIndex 0=周日 ... 6=周六）；无效时为空串。 */
private fun weekDaySuffix(course: Course): String =
    if (course.dayIndex in 0..6) " 星期${DetailDayLabels[course.dayIndex]}" else ""

/**
 * 单课程详情底部弹层的内容（标题行 + 图标信息行）。
 *
 * 布局参考 Pumpkin-Toolkit `SchedulePager` 中的 WindowBottomSheet「课程详细」：
 * 标题 + 一组「图标 + 文本」行（课程、教室、教师、周次）。
 * 图标使用 Salt UI 已有的 `SaltIcons`，不引入额外图标库；
 * 顶部拖拽指示条由 Material 3 `BottomSheetDefaults.DragHandle()` 提供。
 *
 * 冲突课程不再用 Dialog 选择：标题「课程详细」右侧是**横向滑动容器**，
 * 内含每个冲突课程名的 Chip，选中的 Chip 高亮，点击切换展示的课程。
 *
 * 容器见 [CourseBottomSheet]（Material 3 `ModalBottomSheet`）。
 *
 * @param course 当前选中的课程；null 表示尚未选择，只展示提示标题
 * @param onClose 关闭弹层回调
 * @param onEdit 非空时在详情底部显示「编辑课程」按钮
 * @param conflictCourses 冲突课程组（多于 1 门时在标题右侧显示 Chip 选择）
 * @param selectedConflictIndex 当前选中的冲突课程下标（对应高亮 Chip）
 * @param onSelectConflict 点击某个冲突课程 Chip 时回调下标
 */
@OptIn(UnstableSaltUiApi::class)
@Composable
internal fun CourseDetailSheetContent(
    course: Course?,
    onClose: () -> Unit,
    onEdit: (() -> Unit)? = null,
    lessonTimes: List<LessonTime> = emptyList(),
    conflictCourses: List<Course> = emptyList(),
    selectedConflictIndex: Int = 0,
    onSelectConflict: (Int) -> Unit = {}
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // 标题行
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (course == null) "点击课表中的课程查看详情" else "课程详细",
                fontSize = SaltTheme.textStyles.main.fontSize,
                fontWeight = FontWeight.Bold
            )

            // 冲突课程选择：紧跟在「课程详细」后面（10dp），容器无宽度上限、
            // 顶满到行两边（左右各留一点 padding），超出部分在容器内横向滑动
            if (conflictCourses.size > 1) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 10.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    conflictCourses.forEachIndexed { index, conflictCourse ->
                        ConflictChip(
                            name = conflictCourse.name,
                            selected = index == selectedConflictIndex,
                            onClick = { onSelectConflict(index) }
                        )
                    }
                }
            } else {
                // 无冲突：弹性空白，保持标题在左、关闭按钮在右
                Spacer(modifier = Modifier.weight(1f))
            }

            if (course != null) {
                Icon(
                    imageVector = MaterialIcons.KeyboardArrowDown,
                    contentDescription = "收起",
                    modifier = Modifier
                        .size(20.dp)
                        .clickable { onClose() },
                    tint = SaltTheme.colors.subText
                )
            }
        }

        /*Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(SaltTheme.colors.stroke)
        )*/

        if (course == null) {
            EmptySheetHint()
        } else {
            SheetRow(
                icon = MaterialIcons.Book,
                text = course.name.ifBlank { "未命名课程" }
            )
            if (course.classroom.isNotBlank()) {
                SheetRow(
                    icon = MaterialIcons.MeetingRoom,
                    text = course.classroom
                )
            }
            if (course.teacher.isNotBlank()) {
                SheetRow(
                    icon = MaterialIcons.Person,
                    text = course.teacher
                )
            }
            SheetRow(
                icon = MaterialIcons.CalendarMonth,
                // 第几周 + 星期几
                text = if (course.isFixedCourse) "固定课程"
                else "第 ${formatWeekIndices(course.weekIndices)} 周${weekDaySuffix(course)}"
            )
            if (course.isFixedCourse ||
                (course.dayIndex in 0..6 && course.lessonStartIndex >= 0)
            ) {
                SheetRow(
                    icon = MaterialIcons.Schedule,
                    // 第几节 几点-几点（星期几已并到上一行的周次里）
                    text = describeCourseTime(course, lessonTimes)
                )
            }

            // 编辑入口：进入与「添加课程」一致的表单（含删除）
            onEdit?.let {
                ItemOuterSpacer()
                ItemOuterTextButton(
                    onClick = it,
                    text = "编辑课程"
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun EmptySheetHint() {
    ItemTip(text = "点击课表中的课程查看详情")
}

/** 冲突课程 Chip：胶囊形，选中用高亮色（蓝底亮字），未选中用弱背景 + 次要文字。 */
@Composable
private fun ConflictChip(
    name: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(
                if (selected) SaltTheme.colors.highlight
                else SaltTheme.colors.subBackground
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 3.dp)
    ) {
        Text(
            text = name.ifBlank { "未命名课程" },
            fontSize = SaltTheme.textStyles.sub.fontSize,
            color = if (selected) SaltTheme.colors.onHighlight
            else SaltTheme.colors.subText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 80.dp)
        )
    }
}

@Composable
private fun SheetRow(
    icon: ImageVector,
    text: String
) {
    Item(
        onClick = {},
        text = text,
        iconPainter = rememberVectorPainter(icon),
        iconColor = SaltTheme.colors.highlight,
        arrowType = ItemArrowType.None
    )
}
