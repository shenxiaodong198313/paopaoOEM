/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2023 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.ui.main.settings.theme

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Parcelable
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.webkit.MimeTypeMap
import android.widget.SeekBar
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.parcelize.Parcelize
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.data.theme.ThemeFilesManager
import org.fcitx.fcitx5.android.data.theme.ThemePreset
import org.fcitx.fcitx5.android.ui.common.withLoadingDialog
import org.fcitx.fcitx5.android.ui.main.CropImageActivity.CropContract
import org.fcitx.fcitx5.android.ui.main.CropImageActivity.CropOption
import org.fcitx.fcitx5.android.ui.main.CropImageActivity.CropResult
import org.fcitx.fcitx5.android.utils.DarkenColorFilter
import org.fcitx.fcitx5.android.utils.item
import org.fcitx.fcitx5.android.utils.parcelable
import splitties.dimensions.dp
import splitties.resources.color
import splitties.resources.resolveThemeAttribute
import splitties.resources.styledColor
import splitties.resources.styledDrawable
import splitties.views.backgroundColor
import splitties.views.bottomPadding
import splitties.views.dsl.appcompat.switch
import splitties.views.dsl.constraintlayout.above
import splitties.views.dsl.constraintlayout.before
import splitties.views.dsl.constraintlayout.below
import splitties.views.dsl.constraintlayout.bottomOfParent
import splitties.views.dsl.constraintlayout.centerHorizontally
import splitties.views.dsl.constraintlayout.constraintLayout
import splitties.views.dsl.constraintlayout.endOfParent
import splitties.views.dsl.constraintlayout.lParams
import splitties.views.dsl.constraintlayout.matchConstraints
import splitties.views.dsl.constraintlayout.packed
import splitties.views.dsl.constraintlayout.startOfParent
import splitties.views.dsl.constraintlayout.topOfParent
import splitties.views.dsl.constraintlayout.topToTopOf
import splitties.views.dsl.core.add
import splitties.views.dsl.core.matchParent
import splitties.views.dsl.core.seekBar
import splitties.views.dsl.core.textView
import splitties.views.dsl.core.view
import splitties.views.dsl.core.wrapContent
import splitties.views.dsl.core.wrapInScrollView
import splitties.views.gravityVerticalCenter
import splitties.views.horizontalPadding
import splitties.views.textAppearance
import splitties.views.topPadding
import java.io.File
import kotlin.math.roundToInt

class CustomThemeActivity : AppCompatActivity() {

    enum class PreviewTarget {
        Background,
        Toolbar,
        Key,
        AltKey,
        Shift,
        Backspace,
        NumberMode,
        Language,
        Space,
        Enter
    }

    sealed interface BackgroundResult : Parcelable {
        @Parcelize
        data class Updated(val theme: Theme.Custom) : BackgroundResult

        @Parcelize
        data class Created(val theme: Theme.Custom) : BackgroundResult

        @Parcelize
        data class Deleted(val name: String) : BackgroundResult
    }

    class Contract : ActivityResultContract<Theme.Custom?, BackgroundResult?>() {
        override fun createIntent(context: Context, input: Theme.Custom?): Intent =
            CustomThemeActivity.createIntent(context, input)

        override fun parseResult(resultCode: Int, intent: Intent?): BackgroundResult? =
            intent?.parcelable(RESULT)
    }

    private val toolbar by lazy {
        view(::Toolbar) {
            backgroundColor = styledColor(android.R.attr.colorPrimary)
            elevation = dp(4f)
        }
    }

    private lateinit var previewUi: KeyboardPreviewUi

    private fun createTextView(@StringRes string: Int? = null, ripple: Boolean = false) = textView {
        if (string != null) {
            setText(string)
        }
        gravity = gravityVerticalCenter
        textAppearance = resolveThemeAttribute(android.R.attr.textAppearanceListItem)
        horizontalPadding = dp(16)
        if (ripple) {
            background = styledDrawable(android.R.attr.selectableItemBackground)
        }
    }

    private val variantLabel by lazy {
        createTextView(R.string.dark_keys, ripple = true)
    }
    private val variantSwitch by lazy {
        switch {
            // Use dark keys by default
            isChecked = false
        }
    }

    private val brightnessLabel by lazy {
        createTextView(R.string.brightness)
    }
    private val brightnessValue by lazy {
        createTextView()
    }
    private val brightnessSeekBar by lazy {
        seekBar {
            max = 100
        }
    }

    private val toolbarTopInsetLabel by lazy {
        createTextView(R.string.toolbar_top_inset)
    }
    private val toolbarTopInsetValue by lazy {
        createTextView()
    }
    private val toolbarTopInsetSeekBar by lazy {
        seekBar {
            max = 24
        }
    }

    private val toolbarHorizontalInsetLabel by lazy {
        createTextView(R.string.toolbar_horizontal_inset)
    }
    private val toolbarHorizontalInsetValue by lazy {
        createTextView()
    }
    private val toolbarHorizontalInsetSeekBar by lazy {
        seekBar {
            max = 32
        }
    }

    private val cropLabel by lazy {
        createTextView(R.string.recrop_image, ripple = true)
    }

    private val scrollView by lazy {
        val lineHeight = dp(48)
        val itemMargin = dp(30)
        constraintLayout {
            bottomPadding = dp(24)
            add(previewUi.root, lParams(wrapContent, wrapContent) {
                topOfParent()
                centerHorizontally()
                above(cropLabel, dp(8))
                verticalChainStyle = packed
            })
            add(cropLabel, lParams(matchConstraints, lineHeight) {
                below(previewUi.root)
                centerHorizontally(itemMargin)
                above(variantLabel)
            })
            add(variantLabel, lParams(matchConstraints, lineHeight) {
                below(cropLabel)
                startOfParent(itemMargin)
                before(variantSwitch)
                above(brightnessLabel)
            })
            add(variantSwitch, lParams(wrapContent, lineHeight) {
                topToTopOf(variantLabel)
                endOfParent(itemMargin)
            })
            add(brightnessLabel, lParams(matchConstraints, lineHeight) {
                below(variantLabel)
                startOfParent(itemMargin)
                before(brightnessValue)
                above(brightnessSeekBar)
            })
            add(brightnessValue, lParams(wrapContent, lineHeight) {
                topToTopOf(brightnessLabel)
                endOfParent(itemMargin)
            })
            add(brightnessSeekBar, lParams(matchConstraints, wrapContent) {
                below(brightnessLabel)
                centerHorizontally(itemMargin)
                above(toolbarTopInsetLabel)
            })
            add(toolbarTopInsetLabel, lParams(matchConstraints, lineHeight) {
                below(brightnessSeekBar)
                startOfParent(itemMargin)
                before(toolbarTopInsetValue)
                above(toolbarTopInsetSeekBar)
            })
            add(toolbarTopInsetValue, lParams(wrapContent, lineHeight) {
                topToTopOf(toolbarTopInsetLabel)
                endOfParent(itemMargin)
            })
            add(toolbarTopInsetSeekBar, lParams(matchConstraints, wrapContent) {
                below(toolbarTopInsetLabel)
                centerHorizontally(itemMargin)
                above(toolbarHorizontalInsetLabel)
            })
            add(toolbarHorizontalInsetLabel, lParams(matchConstraints, lineHeight) {
                below(toolbarTopInsetSeekBar)
                startOfParent(itemMargin)
                before(toolbarHorizontalInsetValue)
                above(toolbarHorizontalInsetSeekBar)
            })
            add(toolbarHorizontalInsetValue, lParams(wrapContent, lineHeight) {
                topToTopOf(toolbarHorizontalInsetLabel)
                endOfParent(itemMargin)
            })
            add(toolbarHorizontalInsetSeekBar, lParams(matchConstraints, wrapContent) {
                below(toolbarHorizontalInsetLabel)
                centerHorizontally(itemMargin)
                bottomOfParent()
            })
        }.wrapInScrollView {
            isFillViewport = true
        }
    }

    private val ui by lazy {
        constraintLayout {
            add(toolbar, lParams(matchParent, wrapContent) {
                topOfParent()
                centerHorizontally()
            })
            add(scrollView, lParams {
                below(toolbar)
                centerHorizontally()
                bottomOfParent()
            })
        }
    }

    private var newCreated = true

    private lateinit var theme: Theme.Custom
    private lateinit var previewBaseTheme: Theme.Custom
    private var previewTarget: PreviewTarget = PreviewTarget.Background

    private data class CropTargetSpec(
        val width: Int,
        val height: Int
    )

    private class BackgroundStates {
        lateinit var launcher: ActivityResultLauncher<CropOption>
        var srcImageExtension: String? = null
        var srcImageBuffer: ByteArray? = null
        var cropRect: Rect? = null
        var cropRotation: Int = 0
        lateinit var croppedBitmap: Bitmap
        lateinit var filteredDrawable: BitmapDrawable
        lateinit var srcImageFile: File
        lateinit var croppedImageFile: File

        fun hasChosenImage(): Boolean {
            return this::croppedBitmap.isInitialized && this::filteredDrawable.isInitialized
        }

        fun hasSourceImage(): Boolean {
            return this::srcImageFile.isInitialized && srcImageFile.exists()
        }

        fun hasCroppedFileRef(): Boolean {
            return this::croppedImageFile.isInitialized
        }

        fun hasSourceFileRef(): Boolean {
            return this::srcImageFile.isInitialized
        }
    }

    private val backgroundStates by lazy { BackgroundStates() }

    private inline fun whenHasBackground(
        block: BackgroundStates.(Theme.Custom.CustomBackground) -> Unit,
    ) {
        if (theme.backgroundImage != null)
            block(backgroundStates, theme.backgroundImage!!)
    }

    private fun BackgroundStates.setKeyVariant(
        background: Theme.Custom.CustomBackground,
        darkKeys: Boolean
    ) {
        val template = if (darkKeys) ThemePreset.TransparentLight else ThemePreset.TransparentDark
        theme = template.deriveCustomBackground(
            theme.name,
            background.croppedFilePath,
            background.srcFilePath,
            brightnessSeekBar.progress,
            background.cropRect,
            background.cropRotation
        )
        previewUi.setTheme(buildPreviewTheme())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // recover from bundle
        val originTheme = intent?.parcelable<Theme.Custom>(ORIGIN_THEME)?.also { t ->
            theme = if (t.backgroundImage == null) {
                val (_, c, s) = ThemeFilesManager.newCustomBackgroundImages()
                backgroundStates.apply {
                    croppedImageFile = c
                    srcImageFile = s
                }
                t.copy(
                    backgroundImage = Theme.Custom.CustomBackground(
                        croppedFilePath = c.path,
                        srcFilePath = s.path,
                        cropRect = null
                    )
                )
            } else {
                t
            }
            whenHasBackground {
                if (!hasCroppedFileRef()) {
                    croppedImageFile = File(it.croppedFilePath)
                }
                if (!hasSourceFileRef()) {
                    srcImageFile = File(it.srcFilePath)
                }
                cropRect = it.cropRect
                cropRotation = it.cropRotation
                if (File(it.croppedFilePath).exists()) {
                    croppedBitmap = BitmapFactory.decodeFile(it.croppedFilePath)
                    filteredDrawable = BitmapDrawable(resources, croppedBitmap)
                }
            }
            newCreated = false
        }
        // create new
        if (originTheme == null) {
            val (n, c, s) = ThemeFilesManager.newCustomBackgroundImages()
            backgroundStates.apply {
                croppedImageFile = c
                srcImageFile = s
            }
            // Use dark keys by default
            theme = ThemePreset.TransparentDark.deriveCustomBackground(n, c.path, s.path)
        }
        previewTarget = intent?.getStringExtra(PREVIEW_TARGET)
            ?.let { runCatching { PreviewTarget.valueOf(it) }.getOrNull() }
            ?: PreviewTarget.Background
        previewBaseTheme = intent?.parcelable(PREVIEW_THEME) ?: theme
        toolbarTopInsetSeekBar.progress = theme.toolbarTopInsetDp
        toolbarHorizontalInsetSeekBar.progress = theme.toolbarHorizontalInsetDp
        previewUi = KeyboardPreviewUi(this, theme)
        enableEdgeToEdge()
        ViewCompat.setOnApplyWindowInsetsListener(ui) { _, windowInsets ->
            val statusBars = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars())
            val navBars = windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars())
            ui.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                leftMargin = navBars.left
                rightMargin = navBars.right
            }
            toolbar.topPadding = statusBars.top
            scrollView.bottomPadding = navBars.bottom
            windowInsets
        }
        // show Activity label on toolbar
        setSupportActionBar(toolbar)
        // show back button
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        setContentView(ui)
        whenHasBackground { background ->
            brightnessSeekBar.progress = background.brightness
            variantSwitch.isChecked = !theme.isDark
            launcher = registerForActivityResult(CropContract()) {
                when (it) {
                    CropResult.Fail -> {
                        if (newCreated) {
                            cancel()
                        }
                    }
                    is CropResult.Success -> {
                        if (newCreated || !hasSourceImage()) {
                            srcImageExtension = MimeTypeMap.getSingleton()
                                .getExtensionFromMimeType(contentResolver.getType(it.srcUri))
                            srcImageBuffer =
                                contentResolver.openInputStream(it.srcUri)!!
                                    .use { x -> x.readBytes() }
                        }
                        cropRect = it.rect
                        cropRotation = it.rotation
                        croppedBitmap = it.bitmap
                        filteredDrawable = BitmapDrawable(resources, croppedBitmap)
                        updateState()
                    }
                }
            }
            cropLabel.setOnClickListener {
                launchCrop()
            }
            variantLabel.setOnClickListener {
                variantSwitch.isChecked = !variantSwitch.isChecked
            }
            // attach OnCheckedChangeListener after calling setChecked (isChecked in kotlin)
            variantSwitch.setOnCheckedChangeListener { _, isChecked ->
                setKeyVariant(background, darkKeys = isChecked)
            }
            brightnessSeekBar.setOnSeekBarChangeListener(object :
                SeekBar.OnSeekBarChangeListener {
                override fun onStartTrackingTouch(bar: SeekBar) {}
                override fun onStopTrackingTouch(bar: SeekBar) {}

                override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) {
                    if (fromUser) updateState()
                }
            })
        }
        bindToolbarInsetControls()

        if (newCreated) {
            cropLabel.visibility = View.GONE
            whenHasBackground {
                previewUi.onSizeMeasured = { w, h ->
                    if (w > 0 && h > 0) {
                        launchCrop()
                    }
                }
            }
        } else {
            whenHasBackground {
                if (hasChosenImage()) {
                    cropLabel.setText(R.string.recrop_image)
                    brightnessLabel.visibility = View.VISIBLE
                    variantLabel.visibility = View.VISIBLE
                    variantSwitch.visibility = View.VISIBLE
                    brightnessSeekBar.visibility = View.VISIBLE
                    updateState()
                } else {
                    cropLabel.visibility = View.VISIBLE
                    cropLabel.setText(R.string.choose_image)
                    brightnessLabel.visibility = View.GONE
                    variantLabel.visibility = View.GONE
                    variantSwitch.visibility = View.GONE
                    brightnessSeekBar.visibility = View.GONE
                }
            }
        }

        onBackPressedDispatcher.addCallback {
            cancel()
        }
    }

    private fun cropTargetSpec(): CropTargetSpec {
        val previewWidth = previewUi.intrinsicWidth.coerceAtLeast(1)
        val previewHeight = previewUi.intrinsicHeight.coerceAtLeast(1)
        val rowHeight = (previewHeight * 0.22f).roundToInt().coerceIn(96, 192)
        return when (previewTarget) {
            PreviewTarget.Background -> CropTargetSpec(previewWidth, previewHeight)
            PreviewTarget.Toolbar -> CropTargetSpec(
                width = previewWidth,
                height = (previewHeight * 0.18f).roundToInt().coerceIn(96, 180)
            )
            PreviewTarget.Key -> CropTargetSpec(
                width = (previewWidth * 0.14f).roundToInt().coerceIn(96, 192),
                height = rowHeight
            )
            PreviewTarget.AltKey -> CropTargetSpec(
                width = (previewWidth * 0.18f).roundToInt().coerceIn(120, 224),
                height = rowHeight
            )
            PreviewTarget.Shift,
            PreviewTarget.Backspace,
            PreviewTarget.NumberMode,
            PreviewTarget.Language -> CropTargetSpec(
                width = (previewWidth * 0.22f).roundToInt().coerceIn(144, 288),
                height = rowHeight
            )
            PreviewTarget.Space -> CropTargetSpec(
                width = (previewWidth * 0.46f).roundToInt().coerceIn(320, 720),
                height = rowHeight
            )
            PreviewTarget.Enter -> CropTargetSpec(
                width = (previewWidth * 0.22f).roundToInt().coerceIn(160, 320),
                height = (previewHeight * 0.28f).roundToInt().coerceIn(128, 256)
            )
        }
    }

    private fun BackgroundStates.launchCrop() {
        val target = cropTargetSpec()
        if (newCreated || !hasSourceImage()) {
            launcher.launch(CropOption.New(target.width, target.height))
        } else {
            launcher.launch(
                CropOption.Edit(
                    width = target.width,
                    height = target.height,
                    Uri.fromFile(srcImageFile),
                    initialRect = cropRect,
                    initialRotation = cropRotation
                )
            )
        }
    }

    @SuppressLint("SetTextI18n")
    private fun BackgroundStates.updateState() {
        if (!hasChosenImage()) return
        val progress = brightnessSeekBar.progress
        brightnessValue.text = "$progress%"
        filteredDrawable.colorFilter = DarkenColorFilter(100 - progress)
        persistPreviewBitmap()
        previewUi.setTheme(buildPreviewTheme())
        cropLabel.setText(R.string.recrop_image)
        brightnessLabel.visibility = View.VISIBLE
        variantLabel.visibility = View.VISIBLE
        variantSwitch.visibility = View.VISIBLE
        brightnessSeekBar.visibility = View.VISIBLE
        updateToolbarInsetVisibility()
    }

    private fun BackgroundStates.persistPreviewBitmap() {
        if (!hasChosenImage()) return
        croppedImageFile.parentFile?.mkdirs()
        croppedImageFile.outputStream().use {
            croppedBitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun buildPreviewTheme(): Theme.Custom {
        val liveBackground = theme.backgroundImage?.copy(
            brightness = brightnessSeekBar.progress,
            cropRect = backgroundStates.cropRect,
            cropRotation = backgroundStates.cropRotation
        )
        val baseTheme = if (previewTarget == PreviewTarget.Background) theme else previewBaseTheme
        return when (previewTarget) {
            PreviewTarget.Background -> baseTheme.copy(backgroundImage = liveBackground)
            PreviewTarget.Toolbar -> baseTheme.copy(
                toolbarImage = liveBackground,
                toolbarTopInsetDp = toolbarTopInsetSeekBar.progress,
                toolbarHorizontalInsetDp = toolbarHorizontalInsetSeekBar.progress
            )
            PreviewTarget.Key -> baseTheme.copy(keyImage = liveBackground)
            PreviewTarget.AltKey -> baseTheme.copy(altKeyImage = liveBackground)
            PreviewTarget.Shift -> baseTheme.copy(shiftKeyImage = liveBackground)
            PreviewTarget.Backspace -> baseTheme.copy(backspaceKeyImage = liveBackground)
            PreviewTarget.NumberMode -> baseTheme.copy(numberModeKeyImage = liveBackground)
            PreviewTarget.Language -> baseTheme.copy(languageKeyImage = liveBackground)
            PreviewTarget.Space -> baseTheme.copy(spaceBarImage = liveBackground)
            PreviewTarget.Enter -> baseTheme.copy(enterKeyImage = liveBackground)
        }
    }

    private fun cancel() {
        setResult(
            RESULT_CANCELED,
            Intent().apply { putExtra(RESULT, null as BackgroundResult?) }
        )
        finish()
    }

    private fun done() {
        lifecycleScope.withLoadingDialog(this) {
            whenHasBackground {
                withContext(Dispatchers.IO) {
                    if (!hasChosenImage()) return@withContext
                    croppedImageFile.delete()
                    croppedImageFile.outputStream().use {
                        croppedBitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                    }
                    if (newCreated || srcImageBuffer != null) {
                        if (srcImageExtension != null) {
                            srcImageFile = File("${srcImageFile.absolutePath}.$srcImageExtension")
                            theme = theme.copy(
                                backgroundImage = it.copy(
                                    srcFilePath = srcImageFile.absolutePath
                                )
                            )
                        }
                        srcImageFile.writeBytes(srcImageBuffer!!)
                    }
                }
            }
            setResult(
                RESULT_OK,
                Intent().apply {
                    theme = theme.copy(
                        toolbarTopInsetDp = toolbarTopInsetSeekBar.progress,
                        toolbarHorizontalInsetDp = toolbarHorizontalInsetSeekBar.progress
                    )
                    var newTheme = theme
                    whenHasBackground {
                        newTheme = if (hasChosenImage()) {
                            theme.copy(
                                backgroundImage = it.copy(
                                    brightness = brightnessSeekBar.progress,
                                    cropRect = cropRect,
                                    cropRotation = cropRotation
                                )
                            )
                        } else {
                            theme.copy(backgroundImage = null)
                        }
                    }
                    putExtra(
                        RESULT,
                        if (newCreated)
                            BackgroundResult.Created(newTheme)
                        else
                            BackgroundResult.Updated(newTheme)
                    )
                })
            finish()
        }
    }

    private fun delete() {
        setResult(
            RESULT_OK,
            Intent().apply {
                putExtra(RESULT, BackgroundResult.Deleted(theme.name))
            }
        )
        finish()
    }

    private fun promptDelete() {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_theme)
            .setMessage(getString(R.string.delete_theme_msg, theme.name))
            .setPositiveButton(android.R.string.ok) { _, _ ->
                delete()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    @SuppressLint("SetTextI18n")
    private fun bindToolbarInsetControls() {
        val listener = object : SeekBar.OnSeekBarChangeListener {
            override fun onStartTrackingTouch(bar: SeekBar) {}
            override fun onStopTrackingTouch(bar: SeekBar) {}

            override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                theme = theme.copy(
                    toolbarTopInsetDp = toolbarTopInsetSeekBar.progress,
                    toolbarHorizontalInsetDp = toolbarHorizontalInsetSeekBar.progress
                )
                toolbarTopInsetValue.text = "${toolbarTopInsetSeekBar.progress}dp"
                toolbarHorizontalInsetValue.text =
                    "${toolbarHorizontalInsetSeekBar.progress}dp"
                previewUi.setTheme(buildPreviewTheme())
            }
        }
        toolbarTopInsetSeekBar.setOnSeekBarChangeListener(listener)
        toolbarHorizontalInsetSeekBar.setOnSeekBarChangeListener(listener)
        toolbarTopInsetValue.text = "${toolbarTopInsetSeekBar.progress}dp"
        toolbarHorizontalInsetValue.text = "${toolbarHorizontalInsetSeekBar.progress}dp"
        updateToolbarInsetVisibility()
    }

    private fun updateToolbarInsetVisibility() {
        val visible = if (previewTarget == PreviewTarget.Toolbar) View.VISIBLE else View.GONE
        toolbarTopInsetLabel.visibility = visible
        toolbarTopInsetValue.visibility = visible
        toolbarTopInsetSeekBar.visibility = visible
        toolbarHorizontalInsetLabel.visibility = visible
        toolbarHorizontalInsetValue.visibility = visible
        toolbarHorizontalInsetSeekBar.visibility = visible
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        if (!newCreated) {
            val iconTint = color(R.color.red_400)
            menu.item(R.string.save, R.drawable.ic_baseline_delete_24, iconTint, true) {
                promptDelete()
            }
        }
        val iconTint = styledColor(android.R.attr.colorControlNormal)
        menu.item(R.string.save, R.drawable.ic_baseline_check_24, iconTint, true) {
            done()
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        android.R.id.home -> {
            cancel()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    companion object {
        const val RESULT = "result"
        const val ORIGIN_THEME = "origin_theme"
        const val PREVIEW_THEME = "preview_theme"
        const val PREVIEW_TARGET = "preview_target"

        fun createIntent(
            context: Context,
            input: Theme.Custom?,
            previewTarget: PreviewTarget = PreviewTarget.Background,
            previewTheme: Theme.Custom? = input
        ): Intent {
            return Intent(context, CustomThemeActivity::class.java).apply {
                putExtra(ORIGIN_THEME, input)
                putExtra(PREVIEW_THEME, previewTheme)
                putExtra(PREVIEW_TARGET, previewTarget.name)
            }
        }
    }
}
