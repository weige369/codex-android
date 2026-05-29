package com.codex.android.provider

import android.content.Context
import android.util.Log
import com.codex.android.bridge.CodexBridge
import com.codex.android.service.CodexRuntimeService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Codex Agent Provider.
 *
 * 连接 Codex CLI exec-server 的 WebSocket，提供 Agent 执行能力。
 * 支持流式和非流式 Prompt 执行，以及会话管理。
 */
class CodexAgentProvider(
    private val wsPort: Int = CodexRuntimeService.DEFAULT_WS_PORT
) {
    companion object {
        private const val TAG = "CodexAgentProvider"
    }

    enum class ProviderState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        ERROR
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var bridge: CodexBridge? = null
    private val isConnected = AtomicBoolean(false)

    private val _state = MutableStateFlow(ProviderState.DISCONNECTED)
    val state: StateFlow<ProviderState> = _state.asStateFlow()

    private val _currentRequestId = MutableStateFlow<String?>(null)
    val currentRequestId: StateFlow<String?> = _currentRequestId.asStateFlow()

    var onStreamMessage: ((String) -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    /**
     * 连接到 Codex exec-server
     */
    fun connect() {
        if (isConnected.get()) return
        _state.value = ProviderState.CONNECTING

        bridge = CodexBridge("ws://127.0.0.1:$wsPort").also { b ->
            b.onConnectionChange = { state ->
                when (state) {
                    CodexBridge.ConnectionState.CONNECTED -> {
                        isConnected.set(true)
                        _state.value = ProviderState.CONNECTED
                    }
                    CodexBridge.ConnectionState.ERROR -> {
                        _state.value = ProviderState.ERROR
                    }
                    CodexBridge.ConnectionState.DISCONNECTED -> {
                        isConnected.set(false)
                        _state.value = ProviderState.DISCONNECTED
                    }
                    else -> {}
                }
            }
            b.connect()
        }
    }

    /**
     * 断开连接
     */
    fun disconnect() {
        bridge?.destroy()
        bridge = null
        isConnected.set(false)
        _state.value = ProviderState.DISCONNECTED
    }

    /**
     * 发送 Prompt 并等待完整响应
     */
    suspend fun execute(prompt: String, stream: Boolean = true): String {
        val b = bridge ?: throw IllegalStateException("未连接到 Codex exec-server")
        return b.executePrompt(prompt, stream)
    }

    /**
     * 流式发送 Prompt（通过 onStreamMessage 回调接收）
     */
    fun sendPrompt(prompt: String) {
        val b = bridge ?: run {
            onError?.invoke("未连接到 Codex exec-server")
            return
        }
        b.onMessage = { msg -> onStreamMessage?.invoke(msg) }
        b.sendPrompt(prompt)
    }

    /**
     * 检查状态
     */
    suspend fun ping(): Boolean {
        return try {
            val b = bridge ?: return false
            val result = b.getStatus()
            !result.contains("error")
        } catch (e: Exception) {
            false
        }
    }



    fun destroy() {
        disconnect()
        scope.cancel()
    }
}
