# 南瓜校园 · 教务系统课表插件文档

南瓜校园支持通过 **QuickJS 插件**把学校教务系统里的课表并入应用课表。
插件是一个 zip 包，核心是 ES Module 格式的 `index.js`，宿主负责提供
网络（Ktor）、HTML 解析（KSoup）、KV 存储与日志等桥接能力。

> ⚠️ 内置的验证码识别（`captcha.recognize`）**只适用于强智教务系统 80×40 的
> 4 字符验证码**；其他教务系统请改用登录态（Cookie）复用，
> 详见[开发文档第 7 节](plugin-development.md#7-验证码识别只适用于强智教务系统-8040)。

| 文档 | 内容 |
| --- | --- |
| [plugin-development.md](plugin-development.md) | **完整开发文档**：能力与边界、运行模型、包结构、manifest 全字段、`getCourses` 契约、宿主 API 完整参考、验证码适用范围、分步实战、调试、限制与安全、FAQ |
| [plugin-api.md](plugin-api.md) | **API 速查表**：`ctx`、`http`、`ksoup`、`storage`、`captcha`、`console`、课程字段与限制一览 |
| [plugin-template/](plugin-template/) | **插件模板**：可直接改起来的 manifest + 入口 + 工具库 + 说明 |
| [../plugins/nggjx-schedule/](../plugins/nggjx-schedule/) | **现成插件**：nggjx 教务接口课表（含可直接安装的 `nggjx-schedule.zip`） |
| [../plugins/usc/](../plugins/usc/) | **现成插件**：南华大学教务（jsxsd）学生课表 + 实验课表，自动登录并缓存 Cookie（含可直接安装的 `usc.zip`） |
| [../plugins/QiangZhi/](../plugins/QiangZhi/) | **现成插件（通用）**：各校强智教务通用版，教务系统地址由用户填写、脚本检测空值（含可直接安装的 `QiangZhi.zip`） |

快速开始：

1. 复制 `plugin-template/` 为你的插件目录；
2. 按 [完整开发文档](plugin-development.md) 改 `manifest.json` 与 `index.js`；
3. 把整个目录压缩成 zip（`manifest.json` 在根目录或唯一顶层文件夹内均可）；
4. App：**设置 → 教务系统插件 → 从 ZIP 文件安装插件**；
5. 课表页 **⋯ → 课表设置 → 教务系统插件**：选择该插件、填写配置、立即同步；
   之后在课表页**下拉**即可随时刷新插件课表（结果用顶部 Toast 提示）。

## 关键概念

- **插件全局安装/卸载**（设置页），**一个课表只能绑定一个插件**（课表设置页）。
- 插件的**配置值、KV 数据、同步结果按课表隔离**：不同课表可以为同一插件
  保存不同账号/学期，互不干扰；`storage` 只能读写「当前插件 + 当前课表」的数据。
- 插件同步拉回的课程进入**只读的插件课程层**（与自定义课程分开存放，课表里不额外标记）：
  不能直接编辑，只能「转换为自定义课程」复制成可编辑的副本；
  插件层会保留，所以插件更新课表后可能出现重复课程（转换前会提示）。
- **分享/导出**会**清除插件信息**：插件课程并入课程列表一起分享（接收方看到完整课表，
  导入后就是一份普通自定义课表，可直接编辑），信封里不含插件课程字段、插件 id
  与插件配置（账号密码等不出本机）。
- **插件配置支持候选值**：manifest 的 `configs[].options` 会渲染成一排候选按钮，
  用户点选即可，也允许自己手动填写（例如学期 ID、教务系统地址）。
- **密码类配置加密保存**：`type: "password"`（以及 key 命中 `password` / `token` /
  `secret` 的项）由 KVault 存进系统加密存储（Android Keystore / iOS Keychain），
  课表存档里只有 key 名、没有值；历史版本的明文密码会在启动时自动迁移。
- **验证码识别有明确适用范围**：`captcha.recognize` 只认强智教务系统那种
  **80×40、4 字符**的验证码；其他教务系统的插件应当改用登录态（Cookie）复用，
  插件本身也无法让用户手动输入验证码。
