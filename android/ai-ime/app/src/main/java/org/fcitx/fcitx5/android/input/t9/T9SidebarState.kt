/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 */
package org.fcitx.fcitx5.android.input.t9

data class T9SidebarState(
    val items: List<String> = emptyList(),
    val pageIndex: Int = 0,
    val totalPages: Int = 0,
) {
    val showingCandidates: Boolean
        get() = items.isNotEmpty()

    companion object {
        val Empty = T9SidebarState()
    }
}
