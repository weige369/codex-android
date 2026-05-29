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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codex.android.provider.CodexMCPBridge

/**
 * MCP 服务器管理界面。
 * 管理 Codex MCP 服务器的启停、工具列表查看。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CodexMCPScreen(
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val mcpBridge = remember { CodexMCPBridge(context) }
    val mcpState by mcpBridge.state.collectAsState()
    val mcpTools by mcpBridge.tools.collectAsState()
    val mcpLogs by mcpBridge.logs.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("MCP 服务器", fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            // 状态卡片
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

            // 工具列表标题
            if (mcpTools.isNotEmpty()) {
                item {
                    Text(
                        "可用工具 (${mcpTools.size})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                items(mcpTools) { tool ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Build,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    tool.name,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 14.sp,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                tool.description,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // 运行日志
            if (mcpLogs.isNotBlank()) {
                item {
                    Text("运行日志", fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
                }
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFF1A1A2E)
                        )
                    ) {
                        Text(
                            mcpLogs,
                            fontSize = 11.sp,
                            color = Color(0xFF4AF626),
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            modifier = Modifier.padding(12.dp).fillMaxWidth()
                        )
                    }
                }
            }

            // 注册指引
            if (mcpState == CodexMCPBridge.ServerState.RUNNING) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.1f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("💡 使用指引", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "MCP 服务器已启动，可供 Codex Agent 调用 Android 系统工具。\n" +
                                "在 Codex 设置中启用 \"集成 MCP 服务器\" 即可自动注册。",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 20.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
