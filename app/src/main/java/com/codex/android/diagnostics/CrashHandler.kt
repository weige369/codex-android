package com.codex.android.diagnostics

import android.content.Context
import android.os.Build
import android.os.Process
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.*

/**
 * 崩溃捕获与日志导出。
 *
 * 捕获未处理的异常，保存崩溃日志到文件，
 * 支持导出 logcat 日志。
 */
class CrashHandler(private val context: Context) {

    companion object {
        private const val TAG = "CrashHandler"
        private const val CRASH_DIR = "crashes"
        private const val LOG_DIR = "logs"
    }

    private var defaultHandler: Thread.UncaughtExceptionHandler? = null
    private var onCrashCaptured: ((File) -> Unit)? = null

    /**
     * 自动上传最近未上报的崩溃报告
     */
    private fun autoUploadCrash() {
        try {
            val prefs = DiagnosticPrefs(context)
            if (!prefs.uploadOnCrash || !prefs.isConfigured) return

            val crashDir = File(context.filesDir, CRASH_DIR)
            val uploadedFlag = File(context.filesDir, "crashes_uploaded.flag")
            if (!crashDir.exists()) return

            val crashes = crashDir.listFiles()?.filter { it.extension == "txt" } ?: return
            if (crashes.isEmpty()) return
            
            // 标记已上传，避免重复上传
            if (!uploadedFlag.exists()) {
                uploadedFlag.createNewFile()
                
                // 后台异步上传
                Thread {
                    try {
                        val report = buildCrashReportText(crashes.first())
                        val uploader = ReportUploader(context)
                        // 使用 Gist 上传
                        kotlinx.coroutines.runBlocking {
                            uploader.uploadToGist(
                                createDummyReport(context, report),
                                prefs.githubToken
                            )
                        }
                    } catch (_: Exception) {}
                }.start()
            }
        } catch (_: Exception) {}
    }

    private fun createDummyReport(context: Context, crashText: String): com.codex.android.diagnostics.DiagnosticsRunner.DiagnosticsReport {
        return com.codex.android.diagnostics.DiagnosticsRunner.DiagnosticsReport(
            results = listOf(
                com.codex.android.diagnostics.DiagnosticsRunner.TestResult(
                    "崩溃报告", true, "已自动捕获崩溃日志", 
                    com.codex.android.diagnostics.DiagnosticsRunner.Severity.ERROR
                )
            )
        )
    }

    private fun buildCrashReportText(crashFile: File): String {
        return try { crashFile.readText() } catch (_: Exception) { "无法读取崩溃日志" }
    }

    /**
     * 注册崩溃捕获
     */
    fun register(onCrash: ((crashFile: File) -> Unit)? = null) {
        onCrashCaptured = onCrash
        defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        autoUploadCrash()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val crashFile = saveCrashReport(throwable)
                try { onCrashCaptured?.invoke(crashFile) } catch (_: Exception) {}
            } catch (e: Exception) {
                android.util.Log.e(TAG, "崩溃处理异常", e)
            }

            // 传递给默认处理器
            try {
                defaultHandler?.uncaughtException(thread, throwable)
            } catch (_: Exception) {}
            Process.killProcess(Process.myPid())
        }
    }

    /**
     * 保存崩溃报告到文件
     */
    fun saveCrashReport(throwable: Throwable): File {
        val dir = File(context.filesDir, CRASH_DIR).also { it.mkdirs() }
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val file = File(dir, "crash_$timestamp.txt")

        FileWriter(file).use { writer ->
            writer.write("=== Codex Android Crash Report ===\n")
            writer.write("Time: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}\n")
            writer.write("Device: ${Build.MANUFACTURER} ${Build.MODEL}\n")
            writer.write("Android: ${Build.VERSION.SDK_INT} (${Build.VERSION.RELEASE})\n")
            writer.write("App: ${context.packageName}\n")
            writer.write("\n=== Exception ===\n")
            val sw = StringWriter()
            val pw = PrintWriter(sw)
            throwable.printStackTrace(pw)
            pw.flush()
            writer.write(sw.toString())
            writer.write("\n=== Cause ===\n")
            var cause = throwable.cause
            while (cause != null) {
                val cw = StringWriter()
                val cpw = PrintWriter(cw)
                cause.printStackTrace(cpw)
                cpw.flush()
                writer.write(cw.toString())
                writer.write("\n---\n")
                cause = cause.cause
            }
        }
        return file
    }

    /**
     * 获取所有崩溃日志
     */
    fun getCrashLogs(): List<File> {
        val dir = File(context.filesDir, CRASH_DIR)
        return if (dir.exists()) {
            dir.listFiles()?.sortedByDescending { it.lastModified() }?.toList() ?: emptyList()
        } else emptyList()
    }

    /**
     * 清除崩溃日志
     */
    fun clearCrashLogs() {
        File(context.filesDir, CRASH_DIR).let { dir ->
            if (dir.exists()) dir.listFiles()?.forEach { it.delete() }
        }
    }

    /**
     * 导出最近的 logcat 日志
     */
    fun exportLogcat(lines: Int = 500): File {
        val dir = File(context.filesDir, LOG_DIR).also { it.mkdirs() }
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val file = File(dir, "logcat_$timestamp.txt")

        try {
            val process = Runtime.getRuntime().exec(arrayOf(
                "logcat", "-d",
                "-t", lines.toString(),
                "-s", "CodexRuntimeService:V CodexActivity:V CodexBridge:V CodexWebViewBridge:V DevelopmentEnvironment:V DiagnosticsRunner:V chromium:V"
            ))
            val reader = java.io.BufferedReader(java.io.InputStreamReader(process.inputStream))
            FileWriter(file).use { writer ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    writer.write("$line\n")
                }
            }
            process.waitFor()
        } catch (e: Exception) {
            FileWriter(file).use { writer ->
                writer.write("导出 logcat 失败: ${e.message}")
            }
        }

        return file
    }
}
