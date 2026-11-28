package com.example.sharkmentor

import com.example.sharkmentor.core.dashboard.HomeDashboardEngine
import com.example.sharkmentor.core.dashboard.HomeLandingTab
import com.example.sharkmentor.data.local.entity.NetProjectionEntity
import com.example.sharkmentor.data.local.entity.NoteEntity
import com.example.sharkmentor.data.local.entity.ResourceEntity
import com.example.sharkmentor.data.local.entity.StudyTaskEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Mark76DashboardTest {

    @Test
    fun dashboardColdStartShowsInvitingDiscoveryCopyInsteadOfEmptyZeroStates() {
        val snap = HomeDashboardEngine.buildSnapshot(
            hour = 10,
            studentName = "Ali",
            isJarvisActive = false,
            todayFocusMinutes = 0,
            todayTargetMinutes = 120,
            streakDays = 0,
            streakGoalDays = 7,
            dueErrorCount = 0,
            totalErrorCount = 0,
            notes = emptyList(),
            resources = emptyList(),
            todayTasks = emptyList(),
            netProjection = null
        )

        assertTrue(snap.isColdStart)
        assertEquals("Günaydın, Ali", snap.greetingTitle)
        assertEquals(0f, snap.focusProgressFraction, 0.001f)
        assertEquals("FSRS Hata Bankası", snap.errorCardTitle)
        assertEquals("Keşfet", snap.errorCardBadge)
        assertEquals("SharkStudio PDF & Not", snap.studioCardTitle)
        assertEquals("Kement AI", snap.studioCardBadge)
        assertEquals("Kitap & Konu Koçu", snap.libraryCardTitle)
        assertEquals("AI Haftalık Plan", snap.planCardTitle)
        assertEquals("plans", snap.planCardRoute)
    }

    @Test
    fun dashboardReflectsLiveDataFromErrorBankNotesBooksAndTodayPlan() {
        val snap = HomeDashboardEngine.buildSnapshot(
            hour = 15,
            studentName = "Zeynep",
            isJarvisActive = false,
            todayFocusMinutes = 90,
            todayTargetMinutes = 180,
            streakDays = 12,
            streakGoalDays = 14,
            dueErrorCount = 5,
            totalErrorCount = 18,
            notes = listOf(
                NoteEntity(id = "n1", title = "Eski Not", content = "", updatedAt = 1000L),
                NoteEntity(id = "n2", title = "Türev Geometri Özeti", content = "", updatedAt = 5000L)
            ),
            resources = listOf(
                ResourceEntity(id = 1L, title = "Biten Kitap", subject = "Kimya", totalUnits = 50, completedUnits = 50, updatedAtMillis = 9000L),
                ResourceEntity(id = 2L, title = "3D AYT Matematik", subject = "Matematik", totalUnits = 100, completedUnits = 40, updatedAtMillis = 4000L)
            ),
            todayTasks = listOf(
                StudyTaskEntity(dayName = "Cuma", dayIndex = 4, time = "14:00", subject = "Fizik", topic = "Atışlar", goal = "40 soru", isCompleted = true),
                StudyTaskEntity(dayName = "Cuma", dayIndex = 4, time = "16:30", subject = "Matematik", topic = "Limit", goal = "50 soru", isCompleted = false)
            ),
            netProjection = null
        )

        assertFalse(snap.isColdStart)
        assertEquals(0.5f, snap.focusProgressFraction, 0.001f)
        assertEquals(50, snap.focusProgressPercent)
        assertEquals("5 bekliyor", snap.errorCardBadge)
        assertTrue(snap.errorCardHighlighted)
        assertEquals("Türev Geometri Özeti", snap.studioCardTitle)
        assertEquals("2 not", snap.studioCardBadge)
        // Prefers unfinished active book over already-finished book
        assertEquals("3D AYT Matematik", snap.libraryCardTitle)
        assertEquals("%40", snap.libraryCardBadge)
        assertNotNull(snap.libraryProgressFraction)
        assertEquals(0.4f, snap.libraryProgressFraction!!, 0.001f)
        assertEquals("Matematik • Limit", snap.planCardTitle)
        assertEquals("1/2", snap.planCardBadge)
        assertEquals("plans", snap.planCardRoute)
    }

    @Test
    fun dashboardRespectsJarvisGreetingAndNetProjectionFallbackWhenNoTasksToday() {
        val snap = HomeDashboardEngine.buildSnapshot(
            hour = 21,
            studentName = "Emre",
            isJarvisActive = true,
            todayFocusMinutes = 135,
            todayTargetMinutes = 120,
            streakDays = 21,
            streakGoalDays = 30,
            dueErrorCount = 0,
            totalErrorCount = 4,
            notes = emptyList(),
            resources = emptyList(),
            todayTasks = emptyList(),
            netProjection = NetProjectionEntity(
                tytCurrentNet = 78.5f,
                tytProjectedNet = 92.5f,
                tytTargetNet = 100f,
                tytNetGap = 7.5f,
                aytCurrentNet = 52.0f,
                aytProjectedNet = 66.0f,
                aytTargetNet = 72f,
                aytNetGap = 6.0f,
                priorityTopicsJson = "[]",
                strategicSummary = ""
            )
        )

        assertEquals("İyi akşamlar efendim", snap.greetingTitle)
        assertEquals(1f, snap.focusProgressFraction, 0.001f)
        assertEquals("Net Tahmin Sihirbazı", snap.planCardTitle)
        assertEquals("detailed_analytics", snap.planCardRoute)
        assertEquals("2s 15dk", HomeDashboardEngine.formatDurationCompact(135))
        assertEquals(HomeLandingTab.DASHBOARD, HomeLandingTab.entries.first())
    }
}
