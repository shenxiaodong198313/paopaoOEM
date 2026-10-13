package com.paopaoime.aikeyboard.ime.ui.fragment

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment

class ToolsFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val textView = TextView(context)
        textView.text = "宸ュ叿"
        textView.textSize = 24f
        textView.gravity = Gravity.CENTER
        return textView
    }
}

