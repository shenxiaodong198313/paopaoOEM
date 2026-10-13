package com.paopaoime.aikeyboard.ime.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.content.Intent
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.paopaoime.aikeyboard.compat.agent.ui.HostNavigationControl
import com.paopaoime.aikeyboard.ime.R
import com.paopaoime.aikeyboard.ime.auth.AuthApiClient
import com.paopaoime.aikeyboard.ime.auth.AuthSessionStore
import com.paopaoime.aikeyboard.oem.OemEnterpriseConfig
import com.paopaoime.aikeyboard.ime.ui.knowledgebase.KnowledgeBaseUiStore
import com.paopaoime.aikeyboard.ime.ui.knowledgebase.KnowledgeBaseStatsActivity
import com.paopaoime.aikeyboard.ime.ui.knowledgebase.model.KbDirectoryUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ContentFragment : Fragment() {
    companion object {
        const val ARG_IS_DETAIL = "is_detail"
        const val REQUEST_KEY_CREATE_DIR = "kb_create_dir"
        const val RESULT_KEY_CHANGED = "changed"
        private const val KB_DIRS_TTL_MILLIS = 15 * 60 * 1000L

        fun newInstance(isDetail: Boolean): ContentFragment {
            val fragment = ContentFragment()
            val args = Bundle()
            args.putBoolean(ARG_IS_DETAIL, isDetail)
            fragment.arguments = args
            return fragment
        }
    }

    private var adapter: KnowledgeBaseDirectoryAdapter? = null
    private var cloudBacked = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_content, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (!OemEnterpriseConfig.shouldExposeLegacyBusinessFeatures()) {
            Toast.makeText(requireContext(), R.string.oem_feature_disabled, Toast.LENGTH_SHORT).show()
            parentFragmentManager.popBackStack()
            return
        }
        (activity as? HostNavigationControl)?.setBottomNavigationVisibility(false)
        val recyclerView = view.findViewById<RecyclerView>(R.id.rv_kb_directories)
        val btnCreate = view.findViewById<View>(R.id.btn_kb_create)
        val btnData = view.findViewById<View>(R.id.btn_kb_data)
        val tvEmpty = view.findViewById<TextView>(R.id.tv_kb_empty)
        val tvSyncStatus = view.findViewById<TextView>(R.id.tv_kb_sync_status)
        val layoutHeader = view.findViewById<View>(R.id.layout_kb_header)

        // Apply Window Insets
        val initialHeaderPaddingTop = layoutHeader.paddingTop
        val initialRecyclerPaddingBottom = recyclerView.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(layoutHeader) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(top = initialHeaderPaddingTop + bars.top)
            insets
        }

        ViewCompat.setOnApplyWindowInsetsListener(recyclerView) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(bottom = initialRecyclerPaddingBottom + bars.bottom)
            insets
        }

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        adapter = KnowledgeBaseDirectoryAdapter(
            onDirectoryClick = { directory ->
                openDirectory(directory)
            }
        )
        recyclerView.adapter = adapter

        parentFragmentManager.setFragmentResultListener(REQUEST_KEY_CREATE_DIR, viewLifecycleOwner) { _, bundle ->
            val changed = bundle.getBoolean(RESULT_KEY_CHANGED, false)
            if (changed) {
                renderDirectories()
            }
        }

        btnCreate.setOnClickListener {
            val dialog = KnowledgeBaseCreateDirectoryDialogFragment()
            dialog.show(parentFragmentManager, "KnowledgeBaseCreateDirectoryDialogFragment")
        }
        btnData.setOnClickListener {
            val intent = Intent(requireContext(), KnowledgeBaseStatsActivity::class.java)
            startActivity(intent)
        }
        btnData.setOnLongClickListener {
            renderSyncStatus("手动同步中…")
            renderDirectories()
            true
        }

        renderDirectories()
        updateEmptyState(tvEmpty, recyclerView)
        if (AuthSessionStore.isSyncExpired(requireContext(), syncScope(), KB_DIRS_TTL_MILLIS)) {
            tvSyncStatus.text = "缓存可能已过期，建议手动同步"
        }
    }

    override fun onResume() {
        super.onResume()
        (activity as? HostNavigationControl)?.setBottomNavigationVisibility(false)
    }

    private fun renderDirectories() {
        val context = context ?: return
        val localDirectories = KnowledgeBaseUiStore.getDirectories()
        adapter?.submitList(localDirectories)
        val root = view ?: return
        val recyclerView = root.findViewById<RecyclerView>(R.id.rv_kb_directories)
        val tvEmpty = root.findViewById<TextView>(R.id.tv_kb_empty)
        updateEmptyState(tvEmpty, recyclerView)
        if (!AuthSessionStore.isLoggedIn(context)) {
            cloudBacked = false
            renderSyncStatus("离线缓存（非权威）")
            return
        }
        val accessToken = AuthSessionStore.accessToken(context)
        val refreshToken = AuthSessionStore.refreshToken(context)
        if (accessToken.isBlank()) {
            cloudBacked = false
            renderSyncStatus("离线缓存（非权威）")
            return
        }
        lifecycleScope.launch {
            renderSyncStatus("同步中…")
            val result = withContext(Dispatchers.IO) {
                AuthApiClient.listKnowledgeBases(accessToken, refreshToken)
            }
            var nextAccessToken = result.newAccessToken ?: accessToken
            var nextRefreshToken = result.newRefreshToken ?: refreshToken
            if (nextAccessToken != accessToken || nextRefreshToken != refreshToken) {
                AuthSessionStore.updateTokens(context, nextAccessToken, nextRefreshToken)
            }
            if (result.authInvalid) {
                handleAuthInvalid(context)
                return@launch
            }
            if (!result.success) {
                renderSyncStatus("同步失败：${result.message}")
                return@launch
            }
            cloudBacked = true
            val merged = result.items.map {
                KbDirectoryUi(
                    id = it.id,
                    name = it.name,
                    createdAtMillis = 0L,
                    updatedAtMillis = 0L,
                    deletable = true,
                    vectorStoreSize = 0L
                )
            }
            adapter?.submitList(merged)
            updateEmptyState(tvEmpty, recyclerView)
            AuthSessionStore.updateSyncAt(context, syncScope())
            renderSyncStatus("已同步")
        }
    }

    private fun updateEmptyState(tvEmpty: TextView, recyclerView: RecyclerView) {
        val hasItems = (adapter?.itemCount ?: 0) > 0
        tvEmpty.visibility = if (hasItems) View.GONE else View.VISIBLE
        recyclerView.visibility = if (hasItems) View.VISIBLE else View.GONE
    }

    private fun openDirectory(directory: KbDirectoryUi) {
        val fragment = KnowledgeBaseDirectoryFragment.newInstance(directoryId = directory.id)
        parentFragmentManager.beginTransaction()
            .hide(this)
            .add(R.id.fragment_container, fragment)
            .addToBackStack(null)
            .commit()
    }

    private fun renderSyncStatus(text: String) {
        val root = view ?: return
        root.findViewById<TextView>(R.id.tv_kb_sync_status).text = text
    }

    private fun handleAuthInvalid(context: android.content.Context) {
        AuthSessionStore.handleOemAuthInvalid(context, "知识库")
        cloudBacked = false
        renderSyncStatus("离线缓存（非权威）")
    }

    private fun syncScope(): String = "kb_dirs"
}
