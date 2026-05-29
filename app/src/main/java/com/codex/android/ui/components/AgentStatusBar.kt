package com.codex.android.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codex.android.service.CodexRuntimeService
import com.codex.android.service.RuntimeState

/**
 * Agent 运行状态指示器。
 *
 * 参考 Replit 的 AgentStatusAppCard 设计，提供：
 * - 脉冲动画状态灯（断连/连接中/运行中/错误）
 * - 状态文本
 * - 进度/消息显示
 * - 点击展开详情
 */
@Composable
fun AgentStatusBar(
    state: RuntimeState,
    isConnected: Boolean,
    onToggle: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "agentPulse")

    val statusColor by animateColorAsState(
        targetValue = when (state) {
            RuntimeState.RUNNING -> Color(0xFF2ED573)
            RuntimeState.STARTING,
            RuntimeState.DOWNLOADING,
            RuntimeState.EXTRACTING -> Color(0xFFF1A502)
            RuntimeState.ERROR -> Color(0xFFFF4757)
            RuntimeState.STOPPED -> Color(0xFF8888AA)
        },
        label = "statusColor"
    )

    // 脉冲动画 - 仅连接/运行中状态时脉冲
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val shouldPulse = state == RuntimeState.RUNNING

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
        shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 状态灯
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .then(
                        if (shouldPulse) Modifier.scale(pulseScale) else Modifier
                    )
                    .background(statusColor)
            )
            Spacer(Modifier.width(8.dp))

            // 状态文本
            Text(
                text = statusText(state, isConnected),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(Modifier.weight(1f))

            // 子状态详情
            if (isConnected) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color(0xFF2ED573).copy(alpha = 0.15f)
                ) {
                    Text(
                        " 在线 ",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF2ED573)
                    )
                }
            } else {
                Text(
                    state.name.lowercase(),
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

private fun statusText(state: RuntimeState, connected: Boolean): String {
    return when (state) {
        RuntimeState.STOPPED -> "Codex 已停止"
        RuntimeState.DOWNLOADING -> "正在下载 Codex CLI..."
        RuntimeState.EXTRACTING -> "正在解压..."
        RuntimeState.STARTING -> "正在启动..."
        RuntimeState.RUNNING -> if (connected) "Codex 运行中" else "Codex 已启动"
        RuntimeState.ERROR -> "Codex 运行异常"
    }
}

/**
 * Agent 功能按钮 - 浮动在输入框旁的快捷操作区。
 */
@Composable
fun AgentActionButton(
    icon: @Composable () -> Unit,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.size(36.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Box(
                modifier = Modifier.size(36.dp),
                contentAlignment = Alignment.Center
            ) {
                icon()
            }
        }
    }
}

/**
 * 流式消息气泡 - 用于显示 Codex 正在生成的流式内容。
 */
@Composable
fun StreamBubble(
    content: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            // 打字光标
            Box(
                modifier = Modifier
                    .padding(top = 4.dp)
                    .size(8.dp, 14.dp)
                    .background(
                        MaterialTheme.colorScheme.primary,
                        RoundedCornerShape(1.dp)
                    )
            )
            Spacer(Modifier.width(8.dp))
            Text(
                content,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 20.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

private val EaseInOutCubic: Easing = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)
