package org.fcitx.fcitx5.android.data.theme

import android.content.Context
import org.fcitx.fcitx5.android.utils.appContext

/** Server-confirmed cancellations only. Failed/offline requests never replace this snapshot. */
object SkinOwnershipStore {
    private val preferences get() = appContext.getSharedPreferences("skin_ownership", Context.MODE_PRIVATE)
    private fun key() = "revoked:" + ProtectedSkinStore.owner()

    fun update(owner: String, names: Set<String>): Boolean {
        val key = "revoked:" + owner
        val revoked = names - setOf(BundledSystemThemes.LIGHT, BundledSystemThemes.DARK)
        if (preferences.getStringSet(key, emptySet()) == revoked) return false
        check(preferences.edit().putStringSet(key, revoked).commit())
        ProtectedSkinStore.clearMemory()
        return true
    }

    fun denied(name: String): Boolean {
        if (name == BundledSystemThemes.LIGHT || name == BundledSystemThemes.DARK) return false
        return preferences.getStringSet(key(), emptySet())?.contains(name) == true
    }

    fun permitted(theme: Theme): Theme = if (denied(theme.name)) BundledSystemThemes.light else theme
}
