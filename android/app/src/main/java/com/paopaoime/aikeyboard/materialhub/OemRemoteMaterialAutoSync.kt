package com.paopaoime.aikeyboard.materialhub

import android.content.Context
import com.paopaoime.aikeyboard.oem.OemEnterpriseConfig
import com.paopaoime.aikeyboard.sdk.core.OemLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object OemRemoteMaterialAutoSync {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var syncInProgress = false

    @Volatile
    private var lastSyncAt = 0L

    @Volatile
    private var currentJob: Job? = null

    fun isSyncInProgress(): Boolean = syncInProgress

    fun stop() {
        currentJob?.cancel()
        currentJob = null
        syncInProgress = false
    }

    fun warmUp(
        context: Context,
        reason: String,
        force: Boolean = false,
    ) {
        val appContext = context.applicationContext
        if (!OemEnterpriseConfig.isMaterialLibraryEnabled(appContext)) {
            return
        }
        val now = System.currentTimeMillis()
        if (!force && (syncInProgress || now - lastSyncAt < MIN_SYNC_INTERVAL_MS)) {
            return
        }
        syncInProgress = true
        lastSyncAt = now
        currentJob = scope.launch {
            runCatching {
                OemRemoteMaterialService(appContext).syncRemoteFavorites()
            }.onFailure { error ->
                OemLog.e(TAG, "warmUp failed, reason=$reason", error)
            }
            syncInProgress = false
            lastSyncAt = System.currentTimeMillis()
            currentJob = null
        }
    }

    suspend fun syncNow(context: Context) {
        if (!OemEnterpriseConfig.isMaterialLibraryEnabled(context.applicationContext)) {
            return
        }
        syncInProgress = true
        lastSyncAt = System.currentTimeMillis()
        try {
            OemRemoteMaterialService(context.applicationContext).syncRemoteFavorites()
        } finally {
            syncInProgress = false
            lastSyncAt = System.currentTimeMillis()
        }
    }

    private const val TAG = "OemRemoteMaterial"
    private const val MIN_SYNC_INTERVAL_MS = 15_000L
}
