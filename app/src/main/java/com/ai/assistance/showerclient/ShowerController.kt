package com.ai.assistance.showerclient

import android.content.Context

class ShowerController {
    fun getDisplayId(): Int? = null
    fun getVideoSize(): Pair<Int, Int>? = null
    fun setBinaryHandler(handler: ((ByteArray) -> Unit)?) {}
    suspend fun requestScreenshot(timeoutMs: Long = 3000L): ByteArray? = null
    suspend fun prepareMainDisplay(context: Context): Boolean = false
    suspend fun ensureDisplay(context: Context, width: Int, height: Int, dpi: Int, bitrateKbps: Int? = null): Boolean = false
    suspend fun launchApp(packageName: String): Boolean = false
    suspend fun tap(x: Int, y: Int): Boolean = false
    suspend fun swipe(startX: Int, startY: Int, endX: Int, endY: Int, durationMs: Long = 300L): Boolean = false
    suspend fun touchDown(x: Int, y: Int): Boolean = false
    suspend fun touchMove(x: Int, y: Int): Boolean = false
    suspend fun touchUp(x: Int, y: Int): Boolean = false
    suspend fun injectTouchEvent(action: Int, x: Float, y: Float, downTime: Long, eventTime: Long, pressure: Float, size: Float, metaState: Int, xPrecision: Float, yPrecision: Float, deviceId: Int, edgeFlags: Int): Boolean = false
    fun shutdown() {}
    suspend fun key(keyCode: Int): Boolean = false
    suspend fun keyWithMeta(keyCode: Int, metaState: Int): Boolean = false
}
