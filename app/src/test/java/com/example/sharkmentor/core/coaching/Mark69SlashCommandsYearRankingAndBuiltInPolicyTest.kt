package com.example.sharkmentor.core.coaching

import com.example.sharkmentor.data.local.entity.TopicGoalEntity
import com.example.sharkmentor.data.sync.RemoteTopicRecord
import com.example.sharkmentor.data.sync.TopicCloudSyncManager
import com.example.sharkmentor.ui.aichat.SharkSlashCommand
import com.example.sharkmentor.ui.aichat.SharkSlashCommandRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
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

    @Test
    fun testTopicCloudSyncStableDocIdIsDeterministicAcrossReinstalls() {
        val id1 = TopicCloudSyncManager.buildStableTopicDocId("uid_42", "AYT Kimya", "Modern Atom Teorisi")
        val id2 = TopicCloudSyncManager.buildStableTopicDocId("uid_42", "  ayt   kimya ", "modern atom teorisi!")
        val idDifferent = TopicCloudSyncManager.buildStableTopicDocId("uid_42", "AYT Kimya", "Gazlar")

        assertEquals(id1, id2)
        assertNotEquals(id1, idDifferent)
        assertTrue(id1.startsWith("uid_42_t_"))
        assertFalse("Doc ID must never contain Firestore path separators", id1.contains("/"))
    }

    @Test
    fun testTopicCloudSyncMergesSubcollectionAndLegacyRootDocsAndRestoresOnFreshReinstall() {
        // Fresh reinstall: local Room database is completely empty!
        val uid = "uid_student_1"
        val stableModernAtomId = TopicCloudSyncManager.buildStableTopicDocId(uid, "AYT Kimya", "Modern Atom Teorisi")
        val remoteRecords = listOf(
            // Old stale deleted doc in subcollection (previously caused pullRemoteTopics to skip root & wipe!)
            RemoteTopicRecord(
                docId = "${uid}_99",
                localId = 99L,
                subject = "TYT Fizik",
                topic = "Eski Silinmiş Konu",
                currentLevel = 1,
                targetLevel = 5,
                notes = "",
                isDeleted = true,
                updatedAtMillis = 1_000L,
                fromSubcollection = true
            ),
            // Legacy root doc with initial mastery = 1
            RemoteTopicRecord(
                docId = "${uid}_1",
                localId = 1L,
                subject = "AYT Kimya",
                topic = "Modern Atom Teorisi",
                currentLevel = 1,
                targetLevel = 5,
                notes = "",
                isDeleted = false,
                updatedAtMillis = 2_000L,
                fromSubcollection = false
            ),
            // Newer subcollection stable doc with updated user rating = 4
            RemoteTopicRecord(
                docId = stableModernAtomId,
                localId = 1L,
                subject = "AYT Kimya",
                topic = "Modern Atom Teorisi",
                currentLevel = 4,
                targetLevel = 5,
                notes = "Kuantum sayıları tamam",
                isDeleted = false,
                updatedAtMillis = 5_000L,
                fromSubcollection = true
            ),
            // Legacy root-only topic that NEVER existed in subcollection
            RemoteTopicRecord(
                docId = "${uid}_2",
                localId = 2L,
                subject = "TYT Matematik",
                topic = "Temel Kavramlar",
                currentLevel = 3,
                targetLevel = 5,
                notes = "",
                isDeleted = false,
                updatedAtMillis = 3_000L,
                fromSubcollection = false
            )
        )

        val merged = TopicCloudSyncManager.mergeRemoteTopicRecords(remoteRecords)
        val plan = TopicCloudSyncManager.reconcileMergedTopicsWithLocal(
            localTopics = emptyList(),
            mergedRemote = merged,
            canSafelyPruneSyncedOrphans = true
        )

        assertTrue(plan.toUpdate.isEmpty())
        assertTrue(plan.toDeleteIds.isEmpty())
        assertEquals("Both active topics must restore cleanly without duplicates", 2, plan.toInsert.size)

        val restoredChem = plan.toInsert.first { it.topic == "Modern Atom Teorisi" }
        assertEquals("AYT Kimya", restoredChem.subject)
        assertEquals(4, restoredChem.currentLevel)
        assertEquals("Kuantum sayıları tamam", restoredChem.notes)
        assertTrue(restoredChem.isSynced)

        val restoredMath = plan.toInsert.first { it.topic == "Temel Kavramlar" }
        assertEquals("TYT Matematik", restoredMath.subject)
        assertEquals(3, restoredMath.currentLevel)
        assertTrue(restoredMath.isSynced)
    }

    @Test
    fun testTopicCloudSyncPreservesNewerUnsyncedLocalEditAndRespectsDeletions() {
        val localTopics = listOf(
            // User just rated this to 5 locally offline (unsynced, newer timestamp)
            TopicGoalEntity(
                id = 10L,
                subject = "AYT Matematik",
                topic = "Türev",
                currentLevel = 5,
                targetLevel = 5,
                isSynced = false,
                isDeleted = false,
                updatedAtMillis = 9_000L
            ),
            // Synced topic updated remotely on another device
            TopicGoalEntity(
                id = 11L,
                subject = "AYT Matematik",
                topic = "İntegral",
                currentLevel = 1,
                targetLevel = 5,
                isSynced = true,
                isDeleted = false,
                updatedAtMillis = 1_000L
            ),
            // Synced topic deleted remotely
            TopicGoalEntity(
                id = 12L,
                subject = "AYT Fizik",
                topic = "Vektörler",
                currentLevel = 2,
                targetLevel = 5,
                isSynced = true,
                isDeleted = false,
                updatedAtMillis = 1_000L
            ),
            // Locally deleted offline (pending upload) -> must NOT be resurrected by older active cloud doc
            TopicGoalEntity(
                id = 13L,
                subject = "TYT Kimya",
                topic = "Simya",
                currentLevel = 2,
                targetLevel = 5,
                isSynced = false,
                isDeleted = true,
                updatedAtMillis = 8_000L
            )
        )

        val remote = listOf(
            RemoteTopicRecord("uid_t_1", 10L, "AYT Matematik", "Türev", 2, 5, "", false, 4_000L, true),
            RemoteTopicRecord("uid_t_2", 11L, "AYT Matematik", "İntegral", 4, 5, "", false, 6_000L, true),
            RemoteTopicRecord("uid_t_3", 12L, "AYT Fizik", "Vektörler", 2, 5, "", true, 7_000L, true),
            RemoteTopicRecord("uid_t_4", 13L, "TYT Kimya", "Simya", 2, 5, "", false, 2_000L, true)
        )

        val plan = TopicCloudSyncManager.reconcileMergedTopicsWithLocal(
            localTopics = localTopics,
            mergedRemote = remote,
            canSafelyPruneSyncedOrphans = false
        )

        assertTrue(plan.toInsert.isEmpty())
        assertEquals("Only İntegral should be updated from remote", 1, plan.toUpdate.size)
        assertEquals(11L, plan.toUpdate.first().id)
        assertEquals(4, plan.toUpdate.first().currentLevel)
        assertEquals("Only remotely deleted Vektörler should be deleted locally", listOf(12L), plan.toDeleteIds)
    }

    @Test
    fun testTopicCloudSyncSingleDocSnapshotCompactnessHashGuardAndRoundTrip() {
        val sampleTopics = (1..150).map { i ->
            TopicGoalEntity(
                id = i.toLong(),
                subject = if (i % 2 == 0) "AYT Matematik" else "AYT Kimya",
                topic = "Konu $i - Detaylı Başlık",
                currentLevel = i % 6,
                targetLevel = 5,
                notes = if (i == 7) "Önemli not" else "",
                isSynced = false,
                isDeleted = false,
                updatedAtMillis = 1_700_000_000_000L + i
            )
        }

        val (jsonForward, hashForward) = TopicCloudSyncManager.serializeActiveTopicsSnapshotJson(sampleTopics)
        val (_, hashReversed) = TopicCloudSyncManager.serializeActiveTopicsSnapshotJson(sampleTopics.reversed())

        // Order-independent SHA-256 ensures unchanged state costs 0 Firestore writes
        assertEquals("SHA-256 must be order-independent for skip-unchanged quota guard", hashForward, hashReversed)
        // Entire 150-topic curriculum must fit easily inside 1 single Firestore doc (< 25 KB vs 1,000 KB limit)
        assertTrue(
            "150 topics should pack well under 25 KB (was ${jsonForward.toByteArray().size} bytes)",
            jsonForward.toByteArray(Charsets.UTF_8).size < 25_000
        )

        // Exact round-trip parse from single snapshot doc restores all 150 topics with ratings & notes
        val parsed = TopicCloudSyncManager.parseSnapshotTopicsJson("uid_quota_test", jsonForward, 1_700_000_999_000L)
        assertEquals(150, parsed.size)
        val seventh = parsed.first { it.topic == "Konu 7 - Detaylı Başlık" }
        assertEquals("AYT Kimya", seventh.subject)
        assertEquals(1, seventh.currentLevel)
        assertEquals("Önemli not", seventh.notes)

        // Changing even 1 mastery star changes the SHA-256 hash so the 1-doc write triggers cleanly
        val modified = sampleTopics.map { if (it.id == 7L) it.copy(currentLevel = 4) else it }
        val (_, hashModified) = TopicCloudSyncManager.serializeActiveTopicsSnapshotJson(modified)
        assertNotEquals("Changing a rating must change SHA-256 hash", hashForward, hashModified)
    }
}
