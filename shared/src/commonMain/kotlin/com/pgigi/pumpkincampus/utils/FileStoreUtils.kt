package com.pgigi.pumpkincampus.utils

import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.SYSTEM

/**
 * 获取应用数据目录路径（持久化存储）
 * - Android: context.filesDir
 * - iOS: NSDocumentDirectory
 */
expect fun appDataDir(): String

/**
 * 获取应用缓存目录路径（系统可清理）
 * - Android: context.cacheDir
 * - iOS: NSCachesDirectory
 */
expect fun appCacheDir(): String

/**
 * 文件存储工具类（移植自 Pumpkin-Toolkit）
 *
 * 基于 okio FileSystem 实现跨平台文件读写，支持 String 和 ByteArray。
 * 数据目录用于持久化文件，缓存目录用于临时文件（系统可能在存储空间不足时清理）。
 *
 * 使用示例：
 * ```
 * FileStoreUtils.writeString("config.json", """{"key":"value"}""")
 * val json = FileStoreUtils.readString("config.json")
 * FileStoreUtils.writeBytes("temp.bin", byteArray, toCache = true)
 * val data = FileStoreUtils.readBytes("temp.bin", fromCache = true)
 * ```
 */
object FileStoreUtils {

    private val fs: FileSystem = FileSystem.SYSTEM

    val dataDir: String get() = appDataDir()
    val cacheDir: String get() = appCacheDir()

    // ==================== String ====================

    fun writeString(fileName: String, content: String, toCache: Boolean = false): Boolean {
        return try {
            val path = resolvePath(fileName, toCache)
            ensureParentDir(path)
            fs.write(path) { writeUtf8(content) }
            true
        } catch (e: Exception) {
            false
        }
    }

    fun readString(fileName: String, fromCache: Boolean = false): String? {
        return try {
            val path = resolvePath(fileName, fromCache)
            if (fs.exists(path)) fs.read(path) { readUtf8() } else null
        } catch (e: Exception) {
            null
        }
    }

    // ==================== ByteArray ====================

    fun writeBytes(fileName: String, bytes: ByteArray, toCache: Boolean = false): Boolean {
        return try {
            val path = resolvePath(fileName, toCache)
            ensureParentDir(path)
            fs.write(path) { write(bytes) }
            true
        } catch (e: Exception) {
            false
        }
    }

    fun readBytes(fileName: String, fromCache: Boolean = false): ByteArray? {
        return try {
            val path = resolvePath(fileName, fromCache)
            if (fs.exists(path)) fs.read(path) { readByteArray() } else null
        } catch (e: Exception) {
            null
        }
    }

    // ==================== 文件操作 ====================

    fun exists(fileName: String, inCache: Boolean = false): Boolean {
        return fs.exists(resolvePath(fileName, inCache))
    }

    fun delete(fileName: String, inCache: Boolean = false): Boolean {
        return try {
            fs.delete(resolvePath(fileName, inCache), mustExist = false)
            true
        } catch (e: Exception) {
            false
        }
    }

    // ==================== 内部方法 ====================

    private fun resolvePath(fileName: String, useCache: Boolean): Path {
        val base = if (useCache) appCacheDir() else appDataDir()
        return base.toPath() / fileName
    }

    private fun ensureParentDir(path: Path) {
        path.parent?.let { fs.createDirectories(it, mustCreate = false) }
    }
}
