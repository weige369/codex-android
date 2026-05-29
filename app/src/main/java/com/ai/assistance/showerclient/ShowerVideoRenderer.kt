package com.ai.assistance.showerclient

import android.view.Surface

class ShowerVideoRenderer {
    var onFrame: ((ByteArray) -> Unit)? = null
    fun attach(surface: Surface, videoWidth: Int, videoHeight: Int) {}
    fun detach() {}
    fun onBinaryFrame(data: ByteArray) { onFrame?.invoke(data) }
    fun render() {}
    fun captureCurrentFramePng(): ByteArray? = null
}
