package com.example.sharkmentor

import com.example.sharkmentor.core.coaching.StudentDossier
import com.example.sharkmentor.core.coaching.StudentDossierManager
import com.example.sharkmentor.core.data.AppThemeMode
import com.example.sharkmentor.core.data.ThemePalette
import com.example.sharkmentor.core.data.isCircadianNightHour
import com.example.sharkmentor.core.flashcard.FlashcardSourceType
import com.example.sharkmentor.core.flashcard.SmartFlashcard
import com.example.sharkmentor.core.flashcard.TopicPerformanceRecord
import com.example.sharkmentor.core.flashcard.computeFlashcardPriorityScore
import com.example.sharkmentor.core.flashcard.normalizeTopicKey
import com.example.sharkmentor.core.flashcard.orderDeckByPriority
import com.example.sharkmentor.core.flashcard.parseAiFlashcardsJson
import com.example.sharkmentor.core.league.resolveSharkCardArchetype
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Mark70ExperimentalFeaturesTest {

    // ── 1. Student Dossier Mock Exam & /hedef Analysis Detector ──────────────

    @Test
    fun dossier_includesMockExamQuestionAndInjectsMockSummaryIntoPrompt() {
        val mockQuestion = StudentDossierManager.INTERVIEW_QUESTIONS.firstOrNull {
            it.id == "recent_mock_exam"
        }
        assertNotNull("Initial interview must include recent_mock_exam question", mockQuestion)

        val dossier = StudentDossier(
            isCompleted = true,
            consentAccepted = true,
            examType = "YKS 2027",
            latestMockNetSummary = "TYT 85.25 / AYT 57.5",
            latestMockAnalysis = "Matematik süre sıkıştı, Fizik optik eksik"
        )
        val prompt = StudentDossierManager.buildAnonymizedDossierPrompt(dossier)
        assertTrue(prompt.contains("TYT 85.25 / AYT 57.5"))
        assertTrue(prompt.contains("Matematik süre sıkıştı"))
    }

    @Test
    fun dossier_detectsMockExamAnalysisExchangeInTargetMode() {
        assertTrue(
            StudentDossierManager.isMockExamAnalysisExchange(
                userMessage = "Son TYT denememde 81.5 net yaptım, Türkçe 31 doğru 7 yanlış geldi",
                aiResponse = "Bu net dağılımına göre Türkçe paragraf ve dil bilgisi analizi yapalım."
            )
        )
        assertFalse(
            StudentDossierManager.isMockExamAnalysisExchange(
                userMessage = "Merhaba nasılsın bugün hava güzel",
                aiResponse = "İyiyim teşekkür ederim, nasıl yardımcı olabilirim?"
            )
        )

        assertTrue(
            "Image/PDF mock exam attachment without numbers in user text must still be detected",
            StudentDossierManager.isMockExamAnalysisExchange(
                userMessage = "Bu denememi analiz eder misin?",
                aiResponse = "Görseldeki TYT karne sonucuna göre toplam 79.25 net görünüyor. Matematik eksik.",
                hasAttachment = true
            )
        )

        val (nets, summary) = StudentDossierManager.extractCompactMockExamSummary(
            userMessage = "TYT 84 net AYT 55 net yaptım, test@mail.com",
            aiResponse = "# Analiz\nMatematik hız kazanımı öncelikli olmalı.\nFizik dalgalar tekrar edilmeli."
        )
        assertFalse("PII email must be stripped", nets.contains("test@mail.com"))
        assertTrue(nets.contains("TYT 84"))
        assertTrue(summary.contains("Matematik hız kazanımı"))

        val (attachNets, _) = StudentDossierManager.extractCompactMockExamSummary(
            userMessage = "Denememi incele",
            aiResponse = "TYT toplam 83.75 net • Türkçe 33.5 net\nFen bilimleri optik ünitesi öncelikli tekrar edilmeli."
        )
        assertTrue(attachNets.contains("83.75"))
    }

    // ── 2. Smart Flashcards Priority Engine & AI JSON Parser ─────────────────

    @Test
    fun flashcards_parsesAiJsonAndMaintainsStableIds() {
        val raw = """
            ```json
            [
              {"subject": "Fizik", "topic": "Optik", "front": "Tam yansıma şartı nedir?", "back": "Çok kırıcıdan az kırıcıya sınır açısından büyük açıyla gelmesi."},
              {"subject": "Kimya", "topic": "Denge", "front": "Kp ile Kc ilişkisi?", "back": "Kp = Kc * (RT)^delta_n"}
            ]
            ```
        """.trimIndent()

        val firstPass = parseAiFlashcardsJson(
            rawJson = raw,
            sourceType = FlashcardSourceType.NOTE,
            sourceId = "note_42",
            sourceTitle = "Fizik Kimya Özeti"
        )
        val secondPass = parseAiFlashcardsJson(
            rawJson = raw,
            sourceType = FlashcardSourceType.NOTE,
            sourceId = "note_42",
            sourceTitle = "Fizik Kimya Özeti"
        )
        assertEquals(2, firstPass.size)
        assertEquals(firstPass[0].id, secondPass[0].id)
        assertEquals("Optik", firstPass[0].topic)
    }

    @Test
    fun flashcards_ordersMissedBeforeUnseenBeforeMasteredAndBoostsWeakTopics() {
        val now = 1_800_000_000_000L
        val mastered = SmartFlashcard(
            id = "c_mastered",
            sourceType = FlashcardSourceType.NOTE,
            sourceId = "n1",
            sourceTitle = "Türev",
            subject = "Matematik",
            topic = "Türev Alma",
            front = "Soru 1",
            back = "Cevap 1",
            knownCount = 3,
            missedCount = 0,
            lastOutcomeKnown = true,
            lastReviewedAtMillis = now - 60_000L
        )
        val unseen = SmartFlashcard(
            id = "c_unseen",
            sourceType = FlashcardSourceType.PDF,
            sourceId = "p1",
            sourceTitle = "İntegral",
            subject = "Matematik",
            topic = "Belirli İntegral",
            front = "Soru 2",
            back = "Cevap 2"
        )
        val missed = SmartFlashcard(
            id = "c_missed",
            sourceType = FlashcardSourceType.ERROR_BANK,
            sourceId = "Matematik",
            sourceTitle = "Hata Bankası",
            subject = "Matematik",
            topic = "Limit Süreklilik",
            front = "Soru 3",
            back = "Cevap 3",
            knownCount = 0,
            missedCount = 2,
            lastOutcomeKnown = false,
            lastReviewedAtMillis = now - 30_000L
        )

        val ordered = orderDeckByPriority(listOf(mastered, unseen, missed), emptyMap(), now)
        assertEquals(listOf("c_missed", "c_unseen", "c_mastered"), ordered.map { it.id })

        // Weak topic boost increases priority of unseen card in that weak topic
        val weakKey = normalizeTopicKey("Matematik", "Belirli İntegral")
        val topicStats = mapOf(
            weakKey to TopicPerformanceRecord(
                topicKey = weakKey,
                knownTotal = 0,
                missedTotal = 3,
                lastKnown = false
            )
        )
        val baseScore = computeFlashcardPriorityScore(unseen, emptyMap(), now)
        val boostedScore = computeFlashcardPriorityScore(unseen, topicStats, now)
        assertTrue("Weak topic history must boost priority score", boostedScore > baseScore)
    }

    // ── 3. Shark Card Archetypes (Minimalist, Zero Emojis) ───────────────────

    @Test
    fun sharkCard_resolvesCleanArchetypesWithoutEmojis() {
        assertEquals("Megalodon Disiplini", resolveSharkCardArchetype(1900, 15, 10, 5))
        assertEquals("Seri Muhafızı", resolveSharkCardArchetype(400, 25, 4, 2))
        assertEquals("Derin Odak", resolveSharkCardArchetype(1300, 4, 3, 2))
        assertEquals("Hata Analisti", resolveSharkCardArchetype(200, 3, 2, 30))
        assertEquals("Konu Ustası", resolveSharkCardArchetype(200, 3, 18, 4))
        assertEquals("Yükselen Yüzgeç", resolveSharkCardArchetype(45, 1, 1, 0))
    }

    // ── 4. English-Named Functional Themes & Circadian Cycle ─────────────────

    @Test
    fun themes_paperObsidianAndCircadianCycleBehaveDeterministically() {
        assertEquals("Paper", ThemePalette.PAPER.title)
        assertEquals("Obsidian", ThemePalette.OBSIDIAN.title)
        assertEquals(0xFF000000L, ThemePalette.OBSIDIAN.bgHex)
        assertTrue(AppThemeMode.entries.contains(AppThemeMode.CIRCADIAN))

        assertTrue(isCircadianNightHour(20))
        assertTrue(isCircadianNightHour(23))
        assertTrue(isCircadianNightHour(2))
        assertFalse(isCircadianNightHour(7))
        assertFalse(isCircadianNightHour(14))
        assertFalse(isCircadianNightHour(19))
    }
}
