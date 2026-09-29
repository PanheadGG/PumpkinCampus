package com.pgigi.pumpkincampus.utils

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.allocArrayOf
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.usePinned
import org.jetbrains.skia.Color
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image as SkiaImage
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import platform.Foundation.NSData
import platform.Foundation.create
import platform.Foundation.length
import platform.UIKit.UIImage
import platform.UIKit.UIImagePNGRepresentation
import platform.posix.memcpy
import kotlin.math.max
import kotlin.math.min

/**
 * iOS：skia 解码 → 画进一张更小的 raster surface（铺白底）→ 编码 JPEG。
 *
 * skia 不一定认得相册的 HEIC：认不出来时先用 `UIImage` 解一遍、转成 PNG，
 * 再回到上面这条链路（PNG 是 skia 必支持的格式）。
 */
actual fun downscaleToJpeg(bytes: ByteArray, maxSide: Int, quality: Double): ByteArray? {
    if (bytes.isEmpty() || maxSide <= 0) return null
    val source = decodeSkiaImage(bytes) ?: return null

    val width = source.imageInfo.width
    val height = source.imageInfo.height
    if (width <= 0 || height <= 0) return null

    val scale = min(1.0, maxSide.toDouble() / max(width, height))
    val dstWidth = max(1, (width * scale).toInt())
    val dstHeight = max(1, (height * scale).toInt())

    val surface = Surface.makeRasterN32Premul(dstWidth, dstHeight)
    try {
        val canvas = surface.canvas
        // 白底：透明像素压 JPEG 时不会变黑
        canvas.clear(Color.WHITE)
        canvas.drawImageRect(source, Rect.makeWH(dstWidth.toFloat(), dstHeight.toFloat()))
        return surface.makeImageSnapshot()
            .encodeToData(
                format = EncodedImageFormat.JPEG,
                quality = (quality * 100).toInt().coerceIn(1, 100)
            )
            ?.bytes
    } finally {
        surface.close()
    }
}

/** 解码成 [ImageBitmap]（库里已是 ≤1600px 的 JPEG）。 */
actual fun decodeImageBitmap(bytes: ByteArray): ImageBitmap? {
    if (bytes.isEmpty()) return null
    val image = decodeSkiaImage(bytes) ?: return null
    return runCatching { image.toComposeImageBitmap() }.getOrNull()
}

/** skia 解码；失败（HEIC 等）时用 `UIImage` 转 PNG 再解一次。 */
private fun decodeSkiaImage(bytes: ByteArray): SkiaImage? {
    runCatching { SkiaImage.makeFromEncoded(bytes) }.getOrNull()?.let { return it }
    val png = runCatching { pngViaUIImage(bytes) }.getOrNull() ?: return null
    return runCatching { SkiaImage.makeFromEncoded(png) }.getOrNull()
}

/** `UIImage` → PNG 字节（skia 认不出的格式的兜底转换）。 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private fun pngViaUIImage(bytes: ByteArray): ByteArray? {
    val nsData = memScoped {
        NSData.create(bytes = allocArrayOf(bytes), length = bytes.size.toULong())
    }
    val image = UIImage(data = nsData)
    // 非图片或已损坏的数据拿不到 CGImage：先挡住，别把解不开的对象往下传
    if (image.CGImage() == null) return null
    val png = UIImagePNGRepresentation(image) ?: return null
    return png.toByteArray()
}

/** NSData → ByteArray（拷贝一份，避免悬垂指针）。 */
@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val length = length.toInt()
    if (length <= 0) return ByteArray(0)
    return ByteArray(length).also { array ->
        array.usePinned { pinned ->
            memcpy(pinned.addressOf(0), bytes, this.length)
        }
    }
}
