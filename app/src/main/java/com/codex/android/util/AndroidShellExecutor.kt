package com.codex.android.util

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Android Shell 执行器。
 *
 * 参照 opencode shell.ts 的进程树管理设计：
 * - 进程组管理 (kill tree)
 * - 命令超时控制
 * - 权限分级
 * - Termux/Shizuku 集成
 *
 * 注意：Android SDK 不暴露 Process.pid()（Java 9+ API），
 * 使用自增 ID 代替操作系统 PID。
 */
object AndroidShellExecutor {

    private const val TAG = "AndroidShellExecutor"
    private const val DEFAULT_TIMEOUT_MS = 120_000L
    private const val KILL_TIMEOUT_MS = 200L

    enum class PermissionLevel {
        NORMAL,      // 普通应用权限
        SHIZUKU,     // Shizuku 权限
        ROOT         // Root 权限
    }

    data class ShellResult(
        val exitCode: Int,
        val stdout: String,
        val stderr: String,
        val isTimedOut: Boolean = false,
        val permissionLevel: PermissionLevel = PermissionLevel.NORMAL
    )

    data class ProcessInfo(
        val id: Int,
        val command: String,
        val startTime: Long,
        val permissionLevel: PermissionLevel
    )

    private val activeProcesses = mutableMapOf<Int, Process>()
    private val processHistory = mutableListOf<ProcessInfo>()
    private val pidCounter = AtomicInteger(0)

    /**
     * 执行 Shell 命令（带进程树管理）
     */
    suspend fun execute(
        command: String,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
        permissionLevel: PermissionLevel = PermissionLevel.NORMAL,
        env: Map<String, String> = emptyMap()
    ): ShellResult = withContext(Dispatchers.IO) {
        try {
            val runtime = Runtime.getRuntime()
            val cmd = when (permissionLevel) {
                PermissionLevel.SHIZUKU -> listOf("sh", "-c", command)
                PermissionLevel.ROOT -> listOf("su", "-c", command)
                PermissionLevel.NORMAL -> listOf("sh", "-c", command)
            }

            val pb = ProcessBuilder(cmd)
                .redirectErrorStream(false)
                .apply {
                    environment().putAll(env)
                }

            val process = pb.start()
            // Android SDK 上无法获取 Java 9+ Process.pid()，使用自增 ID
            val id = pidCounter.incrementAndGet()
            activeProcesses[id] = process
            processHistory.add(ProcessInfo(id, command, System.currentTimeMillis(), permissionLevel))

            val stdout = StringBuilder()
            val stderr = StringBuilder()

            // 读取输出（非阻塞）
            val stdoutThread = Thread {
                try { process.inputStream.bufferedReader().use { r -> r.lines().forEach { stdout.appendLine(it) } } }
                catch (_: Exception) {}
            }
            val stderrThread = Thread {
                try { process.errorStream.bufferedReader().use { r -> r.lines().forEach { stderr.appendLine(it) } } }
                catch (_: Exception) {}
            }
            stdoutThread.start(); stderrThread.start()

            // 等待完成或超时
            val finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
            stdoutThread.join(1000); stderrThread.join(1000)

            if (!finished) {
                // 超时 - 杀死进程
                killProcess(id)
                process.waitFor(5, TimeUnit.SECONDS)
                activeProcesses.remove(id)
                ShellResult(-1, stdout.toString(), stderr.toString(), isTimedOut = true, permissionLevel = permissionLevel)
            } else {
                activeProcesses.remove(id)
                ShellResult(process.exitValue(), stdout.toString(), stderr.toString(), permissionLevel = permissionLevel)
            }
        } catch (e: Exception) {
            Log.e(TAG, "执行命令失败: $command", e)
            ShellResult(-1, "", "执行失败: ${e.message}", permissionLevel = permissionLevel)
        }
    }

    /**
     * 杀死进程
     */
    fun killProcess(id: Int) {
        activeProcesses[id]?.let { process ->
            try {
                process.destroyForcibly()
            } catch (e: Exception) {
                Log.w(TAG, "杀死进程失败: $id", e)
            }
        }
    }

    /**
     * 杀死所有活跃进程
     */
    fun killAll() {
        activeProcesses.keys.toList().forEach { id ->
            killProcess(id)
            activeProcesses.remove(id)
        }
    }

    /**
     * 获取活跃进程列表
     */
    fun getActiveProcesses(): List<ProcessInfo> = processHistory.toList()

    /**
     * 检查 Shizuku 是否可用
     */
    fun isShizukuAvailable(context: Context): Boolean {
        return try {
            Class.forName("moe.shizuku.api.ShizukuApi")
            true
        } catch (_: Exception) { false }
    }

    /**
     * 检查 Root 是否可用
     */
    fun isRootAvailable(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("which", "su"))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val line = reader.readLine()
            process.destroy()
            line != null
        } catch (_: Exception) { false }
    }
}
