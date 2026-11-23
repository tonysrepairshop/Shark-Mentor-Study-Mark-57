package com.example.sharkmentor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import com.example.sharkmentor.core.coaching.BookCampManager
import com.example.sharkmentor.core.coaching.CoachingEngine
import com.example.sharkmentor.core.coaching.DailyGoalLedgerEntry
import com.example.sharkmentor.core.coaching.DiscoveredYouTubePlaylist
import com.example.sharkmentor.core.coaching.DossierAiChannel
import com.example.sharkmentor.core.coaching.DossierBookSnapshot
import com.example.sharkmentor.core.coaching.DossierTopicSnapshot
import com.example.sharkmentor.core.coaching.StudentDossier
import com.example.sharkmentor.core.coaching.StudentDossierManager
import com.example.sharkmentor.data.local.entity.ResourceEntity
import com.example.sharkmentor.data.local.entity.TopicGoalEntity
import com.example.sharkmentor.ui.coaching.buildBalancedMultiDayPlanSpecs
import com.example.sharkmentor.ui.components.WellnessReminderBus
import com.example.sharkmentor.ui.screens.notes.NoteRichTextHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Mark63CouncilFeaturesTest {

    // ── Pillar 1: Notes Page 2 Sentinel, Coalescing, List Continuation & Performance ──

    @Test
    fun noteRichText_preservesListContinuationAcrossPageBreak() {
        assertEquals(
            "• ",
            NoteRichTextHelper.extractTrailingListContinuationPrefix("• Madde 1", false, false, false)
        )
        assertEquals(
            "4. ",
            NoteRichTextHelper.extractTrailingListContinuationPrefix("1. A\n2. B\n3. C", false, false, false)
        )
        assertEquals(
            "[ ] ",
            NoteRichTextHelper.extractTrailingListContinuationPrefix("[✓] Tamamlandı", false, false, false)
        )
        assertEquals(
            "• ",
            NoteRichTextHelper.extractTrailingListContinuationPrefix("Düz satır", isBulletActive = true, isNumberedActive = false, isCheckboxActive = false)
        )
    }

    @Test
    fun noteRichText_coalesceSpansAndStripsPageSentinelCleanly() {
        val bold = SpanStyle(fontWeight = FontWeight.Bold)
        val fragmented = (0 until 500).map { i -> AnnotatedString.Range(bold, i, i + 1) }
        val coalesced = NoteRichTextHelper.coalesceSpans(fragmented, 500)
        assertEquals("500 adjacent 1-char bold spans should coalesce into 1 span", 1, coalesced.size)
        assertEquals(0, coalesced[0].start)
        assertEquals(500, coalesced[0].end)

        val page2Annotated = AnnotatedString(
            text = "${NoteRichTextHelper.PAGE_SENTINEL}Merhaba",
            spanStyles = listOf(AnnotatedString.Range(bold, 1, 8))
        )
        val tagged = NoteRichTextHelper.toTaggedString(page2Annotated)
        assertFalse("Tagged output must never contain invisible PAGE_SENTINEL", tagged.contains(NoteRichTextHelper.PAGE_SENTINEL))
        assertEquals("<b>Merhaba</b>", tagged)
    }

    @Test
    fun noteRichText_enterOnPage2BulletRespectsLeadingSentinel() {
        val page2Old = TextFieldValue(
            annotatedString = AnnotatedString("${NoteRichTextHelper.PAGE_SENTINEL}• İlk madde"),
            selection = TextRange(12)
        )
        val page2WithNewline = TextFieldValue(
            annotatedString = AnnotatedString("${NoteRichTextHelper.PAGE_SENTINEL}• İlk madde\n"),
            selection = TextRange(13)
        )
        val handled = NoteRichTextHelper.handleEnterOnListItem(page2Old, page2WithNewline)
        assertNotNull(handled)
        assertTrue(handled!!.text.startsWith("${NoteRichTextHelper.PAGE_SENTINEL}• İlk madde\n• "))
    }

    @Test
    fun noteRichText_toggleCheckboxOnPage2Line1RespectsLeadingSentinel() {
        val page2Checkbox = TextFieldValue(
            annotatedString = AnnotatedString("${NoteRichTextHelper.PAGE_SENTINEL}[ ] Görev 1"),
            selection = TextRange(6)
        )
        val toggled = NoteRichTextHelper.toggleCheckboxAtOffset(page2Checkbox, 6)
        assertTrue(toggled.text.startsWith("${NoteRichTextHelper.PAGE_SENTINEL}[✓] Görev 1"))
    }

    // ── Pillar 2: Student Dossier Privacy, Selective Injection, Sync/Edit Intents & High Hours ──

    @Test
    fun studentDossier_enforcesStrictSelectiveAiChannelsAndAnonymization() {
        val dossier = StudentDossier(
            isCompleted = true,
            consentAccepted = true,
            examType = "YKS 2026",
            studentGrade = "12. Sınıf",
            targetHeadline = "İTÜ Bilgisayar Mühendisliği • #2.500",
            weekdayGoalMinutes = 720,
            weekendGoalMinutes = 900,
            topics = listOf(DossierTopicSnapshot("AYT Matematik", "Türev", 1, 5)),
            books = listOf(DossierBookSnapshot("3D AYT Matematik", "AYT Matematik", 10, 40, "Test")),
            interviewAnswers = mapOf(
                "daily_hours" to "12s / 15s",
                "coaching_style" to "Disiplinli; bana test@example.com veya 05551234567 üzerinden ulaşma"
            )
        )

        assertTrue(StudentDossierManager.shouldAttachDossier(DossierAiChannel.COACHING_DIRECT, "", dossier))
        assertTrue(StudentDossierManager.shouldAttachDossier(DossierAiChannel.PLAN_ADD_AI, "", dossier))
        assertTrue(StudentDossierManager.shouldAttachDossier(DossierAiChannel.ZEN_COACH, "", dossier))
        assertTrue(StudentDossierManager.shouldAttachDossier(DossierAiChannel.AUTO_CHAT, "Haftalık çalışma programımı düzenler misin?", dossier))
        assertFalse(StudentDossierManager.shouldAttachDossier(DossierAiChannel.AUTO_CHAT, "Türkiye'nin başkenti neresidir?", dossier))
        assertFalse(StudentDossierManager.shouldAttachDossier(DossierAiChannel.OTHER_NON_COACHING, "Türev sorusunu çöz", dossier))

        val prompt = StudentDossierManager.buildAnonymizedDossierPrompt(dossier)
        assertFalse("Email must be stripped from anonymized prompt", prompt.contains("test@example.com"))
        assertFalse("Phone must be stripped from anonymized prompt", prompt.contains("05551234567"))
        assertTrue(prompt.contains("12 sa"))
        assertTrue(prompt.contains("15 sa"))
        assertTrue(prompt.contains("3D AYT Matematik"))
    }

    @Test
    fun studentDossier_parsesHighDailyHoursAndDetectsSyncAndEditCommands() {
        val parsed = StudentDossierManager.parseDailyHoursFromAnswer("Hafta içi 12 saat, hafta sonu 15 saat")
        assertNotNull(parsed)
        assertEquals(720, parsed!!.first)
        assertEquals(900, parsed.second)
        assertTrue(CoachingEngine.MAX_DAILY_GOAL_MINUTES >= 960)

        assertTrue(StudentDossierManager.isDossierSyncCommand("öğrenci dosyamı güncelle"))
        assertTrue(StudentDossierManager.isDossierSyncCommand("Lütfen dosyamı senkronize et"))
        assertFalse(StudentDossierManager.isDossierEditCommand("öğrenci dosyamı güncelle"))
        assertTrue(StudentDossierManager.isDossierEditCommand("öğrenci dosyama sabah erken çalışmayı ekle"))
    }

    @Test
    fun studentDossier_efficiencySuggestionRespectsStudentVeto() {
        val ledger = listOf(
            DailyGoalLedgerEntry("2026-11-16", 1, "Pazartesi", 360, 720),
            DailyGoalLedgerEntry("2026-11-17", 2, "Salı", 330, 720),
            DailyGoalLedgerEntry("2026-11-18", 3, "Çarşamba", 390, 720),
            DailyGoalLedgerEntry("2026-11-19", 4, "Perşembe", 120, 720)
        )
        val highDossier = StudentDossier(
            isCompleted = true,
            consentAccepted = true,
            weekdayGoalMinutes = 720,
            weekendGoalMinutes = 900,
            efficiencyTrialDeclined = false
        )
        val suggestion = StudentDossierManager.evaluateEfficiencySuggestion(highDossier, ledger, missedTaskCount = 5)
        assertNotNull("Should politely suggest 8-9h trial when high target + missed tasks occur", suggestion)

        val vetoedDossier = highDossier.copy(efficiencyTrialDeclined = true)
        assertNull("Must strictly respect student veto when declined", StudentDossierManager.evaluateEfficiencySuggestion(vetoedDossier, ledger, missedTaskCount = 5))
    }

    // ── Pillar 3: Balanced 7-Day Multi-Subject Flexible-Duration Plan Generation ──

    @Test
    fun balancedMultiDayPlan_respects12h15hSpreadAcrossAll7DaysWithZeroRigidClockTimes() {
        val books = listOf(
            ResourceEntity(id = 1, title = "3D AYT Matematik", subject = "AYT Matematik", totalUnits = 60),
            ResourceEntity(id = 2, title = "Ferrum AYT Kimya", subject = "AYT Kimya", totalUnits = 40),
            ResourceEntity(id = 3, title = "VIP AYT Fizik", subject = "AYT Fizik", totalUnits = 45)
        )
        val topics = listOf(
            TopicGoalEntity(id = 1, subject = "AYT Matematik", topic = "Fonksiyonlar", currentLevel = 1),
            TopicGoalEntity(id = 2, subject = "AYT Kimya", topic = "Modern Atom Teorisi", currentLevel = 2)
        )
        val specs = buildBalancedMultiDayPlanSpecs(
            weekdayMinutes = 720, // 12h
            weekendMinutes = 900, // 15h
            libraryResources = books,
            topicGoals = topics,
            examType = "YKS 2026"
        )

        val daysPresent = specs.map { it.dayOfWeek }.toSet()
        assertEquals("All 7 days (1..7) must be populated — never Sunday-only", (1..7).toSet(), daysPresent)

        val mondayTasks = specs.filter { it.dayOfWeek == 1 }
        val sundayTasks = specs.filter { it.dayOfWeek == 7 }
        assertEquals("12h weekday should schedule 8 flexible 90-min blocks", 8, mondayTasks.size)
        assertEquals("15h weekend should schedule 10 flexible 90-min blocks", 10, sundayTasks.size)

        val distinctSubjectsMonday = mondayTasks.map { it.subject }.toSet()
        assertTrue("Each day must rotate across multiple subjects, not 1 subject", distinctSubjectsMonday.size >= 3)

        val rigidClockRegex = Regex("""\d{1,2}:\d{2}\s*-\s*\d{1,2}:\d{2}""")
        assertTrue(
            "No coaching plan task may contain rigid clock ranges like 09:00 - 10:30",
            specs.none { rigidClockRegex.containsMatchIn(it.flexibleTimeLabel) }
        )
        assertTrue(specs.all { it.flexibleTimeLabel.startsWith("Esnek •") })
    }

    // ── Pillar 4: Hybrid AI Book Playlist Candidate Ranking ──

    @Test
    fun hybridBookDiscovery_ranksDirectBookMatchFirstAndOffersSubjectFallbackWhenNoDirect() {
        val directRaw = listOf(
            DiscoveredYouTubePlaylist("PL_DIRECT_1", "Ferrum AYT Kimya 2026 Video Ders Kampı", "Ferrum", 42),
            DiscoveredYouTubePlaylist("PL_INTRO", "Ferrum Tanıtım Shorts", "Ferrum", 2)
        )
        val subjectRaw = listOf(
            DiscoveredYouTubePlaylist("PL_SUBJ_1", "AYT Kimya Genel Tekrar Kampı", "Benim Hocam", 35)
        )
        val (hasDirect, ranked) = BookCampManager.rankHybridBookCandidates(
            resourceTitle = "Ferrum AYT Kimya Video Ders Defteri",
            subject = "AYT Kimya",
            directRaw = directRaw,
            subjectRaw = subjectRaw
        )
        assertTrue(hasDirect)
        assertTrue(ranked.isNotEmpty())
        assertEquals("PL_DIRECT_1", ranked.first().playlistId)
        assertTrue("Top candidate must be marked Önerilen (isRecommended)", ranked.first().isRecommended)
        assertTrue(ranked.first().isDirectBookMatch)

        // When book has NO direct playlist, must honestly report hasDirect=false AND offer subject camps
        val (hasDirect2, fallbackRanked) = BookCampManager.rankHybridBookCandidates(
            resourceTitle = "Bilinmeyen Özel Baskı Fasikül",
            subject = "TYT Matematik",
            directRaw = emptyList(),
            subjectRaw = listOf(
                DiscoveredYouTubePlaylist("PL_MAT_1", "TYT Matematik 49 Günde Kamp", "Rehber Matematik", 49),
                DiscoveredYouTubePlaylist("PL_MAT_2", "TYT Matematik Konu Anlatım Kampı", "Mert Hoca", 55)
            )
        )
        assertFalse(hasDirect2)
        assertEquals(2, fallbackRanked.size)
        assertTrue(fallbackRanked.first().isRecommended)
        assertFalse(fallbackRanked.first().isDirectBookMatch)
    }

    // ── Pillar 6: Smartwatch Posture Reminder 8 Rotating Micro-Exercises ──

    @Test
    fun wellnessReminderBus_rotatesThrough8DistinctPostureExercisesWithoutRepeats() {
        assertEquals(8, WellnessReminderBus.POSTURE_EXERCISES.size)
        val seenIds = mutableListOf<Int>()
        repeat(8) {
            seenIds.add(WellnessReminderBus.nextPostureExercise().id)
        }
        assertEquals("8 consecutive triggers must cover all 8 unique exercises", 8, seenIds.toSet().size)
        for (i in 1 until seenIds.size) {
            assertNotEquals("Consecutive posture exercises must never repeat back-to-back", seenIds[i - 1], seenIds[i])
        }
    }
}
