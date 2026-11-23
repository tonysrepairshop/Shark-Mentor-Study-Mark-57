package com.example.sharkmentor.core.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

object SharkNotificationEngine {
    const val CHANNEL_TIMER_DESK = "shark_focus_desk"
    const val CHANNEL_SYNC_BACKGROUND = "shark_silent_sync"
    const val CHANNEL_ALERTS_COACH = "shark_alerts"
    const val CHANNEL_CHAT_MESSAGES = "shark_chat_messages"
    const val CHANNEL_LEAGUE_QUESTIONS = "shark_league_questions"
    const val CHANNEL_STUDY_NUDGE = "shark_study_invites"
    const val CHANNEL_WATER_STREAK = "shark_water_streak"
    const val CHANNEL_WEEKLY_COACHING_CHECKIN = "shark_weekly_coaching_checkin"

    fun init(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val deskChannel = NotificationChannel(
            CHANNEL_TIMER_DESK,
            "Masa Odak Sayacı",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Aktif ders ve masa odak seansı görsel zamanlayıcısı"
            setShowBadge(false)
            enableVibration(false)
            setSound(null, null)
        }

        val syncChannel = NotificationChannel(
            CHANNEL_SYNC_BACKGROUND,
            "Arka Plan Eşitleme",
            NotificationManager.IMPORTANCE_MIN
        ).apply {
            description = "Arka plan veri eşitleme ve kayıt bildirimleri"
            setShowBadge(false)
            enableVibration(false)
            setSound(null, null)
        }

        val alertsChannel = NotificationChannel(
            CHANNEL_ALERTS_COACH,
            "Koç Mesajları & Bildirimler",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Koç mesajları, mola ve seans bitiş uyarıları"
            enableVibration(true)
        }

        val chatMessagesChannel = NotificationChannel(
            CHANNEL_CHAT_MESSAGES,
            "Sohbet ve Mesaj Bildirimleri",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Grup sohbetleri ve direkt mesajlar için 7/24 anlık bildirimler"
            enableVibration(true)
        }

        val leagueQuestionsChannel = NotificationChannel(
            CHANNEL_LEAGUE_QUESTIONS,
            "Soru Paylaşım Bildirimleri",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Grubunda ve sohbetlerinde paylaşılan yeni sorular için 7/24 bildirimler"
            enableVibration(true)
        }

        val studyNudgeChannel = NotificationChannel(
            CHANNEL_STUDY_NUDGE,
            "Çalışma Dürtmeleri",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Arkadaşlarından ve grup üyelerinden gelen canlı çalışma dürtmeleri"
            enableVibration(true)
        }

        val waterStreakChannel = NotificationChannel(
            CHANNEL_WATER_STREAK,
            "Su Serisi Hatırlatıcıları 💧",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Günlük su serini canlı tutan köpekbalığı hatırlatmaları ve kutlamaları"
            enableVibration(true)
            setShowBadge(true)
        }

        val weeklyCoachingCheckInChannel = NotificationChannel(
            CHANNEL_WEEKLY_COACHING_CHECKIN,
            "Haftalık Konu & Plan Kontrolü",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Resmi haftanın son günü Pazar için konu hakimiyeti ve kitap ilerlemesi güncelleme hatırlatması"
            enableVibration(true)
            setShowBadge(true)
        }

        manager.createNotificationChannels(
            listOf(
                deskChannel,
                syncChannel,
                alertsChannel,
                chatMessagesChannel,
                leagueQuestionsChannel,
                studyNudgeChannel,
                waterStreakChannel,
                weeklyCoachingCheckInChannel
            )
        )
    }
}
