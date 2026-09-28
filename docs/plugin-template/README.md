# 插件模板（南瓜校园）

把这份目录复制一份改起来，就能得到一个可安装的课表插件。
完整说明见 **[../plugin-development.md](../plugin-development.md)**（能力边界、manifest 全字段、
宿主 API、分步实战、调试与常见问题），API 速查见 [../plugin-api.md](../plugin-api.md)。

```
manifest.json      清单：id / name / entry / minHostVersionCode / configs …
index.js           入口：export async function getCourses(ctx)
timetables.json    可选：推荐本校作息时间表（纯数据，宿主直接读取，见下）
utils/util.js      工具库示例：周次、星期、时间解析与带重试的请求
README.md          本文件
```

## 修改步骤

1. **manifest.json**
   - `id` 换成你自己的反向域名（全局唯一，改了等于新插件）；
   - `name` / `schoolName` / `author` / `updateTime` 按需修改；
   - `configs` 是给用户填的配置项：`title` 是显示名（`key` 省略时兼作键名，
     建议显式写 `key`），`type` 支持 `string / password / int / bool / select`，
     `default` 是**默认配置**——每个课表会另外保存自己的值；
   - `options` 是候选值（`{ "name": "显示名", "value": "实际值" }`）：`select` 类型只能从中选
     （没有输入框），其他类型点一下填入、也允许自己输入。
2. **index.js**
   - 实现 `getCourses(ctx)`：登录 → 拉课表 → 解析 → 返回课程数组；
   - 删掉/替换模板里的两段示例写法（JSON 接口 / HTML 页面），换成你学校的真实接口；
   - 返回的课程字段见 `docs/plugin-development.md` §5.4，
     周次既可以用 0 基的 `weekIndices`，也可以直接给教务写法的 `weeks`（1 基），
     宿主会自动转换。
3. **utils/util.js**：`parseWeekRanges / parseDay / parseClock / fetchWithRetry`
   可直接复用，也可以继续加自己的函数。
4. **timetables.json**（可选）：把你学校的作息时间填进去，用户就能在
   「课表设置 → 上课时间 → 插件推荐时间表」里一键套用；不需要就删掉这个文件。
   格式是纯 JSON，`slots` 每项 `{ "start": "08:00", "end": "08:45" }`，
   最多 5 张表、每张 40 节。

## 本地验证

模板 `index.js` 顶部保证了：缺配置会抛出**中文可读错误**（引导用户去填配置），
网络失败会带上 HTTP 状态码。装到 App 后：

1. 设置 → 教务系统插件 → 从 ZIP 文件安装插件；
2. 课表页 ⋯ → 课表设置 → 教务系统插件：选中插件、填配置、立即同步；
3. 出问题就点「查看同步日志」——`console.log` 的内容都在里面。

## 打包

在模板的**上一级目录**执行（`manifest.json` 会在 zip 的顶层目录里，宿主会自动剥掉）：

```powershell
Compress-Archive -Path plugin-template -DestinationPath my-plugin.zip -Force
```

把 `my-plugin.zip` 装进 App 即可；同 id 再次安装 = 覆盖升级。
