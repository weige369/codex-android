package com.codex.android.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.util.Log
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
import com.codex.android.codex.CodexManager
import com.codex.android.service.CodexNotifications
import com.codex.android.service.CodexRuntimeService
import com.codex.android.service.RuntimeState
import com.codex.android.ui.about.AboutScreen
import com.codex.android.ui.files.FileBrowserScreen
import com.codex.android.ui.github.GitHubImportScreen
import com.codex.android.ui.mcp.CodexMCPScreen
import com.codex.android.ui.settings.CodexSettingsScreen
import com.codex.android.ui.skills.CodexSkillsScreen
import com.codex.android.ui.workspace.WorkspaceScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CodexActivity : ComponentActivity() {

    companion object {
        private const val TAG = "CodexActivity"
    }

    val codexManager by lazy { CodexManager(this) }

    // Runtime state (updated by broadcast receiver)
    private var _runtimeState = RuntimeState.STOPPED
    private var _wsPort = CodexRuntimeService.DEFAULT_WS_PORT
    private var _isRuntimeRunning = false
    private var _wsConnected = false
    private var _currentScreen: Screen = Screen.Workspace

    sealed class Screen {
        data object Workspace : Screen()
        data object Settings : Screen()
        data object Skills : Screen()
        data object MCP : Screen()
        data object GitHubImport : Screen()
        data object FileBrowser : Screen()
        data object About : Screen()
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) Log.i(TAG, "通知权限已授予")
    }

    // State update callback for Compose
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

            // Notify Compose
            stateListener?.invoke(_currentScreen, _runtimeState, _wsPort, _isRuntimeRunning, _wsConnected)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

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
                        var screen by remember { mutableStateOf<Screen>(_currentScreen) }
                        var runtimeState by remember { mutableStateOf<RuntimeState>(_runtimeState) }
                        var wsPort by remember { mutableIntStateOf(_wsPort) }
                        var isRunning by remember { mutableStateOf<Boolean>(_isRuntimeRunning) }
                        var wsConnected by remember { mutableStateOf<Boolean>(_wsConnected) }

                        // Listen for state changes from activity
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
                                    onOpenSettings = { navigateTo(Screen.Settings) },
                                    onOpenSkills = { navigateTo(Screen.Skills) },
                                    onOpenMCP = { navigateTo(Screen.MCP) },
                                    onOpenGitHub = { navigateTo(Screen.GitHubImport) },
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
    }
}
