package com.paopaoime.aikeyboard.ime.auth

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.util.TypedValue
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.RelativeLayout
import android.widget.TextView
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import com.mobile.auth.gatewayauth.AuthRegisterViewConfig
import com.mobile.auth.gatewayauth.AuthRegisterXmlConfig
import com.mobile.auth.gatewayauth.AuthUIConfig
import com.mobile.auth.gatewayauth.AuthUIControlClickListener
import com.mobile.auth.gatewayauth.CustomInterface
import com.mobile.auth.gatewayauth.PhoneNumberAuthHelper
import com.mobile.auth.gatewayauth.PreLoginResultListener
import com.mobile.auth.gatewayauth.ResultCode
import com.mobile.auth.gatewayauth.TokenResultListener
import com.mobile.auth.gatewayauth.model.TokenRet
import com.mobile.auth.gatewayauth.ui.AbstractPnsViewDelegate
import com.paopaoime.aikeyboard.ime.BuildConfig
import com.paopaoime.aikeyboard.ime.R
import java.lang.ref.WeakReference

object OneKeyLoginManager {
    private const val TAG = "OneKeyLogin"
    private const val CELLULAR_BIND_TIMEOUT_MS = 10000L
    private const val CELLULAR_STABILIZE_DELAY_MS = 1200L
    private const val LOGIN_TIMEOUT_MS = 10000
    private const val NETWORK_SWITCH_RETRY_DELAY_MS = 1500L
    private const val MAX_NETWORK_SWITCH_RETRY_COUNT = 1
    const val MESSAGE_SWITCH_TO_SMS = "__ONEKEY_SWITCH_TO_SMS__"
    const val MESSAGE_USER_CANCEL = "__ONEKEY_USER_CANCEL__"

    data class OneKeyTokenResult(
        val success: Boolean,
        val token: String,
        val message: String
    )

    private enum class LoginStage {
        IDLE,
        ENV_CHECK,
        GET_TOKEN
    }

    private data class CellularBinding(
        val connectivityManager: ConnectivityManager,
        val callback: ConnectivityManager.NetworkCallback,
        val timeoutRunnable: Runnable,
        var network: Network? = null
    )

    private val mainHandler = Handler(Looper.getMainLooper())
    private val warmUpListener = object : TokenResultListener {
        override fun onTokenSuccess(data: String) = Unit
        override fun onTokenFailed(data: String) = Unit
    }
    private var authHelper: PhoneNumberAuthHelper? = null
    private var pendingCallback: ((OneKeyTokenResult) -> Unit)? = null
    private var pendingActivityRef: WeakReference<Activity>? = null
    private var loginStage = LoginStage.IDLE
    private var cellularBinding: CellularBinding? = null
    private var tokenListener: TokenResultListener? = null
    private var networkSwitchRetryCount = 0
    private var pendingFlowRunnable: Runnable? = null
    private var sdkWarmedUp = false

    fun warmUp(context: Context) {
        if (sdkWarmedUp || BuildConfig.AUTH_SECRET.isBlank()) {
            return
        }
        runCatching {
            PhoneNumberAuthHelper.getInstance(context.applicationContext, warmUpListener).apply {
                setAuthSDKInfo(BuildConfig.AUTH_SECRET)
            }
            sdkWarmedUp = true
            Log.i(TAG, "warmUp setAuthSDKInfo success")
        }.onFailure {
            Log.w(TAG, "warmUp setAuthSDKInfo failed: ${it.message}")
        }
    }

    fun start(activity: Activity, callback: (OneKeyTokenResult) -> Unit) {
        if (BuildConfig.AUTH_SECRET.isBlank()) {
            callback(OneKeyTokenResult(false, "", "请先配置AUTH_SECRET后再使用一键登录"))
            return
        }
        clearPendingFlow()
        warmUp(activity.applicationContext)
        pendingCallback = callback
        pendingActivityRef = WeakReference(activity)
        loginStage = LoginStage.IDLE
        networkSwitchRetryCount = 0

        val listener = object : TokenResultListener {
            override fun onTokenSuccess(data: String) {
                Log.i(TAG, "onTokenSuccess stage=$loginStage raw=$data")
                val tokenRet = runCatching { TokenRet.fromJson(data) }.getOrNull()
                val code = tokenRet?.code.orEmpty()
                val message = tokenRet?.msg.orEmpty()
                Log.i(TAG, "onTokenSuccess stage=$loginStage code=$code msg=$message")
                when (loginStage) {
                    LoginStage.ENV_CHECK -> handleEnvCheckSuccess(code, message)
                    LoginStage.GET_TOKEN -> handleGetTokenSuccess(tokenRet)
                    LoginStage.IDLE -> Unit
                }
            }

            override fun onTokenFailed(data: String) {
                Log.e(TAG, "onTokenFailed stage=$loginStage raw=$data")
                val tokenRet = runCatching { TokenRet.fromJson(data) }.getOrNull()
                Log.e(TAG, "onTokenFailed stage=$loginStage code=${tokenRet?.code.orEmpty()} msg=${tokenRet?.msg.orEmpty()}")
                if (shouldRetryAfterNetworkSwitch(tokenRet)) {
                    scheduleRetryAfterNetworkSwitch(tokenRet)
                    return
                }
                authHelper?.hideLoginLoading()
                authHelper?.quitLoginPage()
                callbackAndClear(
                    OneKeyTokenResult(
                        false,
                        "",
                        buildFailureMessage(tokenRet)
                    )
                )
            }
        }

        tokenListener = listener
        authHelper = PhoneNumberAuthHelper.getInstance(activity.applicationContext, listener).also { helper ->
            Log.i(TAG, "setAuthSDKInfo secretLength=${BuildConfig.AUTH_SECRET.length}")
            helper.setAuthSDKInfo(BuildConfig.AUTH_SECRET)
            helper.userControlAuthPageCancel()
            helper.keepAuthPageLandscapeFullSreen(true)
            helper.keepAllPageHideNavigationBar()
            helper.expandAuthPageCheckedScope(true)
            helper.setAuthPageUseDayLight(true)
            helper.setAuthListener(listener)
            configureAuthPage(activity, helper)
        }

        tryBindCellularNetwork(activity) { bound ->
            Log.i(TAG, "cellular binding ready bound=$bound")
            val delayMs = if (bound) CELLULAR_STABILIZE_DELAY_MS else 0L
            scheduleFlow(delayMs) {
                startEnvCheck()
            }
        }
    }

    fun closeLoginPage() {
        clearPendingFlow()
        authHelper?.hideLoginLoading()
        authHelper?.quitLoginPage()
        releaseCellularBinding()
    }

    private fun startEnvCheck() {
        loginStage = LoginStage.ENV_CHECK
        authHelper?.checkEnvAvailable(PhoneNumberAuthHelper.SERVICE_TYPE_LOGIN)
    }

    private fun handleEnvCheckSuccess(code: String, message: String) {
        if (code != ResultCode.CODE_ERROR_ENV_CHECK_SUCCESS) {
            callbackAndClear(
                OneKeyTokenResult(
                    false,
                    "",
                    if (message.isNotBlank()) "$message($code)" else "当前网络环境暂不支持一键登录"
                )
            )
            return
        }
        authHelper?.accelerateLoginPage(LOGIN_TIMEOUT_MS, object : PreLoginResultListener {
            override fun onTokenSuccess(vendor: String) {
                Log.i(TAG, "accelerateLoginPage success vendor=$vendor")
            }

            override fun onTokenFailed(vendor: String, errorMsg: String) {
                Log.w(TAG, "accelerateLoginPage failed vendor=$vendor error=$errorMsg")
            }
        })
        val activity = pendingActivityRef?.get()
        if (activity == null || activity.isFinishing || activity.isDestroyed) {
            callbackAndClear(OneKeyTokenResult(false, "", "登录页面已失效，请重新尝试"))
            return
        }
        loginStage = LoginStage.GET_TOKEN
        authHelper?.getLoginToken(activity, LOGIN_TIMEOUT_MS)
    }

    private fun configureAuthPage(activity: Activity, helper: PhoneNumberAuthHelper) {
        helper.setUIClickListener(object : AuthUIControlClickListener {
            override fun onClick(code: String, context: Context?, jsonString: String?) {
                when (code) {
                    ResultCode.CODE_ERROR_USER_CONTROL_CANCEL_BYBTN,
                    ResultCode.CODE_ERROR_USER_CONTROL_CANCEL_BYKEY,
                    ResultCode.CODE_ERROR_USER_CANCEL -> {
                        helper.quitLoginPage()
                        callbackAndClear(OneKeyTokenResult(false, "", MESSAGE_USER_CANCEL))
                    }
                }
            }
        })
        helper.removeAuthRegisterXmlConfig()
        helper.removeAuthRegisterViewConfig()
        helper.removePrivacyAuthRegisterViewConfig()
        helper.removePrivacyRegisterXmlConfig()
        helper.addAuthRegistViewConfig(
            "switch_number",
            AuthRegisterViewConfig.Builder()
                .setRootViewId(AuthRegisterViewConfig.RootViewId.ROOT_VIEW_ID_NUMBER)
                .setView(createSwitchNumberView(activity))
                .setCustomInterface(object : CustomInterface {
                    override fun onClick(context: Context?) {
                        helper.quitLoginPage()
                        callbackAndClear(OneKeyTokenResult(false, "", MESSAGE_SWITCH_TO_SMS))
                    }
                })
                .build()
        )
        helper.addAuthRegisterXmlConfig(
            AuthRegisterXmlConfig.Builder()
                .setLayout(R.layout.layout_auth_onekey_page, object : AbstractPnsViewDelegate() {
                    override fun onViewCreated(view: View) {
                        view.findViewById<View>(R.id.btn_back)?.setOnClickListener {
                            helper.quitLoginPage()
                            callbackAndClear(OneKeyTokenResult(false, "", MESSAGE_USER_CANCEL))
                        }
                        view.findViewById<View>(R.id.iv_login_robot)?.let { robot ->
                            val floatDistance = 10f * activity.resources.displayMetrics.density
                            ObjectAnimator.ofFloat(robot, View.TRANSLATION_Y, 0f, -floatDistance, 0f).apply {
                                duration = 3200L
                                repeatCount = ValueAnimator.INFINITE
                                repeatMode = ValueAnimator.RESTART
                                interpolator = AccelerateDecelerateInterpolator()
                                start()
                            }
                        }
                    }
                })
                .build()
        )
        val density = activity.resources.displayMetrics.density
        val screenWidthDp = (activity.resources.displayMetrics.widthPixels / density).toInt()
        val authPageOrientation =
            if (Build.VERSION.SDK_INT == 26) {
                ActivityInfo.SCREEN_ORIENTATION_BEHIND
            } else {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            }
        helper.setAuthUIConfig(
            AuthUIConfig.Builder()
                .setNavHidden(true)
                .setNavReturnHidden(true)
                .setLogoHidden(true)
                .setSwitchAccHidden(true)
                .setLightColor(true)
                .setStatusBarColor(Color.TRANSPARENT)
                .setWebViewStatusBarColor(Color.TRANSPARENT)
                .setStatusBarUIFlag(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
                .setPageBackgroundPath("bg_skin_channel")
                .setLogBtnText("一键登录")
                .setLogBtnBackgroundPath("bg_login_primary_button")
                .setLogBtnToastHidden(true)
                .setLogBtnWidth(screenWidthDp - 48)
                .setLogBtnHeight(56)
                .setLogBtnOffsetY(378)
                .setLogBtnTextSizeDp(16)
                .setLogBtnTextColor(Color.WHITE)
                .setNumberColor(Color.parseColor("#111111"))
                .setNumberSizeDp(20)
                .setNumFieldOffsetY(304)
                .setNumberLayoutGravity(Gravity.CENTER_HORIZONTAL)
                .setLogBtnLayoutGravity(Gravity.CENTER_HORIZONTAL)
                .setSloganOffsetY(252)
                .setSloganTextSizeDp(12)
                .setPrivacyState(false)
                .setUncheckedImgPath("bg_login_agreement_unchecked")
                .setCheckedImgPath("bg_login_agreement_checked")
                .setPrivacyTextSizeDp(12)
                .setAppPrivacyColor(Color.parseColor("#9AA2B5"), Color.parseColor("#3264ED"))
                .setProtocolGravity(Gravity.CENTER_VERTICAL)
                .setPrivacyMargin(12)
                .setPrivacyOffsetY_B(28)
                .setPrivacyAlertIsNeedShow(true)
                .setPrivacyAlertIsNeedAutoLogin(true)
                .setPrivacyAlertMaskIsNeedShow(true)
                .setTapPrivacyAlertMaskCloseAlert(false)
                .setPrivacyAlertAlignment(Gravity.CENTER)
                .setPrivacyAlertWidth(screenWidthDp - 40)
                .setPrivacyAlertHeight(220)
                .setPrivacyAlertCornerRadiusArray(intArrayOf(20, 20, 20, 20))
                .setPrivacyAlertBackgroundColor(Color.WHITE)
                .setPrivacyAlertTitleContent("服务协议和隐私政策")
                .setPrivacyAlertTitleTextSize(16)
                .setPrivacyAlertTitleColor(Color.parseColor("#111111"))
                .setPrivacyAlertBefore("为了更好地保障你的合法权益，进入下一步前，请阅读并同意")
                .setPrivacyAlertBtnContent("同意并继续")
                .setPrivacyAlertContentTextSize(14)
                .setPrivacyAlertContentBaseColor(Color.parseColor("#8A90A0"))
                .setPrivacyAlertOneColor(Color.parseColor("#3264ED"))
                .setPrivacyAlertTwoColor(Color.parseColor("#3264ED"))
                .setPrivacyAlertThreeColor(Color.parseColor("#3264ED"))
                .setPrivacyAlertOperatorColor(Color.parseColor("#3264ED"))
                .setPrivacyAlertContentHorizontalMargin(24)
                .setPrivacyAlertContentVerticalMargin(22)
                .setPrivacyAlertBtnTextColor(Color.WHITE)
                .setPrivacyAlertBtnTextColorPath("privacy_alert_btn_color")
                .setPrivacyAlertBtnBgStateListDrawable(createPrimaryButtonStateListDrawable(activity))
                .setPrivacyAlertBtnHorizontalMargin(24)
                .setPrivacyAlertBtnVerticalMargin(0)
                .setPrivacyAlertEntryAnimation("auth_page_noop")
                .setPrivacyAlertExitAnimation("auth_page_noop")
                .setAuthPageActIn("auth_page_noop", "auth_page_noop")
                .setAuthPageActOut("auth_page_noop", "auth_page_noop")
                .setScreenOrientation(authPageOrientation)
                .create()
        )
    }

    private fun createSwitchNumberView(activity: Activity): View {
        val textView = TextView(activity)
        textView.text = "换号"
        textView.setTextColor(Color.parseColor("#5C6373"))
        textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        textView.gravity = Gravity.CENTER
        val paddingHorizontal = (12 * activity.resources.displayMetrics.density).toInt()
        val paddingVertical = (6 * activity.resources.displayMetrics.density).toInt()
        textView.setPadding(paddingHorizontal, paddingVertical, paddingHorizontal, paddingVertical)
        val background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 12 * activity.resources.displayMetrics.density
            setColor(Color.WHITE)
            setStroke((activity.resources.displayMetrics.density).toInt(), Color.parseColor("#D5DDF3"))
        }
        textView.background = background
        textView.layoutParams = RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.WRAP_CONTENT,
            RelativeLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            addRule(RelativeLayout.ALIGN_PARENT_END, RelativeLayout.TRUE)
            addRule(RelativeLayout.CENTER_VERTICAL, RelativeLayout.TRUE)
            marginEnd = (64 * activity.resources.displayMetrics.density).toInt()
        }
        return textView
    }

    private fun createPrimaryButtonStateListDrawable(activity: Activity): StateListDrawable {
        val density = activity.resources.displayMetrics.density
        fun buttonDrawable(color: String): GradientDrawable {
            return GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 16 * density
                setColor(Color.parseColor(color))
            }
        }
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), buttonDrawable("#2958E0"))
            addState(intArrayOf(), buttonDrawable("#3264ED"))
        }
    }

    private fun handleGetTokenSuccess(tokenRet: TokenRet?) {
        val code = tokenRet?.code.orEmpty()
        if (code == ResultCode.CODE_START_AUTHPAGE_SUCCESS) {
            return
        }
        if (code == ResultCode.CODE_SUCCESS && !tokenRet?.token.isNullOrBlank()) {
            Log.i(TAG, "token acquired length=${tokenRet.token.length}")
            callbackAndClear(OneKeyTokenResult(true, tokenRet.token.orEmpty(), "获取一键登录令牌成功"))
            return
        }
        callbackAndClear(
            OneKeyTokenResult(
                false,
                "",
                buildFailureMessage(tokenRet)
            )
        )
        authHelper?.quitLoginPage()
    }

    private fun shouldRetryAfterNetworkSwitch(tokenRet: TokenRet?): Boolean {
        if (networkSwitchRetryCount >= MAX_NETWORK_SWITCH_RETRY_COUNT) {
            return false
        }
        val code = tokenRet?.code.orEmpty()
        val message = tokenRet?.msg.orEmpty()
        return code == "600012" ||
            code == "600015" ||
            message.contains("私网IP无效") ||
            message.contains("网络异常超时")
    }

    private fun scheduleRetryAfterNetworkSwitch(tokenRet: TokenRet?) {
        networkSwitchRetryCount += 1
        val code = tokenRet?.code.orEmpty()
        val message = tokenRet?.msg.orEmpty()
        Log.w(
            TAG,
            "schedule retry after network switch unstable, retryCount=$networkSwitchRetryCount code=$code msg=$message"
        )
        authHelper?.hideLoginLoading()
        authHelper?.quitLoginPage()
        scheduleFlow(NETWORK_SWITCH_RETRY_DELAY_MS) {
            startEnvCheck()
        }
    }

    private fun tryBindCellularNetwork(activity: Activity, onReady: (Boolean) -> Unit) {
        releaseCellularBinding()
        val telephonyManager = activity.getSystemService(Context.TELEPHONY_SERVICE) as? android.telephony.TelephonyManager
        if (telephonyManager == null || telephonyManager.simState != android.telephony.TelephonyManager.SIM_STATE_READY) {
            Log.w(TAG, "SIM card not ready, skip cellular binding")
            onReady(false)
            return
        }
        val connectivityManager = activity.getSystemService(ConnectivityManager::class.java)
        if (connectivityManager == null) {
            onReady(false)
            return
        }
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
            .build()
        var finished = false
        lateinit var timeoutRunnable: Runnable
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                val bindSuccess = runCatching {
                    connectivityManager.bindProcessToNetwork(network)
                }.getOrDefault(false)
                Log.i(TAG, "cellular network available bindSuccess=$bindSuccess")
                cellularBinding?.network = network
                
                if (finished) {
                    // 即使超时了，也尽量绑定进程，以辅助后续的阿里云 SDK 网络请求
                    return
                }
                finished = true
                mainHandler.removeCallbacks(timeoutRunnable)
                if (!bindSuccess) {
                    releaseCellularBinding()
                }
                onReady(bindSuccess)
            }

            override fun onUnavailable() {
                if (finished) return
                finished = true
                mainHandler.removeCallbacks(timeoutRunnable)
                releaseCellularBinding()
                onReady(false)
            }

            override fun onLost(network: Network) {
                Log.w(TAG, "cellular network lost")
                if (cellularBinding?.network == network) {
                    cellularBinding?.network = null
                    runCatching { connectivityManager.bindProcessToNetwork(null) }
                }
            }
        }
        timeoutRunnable = Runnable {
            if (finished) return@Runnable
            finished = true
            Log.w(TAG, "bind cellular network timeout, let SDK handle it natively")
            // 超时时不释放 cellularBinding，因为唤醒蜂窝网络可能需要更长时间。
            // 保持 requestNetwork 存活，可以让系统继续建立蜂窝连接，阿里云 SDK 内部切换时会更顺畅。
            onReady(false)
        }
        cellularBinding = CellularBinding(
            connectivityManager = connectivityManager,
            callback = callback,
            timeoutRunnable = timeoutRunnable
        )
        val requestSuccess = runCatching {
            connectivityManager.requestNetwork(request, callback)
            true
        }.getOrDefault(false)
        if (!requestSuccess) {
            releaseCellularBinding()
            onReady(false)
            return
        }
        mainHandler.postDelayed(timeoutRunnable, CELLULAR_BIND_TIMEOUT_MS)
    }

    private fun releaseCellularBinding() {
        val binding = cellularBinding ?: return
        mainHandler.removeCallbacks(binding.timeoutRunnable)
        runCatching {
            binding.connectivityManager.bindProcessToNetwork(null)
        }
        runCatching {
            binding.connectivityManager.unregisterNetworkCallback(binding.callback)
        }
        cellularBinding = null
    }

    private fun scheduleFlow(delayMs: Long, action: () -> Unit) {
        clearPendingFlow()
        val runnable = Runnable {
            pendingFlowRunnable = null
            if (pendingCallback != null) {
                action()
            }
        }
        pendingFlowRunnable = runnable
        if (delayMs <= 0L) {
            mainHandler.post(runnable)
        } else {
            mainHandler.postDelayed(runnable, delayMs)
        }
    }

    private fun clearPendingFlow() {
        pendingFlowRunnable?.let(mainHandler::removeCallbacks)
        pendingFlowRunnable = null
    }

    private fun buildFailureMessage(tokenRet: TokenRet?): String {
        return if (!tokenRet?.code.isNullOrBlank()) {
            "${tokenRet.msg ?: "一键登录失败"}(${tokenRet.code})"
        } else {
            tokenRet?.msg ?: "一键登录失败"
        }
    }

    private fun callbackAndClear(result: OneKeyTokenResult) {
        clearPendingFlow()
        val callback = pendingCallback
        pendingCallback = null
        pendingActivityRef = null
        loginStage = LoginStage.IDLE
        networkSwitchRetryCount = 0
        releaseCellularBinding()
        callback?.invoke(result)
    }
}
