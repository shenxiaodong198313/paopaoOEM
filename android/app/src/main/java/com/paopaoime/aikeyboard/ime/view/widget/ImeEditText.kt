package com.paopaoime.aikeyboard.ime.view.widget

import android.content.Context
import android.util.AttributeSet
import android.widget.EditText

class ImeEditText : EditText {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    fun commitText(content: String) {
        val editable = text ?: return
        val start = selectionStart.coerceIn(0, editable.length)
        val end = selectionEnd.coerceIn(0, editable.length)
        if (start != end) {
            editable.replace(start, end, content)
        } else {
            editable.insert(start, content)
        }
        val next = (start + content.length).coerceIn(0, editable.length)
        setSelection(next)
    }
}

