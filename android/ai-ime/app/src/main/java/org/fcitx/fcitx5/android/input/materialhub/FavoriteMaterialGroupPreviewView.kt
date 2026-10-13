package org.fcitx.fcitx5.android.input.materialhub

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.MediaMetadataRetriever
import android.os.Build
import android.text.TextUtils
import android.text.format.Formatter
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.view.ViewOutlineProvider
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.widget.AppCompatImageButton
import androidx.core.view.isVisible
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.input.favorite.FavoriteAttachmentType
import org.fcitx.fcitx5.android.input.favorite.FavoritePart
import org.fcitx.fcitx5.android.input.favorite.FavoritePickerType
import org.fcitx.fcitx5.android.input.favorite.FavoriteRecord
import splitties.dimensions.dp
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

internal class FavoriteMaterialGroupPreviewView(
    context: Context,
    record: FavoriteRecord,
    private val userAvatarProvider: () -> Bitmap?,
    private val defaultAvatarProvider: () -> Bitmap?,
    private val onAttachmentClick: (FavoritePart.Attachment) -> Unit,
    private val onSendClick: (FavoriteRecord) -> Unit,
    private val onDeleteClick: () -> Unit,
    private val onEditPartClick: (FavoritePart) -> Unit,
) : FrameLayout(context) {
    companion object {
        private const val EDIT_CARD_RADIUS_DP = 9f
        private const val EDIT_CARD_ELEVATION_DP = 2
        private const val EDIT_CARD_VERTICAL_MARGIN_DP = 6
        private const val EDIT_ACTION_TOUCH_SIZE_DP = 28
        private const val EDIT_ACTION_ICON_SCALE = 0.5f
        private const val EDIT_DRAG_HANDLE_WIDTH_DP = 27
        private const val EDIT_DRAG_HANDLE_HEIGHT_DP = 54
    }

    private val actionButton: Button
    private val normalContentContainer: LinearLayout
    private val normalScrollView: ScrollView
    private val editRecyclerView: RecyclerView
    private val deleteLoadingOverlay: View
    private val editAdapter = EditPartAdapter()
    private val workingParts = record.parts.toMutableList()
    private var workingRecord = record.copy(parts = record.parts.toList())
    private var editMode = false
    private var groupDeleting = false
    private var previewEnterAnimationPlayed = false
    private val previewActionButtonWidth = (
        (resources.displayMetrics.widthPixels - context.dp(48)) * 0.5f
    ).roundToInt()
    private val dragTouchHelper = ItemTouchHelper(
        object : ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0) {
            override fun isLongPressDragEnabled(): Boolean = editMode && !groupDeleting

            override fun getMovementFlags(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
            ): Int {
                return if (editMode && !groupDeleting) {
                    makeMovementFlags(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0)
                } else {
                    0
                }
            }

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder,
            ): Boolean {
                val from = viewHolder.bindingAdapterPosition
                val to = target.bindingAdapterPosition
                if (from !in workingParts.indices || to !in workingParts.indices) return false
                val moved = workingParts.removeAt(from)
                workingParts.add(to, moved)
                editAdapter.notifyItemMoved(from, to)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit

            override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                super.onSelectedChanged(viewHolder, actionState)
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                    viewHolder?.itemView?.apply {
                        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        animate().scaleX(1.02f).scaleY(1.02f).translationZ(context.dp(10).toFloat()).setDuration(120).start()
                    }
                }
            }

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                viewHolder.itemView.animate().scaleX(1f).scaleY(1f).translationZ(0f).setDuration(120).start()
            }
        }
    )

    init {
        setBackgroundColor(Color.parseColor("#F3F4F6"))
        normalContentContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(context.dp(24), context.dp(18), context.dp(24), context.dp(120))
        }
        normalScrollView = ScrollView(context).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            addView(
                normalContentContainer,
                LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }
        editRecyclerView = RecyclerView(context).apply {
            visibility = View.GONE
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            layoutManager = LinearLayoutManager(context)
            adapter = editAdapter
            clipToPadding = false
            setPadding(context.dp(20), context.dp(18), context.dp(20), context.dp(28))
            itemAnimator?.changeDuration = 160L
            itemAnimator?.moveDuration = 180L
            itemAnimator?.removeDuration = 180L
            itemAnimator?.addDuration = 180L
        }
        dragTouchHelper.attachToRecyclerView(editRecyclerView)
        addView(
            normalScrollView,
            LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        addView(
            editRecyclerView,
            LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        actionButton = Button(context).apply {
            minHeight = context.dp(48)
            minimumHeight = context.dp(48)
            textSize = 15f
            isAllCaps = false
            setTextColor(Color.WHITE)
            setPadding(context.dp(28), context.dp(12), context.dp(28), context.dp(12))
            stateListAnimator = null
            setOnClickListener {
                if (groupDeleting) return@setOnClickListener
                onSendClick(currentRecordSnapshot())
            }
        }
        addView(
            actionButton,
            LayoutParams(
                previewActionButtonWidth,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            ).apply {
                bottomMargin = context.dp(28)
            }
        )
        deleteLoadingOverlay = buildDeleteLoadingOverlay(context)
        addView(
            deleteLoadingOverlay,
            LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        renderNormalContent()
        renderActionButton()
    }

    fun toggleEditMode() {
        setEditMode(!editMode)
    }

    fun isEditMode(): Boolean = editMode

    fun setEditMode(enabled: Boolean) {
        editMode = enabled
        normalScrollView.isVisible = !enabled
        editRecyclerView.isVisible = enabled
        renderActionButton()
        if (enabled) {
            editAdapter.notifyDataSetChanged()
        } else {
            renderNormalContent()
        }
    }

    fun currentRecordSnapshot(): FavoriteRecord {
        return workingRecord.copy(parts = workingParts.toList())
    }

    fun updateWorkingRecord(record: FavoriteRecord) {
        workingRecord = record.copy(parts = record.parts.toList())
        workingParts.clear()
        workingParts.addAll(record.parts)
        renderNormalContent()
        editAdapter.notifyDataSetChanged()
    }

    fun replacePart(partId: String, newPart: FavoritePart) {
        val index = workingParts.indexOfFirst { it.id == partId }
        if (index < 0) return
        workingParts[index] = newPart
        renderNormalContent()
        editAdapter.notifyItemChanged(index)
    }

    fun updateTextPart(partId: String, text: String) {
        val index = workingParts.indexOfFirst { it.id == partId }
        val existing = workingParts.getOrNull(index) as? FavoritePart.Text ?: return
        workingParts[index] = existing.copy(text = text)
        renderNormalContent()
        editAdapter.notifyItemChanged(index)
    }

    fun setGroupDeleting(loading: Boolean) {
        groupDeleting = loading
        deleteLoadingOverlay.isVisible = loading
        actionButton.isEnabled = !loading
        actionButton.alpha = if (loading) 0.7f else 1f
        editRecyclerView.isEnabled = !loading
        normalScrollView.isEnabled = !loading
    }

    fun playPreviewEnterAnimation() {
        if (previewEnterAnimationPlayed || editMode) return
        previewEnterAnimationPlayed = true
        val buttonTravel = context.dp(56).toFloat()
        val itemTravel = context.dp(18).toFloat()
        val buttonInterpolator = OvershootInterpolator(0.95f)
        val itemInterpolator = OvershootInterpolator(0.75f)

        actionButton.animate().cancel()
        actionButton.alpha = 0f
        actionButton.translationY = buttonTravel

        normalContentContainer.childrenForAnimation().forEachIndexed { index, child ->
            child.animate().cancel()
            child.alpha = 0f
            child.translationY = itemTravel
            child.post {
                child.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setStartDelay((index * 45L).coerceAtMost(180L))
                    .setDuration(180L)
                    .setInterpolator(itemInterpolator)
                    .start()
            }
        }

        actionButton.post {
            actionButton.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(70L)
                .setDuration(340L)
                .setInterpolator(buttonInterpolator)
                .start()
        }
    }

    private fun removePart(partId: String) {
        val index = workingParts.indexOfFirst { it.id == partId }
        if (index < 0) return
        workingParts.removeAt(index)
        renderNormalContent()
        editAdapter.notifyItemRemoved(index)
    }

    private fun renderActionButton() {
        if (editMode) {
            actionButton.visibility = View.GONE
        } else {
            actionButton.visibility = View.VISIBLE
            actionButton.text = "立即发送"
            actionButton.background = roundedDrawable("#07C160", 24f)
        }
    }

    private fun renderNormalContent() {
        normalContentContainer.removeAllViews()
        workingParts.forEachIndexed { index, part ->
            normalContentContainer.addView(
                buildMessageBlock(context, part),
                LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
            if (index != workingParts.lastIndex) {
                normalContentContainer.addView(
                    View(context),
                    LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        context.dp(16)
                    )
                )
            }
        }
        normalScrollView.post { normalScrollView.fullScroll(FOCUS_UP) }
    }

    private fun buildDeleteLoadingOverlay(context: Context): View {
        return FrameLayout(context).apply {
            visibility = View.GONE
            setBackgroundColor(Color.parseColor("#33000000"))
            addView(
                LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    background = roundedDrawable("#CC111827", 16f)
                    setPadding(context.dp(18), context.dp(14), context.dp(18), context.dp(14))
                    addView(
                        ProgressBar(context).apply {
                            isIndeterminate = true
                            indeterminateTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
                        },
                        LinearLayout.LayoutParams(context.dp(20), context.dp(20))
                    )
                    addView(
                        TextView(context).apply {
                            text = "删除中..."
                            setTextColor(Color.WHITE)
                            textSize = 14f
                            typeface = Typeface.DEFAULT_BOLD
                        },
                        LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        ).apply {
                            marginStart = context.dp(12)
                        }
                    )
                },
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER
                )
            )
        }
    }

    private fun buildMessageBlock(context: Context, part: FavoritePart): View {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP or Gravity.END
            val bubbleView = when (part) {
                is FavoritePart.Text -> buildTextBubble(context, part)
                is FavoritePart.Attachment -> when (part.attachmentType) {
                    FavoriteAttachmentType.DOCUMENT -> buildDocumentBubble(context, part)
                    FavoriteAttachmentType.VIDEO -> buildVideoBubble(context, part)
                    else -> buildImageBubble(context, part)
                }
            }
            addView(
                bubbleView,
                LayoutParams(
                    if (part is FavoritePart.Attachment && part.attachmentType == FavoriteAttachmentType.DOCUMENT) {
                        documentBubbleWidth(context)
                    } else {
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    },
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
            addView(
                buildSenderAvatarView(context),
                LayoutParams(
                    context.dp(26),
                    context.dp(26)
                ).apply {
                    marginStart = context.dp(10)
                }
            )
        }
    }

    private fun buildSenderAvatarView(context: Context): View {
        val customAvatarBitmap = userAvatarProvider()
        val defaultAvatarBitmap = defaultAvatarProvider()
        return ImageView(context).apply {
            background = ovalDrawable("#FFFFFF")
            clipToOutline = true
            outlineProvider = roundedOutlineProvider(context, 13f)
            when {
                customAvatarBitmap != null -> {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    setImageBitmap(customAvatarBitmap)
                }
                defaultAvatarBitmap != null -> {
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    setImageBitmap(defaultAvatarBitmap)
                }
                else -> {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                }
            }
        }
    }

    private fun buildTextBubble(context: Context, part: FavoritePart.Text): View {
        return TextView(context).apply {
            text = part.text.trim()
            setTextColor(Color.WHITE)
            textSize = 15f
            maxWidth = ((resources.displayMetrics.widthPixels - context.dp(48)) * 0.7f).roundToInt()
            setLineSpacing(context.dp(4).toFloat(), 1f)
            setPadding(context.dp(16), context.dp(12), context.dp(16), context.dp(12))
            background = roundedDrawable(
                color = "#1D39FF",
                topLeft = 18f,
                topRight = 18f,
                bottomRight = 4f,
                bottomLeft = 18f
            )
        }
    }

    private fun buildImageBubble(context: Context, part: FavoritePart.Attachment): View {
        val file = localAttachmentFileOrNull(part)
        val mediaSize = resolvePreviewSize(context, file?.let(::resolveImageDimensions))
        return FrameLayout(context).apply {
            background = roundedDrawable("#E5E7EB", 12f)
            clipToOutline = true
            outlineProvider = roundedOutlineProvider(context, 12f)
            isClickable = true
            isFocusable = true
            setOnClickListener { onAttachmentClick(part) }
            val imageView = ImageView(context).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                setBackgroundColor(Color.parseColor("#E5E7EB"))
                setImageResource(R.drawable.ic_baseline_auto_awesome_24)
                imageTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#6B7280"))
            }
            if (file != null) {
                imageView.setImageURI(android.net.Uri.fromFile(file))
                imageView.imageTintList = null
            } else if (part.downloadUrl.isNotBlank()) {
                bindRemoteImagePreview(imageView, part.downloadUrl)
            }
            addView(
                imageView,
                FrameLayout.LayoutParams(mediaSize.first, mediaSize.second)
            )
        }
    }

    private fun buildVideoBubble(context: Context, part: FavoritePart.Attachment): View {
        val file = localAttachmentFileOrNull(part)
        val mediaSize = resolvePreviewSize(context, file?.let(::resolveVideoDimensions))
        return FrameLayout(context).apply {
            background = roundedDrawable("#111827", 12f)
            clipToOutline = true
            outlineProvider = roundedOutlineProvider(context, 12f)
            isClickable = true
            isFocusable = true
            setOnClickListener { onAttachmentClick(part) }
            addView(
                ImageView(context).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    setBackgroundColor(Color.parseColor("#111827"))
                    val thumbnail = file?.let(::createVideoThumbnail)
                    if (thumbnail != null) {
                        setImageBitmap(thumbnail)
                    } else {
                        setImageResource(R.drawable.ic_baseline_auto_awesome_24)
                        imageTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
                        scaleType = ImageView.ScaleType.CENTER
                    }
                },
                FrameLayout.LayoutParams(mediaSize.first, mediaSize.second)
            )
            addView(
                ImageView(context).apply {
                    setImageResource(android.R.drawable.ic_media_play)
                    imageTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
                    background = ovalDrawable("#66000000")
                    setPadding(context.dp(10), context.dp(10), context.dp(10), context.dp(10))
                },
                FrameLayout.LayoutParams(context.dp(44), context.dp(44), Gravity.CENTER)
            )
        }
    }

    private fun buildDocumentBubble(context: Context, part: FavoritePart.Attachment): View {
        val file = localAttachmentFileOrNull(part)
        val sizeText = Formatter.formatShortFileSize(
            context,
            part.fileSizeBytes.takeIf { it > 0 } ?: file?.length() ?: 0L
        )
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = roundedDrawable("#FFFFFF", 14f)
            setPadding(context.dp(16), context.dp(14), context.dp(16), context.dp(14))
            isClickable = true
            isFocusable = true
            setOnClickListener { onAttachmentClick(part) }
            layoutParams = LinearLayout.LayoutParams(documentBubbleWidth(context), ViewGroup.LayoutParams.WRAP_CONTENT)
            addView(
                LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(
                        TextView(context).apply {
                            text = part.displayName.ifBlank { context.getString(R.string.favorite_attachment_document) }
                            setTextColor(Color.parseColor("#111827"))
                            textSize = 16f
                            maxLines = 2
                            ellipsize = TextUtils.TruncateAt.END
                            setLineSpacing(context.dp(4).toFloat(), 1f)
                        }
                    )
                    addView(
                        TextView(context).apply {
                            text = sizeText
                            setTextColor(Color.parseColor("#6B7280"))
                            textSize = 13f
                        },
                        LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        ).apply {
                            topMargin = context.dp(6)
                        }
                    )
                },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            )
            addView(
                ImageView(context).apply {
                    setImageResource(resolveDocumentIcon(part))
                    scaleType = ImageView.ScaleType.FIT_CENTER
                },
                LayoutParams(context.dp(52), context.dp(52)).apply {
                    marginStart = context.dp(16)
                }
            )
        }
    }

    private fun buildEditRow(parent: ViewGroup): EditPartViewHolder {
        val root = LinearLayout(parent.context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = context.dp(96)
            background = roundedDrawable("#FFFFFF", EDIT_CARD_RADIUS_DP)
            elevation = context.dp(EDIT_CARD_ELEVATION_DP).toFloat()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                outlineAmbientShadowColor = Color.parseColor("#14000000")
                outlineSpotShadowColor = Color.parseColor("#0D000000")
            }
            setPadding(context.dp(12), context.dp(12), context.dp(12), context.dp(12))
            layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = context.dp(EDIT_CARD_VERTICAL_MARGIN_DP)
                bottomMargin = context.dp(EDIT_CARD_VERTICAL_MARGIN_DP)
            }
        }
        val dragHandle = buildDragHandle(root.context)
        val contentHost = FrameLayout(root.context)
        val actionColumn = LinearLayout(root.context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        val editButton = buildActionIconButton(root.context, R.drawable.ic_favorite_preview_item_edit)
        val deleteButton = buildActionIconButton(root.context, R.drawable.ic_favorite_preview_item_delete)
        actionColumn.addView(
            editButton,
            LinearLayout.LayoutParams(
                root.context.dp(EDIT_ACTION_TOUCH_SIZE_DP),
                root.context.dp(EDIT_ACTION_TOUCH_SIZE_DP)
            )
        )
        actionColumn.addView(
            deleteButton,
            LinearLayout.LayoutParams(
                root.context.dp(EDIT_ACTION_TOUCH_SIZE_DP),
                root.context.dp(EDIT_ACTION_TOUCH_SIZE_DP)
            ).apply {
                topMargin = root.context.dp(14)
            }
        )
        root.addView(
            dragHandle,
            LinearLayout.LayoutParams(
                root.context.dp(EDIT_DRAG_HANDLE_WIDTH_DP),
                root.context.dp(EDIT_DRAG_HANDLE_HEIGHT_DP)
            ).apply {
                marginEnd = root.context.dp(12)
            }
        )
        root.addView(
            contentHost,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
        root.addView(
            actionColumn,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = root.context.dp(12)
            }
        )
        return EditPartViewHolder(
            root = root,
            contentHost = contentHost,
            editButton = editButton,
            deleteButton = deleteButton,
        )
    }

    private fun buildActionIconButton(context: Context, iconRes: Int): AppCompatImageButton {
        return AppCompatImageButton(context).apply {
            background = null
            setImageResource(iconRes)
            imageTintList = null
            scaleType = ImageView.ScaleType.FIT_CENTER
            setPadding(0, 0, 0, 0)
            scaleX = EDIT_ACTION_ICON_SCALE
            scaleY = EDIT_ACTION_ICON_SCALE
        }
    }

    private fun buildDragHandle(context: Context): View {
        return ImageView(context).apply {
            setImageResource(R.drawable.ic_favorite_preview_sort)
            imageTintList = null
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
    }

    private fun buildEditPreviewView(part: FavoritePart): View {
        return when (part) {
            is FavoritePart.Text -> TextView(context).apply {
                text = part.text.trim()
                setTextColor(Color.parseColor("#111827"))
                textSize = 15f
                typeface = Typeface.DEFAULT_BOLD
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
                setLineSpacing(context.dp(4).toFloat(), 1f)
                minHeight = context.dp(72)
                gravity = Gravity.CENTER_VERTICAL
            }
            is FavoritePart.Attachment -> when (part.attachmentType) {
                FavoriteAttachmentType.DOCUMENT -> buildEditDocumentPreview(part)
                FavoriteAttachmentType.VIDEO -> buildEditMediaPreview(part, isVideo = true)
                else -> buildEditMediaPreview(part, isVideo = false)
            }
        }
    }

    private fun buildEditDocumentPreview(part: FavoritePart.Attachment): View {
        val file = localAttachmentFileOrNull(part)
        val sizeText = Formatter.formatShortFileSize(
            context,
            part.fileSizeBytes.takeIf { it > 0 } ?: file?.length() ?: 0L
        )
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = context.dp(72)
            addView(
                ImageView(context).apply {
                    setImageResource(resolveDocumentIcon(part))
                    scaleType = ImageView.ScaleType.FIT_CENTER
                },
                LinearLayout.LayoutParams(context.dp(52), context.dp(52))
            )
            addView(
                LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(
                        TextView(context).apply {
                            text = part.displayName.ifBlank { context.getString(R.string.favorite_attachment_document) }
                            setTextColor(Color.parseColor("#111827"))
                            textSize = 14f
                            typeface = Typeface.DEFAULT_BOLD
                            maxLines = 2
                            ellipsize = TextUtils.TruncateAt.END
                        }
                    )
                    addView(
                        TextView(context).apply {
                            text = sizeText
                            setTextColor(Color.parseColor("#6B7280"))
                            textSize = 12f
                        },
                        LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        ).apply {
                            topMargin = context.dp(6)
                        }
                    )
                },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = context.dp(12)
                }
            )
        }
    }

    private fun buildEditMediaPreview(part: FavoritePart.Attachment, isVideo: Boolean): View {
        val file = localAttachmentFileOrNull(part)
        val size = if (isVideo) {
            resolveEditPreviewSize(file?.let(::resolveVideoDimensions))
        } else {
            resolveEditPreviewSize(file?.let(::resolveImageDimensions))
        }
        return FrameLayout(context).apply {
            minimumHeight = context.dp(72)
            addView(
                FrameLayout(context).apply {
                    background = roundedDrawable(if (isVideo) "#111827" else "#E5E7EB", 12f)
                    clipToOutline = true
                    outlineProvider = roundedOutlineProvider(context, 12f)
                    addView(
                        ImageView(context).apply {
                            scaleType = ImageView.ScaleType.CENTER_CROP
                            if (isVideo) {
                                val thumbnail = file?.let(::createVideoThumbnail)
                                if (thumbnail != null) {
                                    setImageBitmap(thumbnail)
                                } else {
                                    setImageResource(R.drawable.ic_baseline_auto_awesome_24)
                                    imageTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
                                    scaleType = ImageView.ScaleType.CENTER
                                }
                            } else {
                                if (file != null) {
                                    setImageURI(android.net.Uri.fromFile(file))
                                } else {
                                    setImageResource(R.drawable.ic_baseline_auto_awesome_24)
                                    imageTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#6B7280"))
                                    scaleType = ImageView.ScaleType.CENTER
                                }
                            }
                        },
                        FrameLayout.LayoutParams(size.first, size.second)
                    )
                    if (isVideo) {
                        addView(
                            ImageView(context).apply {
                                setImageResource(android.R.drawable.ic_media_play)
                                imageTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
                                background = ovalDrawable("#66000000")
                                setPadding(context.dp(6), context.dp(6), context.dp(6), context.dp(6))
                            },
                            FrameLayout.LayoutParams(context.dp(28), context.dp(28), Gravity.CENTER)
                        )
                    }
                },
                FrameLayout.LayoutParams(size.first, size.second, Gravity.CENTER_VERTICAL)
            )
        }
    }

    private fun documentBubbleWidth(context: Context): Int {
        val availableWidth = resources.displayMetrics.widthPixels - context.dp(48)
        return (availableWidth * 0.8f).roundToInt()
    }

    private fun resolvePreviewSize(context: Context, sourceSize: Pair<Int, Int>?): Pair<Int, Int> {
        val availableWidth = resources.displayMetrics.widthPixels - context.dp(48)
        val maxWidth = max(context.dp(96), (availableWidth * 0.4f).roundToInt())
        val maxHeight = max(context.dp(120), (context.dp(220) * 0.4f).roundToInt())
        val rawWidth = sourceSize?.first?.takeIf { it > 0 } ?: maxWidth
        val rawHeight = sourceSize?.second?.takeIf { it > 0 } ?: maxHeight
        val widthScale = maxWidth.toFloat() / rawWidth.toFloat()
        val heightScale = maxHeight.toFloat() / rawHeight.toFloat()
        val scale = min(1f, min(widthScale, heightScale))
        val finalWidth = max(context.dp(72), (rawWidth * scale).roundToInt())
        val finalHeight = max(context.dp(72), (rawHeight * scale).roundToInt())
        return finalWidth to finalHeight
    }

    private fun resolveEditPreviewSize(sourceSize: Pair<Int, Int>?): Pair<Int, Int> {
        val targetHeight = context.dp(72)
        val maxWidth = context.dp(110)
        val rawWidth = sourceSize?.first?.takeIf { it > 0 } ?: targetHeight
        val rawHeight = sourceSize?.second?.takeIf { it > 0 } ?: targetHeight
        val scale = min(1f, targetHeight.toFloat() / rawHeight.toFloat())
        val scaledWidth = (rawWidth * scale).roundToInt().coerceAtLeast(context.dp(72)).coerceAtMost(maxWidth)
        return scaledWidth to targetHeight
    }

    private fun resolveImageDimensions(file: File): Pair<Int, Int>? {
        return runCatching {
            BitmapFactory.Options().apply {
                inJustDecodeBounds = true
                BitmapFactory.decodeFile(file.absolutePath, this)
            }.let { options ->
                options.outWidth to options.outHeight
            }.takeIf { it.first > 0 && it.second > 0 }
        }.getOrNull()
    }

    private fun resolveVideoDimensions(file: File): Pair<Int, Int>? {
        val retriever = MediaMetadataRetriever()
        return runCatching {
            retriever.setDataSource(file.absolutePath)
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()
            if (width != null && height != null && width > 0 && height > 0) {
                width to height
            } else {
                null
            }
        }.getOrNull().also {
            runCatching { retriever.release() }
        }
    }

    private fun createVideoThumbnail(file: File): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return runCatching {
            retriever.setDataSource(file.absolutePath)
            retriever.frameAtTime
        }.getOrNull().also {
            runCatching { retriever.release() }
        }
    }

    private fun localAttachmentFileOrNull(part: FavoritePart.Attachment): File? {
        return part.storedPath
            .takeIf { it.isNotBlank() }
            ?.let(::File)
            ?.takeIf { it.exists() && it.isFile }
    }

    private fun bindRemoteImagePreview(imageView: ImageView, imageUrl: String) {
        Thread {
            val remoteBitmap = runCatching {
                java.net.URL(imageUrl).openStream().use(BitmapFactory::decodeStream)
            }.getOrNull()
            imageView.post {
                if (remoteBitmap != null) {
                    imageView.setImageBitmap(remoteBitmap)
                    imageView.imageTintList = null
                }
            }
        }.start()
    }

    private fun resolveDocumentIcon(part: FavoritePart.Attachment): Int {
        val lowerName = part.displayName.lowercase()
        val mime = part.mimeType.lowercase()
        return when {
            lowerName.endsWith(".pdf") || mime.contains("pdf") -> R.drawable.ic_office_pdf_36
            lowerName.endsWith(".ppt") || lowerName.endsWith(".pptx") ||
                mime.contains("presentation") -> R.drawable.ic_office_ppt_36
            lowerName.endsWith(".xls") || lowerName.endsWith(".xlsx") ||
                lowerName.endsWith(".csv") || mime.contains("spreadsheet") ||
                mime.contains("excel") || mime.contains("sheet") -> R.drawable.ic_office_excel_36
            lowerName.endsWith(".doc") || lowerName.endsWith(".docx") ||
                mime.contains("word") || mime.contains("document") -> R.drawable.ic_office_word_36
            else -> R.drawable.ic_baseline_library_books_24
        }
    }

    private fun ovalDrawable(color: String): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor(color))
        }
    }

    private fun roundedDrawable(color: String, radiusDp: Float): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = context.dp(radiusDp.roundToInt()).toFloat()
            setColor(Color.parseColor(color))
        }
    }

    private fun LinearLayout.childrenForAnimation(): List<View> {
        return buildList {
            for (index in 0 until childCount step 2) {
                getChildAt(index)?.let(::add)
            }
        }
    }

    private fun roundedDrawable(
        color: String,
        topLeft: Float,
        topRight: Float,
        bottomRight: Float,
        bottomLeft: Float,
    ): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadii = floatArrayOf(
                context.dp(topLeft.roundToInt()).toFloat(), context.dp(topLeft.roundToInt()).toFloat(),
                context.dp(topRight.roundToInt()).toFloat(), context.dp(topRight.roundToInt()).toFloat(),
                context.dp(bottomRight.roundToInt()).toFloat(), context.dp(bottomRight.roundToInt()).toFloat(),
                context.dp(bottomLeft.roundToInt()).toFloat(), context.dp(bottomLeft.roundToInt()).toFloat()
            )
            setColor(Color.parseColor(color))
        }
    }

    private fun roundedOutlineProvider(context: Context, radiusDp: Float): ViewOutlineProvider {
        return object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, context.dp(radiusDp.roundToInt()).toFloat())
            }
        }
    }

    private inner class EditPartAdapter : RecyclerView.Adapter<EditPartViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EditPartViewHolder {
            return buildEditRow(parent)
        }

        override fun onBindViewHolder(holder: EditPartViewHolder, position: Int) {
            val part = workingParts[position]
            holder.contentHost.removeAllViews()
            holder.contentHost.addView(
                buildEditPreviewView(part),
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
            holder.editButton.setOnClickListener {
                if (groupDeleting) return@setOnClickListener
                onEditPartClick(part)
            }
            holder.deleteButton.setOnClickListener {
                if (groupDeleting) return@setOnClickListener
                removePart(part.id)
            }
        }

        override fun getItemCount(): Int = workingParts.size

        override fun getItemId(position: Int): Long = workingParts[position].id.hashCode().toLong()

        init {
            setHasStableIds(true)
        }
    }

    private data class EditPartViewHolder(
        val root: View,
        val contentHost: FrameLayout,
        val editButton: AppCompatImageButton,
        val deleteButton: AppCompatImageButton,
    ) : RecyclerView.ViewHolder(root)
}
