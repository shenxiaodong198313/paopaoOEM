package org.fcitx.fcitx5.android.input.clipboard

import android.content.ClipboardManager
import android.content.Context
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.os.Build
import android.text.InputType
import android.view.inputmethod.EditorInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Capture on the IME thread before asynchronous history storage can outlive its source editor. */
class HostClipboardController(
    private val service: InputMethodService,
    private val foregroundPackage: () -> String?,
) : ClipboardManager.OnPrimaryClipChangedListener {
    private val clipboard = service.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    private val state = HostClipboardState()
    private val handler = Handler(Looper.getMainLooper())
    private var inputActive = false
    private var editable = false
    private val finishInput = Runnable { clearHost() }
    private val mutableSuggestion = MutableStateFlow<HostClipboardState.Suggestion?>(null)
    val suggestion = mutableSuggestion.asStateFlow()

    fun start() { clipboard.addPrimaryClipChangedListener(this) }

    fun startInput(info: EditorInfo) {
        // Android finishes and starts input synchronously when a selectable TextView hands
        // focus back to an editor in the same host. Preserve a still-unseen copy across that
        // handoff; a real departure is cleared on the next IME loop or immediately on unbind.
        handler.removeCallbacks(finishInput)
        inputActive = true
        val variation = info.inputType and InputType.TYPE_MASK_VARIATION
        val textEditor = info.inputType and InputType.TYPE_MASK_CLASS == InputType.TYPE_CLASS_TEXT
        val password = variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
        val privateEditor = info.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING != 0
        editable = textEditor && !password && !privateEditor
        val canCapture = (textEditor || info.inputType == InputType.TYPE_NULL) && !password && !privateEditor
        state.startHost(info.packageName, canCapture &&
            info.packageName != service.packageName, System.currentTimeMillis())
        publish()
    }

    fun markShown() { state.markShown() }
    fun hideKeyboard() { state.hideKeyboard(); publish() }
    fun finishInput() {
        inputActive = false
        publish()
        handler.removeCallbacks(finishInput)
        handler.post(finishInput)
    }
    fun unbindInput() { handler.removeCallbacks(finishInput); clearHost() }
    private fun clearHost() { inputActive = false; editable = false; state.finishHost(); publish() }
    fun dismiss() { state.dismiss(); publish() }

    fun isActiveHost(host: String): Boolean {
        val foreground = foregroundPackage()
        return inputActive && editable && state.matchesHost(host) && service.currentInputEditorInfo?.packageName == host &&
            service.currentInputConnection != null && (foreground == null || foreground == host)
    }

    fun current(): HostClipboardState.Suggestion? = suggestion.value?.takeIf { isActiveHost(it.host) }

    override fun onPrimaryClipChanged() {
        if (!inputActive) return
        val clip = runCatching { clipboard.primaryClip }.getOrNull()
        if (clip == null) { dismiss(); return }
        val timestamp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) clip.description.timestamp
            else System.currentTimeMillis()
        state.copied(
            text = if (clip.itemCount > 0) clip.getItemAt(0).text?.toString() else null,
            timestamp = timestamp,
            sensitive = clip.description.extras?.getBoolean("android.content.extra.IS_SENSITIVE", false) == true,
            foregroundHost = foregroundPackage(),
        )
        publish()
    }

    private fun publish() { mutableSuggestion.value = if (inputActive) state.suggestion else null }
    fun destroy() { clipboard.removePrimaryClipChangedListener(this); unbindInput() }
}
