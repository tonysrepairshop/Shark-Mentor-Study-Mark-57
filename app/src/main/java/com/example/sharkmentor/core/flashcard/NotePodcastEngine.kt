package com.example.sharkmentor.core.flashcard

import android.content.Context
import androidx.compose.runtime.Immutable
import com.example.sharkmentor.core.ai.MultiProviderAiClient
import com.example.sharkmentor.core.util.ImportedPdfDocument
import com.example.sharkmentor.core.util.PdfBitmapConverter
import com.example.sharkmentor.data.local.entity.NoteEntity
import com.example.sharkmentor.ui.screens.notes.NoteDetailViewModel
import com.example.sharkmentor.ui.screens.notes.NoteRichTextHelper
import com.example.sharkmentor.ui.screens.notes.drawing.DrawingSerializer
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.Locale

@Immutable
data class PodcastSegment(
    val title: String,
    val hostA: String,
    val hostB: String
)

@Immutable
data class GeneratedNotePodcast(
    val cacheKey: String,
    val episodeTitle: String,
    val sourceSummaryLabel: String,
    val segments: List<PodcastSegment>,
    val createdAtMillis: Long = System.currentTimeMillis()
)

/**
 * Pure JVM-testable parser for AI-generated Note/PDF Podcast JSON.
 */
fun parsePodcastSegmentsJson(rawJson: String): List<PodcastSegment> {
    val cleaned = rawJson
        .trim()
        .removePrefix("```json")
        .removePrefix("```JSON")
        .removePrefix("```")
        .removeSuffix("```")
        .trim()
    val arrStart = cleaned.indexOf('[')
    val arrEnd = cleaned.lastIndexOf(']')
    val candidate = if (arrStart >= 0 && arrEnd > arrStart && !cleaned.trimStart().startsWith("{")) {
        cleaned.substring(arrStart, arrEnd + 1)
    } else {
        cleaned
    }

    return runCatching {
        val root = JsonParser.parseString(candidate)
        val arr = when {
            root.isJsonArray -> root.asJsonArray
            root.isJsonObject -> {
                val obj = root.asJsonObject
                listOf("segments", "chapters", "dialogue", "podcast", "parts", "items")
                    .firstOrNull { key -> obj.has(key) && obj.get(key).isJsonArray }
                    ?.let { obj.getAsJsonArray(it) }
                    ?: return emptyList()
            }
            else -> return emptyList()
        }
        val list = mutableListOf<PodcastSegment>()
        for (el in arr) {
            if (!el.isJsonObject) continue
            val obj = el.asJsonObject
            val title = (
                obj.get("title")?.takeIf { !it.isJsonNull }?.asString
                    ?: obj.get("chapter")?.takeIf { !it.isJsonNull }?.asString
                    ?: obj.get("baslik")?.takeIf { !it.isJsonNull }?.asString
                    ?: "Bölüm ${list.size + 1}"
                ).trim()
            val hostA = (
                obj.get("hostA")?.takeIf { !it.isJsonNull }?.asString
                    ?: obj.get("host1")?.takeIf { !it.isJsonNull }?.asString
                    ?: obj.get("speaker1")?.takeIf { !it.isJsonNull }?.asString
                    ?: obj.get("sunucu1")?.takeIf { !it.isJsonNull }?.asString
                    ?: obj.get("summary")?.takeIf { !it.isJsonNull }?.asString
                    ?: ""
                ).trim()
            val hostB = (
                obj.get("hostB")?.takeIf { !it.isJsonNull }?.asString
                    ?: obj.get("host2")?.takeIf { !it.isJsonNull }?.asString
                    ?: obj.get("speaker2")?.takeIf { !it.isJsonNull }?.asString
                    ?: obj.get("sunucu2")?.takeIf { !it.isJsonNull }?.asString
                    ?: obj.get("detail")?.takeIf { !it.isJsonNull }?.asString
                    ?: ""
                ).trim()
            if (hostA.length < 8 && hostB.length < 8) continue
            list.add(
                PodcastSegment(
                    title = title.ifBlank { "Bölüm ${list.size + 1}" }.take(80),
                    hostA = sanitizeForNaturalSpeech(hostA.ifBlank { hostB }).take(900),
                    hostB = sanitizeForNaturalSpeech(hostB).take(900)
                )
            )
        }
        list
    }.getOrDefault(emptyList())
}

fun computePodcastSourceKey(noteIds: Set<String>, pdfIds: Set<String>): String {
    val canonical = buildString {
        append("notes:")
        append(noteIds.map { it.trim() }.filter { it.isNotBlank() }.sorted().joinToString(","))
        append("|pdfs:")
        append(pdfIds.map { it.trim() }.filter { it.isNotBlank() }.sorted().joinToString(","))
    }
    val digest = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8))
    return digest.take(10).joinToString("") { "%02x".format(it) }
}

fun sanitizeForNaturalSpeech(raw: String): String {
    return raw
        .replace(Regex("""[*_`#~>•]+"""), " ")
        .replace("²", " kare ")
        .replace("³", " küp ")
        .replace("√", " karekök ")
        .replace("Δ", " delta ")
        .replace("π", " pi ")
        .replace("≤", " küçük eşittir ")
        .replace("≥", " büyük eşittir ")
        .replace("≠", " eşit değildir ")
        .replace("→", ", buradan ")
        .replace(Regex("""\s+"""), " ")
        .trim()
}

/**
 * Generates and caches a true conversational Podcast Summary directly from the student's
 * selected Notes and PDFs (never reading flashcard Q&A verbatim).
 */
object NotePodcastEngine {

    private const val CACHE_FILE_NAME = "note_podcast_cache_v1.json"
    private val gson = Gson()
    private val cacheLock = Any()

    suspend fun loadOrGeneratePodcast(
        context: Context,
        selection: SelectedFlashcardSources,
        availableNotes: List<NoteEntity>,
        availablePdfs: List<ImportedPdfDocument>,
        forceRefresh: Boolean = false
    ): GeneratedNotePodcast = withContext(Dispatchers.IO) {
        val appCtx = context.applicationContext

        val chosenNotes = if (selection.noteIds.isNotEmpty()) {
            availableNotes.filter { it.id in selection.noteIds }
        } else if (selection.pdfIds.isEmpty()) {
            availableNotes.take(3)
        } else {
            emptyList()
        }

        val chosenPdfs = if (selection.pdfIds.isNotEmpty()) {
            availablePdfs.filter { it.id in selection.pdfIds }
        } else if (chosenNotes.isEmpty()) {
            availablePdfs.take(2)
        } else {
            emptyList()
        }

        val effectiveNoteIds = chosenNotes.map { it.id }.toSet()
        val effectivePdfIds = chosenPdfs.map { it.id }.toSet()
        val cacheKey = computePodcastSourceKey(effectiveNoteIds, effectivePdfIds)

        if (!forceRefresh) {
            readCachedEpisode(appCtx, cacheKey)?.let { cached ->
                if (cached.segments.isNotEmpty()) return@withContext cached
            }
        }

        val sourceBlocks = mutableListOf<Pair<String, String>>()

        for (note in chosenNotes) {
            val plainBody = NoteRichTextHelper.toPlainText(
                NoteDetailViewModel.sanitizeNoteTextForAi(note.content)
            ).trim()
            val canvasBoxes = runCatching {
                DrawingSerializer.loadNotePageTextBoxes(appCtx, note.id, 0)
                    .joinToString("\n") { NoteRichTextHelper.toPlainText(it.taggedContent) }
            }.getOrDefault("").trim()
            val merged = listOf(plainBody, canvasBoxes).filter { it.isNotBlank() }.joinToString("\n")
            if (merged.isNotBlank()) {
                sourceBlocks.add((note.title.ifBlank { "Ders Notu" }) to merged.take(5_500))
            }
        }

        for (pdf in chosenPdfs) {
            val file = File(pdf.localFilePath)
            val text = PdfBitmapConverter.extractPdfTextOnlyFast(
                context = appCtx,
                pdfFile = file,
                maxPages = 18,
                maxChars = 6_500
            ).trim()
            val pdfTitle = pdf.displayName.ifBlank { pdf.title }.ifBlank { "PDF Kaynak" }
            if (text.isNotBlank()) {
                sourceBlocks.add(pdfTitle to text)
            }
        }

        if (sourceBlocks.isEmpty()) {
            return@withContext GeneratedNotePodcast(
                cacheKey = cacheKey,
                episodeTitle = "Podcast Özeti",
                sourceSummaryLabel = "Kaynak bulunamadı",
                segments = emptyList()
            )
        }

        val sourceTitlesLabel = sourceBlocks.joinToString(", ") { it.first }.take(72)
        val combinedCorpus = sourceBlocks.joinToString("\n\n---\n\n") { (title, body) ->
            "KAYNAK: $title\n$body"
        }.take(14_000)

        val systemPrompt = """
Sen NotebookLM Audio Overview kalitesinde, öğrencinin ders notlarını ve PDF kaynaklarını iki sunuculu (1. Sunucu: konuyu akıcı biçimde açan ve öğrencinin aklındaki kritik soruları yönelten anlatıcı; 2. Sunucu: konuyu sezgisel benzetmelerle, temel mantığıyla ve YKS/sınav tuzaklarıyla açıklayan uzman anlatıcı) doğal bir Türkçe eğitim podcast'ine dönüştüren sesli içerik yönetmenisin.
KESİN KURALLAR:
1. Asla bilgi kartı veya madde listesi okur gibi mekanik konuşma; gerçek iki insanın stüdyoda sohbet ettiği sıcak, akıcı ve sürükleyici bir anlatım kur.
2. Her bölüm bir önceki bölümle bağlantılı ilerlesin: ilk bölüm konuya ilgi çekici bir giriş yapsın, orta bölümler kritik kavramları, neden-sonuç ilişkilerini ve sınavda en çok karıştırılan noktaları derinleştirsin, son bölüm ise akılda kalıcı kısa bir genel toparlama yapsın.
3. Sembol, markdown veya LaTeX kodu kullanma; tüm matematiksel/bilimsel ifadeleri doğal Türkçe okunuşuyla yaz (örnek: "x kare artı iki x", "delta büyük sıfır"). Emoji kullanma.
4. İçeriğin zenginliğine göre 5 ile 7 bölüm üret. Her konuşma 2 ile 4 akıcı cümle olsun.
5. Yalnızca geçerli bir JSON dizisi döndür:
[{"title":"Bölüm Başlığı","hostA":"Birinci sunucunun doğal, merak uyandıran anlatımı","hostB":"İkinci sunucunun net, öğretici ve sınav odaklı açıklaması"}]
""".trimIndent()

        var aiSegments = runCatching {
            val rawResp = MultiProviderAiClient.executeUnifiedTextPrompt(
                context = appCtx,
                systemPrompt = systemPrompt,
                userPrompt = "Aşağıdaki ders notu ve PDF içeriklerinden iki sunuculu, akıcı bir çalışma podcast'i hazırla:\n\n$combinedCorpus",
                temperature = 0.45f,
                maxTokens = 3000,
                responseJson = true,
                timeoutMs = 42_000L
            )
            parsePodcastSegmentsJson(rawResp)
        }.getOrDefault(emptyList())

        if (aiSegments.isEmpty()) {
            aiSegments = runCatching {
                val retryResp = MultiProviderAiClient.executeUnifiedTextPrompt(
                    context = appCtx,
                    systemPrompt = "Sadece geçerli bir JSON dizisi döndür: [{\"title\":\"Başlık\",\"hostA\":\"1. sunucu konuşması\",\"hostB\":\"2. sunucu konuşması\"}]. Emoji ve sembol kullanma.",
                    userPrompt = "Aşağıdaki ders içeriğini iki sunuculu 5 bölümlük podcast sohbeti olarak özetle:\n\n${combinedCorpus.take(7500)}",
                    temperature = 0.4f,
                    maxTokens = 2400,
                    responseJson = false,
                    timeoutMs = 35_000L
                )
                parsePodcastSegmentsJson(retryResp)
            }.getOrDefault(emptyList())
        }

        val usedAi = aiSegments.isNotEmpty()
        val finalSegments = if (usedAi) {
            aiSegments
        } else {
            buildOfflineSummaryFallbackSegments(sourceBlocks)
        }

        val episode = GeneratedNotePodcast(
            cacheKey = cacheKey,
            episodeTitle = if (sourceBlocks.size == 1) {
                "${sourceBlocks.first().first} • Podcast Özeti"
            } else {
                "Seçili Notlar (${sourceBlocks.size} Kaynak) • Podcast Özeti"
            },
            sourceSummaryLabel = if (usedAi) sourceTitlesLabel else "$sourceTitlesLabel • Çevrimdışı Özet",
            segments = finalSegments
        )

        // Only cache real AI-generated podcasts so transient network failures auto-retry next time
        if (usedAi && finalSegments.isNotEmpty()) {
            saveCachedEpisode(appCtx, episode)
        }
        episode
    }

    internal fun buildOfflineSummaryFallbackSegments(
        sources: List<Pair<String, String>>
    ): List<PodcastSegment> {
        val segments = mutableListOf<PodcastSegment>()
        for ((title, body) in sources) {
            val paragraphs = body
                .split(Regex("""\n{2,}|(?<=[.!?])\s+"""))
                .map { sanitizeForNaturalSpeech(it) }
                .filter { it.length >= 25 }
            if (paragraphs.isEmpty()) continue
            val chunked = paragraphs.chunked(3).take(3)
            chunked.forEachIndexed { idx, group ->
                val lead = group.first()
                val follow = group.drop(1).joinToString(" ")
                segments.add(
                    PodcastSegment(
                        title = if (chunked.size > 1) "$title (${idx + 1}. Kısım)" else title,
                        hostA = lead.take(550),
                        hostB = follow.ifBlank { "Bu bölümdeki temel noktaları notlarından takip edebilirsin." }.take(550)
                    )
                )
            }
        }
        return segments.take(6)
    }

    private fun readCachedEpisode(context: Context, key: String): GeneratedNotePodcast? {
        synchronized(cacheLock) {
            val file = File(context.filesDir, CACHE_FILE_NAME)
            if (!file.exists()) return null
            val map = runCatching {
                val type = object : TypeToken<Map<String, GeneratedNotePodcast>>() {}.type
                gson.fromJson<Map<String, GeneratedNotePodcast>>(file.readText(Charsets.UTF_8), type)
            }.getOrNull() ?: return null
            return map[key]
        }
    }

    private fun saveCachedEpisode(context: Context, episode: GeneratedNotePodcast) {
        synchronized(cacheLock) {
            val file = File(context.filesDir, CACHE_FILE_NAME)
            val existing = if (file.exists()) {
                runCatching {
                    val type = object : TypeToken<MutableMap<String, GeneratedNotePodcast>>() {}.type
                    gson.fromJson<MutableMap<String, GeneratedNotePodcast>>(file.readText(Charsets.UTF_8), type)
                }.getOrNull() ?: mutableMapOf()
            } else {
                mutableMapOf()
            }
            existing[episode.cacheKey] = episode
            // Keep max 12 cached episodes so storage stays tiny
            if (existing.size > 12) {
                val oldestKey = existing.entries.minByOrNull { it.value.createdAtMillis }?.key
                if (oldestKey != null) existing.remove(oldestKey)
            }
            runCatching {
                file.writeText(gson.toJson(existing), Charsets.UTF_8)
            }
        }
    }
}
