package com.paopaoime.aikeyboard.ime.utils

import android.R
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceScreen
import androidx.core.content.ContextCompat
import splitties.resources.drawable

fun PreferenceScreen.addCategory(title: String, block: PreferenceCategory.() -> Unit) {
    val category = PreferenceCategory(context).apply {
        setTitle(title)
        layoutResource = com.paopaoime.aikeyboard.ime.R.layout.item_settings_category
        isIconSpaceReserved = true
    }
    addPreference(category)
    block.invoke(category)
}

fun PreferenceScreen.addCategory(@StringRes title: Int, block: PreferenceCategory.() -> Unit) {
    val ctx = context
    addCategory(ctx.getString(title), block)
}

fun PreferenceScreen.addCategory(@StringRes title: Int, @DrawableRes icon: Int, block: PreferenceCategory.() -> Unit) {
    val ctx = context
    val category = PreferenceCategory(ctx).apply {
        setTitle(ctx.getString(title))
        layoutResource = com.paopaoime.aikeyboard.ime.R.layout.item_settings_category
        isIconSpaceReserved = true
        setIcon(ctx.drawable(icon)?.apply {
            setTint(ContextCompat.getColor(ctx, com.paopaoime.aikeyboard.ime.R.color.settings_title))
        })
    }
    addPreference(category)
    block.invoke(category)
}

fun PreferenceGroup.addPreference(
    title: String,
    summary: String? = null,
    @DrawableRes icon: Int? = null,
    onClick: (() -> Unit)? = null
) {
    addPreference(Preference(context).apply {
        isSingleLineTitle = false
        layoutResource = com.paopaoime.aikeyboard.ime.R.layout.item_settings_card
        setTitle(title)
        isCopyingEnabled = true
        setSummary(summary)
        isIconSpaceReserved = false
        onClick?.also {
            setOnPreferenceClickListener { _ ->
                it.invoke()
                true
            }
        }
    })
}

fun PreferenceGroup.addPreference(
    @StringRes title: Int,
    summary: String,
    @DrawableRes icon: Int? = null,
    onClick: (() -> Unit)? = null
) {
    val ctx = context
    addPreference(ctx.getString(title), summary, icon, onClick)
}

fun PreferenceGroup.addPreference(
    @StringRes title: Int,
    @StringRes summary: Int? = null,
    @DrawableRes icon: Int? = null,
    onClick: (() -> Unit)? = null
) {
    val ctx = context
    addPreference(ctx.getString(title), summary?.let(ctx::getString), icon, onClick)
}

