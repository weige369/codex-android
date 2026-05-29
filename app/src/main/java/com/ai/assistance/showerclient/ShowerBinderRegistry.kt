package com.ai.assistance.showerclient

import com.ai.assistance.shower.IShowerService

object ShowerBinderRegistry {
    @JvmStatic
    fun setService(service: Any?) {}

    @JvmStatic
    fun getService(): IShowerService? = null

    @JvmStatic
    fun hasAliveService(): Boolean = false
}
