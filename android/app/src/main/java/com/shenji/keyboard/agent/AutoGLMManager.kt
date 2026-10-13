package com.shenji.keyboard.agent

import android.accessibilityservice.AccessibilityService

class AutoGLMManager private constructor() {
    val service: AccessibilityService? = null

    fun performTap(x: Float, y: Float): Boolean = false

    companion object {
        fun getInstance(): AutoGLMManager? = null
    }
}
