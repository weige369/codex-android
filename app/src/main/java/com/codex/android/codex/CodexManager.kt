package com.codex.android.codex

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream

/**
 * Codex CLI 管理器。
 *
 * 处理 Codex CLI 二进制文件的下载、验证、生命周期管理。
 * 二进制文件从 GitHub Releases 下载，解压后运行。
 */
class CodexManager(private val context: Context) {

    companion object {
        private const val TAG = "CodexManager"
        private const val CODEX_DIR = "codex"
        private const val CODEX_BINARY_NAME = "codex"
        const val CODEX_VERSION = "0.133.0"

        // GitHub Releases 下载 URL (原始)
        private const val GITHUB_REPO = "openai/codex"
        private const val RELEASE_TAG = "rust-v$CODEX_VERSION"
        private const val BINARY_ARCHIVE = "codex-aarch64-unknown-linux-musl.tar.gz"

        // GitHub release 资源相对路径（github.com 之后的部分）
        private const val RELEASE_PATH =
            "$GITHUB_REPO/releases/download/$RELEASE_TAG/$BINARY_ARCHIVE"
        private const val GITHUB_DIRECT_URL = "https://github.com/$RELEASE_PATH"
        private const val GITHUB_FULL_URL = "https://github.com/$RELEASE_PATH"

        // Gitee 镜像仓库（国内直连，需镜像同步对应 Release）
        private const val GITEE_REPO = "mirrors/codex"
        private const val GITEE_MIRROR_URL =
            "https://gitee.com/$GITEE_REPO/releases/download/$RELEASE_TAG/$BINARY_ARCHIVE"

        // 下载连接超时（毫秒）。缩短以便在不可达镜像间快速切换。
        private const val CONNECT_TIMEOUT_MS = 8_000

        // 镜像下载源（中国大陆友好，按可用性从高到低排列）
        private val MIRROR_URLS = listOf(
            // 国内 GitHub 加速代理（中国大陆可达，优先尝试）
            "https://ghfast.top/$GITHUB_FULL_URL",
            "https://gh-proxy.com/$GITHUB_FULL_URL",
            "https://ghproxy.net/$GITHUB_FULL_URL",
            "https://mirror.ghproxy.com/$GITHUB_FULL_URL",
            // Gitee Release 镜像（国内直连）
            GITEE_MIRROR_URL,
            // GitHub 直连（海外网络 / 已配置代理时可用）
            GITHUB_DIRECT_URL,
            // 旧版加速镜像（备用）
            "https://githubfast.com/$RELEASE_PATH",
        )

        fun getAllDownloadUrls(): List<String> = MIRROR_URLS
    }

    // 文件路径
    val codexDir: File get() = File(context.filesDir, CODEX_DIR)
    val codexBinary: File get() = File(codexDir, CODEX_BINARY_NAME)
    val archiveFile: File get() = File(codexDir, "$CODEX_BINARY_NAME.tar.gz")
    val workspaceDir: File get() = File(context.filesDir, "workspace")

    // 下载进度回调
    var onProgress: ((downloaded: Long, total: Long) -> Unit)? = null

    /**
     * 检查 Codex 二进制是否已安装
     */
    fun isInstalled(): Boolean {
        return codexBinary.exists() && codexBinary.canExecute() && codexBinary.length() > 10_000_000
    }

    /**
     * 验证二进制文件完整性
     */

    /** 手动导入结果，附带可展示给用户的具体原因 */
    data class ImportResult(val success: Boolean, val message: String)

    /**
     * 从本地文件导入 Codex 二进制（带完整性校验）。
     * 校验文件头（ELF）、文件大小，并对常见错误（误选压缩包等）给出明确提示。
     */
    fun importBinaryChecked(sourceFile: File): ImportResult {
        return try {
            if (!sourceFile.exists() || sourceFile.length() == 0L) {
                return ImportResult(false, "文件不存在或为空")
            }

            val header = ByteArray(4)
            FileInputStream(sourceFile).use { input ->
                if (input.read(header) < 4) {
                    return ImportResult(false, "文件过短，无法识别格式")
                }
            }

            // gzip 魔数 1F 8B —— 用户多半误选了未解压的 .tar.gz
            val isGzip = header[0] == 0x1F.toByte() && header[1] == 0x8B.toByte()
            if (isGzip) {
                return ImportResult(
                    false,
                    "这是压缩包(.tar.gz)。请先解压，导入其中名为 codex 的可执行文件"
                )
            }

            // ELF 魔数 7F 45 4C 46
            val isElf = header[0] == 0x7F.toByte() &&
                header[1] == 0x45.toByte() &&
                header[2] == 0x4C.toByte() &&
                header[3] == 0x46.toByte()
            if (!isElf) {
                return ImportResult(false, "不是有效的 Linux ELF 可执行文件（文件头不匹配）")
            }

            if (sourceFile.length() < 10_000_000) {
                val mb = sourceFile.length() / 1024 / 1024
                return ImportResult(
                    false,
                    "文件过小（${mb}MB），Codex 二进制应大于 10MB，文件可能不完整"
                )
            }

            codexDir.mkdirs()
            sourceFile.inputStream().use { input ->
                codexBinary.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            codexBinary.setExecutable(true)
            val sizeMb = codexBinary.length() / 1024 / 1024
            Log.i(TAG, "导入 Codex 二进制: ${codexBinary.length()} bytes")
            ImportResult(true, "导入成功（${sizeMb}MB）")
        } catch (e: Exception) {
            Log.e(TAG, "导入失败", e)
            ImportResult(false, "导入异常: ${e.message ?: "未知错误"}")
        }
    }

    /**
     * 从本地文件导入 Codex 二进制（向后兼容包装）。
     * 用于在网络不可用时手动导入。
     */
    fun importBinary(sourceFile: File): Boolean = importBinaryChecked(sourceFile).success

    /**
     * 检查 Codex 二进制文件完整性
     */
    fun verifyBinary(): Boolean {
        if (!codexBinary.exists()) return false
        if (codexBinary.length() < 10_000_000) return false // 至少 10MB

        // 检查 ELF 头部
        try {
            val header = ByteArray(4)
            RandomAccessFile(codexBinary, "r").use { raf ->
                raf.readFully(header)
            }
            // ELF magic: 0x7F 0x45 0x4C 0x46
            return header[0] == 0x7F.toByte() &&
                   header[1] == 0x45.toByte() &&
                   header[2] == 0x4C.toByte() &&
                   header[3] == 0x46.toByte()
        } catch (e: Exception) {
            return false
        }
    }

    /**
     * 下载 Codex CLI（带进度回调）
     */
    suspend fun downloadWithProgress(
        onProgressCallback: ((progress: Long, total: Long) -> Unit)? = null
    ): Boolean = withContext(Dispatchers.IO) {
        // 确保目录存在
        codexDir.mkdirs()

        // 遍历所有镜像源，直到有一个成功
        for ((index, mirrorUrl) in MIRROR_URLS.withIndex()) {
            try {
                Log.i(TAG, "尝试下载源 #${index + 1}: $mirrorUrl")
                val success = downloadFromUrl(mirrorUrl, onProgressCallback)
                if (success) {
                    Log.i(TAG, "从源 #${index + 1} 下载成功")
                    return@withContext true
                }
            } catch (e: Exception) {
                Log.w(TAG, "源 #${index + 1} 下载失败: ${e.message}")
            }
        }

        Log.e(TAG, "所有下载源均不可用")
        false
    }

    /**
     * 从指定 URL 下载
     */
    private fun downloadFromUrl(
        urlString: String,
        onProgressCallback: ((progress: Long, total: Long) -> Unit)? = null
    ): Boolean {
        val url = URL(urlString)
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = 120_000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("Accept", "application/octet-stream")
        connection.setRequestProperty("User-Agent", "Codex-Android/1.0")

        connection.connect()

        val responseCode = connection.responseCode
        if (responseCode != HttpURLConnection.HTTP_OK) {
            throw IOException("HTTP $responseCode for $urlString")
        }

        val contentLength = connection.contentLength.toLong()
        Log.i(TAG, "开始下载 (大小: ${contentLength / 1024 / 1024}MB)")

        connection.inputStream.use { input ->
            FileOutputStream(archiveFile).use { output ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                var totalBytesRead: Long = 0

                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    totalBytesRead += bytesRead
                    onProgressCallback?.invoke(totalBytesRead, contentLength)
                }
            }
        }

        Log.i(TAG, "下载完成: ${archiveFile.length()} bytes")
        return true
    }

    /**
     * 从备用地址下载
     */
    private fun downloadFromFallback(
        onProgressCallback: ((progress: Long, total: Long) -> Unit)?
    ): Boolean {
        Log.i(TAG, "尝试从备用地址下载...")

        // 创建占位文件提示用户手动下载
        codexBinary.createNewFile()
        codexBinary.writeText(
            "Codex CLI 自动下载失败。\n" +
            "请手动下载: MIRROR_URLS[0]\n" +
            "然后复制到: ${codexBinary.absolutePath}\n" +
            "并执行: chmod +x ${codexBinary.name}"
        )
        return false
    }

    /**
     * 解压下载的 Codex 二进制
     */
    suspend fun extractBinary(): Boolean = withContext(Dispatchers.IO) {
        try {
            if (!archiveFile.exists()) {
                Log.e(TAG, "压缩包不存在: ${archiveFile.absolutePath}")
                return@withContext false
            }

            Log.i(TAG, "解压 Codex 二进制...")

            // 读取 tar.gz 文件
            // 先解压 gzip，然后提取 tar 中的 codex 二进制
            val tempDir = File(codexDir, "extract")
            tempDir.mkdirs()

            // 使用进程调用 tar 命令（如果可用）
            try {
                val process = ProcessBuilder()
                    .command("tar", "-xzf", archiveFile.absolutePath, "-C", tempDir.absolutePath)
                    .redirectErrorStream(true)
                    .start()
                val exitCode = process.waitFor()

                if (exitCode == 0) {
                    // 查找解压后的 codex 二进制
                    val extracted = tempDir.listFiles()?.firstOrNull { file ->
                        file.name == "codex" || file.name.endsWith("/codex") || !file.extension.let { it == "gz" || it == "tar" }
                    } ?: tempDir.resolve("codex")

                    // 如果没有直接找到 codex 文件，递归查找
                    val codexFile = findCodexBinary(tempDir)
                    if (codexFile != null && codexFile.exists()) {
                        codexFile.copyTo(codexBinary, overwrite = true)
                        codexBinary.setExecutable(true)
                        Log.i(TAG, "Codex 二进制解压完成: ${codexBinary.length()} bytes")
                        tempDir.deleteRecursively()
                        archiveFile.delete() // 清理压缩包
                        return@withContext true
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "tar 命令失败: ${e.message}")
            }

            // 如果 tar 命令不可用，使用 Java 方式
            return@withContext extractWithJava(tempDir)
        } catch (e: Exception) {
            Log.e(TAG, "解压失败", e)
            false
        }
    }

    /**
     * 递归查找 codex 二进制文件
     */
    private fun findCodexBinary(dir: File): File? {
        dir.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                val found = findCodexBinary(file)
                if (found != null) return found
            } else if (file.name == CODEX_BINARY_NAME || file.name.startsWith("codex")) {
                if (file.length() > 10_000_000) { // 大于 10MB
                    return file
                }
            }
        }
        return null
    }

    /**
     * 使用 Java 的 GZIP + Tar 解压
     */
    private fun extractWithJava(tempDir: File): Boolean {
        try {
            val tarFile = File(codexDir, "codex.tar")
            
            // 解压 gzip
            GZIPInputStream(FileInputStream(archiveFile)).use { gzis ->
                FileOutputStream(tarFile).use { fos ->
                    gzis.copyTo(fos)
                }
            }

            // 从 tar 提取 codex 二进制
            // 读取 tar 格式的头部并提取文件
            val buffer = ByteArray(8192)
            RandomAccessFile(tarFile, "r").use { raf ->
                while (raf.filePointer < raf.length()) {
                    // Tar 头部 512 字节
                    val header = ByteArray(512)
                    raf.readFully(header)

                    val name = String(header, 0, 100, Charsets.UTF_8).trimEnd('\u0000').trim()
                    if (name.isEmpty()) break

                    val sizeStr = String(header, 124, 12, Charsets.UTF_8).trimEnd('\u0000').trim()
                    val size = try {
                        Integer.parseInt(sizeStr.trim(), 8) // Tar 大小是八进制
                    } catch (e: NumberFormatException) {
                        0
                    }

                    if (size <= 0) continue

                    // 检查是否为 codex 二进制
                    val fileName = name.substringAfterLast('/')
                    if (fileName == CODEX_BINARY_NAME || fileName.startsWith("codex")) {
                        Log.i(TAG, "找到 Codex 二进制: $name (大小: $size)")
                        
                        FileOutputStream(codexBinary).use { fos ->
                            val dataBuffer = ByteArray(8192)
                            var remaining = size
                            while (remaining > 0) {
                                val toRead = minOf(dataBuffer.size, remaining)
                                val read = raf.read(dataBuffer, 0, toRead)
                                if (read == -1) break
                                fos.write(dataBuffer, 0, read)
                                remaining -= read
                            }
                        }

                        codexBinary.setExecutable(true)
                        Log.i(TAG, "Codex 二进制提取完成: ${codexBinary.length()} bytes")

                        // 清理
                        tarFile.delete()
                        tempDir.deleteRecursively()
                        archiveFile.delete()
                        return true
                    } else {
                        // 跳过文件数据
                        val padding = (512 - (size % 512)) % 512
                        raf.skipBytes(size + padding)
                    }
                }
            }

            Log.w(TAG, "在 tar 归档中未找到 codex 二进制")
            tarFile.delete()
            tempDir.deleteRecursively()
            return false
        } catch (e: Exception) {
            Log.e(TAG, "Java 解压失败", e)
            tempDir.deleteRecursively()
            return false
        }
    }

    /**
     * 创建默认 Codex 配置
     */
    fun createDefaultConfig(): Boolean {
        return try {
            val configContent = """
# Codex Android Configuration
model = "gpt-4o"
provider = "openai"
approval = "never"
sandbox = "off"
skip-git-repo-check = true
experimental_features = true

[mcp]
# MCP servers will be auto-configured

[plugins]
# Codex plugin system

[features]
codex-agent = true
codex-mcp = true
""".trimIndent()

            getConfigFile().parentFile?.mkdirs()
            getConfigFile().writeText(configContent)
            Log.i(TAG, "默认配置已创建")
            true
        } catch (e: Exception) {
            Log.e(TAG, "创建配置失败", e)
            false
        }
    }

    fun getConfigDir(): File = File(context.filesDir, ".codex").also { it.mkdirs() }
    fun getConfigFile(): File = File(getConfigDir(), "config.toml")

    /**
     * 清理下载文件
     */
    fun cleanup() {
        try {
            archiveFile.delete()
            codexBinary.delete()
            Log.i(TAG, "已清理 Codex 文件")
        } catch (e: Exception) {
            Log.w(TAG, "清理失败", e)
        }
    }
}
