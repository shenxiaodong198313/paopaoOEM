package org.fcitx.fcitx5.android.data.theme

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.inputmethod.EditorInfo
import org.fcitx.fcitx5.android.utils.appContext
import java.util.UUID

/** Memory-only preview. The random editor token never becomes a theme preference. */
object SkinTrialSession {
    const val OPTION_PREFIX = "paopao.skin.trial:"
    private data class Session(val token:String,val theme:Theme,val owner:String,val expires:Long,val settingsName:String,val configuration:Boolean)
    private var session:Session? = null
    @Volatile private var rendering:Theme? = null
    private val handler=Handler(Looper.getMainLooper())
    private var expiry:Runnable? = null
    fun isTrial(theme:Theme) = theme.name.startsWith("skin_trial_")
    fun renderedTheme():Theme? = rendering

    fun begin(theme:Theme, validityMillis:Long = 600000, settingsName:String = theme.name, configuration:Boolean = false):String {
        check(Looper.myLooper()==Looper.getMainLooper())
        end()
        val token=UUID.randomUUID().toString()
        val preview=if(configuration && theme is Theme.Custom && !isTrial(theme)) theme.copy(name="skin_trial_config_$token") else theme
        session=Session(token,preview,ProtectedSkinStore.owner(),SystemClock.elapsedRealtime()+validityMillis.coerceIn(1,600000),settingsName,configuration)
        expiry=Runnable{end()}.also{handler.postDelayed(it,validityMillis.coerceIn(1,600000))}
        return OPTION_PREFIX+token
    }
    fun editor(info:EditorInfo) {
        val current=session
        val allowed=current!=null&&info.packageName==appContext.packageName&&info.privateImeOptions==OPTION_PREFIX+current.token&&
            current.expires>SystemClock.elapsedRealtime()&&current.owner==ProtectedSkinStore.owner()
        val next=if(allowed)LocalSkinConfiguration.decorate(current!!.theme,current.settingsName) else null
        if(rendering!=next){rendering=next;ThemeManager.notifyTrialThemeChanged()}
    }
    fun refreshLocalConfiguration() {
        val current=session ?: return
        if(rendering==null || current.expires<=SystemClock.elapsedRealtime() || current.owner!=ProtectedSkinStore.owner())return
        val next=LocalSkinConfiguration.decorate(current.theme,current.settingsName)
        if(rendering!=next){rendering=next;ThemeManager.notifyTrialThemeChanged()}
    }
    /** Only the foreground configuration editor can renew its own nonce. Ordinary trials keep their expiry. */
    fun renewConfiguration(option: String) {
        val current=session ?: return
        if (!current.configuration || option!=OPTION_PREFIX+current.token || rendering==null || current.settingsName.startsWith("skin_trial_") || SkinOwnershipStore.denied(current.settingsName) ||
            current.owner!=ProtectedSkinStore.owner() || current.expires<=SystemClock.elapsedRealtime()) return
        session=current.copy(expires=SystemClock.elapsedRealtime()+600000)
        expiry?.let(handler::removeCallbacks)
        expiry=Runnable{end()}.also{handler.postDelayed(it,600000)}
    }
    fun leaveEditor() {
        if(rendering!=null){rendering=null;ThemeManager.notifyTrialThemeChanged()}
    }
    fun end() {
        expiry?.let(handler::removeCallbacks);expiry=null;session=null;leaveEditor()
    }
    fun checkOwner() {
        val current=session ?: return
        if(current.owner!=ProtectedSkinStore.owner() || (current.configuration && SkinOwnershipStore.denied(current.settingsName)))end()
    }
}
