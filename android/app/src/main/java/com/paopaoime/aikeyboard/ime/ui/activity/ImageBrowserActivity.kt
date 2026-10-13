package com.paopaoime.aikeyboard.ime.ui.activity

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.MediaController
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.paopaoime.aikeyboard.ime.R
import com.paopaoime.aikeyboard.ime.ai.liblib.LiblibClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream

class ImageBrowserActivity : AppCompatActivity() {

    private val images = ArrayList<String>()
    private val videos = ArrayList<String>()
    private var accessKey: String = ""
    private var secretKey: String = ""
    private lateinit var client: LiblibClient
    
    private lateinit var viewPager: ViewPager2
    private lateinit var thumbnailsList: RecyclerView
    private lateinit var pageIndicator: TextView
    
    private lateinit var pagerAdapter: ImagePagerAdapter
    private lateinit var thumbAdapter: ImageThumbnailAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_browser)

        images.addAll(intent.getStringArrayListExtra("image_urls") ?: emptyList())
        videos.addAll(intent.getStringArrayListExtra("video_urls") ?: emptyList())
        accessKey = intent.getStringExtra("access_key") ?: ""
        secretKey = intent.getStringExtra("secret_key") ?: ""
        
        if (images.isEmpty()) {
            Toast.makeText(this, getString(R.string.no_images), Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // AK/SK might be empty for local assets
        client = LiblibClient(accessKey, secretKey)

        initViews()
        setupClickListeners()
    }

    private fun initViews() {
        viewPager = findViewById(R.id.view_pager_images)
        thumbnailsList = findViewById(R.id.recycler_thumbnails)
        pageIndicator = findViewById(R.id.tv_page_indicator)

        pagerAdapter = ImagePagerAdapter()
        viewPager.adapter = pagerAdapter
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateIndicator(position)
                thumbAdapter.setSelected(position)
                thumbnailsList.smoothScrollToPosition(position)
            }
        })

        thumbAdapter = ImageThumbnailAdapter { pos ->
            viewPager.currentItem = pos
        }
        thumbnailsList.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        thumbnailsList.adapter = thumbAdapter
        
        val initialPos = intent.getIntExtra("initial_pos", 0)
        if (initialPos > 0 && initialPos < images.size) {
            viewPager.setCurrentItem(initialPos, false)
            updateIndicator(initialPos)
        } else {
            updateIndicator(0)
        }
    }

    private fun updateIndicator(position: Int) {
        pageIndicator.text = "${position + 1}/${images.size}"
        if (images.size <= 1) {
            pageIndicator.visibility = View.GONE
            thumbnailsList.visibility = View.GONE
        } else {
            pageIndicator.visibility = View.VISIBLE
            thumbnailsList.visibility = View.VISIBLE
        }
    }

    private fun setupClickListeners() {
        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }
        
        findViewById<View>(R.id.btn_download).setOnClickListener { showDownloadDialog() }
        findViewById<View>(R.id.btn_share).setOnClickListener { showShareDialog() }
        findViewById<View>(R.id.btn_more).setOnClickListener { 
             val popup = android.widget.PopupMenu(this, it)
             popup.menu.add(getString(R.string.delete))
             popup.setOnMenuItemClickListener { item ->
                 if (item.title == getString(R.string.delete)) {
                     deleteCurrentImage()
                 }
                 true
             }
             popup.show()
        }
    }

    private fun playVideoInApp(videoUrl: String) {
        val uri = when {
            videoUrl.startsWith("file://") -> android.net.Uri.parse(videoUrl)
            videoUrl.startsWith("/") -> android.net.Uri.fromFile(File(videoUrl))
            else -> android.net.Uri.parse(videoUrl)
        }

        val dialog = android.app.Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val root = FrameLayout(this)

        val videoView = VideoView(this).apply {
            setVideoURI(uri)
        }

        val mediaController = MediaController(this)
        mediaController.setAnchorView(videoView)
        videoView.setMediaController(mediaController)

        val closeBtn = ImageView(this).apply {
            setImageResource(R.drawable.ic_menu_close)
            setColorFilter(android.graphics.Color.WHITE)
            background = android.graphics.drawable.ShapeDrawable(android.graphics.drawable.shapes.OvalShape()).apply {
                paint.color = android.graphics.Color.parseColor("#80000000")
            }
            val padding = (12 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
            setOnClickListener { dialog.dismiss() }
        }

        root.addView(
            videoView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        root.addView(
            closeBtn,
            FrameLayout.LayoutParams(
                (44 * resources.displayMetrics.density).toInt(),
                (44 * resources.displayMetrics.density).toInt()
            ).apply {
                gravity = android.view.Gravity.TOP or android.view.Gravity.END
                val margin = (16 * resources.displayMetrics.density).toInt()
                topMargin = margin
                marginEnd = margin
            }
        )

        dialog.setContentView(root)
        dialog.setOnDismissListener {
            videoView.stopPlayback()
        }
        dialog.show()

        videoView.setOnPreparedListener {
            mediaController.show(0)
            videoView.start()
        }
        videoView.setOnErrorListener { _, _, _ ->
            Toast.makeText(this, getString(R.string.video_play_error), Toast.LENGTH_SHORT).show()
            dialog.dismiss()
            true
        }
    }
    
    private fun deleteCurrentImage() {
        val currentPos = viewPager.currentItem
        if (currentPos >= 0 && currentPos < images.size) {
            images.removeAt(currentPos)
            pagerAdapter.notifyItemRemoved(currentPos)
            thumbAdapter.notifyDataSetChanged()
            
            if (images.isEmpty()) {
                finish()
            } else {
                updateIndicator(viewPager.currentItem)
            }
        }
    }

    private fun showShareDialog() {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.layout_dialog_share, null)
        dialog.setContentView(view)
        
        view.findViewById<View>(R.id.btn_share_wechat).setOnClickListener {
            Toast.makeText(this, getString(R.string.sharing_wechat), Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }
        
        view.findViewById<View>(R.id.btn_cancel).setOnClickListener {
            dialog.dismiss()
        }
        
        dialog.show()
    }

    private fun showDownloadDialog() {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.layout_dialog_download, null)
        dialog.setContentView(view)
        
        view.findViewById<View>(R.id.btn_close_download).setOnClickListener {
            dialog.dismiss()
        }
        
        view.findViewById<View>(R.id.btn_download_png).setOnClickListener {
            downloadCurrentImage()
            dialog.dismiss()
        }
        
        val switchWatermark = view.findViewById<Switch>(R.id.switch_watermark)
        switchWatermark.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                switchWatermark.isChecked = false
                Toast.makeText(this, "VIP功能暂未开放", Toast.LENGTH_SHORT).show()
            }
        }
        
        dialog.show()
    }

    private fun downloadCurrentImage() {
        val pos = viewPager.currentItem
        if (pos < 0 || pos >= images.size) return
        val url = images[pos]
        
        Toast.makeText(this, "开始下载...", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val bytes = if (url.startsWith("/") || url.startsWith("file://")) {
                    runCatching { java.io.File(url.removePrefix("file://")).readBytes() }.getOrNull()
                } else {
                    runCatching { client.downloadBytes(url) }.getOrNull()
                } ?: throw RuntimeException("读取图片失败")
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                if (bitmap != null) {
                    saveBitmapToGallery(bitmap)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@ImageBrowserActivity, "保存成功", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ImageBrowserActivity, "下载失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun saveBitmapToGallery(bitmap: Bitmap) {
        val filename = "IMG_${System.currentTimeMillis()}.jpg"
        var fos: OutputStream? = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
            }
            val imageUri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            if (imageUri != null) {
                fos = contentResolver.openOutputStream(imageUri)
            }
        } else {
            val imagesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            if (!imagesDir.exists()) imagesDir.mkdirs()
            val image = java.io.File(imagesDir, filename)
            fos = java.io.FileOutputStream(image)
        }
        
        fos?.use {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it)
        }
    }

    inner class ImagePagerAdapter : RecyclerView.Adapter<ImagePagerAdapter.ViewHolder>() {
        inner class ViewHolder(itemView: View, val imageView: ImageView, val playButton: ImageView) : RecyclerView.ViewHolder(itemView)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val layout = android.widget.FrameLayout(parent.context).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            }
            val iv = ImageView(parent.context).apply {
                layoutParams = android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
            val playBtn = ImageView(parent.context).apply {
                setImageResource(android.R.drawable.ic_media_play)
                background = android.graphics.drawable.ShapeDrawable(android.graphics.drawable.shapes.OvalShape()).apply {
                    paint.color = android.graphics.Color.parseColor("#80000000")
                }
                setPadding(24, 24, 24, 24)
                setColorFilter(android.graphics.Color.WHITE)
                layoutParams = android.widget.FrameLayout.LayoutParams(144, 144).apply {
                    gravity = android.view.Gravity.CENTER
                }
                visibility = View.GONE
            }
            layout.addView(iv)
            layout.addView(playBtn)
            return ViewHolder(layout, iv, playBtn)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val url = images[position]
            lifecycleScope.launch(Dispatchers.IO) {
                val bytes = if (url.startsWith("/") || url.startsWith("file://")) {
                    runCatching { java.io.File(url.removePrefix("file://")).readBytes() }.getOrNull()
                } else {
                    runCatching { client.downloadBytes(url) }.getOrNull()
                }
                
                if (bytes != null) {
                    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    withContext(Dispatchers.Main) {
                        holder.imageView.setImageBitmap(bmp)
                    }
                }
            }
            
            if (position < videos.size && videos[position].isNotEmpty()) {
                holder.playButton.visibility = View.VISIBLE
                holder.playButton.setOnClickListener {
                    playVideoInApp(videos[position])
                }
            } else {
                holder.playButton.visibility = View.GONE
            }
        }

        override fun getItemCount(): Int = images.size
    }
    
    inner class ImageThumbnailAdapter(val onSelect: (Int) -> Unit) : RecyclerView.Adapter<ImageThumbnailAdapter.ViewHolder>() {
        private var selectedPos = 0
        
        fun setSelected(pos: Int) {
            val old = selectedPos
            selectedPos = pos
            notifyItemChanged(old)
            notifyItemChanged(selectedPos)
        }
        
        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val imageView: ImageView = itemView.findViewById(R.id.iv_thumb)
            val border: View = itemView.findViewById(R.id.view_border)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
             val view = LayoutInflater.from(parent.context).inflate(R.layout.item_image_thumbnail, parent, false)
             return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val url = images[position]
            lifecycleScope.launch(Dispatchers.IO) {
                val bytes = if (url.startsWith("/") || url.startsWith("file://")) {
                    runCatching { java.io.File(url.removePrefix("file://")).readBytes() }.getOrNull()
                } else {
                    runCatching { client.downloadBytes(url) }.getOrNull()
                }
                if (bytes != null) {
                    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    withContext(Dispatchers.Main) {
                        holder.imageView.setImageBitmap(bmp)
                    }
                }
            }
            
            if (selectedPos == position) {
                holder.border.visibility = View.VISIBLE
            } else {
                holder.border.visibility = View.GONE
            }
            
            holder.itemView.setOnClickListener { onSelect(position) }
        }

        override fun getItemCount(): Int = images.size
    }
}

