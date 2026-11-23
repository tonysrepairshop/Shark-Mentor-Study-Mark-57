package com.example.sharkmentor.core.coaching

import com.example.sharkmentor.data.local.entity.ErrorQuestionEntity
import com.example.sharkmentor.data.local.entity.ResourceEntity
import com.example.sharkmentor.data.local.entity.TopicGoalEntity
import com.example.sharkmentor.ui.coaching.buildBalancedMultiDayPlanSpecs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Mark 67 Comprehensive Unit Test Suite:
 * 1. Subject Book Coaching Roles, Priorities (#1/#2/#3), Passive exclusion & Video Opt-Out (`BookCoachingRoleManager`)
 * 2. 1-3-7 -> Monthly Spaced Repetition State Machine, Two-Way Error Bank Sync, Weekly->Monthly Rollup & Per-UID Isolation (`AiCoachLedgerManager`)
 * 3. YouTube Structural `ytInitialData` JSON Parser (including giant-thumbnail `lockupViewModel`), Strict Top-3 Relevance Cap & Real Playlist Absorption (`BookCampManager`)
 * 4. Multi-Role Balanced 7-Day Planner (`buildBalancedMultiDayPlanSpecs`): Concept -> Same-Week Practice -> 1-3-7 Revision + Error Bank, Passive Excluded
 */
class Mark67BookRolesRepetitionAndLedgerTest {

    @Test
    fun testBookCoachingRolesPriorityPassiveExclusionAndNaturalLanguageParsers() {
        val ferrum = ResourceEntity(id = 1L, title = "Ferrum AYT Kimya", subject = "AYT Kimya")
        val aydin = ResourceEntity(id = 2L, title = "Aydın AYT Kimya", subject = "AYT Kimya")
        val orbital = ResourceEntity(id = 3L, title = "Orbital AYT Kimya", subject = "AYT Kimya")
        val palme = ResourceEntity(id = 4L, title = "Palme AYT Kimya", subject = "AYT Kimya")
        val passiveExtra = ResourceEntity(id = 5L, title = "Eski Kimya Soru Bankası", subject = "AYT Kimya")

        val assignments = mapOf(
            1L to BookCoachingAssignment(1L, "AYT Kimya", BookCoachingRole.CONCEPT_VIDEO, priorityOrder = 1, videoDisabled = false),
            2L to BookCoachingAssignment(2L, "AYT Kimya", BookCoachingRole.PRIMARY_PRACTICE, priorityOrder = 1, videoDisabled = true),
            3L to BookCoachingAssignment(3L, "AYT Kimya", BookCoachingRole.REVISION_BOOK, priorityOrder = 1, videoDisabled = true),
            4L to BookCoachingAssignment(4L, "AYT Kimya", BookCoachingRole.REVISION_BOOK, priorityOrder = 2, videoDisabled = true),
            5L to BookCoachingAssignment(5L, "AYT Kimya", BookCoachingRole.PASSIVE, priorityOrder = 1, videoDisabled = true)
        )
        val allBooks = listOf(ferrum, aydin, orbital, palme, passiveExtra)

        // Passive book MUST be excluded from active subject books
        val activeChem = BookCoachingRoleManager.getActiveBooksForSubject("AYT Kimya", allBooks, assignments)
        assertEquals(4, activeChem.size)
        assertFalse(activeChem.any { it.first.id == 5L })

        // Concept, Primary Practice, and Revision priority orderings
        assertEquals(1L, BookCoachingRoleManager.getConceptBookForSubject("AYT Kimya", allBooks, assignments)?.first?.id)
        assertEquals(listOf(2L), BookCoachingRoleManager.getPrimaryPracticeBooksForSubject("AYT Kimya", allBooks, assignments).map { it.first.id })
        assertEquals(listOf(3L, 4L), BookCoachingRoleManager.getRevisionBooksForSubject("AYT Kimya", allBooks, assignments).map { it.first.id })

        // Video opt-out check
        assertFalse(BookCoachingRoleManager.isVideoDisabledFor(1L, assignments))
        assertTrue(BookCoachingRoleManager.isVideoDisabledFor(2L, assignments))
        assertTrue(BookCoachingRoleManager.isVideoDisabledFor(5L, assignments))

        // Natural language Turkish parsers
        assertEquals(BookCoachingRole.REVISION_BOOK, BookCoachingRoleManager.parseRoleFromNaturalLanguage("kimyanın 1. tekrar kitabı yap"))
        assertEquals(BookCoachingRole.PRIMARY_PRACTICE, BookCoachingRoleManager.parseRoleFromNaturalLanguage("2. ilerleme kaynağı olsun"))
        assertEquals(BookCoachingRole.CONCEPT_VIDEO, BookCoachingRoleManager.parseRoleFromNaturalLanguage("konu anlatım videosu"))
        assertEquals(BookCoachingRole.PASSIVE, BookCoachingRoleManager.parseRoleFromNaturalLanguage("pasif yap plana dahil etme"))
        assertEquals(1, BookCoachingRoleManager.parsePriorityFromNaturalLanguage("1. tekrar kaynağı"))
        assertEquals(2, BookCoachingRoleManager.parsePriorityFromNaturalLanguage("ikinci ilerleme kitabı"))
        assertEquals(3, BookCoachingRoleManager.parsePriorityFromNaturalLanguage("3. tekrar kaynağı"))

        // AI Prompt emits exact roles, priorities, and explicitly excludes passive books
        val prompt = BookCoachingRoleManager.buildAiBookRolesPrompt(allBooks, assignments)
        assertTrue(prompt.contains("Konu/Video: #1 Ferrum AYT Kimya"))
        assertTrue(prompt.contains("İlerleme/Soru: #1 Aydın AYT Kimya"))
        assertTrue(prompt.contains("Tekrar: #1 Orbital AYT Kimya, #2 Palme AYT Kimya"))
        assertTrue(prompt.contains("PASİF / PLANA DAHİL EDİLMEYECEK KİTAPLAR"))
        assertTrue(prompt.contains("Eski Kimya Soru Bankası"))

        // Gson round-trip & strict per-UID key isolation
        val roundTripped = BookCoachingRoleManager.parseAssignmentsJson(BookCoachingRoleManager.serializeAssignmentsJson(assignments))
        assertEquals(assignments.size, roundTripped.size)
        assertEquals(BookCoachingRole.REVISION_BOOK, roundTripped[3L]?.role)
        assertEquals(2, roundTripped[4L]?.priorityOrder)
        assertNotEquals(BookCoachingRoleManager.uidKeyFor("uid_student_A"), BookCoachingRoleManager.uidKeyFor("uid_student_B"))
        assertEquals("guest_local", BookCoachingRoleManager.uidKeyFor(null))
    }

    @Test
    fun testSpacedRepetition137ToMonthlyCycleErrorBankSyncAndMonthlyRollup() {
        val day0 = 20_000L
        var ledger = AiCoachLedger(uidKey = AiCoachLedgerManager.uidKeyFor("student_alpha"), firstActivatedEpochDay = day0)

        // 1. Learn concept on Day 0 -> CONCEPT_LEARNED + needsSameWeekPractice = true
        ledger = AiCoachLedgerManager.advanceTopicLifecycle(
            ledger = ledger,
            subject = "AYT Kimya",
            topic = "Modern Atom Teorisi",
            actionKind = TopicStudyActionKind.CONCEPT_STUDY,
            todayEpochDay = day0,
            bookTitle = "Ferrum AYT Kimya",
            completedVideoId = "dQw4w9WgXcQ"
        )
        val key = AiCoachLedgerManager.topicKey("AYT Kimya", "Modern Atom Teorisi")
        val afterConcept = ledger.topicRecords[key]!!
        assertEquals(TopicLearningStage.CONCEPT_LEARNED, afterConcept.stage)
        assertTrue(afterConcept.needsSameWeekPractice)
        assertTrue(AiCoachLedgerManager.isTopicAlreadyConceptLearned("AYT Kimya", "Modern Atom Teorisi", ledger))
        assertEquals(1, AiCoachLedgerManager.getTopicsNeedingSameWeekPractice(ledger).size)

        // 2. Solve reinforcement questions in same week (Day +2) -> enters 1-3-7 at step 0 (+1d = Day +3)
        ledger = AiCoachLedgerManager.advanceTopicLifecycle(
            ledger = ledger,
            subject = "AYT Kimya",
            topic = "Modern Atom Teorisi",
            actionKind = TopicStudyActionKind.PRIMARY_PRACTICE,
            todayEpochDay = day0 + 2,
            bookTitle = "Aydın AYT Kimya",
            masteryLevel = 3
        )
        val afterPractice = ledger.topicRecords[key]!!
        assertEquals(TopicLearningStage.REVIEW_1_3_7, afterPractice.stage)
        assertFalse(afterPractice.needsSameWeekPractice)
        assertEquals(1, afterPractice.repetitionCycle)
        assertEquals(0, afterPractice.repetitionStepIndex)
        assertEquals(day0 + 3, afterPractice.nextReviewEpochDay)

        // 3. Complete +1d review -> schedules +3d review (Day +3 + 3 = Day +6)
        ledger = AiCoachLedgerManager.advanceTopicLifecycle(
            ledger = ledger,
            subject = "AYT Kimya",
            topic = "Modern Atom Teorisi",
            actionKind = TopicStudyActionKind.REVIEW_SESSION,
            todayEpochDay = day0 + 3,
            bookTitle = "Orbital AYT Kimya",
            masteryLevel = 3,
            linkedErrorCount = 0
        )
        assertEquals(1, ledger.topicRecords[key]!!.repetitionStepIndex)
        assertEquals(day0 + 6, ledger.topicRecords[key]!!.nextReviewEpochDay)

        // 4. Complete +3d review -> schedules +7d review (Day +6 + 7 = Day +13)
        ledger = AiCoachLedgerManager.advanceTopicLifecycle(
            ledger = ledger,
            subject = "AYT Kimya",
            topic = "Modern Atom Teorisi",
            actionKind = TopicStudyActionKind.REVIEW_SESSION,
            todayEpochDay = day0 + 6,
            bookTitle = "Orbital AYT Kimya",
            masteryLevel = 3,
            linkedErrorCount = 0
        )
        assertEquals(2, ledger.topicRecords[key]!!.repetitionStepIndex)
        assertEquals(day0 + 13, ledger.topicRecords[key]!!.nextReviewEpochDay)

        // 5. Complete +7d review while mastery is STILL 3 (< 4) -> MUST REPEAT 1-3-7 (Cycle 2, +1d) rather than jumping to Monthly!
        ledger = AiCoachLedgerManager.advanceTopicLifecycle(
            ledger = ledger,
            subject = "AYT Kimya",
            topic = "Modern Atom Teorisi",
            actionKind = TopicStudyActionKind.REVIEW_SESSION,
            todayEpochDay = day0 + 13,
            bookTitle = "Orbital AYT Kimya",
            masteryLevel = 3,
            linkedErrorCount = 0
        )
        val cycle2Start = ledger.topicRecords[key]!!
        assertEquals(TopicLearningStage.REVIEW_1_3_7, cycle2Start.stage)
        assertEquals(2, cycle2Start.repetitionCycle)
        assertEquals(0, cycle2Start.repetitionStepIndex)
        assertEquals(day0 + 14, cycle2Start.nextReviewEpochDay)

        // 6. Complete Cycle 2 (+1d -> +3d -> +7d) with mastery = 4 and linkedErrorCount = 0 -> promotes to MONTHLY_MAINTENANCE (+30d)!
        ledger = AiCoachLedgerManager.advanceTopicLifecycle(ledger, "AYT Kimya", "Modern Atom Teorisi", TopicStudyActionKind.REVIEW_SESSION, day0 + 14, "Orbital", masteryLevel = 4, linkedErrorCount = 0)
        ledger = AiCoachLedgerManager.advanceTopicLifecycle(ledger, "AYT Kimya", "Modern Atom Teorisi", TopicStudyActionKind.REVIEW_SESSION, day0 + 17, "Orbital", masteryLevel = 4, linkedErrorCount = 0)
        ledger = AiCoachLedgerManager.advanceTopicLifecycle(ledger, "AYT Kimya", "Modern Atom Teorisi", TopicStudyActionKind.REVIEW_SESSION, day0 + 24, "Orbital", masteryLevel = 4, linkedErrorCount = 0)
        val mastered = ledger.topicRecords[key]!!
        assertEquals(TopicLearningStage.MONTHLY_MAINTENANCE, mastered.stage)
        assertEquals(day0 + 54, mastered.nextReviewEpochDay)

        // 7. Two-way Error Bank lapse pulls MONTHLY_MAINTENANCE topic right back into 1-3-7!
        val nowMillis = 1_750_000_000_000L
        val errorQuestions = listOf(
            ErrorQuestionEntity(id = 10, subject = "AYT Kimya", topic = "Modern Atom Teorisi", aiTopic = "Modern Atom Teorisi", questionImageUri = "u1", dueDateMillis = nowMillis - 1000L, state = 0),
            ErrorQuestionEntity(id = 11, subject = "AYT Kimya", topic = "Modern Atom Teorisi", aiTopic = "Modern Atom Teorisi", questionImageUri = "u2", dueDateMillis = nowMillis - 500L, state = 1)
        )
        ledger = AiCoachLedgerManager.mergeErrorBankCountsIntoLedger(ledger, errorQuestions, nowMillis = nowMillis, todayEpochDay = day0 + 30)
        val afterLapse = ledger.topicRecords[key]!!
        assertEquals(TopicLearningStage.REVIEW_1_3_7, afterLapse.stage)
        assertEquals(2, afterLapse.linkedErrorCount)
        assertTrue(afterLapse.nextReviewEpochDay in (day0 + 30)..(day0 + 31))

        // 8. Automatic Weekly -> Monthly archive compression (keeps rolling 4 weeks, rolls older weeks into permanent monthly archive)
        for (wkIdx in 1..6) {
            val monthKey = if (wkIdx <= 2) "2026-10" else "2026-11"
            ledger = AiCoachLedgerManager.appendWeeklyReportAndRollup(
                ledger = ledger,
                report = WeeklyCoachReport(
                    weekIsoKey = "2026-W${40 + wkIdx}",
                    monthIsoKey = monthKey,
                    startEpochDay = day0 + (wkIdx * 7L),
                    endEpochDay = day0 + (wkIdx * 7L) + 6L,
                    plannedCount = 20,
                    completedCount = 16,
                    carriedOverCount = 4,
                    newlyLearnedTopics = listOf("AYT Kimya: Konu $wkIdx"),
                    booksUsed = listOf("Ferrum AYT Kimya", "Aydın AYT Kimya")
                )
            )
        }
        assertEquals(4, ledger.weeklyReports.size)
        assertEquals("2026-W43", ledger.weeklyReports.first().weekIsoKey)
        assertEquals("2026-W46", ledger.weeklyReports.last().weekIsoKey)
        assertEquals(1, ledger.monthlyReports.size)
        assertEquals("2026-10", ledger.monthlyReports.first().monthIsoKey)
        assertEquals(2, ledger.monthlyReports.first().weeksAggregated)
        assertEquals(32, ledger.monthlyReports.first().totalCompleted)

        // 9. Prompt & Gson round-trip + UID isolation
        val prompt = AiCoachLedgerManager.buildAiLedgerPrompt(ledger, todayEpochDay = day0 + 30)
        assertTrue(prompt.contains("BU HAFTA VADESİ GELEN 1-3-7"))
        assertTrue(prompt.contains("Hata Bankası: 2 soru"))
        assertTrue(prompt.contains("AYLIK KOÇLUK ARŞİVİ"))
        assertTrue(prompt.contains("2026-10"))

        val roundTripped = AiCoachLedgerManager.parseLedgerJson(AiCoachLedgerManager.serializeLedgerJson(ledger))
        assertEquals(ledger.topicRecords.size, roundTripped.topicRecords.size)
        assertEquals(ledger.monthlyReports.size, roundTripped.monthlyReports.size)
        assertNotEquals(AiCoachLedgerManager.uidKeyFor("uid_1"), AiCoachLedgerManager.uidKeyFor("uid_2"))
    }

    @Test
    fun testYouTubeStructuralJsonParserTop3RelevanceFilterAndRealPlaylistAbsorption() {
        // 1. Modern lockupViewModel with a 5,000+ char nested thumbnail JSON blob (previously killed by regex!)
        val giantThumbPadding = (1..40).joinToString(",") {
            """{"url":"https://i.ytimg.com/vi/thumb$it/hqdefault.jpg?sqp=long_padding_blob_1234567890_abcdefghij","width":480,"height":360}"""
        }
        val fakeSearchHtml = """
            <html><script>var ytInitialData = {
              "contents": {
                "items": [
                  {
                    "lockupViewModel": {
                      "contentType": "LOCKUP_CONTENT_TYPE_PLAYLIST",
                      "contentId": "PLFerrumAytKimya2026",
                      "contentImage": {"collectionThumbnailViewModel": {"primaryThumbnail": {"sources": [$giantThumbPadding]}}},
                      "metadata": {
                        "lockupMetadataViewModel": {
                          "title": {"content": "Ferrum 2026 AYT Kimya Kampı (45 Video)"},
                          "metadata": {
                            "contentMetadataViewModel": {
                              "metadataRows": [{"metadataParts": [{"text": {"content": "Ferrum Kimya"}}]}]
                            }
                          }
                        }
                      }
                    }
                  },
                  {
                    "playlistRenderer": {
                      "playlistId": "PLGorkemAytKimya2026",
                      "title": {"simpleText": "AYT Kimya Video Ders Defteri Kampı"},
                      "shortBylineText": {"runs": [{"text": "Benim Hocam"}]},
                      "videoCount": "62"
                    }
                  }
                ]
              }
            };</script></html>
        """.trimIndent()

        val extractedPlaylists = BookCampManager.extractPlaylistsFromSearchHtml(fakeSearchHtml)
        assertEquals(2, extractedPlaylists.size)
        assertEquals("PLFerrumAytKimya2026", extractedPlaylists[0].playlistId)
        assertEquals("Ferrum Kimya", extractedPlaylists[0].channelName)
        assertEquals(45, extractedPlaylists[0].videoCount)
        assertEquals("PLGorkemAytKimya2026", extractedPlaylists[1].playlistId)

        // 2. Strict Top-3 relevance cap + disqualification of <3 videos, #shorts, wrong subject, and TYT-vs-AYT conflicts
        val noisyCandidates = listOf(
            DiscoveredYouTubePlaylist("PL_FERRUM_AYT_1", "Ferrum AYT Kimya Kampı 2026", "Ferrum Kimya", 45),
            DiscoveredYouTubePlaylist("PL_FERRUM_AYT_2", "Ferrum AYT Kimya Soru Çözümü", "Ferrum Kimya", 30),
            DiscoveredYouTubePlaylist("PL_KIMYA_AYT_3", "AYT Kimya Full Tekrar Kampı", "Kimya Adası", 22),
            DiscoveredYouTubePlaylist("PL_KIMYA_AYT_4", "AYT Kimya Konu Anlatım 2026", "Kimya Dersleri", 40),
            DiscoveredYouTubePlaylist("PL_WRONG_TYT_5", "Ferrum TYT Kimya Kampı", "Ferrum Kimya", 35),      // TYT conflict!
            DiscoveredYouTubePlaylist("PL_WRONG_MAT_6", "AYT Matematik Türev İntegral Kampı", "Mert Hoca", 50), // Subject conflict!
            DiscoveredYouTubePlaylist("PL_TINY_VID_7", "Ferrum AYT Kimya Tanıtım", "Ferrum Kimya", 2),       // < 3 videos!
            DiscoveredYouTubePlaylist("PL_SHORTS_8", "AYT Kimya #shorts özetler", "Kimya Shorts", 15)        // Shorts junk!
        )
        val rankedTop3 = BookCampManager.rankHybridBookCandidates(
            bookTitle = "Ferrum AYT Kimya",
            subject = "AYT Kimya",
            candidates = noisyCandidates
        )
        assertTrue("Must return at most 3 playlists", rankedTop3.size <= 3)
        assertTrue("Top match must be Ferrum AYT Kimya", rankedTop3.first().playlistId == "PL_FERRUM_AYT_1")
        assertFalse(rankedTop3.any { it.playlistId == "PL_WRONG_TYT_5" })
        assertFalse(rankedTop3.any { it.playlistId == "PL_WRONG_MAT_6" })
        assertFalse(rankedTop3.any { it.playlistId == "PL_TINY_VID_7" })
        assertFalse(rankedTop3.any { it.playlistId == "PL_SHORTS_8" })

        // 3. Real playlist HTML structural absorption: strips intros/promos, preserves real videoIds, maps canonical topics!
        val fakePlaylistHtml = """
            <html><script>var ytInitialData = {
              "contents": [
                {"playlistVideoRenderer": {"videoId": "intro123456", "title": {"runs": [{"text": "2026 AYT Kimya Kamp Tanıtım ve Kitap İnceleme"}]}, "lengthText": {"simpleText": "06:15"}}},
                {"playlistVideoRenderer": {"videoId": "promo123456", "title": {"runs": [{"text": "Kamp Duyuru ve Çekiliş #shorts"}]}, "lengthText": {"simpleText": "00:58"}}},
                {"playlistVideoRenderer": {"videoId": "vidAtom0001", "title": {"runs": [{"text": "1. Gün | Modern Atom Teorisi - Kuantum Sayıları | Ferrum"}]}, "lengthText": {"simpleText": "42:10"}}},
                {"playlistVideoRenderer": {"videoId": "vidGaz00002", "title": {"runs": [{"text": "2. Gün | Gazlar - İdeal Gaz Yasası | Ferrum"}]}, "lengthText": {"simpleText": "38:45"}}},
                {"playlistVideoRenderer": {"videoId": "vidDenge003", "title": {"runs": [{"text": "3. Gün | Kimyasal Tepkimelerde Denge Soru Çözümü"}]}, "lengthText": {"simpleText": "35:00"}}}
              ]
            };</script></html>
        """.trimIndent()
        val rawVideos = BookCampManager.extractPlaylistVideosFromHtml(fakePlaylistHtml)
        assertEquals(5, rawVideos.size)

        val episodes = BookCampManager.buildEpisodesFromRealPlaylistVideos(
            playlistId = "PLFerrumAytKimya2026",
            videos = rawVideos,
            subject = "AYT Kimya"
        )
        assertEquals(3, episodes.size) // Both intro + shorts promo cleanly stripped!
        assertEquals("vidAtom0001", episodes[0].videoId)
        assertEquals("Modern Atom Teorisi", episodes[0].mappedTopic)
        assertEquals("vidGaz00002", episodes[1].videoId)
        assertEquals("Gazlar", episodes[1].mappedTopic)
        assertEquals("vidDenge003", episodes[2].videoId)
        assertEquals("Kimyasal Tepkimelerde Denge", episodes[2].mappedTopic)
        assertTrue(episodes[2].isQuestionSolvingVideo)
    }

    @Test
    fun testBalancedMultiDayPlanSpecsEnforcesMultiRoleSameWeekPractice137ReviewsAndPassiveExclusion() {
        val today = 20_100L
        val ferrum = ResourceEntity(id = 1L, title = "Ferrum AYT Kimya", subject = "AYT Kimya")
        val aydin = ResourceEntity(id = 2L, title = "Aydın AYT Kimya", subject = "AYT Kimya")
        val orbital = ResourceEntity(id = 3L, title = "Orbital AYT Kimya", subject = "AYT Kimya")
        val passiveChem = ResourceEntity(id = 4L, title = "Kullanılmayan Pasif Kimya", subject = "AYT Kimya")
        val math3D = ResourceEntity(id = 5L, title = "3D AYT Matematik", subject = "AYT Matematik")

        val assignments = mapOf(
            1L to BookCoachingAssignment(1L, "AYT Kimya", BookCoachingRole.CONCEPT_VIDEO, priorityOrder = 1),
            2L to BookCoachingAssignment(2L, "AYT Kimya", BookCoachingRole.PRIMARY_PRACTICE, priorityOrder = 1, videoDisabled = true),
            3L to BookCoachingAssignment(3L, "AYT Kimya", BookCoachingRole.REVISION_BOOK, priorityOrder = 1, videoDisabled = true),
            4L to BookCoachingAssignment(4L, "AYT Kimya", BookCoachingRole.PASSIVE, priorityOrder = 1, videoDisabled = true),
            5L to BookCoachingAssignment(5L, "AYT Matematik", BookCoachingRole.PRIMARY_PRACTICE, priorityOrder = 1, videoDisabled = true)
        )

        // Ledger has "Modern Atom Teorisi" already learned & due TODAY for 1-3-7 review with 3 Error Bank mistakes!
        val reviewKey = AiCoachLedgerManager.topicKey("AYT Kimya", "Modern Atom Teorisi")
        val ledger = AiCoachLedger(
            topicRecords = mapOf(
                reviewKey to TopicLifecycleRecord(
                    key = reviewKey,
                    subject = "AYT Kimya",
                    topic = "Modern Atom Teorisi",
                    stage = TopicLearningStage.REVIEW_1_3_7,
                    conceptBookTitle = "Ferrum AYT Kimya",
                    practiceBookTitle = "Aydın AYT Kimya",
                    repetitionCycle = 1,
                    repetitionStepIndex = 1,
                    nextReviewEpochDay = today,
                    masterySnapshot = 3,
                    linkedErrorCount = 3
                )
            )
        )

        val specs = buildBalancedMultiDayPlanSpecs(
            weekdayMinutes = 360,
            weekendMinutes = 450,
            libraryResources = listOf(ferrum, aydin, orbital, passiveChem, math3D),
            topicGoals = listOf(
                TopicGoalEntity(id = 1, subject = "AYT Kimya", topic = "Gazlar", currentLevel = 1, targetLevel = 5),
                TopicGoalEntity(id = 2, subject = "AYT Matematik", topic = "Fonksiyonlar", currentLevel = 2, targetLevel = 5)
            ),
            examType = "YKS Sayısal",
            bookAssignments = assignments,
            coachLedger = ledger,
            todayEpochDay = today
        )

        // 1. Passive book MUST NEVER appear in the plan
        assertFalse("Passive book must be strictly excluded", specs.any { it.topicLabel.contains("Kullanılmayan Pasif Kimya") })

        // 2. Due 1-3-7 review MUST be scheduled with REVISION_BOOK (#1 Orbital) + Error Bank count!
        val reviewSpec = specs.firstOrNull {
            it.topicLabel.contains("Orbital AYT Kimya") &&
                it.topicLabel.contains("Modern Atom Teorisi") &&
                it.topicLabel.contains("1-3-7") &&
                it.topicLabel.contains("Hata Bankası: 3 soru")
        }
        assertTrue("Expected Orbital AYT Kimya 1-3-7 review with Error Bank count", reviewSpec != null)

        // 3. New AYT Kimya topic taught via CONCEPT_VIDEO (#1 Ferrum) MUST be paired in the SAME WEEK with PRIMARY_PRACTICE (#1 Aydın)!
        val conceptSpec = specs.firstOrNull {
            it.subject == "AYT Kimya" &&
                it.topicLabel.contains("Ferrum AYT Kimya") &&
                it.topicLabel.contains("[Konu Anlatım]")
        }
        assertTrue("Expected Ferrum AYT Kimya concept block", conceptSpec != null)
        val pairedPractice = specs.firstOrNull {
            it.subject == "AYT Kimya" &&
                it.topicLabel.contains("Aydın AYT Kimya") &&
                it.topicLabel.contains("[Soru Pekiştirme]")
        }
        assertTrue("Expected same-week Aydın AYT Kimya practice block paired with Ferrum concept", pairedPractice != null)
        assertTrue("Practice must follow concept within the same week", pairedPractice!!.dayOfWeek >= conceptSpec!!.dayOfWeek)

        // 4. Video concept book must NOT flood the entire week (capped to balanced count!)
        val totalFerrumCount = specs.count { it.topicLabel.contains("Ferrum AYT Kimya") }
        assertTrue("Concept video book must not hijack all 7 days (found $totalFerrumCount)", totalFerrumCount in 1..2)
    }
}
