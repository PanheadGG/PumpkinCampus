package com.pgigi.pumpkincampus.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.ByteArrayOutputStream
import kotlin.math.max

/**
 * Android：`BitmapFactory` 解码（[BitmapFactory.Options.inSampleSize] 降采样）
 * → 铺白底（PNG 透明像素压 JPEG 不会变黑）→ JPEG 编码。
 */
actual fun downscaleToJpeg(bytes: ByteArray, maxSide: Int, quality: Double): ByteArray? {
    if (bytes.isEmpty() || maxSide <= 0) return null

    // 只读尺寸，不分配位图
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    val options = BitmapFactory.Options().apply {
        inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxSide)
    }
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null

    // 透明图（PNG/WebP）直接压 JPEG 会把透明像素变成黑的，先铺一层白底
    val flattened = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(flattened)
    canvas.drawColor(Color.WHITE)
    canvas.drawBitmap(bitmap, 0f, 0f, null)
    if (flattened !== bitmap) bitmap.recycle()

    val stream = ByteArrayOutputStream()
    val ok = flattened.compress(
        Bitmap.CompressFormat.JPEG,
        (quality * 100).toInt().coerceIn(1, 100),
        stream
    )
    flattened.recycle()
    return if (ok) stream.toByteArray() else null
}

/** 解码成 [ImageBitmap]（库里已是 ≤1600px 的 JPEG，直接解即可）。 */
actual fun decodeImageBitmap(bytes: ByteArray): ImageBitmap? {
    if (bytes.isEmpty()) return null
    return runCatching {
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
    }.getOrNull()
}

/** 按最长边算 2 的幂次采样率（1/2、1/4……），保证结果边长 ≤ [maxSide] 的两倍以内。 */
private fun sampleSize(width: Int, height: Int, maxSide: Int): Int {
    var sample = 1
    val longest = max(width, height)
    while (longest / sample > maxSide) sample *= 2
    return sample
}
