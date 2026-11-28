package com.example.sharkmentor

import com.example.sharkmentor.core.timer.TimerPhase
import com.example.sharkmentor.ui.components.StudioQuickAccessCatalog
import com.example.sharkmentor.ui.components.StudioShowcaseCategory
import com.example.sharkmentor.ui.components.StudioToolIcon
import com.example.sharkmentor.ui.components.formatErrorBankDueSubtitle
import com.example.sharkmentor.ui.components.shouldShowStudioQuickAccessStrip
import com.example.sharkmentor.ui.navigation.Screen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Mark76StudioStripTest {

    @Test
    fun stripItemsContainAllEightSuperPowerToolsWithExpectedRoutesAndPrompts() {
        val items = StudioQuickAccessCatalog.buildStripItems(dueErrorCount = 0)
        assertEquals(8, items.size)
        assertEquals(8, items.map { it.id }.distinct().size)

        assertEquals("Grafikli Çöz", items[0].title)
        assertEquals(Screen.Shark.route, items[0].route)
        assertEquals("/detayli ", items[0].aiPrompt)
        assertEquals(StudioToolIcon.GRAPHIC_SOLVE, items[0].icon)

        assertEquals("Simülasyon Lab", items[1].title)
        assertEquals(Screen.Shark.route, items[1].route)
        assertEquals("/simulasyon ", items[1].aiPrompt)
        assertEquals(StudioToolIcon.SIMULATION_LAB, items[1].icon)

        assertEquals("Hata Bankası", items[2].title)
        assertEquals(Screen.ErrorBank.route, items[2].route)
        assertNull(items[2].aiPrompt)
        assertEquals("1-3-7 Hazır", items[2].subtitle)
        assertEquals(0, items[2].badgeCount)
        assertEquals(StudioToolIcon.ERROR_BANK, items[2].icon)

        assertEquals("PDF & Kement AI", items[3].title)
        assertEquals(Screen.Notes.route, items[3].route)
        assertNull(items[3].aiPrompt)
        assertEquals(StudioToolIcon.PDF_LASSO, items[3].icon)

        assertEquals("Kitaplarım", items[4].title)
        assertEquals(Screen.Resources.route, items[4].route)
        assertNull(items[4].aiPrompt)
        assertEquals(StudioToolIcon.BOOKS, items[4].icon)

        assertEquals("Net Sihirbazı", items[5].title)
        assertEquals(Screen.DetailedAnalytics.route, items[5].route)
        assertNull(items[5].aiPrompt)
        assertEquals(StudioToolIcon.NET_WIZARD, items[5].icon)

        assertEquals("PACE / KEEP", items[6].title)
        assertEquals(Screen.PaceKeep.route, items[6].route)
        assertNull(items[6].aiPrompt)
        assertEquals(StudioToolIcon.PACE_KEEP, items[6].icon)

        assertEquals("Zen Odası", items[7].title)
        assertEquals("zen_room", items[7].route)
        assertNull(items[7].aiPrompt)
        assertEquals(StudioToolIcon.ZEN_ROOM, items[7].icon)

        items.forEach { item ->
            assertTrue("Title must be non-blank for ${item.id}", item.title.isNotBlank())
            assertTrue("Subtitle must be non-blank for ${item.id}", item.subtitle.isNotBlank())
            assertTrue("Route must be non-blank for ${item.id}", item.route.isNotBlank())
        }
    }

    @Test
    fun errorBankLiveSubtitleReflectsFsrsDueCountAccurately() {
        assertEquals("1-3-7 Hazır", formatErrorBankDueSubtitle(0))
        assertEquals("1-3-7 Hazır", formatErrorBankDueSubtitle(-3))
        assertEquals("1 tekrar", formatErrorBankDueSubtitle(1))
        assertEquals("9 tekrar", formatErrorBankDueSubtitle(9))

        val stripWithDue = StudioQuickAccessCatalog.buildStripItems(dueErrorCount = 7)
        val errorCard = stripWithDue.first { it.id == "error_bank" }
        assertEquals("7 tekrar", errorCard.subtitle)
        assertEquals(7, errorCard.badgeCount)

        val stripNegativeGuard = StudioQuickAccessCatalog.buildStripItems(dueErrorCount = -4)
        val clampedErrorCard = stripNegativeGuard.first { it.id == "error_bank" }
        assertEquals("1-3-7 Hazır", clampedErrorCard.subtitle)
        assertEquals(0, clampedErrorCard.badgeCount)
    }

    @Test
    fun studioQuickAccessStripHidesOnlyDuringActiveFocusPhase() {
        assertFalse(shouldShowStudioQuickAccessStrip(TimerPhase.FOCUSING))
        assertTrue(shouldShowStudioQuickAccessStrip(TimerPhase.IDLE))
        assertTrue(shouldShowStudioQuickAccessStrip(TimerPhase.PAUSED))
        assertTrue(shouldShowStudioQuickAccessStrip(TimerPhase.MANDATORY_BREAK))

        for (phase in TimerPhase.entries) {
            val expectedVisible = phase != TimerPhase.FOCUSING
            assertEquals(
                "Visibility mismatch for $phase",
                expectedVisible,
                shouldShowStudioQuickAccessStrip(phase)
            )
        }
    }

    @Test
    fun showcaseModalCatalogGroupsAllCategoriesInBalancedTwoColumnRowsWithLiveErrorBadge() {
        val groupedZero = StudioQuickAccessCatalog.groupShowcaseByCategory(dueErrorCount = 0)
        assertEquals(StudioShowcaseCategory.entries.toSet(), groupedZero.keys)

        val allModules = groupedZero.values.flatten()
        assertEquals(allModules.size, allModules.map { it.id }.distinct().size)

        groupedZero.forEach { (category, modules) ->
            assertTrue("Category ${category.id} must have at least 2 modules", modules.size >= 2)
            assertEquals(
                "Category ${category.id} should have an even module count for a balanced 2-column grid",
                0,
                modules.size % 2
            )
            modules.forEach { mod ->
                assertTrue(mod.title.isNotBlank())
                assertTrue(mod.badge.isNotBlank())
                assertTrue(mod.description.isNotBlank())
                assertTrue(mod.route.isNotBlank())
                val prompt = mod.aiPrompt
                if (prompt != null) {
                    assertTrue("AI prompt must start with /", prompt.startsWith("/"))
                    assertEquals(Screen.Shark.route, mod.route)
                }
            }
        }

        val errorZero = allModules.first { it.id == "showcase_error_bank" }
        assertEquals("1-3-7 Hazır", errorZero.badge)

        val errorLive = StudioQuickAccessCatalog.buildShowcaseModules(dueErrorCount = 5)
            .first { it.id == "showcase_error_bank" }
        assertEquals("5 tekrar", errorLive.badge)
    }
}
