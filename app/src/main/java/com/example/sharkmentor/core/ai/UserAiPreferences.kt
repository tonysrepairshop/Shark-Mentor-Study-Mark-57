package com.example.sharkmentor.core.ai

import android.content.Context
import android.content.SharedPreferences
import com.example.sharkmentor.BuildConfig
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class GeminiModelOption(
    val id: String,
    val displayName: String,
    val description: String = ""
)

typealias AiModelOption = GeminiModelOption

enum class AiProviderChoice(val id: String, val label: String, val shortLabel: String) {
    AUTO("auto", "Otomatik", "Otomatik"),
    GEMINI("gemini", "Gemini", "Gemini"),
    DEEPSEEK("deepseek", "DeepSeek", "DeepSeek"),
    KIMI("kimi", "Kimi", "Kimi"),
    CLAUDE("claude", "Claude", "Claude"),
    OPENAI("openai", "GPT (OpenAI)", "GPT"),
    GROK("grok", "Grok (xAI)", "Grok");

    companion object {
        fun fromId(raw: String?): AiProviderChoice =
            entries.firstOrNull {
                it.id.equals(raw, ignoreCase = true) || it.name.equals(raw, ignoreCase = true)
            } ?: AUTO
    }
}

data class UserAiConfigState(
    val customGeminiApiKey: String = "",
    val selectedUserGeminiModel: String = UserAiPreferences.BUILTIN_DEFAULT_MODEL,
    val cachedCustomModels: List<String> = emptyList()
) {
    val hasCustomKey: Boolean
        get() = UserAiPreferences.parseGeminiKeyList(customGeminiApiKey).isNotEmpty()

    val effectiveModel: String
        get() = if (hasCustomKey) {
            UserAiPreferences.sanitizeModelId(selectedUserGeminiModel)
                .ifBlank { UserAiPreferences.BUILTIN_DEFAULT_MODEL }
        } else {
            UserAiPreferences.BUILTIN_DEFAULT_MODEL
        }
}

/**
 * SharedPreferences-backed singleton holding user-supplied AI API keys,
 * discovered/selected models for all 6 supported providers
 * (Gemini, DeepSeek, Kimi, Claude, OpenAI GPT, xAI Grok), and preferred AI provider.
 *
 * Safe to access without prior [init] on pure-JVM unit tests (falls back to in-memory state
 * and Gson serialization with zero `org.json` dependency).
 */
object UserAiPreferences {

    const val BUILTIN_DEFAULT_MODEL = "gemini-3.6-flash"
    const val STANDARD_GEMINI_MODEL = BUILTIN_DEFAULT_MODEL
    const val CONTROLLED_404_FALLBACK_MODEL = "gemini-3.5-flash-lite"
    val STANDARD_ALLOWED_MODELS = listOf(BUILTIN_DEFAULT_MODEL, CONTROLLED_404_FALLBACK_MODEL)

    const val DEFAULT_DEEPSEEK_MODEL = "deepseek-chat"
    const val DEFAULT_KIMI_MODEL = "moonshot-v1-8k"
    const val DEFAULT_CLAUDE_MODEL = "claude-sonnet-4-5"
    const val DEFAULT_OPENAI_MODEL = "gpt-4o-mini"
    const val DEFAULT_GROK_MODEL = "grok-3-mini"

    val KNOWN_VERTEX_GEMINI_MODELS = listOf(
        "gemini-3.6-flash"
    )

    private const val PREFS_NAME = "sharkmentor_user_ai_prefs"

    private const val KEY_GEMINI_KEYS = "user_gemini_keys"
    private const val KEY_LEGACY_CUSTOM_GEMINI_KEY = "custom_gemini_api_key"
    private const val KEY_SELECTED_GEMINI_MODEL = "selected_user_gemini_model"
    private const val KEY_DISCOVERED_GEMINI_MODELS = "discovered_gemini_models"
    private const val KEY_LEGACY_CACHED_MODELS_JSON = "cached_custom_gemini_models_json"

    private const val KEY_DEEPSEEK_KEY = "user_deepseek_key"
    private const val KEY_SELECTED_DEEPSEEK_MODEL = "selected_deepseek_model"
    private const val KEY_DISCOVERED_DEEPSEEK_MODELS = "discovered_deepseek_models"

    private const val KEY_KIMI_KEY = "user_kimi_key"
    private const val KEY_SELECTED_KIMI_MODEL = "selected_kimi_model"
    private const val KEY_DISCOVERED_KIMI_MODELS = "discovered_kimi_models"

    private const val KEY_CLAUDE_KEY = "user_claude_key"
    private const val KEY_SELECTED_CLAUDE_MODEL = "selected_claude_model"
    private const val KEY_DISCOVERED_CLAUDE_MODELS = "discovered_claude_models"

    private const val KEY_OPENAI_KEY = "user_openai_key"
    private const val KEY_SELECTED_OPENAI_MODEL = "selected_openai_model"
    private const val KEY_DISCOVERED_OPENAI_MODELS = "discovered_openai_models"

    private const val KEY_GROK_KEY = "user_grok_key"
    private const val KEY_SELECTED_GROK_MODEL = "selected_grok_model"
    private const val KEY_DISCOVERED_GROK_MODELS = "discovered_grok_models"

    private const val KEY_AI_PROVIDER = "selected_ai_provider"

    // ── Mark 60: Modular Feature Toggles (Studio AI, Ad Blocker, Notes AI, Drawing AI) ──
    private const val KEY_VIDEO_STUDIO_AI_ENABLED = "video_studio_ai_enabled"
    private const val KEY_VIDEO_AD_BLOCKER_ENABLED = "video_ad_blocker_enabled"
    private const val KEY_NOTES_AI_ENABLED = "notes_ai_enabled"
    private const val KEY_DRAWING_AI_ENABLED = "drawing_ai_enabled"

    private val gson = Gson()
    private val stringListType = object : TypeToken<List<String>>() {}.type
    private val modelOptionListType = object : TypeToken<List<GeminiModelOption>>() {}.type

    @Volatile
    private var sharedPrefs: SharedPreferences? = null

    private val _configState = MutableStateFlow(UserAiConfigState())
    val configState: StateFlow<UserAiConfigState> = _configState.asStateFlow()

    // ── Gemini ───────────────────────────────────────────────────────────────
    private val _userGeminiKeys = MutableStateFlow("")
    val userGeminiKeys: StateFlow<String> = _userGeminiKeys.asStateFlow()

    private val _selectedUserGeminiModel = MutableStateFlow(BUILTIN_DEFAULT_MODEL)
    val selectedUserGeminiModel: StateFlow<String> = _selectedUserGeminiModel.asStateFlow()

    private val _discoveredGeminiModels = MutableStateFlow<List<GeminiModelOption>>(emptyList())
    val discoveredGeminiModels: StateFlow<List<GeminiModelOption>> = _discoveredGeminiModels.asStateFlow()

    // ── DeepSeek ─────────────────────────────────────────────────────────────
    private val _userDeepSeekKey = MutableStateFlow("")
    val userDeepSeekKey: StateFlow<String> = _userDeepSeekKey.asStateFlow()

    private val _selectedDeepSeekModel = MutableStateFlow(DEFAULT_DEEPSEEK_MODEL)
    val selectedDeepSeekModel: StateFlow<String> = _selectedDeepSeekModel.asStateFlow()

    private val _discoveredDeepSeekModels = MutableStateFlow<List<GeminiModelOption>>(emptyList())
    val discoveredDeepSeekModels: StateFlow<List<GeminiModelOption>> = _discoveredDeepSeekModels.asStateFlow()

    // ── Kimi (Moonshot) ──────────────────────────────────────────────────────
    private val _userKimiKey = MutableStateFlow("")
    val userKimiKey: StateFlow<String> = _userKimiKey.asStateFlow()

    private val _selectedKimiModel = MutableStateFlow(DEFAULT_KIMI_MODEL)
    val selectedKimiModel: StateFlow<String> = _selectedKimiModel.asStateFlow()

    private val _discoveredKimiModels = MutableStateFlow<List<GeminiModelOption>>(emptyList())
    val discoveredKimiModels: StateFlow<List<GeminiModelOption>> = _discoveredKimiModels.asStateFlow()

    // ── Claude (Anthropic) ───────────────────────────────────────────────────
    private val _userClaudeKey = MutableStateFlow("")
    val userClaudeKey: StateFlow<String> = _userClaudeKey.asStateFlow()

    private val _selectedClaudeModel = MutableStateFlow(DEFAULT_CLAUDE_MODEL)
    val selectedClaudeModel: StateFlow<String> = _selectedClaudeModel.asStateFlow()

    private val _discoveredClaudeModels = MutableStateFlow<List<GeminiModelOption>>(emptyList())
    val discoveredClaudeModels: StateFlow<List<GeminiModelOption>> = _discoveredClaudeModels.asStateFlow()

    // ── OpenAI (GPT) ─────────────────────────────────────────────────────────
    private val _userOpenAiKey = MutableStateFlow("")
    val userOpenAiKey: StateFlow<String> = _userOpenAiKey.asStateFlow()

    private val _selectedOpenAiModel = MutableStateFlow(DEFAULT_OPENAI_MODEL)
    val selectedOpenAiModel: StateFlow<String> = _selectedOpenAiModel.asStateFlow()

    private val _discoveredOpenAiModels = MutableStateFlow<List<GeminiModelOption>>(emptyList())
    val discoveredOpenAiModels: StateFlow<List<GeminiModelOption>> = _discoveredOpenAiModels.asStateFlow()

    // ── Grok (xAI) ───────────────────────────────────────────────────────────
    private val _userGrokKey = MutableStateFlow("")
    val userGrokKey: StateFlow<String> = _userGrokKey.asStateFlow()

    private val _selectedGrokModel = MutableStateFlow(DEFAULT_GROK_MODEL)
    val selectedGrokModel: StateFlow<String> = _selectedGrokModel.asStateFlow()

    private val _discoveredGrokModels = MutableStateFlow<List<GeminiModelOption>>(emptyList())
    val discoveredGrokModels: StateFlow<List<GeminiModelOption>> = _discoveredGrokModels.asStateFlow()

    // ── Selected Active Provider ─────────────────────────────────────────────
    private val _selectedAiProvider = MutableStateFlow(AiProviderChoice.AUTO)
    val selectedAiProvider: StateFlow<AiProviderChoice> = _selectedAiProvider.asStateFlow()

    // ── Modular AI & Video Studio Toggles ────────────────────────────────────
    private val _videoStudioAiEnabled = MutableStateFlow(true)
    val videoStudioAiEnabled: StateFlow<Boolean> = _videoStudioAiEnabled.asStateFlow()

    private val _videoAdBlockerEnabled = MutableStateFlow(true)
    val videoAdBlockerEnabled: StateFlow<Boolean> = _videoAdBlockerEnabled.asStateFlow()

    private val _notesAiEnabled = MutableStateFlow(true)
    val notesAiEnabled: StateFlow<Boolean> = _notesAiEnabled.asStateFlow()

    private val _drawingAiEnabled = MutableStateFlow(true)
    val drawingAiEnabled: StateFlow<Boolean> = _drawingAiEnabled.asStateFlow()

    @Synchronized
    fun init(context: Context) {
        if (sharedPrefs != null) return
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sharedPrefs = prefs

        val savedGeminiRaw = prefs.getString(KEY_GEMINI_KEYS, "").orEmpty()
            .ifBlank { prefs.getString(KEY_LEGACY_CUSTOM_GEMINI_KEY, "").orEmpty() }
        val cleanGeminiRaw = sanitizeRawGeminiKeys(savedGeminiRaw)
        val hasGeminiKey = parseGeminiKeyList(cleanGeminiRaw).isNotEmpty()

        val savedGeminiModel = sanitizeProviderModelId(
            prefs.getString(KEY_SELECTED_GEMINI_MODEL, BUILTIN_DEFAULT_MODEL),
            BUILTIN_DEFAULT_MODEL
        )
        val effectiveGeminiModel = if (hasGeminiKey) savedGeminiModel else BUILTIN_DEFAULT_MODEL

        val decodedGeminiOptions = decodeModelOptions(
            prefs.getString(KEY_DISCOVERED_GEMINI_MODELS, "").orEmpty()
        ).ifEmpty {
            parseCachedModelIds(prefs.getString(KEY_LEGACY_CACHED_MODELS_JSON, null))
                .map { GeminiModelOption(id = it, displayName = it) }
        }
        val effectiveGeminiOptions = if (hasGeminiKey) decodedGeminiOptions else emptyList()

        _userGeminiKeys.value = cleanGeminiRaw
        _selectedUserGeminiModel.value = effectiveGeminiModel
        _discoveredGeminiModels.value = effectiveGeminiOptions

        _configState.value = UserAiConfigState(
            customGeminiApiKey = cleanGeminiRaw,
            selectedUserGeminiModel = effectiveGeminiModel,
            cachedCustomModels = effectiveGeminiOptions.map { it.id }
        )

        val savedDeepSeekKey = sanitizeKey(prefs.getString(KEY_DEEPSEEK_KEY, ""))
        val deepSeekAllowed = savedDeepSeekKey.isNotBlank() || hasBuiltInDeepSeekKey()
        _userDeepSeekKey.value = savedDeepSeekKey
        _selectedDeepSeekModel.value = if (deepSeekAllowed) {
            sanitizeProviderModelId(prefs.getString(KEY_SELECTED_DEEPSEEK_MODEL, DEFAULT_DEEPSEEK_MODEL), DEFAULT_DEEPSEEK_MODEL)
        } else {
            DEFAULT_DEEPSEEK_MODEL
        }
        _discoveredDeepSeekModels.value = if (deepSeekAllowed) {
            decodeModelOptions(prefs.getString(KEY_DISCOVERED_DEEPSEEK_MODELS, "").orEmpty())
        } else {
            emptyList()
        }

        val savedKimiKey = sanitizeKey(prefs.getString(KEY_KIMI_KEY, ""))
        val kimiAllowed = savedKimiKey.isNotBlank() || hasBuiltInKimiKey()
        _userKimiKey.value = savedKimiKey
        _selectedKimiModel.value = if (kimiAllowed) {
            sanitizeProviderModelId(prefs.getString(KEY_SELECTED_KIMI_MODEL, DEFAULT_KIMI_MODEL), DEFAULT_KIMI_MODEL)
        } else {
            DEFAULT_KIMI_MODEL
        }
        _discoveredKimiModels.value = if (kimiAllowed) {
            decodeModelOptions(prefs.getString(KEY_DISCOVERED_KIMI_MODELS, "").orEmpty())
        } else {
            emptyList()
        }

        _userClaudeKey.value = sanitizeKey(prefs.getString(KEY_CLAUDE_KEY, ""))
        _selectedClaudeModel.value = sanitizeProviderModelId(
            prefs.getString(KEY_SELECTED_CLAUDE_MODEL, DEFAULT_CLAUDE_MODEL),
            DEFAULT_CLAUDE_MODEL
        )
        _discoveredClaudeModels.value = decodeModelOptions(
            prefs.getString(KEY_DISCOVERED_CLAUDE_MODELS, "").orEmpty()
        )

        _userOpenAiKey.value = sanitizeKey(prefs.getString(KEY_OPENAI_KEY, ""))
        _selectedOpenAiModel.value = sanitizeProviderModelId(
            prefs.getString(KEY_SELECTED_OPENAI_MODEL, DEFAULT_OPENAI_MODEL),
            DEFAULT_OPENAI_MODEL
        )
        _discoveredOpenAiModels.value = decodeModelOptions(
            prefs.getString(KEY_DISCOVERED_OPENAI_MODELS, "").orEmpty()
        )

        _userGrokKey.value = sanitizeKey(prefs.getString(KEY_GROK_KEY, ""))
        _selectedGrokModel.value = sanitizeProviderModelId(
            prefs.getString(KEY_SELECTED_GROK_MODEL, DEFAULT_GROK_MODEL),
            DEFAULT_GROK_MODEL
        )
        _discoveredGrokModels.value = decodeModelOptions(
            prefs.getString(KEY_DISCOVERED_GROK_MODELS, "").orEmpty()
        )

        _selectedAiProvider.value = AiProviderChoice.fromId(
            prefs.getString(KEY_AI_PROVIDER, AiProviderChoice.AUTO.id)
        )

        _videoStudioAiEnabled.value = prefs.getBoolean(KEY_VIDEO_STUDIO_AI_ENABLED, true)
        _videoAdBlockerEnabled.value = prefs.getBoolean(KEY_VIDEO_AD_BLOCKER_ENABLED, true)
        _notesAiEnabled.value = prefs.getBoolean(KEY_NOTES_AI_ENABLED, true)
        _drawingAiEnabled.value = prefs.getBoolean(KEY_DRAWING_AI_ENABLED, true)
    }

    fun initialize(context: Context): UserAiPreferences {
        init(context)
        return this
    }

    fun getInstance(context: Context): UserAiPreferences = initialize(context)

    fun getOrNull(context: Context? = null): UserAiPreferences? {
        if (context != null) {
            init(context)
            return this
        }
        if (sharedPrefs != null) {
            return this
        }
        val resolvedApp = runCatching {
            Class.forName("android.app.ActivityThread")
                .getMethod("currentApplication")
                .invoke(null) as? Context
        }.getOrNull()
        if (resolvedApp != null) {
            init(resolvedApp)
        }
        return this
    }

    // ── Sanitizers & Key Parsers ─────────────────────────────────────────────

    fun sanitizeKey(raw: String?): String {
        return raw.orEmpty()
            .replace("\u200B", "")
            .replace("\uFEFF", "")
            .replace("\u00A0", " ")
            .trim()
            .removePrefix("Bearer ")
            .removePrefix("bearer ")
            .replace("\r", "")
            .replace("\n", "")
            .replace("\"", "")
            .replace("'", "")
            .trim()
    }

    fun sanitizeModelId(raw: String?): String {
        return raw.orEmpty()
            .trim()
            .removePrefix("publishers/google/models/")
            .removePrefix("models/")
            .removePrefix("/")
            .trim()
    }

    fun sanitizeProviderModelId(raw: String?, fallback: String): String {
        return sanitizeModelId(raw).ifBlank { fallback }
    }

    fun parseGeminiKeyList(raw: String?): List<String> {
        return raw.orEmpty()
            .split(",", "\n", "\r", ";", " ", "\t")
            .map { sanitizeKey(it) }
            .filter { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
            .distinct()
    }

    fun sanitizeRawGeminiKeys(raw: String?): String =
        parseGeminiKeyList(raw).joinToString(", ")

    // ── Opus Custom Config API ───────────────────────────────────────────────

    fun hasCustomKey(): Boolean = _configState.value.hasCustomKey

    fun hasCustomUserGeminiKeys(context: Context? = null): Boolean {
        context?.let { init(it) }
        return hasCustomKey()
    }

    fun getCustomApiKey(): String = _configState.value.customGeminiApiKey

    fun getSelectedUserGeminiModel(): String = _configState.value.effectiveModel

    fun getCachedCustomModels(): List<String> = _configState.value.cachedCustomModels

    fun resolveEffectiveModel(requestedModel: String? = null): String {
        val current = _configState.value
        return if (current.hasCustomKey) current.effectiveModel else BUILTIN_DEFAULT_MODEL
    }

    fun getEffectiveSelectableModels(): List<String> {
        val current = _configState.value
        if (!current.hasCustomKey) {
            return listOf(BUILTIN_DEFAULT_MODEL)
        }
        val combined = buildList {
            addAll(current.cachedCustomModels)
            if (isEmpty()) {
                addAll(KNOWN_VERTEX_GEMINI_MODELS)
            }
            val selected = current.selectedUserGeminiModel
            if (selected.isNotBlank() && !contains(selected)) {
                add(0, selected)
            }
        }
            .map { sanitizeModelId(it) }
            .filter { it.isNotBlank() }
            .distinct()

        return combined.ifEmpty { KNOWN_VERTEX_GEMINI_MODELS }
    }

    // ── Multi-Provider Getters ───────────────────────────────────────────────

    fun getUserGeminiKeysRaw(): String = _userGeminiKeys.value
    fun getParsedUserGeminiKeys(): List<String> = parseGeminiKeyList(_userGeminiKeys.value)
    fun getDiscoveredGeminiModels(): List<GeminiModelOption> = _discoveredGeminiModels.value

    fun getUserDeepSeekKey(): String = sanitizeKey(_userDeepSeekKey.value)
    fun getSelectedDeepSeekModel(): String = _selectedDeepSeekModel.value
    fun getDiscoveredDeepSeekModels(): List<GeminiModelOption> = _discoveredDeepSeekModels.value

    fun getUserKimiKey(): String = sanitizeKey(_userKimiKey.value)
    fun getSelectedKimiModel(): String = _selectedKimiModel.value
    fun getDiscoveredKimiModels(): List<GeminiModelOption> = _discoveredKimiModels.value

    fun getUserClaudeKey(): String = sanitizeKey(_userClaudeKey.value)
    fun getSelectedClaudeModel(): String = _selectedClaudeModel.value
    fun getDiscoveredClaudeModels(): List<GeminiModelOption> = _discoveredClaudeModels.value

    fun getUserOpenAiKey(): String = sanitizeKey(_userOpenAiKey.value)
    fun getSelectedOpenAiModel(): String = _selectedOpenAiModel.value
    fun getDiscoveredOpenAiModels(): List<GeminiModelOption> = _discoveredOpenAiModels.value

    fun getUserGrokKey(): String = sanitizeKey(_userGrokKey.value)
    fun getSelectedGrokModel(): String = _selectedGrokModel.value
    fun getDiscoveredGrokModels(): List<GeminiModelOption> = _discoveredGrokModels.value

    private fun hasUserKeyForProvider(provider: AiProviderChoice): Boolean = when (provider) {
        AiProviderChoice.AUTO, AiProviderChoice.GEMINI -> true
        AiProviderChoice.DEEPSEEK -> getUserDeepSeekKey().isNotBlank()
        AiProviderChoice.KIMI -> getUserKimiKey().isNotBlank()
        AiProviderChoice.CLAUDE -> getUserClaudeKey().isNotBlank()
        AiProviderChoice.OPENAI -> getUserOpenAiKey().isNotBlank()
        AiProviderChoice.GROK -> getUserGrokKey().isNotBlank()
    }

    fun getSelectedProvider(): AiProviderChoice {
        val selected = _selectedAiProvider.value
        return if (hasUserKeyForProvider(selected)) selected else AiProviderChoice.AUTO
    }

    fun resolveEffectiveGeminiModel(forKey: String? = null): String {
        val userKeys = getParsedUserGeminiKeys()
        if (userKeys.isEmpty()) return BUILTIN_DEFAULT_MODEL
        if (forKey != null) {
            val cleanedForKey = sanitizeKey(forKey)
            if (userKeys.none { it == cleanedForKey }) {
                return BUILTIN_DEFAULT_MODEL
            }
        }
        return _configState.value.effectiveModel
    }

    fun resolveEffectiveGeminiModel(context: Context?, forKey: String? = null): String {
        if (context != null) init(context)
        return resolveEffectiveGeminiModel(forKey)
    }

    fun getActiveProviderAndModel(): Pair<AiProviderChoice, String> {
        return when (getSelectedProvider()) {
            AiProviderChoice.GEMINI -> AiProviderChoice.GEMINI to resolveEffectiveGeminiModel()
            AiProviderChoice.DEEPSEEK -> AiProviderChoice.DEEPSEEK to getSelectedDeepSeekModel()
            AiProviderChoice.KIMI -> AiProviderChoice.KIMI to getSelectedKimiModel()
            AiProviderChoice.CLAUDE -> AiProviderChoice.CLAUDE to getSelectedClaudeModel()
            AiProviderChoice.OPENAI -> AiProviderChoice.OPENAI to getSelectedOpenAiModel()
            AiProviderChoice.GROK -> AiProviderChoice.GROK to getSelectedGrokModel()
            AiProviderChoice.AUTO -> when {
                getParsedUserGeminiKeys().isNotEmpty() ->
                    AiProviderChoice.GEMINI to resolveEffectiveGeminiModel()
                getUserClaudeKey().isNotBlank() ->
                    AiProviderChoice.CLAUDE to getSelectedClaudeModel()
                getUserOpenAiKey().isNotBlank() ->
                    AiProviderChoice.OPENAI to getSelectedOpenAiModel()
                getUserGrokKey().isNotBlank() ->
                    AiProviderChoice.GROK to getSelectedGrokModel()
                getUserDeepSeekKey().isNotBlank() ->
                    AiProviderChoice.DEEPSEEK to getSelectedDeepSeekModel()
                getUserKimiKey().isNotBlank() ->
                    AiProviderChoice.KIMI to getSelectedKimiModel()
                else ->
                    AiProviderChoice.GEMINI to BUILTIN_DEFAULT_MODEL
            }
        }
    }

    fun getActiveDisplayText(): String {
        val (provider, model) = getActiveProviderAndModel()
        return "${provider.shortLabel} • $model"
    }

    fun getSelectedModelFor(provider: AiProviderChoice): String = when (provider) {
        AiProviderChoice.GEMINI, AiProviderChoice.AUTO -> resolveEffectiveGeminiModel()
        AiProviderChoice.DEEPSEEK -> getSelectedDeepSeekModel()
        AiProviderChoice.KIMI -> getSelectedKimiModel()
        AiProviderChoice.CLAUDE -> getSelectedClaudeModel()
        AiProviderChoice.OPENAI -> getSelectedOpenAiModel()
        AiProviderChoice.GROK -> getSelectedGrokModel()
    }

    // ── Mutators ─────────────────────────────────────────────────────────────

    @Synchronized
    fun setCustomGeminiConfig(apiKey: String, selectedModel: String? = null) {
        val cleanKeys = sanitizeRawGeminiKeys(apiKey)
        if (parseGeminiKeyList(cleanKeys).isEmpty()) {
            clearCustomGeminiConfig()
            return
        }

        val current = _configState.value
        val keyChanged = cleanKeys != current.customGeminiApiKey
        val nextModel = sanitizeModelId(selectedModel)
            .ifBlank { if (keyChanged) BUILTIN_DEFAULT_MODEL else current.selectedUserGeminiModel }
            .ifBlank { BUILTIN_DEFAULT_MODEL }

        val nextCachedIds = if (keyChanged) emptyList() else current.cachedCustomModels
        val nextOptions = if (keyChanged) emptyList() else _discoveredGeminiModels.value

        val nextState = UserAiConfigState(
            customGeminiApiKey = cleanKeys,
            selectedUserGeminiModel = nextModel,
            cachedCustomModels = nextCachedIds
        )
        _configState.value = nextState
        _userGeminiKeys.value = cleanKeys
        _selectedUserGeminiModel.value = nextModel
        _discoveredGeminiModels.value = nextOptions

        sharedPrefs?.edit()
            ?.putString(KEY_GEMINI_KEYS, cleanKeys)
            ?.putString(KEY_LEGACY_CUSTOM_GEMINI_KEY, cleanKeys)
            ?.putString(KEY_SELECTED_GEMINI_MODEL, nextModel)
            ?.putString(KEY_DISCOVERED_GEMINI_MODELS, gson.toJson(nextOptions))
            ?.putString(KEY_LEGACY_CACHED_MODELS_JSON, gson.toJson(nextCachedIds))
            ?.apply()

        ApiKeyRotator.invalidateAndReset()
    }

    @Synchronized
    fun setUserGeminiKeys(raw: String) {
        val cleaned = sanitizeRawGeminiKeys(raw)
        if (cleaned.isBlank()) {
            clearCustomGeminiConfig()
        } else {
            val current = _configState.value
            val keyChanged = cleaned != current.customGeminiApiKey
            val nextModel = if (keyChanged) BUILTIN_DEFAULT_MODEL else current.effectiveModel
            val nextCachedIds = if (keyChanged) emptyList() else current.cachedCustomModels
            val nextOptions = if (keyChanged) emptyList() else _discoveredGeminiModels.value

            _configState.value = UserAiConfigState(
                customGeminiApiKey = cleaned,
                selectedUserGeminiModel = nextModel,
                cachedCustomModels = nextCachedIds
            )
            _userGeminiKeys.value = cleaned
            _selectedUserGeminiModel.value = nextModel
            _discoveredGeminiModels.value = nextOptions

            sharedPrefs?.edit()
                ?.putString(KEY_GEMINI_KEYS, cleaned)
                ?.putString(KEY_LEGACY_CUSTOM_GEMINI_KEY, cleaned)
                ?.putString(KEY_SELECTED_GEMINI_MODEL, nextModel)
                ?.putString(KEY_DISCOVERED_GEMINI_MODELS, gson.toJson(nextOptions))
                ?.putString(KEY_LEGACY_CACHED_MODELS_JSON, gson.toJson(nextCachedIds))
                ?.apply()

            ApiKeyRotator.invalidateAndReset()
        }
    }

    @Synchronized
    fun setSelectedUserGeminiModel(modelId: String) {
        val current = _configState.value
        val cleanModel = if (current.hasCustomKey) {
            sanitizeProviderModelId(modelId, BUILTIN_DEFAULT_MODEL)
        } else {
            BUILTIN_DEFAULT_MODEL
        }
        val nextState = current.copy(selectedUserGeminiModel = cleanModel)
        _configState.value = nextState
        _selectedUserGeminiModel.value = cleanModel
        sharedPrefs?.edit()
            ?.putString(KEY_SELECTED_GEMINI_MODEL, cleanModel)
            ?.apply()
    }

    @Synchronized
    fun setCachedModels(models: List<String>) {
        val current = _configState.value
        val cleanedIds = models
            .map { sanitizeModelId(it) }
            .filter { it.isNotBlank() }
            .distinct()

        val effectiveList = if (current.hasCustomKey && cleanedIds.isEmpty()) {
            KNOWN_VERTEX_GEMINI_MODELS
        } else {
            cleanedIds
        }
        val nextSelected = if (current.hasCustomKey) {
            if (current.selectedUserGeminiModel in effectiveList) {
                current.selectedUserGeminiModel
            } else {
                effectiveList.firstOrNull { it == BUILTIN_DEFAULT_MODEL }
                    ?: effectiveList.firstOrNull()
                    ?: BUILTIN_DEFAULT_MODEL
            }
        } else {
            BUILTIN_DEFAULT_MODEL
        }

        val options = effectiveList.map { GeminiModelOption(id = it, displayName = it) }
        val nextState = current.copy(
            selectedUserGeminiModel = nextSelected,
            cachedCustomModels = effectiveList
        )
        _configState.value = nextState
        _selectedUserGeminiModel.value = nextSelected
        _discoveredGeminiModels.value = options

        sharedPrefs?.edit()
            ?.putString(KEY_SELECTED_GEMINI_MODEL, nextSelected)
            ?.putString(KEY_DISCOVERED_GEMINI_MODELS, gson.toJson(options))
            ?.putString(KEY_LEGACY_CACHED_MODELS_JSON, gson.toJson(effectiveList))
            ?.apply()
    }

    @Synchronized
    fun setDiscoveredGeminiModels(models: List<GeminiModelOption>) {
        val distinctModels = cleanDistinctModels(models)
        val ids = distinctModels.map { it.id }
        _discoveredGeminiModels.value = distinctModels
        _configState.value = _configState.value.copy(cachedCustomModels = ids)
        sharedPrefs?.edit()
            ?.putString(KEY_DISCOVERED_GEMINI_MODELS, gson.toJson(distinctModels))
            ?.putString(KEY_LEGACY_CACHED_MODELS_JSON, gson.toJson(ids))
            ?.apply()
    }

    @Synchronized
    fun clearCustomGeminiConfig() {
        val nextState = UserAiConfigState(
            customGeminiApiKey = "",
            selectedUserGeminiModel = BUILTIN_DEFAULT_MODEL,
            cachedCustomModels = emptyList()
        )
        _configState.value = nextState
        _userGeminiKeys.value = ""
        _selectedUserGeminiModel.value = BUILTIN_DEFAULT_MODEL
        _discoveredGeminiModels.value = emptyList()

        sharedPrefs?.edit()
            ?.remove(KEY_GEMINI_KEYS)
            ?.remove(KEY_LEGACY_CUSTOM_GEMINI_KEY)
            ?.putString(KEY_SELECTED_GEMINI_MODEL, BUILTIN_DEFAULT_MODEL)
            ?.remove(KEY_DISCOVERED_GEMINI_MODELS)
            ?.remove(KEY_LEGACY_CACHED_MODELS_JSON)
            ?.apply()

        ApiKeyRotator.invalidateAndReset()
    }

    @Synchronized
    fun setSelectedAiProvider(provider: AiProviderChoice) {
        sharedPrefs?.edit()?.putString(KEY_AI_PROVIDER, provider.id)?.apply()
        _selectedAiProvider.value = provider
    }

    @Synchronized
    fun saveSettings(
        geminiKeys: String,
        deepSeekKey: String,
        kimiKey: String,
        claudeKey: String,
        openAiKey: String,
        provider: AiProviderChoice,
        selectedGeminiModel: String = _selectedUserGeminiModel.value,
        discoveredGeminiModels: List<GeminiModelOption>? = null,
        selectedDeepSeekModel: String = _selectedDeepSeekModel.value,
        discoveredDeepSeekModels: List<GeminiModelOption>? = null,
        selectedKimiModel: String = _selectedKimiModel.value,
        discoveredKimiModels: List<GeminiModelOption>? = null,
        selectedClaudeModel: String = _selectedClaudeModel.value,
        discoveredClaudeModels: List<GeminiModelOption>? = null,
        selectedOpenAiModel: String = _selectedOpenAiModel.value,
        discoveredOpenAiModels: List<GeminiModelOption>? = null,
        grokKey: String = _userGrokKey.value,
        selectedGrokModel: String = _selectedGrokModel.value,
        discoveredGrokModels: List<GeminiModelOption>? = null
    ) {
        val cleanGemini = sanitizeRawGeminiKeys(geminiKeys)
        val hasGeminiKey = parseGeminiKeyList(cleanGemini).isNotEmpty()
        val cleanDeepSeek = sanitizeKey(deepSeekKey)
        val cleanKimi = sanitizeKey(kimiKey)
        val cleanClaude = sanitizeKey(claudeKey)
        val cleanOpenAi = sanitizeKey(openAiKey)
        val cleanGrok = sanitizeKey(grokKey)

        val deepSeekAllowed = cleanDeepSeek.isNotBlank() || hasBuiltInDeepSeekKey()
        val kimiAllowed = cleanKimi.isNotBlank() || hasBuiltInKimiKey()

        val effGeminiModel = if (!hasGeminiKey) BUILTIN_DEFAULT_MODEL else sanitizeProviderModelId(selectedGeminiModel, BUILTIN_DEFAULT_MODEL)
        val effDeepSeekModel = if (!deepSeekAllowed) DEFAULT_DEEPSEEK_MODEL else sanitizeProviderModelId(selectedDeepSeekModel, DEFAULT_DEEPSEEK_MODEL)
        val effKimiModel = if (!kimiAllowed) DEFAULT_KIMI_MODEL else sanitizeProviderModelId(selectedKimiModel, DEFAULT_KIMI_MODEL)
        val effClaudeModel = if (cleanClaude.isBlank()) DEFAULT_CLAUDE_MODEL else sanitizeProviderModelId(selectedClaudeModel, DEFAULT_CLAUDE_MODEL)
        val effOpenAiModel = if (cleanOpenAi.isBlank()) DEFAULT_OPENAI_MODEL else sanitizeProviderModelId(selectedOpenAiModel, DEFAULT_OPENAI_MODEL)
        val effGrokModel = if (cleanGrok.isBlank()) DEFAULT_GROK_MODEL else sanitizeProviderModelId(selectedGrokModel, DEFAULT_GROK_MODEL)

        val effectiveProvider = when (provider) {
            AiProviderChoice.DEEPSEEK -> if (cleanDeepSeek.isNotBlank()) provider else AiProviderChoice.AUTO
            AiProviderChoice.KIMI -> if (cleanKimi.isNotBlank()) provider else AiProviderChoice.AUTO
            AiProviderChoice.CLAUDE -> if (cleanClaude.isNotBlank()) provider else AiProviderChoice.AUTO
            AiProviderChoice.OPENAI -> if (cleanOpenAi.isNotBlank()) provider else AiProviderChoice.AUTO
            AiProviderChoice.GROK -> if (cleanGrok.isNotBlank()) provider else AiProviderChoice.AUTO
            AiProviderChoice.GEMINI, AiProviderChoice.AUTO -> provider
        }

        val editor = sharedPrefs?.edit()
            ?.putString(KEY_GEMINI_KEYS, cleanGemini)
            ?.putString(KEY_LEGACY_CUSTOM_GEMINI_KEY, cleanGemini)
            ?.putString(KEY_SELECTED_GEMINI_MODEL, effGeminiModel)
            ?.putString(KEY_DEEPSEEK_KEY, cleanDeepSeek)
            ?.putString(KEY_SELECTED_DEEPSEEK_MODEL, effDeepSeekModel)
            ?.putString(KEY_KIMI_KEY, cleanKimi)
            ?.putString(KEY_SELECTED_KIMI_MODEL, effKimiModel)
            ?.putString(KEY_CLAUDE_KEY, cleanClaude)
            ?.putString(KEY_SELECTED_CLAUDE_MODEL, effClaudeModel)
            ?.putString(KEY_OPENAI_KEY, cleanOpenAi)
            ?.putString(KEY_SELECTED_OPENAI_MODEL, effOpenAiModel)
            ?.putString(KEY_GROK_KEY, cleanGrok)
            ?.putString(KEY_SELECTED_GROK_MODEL, effGrokModel)
            ?.putString(KEY_AI_PROVIDER, effectiveProvider.id)

        fun applyModelList(
            allowed: Boolean,
            list: List<GeminiModelOption>?,
            prefKey: String,
            flow: MutableStateFlow<List<GeminiModelOption>>
        ) {
            if (!allowed) {
                editor?.remove(prefKey)
                flow.value = emptyList()
            } else if (list != null) {
                val cleanList = cleanDistinctModels(list)
                editor?.putString(prefKey, gson.toJson(cleanList))
                flow.value = cleanList
            }
        }

        applyModelList(hasGeminiKey, discoveredGeminiModels, KEY_DISCOVERED_GEMINI_MODELS, _discoveredGeminiModels)
        applyModelList(deepSeekAllowed, discoveredDeepSeekModels, KEY_DISCOVERED_DEEPSEEK_MODELS, _discoveredDeepSeekModels)
        applyModelList(kimiAllowed, discoveredKimiModels, KEY_DISCOVERED_KIMI_MODELS, _discoveredKimiModels)
        applyModelList(cleanClaude.isNotBlank(), discoveredClaudeModels, KEY_DISCOVERED_CLAUDE_MODELS, _discoveredClaudeModels)
        applyModelList(cleanOpenAi.isNotBlank(), discoveredOpenAiModels, KEY_DISCOVERED_OPENAI_MODELS, _discoveredOpenAiModels)
        applyModelList(cleanGrok.isNotBlank(), discoveredGrokModels, KEY_DISCOVERED_GROK_MODELS, _discoveredGrokModels)

        val currentGeminiIds = _discoveredGeminiModels.value.map { it.id }
        if (!hasGeminiKey) {
            editor?.remove(KEY_LEGACY_CACHED_MODELS_JSON)
        } else {
            editor?.putString(KEY_LEGACY_CACHED_MODELS_JSON, gson.toJson(currentGeminiIds))
        }
        editor?.apply()

        _userGeminiKeys.value = cleanGemini
        _selectedUserGeminiModel.value = effGeminiModel
        _configState.value = UserAiConfigState(
            customGeminiApiKey = cleanGemini,
            selectedUserGeminiModel = effGeminiModel,
            cachedCustomModels = if (hasGeminiKey) currentGeminiIds else emptyList()
        )

        _userDeepSeekKey.value = cleanDeepSeek
        _selectedDeepSeekModel.value = effDeepSeekModel
        _userKimiKey.value = cleanKimi
        _selectedKimiModel.value = effKimiModel
        _userClaudeKey.value = cleanClaude
        _selectedClaudeModel.value = effClaudeModel
        _userOpenAiKey.value = cleanOpenAi
        _selectedOpenAiModel.value = effOpenAiModel
        _userGrokKey.value = cleanGrok
        _selectedGrokModel.value = effGrokModel
        _selectedAiProvider.value = effectiveProvider

        ApiKeyRotator.invalidateAndReset()
    }

    @Synchronized
    fun clearAllKeys() {
        sharedPrefs?.edit()
            ?.remove(KEY_GEMINI_KEYS)
            ?.remove(KEY_LEGACY_CUSTOM_GEMINI_KEY)
            ?.remove(KEY_SELECTED_GEMINI_MODEL)
            ?.remove(KEY_DISCOVERED_GEMINI_MODELS)
            ?.remove(KEY_LEGACY_CACHED_MODELS_JSON)
            ?.remove(KEY_DEEPSEEK_KEY)
            ?.remove(KEY_SELECTED_DEEPSEEK_MODEL)
            ?.remove(KEY_DISCOVERED_DEEPSEEK_MODELS)
            ?.remove(KEY_KIMI_KEY)
            ?.remove(KEY_SELECTED_KIMI_MODEL)
            ?.remove(KEY_DISCOVERED_KIMI_MODELS)
            ?.remove(KEY_CLAUDE_KEY)
            ?.remove(KEY_SELECTED_CLAUDE_MODEL)
            ?.remove(KEY_DISCOVERED_CLAUDE_MODELS)
            ?.remove(KEY_OPENAI_KEY)
            ?.remove(KEY_SELECTED_OPENAI_MODEL)
            ?.remove(KEY_DISCOVERED_OPENAI_MODELS)
            ?.remove(KEY_GROK_KEY)
            ?.remove(KEY_SELECTED_GROK_MODEL)
            ?.remove(KEY_DISCOVERED_GROK_MODELS)
            ?.remove(KEY_AI_PROVIDER)
            ?.apply()

        _configState.value = UserAiConfigState()
        _userGeminiKeys.value = ""
        _selectedUserGeminiModel.value = BUILTIN_DEFAULT_MODEL
        _discoveredGeminiModels.value = emptyList()
        _userDeepSeekKey.value = ""
        _selectedDeepSeekModel.value = DEFAULT_DEEPSEEK_MODEL
        _discoveredDeepSeekModels.value = emptyList()
        _userKimiKey.value = ""
        _selectedKimiModel.value = DEFAULT_KIMI_MODEL
        _discoveredKimiModels.value = emptyList()
        _userClaudeKey.value = ""
        _selectedClaudeModel.value = DEFAULT_CLAUDE_MODEL
        _discoveredClaudeModels.value = emptyList()
        _userOpenAiKey.value = ""
        _selectedOpenAiModel.value = DEFAULT_OPENAI_MODEL
        _discoveredOpenAiModels.value = emptyList()
        _userGrokKey.value = ""
        _selectedGrokModel.value = DEFAULT_GROK_MODEL
        _discoveredGrokModels.value = emptyList()
        _selectedAiProvider.value = AiProviderChoice.AUTO

        ApiKeyRotator.invalidateAndReset()
    }

    fun setVideoStudioAiEnabled(context: Context? = null, enabled: Boolean) {
        context?.let { init(it) }
        _videoStudioAiEnabled.value = enabled
        sharedPrefs?.edit()?.putBoolean(KEY_VIDEO_STUDIO_AI_ENABLED, enabled)?.apply()
    }

    fun setVideoAdBlockerEnabled(context: Context? = null, enabled: Boolean) {
        context?.let { init(it) }
        _videoAdBlockerEnabled.value = enabled
        sharedPrefs?.edit()?.putBoolean(KEY_VIDEO_AD_BLOCKER_ENABLED, enabled)?.apply()
    }

    fun setNotesAiEnabled(context: Context? = null, enabled: Boolean) {
        context?.let { init(it) }
        _notesAiEnabled.value = enabled
        sharedPrefs?.edit()?.putBoolean(KEY_NOTES_AI_ENABLED, enabled)?.apply()
    }

    fun setDrawingAiEnabled(context: Context? = null, enabled: Boolean) {
        context?.let { init(it) }
        _drawingAiEnabled.value = enabled
        sharedPrefs?.edit()?.putBoolean(KEY_DRAWING_AI_ENABLED, enabled)?.apply()
    }

    /** Only for pure-JVM unit tests to ensure isolated state between test cases. */
    @Synchronized
    internal fun resetForTesting() {
        sharedPrefs = null
        _configState.value = UserAiConfigState()
        _userGeminiKeys.value = ""
        _selectedUserGeminiModel.value = BUILTIN_DEFAULT_MODEL
        _discoveredGeminiModels.value = emptyList()
        _userDeepSeekKey.value = ""
        _selectedDeepSeekModel.value = DEFAULT_DEEPSEEK_MODEL
        _discoveredDeepSeekModels.value = emptyList()
        _userKimiKey.value = ""
        _selectedKimiModel.value = DEFAULT_KIMI_MODEL
        _discoveredKimiModels.value = emptyList()
        _userClaudeKey.value = ""
        _selectedClaudeModel.value = DEFAULT_CLAUDE_MODEL
        _discoveredClaudeModels.value = emptyList()
        _userOpenAiKey.value = ""
        _selectedOpenAiModel.value = DEFAULT_OPENAI_MODEL
        _discoveredOpenAiModels.value = emptyList()
        _userGrokKey.value = ""
        _selectedGrokModel.value = DEFAULT_GROK_MODEL
        _discoveredGrokModels.value = emptyList()
        _selectedAiProvider.value = AiProviderChoice.AUTO
        _videoStudioAiEnabled.value = true
        _videoAdBlockerEnabled.value = true
        _notesAiEnabled.value = true
        _drawingAiEnabled.value = true
        ApiKeyRotator.invalidateAndReset()
    }

    fun hasBuiltInDeepSeekKey(): Boolean =
        runCatching { sanitizeKey(BuildConfig.DEEPSEEK_API_KEY).isNotBlank() }.getOrDefault(false)

    fun hasBuiltInKimiKey(): Boolean =
        runCatching { sanitizeKey(BuildConfig.KIMI_API_KEY).isNotBlank() }.getOrDefault(false)

    private fun cleanDistinctModels(models: List<GeminiModelOption>): List<GeminiModelOption> {
        return models.mapNotNull { opt ->
            val cleanId = sanitizeModelId(opt.id)
            if (cleanId.isBlank()) null
            else opt.copy(id = cleanId, displayName = opt.displayName.ifBlank { cleanId })
        }.distinctBy { it.id }
    }

    private fun decodeModelOptions(raw: String): List<GeminiModelOption> {
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val parsed: List<GeminiModelOption>? = gson.fromJson(raw, modelOptionListType)
            cleanDistinctModels(parsed.orEmpty())
        }.getOrDefault(emptyList())
    }

    private fun parseCachedModelIds(json: String?): List<String> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val parsed: List<String>? = gson.fromJson(json, stringListType)
            parsed.orEmpty()
                .map { sanitizeModelId(it) }
                .filter { it.isNotBlank() }
                .distinct()
        }.getOrDefault(emptyList())
    }
}
