package com.pgigi.pumpkincampus.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.moriafly.salt.ui.Icon
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.Text
import com.moriafly.salt.ui.icons.ChevronRight
import com.moriafly.salt.ui.icons.SaltIcons
import com.pgigi.pumpkincampus.pages.sceneCardColor
import com.pgigi.pumpkincampus.schedule.currentLocalDate
import com.pgigi.pumpkincampus.schedule.dayIndexOf
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.number
import kotlinx.datetime.plus

private val DatePickerWeekLabels = listOf("日", "一", "二", "三", "四", "五", "六")

/**
 * 日期选择器：移植自 Pumpkin-Toolkit `components/miuix/DatePicker.kt` 并改造。
 *
 * 与原版的差异：
 * - **去 miuix**：改用 Salt UI 主题（[SaltTheme]）与 `SaltIcons.ChevronRight`（左箭头 = 旋转 180°）
 * - **新增 [firstDayOfWeek]**：可自定义「一周的第一天」（0=周日 ... 6=周六），
 *   星期表头顺序与日历每行的起始列都会按该天对齐
 * - 选中态直接以 [selected] 日期判断（不再依赖单独的年月日状态）
 *
 * @param selected 当前选中日期
 * @param onSelected 点击日期回调（点到上/下月的日期会同时翻页）
 * @param firstDayOfWeek 一周的第一天（0=周日、1=周一 ... 6=周六）
 * @param start 可选范围起
 * @param end 可选范围止
 */
@Composable
fun AppDatePicker(
    selected: LocalDate,
    onSelected: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    firstDayOfWeek: Int = 0,
    start: LocalDate = LocalDate(1970, 1, 1),
    end: LocalDate = LocalDate(2099, 12, 31)
) {
    val today = remember { currentLocalDate() }
    val firstDay = ((firstDayOfWeek % 7) + 7) % 7

    var displayYear by remember { mutableIntStateOf(selected.year) }
    var displayMonth by remember { mutableIntStateOf(selected.month.number) }

    val firstOfMonth = remember(displayYear, displayMonth) {
        LocalDate(displayYear, displayMonth, 1)
    }
    val nextMonthFirst = remember(displayYear, displayMonth) {
        if (displayMonth == 12) LocalDate(displayYear + 1, 1, 1)
        else LocalDate(displayYear, displayMonth + 1, 1)
    }
    val prevMonthFirst = remember(displayYear, displayMonth) {
        if (displayMonth == 1) LocalDate(displayYear - 1, 12, 1)
        else LocalDate(displayYear, displayMonth - 1, 1)
    }
    val daysInMonth = remember(displayYear, displayMonth) {
        (nextMonthFirst.toEpochDays() - firstOfMonth.toEpochDays()).toInt()
    }
    val daysInPrevMonth = remember(displayYear, displayMonth) {
        (firstOfMonth.toEpochDays() - prevMonthFirst.toEpochDays()).toInt()
    }
    // 本月 1 号前面要补几个上月的格子（按 firstDayOfWeek 对齐）
    val firstDayOffset = remember(displayYear, displayMonth, firstDay) {
        (dayIndexOf(firstOfMonth) - firstDay + 7) % 7
    }

    val cells = remember(displayYear, displayMonth, firstDay) {
        buildList<Pair<LocalDate, Boolean>> {
            for (i in 0 until firstDayOffset) {
                add(prevMonthFirst.plus(daysInPrevMonth - firstDayOffset + 1 + i, DateTimeUnit.DAY) to false)
            }
            for (day in 1..daysInMonth) {
                add(firstOfMonth.plus(day - 1, DateTimeUnit.DAY) to true)
            }
            var next = 0
            while (size < 42) {
                add(nextMonthFirst.plus(next, DateTimeUnit.DAY) to false)
                next++
            }
        }
    }

    fun goto(date: LocalDate) {
        displayYear = date.year
        displayMonth = date.month.number
    }

    Column(modifier = modifier.padding(horizontal = 4.dp)) {
        // 标题栏：上一月 / 年月 / 下一月
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickable {
                        goto(if (displayMonth == 1) LocalDate(displayYear - 1, 12, 1)
                        else LocalDate(displayYear, displayMonth - 1, 1))
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = SaltIcons.ChevronRight,
                    contentDescription = "上一月",
                    modifier = Modifier
                        .size(18.dp)
                        .graphicsLayer { rotationZ = 180f },
                    tint = SaltTheme.colors.text
                )
            }
            Text(
                text = "$displayYear 年 $displayMonth 月",
                fontSize = SaltTheme.textStyles.main.fontSize,
                fontWeight = FontWeight.Medium,
                color = SaltTheme.colors.text,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickable {
                        goto(if (displayMonth == 12) LocalDate(displayYear + 1, 1, 1)
                        else LocalDate(displayYear, displayMonth + 1, 1))
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = SaltIcons.ChevronRight,
                    contentDescription = "下一月",
                    modifier = Modifier.size(18.dp),
                    tint = SaltTheme.colors.text
                )
            }
        }

        // 星期表头：从 firstDayOfWeek 开始轮转
        Row(modifier = Modifier.fillMaxWidth()) {
            for (i in 0 until 7) {
                Text(
                    text = DatePickerWeekLabels[(firstDay + i) % 7],
                    fontSize = SaltTheme.textStyles.sub.fontSize,
                    color = SaltTheme.colors.subText,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // 日期网格：6 行 × 7 列
        cells.chunked(7).forEach { week ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                week.forEach { (date, inMonth) ->
                    val isSelected = date == selected
                    val isToday = date == today
                    val enabled = date >= start && date <= end
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                            .padding(vertical = 2.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    isSelected -> SaltTheme.colors.highlight
                                    else -> sceneCardColor()
                                }
                            )
                            .clickable(enabled = enabled) {
                                if (date != selected) onSelected(date)
                                goto(date)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = date.day.toString(),
                            fontSize = SaltTheme.textStyles.main.fontSize,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = when {
                                !enabled -> SaltTheme.colors.subText.copy(alpha = 0.4f)
                                isSelected -> SaltTheme.colors.onHighlight
                                isToday -> SaltTheme.colors.highlight
                                inMonth -> SaltTheme.colors.text
                                else -> SaltTheme.colors.subText
                            },
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
    }
}
