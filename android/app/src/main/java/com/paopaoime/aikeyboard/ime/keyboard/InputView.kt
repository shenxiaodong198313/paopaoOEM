package com.paopaoime.aikeyboard.ime.keyboard

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import com.paopaoime.aikeyboard.ime.entity.keyboard.SoftKey

class InputView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    fun responseKeyEvent(key: SoftKey) {}

    fun updateCandidateBar() {}
}

