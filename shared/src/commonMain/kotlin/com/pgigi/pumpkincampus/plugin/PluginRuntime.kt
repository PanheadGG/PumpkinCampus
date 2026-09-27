package com.pgigi.pumpkincampus.plugin

import com.dokar.quickjs.ModuleContent
import com.dokar.quickjs.QuickJs
import com.dokar.quickjs.QuickJsException
import com.dokar.quickjs.binding.asyncFunction
import com.dokar.quickjs.binding.define
import com.dokar.quickjs.binding.function
import com.dokar.quickjs.moduleLoader
import com.dokar.quickjs.quickJs
import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Element
import com.pgigi.pumpkincampus.appVersionCode
import com.pgigi.pumpkincampus.models.Course
import com.pgigi.pumpkincampus.plugin.captcha.recognizeCaptchaImage
import com.pgigi.pumpkincampus.plugin.captcha.decodeCaptchaImage
import com.pgigi.pumpkincampus.utils.JsonUtil
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.cookies.AcceptAllCookiesStorage
import io.ktor.client.plugins.cookies.CookiesStorage
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readRawBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlin.time.TimeSource

/**
 * 一次插件执行请求：宿主把插件、课表与配置打包交给 [runPlugin]。
 *
 * @property files 插件包内全部文件（相对路径 → 源码），由 [PluginManager.readFiles] 提供
 * @property config 该课表生效的**已合并**配置 JSON（`buildPluginConfigJson` 的结果）
 * @property scheduleId / scheduleName 当前课表（KV 隔离与 ctx 展示用）
 */
data class PluginRunRequest(
    val manifest: PluginManifest,
    val files: Map<String, String>,
    val config: String,
    val scheduleId: String,
    val scheduleName: String
)

/**
 * 插件执行结果。
 *
 * @property courses 解析并校验通过的课程（成功时非 null 语义：失败为空列表）
 * @property error 失败原因（面向用户 + 开发者的可读文案）；成功为 null
 * @property logs 本次执行捕获的日志：插件 `console.*` 输出 + 宿主自动记录的
 *   **原始请求 / 原始响应**（方法、URL、请求头、请求体、状态码、耗时、响应头、响应体，
 *   敏感值已脱敏）、模块加载与课程校验告警；条数与总长度见 [MAX_LOG_LINES] /
 *   [MAX_LOG_TOTAL_CHARS]
 */
data class PluginRunResult(
    val success: Boolean,
    val courses: List<Course>,
    val error: String? = null,
    val logs: List<String> = emptyList()
)

/** console 日志最多保留条数。 */
private const val MAX_LOG_LINES = 400

/** 单条日志最大长度。 */
private const val MAX_LOG_LINE = 4000

/** 日志总长度上限（字符）：超出后不再追加，避免插件存档（含日志）无限膨胀。 */
private const val MAX_LOG_TOTAL_CHARS = 60_000

/** HTTP 原始请求 / 响应体在日志里的最大长度；超出截断并标注原始长度。 */
private const val HTTP_LOG_BODY_CHARS = 4000

/** 生效配置在日志里的最大长度。 */
private const val CONFIG_LOG_CHARS = 1000

/** 墙钟超时（含网络等待）：整个插件执行的硬上限。 */
private const val WALL_TIMEOUT_MS = 90_000L

/** 纯 JS 执行超时（QuickJS CPU 时间；网络等待不计入）。 */
private const val JS_TIMEOUT_MS = 20_000L

/** 单个 HTTP 请求超时。 */
private const val HTTP_TIMEOUT_MS = 30_000L

/**
 * 在独立 QuickJS 运行时里执行插件：加载 ES Module 入口 → 调用 `getCourses(ctx)`
 * → 把返回的课程 JSON 解析成 [Course] 列表。
 *
 * 每次执行创建并销毁一个运行时（隔离全局状态），桥接四组宿主能力：
 * - `console.*` → 宿主日志（返回给 UI 的 logs）
 * - `ksoup.*` → KSoup HTML 解析（节点 id 注册表，见 [KsoupBridge]）
 * - `http.*` → Ktor 客户端（每次执行独立 Cookie 会话，登录态在同步内保持）
 * - `storage.*` → 按 (插件, 课表) 隔离的 KV（[PluginKvStore]）
 *
 * 超时：墙钟 [WALL_TIMEOUT_MS]（网络 + 执行）、JS CPU [JS_TIMEOUT_MS]、
 * 单请求 [HTTP_TIMEOUT_MS]。
 */
suspend fun runPlugin(request: PluginRunRequest): PluginRunResult {
    // 本次执行共享的日志缓冲：超时/异常时也能拿到已产生的 console 输出
    val logs = mutableListOf<String>()

    val result = withTimeoutOrNull(WALL_TIMEOUT_MS) {
        try {
            withContext(Dispatchers.Default) {
                executePlugin(request, logs)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: QuickJsException) {
            PluginRunResult(false, emptyList(), describeJsError(e), logs.toList())
        } catch (e: Exception) {
            PluginRunResult(
                false, emptyList(),
                "插件执行出错：${e.message ?: e::class.simpleName}",
                logs.toList()
            )
        }
    }

    return result ?: PluginRunResult(
        false, emptyList(),
        "同步超时：插件超过 ${WALL_TIMEOUT_MS / 1000} 秒未完成（网络太慢或插件死循环）",
        logs.toList()
    )
}

/* ------------------------------------------------------------------ */
/* 执行主体                                                             */
/* ------------------------------------------------------------------ */

private suspend fun executePlugin(
    request: PluginRunRequest,
    logs: MutableList<String>
): PluginRunResult {
    val manifest = request.manifest
    val entryPath = manifest.entry.replace('\\', '/')
    // 日志预算：单条限长 + 总量封顶（超限时补一条说明，避免静默丢日志）
    var logChars = 0
    var budgetWarned = false
    fun log(level: String, message: String) {
        if (logs.size >= MAX_LOG_LINES) return
        val line = ("[$level] " + message).take(MAX_LOG_LINE)
        if (logChars + line.length > MAX_LOG_TOTAL_CHARS) {
            if (!budgetWarned) {
                budgetWarned = true
                logs += "[warn] 日志已达上限（$MAX_LOG_TOTAL_CHARS 字符），后续输出被截断"
            }
            return
        }
        logChars += line.length
        logs += line
    }

    var captured: String? = null
    val ksoup = KsoupBridge()
    val kv = PluginKvStore(manifest.id, request.scheduleId)
    val httpSession = PluginHttpSession()
    val contextJson = buildContextJson(request)

    // —— 执行环境概览（排查问题先看这几行） ——
    log(
        "info",
        "插件 ${manifest.id} v${manifest.version.ifBlank { "?" }} · 入口 ${manifest.entry} · " +
            "宿主 versionCode=${appVersionCode()} · 课表「${request.scheduleName}」(${request.scheduleId})"
    )
    log("info", "包内文件：${request.files.keys.sorted().joinToString(", ")}")
    log("info", "生效配置：${clipForLog(maskConfigForLog(request.config, manifest.configs), CONFIG_LOG_CHARS)}")

    try {
        val loader = moduleLoader {
            load { name ->
                val content = resolveModuleContent(name, request.files)
                if (content == null) {
                    // 常见故障：entry 写错 / 相对导入路径不对——把包内文件列出来便于对照
                    log(
                        "warn",
                        "模块加载失败：找不到 $name；包内可用文件：" +
                            request.files.keys.sorted().joinToString(", ")
                    )
                } else {
                    log("info", "加载模块：$name")
                }
                content
            }
        }

        quickJs(moduleLoader = loader) {
            evaluationTimeoutMillis = JS_TIMEOUT_MS

            // —— 结果与日志 ——
            function("__result") { args ->
                captured = args.getOrNull(0) as? String
                Unit
            }
            function("__log") { args ->
                val level = args.getOrNull(0)?.toString() ?: "log"
                log(level, args.getOrNull(1)?.toString() ?: "")
                Unit
            }
            function("__ctx") { contextJson }

            // —— ksoup：节点 id 注册表（同名对象由 prelude 包成 KsoupNode 类） ——
            define("__ksoup") {
                function("parse") { args ->
                    val html = args.getOrNull(0)?.toString() ?: ""
                    ksoup.register(Ksoup.parse(html).body())
                }
                function("clear") {
                    ksoup.clear()
                    Unit
                }
                function("tagName") { args -> ksoup.node(args)?.tagName() }
                function("text") { args -> ksoup.node(args)?.text() }
                function("html") { args -> ksoup.node(args)?.html() }
                function("outerHtml") { args -> ksoup.node(args)?.outerHtml() }
                function("id") { args -> ksoup.node(args)?.id() }
                function("attr") { args ->
                    ksoup.node(args)?.attr(args.getOrNull(1)?.toString() ?: "")
                }
                function("hasAttr") { args ->
                    ksoup.node(args)?.hasAttr(args.getOrNull(1)?.toString() ?: "") == true
                }
                function("children") { args ->
                    ksoup.node(args)?.children()?.map { ksoup.register(it) } ?: emptyList()
                }
                function("select") { args ->
                    ksoup.node(args)?.select(args.getOrNull(1)?.toString() ?: "")
                        ?.map { ksoup.register(it) } ?: emptyList()
                }
                function("selectFirst") { args ->
                    val el = ksoup.node(args)
                        ?.selectFirst(args.getOrNull(1)?.toString() ?: "")
                    el?.let { ksoup.register(it) } ?: -1
                }
                function("parent") { args ->
                    ksoup.node(args)?.parent()?.let { ksoup.register(it) } ?: -1
                }
            }

            // —— storage：按 (插件, 课表) 隔离的 KV ——
            define("__kv") {
                function("get") { args ->
                    args.getOrNull(0)?.toString()?.let { kv.get(it) }
                }
                function("set") { args ->
                    val key = args.getOrNull(0)?.toString()
                    val value = args.getOrNull(1)?.toString()
                    if (key != null && value != null) kv.set(key, value)
                    Unit
                }
                function("remove") { args ->
                    args.getOrNull(0)?.toString()?.let { kv.remove(it) }
                    Unit
                }
                function("has") { args ->
                    args.getOrNull(0)?.toString()?.let { kv.has(it) } == true
                }
                function("keys") { kv.keysJson() }
                function("clear") {
                    kv.clear()
                    Unit
                }
            }

            // —— captcha：QZ 验证码识别（图片 → 4 位验证码），独立于 http 的单独方法 ——
            define("__captcha") {
                function("recognize") { args ->
                    val raw = args.getOrNull(0)?.toString() ?: ""
                    if (raw.isBlank()) {
                        log("warn", "captcha.recognize() 没有收到图片数据")
                        null
                    } else {
                        val bytes = decodeCaptchaImage(raw)
                        if (bytes == null) {
                            log(
                                "warn",
                                "captcha.recognize() 的图片不是合法 base64" +
                                    "（长度 ${raw.length}，支持纯 base64 或 data:image/...;base64, 前缀）"
                            )
                            null
                        } else {
                            val code = recognizeCaptchaImage(bytes)
                            if (code == null) {
                                log(
                                    "warn",
                                    "captcha.recognize() 识别失败：图片无法解码或尺寸不符" +
                                        "（${bytes.size} 字节，验证码需 ≥78×33）"
                                )
                                null
                            } else {
                                log("info", "captcha.recognize() → $code（图片 ${bytes.size} 字节）")
                                code
                            }
                        }
                    }
                }
            }

            // —— http：Ktor（async，可 top-level await） ——
            asyncFunction("__http") { args ->
                performHttp(
                    session = httpSession,
                    raw = args.getOrNull(0)?.toString() ?: "{}",
                    log = { level, message -> log(level, message) }
                )
            }

            // 1) prelude：把原始桥接包装成插件友好的 console/ksoup/storage/http
            evaluate<Any?>(PLUGIN_PRELUDE, filename = "pumpkin-prelude.js")
            // 2) 引导模块：import 插件入口并调用导出的 getCourses
            evaluate<Any?>(
                buildBootstrap(entryPath),
                filename = BOOTSTRAP_MODULE,
                asModule = true
            )
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: QuickJsException) {
        return PluginRunResult(false, emptyList(), describeJsError(e), logs.toList())
    } catch (e: Exception) {
        return PluginRunResult(
            false, emptyList(),
            "插件执行出错：${e.message ?: e::class.simpleName}",
            logs.toList()
        )
    } finally {
        ksoup.clear()
        httpSession.close()
    }

    val (courses, warnings) = parsePluginCourses(captured)
    warnings.forEach { log("warn", it) }
    if (captured == null) {
        log("warn", "getCourses() 没有返回值（入口需要 export async function getCourses(ctx)）")
        return PluginRunResult(
            false, emptyList(),
            "插件没有返回结果：入口需要 export async function getCourses(ctx)",
            logs.toList()
        )
    }
    log("info", "getCourses 返回 ${courses.size} 门课程（已通过宿主校验）")
    return PluginRunResult(true, courses, null, logs.toList())
}

/** 引导模块名（宿主私有，避免与插件文件重名）。 */
private const val BOOTSTRAP_MODULE = "__pumpkin_bootstrap.js"

/** 引导模块：导入插件入口、构造 ctx、调用 getCourses 并把结果交回宿主。 */
private fun buildBootstrap(entryPath: String): String = """
import { getCourses } from "./$entryPath";
const ctx = JSON.parse(globalThis.__ctx());
const courses = await getCourses(ctx);
let out;
try {
  out = JSON.stringify(courses === undefined ? null : courses);
} catch (e) {
  throw new Error("getCourses 返回值无法序列化为 JSON：" + e);
}
globalThis.__result(out);
""".trimIndent()

/** ES Module 加载：包内文件 + 常见解析回退（`./a.js`、`a/index.js`）。 */
private fun resolveModuleContent(
    name: String,
    files: Map<String, String>
): ModuleContent? {
    val clean = name.removePrefix("./")
    val candidates = buildList {
        add(clean)
        if (!clean.endsWith(".js")) add("$clean.js")
        add("$clean/index.js")
    }
    for (candidate in candidates) {
        files[candidate]?.let { return ModuleContent.Source(it) }
    }
    return null
}

/* ------------------------------------------------------------------ */
/* 宿主能力                                                             */
/* ------------------------------------------------------------------ */

/** 插件执行的上下文 JSON（JS 侧 `ctx`）。 */
private fun buildContextJson(request: PluginRunRequest): String = buildJsonObject {
    put("pluginId", request.manifest.id)
    put("pluginName", request.manifest.name)
    put("pluginVersion", request.manifest.version)
    put("schoolName", request.manifest.schoolName)
    put("hostVersionCode", appVersionCode())
    put("sdk", "pumpkincampus-plugin")
    putJsonObject("schedule") {
        put("id", request.scheduleId)
        put("name", request.scheduleName)
    }
    put("config", Json.parseToJsonElement(request.config))
}.toString()

/**
 * 插件 HTTP 会话：两个客户端共用一个 Cookie 罐。
 *
 * 为什么要两个客户端：Ktor 的重定向跟随只能在客户端级别开关
 * （`HttpRedirect` 插件没有按请求生效的开关）。教务系统登录往往要
 * 「看到 302 + Location + 该跳转响应上的 Set-Cookie」，所以插件可以按请求
 * 用 `followRedirects: false` 选择不跟随的那个客户端；
 * 两者共享 [AcceptAllCookiesStorage]，会话不会因为换客户端而丢失。
 */
private class PluginHttpSession {
    private val cookieStorage = AcceptAllCookiesStorage()
    private val following = buildHttpClient(cookieStorage, followRedirects = true)
    private val noRedirect = buildHttpClient(cookieStorage, followRedirects = false)

    /** 按请求选择客户端；[followRedirects] 为 false 时才用不跟随的客户端。 */
    fun clientFor(followRedirects: Boolean?): HttpClient =
        if (followRedirects == false) noRedirect else following

    fun close() {
        runCatching { following.close() }
        runCatching { noRedirect.close() }
    }
}

/** 每次执行独立的 Ktor 客户端：30 秒超时 + 同步内的 Cookie 会话（登录态保持）。 */
private fun buildHttpClient(
    cookieStorage: CookiesStorage,
    followRedirects: Boolean
): HttpClient = HttpClient {
    this.followRedirects = followRedirects
    install(HttpTimeout) {
        requestTimeoutMillis = HTTP_TIMEOUT_MS
        connectTimeoutMillis = 15_000
        socketTimeoutMillis = HTTP_TIMEOUT_MS
    }
    // 每次同步一个全新 Cookie 罐：登录 → 带 cookie 拉课表，同步结束随客户端释放
    install(HttpCookies) { storage = cookieStorage }
}

/** `http` 桥的请求/响应契约（与 docs/plugin-api.md 一致）。 */
@Serializable
data class PluginHttpRequest(
    val url: String,
    val method: String = "GET",
    val headers: Map<String, String> = emptyMap(),
    val body: String? = null,
    val contentType: String? = null,
    /**
     * 响应体形式：默认（null / `"text"`）走 `body` 字符串；
     * `"base64"`（等价 `"binary"`）走 `bodyBase64`——用于验证码图片等二进制响应。
     */
    val responseType: String? = null,
    /**
     * 是否自动跟随 3xx 跳转，默认 true（等价 Ktor 默认行为）。
     *
     * 传 `false` 时宿主返回原始 3xx 响应，插件可以从 `headers.location`
     * 读到跳转地址、从 `headers["set-cookie"]` 读到该跳转下发的 Cookie——
     * 教务系统登录流程通常需要这样手动跟跳。
     */
    val followRedirects: Boolean? = null
)

@Serializable
data class PluginHttpResponse(
    val ok: Boolean,
    val status: Int,
    val headers: Map<String, String> = emptyMap(),
    val body: String = "",
    val error: String? = null,
    /** `responseType: "base64"` 时的响应体（base64 文本）；文本模式下为 null。 */
    val bodyBase64: String? = null,
    /** `responseType: "base64"` 时的原始字节数（便于日志/校验）。 */
    val bodyBytes: Int? = null
)

/**
 * 执行一次 HTTP 请求；网络失败返回 `ok:false + error`（不抛给插件，便于 try/catch）。
 *
 * **日志**：把整个请求与响应原样写进同步日志（方法/URL/请求头/请求体、
 * 状态码/耗时/响应头/响应体），敏感值脱敏、超长内容截断——
 * 插件不用自己 `console.log` 就能排查接口问题。
 */
private suspend fun performHttp(
    session: PluginHttpSession,
    raw: String,
    log: (String, String) -> Unit
): String {
    val req = try {
        JsonUtil.parseJson(raw, PluginHttpRequest.serializer())
    } catch (e: Exception) {
        val message = "请求参数不是合法 JSON：${e.message}"
        log("error", message)
        return JsonUtil.toJson(
            PluginHttpResponse(ok = false, status = 0, error = message),
            PluginHttpResponse.serializer()
        )
    }
    if (req.url.isBlank()) {
        log("error", "http 请求的 url 为空")
        return JsonUtil.toJson(
            PluginHttpResponse(ok = false, status = 0, error = "url 为空"),
            PluginHttpResponse.serializer()
        )
    }

    val methodName = req.method.ifBlank { "GET" }.uppercase()
    // 响应体是否按二进制取（base64 文本返回）：验证码图片这类响应不能用 bodyAsText
    val wantBase64 = req.responseType?.lowercase() in setOf("base64", "binary", "bytes")
    // —— 原始请求 ——
    log("http", "→ $methodName ${req.url}")
    if (req.headers.isNotEmpty()) {
        log(
            "http",
            "  请求头：${clipForLog(maskSecrets(formatHeaders(req.headers)), HTTP_LOG_BODY_CHARS)}"
        )
    }
    req.contentType?.let { log("http", "  请求 Content-Type：$it") }
    log(
        "http",
        "  请求体：${clipForLog(maskSecrets(req.body ?: ""), HTTP_LOG_BODY_CHARS).ifEmpty { "(空)" }}"
    )
    if (wantBase64) log("http", "  响应体按二进制读取（base64 返回）")
    if (req.followRedirects == false) log("http", "  不自动跟随 3xx 跳转（返回原始重定向响应）")

    val startedAt = TimeSource.Monotonic.markNow()
    return try {
        val resp = session.clientFor(req.followRedirects).request(req.url) {
            method = HttpMethod.parse(methodName)
            req.headers.forEach { (key, value) ->
                if (key.isNotBlank()) header(key, value)
            }
            req.body?.let { body ->
                setBody(body)
                req.contentType?.let { contentType(ContentType.parse(it)) }
            }
        }
        val headerMap = mutableMapOf<String, String>()
        resp.headers.entries().forEach { entry ->
            headerMap[entry.key] = entry.value.joinToString(", ")
        }
        val status = resp.status.value
        // 二进制模式：读原始字节再 base64 编码；文本模式：直接读文本
        val rawBytes = if (wantBase64) resp.readRawBytes() else null
        val text = if (wantBase64) "" else resp.bodyAsText()
        val elapsed = startedAt.elapsedNow().inWholeMilliseconds

        // —— 原始响应 ——
        if (wantBase64) {
            val size = rawBytes?.size ?: 0
            log("http", "← $status ${resp.status.description} · $size 字节 · ${elapsed}ms")
            if (headerMap.isNotEmpty()) {
                log(
                    "http",
                    "  响应头：${clipForLog(maskSecrets(formatHeaders(headerMap)), HTTP_LOG_BODY_CHARS)}"
                )
            }
            // base64 全文太长且无排查价值，只记录字节数（识别结果由 captcha.recognize 记日志）
            log("http", "  响应体：<二进制 $size 字节，已转为 base64 返回给插件>")
        } else {
            log("http", "← $status ${resp.status.description} · ${text.length} 字符 · ${elapsed}ms")
            if (headerMap.isNotEmpty()) {
                log(
                    "http",
                    "  响应头：${clipForLog(maskSecrets(formatHeaders(headerMap)), HTTP_LOG_BODY_CHARS)}"
                )
            }
            log(
                "http",
                "  响应体：${clipForLog(maskSecrets(text), HTTP_LOG_BODY_CHARS).ifEmpty { "(空)" }}"
            )
        }

        JsonUtil.toJson(
            PluginHttpResponse(
                ok = status in 200..299,
                status = status,
                headers = headerMap,
                body = text,
                bodyBase64 = rawBytes?.let { encodeBase64(it) },
                bodyBytes = rawBytes?.size
            ),
            PluginHttpResponse.serializer()
        )
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        val message = e.message ?: "网络请求失败"
        log(
            "error",
            "← 请求失败（${startedAt.elapsedNow().inWholeMilliseconds}ms）：$message"
        )
        JsonUtil.toJson(
            PluginHttpResponse(ok = false, status = 0, error = message),
            PluginHttpResponse.serializer()
        )
    }
}

/* ------------------------------------------------------------------ */
/* 日志辅助：脱敏与截断                                                  */
/* ------------------------------------------------------------------ */

/** 二进制响应体 → base64 文本（`responseType: "base64"` 用）。 */
@OptIn(ExperimentalEncodingApi::class)
private fun encodeBase64(bytes: ByteArray): String = Base64.encode(bytes)

/**
 * 表单体 / JSON 里的敏感键（值会被替换成 `***`）。
 *
 * 同时被 [isPluginSecretConfig] 复用：命中这些词的**插件配置项**会被当作敏感值，
 * 写进 KVault 加密存储而不是明文存档。
 */
internal const val SENSITIVE_KEY_PATTERN =
    "password|passwd|pwd|pass|token|secret|access_token|refresh_token|sessionid|session_id"

private val FormSecretRegex = Regex(
    """\b($SENSITIVE_KEY_PATTERN)=([^&\s]*)""",
    RegexOption.IGNORE_CASE
)

private val JsonSecretRegex = Regex(
    "\"($SENSITIVE_KEY_PATTERN)\"\\s*:\\s*\"[^\"]*\"",
    RegexOption.IGNORE_CASE
)

/**
 * 日志脱敏：请求体里的密码 / token 用 `***` 代替（保留键名，便于确认字段是否送到）。
 *
 * Cookie 头只隐藏**值**、保留 cookie 名与属性——登录排查时「有没有拿到 Cookie」
 * 往往比 Cookie 内容更重要。
 */
internal fun maskSecrets(text: String): String = text
    .replace(FormSecretRegex) { match -> "${match.groupValues[1]}=***" }
    .replace(JsonSecretRegex) { match -> "\"${match.groupValues[1]}\":\"***\"" }

/**
 * **生效配置**写进日志前的脱敏。
 *
 * 除了按 key 名脱敏（[maskSecrets]），还会把 manifest 里声明为 `password`
 * （或 key 命中敏感词，见 [isPluginSecretConfig]）的配置值替换成 `***`：
 * 这些值在存档里是加密的，日志里同样不应该出现明文——即使插件作者把 key
 * 起成了 `mima` 这种不命中敏感词的名字。
 */
internal fun maskConfigForLog(configJson: String, configs: List<PluginConfigItem>): String {
    val obj = runCatching { Json.parseToJsonElement(configJson) }.getOrNull() as? JsonObject
        ?: return maskSecrets(configJson)
    val masked = buildMap {
        for ((key, value) in obj) {
            put(
                key,
                if (isPluginSecretConfig(key, configs.configTypeOf(key))) JsonPrimitive("***") else value
            )
        }
    }
    return maskSecrets(JsonObject(masked).toString())
}

/** 请求 / 响应头拼成一行（Cookie / 认证类只保留可公开的部分）。 */
internal fun formatHeaders(headers: Map<String, String>): String =
    headers.entries.joinToString(", ") { (key, value) ->
        val masked = when (key.lowercase()) {
            "cookie", "set-cookie" -> maskCookieValue(value)
            "authorization", "proxy-authorization" -> "***"
            else -> value
        }
        "$key: $masked"
    }

/** Cookie 属性名：这些 `name=value` 不是凭据，保留原样便于排查。 */
private val CookieAttributeNames = setOf(
    "path", "domain", "expires", "max-age", "secure", "httponly",
    "samesite", "priority", "partitioned", "version", "comment"
)

/** `JSESSIONID=abc` / `name=value` 对（不含属性）的匹配。 */
private val CookiePairRegex = Regex("""([^=;,\s]+)=([^;]*)""")

/**
 * Cookie / Set-Cookie 的值脱敏：`JSESSIONID=abc; Path=/; HttpOnly`
 * → `JSESSIONID=***; Path=/; HttpOnly`（cookie 名与属性保留，便于确认登录是否拿到 Cookie）。
 */
internal fun maskCookieValue(value: String): String =
    CookiePairRegex.replace(value) { match ->
        val name = match.groupValues[1]
        if (name.lowercase() in CookieAttributeNames) match.value else "$name=***"
    }

/** 截断过长文本用于日志，并标注原始长度。 */
internal fun clipForLog(text: String, limit: Int = HTTP_LOG_BODY_CHARS): String =
    if (text.length <= limit) {
        text
    } else {
        text.take(limit) + "…（已截断，原始 ${text.length} 字符）"
    }

/** 把 QuickJsException 转成带 文件:行号 与堆栈的可读错误。 */
private fun describeJsError(e: QuickJsException): String {
    val location = buildString {
        e.fileName?.let { append(it) }
        e.lineNumber?.let {
            if (isNotEmpty()) append(':')
            append(it)
        }
    }
    val head = if (location.isEmpty()) "JS 错误" else "JS 错误 @ $location"
    val detail = (e.message ?: e.stack ?: "执行失败").take(1500)
    return "$head：$detail"
}

/* ------------------------------------------------------------------ */
/* ksoup 桥                                                             */
/* ------------------------------------------------------------------ */

/**
 * KSoup 节点注册表：JS 侧只持有 `int` 节点 id，Kotlin 侧保存 [Element] 实例。
 *
 * 一次插件执行对应一个实例，执行结束 [clear]，不会跨运行时泄漏。
 */
private class KsoupBridge {
    private val nodes = mutableMapOf<Int, Element>()
    private var nextId = 1

    fun register(element: Element): Int {
        val id = nextId++
        nodes[id] = element
        return id
    }

    fun node(args: Array<Any?>): Element? {
        val id = (args.getOrNull(0) as? Number)?.toInt() ?: return null
        return nodes[id]
    }

    fun clear() = nodes.clear()
}

/* ------------------------------------------------------------------ */
/* JS prelude（原始桥接 → 插件 API）                                    */
/* ------------------------------------------------------------------ */

/**
 * 在入口模块之前以**普通脚本**执行，安装插件可见的全局 API：
 * `console` / `KsoupNode` / `ksoup` / `storage` / `http`。
 *
 * 注意：字符串里不能出现 `$`（Kotlin 原始字符串插值）。
 */
private val PLUGIN_PRELUDE: String = """
(function () {
  "use strict";
  var g = globalThis;

  function fmt(v) {
    if (typeof v === "string") return v;
    try {
      var s = JSON.stringify(v);
      return s === undefined ? String(v) : s;
    } catch (e) {
      return String(v);
    }
  }
  function joinArgs(args) {
    return Array.prototype.map.call(args, fmt).join(" ");
  }

  g.console = {
    log: function () { __log("log", joinArgs(arguments)); },
    info: function () { __log("info", joinArgs(arguments)); },
    warn: function () { __log("warn", joinArgs(arguments)); },
    error: function () { __log("error", joinArgs(arguments)); },
    debug: function () { __log("debug", joinArgs(arguments)); }
  };

  g.KsoupNode = class KsoupNode {
    constructor(id) { this._id = id; }
    get tagName() { return __ksoup.tagName(this._id); }
    get text() { return __ksoup.text(this._id); }
    get html() { return __ksoup.html(this._id); }
    get outerHtml() { return __ksoup.outerHtml(this._id); }
    get id() { return __ksoup.id(this._id); }
    get parent() {
      var id = __ksoup.parent(this._id);
      return id < 0 ? null : new g.KsoupNode(id);
    }
    get children() {
      return __ksoup.children(this._id).map(function (i) { return new g.KsoupNode(i); });
    }
    attr(name) { return __ksoup.attr(this._id, String(name)); }
    hasAttr(name) { return __ksoup.hasAttr(this._id, String(name)); }
    select(selector) {
      return __ksoup.select(this._id, String(selector)).map(function (i) {
        return new g.KsoupNode(i);
      });
    }
    selectFirst(selector) {
      var id = __ksoup.selectFirst(this._id, String(selector));
      return id < 0 ? null : new g.KsoupNode(id);
    }
    toString() { return "[KsoupNode #" + this._id + "]"; }
  };

  g.ksoup = {
    parse: function (html) { return new g.KsoupNode(__ksoup.parse(String(html))); },
    clear: function () { __ksoup.clear(); }
  };

  g.storage = {
    get: function (key) {
      var v = __kv.get(String(key));
      return v === null || v === undefined ? null : JSON.parse(v);
    },
    set: function (key, value) {
      __kv.set(String(key), JSON.stringify(value === undefined ? null : value));
    },
    remove: function (key) { __kv.remove(String(key)); },
    has: function (key) { return __kv.has(String(key)); },
    keys: function () { return JSON.parse(__kv.keys()); },
    clear: function () { __kv.clear(); }
  };

  // 验证码识别（QZ 模板匹配）：入参是图片 base64（也接受 data:image/...;base64, 前缀），
  // 返回 4 位验证码字符串；图片不合法/识别不出时返回 null。
  g.captcha = {
    recognize: function (image) {
      var v = __captcha.recognize(image === undefined || image === null ? "" : String(image));
      return v === undefined ? null : v;
    }
  };
  // 单独开的全局方法，等价于 captcha.recognize(...)
  g.recognizeCaptcha = g.captcha.recognize;

  function withDefaults(opts, url, method) {
    var o = Object.assign({}, opts || {});
    o.url = String(url);
    o.method = method;
    return o;
  }
  function withBody(opts, url, method, body) {
    var o = withDefaults(opts, url, method);
    if (body !== undefined && body !== null) {
      if (typeof body === "object") {
        o.body = JSON.stringify(body);
        if (!o.contentType) o.contentType = "application/json; charset=utf-8";
      } else {
        o.body = String(body);
      }
    }
    return o;
  }

  g.http = {
    request: async function (opts) {
      return JSON.parse(await __http(JSON.stringify(opts)));
    },
    get: function (url, opts) { return g.http.request(withDefaults(opts, url, "GET")); },
    head: function (url, opts) { return g.http.request(withDefaults(opts, url, "HEAD")); },
    post: function (url, body, opts) {
      return g.http.request(withBody(opts, url, "POST", body));
    },
    put: function (url, body, opts) {
      return g.http.request(withBody(opts, url, "PUT", body));
    },
    delete: function (url, body, opts) {
      return g.http.request(withBody(opts, url, "DELETE", body));
    },
    text: async function (url, opts) {
      var r = await g.http.get(url, opts);
      return r.body;
    },
    json: async function (url, opts) {
      var r = await g.http.get(url, opts);
      return r.body ? JSON.parse(r.body) : null;
    }
  };
})();
"""
