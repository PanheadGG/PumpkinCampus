# 插件宿主 API 速查表

> 这是**一页速查**。完整的开发文档（包结构、manifest 字段、`getCourses` 契约、
> 分步实战、调试、限制与安全）见 **[plugin-development.md](plugin-development.md)**。
> 遇到具体问题时请看完整文档，这里只列签名与关键约束。

插件运行在 App 内置的 **QuickJS** 里：每次同步新建运行时 → 注入下列全局 API →
执行入口 ES Module → 调用 `getCourses(ctx)` → 销毁。

---

## ctx（`getCourses(ctx)` 的参数）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `pluginId` / `pluginName` / `pluginVersion` / `schoolName` | string | 来自 manifest |
| `hostVersionCode` | number | 宿主 App versionCode |
| `sdk` | string | 恒为 `"pumpkincampus-plugin"` |
| `schedule.id` / `schedule.name` | string | 当前课表（`storage` 按 `id` 隔离） |
| `config` | object | **已合并**的配置：课表保存值 > manifest `default` > 类型零值；类型按 `configs[].type` 转成 string/number/boolean，每个 key 一定存在。`password` 类（及 key 命中 `password`/`token`/`secret` 的项）由宿主从**加密存储**（KVault：Android Keystore / iOS Keychain）解密后合并进来，不会明文落进课表存档 |

---

## 全局 API

```js
// —— http（都返回 Promise；网络失败不抛异常，看 resp.ok） ——
await http.get(url, opts?)
await http.head(url, opts?)
await http.post(url, body?, opts?)      // body 传对象 → 自动 JSON + application/json
await http.put(url, body?, opts?)
await http.delete(url, body?, opts?)
await http.request({ url, method, headers, body, contentType, responseType, followRedirects })
await http.text(url, opts?)             // → string
await http.json(url, opts?)             // → object | null

// —— ksoup ——
const doc = ksoup.parse(html)           // KsoupNode（根 = body）
ksoup.clear()

// —— storage（按「插件 id + 课表 id」隔离；值为 JSON 可序列化数据） ——
storage.get(key) / storage.set(key, value) / storage.has(key)
storage.keys() / storage.remove(key) / storage.clear()

// —— captcha（⚠️ 只支持强智教务系统 80×40 的 4 字符验证码） ——
captcha.recognize(imageBase64)          // → "1bmz" | null
recognizeCaptcha(imageBase64)           // 等价写法

// —— console（输出进入 App 内「同步日志」） ——
console.log / info / warn / error / debug(...)
```

### http 的 opts

| 字段 | 说明 |
| --- | --- |
| `headers` | 附加请求头对象 |
| `contentType` | 请求体类型（传对象时默认 `application/json; charset=utf-8`） |
| `body` | 已序列化的请求体字符串 |
| `responseType` | `"base64"`（等价 `"binary"`/`"bytes"`）：响应体按原始字节读，返回 `bodyBase64` + `bodyBytes`（验证码图片用） |
| `followRedirects` | 默认 `true`；`false` 时不跟随 3xx，返回原始重定向响应（`headers.location` + `headers["set-cookie"]`） |

### http 的响应

```js
{
  ok: true,          // 2xx
  status: 200,       // 网络层失败时为 0
  headers: { "content-type": "...", "set-cookie": "..." },  // 同名多值 ", " 连接，名字小写
  body: "<html>…",   // 文本模式；二进制模式为 ""
  error: null,       // 网络失败时是原因字符串
  bodyBase64: null,  // responseType:"base64" 时
  bodyBytes: null    // responseType:"base64" 时
}
```

超时：单请求 30 秒、连接 15 秒。同一次同步内 **Cookie 自动保持**（登录 → 拉数据），
同步结束丢弃；跨同步要自己 `storage.set`。

### KsoupNode（全部同步，不是 Promise）

| 成员 | 说明 |
| --- | --- |
| `tagName` / `text` / `html` / `outerHtml` / `id` | 标签名 / 纯文本 / innerHTML / 含自身标签片段 / id 属性 |
| `attr(name)` / `hasAttr(name)` | 读属性（无则 `""`）/ 是否存在 |
| `parent` / `children` | 父元素（可能为 null）/ 子元素数组 |
| `select(sel)` / `selectFirst(sel)` | CSS 选择器（完整 jsoup 语法）/ 第一个匹配或 null |

节点只在**本次同步内**有效，别存进 `storage`。

### captcha（⚠️ 适用范围有限）

**只适用于强智教务系统 80×40、4 字符的验证码**（模板匹配，34 个模板，
字符集 `1-9a-z` 不含 `o`）。其他尺寸/字体/字符集的验证码识别率极低甚至全错。

| 项 | 说明 |
| --- | --- |
| 入参 | 图片二进制：纯 base64 或 `data:image/jpeg;base64,…`（允许换行/空格） |
| 返回 | 4 位字符串，或 `null`（不抛异常） |
| 尺寸要求 | 宽 ≥ 78、高 ≥ 33（算法按 80 宽分块：`x = 4/22/40/58`，每块 20 列） |
| 日志 | 成功 `[info] captcha.recognize() → 1bmz`，失败 `[warn]` + 原因 |

宿主**没有**让用户手动输入验证码的能力，其他教务系统请改用「登录态复用」
（把 Cookie 存 `storage`），详见完整文档第 7 节。

---

## getCourses 返回的课程

| 字段 | 说明 |
| --- | --- |
| `name` | 必需，课程名 |
| `teacher` / `classroom` | 可选 |
| `dayIndex` | 0 = 周日、1 = 周一 … 6 = 周六 |
| `lessonStartIndex` | 起始节次，0 = 一天的第一节 |
| `lessonCount` | 占用节数，默认 2，至少 1 |
| `weekIndices` | 教学周，**0 基**（0 = 第 1 教学周） |
| `weeks` | 教学周，**1 基**（教务常见写法），宿主自动减一；与 `weekIndices` 同时出现时后者优先 |
| `fixedDate` + `fixedStartMinute` + `fixedDurationMinutes` | 固定课程（绝对日期 + 钟点），三个必须齐全 |

校验不过的课程会被**单独忽略**并写进同步日志（不会让整次同步失败）；
单次最多 2000 门，超出忽略并告警。详见完整文档第 5.5 节。

---

## 限制

| 限制 | 值 |
| --- | --- |
| 墙钟超时（网络 + 执行） | 90 秒 |
| JS 执行时间（CPU） | 20 秒 |
| 单请求超时 | 30 秒（连接 15 秒） |
| 课程数量 | 2000 门 |
| 同步日志 | 400 条 × 4000 字符（总量约 60000 字符） |
| 插件包 | 单文件 ≤ 2 MB、解压总量 ≤ 8 MB |
| HTTP 日志 | 请求体/响应体/响应头各 4000 字符 |

---

## JS 能力

**有**：QuickJS 的 ES2025 绝大部分（`JSON`、`Promise`、`Map`/`Set`、`RegExp`、`Date`、
定型数组、`Proxy`、可选链、`async/await`、顶层 `await`、动态 `import()`）、
ES Module 的 `import`（相对路径解析到包内文件）。

**没有**（别用）：DOM、`fetch`/`XMLHttpRequest`、`localStorage`、
`setTimeout`/`setInterval`、`URL`/`URLSearchParams`、`TextEncoder`/`TextDecoder`、
`atob`/`btoa`、`Intl`、Node.js API、文件系统、UI 交互。
