package com.paopaoime.aikeyboard.ime.data

import android.content.Context

object PersonaCapabilityStore {
    private const val PREFS_NAME = "feifei_companion"
    private const val KEY_SELECTED_DIRS = "selected_kb_directory_ids"
    private const val KEY_ENABLED_SKILLS = "enabled_skill_ids"
    private const val KEY_PERSONA_NICKNAME = "persona_nickname"
    private const val KEY_PERSONA_STYLE = "persona_style"
    private const val KEY_PERSONA_PROMPT = "persona_prompt"

    data class PersonaProfile(
        val nickname: String,
        val speakingStyle: String,
        val prompt: String
    )

    fun getSelectedDirectoryIds(context: Context, personaId: String): Set<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getStringSet("${KEY_SELECTED_DIRS}_$personaId", emptySet()) ?: emptySet()
    }

    fun getEnabledSkillIds(context: Context, personaId: String): Set<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getStringSet("${KEY_ENABLED_SKILLS}_$personaId", emptySet()) ?: emptySet()
    }

    fun isSkillEnabled(context: Context, personaId: String, skillId: String): Boolean {
        return getEnabledSkillIds(context, personaId).contains(skillId)
    }

    fun setSkillEnabled(context: Context, personaId: String, skillId: String, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val key = "${KEY_ENABLED_SKILLS}_$personaId"
        val current = prefs.getStringSet(key, emptySet())?.toMutableSet() ?: mutableSetOf()
        if (enabled) {
            current.add(skillId)
        } else {
            current.remove(skillId)
        }
        prefs.edit().putStringSet(key, current).apply()
    }

    fun getPersonaProfile(context: Context, personaId: String): PersonaProfile {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val defaults = defaultProfile(personaId)
        val nickname = prefs.getString("${KEY_PERSONA_NICKNAME}_$personaId", defaults.nickname).orEmpty()
        val style = prefs.getString("${KEY_PERSONA_STYLE}_$personaId", defaults.speakingStyle).orEmpty()
        val prompt = prefs.getString("${KEY_PERSONA_PROMPT}_$personaId", defaults.prompt).orEmpty()
        return PersonaProfile(
            nickname = nickname.ifBlank { defaults.nickname },
            speakingStyle = style.ifBlank { defaults.speakingStyle },
            prompt = prompt.ifBlank { defaults.prompt }
        )
    }

    fun setPersonaProfile(context: Context, personaId: String, profile: PersonaProfile) {
        val defaults = defaultProfile(personaId)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString("${KEY_PERSONA_NICKNAME}_$personaId", profile.nickname.ifBlank { defaults.nickname })
            .putString("${KEY_PERSONA_STYLE}_$personaId", profile.speakingStyle.ifBlank { defaults.speakingStyle })
            .putString("${KEY_PERSONA_PROMPT}_$personaId", profile.prompt.ifBlank { defaults.prompt })
            .apply()
    }

    fun defaultProfile(personaId: String): PersonaProfile {
        return when (personaId) {
            "npc1" -> PersonaProfile(
                nickname = "新新",
                speakingStyle = "热情简洁",
                prompt = "你是新新，语气热情、轻松、短句、偏鼓励，优先给行动建议。"
            )
            "npc2" -> PersonaProfile(
                nickname = "神小仙",
                speakingStyle = "冷静分析",
                prompt = "你是神小仙，语气冷静、条理化、偏分析，先结论后原因。"
            )
            "npc3" -> PersonaProfile(
                nickname = "白老板",
                speakingStyle = "结果导向",
                prompt = "你是白老板，语气克制、结果导向、关注风险与收益，不空话。"
            )
            else -> PersonaProfile(
                nickname = personaId,
                speakingStyle = "自然口语",
                prompt = "请使用自然中文口语，简洁表达观点。"
            )
        }
    }
}
