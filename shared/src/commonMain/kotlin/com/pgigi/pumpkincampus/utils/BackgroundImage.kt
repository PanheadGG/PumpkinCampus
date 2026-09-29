package com.pgigi.pumpkincampus.utils

import androidx.compose.ui.graphics.ImageBitmap
import com.pgigi.pumpkincampus.constants.FileName
import kotlin.time.Clock

/** 导入时的目标最长边：选图即降采样，展示端内存可控（1600×1200 ≈ 7MB 位图）。 */
private const val MAX_SIDE = 1600

/** 入库 JPEG 质量。 */
private const val JPEG_QUALITY = 0.85

/**
 * 把用户选的图片**降采样 + 压成 JPEG** 存进应用私有目录，返回配置里存的文件名。
 *
 * 选图这一步就压好，比「原图入库、展示时再缩」划算得多：
 * - 落库的是几百 KB 的 JPEG（原图可能是 5MB+ 的相册原片）；
 * - 透明的 PNG 会被平台端铺白底再压 JPEG，不会压出黑块；
 * - iOS 相册的 HEIC 在这一步统一转成 JPEG，展示端只认一种格式。
 *
 * @param raw 用户选中的原始图片字节
 * @return 相对应用数据目录的文件名；解码/写入失败返回 null
 */
fun saveBackgroundImage(raw: ByteArray): String? {
    if (raw.isEmpty()) return null
    val encoded = downscaleToJpeg(raw, MAX_SIDE, JPEG_QUALITY) ?: return null
    val fileName =
        "${FileName.BACKGROUND_DIR}/bg_${Clock.System.now().toEpochMilliseconds()}.jpg"
    return fileName.takeIf { FileStoreUtils.writeBytes(fileName, encoded) }
}

/**
 * 读回背景图片并解码成 [ImageBitmap]。
 *
 * 文件不存在（换设备 / 清数据 / 被清理）或损坏时返回 null——调用方回落跟随系统背景，
 * 不会因此崩掉。
 */
fun loadBackgroundImage(fileName: String): ImageBitmap? {
    if (fileName.isEmpty()) return null
    val bytes = FileStoreUtils.readBytes(fileName) ?: return null
    if (bytes.isEmpty()) return null
    return decodeImageBitmap(bytes)
}

/** 删掉一张背景图片（配置被清除或被替换时调用）。 */
fun deleteBackgroundImage(fileName: String) {
    if (fileName.isNotEmpty()) FileStoreUtils.delete(fileName)
}

/** 平台：解码并降采样、编码成 JPEG（失败返回 null）。 */
expect fun downscaleToJpeg(bytes: ByteArray, maxSide: Int, quality: Double): ByteArray?

/** 平台：把（已压缩过的）图片字节解码成 [ImageBitmap]（失败返回 null）。 */
expect fun decodeImageBitmap(bytes: ByteArray): ImageBitmap?
