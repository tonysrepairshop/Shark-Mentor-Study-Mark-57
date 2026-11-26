package com.example.sharkmentor

import com.example.sharkmentor.core.audio.MusicPlaybackState
import com.example.sharkmentor.core.audio.MusicPlayerSkin
import com.example.sharkmentor.core.audio.MusicSourceKind
import com.example.sharkmentor.core.audio.formatMusicDurationLabel
import com.example.sharkmentor.core.audio.shouldShowLeftMusicScreen
import com.example.sharkmentor.core.coaching.BookCampManager
import com.example.sharkmentor.core.coaching.CampDayEpisode
import com.example.sharkmentor.core.coaching.CoachingEngine
import com.example.sharkmentor.core.flashcard.NotePodcastEngine
import com.example.sharkmentor.core.flashcard.computePodcastSourceKey
import com.example.sharkmentor.core.flashcard.parsePodcastSegmentsJson
import com.example.sharkmentor.core.flashcard.sanitizeForNaturalSpeech
import com.example.sharkmentor.core.theme.SolarScheduleCalculator
import com.example.sharkmentor.core.theme.formatMinuteOfDay
import com.example.sharkmentor.core.theme.isMinuteInNightWindow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class Mark71FeaturesTest {

    @Test
    fun focusSubjectStartsBlankAndPreservesCustomTypedStudyTopic() {
        // Empty title + empty subNote defaults cleanly to "Odak" (never "Kimya")
        assertEquals("Odak", CoachingEngine.buildFormattedFocusSubject("", ""))

        // Empty title + user-written topic ("Analitik Geometri") -> exact user text
        val customOnly = CoachingEngine.buildFormattedFocusSubject("", "Analitik Geometri")
        assertEquals("Analitik Geometri", customOnly)
        assertEquals("Analitik Geometri", CoachingEngine.formatCleanLeagueSubject(customOnly))

        // Category + user-written topic -> both preserved across app boxes & League
        val combined = CoachingEngine.buildFormattedFocusSubject("AYT", "Analitik Geometri")
        assertEquals("AYT • Analitik Geometri", combined)
        assertEquals("AYT • Analitik Geometri", CoachingEngine.formatCleanLeagueSubject(combined))
    }

    @Test
    fun removeEpisodeAndReindexDeletesTargetVideoAndRenumbersSequentially() {
        val episodes = listOf(
            CampDayEpisode(dayNumber = 1, topicTitle = "Atom Modelleri", videoId = "vid00000001"),
            CampDayEpisode(dayNumber = 2, topicTitle = "Periyodik Sistem", videoId = "vid00000002"),
            CampDayEpisode(dayNumber = 3, topicTitle = "Kimyasal Türler", videoId = "vid00000003")
        )

        val updated = BookCampManager.removeEpisodeAndReindex(
            episodes = episodes,
            targetDayNumber = 2,
            targetVideoId = "vid00000002"
        )

        assertEquals(2, updated.size)
        assertEquals(1, updated[0].dayNumber)
        assertEquals("Atom Modelleri", updated[0].topicTitle)
        assertEquals(2, updated[1].dayNumber)
        assertEquals("Kimyasal Türler", updated[1].topicTitle)
    }

    @Test
    fun podcastParserExtractsTwoHostSummarySegmentsAndSanitizesMath() {
        val raw = """
            ```json
            [
              {
                "title": "Doğrunun Analitik İncelenmesi",
                "hostA": "Bu bölümde eğim ve doğru denklemlerinin temel mantığını özetliyoruz.",
                "hostB": "Özellikle iki noktası bilinen doğrunun eğim formülüne dikkat edelim."
              },
              {
                "title": "Noktanın Doğruya Uzaklığı",
                "hostA": "Mutlak değer içindeki denklemi kök içinde a² + b² ifadesine bölüyoruz.",
                "hostB": "Paralel iki doğru arasındaki uzaklıkta da aynı payda kullanılır."
              }
            ]
            ```
        """.trimIndent()

        val parsed = parsePodcastSegmentsJson(raw)
        assertEquals(2, parsed.size)
        assertEquals("Doğrunun Analitik İncelenmesi", parsed[0].title)
        assertTrue(parsed[1].hostA.contains("kare"))
        assertFalse(parsed[1].hostA.contains("²"))

        // Deterministic cache key regardless of set ordering
        val key1 = computePodcastSourceKey(setOf("n2", "n1"), setOf("p1"))
        val key2 = computePodcastSourceKey(setOf("n1", "n2"), setOf("p1"))
        assertEquals(key1, key2)

        // Offline paragraph summary fallback never uses flashcard Q&A
        val fallback = NotePodcastEngine.buildOfflineSummaryFallbackSegments(
            listOf("Analitik Notu" to "İki nokta arasındaki uzaklık Pisagor bağıntısından elde edilir. Orta nokta koordinatları ise aritmetik ortalamadır.")
        )
        assertTrue(fallback.isNotEmpty())
        assertEquals("Analitik Notu", fallback.first().title)
        assertEquals("x kare artı pi", sanitizeForNaturalSpeech("x² artı π"))
    }

    @Test
    fun solarSunriseSunsetAndNightWindowCalculationWorksAcrossMidnight() {
        val istanbulZone = ZoneId.of("Europe/Istanbul")
        val summer = SolarScheduleCalculator.calculateSunriseSunset(
            latitude = 41.0082,
            longitude = 28.9784,
            date = LocalDate.of(2026, 6, 21),
            zoneId = istanbulZone
        )
        val winter = SolarScheduleCalculator.calculateSunriseSunset(
            latitude = 41.0082,
            longitude = 28.9784,
            date = LocalDate.of(2026, 12, 21),
            zoneId = istanbulZone
        )

        // Summer days in Istanbul have earlier sunrise and later sunset than winter days
        assertTrue(summer.sunriseMinuteOfDay < winter.sunriseMinuteOfDay)
        assertTrue(summer.sunsetMinuteOfDay > winter.sunsetMinuteOfDay)

        // Overnight window 21:00 (1260) -> 07:00 (420)
        assertTrue(isMinuteInNightWindow(22 * 60 + 30, 21 * 60, 7 * 60))
        assertTrue(isMinuteInNightWindow(2 * 60 + 15, 21 * 60, 7 * 60))
        assertFalse(isMinuteInNightWindow(13 * 60, 21 * 60, 7 * 60))
        assertEquals("21:05", formatMinuteOfDay(21 * 60 + 5))
    }

    @Test
    fun leftMusicScreenVisibilityAndFiveSkinsMatchSpecification() {
        assertEquals(5, MusicPlayerSkin.entries.size)
        assertEquals(MusicPlayerSkin.DEFAULT, MusicPlayerSkin.fromId("default"))
        assertEquals(MusicPlayerSkin.MODERN_VINYL, MusicPlayerSkin.fromId("modern_vinyl"))
        assertEquals(MusicPlayerSkin.CLASSIC_VINYL, MusicPlayerSkin.fromId("classic_vinyl"))
        assertEquals(MusicPlayerSkin.CD, MusicPlayerSkin.fromId("cd"))
        assertEquals(MusicPlayerSkin.CASSETTE, MusicPlayerSkin.fromId("cassette"))

        val idleState = MusicPlaybackState(hasActiveSession = false)
        val activeSpotify = MusicPlaybackState(
            hasActiveSession = true,
            isPlaying = true,
            sourceKind = MusicSourceKind.EXTERNAL_MEDIA,
            sourcePackage = "com.spotify.music",
            title = "Enjoy Music",
            durationMs = 210_000L,
            positionMs = 56_000L
        )

        // Opens ONLY when feature is enabled AND music session is active
        assertFalse(shouldShowLeftMusicScreen(isFeatureEnabled = true, playbackState = idleState))
        assertFalse(shouldShowLeftMusicScreen(isFeatureEnabled = false, playbackState = activeSpotify))
        assertTrue(shouldShowLeftMusicScreen(isFeatureEnabled = true, playbackState = activeSpotify))
        assertEquals("00:56", formatMusicDurationLabel(56_000L))
        assertEquals("03:30", formatMusicDurationLabel(210_000L))
    }

    @Test
    fun sharkMusicThemeSyncPacksAndSharkMixPresetsAreValid() {
        // Removed custom skins fall back cleanly to MODERN_VINYL
        assertEquals(MusicPlayerSkin.MODERN_VINYL, MusicPlayerSkin.fromId("ocean"))
        assertEquals(MusicPlayerSkin.MODERN_VINYL, MusicPlayerSkin.fromId("christmas"))

        // Every one of the 13 app palettes has 3 distinct sync packs + pack 0 returns null (Original)
        com.example.sharkmentor.core.data.ThemePalette.entries.forEach { palette ->
            assertEquals(null, com.example.sharkmentor.ui.components.resolvePlayerSyncPalette(palette, 0))
            assertEquals(null, com.example.sharkmentor.ui.components.resolvePlayerSyncPalette(palette, 4))
            for (pack in 1..3) {
                val resolved = com.example.sharkmentor.ui.components.resolvePlayerSyncPalette(palette, pack)
                assertTrue(resolved != null)
                assertEquals(pack, resolved!!.packIndex)
                assertEquals(3, resolved.cardGradient.size)
            }
        }

        // SharkMix presets stay within MAX_MIX_LAYERS (3)
        assertTrue(com.example.sharkmentor.core.audio.SHARK_MIX_PRESETS.size >= 5)
        com.example.sharkmentor.core.audio.SHARK_MIX_PRESETS.forEach { preset ->
            assertTrue(preset.layers.size in 2..com.example.sharkmentor.core.audio.ZenAudioManager.MAX_MIX_LAYERS)
        }
    }

    @Test
    fun stylusShortcutEnumsParseAndFallbackCleanly() {
        assertEquals(
            com.example.sharkmentor.ui.screens.notes.drawing.StylusHoldAction.ERASER,
            com.example.sharkmentor.ui.screens.notes.drawing.StylusHoldAction.fromId("eraser")
        )
        assertEquals(
            com.example.sharkmentor.ui.screens.notes.drawing.StylusHoldAction.LASSO,
            com.example.sharkmentor.ui.screens.notes.drawing.StylusHoldAction.fromId("lasso")
        )
        assertEquals(
            com.example.sharkmentor.ui.screens.notes.drawing.StylusHoldAction.ERASER,
            com.example.sharkmentor.ui.screens.notes.drawing.StylusHoldAction.fromId("unknown")
        )
        assertEquals(
            com.example.sharkmentor.ui.screens.notes.drawing.StylusDoubleTapAction.LASSO,
            com.example.sharkmentor.ui.screens.notes.drawing.StylusDoubleTapAction.fromId("lasso")
        )
        assertEquals(
            com.example.sharkmentor.ui.screens.notes.drawing.StylusDoubleTapAction.UNDO,
            com.example.sharkmentor.ui.screens.notes.drawing.StylusDoubleTapAction.fromId("undo")
        )
        assertEquals(
            com.example.sharkmentor.ui.screens.notes.drawing.StylusDoubleTapAction.LASSO,
            com.example.sharkmentor.ui.screens.notes.drawing.StylusDoubleTapAction.fromId(null)
        )
    }

    @Test
    fun stylusSingleTapAndAutoShapeLiveResizeWorkCleanly() {
        assertEquals(
            com.example.sharkmentor.ui.screens.notes.drawing.StylusSingleTapAction.PEN,
            com.example.sharkmentor.ui.screens.notes.drawing.StylusSingleTapAction.fromId("pen")
        )
        assertEquals(
            com.example.sharkmentor.ui.screens.notes.drawing.StylusSingleTapAction.PEN,
            com.example.sharkmentor.ui.screens.notes.drawing.StylusSingleTapAction.fromId("unknown")
        )
        assertEquals(
            com.example.sharkmentor.ui.screens.notes.drawing.StylusHoldAction.PEN,
            com.example.sharkmentor.ui.screens.notes.drawing.StylusHoldAction.fromId("pen")
        )

        val baseLine = com.example.sharkmentor.ui.screens.notes.drawing.RecognizedShape(
            shapeKind = com.example.sharkmentor.ui.screens.notes.drawing.ShapeKind.LINE,
            points = listOf(
                com.example.sharkmentor.ui.screens.notes.drawing.DrawingPoint(10f, 100f, 1f),
                com.example.sharkmentor.ui.screens.notes.drawing.DrawingPoint(110f, 100f, 1f)
            ),
            labelTr = "Düz Çizgi"
        )
        val extended = com.example.sharkmentor.ui.screens.notes.drawing.AutoShapeRecognizer.resizeRecognizedShape(
            baseShape = baseLine,
            anchorX = 110f,
            anchorY = 100f,
            currentX = 260f,
            currentY = 102f
        )
        assertEquals(260f, extended.points.last().x, 0.5f)
        assertEquals(100f, extended.points.last().y, 0.5f)
    }
}
