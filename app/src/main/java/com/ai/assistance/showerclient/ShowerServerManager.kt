package com.ai.assistance.showerclient

import android.content.Context

object ShowerServerManager {
    @JvmStatic
    suspend fun ensureServerStarted(context: Context): Boolean = false
    
    @JvmStatic
    suspend fun stopServer(): Boolean = false
    
    @JvmStatic
    fun isRunning(): Boolean = false
    
    @JvmStatic
    fun start(context: Any?) {}
    
    @JvmStatic
    fun stop() {}
}
