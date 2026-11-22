package com.example.sharkmentor.core.timer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusSessionRecorderTest {

    @Test
    fun testCheckpointConstantsMatchCouncilSpec() {
        assertEquals(20L * 60L * 1_000L, FocusSessionRecorder.LOCAL_CHECKPOINT_INTERVAL_MS)
        assertEquals(60L * 60L * 1_000L, FocusSessionRecorder.CLOUD_SYNC_INTERVAL_MS)
        assertEquals(60_000L, FocusSessionRecorder.MIN_RECORDABLE_DURATION_MS)
    }

    @Test
    fun testLocal20MinAndCloud60MinTriggersContinuePastOneHourTo3h49m() {
        var lastHeartbeatMs = 0L
        var lastLocalSaveMs = 0L
        var lastCloudSyncMs = 0L

        val localCheckpointMinutes = mutableListOf<Int>()
        val cloudSyncMinutes = mutableListOf<Int>()

        // Simulate 3 hours 49 minutes (229 minutes) tick-by-tick in 1-minute steps
        for (minute in 1..229) {
            val elapsedMs = minute * 60_000L
            val (heartbeat, local20m, cloud60m) = FocusSessionRecorder.computeCheckpointTriggers(
                elapsedMillis = elapsedMs,
                lastHeartbeatMs = lastHeartbeatMs,
                lastLocalSaveMs = lastLocalSaveMs,
                lastCloudSyncMs = lastCloudSyncMs
            )

            assertTrue("Heartbeat must fire at minute $minute", heartbeat)
            lastHeartbeatMs = elapsedMs

            if (local20m) {
                localCheckpointMinutes.add(minute)
                lastLocalSaveMs = elapsedMs
            }
            if (cloud60m) {
                cloudSyncMinutes.add(minute)
                lastCloudSyncMs = elapsedMs
            }
        }

        // Local Room saves MUST fire every 20 minutes uninterrupted well past the 1-hour mark
        assertEquals(
            listOf(20, 40, 60, 80, 100, 120, 140, 160, 180, 200, 220),
            localCheckpointMinutes
        )
        // Cloud sync attempts MUST fire every 60 minutes (1h, 2h, 3h)
        assertEquals(
            listOf(60, 120, 180),
            cloudSyncMinutes
        )
    }

    @Test
    fun testLocalCheckpointsRemainIndependentWhenCloudNeverCompletesOffline() {
        var lastHeartbeatMs = 0L
        var lastLocalSaveMs = 0L
        val lastCloudSyncMs = 0L // Simulates offline where cloud sync never succeeded

        val localCheckpointMinutes = mutableListOf<Int>()
        for (minute in 1..229) {
            val elapsedMs = minute * 60_000L
            val (_, local20m, cloud60m) = FocusSessionRecorder.computeCheckpointTriggers(
                elapsedMillis = elapsedMs,
                lastHeartbeatMs = lastHeartbeatMs,
                lastLocalSaveMs = lastLocalSaveMs,
                lastCloudSyncMs = lastCloudSyncMs
            )
            lastHeartbeatMs = elapsedMs
            if (local20m) {
                localCheckpointMinutes.add(minute)
                lastLocalSaveMs = elapsedMs
            }
            if (minute >= 60) {
                assertTrue("Cloud trigger remains eligible offline at minute $minute", cloud60m)
            }
        }

        assertEquals(
            listOf(20, 40, 60, 80, 100, 120, 140, 160, 180, 200, 220),
            localCheckpointMinutes
        )
    }

    @Test
    fun testExactThresholdBoundaries() {
        val justBefore20m = FocusSessionRecorder.computeCheckpointTriggers(
            elapsedMillis = FocusSessionRecorder.LOCAL_CHECKPOINT_INTERVAL_MS - 1L,
            lastHeartbeatMs = 0L,
            lastLocalSaveMs = 0L,
            lastCloudSyncMs = 0L
        )
        assertTrue(justBefore20m.first)
        assertFalse(justBefore20m.second)
        assertFalse(justBefore20m.third)

        val exact20m = FocusSessionRecorder.computeCheckpointTriggers(
            elapsedMillis = FocusSessionRecorder.LOCAL_CHECKPOINT_INTERVAL_MS,
            lastHeartbeatMs = 0L,
            lastLocalSaveMs = 0L,
            lastCloudSyncMs = 0L
        )
        assertTrue(exact20m.second)
        assertFalse(exact20m.third)

        val justBefore60m = FocusSessionRecorder.computeCheckpointTriggers(
            elapsedMillis = FocusSessionRecorder.CLOUD_SYNC_INTERVAL_MS - 1L,
            lastHeartbeatMs = FocusSessionRecorder.CLOUD_SYNC_INTERVAL_MS - 10_000L,
            lastLocalSaveMs = 40L * 60_000L,
            lastCloudSyncMs = 0L
        )
        assertFalse(justBefore60m.first)
        assertFalse(justBefore60m.second)
        assertFalse(justBefore60m.third)

        val exact60m = FocusSessionRecorder.computeCheckpointTriggers(
            elapsedMillis = FocusSessionRecorder.CLOUD_SYNC_INTERVAL_MS,
            lastHeartbeatMs = 0L,
            lastLocalSaveMs = 40L * 60_000L,
            lastCloudSyncMs = 0L
        )
        assertTrue(exact60m.second)
        assertTrue(exact60m.third)
    }

    @Test
    fun testOfflineSessionPreservesRealStudyTimestampInsteadOf3AmWifiReconnectTime() {
        val studyStartedAt1800 = 1_700_000_000_000L
        val studyDuration90Min = 90L * 60_000L
        val studyLastActiveAt1930 = studyStartedAt1800 + studyDuration90Min
        val wifiReconnectedAt0300 = studyLastActiveAt1930 + (7L * 3_600_000L) // 7 hours later at 3 AM

        val resolvedCompletedAt = FocusSessionRecorder.resolveActualCompletedAtMillis(
            startedAtWallMs = studyStartedAt1800,
            lastActiveWallMs = studyLastActiveAt1930,
            segmentDurationMs = studyDuration90Min,
            nowWallMs = wifiReconnectedAt0300
        )
        assertEquals(studyLastActiveAt1930, resolvedCompletedAt)
    }

    @Test
    fun testShouldSplitSessionAfterPausePreventsMergingOfflineStudyBlocks() {
        val pausedAt = 1_700_000_000_000L
        // Short 5-minute pause should NOT split into a new session
        assertFalse(
            FocusSessionRecorder.shouldSplitSessionAfterPause(
                lastActiveWallMs = pausedAt,
                resumeWallMs = pausedAt + (5L * 60_000L)
            )
        )
        // 25-minute pause (>= 20m threshold) MUST split into a separate session
        assertTrue(
            FocusSessionRecorder.shouldSplitSessionAfterPause(
                lastActiveWallMs = pausedAt,
                resumeWallMs = pausedAt + (25L * 60_000L)
            )
        )
    }
}
