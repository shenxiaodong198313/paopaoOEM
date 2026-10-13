package com.paopaoime.aikeyboard.ime.utils

import androidx.annotation.StringRes
import com.paopaoime.aikeyboard.ime.application.Launcher

inline fun <T : Throwable> errorT(cons: (String) -> T, @StringRes messageTemplate: Int, messageArg: String? = null): Nothing =
    throw cons(
        messageArg?.let {
            Launcher.instance.context.getString(messageTemplate, it)
        } ?: Launcher.instance.context.getString(
            messageTemplate
        )
    )

fun errorRuntime(@StringRes messageTemplate: Int, messageArg: String? = null): Nothing =
    errorT(::RuntimeException, messageTemplate, messageArg)
