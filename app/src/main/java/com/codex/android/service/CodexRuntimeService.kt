package com.codex.android.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.Process
import android.util.Log
import androidx.core.app.NotificationCompat
import com.codex.android.codex.CodexManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.net.ServerSocket

/**
 * 前台服务，管理 Codex CLI 运行时的完整生命周期。
 *
 * 职责：
 * - 下载/验证 Codex 二进制文件
 * - 以 exec-server 模式启动 Codex
 * - 监控进程健康状态
 * - 提供 WebSocket 端点
 */
class CodexRuntimeService : Service() {

    companion object {
        private const val TAG = "CodexRuntimeService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "codex_runtime"
        private const val ACTION_START = "com.codex.android.action.START_CODEX"
        private const val ACTION_STOP = "com.codex.android.action.STOP_CODEX"
        private const val ACTION_STATUS = "com.codex.android.action.CODEX_STATUS"

        /** Codex exec-server 默认 WebSocket 端口 */
        const val DEFAULT_WS_PORT = 9877

        /** Codex HTTP 服务器默认端口(Codex Web UI) */
        const val DEFAULT_HTTP_PORT = 19327

        // 运行时状态
        enum class RuntimeState {
            STOPPED,        // 已停止
            DOWNLOADING,    // 下载中
            EXTRACTING,     // 解压中
            STARTING,       // 启动中
            RUNNING,        // 运行中
            ERROR           // 错误
        }

        private val _state = MutableStateFlow(RuntimeState.STOPPED)
        val state: StateFlow<RuntimeState> = _state.asStateFlow()

        private val _logs = MutableStateFlow<List<String>>(emptyList())
        val logs: StateFlow<List<String>> = _logs.asStateFlow()

        private var _wsPort = DEFAULT_WS_PORT
        val wsPort: Int get() = _wsPort

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
    private var codexProcess: java.lang.Process? = null
    @Volatile
    private var isRunning = false

    override fun onCreate() {
        super.onCreate()
        codexManager = CodexManager(this)
        createNotificationChannel()
        addLog("CodexRuntimeService 已创建")
        Log.i(TAG, "服务已创建")
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
            ACTION_STATUS -> {
                // 广播状态 - 用于 Activity 查询
                broadcastStatus()
            }
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

    /**
     * 启动 Codex 运行时的完整流程
     */
    private suspend fun startCodex() {
        if (isRunning) {
            addLog("Codex 已在运行中")
            return
        }

        try {
            // Step 1: 确保二进制文件就绪
            _state.value = RuntimeState.STARTING
            addLog("检查 Codex 二进制文件...")

            if (!codexManager.isInstalled()) {
                _state.value = RuntimeState.DOWNLOADING
                addLog("需要下载 Codex CLI...")

                // 使用进度回调下载
                val success = withContext(Dispatchers.IO) {
                    codexManager.downloadWithProgress { progress, total ->
                        val pct = if (total > 0) (progress * 100 / total) else 0
                        addLog("下载进度: $pct% ($progress/$total bytes)")
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
                addLog("解压 Codex 二进制文件...")
                updateNotification("正在准备 Codex...")

                if (!codexManager.extractBinary()) {
                    _state.value = RuntimeState.ERROR
                    addLog("Codex 解压失败")
                    updateNotification("Codex 解压失败")
                    return
                }
            }

            // Step 2: 验证二进制
            addLog("验证 Codex 二进制...")
            if (!codexManager.verifyBinary()) {
                // 若验证失败，尝试重新下载
                addLog("二进制验证失败，重新下载...")
                codexManager.cleanup()
                _state.value = RuntimeState.DOWNLOADING
                startCodex() // 递归重试
                return
            }

            // Step 3: 查找空闲端口
            _wsPort = findFreePort(DEFAULT_WS_PORT)
            val configDir = codexManager.getConfigDir()

            // Step 4: 配置 Codex
            codexManager.createDefaultConfig()

            // Step 5: 启动 Codex exec-server
            _state.value = RuntimeState.STARTING
            addLog("启动 Codex exec-server (端口: $_wsPort)...")
            updateNotification("Codex 启动中...")

            val cmd = buildList {
                add(codexManager.codexBinary.absolutePath)
                add("exec-server")
                add("--listen")
                add("ws://127.0.0.1:$_wsPort")
                add("-c")
                add("approval=never")
                add("-c")
                add("sandbox=off")
                add("-c")
                add("skip-git-repo-check=true")
                add("-c")
                add("model=gpt-4o")
            }

            addLog("执行命令: ${cmd.joinToString(" ")}")

            val pb = ProcessBuilder(cmd)
                .directory(codexManager.workspaceDir)
                .redirectErrorStream(true)
            pb.environment().apply {
                put("HOME", codexManager.codexDir.absolutePath)
                put("XDG_CONFIG_HOME", codexManager.getConfigDir().absolutePath)
                put("CODEX_CONFIG_DIR", codexManager.getConfigDir().absolutePath)
            }

            codexProcess = pb.start()
            isRunning = true

            // Step 6: 监控进程输出
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

            // Step 7: 等待一小段时间确认启动成功
            delay(2000)

            // 检查进程是否存活
            if (codexProcess?.isAlive == true) {
                _state.value = RuntimeState.RUNNING
                addLog("Codex exec-server 已启动")
                updateNotification("Codex 已就绪")
                broadcastStatus()
            } else {
                _state.value = RuntimeState.ERROR
                addLog("Codex 进程异常退出")
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
     * 停止 Codex 进程
     */
    private fun stopCodex() {
        addLog("正在停止 Codex...")
        isRunning = false

        try {
            codexProcess?.destroyForcibly()
            codexProcess?.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)
        } catch (e: Exception) {
            Log.w(TAG, "停止 Codex 进程时出错", e)
        }

        codexProcess = null
        _state.value = RuntimeState.STOPPED
        addLog("Codex 已停止")
    }

    /**
     * 广播当前状态给 Activity
     */
    private fun broadcastStatus() {
        val intent = Intent("com.codex.android.CODEX_STATUS").apply {
            putExtra("state", _state.value.name)
            putExtra("wsPort", _wsPort)
            putExtra("isRunning", isRunning)
        }
        sendBroadcast(intent)
    }

    private fun findFreePort(startPort: Int): Int {
        var port = startPort
        while (port < startPort + 100) {
            try {
                ServerSocket(port).use { it.close(); return port }
            } catch (e: Exception) {
                port++
            }
        }
        return startPort
    }

    // ========== 通知管理 ==========

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Codex 运行时",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Codex AI 编码代理后台服务"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(content: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            packageManager.getLaunchIntentForPackage(packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Codex AI")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(content: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, createNotification(content))
    }

    private fun addLog(message: String) {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date())
        synchronized(logsLock) { _logs.value = _logs.value + "[$timestamp] $message" }
        Log.d(TAG, message)
    }
}
