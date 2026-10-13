package com.paopaoime.aikeyboard.ime.voice

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.text.InputType
import android.view.View
import android.widget.Toast
import androidx.core.content.ContextCompat
import android.inputmethodservice.InputMethodService
import org.fcitx.fcitx5.android.input.voice.OemVoiceController

class VoiceImeController(private val service: InputMethodService, private val show: (View?) -> Unit) : OemVoiceController {
    private var session: VoiceSession? = null
    private var textSession: VoiceTextSession? = null
    private var panel: VoicePanel? = null
    private val display = VoiceDisplay()
    private var epoch = 0L
    private var cursor = -1
    override val active get() = session?.active == true
    override fun start(): Boolean {
        if (!org.fcitx.fcitx5.android.input.voice.OemVoiceConfig.get(service).enabled) return false
        if (active) return true
        val info = service.currentInputEditorInfo ?: return false
        if (!service.isInputViewShown || isPassword(info.inputType)) return false
        if (!VoiceRepository.get(service).ready()) {
            service.startActivity(Intent(service, VoiceSettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return true
        }
        if (ContextCompat.checkSelfPermission(service, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            service.startActivity(Intent(service, VoicePermissionActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return true
        }
        val connection = service.currentInputConnection ?: return false
        val token = ++epoch
        textSession = VoiceTextSession(connection, cursor) { epoch == token && service.currentInputConnection === connection && service.isInputViewShown }
        display.reset()
        val surface = VoicePanel(service) { session?.finish() }; panel = surface; show(surface)
        val next = VoiceSession(service, object : VoiceSession.Observer {
            override fun state(value: String, message: String) {
                if (epoch != token) return
                if (value == "idle") {
                    textSession?.finish(); textSession = null; show(null); panel = null; display.reset()
                    if (message.isNotBlank()) Toast.makeText(service, message, Toast.LENGTH_SHORT).show()
                } else surface.updateState(value, message)
            }
            override fun text(value: String, final: Boolean) {
                if (epoch != token) return
                if (textSession?.apply(value, final) != true) { cancel(); return }
                surface.updateText(display.apply(value, final))
            }
            override fun level(value: Float) { if (epoch == token) surface.updateLevel(value) }
        })
        session = next; next.start(); return true
    }
    override fun selection(start: Int, end: Int, candidatesStart: Int, candidatesEnd: Int) {
        cursor = if (start == end) start else -1
        if (active && textSession?.selection(start, end, candidatesStart, candidatesEnd) == false) cancel()
    }
    override fun editorChanged(start: Int, end: Int) { cancel(); cursor = if (start == end) start else -1 }
    override fun cancel() {
        session?.cancel(); session = null; textSession?.finish(); textSession = null
        epoch++; if (panel != null) show(null); panel = null; display.reset()
    }
    companion object {
        fun isPassword(type: Int): Boolean {
            val variation = type and InputType.TYPE_MASK_VARIATION
            return type and InputType.TYPE_MASK_CLASS == InputType.TYPE_CLASS_NUMBER && variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD ||
                type and InputType.TYPE_MASK_CLASS == InputType.TYPE_CLASS_TEXT && variation in setOf(InputType.TYPE_TEXT_VARIATION_PASSWORD, InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD, InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)
        }
    }
}
