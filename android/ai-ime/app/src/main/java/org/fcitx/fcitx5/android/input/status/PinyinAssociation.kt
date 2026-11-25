package org.fcitx.fcitx5.android.input.status

import org.fcitx.fcitx5.android.core.FcitxAPI
import org.fcitx.fcitx5.android.core.RawConfig
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** One user preference controls native pinyin and supported Rime schemas. */
object PinyinAssociation {
    private val changes = Mutex()
    suspend fun <T> change(block: suspend () -> T): T = changes.withLock { block() }
    fun supports(nativeIme: String) = nativeIme == "pinyin" || nativeIme == "shuangpin"

    fun enabled(config: RawConfig): Boolean = when (config.findByName("cfg")?.findByName("Prediction")?.value) {
        "True" -> true
        "False" -> false
        else -> error("中文联想暂不可用，请重新打开键盘重试")
    }

    suspend fun apply(engine: FcitxAPI, value: Boolean) {
        if (enabled(engine.getAddonConfig("pinyin")) == value) return
        engine.setAddonConfig("pinyin", RawConfig(arrayOf(RawConfig("Prediction", if (value) "True" else "False"))))
        check(enabled(engine.getAddonConfig("pinyin")) == value) { "中文联想未能生效，请重试" }
    }

    suspend fun applyRime(engine: FcitxAPI, value: Boolean) {
        engine.setAddonSubConfig("rime", "paopao-prediction", RawConfig(arrayOf(
            RawConfig("Enabled", if (value) "True" else "False")
        )))
        val result = engine.getAddonSubConfig("rime", "paopao-prediction").findByName("cfg")
        check(result?.findByName("Enabled")?.value == if (value) "True" else "False") { "中文联想未能生效，请重试" }
        check(result?.findByName("ErrorCode")?.value.isNullOrEmpty()) { "中文联想暂不可用，请重试" }
    }
}
