package com.codex.android.ui.workspace

import android.content.Context
import android.util.Log
import android.webkit.WebView
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.codex.android.bridge.CodexBridge
import com.codex.android.codex.CodexManager
import com.codex.android.service.CodexRuntimeService
import com.codex.android.service.RuntimeState
import com.codex.android.ui.components.AgentStatusBar
import kotlinx.coroutines.launch
import java.io.File

/**
 * Codex 工作区主界面。
 *
 * 三栏布局（参考 Replit WorkspaceScreen）：
 * ┌─────────────┬──────────────────────────┬──────────────┐
 * │  文件树/侧栏  │  WebView (Codex Chat UI)  │ 终端/输出    │
 * │  (可收起)    │  (主工作区)               │ (可收起)     │
 * └─────────────┴──────────────────────────┴──────────────┘
 *
 * 顶部状态栏显示 Agent 运行状态。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceScreen(
    codexManager: CodexManager,
    runtimeState: RuntimeState,
    isWsConnected: Boolean,
    workspacePath: String,
    wsPort: Int,
    onOpenSettings: () -> Unit,
    onOpenSkills: () -> Unit,
    onOpenMCP: () -> Unit,
    onOpenGitHub: () -> Unit,
    onOpenFileBrowser: (() -> Unit)? = null,
    onOpenAbout: (() -> Unit)? = null,
    onToggleRuntime: () -> Unit,
    onExportFile: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    var showSidebar by remember { mutableStateOf(true) }
    var showTerminal by remember { mutableStateOf(false) }
    var showDrawer by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            Column {
                // 顶部工具栏
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
                        // 侧栏切换
                        IconButton(onClick = { showSidebar = !showSidebar }) {
                            Icon(
                                if (showSidebar) Icons.Default.Menu else Icons.Default.MenuOpen,
                                "切换侧栏"
                            )
                        }

                        // Logo / 标题
                        Text(
                            "Codex",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Spacer(Modifier.weight(1f))

                        // 快捷按钮
                        ToolbarAction(Icons.Default.Code, "GitHub", onOpenGitHub)
                        ToolbarAction(Icons.Default.Extension, "Skills", onOpenSkills)
                        ToolbarAction(Icons.Default.Memory, "MCP", onOpenMCP)
                        ToolbarAction(Icons.Default.Settings, "设置", onOpenSettings)

                        Spacer(Modifier.width(4.dp))

                        // 更多菜单
                        IconButton(onClick = { showDrawer = !showDrawer }) {
                            Icon(Icons.Default.MoreVert, "更多")
                        }
                    }
                }
            }
        },
        bottomBar = {
            AgentStatusBar(
                state = runtimeState,
                isConnected = isWsConnected,
                onToggle = onToggleRuntime
            )
        },
        floatingActionButton = {
            if (showTerminal) {
                SmallFloatingActionButton(
                    onClick = { showTerminal = false },
                    containerColor = MaterialTheme.colorScheme.error
                ) {
                    Icon(Icons.Default.Close, "关闭终端")
                }
            }
        }
    ) { padding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // ===== 左侧栏：文件树/导航 =====
            AnimatedVisibility(
                visible = showSidebar,
                enter = slideInHorizontally() + fadeIn(),
                exit = slideOutHorizontally() + fadeOut()
            ) {
                SidebarPanel(
                    workspacePath = workspacePath,
                    onOpenGitHub = onOpenGitHub,
                    onOpenSettings = onOpenSettings,
                    onOpenSkills = onOpenSkills,
                    onOpenMCP = onOpenMCP,
                    onOpenFileBrowser = onOpenFileBrowser,
                    onOpenAbout = onOpenAbout,
                    wsConnected = isWsConnected,
                    onExport = { onExportFile?.invoke(workspacePath) }
                )
            }

            // ===== 主区域：WebView Codex Chat =====
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                CodexWebViewPanel(
                    wsPort = wsPort,
                    isRunning = runtimeState == RuntimeState.RUNNING
                )
            }

            // ===== 右侧栏：终端/输出 =====
            AnimatedVisibility(
                visible = showTerminal,
                enter = slideInHorizontally() + fadeIn(),
                exit = slideOutHorizontally() + fadeOut()
            ) {
                TerminalPanel(
                    onClose = { showTerminal = false }
                )
            }
        }
    }

    // 更多菜单抽屉
    if (showDrawer) {
        AlertDialog(
            onDismissRequest = { showDrawer = false },
            title = { Text("Codex 工作区") },
            text = {
                Column {
                    DrawerItem(Icons.Default.Terminal, "切换终端", onClick = {
                        showTerminal = !showTerminal
                        showDrawer = false
                    })
                    DrawerItem(Icons.Default.FolderOpen, "工作区目录", onClick = {
                        showDrawer = false
                    })
                    if (!showSidebar) {
                        DrawerItem(Icons.Default.MenuOpen, "显示侧栏", onClick = {
                            showSidebar = true
                            showDrawer = false
                        })
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showDrawer = false }) { Text("关闭") } }
        )
    }
}

// ========== 组件 ==========

@Composable
private fun ToolbarAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(icon, label, modifier = Modifier.size(20.dp))
    }
}

// ===== 左侧栏 =====
@Composable
private fun SidebarPanel(
    workspacePath: String,
    onOpenGitHub: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSkills: () -> Unit,
    onOpenMCP: () -> Unit,
    onOpenFileBrowser: (() -> Unit)? = null,
    onOpenAbout: (() -> Unit)? = null,
    wsConnected: Boolean,
    onExport: () -> Unit = {}
) {
    Surface(
        modifier = Modifier
            .width(220.dp)
            .fillMaxHeight(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        tonalElevation = 2.dp
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 标题
            Text(
                "导航",
                modifier = Modifier.padding(12.dp),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // 导航项
            SidebarItem(Icons.Default.Chat, "Codex 对话", true, null)
            SidebarItem(Icons.Default.Code, "GitHub 导入", false, onOpenGitHub)
            SidebarItem(Icons.Default.Folder, "文件浏览器", false, onOpenFileBrowser)
            SidebarItem(Icons.Default.Info, "关于", false, onOpenAbout)
            SidebarItem(Icons.Default.Extension, "Skills", false, onOpenSkills)
            SidebarItem(Icons.Default.Memory, "MCP 工具", false, onOpenMCP)

            Divider(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))

            Text(
                "工作区",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // 工作区路径（可点击）
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
            ) {
                Text(
                    workspacePath.split("/").lastOrNull() ?: "workspace",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "点击展开位置",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
            Spacer(Modifier.height(4.dp))
            // 目录快速入口
            SidebarItem(Icons.Default.Folder, "导出到 Downloads", false, onExport)

            Spacer(Modifier.weight(1f))

            // 底部状态
            Surface(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                shape = RoundedCornerShape(8.dp),
                color = if (wsConnected) Color(0xFF2ED573).copy(alpha = 0.1f)
                    else Color(0xFF8888AA).copy(alpha = 0.1f)
            ) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(if (wsConnected) Color(0xFF2ED573) else Color(0xFF8888AA))
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (wsConnected) "WebSocket 已连接" else "未连接",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SidebarItem(
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    onClick: (() -> Unit)?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            null,
            modifier = Modifier.size(18.dp),
            tint = if (isActive) MaterialTheme.colorScheme.primary
                   else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(10.dp))
        Text(
            label,
            fontSize = 13.sp,
            fontWeight = if (isActive) FontWeight.Medium else FontWeight.Normal,
            color = if (isActive) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface
        )
    }
}

// ===== 主 WebView 面板 =====
@Composable
private fun CodexWebViewPanel(
    wsPort: Int,
    isRunning: Boolean
) {
    val context = LocalContext.current
    var webView by remember { mutableStateOf<WebView?>(null) }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // 未运行时的提示
        if (!isRunning) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Default.Terminal,
                    null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "Codex 运行时未启动",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "启动后将自动加载 Codex 对话界面",
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    fontSize = 12.sp
                )
            }
        }

        // WebView (始终存在，通过 JS 隐藏/显示)
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = true
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    settings.builtInZoomControls = false

                    webViewClient = object : android.webkit.WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String) {
                            super.onPageFinished(view, url)
                            // Inject WS port to JS
                            view.evaluateJavascript(
                                "window.CODEX_WS_PORT = $wsPort; window.onCodexWsPortUpdate && window.onCodexWsPortUpdate($wsPort);",
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
                    webView = this
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // 停止/开始按钮（浮动）
        if (!isRunning) {
            Button(
                onClick = { /* handled by parent */ },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
            ) {
                Icon(Icons.Default.PlayArrow, null)
                Spacer(Modifier.width(6.dp))
                Text("启动 Codex")
            }
        }
    }
}

// ===== 终端面板 =====
@Composable
private fun TerminalPanel(onClose: () -> Unit) {
    Surface(
        modifier = Modifier
            .width(280.dp)
            .fillMaxHeight(),
        color = Color(0xFF0A0A0F),
        tonalElevation = 4.dp
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 终端顶栏
            Surface(
                color = Color(0xFF1A1A2E),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Terminal,
                        null,
                        tint = Color(0xFF4AF626),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "终端",
                        fontSize = 12.sp,
                        color = Color(0xFF4AF626),
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onClose, modifier = Modifier.size(24.dp)) {
                        Icon(
                            Icons.Default.Close,
                            null,
                            tint = Color(0xFF8888AA),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            // 终端内容占位 (后续集成 Operit 终端组件)
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "终端集成开发中...\n\n通过 Shizuku/Termux 运行 Shell",
                    color = Color(0xFF8888AA),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

// ===== 抽屉菜单项 =====
@Composable
private fun DrawerItem(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(12.dp))
        Text(label, fontSize = 14.sp)
    }
}
