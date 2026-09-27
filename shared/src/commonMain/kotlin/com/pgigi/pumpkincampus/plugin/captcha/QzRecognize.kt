package com.pgigi.pumpkincampus.plugin.captcha

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * QZ 验证码识别 —— 从 Pumpkin-Toolkit 移植（原包 `com.pgigi.pumpkintoolkit.qzrc`）。
 *
 * 原始项目: https://github.com/shuo747/QZRecognize
 * 原始逻辑: BufferedImage → 裁剪 → 分割 4 块 → 二值化 → 转 01 数组 → 与训练模板逐一比较。
 *
 * 本文件提供：
 * 1. [preprocessImage] —— expect/actual，输入图片二进制，灰度 + 二值化后返回 01 数组；
 * 2. [recognizeCaptcha] —— 纯逻辑，输入 01 数组返回 4 位验证码；
 * 3. [recognizeCaptchaImage] —— 上面两步的便捷组合（宿主 `captcha.recognize` 用它）。
 */

/**
 * 将图片二进制流灰度化、二值化后转换为 01 图片数组。
 *
 * 各平台通过 expect/actual 实现图片解码逻辑：
 * - Android: `BitmapFactory` 解码
 * - iOS:     `UIImage` + CoreGraphics 解码
 *
 * 二值化阈值与原始 `binaryImage` 一致：`avg = (R + G + B) / 3`，`avg < 192` → 1（黑），否则 → 0（白）。
 *
 * @param imageBytes 图片二进制流（JPEG / PNG 等）
 * @return 二维 01 数组，`result[row][col]`（即 `result[y][x]`），1 = 前景（黑），0 = 背景（白）；
 *   图片无法解码时返回空数组
 */
expect fun preprocessImage(imageBytes: ByteArray): Array<IntArray>

/**
 * 识别验证码 —— 参数从原始的 `BufferedImage` 改为 01 数组，其余逻辑与原函数一致。
 *
 * 流程（与原始 `QZRC.RC` 对应）：
 * 1. 裁剪：`getSubimage(0, 9, 80, 24)` → 取第 9 行起的 24 行；
 * 2. 分割 4 块：`getSubimage(4,0,20,h)` / `(22,0,20,h)` / `(40,0,20,h)` / `(58,0,20,h)`；
 * 3. 比较：每块与 [CharacterTemplates] 中 34 个模板逐一比较，取命中率最高者。
 *
 * @param imageArray 由 [preprocessImage] 返回的 01 图片数组（整张图的 `height` × `width`）
 * @return 识别出的 4 位验证码字符串；图片尺寸不够（宽 < 78 或高 < 33）时返回空串
 */
fun recognizeCaptcha(imageArray: Array<IntArray>): String {
    // ===== 1. 裁剪: 对应 image.getSubimage(0, 9, 80, 24) =====
    val cropY = 9
    val cropHeight = 24
    // 需要的最少尺寸：裁剪起点 + 24 行；最右侧分块到第 58 + 20 = 78 列
    if (imageArray.size < cropY + cropHeight) return ""
    if (imageArray.any { it.size < MaxColumn }) return ""
    val cropped = Array(cropHeight) { row ->
        imageArray[cropY + row]
    }

    // ===== 2. 分割4块: 每块 20 列宽 =====
    // 对应: getSubimage(4,0,20,h), getSubimage(22,0,20,h), getSubimage(40,0,20,h), getSubimage(58,0,20,h)
    val subWidth = 20
    val startXs = intArrayOf(4, 22, 40, 58)
    val subArrays = Array(4) { idx ->
        val startX = startXs[idx]
        Array(cropHeight) { row ->
            cropped[row].copyOfRange(startX, startX + subWidth)
        }
    }

    // ===== 3. 与训练模板逐一比较 (与原始 RC 逻辑完全一致) =====
    val sb = StringBuilder()
    var count = -1
    while (++count < 4) {
        var chCount = 0
        var target = 0      // 最接近的模板索引
        var temp = -1.0     // 当前最高命中率

        while (chCount < CharacterTemplates.templates.size) {
            var total = 1.0  // 从 1 开始, 避免除零 (与原始一致)
            var hit = 0

            val input = subArrays[count]
            val template = CharacterTemplates.templates[chCount]

            for (i in input.indices) {
                for (j in input[i].indices) {
                    if (template[i][j] == 1) {
                        total++
                        if (input[i][j] == template[i][j]) {
                            hit++
                        }
                    }
                }
            }

            val ratio = hit / total
            if (ratio > temp) {
                target = chCount
                temp = ratio
            }
            chCount++
        }

        // 比例最大的给他
        sb.append(CharacterTemplates.labels[target])
    }

    return sb.toString()
}

/**
 * 一步识别：图片二进制 → 01 数组 → 4 位验证码（宿主 `captcha.recognize` 的实现）。
 *
 * @param imageBytes 图片二进制（JPEG / PNG 等）
 * @return 识别结果；图片无法解码或尺寸不符时返回 null
 */
fun recognizeCaptchaImage(imageBytes: ByteArray): String? {
    if (imageBytes.isEmpty()) return null
    // 平台解码（BitmapFactory / UIImage）遇到损坏图片或宿主环境异常可能抛错，
    // 这里统一兜住返回 null，避免把异常抛进插件执行流程
    val array = runCatching { preprocessImage(imageBytes) }.getOrNull() ?: return null
    if (array.isEmpty()) return null
    return recognizeCaptcha(array).ifEmpty { null }
}

/** 分块最右侧需要的列数：最后一个分块起点 58 + 宽 20。 */
private const val MaxColumn = 78

/**
 * 把插件传来的图片字符串解码成二进制。
 *
 * 支持三种写法（插件 `http` 拿到的 base64、或直接给 data URL）：
 * - 纯 base64：`"/9j/4AAQSkZJRg..."`；
 * - data URL：`"data:image/jpeg;base64,/9j/4AAQ..."`；
 * - 空串 / 非法 base64 → null。
 *
 * 允许空白字符（换行、空格），便于插件把长 base64 分行书写。
 */
@OptIn(ExperimentalEncodingApi::class)
fun decodeCaptchaImage(raw: String): ByteArray? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return null
    val payload = if (trimmed.startsWith("data:", ignoreCase = true)) {
        val comma = trimmed.indexOf(',')
        if (comma < 0) return null
        trimmed.substring(comma + 1)
    } else {
        trimmed
    }
    val cleaned = payload.filterNot { it.isWhitespace() }
    if (cleaned.isEmpty()) return null
    return runCatching { Base64.decode(cleaned) }.getOrNull()
}
