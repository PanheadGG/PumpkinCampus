# Vendored source: DhyanToast

本目录的 `.kt` 文件**不是本项目原创**，而是从上游仓库 vendored（源码引入）进来的：

- 上游：<https://github.com/androidpoet/Dhyantoast>
- 版本：`master` @ commit `f184e046a8a5c0f3dec75bfbb51e4ce8601033fb`（2026-07-03）
- 许可：Apache License 2.0（每个源文件头部保留了上游版权/许可声明）

## 为什么不用 Maven 依赖 `io.github.androidpoet:dhyantoast:0.0.1`

Maven Central 上该库只有 `0.0.1`（2025-11-30 发布），它是用 **Kotlin 2.1.0 + Compose
Multiplatform 1.7.3 / material3 1.7.3** 编译的**预编译 klib**；而本项目用的是
**Kotlin 2.4.20 + CMP 1.12.1 + material3 1.12.0-alpha03**。

material3 在 1.7.3 → 1.12 之间，`MaterialTheme$stable` 这个 Compose 合成属性的 backing field
变成了 private。预编译 klib 里已经固化的跨模块读取于是链接不上，Kotlin/Native 在运行时抛出：

```
kotlin.internal.IrLinkageError: Can not read value from backing field of property
'androidx_compose_material3_MaterialTheme$stable': Private backing field of property declared
in module <org.jetbrains.compose.material3:material3> can not be accessed in module
<DhyanToastDemo:dhyantoast>
```

同样的"预编译产物比项目依赖旧"问题，在 Android 上还表现为 `kotlinx.datetime.Clock`
缺失（此前靠 `androidApp/.../kotlinx/datetime/*.java` 垫片绕过，现已随本 vendoring 一并删除）。

源码引入后，DhyanToast 会用**本项目当前的 material3 直接重新编译**，引用在编译期解析，
链接错误不复存在；同时也不再依赖 `feather-icons` / `material-icons-extended` /
`kotlinx-datetime`（vendored 源码里并未用到这些）。

## 更新方式

上游发布适配新版 Compose 的版本后，可改回 `libs.dhyantoast` 依赖并删除本目录；
或重新拉取上游源码覆盖本目录。
