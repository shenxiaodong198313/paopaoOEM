package com.paopaoime.aikeyboard.qa

import android.content.Context
import android.text.InputType
import android.view.inputmethod.EditorInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.paopaoime.aikeyboard.qa.data.QaDatabase
import com.paopaoime.aikeyboard.qa.data.QaSessionEntity
import com.paopaoime.aikeyboard.oem.OemEnterpriseConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.UUID

import kotlinx.coroutines.Job
import kotlinx.coroutines.delay

class InputQaCaptureManager private constructor(private val context: Context) {

    private val db = QaDatabase.getInstance(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var currentPackageName: String = "未知应用"
    private var isPassword: Boolean = false
    private var isOwnApp: Boolean = false

    private var pendingSaveJob: Job? = null
    private val sessionBuilder = java.lang.StringBuilder()
    private var currentSessionId: String? = null
    private var currentInputSource: String = "type"

    companion object {
        @Volatile
        private var INSTANCE: InputQaCaptureManager? = null

        fun getInstance(context: Context): InputQaCaptureManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: InputQaCaptureManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    /**
     * 当键盘唤起时调用
     */
    fun onStartInputView(editorInfo: EditorInfo?) {
        if (!InputQaConfig.isEnabled(context)) return

        val newPackageName = editorInfo?.packageName ?: "未知应用"
        
        // 过滤输入法自身的内部输入框（如 AI 创作、候选词面板内的搜索等）
        if (newPackageName == context.packageName) {
            isOwnApp = !OemEnterpriseConfig.shouldCaptureOwnPackageInput()
            if (isOwnApp) {
                return
            }
        } else {
            isOwnApp = false
        }
        
        // 取消可能的结束延时
        pendingSaveJob?.cancel()

        // 如果切换了应用，立即保存上一段内容
        if (newPackageName != currentPackageName && currentPackageName != "未知应用") {
            forceSaveCurrentSession()
        }
        
        currentPackageName = newPackageName
        
        // 输入类型校验，过滤密码框
        val inputType = editorInfo?.inputType ?: 0
        val clazz = inputType and InputType.TYPE_MASK_CLASS
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        
        isPassword = if (clazz == InputType.TYPE_CLASS_TEXT) {
            variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
        } else if (clazz == InputType.TYPE_CLASS_NUMBER) {
            variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
        } else {
            false
        }
    }

    /**
     * 当发生文本上屏时调用
     */
    fun onCommitText(text: CharSequence) {
        if (!InputQaConfig.isEnabled(context)) return
        if (isOwnApp) return
        if (isPassword) return
        if (InputQaConfig.blockedPackages.contains(currentPackageName)) return
        
        val content = text.toString()
        if (content.isBlank()) return
        
        if (currentSessionId == null) {
            currentSessionId = UUID.randomUUID().toString()
        }
        
        if (text.length > 20) {
            currentInputSource = "paste"
        }
        
        sessionBuilder.append(text)
        scheduleSave(resetContextAfterSave = false)
    }

    /**
     * 当触发剪贴板粘贴时调用
     */
    fun onPasteText(text: CharSequence) {
        if (!InputQaConfig.isEnabled(context)) return
        if (isOwnApp) return
        if (isPassword) return
        if (InputQaConfig.blockedPackages.contains(currentPackageName)) return
        
        val content = text.toString()
        if (content.isBlank()) return
        
        if (currentSessionId == null) {
            currentSessionId = UUID.randomUUID().toString()
        }
        currentInputSource = "paste"
        sessionBuilder.append(text)
        scheduleSave(resetContextAfterSave = false)
    }

    /**
     * 当键盘收起时调用
     */
    fun onFinishInputView() {
        if (isOwnApp) return
        // 收起键盘时尽快保存一条，并重置上下文，避免同一宿主多次输入被并成一条。
        scheduleSave(resetContextAfterSave = true, delayMs = 800)
    }

    private fun scheduleSave(resetContextAfterSave: Boolean, delayMs: Long = 1500) {
        pendingSaveJob?.cancel()
        pendingSaveJob = mainScope.launch {
            delay(delayMs)
            forceSaveCurrentSession()
            if (resetContextAfterSave) {
                currentPackageName = "未知应用"
                isPassword = false
            }
        }
    }

    private fun forceSaveCurrentSession() {
        pendingSaveJob?.cancel()
        if (sessionBuilder.isNotEmpty()) {
            val sId = currentSessionId ?: UUID.randomUUID().toString()
            val content = sessionBuilder.toString()
            val source = currentInputSource
            val pkg = currentPackageName
            
            // 重置状态
            sessionBuilder.clear()
            currentSessionId = null
            currentInputSource = "type"
            
            scope.launch {
                processAndSaveSession(sId, pkg, content, source)
            }
        }
    }

    private suspend fun processAndSaveSession(sessionId: String, packageName: String, content: String, inputSource: String) {
        val currentPolicy = InputQaConfig.resolvePolicy(context)
        // 获取应用名称
        var appName = packageName
        try {
            if (packageName != "未知应用") {
                val pm = context.packageManager
                val applicationInfo = pm.getApplicationInfo(packageName, 0)
                appName = pm.getApplicationLabel(applicationInfo).toString()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 简单的正则脱敏 (PII Masking)
        var maskedContent = content
        // 脱敏手机号: 1开头11位数字
        maskedContent = maskedContent.replace(Regex("1[3-9]\\d{9}"), "1***MASKED***")
        // 脱敏身份证: 简单匹配18位
        maskedContent = maskedContent.replace(Regex("\\d{17}[\\dXx]"), "ID***MASKED***")

        val entity = QaSessionEntity(
            sessionId = sessionId,
            packageName = packageName,
            appName = appName,
            qaContent = maskedContent,
            inputSource = inputSource,
            timestamp = System.currentTimeMillis(),
            policyState = currentPolicy.name
        )

        db.qaSessionDao().insertSession(entity)

        // 除了纯本地模式，其他模式都交给统一 Worker 做投递和重试。
        if (currentPolicy != InputQaConfig.QaDataPolicy.LOCAL_ONLY) {
            val uploadWork = OneTimeWorkRequestBuilder<QaUploadWorker>().build()
            WorkManager.getInstance(context).enqueue(uploadWork)
        }
    }
}
