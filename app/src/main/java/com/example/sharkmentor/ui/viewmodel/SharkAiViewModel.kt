package com.example.sharkmentor.ui.viewmodel

import android.app.Application
import android.content.ContentResolver
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.sharkmentor.core.util.ImageStorageManager
import com.example.sharkmentor.core.util.PdfBitmapConverter
import com.example.sharkmentor.core.util.SessionManager
import com.example.sharkmentor.data.local.SharkMentorDatabase
import com.example.sharkmentor.data.local.entity.ActivePlanEntity
import com.example.sharkmentor.data.local.entity.ConcreteTargetEntity
import com.example.sharkmentor.data.local.entity.ErrorQuestionEntity
import com.example.sharkmentor.data.local.entity.InstitutionScheduleEntity
import com.example.sharkmentor.data.local.entity.PlanEntity
import com.example.sharkmentor.data.local.entity.PlanFolderEntity
import com.example.sharkmentor.data.local.entity.ResourceEntity
import com.example.sharkmentor.data.local.entity.StudyTaskEntity
import com.example.sharkmentor.data.local.entity.TargetEntity
import com.example.sharkmentor.data.local.entity.TargetPeriod
import com.example.sharkmentor.data.local.entity.TargetType
import com.example.sharkmentor.data.local.entity.TargetWithTasks
import com.example.sharkmentor.data.local.entity.TopicGoalEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.example.sharkmentor.service.sync.SyncWorker
import com.example.sharkmentor.worker.TopicTaggingWorker
import com.example.sharkmentor.core.util.GoogleServicesHelper
import com.google.firebase.firestore.SetOptions
import com.example.sharkmentor.data.repository.AgentMemoryRepository
import com.example.sharkmentor.data.repository.AiRouterRepository
import com.example.sharkmentor.data.repository.AutoIntentClassifier
import com.example.sharkmentor.data.repository.NetPredictionRepository
import com.example.sharkmentor.data.repository.NetPredictionRepositoryImpl
import com.example.sharkmentor.data.repository.PlanRepository
import com.example.sharkmentor.data.repository.PlanRepositoryImpl
import com.example.sharkmentor.data.repository.SharkAiCapability
import com.example.sharkmentor.data.repository.SharkAiEngine
import com.example.sharkmentor.data.sync.InstitutionScheduleSyncListener
import com.example.sharkmentor.domain.model.prediction.NetWizardUiState
import com.example.sharkmentor.core.ai.AiProviderChoice
import com.example.sharkmentor.core.ai.GeminiCoreService
import com.example.sharkmentor.core.ai.UserAiPreferences
import com.example.sharkmentor.core.ai.YksCurriculumCatalog
import com.example.sharkmentor.data.repository.AiRouterResult
import com.example.sharkmentor.ui.aichat.AiChatHubUiState
import com.example.sharkmentor.ui.aichat.AiChatMessage
import com.example.sharkmentor.ui.aichat.AiPersona
import com.example.sharkmentor.ui.aichat.PersistedChatSession
import com.example.sharkmentor.ui.aichat.PersistedChatSessionSnapshot
import com.example.sharkmentor.ui.aichat.SharkAiChatSessionStore
import com.example.sharkmentor.ui.aichat.isPersonaAvailable
import com.example.sharkmentor.ui.screens.SelectedAttachmentPreview
import com.google.gson.Gson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class SharkAiHandoffRequest(
    val imageUri: Uri?,
    val promptText: String,
    val targetEngine: SharkAiEngine
)

/**
 * Coordinates seamless cross-screen handoff from ErrorBankScreen ("Soru Çözücü" / "İkiz Soru")
 * into SharkAiScreen so the standalone AI engine triggers automatically.
 */
object SharkAiHandoffManager {
    private val _pendingHandoff = MutableStateFlow<SharkAiHandoffRequest?>(null)
    val pendingHandoff: StateFlow<SharkAiHandoffRequest?> = _pendingHandoff.asStateFlow()

    fun enqueueFromErrorQuestion(
        imagePath: String?,
        subject: String,
        topic: String?,
        targetEngine: SharkAiEngine
    ) {
        val resolvedUri = imagePath?.takeIf { it.isNotBlank() }?.let { raw ->
            if (raw.startsWith("content://") || raw.startsWith("file://")) {
                Uri.parse(raw)
            } else {
                val file = File(raw)
                if (file.exists()) Uri.fromFile(file) else Uri.parse(raw)
            }
        }
        val topicLabel = topic?.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: ""
        val prompt = when (targetEngine) {
            SharkAiEngine.SOLVER_ENGINE ->
                "$subject$topicLabel sorusunu adım adım çöz."
            SharkAiEngine.TWIN_QUESTION_ENGINE ->
                "$subject$topicLabel sorusuna paralel ÖSYM tarzı özgün bir ikiz soru (A-E şıklı) üret ve çözümünü paylaş."
            else ->
                "$subject$topicLabel sorusunu incele."
        }
        _pendingHandoff.value = SharkAiHandoffRequest(
            imageUri = resolvedUri,
            promptText = prompt,
            targetEngine = targetEngine
        )
    }

    fun consumePending(): SharkAiHandoffRequest? {
        val current = _pendingHandoff.value
        _pendingHandoff.value = null
        return current
    }
}

open class SharkAiViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private val CHAT_ACTION_LINE_REGEX = Regex("#ACTION:.*")
        private val STREAM_ACTION_BLOCK_REGEX = Regex("#ACTION:(\\{.*?\\})#", RegexOption.DOT_MATCHES_ALL)
        private val STREAM_ACTION_TAIL_REGEX = Regex("#ACTION:.*", RegexOption.DOT_MATCHES_ALL)
        private val JSON_FENCE_COMPLETE_REGEX = Regex("```json[\\s\\S]*?```", RegexOption.IGNORE_CASE)
        private val JSON_FENCE_OPEN_REGEX = Regex("```json[\\s\\S]*", RegexOption.IGNORE_CASE)
        private val CODE_FENCE_COMPLETE_REGEX = Regex("```[\\s\\S]*?```")
        private val CODE_FENCE_OPEN_REGEX = Regex("```(?:json)?\\s*", RegexOption.IGNORE_CASE)
        private val NON_ALNUM_TR_REGEX = Regex("[^a-z0-9çğıöşü\\s]")
        private val MULTI_SPACE_REGEX = Regex("\\s+")
        private val OPTION_PAREN_REGEX = Regex("""\b[a-eA-E]\)""")
        private val OPTION_WORD_REGEX = Regex("""\b[a-eA-E]\s*şıkkı""")
        private val RESOURCE_MATCH_STOPWORDS = setOf(
            "soru", "bankası", "bankasi", "deneme", "denemesi", "denemeleri",
            "kitap", "kitabı", "kitabi", "yayınları", "yayinlari", "yayın", "yayin",
            "fasikül", "fasikul", "fasikülleri", "konu", "anlatımı", "anlatimi",
            "test", "sayfa", "tyt", "ayt", "yks", "lgs", "ve", "ile", "icin", "için", "serisi",
            "matematik", "geometri", "fizik", "kimya", "biyoloji",
            "türkçe", "turkce", "edebiyat", "tarih", "coğrafya", "cografya",
            "felsefe", "din", "ingilizce", "genel", "ders"
        )
    }

    private val gson = Gson()
    private val userAiPrefs = UserAiPreferences.initialize(application)
    private val database = SharkMentorDatabase.getDatabase(application)
    private val aiRouterRepository = AiRouterRepository(application.applicationContext, database)
    private val geminiCoreService = GeminiCoreService(application.applicationContext)
    private val repository = PlanRepository(
        database.targetDao(),
        database.sessionDao(),
        database.planFolderDao()
    )
    private val planRepositoryImpl = PlanRepositoryImpl(
        database.planDao(),
        database.pendingDeletionDao()
    )
    private val memoryRepository = AgentMemoryRepository(database.agentMemoryDao())
    private val netPredictionRepository: NetPredictionRepository = NetPredictionRepositoryImpl(
        netProjectionDao = database.netProjectionDao(),
        examNetDao = database.monthlyExamNetDao(),
        goalsDao = database.goalsDao(),
        concreteTargetDao = database.concreteTargetDao()
    )
    private val institutionSyncListener = InstitutionScheduleSyncListener(
        database.institutionScheduleDao()
    )

    private val initialSessionSnapshot = SharkAiChatSessionStore.restoreSession(application)

    private val _chatSessions = MutableStateFlow(initialSessionSnapshot.sessions)
    val chatSessions: StateFlow<List<PersistedChatSession>> = _chatSessions.asStateFlow()

    private val _activeChatSessionId = MutableStateFlow(initialSessionSnapshot.activeSessionId)
    val activeChatSessionId: StateFlow<String> = _activeChatSessionId.asStateFlow()

    private val _inputText = MutableStateFlow(initialSessionSnapshot.draftInputText)
    val inputText: StateFlow<String> = _inputText.asStateFlow()

    private val _selectedAttachmentUri = MutableStateFlow<Uri?>(null)
    val selectedAttachmentUri: StateFlow<Uri?> = _selectedAttachmentUri.asStateFlow()

    private val _attachmentPreview = MutableStateFlow<SelectedAttachmentPreview?>(null)
    val attachmentPreview: StateFlow<SelectedAttachmentPreview?> = _attachmentPreview.asStateFlow()

    private val _activeModelDisplay = MutableStateFlow(userAiPrefs.getActiveDisplayText())
    val activeModelDisplay: StateFlow<String> = _activeModelDisplay.asStateFlow()

    private val _activeCapability = MutableStateFlow<SharkAiCapability?>(null)
    val activeCapability: StateFlow<SharkAiCapability?> = _activeCapability.asStateFlow()

    private val _detectedEngine = MutableStateFlow(SharkAiEngine.CONVERSATIONAL_PEER)
    val detectedEngine: StateFlow<SharkAiEngine> = _detectedEngine.asStateFlow()

    private val _lockedEngine = MutableStateFlow<SharkAiEngine?>(initialSessionSnapshot.lockedEngine)
    val lockedEngine: StateFlow<SharkAiEngine?> = _lockedEngine.asStateFlow()

    private val _isRefreshingNetProjection = MutableStateFlow(false)
    val isRefreshingNetProjection: StateFlow<Boolean> = _isRefreshingNetProjection.asStateFlow()

    enum class PendingDossierActionType {
        SYNC_NOW,
        EDIT_NOTE
    }

    data class PendingDossierAction(
        val type: PendingDossierActionType,
        val title: String,
        val description: String,
        val rawNote: String = ""
    )

    private val _pendingDossierAction = MutableStateFlow<PendingDossierAction?>(null)
    val pendingDossierAction: StateFlow<PendingDossierAction?> = _pendingDossierAction.asStateFlow()

    data class PendingPlanRebuildRequest(
        val userInput: String,
        val attachmentUri: Uri? = null,
        val attachmentName: String? = null,
        val capabilityOverride: SharkAiCapability? = null,
        val engineOverride: SharkAiEngine? = null
    )

    private val _pendingPlanRebuildRequest = MutableStateFlow<PendingPlanRebuildRequest?>(null)
    val pendingPlanRebuildRequest: StateFlow<PendingPlanRebuildRequest?> = _pendingPlanRebuildRequest.asStateFlow()

    fun confirmPendingPlanRebuild() {
        val pending = _pendingPlanRebuildRequest.value ?: return
        _pendingPlanRebuildRequest.value = null
        sendMessage(
            userInput = pending.userInput,
            attachmentUri = pending.attachmentUri,
            attachmentName = pending.attachmentName,
            capabilityOverride = pending.capabilityOverride,
            engineOverride = pending.engineOverride ?: SharkAiEngine.PLANNER_OR_ORGANIZER,
            bypassReadinessCheck = true,
            appendUserMessage = false
        )
    }

    fun cancelPendingPlanRebuild() {
        if (_pendingPlanRebuildRequest.value == null) return
        _pendingPlanRebuildRequest.value = null
        val aiMsg = AiChatMessage(
            sender = "ai",
            text = "Tamamdır, konu hakimiyetini ve kitaplarını dilediğin zaman güncelleyip planını yeniden kurgulayabilirsin.",
            personaTag = _uiState.value.currentPersona.id
        )
        _uiState.value = _uiState.value.copy(messages = _uiState.value.messages + aiMsg, isLoading = false)
        persistCurrentChatState()
    }

    private fun normalizeFlexibleTimeSlot(raw: String?): String {
        val clean = raw?.trim().orEmpty()
        if (clean.isBlank()) return "Esnek • 90 dk"
        val rangeMatch = Regex("""(\d{1,2}):(\d{2})\s*[-–—]\s*(\d{1,2}):(\d{2})""").find(clean)
        if (rangeMatch != null) {
            val h1 = rangeMatch.groupValues[1].toIntOrNull() ?: 9
            val m1 = rangeMatch.groupValues[2].toIntOrNull() ?: 0
            val h2 = rangeMatch.groupValues[3].toIntOrNull() ?: 10
            val m2 = rangeMatch.groupValues[4].toIntOrNull() ?: 30
            val diff = ((h2 * 60 + m2) - (h1 * 60 + m1)).let { if (it in 20..240) it else 90 }
            return "Esnek • $diff dk"
        }
        return clean
    }

    fun confirmPendingDossierAction() {
        val pending = _pendingDossierAction.value ?: return
        _pendingDossierAction.value = null
        val appCtx = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            com.example.sharkmentor.core.coaching.StudentDossierManager.ensureLoaded(appCtx)
            val replyText = when (pending.type) {
                PendingDossierActionType.SYNC_NOW -> {
                    val synced = com.example.sharkmentor.core.coaching.StudentDossierManager.syncFromDatabase(appCtx, force = true)
                    "Öğrenci dosyan güncellendi. Hedeflerin, ${synced.topics.size} konu hakimiyeti kaydın ve ${synced.books.size} kütüphane kaynağın senkronize edildi; silinen kayıtlar temizlendi."
                }
                PendingDossierActionType.EDIT_NOTE -> {
                    val cleanNote = pending.rawNote.trim()
                    if (cleanNote.isNotBlank()) {
                        com.example.sharkmentor.core.coaching.StudentDossierManager.updateSingleAnswerOrInsight(
                            context = appCtx,
                            questionId = null,
                            newValue = cleanNote,
                            asInsight = true
                        )
                    }
                    "Onayınla birlikte bu bilgi anonim Öğrenci Dosyana eklendi. Koçluk ve plan önerilerin artık buna göre şekillenecek."
                }
            }
            val aiMsg = AiChatMessage(
                sender = "ai",
                text = replyText,
                personaTag = _uiState.value.currentPersona.id
            )
            _uiState.value = _uiState.value.copy(messages = _uiState.value.messages + aiMsg, isLoading = false)
            persistCurrentChatState()
        }
    }

    fun cancelPendingDossierAction() {
        if (_pendingDossierAction.value == null) return
        _pendingDossierAction.value = null
        val aiMsg = AiChatMessage(
            sender = "ai",
            text = "Tamamdır, Öğrenci Dosyanda herhangi bir değişiklik yapılmadı.",
            personaTag = _uiState.value.currentPersona.id
        )
        _uiState.value = _uiState.value.copy(messages = _uiState.value.messages + aiMsg, isLoading = false)
        persistCurrentChatState()
    }

    fun completeDossierInterview(answers: Map<String, String>) {
        val appCtx = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            com.example.sharkmentor.core.coaching.StudentDossierManager.saveInterviewAnswers(
                context = appCtx,
                answers = answers,
                markCompleted = true
            )
            val synced = com.example.sharkmentor.core.coaching.StudentDossierManager.syncFromDatabase(appCtx, force = true)
            val wdH = com.example.sharkmentor.core.coaching.CoachingEngine.formatMinutesShort(synced.weekdayGoalMinutes)
            val weH = com.example.sharkmentor.core.coaching.CoachingEngine.formatMinutesShort(synced.weekendGoalMinutes)
            val summaryMsg = AiChatMessage(
                sender = "ai",
                text = "Öğrenci Dosyan başarıyla oluşturuldu! Hedeflerin, ${synced.topics.size} konu hakimiyeti kaydın ve ${synced.books.size} kaynağın anonim profiline işlendi (Hafta içi $wdH / Hafta sonu $weH). Artık Koçluk Yolu ekranından sana özel dengeli planını başlatabilirsin. İstediğin zaman \"öğrenci dosyamı güncelle\" yazarak verilerini yenileyebilirsin.",
                personaTag = AiPersona.SHARK_COACH.id
            )
            _uiState.value = _uiState.value.copy(
                messages = _uiState.value.messages + summaryMsg,
                isLoading = false
            )
            persistCurrentChatState()
        }
    }

    val netWizardState: StateFlow<NetWizardUiState> = netPredictionRepository.getNetProjectionStream()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = NetWizardUiState(isLoading = true)
        )

    private val _uiState = MutableStateFlow(
        AiChatHubUiState(
            messages = initialSessionSnapshot.messages,
            currentPersona = initialSessionSnapshot.currentPersona
        )
    )
    val uiState: StateFlow<AiChatHubUiState> = _uiState.asStateFlow()

    private var activeAiJob: Job? = null
    @Volatile
    private var lastUserPromptText: String = ""

    init {
        observeStateDependencies()
        institutionSyncListener.start()
        refreshAutoDetectedEngine()
        consumePendingHandoffIfAny()
    }

    private fun applySessionSnapshot(snapshot: PersistedChatSessionSnapshot) {
        _chatSessions.value = snapshot.sessions
        _activeChatSessionId.value = snapshot.activeSessionId
        _inputText.value = snapshot.draftInputText
        _lockedEngine.value = snapshot.lockedEngine
        _activeCapability.value = when (snapshot.lockedEngine) {
            SharkAiEngine.SOLVER_ENGINE -> SharkAiCapability.PROBLEM_SOLVER
            SharkAiEngine.TWIN_QUESTION_ENGINE -> SharkAiCapability.TWIN_QUESTION
            SharkAiEngine.PLANNER_OR_ORGANIZER -> SharkAiCapability.ADD_TO_STUDY_PLAN
            else -> null
        }
        _uiState.value = _uiState.value.copy(
            messages = snapshot.messages,
            currentPersona = snapshot.currentPersona,
            isLoading = false,
            error = null
        )
        refreshAutoDetectedEngine()
    }

    private fun persistCurrentChatState() {
        val current = _uiState.value
        val snapshot = SharkAiChatSessionStore.saveMessagesAndPersona(
            context = getApplication(),
            messages = current.messages,
            persona = current.currentPersona
        )
        _chatSessions.value = snapshot.sessions
        _activeChatSessionId.value = snapshot.activeSessionId
    }

    override fun onCleared() {
        super.onCleared()
        institutionSyncListener.stop()
    }

    private fun refreshAutoDetectedEngine() {
        _detectedEngine.value = AutoIntentClassifier.classify(
            text = _inputText.value,
            hasAttachment = _selectedAttachmentUri.value != null
        )
    }

    fun consumePendingHandoffIfAny() {
        val handoff = SharkAiHandoffManager.consumePending() ?: return
        _lockedEngine.value = handoff.targetEngine
        val snapshot = SharkAiChatSessionStore.saveDraftState(getApplication(), _inputText.value, _lockedEngine.value)
        _chatSessions.value = snapshot.sessions
        sendMessage(
            userInput = handoff.promptText,
            attachmentUri = handoff.imageUri,
            attachmentName = handoff.imageUri?.lastPathSegment ?: "Hata Kumbarası Sorusu",
            engineOverride = handoff.targetEngine
        )
    }

    fun onInputTextChanged(newText: String) {
        _inputText.value = newText
        val snapshot = SharkAiChatSessionStore.saveDraftState(getApplication(), newText, _lockedEngine.value)
        _chatSessions.value = snapshot.sessions
        refreshAutoDetectedEngine()
    }

    fun onAttachmentSelected(uri: Uri?) {
        _selectedAttachmentUri.value = uri
        refreshAutoDetectedEngine()
        if (uri == null) {
            _attachmentPreview.value = null
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val preview = buildAttachmentPreview(
                context = getApplication(),
                resolver = getApplication<Application>().contentResolver,
                uri = uri
            )
            _attachmentPreview.value = preview
        }
    }

    fun clearAttachment() {
        _selectedAttachmentUri.value = null
        _attachmentPreview.value = null
        _activeCapability.value = null
        _lockedEngine.value = null
        val snapshot = SharkAiChatSessionStore.saveDraftState(getApplication(), _inputText.value, null)
        _chatSessions.value = snapshot.sessions
        refreshAutoDetectedEngine()
    }

    fun setActiveCapability(capability: SharkAiCapability?) {
        _activeCapability.value = capability
        _lockedEngine.value = SharkAiEngine.fromCapability(capability)
        val snapshot = SharkAiChatSessionStore.saveDraftState(getApplication(), _inputText.value, _lockedEngine.value)
        _chatSessions.value = snapshot.sessions
    }

    fun setLockedEngine(engine: SharkAiEngine?) {
        _lockedEngine.value = engine
        _activeCapability.value = when (engine) {
            SharkAiEngine.SOLVER_ENGINE -> SharkAiCapability.PROBLEM_SOLVER
            SharkAiEngine.TWIN_QUESTION_ENGINE -> SharkAiCapability.TWIN_QUESTION
            SharkAiEngine.PLANNER_OR_ORGANIZER -> SharkAiCapability.ADD_TO_STUDY_PLAN
            else -> null
        }
        val snapshot = SharkAiChatSessionStore.saveDraftState(getApplication(), _inputText.value, engine)
        _chatSessions.value = snapshot.sessions
    }

    /**
     * Starts a fresh chat session ("Yeni Sohbet"), clears displayed messages, resets draft/attachment/engine,
     * and resets an unused session in place instead of creating empty duplicates.
     */
    fun startNewChat() {
        activeAiJob?.cancel()
        activeAiJob = null
        _selectedAttachmentUri.value = null
        _attachmentPreview.value = null
        _activeCapability.value = null
        _lockedEngine.value = null
        _inputText.value = ""
        val snapshot = SharkAiChatSessionStore.createNewSession(getApplication())
        applySessionSnapshot(snapshot)
        _uiState.value = _uiState.value.copy(
            messages = emptyList(),
            currentPersona = AiPersona.SHARK_COACH,
            isLoading = false,
            error = null
        )
        refreshAutoDetectedEngine()
    }

    /**
     * Switches active chat session to [sessionId].
     */
    fun selectChatSession(sessionId: String) {
        if (sessionId == _activeChatSessionId.value && !_uiState.value.isLoading) return
        activeAiJob?.cancel()
        activeAiJob = null
        _selectedAttachmentUri.value = null
        _attachmentPreview.value = null
        val snapshot = SharkAiChatSessionStore.selectSession(getApplication(), sessionId)
        applySessionSnapshot(snapshot)
    }

    /**
     * Deletes a saved chat session by [sessionId]. If it was active, switches to the latest remaining
     * session or creates a fresh default session automatically.
     */
    fun deleteChatSession(sessionId: String) {
        if (sessionId == _activeChatSessionId.value) {
            activeAiJob?.cancel()
            activeAiJob = null
            _selectedAttachmentUri.value = null
            _attachmentPreview.value = null
        }
        val snapshot = SharkAiChatSessionStore.deleteSession(getApplication(), sessionId)
        applySessionSnapshot(snapshot)
    }

    /**
     * Triggers or locks the standalone `SolverEngine` WITHOUT injecting fake text prefixes into `_inputText`.
     */
    fun triggerProblemSolver(
        userInput: String = _inputText.value,
        attachmentUri: Uri? = _selectedAttachmentUri.value
    ) {
        setLockedEngine(SharkAiEngine.SOLVER_ENGINE)
        if (userInput.isNotBlank() || attachmentUri != null) {
            sendMessage(
                userInput = userInput,
                attachmentUri = attachmentUri,
                attachmentName = _attachmentPreview.value?.displayName,
                capabilityOverride = SharkAiCapability.PROBLEM_SOLVER,
                engineOverride = SharkAiEngine.SOLVER_ENGINE
            )
        }
    }

    /**
     * Triggers or locks the standalone `TwinQuestionEngine` WITHOUT injecting fake text prefixes into `_inputText`.
     */
    fun triggerTwinQuestionGenerator(
        userInput: String = _inputText.value,
        attachmentUri: Uri? = _selectedAttachmentUri.value
    ) {
        setLockedEngine(SharkAiEngine.TWIN_QUESTION_ENGINE)
        if (userInput.isNotBlank() || attachmentUri != null) {
            sendMessage(
                userInput = userInput,
                attachmentUri = attachmentUri,
                attachmentName = _attachmentPreview.value?.displayName,
                capabilityOverride = SharkAiCapability.TWIN_QUESTION,
                engineOverride = SharkAiEngine.TWIN_QUESTION_ENGINE
            )
        }
    }

    /**
     * Triggers or locks the standalone `AutonomousPlannerEngine` WITHOUT injecting fake text prefixes into `_inputText`.
     */
    fun triggerAddToStudyPlan(
        userInput: String = _inputText.value,
        attachmentUri: Uri? = _selectedAttachmentUri.value
    ) {
        setLockedEngine(SharkAiEngine.PLANNER_OR_ORGANIZER)
        if (userInput.isNotBlank() || attachmentUri != null) {
            sendMessage(
                userInput = userInput,
                attachmentUri = attachmentUri,
                attachmentName = _attachmentPreview.value?.displayName,
                capabilityOverride = SharkAiCapability.ADD_TO_STUDY_PLAN,
                engineOverride = SharkAiEngine.PLANNER_OR_ORGANIZER
            )
        }
    }

    fun refreshNetProjection() {
        viewModelScope.launch(Dispatchers.IO) {
            _isRefreshingNetProjection.value = true
            netPredictionRepository.refreshProjection()
            _isRefreshingNetProjection.value = false
        }
    }

    private fun observeStateDependencies() {
        viewModelScope.launch(Dispatchers.IO) {
            SessionManager.isLinkedToInstitution.collect { linked ->
                _uiState.value = _uiState.value.copy(isInstitutional = linked)
            }
        }
        viewModelScope.launch {
            merge(
                userAiPrefs.userGeminiKeys,
                userAiPrefs.selectedUserGeminiModel,
                userAiPrefs.userDeepSeekKey,
                userAiPrefs.selectedDeepSeekModel,
                userAiPrefs.userKimiKey,
                userAiPrefs.selectedKimiModel,
                userAiPrefs.userClaudeKey,
                userAiPrefs.selectedClaudeModel,
                userAiPrefs.userOpenAiKey,
                userAiPrefs.selectedOpenAiModel,
                userAiPrefs.userGrokKey,
                userAiPrefs.selectedGrokModel,
                userAiPrefs.selectedAiProvider
            ).collect {
                _activeModelDisplay.value = userAiPrefs.getActiveDisplayText()
            }
        }
        val prefs = getApplication<Application>().getSharedPreferences("shark_dopamine_prefs", Context.MODE_PRIVATE)
        val updateDopamineState = {
            val dopamineActive = prefs.getBoolean("is_active", false)
            _uiState.value = _uiState.value.copy(isDopamineActive = dopamineActive)
        }
        updateDopamineState()
        prefs.registerOnSharedPreferenceChangeListener { _, key ->
            if (key == "is_active") {
                updateDopamineState()
            }
        }
    }

    fun selectUnifiedMode(
        persona: AiPersona = AiPersona.SHARK_COACH,
        engine: SharkAiEngine? = null
    ): Pair<Boolean, String?> {
        val (available, reason) = switchPersona(persona)
        if (!available) {
            return false to reason
        }
        setLockedEngine(engine)
        return true to null
    }

    fun switchPersona(newPersona: AiPersona): Pair<Boolean, String?> {
        val prefs = getApplication<Application>().getSharedPreferences("shark_dopamine_prefs", Context.MODE_PRIVATE)
        val dopamineActive = prefs.getBoolean("is_active", false)
        _uiState.value = _uiState.value.copy(isDopamineActive = dopamineActive)

        val current = _uiState.value
        val (available, reason) = isPersonaAvailable(newPersona, current.isDopamineActive, current.isInstitutional)
        if (!available) {
            return false to reason
        }

        if (current.currentPersona == newPersona) return true to null

        val newMessages = current.messages + AiChatMessage(
            sender = "system",
            text = "${newPersona.title} moduna geçildi.",
            personaTag = newPersona.id
        )

        _uiState.value = current.copy(
            currentPersona = newPersona,
            messages = newMessages,
            error = null
        )
        persistCurrentChatState()
        return true to null
    }

    fun resetToDefaultPersona() {
        setLockedEngine(null)
        val current = _uiState.value
        if (current.currentPersona == AiPersona.SHARK_COACH) return

        val newMessages = current.messages + AiChatMessage(
            sender = "system",
            text = "Shark Sınav Koçu moduna dönüldü.",
            personaTag = AiPersona.SHARK_COACH.id
        )

        _uiState.value = current.copy(
            currentPersona = AiPersona.SHARK_COACH,
            messages = newMessages,
            error = null
        )
        persistCurrentChatState()
    }

    fun stopGeneration() {
        activeAiJob?.cancel()
        _uiState.value = _uiState.value.copy(isLoading = false)
        persistCurrentChatState()
    }

    fun deleteMessage(messageId: String) {
        val currentMessages = _uiState.value.messages
        _uiState.value = _uiState.value.copy(
            messages = currentMessages.filter { it.id != messageId }
        )
        persistCurrentChatState()
    }

    fun sendMessage(
        userInput: String = _inputText.value,
        attachmentUri: Uri? = _selectedAttachmentUri.value,
        attachmentName: String? = _attachmentPreview.value?.displayName,
        capabilityOverride: SharkAiCapability? = _activeCapability.value,
        engineOverride: SharkAiEngine? = _lockedEngine.value,
        bypassReadinessCheck: Boolean = false,
        appendUserMessage: Boolean = true
    ) {
        val text = userInput.trim()
        if ((text.isBlank() && attachmentUri == null) || _uiState.value.isLoading) return

        lastUserPromptText = text
        val resolvedAttachmentName = attachmentName ?: attachmentUri?.let { resolveDisplayName(it) }
        val currentPersona = _uiState.value.currentPersona

        // Automatic Mode Detection (`AutoIntentClassifier`) unless manually locked/overridden
        val resolvedEngine: SharkAiEngine = engineOverride
            ?: SharkAiEngine.fromCapability(capabilityOverride)
            ?: if (currentPersona == AiPersona.STUDY_PLANNER) {
                SharkAiEngine.PLANNER_OR_ORGANIZER
            } else {
                AutoIntentClassifier.classify(text, hasAttachment = attachmentUri != null)
            }

        _detectedEngine.value = resolvedEngine

        val displayMessageText = when {
            text.isNotBlank() -> text
            resolvedAttachmentName != null -> resolvedAttachmentName
            else -> "Ekli Dosya"
        }

        val userMsg = AiChatMessage(
            sender = "user",
            text = displayMessageText,
            personaTag = currentPersona.id,
            attachmentUri = attachmentUri,
            attachmentName = resolvedAttachmentName
        )

        _inputText.value = ""
        _selectedAttachmentUri.value = null
        _attachmentPreview.value = null
        _activeCapability.value = null
        _lockedEngine.value = null
        SharkAiChatSessionStore.saveDraftState(getApplication(), "", null)
        refreshAutoDetectedEngine()

        val nextMessages = if (appendUserMessage) {
            _uiState.value.messages + userMsg
        } else {
            _uiState.value.messages
        }
        _uiState.value = _uiState.value.copy(
            messages = nextMessages,
            isLoading = true,
            error = null
        )
        persistCurrentChatState()

        // Explicit confirmation gate for Student Dossier sync ("öğrenci dosyamı güncelle") or edit commands
        if (attachmentUri == null && text.isNotBlank()) {
            val isSyncCmd = com.example.sharkmentor.core.coaching.StudentDossierManager.isDossierSyncCommand(text)
            val isEditCmd = !isSyncCmd && com.example.sharkmentor.core.coaching.StudentDossierManager.isDossierEditCommand(text)
            if (isSyncCmd || isEditCmd) {
                val action = if (isSyncCmd) {
                    PendingDossierAction(
                        type = PendingDossierActionType.SYNC_NOW,
                        title = "Öğrenci Dosyasını Güncelle",
                        description = "Mevcut hedeflerin, tüm konu hakimiyeti kayıtların ve kütüphanendeki kitapların güncel hali Öğrenci Dosyana aktarılacak; sildiğin kayıtlar dosyadan temizlenecek. Onaylıyor musun?"
                    )
                } else {
                    PendingDossierAction(
                        type = PendingDossierActionType.EDIT_NOTE,
                        title = "Öğrenci Dosyasını Düzenle",
                        description = "Aşağıdaki isteğin anonim Öğrenci Dosyana koçluk notu olarak işlenecek:\n\n\"${text.take(220)}\"\n\nBu değişikliği onaylıyor musun?",
                        rawNote = text
                    )
                }
                _pendingDossierAction.value = action
                val confirmPrompt = AiChatMessage(
                    sender = "ai",
                    text = if (isSyncCmd) {
                        "Öğrenci Dosyanı güncel hedefler, konu hakimiyeti ve kitap listesiyle eşitlemem için açılan pencereden onay vermen yeterli."
                    } else {
                        "Öğrenci Dosyanda değişiklik yapmadan önce onayını istiyorum. Açılan pencereden onaylayabilir veya iptal edebilirsin."
                    },
                    personaTag = currentPersona.id
                )
                _uiState.value = _uiState.value.copy(
                    messages = _uiState.value.messages + confirmPrompt,
                    isLoading = false
                )
                persistCurrentChatState()
                return
            }
        }

        // Pre-plan readiness check-in card whenever student requests building/rebuilding a plan in chat
        if (!bypassReadinessCheck &&
            attachmentUri == null &&
            text.isNotBlank() &&
            resolvedEngine != SharkAiEngine.SOLVER_ENGINE &&
            resolvedEngine != SharkAiEngine.TWIN_QUESTION_ENGINE &&
            com.example.sharkmentor.core.coaching.PlanProgressAuditor.isPlanRebuildOrCreateIntent(text)
        ) {
            _pendingPlanRebuildRequest.value = PendingPlanRebuildRequest(
                userInput = text,
                attachmentUri = attachmentUri,
                attachmentName = resolvedAttachmentName,
                capabilityOverride = capabilityOverride,
                engineOverride = engineOverride
            )
            val readinessPrompt = AiChatMessage(
                sender = "ai",
                text = "Yeni planını eksiklerini devredip tamamladıklarını tekrar etmeden en sağlıklı şekilde kurgulayabilmem için açılan karttan konu hakimiyetini ve kitaplarında çözdüğün noktaları son haline getirmeni rica ediyorum. Onayladığında planını anlık durumuna göre oluşturacağım.",
                personaTag = currentPersona.id
            )
            _uiState.value = _uiState.value.copy(
                messages = _uiState.value.messages + readinessPrompt,
                isLoading = false
            )
            persistCurrentChatState()
            return
        }

        val streamingMsgId = UUID.randomUUID().toString()
        val originatingSessionId = _activeChatSessionId.value

        activeAiJob?.cancel()
        activeAiJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val appCtx = getApplication<Application>()
                val (memorySnapshot, concrete, shortTargets, longTargets, allResources, allTopics, schedules, planAudit) = coroutineScope {
                    val memoryDef = async { memoryRepository.buildSemanticSnapshot() }
                    val concreteDef = async { database.concreteTargetDao().getTargetRaw() }
                    val shortDef = async { database.targetDao().getTargetsWithTasksSync(TargetType.SHORT_TERM) }
                    val longDef = async { database.targetDao().getTargetsWithTasksSync(TargetType.LONG_TERM) }
                    val resourcesDef = async { database.goalsDao().getAllResourcesRaw() }
                    val topicsDef = async { database.goalsDao().getAllTopicsRaw() }
                    val schedulesDef = async { database.institutionScheduleDao().getAllSchedules() }
                    val planAuditDef = async {
                        com.example.sharkmentor.core.coaching.PlanProgressAuditor.captureFromDatabase(appCtx)
                    }
                    ContextSnapshotBundle(
                        memorySnapshot = memoryDef.await(),
                        concrete = concreteDef.await(),
                        shortTargets = shortDef.await(),
                        longTargets = longDef.await(),
                        resources = resourcesDef.await(),
                        topics = topicsDef.await(),
                        schedules = schedulesDef.await(),
                        planAudit = planAuditDef.await()
                    )
                }

                val targetInfo = if (concrete != null) {
                    "${concrete.targetTitle} (${concrete.targetScoreOrRank})"
                } else "Girilmedi"
                val allTargets = (shortTargets + longTargets).map { it.target }
                val targetSummary = if (allTargets.isNotEmpty()) {
                    allTargets.take(8).joinToString(" | ") { t ->
                        "id=${t.id} \"${t.title}\" (${t.type.name}, ${t.solvedQuestions}/${t.targetQuestions} Soru, %${(t.progress * 100).toInt()})"
                    }
                } else "Aktif hedef yok"

                val librarySummary = if (allResources.isNotEmpty()) {
                    allResources.take(30).joinToString(" | ") { r ->
                        val progressLabel = if (r.totalUnits > 0) {
                            "${r.completedUnits}/${r.totalUnits} ${r.unitType}"
                        } else {
                            "${r.completedUnits} ${r.unitType} (Toplam belirtilmedi)"
                        }
                        "id=${r.id} \"${r.title}\" (Ders: ${r.subject}, İlerleme: $progressLabel)"
                    }
                } else "Kütüphanede kayıtlı kitap yok"

                val topicSummary = if (allTopics.isNotEmpty()) {
                    val bySubject = allTopics.groupBy { it.subject }
                        .entries.take(16)
                        .joinToString(" | ") { (subj, items) ->
                            val topSample = items.take(8).joinToString(", ") { "${it.topic} (${it.currentLevel}/${it.targetLevel})" }
                            val suffix = if (items.size > 8) " +${items.size - 8}" else ""
                            "$subj: [$topSample$suffix]"
                        }
                    "Toplam ${allTopics.size} alt konu -> $bySubject"
                } else "Henüz konu eklenmedi"

                val userStatsAndLibrarySnapshot = """
                - Net/Sıralama Hedefi: $targetInfo
                - [MEVCUT KÜTÜPHANE KAYNAKLARI (resources)]: $librarySummary
                - [MEVCUT HEDEFLER (targets)]: $targetSummary
                - [KONU HAKİMİYETİ ÖZETİ]: $topicSummary
                """.trimIndent()

                // Selective anonymized Student Dossier + Live Plan Progress Audit injection
                val dossierChannel = when {
                    resolvedEngine == SharkAiEngine.SOLVER_ENGINE || resolvedEngine == SharkAiEngine.TWIN_QUESTION_ENGINE ->
                        com.example.sharkmentor.core.coaching.DossierAiChannel.OTHER_NON_COACHING
                    resolvedEngine == SharkAiEngine.PLANNER_OR_ORGANIZER || currentPersona == AiPersona.STUDY_PLANNER ->
                        com.example.sharkmentor.core.coaching.DossierAiChannel.PLAN_ADD_AI
                    currentPersona == AiPersona.ZEN_CALM ->
                        com.example.sharkmentor.core.coaching.DossierAiChannel.ZEN_COACH
                    currentPersona == AiPersona.SHARK_COACH || currentPersona == AiPersona.GOAL_SETTER ->
                        if (com.example.sharkmentor.core.coaching.StudentDossierManager.isCoachingPlanOrZenIntent(text)) {
                            com.example.sharkmentor.core.coaching.DossierAiChannel.COACHING_DIRECT
                        } else {
                            com.example.sharkmentor.core.coaching.DossierAiChannel.AUTO_CHAT
                        }
                    else -> com.example.sharkmentor.core.coaching.DossierAiChannel.AUTO_CHAT
                }
                com.example.sharkmentor.core.coaching.StudentDossierManager.ensureLoaded(appCtx)
                val shouldAttachDossier = com.example.sharkmentor.core.coaching.StudentDossierManager.shouldAttachDossier(
                    channel = dossierChannel,
                    userMessage = text
                )
                val dossierAndAuditBlock = if (shouldAttachDossier) {
                    val baseDossier = com.example.sharkmentor.core.coaching.StudentDossierManager.dossier.value
                    val liveDossier = if (planAudit.hasAnyTasks) {
                        baseDossier.copy(
                            activePlanTitle = planAudit.folderTitle,
                            completedPlanTasks = planAudit.completedTasks.take(60).map { item ->
                                com.example.sharkmentor.core.coaching.DossierPlanTaskSnapshot(
                                    dayOfWeek = item.dayOfWeek,
                                    subject = item.subject,
                                    topic = item.topic,
                                    targetQuestions = item.targetQuestions,
                                    isCompleted = true
                                )
                            },
                            incompletePlanTasks = planAudit.incompleteTasks.take(60).map { item ->
                                com.example.sharkmentor.core.coaching.DossierPlanTaskSnapshot(
                                    dayOfWeek = item.dayOfWeek,
                                    subject = item.subject,
                                    topic = item.topic,
                                    targetQuestions = item.targetQuestions,
                                    isCompleted = false
                                )
                            }
                        )
                    } else baseDossier
                    "\n\n" + com.example.sharkmentor.core.coaching.StudentDossierManager.buildAnonymizedDossierPrompt(liveDossier)
                } else if (
                    dossierChannel != com.example.sharkmentor.core.coaching.DossierAiChannel.OTHER_NON_COACHING &&
                    planAudit.hasAnyTasks &&
                    (dossierChannel == com.example.sharkmentor.core.coaching.DossierAiChannel.PLAN_ADD_AI ||
                        com.example.sharkmentor.core.coaching.StudentDossierManager.isCoachingPlanOrZenIntent(text))
                ) {
                    "\n\n" + com.example.sharkmentor.core.coaching.PlanProgressAuditor.buildAiPlanAuditPrompt(planAudit)
                } else {
                    ""
                }

                val projectionReport = if (currentPersona == AiPersona.GOAL_SETTER) {
                    val wizardState = netWizardState.value
                    val tyt = wizardState.tytProjection
                    val ayt = wizardState.aytProjection
                    """
                    [NET TAHMİN SİHİRBAZI RAPORU]
                    - TYT Mevcut: ${tyt?.currentAverageNet ?: 0f} | Hedef: ${tyt?.targetNet ?: 0f} | Projeksiyon: ${tyt?.projectedNet ?: 0f} (Açık: ${tyt?.netGap ?: 0f})
                    - AYT Mevcut: ${ayt?.currentAverageNet ?: 0f} | Hedef: ${ayt?.targetNet ?: 0f} | Projeksiyon: ${ayt?.projectedNet ?: 0f} (Açık: ${ayt?.netGap ?: 0f})
                    - Kritik Eksik Konular: ${tyt?.topPriorityTopics?.joinToString { "${it.subjectName}: ${it.topicName} (+${it.potentialNetBoost} Net)" } ?: "Yok"}
                    TALİMAT: Net tahminlerini kafadan uydurma. Bu rapordaki projeksiyon ve açık değerlerini referans alarak öğrenciye stratejik yol göster.
                    """.trimIndent()
                } else ""

                val scheduleConstraintPrompt = if (schedules.isNotEmpty()) {
                    val formattedSlots = schedules.groupBy { it.dayOfWeek }.map { (day, classes) ->
                        val busyTimes = classes.joinToString(", ") { "${it.subject} (${it.startTime}-${it.endTime})" }
                        "$day: KİLİTLİ OKUL SAATLERİ: [$busyTimes]"
                    }.joinToString("\n")
                    "\n\nÖĞRENCİNİN SABİT OKUL/DERSHANE PROGRAMI:\n$formattedSlots\nUYARI: Bu saatlerde öğrenci okulda/derstedir. ASLA bu saatlere çalışma planı yazma. Ders bitiminden (örn. 13:00) 30 dk sonra (13:30) çalışma başlatabilirsin."
                } else ""

                val attachmentContext = if (attachmentUri != null) {
                    val mime = getApplication<Application>().contentResolver.getType(attachmentUri) ?: "application/octet-stream"
                    "\n[EKLİ DOSYA: ${resolvedAttachmentName ?: attachmentUri.lastPathSegment} (MIME: $mime)]"
                } else ""

                // ── ROLLING CONTEXT WINDOW (Sohbet Geçmişi: Son 6 Mesaj / 3 Tur) ──
                val historyCandidates = _uiState.value.messages
                    .filter { (it.sender == "user" || it.sender == "ai") && it.id != streamingMsgId }
                    .takeLast(6)

                val openAiHistory = if (attachmentUri == null) {
                    historyCandidates.map { msg ->
                        val cleanContent = msg.text
                            .replace(CHAT_ACTION_LINE_REGEX, "")
                            .replace(JSON_FENCE_COMPLETE_REGEX, "")
                            .trim()
                            .take(500)
                        com.example.sharkmentor.core.ai.OpenAiChatMessage(
                            role = if (msg.sender == "user") "user" else "assistant",
                            content = if (msg.attachmentUri != null && cleanContent.isBlank()) "[Ekli Belge/Görsel: ${msg.attachmentName ?: "Dosya"}]" else cleanContent
                        )
                    }
                } else {
                    emptyList()
                }

                // Multimodal pipeline does not receive structured chatHistory, so include a concise 4-turn snippet only there.
                val conversationHistoryPrompt = if (attachmentUri != null && historyCandidates.isNotEmpty()) {
                    val historyLines = historyCandidates.takeLast(4).joinToString("\n") { msg ->
                        val tag = if (msg.sender == "user") "ÖĞRENCİ" else "SHARK AI"
                        val cleanContent = msg.text
                            .replace(CHAT_ACTION_LINE_REGEX, "")
                            .replace(JSON_FENCE_COMPLETE_REGEX, "")
                            .trim()
                            .take(280)
                        val fileNote = if (msg.attachmentUri != null) " [Ek: ${msg.attachmentName ?: "Görsel"}]" else ""
                        "[$tag]$fileNote: $cleanContent"
                    }
                    "\n\n[SON SOHBET DİYALOĞU GEÇMİŞİ]:\n$historyLines\nTALİMAT: Öğrencinin 'bunu çöz', '2. soru', 'az önceki formül/plan' gibi atıflarını yukarıdaki son konuşulanları referans alarak cevapla."
                } else ""

                val isConversationalPeer = resolvedEngine == SharkAiEngine.CONVERSATIONAL_PEER
                val compressedSystemPrompt = """
                    ${getSystemPromptForPersona(currentPersona, includeActionInstructions = isConversationalPeer, includeMandatoryRules = isConversationalPeer)}

                    [SİSTEMİK KİMLİK: SharkMentor Otonom Hub - Motor: ${resolvedEngine.displayName} - Mod: ${currentPersona.title}]
                    [ÖĞRENCİ, KÜTÜPHANE VE HEDEF BAĞLAMI]:
                    $userStatsAndLibrarySnapshot$dossierAndAuditBlock
                    [AKTİF HAFIZA: $memorySnapshot]$attachmentContext
                    $projectionReport$scheduleConstraintPrompt$conversationHistoryPrompt
                    Talimat: Öğrencinin mevcut kütüphane kitaplarına, ders ve konu eşleşmelerine, hedeflerine, tamamlanan/eksik kalan plan görevlerine ve geçmiş kararlarına sadık kal. Mevcut bir kitabın ilerlemesi söylendiğinde yeni kitap açma, UPDATE_RESOURCE_PROGRESS ile doğru `resourceId`, tam kitap adı ve dersini kullan. Kullanıcı toplam sayfa/test sayısını belirtmediyse asla `totalUnits` uydurma (`0` bırak).
                """.trimIndent()

                val effectivePrompt = if (attachmentUri != null && text.isBlank()) {
                    when (resolvedEngine) {
                        SharkAiEngine.TWIN_QUESTION_ENGINE ->
                            "Eklenen dosyadaki ($resolvedAttachmentName) sorunun kazanımını ve çözüm algoritmasını birebir koruyarak ÖSYM tarzı özgün bir ikiz soru (A-E şıklı) üret ve çözümünü ekle."
                        SharkAiEngine.SOLVER_ENGINE ->
                            "Eklenen dosyadaki ($resolvedAttachmentName) soruyu hatasız oku ve adım adım çöz."
                        else ->
                            "Eklenen dosyayı ($resolvedAttachmentName) incele ve analiz et."
                    }
                } else {
                    text
                }

                var lastUiEmitMs = 0L
                var lastEmittedUiText = ""
                val onStreamChunk: (String) -> Unit = streamCallback@{ partialText ->
                    if (_activeChatSessionId.value != originatingSessionId) {
                        return@streamCallback
                    }
                    // Strip in-progress or completed action tags and markdown json code blocks
                    val cleanPartial = partialText
                        .replace(STREAM_ACTION_BLOCK_REGEX, "")
                        .replace(STREAM_ACTION_TAIL_REGEX, "")
                        .replace(JSON_FENCE_COMPLETE_REGEX, "")
                        .replace(JSON_FENCE_OPEN_REGEX, "")
                        .replace(CODE_FENCE_COMPLETE_REGEX, "")
                        .trim()

                    val displayText = if (cleanPartial.isEmpty() && (partialText.contains("\"schedule\"") || partialText.contains("CREATE_WEEKLY_PLAN"))) {
                        "Haftalık çalışma planın hazırlanıyor..."
                    } else if (cleanPartial.isEmpty() && partialText.contains("INSERT_TOPICS_BATCH")) {
                        "Konu hakimiyeti listesi ekleniyor..."
                    } else if (cleanPartial.startsWith("{") || cleanPartial.startsWith("[")) {
                        "Plan ve görevler hazırlanıyor..."
                    } else {
                        cleanPartial
                    }

                    val now = System.currentTimeMillis()
                    if (displayText.isNotEmpty() && displayText != lastEmittedUiText &&
                        (lastUiEmitMs == 0L || now - lastUiEmitMs >= 45L)
                    ) {
                        lastUiEmitMs = now
                        lastEmittedUiText = displayText
                        val currentList = _uiState.value.messages
                        val existingIdx = currentList.indexOfFirst { it.id == streamingMsgId }
                        val updatedMsg = AiChatMessage(
                            id = streamingMsgId,
                            sender = "ai",
                            text = displayText,
                            personaTag = currentPersona.id
                        )
                        val nextList = if (existingIdx >= 0) {
                            currentList.toMutableList().apply { set(existingIdx, updatedMsg) }
                        } else {
                            currentList + updatedMsg
                        }
                        _uiState.value = _uiState.value.copy(
                            messages = nextList,
                            isLoading = false
                        )
                    }
                }

                // Hybrid Multi-Provider Routing:
                // - Multimodal/PDF/Images route through Vision pipeline.
                // - Text-only honors UserAiPreferences.selectedAiProvider across all 6 providers and AUTO.
                val routerResult = if (attachmentUri != null) {
                    val visionContext = when (resolvedEngine) {
                        SharkAiEngine.SOLVER_ENGINE ->
                            "${AiRouterRepository.SOLVER_ENGINE_SYSTEM_PROMPT}\n$compressedSystemPrompt"
                        SharkAiEngine.TWIN_QUESTION_ENGINE ->
                            "${AiRouterRepository.TWIN_QUESTION_SYSTEM_PROMPT}\n$compressedSystemPrompt"
                        SharkAiEngine.PLANNER_OR_ORGANIZER ->
                            "${AiRouterRepository.AUTONOMOUS_PLANNER_SYSTEM_PROMPT}\n$compressedSystemPrompt"
                        else ->
                            compressedSystemPrompt
                    }
                    aiRouterRepository.executeGeminiVision(
                        prompt = effectivePrompt,
                        systemInstruction = visionContext,
                        attachmentUri = attachmentUri,
                        activeEngine = resolvedEngine,
                        onPartialText = onStreamChunk
                    ).getOrThrow()
                } else {
                    val textInstruction = when (resolvedEngine) {
                        SharkAiEngine.SOLVER_ENGINE ->
                            "${AiRouterRepository.SOLVER_ENGINE_SYSTEM_PROMPT}\n$compressedSystemPrompt"
                        SharkAiEngine.TWIN_QUESTION_ENGINE ->
                            "${AiRouterRepository.TWIN_QUESTION_SYSTEM_PROMPT}\n$compressedSystemPrompt"
                        SharkAiEngine.PLANNER_OR_ORGANIZER ->
                            "${AiRouterRepository.AUTONOMOUS_PLANNER_SYSTEM_PROMPT}\n$compressedSystemPrompt"
                        SharkAiEngine.CONVERSATIONAL_PEER ->
                            compressedSystemPrompt
                    }
                    aiRouterRepository.executeTextStream(
                        activeEngine = resolvedEngine,
                        prompt = effectivePrompt,
                        systemInstruction = textInstruction,
                        chatHistory = openAiHistory,
                        onPartialText = onStreamChunk
                    ).getOrThrow()
                }

                if (_activeChatSessionId.value != originatingSessionId) {
                    return@launch
                }

                handleAiResponse(
                    rawResponse = routerResult.text,
                    toolPlanFolderId = routerResult.createdPlanFolderId,
                    streamingMessageId = streamingMsgId
                )

                // YALNIZCA aktif mod Soru Çözümü veya Benzer Soru ise Antrenman Bankasına otomatik kaydet
                val isExplicitQuestionMode = (resolvedEngine == SharkAiEngine.SOLVER_ENGINE ||
                    resolvedEngine == SharkAiEngine.TWIN_QUESTION_ENGINE ||
                    capabilityOverride == SharkAiCapability.PROBLEM_SOLVER ||
                    capabilityOverride == SharkAiCapability.TWIN_QUESTION) &&
                    resolvedEngine != SharkAiEngine.CONVERSATIONAL_PEER &&
                    resolvedEngine != SharkAiEngine.PLANNER_OR_ORGANIZER

                if (attachmentUri != null && isExplicitQuestionMode) {
                    autoSaveQuestionToErrorBank(
                        attachmentUri = attachmentUri,
                        resolvedAttachmentName = resolvedAttachmentName,
                        userPrompt = effectivePrompt,
                        aiResponseText = routerResult.text
                    )
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                if (_activeChatSessionId.value != originatingSessionId) {
                    return@launch
                }
                if (attachmentUri != null) {
                    Log.e("SharkAiVision", "Vision error: ${e.javaClass.simpleName} - ${e.message}", e)
                } else {
                    Log.e("SharkAiViewModel", "Error calling AI Router: ${e.javaClass.simpleName} - ${e.message}", e)
                }
                val currentMessages = _uiState.value.messages
                val alreadyStreamedMsg = currentMessages.firstOrNull { it.id == streamingMsgId }

                // Eliminate stream resets: never wipe out already-streamed tokens on network blips
                if (alreadyStreamedMsg != null && alreadyStreamedMsg.text.isNotBlank()) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = null
                    )
                    persistCurrentChatState()
                } else {
                    val remainingMessages = currentMessages.filter { it.id != streamingMsgId }
                    val errDetail = e.localizedMessage?.takeIf { it.isNotBlank() } ?: e.message ?: "Bağlantı kurulamadı"
                    val errorMsg = AiChatMessage(
                        sender = "ai",
                        text = if (attachmentUri != null) {
                            "Görsel işlenemedi ($errDetail)."
                        } else {
                            "Bağlantı kurulamadı ($errDetail)."
                        },
                        personaTag = currentPersona.id
                    )
                    _uiState.value = _uiState.value.copy(
                        messages = remainingMessages + errorMsg,
                        isLoading = false
                    )
                    persistCurrentChatState()
                }
            }
        }
    }

    private fun resolveDisplayName(uri: Uri): String {
        val resolver = getApplication<Application>().contentResolver
        runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx != -1 && cursor.moveToFirst()) {
                    val name = cursor.getString(idx)
                    if (!name.isNullOrBlank()) return name
                }
            }
        }
        return uri.lastPathSegment ?: "Dosya"
    }

    private suspend fun buildAttachmentPreview(
        context: Context,
        resolver: ContentResolver,
        uri: Uri
    ): SelectedAttachmentPreview {
        val mimeType = resolver.getType(uri)
        val displayName = resolveDisplayName(uri)
        val isPdf = mimeType == "application/pdf" || displayName.endsWith(".pdf", ignoreCase = true)
        val isImage = mimeType?.startsWith("image/") == true ||
            displayName.endsWith(".png", ignoreCase = true) ||
            displayName.endsWith(".jpg", ignoreCase = true) ||
            displayName.endsWith(".jpeg", ignoreCase = true) ||
            displayName.endsWith(".webp", ignoreCase = true)

        val thumbnail: ImageBitmap? = when {
            isImage -> runCatching {
                ImageStorageManager.prepareOptimizedBitmapForAi(
                    context = context,
                    uri = uri,
                    enhanceContrast = false
                )?.asImageBitmap()
            }.getOrNull()
            isPdf -> runCatching {
                PdfBitmapConverter.convertPdfUriToBitmaps(context, uri, maxPages = 1)
                    .firstOrNull()
                    ?.asImageBitmap()
            }.getOrNull()
            else -> null
        }

        return SelectedAttachmentPreview(
            uri = uri,
            displayName = displayName,
            mimeType = mimeType ?: if (isPdf) "application/pdf" else "image/jpeg",
            thumbnail = thumbnail
        )
    }

    data class ProcessActionResult(
        val cleanText: String,
        val createdPlanFolderId: String? = null,
        val isPlanCard: Boolean = false
    )

    private data class ContextSnapshotBundle(
        val memorySnapshot: String,
        val concrete: ConcreteTargetEntity?,
        val shortTargets: List<TargetWithTasks>,
        val longTargets: List<TargetWithTasks>,
        val resources: List<ResourceEntity>,
        val topics: List<TopicGoalEntity>,
        val schedules: List<InstitutionScheduleEntity>,
        val planAudit: com.example.sharkmentor.core.coaching.PlanAuditSnapshot
    )

    private fun extractActionsFromResponse(rawResponse: String): List<Pair<String, Map<String, Any>>> {
        val results = mutableListOf<Pair<String, Map<String, Any>>>()
        val actionMarker = "#ACTION:"
        var searchIdx = 0

        while (searchIdx < rawResponse.length) {
            val foundIdx = rawResponse.indexOf(actionMarker, searchIdx, ignoreCase = true)
            if (foundIdx == -1) break

            val braceStart = rawResponse.indexOf('{', foundIdx + actionMarker.length)
            if (braceStart == -1) {
                searchIdx = foundIdx + actionMarker.length
                continue
            }

            var depth = 0
            var braceEnd = -1
            var inQuotes = false
            var escape = false

            for (i in braceStart until rawResponse.length) {
                val c = rawResponse[i]
                if (escape) {
                    escape = false
                    continue
                }
                if (c == '\\') {
                    escape = true
                    continue
                }
                if (c == '"') {
                    inQuotes = !inQuotes
                    continue
                }
                if (!inQuotes) {
                    if (c == '{') depth++
                    else if (c == '}') {
                        depth--
                        if (depth == 0) {
                            braceEnd = i
                            break
                        }
                    }
                }
            }

            if (braceEnd != -1) {
                val jsonCandidate = rawResponse.substring(braceStart, braceEnd + 1)
                var fullEnd = braceEnd + 1
                while (fullEnd < rawResponse.length && (rawResponse[fullEnd] == '`' || rawResponse[fullEnd] == ' ' || rawResponse[fullEnd] == '\t' || rawResponse[fullEnd] == '\r' || rawResponse[fullEnd] == '\n')) {
                    fullEnd++
                }
                if (fullEnd < rawResponse.length && rawResponse[fullEnd] == '#') {
                    fullEnd++
                }
                val fullMatchText = rawResponse.substring(foundIdx, fullEnd)

                runCatching {
                    @Suppress("UNCHECKED_CAST")
                    val map = gson.fromJson(jsonCandidate, Map::class.java) as? Map<String, Any>
                    if (map != null) {
                        results.add(Pair(fullMatchText, map))
                    }
                }
                searchIdx = fullEnd
            } else {
                searchIdx = foundIdx + actionMarker.length
            }
        }

        return results
    }

    private suspend fun processActionAndRecordMemory(
        persona: AiPersona,
        rawResponse: String
    ): ProcessActionResult {
        var cleanText = rawResponse
        var createdFolderId: String? = null
        var isPlan = false

        try {
            val actionPairs = extractActionsFromResponse(rawResponse)

            for ((fullMatch, actionMap) in actionPairs) {
                cleanText = cleanText.replace(fullMatch, "").trim()
                val type = (actionMap["type"] as? String)?.uppercase()

                when (type) {
                    "CREATE_WEEKLY_PLAN", "CREATE_PLAN", "INSERT_PLAN", "WEEKLY_PLAN", "ADD_PLAN", "PLAN" -> {
                        val title = (actionMap["title"] as? String)?.ifBlank { "Haftalık Çalışma Programı" }
                            ?: "Haftalık Çalışma Programı"
                        val summary = (actionMap["summary"] as? String ?: actionMap["weeklyFocusSummary"] as? String)?.ifBlank { title } ?: title
                        val rawSchedule = (actionMap["schedule"] as? List<*>)
                            ?: (actionMap["tasks"] as? List<*>)
                            ?: (actionMap["plan"] as? List<*>)
                            ?: (actionMap["days"] as? List<*>)
                            ?: emptyList<Any>()
                        val parsedTasks = mutableListOf<PlanEntity>()

                        rawSchedule.forEachIndexed { idx, item ->
                            val taskMap = item as? Map<*, *> ?: return@forEachIndexed
                            val subj = (taskMap["subject"] as? String ?: taskMap["course"] as? String ?: "Ders").ifBlank { "Ders" }
                            val top = (taskMap["topic"] as? String ?: taskMap["title"] as? String ?: "").trim()
                            val rawTime = taskMap["timeSlot"] as? String ?: taskMap["time"] as? String ?: taskMap["scheduledTime"] as? String
                            val time = normalizeFlexibleTimeSlot(rawTime)
                            val questions = (taskMap["targetQuestions"] as? Number)?.toInt()
                                ?: (taskMap["questions"] as? Number)?.toInt()
                                ?: (taskMap["count"] as? Number)?.toInt()
                                ?: 30
                            val day = (taskMap["dayOfWeek"] as? Number)?.toInt()
                                ?: (taskMap["day"] as? Number)?.toInt()
                                ?: (taskMap["dayIndex"] as? Number)?.toInt()
                                ?: (idx % 7 + 1)

                            parsedTasks.add(
                                PlanEntity(
                                    id = UUID.randomUUID().toString(),
                                    folderId = "",
                                    title = top.ifBlank { subj },
                                    subject = subj,
                                    topic = top,
                                    dayOfWeek = day.coerceIn(1, 7),
                                    scheduledTime = time,
                                    targetQuestions = if (questions > 0) questions else 30,
                                    orderIndex = idx
                                )
                            )
                        }

                        if (parsedTasks.isNotEmpty()) {
                            val newFolderId = withContext(Dispatchers.IO) {
                                val savedFolderId = planRepositoryImpl.saveGeneratedPlan(
                                    folderTitle = if (title.length > 38) title.take(38) + "…" else title,
                                    tasks = parsedTasks
                                )

                                // Dual persistence: active_study_plans & study_plan_tasks (StudyPlanDao)
                                val dayNames = listOf("Pazartesi", "Salı", "Çarşamba", "Perşembe", "Cuma", "Cumartesi", "Pazar")
                                val activePlan = ActivePlanEntity(
                                    id = "CURRENT_ACTIVE_PLAN",
                                    title = title,
                                    strategyNote = summary,
                                    totalTargetQuestions = parsedTasks.sumOf { it.targetQuestions },
                                    updatedAtTimestamp = System.currentTimeMillis()
                                )
                                val studyTasks = parsedTasks.mapIndexed { idx, p ->
                                    val dName = dayNames.getOrElse(p.dayOfWeek - 1) { "Pazartesi" }
                                    StudyTaskEntity(
                                        localId = 0,
                                        planId = "CURRENT_ACTIVE_PLAN",
                                        dayName = dName,
                                        dayIndex = (p.dayOfWeek - 1).coerceIn(0, 6),
                                        time = p.scheduledTime,
                                        subject = p.subject,
                                        topic = p.topic.ifBlank { p.subject },
                                        goal = "${p.targetQuestions} Soru Hedefi",
                                        isCompleted = false
                                    )
                                }
                                database.studyPlanDao().replaceActivePlanWithTasks(activePlan, studyTasks)

                                // Remote cloud sync
                                runCatching {
                                    val firestore = GoogleServicesHelper.getFirebaseFirestore(getApplication())
                                    val uid = GoogleServicesHelper.getCurrentUser(context = getApplication())?.uid
                                    if (uid != null && firestore != null) {
                                        val planMap = hashMapOf<String, Any>(
                                            "id" to "CURRENT_ACTIVE_PLAN",
                                            "title" to title,
                                            "strategyNote" to summary,
                                            "totalTargetQuestions" to activePlan.totalTargetQuestions,
                                            "updatedAtTimestamp" to activePlan.updatedAtTimestamp,
                                            "userId" to uid,
                                            "studentId" to uid,
                                            "tasks" to studyTasks.map { task ->
                                                hashMapOf(
                                                    "dayIndex" to task.dayIndex,
                                                    "dayName" to task.dayName,
                                                    "subject" to task.subject,
                                                    "time" to task.time,
                                                    "topic" to task.topic,
                                                    "goal" to task.goal,
                                                    "isCompleted" to task.isCompleted
                                                )
                                            }
                                        )
                                        firestore.collection("students").document(uid).collection("plans").document("CURRENT_ACTIVE_PLAN").set(planMap, SetOptions.merge())
                                    }
                                }
                                SyncWorker.enqueueImmediateSync(getApplication())
                                savedFolderId
                            }
                            createdFolderId = newFolderId
                            isPlan = true
                            memoryRepository.recordInsight(
                                persona = persona,
                                memoryType = "DECISION",
                                content = "Haftalık Plan Oluşturuldu: $title (${parsedTasks.size} Görev)"
                            )
                            if (!cleanText.contains("#ACTION:NAV_PLANS")) {
                                cleanText += "\n\n#ACTION:NAV_PLANS"
                            }
                        }
                    }

                    "INSERT_PLAN_TASK", "INSERT_TASK", "ADD_TASK", "ADD_PLAN_TASK" -> {
                        val subject = (actionMap["subject"] as? String ?: actionMap["course"] as? String ?: "Ders").ifBlank { "Ders" }
                        val topic = (actionMap["topic"] as? String ?: actionMap["title"] as? String ?: "").trim()
                        val questions = (actionMap["questions"] as? Number)?.toInt()
                            ?: (actionMap["targetQuestions"] as? Number)?.toInt()
                            ?: 30
                        val day = (actionMap["day"] as? Number)?.toInt()
                            ?: (actionMap["dayOfWeek"] as? Number)?.toInt()
                            ?: 1
                        val time = normalizeFlexibleTimeSlot(actionMap["time"] as? String ?: actionMap["timeSlot"] as? String)

                        withContext(Dispatchers.IO) {
                            val folders = database.planFolderDao().getFoldersCreatedAfter(0)
                            val folderId = folders.firstOrNull()?.id ?: run {
                                val newF = PlanFolderEntity(
                                    id = UUID.randomUUID().toString(),
                                    title = "Haftalık Çalışma Programı",
                                    createdAt = System.currentTimeMillis(),
                                    endTimestamp = System.currentTimeMillis() + (7L * 24 * 3600 * 1000),
                                    source = "AI_PLANNER"
                                )
                                database.planFolderDao().insertFolder(newF)
                                newF.id
                            }
                            val task = PlanEntity(
                                id = UUID.randomUUID().toString(),
                                folderId = folderId,
                                title = topic.ifBlank { subject },
                                subject = subject,
                                topic = topic,
                                dayOfWeek = day.coerceIn(1, 7),
                                scheduledTime = time,
                                targetQuestions = if (questions > 0) questions else 30,
                                orderIndex = 0
                            )
                            database.planDao().insertTasks(listOf(task))

                            val dayNames = listOf("Pazartesi", "Salı", "Çarşamba", "Perşembe", "Cuma", "Cumartesi", "Pazar")
                            val studyTask = StudyTaskEntity(
                                localId = 0,
                                planId = "CURRENT_ACTIVE_PLAN",
                                dayName = dayNames.getOrElse(day.coerceIn(1, 7) - 1) { "Pazartesi" },
                                dayIndex = (day.coerceIn(1, 7) - 1).coerceIn(0, 6),
                                time = time,
                                subject = subject,
                                topic = topic.ifBlank { subject },
                                goal = "$questions Soru Hedefi",
                                isCompleted = false
                            )
                            database.studyPlanDao().insertTasks(listOf(studyTask))

                            runCatching {
                                val firestore = GoogleServicesHelper.getFirebaseFirestore(getApplication())
                                val uid = GoogleServicesHelper.getCurrentUser(context = getApplication())?.uid
                                if (uid != null && firestore != null) {
                                    firestore.collection("users").document(uid).collection("plans").document(task.id).set(task)
                                }
                            }
                            SyncWorker.enqueueImmediateSync(getApplication())
                        }
                        memoryRepository.recordInsight(
                            persona = persona,
                            memoryType = "DECISION",
                            content = "Görev Eklendi: Gün $day $subject - $topic ($questions Soru)"
                        )
                        if (!cleanText.contains("#ACTION:NAV_PLANS")) {
                            cleanText += "\n\n#ACTION:NAV_PLANS"
                        }
                    }

                    "INSERT_TARGET" -> {
                        val title = (actionMap["title"] as? String ?: "Hedef").trim()
                        val desc = (actionMap["description"] as? String ?: "").trim()
                        val questions = (actionMap["targetQuestions"] as? Number)?.toInt()
                            ?: (actionMap["questions"] as? Number)?.toInt() ?: 50
                        val rawType = actionMap["type"] as? String ?: "SHORT_TERM"
                        val targetType = if (rawType.contains("LONG", ignoreCase = true)) TargetType.LONG_TERM else TargetType.SHORT_TERM
                        val rawPeriod = (actionMap["period"] as? String ?: "").uppercase(Locale.ROOT)
                        val targetPeriod = when {
                            rawPeriod.contains("SIX") -> TargetPeriod.SIX_MONTH
                            rawPeriod.contains("MONTH") -> TargetPeriod.MONTHLY
                            rawPeriod.contains("DAY") || rawPeriod.contains("DAILY") -> TargetPeriod.DAILY
                            targetType == TargetType.LONG_TERM -> TargetPeriod.MONTHLY
                            else -> TargetPeriod.WEEKLY
                        }
                        val priority = actionMap["priority"] as? String ?: "HIGH"
                        val category = (actionMap["category"] as? String ?: actionMap["subject"] as? String ?: "HEDEF").ifBlank { "HEDEF" }

                        val targetEntity = TargetEntity(
                            title = title,
                            description = desc,
                            type = targetType,
                            period = targetPeriod,
                            deadlineMillis = System.currentTimeMillis() + (7L * 24 * 3600 * 1000),
                            targetQuestions = questions,
                            priority = priority,
                            category = category
                        )
                        repository.addTarget(
                            target = targetEntity,
                            tasks = if (desc.isNotBlank()) listOf(desc) else listOf("$title ($questions Soru)")
                        )
                        SyncWorker.enqueueImmediateSync(getApplication())
                        memoryRepository.recordInsight(
                            persona = persona,
                            memoryType = "DECISION",
                            content = "Hedef Eklendi: $title ($questions Soru)"
                        )
                        if (!cleanText.contains("#ACTION:NAV_TARGETS")) {
                            cleanText += "\n\n#ACTION:NAV_TARGETS"
                        }
                    }

                    "INSERT_TARGETS_BATCH", "ADD_TARGETS_BATCH", "CREATE_TARGETS_BATCH" -> {
                        val rawTargets = (actionMap["targets"] as? List<*>)
                            ?: (actionMap["items"] as? List<*>)
                            ?: emptyList<Any>()
                        var insertedCount = 0

                        withContext(Dispatchers.IO) {
                            rawTargets.forEach { item ->
                                val tMap = item as? Map<*, *> ?: return@forEach
                                val title = (tMap["title"] as? String ?: tMap["name"] as? String ?: "").trim()
                                if (title.isBlank()) return@forEach
                                val desc = (tMap["description"] as? String ?: "").trim()
                                val questions = (tMap["targetQuestions"] as? Number)?.toInt()
                                    ?: (tMap["questions"] as? Number)?.toInt() ?: 50
                                val rawType = tMap["type"] as? String ?: "SHORT_TERM"
                                val targetType = if (rawType.contains("LONG", ignoreCase = true)) TargetType.LONG_TERM else TargetType.SHORT_TERM
                                val rawPeriod = (tMap["period"] as? String ?: "").uppercase(Locale.ROOT)
                                val targetPeriod = when {
                                    rawPeriod.contains("SIX") -> TargetPeriod.SIX_MONTH
                                    rawPeriod.contains("MONTH") -> TargetPeriod.MONTHLY
                                    rawPeriod.contains("DAY") || rawPeriod.contains("DAILY") -> TargetPeriod.DAILY
                                    targetType == TargetType.LONG_TERM -> TargetPeriod.MONTHLY
                                    else -> TargetPeriod.WEEKLY
                                }
                                val priority = tMap["priority"] as? String ?: "HIGH"
                                val category = (tMap["category"] as? String ?: tMap["subject"] as? String ?: "HEDEF").ifBlank { "HEDEF" }

                                val targetEntity = TargetEntity(
                                    title = title,
                                    description = desc,
                                    type = targetType,
                                    period = targetPeriod,
                                    deadlineMillis = System.currentTimeMillis() + (7L * 24 * 3600 * 1000),
                                    targetQuestions = questions,
                                    priority = priority,
                                    category = category
                                )
                                repository.addTarget(
                                    target = targetEntity,
                                    tasks = if (desc.isNotBlank()) listOf(desc) else listOf("$title ($questions Soru)")
                                )
                                insertedCount++
                            }
                            if (insertedCount > 0) {
                                SyncWorker.enqueueImmediateSync(getApplication())
                            }
                        }

                        if (insertedCount > 0) {
                            memoryRepository.recordInsight(
                                persona = persona,
                                memoryType = "DECISION",
                                content = "Toplu Hedef Eklendi ($insertedCount adet)"
                            )
                            if (!cleanText.contains("#ACTION:NAV_TARGETS")) {
                                cleanText += "\n\n#ACTION:NAV_TARGETS"
                            }
                        }
                    }

                    "UPDATE_TARGET_PROGRESS", "UPDATE_TARGET", "SET_TARGET_PROGRESS" -> {
                        val targetId = (actionMap["targetId"] as? Number)?.toLong()
                            ?: (actionMap["id"] as? Number)?.toLong() ?: 0L
                        val title = (actionMap["title"] as? String ?: "").trim()
                        val solvedInput = (actionMap["solvedQuestions"] as? Number)?.toInt()
                            ?: (actionMap["completedQuestions"] as? Number)?.toInt()
                            ?: (actionMap["questions"] as? Number)?.toInt()
                        val explicitProgress = (actionMap["progress"] as? Number)?.toFloat()
                        val explicitCompleted = actionMap["isCompleted"] as? Boolean
                        val isIncremental = actionMap["isIncremental"] as? Boolean ?: false
                        val targetNet = (actionMap["targetNet"] as? Number)?.toInt()
                            ?: (actionMap["targetValue"] as? Number)?.toInt()

                        val updatedTarget = withContext(Dispatchers.IO) {
                            val existing = if (targetId > 0L) {
                                database.targetDao().getTargetById(targetId)
                            } else {
                                val allActive = (database.targetDao().getTargetsWithTasksSync(TargetType.SHORT_TERM) +
                                    database.targetDao().getTargetsWithTasksSync(TargetType.LONG_TERM)).map { it.target }
                                findMatchingTarget(allActive, title)
                            }

                            if (existing != null) {
                                val nextSolved = when {
                                    solvedInput != null && isIncremental -> (existing.solvedQuestions + solvedInput).coerceAtLeast(0)
                                    solvedInput != null -> solvedInput.coerceAtLeast(0)
                                    else -> existing.solvedQuestions
                                }
                                val nextTotal = if (existing.targetQuestions > 0) {
                                    existing.targetQuestions.coerceAtLeast(nextSolved)
                                } else {
                                    (solvedInput ?: 50).coerceAtLeast(1)
                                }
                                val computedProgress = when {
                                    explicitProgress != null -> if (explicitProgress > 1f) (explicitProgress / 100f).coerceIn(0f, 1f) else explicitProgress.coerceIn(0f, 1f)
                                    nextTotal > 0 -> (nextSolved.toFloat() / nextTotal.toFloat()).coerceIn(0f, 1f)
                                    else -> existing.progress
                                }
                                val nextCompleted = explicitCompleted ?: (computedProgress >= 1f)
                                val modified = existing.copy(
                                    solvedQuestions = nextSolved,
                                    targetQuestions = nextTotal,
                                    progress = computedProgress,
                                    isCompleted = nextCompleted,
                                    targetValue = targetNet?.toString() ?: existing.targetValue,
                                    isSynced = false,
                                    lastModifiedMillis = System.currentTimeMillis()
                                )
                                database.targetDao().updateTarget(modified)
                                SyncWorker.enqueueImmediateSync(getApplication())
                                modified
                            } else null
                        }

                        memoryRepository.recordInsight(
                            persona = persona,
                            memoryType = "GOAL_STATE",
                            content = if (updatedTarget != null) {
                                "Hedef İlerlemesi Güncellendi: ${updatedTarget.title} (${updatedTarget.solvedQuestions}/${updatedTarget.targetQuestions} Soru)"
                            } else {
                                "Hedef Güncellendi: $title (${targetNet ?: solvedInput ?: 0})"
                            }
                        )
                        if (!cleanText.contains("#ACTION:NAV_TARGETS")) {
                            cleanText += "\n\n#ACTION:NAV_TARGETS"
                        }
                    }

                    "UPDATE_RESOURCE_PROGRESS", "SET_RESOURCE_PROGRESS", "UPDATE_RESOURCE", "UPDATE_BOOK_PROGRESS" -> {
                        val resourceId = (actionMap["resourceId"] as? Number)?.toLong()
                            ?: (actionMap["id"] as? Number)?.toLong() ?: 0L
                        val title = (actionMap["title"] as? String ?: actionMap["book"] as? String ?: "").trim()
                        val rawSubject = (actionMap["subject"] as? String ?: "").trim()
                        val resolvedSubject = inferSubjectFromResourceTitle(title, rawSubject)
                        val completedInput = (actionMap["completedUnits"] as? Number)?.toInt()
                            ?: (actionMap["progress"] as? Number)?.toInt()
                            ?: (actionMap["page"] as? Number)?.toInt()
                            ?: (actionMap["currentUnits"] as? Number)?.toInt()
                            ?: 0
                        val validTotalInput = (actionMap["totalUnits"] as? Number)?.toInt()?.takeIf { it > 0 }
                        val unitTypeInput = (actionMap["unitType"] as? String)?.trim()?.takeIf { it.isNotBlank() }
                        val isIncremental = actionMap["isIncremental"] as? Boolean ?: false

                        withContext(Dispatchers.IO) {
                            val allResources = database.goalsDao().getAllResourcesRaw()
                            val existing = if (resourceId > 0L) {
                                val byId = allResources.firstOrNull { it.id == resourceId }
                                if (byId != null && (title.isBlank() || findMatchingResource(listOf(byId), title, resolvedSubject) != null)) {
                                    byId
                                } else {
                                    findMatchingResource(allResources, title, resolvedSubject)
                                }
                            } else {
                                findMatchingResource(allResources, title, resolvedSubject)
                            }

                            if (existing != null) {
                                val newCompleted = if (isIncremental) {
                                    (existing.completedUnits + completedInput).coerceAtLeast(0)
                                } else {
                                    completedInput.coerceAtLeast(0)
                                }
                                val newTotal = when {
                                    validTotalInput != null -> maxOf(validTotalInput, newCompleted)
                                    existing.totalUnits > 0 -> maxOf(existing.totalUnits, newCompleted)
                                    else -> 0
                                }
                                val clampedCompleted = if (newTotal > 0) newCompleted.coerceIn(0, newTotal) else newCompleted.coerceAtLeast(0)
                                val newUnitType = unitTypeInput ?: existing.unitType
                                val nextSubject = if ((existing.subject.isBlank() || existing.subject.equals("Genel", ignoreCase = true)) && resolvedSubject != "Genel") {
                                    resolvedSubject
                                } else {
                                    existing.subject
                                }
                                val now = System.currentTimeMillis()
                                val updated = existing.copy(
                                    subject = nextSubject,
                                    completedUnits = clampedCompleted,
                                    totalUnits = newTotal,
                                    unitType = newUnitType,
                                    updatedAtMillis = now,
                                    lastModifiedMillis = now,
                                    isSynced = false
                                )
                                database.goalsDao().updateResource(updated)
                                database.goalsDao().setProgress(existing.id, updated.completedUnits, now)

                                runCatching {
                                    val firestore = GoogleServicesHelper.getFirebaseFirestore(getApplication())
                                    val uid = GoogleServicesHelper.getCurrentUser(context = getApplication())?.uid
                                    if (uid != null && firestore != null) {
                                        val docId = updated.cloudId?.takeIf { it.isNotBlank() } ?: "${uid}_${updated.id}"
                                        val resData = hashMapOf<String, Any>(
                                            "id" to docId,
                                            "localId" to updated.id,
                                            "title" to updated.title,
                                            "subject" to updated.subject,
                                            "totalUnits" to updated.totalUnits,
                                            "totalTarget" to updated.totalUnits,
                                            "currentUnits" to updated.completedUnits,
                                            "completedUnits" to updated.completedUnits,
                                            "isCompleted" to (updated.totalUnits > 0 && updated.completedUnits >= updated.totalUnits),
                                            "unitType" to updated.unitType,
                                            "isDeleted" to false,
                                            "studentId" to uid,
                                            "userId" to uid,
                                            "updatedAt" to now
                                        )
                                        firestore.collection("students").document(uid).collection("resources").document(docId).set(resData, SetOptions.merge())
                                        firestore.collection("resources").document(docId).set(resData, SetOptions.merge())
                                    }
                                }
                                SyncWorker.enqueueImmediateSync(getApplication())
                                val progressDesc = if (updated.totalUnits > 0) {
                                    "${updated.completedUnits}/${updated.totalUnits} ${updated.unitType}"
                                } else {
                                    "${updated.completedUnits} ${updated.unitType}"
                                }
                                memoryRepository.recordInsight(
                                    persona = persona,
                                    memoryType = "DECISION",
                                    content = "Kaynak İlerlemesi Güncellendi: ${updated.title} ($progressDesc)"
                                )
                            } else if (title.isNotBlank()) {
                                // Only create if the book truly does not exist in the library yet; never fabricate totalUnits
                                val institutionId = com.example.sharkmentor.data.local.preferences.UserPreferences.getInstance(getApplication()).institutionId ?: "INDIVIDUAL"
                                val nonNegCompleted = completedInput.coerceAtLeast(0)
                                val resolvedTotal = validTotalInput?.let { maxOf(it, nonNegCompleted) } ?: 0
                                val finalCompleted = if (resolvedTotal > 0) nonNegCompleted.coerceIn(0, resolvedTotal) else nonNegCompleted
                                val resolvedUnitType = unitTypeInput ?: inferUnitTypeFromText("$title $lastUserPromptText")
                                val now = System.currentTimeMillis()
                                val resEntity = ResourceEntity(
                                    title = title,
                                    subject = resolvedSubject,
                                    totalUnits = resolvedTotal,
                                    completedUnits = finalCompleted,
                                    unitType = resolvedUnitType,
                                    institutionId = institutionId,
                                    updatedAtMillis = now,
                                    lastModifiedMillis = now,
                                    isSynced = false
                                )
                                val insertedId = database.goalsDao().insertResource(resEntity)
                                val finalId = if (insertedId > 0L) insertedId else resEntity.id
                                runCatching {
                                    val firestore = GoogleServicesHelper.getFirebaseFirestore(getApplication())
                                    val uid = GoogleServicesHelper.getCurrentUser(context = getApplication())?.uid
                                    if (uid != null && firestore != null && finalId > 0L) {
                                        val docId = "${uid}_${finalId}"
                                        val resData = hashMapOf<String, Any>(
                                            "id" to docId,
                                            "localId" to finalId,
                                            "title" to title,
                                            "subject" to resolvedSubject,
                                            "totalUnits" to resolvedTotal,
                                            "totalTarget" to resolvedTotal,
                                            "currentUnits" to finalCompleted,
                                            "completedUnits" to finalCompleted,
                                            "isCompleted" to (resolvedTotal > 0 && finalCompleted >= resolvedTotal),
                                            "unitType" to resolvedUnitType,
                                            "isDeleted" to false,
                                            "studentId" to uid,
                                            "userId" to uid,
                                            "createdAt" to now,
                                            "updatedAt" to now
                                        )
                                        firestore.collection("students").document(uid).collection("resources").document(docId).set(resData, SetOptions.merge())
                                        firestore.collection("resources").document(docId).set(resData, SetOptions.merge())
                                    }
                                }
                                SyncWorker.enqueueImmediateSync(getApplication())
                                val progressDesc = if (resolvedTotal > 0) "$finalCompleted/$resolvedTotal $resolvedUnitType" else "$finalCompleted $resolvedUnitType"
                                memoryRepository.recordInsight(
                                    persona = persona,
                                    memoryType = "DECISION",
                                    content = "Yeni Kaynak Eklendi: $title ($resolvedSubject - $progressDesc)"
                                )
                            }
                        }
                        if (!cleanText.contains("#ACTION:NAV_RESOURCES")) {
                            cleanText += "\n\n#ACTION:NAV_RESOURCES"
                        }
                    }

                    "INSERT_RESOURCE" -> {
                        val title = (actionMap["title"] as? String ?: actionMap["book"] as? String ?: "Kaynak Kitap").trim()
                        val rawSubject = (actionMap["subject"] as? String ?: "").trim()
                        val resolvedSubject = inferSubjectFromResourceTitle(title, rawSubject)
                        val totalInput = (actionMap["totalUnits"] as? Number)?.toInt()?.takeIf { it > 0 }
                        val completedUnits = ((actionMap["completedUnits"] as? Number)?.toInt()
                            ?: (actionMap["currentUnits"] as? Number)?.toInt()
                            ?: (actionMap["progress"] as? Number)?.toInt()
                            ?: 0).coerceAtLeast(0)
                        val explicitUnitType = (actionMap["unitType"] as? String)?.trim()?.takeIf { it.isNotBlank() }
                        val unitType = explicitUnitType ?: inferUnitTypeFromText("$title $lastUserPromptText")

                        var savedCompleted = completedUnits
                        var savedTotal = totalInput ?: 0

                        withContext(Dispatchers.IO) {
                            val allResources = database.goalsDao().getAllResourcesRaw()
                            val existing = findMatchingResource(allResources, title, resolvedSubject)
                            val now = System.currentTimeMillis()

                            if (existing != null) {
                                // Do NOT open a duplicate book! Update existing ResourceEntity
                                val nextCompleted = if (completedUnits > 0) completedUnits else existing.completedUnits
                                val nextTotal = when {
                                    totalInput != null && existing.totalUnits > 0 -> maxOf(existing.totalUnits, totalInput, nextCompleted)
                                    totalInput != null -> maxOf(totalInput, nextCompleted)
                                    existing.totalUnits > 0 -> maxOf(existing.totalUnits, nextCompleted)
                                    else -> 0
                                }
                                val finalCompleted = if (nextTotal > 0) nextCompleted.coerceIn(0, nextTotal) else nextCompleted
                                savedCompleted = finalCompleted
                                savedTotal = nextTotal
                                val nextSubject = if ((existing.subject.isBlank() || existing.subject.equals("Genel", ignoreCase = true)) && resolvedSubject != "Genel") {
                                    resolvedSubject
                                } else {
                                    existing.subject
                                }
                                val updated = existing.copy(
                                    subject = nextSubject,
                                    completedUnits = finalCompleted,
                                    totalUnits = nextTotal,
                                    unitType = explicitUnitType ?: existing.unitType,
                                    updatedAtMillis = now,
                                    lastModifiedMillis = now,
                                    isSynced = false
                                )
                                database.goalsDao().updateResource(updated)
                                database.goalsDao().setProgress(existing.id, updated.completedUnits, now)
                                runCatching {
                                    val firestore = GoogleServicesHelper.getFirebaseFirestore(getApplication())
                                    val uid = GoogleServicesHelper.getCurrentUser(context = getApplication())?.uid
                                    if (uid != null && firestore != null) {
                                        val docId = updated.cloudId?.takeIf { it.isNotBlank() } ?: "${uid}_${updated.id}"
                                        val resData = hashMapOf<String, Any>(
                                            "id" to docId,
                                            "localId" to updated.id,
                                            "title" to updated.title,
                                            "subject" to updated.subject,
                                            "totalUnits" to updated.totalUnits,
                                            "totalTarget" to updated.totalUnits,
                                            "currentUnits" to updated.completedUnits,
                                            "completedUnits" to updated.completedUnits,
                                            "isCompleted" to (updated.totalUnits > 0 && updated.completedUnits >= updated.totalUnits),
                                            "unitType" to updated.unitType,
                                            "isDeleted" to false,
                                            "studentId" to uid,
                                            "userId" to uid,
                                            "updatedAt" to now
                                        )
                                        firestore.collection("students").document(uid).collection("resources").document(docId).set(resData, SetOptions.merge())
                                        firestore.collection("resources").document(docId).set(resData, SetOptions.merge())
                                    }
                                }
                                SyncWorker.enqueueImmediateSync(getApplication())
                            } else {
                                val institutionId = com.example.sharkmentor.data.local.preferences.UserPreferences.getInstance(getApplication()).institutionId ?: "INDIVIDUAL"
                                val resolvedTotal = totalInput?.let { maxOf(it, completedUnits) } ?: 0
                                val finalCompleted = if (resolvedTotal > 0) completedUnits.coerceIn(0, resolvedTotal) else completedUnits
                                savedCompleted = finalCompleted
                                savedTotal = resolvedTotal
                                val resEntity = ResourceEntity(
                                    title = title,
                                    subject = resolvedSubject,
                                    totalUnits = resolvedTotal,
                                    completedUnits = finalCompleted,
                                    unitType = unitType,
                                    institutionId = institutionId,
                                    updatedAtMillis = now,
                                    lastModifiedMillis = now,
                                    isSynced = false
                                )
                                val insertedId = database.goalsDao().insertResource(resEntity)
                                val finalId = if (insertedId > 0L) insertedId else resEntity.id
                                runCatching {
                                    val firestore = GoogleServicesHelper.getFirebaseFirestore(getApplication())
                                    val uid = GoogleServicesHelper.getCurrentUser(context = getApplication())?.uid
                                    if (uid != null && firestore != null && finalId > 0L) {
                                        val docId = "${uid}_${finalId}"
                                        val resData = hashMapOf<String, Any>(
                                            "id" to docId,
                                            "localId" to finalId,
                                            "title" to title,
                                            "subject" to resolvedSubject,
                                            "totalUnits" to resolvedTotal,
                                            "totalTarget" to resolvedTotal,
                                            "currentUnits" to finalCompleted,
                                            "completedUnits" to finalCompleted,
                                            "isCompleted" to (resolvedTotal > 0 && finalCompleted >= resolvedTotal),
                                            "unitType" to unitType,
                                            "isDeleted" to false,
                                            "studentId" to uid,
                                            "userId" to uid,
                                            "createdAt" to now,
                                            "updatedAt" to now
                                        )
                                        firestore.collection("students").document(uid).collection("resources").document(docId).set(resData, SetOptions.merge())
                                        firestore.collection("resources").document(docId).set(resData, SetOptions.merge())
                                    }
                                }
                                SyncWorker.enqueueImmediateSync(getApplication())
                            }
                        }
                        val progressDesc = if (savedTotal > 0) "$savedCompleted/$savedTotal $unitType" else "$savedCompleted $unitType"
                        memoryRepository.recordInsight(
                            persona = persona,
                            memoryType = "DECISION",
                            content = "Kaynak Kaydedildi/Güncellendi: $title ($resolvedSubject - $progressDesc)"
                        )
                        if (!cleanText.contains("#ACTION:NAV_RESOURCES")) {
                            cleanText += "\n\n#ACTION:NAV_RESOURCES"
                        }
                    }

                    "INSERT_TOPICS_BATCH", "ADD_TOPICS_BATCH", "CREATE_TOPICS_BATCH", "BULK_TOPICS" -> {
                        val preset = (actionMap["preset"] as? String)?.trim()?.takeIf { it.isNotBlank() }
                        val exam = (actionMap["exam"] as? String ?: "").trim()
                        val effectiveExamHint = exam.ifBlank {
                            if (lastUserPromptText.contains("ayt", ignoreCase = true) && !lastUserPromptText.contains("tyt", ignoreCase = true)) "AYT" else "TYT"
                        }
                        val defaultTargetLevel = ((actionMap["targetLevel"] as? Number)?.toInt() ?: 5).coerceIn(1, 5)
                        val rawTopicsList = (actionMap["topics"] as? List<*>)
                            ?: (actionMap["items"] as? List<*>)
                            ?: emptyList<Any>()
                        val rawSubjectsList = (actionMap["subjects"] as? List<*>) ?: emptyList<Any>()

                        val collectedPairs = mutableListOf<Triple<String, String, Int>>()

                        // 1. Expand preset / exam ONLY when explicitly requested via preset, empty arrays, or bulk curriculum wording
                        val promptLower = lastUserPromptText.lowercase(Locale("tr"))
                        val promptAsksBulk = promptLower.contains("tüm konular") ||
                            promptLower.contains("tum konular") ||
                            promptLower.contains("bütün konular") ||
                            promptLower.contains("müfredat") ||
                            promptLower.contains("mufredat")
                        if (preset != null || (rawTopicsList.isEmpty() && rawSubjectsList.isEmpty()) || promptAsksBulk) {
                            val presetPairs = YksCurriculumCatalog.expandFromPresetOrPrompt(
                                preset = preset,
                                exam = exam.ifBlank { null },
                                userPrompt = lastUserPromptText
                            )
                            presetPairs.forEach { (subj, top) ->
                                collectedPairs.add(Triple(subj, top, defaultTargetLevel))
                            }
                        }

                        // 2. Parse explicit `topics` array from AI (and expand any placeholder/empty subject entries!)
                        rawTopicsList.forEach { entry ->
                            when (entry) {
                                is Map<*, *> -> {
                                    val subj = (entry["subject"] as? String ?: entry["course"] as? String ?: exam).trim().ifBlank { "Genel" }
                                    val top = (entry["topic"] as? String ?: entry["title"] as? String ?: entry["name"] as? String ?: "").trim()
                                    val subTopicsArr = entry["topics"] as? List<*> ?: entry["subtopics"] as? List<*>
                                    val lvl = ((entry["targetLevel"] as? Number)?.toInt() ?: defaultTargetLevel).coerceIn(1, 5)

                                    if (!subTopicsArr.isNullOrEmpty()) {
                                        subTopicsArr.forEach { st ->
                                            val stName = st?.toString()?.trim().orEmpty()
                                            if (stName.isNotBlank() && !YksCurriculumCatalog.isPlaceholderOrBroadTopic(stName)) {
                                                collectedPairs.add(Triple(subj, stName, lvl))
                                            }
                                        }
                                    } else if (YksCurriculumCatalog.isPlaceholderOrBroadTopic(top)) {
                                        val expanded = YksCurriculumCatalog.expandSubjectTopics(subj, effectiveExamHint)
                                        if (expanded.isNotEmpty()) {
                                            expanded.forEach { (s, t) -> collectedPairs.add(Triple(s, t, lvl)) }
                                        }
                                    } else {
                                        collectedPairs.add(Triple(subj, top, lvl))
                                    }
                                }
                            }
                        }

                        // 3. Parse optional `subjects` array (`[{"subject": "TYT Matematik", "topics": ["..."]}]`)
                        rawSubjectsList.forEach { sEntry ->
                            val sMap = sEntry as? Map<*, *> ?: return@forEach
                            val subj = (sMap["subject"] as? String ?: sMap["name"] as? String ?: exam).trim().ifBlank { "Genel" }
                            val subList = (sMap["topics"] as? List<*>) ?: emptyList<Any>()
                            if (subList.isEmpty()) {
                                YksCurriculumCatalog.expandSubjectTopics(subj, effectiveExamHint).forEach { (s, t) ->
                                    collectedPairs.add(Triple(s, t, defaultTargetLevel))
                                }
                            } else {
                                subList.forEach { tItem ->
                                    val tName = tItem?.toString()?.trim().orEmpty()
                                    if (tName.isNotBlank()) {
                                        collectedPairs.add(Triple(subj, tName, defaultTargetLevel))
                                    }
                                }
                            }
                        }

                        val insertedCount = batchInsertTopicGoalsDeduped(collectedPairs)
                        if (insertedCount > 0) {
                            memoryRepository.recordInsight(
                                persona = persona,
                                memoryType = "DECISION",
                                content = "Toplu Konu Hakimiyeti Eklendi: $insertedCount alt konu"
                            )
                            if (!cleanText.contains("#ACTION:NAV_TOPICS")) {
                                cleanText += "\n\n#ACTION:NAV_TOPICS"
                            }
                        }
                    }

                    "INSERT_TOPIC", "UPDATE_TOPIC_PROGRESS", "UPDATE_TOPIC" -> {
                        val rawSubject = (actionMap["subject"] as? String ?: "").trim()
                        val rawTopic = (actionMap["topic"] as? String ?: actionMap["topicName"] as? String ?: actionMap["title"] as? String ?: "").trim()
                        val rawCurrentLevel = (actionMap["currentLevel"] as? Number)?.toInt()
                            ?: (actionMap["level"] as? Number)?.toInt()
                            ?: (actionMap["masteryLevel"] as? Number)?.toInt()
                        val hasExplicitLevel = rawCurrentLevel != null
                        val isUpdateOnly = type == "UPDATE_TOPIC_PROGRESS" || type == "UPDATE_TOPIC"
                        val currentLevel = rawCurrentLevel ?: if (isUpdateOnly) 1 else 0
                        val targetLevel = ((actionMap["targetLevel"] as? Number)?.toInt()
                            ?: (actionMap["targetMasteryScore"] as? Number)?.toInt()
                            ?: 5).coerceIn(1, 5)

                        // Expand broad placeholders on INSERT (or when prompt asks for all curriculum topics)
                        val promptLower = lastUserPromptText.lowercase(Locale("tr"))
                        val asksBulkFromPrompt = promptLower.contains("tüm konular") ||
                            promptLower.contains("tum konular") ||
                            promptLower.contains("bütün konular") ||
                            promptLower.contains("müfredat") ||
                            promptLower.contains("mufredat")
                        if ((!isUpdateOnly && (YksCurriculumCatalog.isPlaceholderOrBroadTopic(rawTopic) || YksCurriculumCatalog.isPlaceholderOrBroadTopic(rawSubject))) || asksBulkFromPrompt) {
                            val expandedFromPrompt = YksCurriculumCatalog.expandFromPresetOrPrompt(
                                preset = null,
                                exam = rawSubject.ifBlank { null },
                                userPrompt = "$rawSubject $rawTopic $lastUserPromptText"
                            ).ifEmpty {
                                if (rawSubject.isNotBlank() && !YksCurriculumCatalog.isPlaceholderOrBroadTopic(rawSubject)) {
                                    YksCurriculumCatalog.expandSubjectTopics(rawSubject)
                                } else {
                                    emptyList()
                                }
                            }
                            if (expandedFromPrompt.isNotEmpty()) {
                                val batchCount = batchInsertTopicGoalsDeduped(
                                    expandedFromPrompt.map { (s, t) -> Triple(s, t, targetLevel) }
                                )
                                memoryRepository.recordInsight(
                                    persona = persona,
                                    memoryType = "DECISION",
                                    content = "Müfredat Konuları Eklendi ($batchCount alt konu)"
                                )
                                if (!cleanText.contains("#ACTION:NAV_TOPICS")) {
                                    cleanText += "\n\n#ACTION:NAV_TOPICS"
                                }
                                continue
                            }
                        }

                        if (YksCurriculumCatalog.isPlaceholderOrBroadTopic(rawTopic)) {
                            continue
                        }

                        val catalogMatch = YksCurriculumCatalog.findCurriculumMatch(rawSubject, rawTopic, lastUserPromptText)
                        var resolvedSubject = catalogMatch?.first ?: YksCurriculumCatalog.canonicalizeSubject(rawSubject, rawTopic, lastUserPromptText)
                        val resolvedTopic = catalogMatch?.second ?: rawTopic
                        if (resolvedSubject.isBlank() || resolvedSubject.equals("Genel", ignoreCase = true) || resolvedSubject.equals("Ders", ignoreCase = true)) {
                            val detected = detectSubjectFromContext("$rawSubject $resolvedTopic $lastUserPromptText".lowercase(Locale("tr")))
                            resolvedSubject = if (detected != "Genel") {
                                YksCurriculumCatalog.canonicalizeSubject(detected, resolvedTopic, lastUserPromptText)
                            } else {
                                "Genel"
                            }
                        }

                        var savedSubject = resolvedSubject
                        var savedTopic = resolvedTopic
                        var savedLevel = currentLevel.coerceIn(0, targetLevel)
                        var savedTarget = targetLevel

                        withContext(Dispatchers.IO) {
                            val existingTopics = database.goalsDao().getAllTopicsRaw()
                            val existing = findMatchingTopic(existingTopics, rawSubject, rawTopic)
                                ?: findMatchingTopic(existingTopics, resolvedSubject, resolvedTopic)
                            val now = System.currentTimeMillis()
                            val targetTopicId: Long

                            if (existing != null) {
                                val nextTarget = targetLevel.coerceAtLeast(1)
                                val nextLevel = if (hasExplicitLevel) currentLevel.coerceIn(0, nextTarget) else existing.currentLevel.coerceIn(0, nextTarget)
                                val upgradedSubject = if (catalogMatch != null && (existing.subject.equals("Genel", ignoreCase = true) || YksCurriculumCatalog.coreSubjectName(existing.subject) != YksCurriculumCatalog.coreSubjectName(catalogMatch.first))) {
                                    catalogMatch.first
                                } else {
                                    existing.subject
                                }
                                val updated = existing.copy(
                                    subject = upgradedSubject,
                                    currentLevel = nextLevel,
                                    targetLevel = nextTarget,
                                    isSynced = false,
                                    updatedAtMillis = now
                                )
                                database.goalsDao().updateTopicGoal(updated)
                                targetTopicId = updated.id
                                savedSubject = updated.subject
                                savedTopic = updated.topic
                                savedLevel = updated.currentLevel
                                savedTarget = updated.targetLevel
                            } else {
                                val existingSameCoreSubject = existingTopics.firstOrNull {
                                    YksCurriculumCatalog.areSubjectsCompatible(it.subject, resolvedSubject) &&
                                        !it.subject.equals("Genel", ignoreCase = true)
                                }?.subject
                                val folderSubject = if (catalogMatch != null) catalogMatch.first else (existingSameCoreSubject ?: resolvedSubject)
                                val institutionId = com.example.sharkmentor.data.local.preferences.UserPreferences.getInstance(getApplication()).institutionId ?: "INDIVIDUAL"
                                val topicEntity = TopicGoalEntity(
                                    subject = folderSubject,
                                    topic = resolvedTopic,
                                    currentLevel = savedLevel,
                                    targetLevel = targetLevel,
                                    institutionId = institutionId,
                                    isSynced = false,
                                    updatedAtMillis = now
                                )
                                val insertedId = database.goalsDao().insertTopicGoal(topicEntity)
                                targetTopicId = if (insertedId > 0L) insertedId else topicEntity.id
                                savedSubject = topicEntity.subject
                                savedTopic = topicEntity.topic
                                savedLevel = topicEntity.currentLevel
                                savedTarget = topicEntity.targetLevel
                            }

                            runCatching {
                                val firestore = GoogleServicesHelper.getFirebaseFirestore(getApplication())
                                val uid = GoogleServicesHelper.getCurrentUser(context = getApplication())?.uid
                                if (uid != null && firestore != null && targetTopicId > 0L) {
                                    val docId = "${uid}_${targetTopicId}"
                                    val topicData = hashMapOf<String, Any>(
                                        "id" to docId,
                                        "localId" to targetTopicId,
                                        "subject" to savedSubject,
                                        "topic" to savedTopic,
                                        "topicName" to savedTopic,
                                        "masteryLevel" to savedLevel,
                                        "targetMasteryScore" to savedTarget,
                                        "isDeleted" to false,
                                        "studentId" to uid,
                                        "userId" to uid,
                                        "updatedAt" to now
                                    )
                                    firestore.collection("students").document(uid).collection("topics").document(docId).set(topicData, SetOptions.merge())
                                    firestore.collection("topics").document(docId).set(topicData, SetOptions.merge())
                                }
                            }
                            SyncWorker.enqueueImmediateSync(getApplication())
                        }
                        memoryRepository.recordInsight(
                            persona = persona,
                            memoryType = "DECISION",
                            content = "Konu Hedefi İşlendi: $savedSubject - $savedTopic (Seviye $savedLevel/$savedTarget)"
                        )
                        if (!cleanText.contains("#ACTION:NAV_TOPICS")) {
                            cleanText += "\n\n#ACTION:NAV_TOPICS"
                        }
                    }
                }
            }

            // ── FALLBACK: Kullanıcı "TYT tüm konular" / "AYT tüm konular" istediyse ve AI tekil/eksik bıraktıysa ──
            if (!cleanText.contains("#ACTION:NAV_TOPICS")) {
                val promptLower = lastUserPromptText.lowercase(Locale("tr"))
                val asksBulkTopics = (promptLower.contains("tüm konular") || promptLower.contains("tum konular") || promptLower.contains("bütün konular") || promptLower.contains("müfredat")) &&
                    (promptLower.contains("tyt") || promptLower.contains("ayt")) &&
                    (promptLower.contains("ekle") || promptLower.contains("oluştur") || promptLower.contains("kaydet") || promptLower.contains("aç"))
                if (asksBulkTopics) {
                    val autoExpanded = YksCurriculumCatalog.expandFromPresetOrPrompt(
                        preset = null,
                        exam = null,
                        userPrompt = lastUserPromptText
                    )
                    if (autoExpanded.isNotEmpty()) {
                        val added = batchInsertTopicGoalsDeduped(
                            autoExpanded.map { (s, t) -> Triple(s, t, 5) }
                        )
                        if (added > 0) {
                            cleanText += "\n\n**Toplam $added ders ve alt konu** Konu Hakimiyeti tablosuna eksiksiz eklendi.\n\n#ACTION:NAV_TOPICS"
                        }
                    }
                }
            }

            // ── FALLBACK: Model #ACTION: etiketi koymadan saf JSON veya Markdown JSON schedule döndürdüyse ──
            if (!isPlan) {
                val jsonCandidate = extractBalancedJson(rawResponse)
                if (jsonCandidate != null) {
                    try {
                        val jsonObj = JSONObject(jsonCandidate)
                        val summary = jsonObj.optString("weeklyFocusSummary", jsonObj.optString("summary", "7 Günlük Çalışma Programı"))
                        val title = jsonObj.optString("title", summary)
                        val scheduleArray = jsonObj.optJSONArray("schedule")
                            ?: jsonObj.optJSONArray("tasks")
                            ?: jsonObj.optJSONArray("plan")
                            ?: jsonObj.optJSONArray("days")
                        val parsedTasks = mutableListOf<PlanEntity>()

                        if (scheduleArray != null) {
                            for (i in 0 until scheduleArray.length()) {
                                val item = scheduleArray.optJSONObject(i) ?: continue
                                val subj = item.optString("subject", item.optString("course", "Ders")).ifBlank { "Ders" }
                                val top = item.optString("topic", item.optString("title", "")).trim()
                                val rawTime = item.optString("timeSlot", item.optString("time", item.optString("scheduledTime", "")))
                                val time = normalizeFlexibleTimeSlot(rawTime)
                                val questions = item.optInt("targetQuestions", item.optInt("questions", 30))
                                val day = item.optInt("dayOfWeek", item.optInt("day", item.optInt("dayIndex", (i % 7 + 1))))

                                parsedTasks.add(
                                    PlanEntity(
                                        id = UUID.randomUUID().toString(),
                                        folderId = "",
                                        title = top.ifBlank { subj },
                                        subject = subj,
                                        topic = top,
                                        dayOfWeek = day.coerceIn(1, 7),
                                        scheduledTime = time,
                                        targetQuestions = if (questions > 0) questions else 30,
                                        orderIndex = i
                                    )
                                )
                            }
                        }

                        if (parsedTasks.isNotEmpty()) {
                            val newFolderId = withContext(Dispatchers.IO) {
                                val savedFolderId = planRepositoryImpl.saveGeneratedPlan(
                                    folderTitle = if (title.length > 38) title.take(38) + "…" else title,
                                    tasks = parsedTasks
                                )

                                val dayNames = listOf("Pazartesi", "Salı", "Çarşamba", "Perşembe", "Cuma", "Cumartesi", "Pazar")
                                val activePlan = ActivePlanEntity(
                                    id = "CURRENT_ACTIVE_PLAN",
                                    title = title,
                                    strategyNote = summary,
                                    totalTargetQuestions = parsedTasks.sumOf { it.targetQuestions },
                                    updatedAtTimestamp = System.currentTimeMillis()
                                )
                                val studyTasks = parsedTasks.mapIndexed { idx, p ->
                                    val dName = dayNames.getOrElse(p.dayOfWeek - 1) { "Pazartesi" }
                                    StudyTaskEntity(
                                        localId = 0,
                                        planId = "CURRENT_ACTIVE_PLAN",
                                        dayName = dName,
                                        dayIndex = (p.dayOfWeek - 1).coerceIn(0, 6),
                                        time = p.scheduledTime,
                                        subject = p.subject,
                                        topic = p.topic.ifBlank { p.subject },
                                        goal = "${p.targetQuestions} Soru Hedefi",
                                        isCompleted = false
                                    )
                                }
                                database.studyPlanDao().replaceActivePlanWithTasks(activePlan, studyTasks)

                                runCatching {
                                    val firestore = GoogleServicesHelper.getFirebaseFirestore(getApplication())
                                    val uid = GoogleServicesHelper.getCurrentUser(context = getApplication())?.uid
                                    if (uid != null && firestore != null) {
                                        val planMap = hashMapOf<String, Any>(
                                            "id" to "CURRENT_ACTIVE_PLAN",
                                            "title" to title,
                                            "strategyNote" to summary,
                                            "totalTargetQuestions" to activePlan.totalTargetQuestions,
                                            "updatedAtTimestamp" to activePlan.updatedAtTimestamp,
                                            "userId" to uid,
                                            "studentId" to uid,
                                            "tasks" to studyTasks.map { task ->
                                                hashMapOf(
                                                    "dayIndex" to task.dayIndex,
                                                    "dayName" to task.dayName,
                                                    "subject" to task.subject,
                                                    "time" to task.time,
                                                    "topic" to task.topic,
                                                    "goal" to task.goal,
                                                    "isCompleted" to task.isCompleted
                                                )
                                            }
                                        )
                                        firestore.collection("students").document(uid).collection("plans").document("CURRENT_ACTIVE_PLAN").set(planMap, SetOptions.merge())
                                    }
                                }
                                SyncWorker.enqueueImmediateSync(getApplication())
                                savedFolderId
                            }
                            createdFolderId = newFolderId
                            isPlan = true
                            cleanText = cleanText.replace(jsonCandidate, "").trim()

                            val userSummary = if (summary.isNotBlank()) "\n\n**Haftalık Odak Stratejisi:** $summary" else ""
                            cleanText = "**7 Günlük Kişisel Çalışma Programın Hazırlandı.**$userSummary\n\nProgramın gün gün planlandı, hedef soru sayıları belirlendi ve doğrudan **Planlar** sekmesine kaydedildi.\n\n#ACTION:NAV_PLANS"

                            memoryRepository.recordInsight(
                                persona = persona,
                                memoryType = "DECISION",
                                content = "Haftalık Plan Oluşturuldu: $title (${parsedTasks.size} Görev)"
                            )
                        }
                    } catch (e: Exception) {
                        Log.w("SharkAiViewModel", "Fallback schedule parsing error: ${e.message}")
                    }
                }
            }

            if (actionPairs.isNotEmpty() || isPlan) {
                com.example.sharkmentor.core.coaching.StudentDossierManager.syncInBackground(getApplication())
                com.example.sharkmentor.core.coaching.CoachingEngine.refreshTargetAndLedgerAsync(getApplication())
            }

            if (persona.isTemporaryMemory) {
                memoryRepository.recordInsight(
                    persona = persona,
                    memoryType = "EMOTIONAL_LOG",
                    content = cleanText.take(150)
                )
            }
        } catch (e: Exception) {
            Log.e("SharkAiViewModel", "Error processing action/memory", e)
        }

        // Clean out any raw markdown code blocks from the final chat display
        cleanText = cleanText
            .replace(JSON_FENCE_COMPLETE_REGEX, "")
            .replace(CODE_FENCE_COMPLETE_REGEX, "")
            .trim()

        if (cleanText.isBlank() && isPlan) {
            cleanText = "**Haftalık Çalışma Programın Oluşturuldu.**\nProgram Planlar sekmesine gün gün dağıtıldı ve başarıyla kaydedildi.\n\n#ACTION:NAV_PLANS"
        }

        return ProcessActionResult(
            cleanText = cleanText,
            createdPlanFolderId = createdFolderId,
            isPlanCard = isPlan
        )
    }

    private suspend fun batchInsertTopicGoalsDeduped(
        candidates: List<Triple<String, String, Int>>
    ): Int = withContext(Dispatchers.IO) {
        if (candidates.isEmpty()) return@withContext 0
        val existingTopics = database.goalsDao().getAllTopicsRaw()
        val existingKeySet = existingTopics.map {
            "${normalizeForMatch(it.subject)}::${normalizeForMatch(it.topic)}"
        }.toMutableSet()

        val institutionId = com.example.sharkmentor.data.local.preferences.UserPreferences.getInstance(getApplication()).institutionId ?: "INDIVIDUAL"
        val now = System.currentTimeMillis()
        val entitiesToInsert = mutableListOf<TopicGoalEntity>()

        for ((rawSubj, rawTop, targetLvl) in candidates) {
            val trimmedSubj = rawSubj.trim()
            val trimmedTop = rawTop.trim()
            if (trimmedTop.isBlank() || YksCurriculumCatalog.isPlaceholderOrBroadTopic(trimmedTop)) continue

            val catalogMatch = YksCurriculumCatalog.findCurriculumMatch(trimmedSubj, trimmedTop, lastUserPromptText)
            var subj = catalogMatch?.first ?: YksCurriculumCatalog.canonicalizeSubject(trimmedSubj, trimmedTop, lastUserPromptText)
            val top = catalogMatch?.second ?: trimmedTop
            if (subj.isBlank() || subj.equals("Genel", ignoreCase = true) || subj.equals("Ders", ignoreCase = true)) {
                val detected = detectSubjectFromContext("$trimmedSubj $top $lastUserPromptText".lowercase(Locale("tr")))
                if (detected != "Genel") {
                    subj = YksCurriculumCatalog.canonicalizeSubject(detected, top, lastUserPromptText)
                }
            }
            if (subj.isBlank() || YksCurriculumCatalog.isPlaceholderOrBroadTopic(top)) continue

            val key = "${normalizeForMatch(subj)}::${normalizeForMatch(top)}"
            val alreadyTracked = key in existingKeySet ||
                findMatchingTopic(existingTopics + entitiesToInsert, subj, top) != null
            if (!alreadyTracked && existingKeySet.add(key)) {
                entitiesToInsert.add(
                    TopicGoalEntity(
                        subject = subj,
                        topic = top,
                        currentLevel = 0,
                        targetLevel = targetLvl.coerceIn(1, 5),
                        institutionId = institutionId,
                        isSynced = false,
                        updatedAtMillis = now
                    )
                )
            }
        }

        if (entitiesToInsert.isNotEmpty()) {
            database.goalsDao().insertTopicGoals(entitiesToInsert)
            SyncWorker.enqueueImmediateSync(getApplication())
        }
        entitiesToInsert.size
    }

    private fun normalizeForMatch(input: String): String {
        return input.lowercase(Locale("tr"))
            .replace(NON_ALNUM_TR_REGEX, " ")
            .replace(MULTI_SPACE_REGEX, " ")
            .trim()
    }

    private fun inferSubjectFromResourceTitle(title: String, rawSubject: String): String {
        val cleanedRaw = rawSubject.trim()
            .removePrefix("Ders:")
            .removePrefix("ders:")
            .trim()
        val isGenericRaw = cleanedRaw.isBlank() ||
            cleanedRaw.equals("Genel", ignoreCase = true) ||
            cleanedRaw.equals("Ders", ignoreCase = true) ||
            cleanedRaw.equals("Tümü", ignoreCase = true) ||
            cleanedRaw.equals("Diğer", ignoreCase = true) ||
            cleanedRaw.equals("Soru Bankası", ignoreCase = true)

        if (!isGenericRaw) {
            return YksCurriculumCatalog.canonicalizeSubject(cleanedRaw, promptHint = title)
        }

        val lowerTitle = title.lowercase(Locale("tr"))
        if (lowerTitle.isBlank()) return "Genel"

        val baseSubject = when {
            lowerTitle.contains("geometri") || Regex("""\bgeo\b""").containsMatchIn(lowerTitle) -> "Geometri"
            lowerTitle.contains("matematik") || Regex("""\bmat\b""").containsMatchIn(lowerTitle) -> "Matematik"
            lowerTitle.contains("fizik") -> "Fizik"
            lowerTitle.contains("kimya") -> "Kimya"
            lowerTitle.contains("biyoloji") -> "Biyoloji"
            lowerTitle.contains("edebiyat") -> "Edebiyat"
            lowerTitle.contains("türkçe") || lowerTitle.contains("turkce") || lowerTitle.contains("paragraf") || lowerTitle.contains("dil bilgisi") -> "Türkçe"
            lowerTitle.contains("tarih") -> "Tarih"
            lowerTitle.contains("coğrafya") || lowerTitle.contains("cografya") -> "Coğrafya"
            lowerTitle.contains("felsefe") -> "Felsefe"
            lowerTitle.contains("din kültürü") || lowerTitle.contains("din kulturu") -> "Din Kültürü"
            lowerTitle.contains("ingilizce") || Regex("""\bydt\b""").containsMatchIn(lowerTitle) -> "İngilizce"
            lowerTitle.contains("fen bilimleri") -> "Fen Bilimleri"
            lowerTitle.contains("sosyal bilimler") -> "Sosyal Bilimler"
            else -> null
        }
        if (baseSubject != null) {
            return YksCurriculumCatalog.canonicalizeSubject(baseSubject, promptHint = title)
        }
        YksCurriculumCatalog.findCurriculumMatch("", title, title)?.first?.let { return it }
        return "Genel"
    }

    private fun inferUnitTypeFromText(text: String, fallback: String = "Test"): String {
        val lower = text.lowercase(Locale("tr"))
        return when {
            lower.contains("sayfa") || Regex("""\bsf\.?\b""").containsMatchIn(lower) -> "Sayfa"
            lower.contains("test") -> "Test"
            lower.contains("deneme") -> "Deneme"
            lower.contains("bölüm") || lower.contains("bolum") -> "Bölüm"
            Regex("""\bsoru\b""").containsMatchIn(lower) && !lower.contains("soru bankas") -> "Soru"
            else -> fallback
        }
    }

    private fun findMatchingTopic(
        existingTopics: List<TopicGoalEntity>,
        rawSubject: String,
        rawTopic: String
    ): TopicGoalEntity? {
        if (existingTopics.isEmpty() || rawTopic.isBlank()) return null

        existingTopics.firstOrNull {
            it.subject.equals(rawSubject, ignoreCase = true) &&
                it.topic.equals(rawTopic, ignoreCase = true)
        }?.let { return it }

        val compatible = existingTopics.filter {
            YksCurriculumCatalog.areSubjectsCompatible(it.subject, rawSubject)
        }
        val normTopic = normalizeForMatch(rawTopic)
        if (normTopic.isNotBlank()) {
            compatible.firstOrNull { normalizeForMatch(it.topic) == normTopic }?.let { return it }
        }

        val catalogMatch = YksCurriculumCatalog.findCurriculumMatch(rawSubject, rawTopic, lastUserPromptText)
        if (catalogMatch != null) {
            val canonTopicNorm = normalizeForMatch(catalogMatch.second)
            existingTopics.firstOrNull {
                YksCurriculumCatalog.areSubjectsCompatible(it.subject, catalogMatch.first) &&
                    normalizeForMatch(it.topic) == canonTopicNorm
            }?.let { return it }
        }

        val queryTokens = YksCurriculumCatalog.normalizeTokens(rawTopic).filter { it.length >= 3 }
        if (normTopic.length >= 4 && queryTokens.isNotEmpty()) {
            val containmentMatches = compatible.filter { existing ->
                val exNorm = normalizeForMatch(existing.topic)
                exNorm.isNotBlank() && (exNorm.contains(normTopic) || (normTopic.contains(exNorm) && exNorm.length >= 4))
            }
            if (containmentMatches.size == 1) return containmentMatches.first()

            val tokenMatch = compatible.mapNotNull { existing ->
                val exTokens = YksCurriculumCatalog.normalizeTokens(existing.topic).filter { it.length >= 3 }
                val overlap = queryTokens.count { qt ->
                    exTokens.any { et -> et == qt || (qt.length >= 4 && et.startsWith(qt)) || (et.length >= 4 && qt.startsWith(et)) }
                }
                if (overlap > 0 && overlap >= queryTokens.size) existing to overlap else null
            }
            if (tokenMatch.size == 1) return tokenMatch.first().first
        }

        return null
    }

    private fun findMatchingResource(
        resources: List<ResourceEntity>,
        queryTitle: String,
        querySubject: String = ""
    ): ResourceEntity? {
        if (resources.isEmpty()) return null
        val normQuery = normalizeForMatch(queryTitle)
        val cleanSubject = querySubject.trim()
        val titleInferredSubject = inferSubjectFromResourceTitle(queryTitle, "")
        val effectiveSubject = when {
            cleanSubject.isNotBlank() && !cleanSubject.equals("Genel", ignoreCase = true) && !cleanSubject.equals("Ders", ignoreCase = true) ->
                cleanSubject
            titleInferredSubject != "Genel" -> titleInferredSubject
            else -> ""
        }

        val queryCombined = "$querySubject $queryTitle".lowercase(Locale("tr"))
        val qHasTyt = Regex("""\btyt\b""").containsMatchIn(queryCombined)
        val qHasAyt = Regex("""\bayt\b""").containsMatchIn(queryCombined)

        val subjectCompatiblePool = resources.filter { res ->
            val resEffectiveSubj = inferSubjectFromResourceTitle(res.title, res.subject)
            val subjOk = effectiveSubject.isBlank() || YksCurriculumCatalog.areSubjectsCompatible(resEffectiveSubj, effectiveSubject)
            val titleSubjOk = titleInferredSubject == "Genel" || YksCurriculumCatalog.areSubjectsCompatible(resEffectiveSubj, titleInferredSubject)
            val resCombined = "${res.subject} ${res.title}".lowercase(Locale("tr"))
            val resHasTyt = Regex("""\btyt\b""").containsMatchIn(resCombined)
            val resHasAyt = Regex("""\bayt\b""").containsMatchIn(resCombined)
            val examOk = when {
                qHasTyt && !qHasAyt && resHasAyt && !resHasTyt -> false
                qHasAyt && !qHasTyt && resHasTyt && !resHasAyt -> false
                else -> true
            }
            subjOk && titleSubjOk && examOk
        }

        if (subjectCompatiblePool.isEmpty()) return null

        if (normQuery.isBlank()) {
            return if (effectiveSubject.isNotBlank()) subjectCompatiblePool.singleOrNull() else null
        }

        // 1. Exact title match within subject-compatible pool
        subjectCompatiblePool.firstOrNull { normalizeForMatch(it.title) == normQuery }?.let { return it }

        val queryTokens = normQuery.split(" ").filter { it.length >= 2 }
        val distinctiveQueryTokens = queryTokens.filter { it !in RESOURCE_MATCH_STOPWORDS }

        // 2. Substring/containment match only when query has meaningful tokens
        if (normQuery.length >= 5 && (distinctiveQueryTokens.isNotEmpty() || queryTokens.size >= 2)) {
            val containmentMatches = subjectCompatiblePool.filter { res ->
                val normRes = normalizeForMatch(res.title)
                normRes.contains(normQuery) || (normQuery.contains(normRes) && normRes.length >= 5)
            }
            if (containmentMatches.size == 1) return containmentMatches.first()
            if (containmentMatches.size > 1) {
                val scoredContainment = containmentMatches.map { res ->
                    val resNorm = normalizeForMatch(res.title)
                    res to distinctiveQueryTokens.count { tok -> resNorm.contains(tok) }
                }
                val topScore = scoredContainment.maxOf { it.second }
                val winners = scoredContainment.filter { it.second == topScore && topScore > 0 }.map { it.first }
                if (winners.size == 1) return winners.first()
            }
        }

        // 3. Distinctive token overlap against res.title ONLY (never matching res.subject or generic words)
        if (distinctiveQueryTokens.isNotEmpty()) {
            val scored = subjectCompatiblePool.mapNotNull { res ->
                val resTitleNorm = normalizeForMatch(res.title)
                val resTokens = resTitleNorm.split(" ").filter { it.length >= 2 }
                val distinctiveResTokens = resTokens.filter { it !in RESOURCE_MATCH_STOPWORDS }
                val distMatches = distinctiveQueryTokens.count { dt ->
                    distinctiveResTokens.any { rt ->
                        rt == dt || (dt.length >= 4 && rt.startsWith(dt)) || (rt.length >= 4 && dt.startsWith(rt))
                    }
                }
                val passesThreshold = if (distinctiveQueryTokens.size <= 2) {
                    distMatches == distinctiveQueryTokens.size
                } else {
                    distMatches >= distinctiveQueryTokens.size - 1 && distMatches * 10 >= distinctiveQueryTokens.size * 6
                }
                if (!passesThreshold) return@mapNotNull null
                val bonusAll = queryTokens.count { qt -> qt in resTokens }
                res to (distMatches * 100 + bonusAll)
            }
            if (scored.isNotEmpty()) {
                val topScore = scored.maxOf { it.second }
                val winners = scored.filter { it.second == topScore }.map { it.first }
                if (winners.size == 1) return winners.first()
            }
        } else {
            val genericMatches = subjectCompatiblePool.filter { res ->
                val resTokens = normalizeForMatch(res.title).split(" ").filter { it.length >= 2 }
                queryTokens.all { qt -> qt in resTokens }
            }
            return genericMatches.singleOrNull()
        }

        return null
    }

    private fun findMatchingTarget(
        targets: List<TargetEntity>,
        queryTitle: String
    ): TargetEntity? {
        if (targets.isEmpty()) return null
        val normQuery = normalizeForMatch(queryTitle)
        if (normQuery.isBlank()) return targets.firstOrNull()

        targets.firstOrNull { normalizeForMatch(it.title) == normQuery }?.let { return it }
        targets.firstOrNull {
            val normT = normalizeForMatch(it.title)
            normT.contains(normQuery) || normQuery.contains(normT)
        }?.let { return it }

        val queryTokens = normQuery.split(" ").filter { it.length >= 2 }
        if (queryTokens.isNotEmpty()) {
            val best = targets.maxByOrNull { t ->
                val tNorm = normalizeForMatch("${t.title} ${t.category}")
                queryTokens.count { token -> tNorm.contains(token) }
            }
            if (best != null) {
                val tNorm = normalizeForMatch("${best.title} ${best.category}")
                if (queryTokens.count { token -> tNorm.contains(token) } >= 1) {
                    return best
                }
            }
        }
        return null
    }

    private fun extractBalancedJson(raw: String): String? {
        val text = raw
            .replace(CODE_FENCE_OPEN_REGEX, "")
            .replace("```", "")
            .trim()

        var depth = 0
        var start = -1

        for (i in text.indices) {
            when (text[i]) {
                '{' -> {
                    if (depth == 0) start = i
                    depth++
                }
                '}' -> {
                    depth--
                    if (depth == 0 && start != -1) {
                        val candidate = text.substring(start, i + 1)
                        if (candidate.contains("\"schedule\"") || candidate.contains("\"tasks\"") || candidate.contains("CREATE_WEEKLY_PLAN") || candidate.contains("CREATE_PLAN")) return candidate
                        start = -1
                    }
                }
            }
        }
        return null
    }

    private suspend fun handleAiResponse(
        rawResponse: String,
        toolPlanFolderId: String? = null,
        streamingMessageId: String? = null
    ) {
        val actionResult = processActionAndRecordMemory(_uiState.value.currentPersona, rawResponse)
        var finalCleanText = actionResult.cleanText
        var planFolderId = actionResult.createdPlanFolderId ?: toolPlanFolderId
        var isPlanCard = actionResult.isPlanCard

        // Fallback: If CREATE_WEEKLY_PLAN was output as raw JSON instead of action tag
        if (planFolderId == null) {
            val jsonCandidate = extractBalancedJson(rawResponse)
            if (jsonCandidate != null) {
                try {
                    val root = JSONObject(jsonCandidate)
                    val summary = root.optString("weeklyFocusSummary", root.optString("summary", "Haftalık Çalışma Planı")).trim()
                        .ifBlank { "Haftalık Çalışma Planı" }
                    val scheduleArray = root.optJSONArray("schedule")
                        ?: root.optJSONArray("tasks")
                        ?: root.optJSONArray("plan")
                        ?: root.optJSONArray("days")

                    if (scheduleArray != null && scheduleArray.length() > 0) {
                        val parsedTasks = mutableListOf<PlanEntity>()
                        for (i in 0 until scheduleArray.length()) {
                            val item = scheduleArray.getJSONObject(i)
                            val subject = item.optString("subject", item.optString("course", "Ders")).ifBlank { "Ders" }
                            val topic = item.optString("topic", item.optString("title", "")).trim()
                            val rawTimeSlot = item.optString(
                                "timeSlot",
                                item.optString("time", item.optString("scheduledTime", ""))
                            )
                            val timeSlot = normalizeFlexibleTimeSlot(rawTimeSlot)
                            val rawQ = item.optInt("targetQuestions", item.optInt("questions", 30))

                            parsedTasks.add(
                                PlanEntity(
                                    id = UUID.randomUUID().toString(),
                                    folderId = "",
                                    title = topic.ifBlank { subject },
                                    subject = subject,
                                    topic = topic,
                                    dayOfWeek = item.optInt("dayOfWeek", item.optInt("day", (i % 7 + 1))).coerceIn(1, 7),
                                    scheduledTime = timeSlot,
                                    targetQuestions = if (rawQ > 0) rawQ else 30,
                                    orderIndex = i
                                )
                            )
                        }

                        val newFolderId = withContext(Dispatchers.IO) {
                            val savedFolderId = planRepositoryImpl.saveGeneratedPlan(
                                folderTitle = if (summary.length > 38) summary.take(38) + "…" else summary,
                                tasks = parsedTasks
                            )

                            val dayNames = listOf("Pazartesi", "Salı", "Çarşamba", "Perşembe", "Cuma", "Cumartesi", "Pazar")
                            val activePlan = ActivePlanEntity(
                                id = "CURRENT_ACTIVE_PLAN",
                                title = summary,
                                strategyNote = summary,
                                totalTargetQuestions = parsedTasks.sumOf { it.targetQuestions },
                                updatedAtTimestamp = System.currentTimeMillis()
                            )
                            val studyTasks = parsedTasks.mapIndexed { idx, p ->
                                val dName = dayNames.getOrElse(p.dayOfWeek - 1) { "Pazartesi" }
                                StudyTaskEntity(
                                    localId = 0,
                                    planId = "CURRENT_ACTIVE_PLAN",
                                    dayName = dName,
                                    dayIndex = (p.dayOfWeek - 1).coerceIn(0, 6),
                                    time = p.scheduledTime,
                                    subject = p.subject,
                                    topic = p.topic.ifBlank { p.subject },
                                    goal = "${p.targetQuestions} Soru Hedefi",
                                    isCompleted = false
                                )
                            }
                            database.studyPlanDao().replaceActivePlanWithTasks(activePlan, studyTasks)

                            runCatching {
                                val firestore = GoogleServicesHelper.getFirebaseFirestore(getApplication())
                                val uid = GoogleServicesHelper.getCurrentUser(context = getApplication())?.uid
                                if (uid != null && firestore != null) {
                                    val planMap = hashMapOf<String, Any>(
                                        "id" to "CURRENT_ACTIVE_PLAN",
                                        "title" to summary,
                                        "strategyNote" to summary,
                                        "totalTargetQuestions" to activePlan.totalTargetQuestions,
                                        "updatedAtTimestamp" to activePlan.updatedAtTimestamp,
                                        "userId" to uid,
                                        "studentId" to uid,
                                        "tasks" to studyTasks.map { task ->
                                            hashMapOf(
                                                "dayIndex" to task.dayIndex,
                                                "dayName" to task.dayName,
                                                "subject" to task.subject,
                                                "time" to task.time,
                                                "topic" to task.topic,
                                                "goal" to task.goal,
                                                "isCompleted" to task.isCompleted
                                            )
                                        }
                                    )
                                    firestore.collection("students").document(uid).collection("plans").document("CURRENT_ACTIVE_PLAN").set(planMap, SetOptions.merge())
                                }
                            }
                            com.example.sharkmentor.core.coaching.StudentDossierManager.syncInBackground(getApplication())
                            com.example.sharkmentor.core.coaching.CoachingEngine.refreshTargetAndLedgerAsync(getApplication())
                            SyncWorker.enqueueImmediateSync(getApplication())
                            savedFolderId
                        }
                        planFolderId = newFolderId
                        isPlanCard = true
                        finalCleanText = if (finalCleanText.isBlank()) summary else finalCleanText
                    }
                } catch (e: Exception) {
                    Log.e("SharkAiViewModel", "JSON parse fallback failed: ${e.message}", e)
                }
            }
        }

        val baseMessages = if (streamingMessageId != null) {
            _uiState.value.messages.filter { it.id != streamingMessageId }
        } else {
            _uiState.value.messages
        }

        val aiMsg = AiChatMessage(
            id = streamingMessageId ?: UUID.randomUUID().toString(),
            sender = "ai",
            text = finalCleanText.ifBlank { "İşlem tamamlandı." },
            personaTag = _uiState.value.currentPersona.id,
            planFolderId = planFolderId,
            isPlanCard = isPlanCard
        )

        _uiState.value = _uiState.value.copy(
            messages = baseMessages + aiMsg,
            isLoading = false
        )
        persistCurrentChatState()
    }

    private fun getSystemPromptForPersona(
        persona: AiPersona,
        includeActionInstructions: Boolean = true,
        includeMandatoryRules: Boolean = true
    ): String {
        val rulesBlock = if (includeMandatoryRules) "\n${AiRouterRepository.MANDATORY_PERSONA_RULES}" else ""

        val actionInstruction = if (includeActionInstructions) """

            AKSİYON VE EYLEM TALİMATLARI (HEM METİN HEM GÖRSEL ANALİZİNDE GEÇERLİDİR):
            - Eğer öğrenci çalışma planı, haftalık program veya ders çalışma çizelgesi istiyorsa 7 güne ve çoklu derse dengeli, katı saat aralığı olmadan esnek sürelerle (`"Esnek • 90 dk"`) mesajının EN SONUNA plan eylemini ekle. KRİTİK KURAL: Önceki plandan EKSİK/TAMAMLANMAMIŞ görevleri mutlaka yeni planın ilk günlerine devret; TAMAMLANMIŞ görevleri (konu hakimiyeti ≤ 2/5 değilse) tekrar tekrar yazma:
            #ACTION:{"type":"CREATE_WEEKLY_PLAN","title":"Haftalık Çalışma Programı","summary":"Haftalık odak stratejisi","schedule":[{"dayOfWeek":1,"timeSlot":"Esnek • 90 dk","subject":"Matematik","topic":"Türev","targetQuestions":40}]}#
            - Eğer öğrenci "TYT tüm konular", "AYT tüm konular" veya bir dersin tüm konularını/müfredatını eklemek istiyorsa ASLA ders başına tek boş klasör açma; INSERT_TOPICS_BATCH kullan:
            #ACTION:{"type":"INSERT_TOPICS_BATCH","exam":"TYT","preset":"TYT_ALL","topics":[{"subject":"TYT Türkçe","topic":"Sözcükte Anlam","targetLevel":5},{"subject":"TYT Matematik","topic":"Temel Kavramlar","targetLevel":5}]}#
            - Eğer öğrenci tek bir konu hedefi eklemek veya seviyesini güncellemek istiyorsa, doğru ders adını (TYT/AYT ayrımıyla, örn. Türev için AYT Matematik) ve konuyu seç:
            #ACTION:{"type":"INSERT_TOPIC","subject":"Ders Adı","topic":"Konu Adı","currentLevel":1,"targetLevel":5}#
            - Eğer öğrenci mevcut kütüphanesindeki bir kitabı ("X kitabını 45. sayfaya/teste kadar çözdüm işaretle") güncellemek istiyorsa YENİ KİTAP EKLEME, [MEVCUT KÜTÜPHANE KAYNAKLARI] listesindeki doğru `resourceId`, tam kitap adı ve dersiyle UPDATE_RESOURCE_PROGRESS kullan:
            #ACTION:{"type":"UPDATE_RESOURCE_PROGRESS","resourceId":0,"title":"Kitap Adı","subject":"Ders Adı","completedUnits":45,"unitType":"Sayfa","isIncremental":false}#
            - Eğer öğrenci kütüphanede henüz olmayan YENİ bir kaynak/kitap eklemek istiyorsa (öğrenci toplam sayfa/test sayısını belirtmediyse `totalUnits`: 0 yaz, ASLA tahmini sayı uydurma):
            #ACTION:{"type":"INSERT_RESOURCE","title":"Kitap Adı","subject":"Ders Adı","totalUnits":0,"completedUnits":0,"unitType":"Test"}#
            - Eğer öğrenci tekil veya toplu hedef belirlemek ya da mevcut hedef ilerlemesini güncellemek istiyorsa:
            #ACTION:{"type":"INSERT_TARGET","title":"Hedef Başlığı","description":"Açıklama","type":"SHORT_TERM","period":"WEEKLY","targetQuestions":100,"priority":"HIGH"}#
            #ACTION:{"type":"INSERT_TARGETS_BATCH","targets":[{"title":"Hedef Başlığı","description":"Açıklama","type":"SHORT_TERM","period":"WEEKLY","targetQuestions":100,"priority":"HIGH"}]}#
            #ACTION:{"type":"UPDATE_TARGET_PROGRESS","targetId":0,"title":"Hedef Başlığı","solvedQuestions":50,"isCompleted":false,"isIncremental":false}#
            - Eğer öğrencinin hedefi yoksa veya hedeflerini güncellemesi gerekiyorsa mesajın en sonuna '#ACTION:NAV_TARGETS' ekle.
            - Eğer konuşma bittiyse ve öğrenciyi çalışmaya teşvik ediyorsan ('Hadi başlayalım', 'Masaya geçelim' vb.) mesajın sonuna '#ACTION:NAV_DESK' ekle.
            - Eğer plan oluşturduysan veya planı incelemesini istiyorsan mesajın sonuna '#ACTION:NAV_PLANS' ekle.
        """.trimIndent() else ""

        val lengthGuide = "Açıklamaları net, anlaşılır ve dengeli tut; kısa selamlamalarda maksimum 1 cümle kur."

        val basePrompt = when (persona) {
            AiPersona.SHARK_COACH -> """
                Sen SharkMentor'un kıdemli sınav koçu ve akran mentörüsün.
                MİSYONUN: Öğrenciye net analizi, stratejik yönlendirme ve odak desteği sağla.
                TONUN: Doğal, net, sıfır laf kalabalığı, analitik ve samimi ($lengthGuide).$rulesBlock
            """.trimIndent()

            AiPersona.GOAL_SETTER -> """
                Sen SharkMentor Hedef Belirleme AI koçusun.
                MİSYONUN: Öğrencinin muğlak isteklerini somut net hedeflerine ve kısa/uzun vadeli sıralama rotalarına dönüştür.
                TONUN: Odaklı, net, sayısal ve aksiyon odaklı ($lengthGuide).$rulesBlock
            """.trimIndent()

            AiPersona.ZEN_CALM -> """
                Sen SharkMentor Empatik Zen Sakinleşme Koçusun.
                MİSYONUN: Sınav kaygısı, stres ve tükenmişlik hisseden öğrenciye psikolojik zemin sağla, nefes porsiyonlaması ver.
                TONUN: Şefkatli, ferahlatıcı, sakin ve içten ($lengthGuide).$rulesBlock
            """.trimIndent()

            AiPersona.DOPAMINE_COACH -> """
                Sen SharkMentor Dopamin Orucu ve Nöral Odak Koçusun.
                MİSYONUN: Öğrencinin sosyal medya, erteleme ve uyarım dürtülerini yönetmesine yardımcı ol, irade sürtünmesini azalt.
                TONUN: Kararlı, nörolojik gerçeklere dayalı ve net ($lengthGuide).$rulesBlock
            """.trimIndent()

            AiPersona.STUDY_PLANNER -> if (includeActionInstructions) {
                """
                    Sen SharkMentor Otonom Çalışma Planlayıcısısın.
                    GÖREV: Öğrencinin talebine ve [MEVCUT PLAN İLERLEME VE EKSİK DENETİMİ] kaydına dayanarak 7 günlük (Pazartesi-Pazar) esnek çalışma planı hazırla.
                    KÜMÜLATİF KURAL: Önceki plandan tamamlanmayan/eksik kalan görevleri mutlaka öncelikli olarak yeni plana devret; halihazırda tamamlanmış görevleri (konu hakimiyeti ≤ 2/5 değilse) gereksiz yere tekrar ettirme.
                    1. Önce öğrenciye 2-3 samimi cümleyle bu haftaki çalışma stratejisini, eksiklerini nasıl toparlayacağını açıkla.
                    2. Ardından mesajın en sonuna çalışma programını aşağıdaki sistem eylem formatında ekle:
                    #ACTION:{"type":"CREATE_WEEKLY_PLAN","title":"Haftalık Çalışma Programı","summary":"Bu haftaki stratejik yönelim özeti","schedule":[{"dayOfWeek":1,"timeSlot":"Esnek • 90 dk","subject":"Matematik","topic":"Türev","targetQuestions":40,"focusStrategy":"Konu Pekiştirme"}]}#$rulesBlock
                """.trimIndent()
            } else {
                """
                    Sen SharkMentor Otonom Çalışma Planlayıcısısın.
                    GÖREV: Öğrencinin talebine ve [MEVCUT PLAN İLERLEME VE EKSİK DENETİMİ] kaydına dayanarak 7 günlük (Pazartesi-Pazar) esnek çalışma planı hazırla.
                    KÜMÜLATİF KURAL: Önceki plandan tamamlanmayan/eksik kalan görevleri mutlaka öncelikli olarak yeni plana devret; halihazırda tamamlanmış görevleri (konu hakimiyeti ≤ 2/5 değilse) tekrar ettirme.
                    Önce öğrenciye 2-3 samimi cümleyle bu haftaki çalışma stratejisini, eksiklerini nasıl toparlayacağını açıkla.$rulesBlock
                """.trimIndent()
            }
        }

        return if (actionInstruction.isNotEmpty()) "$basePrompt\n$actionInstruction" else basePrompt
    }

    /**
     * Shark AI'a atılan soru görselini/belgesini arka planda (Dispatchers.IO)
     * asenkron ve sıfır gecikmeyle (UI veya AI modellerini hiç yavaşlatmadan)
     * Antrenman Hata Bankasına (ErrorQuestionEntity) kaydeder.
     */
    private fun autoSaveQuestionToErrorBank(
        attachmentUri: Uri,
        resolvedAttachmentName: String?,
        userPrompt: String,
        aiResponseText: String
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = getApplication<Application>().applicationContext
                val resolver = context.contentResolver
                val mimeType = resolver.getType(attachmentUri) ?: ""
                val isImage = mimeType.startsWith("image/") ||
                    resolvedAttachmentName?.endsWith(".jpg", ignoreCase = true) == true ||
                    resolvedAttachmentName?.endsWith(".jpeg", ignoreCase = true) == true ||
                    resolvedAttachmentName?.endsWith(".png", ignoreCase = true) == true ||
                    resolvedAttachmentName?.endsWith(".webp", ignoreCase = true) == true
                val isPdf = mimeType == "application/pdf" || resolvedAttachmentName?.endsWith(".pdf", ignoreCase = true) == true

                if (!isImage && !isPdf) return@launch

                // 1. Resmi optimize ederek kalıcı dahili depolamaya kaydet
                val savedImagePath = if (isPdf) {
                    val bitmaps = PdfBitmapConverter.convertPdfUriToBitmaps(context, attachmentUri, maxPages = 1)
                    val firstPage = bitmaps.firstOrNull()
                    if (firstPage != null) {
                        val file = File(context.filesDir, "shark_ai_q_${System.currentTimeMillis()}.jpg")
                        file.outputStream().use { out ->
                            firstPage.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, out)
                        }
                        file.absolutePath
                    } else null
                } else {
                    ImageStorageManager.processAndSaveImage(context, attachmentUri, isQuestion = true)
                }

                if (savedImagePath.isNullOrBlank()) return@launch

                // 2. İçeriğin gerçekten bir soru / sınav sorusu olduğunu doğrula (alakasız sıradan görselleri engelle)
                val combinedText = "$userPrompt $resolvedAttachmentName $aiResponseText".lowercase(java.util.Locale("tr"))
                val hasQuestionSignal = combinedText.contains("soru") ||
                    combinedText.contains("çözüm") ||
                    combinedText.contains("cozum") ||
                    combinedText.contains("doğru cevap") ||
                    combinedText.contains("dogru cevap") ||
                    combinedText.contains("cevap:") ||
                    combinedText.contains("şık") ||
                    combinedText.contains("seçenek") ||
                    combinedText.contains("adım 1") ||
                    combinedText.contains("1. adım") ||
                    combinedText.contains("denklem") ||
                    OPTION_PAREN_REGEX.containsMatchIn(aiResponseText) ||
                    OPTION_WORD_REGEX.containsMatchIn(aiResponseText)

                if (!hasQuestionSignal) {
                    Log.d("SharkAiAutoSave", "Görsel bir soru çözümü niteliği taşımıyor, Antrenman Bankasına kaydedilmedi.")
                    return@launch
                }

                // 3. Metin analiziyle Ders ve Konuyu tespit et
                val detectedSubject = detectSubjectFromContext(combinedText)
                val detectedTopic = detectTopicFromContext(combinedText, detectedSubject)

                // 4. Room veritabanına ekle
                val db = SharkMentorDatabase.getDatabase(context)
                val institutionId = com.example.sharkmentor.data.local.preferences.UserPreferences.getInstance(context).institutionId
                val entity = ErrorQuestionEntity(
                    subject = detectedSubject,
                    questionImageUri = savedImagePath,
                    solutionImageUri = null,
                    topic = detectedTopic,
                    aiTopic = detectedTopic,
                    dueDateMillis = System.currentTimeMillis(),
                    institutionId = institutionId,
                    isSynced = false
                )
                db.errorQuestionDao().insertQuestion(entity)

                // 5. Arka plan senkronizasyonu
                SyncWorker.enqueueImmediateSync(context)
                if (detectedTopic == "Shark AI Soruları" || detectedTopic == "Genel") {
                    TopicTaggingWorker.enqueue(context)
                }
                Log.d("SharkAiAutoSave", "Soru arka planda Antrenman Bankasına eklendi: Ders=$detectedSubject, Konu=$detectedTopic")
            } catch (e: Exception) {
                Log.w("SharkAiAutoSave", "Soru Antrenman Bankasına kaydedilirken arka plan hatası: ${e.message}")
            }
        }
    }

    private fun detectSubjectFromContext(text: String): String {
        return when {
            text.contains("geometri") || text.contains("üçgen") || text.contains("çember") || text.contains("dörtgen") || text.contains("analitik") -> "Geometri"
            text.contains("matematik") || text.contains("türev") || text.contains("integral") || text.contains("fonksiyon") || text.contains("polinom") || text.contains("logaritma") || text.contains("olasılık") || text.contains("trigonometri") || text.contains("denklem") -> "Matematik"
            text.contains("fizik") || text.contains("kuvvet") || text.contains("dinamik") || text.contains("elektrik") || text.contains("manyetizma") || text.contains("optik") || text.contains("dalga") || text.contains("termodinamik") || text.contains("vektör") -> "Fizik"
            text.contains("kimya") || text.contains("periyodik") || text.contains("organik") || text.contains("asit") || text.contains("gazlar") || text.contains("mol") || text.contains("çözelti") || text.contains("tepkime") -> "Kimya"
            text.contains("biyoloji") || text.contains("hücre") || text.contains("dna") || text.contains("genetik") || text.contains("fotosentez") || text.contains("ekosistem") || text.contains("sistemler") -> "Biyoloji"
            text.contains("tarih") || text.contains("osmanlı") || text.contains("inkılap") || text.contains("selçuklu") || text.contains("savaş") -> "Tarih"
            text.contains("coğrafya") || text.contains("harita") || text.contains("iklim") || text.contains("nüfus") || text.contains("yerşekilleri") -> "Coğrafya"
            text.contains("türkçe") || text.contains("edebiyat") || text.contains("paragraf") || text.contains("dil bilgisi") || text.contains("yazım") || text.contains("noktalama") || text.contains("şiir") || text.contains("divan") || text.contains("tanzimat") || text.contains("yazar") -> "Türkçe / Edebiyat"
            text.contains("felsefe") || text.contains("din") -> "Felsefe / Din"
            else -> "Genel"
        }
    }

    private fun detectTopicFromContext(text: String, subject: String): String {
        return when (subject) {
            "Matematik" -> when {
                text.contains("türev") -> "Türev"
                text.contains("integral") -> "İntegral"
                text.contains("limit") -> "Limit ve Süreklilik"
                text.contains("trigonometri") -> "Trigonometri"
                text.contains("logaritma") -> "Logaritma"
                text.contains("dizi") || text.contains("seri") -> "Diziler"
                text.contains("polinom") -> "Polinomlar"
                text.contains("fonksiyon") -> "Fonksiyonlar"
                text.contains("olasılık") || text.contains("kombinasyon") || text.contains("permütasyon") -> "Olasılık ve Kombinasyon"
                text.contains("parabol") -> "Parabol"
                text.contains("eşitsizlik") -> "Eşitsizlikler"
                text.contains("üslü") || text.contains("köklü") -> "Üslü ve Köklü Sayılar"
                text.contains("çarpanlara ayırma") -> "Çarpanlara Ayırma"
                text.contains("küme") -> "Kümeler"
                text.contains("problem") -> "Problemler"
                text.contains("temel kavram") || text.contains("sayı") -> "Temel Kavramlar"
                else -> "Shark AI Soruları"
            }
            "Geometri" -> when {
                text.contains("üçgen") -> "Üçgenler"
                text.contains("çember") || text.contains("daire") -> "Çember ve Daire"
                text.contains("dörtgen") || text.contains("kare") || text.contains("dikdörtgen") -> "Dörtgenler"
                text.contains("analitik") -> "Analitik Geometri"
                text.contains("katı cisim") || text.contains("prizma") || text.contains("koni") -> "Katı Cisimler"
                else -> "Shark AI Soruları"
            }
            "Fizik" -> when {
                text.contains("kuvvet") || text.contains("hareket") || text.contains("newton") -> "Kuvvet ve Hareket"
                text.contains("elektrik") || text.contains("manyetizma") || text.contains("akım") -> "Elektrik ve Manyetizma"
                text.contains("optik") || text.contains("mercek") || text.contains("ayna") -> "Optik"
                text.contains("dalga") -> "Dalgalar"
                text.contains("iş") || text.contains("güç") || text.contains("enerji") -> "İş, Güç ve Enerji"
                text.contains("termodinamik") || text.contains("ısı") || text.contains("sıcaklık") -> "Termodinamik"
                text.contains("basınç") || text.contains("kaldırma") -> "Basınç ve Kaldırma Kuvveti"
                text.contains("modern fizik") || text.contains("radyoaktivite") -> "Modern Fizik"
                else -> "Shark AI Soruları"
            }
            "Kimya" -> when {
                text.contains("organik") -> "Organik Kimya"
                text.contains("periyodik") || text.contains("atom") -> "Atom ve Periyodik Sistem"
                text.contains("gaz") -> "Gazlar"
                text.contains("sıvı çözelti") || text.contains("çözelti") || text.contains("derişim") -> "Sıvı Çözeltiler"
                text.contains("kimyasal hesaplama") || text.contains("mol") -> "Mol ve Hesaplamalar"
                text.contains("termodinamik") || text.contains("entalpi") || text.contains("enerji") -> "Kimyasal Tepkimelerde Enerji"
                text.contains("hız") -> "Tepkime Hızları"
                text.contains("denge") -> "Kimyasal Denge"
                text.contains("asit") || text.contains("baz") -> "Asitler ve Bazlar"
                text.contains("elektrokimya") || text.contains("redoks") || text.contains("pil") -> "Elektrokimya"
                else -> "Shark AI Soruları"
            }
            "Biyoloji" -> when {
                text.contains("hücre") -> "Hücre ve Organeller"
                text.contains("dna") || text.contains("rna") || text.contains("genetik") -> "Genetik ve Nükleik Asitler"
                text.contains("fotosentez") || text.contains("kemosentez") || text.contains("solunum") -> "Hücresel Solunum ve Fotosentez"
                text.contains("sinir") || text.contains("endokrin") || text.contains("duyu") -> "Denetleyici ve Düzenleyici Sistemler"
                text.contains("dolaşım") || text.contains("bağışıklık") -> "Dolaşım ve Bağışıklık Sistemi"
                text.contains("sindirim") -> "Sindirim Sistemi"
                text.contains("boşaltım") -> "Boşaltım Sistemi"
                text.contains("destek") || text.contains("hareket") -> "Destek ve Hareket Sistemi"
                text.contains("ekosistem") || text.contains("çevre") -> "Ekosistem Ekolojisi"
                text.contains("kalıtım") -> "Kalıtım"
                else -> "Shark AI Soruları"
            }
            "Türkçe / Edebiyat", "Türkçe", "Edebiyat" -> when {
                text.contains("paragraf") -> "Paragrafta Anlam"
                text.contains("cümle") -> "Cümlede Anlam"
                text.contains("sözcük") -> "Sözcükte Anlam"
                text.contains("yazım") -> "Yazım Kuralları"
                text.contains("noktalama") -> "Noktalama İşaretleri"
                text.contains("ses bilgisi") -> "Ses Bilgisi"
                text.contains("fiil") || text.contains("eylem") -> "Fiiller ve Ek Fiil"
                text.contains("öge") -> "Cümlenin Ögeleri"
                text.contains("anlatım bozukluğu") -> "Anlatım Bozuklukları"
                text.contains("divan") -> "Divan Edebiyatı"
                text.contains("tanzimat") -> "Tanzimat Edebiyatı"
                text.contains("halk") -> "Halk Edebiyatı"
                text.contains("cumhuriyet") -> "Cumhuriyet Dönemi Edebiyatı"
                text.contains("servetifünun") || text.contains("fecriati") -> "Servetifünun ve Fecriati"
                text.contains("şiir") -> "Şiir Bilgisi"
                else -> "Shark AI Soruları"
            }
            "Tarih" -> when {
                text.contains("osmanlı") -> "Osmanlı Tarihi"
                text.contains("inkılap") || text.contains("atatürk") || text.contains("kurtuluş savaşı") -> "İnkılap Tarihi ve Atatürkçülük"
                text.contains("ilk türk") || text.contains("orta asya") -> "İlk Türk Devletleri"
                text.contains("islam") -> "Türk-İslam Tarihi"
                text.contains("dünya savaşı") -> "20. Yüzyıl ve Dünya Savaşları"
                else -> "Shark AI Soruları"
            }
            "Coğrafya" -> when {
                text.contains("iklim") -> "İklim Bilgisi"
                text.contains("harita") -> "Harita Bilgisi"
                text.contains("nüfus") || text.contains("yerleşme") -> "Nüfus ve Yerleşme"
                text.contains("yer şekilleri") || text.contains("iç kuvvetler") || text.contains("dış kuvvetler") -> "Yer Şekilleri ve Kuvvetler"
                text.contains("ekonomik") || text.contains("tarım") || text.contains("sanayi") -> "Ekonomik Coğrafya"
                else -> "Shark AI Soruları"
            }
            "Felsefe / Din", "Felsefe", "Din" -> when {
                text.contains("din") || text.contains("inanç") || text.contains("ibadet") || text.contains("kuran") -> "Din Kültürü ve Ahlak Bilgisi"
                text.contains("bilgi felsefesi") || text.contains("epistemoloji") -> "Bilgi Felsefesi"
                text.contains("varlık felsefesi") || text.contains("ontoloji") -> "Varlık Felsefesi"
                text.contains("ahlak felsefesi") || text.contains("etik") -> "Ahlak Felsefesi"
                text.contains("siyaset felsefesi") -> "Siyaset Felsefesi"
                else -> "Felsefe ve Din"
            }
            else -> "Shark AI Soruları"
        }
    }
}
