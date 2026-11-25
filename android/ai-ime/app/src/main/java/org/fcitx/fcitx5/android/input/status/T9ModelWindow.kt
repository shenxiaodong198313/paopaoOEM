// SPDX-License-Identifier: LGPL-2.1-or-later
package org.fcitx.fcitx5.android.input.status

import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import kotlinx.coroutines.*
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.input.dependency.inputMethodService
import org.fcitx.fcitx5.android.input.dependency.theme
import org.fcitx.fcitx5.android.input.keyboard.KeyboardWindow
import org.fcitx.fcitx5.android.input.wm.InputWindow
import org.fcitx.fcitx5.android.input.wm.InputWindowManager
import org.mechdancer.dependency.manager.must

class T9ModelWindow : InputWindow.ExtendedInputWindow<T9ModelWindow>() {
    override val title = "九键增强"
    private val service by manager.inputMethodService()
    private val theme by manager.theme()
    private val windows: InputWindowManager by manager.must()
    private var scope: CoroutineScope? = null
    private var request: T9LanguageModel.Request? = null
    private lateinit var status: TextView
    private lateinit var download: TextView
    private lateinit var enable: TextView
    private val preference get() = AppPrefs.getInstance().internal.t9LanguageModelEnabled
    override fun onCreateView(): View {
        val palette = ToolPanelPalette(theme.backgroundColor, theme.keyTextColor, theme.accentKeyBackgroundColor)
        fun dp(v: Int) = (v * context.resources.displayMetrics.density).toInt()
        val root = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(5), dp(14), dp(5)) }
        fun label(text: String, action: (() -> Unit)? = null) = TextView(context).apply {
            this.text = text; textSize = 13f; setTextColor(palette.ink); gravity = Gravity.CENTER
            if (action != null) {
                background = GradientDrawable().apply { setColor(palette.card); cornerRadius = dp(10).toFloat() }
                setOnClickListener { action() }
            }
            root.addView(this, LinearLayout.LayoutParams(-1, 0, if (action == null) 1.5f else 1f).apply { setMargins(0, dp(3), 0, dp(3)) })
        }
        label("万象词组与语法增强 · 约 399 MB\n下载后离线使用，长句可能更慢")
        status = label("检查模型…")
        download = label("下载模型") {
            if (request != null) { request?.cancel(); return@label }
            val current = T9LanguageModel.Request(); request = current
            download.text = "取消下载"; enable.isEnabled = false
            scope?.launch {
                try {
                    T9LanguageModel.download(current) { percent -> status.post {
                        if (request === current) status.text = "正在下载 $percent% · 退出面板会暂停"
                    } }
                    status.text = "模型已校验，可开启九键增强"
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (error: Exception) { status.text = if (error is java.io.IOException) "无法连接下载源，进度已保留，请检查网络后重试" else error.message ?: "下载失败，请重试" }
                finally { request = null; download.text = "下载 / 继续下载"; update() }
            }
        }
        enable = label("开启九键增强") {
            if (request != null) return@label
            if (preference.getValue()) {
                preference.setValue(false); AppPrefs.getInstance().internal.handwritingSelected.setValue(false); service.selectT9Keyboard(); windows.attachWindow(KeyboardWindow)
            } else scope?.launch {
                enable.isEnabled = false
                if (T9LanguageModel.verifyInstalled()) {
                    preference.setValue(true); AppPrefs.getInstance().internal.handwritingSelected.setValue(false); service.selectT9Keyboard(); windows.attachWindow(KeyboardWindow)
                } else { status.text = "请先下载完整模型"; update() }
            }
        }
        label("amzxyz / RIME-LMDG · CC BY 4.0\n模型原样使用，泡泡独立适配。长句可在候选区逐段选词。")
        label("返回键盘") { windows.attachWindow(KeyboardWindow) }
        return root
    }
    private fun update() {
        enable.text = if (preference.getValue()) "关闭九键增强" else "开启九键增强"
        enable.isEnabled = request == null && (T9LanguageModel.available || preference.getValue())
        enable.alpha = if (enable.isEnabled) 1f else .45f
        download.visibility = if (T9LanguageModel.available) View.GONE else View.VISIBLE
    }
    override fun onAttached() {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope?.launch {
            withContext(Dispatchers.IO) { T9LanguageModel.restore() }
            status.text = if (T9LanguageModel.available) "模型已下载" else "从上游 GitHub 下载，建议使用 Wi-Fi"
            update()
        }
    }
    override fun onDetached() { request?.cancel(); scope?.cancel(); scope = null }
}
