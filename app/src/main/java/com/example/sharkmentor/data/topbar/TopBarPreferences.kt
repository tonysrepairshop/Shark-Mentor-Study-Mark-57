package com.example.sharkmentor.data.topbar

import android.content.Context
import android.content.SharedPreferences
import com.example.sharkmentor.core.ai.ApiKeyRotator
import com.example.sharkmentor.core.ai.GeminiAiFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

enum class TopBarTextSource(val label: String) {
    AI_SUGGESTIONS("AI & Uygulama Önerileri"),
    CUSTOM_TEXTS("Kendi Metinlerim"),
    AI_QUOTES("AI Motivasyon")
}

enum class TopBarAnimationType(val label: String) {
    NONE("Kapalı"),
    TYPEWRITER("Daktilo"),
    FLIP_BOX("Dönen Kutu"),
    FADE("Yumuşak Geçiş"),
    SLIDE_VERTICAL("Yukarı Kaydır"),
    SCALE_FADE("Büyüterek Geç")
}

enum class TopBarFontOption(val label: String) {
    DEFAULT("Varsayılan"),
    SANS_SERIF("Modern Sans"),
    SERIF("Klasik Serif"),
    MONOSPACE("Daktilo Mono")
}

/**
 * Wide fixed-interval scale for rotating top bar texts / motivations / suggestions.
 */
enum class TopBarRefreshInterval(val durationMs: Long, val label: String) {
    SEC_5(5_000L, "5 sn"),
    SEC_10(10_000L, "10 sn"),
    SEC_15(15_000L, "15 sn"),
    SEC_30(30_000L, "30 sn"),
    MIN_1(60_000L, "1 dk"),
    MIN_3(180_000L, "3 dk"),
    MIN_5(300_000L, "5 dk"),
    MIN_10(600_000L, "10 dk"),
    MIN_15(900_000L, "15 dk"),
    MIN_30(1_800_000L, "30 dk"),
    HOUR_1(3_600_000L, "1 sa"),
    HOUR_3(10_800_000L, "3 sa"),
    HOUR_6(21_600_000L, "6 sa"),
    HOUR_12(43_200_000L, "12 sa"),
    HOUR_24(86_400_000L, "24 sa");

    companion object {
        fun fromNameOrDefault(name: String?): TopBarRefreshInterval =
            entries.firstOrNull { it.name == name } ?: SEC_10
    }
}

private val TR_LOCALE_TOPBAR = java.util.Locale("tr", "TR")

/**
 * Guarantees every AI motivation quote and top-bar sentence begins with an uppercase Turkish letter
 * (properly mapping `i -> İ`, `ı -> I`, `ş -> Ş`, `ğ -> Ğ`, `ü -> Ü`, `ö -> Ö`, `ç -> Ç`).
 */
fun capitalizeTurkishSentence(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return ""
    val firstLetterIdx = trimmed.indexOfFirst { it.isLetter() }
    if (firstLetterIdx < 0) return trimmed
    val ch = trimmed[firstLetterIdx]
    val upper = ch.toString().uppercase(TR_LOCALE_TOPBAR)
    return trimmed.substring(0, firstLetterIdx) + upper + trimmed.substring(firstLetterIdx + 1)
}

enum class TopBarModule(val label: String) {
    BRANDING("Logo"),
    MUSIC("Müzik"),
    WEATHER("Hava Durumu"),
    AI_BAR("Yazı / AI Barı"),
    DM("Mesajlar"),
    COUNTDOWN("Sınav Sayacı");

    companion object {
        val DEFAULT_ORDER: List<TopBarModule> = listOf(
            BRANDING, MUSIC, WEATHER, AI_BAR, DM, COUNTDOWN
        )
    }
}

enum class TopBarModuleSize(val label: String, val scale: Float) {
    COMPACT("Küçük", 0.86f),
    NORMAL("Normal", 1.0f),
    LARGE("Büyük", 1.14f);

    fun next(): TopBarModuleSize = when (this) {
        COMPACT -> NORMAL
        NORMAL -> LARGE
        LARGE -> COMPACT
    }
}

data class TopBarSettings(
    val textSource: TopBarTextSource = TopBarTextSource.AI_SUGGESTIONS,
    val animationType: TopBarAnimationType = TopBarAnimationType.FADE,
    val fontOption: TopBarFontOption = TopBarFontOption.DEFAULT,
    val refreshInterval: TopBarRefreshInterval = TopBarRefreshInterval.SEC_10,
    val customTextColorArgb: Long? = null,
    val customTexts: List<String> = listOf(""),
    val aiQuotesTopic: String = "",
    val aiQuotes: List<String> = emptyList(),
    val hideBubbles: Boolean = false,
    val hideTopBarMenu: Boolean = false,
    val hideDuringFocus: Boolean = false,
    val inAppSuggestionsEnabled: Boolean = true,
    val hydrationReminderEnabled: Boolean = true,
    val hydrationIntervalMinutes: Int = 45,
    val postureReminderEnabled: Boolean = true,
    val postureIntervalMinutes: Int = 50,
    val moduleOrder: List<TopBarModule> = TopBarModule.DEFAULT_ORDER,
    val hiddenModules: Set<TopBarModule> = emptySet(),
    val moduleSizes: Map<TopBarModule, TopBarModuleSize> = emptyMap()
) {
    fun isModuleVisible(module: TopBarModule): Boolean = module !in hiddenModules
    fun sizeFor(module: TopBarModule): TopBarModuleSize = moduleSizes[module] ?: TopBarModuleSize.NORMAL
}

object FocusSessionState {
    private val _isAnyFocusRunning = MutableStateFlow(false)
    val isAnyFocusRunning: StateFlow<Boolean> = _isAnyFocusRunning.asStateFlow()

    fun setRunning(running: Boolean) {
        _isAnyFocusRunning.value = running
    }
}

class TopBarPreferences private constructor(context: Context) {
    private val appContext: Context = context.applicationContext
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<TopBarSettings> = _settings.asStateFlow()

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    private fun loadSettings(): TopBarSettings {
        val sourceName = prefs.getString(KEY_TEXT_SOURCE, TopBarTextSource.AI_SUGGESTIONS.name)
        val animName = prefs.getString(KEY_ANIMATION_TYPE, TopBarAnimationType.FADE.name)
        val fontName = prefs.getString(KEY_FONT_OPTION, TopBarFontOption.DEFAULT.name)
        val refreshName = prefs.getString(KEY_REFRESH_INTERVAL, TopBarRefreshInterval.SEC_10.name)

        val textSource = runCatching { TopBarTextSource.valueOf(sourceName ?: "") }
            .getOrDefault(TopBarTextSource.AI_SUGGESTIONS)
        val animationType = runCatching { TopBarAnimationType.valueOf(animName ?: "") }
            .getOrDefault(TopBarAnimationType.FADE)
        val fontOption = runCatching { TopBarFontOption.valueOf(fontName ?: "") }
            .getOrDefault(TopBarFontOption.DEFAULT)
        val refreshInterval = TopBarRefreshInterval.fromNameOrDefault(refreshName)

        val colorArgb = if (prefs.contains(KEY_CUSTOM_COLOR)) {
            prefs.getLong(KEY_CUSTOM_COLOR, 0L)
        } else {
            null
        }

        val customTexts = readStringList(KEY_CUSTOM_TEXTS)
            .take(MAX_CUSTOM_TEXTS)
            .ifEmpty { listOf("") }

        val aiQuotesTopic = prefs.getString(KEY_AI_QUOTES_TOPIC, "").orEmpty()
        val aiQuotes = readStringList(KEY_AI_QUOTES)
            .map { capitalizeTurkishSentence(it) }
            .filter { it.isNotBlank() }
            .take(MAX_AI_QUOTES)

        return TopBarSettings(
            textSource = textSource,
            animationType = animationType,
            fontOption = fontOption,
            refreshInterval = refreshInterval,
            customTextColorArgb = colorArgb,
            customTexts = customTexts,
            aiQuotesTopic = aiQuotesTopic,
            aiQuotes = aiQuotes,
            hideBubbles = prefs.getBoolean(KEY_HIDE_BUBBLES, false),
            hideTopBarMenu = prefs.getBoolean(KEY_HIDE_TOP_BAR_MENU, false),
            hideDuringFocus = prefs.getBoolean(KEY_HIDE_DURING_FOCUS, false),
            inAppSuggestionsEnabled = prefs.getBoolean(KEY_IN_APP_SUGGESTIONS, true),
            hydrationReminderEnabled = prefs.getBoolean(KEY_HYDRATION_REMINDER, true),
            hydrationIntervalMinutes = prefs.getInt(KEY_HYDRATION_INTERVAL_MIN, 45).coerceIn(20, 120),
            postureReminderEnabled = prefs.getBoolean(KEY_POSTURE_REMINDER, true),
            postureIntervalMinutes = prefs.getInt(KEY_POSTURE_INTERVAL_MIN, 50).coerceIn(20, 120),
            moduleOrder = loadModuleOrder(),
            hiddenModules = loadHiddenModules(),
            moduleSizes = loadModuleSizes()
        )
    }

    private fun readStringList(key: String): List<String> {
        val raw = prefs.getString(key, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            List(arr.length()) { index -> arr.optString(index, "") }
        }.getOrDefault(emptyList())
    }

    private fun writeStringList(editor: SharedPreferences.Editor, key: String, list: List<String>) {
        val arr = JSONArray()
        list.forEach { arr.put(it) }
        editor.putString(key, arr.toString())
    }

    fun setTextSource(source: TopBarTextSource) {
        prefs.edit().putString(KEY_TEXT_SOURCE, source.name).apply()
        _settings.value = _settings.value.copy(textSource = source)
    }

    fun setAnimationType(type: TopBarAnimationType) {
        prefs.edit().putString(KEY_ANIMATION_TYPE, type.name).apply()
        _settings.value = _settings.value.copy(animationType = type)
    }

    fun setFontOption(font: TopBarFontOption) {
        prefs.edit().putString(KEY_FONT_OPTION, font.name).apply()
        _settings.value = _settings.value.copy(fontOption = font)
    }

    fun setRefreshInterval(interval: TopBarRefreshInterval) {
        prefs.edit().putString(KEY_REFRESH_INTERVAL, interval.name).apply()
        _settings.value = _settings.value.copy(refreshInterval = interval)
    }

    fun setCustomTextColorArgb(colorArgb: Long?) {
        val editor = prefs.edit()
        if (colorArgb == null) {
            editor.remove(KEY_CUSTOM_COLOR)
        } else {
            editor.putLong(KEY_CUSTOM_COLOR, colorArgb)
        }
        editor.apply()
        _settings.value = _settings.value.copy(customTextColorArgb = colorArgb)
    }

    fun setCustomTexts(texts: List<String>) {
        val normalized = texts.take(MAX_CUSTOM_TEXTS).ifEmpty { listOf("") }
        prefs.edit().also { writeStringList(it, KEY_CUSTOM_TEXTS, normalized) }.apply()
        _settings.value = _settings.value.copy(customTexts = normalized)
    }

    fun setHideBubbles(hide: Boolean) {
        prefs.edit().putBoolean(KEY_HIDE_BUBBLES, hide).apply()
        _settings.value = _settings.value.copy(hideBubbles = hide)
    }

    fun setHideTopBarMenu(hide: Boolean) {
        prefs.edit().putBoolean(KEY_HIDE_TOP_BAR_MENU, hide).apply()
        _settings.value = _settings.value.copy(hideTopBarMenu = hide)
    }

    fun setHideDuringFocus(hide: Boolean) {
        prefs.edit().putBoolean(KEY_HIDE_DURING_FOCUS, hide).apply()
        _settings.value = _settings.value.copy(hideDuringFocus = hide)
    }

    fun setInAppSuggestionsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_IN_APP_SUGGESTIONS, enabled).apply()
        _settings.value = _settings.value.copy(inAppSuggestionsEnabled = enabled)
    }

    fun setHydrationReminderEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_HYDRATION_REMINDER, enabled).apply()
        _settings.value = _settings.value.copy(hydrationReminderEnabled = enabled)
    }

    fun setHydrationIntervalMinutes(minutes: Int) {
        val clamped = minutes.coerceIn(20, 120)
        prefs.edit().putInt(KEY_HYDRATION_INTERVAL_MIN, clamped).apply()
        _settings.value = _settings.value.copy(hydrationIntervalMinutes = clamped)
    }

    fun setPostureReminderEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_POSTURE_REMINDER, enabled).apply()
        _settings.value = _settings.value.copy(postureReminderEnabled = enabled)
    }

    fun setPostureIntervalMinutes(minutes: Int) {
        val clamped = minutes.coerceIn(20, 120)
        prefs.edit().putInt(KEY_POSTURE_INTERVAL_MIN, clamped).apply()
        _settings.value = _settings.value.copy(postureIntervalMinutes = clamped)
    }

    suspend fun fetchMotivationalQuotes(topic: String): Result<List<String>> = withContext(Dispatchers.IO) {
        val cleanTopic = topic.trim()
        if (cleanTopic.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Konu boş olamaz."))
        }

        com.example.sharkmentor.core.ai.UserAiPreferences.initialize(appContext)
        val (candidateKeys, isBuiltInMode) = GeminiAiFactory.resolveKeyCandidates(
            overrideKey = null,
            context = appContext
        )

        val prompt = buildString {
            append("İnternet araması kullanarak \"$cleanTopic\" filmine, dizisine veya karakterine/kişisine ait ")
            append("en bilinen, gerçek ve ilham verici 8 ila 10 motivasyon sözünü bul. ")
            append("Türkçe olarak ve üst barda tek satırda okunabilecek kısalıkta yaz. ")
            append("Numaralandırma, madde işareti, tırnak işareti veya açıklama ekleme; ")
            append("yalnızca her satırda 1 söz olacak şekilde en az 5, en fazla 10 satır döndür.")
        }

        val models = if (isBuiltInMode) {
            listOf(
                GeminiAiFactory.SOLE_GEMINI_MODEL,
                GeminiAiFactory.CONTROLLED_404_FALLBACK_MODEL
            ).distinct()
        } else {
            val preferred = GeminiAiFactory.resolveModelName(appContext)
            listOf(
                preferred,
                GeminiAiFactory.SOLE_GEMINI_MODEL,
                GeminiAiFactory.CONTROLLED_404_FALLBACK_MODEL
            ).filter { it.isNotBlank() }.distinct()
        }

        var lastError: Throwable? = null
        var bestPartialQuotes: List<String> = emptyList()

        // 1. Direct grounded search call using resolved endpoint per key (Vertex AI Express for AQ., AI Studio for AIza)
        for (model in models) {
            for (rawKey in candidateKeys) {
                val cleanKey = com.example.sharkmentor.core.ai.GeminiRestClient.sanitizeApiKey(rawKey)
                if (cleanKey.isBlank()) continue
                val endpoints = com.example.sharkmentor.core.ai.GeminiRestClient.resolveEndpointsForKey(cleanKey)
                var keyFailedHard = false

                for (endpoint in endpoints) {
                    for (useSearch in listOf(true, false)) {
                        val res = requestQuotesFromGemini(
                            apiKey = cleanKey,
                            endpoint = endpoint,
                            model = model,
                            prompt = prompt,
                            useGoogleSearch = useSearch
                        )
                        if (res.isSuccess) {
                            ApiKeyRotator.reportKeySuccess(cleanKey)
                            val quotes = res.getOrThrow().take(MAX_AI_QUOTES)
                            if (quotes.size >= MIN_AI_QUOTES) {
                                saveQuotesAndActivate(cleanTopic, quotes)
                                return@withContext Result.success(quotes)
                            }
                            if (quotes.size > bestPartialQuotes.size) {
                                bestPartialQuotes = quotes
                            }
                        } else {
                            lastError = res.exceptionOrNull()
                            val msg = lastError?.message.orEmpty()
                            if (msg.contains("HTTP 401") || msg.contains("HTTP 403") || msg.contains("HTTP 429")) {
                                ApiKeyRotator.reportKeyFailure(cleanKey)
                                keyFailedHard = true
                                break
                            }
                        }
                    }
                    if (keyFailedHard) break
                }
            }
        }

        // 2. Fallback via GeminiAiFactory (supports Firebase Gemini Proxy + full model/key failover)
        val proxyFallback = runCatching {
            val response = GeminiAiFactory.generateContent(
                contents = listOf(com.example.sharkmentor.core.ai.GeminiContent.userText(prompt)),
                systemPrompt = "Yalnızca istenen film, dizi veya karaktere ait 5 ile 10 arasında kısa Türkçe motivasyon sözünü her satırda tek cümle olacak şekilde döndür.",
                taskType = com.example.sharkmentor.core.ai.AiTaskType.LIGHT_INTERACTION,
                generationConfig = com.example.sharkmentor.core.ai.GeminiGenerationConfig(
                    temperature = 0.7f,
                    maxOutputTokens = 1024
                )
            )
            parsePlainLinesToQuotes(response.text)
        }
        if (proxyFallback.isSuccess) {
            val quotes = proxyFallback.getOrThrow().take(MAX_AI_QUOTES)
            if (quotes.size >= MIN_AI_QUOTES) {
                saveQuotesAndActivate(cleanTopic, quotes)
                return@withContext Result.success(quotes)
            }
            if (quotes.size > bestPartialQuotes.size) {
                bestPartialQuotes = quotes
            }
        } else {
            lastError = proxyFallback.exceptionOrNull() ?: lastError
        }

        if (bestPartialQuotes.isNotEmpty()) {
            saveQuotesAndActivate(cleanTopic, bestPartialQuotes)
            return@withContext Result.success(bestPartialQuotes)
        }

        val friendlyMessage = when {
            lastError?.message?.contains("Unable to resolve host", ignoreCase = true) == true ||
                lastError?.message?.contains("Failed to connect", ignoreCase = true) == true ||
                lastError?.message?.contains("timeout", ignoreCase = true) == true ->
                "İnternet bağlantısı sağlanamadığı için motivasyon sözleri alınamadı."
            else -> "Motivasyon sözleri alınamadı. Bağlantınızı kontrol edip tekrar deneyin."
        }
        Result.failure(IllegalStateException(friendlyMessage, lastError))
    }

    private fun saveQuotesAndActivate(topic: String, quotes: List<String>) {
        prefs.edit().apply {
            putString(KEY_AI_QUOTES_TOPIC, topic)
            writeStringList(this, KEY_AI_QUOTES, quotes)
            putString(KEY_TEXT_SOURCE, TopBarTextSource.AI_QUOTES.name)
        }.apply()

        _settings.value = _settings.value.copy(
            aiQuotesTopic = topic,
            aiQuotes = quotes,
            textSource = TopBarTextSource.AI_QUOTES
        )
    }

    private fun requestQuotesFromGemini(
        apiKey: String,
        endpoint: com.example.sharkmentor.core.ai.GeminiEndpointType,
        model: String,
        prompt: String,
        useGoogleSearch: Boolean
    ): Result<List<String>> = runCatching {
        val url = when (endpoint) {
            com.example.sharkmentor.core.ai.GeminiEndpointType.VERTEX_AI_EXPRESS ->
                "https://aiplatform.googleapis.com/v1/publishers/google/models/$model:generateContent?key=$apiKey"
            else ->
                "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
        }
        val bodyJson = JSONObject().apply {
            put(
                "contents",
                JSONArray().put(
                    JSONObject().apply {
                        put("role", "user")
                        put(
                            "parts",
                            JSONArray().put(JSONObject().put("text", prompt))
                        )
                    }
                )
            )
            if (useGoogleSearch) {
                val searchKey = if (endpoint == com.example.sharkmentor.core.ai.GeminiEndpointType.VERTEX_AI_EXPRESS) {
                    "googleSearch"
                } else {
                    "google_search"
                }
                put(
                    "tools",
                    JSONArray().put(JSONObject().put(searchKey, JSONObject()))
                )
            }
            put(
                "generationConfig",
                JSONObject().apply {
                    put("temperature", 0.7)
                    put("maxOutputTokens", 1024)
                }
            )
        }

        val request = Request.Builder()
            .url(url)
            .post(bodyJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        httpClient.newCall(request).execute().use { response ->
            val responseText = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException("HTTP ${response.code}: ${responseText.take(240)}")
            }
            val parsed = parseQuotesFromResponse(responseText)
            if (parsed.isEmpty()) {
                throw IllegalStateException("Boş yanıt alındı.")
            }
            parsed
        }
    }

    private fun parsePlainLinesToQuotes(combined: String): List<String> {
        return combined
            .lines()
            .map { line ->
                val stripped = line.trim()
                    .replace(Regex("^\\d+[.)\\-:\\s]+"), "")
                    .replace(Regex("^[-•*]\\s*"), "")
                    .trim('"', '“', '”', '\'', ' ')
                capitalizeTurkishSentence(stripped)
            }
            .filter { it.isNotBlank() && it.length >= 4 }
            .distinct()
            .take(MAX_AI_QUOTES)
    }

    private fun parseQuotesFromResponse(responseText: String): List<String> {
        val root = JSONObject(responseText)
        val candidates = root.optJSONArray("candidates") ?: return emptyList()
        if (candidates.length() == 0) return emptyList()
        val parts = candidates.getJSONObject(0)
            .optJSONObject("content")
            ?.optJSONArray("parts") ?: return emptyList()

        val combined = buildString {
            for (i in 0 until parts.length()) {
                val text = parts.optJSONObject(i)?.optString("text", "").orEmpty()
                if (text.isNotBlank()) {
                    if (isNotEmpty()) append('\n')
                    append(text)
                }
            }
        }

        return parsePlainLinesToQuotes(combined)
    }

    private fun loadModuleOrder(): List<TopBarModule> {
        val raw = prefs.getString(KEY_MODULE_ORDER, null) ?: return TopBarModule.DEFAULT_ORDER
        val parsed = raw.split(",").mapNotNull { name ->
            TopBarModule.entries.firstOrNull { it.name == name.trim() }
        }.distinct()
        val missing = TopBarModule.DEFAULT_ORDER.filter { it !in parsed }
        return (parsed + missing).ifEmpty { TopBarModule.DEFAULT_ORDER }
    }

    private fun loadHiddenModules(): Set<TopBarModule> {
        val raw = prefs.getString(KEY_HIDDEN_MODULES, null) ?: return emptySet()
        return raw.split(",").mapNotNull { name ->
            TopBarModule.entries.firstOrNull { it.name == name.trim() }
        }.toSet()
    }

    private fun loadModuleSizes(): Map<TopBarModule, TopBarModuleSize> {
        val raw = prefs.getString(KEY_MODULE_SIZES, null) ?: return emptyMap()
        val out = mutableMapOf<TopBarModule, TopBarModuleSize>()
        raw.split(",").forEach { entry ->
            val parts = entry.split(":")
            if (parts.size == 2) {
                val mod = TopBarModule.entries.firstOrNull { it.name == parts[0].trim() }
                val sz = TopBarModuleSize.entries.firstOrNull { it.name == parts[1].trim() }
                if (mod != null && sz != null) out[mod] = sz
            }
        }
        return out
    }

    fun setModuleVisible(module: TopBarModule, visible: Boolean) {
        val next = _settings.value.hiddenModules.toMutableSet()
        if (visible) next.remove(module) else next.add(module)
        prefs.edit().putString(KEY_HIDDEN_MODULES, next.joinToString(",") { it.name }).apply()
        _settings.value = _settings.value.copy(hiddenModules = next)
    }

    fun moveModule(module: TopBarModule, delta: Int) {
        val list = _settings.value.moduleOrder.toMutableList()
        val idx = list.indexOf(module)
        if (idx < 0) return
        val target = (idx + delta).coerceIn(0, list.lastIndex)
        if (target == idx) return
        list.removeAt(idx)
        list.add(target, module)
        setModuleOrder(list)
    }

    fun setModuleOrder(order: List<TopBarModule>) {
        val normalized = (order.distinct() + TopBarModule.DEFAULT_ORDER.filter { it !in order })
        prefs.edit().putString(KEY_MODULE_ORDER, normalized.joinToString(",") { it.name }).apply()
        _settings.value = _settings.value.copy(moduleOrder = normalized)
    }

    fun setModuleSize(module: TopBarModule, size: TopBarModuleSize) {
        val next = _settings.value.moduleSizes.toMutableMap()
        next[module] = size
        val encoded = next.entries.joinToString(",") { "${it.key.name}:${it.value.name}" }
        prefs.edit().putString(KEY_MODULE_SIZES, encoded).apply()
        _settings.value = _settings.value.copy(moduleSizes = next)
    }

    fun resetTopBarLayout() {
        prefs.edit()
            .remove(KEY_MODULE_ORDER)
            .remove(KEY_HIDDEN_MODULES)
            .remove(KEY_MODULE_SIZES)
            .apply()
        _settings.value = _settings.value.copy(
            moduleOrder = TopBarModule.DEFAULT_ORDER,
            hiddenModules = emptySet(),
            moduleSizes = emptyMap()
        )
    }

    companion object {
        private const val PREFS_NAME = "shark_topbar_prefs"
        private const val KEY_TEXT_SOURCE = "text_source"
        private const val KEY_ANIMATION_TYPE = "animation_type"
        private const val KEY_FONT_OPTION = "font_option"
        private const val KEY_REFRESH_INTERVAL = "refresh_interval"
        private const val KEY_CUSTOM_COLOR = "custom_text_color_argb"
        private const val KEY_CUSTOM_TEXTS = "custom_texts"
        private const val KEY_AI_QUOTES_TOPIC = "ai_quotes_topic"
        private const val KEY_AI_QUOTES = "ai_quotes"
        private const val KEY_HIDE_BUBBLES = "hide_bubbles"
        private const val KEY_HIDE_TOP_BAR_MENU = "hide_top_bar_menu"
        private const val KEY_HIDE_DURING_FOCUS = "hide_during_focus"
        private const val KEY_IN_APP_SUGGESTIONS = "in_app_suggestions_enabled"
        private const val KEY_HYDRATION_REMINDER = "hydration_reminder_enabled"
        private const val KEY_HYDRATION_INTERVAL_MIN = "hydration_interval_minutes"
        private const val KEY_POSTURE_REMINDER = "posture_reminder_enabled"
        private const val KEY_POSTURE_INTERVAL_MIN = "posture_interval_minutes"
        private const val KEY_MODULE_ORDER = "topbar_module_order"
        private const val KEY_HIDDEN_MODULES = "topbar_hidden_modules"
        private const val KEY_MODULE_SIZES = "topbar_module_sizes"

        const val MAX_CUSTOM_TEXTS = 10
        const val MIN_AI_QUOTES = 5
        const val MAX_AI_QUOTES = 10

        @Volatile
        private var INSTANCE: TopBarPreferences? = null

        fun getInstance(context: Context): TopBarPreferences {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: TopBarPreferences(context).also { INSTANCE = it }
            }
        }
    }
}
