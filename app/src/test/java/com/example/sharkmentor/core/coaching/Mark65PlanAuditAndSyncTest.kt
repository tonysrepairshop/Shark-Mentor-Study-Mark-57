package com.example.sharkmentor.core.coaching

import com.example.sharkmentor.data.local.entity.PlanEntity
import com.example.sharkmentor.data.local.entity.ResourceEntity
import com.example.sharkmentor.data.local.entity.TopicGoalEntity
import com.example.sharkmentor.data.repository.PlanRepositoryImpl
import com.example.sharkmentor.ui.coaching.buildBalancedMultiDayPlanSpecs
import com.example.sharkmentor.worker.WeeklyMasteryCheckInWorker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Mark65PlanAuditAndSyncTest {

    @Test
    fun testPlanProgressAuditorSplitsCompletedAndIncompleteTasksAccurately() {
        val tasks = (1..10).map { idx ->
            PlanEntity(
                id = "task_$idx",
                folderId = "folder_w1",
                title = "Konu $idx",
                subject = if (idx % 2 == 0) "AYT Matematik" else "AYT Fizik",
                topic = "Konu $idx",
                dayOfWeek = ((idx - 1) % 5) + 1,
                scheduledTime = "Esnek • 90 dk",
                targetQuestions = 35,
                isCompleted = idx <= 5,
                orderIndex = idx
            )
        }

        val audit = PlanProgressAuditor.auditPlanEntities(
            folderTitle = "Haftalık Plan",
            tasks = tasks,
            todayIsoDay = 5
        )

        assertTrue(audit.hasAnyTasks)
        assertEquals(10, audit.totalTasks)
        assertEquals(5, audit.completedCount)
        assertEquals(5, audit.incompleteCount)
        assertEquals(50, audit.completionPercent)
        assertTrue(audit.incompleteTasks.all { !it.isCompleted && it.isOverdueOrDueToday })
        assertTrue(audit.completedTasks.all { it.isCompleted })
    }

    @Test
    fun testBuildAiPlanAuditPromptContainsCarryOverAndNonRepetitionRules() {
        val audit = PlanAuditSnapshot(
            folderTitle = "Koçluk Haftası",
            totalTasks = 4,
            completedTasks = listOf(
                PlanTaskAuditItem("1", 1, "AYT Matematik", "Fonksiyonlar", "Esnek • 90 dk", 40, isCompleted = true),
                PlanTaskAuditItem("2", 1, "AYT Kimya", "Modern Atom", "Esnek • 90 dk", 35, isCompleted = true)
            ),
            incompleteTasks = listOf(
                PlanTaskAuditItem("3", 2, "AYT Matematik", "Limit ve Süreklilik", "Esnek • 90 dk", 40, isCompleted = false, isOverdueOrDueToday = true),
                PlanTaskAuditItem("4", 3, "AYT Fizik", "Tork ve Denge", "Esnek • 90 dk", 35, isCompleted = false, isOverdueOrDueToday = true)
            )
        )

        val prompt = PlanProgressAuditor.buildAiPlanAuditPrompt(audit)
        assertTrue(prompt.contains("MEVCUT PLAN & GÖREV DENETİMİ"))
        assertTrue(prompt.contains("EKSİK / TAMAMLANMAYAN GÖREVLER (2 adet"))
        assertTrue(prompt.contains("Limit ve Süreklilik"))
        assertTrue(prompt.contains("Tork ve Denge"))
        assertTrue(prompt.contains("TAMAMLANAN GÖREVLER (2 adet"))
        assertTrue(prompt.contains("Fonksiyonlar"))
        assertTrue(prompt.contains("Modern Atom"))
        assertTrue(prompt.contains("KRİTİK KÜMÜLATİF TEMEL & TEKRARSIZLIK KURALI"))
    }

    @Test
    fun testBalancedPlanCarriesOverIncompleteFirstAndSkipsStrongCompletedTopics() {
        val completed = listOf(
            PlanTaskAuditItem("c1", 1, "AYT Matematik", "Fonksiyonlar", "Esnek • 90 dk", 40, isCompleted = true),
            PlanTaskAuditItem("c2", 1, "AYT Kimya", "Gazlar", "Esnek • 90 dk", 35, isCompleted = true)
        )
        val incomplete = listOf(
            PlanTaskAuditItem("i1", 2, "AYT Matematik", "Limit ve Süreklilik", "Esnek • 90 dk", 40, isCompleted = false, isOverdueOrDueToday = true),
            PlanTaskAuditItem("i2", 3, "AYT Fizik", "Çembersel Hareket", "Esnek • 90 dk", 35, isCompleted = false, isOverdueOrDueToday = true)
        )
        val topics = listOf(
            TopicGoalEntity(id = 1, subject = "AYT Matematik", topic = "Fonksiyonlar", currentLevel = 4, targetLevel = 5), // strong completed -> MUST skip
            TopicGoalEntity(id = 2, subject = "AYT Kimya", topic = "Gazlar", currentLevel = 2, targetLevel = 5),          // weak completed (<=2) -> reinforcement allowed
            TopicGoalEntity(id = 3, subject = "AYT Matematik", topic = "Türev", currentLevel = 2, targetLevel = 5),
            TopicGoalEntity(id = 4, subject = "AYT Biyoloji", topic = "Sinir Sistemi", currentLevel = 1, targetLevel = 5)
        )
        val books = listOf(
            ResourceEntity(id = 1, title = "3D AYT Matematik", subject = "AYT Matematik", totalUnits = 300, completedUnits = 90),
            ResourceEntity(id = 2, title = "Aydın AYT Kimya", subject = "AYT Kimya", totalUnits = 240, completedUnits = 80),
            ResourceEntity(id = 3, title = "Palme AYT Fizik", subject = "AYT Fizik", totalUnits = 260, completedUnits = 60)
        )

        assertTrue(
            PlanProgressAuditor.isTopicAlreadyCompletedInPlan("AYT Matematik", "Fonksiyonlar", completed, topics)
        )
        assertFalse(
            PlanProgressAuditor.isTopicAlreadyCompletedInPlan("AYT Kimya", "Gazlar", completed, topics)
        )

        val specs = buildBalancedMultiDayPlanSpecs(
            weekdayMinutes = 360,
            weekendMinutes = 450,
            libraryResources = books,
            topicGoals = topics,
            examType = "YKS Sayısal",
            completedTasks = completed,
            incompleteCarryOverTasks = incomplete,
            startDayIso = 1
        )

        // All 7 days must be populated
        assertEquals((1..7).toSet(), specs.map { it.dayOfWeek }.toSet())

        // Both incomplete carry-over tasks MUST be scheduled first with isCarryOver = true
        val carryOvers = specs.filter { it.isCarryOver }
        assertEquals(2, carryOvers.size)
        assertTrue(carryOvers.any { it.topicLabel.contains("Limit ve Süreklilik") })
        assertTrue(carryOvers.any { it.topicLabel.contains("Çembersel Hareket") })
        assertTrue(carryOvers.all { it.dayOfWeek <= 3 })

        // Strong completed topic ("Fonksiyonlar", mastery 4/5) MUST NOT be repeated
        assertFalse(specs.any { it.topicLabel.contains("Fonksiyonlar", ignoreCase = true) })

        // Zero rigid clock times ("09:00 - 10:30"); every block uses flexible duration
        val clockRegex = Regex("""\d{1,2}:\d{2}""")
        assertTrue(specs.all { it.flexibleTimeLabel.startsWith("Esnek •") && !clockRegex.containsMatchIn(it.flexibleTimeLabel) })
    }

    @Test
    fun testOfficialSundayWeeklyCheckInNotificationDecisionRule() {
        val sundayIso = "2026-11-22" // Official ISO day 7 (Sunday / Pazar)

        // Fires on Sunday 10:00..21:59 when not yet sent or completed today
        assertTrue(WeeklyMasteryCheckInWorker.shouldSendSundayCheckIn(7, 10, sundayIso, null, null))
        assertTrue(WeeklyMasteryCheckInWorker.shouldSendSundayCheckIn(7, 18, sundayIso, "2026-11-15", "2026-11-15"))
        assertTrue(WeeklyMasteryCheckInWorker.shouldSendSundayCheckIn(7, 21, sundayIso, null, null))

        // Never fires on Monday..Saturday (ISO 1..6)
        for (day in 1..6) {
            assertFalse(WeeklyMasteryCheckInWorker.shouldSendSundayCheckIn(day, 14, "2026-11-21", null, null))
        }

        // Respects waking hours window (not 08:00, not 22:00)
        assertFalse(WeeklyMasteryCheckInWorker.shouldSendSundayCheckIn(7, 8, sundayIso, null, null))
        assertFalse(WeeklyMasteryCheckInWorker.shouldSendSundayCheckIn(7, 22, sundayIso, null, null))

        // Deduplicates once per Sunday & suppresses if student already checked in today
        assertFalse(WeeklyMasteryCheckInWorker.shouldSendSundayCheckIn(7, 15, sundayIso, sundayIso, null))
        assertFalse(WeeklyMasteryCheckInWorker.shouldSendSundayCheckIn(7, 15, sundayIso, null, sundayIso))

        val (title, body) = WeeklyMasteryCheckInWorker.buildSundayNotificationCopy(
            PlanAuditSnapshot(
                folderTitle = "Hafta 1",
                totalTasks = 10,
                completedTasks = List(5) { PlanTaskAuditItem("c$it", 1, "Mat", "K$it", "Esnek • 90 dk", 30, true) },
                incompleteTasks = List(5) { PlanTaskAuditItem("i$it", 2, "Fiz", "E$it", "Esnek • 90 dk", 30, false) }
            )
        )
        assertTrue(title.contains("Konu"))
        assertTrue(body.contains("Pazar"))
        assertTrue(body.contains("5/10"))
        assertTrue(body.contains("5 eksik görev"))
    }

    @Test
    fun testSharkAiPlanRebuildIntentDetection() {
        // Positive plan rebuild/creation prompts (any day of the week)
        assertTrue(PlanProgressAuditor.isPlanRebuildOrCreateIntent("Planımı baştan kurgular mısın?"))
        assertTrue(PlanProgressAuditor.isPlanRebuildOrCreateIntent("Bu hafta için yeni çalışma programı hazırla"))
        assertTrue(PlanProgressAuditor.isPlanRebuildOrCreateIntent("Haftalık planımı güncelle"))
        assertTrue(PlanProgressAuditor.isPlanRebuildOrCreateIntent("Kalan eksiklerime göre program oluştur"))
        assertTrue(PlanProgressAuditor.isPlanRebuildOrCreateIntent("Yeni plan yap"))

        // Negative prompts that must NOT trigger the plan check-in modal
        assertFalse(PlanProgressAuditor.isPlanRebuildOrCreateIntent("Türev sorusunu adım adım çöz"))
        assertFalse(PlanProgressAuditor.isPlanRebuildOrCreateIntent("3D Matematik kitabını 45. sayfaya kadar çözdüm işaretle"))
        assertFalse(PlanProgressAuditor.isPlanRebuildOrCreateIntent("Öğrenci dosyamı güncelle"))
        assertFalse(PlanProgressAuditor.isPlanRebuildOrCreateIntent("Merhaba nasılsın"))
        assertFalse(PlanProgressAuditor.isPlanRebuildOrCreateIntent("Okul ders programımı incele"))
    }

    @Test
    fun testStudentDossierJsonRoundTripPreservesPlanTasksAndEmitsPromptAudit() {
        val dossier = StudentDossier(
            consentAccepted = true,
            isCompleted = true,
            examType = "YKS Sayısal",
            weekdayGoalMinutes = 480,
            weekendGoalMinutes = 540,
            activePlanTitle = "Hafta 4 Planı",
            completedPlanTasks = listOf(
                DossierPlanTaskSnapshot(1, "AYT Matematik", "Polinomlar", 40, isCompleted = true)
            ),
            incompletePlanTasks = listOf(
                DossierPlanTaskSnapshot(2, "AYT Fizik", "Basit Harmonik Hareket", 35, isCompleted = false)
            )
        )

        val serialized = StudentDossierManager.serializeDossierToJson(dossier)
        val restored = StudentDossierManager.parseJsonToDossier(serialized)

        assertEquals("Hafta 4 Planı", restored.activePlanTitle)
        assertEquals(1, restored.completedPlanTasks.size)
        assertEquals("Polinomlar", restored.completedPlanTasks.first().topic)
        assertEquals(1, restored.incompletePlanTasks.size)
        assertEquals("Basit Harmonik Hareket", restored.incompletePlanTasks.first().topic)

        val prompt = StudentDossierManager.buildAnonymizedDossierPrompt(restored)
        assertTrue(prompt.contains("MEVCUT PLAN & GÖREV DENETİMİ"))
        assertTrue(prompt.contains("Basit Harmonik Hareket"))
        assertTrue(prompt.contains("Polinomlar"))
    }

    @Test
    fun testPlanRepositoryDistinctKeyPreservesSameDaySameSubjectDifferentTopicsWithFlexibleSlot() {
        val key1 = PlanRepositoryImpl.buildPlanDistinctKey(
            dayOfWeek = 1,
            scheduledTime = "Esnek • 90 dk",
            subject = "AYT Matematik",
            topic = "Limit ve Süreklilik"
        )
        val key2 = PlanRepositoryImpl.buildPlanDistinctKey(
            dayOfWeek = 1,
            scheduledTime = "Esnek • 90 dk",
            subject = "AYT Matematik",
            topic = "Türev"
        )
        val key2Duplicate = PlanRepositoryImpl.buildPlanDistinctKey(
            dayOfWeek = 1,
            scheduledTime = "Esnek • 90 dk",
            subject = "ayt matematik",
            topic = "  türev "
        )

        assertNotEquals(key1, key2)
        assertEquals(key2, key2Duplicate)
    }
}
