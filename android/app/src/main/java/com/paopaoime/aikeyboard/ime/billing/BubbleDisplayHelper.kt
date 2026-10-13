package com.paopaoime.aikeyboard.ime.billing

import java.util.Locale
import kotlin.math.ceil

object BubbleDisplayHelper {
    private const val RMB_TO_BUBBLES = 100.0
    private const val LIBLIB_POINTS_TO_BUBBLES = 1.5
    private const val EXPLICIT_CACHE_RATE = 0.1
    private const val IMPLICIT_CACHE_RATE = 0.2

    data class BubbleEstimate(
        val amount: Int,
        val estimated: Boolean
    )

    private data class TokenPricingRule(
        val inputYuanPerMillion: Double,
        val outputYuanPerMillion: Double,
        val cacheCreationInputYuanPerMillion: Double? = null,
        val cachedInputYuanPerMillion: Double? = null
    )

    private val tokenPricingRules = linkedMapOf(
        "qwen3.6-max-preview" to TokenPricingRule(
            inputYuanPerMillion = 9.0,
            outputYuanPerMillion = 54.0,
            cacheCreationInputYuanPerMillion = 11.25,
            cachedInputYuanPerMillion = 0.9
        ),
        "qwen3.6-plus" to TokenPricingRule(
            inputYuanPerMillion = 2.0,
            outputYuanPerMillion = 12.0,
            cacheCreationInputYuanPerMillion = 2.5,
            cachedInputYuanPerMillion = 0.2
        ),
        "qwen3.6-35b-a3b" to TokenPricingRule(inputYuanPerMillion = 1.8, outputYuanPerMillion = 10.8),
        "qwen3.6-27b" to TokenPricingRule(inputYuanPerMillion = 3.0, outputYuanPerMillion = 18.0),
        "qwen3-vl-plus" to TokenPricingRule(inputYuanPerMillion = 2.0, outputYuanPerMillion = 12.0),
        "qwen3.6-flash-2026-04-16" to TokenPricingRule(
            inputYuanPerMillion = 1.2,
            outputYuanPerMillion = 7.2,
            cacheCreationInputYuanPerMillion = 1.5,
            cachedInputYuanPerMillion = 0.12
        ),
        "qwen3.6-flash" to TokenPricingRule(
            inputYuanPerMillion = 1.2,
            outputYuanPerMillion = 7.2,
            cacheCreationInputYuanPerMillion = 1.5,
            cachedInputYuanPerMillion = 0.12
        )
    )

    fun balanceToBubbles(balance: Int): Int {
        return balance.coerceAtLeast(0)
    }

    fun liblibPointsToBubbles(points: Int): Int {
        return ceil(points.coerceAtLeast(0) * LIBLIB_POINTS_TO_BUBBLES).toInt()
    }

    fun estimateBubbleCost(
        provider: String,
        model: String,
        promptTokens: Int,
        completionTokens: Int,
        cachedTokens: Int = 0,
        cacheCreationInputTokens: Int = 0,
        totalTokens: Int,
        pointsCost: Int
    ): BubbleEstimate? {
        val normalizedProvider = provider.trim().lowercase(Locale.ROOT)
        val safePointsCost = pointsCost.coerceAtLeast(0)
        val safeTotalTokens = totalTokens.coerceAtLeast(0)
        if (normalizedProvider == "liblib") {
            val rawPoints = maxOf(safePointsCost, safeTotalTokens)
            if (rawPoints <= 0) return null
            return BubbleEstimate(
                amount = liblibPointsToBubbles(rawPoints),
                estimated = false
            )
        }
        if (safePointsCost > 0) {
            return BubbleEstimate(amount = safePointsCost, estimated = false)
        }
        val rule = resolveTokenPricingRule(model) ?: return null
        val safePromptTokens = promptTokens.coerceAtLeast(0)
        val safeCachedTokens = cachedTokens.coerceAtLeast(0).coerceAtMost(safePromptTokens)
        val safeCacheCreationInputTokens = cacheCreationInputTokens
            .coerceAtLeast(0)
            .coerceAtMost((safePromptTokens - safeCachedTokens).coerceAtLeast(0))
        val standardInputTokens = (safePromptTokens - safeCachedTokens - safeCacheCreationInputTokens).coerceAtLeast(0)
        val inputTokens = standardInputTokens.toDouble()
        val outputTokens = completionTokens.coerceAtLeast(0).toDouble()
        val cachedInputTokens = safeCachedTokens.toDouble()
        val cacheCreationTokens = safeCacheCreationInputTokens.toDouble()
        val yuanCost = inputTokens * rule.inputYuanPerMillion / 1_000_000.0 +
            cachedInputTokens * (rule.cachedInputYuanPerMillion ?: rule.inputYuanPerMillion) / 1_000_000.0 +
            cacheCreationTokens * (rule.cacheCreationInputYuanPerMillion ?: rule.inputYuanPerMillion) / 1_000_000.0 +
            outputTokens * rule.outputYuanPerMillion / 1_000_000.0
        val bubbles = ceil(yuanCost * RMB_TO_BUBBLES * LIBLIB_POINTS_TO_BUBBLES).toInt()
        return if (bubbles > 0) {
            BubbleEstimate(amount = bubbles, estimated = true)
        } else {
            null
        }
    }

    fun estimateShortcutCachedBubbleEquivalent(
        provider: String,
        model: String,
        cachedTokens: Int,
        cacheType: String = "",
        transportMode: String = ""
    ): BubbleEstimate? {
        val normalizedProvider = provider.trim().lowercase(Locale.ROOT)
        if (normalizedProvider == "liblib") return null
        val safeCachedTokens = cachedTokens.coerceAtLeast(0)
        if (safeCachedTokens <= 0) return null
        val rule = resolveTokenPricingRule(model) ?: return null
        val normalizedCacheType = cacheType.trim().lowercase(Locale.ROOT)
        val normalizedTransportMode = transportMode.trim().lowercase(Locale.ROOT)
        val inputYuanPerMillion = rule.inputYuanPerMillion
        val configuredCachedInputYuanPerMillion = rule.cachedInputYuanPerMillion ?: -1.0
        val cachedInputYuanPerMillion = when {
            normalizedCacheType == "implicit" -> inputYuanPerMillion * IMPLICIT_CACHE_RATE
            normalizedTransportMode == "chat_explicit" ||
                normalizedCacheType == "ephemeral" -> {
                when {
                    configuredCachedInputYuanPerMillion >= 0 ->
                        configuredCachedInputYuanPerMillion
                    else -> inputYuanPerMillion * EXPLICIT_CACHE_RATE
                }
            }

            configuredCachedInputYuanPerMillion >= 0 ->
                configuredCachedInputYuanPerMillion

            else -> inputYuanPerMillion * IMPLICIT_CACHE_RATE
        }
        val yuanCost = safeCachedTokens.toDouble() * cachedInputYuanPerMillion / 1_000_000.0
        val bubbles = ceil(yuanCost * RMB_TO_BUBBLES * LIBLIB_POINTS_TO_BUBBLES).toInt()
        return if (bubbles > 0) {
            BubbleEstimate(amount = bubbles, estimated = true)
        } else {
            null
        }
    }

    fun formatBubbleLabel(estimate: BubbleEstimate?): String {
        if (estimate == null || estimate.amount <= 0) return "泡泡 -"
        return if (estimate.estimated) {
            "约 ${estimate.amount} 泡泡"
        } else {
            "${estimate.amount} 泡泡"
        }
    }

    fun formatLedgerValue(
        provider: String,
        model: String,
        promptTokens: Int,
        completionTokens: Int,
        totalTokens: Int,
        pointsCost: Int
    ): String {
        val normalizedProvider = provider.trim().lowercase(Locale.ROOT)
        val rawValue = when {
            normalizedProvider == "liblib" -> {
                val rawPoints = maxOf(pointsCost.coerceAtLeast(0), totalTokens.coerceAtLeast(0))
                if (rawPoints > 0) "$rawPoints 积分" else "-"
            }

            totalTokens > 0 -> "${totalTokens.coerceAtLeast(0)} Token"
            else -> "-"
        }
        val bubbleLabel = formatBubbleLabel(
            estimateBubbleCost(
                provider = provider,
                model = model,
                promptTokens = promptTokens,
                completionTokens = completionTokens,
                totalTokens = totalTokens,
                pointsCost = pointsCost
            )
        )
        return if (bubbleLabel == "泡泡 -") rawValue else "$rawValue / $bubbleLabel"
    }

    private fun resolveTokenPricingRule(model: String): TokenPricingRule? {
        val normalizedModel = model.trim().lowercase(Locale.ROOT)
        if (normalizedModel.isBlank()) return null
        return tokenPricingRules.entries.firstOrNull { (key, _) ->
            normalizedModel == key || normalizedModel.contains(key)
        }?.value
    }
}
