
package com.paopaoime.aikeyboard.ime.ui.activity

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.paopaoime.aikeyboard.ime.R
import com.paopaoime.aikeyboard.ime.application.Launcher

class SettingsViewModel : ViewModel() {

    val toolbarTitle = MutableLiveData(Launcher.instance.context.getString(R.string.ime_app_name))

    val toolbarShadow = MutableLiveData(true)

    fun enableToolbarShadow() {
        toolbarShadow.value = true
    }

    fun disableToolbarShadow() {
        toolbarShadow.value = false
    }

    override fun onCleared() {
    }
}

