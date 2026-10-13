package com.paopaoime.aikeyboard.ime.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.content.Intent
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.textfield.TextInputEditText
import com.paopaoime.aikeyboard.ime.R
import com.paopaoime.aikeyboard.ime.auth.AuthApiClient
import com.paopaoime.aikeyboard.ime.auth.AuthSessionStore
import com.paopaoime.aikeyboard.ime.ui.knowledgebase.KnowledgeBaseUiStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class KnowledgeBaseCreateDirectoryDialogFragment : BottomSheetDialogFragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.dialog_kb_create_directory, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val btnCancel = view.findViewById<TextView>(R.id.btn_kb_create_cancel)
        val btnCreate = view.findViewById<TextView>(R.id.btn_kb_create_confirm)
        val etName = view.findViewById<TextInputEditText>(R.id.et_kb_dir_name)
        val etDesc = view.findViewById<TextInputEditText>(R.id.et_kb_dir_desc)

        fun updateCreateEnabled() {
            val enabled = !etName.text.isNullOrBlank()
            btnCreate.isEnabled = enabled
            btnCreate.alpha = if (enabled) 1f else 0.4f
        }

        btnCancel.setOnClickListener { dismiss() }
        btnCreate.setOnClickListener {
            val name = etName.text?.toString()?.trim().orEmpty()
            if (name.isEmpty()) return@setOnClickListener
            val context = context ?: return@setOnClickListener
            if (!AuthSessionStore.isLoggedIn(context)) {
                KnowledgeBaseUiStore.createDirectory(name)
                parentFragmentManager.setFragmentResult(
                    ContentFragment.REQUEST_KEY_CREATE_DIR,
                    bundleOf(ContentFragment.RESULT_KEY_CHANGED to true)
                )
                dismiss()
                return@setOnClickListener
            }
            val accessToken = AuthSessionStore.accessToken(context)
            val refreshToken = AuthSessionStore.refreshToken(context)
            if (accessToken.isBlank()) {
                KnowledgeBaseUiStore.createDirectory(name)
                parentFragmentManager.setFragmentResult(
                    ContentFragment.REQUEST_KEY_CREATE_DIR,
                    bundleOf(ContentFragment.RESULT_KEY_CHANGED to true)
                )
                dismiss()
                return@setOnClickListener
            }
            btnCreate.isEnabled = false
            lifecycleScope.launch {
                val result = withContext(Dispatchers.IO) {
                    AuthApiClient.createKnowledgeBase(
                        accessToken = accessToken,
                        refreshToken = refreshToken,
                        name = name
                    )
                }
                if (!result.newAccessToken.isNullOrBlank()) {
                    AuthSessionStore.updateTokens(context, result.newAccessToken, result.newRefreshToken)
                }
                if (result.authInvalid) {
                    AuthSessionStore.handleOemAuthInvalid(context, "知识库")
                    dismiss()
                    return@launch
                }
                btnCreate.isEnabled = true
                if (!result.success) {
                    Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
                    return@launch
                }
                parentFragmentManager.setFragmentResult(
                    ContentFragment.REQUEST_KEY_CREATE_DIR,
                    bundleOf(ContentFragment.RESULT_KEY_CHANGED to true)
                )
                dismiss()
            }
        }

        etName.addTextChangedListener { updateCreateEnabled() }
        updateCreateEnabled()
    }
}

