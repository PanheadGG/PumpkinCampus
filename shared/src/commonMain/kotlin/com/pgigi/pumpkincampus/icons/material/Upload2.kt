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
val MaterialIcons.Upload2: ImageVector
  get() {
    if (_upload_2 != null) {
      return _upload_2!!
    }
    _upload_2 =
      ImageVector.Builder(
          name = "Upload2",
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
            moveTo(9f, 18f)
            verticalLineTo(11f)
            horizontalLineTo(5f)
            lineTo(12f, 2f)
            lineToRelative(7f, 9f)
            horizontalLineTo(15f)
            verticalLineToRelative(7f)
            horizontalLineTo(9f)
            close()
            moveToRelative(2f, -2f)
            horizontalLineToRelative(2f)
            verticalLineTo(9f)
            horizontalLineToRelative(1.9f)
            lineTo(12f, 5.25f)
            lineTo(9.1f, 9f)
            horizontalLineTo(11f)
            verticalLineToRelative(7f)
            close()
            moveTo(12f, 9f)
            close()
          }
        }
        .build()
    return _upload_2!!
  }

private var _upload_2: ImageVector? = null
