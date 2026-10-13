package com.paopaoime.aikeyboard.midscene.bridge

import android.webkit.JavascriptInterface
import com.paopaoime.aikeyboard.automation.bridge.AutomationBridgeFacade
import com.paopaoime.aikeyboard.midscene.bridge.model.MidsceneBridgeCommandResult
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.runBlocking

class MidsceneBridgeJavascriptInterface {
    private val json = Json {
        encodeDefaults = true
    }

    @JavascriptInterface
    fun captureScreenState(): String {
        return runBlocking {
            AutomationBridgeFacade.captureScreenStateJson()
        }
    }

    @JavascriptInterface
    fun executeCommand(commandJson: String): String {
        return runBlocking {
            runCatching {
                AutomationBridgeFacade.executeCommandJson(commandJson)
            }.getOrElse { throwable ->
                json.encodeToString(
                    MidsceneBridgeCommandResult(
                        success = false,
                        errorCode = "BRIDGE_EXECUTION_ERROR",
                        message = throwable.message ?: "Unknown bridge execution error",
                        retryable = false
                    )
                )
            }
        }
    }
}
