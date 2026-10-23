package com.example.expensetracker.data.sms

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.expensetracker.data.local.ExpenseDatabase
import com.example.expensetracker.data.local.PreferencesManager
import com.example.expensetracker.data.repository.ExpenseRepository
import java.util.concurrent.TimeUnit

class SmsSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val database = ExpenseDatabase.getInstance(applicationContext)
            val repository = ExpenseRepository(applicationContext, database.expenseDao())
            val prefs = PreferencesManager(applicationContext)

            val forceFullSync = inputData.getBoolean("force_full_sync", false)
            val since = if (forceFullSync) 0L else prefs.lastSyncTimestamp
            val syncTime = System.currentTimeMillis()

            repository.syncSms(sinceTimestamp = since)
            prefs.lastSyncTimestamp = syncTime

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        fun enqueueImmediateSync(context: Context, forceFullSync: Boolean = false) {
            val inputData = workDataOf("force_full_sync" to forceFullSync)
            val workRequest = OneTimeWorkRequestBuilder<SmsSyncWorker>()
                .setInputData(inputData)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                "ImmediateSmsSync",
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
        }

        fun schedulePeriodicSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<SmsSyncWorker>(3, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "BackgroundSmsSync",
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
        }
    }
}
