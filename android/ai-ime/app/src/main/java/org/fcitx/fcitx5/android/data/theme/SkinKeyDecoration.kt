package org.fcitx.fcitx5.android.data.theme

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

/** Optional static artwork anchored to a key face, drawn above keys without owning input. */
@Serializable
@Parcelize
data class SkinKeyDecoration(
    val image: Theme.Custom.CustomBackground,
    val widthDp: Float,
    val heightDp: Float,
    val anchorX: Float = .5f,
    val anchorY: Float = .5f,
    val offsetXDp: Float = 0f,
    val offsetYDp: Float = 0f,
    val maxOverflowDp: Float = 0f
) : Parcelable {
    init {
        require(widthDp.isFinite() && widthDp in 4f..128f)
        require(heightDp.isFinite() && heightDp in 4f..96f)
        require(anchorX.isFinite() && anchorX in 0f..1f)
        require(anchorY.isFinite() && anchorY in 0f..1f)
        require(offsetXDp.isFinite() && offsetXDp in -48f..48f)
        require(offsetYDp.isFinite() && offsetYDp in -48f..48f)
        require(maxOverflowDp.isFinite() && maxOverflowDp in 0f..24f)
    }
}
