package com.paopaoime.aikeyboard.midscene.agent

import android.content.Context
import com.paopaoime.aikeyboard.ime.auth.AuthSessionStore

data class MidsceneModelOption(
    val modelId: String,
    val displayName: String,
    val iconUrl: String = ""
)

private val fallbackMidsceneModels = listOf(
    MidsceneModelOption("qwen3.6-plus", "qwen3.6-plus", "/admin/model-icons/qwen.png"),
    MidsceneModelOption("qwen3.6-flash", "qwen3.6-flash", "/admin/model-icons/qwen.png"),
    MidsceneModelOption("qwen3.6-35b-a3b", "qwen3.6-35b-a3b", "/admin/model-icons/qwen.png"),
    MidsceneModelOption("qwen3.6-27b", "qwen3.6-27b", "/admin/model-icons/qwen.png"),
    MidsceneModelOption("qwen3-vl-plus", "qwen3-vl-plus", "/admin/model-icons/qwen.png")
)

private const val DEFAULT_SCREENSHOT_SHRINK_FACTOR = 2

private fun inferModelIconUrl(modelId: String?): String {
    val normalized = modelId?.trim().orEmpty().lowercase()
    return when {
        normalized.contains("deepseek") -> "/admin/model-icons/deepseek.png"
        normalized.contains("qwen") -> "/admin/model-icons/qwen.png"
        else -> ""
    }
}

private fun resolveLegacyModel(key: String?): MidsceneModelOption? {
    return when (key?.trim().orEmpty()) {
        "qwen3_vl_plus" -> MidsceneModelOption("qwen3-vl-plus", "qwen3-vl-plus")
        "qwen3_6_plus" -> MidsceneModelOption("qwen3.6-plus", "qwen3.6-plus")
        "qwen3_6_flash" -> MidsceneModelOption("qwen3.6-flash", "qwen3.6-flash")
        "qwen3_6_flash_20260416" -> MidsceneModelOption("qwen3.6-flash-2026-04-16", "qwen3.6-flash-2026-04-16")
        "qwen3_6_27b" -> MidsceneModelOption("qwen3.6-27b", "qwen3.6-27b")
        "qwen3_6_35b_a3b" -> MidsceneModelOption("qwen3.6-35b-a3b", "qwen3.6-35b-a3b")
        else -> null
    }
}

private fun resolveStoredModel(modelId: String?, displayName: String?, iconUrl: String? = null): MidsceneModelOption {
    val normalizedId = modelId?.trim().orEmpty()
    val normalizedName = displayName?.trim().orEmpty()
    val normalizedIconUrl = iconUrl?.trim().orEmpty()
    resolveLegacyModel(normalizedId)?.let { legacy ->
        return legacy.copy(
            displayName = normalizedName.ifBlank { legacy.displayName },
            iconUrl = normalizedIconUrl.ifBlank { legacy.iconUrl.ifBlank { inferModelIconUrl(normalizedId) } }
        )
    }
    if (normalizedId.isNotBlank()) {
        return MidsceneModelOption(
            modelId = normalizedId,
            displayName = normalizedName.ifBlank { normalizedId },
            iconUrl = normalizedIconUrl.ifBlank { inferModelIconUrl(normalizedId) }
        )
    }
    return resolveLegacyModel(modelId)
        ?: MidsceneModelOption("", "", normalizedIconUrl.ifBlank { inferModelIconUrl(normalizedId) })
}

data class MidsceneModelSelection(
    val baseModel: MidsceneModelOption,
    val independentPlanningEnabled: Boolean,
    val autoModeEnabled: Boolean,
    val comboModeEnabled: Boolean
)

data class MidsceneExecutionModelSelection(
    val defaultModel: MidsceneModelOption,
    val planningModel: MidsceneModelOption?,
    val insightModel: MidsceneModelOption?,
    val screenshotShrinkFactor: Int = DEFAULT_SCREENSHOT_SHRINK_FACTOR
) {
    val planningEnabled: Boolean
        get() = planningModel != null

    val insightEnabled: Boolean
        get() = insightModel != null

    val comboModeEnabled: Boolean
        get() = planningEnabled && insightEnabled

    fun modeLabel(): String {
        return when {
            planningEnabled && insightEnabled -> "默认模型 + Planning 模型 + Insight 模型"
            planningEnabled -> "默认模型 + Planning 模型"
            insightEnabled -> "默认模型 + Insight 模型"
            else -> "默认模型"
        }
    }
}

object MidsceneAgentModelPreferences {
    private const val prefsName = "midscene_agent_model_prefs"
    private const val keyBaseModel = "base_model"
    private const val keyBaseModelDisplayName = "base_model_display_name"
    private const val keyIndependentPlanning = "independent_planning"
    private const val keyAutoModeEnabled = "auto_mode_enabled"
    private const val keyComboModeEnabled = "combo_mode_enabled"
    private const val keyDebugModeEnabled = "debug_mode_enabled"
    private const val keyTaskLogEnabled = "task_log_enabled"
    private const val keyDefaultModelId = "default_model_id"
    private const val keyDefaultModelDisplayName = "default_model_display_name"
    private const val keyDefaultModelIconUrl = "default_model_icon_url"
    private const val keyPlanningEnabled = "planning_enabled"
    private const val keyPlanningModelId = "planning_model_id"
    private const val keyPlanningModelDisplayName = "planning_model_display_name"
    private const val keyPlanningModelIconUrl = "planning_model_icon_url"
    private const val keyInsightEnabled = "insight_enabled"
    private const val keyInsightModelId = "insight_model_id"
    private const val keyInsightModelDisplayName = "insight_model_display_name"
    private const val keyInsightModelIconUrl = "insight_model_icon_url"
    private const val keyScreenshotShrinkFactor = "screenshot_shrink_factor"
    private const val legacyKeyCompressImageEnabled = "compress_image_enabled"
    private val defaultExecutionModel = MidsceneModelOption("", "", "")

    private fun scopedKey(context: Context, key: String): String {
        val userId = AuthSessionStore.userId(context).trim().ifBlank { "guest" }
        return "${userId}_$key"
    }

    fun getSelection(context: Context): MidsceneModelSelection {
        val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        return MidsceneModelSelection(
            baseModel = resolveStoredModel(
                prefs.getString(keyBaseModel, fallbackMidsceneModels.first().modelId),
                prefs.getString(keyBaseModelDisplayName, fallbackMidsceneModels.first().displayName),
                fallbackMidsceneModels.first().iconUrl
            ),
            independentPlanningEnabled = true,
            autoModeEnabled = prefs.getBoolean(keyAutoModeEnabled, true),
            comboModeEnabled = prefs.getBoolean(keyComboModeEnabled, false)
        )
    }

    fun getExecutionSelection(context: Context): MidsceneExecutionModelSelection {
        val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        val defaultModel = resolveStoredModel(
            prefs.getString(scopedKey(context, keyDefaultModelId), ""),
            prefs.getString(scopedKey(context, keyDefaultModelDisplayName), ""),
            prefs.getString(scopedKey(context, keyDefaultModelIconUrl), "")
        )
        val planningEnabled = prefs.getBoolean(scopedKey(context, keyPlanningEnabled), false)
        val planningModel = if (planningEnabled) {
            resolveStoredModel(
                prefs.getString(scopedKey(context, keyPlanningModelId), ""),
                prefs.getString(scopedKey(context, keyPlanningModelDisplayName), ""),
                prefs.getString(scopedKey(context, keyPlanningModelIconUrl), "")
            )
        } else {
            null
        }
        val insightEnabled = prefs.getBoolean(scopedKey(context, keyInsightEnabled), false)
        val insightModel = if (insightEnabled) {
            resolveStoredModel(
                prefs.getString(scopedKey(context, keyInsightModelId), ""),
                prefs.getString(scopedKey(context, keyInsightModelDisplayName), ""),
                prefs.getString(scopedKey(context, keyInsightModelIconUrl), "")
            )
        } else {
            null
        }
        return MidsceneExecutionModelSelection(
            defaultModel = defaultModel,
            planningModel = planningModel,
            insightModel = insightModel,
            screenshotShrinkFactor = prefs
                .getInt(
                    scopedKey(context, keyScreenshotShrinkFactor),
                    DEFAULT_SCREENSHOT_SHRINK_FACTOR
                )
                .coerceAtLeast(1)
        )
    }

    fun saveExecutionSelection(context: Context, selection: MidsceneExecutionModelSelection) {
        context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
            .edit()
            .putString(scopedKey(context, keyDefaultModelId), selection.defaultModel.modelId)
            .putString(scopedKey(context, keyDefaultModelDisplayName), selection.defaultModel.displayName)
            .putString(scopedKey(context, keyDefaultModelIconUrl), selection.defaultModel.iconUrl)
            .putBoolean(scopedKey(context, keyPlanningEnabled), selection.planningEnabled)
            .putString(
                scopedKey(context, keyPlanningModelId),
                selection.planningModel?.modelId.orEmpty()
            )
            .putString(
                scopedKey(context, keyPlanningModelDisplayName),
                selection.planningModel?.displayName.orEmpty()
            )
            .putString(
                scopedKey(context, keyPlanningModelIconUrl),
                selection.planningModel?.iconUrl.orEmpty()
            )
            .putBoolean(scopedKey(context, keyInsightEnabled), selection.insightEnabled)
            .putString(
                scopedKey(context, keyInsightModelId),
                selection.insightModel?.modelId.orEmpty()
            )
            .putString(
                scopedKey(context, keyInsightModelDisplayName),
                selection.insightModel?.displayName.orEmpty()
            )
            .putString(
                scopedKey(context, keyInsightModelIconUrl),
                selection.insightModel?.iconUrl.orEmpty()
            )
            .putInt(
                scopedKey(context, keyScreenshotShrinkFactor),
                selection.screenshotShrinkFactor.coerceAtLeast(1)
            )
            .remove(scopedKey(context, legacyKeyCompressImageEnabled))
            .apply()
    }

    fun getScreenshotShrinkFactor(context: Context): Int {
        return context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
            .getInt(
                scopedKey(context, keyScreenshotShrinkFactor),
                DEFAULT_SCREENSHOT_SHRINK_FACTOR
            )
            .coerceAtLeast(1)
    }

    fun defaultExecutionModel(): MidsceneModelOption = defaultExecutionModel

    fun setBaseModel(context: Context, model: MidsceneModelOption) {
        context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
            .edit()
            .putString(keyBaseModel, model.modelId)
            .putString(keyBaseModelDisplayName, model.displayName)
            .apply()
    }

    fun defaultModels(): List<MidsceneModelOption> = fallbackMidsceneModels

    fun setIndependentPlanningEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(keyIndependentPlanning, enabled)
            .apply()
    }

    fun setAutoModeEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(keyAutoModeEnabled, enabled)
            .apply()
    }

    fun isComboModeEnabled(context: Context): Boolean {
        return context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
            .getBoolean(keyComboModeEnabled, false)
    }

    fun setComboModeEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(keyComboModeEnabled, enabled)
            .apply()
    }

    fun isDebugModeEnabled(context: Context): Boolean {
        return context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
            .getBoolean(keyDebugModeEnabled, false)
    }

    fun setDebugModeEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(keyDebugModeEnabled, enabled)
            .apply()
    }

    fun isTaskLogEnabled(context: Context): Boolean {
        return context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
            .getBoolean(keyTaskLogEnabled, false)
    }

    fun setTaskLogEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(keyTaskLogEnabled, enabled)
            .apply()
    }
}
