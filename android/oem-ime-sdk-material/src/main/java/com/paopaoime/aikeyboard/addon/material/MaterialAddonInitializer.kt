package com.paopaoime.aikeyboard.addon.material

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import com.paopaoime.aikeyboard.sdk.core.OemAddons

class MaterialAddonInitializer : ContentProvider() {

    override fun onCreate(): Boolean {
        val addon = MaterialAddonImpl()
        OemAddons.install(addon)
        val context = context
        if (context != null) {
            addon.installDrawerKitRuntime(context)
        }
        return true
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
