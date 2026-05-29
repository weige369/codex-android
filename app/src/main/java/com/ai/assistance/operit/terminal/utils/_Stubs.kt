package com.ai.assistance.operit.terminal.utils

import android.content.Context
import com.ai.assistance.operit.terminal.data.PackageManagerType
import com.ai.assistance.operit.terminal.provider.filesystem.FileSystemProvider

open class SSHFileConnectionManager {
    constructor() {}
    companion object {
        @JvmStatic
        fun getInstance(context: Context): SSHFileConnectionManager = SSHFileConnectionManager()
    }
    fun getFileSystemProvider(): FileSystemProvider? = null
}

open class SourceManager {
    constructor() {}
    constructor(context: Context) {}
    fun getSelectedSource(type: PackageManagerType? = null): String = ""
    fun getAptSourceChangeCommand(source: String): String = ""
    fun getSelectedSource(): String = ""
}
