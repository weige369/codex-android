package com.ai.assistance.operit.terminal.provider.type

open class HiddenExecResult {
    constructor()
    val stdout: String = ""
    val stderr: String = ""
    val exitCode: Int = 0
    val isSuccess: Boolean = false
    val output: String = ""
    val isOk: Boolean = false
    val error: String = ""
    val rawOutputPreview: String = ""

    enum class State {
        RUNNING, SUCCESS, FAILED, TIMEOUT
    }
    val state: State = State.SUCCESS
    val ansiParser: Any? = null
    val size: Int = 0
}
