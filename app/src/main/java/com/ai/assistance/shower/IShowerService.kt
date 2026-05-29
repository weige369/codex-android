package com.ai.assistance.shower
import android.os.IInterface
interface IShowerService : IInterface {
    fun getDisplayId(): Int = 0; fun getVideoSize(): Pair<Int, Int> = 0 to 0
    fun ensureDisplay(): Boolean = false; fun prepareMainDisplay(): Boolean = false
    fun requestScreenshot(): ByteArray? = null; fun injectTouchEvent(x: Float, y: Float, action: Int) {}
    fun tap(x: Float, y: Float) {}; fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, duration: Long) {}
    fun launchApp(packageName: String) {}; fun shutdown() {}
    abstract class Stub : android.os.Binder(), IShowerService {
        override fun asBinder() = this
        companion object { fun asInterface(binder: android.os.IBinder?): IShowerService? = null }
    }
}
