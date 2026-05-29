package com.ai.assistance.operit.core.tools.system.shell

import android.content.Context
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.core.tools.system.AndroidPermissionLevel
import com.ai.assistance.operit.core.tools.system.ShellIdentity
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn

/** 基于标准Android权限的Shell命令执行器 实现STANDARD权限级别的命令执行 */
class StandardShellExecutor(private val context: Context) : ShellExecutor {
    companion object {
        private const val TAG = "StandardShellExecutor"
        private const val COMMAND_TIMEOUT = 30L // 秒
    }

    override fun getPermissionLevel(): AndroidPermissionLevel = AndroidPermissionLevel.STANDARD

    override fun isAvailable(): Boolean = true // 标准执行器始终可用

    override fun hasPermission(): ShellExecutor.PermissionStatus =
            ShellExecutor.PermissionStatus.granted() // 标准执行器不需要额外权限

    override fun initialize() {
        // 标准执行器不需要初始化
    }

    override fun requestPermission(onResult: (Boolean) -> Unit) {
        // 标准执行器不需要额外权限
        onResult(true)
    }

    override suspend fun executeCommand(
        command: String,
        identity: ShellIdentity
    ): ShellExecutor.CommandResult =
            withContext(Dispatchers.IO) {
                AppLogger.d(TAG, "Executing standard command: $command")

                try {
                    // 判断是否包含shell特殊字符
                    if (containsShellOperators(command)) {
                        return@withContext executeWithShell(command)
                    }

                    // 使用ProcessBuilder执行命令，避免Runtime.exec(String)的分词问题
                    val process = ProcessBuilder("sh", "-c", command)
                        .redirectErrorStream(false)
                        .start()

                    // 读取输出流和错误流的同时等待进程结束，防止缓冲区满死锁
                    val stdoutBuilder = StringBuilder()
                    val stderrBuilder = StringBuilder()
                    val stdoutThread = Thread {
                        BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                            var line: String?
                            while (reader.readLine().also { line = it } != null) {
                                stdoutBuilder.append(line).append('\n')
                            }
                        }
                    }
                    val stderrThread = Thread {
                        BufferedReader(InputStreamReader(process.errorStream)).use { reader ->
                            var line: String?
                            while (reader.readLine().also { line = it } != null) {
                                stderrBuilder.append(line).append('\n')
                            }
                        }
                    }
                    stdoutThread.start()
                    stderrThread.start()

                    // 设置超时
                    val completed = process.waitFor(COMMAND_TIMEOUT, TimeUnit.SECONDS)
                    stdoutThread.join(1000)
                    stderrThread.join(1000)
                    if (!completed) {
                        process.destroyForcibly()
                        return@withContext ShellExecutor.CommandResult(
                                false,
                                "",
                                "Command timed out after $COMMAND_TIMEOUT seconds",
                                -1
                        )
                    }

                    val stdout = stdoutBuilder.toString()
                    val stderr = stderrBuilder.toString()
                    val exitCode = process.exitValue()

                    return@withContext ShellExecutor.CommandResult(
                            exitCode == 0,
                            stdout,
                            stderr,
                            exitCode
                    )
                } catch (e: Exception) {
                    AppLogger.e(TAG, "Error executing standard command", e)
                    return@withContext ShellExecutor.CommandResult(
                            false,
                            "",
                            "Error: ${e.message}",
                            -1
                    )
                }
            }

    override suspend fun startProcess(command: String): ShellProcess = withContext(Dispatchers.IO) {
        StandardShellProcess(command)
            }

    /** 通过shell解释器执行包含特殊操作符的命令 */
    private suspend fun executeWithShell(command: String): ShellExecutor.CommandResult =
            withContext(Dispatchers.IO) {
                try {
                    // 使用ProcessBuilder执行shell命令
                    val process = ProcessBuilder("sh", "-c", command)
                        .redirectErrorStream(true)
                        .start()

                    // 读取输出流的同时等待进程结束，防止缓冲区满死锁
                    val outputBuilder = StringBuilder()
                    val outputThread = Thread {
                        BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                            var line: String?
                            while (reader.readLine().also { line = it } != null) {
                                outputBuilder.append(line).append('\n')
                            }
                        }
                    }
                    outputThread.start()

                    // 设置超时
                    val completed = process.waitFor(COMMAND_TIMEOUT, TimeUnit.SECONDS)
                    outputThread.join(1000)
                    if (!completed) {
                        process.destroyForcibly()
                        return@withContext ShellExecutor.CommandResult(
                                false,
                                "",
                                "Command timed out after $COMMAND_TIMEOUT seconds",
                                -1
                        )
                    }

                    // outputThread 已经读取全部输出到 outputBuilder
                    // redirectErrorStream(true) 已将 stderr 合并到 stdout
                    val stdout = outputBuilder.toString()
                    val stderr = ""

                    val exitCode = process.exitValue()

                    // 对于grep命令，即使没有匹配也认为成功
                    val success =
                            if (command.contains("grep")) {
                                exitCode == 0 || exitCode == 1
                            } else {
                                exitCode == 0
                            }

                    return@withContext ShellExecutor.CommandResult(
                            success,
                            stdout,
                            stderr,
                            exitCode
                    )
                } catch (e: Exception) {
                    AppLogger.e(TAG, "Error executing shell command", e)
                    return@withContext ShellExecutor.CommandResult(
                            false,
                            "",
                            "Error: ${e.message}",
                            -1
                    )
                }
            }

    /**
     * 检测命令是否包含需要shell解释的特殊操作符
     * @param command 要检查的命令
     * @return 是否包含shell操作符
     */
    private fun containsShellOperators(command: String): Boolean {
        // 预处理：标记引号内的内容，避免检测引号内的操作符
        var inSingleQuotes = false
        var inDoubleQuotes = false
        var escaped = false
        var i = 0

        while (i < command.length) {
            val c = command[i]

            // 处理转义字符
            if (c == '\\' && !escaped) {
                escaped = true
                i++
                continue
            }

            // 处理引号
            if (c == '\'' && !escaped && !inDoubleQuotes) {
                inSingleQuotes = !inSingleQuotes
            } else if (c == '"' && !escaped && !inSingleQuotes) {
                inDoubleQuotes = !inDoubleQuotes
            }
            // 只在不在引号内时检测操作符
            else if (!inSingleQuotes && !inDoubleQuotes && !escaped) {
                // 检测管道
                if (c == '|') {
                    return true
                }

                // 检测 && 和 & 操作符
                if (c == '&') {
                    return true
                }

                // 检测重定向
                if (c == '>' || c == '<') {
                    return true
                }

                // 检测分号
                if (c == ';') {
                    return true
                }
            }

            escaped = false
            i++
        }

        return false
    }
}

/**
 * 标准的 ShellProcess 实现，使用 Runtime.exec()
 */
private class StandardShellProcess(command: String) : ShellProcess {
    private val process: Process = ProcessBuilder("sh", "-c", command)
        .redirectErrorStream(false)
        .start()
    
    override val stdout: Flow<String> = callbackFlow {
        try {
            BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                var line = reader.readLine()
                while (line != null) {
                    trySend(line)
                    line = reader.readLine()
                }
            }
        } catch (e: Exception) {
            com.ai.assistance.operit.util.AppLogger.w("StandardShellProcess", "stdout流读取结束: ${e.message}")
        }
        awaitClose { }
    }.flowOn(Dispatchers.IO)

    override val stderr: Flow<String> = callbackFlow {
        try {
            BufferedReader(InputStreamReader(process.errorStream)).use { reader ->
                var line = reader.readLine()
                while (line != null) {
                    trySend(line)
                    line = reader.readLine()
                }
            }
        } catch (e: Exception) {
            com.ai.assistance.operit.util.AppLogger.w("StandardShellProcess", "stderr流读取结束: ${e.message}")
        }
        awaitClose { }
    }.flowOn(Dispatchers.IO)

    override val isAlive: Boolean
        get() = process.isAlive

    override fun destroy() {
        process.destroy()
    }

    override suspend fun waitFor(): Int = withContext(Dispatchers.IO) {
        process.waitFor()
    }
    
    companion object {
        /**
         * 检测命令是否包含需要shell解释的特殊操作符
         */
        private fun containsShellOperators(command: String): Boolean {
            // 预处理：标记引号内的内容，避免检测引号内的操作符
            var inSingleQuotes = false
            var inDoubleQuotes = false
            var escaped = false
            var i = 0

            while (i < command.length) {
                val c = command[i]

                // 处理转义字符
                if (c == '\\' && !escaped) {
                    escaped = true
                    i++
                    continue
                }

                // 处理引号
                if (c == '\'' && !escaped && !inDoubleQuotes) {
                    inSingleQuotes = !inSingleQuotes
                } else if (c == '"' && !escaped && !inSingleQuotes) {
                    inDoubleQuotes = !inDoubleQuotes
                }
                // 只在不在引号内时检测操作符
                else if (!inSingleQuotes && !inDoubleQuotes && !escaped) {
                    // 检测管道
                    if (c == '|') {
                        return true
                    }

                    // 检测 && 和 & 操作符
                    if (c == '&') {
                        return true
                    }

                    // 检测重定向
                    if (c == '>' || c == '<') {
                        return true
                    }

                    // 检测分号
                    if (c == ';') {
                        return true
                    }
                }

                escaped = false
                i++
            }

            return false
        }
    }
}
