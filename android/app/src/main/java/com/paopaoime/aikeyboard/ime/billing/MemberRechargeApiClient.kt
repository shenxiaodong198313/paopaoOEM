package com.paopaoime.aikeyboard.ime.billing

import com.paopaoime.aikeyboard.ime.auth.AuthApiClient
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.UUID
import java.util.concurrent.TimeUnit

object MemberRechargeApiClient {
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    data class RechargePlan(
        val planCode: String,
        val amountFen: Int,
        val amountYuanText: String,
        val bubbles: Int
    )

    data class RechargeConfigResult(
        val success: Boolean,
        val message: String,
        val plans: List<RechargePlan>,
        val defaultAmountFen: Int,
        val newAccessToken: String?,
        val newRefreshToken: String?,
        val authInvalid: Boolean = false
    )

    data class CreateOrderResult(
        val success: Boolean,
        val message: String,
        val outTradeNo: String,
        val payUrl: String,
        val newAccessToken: String?,
        val newRefreshToken: String?,
        val authInvalid: Boolean = false
    )

    data class QueryOrderResult(
        val success: Boolean,
        val message: String,
        val tradeState: String,
        val newAccessToken: String?,
        val newRefreshToken: String?,
        val authInvalid: Boolean = false
    )

    data class OrderHistoryItem(
        val orderId: String,
        val outTradeNo: String,
        val recordType: String,
        val orderChannelLabel: String,
        val payMode: String,
        val amountYuan: String,
        val bubbleAmount: Int,
        val status: String,
        val tradeState: String,
        val description: String,
        val paidAt: String,
        val createdAt: String
    )

    data class OrderHistoryResult(
        val success: Boolean,
        val message: String,
        val items: List<OrderHistoryItem>,
        val newAccessToken: String?,
        val newRefreshToken: String?,
        val authInvalid: Boolean = false
    )

    private data class Envelope(
        val success: Boolean,
        val data: JSONObject,
        val message: String
    )

    private data class AuthorizedResponse(
        val statusCode: Int,
        val envelope: Envelope,
        val newAccessToken: String?,
        val newRefreshToken: String?
    )

    fun getRechargeConfig(accessToken: String, refreshToken: String): RechargeConfigResult {
        return try {
            val response = doAuthorizedRequest(
                method = "GET",
                path = "/api/v1/pay/alipay/member/config",
                accessToken = accessToken,
                refreshToken = refreshToken,
                body = null
            )
            if (response.statusCode in 200..299 && response.envelope.success) {
                val options = response.envelope.data.optJSONArray("amountOptions")
                val plans = buildList {
                    if (options != null) {
                        for (index in 0 until options.length()) {
                            val item = options.optJSONObject(index) ?: continue
                            val amountFen = item.optInt("amountFen", 0)
                            if (amountFen <= 0) continue
                            add(
                                RechargePlan(
                                    planCode = item.optString("plan"),
                                    amountFen = amountFen,
                                    amountYuanText = formatYuan(amountFen),
                                    bubbles = amountFen
                                )
                            )
                        }
                    }
                }
                return RechargeConfigResult(
                    success = plans.isNotEmpty(),
                    message = if (plans.isNotEmpty()) "获取成功" else "当前无可用充值档位",
                    plans = plans,
                    defaultAmountFen = response.envelope.data.optInt("defaultAmountFen", plans.firstOrNull()?.amountFen ?: 0),
                    newAccessToken = response.newAccessToken,
                    newRefreshToken = response.newRefreshToken,
                    authInvalid = false
                )
            }
            RechargeConfigResult(
                success = false,
                message = response.envelope.message.ifBlank { "获取充值档位失败" },
                plans = emptyList(),
                defaultAmountFen = 0,
                newAccessToken = response.newAccessToken,
                newRefreshToken = response.newRefreshToken,
                authInvalid = response.statusCode == 401
            )
        } catch (_: SocketTimeoutException) {
            RechargeConfigResult(false, "连接服务超时，请检查服务端是否启动", emptyList(), 0, null, null, false)
        } catch (_: Exception) {
            RechargeConfigResult(false, "网络异常，请检查手机与服务端网络连通性", emptyList(), 0, null, null, false)
        }
    }

    fun createAlipayOrder(
        accessToken: String,
        refreshToken: String,
        amountFen: Int
    ): CreateOrderResult {
        return try {
            val response = doAuthorizedRequest(
                method = "POST",
                path = "/api/v1/pay/alipay/wap/order",
                accessToken = accessToken,
                refreshToken = refreshToken,
                body = JSONObject()
                    .put("amountFen", amountFen)
                    .put("returnUrl", "${AuthApiClient.getResolvedBaseUrl()}/member/pay?channel=alipay&from=android")
                    .put("idempotencyKey", "android_member_${UUID.randomUUID()}")
            )
            if (response.statusCode in 200..299 && response.envelope.success) {
                return CreateOrderResult(
                    success = true,
                    message = "创建订单成功",
                    outTradeNo = response.envelope.data.optString("outTradeNo"),
                    payUrl = response.envelope.data.optString("payUrl"),
                    newAccessToken = response.newAccessToken,
                    newRefreshToken = response.newRefreshToken,
                    authInvalid = false
                )
            }
            CreateOrderResult(
                success = false,
                message = response.envelope.message.ifBlank { "创建订单失败" },
                outTradeNo = "",
                payUrl = "",
                newAccessToken = response.newAccessToken,
                newRefreshToken = response.newRefreshToken,
                authInvalid = response.statusCode == 401
            )
        } catch (_: SocketTimeoutException) {
            CreateOrderResult(false, "连接服务超时，请检查服务端是否启动", "", "", null, null, false)
        } catch (_: Exception) {
            CreateOrderResult(false, "网络异常，请检查手机与服务端网络连通性", "", "", null, null, false)
        }
    }

    fun listPayOrders(
        accessToken: String,
        refreshToken: String,
        page: Int = 1,
        pageSize: Int = 50
    ): OrderHistoryResult {
        return try {
            val response = doAuthorizedRequest(
                method = "GET",
                path = "/api/v1/pay/orders?page=$page&pageSize=$pageSize",
                accessToken = accessToken,
                refreshToken = refreshToken,
                body = null
            )
            if (response.statusCode in 200..299 && response.envelope.success) {
                val items = response.envelope.data.optJSONArray("items")
                val orders = buildList {
                    if (items != null) {
                        for (index in 0 until items.length()) {
                            val item = items.optJSONObject(index) ?: continue
                            add(
                                OrderHistoryItem(
                                    orderId = item.optString("orderId"),
                                    outTradeNo = item.optString("outTradeNo"),
                                    recordType = item.optString("recordType"),
                                    orderChannelLabel = item.optString("orderChannelLabel"),
                                    payMode = item.optString("payMode"),
                                    amountYuan = item.optString("amountYuan"),
                                    bubbleAmount = item.optInt("bubbleAmount", item.optInt("creditedPower", 0)),
                                    status = item.optString("status"),
                                    tradeState = item.optString("tradeState"),
                                    description = item.optString("description"),
                                    paidAt = item.optString("paidAt"),
                                    createdAt = item.optString("createdAt")
                                )
                            )
                        }
                    }
                }
                return OrderHistoryResult(
                    success = true,
                    message = "获取成功",
                    items = orders,
                    newAccessToken = response.newAccessToken,
                    newRefreshToken = response.newRefreshToken,
                    authInvalid = false
                )
            }
            OrderHistoryResult(
                success = false,
                message = response.envelope.message.ifBlank { "获取订单记录失败" },
                items = emptyList(),
                newAccessToken = response.newAccessToken,
                newRefreshToken = response.newRefreshToken,
                authInvalid = response.statusCode == 401
            )
        } catch (_: SocketTimeoutException) {
            OrderHistoryResult(false, "连接服务超时，请检查服务端是否启动", emptyList(), null, null, false)
        } catch (_: Exception) {
            OrderHistoryResult(false, "网络异常，请检查手机与服务端网络连通性", emptyList(), null, null, false)
        }
    }

    fun queryAlipayOrder(
        accessToken: String,
        refreshToken: String,
        outTradeNo: String
    ): QueryOrderResult {
        return try {
            val response = doAuthorizedRequest(
                method = "GET",
                path = "/api/v1/pay/alipay/orders/$outTradeNo",
                accessToken = accessToken,
                refreshToken = refreshToken,
                body = null
            )
            if (response.statusCode in 200..299 && response.envelope.success) {
                return QueryOrderResult(
                    success = true,
                    message = "查询成功",
                    tradeState = response.envelope.data.optString("tradeState"),
                    newAccessToken = response.newAccessToken,
                    newRefreshToken = response.newRefreshToken,
                    authInvalid = false
                )
            }
            QueryOrderResult(
                success = false,
                message = response.envelope.message.ifBlank { "查询订单失败" },
                tradeState = "",
                newAccessToken = response.newAccessToken,
                newRefreshToken = response.newRefreshToken,
                authInvalid = response.statusCode == 401
            )
        } catch (_: SocketTimeoutException) {
            QueryOrderResult(false, "连接服务超时，请检查服务端是否启动", "", null, null, false)
        } catch (_: Exception) {
            QueryOrderResult(false, "网络异常，请检查手机与服务端网络连通性", "", null, null, false)
        }
    }

    private fun doAuthorizedRequest(
        method: String,
        path: String,
        accessToken: String,
        refreshToken: String,
        body: JSONObject?
    ): AuthorizedResponse {
        val first = requestJson(method, path, accessToken, body)
        if (first.first != 401) {
            return AuthorizedResponse(first.first, parseEnvelope(first.second, "请求失败"), null, null)
        }
        if (refreshToken.isBlank()) {
            return AuthorizedResponse(first.first, Envelope(false, JSONObject(), "登录态已失效"), null, null)
        }
        val refreshResult = AuthApiClient.refreshAccessToken(refreshToken)
        if (!refreshResult.success || refreshResult.accessToken.isBlank()) {
            return AuthorizedResponse(first.first, Envelope(false, JSONObject(), "登录态已失效"), null, null)
        }
        val second = requestJson(method, path, refreshResult.accessToken, body)
        return AuthorizedResponse(
            second.first,
            parseEnvelope(second.second, "请求失败"),
            refreshResult.accessToken,
            refreshResult.refreshToken
        )
    }

    private fun requestJson(
        method: String,
        path: String,
        accessToken: String,
        body: JSONObject?
    ): Pair<Int, JSONObject> {
        val requestBuilder = Request.Builder()
            .url("${AuthApiClient.getResolvedBaseUrl()}$path")
            .addHeader("Authorization", "Bearer $accessToken")
        when (method.uppercase()) {
            "GET" -> requestBuilder.get()
            else -> requestBuilder.post((body ?: JSONObject()).toString().toRequestBody(jsonMediaType))
        }
        client.newCall(requestBuilder.build()).execute().use { response ->
            val bodyString = response.body?.string().orEmpty()
            val json = if (bodyString.isNotBlank()) JSONObject(bodyString) else JSONObject()
            return response.code to json
        }
    }

    private fun parseEnvelope(json: JSONObject, fallbackMessage: String): Envelope {
        if (json.has("success")) {
            return Envelope(
                success = json.optBoolean("success", false),
                data = json.optJSONObject("data") ?: JSONObject(),
                message = parseMessage(json, fallbackMessage)
            )
        }
        return Envelope(
            success = json.optBoolean("ok", false),
            data = json,
            message = parseMessage(json, fallbackMessage)
        )
    }

    private fun parseMessage(json: JSONObject, fallbackMessage: String): String {
        val errorMessage = json.optJSONObject("error")?.optString("message").orEmpty()
        if (errorMessage.isNotBlank()) return errorMessage
        val message = json.optString("message").orEmpty()
        if (message.isNotBlank()) return message
        return fallbackMessage
    }

    private fun formatYuan(amountFen: Int): String {
        return String.format("%.2f", amountFen / 100.0)
    }
}
