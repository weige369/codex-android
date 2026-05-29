package com.codex.android.util

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * 开发环境管理器。
 *
 * 基于 Termux 提供完整的 Linux 开发环境：
 * - Termux 基础环境（必须）
 * - Ubuntu 发行版（通过 proot-distro）
 * - Node.js / Python / Git 等开发工具
 * - 环境健康监控
 *
 * Android 使用 Bionic libc，无法直接运行 glibc Linux 二进制。
 * Termux 提供 Linux 用户空间，proot-distro 提供 Ubuntu 发行版，
 * 这是 Codex CLI 和其他开发工具的运行基础。
 */
class DevelopmentEnvironment(private val context: Context) {

    companion object {
        private const val TAG = "DevelopmentEnvironment"

        // Termux 包名和路径
        const val TERMUX_PACKAGE = "com.termux"
        const val TERMUX_PREFIX = "/data/data/com.termux/files/usr"
        const val TERMUX_HOME = "/data/data/com.termux/files/home"
        const val TERMUX_BIN = "$TERMUX_PREFIX/bin"
        const val TERMUX_BASH = "$TERMUX_BIN/bash"

        // Ubuntu 路径（proot-distro 内）
        const val UBUNTU_ROOT = "/data/data/com.termux/files/usr/var/lib/proot-distro/installed-rootfs/ubuntu"
        const val UBUNTU_HOME = "$UBUNTU_ROOT/home"
        const val UBUNTU_BIN = "$UBUNTU_ROOT/bin"
        const val UBUNTU_USR_BIN = "$UBUNTU_ROOT/usr/bin"
    }

    /**
     * 环境状态
     */
    enum class EnvState {
        /** Termux 未安装 */
        TERMUX_MISSING,
        /** Termux 已安装，工具未就绪 */
        TERMUX_READY,
        /** Ubuntu 正在安装 */
        UBUNTU_INSTALLING,
        /** Ubuntu 已安装 */
        UBUNTU_READY,
        /** 完整环境就绪（Ubuntu + Node.js + Python + Git） */
        FULLY_READY,
        /** 环境出错 */
        ERROR
    }

    /**
     * 环境信息
     */
    data class EnvInfo(
        val state: EnvState = EnvState.TERMUX_MISSING,
        val termuxVersion: String = "",
        val hasNodeJs: Boolean = false,
        val hasPython: Boolean = false,
        val hasGit: Boolean = false,
        val hasUbuntu: Boolean = false,
        val hasCodex: Boolean = false,
        val nodeVersion: String = "",
        val pythonVersion: String = "",
        val gitVersion: String = "",
        val ubuntuVersion: String = "",
        val errorMessage: String = ""
    )

    /**
     * 检测 Termux 安装状态
     */
    fun detectTermux(): Boolean {
        return try {
            context.packageManager.getPackageInfo(TERMUX_PACKAGE, 0)
            Log.i(TAG, "Termux 已安装")
            true
        } catch (e: PackageManager.NameNotFoundException) {
            Log.w(TAG, "Termux 未安装")
            false
        }
    }

    /**
     * 完整环境检测
     */
    suspend fun getEnvironmentInfo(): EnvInfo = withContext(Dispatchers.IO) {
        if (!detectTermux()) {
            return@withContext EnvInfo(state = EnvState.TERMUX_MISSING)
        }

        try {
            val nodeVer = runInTermuxForOutput("node --version 2>/dev/null || echo ''")
            val pyVer = runInTermuxForOutput("python3 --version 2>/dev/null || python --version 2>/dev/null || echo ''")
            val gitVer = runInTermuxForOutput("git --version 2>/dev/null || echo ''")
            val ubuntuCheck = File(UBUNTU_ROOT).exists()
            val ubuntuVer = if (ubuntuCheck) {
                runInTermuxForOutput("proot-distro login ubuntu -- cat /etc/os-release 2>/dev/null | grep PRETTY_NAME || echo 'Ubuntu'")
            } else ""
            val codexCheck = runInTermuxForOutput("command -v codex 2>/dev/null && codex --version 2>/dev/null || echo ''")
            val termuxVer = try {
                context.packageManager.getPackageInfo(TERMUX_PACKAGE, 0).versionName ?: ""
            } catch (_: Exception) { "" }

            val hasNode = nodeVer.isNotBlank()
            val hasPython = pyVer.isNotBlank()
            val hasGit = gitVer.isNotBlank()
            val hasUbuntu = ubuntuCheck
            val hasCodex = codexCheck.isNotBlank()

            val state = when {
                hasUbuntu && hasNode && hasPython && hasGit -> EnvState.FULLY_READY
                hasUbuntu -> EnvState.UBUNTU_READY
                else -> EnvState.TERMUX_READY
            }

            EnvInfo(
                state = state,
                termuxVersion = termuxVer,
                hasNodeJs = hasNode,
                hasPython = hasPython,
                hasGit = hasGit,
                hasUbuntu = hasUbuntu,
                hasCodex = hasCodex,
                nodeVersion = nodeVer.trim(),
                pythonVersion = pyVer.trim(),
                gitVersion = gitVer.trim(),
                ubuntuVersion = ubuntuVer.trim()
            )
        } catch (e: Exception) {
            Log.e(TAG, "环境检测失败", e)
            EnvInfo(state = EnvState.ERROR, errorMessage = e.message ?: "检测失败")
        }
    }

    /**
     * 安装 Ubuntu 环境（通过 proot-distro）
     */
    suspend fun installUbuntu(
        onProgress: ((String) -> Unit)? = null
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            if (!detectTermux()) {
                onProgress?.invoke("Termux 未安装，请先安装 Termux")
                return@withContext false
            }

            onProgress?.invoke("正在安装 proot-distro...")
            // 确保 proot-distro 已安装
            var result = runInTermuxSync("pkg install -y proot-distro 2>&1")
            Log.i(TAG, "proot-distro 安装结果: ${result.first}")

            if (result.first != 0) {
                onProgress?.invoke("proot-distro 安装失败")
                return@withContext false
            }

            onProgress?.invoke("正在安装 Ubuntu (需要 3-5 分钟，请耐心等待)...")
            onProgress?.invoke("下载 Ubuntu rootfs...")

            // 安装 Ubuntu
            result = runInTermuxSync("proot-distro install ubuntu 2>&1")
            Log.i(TAG, "Ubuntu 安装结果: ${result.first}")

            if (result.first == 0) {
                onProgress?.invoke("Ubuntu 安装完成！")
                
                // 更新 Ubuntu 内的包
                onProgress?.invoke("更新 Ubuntu 软件源...")
                runInTermuxSync("proot-distro login ubuntu -- apt update -y 2>&1")
                
                onProgress?.invoke("安装基础开发工具...")
                runInTermuxSync("proot-distro login ubuntu -- apt install -y curl wget git build-essential 2>&1")
                
                return@withContext true
            } else {
                onProgress?.invoke("Ubuntu 安装失败: ${result.second.take(200)}")
                return@withContext false
            }
        } catch (e: Exception) {
            Log.e(TAG, "安装 Ubuntu 失败", e)
            onProgress?.invoke("安装出错: ${e.message}")
            false
        }
    }

    /**
     * 在 Termux 中安装开发工具
     */
    suspend fun installDevTools(
        onProgress: ((String) -> Unit)? = null
    ): Boolean = withContext(Dispatchers.IO) {
        if (!detectTermux()) {
            onProgress?.invoke("Termux 未安装")
            return@withContext false
        }

        try {
            // 更新源
            onProgress?.invoke("更新软件源...")
            runInTermuxSync("pkg upgrade -y 2>&1")

            // Node.js (LTS)
            onProgress?.invoke("安装 Node.js...")
            var result = runInTermuxSync("pkg install -y nodejs-lts 2>&1")
            if (result.first == 0) {
                val ver = runInTermuxForOutput("node --version")
                onProgress?.invoke("Node.js $ver 安装完成")
            }

            // Python
            onProgress?.invoke("安装 Python...")
            result = runInTermuxSync("pkg install -y python 2>&1")
            if (result.first == 0) {
                val ver = runInTermuxForOutput("python --version")
                onProgress?.invoke("Python $ver 安装完成")
            }

            // Git
            onProgress?.invoke("安装 Git...")
            result = runInTermuxSync("pkg install -y git 2>&1")
            if (result.first == 0) {
                val ver = runInTermuxForOutput("git --version")
                onProgress?.invoke("Git $ver 安装完成")
            }

            // 常用开发工具
            onProgress?.invoke("安装常用开发工具...")
            runInTermuxSync("pkg install -y curl wget cmake openssh nano 2>&1")

            // 可选工具
            runInTermuxSync("pkg install -y rust 2>&1 || true")

            onProgress?.invoke("开发工具安装完成！")
            true
        } catch (e: Exception) {
            Log.e(TAG, "安装开发工具失败", e)
            onProgress?.invoke("安装失败: ${e.message}")
            false
        }
    }

    /**
     * 在 Ubuntu 环境中安装开发工具
     */
    suspend fun installToolsInUbuntu(
        onProgress: ((String) -> Unit)? = null
    ): Boolean = withContext(Dispatchers.IO) {
        if (!File(UBUNTU_ROOT).exists()) {
            onProgress?.invoke("Ubuntu 未安装，请先安装 Ubuntu")
            return@withContext false
        }

        try {
            onProgress?.invoke("在 Ubuntu 中安装 Node.js...")
            runInTermuxSync("""proot-distro login ubuntu -- bash -c '
                curl -fsSL https://deb.nodesource.com/setup_lts.x | bash - && apt install -y nodejs
            ' 2>&1""")

            onProgress?.invoke("在 Ubuntu 中安装 Python 和开发工具...")
            runInTermuxSync("proot-distro login ubuntu -- apt install -y python3 python3-pip build-essential 2>&1")

            onProgress?.invoke("Ubuntu 环境配置完成！")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Ubuntu 工具安装失败", e)
            onProgress?.invoke("安装失败: ${e.message}")
            false
        }
    }

    /**
     * 在指定环境中执行命令
     *
     * @param command 命令
     * @param useUbuntu 是否在 Ubuntu proot 中执行
     * @param workDir 工作目录
     * @param env 额外环境变量
     */
    fun executeCommand(
        command: String,
        useUbuntu: Boolean = false,
        workDir: String = TERMUX_HOME,
        env: Map<String, String> = emptyMap()
    ): Process {
        return if (useUbuntu && File(UBUNTU_ROOT).exists()) {
            val fullCmd = "proot-distro login ubuntu -- bash -c 'cd \"$workDir\" && $command'"
            runInTermux(fullCmd, env)
        } else {
            // Use ProcessBuilder with working directory
            val pb = ProcessBuilder(
                TERMUX_BASH, "-c", command
            ).apply {
                environment().putAll(mapOf(
                    "PATH" to "$TERMUX_BIN:/system/bin:/system/xbin",
                    "HOME" to TERMUX_HOME,
                    "PREFIX" to TERMUX_PREFIX,
                    "TMPDIR" to "$TERMUX_PREFIX/tmp",
                    "LD_LIBRARY_PATH" to "$TERMUX_PREFIX/lib"
                ))
                environment().putAll(env)
                directory(File(workDir))
                redirectErrorStream(true)
            }
            pb.start()
        }
    }

    /**
     * 执行命令并等待结果
     */
    suspend fun executeCommandSync(
        command: String,
        useUbuntu: Boolean = false,
        workDir: String = TERMUX_HOME,
        timeoutMs: Long = 120_000
    ): AndroidShellExecutor.ShellResult = withContext(Dispatchers.IO) {
        try {
            val process = executeCommand(command, useUbuntu, workDir)
            val stdout = StringBuilder()
            val stderr = StringBuilder()

            val stdoutThread = Thread {
                try { process.inputStream.bufferedReader().use { r -> r.lines().forEach { stdout.appendLine(it) } } }
                catch (_: Exception) {}
            }
            val stderrThread = Thread {
                try { process.errorStream.bufferedReader().use { r -> r.lines().forEach { stderr.appendLine(it) } } }
                catch (_: Exception) {}
            }
            stdoutThread.start(); stderrThread.start()

            val finished = process.waitFor(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
            stdoutThread.join(1000); stderrThread.join(1000)

            if (finished) {
                AndroidShellExecutor.ShellResult(process.exitValue(), stdout.toString(), stderr.toString())
            } else {
                process.destroyForcibly()
                AndroidShellExecutor.ShellResult(-1, stdout.toString(), stderr.toString(), isTimedOut = true)
            }
        } catch (e: Exception) {
            Log.e(TAG, "执行失败: $command", e)
            AndroidShellExecutor.ShellResult(-1, "", "执行失败: ${e.message}")
        }
    }

    /**
     * 运行 Termux 命令（异步，返回 Process）
     */
    fun runInTermux(
        command: String,
        env: Map<String, String> = emptyMap()
    ): Process {
        val pb = ProcessBuilder(
            TERMUX_BASH, "-c", command
        ).apply {
            environment().putAll(mapOf(
                "PATH" to "$TERMUX_BIN:/system/bin:/system/xbin",
                "HOME" to TERMUX_HOME,
                "PREFIX" to TERMUX_PREFIX,
                "TMPDIR" to "$TERMUX_PREFIX/tmp",
                "LD_LIBRARY_PATH" to "$TERMUX_PREFIX/lib"
            ))
            environment().putAll(env)
            directory(File(TERMUX_HOME))
            redirectErrorStream(true) // 合并 stdout/stderr
        }
        return pb.start()
    }

    /**
     * 运行命令并返回 stdout（快速检测用）
     */
    private fun runInTermuxForOutput(command: String): String {
        return try {
            val process = runInTermux(command)
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = reader.readText().trim()
            process.waitFor()
            output
        } catch (e: Exception) { "" }
    }

    /**
     * 运行命令并返回 (exitCode, stdout)
     */
    private fun runInTermuxSync(command: String): Pair<Int, String> {
        return try {
            val process = runInTermux(command)
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = reader.readText().trim()
            val exitCode = process.waitFor()
            Pair(exitCode, output)
        } catch (e: Exception) { Pair(-1, e.message ?: "unknown") }
    }

    /**
     * 获取 Ubuntu 的 /etc/os-release
     */
    fun getUbuntuVersion(): String {
        return try {
            val file = File("$UBUNTU_ROOT/etc/os-release")
            if (file.exists()) {
                file.readLines().firstOrNull { it.startsWith("PRETTY_NAME=") }
                    ?.removePrefix("PRETTY_NAME=")?.trim('"') ?: "Ubuntu"
            } else ""
        } catch (_: Exception) { "" }
    }

    /**
     * 检查 Ubuntu 是否已安装
     */
    fun isUbuntuInstalled(): Boolean = File(UBUNTU_ROOT).exists()

    /**
     * 卸载 Ubuntu
     */
    suspend fun removeUbuntu(onProgress: ((String) -> Unit)? = null): Boolean = withContext(Dispatchers.IO) {
        if (!detectTermux()) return@withContext false
        try {
            onProgress?.invoke("正在卸载 Ubuntu...")
            val result = runInTermuxSync("proot-distro remove ubuntu 2>&1")
            onProgress?.invoke("Ubuntu 已卸载")
            result.first == 0
        } catch (e: Exception) {
            Log.e(TAG, "卸载 Ubuntu 失败", e)
            false
        }
    }

    /**
     * 将文件安装到 Termux 系统
     */
    suspend fun installBinaryToTermux(sourceFile: File, targetName: String): Boolean = withContext(Dispatchers.IO) {
        if (!detectTermux()) return@withContext false
        try {
            val encoded = java.util.Base64.getEncoder().encodeToString(sourceFile.readBytes())
            val cmd = "echo '$encoded' | base64 -d > '$TERMUX_BIN/$targetName' && chmod +x '$TERMUX_BIN/$targetName'"
            val result = runInTermuxSync(cmd)
            Log.i(TAG, "安装 $targetName 结果: exit=${result.first}")
            result.first == 0
        } catch (e: Exception) {
            Log.e(TAG, "安装二进制失败", e)
            false
        }
    }

    /**
     * 获取安装引导文本
     */
    fun getSetupGuide(): String {
        return """
╔══════════════════════════════════════╗
║      Codex Android 开发环境安装      ║
╚══════════════════════════════════════╝

步骤 1: 安装 Termux
从 F-Droid 安装 Termux:
https://f-droid.org/packages/com.termux/

步骤 2: 打开 Termux 并执行:
  pkg upgrade -y
  pkg install -y proot-distro

步骤 3: 安装 Ubuntu
  proot-distro install ubuntu

步骤 4: 安装开发工具
  pkg install -y nodejs-lts python git

步骤 5: 返回本 App 点击"刷新环境"

完成后你将获得:
• Ubuntu 24.04 LTS 环境
• Node.js / Python / Git
• 完整的 Linux 开发体验
        """.trimIndent()
    }
}
