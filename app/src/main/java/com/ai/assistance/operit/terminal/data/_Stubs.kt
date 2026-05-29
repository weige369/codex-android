package com.ai.assistance.operit.terminal.data

import com.ai.assistance.operit.terminal.TerminalManager

class TerminalState {
    val sessions: List<TerminalManager.SessionInfo> = emptyList()
}

open class PackageManagerType {
    constructor() {}
    companion object {
        val APT: PackageManagerType = PackageManagerType()
    }
}
