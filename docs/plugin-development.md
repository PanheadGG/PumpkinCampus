# 南瓜校园 · 课表插件开发文档

> 这份文档是插件开发的**唯一完整参考**：包结构、manifest 字段、`getCourses` 契约、
> 全部宿主 API、验证码识别的适用范围、打包安装、调试与常见问题。
> 只想查 API 签名可以看 [plugin-api.md](plugin-api.md)（速查表），
> 想直接改代码可以从 [plugin-template/](plugin-template/) 开始。

**目录**

1. [插件是什么：能力与边界](#1-插件是什么能力与边界)
2. [运行模型](#2-运行模型)
3. [插件包结构](#3-插件包结构)
4. [manifest.json 参考](#4-manifestjson-参考)
5. [入口与 getCourses 契约](#5-入口与-getcourses-契约)
6. [宿主 API 完整参考](#6-宿主-api-完整参考)
7. [验证码识别：只适用于强智教务系统 80×40](#7-验证码识别只适用于强智教务系统-8040)
8. [实战：从零写一个教务插件](#8-实战从零写一个教务插件)
9. [调试与排查](#9-调试与排查)
10. [用户侧流程：安装、同步、只读层与分享](#10-用户侧流程安装同步只读层与分享)
11. [限制与安全](#11-限制与安全)
12. [常见问题](#12-常见问题)
13. [附录 A：最小可安装插件](#附录-a最小可安装插件)
14. [附录 B：API 速查表](#附录-bapi-速查表)

---

## 1. 插件是什么：能力与边界

插件是一段**跑在 App 内置 QuickJS 引擎里的 JavaScript**，负责「登录学校教务系统 →
抓课表 → 解析 → 返回课程数组」。App 负责把返回的课程画进课表。

| 能做 | 说明 |
| --- | --- |
| 发 HTTP 请求 | `http.*`（Ktor 桥接），支持自定义请求头、表单/JSON 请求体、Cookie 会话、二进制响应、手动跟跳 302 |
| 解析 HTML | `ksoup.*`（KSoup/jsoup 桥接），完整 CSS 选择器 |
| 存自己的数据 | `storage.*`（KV，**按「插件 + 课表」隔离**），存登录 Cookie、学期列表、缓存等 |
| 识别验证码 | `captcha.recognize()` —— **只适用于强智教务系统 80×40 的 4 字符验证码**，见 [第 7 节](#7-验证码识别只适用于强智教务系统-8040) |
| 打日志 | `console.*`，输出进入 App 内的「同步日志」，可一键复制 |
| 用现代 JS | QuickJS 支持 ES2025 绝大部分语法：`class`、解构、可选链、`async/await`、顶层 `await`、`Map`/`Set`、定型数组、`Proxy` 等 |

| 不能做 | 说明 |
| --- | --- |
| 访问文件系统 | 没有 `require`/`fs`，插件只能读**自己包内**的文件（通过 `import`） |
| 访问 App 内部状态 | 拿不到课表、课程、设置；只能通过 `ctx` 拿到宿主给的那几个字段 |
| 与用户交互 | 没有弹窗/输入框 API：**无法让用户手动输入验证码**，也不能弹提示 |
| 修改课表 | 插件课程是只读层，用户要改必须「转换为自定义课程」（见 [第 10 节](#10-用户侧流程安装同步只读层与分享)） |
| 用 DOM / `fetch` / `setTimeout` | 没有 DOM、`fetch`、`XMLHttpRequest`、`localStorage`、计时器、`URLSearchParams`、`TextEncoder`、`atob/btoa`、`Intl` |
| 常驻后台 | 每次同步新建一个运行时，跑完即销毁；插件之间不共享全局，也没有定时任务 |

---

## 2. 运行模型

```
  my-plugin.zip
        │  用户在「设置 → 教务系统插件」安装（全局安装，所有课表共享）
        ▼
  <数据目录>/plugins/<插件id>/          manifest.json · index.js · utils/…
        │
        │  用户在某个课表里选中该插件并填配置（配置值按课表保存）
        ▼
  触发同步（自动 / 下拉刷新 / 手动点「立即同步课表」）
        │
        ▼
  ┌──────────────── 本次同步专用的 QuickJS 运行时 ────────────────┐
  │  prelude 注入：console · ksoup · storage · http · captcha     │
  │  import 入口 index.js（ES Module）                            │
  │  await getCourses(ctx)  ──▶  课程 JSON 数组                   │
  └───────────────────────────────────────────────────────────────┘
        │  宿主校验 / 归一化（0 基周次、节次、星期…）
        ▼
  plugin-schedule.json（按课表隔离，只读层）
        │
        ▼
  与用户自己的自定义课程叠加展示在课表 / 日程页
```

> 顺带一提：插件包根目录还可以放 `timetables.json` **推荐本校作息时间表**
> （纯数据、不执行代码、不需要同步，见 [3.1](#31-插件推荐时间表timetablesjson)）。

一次同步里：

1. **新建运行时**并注入全局 API（上次同步的全局状态、`ksoup` 节点、Cookie 罐都不复用）；
2. 执行 `index.js`（连同它 `import` 的包内模块）；
3. 调用入口导出的 `getCourses(ctx)`，把返回值 `JSON.stringify` 交回宿主；
4. 宿主逐门校验，合法的进课表，不合法的写进同步日志；
5. 运行时销毁，HTTP 客户端与 Cookie 罐一起释放。

> 因为运行时每次都是新的，**想跨同步保留登录态必须自己 `storage.set`**
> （见 [6.4 storage](#64-storage--kv-存储按插件--课表隔离)）。

---

## 3. 插件包结构

```
my-plugin/                     ← 整个目录压成 zip
├── manifest.json              # 必需：元数据 + 配置项声明
├── index.js                   # 必需：ES Module 入口，必须导出 getCourses
├── timetables.json            # 可选：推荐本校作息时间表（纯数据，见 3.1）
├── utils/                     # 可选：自己的模块，用相对路径 import
│   ├── client.js
│   └── schedule.js
└── README.md                  # 推荐：使用说明（安装后保留在插件目录里）
```

打包与安装规则（宿主在安装时执行）：

| 规则 | 说明 |
| --- | --- |
| manifest 位置 | 可以在 zip 根目录，也可以套**一层**顶层目录（如 `my-plugin/manifest.json`）——宿主自动剥掉共同前缀，取层级最浅的那个 |
| 忽略项 | 以 `.` 开头的文件/目录（`.DS_Store` 等）与目录项不写入安装目录；`__MACOSX/` 里的文件不会被当成 manifest |
| 单文件上限 | 2 MB |
| 解压总量上限 | 8 MB |
| 路径安全 | 不允许 `..`、绝对路径、Windows 盘符（`C:`）——含这些的条目直接丢弃 |
| 覆盖升级 | 同 `id` 再次安装 = 删旧目录再写入（配置值与 KV 不受影响） |
| 入口存在性 | `manifest.entry` 指向的文件必须真的在包里，否则拒绝安装 |
| 宿主版本 | `minHostVersionCode` > App 的 versionCode 时拒绝安装 |

**快速开始**：把 [plugin-template/](plugin-template/) 复制成你的插件目录，
改 `manifest.json`（`id`/`name`/`schoolName`/`configs`）和 `index.js`（换成你学校的实现），
然后按 [第 8.8 节](#88-打包与安装) 打包安装。

### 3.1 插件推荐时间表：`timetables.json`

插件包**根目录**放一个 `timetables.json`，就能给用户推荐本校的作息时间表：

```
课表设置 → 上课时间 → 插件推荐时间表 → 点「使用」
```

> 同一页的「全局时间表」分组（内置「默认作息」+「设置 → 全局课表设置」里的默认时间表）
> 用的是**同一套「使用 = 复制一份进本课表」逻辑**，插件推荐表和它并列展示。

```jsonc
[
  {
    "name": "三峡大学作息时间",   // 必填（为空时宿主用「插件推荐时间表 N」）
    "description": "本部校区",    // 可选，只在日志里出现
    "slots": [                   // 必填，至少 1 节（也接受别名 periods）
      { "start": "08:00", "end": "08:45" },
      { "start": "08:55", "end": "09:40" }
    ]
  }
]
```

顶层写成数组，或者包一层 `{ "timetables": [ … ] }` 都行。

| 规则 | 行为 |
| --- | --- |
| 文件不存在 | 正常，页面提示「插件没有推荐时间表」 |
| 纯数据 | **不执行任何插件代码**，选中插件后宿主直接读文件，**不需要先同步课表** |
| `start` / `end` 是 `HH:mm`（`H:mm` 也会补零） | 非法或 `end <= start` 的节次被单独丢弃，其余照常可用 |
| 最多 5 张表、每张最多 40 节 | 超出截断 |
| 节次乱序 | 宿主按开始时间升序排好 |
| 格式整体不对 | 整份忽略（页面按「没有推荐」提示），不会影响插件本身 |
| 用户点「使用」 | 复制成本课表的一张时间表（`fromDefaults = false`），之后随便改；插件不会覆盖它 |

> 推荐时间表是**建议**不是强制：宿主只负责展示，是否套用完全由用户决定；
> 同一张表（同名 + 同节次）重复点「使用」不会添加第二张。

---

## 4. manifest.json 参考

```jsonc
{
  "id": "com.example.university.course-plugin", // 必需，全局唯一，= 安装目录名
  "name": "某大学教务课表适配器",                  // 必需，展示名
  "version": "1.0.0",
  "entry": "index.js",
  "minHostVersionCode": 1,
  "schoolName": "某大学",
  "description": "支持新教务系统（jwxt 版本）的课表导入",
  "author": "your-name",
  "updateTime": "2026-09-26",
  "configs": [
    { "title": "教务系统地址", "key": "base_url", "description": "例如 https://jwxt.example.edu.cn",
      "type": "string", "default": "https://jwxt.example.edu.cn",
      "options": [
        { "name": "https://jwxt.example.edu.cn", "value": "https://jwxt.example.edu.cn" },
        { "name": "http://10.0.0.2:8080", "value": "http://10.0.0.2:8080" }
      ] },
    { "title": "学号", "key": "username", "type": "string", "default": null },
    { "title": "密码", "key": "password", "type": "password", "default": null },
    { "title": "学期 ID", "key": "term_id", "type": "string", "default": "" },
    { "title": "包含实验课表", "key": "include_experiment", "type": "bool", "default": true },
    { "title": "验证码重试次数", "key": "captcha_retries", "type": "int", "default": 3 },
    { "title": "登录方式", "key": "login_type", "type": "select", "default": "local",
      "options": [
        { "name": "本地登录", "value": "local" },
        { "name": "统一身份认证平台登录", "value": "cas" }
      ] }
  ]
}
```

### 4.1 字段表

| 字段 | 必需 | 默认 | 说明 |
| --- | --- | --- | --- |
| `id` | ✅ | — | 字母/数字开头，只允许 `[A-Za-z0-9._-]`；决定安装目录 `plugins/<id>/`；改 id = 变成另一个插件（配置与选择都不会跟过去） |
| `name` | ✅ | — | 展示名称，出现在插件列表与课表设置里 |
| `version` | 建议 | `1.0.0` | 纯展示；同 id 重装即覆盖（可升可降） |
| `entry` | 建议 | `index.js` | 入口 ES Module，包内相对路径；只允许字母/数字/`_ - . /`，不能含 `..` |
| `minHostVersionCode` | 建议 | `1` | 需要的最小宿主 versionCode；不满足时**拒绝安装**，已安装的会在同步时报错 |
| `schoolName` | 可选 | `""` | 学校名（`ctx.schoolName`） |
| `description` | 可选 | `""` | 一句话说明 |
| `author` | 可选 | `""` | 作者 |
| `updateTime` | 可选 | `""` | 更新日期（展示用） |
| `configs` | 可选 | `[]` | 配置项声明，见下 |

解析行为（宽松，避免一处笔误整个包装不上）：

- **允许 `//` 与 `/* */` 注释**（解析前剥离）；别把注释放进字符串值里；
- 未知字段忽略（`ignoreUnknownKeys`）；
- `type` 未知值按 `string` 处理；`configs[].options` 不是数组时按空数组处理，
  数组里不是对象的项按「名称 = 值」兼容（见 [4.2](#42-configs配置项声明)）。

### 4.2 configs：配置项声明

每个配置项分**声明**（manifest 里的 `default`）与**值**（每个课表各存一份）。

| 子字段 | 说明 |
| --- | --- |
| `title` | 配置项标题；**同时作为配置 key**（除非显式给 `key`） |
| `key` | 显式 key；`ctx.config` 与课表存档都按它存取。**建议显式写**（`title` 是文案，改了会换 key） |
| `description` | 展示在配置弹层里的说明，可为 null |
| `type` | `string`（默认）/ `password` / `int` / `bool` / `select`（`integer`/`number` → int，`boolean` → bool，`option`/`enum` → select） |
| `default` | `null` / 字符串 / 整数 / 布尔；该课表没保存过值时用它。`select` 的默认值应当是某个候选值的 `value` |
| `options` | 候选值数组（`{ "name": …, "value": … }` 对象），见下 |

类型对应的 UI 与取值：

- `string`：普通输入框，值是字符串；
- `password`：掩码输入框（`••••••`），值是字符串；**值加密保存**在系统加密存储里
  （KVault：Android Keystore / iOS Keychain），不会明文写进课表存档；
- `int`：只能输入数字，值是整数（解析不出时为 `0`）；
- `bool`：开关，值是布尔；
- `select`：**只能从 `options` 里选一个**（配置弹层里没有输入框，选完点「保存」），
  值是选中项的 `value`；适合「互斥的几种模式」，如登录方式 `local` / `cas`。

**候选值 `options`**：每项是一个对象，`name` 是**展示名称**，`value` 是**实际值**
（写进课表配置、插件在 `ctx.config` 里拿到的就是它）：

```json
{ "title": "学期 ID", "key": "term_id", "type": "string", "default": "",
  "options": [
    { "name": "2025-2026-1", "value": "2025-2026-1" },
    { "name": "2025-2026-2", "value": "2025-2026-2" }
  ] }
```

- 非 `select` 类型：候选值在配置弹层里排成一排按钮，点一下填入，
  **同时仍然允许手动输入**（候选只是建议，宿主不校验）——适合「常见取值 + 允许自定义」；
- `select` 类型：候选值就是**全部可选项**，用户只能点选，不能自己输入；
- 解析很宽松：对象取 `name` / `value`（缺 `name` 时用 `value` 当展示名，缺 `value` 时名称兼作值）；
  字符串/数字/布尔按「名称 = 值」处理（早期 `"2024-2025-1"` 这种纯字符串写法仍然可用）；
  `null`、数组与空串丢弃；整个字段不是数组时按空列表处理。

> `select` 的值由宿主**原样**放进 `ctx.config`（字符串）。插件升级后用户存的旧值可能已不在候选里，
> 所以插件脚本要对未知值兜底：`plugins/ctgu` 的 `login_type` 就是识别不出时回落默认的「本地登录」。

> **同一个 `configs` 里 key 不要重复**：重复时后面的会覆盖前面的（宿主不做去重校验）。

### 4.3 配置值合并规则

宿主把「课表保存的值」和「manifest 默认值」合并成 `ctx.config`，优先级：

1. **当前课表保存的值**（用户填的）；
2. manifest 的 `default`；
3. 类型零值：`string`/`password`/`select` → `""`、`int` → `0`、`bool` → `false`。

所以 `ctx.config` 里的每个 key **一定存在**，插件不用判 `undefined`，
只需判空串（例如「学期 ID 留空 = 用教务系统当前学期」）。

---

## 5. 入口与 getCourses 契约

### 5.1 导出要求

入口必须是 **ES Module**，并**命名导出** `getCourses`：

```js
export async function getCourses(ctx) {
  // 登录、抓取、解析……
  return [ /* 课程对象数组 */ ];
}
```

宿主实际执行的是这段引导代码（决定了几个硬性要求）：

```js
import { getCourses } from "./index.js";        // ← 必须是命名导出
const ctx = JSON.parse(globalThis.__ctx());
const courses = await getCourses(ctx);
globalThis.__result(JSON.stringify(courses === undefined ? null : courses));
```

- `export default` ✗、只写 `function getCourses` ✗ —— 都会报
  `does not provide an export named 'getCourses'`；
- 返回值必须能 `JSON.stringify`（**不要返回函数、循环引用、`Map`**）；
- 返回值不是数组（或为 `null`/`undefined`）→ 本次同步失败；
- 函数可以是 `async`，也可以同步返回数组；内部可用顶层 `await`；
- 抛异常 → 同步失败，错误信息带 `文件:行号` 与堆栈。

### 5.2 模块解析

相对导入按包内路径解析，并带常见回退（下面三种写法都能命中 `utils/util.js`，**选一种**）：

```js
import { parseWeekRanges } from "./utils/util.js";   // 标准写法
```

```js
import { parseWeekRanges } from "./utils/util";      // 自动补 .js
import parseSchedule from "./utils";                 // 自动补 /index.js（默认导出）
```

`..` 越界、绝对路径在安装阶段就被丢弃，所以不会加载到包外文件。

### 5.3 ctx 参数

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `pluginId` | string | manifest 的 `id` |
| `pluginName` | string | manifest 的 `name` |
| `pluginVersion` | string | manifest 的 `version` |
| `schoolName` | string | manifest 的 `schoolName` |
| `hostVersionCode` | number | 宿主 App 的 versionCode（可据此做能力判断） |
| `sdk` | string | 恒为 `"pumpkincampus-plugin"`，用于探测宿主 |
| `schedule.id` / `schedule.name` | string | 当前课表（`storage` 按 `id` 隔离） |
| `config` | object | 已合并的配置（见 [4.3](#43-配置值合并规则)），值类型已按 `type` 转好 |

### 5.4 返回的课程 JSON

每个元素是一门课程，字段与应用内课程模型一致：

| 字段 | 类型 | 必需 | 说明 |
| --- | --- | --- | --- |
| `name` | string | ✅ | 课程名，不能为空 |
| `teacher` | string | | 授课教师 |
| `classroom` | string | | 上课地点 |
| `dayIndex` | number | ✅（普通课程） | 星期：**0 = 周日**、1 = 周一 … 6 = 周六 |
| `lessonStartIndex` | number | ✅（普通课程） | 起始节次：**0 = 一天的第一节** |
| `lessonCount` | number | | 占用节数，默认 2，至少 1 |
| `weekIndices` | number[] | ✅（普通课程） | 教学周，**0 基**（0 = 第 1 教学周） |
| `weeks` | number[] | 替代写法 | 教学周，**1 基**（教务系统常见写法），宿主自动减一；两者同时出现时以 `weekIndices` 为准 |
| `fixedDate` | string | 固定课程 | `"yyyy-MM-dd"`；给了它即视为**固定课程**（按绝对日期 + 钟表时间排课） |
| `fixedStartMinute` | number | 固定课程 | 当天 0 点起的分钟数（`8:00` → `480`） |
| `fixedDurationMinutes` | number | 固定课程 | 持续分钟数 |

示例：

```js
return [
  {
    name: "高等数学",
    teacher: "张三",
    classroom: "教一楼 101",
    dayIndex: 1,              // 周一
    lessonStartIndex: 0,      // 第一节
    lessonCount: 2,           // 连上两节
    weekIndices: [0, 1, 2, 3, 5]   // 第 1-4、6 教学周
  },
  {
    name: "期末考试",
    classroom: "A201",
    fixedDate: "2026-01-12",       // 固定课程：只在这一天
    fixedStartMinute: 9 * 60,      // 09:00
    fixedDurationMinutes: 120      // 两小时
  }
];
```

固定课程的额外行为（宿主侧）：只出现在 `fixedDate` 所在的教学周，
`dayIndex` 由日期推导，绘制位置按真实钟点定位、高度按持续时长换算，
**不受作息时间表（课次时间）调整影响**。

### 5.5 校验规则

不满足的课程**不会导致整次同步失败**，而是被单独忽略并写进同步日志：

| 规则 | 忽略原因（日志原文） |
| --- | --- |
| `name` 不能为空 | `name 为空` |
| 普通课程：`dayIndex ∈ 0..6` | `dayIndex 需要在 0(周日)~6(周六) 之间` |
| 普通课程：`lessonStartIndex ≥ 0` | `lessonStartIndex 不能为负（0 = 第一节）` |
| 普通课程：`lessonCount ≥ 1` | `lessonCount 至少为 1` |
| 普通课程：`weekIndices` 非空 | `weekIndices 为空（可用 weeks: [1,2,...] 1 基写法）` |
| 普通课程：`weekIndices` 不为负 | `weekIndices 不能为负（0 = 第 1 教学周）` |
| 固定课程：三个 `fixed*` 字段齐全 | `fixedDate 存在但缺少 fixedStartMinute / fixedDurationMinutes` |
| 元素必须是对象、字段类型正确 | `第 N 门课程解析失败/字段不合法：…` |
| 单次最多 2000 门 | `课程数量超过上限 2000，多余的已忽略` |

日志示例：

```
[warn] 第 3 门课程「大学物理」被忽略：dayIndex 需要在 0(周日)~6(周六) 之间
[info] getCourses 返回 42 门课程（已通过宿主校验）
```

---

## 6. 宿主 API 完整参考

所有全局 API 在入口执行前就已就绪，直接用，不需要 `import`。

### 6.1 console —— 日志

```js
console.log("开始同步", ctx.schoolName, { term });   // 任意个参数
console.info(...); console.warn(...); console.error(...); console.debug(...);
```

- 对象/数组会 `JSON.stringify` 后拼进同一行；
- 输出进入**同步日志**（课表设置 → 教务系统插件 → 查看同步日志，可一键复制整份）；
- 上限：最多 400 条、每条 4000 字符、总量约 60000 字符，**只保留最近一次同步**的输出；
- 超出总量后会补一条 `[warn] 日志已达上限…`，不会静默丢失。

### 6.2 http —— 网络请求

```js
const resp = await http.get(url, opts?);
const resp = await http.head(url, opts?);
const resp = await http.post(url, body?, opts?);    // body 传对象 → 自动 JSON 序列化
const resp = await http.put(url, body?, opts?);
const resp = await http.delete(url, body?, opts?);
const resp = await http.request(opts);              // 底层方法，opts 需含 url / method
const text = await http.text(url, opts?);           // = (await http.get(url, opts)).body
const obj  = await http.json(url, opts?);           // body JSON.parse，空 body 返回 null
```

`opts`：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `headers` | object | 附加请求头，如 `{ Referer: "...", "User-Agent": "..." }` |
| `contentType` | string | 请求体类型；`post/put/delete` 传对象时默认 `application/json; charset=utf-8` |
| `body` | string | 已序列化的请求体（传对象时宿主自动处理，一般不用手写） |
| `responseType` | string | `"base64"`（等价 `"binary"` / `"bytes"`）：响应体按**原始字节**读，返回 `bodyBase64` + `bodyBytes`——验证码图片等二进制响应用它 |
| `followRedirects` | bool | 默认 `true`。传 `false` 时不跟随 3xx，直接返回原始重定向响应（`headers.location` + `headers["set-cookie"]`），教务系统登录常需要 |

响应对象：

```js
{
  ok: true,          // 2xx 为 true
  status: 200,       // 网络层失败时为 0
  headers: { "content-type": "text/html; charset=utf-8", "set-cookie": "JSESSIONID=abc; Path=/" },
                     // 同名多值以 ", " 连接；header 名统一小写
  body: "<html>…",   // 文本模式下的响应体；二进制模式下为 ""
  error: null,       // 网络失败时是原因字符串
  bodyBase64: null,  // responseType:"base64" 时是 base64 文本
  bodyBytes: null    // responseType:"base64" 时是原始字节数
}
```

要点：

- **网络失败不抛异常**：超时、DNS、连接被拒都会 resolve 成
  `{ ok:false, status:0, body:"", error:"…" }`。`try/catch` 抓不到，
  必须检查 `resp.ok`（或 `resp.status`），否则会把空页面当数据解析；
- **Cookie 会话在一次同步内自动保持**：登录后的 `Set-Cookie` 由 Cookie 罐自动带上，
  同步结束随运行时丢弃；想跨同步保留，把凭据 `storage.set` 起来；
- **超时**：单请求 30 秒、连接 15 秒（超时表现为 `error`，不是异常）；
- **重定向**：GET 默认跟随；`POST` 等非安全方法 Ktor 默认不跟随，
  需要看到 302 时显式传 `followRedirects: false`；
- **宿主自动记录原始请求/响应**（见 [9.1](#91-同步日志里有什么)），不用自己打日志。

表单登录（QuickJS **没有** `URLSearchParams`，自己拼）：

```js
const body = "username=" + encodeURIComponent(user) +
             "&password=" + encodeURIComponent(pass);
const resp = await http.post(base + "/login", body, {
  contentType: "application/x-www-form-urlencoded"
});
```

取验证码图片：

```js
const img = await http.get(captchaUrl, { responseType: "base64" });
if (!img.ok) throw new Error("取验证码失败：" + img.error);
// img.bodyBase64 交给 captcha.recognize（见第 7 节）
```

### 6.3 ksoup —— HTML 解析

```js
const doc = ksoup.parse(html);            // 返回根节点（body）的 KsoupNode
ksoup.clear();                            // 释放本次同步注册的所有节点（一般不用手动调）
```

`KsoupNode` 的成员（全部同步，不是 Promise）：

| 成员 | 类型 | 说明 |
| --- | --- | --- |
| `tagName` | string | 标签名，如 `"td"` |
| `text` | string | 纯文本（空白已合并） |
| `html` | string | innerHTML |
| `outerHtml` | string | 含自身标签的片段 |
| `id` | string | `id` 属性值，无则 `""` |
| `attr(name)` | string | 读属性，无则 `""` |
| `hasAttr(name)` | boolean | 是否有该属性 |
| `parent` | KsoupNode \| null | 父元素 |
| `children` | KsoupNode[] | 子**元素**节点（不含文本节点） |
| `select(selector)` | KsoupNode[] | CSS 选择器，支持完整 jsoup 语法 |
| `selectFirst(selector)` | KsoupNode \| null | 第一个匹配 |

```js
const rows = doc.select("table#kbtable tbody tr");
for (const row of rows) {
  const name = row.selectFirst("td.course")?.text?.trim() ?? "";
  const room = row.selectFirst("td.room")?.attr("data-room") ?? "";
}
```

注意：

- 节点只在**本次同步内有效**（宿主用 id 注册表管理），**不要把节点存进 `storage`**；
- 解析容错与 jsoup 一致（脏 HTML 也能解析）。

### 6.4 storage —— KV 存储（按插件 + 课表隔离）

```js
storage.set("session", { cookie: "JSESSIONID=…", savedAt: Date.now() });
const session = storage.get("session");   // 对象 | null（key 不存在）
storage.has("session");                   // boolean
storage.keys();                           // string[]（已排序）
storage.remove("session");
storage.clear();                          // 清空本插件在当前课表下的全部数据
```

- 值可以是任意 **JSON 可序列化**的数据（对象/数组/字符串/数字/布尔/`null`）；
  内部以 JSON 文本保存，读回来是**新的对象**（不是同一个引用）；
- **隔离维度是「插件 id + 课表 id」**：A 课表写入的数据 B 课表读不到，
  同一个课表换插件也读不到；
- 落盘位置：`plugin-kv/<插件id>/<课表id>.json`；
- 卸载插件会删除该插件**所有课表**的 KV；但**各课表填写的配置值会保留**
  （重新安装后无缝恢复）；
- 没有硬性容量限制，但它会写进本机存档——只存必要数据（凭据、缓存、学期列表）。

### 6.5 captcha —— 验证码识别

```js
const code = captcha.recognize(imageBase64);   // "1bmz" | null
const code2 = recognizeCaptcha(imageBase64);   // 等价写法（全局方法）
```

⚠️ **只适用于强智教务系统那种 80×40 的 4 字符验证码**，
细节与适用范围见下一节。

### 6.6 JS 内建能力

QuickJS 支持 ES2025 的绝大部分：`JSON`、`Promise`、`Map`/`Set`、`RegExp`、`Date`、
`Math`、`BigInt`、`Symbol`、`Proxy`、`WeakMap`/`WeakRef`、定型数组（`Uint8Array` 等）、
`encodeURIComponent`/`decodeURIComponent`、`Object`/`Array` 全部方法、可选链、空值合并。

宿主额外提供：`console`、`http`、`ksoup`、`storage`、`captcha`、`recognizeCaptcha`、
ES Module 的 `import`。

**没有**（别用）：Node.js API、DOM、`fetch`/`XMLHttpRequest`、`localStorage`、
`setTimeout`/`setInterval`、`URL`/`URLSearchParams`、`TextEncoder`/`TextDecoder`、
`atob`/`btoa`、`Intl`。缺的能力用宿主桥接替代：
网络 → `http`，表单编码 → `encodeURIComponent` 手工拼，
HTML → `ksoup`，二进制 → `http` 的 `responseType: "base64"`。

---

## 7. 验证码识别：只适用于强智教务系统 80×40

> **这是本宿主验证码识别的硬限制，务必先读这一节。**
>
> `captcha.recognize()` / `recognizeCaptcha()` 内置的是**强智教务系统（QZ）**
> 那一种固定尺寸 **80×40**、**4 个字符**的验证码的模板匹配算法
> （移植自 Pumpkin-Toolkit 的 `QZRecognize`，原始项目
> <https://github.com/shuo747/QZRecognize>）。
>
> **换任何别的教务系统**（不同尺寸、不同字体、不同字符集、带干扰线/噪点/扭曲），
> 识别率会很低甚至全错 —— 那种情况下不要依赖它。

算法做了什么（决定了它的适用范围）：

| 步骤 | 具体参数 |
| --- | --- |
| 灰度 + 二值化 | `avg = (R+G+B)/3`，`avg < 192` 记为前景（黑），否则背景（白） |
| 裁剪 | 取 `y = 9` 起的 **24 行**（对应原始 `getSubimage(0, 9, 80, 24)`） |
| 分割 | 按 `x = 4 / 22 / 40 / 58`、每块 **20 列**切成 **4 块**（对应 4 个字符） |
| 识别 | 每块与 34 个训练模板逐一比对命中率，取最高者作为该位字符 |
| 字符集 | `1-9` + `a-z`（**不含字母 `o`**），共 34 个字符 |

由此产生的硬性要求与行为：

| 情形 | 结果 |
| --- | --- |
| 图片宽度 < 78 或高度 < 33 | 返回 `null`（`[warn] 识别失败：图片无法解码或尺寸不符`） |
| 强智 80×40 验证码 | 正常返回 4 位字符串，如 `"1bmz"` |
| 非 80 宽的验证码 | 分块位置对不上，结果基本无意义 |
| 不是合法 base64 / 解码失败 | 返回 `null`（`[warn] 图片不是合法 base64`） |
| 传入空串 | 返回 `null`（`[warn] captcha.recognize() 没有收到图片数据`） |

入参与返回：

| 参数 | 类型 | 说明 |
| --- | --- | --- |
| `imageBase64` | string | 图片二进制：**纯 base64** 或 `data:image/jpeg;base64,…` 都行；允许换行/空格 |

- 返回 **4 位字符串**（成功）或 **`null`**（失败）——**不会抛异常**；
- 每次调用都会写日志：成功 `[info] captcha.recognize() → 1bmz（图片 1234 字节）`，
  失败 `[warn]` + 原因，便于判断是「没拿到图片」还是「尺寸不对」；
- 已知上游行为：比对只看模板的前景像素，**宽字形（如 `m`）落在靠前的分块时
  可能被判成相近字符**——这是原算法特性，不是宿主 bug。

### 其他教务系统怎么办

宿主**没有**「把验证码显示给用户、让用户输入」的能力（插件不能与 UI 交互），所以：

1. **优先复用登录态**：第一次登录后把 Cookie `storage.set` 存起来，
   下次同步先用它探测，失效了再登录（`plugins/usc` 就是这么做的）；
2. **找免验证码的入口**：很多教务系统的课表页在登录态下可直接访问，
   或提供不校验验证码的接口；
3. 如果某系统**每次都必须过验证码且算法不匹配**，那它目前无法做成插件 ——
   可以先让用户用 App 的「导入课程 / 分享口令」流程，或给宿主提需求扩展识别算法。

正确用法示例（配合 `http` 的二进制响应）：

```js
const img = await http.get(base + "/verifycode.servlet?t=" + Date.now(),
                           { responseType: "base64" });
if (!img.ok) throw new Error("取验证码失败：" + img.error);
const code = captcha.recognize(img.bodyBase64);
if (!code) throw new Error("验证码识别失败（本识别仅支持强智教务系统 80×40 验证码）");
```

---

## 8. 实战：从零写一个教务插件

### 8.1 先跑通链路（最小可用）

```js
// index.js —— 不联网，先确认「安装 → 选择 → 同步 → 出现在课表里」整条链路
export async function getCourses(ctx) {
  console.log("ctx =", ctx);
  return [
    { name: "调试课程", dayIndex: 1, lessonStartIndex: 0, lessonCount: 2, weeks: [1, 2] }
  ];
}
```

装上去同步一次，课表里出现「调试课程」就说明环境没问题（`weeks: [1,2]` 是 1 基写法，
等价于 `weekIndices: [0,1]`）。

### 8.2 登录

按教务系统的类型选一种：

```js
// A. 表单登录（application/x-www-form-urlencoded）
const body = "username=" + encodeURIComponent(user) + "&password=" + encodeURIComponent(pass);
const login = await http.post(base + "/login", body, {
  contentType: "application/x-www-form-urlencoded"
});
if (!login.ok) throw new Error("登录请求失败：HTTP " + login.status);
if (login.body.includes("密码错误")) throw new Error("学号或密码错误");  // 200 + 页面提示

// B. JSON 登录
const login2 = await http.post(base + "/api/login", { username: user, password: pass });
// 传对象时宿主自动 JSON 序列化并设置 application/json

// C. 302 跳转登录（正方 jsxsd 这类：真正凭证在跳转响应的 Set-Cookie 上）
const logon = await http.post(logonUrl, form, {
  headers: { Cookie: captchaCookie },
  contentType: "application/x-www-form-urlencoded",
  followRedirects: false                       // 关键：自己看 302
});
if (logon.status !== 302) throw new Error("登录失败：" + showMsg(logon.body));
const hop = await http.get(logon.headers["location"], {
  headers: { Cookie: captchaCookie },
  followRedirects: false                       // 这一步的 Set-Cookie 才是会话凭证
});
storage.set("session", {
  cookie: mergeCookies(captchaCookie, hop.headers["set-cookie"]),
  savedAt: Date.now()
});

// D. 前端加密登录（金智 jwapp / 很多新版教务：密码在前端加密后才提交）
//    做法：把教务系统前端自己的加密脚本一起打进插件包，import 进来直接用——
//    这样加密结果必然被服务器接受，也不用自己移植算法。
//    （plugins/JinZhi 就是这么做的：utils/des.js 就是站点原版 des.js，
//      只在文件末尾补了 `export const strEnc = globalThis.DES.strEnc;` 之类的导出）
import { strEnc, strEncSimple } from "./utils/des.js";
const token = randomToken(36);                 // 随机 36 位数字+小写字母
const login3 = await http.request({
  url: base + "/jwapp/sys/yjsrzfwapp/dbLogin/doDbLogin.do",
  method: "POST",
  headers: { Cookie: "GS_DBLOGIN_TOKEN=" + token },   // 加密用的 token 放进 Cookie
  body: buildMultipartBody([
    ["userId", strEncSimple(user)],
    ["password", strEnc(pass, token, user, "")]
  ], boundary),                                       // 宿主没有 multipart 帮手：自己拼边界
  contentType: "multipart/form-data; boundary=" + boundary
});
// 提示：字符串 body 可以配任意 content-type（宿主按 UTF-8 编码发送）
```

### 8.3 拉数据

```js
// A. 直接有 JSON 接口
const resp = await http.get(base + "/api/schedule?term=" + encodeURIComponent(term));
if (!resp.ok) throw new Error("拉取课表失败：HTTP " + resp.status);
const data = JSON.parse(resp.body);            // 或 await http.json(url)

// B. HTML 页面（ksoup）
const page = await http.get(base + "/students/schedule?term=" + encodeURIComponent(term));
if (!page.ok) throw new Error("拉取课表页面失败：HTTP " + page.status);
const doc = ksoup.parse(page.body);
const rows = doc.select("#kbtable tbody tr");  // ← 换成你学校的选择器
if (rows.length === 0) {
  console.warn("选择器没命中，页面开头：", page.body.slice(0, 200));  // 排查用
  return [];
}
```

### 8.4 解析成课程（三种转换最容易出错）

```js
// 1) 周次：教务常写 "1-8,10"（1 基）→ weekIndices（0 基）
//    模板里的 parseWeekRanges 已实现；也可以直接返回 weeks: [1,2,3] 让宿主转换
// 2) 节次：教务常写 "3-4 节" → lessonStartIndex = 2、lessonCount = 2
//    只有明确写了区间才按区间算，否则按「一大节 = 2 小节」估
// 3) 星期：周一~周日 → 0..6（注意周日是 0，不是 7）
const courses = [];
for (const row of rows) {
  const name = row.selectFirst("td.course")?.text?.trim();
  if (!name) continue;
  courses.push({
    name,
    teacher: row.selectFirst("td.teacher")?.text?.trim() ?? "",
    classroom: row.selectFirst("td.room")?.text?.trim() ?? "",
    dayIndex: parseDay(row.attr("data-day") ?? ""),          // 模板工具函数
    lessonStartIndex: parseInt(row.attr("data-start") ?? "1", 10) - 1,
    lessonCount: Math.max(1, parseInt(row.attr("data-len") ?? "2", 10)),
    weekIndices: parseWeekRanges(row.attr("data-weeks") ?? "") ?? []
  });
}
return courses;
```

### 8.5 缓存登录态（避免每次都登录）

```js
const SESSION_KEY = "session";

function loadSession(username, serverUrl) {
  const saved = storage.get(SESSION_KEY);
  if (!saved || !saved.cookie) return null;
  // 账号或地址变了，旧凭据作废
  if (saved.username !== username || saved.serverUrl !== serverUrl) {
    storage.remove(SESSION_KEY);
    return null;
  }
  return saved;
}

// 同步开头：先用旧 Cookie 探测；失败再登录，登录成功把新凭据写回
const saved = loadSession(username, serverUrl);
let html = saved ? await probeWithCookie(saved.cookie) : null;
if (!html) {
  await login();
  storage.set(SESSION_KEY, { cookie: currentCookie, username, serverUrl, savedAt: Date.now() });
  html = await fetchSchedule();
}
```

### 8.6 错误处理与日志

```js
// 1) 配置缺失 → 直接抛，用户能在「上次同步失败」里看到该怎么做
if (!username) throw new Error("请先在「课表设置 → 教务系统插件」里填写学号");

// 2) 网络失败 → 检查 resp.ok（不会抛异常）
if (!resp.ok) throw new Error("拉取失败：HTTP " + resp.status + "（" + resp.error + "）");

// 3) 解析异常 → 记下页面开头，方便对照选择器
console.warn("没解析出课程，页面开头：", page.body.slice(0, 200));

// 4) 关键节点打点：登录方式、学期、课程数
console.log("教务系统：" + base, "学期：" + (term || "当前学期"));
console.log("解析出 " + courses.length + " 门课程");
```

### 8.7 参考现成实现

| 插件 | 特点 | 适合参考 |
| --- | --- | --- |
| [plugins/QiangZhi/](../plugins/QiangZhi/) | 强智教务**通用版**：地址必须由用户填写，脚本检测空地址并提示；粘贴课表页地址会自动归一化 | 通用插件的做法（不给学校默认值、`configs[].default = null` + 脚本检测）、地址归一化、验证码登录 |
| [plugins/JinZhi/](../plugins/JinZhi/) | 金智教育 **jwapp 通用版**（以三峡大学为例）：`portal/index.do` 探测 302 → DES 加密登录（前端 `des.js`）→ 解锁课表权限 → 当前学期 → JSON 课表 | 探测式登录态判断、**把教务前端自己的加密脚本当模块 import**、multipart 表单、Cookie 跨同步复用、位图周次（`SKZC`）解析 |
| [plugins/usc/](../plugins/usc/) | 南华大学教务 jsxsd：验证码登录 + Cookie 复用 + 学生课表/实验课表合并 | 表单登录、手动跟跳 302、HTML 表格解析、会话缓存 |
| [plugins/nggjx-schedule/](../plugins/nggjx-schedule/) | 自建 JSON 接口：POST 登录 + JSON 课表 | JSON 接口、`weeks` 1 基写法 |

四者都在同目录提供了可直接安装的 zip
（`QiangZhi.zip` / `JinZhi.zip` / `usc.zip` / `nggjx-schedule.zip`）。

### 8.8 打包与安装

```powershell
# 在插件目录的上一级执行：生成 my-plugin.zip（manifest 在顶层目录内，宿主会自动剥）
Compress-Archive -Path my-plugin -DestinationPath my-plugin.zip -Force
```

macOS / Linux：`zip -r my-plugin.zip my-plugin`。

安装：App → **设置 → 教务系统插件 → 从 ZIP 文件安装插件**。
失败时会给出具体原因（zip 无法读取 / 缺 manifest / JSON 解析失败 / id 不合法 /
入口不存在 / 宿主版本不足 / 超出体积限制）。

---

## 9. 调试与排查

### 9.1 同步日志里有什么

课表页 **⋯ → 课表设置 → 教务系统插件 → 查看同步日志**（可一键复制整份）：

| 前缀 | 来源 |
| --- | --- |
| `[info]` | 执行环境概览（插件 id/版本、宿主 versionCode、课表、包内文件清单、生效配置、模块加载）、`getCourses` 返回课程数 |
| `[http]` | **宿主自动记录的原始请求/响应**：方法、URL、请求头、请求体、状态码、耗时、响应头、响应体 |
| `[log]` `[warn]` `[error]` `[debug]` | 插件自己的 `console.*` |
| `[warn]` | 课程校验告警（逐门说明被忽略的原因）、模块加载失败、日志超限 |

一次成功同步的日志大致长这样：

```
[info] 插件 com.example.usc v1.2.0 · 入口 index.js · 宿主 versionCode=1 · 课表「默认课表」(default)
[info] 包内文件：README.md, index.js, manifest.json, utils/qzclient.js, utils/schedule.js
[info] 生效配置：{"server_url":"http://…","username":"20230001","password":"***",…}
[info] 加载模块：./index.js
[log] 教务系统：http://61.187.179.66:8924/
[http] → POST http://…/Logon.do?method=logon
[http]   请求 Content-Type：application/x-www-form-urlencoded
[http]   请求体：username=20230001&password=***
[http] ← 302 Found · 0 字符 · 412ms
[http]   响应头：location: http://…/jsxsd/framework/xsMain.jsp, set-cookie: JSESSIONID=***
[log] 登录凭证已保存到本机，下次同步可直接复用
[http] → GET http://…/jsxsd/xskb/xskb_list.do?xnxq01id=
[http] ← 200 OK · 51234 字符 · 233ms
[log] 学生课表解析出 42 条课程记录
[info] getCourses 返回 42 门课程（已通过宿主校验）
```

### 9.2 脱敏与截断规则

- 请求体里的 `password` / `passwd` / `pwd` / `pass` / `token` / `secret` /
  `access_token` / `refresh_token` / `sessionid` 等键的值 → `***`（键名保留）；
- 「生效配置」那行还会把 manifest 里 `type: "password"` 的配置项一并脱敏
  ——即使插件把 key 起成了不命中敏感词的名字（如 `mima`）；
- `Cookie` / `Set-Cookie` 头只隐藏 **cookie 值**，cookie 名与属性（`Path`/`HttpOnly`…）保留
  ——排查「登录到底有没有拿到 Cookie」很有用；
- `Authorization` / `Proxy-Authorization` 整个值 → `***`；
- 请求体/响应体/响应头各最多 4000 字符，超出截断并标注原始长度；
- 生效配置最多 1000 字符。

### 9.3 常见错误对照表

| 日志 / 提示 | 原因 | 处理 |
| --- | --- | --- |
| `does not provide an export named 'getCourses'` | 用了 `export default` 或忘了 `export` | 改成 `export async function getCourses(ctx)` |
| `找不到 ./xxx.js；包内可用文件：…` | `import` 路径写错 / 文件没打进 zip | 对照日志里的文件清单修正 |
| `getCourses() 没有返回值` | 忘了 `return`，或返回 `undefined` | 返回课程数组 |
| `返回值不是合法 JSON` | 返回了 `Map`、循环引用、函数 | 只返回可 `JSON.stringify` 的普通对象 |
| `第 N 门课程「…」被忽略：dayIndex …` | 星期/节次/周次越界或为空 | 按 [5.5](#55-校验规则) 修正（周日是 0） |
| `课程数量超过上限 2000` | 返回过多 | 过滤掉不需要的课程（如已结课） |
| `[http] ← 0 …` / `resp.ok === false` | 网络失败、超时、URL 写错 | 看 `resp.error`；确认地址带 `http(s)://` |
| `[http] ← 404` | 路径不对 / 没登录 | 检查路径大小写与是否需要登录态 |
| 页面拿到了但 `select` 命中 0 行 | 选择器不对 / 返回的是登录页 | 打 `page.body.slice(0, 200)`，确认是不是被踢回登录页 |
| `captcha.recognize() 识别失败：…（需 ≥78×33）` | 不是强智 80×40 验证码 | 见 [第 7 节](#7-验证码识别只适用于强智教务系统-8040)：改用登录态复用 |
| `JS 错误 @ index.js:42：…` | 插件自身报错 | 按行号定位（`undefined` 访问最常见） |
| `同步超时：插件超过 90 秒未完成` | 死循环或接口极慢 | 检查循环；必要时减少请求 |
| 提示「需要更高的 App 版本」 | `minHostVersionCode` 过高 | 调低它或让用户升级 App |

### 9.4 本地先验证 JS 逻辑

不需要每次装包试错：把解析函数抽到 `utils/` 里，用 Node 直接跑单元测试
（喂一段真实页面 HTML，断言课程数组），逻辑对了再打包。
注意 Node 里没有 `http`/`ksoup`/`storage`，只测**纯函数**（周次解析、星期映射、
节次换算、表格解析——把 `ksoup` 换成 `linkedom`/`jsdom` 之类的 DOM 实现即可）。

---

## 10. 用户侧流程：安装、同步、只读层与分享

### 10.1 安装与绑定

- **安装是全局的**：设置 → 教务系统插件 → 从 ZIP 安装；同 id 重装即覆盖升级；
- **绑定是按课表的**：课表页 ⋯ → 课表设置 → 教务系统插件 → 选一个插件
  （每个课表**只能选一个**，可选「不使用插件」解绑）；
- **配置值按课表保存**：同一个插件在不同课表可以填不同账号/学期，
  `ctx.config` 与 `storage` 都跟着当前课表走。

### 10.2 三种触发方式

| 触发 | 行为 |
| --- | --- |
| **打开软件**（冷启动 / 从后台回前台）、**切换课表** | 自动静默同步（不弹 Toast，课表页顶部转圈）；30 秒内刚试过、正在同步、未绑定或插件已卸载时跳过（防抖） |
| **课表页下拉刷新** | 手动同步当前课表，结果用顶部 Toast 提示（成功「插件课表已更新：共 N 门课程」，失败给具体原因）；**不受 30 秒防抖限制** |
| **课表设置 → 立即同步课表** | 同上，并在行内显示 `上次 yyyy-MM-dd HH:mm · N 门` 或红色失败原因 |

同步**失败不会清空**上一次成功的课程，课表继续可用；失败原因与日志都会留下。

### 10.3 插件课程是只读的

- 插件课程与用户自定义课程**分开存放**，课表格子外观完全一致，不额外加标记；
- 点开详情只有只读提示 +「转换为自定义课程」，**没有编辑**；
- 「转换为自定义课程」把课程**复制**成可编辑的自定义课程
  （课表设置里也能一次转换当前课表全部插件课程）；
- **插件层不会被删除**，每次同步照旧刷新——所以插件更新课表后可能出现重复
  （插件课程 + 已转换的副本），转换前的确认框会明确提示这一点。

> 想彻底避免重复：不要在 App 里转换（直接在插件数据源侧改），
> 或者转换完成后到课表设置里选「不使用插件」。

### 10.4 分享与导出

- 分享/导出会**清除全部插件信息**：插件课程在导出前并入课程列表，接收方导入后
  就是一份普通自定义课表，可直接编辑；
- 信封里**不含**插件课程字段、插件 id、插件配置（账号密码等**不会离开本机**）、
  KV 数据；
- 兼容读取：早期版本分享的信封里单独的 `pluginCourses` 字段仍会被读出并并入课程。

### 10.5 推荐时间表怎么到用户手里

插件包里的 `timetables.json`（见 [3.1](#31-插件推荐时间表timetablesjson)）会在用户**选中该插件**后
被宿主读出来，出现在「课表页 ⋯ → 课表设置 → 上课时间 → **插件推荐时间表**」：

| 用户看到 | 含义 |
| --- | --- |
| 一行推荐表（`N 节 · 08:00–21:30`） | 点「使用」复制成本课表的时间表并启用；之后按需要改 |
| 「已添加」 | 本课表已有同名同节次的表，点「启用」直接切过去，不会重复添加 |
| 「插件没有推荐时间表」 | 包内没有 `timetables.json`，或文件内容没解析出可用的表 |
| 整组不出现 | 该课表没绑定插件（全局默认设置页里也不显示这一组） |
| 「全局时间表」分组 | 同一页的另一个来源分组（内置「默认作息」+ 全局课表设置里的默认时间表），「使用 / 已添加 / 启用」的含义与插件推荐完全一致 |

复制出来的时间表**完全归用户**：改名、调节次、删除都只影响本课表；插件升级/重装
不会改动它（`timetables.json` 变了也只是重新出现一行新的推荐）。

---

## 11. 限制与安全

### 11.1 执行限制

| 限制 | 值 | 触发后的表现 |
| --- | --- | --- |
| 墙钟超时（网络 + 执行） | 90 秒 | `同步超时：插件超过 90 秒未完成（网络太慢或插件死循环）` |
| JS 执行时间（CPU） | 20 秒 | QuickJS 中断，报中断类错误 |
| 单请求超时 | 30 秒（连接 15 秒） | 该请求返回 `ok:false` + `error` |
| 课程数量 | 2000 门 | 超出部分忽略 + 日志告警 |
| 同步日志 | 400 条 × 4000 字符（总量约 60000 字符） | 超出丢弃并提示已截断 |
| 插件包 | 单文件 2 MB / 解压总量 8 MB | 拒绝安装 |
| HTTP 日志 | 请求体/响应体/响应头各 4000 字符 | 截断并标注原始长度 |

### 11.2 安全边界

- 插件只能访问**网络**（经宿主 HTTP 桥）与**自己按课表隔离的 KV**；
  没有文件系统、没有 App 内部状态、没有 UI 交互能力；
- 每次同步是**独立运行时**，结束后销毁：插件之间不共享全局，
  也不能把状态偷偷留在内存里跨同步（要留就 `storage.set`）；
- Cookie 会话只在**一次同步内**保持，同步结束即丢弃；
- 插件包内文件只能通过 `import` 读取（安装时已做路径安全检查）。

### 11.3 凭据与隐私（写给插件作者）

- **`type: "password"` 的配置值（以及 key 命中 `password` / `token` / `secret` / `pwd` 等的项）
  由宿主加密保存**：Android 用 `EncryptedSharedPreferences`（密钥在 Android Keystore）、
  iOS 用 Keychain（KVault），课表存档 `custom-schedule.json` 里只有 key 名、没有值；
  值只在运行时读进内存、交给 `ctx.config`，并随请求发往**你配置的教务系统地址**；
- 其余配置项（地址、学期、开关…）仍然明文保存，便于排查与迁移；
- 历史版本留下的明文密码会在 App 启动时**自动迁移**进加密存储，并从存档中删除；
- 即便如此：**不要在插件里把账号密码发往第三方地址**，
  也不要把凭据写进日志（宿主只自动脱敏常见键名，自定义键名不会）；
- 分享/导出**不会**带走插件配置与 KV，用户不必担心换机时泄露；
- 用户应当只安装信任来源的插件——这一点也写在了 App 的插件页说明里。

---

## 12. 常见问题

**Q：一个插件能适配多个学校吗？**
可以。把地址、路径做成 `configs`（配合 `options` 给候选值、用 `select` 限制只能选），
或者按 `ctx.config` 分支走不同实现；`id` 保持一个即可。

**Q：一个课表能同时用两个插件吗（比如本科课表 + 实验课表）？**
不能，一个课表只能绑定一个插件。要么在同一个插件里合并两种数据源
（`plugins/usc` 就是这么做的：学生课表 + 实验课表），要么建两个课表分别绑定。

**Q：能定时后台同步吗？**
不能。插件没有定时器，同步时机由宿主决定（打开软件、切课表、下拉刷新、手动点同步）。

**Q：能改课表里的课程吗（改名、改时间）？**
不能。插件课程是只读层，用户只能「转换为自定义课程」后在 App 里改。

**Q：能改用户的时间表（作息时间）吗？**
不能自动改。插件只能在包内放 `timetables.json` **推荐**作息时间（见
[3.1](#31-插件推荐时间表timetablesjson)），用户点「使用」才会复制一张成本课表；
复制出来的表归用户，插件改文件也不会动它。

**Q：插件能读别的课表的数据吗？**
不能。`ctx.schedule` 只有当前课表的 id 与名称，`storage` 也只在当前课表维度可读写。

**Q：`storage` 里的数据在用户换课表后会丢吗？**
不会丢，但读不到——每个课表一份。插件重新被该课表使用时数据还在。

**Q：插件能读到用户保存的密码吗？**
能，但只在运行时：宿主把 `type: "password"` 的配置值从加密存储解密后合并进
`ctx.config`（明文，插件按需使用）。宿主保证的是**不把密码明文写进课表存档**，
插件自己仍然不要把凭据写进日志或发往第三方地址。

**Q：升级插件后用户要重填配置吗？**
不用。配置值存在课表里（`pluginConfig`），与插件文件分开；
`configs` 里新增的项会按 `default` 生效，删掉的项留在存档里但不进 `ctx.config`。

**Q：怎么知道用户装的是哪个版本的宿主？**
`ctx.hostVersionCode`（配合 `manifest.minHostVersionCode` 做兼容）。

**Q：为什么我的插件在别的 App 里跑不起来？**
宿主 API（`http`/`ksoup`/`storage`/`captcha`）是南瓜校园特有的；
用 `ctx.sdk === "pumpkincampus-plugin"` 可以探测宿主。

---

## 附录 A：最小可安装插件

**manifest.json**

```json
{
  "id": "com.example.minimal",
  "name": "最小示例插件",
  "version": "1.0.0",
  "entry": "index.js",
  "minHostVersionCode": 1,
  "schoolName": "示例大学",
  "configs": [
    { "title": "教务系统地址", "key": "base_url", "type": "string", "default": "" },
    { "title": "学号", "key": "username", "type": "string", "default": "" },
    { "title": "密码", "key": "password", "type": "password", "default": "" }
  ]
}
```

**index.js**

```js
export async function getCourses(ctx) {
  const { base_url, username, password } = ctx.config;
  if (!base_url) throw new Error("请先填写教务系统地址");

  // 1) 登录（同一同步内 Cookie 自动保持）
  const login = await http.post(
    base_url + "/login",
    "username=" + encodeURIComponent(username) + "&password=" + encodeURIComponent(password),
    { contentType: "application/x-www-form-urlencoded" }
  );
  if (!login.ok) throw new Error("登录失败：HTTP " + login.status);

  // 2) 取课表页面
  const page = await http.get(base_url + "/schedule");
  if (!page.ok) throw new Error("拉取课表失败：HTTP " + page.status);

  // 3) 解析（选择器换成你学校的）
  const doc = ksoup.parse(page.body);
  const rows = doc.select("table.schedule tbody tr");
  if (rows.length === 0) {
    console.warn("没有匹配到课程行，页面开头：", page.body.slice(0, 200));
    return [];
  }

  const courses = [];
  for (const row of rows) {
    const name = row.selectFirst("td.name")?.text?.trim();
    if (!name) continue;
    courses.push({
      name,
      teacher: row.selectFirst("td.teacher")?.text?.trim() ?? "",
      classroom: row.selectFirst("td.room")?.text?.trim() ?? "",
      dayIndex: Number(row.attr("data-day") || 1),          // 0=周日
      lessonStartIndex: Number(row.attr("data-start") || 1) - 1,
      lessonCount: Number(row.attr("data-len") || 2),
      weekIndices: (row.attr("data-weeks") || "").split(",")
        .filter(Boolean).map((w) => Number(w) - 1)           // 1 基 → 0 基
    });
  }
  console.log("解析出 " + courses.length + " 门课程");
  return courses;
}
```

把这两个文件（以及可选的 `utils/`、`README.md`、`timetables.json`）压成 zip 安装即可。

**timetables.json**（可选，推荐本校作息时间，见 [3.1](#31-插件推荐时间表timetablesjson)）

```json
[
  {
    "name": "示例大学作息时间",
    "slots": [
      { "start": "08:00", "end": "08:45" },
      { "start": "08:55", "end": "09:40" },
      { "start": "10:00", "end": "10:45" },
      { "start": "10:55", "end": "11:40" }
    ]
  }
]
```

---

## 附录 B：API 速查表

```js
// —— ctx ——
ctx.pluginId / pluginName / pluginVersion / schoolName / hostVersionCode / sdk
ctx.schedule.id / ctx.schedule.name
ctx.config.<key>                       // 已合并、已按类型转换

// —— http（都返回 Promise，网络失败不抛异常，看 resp.ok） ——
await http.get(url, opts?)             // opts: { headers, contentType, body, responseType, followRedirects }
await http.head(url, opts?)
await http.post(url, body?, opts?)     // body 传对象 → JSON
await http.put(url, body?, opts?)
await http.delete(url, body?, opts?)
await http.request({ url, method, headers, body, contentType, responseType, followRedirects })
await http.text(url, opts?)            // → string
await http.json(url, opts?)            // → object | null
// resp: { ok, status, headers, body, error, bodyBase64, bodyBytes }

// —— ksoup ——
const doc = ksoup.parse(html)          // KsoupNode（根 = body）
doc.select(sel) / doc.selectFirst(sel)
node.tagName / text / html / outerHtml / id / parent / children
node.attr(name) / node.hasAttr(name)

// —— storage（按「插件 + 课表」隔离，值为 JSON 可序列化数据） ——
storage.get(key) / set(key, value) / has(key) / keys() / remove(key) / clear()

// —— captcha（仅强智教务系统 80×40 验证码） ——
captcha.recognize(imageBase64)         // → "1bmz" | null
recognizeCaptcha(imageBase64)          // 等价

// —— console ——
console.log / info / warn / error / debug(...)
```

```js
// —— getCourses 返回的课程 ——
{ name, teacher, classroom, dayIndex, lessonStartIndex, lessonCount, weekIndices }
{ name, teacher, classroom, dayIndex, lessonStartIndex, lessonCount, weeks }   // weeks 是 1 基
{ name, classroom, fixedDate, fixedStartMinute, fixedDurationMinutes }         // 固定课程
```

**没有的**：DOM、`fetch`、`XMLHttpRequest`、`localStorage`、`setTimeout`、
`URLSearchParams`、`TextEncoder`、`atob/btoa`、`Intl`、Node.js API、文件系统、UI 交互。
