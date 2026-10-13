package com.paopaoime.aikeyboard.ime.ui.fragment

import android.os.Bundle
import android.view.View
import android.widget.EditText
import androidx.activity.addCallback
import com.paopaoime.aikeyboard.ime.R
import com.paopaoime.aikeyboard.ime.bridge.ImeTextBridge

class KeyboardHeightAdjustFragment : BaseInputMethodSettingsFragment(
    R.layout.fragment_keyboard_height_adjust
) {
    private var keyboardAdjustmentActive = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val focusEditor = view.findViewById<EditText>(R.id.et_keyboard_height_adjust_focus)

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            if (!keyboardAdjustmentActive || !ImeTextBridge.requestDismissKeyboardAdjustment()) {
                parentFragmentManager.popBackStack()
            }
        }

        ImeTextBridge.bindKeyboardAdjustmentState { active ->
            keyboardAdjustmentActive = active
            if (!active && isAdded) {
                parentFragmentManager.popBackStack()
            }
        }

        focusEditor.post {
            focusEditor.requestFocus()
            focusEditor.setSelection(focusEditor.text?.length ?: 0)
            ImeTextBridge.requestShowKeyboard()
            focusEditor.postDelayed({
                keyboardAdjustmentActive = ImeTextBridge.requestBeginKeyboardAdjustment()
                if (!keyboardAdjustmentActive && isAdded) {
                    parentFragmentManager.popBackStack()
                }
            }, 220)
        }
    }

    override fun onDestroyView() {
        ImeTextBridge.unbindKeyboardAdjustmentState()
        super.onDestroyView()
    }
}
