package com.paopaoime.company

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.paopaoime.aikeyboard.sdk.core.*
import com.paopaoime.aikeyboard.sdk.core.drawer.*

/** Test consumer only; never included in the public demo or SDK. */
class QuickReplyValidationActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = intent.getStringExtra("layout") ?: "builtin"
        intent.getStringExtra("theme")?.let { theme ->
            com.paopaoime.aikeyboard.ime.ui.settings.InputMethodSettingsBridge.setDarkModeOption(
                if (theme == "dark") com.paopaoime.aikeyboard.ime.ui.settings.InputMethodSettingsBridge.DarkModeOption.Dark
                else com.paopaoime.aikeyboard.ime.ui.settings.InputMethodSettingsBridge.DarkModeOption.Light
            )
        }
        val failFirst = intent.getBooleanExtra("failFirst", false)
        // Exercise the existing customer file, with synthetic values and no API keys.
        val configFile = java.io.File(filesDir, "oem_developer_ai_config.json")
        val backupFile = java.io.File(filesDir, "quick_reply_original_config.json")
        if (configFile.exists() && !backupFile.exists()) configFile.copyTo(backupFile)
        configFile.writeText("""{"modelConfig":{"temperature":0.27,"topP":0.81,"maxTokens":321},"replyConfig":{"systemPrompt":"CLIENT_CONFIGURATION_PROBE","replyCount":3,"tonePresets":[{"label":"客户文件语气","promptHint":"用自然的中文回复"}]}}""")
        var calls = 0
        OemImeSdk.setFeatureToggles(OemFeatureToggles(aiReplyEnabled = true, materialLibraryEnabled = true,
            developerEnabled = true, aiReplyCustomUiEnabled = layout != "builtin"))
        OemImeSdk.setAiReplyDispatchMode(OemAiReplyDispatchMode.HOST_ONLY)
        OemImeSdk.setAiReplyErrorTextCustomEnabled(true)
        OemImeSdk.setAiReplyErrorTexts(mapOf(OemAiReplyErrorCode.NETWORK to OemAiReplyErrorText("客户配置错误标题", "客户配置错误说明")))
        OemImeSdk.setAiReplyHostDelegate(OemAiReplyHostDelegate { request, callback ->
            calls++
            val configMatches = request.systemPrompt.contains("CLIENT_CONFIGURATION_PROBE") &&
                request.toneLabel == "客户文件语气" && request.maxTokens == 321 &&
                request.replyCount == 3 && kotlin.math.abs(request.temperature - 0.27) < 0.001 &&
                kotlin.math.abs(request.topP - 0.81) < 0.001
            val fail = (failFirst && calls == 1) || !configMatches
            val valid = request.ocrText == "今天有点累，工作上遇到一些不顺心的事情。"
            Log.i("QuickReplyValidation", "customerModelCall call=$calls exactSource=$valid configFile=$configMatches screenshotCount=${request.screenshots.size} mode=${request.generateMode}")
            Handler(Looper.getMainLooper()).postDelayed({
                callback.onResult(OemAiReplyResult(request.requestId, success = !fail,
                    replies = if (fail) emptyList() else listOf("客户模型回复：先休息一下，我一直都在", "慢慢来，我们一起把问题处理好", "辛苦了，先让自己放松一下"),
                    modelName = "customer-test-model", errorCode = if (fail) OemAiReplyErrorCode.NETWORK else null,
                    errorMessage = if (fail) "test network error" else null))
            }, 600)
            true
        })
        OemImeSdk.setDrawerContentFactory(OemDrawerKit.DRAWER_AI_REPLY,
            if (layout == "builtin") null else CustomerReplyFactory(layout == "full"))
        setContentView(TextView(this).apply { text = "测试配置就绪：$layout" })
        Log.i("QuickReplyValidation", "configured layout=$layout failFirst=$failFirst")
    }
}

private class CustomerReplyFactory(private val full: Boolean) : OemDrawerContentFactory {
    private var controller: OemDrawerController? = null
    private var content: LinearLayout? = null
    override fun onCreateFullDrawerView(context: Context, controller: OemDrawerController): View? =
        if (full) create(context, controller) else null
    override fun onCreateContentView(context: Context, controller: OemDrawerController): View = create(context, controller)
    private fun create(context: Context, control: OemDrawerController): View {
        controller = control
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 24, 32, 24)
            setBackgroundColor(0xFFF1F8FF.toInt())
        }
        content = root
        if (control.clipboardReplyState() == null) {
            root.addView(Button(context).apply {
                text = "标准复制回复"
                setOnClickListener {
                    OemImeSdk.aiReplyCapabilities()?.startPasteSession(object : OemAiReplyListener {
                        override fun onSuccess(result: OemAiReplySuggestions) {
                            onClipboardReplyStateChanged(OemClipboardReplyState("", suggestions = result.suggestions, modelName = result.modelName))
                        }
                        override fun onError(message: String) { root.addView(TextView(context).apply { text = message }) }
                    })
                }
            })
        }
        return root
    }
    override fun onClipboardReplyStateChanged(state: OemClipboardReplyState) {
        val root = content ?: return
        root.removeAllViews()
        root.addView(TextView(root.context).apply { text = if (full) "客户整块自绘结果" else "客户内容自绘结果" })
        root.addView(TextView(root.context).apply { text = if (state.loading) "客户加载中" else state.errorTitle ?: "客户模型：${state.modelName}" })
        state.errorDescription?.let { message -> root.addView(TextView(root.context).apply { text = message }) }
        state.suggestions.forEach { reply -> root.addView(Button(root.context).apply {
            text = reply
            setOnClickListener { controller?.insertClipboardReply(reply) }
        }) }
        if (!state.loading) root.addView(Button(root.context).apply {
            text = "原文重新生成"
            setOnClickListener { controller?.regenerateClipboardReply() }
        })
        root.addView(Button(root.context).apply { text = "关闭客户抽屉"; setOnClickListener { controller?.closeDrawer() } })
        Log.i("QuickReplyValidation", "customState full=$full loading=${state.loading} replies=${state.suggestions.size} error=${state.errorCode}")
    }
    override fun onDrawerStateChanged(state: OemDrawerState) {
        if (state == OemDrawerState.CLOSED) { content = null; controller = null }
    }
}
