package com.pgigi.pumpkincampus.utils

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** 南瓜分享服务根地址（哈希存储，30 分钟自动过期）。 */
private const val SHARE_BASE = "https://pumpkin-share-api.pgigi.com"

/**
 * 共享 Ktor HTTP 客户端：引擎由平台注入——
 * Android = `ktor-client-android`，iOS = `ktor-client-darwin`；10 秒超时。
 */
private val client by lazy {
    HttpClient {
        install(HttpTimeout) {
            requestTimeoutMillis = 10_000
            connectTimeoutMillis = 10_000
            socketTimeoutMillis = 10_000
        }
    }
}

/**
 * 上传分享内容（当前课表的导出信封 JSON）到 `POST /share`：
 * 服务端计算内容 SHA256 作为分享口令存入 KV（30 分钟过期）。
 *
 * @return 成功返回分享口令，网络 / 服务错误返回 null
 */
suspend fun postShareContent(text: String): String? {
    val body = runCatching {
        val resp = client.post("$SHARE_BASE/share") {
            setBody(text)
            contentType(ContentType.Text.Plain)
        }
        if (resp.status.value in 200..299) resp.bodyAsText() else null
    }.getOrNull() ?: return null

    return runCatching {
        val obj = Json.parseToJsonElement(body).jsonObject
        if (obj["success"]?.jsonPrimitive?.booleanOrNull == true) {
            obj["key"]?.jsonPrimitive?.contentOrNull
        } else {
            null
        }
    }.getOrNull()
}

/** 按分享口令读取分享内容原文（`GET /get/{key}`）；口令不存在或已过期返回 null。 */
suspend fun getShareContent(key: String): String? = runCatching {
    val resp = client.get("$SHARE_BASE/get/$key")
    if (resp.status.value in 200..299) resp.bodyAsText() else null
}.getOrNull()

/**
 * 从粘贴的推荐语 / 口令文本中提取分享口令：
 * 优先匹配「」【】[] 括起来的 32~64 位十六进制串，否则取文本中第一段。
 * 用户只需复制朋友发来的整段推荐语粘贴即可，无需手动摘出口令。
 */
fun extractShareKey(text: String): String? =
    Regex("""[「\[【]([0-9a-fA-F]{32,64})[」\]】]""").find(text)?.groupValues?.get(1)
        ?: Regex("""\b[0-9a-fA-F]{32,64}\b""").find(text)?.value

/** 生成随口令拼接的分享推荐语（「分享」与「复制」按钮使用同一段文案）。 */
fun buildShareMessage(key: String): String =
    "这是来自「南瓜校园」的课表分享，30分钟内有效哦，如果失效请朋友再分享一遍叭。" +
        "为了保护隐私我们选择不监听你的剪贴板，请复制这条消息后，" +
        "打开App的主界面，右上角第二个按钮 -> 从分享口令导入，按操作提示即可完成导入~" +
        "分享口令为「$key」"
