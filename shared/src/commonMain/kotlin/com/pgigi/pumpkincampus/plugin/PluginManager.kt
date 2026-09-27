package com.pgigi.pumpkincampus.plugin

import com.pgigi.pumpkincampus.appVersionCode
import com.pgigi.pumpkincampus.constants.FileName
import com.pgigi.pumpkincampus.utils.FileStoreUtils
import com.pgigi.pumpkincampus.utils.appCacheDir
import com.pgigi.pumpkincampus.utils.appDataDir
import de.jonasbroeckmann.kzip.Zip
import de.jonasbroeckmann.kzip.open
import okio.FileSystem
import okio.Path
import okio.SYSTEM
import okio.Path.Companion.toPath

/**
 * 插件包管理：**全局**安装 / 卸载 / 列出插件（与课表无关，所有课表共享安装结果）。
 *
 * 安装包是一个 zip，允许两种布局：
 * - 根目录直接是 `manifest.json` + `index.js` + `utils/...`
 * - 整个插件目录被套了一层（如 `my-plugin/manifest.json`）——宿主自动剥掉共同的顶层目录
 * - `__MACOSX/`、`.DS_Store` 等系统垃圾文件会被忽略
 *
 * 安装位置：`plugins/<id>/`（数据目录下），同 id 重装即覆盖升级；
 * 卸载会同时清掉该插件的 KV 数据（`plugin-kv/<id>/`）。
 *
 * zip 解压使用 **de.jonasbroeckmann.kzip:kzip**。
 */
object PluginManager {

    /** 单个插件包解压后的最大字节数（防止恶意大包撑爆存储）。 */
    private const val MAX_TOTAL_BYTES = 8L * 1024 * 1024

    /** 单个文件的最大字节数。 */
    private const val MAX_FILE_BYTES = 2L * 1024 * 1024

    private val fs: FileSystem = FileSystem.SYSTEM

    /** 插件安装根目录（`<数据目录>/plugins`）。 */
    private val pluginsRoot: Path
        get() = dataDir() / FileName.PLUGIN_DIR

    private fun appDataDirSafe(): String = runCatching { appDataDir() }.getOrDefault(".")

    /** 数据目录（FileStoreUtils 暴露的目录，见 utils/FileStoreUtils.kt）。 */
    private fun dataDir(): Path = appDataDirSafe().toPath()

    /* ---------------------------------------------------------------- */
    /* 列出已安装插件                                                     */
    /* ---------------------------------------------------------------- */

    /**
     * 扫描插件根目录下每个子目录的 `manifest.json`，返回按名称排序的已安装插件。
     * manifest 缺失 / 损坏的目录会被跳过（残留目录不影响使用）。
     */
    fun listInstalled(): List<InstalledPlugin> {
        val root = pluginsRoot
        val dirs = runCatching { fs.list(root) }.getOrDefault(emptyList())
        return dirs.mapNotNull { dir ->
            if (fs.metadataOrNull(dir)?.isDirectory != true) return@mapNotNull null
            val raw = runCatching {
                fs.read(dir / "manifest.json") { readUtf8() }
            }.getOrNull() ?: return@mapNotNull null
            val manifest = parsePluginManifest(raw) ?: return@mapNotNull null
            if (!manifest.isIdValid()) return@mapNotNull null
            InstalledPlugin(
                manifest = manifest,
                compatible = manifest.isCompatibleWithHost()
            )
        }.sortedBy { it.name.ifBlank { it.id } }
    }

    /** 按 id 取已安装插件；未安装返回 null。 */
    fun installedOrNull(id: String?): InstalledPlugin? {
        if (id.isNullOrBlank()) return null
        return listInstalled().firstOrNull { it.id == id }
    }

    /* ---------------------------------------------------------------- */
    /* 安装 / 卸载                                                        */
    /* ---------------------------------------------------------------- */

    /**
     * 从 zip 字节流安装（或覆盖升级）插件。
     *
     * 校验顺序：zip 可读 → 找到 manifest → manifest 可解析 → 必填项合法 →
     * 入口文件存在 → 宿主版本满足 `minHostVersionCode`。
     *
     * @return 成功时携带解析出的 [PluginManifest]，message 可直接提示给用户
     */
    fun install(zipBytes: ByteArray): PluginInstallResult {
        // 1) 读出 zip 内所有文件（kzip：写入临时文件后打开）
        val entries: List<Pair<String, ByteArray>> = try {
            readZipEntries(zipBytes)
        } catch (e: Exception) {
            return PluginInstallResult(false, "无法读取插件包（不是有效的 zip？）：${e.message}")
        }
        if (entries.isEmpty()) {
            return PluginInstallResult(false, "插件包是空的")
        }

        // 2) 定位 manifest（允许包外层套一层目录；忽略 __MACOSX 等垃圾）
        val manifestPath = entries
            .map { it.first }
            .filter { it == "manifest.json" || it.endsWith("/manifest.json") }
            .filter { path -> path.split('/').none { it.startsWith(".") || it == "__MACOSX" } }
            .minByOrNull { it.count { c -> c == '/' } }
            ?: return PluginInstallResult(
                false, "插件包里没有 manifest.json（目录结构应为 manifest.json + index.js + README.md）"
            )
        val rootPrefix = manifestPath.substringBeforeLast('/', "")
            .let { if (it.isEmpty()) "" else "$it/" }

        // 3) 统一成相对 manifest 目录的路径
        val normalized = entries.mapNotNull { (path, bytes) ->
            if (!path.startsWith(rootPrefix)) return@mapNotNull null
            val rel = path.removePrefix(rootPrefix)
            if (rel.isEmpty() || rel.endsWith("/")) return@mapNotNull null // 目录项
            if (!PluginManifest.isSafeRelativePath(rel)) return@mapNotNull null
            if (rel.split('/').any { it.startsWith(".") && it != "." }) return@mapNotNull null
            rel to bytes
        }
        val manifestRaw = normalized.firstOrNull { it.first == "manifest.json" }?.second
            ?: return PluginInstallResult(false, "插件包里没有 manifest.json")

        // 4) 解析与校验
        val manifest = parsePluginManifest(manifestRaw.decodeToString())
            ?: return PluginInstallResult(false, "manifest.json 解析失败（检查 JSON 语法与括号）")
        validatePluginManifest(manifest)?.let { return PluginInstallResult(false, it) }

        val entryRel = manifest.entry.replace('\\', '/')
        if (normalized.none { it.first == entryRel }) {
            return PluginInstallResult(false, "入口文件 ${manifest.entry} 不在插件包里")
        }

        // 5) 宿主版本检查（minHostVersionCode vs AppVersionCode）
        val hostVersion = appVersionCode()
        if (manifest.minHostVersionCode > hostVersion) {
            return PluginInstallResult(
                false,
                "「${manifest.name}」需要宿主版本 ≥ ${manifest.minHostVersionCode}（当前 $hostVersion），请升级 App 后再安装"
            )
        }

        // 6) 体积检查 + 落盘（先删同 id 旧目录再写，实现覆盖升级）
        val totalBytes = normalized.sumOf { it.second.size.toLong() }
        if (totalBytes > MAX_TOTAL_BYTES) {
            return PluginInstallResult(false, "插件包解压后超过 8MB，已拒绝安装")
        }
        if (normalized.any { it.second.size.toLong() > MAX_FILE_BYTES }) {
            return PluginInstallResult(false, "插件包含超过 2MB 的单个文件，已拒绝安装")
        }

        val pluginDir = FileName.PLUGIN_DIR + "/" + manifest.id
        val existed = runCatching { fs.list(dataDir() / pluginDir) }.getOrNull()?.isNotEmpty() == true
        try {
            deleteDir(dataDir() / pluginDir)
            for ((rel, bytes) in normalized) {
                if (!FileStoreUtils.writeBytes("$pluginDir/$rel", bytes)) {
                    return PluginInstallResult(false, "写入文件失败：$rel（存储空间不足？）")
                }
            }
        } catch (e: Exception) {
            return PluginInstallResult(false, "安装失败：${e.message}")
        }

        val verb = if (existed) "已更新" else "已安装"
        return PluginInstallResult(true, "$verb「${manifest.name}」v${manifest.version}", manifest)
    }

    /** 卸载插件：删除 `plugins/<id>/` 与它的 KV 数据 `plugin-kv/<id>/`。 */
    fun uninstall(id: String) {
        if (!Regex("""[A-Za-z0-9][A-Za-z0-9._-]*""").matches(id)) return
        deleteDir(dataDir() / FileName.PLUGIN_DIR / id)
        deleteDir(dataDir() / FileName.PLUGIN_KV_DIR / id)
    }

    /* ---------------------------------------------------------------- */
    /* 读取插件文件（运行时用）                                            */
    /* ---------------------------------------------------------------- */

    /**
     * 读出已安装插件的全部文件，key 为相对包根的路径（`index.js`、`utils/util.js`…）。
     * 文件按 UTF-8 解码（插件源码就是文本）。
     */
    fun readFiles(id: String): Map<String, String> {
        if (!Regex("""[A-Za-z0-9][A-Za-z0-9._-]*""").matches(id)) return emptyMap()
        val dir = dataDir() / FileName.PLUGIN_DIR / id
        if (fs.metadataOrNull(dir)?.isDirectory != true) return emptyMap()
        val out = mutableMapOf<String, String>()
        collectFiles(dir, dir, out)
        return out
    }

    /** 深度优先收集目录内所有文件（相对路径 → UTF-8 文本）。 */
    private fun collectFiles(root: Path, dir: Path, out: MutableMap<String, String>) {
        val children = runCatching { fs.list(dir) }.getOrDefault(emptyList())
        for (child in children) {
            val meta = runCatching { fs.metadataOrNull(child) }.getOrNull() ?: continue
            if (meta.isDirectory) {
                collectFiles(root, child, out)
            } else {
                val rel = child.toString()
                    .removePrefix(root.toString())
                    .trimStart('/', '\\')
                    .replace('\\', '/')
                val text = runCatching { fs.read(child) { readUtf8() } }.getOrNull()
                if (text != null) out[rel] = text
            }
        }
    }

    /* ---------------------------------------------------------------- */
    /* 内部：zip 读取与目录删除                                            */
    /* ---------------------------------------------------------------- */

    /**
     * 把 zip 字节写入缓存临时文件后用 kzip 打开，读出全部文件条目（路径 → 字节）。
     * 自动忽略目录项与 `__MACOSX` / 隐藏文件。
     */
    private fun readZipEntries(zipBytes: ByteArray): List<Pair<String, ByteArray>> {
        val tempName = "plugin-install-${zipBytes.size}.zip"
        if (!FileStoreUtils.writeBytes(tempName, zipBytes, toCache = true)) {
            throw IllegalStateException("无法写入临时文件")
        }
        val tempPath = appCacheDirSafe().toPath() / tempName
        val out = mutableListOf<Pair<String, ByteArray>>()
        try {
            // kzip 使用 kotlinx-io 的 Path，这里从 okio 路径字符串转换。
            // 注意：用非内联的 entry(index) 遍历——kzip 的 forEachEntry 是 inline，
            // 而 kzip 按 JVM 22 编译、本模块是 JVM 11，内联会触发目标字节码冲突。
            Zip.open(kotlinx.io.files.Path(tempPath.toString())).use { zip ->
                for (index in 0 until zip.numberOfEntries) {
                    val item = zip.entry(index) {
                        if (isDirectory) return@entry null
                        val name = path.toString().replace('\\', '/').removePrefix("./")
                        if (name.isEmpty()) return@entry null
                        if (name.startsWith("__MACOSX/") ||
                            name.substringAfterLast('/') == ".DS_Store"
                        ) {
                            return@entry null
                        }
                        if (!PluginManifest.isSafeRelativePath(name)) {
                            throw IllegalStateException("包含不安全路径：$name")
                        }
                        name to readToBytes()
                    }
                    if (item != null) out += item
                }
            }
        } finally {
            runCatching { FileStoreUtils.delete(tempName, inCache = true) }
        }
        return out
    }

    private fun appCacheDirSafe(): String = runCatching { appCacheDir() }.getOrDefault(".")

    /** 递归删除目录（含文件）；不存在时静默返回。 */
    private fun deleteDir(dir: Path) {
        val meta = runCatching { fs.metadataOrNull(dir) }.getOrNull() ?: return
        if (meta.isDirectory) {
            val children = runCatching { fs.list(dir) }.getOrDefault(emptyList())
            for (child in children) deleteDir(child)
        }
        runCatching { fs.delete(dir, mustExist = false) }
    }
}
