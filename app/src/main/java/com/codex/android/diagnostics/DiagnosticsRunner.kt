package com.codex.android.diagnostics

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log
import android.webkit.WebView
import com.codex.android.bridge.CodexBridge
import com.codex.android.codex.CodexManager
import com.codex.android.util.AndroidShellExecutor
import com.codex.android.util.DevelopmentEnvironment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * APP 全方位自测系统。
 *
 * 自动检测所有模块的健康状态，定位问题根因。
 * 测试项覆盖：UI/WebView、桥接通信、运行时环境、
 * 开发环境、文件系统、网络、权限等。
 */
class DiagnosticsRunner(private val context: Context) {

    companion object {
        private const val TAG = "DiagnosticsRunner"
    }

    /** 单条测试结果 */
    data class TestResult(
        val name: String,            // 测试名称
        val passed: Boolean,         // 是否通过
        val detail: String,          // 结果详情
        val severity: Severity = if (passed) Severity.INFO else Severity.ERROR,
        val suggestion: String = ""  // 修复建议
    )

    enum class Severity { INFO, WARNING, ERROR }

    /** 完整的诊断报告 */
    data class DiagnosticsReport(
        val timestamp: Long = System.currentTimeMillis(),
        val deviceInfo: DeviceInfo = DeviceInfo(),
        val results: List<TestResult> = emptyList()
    ) {
        val passedCount: Int get() = results.count { it.passed }
        val failedCount: Int get() = results.count { !it.passed }
        val totalCount: Int get() = results.size
        val isAllPassed: Boolean get() = failedCount == 0
    }

    data class DeviceInfo(
        val androidVersion: String = "${Build.VERSION.SDK_INT} (${Build.VERSION.RELEASE})",
        val device: String = "${Build.MANUFACTURER} ${Build.MODEL}",
        val arch: String = Build.SUPPORTED_ABIS.joinToString(", "),
        val memory: String = "${Runtime.getRuntime().totalMemory() / 1024 / 1024}MB / ${Runtime.getRuntime().maxMemory() / 1024 / 1024}MB",
        val diskSpace: String = "${android.os.Environment.getExternalStorageDirectory().totalSpace / 1024 / 1024}MB 可用"
    )

    /**
     * 运行全部诊断测试
     */
    suspend fun runAll(): DiagnosticsReport = withContext(Dispatchers.IO) {
        val results = mutableListOf<TestResult>()

        // 1. 设备基础信息
        results.addAll(runDeviceTests())

        // 2. 权限测试
        results.addAll(runPermissionTests())

        // 3. 网络测试
        results.addAll(runNetworkTests())

        // 4. WebView 与桥接测试
        results.addAll(runBridgeTests())

        // 5. 文件系统测试
        results.addAll(runFileSystemTests())

        // 6. Codex 二进制测试
        results.addAll(runCodexBinaryTests())

        // 7. 开发环境测试
        results.addAll(runDevEnvironmentTests())

        // 8. Shell 执行测试
        results.addAll(runShellTests())

        DiagnosticsReport(
            deviceInfo = DeviceInfo(),
            results = results
        )
    }

    /**
     * 运行指定分类的测试
     */
    suspend fun runCategory(category: String): List<TestResult> = withContext(Dispatchers.IO) {
        when (category) {
            "device" -> runDeviceTests()
            "permissions" -> runPermissionTests()
            "network" -> runNetworkTests()
            "bridge" -> runBridgeTests()
            "filesystem" -> runFileSystemTests()
            "codex" -> runCodexBinaryTests()
            "environment" -> runDevEnvironmentTests()
            "shell" -> runShellTests()
            else -> emptyList()
        }
    }

    // ====== 1. 设备基础测试 ======
    private fun runDeviceTests(): List<TestResult> {
        return listOf(
            TestResult(
                "Android 版本", true,
                "API ${Build.VERSION.SDK_INT} (${Build.VERSION.RELEASE})"
            ),
            TestResult(
                "设备型号", true,
                "${Build.MANUFACTURER} ${Build.MODEL}"
            ),
            TestResult(
                "CPU 架构", true,
                Build.SUPPORTED_ABIS.joinToString(", ")
            ),
            TestResult(
                "内存配置", true,
                "堆内存: ${Runtime.getRuntime().maxMemory() / 1024 / 1024}MB"
            ),
            TestResult(
                "是否模拟器", Build.FINGERPRINT.contains("generic") ||
                        Build.FINGERPRINT.contains("emulator"),
                if (Build.FINGERPRINT.contains("generic")) "可能运行在模拟器上" else "物理设备",
                severity = if (Build.FINGERPRINT.contains("generic")) Severity.WARNING else Severity.INFO
            )
        )
    }

    // ====== 2. 权限测试 ======
    private fun runPermissionTests(): List<TestResult> {
        val results = mutableListOf<TestResult>()

        // 检查必要权限
        val permissions = mapOf(
            "INTERNET" to android.Manifest.permission.INTERNET,
            "FOREGROUND_SERVICE" to android.Manifest.permission.FOREGROUND_SERVICE,
            "POST_NOTIFICATIONS" to android.Manifest.permission.POST_NOTIFICATIONS,
        )

        for ((name, perm) in permissions) {
            val granted = try {
                context.checkSelfPermission(perm) == android.content.pm.PackageManager.PERMISSION_GRANTED
            } catch (e: Exception) { false }
            results.add(TestResult(
                "权限: $name", granted,
                if (granted) "已授予" else "未授予",
                severity = if (granted) Severity.INFO else Severity.WARNING,
                suggestion = if (!granted) "请授予 $name 权限" else ""
            ))
        }

        // Shizuku 检测
        val hasShizuku = try {
            Class.forName("moe.shizuku.api.ShizukuApi")
            true
        } catch (_: Exception) { false }
        results.add(TestResult(
            "Shizuku API", hasShizuku,
            if (hasShizuku) "可用" else "未安装",
            severity = Severity.INFO
        ))

        return results
    }

    // ====== 3. 网络测试 ======
    private fun runNetworkTests(): List<TestResult> {
        val results = mutableListOf<TestResult>()

        // 网络连接检测
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val network = cm?.activeNetwork
        val caps = network?.let { cm.getNetworkCapabilities(it) }
        val hasInternet = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true

        results.add(TestResult(
            "网络连接", hasInternet,
            if (hasInternet) "已连接" else "未连接",
            severity = if (hasInternet) Severity.INFO else Severity.ERROR,
            suggestion = if (!hasInternet) "请连接网络" else ""
        ))

        // GitHub API 可达性
        val githubReachable = try {
            val url = java.net.URL("https://api.github.com")
            val conn = url.openConnection() as java.net.HttpURLConnection
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.responseCode == 200
        } catch (e: Exception) { false }
        results.add(TestResult(
            "GitHub API 可达", githubReachable,
            if (githubReachable) "正常" else "连接失败",
            severity = if (githubReachable) Severity.INFO else Severity.ERROR,
            suggestion = if (!githubReachable) "检查网络或防火墙" else ""
        ))

        // OpenAI API 可达性
        val openaiReachable = try {
            val url = java.net.URL("https://api.openai.com")
            val conn = url.openConnection() as java.net.HttpURLConnection
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.responseCode in 200..499
        } catch (e: Exception) { false }
        results.add(TestResult(
            "OpenAI API 可达", openaiReachable,
            if (openaiReachable) "正常" else "连接失败",
            severity = Severity.INFO
        ))

        // 本地端口检查 (Codex WebSocket)
        val wsPortOpen = try {
            val s = java.net.ServerSocket(9877)
            s.close()
            false // 端口可用说明 Codex 没在运行
        } catch (e: Exception) {
            true // 端口被占用说明已在运行
        }
        results.add(TestResult(
            "Codex WebSocket 端口 (9877)", true,
            if (wsPortOpen) "Codex 正在运行" else "端口空闲",
            severity = Severity.INFO
        ))

        return results
    }

    // ====== 4. WebView & 桥接测试 ======
    private fun runBridgeTests(): List<TestResult> {
        val results = mutableListOf<TestResult>()

        // WebView 版本
        val wvVersion = try {
            WebView.getCurrentWebViewPackage()?.versionName ?: "未知"
        } catch (e: Exception) { "未知" }
        results.add(TestResult(
            "WebView 版本", true, wvVersion
        ))

        // WebView 可用性
        try {
            val wv = WebView(context)
            wv.destroy()
            results.add(TestResult("WebView 创建", true, "正常"))
        } catch (e: Exception) {
            results.add(TestResult("WebView 创建", false, "失败: ${e.message}", Severity.ERROR))
        }

        // HTML 资源检查
        val htmlExists = File(context.filesDir.parentFile?.parentFile, "app/src/main/assets/web/codex-ui.html")
            .exists() || try {
            context.assets.open("web/codex-ui.html").use { true }
        } catch (e: Exception) { false }
        results.add(TestResult(
            "Codex UI HTML 资源", htmlExists,
            if (htmlExists) "存在" else "缺失",
            severity = if (htmlExists) Severity.INFO else Severity.ERROR,
            suggestion = if (!htmlExists) "assets/web/codex-ui.html 文件缺失" else ""
        ))

        // Bridge 对象可用性
        val bridgeAvailable = try {
            Class.forName("com.codex.android.bridge.CodexBridge")
            true
        } catch (e: Exception) { false }
        results.add(TestResult(
            "CodexBridge 类", bridgeAvailable,
            if (bridgeAvailable) "正常" else "缺失",
            severity = if (bridgeAvailable) Severity.INFO else Severity.ERROR
        ))

        return results
    }

    // ====== 5. 文件系统测试 ======
    private fun runFileSystemTests(): List<TestResult> {
        val results = mutableListOf<TestResult>()

        val codexDir = File(context.filesDir, "codex")
        val workspaceDir = File(context.filesDir, "workspace")
        val configDir = File(context.filesDir, ".codex")

        // 目录创建
        val dirsOk = codexDir.mkdirs() && workspaceDir.mkdirs() && configDir.mkdirs()
        results.add(TestResult(
            "文件目录访问", dirsOk,
            "codex: ${codexDir.exists()} | workspace: ${workspaceDir.exists()} | .codex: ${configDir.exists()}",
            severity = if (dirsOk) Severity.INFO else Severity.ERROR
        ))

        // 写入测试
        val writeOk = try {
            val testFile = File(codexDir, ".write_test")
            testFile.writeText("ok")
            val readBack = testFile.readText()
            testFile.delete()
            readBack == "ok"
        } catch (e: Exception) { false }
        results.add(TestResult(
            "文件写入权限", writeOk,
            if (writeOk) "正常" else "写入失败",
            severity = if (writeOk) Severity.INFO else Severity.ERROR
        ))

        // 可用空间
        try {
            val dirPath = context.filesDir.parentFile?.absolutePath ?: context.filesDir.absolutePath
            val dir = java.io.File(dirPath)
            val free = dir.freeSpace / 1024 / 1024
            val enough = free > 200
            results.add(TestResult(
                "存储空间", enough,
                "${free}MB 可用 (建议 >200MB)",
                severity = if (enough) Severity.INFO else Severity.WARNING,
                suggestion = if (!enough) "存储空间不足，请清理" else ""
            ))
        } catch (e: Exception) {
            results.add(TestResult("存储空间检测", false, "检测失败: ${e.message}"))
        }

        return results
    }

    // ====== 6. Codex 二进制测试 ======
    private fun runCodexBinaryTests(): List<TestResult> {
        val results = mutableListOf<TestResult>()
        val codexManager = CodexManager(context)
        val binary = codexManager.codexBinary

        // 二进制是否存在
        val exists = binary.exists()
        results.add(TestResult(
            "Codex 二进制文件", exists,
            if (exists) "${binary.length() / 1024 / 1024}MB" else "未下载",
            severity = if (exists) Severity.INFO else Severity.WARNING,
            suggestion = if (!exists) "点击启动 Codex 自动下载" else ""
        ))

        // 二进制完整性校验
        if (exists) {
            val valid = codexManager.verifyBinary()
            results.add(TestResult(
                "Codex 二进制完整性", valid,
                if (valid) "ELF 头校验通过" else "校验失败，文件损坏",
                severity = if (valid) Severity.INFO else Severity.ERROR,
                suggestion = if (!valid) "删除重试: codexManager.cleanup()" else ""
            ))
        }

        // 本地下载 URL 可达性
        val downloadUrl = "https://github.com/openai/codex/releases/download/rust-v" + CodexManager.CODEX_VERSION + "/codex-aarch64-unknown-linux-musl.tar.gz"
        val urlReachable = try {
            val url = java.net.URL(downloadUrl)
            val conn = url.openConnection() as java.net.HttpURLConnection
            conn.connectTimeout = 5000
            conn.setRequestProperty("User-Agent", "Codex-Android/1.0")
            conn.responseCode in 200..399
        } catch (e: Exception) { false }
        results.add(TestResult(
            "Codex 下载源可达", urlReachable,
            if (urlReachable) "GitHub Release 可访问" else "连接失败",
            severity = if (urlReachable) Severity.INFO else Severity.WARNING
        ))

        return results
    }

    // ====== 7. 开发环境测试 ======
    private suspend fun runDevEnvironmentTests(): List<TestResult> {
        val results = mutableListOf<TestResult>()
        val devEnv = DevelopmentEnvironment(context)

        // Termux 检测
        val hasTermux = devEnv.detectTermux()
        results.add(TestResult(
            "Termux", hasTermux,
            if (hasTermux) "已安装" else "未安装",
            severity = if (hasTermux) Severity.INFO else Severity.WARNING,
            suggestion = if (!hasTermux) "从 F-Droid 安装 Termux" else ""
        ))

        if (hasTermux) {
            val envInfo = devEnv.getEnvironmentInfo()

            results.add(TestResult(
                "Node.js", envInfo.hasNodeJs,
                if (envInfo.hasNodeJs) envInfo.nodeVersion else "未安装",
                severity = if (envInfo.hasNodeJs) Severity.INFO else Severity.WARNING,
                suggestion = if (!envInfo.hasNodeJs) "在'环境'页面安装开发工具" else ""
            ))
            results.add(TestResult(
                "Python", envInfo.hasPython,
                if (envInfo.hasPython) envInfo.pythonVersion else "未安装",
                severity = if (envInfo.hasPython) Severity.INFO else Severity.WARNING,
                suggestion = if (!envInfo.hasPython) "在'环境'页面安装开发工具" else ""
            ))
            results.add(TestResult(
                "Git", envInfo.hasGit,
                if (envInfo.hasGit) envInfo.gitVersion else "未安装",
                severity = if (envInfo.hasGit) Severity.INFO else Severity.WARNING
            ))
            results.add(TestResult(
                "Ubuntu (proot-distro)", envInfo.hasUbuntu,
                if (envInfo.hasUbuntu) envInfo.ubuntuVersion else "未安装",
                severity = if (envInfo.hasUbuntu) Severity.INFO else Severity.INFO
            ))
        }

        return results
    }

    // ====== 8. Shell 执行测试 ======
    private suspend fun runShellTests(): List<TestResult> {
        val results = mutableListOf<TestResult>()

        // 基本 shell 执行
        try {
            val result = AndroidShellExecutor.execute("echo 'hello'", permissionLevel = AndroidShellExecutor.PermissionLevel.NORMAL)
            results.add(TestResult(
                "Shell 执行 (NORMAL)", result.exitCode == 0,
                "exit=${result.exitCode}: ${result.stdout.take(100)}",
                severity = if (result.exitCode == 0) Severity.INFO else Severity.ERROR
            ))
        } catch (e: Exception) {
            results.add(TestResult("Shell 执行 (NORMAL)", false, "异常: ${e.message}", Severity.ERROR))
        }

        // Termux shell 执行
        val devEnv = DevelopmentEnvironment(context)
        if (devEnv.detectTermux()) {
            try {
                val result = AndroidShellExecutor.execute("echo 'termux ok'", permissionLevel = AndroidShellExecutor.PermissionLevel.TERMUX)
                results.add(TestResult(
                    "Termux Shell 执行", result.exitCode == 0,
                    "exit=${result.exitCode}",
                    severity = if (result.exitCode == 0) Severity.INFO else Severity.ERROR
                ))
            } catch (e: Exception) {
                results.add(TestResult("Termux Shell 执行", false, "异常: ${e.message}", Severity.ERROR))
            }
        }

        // Root 可用性
        val hasRoot = AndroidShellExecutor.isRootAvailable()
        results.add(TestResult(
            "Root 权限", hasRoot,
            if (hasRoot) "可用" else "不可用",
            severity = Severity.INFO
        ))

        return results
    }
}
