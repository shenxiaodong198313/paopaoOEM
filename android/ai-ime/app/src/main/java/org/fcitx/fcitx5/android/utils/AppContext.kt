/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2024 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.utils

import android.content.Context
import org.fcitx.fcitx5.android.FcitxApplication

private var hostAppContext: Context? = null

fun initAppContext(context: Context) {
    hostAppContext = context.applicationContext
}

val appContext: Context
    get() = hostAppContext ?: FcitxApplication.getInstance().applicationContext
