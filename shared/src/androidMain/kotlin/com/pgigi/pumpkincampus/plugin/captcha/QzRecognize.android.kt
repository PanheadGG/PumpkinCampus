package com.pgigi.pumpkincampus.plugin.captcha

import android.graphics.BitmapFactory

/**
 * Android 平台 actual 实现（移植自 Pumpkin-Toolkit `qzrc/QZRecognize.android.kt`）。
 *
 * 使用 `BitmapFactory` 解码图片二进制流，获取像素后执行灰度 + 二值化 + 转 01 数组。
 * 二值化逻辑与原始 `QZRC.binaryImage` + `img2Array` 一致：
 * `avg = (R + G + B) / 3`，`avg < 192` → 1（黑），否则 → 0（白）。
 */
actual fun preprocessImage(imageBytes: ByteArray): Array<IntArray> {
    if (imageBytes.isEmpty()) return emptyArray()
    val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        ?: return emptyArray()

    val width = bitmap.width
    val height = bitmap.height

    // 获取全部像素 (ARGB int 数组)
    val pixels = IntArray(width * height)
    bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

    // 灰度 + 二值化 → 01 数组
    // result[row][col] = result[y][x], 与原始 img2Array 的 res[height][width] 一致
    val result = Array(height) { IntArray(width) }
    for (y in 0 until height) {
        for (x in 0 until width) {
            val color = pixels[y * width + x]
            val r = (color shr 16) and 0xff
            val g = (color shr 8) and 0xff
            val b = color and 0xff
            // 与原始 binaryImage 一致: 使用 RGB 三通道平均值作为灰度
            val avg = (r + g + b) / 3f
            // 与原始一致: SW = 192, avg < 192 → 黑(1), 否则 → 白(0)
            result[y][x] = if (avg < 192) 1 else 0
        }
    }

    bitmap.recycle()
    return result
}
