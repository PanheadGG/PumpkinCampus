package com.pgigi.pumpkincampus.components

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.Painter

/**
 * 底部导航栏使用的两个自绘描边图标（24×24 单位网格，绘制时按目标尺寸等比缩放）。
 *
 * Salt UI 内置的 `SaltIcons` 只有返回/勾选/箭头等通用图标，没有日历与表格语义，
 * 因此这里用 Canvas 直接绘制，不引入额外图标库。
 * 图标以黑色绘制，最终颜色由 [com.moriafly.salt.ui.Icon] 的 `tint` 覆盖，
 * 会随选中状态在 highlight / subText 之间切换。
 */

private const val IconUnits = 24f
private const val IconStroke = 1.8f

/** 日程：日历（主体 + 两个挂环 + 日期分隔线）。 */
internal val AgendaIcon: Painter = GridStrokeIcon { s ->
    val ink = Color.Black
    drawRoundRect(
        color = ink,
        topLeft = Offset(3.5f * s, 5.5f * s),
        size = Size(17f * s, 15f * s),
        cornerRadius = CornerRadius(2.5f * s, 2.5f * s),
        style = Stroke(width = IconStroke * s)
    )
    drawLine(
        color = ink,
        start = Offset(8f * s, 2.5f * s),
        end = Offset(8f * s, 7f * s),
        strokeWidth = IconStroke * s,
        cap = StrokeCap.Round
    )
    drawLine(
        color = ink,
        start = Offset(16f * s, 2.5f * s),
        end = Offset(16f * s, 7f * s),
        strokeWidth = IconStroke * s,
        cap = StrokeCap.Round
    )
    drawLine(
        color = ink,
        start = Offset(3.5f * s, 10f * s),
        end = Offset(20.5f * s, 10f * s),
        strokeWidth = IconStroke * s,
        cap = StrokeCap.Round
    )
}

/** 课表：表格（外框 + 两横一竖分隔线）。 */
internal val TimetableIcon: Painter = GridStrokeIcon { s ->
    val ink = Color.Black
    drawRoundRect(
        color = ink,
        topLeft = Offset(3.5f * s, 4.5f * s),
        size = Size(17f * s, 15f * s),
        cornerRadius = CornerRadius(2.5f * s, 2.5f * s),
        style = Stroke(width = IconStroke * s)
    )
    drawLine(
        color = ink,
        start = Offset(3.5f * s, 9.5f * s),
        end = Offset(20.5f * s, 9.5f * s),
        strokeWidth = IconStroke * s,
        cap = StrokeCap.Round
    )
    drawLine(
        color = ink,
        start = Offset(3.5f * s, 14.5f * s),
        end = Offset(20.5f * s, 14.5f * s),
        strokeWidth = IconStroke * s,
        cap = StrokeCap.Round
    )
    drawLine(
        color = ink,
        start = Offset(12f * s, 4.5f * s),
        end = Offset(12f * s, 19.5f * s),
        strokeWidth = IconStroke * s,
        cap = StrokeCap.Round
    )
}

/**
 * 以 24 单位网格绘制的描边图标。
 *
 * @param draw 真正的绘制逻辑，参数 `s` 为「1 单位 = 多少像素」的缩放系数
 */
private class GridStrokeIcon(
    private val draw: DrawScope.(s: Float) -> Unit
) : Painter() {

    override val intrinsicSize: Size = Size(IconUnits, IconUnits)

    override fun DrawScope.onDraw() {
        draw(this, size.width / IconUnits)
    }
}

/** 添加（加号），用于课表 TopBar 的「添加课程」按钮。 */
internal val AddIcon: Painter = GridStrokeIcon { s ->
    val ink = Color.Black
    drawLine(
        color = ink,
        start = Offset(12f * s, 4f * s),
        end = Offset(12f * s, 20f * s),
        strokeWidth = IconStroke * s,
        cap = StrokeCap.Round
    )
    drawLine(
        color = ink,
        start = Offset(4f * s, 12f * s),
        end = Offset(20f * s, 12f * s),
        strokeWidth = IconStroke * s,
        cap = StrokeCap.Round
    )
}

/** 设置：滑杆（两根横线 + 圆形旋钮），用于底部导航的「设置」。 */
internal val SettingsIcon: Painter = GridStrokeIcon { s ->
    val ink = Color.Black
    drawLine(
        color = ink,
        start = Offset(3.5f * s, 9f * s),
        end = Offset(20.5f * s, 9f * s),
        strokeWidth = IconStroke * s,
        cap = StrokeCap.Round
    )
    drawLine(
        color = ink,
        start = Offset(3.5f * s, 15f * s),
        end = Offset(20.5f * s, 15f * s),
        strokeWidth = IconStroke * s,
        cap = StrokeCap.Round
    )
    drawCircle(
        color = ink,
        radius = 2.8f * s,
        center = Offset(9f * s, 9f * s),
        style = Stroke(width = IconStroke * s)
    )
    drawCircle(
        color = ink,
        radius = 2.8f * s,
        center = Offset(15f * s, 15f * s),
        style = Stroke(width = IconStroke * s)
    )
}
