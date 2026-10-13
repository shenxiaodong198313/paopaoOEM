package org.fcitx.fcitx5.android.input.voice

import android.content.Context
import android.inputmethodservice.InputMethodService
import android.view.View
import androidx.fragment.app.Fragment

/** Small optional-module contract; the base IME has no dependency on sherpa or voice implementation. */
interface OemVoiceController {
    val active: Boolean
    fun start(): Boolean
    fun selection(start: Int, end: Int, candidatesStart: Int, candidatesEnd: Int)
    fun editorChanged(start: Int, end: Int)
    fun cancel()
}
interface OemVoiceProvider {
    fun controller(service: InputMethodService, show: (View?) -> Unit): OemVoiceController
    fun settings(): Fragment
    fun release(reason: String)
}
object OemVoiceBridge {
    private val provider: OemVoiceProvider? by lazy {
        try {
            Class.forName("com.paopaoime.aikeyboard.ime.voice.VoiceAddon")
                .getField("INSTANCE").get(null) as OemVoiceProvider
        } catch (_: ClassNotFoundException) { null }
          catch (error: ReflectiveOperationException) {
            android.util.Log.e("OemVoice", "Voice module unavailable (${error.javaClass.simpleName})"); null
        } catch (error: LinkageError) {
            android.util.Log.e("OemVoice", "Voice module incompatible (${error.javaClass.simpleName})"); null
        }
    }
    val installed: Boolean get() = provider != null
    fun enabled(context: Context) = installed && OemVoiceConfig.get(context).enabled
    fun controller(service: InputMethodService, show: (View?) -> Unit) =
        if (enabled(service)) provider?.controller(service, show) else null
    fun settings(context: Context): Fragment? = if (enabled(context)) provider?.settings() else null
    fun release(reason: String) { provider?.release(reason) }
}
