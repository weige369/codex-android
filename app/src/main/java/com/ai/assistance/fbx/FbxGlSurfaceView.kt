package com.ai.assistance.fbx

import android.content.Context
import android.opengl.GLSurfaceView

open class FbxGlSurfaceView(context: Context) : GLSurfaceView(context) {
    fun loadModel(path: String) {}
    fun loadModelFromBytes(data: ByteArray) {}
    fun setModelPosition(x: Float, y: Float, z: Float) {}
    fun setModelRotation(x: Float, y: Float, z: Float) {}
    fun setModelScale(scale: Float) {}
    fun setBackgroundColor(r: Float, g: Float, b: Float, a: Float) {}
    fun setAutoRotate(enabled: Boolean) {}
    fun captureScreenshot(): ByteArray? = null
    fun release() {}
    fun setOnRenderErrorListener(listener: (String) -> Unit) {}
    fun setOnAnimationsDiscoveredListener(listener: (List<String>, Map<String, Long>) -> Unit) {}
    fun setModelPath(path: String) {}
    fun setAnimationState(animation: String?, isLooping: Boolean, playbackNonce: Long) {}
    fun setCameraPose(pitch: Float, yaw: Float, distance: Float, height: Float) {}
    override fun onResume() { super.onResume() }
}

object FbxInspector {
    fun getMeshCount(path: String): Int = 0
    fun getAnimationNames(path: String): List<String> = emptyList()
    fun getMaterialCount(path: String): Int = 0
    fun inspectModel(path: String): FbxModelInfo? = FbxModelInfo()
    fun getLastError(): String = ""
}

data class FbxModelInfo(
    val meshCount: Int = 0,
    val animationCount: Int = 0,
    val hasSkeleton: Boolean = false,
    val materialCount: Int = 0,
    val animationNames: List<String> = emptyList(),
    val defaultAnimation: String? = null,
    val missingExternalFiles: List<String> = emptyList(),
    val requiredExternalFiles: List<String> = emptyList(),
)
