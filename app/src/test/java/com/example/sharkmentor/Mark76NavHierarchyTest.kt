package com.example.sharkmentor

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import com.example.sharkmentor.ui.aichat.SharkSlashCommand
import com.example.sharkmentor.ui.aichat.SharkSlashCommandRegistry
import com.example.sharkmentor.ui.navigation.Screen
import com.example.sharkmentor.ui.navigation.bottomBarCenterIndex
import com.example.sharkmentor.ui.navigation.buildSharkQuickActionPrompt
import com.example.sharkmentor.ui.navigation.formatDueBadgeCount
import com.example.sharkmentor.ui.navigation.isBottomTabSelected
import com.example.sharkmentor.ui.navigation.isCenterAiCore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Mark76NavHierarchyTest {

    @Test
    fun bottomBarItemsOrderPlacesSharkAiDeadCenter() {
        val items = Screen.bottomBarItems
        assertEquals(
            listOf(
                Screen.Focus,
                Screen.Dashboard,
                Screen.Shark,
                Screen.Notes,
                Screen.ErrorBank,
            ),
            items
        )
        assertEquals(5, items.size)
        assertEquals(Screen.CENTER_CORE_INDEX, bottomBarCenterIndex(items))
        assertEquals(2, bottomBarCenterIndex(items))
        assertEquals(Screen.Shark, items[bottomBarCenterIndex(items)])

        assertTrue(isCenterAiCore(Screen.Shark, items))
        assertFalse(isCenterAiCore(Screen.Focus, items))
        assertFalse(isCenterAiCore(Screen.Dashboard, items))
        assertFalse(isCenterAiCore(Screen.Notes, items))
        assertFalse(isCenterAiCore(Screen.ErrorBank, items))
        assertFalse(isCenterAiCore(Screen.Resources, items))
    }

    @Test
    fun bottomBarLabelsAreConciseSingleLineAndSharkUsesAutoAwesomeIcon() {
        val labels = Screen.bottomBarItems.map { it.label }
        assertEquals(
            listOf("Odak", "Plan & Kitap", "SharkAI", "Studio", "Hata"),
            labels
        )
        labels.forEach { label ->
            assertTrue("Label must not be blank", label.isNotBlank())
            assertFalse("Label must stay single-line: $label", label.contains('\n'))
        }
        assertEquals(Icons.Default.AutoAwesome, Screen.Shark.icon)

        // Resources destination must remain preserved even though it sits outside bottomBarItems
        assertEquals("resources", Screen.Resources.route)
        assertEquals("Kaynaklar", Screen.Resources.label)
    }

    @Test
    fun formatDueBadgeCountHandlesZeroNegativeSingleDoubleAndOver99() {
        assertNull(formatDueBadgeCount(Int.MIN_VALUE))
        assertNull(formatDueBadgeCount(-5))
        assertNull(formatDueBadgeCount(0))

        assertEquals("1", formatDueBadgeCount(1))
        assertEquals("9", formatDueBadgeCount(9))
        assertEquals("10", formatDueBadgeCount(10))
        assertEquals("42", formatDueBadgeCount(42))
        assertEquals("99", formatDueBadgeCount(99))

        assertEquals("99+", formatDueBadgeCount(100))
        assertEquals("99+", formatDueBadgeCount(250))
        assertEquals("99+", formatDueBadgeCount(Int.MAX_VALUE))
    }

    @Test
    fun isCenterAiCoreRejectsEmptyEvenOrOffCenterLists() {
        assertEquals(-1, bottomBarCenterIndex(emptyList()))
        assertFalse(isCenterAiCore(Screen.Shark, emptyList()))

        val evenList = listOf(Screen.Focus, Screen.Dashboard, Screen.Shark, Screen.Notes)
        assertFalse(isCenterAiCore(Screen.Shark, evenList))

        val legacyOrder = listOf(Screen.Focus, Screen.Dashboard, Screen.Notes, Screen.Shark, Screen.ErrorBank)
        assertFalse(isCenterAiCore(Screen.Shark, legacyOrder))
        assertFalse(isCenterAiCore(Screen.Notes, legacyOrder))
    }

    @Test
    fun isBottomTabSelectedLinksDashboardAndResourcesWithoutBreakingResourcesRoute() {
        assertFalse(isBottomTabSelected(Screen.Dashboard, null))
        assertFalse(isBottomTabSelected(Screen.Dashboard, ""))
        assertFalse(isBottomTabSelected(Screen.Focus, null))

        // Plan & Kitap covers both Dashboard ("plans") and Resources ("resources")
        assertTrue(isBottomTabSelected(Screen.Dashboard, Screen.Dashboard.route))
        assertTrue(isBottomTabSelected(Screen.Dashboard, Screen.Resources.route))
        assertFalse(isBottomTabSelected(Screen.Dashboard, Screen.Focus.route))

        assertTrue(isBottomTabSelected(Screen.Focus, Screen.Focus.route))
        assertFalse(isBottomTabSelected(Screen.Focus, Screen.Resources.route))
        assertTrue(isBottomTabSelected(Screen.Shark, Screen.Shark.route))
        assertTrue(isBottomTabSelected(Screen.Notes, Screen.Notes.route))
        assertTrue(isBottomTabSelected(Screen.ErrorBank, Screen.ErrorBank.route))
    }

    @Test
    fun sharkCoreQuickActionsExposeDetayliSimulasyonPlanAndIntegrateWithSlashRegistry() {
        val actions = Screen.sharkCoreQuickActions
        assertEquals(listOf("/detayli", "/simulasyon", "/plan"), actions.map { it.command })

        actions.forEach { action ->
            assertTrue(action.title.isNotBlank())
            assertTrue(action.subtitle.isNotBlank())
            assertNotNull(action.icon)

            val prefill = buildSharkQuickActionPrompt(action.command)
            assertTrue(prefill.endsWith(" "))
            assertTrue(
                "Prefill should not re-open autocomplete dropdown",
                SharkSlashCommandRegistry.filterSuggestions(prefill).isEmpty()
            )
            val parsed = SharkSlashCommandRegistry.parseLeadingCommand(prefill)
            assertNotNull("Command must parse cleanly: ${action.command}", parsed)
            assertEquals(action.command, parsed!!.command.command)
        }

        assertEquals(
            SharkSlashCommand.DETAYLI,
            SharkSlashCommandRegistry.parseLeadingCommand(buildSharkQuickActionPrompt("/detayli"))?.command
        )
        assertEquals(
            SharkSlashCommand.SIMULASYON,
            SharkSlashCommandRegistry.parseLeadingCommand(buildSharkQuickActionPrompt("/simulasyon"))?.command
        )
        assertEquals(
            SharkSlashCommand.PLAN,
            SharkSlashCommandRegistry.parseLeadingCommand(buildSharkQuickActionPrompt("/plan"))?.command
        )
    }
}
