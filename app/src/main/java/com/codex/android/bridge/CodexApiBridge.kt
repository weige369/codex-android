package com.codex.android.bridge

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import org.json.JSONArray
import org.json.JSONObject

/**
 * Codex API 桥接器。
 *
 * 直接连接 OpenAI 兼容 API，无需本地二进制。
 * 支持流式 SSE 响应，与 WebView 前端双向通信。
 */
class CodexApiBridge(private val context: Context) {

    companion object {
        private const val TAG = "CodexApiBridge"
        private const val PREFS_NAME = "codex_prefs"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_API_URL = "api_url"
        private const val KEY_API_MODEL = "api_model"
    }

    enum class ConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        ERROR,
        STREAMING
    }

    // 默认值
    private val DEFAULT_API_URL = "https://api.openai.com/v1"
    private val DEFAULT_MODEL = "gpt-4o"

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(0, java.util.concurrent.TimeUnit.MILLISECONDS)
        .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    // 消息历史
    private val messageHistory = mutableListOf<JSONObject>()

    // 回调 - 由 WebView 层设置
    var onStreamMessage: ((String) -> Unit)? = null
    var onError: ((String) -> Unit)? = null
    var onConnectionChange: ((ConnectionState) -> Unit)? = null

    // 当前 SSE 事件源
    private var currentEventSource: EventSource? = null

    /**
     * 读取 API 配置
     */
    fun getApiKey(): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_API_KEY, "") ?: ""
    }

    fun getApiUrl(): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_API_URL, DEFAULT_API_URL) ?: DEFAULT_API_URL
    }

    fun getApiModel(): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_API_MODEL, DEFAULT_MODEL) ?: DEFAULT_MODEL
    }

    fun setApiConfig(key: String, url: String, model: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().apply {
            putString(KEY_API_KEY, key)
            putString(KEY_API_URL, url)
            putString(KEY_API_MODEL, model)
            apply()
        }
    }

    fun isConfigured(): Boolean {
        val key = getApiKey()
        val url = getApiUrl()
        return key.isNotBlank() && url.isNotBlank()
    }

    /**
     * 发送 Prompt 到 API（流式 SSE）
     */
    fun sendPrompt(prompt: String) {
        val apiKey = getApiKey()
        if (apiKey.isBlank()) {
            onError?.invoke("请先配置 API Key")
            return
        }

        val apiUrl = getApiUrl().trimEnd('/')
        val model = getApiModel()

        // 构建消息
        messageHistory.add(JSONObject().apply {
            put("role", "user")
            put("content", prompt)
        })

        val requestBody = JSONObject().apply {
            put("model", model)
            put("stream", true)
            put("messages", JSONArray(messageHistory))
        }

        Log.i(TAG, "发送 API 请求: $model")

        val request = Request.Builder()
            .url("$apiUrl/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "text/event-stream")
            .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        _connectionState.value = ConnectionState.STREAMING
        onConnectionChange?.invoke(ConnectionState.STREAMING)

        val factory = EventSources.createFactory(client)
        currentEventSource = factory.newEventSource(request, object : EventSourceListener() {
            private val assistantContent = StringBuilder()

            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                if (data == "[DONE]") {
                    // 流结束
                    val fullResponse = assistantContent.toString()
                    messageHistory.add(JSONObject().apply {
                        put("role", "assistant")
                        put("content", fullResponse)
                    })
                    _connectionState.value = ConnectionState.CONNECTED
                    onConnectionChange?.invoke(ConnectionState.CONNECTED)
                    onStreamMessage?.invoke("__STREAM_END__")
                    return
                }

                try {
                    val json = JSONObject(data)
                    val choices = json.optJSONArray("choices")
                    if (choices != null && choices.length() > 0) {
                        val delta = choices.getJSONObject(0).optJSONObject("delta")
                        if (delta != null) {
                            val content = delta.optString("content", "")
                            if (content.isNotEmpty()) {
                                assistantContent.append(content)
                                onStreamMessage?.invoke(content)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "SSE 解析错误: ${e.message}")
                }
            }

            override fun onOpen(eventSource: EventSource, response: Response) {
                Log.i(TAG, "API 流连接已打开")
                _connectionState.value = ConnectionState.CONNECTED
                onConnectionChange?.invoke(ConnectionState.CONNECTED)
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                val errorMsg = when {
                    t != null -> "连接失败: ${t.message}"
                    response != null -> "HTTP ${response.code}: ${response.message}"
                    else -> "未知错误"
                }
                Log.e(TAG, errorMsg)
                _connectionState.value = ConnectionState.ERROR
                onConnectionChange?.invoke(ConnectionState.ERROR)
                onError?.invoke(errorMsg)
            }

            override fun onClosed(eventSource: EventSource) {
                Log.i(TAG, "API 流已关闭")
                _connectionState.value = ConnectionState.DISCONNECTED
                onConnectionChange?.invoke(ConnectionState.DISCONNECTED)
            }
        })
    }

    /**
     * 普通（非流式）请求 - 用于 ping/状态检查
     */
    suspend fun ping(): Boolean = withContext(Dispatchers.IO) {
        try {
            val apiUrl = getApiUrl().trimEnd('/')
            val request = Request.Builder()
                .url("$apiUrl/models")
                .addHeader("Authorization", "Bearer ${getApiKey()}")
                .build()
            val response = client.newCall(request).execute()
            response.isSuccessful
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 取消当前流
     */
    fun cancelStream() {
        currentEventSource?.cancel()
        currentEventSource = null
        _connectionState.value = ConnectionState.CONNECTED
        onConnectionChange?.invoke(ConnectionState.CONNECTED)
        onStreamMessage?.invoke("__STREAM_END__")
    }

    /**
     * 清除对话历史
     */
    fun clearHistory() {
        messageHistory.clear()
    }

    /**
     * 获取对话历史
     */
    fun getHistory(): String {
        return JSONArray(messageHistory).toString()
    }

    fun destroy() {
        cancelStream()
        scope.cancel()
        messageHistory.clear()
    }
}
