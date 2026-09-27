package com.pgigi.pumpkincampus.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.moriafly.salt.ui.ItemDivider
import com.moriafly.salt.ui.ItemEdit
import com.moriafly.salt.ui.ItemOuterSpacer
import com.moriafly.salt.ui.ItemOuterTitle
import com.moriafly.salt.ui.RoundedColumn
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.Text
import com.moriafly.salt.ui.UnstableSaltUiApi
import com.moriafly.salt.ui.innerPadding
import com.moriafly.salt.ui.outerPadding
import com.pgigi.pumpkincampus.DemoWeekCount
import com.pgigi.pumpkincampus.models.Course
import com.pgigi.pumpkincampus.models.isFixedCourse
import kotlin.math.roundToInt

private val FormDayLabels = listOf("日", "一", "二", "三", "四", "五", "六")

/**
 * 课程表单状态：添加（[initial] 为 null）与编辑（[initial] 为原课程，预填）共用。
 *
 * 普通课程字段：课程名称、教师、教室、星期（方块单选）、周数（方块多选，
 * 选中蓝色/未选灰色）、节数（两端范围滑块，左头开始、右头结束，结束不能小于开始）。
 *
 * [weekCount] 学期周数、[lessonCount] 一天课程节数（来自设置页的课表数据）。
 *
 * 固定课程（[isFixed]）：按绝对日期 + 钟表时间，忽略星期/周数/节数，
 * 不随教学周与作息时间表变化。
 */
internal class CourseFormState(
    initial: Course? = null,
    val weekCount: Int = DemoWeekCount,
    val lessonCount: Int = DefaultLessonCount
) {

    var name by mutableStateOf(initial?.name ?: "")
    var teacher by mutableStateOf(initial?.teacher ?: "")
    var classroom by mutableStateOf(initial?.classroom ?: "")

    /** 星期 0=周日 ... 6=周六，默认今天。 */
    var dayIndex by mutableStateOf(
        initial?.dayIndex?.takeIf { it in 0..6 } ?: dayIndexOf(currentLocalDate())
    )

    /** 1 基周数集合，默认全选。 */
    var selectedWeeks by mutableStateOf(
        initial?.weekIndices
            ?.map { it + 1 }
            ?.filter { it in 1..weekCount }
            ?.toSet()
            ?.takeIf { it.isNotEmpty() }
            ?: (1..weekCount).toSet()
    )

    /** 1 基开始节次 / 结束节次（结束 >= 开始）。 */
    var startLesson by mutableStateOf(1)
    var endLesson by mutableStateOf(2)

    // —— 固定课程（绝对日期 + 钟表时间，不随教学周/作息时间表变化）——

    /** 是否为固定课程。 */
    var isFixed by mutableStateOf(initial?.isFixedCourse ?: false)

    /** 固定日期文本 `yyyy-MM-dd`，默认今天。 */
    var fixedDateText by mutableStateOf(
        initial?.fixedDate ?: currentLocalDate().toString()
    )

    /** 开始时间文本 `HH:mm`，默认 14:30。 */
    var fixedTimeText by mutableStateOf(
        initial?.fixedStartMinute?.let { formatClockMinutes(it) } ?: "14:30"
    )

    /** 持续分钟数，默认 90。 */
    var fixedDuration by mutableStateOf(initial?.fixedDurationMinutes ?: 90)

    var errorText by mutableStateOf<String?>(null)

    init {
        val startIndex = initial?.lessonStartIndex?.takeIf { it >= 0 } ?: 0
        startLesson = (startIndex + 1).coerceIn(1, lessonCount)
        endLesson = (startIndex + (initial?.lessonCount ?: 2))
            .coerceIn(1, lessonCount)
            .coerceAtLeast(startLesson)
    }

    /** 校验并生成课程；不通过时写入 [errorText] 并返回 null。 */
    fun buildCourse(): Course? {
        val trimmed = name.trim()

        // 固定课程：按绝对日期 + 钟表时间生成，忽略星期/周数/节数
        if (isFixed) {
            val date = parseFixedDate(fixedDateText)
            val startMinute = parseClockMinutes(fixedTimeText)
            val fixedError = when {
                trimmed.isEmpty() -> "请输入课程名称"
                date == null -> "日期格式应为 2026-10-01"
                startMinute == null -> "时间格式应为 14:30"
                fixedDuration !in 5..1440 -> "持续时间应为 5-1440 分钟"
                else -> null
            }
            errorText = fixedError
            if (fixedError != null || date == null || startMinute == null) return null
            return Course(
                name = trimmed,
                classroom = classroom.trim(),
                teacher = teacher.trim(),
                weekIndices = emptyList(),
                dayIndex = dayIndexOf(date),
                lessonStartIndex = -1,
                lessonCount = 0,
                fixedDate = date.toString(),
                fixedStartMinute = startMinute,
                fixedDurationMinutes = fixedDuration
            )
        }

        val error = when {
            trimmed.isEmpty() -> "请输入课程名称"
            selectedWeeks.isEmpty() -> "请选择上课周数"
            else -> null
        }
        errorText = error
        if (error != null) return null
        return Course(
            name = trimmed,
            classroom = classroom.trim(),
            teacher = teacher.trim(),
            weekIndices = selectedWeeks.map { it - 1 }.sorted(),
            dayIndex = dayIndex,
            lessonStartIndex = startLesson - 1,
            lessonCount = endLesson - startLesson + 1
        )
    }
}

@Composable
internal fun rememberCourseFormState(
    initial: Course? = null,
    weekCount: Int = DemoWeekCount,
    lessonCount: Int = DefaultLessonCount
): CourseFormState =
    remember(initial, weekCount, lessonCount) {
        CourseFormState(initial, weekCount, lessonCount)
    }

/**
 * 课程表单字段（不含标题与底部按钮，由 [AddCourseSheet] / [EditCourseSheet] 提供）。
 *
 * 其中节数为**两端范围滑块**（Material 3 `RangeSlider`）：一条轨道上两个滑块头，
 * 左头 = 开始节次、右头 = 结束节次，拖动时两头不会交叉（结束不会小于开始）。
 */
@OptIn(UnstableSaltUiApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun CourseForm(state: CourseFormState) {
    // 课程名称 / 教师 / 教室
    RoundedColumn {
        ItemEdit(text = state.name, onChange = { state.name = it }, hint = "课程名称")
        ItemDivider()
        ItemEdit(text = state.teacher, onChange = { state.teacher = it }, hint = "教师")
        ItemDivider()
        ItemEdit(text = state.classroom, onChange = { state.classroom = it }, hint = "教室")
    }

    // 「课程类型」切换（普通 ↔ 固定）暂时隐藏：功能完整保留，表单默认且始终按
    // 周次节次（普通）模式创建课程。取消下面这段注释即可恢复切换入口。
    //（注：编辑已存在的固定课程时，下方 if (state.isFixed) 分支仍会显示日期/时间字段，
    //  保证功能可用；若连这也想隐藏，可把该分支一并注释、只保留 RegularCourseFields。）
    /*
    ItemOuterTitle("课程类型")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .outerPadding(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (state.isFixed) "固定课程" else "普通课程",
                fontSize = SaltTheme.textStyles.main.fontSize,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = if (state.isFixed) "按指定日期与时间显示，不随教学周与作息表变化"
                else "按周数重复，按课次显示",
                fontSize = SaltTheme.textStyles.sub.fontSize,
                color = SaltTheme.colors.subText
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        // 方块开关（与星期/周数方块同一套视觉）
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(
                    if (state.isFixed) SaltTheme.colors.highlight
                    else SaltTheme.colors.subBackground
                )
                .clickable { state.isFixed = !state.isFixed }
                .padding(horizontal = 16.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (state.isFixed) "固定" else "普通",
                fontSize = SaltTheme.textStyles.sub.fontSize,
                fontWeight = FontWeight.Medium,
                color = if (state.isFixed) SaltTheme.colors.onHighlight
                else SaltTheme.colors.text
            )
        }
    }
    ItemOuterSpacer()
    */

    if (state.isFixed) {
        FixedCourseFields(state)
    } else {
        RegularCourseFields(state)
    }

    state.errorText?.let { message ->
        Text(
            text = message,
            fontSize = SaltTheme.textStyles.sub.fontSize,
            color = SaltTheme.colors.error,
            modifier = Modifier.padding(
                horizontal = SaltTheme.dimens.padding,
                vertical = 6.dp
            )
        )
    }
}

/** 普通课程字段：星期（方块单选）/ 周数（方块多选）/ 节数（两端范围滑块）。 */
@OptIn(UnstableSaltUiApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun RegularCourseFields(state: CourseFormState) {
    // 星期：方块单选
    ItemOuterTitle("星期")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .outerPadding(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        FormDayLabels.forEachIndexed { index, label ->
            val selected = index == state.dayIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (selected) SaltTheme.colors.highlight
                        else SaltTheme.colors.subBackground
                    )
                    .clickable { state.dayIndex = index },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    fontSize = SaltTheme.textStyles.main.fontSize,
                    fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                    color = if (selected) SaltTheme.colors.onHighlight
                    else SaltTheme.colors.text
                )
            }
        }
    }
    ItemOuterSpacer()

    // 周数：方块多选（选中蓝色 / 未选灰色），右侧带全选、清空
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = SaltTheme.dimens.padding * 2,
                top = SaltTheme.dimens.padding * 0.5f + SaltTheme.dimens.subPadding,
                end = SaltTheme.dimens.padding * 2,
                bottom = SaltTheme.dimens.subPadding
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "周数",
            style = SaltTheme.textStyles.sub,
            color = SaltTheme.colors.subText,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "全选",
            fontSize = SaltTheme.textStyles.sub.fontSize,
            color = SaltTheme.colors.highlight,
            modifier = Modifier.clickable {
                state.selectedWeeks = (1..state.weekCount).toSet()
            }
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = "清空",
            fontSize = SaltTheme.textStyles.sub.fontSize,
            color = SaltTheme.colors.highlight,
            modifier = Modifier.clickable { state.selectedWeeks = emptySet() }
        )
    }
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .outerPadding(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        (1..state.weekCount).forEach { week ->
            val selected = week in state.selectedWeeks
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (selected) SaltTheme.colors.highlight
                        else SaltTheme.colors.subBackground
                    )
                    .clickable {
                        state.selectedWeeks =
                            if (selected) state.selectedWeeks - week
                            else state.selectedWeeks + week
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = week.toString(),
                    fontSize = SaltTheme.textStyles.main.fontSize,
                    fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                    color = if (selected) SaltTheme.colors.onHighlight
                    else SaltTheme.colors.text
                )
            }
        }
    }
    ItemOuterSpacer()

    // 节数：单个范围滑块（两端滑块）——开始与结束并在一起，两个滑块头分踞两端
    ItemOuterTitle("节数")
    RoundedColumn {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .innerPadding()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "选择节次",
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "第${state.startLesson}节 ~ 第${state.endLesson}节",
                    color = SaltTheme.colors.subText
                )
            }
            RangeSlider(
                value = state.startLesson.toFloat()..state.endLesson.toFloat(),
                onValueChange = { range ->
                    val start = range.start.roundToInt().coerceIn(1, state.lessonCount)
                    val end = range.endInclusive.roundToInt().coerceIn(1, state.lessonCount)
                    state.startLesson = start
                    // 结束不能小于开始（RangeSlider 本身不允许交叉，这里再兜底一次）
                    state.endLesson = end.coerceAtLeast(start)
                },
                valueRange = 1f..state.lessonCount.toFloat(),
                steps = (state.lessonCount - 2).coerceAtLeast(0),
                colors = SliderDefaults.colors(
                    thumbColor = SaltTheme.colors.highlight,
                    activeTrackColor = SaltTheme.colors.highlight,
                    inactiveTrackColor = SaltTheme.colors.stroke,
                    activeTickColor = SaltTheme.colors.highlight,
                    inactiveTickColor = SaltTheme.colors.stroke
                )
            )
        }
    }
}

/**
 * 固定课程字段：日期 / 开始时间（文本输入）+ 持续时长（滑块）。
 *
 * 固定课程不显示星期/周数/节数——它按绝对日期与钟表时间定位，
 * 不随教学周与作息时间表变化。
 */
@OptIn(UnstableSaltUiApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun FixedCourseFields(state: CourseFormState) {
    RoundedColumn {
        ItemEdit(
            text = state.fixedDateText,
            onChange = { state.fixedDateText = it },
            hint = "日期（2026-10-01）"
        )
        ItemDivider()
        ItemEdit(
            text = state.fixedTimeText,
            onChange = { state.fixedTimeText = it },
            hint = "开始时间（14:30）"
        )
        ItemDivider()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .innerPadding(vertical = false)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "持续时长",
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${state.fixedDuration} 分钟",
                    color = SaltTheme.colors.subText
                )
            }
            Slider(
                value = state.fixedDuration.toFloat(),
                onValueChange = { state.fixedDuration = it.roundToInt() },
                valueRange = 15f..480f,
                steps = 30,
                colors = SliderDefaults.colors(
                    thumbColor = SaltTheme.colors.highlight,
                    activeTrackColor = SaltTheme.colors.highlight,
                    inactiveTrackColor = SaltTheme.colors.stroke,
                    activeTickColor = SaltTheme.colors.highlight,
                    inactiveTickColor = SaltTheme.colors.stroke
                )
            )
        }
    }
}
