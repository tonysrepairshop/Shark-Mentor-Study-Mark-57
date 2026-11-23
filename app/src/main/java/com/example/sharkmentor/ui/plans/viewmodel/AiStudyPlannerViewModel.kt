package com.example.sharkmentor.ui.plans.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.sharkmentor.core.ai.ApiKeyRotator
import com.example.sharkmentor.core.ai.GeminiAiFactory
import com.example.sharkmentor.core.ai.GeminiRestClient
import com.example.sharkmentor.core.ai.UserAiPreferences
import com.example.sharkmentor.core.util.GoogleServicesHelper
import com.example.sharkmentor.data.local.SharkMentorDatabase
import com.example.sharkmentor.data.local.entity.InstitutionScheduleEntity
import com.example.sharkmentor.data.local.entity.PlanEntity
import com.example.sharkmentor.data.local.entity.TargetEntity
import com.example.sharkmentor.data.local.entity.TargetPeriod
import com.example.sharkmentor.data.local.entity.TargetType
import com.example.sharkmentor.data.remote.GeminiRepository
import com.example.sharkmentor.data.remote.ScannedTaskDto
import com.example.sharkmentor.data.repository.PlanRepository
import com.example.sharkmentor.data.repository.PlanRepositoryImpl
import com.example.sharkmentor.ui.plans.model.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import java.util.UUID

sealed interface PlannerUiState {
    object Idle : PlannerUiState
    object Loading : PlannerUiState
    data class Success(val summary: String) : PlannerUiState
    data class Error(val message: String) : PlannerUiState
}

class AiStudyPlannerViewModel(application: Application) : AndroidViewModel(application) {

    private val firestore get() = GoogleServicesHelper.getFirebaseFirestore(getApplication())
    private val auth get() = GoogleServicesHelper.getFirebaseAuth(getApplication())
    private val gson = Gson()

    private val database = SharkMentorDatabase.getDatabase(application)
    private val repository = PlanRepository(database.targetDao(), database.sessionDao(), database.planFolderDao())

    private val _uiState = MutableStateFlow<PlannerUiState>(PlannerUiState.Idle)
    val uiState: StateFlow<PlannerUiState> = _uiState.asStateFlow()

    private val _activePlans = MutableStateFlow<List<PlanItem>>(emptyList())
    val activePlans: StateFlow<List<PlanItem>> = _activePlans.asStateFlow()

    private val _isOrganizing = MutableStateFlow(false)
    val isOrganizing: StateFlow<Boolean> = _isOrganizing.asStateFlow()

    private var isPlanGenerationInProgress = false

    init {
        fetchActivePlans()
    }

    fun fetchActivePlans() {
        val uid = GoogleServicesHelper.getCurrentUser(auth, getApplication())?.uid ?: return
        firestore?.collection("students")?.document(uid)
            ?.collection("plans")
            ?.orderBy("dayOfWeek")
            ?.get()
            ?.addOnSuccessListener { snapshot ->
                if (snapshot == null) return@addOnSuccessListener
                val items = snapshot.documents.mapNotNull { it.toObject(PlanItem::class.java) }
                _activePlans.value = items
            }
    }

    fun togglePlanCompletion(planId: String, currentStatus: Boolean) {
        val nextStatus = !currentStatus
        val previousPlans = _activePlans.value
        _activePlans.value = previousPlans.map { item ->
            if (item.id == planId) item.copy(isCompleted = nextStatus) else item
        }
        val uid = GoogleServicesHelper.getCurrentUser(auth, getApplication())?.uid ?: return
        firestore?.collection("students")?.document(uid)
            ?.collection("plans")?.document(planId)
            ?.update("completed", nextStatus)
            ?.addOnFailureListener {
                _activePlans.value = previousPlans
            }
    }

    fun organizeWeeklyPlan() {
        viewModelScope.launch(Dispatchers.IO) {
            if (_isOrganizing.value) return@launch
            _isOrganizing.value = true

            val uid = GoogleServicesHelper.getCurrentUser(auth, getApplication())?.uid ?: run {
                _isOrganizing.value = false
                return@launch
            }
            val db = firestore ?: run {
                _isOrganizing.value = false
                return@launch
            }

            val currentPlans = _activePlans.value
            if (currentPlans.isEmpty()) {
                _isOrganizing.value = false
                return@launch
            }

            val timeSlots = listOf(
                "09:00 - 10:30",
                "11:00 - 12:30",
                "14:00 - 15:30",
                "16:30 - 18:00",
                "19:00 - 20:30"
            )

            // Sort each subject's topics across the week in canonical curriculum order (e.g. Fonksiyonlar before Türev)
            val localTopicGoals = runCatching { database.goalsDao().getAllTopicsRaw() }.getOrDefault(emptyList())
            val gatedPlans = currentPlans.map { item ->
                val gate = com.example.sharkmentor.core.coaching.BookCampManager.enforcePrerequisiteFoundationGate(
                    subject = item.subject,
                    requestedTopic = item.topic,
                    studentTopicGoals = localTopicGoals
                )
                item.copy(topic = gate.effectiveTopic)
            }
            val curriculumReorderedBySubject = gatedPlans
                .groupBy { it.subject.lowercase() }
                .values
                .flatMap { subjectTasks ->
                    val sortedByDaySlots = subjectTasks.sortedWith(compareBy<PlanItem> { it.dayOfWeek }.thenBy { it.timeSlot })
                    val sortedTopics = subjectTasks.sortedBy {
                        com.example.sharkmentor.core.coaching.BookCampManager.canonicalTopicIndex(it.subject, it.topic)
                    }
                    sortedByDaySlots.mapIndexed { idx, slotItem ->
                        val topicSource = sortedTopics[idx]
                        slotItem.copy(
                            topic = topicSource.topic,
                            targetQuestions = topicSource.targetQuestions,
                            focusStrategy = topicSource.focusStrategy
                        )
                    }
                }

            val organized: List<PlanItem> = curriculumReorderedBySubject
                .groupBy { it.dayOfWeek }
                .toSortedMap()
                .flatMap { (_, dayTasks) ->
                    dayTasks
                        .sortedWith(
                            compareBy<PlanItem> {
                                com.example.sharkmentor.core.coaching.BookCampManager.canonicalTopicIndex(it.subject, it.topic)
                            }.thenByDescending { it.targetQuestions }
                                .thenBy { it.subject }
                        )
                        .mapIndexed { index, item ->
                            item.copy(timeSlot = timeSlots[index.coerceAtMost(timeSlots.lastIndex)])
                        }
                }

            // 1. Instant local update
            _activePlans.value = organized

            // 2. Persist to Firestore
            try {
                val plansCollection = db
                    .collection("students").document(uid)
                    .collection("plans")

                val batch = db.batch()

                // Delete current week's entries
                currentPlans.forEach { item ->
                    batch.delete(plansCollection.document(item.id))
                }

                // Write organized entries
                organized.forEach { item ->
                    batch.set(plansCollection.document(item.id), item)
                }

                batch.commit().await()
                _uiState.value = PlannerUiState.Success("Plan başarıyla organize edildi.")
            } catch (e: Exception) {
                _uiState.value = PlannerUiState.Error("Plan organize edilirken hata: ${e.localizedMessage}")
            } finally {
                _isOrganizing.value = false
            }
        }
    }

    private suspend fun executeWithKeyRotation(promptText: String, systemPrompt: String): String {
        val result = GeminiAiFactory.generateContentWithFailover(
            context = getApplication(),
            systemInstruction = systemPrompt,
            userPrompt = promptText,
            responseJson = true,
            maxOutputTokens = 8192,
            timeoutMs = 55_000L
        )
        val raw = result.getOrElse { err ->
            throw (err as? Exception) ?: RuntimeException(err.localizedMessage ?: "Gemini API hatası", err)
        }.text
        val cleanJson = GeminiRestClient.extractCleanJsonString(raw)
        if (cleanJson.isBlank()) {
            throw IllegalStateException("Yapay zeka boş plan yanıtı döndürdü.")
        }
        return cleanJson
    }

    fun generateWeeklyPlan(userCustomNote: String) {
        if (isPlanGenerationInProgress) {
            Log.w("AiStudyPlanner", "Plan generation already active. Ignoring duplicate request.")
            return
        }
        isPlanGenerationInProgress = true

        viewModelScope.launch(Dispatchers.IO) {
            try {
            _uiState.value = PlannerUiState.Loading
            val uid = GoogleServicesHelper.getCurrentUser(auth, getApplication())?.uid ?: run {
                _uiState.value = PlannerUiState.Error("Kullanıcı oturumu bulunamadı.")
                return@launch
            }
            val db = firestore ?: run {
                _uiState.value = PlannerUiState.Error("Veritabanına erişilemedi.")
                return@launch
            }

            UserAiPreferences.initialize(getApplication())
            if (!ApiKeyRotator.hasAnyGeminiKey(getApplication())) {
                _uiState.value = PlannerUiState.Error("Gemini API anahtarı tanımlanmamış. Ayarlardan bir anahtar ekleyin.")
                return@launch
            }

            try {
                // 1. Fetch competencies, local Room topic mastery, goal, and past schedules in parallel
                val scheduleDao = database.institutionScheduleDao()
                val localTopicGoals = runCatching { database.goalsDao().getAllTopicsRaw() }.getOrDefault(emptyList())
                val (compDocs, goalDoc, currentSchedules) = coroutineScope {
                    val compDeferred = async {
                        db.collection("students").document(uid)
                            .collection("competencies").get().await()
                    }
                    val goalDeferred = async {
                        db.collection("students").document(uid)
                            .collection("goals").document("active").get().await()
                    }
                    val schedulesDeferred = async { scheduleDao.getAllSchedules() }
                    Triple(compDeferred.await(), goalDeferred.await(), schedulesDeferred.await())
                }
                val remoteCompetencies = compDocs.documents.mapNotNull { it.toObject(TopicCompetency::class.java) }
                val localCompetencies = localTopicGoals.map {
                    TopicCompetency(subject = it.subject, topicName = it.topic, level = it.currentLevel)
                }
                val mergedCompetencies = (localCompetencies + remoteCompetencies)
                    .distinctBy { "${it.subject.lowercase()}_${it.topicName.lowercase()}" }

                // Filter to top 15 weak competencies (weakest scores first)
                val prunedCompetencies = mergedCompetencies
                    .filter { it.level <= 3 }
                    .sortedWith(
                        compareBy<TopicCompetency> { it.level }
                            .thenBy { com.example.sharkmentor.core.coaching.BookCampManager.canonicalTopicIndex(it.subject, it.topicName) }
                    )
                    .take(15)

                val activeGoal = goalDoc.toObject(UserGoal::class.java) ?: UserGoal()

                // Optimization: Limit previous plan items payload context
                val previousPlanItems = _activePlans.value.take(15)

                val scheduleConstraintsPrompt = (1..7).map { dayIndex ->
                    val dayName = getDayName(dayIndex)
                    val classes = currentSchedules.filter { it.dayOfWeek == dayIndex }
                    val blockedTimes = classes.map { "${it.startTime}-${it.endTime} (${it.subject})" }
                    "GÜN $dayIndex ($dayName): ${if (blockedTimes.isEmpty()) "Serbest Gün" else blockedTimes.joinToString(", ")}"
                }.joinToString("\n")

                val appCtx = getApplication<Application>()
                com.example.sharkmentor.core.coaching.StudentDossierManager.ensureLoaded(appCtx)
                val targetCompassPrompt = runCatching {
                    if (com.example.sharkmentor.core.coaching.StudentDossierManager.shouldAttachDossier(
                            com.example.sharkmentor.core.coaching.DossierAiChannel.PLAN_ADD_AI
                        )
                    ) {
                        com.example.sharkmentor.core.coaching.StudentDossierManager.buildAnonymizedDossierPrompt()
                    } else {
                        com.example.sharkmentor.core.coaching.CoachingEngine.buildAiSystemCompassPrompt(appCtx)
                    }
                }.getOrDefault("")

                val wdGoalMins = com.example.sharkmentor.core.coaching.CoachingEngine.weekdayGoalMinutes.value
                val weGoalMins = com.example.sharkmentor.core.coaching.CoachingEngine.weekendGoalMinutes.value
                val wdBlocksTarget = (wdGoalMins / 90).coerceIn(2, 10)
                val weBlocksTarget = (weGoalMins / 90).coerceIn(2, 11)

                // 2. System Instruction
                val systemPrompt = """
                    Sen SharkMentor'un kıdemli pedagojik planlama motorusun.
                    GÖREV: Öğrencinin konu yetkinliklerine, anonim öğrenci dosyasına, hedef üniversite/bölüm/sıralama pusulasına ve seçtiği günlük çalışma saatine dayanarak 7 günlük (Pazartesi=1 .. Pazar=7) dengeli ve esnek çalışma planı hazırla.

                    $targetCompassPrompt

                    ÖĞRENCİNİN KİLİTLİ OKUL/DERS SAATLERİ (Yalnızca yoğunluk dengelemesi için referans):
                    $scheduleConstraintsPrompt

                    KESİN TALİMATLAR:
                    1. ESNEK SÜRE ZORUNLULUĞU: 'timeSlot' alanına ASLA '09:00 - 10:30' veya '10:00 - 11:30' gibi katı saat aralığı YAZMA! Her görev için yalnızca esnek süre etiketi yaz: örn. "Esnek • 90 dk", "Esnek • 60 dk", "Esnek • 75 dk". Öğrenci gün içinde kendi müsait anında esnek olarak tamamlayabilsin.
                    2. SEÇİLEN HEDEF SAATİNE MUTLAK SAYGI: Öğrencinin günlük hedefi hafta içi ${wdGoalMins / 60} saat (~$wdBlocksTarget etüt bloğu), hafta sonu ${weGoalMins / 60} saat (~$weBlocksTarget etüt bloğu). Bu saat hedefini ASLA sessizce 4-5 saate düşürme!
                    3. 7 GÜN & ÇOKLU DERS DENGESİ: Görevleri Pazartesi'den Pazar'a (1..7) tüm haftaya dengeli dağıt. Görevleri ASLA tek güne (örn. sadece Pazar) veya tek derse (örn. sadece Kimya) yığma! Her gün en az 2-3 farklı ders dengesi kur.

                    EK KURALLAR (Müfredat Bütünlüğü, Ön Koşul Zemini ve Eksik Konu Takibi):
                    - MÜFREDAT SIRALAMASI VE DERS BÜTÜNLÜĞÜ: Konuları ders içindeki pedagojik sırasına göre diz. Asla ilk güne Fonksiyonlar veya Limit'ten önce Türev/İntegral yazma.
                    - EKSİK KONUNUN PEŞİNİ BIRAKMA: Konu hakimiyetinde seviyesi 1 veya 2 olan zayıf konulara hafta içinde birden fazla etüt bloğu vererek ağırlık tanı.
                    - ZEMİN OTURTMADAN GEÇİRTME: Temel ön koşul konunun hakimiyeti düşükse ileri konuyu yazma, önce ön koşulu sağlamlaştır.
                    - KLON PLAN YASAKTIR: 'previousPlanItems' içinde 'isCompleted=true' ve hakimiyeti yüksek konuları tekrar ana odak yapma.
                    - Her çalışma bloğunda net konu başlığı ve minimum 30 soru hedefi belirt.

                    ÇIKTI FORMATI:
                    Yalnızca belirtilen JSON şemasında çıktı ver. Fazlalık metin ekleme.
                    JSON Şeması:
                    {
                      "weeklyFocusSummary": "Bu haftaki stratejik yönelim özeti",
                      "schedule": [
                        {
                          "dayOfWeek": 1,
                          "timeSlot": "Esnek • 90 dk",
                          "subject": "Matematik",
                          "topic": "Fonksiyonlar",
                          "targetQuestions": 40,
                          "focusStrategy": "Konu Pekiştirme ve Hata Çözümü"
                        }
                      ]
                    }
                """.trimIndent()

                // 3. Prepare payload context with pruned items
                val promptPayload = mapOf(
                    "userGoal" to activeGoal,
                    "weekdayTargetMinutes" to wdGoalMins,
                    "weekendTargetMinutes" to weGoalMins,
                    "competencies" to prunedCompetencies,
                    "previousPlanItems" to previousPlanItems.map {
                        mapOf(
                            "subject" to it.subject,
                            "topic" to it.topic,
                            "isCompleted" to it.isCompleted
                        )
                    },
                    "institutionSchedules" to currentSchedules.map {
                        mapOf(
                            "dayOfWeek" to it.dayOfWeek,
                            "startTime" to it.startTime,
                            "endTime" to it.endTime,
                            "subject" to it.subject
                        )
                    },
                    "studentNote" to userCustomNote
                )

                // 4. Enforce 60-Second Network & Execution Timeout with Multi-Key Rotation
                val jsonString = withTimeout(60_000L) {
                    executeWithKeyRotation(gson.toJson(promptPayload), systemPrompt)
                }
                val parsedResponse = gson.fromJson(jsonString, AiWeeklyPlanResponse::class.java)

                // Programmatic Validation:
                // (a) Normalize any accidental rigid clock range (e.g. "09:00 - 10:30") into flexible duration ("Esnek • 90 dk")
                // (b) Prerequisite Foundation Gatekeeping (don't allow Türev if Fonksiyonlar/Limit mastery <= 2)
                // (c) Canonical Curriculum Ordering across days within each subject (Fonksiyonlar always before Türev)
                val collisionChecked = parsedResponse.schedule.map { task ->
                    val normalizedSlot = normalizeToFlexibleDurationSlot(task.timeSlot)
                    val gate = com.example.sharkmentor.core.coaching.BookCampManager.enforcePrerequisiteFoundationGate(
                        subject = task.subject,
                        requestedTopic = task.topic,
                        studentTopicGoals = localTopicGoals
                    )
                    task.copy(
                        timeSlot = normalizedSlot,
                        topic = gate.effectiveTopic
                    )
                }

                // Ensure within each subject across the 7-day week, earlier curriculum topics come on earlier days
                val validatedSchedule = collisionChecked
                    .groupBy { it.subject.trim().lowercase() }
                    .values
                    .flatMap { subjectTasks ->
                        val chronologicalSlots = subjectTasks.sortedWith(compareBy({ it.dayOfWeek }, { it.timeSlot }))
                        val pedagogicalOrder = subjectTasks.sortedBy {
                            com.example.sharkmentor.core.coaching.BookCampManager.canonicalTopicIndex(it.subject, it.topic)
                        }
                        chronologicalSlots.mapIndexed { idx, slotTask ->
                            val orderedTopicTask = pedagogicalOrder[idx]
                            slotTask.copy(
                                topic = orderedTopicTask.topic,
                                targetQuestions = orderedTopicTask.targetQuestions,
                                focusStrategy = orderedTopicTask.focusStrategy
                            )
                        }
                    }
                    .sortedWith(compareBy({ it.dayOfWeek }, { it.timeSlot }))

                // 5. PRIMARY PERSISTENCE: Save generated items into Room Database for instant UI rendering
                val tasksToSave = validatedSchedule.map { raw ->
                    val safeQuestions = if (raw.targetQuestions > 0) raw.targetQuestions else 30
                    val targetEntity = TargetEntity(
                        title = "${raw.subject} (${raw.timeSlot})",
                        description = "${raw.topic} - ${raw.focusStrategy}",
                        type = TargetType.SHORT_TERM,
                        period = TargetPeriod.DAILY,
                        deadlineMillis = System.currentTimeMillis() + (raw.dayOfWeek - 1) * 86_400_000L,
                        targetQuestions = safeQuestions,
                        priority = "HIGH",
                        category = raw.subject
                    )
                    Pair(targetEntity, listOf("${raw.topic} - ${raw.focusStrategy} ($safeQuestions Soru)"))
                }
                
                // Route through PlanRepository's Ai-Planner root folder logic
                repository.saveAiGeneratedPlan(tasksToSave, forceNewFolder = false)

                // Save directly into PlanFolder & PlanEntity tables for the 2-level Plan Workspace
                val planDao = database.planDao()
                val planRepoImpl = PlanRepositoryImpl(planDao)
                val scannedTasks = validatedSchedule.map { raw ->
                    val safeDay = when {
                        raw.dayOfWeek in 1..7 -> raw.dayOfWeek
                        raw.dayOfWeek == 0 -> 1
                        else -> ((raw.dayOfWeek % 7) + 1).coerceIn(1, 7)
                    }
                    val timeStr = normalizeToFlexibleDurationSlot(raw.timeSlot)
                    val safeQuestions = if (raw.targetQuestions > 0) raw.targetQuestions else 30

                    ScannedTaskDto(
                        dayOfWeek = safeDay,
                        scheduledTime = timeStr,
                        subject = raw.subject,
                        topic = raw.topic,
                        targetQuestions = safeQuestions
                    )
                }
                planRepoImpl.saveAiPlannerSchedule(scannedTasks)

                // 6. SECONDARY PERSISTENCE: Save to Firestore
                val batch = db.batch()
                val plansCollection = db.collection("students").document(uid).collection("plans")

                // Clear outdated active Firestore plans
                _activePlans.value.forEach { item ->
                    batch.delete(plansCollection.document(item.id))
                }

                // Insert newly minted intelligent items into Firestore
                validatedSchedule.forEach { raw ->
                    val newId = UUID.randomUUID().toString()
                    val entity = PlanItem(
                        id = newId,
                        dayOfWeek = raw.dayOfWeek,
                        timeSlot = raw.timeSlot,
                        subject = raw.subject,
                        topic = raw.topic,
                        targetQuestions = raw.targetQuestions,
                        focusStrategy = raw.focusStrategy,
                        isCompleted = false
                    )
                    batch.set(plansCollection.document(newId), entity)
                }
                batch.commit().await()

                _uiState.value = PlannerUiState.Success(parsedResponse.weeklyFocusSummary)

            } catch (e: Exception) {
                val userMessage = when {
                    e is TimeoutCancellationException -> {
                        "Yapay zeka yanıt süresi aşıldı (60s). Lütfen tekrar deneyin."
                    }
                    e.message?.contains("PERMISSION_DENIED", ignoreCase = true) == true ||
                    e.message?.contains("unregistered callers", ignoreCase = true) == true -> {
                        "API erişim yetkisi reddedildi. Lütfen Gemini API anahtarınızı ve internet bağlantınızı doğrulayın."
                    }
                    e.message?.contains("MissingFieldException", ignoreCase = true) == true -> {
                        "Yapay zeka sunucusu ile iletişim kurulurken bir doğrulama hatası oluştu."
                    }
                    else -> e.localizedMessage ?: "Plan oluşturulurken bilinmeyen bir hata oluştu."
                }
                _uiState.value = PlannerUiState.Error(userMessage)
            } // Closes inner try-catch

            } finally {
                isPlanGenerationInProgress = false
            } // Closes outer try-finally
        } // Closes launch
    } // Closes generateWeeklyPlan

    private fun getDayName(dayOfWeek: Int): String {
        return when (dayOfWeek) {
            1 -> "Pazartesi"
            2 -> "Salı"
            3 -> "Çarşamba"
            4 -> "Perşembe"
            5 -> "Cuma"
            6 -> "Cumartesi"
            7 -> "Pazar"
            else -> "Bilinmiyor"
        }
    }

    private fun calculateAvailableStudySlots(
        classes: List<InstitutionScheduleEntity>,
        dayStartTime: String = "08:30",
        dayEndTime: String = "22:30"
    ): List<String> {
        if (classes.isEmpty()) {
            return listOf("09:00 - 12:30 (Sabah Etüdü)", "13:30 - 17:00 (Öğleden Sonra)", "18:30 - 21:00 (Akşam Tekrarı)")
        }

        val sorted = classes.sortedBy { it.startTime }
        val freeSlots = mutableListOf<String>()

        // Check gap before first class
        val firstClassStart = sorted.first().startTime
        if (getMinutesBetween(dayStartTime, firstClassStart) >= 60) {
            freeSlots.add("$dayStartTime -$firstClassStart (Sabah Hazırlık & Paragraf)")
        }

        // Dismissal gap calculation
        val lastClassEnd = sorted.maxOf { it.endTime }
        
        // Add 30-45 minutes buffer after school for transit/lunch
        val effectiveStudyStart = when {
            lastClassEnd <= "13:00" -> "13:30"
            lastClassEnd <= "15:30" -> "16:00"
            else -> lastClassEnd
        }

        if (effectiveStudyStart < "17:00") {
            freeSlots.add("$effectiveStudyStart - 15:30 (Öğleden Sonra 1. Blok)")
            freeSlots.add("15:45 - 17:00 (Öğleden Sonra 2. Blok)")
            freeSlots.add("18:30 - 20:30 (Akşam Pekiştirme & Soru)")
            freeSlots.add("20:45 - 22:30 (Gece Kapanış & Deneme Analizi)")
        } else {
            freeSlots.add("$effectiveStudyStart - 20:30 (Akşam Etüdü)")
            freeSlots.add("20:45 - 22:30 (Gece Kapanış)")
        }

        return freeSlots
    }

    private fun getMinutesBetween(start: String, end: String): Int {
        return try {
            val (h1, m1) = start.split(":").map { it.toInt() }
            val (h2, m2) = end.split(":").map { it.toInt() }
            (h2 * 60 + m2) - (h1 * 60 + m1)
        } catch (e: Exception) {
            0
        }
    }

    private fun normalizeToFlexibleDurationSlot(rawSlot: String): String {
        val clean = rawSlot.trim()
        if (clean.isBlank()) return "Esnek • 90 dk"
        val rangeMatch = Regex("""(\d{1,2}:\d{2})\s*[-–—]\s*(\d{1,2}:\d{2})""").find(clean)
        if (rangeMatch != null) {
            val mins = getMinutesBetween(rangeMatch.groupValues[1], rangeMatch.groupValues[2])
                .let { if (it in 20..240) it else 90 }
            return "Esnek • $mins dk"
        }
        return clean
    }
}
