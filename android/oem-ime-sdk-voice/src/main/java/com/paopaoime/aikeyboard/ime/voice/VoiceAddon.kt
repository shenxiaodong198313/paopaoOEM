package com.paopaoime.aikeyboard.ime.voice

import android.inputmethodservice.InputMethodService
import android.view.View
import com.paopaoime.aikeyboard.ime.ui.fragment.VoiceToTextSettingsFragment
import org.fcitx.fcitx5.android.input.voice.OemVoiceProvider

/** Loaded by the base keyboard only when this optional AAR is installed. */
object VoiceAddon : OemVoiceProvider {
    override fun controller(service: InputMethodService, show: (View?) -> Unit) = VoiceImeController(service, show)
    override fun settings() = VoiceToTextSettingsFragment()
    override fun release(reason: String) = VoiceRuntime.release(reason)
}
