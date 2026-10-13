package com.paopaoime.aikeyboard.materialhub

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.paopaoime.aikeyboard.oem.OemEnterpriseConfig
import com.paopaoime.aikeyboard.sdk.core.OemLog
import java.util.concurrent.TimeUnit

object OemRemoteMaterialSyncScheduler {

    fun ensureScheduled(context: Context) {
        if (!OemEnterpriseConfig.isMaterialLibraryEnabled(context.applicationContext)) {
            return
        }
        val request = PeriodicWorkRequestBuilder<OemRemoteMaterialCatalogWorker>(1, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    private const val UNIQUE_WORK_NAME = "oem_remote_material_catalog_hourly_check"
}

class OemRemoteMaterialCatalogWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return runCatching {
            OemRemoteMaterialAutoSync.syncNow(applicationContext)
        }.fold(
            onSuccess = { Result.success() },
            onFailure = { error ->
                OemLog.e(TAG, "OemRemoteMaterialCatalogWorker failed", error)
                Result.retry()
            },
        )
    }

    private companion object {
        private const val TAG = "OemRemoteMaterial"
    }
}
