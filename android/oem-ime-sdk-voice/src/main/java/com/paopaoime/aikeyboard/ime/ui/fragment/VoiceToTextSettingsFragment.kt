package com.paopaoime.aikeyboard.ime.ui.fragment

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.paopaoime.aikeyboard.addon.voice.R
import com.paopaoime.aikeyboard.ime.voice.*
import kotlinx.coroutines.launch

class VoiceToTextSettingsFragment : androidx.fragment.app.Fragment(R.layout.fragment_voice_to_text_settings) {
    private val repository get() = VoiceRepository.get(requireContext())
    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted && !shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) {
            AlertDialog.Builder(requireContext()).setTitle("麦克风权限未开启").setMessage("请在系统权限设置中允许此输入法使用麦克风。")
                .setPositiveButton("去设置") { _, _ -> startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${requireContext().packageName}"))) }
                .setNegativeButton("取消", null).show()
        }
        render()
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (!org.fcitx.fcitx5.android.input.voice.OemVoiceConfig.get(requireContext()).enabled) { requireActivity().onBackPressedDispatcher.onBackPressed(); return }
        val header = view.findViewById<View>(R.id.layout_voice_to_text_header)
        val top = header.paddingTop
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(header) { v, insets ->
            v.setPadding(v.paddingLeft, top + insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars()).top, v.paddingRight, v.paddingBottom)
            insets
        }
        view.findViewById<View>(R.id.btn_voice_to_text_back).setOnClickListener {
            if (parentFragmentManager.backStackEntryCount > 0) parentFragmentManager.popBackStack()
            else requireActivity().onBackPressedDispatcher.onBackPressed()
        }
        val group = view.findViewById<RadioGroup>(R.id.voice_input_modes)
        modes.forEach { (id, mode) ->
            view.findViewById<RadioButton>(id).text = "${mode.title} · 约 ${(VoiceModels.bytes(mode) + 999_999) / 1_000_000} MB\n${mode.description}"
        }
        group.check(modes.entries.first { it.value == repository.mode }.key)
        group.setOnCheckedChangeListener { _, id ->
            val next = modes[id] ?: return@setOnCheckedChangeListener
            if (next != repository.mode) { repository.mode = next; VoiceRuntime.release("mode-change") }
            render()
        }
        view.findViewById<View>(R.id.voice_input_download).setOnClickListener { if (repository.download.value.running) repository.pause() else repository.startDownload() }
        view.findViewById<View>(R.id.voice_input_permission).setOnClickListener { if (!allowed()) permission.launch(Manifest.permission.RECORD_AUDIO) }
        viewLifecycleOwner.lifecycleScope.launch { viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) { repository.download.collect { render() } } }
        render()
    }
    override fun onResume() { super.onResume(); render() }
    private fun allowed() = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    private fun render() {
        val root = view ?: return
        val state = repository.download.value; val ready = repository.ready()
        val size = VoiceModels.bytes(repository.mode); val downloaded = if (state.running) state.downloaded else repository.downloaded()
        val progress = root.findViewById<ProgressBar>(R.id.voice_input_progress)
        progress.progress = (downloaded * 1000 / size).toInt(); progress.visibility = if (downloaded > 0 && !ready) View.VISIBLE else View.GONE
        root.findViewById<TextView>(R.id.voice_input_download_status).text = when {
            ready -> "已下载，可离线使用"
            state.running -> "${state.message} · ${downloaded * 100 / size}%"
            downloaded > 0 -> if (state.message.isBlank()) "已下载 ${downloaded / 1_000_000} MB，继续下载后即可使用" else "${state.message} · 已下载 ${downloaded / 1_000_000} MB"
            state.message.isNotBlank() -> state.message
            else -> "需下载约 ${(size + 999_999) / 1_000_000} MB"
        }
        root.findViewById<Button>(R.id.voice_input_download).apply {
            text = when { state.running -> "暂停下载"; ready -> "语音包已下载"; downloaded > 0 -> "继续下载"; else -> "下载语音包" }
            isEnabled = state.running || !ready
        }
        modes.keys.forEach { root.findViewById<View>(it).isEnabled = !state.running }
        root.findViewById<TextView>(R.id.voice_input_permission).apply { text = if (allowed()) "麦克风权限已开启" else "允许使用麦克风"; isEnabled = !allowed() }
    }
    private val modes = linkedMapOf(
        R.id.voice_mode_performance to VoiceMode.PERFORMANCE,
        R.id.voice_mode_balanced to VoiceMode.BALANCED,
        R.id.voice_mode_quality to VoiceMode.QUALITY
    )
}
