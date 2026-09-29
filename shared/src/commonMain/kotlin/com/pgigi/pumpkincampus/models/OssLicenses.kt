package com.pgigi.pumpkincampus.models

/**
 * 本 App 用到的开源项目清单（「关于 → 开放源代码许可」的数据源）。
 *
 * 收录范围：**直接依赖**，外加随 APK 一起分发、对功能有实质影响的关键传递依赖
 * （QuickJS 引擎、Salt UI 依赖的 Haze / Feather 图标 / HiddenApiBypass、
 * kzip 依赖的 Kompress 等）。纯注解 / 日志门面等无实体行为的传递依赖不单列，
 * 它们各自的许可证同样有效。
 *
 * 每条的许可证名与主页地址都以 Maven POM 里的 `<licenses>` / `<url>` 为准
 * （构建时从 Gradle 缓存核对），不是照抄二手清单。
 */
val ossLicenses: List<OssLicense> = listOf(
    // ---- Kotlin 运行时与官方库 ----
    OssLicense(
        title = "Kotlin",
        author = "JetBrains",
        link = "https://github.com/JetBrains/kotlin",
        licence = APACHE_LICENSE,
        file = APACHE_LICENSE_FILE,
        group = "Kotlin"
    ),
    OssLicense(
        title = "Kotlinx Coroutines",
        author = "JetBrains",
        link = "https://github.com/Kotlin/kotlinx.coroutines",
        licence = APACHE_LICENSE,
        file = APACHE_LICENSE_FILE,
        group = "Kotlin"
    ),
    OssLicense(
        title = "Kotlinx Serialization",
        author = "JetBrains",
        link = "https://github.com/Kotlin/kotlinx.serialization",
        licence = APACHE_LICENSE,
        note = "课表 / 设置 / 插件清单的 JSON 读写",
        file = APACHE_LICENSE_FILE,
        group = "Kotlin"
    ),
    OssLicense(
        title = "Kotlinx Datetime",
        author = "JetBrains",
        link = "https://github.com/Kotlin/kotlinx-datetime",
        licence = APACHE_LICENSE,
        note = "教学周与日期换算",
        file = APACHE_LICENSE_FILE,
        group = "Kotlin"
    ),
    OssLicense(
        title = "Kotlinx IO",
        author = "JetBrains",
        link = "https://github.com/Kotlin/kotlinx-io",
        licence = APACHE_LICENSE,
        file = APACHE_LICENSE_FILE,
        group = "Kotlin"
    ),

    // ---- Compose 与界面 ----
    OssLicense(
        title = "Compose Multiplatform",
        author = "JetBrains",
        link = "https://github.com/JetBrains/compose-multiplatform",
        licence = APACHE_LICENSE,
        note = "全部界面的声明式 UI 框架（含 Material 3）",
        file = APACHE_LICENSE_FILE,
        group = "界面"
    ),
    OssLicense(
        title = "Salt UI",
        author = "Moriafly",
        link = "https://github.com/Moriafly/SaltUI",
        licence = APACHE_LICENSE,
        note = "界面组件与页面导航（salt-ui / salt-ui-navigation）",
        file = APACHE_LICENSE_FILE,
        group = "界面"
    ),
    OssLicense(
        title = "Navigation3",
        author = "Google",
        link = "https://github.com/androidx/androidx",
        licence = APACHE_LICENSE,
        note = "设置、课表等子页的导航栈",
        file = APACHE_LICENSE_FILE,
        group = "界面"
    ),
    OssLicense(
        title = "Haze",
        author = "Chris Banes",
        link = "https://github.com/chrisbanes/haze",
        licence = APACHE_LICENSE,
        note = "毛玻璃模糊效果（Salt UI 依赖）",
        file = APACHE_LICENSE_FILE,
        group = "界面"
    ),
    OssLicense(
        title = "Feather Icons",
        author = "DevSrSouza",
        link = "https://github.com/DevSrSouza/compose-icons",
        licence = MIT_LICENSE,
        copyright = "Copyright (c) DevSrSouza",
        note = "界面图标（Salt UI 依赖）",
        file = MIT_LICENSE_FILE,
        group = "界面"
    ),
    OssLicense(
        title = "Material Components for Android",
        author = "Google",
        link = "https://github.com/material-components/material-components-android",
        licence = APACHE_LICENSE,
        note = "Android 端底层主题与控件（Salt UI 依赖）",
        file = APACHE_LICENSE_FILE,
        group = "界面"
    ),
    OssLicense(
        title = "DhyanToast",
        author = "androidpoet",
        link = "https://github.com/androidpoet/Dhyantoast",
        licence = APACHE_LICENSE,
        note = "下拉刷新等操作的顶部 Toast 提示（源码 vendored 于 shared 的 io/androidpoet/dhyantoast，见该目录 VENDORED.md）",
        file = APACHE_LICENSE_FILE,
        group = "界面"
    ),

    // ---- 网络 ----
    OssLicense(
        title = "Ktor",
        author = "ktorio",
        link = "https://github.com/ktorio/ktor",
        licence = APACHE_LICENSE,
        note = "在线分享课表的 HTTP 客户端",
        file = APACHE_LICENSE_FILE,
        group = "网络"
    ),
    OssLicense(
        title = "Okio",
        author = "Square",
        link = "https://github.com/square/okio",
        licence = APACHE_LICENSE,
        note = "文件与流读写（存档、插件包）",
        file = APACHE_LICENSE_FILE,
        group = "网络"
    ),
    OssLicense(
        title = "KSoup",
        author = "FleekSoft",
        link = "https://github.com/fleeksoft/ksoup",
        licence = MIT_LICENSE,
        copyright = "Copyright (c) FleekSoft",
        note = "教务系统网页解析（jsoup 的 Kotlin Multiplatform 移植）",
        file = MIT_LICENSE_FILE,
        group = "网络"
    ),

    // ---- 课表插件运行时 ----
    OssLicense(
        title = "QuickJS",
        author = "Fabrice Bellard",
        link = "https://github.com/bellard/quickjs",
        licence = MIT_LICENSE,
        copyright = "Copyright (c) Fabrice Bellard",
        note = "执行课表插件脚本的 JavaScript 引擎（随 quickjs-kt 内置）",
        file = MIT_LICENSE_FILE,
        group = "课表插件"
    ),
    OssLicense(
        title = "quickjs-kt",
        author = "dokar3",
        link = "https://github.com/dokar3/quickjs-kt",
        licence = APACHE_LICENSE,
        note = "QuickJS 的 Kotlin Multiplatform 绑定",
        file = APACHE_LICENSE_FILE,
        group = "课表插件"
    ),
    OssLicense(
        title = "kzip",
        author = "Jonas Broeckmann",
        link = "https://github.com/Jojo4GH/kzip",
        licence = MIT_LICENSE,
        copyright = "Copyright (c) Jonas Broeckmann",
        note = "解压 zip 安装的课表插件",
        file = MIT_LICENSE_FILE,
        group = "课表插件"
    ),
    OssLicense(
        title = "Kompress",
        author = "Karma Krafts",
        link = "https://github.com/karmakrafts/Kompress",
        licence = APACHE_LICENSE,
        note = "压缩 / 解压实现（kzip 依赖）",
        file = APACHE_LICENSE_FILE,
        group = "课表插件"
    ),
    OssLicense(
        title = "KVault",
        author = "Liftric",
        link = "https://github.com/Liftric/kvault",
        licence = MIT_LICENSE,
        copyright = "Copyright (c) 2020 Liftric GmbH",
        note = "插件密码类配置的加密存储（Android Keystore / iOS Keychain）",
        file = MIT_LICENSE_FILE,
        group = "课表插件"
    ),

    // ---- 平台基础库 ----
    OssLicense(
        title = "AndroidX",
        author = "Google",
        link = "https://github.com/androidx/androidx",
        licence = APACHE_LICENSE,
        note = "Activity / Lifecycle / SplashScreen / Security（加密存储）等平台基础库",
        file = APACHE_LICENSE_FILE,
        group = "平台基础库"
    ),
    OssLicense(
        title = "HiddenApiBypass",
        author = "LSPosed",
        link = "https://github.com/LSPosed/AndroidHiddenApiBypass",
        licence = APACHE_LICENSE,
        note = "Android 上访问隐藏 API（Salt UI 依赖）",
        file = APACHE_LICENSE_FILE,
        group = "平台基础库"
    )
)

/** 清单按 [OssLicense.group] 分组（保持清单里的原始顺序），列表页直接用。 */
val ossLicenseGroups: List<Pair<String, List<OssLicense>>>
    get() = ossLicenses.groupBy { it.group ?: "其他" }.toList()
