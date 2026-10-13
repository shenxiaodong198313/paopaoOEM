/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2023 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.clipboard

import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.data.prefs.ManagedPreferenceEnum

enum class ClipboardLayoutMode(override val stringRes: Int) : ManagedPreferenceEnum {
    ListView(R.string.clipboard_layout_mode_list),
    GridView(R.string.clipboard_layout_mode_grid),
    FlexboxView(R.string.clipboard_layout_mode_flexbox);
}
