package com.example.sharkmentor.core.ai

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.Base64

enum class AiTaskType(
    val modelName: String,
    val defaultTemperature: Float,
    val defaultMaxOutputTokens: Int,
    val defaultTopP: Float
) {
    LIGHT_INTERACTION(
        modelName = GeminiAiFactory.SOLE_GEMINI_MODEL,
        defaultTemperature = 0.25f,
        defaultMaxOutputTokens = 4096,
        defaultTopP = 0.8f
    ),
    HEAVY_ANALYSIS(
        modelName = GeminiAiFactory.SOLE_GEMINI_MODEL,
        defaultTemperature = 0.45f,
        defaultMaxOutputTokens = 8192,
        defaultTopP = 0.9f
    );

    fun toGenerationConfig(): GeminiGenerationConfig = GeminiGenerationConfig(
        temperature = defaultTemperature,
        maxOutputTokens = defaultMaxOutputTokens,
        topP = defaultTopP
    )
}

data class GeminiSdkResult(
    val text: String,
    val functionCalls: List<GeminiFunctionCall> = emptyList(),
    val usedKeyIndex: Int = 0,
    val modelUsed: String = GeminiAiFactory.SOLE_GEMINI_MODEL
)

/**
 * Pure OkHttp + Gson Gemini execution & failover pipeline.
 *
 * - Standard mode (no custom user key) first routes through Firebase Cloud Functions `geminiProxy`
 *   using Firebase Auth ID token, and falls back to built-in keys if proxy is unavailable.
 * - Strict standard-mode model policy: primary `gemini-3.6-flash`, single fallback
 *   `gemini-3.5-flash-lite` (only on 404/NOT_FOUND, 429, 503 or timeout/unreachable).
 * - User-entered keys run the user's selected supported model directly against AI Studio / Vertex Express.
 */
object GeminiAiFactory {
    const val SOLE_GEMINI_MODEL = "gemini-3.6-flash"
    const val CONTROLLED_404_FALLBACK_MODEL = "gemini-3.5-flash-lite"
    val STANDARD_ALLOWED_MODELS = listOf(SOLE_GEMINI_MODEL, CONTROLLED_404_FALLBACK_MODEL)
    const val VISION_MODEL = SOLE_GEMINI_MODEL
    const val FAST_MODEL_3_6 = SOLE_GEMINI_MODEL
    const val FALLBACK_MODEL_3_6 = CONTROLLED_404_FALLBACK_MODEL

    internal val CUSTOM_GEMINI_FALLBACK_MODELS = listOf(
        "gemini-2.5-flash",
        "gemini-2.5-flash-lite",
        "gemini-2.0-flash"
    )
    private val workingModelByCustomKey = java.util.concurrent.ConcurrentHashMap<String, String>()

    internal fun clearCustomKeyModelCacheForTesting() {
        workingModelByCustomKey.clear()
    }

    internal fun rememberWorkingCustomKeyModel(
        apiKey: String,
        modelName: String,
        configuredModel: String? = null
    ) {
        val cleanKey = GeminiRestClient.sanitizeApiKey(apiKey)
        val cleanModel = GeminiRestClient.sanitizeModelName(modelName)
        if (cleanKey.isNotBlank() && cleanModel.isNotBlank()) {
            val baseModel = configuredModel?.takeIf { it.isNotBlank() }
                ?.let { GeminiRestClient.sanitizeModelName(it) }
                ?: UserAiPreferences.resolveEffectiveGeminiModel(cleanKey)
            workingModelByCustomKey["$cleanKey|$baseModel"] = cleanModel
        }
    }

    internal fun resolveEffectiveModelForCustomKey(
        context: Context?,
        apiKey: String,
        overrideModel: String? = null
    ): String {
        if (!overrideModel.isNullOrBlank()) {
            return GeminiRestClient.sanitizeModelName(overrideModel)
        }
        val cleanKey = GeminiRestClient.sanitizeApiKey(apiKey)
        val configuredModel = UserAiPreferences.resolveEffectiveGeminiModel(context, cleanKey)
        workingModelByCustomKey["$cleanKey|$configuredModel"]?.takeIf { it.isNotBlank() }?.let { return it }
        return configuredModel
    }

    internal fun resolveCustomKeyFallbackModels(failedModel: String): List<String> {
        val cleanFailed = GeminiRestClient.sanitizeModelName(failedModel)
        return CUSTOM_GEMINI_FALLBACK_MODELS.filterNot { it.equals(cleanFailed, ignoreCase = true) }
    }

    val VISION_PRESET_CONFIG = GeminiGenerationConfig(
        temperature = 0.1f,
        maxOutputTokens = 8192,
        topP = 0.8f
    )

    val FAST_PRESET_CONFIG = GeminiGenerationConfig(
        temperature = 0.25f,
        maxOutputTokens = 4096,
        topP = 0.8f
    )

    val DEEP_PRESET_CONFIG = GeminiGenerationConfig(
        temperature = 0.45f,
        maxOutputTokens = 8192,
        topP = 0.9f
    )

    private const val TAG = "GeminiAiFactory"

    private fun safeLogW(msg: String, t: Throwable? = null) {
        try {
            if (t != null) Log.w(TAG, msg, t) else Log.w(TAG, msg)
        } catch (_: Throwable) {
            // Pure-JVM unit test fallback
        }
    }

    private fun safeLogE(msg: String, t: Throwable? = null) {
        try {
            if (t != null) Log.e(TAG, msg, t) else Log.e(TAG, msg)
        } catch (_: Throwable) {
            // Pure-JVM unit test fallback
        }
    }

    fun sanitizeModelName(name: String? = null): String {
        return UserAiPreferences.resolveEffectiveModel(name)
    }

    fun resolveModelName(context: Context? = null, forKey: String? = null): String =
        UserAiPreferences.resolveEffectiveGeminiModel(context, forKey)

    fun resolveCleanApiKey(overrideKey: String? = null, context: Context? = null): String {
        if (context != null) {
            UserAiPreferences.initialize(context)
        }
        val rawKey = when {
            !overrideKey.isNullOrBlank() -> overrideKey
            UserAiPreferences.hasCustomKey() ->
                UserAiPreferences.getParsedUserGeminiKeys().firstOrNull() ?: UserAiPreferences.getCustomApiKey()
            else -> runCatching { ApiKeyRotator.getActiveKey(context) }.getOrDefault("")
        }
        return GeminiRestClient.sanitizeApiKey(rawKey)
    }

    internal fun resolveKeyCandidates(overrideKey: String? = null, context: Context? = null): Pair<List<String>, Boolean> {
        if (!overrideKey.isNullOrBlank()) {
            val parsedOverride = UserAiPreferences.parseGeminiKeyList(overrideKey)
            return if (parsedOverride.isNotEmpty()) parsedOverride to false else emptyList<String>() to false
        }
        val effective = ApiKeyRotator.getAllEffectiveKeys(context)
        val hasCustom = UserAiPreferences.hasCustomUserGeminiKeys(context)
        if (effective.isEmpty()) {
            val fallback = resolveCleanApiKey(null, context)
            return if (fallback.isNotBlank()) listOf(fallback) to !hasCustom else emptyList<String>() to !hasCustom
        }
        val startIdx = ApiKeyRotator.getStartIndex(context).coerceAtLeast(0) % effective.size
        val ordered = List(effective.size) { offset ->
            effective[(startIdx + offset) % effective.size]
        }.sortedBy { if (ApiKeyRotator.isKeyCoolingDown(it)) 1 else 0 }
        return ordered to !hasCustom
    }

    fun bytesToInlineData(
        bytes: ByteArray,
        mimeType: String = "application/pdf"
    ): GeminiPart.InlineData {
        val base64 = Base64.getEncoder().encodeToString(bytes)
        return GeminiPart.InlineData(
            mimeType = mimeType.ifBlank { "application/octet-stream" },
            base64Data = base64
        )
    }

    fun bitmapToInlineData(
        bitmap: Bitmap,
        jpegQuality: Int = 82
    ): GeminiPart.InlineData {
        if (bitmap.isRecycled) {
            throw IllegalStateException("Görsel bellekte serbest bırakılmış (recycled Bitmap).")
        }
        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, jpegQuality.coerceIn(40, 95), baos)
        val base64 = Base64.getEncoder().encodeToString(baos.toByteArray())
        return GeminiPart.InlineData(mimeType = "image/jpeg", base64Data = base64)
    }

    suspend fun generateContent(
        contents: List<GeminiContent>,
        systemPrompt: String = "",
        taskType: AiTaskType = AiTaskType.LIGHT_INTERACTION,
        generationConfig: GeminiGenerationConfig? = null,
        tools: List<GeminiFunctionDeclaration> = emptyList(),
        overrideKey: String? = null,
        overrideModel: String? = null
    ): GeminiResponse = withContext(Dispatchers.IO) {
        val resolvedConfig = generationConfig ?: taskType.toGenerationConfig()
        val (keys, isBuiltInMode) = resolveKeyCandidates(overrideKey, null)

        var proxyError: Throwable? = null
        if (overrideKey.isNullOrBlank() && isBuiltInMode) {
            val (proxyResp, err) = tryExecuteViaProxy(
                stream = false,
                contents = contents,
                systemInstruction = systemPrompt,
                generationConfig = resolvedConfig,
                tools = tools
            )
            proxyError = err
            if (proxyResp != null) return@withContext proxyResp
        }

        if (keys.isEmpty()) {
            if (proxyError != null) throw proxyError
            throw GeminiApiException(
                message = "Gemini API anahtarı bulunamadı. Ayarlar'dan kendi anahtarınızı ekleyebilirsiniz.",
                httpCode = 401,
                apiStatus = "UNAUTHENTICATED"
            )
        }

        val customKeysSet = UserAiPreferences.getParsedUserGeminiKeys().toSet()
        val modelResolver: (String) -> String = { key ->
            when {
                !overrideModel.isNullOrBlank() && (!overrideKey.isNullOrBlank() || key in customKeysSet) ->
                    GeminiRestClient.sanitizeModelName(overrideModel)
                key in customKeysSet ->
                    resolveEffectiveModelForCustomKey(null, key)
                else ->
                    SOLE_GEMINI_MODEL
            }
        }

        val primaryAttempt = tryExecuteKeys(
            keys = keys,
            modelResolver = modelResolver,
            contents = contents,
            systemPrompt = systemPrompt,
            generationConfig = resolvedConfig,
            tools = tools
        )
        primaryAttempt.response?.let { return@withContext it }

        val primaryError = primaryAttempt.lastError!!
        val builtInFallbackKeys = keys.filter {
            it !in customKeysSet && modelResolver(it) != CONTROLLED_404_FALLBACK_MODEL
        }
        val canFallbackModel = primaryAttempt.fallbackEligibleError != null || isFallbackEligibleFailure(primaryError)
        if (builtInFallbackKeys.isNotEmpty() && (isBuiltInMode || primaryAttempt.triedBuiltInKeys) && canFallbackModel) {
            safeLogW(
                "$SOLE_GEMINI_MODEL unavailable across built-in endpoints/keys. Trying controlled fallback $CONTROLLED_404_FALLBACK_MODEL..."
            )
            val fallbackAttempt = tryExecuteKeys(
                keys = builtInFallbackKeys,
                modelResolver = { CONTROLLED_404_FALLBACK_MODEL },
                contents = contents,
                systemPrompt = systemPrompt,
                generationConfig = resolvedConfig,
                tools = tools
            )
            fallbackAttempt.response?.let { return@withContext it }
        }

        // Ultimate safety net: if custom user key + direct built-in keys all failed, try Firebase Proxy!
        if (overrideKey.isNullOrBlank() && !isBuiltInMode) {
            val (proxyResp, _) = tryExecuteViaProxy(
                stream = false,
                contents = contents,
                systemInstruction = systemPrompt,
                generationConfig = resolvedConfig,
                tools = tools
            )
            if (proxyResp != null) return@withContext proxyResp
        }

        throw primaryError
    }

    suspend fun generateText(
        prompt: String,
        systemPrompt: String = "",
        taskType: AiTaskType = AiTaskType.LIGHT_INTERACTION,
        generationConfig: GeminiGenerationConfig? = null,
        tools: List<GeminiFunctionDeclaration> = emptyList(),
        overrideKey: String? = null,
        overrideModel: String? = null
    ): String {
        val response = generateContent(
            contents = listOf(GeminiContent.userText(prompt)),
            systemPrompt = systemPrompt,
            taskType = taskType,
            generationConfig = generationConfig,
            tools = tools,
            overrideKey = overrideKey,
            overrideModel = overrideModel
        )
        return response.text
    }

    suspend fun generateWithImages(
        prompt: String,
        bitmaps: List<Bitmap> = emptyList(),
        inlineDataList: List<GeminiPart.InlineData> = emptyList(),
        systemPrompt: String = "",
        taskType: AiTaskType = AiTaskType.HEAVY_ANALYSIS,
        generationConfig: GeminiGenerationConfig? = VISION_PRESET_CONFIG,
        tools: List<GeminiFunctionDeclaration> = emptyList(),
        overrideKey: String? = null,
        overrideModel: String? = null
    ): String {
        val parts = buildList {
            bitmaps.forEach { if (!it.isRecycled) add(bitmapToInlineData(it)) }
            addAll(inlineDataList)
            add(GeminiPart.Text(prompt))
        }
        val response = generateContent(
            contents = listOf(GeminiContent(role = "user", parts = parts)),
            systemPrompt = systemPrompt,
            taskType = taskType,
            generationConfig = generationConfig,
            tools = tools,
            overrideKey = overrideKey,
            overrideModel = overrideModel
        )
        return response.text
    }

    suspend fun streamContents(
        contents: List<GeminiContent>,
        systemPrompt: String = "",
        taskType: AiTaskType = AiTaskType.LIGHT_INTERACTION,
        generationConfig: GeminiGenerationConfig? = null,
        tools: List<GeminiFunctionDeclaration> = emptyList(),
        overrideKey: String? = null,
        overrideModel: String? = null,
        onPartialText: (String) -> Unit = {},
        onThinkingText: ((String) -> Unit)? = null
    ): GeminiResponse = withContext(Dispatchers.IO) {
        val resolvedConfig = generationConfig ?: taskType.toGenerationConfig()
        val (keys, isBuiltInMode) = resolveKeyCandidates(overrideKey, null)

        var proxyError: Throwable? = null
        if (overrideKey.isNullOrBlank() && isBuiltInMode) {
            val (proxyResp, err) = tryExecuteViaProxy(
                stream = true,
                contents = contents,
                systemInstruction = systemPrompt,
                generationConfig = resolvedConfig,
                tools = tools,
                onPartialText = onPartialText,
                onThinkingText = onThinkingText
            )
            proxyError = err
            if (proxyResp != null) return@withContext proxyResp
        }

        if (keys.isEmpty()) {
            if (proxyError != null) throw proxyError
            throw GeminiApiException(
                message = "Gemini API anahtarı bulunamadı. Ayarlar'dan kendi anahtarınızı ekleyebilirsiniz.",
                httpCode = 401,
                apiStatus = "UNAUTHENTICATED"
            )
        }

        val customKeysSet = UserAiPreferences.getParsedUserGeminiKeys().toSet()
        val modelResolver: (String) -> String = { key ->
            when {
                !overrideModel.isNullOrBlank() && (!overrideKey.isNullOrBlank() || key in customKeysSet) ->
                    GeminiRestClient.sanitizeModelName(overrideModel)
                key in customKeysSet ->
                    resolveEffectiveModelForCustomKey(null, key)
                else ->
                    SOLE_GEMINI_MODEL
            }
        }

        val primaryAttempt = tryStreamKeys(
            keys = keys,
            modelResolver = modelResolver,
            contents = contents,
            systemPrompt = systemPrompt,
            generationConfig = resolvedConfig,
            tools = tools,
            onPartialText = onPartialText,
            onThinkingText = onThinkingText
        )
        primaryAttempt.response?.let { return@withContext it }

        val primaryError = primaryAttempt.lastError!!
        val builtInFallbackKeys = keys.filter {
            it !in customKeysSet && modelResolver(it) != CONTROLLED_404_FALLBACK_MODEL
        }
        val canFallbackModel = primaryAttempt.fallbackEligibleError != null || isFallbackEligibleFailure(primaryError)
        if (builtInFallbackKeys.isNotEmpty() && (isBuiltInMode || primaryAttempt.triedBuiltInKeys) && canFallbackModel) {
            safeLogW(
                "Stream: $SOLE_GEMINI_MODEL unavailable across built-in endpoints/keys. Trying controlled fallback $CONTROLLED_404_FALLBACK_MODEL..."
            )
            val fallbackAttempt = tryStreamKeys(
                keys = builtInFallbackKeys,
                modelResolver = { CONTROLLED_404_FALLBACK_MODEL },
                contents = contents,
                systemPrompt = systemPrompt,
                generationConfig = resolvedConfig,
                tools = tools,
                onPartialText = onPartialText,
                onThinkingText = onThinkingText
            )
            fallbackAttempt.response?.let { return@withContext it }
        }

        // Ultimate safety net: if custom user key + direct built-in keys all failed, try Firebase Proxy!
        if (overrideKey.isNullOrBlank() && !isBuiltInMode) {
            val (proxyResp, _) = tryExecuteViaProxy(
                stream = true,
                contents = contents,
                systemInstruction = systemPrompt,
                generationConfig = resolvedConfig,
                tools = tools,
                onPartialText = onPartialText,
                onThinkingText = onThinkingText
            )
            if (proxyResp != null) return@withContext proxyResp
        }

        throw primaryError
    }

    suspend fun streamText(
        prompt: String,
        systemPrompt: String = "",
        taskType: AiTaskType = AiTaskType.LIGHT_INTERACTION,
        generationConfig: GeminiGenerationConfig? = null,
        tools: List<GeminiFunctionDeclaration> = emptyList(),
        overrideKey: String? = null,
        overrideModel: String? = null,
        onPartialText: (String) -> Unit = {},
        onThinkingText: ((String) -> Unit)? = null
    ): GeminiResponse {
        return streamContents(
            contents = listOf(GeminiContent.userText(prompt)),
            systemPrompt = systemPrompt,
            taskType = taskType,
            generationConfig = generationConfig,
            tools = tools,
            overrideKey = overrideKey,
            overrideModel = overrideModel,
            onPartialText = onPartialText,
            onThinkingText = onThinkingText
        )
    }

    suspend fun streamWithImages(
        prompt: String,
        bitmaps: List<Bitmap> = emptyList(),
        inlineDataList: List<GeminiPart.InlineData> = emptyList(),
        systemPrompt: String = "",
        taskType: AiTaskType = AiTaskType.HEAVY_ANALYSIS,
        generationConfig: GeminiGenerationConfig? = VISION_PRESET_CONFIG,
        tools: List<GeminiFunctionDeclaration> = emptyList(),
        overrideKey: String? = null,
        overrideModel: String? = null,
        onPartialText: (String) -> Unit = {},
        onThinkingText: ((String) -> Unit)? = null
    ): GeminiResponse {
        val parts = buildList {
            bitmaps.forEach { if (!it.isRecycled) add(bitmapToInlineData(it)) }
            addAll(inlineDataList)
            add(GeminiPart.Text(prompt))
        }
        return streamContents(
            contents = listOf(GeminiContent(role = "user", parts = parts)),
            systemPrompt = systemPrompt,
            taskType = taskType,
            generationConfig = generationConfig,
            tools = tools,
            overrideKey = overrideKey,
            overrideModel = overrideModel,
            onPartialText = onPartialText,
            onThinkingText = onThinkingText
        )
    }

    /**
     * Unified non-streaming failover entrypoint used across repositories, ViewModels, workers & activities.
     */
    suspend fun generateContentWithFailover(
        context: Context? = null,
        systemInstruction: String = "",
        userPrompt: String,
        chatHistory: List<GeminiChatTurn> = emptyList(),
        bitmaps: List<Bitmap> = emptyList(),
        inlineFiles: List<Pair<String, ByteArray>> = emptyList(),
        tools: List<GeminiFunctionDeclaration> = emptyList(),
        responseJson: Boolean = false,
        temperature: Float? = null,
        maxOutputTokens: Int = 8192,
        topP: Float? = null,
        timeoutMs: Long = 60_000L
    ): Result<GeminiSdkResult> = withContext(Dispatchers.IO) {
        executeRestWithFailover(
            context = context,
            stream = false,
            systemInstruction = systemInstruction,
            userPrompt = userPrompt,
            chatHistory = chatHistory,
            bitmaps = bitmaps,
            inlineFiles = inlineFiles,
            tools = tools,
            responseJson = responseJson,
            temperature = temperature,
            maxOutputTokens = maxOutputTokens,
            topP = topP,
            onPartialText = {}
        )
    }

    /**
     * Unified SSE streaming failover entrypoint used across Shark AI, Notes AI, and PDF Reader AI.
     */
    suspend fun streamContentWithFailover(
        context: Context? = null,
        systemInstruction: String = "",
        userPrompt: String,
        chatHistory: List<GeminiChatTurn> = emptyList(),
        bitmaps: List<Bitmap> = emptyList(),
        inlineFiles: List<Pair<String, ByteArray>> = emptyList(),
        tools: List<GeminiFunctionDeclaration> = emptyList(),
        responseJson: Boolean = false,
        temperature: Float? = null,
        maxOutputTokens: Int = 8192,
        topP: Float? = null,
        timeoutMs: Long = 60_000L,
        onPartialText: (String) -> Unit = {}
    ): Result<GeminiSdkResult> = withContext(Dispatchers.IO) {
        executeRestWithFailover(
            context = context,
            stream = true,
            systemInstruction = systemInstruction,
            userPrompt = userPrompt,
            chatHistory = chatHistory,
            bitmaps = bitmaps,
            inlineFiles = inlineFiles,
            tools = tools,
            responseJson = responseJson,
            temperature = temperature,
            maxOutputTokens = maxOutputTokens,
            topP = topP,
            onPartialText = onPartialText
        )
    }

    private suspend fun executeRestWithFailover(
        context: Context?,
        stream: Boolean,
        systemInstruction: String,
        userPrompt: String,
        chatHistory: List<GeminiChatTurn>,
        bitmaps: List<Bitmap>,
        inlineFiles: List<Pair<String, ByteArray>>,
        tools: List<GeminiFunctionDeclaration>,
        responseJson: Boolean,
        temperature: Float?,
        maxOutputTokens: Int,
        topP: Float?,
        onPartialText: (String) -> Unit
    ): Result<GeminiSdkResult> {
        if (context != null) {
            UserAiPreferences.initialize(context)
        }
        val hasCustomUserKeys = UserAiPreferences.hasCustomUserGeminiKeys(context)
        val keys = ApiKeyRotator.getAllEffectiveKeys(context)
            .map { GeminiRestClient.sanitizeApiKey(it) }
            .filter { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
            .distinct()

        val resolvedTemp = temperature ?: if (responseJson) 0.2f else if (bitmaps.isNotEmpty()) 0.1f else 0.35f
        val resolvedTopP = topP ?: if (responseJson) 0.8f else 0.85f
        val genConfig = GeminiGenerationConfig(
            temperature = resolvedTemp,
            maxOutputTokens = maxOutputTokens.coerceIn(256, 32768),
            topP = resolvedTopP,
            responseMimeType = if (responseJson) "application/json" else null
        )

        val tempScaledBitmaps = mutableListOf<Bitmap>()
        try {
            val preparedBitmaps = bitmaps.mapNotNull { bmp ->
                if (bmp.isRecycled) return@mapNotNull null
                val maxDim = maxOf(bmp.width, bmp.height)
                if (maxDim > 1400) {
                    val scale = 1400f / maxDim.toFloat()
                    val w = (bmp.width * scale).toInt().coerceAtLeast(1)
                    val h = (bmp.height * scale).toInt().coerceAtLeast(1)
                    runCatching {
                        Bitmap.createScaledBitmap(bmp, w, h, true).also { tempScaledBitmaps.add(it) }
                    }.getOrDefault(bmp)
                } else {
                    bmp
                }
            }

            val contents = buildContentsList(
                chatHistory = chatHistory,
                userPrompt = userPrompt,
                bitmaps = preparedBitmaps,
                inlineFiles = inlineFiles
            )

            // Standard mode (no custom user key): prefer Firebase Cloud Functions proxy first.
            var proxyError: Throwable? = null
            if (!hasCustomUserKeys) {
                var lastProxyEmitted = ""
                val (proxyResp, err) = tryExecuteViaProxy(
                    stream = stream,
                    contents = contents,
                    systemInstruction = systemInstruction,
                    generationConfig = genConfig,
                    tools = tools,
                    responseJson = responseJson,
                    onPartialText = { partial ->
                        lastProxyEmitted = partial
                        onPartialText(partial)
                    }
                )
                proxyError = err
                if (proxyResp != null) {
                    val finalText = if (responseJson) {
                        GeminiRestClient.extractCleanJsonString(proxyResp.text)
                    } else {
                        proxyResp.text.trim()
                    }
                    if (finalText.isNotBlank() || proxyResp.functionCalls.isNotEmpty()) {
                        if (finalText.isNotBlank() && finalText != lastProxyEmitted) {
                            onPartialText(finalText)
                        }
                        return Result.success(
                            GeminiSdkResult(
                                text = finalText,
                                functionCalls = proxyResp.functionCalls,
                                usedKeyIndex = -1,
                                modelUsed = proxyResp.modelName.ifBlank { SOLE_GEMINI_MODEL }
                            )
                        )
                    }
                }
            }

            if (keys.isEmpty()) {
                val failure = proxyError
                    ?: IllegalStateException("Gemini API anahtarı tanımlı değil. Ayarlardan API anahtarı ekleyin.")
                return Result.failure(failure)
            }

            val startIdx = ApiKeyRotator.getStartIndex(context).coerceAtLeast(0) % keys.size
            val roundRobin = List(keys.size) { offset ->
                val actualIdx = (startIdx + offset) % keys.size
                actualIdx to keys[actualIdx]
            }
            val (healthyEntries, coolingEntries) = roundRobin.partition { (_, candidateKey) ->
                !ApiKeyRotator.isKeyCoolingDown(candidateKey)
            }
            val orderedWithIndex = healthyEntries + coolingEntries

            var lastException: Throwable? = proxyError
            var fallbackEligibleBuiltInError: Throwable? = null
            var triedAnyBuiltInKey = false
            var builtInKeysNeedFallbackModel = false
            val userKeysSet = UserAiPreferences.getParsedUserGeminiKeys().toSet()
            val failureNotes = mutableListOf<String>()

            for ((pos, pair) in orderedWithIndex.withIndex()) {
                val (keyIndex, activeKey) = pair
                val masked = ApiKeyRotator.maskApiKey(activeKey, slotIndex = null)
                val isBuiltInKey = activeKey !in userKeysSet
                val modelName = if (isBuiltInKey) {
                    SOLE_GEMINI_MODEL
                } else {
                    resolveEffectiveModelForCustomKey(context, activeKey)
                }
                if (isBuiltInKey) {
                    triedAnyBuiltInKey = true
                    if (modelName != CONTROLLED_404_FALLBACK_MODEL) {
                        builtInKeysNeedFallbackModel = true
                    }
                }

                var lastEmitted = ""
                val attempt = executeSingleKey(
                    key = activeKey,
                    modelName = modelName,
                    stream = stream,
                    contents = contents,
                    systemInstruction = systemInstruction,
                    generationConfig = genConfig,
                    tools = tools,
                    responseJson = responseJson,
                    onPartialText = { partial ->
                        lastEmitted = partial
                        onPartialText(partial)
                    }
                )

                attempt.response?.let { resp ->
                    val finalText = if (responseJson) {
                        GeminiRestClient.extractCleanJsonString(resp.text)
                    } else {
                        resp.text.trim()
                    }
                    if (finalText.isNotBlank() || resp.functionCalls.isNotEmpty()) {
                        ApiKeyRotator.reportKeySuccess(activeKey)
                        if (!isBuiltInKey) rememberWorkingCustomKeyModel(activeKey, resp.modelName.ifBlank { modelName })
                        if (finalText.isNotBlank() && finalText != lastEmitted) {
                            onPartialText(finalText)
                        }
                        ApiKeyRotator.markCurrentIndex(keyIndex)
                        return Result.success(
                            GeminiSdkResult(
                                text = finalText,
                                functionCalls = resp.functionCalls,
                                usedKeyIndex = keyIndex,
                                modelUsed = resp.modelName.ifBlank { modelName }
                            )
                        )
                    }
                }

                var err = attempt.lastError
                if (err is CancellationException) throw err

                // Smart custom-key 404 model recovery: if user's own Gemini key (e.g. AIza...) returned
                // 404 NOT_FOUND on default/preview model (e.g. gemini-3.6-flash), automatically probe live
                // Google AI Studio models (gemini-2.5-flash -> gemini-2.5-flash-lite -> gemini-2.0-flash)
                // on that SAME user key and cache the working model for zero-latency future calls!
                if (!isBuiltInKey && (err as? GeminiApiException)?.isModelNotFound == true) {
                    for (fbModel in resolveCustomKeyFallbackModels(modelName)) {
                        var fbLastEmitted = ""
                        val fbAttempt = executeSingleKey(
                            key = activeKey,
                            modelName = fbModel,
                            stream = stream,
                            contents = contents,
                            systemInstruction = systemInstruction,
                            generationConfig = genConfig,
                            tools = tools,
                            responseJson = responseJson,
                            onPartialText = { partial ->
                                fbLastEmitted = partial
                                onPartialText(partial)
                            }
                        )
                        fbAttempt.response?.let { resp ->
                            val finalText = if (responseJson) {
                                GeminiRestClient.extractCleanJsonString(resp.text)
                            } else {
                                resp.text.trim()
                            }
                            if (finalText.isNotBlank() || resp.functionCalls.isNotEmpty()) {
                                ApiKeyRotator.reportKeySuccess(activeKey)
                                rememberWorkingCustomKeyModel(activeKey, fbModel)
                                if (finalText.isNotBlank() && finalText != fbLastEmitted) {
                                    onPartialText(finalText)
                                }
                                ApiKeyRotator.markCurrentIndex(keyIndex)
                                return Result.success(
                                    GeminiSdkResult(
                                        text = finalText,
                                        functionCalls = resp.functionCalls,
                                        usedKeyIndex = keyIndex,
                                        modelUsed = fbModel
                                    )
                                )
                            }
                        }
                        val fbErr = fbAttempt.lastError
                        if (fbErr is CancellationException) throw fbErr
                        if (fbErr != null) {
                            err = fbErr
                            // Stop probing models on this key if key itself is unauthorized/rate-limited/offline
                            val fbApiErr = fbErr as? GeminiApiException
                            if (fbApiErr?.isAuthOrKeyError == true || fbApiErr?.isQuotaOrRateLimit == true ||
                                (GeminiRestClient.isNetworkUnreachableError(fbErr) && !GeminiRestClient.isTimeoutError(fbErr))
                            ) {
                                break
                            }
                        }
                    }
                }

                lastException = err

                if (err != null && GeminiRestClient.isNetworkUnreachableError(err) && !GeminiRestClient.isTimeoutError(err)) {
                    return Result.failure(
                        Exception("İnternet bağlantısı kurulamadı. Lütfen ağ bağlantınızı kontrol edin.", err)
                    )
                }

                if (err != null && (
                        ApiKeyRotator.shouldRotateKey(err) ||
                        (!isBuiltInKey && ((err as? GeminiApiException)?.isModelNotFound == true || GeminiRestClient.isTimeoutError(err)))
                    )
                ) {
                    ApiKeyRotator.reportKeyFailure(activeKey)
                }

                if (isBuiltInKey && fallbackEligibleBuiltInError == null &&
                    (attempt.fallbackEligibleError != null || isFallbackEligibleFailure(err))
                ) {
                    fallbackEligibleBuiltInError = attempt.fallbackEligibleError ?: err
                }

                if (err != null) {
                    failureNotes.add("#${keyIndex + 1} ($masked • $modelName): ${formatError(err)}")
                }

                if (pos + 1 < orderedWithIndex.size) {
                    ApiKeyRotator.rotateToNextKey(context)
                    val apiErr = err as? GeminiApiException
                    if (apiErr?.isQuotaOrRateLimit == true || apiErr?.isOverloadOrTransient == true) {
                        delay(40L)
                    }
                }
            }

            // Controlled fallback (gemini-3.5-flash-lite) ONLY on built-in keys when primary fails with 404/429/503/timeout.
            if (triedAnyBuiltInKey && builtInKeysNeedFallbackModel && fallbackEligibleBuiltInError != null) {
                val builtInEntries = orderedWithIndex.filter {
                    it.second !in userKeysSet &&
                        UserAiPreferences.resolveEffectiveGeminiModel(context, it.second) != CONTROLLED_404_FALLBACK_MODEL
                }
                for ((keyIndex, activeKey) in builtInEntries) {
                    var lastEmitted = ""
                    val attempt = executeSingleKey(
                        key = activeKey,
                        modelName = CONTROLLED_404_FALLBACK_MODEL,
                        stream = stream,
                        contents = contents,
                        systemInstruction = systemInstruction,
                        generationConfig = genConfig,
                        tools = tools,
                        responseJson = responseJson,
                        onPartialText = { partial ->
                            lastEmitted = partial
                            onPartialText(partial)
                        }
                    )
                    attempt.response?.let { resp ->
                        val finalText = if (responseJson) {
                            GeminiRestClient.extractCleanJsonString(resp.text)
                        } else {
                            resp.text.trim()
                        }
                        if (finalText.isNotBlank() || resp.functionCalls.isNotEmpty()) {
                            ApiKeyRotator.reportKeySuccess(activeKey)
                            if (finalText.isNotBlank() && finalText != lastEmitted) {
                                onPartialText(finalText)
                            }
                            ApiKeyRotator.markCurrentIndex(keyIndex)
                            return Result.success(
                                GeminiSdkResult(
                                    text = finalText,
                                    functionCalls = resp.functionCalls,
                                    usedKeyIndex = keyIndex,
                                    modelUsed = CONTROLLED_404_FALLBACK_MODEL
                                )
                            )
                        }
                    }
                    attempt.lastError?.let { fbErr ->
                        lastException = fbErr
                        failureNotes.add(
                            "#${keyIndex + 1} (${ApiKeyRotator.maskApiKey(activeKey)} • $CONTROLLED_404_FALLBACK_MODEL): ${formatError(fbErr)}"
                        )
                    }
                }
            }

            // Ultimate safety net: if custom user key + direct built-in keys all failed, try Firebase Proxy!
            if (hasCustomUserKeys) {
                var lastProxyEmitted = ""
                val (proxyResp, _) = tryExecuteViaProxy(
                    stream = stream,
                    contents = contents,
                    systemInstruction = systemInstruction,
                    generationConfig = genConfig,
                    tools = tools,
                    responseJson = responseJson,
                    onPartialText = { partial ->
                        lastProxyEmitted = partial
                        onPartialText(partial)
                    }
                )
                if (proxyResp != null) {
                    val finalText = if (responseJson) {
                        GeminiRestClient.extractCleanJsonString(proxyResp.text)
                    } else {
                        proxyResp.text.trim()
                    }
                    if (finalText.isNotBlank() || proxyResp.functionCalls.isNotEmpty()) {
                        if (finalText.isNotBlank() && finalText != lastProxyEmitted) {
                            onPartialText(finalText)
                        }
                        return Result.success(
                            GeminiSdkResult(
                                text = finalText,
                                functionCalls = proxyResp.functionCalls,
                                usedKeyIndex = -1,
                                modelUsed = proxyResp.modelName.ifBlank { SOLE_GEMINI_MODEL }
                            )
                        )
                    }
                }
            }

            val summary = buildFailureMessage(lastException, failureNotes)
            return Result.failure(Exception(summary, lastException))
        } finally {
            tempScaledBitmaps.forEach { runCatching { if (!it.isRecycled) it.recycle() } }
        }
    }

    private suspend fun executeSingleKey(
        key: String,
        modelName: String,
        stream: Boolean,
        contents: List<GeminiContent>,
        systemInstruction: String,
        generationConfig: GeminiGenerationConfig,
        tools: List<GeminiFunctionDeclaration>,
        responseJson: Boolean,
        onPartialText: (String) -> Unit
    ): KeyAttemptOutcome {
        var lastError: Throwable? = null
        val maxRetries = 2
        for (retry in 1..maxRetries) {
            val partialBackup = StringBuilder()
            try {
                val response = if (!stream) {
                    GeminiRestClient.generateContent(
                        apiKey = key,
                        modelName = modelName,
                        contents = contents,
                        systemInstruction = systemInstruction,
                        generationConfig = generationConfig,
                        tools = tools
                    )
                } else {
                    GeminiRestClient.streamGenerateContent(
                        apiKey = key,
                        modelName = modelName,
                        contents = contents,
                        systemInstruction = systemInstruction,
                        generationConfig = generationConfig,
                        tools = tools,
                        onPartialText = { accumulated ->
                            partialBackup.setLength(0)
                            partialBackup.append(accumulated)
                            if (!responseJson) {
                                onPartialText(accumulated)
                            }
                        }
                    )
                }
                if (response.text.isBlank() && response.functionCalls.isEmpty()) {
                    throw IllegalStateException("Model boş yanıt döndürdü ($modelName).")
                }
                return KeyAttemptOutcome(response = response)
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Throwable) {
                if (stream && partialBackup.length >= 40) {
                    safeLogW("Stream interrupted mid-response; retaining ${partialBackup.length} streamed chars.")
                    return KeyAttemptOutcome(
                        response = GeminiResponse(
                            text = partialBackup.toString(),
                            modelName = modelName
                        )
                    )
                }
                lastError = e
                safeLogE("Gemini REST failed ($modelName, retry=$retry): ${e.message}", e)
                if (GeminiRestClient.isNetworkUnreachableError(e) && !GeminiRestClient.isTimeoutError(e)) {
                    return KeyAttemptOutcome(
                        lastError = e,
                        fallbackEligibleError = null
                    )
                }
                if (GeminiRestClient.isTimeoutError(e)) {
                    break
                }
                val isTransient = (e as? GeminiApiException)?.isOverloadOrTransient == true
                if (isTransient && retry < maxRetries) {
                    delay(200L)
                    continue
                }
                break
            }
        }
        return KeyAttemptOutcome(
            lastError = lastError,
            fallbackEligibleError = if (isFallbackEligibleFailure(lastError)) lastError else null
        )
    }

    private fun buildContentsList(
        chatHistory: List<GeminiChatTurn>,
        userPrompt: String,
        bitmaps: List<Bitmap>,
        inlineFiles: List<Pair<String, ByteArray>>
    ): List<GeminiContent> {
        val mergedHistory = mutableListOf<Pair<String, StringBuilder>>()
        for (turn in chatHistory) {
            val txt = turn.text.trim()
            if (txt.isBlank()) continue
            val role = if (turn.role.equals("model", ignoreCase = true) ||
                turn.role.equals("assistant", ignoreCase = true) ||
                turn.role.equals("ai", ignoreCase = true)
            ) "model" else "user"

            val last = mergedHistory.lastOrNull()
            if (last != null && last.first == role) {
                last.second.append("\n\n").append(txt)
            } else {
                mergedHistory.add(role to StringBuilder(txt))
            }
        }

        while (mergedHistory.firstOrNull()?.first == "model") {
            mergedHistory.removeAt(0)
        }

        val effectivePrompt = userPrompt.trim().ifBlank { "Devam et." }
        val lastIsUser = mergedHistory.lastOrNull()?.first == "user"
        val trailingUserPrefix = if (lastIsUser) {
            mergedHistory.removeAt(mergedHistory.lastIndex).second.toString() + "\n\n"
        } else {
            ""
        }

        val result = mutableListOf<GeminiContent>()
        for ((role, sb) in mergedHistory) {
            result.add(
                GeminiContent(
                    role = role,
                    parts = listOf(GeminiPart.Text(sb.toString()))
                )
            )
        }

        val finalParts = buildList {
            for (bmp in bitmaps) {
                if (!bmp.isRecycled) {
                    add(bitmapToInlineData(bmp))
                }
            }
            for ((mimeType, bytes) in inlineFiles) {
                if (bytes.isNotEmpty()) {
                    add(bytesToInlineData(bytes, mimeType))
                }
            }
            add(GeminiPart.Text(trailingUserPrefix + effectivePrompt))
        }
        result.add(GeminiContent(role = "user", parts = finalParts))
        return result
    }

    suspend fun fetchAvailableModels(apiKey: String? = null): GeminiModelListResult {
        val key = GeminiRestClient.sanitizeApiKey(
            apiKey ?: if (UserAiPreferences.hasCustomKey()) UserAiPreferences.getCustomApiKey() else ApiKeyRotator.getActiveKey()
        )
        return GeminiRestClient.listModels(key).also { result ->
            if (result.models.isNotEmpty() && UserAiPreferences.hasCustomKey()) {
                UserAiPreferences.setCachedModels(result.models.map { it.id })
            }
        }
    }

    suspend fun testAllConfiguredConnections(
        customKeyOverride: String? = null,
        customModelOverride: String? = null
    ): List<GeminiConnectionTestResult> = withContext(Dispatchers.IO) {
        val cleanCustomOverride = GeminiRestClient.sanitizeApiKey(customKeyOverride)
        val effectiveCustomKey = when {
            cleanCustomOverride.isNotBlank() -> cleanCustomOverride
            UserAiPreferences.hasCustomKey() -> UserAiPreferences.getCustomApiKey()
            else -> ""
        }

        if (effectiveCustomKey.isNotBlank()) {
            val targetModel = GeminiRestClient.sanitizeModelName(
                customModelOverride?.takeIf { it.isNotBlank() }
                    ?: UserAiPreferences.getSelectedUserGeminiModel()
            ).ifBlank { SOLE_GEMINI_MODEL }

            return@withContext UserAiPreferences.parseGeminiKeyList(effectiveCustomKey).mapIndexed { idx, singleKey ->
                val slotLabel = "Özel Anahtar #${idx + 1}"
                val masked = ApiKeyRotator.maskApiKey(singleKey, slotIndex = null)
                val initialModel = resolveEffectiveModelForCustomKey(null, singleKey, customModelOverride)
                var result = GeminiRestClient.testConnection(
                    apiKey = singleKey,
                    modelName = initialModel,
                    slotLabel = slotLabel,
                    maskedKey = masked
                )
                if (result.isSuccess) {
                    rememberWorkingCustomKeyModel(singleKey, initialModel, targetModel)
                } else if (result.httpCode == 404) {
                    for (fbModel in resolveCustomKeyFallbackModels(initialModel)) {
                        val fbResult = GeminiRestClient.testConnection(
                            apiKey = singleKey,
                            modelName = fbModel,
                            slotLabel = slotLabel,
                            maskedKey = masked
                        )
                        if (fbResult.isSuccess) {
                            rememberWorkingCustomKeyModel(singleKey, fbModel, targetModel)
                            result = fbResult
                            break
                        }
                        if (fbResult.httpCode != 404) {
                            result = fbResult
                            break
                        }
                    }
                }
                result
            }
        }

        val builtInKeys = ApiKeyRotator.getAllBuiltInKeys()
        if (builtInKeys.isEmpty()) {
            return@withContext listOf(
                GeminiConnectionTestResult(
                    slotLabel = "Yerleşik Anahtarlar",
                    maskedKey = "Tanımsız",
                    isSuccess = false,
                    endpoint = GeminiEndpointType.GOOGLE_AI_STUDIO,
                    modelName = SOLE_GEMINI_MODEL,
                    httpCode = 0,
                    message = "BuildConfig içinde yerleşik Gemini API anahtarı bulunamadı. Yukarıdan özel anahtar girebilirsiniz."
                )
            )
        }

        builtInKeys.mapIndexed { idx, key ->
            GeminiRestClient.testConnection(
                apiKey = key,
                modelName = SOLE_GEMINI_MODEL,
                slotLabel = "Yerleşik ${ApiKeyRotator.getKeySlotLabel(idx)}",
                maskedKey = ApiKeyRotator.maskApiKey(key, slotIndex = idx)
            )
        }
    }

    private data class KeyAttemptOutcome(
        val response: GeminiResponse? = null,
        val lastError: Throwable? = null,
        val fallbackEligibleError: Throwable? = null,
        val triedBuiltInKeys: Boolean = false
    )

    private data class FirebaseProxyContext(
        val proxyUrl: String,
        val idToken: String
    )

    private const val PROXY_COOLDOWN_MS = 10L * 60L * 1_000L

    @Volatile
    private var proxyCooldownUntilMs: Long = 0L

    internal fun isProxyCoolingDown(nowMs: Long = System.currentTimeMillis()): Boolean =
        nowMs < proxyCooldownUntilMs

    internal fun markProxyUnavailable(
        httpCode: Int?,
        rawMessage: String?,
        nowMs: Long = System.currentTimeMillis()
    ): Boolean {
        val msg = rawMessage.orEmpty().lowercase()
        val shouldCooldown = httpCode in setOf(403, 404) ||
            (httpCode == 503 && (msg.contains("not configured") || msg.contains("missing gemini"))) ||
            msg.contains("not found")
        if (shouldCooldown) {
            proxyCooldownUntilMs = nowMs + PROXY_COOLDOWN_MS
        }
        return shouldCooldown
    }

    internal fun resetProxyCircuitBreakerForTesting() {
        proxyCooldownUntilMs = 0L
    }

    private suspend fun resolveFirebaseProxyContext(): FirebaseProxyContext? {
        if (isProxyCoolingDown()) return null
        val projectId = runCatching {
            FirebaseApp.getInstance().options.projectId
        }.getOrNull()
        val proxyUrl = GeminiRestClient.resolveProxyUrl(projectId) ?: return null
        val idToken = runCatching {
            FirebaseAuth.getInstance().currentUser?.getIdToken(false)?.await()?.token
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: return null
        return FirebaseProxyContext(proxyUrl = proxyUrl, idToken = idToken)
    }

    private suspend fun tryExecuteViaProxy(
        stream: Boolean,
        contents: List<GeminiContent>,
        systemInstruction: String,
        generationConfig: GeminiGenerationConfig,
        tools: List<GeminiFunctionDeclaration>,
        responseJson: Boolean = false,
        onPartialText: (String) -> Unit = {},
        onThinkingText: ((String) -> Unit)? = null
    ): Pair<GeminiResponse?, Throwable?> {
        val proxyCtx = resolveFirebaseProxyContext() ?: return null to null
        val partialBackup = StringBuilder()
        return try {
            val response = if (!stream) {
                GeminiRestClient.generateContentViaProxy(
                    proxyUrl = proxyCtx.proxyUrl,
                    idToken = proxyCtx.idToken,
                    modelName = SOLE_GEMINI_MODEL,
                    contents = contents,
                    systemInstruction = systemInstruction,
                    generationConfig = generationConfig,
                    tools = tools
                )
            } else {
                GeminiRestClient.streamGenerateContentViaProxy(
                    proxyUrl = proxyCtx.proxyUrl,
                    idToken = proxyCtx.idToken,
                    modelName = SOLE_GEMINI_MODEL,
                    contents = contents,
                    systemInstruction = systemInstruction,
                    generationConfig = generationConfig,
                    tools = tools,
                    onPartialText = { accumulated ->
                        partialBackup.setLength(0)
                        partialBackup.append(accumulated)
                        if (!responseJson) {
                            onPartialText(accumulated)
                        }
                    },
                    onThinkingText = onThinkingText
                )
            }
            if (response.text.isBlank() && response.functionCalls.isEmpty()) {
                null to IllegalStateException("Firebase proxy boş yanıt döndürdü.")
            } else {
                proxyCooldownUntilMs = 0L
                response to null
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Throwable) {
            if (stream && partialBackup.length >= 40) {
                safeLogW("Proxy stream interrupted mid-response; retaining ${partialBackup.length} streamed chars.")
                GeminiResponse(
                    text = partialBackup.toString(),
                    modelName = SOLE_GEMINI_MODEL,
                    endpoint = GeminiEndpointType.FIREBASE_PROXY
                ) to null
            } else {
                val apiEx = e as? GeminiApiException
                markProxyUnavailable(apiEx?.httpCode, e.message)
                safeLogW("Firebase Gemini proxy failed; falling back to direct built-in path: ${e.message}")
                null to e
            }
        }
    }

    private suspend fun tryExecuteKeys(
        keys: List<String>,
        modelResolver: (String) -> String,
        contents: List<GeminiContent>,
        systemPrompt: String,
        generationConfig: GeminiGenerationConfig,
        tools: List<GeminiFunctionDeclaration>
    ): KeyAttemptOutcome {
        var lastError: Throwable? = null
        var fallbackEligibleError: Throwable? = null
        val userKeysSet = UserAiPreferences.getParsedUserGeminiKeys().toSet()
        var triedBuiltIn = false

        for ((idx, key) in keys.withIndex()) {
            val modelName = modelResolver(key)
            val isBuiltInKey = key !in userKeysSet
            if (isBuiltInKey) triedBuiltIn = true
            val maxRetries = 2
            for (retry in 1..maxRetries) {
                try {
                    val response = GeminiRestClient.generateContent(
                        apiKey = key,
                        modelName = modelName,
                        contents = contents,
                        systemInstruction = systemPrompt,
                        generationConfig = generationConfig,
                        tools = tools
                    )
                    ApiKeyRotator.reportKeySuccess(key)
                    if (!isBuiltInKey) rememberWorkingCustomKeyModel(key, response.modelName.ifBlank { modelName })
                    return KeyAttemptOutcome(response = response, triedBuiltInKeys = triedBuiltIn)
                } catch (ce: CancellationException) {
                    throw ce
                } catch (e: Throwable) {
                    var effectiveErr: Throwable = e
                    // Smart custom-key 404 model recovery on user's own Gemini key
                    if (!isBuiltInKey && (e as? GeminiApiException)?.isModelNotFound == true) {
                        for (fbModel in resolveCustomKeyFallbackModels(modelName)) {
                            try {
                                val fbResp = GeminiRestClient.generateContent(
                                    apiKey = key,
                                    modelName = fbModel,
                                    contents = contents,
                                    systemInstruction = systemPrompt,
                                    generationConfig = generationConfig,
                                    tools = tools
                                )
                                ApiKeyRotator.reportKeySuccess(key)
                                rememberWorkingCustomKeyModel(key, fbModel)
                                return KeyAttemptOutcome(response = fbResp, triedBuiltInKeys = triedBuiltIn)
                            } catch (fbCe: CancellationException) {
                                throw fbCe
                            } catch (fbErr: Throwable) {
                                effectiveErr = fbErr
                                val fbApi = fbErr as? GeminiApiException
                                if (fbApi?.isAuthOrKeyError == true || fbApi?.isQuotaOrRateLimit == true ||
                                    (GeminiRestClient.isNetworkUnreachableError(fbErr) && !GeminiRestClient.isTimeoutError(fbErr))
                                ) {
                                    break
                                }
                            }
                        }
                    }

                    lastError = effectiveErr
                    if (isBuiltInKey && fallbackEligibleError == null && isFallbackEligibleFailure(effectiveErr)) {
                        fallbackEligibleError = effectiveErr
                    }
                    safeLogE("generateContent failed ($modelName, keySlot=${idx + 1}/${keys.size}): ${effectiveErr.message}", effectiveErr)

                    if (GeminiRestClient.isNetworkUnreachableError(effectiveErr) && !GeminiRestClient.isTimeoutError(effectiveErr)) {
                        return KeyAttemptOutcome(
                            lastError = GeminiApiException(
                                message = "Lütfen internet bağlantınızı kontrol edin. Ağ erişilemiyor.",
                                httpCode = 0,
                                modelName = modelName,
                                cause = effectiveErr
                            ),
                            triedBuiltInKeys = triedBuiltIn
                        )
                    }

                    if (GeminiRestClient.isTimeoutError(effectiveErr)) {
                        if (!isBuiltInKey) ApiKeyRotator.reportKeyFailure(key)
                        break
                    }

                    val apiEx = effectiveErr as? GeminiApiException
                    val is503 = apiEx?.isOverloadOrTransient == true

                    if (is503 && retry < maxRetries) {
                        delay(200L)
                        continue
                    }

                    if (ApiKeyRotator.shouldRotateKey(effectiveErr) || (!isBuiltInKey && apiEx?.isModelNotFound == true)) {
                        ApiKeyRotator.reportKeyFailure(key)
                    }

                    if (idx < keys.lastIndex) {
                        if (ApiKeyRotator.shouldRotateKey(effectiveErr) || apiEx?.isModelNotFound == true) {
                            ApiKeyRotator.rotateToNextKey()
                            if (apiEx?.isQuotaOrRateLimit == true || apiEx?.isOverloadOrTransient == true) {
                                delay(40L)
                            }
                            break
                        }
                    }

                    break
                }
            }
        }

        return KeyAttemptOutcome(
            lastError = lastError ?: GeminiApiException("Gemini yanıt vermedi.", modelName = SOLE_GEMINI_MODEL),
            fallbackEligibleError = fallbackEligibleError,
            triedBuiltInKeys = triedBuiltIn
        )
    }

    private suspend fun tryStreamKeys(
        keys: List<String>,
        modelResolver: (String) -> String,
        contents: List<GeminiContent>,
        systemPrompt: String,
        generationConfig: GeminiGenerationConfig,
        tools: List<GeminiFunctionDeclaration>,
        onPartialText: (String) -> Unit,
        onThinkingText: ((String) -> Unit)?
    ): KeyAttemptOutcome {
        var lastError: Throwable? = null
        var fallbackEligibleError: Throwable? = null
        val userKeysSet = UserAiPreferences.getParsedUserGeminiKeys().toSet()
        var triedBuiltIn = false

        for ((idx, key) in keys.withIndex()) {
            val modelName = modelResolver(key)
            val isBuiltInKey = key !in userKeysSet
            if (isBuiltInKey) triedBuiltIn = true
            val maxRetries = 2
            for (retry in 1..maxRetries) {
                val partialBackup = StringBuilder()
                try {
                    val response = GeminiRestClient.streamGenerateContent(
                        apiKey = key,
                        modelName = modelName,
                        contents = contents,
                        systemInstruction = systemPrompt,
                        generationConfig = generationConfig,
                        tools = tools,
                        onPartialText = { accumulated ->
                            partialBackup.setLength(0)
                            partialBackup.append(accumulated)
                            onPartialText(accumulated)
                        },
                        onThinkingText = onThinkingText
                    )
                    ApiKeyRotator.reportKeySuccess(key)
                    if (!isBuiltInKey) rememberWorkingCustomKeyModel(key, response.modelName.ifBlank { modelName })
                    return KeyAttemptOutcome(response = response, triedBuiltInKeys = triedBuiltIn)
                } catch (ce: CancellationException) {
                    throw ce
                } catch (e: Throwable) {
                    if (partialBackup.length >= 40) {
                        safeLogW("Stream interrupted mid-response; retaining ${partialBackup.length} streamed chars.")
                        ApiKeyRotator.reportKeySuccess(key)
                        return KeyAttemptOutcome(
                            response = GeminiResponse(
                                text = partialBackup.toString(),
                                modelName = modelName
                            ),
                            triedBuiltInKeys = triedBuiltIn
                        )
                    }

                    var effectiveErr: Throwable = e
                    if (!isBuiltInKey && (e as? GeminiApiException)?.isModelNotFound == true) {
                        for (fbModel in resolveCustomKeyFallbackModels(modelName)) {
                            val fbPartial = StringBuilder()
                            try {
                                val fbResp = GeminiRestClient.streamGenerateContent(
                                    apiKey = key,
                                    modelName = fbModel,
                                    contents = contents,
                                    systemInstruction = systemPrompt,
                                    generationConfig = generationConfig,
                                    tools = tools,
                                    onPartialText = { acc ->
                                        fbPartial.setLength(0)
                                        fbPartial.append(acc)
                                        onPartialText(acc)
                                    },
                                    onThinkingText = onThinkingText
                                )
                                ApiKeyRotator.reportKeySuccess(key)
                                rememberWorkingCustomKeyModel(key, fbModel)
                                return KeyAttemptOutcome(response = fbResp, triedBuiltInKeys = triedBuiltIn)
                            } catch (fbCe: CancellationException) {
                                throw fbCe
                            } catch (fbErr: Throwable) {
                                if (fbPartial.length >= 40) {
                                    ApiKeyRotator.reportKeySuccess(key)
                                    rememberWorkingCustomKeyModel(key, fbModel)
                                    return KeyAttemptOutcome(
                                        response = GeminiResponse(text = fbPartial.toString(), modelName = fbModel),
                                        triedBuiltInKeys = triedBuiltIn
                                    )
                                }
                                effectiveErr = fbErr
                                val fbApi = fbErr as? GeminiApiException
                                if (fbApi?.isAuthOrKeyError == true || fbApi?.isQuotaOrRateLimit == true ||
                                    (GeminiRestClient.isNetworkUnreachableError(fbErr) && !GeminiRestClient.isTimeoutError(fbErr))
                                ) {
                                    break
                                }
                            }
                        }
                    }

                    lastError = effectiveErr
                    if (isBuiltInKey && fallbackEligibleError == null && isFallbackEligibleFailure(effectiveErr)) {
                        fallbackEligibleError = effectiveErr
                    }
                    safeLogE("streamGenerateContent failed ($modelName, keySlot=${idx + 1}/${keys.size}): ${effectiveErr.message}", effectiveErr)

                    if (GeminiRestClient.isNetworkUnreachableError(effectiveErr) && !GeminiRestClient.isTimeoutError(effectiveErr)) {
                        return KeyAttemptOutcome(
                            lastError = GeminiApiException(
                                message = "Lütfen internet bağlantınızı kontrol edin. Ağ erişilemiyor.",
                                httpCode = 0,
                                modelName = modelName,
                                cause = effectiveErr
                            ),
                            triedBuiltInKeys = triedBuiltIn
                        )
                    }

                    if (GeminiRestClient.isTimeoutError(effectiveErr)) {
                        if (!isBuiltInKey) ApiKeyRotator.reportKeyFailure(key)
                        break
                    }

                    val apiEx = effectiveErr as? GeminiApiException
                    val is503 = apiEx?.isOverloadOrTransient == true

                    if (is503 && retry < maxRetries) {
                        delay(200L)
                        continue
                    }

                    if (ApiKeyRotator.shouldRotateKey(effectiveErr) || (!isBuiltInKey && apiEx?.isModelNotFound == true)) {
                        ApiKeyRotator.reportKeyFailure(key)
                    }

                    if (idx < keys.lastIndex) {
                        if (ApiKeyRotator.shouldRotateKey(effectiveErr) || apiEx?.isModelNotFound == true) {
                            ApiKeyRotator.rotateToNextKey()
                            if (apiEx?.isQuotaOrRateLimit == true || apiEx?.isOverloadOrTransient == true) {
                                delay(40L)
                            }
                            break
                        }
                    }

                    break
                }
            }
        }

        return KeyAttemptOutcome(
            lastError = lastError ?: GeminiApiException("Gemini akışı yanıt vermedi.", modelName = SOLE_GEMINI_MODEL),
            fallbackEligibleError = fallbackEligibleError,
            triedBuiltInKeys = triedBuiltIn
        )
    }

    internal fun isFallbackEligibleFailure(throwable: Throwable?): Boolean {
        if (throwable == null) return false
        val apiEx = throwable as? GeminiApiException
        if (apiEx != null) {
            if (apiEx.isModelNotFound || apiEx.isQuotaOrRateLimit || apiEx.isOverloadOrTransient) {
                return true
            }
            if (apiEx.httpCode in setOf(404, 429, 503)) {
                return true
            }
        }
        if (GeminiRestClient.isTimeoutError(throwable)) return true
        val msg = throwable.message.orEmpty()
        return msg.contains("404") ||
            msg.contains("NOT_FOUND", ignoreCase = true) ||
            msg.contains("429") ||
            msg.contains("RESOURCE_EXHAUSTED", ignoreCase = true) ||
            msg.contains("503") ||
            msg.contains("UNAVAILABLE", ignoreCase = true)
    }

    private fun formatError(t: Throwable?): String {
        if (t == null) return "Bilinmeyen hata"
        return (t.localizedMessage ?: t.message ?: t.javaClass.simpleName).take(220)
    }

    private fun buildFailureMessage(lastException: Throwable?, notes: List<String>): String {
        val raw = "${lastException?.message} ${lastException?.cause?.message}".lowercase()
        val detail = notes.joinToString(" | ").take(480)
        val apiEx = lastException as? GeminiApiException
        return when {
            apiEx?.isAuthOrKeyError == true || raw.contains("401") || raw.contains("403") || raw.contains("api_key") || raw.contains("api key not valid") ->
                "API anahtarı doğrulama hatası. Ayarlardan kendi anahtarınızı girebilir veya standart anahtarları kontrol edebilirsiniz. [$detail]"
            apiEx?.isQuotaOrRateLimit == true || raw.contains("429") || raw.contains("quota") ->
                "API kota limiti doldu (429). Kısa süre sonra tekrar deneyin veya yeni bir anahtar ekleyin."
            apiEx?.isOverloadOrTransient == true || raw.contains("503") || raw.contains("unavailable") ->
                "Yapay zeka sunucularında anlık yoğunluk yaşanıyor (503). Lütfen biraz sonra tekrar deneyin."
            else -> lastException?.localizedMessage?.takeIf { it.isNotBlank() }
                ?: "Gemini API bağlantısı kurulamadı."
        }
    }
}
