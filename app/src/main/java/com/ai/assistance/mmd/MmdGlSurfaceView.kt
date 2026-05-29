package com.ai.assistance.mmd

import android.content.Context
import android.opengl.GLSurfaceView

open class MmdGlSurfaceView(context: Context) : GLSurfaceView(context) {
    fun loadModel(path: String) {}
    fun loadMotion(path: String) {}
    fun setAnimation(name: String) {}
    fun play() {}
    fun pause() {}
    fun release() {}
    fun setOnRenderErrorListener(listener: (String) -> Unit) {}
    fun setModelPath(path: String) {}
    fun setAnimationState(animation: String?, isLooping: Boolean, playbackNonce: Long = 0L) {}
    fun setModelRotation(x: Float, y: Float, z: Float) {}
    fun setCameraDistanceScale(scale: Float) {}
    fun setCameraTargetHeight(height: Float) {}
}

object MmdNative {
    fun init() {}
    fun getVersion(): String = "stub"
    fun nativeReadMotionMaxFrame(motionPath: String): Int = 0
}
