package com.ai.assistance.operit.terminal

import android.content.Context
import androidx.compose.runtime.Composable
import com.ai.assistance.operit.terminal.data.TerminalState
import com.ai.assistance.operit.terminal.provider.filesystem.FileSystemProvider
import com.ai.assistance.operit.terminal.provider.type.HiddenExecResult
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

class TerminalManager {
    val sessions: List<SessionInfo> = emptyList()
    val currentSessionId: String? = null
    val currentDirectory: String = "/"
    val isInteractiveMode: Boolean = false
    val interactivePrompt: String = ""
    val isFullscreen: Boolean = false
    val currentExecutingCommand: String? = null
    val ansiParser: AnsiParser? = null
    val output: String = ""

    private val _commandExecutionEvents = MutableSharedFlow<CommandExecutionEvent>()
    val commandExecutionEvents: SharedFlow<CommandExecutionEvent> = _commandExecutionEvents

    private val _directoryChangeEvents = MutableSharedFlow<SessionDirectoryEvent>()
    val directoryChangeEvents: SharedFlow<SessionDirectoryEvent> = _directoryChangeEvents

    private val _terminalState = MutableStateFlow(TerminalState())
    val terminalState: StateFlow<TerminalState> = _terminalState

    companion object {
        @JvmStatic
        fun getInstance(context: Context): TerminalManager = TerminalManager()
    }

    fun createSession(name: String): String = ""
    fun destroySession(id: String) {}
    fun executeCommand(sessionId: String, command: String): CommandExecutionEvent = CommandExecutionEvent("", "", "", 0, true)
    suspend fun initializeEnvironment(): Boolean = false
    fun cleanup() {}
    suspend fun createNewSession(title: String? = null): SessionInfo = SessionInfo("session_0")
    fun switchToSession(sessionId: String) {}
    fun closeSession(sessionId: String) {}
    fun sendCommandToSession(sessionId: String, command: String) {}
    fun sendCommandToSession(sessionId: String, command: String, commandId: String) {}
    fun executeHiddenCommand(command: String, executorKey: String = "default", timeoutMs: Long = 120000L): HiddenExecResult = HiddenExecResult()
    fun getFileSystemProvider(): FileSystemProvider = FileSystemProvider()
    fun char(c: Char) {}
    fun key(keyCode: Int) {}
    fun keyWithMeta(keyCode: Int, meta: Int) {}
    fun sendInput(input: String) {}
    fun sendInterruptSignal() {}
    fun getLineCount(): Int = 0
    fun getLineCount(path: String): Int = 0

    class SessionInfo(val id: String) {
        val title: String = ""
        val currentDirectory: String = "/"
        val isInteractiveMode: Boolean = false
        val currentExecutingCommand: String? = null
        val ansiParser: AnsiParser = AnsiParser()
    }
}

class AnsiParser {
    val size: Int = 0
    fun char(c: Char) {}
    fun key(keyCode: Int) {}
    fun keyWithMeta(keyCode: Int, meta: Int) {}
    fun getScreenContent(): Array<Array<TerminalChar>> = arrayOf()
    operator fun get(index: Int): Array<TerminalChar> = arrayOf()
}

class TerminalChar(val char: Char = ' ')

class CommandExecutionEvent(
    val sessionId: String,
    val command: String,
    val outputChunk: String,
    val exitCode: Int,
    val isCompleted: Boolean,
    val commandId: String = "",
    val output: String = "",
    val id: String = ""
)

class SessionDirectoryEvent(
    val sessionId: String,
    val path: String,
    val id: String = ""
)

class TerminalEnv {
    val terminalManager: TerminalManager = TerminalManager()
}

@Composable
fun rememberTerminalEnv(terminalManager: TerminalManager, forceShowSetup: Boolean = false): TerminalEnv = TerminalEnv()
