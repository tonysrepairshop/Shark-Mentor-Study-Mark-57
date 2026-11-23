package com.example.sharkmentor.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.sharkmentor.core.coaching.StudentDossierManager
import java.util.concurrent.TimeUnit

/**
 * Syncs the encrypted local Student Dossier ("Öğrenci Dosyası") 2x per day (every 12 hours)
 * with current Room Goals, Topic Mastery items, and Library Books — cleaning out deleted items.
 */
class StudentDossierSyncWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return runCatching {
            val appCtx = context.applicationContext
            StudentDossierManager.ensureLoaded(appCtx)
            val current = StudentDossierManager.dossier.value
            if (current.consentAccepted || current.isCompleted) {
                StudentDossierManager.syncFromDatabase(appCtx, force = true)
            }
            Result.success()
        }.getOrElse { e ->
            Log.w(TAG, "StudentDossierSyncWorker failed: ${e.message}")
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "StudentDossierSyncWkr"
        private const val WORK_NAME = "shark_student_dossier_sync_12h"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<StudentDossierSyncWorker>(
                12L, TimeUnit.HOURS
            ).build()
            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
