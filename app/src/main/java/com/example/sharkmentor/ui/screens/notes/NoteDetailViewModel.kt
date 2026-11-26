package com.example.sharkmentor.ui.screens.notes

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.sharkmentor.core.ai.AiProviderChoice
import com.example.sharkmentor.core.ai.DeepSeekClient
import com.example.sharkmentor.core.ai.DocumentAiScope
import com.example.sharkmentor.core.ai.GeminiAiFactory
import com.example.sharkmentor.core.ai.MultiProviderAiClient
import com.example.sharkmentor.core.ai.NoteDocumentAiHelper
import com.example.sharkmentor.core.ai.UserAiPreferences
import com.example.sharkmentor.core.util.NoteStyleParser
import com.example.sharkmentor.core.util.PaperPattern
import com.example.sharkmentor.core.util.SharkStudioBackgroundHelper
import com.example.sharkmentor.data.local.SharkMentorDatabase
import com.example.sharkmentor.data.local.entity.NoteEntity
import com.example.sharkmentor.data.repository.NoteRepository
import com.example.sharkmentor.data.repository.NoteRepositoryImpl
import com.example.sharkmentor.ui.screens.notes.drawing.DrawingSerializer
import com.example.sharkmentor.ui.screens.notes.drawing.DrawingStroke
import com.example.sharkmentor.ui.screens.notes.drawing.DrawingTextBox
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

@OptIn(FlowPreview::class)
class NoteDetailViewModel(
    private val noteRepository: NoteRepository,
    private val appContext: Context? = null
) : ViewModel() {

    companion object {
        const val ISOLATED_NOTE_SYSTEM_INSTRUCTION = """
Sen öğrencilere ders çalışma ve not analizi konusunda rehberlik eden uzman bir AI çalışma asistanısın. YALNIZCA verilen not içeriğini ve belgedeki bilgileri baz al. Asla belgede olmayan uydurma bilgi ekleme. Eğer sorulan bilgi notta yoksa açıkça 'Bu bilgi not içinde bulunamadı.' yaz.
KESİN DAVRANIŞ KURALLARI:
1. DOĞRUDAN VE ETKİLİ BAŞLANGIÇ: "Harika bir not!", "Merhaba!" gibi yapay girişler kullanma. Doğrudan analiz, özet veya soru cevabına gir.
2. DERİNLEMESİNE VE DETAYLI ANLATIM: Kullanıcının sorusuna veya özet isteğine göre kapsamlı, öğretici ve doyurucu açıklamalar sun. Önemli kavramları, formülleri, tanımları ve alt başlıkları detaylandır. Yüzeysel veya aşırı kısa cevaplar verme; öğrencinin konuyu eksiksiz ve derinlemesine anlamasını sağla.
3. DÜZENLİ FORMATLAMA: Bilgileri net başlıklar (Markdown), maddeler, formüller ve vurgulu anahtar kelimeler ile sun.
4. ÇALIŞMA KANCASI: Yanıtın en sonuna, öğrencinin konuyu pekiştirmesi için tek cümlelik doğal bir çalışma önerisi veya takip sorusu ekle.
"""

        const val NOTE_LAYOUT_PREFS = "note_layout_prefs"
        const val KEY_DEFAULT_PAPER_PATTERN = "default_paper_pattern"
        const val KEY_DEFAULT_PAPER_TINT = "default_paper_tint"
        const val NOTE_PAGE_BREAK_DELIMITER = "\n<!--SHARK_PAGE_BREAK-->\n"
        private val PAGE_BREAK_SPLIT_REGEX = Regex("\\n?<!--SHARK_PAGE_BREAK-->\\n?")

        private val BASE64_OR_PATH_REGEX = Regex(
            pattern = "(\\[PDF_(?:PAGE|FILE):[^\\]]+\\]|data:image/[^;]+;base64,[A-Za-z0-9+/=]+|file:///[^\\s]+|/data/user/[^\\s]+|[A-Za-z0-9+/=]{160,})"
        )

        private val PDF_PAGE_TAG_REGEX = Regex("\\[PDF_PAGE:([^\\]]+)\\]")
        private val HTML_TAG_REGEX = Regex("<[^>]+>")
        private val EXCESS_NEWLINES_REGEX = Regex("\\n{3,}")
        private val PDF_HEADER_REGEX = Regex("^[^\\n]+\\([0-9]+\\s*sayfa\\)", RegexOption.IGNORE_CASE)

        fun splitContentIntoPages(cleanContent: String): List<String> {
            if (cleanContent.isEmpty()) return listOf("")
            val parts = cleanContent.split(PAGE_BREAK_SPLIT_REGEX)
            return if (parts.isEmpty()) listOf("") else parts
        }

        fun joinPagesToContent(pages: List<String>): String {
            if (pages.isEmpty()) return ""
            return pages.joinToString(separator = NOTE_PAGE_BREAK_DELIMITER)
        }

        fun extractPdfPagePaths(rawContent: String): List<String> {
            return PDF_PAGE_TAG_REGEX.findAll(rawContent)
                .map { it.groupValues[1] }
                .filter { it.isNotBlank() }
                .toList()
        }

        fun sanitizeNoteTextForAi(raw: String): String {
            return raw
                .replace(PAGE_BREAK_SPLIT_REGEX, "\n\n")
                .replace(BASE64_OR_PATH_REGEX, "")
                .replace(HTML_TAG_REGEX, "")
                .replace(EXCESS_NEWLINES_REGEX, "\n\n")
                .trim()
                .take(12_000)
        }
    }

    fun getPreferredDefaultColorHex(): String {
        val prefs = runCatching {
            appContext?.getSharedPreferences(NOTE_LAYOUT_PREFS, Context.MODE_PRIVATE)
        }.getOrNull()
        val savedTint = prefs?.getString(KEY_DEFAULT_PAPER_TINT, "#F8FAFC")
            ?.takeIf { it.startsWith("#") }
            ?: "#F8FAFC"
        val savedPatternName = prefs?.getString(KEY_DEFAULT_PAPER_PATTERN, PaperPattern.PLAIN.name)
            ?: PaperPattern.PLAIN.name
        val savedPattern = PaperPattern.entries.find {
            it.name.equals(savedPatternName, ignoreCase = true)
        } ?: PaperPattern.PLAIN
        return NoteStyleParser.encode(savedTint, savedPattern)
    }

    private val _noteId = MutableStateFlow(UUID.randomUUID().toString())
    val noteId: StateFlow<String> = _noteId.asStateFlow()

    private val _noteTitle = MutableStateFlow("")
    val noteTitle: StateFlow<String> = _noteTitle.asStateFlow()

    private val _noteContent = MutableStateFlow("")
    val noteContent: StateFlow<String> = _noteContent.asStateFlow()

    private val _colorHex = MutableStateFlow(getPreferredDefaultColorHex())
    val colorHex: StateFlow<String> = _colorHex.asStateFlow()

    private val _isPinned = MutableStateFlow(false)
    val isPinned: StateFlow<Boolean> = _isPinned.asStateFlow()

    private val _createdAt = MutableStateFlow(System.currentTimeMillis())

    private val _aiResult = MutableStateFlow<String?>(null)
    val aiResult: StateFlow<String?> = _aiResult.asStateFlow()

    private val _isAiLoading = MutableStateFlow(false)
    val isAiLoading: StateFlow<Boolean> = _isAiLoading.asStateFlow()

    private val _aiError = MutableStateFlow<String?>(null)
    val aiError: StateFlow<String?> = _aiError.asStateFlow()

    private val _aiSavedNewNoteId = MutableStateFlow<String?>(null)
    val aiSavedNewNoteId: StateFlow<String?> = _aiSavedNewNoteId.asStateFlow()

    private val _aiScopeNotice = MutableStateFlow<String?>(null)
    val aiScopeNotice: StateFlow<String?> = _aiScopeNotice.asStateFlow()

    private var aiAnalysisJob: Job? = null

    @Volatile
    private var loadGeneration: Long = 0L
    private var loadJob: Job? = null
    private val _isNoteReadyForAutoSave = MutableStateFlow(true)
    val isNoteReady: StateFlow<Boolean> = _isNoteReadyForAutoSave.asStateFlow()

    fun resetAiSavedNewNoteStatus() {
        _aiSavedNewNoteId.value = null
    }

    fun cancelAiAnalysis() {
        // Keep SharkAI Not Asistanı running in background until completion
        aiAnalysisJob = null
        _isAiLoading.value = false
    }

    private val persistenceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        appContext?.let { UserAiPreferences.initialize(it) }
        observeAutoSaveDebounce()
    }

    private fun observeAutoSaveDebounce() {
        viewModelScope.launch(Dispatchers.IO) {
            combine(_noteTitle, _noteContent, _colorHex, _isPinned) { title, content, color, pinned ->
                NoteEntity(
                    id = _noteId.value,
                    title = title,
                    content = content,
                    colorHex = color,
                    isPinned = pinned,
                    createdAt = _createdAt.value,
                    updatedAt = System.currentTimeMillis()
                )
            }
                .drop(1)
                .filter { _isNoteReadyForAutoSave.value }
                .debounce(600L)
                .distinctUntilChanged { old, new ->
                    old.id == new.id &&
                        old.title == new.title &&
                        old.content == new.content &&
                        old.colorHex == new.colorHex &&
                        old.isPinned == new.isPinned
                }
                .collectLatest { note ->
                    if (_isNoteReadyForAutoSave.value &&
                        note.id == _noteId.value &&
                        (note.title.isNotBlank() || note.content.isNotBlank())
                    ) {
                        withContext(Dispatchers.IO) {
                            noteRepository.insertOrUpdateNote(note)
                        }
                    }
                }
        }
    }

    fun loadNoteById(id: String) {
        val targetGen = ++loadGeneration
        loadJob?.cancel()
        _isNoteReadyForAutoSave.value = false
        _noteId.value = id
        _noteTitle.value = ""
        _noteContent.value = ""
        _colorHex.value = getPreferredDefaultColorHex()
        _isPinned.value = false
        _aiResult.value = null
        _aiError.value = null
        loadJob = viewModelScope.launch(Dispatchers.IO) {
            val note = noteRepository.getNoteById(id)
            withContext(Dispatchers.Main) {
                if (targetGen != loadGeneration) return@withContext
                if (note != null) {
                    _noteTitle.value = note.title
                    _noteContent.value = note.content
                    _colorHex.value = note.colorHex.ifBlank { getPreferredDefaultColorHex() }
                    _isPinned.value = note.isPinned
                    _createdAt.value = note.createdAt
                }
                _isNoteReadyForAutoSave.value = true
            }
        }
    }

    fun initializeEmptyNote() {
        loadGeneration++
        loadJob?.cancel()
        _isNoteReadyForAutoSave.value = false
        _noteId.value = UUID.randomUUID().toString()
        _noteTitle.value = ""
        _noteContent.value = ""
        _colorHex.value = getPreferredDefaultColorHex()
        _isPinned.value = false
        _createdAt.value = System.currentTimeMillis()
        _aiResult.value = null
        _aiError.value = null
        _isNoteReadyForAutoSave.value = true
    }

    fun initializeNote(
        initialNote: NoteEntity?,
        fallbackTitle: String = "",
        fallbackContent: String = ""
    ) {
        loadGeneration++
        loadJob?.cancel()
        _isNoteReadyForAutoSave.value = false
        _noteId.value = initialNote?.id ?: UUID.randomUUID().toString()
        _noteTitle.value = initialNote?.title ?: fallbackTitle
        _noteContent.value = initialNote?.content ?: fallbackContent
        _colorHex.value = initialNote?.colorHex?.takeIf { it.isNotBlank() } ?: getPreferredDefaultColorHex()
        _isPinned.value = initialNote?.isPinned ?: false
        _createdAt.value = initialNote?.createdAt ?: System.currentTimeMillis()
        _aiResult.value = null
        _aiError.value = null
        _isNoteReadyForAutoSave.value = true
    }

    fun onTitleChanged(newTitle: String) {
        _noteTitle.value = newTitle
    }

    fun onContentChanged(newContent: String) {
        _noteContent.value = newContent
    }

    fun onColorHexChanged(newColorHex: String) {
        _colorHex.value = newColorHex
    }

    fun onPinnedChanged(pinned: Boolean) {
        _isPinned.value = pinned
        saveImmediately()
    }

    fun setPaperPattern(pattern: PaperPattern) {
        val currentTint = NoteStyleParser.getTintHex(_colorHex.value)
        _colorHex.value = NoteStyleParser.encode(currentTint, pattern)
        runCatching {
            appContext?.getSharedPreferences(NOTE_LAYOUT_PREFS, Context.MODE_PRIVATE)
                ?.edit()
                ?.putString(KEY_DEFAULT_PAPER_PATTERN, pattern.name)
                ?.apply()
        }
    }

    fun setPaperTint(tintHex: String) {
        val currentPattern = NoteStyleParser.getPattern(_colorHex.value)
        _colorHex.value = NoteStyleParser.encode(tintHex, currentPattern)
        runCatching {
            appContext?.getSharedPreferences(NOTE_LAYOUT_PREFS, Context.MODE_PRIVATE)
                ?.edit()
                ?.putString(KEY_DEFAULT_PAPER_TINT, tintHex)
                ?.apply()
        }
    }

    fun duplicateCurrentNote(onComplete: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val originalTitle = _noteTitle.value.trim().ifBlank { "Not" }
            val copyTitle = "$originalTitle (Kopya)"
            val newId = UUID.randomUUID().toString()
            val copyNote = NoteEntity(
                id = newId,
                title = copyTitle,
                content = _noteContent.value,
                colorHex = _colorHex.value,
                isPinned = false,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            noteRepository.saveNote(copyNote)
            withContext(Dispatchers.Main) {
                onComplete(newId)
            }
        }
    }

    fun deleteCurrentNote(onComplete: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            noteRepository.deleteNote(_noteId.value)
            withContext(Dispatchers.Main) {
                onComplete()
            }
        }
    }

    fun saveAiSummaryAsNewNote(summaryText: String, onComplete: ((String) -> Unit)? = null) {
        if (summaryText.isBlank()) return
        val currentTitle = _noteTitle.value.trim().ifBlank { "Ders Notu" }
        val newNoteTitle = "[Özet] $currentTitle"
        val newNoteId = UUID.randomUUID().toString()
        val newNote = NoteEntity(
            id = newNoteId,
            title = newNoteTitle,
            content = summaryText.trim(),
            colorHex = NoteStyleParser.encode("#FDF6E2", PaperPattern.RULED),
            isPinned = false,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        viewModelScope.launch(Dispatchers.IO) {
            noteRepository.saveNote(newNote)
            _aiSavedNewNoteId.value = newNoteId
            if (onComplete != null) {
                withContext(Dispatchers.Main) {
                    onComplete(newNoteId)
                }
            }
        }
    }

    fun saveImmediately(
        overrideTitle: String? = null,
        overrideContent: String? = null
    ) {
        if (overrideTitle != null) {
            _noteTitle.value = overrideTitle
        }
        if (overrideContent != null) {
            _noteContent.value = overrideContent
        }

        val currentId = _noteId.value
        val currentTitle = overrideTitle ?: _noteTitle.value
        val currentContent = overrideContent ?: _noteContent.value
        val currentColorHex = _colorHex.value
        val currentPinned = _isPinned.value
        val currentCreatedAt = _createdAt.value

        if (currentTitle.isNotBlank() || currentContent.isNotBlank() || currentPinned) {
            persistenceScope.launch {
                runCatching {
                    val entity = NoteEntity(
                        id = currentId,
                        title = currentTitle,
                        content = currentContent,
                        colorHex = currentColorHex,
                        isPinned = currentPinned,
                        createdAt = currentCreatedAt,
                        updatedAt = System.currentTimeMillis()
                    )
                    noteRepository.insertOrUpdateNote(entity)
                }.onFailure { e ->
                    Log.e("NoteDetailVM", "saveImmediately failed for $currentId", e)
                }
            }
        }
    }

    fun saveNoteDrawingImmediately(
        context: Context,
        targetNoteId: String,
        strokes: List<DrawingStroke>
    ) {
        saveNotePageDrawingImmediately(context, targetNoteId, 0, strokes)
    }

    fun saveNotePageDrawingImmediately(
        context: Context,
        targetNoteId: String,
        pageIndex: Int,
        strokes: List<DrawingStroke>
    ) {
        if (targetNoteId.isBlank()) return
        val appCtx = context.applicationContext
        val snapshot = strokes.toList()
        persistenceScope.launch {
            runCatching {
                DrawingSerializer.saveNotePageDrawing(appCtx, targetNoteId, pageIndex, snapshot)
            }.onFailure { e ->
                Log.e("NoteDetailVM", "saveNotePageDrawingImmediately failed for $targetNoteId p$pageIndex", e)
            }
        }
    }

    fun saveNotePageTextBoxesImmediately(
        context: Context,
        targetNoteId: String,
        pageIndex: Int,
        textBoxes: List<DrawingTextBox>
    ) {
        if (targetNoteId.isBlank()) return
        val appCtx = context.applicationContext
        val snapshot = textBoxes.toList()
        persistenceScope.launch {
            runCatching {
                DrawingSerializer.saveNotePageTextBoxes(appCtx, targetNoteId, pageIndex, snapshot)
            }.onFailure { e ->
                Log.e("NoteDetailVM", "saveNotePageTextBoxesImmediately failed for $targetNoteId p$pageIndex", e)
            }
        }
    }

    fun saveNotePageAnnotationsImmediately(
        context: Context,
        targetNoteId: String,
        pageIndex: Int,
        strokes: List<DrawingStroke>,
        textBoxes: List<DrawingTextBox>
    ) {
        if (targetNoteId.isBlank()) return
        val appCtx = context.applicationContext
        val strokeSnapshot = strokes.toList()
        val boxSnapshot = textBoxes.toList()
        persistenceScope.launch {
            runCatching {
                DrawingSerializer.saveNotePageAnnotations(
                    appCtx,
                    targetNoteId,
                    pageIndex,
                    strokeSnapshot,
                    boxSnapshot
                )
            }.onFailure { e ->
                Log.e("NoteDetailVM", "saveNotePageAnnotationsImmediately failed for $targetNoteId p$pageIndex", e)
            }
        }
    }

    fun analyzeNoteWithAi(
        query: String,
        scope: DocumentAiScope = DocumentAiScope.CURRENT_PAGE,
        activePdfPageIndex: Int = 0,
        activeNotePageIndex: Int = 0
    ) {
        val rawContent = _noteContent.value
        val currentTitle = _noteTitle.value.trim()
        val cleanNoteText = sanitizeNoteTextForAi(rawContent)
        val pdfPagePaths = extractPdfPagePaths(rawContent)

        aiAnalysisJob?.cancel()
        _isAiLoading.value = true
        _aiError.value = null
        _aiScopeNotice.value = null
        _aiSavedNewNoteId.value = null
        _aiResult.value = ""

        aiAnalysisJob = SharkStudioBackgroundHelper.backgroundScope.launch {
            try {
                val selectedProvider = UserAiPreferences.getOrNull(appContext)?.getSelectedProvider()
                    ?: AiProviderChoice.AUTO

                val textWithoutPdfHeader = cleanNoteText
                    .replace(PDF_HEADER_REGEX, "")
                    .replace("Tüm PDF sayfaları yüklendi. Notlarınızı aşağıya ekleyebilirsiniz:", "")
                    .replace("Tüm PDF sayfaları yüklendi.", "")
                    .trim()

                var lastStreamUiEmitMs = 0L
                fun emitThrottledStream(partial: String) {
                    val now = System.currentTimeMillis()
                    if (lastStreamUiEmitMs == 0L || now - lastStreamUiEmitMs >= 50L) {
                        lastStreamUiEmitMs = now
                        _aiResult.value = partial
                        _isAiLoading.value = false
                    }
                }

                val hasSubstantialText = textWithoutPdfHeader.length >= 120
                val usePdfVisionPath = pdfPagePaths.isNotEmpty() &&
                    (scope != DocumentAiScope.WHOLE_DOC_TEXT || !hasSubstantialText)

                if (usePdfVisionPath) {
                    val effectiveVisionScope = if (scope == DocumentAiScope.WHOLE_DOC_TEXT) {
                        _aiScopeNotice.value =
                            "Bu PDF notunda seçilebilir metin yok; aktif sayfa (${activePdfPageIndex.coerceIn(0, pdfPagePaths.lastIndex) + 1}) görsel olarak inceleniyor."
                        DocumentAiScope.CURRENT_PAGE
                    } else {
                        scope
                    }

                    val targetIndices = NoteDocumentAiHelper.selectPageIndicesForScope(
                        currentPage = activePdfPageIndex,
                        totalPages = pdfPagePaths.size,
                        scope = effectiveVisionScope
                    )
                    val targetMaxDim = if (targetIndices.size <= 1) 1024 else 800
                    val pageBitmaps = targetIndices.mapNotNull { idx ->
                        val path = pdfPagePaths.getOrNull(idx) ?: return@mapNotNull null
                        NoteDocumentAiHelper.decodeDownsampledBitmap(path, maxDimension = targetMaxDim)
                    }

                    var visionErrorMessage: String? = null
                    if (pageBitmaps.isNotEmpty()) {
                        try {
                            val pageLabel = if (targetIndices.size == 1) {
                                "Sayfa ${targetIndices.first() + 1} / ${pdfPagePaths.size}"
                            } else {
                                "Sayfalar ${targetIndices.first() + 1}-${targetIndices.last() + 1} / ${pdfPagePaths.size}"
                            }
                            val visionSystemInstruction = """
                                Sen öğrencinin açtığı ders belgesi sayfalarını inceleyen uzman bir çalışma asistanısın. YALNIZCA verilen sayfa görsellerindeki ve not metnindeki bilgileri baz al. Asla belgede olmayan uydurma bilgi ekleme. Eğer sorulan bilgi sayfalarda yoksa açıkça 'Bu bilgi seçilen sayfalarda bulunamadı.' yaz.
                                KESİN DAVRANIŞ KURALLARI:
                                1. DOĞRUDAN BAŞLANGIÇ: Yapay selamlama, gereksiz giriş veya emoji kullanma. Doğrudan analize gir.
                                2. ODAKLI VE ANLAŞILIR ANALİZ: Sorulan konuyu veya sayfadaki ana başlıkları, kavramları ve çözüm adımlarını net, anlaşılır ve düzenli şekilde açıkla.
                                3. DÜZENLİ BİÇİMLENDİRME: Başlıklar, maddeler ve vurgulu terimler kullan.
                            """.trimIndent()

                            val userRequest = query.trim().ifBlank {
                                if (targetIndices.size == 1) {
                                    "Bu sayfadaki ana konuları, önemli kavramları ve varsa soruları özetle."
                                } else {
                                    "Seçilen sayfalardaki ana konuları ve önemli çalışma noktalarını özetle."
                                }
                            }

                            val visionPrompt = buildString {
                                append("[NOT BAŞLIĞI]: ${currentTitle.ifBlank { "PDF Notu" }}\n")
                                append("[ODAK]: $pageLabel\n")
                                if (textWithoutPdfHeader.length >= 30) {
                                    append("[KULLANICI NOTU]:\n")
                                    append(textWithoutPdfHeader.take(2_500))
                                    append("\n\n")
                                }
                                append("[KULLANICI İSTEĞİ]:\n$userRequest")
                            }

                            val visionResult = GeminiAiFactory.streamContentWithFailover(
                                context = appContext,
                                systemInstruction = visionSystemInstruction,
                                userPrompt = visionPrompt,
                                bitmaps = pageBitmaps,
                                responseJson = false,
                                maxOutputTokens = 6144,
                                timeoutMs = 55_000L,
                                onPartialText = { partial ->
                                    emitThrottledStream(partial)
                                }
                            )

                            if (visionResult.isSuccess) {
                                val text = visionResult.getOrThrow().text.trim()
                                if (text.isNotBlank()) {
                                    _aiResult.value = text
                                    _aiError.value = null
                                    _isAiLoading.value = false
                                    return@launch
                                }
                            } else {
                                val err = visionResult.exceptionOrNull()
                                if (err is CancellationException) throw err
                                visionErrorMessage = err?.localizedMessage
                                Log.w("NoteDetailVM", "PDF vision failed: $visionErrorMessage")
                            }
                        } finally {
                            pageBitmaps.forEach { runCatching { if (!it.isRecycled) it.recycle() } }
                        }
                    }

                    if (!hasSubstantialText) {
                        _aiError.value = visionErrorMessage
                            ?: "Seçilen sayfa görseli çözümlenemedi. İnternet bağlantınızı ve API ayarlarınızı kontrol edin."
                        _aiResult.value = null
                        _isAiLoading.value = false
                        return@launch
                    }
                }

                // Scoped text extraction for multi-page notes
                val rawNotePages = splitContentIntoPages(rawContent)
                val scopedNoteText: String
                val scopeLabel: String
                if (rawNotePages.size > 1 && scope != DocumentAiScope.WHOLE_DOC_TEXT) {
                    val pageIndices = NoteDocumentAiHelper.selectPageIndicesForScope(
                        currentPage = activeNotePageIndex,
                        totalPages = rawNotePages.size,
                        scope = scope
                    )
                    val joinedPages = pageIndices.mapNotNull { idx ->
                        val pageClean = sanitizeNoteTextForAi(rawNotePages.getOrNull(idx).orEmpty())
                        if (pageClean.isBlank()) null else "[Sayfa ${idx + 1}]\n$pageClean"
                    }.joinToString("\n\n").take(10_000)

                    if (joinedPages.isNotBlank()) {
                        scopedNoteText = joinedPages
                        scopeLabel = if (pageIndices.size == 1) {
                            "Sayfa ${pageIndices.first() + 1} / ${rawNotePages.size}"
                        } else {
                            "Sayfalar ${pageIndices.first() + 1}-${pageIndices.last() + 1} / ${rawNotePages.size}"
                        }
                    } else {
                        scopedNoteText = cleanNoteText
                        scopeLabel = "Tüm Not"
                    }
                } else {
                    scopedNoteText = cleanNoteText
                    scopeLabel = if (rawNotePages.size > 1) "Tüm Not (${rawNotePages.size} sayfa)" else "Bu Sayfa"
                }

                if (scopedNoteText.isBlank() && currentTitle.isBlank()) {
                    _aiResult.value = "Bu bilgi not içinde bulunamadı."
                    _isAiLoading.value = false
                    return@launch
                }

                val effectiveQuery = query.trim().ifBlank {
                    "Bu notun içeriğini özetle ve yalnızca notta geçen temel noktaları listele."
                }

                val contextualPrompt = """
                    [NOT BAŞLIĞI]: ${currentTitle.ifBlank { "Başlıksız Not" }}
                    [KAPSAM]: $scopeLabel
                    [NOT METNİ]:
                    $scopedNoteText

                    [KULLANICI SORUSU / İSTEĞİ]:
                    $effectiveQuery
                """.trimIndent()

                val prefs = UserAiPreferences.getOrNull(appContext)
                val targetExternalProvider: AiProviderChoice? = when (selectedProvider) {
                    AiProviderChoice.GEMINI -> null
                    AiProviderChoice.CLAUDE ->
                        AiProviderChoice.CLAUDE.takeIf { MultiProviderAiClient.getClaudeApiKey(appContext).isNotBlank() }
                    AiProviderChoice.OPENAI ->
                        AiProviderChoice.OPENAI.takeIf { MultiProviderAiClient.getOpenAiApiKey(appContext).isNotBlank() }
                    AiProviderChoice.GROK ->
                        AiProviderChoice.GROK.takeIf { MultiProviderAiClient.getGrokApiKey(appContext).isNotBlank() }
                    AiProviderChoice.DEEPSEEK ->
                        AiProviderChoice.DEEPSEEK.takeIf { DeepSeekClient.getDeepSeekApiKey(appContext).isNotBlank() }
                    AiProviderChoice.KIMI ->
                        AiProviderChoice.KIMI.takeIf { DeepSeekClient.getKimiApiKey(appContext).isNotBlank() }
                    AiProviderChoice.AUTO -> {
                        val activeAuto = prefs?.getActiveProviderAndModel()?.first ?: AiProviderChoice.GEMINI
                        if (activeAuto != AiProviderChoice.GEMINI && MultiProviderAiClient.hasAnyExternalProviderKey(appContext)) {
                            activeAuto
                        } else {
                            null
                        }
                    }
                }

                if (targetExternalProvider != null) {
                    lastStreamUiEmitMs = 0L
                    try {
                        val streamedResult = MultiProviderAiClient.streamChatCompletion(
                            provider = targetExternalProvider,
                            systemPrompt = ISOLATED_NOTE_SYSTEM_INSTRUCTION.trim(),
                            userPrompt = contextualPrompt,
                            temperature = 0.2f,
                            maxTokens = 4096,
                            context = appContext,
                            onAccumulatedChunk = { partial ->
                                emitThrottledStream(partial)
                            }
                        )
                        if (streamedResult.isNotBlank()) {
                            _aiResult.value = streamedResult.trim()
                            _aiError.value = null
                            return@launch
                        }
                    } catch (ce: CancellationException) {
                        _isAiLoading.value = false
                        return@launch
                    } catch (e: Exception) {
                        Log.w("NoteDetailVM", "${targetExternalProvider.shortLabel} note analysis failed (${e.message}), trying Gemini...", e)
                    }
                }

                lastStreamUiEmitMs = 0L
                val geminiResult = GeminiAiFactory.streamContentWithFailover(
                    context = appContext,
                    systemInstruction = ISOLATED_NOTE_SYSTEM_INSTRUCTION.trim(),
                    userPrompt = contextualPrompt,
                    responseJson = false,
                    maxOutputTokens = 6144,
                    timeoutMs = 55_000L,
                    onPartialText = { partial ->
                        emitThrottledStream(partial.trimStart())
                    }
                )

                if (geminiResult.isSuccess) {
                    val responseText = geminiResult.getOrThrow().text.trim()
                    if (responseText.isNotBlank()) {
                        _aiResult.value = responseText
                        _aiError.value = null
                    } else {
                        _aiResult.value = null
                        _aiError.value = "Not analiz edilemedi."
                    }
                } else {
                    val err = geminiResult.exceptionOrNull()
                    if (err is CancellationException) throw err
                    _aiResult.value = null
                    _aiError.value = err?.localizedMessage ?: "Not analiz edilemedi."
                }
            } catch (e: Throwable) {
                if (e !is CancellationException) {
                    Log.e("NoteDetailVM", "Unhandled Throwable in analyzeNoteWithAi", e)
                    _aiError.value = e.localizedMessage ?: "Not AI sırasında beklenmeyen bir hata oluştu."
                }
            } finally {
                _isAiLoading.value = false
                if (!_aiResult.value.isNullOrBlank()) {
                    appContext?.let { ctx ->
                        SharkStudioBackgroundHelper.notifyTaskCompleted(
                            context = ctx,
                            notificationId = 3013,
                            title = "SharkAI Not Asistanı Hazır",
                            body = "Not analizi ve yanıtı tamamlandı."
                        )
                    }
                }
            }
        }
    }

    fun appendAiResultToNote(customResult: String? = null): String {
        val resultToAppend = (customResult ?: _aiResult.value)?.trim()
        if (resultToAppend.isNullOrBlank()) return _noteContent.value

        val existing = _noteContent.value
        val updatedContent = if (existing.isBlank()) {
            resultToAppend
        } else {
            "$existing\n\n--- Not AI Analizi ---\n$resultToAppend"
        }

        _noteContent.value = updatedContent
        saveImmediately()
        return updatedContent
    }

    fun clearAiResult() {
        cancelAiAnalysis()
        _aiResult.value = null
        _aiError.value = null
        _aiScopeNotice.value = null
        _aiSavedNewNoteId.value = null
    }

    override fun onCleared() {
        aiAnalysisJob?.cancel()
        appContext?.let { NoteRepositoryImpl.flushPendingCloudWrites(it) }
        super.onCleared()
    }

    fun deleteNote(onDeleted: () -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            noteRepository.deleteNote(_noteId.value)
            withContext(Dispatchers.Main) {
                onDeleted()
            }
        }
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val appCtx = context.applicationContext
            val db = SharkMentorDatabase.getDatabase(appCtx)
            val repo = NoteRepositoryImpl(db.noteDao(), appCtx)
            return NoteDetailViewModel(repo, appCtx) as T
        }
    }
}
