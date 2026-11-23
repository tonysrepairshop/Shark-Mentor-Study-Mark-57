package com.example.sharkmentor.core.coaching

import com.example.sharkmentor.ui.aichat.SharkSlashCommand
import com.example.sharkmentor.ui.aichat.SharkSlashCommandRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Mark 69 Unit Test Suite:
 * 1. YouTube playlist dynamic edition-year & updatedHint freshness prioritization (2027 > 2026 > 2025 > older)
 * 2. Exact regression check: "konu takip çizelgesi hazırlar mısın" NEVER triggers Plan Rebuild intent
 * 3. Antigravity-style `/` slash command autocomplete, parsing, lock badges (/dopamin, /plan),
 *    missing-slash guidance for dedicated sub-runners, and full In-App Agent preservation without `/`.
 */
class Mark69SlashCommandsYearRankingAndBuiltInPolicyTest {

    @Test
    fun testYouTubePlaylistFreshnessPrioritizesNewestEditionYearsAndRecentUpdates() {
        val candidates = listOf(
            DiscoveredYouTubePlaylist(
                playlistId = "PL_FERRUM_2024",
                title = "Ferrum 2024 AYT Kimya Kampı",
                channelName = "Ferrum Kimya",
                videoCount = 45,
                editionYear = 2024,
                updatedHint = "2 yıl önce güncellendi"
            ),
            DiscoveredYouTubePlaylist(
                playlistId = "PL_FERRUM_2025",
                title = "Ferrum 2025 AYT Kimya Kampı",
                channelName = "Ferrum Kimya",
                videoCount = 46,
                editionYear = 2025,
                updatedHint = "1 yıl önce güncellendi"
            ),
            DiscoveredYouTubePlaylist(
                playlistId = "PL_FERRUM_2026",
                title = "Ferrum 2026 AYT Kimya Kampı",
                channelName = "Ferrum Kimya",
                videoCount = 45,
                editionYear = 2026,
                updatedHint = "2 ay önce güncellendi"
            ),
            DiscoveredYouTubePlaylist(
                playlistId = "PL_FERRUM_2027",
                title = "Ferrum 2027 AYT Kimya Kampı",
                channelName = "Ferrum Kimya",
                videoCount = 44,
                editionYear = 2027,
                updatedHint = "Bugün güncellendi"
            )
        )

        val (hasDirect, rankedTop3) = BookCampManager.rankHybridBookCandidates(
            resourceTitle = "Ferrum AYT Kimya",
            subject = "AYT Kimya",
            directRaw = candidates,
            subjectRaw = emptyList(),
            maxResults = 3,
            referenceYear = 2026
        )

        assertTrue(hasDirect)
        assertEquals(3, rankedTop3.size)
        // Strict newest-edition ordering: 2027 #1 > 2026 #2 > 2025 #3 (2024 pushed out of Top 3!)
        assertEquals("PL_FERRUM_2027", rankedTop3[0].playlistId)
        assertTrue(rankedTop3[0].isRecommended)
        assertTrue(rankedTop3[0].descriptionNote.contains("2027 güncel"))
        assertEquals("PL_FERRUM_2026", rankedTop3[1].playlistId)
        assertEquals("PL_FERRUM_2025", rankedTop3[2].playlistId)

        // When student explicitly writes an older year in book title (e.g. "Ferrum 2024 Baskı AYT Kimya"), honor it!
        val (_, explicitYearRanked) = BookCampManager.rankHybridBookCandidates(
            resourceTitle = "Ferrum 2024 AYT Kimya",
            subject = "AYT Kimya",
            directRaw = candidates,
            subjectRaw = emptyList(),
            maxResults = 3,
            referenceYear = 2026
        )
        assertEquals("PL_FERRUM_2024", explicitYearRanked.first().playlistId)
    }

    @Test
    fun testExtractPlaylistsFromSearchHtmlCapturesEditionYearAndUpdatedHint() {
        val html = """
            <html><script>var ytInitialData = {
              "contents": {
                "items": [
                  {
                    "lockupViewModel": {
                      "contentType": "LOCKUP_CONTENT_TYPE_PLAYLIST",
                      "contentId": "PLFerrumAyt2027New",
                      "metadata": {
                        "lockupMetadataViewModel": {
                          "title": {"content": "Ferrum 2027 AYT Kimya Kampı (48 Video)"},
                          "metadata": {
                            "contentMetadataViewModel": {
                              "metadataRows": [
                                {"metadataParts": [{"text": {"content": "Ferrum Kimya"}}]},
                                {"metadataParts": [{"text": {"content": "3 gün önce güncellendi"}}]}
                              ]
                            }
                          }
                        }
                      }
                    }
                  }
                ]
              }
            };</script></html>
        """.trimIndent()

        val extracted = BookCampManager.extractPlaylistsFromSearchHtml(html)
        assertEquals(1, extracted.size)
        assertEquals("PLFerrumAyt2027New", extracted[0].playlistId)
        assertEquals("Ferrum Kimya", extracted[0].channelName)
        assertEquals(2027, extracted[0].editionYear)
        assertEquals("3 gün önce güncellendi", extracted[0].updatedHint)
    }

    @Test
    fun testKonuTakipCizelgesiAndInAppAgentActionsRunDirectlyWithoutSlashOrFalsePlanPopup() {
        // Exact user prompt that previously misfired the Plan Rebuild / Dossier confirmation card!
        val exactTopicTrackingPrompt =
            "benim için internetten TYT ve AYT SAYISAL konularına bakıp en az 3 kaynaktan teyit edip sonra tyt ayt diye ayırmadan yalnızca ders klasörleri oluşturarak ve ortak konuları sadece 1 kez kaydederek konu takip çizelgesi hazırlar mısın hepsinin hakimiyet puanı 0 başlasın ben ayarlarım"

        assertFalse(
            "Konu takip çizelgesi must NEVER trigger Plan Rebuild intent",
            PlanProgressAuditor.isPlanRebuildOrCreateIntent(exactTopicTrackingPrompt)
        )
        assertNull(
            "Konu takip çizelgesi must run directly in Shark AI Agent mode without requiring /",
            SharkSlashCommandRegistry.detectMissingSlashCommandSuggestion(exactTopicTrackingPrompt)
        )

        // Other In-App Agent tasks MUST also work directly without `/`
        val directAgentPrompts = listOf(
            "Kütüphaneme Aydın AYT Kimya Soru Bankası kitabını ekle",
            "Hata bankamdaki fizik sorularını filtreleyip en çok hata yaptığım konuyu düzenle",
            "AYT Kimya Modern Atom Teorisi hakimiyet seviyemi 3 yap",
            "Aydın AYT Kimya kitabında 120. sayfaya kadar çözdüm işaretle",
            "Türev ile integral arasındaki temel ilişkiyi kısaca açıklar mısın?"
        )
        directAgentPrompts.forEach { prompt ->
            assertNull(
                "In-App Agent prompt should NOT require a slash command: $prompt",
                SharkSlashCommandRegistry.detectMissingSlashCommandSuggestion(prompt)
            )
        }
    }

    @Test
    fun testSlashCommandSuggestionsParsingLocksAndMissingSlashGuidance() {
        // 1. Autocomplete suggestions
        assertEquals(SharkSlashCommand.entries.size, SharkSlashCommandRegistry.filterSuggestions("/").size)
        assertEquals(listOf(SharkSlashCommand.PLAN), SharkSlashCommandRegistry.filterSuggestions("/pl"))
        assertEquals(listOf(SharkSlashCommand.DOPAMIN), SharkSlashCommandRegistry.filterSuggestions("/dop"))
        assertTrue("Menu must close once space is typed", SharkSlashCommandRegistry.filterSuggestions("/plan haftalık").isEmpty())
        assertTrue("Menu must not show on plain text", SharkSlashCommandRegistry.filterSuggestions("merhaba").isEmpty())

        // 2. Leading command parsing
        val parsedPlan = SharkSlashCommandRegistry.parseLeadingCommand("/plan Sayısal için haftalık program hazırla")
        assertNotNull(parsedPlan)
        assertEquals(SharkSlashCommand.PLAN, parsedPlan!!.command)
        assertEquals("Sayısal için haftalık program hazırla", parsedPlan.argumentText)

        // 3. Lock state badges (/dopamin locked when detox inactive; /plan locked for institutional students)
        val (dopLocked, dopReason) = SharkSlashCommandRegistry.isCommandLocked(
            SharkSlashCommand.DOPAMIN,
            isDopamineActive = false,
            isInstitutional = false
        )
        assertTrue(dopLocked)
        assertTrue(!dopReason.isNullOrBlank())

        val (dopUnlocked, _) = SharkSlashCommandRegistry.isCommandLocked(
            SharkSlashCommand.DOPAMIN,
            isDopamineActive = true,
            isInstitutional = false
        )
        assertFalse(dopUnlocked)

        val (planInstLocked, _) = SharkSlashCommandRegistry.isCommandLocked(
            SharkSlashCommand.PLAN,
            isDopamineActive = false,
            isInstitutional = true
        )
        assertTrue(planInstLocked)

        // 4. Dedicated sub-runner requests WITHOUT `/` return clear guidance + quickfill payload
        assertEquals(
            SharkSlashCommand.PLAN,
            SharkSlashCommandRegistry.detectMissingSlashCommandSuggestion("Bana bu hafta için haftalık çalışma planı hazırlar mısın?")
        )
        assertEquals(
            SharkSlashCommand.BENZER,
            SharkSlashCommandRegistry.detectMissingSlashCommandSuggestion("Türev konusunda ÖSYM tarzı benzer soru üret")
        )
        assertEquals(
            SharkSlashCommand.ZEN,
            SharkSlashCommandRegistry.detectMissingSlashCommandSuggestion("Zen koçu aç sınav kaygım var")
        )
        assertEquals(
            SharkSlashCommand.DOPAMIN,
            SharkSlashCommandRegistry.detectMissingSlashCommandSuggestion("Dopamin koçu başlat odaklanamıyorum")
        )

        val guidance = SharkSlashCommandRegistry.buildSlashRequiredGuidanceMessage(
            cmd = SharkSlashCommand.PLAN,
            originalText = "Sayısal için haftalık plan yap"
        )
        val (cleanDisplay, quickFill) = SharkSlashCommandRegistry.extractQuickFillPayload(guidance)
        assertFalse("Raw #SLASH_QUICKFILL tag must be stripped from display text", cleanDisplay.contains(SharkSlashCommandRegistry.SLASH_QUICKFILL_TAG))
        assertTrue(cleanDisplay.contains("/plan"))
        assertEquals("/plan Sayısal için haftalık plan yap", quickFill)
    }
}
