package com.paopaoime.aikeyboard.ime.ui.fragment

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.AppCompatRadioButton
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.paopaoime.aikeyboard.ime.R
import com.paopaoime.aikeyboard.ime.ui.settings.InputMethodSettingsBridge
import com.paopaoime.aikeyboard.ime.ui.settings.PinyinDictionaryCatalog
import com.paopaoime.aikeyboard.ime.ui.settings.PinyinDictionaryPreparationRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.fcitx.fcitx5.android.input.status.PinyinDictionaryPackages

class DictionaryManagementFragment : BaseInputMethodSettingsFragment(R.layout.fragment_dictionary_management) {
    private data class Row(val radio: AppCompatRadioButton, val download: AppCompatButton)

    private val rows = mutableMapOf<String, Row>()
    private var operation: Job? = null
    private var request: PinyinDictionaryPackages.Request? = null
    private var installed = emptySet<String>()
    private var selected: String? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bindHeader(view, R.id.dictionary_header, R.id.dictionary_back)
        val list = view.findViewById<LinearLayout>(R.id.dictionary_options)
        for (option in PinyinDictionaryCatalog.options) {
            val card = layoutInflater.inflate(R.layout.item_oem_dictionary, list, false)
            val radio = card.findViewById<AppCompatRadioButton>(R.id.dictionary_option)
            val download = card.findViewById<AppCompatButton>(R.id.dictionary_download)
            radio.text = option.title
            radio.supportButtonTintList = ColorStateList.valueOf(ContextCompat.getColor(card.context, R.color.text_cyan))
            radio.setOnClickListener {
                renderRows(busy = false)
                if (option.id != selected && option.id in installed) startOperation(view, option.id, false)
            }
            card.findViewById<TextView>(R.id.dictionary_description).text = option.description
            download.contentDescription = getString(R.string.oem_dictionary_download_named, option.title)
            download.setOnClickListener { startOperation(view, option.id, true) }
            list.addView(card)
            rows[option.id] = Row(radio, download)
        }
        view.findViewById<View>(R.id.dictionary_cancel).setOnClickListener {
            request?.cancel()
            operation?.cancel()
        }
        renderRows(busy = true)
        viewLifecycleOwner.lifecycleScope.launch {
            refreshState()
            renderRows(busy = false)
        }
    }

    private suspend fun refreshState() {
        val state = withContext(Dispatchers.IO) {
            val available = PinyinDictionaryCatalog.options.map { it.id }.filter(PinyinDictionaryPackages::ready).toSet()
            available to InputMethodSettingsBridge.getPinyinDictionaryProfile()
        }
        installed = state.first
        selected = state.second
    }

    private fun renderRows(busy: Boolean) {
        for ((profile, row) in rows) {
            row.radio.isChecked = profile == selected
            row.radio.isEnabled = !busy && profile in installed
            row.download.isVisible = profile !in installed && profile != "frost"
            row.download.isEnabled = !busy
        }
    }

    private fun startOperation(root: View, profile: String, download: Boolean) {
        if (operation?.isActive == true) return
        val active = if (download) PinyinDictionaryPackages.Request() else null
        request = active
        val progress = root.findViewById<ProgressBar>(R.id.dictionary_progress)
        val status = root.findViewById<TextView>(R.id.dictionary_status)
        val cancel = root.findViewById<View>(R.id.dictionary_cancel)
        val scope = viewLifecycleOwner.lifecycleScope
        renderRows(busy = true)
        status.setText(if (download) R.string.oem_dictionary_downloading else R.string.oem_dictionary_preparing)
        status.setTextColor(Color.DKGRAY)
        progress.isVisible = true
        progress.isIndeterminate = !download
        progress.progress = 0
        cancel.isVisible = true
        operation = scope.launch {
            try {
                if (active != null) {
                    PinyinDictionaryPreparationRepository.prepare("frost")
                    PinyinDictionaryPackages.install(profile, active, { percent, preparing ->
                        scope.launch {
                            if (request === active && !active.cancelled) {
                                progress.isIndeterminate = preparing
                                progress.progress = percent
                                status.text = if (preparing) getString(R.string.oem_dictionary_preparing)
                                    else getString(R.string.oem_dictionary_progress, percent)
                            }
                        }
                    }) { PinyinDictionaryPreparationRepository.prepare(profile) }
                    status.setText(R.string.oem_dictionary_ready)
                } else {
                    InputMethodSettingsBridge.setPinyinDictionaryProfile(profile)
                    status.setText(R.string.oem_dictionary_selected)
                }
                refreshState()
            } catch (cancelled: CancellationException) {
                if (view === root) status.setText(R.string.oem_dictionary_cancelled)
                throw cancelled
            } catch (failure: Exception) {
                status.text = if (active?.cancelled == true) getString(R.string.oem_dictionary_cancelled)
                    else failure.message ?: getString(R.string.oem_dictionary_failed)
            } finally {
                if (view === root) {
                    request = null
                    progress.isVisible = false
                    cancel.isVisible = false
                    renderRows(busy = false)
                }
            }
        }
    }

    override fun onDestroyView() {
        request?.cancel()
        operation?.cancel()
        request = null
        operation = null
        rows.clear()
        super.onDestroyView()
    }
}
