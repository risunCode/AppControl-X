package com.appcontrolx.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.appcontrolx.service.AppFetcher

class AppSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    
    private val appFetcher = AppFetcher(context)
    
    override suspend fun doWork(): Result {
        return try {
            Log.d(TAG, "Starting app sync worker")
            
            // Pre-fetch apps to cache
            appFetcher.getUserApps()
            appFetcher.getSystemApps()
            
            Log.d(TAG, "App sync completed successfully")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "App sync failed", e)
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }
    
    companion object {
        private const val TAG = "AppSyncWorker"
        const val WORK_NAME = "app_sync_worker"
    }
}
