package com.example.sharkmentor

import com.example.sharkmentor.data.repository.ExamCountdownState
import com.example.sharkmentor.data.repository.StreakState
import com.example.sharkmentor.data.repository.resolveCountdownMerge
import com.example.sharkmentor.data.repository.resolveStreakMerge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class Mark61CloudSyncTest {

    // ── Water Streak ("Su Serisi") Multi-Device Sync Tests ──────────────────

    @Test
    fun waterStreak_newDeviceRestoresRemoteStateAndCompletedDates() {
        val today = LocalDate.of(2026, 11, 22)
        val local = StreakState() // brand-new device / empty prefs
        val remote = StreakState(
            streakCount = 14,
            streakFreezeAvailable = 2,
            lastStreakDate = "2026-11-22",
            completedDates = setOf("2026-11-22", "2026-11-21", "2026-11-20"),
            updatedAtMillis = 1_795_300_000_000L
        )

        val decision = resolveStreakMerge(local, remote, today = today)
        assertTrue(decision.shouldUpdateLocal)
        assertFalse(decision.shouldPushToCloud)
        assertEquals(14, decision.merged.streakCount)
        assertEquals(2, decision.merged.streakFreezeAvailable)
        assertEquals("2026-11-22", decision.merged.lastStreakDate)
        assertEquals(0, decision.merged.daysSinceLastStudy)
        assertTrue(decision.merged.completedDates.contains("2026-11-21"))
    }

    @Test
    fun waterStreak_existingDeviceUploadsLocalStreakToEmptyCloud() {
        val today = LocalDate.of(2026, 11, 22)
        val local = StreakState(
            streakCount = 9,
            streakFreezeAvailable = 1,
            lastStreakDate = "2026-11-22",
            completedDates = setOf("2026-11-22", "2026-11-21"),
            updatedAtMillis = 1_795_300_000_000L
        )
        val remote = StreakState()

        val decision = resolveStreakMerge(local, remote, today = today)
        assertTrue(decision.shouldPushToCloud)
        assertEquals(9, decision.merged.streakCount)
        assertEquals("2026-11-22", decision.merged.lastStreakDate)
    }

    @Test
    fun waterStreak_crossDeviceNewerStudyDayWinsAndUnionsHistory() {
        val today = LocalDate.of(2026, 11, 22)
        val local = StreakState(
            streakCount = 7,
            streakFreezeAvailable = 1,
            lastStreakDate = "2026-11-21",
            completedDates = setOf("2026-11-21", "2026-11-20"),
            updatedAtMillis = 1_795_200_000_000L
        )
        val remote = StreakState(
            streakCount = 8,
            streakFreezeAvailable = 1,
            lastStreakDate = "2026-11-22",
            completedDates = setOf("2026-11-22", "2026-11-21"),
            updatedAtMillis = 1_795_300_000_000L
        )

        val decision = resolveStreakMerge(local, remote, today = today)
        assertTrue(decision.shouldUpdateLocal)
        assertEquals(8, decision.merged.streakCount)
        assertEquals("2026-11-22", decision.merged.lastStreakDate)
        assertEquals(setOf("2026-11-22", "2026-11-21", "2026-11-20"), decision.merged.completedDates)
    }

    @Test
    fun waterStreak_sameDayNeverRegressesCountAndUsesNewerFreeze() {
        val today = LocalDate.of(2026, 11, 22)
        val local = StreakState(
            streakCount = 10,
            streakFreezeAvailable = 2,
            lastStreakDate = "2026-11-22",
            completedDates = setOf("2026-11-22"),
            updatedAtMillis = 1_795_250_000_000L
        )
        val remote = StreakState(
            streakCount = 12,
            streakFreezeAvailable = 1,
            lastStreakDate = "2026-11-22",
            completedDates = setOf("2026-11-22"),
            updatedAtMillis = 1_795_310_000_000L
        )

        val decision = resolveStreakMerge(local, remote, today = today)
        assertTrue(decision.shouldUpdateLocal)
        assertEquals(12, decision.merged.streakCount)
        assertEquals(1, decision.merged.streakFreezeAvailable)
    }

    // ── Exam Countdown ("Geri Sayım Aracı") Multi-Device Sync Tests ─────────

    @Test
    fun countdown_newDeviceRestoresRemoteExamTitleAndDate() {
        val local = ExamCountdownState()
        val remote = ExamCountdownState(
            title = "YKS 2027",
            targetDateMillis = 1_813_536_000_000L,
            updatedAtMillis = 1_795_300_000_000L
        )

        val decision = resolveCountdownMerge(local, remote)
        assertTrue(decision.shouldUpdateLocal)
        assertFalse(decision.shouldPushToCloud)
        assertEquals("YKS 2027", decision.merged.title)
        assertEquals(1_813_536_000_000L, decision.merged.targetDateMillis)
        assertFalse(decision.merged.isPendingSync)
    }

    @Test
    fun countdown_newerRemoteEditOverwritesOlderLocalCountdown() {
        val local = ExamCountdownState(
            title = "YKS 2026",
            targetDateMillis = 1_781_990_000_000L,
            updatedAtMillis = 1_795_100_000_000L
        )
        val remote = ExamCountdownState(
            title = "TUS 2027",
            targetDateMillis = 1_813_536_000_000L,
            updatedAtMillis = 1_795_300_000_000L
        )

        val decision = resolveCountdownMerge(local, remote)
        assertTrue(decision.shouldUpdateLocal)
        assertFalse(decision.shouldPushToCloud)
        assertEquals("TUS 2027", decision.merged.title)
        assertEquals(1_813_536_000_000L, decision.merged.targetDateMillis)
    }

    @Test
    fun countdown_newerRemoteClearRemovesCountdownOnSecondDevice() {
        val local = ExamCountdownState(
            title = "YKS 2027",
            targetDateMillis = 1_813_536_000_000L,
            updatedAtMillis = 1_795_100_000_000L
        )
        val remoteCleared = ExamCountdownState(
            title = "",
            targetDateMillis = 0L,
            updatedAtMillis = 1_795_350_000_000L
        )

        val decision = resolveCountdownMerge(local, remoteCleared)
        assertTrue(decision.shouldUpdateLocal)
        assertFalse(decision.shouldPushToCloud)
        assertEquals(0L, decision.merged.targetDateMillis)
        assertEquals("", decision.merged.title)
    }

    @Test
    fun countdown_newerOfflineLocalEditPushesToCloud() {
        val local = ExamCountdownState(
            title = "AYT Matematik Final",
            targetDateMillis = 1_813_999_000_000L,
            updatedAtMillis = 1_795_400_000_000L,
            isPendingSync = true
        )
        val remote = ExamCountdownState(
            title = "YKS 2027",
            targetDateMillis = 1_813_536_000_000L,
            updatedAtMillis = 1_795_200_000_000L
        )

        val decision = resolveCountdownMerge(local, remote)
        assertFalse(decision.shouldUpdateLocal)
        assertTrue(decision.shouldPushToCloud)
        assertEquals("AYT Matematik Final", decision.merged.title)
    }

    @Test
    fun countdown_legacyZeroTimestampMigratesWithoutLoss() {
        val emptyLocal = ExamCountdownState()
        val legacyRemote = ExamCountdownState(
            title = "KPSS 2027",
            targetDateMillis = 1_815_000_000_000L,
            updatedAtMillis = 0L
        )

        val pullDecision = resolveCountdownMerge(emptyLocal, legacyRemote)
        assertTrue(pullDecision.shouldUpdateLocal)
        assertEquals("KPSS 2027", pullDecision.merged.title)
        assertEquals(1_815_000_000_000L, pullDecision.merged.targetDateMillis)

        val legacyLocal = ExamCountdownState(
            title = "LGS 2027",
            targetDateMillis = 1_812_000_000_000L,
            updatedAtMillis = 0L
        )
        val pushDecision = resolveCountdownMerge(legacyLocal, ExamCountdownState())
        assertTrue(pushDecision.shouldPushToCloud)
        assertEquals("LGS 2027", pushDecision.merged.title)
    }
}
