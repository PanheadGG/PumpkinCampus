package com.pgigi.pumpkincampus.plugin

/**
 * 去除 JSON 文本中的注释，方便手写 `manifest.json`。
 *
 * kotlinx-serialization 的 Json 解析器不接受注释，而插件清单经常需要
 * 行注释（双斜杠开头）或块注释说明字段含义，因此解析前先做一次
 * **不进入字符串内部**的注释剥离（字符串里的双斜杠，
 * 如 `"url": "https://..."` 不会被误删）。
 */
object JsonComments {

    /** 返回去掉行注释与块注释后的 JSON 文本（保留换行，行号不漂移）。 */
    fun strip(raw: String): String {
        val out = StringBuilder(raw.length)
        var i = 0
        var inString = false
        var escaped = false
        while (i < raw.length) {
            val c = raw[i]
            if (inString) {
                out.append(c)
                when {
                    escaped -> escaped = false
                    c == '\\' -> escaped = true
                    c == '"' -> inString = false
                }
                i++
                continue
            }
            // 行注释
            if (c == '/' && i + 1 < raw.length && raw[i + 1] == '/') {
                while (i < raw.length && raw[i] != '\n') i++
                continue
            }
            // 块注释
            if (c == '/' && i + 1 < raw.length && raw[i + 1] == '*') {
                i += 2
                while (i + 1 < raw.length && !(raw[i] == '*' && raw[i + 1] == '/')) {
                    // 保留换行，避免把多行压成一行
                    if (raw[i] == '\n') out.append('\n')
                    i++
                }
                i = (i + 2).coerceAtMost(raw.length)
                continue
            }
            if (c == '"') inString = true
            out.append(c)
            i++
        }
        return out.toString()
    }
}
