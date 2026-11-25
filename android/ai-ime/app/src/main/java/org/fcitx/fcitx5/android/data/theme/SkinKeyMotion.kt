package org.fcitx.fcitx5.android.data.theme

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

/** Authored material response. All values affect paint only, never input timing or hit slots. */
@Serializable
@Parcelize
data class SkinKeyMotion(
    val enabled: Boolean = true,
    val scaleX: Float = .96f,
    val scaleY: Float = .96f,
    val sinkDp: Float = 2f,
    val pressMs: Int = 70,
    val releaseMs: Int = 380,
    val bounce: Float = .5f
) : Parcelable {
    init {
        require(scaleX.isFinite() && scaleX in .88f..1f)
        require(scaleY.isFinite() && scaleY in .88f..1f)
        require(sinkDp.isFinite() && sinkDp in 0f..6f)
        require(pressMs in 0..160 && releaseMs in 80..800)
        require(bounce.isFinite() && bounce in 0f..1.5f)
    }
}

/** Already decoded at keyboard construction; no file access is allowed on a touch. */
@Serializable
@Parcelize
data class SkinKeyStateImages(
    val pressed: Theme.Custom.CustomBackground,
    val released: Theme.Custom.CustomBackground? = null
) : Parcelable

/** One isolated character frame at a time over an immutable keycap. No crossfade or trail. */
@Serializable
@Parcelize
data class SkinKeySpriteAnimation(
    val frames: List<Theme.Custom.CustomBackground>,
    val frameDurationsMs: List<Int>,
    val left: Float = .30f,
    val top: Float = .46f,
    val right: Float = .98f,
    val bottom: Float = .98f,
    val fillsKeyFace: Boolean = false
) : Parcelable {
    init {
        require(frames.size in 3..12 && frameDurationsMs.size == frames.size - 1)
        require(frameDurationsMs.all { it in 16..200 } && frameDurationsMs.sum() <= 1200)
        require(listOf(left, top, right, bottom).all { it.isFinite() && it in 0f..1f })
        require(left < right && top < bottom)
    }
}

/** 0 is the immutable rest frame; a press starts at frame 1 and always ends at rest. */
object SkinSpriteTimeline {
    fun frameAt(elapsedMs: Long, durations: IntArray): Int {
        var remaining = elapsedMs.coerceAtLeast(0)
        durations.forEachIndexed { index, duration ->
            if (remaining < duration) return index + 1
            remaining -= duration
        }
        return 0
    }
}


/** Palette only: popup paths, anchors, font metrics and touch semantics stay unchanged. */
@Serializable
@Parcelize
data class SkinPopupStyle(
    val topColor: Int,
    val bottomColor: Int,
    val textColor: Int,
    val borderColor: Int,
    val borderWidthDp: Float = .8f,
    val shadowColor: Int = 0x49355d58,
    val selectedColor: Int,
    val selectedTextColor: Int
) : Parcelable {
    init {
        require(borderWidthDp.isFinite() && borderWidthDp in 0f..2f)
        require(listOf(topColor, bottomColor, textColor, selectedColor, selectedTextColor).all { (it ushr 24) == 255 })
    }
    companion object {
        fun resolve(theme: Theme): SkinPopupStyle = (theme as? Theme.Custom)?.renderStyle?.popupStyle ?: SkinPopupStyle(
            topColor = if (theme.isDark) theme.popupBackgroundColor else -1,
            bottomColor = if (theme.isDark) theme.popupBackgroundColor else -1,
            textColor = if (theme.isDark) theme.popupTextColor else 0xff000000.toInt(),
            borderColor = 0, borderWidthDp = 0f, shadowColor = 0x4d2d3039,
            selectedColor = 0xff007aff.toInt(), selectedTextColor = -1)
    }
}
