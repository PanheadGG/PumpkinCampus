package com.pgigi.pumpkincampus.plugin.captcha

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.allocArrayOf
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.usePinned
import platform.CoreGraphics.CGBitmapContextCreate
import platform.CoreGraphics.CGColorSpaceCreateDeviceRGB
import platform.CoreGraphics.CGContextDrawImage
import platform.CoreGraphics.CGImageAlphaInfo
import platform.CoreGraphics.CGImageGetHeight
import platform.CoreGraphics.CGImageGetWidth
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSData
import platform.Foundation.create
import platform.UIKit.UIImage

/**
 * iOS 平台 actual 实现（移植自 Pumpkin-Toolkit `qzrc/QZRecognize.ios.kt`）。
 *
 * 使用 `UIImage` 解码图片二进制流，通过 CoreGraphics 提取 RGBA 像素后执行灰度 + 二值化 + 转 01 数组。
 * 二值化逻辑与原始 `QZRC.binaryImage` + `img2Array` 一致：
 * `avg = (R + G + B) / 3`，`avg < 192` → 1（黑），否则 → 0（白）。
 *
 * 注意：使用 `kCGImageAlphaPremultipliedLast`（RGBA 预乘 alpha）而非 `kCGImageAlphaNoneSkipLast`，
 * 因为后者第 4 字节（skip）值未定义，某些 iOS 版本上会让 `CGBitmapContextCreate` 返回 null；
 * 预乘实现在所有 iOS 版本均受支持，且对不透明图片（如 JPEG 验证码，alpha=255）不影响 RGB 值。
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
actual fun preprocessImage(imageBytes: ByteArray): Array<IntArray> {
    if (imageBytes.isEmpty()) return emptyArray()

    // 1. ByteArray → NSData → UIImage
    val nsData = memScoped {
        NSData.create(
            bytes = allocArrayOf(imageBytes),
            length = imageBytes.size.toULong()
        )
    }
    val image = UIImage(data = nsData)
    val cgImage = image.CGImage() ?: return emptyArray()

    val width = CGImageGetWidth(cgImage).toInt()
    val height = CGImageGetHeight(cgImage).toInt()
    if (width <= 0 || height <= 0) return emptyArray()

    // 2. 创建 RGBA 位图上下文，将图片绘制进去以获取原始像素
    val bytesPerPixel = 4
    val bytesPerRow = width * bytesPerPixel
    val colorSpace = CGColorSpaceCreateDeviceRGB()
    val pixelData = ByteArray(width * height * bytesPerPixel)

    // 用标志位追踪上下文是否创建成功（不能直接 return@usePinned，它只退出 lambda，
    // 会让全 0 的 pixelData 继续参与识别，得到全黑图）
    var contextCreated = false
    pixelData.usePinned { pinned ->
        val context = CGBitmapContextCreate(
            pinned.addressOf(0),
            width.toULong(),
            height.toULong(),
            8u,
            bytesPerRow.toULong(),
            colorSpace,
            CGImageAlphaInfo.kCGImageAlphaPremultipliedLast.value
        )
        if (context != null) {
            CGContextDrawImage(
                context,
                CGRectMake(0.0, 0.0, width.toDouble(), height.toDouble()),
                cgImage
            )
            contextCreated = true
        }
    }

    if (!contextCreated) return emptyArray()

    // 3. 灰度 + 二值化 → 01 数组
    // kCGImageAlphaPremultipliedLast 字节顺序: R(0), G(1), B(2), A(3)
    // 对不透明图片 A=255，预乘后 RGB 值不变
    val result = Array(height) { IntArray(width) }
    for (y in 0 until height) {
        for (x in 0 until width) {
            val offset = (y * width + x) * bytesPerPixel
            val r = pixelData[offset].toInt() and 0xff
            val g = pixelData[offset + 1].toInt() and 0xff
            val b = pixelData[offset + 2].toInt() and 0xff
            val avg = (r + g + b) / 3f
            result[y][x] = if (avg < 192) 1 else 0
        }
    }

    return result
}
