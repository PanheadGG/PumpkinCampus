package com.pgigi.pumpkincampus.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.calculateTargetValue
import androidx.compose.animation.core.spring
import androidx.compose.animation.splineBasedDecay
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.Text
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * 滚轮选择器（NumberPicker 风格）。
 *
 * - 上下拖动切换项，松手按惯性滑动并**吸附**到整数项；
 * - 中间项高亮放大，上下相邻项按距离淡出（视觉上是「滚轮」而不是列表）；
 * - 点某一项可直接滚到它。
 *
 * 纯 Compose 手势 + `Animatable` 实现（不依赖 `LazyColumn` 的 contentPadding 语义），
 * 因此 Android / iOS 的表现完全一致，吸附位置也完全可控。
 *
 * @param items 全部候选项文本（顺序即上下顺序）
 * @param selectedIndex 当前选中项下标（超出范围会被夹到合法区间）
 * @param onSelectedIndexChange 选中项变化回调（只在**整数项**变化时触发，拖动过程中不回调）
 * @param visibleCount 可见项数（建议奇数，中间那项即选中项）
 * @param itemHeight 单项高度（滚轮总高 = `itemHeight * visibleCount`）
 * @param contentDescription 无障碍描述（如「小时」「分钟」）
 */
@Composable
fun WheelPicker(
    items: List<String>,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    visibleCount: Int = 5,
    itemHeight: Dp = 34.dp,
    contentDescription: String? = null
) {
    if (items.isEmpty()) return

    val lastIndex = items.lastIndex
    val sideCount = (visibleCount - 1) / 2
    val density = LocalDensity.current
    val itemHeightPx = with(density) { itemHeight.toPx() }
    val position = remember { Animatable(selectedIndex.coerceIn(0, lastIndex).toFloat()) }
    val scope = rememberCoroutineScope()
    val currentOnChange by rememberUpdatedState(onSelectedIndexChange)

    // 外部值变化（例如小时改了导致分钟候选区间变化）：滚到新的选中项
    LaunchedEffect(selectedIndex, lastIndex) {
        val target = selectedIndex.coerceIn(0, lastIndex).toFloat()
        if (abs(position.value - target) > 0.01f) {
            position.animateTo(target, spring(dampingRatio = 0.9f, stiffness = 600f))
        }
    }

    // 位置 → 回调：只在跨过整数项时通知一次，避免拖动过程中疯狂回调
    LaunchedEffect(position, lastIndex) {
        snapshotFlow { position.value.roundToInt().coerceIn(0, lastIndex) }
            .distinctUntilChanged()
            .collect { currentOnChange(it) }
    }

    val center = position.value.roundToInt()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(itemHeight * visibleCount)
            .clipToBounds()
            .semantics {
                if (contentDescription != null) this.contentDescription = contentDescription
                stateDescription = items[position.value.roundToInt().coerceIn(0, lastIndex)]
            }
            .draggable(
                orientation = Orientation.Vertical,
                state = rememberDraggableState { delta ->
                    scope.launch {
                        position.snapTo(
                            (position.value - delta / itemHeightPx).coerceIn(0f, lastIndex.toFloat())
                        )
                    }
                },
                onDragStopped = { velocity ->
                    // 惯性投影 → 吸附到最近的整数项
                    val decay = splineBasedDecay<Float>(density)
                    val projected = decay.calculateTargetValue(position.value, -velocity / itemHeightPx)
                    position.animateTo(
                        projected.roundToInt().coerceIn(0, lastIndex).toFloat(),
                        spring(dampingRatio = 0.85f, stiffness = 400f)
                    )
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        // 中间选中带（浅色底 + 上下细线，和系统 NumberPicker 的观感接近）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(itemHeight)
                .padding(horizontal = 2.dp)
                .background(SaltTheme.colors.subBackground, RoundedCornerShape(10.dp))
        )

        for (index in (center - sideCount)..(center + sideCount)) {
            if (index < 0 || index > lastIndex) continue
            val distance = abs(index - position.value)
            val selected = distance < 0.5f
            Text(
                text = items[index],
                fontSize = SaltTheme.textStyles.main.fontSize,
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                color = if (selected) SaltTheme.colors.highlight else SaltTheme.colors.subText,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = itemHeight * (index - position.value))
                    .graphicsLayer {
                        alpha = (1f - distance * 0.32f).coerceIn(0.15f, 1f)
                        val scale = (1f - distance * 0.08f).coerceIn(0.7f, 1f)
                        scaleX = scale
                        scaleY = scale
                    }
                    .clickable {
                        scope.launch {
                            position.animateTo(
                                index.toFloat(),
                                spring(dampingRatio = 0.9f, stiffness = 600f)
                            )
                        }
                    }
            )
        }
    }
}

/**
 * 滚轮 + 下方小标题（如「小时」「分钟」），方便横向并排两个滚轮。
 *
 * @param label 滚轮下方的小字标题
 */
@Composable
fun WheelPickerColumn(
    items: List<String>,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    visibleCount: Int = 5,
    itemHeight: Dp = 34.dp
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        WheelPicker(
            items = items,
            selectedIndex = selectedIndex,
            onSelectedIndexChange = onSelectedIndexChange,
            visibleCount = visibleCount,
            itemHeight = itemHeight,
            contentDescription = label
        )
        Text(
            text = label,
            fontSize = 11.sp,
            color = SaltTheme.colors.subText,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
