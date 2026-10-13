package com.paopaoime.aikeyboard.ime.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CreativeAssetBackfillWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "CreativeAssetBackfill"
    }

    override suspend fun doWork(): Result {
        return runCatching {
            val syncedCount = withContext(Dispatchers.IO) {
                CreativeAssetCloudSync.syncLegacyLocalAssets(applicationContext)
            }
            Log.i(TAG, "backfillFinished syncedCount=$syncedCount")
            Result.success(workDataOf("synced_count" to syncedCount))
        }.getOrElse { error ->
            Log.e(TAG, "backfillFailed", error)
            Result.retry()
        }
    }
}
