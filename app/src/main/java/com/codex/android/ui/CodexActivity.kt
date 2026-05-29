package com.codex.android.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.codex.android.bridge.CodexBridge
import com.codex.android.service.CodexRuntimeService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Codex 主界面 Activity。
 *
 * 加载完整的 Codex Web UI 并通过 WebSocket 连接到 Codex CLI exec-server。
 * 支持全屏沉浸式体验，适配 Android 全面屏。
 */
class CodexActivity : ComponentActivity() {

    companion object {
        private const val TAG = "CodexActivity"
        private const val JS_BRIDGE_NAME = "CodexAndroidBridge"
    }

    private lateinit var webView: WebView
    private var codexBridge: CodexBridge? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // 运行时代码
    private var codexWsPort: Int = CodexRuntimeService.DEFAULT_WS_PORT
    private var isCodexRunning = false

    // 广播接收器 - 监听 CodexRuntimeService 状态
    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val state = intent.getStringExtra("state")
            val wsPort = intent.getIntExtra("wsPort", CodexRuntimeService.DEFAULT_WS_PORT)
            val running = intent.getBooleanExtra("isRunning", false)

            codexWsPort = wsPort
            isCodexRunning = running

            Log.i(TAG, "Codex 状态更新: $state (端口: $wsPort)")

            if (running && state == "RUNNING") {
                connectToCodex()
            }

            // 通知 WebView
            webView.post {
                webView.evaluateJavascript(
                    """window.onCodexStatusUpdate && window.onCodexStatusUpdate(${toJsString(state)}, $wsPort, $running)""",
                    null
                )
            }
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            Log.i(TAG, "通知权限已授予")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 全屏沉浸式设置
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        // 初始化 WebView
        webView = WebView(this)
        setContentView(webView)

        setupWebView()

        // 注册状态广播接收器
        registerReceiver(statusReceiver, IntentFilter("com.codex.android.CODEX_STATUS"),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) RECEIVER_NOT_EXPORTED else 0
        )

        // 请求通知权限 (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        // 启动 Codex 运行时服务
        CodexRuntimeService.start(this)

        // 初始化桥接
        initBridge()
    }

    override fun onResume() {
        super.onResume()
        // 重新隐藏系统栏
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(statusReceiver)
        } catch (e: Exception) {
            // 忽略未注册的情况
        }
        codexBridge?.destroy()
        webView.destroy()
    }

    /**
     * 设置 WebView 配置
     */
    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            loadWithOverviewMode = true
            useWideViewPort = true
            cacheMode = WebSettings.LOAD_NO_CACHE
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            mediaPlaybackRequiresUserGesture = false
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false

            // 启用现代 Web 功能
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                @Suppress("DEPRECATION")
                textZoom = 100
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(message: android.webkit.ConsoleMessage): Boolean {
                Log.d(TAG, "JS: [${message.messageLevel()}] ${message.message()}")
                return true
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url.toString()
                if (url.startsWith("ws://") || url.startsWith("wss://")) {
                    return true // WebSocket handled by JS
                }
                return false
            }

            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                Log.i(TAG, "页面加载完成: $url")

                // 注入桥梁状态
                view.evaluateJavascript(
                    """window.CODEX_WS_PORT = $codexWsPort""", null
                )
            }
        }

        // 加载 Web UI
        webView.loadUrl("file:///android_asset/web/codex-ui.html")
    }

    /**
     * 初始化 Codex 桥接
     */
    private fun initBridge() {
        codexBridge = CodexBridge("ws://127.0.0.1:$codexWsPort")

        // 设置 JavaScript 接口 (使用命名类以确保 WebView 兼容性)
        windowAndroidBridge = CodexWebBridge(this)
        webView.addJavascriptInterface(windowAndroidBridge!!, JS_BRIDGE_NAME)
    }

    /**
     * 连接到 Codex exec-server
     */
    private fun connectToCodex() {
        codexBridge?.let { bridge ->
            bridge.connect()
            bridge.onConnectionChange = { state ->
                Log.i(TAG, "桥接状态: $state")
                webView.post {
                    webView.evaluateJavascript(
                        """window.onCodexBridgeState && window.onCodexBridgeState('${state.name}')""",
                        null
                    )
                }
            }
            bridge.onMessage = { message ->
                webView.post {
                    webView.evaluateJavascript(
                        """window.onCodexMessage && window.onCodexMessage(${toJsString(message)})""",
                        null
                    )
                }
            }
        }
    }

    private fun showSettings() {
        Toast.makeText(this, "设置功能开发中...", Toast.LENGTH_SHORT).show()
    }

    fun getStoredApiKey(keyType: String): String {
        // 从 SharedPreferences 读取 API Key
        val prefs = getSharedPreferences("codex_prefs", Context.MODE_PRIVATE)
        return prefs.getString("api_key_$keyType", "") ?: ""
    }

    private var windowAndroidBridge: CodexWebBridge? = null

    // ===== JavaScript 桥接委托方法 =====

    fun postCodexMessage(jsonMessage: String) {
        codexBridge?.postMessage(jsonMessage)
    }

    fun isCodexReady(): Boolean = isCodexRunning

    fun getCodexWsPort(): Int = codexWsPort

    fun showCodexSettings() {
        showSettings()
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    /**
     * 将字符串转为 JS 安全字符串
     */
    private fun toJsString(str: String?): String {
        if (str == null) return "null"
        return "'" + str.replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\u2028", "\\u2028")
            .replace("\u2029", "\\u2029") + "'"
    }
}



/**
 * Codex WebView JavaScript 桥接类。
 * 使用命名类以确保 WebView 的 addJavascriptInterface 正确工作。
 */
class CodexWebBridge(private val activity: CodexActivity) {

    @JavascriptInterface
    fun postMessage(jsonMessage: String) {
        activity.postCodexMessage(jsonMessage)
    }

    @JavascriptInterface
    fun isCodexReady(): Boolean = activity.isCodexReady()

    @JavascriptInterface
    fun getWsPort(): Int = activity.getCodexWsPort()

    @JavascriptInterface
    fun getApiKey(keyType: String): String = activity.getStoredApiKey(keyType)

    @JavascriptInterface
    fun openSettings() {
        activity.showCodexSettings()
    }

    @JavascriptInterface
    fun shareText(text: String) {
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_TEXT, text)
        }
        activity.startActivity(android.content.Intent.createChooser(intent, "分享"))
    }

    @JavascriptInterface
    fun copyToClipboard(text: String) {
        val clipboard = activity.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Codex", text))
        android.widget.Toast.makeText(activity, "已复制", android.widget.Toast.LENGTH_SHORT).show()
    }
}
