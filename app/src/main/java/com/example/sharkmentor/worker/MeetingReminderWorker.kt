package com.example.sharkmentor.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.*
import java.util.concurrent.TimeUnit

class MeetingReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val studentName = inputData.getString("studentName") ?: "Öğrenci"
        val meetingDateStr = inputData.getString("meetingDateStr") ?: ""

        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "meeting_reminders"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Randevu Hatırlatıcıları", NotificationManager.IMPORTANCE_HIGH)
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(com.example.sharkmentor.core.util.AppIconManager.getActiveIcon(applicationContext).notificationSmallIconRes)
            .setContentTitle("Koç Randevu Hatırlatması")
            .setContentText("Koçunuzla randevunuza 36 saat kaldı ($meetingDateStr). Katılabilecek misiniz?")
            .setStyle(NotificationCompat.BigTextStyle().bigText("Koçunuzla planlanan görüşmenize 36 saat kaldı. Bir aksilik veya erteleme talebiniz varsa kurum bağlantı ekranından kontrol edebilirsiniz."))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(1001, notification)
        return Result.success()
    }

    companion object {
        fun scheduleReminder(context: Context, appointmentTimestampMillis: Long, studentName: String, dateStr: String) {
            val now = System.currentTimeMillis()
            val thirtySixHoursMillis = 36 * 60 * 60 * 1000L
            val targetTime = appointmentTimestampMillis - thirtySixHoursMillis
            val delay = if (targetTime > now) targetTime - now else 0L

            val inputData = workDataOf(
                "studentName" to studentName,
                "meetingDateStr" to dateStr
            )

            val reminderRequest = OneTimeWorkRequestBuilder<MeetingReminderWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setInputData(inputData)
                .addTag("meeting_reminder")
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                "meeting_reminder_${appointmentTimestampMillis}",
                ExistingWorkPolicy.REPLACE,
                reminderRequest
            )
        }
    }
}
