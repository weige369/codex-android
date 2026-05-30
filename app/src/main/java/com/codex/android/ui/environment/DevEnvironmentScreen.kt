package com.codex.android.ui.environment

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codex.android.util.DevelopmentEnvironment
import kotlinx.coroutines.launch

/**
 * 开发环境管理界面。
 *
 * 管理：
 * - Termux 安装引导
 * - Ubuntu 环境（proot-distro）
 * - Node.js / Python / Git 等工具
 * - 环境状态检测与修复
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevEnvironmentScreen(
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val devEnv = remember { DevelopmentEnvironment(context) }

    var envInfo by remember { mutableStateOf<DevelopmentEnvironment.EnvInfo?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isInstalling by remember { mutableStateOf(false) }
    var installLog by remember { mutableStateOf("") }
    var installProgress by remember { mutableStateOf("") }
    var currentAction by remember { mutableStateOf<String?>(null) }

    // 初始环境检测
    LaunchedEffect(Unit) {
        isLoading = true
        envInfo = devEnv.getEnvironmentInfo()
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("开发环境", fontSize = 18.sp) },
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            // ===== 状态卡片 =====
            item {
                EnvironmentStatusCard(
                    envInfo = envInfo,
                    isLoading = isLoading
                )
            }

            // ===== Termux 安装引导 =====
            if (false) {
                item {
                    TermuxSetupCard(
                        onInstallTermux = {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, 
                                    Uri.parse("https://f-droid.org/packages/com.termux/"))
                                context.startActivity(intent)
                            } catch (_: Exception) {}
                        }
                    )
                }
            }

            // ===== 快速操作 =====
            if (envInfo?.state != DevelopmentEnvironment.EnvState.ERROR) {
                item {
                    SectionTitle("快速操作")
                }

                // 安装/刷新 Ubuntu
                item {
                    ActionCard(
                        icon = Icons.Default.Terminal,
                        title = if (envInfo?.hasUbuntu == true) "已安装 Ubuntu ${envInfo?.ubuntuVersion}" else "安装 Ubuntu 环境",
                        subtitle = "通过 proot-distro 安装 Ubuntu 24.04",
                        buttonText = if (envInfo?.hasUbuntu == true) "重新安装" else "安装",
                        buttonColor = if (envInfo?.hasUbuntu == true) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                        enabled = !isInstalling,
                        onAction = {
                            isInstalling = true
                            installLog = ""
                            currentAction = "ubuntu"
                            scope.launch {
                                // Ubuntu needs Termux - show guide
                                installLog = "\n请先安装 Termux 后执行:\n  pkg install proot-distro\n  proot-distro install ubuntu"
                                envInfo = devEnv.getEnvironmentInfo()
                                isInstalling = false
                                currentAction = null
                            }
                        }
                    )
                }

                // 安装开发工具
                item {
                    ActionCard(
                        icon = Icons.Default.Build,
                        title = "安装开发工具",
                        subtitle = "Node.js / Python / Git / Cmake / Rust",
                        buttonText = "安装",
                        enabled = !isInstalling,
                        onAction = {
                            isInstalling = true
                            installLog = ""
                            currentAction = "tools"
                            scope.launch {
                                // Dev tools need Termux - show guide
                                installLog = "\n请先安装 Termux 后执行:\n  pkg install nodejs-lts python git"
                                envInfo = devEnv.getEnvironmentInfo()
                                isInstalling = false
                                currentAction = null
                            }
                        }
                    )
                }

                // 刷新环境
                item {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                isLoading = true
                                envInfo = devEnv.getEnvironmentInfo()
                                isLoading = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isLoading
                    ) {
                        Icon(Icons.Default.Refresh, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("刷新环境检测")
                    }
                }
            }

            // ===== 安装日志 =====
            if (installLog.isNotBlank()) {
                item {
                    SectionTitle("安装日志")
                }
                item {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF0A0A0F)
                    ) {
                        LazyColumn(
                            modifier = Modifier.padding(8.dp)
                        ) {
                            item {
                                Text(
                                    installLog,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = Color(0xFF4AF626),
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }
                }
            }

            // ===== 已安装工具列表 =====
            if (envInfo != null && envInfo!!.state != DevelopmentEnvironment.EnvState.ERROR) {
                item {
                    SectionTitle("已安装环境")
                }

                item {
                    ToolStatusList(envInfo!!)
                }
            }

            // ===== 已安装工具详情 =====
            if (envInfo != null && envInfo!!.state != DevelopmentEnvironment.EnvState.ERROR) {
                item {
                    SectionTitle("工具版本")
                }
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            ToolVersionRow("Termux", envInfo?.termuxVersion ?: "-", envInfo?.state != DevelopmentEnvironment.EnvState.ERROR)
                            ToolVersionRow("Node.js", envInfo?.nodeVersion ?: "-", envInfo?.hasNodeJs == true)
                            ToolVersionRow("Python", envInfo?.pythonVersion ?: "-", envInfo?.hasPython == true)
                            ToolVersionRow("Git", envInfo?.gitVersion ?: "-", envInfo?.hasGit == true)
                            ToolVersionRow("Codex CLI", "已安装", envInfo?.hasCodex == true)
                        }
                    }
                }
            }

            // ===== 关于 =====
            item {
                Spacer(Modifier.height(16.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                ) {
                    Text(
                        "开发环境基于 Termux + proot-distro\nUbuntu 提供完整的 Linux 开发体验",
                        modifier = Modifier.padding(16.dp),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

// ===== 状态卡片 =====
@Composable
private fun EnvironmentStatusCard(
    envInfo: DevelopmentEnvironment.EnvInfo?,
    isLoading: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            if (isLoading) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("正在检测环境...", fontSize = 14.sp)
                }
            } else if (envInfo == null) {
                Text("环境检测失败", color = MaterialTheme.colorScheme.error)
            } else {
                // 状态图标和文字
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val (icon, color, text) = when (envInfo.state) {
                        DevelopmentEnvironment.EnvState.UBUNTU_READY -> 
                            Triple(Icons.Default.CheckCircle, Color(0xFF2ED573), "Ubuntu 已安装")
                        DevelopmentEnvironment.EnvState.TERMUX_READY -> 
                            Triple(Icons.Default.Info, Color(0xFFFFA502), "Termux 已安装")
                        DevelopmentEnvironment.EnvState.SELF_CONTAINED -> 
                            Triple(Icons.Default.CheckCircle, Color(0xFF2ED573), "自包含模式")
                        DevelopmentEnvironment.EnvState.ERROR -> 
                            Triple(Icons.Default.Error, Color(0xFFFF4757), "环境异常")
                    }
                    Icon(icon, null, tint = color, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        if (envInfo.errorMessage.isNotBlank()) {
                            Text(envInfo.errorMessage, fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}

// ===== Termux 安装引导 =====
@Composable
private fun TermuxSetupCard(onInstallTermux: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("需要安装 Termux", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                "Codex Android 需要 Termux 提供 Linux 运行环境。" +
                "请从 F-Droid 安装 Termux，然后返回此页面刷新。",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 20.sp
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = onInstallTermux, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.OpenInNew, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("打开 F-Droid Termux 页面")
            }
        }
    }
}

// ===== 操作卡片 =====
@Composable
private fun ActionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    buttonText: String,
    buttonColor: Color = MaterialTheme.colorScheme.primary,
    enabled: Boolean = true,
    onAction: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = onAction,
                enabled = enabled,
                colors = ButtonDefaults.buttonColors(containerColor = buttonColor),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
            ) {
                if (enabled) Text(buttonText, fontSize = 13.sp)
                else CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            }
        }
    }
}

// ===== 工具状态列表 =====
@Composable
private fun ToolStatusList(info: DevelopmentEnvironment.EnvInfo) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            ToolStatusRow("Termux", info.state != DevelopmentEnvironment.EnvState.ERROR)
            ToolStatusRow("Ubuntu", info.hasUbuntu)
            ToolStatusRow("Node.js", info.hasNodeJs)
            ToolStatusRow("Python", info.hasPython)
            ToolStatusRow("Git", info.hasGit)
            ToolStatusRow("Codex CLI", info.hasCodex)
        }
    }
}

@Composable
private fun ToolStatusRow(name: String, installed: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (installed) Icons.Default.CheckCircle else Icons.Default.Cancel,
            null,
            tint = if (installed) Color(0xFF2ED573) else Color(0xFF8888AA),
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text(name, fontSize = 14.sp)
    }
}

@Composable
private fun ToolVersionRow(name: String, version: String, installed: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(name, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            if (installed) version else "未安装",
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = if (installed) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        title,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp)
    )
}
