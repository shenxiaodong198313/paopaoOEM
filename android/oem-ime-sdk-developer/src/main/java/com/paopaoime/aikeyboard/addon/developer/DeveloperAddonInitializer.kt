package com.paopaoime.aikeyboard.addon.developer

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.util.Log
import com.paopaoime.aikeyboard.sdk.core.OemAddons

class DeveloperAddonInitializer : ContentProvider() {

    override fun onCreate(): Boolean {
        Log.d("OemDeveloperAddon", "DeveloperAddonInitializer.onCreate")
        OemAddons.install(DeveloperAddonImpl())
        return true
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
