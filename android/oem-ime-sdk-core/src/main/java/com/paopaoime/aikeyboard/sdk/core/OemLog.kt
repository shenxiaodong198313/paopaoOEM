package com.paopaoime.aikeyboard.sdk.core

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log

/**
 * SDK 集成调试日志级别。
 */
enum class OemLogLevel(val priority: Int) {
    VERBOSE(Log.VERBOSE),
    DEBUG(Log.DEBUG),
    INFO(Log.INFO),
    WARN(Log.WARN),
    ERROR(Log.ERROR),
}

/**
 * 宿主日志接收器。
 *
 * 回调在日志产生线程同步执行；宿主如需更新 UI 请自行切换线程。
 * 宿主实现内部抛出的异常会被 SDK 捕获，不影响 SDK 自身逻辑。
 */
fun interface OemLogListener {
    fun onLog(level: OemLogLevel, tag: String, message: String, throwable: Throwable?)
}

/**
 * SDK 集成调试日志门面（日志桥接）。
 *
 * 总开关默认关闭：不产生任何输出，开销近似为零。
 * 开启方式（任一即可）：
 * 1. AndroidManifest meta-data `com.paopaoime.oem.enableDebugLog=true`（SDK 初始化入口自动读取）；
 * 2. [OemSdkConfig.enableDebugLog]；
 * 3. 运行时 [setEnabled]。
 *
 * 开启后日志同时发往 logcat（可用 [setLogcatEnabled] 关闭）与宿主注册的 [OemLogListener]。
 * SDK 自身不存储任何日志，无积累增长问题。
 */
object OemLog {
    private const val TAG = "OemLog"

    @Volatile
    private var enabled = false

    @Volatile
    private var logcatEnabled = true

    @Volatile
    private var minLevel: OemLogLevel = OemLogLevel.DEBUG

    @Volatile
    private var listener: OemLogListener? = null

    @Volatile
    private var manifestAttached = false

    fun setEnabled(value: Boolean) {
        enabled = value
    }

    fun isEnabled(): Boolean = enabled

    fun setLogcatEnabled(value: Boolean) {
        logcatEnabled = value
    }

    fun isLogcatEnabled(): Boolean = logcatEnabled

    fun setMinLevel(level: OemLogLevel) {
        minLevel = level
    }

    fun getMinLevel(): OemLogLevel = minLevel

    fun setListener(value: OemLogListener?) {
        listener = value
    }

    fun hasListener(): Boolean = listener != null

    /**
     * 读取宿主 AndroidManifest 中的调试日志开关，幂等；
     * 由 SDK 初始化入口自动调用，宿主无需手动调用。
     */
    fun attachContext(context: Context) {
        if (manifestAttached) return
        manifestAttached = true
        val metaData = runCatching {
            val appContext = context.applicationContext
            val pm = appContext.packageManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getApplicationInfo(
                    appContext.packageName,
                    PackageManager.ApplicationInfoFlags.of(PackageManager.GET_META_DATA.toLong()),
                ).metaData
            } else {
                @Suppress("DEPRECATION")
                pm.getApplicationInfo(appContext.packageName, PackageManager.GET_META_DATA).metaData
            }
        }.getOrNull() ?: return
        if (metaData.getBoolean(OemManifestMetaKeys.ENABLE_DEBUG_LOG, false)) {
            enabled = true
            Log.i(TAG, "debug log enabled by manifest meta-data ${OemManifestMetaKeys.ENABLE_DEBUG_LOG}")
        }
    }

    fun v(tag: String, message: String) = log(OemLogLevel.VERBOSE, tag, message, null)

    fun d(tag: String, message: String) = log(OemLogLevel.DEBUG, tag, message, null)

    fun i(tag: String, message: String) = log(OemLogLevel.INFO, tag, message, null)

    fun w(tag: String, message: String) = log(OemLogLevel.WARN, tag, message, null)

    fun w(tag: String, message: String, throwable: Throwable?) = log(OemLogLevel.WARN, tag, message, throwable)

    fun e(tag: String, message: String) = log(OemLogLevel.ERROR, tag, message, null)

    fun e(tag: String, message: String, throwable: Throwable?) = log(OemLogLevel.ERROR, tag, message, throwable)

    fun log(level: OemLogLevel, tag: String, message: String, throwable: Throwable? = null) {
        if (!enabled || level.priority < minLevel.priority) return
        if (logcatEnabled) {
            if (throwable != null) {
                Log.println(level.priority, tag, message + System.lineSeparator() + Log.getStackTraceString(throwable))
            } else {
                Log.println(level.priority, tag, message)
            }
        }
        listener?.let { target ->
            runCatching { target.onLog(level, tag, message, throwable) }
        }
    }
}
