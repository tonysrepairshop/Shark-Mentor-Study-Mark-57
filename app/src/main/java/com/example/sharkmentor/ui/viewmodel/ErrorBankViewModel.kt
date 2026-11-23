package com.example.sharkmentor.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.sharkmentor.SharkMentorApplication
import com.example.sharkmentor.core.ai.GeminiCoreService
import com.example.sharkmentor.core.badges.SharkBadgeCatalog
import com.example.sharkmentor.core.fsrs.FsrsEngine
import com.example.sharkmentor.core.fsrs.FsrsRating
import com.example.sharkmentor.core.model.TwinQuestionData
import com.example.sharkmentor.core.util.GoogleServicesHelper
import com.example.sharkmentor.core.util.ImageStorageManager
import com.example.sharkmentor.data.local.entity.ErrorQuestionEntity
import com.example.sharkmentor.data.local.entity.TrainingFolderEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import kotlinx.coroutines.flow.combine
import com.example.sharkmentor.data.repository.AiSolverRepository

data class QuestionInteractionState(
    val hasUsedSocraticHint: Boolean = false,
    val hasUsedAiSolution: Boolean = false,
    val isTwinDisabled: Boolean = false,
    val cachedAiSolution: String? = null,
    val isAiLoading: Boolean = false,
    val isAiError: Boolean = false,
    val socraticHint: String? = null,
    val isHintLoading: Boolean = false
)

@Immutable
sealed interface TrainingUiState {
    object Loading : TrainingUiState
    
    @Immutable
    data class Active(
        val currentQuestion: ErrorQuestionEntity,
        val remainingCount: Int,
        val parsedTwinData: TwinQuestionData? = null
    ) : TrainingUiState
    
    object Completed : TrainingUiState
    
    @Immutable
    data class Error(val message: String) : TrainingUiState
}

@Immutable
data class SubjectFolderItem(
    val subjectName: String,
    val totalCount: Int,
    val dueCount: Int
)

@Immutable
data class ErrorBankUiState(
    val folders: List<SubjectFolderItem> = emptyList(),
    val aiTrainingFolders: List<TrainingFolderEntity> = emptyList(),
    val selectedSubject: String? = null,
    val selectedTopic: String? = null,
    val subjectQuestions: List<ErrorQuestionEntity> = emptyList(),
    val availableTopics: List<String> = emptyList(),
    val previewQuestion: ErrorQuestionEntity? = null,
    val allQuestions: List<ErrorQuestionEntity> = emptyList(),
    val dueQuestions: List<ErrorQuestionEntity> = emptyList(),
    val dueQuestionsCount: Int = 0
)

class ErrorBankViewModel(application: Application, savedStateHandle: androidx.lifecycle.SavedStateHandle) : AndroidViewModel(application) {
    private var dao: com.example.sharkmentor.data.local.dao.ErrorQuestionDao? = null
    private var trainingFolderDao: com.example.sharkmentor.data.local.dao.TrainingFolderDao? = null
    private var trainingQuestionDao: com.example.sharkmentor.data.local.dao.TrainingQuestionDao? = null
    private val context = application.applicationContext
    private val geminiService = GeminiCoreService(context)

    private val _uiState = MutableStateFlow<TrainingUiState>(TrainingUiState.Loading)
    val uiState: StateFlow<TrainingUiState> = _uiState.asStateFlow()

    private val sessionQueue = mutableListOf<ErrorQuestionEntity>()
    private var isInitialized = false
    
    val targetSubject: String? = savedStateHandle.get<String>("subject")?.takeIf { it != "all" && it.isNotBlank() }
    val targetTopic: String? = savedStateHandle.get<String>("topic")?.takeIf { it != "all" && it.isNotBlank() }
    val targetFolderId: String? = savedStateHandle.get<String>("folderId")?.takeIf { it != "all" && it.isNotBlank() }

    private val _interactionStates = MutableStateFlow<Map<Long, QuestionInteractionState>>(emptyMap())
    val interactionStates: StateFlow<Map<Long, QuestionInteractionState>> = _interactionStates.asStateFlow()

    private val aiSolverRepository by lazy { AiSolverRepository(context) }

    val isAnswerRevealed = MutableStateFlow(false)
    val isTwinGenerating = MutableStateFlow(false)
    val feedbackMessage = MutableStateFlow<String?>(null)

    // Twin Quiz UI States
    val selectedOptionIndex = MutableStateFlow<Int?>(null)
    val isOptionChecked = MutableStateFlow(false)

    // For Bank Screen lists (due/all)
    private val _refreshTrigger = MutableStateFlow(System.currentTimeMillis())
    
    private val _allQuestions = MutableStateFlow<List<ErrorQuestionEntity>>(emptyList())
    private val _dueQuestions = MutableStateFlow<List<ErrorQuestionEntity>>(emptyList())
    private val _dueQuestionsCount = MutableStateFlow<Int>(0)
    val dueQuestionsCount: StateFlow<Int> = _dueQuestionsCount.asStateFlow()

    private val _aiTrainingFolders = MutableStateFlow<List<TrainingFolderEntity>>(emptyList())

    private val _selectedSubject = MutableStateFlow<String?>(null)
    private val _selectedTopic = MutableStateFlow<String?>(null)
    private val _previewQuestion = MutableStateFlow<ErrorQuestionEntity?>(null)

    val bankUiState: StateFlow<ErrorBankUiState> = combine(
        combine(_allQuestions, _dueQuestions, _dueQuestionsCount, ::Triple),
        combine(_selectedSubject, _previewQuestion, _selectedTopic, ::Triple),
        _aiTrainingFolders
    ) { (all, due, count), (selected, preview, topic), aiFolders ->
        val now = System.currentTimeMillis()
        val folders = all.groupBy { it.subject.trim() }.map { (subj, list) ->
            SubjectFolderItem(
                subjectName = subj,
                totalCount = list.size,
                dueCount = list.count { it.state == 0 || it.dueDateMillis <= now }
            )
        }.sortedBy { it.subjectName }

        val allSubjectQuestions = if (selected != null) {
            all.filter { it.subject.trim().equals(selected.trim(), ignoreCase = true) }
        } else emptyList()

        // Collect distinct non-null aiTopics and user topics for the subfolder/filter chip row
        val availableTopics = allSubjectQuestions
            .flatMap { q ->
                listOfNotNull(
                    q.aiTopic?.trim()?.takeIf { it.isNotBlank() },
                    q.topic.trim().takeIf { it.isNotBlank() && !it.equals("Genel", ignoreCase = true) }
                )
            }
            .distinct()
            .sorted()

        // Apply topic filter when one is selected (excluding special '__ALL__' wildcard)
        val subjectQuestions = if (topic != null && topic != "__ALL__") {
            allSubjectQuestions.filter { q ->
                q.aiTopic.equals(topic, ignoreCase = true) || q.topic.equals(topic, ignoreCase = true)
            }
        } else {
            allSubjectQuestions
        }

        ErrorBankUiState(
            folders = folders,
            aiTrainingFolders = aiFolders,
            selectedSubject = selected,
            selectedTopic = topic,
            subjectQuestions = subjectQuestions,
            availableTopics = availableTopics,
            previewQuestion = preview,
            allQuestions = all,
            dueQuestions = due,
            dueQuestionsCount = count
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ErrorBankUiState()
    )

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val db = com.example.sharkmentor.data.local.SharkMentorDatabase.getDatabase(application)
            val databaseDao = db.errorQuestionDao()
            dao = databaseDao
            trainingFolderDao = db.trainingFolderDao()
            trainingQuestionDao = db.trainingQuestionDao()
            
            launch {
                databaseDao.getAllQuestions().collect {
                    _allQuestions.value = it
                    _aiTrainingFolders.value = runCatching { trainingFolderDao?.getAllFolders().orEmpty() }.getOrDefault(emptyList())
                }
            }
            
            launch {
                _refreshTrigger.flatMapLatest { now -> databaseDao.getDueQuestions(now) }
                    .collect { _dueQuestions.value = it }
            }
            
            launch {
                _refreshTrigger.flatMapLatest { now -> databaseDao.getDueQuestionsCount(now) }
                    .collect { _dueQuestionsCount.value = it }
            }

            while (true) {
                delay(60_000) // update every minute
                _refreshTrigger.value = System.currentTimeMillis()
                _aiTrainingFolders.value = runCatching { trainingFolderDao?.getAllFolders().orEmpty() }.getOrDefault(emptyList())
            }
        }
    }

    fun refreshDueQuestions() {
        _refreshTrigger.value = System.currentTimeMillis()
        viewModelScope.launch(Dispatchers.IO) {
            _aiTrainingFolders.value = runCatching { trainingFolderDao?.getAllFolders().orEmpty() }.getOrDefault(emptyList())
        }
    }

    fun selectSubject(subject: String?) {
        _selectedSubject.value = subject
        _selectedTopic.value = null
    }

    fun setPreviewQuestion(question: ErrorQuestionEntity?) {
        _previewQuestion.value = question
    }

    fun addQuestion(questionUri: String, solutionUri: String?, subject: String, topic: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val institutionId = com.example.sharkmentor.data.local.preferences.UserPreferences.getInstance(context).institutionId
            val entity = ErrorQuestionEntity(
                subject = subject,
                questionImageUri = questionUri,
                solutionImageUri = solutionUri,
                topic = topic,
                dueDateMillis = System.currentTimeMillis(), // Due immediately by default
                institutionId = institutionId,
                isSynced = false
                // aiTopic starts as null — TopicTaggingWorker will fill it in
            )
            dao?.insertQuestion(entity)
            com.example.sharkmentor.core.coaching.AiCoachLedgerManager.syncWithErrorBankInBackground(context)
            com.example.sharkmentor.service.sync.SyncWorker.enqueueImmediateSync(context)
            // Trigger background AI topic tagging for any untagged questions
            com.example.sharkmentor.worker.TopicTaggingWorker.enqueue(context)
        }
    }

    fun deleteQuestion(question: ErrorQuestionEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            // Delete associated images
            ImageStorageManager.deleteImageFile(question.questionImageUri)
            ImageStorageManager.deleteImageFile(question.solutionImageUri)

            // Delete from database
            dao?.deleteQuestion(question)
            com.example.sharkmentor.core.coaching.AiCoachLedgerManager.syncWithErrorBankInBackground(context)
        }
    }

    /** Selects (or clears) the topic filter for the active subject's question list. */
    fun selectTopic(topic: String?) {
        _selectedTopic.value = topic
    }

    /**
     * Manually reassigns the topic tag for a question within its current subject.
     * Updates Room, refreshes state, and enqueues immediate Cloud Sync.
     */
    fun updateQuestionTopic(questionId: Long, newTopic: String) {
        val cleanTopic = newTopic.trim().ifBlank { "Genel" }
        val existing = _allQuestions.value.find { it.id == questionId }
        if (existing != null) {
            reclassifyQuestion(
                questionId = questionId,
                newSubject = existing.subject,
                newTopic = cleanTopic,
                aiTopic = cleanTopic
            )
        } else {
            viewModelScope.launch(Dispatchers.IO) {
                dao?.updateAiTopic(questionId, cleanTopic)
                refreshDueQuestions()
                com.example.sharkmentor.service.sync.SyncWorker.enqueueImmediateSync(context)
            }
        }
    }

    /**
     * Updates a question's subject, topic, and aiTopic in Room (`isSynced = false`),
     * updates preview/session state, refreshes due counts, and triggers immediate Cloud Sync.
     */
    fun reclassifyQuestion(
        questionId: Long,
        newSubject: String,
        newTopic: String,
        aiTopic: String? = newTopic,
        onCompleted: (() -> Unit)? = null
    ) {
        val cleanSubject = newSubject.trim().ifBlank { "Matematik" }
        val cleanTopic = newTopic.trim().ifBlank { "Genel" }
        val cleanAiTopic = aiTopic?.trim()?.takeIf { it.isNotBlank() } ?: cleanTopic

        viewModelScope.launch(Dispatchers.IO) {
            val db = com.example.sharkmentor.data.local.SharkMentorDatabase.getDatabase(getApplication())
            val activeDao = dao ?: db.errorQuestionDao().also { dao = it }
            activeDao.updateQuestionClassification(
                id = questionId,
                subject = cleanSubject,
                topic = cleanTopic,
                aiTopic = cleanAiTopic
            )

            // Keep previewQuestion in sync if currently open
            val currentPreview = _previewQuestion.value
            if (currentPreview != null && currentPreview.id == questionId) {
                _previewQuestion.value = currentPreview.copy(
                    subject = cleanSubject,
                    topic = cleanTopic,
                    aiTopic = cleanAiTopic,
                    isSynced = false,
                    lastModifiedMillis = System.currentTimeMillis()
                )
            }

            refreshDueQuestions()
            com.example.sharkmentor.service.sync.SyncWorker.enqueueImmediateSync(context)

            if (onCompleted != null) {
                withContext(Dispatchers.Main) {
                    onCompleted()
                }
            }
        }
    }

    /**
     * On-demand AI reclassification using ONLY `gemini-3.6-flash` (with strict
     * Physics / Chemistry / Biology disambiguation).
     */
    fun reclassifyQuestionWithAi(
        question: ErrorQuestionEntity,
        onResult: ((resolvedSubject: String, resolvedTopic: String) -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = com.example.sharkmentor.worker.TopicTaggingWorker.classifyQuestionWithGemini(
                    context = context,
                    question = question
                )
                if (result != null) {
                    val (resolvedSubject, resolvedTopic) = result
                    reclassifyQuestion(
                        questionId = question.id,
                        newSubject = resolvedSubject,
                        newTopic = resolvedTopic,
                        aiTopic = resolvedTopic,
                        onCompleted = {
                            onResult?.invoke(resolvedSubject, resolvedTopic)
                        }
                    )
                } else {
                    withContext(Dispatchers.Main) {
                        onError?.invoke("AI sınıflandırma yanıtı alınamadı.")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onError?.invoke("AI sınıflandırma hatası: ${e.localizedMessage ?: "Bilinmeyen hata"}")
                }
            }
        }
    }

    /** Manually triggers a batch AI-tagging run for all untagged questions. */
    fun enqueueTopicTagging() {
        com.example.sharkmentor.worker.TopicTaggingWorker.enqueue(context)
    }

    // --- TRAINING SESSION LOGIC ---

    fun initializeSession(
        subject: String? = null,
        topic: String? = null,
        folderId: String? = null
    ) {
        if (isInitialized) return
        isInitialized = true
        loadQuestions(
            subject = subject ?: targetSubject,
            topic = topic ?: targetTopic ?: _selectedTopic.value,
            folderId = folderId ?: targetFolderId
        )
    }

    fun loadQuestions(
        subject: String? = null,
        topic: String? = null,
        folderId: String? = null
    ) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _uiState.value = TrainingUiState.Loading
            try {
                val now = System.currentTimeMillis()
                val loadedQuestions = withContext(Dispatchers.IO) {
                    val db = com.example.sharkmentor.data.local.SharkMentorDatabase.getDatabase(getApplication())
                    val errorDao = dao ?: db.errorQuestionDao().also { dao = it }
                    val folderDao = trainingFolderDao ?: db.trainingFolderDao().also { trainingFolderDao = it }
                    val folderQDao = trainingQuestionDao ?: db.trainingQuestionDao().also { trainingQuestionDao = it }

                    val allErrorQuestions = errorDao.getAllQuestionsDirect()

                    if (!folderId.isNullOrBlank()) {
                        val folderQuestions = folderQDao.getQuestionsByFolder(folderId)
                        val folderEntity = folderDao.getAllFolders().find { it.id == folderId }
                        val mappedFromFolder = folderQuestions.mapNotNull { tq ->
                            val existingMatch = allErrorQuestions.find { eq ->
                                !tq.imageUri.isNullOrBlank() && eq.questionImageUri == tq.imageUri
                            }
                            existingMatch ?: if (!tq.imageUri.isNullOrBlank()) {
                                ErrorQuestionEntity(
                                    id = tq.id.hashCode().toLong().let { if (it < 0) -it else it + 1L },
                                    subject = folderEntity?.title?.substringBefore("•")?.trim()?.ifBlank { "AI Klasörü" } ?: "AI Klasörü",
                                    topic = folderEntity?.title?.substringAfter("•", tq.questionText)?.trim()?.ifBlank { "Genel" } ?: "Genel",
                                    aiTopic = folderEntity?.title,
                                    questionImageUri = tq.imageUri,
                                    dueDateMillis = now
                                )
                            } else null
                        }
                        if (mappedFromFolder.isNotEmpty()) {
                            mappedFromFolder.take(20)
                        } else {
                            // Fallback: match folder title keywords against error_questions subject/topic
                            val titleTokens = folderEntity?.title
                                ?.split("•", "-", " ")
                                ?.map { it.trim() }
                                ?.filter { it.length >= 3 }
                                .orEmpty()
                            val matchedByTitle = allErrorQuestions.filter { eq ->
                                titleTokens.any { token ->
                                    eq.subject.contains(token, ignoreCase = true) ||
                                        eq.topic.contains(token, ignoreCase = true) ||
                                        (eq.aiTopic?.contains(token, ignoreCase = true) == true)
                                }
                            }
                            (if (matchedByTitle.isNotEmpty()) matchedByTitle else allErrorQuestions).take(20)
                        }
                    } else {
                        val filteredByFolderAndTopic = allErrorQuestions.filter { eq ->
                            val matchesSubject = subject.isNullOrBlank() || eq.subject.equals(subject, ignoreCase = true)
                            val matchesTopic = topic.isNullOrBlank() ||
                                eq.aiTopic.equals(topic, ignoreCase = true) ||
                                eq.topic.equals(topic, ignoreCase = true)
                            matchesSubject && matchesTopic
                        }
                        val dueFiltered = filteredByFolderAndTopic.filter { eq ->
                            eq.state == 0 || eq.dueDateMillis <= now
                        }
                        // Prioritize due questions; if all due questions were already reviewed, allow practicing the folder's questions anyway
                        val finalPool = if (dueFiltered.isNotEmpty()) dueFiltered else filteredByFolderAndTopic
                        finalPool.take(20)
                    }
                }

                sessionQueue.clear()
                sessionQueue.addAll(loadedQuestions)

                resetCardStates()
                emitActiveOrCompleted()
            } catch (e: Exception) {
                _uiState.value = TrainingUiState.Error("Sorular alınamadı: ${e.localizedMessage}")
            }
        }
    }

    private fun emitActiveOrCompleted() {
        if (sessionQueue.isEmpty()) {
            _uiState.value = TrainingUiState.Completed
        } else {
            val currentQ = sessionQueue.first()
            val parsedTwin = parseTwinJson(currentQ.twinQuestionJson)
            _uiState.value = TrainingUiState.Active(
                currentQuestion = currentQ,
                remainingCount = sessionQueue.size,
                parsedTwinData = parsedTwin
            )
        }
    }

    private fun resetCardStates() {
        isAnswerRevealed.value = false
        selectedOptionIndex.value = null
        isOptionChecked.value = false
    }

    // ON-DEMAND TWIN QUESTION GENERATOR
    fun generateTwinQuestionOnDemand() {
        val currentState = _uiState.value as? TrainingUiState.Active ?: return
        val currentQ = currentState.currentQuestion
        val interactionState = _interactionStates.value[currentQ.id]

        if (interactionState?.isTwinDisabled == true) {
            feedbackMessage.value = context.getString(com.example.sharkmentor.R.string.twin_question_locked_notice)
            return
        }

        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            isTwinGenerating.value = true
            feedbackMessage.value = context.getString(com.example.sharkmentor.R.string.twin_question_generating)

            val res = geminiService.generateTwinQuestion(currentQ.questionImageUri, currentQ.topic)
            res.onSuccess { twinData ->
                val json = JSONObject().apply {
                    put("questionText", twinData.questionText)
                    put("options", org.json.JSONArray(twinData.options))
                    put("correctIndex", twinData.correctIndex)
                    put("explanation", twinData.explanation)
                }.toString()

                val updatedEntity = currentQ.copy(twinQuestionJson = json, isTwinGenerated = true, isSynced = false)
                
                withContext(Dispatchers.IO) {
                    dao?.updateQuestion(updatedEntity)
                    com.example.sharkmentor.service.sync.SyncWorker.enqueueImmediateSync(context)
                }

                // Immediate Session Sync: Replace in active queue & update active view state
                val queueIdx = sessionQueue.indexOfFirst { it.id == currentQ.id }
                if (queueIdx != -1) {
                    sessionQueue[queueIdx] = updatedEntity
                }

                resetCardStates()
                _uiState.value = TrainingUiState.Active(
                    currentQuestion = updatedEntity,
                    remainingCount = sessionQueue.size,
                    parsedTwinData = twinData
                )
                feedbackMessage.value = context.getString(com.example.sharkmentor.R.string.twin_question_success)
            }.onFailure { err ->
                feedbackMessage.value = context.getString(com.example.sharkmentor.R.string.twin_question_error) + " (${err.localizedMessage})"
            }
            isTwinGenerating.value = false
        }
    }

    // Interactive Twin Quiz Option Selection
    fun selectOption(index: Int) {
        if (!isOptionChecked.value) {
            selectedOptionIndex.value = index
        }
    }

    fun verifyTwinAnswer() {
        isOptionChecked.value = true
    }

    // FSRS RATING HANDLER
    fun onRatingSelected(rating: FsrsRating) {
        val currentState = _uiState.value as? TrainingUiState.Active ?: return
        val currentQ = currentState.currentQuestion

        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            withContext(Dispatchers.IO) {
                val updated = FsrsEngine.calculateNextReview(currentQ, rating, System.currentTimeMillis())
                val newLapses = if (rating == FsrsRating.AGAIN) currentQ.lapses + 1 else updated.lapses
                val entityToSave = updated.copy(lapses = newLapses, isSynced = false)
                
                dao?.updateQuestion(entityToSave)
                com.example.sharkmentor.core.coaching.AiCoachLedgerManager.onErrorQuestionReviewedInBackground(
                    context = context,
                    question = entityToSave,
                    wasSolvedCorrectly = (rating == FsrsRating.GOOD || rating == FsrsRating.EASY)
                )
                com.example.sharkmentor.service.sync.SyncWorker.enqueueImmediateSync(context)

                if (rating == FsrsRating.GOOD || rating == FsrsRating.EASY) {
                    val effectiveTopic = currentQ.aiTopic?.takeIf { it.isNotBlank() } ?: currentQ.topic
                    SharkBadgeCatalog.recordTopicQuestionSolvedLocally(
                        context = context,
                        subject = currentQ.subject,
                        topic = effectiveTopic,
                        syncToCloudIfLoggedIn = false
                    )
                    val uid = runCatching { GoogleServicesHelper.getCurrentUser(context = context)?.uid }.getOrNull()
                    if (!uid.isNullOrBlank() && uid != "local_student" && uid != "student_default") {
                        SharkBadgeCatalog.syncShowcaseAndBadgesToCloud(context, uid)
                    }
                }

                // Session Queue Logic:
                sessionQueue.remove(currentQ)
                if (rating == FsrsRating.AGAIN) {
                    // Only failed questions are re-queued at the end of current session
                    sessionQueue.add(entityToSave)
                }
            }

            resetCardStates()
            emitActiveOrCompleted()
        }
    }

    fun toggleAnswerReveal() {
        isAnswerRevealed.value = !isAnswerRevealed.value
    }

    fun requestSocraticHint() {
        val currentState = _uiState.value as? TrainingUiState.Active ?: return
        val qId = currentState.currentQuestion.id
        val current = _interactionStates.value[qId] ?: QuestionInteractionState()
        
        _interactionStates.value = _interactionStates.value + (qId to current.copy(isHintLoading = true))
        
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val res = geminiService.generateSocraticHint(
                currentState.currentQuestion.questionImageUri,
                currentState.currentQuestion.topic
            )
            res.onSuccess { hint -> 
                _interactionStates.value = _interactionStates.value + (qId to current.copy(
                    isHintLoading = false,
                    socraticHint = hint,
                    hasUsedSocraticHint = true
                ))
            }.onFailure { 
                _interactionStates.value = _interactionStates.value + (qId to current.copy(isHintLoading = false))
                feedbackMessage.value = "İpucu alınamadı: ${it.localizedMessage}" 
            }
        }
    }

    fun requestAiSolution(questionId: Long, questionUri: String, subject: String, optionsText: String? = null) {
        val current = _interactionStates.value[questionId] ?: QuestionInteractionState()

        // In-memory token cache: Do not charge tokens if already generated
        if (current.cachedAiSolution != null) return

        _interactionStates.value = _interactionStates.value + (questionId to current.copy(
            isAiLoading = true,
            isAiError = false
        ))

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = withTimeout(15_000L) {
                    aiSolverRepository.getFullSolution(questionUri, subject, optionsText)
                }

                result.onSuccess { solution ->
                    _interactionStates.value = _interactionStates.value + (questionId to current.copy(
                        hasUsedSocraticHint = true,
                        hasUsedAiSolution = true,
                        // isTwinDisabled intentionally NOT set — İkiz Soru stays available
                        cachedAiSolution = solution,
                        isAiLoading = false,
                        isAiError = false
                    ))

                    // Mark question as failed in local database/session
                    val currentState = _uiState.value as? TrainingUiState.Active
                    val currentQ = currentState?.currentQuestion
                    if (currentQ != null && currentQ.id == questionId) {
                        val updated = FsrsEngine.calculateNextReview(currentQ, FsrsRating.AGAIN, System.currentTimeMillis())
                        val entityToSave = updated.copy(lapses = currentQ.lapses + 1, isSynced = false)
                        dao?.updateQuestion(entityToSave)
                        com.example.sharkmentor.service.sync.SyncWorker.enqueueImmediateSync(context)
                    }
                }.onFailure { error ->
                    _interactionStates.value = _interactionStates.value + (questionId to current.copy(
                        isAiLoading = false,
                        isAiError = true,
                        cachedAiSolution = null
                    ))
                    feedbackMessage.value = "AI çözüm alınamadı: ${error.localizedMessage}"
                }
            } catch (e: TimeoutCancellationException) {
                _interactionStates.value = _interactionStates.value + (questionId to current.copy(
                    isAiLoading = false,
                    isAiError = true,
                    cachedAiSolution = null
                ))
                feedbackMessage.value = "AI yanıt süresi doldu. Lütfen tekrar deneyin."
            } catch (e: Exception) {
                _interactionStates.value = _interactionStates.value + (questionId to current.copy(
                    isAiLoading = false,
                    isAiError = true,
                    cachedAiSolution = null
                ))
                feedbackMessage.value = "Bağlantı hatası: ${e.localizedMessage}"
            }
        }
    }

    /** Clears error state and retries AI solution for the currently active question. */
    fun retrySolveCurrentQuestion() {
        val state = _uiState.value as? TrainingUiState.Active ?: return
        val q = state.currentQuestion
        val current = _interactionStates.value[q.id] ?: QuestionInteractionState()
        // Reset error + solution so the request path is not short-circuited
        _interactionStates.value = _interactionStates.value + (q.id to current.copy(
            isAiError = false,
            isAiLoading = false,
            cachedAiSolution = null
        ))
        requestAiSolution(q.id, q.questionImageUri, q.subject)
    }

    fun clearFeedbackMessage() {
        feedbackMessage.value = null
    }

    private fun parseTwinJson(jsonStr: String?): TwinQuestionData? {
        if (jsonStr.isNullOrBlank()) return null
        return try {
            val jsonObj = JSONObject(jsonStr)
            val qText = jsonObj.getString("questionText")
            val optsArray = jsonObj.getJSONArray("options")
            val options = (0 until optsArray.length()).map { optsArray.getString(it) }
            val correctIdx = jsonObj.getInt("correctIndex")
            val exp = jsonObj.optString("explanation", "")
            TwinQuestionData(qText, options, correctIdx, exp)
        } catch (e: Exception) {
            null
        }
    }
}

