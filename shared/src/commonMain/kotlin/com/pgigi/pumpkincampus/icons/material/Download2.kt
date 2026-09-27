package com.pgigi.pumpkincampus.icons.material

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import com.pgigi.pumpkincampus.icons.MaterialIcons

@Suppress("CheckReturnValue")
val MaterialIcons.Download2: ImageVector
  get() {
    if (_download_2 != null) {
      return _download_2!!
    }
    _download_2 =
      ImageVector.Builder(
          name = "download_2",
          defaultWidth = 24.dp,
          defaultHeight = 24.dp,
          viewportWidth = 24f,
          viewportHeight = 24f,
        )
        .apply {
          path(
            fill = SolidColor(Color.Black),
            fillAlpha = 1f,
            stroke = null,
            strokeAlpha = 1f,
            strokeLineWidth = 1f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Bevel,
            strokeLineMiter = 1f,
            pathFillType = PathFillType.Companion.NonZero,
          ) {
            moveTo(4f, 22f)
            verticalLineTo(20f)
            horizontalLineTo(20f)
            verticalLineToRelative(2f)
            horizontalLineTo(4f)
            close()
            moveToRelative(8f, -4f)
            lineTo(5f, 9f)
            horizontalLineTo(9f)
            verticalLineTo(2f)
            horizontalLineToRelative(6f)
            verticalLineTo(9f)
            horizontalLineToRelative(4f)
            lineToRelative(-7f, 9f)
            close()
            moveToRelative(0f, -3.25f)
            lineTo(14.9f, 11f)
            horizontalLineTo(13f)
            verticalLineTo(4f)
            horizontalLineTo(11f)
            verticalLineToRelative(7f)
            horizontalLineTo(9.1f)
            lineTo(12f, 14.75f)
            close()
            moveTo(12f, 11f)
            close()
          }
        }
        .build()
    return _download_2!!
  }

private var _download_2: ImageVector? = null
