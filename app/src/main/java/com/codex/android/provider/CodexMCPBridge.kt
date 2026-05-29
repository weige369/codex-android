package com.codex.android.provider

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Codex MCP 桥接器。
 *
 * 将 Android 系统功能暴露为 Codex MCP (Model Context Protocol) 工具，
 * 供 Codex Agent 调用。支持:
 * - 文件系统操作
 * - Shizuku 特权命令
 * - 系统信息查询
 * - 截图辅助
 * - 剪贴板操作
 * - 网络状态
 *
 * 作为本地 stdio MCP 服务器运行，由 Codex exec-server 管理。
 */
class CodexMCPBridge(private val context: Context) {

    companion object {
        private const val TAG = "CodexMCPBridge"
        const val SERVER_NAME = "android-system-tools"
        const val SERVER_DESCRIPTION = "Android 系统工具集 - 文件、Shell、系统信息"
        const val SERVER_VERSION = "1.0.0"
    }

    /** MCP 服务器运行状态 */
    enum class ServerState {
        STOPPED,
        STARTING,
        RUNNING,
        ERROR
    }

    private val _state = MutableStateFlow(ServerState.STOPPED)
    val state: StateFlow<ServerState> = _state.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var serverJob: Job? = null

    private val _logs = MutableStateFlow("")
    val logs: StateFlow<String> = _logs.asStateFlow()

    // 已注册的工具列表
    private val _tools = MutableStateFlow<List<MCPTool>>(emptyList())
    val tools: StateFlow<List<MCPTool>> = _tools.asStateFlow()

    data class MCPTool(
        val name: String,
        val description: String,
        val inputSchema: JSONObject
    )

    /**
     * 启动 MCP 服务器
     */
    fun start() {
        if (_state.value == ServerState.RUNNING) return
        _state.value = ServerState.STARTING
        addLog("正在启动 MCP 服务器...")

        serverJob = scope.launch {
            try {
                // 注册 Android 工具
                registerTools()
                _state.value = ServerState.RUNNING
                addLog("✅ MCP 服务器已启动 (${_tools.value.size} 个工具)")
            } catch (e: Exception) {
                _state.value = ServerState.ERROR
                addLog("❌ MCP 服务器启动失败: ${e.message}")
                Log.e(TAG, "MCP server start failed", e)
            }
        }
    }

    /**
     * 停止 MCP 服务器
     */
    fun stop() {
        serverJob?.cancel()
        serverJob = null
        _state.value = ServerState.STOPPED
        _tools.value = emptyList()
        addLog("MCP 服务器已停止")
    }

    /**
     * 执行 MCP 工具调用
     */
    suspend fun executeTool(name: String, arguments: JSONObject): JSONObject {
        addLog("🔧 执行工具: $name")

        return when (name) {
            "android_read_file" -> executeReadFile(arguments)
            "android_write_file" -> executeWriteFile(arguments)
            "android_list_files" -> executeListFiles(arguments)
            "android_delete_file" -> executeDeleteFile(arguments)
            "android_file_info" -> executeFileInfo(arguments)
            "android_shell_exec" -> executeShellExec(arguments)
            "android_system_info" -> executeSystemInfo(arguments)
            "android_clipboard" -> executeClipboard(arguments)
            "android_network_info" -> executeNetworkInfo(arguments)
            "android_package_info" -> executePackageInfo(arguments)
            else -> JSONObject().apply {
                put("isError", true)
                put("content", "未知工具: $name")
            }
        }
    }

    /**
     * 获取 MCP 服务器配置 JSON
     * 用于注册到 Codex exec-server
     */
    fun getServerConfig(): JSONObject {
        return JSONObject().apply {
            put("name", SERVER_NAME)
            put("description", SERVER_DESCRIPTION)
            put("version", SERVER_VERSION)
            put("type", "stdio")
            put("command", "internal") // 内置服务，非外部进程
            put("tools", JSONArray().apply {
                _tools.value.forEach { tool ->
                    put(JSONObject().apply {
                        put("name", tool.name)
                        put("description", tool.description)
                        put("inputSchema", tool.inputSchema)
                    })
                }
            })
        }
    }

    // ========== 工具注册 ==========

    private fun registerTools() {
        val toolList = mutableListOf<MCPTool>()

        // 文件读取
        toolList.add(MCPTool(
            name = "android_read_file",
            description = "读取 Android 文件系统上的文件内容（需权限）",
            inputSchema = JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("path", JSONObject().apply {
                        put("type", "string")
                        put("description", "文件完整路径")
                    })
                    put("encoding", JSONObject().apply {
                        put("type", "string")
                        put("description", "编码方式: utf-8 / base64")
                        put("default", "utf-8")
                    })
                })
                put("required", JSONArray().apply { put("path") })
            }
        ))

        // 文件写入
        toolList.add(MCPTool(
            name = "android_write_file",
            description = "写入内容到 Android 文件系统（需权限）",
            inputSchema = JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("path", JSONObject().apply {
                        put("type", "string")
                        put("description", "文件完整路径")
                    })
                    put("content", JSONObject().apply {
                        put("type", "string")
                        put("description", "文件内容")
                    })
                    put("append", JSONObject().apply {
                        put("type", "boolean")
                        put("description", "是否追加到文件末尾")
                        put("default", false)
                    })
                })
                put("required", JSONArray().apply { put("path"); put("content") })
            }
        ))

        // 文件列表
        toolList.add(MCPTool(
            name = "android_list_files",
            description = "列出目录中的文件和子目录",
            inputSchema = JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("path", JSONObject().apply {
                        put("type", "string")
                        put("description", "目录路径")
                    })
                    put("recursive", JSONObject().apply {
                        put("type", "boolean")
                        put("description", "是否递归列出所有子目录")
                        put("default", false)
                    })
                })
                put("required", JSONArray().apply { put("path") })
            }
        ))

        // 文件删除
        toolList.add(MCPTool(
            name = "android_delete_file",
            description = "删除文件或空目录",
            inputSchema = JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("path", JSONObject().apply {
                        put("type", "string")
                        put("description", "要删除的文件/目录路径")
                    })
                    put("recursive", JSONObject().apply {
                        put("type", "boolean")
                        put("description", "是否递归删除（用于目录）")
                        put("default", false)
                    })
                })
                put("required", JSONArray().apply { put("path") })
            }
        ))

        // 文件信息
        toolList.add(MCPTool(
            name = "android_file_info",
            description = "获取文件或目录的详细信息（大小、修改时间、权限等）",
            inputSchema = JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("path", JSONObject().apply {
                        put("type", "string")
                        put("description", "文件/目录路径")
                    })
                })
                put("required", JSONArray().apply { put("path") })
            }
        ))

        // Shell 命令执行
        toolList.add(MCPTool(
            name = "android_shell_exec",
            description = "执行 Android Shell 命令（需 Shizuku 或 root 权限）",
            inputSchema = JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("command", JSONObject().apply {
                        put("type", "string")
                        put("description", "要执行的 Shell 命令")
                    })
                    put("timeout", JSONObject().apply {
                        put("type", "number")
                        put("description", "超时时间（秒）")
                        put("default", 30)
                    })
                    put("workdir", JSONObject().apply {
                        put("type", "string")
                        put("description", "工作目录")
                        put("default", "/")
                    })
                })
                put("required", JSONArray().apply { put("command") })
            }
        ))

        // 系统信息
        toolList.add(MCPTool(
            name = "android_system_info",
            description = "获取 Android 设备系统信息",
            inputSchema = JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("category", JSONObject().apply {
                        put("type", "string")
                        put("description", "信息类别: all / hardware / software / storage / battery")
                        put("default", "all")
                    })
                })
                put("required", JSONArray())
            }
        ))

        // 剪贴板
        toolList.add(MCPTool(
            name = "android_clipboard",
            description = "读取或写入系统剪贴板",
            inputSchema = JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("action", JSONObject().apply {
                        put("type", "string")
                        put("description", "操作: read / write")
                    })
                    put("text", JSONObject().apply {
                        put("type", "string")
                        put("description", "要写入的文本（write 操作时需要）")
                    })
                })
                put("required", JSONArray().apply { put("action") })
            }
        ))

        // 网络信息
        toolList.add(MCPTool(
            name = "android_network_info",
            description = "获取网络状态和连接信息",
            inputSchema = JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("detail", JSONObject().apply {
                        put("type", "string")
                        put("description", "详细程度: basic / full")
                        put("default", "basic")
                    })
                })
                put("required", JSONArray())
            }
        ))

        // 应用信息
        toolList.add(MCPTool(
            name = "android_package_info",
            description = "获取已安装应用信息",
            inputSchema = JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("package", JSONObject().apply {
                        put("type", "string")
                        put("description", "包名（可选，留空返回所有应用列表）")
                    })
                })
                put("required", JSONArray())
            }
        ))

        _tools.value = toolList
    }

    // ========== 工具执行实现 ==========

    private fun executeReadFile(args: JSONObject): JSONObject {
        val path = args.optString("path", "")
        val encoding = args.optString("encoding", "utf-8")
        return try {
            val file = File(path)
            if (!file.exists()) return errorResult("文件不存在: $path")
            if (!file.canRead()) return errorResult("无读取权限: $path")

            val content = if (encoding == "base64") {
                android.util.Base64.encodeToString(file.readBytes(), android.util.Base64.NO_WRAP)
            } else {
                file.readText()
            }

            successResult("content" to content, "size" to file.length(), "path" to path)
        } catch (e: Exception) {
            errorResult("读取文件失败: ${e.message}")
        }
    }

    private fun executeWriteFile(args: JSONObject): JSONObject {
        val path = args.optString("path", "")
        val content = args.optString("content", "")
        val append = args.optBoolean("append", false)
        return try {
            val file = File(path)
            file.parentFile?.mkdirs()
            if (append) {
                file.appendText(content)
            } else {
                file.writeText(content)
            }
            successResult("path" to path, "size" to content.length.toLong(), "append" to append)
        } catch (e: Exception) {
            errorResult("写入文件失败: ${e.message}")
        }
    }

    private fun executeListFiles(args: JSONObject): JSONObject {
        val path = args.optString("path", "")
        val recursive = args.optBoolean("recursive", false)
        return try {
            val dir = File(path)
            if (!dir.exists()) return errorResult("目录不存在: $path")
            if (!dir.isDirectory()) return errorResult("不是目录: $path")

            val files = if (recursive) {
                dir.walkTopDown().toList()
            } else {
                dir.listFiles()?.toList() ?: emptyList()
            }

            val fileList = JSONArray()
            files.forEach { f ->
                fileList.put(JSONObject().apply {
                    put("name", f.name)
                    put("path", f.absolutePath)
                    put("isDirectory", f.isDirectory)
                    put("size", f.length())
                    put("lastModified", f.lastModified())
                    put("isHidden", f.isHidden)
                    put("canRead", f.canRead())
                    put("canWrite", f.canWrite())
                })
            }

            successResult("files" to fileList, "count" to files.size, "path" to path)
        } catch (e: Exception) {
            errorResult("列出目录失败: ${e.message}")
        }
    }

    private fun executeDeleteFile(args: JSONObject): JSONObject {
        val path = args.optString("path", "")
        val recursive = args.optBoolean("recursive", false)
        return try {
            val file = File(path)
            if (!file.exists()) return errorResult("文件不存在: $path")

            val deleted = if (file.isDirectory() && recursive) {
                file.deleteRecursively()
            } else {
                file.delete()
            }

            if (deleted) successResult("deleted" to true, "path" to path)
            else errorResult("删除失败: $path")
        } catch (e: Exception) {
            errorResult("删除文件失败: ${e.message}")
        }
    }

    private fun executeFileInfo(args: JSONObject): JSONObject {
        val path = args.optString("path", "")
        return try {
            val file = File(path)
            if (!file.exists()) return errorResult("文件不存在: $path")

            successResult(
                "name" to file.name,
                "path" to file.absolutePath,
                "isDirectory" to file.isDirectory,
                "isFile" to file.isFile,
                "size" to file.length(),
                "lastModified" to file.lastModified(),
                "isHidden" to file.isHidden,
                "canRead" to file.canRead(),
                "canWrite" to file.canWrite(),
                "canExecute" to file.canExecute(),
                "parent" to (file.parent ?: "")
            )
        } catch (e: Exception) {
            errorResult("获取文件信息失败: ${e.message}")
        }
    }

    private fun executeShellExec(args: JSONObject): JSONObject {
        val command = args.optString("command", "")
        val timeout = args.optInt("timeout", 30)
        val workdir = args.optString("workdir", "/")
        if (command.isBlank()) return errorResult("命令不能为空")

        return try {
            val process = ProcessBuilder("sh", "-c", command)
                .directory(File(workdir))
                .redirectErrorStream(true)
                .start()

            val completed = if (timeout > 0) {
                process.waitFor(timeout.toLong(), TimeUnit.SECONDS)
            } else {
                process.waitFor()
                true
            }

            if (!completed) {
                process.destroyForcibly()
                return errorResult("命令执行超时")
            }

            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.exitValue()

            successResult(
                "stdout" to output,
                "exitCode" to exitCode,
                "command" to command
            )
        } catch (e: Exception) {
            errorResult("执行命令失败: ${e.message}")
        }
    }

    private fun executeSystemInfo(args: JSONObject): JSONObject {
        val category = args.optString("category", "all")
        return try {
            val info = JSONObject()

            if (category == "all" || category == "hardware") {
                val hardware = JSONObject().apply {
                    put("brand", android.os.Build.BRAND)
                    put("model", android.os.Build.MODEL)
                    put("manufacturer", android.os.Build.MANUFACTURER)
                    put("device", android.os.Build.DEVICE)
                    put("product", android.os.Build.PRODUCT)
                    put("hardware", android.os.Build.HARDWARE)
                    put("board", android.os.Build.BOARD)
                    put("abi", android.os.Build.SUPPORTED_ABIS.joinToString(", "))
                }
                info.put("hardware", hardware)
            }

            if (category == "all" || category == "software") {
                val software = JSONObject().apply {
                    put("os", "Android")
                    put("sdk", android.os.Build.VERSION.SDK_INT)
                    put("release", android.os.Build.VERSION.RELEASE)
                    put("securityPatch", android.os.Build.VERSION.SECURITY_PATCH ?: "N/A")
                    put("type", android.os.Build.TYPE)
                    put("tags", android.os.Build.TAGS)
                    put("host", android.os.Build.HOST)
                }
                info.put("software", software)
            }

            if (category == "all" || category == "storage") {
                val storage = JSONObject().apply {
                    val root = File("/")
                    put("totalSpace", root.totalSpace)
                    put("freeSpace", root.freeSpace)
                    put("usableSpace", root.usableSpace)
                    put("dataDir", context.filesDir.absolutePath)
                    put("cacheDir", context.cacheDir.absolutePath)
                    put("externalDirs", context.getExternalFilesDirs(null).map { it.absolutePath })
                }
                info.put("storage", storage)
            }

            if (category == "all" || category == "battery") {
                val battery = JSONObject().apply {
                    put("note", "Use Android Intent to get battery info")
                }
                info.put("battery", battery)
            }

            successResult("info" to info)
        } catch (e: Exception) {
            errorResult("获取系统信息失败: ${e.message}")
        }
    }

    private fun executeClipboard(args: JSONObject): JSONObject {
        val action = args.optString("action", "read")
        return try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager

            if (action == "read") {
                val clip = clipboard.primaryClip
                val text = if (clip != null && clip.itemCount > 0) {
                    clip.getItemAt(0)?.text?.toString() ?: ""
                } else ""
                successResult("text" to text, "action" to action)
            } else if (action == "write") {
                val text = args.optString("text", "")
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("codex", text))
                successResult("action" to action, "written" to text.length)
            } else {
                errorResult("未知剪贴板操作: $action")
            }
        } catch (e: Exception) {
            errorResult("剪贴板操作失败: ${e.message}")
        }
    }

    private fun executeNetworkInfo(args: JSONObject): JSONObject {
        return try {
            val info = JSONObject()
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            val network = connectivityManager.activeNetwork
            val capabilities = network?.let { connectivityManager.getNetworkCapabilities(it) }

            info.put("hasNetwork", network != null)
            info.put("hasInternet", capabilities?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) ?: false)
            info.put("isWifi", capabilities?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) ?: false)
            info.put("isCellular", capabilities?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) ?: false)
            info.put("isEthernet", capabilities?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET) ?: false)
            info.put("isVPN", capabilities?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN) ?: false)

            successResult("network" to info)
        } catch (e: Exception) {
            errorResult("获取网络信息失败: ${e.message}")
        }
    }

    private fun executePackageInfo(args: JSONObject): JSONObject {
        return try {
            val packageManager = context.packageManager
            val packageName = args.optString("package", "")

            if (packageName.isNotBlank()) {
                val pkg = packageManager.getPackageInfo(packageName, 0)
                val appInfo = packageManager.getApplicationInfo(packageName, 0)
                successResult(
                    "package" to packageName,
                    "versionName" to (pkg.versionName ?: ""),
                    "versionCode" to pkg.longVersionCode,
                    "appName" to packageManager.getApplicationLabel(appInfo).toString(),
                    "firstInstall" to pkg.firstInstallTime,
                    "lastUpdate" to pkg.lastUpdateTime
                )
            } else {
                val packages = packageManager.getInstalledPackages(0)
                val pkgList = JSONArray()
                packages.forEach { pkg ->
                    pkgList.put(JSONObject().apply {
                        put("package", pkg.packageName)
                        put("versionName", pkg.versionName ?: "")
                    })
                }
                successResult("packages" to pkgList, "count" to packages.size)
            }
        } catch (e: Exception) {
            errorResult("获取应用信息失败: ${e.message}")
        }
    }

    // ========== 工具函数 ==========

    private fun successResult(vararg pairs: Pair<String, Any?>): JSONObject {
        return JSONObject().apply {
            put("isError", false)
            put("content", JSONObject().apply {
                pairs.forEach { (k, v) ->
                    when (v) {
                        is String -> put(k, v)
                        is Number -> put(k, v)
                        is Boolean -> put(k, v)
                        is JSONObject -> put(k, v)
                        is JSONArray -> put(k, v)
                        else -> put(k, v?.toString() ?: JSONObject.NULL)
                    }
                }
            }.toString(2))
        }
    }

    private fun errorResult(message: String): JSONObject {
        addLog("❌ $message")
        return JSONObject().apply {
            put("isError", true)
            put("content", message)
        }
    }

    private fun addLog(message: String) {
        _logs.value = _logs.value + "\n[${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}] $message"
        Log.d(TAG, message)
    }
}
