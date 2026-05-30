package com.codex.android.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.codex.android.bridge.CodexBridge
import com.codex.android.codex.CodexManager
import com.codex.android.service.CodexNotifications
import com.codex.android.service.CodexRuntimeService
import com.codex.android.service.RuntimeState
import com.codex.android.ui.about.AboutScreen
import com.codex.android.ui.environment.DevEnvironmentScreen
import com.codex.android.ui.diagnostics.DiagnosticsScreen
import com.codex.android.ui.files.FileBrowserScreen
import com.codex.android.ui.github.GitHubImportScreen
import com.codex.android.ui.mcp.CodexMCPScreen
import com.codex.android.ui.settings.CodexSettingsScreen
import com.codex.android.ui.skills.CodexSkillsScreen
import com.codex.android.ui.workspace.WorkspaceScreen
import com.codex.android.ui.workspace.forwardStatusToWebView
import com.codex.android.util.AndroidShellExecutor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CodexActivity : ComponentActivity() {

    companion object {
        private const val TAG = "CodexActivity"
    }

    val codexManager by lazy { CodexManager(this) }
    val codexBridge by lazy { CodexBridge("ws://127.0.0.1:${CodexRuntimeService.DEFAULT_WS_PORT}") }

    private var _runtimeState = RuntimeState.STOPPED
    private var _wsPort = CodexRuntimeService.DEFAULT_WS_PORT
    private var _isRuntimeRunning = false
    private var _wsConnected = false
    private var _currentScreen: Screen = Screen.Workspace

    // WebView 引用，用于状态转发
    private var _webView: WebView? = null

    sealed class Screen {
        data object Workspace : Screen()
        data object Settings : Screen()
        data object Skills : Screen()
        data object MCP : Screen()
        data object GitHubImport : Screen()
        data object FileBrowser : Screen()
        data object DevEnvironment : Screen()
        data object Diagnostic : Screen()
        data object About : Screen()
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) Log.i(TAG, "通知权限已授予")
    }

    private var stateListener: ((Screen, RuntimeState, Int, Boolean, Boolean) -> Unit)? = null

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val state = intent.getStringExtra("state") ?: return
            val port = intent.getIntExtra("wsPort", CodexRuntimeService.DEFAULT_WS_PORT)
            val running = intent.getBooleanExtra("isRunning", false)

            _wsPort = port
            _isRuntimeRunning = running
            _runtimeState = try {
                RuntimeState.valueOf(state)
            } catch (e: Exception) {
                RuntimeState.ERROR
            }
            Log.i(TAG, "Codex 状态: $state (端口: $port, 运行: $running)")

            // 转发状态到 WebView
            forwardStatusToWebView(_webView, _runtimeState, _wsPort, _isRuntimeRunning)

            // 如果运行时已启动，连接桥接器
            if (_runtimeState == RuntimeState.RUNNING) {
                if (codexBridge.connectionState.value != CodexBridge.ConnectionState.CONNECTED) {
                    codexBridge.connect()
                }
            }

            stateListener?.invoke(_currentScreen, _runtimeState, _wsPort, _isRuntimeRunning, _wsConnected)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 初始化 Shell 执行器和开发环境
        AndroidShellExecutor.init(this)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        registerReceiver(
            statusReceiver,
            IntentFilter("com.codex.android.CODEX_STATUS"),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) RECEIVER_NOT_EXPORTED else 0
        )

        CodexNotifications.createChannels(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        CodexRuntimeService.start(this)

        setContentView(ComposeView(this).apply {
            setContent {
                val colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
                MaterialTheme(colorScheme = colorScheme) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        var screen by remember { mutableStateOf<Screen>(Screen.Workspace) }
                        var runtimeState by remember { mutableStateOf(_runtimeState) }
                        var wsPort by remember { mutableIntStateOf(_wsPort) }
                        var isRunning by remember { mutableStateOf(_isRuntimeRunning) }
                        var wsConnected by remember { mutableStateOf(false) }

                        stateListener = { newScreen, newState, port, running, connected ->
                            screen = newScreen
                            runtimeState = newState
                            wsPort = port
                            isRunning = running
                            wsConnected = connected
                        }

                        when (screen) {
                            Screen.Workspace -> {
                                WorkspaceScreen(
                                    codexManager = codexManager,
                                    runtimeState = runtimeState,
                                    isWsConnected = wsConnected,
                                    workspacePath = codexManager.workspaceDir.absolutePath,
                                    wsPort = wsPort,
                                    codexBridge = codexBridge,
                                    onOpenSettings = { navigateTo(Screen.Settings) },
                                    onOpenSkills = { navigateTo(Screen.Skills) },
                                    onOpenMCP = { navigateTo(Screen.MCP) },
                                    onOpenGitHub = { navigateTo(Screen.GitHubImport) },
                                    onOpenDevEnv = { navigateTo(Screen.DevEnvironment) },
                                    onOpenDiagnostic = { navigateTo(Screen.Diagnostic) },
                                    onToggleRuntime = {
                                        if (isRunning) {
                                            CodexRuntimeService.stop(this@CodexActivity)
                                            wsConnected = false
                                        } else {
                                            CodexRuntimeService.start(this@CodexActivity)
                                        }
                                    },
                                    onExportFile = { path ->
                                        exportWorkspaceToDownloads(path)
                                    },
                                    onOpenFileBrowser = { navigateTo(Screen.FileBrowser) },
                                    onOpenAbout = { navigateTo(Screen.About) }
                                )
                            }
                            Screen.Settings -> CodexSettingsScreen(onBack = { navigateTo(Screen.Workspace) })
                            Screen.Skills -> CodexSkillsScreen(onBack = { navigateTo(Screen.Workspace) })
                            Screen.MCP -> CodexMCPScreen(onBack = { navigateTo(Screen.Workspace) })
                            Screen.GitHubImport -> GitHubImportScreen(
                                workspaceDir = codexManager.workspaceDir.absolutePath,
                                onBack = { navigateTo(Screen.Workspace) }
                            )
                            Screen.FileBrowser -> FileBrowserScreen(
                                workspacePath = codexManager.workspaceDir.absolutePath,
                                onBack = { navigateTo(Screen.Workspace) }
                            )
                            Screen.DevEnvironment -> DevEnvironmentScreen(
                                onBack = { navigateTo(Screen.Workspace) }
                            )
                            Screen.Diagnostic -> DiagnosticsScreen(
                                onBack = { navigateTo(Screen.Workspace) }
                            )
                            Screen.About -> AboutScreen(onBack = { navigateTo(Screen.Workspace) })
                        }
                    }
                }
            }
        })
    }

    private fun navigateTo(screen: Screen) {
        _currentScreen = screen
        stateListener?.invoke(screen, _runtimeState, _wsPort, _isRuntimeRunning, _wsConnected)
    }

    fun setWebViewRef(webView: WebView?) {
        _webView = webView
    }

    private fun exportWorkspaceToDownloads(workspacePath: String) {
        try {
            val sourceDir = java.io.File(workspacePath)
            if (!sourceDir.exists()) {
                android.widget.Toast.makeText(this, "工作区为空，无文件可导出", android.widget.Toast.LENGTH_SHORT).show()
                return
            }
            val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(
                android.os.Environment.DIRECTORY_DOWNLOADS
            )
            val exportDir = java.io.File(downloadsDir, "CodexWorkspace")
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    sourceDir.copyRecursively(exportDir, overwrite = true)
                    CoroutineScope(Dispatchers.Main).launch {
                        android.widget.Toast.makeText(
                            this@CodexActivity,
                            "已导出到: ${exportDir.absolutePath}",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                        Log.i(TAG, "工作区已导出到: ${exportDir.absolutePath}")
                    }
                } catch (e: Exception) {
                    CoroutineScope(Dispatchers.Main).launch {
                        android.widget.Toast.makeText(this@CodexActivity, "导出失败: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "导出工作区失败", e)
            android.widget.Toast.makeText(this, "导出失败: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(statusReceiver) } catch (_: Exception) {}
        codexBridge.destroy()
    }
}
