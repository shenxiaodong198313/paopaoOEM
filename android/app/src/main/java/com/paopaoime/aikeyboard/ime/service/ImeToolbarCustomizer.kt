package com.paopaoime.aikeyboard.ime.service

import android.view.View
import org.fcitx.fcitx5.android.data.theme.SkinLabStandard
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ViewAnimator
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
import com.google.android.flexbox.FlexboxLayout
import com.google.android.flexbox.JustifyContent
import com.paopaoime.aikeyboard.ime.R
import com.paopaoime.aikeyboard.ime.auth.AuthSessionStore
import com.paopaoime.aikeyboard.ime.ui.toolbar.OemToolbarIconHelper
import com.paopaoime.aikeyboard.oem.OemEnterpriseConfig
import com.paopaoime.aikeyboard.sdk.core.OemAddons
import com.paopaoime.aikeyboard.ime.ui.settings.InputMethodSettingsBridge
import com.paopaoime.aikeyboard.ime.ui.settings.InputMethodSettingsBridge.ToolbarItem
import org.fcitx.fcitx5.android.R as FcitxR
import org.fcitx.fcitx5.android.data.theme.ThemeManager
import org.fcitx.fcitx5.android.input.bar.ToolbarActionHostBridge
import org.fcitx.fcitx5.android.input.bar.ui.ToolButton
import splitties.dimensions.dp

object ImeToolbarCustomizer {
    fun apply(root: View, service: AiInputMethodService) {
        val descriptions = ToolbarDescriptions.from(service)
        val toolbarSettings = InputMethodSettingsBridge.getToolbarSettings(service)
        val menuButton = findFirstByDescriptions(
            root,
            descriptions.expandToolbar,
            descriptions.hideToolbar,
            descriptions.privateMode
        )
        if (service.hostClipboard.current() == null && toolbarSettings.expandByDefault &&
            menuButton?.contentDescription?.toString() == descriptions.expandToolbar
        ) {
            menuButton.performClick()
        }

        menuButton?.makeGone()
        val undoButton = findFirstByDescriptions(root, descriptions.undo)
        val redoButton = findFirstByDescriptions(root, descriptions.redo)
        val textEditingButton = findFirstByDescriptions(root, descriptions.textEditing)
        val materialHubButton = findFirstByDescriptions(root, descriptions.materialHub)
        val clipboardButton = findFirstByDescriptions(root, descriptions.clipboard)
        val moreButton = findFirstByDescriptions(root, descriptions.statusArea)

        listOf(undoButton, redoButton, textEditingButton, materialHubButton, clipboardButton).forEach {
            it?.makeGone()
        }

        relayoutToolbarBody(
            menuButton = menuButton,
            clipboardButton = clipboardButton,
            moreButton = moreButton,
            service = service
        )
    }

    private fun relayoutToolbarBody(
        menuButton: View?,
        clipboardButton: View?,
        moreButton: View?,
        service: AiInputMethodService,
    ) {
        val idleBody = sequenceOf(
            menuButton?.parent,
            moreButton?.parent,
            moreButton?.parent?.parent,
            clipboardButton?.parent,
            clipboardButton?.parent?.parent,
        ).filterIsInstance<ConstraintLayout>().firstOrNull() ?: return
        val animator = idleBody.children().filterIsInstance<ViewAnimator>().firstOrNull() ?: return
        val flexbox = animator.children().filterIsInstance<FlexboxLayout>().firstOrNull() ?: return
        val hideButton = idleBody.children()
            .firstOrNull { candidate ->
                candidate !== animator && candidate !== menuButton && candidate !== moreButton
            } ?: return

        val leadingMoreButton = moveMoreButtonToLeading(
            idleBody = idleBody,
            moreButton = moreButton,
            service = service
        )
        configureCustomButtons(
            flexbox = flexbox,
            orderedItems = InputMethodSettingsBridge.getToolbarDisplaySettings(service).orderedItems,
            service = service
        )
        styleToolbarButton(hideButton, service, FcitxR.drawable.ic_baseline_arrow_drop_down_24)

        (animator.layoutParams as? LayoutParams)?.let { animatorLayoutParams ->
            animatorLayoutParams.width = 0
            animatorLayoutParams.startToEnd =
                leadingMoreButton.id.takeIf { it != View.NO_ID } ?: LayoutParams.UNSET
            animatorLayoutParams.startToStart = LayoutParams.UNSET
            animatorLayoutParams.leftToRight =
                leadingMoreButton.id.takeIf { it != View.NO_ID } ?: LayoutParams.UNSET
            animatorLayoutParams.leftToLeft = LayoutParams.UNSET
            animatorLayoutParams.endToStart =
                hideButton.id.takeIf { it != View.NO_ID } ?: LayoutParams.UNSET
            animatorLayoutParams.rightToLeft =
                hideButton.id.takeIf { it != View.NO_ID } ?: LayoutParams.UNSET
            animator.layoutParams = animatorLayoutParams
        }

        (hideButton.layoutParams as? LayoutParams)?.let { params ->
            params.width = service.dp(OemToolbarIconHelper.TOOLBAR_BUTTON_SIZE_DP)
            params.height = service.dp(OemToolbarIconHelper.TOOLBAR_BUTTON_SIZE_DP)
            params.marginEnd = service.dp(OemToolbarIconHelper.TOOLBAR_EDGE_MARGIN_DP)
            hideButton.layoutParams = params
        }
        val style = (ThemeManager.activeTheme as? org.fcitx.fcitx5.android.data.theme.Theme.Custom)?.renderStyle
        if (style?.skinLabStandard == true) {
            val ctx = idleBody.context
            val diameter = if (SkinLabStandard.landscape(ctx)) 30.5f else 34f
            val edgeInset = ctx.dp(SkinLabStandard.gapX(ctx) / 2 + 1.125f).toInt()
            for (edge in listOf(leadingMoreButton, hideButton)) {
                (edge.layoutParams as? LayoutParams)?.let { p ->
                    p.width = ctx.dp(diameter).toInt(); p.height = ctx.dp(diameter).toInt()
                    edge.translationY = ctx.dp(-.875f)
                    p.matchConstraintDefaultWidth = LayoutParams.MATCH_CONSTRAINT_SPREAD
                    p.marginStart = if (edge === leadingMoreButton) edgeInset else 0
                    p.marginEnd = if (edge === hideButton) edgeInset else 0
                    edge.layoutParams = p
                }
            }
            flexbox.translationY = ctx.dp(-.875f)
            flexbox.justifyContent = JustifyContent.FLEX_END
            flexbox.children().forEach { child ->
                child.layoutParams = FlexboxLayout.LayoutParams(ctx.dp(diameter).toInt(), ctx.dp(diameter).toInt()).apply {
                    marginEnd = ctx.dp(46f - diameter).toInt(); flexGrow = 0f; flexShrink = 0f
                }
            }
        } else if (style?.equalToolbarSlots == true) {
            val count = flexbox.childCount + 2
            val height = service.dp(style.barBodyHeightDp - 4)
            for (edge in listOf(leadingMoreButton, hideButton)) {
                (edge.layoutParams as? LayoutParams)?.let { p ->
                    p.width = 0
                    p.matchConstraintDefaultWidth = LayoutParams.MATCH_CONSTRAINT_PERCENT
                    p.matchConstraintPercentWidth = 1f / count
                    p.height = height
                    p.marginStart = 0; p.marginEnd = 0
                    edge.layoutParams = p
                }
            }
            flexbox.justifyContent = JustifyContent.FLEX_START
            flexbox.children().forEach { child ->
                child.layoutParams = FlexboxLayout.LayoutParams(0, height).apply {
                    flexGrow = 1f; flexShrink = 1f
                }
            }
        }
    }

    private fun moveMoreButtonToLeading(
        idleBody: ConstraintLayout,
        moreButton: View?,
        service: AiInputMethodService,
    ): View {
        val target = moreButton ?: ToolButton(idleBody.context, OemToolbarIconHelper.leadingEntryIconRes(), ThemeManager.activeTheme).apply {
            id = View.generateViewId()
            contentDescription = service.getString(FcitxR.string.status_area)
            setOnClickListener { ToolbarActionHostBridge.launchKeyboardSelection() }
        }
        if (target.id == View.NO_ID) {
            target.id = View.generateViewId()
        }
        when (target) {
            is ToolButton -> target.setIcon(OemToolbarIconHelper.leadingEntryIconRes())
            is ImageView -> target.setImageResource(OemToolbarIconHelper.leadingEntryIconRes())
        }
        (target.parent as? ViewGroup)?.removeView(target)
        if (target.parent == null) {
            idleBody.addView(
                target,
                LayoutParams(
                    service.dp(OemToolbarIconHelper.TOOLBAR_BUTTON_SIZE_DP),
                    service.dp(OemToolbarIconHelper.TOOLBAR_BUTTON_SIZE_DP)
                ).apply {
                    startToStart = LayoutParams.PARENT_ID
                    topToTop = LayoutParams.PARENT_ID
                    bottomToBottom = LayoutParams.PARENT_ID
                    marginStart = service.dp(OemToolbarIconHelper.TOOLBAR_EDGE_MARGIN_DP)
                }
            )
        }
        styleToolbarButton(target, service, OemToolbarIconHelper.leadingEntryIconRes())
        target.makeVisible()
        return target
    }

    private fun configureCustomButtons(
        flexbox: FlexboxLayout,
        orderedItems: List<InputMethodSettingsBridge.ToolbarDisplayItem>,
        service: AiInputMethodService,
    ) {
        flexbox.removeAllViews()
        flexbox.translationY = 0f
        flexbox.justifyContent = JustifyContent.FLEX_END
        flexbox.setPadding(0, 0, 0, 0)
        val size = flexbox.context.dp(OemToolbarIconHelper.TOOLBAR_BUTTON_SIZE_DP)
        val gap = flexbox.context.dp(OemToolbarIconHelper.TOOLBAR_BUTTON_MARGIN_DP)
        orderedItems.take(4).forEach { displayItem ->
            val button = when (displayItem) {
                is InputMethodSettingsBridge.ToolbarDisplayItem.Builtin -> {
                    if (!isToolbarItemEnabled(displayItem.item, service)) null
                    else createCustomButton(displayItem.item, service, flexbox.context)
                }
                is InputMethodSettingsBridge.ToolbarDisplayItem.Custom -> {
                    createCustomDrawerButton(displayItem, service, flexbox.context)
                }
            } ?: return@forEach
            flexbox.addView(
                button,
                FlexboxLayout.LayoutParams(size, size).apply {
                    marginStart = 0
                    marginEnd = gap
                    flexShrink = 0f
                }
            )
            styleToolbarButton(button, service, (displayItem as? InputMethodSettingsBridge.ToolbarDisplayItem.Builtin)?.item?.let(OemToolbarIconHelper::toolbarIconRes))
        }
        flexbox.requestLayout()
    }

    private fun createCustomDrawerButton(
        displayItem: InputMethodSettingsBridge.ToolbarDisplayItem.Custom,
        service: AiInputMethodService,
        context: android.content.Context,
    ): View {
        return ToolButton(context).apply {
            image.imageTintList = null
            image.setImageDrawable(
                displayItem.icon
                    ?: service.getDrawable(R.drawable.ic_oem_toolbar_custom_placeholder)
            )
            setPressHighlightColor(ThemeManager.activeTheme.keyPressHighlightColor)
            addView(
                image,
                android.widget.FrameLayout.LayoutParams(
                    android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                    android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                    android.view.Gravity.CENTER
                )
            )
            contentDescription = displayItem.title
            setOnClickListener { ToolbarActionHostBridge.launchCustomDrawer(displayItem.id) }
        }
    }

    private fun isToolbarItemEnabled(item: ToolbarItem, service: AiInputMethodService): Boolean {
        return when (item) {
            ToolbarItem.Skin -> false
            ToolbarItem.Voice -> org.fcitx.fcitx5.android.input.voice.OemVoiceBridge.enabled(service)
            ToolbarItem.AiReply -> OemAddons.hasAiReply && OemEnterpriseConfig.isAiReplyEntryEnabled(service)
            ToolbarItem.MaterialHub -> OemAddons.hasMaterialHub && OemEnterpriseConfig.isMaterialLibraryEntryEnabled(service)
            else -> true
        }
    }

    private fun createCustomButton(
        item: ToolbarItem,
        service: AiInputMethodService,
        context: android.content.Context,
    ): View {
        return ToolButton(context, OemToolbarIconHelper.toolbarIconRes(item), ThemeManager.activeTheme).apply {
            contentDescription = service.getString(item.labelRes)
            setOnClickListener { handleToolbarItemClick(item, service) }
        }
    }

    private fun handleToolbarItemClick(
        item: ToolbarItem,
        service: AiInputMethodService,
    ) {
        when (item) {
            ToolbarItem.Voice -> ToolbarActionHostBridge.launchVoiceInput()
            ToolbarItem.KeyboardSelection -> ToolbarActionHostBridge.launchKeyboardSelection()
            ToolbarItem.AiReply -> ToolbarActionHostBridge.launchAiReply()
            ToolbarItem.MaterialHub -> ToolbarActionHostBridge.launchMaterialHub()
            ToolbarItem.Emoji -> ToolbarActionHostBridge.launchEmoji()
            ToolbarItem.Clipboard -> ToolbarActionHostBridge.launchClipboard()
            ToolbarItem.CustomizeToolbar -> ToolbarActionHostBridge.launchCustomizeToolbar()
            ToolbarItem.Skin -> AuthSessionStore.showHostManagedAccountToast(service, "皮肤")
            ToolbarItem.TraditionalInput -> ToolbarActionHostBridge.launchTraditionalInput()
            ToolbarItem.KeyboardAdjust -> ToolbarActionHostBridge.launchKeyboardAdjust()
        }
    }

    private fun styleToolbarButton(view: View, service: AiInputMethodService, iconRes: Int? = null) {
        val size = view.context.dp(OemToolbarIconHelper.TOOLBAR_BUTTON_SIZE_DP)
        val gap = view.context.dp(OemToolbarIconHelper.TOOLBAR_BUTTON_MARGIN_DP)
        when (val params = view.layoutParams) {
            is FlexboxLayout.LayoutParams -> {
                params.width = size
                params.height = size
                params.marginStart = 0
                params.marginEnd = gap
                params.flexShrink = 0f
                view.layoutParams = params
            }
            is LayoutParams -> {
                params.width = size
                params.height = size
                params.marginStart = view.context.dp(OemToolbarIconHelper.TOOLBAR_EDGE_MARGIN_DP)
                params.marginEnd = view.context.dp(OemToolbarIconHelper.TOOLBAR_EDGE_MARGIN_DP)
                view.layoutParams = params
            }
            is ViewGroup.MarginLayoutParams -> {
                params.width = size
                params.height = size
                params.setMargins(0, 0, gap, 0)
                view.layoutParams = params
            }
        }
        when (view) {
            is ToolButton -> {
                OemToolbarIconHelper.applyToolbarButtonStyle(view, view.context, iconRes)
            }
            is ImageView -> {
                view.imageTintList = android.content.res.ColorStateList.valueOf(OemToolbarIconHelper.toolbarIconTintColor())
            }
            is ViewGroup -> {
                view.children().forEach { child ->
                    if (child is ImageView) {
                        child.imageTintList = android.content.res.ColorStateList.valueOf(OemToolbarIconHelper.toolbarIconTintColor())
                    }
                }
            }
        }
    }

    private fun findFirstByDescriptions(root: View, vararg descriptions: String): View? {
        if (descriptions.isEmpty()) return null
        val expected = descriptions.filter { it.isNotBlank() }.toSet()
        if (expected.isEmpty()) return null
        return root.findDescendant { candidate ->
            candidate.contentDescription?.toString() in expected
        }
    }

    private fun View.findDescendant(predicate: (View) -> Boolean): View? {
        if (predicate(this)) return this
        val group = this as? ViewGroup ?: return null
        for (index in 0 until group.childCount) {
            val match = group.getChildAt(index).findDescendant(predicate)
            if (match != null) return match
        }
        return null
    }

    private fun View.makeGone() {
        visibility = View.GONE
        isEnabled = false
        isClickable = false
    }

    private fun View.makeVisible() {
        visibility = View.VISIBLE
        isEnabled = true
        isClickable = true
    }

    private fun ViewGroup.children(): Sequence<View> = sequence {
        for (index in 0 until childCount) {
            yield(getChildAt(index))
        }
    }

    private data class ToolbarDescriptions(
        val expandToolbar: String,
        val hideToolbar: String,
        val privateMode: String,
        val undo: String,
        val redo: String,
        val textEditing: String,
        val materialHub: String,
        val clipboard: String,
        val statusArea: String,
    ) {
        companion object {
            fun from(service: AiInputMethodService): ToolbarDescriptions {
                return ToolbarDescriptions(
                    expandToolbar = service.getString(FcitxR.string.expand_toolbar),
                    hideToolbar = service.getString(FcitxR.string.hide_toolbar),
                    privateMode = service.getString(FcitxR.string.private_mode),
                    undo = service.getString(FcitxR.string.undo),
                    redo = service.getString(FcitxR.string.redo),
                    textEditing = service.getString(FcitxR.string.text_editing),
                    materialHub = service.getString(FcitxR.string.material_hub_entry),
                    clipboard = service.getString(FcitxR.string.clipboard),
                    statusArea = service.getString(FcitxR.string.status_area),
                )
            }
        }
    }
}
