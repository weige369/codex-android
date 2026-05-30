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
 * 开发环境管理器（自包含模式）。
 *
 * 不再强制依赖外部 Termux 安装。
 * 使用 APP 内置目录和 Android 系统 shell 实现自包含运行。
 * 如果 Termux 已安装且初始化完成，则优先使用 Termux 环境。
 */
class DevelopmentEnvironment(private val context: Context) {

    companion object {
        private const val TAG = "DevelopmentEnvironment"

        // Termux 包名和路径（外部，可选）
        const val TERMUX_PACKAGE = "com.termux"
        const val TERMUX_PREFIX = "/data/data/com.termux/files/usr"
        const val TERMUX_BIN = "$TERMUX_PREFIX/bin"
        const val TERMUX_HOME = "/data/data/com.termux/files/home"
        const val TERMUX_BASH = "$TERMUX_BIN/bash"

        // 自包含路径（在 APP 内部）
        const val APP_BIN = "bin"
        const val APP_HOME = "home"

        // Ubuntu 路径（通过 Termux proot-distro）
        const val UBUNTU_ROOT = "/data/data/com.termux/files/usr/var/lib/proot-distro/installed-rootfs/ubuntu"

        // Android 系统 shell
        const val SYSTEM_SH = "/system/bin/sh"
        const val SYSTEM_BIN = "/system/bin"
    }

    /**
     * 环境状态
     */
    enum class EnvState {
        /** 自包含模式（无 Termux） */
        SELF_CONTAINED,
        /** Termux 已安装 */
        TERMUX_READY,
        /** Ubuntu 已安装 */
        UBUNTU_READY,
        /** 环境出错 */
        ERROR
    }

    /**
     * 环境信息
     */
    data class EnvInfo(
        val state: EnvState = EnvState.SELF_CONTAINED,
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
     * 获取 APP 内部 bin 目录
     */
    fun getAppBinDir(): File = File(context.filesDir, APP_BIN).also { it.mkdirs() }

    /**
     * 获取 APP 内部 home 目录
     */
    fun getAppHomeDir(): File = File(context.filesDir, APP_HOME).also { it.mkdirs() }

    /**
     * 检测 Termux 是否已安装
     */
    fun detectTermux(): Boolean {
        return try {
            context.packageManager.getPackageInfo(TERMUX_PACKAGE, 0)
            // 验证 Termux bash 是否可执行
            File(TERMUX_BASH).canExecute()
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 完整环境检测
     */
    suspend fun getEnvironmentInfo(): EnvInfo = withContext(Dispatchers.IO) {
        // 检查自身 bin 目录
        val appBinDir = getAppBinDir()
        val hasCodexSelf = File(appBinDir, "codex").canExecute()

        if (!detectTermux()) {
            return@withContext EnvInfo(
                state = EnvState.SELF_CONTAINED,
                hasCodex = hasCodexSelf
            )
        }

        try {
            val nodeVer = runInExternalShell("node --version 2>/dev/null || echo ''")
            val pyVer = runInExternalShell("python3 --version 2>/dev/null || python --version 2>/dev/null || echo ''")
            val gitVer = runInExternalShell("git --version 2>/dev/null || echo ''")
            val ubuntuCheck = File(UBUNTU_ROOT).exists()
            val ubuntuVer = if (ubuntuCheck) {
                runInExternalShell("proot-distro login ubuntu -- cat /etc/os-release 2>/dev/null | grep PRETTY_NAME || echo 'Ubuntu'")
            } else ""
            val codexCheck = runInExternalShell("command -v codex 2>/dev/null && codex --version 2>/dev/null || echo ''")
            val termuxVer = try {
                context.packageManager.getPackageInfo(TERMUX_PACKAGE, 0).versionName ?: ""
            } catch (_: Exception) { "" }

            val hasNode = nodeVer.isNotBlank()
            val hasPython = pyVer.isNotBlank()
            val hasGit = gitVer.isNotBlank()
            val hasUbuntu = ubuntuCheck
            val hasCodex = codexCheck.isNotBlank() || hasCodexSelf

            val state = when {
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
            Log.e(TAG, "环境检测异常", e)
            EnvInfo(state = EnvState.ERROR, errorMessage = e.message ?: "")
        }
    }

    /**
     * 执行命令（自动选择最佳 shell）
     * 优先 Termux bash，其次 Android sh，最后直接执行
     */
    fun executeCommand(
        command: String,
        env: Map<String, String> = emptyMap()
    ): AndroidShellExecutor.ShellResult {
        return try {
            // 尝试在 Termux 中执行
            if (detectTermux()) {
                val result = executeInTermux(command, env)
                if (result.exitCode != -1) return result
            }
            // 尝试在 Android sh 中执行
            executeInSystemShell(command, env)
        } catch (e: Exception) {
            Log.e(TAG, "命令执行失败: $command", e)
            AndroidShellExecutor.ShellResult(-1, "", "执行失败: ${e.message}")
        }
    }

    /**
     * 在 Termux bash 中执行（同步，带超时）
     */
    private fun executeInTermux(
        command: String,
        env: Map<String, String> = emptyMap(),
        timeoutMs: Long = 30_000
    ): AndroidShellExecutor.ShellResult {
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
            redirectErrorStream(true)
        }

        return runProcess(pb, timeoutMs)
    }

    /**
     * 在 Android 系统 shell 中执行
     */
    private fun executeInSystemShell(
        command: String,
        env: Map<String, String> = emptyMap(),
        timeoutMs: Long = 30_000
    ): AndroidShellExecutor.ShellResult {
        val appBin = getAppBinDir().absolutePath
        val pb = ProcessBuilder(
            SYSTEM_SH, "-c", command
        ).apply {
            environment().putAll(mapOf(
                "PATH" to "$appBin:$SYSTEM_BIN:/system/xbin",
                "HOME" to getAppHomeDir().absolutePath,
                "TMPDIR" to "${context.cacheDir.absolutePath}",
            ))
            environment().putAll(env)
            directory(getAppHomeDir())
            redirectErrorStream(true)
        }

        return runProcess(pb, timeoutMs)
    }

    /**
     * 运行进程并收集输出
     */
    private fun runProcess(pb: ProcessBuilder, timeoutMs: Long): AndroidShellExecutor.ShellResult {
        val process = pb.start()
        val stdout = StringBuffer()
        val stderr = StringBuffer()

        val stdoutThread = Thread {
            process.inputStream.bufferedReader().use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    stdout.appendLine(line)
                }
            }
        }
        val stderrThread = Thread {
            process.errorStream.bufferedReader().use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    stderr.appendLine(line)
                }
            }
        }

        stdoutThread.start()
        stderrThread.start()

        val finished = try {
            process.waitFor(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
        } catch (e: InterruptedException) {
            false
        }
        stdoutThread.join(1000)
        stderrThread.join(1000)

        return if (finished) {
            AndroidShellExecutor.ShellResult(process.exitValue(), stdout.toString(), stderr.toString())
        } else {
            process.destroyForcibly()
            AndroidShellExecutor.ShellResult(-1, stdout.toString(), stderr.toString(), isTimedOut = true)
        }
    }

    /**
     * 运行 Termux 命令（异步，返回 Process）
     * 内部使用，保持向后兼容
     */
    fun runInTermux(
        command: String,
        env: Map<String, String> = emptyMap()
    ): Process {
        // 优先使用 Termux
        if (detectTermux()) {
            return startTermuxProcess(command, env)
        }
        // 回退到系统 shell
        return startSystemProcess(command, env)
    }

    /**
     * 启动 Termux 进程
     */
    private fun startTermuxProcess(command: String, env: Map<String, String>): Process {
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
            redirectErrorStream(true)
        }
        return pb.start()
    }

    /**
     * 启动系统 shell 进程
     */
    private fun startSystemProcess(command: String, env: Map<String, String>): Process {
        val appBin = getAppBinDir().absolutePath
        val pb = ProcessBuilder(
            SYSTEM_SH, "-c", command
        ).apply {
            environment().putAll(mapOf(
                "PATH" to "$appBin:$SYSTEM_BIN:/system/xbin",
                "HOME" to getAppHomeDir().absolutePath,
                "TMPDIR" to "${context.cacheDir.absolutePath}",
            ))
            environment().putAll(env)
            directory(getAppHomeDir())
            redirectErrorStream(true)
        }
        return pb.start()
    }

    /**
     * 运行命令并返回 stdout（快速检测用）
     */
    private fun runInExternalShell(command: String): String {
        return try {
            val process = runInTermux(command)
            process.inputStream.bufferedReader().use { reader ->
                val output = reader.readText().trim()
                process.waitFor()
                output
            }
        } catch (e: Exception) { "" }
    }

    /**
     * 运行命令并返回 (exitCode, stdout)
     */
    private fun runInTermuxSync(command: String): Pair<Int, String> {
        return try {
            val process = runInTermux(command)
            process.inputStream.bufferedReader().use { reader ->
                val output = reader.readText().trim()
                val exitCode = process.waitFor()
                Pair(exitCode, output)
            }
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
     * 安装二进制到可执行路径
     */
    suspend fun installBinaryToTermux(sourceFile: File, targetName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            // 先尝试安装到 Termux
            if (detectTermux()) {
                val sourcePath = sourceFile.absolutePath
                val cmd = "cat '$sourcePath' > '$TERMUX_BIN/$targetName' && chmod +x '$TERMUX_BIN/$targetName'"
                val result = runInTermuxSync(cmd)
                if (result.first == 0) {
                    Log.i(TAG, "已安装到 Termux: $targetName")
                    return@withContext true
                }
            }
            // 回退：安装到 APP 内部目录
            val appBin = getAppBinDir()
            val targetFile = File(appBin, targetName)
            sourceFile.inputStream().use { input ->
                targetFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            targetFile.setExecutable(true)
            Log.i(TAG, "已安装到 APP 内部: ${targetFile.absolutePath}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "安装二进制失败", e)
            false
        }
    }

    /**
     * 获取安装引导文本
     */
    /**
     * 自动安装 Ubuntu 环境（通过 proot-distro）
     */
    suspend fun installUbuntu(onProgress: ((String) -> Unit)? = null): Boolean = withContext(Dispatchers.IO) {
        if (!detectTermux()) {
            onProgress?.invoke("Termux 未安装，无法安装 Ubuntu")
            return@withContext false
        }

        try {
            onProgress?.invoke("更新 Termux 包列表...")
            executeCommand("pkg update -y 2>&1 | tail -5")
            
            onProgress?.invoke("安装 proot-distro...")
            executeCommand("pkg install proot-distro -y 2>&1 | tail -10")
            
            onProgress?.invoke("安装 Ubuntu 24.04...")
            val installResult = executeCommand("proot-distro install ubuntu 2>&1 | tail -20")
            val installLog = installResult.stdout
            onProgress?.invoke(installLog)
            
            val installed = File(UBUNTU_ROOT).exists()
            if (installed) {
                onProgress?.invoke("Ubuntu 安装成功!")
            } else {
                onProgress?.invoke("Ubuntu 安装可能未完成，请检查 Termux")
            }
            installed
        } catch (e: Exception) {
            onProgress?.invoke("安装异常: ${e.message}")
            Log.e(TAG, "安装 Ubuntu 失败", e)
            false
        }
    }

    fun getSetupGuide(): String {
        return """
╔══════════════════════════════════════╗
║      Codex Android 开发环境          ║
╚══════════════════════════════════════╝

Codex Android 现在采用自包含模式运行：
• 使用 Android 系统 shell 执行命令
• Codex 二进制运行在 APP 内部目录
• 零外部依赖

可选增强（推荐）：
安装 Termux + Ubuntu 可获得完整开发环境：
1. 从 F-Droid 安装 Termux
2. pkg upgrade -y && pkg install proot-distro
3. proot-distro install ubuntu
4. pkg install nodejs-lts python git

完成后你将获得：
• Ubuntu 24.04 LTS 环境
• Node.js / Python / Git
• 完整的 Linux 开发体验
        """.trimIndent()
    }
}
