/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2023 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.data.theme

import android.graphics.BitmapFactory
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.InsetDrawable
import android.os.Parcelable
import android.util.LruCache
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable
import org.fcitx.fcitx5.android.utils.DarkenColorFilter
import org.fcitx.fcitx5.android.utils.RectSerializer
import org.fcitx.fcitx5.android.utils.alpha
import org.fcitx.fcitx5.android.utils.appContext
import splitties.dimensions.dp
import java.io.File
import kotlin.math.roundToInt

@Serializable
sealed class Theme : Parcelable {

    enum class KeyImageRole {
        Normal,
        Alternative,
        Shift,
        Backspace,
        NumberMode,
        Language,
        Space,
        Enter
    }

    abstract val name: String
    abstract val isDark: Boolean

    abstract val backgroundColor: Int
    abstract val barColor: Int
    abstract val keyboardColor: Int

    abstract val keyBackgroundColor: Int
    abstract val keyTextColor: Int

    //  Color of candidate text
    abstract val candidateTextColor: Int
    abstract val candidateLabelColor: Int
    abstract val candidateCommentColor: Int

    abstract val altKeyBackgroundColor: Int
    abstract val altKeyTextColor: Int

    abstract val accentKeyBackgroundColor: Int
    abstract val accentKeyTextColor: Int

    abstract val keyPressHighlightColor: Int
    abstract val keyShadowColor: Int

    abstract val popupBackgroundColor: Int
    abstract val popupTextColor: Int

    abstract val spaceBarColor: Int
    abstract val dividerColor: Int
    abstract val clipboardEntryColor: Int

    abstract val genericActiveBackgroundColor: Int
    abstract val genericActiveForegroundColor: Int

    open fun backgroundDrawable(keyBorder: Boolean = false): Drawable {
        return ColorDrawable(if (keyBorder) backgroundColor else keyboardColor)
    }

    @Serializable
    @Parcelize
    data class Custom(
        override val name: String,
        override val isDark: Boolean,
        /**
         * absolute paths of cropped and src png files
         */
        val backgroundImage: CustomBackground?,
        val toolbarImage: CustomBackground? = null,
        val preeditImage: CustomBackground? = null,
        val keyImage: CustomBackground? = null,
        val altKeyImage: CustomBackground? = null,
        // Kept for backward compatibility with old exported themes.
        val accentKeyImage: CustomBackground? = null,
        val shiftKeyImage: CustomBackground? = null,
        val backspaceKeyImage: CustomBackground? = null,
        val numberModeKeyImage: CustomBackground? = null,
        val languageKeyImage: CustomBackground? = null,
        val spaceBarImage: CustomBackground? = null,
        val enterKeyImage: CustomBackground? = null,
        override val backgroundColor: Int,
        override val barColor: Int,
        override val keyboardColor: Int,
        override val keyBackgroundColor: Int,
        override val keyTextColor: Int,
        override val candidateTextColor: Int,
        override val candidateLabelColor: Int,
        override val candidateCommentColor: Int,
        override val altKeyBackgroundColor: Int,
        override val altKeyTextColor: Int,
        override val accentKeyBackgroundColor: Int,
        override val accentKeyTextColor: Int,
        override val keyPressHighlightColor: Int,
        override val keyShadowColor: Int,
        override val popupBackgroundColor: Int,
        override val popupTextColor: Int,
        override val spaceBarColor: Int,
        override val dividerColor: Int,
        override val clipboardEntryColor: Int,
        override val genericActiveBackgroundColor: Int,
        override val genericActiveForegroundColor: Int,
        val toolbarTopInsetDp: Int = 0,
        val toolbarHorizontalInsetDp: Int = 0,
        val renderStyle: SkinRenderStyle? = null,
        val sidebarImage: CustomBackground? = null,
        val keyImageOverrides: Map<String, CustomBackground> = emptyMap(),
        val keyColorOverrides: Map<String, Int> = emptyMap(),
        val toolbarIcons: Map<String, CustomBackground> = emptyMap(),
        val keyForegrounds: Map<String, CustomBackground> = emptyMap(),
        val keyDecorations: Map<String, SkinKeyDecoration> = emptyMap(),
        val keyStateImages: Map<String, SkinKeyStateImages> = emptyMap(),
        val keyAnimations: Map<String, SkinKeySpriteAnimation> = emptyMap(),
        val revision: Long = 1,
        val minimumSkinEngine: Int = 1,
        val skinSound: SkinSound? = null
    ) : Theme() {
        init {
            val selector = Regex("^(?:(?:qwerty|t9|number)/)?(?:role:[a-z_]+|r[1-4]c(?:10|[1-9])|letter:[a-z]|digit:[0-9]|t9:[1-9])$")
            require(keyStateImages.size <= 160 && keyStateImages.keys.all { selector.matches(it) || it == "t9/sidebar" || it == "number/sidebar" })
            require(keyAnimations.size <= 16 && keyAnimations.keys.all(selector::matches))
        }
        @Parcelize
        @Serializable
        data class CustomBackground(
            val croppedFilePath: String,
            val srcFilePath: String,
            val brightness: Int = 70,
            val cropRect: @Serializable(RectSerializer::class) Rect?,
            val cropRotation: Int = 0,
            val slice: SkinImageSlice? = null,
            val patternFill: SkinPatternFill? = null,
            // Source-pixel silhouette, independent of the key slot and its cast shadow.
            // Useful for authored bitmap caps whose extraction has a residual outer matte.
            val sourceCornerRadiusPx: Float? = null
        ) : Parcelable {
            init {
                require(sourceCornerRadiusPx == null ||
                    (sourceCornerRadiusPx.isFinite() && sourceCornerRadiusPx in 0f..8192f))
            }
            fun toDrawable(context: android.content.Context = appContext): Drawable? {
                val cropped = File(croppedFilePath)
                if (!cropped.exists()) return null
                val bitmap = loadBitmap(cropped) ?: return null
                val patternBitmap = patternFill?.image?.let { loadBitmap(File(it.croppedFilePath)) }
                val image = slice?.let { SlicedSkinDrawable(bitmap, it, context.resources.displayMetrics.densityDpi, patternFill, patternBitmap) }
                    ?: BitmapDrawable(context.resources, bitmap)
                return (sourceCornerRadiusPx?.let {
                    SourceClippedSkinDrawable(image, bitmap.width, bitmap.height, it, slice, context.resources.displayMetrics.densityDpi)
                } ?: image).apply {
                    colorFilter = DarkenColorFilter(100 - brightness)
                }
            }

            companion object {
                private val bitmapCache = object : LruCache<String, android.graphics.Bitmap>(
                    (Runtime.getRuntime().maxMemory() / 1024L / 8L).coerceAtMost(32L * 1024L)
                        .toInt()
                ) {
                    override fun sizeOf(key: String, value: android.graphics.Bitmap): Int {
                        return value.allocationByteCount / 1024
                    }
                }

                fun clearBitmapCache() { bitmapCache.evictAll() }

                private fun loadBitmap(file: File): android.graphics.Bitmap? {
                    val protected = file.name.endsWith(".psk")
                    if (protected && !ProtectedSkinStore.authorized(file)) return null
                    val cacheKey = "${if (protected) ProtectedSkinStore.owner() else ""}:${file.absolutePath}:${file.lastModified()}:${file.length()}"
                    bitmapCache.get(cacheKey)?.let { return it }
                    val bitmap = if (protected) runCatching {
                        val bytes = ProtectedSkinStore.readImage(file)
                        try { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) } finally { bytes.fill(0) }
                    }.getOrNull() else BitmapFactory.decodeFile(file.absolutePath)
                    return bitmap?.also {
                        bitmapCache.put(cacheKey, it)
                    }
                }
            }
        }

        override fun backgroundDrawable(keyBorder: Boolean): Drawable {
            val config = renderStyle?.localConfiguration
            if (config?.background == "transparent") return ColorDrawable(android.graphics.Color.TRANSPARENT)
            val original = backgroundImage?.toDrawable() ?: super.backgroundDrawable(keyBorder)
            return if (config?.background == "gradient" && config.gradientRange > 0) SkinConfiguredBackground(original, config.gradientRange) else original
        }

        fun barDrawable(keyBorder: Boolean, context: android.content.Context = appContext, keyInsets: Pair<Int, Int>? = null): Drawable {
            if (renderStyle?.localConfiguration?.toolbarVisible == false) return ColorDrawable(android.graphics.Color.TRANSPARENT)
            val original = toolbarImage?.toDrawable(context) ?: ColorDrawable(
                if (keyBorder) backgroundColor else barColor
            )
            val config = renderStyle?.localConfiguration
            val reservedPreedit = renderStyle?.reservedPreeditDp ?: 0
            val topGap = if (renderStyle?.customToolbarLayout == true)
                renderStyle.topSpacingHeightDp(toolbarTopInsetDp) else toolbarTopInsetDp.coerceAtLeast(0)
            val legacyDefault = if (renderStyle?.customToolbarLayout == true) toolbarHorizontalInsetDp.coerceIn(0, 36) else 0
            val aligned = keyInsets?.takeIf { config?.followsKeyEdges(legacyDefault) != false }
            fun withPreedit(bar: Drawable): Drawable {
                val artwork = preeditImage?.takeIf { reservedPreedit > 0 }?.toDrawable(context) ?: return bar
                return android.graphics.drawable.LayerDrawable(arrayOf(bar, artwork)).apply {
                    paddingMode = android.graphics.drawable.LayerDrawable.PADDING_MODE_STACK
                    setLayerHeight(1, context.dp(reservedPreedit))
                    setLayerGravity(1, android.view.Gravity.TOP or android.view.Gravity.FILL_HORIZONTAL)
                    setLayerInsetLeft(1, aligned?.first ?: context.dp(config?.toolbarLeft ?: toolbarHorizontalInsetDp.coerceAtLeast(0)))
                    setLayerInsetRight(1, aligned?.second ?: context.dp(config?.toolbarRight ?: toolbarHorizontalInsetDp.coerceAtLeast(0)))
                }
            }
            if (aligned != null) return withPreedit(InsetDrawable(SkinInkFitDrawable(original), aligned.first,
                context.dp(topGap + reservedPreedit), aligned.second, 0))
            if (config != null) return withPreedit(InsetDrawable(original, context.dp(config.toolbarLeft),
                context.dp(topGap + reservedPreedit), context.dp(config.toolbarRight), 0))
            val base = original
            if (topGap <= 0 && toolbarHorizontalInsetDp <= 0 && reservedPreedit == 0) {
                return withPreedit(base)
            }
            val horizontalInset = context.dp(if (renderStyle?.skinLabStandard == true && !renderStyle.customToolbarLayout) {
                (if (SkinLabStandard.landscape(context)) 6f else 3f) +
                    (if (renderStyle.customKeyFaceInsets) renderStyle.keyVisualHorizontalInsetDp ?: 0f else SkinLabStandard.gapX(context) / 2)
            } else toolbarHorizontalInsetDp.toFloat()).roundToInt()
            val topInset = context.dp(topGap + reservedPreedit)
            return withPreedit(InsetDrawable(base, horizontalInset, topInset, horizontalInset, 0))
        }

        fun keyDrawable(role: KeyImageRole, keyId: String? = null): Drawable? {
            return keyDrawable(role, listOfNotNull(keyId))
        }

        fun keyDrawable(
            role: KeyImageRole,
            keyIds: List<String>,
            context: android.content.Context = appContext,
            colorDrawable: (Int) -> Drawable = { android.graphics.drawable.ColorDrawable(it) }
        ): Drawable? {
            for (id in keyIds) {
                keyImageOverrides[id]?.toDrawable(context)?.let { return it }
                keyColorOverrides[id]?.let { return colorDrawable(it) }
            }
            return keyImageForRole(role)?.toDrawable(context)
        }

        private fun keyImageForRole(role: KeyImageRole): CustomBackground? {
            return when (role) {
                KeyImageRole.Normal -> keyImage
                KeyImageRole.Alternative -> altKeyImage ?: keyImage
                KeyImageRole.Shift -> shiftKeyImage ?: altKeyImage ?: keyImage
                KeyImageRole.Backspace -> backspaceKeyImage ?: altKeyImage ?: keyImage
                KeyImageRole.NumberMode -> numberModeKeyImage ?: altKeyImage ?: keyImage
                KeyImageRole.Language -> languageKeyImage ?: altKeyImage ?: keyImage
                KeyImageRole.Space -> spaceBarImage ?: altKeyImage ?: keyImage
                KeyImageRole.Enter -> enterKeyImage ?: accentKeyImage ?: altKeyImage ?: keyImage
            }
        }

        fun allImageAssets(): List<CustomBackground> {
            fun nested(asset: CustomBackground): List<CustomBackground> = listOf(asset) +
                (asset.patternFill?.image?.let(::nested) ?: emptyList())
            return (listOfNotNull(
                backgroundImage,
                toolbarImage,
                preeditImage,
                keyImage,
                altKeyImage,
                accentKeyImage,
                shiftKeyImage,
                backspaceKeyImage,
                numberModeKeyImage,
                languageKeyImage,
                spaceBarImage,
                enterKeyImage,
                sidebarImage
            ) + keyImageOverrides.values + toolbarIcons.values + keyForegrounds.values + keyDecorations.values.map { it.image } +
                keyStateImages.values.flatMap { listOfNotNull(it.pressed, it.released) } + keyAnimations.values.flatMap { it.frames } +
                renderStyle?.keyPressEffect?.particleSprites.orEmpty())
                .flatMap(::nested).distinctBy { it.croppedFilePath to it.srcFilePath }
        }

        fun mapImageAssets(
            mapper: (CustomBackground) -> CustomBackground
        ): Custom {
            fun transform(asset: CustomBackground): CustomBackground = mapper(asset.copy(
                patternFill = asset.patternFill?.let { it.copy(image = transform(it.image)) }
            ))
            return copy(
                backgroundImage = backgroundImage?.let(::transform),
                toolbarImage = toolbarImage?.let(::transform),
                preeditImage = preeditImage?.let(::transform),
                keyImage = keyImage?.let(::transform),
                altKeyImage = altKeyImage?.let(::transform),
                accentKeyImage = accentKeyImage?.let(::transform),
                shiftKeyImage = shiftKeyImage?.let(::transform),
                backspaceKeyImage = backspaceKeyImage?.let(::transform),
                numberModeKeyImage = numberModeKeyImage?.let(::transform),
                languageKeyImage = languageKeyImage?.let(::transform),
                spaceBarImage = spaceBarImage?.let(::transform),
                enterKeyImage = enterKeyImage?.let(::transform),
                sidebarImage = sidebarImage?.let(::transform),
                keyImageOverrides = keyImageOverrides.mapValues { transform(it.value) },
                toolbarIcons = toolbarIcons.mapValues { transform(it.value) },
                keyForegrounds = keyForegrounds.mapValues { transform(it.value) },
                renderStyle = renderStyle?.let { style ->
                    val effect = style.keyPressEffect
                    if (effect == null || effect.particleSprites.isEmpty()) style else
                        style.copy(keyPressEffect = effect.copy(particleSprites = effect.particleSprites.map(::transform)))
                },
                keyDecorations = keyDecorations.mapValues { (_, decoration) ->
                    decoration.copy(image = transform(decoration.image))
                },
                keyStateImages = keyStateImages.mapValues { (_, images) ->
                    images.copy(pressed = transform(images.pressed), released = images.released?.let(::transform))
                },
                keyAnimations = keyAnimations.mapValues { (_, animation) -> animation.copy(frames = animation.frames.map(::transform)) }
            )
        }

    }

    @Parcelize
    data class Builtin(
        override val name: String,
        override val isDark: Boolean,
        override val backgroundColor: Int,
        override val barColor: Int,
        override val keyboardColor: Int,
        override val keyBackgroundColor: Int,
        override val keyTextColor: Int,
        override val candidateTextColor: Int,
        override val candidateLabelColor: Int,
        override val candidateCommentColor: Int,
        override val altKeyBackgroundColor: Int,
        override val altKeyTextColor: Int,
        override val accentKeyBackgroundColor: Int,
        override val accentKeyTextColor: Int,
        override val keyPressHighlightColor: Int,
        override val keyShadowColor: Int,
        override val popupBackgroundColor: Int,
        override val popupTextColor: Int,
        override val spaceBarColor: Int,
        override val dividerColor: Int,
        override val clipboardEntryColor: Int,
        override val genericActiveBackgroundColor: Int,
        override val genericActiveForegroundColor: Int
    ) : Theme() {

        // an alias to use 0xAARRGGBB color literal in code
        // because kotlin compiler treats `0xff000000` as Long, not Int
        constructor(
            name: String,
            isDark: Boolean,
            backgroundColor: Number,
            barColor: Number,
            keyboardColor: Number,
            keyBackgroundColor: Number,
            keyTextColor: Number,
            candidateTextColor: Number,
            candidateLabelColor: Number,
            candidateCommentColor: Number,
            altKeyBackgroundColor: Number,
            altKeyTextColor: Number,
            accentKeyBackgroundColor: Number,
            accentKeyTextColor: Number,
            keyPressHighlightColor: Number,
            keyShadowColor: Number,
            popupBackgroundColor: Number,
            popupTextColor: Number,
            spaceBarColor: Number,
            dividerColor: Number,
            clipboardEntryColor: Number,
            genericActiveBackgroundColor: Number,
            genericActiveForegroundColor: Number
        ) : this(
            name,
            isDark,
            backgroundColor.toInt(),
            barColor.toInt(),
            keyboardColor.toInt(),
            keyBackgroundColor.toInt(),
            keyTextColor.toInt(),
            candidateTextColor.toInt(),
            candidateLabelColor.toInt(),
            candidateCommentColor.toInt(),
            altKeyBackgroundColor.toInt(),
            altKeyTextColor.toInt(),
            accentKeyBackgroundColor.toInt(),
            accentKeyTextColor.toInt(),
            keyPressHighlightColor.toInt(),
            keyShadowColor.toInt(),
            popupBackgroundColor.toInt(),
            popupTextColor.toInt(),
            spaceBarColor.toInt(),
            dividerColor.toInt(),
            clipboardEntryColor.toInt(),
            genericActiveBackgroundColor.toInt(),
            genericActiveForegroundColor.toInt()
        )

        fun deriveCustomNoBackground(name: String) = Custom(
            name = name,
            isDark = isDark,
            backgroundImage = null,
            toolbarImage = null,
            keyImage = null,
            altKeyImage = null,
            accentKeyImage = null,
            shiftKeyImage = null,
            backspaceKeyImage = null,
            numberModeKeyImage = null,
            languageKeyImage = null,
            spaceBarImage = null,
            enterKeyImage = null,
            backgroundColor = backgroundColor,
            barColor = barColor,
            keyboardColor = keyboardColor,
            keyBackgroundColor = keyBackgroundColor,
            keyTextColor = keyTextColor,
            candidateTextColor = candidateTextColor,
            candidateLabelColor = candidateLabelColor,
            candidateCommentColor = candidateCommentColor,
            altKeyBackgroundColor = altKeyBackgroundColor,
            altKeyTextColor = altKeyTextColor,
            accentKeyBackgroundColor = accentKeyBackgroundColor,
            accentKeyTextColor = accentKeyTextColor,
            keyPressHighlightColor = keyPressHighlightColor,
            keyShadowColor = keyShadowColor,
            popupBackgroundColor = popupBackgroundColor,
            popupTextColor = popupTextColor,
            spaceBarColor = spaceBarColor,
            dividerColor = dividerColor,
            clipboardEntryColor = clipboardEntryColor,
            genericActiveBackgroundColor = genericActiveBackgroundColor,
            genericActiveForegroundColor = genericActiveForegroundColor
        )

        fun deriveCustomBackground(
            name: String,
            croppedBackgroundImage: String,
            originBackgroundImage: String,
            brightness: Int = 70,
            cropBackgroundRect: Rect? = null,
            cropBackgroundRotation: Int = 0
        ) = Custom(
            name = name,
            isDark = isDark,
            backgroundImage = Custom.CustomBackground(
                croppedBackgroundImage,
                originBackgroundImage,
                brightness,
                cropBackgroundRect,
                cropBackgroundRotation
            ),
            toolbarImage = null,
            keyImage = null,
            altKeyImage = null,
            accentKeyImage = null,
            shiftKeyImage = null,
            backspaceKeyImage = null,
            numberModeKeyImage = null,
            languageKeyImage = null,
            spaceBarImage = null,
            enterKeyImage = null,
            backgroundColor = backgroundColor,
            barColor = barColor,
            keyboardColor = keyboardColor,
            keyBackgroundColor = keyBackgroundColor,
            keyTextColor = keyTextColor,
            candidateTextColor = candidateTextColor,
            candidateLabelColor = candidateLabelColor,
            candidateCommentColor = candidateCommentColor,
            altKeyBackgroundColor = altKeyBackgroundColor,
            altKeyTextColor = altKeyTextColor,
            accentKeyBackgroundColor = accentKeyBackgroundColor,
            accentKeyTextColor = accentKeyTextColor,
            keyPressHighlightColor = keyPressHighlightColor,
            keyShadowColor = keyShadowColor,
            popupBackgroundColor = popupBackgroundColor,
            popupTextColor = popupTextColor,
            spaceBarColor = spaceBarColor,
            dividerColor = dividerColor,
            clipboardEntryColor = clipboardEntryColor,
            genericActiveBackgroundColor = genericActiveBackgroundColor,
            genericActiveForegroundColor = genericActiveForegroundColor
        )
    }

    @Parcelize
    data class Monet(
        override val name: String,
        override val isDark: Boolean,
        override val backgroundColor: Int,
        override val barColor: Int,
        override val keyboardColor: Int,
        override val keyBackgroundColor: Int,
        override val keyTextColor: Int,
        override val candidateTextColor: Int,
        override val candidateLabelColor: Int,
        override val candidateCommentColor: Int,
        override val altKeyBackgroundColor: Int,
        override val altKeyTextColor: Int,
        override val accentKeyBackgroundColor: Int,
        override val accentKeyTextColor: Int,
        override val keyPressHighlightColor: Int,
        override val keyShadowColor: Int,
        override val popupBackgroundColor: Int,
        override val popupTextColor: Int,
        override val spaceBarColor: Int,
        override val dividerColor: Int,
        override val clipboardEntryColor: Int,
        override val genericActiveBackgroundColor: Int,
        override val genericActiveForegroundColor: Int
    ) : Theme() {
        constructor(
            isDark: Boolean,
            surfaceContainer: Int,
            surfaceContainerHigh: Int,
            surfaceBright: Int,
            onSurface: Int,
            primary: Int,
            onPrimary: Int,
            secondaryContainer: Int,
            onSurfaceVariant: Int,
        ) : this(
            name = "Monet" + if (isDark) "Dark" else "Light",
            isDark = isDark,
            backgroundColor = surfaceContainer,
            barColor = surfaceContainerHigh,
            keyboardColor = surfaceContainer,
            keyBackgroundColor = surfaceBright,
            keyTextColor = onSurface,
            candidateTextColor = onSurface,
            candidateLabelColor = onSurface,
            candidateCommentColor = onSurfaceVariant,
            altKeyBackgroundColor = secondaryContainer,
            altKeyTextColor = onSurfaceVariant,
            accentKeyBackgroundColor = primary,
            accentKeyTextColor = onPrimary,
            keyPressHighlightColor = onSurface.alpha(if (isDark) 0.2f else 0.12f),
            keyShadowColor = 0x000000,
            popupBackgroundColor = surfaceContainer,
            popupTextColor = onSurface,
            spaceBarColor = surfaceBright,
            dividerColor = surfaceBright,
            clipboardEntryColor = surfaceBright,
            genericActiveBackgroundColor = primary,
            genericActiveForegroundColor = onPrimary
        )

        @OptIn(ExperimentalStdlibApi::class)
        fun toCustom() = Custom(
            name = name + "#" + this.accentKeyBackgroundColor.toHexString(), // Use primary color as identifier
            isDark = isDark,
            backgroundImage = null,
            toolbarImage = null,
            keyImage = null,
            altKeyImage = null,
            accentKeyImage = null,
            shiftKeyImage = null,
            backspaceKeyImage = null,
            numberModeKeyImage = null,
            languageKeyImage = null,
            spaceBarImage = null,
            enterKeyImage = null,
            backgroundColor = backgroundColor,
            barColor = barColor,
            keyboardColor = keyboardColor,
            keyBackgroundColor = keyBackgroundColor,
            keyTextColor = keyTextColor,
            candidateTextColor = candidateTextColor,
            candidateLabelColor = candidateLabelColor,
            candidateCommentColor = candidateCommentColor,
            altKeyBackgroundColor = altKeyBackgroundColor,
            altKeyTextColor = altKeyTextColor,
            accentKeyBackgroundColor = accentKeyBackgroundColor,
            accentKeyTextColor = accentKeyTextColor,
            keyPressHighlightColor = keyPressHighlightColor,
            keyShadowColor = keyShadowColor,
            popupBackgroundColor = popupBackgroundColor,
            popupTextColor = popupTextColor,
            spaceBarColor = spaceBarColor,
            dividerColor = dividerColor,
            clipboardEntryColor = clipboardEntryColor,
            genericActiveBackgroundColor = genericActiveBackgroundColor,
            genericActiveForegroundColor = genericActiveForegroundColor
        )
    }
}

/** The immutable source stays intact; the drawable owns a cached, explicit cap silhouette. */
internal class SourceClippedSkinDrawable(
    internal val image: Drawable, private val sourceWidth: Int, private val sourceHeight: Int,
    private val radiusPx: Float, private val slice: SkinImageSlice?, private val targetDensityDpi: Int
) : Drawable() {
    private val path = android.graphics.Path()
    override fun onBoundsChange(bounds: Rect) {
        image.bounds = bounds
        path.reset()
        val capScale = slice?.let {
            val horizontal = skinSliceCaps(sourceWidth, it.left, it.right, it.asymmetricCaps)
            val vertical = skinSliceCaps(sourceHeight, it.top, it.bottom, it.asymmetricCaps)
            minOf(targetDensityDpi.toFloat() / it.sourceDensityDpi.coerceIn(72, 1280),
                bounds.width().toFloat() / (horizontal[0] + horizontal[1]).coerceAtLeast(1),
                bounds.height().toFloat() / (vertical[0] + vertical[1]).coerceAtLeast(1))
        }
        path.addRoundRect(android.graphics.RectF(bounds),
            radiusPx.coerceAtMost(sourceWidth / 2f) * (capScale ?: (bounds.width().toFloat() / sourceWidth)),
            radiusPx.coerceAtMost(sourceHeight / 2f) * (capScale ?: (bounds.height().toFloat() / sourceHeight)),
            android.graphics.Path.Direction.CW)
    }
    override fun draw(canvas: android.graphics.Canvas) {
        val count = canvas.save()
        canvas.clipPath(path)
        image.draw(canvas)
        canvas.restoreToCount(count)
    }
    override fun setAlpha(alpha: Int) { image.alpha = alpha; invalidateSelf() }
    override fun setColorFilter(filter: android.graphics.ColorFilter?) { image.colorFilter = filter; invalidateSelf() }
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
    override fun getIntrinsicWidth(): Int = image.intrinsicWidth
    override fun getIntrinsicHeight(): Int = image.intrinsicHeight
}
