package com.ai.assistance.showerclient

import android.util.Log

enum class ShellIdentity {
    DEFAULT,
    SHELL,
    ROOT,
}

data class ShellCommandResult(
    val success: Boolean = false,
    val stdout: String = "",
    val stderr: String = "",
    val exitCode: Int = -1,
)

fun interface ShellRunner {
    suspend fun run(command: String, identity: ShellIdentity): ShellCommandResult
}

fun interface ShowerLogSink {
    fun log(priority: Int, tag: String, message: String, throwable: Throwable?)
}

object ShowerEnvironment {
    @Volatile
    var shellRunner: ShellRunner? = null

    @Volatile
    var logSink: ShowerLogSink? = null

    @Volatile
    var emitToSystemLog: Boolean = true
}

object ShowerLog {
    private fun emit(priority: Int, tag: String, message: String, throwable: Throwable? = null) {
        if (ShowerEnvironment.emitToSystemLog) {
            when (priority) {
                Log.VERBOSE -> if (throwable != null) Log.v(tag, message, throwable) else Log.v(tag, message)
                Log.DEBUG -> if (throwable != null) Log.d(tag, message, throwable) else Log.d(tag, message)
                Log.INFO -> if (throwable != null) Log.i(tag, message, throwable) else Log.i(tag, message)
                Log.WARN -> if (throwable != null) Log.w(tag, message, throwable) else Log.w(tag, message)
                Log.ERROR -> if (throwable != null) Log.e(tag, message, throwable) else Log.e(tag, message)
                Log.ASSERT -> if (throwable != null) Log.wtf(tag, message, throwable) else Log.wtf(tag, message)
                else -> Log.println(priority, tag, message)
            }
        }

        try {
            ShowerEnvironment.logSink?.log(priority, tag, message, throwable)
        } catch (_: Throwable) { }
    }

    fun v(tag: String, message: String) = emit(Log.VERBOSE, tag, message)
    fun v(tag: String, message: String, throwable: Throwable) = emit(Log.VERBOSE, tag, message, throwable)
    fun d(tag: String, message: String) = emit(Log.DEBUG, tag, message)
    fun d(tag: String, message: String, throwable: Throwable) = emit(Log.DEBUG, tag, message, throwable)
    fun i(tag: String, message: String) = emit(Log.INFO, tag, message)
    fun i(tag: String, message: String, throwable: Throwable) = emit(Log.INFO, tag, message, throwable)
    fun w(tag: String, message: String) = emit(Log.WARN, tag, message)
    fun w(tag: String, message: String, throwable: Throwable) = emit(Log.WARN, tag, message, throwable)
    fun e(tag: String, message: String) = emit(Log.ERROR, tag, message)
    fun e(tag: String, message: String, throwable: Throwable) = emit(Log.ERROR, tag, message, throwable)
}
