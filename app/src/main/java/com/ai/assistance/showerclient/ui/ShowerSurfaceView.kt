package com.ai.assistance.showerclient.ui

import android.content.Context
import android.util.AttributeSet
import android.view.SurfaceView
import com.ai.assistance.showerclient.ShowerController

open class ShowerSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : SurfaceView(context, attrs) {
    fun bindController(controller: ShowerController?) {}
    fun captureCurrentFramePng(): ByteArray? = null
}
