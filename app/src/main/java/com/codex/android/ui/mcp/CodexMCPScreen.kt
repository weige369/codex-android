package com.codex.android.ui.mcp

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codex.android.codex.AnyclawManager
import com.codex.android.provider.CodexMCPBridge
import kotlinx.coroutines.launch

/**
 * MCP 服务器管理界面 + Anyclaw 工具注册表。
 * 管理 MCP 服务器、浏览和安装工具包。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CodexMCPScreen(
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val mcpBridge = remember { CodexMCPBridge(context) }
    val anyclawManager = mcpBridge.anyclawManager
    val mcpState by mcpBridge.state.collectAsState()
    val mcpTools by mcpBridge.tools.collectAsState()
    val mcpLogs by mcpBridge.logs.collectAsState()
    val installedPackages by anyclawManager.installedPackages.collectAsState()
    val registryPackages by anyclawManager.registryPackages.collectAsState()
    val registryState by anyclawManager.state.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<AnyclawManager.AnyclawPackage>>(emptyList()) }
    var showInstallDialog by remember { mutableStateOf<String?>(null) }
    var showSearch by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (showSearch) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { q ->
                                searchQuery = q
                                scope.launch { searchResults = anyclawManager.search(q) }
                            },
                            placeholder = { Text("搜索工具包...", fontSize = 14.sp) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedBorderColor = Color.Transparent,
                                focusedBorderColor = Color.Transparent
                            )
                        )
                    } else {
                        Text("MCP 工具", fontSize = 18.sp)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = if (showSearch) { { showSearch = false; searchQuery = "" } } else onBack) {
                        Icon(if (showSearch) Icons.Default.Close else Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                actions = {
                    if (!showSearch) {
                        IconButton(onClick = { showSearch = true }) {
                            Icon(Icons.Default.Search, "搜索工具")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            if (mcpState == CodexMCPBridge.ServerState.RUNNING) {
                ExtendedFloatingActionButton(
                    onClick = { mcpBridge.stop() },
                    icon = { Icon(Icons.Default.Stop, "停止") },
                    text = { Text("停止") },
                    containerColor = MaterialTheme.colorScheme.error
                )
            } else {
                ExtendedFloatingActionButton(
                    onClick = { mcpBridge.start() },
                    icon = { Icon(Icons.Default.PlayArrow, "启动") },
                    text = { Text("启动") }
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // 标签切换
            PrimaryTabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 },
                    text = { Text("工具") })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 },
                    text = { Text("注册表") })
                Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 },
                    text = { Text("日志") })
            }

            when (selectedTab) {
                0 -> ToolsTab(mcpState, mcpTools, installedPackages)
                1 -> RegistryTab(
                    registryState, registryPackages, installedPackages,
                    anyclawManager, scope
                )
                2 -> LogsTab(mcpLogs)
            }
        }
    }
}

@Composable
private fun ToolsTab(
    mcpState: CodexMCPBridge.ServerState,
    mcpTools: List<CodexMCPBridge.MCPTool>,
    installedPackages: List<AnyclawManager.AnyclawPackage>
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        // MCP 状态卡片
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = when (mcpState) {
                        CodexMCPBridge.ServerState.RUNNING -> Color(0xFF1B5E20).copy(alpha = 0.1f)
                        CodexMCPBridge.ServerState.ERROR -> Color(0xFFB71C1C).copy(alpha = 0.1f)
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = when (mcpState) {
                            CodexMCPBridge.ServerState.RUNNING -> Icons.Default.CheckCircle
                            CodexMCPBridge.ServerState.ERROR -> Icons.Default.Error
                            CodexMCPBridge.ServerState.STARTING -> Icons.Default.HourglassTop
                            else -> Icons.Default.StopCircle
                        },
                        contentDescription = null,
                        tint = when (mcpState) {
                            CodexMCPBridge.ServerState.RUNNING -> Color(0xFF4CAF50)
                            CodexMCPBridge.ServerState.ERROR -> Color(0xFFF44336)
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Android 系统工具集", fontWeight = FontWeight.Bold)
                        Text(
                            text = when (mcpState) {
                                CodexMCPBridge.ServerState.RUNNING -> "运行中 (${mcpTools.size} 个工具)"
                                CodexMCPBridge.ServerState.STARTING -> "启动中..."
                                CodexMCPBridge.ServerState.ERROR -> "启动失败"
                                CodexMCPBridge.ServerState.STOPPED -> "已停止"
                            },
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // 已安装工具包
        item {
            Text("已安装工具包 (${installedPackages.size})", fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }

        items(installedPackages) { pkg ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (pkg.isBuiltin) Icons.Default.Build else Icons.Default.Extension,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(pkg.name, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                        Spacer(Modifier.weight(1f))
                        if (pkg.isBuiltin) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                            ) {
                                Text("内置", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(pkg.description, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Text("${pkg.tools.size} 个工具", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // 工具列表
        if (mcpTools.isNotEmpty()) {
            item {
                Text("可用 MCP 工具 (${mcpTools.size})", fontWeight = FontWeight.Bold, fontSize = 14.sp,
                    modifier = Modifier.padding(top = 8.dp))
            }
            items(mcpTools) { tool ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Build, contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (tool.serverId == "anyclaw") Color(0xFF7C4DFF) else MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(tool.name, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                            Text(tool.description, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RegistryTab(
    registryState: AnyclawManager.RegistryState,
    registryPackages: List<AnyclawManager.AnyclawPackage>,
    installedPackages: List<AnyclawManager.AnyclawPackage>,
    anyclawManager: AnyclawManager,
    scope: kotlinx.coroutines.CoroutineScope
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        // 刷新按钮和信息
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("工具注册表", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                TextButton(onClick = {
                    scope.launch { anyclawManager.refreshRegistry() }
                }) {
                    if (registryState == AnyclawManager.RegistryState.LOADING) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(4.dp))
                    }
                    Text("刷新")
                }
            }
        }

        // 内置包
        item {
            Text("内置工具", fontWeight = FontWeight.Medium, fontSize = 13.sp,
                color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp))
        }
        items(AnyclawManager.BUILTIN_PACKAGES) { pkg ->
            val isInstalled = installedPackages.any { it.id == pkg.id }
            PackageCard(
                pkg = pkg,
                isInstalled = isInstalled,
                onInstall = {
                    scope.launch {
                        anyclawManager.installPackage(pkg.id)
                    }
                },
                onUninstall = { if (!pkg.isBuiltin) anyclawManager.uninstallPackage(pkg.id) }
            )
        }

        // 注册表包
        if (registryPackages.isNotEmpty()) {
            item {
                Text("在线工具", fontWeight = FontWeight.Medium, fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
            }
            items(registryPackages) { pkg ->
                val isInstalled = installedPackages.any { it.id == pkg.id }
                PackageCard(
                    pkg = pkg,
                    isInstalled = isInstalled,
                    onInstall = {
                        scope.launch {
                            anyclawManager.installPackage(pkg.id)
                        }
                    },
                    onUninstall = { if (!pkg.isBuiltin) anyclawManager.uninstallPackage(pkg.id) }
                )
            }
        }

        // 添加说明
        item {
            Card(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.1f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("💡 Anyclaw 工具注册表", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Anyclaw 是通用的 AI 工具适配器，可将 API、CLI、脚本等转换为 MCP 工具。\n" +
                        "内置工具无需联网即可使用，在线工具需要网络连接。\n" +
                        "启动 MCP 服务器后，这些工具即可被 Codex 调用。",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 20.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun PackageCard(
    pkg: AnyclawManager.AnyclawPackage,
    isInstalled: Boolean,
    onInstall: () -> Unit,
    onUninstall: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(pkg.name, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                    Spacer(Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = when (pkg.type) {
                            "builtin" -> Color(0xFF4CAF50).copy(alpha = 0.1f)
                            "openapi" -> Color(0xFF2196F3).copy(alpha = 0.1f)
                            "cli" -> Color(0xFFFF9800).copy(alpha = 0.1f)
                            else -> Color(0xFF9C27B0).copy(alpha = 0.1f)
                        }
                    ) {
                        Text(
                            when (pkg.type) { "builtin" -> "内置" "openapi" -> "API" "cli" -> "CLI" else -> "注册表" },
                            fontSize = 10.sp,
                            color = when (pkg.type) {
                                "builtin" -> Color(0xFF4CAF50); "openapi" -> Color(0xFF2196F3)
                                "cli" -> Color(0xFFFF9800); else -> Color(0xFF9C27B0)
                            },
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                if (pkg.description.isNotBlank()) {
                    Text(pkg.description, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2)
                }
                if (pkg.tools.isNotEmpty()) {
                    Text("${pkg.tools.size} 个工具", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp))
                }
            }
            Spacer(Modifier.width(8.dp))
            if (isInstalled) {
                OutlinedButton(
                    onClick = onUninstall,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFF44336)),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text("已安装", fontSize = 12.sp)
                }
            } else {
                Button(
                    onClick = onInstall,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text("安装", fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun LogsTab(logs: String) {
    if (logs.isBlank()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("暂无日志", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 500.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E))
                ) {
                    Text(
                        logs,
                        fontSize = 11.sp,
                        color = Color(0xFF4AF626),
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        modifier = Modifier.padding(12.dp).fillMaxWidth()
                    )
                }
            }
        }
    }
}
