package com.paopaoime.aikeyboard.ime.ui.activity

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.MediaController
import android.widget.TextView
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.paopaoime.aikeyboard.ime.ui.fragment.KnowledgeBaseDocumentViewerFragment
import com.paopaoime.aikeyboard.ime.ui.knowledgebase.KnowledgeBaseUiStore
import java.io.File

object FavoriteAttachmentPreviewBridge {
    @Volatile
    var onDismissed: (() -> Unit)? = null
}

class FavoriteAttachmentPreviewActivity : AppCompatActivity() {
    companion object {
        private const val EXTRA_FILE_PATH = "file_path"
        private const val EXTRA_DISPLAY_NAME = "display_name"
        private const val EXTRA_MIME_TYPE = "mime_type"
        private const val EXTRA_ATTACHMENT_TYPE = "attachment_type"
        // #region debug-point E:media-preview-back-reporter
        private fun reportMediaPreviewBackDebug(hypothesisId: String, location: String, msg: String, data: String) = Unit
        // #endregion

        fun createIntent(
            context: Context,
            filePath: String,
            displayName: String,
            mimeType: String,
            attachmentType: String,
        ): Intent {
            return Intent(context, FavoriteAttachmentPreviewActivity::class.java).apply {
                putExtra(EXTRA_FILE_PATH, filePath)
                putExtra(EXTRA_DISPLAY_NAME, displayName)
                putExtra(EXTRA_MIME_TYPE, mimeType)
                putExtra(EXTRA_ATTACHMENT_TYPE, attachmentType)
            }
        }
    }

    private var transientDocumentId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        overridePendingTransition(0, 0)
        val filePath = intent.getStringExtra(EXTRA_FILE_PATH).orEmpty()
        val displayName = intent.getStringExtra(EXTRA_DISPLAY_NAME).orEmpty()
        val mimeType = intent.getStringExtra(EXTRA_MIME_TYPE).orEmpty()
        val attachmentType = intent.getStringExtra(EXTRA_ATTACHMENT_TYPE).orEmpty()
        // #region debug-point E:preview-on-create
        reportMediaPreviewBackDebug(
            hypothesisId = "E",
            location = "FavoriteAttachmentPreviewActivity:onCreate",
            msg = "attachment preview activity created",
            data = "attachmentType=$attachmentType,displayName=$displayName,filePath=$filePath,mimeType=$mimeType"
        )
        // #endregion
        val file = File(filePath)
        if (!file.exists() || !file.isFile) {
            finish()
            return
        }

        val root = FrameLayout(this).apply {
            setBackgroundColor(if (attachmentType == "VIDEO") Color.BLACK else Color.WHITE)
        }
        val header = buildHeader(displayName.ifBlank { file.name })
        val content = FrameLayout(this).apply {
            id = View.generateViewId()
        }
        ViewCompat.setOnApplyWindowInsetsListener(header) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(top = bars.top + dp(8))
            insets
        }
        root.addView(content, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))
        root.addView(header, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.TOP
        ))
        setContentView(root)

        when (attachmentType) {
            "DOCUMENT" -> showDocumentPreview(content.id, file, displayName.ifBlank { file.name }, mimeType)
            "VIDEO" -> showVideoPreview(content, file)
            else -> showImagePreview(content, file)
        }
    }

    override fun onDestroy() {
        // #region debug-point E:preview-on-destroy
        reportMediaPreviewBackDebug(
            hypothesisId = "E",
            location = "FavoriteAttachmentPreviewActivity:onDestroy",
            msg = "attachment preview activity destroyed",
            data = "isFinishing=$isFinishing,attachmentType=${intent.getStringExtra(EXTRA_ATTACHMENT_TYPE).orEmpty()},displayName=${intent.getStringExtra(EXTRA_DISPLAY_NAME).orEmpty()}"
        )
        // #endregion
        if (isFinishing) {
            FavoriteAttachmentPreviewBridge.onDismissed?.invoke()
            FavoriteAttachmentPreviewBridge.onDismissed = null
        }
        transientDocumentId?.let(KnowledgeBaseUiStore::clearTransientDocument)
        transientDocumentId = null
        super.onDestroy()
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(0, 0)
    }

    private fun buildHeader(title: String): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.TRANSPARENT)
            setPadding(dp(16), dp(8), dp(16), dp(12))
            addView(ImageView(context).apply {
                setImageResource(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
                imageTintList = android.content.res.ColorStateList.valueOf(
                    if (intent.getStringExtra(EXTRA_ATTACHMENT_TYPE) == "VIDEO") Color.WHITE else Color.BLACK
                )
                setOnClickListener { finish() }
            }, LinearLayout.LayoutParams(dp(24), dp(24)))
            addView(TextView(context).apply {
                text = title
                textSize = 18f
                setTextColor(if (intent.getStringExtra(EXTRA_ATTACHMENT_TYPE) == "VIDEO") Color.WHITE else Color.BLACK)
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(12)
            })
        }
    }

    private fun showImagePreview(container: FrameLayout, file: File) {
        container.addView(ImageView(this).apply {
            setBackgroundColor(Color.BLACK)
            scaleType = ImageView.ScaleType.FIT_CENTER
            setImageBitmap(BitmapFactory.decodeFile(file.absolutePath))
        }, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))
    }

    private fun showVideoPreview(container: FrameLayout, file: File) {
        val videoView = VideoView(this).apply {
            setVideoURI(Uri.fromFile(file))
            setMediaController(MediaController(this@FavoriteAttachmentPreviewActivity).apply {
                setAnchorView(this@apply)
            })
            setOnPreparedListener { player ->
                player.isLooping = true
                start()
            }
        }
        container.addView(videoView, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))
    }

    private fun showDocumentPreview(containerId: Int, file: File, displayName: String, mimeType: String) {
        val documentId = KnowledgeBaseUiStore.registerTransientDocument(file, displayName, mimeType)
        transientDocumentId = documentId
        supportFragmentManager.beginTransaction()
            .replace(containerId, KnowledgeBaseDocumentViewerFragment.newInstance(documentId))
            .commitNowAllowingStateLoss()
    }

    private fun dp(value: Int): Int = (resources.displayMetrics.density * value).toInt()
}
