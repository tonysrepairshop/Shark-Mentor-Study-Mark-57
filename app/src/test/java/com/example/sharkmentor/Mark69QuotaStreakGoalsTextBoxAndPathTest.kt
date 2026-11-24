package com.example.sharkmentor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.example.sharkmentor.data.repository.StreakGoalTier
import com.example.sharkmentor.data.repository.StreakState
import com.example.sharkmentor.data.repository.evaluateStreakGoalMilestone
import com.example.sharkmentor.data.repository.resolveStreakMerge
import com.example.sharkmentor.ui.coaching.buildOtherPreviewDayOrder
import com.example.sharkmentor.ui.coaching.computeMapNodeHorizontalBias
import com.example.sharkmentor.ui.screens.notes.drawing.DrawingController
import com.example.sharkmentor.ui.screens.notes.drawing.DrawingTool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class Mark69QuotaStreakGoalsTextBoxAndPathTest {

    // ── 1. Streak Goal Challenges (7 / 14 / 30 / 50 gün) & Progressive Rewards ─

    @Test
    fun streakGoalTiers_rewardsScaleProgressivelyWithLongerConsecutiveDays() {
        val tiers = StreakGoalTier.ALL_TIERS
        assertEquals(listOf(7, 14, 30, 50), tiers.map { it.targetDays })

        for (i in 1 until tiers.size) {
            val prev = tiers[i - 1]
            val curr = tiers[i]
            assertTrue(
                "League points must strictly increase (${prev.targetDays} -> ${curr.targetDays})",
                curr.leaguePointsReward > prev.leaguePointsReward
            )
            assertTrue(
                "Weekly bonus seconds must strictly increase (${prev.targetDays} -> ${curr.targetDays})",
                curr.weeklyLeagueBonusSeconds > prev.weeklyLeagueBonusSeconds
            )
            assertTrue(
                "Streak freezes must strictly increase (${prev.targetDays} -> ${curr.targetDays})",
                curr.freezeReward > prev.freezeReward
            )
            val prevPerDay = prev.leaguePointsReward.toDouble() / prev.targetDays
            val currPerDay = curr.leaguePointsReward.toDouble() / curr.targetDays
            assertTrue(
                "Longer streaks must reward more points per day ($prevPerDay -> $currPerDay)",
                currPerDay > prevPerDay
            )
        }
    }

    @Test
    fun evaluateStreakGoal_unlocksSevenDayGoalOnceAndAdvancesToFourteen() {
        val below = evaluateStreakGoalMilestone(
            currentStreak = 6,
            selectedGoalDays = 7,
            claimedMilestones = emptySet()
        )
        assertTrue(below.newlyUnlockedTiers.isEmpty())
        assertEquals(0, below.totalLeaguePointsEarned)
        assertEquals(0, below.totalFreezesEarned)
        assertEquals(7, below.nextGoalDays)

        val reached = evaluateStreakGoalMilestone(
            currentStreak = 7,
            selectedGoalDays = 7,
            claimedMilestones = emptySet()
        )
        assertEquals(listOf(StreakGoalTier.DAYS_7), reached.newlyUnlockedTiers)
        assertEquals(150, reached.totalLeaguePointsEarned)
        assertEquals(900L, reached.totalWeeklyBonusSecondsEarned)
        assertEquals(1, reached.totalFreezesEarned)
        assertEquals(setOf(7), reached.updatedClaimedMilestones)
        assertEquals(14, reached.nextGoalDays)

        // Strictly idempotent when called again on same milestone
        val duplicate = evaluateStreakGoalMilestone(
            currentStreak = 7,
            selectedGoalDays = 7,
            claimedMilestones = reached.updatedClaimedMilestones
        )
        assertTrue(duplicate.newlyUnlockedTiers.isEmpty())
        assertEquals(0, duplicate.totalLeaguePointsEarned)
        assertEquals(0, duplicate.totalFreezesEarned)
    }

    @Test
    fun evaluateStreakGoal_reachingFiftyDayGoalAwardsTopMegalodonTier() {
        val result = evaluateStreakGoalMilestone(
            currentStreak = 50,
            selectedGoalDays = 50,
            claimedMilestones = setOf(7, 14, 30)
        )
        assertEquals(listOf(StreakGoalTier.DAYS_50), result.newlyUnlockedTiers)
        assertEquals(2000, result.totalLeaguePointsEarned)
        assertEquals(5400L, result.totalWeeklyBonusSecondsEarned)
        assertEquals(5, result.totalFreezesEarned)
        assertEquals(setOf(7, 14, 30, 50), result.updatedClaimedMilestones)
    }

    @Test
    fun streakMerge_preservesGoalSelectionAndUnionsClaimedMilestonesAcrossDevices() {
        val today = LocalDate.of(2026, 11, 23)
        val local = StreakState(
            streakCount = 14,
            streakFreezeAvailable = 2,
            lastStreakDate = "2026-11-23",
            completedDates = setOf("2026-11-23", "2026-11-22"),
            updatedAtMillis = 2_000_000L,
            streakGoalDays = 30,
            claimedGoalMilestones = setOf(7, 14)
        )
        val remote = StreakState(
            streakCount = 13,
            streakFreezeAvailable = 1,
            lastStreakDate = "2026-11-22",
            completedDates = setOf("2026-11-22"),
            updatedAtMillis = 1_500_000L,
            streakGoalDays = 14,
            claimedGoalMilestones = setOf(7)
        )

        val decision = resolveStreakMerge(local, remote, today = today)
        assertEquals(30, decision.merged.streakGoalDays)
        assertEquals(setOf(7, 14), decision.merged.claimedGoalMilestones)
    }

    // ── 2. Notes & PDFs Text Box Redesign State Machine ──────────────────────

    @Test
    fun textBox_closingOrSwitchingToolNeverDeletesNonEmptyText() {
        val controller = DrawingController()
        val box = controller.addTextBox(normalizedX = 0.2f, normalizedY = 0.25f)
        assertEquals(box.id, controller.activeTextBoxId)

        controller.updateTextBoxFieldValue(
            box.id,
            TextFieldValue(text = "Türev kritik nokta notu", selection = TextRange(23))
        )

        // Closing the active box (Done button / outside tap) must keep the text intact
        controller.closeActiveTextBox()
        assertNull(controller.activeTextBoxId)
        assertEquals(1, controller.textBoxes.size)
        assertEquals("Türev kritik nokta notu", controller.textBoxes.first().text)

        // Re-opening and switching to Pen tool must also preserve the text box
        controller.selectTextBox(box.id)
        assertEquals(box.id, controller.activeTextBoxId)
        controller.selectTool(DrawingTool.PEN)
        assertNull(controller.activeTextBoxId)
        assertEquals(1, controller.textBoxes.size)
        assertEquals("Türev kritik nokta notu", controller.textBoxes.first().text)
    }

    @Test
    fun textBox_closingBlankPlaceholderPrunesItCleanlyAndExplicitDeleteRemovesBox() {
        val controller = DrawingController()
        controller.addTextBox(normalizedX = 0.1f, normalizedY = 0.1f)
        assertEquals(1, controller.textBoxes.size)

        // Untouched blank placeholder is cleaned up on close
        controller.closeActiveTextBox()
        assertTrue(controller.textBoxes.isEmpty())

        // Non-empty box can still be explicitly deleted via Trash icon
        val populated = controller.addTextBox(normalizedX = 0.3f, normalizedY = 0.3f)
        controller.updateTextBoxFieldValue(
            populated.id,
            TextFieldValue(text = "Silinecek deneme", selection = TextRange(16))
        )
        controller.closeActiveTextBox()
        assertEquals(1, controller.textBoxes.size)

        controller.deleteTextBox(populated.id)
        assertTrue(controller.textBoxes.isEmpty())
    }

    @Test
    fun textBox_formattingControlsUpdateBoxEvenWithCollapsedSelection() {
        val controller = DrawingController()
        val box = controller.addTextBox(normalizedX = 0.2f, normalizedY = 0.2f)
        controller.updateTextBoxFieldValue(
            box.id,
            TextFieldValue(text = "Formül", selection = TextRange(6)) // collapsed caret at end
        )

        val initialSize = controller.textBoxes.first().fontSizeSp
        controller.stepTextBoxFontSize(4f)
        controller.applyTextBoxColor(Color(0xFF2563EB))

        val updated = controller.textBoxes.firstOrNull { it.id == box.id }
        assertNotNull(updated)
        assertEquals(initialSize + 4f, updated!!.fontSizeSp, 0.01f)
        assertEquals(0xFF2563EB.toInt(), updated.textColorArgb)
    }

    // ── 3. Full-Screen Today-Only Coaching Path Map & Preview Helpers ────────

    @Test
    fun coachingPathPreview_excludesTodayAndStartsWithTomorrowAcrossWeek() {
        for (today in 1..7) {
            val previewDays = buildOtherPreviewDayOrder(today)
            assertEquals("Must show exactly 6 other days", 6, previewDays.size)
            assertFalse("Preview must not include today ($today)", today in previewDays)
            assertEquals("All 6 other days must be distinct", 6, previewDays.toSet().size)
            val expectedTomorrow = if (today == 7) 1 else today + 1
            assertEquals("First preview day must be tomorrow", expectedTomorrow, previewDays.first())
        }
    }

    @Test
    fun coachingPathMap_horizontalBiasFormsSmoothBoundedSCurve() {
        val biases = (0 until 8).map { computeMapNodeHorizontalBias(it) }
        biases.forEach { b ->
            assertTrue("Bias must be within [-1f, 1f], got $b", b in -1f..1f)
        }
        assertEquals(0f, biases[0], 0.001f)
        assertTrue("Node 1 should curve left", biases[1] < 0f)
        assertEquals(0f, biases[2], 0.001f)
        assertTrue("Node 3 should curve right", biases[3] > 0f)
    }
}
