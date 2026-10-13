package com.paopaoime.company

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.paopaoime.aikeyboard.sdk.core.*
import com.paopaoime.aikeyboard.sdk.core.OemImeSdk
import com.paopaoime.aikeyboard.sdk.core.drawer.*
import com.paopaoime.aikeyboard.ime.ui.settings.InputMethodSettingsBridge as Settings
import org.fcitx.fcitx5.android.input.bar.ToolbarActionHostBridge
import org.fcitx.fcitx5.android.input.status.CustomDrawerHostBridge
import org.json.JSONObject
import org.fcitx.fcitx5.android.input.materialhub.MaterialHubHostBridge
import org.fcitx.fcitx5.android.input.materialhub.MaterialHubLaunchRequest

/** External SDK consumer regression fixture; never ship in SDK or public demo. */
class EntryValidationActivity : Activity() {
    private val main = Handler(Looper.getMainLooper())
    private lateinit var editor: EditText
    private lateinit var status: TextView

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        editor = EditText(this).apply { hint = "Callback regression editor"; isSingleLine = true }
        status = TextView(this).apply { text = "Waiting for SDK" }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 100, 24, 24)
            addView(status); addView(editor)
        })
        ready(0)
    }

    private fun ready(attempt: Int) {
        if (CompanyApplication.lastStatus == null && attempt < 120) {
            main.postDelayed({ ready(attempt + 1) }, 500); return
        }
        check(CompanyApplication.lastStatus != null) { "SDK init failed" }
        OemImeSdk.setFeatureToggles(OemFeatureToggles(customDrawersEnabled = true,
            materialLibraryEnabled = true, aiReplyEnabled = true))
        if (OemImeSdk.customDrawers().isEmpty()) register()
        status.text = "SDK ready: callback entry regression"
        report("ready")
        showKeyboard()
        main.postDelayed({ toolbarLayout(); command(intent) }, 1800)
    }

    private fun register() {
        val factory = object : OemDrawerContentFactory {
            override fun onCreateContentView(context: Context, controller: OemDrawerController): View {
                factories++
                report("factory")
                return TextView(context).apply { text = "DEFAULT_DRAWER_CONTENT"; textSize = 24f }
            }
        }
        for ((id, title) in listOf("yes" to "回调真", "no" to "回调假", "throws" to "回调异常", "default" to "默认抽屉")) {
            // Old Kotlin five-position constructor remains source compatible.
            val spec = OemCustomDrawerSpec(id, title, 0, null, factory)
            check(spec.component5() === factory)
            val configured = if (id == "default") spec else spec.copy(entryHandler = OemCustomDrawerEntryHandler { context, clicked ->
                callbacks++
                lastId = clicked
                onMain = Looper.myLooper() == Looper.getMainLooper()
                serviceContext = context is android.inputmethodservice.InputMethodService
                report("callback")
                if (id == "throws") throw IllegalStateException("SYNTHETIC_HOST_EXCEPTION")
                id == "yes"
            })
            check(OemImeSdk.registerCustomDrawer(configured) == OemRegisterResult.OK)
        }
        check(JavaCompatibility.create(factory).entryHandler == null)
        check(OemImeSdk.registerCustomDrawer(OemCustomDrawerSpec("yes", "duplicate", contentFactory = factory)) == OemRegisterResult.DUPLICATE_ID)
        check(OemImeSdk.registerCustomDrawer(OemCustomDrawerSpec("fifth", "quota", contentFactory = factory)) == OemRegisterResult.QUOTA_EXCEEDED)
    }

    override fun onNewIntent(value: Intent) {
        super.onNewIntent(value); intent = value; command(value)
    }

    private fun command(value: Intent) {
        val action = value.getStringExtra("action") ?: "snapshot"
        val id = value.getStringExtra("drawer") ?: "yes"
        when (action) {
            "show" -> showKeyboard()
            "quick_setup" -> setupQuickReply(value.getStringExtra("mode") ?: "true")
            "copy" -> getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("fixture", SOURCE))
            "quick_bridge" -> check(MaterialHubHostBridge.launch(MaterialHubLaunchRequest(
                target = MaterialHubLaunchRequest.Target.AI_REPLY, clipboardReplyText = SOURCE,
                hostPackageName = if (value.getBooleanExtra("nullHost", false)) null else packageName)))
            "theme" -> Settings.setDarkModeOption(if (value.getStringExtra("theme") == "dark") Settings.DarkModeOption.Dark else Settings.DarkModeOption.Light)
            "reset" -> { callbacks = 0; factories = 0; lastId = ""; onMain = false; serviceContext = false }
            "toolbar" -> check(ToolbarActionHostBridge.launchCustomDrawer(id))
            "status" -> check(CustomDrawerHostBridge.launch(id))
            "background" -> Thread { check(ToolbarActionHostBridge.launchCustomDrawer(id)) }.start()
            "registry" -> runCatching { registryChecks() }.onFailure { Log.e("EntryValidation", "registry_failed", it) }
            "layout" -> toolbarLayout()
        }
        main.postDelayed({ report(action) }, 700)
    }

    private fun setupQuickReply(mode: String) {
        quickCalls = 0; quickMain = false; quickSource = false; quickHost = null; modelCalls = 0
        OemImeSdk.setFeatureToggles(OemFeatureToggles(customDrawersEnabled = true,
            aiReplyEnabled = mode == "default", materialLibraryEnabled = mode == "default"))
        OemImeSdk.setAiReplyDispatchMode(OemAiReplyDispatchMode.HOST_ONLY)
        OemImeSdk.setAiReplyHostDelegate(OemAiReplyHostDelegate { request, callback ->
            modelCalls++
            check(request.ocrText == SOURCE)
            callback.onResult(OemAiReplyResult(request.requestId, success = true,
                replies = listOf("DEFAULT_QUICK_REPLY_DRAFT"), modelName = "synthetic-offline"))
            report("model"); true
        })
        OemImeSdk.setClipboardQuickReplyHandler(if (mode == "default") null else OemClipboardQuickReplyHandler { context, text, host ->
            quickCalls++; quickMain = Looper.myLooper() == Looper.getMainLooper()
            quickSource = text == SOURCE && context is android.inputmethodservice.InputMethodService
            quickHost = host; report("quick_callback")
            if (mode == "unregister") OemImeSdk.setClipboardQuickReplyHandler(null)
            if (mode == "throws") throw IllegalStateException("SYNTHETIC_QUICK_REPLY_EXCEPTION")
            mode != "false"
        })
        editor.setText("")
    }

    private fun toolbarLayout() {
        val items = Settings.availableDisplayItems(this)
        val selected = items.filterIsInstance<Settings.ToolbarDisplayItem.Custom>() +
            items.filterIsInstance<Settings.ToolbarDisplayItem.Builtin>().filter {
                it.item == Settings.ToolbarItem.Clipboard || it.item == Settings.ToolbarItem.CustomizeToolbar
            }
        Settings.setToolbarDisplayVisibleItems(this, selected.toSet())
        Settings.setToolbarDisplayOrder(this, selected)
    }

    private fun registryChecks() {
        val original = OemImeSdk.findCustomDrawer("yes")!!
        check(OemImeSdk.updateCustomDrawer(original.copy(title = "Updated callback")) == OemRegisterResult.OK)
        check(CustomDrawerHostBridge.entries().first { it.id == "yes" }.title == "Updated callback")
        val items = Settings.availableDisplayItems(this)
        val chosen = items.filter { it.prefValue() != "custom_yes" }.take(Settings.MAX_TOOLBAR_ITEMS)
        Settings.setToolbarDisplayVisibleItems(this, chosen.toSet())
        Settings.setToolbarDisplayOrder(this, chosen.reversed())
        val full = Settings.getToolbarDisplaySettings(this)
        Log.i("EntryValidation", "registry chosen=${chosen.map { it.prefValue() }} actual=${full.visibleItems.map { it.prefValue() }}")
        check(full.visibleItems.size == Settings.MAX_TOOLBAR_ITEMS && full.visibleItems.none { it.prefValue() == "custom_yes" })
        check(full.orderedItems.map { it.prefValue() } == chosen.reversed().map { it.prefValue() })
        toolbarLayout()
        val stored = Settings.getToolbarDisplaySettings(this)
        check(stored.orderedItems.first().prefValue() == "custom_yes")
        check(OemImeSdk.unregisterCustomDrawer("yes"))
        check(CustomDrawerHostBridge.entries().none { it.id == "yes" })
        check(Settings.getToolbarDisplaySettings(this).visibleItems.none { it.prefValue() == "custom_yes" })
        check(Settings.getToolbarDisplaySettings(this).orderedItems.none { it.prefValue() == "custom_yes" })
        check(OemImeSdk.registerCustomDrawer(original) == OemRegisterResult.OK)
        toolbarLayout()
        report("registry_pass")
    }

    private fun showKeyboard() {
        editor.requestFocus()
        main.postDelayed({ getSystemService(InputMethodManager::class.java).showSoftInput(editor, InputMethodManager.SHOW_IMPLICIT) }, 500)
    }

    private fun report(event: String) {
        Log.i("EntryValidation", JSONObject().put("event", event).put("callbacks", callbacks)
            .put("factories", factories).put("drawerId", lastId).put("mainThread", onMain)
            .put("serviceContext", serviceContext).put("registered", OemImeSdk.customDrawers().size)
            .put("quickCalls", quickCalls).put("quickMain", quickMain).put("quickSource", quickSource)
            .put("quickHost", quickHost ?: JSONObject.NULL).put("modelCalls", modelCalls).put("editor", editor.text.toString()).toString())
    }

    companion object {
        const val SOURCE = "客户复制的测试原文：今天工作有点累。"
        var quickCalls = 0
        var quickMain = false
        var quickSource = false
        var quickHost: String? = null
        var modelCalls = 0
        var callbacks = 0
        var factories = 0
        var lastId = ""
        var onMain = false
        var serviceContext = false
    }
}
