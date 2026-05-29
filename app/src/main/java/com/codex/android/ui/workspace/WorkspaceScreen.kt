package com.codex.android.ui.workspace

import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.codex.android.bridge.CodexBridge
import com.codex.android.codex.CodexManager
import com.codex.android.service.RuntimeState
import com.codex.android.ui.components.AgentStatusBar

/**
 * Codex 工作区主界面。
 * WebView（Codex UI）+ 底部状态栏。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceScreen(
    codexManager: CodexManager,
    runtimeState: RuntimeState,
    isWsConnected: Boolean,
    workspacePath: String,
    wsPort: Int,
    codexBridge: CodexBridge?,
    onOpenSettings: () -> Unit,
    onOpenSkills: () -> Unit,
    onOpenMCP: () -> Unit,
    onOpenGitHub: () -> Unit,
    onOpenDevEnv: () -> Unit = {},
    onOpenFileBrowser: (() -> Unit)? = null,
    onOpenAbout: (() -> Unit)? = null,
    onToggleRuntime: () -> Unit,
    onExportFile: ((String) -> Unit)? = null
) {
    val isRunning = runtimeState == RuntimeState.RUNNING

    Scaffold(
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 1.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Codex",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.weight(1f))
                    ToolbarAction(Icons.Default.Code, "GitHub", onOpenGitHub)
                    ToolbarAction(Icons.Default.Extension, "Skills", onOpenSkills)
                    ToolbarAction(Icons.Default.Memory, "MCP", onOpenMCP)
                    ToolbarAction(Icons.Default.Build, "环境", onOpenDevEnv)
                    ToolbarAction(Icons.Default.Settings, "设置", onOpenSettings)
                }
            }
        },
        bottomBar = {
            AgentStatusBar(
                state = runtimeState,
                isConnected = isWsConnected,
                onToggle = onToggleRuntime
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.allowFileAccess = true
                        settings.loadWithOverviewMode = true
                        settings.useWideViewPort = true
                        settings.builtInZoomControls = false
                        settings.mediaPlaybackRequiresUserGesture = false

                        addJavascriptInterface(
                            CodexWebViewBridge(codexBridge),
                            "CodexAndroidBridge"
                        )

                        webViewClient = object : android.webkit.WebViewClient() {
                            override fun onPageFinished(view: WebView, url: String) {
                                super.onPageFinished(view, url)
                                view.evaluateJavascript(
                                    "window.CODEX_WS_PORT = $wsPort;" +
                                    "window.WS_PORT = $wsPort;" +
                                    "window.onCodexWsPortUpdate && window.onCodexWsPortUpdate($wsPort);",
                                    null
                                )
                                if (isRunning) {
                                    view.evaluateJavascript(
                                        "window.connectWebSocket && window.connectWebSocket();",
                                        null
                                    )
                                }
                            }
                        }

                        loadUrl("file:///android_asset/web/codex-ui.html")
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // 底部启动按钮
            if (!isRunning && runtimeState != RuntimeState.STARTING && runtimeState != RuntimeState.DOWNLOADING) {
                Button(
                    onClick = onToggleRuntime,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(Icons.Default.PlayArrow, null)
                    Spacer(Modifier.width(6.dp))
                    Text("启动 Codex")
                }
            }
        }
    }
}

/**
 * WebView JavaScript 桥接接口。
 * 注册为 `window.CodexAndroidBridge`，供前端 JS 调用。
 */
class CodexWebViewBridge(
    private val codexBridge: CodexBridge?
) {
    companion object {
        private const val TAG = "CodexWebViewBridge"
    }

    @JavascriptInterface
    fun isCodexReady(): Boolean {
        return codexBridge?.connectionState?.value == CodexBridge.ConnectionState.CONNECTED
    }

    @JavascriptInterface
    fun postMessage(jsonMessage: String) {
        Log.d(TAG, "JS -> Bridge: $jsonMessage")
        codexBridge?.let { bridge ->
            try {
                val msg = org.json.JSONObject(jsonMessage)
                when (msg.optString("type")) {
                    "prompt" -> bridge.sendPrompt(msg.getString("data"))
                    "disconnect" -> bridge.disconnect()
                }
            } catch (e: Exception) {
                Log.e(TAG, "JS message parse error", e)
            }
        }
    }
}

/**
 * 将运行时状态转发到 WebView JS。
 */
fun forwardStatusToWebView(
    webView: WebView?,
    state: RuntimeState,
    wsPort: Int,
    isRunning: Boolean
) {
    webView?.post {
        webView.evaluateJavascript(
            "window.onCodexStatusUpdate && window.onCodexStatusUpdate('${state.name}', $wsPort, $isRunning);",
            null
        )
        if (state == RuntimeState.RUNNING) {
            webView.evaluateJavascript(
                "window.connectWebSocket && window.connectWebSocket();",
                null
            )
        }
    }
}

@Composable
private fun ToolbarAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick) {
        Icon(icon, label, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
