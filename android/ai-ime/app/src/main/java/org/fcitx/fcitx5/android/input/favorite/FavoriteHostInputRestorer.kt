package org.fcitx.fcitx5.android.input.favorite

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay

/**
 * Keeps host-editor restoration identical for material-library and AI-reply sends.
 *
 * Activity based drawers temporarily take the host app out of the foreground. A send must not
 * use the old InputConnection until the original editor has rebound to the IME.
 */
object FavoriteHostInputRestorer {
    private const val TAG = "FavoriteInputRestore"

    suspend fun restore(hostPackageName: String): Boolean {
        val normalizedHostPackageName = hostPackageName.trim()
        if (normalizedHostPackageName.isBlank()) {
            Log.w(TAG, "restore rejected because host package is blank")
            return false
        }

        val restorePolicy = FavoriteBridge.hostInputRestorePolicy(normalizedHostPackageName)
        val currentStartCount = FavoriteBridge.currentInputViewStartCount()
        if (
            restorePolicy.reuseActiveInputSession &&
            isHostInputActive(normalizedHostPackageName)
        ) {
            Log.d(
                TAG,
                "restore reused active host input package=$normalizedHostPackageName " +
                    "currentStartCount=$currentStartCount",
            )
            return true
        }

        repeat(restorePolicy.restoreAttempts.coerceIn(1, 3)) { roundIndex ->
            val beforeStartCount = FavoriteBridge.currentInputViewStartCount()
            val activated = if (restorePolicy.requiresHostInputActivation) {
                FavoriteBridge.activateHostInput(normalizedHostPackageName)
            } else {
                false
            }
            val requested = FavoriteBridge.requestShowKeyboard()
            Log.d(
                TAG,
                "restore requested package=$normalizedHostPackageName round=${roundIndex + 1} " +
                    "activated=$activated requested=$requested beforeStartCount=$beforeStartCount",
            )
            if (!requested) return false

            repeat(8) { pollIndex ->
                delay(140L)
                val currentPackageName = FavoriteBridge.currentInputTargetPackage().orEmpty()
                val currentStartCount = FavoriteBridge.currentInputViewStartCount()
                if (
                    currentPackageName.equals(normalizedHostPackageName, ignoreCase = true) &&
                    currentStartCount > beforeStartCount
                ) {
                    Log.d(
                        TAG,
                        "restore succeeded package=$normalizedHostPackageName round=${roundIndex + 1} " +
                            "poll=${pollIndex + 1} currentStartCount=$currentStartCount",
                    )
                    delay(100L)
                    return true
                }
            }
        }
        Log.w(
            TAG,
            "restore failed package=$normalizedHostPackageName " +
                "currentPackage=${FavoriteBridge.currentInputTargetPackage().orEmpty()} " +
                "currentStartCount=${FavoriteBridge.currentInputViewStartCount()}",
        )
        return false
    }

    private fun isHostInputActive(hostPackageName: String): Boolean {
        return FavoriteBridge.currentInputTargetPackage().orEmpty()
            .equals(hostPackageName, ignoreCase = true) &&
            FavoriteBridge.currentInputViewStartCount() > 0
    }
}

/** Work that must survive closing the material/AI drawer activity. */
object FavoriteHostSendWorker {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
}
