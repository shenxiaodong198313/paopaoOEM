package com.paopaoime.aikeyboard.ime.ui.fragment

import android.os.Bundle
import android.view.View
import com.paopaoime.aikeyboard.ime.R

class DeveloperSettingsFragment : BaseInputMethodSettingsFragment(
    R.layout.fragment_developer_settings
) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bindHeader(view, R.id.layout_developer_header, R.id.btn_developer_back)
        applyCardShadow(
            view,
            intArrayOf(
                R.id.card_developer_intro,
                R.id.card_developer_tools,
            )
        )
        view.findViewById<View>(R.id.item_developer_session_archive)?.setOnClickListener {
            openSettingsPage(DeveloperSessionArchiveFragment())
        }
        view.findViewById<View>(R.id.item_developer_material_callback)?.setOnClickListener {
            openSettingsPage(DeveloperMaterialCallbackFragment())
        }
        view.findViewById<View>(R.id.item_developer_ai_model_config)?.setOnClickListener {
            openSettingsPage(DeveloperAiModelConfigFragment())
        }
        view.findViewById<View>(R.id.item_developer_ai_reply_config)?.setOnClickListener {
            openSettingsPage(DeveloperAiReplyConfigFragment())
        }
    }
}
