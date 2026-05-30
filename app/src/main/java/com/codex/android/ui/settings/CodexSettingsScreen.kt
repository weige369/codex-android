package com.codex.android.ui.settings

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import com.codex.android.codex.CodexManager
import com.codex.android.service.CodexRuntimeService

/**
 * Codex 设置界面。
 * 完全使用 Codex Material 3 风格，集成在 Codex 设置菜单中。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CodexSettingsScreen(
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val codexManager = remember { CodexManager(context) }

    // 连接方式
    var connMode by remember { mutableStateOf(
        context.getSharedPreferences("codex_prefs", Context.MODE_PRIVATE)
            .getString("conn_mode", "local")
    ) }
    var apiKey by remember { mutableStateOf(
        context.getSharedPreferences("codex_prefs", Context.MODE_PRIVATE)
            .getString("api_key", "") ?: ""
    ) }
    var apiUrl by remember { mutableStateOf(
        context.getSharedPreferences("codex_prefs", Context.MODE_PRIVATE)
            .getString("api_url", "https://api.openai.com/v1") ?: "https://api.openai.com/v1"
    ) }
    var apiModel by remember { mutableStateOf(
        context.getSharedPreferences("codex_prefs", Context.MODE_PRIVATE)
            .getString("api_model", "gpt-4o") ?: "gpt-4o"
    ) }
    var showApiKey by remember { mutableStateOf(false) }

    // 二进制状态
    val isInstalled = codexManager.isInstalled()
    val binarySize = if (isInstalled) {
        "%.1f MB".format(codexManager.codexBinary.length() / (1024.0 * 1024.0))
    } else "未安装"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Codex 设置", fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            // ===== 连接方式 =====
            item {
                SectionHeader("连接方式")
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        ConnModeOption(
                            title = "本地 Codex CLI (推荐)",
                            subtitle = "下载并运行原生 Codex CLI 二进制，完整 Agent 功能",
                            icon = Icons.Default.Terminal,
                            selected = connMode == "local",
                            onClick = { connMode = "local"; saveConnMode(context, "local") }
                        )
                        Spacer(Modifier.height(4.dp))
                        ConnModeOption(
                            title = "OpenAI 兼容 API",
                            subtitle = "通过 API Key 连接，无需下载二进制",
                            icon = Icons.Default.Cloud,
                            selected = connMode == "api",
                            onClick = { connMode = "api"; saveConnMode(context, "api") }
                        )
                        Spacer(Modifier.height(4.dp))
                        ConnModeOption(
                            title = "自定义 WebSocket",
                            subtitle = "手动连接 Codex exec-server",
                            icon = Icons.Default.Link,
                            selected = connMode == "ws",
                            onClick = { connMode = "ws"; saveConnMode(context, "ws") }
                        )
                    }
                }
            }

            // ===== API 配置 (api 模式下显示) =====
            if (connMode == "api") {
                item {
                    SectionHeader("API 配置")
                }
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            OutlinedTextField(
                                value = apiKey,
                                onValueChange = {
                                    apiKey = it
                                    context.getSharedPreferences("codex_prefs", Context.MODE_PRIVATE)
                                        .edit().putString("api_key", it).apply()
                                },
                                label = { Text("API Key") },
                                placeholder = { Text("sk-...") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                visualTransformation = if (showApiKey) VisualTransformation.None
                                    else PasswordVisualTransformation(),
                                trailingIcon = {
                                    IconButton(onClick = { showApiKey = !showApiKey }) {
                                        Icon(
                                            if (showApiKey) Icons.Default.VisibilityOff
                                            else Icons.Default.Visibility,
                                            contentDescription = null
                                        )
                                    }
                                },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = apiUrl,
                                onValueChange = {
                                    apiUrl = it
                                    context.getSharedPreferences("codex_prefs", Context.MODE_PRIVATE)
                                        .edit().putString("api_url", it).apply()
                                },
                                label = { Text("API 地址") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = apiModel,
                                onValueChange = {
                                    apiModel = it
                                    context.getSharedPreferences("codex_prefs", Context.MODE_PRIVATE)
                                        .edit().putString("api_model", it).apply()
                                },
                                label = { Text("模型") },
                                placeholder = { Text("gpt-4o") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                        }
                    }
                }
            }

            // ===== 二进制状态 (local 模式下显示) =====
            if (connMode == "local") {
                item {
                    SectionHeader("Codex 运行时")
                }
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            SettingsRow("状态", if (isInstalled) "✅ 已安装" else "❌ 未安装")
                            if (isInstalled) {
                                SettingsRow("版本", CodexManager.CODEX_VERSION)
                                SettingsRow("大小", binarySize)
                            }
                            Spacer(Modifier.height(8.dp))
                            val connMode = context.getSharedPreferences("codex_prefs", Context.MODE_PRIVATE)
                                .getString("conn_mode", "local")
                            if (connMode == "local") {
                                if (isInstalled) {
                                    Button(
                                        onClick = { CodexRuntimeService.start(context) },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary
                                        )
                                    ) { Text("启动 Codex 运行时") }
                                } else {
                                    Button(
                                        onClick = { CodexRuntimeService.start(context) },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary
                                        )
                                    ) { Text("下载并启动 Codex CLI") }
                                }
                            } else {
                                Button(
                                    onClick = {
                                        Toast.makeText(context, "API 模式：请在设置中配置 API Key 和 URL", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.secondary
                                    )
                                ) { Text("🔗 API 模式已启用") }
                            }
                        }
                    }
                }
            }

            // ===== MCP & Skills =====
            item {
                SectionHeader("MCP & Skills")
            }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "MCP 和 Skills 管理功能开发中...",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            // ===== 关于 =====
            item {
                SectionHeader("关于")
            }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        SettingsRow("Codex CLI 版本", CodexManager.CODEX_VERSION)
                        SettingsRow("Agent 引擎", "Codex CLI (OpenAI)")
                        SettingsRow("集成方式", "WebSocket JSON-RPC")
                    }
                }
            }

            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp, start = 4.dp)
    )
}

@Composable
private fun ConnModeOption(
    title: String,
    subtitle: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.primary
                   else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                fontSize = 14.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface
            )
            Text(
                subtitle,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        RadioButton(
            selected = selected,
            onClick = onClick
        )
    }
}

@Composable
private fun SettingsRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

private fun saveConnMode(context: Context, mode: String) {
    context.getSharedPreferences("codex_prefs", Context.MODE_PRIVATE)
        .edit().putString("conn_mode", mode).apply()
}
