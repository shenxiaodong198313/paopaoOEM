package com.paopaoime.aikeyboard.addon.developer

import android.app.Application
import android.content.Context
import android.content.Intent
import com.paopaoime.aikeyboard.oem.debug.OemDeveloperAiConfigStore
import com.paopaoime.aikeyboard.oem.debug.OemDeveloperMaterialCallbackRuntime
import com.paopaoime.aikeyboard.oem.debug.OemDeveloperSessionArchiveStore
import com.paopaoime.aikeyboard.sdk.core.OemDeveloperAddon
import com.paopaoime.aikeyboard.sdk.core.OemDeveloperAiModelConfig
import com.paopaoime.aikeyboard.sdk.core.OemDeveloperAiTonePreset
import com.paopaoime.aikeyboard.sdk.core.OemDeveloperResolvedAiReplyConfig
import com.paopaoime.aikeyboard.sdk.core.OemSessionArchiveEvent

class DeveloperAddonImpl : OemDeveloperAddon {
    override fun initializeRuntime(application: Application) {
        OemDeveloperMaterialCallbackRuntime.install(application)
    }

    override fun loadAiModelConfig(context: Context): OemDeveloperAiModelConfig {
        val source = OemDeveloperAiConfigStore.load(context).modelConfig
        return OemDeveloperAiModelConfig(
            apiBaseUrl = source.apiBaseUrl,
            aiOpenAiBaseUrl = source.aiOpenAiBaseUrl,
            aiOpenAiApiKey = source.aiOpenAiApiKey,
            aiOpenAiModel = source.aiOpenAiModel,
            temperature = source.temperature,
            topP = source.topP,
            maxTokens = source.maxTokens,
        )
    }

    override fun resolveAiReplyConfig(context: Context): OemDeveloperResolvedAiReplyConfig {
        val source = OemDeveloperAiConfigStore.resolveReplyConfig(context)
        return OemDeveloperResolvedAiReplyConfig(
            systemPrompt = source.systemPrompt,
            tonePresets = source.tonePresets.map { preset ->
                OemDeveloperAiTonePreset(
                    label = preset.label,
                    promptHint = preset.promptHint,
                )
            },
            replyCount = source.replyCount,
            replyLengthPolicy = source.replyLengthPolicy,
            businessRole = source.businessRole,
            minChars = source.minChars,
            maxChars = source.maxChars,
            allowEmoji = source.allowEmoji,
            avoidNickname = source.avoidNickname,
            language = source.language,
        )
    }

    override fun saveSessionArchiveEvent(context: Context, event: OemSessionArchiveEvent) {
        OemDeveloperSessionArchiveStore.save(context, event)
    }

    override fun createDeveloperSettingsIntent(context: Context): Intent? {
        return Intent(context, DeveloperSettingsHostActivity::class.java)
    }
}
