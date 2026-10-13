package com.paopaoime.aikeyboard.automation.stellar

import android.content.Context
import com.paopaoime.aikeyboard.midscene.bridge.model.MidsceneBridgeCommand
import com.paopaoime.aikeyboard.midscene.bridge.model.MidsceneBridgeCommandResult
import com.paopaoime.aikeyboard.midscene.bridge.model.MidsceneBridgeRuntimeStatus
import com.paopaoime.aikeyboard.midscene.bridge.model.MidsceneBridgeScreenState
import com.paopaoime.aikeyboard.midscene.shizuku.MidsceneShizukuRuntime

class StellarAutomationRuntime(
    context: Context,
) {
    private val legacy = MidsceneShizukuRuntime(context)

    fun status(): MidsceneBridgeRuntimeStatus = legacy.status()

    suspend fun captureScreenState(): MidsceneBridgeScreenState? = legacy.captureScreenState()

    suspend fun executeCommand(command: MidsceneBridgeCommand): MidsceneBridgeCommandResult {
        return legacy.executeCommand(command)
    }
}
