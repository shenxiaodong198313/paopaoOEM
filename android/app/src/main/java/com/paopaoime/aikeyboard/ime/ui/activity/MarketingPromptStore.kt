package com.paopaoime.aikeyboard.ime.ui.activity

import android.content.Context
import androidx.preference.PreferenceManager

object MarketingPromptStore {
    const val TYPE_COPY = "copy"
    const val TYPE_POSTER = "poster"
    const val TYPE_AGENT = "agent"
    const val TYPE_AGENT_XHS = "agent_xhs"

    private const val KEY_COPY_PROMPT = "marketing_prompt_copy"
    private const val KEY_POSTER_PROMPT = "marketing_prompt_poster"
    private const val KEY_AGENT_PROMPT = "marketing_prompt_agent"
    private const val KEY_AGENT_PROMPT_XHS = "marketing_prompt_agent_xhs"
    private const val KEY_COPY_BACKUP = "marketing_prompt_copy_backup"
    private const val KEY_POSTER_BACKUP = "marketing_prompt_poster_backup"
    private const val KEY_AGENT_BACKUP = "marketing_prompt_agent_backup"
    private const val KEY_AGENT_BACKUP_XHS = "marketing_prompt_agent_xhs_backup"

    private const val PLACEHOLDER_CONTEXT = "{{context}}"
    private const val PLACEHOLDER_COPY = "{{copy}}"
    private const val PLACEHOLDER_POSTER_COUNT = "{{posterCount}}"

    fun getTemplate(context: Context, type: String): String {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        ensureBackup(prefs)
        return when (type) {
            TYPE_COPY -> prefs.getString(KEY_COPY_PROMPT, defaultCopyPrompt).orEmpty()
            TYPE_POSTER -> prefs.getString(KEY_POSTER_PROMPT, defaultPosterPrompt).orEmpty()
            TYPE_AGENT -> prefs.getString(KEY_AGENT_PROMPT, defaultAgentInstruction).orEmpty()
            TYPE_AGENT_XHS -> prefs.getString(KEY_AGENT_PROMPT_XHS, defaultAgentInstructionXhs).orEmpty()
            else -> ""
        }
    }

    fun saveTemplate(context: Context, type: String, value: String) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        ensureBackup(prefs)
        val key = when (type) {
            TYPE_COPY -> KEY_COPY_PROMPT
            TYPE_POSTER -> KEY_POSTER_PROMPT
            TYPE_AGENT -> KEY_AGENT_PROMPT
            TYPE_AGENT_XHS -> KEY_AGENT_PROMPT_XHS
            else -> null
        } ?: return
        prefs.edit().putString(key, value).apply()
    }

    fun restoreTemplate(context: Context, type: String): String {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        ensureBackup(prefs)
        val (backupKey, fallback) = when (type) {
            TYPE_COPY -> KEY_COPY_BACKUP to defaultCopyPrompt
            TYPE_POSTER -> KEY_POSTER_BACKUP to defaultPosterPrompt
            TYPE_AGENT -> KEY_AGENT_BACKUP to defaultAgentInstruction
            TYPE_AGENT_XHS -> KEY_AGENT_BACKUP_XHS to defaultAgentInstructionXhs
            else -> null to ""
        }
        if (backupKey == null) return ""
        val value = prefs.getString(backupKey, fallback).orEmpty()
        val key = when (type) {
            TYPE_COPY -> KEY_COPY_PROMPT
            TYPE_POSTER -> KEY_POSTER_PROMPT
            TYPE_AGENT -> KEY_AGENT_PROMPT
            TYPE_AGENT_XHS -> KEY_AGENT_PROMPT_XHS
            else -> null
        } ?: return value
        prefs.edit().putString(key, value).apply()
        return value
    }

    private fun ensureBackup(prefs: android.content.SharedPreferences) {
        if (prefs.contains(KEY_COPY_BACKUP) &&
            prefs.contains(KEY_POSTER_BACKUP) &&
            prefs.contains(KEY_AGENT_BACKUP) &&
            prefs.contains(KEY_AGENT_BACKUP_XHS)
        ) {
            return
        }
        prefs.edit()
            .putString(KEY_COPY_BACKUP, prefs.getString(KEY_COPY_BACKUP, defaultCopyPrompt))
            .putString(KEY_POSTER_BACKUP, prefs.getString(KEY_POSTER_BACKUP, defaultPosterPrompt))
            .putString(KEY_AGENT_BACKUP, prefs.getString(KEY_AGENT_BACKUP, defaultAgentInstruction))
            .putString(KEY_AGENT_BACKUP_XHS, prefs.getString(KEY_AGENT_BACKUP_XHS, defaultAgentInstructionXhs))
            .apply()
    }

    fun getCopyPrompt(context: Context, kbContext: String): String {
        return getTemplate(context, TYPE_COPY).replace(PLACEHOLDER_CONTEXT, kbContext)
    }

    fun getPosterPrompt(context: Context, copy: String): String {
        return getTemplate(context, TYPE_POSTER).replace(PLACEHOLDER_COPY, copy)
    }

    fun getAgentInstruction(context: Context, copy: String, posterCount: Int, type: String = TYPE_AGENT): String {
        return getTemplate(context, type)
            .replace(PLACEHOLDER_COPY, copy)
            .replace(PLACEHOLDER_POSTER_COUNT, posterCount.toString())
    }

    val defaultCopyPrompt = """
        你是一个专业的社交媒体营销专家。请根据以下知识库内容（如果有），撰写一条微信朋友圈营销文案。
        知识库内容：{{context}}
        
        要求：
        1. 语气轻松活泼，富有感染力，适当使用Emoji。
        2. 字数控制在200字以内。
        3. 包含思考过程（<think>...</think>）。
    """.trimIndent()

    val defaultPosterPrompt = """
        你是一个资深的视觉创意总监。请基于下方提供的[营销文案]，为该产品/服务设计一张极具吸引力的商业海报，并输出结构化的生图提示词。
        
        [营销文案]：
        {{copy}}
        
        请严格按照以下结构提取并生成内容（全部使用中文输出）：
        
        1. 海报风格：[必须] 深度分析文案所属行业（如美妆、科技、餐饮、教育等），定义匹配的视觉风格。例如：科技行业突出未来感/光效；美妆行业突出唯美/水润/高级感；教育行业突出温馨/成长/希望。
        2. 背景设计：[必须] 描述具体的背景画面。要求包含主色调（如：浅蓝+紫色透明渐变）、光影效果（如：柔光、丁达尔效应）及环境氛围（如：远景模糊、科技线条背景、自然风光等）。
        3. 主标题：[必须] 从文案中提炼最核心、最吸引眼球的短句作为海报主标题。
        4. 副标题：[非必须] 补充说明主标题的辅助性文字，如无合适内容可留空。
        5. 宣传语：[非必须] 一句简短有力的Slogan，如无合适内容可留空。
        6. 主体创意：[必须] 详细描述画面中心的视觉主体。这是海报的核心，需要极强的画面感。
           * 设计建议：可以将抽象的产品卖点具象化。例如：
             - 销售增长 -> 上升的透明玻璃质感箭头与数据图表
             - 美容护肤 -> 水润通透的水滴包裹着发光的产品/花朵
             - 课程培训 -> 正在专注阅读的卡通人物/发光的书本
           * 描述范例：一个透明渐变的销售增长指标类的图形，曲线有波动但整体趋于上升，箭头突出主视觉中有个【AI】文案，文案风格与整体画面相契合，透明渐变玻璃质感，主元素被科技感丝带和线条围住。整体主视觉底部为一个立体圆形，圆形周边围绕着轻飘飘的若干个透明圆形，营造气氛，颜色为浅蓝+透明渐变的底座。整体主视觉位于画面右侧。
        
        请直接输出最终的结构化Prompt，格式如下：
        海报风格：[内容]
        背景设计：[内容]
        主标题：[内容]
        副标题：[内容]
        宣传语：[内容]
        主体创意：[内容]
    """.trimIndent()

    val defaultAgentInstruction = """
        打开微信，发布朋友圈：
        文案：{{copy}}
        图片：[已生成海报]{{posterCount}}张，位置在本机相册，默认相册最新{{posterCount}}张，按倒序选择。
        发布朋友圈文本的方法：
        1.启动微信进入微信首页（如发现不是首页则先返回微信首页）
        2.从微信底部导航进入发现频道
        3.在发现频道点击朋友圈入口
        4.在朋友圈页面点击右上角相机图标，会在页面底部弹出功能：拍摄（照片或视频）、从相册选择、取消。点击从相册选择进入发布
    """.trimIndent()

    val defaultAgentInstructionXhs = """
        打开小红书，发布笔记：
        文案：{{copy}}
        图片：[已生成海报]{{posterCount}}张，位置在本机相册的最新{{posterCount}}张，选择图片确认无误后点击右下角完成按钮。
        发布小红书笔记的方法：
        1.启动小红书
        2.从小红书底部导航进入中间红色添加图标按钮
        3.在底部弹出菜单点击从相册选择
        4.进入手机相册后选择最新的图片后，底部会弹出窗口显示一键成片、下一步按钮，此时点击下一步按钮进入图片编辑页面，继续选择下一步，进入正文填写页面
        5.将文案填写到正文输入框，可以忽略标题、话题、用户、投屏等设置，直接点击发布笔记。最终发布之前必须检查文案内容是否被填写入输入框
    """.trimIndent()
}
