package com.paopaoime.aikeyboard.ime.ui.fragment

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.paopaoime.aikeyboard.ime.R
import com.paopaoime.aikeyboard.oem.OemEnterpriseConfig
import com.paopaoime.aikeyboard.oem.debug.OemDeveloperSessionArchiveRecord
import com.paopaoime.aikeyboard.oem.debug.OemDeveloperSessionArchiveStore
import com.paopaoime.aikeyboard.sdk.core.OemSessionArchiveMode
import java.text.DateFormat
import java.util.Date

class DeveloperSessionArchiveFragment : BaseInputMethodSettingsFragment(
    R.layout.fragment_developer_session_archive
) {
    private var introView: TextView? = null
    private var summaryView: TextView? = null
    private var listContainer: LinearLayout? = null
    private var emptyView: TextView? = null
    private var clearButton: TextView? = null
    private val modeButtons = linkedMapOf<OemSessionArchiveMode, TextView>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bindHeader(view, R.id.layout_developer_archive_header, R.id.btn_developer_archive_back)
        applyCardShadow(
            view,
            intArrayOf(
                R.id.card_developer_archive_intro,
                R.id.card_developer_archive_summary,
                R.id.card_developer_archive_list,
            )
        )
        introView = view.findViewById(R.id.tv_developer_archive_intro)
        summaryView = view.findViewById(R.id.tv_developer_archive_summary)
        listContainer = view.findViewById(R.id.layout_developer_archive_list)
        emptyView = view.findViewById(R.id.tv_developer_archive_empty)
        bindModeButtons(view)
        clearButton = view.findViewById<TextView>(R.id.btn_developer_archive_clear)?.apply {
            setOnClickListener {
                val context = context ?: return@setOnClickListener
                OemDeveloperSessionArchiveStore.clear(context)
                render()
                Toast.makeText(context, R.string.developer_archive_clear_done, Toast.LENGTH_SHORT).show()
            }
        }
        render()
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        val context = context ?: return
        val mode = OemEnterpriseConfig.resolveSessionArchiveMode(context)
        val snapshot = OemDeveloperSessionArchiveStore.load(context)
        updateModeButtons(mode)
        introView?.text = getString(
            R.string.developer_archive_intro_template,
            mode.name,
            modeDescription(mode),
        )
        summaryView?.text = if (snapshot == null) {
            getString(
                R.string.developer_archive_summary_empty,
                mode.name,
                modeDescription(mode),
            )
        } else {
            getString(
                R.string.developer_archive_summary_template,
                mode.name,
                modeDescription(mode),
                snapshot.totalCount,
                snapshot.records.size,
            )
        }
        clearButton?.visibility = if (snapshot == null) View.GONE else View.VISIBLE
        listContainer?.removeAllViews()
        val records = snapshot?.records.orEmpty()
        emptyView?.visibility = if (records.isEmpty()) View.VISIBLE else View.GONE
        records.forEachIndexed { index, record ->
            listContainer?.addView(createArchiveItemView(index, record))
        }
    }

    private fun bindModeButtons(root: View) {
        val cloudOnly = root.findViewById<TextView>(R.id.btn_mode_cloud_only)
        val hostOnly = root.findViewById<TextView>(R.id.btn_mode_host_only)
        val dualWrite = root.findViewById<TextView>(R.id.btn_mode_dual_write)
        val localOnly = root.findViewById<TextView>(R.id.btn_mode_local_only)
        modeButtons.clear()
        modeButtons[OemSessionArchiveMode.CLOUD_ONLY] = cloudOnly
        modeButtons[OemSessionArchiveMode.HOST_ONLY] = hostOnly
        modeButtons[OemSessionArchiveMode.DUAL_WRITE] = dualWrite
        modeButtons[OemSessionArchiveMode.LOCAL_ONLY] = localOnly
        modeButtons.forEach { (mode, button) ->
            button.setOnClickListener {
                val context = context ?: return@setOnClickListener
                OemEnterpriseConfig.setDeveloperSessionArchiveModeOverride(context, mode)
                Toast.makeText(
                    context,
                    getString(R.string.developer_archive_mode_switch_done, mode.name),
                    Toast.LENGTH_SHORT
                ).show()
                render()
            }
        }
    }

    private fun updateModeButtons(selectedMode: OemSessionArchiveMode) {
        val context = context ?: return
        modeButtons.forEach { (mode, button) ->
            val isSelected = mode == selectedMode
            button.setBackgroundResource(
                if (isSelected) {
                    R.drawable.bg_developer_mode_chip_selected
                } else {
                    R.drawable.bg_developer_mode_chip_unselected
                }
            )
            button.setTextColor(
                ContextCompat.getColor(
                    context,
                    if (isSelected) android.R.color.white else android.R.color.black
                )
            )
        }
    }

    private fun createArchiveItemView(index: Int, record: OemDeveloperSessionArchiveRecord): TextView {
        val formattedTime = DateFormat.getDateTimeInstance().format(Date(record.timestamp))
        val body = getString(
            R.string.developer_archive_item_body_template,
            record.eventId.ifBlank { getString(R.string.none_label) },
            record.sessionId.ifBlank { getString(R.string.none_label) },
            record.hostApplicationId.ifBlank { getString(R.string.none_label) },
            record.targetPackageName.ifBlank { getString(R.string.none_label) },
            record.targetAppName.ifBlank { getString(R.string.none_label) },
            record.inputSource.ifBlank { getString(R.string.none_label) },
            record.policyState.ifBlank { getString(R.string.none_label) },
            formattedTime,
            record.maskedContent.ifBlank { getString(R.string.none_label) },
        )
        return TextView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).also { params ->
                if (index > 0) {
                    params.topMargin = dp(12)
                }
            }
            setBackgroundResource(R.drawable.bg_card_white_12dp)
            setPadding(dp(14), dp(14), dp(14), dp(14))
            setLineSpacing(0f, 1.25f)
            textSize = 13f
            setTextColor(requireContext().getColor(android.R.color.black))
            text = getString(R.string.developer_archive_item_title_template, index + 1) + "\n" + body
        }
    }

    private fun modeDescription(mode: OemSessionArchiveMode): String {
        val resId = when (mode) {
            OemSessionArchiveMode.CLOUD_ONLY -> R.string.developer_archive_mode_cloud_only
            OemSessionArchiveMode.HOST_ONLY -> R.string.developer_archive_mode_host_only
            OemSessionArchiveMode.DUAL_WRITE -> R.string.developer_archive_mode_dual_write
            OemSessionArchiveMode.LOCAL_ONLY -> R.string.developer_archive_mode_local_only
        }
        return getString(resId)
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
