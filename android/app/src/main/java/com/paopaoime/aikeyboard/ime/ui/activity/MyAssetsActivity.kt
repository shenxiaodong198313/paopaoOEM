package com.paopaoime.aikeyboard.ime.ui.activity

import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.paopaoime.aikeyboard.ime.R
import com.paopaoime.aikeyboard.ime.ai.store.LocalAsset
import com.paopaoime.aikeyboard.ime.ai.store.LocalAssetManager
import com.paopaoime.aikeyboard.ime.auth.AuthApiClient
import com.paopaoime.aikeyboard.ime.auth.AuthSessionStore
import com.paopaoime.aikeyboard.ime.worker.CreativeAssetCloudSync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale
import java.net.URL

class MyAssetsActivity : AppCompatActivity() {

    private data class UiAsset(
        val id: String,
        val type: Int,
        val prompt: String,
        val createTime: Long,
        val thumbUrl: String,
        val imageUrl: String,
        val videoUrl: String,
        val debugSource: String,
        val debugKey: String
    )

    private data class TokenState(
        var accessToken: String,
        var refreshToken: String
    )

    private data class CloudLoadResult(val items: List<UiAsset>)

    private lateinit var rvAssets: RecyclerView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var adapter: AssetAdapter
    private val allAssets = ArrayList<UiAsset>()
    private val displayedAssets = ArrayList<UiAsset>()
    
    private var currentTab = 0 // 0: All, 1: Images, 2: Videos
    
    private lateinit var tabAll: TextView
    private lateinit var tabImages: TextView
    private lateinit var tabVideos: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_my_assets)

        initViews()
        loadAssets()
    }

    private fun initViews() {
        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }

        tabAll = findViewById(R.id.tab_all)
        tabImages = findViewById(R.id.tab_images)
        tabVideos = findViewById(R.id.tab_videos)

        tabAll.setOnClickListener { switchTab(0) }
        tabImages.setOnClickListener { switchTab(1) }
        tabVideos.setOnClickListener { switchTab(2) }

        swipeRefresh = findViewById(R.id.swipe_refresh)
        swipeRefresh.setOnRefreshListener {
            loadAssets()
        }

        rvAssets = findViewById(R.id.rv_assets)
        rvAssets.layoutManager = GridLayoutManager(this, 2) // 2 columns
        adapter = AssetAdapter()
        rvAssets.adapter = adapter
    }

    private fun switchTab(index: Int) {
        if (currentTab == index) return
        currentTab = index
        updateTabUI()
        filterAssets()
    }

    private fun updateTabUI() {
        val activeColor = Color.parseColor("#333333")
        val inactiveColor = Color.parseColor("#999999")
        val activeBg = R.drawable.bg_card_white_12dp

        val tabs = listOf(tabAll, tabImages, tabVideos)
        
        tabs.forEachIndexed { i, tab ->
            if (i == currentTab) {
                tab.setTextColor(activeColor)
                tab.background = getDrawable(activeBg)
                tab.backgroundTintList = null
            } else {
                tab.setTextColor(inactiveColor)
                tab.background = null
            }
        }
    }

    private fun loadAssets() {
        swipeRefresh.isRefreshing = true
        CreativeAssetCloudSync.scheduleLegacyBackfill(this)
        CoroutineScope(Dispatchers.IO).launch {
            val localAssets = LocalAssetManager.getInstance(this@MyAssetsActivity).getAssets()
            val localUi = mapLocalAssets(localAssets)
            val cloudResult = runCatching { loadCloudAssets() }.getOrElse { CloudLoadResult(emptyList()) }
            val cloudUi = cloudResult.items
            allAssets.clear()
            allAssets.addAll(mergeAssets(localUi, cloudUi))
            withContext(Dispatchers.Main) {
                filterAssets()
                swipeRefresh.isRefreshing = false
            }
        }
    }

    private fun mapLocalAssets(assets: List<LocalAsset>): List<UiAsset> {
        val unique = LinkedHashMap<String, LocalAsset>()
        assets.sortedByDescending { it.createTime }.forEach { asset ->
            val key = "${asset.type}|${asset.localPath}|${asset.coverPath}"
            if (!unique.containsKey(key)) {
                unique[key] = asset
            }
        }
        return unique.values.map { asset ->
            val debugKey = "${asset.type}|${asset.localPath}|${asset.coverPath}"
            val thumbPath = if (asset.type == 1 && asset.coverPath.isNotBlank()) asset.coverPath else asset.localPath
            val imageUrl = if (asset.type == 1 && asset.coverPath.isNotBlank()) "file://${asset.coverPath}" else "file://${asset.localPath}"
            val videoUrl = if (asset.type == 1) "file://${asset.localPath}" else ""
            UiAsset(
                id = asset.id,
                type = asset.type,
                prompt = asset.prompt,
                createTime = asset.createTime,
                thumbUrl = "file://$thumbPath",
                imageUrl = imageUrl,
                videoUrl = videoUrl,
                debugSource = "local",
                debugKey = debugKey
            )
        }
    }

    private fun loadCloudAssets(): CloudLoadResult {
        val accessToken = AuthSessionStore.accessToken(this)
        val refreshToken = AuthSessionStore.refreshToken(this)
        if (accessToken.isBlank() || refreshToken.isBlank()) return CloudLoadResult(emptyList())
        if (!isNetworkAvailable()) return CloudLoadResult(emptyList())
        val tokenState = TokenState(accessToken, refreshToken)
        val listResult = AuthApiClient.listCreativeAssets(
            accessToken = tokenState.accessToken,
            refreshToken = tokenState.refreshToken,
            page = 1,
            pageSize = 100
        )
        if (!listResult.newAccessToken.isNullOrBlank() || !listResult.newRefreshToken.isNullOrBlank()) {
            AuthSessionStore.updateTokens(this, listResult.newAccessToken ?: tokenState.accessToken, listResult.newRefreshToken)
            tokenState.accessToken = listResult.newAccessToken ?: tokenState.accessToken
            tokenState.refreshToken = listResult.newRefreshToken ?: tokenState.refreshToken
        }
        if (!listResult.success || listResult.items.isEmpty()) {
            return CloudLoadResult(emptyList())
        }
        val sortedItems = listResult.items.sortedByDescending { it.createdAt }
        val uniqueItems = LinkedHashMap<String, AuthApiClient.AssetCreation>()
        sortedItems.forEach { item ->
            val uniqueKey = when {
                item.originalObjectKey.isNotBlank() -> "orig:${item.originalObjectKey}"
                item.clientAssetId.isNotBlank() -> "client:${item.clientAssetId}"
                else -> "asset:${item.assetId}"
            }
            if (!uniqueItems.containsKey(uniqueKey)) {
                uniqueItems[uniqueKey] = item
            }
        }
        val result = ArrayList<UiAsset>(uniqueItems.size)
        uniqueItems.values.forEach { item ->
            val debugKey = when {
                item.originalObjectKey.isNotBlank() -> "orig:${item.originalObjectKey}"
                item.clientAssetId.isNotBlank() -> "client:${item.clientAssetId}"
                else -> "asset:${item.assetId}"
            }
            val isVideo = item.assetType == "video"
            val type = if (isVideo) 1 else 0
            val imagePurpose = if (item.coverObjectKey.isNotBlank()) "cover" else "original"
            val imageSign = AuthApiClient.createCreativeDownloadSign(
                accessToken = tokenState.accessToken,
                refreshToken = tokenState.refreshToken,
                assetId = item.assetId,
                purpose = imagePurpose
            )
            if (!imageSign.newAccessToken.isNullOrBlank() || !imageSign.newRefreshToken.isNullOrBlank()) {
                AuthSessionStore.updateTokens(this, imageSign.newAccessToken ?: tokenState.accessToken, imageSign.newRefreshToken)
                tokenState.accessToken = imageSign.newAccessToken ?: tokenState.accessToken
                tokenState.refreshToken = imageSign.newRefreshToken ?: tokenState.refreshToken
            }
            val imageUrl = if (imageSign.success) imageSign.downloadUrl else ""
            var videoUrl = ""
            if (isVideo) {
                val videoSign = AuthApiClient.createCreativeDownloadSign(
                    accessToken = tokenState.accessToken,
                    refreshToken = tokenState.refreshToken,
                    assetId = item.assetId,
                    purpose = "original"
                )
                if (!videoSign.newAccessToken.isNullOrBlank() || !videoSign.newRefreshToken.isNullOrBlank()) {
                    AuthSessionStore.updateTokens(this, videoSign.newAccessToken ?: tokenState.accessToken, videoSign.newRefreshToken)
                    tokenState.accessToken = videoSign.newAccessToken ?: tokenState.accessToken
                    tokenState.refreshToken = videoSign.newRefreshToken ?: tokenState.refreshToken
                }
                if (videoSign.success) {
                    videoUrl = videoSign.downloadUrl
                }
            }
            if (imageUrl.isBlank() && videoUrl.isBlank()) return@forEach
            result.add(
                UiAsset(
                    id = item.clientAssetId.ifBlank { item.assetId },
                    type = type,
                    prompt = item.promptText.ifBlank { item.title },
                    createTime = parseServerTime(item.createdAt),
                    thumbUrl = imageUrl,
                    imageUrl = imageUrl,
                    videoUrl = videoUrl,
                    debugSource = "cloud",
                    debugKey = debugKey
                )
            )
        }
        return CloudLoadResult(result)
    }

    private fun parseServerTime(value: String): Long {
        val normalized = value.trim()
        if (normalized.isBlank()) return System.currentTimeMillis()
        val patterns = listOf(
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"
        )
        patterns.forEach { pattern ->
            runCatching {
                SimpleDateFormat(pattern, Locale.getDefault()).parse(normalized)?.time
            }.getOrNull()?.let { parsed ->
                return parsed
            }
        }
        return System.currentTimeMillis()
    }

    private fun isNetworkAvailable(): Boolean {
        val cm = getSystemService(CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun mergeAssets(localUi: List<UiAsset>, cloudUi: List<UiAsset>): List<UiAsset> {
        val merged = LinkedHashMap<String, UiAsset>()
        cloudUi.sortedByDescending { it.createTime }.forEach { asset ->
            merged[asset.id] = asset
        }
        localUi.sortedByDescending { it.createTime }.forEach { asset ->
            if (!merged.containsKey(asset.id)) {
                merged[asset.id] = asset
            }
        }
        return merged.values.toList().sortedByDescending { it.createTime }
    }

    private fun filterAssets() {
        displayedAssets.clear()
        when (currentTab) {
            0 -> displayedAssets.addAll(allAssets)
            1 -> displayedAssets.addAll(allAssets.filter { it.type == 0 })
            2 -> displayedAssets.addAll(allAssets.filter { it.type == 1 })
        }
        adapter.notifyDataSetChanged()
    }

    inner class AssetAdapter : RecyclerView.Adapter<AssetAdapter.ViewHolder>() {

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val ivThumb: ImageView = itemView.findViewById(R.id.iv_asset_thumb)
            val ivPlay: ImageView = itemView.findViewById(R.id.iv_play_icon)
            val tvType: TextView = itemView.findViewById(R.id.tv_asset_type)
            val tvPrompt: TextView = itemView.findViewById(R.id.tv_prompt)
            // val tvDate: TextView = itemView.findViewById(R.id.tv_asset_date)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_asset, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val asset = displayedAssets[position]
            loadThumb(holder.ivThumb, asset.thumbUrl)

            holder.ivPlay.isVisible = asset.type == 1
            holder.tvType.text = if (asset.type == 0) "图片" else "视频"
            holder.tvPrompt.text = asset.prompt.ifBlank { "未命名创作" }

            holder.itemView.setOnClickListener {
                openAsset(position)
            }
        }

        override fun getItemCount(): Int = displayedAssets.size
    }

    private fun openAsset(position: Int) {
        val imageUrls = ArrayList<String>()
        val videoUrls = ArrayList<String>()

        displayedAssets.forEach {
            if (it.type == 1) {
                imageUrls.add(it.imageUrl)
                videoUrls.add(it.videoUrl)
            } else {
                imageUrls.add(it.imageUrl)
                videoUrls.add("")
            }
        }

        val intent = Intent(this, ImageBrowserActivity::class.java).apply {
            putStringArrayListExtra("image_urls", imageUrls)
            putStringArrayListExtra("video_urls", videoUrls)
            putExtra("initial_pos", position)
        }
        startActivity(intent)
    }

    private fun loadThumb(imageView: ImageView, url: String) {
        if (url.startsWith("file://")) {
            imageView.setImageURI(Uri.parse(url))
            return
        }
        if (url.startsWith("/")) {
            imageView.setImageURI(Uri.fromFile(java.io.File(url)))
            return
        }
        CoroutineScope(Dispatchers.IO).launch {
            val bytes = runCatching { URL(url).openStream().use { it.readBytes() } }.getOrNull() ?: return@launch
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@launch
            withContext(Dispatchers.Main) {
                imageView.setImageBitmap(bitmap)
            }
        }
    }
}


