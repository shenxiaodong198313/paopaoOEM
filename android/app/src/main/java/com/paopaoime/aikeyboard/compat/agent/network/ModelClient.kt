package com.paopaoime.aikeyboard.compat.agent.network

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException

interface OpenAIApi {
    @POST("chat/completions")
    suspend fun chatCompletion(
        @Header("Authorization") authorization: String,
        @Body request: ChatRequest
    ): retrofit2.Response<ChatResponse>
}

class ModelClient(
    private val baseUrl: String,
    private val apiKey: String,
    private val modelName: String,
    private val isGemini: Boolean = false,
    private val forceOpenAiCompat: Boolean = false,
    private val networkTimeoutMs: Long = 60_000L
) {

    private val openAiApi: OpenAIApi?
    private val geminiApi: GeminiApi?
    private val doubaoApi: DoubaoApi?

    data class VerificationResult(
        val isSuccessful: Boolean,
        val error: String? = null
    )

    data class TokenUsage(
        val model: String,
        val promptTokens: Int,
        val completionTokens: Int,
        val totalTokens: Int,
        val timestamp: Long = System.currentTimeMillis()
    )

    init {
        val timeoutMs = if (networkTimeoutMs > 0) networkTimeoutMs else 60_000L
        // val logging = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.NONE }
        val client = OkHttpClient.Builder()
            // .addInterceptor(logging)
            .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .writeTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .build()

        val finalBaseUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        Log.d("ModelClient", "ModelClient initialized with Base URL: $finalBaseUrl")
        
        val retrofit = Retrofit.Builder()
            .baseUrl(finalBaseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        val isDoubao = modelName.contains("doubao", ignoreCase = true) || baseUrl.contains("volces.com", ignoreCase = true)

        if (forceOpenAiCompat) {
            openAiApi = retrofit.create(OpenAIApi::class.java)
            geminiApi = null
            doubaoApi = null
        } else if (isGemini) {
            openAiApi = null
            geminiApi = retrofit.create(GeminiApi::class.java)
            doubaoApi = null
        } else if (isDoubao) {
            openAiApi = null
            geminiApi = null
            doubaoApi = retrofit.create(DoubaoApi::class.java)
        } else {
            openAiApi = retrofit.create(OpenAIApi::class.java)
            geminiApi = null
            doubaoApi = null
        }
    }

    suspend fun sendRequest(
        history: List<Message>,
        screenshot: Bitmap?,
        isReasoning: Boolean = false,
        thinkingEnabled: Boolean? = null,
        maxTokensOverride: Int? = null,
        temperatureOverride: Double? = null,
        topPOverride: Double? = null,
        onTokenUsage: ((TokenUsage) -> Unit)? = null
    ): String {
        Log.d("ModelClient", "ModelClient.sendRequest called. isGemini: $isGemini, isReasoning: $isReasoning")
        if (forceOpenAiCompat) {
            return sendOpenAIRequest(
                history = history,
                isReasoning = isReasoning,
                thinkingEnabled = thinkingEnabled,
                maxTokensOverride = maxTokensOverride,
                temperatureOverride = temperatureOverride,
                topPOverride = topPOverride,
                onTokenUsage = onTokenUsage,
            )
        }
        val isDoubao = modelName.contains("doubao", ignoreCase = true) || baseUrl.contains("volces.com", ignoreCase = true)
        return if (isGemini) {
            sendGeminiRequest(history)
        } else if (isDoubao) {
            sendDoubaoRequest(history, thinkingEnabled, onTokenUsage)
        } else {
            sendOpenAIRequest(
                history = history,
                isReasoning = isReasoning,
                thinkingEnabled = thinkingEnabled,
                maxTokensOverride = maxTokensOverride,
                temperatureOverride = temperatureOverride,
                topPOverride = topPOverride,
                onTokenUsage = onTokenUsage,
            )
        }
    }

    suspend fun verifyConnection(): VerificationResult {
        val responseText = sendRequest(
            history = listOf(Message(role = "user", content = "Reply with exactly: OK")),
            screenshot = null
        ).trim()

        val isError = responseText.startsWith("Error:", ignoreCase = true) ||
            responseText.startsWith("Network Error:", ignoreCase = true)

        if (isError) {
            return VerificationResult(isSuccessful = false, error = responseText)
        }

        if (responseText.isBlank()) {
            return VerificationResult(isSuccessful = false, error = "Empty response")
        }

        return VerificationResult(isSuccessful = true)
    }

    private suspend fun sendGeminiRequest(history: List<Message>): String {
        Log.d("ModelClient", "sendGeminiRequest called")
        if (geminiApi == null) {
            Log.e("ModelClient", "Gemini API not initialized")
            return "Error: Gemini API not initialized"
        }

        val geminiContents = mutableListOf<GeminiContent>()
        var currentRole: String? = null
        var currentParts = mutableListOf<GeminiPart>()

        history.forEach { msg ->
            val mappedRole = if (msg.role == "system" || msg.role == "user") "user" else "model"
            
            val parts = mutableListOf<GeminiPart>()
            if (msg.role == "system") {
                 parts.add(GeminiPart(text = "System Instruction: ${msg.content}"))
            } else if (msg.content is String) {
                parts.add(GeminiPart(text = msg.content as String))
            } else if (msg.content is List<*>) {
                @Suppress("UNCHECKED_CAST")
                val list = msg.content as List<ContentItem>
                list.forEach { item ->
                    if (item.type == "text") {
                        parts.add(GeminiPart(text = item.text))
                    } else if (item.type == "image_url") {
                        val url = item.imageUrl?.url ?: ""
                        if (url.startsWith("data:")) {
                            val commaIndex = url.indexOf(",")
                            if (commaIndex != -1) {
                                val base64Data = url.substring(commaIndex + 1)
                                val mimeType = url.substring(5, url.indexOf(";"))
                                parts.add(GeminiPart(inline_data = GeminiInlineData(mimeType, base64Data)))
                            }
                        }
                    }
                }
            }

            if (mappedRole == currentRole) {
                currentParts.addAll(parts)
            } else {
                if (currentRole != null) {
                    geminiContents.add(GeminiContent(role = currentRole!!, parts = currentParts.toList()))
                }
                currentRole = mappedRole
                currentParts = parts.toMutableList()
            }
        }
        
        if (currentRole != null) {
            geminiContents.add(GeminiContent(role = currentRole!!, parts = currentParts.toList()))
        }

        val request = GeminiRequest(
            contents = geminiContents,
            generationConfig = GeminiGenerationConfig(temperature = 0.0)
        )
        Log.d("ModelClient", "Gemini Request prepared. Content count: ${geminiContents.size}")

        try {
            // Default model fallback if not specified properly for Gemini
            val model = if (modelName.isBlank() || modelName == "autoglm-phone") "gemini-1.5-flash-latest" else modelName
            Log.d("ModelClient", "Calling Gemini API with model: $model")
            val response = geminiApi.generateContent(model, apiKey, request)
            if (response.isSuccessful) {
                Log.d("ModelClient", "Gemini API success")
                val text = response.body()?.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                return text ?: ""
            } else {
                val errorBody = response.errorBody()?.string()
                Log.e("ModelClient", "Gemini API Error: $errorBody")
                val errorMessage = try {
                    val json = org.json.JSONObject(errorBody ?: "")
                    val errorObj = json.optJSONObject("error")
                    errorObj?.optString("message") ?: errorBody
                } catch (e: Exception) {
                    errorBody
                }
                return "Error: ${response.code()} $errorMessage"
            }
        } catch (e: CancellationException) {
            // Rethrow cancellation to let the ViewModel handle it properly
            throw e
        } catch (e: Exception) {
            Log.e("ModelClient", "Gemini API Exception", e)
            val errorMessage = when (e) {
                is javax.net.ssl.SSLHandshakeException -> "Network Error: SSL Handshake failed. Check your VPN or Proxy settings."
                is java.net.SocketTimeoutException -> "Network Error: Connection timed out. Check your internet connection."
                is java.net.UnknownHostException -> "Network Error: Unknown host. Check the Base URL."
                else -> "Error: ${e.message}"
            }
            return errorMessage
        }
    }

    private suspend fun sendDoubaoRequest(
        history: List<Message>,
        thinkingEnabled: Boolean?,
        onTokenUsage: ((TokenUsage) -> Unit)?
    ): String {
        Log.d("ModelClient", "sendDoubaoRequest called")
        if (doubaoApi == null) {
            Log.e("ModelClient", "Doubao API not initialized")
            return "Error: Doubao API not initialized"
        }
        Log.d(
            "ModelClient",
            "Doubao request model=${modelName.trim()} baseUrl=${baseUrl.trim()} apiKeyLen=${apiKey.trim().length}"
        )
        val (thinking, reasoningEffort) = if (thinkingEnabled == null) {
            Pair(null, "medium")
        } else if (thinkingEnabled) {
            Pair(DoubaoThinkingConfig("enabled"), "medium")
        } else {
            Pair(DoubaoThinkingConfig("disabled"), null)
        }
        var candidateMessages = history
        var completionBudget = 1200
        val contextWindow = resolveContextWindowTokens()

        repeat(3) { attempt ->
            val inputBudget = (contextWindow - completionBudget).coerceAtLeast(1200)
            candidateMessages = normalizeMessagesForBudget(candidateMessages, inputBudget)
            val doubaoMessages = candidateMessages.map { msg ->
                if (msg.content is String) {
                    DoubaoMessage(msg.role, msg.content)
                } else {
                    @Suppress("UNCHECKED_CAST")
                    val contentList = msg.content as List<ContentItem>
                    val doubaoContentList = contentList.map { item ->
                        if (item.type == "text") {
                            DoubaoContentItem(type = "text", text = item.text)
                        } else {
                            DoubaoContentItem(type = "image_url", imageUrl = DoubaoImageUrl(item.imageUrl?.url ?: ""))
                        }
                    }
                    DoubaoMessage(msg.role, doubaoContentList)
                }
            }
            val request = DoubaoRequest(
                model = modelName,
                messages = doubaoMessages,
                maxCompletionTokens = completionBudget,
                stream = false,
                reasoningEffort = reasoningEffort,
                thinking = thinking
            )
            try {
                val response = doubaoApi.chatCompletion("Bearer $apiKey", request)
                if (response.isSuccessful) {
                    val body = response.body()
                    val usage = body?.usage
                    val promptTokens = usage?.promptTokens ?: 0
                    val completionTokens = usage?.completionTokens ?: 0
                    val totalTokens = usage?.totalTokens ?: (promptTokens + completionTokens)
                    onTokenUsage?.invoke(
                        TokenUsage(
                            model = body?.model ?: modelName,
                            promptTokens = promptTokens,
                            completionTokens = completionTokens,
                            totalTokens = totalTokens
                        )
                    )
                    return body?.choices?.firstOrNull()?.message?.content ?: ""
                }
                val errorBody = response.errorBody()?.string()
                Log.e("ModelClient", "Doubao API Error: $errorBody")
                val errorMessage = try {
                    val json = org.json.JSONObject(errorBody ?: "")
                    val errorObj = json.optJSONObject("error")
                    errorObj?.optString("message") ?: errorBody
                } catch (e: Exception) {
                    errorBody
                }.orEmpty()
                if (isContextOverflowError(errorMessage) && attempt < 2) {
                    completionBudget = (completionBudget * 0.7f).toInt().coerceAtLeast(320)
                    candidateMessages = normalizeMessagesForBudget(candidateMessages, (inputBudget * 0.65f).toInt())
                    return@repeat
                }
                return "Error: ${response.code()} $errorMessage"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("ModelClient", "Doubao API Exception", e)
                return "Error: ${e.message}"
            }
        }
        return "Error: 模型上下文过长，已自动重试但仍失败"
    }

    private suspend fun sendOpenAIRequest(
        history: List<Message>,
        isReasoning: Boolean,
        thinkingEnabled: Boolean?,
        maxTokensOverride: Int?,
        temperatureOverride: Double?,
        topPOverride: Double?,
        onTokenUsage: ((TokenUsage) -> Unit)?
    ): String {
        Log.d("ModelClient", "sendOpenAIRequest called. isReasoning: $isReasoning")
        if (openAiApi == null) {
            Log.e("ModelClient", "OpenAI API not initialized")
            return "Error: OpenAI API not initialized"
        }
        
        // DeepSeek API (and some others) might not support image_url or the array content format.
        // Specifically, the error "unknown variant image_url, expected text" implies the API expects a simple string content.
        val isDeepSeek = modelName.contains("deepseek", ignoreCase = true) || baseUrl.contains("deepseek", ignoreCase = true)
        
        val finalMessages = if (isDeepSeek) {
             Log.w("ModelClient", "DeepSeek detected, stripping images from request to avoid API error.")
             history.map { msg ->
                if (msg.content is List<*>) {
                    val textContent = (msg.content as List<*>)
                        .filterIsInstance<ContentItem>()
                        .filter { it.type == "text" }
                        .joinToString("\n") { it.text ?: "" }
                    Message(msg.role, textContent)
                } else {
                    msg
                }
            }
        } else {
            history
        }
        
        var candidateMessages = finalMessages
        var completionBudget = maxTokensOverride ?: if (isReasoning) 1200 else 3000
        val contextWindow = resolveContextWindowTokens()
        val hasImagePayload = history.any { msg ->
            (msg.content as? List<*>)?.filterIsInstance<ContentItem>()?.any { it.type == "image_url" } == true
        }
        val isBigModel = baseUrl.contains("open.bigmodel.cn", ignoreCase = true)
        val fallbackVisionModel = "glm-4.6v-flash"

        repeat(3) { attempt ->
            val inputBudget = (contextWindow - completionBudget).coerceAtLeast(1500)
            candidateMessages = normalizeMessagesForBudget(candidateMessages, inputBudget)
            val requestModel = if (hasImagePayload && isBigModel && !isVisionModel(modelName)) {
                fallbackVisionModel
            } else {
                modelName
            }
            Log.d(
                "ModelClient",
                "OpenAI request attempt=${attempt + 1} model=$requestModel baseUrl=$baseUrl hasImage=$hasImagePayload isReasoning=$isReasoning"
            )
            val request = if (isReasoning) {
                ChatRequest(
                    model = requestModel,
                    messages = candidateMessages,
                    maxCompletionTokens = null,
                    maxTokens = null,
                    temperature = null,
                    topP = null,
                    frequencyPenalty = null,
                    reasoningEffort = null,
                    thinking = null,
                    enableThinking = thinkingEnabled
                )
            } else {
                ChatRequest(
                    model = requestModel,
                    messages = candidateMessages,
                    maxTokens = completionBudget,
                    temperature = temperatureOverride ?: 0.0,
                    topP = topPOverride ?: 0.85,
                    frequencyPenalty = null,
                    thinking = null,
                    enableThinking = thinkingEnabled
                )
            }
            try {
                val response = openAiApi.chatCompletion("Bearer $apiKey", request)
                if (response.isSuccessful) {
                    val body = response.body()
                    val usage = body?.usage
                    val promptTokens = usage?.promptTokens ?: 0
                    val completionTokens = usage?.completionTokens ?: 0
                    val totalTokens = usage?.totalTokens ?: (promptTokens + completionTokens)
                    onTokenUsage?.invoke(
                        TokenUsage(
                            model = body?.model ?: modelName,
                            promptTokens = promptTokens,
                            completionTokens = completionTokens,
                            totalTokens = totalTokens
                        )
                    )
                    return body?.choices?.firstOrNull()?.message?.content ?: ""
                }
                val errorBody = response.errorBody()?.string()
                Log.e("ModelClient", "API Error: $errorBody")
                val errorMessage = try {
                    val json = org.json.JSONObject(errorBody ?: "")
                    val errorObj = json.optJSONObject("error")
                    errorObj?.optString("message") ?: errorBody
                } catch (e: Exception) {
                    errorBody
                }.orEmpty()
                val errorCode = try {
                    val json = org.json.JSONObject(errorBody ?: "")
                    val errorObj = json.optJSONObject("error")
                    errorObj?.optString("code").orEmpty()
                } catch (e: Exception) {
                    ""
                }
                if (isContextOverflowError(errorMessage) && attempt < 2) {
                    completionBudget = (completionBudget * 0.6f).toInt().coerceAtLeast(512)
                    candidateMessages = normalizeMessagesForBudget(candidateMessages, (inputBudget * 0.6f).toInt())
                    Log.w("ModelClient", "Context overflow detected, auto retry #${attempt + 1} with reduced budgets")
                    return@repeat
                }
                if (errorCode == "1210" && attempt < 2) {
                    completionBudget = (completionBudget * 0.7f).toInt().coerceAtLeast(512)
                    candidateMessages = normalizeMessagesForBudget(candidateMessages, (inputBudget * 0.7f).toInt())
                    Log.w("ModelClient", "Provider rejected parameters(1210), auto retry #${attempt + 1} with minimal request")
                    return@repeat
                }
                return "Error: ${response.code()} $errorMessage"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("ModelClient", "Exception", e)
                return "Error: ${e.message}"
            }
        }
        return "Error: 模型上下文过长，已自动重试但仍失败"
    }

    private fun isVisionModel(name: String): Boolean {
        val model = name.lowercase()
        return model.contains("vl") ||
            model.contains("vision") ||
            model.contains("autoglm") ||
            model.contains("4.5v") ||
            model.contains("4.6v") ||
            model.contains("qwen-vl") ||
            model.contains("qwen3.6-flash") ||
            model.contains("qwen36flash")
    }

    private fun resolveContextWindowTokens(): Int {
        val source = "${modelName.lowercase()}|${baseUrl.lowercase()}"
        val explicit = Regex("""(\d{4,6})k""").find(source)?.groupValues?.getOrNull(1)?.toIntOrNull()
        if (explicit != null) {
            return explicit * 1000
        }
        return when {
            source.contains("qwen2.5-vl-3b") || source.contains("3b") -> 12_000
            source.contains("qwen2.5-vl-7b") || source.contains("7b") -> 16_000
            source.contains("32k") -> 32_000
            source.contains("128k") -> 128_000
            else -> 24_000
        }
    }

    private fun isContextOverflowError(message: String): Boolean {
        val normalized = message.lowercase()
        return normalized.contains("maximum context length") ||
            normalized.contains("requested token count exceeds") ||
            normalized.contains("context length") ||
            normalized.contains("too many tokens")
    }

    private fun trimMessagesByBudget(messages: List<Message>, tokenBudget: Int): List<Message> {
        if (messages.isEmpty()) return messages
        val system = messages.firstOrNull { it.role == "system" }
        val nonSystem = messages.filterNot { it.role == "system" }
        val selected = ArrayDeque<Message>()
        var used = system?.let { estimateMessageTokens(it) } ?: 0
        for (msg in nonSystem.asReversed()) {
            val cost = estimateMessageTokens(msg)
            if (used + cost > tokenBudget && selected.isNotEmpty()) {
                break
            }
            selected.addFirst(msg)
            used += cost
            if (used >= tokenBudget) {
                break
            }
        }
        return buildList {
            if (system != null) add(system)
            addAll(selected)
        }
    }

    private fun normalizeMessagesForBudget(messages: List<Message>, tokenBudget: Int): List<Message> {
        if (messages.isEmpty()) return messages
        val trimmed = trimMessagesByBudget(messages, tokenBudget)
        val perMessageBudget = (tokenBudget / trimmed.size.coerceAtLeast(1)).coerceIn(220, 1400)
        return trimmed.map { message ->
            when (val content = message.content) {
                is String -> {
                    val maxChars = (perMessageBudget * 2).coerceAtLeast(240)
                    if (content.length <= maxChars) {
                        message
                    } else {
                        message.copy(content = content.take(maxChars))
                    }
                }
                is List<*> -> {
                    val normalizedItems = content.filterIsInstance<ContentItem>().map { item ->
                        if (item.type == "text") {
                            val text = item.text.orEmpty()
                            val maxChars = (perMessageBudget * 2).coerceAtLeast(240)
                            if (text.length <= maxChars) item else item.copy(text = text.take(maxChars))
                        } else {
                            item
                        }
                    }
                    message.copy(content = normalizedItems)
                }
                else -> message
            }
        }
    }

    private fun estimateMessageTokens(message: Message): Int {
        return when (val content = message.content) {
            is String -> (content.length / 2).coerceAtLeast(1) + 28
            is List<*> -> {
                var tokens = 24
                content.filterIsInstance<ContentItem>().forEach { item ->
                    if (item.type == "text") {
                        tokens += (item.text.orEmpty().length / 2).coerceAtLeast(1)
                    } else if (item.type == "image_url") {
                        tokens += 2200
                    }
                }
                tokens
            }
            else -> 32
        }
    }
    
    companion object {
        val SYSTEM_PROMPT = """
浣犳槸涓€涓墜鏈?GUI 鑷姩鍖栨墽琛屾櫤鑳戒綋銆備綘浼氭敹鍒颁换鍔°€佸巻鍙叉搷浣滃拰褰撳墠鎴浘銆備綘鐨勮亴璐ｆ槸鎸佺画鎵ц浠诲姟锛屼笉鏄瑙ｄ换鍔°€傛瘡杞彧杈撳嚭涓€涓渶鍚堢悊鐨勪笅涓€姝ュ姩浣滐紝鐩村埌浠诲姟缁撴潫銆?
## 杈撳嚭鏍煎紡
姣忔鎿嶄綔蹇呴』鍖呭惈涓ら儴鍒嗭細
1. <think></think>锛氳缁嗚鏄庝綘鐨勫垎鏋愬拰鍐崇瓥
2. <answer></answer>锛氳繑鍥炲敮涓€涓€涓姩浣滐紙do(...) 鎴?finish(message="...")锛?
绀轰緥锛?<think>
褰撳墠灞忓箷鏄剧ず寰俊棣栭〉銆傜洰鏍囨槸杩涘叆鍙戠幇椤靛苟鐐瑰嚮鏈嬪弸鍦堝叆鍙ｃ€?涓嬩竴姝ュ厛鐐瑰嚮搴曢儴鈥滃彂鐜扳€濇爣绛俱€?</think>
<answer>
do(action="Tap", element=[500,950])
</answer>

## 鍔ㄤ綔绌洪棿锛堜弗鏍奸伒瀹堬級

### 鍩虹鎿嶄綔
- do(action="Tap", element=[x,y])
- do(action="Long Press", element=[x,y])
- do(action="Type", text="...")
- do(action="Type_Name", text="...")

### 婊戝姩鎿嶄綔
- do(action="Swipe", start=[x1,y1], end=[x2,y2])

### 绯荤粺鎿嶄綔
- do(action="Launch", app="搴旂敤鍚?)
- do(action="Back")
- do(action="Home")

### 浠诲姟鎺у埗
- do(action="Wait", duration="x seconds")
- do(action="Interact")
- do(action="Take_over", message="...")
- do(action="Note", message="True")
- do(action="Call_API", instruction="...")
- finish(message="...")

## 鍧愭爣绯荤粺
- 鑼冨洿锛歺 鍜?y 閮藉湪 [0,1000] 涔嬮棿
- 鍘熺偣锛?0,0) 鏄睆骞曞乏涓婅
- 杈圭晫锛?1000,1000) 鏄睆骞曞彸涓嬭
- 绮惧害锛氬潗鏍囨槸褰掍竴鍖栫殑锛屼細鑷姩鏄犲皠鍒板疄闄呭睆骞曞垎杈ㄧ巼

## 鎿嶄綔鎸囧崡

### 鎬濊€冭繃绋嬪缓璁?鍦?<think> 涓簲鍖呭惈锛?1. 瑙傚療锛氬綋鍓嶅睆骞曟樉绀虹殑鍐呭鍜岀姸鎬?2. 鍒嗘瀽锛氳瘑鍒洰鏍囧厓绱犵殑浣嶇疆鍜岀壒寰?3. 鍐崇瓥锛氶€夋嫨鏈€鍚堥€傜殑鎿嶄綔鍜屽弬鏁?4. 鎬荤粨锛氱敤涓€鍙ヨ瘽鏄庣‘涓嬩竴姝ュ姩浣?
### 甯歌搴旂敤鎿嶄綔鎶€宸?- 椤甸潰鍐呭鏈畬鍏ㄦ樉绀烘椂锛屼紭鍏?Swipe 婊氬姩鏌ョ湅
- 杈撳叆鏂囨湰鍓嶅繀椤诲厛 Tap 杈撳叆妗嗚幏寰楃劍鐐?- 閬囧埌鍔犺浇鍔ㄧ敾鍙?Wait 涓€娆★紝閬垮厤杩炵画 Wait
- 鏃犳硶鎵惧埌鐩爣鍏冪礌鏃讹紝浼樺厛 Back 閲嶆柊瀵艰埅

### 甯歌閿欒閬垮厤
- 涓嶈鍦ㄦ湭鐐瑰嚮杈撳叆妗嗙殑鎯呭喌涓嬬洿鎺?Type
- 涓嶈浣跨敤瓒呭嚭 [0,1000] 鑼冨洿鐨勫潗鏍?- 涓嶈閬楁紡 <think> 鎴?<answer> 鏍囩
- 涓嶈涓€娆¤緭鍑哄涓姩浣?- 涓嶈杩炵画鎵ц澶氫釜鐩稿悓鐨勬棤鏁堟搷浣?
## 娉ㄦ剰浜嬮」
- 蹇呴』涓ユ牸閬靛惊鍔ㄤ綔绌洪棿锛屽弬鏁板繀椤荤鍚堟牸寮?- 鍧愭爣蹇呴』鏄?[0,1000] 鐨勬暣鏁?- 姣忔鍙繑鍥炰竴涓姩浣?- 杩斿洖/閫€鍑哄綋鍓嶉〉闈㈡椂锛屼紭鍏堜娇鐢?do(action="Back")
- 鐐瑰嚮鍚庤嫢鏈揪棰勬湡锛屽厛鍋氫竴娆′慨姝ｅ啀缁х画
- 浠诲姟瀹屾垚鎴栧け璐ユ椂锛屽繀椤昏緭鍑?finish(message="...") 浣滀负缁撴潫鍔ㄤ綔
""".trimIndent()

        fun bitmapToBase64(bitmap: Bitmap, quality: Int = 92): String {
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            val bytes = outputStream.toByteArray()
            Log.d("ModelClient", "Image Base64 size: ${bytes.size / 1024} KB")
            return Base64.encodeToString(bytes, Base64.NO_WRAP)
        }
    }
}
