package com.codex.android.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.codex.android.codex.CodexManager
import com.codex.android.util.AndroidShellExecutor
import com.codex.android.util.DevelopmentEnvironment
import com.codex.android.util.LinuxEnvironment
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.net.ServerSocket

enum class RuntimeState {
    STOPPED,
    DOWNLOADING,
    EXTRACTING,
    STARTING,
    RUNNING,
    ERROR
}

/**
 * 前台服务，管理 Codex CLI 运行时生命周期。
 *
 * 运行策略：
 * 1. Termux + Ubuntu 已安装 → 在 Ubuntu proot 中运行
 * 2. Termux 已安装但无 Ubuntu → 在 Termux 中运行
 * 3. Termux 未安装 → 尝试直接运行（大概率失败，提示安装）
 */
class CodexRuntimeService : Service() {

    companion object {
        private const val TAG = "CodexRuntimeService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "codex_runtime"
        private const val ACTION_START = "com.codex.android.action.START_CODEX"
        private const val ACTION_STOP = "com.codex.android.action.STOP_CODEX"
        private const val ACTION_STATUS = "com.codex.android.action.CODEX_STATUS"

        const val DEFAULT_WS_PORT = 9877
        const val DEFAULT_HTTP_PORT = 19327

        private val _state = MutableStateFlow(RuntimeState.STOPPED)
        val state: StateFlow<RuntimeState> = _state.asStateFlow()

        private val _logs = MutableStateFlow<List<String>>(emptyList())
        val logs: StateFlow<List<String>> = _logs.asStateFlow()

        private var _wsPort = DEFAULT_WS_PORT
        val wsPort: Int get() = _wsPort

        private var _runningMode: String = "unknown"
        val runningMode: String get() = _runningMode

        fun start(context: Context) {
            val intent = Intent(context, CodexRuntimeService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, CodexRuntimeService::class.java))
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val logsLock = Any()
    private lateinit var codexManager: CodexManager
    private lateinit var devEnv: DevelopmentEnvironment
    private var codexProcess: java.lang.Process? = null
    @Volatile
    private var isRunning = false

    override fun onCreate() {
        super.onCreate()
        codexManager = CodexManager(this)
        devEnv = DevelopmentEnvironment(this)
        AndroidShellExecutor.init(this)
        createNotificationChannel()
        addLog("CodexRuntimeService 已创建")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                startForeground(NOTIFICATION_ID, createNotification("Codex 启动中..."))
                serviceScope.launch { startCodex() }
            }
            ACTION_STOP -> {
                stopCodex()
                stopSelf()
            }
            ACTION_STATUS -> broadcastStatus()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopCodex()
        serviceScope.cancel()
        _state.value = RuntimeState.STOPPED
        addLog("CodexRuntimeService 已销毁")
        super.onDestroy()
    }

    private suspend fun startCodex() {
        if (isRunning) {
            addLog("Codex 已在运行中")
            return
        }

        try {
            // 检测运行环境
            val useTermux = devEnv.detectTermux()
            val envInfo = if (useTermux) devEnv.getEnvironmentInfo() else DevelopmentEnvironment.EnvInfo()

            // 优先使用自包含 Linux 环境（免 Termux proot + Ubuntu）
            val linuxEnv = LinuxEnvironment(this)
            val linuxInfo = linuxEnv.getInfo()
            val hasSelfContainedLinux = linuxInfo.state == LinuxEnvironment.EngineState.READY

            // 也检查 Termux Ubuntu 的可用性
            val hasUbuntu = if (useTermux) envInfo.hasUbuntu else false

            _runningMode = when {
                hasSelfContainedLinux -> "proot-linux"
                hasUbuntu -> "ubuntu"
                useTermux -> "termux"
                else -> "direct"
            }
            addLog("运行环境: $_runningMode (${
                when(_runningMode) {
                    "proot-linux" -> "自包含 proot Linux"
                    "ubuntu" -> "Termux Ubuntu proot"
                    "termux" -> "Termux"
                    else -> "Android 原生 (自包含)"
                }
            })")

            if (!useTermux && !hasSelfContainedLinux) {
                addLog("使用自包含模式运行（无需 Termux，但无法运行 Codex）")
            }
            if (hasSelfContainedLinux) {
                addLog("自包含 Linux 环境已就绪，将通过 proot 运行 Codex")
            }

            // 下载/验证二进制
            _state.value = RuntimeState.STARTING
            addLog("检查 Codex 二进制文件...")

            if (hasSelfContainedLinux && codexManager.isInstalled()) {
                // 自包含 Linux 模式：将 Codex 二进制复制到 rootfs 中运行
                addLog("自包含 Linux 模式启动...")
                _state.value = RuntimeState.STARTING
                startCodexInProot(linuxInfo)
                return
            }

            if (!codexManager.isInstalled()) {
                _state.value = RuntimeState.DOWNLOADING
                addLog("需要下载 Codex CLI...")

                val success = withContext(Dispatchers.IO) {
                    codexManager.downloadWithProgress { progress, total ->
                        val pct = if (total > 0) (progress * 100 / total) else 0
                        addLog("下载进度: $pct%")
                        updateNotification("下载 Codex... $pct%")
                    }
                }

                if (!success) {
                    _state.value = RuntimeState.ERROR
                    addLog("Codex 下载失败")
                    updateNotification("Codex 下载失败")
                    return
                }

                _state.value = RuntimeState.EXTRACTING
                addLog("正在解压 Codex CLI...")
                updateNotification("正在解压 Codex CLI...")

                if (!codexManager.extractBinary()) {
                    _state.value = RuntimeState.ERROR
                    addLog("Codex 解压失败")
                    updateNotification("Codex 解压失败")
                    return
                }

                addLog("Codex 二进制就绪: ${codexManager.codexBinary.length()} bytes")
            }

            if (!codexManager.verifyBinary()) {
                addLog("二进制验证失败，重新下载...")
                codexManager.cleanup()
                _state.value = RuntimeState.ERROR
                updateNotification("Codex 二进制损坏")
                return
            }

            if (!codexManager.getConfigFile().exists()) {
                codexManager.createDefaultConfig()
                addLog("已创建默认配置")
            }

            codexManager.workspaceDir.mkdirs()
            _wsPort = findFreePort(DEFAULT_WS_PORT)
            addLog("WebSocket 端口: $_wsPort")

            // 启动 Codex
            _state.value = RuntimeState.STARTING
            addLog("正在启动 Codex exec-server...")
            updateNotification("正在启动 Codex...")

            when (_runningMode) {
                "ubuntu" -> startInUbuntu(envInfo)
                "termux" -> if (!devEnv.detectTermux()) {
                    addLog("Termux 不可用，切换至自包含模式")
                    startDirect()
                } else {
                    startInTermux()
                }
                else -> startDirect()
            }

            // 等待确认
            delay(3000)

            if (codexProcess?.isAlive == true) {
                _state.value = RuntimeState.RUNNING
                addLog("Codex exec-server 已启动 ($_runningMode)")
                updateNotification("Codex 已就绪")
                broadcastStatus()
            } else {
                val exitCode = codexProcess?.exitValue() ?: -1
                _state.value = RuntimeState.ERROR
                addLog("Codex 进程异常退出 (exit=$exitCode)")
                addLog("提示: 请确保已安装 Termux 和必要的依赖")
                updateNotification("Codex 启动失败")
                isRunning = false
            }

        } catch (e: Exception) {
            _state.value = RuntimeState.ERROR
            addLog("Codex 启动异常: ${e.message}")
            Log.e(TAG, "启动 Codex 失败", e)
            updateNotification("Codex 错误: ${e.message}")
        }
    }

    /**
     * 在 Ubuntu proot 中启动 Codex
     */
    private suspend fun startInUbuntu(envInfo: DevelopmentEnvironment.EnvInfo) {
        val startCmd = buildString {
            append("proot-distro login ubuntu -- bash -c '")
            append("export HOME=/root && ")
            append("export CODEX_CONFIG_DIR='/data/data/com.termux/files/home/.codex' && ")
            append("cd /root && ")
            append("codex exec-server ")
            append("--port $_wsPort ")
            append("--http-port ${_wsPort + 1} ")
            append("--skip-git-repo-check ")
            append("2>&1'")
        }
        startCodexProcess(startCmd, "Ubuntu")
    }

    /**
     * 在 Termux 中启动 Codex
     */
    private suspend fun startInTermux() {
        val startCmd = buildString {
            append("cd ~ && ")
            append("export CODEX_CONFIG_DIR='${codexManager.getConfigDir().absolutePath}' && ")
            append("codex exec-server ")
            append("--port $_wsPort ")
            append("--http-port ${_wsPort + 1} ")
            append("--skip-git-repo-check ")
            append("2>&1")
        }
        startCodexProcess(startCmd, "Termux")
    }

    /**
     * 直接启动（Android 原生，失败率高）
     */
    private suspend fun startDirect() {
        addLog("尝试直接运行 Codex（无 Termux）...")
        // 先做可行性自检：能否直接执行该二进制
        val probe = codexManager.testDirectExecution()
        if (!probe.success) {
            addLog("直接运行不可用: ${probe.message}")
            addLog("Codex CLI 二进制编译目标为 Linux musl")
            if (probe.sdkInt >= 29) {
                addLog("Android ${probe.sdkInt} 禁止执行应用私有目录中的可执行文件（W^X）")
            }
            addLog("请在'环境'页面安装 Termux + Ubuntu 后重试")
            _state.value = RuntimeState.ERROR
            updateNotification("需要 Termux 环境")
            return
        }

        addLog("直接运行自检通过：${probe.message}")
        try {
            val process = ProcessBuilder(
                codexManager.codexBinary.absolutePath,
                "exec-server",
                "--port", _wsPort.toString(),
                "--http-port", (_wsPort + 1).toString(),
                "--skip-git-repo-check"
            ).apply {
                redirectErrorStream(true)
                environment()["CODEX_CONFIG_DIR"] = codexManager.getConfigDir().absolutePath
                environment()["HOME"] = codexManager.workspaceDir.absolutePath
                directory(codexManager.workspaceDir)
            }.start()
            codexProcess = process
            isRunning = true
            addLog("已直接启动 Codex exec-server（自包含模式）")

            serviceScope.launch {
                try {
                    process.inputStream.bufferedReader().use { reader ->
                        reader.lines().forEach { line ->
                            addLog("[Codex] $line")
                            if (line.contains("listening", ignoreCase = true) ||
                                line.contains("started", ignoreCase = true) ||
                                line.contains("ready", ignoreCase = true)) {
                                _state.value = RuntimeState.RUNNING
                                updateNotification("Codex 已就绪")
                                broadcastStatus()
                            }
                        }
                    }
                } catch (e: Exception) {
                    addLog("Codex 输出流已关闭: ${e.message}")
                }
            }
        } catch (e: Exception) {
            addLog("直接启动 exec-server 失败: ${e.message}")
            _state.value = RuntimeState.ERROR
            updateNotification("Codex 启动失败")
        }
    }

    private fun stopCodex() {
        addLog("正在停止 Codex...")
        isRunning = false
        try {
            codexProcess?.destroyForcibly()
            codexProcess?.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)
        } catch (e: Exception) { Log.w(TAG, "停止 Codex 进程时出错", e) }
        codexProcess = null
        _state.value = RuntimeState.STOPPED
        addLog("Codex 已停止")
    }


    /**
     * 通用的 Codex 进程启动方法
     */
    private suspend fun startCodexProcess(launchCmd: String, modeName: String) {
        addLog("安装 Codex 到 $modeName 环境...")
        val installOk = devEnv.installBinaryToTermux(codexManager.codexBinary, "codex")
        if (!installOk) {
            addLog("安装 Codex 到 Termux 失败，尝试直接启动...")
        }

        codexProcess = devEnv.runInTermux(launchCmd)
        isRunning = true

        serviceScope.launch {
            try {
                codexProcess?.inputStream?.bufferedReader()?.use { reader ->
                    reader.lines().forEach { line ->
                        addLog("[Codex] $line")
                        if (line.contains("listening", ignoreCase = true) ||
                            line.contains("started", ignoreCase = true) ||
                            line.contains("ready", ignoreCase = true)) {
                            _state.value = RuntimeState.RUNNING
                            updateNotification("Codex 已就绪")
                            broadcastStatus()
                        }
                    }
                }
            } catch (e: Exception) {
                addLog("Codex 输出流已关闭: ${e.message}")
            }
        }
    }
    /**
     * 在自包含 Linux（proot）环境中启动 Codex
     */
    private suspend fun startCodexInProot(linuxInfo: LinuxEnvironment.LinuxEnvInfo) {
        try {
            val linuxEnv = LinuxEnvironment(this)
            
            // 将 Codex 二进制复制到 rootfs 中
            addLog("安装 Codex 到 proot rootfs...")
            val rootfsBin = File(linuxInfo.rootfsPath, "/usr/local/bin")
            rootfsBin.mkdirs()
            codexManager.codexBinary.inputStream().use { input ->
                File(rootfsBin, "codex").outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            File(rootfsBin, "codex").setExecutable(true)

            // 构建启动命令
            addLog("通过 proot 启动 Codex...")
            val launchCmd = "codex --unix-daemon --http-port $_wsPort --ws-port $_wsPort"
            val cmd = linuxEnv.buildProotCommand(launchCmd)
            val prootEnv = linuxEnv.getProotEnv()

            codexProcess = ProcessBuilder(cmd)
                .apply {
                    environment().putAll(prootEnv)
                    redirectErrorStream(false)
                }
                .start()
            isRunning = true

            // 监控输出
            serviceScope.launch {
                try {
                    codexProcess?.inputStream?.bufferedReader()?.use { reader ->
                        reader.lines().forEach { line ->
                            addLog("[Codex-proot] $line")
                            if (line.contains("listening", ignoreCase = true) ||
                                line.contains("started", ignoreCase = true) ||
                                line.contains("ready", ignoreCase = true)) {
                                _state.value = RuntimeState.RUNNING
                                updateNotification("Codex 已就绪 (自包含 Linux)")
                                broadcastStatus()
                            }
                        }
                    }
                } catch (e: Exception) {
                    addLog("Codex proot 输出流已关闭: ${e.message}")
                }
            }
        } catch (e: Exception) {
            addLog("自包含 Linux 启动失败: ${e.message}")
            _state.value = RuntimeState.ERROR
            updateNotification("Codex 启动失败（自包含 Linux）")
        }
    }

    private fun broadcastStatus() {
        val intent = Intent("com.codex.android.CODEX_STATUS").apply {
            putExtra("state", _state.value.name)
            putExtra("wsPort", _wsPort)
            putExtra("isRunning", isRunning)
            putExtra("runningMode", _runningMode)
        }
        sendBroadcast(intent)
    }

    private fun findFreePort(startPort: Int): Int {
        var port = startPort
        while (port < startPort + 100) {
            try { ServerSocket(port).use { it.close(); return port } } catch (e: Exception) { port++ }
        }
        return startPort
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Codex 运行时", NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Codex AI 编码代理后台服务"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun createNotification(content: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            packageManager.getLaunchIntentForPackage(packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // 停止按钮
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, CodexRuntimeService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Codex AI")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_media_pause, "停止", stopIntent)
            .build()
    }

    private fun updateNotification(content: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, createNotification(content))
    }

    private fun addLog(message: String) {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        synchronized(logsLock) {
            val newLogs = _logs.value + "[$timestamp] $message"
            _logs.value = if (newLogs.size > 500) newLogs.takeLast(200) else newLogs
        }
        Log.d(TAG, message)
    }
}
