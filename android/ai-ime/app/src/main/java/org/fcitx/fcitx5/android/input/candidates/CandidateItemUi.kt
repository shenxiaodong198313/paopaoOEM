/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2023 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.candidates

import android.content.Context
import androidx.core.text.buildSpannedString
import androidx.core.text.color
import org.fcitx.fcitx5.android.core.CandidateWord
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.data.InputFeedbacks
import org.fcitx.fcitx5.android.data.theme.SkinSoundKey
import org.fcitx.fcitx5.android.data.theme.SkinSoundResolver
import org.fcitx.fcitx5.android.input.AutoScaleTextView
import org.fcitx.fcitx5.android.input.keyboard.CustomGestureView
import org.fcitx.fcitx5.android.utils.pressHighlightDrawable
import splitties.views.dsl.core.Ui
import splitties.views.dsl.core.add
import splitties.views.dsl.core.lParams
import splitties.views.dsl.core.view
import splitties.views.dsl.core.wrapContent
import splitties.views.gravityCenter
import splitties.views.setPaddingDp

class CandidateItemUi(override val ctx: Context, val theme: Theme, expanded: Boolean = false) : Ui {

    private val panel = if (expanded) org.fcitx.fcitx5.android.input.status.ExpandedCandidatePalette(
        theme.backgroundColor, theme.candidateTextColor, theme.candidateCommentColor, theme.accentKeyBackgroundColor
    ) else null
    private val config = (theme as? Theme.Custom)?.renderStyle?.localConfiguration
    private val candidateInk = config?.candidateTextColor?.takeIf { it != 0 } ?: panel?.ink ?: theme.candidateTextColor
    private val commentInk = panel?.commentInk ?: theme.candidateCommentColor

    val text = view(::AutoScaleTextView) {
        scaleMode = AutoScaleTextView.Mode.Proportional
        textSize = 20f * (config?.candidateTextScale ?: 100) / 100f // sp
        isSingleLine = true
        gravity = gravityCenter
        setTextColor(candidateInk)
        val sidePadding = if (expanded) 3 else 10
        setPaddingDp(sidePadding, 2, sidePadding, 2)
    }

    override val root = view(::CustomGestureView) {
        soundOnTouch = false
        background = pressHighlightDrawable(panel?.pressed ?: theme.keyPressHighlightColor)

        /**
         * candidate long press feedback is handled by [org.fcitx.fcitx5.android.input.BaseInputView.showCandidateActionMenu]
         */
        longPressFeedbackEnabled = false

        add(text, lParams(wrapContent, wrapContent) {
            gravity = gravityCenter
        })
    }

    private val soundCandidates = SkinSoundResolver.candidates((theme as? Theme.Custom)?.skinSound,
        SkinSoundKey("", "role:candidate", "role:candidate"))

    fun captureSelectionFeedback(): () -> Unit {
        val ticket = InputFeedbacks.captureSoundAction(soundCandidates)
        return { InputFeedbacks.completeSoundAction(ticket) }
    }

    fun updateCandidate(candidate: CandidateWord) {
        val fg = candidateInk
        val altFg = commentInk
        text.text = buildSpannedString {
            color(fg) {
                append(candidate.text)
            }
            if (candidate.comment.isNotBlank()) {
                if (candidate.spaceBetweenComment) {
                    append(" ")
                }
                color(altFg) {
                    append(candidate.comment)
                }
            }
        }
    }
}
