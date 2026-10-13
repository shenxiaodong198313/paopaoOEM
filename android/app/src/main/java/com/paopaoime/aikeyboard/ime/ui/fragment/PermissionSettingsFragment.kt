package com.paopaoime.aikeyboard.ime.ui.fragment

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.SwitchCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import com.paopaoime.aikeyboard.compat.agent.ui.HostNavigationControl
import com.paopaoime.aikeyboard.ime.R
import com.paopaoime.aikeyboard.sdk.core.OemAddons
import com.paopaoime.aikeyboard.sdk.core.OemAutomationRuntimeSnapshot

class PermissionSettingsFragment : Fragment() {
    private var floatingStatusView: View? = null
    private var floatingActionView: View? = null
    private var locationStatusView: View? = null
    private var locationActionView: View? = null
    private var deviceInfoStatusView: View? = null
    private var deviceInfoActionView: View? = null
    private var notificationStatusView: View? = null
    private var notificationActionView: View? = null
    private var imageStatusView: View? = null
    private var imageActionView: View? = null
    private var microphoneStatusView: View? = null
    private var microphoneActionView: View? = null
    private var screencapStatusView: View? = null
    private var screencapActionView: View? = null
    private var shizukuStatusView: View? = null
    private var shizukuActionView: View? = null
    private var shizukuSummaryView: TextView? = null
    private var installedAppsSwitch: SwitchCompat? = null
    private var lastOverlayEnabled = false


    private val requestMicrophonePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val message = if (granted) "麦克风权限已开启" else "未获取麦克风权限"
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
            updateAllPermissionStates()
        }

    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val message = if (granted) "通知权限已开启" else "未获取通知权限"
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
            updateAllPermissionStates()
        }

    private val requestImagePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val message = if (granted) "图片与相册权限已开启" else "未获取图片与相册权限"
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
            updateAllPermissionStates()
        }

    private val requestLocationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            val granted = result.any { it.value }
            val message = if (granted) "位置信息权限已开启" else "未获取位置信息权限"
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
            updateAllPermissionStates()
        }

    private val requestDeviceInfoPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val message = if (granted) "设备信息权限已开启" else "未获取设备信息权限"
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
            updateAllPermissionStates()
        }

    private val requestScreenCaptureLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val granted = result.resultCode == Activity.RESULT_OK
            if (granted) {
                OemAddons.aiReply?.startScreenCaptureForegroundService(
                    requireContext(),
                    result.resultCode,
                    result.data,
                )
            } else {
                OemAddons.aiReply?.clearScreenCapturePermission(requireContext())
            }
            val message = if (granted) "截屏权限已开启" else "未获取截屏权限"
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
            
            // 延迟一点更新状态，等待服务启动并写入 hint
            view?.postDelayed({
                updateAllPermissionStates()
            }, 500)
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_permission_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as? HostNavigationControl)?.setBottomNavigationVisibility(false)

        val layoutHeader = view.findViewById<View>(R.id.layout_permission_header)
        val initialHeaderPaddingTop = layoutHeader.paddingTop
        ViewCompat.setOnApplyWindowInsetsListener(layoutHeader) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(top = initialHeaderPaddingTop + bars.top)
            insets
        }

        view.findViewById<View>(R.id.btn_permission_back)?.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        floatingStatusView = view.findViewById(R.id.img_permission_floating_status)
        floatingActionView = view.findViewById(R.id.btn_permission_floating_action)
        locationStatusView = view.findViewById(R.id.img_permission_location_status)
        locationActionView = view.findViewById(R.id.btn_permission_location_action)
        deviceInfoStatusView = view.findViewById(R.id.img_permission_deviceinfo_status)
        deviceInfoActionView = view.findViewById(R.id.btn_permission_deviceinfo_action)
        notificationStatusView = view.findViewById(R.id.img_permission_notification_status)
        notificationActionView = view.findViewById(R.id.btn_permission_notification_action)
        imageStatusView = view.findViewById(R.id.img_permission_image_status)
        imageActionView = view.findViewById(R.id.btn_permission_image_action)
        microphoneStatusView = view.findViewById(R.id.img_permission_microphone_status)
        microphoneActionView = view.findViewById(R.id.btn_permission_microphone_action)
        view.findViewById<View>(R.id.item_permission_microphone)?.visibility =
            if (org.fcitx.fcitx5.android.input.voice.OemVoiceBridge.enabled(requireContext())) View.VISIBLE else View.GONE
        screencapStatusView = view.findViewById(R.id.img_permission_screencap_status)
        screencapActionView = view.findViewById(R.id.btn_permission_screencap_action)
        shizukuStatusView = view.findViewById(R.id.img_permission_shizuku_status)
        shizukuActionView = view.findViewById(R.id.btn_permission_shizuku_action)
        shizukuSummaryView = view.findViewById(R.id.tv_permission_shizuku_summary)
        installedAppsSwitch = view.findViewById(R.id.switch_permission_installed_apps)

        installedAppsSwitch?.isChecked = OemAddons.automation?.isInstalledAppCatalogEnabled(requireContext()) ?: false
        installedAppsSwitch?.setOnCheckedChangeListener { _, isChecked ->
            OemAddons.automation?.setInstalledAppCatalogEnabled(requireContext(), isChecked)
            val message = if (isChecked) {
                "已允许读取应用列表"
            } else {
                "已关闭读取应用列表"
            }
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        }

        view.findViewById<View>(R.id.item_permission_floating)?.setOnClickListener {
            openOverlaySettings()
        }
        floatingActionView?.setOnClickListener {
            openOverlaySettings()
        }

        view.findViewById<View>(R.id.item_permission_location)?.setOnClickListener {
            handleLocationPermissionRequest()
        }
        locationActionView?.setOnClickListener {
            handleLocationPermissionRequest()
        }

        view.findViewById<View>(R.id.item_permission_deviceinfo)?.setOnClickListener {
            handleDeviceInfoPermissionRequest()
        }
        deviceInfoActionView?.setOnClickListener {
            handleDeviceInfoPermissionRequest()
        }

        view.findViewById<View>(R.id.item_permission_notification)?.setOnClickListener {
            handleNotificationPermissionRequest()
        }
        notificationActionView?.setOnClickListener {
            handleNotificationPermissionRequest()
        }

        view.findViewById<View>(R.id.item_permission_image)?.setOnClickListener {
            handleImagePermissionRequest()
        }
        imageActionView?.setOnClickListener {
            handleImagePermissionRequest()
        }

        view.findViewById<View>(R.id.item_permission_microphone)?.setOnClickListener {
            handleMicrophonePermissionRequest()
        }
        microphoneActionView?.setOnClickListener {
            handleMicrophonePermissionRequest()
        }

        view.findViewById<View>(R.id.item_permission_screencap)?.setOnClickListener {
            handleScreenCaptureRequest()
        }
        screencapActionView?.setOnClickListener {
            handleScreenCaptureRequest()
        }
        view.findViewById<View>(R.id.item_permission_shizuku)?.setOnClickListener {
            openAutomationSettings()
        }
        shizukuActionView?.setOnClickListener {
            openAutomationSettings()
        }

        lastOverlayEnabled = hasOverlayPermission()
        updateAllPermissionStates()
    }

    override fun onResume() {
        super.onResume()
        (activity as? HostNavigationControl)?.setBottomNavigationVisibility(false)
        val overlayEnabled = hasOverlayPermission()
        if (overlayEnabled != lastOverlayEnabled) {
            val message = if (overlayEnabled) "悬浮窗已开启" else "悬浮窗已关闭"
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
            lastOverlayEnabled = overlayEnabled
        }
        updateAllPermissionStates()
    }

    private fun hasOverlayPermission(): Boolean {
        val context = context ?: return false
        return Settings.canDrawOverlays(context)
    }

    private fun hasLocationPermission(): Boolean {
        val context = context ?: return false
        val fine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    private fun hasMicrophonePermission(): Boolean {
        val context = context ?: return false
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasDeviceInfoPermission(): Boolean {
        val context = context ?: return false
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasNotificationPermission(): Boolean {
        val context = context ?: return false
        return if (Build.VERSION.SDK_INT >= 33) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
    }

    private fun hasImagePermission(): Boolean {
        val context = context ?: return false
        val permission = if (Build.VERSION.SDK_INT >= 33) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return ContextCompat.checkSelfPermission(
            context,
            permission
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun updateAllPermissionStates() {
        updatePermissionRow(hasOverlayPermission(), floatingStatusView, floatingActionView)
        updatePermissionRow(hasLocationPermission(), locationStatusView, locationActionView)
        updatePermissionRow(hasDeviceInfoPermission(), deviceInfoStatusView, deviceInfoActionView)
        updatePermissionRow(hasNotificationPermission(), notificationStatusView, notificationActionView)
        updatePermissionRow(hasImagePermission(), imageStatusView, imageActionView)
        updatePermissionRow(hasMicrophonePermission(), microphoneStatusView, microphoneActionView)
        updatePermissionRow(
            OemAddons.aiReply?.isScreenCapturePermissionGranted(requireContext()) ?: false,
            screencapStatusView,
            screencapActionView,
        )
        updateAutomationState()
    }

    private fun updatePermissionRow(enabled: Boolean, statusView: View?, actionView: View?) {
        statusView?.visibility = if (enabled) View.VISIBLE else View.GONE
        actionView?.visibility = if (enabled) View.GONE else View.VISIBLE
    }

    private fun updateAutomationState() {
        val context = context ?: return
        val status = OemAddons.automation?.getAutomationRuntimeSnapshot(context)
            ?: OemAutomationRuntimeSnapshot(detailMessage = "自动化引擎功能包未加载")
        shizukuSummaryView?.text = status.detailMessage
        updatePermissionRow(status.ready, shizukuStatusView, shizukuActionView)
    }

    private fun openOverlaySettings() {
        val context = context ?: return
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
        }
    }

    private fun openAutomationSettings() {
        OemAddons.automation?.launchAutomationSettings(requireContext())
    }

    private fun handleLocationPermissionRequest() {
        if (hasLocationPermission()) {
            openAppDetailsSettings()
            return
        }
        requestLocationPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    private fun handleNotificationPermissionRequest() {
        if (hasNotificationPermission()) {
            openNotificationSettings()
            return
        }
        if (Build.VERSION.SDK_INT >= 33) {
            requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            openNotificationSettings()
        }
    }

    private fun handleDeviceInfoPermissionRequest() {
        if (hasDeviceInfoPermission()) {
            openAppDetailsSettings()
            return
        }
        requestDeviceInfoPermissionLauncher.launch(Manifest.permission.READ_PHONE_STATE)
    }

    private fun handleImagePermissionRequest() {
        if (hasImagePermission()) {
            openAppDetailsSettings()
            return
        }
        val permission = if (Build.VERSION.SDK_INT >= 33) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        requestImagePermissionLauncher.launch(permission)
    }

    private fun handleMicrophonePermissionRequest() {
        if (!org.fcitx.fcitx5.android.input.voice.OemVoiceBridge.enabled(requireContext())) return
        if (hasMicrophonePermission()) {
            openAppDetailsSettings()
            return
        }
        requestMicrophonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun handleScreenCaptureRequest() {
        val context = context ?: return
        val manager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
        val intent = manager?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                it.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay())
            } else {
                it.createScreenCaptureIntent()
            }
        }
        if (intent == null) {
            Toast.makeText(context, "无法打开截屏权限页面", Toast.LENGTH_SHORT).show()
            return
        }
        requestScreenCaptureLauncher.launch(intent)
    }

    private fun openAppDetailsSettings() {
        val context = context ?: return
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${context.packageName}")
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
        }
    }

    private fun openNotificationSettings() {
        val context = context ?: return
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            }
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        }
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
        }
    }
}
