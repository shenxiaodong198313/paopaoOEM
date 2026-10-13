package com.paopaoime.aikeyboard.ime.ui.fragment

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.paopaoime.aikeyboard.ime.R
import com.paopaoime.aikeyboard.oem.OemEnterpriseConfig
import com.paopaoime.aikeyboard.sdk.core.OemAddons
import com.paopaoime.aikeyboard.sdk.core.OemAutomationRuntimeDiagnosis
import com.paopaoime.aikeyboard.sdk.core.OemAutomationStartBlocker
import com.paopaoime.aikeyboard.sdk.core.OemAutomationStartResult
import com.paopaoime.aikeyboard.sdk.core.OemAutomationStopResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class InputMethodSettingsFragment : BaseInputMethodSettingsFragment(
    R.layout.fragment_input_method_settings
) {
    private companion object {
        const val STELLAR_ENTRY_TAP_TARGET = 10
        const val STELLAR_ENTRY_TAP_TIMEOUT_MS = 1_500L
    }

    private var automationToggleSwitch: SwitchCompat? = null
    private var automationToggleProgress: ProgressBar? = null
    private var automationSummaryView: TextView? = null
    private var automationFeedbackContainer: View? = null
    private var automationFeedbackTitle: TextView? = null
    private var automationFeedbackMessage: TextView? = null
    private var automationFeedbackPrimary: TextView? = null
    private var automationFeedbackSecondary: TextView? = null
    private var automationToggleSyncing = false
    private var automationToggleActionInFlight = false
    private var automationTogglePendingCheckedState: Boolean? = null
    private var automationFeedbackState: FeedbackState = FeedbackState.Hidden
    private var automationCardTapCount = 0
    private var automationCardLastTapAt = 0L

    private enum class FeedbackAction {
        OPEN_STARTER_GUIDE,
        OPEN_DEVELOPER_OPTIONS,
        OPEN_NOTIFICATION_SETTINGS,
        RETRY_ENABLE,
    }

    private sealed class FeedbackState {
        object Hidden : FeedbackState()

        data class Message(
            val title: String,
            val message: String,
        ) : FeedbackState()

        data class ActionRequired(
            val title: String,
            val message: String,
            val primaryAction: FeedbackAction,
            val secondaryAction: FeedbackAction? = null,
        ) : FeedbackState()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bindHeader(view, R.id.layout_ime_settings_header)
        val developerEntry = view.findViewById<View>(R.id.btn_ime_settings_developer)
        developerEntry.visibility = if (OemEnterpriseConfig.isDeveloperEntryEnabled(requireContext()) && OemAddons.hasDeveloper) {
            View.VISIBLE
        } else {
            View.GONE
        }
        applyCardShadow(
            view,
            intArrayOf(
                R.id.card_layout_display,
                R.id.card_key_effect,
                R.id.card_customize_toolbar,
                R.id.card_assist_input,
                R.id.card_clipboard,
                R.id.card_keyboard_manage,
                R.id.card_automation_settings,
            )
        )
        view.findViewById<View>(R.id.card_key_effect)?.setOnClickListener {
            openKeyEffectSettings()
        }
        view.findViewById<View>(R.id.card_layout_display)?.setOnClickListener {
            openLayoutDisplaySettings()
        }
        view.findViewById<View>(R.id.card_customize_toolbar)?.setOnClickListener {
            openCustomizeToolbarSettings()
        }
        view.findViewById<View>(R.id.card_assist_input)?.setOnClickListener {
            openAssistInputSettings()
        }
        view.findViewById<View>(R.id.card_clipboard)?.setOnClickListener {
            openClipboardSettings()
        }
        view.findViewById<View>(R.id.card_keyboard_manage)?.setOnClickListener {
            openKeyboardManageSettings()
        }
        view.findViewById<View>(R.id.card_voice_to_text).apply {
            visibility = if (org.fcitx.fcitx5.android.input.voice.OemVoiceBridge.enabled(requireContext())) View.VISIBLE else View.GONE
            setOnClickListener { org.fcitx.fcitx5.android.input.voice.OemVoiceBridge.settings(requireContext())?.let { openSettingsPage(it) } }
        }
        developerEntry.setOnClickListener {
            openDeveloperChannel()
        }
        if (setupAutomationCard(view)) {
            observeAutomationToggleState()
        }
    }

    private fun openLayoutDisplaySettings() {
        openSettingsPage(LayoutDisplaySettingsFragment())
    }

    private fun openKeyEffectSettings() {
        openSettingsPage(KeyEffectSettingsFragment())
    }

    private fun openCustomizeToolbarSettings() {
        openSettingsPage(CustomizeToolbarSettingsFragment())
    }

    private fun openAssistInputSettings() {
        openSettingsPage(AssistInputSettingsFragment())
    }

    private fun openClipboardSettings() {
        openSettingsPage(ClipboardSettingsFragment())
    }

    private fun openKeyboardManageSettings() {
        openSettingsPage(KeyboardManageSettingsFragment())
    }

    private fun openDeveloperChannel() {
        val ctx = context ?: return
        val intent = OemAddons.developer?.createDeveloperSettingsIntent(ctx) ?: return
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
        }
    }

    private fun setupAutomationCard(rootView: View): Boolean {
        val card = rootView.findViewById<View>(R.id.card_automation_settings)
        if (!OemAddons.hasAutomation || !OemEnterpriseConfig.isAutomationSettingsEntryEnabled(context)) {
            card?.visibility = View.GONE
            return false
        }
        val rowView = rootView.findViewById<View>(R.id.layout_automation_settings_row)
        automationToggleSwitch = rootView.findViewById(R.id.switch_automation_settings)
        automationToggleProgress = rootView.findViewById(R.id.progress_automation_settings)
        automationSummaryView = rootView.findViewById(R.id.tv_automation_settings_summary)
        automationFeedbackContainer = rootView.findViewById(R.id.layout_automation_settings_feedback)
        automationFeedbackTitle = rootView.findViewById(R.id.tv_automation_settings_feedback_title)
        automationFeedbackMessage = rootView.findViewById(R.id.tv_automation_settings_feedback_message)
        automationFeedbackPrimary = rootView.findViewById(R.id.btn_automation_settings_primary)
        automationFeedbackSecondary = rootView.findViewById(R.id.btn_automation_settings_secondary)

        automationToggleSwitch?.setOnCheckedChangeListener { _, isChecked ->
            if (automationToggleSyncing) {
                return@setOnCheckedChangeListener
            }
            handleAutomationToggleChanged(isChecked)
        }
        automationFeedbackPrimary?.setOnClickListener {
            performFeedbackAction((automationFeedbackState as? FeedbackState.ActionRequired)?.primaryAction)
        }
        automationFeedbackSecondary?.setOnClickListener {
            performFeedbackAction((automationFeedbackState as? FeedbackState.ActionRequired)?.secondaryAction)
        }
        rootView.findViewById<View>(R.id.card_automation_settings)?.setOnClickListener {
            handleAutomationCardTapped()
        }
        rowView?.setOnClickListener {
            handleAutomationCardTapped()
        }
        return true
    }

    private fun handleAutomationCardTapped() {
        if (consumeHiddenStellarEntryTap()) {
            return
        }
        if (automationToggleSwitch?.isEnabled == true) {
            automationToggleSwitch?.performClick()
        }
    }

    private fun consumeHiddenStellarEntryTap(): Boolean {
        val ctx = context ?: return false
        val now = SystemClock.elapsedRealtime()
        if (now - automationCardLastTapAt > STELLAR_ENTRY_TAP_TIMEOUT_MS) {
            automationCardTapCount = 0
        }
        automationCardLastTapAt = now
        automationCardTapCount += 1

        if (automationCardTapCount >= STELLAR_ENTRY_TAP_TARGET) {
            automationCardTapCount = 0
            openStellarManager()
            return true
        }

        val remaining = STELLAR_ENTRY_TAP_TARGET - automationCardTapCount
        if (remaining in 1..3) {
            Toast.makeText(
                ctx,
                getString(R.string.automation_hidden_entry_remaining, remaining),
                Toast.LENGTH_SHORT,
            ).show()
        }
        return false
    }

    private fun openStellarManager() {
        val ctx = context ?: return
        val intent = OemAddons.automation?.createStellarManagerIntent(ctx)
        if (intent == null) {
            Toast.makeText(ctx, R.string.automation_hidden_entry_open_failed, Toast.LENGTH_SHORT).show()
            return
        }
        try {
            startActivity(intent)
            Toast.makeText(ctx, R.string.automation_hidden_entry_opened, Toast.LENGTH_SHORT).show()
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(ctx, R.string.automation_hidden_entry_open_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleAutomationToggleChanged(isChecked: Boolean) {
        val context = context ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            automationToggleActionInFlight = true
            automationTogglePendingCheckedState = isChecked
            if (isChecked) {
                automationFeedbackState = FeedbackState.Hidden
            }
            applyAutomationToggleState(checked = isChecked, enabled = false)

            if (isChecked) {
                Toast.makeText(context, R.string.automation_toggle_starting, Toast.LENGTH_SHORT).show()
                when (val startResult = withContext(Dispatchers.IO) {
                    OemAddons.automation?.requestAutomationStart(context)
                        ?: OemAutomationStartResult.Failed("自动化引擎功能包未加载")
                }) {
                    is OemAutomationStartResult.Started,
                    is OemAutomationStartResult.AlreadyRunning -> {
                        Toast.makeText(context, R.string.automation_toggle_running, Toast.LENGTH_SHORT).show()
                        automationFeedbackState = FeedbackState.Hidden
                        OemAddons.automation?.markAutomationReadyConfirmed(context)
                    }
                    is OemAutomationStartResult.Unsupported -> {
                        Toast.makeText(context, R.string.automation_toggle_unsupported, Toast.LENGTH_SHORT).show()
                        automationFeedbackState = FeedbackState.Message(
                            title = getString(R.string.automation_toggle_unsupported_reason),
                            message = getString(R.string.automation_toggle_unsupported_hint),
                        )
                    }
                    is OemAutomationStartResult.NeedsUserAction -> {
                        automationFeedbackState = FeedbackState.ActionRequired(
                            title = startResult.reason,
                            message = startResult.hint,
                            primaryAction = FeedbackAction.OPEN_STARTER_GUIDE,
                            secondaryAction = FeedbackAction.OPEN_DEVELOPER_OPTIONS,
                        )
                    }
                    is OemAutomationStartResult.Failed -> {
                        Toast.makeText(context, R.string.automation_toggle_start_failed, Toast.LENGTH_SHORT).show()
                        automationFeedbackState = FeedbackState.ActionRequired(
                            title = startResult.reason,
                            message = startResult.hint ?: "",
                            primaryAction = FeedbackAction.OPEN_STARTER_GUIDE,
                            secondaryAction = FeedbackAction.OPEN_DEVELOPER_OPTIONS,
                        )
                    }
                }
            } else {
                Toast.makeText(context, R.string.automation_toggle_stopping, Toast.LENGTH_SHORT).show()
                when (val stopResult = withContext(Dispatchers.IO) {
                    OemAddons.automation?.requestAutomationStop(context)
                        ?: OemAutomationStopResult.Failed("自动化引擎功能包未加载")
                }) {
                    is OemAutomationStopResult.Stopped,
                    is OemAutomationStopResult.AlreadyStopped -> {
                        Toast.makeText(context, R.string.automation_toggle_stopped, Toast.LENGTH_SHORT).show()
                        automationFeedbackState = FeedbackState.Hidden
                        OemAddons.automation?.markAutomationManuallyStopped(context)
                    }
                    is OemAutomationStopResult.Failed -> {
                        Toast.makeText(context, R.string.automation_toggle_stop_failed, Toast.LENGTH_SHORT).show()
                        automationFeedbackState = FeedbackState.Message(
                            title = stopResult.reason,
                            message = stopResult.hint ?: "",
                        )
                    }
                }
            }

            automationToggleActionInFlight = false
            automationTogglePendingCheckedState = null
            refreshAutomationToggleState(force = true)
        }
    }

    private fun retryAutomationStartAfterManualEnable() {
        val toggle = automationToggleSwitch ?: return
        if (automationToggleActionInFlight) {
            return
        }
        automationToggleSyncing = true
        toggle.isChecked = true
        automationToggleSyncing = false
        handleAutomationToggleChanged(true)
    }

    private fun observeAutomationToggleState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (isActive) {
                    refreshAutomationToggleState(force = false)
                    delay(1500L)
                }
            }
        }
    }

    private suspend fun refreshAutomationToggleState(force: Boolean) {
        val context = context ?: return
        val diagnosis = withContext(Dispatchers.IO) {
            OemAddons.automation?.diagnoseAutomationRuntime(context)
                ?: OemAutomationRuntimeDiagnosis(
                    ready = false,
                    running = false,
                    authorized = false,
                    startInProgress = false,
                    hasNotificationPermission = false,
                    hasReachableAdbPort = false,
                    hasSavedAdbPortCandidate = false,
                    blocker = OemAutomationStartBlocker.NONE,
                    summary = "自动化引擎功能包未加载",
                )
        }
        val pendingCheckedState = automationTogglePendingCheckedState
        if (automationToggleActionInFlight) {
            val startFinished = pendingCheckedState == true &&
                (diagnosis.ready || diagnosis.blocker != OemAutomationStartBlocker.STARTING)
            val stopFinished = pendingCheckedState == false &&
                !diagnosis.running &&
                !diagnosis.startInProgress
            if (startFinished || stopFinished) {
                automationToggleActionInFlight = false
                automationTogglePendingCheckedState = null
                if (startFinished && diagnosis.ready) {
                    OemAddons.automation?.markAutomationReadyConfirmed(context)
                } else if (stopFinished) {
                    OemAddons.automation?.markAutomationManuallyStopped(context)
                }
            }
        }
        if (diagnosis.ready) {
            automationFeedbackState = FeedbackState.Hidden
        } else if (!automationToggleActionInFlight || force) {
            automationFeedbackState = buildFeedbackState(diagnosis)
        }
        automationSummaryView?.text = diagnosis.summary
        val checked = automationTogglePendingCheckedState ?: diagnosis.ready
        applyAutomationToggleState(checked = checked, enabled = !automationToggleActionInFlight)
    }

    private fun applyAutomationToggleState(checked: Boolean, enabled: Boolean) {
        val toggle = automationToggleSwitch ?: return
        val progress = automationToggleProgress
        val loading = automationToggleActionInFlight
        automationToggleSyncing = true
        toggle.isEnabled = enabled
        toggle.visibility = if (loading) View.INVISIBLE else View.VISIBLE
        progress?.visibility = if (loading) View.VISIBLE else View.GONE
        toggle.isChecked = checked
        automationToggleSyncing = false
        applyAutomationFeedback()
    }

    private fun applyAutomationFeedback() {
        val container = automationFeedbackContainer ?: return
        val titleView = automationFeedbackTitle ?: return
        val messageView = automationFeedbackMessage ?: return
        val primaryButton = automationFeedbackPrimary ?: return
        val secondaryButton = automationFeedbackSecondary ?: return
        when (val state = automationFeedbackState) {
            is FeedbackState.Hidden -> {
                container.visibility = View.GONE
            }
            is FeedbackState.Message -> {
                container.visibility = View.VISIBLE
                titleView.text = state.title
                messageView.text = state.message
                primaryButton.visibility = View.GONE
                secondaryButton.visibility = View.GONE
            }
            is FeedbackState.ActionRequired -> {
                container.visibility = View.VISIBLE
                titleView.text = state.title
                messageView.text = state.message
                primaryButton.visibility = View.VISIBLE
                primaryButton.text = feedbackActionLabel(state.primaryAction)
                if (state.secondaryAction != null) {
                    secondaryButton.visibility = View.VISIBLE
                    secondaryButton.text = feedbackActionLabel(state.secondaryAction)
                } else {
                    secondaryButton.visibility = View.GONE
                }
            }
        }
    }

    private fun buildFeedbackState(
        diagnosis: OemAutomationRuntimeDiagnosis,
    ): FeedbackState {
        return when (diagnosis.blocker) {
            OemAutomationStartBlocker.NONE,
            OemAutomationStartBlocker.STARTING -> FeedbackState.Hidden
            OemAutomationStartBlocker.NOTIFICATION_PERMISSION_REQUIRED -> {
                FeedbackState.ActionRequired(
                    title = getString(R.string.automation_toggle_notification_permission_missing_title),
                    message = getString(R.string.automation_toggle_notification_permission_missing_hint),
                    primaryAction = FeedbackAction.OPEN_NOTIFICATION_SETTINGS,
                    secondaryAction = FeedbackAction.OPEN_STARTER_GUIDE,
                )
            }
            OemAutomationStartBlocker.WIRELESS_DEBUGGING_REQUIRED -> {
                FeedbackState.ActionRequired(
                    title = getString(R.string.automation_toggle_no_port_detected),
                    message = getString(R.string.automation_toggle_enable_wireless_hint),
                    primaryAction = FeedbackAction.OPEN_STARTER_GUIDE,
                    secondaryAction = FeedbackAction.OPEN_DEVELOPER_OPTIONS,
                )
            }
            OemAutomationStartBlocker.ADB_PAIRING_REQUIRED -> {
                FeedbackState.ActionRequired(
                    title = getString(R.string.automation_toggle_pairing_required_title),
                    message = getString(R.string.automation_toggle_pairing_required_hint),
                    primaryAction = FeedbackAction.OPEN_STARTER_GUIDE,
                    secondaryAction = FeedbackAction.OPEN_DEVELOPER_OPTIONS,
                )
            }
            OemAutomationStartBlocker.AUTHORIZATION_REQUIRED -> {
                FeedbackState.Message(
                    title = getString(R.string.automation_toggle_authorization_required_title),
                    message = diagnosis.summary,
                )
            }
        }
    }

    private fun feedbackActionLabel(action: FeedbackAction): String {
        return when (action) {
            FeedbackAction.OPEN_STARTER_GUIDE -> getString(R.string.automation_toggle_open_starter_guide)
            FeedbackAction.OPEN_DEVELOPER_OPTIONS -> getString(R.string.automation_toggle_open_dev_options)
            FeedbackAction.OPEN_NOTIFICATION_SETTINGS -> getString(R.string.automation_toggle_open_notification_settings)
            FeedbackAction.RETRY_ENABLE -> getString(R.string.automation_toggle_continue_after_enable)
        }
    }

    private fun performFeedbackAction(action: FeedbackAction?) {
        when (action) {
            FeedbackAction.OPEN_STARTER_GUIDE -> openAutomationStarterGuide()
            FeedbackAction.OPEN_DEVELOPER_OPTIONS -> openDeveloperOptions()
            FeedbackAction.OPEN_NOTIFICATION_SETTINGS -> openNotificationSettings()
            FeedbackAction.RETRY_ENABLE -> retryAutomationStartAfterManualEnable()
            null -> Unit
        }
    }

    private fun openAutomationStarterGuide() {
        val ctx = context ?: return
        val hasSecureSettings = ContextCompat.checkSelfPermission(
            ctx,
            Manifest.permission.WRITE_SECURE_SETTINGS,
        ) == PackageManager.PERMISSION_GRANTED
        val intent = OemAddons.automation?.createAutomationStarterGuideIntent(ctx, hasSecureSettings)
        if (intent == null) {
            Toast.makeText(ctx, R.string.automation_toggle_open_starter_failed, Toast.LENGTH_SHORT).show()
            return
        }
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(ctx, R.string.automation_toggle_open_starter_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun openDeveloperOptions() {
        val ctx = context ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            Toast.makeText(ctx, R.string.automation_toggle_open_dev_options_failed, Toast.LENGTH_SHORT).show()
            return
        }
        try {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).apply {
                    putExtra(":settings:fragment_args_key", "toggle_adb_wireless")
                },
            )
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(ctx, R.string.automation_toggle_open_dev_options_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun openNotificationSettings() {
        val ctx = context ?: return
        try {
            startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                },
            )
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(ctx, R.string.automation_toggle_open_notification_settings_failed, Toast.LENGTH_SHORT).show()
        }
    }
}
