package com.paopaoime.aikeyboard.ime.view

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.text.Editable
import android.text.TextWatcher
import android.util.Size
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.core.view.setPadding
import com.paopaoime.aikeyboard.ime.R
import com.paopaoime.aikeyboard.ime.data.theme.Theme
import com.paopaoime.aikeyboard.ime.data.theme.ThemeManager
import com.paopaoime.aikeyboard.ime.database.DataBaseKT
import com.paopaoime.aikeyboard.ime.database.entry.Phrase
import com.paopaoime.aikeyboard.ime.utils.copyUriToPhraseAttachment
import com.paopaoime.aikeyboard.ime.utils.deletePhraseAttachmentUri
import com.paopaoime.aikeyboard.ime.libs.pinyin4j.PinyinHelper
import com.paopaoime.aikeyboard.ime.utils.toast
import com.paopaoime.aikeyboard.ime.view.widget.ImeEditText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import splitties.dimensions.dp
import splitties.views.dsl.core.add
import splitties.views.dsl.core.lParams
import splitties.views.dsl.core.matchParent
import splitties.views.dsl.core.wrapContent

class EditPhrasesView(context: Context) : LinearLayout(context){

    var onDone: (() -> Unit)? = null
    var onInputClick: (() -> Unit)? = null
    var onAddFileClick: (() -> Unit)? = null

    private val selectedImageUris = mutableListOf<Uri>()
    private val selectedFileUris = mutableListOf<Uri>()
    private val maxAttachments = 9

    private var mEtPhrases: ImeEditText
    private var mEtPhrasesQuickCode:ImeEditText
    private var mEtPhrasesTips:TextView
    private val editContainerBackground: GradientDrawable
    private val addFileButtonBackground: GradientDrawable
    private val addFileIcon: ImageView
    private val addFileText: TextView
    private val addFileEmptyLayout: LinearLayout
    private val attachmentsRecyclerView: RecyclerView
    private val attachmentsAdapter: AttachmentsAdapter
    init {
        orientation = VERTICAL
        mEtPhrasesTips = TextView(context).apply {
            visibility = GONE
        }
        mEtPhrasesQuickCode = ImeEditText(context).apply {
            setPadding(dp(5))
            background = null
            isCursorVisible = true
            isFocusable = true
            isFocusableInTouchMode = true
            textSize = 12f
            visibility = GONE
        }
       val llTipContainer = LinearLayout(context).apply {
            visibility = GONE
        }
        llTipContainer.add(mEtPhrasesTips, lParams(weight = 0f))
        llTipContainer.add(mEtPhrasesQuickCode, lParams(weight = 1f))
        mEtPhrases = ImeEditText(context).apply {
            background = null
            isCursorVisible = true
            isFocusable = true
            isFocusableInTouchMode = true
            isSingleLine = true
            gravity = Gravity.CENTER_VERTICAL
            textSize = 16f
            minLines = 1
            maxLines = 1
            setPadding(dp(10))
            setHint(R.string.add_phrases_input_hint)
        }
        editContainerBackground = GradientDrawable().apply {
            setColor(Color.parseColor("#F5F5F5"))
            cornerRadius = dp(12).toFloat()
        }
        val editContainer = FrameLayout(context).apply {
            background = editContainerBackground
        }
        editContainer.addView(
            mEtPhrases,
            FrameLayout.LayoutParams(matchParent, dp(44)).apply {
                gravity = Gravity.CENTER_VERTICAL
            }
        )
        add(llTipContainer, lParams(width = matchParent,height = wrapContent, weight = 0f))
        add(editContainer, lParams(width = matchParent, height = wrapContent, weight = 0f).apply {
            leftMargin = dp(12)
            rightMargin = dp(12)
            topMargin = dp(10)
            bottomMargin = dp(10)
        })

        addFileEmptyLayout = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(6), dp(12), dp(6))
        }

        addFileButtonBackground = GradientDrawable().apply {
            setColor(Color.parseColor("#F0F0F0"))
            cornerRadius = dp(10).toFloat()
        }
        val addButtonContainer = FrameLayout(context).apply {
             background = addFileButtonBackground
        }
        addFileIcon = ImageView(context).apply {
             setImageResource(R.drawable.ic_menu_plus)
             setColorFilter(ThemeManager.activeTheme.keyTextColor)
        }
        addButtonContainer.addView(addFileIcon, FrameLayout.LayoutParams(dp(18), dp(18), Gravity.CENTER))
        addButtonContainer.setOnClickListener { onAddFileClick?.invoke() }

        addFileText = TextView(context).apply {
            setText(R.string.add_file)
            textSize = 16f
            setTextColor(ThemeManager.activeTheme.keyTextColor)
            alpha = 0.6f
            setOnClickListener { onAddFileClick?.invoke() }
        }

        addFileEmptyLayout.add(addButtonContainer, LinearLayout.LayoutParams(dp(40), dp(40)).apply { marginEnd = dp(12) })
        addFileEmptyLayout.add(addFileText, LinearLayout.LayoutParams(wrapContent, wrapContent))

        attachmentsAdapter = AttachmentsAdapter(
            onAddClick = { onAddFileClick?.invoke() },
        )
        attachmentsRecyclerView = RecyclerView(context).apply {
            overScrollMode = OVER_SCROLL_NEVER
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            adapter = attachmentsAdapter
            visibility = GONE
            setPadding(dp(12), dp(6), dp(12), dp(6))
        }

        add(addFileEmptyLayout, lParams(width = matchParent, height = wrapContent, weight = 0f))
        add(attachmentsRecyclerView, lParams(width = matchParent, height = wrapContent, weight = 0f))
    }

    fun handleAddPhrasesView() {
        mEtPhrases.requestFocus()
        mEtPhrases.setOnClickListener { onInputClick?.invoke() }
        mEtPhrases.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(editable: Editable) {
                val pinYinHeadChar  = PinyinHelper.getPinYinHeadChar(editable.toString().take(4))
                if(pinYinHeadChar.isNotBlank()) {
                    mEtPhrasesQuickCode.setText(pinYinHeadChar)
                    mEtPhrasesQuickCode.setSelection(pinYinHeadChar.length)
                }
            }
        })
    }

    private fun pinyinCharToT9Key(ch: Char): Char {
        return when (ch.lowercaseChar()) {
            'a', 'b', 'c' -> '2'
            'd', 'e', 'f' -> '3'
            'g', 'h', 'i' -> '4'
            'j', 'k', 'l' -> '5'
            'm', 'n', 'o' -> '6'
            'p', 'q', 'r', 's' -> '7'
            't', 'u', 'v' -> '8'
            'w', 'x', 'y', 'z' -> '9'
            else -> ch
        }
    }

    private fun pinyinCharToLx17Key(ch: Char): Char {
        return pinyinCharToT9Key(ch)
    }

    fun clearPhrasesContent() {
        mEtPhrases.setText("")
        setSelectedImageUris(emptyList())
        selectedFileUris.clear()
        updateAttachmentsUi()
    }

    private fun encodeUris(list: List<Uri>): String? {
        if (list.isEmpty()) return null
        return list.joinToString(separator = "|") { it.toString() }
    }

    private fun decodeUris(value: String?): List<Uri> {
        if (value.isNullOrBlank()) return emptyList()
        return value.split("|")
            .mapNotNull {
                try {
                    Uri.parse(it)
                } catch (_: Throwable) {
                    null
                }
            }
    }

    fun addPhrasesHandle() {
        val content = mEtPhrases.text?.toString()?.trim().orEmpty()
        if (content.isBlank()) return

        val qwerty = mEtPhrasesQuickCode.text?.toString()?.trim()
            .orEmpty()
            .ifBlank { PinyinHelper.getPinYinHeadChar(content.take(4)) }

        val qwertyLower = qwerty.lowercase()
        val t9 = buildString {
            qwertyLower.forEach { ch ->
                append(pinyinCharToT9Key(ch))
            }
        }
        val lx17 = buildString {
            qwertyLower.forEach { ch ->
                append(pinyinCharToLx17Key(ch))
            }
        }

        val imageSnapshot = selectedImageUris.toList()
        val fileSnapshot = selectedFileUris.toList()
        CoroutineScope(Dispatchers.Main).launch {
            val copied = withContext(Dispatchers.IO) {
                val copiedImages = imageSnapshot.mapNotNull { runCatching { context.copyUriToPhraseAttachment(it) }.getOrNull() }
                val copiedFiles = fileSnapshot.mapNotNull { runCatching { context.copyUriToPhraseAttachment(it) }.getOrNull() }
                copiedImages to copiedFiles
            }
            if (copied.first.size != imageSnapshot.size || copied.second.size != fileSnapshot.size) {
                context.toast("附件保存失败，请重新选择")
                return@launch
            }
            val phrase = Phrase(
                content = content,
                isKeep = 0,
                t9 = t9,
                qwerty = qwerty,
                lx17 = lx17,
                time = System.currentTimeMillis(),
                imageUris = encodeUris(copied.first),
                fileUris = encodeUris(copied.second),
            )
            DataBaseKT.instance.phraseDao().insert(phrase)
            clearPhrasesContent()
            onDone?.invoke()
        }
    }

    fun updatePhrasesHandle(original: Phrase) {
        val content = mEtPhrases.text?.toString()?.trim().orEmpty()
        if (content.isBlank()) return

        val qwerty = mEtPhrasesQuickCode.text?.toString()?.trim()
            .orEmpty()
            .ifBlank { PinyinHelper.getPinYinHeadChar(content.take(4)) }

        val qwertyLower = qwerty.lowercase()
        val t9 = buildString {
            qwertyLower.forEach { ch ->
                append(pinyinCharToT9Key(ch))
            }
        }
        val lx17 = buildString {
            qwertyLower.forEach { ch ->
                append(pinyinCharToLx17Key(ch))
            }
        }

        val oldUris = decodeUris(original.imageUris) + decodeUris(original.fileUris)
        val imageSnapshot = selectedImageUris.toList()
        val fileSnapshot = selectedFileUris.toList()
        CoroutineScope(Dispatchers.Main).launch {
            val copied = withContext(Dispatchers.IO) {
                val copiedImages = imageSnapshot.mapNotNull { runCatching { context.copyUriToPhraseAttachment(it) }.getOrNull() }
                val copiedFiles = fileSnapshot.mapNotNull { runCatching { context.copyUriToPhraseAttachment(it) }.getOrNull() }
                copiedImages to copiedFiles
            }
            if (copied.first.size != imageSnapshot.size || copied.second.size != fileSnapshot.size) {
                context.toast("附件保存失败，请重新选择")
                return@launch
            }
            val newUriStrings = HashSet<String>(copied.first.size + copied.second.size).apply {
                copied.first.forEach { add(it.toString()) }
                copied.second.forEach { add(it.toString()) }
            }
            withContext(Dispatchers.IO) {
                oldUris.forEach { uri ->
                    if (!newUriStrings.contains(uri.toString())) {
                        runCatching { context.deletePhraseAttachmentUri(uri) }
                    }
                }
            }
            if (content != original.content) {
                DataBaseKT.instance.phraseDao().deleteByContent(original.content)
            }
            val phrase = Phrase(
                content = content,
                isKeep = original.isKeep,
                t9 = t9,
                qwerty = qwerty,
                lx17 = lx17,
                time = System.currentTimeMillis(),
                imageUris = encodeUris(copied.first),
                fileUris = encodeUris(copied.second),
            )
            DataBaseKT.instance.phraseDao().insert(phrase)
            clearPhrasesContent()
            onDone?.invoke()
        }
    }

    fun setExtraData(extra:Phrase) {
        val content = extra.content
        val qwerty = extra.qwerty
        mEtPhrases.setText(content)
        mEtPhrases.setSelection(content.length)
        mEtPhrasesQuickCode.setText(qwerty)
        mEtPhrasesQuickCode.setSelection(qwerty.length)
        setSelectedImageUris(decodeUris(extra.imageUris))
        selectedFileUris.clear()
        selectedFileUris.addAll(decodeUris(extra.fileUris))
        updateAttachmentsUi()
    }

    fun commitText(text:String) {
        val currentView = if(mEtPhrasesQuickCode.isFocused)mEtPhrasesQuickCode else mEtPhrases
        currentView.commitText(text)
    }

    fun sendKeyEvent(keyCode: Int) {
        val currentView = if(mEtPhrasesQuickCode.isFocused)mEtPhrasesQuickCode else mEtPhrases
        when(keyCode){
            KeyEvent.KEYCODE_DEL ->{
                currentView.onKeyDown(keyCode, KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
                currentView.onKeyUp(keyCode, KeyEvent(KeyEvent.ACTION_UP, keyCode))
            }
            KeyEvent.KEYCODE_ENTER ->{
                addPhrasesHandle()
            }
            else -> {
                val unicodeChar: Char = KeyEvent(KeyEvent.ACTION_DOWN, keyCode).unicodeChar.toChar()
                if (unicodeChar != Character.MIN_VALUE)currentView.commitText(unicodeChar.toString())
            }
        }
    }

    fun updateTheme(theme: Theme) {
        val keyTextColor = ThemeManager.activeTheme.keyTextColor
        setBackgroundColor(theme.barColor)
        editContainerBackground.setColor(Color.parseColor("#FFE0B2"))
        editContainerBackground.cornerRadius = dp(12).toFloat()
        mEtPhrases.setTextColor(keyTextColor)
        mEtPhrases.setHintTextColor(keyTextColor)
        mEtPhrasesTips.setTextColor(keyTextColor)
        mEtPhrasesQuickCode.setTextColor(keyTextColor)
        addFileButtonBackground.setColor(if (theme.isDark) Color.parseColor("#2A2A2A") else Color.parseColor("#F0F0F0"))
        addFileIcon.setColorFilter(keyTextColor)
        addFileText.setTextColor(keyTextColor)
        addFileText.alpha = 0.6f
    }

    fun getSelectedImageUris(): List<Uri> = selectedImageUris.toList()

    fun setSelectedImageUris(uris: List<Uri>) {
        selectedImageUris.clear()
        selectedImageUris.addAll(uris)
        updateAttachmentsUi()
    }

    fun addSelectedFileUri(uri: Uri) {
        val total = selectedImageUris.size + selectedFileUris.size
        if (total >= maxAttachments) {
            context.toast("最多选择 $maxAttachments 个附件")
            return
        }
        if (selectedFileUris.contains(uri) || selectedImageUris.contains(uri)) return
        selectedFileUris.add(uri)
        updateAttachmentsUi()
    }

    private fun updateAttachmentsUi() {
        val items = buildAttachments()
        attachmentsAdapter.setItems(items)
        val hasAttachments = items.isNotEmpty()
        addFileEmptyLayout.visibility = if (hasAttachments) GONE else VISIBLE
        attachmentsRecyclerView.visibility = if (hasAttachments) VISIBLE else GONE
    }

    private inner class AttachmentsAdapter(
        private val onAddClick: () -> Unit,
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        private val viewTypeImage = 1
        private val viewTypeDoc = 2
        private val viewTypeAdd = 3
        private var items: List<AttachmentItem> = emptyList()

        fun setItems(newItems: List<AttachmentItem>) {
            items = newItems
            notifyDataSetChanged()
        }

        override fun getItemViewType(position: Int): Int {
            if (position == items.size) return viewTypeAdd
            return when (items[position].type) {
                AttachmentType.Doc -> viewTypeDoc
                else -> viewTypeImage
            }
        }

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return when (viewType) {
                viewTypeAdd -> {
                    val boxBg = GradientDrawable().apply {
                        setColor(if (ThemeManager.activeTheme.isDark) Color.parseColor("#2A2A2A") else Color.parseColor("#F0F0F0"))
                        cornerRadius = dp(10).toFloat()
                    }
                    val root = FrameLayout(parent.context).apply {
                        background = boxBg
                        isClickable = true
                        isFocusable = true
                        setOnClickListener { onAddClick() }
                    }
                    val icon = ImageView(parent.context).apply {
                        setImageResource(R.drawable.ic_menu_plus)
                        setColorFilter(ThemeManager.activeTheme.keyTextColor)
                    }
                    root.addView(icon, FrameLayout.LayoutParams(dp(18), dp(18), Gravity.CENTER))
                    val lp = RecyclerView.LayoutParams(dp(44), dp(44)).apply {
                        marginEnd = dp(10)
                    }
                    root.layoutParams = lp
                    object : RecyclerView.ViewHolder(root) {}
                }
                viewTypeDoc -> {
                    val root = LinearLayout(parent.context).apply {
                        orientation = LinearLayout.HORIZONTAL
                        isClickable = true
                        isFocusable = true
                        val paddingH = dp(12)
                        val paddingV = dp(8)
                        setPadding(paddingH, paddingV, paddingH, paddingV)
                    }
                    val tileBg = GradientDrawable().apply {
                        val isDark = ThemeManager.activeTheme.isDark
                        setColor(if (isDark) Color.parseColor("#303030") else Color.parseColor("#F3F5F7"))
                        cornerRadius = dp(10).toFloat()
                    }
                    root.background = tileBg
                    root.clipToOutline = true
                    val icon = ImageView(parent.context).apply {
                        scaleType = ImageView.ScaleType.CENTER_INSIDE
                    }
                    val iconLp = LinearLayout.LayoutParams(dp(28), dp(28)).apply {
                        marginEnd = dp(8)
                    }
                    root.addView(icon, iconLp)
                    val label = TextView(parent.context).apply {
                        textSize = 14f
                        setTextColor(ThemeManager.activeTheme.keyTextColor)
                        maxLines = 1
                    }
                    val labelLp = LinearLayout.LayoutParams(0, wrapContent, 1f)
                    root.addView(label, labelLp)
                    val lp = RecyclerView.LayoutParams(RecyclerView.LayoutParams.WRAP_CONTENT, RecyclerView.LayoutParams.WRAP_CONTENT).apply {
                        marginEnd = dp(10)
                    }
                    root.layoutParams = lp
                    DocVH(root, icon, label)
                }
                else -> {
                    val root = FrameLayout(parent.context).apply {
                        isClickable = true
                        isFocusable = true
                    }
                    val image = ImageView(parent.context).apply {
                        scaleType = ImageView.ScaleType.CENTER_CROP
                    }
                    val bg = GradientDrawable().apply {
                        setColor(Color.parseColor("#00000000"))
                        cornerRadius = dp(10).toFloat()
                    }
                    root.background = bg
                    root.clipToOutline = true
                    root.addView(image, FrameLayout.LayoutParams(dp(44), dp(44), Gravity.CENTER))
                    val lp = RecyclerView.LayoutParams(dp(44), dp(44)).apply {
                        marginEnd = dp(10)
                    }
                    root.layoutParams = lp
                    ImageVH(root, image)
                }
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            if (position >= items.size) return
            val item = items[position]
            when (holder) {
                is ImageVH -> holder.bind(item.uri)
                is DocVH -> holder.bind(item)
            }
        }

        override fun getItemCount(): Int = items.size + 1

        private inner class ImageVH(
            root: FrameLayout,
            private val image: ImageView,
        ) : RecyclerView.ViewHolder(root) {
            fun bind(uri: Uri) {
                val targetPx = dp(44)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    try {
                        val bm = context.contentResolver.loadThumbnail(uri, Size(targetPx, targetPx), null)
                        image.setImageDrawable(BitmapDrawable(resources, bm))
                        return
                    } catch (_: Throwable) {
                    }
                }
                image.setImageURI(uri)
            }
        }

        private inner class DocVH(
            root: View,
            private val icon: ImageView,
            private val label: TextView,
        ) : RecyclerView.ViewHolder(root) {
            fun bind(item: AttachmentItem) {
                val rawName = queryDisplayName(item.uri)
                val displayName = rawName
                    ?.substringBeforeLast('.', rawName)
                    ?.takeIf { it.isNotBlank() }
                    ?: item.label
                    ?: "文件"
                label.text = if (displayName.length <= 5) displayName else displayName.take(5) + "..."
                val iconRes = when (item.label) {
                    "PDF" -> R.drawable.ic_doc_pdf
                    "DOC", "WORD" -> R.drawable.ic_doc_word
                    "XLS", "EXCEL" -> R.drawable.ic_doc_excel
                    "PPT" -> R.drawable.ic_doc_ppt
                    else -> R.drawable.ic_doc_word
                }
                icon.setImageResource(iconRes)
            }
        }
    }

    private enum class AttachmentType {
        Image,
        Video,
        Doc,
    }

    private data class AttachmentItem(
        val uri: Uri,
        val type: AttachmentType,
        val label: String?,
    )

    private fun buildAttachments(): List<AttachmentItem> {
        val list = ArrayList<AttachmentItem>(selectedImageUris.size + selectedFileUris.size)
        selectedImageUris.forEach { uri ->
            list.add(AttachmentItem(uri, AttachmentType.Image, null))
        }
        selectedFileUris.forEach { uri ->
            val (type, label) = inferFileTypeAndLabel(uri)
            list.add(AttachmentItem(uri, type, label))
        }
        return list
    }

    private fun inferFileTypeAndLabel(uri: Uri): Pair<AttachmentType, String?> {
        val mime = runCatching { context.contentResolver.getType(uri) }.getOrNull().orEmpty()
        if (mime.startsWith("image/")) return AttachmentType.Image to null
        if (mime.startsWith("video/")) return AttachmentType.Video to null
        if (mime.contains("pdf")) return AttachmentType.Doc to "PDF"
        if (mime.contains("wordprocessingml") || mime.contains("msword")) return AttachmentType.Doc to "DOC"
        if (mime.contains("spreadsheetml") || mime.contains("ms-excel")) return AttachmentType.Doc to "XLS"
        if (mime.contains("presentationml") || mime.contains("ms-powerpoint")) return AttachmentType.Doc to "PPT"

        val ext = queryDisplayName(uri)
            ?.substringAfterLast('.', "")
            ?.trim()
            ?.uppercase()
            ?.takeIf { it.isNotBlank() }

        return AttachmentType.Doc to (ext?.take(4) ?: "FILE")
    }

    private fun queryDisplayName(uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
            }
        } catch (_: Throwable) {
            null
        }
    }
}

