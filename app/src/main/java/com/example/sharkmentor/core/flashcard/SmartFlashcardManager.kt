package com.example.sharkmentor.core.flashcard

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Immutable
import com.example.sharkmentor.core.ai.MultiProviderAiClient
import com.example.sharkmentor.core.coaching.CoachingEngine
import com.example.sharkmentor.core.coaching.StudentDossierManager
import com.example.sharkmentor.core.fsrs.FsrsEngine
import com.example.sharkmentor.core.fsrs.FsrsRating
import com.example.sharkmentor.core.util.ImportedPdfDocument
import com.example.sharkmentor.core.util.PdfBitmapConverter
import com.example.sharkmentor.data.local.SharkMentorDatabase
import com.example.sharkmentor.data.local.entity.ErrorQuestionEntity
import com.example.sharkmentor.data.local.entity.NoteEntity
import com.example.sharkmentor.ui.screens.notes.NoteDetailViewModel
import com.example.sharkmentor.ui.screens.notes.NoteRichTextHelper
import com.example.sharkmentor.ui.screens.notes.drawing.DrawingSerializer
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.Locale

enum class FlashcardSourceType {
    NOTE,
    PDF,
    ERROR_BANK
}

enum class FlashcardDensity(
    val label: String,
    val promptGuidance: String,
    val maxTokens: Int
) {
    LOW(
        label = "Az",
        promptGuidance = "Yalnızca en kritik temel kavramlardan 6 ila 10 adet (kaynak çok kısaysa daha az; asla zorlama soru ekleme) yüksek kaliteli soru-cevap kartı üret.",
        maxTokens = 2600
    ),
    STANDARD(
        label = "Standart",
        promptGuidance = "Kaynağın bilgi yoğunluğuna göre dengeli biçimde yaklaşık 15 ila 22 adet (kaynak kısaysa mantıklı olan kadar; asla tekrar veya zorlama soru ekleme) yüksek kaliteli soru-cevap kartı üret.",
        maxTokens = 4800
    ),
    HIGH(
        label = "Çok",
        promptGuidance = "Kaynaktaki tüm önemli kavram, formül, kural, karşılaştırma ve istisnaları kapsayacak şekilde mantıklı olabildiğince çok (zengin içeriklerde 30 ila 50+ adet, ancak kısa kaynaklarda asla zorlama veya tekrar soru üretmeden) yüksek kaliteli soru-cevap kartı üret.",
        maxTokens = 8192
    )
}

@Immutable
data class SmartFlashcard(
    val id: String,
    val sourceType: FlashcardSourceType,
    val sourceId: String,
    val sourceTitle: String,
    val subject: String = "",
    val topic: String = "",
    val front: String,
    val back: String,
    val questionImageUri: String? = null,
    val solutionImageUri: String? = null,
    val errorQuestionId: Long? = null,
    val knownCount: Int = 0,
    val missedCount: Int = 0,
    val lastOutcomeKnown: Boolean? = null,
    val lastReviewedAtMillis: Long = 0L
)

@Immutable
data class SmartFlashcardDeck(
    val id: String,
    val name: String,
    val selection: SelectedFlashcardSources = SelectedFlashcardSources(),
    val cards: List<SmartFlashcard> = emptyList(),
    val createdAtMillis: Long = System.currentTimeMillis()
)

@Immutable
data class SelectedFlashcardSources(
    val noteIds: Set<String> = emptySet(),
    val pdfIds: Set<String> = emptySet(),
    val errorSubjects: Set<String> = emptySet()
) {
    val totalSelectedCount: Int
        get() = noteIds.size + pdfIds.size + errorSubjects.size

    val isEmpty: Boolean
        get() = noteIds.isEmpty() && pdfIds.isEmpty() && errorSubjects.isEmpty()
}

@Immutable
data class TopicPerformanceRecord(
    val topicKey: String,
    val knownTotal: Int = 0,
    val missedTotal: Int = 0,
    val lastKnown: Boolean = true,
    val updatedAtMillis: Long = 0L
)

/**
 * Pure JVM-testable priority scorer for NotebookLM/Anki-style adaptive study ordering.
 *
 * Rules:
 * 1. Cards whose last outcome was Missed (`lastOutcomeKnown == false`) get top priority (`120+`).
 * 2. Unseen cards (`lastOutcomeKnown == null`) come next (`70+`).
 * 3. Mastered cards (`lastOutcomeKnown == true`) drop lower (`20+`) and slowly rise as hours pass.
 * 4. Weak topics (`topicStats` with net misses or recent failure) receive an automatic topic boost.
 */
fun computeFlashcardPriorityScore(
    card: SmartFlashcard,
    topicStats: Map<String, TopicPerformanceRecord> = emptyMap(),
    nowMillis: Long = System.currentTimeMillis()
): Double {
    val baseScore = when (card.lastOutcomeKnown) {
        false -> 125.0 + (card.missedCount * 16.0) - (card.knownCount * 2.5)
        null -> 72.0 + (card.missedCount * 8.0)
        true -> {
            val elapsedHours = if (card.lastReviewedAtMillis > 0L) {
                ((nowMillis - card.lastReviewedAtMillis).coerceAtLeast(0L) / 3_600_000.0)
            } else {
                12.0
            }
            (18.0 + minOf(elapsedHours * 0.75, 42.0) - (card.knownCount * 4.5)).coerceAtLeast(2.0)
        }
    }
    val normTopic = normalizeTopicKey(card.subject, card.topic)
    val topicRec = topicStats[normTopic]
    val topicBoost = if (topicRec != null) {
        val missDelta = (topicRec.missedTotal - topicRec.knownTotal).coerceAtLeast(0)
        (missDelta * 9.0) + (if (!topicRec.lastKnown) 18.0 else 0.0)
    } else {
        0.0
    }
    return baseScore + topicBoost
}

fun orderDeckByPriority(
    cards: List<SmartFlashcard>,
    topicStats: Map<String, TopicPerformanceRecord> = emptyMap(),
    nowMillis: Long = System.currentTimeMillis()
): List<SmartFlashcard> {
    return cards.sortedWith(
        compareByDescending<SmartFlashcard> { computeFlashcardPriorityScore(it, topicStats, nowMillis) }
            .thenBy { it.lastReviewedAtMillis }
            .thenBy { it.id }
    )
}

fun normalizeTopicKey(subject: String, topic: String): String {
    val s = subject.trim().lowercase(Locale("tr", "TR"))
    val t = topic.trim().lowercase(Locale("tr", "TR"))
    return when {
        s.isNotBlank() && t.isNotBlank() && s != t -> "$s :: $t"
        t.isNotBlank() -> t
        s.isNotBlank() -> s
        else -> "genel"
    }
}

/**
 * Parses AI JSON output into clean front/back flashcards using Gson (100% pure JVM-testable).
 */
fun parseAiFlashcardsJson(
    rawJson: String,
    sourceType: FlashcardSourceType,
    sourceId: String,
    sourceTitle: String,
    defaultSubject: String = ""
): List<SmartFlashcard> {
    val cleaned = rawJson
        .trim()
        .removePrefix("```json")
        .removePrefix("```JSON")
        .removePrefix("```")
        .removeSuffix("```")
        .trim()
    val startIdx = cleaned.indexOf('[')
    val endIdx = cleaned.lastIndexOf(']')
    val arraySlice = if (startIdx >= 0 && endIdx > startIdx) {
        cleaned.substring(startIdx, endIdx + 1)
    } else {
        cleaned
    }

    return runCatching {
        val root = JsonParser.parseString(arraySlice)
        val arr = when {
            root.isJsonArray -> root.asJsonArray
            root.isJsonObject -> {
                val obj = root.asJsonObject
                listOf("cards", "flashcards", "questions", "items", "deck", "data")
                    .firstOrNull { key -> obj.has(key) && obj.get(key).isJsonArray }
                    ?.let { obj.getAsJsonArray(it) }
                    ?: return emptyList()
            }
            else -> return emptyList()
        }
        val out = mutableListOf<SmartFlashcard>()
        for (el in arr) {
            if (!el.isJsonObject) continue
            val obj = el.asJsonObject
            val front = (
                obj.get("front")?.takeIf { !it.isJsonNull }?.asString
                    ?: obj.get("question")?.takeIf { !it.isJsonNull }?.asString
                    ?: obj.get("soru")?.takeIf { !it.isJsonNull }?.asString
                    ?: obj.get("q")?.takeIf { !it.isJsonNull }?.asString
                    ?: ""
                ).trim()
            val back = (
                obj.get("back")?.takeIf { !it.isJsonNull }?.asString
                    ?: obj.get("answer")?.takeIf { !it.isJsonNull }?.asString
                    ?: obj.get("cevap")?.takeIf { !it.isJsonNull }?.asString
                    ?: obj.get("a")?.takeIf { !it.isJsonNull }?.asString
                    ?: ""
                ).trim()
            if (!isQualityFlashcard(front, back)) continue
            val topic = (
                obj.get("topic")?.takeIf { !it.isJsonNull }?.asString
                    ?: obj.get("konu")?.takeIf { !it.isJsonNull }?.asString
                    ?: sourceTitle
                ).trim().ifBlank { sourceTitle }
            val subject = (
                obj.get("subject")?.takeIf { !it.isJsonNull }?.asString
                    ?: obj.get("ders")?.takeIf { !it.isJsonNull }?.asString
                    ?: defaultSubject
                ).trim()
            val stableId = stableCardHash("${sourceType.name}|$sourceId|${front.lowercase(Locale.ROOT)}")
            out.add(
                SmartFlashcard(
                    id = stableId,
                    sourceType = sourceType,
                    sourceId = sourceId,
                    sourceTitle = sourceTitle,
                    subject = subject,
                    topic = topic,
                    front = front.take(360),
                    back = back.take(640)
                )
            )
        }
        out.distinctBy { it.id }
    }.getOrDefault(emptyList())
}

internal fun isQualityFlashcard(front: String, back: String): Boolean {
    val f = front.trim()
    val b = back.trim()
    if (f.length < 8 || b.length < 3) return false
    if (f.equals(b, ignoreCase = true)) return false
    val lowerF = f.lowercase(Locale("tr", "TR"))
    if (lowerF.contains("önemli nokta ") || lowerF.endsWith("önemli nokta")) return false
    if (lowerF.matches(Regex("""^(madde|not|bölüm|kısım|başlık)\s*\d+[:.\-]?$"""))) return false
    return true
}

private fun stableCardHash(seed: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(seed.toByteArray(Charsets.UTF_8))
    return digest.take(8).joinToString("") { "%02x".format(it) }
}

/**
 * Local-first Smart Flashcard & Audio Deck Manager.
 * Zero background Firestore reads/writes — stores deck & progress locally in `files/smart_flashcards_v1.json`
 * and pushes concise mastered/missed topic insights into the encrypted `StudentDossierManager` when AI Coaching is active.
 */
object SmartFlashcardManager {

    private const val TAG = "SmartFlashcardManager"
    private const val PREFS_NAME = "shark_smart_flashcards_prefs"
    private const val KEY_SELECTED_SOURCES = "selected_sources_json"
    private const val STORAGE_FILE_NAME = "smart_flashcards_v1.json"

    private val gson = Gson()
    private val fileLock = Any()

    @Volatile
    private var initialized = false

    const val MAX_SAVED_DECKS = 5

    private data class PersistedFlashcardStore(
        val cards: List<SmartFlashcard> = emptyList(),
        val topicStats: Map<String, TopicPerformanceRecord> = emptyMap(),
        val decks: List<SmartFlashcardDeck>? = null
    )

    private val _selectedSources = MutableStateFlow(SelectedFlashcardSources())
    val selectedSources: StateFlow<SelectedFlashcardSources> = _selectedSources.asStateFlow()

    private val _allStoredCards = MutableStateFlow<List<SmartFlashcard>>(emptyList())
    val allStoredCards: StateFlow<List<SmartFlashcard>> = _allStoredCards.asStateFlow()

    private val _topicStats = MutableStateFlow<Map<String, TopicPerformanceRecord>>(emptyMap())
    val topicStats: StateFlow<Map<String, TopicPerformanceRecord>> = _topicStats.asStateFlow()

    private val _savedDecks = MutableStateFlow<List<SmartFlashcardDeck>>(emptyList())
    val savedDecks: StateFlow<List<SmartFlashcardDeck>> = _savedDecks.asStateFlow()

    private val _activeDeckId = MutableStateFlow<String?>(null)
    val activeDeckId: StateFlow<String?> = _activeDeckId.asStateFlow()

    fun ensureLoaded(context: Context) {
        if (initialized) return
        synchronized(fileLock) {
            if (initialized) return
            val appCtx = context.applicationContext
            val prefs = appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val rawSel = prefs.getString(KEY_SELECTED_SOURCES, null)
            if (!rawSel.isNullOrBlank()) {
                runCatching {
                    gson.fromJson(rawSel, SelectedFlashcardSources::class.java)
                }.getOrNull()?.let { loaded ->
                    _selectedSources.value = SelectedFlashcardSources(
                        noteIds = loaded.noteIds ?: emptySet(),
                        pdfIds = loaded.pdfIds ?: emptySet(),
                        errorSubjects = loaded.errorSubjects ?: emptySet()
                    )
                }
            }
            val file = File(appCtx.filesDir, STORAGE_FILE_NAME)
            if (file.exists()) {
                runCatching {
                    val text = file.readText(Charsets.UTF_8)
                    val type = object : TypeToken<PersistedFlashcardStore>() {}.type
                    gson.fromJson<PersistedFlashcardStore>(text, type)
                }.getOrNull()?.let { store ->
                    _allStoredCards.value = store.cards ?: emptyList()
                    _topicStats.value = store.topicStats ?: emptyMap()
                    val loadedDecks = (store.decks ?: emptyList()).filter { it.cards.isNotEmpty() }.take(MAX_SAVED_DECKS)
                    _savedDecks.value = loadedDecks
                    if (_activeDeckId.value == null && loadedDecks.isNotEmpty()) {
                        _activeDeckId.value = loadedDecks.first().id
                    }
                }
            }
            initialized = true
        }
    }

    fun updateSelectedSources(context: Context, updated: SelectedFlashcardSources) {
        ensureLoaded(context)
        _selectedSources.value = updated
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SELECTED_SOURCES, gson.toJson(updated))
            .apply()
    }

    private fun persistStore(context: Context) {
        synchronized(fileLock) {
            runCatching {
                val file = File(context.applicationContext.filesDir, STORAGE_FILE_NAME)
                val tmp = File(context.applicationContext.filesDir, "$STORAGE_FILE_NAME.tmp")
                val payload = PersistedFlashcardStore(
                    cards = _allStoredCards.value,
                    topicStats = _topicStats.value,
                    decks = _savedDecks.value
                )
                tmp.writeText(gson.toJson(payload), Charsets.UTF_8)
                if (!tmp.renameTo(file)) {
                    file.writeText(gson.toJson(payload), Charsets.UTF_8)
                    tmp.delete()
                }
            }.onFailure { e ->
                Log.w(TAG, "persistStore failed: ${e.message}")
            }
        }
    }

    /**
     * Builds (or loads from local cache) the ordered flashcard deck for [selection].
     * Uses in-app AI (`MultiProviderAiClient`) to generate cards for newly selected Notes/PDFs
     * (or when [forceRegenerate] is true), preserving existing known/missed counts!
     */
    fun selectDeck(deckId: String): SmartFlashcardDeck? {
        val target = _savedDecks.value.firstOrNull { it.id == deckId } ?: return null
        _activeDeckId.value = target.id
        return target
    }

    fun renameDeck(context: Context, deckId: String, newName: String) {
        val clean = newName.trim().take(60)
        if (clean.isBlank()) return
        ensureLoaded(context)
        _savedDecks.value = _savedDecks.value.map {
            if (it.id == deckId) it.copy(name = clean) else it
        }
        persistStore(context)
    }

    fun deleteDeck(context: Context, deckId: String) {
        ensureLoaded(context)
        val remaining = _savedDecks.value.filterNot { it.id == deckId }
        _savedDecks.value = remaining
        if (_activeDeckId.value == deckId) {
            _activeDeckId.value = remaining.firstOrNull()?.id
        }
        persistStore(context)
    }

    suspend fun createNamedDeck(
        context: Context,
        customName: String,
        selection: SelectedFlashcardSources,
        availableNotes: List<NoteEntity>,
        availablePdfs: List<ImportedPdfDocument>,
        forceRegenerate: Boolean = false,
        density: FlashcardDensity = FlashcardDensity.STANDARD
    ): Result<SmartFlashcardDeck> = withContext(Dispatchers.IO) {
        ensureLoaded(context)
        if (_savedDecks.value.size >= MAX_SAVED_DECKS) {
            return@withContext Result.failure(
                IllegalStateException("En fazla $MAX_SAVED_DECKS flashcard serisi oluşturabilirsiniz. Yeni seri için mevcut bir seriyi silin.")
            )
        }
        val cards = buildOrRefreshDeck(
            context = context,
            selection = selection,
            availableNotes = availableNotes,
            availablePdfs = availablePdfs,
            forceRegenerate = forceRegenerate,
            density = density
        )
        if (cards.isEmpty()) {
            return@withContext Result.failure(
                IllegalStateException("Seçili kaynaklardan kart oluşturulamadı.")
            )
        }
        val autoName = customName.trim().take(60).ifBlank {
            val firstNote = availableNotes.firstOrNull { it.id in selection.noteIds }?.title?.takeIf { it.isNotBlank() }
            val firstPdf = availablePdfs.firstOrNull { it.id in selection.pdfIds }?.displayName?.takeIf { it.isNotBlank() }
            val firstErr = selection.errorSubjects.firstOrNull()
            (firstNote ?: firstPdf ?: firstErr ?: "Seri ${_savedDecks.value.size + 1}").take(42)
        }
        val deckId = "deck_${System.currentTimeMillis()}_${stableCardHash(autoName)}"
        val newDeck = SmartFlashcardDeck(
            id = deckId,
            name = autoName,
            selection = selection,
            cards = cards,
            createdAtMillis = System.currentTimeMillis()
        )
        _savedDecks.value = (listOf(newDeck) + _savedDecks.value).take(MAX_SAVED_DECKS)
        _activeDeckId.value = newDeck.id
        persistStore(context)
        Result.success(newDeck)
    }

    suspend fun buildOrRefreshDeck(
        context: Context,
        selection: SelectedFlashcardSources,
        availableNotes: List<NoteEntity>,
        availablePdfs: List<ImportedPdfDocument>,
        forceRegenerate: Boolean = false,
        density: FlashcardDensity = FlashcardDensity.STANDARD
    ): List<SmartFlashcard> = withContext(Dispatchers.IO) {
        ensureLoaded(context)
        updateSelectedSources(context, selection)
        if (selection.isEmpty) return@withContext emptyList()

        val appCtx = context.applicationContext
        val existingById = _allStoredCards.value.associateBy { it.id }.toMutableMap()
        val generatedDeck = mutableListOf<SmartFlashcard>()

        // 1. Selected Notes
        val chosenNotes = availableNotes.filter { it.id in selection.noteIds }
        for (note in chosenNotes) {
            val cachedForNote = _allStoredCards.value.filter {
                it.sourceType == FlashcardSourceType.NOTE &&
                    it.sourceId == note.id &&
                    isQualityFlashcard(it.front, it.back)
            }
            if (cachedForNote.isNotEmpty() && !forceRegenerate) {
                generatedDeck.addAll(cachedForNote)
                continue
            }
            val plainBody = NoteRichTextHelper.toPlainText(
                NoteDetailViewModel.sanitizeNoteTextForAi(note.content)
            ).trim()
            val textBoxesText = runCatching {
                DrawingSerializer.loadNotePageTextBoxes(appCtx, note.id, 0)
                    .joinToString("\n") { NoteRichTextHelper.toPlainText(it.taggedContent) }
            }.getOrDefault("").trim()
            val fullSourceText = listOf(note.title.trim(), plainBody, textBoxesText)
                .filter { it.isNotBlank() }
                .joinToString("\n\n")
                .take(8_500)

            val freshCards = generateCardsWithAiOrFallback(
                context = appCtx,
                sourceType = FlashcardSourceType.NOTE,
                sourceId = note.id,
                sourceTitle = note.title.ifBlank { "Not" },
                rawContent = fullSourceText,
                density = density
            ).map { fresh -> mergeWithPreviousStats(fresh, existingById[fresh.id]) }

            freshCards.forEach { existingById[it.id] = it }
            generatedDeck.addAll(freshCards)
        }

        // 2. Selected PDFs
        val chosenPdfs = availablePdfs.filter { it.id in selection.pdfIds }
        for (pdf in chosenPdfs) {
            val cachedForPdf = _allStoredCards.value.filter {
                it.sourceType == FlashcardSourceType.PDF &&
                    it.sourceId == pdf.id &&
                    isQualityFlashcard(it.front, it.back)
            }
            if (cachedForPdf.isNotEmpty() && !forceRegenerate) {
                generatedDeck.addAll(cachedForPdf)
                continue
            }
            val pdfFile = File(pdf.localFilePath)
            val extractedText = PdfBitmapConverter.extractPdfTextOnlyFast(
                context = appCtx,
                pdfFile = pdfFile,
                maxPages = 20,
                maxChars = 9_000
            ).trim()
            val titleClean = pdf.displayName.ifBlank { pdf.title }.ifBlank { "PDF Kaynak" }
            val freshCards = generateCardsWithAiOrFallback(
                context = appCtx,
                sourceType = FlashcardSourceType.PDF,
                sourceId = pdf.id,
                sourceTitle = titleClean,
                rawContent = if (extractedText.isNotBlank()) "$titleClean\n\n$extractedText" else titleClean,
                density = density
            ).map { fresh -> mergeWithPreviousStats(fresh, existingById[fresh.id]) }

            freshCards.forEach { existingById[it.id] = it }
            generatedDeck.addAll(freshCards)
        }

        // 3. Selected Error Bank Subjects
        if (selection.errorSubjects.isNotEmpty()) {
            val db = SharkMentorDatabase.getDatabase(appCtx)
            val allErrors = runCatching { db.errorQuestionDao().getAllQuestionsRaw() }.getOrDefault(emptyList())
            val matchingErrors = allErrors
                .filter { it.subject in selection.errorSubjects }
                .take(45)
            for (err in matchingErrors) {
                val cardId = "err_${err.id}"
                val prev = existingById[cardId]
                val cleanTopic = err.aiTopic?.takeIf { it.isNotBlank() }
                    ?: err.topic.takeIf { it.isNotBlank() }
                    ?: err.subject
                val frontPrompt = "${err.subject} • $cleanTopic\nBu sorudaki temel çözüm adımını ve dikkat noktasını hatırla."
                val backPrompt = if (!err.solutionImageUri.isNullOrBlank()) {
                    "Çözüm görselini incele • Konu: $cleanTopic"
                } else {
                    "Konu: $cleanTopic • Tekrar kontrolü tamamlandı."
                }
                val errCard = SmartFlashcard(
                    id = cardId,
                    sourceType = FlashcardSourceType.ERROR_BANK,
                    sourceId = err.subject,
                    sourceTitle = "Hata Bankası • ${err.subject}",
                    subject = err.subject,
                    topic = cleanTopic,
                    front = frontPrompt,
                    back = backPrompt,
                    questionImageUri = err.questionImageUri.takeIf { it.isNotBlank() },
                    solutionImageUri = err.solutionImageUri?.takeIf { it.isNotBlank() },
                    errorQuestionId = err.id,
                    knownCount = prev?.knownCount ?: err.reps,
                    missedCount = prev?.missedCount ?: err.lapses,
                    lastOutcomeKnown = prev?.lastOutcomeKnown ?: when {
                        err.lapses > 0 && err.state == 3 -> false
                        err.reps > 0 -> true
                        else -> null
                    },
                    lastReviewedAtMillis = prev?.lastReviewedAtMillis ?: (err.lastReviewMillis ?: 0L)
                )
                existingById[cardId] = errCard
                generatedDeck.add(errCard)
            }
        }

        // Replace stale Note/PDF cards if regenerated, keep other stored cards intact
        val refreshedSourceKeys = buildSet {
            if (forceRegenerate) {
                chosenNotes.forEach { add("${FlashcardSourceType.NOTE.name}:${it.id}") }
                chosenPdfs.forEach { add("${FlashcardSourceType.PDF.name}:${it.id}") }
            }
        }
        val mergedAll = (_allStoredCards.value.filterNot {
            "${it.sourceType.name}:${it.sourceId}" in refreshedSourceKeys || existingById.containsKey(it.id)
        } + existingById.values).distinctBy { it.id }

        _allStoredCards.value = mergedAll
        persistStore(appCtx)

        orderDeckByPriority(generatedDeck.distinctBy { it.id }, _topicStats.value)
    }

    private fun mergeWithPreviousStats(
        fresh: SmartFlashcard,
        previous: SmartFlashcard?
    ): SmartFlashcard {
        if (previous == null) return fresh
        return fresh.copy(
            knownCount = previous.knownCount,
            missedCount = previous.missedCount,
            lastOutcomeKnown = previous.lastOutcomeKnown,
            lastReviewedAtMillis = previous.lastReviewedAtMillis
        )
    }

    private suspend fun generateCardsWithAiOrFallback(
        context: Context,
        sourceType: FlashcardSourceType,
        sourceId: String,
        sourceTitle: String,
        rawContent: String,
        density: FlashcardDensity = FlashcardDensity.STANDARD
    ): List<SmartFlashcard> {
        if (rawContent.length >= 30) {
            val systemPrompt = """
                Sen YKS ve üniversite sınavına hazırlanan öğrenciler için Anki ve NotebookLM kalitesinde aktif hatırlama (active recall) bilgi kartları üreten uzman bir eğitmensin.
                ${density.promptGuidance}

                KESİN KURALLAR:
                1. Her kartın "front" alanı MUTLAKA net, tek bir bilgiyi yoklayan gerçek bir soru cümlesi olsun ve soru işareti (?) ile bitsin (örnek: "Oksijenli solunumda Krebs döngüsü ökaryot hücrenin hangi bölümünde gerçekleşir ve net kaç ATP üretilir?").
                2. ASLA başlık, madde adı veya soru olmayan genel ifadeler yazma ("Önemli Nokta 1", "Konu özeti", sadece kavram adı vb. YASAK).
                3. Sorular doğrudan verilen kaynak metindeki tanım, formül, neden-sonuç ilişkisi, karşılaştırma, istisna veya sık yapılan sınav tuzaklarını ölçsün.
                4. "back" alanı kısa, net, doğrudan soruyu yanıtlayan ve ezberlenebilir bir cevap olsun (1-3 cümle).
                5. Yalnızca geçerli bir JSON dizisi döndür, başına/sonuna açıklama veya markdown ekleme:
                [{"subject":"Ders Adı","topic":"Alt Konu","front":"Net ve ölçülebilir sınav sorusu?","back":"Özlü ve doğru cevap."}]
            """.trimIndent()

            val primaryJson = runCatching {
                MultiProviderAiClient.executeUnifiedTextPrompt(
                    context = context,
                    systemPrompt = systemPrompt,
                    userPrompt = "Kaynak Başlığı: $sourceTitle\n\nDers İçeriği:\n$rawContent",
                    temperature = 0.25f,
                    maxTokens = density.maxTokens,
                    responseJson = true,
                    timeoutMs = 55_000L
                )
            }.getOrDefault("")

            val parsedPrimary = parseAiFlashcardsJson(
                rawJson = primaryJson,
                sourceType = sourceType,
                sourceId = sourceId,
                sourceTitle = sourceTitle
            )
            if (parsedPrimary.size >= 2) return parsedPrimary

            // Second attempt without strict responseJson flag for providers that wrap JSON in text
            val retryJson = runCatching {
                MultiProviderAiClient.executeUnifiedTextPrompt(
                    context = context,
                    systemPrompt = "Sadece JSON dizisi döndür: [{\"subject\":\"Ders\",\"topic\":\"Konu\",\"front\":\"Soru?\",\"back\":\"Cevap\"}]. Her front gerçek bir soru olsun ve ? ile bitsin.",
                    userPrompt = "Kaynak: $sourceTitle\n\nBu metinden 6 adet kaliteli soru-cevap bilgi kartı çıkar:\n${rawContent.take(5500)}",
                    temperature = 0.2f,
                    maxTokens = 2000,
                    responseJson = false,
                    timeoutMs = 35_000L
                )
            }.getOrDefault("")

            val parsedRetry = parseAiFlashcardsJson(
                rawJson = retryJson,
                sourceType = sourceType,
                sourceId = sourceId,
                sourceTitle = sourceTitle
            )
            if (parsedRetry.isNotEmpty()) return parsedRetry
            if (parsedPrimary.isNotEmpty()) return parsedPrimary
        }

        // Clean deterministic offline fallback: extract real concept/definition pairs or cloze questions
        return buildStructuredOfflineFallbackCards(
            sourceType = sourceType,
            sourceId = sourceId,
            sourceTitle = sourceTitle,
            rawContent = rawContent
        )
    }

    private fun buildStructuredOfflineFallbackCards(
        sourceType: FlashcardSourceType,
        sourceId: String,
        sourceTitle: String,
        rawContent: String
    ): List<SmartFlashcard> {
        val lines = rawContent
            .split(Regex("""\n+"""))
            .map { it.trim().trimStart('-', '*', '•', ' ') }
            .filter { it.length >= 16 && !it.equals(sourceTitle, ignoreCase = true) }

        val out = mutableListOf<SmartFlashcard>()
        for (line in lines) {
            if (out.size >= 6) break
            // Pattern 1: "Kavram: Açıklama" or "Kavram - Açıklama"
            val colonIdx = line.indexOf(':').takeIf { it in 3..58 }
                ?: line.indexOf(" - ").takeIf { it in 3..58 }
            if (colonIdx != null) {
                val term = line.substring(0, colonIdx).trim()
                val def = line.substring(colonIdx + 1).trimStart('-', ' ', ':').trim()
                if (term.length >= 3 && def.length >= 10) {
                    val front = "$term nedir ve temel özelliği nasıl tanımlanır?"
                    val id = stableCardHash("${sourceType.name}|$sourceId|def|${term.lowercase(Locale.ROOT)}")
                    out.add(
                        SmartFlashcard(
                            id = id,
                            sourceType = sourceType,
                            sourceId = sourceId,
                            sourceTitle = sourceTitle,
                            topic = sourceTitle,
                            front = front,
                            back = def.take(480)
                        )
                    )
                    continue
                }
            }
            // Pattern 2: Cloze deletion on the longest meaningful word (>5 chars)
            val words = line.split(Regex("""\s+"""))
            val targetIdx = words.indices
                .filter { idx ->
                    val clean = words[idx].trim(',', '.', ';', ':', '(', ')', '"', '\'')
                    clean.length >= 6 && clean.all { it.isLetter() }
                }
                .maxByOrNull { words[it].length }
            if (targetIdx != null && words.size >= 5) {
                val answerWord = words[targetIdx].trim(',', '.', ';', ':', '(', ')', '"', '\'')
                val masked = words.mapIndexed { i, w ->
                    if (i == targetIdx) "______" else w
                }.joinToString(" ")
                val front = "Boşluğu doldur ($sourceTitle): $masked"
                val id = stableCardHash("${sourceType.name}|$sourceId|cloze|${line.take(40)}")
                out.add(
                    SmartFlashcard(
                        id = id,
                        sourceType = sourceType,
                        sourceId = sourceId,
                        sourceTitle = sourceTitle,
                        topic = sourceTitle,
                        front = front.take(360),
                        back = "$answerWord — $line".take(480)
                    )
                )
            }
        }
        if (out.isNotEmpty()) return out.distinctBy { it.id }

        val singleId = stableCardHash("${sourceType.name}|$sourceId|$sourceTitle")
        return listOf(
            SmartFlashcard(
                id = singleId,
                sourceType = sourceType,
                sourceId = sourceId,
                sourceTitle = sourceTitle,
                topic = sourceTitle,
                front = "$sourceTitle konusunda bilmen gereken temel kavramlar nelerdir?",
                back = rawContent.ifBlank { sourceTitle }.take(420)
            )
        )
    }

    /**
     * Records a single card outcome ("Yaptım" = true, "Yapamadım" = false), updates topic priority memory,
     * and syncs Error Bank FSRS state if backed by an `ErrorQuestionEntity`.
     */
    suspend fun recordCardOutcome(
        context: Context,
        card: SmartFlashcard,
        known: Boolean
    ): SmartFlashcard = withContext(Dispatchers.IO) {
        ensureLoaded(context)
        val now = System.currentTimeMillis()
        val updatedCard = card.copy(
            knownCount = if (known) card.knownCount + 1 else card.knownCount,
            missedCount = if (!known) card.missedCount + 1 else card.missedCount,
            lastOutcomeKnown = known,
            lastReviewedAtMillis = now
        )

        val nextCards = _allStoredCards.value.let { list ->
            val replaced = list.map { if (it.id == updatedCard.id) updatedCard else it }
            if (replaced.any { it.id == updatedCard.id }) replaced else replaced + updatedCard
        }
        _allStoredCards.value = nextCards

        val topicKey = normalizeTopicKey(card.subject, card.topic)
        val prevTopic = _topicStats.value[topicKey] ?: TopicPerformanceRecord(topicKey = topicKey)
        val nextTopic = prevTopic.copy(
            knownTotal = if (known) prevTopic.knownTotal + 1 else prevTopic.knownTotal,
            missedTotal = if (!known) prevTopic.missedTotal + 1 else prevTopic.missedTotal,
            lastKnown = known,
            updatedAtMillis = now
        )
        _topicStats.value = _topicStats.value + (topicKey to nextTopic)
        persistStore(context)

        // Sync Error Bank FSRS interval locally if card came from Error Bank
        val errId = card.errorQuestionId
        if (errId != null && errId > 0L) {
            runCatching {
                val dao = SharkMentorDatabase.getDatabase(context).errorQuestionDao()
                val existing: ErrorQuestionEntity? = dao.getAllQuestionsRaw().firstOrNull { it.id == errId }
                if (existing != null) {
                    val nextEntity = FsrsEngine.calculateNextReview(
                        entity = existing,
                        rating = if (known) FsrsRating.GOOD else FsrsRating.AGAIN,
                        reviewTimeMillis = now
                    ).copy(isSynced = false, lastModifiedMillis = now)
                    dao.updateQuestion(nextEntity)
                }
            }
        }

        updatedCard
    }

    /**
     * Writes a concise summary of mastered and missed flashcard topics into `StudentDossierManager`
     * when AI Coaching (`CoachingEngine.comprehensiveSupportEnabled`) is active.
     */
    suspend fun syncSessionOutcomesToDossierIfActive(
        context: Context,
        sessionResults: Map<SmartFlashcard, Boolean>
    ) = withContext(Dispatchers.IO) {
        if (sessionResults.isEmpty()) return@withContext
        if (sessionResults.keys.any { it.errorQuestionId != null }) {
            com.example.sharkmentor.service.sync.SyncWorker.enqueueImmediateSync(context.applicationContext)
        }
        CoachingEngine.ensureInitialized(context)
        if (!CoachingEngine.comprehensiveSupportEnabled.value) return@withContext
        StudentDossierManager.ensureLoaded(context)
        val dossier = StudentDossierManager.dossier.value
        if (!dossier.isCompleted && !dossier.consentAccepted) return@withContext

        val mastered = sessionResults
            .filter { it.value }
            .keys
            .map { formatTopicLabel(it) }
            .distinct()
        val struggled = sessionResults
            .filter { !it.value }
            .keys
            .map { formatTopicLabel(it) }
            .distinct()

        StudentDossierManager.recordFlashcardStudySummary(
            context = context,
            masteredTopics = mastered,
            struggledTopics = struggled
        )
    }

    private val flushScope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + Dispatchers.IO
    )

    fun flushSessionOutcomesInBackground(
        context: Context,
        sessionResults: Map<SmartFlashcard, Boolean>
    ) {
        if (sessionResults.isEmpty()) return
        val appCtx = context.applicationContext
        val snapshot = sessionResults.toMap()
        flushScope.launch {
            runCatching {
                syncSessionOutcomesToDossierIfActive(appCtx, snapshot)
            }
        }
    }

    private fun formatTopicLabel(card: SmartFlashcard): String {
        val subj = card.subject.trim()
        val top = card.topic.trim().ifBlank { card.sourceTitle.trim() }
        return if (subj.isNotBlank() && !top.startsWith(subj, ignoreCase = true)) {
            "$subj - $top"
        } else {
            top
        }
    }
}
