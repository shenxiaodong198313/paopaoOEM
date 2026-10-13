package com.paopaoime.captureregression

import android.app.*
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.view.View
import android.view.WindowManager.LayoutParams
import com.paopaoime.aikeyboard.materialhub.MaterialHubActivity
import com.paopaoime.aikeyboard.materialhub.AiCommunicateReplyService
import com.paopaoime.aikeyboard.materialhub.MaterialHubLaunchAuthorizer
import com.paopaoime.aikeyboard.sdk.core.*
import kotlinx.coroutines.*
import java.io.File
import java.lang.reflect.InvocationTargetException
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn

/** Runs against the R8-built SDK consumed from local-sdk/repo, without a JUnit dependency. */
class CaptureInstrumentation : Instrumentation() {
    private val passed = mutableListOf<String>()
    private var panelStops = 0
    private var chatStops = 0
    private var initialClickable = false
    private var neonVisibleDuringTaskPause = false
    private lateinit var chat: ChatActivity
    private lateinit var panel: MaterialHubActivity

    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }
    override fun onStart() {
        val result = Bundle()
        try {
            runBlocking { runChecks() }
            result.putString("stream", "\nPASS: ${passed.joinToString(", ")}\n")
            finish(Activity.RESULT_OK, result)
        } catch (t: Throwable) {
            result.putString("stream", "\nFAIL after ${passed.joinToString()}: ${t.stackTraceToString()}\n")
            finish(Activity.RESULT_CANCELED, result)
        }
    }

    private suspend fun invokeSuspend(name: String, vararg args: Any?): Any? =
        suspendCoroutineUninterceptedOrReturn { continuation ->
            val method = MaterialHubActivity::class.java.declaredMethods.single {
                it.name == name && it.parameterCount == args.size + 1
            }.apply { isAccessible = true }
            try { method.invoke(panel, *args, continuation) }
            catch (e: InvocationTargetException) { throw e.targetException }
        }

    private fun field(name: String): Any? = MaterialHubActivity::class.java.getDeclaredField(name).run {
        isAccessible = true; get(panel)
    }
    private fun call(name: String) = MaterialHubActivity::class.java.getDeclaredMethod(name).run {
        isAccessible = true; invoke(panel)
    }
    private fun assertRestored() {
        val flags = panel.window.attributes.flags
        check(flags and (LayoutParams.FLAG_NOT_FOCUSABLE or LayoutParams.FLAG_NOT_TOUCHABLE) == 0) { "Window left non-interactive: $flags" }
        val drawer = field("drawerHost") as View
        check(drawer.alpha == 1f && drawer.translationY == 0f) { "Drawer still hidden" }
        check(drawer.isClickable == initialClickable) { "Drawer click state not restored" }
    }
    private suspend fun transaction(block: suspend () -> Any?): Any? =
        invokeSuspend("withAiCommunicateDrawerHidden", block)

    private suspend fun runChecks() {
        OemLog.setEnabled(true)
        OemLog.setLogcatEnabled(true)
        val app = targetContext.applicationContext as Application
        val callbacks = object : Application.ActivityLifecycleCallbacks {
            override fun onActivityStopped(a: Activity) { if (a === panelOrNull()) panelStops++; if (::chat.isInitialized && a === chat) chatStops++ }
            override fun onActivityCreated(a: Activity, b: Bundle?) {}
            override fun onActivityStarted(a: Activity) {}
            override fun onActivityResumed(a: Activity) {}
            override fun onActivityPaused(a: Activity) {
                if (a === panelOrNull() && !a.isFinishing) {
                    neonVisibleDuringTaskPause = neonVisibleDuringTaskPause ||
                        (field("aiCommunicateNeonOverlay") as View).visibility == View.VISIBLE
                }
            }
            override fun onActivitySaveInstanceState(a: Activity, b: Bundle) {}
            override fun onActivityDestroyed(a: Activity) {}
        }
        app.registerActivityLifecycleCallbacks(callbacks)
        chat = startActivitySync(Intent(targetContext, ChatActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as ChatActivity
        val token = withContext(Dispatchers.Main) { MaterialHubLaunchAuthorizer.issue() }
        panel = startActivitySync(Intent(targetContext, MaterialHubActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
            .putExtra("material_hub_launch_token", token)
            .putExtra("material_hub_entry_mode", "ai_communicate")
            .putExtra("material_hub_host_package_name", targetContext.packageName)) as MaterialHubActivity
        delay(1000)
        check(!panel.isFinishing)
        initialClickable = withContext(Dispatchers.Main) { (field("drawerHost") as View).isClickable }
        val task = panel.taskId
        try {
            withContext(Dispatchers.Main) {
                assertRestored()
                val ok = transaction {
                    check(panel.window.attributes.flags and LayoutParams.FLAG_NOT_TOUCHABLE != 0)
                    check((field("drawerHost") as View).alpha == 0f)
                    "captured"
                }
                check(ok == "captured"); assertRestored(); passed += "normal window restoration"

                try { transaction { error("injected capture failure") }; error("Failure was swallowed") }
                catch (e: IllegalStateException) { check(e.message == "injected capture failure") }
                assertRestored(); passed += "exception restoration"

                val early = withTimeoutOrNull(60) { transaction { error("Should cancel during preparation") } }
                check(early == null); assertRestored(); passed += "cancel during 180ms preparation"

                invokeSuspend("animateDrawerRetractionForPreparation")
                val late = withTimeoutOrNull(300) { transaction { delay(5000) } }
                check(late == null); assertRestored(); passed += "timeout restores retracted drawer"

                call("showAiCommunicateNeonOverlay")
                val hidden: suspend () -> Any? = {
                    check((field("aiCommunicateNeonOverlay") as View).visibility == View.INVISIBLE)
                    delay(5000)
                }
                check(withTimeoutOrNull(100) { invokeSuspend("withAiCommunicateNeonSuppressed", hidden) } == null)
                check((field("aiCommunicateNeonOverlay") as View).visibility == View.VISIBLE)
                passed += "cancel restores neon"
            }
            withContext(Dispatchers.Main) {
                val controller = field("aiCommunicateController")
                val capture: suspend () -> Any? = { delay(5000) }
                check(withTimeoutOrNull(250) {
                    transaction { invokeSuspend("withAiCommunicateCaptureTaskHidden", capture) }
                } == null)
                assertRestored()
                check(field("aiCommunicateController") === controller)
                check(panel.taskId == task && !panel.isFinishing)
            }
            passed += "cancel after task background restores same session"
            OemImeSdk.setScreenshotProvider(OemScreenshotProvider { null })
            withContext(Dispatchers.Main) {
                try {
                    invokeSuspend("captureFastLlmBatchBehindDrawer", 3, 3, null)
                    error("Missing capture failure")
                } catch (expected: AiCommunicateReplyService.UserVisibleFailure) {
                    assertRestored()
                }
            }
            passed += "empty first capture"
            OemImeSdk.setScreenshotProvider(OemScreenshotProvider { error("injected provider failure") })
            withContext(Dispatchers.Main) {
                try {
                    invokeSuspend("captureFastLlmBatchBehindDrawer", 3, 3, null)
                    error("Missing capture failure")
                } catch (expected: AiCommunicateReplyService.UserVisibleFailure) {
                    assertRestored()
                }
            }
            passed += "host provider failure"

            var screenshots = 0
            var swipes = 0
            OemImeSdk.setScreenshotProvider(OemScreenshotProvider {
                check(panel.taskId == task && !panel.isFinishing) { "SDK recreated its task during capture" }
                val bitmap = uiAutomation.takeScreenshot() ?: error("Device screenshot empty")
                if (screenshots++ == 0) {
                    val greenSamples = (1..8).sumOf { x -> (2..5).count { y ->
                        val color = bitmap.getPixel(bitmap.width * x / 10, bitmap.height * y / 10)
                        android.graphics.Color.green(color) > android.graphics.Color.red(color) + 10
                    } }
                    check(greenSamples >= 10) { "Chat surface absent from screenshot: $greenSamples" }
                    File(targetContext.filesDir, "capture.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
                }
                bitmap
            })
            OemImeSdk.setSwipeHostDelegate(OemSwipeHostDelegate { request, callback ->
                Thread {
                    swipes++
                    val cmd = "input swipe ${request.startX} ${request.startY} ${request.endX} ${request.endY} ${request.durationMs}"
                    ParcelFileDescriptor.AutoCloseInputStream(uiAutomation.executeShellCommand(cmd)).use { it.readBytes() }
                    callback.onResult(OemSwipeResult(request.requestId, true))
                }.start()
                true
            })
            val oldScroll = withContext(Dispatchers.Main) { chat.scroll.scrollY }
            withContext(Dispatchers.Main) {
                invokeSuspend("animateDrawerRetractionForPreparation")
                val batch = withTimeout(25000) { invokeSuspend("captureFastLlmBatchBehindDrawer", 3, 3, null) }
                check(batch != null); assertRestored()
                check(chat.scroll.scrollY < oldScroll) { "Injected gesture did not reach chat" }
            }
            check(screenshots >= 2 && swipes >= 1)
            check(panel.taskId == task && !panel.isFinishing && chatStops == 0) { "Chat stopped or panel recreated: panel=$panelStops chat=$chatStops" }
            passed += "multi-capture and real gesture with same panel task and live chat"
            check(!neonVisibleDuringTaskPause) { "Local neon was carried into task exit transition" }
            passed += "local neon disappears before task exit"
            File(targetContext.filesDir, "result.txt").writeText(passed.joinToString("\n"))
        } finally {
            OemImeSdk.setScreenshotProvider(null)
            OemImeSdk.setSwipeHostDelegate(null)
            app.unregisterActivityLifecycleCallbacks(callbacks)
            withContext(NonCancellable + Dispatchers.Main) { panel.finish(); chat.finish() }
        }
    }
    private fun panelOrNull(): Activity? = if (::panel.isInitialized) panel else null
}
