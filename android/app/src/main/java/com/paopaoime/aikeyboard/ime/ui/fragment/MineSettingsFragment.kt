package com.paopaoime.aikeyboard.ime.ui.fragment

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.paopaoime.aikeyboard.compat.agent.ui.HostNavigationControl
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

class MineSettingsFragment : Fragment() {
    private companion object {
        const val AUTOMATION_HIDDEN_ENTRY_TAP_THRESHOLD = 10
        const val AUTOMATION_HIDDEN_ENTRY_WINDOW_MS = 3_000L
    }

    private var automationToggleSwitch: SwitchCompat? = null
    private var automationToggleProgress: ProgressBar? = null
    private var automationToggleFeedbackContainer: View? = null
    private var automationToggleFeedbackTitle: TextView? = null
    private var automationToggleFeedbackMessage: TextView? = null
    private var automationToggleFeedbackPrimary: TextView? = null
    private var automationToggleFeedbackSecondary: TextView? = null
    private var automationToggleSyncing = false
    private var automationToggleActionInFlight = false
    private var automationTogglePendingCheckedState: Boolean? = null
    private var automationToggleFeedbackState: FeedbackState = FeedbackState.Hidden
    private var automationHiddenEntryTapCount = 0
    private var automationHiddenEntryFirstTapAt = 0L

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
            val message: String
        ) : FeedbackState()
        data class ActionRequired(
            val title: String,
            val message: String,
            val primaryAction: FeedbackAction,
            val secondaryAction: FeedbackAction? = null,
        ) : FeedbackState()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_mine_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as? HostNavigationControl)?.setBottomNavigationVisibility(false)

        val layoutHeader = view.findViewById<View>(R.id.layout_settings_header)
        val initialHeaderPaddingTop = layoutHeader.paddingTop
        ViewCompat.setOnApplyWindowInsetsListener(layoutHeader) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(top = initialHeaderPaddingTop + bars.top)
            insets
        }

        view.findViewById<View>(R.id.btn_settings_back)?.setOnClickListener {
            if (parentFragmentManager.backStackEntryCount > 0) {
                parentFragmentManager.popBackStack()
            } else {
                requireActivity().onBackPressedDispatcher.onBackPressed()
            }
        }

        setupMenuItem(view, R.id.item_permission_settings, R.drawable.ic_permission, "权限设置") {
            openPermissionSettings()
        }

        if (setupAutomationMenuItem(view)) {
            observeAutomationToggleState()
        }
        applyLegacyMenuVisibility(view)
    }

    private fun setupMenuItem(
        rootView: View,
        itemId: Int,
        iconRes: Int,
        text: String,
        listener: () -> Unit
    ) {
        val item = rootView.findViewById<View>(itemId) ?: return
        val iconView = item.findViewById<ImageView>(R.id.iv_icon)
        val titleView = item.findViewById<TextView>(R.id.tv_title)

        iconView?.setImageResource(iconRes)
        titleView?.text = text
        item.setOnClickListener { listener() }
    }

    private fun openPermissionSettings() {
        parentFragmentManager.beginTransaction()
            .hide(this)
            .add(R.id.fragment_container, PermissionSettingsFragment())
            .addToBackStack(null)
            .commit()
    }

    private fun setupAutomationMenuItem(rootView: View): Boolean {
        val item = rootView.findViewById<View>(R.id.item_shizuku_adb) ?: return false
        if (!OemAddons.hasAutomation || !OemEnterpriseConfig.isAutomationSettingsEntryEnabled(context)) {
            item.visibility = View.GONE
            return false
        }
        val rowView = item.findViewById<View>(R.id.layout_toggle_row)
        val iconView = item.findViewById<ImageView>(R.id.iv_icon)
        val titleView = item.findViewById<TextView>(R.id.tv_title)
        val toggleView = item.findViewById<SwitchCompat>(R.id.switch_action)
        val progressView = item.findViewById<ProgressBar>(R.id.progress_toggle_action)
        val feedbackContainer = item.findViewById<View>(R.id.layout_toggle_feedback)
        val feedbackTitle = item.findViewById<TextView>(R.id.tv_toggle_feedback_title)
        val feedbackMessage = item.findViewById<TextView>(R.id.tv_toggle_feedback_message)
        val feedbackPrimary = item.findViewById<TextView>(R.id.btn_toggle_feedback_primary)
        val feedbackSecondary = item.findViewById<TextView>(R.id.btn_toggle_feedback_secondary)

        iconView?.setImageResource(R.drawable.ic_remote_control)
        titleView?.text = "自动化设置"
        automationToggleSwitch = toggleView
        automationToggleProgress = progressView
        automationToggleFeedbackContainer = feedbackContainer
        automationToggleFeedbackTitle = feedbackTitle
        automationToggleFeedbackMessage = feedbackMessage
        automationToggleFeedbackPrimary = feedbackPrimary
        automationToggleFeedbackSecondary = feedbackSecondary

        toggleView?.setOnCheckedChangeListener { _, isChecked ->
            if (automationToggleSyncing) {
                return@setOnCheckedChangeListener
            }
            handleAutomationToggleChanged(isChecked)
        }

        feedbackPrimary?.setOnClickListener {
            performFeedbackAction((automationToggleFeedbackState as? FeedbackState.ActionRequired)?.primaryAction)
        }
        feedbackSecondary?.setOnClickListener {
            performFeedbackAction((automationToggleFeedbackState as? FeedbackState.ActionRequired)?.secondaryAction)
        }

        rowView?.setOnClickListener {
            handleAutomationRowClick()
        }
        return true
    }

    private fun handleAutomationRowClick() {
        val now = SystemClock.elapsedRealtime()
        if (now - automationHiddenEntryFirstTapAt > AUTOMATION_HIDDEN_ENTRY_WINDOW_MS) {
            automationHiddenEntryFirstTapAt = now
            automationHiddenEntryTapCount = 0
        }
        automationHiddenEntryTapCount += 1
        if (automationHiddenEntryTapCount >= AUTOMATION_HIDDEN_ENTRY_TAP_THRESHOLD) {
            automationHiddenEntryTapCount = 0
            automationHiddenEntryFirstTapAt = 0L
            openAutomationSettings()
        }
    }

    private fun openAutomationSettings() {
        val ctx = context ?: return
        val automation = OemAddons.automation
        if (automation == null) {
            Toast.makeText(ctx, R.string.automation_toggle_open_starter_failed, Toast.LENGTH_SHORT).show()
            return
        }
        runCatching {
            automation.launchAutomationSettings(ctx)
        }.onFailure {
            Toast.makeText(ctx, R.string.automation_toggle_open_starter_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleAutomationToggleChanged(isChecked: Boolean) {
        val context = context ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            automationToggleActionInFlight = true
            automationTogglePendingCheckedState = isChecked
            if (isChecked) {
                automationToggleFeedbackState = FeedbackState.Hidden
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
                        automationToggleFeedbackState = FeedbackState.Hidden
                        OemAddons.automation?.markAutomationReadyConfirmed(context)
                    }
                    is OemAutomationStartResult.Unsupported -> {
                        Toast.makeText(context, R.string.automation_toggle_unsupported, Toast.LENGTH_SHORT).show()
                        automationToggleFeedbackState = FeedbackState.Message(
                            title = getString(R.string.automation_toggle_unsupported_reason),
                            message = getString(R.string.automation_toggle_unsupported_hint)
                        )
                    }
                    is OemAutomationStartResult.NeedsUserAction -> {
                        automationToggleFeedbackState = FeedbackState.ActionRequired(
                            title = startResult.reason,
                            message = startResult.hint,
                            primaryAction = FeedbackAction.OPEN_STARTER_GUIDE,
                            secondaryAction = FeedbackAction.OPEN_DEVELOPER_OPTIONS,
                        )
                    }
                    is OemAutomationStartResult.Failed -> {
                        Toast.makeText(context, R.string.automation_toggle_start_failed, Toast.LENGTH_SHORT).show()
                        automationToggleFeedbackState = FeedbackState.ActionRequired(
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
                        automationToggleFeedbackState = FeedbackState.Hidden
                        OemAddons.automation?.markAutomationManuallyStopped(context)
                    }
                    is OemAutomationStopResult.Failed -> {
                        Toast.makeText(context, R.string.automation_toggle_stop_failed, Toast.LENGTH_SHORT).show()
                        automationToggleFeedbackState = FeedbackState.Message(
                            title = stopResult.reason,
                            message = stopResult.hint ?: ""
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
            automationToggleFeedbackState = FeedbackState.Hidden
        } else if (!automationToggleActionInFlight || force) {
            automationToggleFeedbackState = buildFeedbackState(diagnosis)
        }
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
        val container = automationToggleFeedbackContainer ?: return
        val titleView = automationToggleFeedbackTitle ?: return
        val messageView = automationToggleFeedbackMessage ?: return
        val primaryButton = automationToggleFeedbackPrimary ?: return
        val secondaryButton = automationToggleFeedbackSecondary ?: return

        when (val state = automationToggleFeedbackState) {
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
        diagnosis: OemAutomationRuntimeDiagnosis
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
                }
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
                }
            )
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(ctx, R.string.automation_toggle_open_notification_settings_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun applyLegacyMenuVisibility(rootView: View) {
        if (OemEnterpriseConfig.shouldExposeLegacyBusinessFeatures()) {
            setupMenuItem(rootView, R.id.item_knowledge_base, R.drawable.ic_knowledge, "知识库") {
                parentFragmentManager.beginTransaction()
                    .hide(this)
                    .add(R.id.fragment_container, ContentFragment())
                    .addToBackStack(null)
                    .commit()
            }
            return
        }
        rootView.findViewById<View>(R.id.item_knowledge_base)?.visibility = View.GONE
        rootView.findViewById<View>(R.id.item_invite_code)?.visibility = View.GONE
        rootView.findViewById<View>(R.id.list_group_2)?.visibility = View.GONE
    }

}
