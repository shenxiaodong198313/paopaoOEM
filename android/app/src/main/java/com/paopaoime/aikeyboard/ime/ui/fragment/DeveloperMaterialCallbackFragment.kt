package com.paopaoime.aikeyboard.ime.ui.fragment

import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.paopaoime.aikeyboard.ime.R
import com.paopaoime.aikeyboard.oem.debug.OemDeveloperMaterialCallbackRuntime
import com.paopaoime.aikeyboard.oem.debug.OemDeveloperMaterialCallbackStore
import com.paopaoime.aikeyboard.oem.debug.OemDeveloperMaterialTaxonomyRecord
import com.paopaoime.aikeyboard.sdk.core.OemImeSdk
import com.paopaoime.aikeyboard.sdk.core.OemMaterialDispatchMode
import com.paopaoime.aikeyboard.sdk.core.OemMaterialNavigationMode
import com.paopaoime.aikeyboard.sdk.core.OemRemoteMaterialItem
import com.paopaoime.aikeyboard.sdk.core.OemRemoteMaterialPart
import java.text.DateFormat
import java.util.Date

class DeveloperMaterialCallbackFragment : BaseInputMethodSettingsFragment(
    R.layout.fragment_developer_material_callback,
) {
    private var introView: TextView? = null
    private var summaryView: TextView? = null
    private var categoryEmptyView: TextView? = null
    private var tagEmptyView: TextView? = null
    private var materialEmptyView: TextView? = null
    private var categoryListContainer: LinearLayout? = null
    private var tagListContainer: LinearLayout? = null
    private var materialListContainer: LinearLayout? = null
    private val dispatchButtons = linkedMapOf<OemMaterialDispatchMode, TextView>()
    private val navigationButtons = linkedMapOf<OemMaterialNavigationMode, TextView>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bindHeader(view, R.id.layout_developer_material_header, R.id.btn_developer_material_back)
        applyCardShadow(
            view,
            intArrayOf(
                R.id.card_developer_material_intro,
                R.id.card_developer_material_modes,
                R.id.card_developer_material_summary,
                R.id.card_developer_material_categories,
                R.id.card_developer_material_tags,
                R.id.card_developer_material_preview,
            ),
        )
        introView = view.findViewById(R.id.tv_developer_material_intro)
        summaryView = view.findViewById(R.id.tv_developer_material_summary)
        categoryEmptyView = view.findViewById(R.id.tv_developer_material_categories_empty)
        tagEmptyView = view.findViewById(R.id.tv_developer_material_tags_empty)
        materialEmptyView = view.findViewById(R.id.tv_developer_material_preview_empty)
        categoryListContainer = view.findViewById(R.id.layout_developer_material_categories)
        tagListContainer = view.findViewById(R.id.layout_developer_material_tags)
        materialListContainer = view.findViewById(R.id.layout_developer_material_preview)

        bindModeButtons(view)
        view.findViewById<View>(R.id.btn_developer_material_add_category)?.setOnClickListener {
            showTaxonomyEditor(isCategory = true, existing = null)
        }
        view.findViewById<View>(R.id.btn_developer_material_add_tag)?.setOnClickListener {
            showTaxonomyEditor(isCategory = false, existing = null)
        }
        view.findViewById<View>(R.id.btn_developer_material_reset)?.setOnClickListener {
            val context = requireContext()
            OemDeveloperMaterialCallbackStore.resetToDemo(context)
            OemDeveloperMaterialCallbackRuntime.applySavedModes(context)
            OemDeveloperMaterialCallbackRuntime.notifyMaterialCatalogChanged()
            Toast.makeText(context, R.string.developer_material_reset_done, Toast.LENGTH_SHORT).show()
            render()
        }
        view.findViewById<View>(R.id.btn_developer_material_clear)?.setOnClickListener {
            val context = requireContext()
            OemDeveloperMaterialCallbackStore.clear(context)
            OemDeveloperMaterialCallbackRuntime.applySavedModes(context)
            OemDeveloperMaterialCallbackRuntime.notifyMaterialCatalogChanged()
            Toast.makeText(context, R.string.developer_material_clear_done, Toast.LENGTH_SHORT).show()
            render()
        }
        render()
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        val context = context ?: return
        val snapshot = OemDeveloperMaterialCallbackStore.load(context)
        val materials = OemDeveloperMaterialCallbackStore.buildRemoteMaterials(context)
        introView?.text = getString(
            R.string.developer_material_intro_template,
            snapshot.dispatchMode.name,
            snapshot.navigationMode.name,
        )
        summaryView?.text = getString(
            R.string.developer_material_summary_template,
            context.packageName,
            currentDefaultImeId(context),
            if (isCurrentPackageSelectedAsIme(context)) {
                getString(R.string.developer_material_current_ime_owned)
            } else {
                getString(R.string.developer_material_current_ime_foreign)
            },
            snapshot.dispatchMode.name,
            snapshot.navigationMode.name,
            if (OemImeSdk.isMaterialHostDelegateRegistered()) getString(R.string.developer_material_registered) else getString(R.string.developer_material_not_registered),
            if (OemImeSdk.isMaterialTaxonomyHostDelegateRegistered()) getString(R.string.developer_material_registered) else getString(R.string.developer_material_not_registered),
            snapshot.categories.size,
            snapshot.tags.size,
            materials.size,
        )
        updateDispatchButtons(snapshot.dispatchMode)
        updateNavigationButtons(snapshot.navigationMode)
        renderTaxonomyList(
            records = snapshot.categories,
            container = categoryListContainer,
            emptyView = categoryEmptyView,
            isCategory = true,
        )
        renderTaxonomyList(
            records = snapshot.tags,
            container = tagListContainer,
            emptyView = tagEmptyView,
            isCategory = false,
        )
        renderMaterialPreview(materials)
    }

    private fun currentDefaultImeId(context: android.content.Context): String {
        return Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.DEFAULT_INPUT_METHOD,
        ).orEmpty().ifBlank { getString(R.string.none_label) }
    }

    private fun isCurrentPackageSelectedAsIme(context: android.content.Context): Boolean {
        return currentDefaultImeId(context).startsWith("${context.packageName}/")
    }

    private fun bindModeButtons(root: View) {
        dispatchButtons.clear()
        dispatchButtons[OemMaterialDispatchMode.CLOUD_ONLY] = root.findViewById(R.id.btn_material_mode_cloud_only)
        dispatchButtons[OemMaterialDispatchMode.HOST_ONLY] = root.findViewById(R.id.btn_material_mode_host_only)
        dispatchButtons[OemMaterialDispatchMode.DUAL_WRITE] = root.findViewById(R.id.btn_material_mode_dual_write)
        dispatchButtons[OemMaterialDispatchMode.LOCAL_ONLY] = root.findViewById(R.id.btn_material_mode_local_only)
        dispatchButtons.forEach { (mode, button) ->
            button.setOnClickListener {
                val context = context ?: return@setOnClickListener
                val current = OemDeveloperMaterialCallbackStore.load(context)
                OemDeveloperMaterialCallbackStore.saveModes(
                    context = context,
                    dispatchMode = mode,
                    navigationMode = current.navigationMode,
                )
                OemDeveloperMaterialCallbackRuntime.applySavedModes(context)
                OemDeveloperMaterialCallbackRuntime.notifyMaterialCatalogChanged()
                Toast.makeText(
                    context,
                    getString(R.string.developer_material_mode_switch_done, mode.name),
                    Toast.LENGTH_SHORT,
                ).show()
                render()
            }
        }

        navigationButtons.clear()
        navigationButtons[OemMaterialNavigationMode.TYPE_ONLY] = root.findViewById(R.id.btn_material_nav_type_only)
        navigationButtons[OemMaterialNavigationMode.TYPE_PRIMARY_CATEGORY_SECONDARY] = root.findViewById(R.id.btn_material_nav_type_primary)
        navigationButtons[OemMaterialNavigationMode.CATEGORY_PRIMARY_TYPE_SECONDARY] = root.findViewById(R.id.btn_material_nav_category_primary)
        navigationButtons[OemMaterialNavigationMode.CATEGORY_ONLY] = root.findViewById(R.id.btn_material_nav_category_only)
        navigationButtons.forEach { (mode, button) ->
            button.setOnClickListener {
                val context = context ?: return@setOnClickListener
                val current = OemDeveloperMaterialCallbackStore.load(context)
                OemDeveloperMaterialCallbackStore.saveModes(
                    context = context,
                    dispatchMode = current.dispatchMode,
                    navigationMode = mode,
                )
                OemDeveloperMaterialCallbackRuntime.applySavedModes(context)
                OemDeveloperMaterialCallbackRuntime.notifyMaterialCatalogChanged()
                Toast.makeText(
                    context,
                    getString(R.string.developer_material_navigation_switch_done, mode.name),
                    Toast.LENGTH_SHORT,
                ).show()
                render()
            }
        }
    }

    private fun updateDispatchButtons(selectedMode: OemMaterialDispatchMode) {
        val context = context ?: return
        dispatchButtons.forEach { (mode, button) ->
            val isSelected = mode == selectedMode
            button.setBackgroundResource(
                if (isSelected) R.drawable.bg_developer_mode_chip_selected
                else R.drawable.bg_developer_mode_chip_unselected,
            )
            button.setTextColor(
                ContextCompat.getColor(
                    context,
                    if (isSelected) android.R.color.white else android.R.color.black,
                ),
            )
        }
    }

    private fun updateNavigationButtons(selectedMode: OemMaterialNavigationMode) {
        val context = context ?: return
        navigationButtons.forEach { (mode, button) ->
            val isSelected = mode == selectedMode
            button.setBackgroundResource(
                if (isSelected) R.drawable.bg_developer_mode_chip_selected
                else R.drawable.bg_developer_mode_chip_unselected,
            )
            button.setTextColor(
                ContextCompat.getColor(
                    context,
                    if (isSelected) android.R.color.white else android.R.color.black,
                ),
            )
        }
    }

    private fun renderTaxonomyList(
        records: List<OemDeveloperMaterialTaxonomyRecord>,
        container: LinearLayout?,
        emptyView: TextView?,
        isCategory: Boolean,
    ) {
        container?.removeAllViews()
        emptyView?.visibility = if (records.isEmpty()) View.VISIBLE else View.GONE
        records.forEachIndexed { index, record ->
            container?.addView(
                createTaxonomyItemView(index, record, isCategory),
            )
        }
    }

    private fun renderMaterialPreview(materials: List<OemRemoteMaterialItem>) {
        val context = context ?: return
        val container = materialListContainer ?: return
        container.removeAllViews()
        materialEmptyView?.visibility = if (materials.isEmpty()) View.VISIBLE else View.GONE
        materials.forEachIndexed { index, item ->
            val formattedTime = DateFormat.getDateTimeInstance().format(Date(item.updatedAt))
            val previewText = item.parts.filterIsInstance<OemRemoteMaterialPart.Text>().firstOrNull()?.text.orEmpty()
            container.addView(
                TextView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    ).also { params ->
                        if (index > 0) {
                            params.topMargin = dp(10)
                        }
                    }
                    setBackgroundResource(R.drawable.bg_card_white_12dp)
                    setPadding(dp(14), dp(14), dp(14), dp(14))
                    setTextColor(context.getColor(android.R.color.black))
                    textSize = 13f
                    text = getString(
                        R.string.developer_material_preview_item_template,
                        index + 1,
                        item.materialId,
                        item.categoryName.orEmpty().ifBlank { getString(R.string.none_label) },
                        item.tags.joinToString("、").ifBlank { getString(R.string.none_label) },
                        formattedTime,
                        previewText.ifBlank { getString(R.string.none_label) },
                    )
                },
            )
        }
    }

    private fun createTaxonomyItemView(
        index: Int,
        record: OemDeveloperMaterialTaxonomyRecord,
        isCategory: Boolean,
    ): View {
        val context = requireContext()
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_card_white_12dp)
            setPadding(dp(14), dp(14), dp(14), dp(14))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).also { params ->
                if (index > 0) {
                    params.topMargin = dp(10)
                }
            }

            addView(TextView(context).apply {
                text = record.name
                setTextColor(context.getColor(android.R.color.black))
                textSize = 14f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })
            addView(TextView(context).apply {
                text = getString(
                    R.string.developer_material_taxonomy_meta_template,
                    record.id,
                    record.sort,
                )
                setTextColor(context.getColor(android.R.color.darker_gray))
                textSize = 12f
            }, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = dp(6)
            })
            addView(
                LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    addView(
                        buildActionChip(getString(R.string.developer_ai_tone_edit)) {
                            showTaxonomyEditor(isCategory = isCategory, existing = record)
                        },
                    )
                    addView(
                        buildActionChip(getString(R.string.developer_ai_tone_delete)) {
                            deleteTaxonomy(isCategory = isCategory, record = record)
                        }.also { chip ->
                            (chip.layoutParams as? LinearLayout.LayoutParams)?.marginStart = dp(8)
                        },
                    )
                },
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply {
                    topMargin = dp(10)
                },
            )
        }
    }

    private fun buildActionChip(text: String, onClick: () -> Unit): TextView {
        return TextView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
            setBackgroundResource(R.drawable.bg_developer_mode_chip_unselected)
            setPadding(dp(12), dp(6), dp(12), dp(6))
            setTextColor(requireContext().getColor(android.R.color.black))
            textSize = 12f
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            this.text = text
            setOnClickListener { onClick() }
        }
    }

    private fun showTaxonomyEditor(
        isCategory: Boolean,
        existing: OemDeveloperMaterialTaxonomyRecord?,
    ) {
        val input = EditText(requireContext()).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            hint = getString(
                if (isCategory) R.string.developer_material_category_hint
                else R.string.developer_material_tag_hint,
            )
            setText(existing?.name.orEmpty())
            setSelection(text.length)
        }
        AlertDialog.Builder(requireContext())
            .setTitle(
                if (existing == null) {
                    if (isCategory) R.string.developer_material_add_category else R.string.developer_material_add_tag
                } else {
                    if (isCategory) R.string.developer_material_edit_category else R.string.developer_material_edit_tag
                },
            )
            .setView(input)
            .setPositiveButton(R.string.developer_ai_tone_save) { _, _ ->
                val value = input.text?.toString().orEmpty().trim()
                if (value.isBlank()) {
                    Toast.makeText(requireContext(), R.string.developer_ai_input_invalid, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val context = requireContext()
                if (isCategory) {
                    OemDeveloperMaterialCallbackStore.upsertCategory(context, existing?.id, value)
                } else {
                    OemDeveloperMaterialCallbackStore.upsertTag(context, existing?.id, value)
                }
                OemDeveloperMaterialCallbackRuntime.notifyMaterialCatalogChanged()
                Toast.makeText(
                    context,
                    getString(
                        if (existing == null) {
                            if (isCategory) R.string.developer_material_category_added else R.string.developer_material_tag_added
                        } else {
                            if (isCategory) R.string.developer_material_category_updated else R.string.developer_material_tag_updated
                        },
                    ),
                    Toast.LENGTH_SHORT,
                ).show()
                render()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun deleteTaxonomy(
        isCategory: Boolean,
        record: OemDeveloperMaterialTaxonomyRecord,
    ) {
        AlertDialog.Builder(requireContext())
            .setTitle(
                if (isCategory) R.string.developer_material_delete_category
                else R.string.developer_material_delete_tag,
            )
            .setMessage(getString(R.string.developer_material_delete_confirm, record.name))
            .setPositiveButton(R.string.developer_ai_tone_delete) { _, _ ->
                val context = requireContext()
                if (isCategory) {
                    OemDeveloperMaterialCallbackStore.deleteCategory(context, record.id)
                } else {
                    OemDeveloperMaterialCallbackStore.deleteTag(context, record.id)
                }
                OemDeveloperMaterialCallbackRuntime.notifyMaterialCatalogChanged()
                Toast.makeText(
                    context,
                    getString(
                        if (isCategory) R.string.developer_material_category_deleted else R.string.developer_material_tag_deleted,
                    ),
                    Toast.LENGTH_SHORT,
                ).show()
                render()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
