package com.example.sharkmentor

import com.example.sharkmentor.core.ai.SharkTokenQuotaManager
import com.example.sharkmentor.core.audio.MusicPlaybackState
import com.example.sharkmentor.core.audio.MusicQueueItem
import com.example.sharkmentor.core.audio.PlatterScrubMath
import com.example.sharkmentor.core.audio.sliceUpcomingQueueItems
import com.example.sharkmentor.data.topbar.TopBarModule
import com.example.sharkmentor.data.topbar.TopBarSettings
import com.example.sharkmentor.ui.screens.notes.drawing.StylusHardwareInputBridge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Mark75FeaturesTest {

    @Test
    fun stylusHardwareBridgeDetectsSamsungSPenAndHonorMagicPencilButtonsWithoutFalsePositives() {
        // Samsung S-Pen barrel buttons (BUTTON_STYLUS_PRIMARY = 0x20, BUTTON_STYLUS_SECONDARY = 0x40)
        assertTrue(StylusHardwareInputBridge.isStylusMotionButtonPressed(StylusHardwareInputBridge.MASK_BUTTON_STYLUS_PRIMARY))
        assertTrue(StylusHardwareInputBridge.isStylusMotionButtonPressed(StylusHardwareInputBridge.MASK_BUTTON_STYLUS_SECONDARY))
        assertTrue(StylusHardwareInputBridge.isStylusMotionButtonPressed(0, StylusHardwareInputBridge.MASK_BUTTON_STYLUS_PRIMARY))
        // Primary finger/tip touch bit (0x01) must NEVER trigger barrel button
        assertFalse(StylusHardwareInputBridge.isStylusMotionButtonPressed(0x01))
        assertFalse(StylusHardwareInputBridge.isStylusMotionButtonPressed(0))

        // Android 14+ & Honor Magic-Pencil / Samsung S-Pen Pro hardware stylus keycodes
        assertTrue(StylusHardwareInputBridge.isStylusHardwareKeyCode(StylusHardwareInputBridge.KEYCODE_STYLUS_PRIMARY, isFromStylusSource = false))
        assertTrue(StylusHardwareInputBridge.isStylusHardwareKeyCode(StylusHardwareInputBridge.KEYCODE_STYLUS_SECONDARY, isFromStylusSource = false))
        assertTrue(StylusHardwareInputBridge.isStylusHardwareKeyCode(StylusHardwareInputBridge.KEYCODE_STYLUS_TERTIARY, isFromStylusSource = false))
        assertTrue(StylusHardwareInputBridge.isStylusHardwareKeyCode(StylusHardwareInputBridge.KEYCODE_STYLUS_TAIL, isFromStylusSource = false))
        // Honor/OEM Bluetooth stylus F20 (150) keycode with stylus source
        assertTrue(StylusHardwareInputBridge.isStylusHardwareKeyCode(150, isFromStylusSource = true))
        assertFalse(StylusHardwareInputBridge.isStylusHardwareKeyCode(150, isFromStylusSource = false))

        // Standard Back (4), Volume Up (24), Volume Down (25) must NEVER be captured as stylus button
        assertFalse(StylusHardwareInputBridge.isStylusHardwareKeyCode(4, isFromStylusSource = true))
        assertFalse(StylusHardwareInputBridge.isStylusHardwareKeyCode(24, isFromStylusSource = true))
        assertFalse(StylusHardwareInputBridge.isStylusHardwareKeyCode(25, isFromStylusSource = true))

        // Bluetooth capacitive double-tap / secondary key mapping
        assertTrue(StylusHardwareInputBridge.isNativeSecondaryOrDoubleTapKey(StylusHardwareInputBridge.KEYCODE_STYLUS_SECONDARY, isBluetoothStylus = false))
        assertTrue(StylusHardwareInputBridge.isNativeSecondaryOrDoubleTapKey(StylusHardwareInputBridge.KEYCODE_STYLUS_TAIL, isBluetoothStylus = false))
        assertTrue(StylusHardwareInputBridge.isNativeSecondaryOrDoubleTapKey(StylusHardwareInputBridge.KEYCODE_STYLUS_PRIMARY, isBluetoothStylus = true))
        assertFalse(StylusHardwareInputBridge.isNativeSecondaryOrDoubleTapKey(StylusHardwareInputBridge.KEYCODE_STYLUS_PRIMARY, isBluetoothStylus = false))
    }

    @Test
    fun musicPlaybackStateCarriesStructuredTappableQueueItems() {
        val items = listOf(
            MusicQueueItem(queueId = 42L, title = "Interstellar Main Theme", subtitle = "Hans Zimmer"),
            MusicQueueItem(queueId = 43L, title = "Time", subtitle = "Hans Zimmer")
        )
        val state = MusicPlaybackState(
            hasActiveSession = true,
            isPlaying = true,
            title = "Cornfield Chase",
            queueTitles = items.map { it.title },
            queueItems = items
        )
        assertEquals(2, state.queueItems.size)
        assertEquals(42L, state.queueItems[0].queueId)
        assertEquals("Time", state.queueTitles[1])
    }

    @Test
    fun platterScrubMathHandlesCircularForwardBackwardAndClampingCleanly() {
        // Wrap-around across -180/+180 boundary
        assertEquals(20f, PlatterScrubMath.normalizeAngleDeltaDeg(-340f), 0.01f)
        assertEquals(-20f, PlatterScrubMath.normalizeAngleDeltaDeg(340f), 0.01f)

        // Clockwise drag from 12 o'clock (100, 20) to 3 o'clock (180, 100) around center (100, 100) => +90 deg
        val cwStep = PlatterScrubMath.computePlatterStepDegrees(
            centerX = 100f, centerY = 100f,
            prevX = 100f, prevY = 20f,
            currX = 180f, currY = 100f,
            minRadiusPx = 16f
        )
        assertEquals(90f, cwStep, 0.5f)

        // Counter-clockwise drag from 3 o'clock (180, 100) back to 12 o'clock (100, 20) => -90 deg
        val ccwStep = PlatterScrubMath.computePlatterStepDegrees(
            centerX = 100f, centerY = 100f,
            prevX = 180f, prevY = 100f,
            currX = 100f, currY = 20f,
            minRadiusPx = 16f
        )
        assertEquals(-90f, ccwStep, 0.5f)

        // Full +360° spin advances +30s; -180° rewinds -15s; clamps within [0, durationMs]
        assertEquals(90_000L, PlatterScrubMath.computeScrubTargetMs(60_000L, 240_000L, 360f))
        assertEquals(45_000L, PlatterScrubMath.computeScrubTargetMs(60_000L, 240_000L, -180f))
        assertEquals(0L, PlatterScrubMath.computeScrubTargetMs(5_000L, 240_000L, -360f))
        assertEquals(240_000L, PlatterScrubMath.computeScrubTargetMs(230_000L, 240_000L, 360f))

        assertEquals("⏩ +14sn • 02:18", PlatterScrubMath.formatScrubBadgeText(14_000L, 138_000L))
        assertEquals("⏪ -9sn • 01:55", PlatterScrubMath.formatScrubBadgeText(-9_000L, 115_000L))
    }

    @Test
    fun upcomingQueueIsNoLongerCappedAtTwelveItems() {
        val fullPlaylist = (1..40).map { i ->
            MusicQueueItem(queueId = i.toLong(), title = "Track $i", subtitle = "Artist $i")
        }
        // Active track at id = 5 => should return ALL 35 remaining tracks (NOT capped at 12)
        val upcoming = sliceUpcomingQueueItems(fullPlaylist, activeQueueId = 5L)
        assertEquals(35, upcoming.size)
        assertEquals(6L, upcoming.first().queueId)
        assertEquals(40L, upcoming.last().queueId)

        // Unknown active id => returns all 40 valid tracks without truncation
        val allTracks = sliceUpcomingQueueItems(fullPlaylist, activeQueueId = -1L)
        assertEquals(40, allTracks.size)
    }

    @Test
    fun appLogoRemainsPermanentlyFixedAndCannotBeHiddenInTopBar() {
        val normalized = TopBarModule.normalizeOrder(
            listOf(TopBarModule.WEATHER, TopBarModule.MUSIC, TopBarModule.BRANDING)
        )
        assertEquals(TopBarModule.BRANDING, normalized.first())
        assertEquals(TopBarModule.DEFAULT_ORDER.size, normalized.size)

        // Even if BRANDING appears in a legacy hiddenModules set, isModuleVisible MUST stay true
        val settings = TopBarSettings(hiddenModules = setOf(TopBarModule.BRANDING, TopBarModule.COUNTDOWN))
        assertTrue(settings.isModuleVisible(TopBarModule.BRANDING))
        assertFalse(settings.isModuleVisible(TopBarModule.COUNTDOWN))
    }

    @Test
    fun tokenEstimatorReturnsSafeNonZeroFallbackForBuiltInQuotaRecording() {
        val estimated = SharkTokenQuotaManager.estimateTokens("Soru metni", "Detaylı çözüm")
        assertTrue("Estimated tokens must be >= 64", estimated >= 64L)
        assertEquals("80.0K", SharkTokenQuotaManager.formatCompactTokens(80_000L))
    }

    @Test
    fun jarvisAndUltronCodesMatchTurkishLocaleVariantsAndControlTokenLimitsCleanly() {
        assertTrue(SharkTokenQuotaManager.isUnlimitedTokenCode("JARVIS"))
        assertTrue(SharkTokenQuotaManager.isUnlimitedTokenCode("jarvis"))
        assertTrue(SharkTokenQuotaManager.isUnlimitedTokenCode("  Jarvis  "))
        assertTrue(SharkTokenQuotaManager.isUnlimitedTokenCode("JARVİS"))
        assertTrue(SharkTokenQuotaManager.isUnlimitedTokenCode("jarvıs"))

        assertTrue(SharkTokenQuotaManager.isResetTokenCode("ULTRON"))
        assertTrue(SharkTokenQuotaManager.isResetTokenCode("ultron"))
        assertTrue(SharkTokenQuotaManager.isResetTokenCode("  Ultron  "))
        assertTrue(SharkTokenQuotaManager.isSpecialTokenCode("JARVIS"))
        assertTrue(SharkTokenQuotaManager.isSpecialTokenCode("ULTRON"))

        assertFalse(SharkTokenQuotaManager.isUnlimitedTokenCode(null))
        assertFalse(SharkTokenQuotaManager.isUnlimitedTokenCode(""))
        assertFalse(SharkTokenQuotaManager.isUnlimitedTokenCode("SHK-102"))
        assertFalse(SharkTokenQuotaManager.isUnlimitedTokenCode("JARVIS2"))
        assertFalse(SharkTokenQuotaManager.isResetTokenCode("JARVIS"))
        assertFalse(SharkTokenQuotaManager.isResetTokenCode("ULTRON2"))
        assertFalse(SharkTokenQuotaManager.isSpecialTokenCode("SHK-102"))

        val maxedBlocked = com.example.sharkmentor.core.ai.SharkQuotaSnapshot(
            used5hTokens = 999_999L,
            usedWeeklyTokens = 9_999_999L,
            usedHourlyVisual = 99,
            usedHourlySimulation = 99,
            isUnlimitedUnlocked = false
        )
        assertTrue(
            SharkTokenQuotaManager.evaluateCheck(maxedBlocked, com.example.sharkmentor.core.ai.QuotaUsageKind.CHAT)
                is com.example.sharkmentor.core.ai.QuotaCheckResult.Blocked
        )
        assertTrue(
            SharkTokenQuotaManager.evaluateCheck(maxedBlocked, com.example.sharkmentor.core.ai.QuotaUsageKind.VISUAL_QUESTION)
                is com.example.sharkmentor.core.ai.QuotaCheckResult.Blocked
        )
        assertTrue(
            SharkTokenQuotaManager.evaluateCheck(maxedBlocked, com.example.sharkmentor.core.ai.QuotaUsageKind.SIMULATION)
                is com.example.sharkmentor.core.ai.QuotaCheckResult.Blocked
        )

        val unlocked = maxedBlocked.copy(isUnlimitedUnlocked = true)
        assertEquals(
            com.example.sharkmentor.core.ai.QuotaCheckResult.Allowed,
            SharkTokenQuotaManager.evaluateCheck(unlocked, com.example.sharkmentor.core.ai.QuotaUsageKind.CHAT)
        )
        assertEquals(
            com.example.sharkmentor.core.ai.QuotaCheckResult.Allowed,
            SharkTokenQuotaManager.evaluateCheck(unlocked, com.example.sharkmentor.core.ai.QuotaUsageKind.VISUAL_QUESTION)
        )
        assertEquals(
            com.example.sharkmentor.core.ai.QuotaCheckResult.Allowed,
            SharkTokenQuotaManager.evaluateCheck(unlocked, com.example.sharkmentor.core.ai.QuotaUsageKind.SIMULATION)
        )
    }

    @Test
    fun allThemesDefineDedicatedContainersWithoutM3PurpleFallbackAndPaperUsesLightCoffee() {
        val forbiddenM3BaselinePurpleColors = setOf(
            androidx.compose.ui.graphics.Color(0xFFF7F2FA),
            androidx.compose.ui.graphics.Color(0xFFF3EDF7),
            androidx.compose.ui.graphics.Color(0xFFECE6F0),
            androidx.compose.ui.graphics.Color(0xFFE6E0E9),
            androidx.compose.ui.graphics.Color(0xFFFEF7FF),
            androidx.compose.ui.graphics.Color(0xFFDED8E1),
            androidx.compose.ui.graphics.Color(0xFFFFD8E4),
            androidx.compose.ui.graphics.Color(0xFF211F26),
            androidx.compose.ui.graphics.Color(0xFF2B2930),
            androidx.compose.ui.graphics.Color(0xFF36343B)
        )

        for (palette in com.example.sharkmentor.core.data.ThemePalette.entries) {
            for (isDark in listOf(false, true)) {
                val scheme = com.example.sharkmentor.ui.theme.paletteSchemeFor(palette, isDark)
                val tokens = listOf(
                    scheme.surfaceVariant,
                    scheme.surfaceBright,
                    scheme.surfaceDim,
                    scheme.surfaceContainerLow,
                    scheme.surfaceContainer,
                    scheme.surfaceContainerHigh,
                    scheme.surfaceContainerHighest,
                    scheme.tertiaryContainer
                )
                for (color in tokens) {
                    assertFalse(
                        "Theme ${palette.name} (dark=$isDark) fell back to M3 default purple token $color",
                        color in forbiddenM3BaselinePurpleColors
                    )
                }
            }
        }

        // Paper Light MUST use warm light-coffee tones (R > G > B) across cards & containers
        val paperLight = com.example.sharkmentor.ui.theme.paletteSchemeFor(
            com.example.sharkmentor.core.data.ThemePalette.PAPER,
            isDark = false
        )
        for (coffeeSurface in listOf(
            paperLight.surfaceVariant,
            paperLight.surfaceContainerLow,
            paperLight.surfaceContainer,
            paperLight.surfaceContainerHigh,
            paperLight.surfaceContainerHighest
        )) {
            assertTrue("Paper Light surface must be warm light-coffee (R > G > B)", coffeeSurface.red > coffeeSurface.green && coffeeSurface.green > coffeeSurface.blue)
        }

        // Blueberry Light exclusively keeps its dedicated purple/lavender container tint (B > R & B > G)
        val blueberryLight = com.example.sharkmentor.ui.theme.paletteSchemeFor(
            com.example.sharkmentor.core.data.ThemePalette.BLUEBERRY,
            isDark = false
        )
        assertTrue(blueberryLight.surfaceVariant.blue > blueberryLight.surfaceVariant.red)
        assertTrue(blueberryLight.surfaceContainer.blue > blueberryLight.surfaceContainer.green)
    }

    @Test
    fun libraryFilterEngineOnlyExposesStudentSubjectsAndMultiSelectFiltersAccurately() {
        val books = listOf(
            com.example.sharkmentor.data.local.entity.ResourceEntity(
                id = 1L, title = "Palme Biyoloji", subject = "Biyoloji", totalUnits = 100, completedUnits = 100
            ),
            com.example.sharkmentor.data.local.entity.ResourceEntity(
                id = 2L, title = "Aydın Kimya Konu", subject = "Kimya", totalUnits = 80, completedUnits = 20
            ),
            com.example.sharkmentor.data.local.entity.ResourceEntity(
                id = 3L, title = "Orbital Kimya Tekrar", subject = "Kimya", totalUnits = 60, completedUnits = 45
            )
        )
        val assignments = mapOf(
            1L to com.example.sharkmentor.core.coaching.BookCoachingAssignment(
                resourceId = 1L, subject = "Biyoloji", role = com.example.sharkmentor.core.coaching.BookCoachingRole.PRIMARY_PRACTICE
            ),
            2L to com.example.sharkmentor.core.coaching.BookCoachingAssignment(
                resourceId = 2L, subject = "Kimya", role = com.example.sharkmentor.core.coaching.BookCoachingRole.CONCEPT_VIDEO
            ),
            3L to com.example.sharkmentor.core.coaching.BookCoachingAssignment(
                resourceId = 3L, subject = "Kimya", role = com.example.sharkmentor.core.coaching.BookCoachingRole.REVISION_BOOK
            )
        )

        // Only Biyoloji and Kimya exist -> Matematik MUST NEVER appear
        val subjects = com.example.sharkmentor.core.coaching.LibraryFilterEngine.availableSubjects(books, assignments)
        assertEquals(listOf("Biyoloji", "Kimya"), subjects)
        assertFalse(subjects.any { it.equals("Matematik", ignoreCase = true) })

        // Dynamic roles scoped to Kimya -> only CONCEPT_VIDEO and REVISION_BOOK (not PRIMARY_PRACTICE)
        val kimyaRoles = com.example.sharkmentor.core.coaching.LibraryFilterEngine.availableRoles(
            resources = books,
            assignments = assignments,
            selectedSubjects = setOf("Kimya")
        )
        assertEquals(
            listOf(
                com.example.sharkmentor.core.coaching.BookCoachingRole.CONCEPT_VIDEO,
                com.example.sharkmentor.core.coaching.BookCoachingRole.REVISION_BOOK
            ),
            kimyaRoles
        )

        // Filter by Book Type = REVISION_BOOK -> only Orbital Kimya Tekrar
        val revisionOnly = com.example.sharkmentor.core.coaching.LibraryFilterEngine.filterAndSort(
            resources = books,
            assignments = assignments,
            state = com.example.sharkmentor.core.coaching.LibraryFilterState(
                selectedRoles = setOf(com.example.sharkmentor.core.coaching.BookCoachingRole.REVISION_BOOK)
            )
        )
        assertEquals(listOf(3L), revisionOnly.map { it.id })

        // Filter by Status = IN_PROGRESS + Sort PROGRESS_DESC -> Orbital (75%) before Aydın (25%)
        val inProgressSorted = com.example.sharkmentor.core.coaching.LibraryFilterEngine.filterAndSort(
            resources = books,
            assignments = assignments,
            state = com.example.sharkmentor.core.coaching.LibraryFilterState(
                status = com.example.sharkmentor.core.coaching.LibraryStatusFilter.IN_PROGRESS,
                sortMode = com.example.sharkmentor.core.coaching.LibrarySortMode.PROGRESS_DESC
            )
        )
        assertEquals(listOf(3L, 2L), inProgressSorted.map { it.id })

        // Stale selection pruning when Biyoloji book is removed
        val pruned = com.example.sharkmentor.core.coaching.LibraryFilterEngine.pruneStaleSelections(
            state = com.example.sharkmentor.core.coaching.LibraryFilterState(
                selectedSubjects = setOf("Biyoloji", "Kimya"),
                selectedRoles = setOf(
                    com.example.sharkmentor.core.coaching.BookCoachingRole.PRIMARY_PRACTICE,
                    com.example.sharkmentor.core.coaching.BookCoachingRole.CONCEPT_VIDEO
                )
            ),
            resources = books.filter { it.id != 1L },
            assignments = assignments
        )
        assertEquals(setOf("Kimya"), pruned.selectedSubjects)
        assertEquals(setOf(com.example.sharkmentor.core.coaching.BookCoachingRole.CONCEPT_VIDEO), pruned.selectedRoles)
    }

    @Test
    fun detailedVisualSolveDetectsFunctionGraphsMasksStreamingSvgAndEnsuresViewBox() {
        assertTrue(
            com.example.sharkmentor.ui.components.requiresVisualDiagramForDetailedSolve(
                userPrompt = "f(x) = x^2 - 4x + 3 fonksiyonunun grafiğini ve tepe noktasını bul",
                solutionText = "Adım 1: Tepe noktası (2, -1) ve kökler 1 ile 3'tür.",
                hasImageAttachment = false
            )
        )
        assertFalse(
            com.example.sharkmentor.ui.components.requiresVisualDiagramForDetailedSolve(
                userPrompt = "Osmanlı Devleti hangi yılda kuruldu?",
                solutionText = "Osmanlı Devleti 1299 yılında kuruldu.",
                hasImageAttachment = false
            )
        )

        // Streaming mask replaces incomplete SVG code with clean indicator
        val masked = com.example.sharkmentor.ui.components.maskInProgressSvgForStreaming(
            "```svg\n<svg viewBox=\"0 0 380 240\"><line x1=\"0\" "
        )
        assertTrue(masked.contains("Grafik ve görsel diyagram çiziliyor"))
        assertFalse(masked.contains("<line"))

        // Completed SVG without viewBox gets normalized with default viewBox
        val (svg, cleanText) = com.example.sharkmentor.ui.components.extractSvgDiagramAndCleanText(
            "```svg\n<svg><circle cx=\"50\" cy=\"50\" r=\"20\"/></svg>\n```\nAdım 1: Çember çizildi."
        )
        assertTrue(svg != null && svg.contains("viewBox=\"0 0 380 240\""))
        assertEquals("Adım 1: Çember çizildi.", cleanText)
    }

    @Test
    fun jarvisPersonaDirectiveAndLaunchBriefingEngineRespondToTimeWeatherAndErrors() {
        val activeDirective = SharkTokenQuotaManager.buildJarvisPersonaDirective(true)
        assertTrue(activeDirective.contains("J.A.R.V.I.S."))
        assertTrue(activeDirective.contains("efendim"))
        assertEquals("", SharkTokenQuotaManager.buildJarvisPersonaDirective(false))

        assertEquals("Günaydın efendim.", com.example.sharkmentor.ui.components.JarvisBriefingEngine.buildGreeting(8))
        assertEquals("İyi günler efendim.", com.example.sharkmentor.ui.components.JarvisBriefingEngine.buildGreeting(14))
        assertEquals("İyi akşamlar efendim.", com.example.sharkmentor.ui.components.JarvisBriefingEngine.buildGreeting(20))
        assertEquals("İyi geceler efendim.", com.example.sharkmentor.ui.components.JarvisBriefingEngine.buildGreeting(1))

        val withDueErrors = com.example.sharkmentor.ui.components.JarvisBriefingEngine.buildBriefing(
            hour = 9,
            dueErrorQuestions = 6,
            weatherTempC = 12.0,
            weatherCode = 61
        )
        assertEquals(3, withDueErrors.actions.size)
        assertEquals("error_bank", withDueErrors.actions.first().route)
        assertTrue(withDueErrors.recommendation.contains("6"))
        assertTrue(withDueErrors.recommendation.contains("yağış"))
    }

    @Test
    fun controlPanelSettingsNormalizesReordersAndTogglesModulesCleanly() {
        // Modular Control Panel MUST stay locked for standard users (and after ULTRON) and open ONLY with JARVIS
        assertFalse(com.example.sharkmentor.data.controlpanel.ControlPanelPreferences.isModularControlPanelEnabled(false))
        assertTrue(com.example.sharkmentor.data.controlpanel.ControlPanelPreferences.isModularControlPanelEnabled(true))

        val normalized = com.example.sharkmentor.data.controlpanel.ControlPanelModule.normalizeOrder(
            listOf(
                com.example.sharkmentor.data.controlpanel.ControlPanelModule.THEME_STRIP,
                com.example.sharkmentor.data.controlpanel.ControlPanelModule.THEME_STRIP
            )
        )
        assertEquals(com.example.sharkmentor.data.controlpanel.ControlPanelModule.DEFAULT_ORDER.size, normalized.size)
        assertEquals(com.example.sharkmentor.data.controlpanel.ControlPanelModule.THEME_STRIP, normalized.first())

        var settings = com.example.sharkmentor.data.controlpanel.ControlPanelSettings()
        settings = settings.moveUp(com.example.sharkmentor.data.controlpanel.ControlPanelModule.STUDY_MACROS)
        assertEquals(
            com.example.sharkmentor.data.controlpanel.ControlPanelModule.STUDY_MACROS,
            settings.moduleOrder.first()
        )
        settings = settings.toggleVisibility(com.example.sharkmentor.data.controlpanel.ControlPanelModule.STUDY_MACROS)
        assertFalse(settings.isModuleVisible(com.example.sharkmentor.data.controlpanel.ControlPanelModule.STUDY_MACROS))
        assertEquals(
            com.example.sharkmentor.data.controlpanel.ControlPanelModule.DAILY_SUMMARY,
            settings.visibleModulesInOrder().first()
        )
    }
}

