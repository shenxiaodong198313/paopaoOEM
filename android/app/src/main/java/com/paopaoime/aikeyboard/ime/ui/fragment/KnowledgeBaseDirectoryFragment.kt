package com.paopaoime.aikeyboard.ime.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.paopaoime.aikeyboard.compat.agent.ui.HostNavigationControl
import com.paopaoime.aikeyboard.ime.R
import com.paopaoime.aikeyboard.ime.data.theme.ThemeManager
import com.paopaoime.aikeyboard.ime.libs.recyclerview.SwipeMenu
import com.paopaoime.aikeyboard.ime.libs.recyclerview.SwipeMenuBridge
import com.paopaoime.aikeyboard.ime.libs.recyclerview.SwipeMenuItem
import com.paopaoime.aikeyboard.ime.libs.recyclerview.SwipeRecyclerView
import com.paopaoime.aikeyboard.ime.auth.AuthApiClient
import com.paopaoime.aikeyboard.ime.auth.AuthSessionStore
import com.paopaoime.aikeyboard.ime.ui.activity.FilePickBridge
import com.paopaoime.aikeyboard.ime.ui.activity.FilePickerActivity
import com.paopaoime.aikeyboard.ime.ui.knowledgebase.KnowledgeBaseUiStore
import com.paopaoime.aikeyboard.ime.ui.knowledgebase.model.KbDocumentUi
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class KnowledgeBaseDirectoryFragment : Fragment() {

    companion object {
        private const val ARG_DIRECTORY_ID = "directory_id"
        private const val KB_DOCS_TTL_MILLIS = 15 * 60 * 1000L

        fun newInstance(directoryId: String): KnowledgeBaseDirectoryFragment {
            val fragment = KnowledgeBaseDirectoryFragment()
            fragment.arguments = Bundle().apply {
                putString(ARG_DIRECTORY_ID, directoryId)
            }
            return fragment
        }
    }

    private var directoryId: String = ""
    private var adapter: KnowledgeBaseDocumentAdapter? = null
    private var documents: List<KbDocumentUi> = emptyList()
    private val handler = Handler(Looper.getMainLooper())
    private var autoRefreshRunnable: Runnable? = null
    private var cloudBacked = false
    private val remoteDocumentIds = linkedSetOf<String>()
    private val remoteDocuments = linkedMapOf<String, AuthApiClient.AssetKnowledgeDoc>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        directoryId = arguments?.getString(ARG_DIRECTORY_ID).orEmpty()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_kb_directory, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        (activity as? HostNavigationControl)?.setBottomNavigationVisibility(false)

        val tvTitle = view.findViewById<TextView>(R.id.tv_kb_dir_title)
        val btnBack = view.findViewById<ImageView>(R.id.btn_kb_dir_back)
        val btnAdd = view.findViewById<ImageView>(R.id.btn_kb_dir_add)
        val recyclerView = view.findViewById<SwipeRecyclerView>(R.id.rv_kb_documents)
        val tvEmpty = view.findViewById<TextView>(R.id.tv_kb_documents_empty)
        val tvSyncStatus = view.findViewById<TextView>(R.id.tv_kb_dir_sync_status)
        val layoutHeader = view.findViewById<View>(R.id.layout_kb_dir_header)

        // Apply Window Insets
        val initialHeaderPaddingTop = layoutHeader.paddingTop

        ViewCompat.setOnApplyWindowInsetsListener(layoutHeader) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(top = initialHeaderPaddingTop + bars.top)
            insets
        }


        tvTitle.text = KnowledgeBaseUiStore.getDirectory(directoryId)?.name ?: getString(R.string.kb_default_title)

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        adapter = KnowledgeBaseDocumentAdapter(
            onDocumentClick = { doc -> openDocument(doc) },
            onRetryVectorize = { doc ->
                if (remoteDocumentIds.contains(doc.id) && cloudBacked) {
                    syncDocumentStatusToServer(doc.id, "done", "pending")
                } else {
                    KnowledgeBaseUiStore.enqueueVectorize(doc.id)
                    renderDocuments()
                    startAutoRefreshIfNeeded()
                }
            }
        )
        recyclerView.setSwipeMenuCreator { _: SwipeMenu, rightMenu: SwipeMenu, position: Int ->
            val doc = documents.getOrNull(position) ?: return@setSwipeMenuCreator
            val deleteItem = SwipeMenuItem(requireContext()).apply {
                setImage(R.drawable.ic_menu_delete)
                image.setTint(ThemeManager.activeTheme.keyTextColor)
            }
            rightMenu.addMenuItem(deleteItem)
        }
        recyclerView.setOnItemMenuClickListener { menuBridge: SwipeMenuBridge, position: Int ->
            menuBridge.closeMenu()
            val doc = documents.getOrNull(position) ?: return@setOnItemMenuClickListener
            when (menuBridge.position) {
                0 -> {
                    if (cloudBacked && remoteDocumentIds.contains(doc.id) && AuthSessionStore.isLoggedIn(requireContext())) {
                        val accessToken = AuthSessionStore.accessToken(requireContext())
                        val refreshToken = AuthSessionStore.refreshToken(requireContext())
                        lifecycleScope.launch {
                            val result = withContext(Dispatchers.IO) {
                                AuthApiClient.deleteKnowledgeDoc(accessToken, refreshToken, doc.id)
                            }
                            if (!result.newAccessToken.isNullOrBlank()) {
                                AuthSessionStore.updateTokens(requireContext(), result.newAccessToken, result.newRefreshToken)
                            }
                            if (result.authInvalid) {
                                handleAuthInvalid()
                                return@launch
                            }
                            if (!result.success) {
                                renderSyncStatus("同步失败：${result.message}")
                                Toast.makeText(requireContext(), result.message, Toast.LENGTH_SHORT).show()
                                return@launch
                            }
                            KnowledgeBaseUiStore.deleteDocument(doc.id)
                            renderSyncStatus("已同步")
                            renderDocuments()
                        }
                    } else {
                        val ok = KnowledgeBaseUiStore.deleteDocument(doc.id)
                        if (!ok) {
                            Toast.makeText(requireContext(), getString(R.string.kb_delete_failed), Toast.LENGTH_SHORT).show()
                            return@setOnItemMenuClickListener
                        }
                        renderDocuments()
                    }
                }
            }
        }
        recyclerView.adapter = adapter

        btnBack.setOnClickListener { parentFragmentManager.popBackStack() }
        btnAdd.setOnClickListener {
            openFilePicker()
        }
        btnAdd.setOnLongClickListener {
            renderSyncStatus("手动同步中…")
            renderDocuments()
            true
        }

        renderDocuments()
        startAutoRefreshIfNeeded()
        updateEmptyState(tvEmpty, recyclerView)
        val expired = AuthSessionStore.isSyncExpired(requireContext(), syncScope(), KB_DOCS_TTL_MILLIS)
        if (expired) {
            tvSyncStatus.text = "缓存可能已过期，建议手动同步"
        }
    }

    override fun onResume() {
        super.onResume()
        renderDocuments()
        startAutoRefreshIfNeeded()
    }

    override fun onPause() {
        super.onPause()
        stopAutoRefresh()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stopAutoRefresh()
    }

    private fun renderDocuments() {
        val localDocs = KnowledgeBaseUiStore.listDocuments(directoryId)
        documents = localDocs
        remoteDocumentIds.clear()
        remoteDocuments.clear()
        adapter?.submitList(documents)
        view?.let { root ->
            val recyclerView = root.findViewById<SwipeRecyclerView>(R.id.rv_kb_documents)
            val tvEmpty = root.findViewById<TextView>(R.id.tv_kb_documents_empty)
            updateEmptyState(tvEmpty, recyclerView)
        }
        syncDocumentsFromServer(localDocs)
    }

    private fun startAutoRefreshIfNeeded() {
        stopAutoRefresh()
        if (!hasRunningParse(documents)) return
        autoRefreshRunnable = Runnable {
            if (!isAdded || view == null) return@Runnable
            renderDocuments()
            if (hasRunningParse(documents)) {
                handler.postDelayed(autoRefreshRunnable!!, 700L)
            } else {
                stopAutoRefresh()
            }
        }
        handler.postDelayed(autoRefreshRunnable!!, 700L)
    }

    private fun stopAutoRefresh() {
        autoRefreshRunnable?.let { handler.removeCallbacks(it) }
        autoRefreshRunnable = null
    }

    private fun hasRunningParse(list: List<KbDocumentUi>): Boolean {
        return list.any { it.parseStatus == 1 || it.parseStatus == 2 || it.vectorStatus == 1 }
    }

    private fun updateEmptyState(tvEmpty: TextView, recyclerView: SwipeRecyclerView) {
        val hasItems = (adapter?.itemCount ?: 0) > 0
        tvEmpty.visibility = if (hasItems) View.GONE else View.VISIBLE
        recyclerView.visibility = if (hasItems) View.VISIBLE else View.GONE
    }

    private fun openDocument(document: KbDocumentUi) {
        val remote = remoteDocuments[document.id]
        val fragment = KnowledgeBaseDocumentViewerFragment.newInstance(
            documentId = document.id,
            remoteObjectKey = remote?.objectKey.orEmpty(),
            remoteTitle = remote?.title.orEmpty(),
            remoteMime = remote?.mimeType.orEmpty()
        )
        parentFragmentManager.beginTransaction()
            .hide(this)
            .add(R.id.fragment_container, fragment)
            .addToBackStack(null)
            .commit()
    }

    private fun openFilePicker() {
        val oldPicked = FilePickBridge.onPicked
        val oldDismissed = FilePickBridge.onDismissed

        FilePickBridge.onPicked = { uri ->
            activity?.runOnUiThread {
                try {
                    val localDoc = KnowledgeBaseUiStore.addDocumentFromUri(directoryId, uri)
                    renderSyncStatus("同步中…")
                    renderDocuments()
                    startAutoRefreshIfNeeded()
                    if (AuthSessionStore.isLoggedIn(requireContext())) {
                        syncLocalDocumentToCloud(localDoc)
                    }
                } catch (t: Throwable) {
                    Toast.makeText(requireContext(), t.message ?: "添加失败", Toast.LENGTH_SHORT).show()
                } finally {
                    FilePickBridge.onPicked = oldPicked
                    FilePickBridge.onDismissed = oldDismissed
                }
            }
        }
        FilePickBridge.onDismissed = {
            activity?.runOnUiThread {
                FilePickBridge.onPicked = oldPicked
                FilePickBridge.onDismissed = oldDismissed
            }
        }

        try {
            val intent = Intent(requireContext(), FilePickerActivity::class.java)
            startActivity(intent)
        } catch (_: Throwable) {
            FilePickBridge.onPicked = oldPicked
            FilePickBridge.onDismissed = oldDismissed
            Toast.makeText(requireContext(), "无法打开文件选择器", Toast.LENGTH_SHORT).show()
        }
    }

    private fun syncDocumentsFromServer(localDocs: List<KbDocumentUi>) {
        if (!AuthSessionStore.isLoggedIn(requireContext())) {
            cloudBacked = false
            return
        }
        val accessToken = AuthSessionStore.accessToken(requireContext())
        val refreshToken = AuthSessionStore.refreshToken(requireContext())
        if (accessToken.isBlank()) {
            cloudBacked = false
            return
        }
        lifecycleScope.launch {
            renderSyncStatus("同步中…")
            val result = withContext(Dispatchers.IO) {
                AuthApiClient.listKnowledgeDocs(accessToken, refreshToken, directoryId)
            }
            if (!result.newAccessToken.isNullOrBlank()) {
                AuthSessionStore.updateTokens(requireContext(), result.newAccessToken, result.newRefreshToken)
            }
            if (result.authInvalid) {
                handleAuthInvalid()
                return@launch
            }
            if (!result.success) {
                renderSyncStatus("同步失败：${result.message}")
                return@launch
            }
            cloudBacked = true
            remoteDocumentIds.clear()
            remoteDocumentIds.addAll(result.items.map { it.id })
            remoteDocuments.clear()
            result.items.forEach { remoteDocuments[it.id] = it }
            val localMap = localDocs.associateBy { it.id }
            val merged = result.items.map { remote ->
                localMap[remote.id] ?: KbDocumentUi(
                    id = remote.id,
                    directoryId = remote.knowledgeBaseId.ifBlank { directoryId },
                    fileName = remote.title,
                    mimeType = remote.mimeType.ifBlank { "application/octet-stream" },
                    updatedAtMillis = 0L,
                    parseStatus = mapSplitStatus(remote.splitStatus),
                    parseProgress = if (remote.splitStatus == "running") 50 else 0,
                    parsedTextPath = null,
                    parseError = null,
                    vectorStatus = mapVectorStatus(remote.vectorStatus),
                    vectorError = null,
                    vectorUpdatedAtMillis = 0L
                )
            }
            documents = merged
            adapter?.submitList(merged)
            view?.let { root ->
                val recyclerView = root.findViewById<SwipeRecyclerView>(R.id.rv_kb_documents)
                val tvEmpty = root.findViewById<TextView>(R.id.tv_kb_documents_empty)
                updateEmptyState(tvEmpty, recyclerView)
            }
            AuthSessionStore.updateSyncAt(requireContext(), syncScope())
            renderSyncStatus("已同步")
            startAutoRefreshIfNeeded()
        }
    }

    private fun syncLocalDocumentToCloud(localDoc: KbDocumentUi) {
        val accessToken = AuthSessionStore.accessToken(requireContext())
        val refreshToken = AuthSessionStore.refreshToken(requireContext())
        if (accessToken.isBlank()) return
        lifecycleScope.launch {
            renderSyncStatus("同步中…")
            val localFile = resolveLocalDocumentFile(localDoc)
            if (!localFile.exists()) return@launch
            val signResult = withContext(Dispatchers.IO) {
                AuthApiClient.createOssUploadSign(
                    accessToken = accessToken,
                    refreshToken = refreshToken,
                    dir = "kb/attachments/",
                    fileName = localDoc.fileName,
                    contentType = localDoc.mimeType.ifBlank { "application/octet-stream" }
                )
            }
            var nextAccessToken = signResult.newAccessToken ?: accessToken
            var nextRefreshToken = signResult.newRefreshToken ?: refreshToken
            if (nextAccessToken != accessToken || nextRefreshToken != refreshToken) {
                AuthSessionStore.updateTokens(requireContext(), nextAccessToken, nextRefreshToken)
            }
            if (signResult.authInvalid) {
                handleAuthInvalid()
                return@launch
            }
            if (!signResult.success || signResult.uploadUrl.isBlank() || signResult.objectKey.isBlank()) {
                renderSyncStatus("同步失败：${signResult.message}")
                Toast.makeText(requireContext(), signResult.message, Toast.LENGTH_SHORT).show()
                return@launch
            }
            val uploadResult = withContext(Dispatchers.IO) {
                AuthApiClient.uploadFileBySignedUrl(
                    uploadUrl = signResult.uploadUrl,
                    file = localFile,
                    contentType = signResult.contentType.ifBlank { "application/octet-stream" }
                )
            }
            if (!uploadResult.success) {
                renderSyncStatus("同步失败：${uploadResult.message}")
                Toast.makeText(requireContext(), uploadResult.message, Toast.LENGTH_SHORT).show()
                return@launch
            }
            val createResult = withContext(Dispatchers.IO) {
                AuthApiClient.createKnowledgeDoc(
                    accessToken = nextAccessToken,
                    refreshToken = nextRefreshToken,
                    knowledgeBaseId = directoryId,
                    title = localDoc.fileName,
                    documentId = localDoc.id,
                    objectKey = signResult.objectKey,
                    fileSize = localFile.length(),
                    mimeType = localDoc.mimeType.ifBlank { signResult.contentType },
                    splitStatus = toRemoteSplitStatus(localDoc.parseStatus),
                    vectorStatus = toRemoteVectorStatus(localDoc.vectorStatus)
                )
            }
            nextAccessToken = createResult.newAccessToken ?: nextAccessToken
            nextRefreshToken = createResult.newRefreshToken ?: nextRefreshToken
            if (nextAccessToken != accessToken || nextRefreshToken != refreshToken) {
                AuthSessionStore.updateTokens(requireContext(), nextAccessToken, nextRefreshToken)
            }
            if (createResult.authInvalid) {
                handleAuthInvalid()
                return@launch
            }
            if (!createResult.success) {
                renderSyncStatus("同步失败：${createResult.message}")
                Toast.makeText(requireContext(), createResult.message, Toast.LENGTH_SHORT).show()
                return@launch
            }
            cloudBacked = true
            AuthSessionStore.updateSyncAt(requireContext(), syncScope())
            renderSyncStatus("已同步")
            renderDocuments()
        }
    }

    private fun syncDocumentStatusToServer(docId: String, splitStatus: String, vectorStatus: String) {
        if (!AuthSessionStore.isLoggedIn(requireContext())) return
        val accessToken = AuthSessionStore.accessToken(requireContext())
        val refreshToken = AuthSessionStore.refreshToken(requireContext())
        if (accessToken.isBlank()) return
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                AuthApiClient.updateKnowledgeDocStatus(accessToken, refreshToken, docId, splitStatus, vectorStatus)
            }
            if (!result.newAccessToken.isNullOrBlank()) {
                AuthSessionStore.updateTokens(requireContext(), result.newAccessToken, result.newRefreshToken)
            }
            if (result.authInvalid) {
                handleAuthInvalid()
                return@launch
            }
            if (!result.success) {
                renderSyncStatus("同步失败：${result.message}")
                return@launch
            }
            renderSyncStatus("已同步")
            renderDocuments()
        }
    }

    private fun mapSplitStatus(status: String): Int {
        return when (status.lowercase()) {
            "pending" -> 1
            "running" -> 2
            "done" -> 3
            "failed" -> 4
            else -> 0
        }
    }

    private fun mapVectorStatus(status: String): Int {
        return when (status.lowercase()) {
            "running" -> 1
            "done" -> 2
            "failed" -> 3
            else -> 0
        }
    }

    private fun toRemoteSplitStatus(parseStatus: Int): String {
        return when (parseStatus) {
            4 -> "failed"
            3 -> "done"
            2 -> "running"
            else -> "pending"
        }
    }

    private fun toRemoteVectorStatus(vectorStatus: Int): String {
        return when (vectorStatus) {
            3 -> "failed"
            2 -> "done"
            1 -> "running"
            else -> "pending"
        }
    }

    private fun resolveLocalDocumentFile(document: KbDocumentUi): File {
        val root = File(requireContext().filesDir, "knowledge_base")
        return File(File(root, document.directoryId), document.fileName)
    }

    private fun renderSyncStatus(text: String) {
        val root = view ?: return
        root.findViewById<TextView>(R.id.tv_kb_dir_sync_status).text = text
    }

    private fun handleAuthInvalid() {
        AuthSessionStore.handleOemAuthInvalid(requireContext(), "知识库")
        cloudBacked = false
        renderSyncStatus("离线缓存（非权威）")
    }

    private fun syncScope(): String = "kb_docs_$directoryId"
}

