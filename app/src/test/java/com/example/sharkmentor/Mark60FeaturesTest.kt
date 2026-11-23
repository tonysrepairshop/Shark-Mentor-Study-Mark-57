package com.example.sharkmentor

import com.example.sharkmentor.core.audio.ZenSound
import com.example.sharkmentor.core.coaching.BookCampManager
import com.example.sharkmentor.data.topbar.TopBarRefreshInterval
import com.example.sharkmentor.data.topbar.TopBarSettings
import com.example.sharkmentor.data.topbar.capitalizeTurkishSentence
import com.example.sharkmentor.data.weather.WeatherUiState
import com.example.sharkmentor.ui.components.MathSymbolCatalog
import com.example.sharkmentor.ui.components.TopBarSuggestionAction
import com.example.sharkmentor.ui.components.buildSmartTopBarSuggestions
import com.example.sharkmentor.ui.components.isRainyWeatherCode
import com.example.sharkmentor.ui.components.isSnowyOrColdWeather
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Mark60FeaturesTest {

    @Test
    fun capitalizeTurkishSentence_handlesTurkishIAndDotlessI() {
        assertEquals("İstikrarlı çalışmak seni zirveye taşır.", capitalizeTurkishSentence("istikrarlı çalışmak seni zirveye taşır."))
        assertEquals("Işığını kaybetme!", capitalizeTurkishSentence("ışığını kaybetme!"))
        assertEquals("Bugün yeni bir başlangıç.", capitalizeTurkishSentence("  bugün yeni bir başlangıç.  "))
        assertEquals("\"İnanmak başarmanın yarısıdır.\"", capitalizeTurkishSentence("\"inanmak başarmanın yarısıdır.\""))
        assertEquals("", capitalizeTurkishSentence("   "))
    }

    @Test
    fun topBarRefreshInterval_coversWideFixedScale() {
        val entries = TopBarRefreshInterval.entries
        assertTrue("Expected at least 12 refresh intervals", entries.size >= 12)
        assertEquals(5_000L, TopBarRefreshInterval.SEC_5.durationMs)
        assertEquals(86_400_000L, TopBarRefreshInterval.HOUR_24.durationMs)
        for (i in 1 until entries.size) {
            assertTrue(
                "Refresh intervals must be strictly increasing",
                entries[i].durationMs > entries[i - 1].durationMs
            )
        }
    }

    @Test
    fun smartTopBarSuggestions_recommendsRainSoundOnRainyDay() {
        assertTrue(isRainyWeatherCode(63))
        assertFalse(isRainyWeatherCode(0))
        assertTrue(isSnowyOrColdWeather(73, 12.0))
        assertTrue(isSnowyOrColdWeather(0, 5.0))

        val suggestions = buildSmartTopBarSuggestions(
            weatherState = WeatherUiState(currentWeatherCode = 63, currentTempCelsius = 14.0),
            isAnyFocusRunning = false,
            elapsedFocusMinutes = 0,
            settings = TopBarSettings(inAppSuggestionsEnabled = true)
        )
        val hasRainAction = suggestions.any { entry ->
            val action = entry.action
            action is TopBarSuggestionAction.StartZenSound && action.sound == ZenSound.RAIN
        }
        assertTrue("Rainy weather should suggest starting ZenSound.RAIN", hasRainAction)
        assertTrue("All top bar suggestions must start with uppercase letter", suggestions.all { s ->
            val firstLetter = s.text.firstOrNull { it.isLetter() }
            firstLetter == null || firstLetter.isUpperCase()
        })
    }

    @Test
    fun smartTopBarSuggestions_excludesHydrationAndPostureMovedToSmartwatchPopup() {
        val suggestions = buildSmartTopBarSuggestions(
            weatherState = WeatherUiState(currentWeatherCode = 0, currentTempCelsius = 22.0),
            isAnyFocusRunning = true,
            elapsedFocusMinutes = 65,
            settings = TopBarSettings(
                inAppSuggestionsEnabled = true,
                hydrationReminderEnabled = true,
                hydrationIntervalMinutes = 45,
                postureReminderEnabled = true,
                postureIntervalMinutes = 50
            )
        )
        val wellnessCount = suggestions.count { it.action is TopBarSuggestionAction.WellnessReminder }
        assertEquals(0, wellnessCount)
    }

    @Test
    fun smartTopBarSuggestions_respectsDisabledInAppAndWellnessToggles() {
        val suggestions = buildSmartTopBarSuggestions(
            weatherState = WeatherUiState(currentWeatherCode = 63, currentTempCelsius = 14.0),
            isAnyFocusRunning = true,
            elapsedFocusMinutes = 90,
            settings = TopBarSettings(
                inAppSuggestionsEnabled = false,
                hydrationReminderEnabled = false,
                postureReminderEnabled = false
            )
        )
        assertTrue(suggestions.none { it.action is TopBarSuggestionAction.StartZenSound })
        assertTrue(suggestions.none { it.action is TopBarSuggestionAction.WellnessReminder })
        assertTrue(suggestions.isNotEmpty())
    }

    @Test
    fun bookCampTimestampExtraction_parsesTurkishNaturalFormats() {
        assertEquals(270, BookCampManager.extractMentionedTimestampSeconds("4.5 dakikadaki formül neden öyle?"))
        assertEquals(270, BookCampManager.extractMentionedTimestampSeconds("4,5 dk civarındaki işlemi açıkla"))
        assertEquals(270, BookCampManager.extractMentionedTimestampSeconds("4:30 da ne yaptı hoca?"))
        assertEquals(3750, BookCampManager.extractMentionedTimestampSeconds("1:02:30 kısmını özetle"))
        assertEquals(480, BookCampManager.extractMentionedTimestampSeconds("8. dakikada neden eksi geldi?"))
        assertNull(BookCampManager.extractMentionedTimestampSeconds("Bu konunun temel formülü nedir?"))
        assertEquals("04:30", BookCampManager.formatSecondsAsTimestamp(270))
        assertEquals("1:02:30", BookCampManager.formatSecondsAsTimestamp(3750))
    }

    @Test
    fun videoAdBlocker_blocksAdEndpointsWhileAllowingLessonVideoPlayback() {
        assertTrue(BookCampManager.isAdOrTrackerUrl("https://googleads.g.doubleclick.net/pagead/id"))
        assertTrue(BookCampManager.isAdOrTrackerUrl("https://www.youtube.com/api/stats/ads?foo=1"))
        assertTrue(BookCampManager.isAdOrTrackerUrl("https://www.youtube.com/get_midroll_info?v=abc"))
        assertTrue(BookCampManager.isAdOrTrackerUrl("https://rr1---sn.googlevideo.com/videoplayback?ctier=A&oad=1"))

        assertFalse(BookCampManager.isAdOrTrackerUrl("https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ?autoplay=1"))
        assertFalse(BookCampManager.isAdOrTrackerUrl("https://rr1---sn.googlevideo.com/videoplayback?expire=9999&itag=18"))
        assertFalse(BookCampManager.isAdOrTrackerUrl("https://m.youtube.com/watch?v=dQw4w9WgXcQ"))
    }

    @Test
    fun mathSymbolCatalog_insertsSymbolsCleanly() {
        assertEquals("∫", MathSymbolCatalog.insertSymbol("", "∫"))
        assertEquals("f(x) = x² + √()", MathSymbolCatalog.insertSymbol("f(x) = x² + ", "√()"))
        assertEquals("x² + 1 = ", MathSymbolCatalog.insertSymbol("x² + 1", "="))
        assertTrue(MathSymbolCatalog.categories.size >= 4)
        assertTrue(MathSymbolCatalog.categories.all { it.symbols.isNotEmpty() })
    }
}
