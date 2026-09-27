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
val MaterialIcons.MeetingRoom: ImageVector
  get() {
    if (_meeting_room != null) {
      return _meeting_room!!
    }
    _meeting_room =
      ImageVector.Builder(
          name = "MeetingRoom",
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
            moveTo(3f, 21f)
            verticalLineTo(19f)
            horizontalLineTo(5f)
            verticalLineTo(3f)
            horizontalLineTo(15f)
            verticalLineTo(4f)
            horizontalLineToRelative(4f)
            verticalLineTo(19f)
            horizontalLineToRelative(2f)
            verticalLineToRelative(2f)
            horizontalLineTo(17f)
            verticalLineTo(6f)
            horizontalLineTo(15f)
            verticalLineTo(21f)
            horizontalLineTo(3f)
            close()
            moveTo(7f, 5f)
            verticalLineTo(19f)
            verticalLineTo(5f)
            close()
            moveToRelative(4.71f, 7.71f)
            quadTo(12f, 12.43f, 12f, 12f)
            reflectiveQuadTo(11.71f, 11.29f)
            reflectiveQuadTo(11f, 11f)
            reflectiveQuadToRelative(-0.71f, 0.29f)
            reflectiveQuadTo(10f, 12f)
            reflectiveQuadToRelative(0.29f, 0.71f)
            reflectiveQuadTo(11f, 13f)
            reflectiveQuadToRelative(0.71f, -0.29f)
            close()
            moveTo(7f, 19f)
            horizontalLineToRelative(6f)
            verticalLineTo(5f)
            horizontalLineTo(7f)
            verticalLineTo(19f)
            close()
          }
        }
        .build()
    return _meeting_room!!
  }

private var _meeting_room: ImageVector? = null
