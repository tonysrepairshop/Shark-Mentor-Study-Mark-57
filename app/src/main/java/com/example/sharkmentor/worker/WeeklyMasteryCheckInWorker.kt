package com.example.sharkmentor.worker

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.sharkmentor.MainActivity
import com.example.sharkmentor.core.coaching.PlanAuditSnapshot
import com.example.sharkmentor.core.coaching.PlanProgressAuditor
import com.example.sharkmentor.core.coaching.PlanReadinessNavBus
import com.example.sharkmentor.core.coaching.StudentDossierManager
import com.example.sharkmentor.core.notification.SharkNotificationEngine
import com.example.sharkmentor.core.util.AppIconManager
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/**
 * Official Calendar Week-End (Sunday / Pazar) Check-In Notification Worker.
 *
 * Every Sunday during waking hours (10:00–21:59), prompts the student ONCE to update their
 * current Topic Mastery ("Konu Hakimiyeti") and Book Progress ("Kitap İlerlemesi") so the
 * upcoming week's plan is generated flexibly based on their exact live state — carrying over
 * unfinished tasks without repeating completed ones.
 */
class WeeklyMasteryCheckInWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val appCtx = context.applicationContext
        val now = LocalDateTime.now()
        val today = now.toLocalDate()
        val todayIsoDate = today.format(DateTimeFormatter.ISO_LOCAL_DATE)
        val dayOfWeekIso = today.dayOfWeek.value // 1=Mon .. 7=Sun (Official ISO Calendar)

        val prefs = appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastSentDate = prefs.getString(KEY_LAST_SENT_DATE, null)
        val lastCompletedDate = PlanReadinessNavBus.getLastCompletedCheckInDate(appCtx)

        if (!shouldSendSundayCheckIn(
                dayOfWeekIso = dayOfWeekIso,
                hourOfDay = now.hour,
                todayIsoDate = todayIsoDate,
                lastSentDate = lastSentDate,
                lastCompletedCheckInDate = lastCompletedDate
            )
        ) {
            return Result.success()
        }

        val audit = runCatching { PlanProgressAuditor.captureFromDatabase(appCtx, todayIsoDay = 7) }
            .getOrDefault(PlanAuditSnapshot())

        // Keep encrypted Student Dossier & hidden AI Coaching Ledger synced with Sunday's exact plan audit
        runCatching {
            StudentDossierManager.ensureLoaded(appCtx)
            if (StudentDossierManager.dossier.value.consentAccepted || StudentDossierManager.dossier.value.isCompleted) {
                StudentDossierManager.syncFromDatabase(appCtx, force = true)
            }
        }
        runCatching {
            com.example.sharkmentor.core.coaching.AiCoachLedgerManager.recordWeeklySnapshotAndRollup(
                context = appCtx,
                audit = audit,
                summaryNote = "Pazar günü otomatik haftalık değerlendirme ve aylık arşiv güncellemesi."
            )
        }

        val (title, body) = buildSundayNotificationCopy(audit)
        val delivered = showSundayCheckInNotification(
            context = appCtx,
            title = title,
            body = body
        )

        if (delivered) {
            prefs.edit().putString(KEY_LAST_SENT_DATE, todayIsoDate).apply()
            Log.i(TAG, "Delivered Sunday weekly topic mastery & plan check-in notification ($todayIsoDate).")
        }
        return Result.success()
    }

    companion object {
        private const val TAG = "WeeklyCheckInWorker"
        private const val WORK_NAME = "shark_sunday_weekly_mastery_checkin"
        private const val PREFS_NAME = "shark_sunday_checkin_notif_prefs"
        private const val KEY_LAST_SENT_DATE = "last_sent_sunday_iso_date"
        private const val NOTIFICATION_ID = 2065
        const val EXTRA_OPEN_WEEKLY_CHECKIN = "EXTRA_OPEN_WEEKLY_CHECKIN"

        /**
         * Pure decision rule (fully unit-testable):
         * Sends ONLY on official week-end Sunday (ISO day 7), between 10:00 and 21:59,
         * at most once per Sunday, and never if the student already updated today.
         */
        fun shouldSendSundayCheckIn(
            dayOfWeekIso: Int,
            hourOfDay: Int,
            todayIsoDate: String,
            lastSentDate: String?,
            lastCompletedCheckInDate: String?
        ): Boolean {
            if (dayOfWeekIso != DayOfWeek.SUNDAY.value) return false
            if (hourOfDay !in 10..21) return false
            if (lastSentDate == todayIsoDate) return false
            if (lastCompletedCheckInDate == todayIsoDate) return false
            return true
        }

        fun buildSundayNotificationCopy(audit: PlanAuditSnapshot): Pair<String, String> {
            val title = "Haftalık Plan Hazırlığı • Konu & Kitap Güncellemesi"
            val body = when {
                audit.hasAnyTasks && audit.incompleteCount > 0 ->
                    "Haftanın son günü Pazar. Bu hafta ${audit.completedCount}/${audit.totalTasks} görev tamamlandı, ${audit.incompleteCount} eksik görev yeni haftaya devrediyor. Esnek planın tam uyum sağlaması için konu hakimiyetini ve kitap ilerlemeni güncelle."
                audit.hasAnyTasks ->
                    "Haftanın son günü Pazar. Bu haftaki ${audit.totalTasks} görevin tamamını bitirdin. Yeni haftanın planı sana tam uyum sağlasın diye mevcut konu hakimiyetini ve kitaplarını güncelle."
                else ->
                    "Haftanın son günü Pazar. Yeni haftanın esnek çalışma planı sana tam uyum sağlasın diye mevcut konu hakimiyetini ve kitap ilerlemeni güncelle."
            }
            return title to body
        }

        fun schedule(context: Context) {
            val appCtx = context.applicationContext
            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(false)
                .build()

            val request = PeriodicWorkRequestBuilder<WeeklyMasteryCheckInWorker>(
                3L, TimeUnit.HOURS,
                30L, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .build()

            runCatching {
                WorkManager.getInstance(appCtx).enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    request
                )
            }.onFailure { e ->
                Log.w(TAG, "Failed to schedule Sunday weekly check-in worker: ${e.message}")
            }
        }

        private fun showSundayCheckInNotification(
            context: Context,
            title: String,
            body: String
        ): Boolean {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val granted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
                if (!granted) return false
            }

            SharkNotificationEngine.init(context)
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return false

            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_OPEN_WEEKLY_CHECKIN, true)
                putExtra("EXTRA_TARGET_ROUTE", "focus")
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                NOTIFICATION_ID,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val smallIconRes = runCatching {
                AppIconManager.getActiveIcon(context).notificationSmallIconRes
            }.getOrDefault(com.example.sharkmentor.R.drawable.ic_notification_original)

            val notification = NotificationCompat.Builder(
                context,
                SharkNotificationEngine.CHANNEL_WEEKLY_COACHING_CHECKIN
            )
                .setSmallIcon(smallIconRes)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()

            return runCatching {
                manager.notify(NOTIFICATION_ID, notification)
                true
            }.getOrDefault(false)
        }
    }
}
