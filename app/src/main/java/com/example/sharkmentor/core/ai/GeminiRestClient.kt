package com.example.sharkmentor.core.ai

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

data class GeminiChatTurn(
    val role: String,
    val text: String
)

data class AiProviderConnectionResult(
    val success: Boolean,
    val summary: String,
    val details: List<String> = emptyList()
)

data class GeminiModelDiscoveryResult(
    val success: Boolean,
    val models: List<GeminiModelOption> = emptyList(),
    val summary: String
)

enum class GeminiEndpointType(val displayName: String) {
    VERTEX_AI_EXPRESS("Vertex AI Express"),
    GOOGLE_AI_STUDIO("Google AI Studio"),
    FIREBASE_PROXY("Firebase Gemini Proxy")
}

class GeminiApiException(
    message: String,
    val httpCode: Int = 0,
    val apiStatus: String? = null,
    val endpoint: GeminiEndpointType? = null,
    val modelName: String = "",
    val rawBody: String = "",
    val usedEndpointFallback: Boolean = false,
    cause: Throwable? = null
) : RuntimeException(message, cause) {

    val isModelNotFound: Boolean
        get() = httpCode == 404 ||
            apiStatus.equals("NOT_FOUND", ignoreCase = true) ||
            (message?.contains("not found for api version", ignoreCase = true) == true) ||
            (message?.contains("no longer available", ignoreCase = true) == true) ||
            (message?.contains("is not supported", ignoreCase = true) == true)

    val isQuotaOrRateLimit: Boolean
        get() = httpCode == 429 ||
            apiStatus.equals("RESOURCE_EXHAUSTED", ignoreCase = true) ||
            (message?.contains("quota", ignoreCase = true) == true) ||
            (message?.contains("rate_limit", ignoreCase = true) == true) ||
            (message?.contains("rate limit", ignoreCase = true) == true)

    val isAuthOrKeyError: Boolean
        get() = httpCode == 401 ||
            httpCode == 403 ||
            apiStatus.equals("PERMISSION_DENIED", ignoreCase = true) ||
            apiStatus.equals("UNAUTHENTICATED", ignoreCase = true) ||
            rawBody.contains("API_KEY_INVALID", ignoreCase = true) ||
            rawBody.contains("API key not valid", ignoreCase = true) ||
            (message?.contains("unregistered", ignoreCase = true) == true) ||
            (message?.contains("permission_denied", ignoreCase = true) == true)

    val isOverloadOrTransient: Boolean
        get() = httpCode == 503 ||
            httpCode == 502 ||
            httpCode == 504 ||
            httpCode == 500 ||
            apiStatus.equals("UNAVAILABLE", ignoreCase = true) ||
            (message?.contains("overloaded", ignoreCase = true) == true) ||
            (message?.contains("high demand", ignoreCase = true) == true)
}

data class GeminiThinkingConfig(
    val thinkingLevel: String? = null,
    val thinkingBudget: Int? = null
)

/**
 * Generation configuration sent to Gemini REST endpoints.
 * Intentionally excludes `frequencyPenalty` and `presencePenalty` so Gemini 3.x /
 * Vertex Express models never reject requests with unsupported penalty parameters.
 */
data class GeminiGenerationConfig(
    val temperature: Float? = null,
    val maxOutputTokens: Int? = null,
    val topP: Float? = null,
    val topK: Int? = null,
    val responseMimeType: String? = null,
    val thinkingConfig: GeminiThinkingConfig? = null
)

data class GeminiSchemaProperty(
    val type: String,
    val description: String = ""
)

data class GeminiFunctionDeclaration(
    val name: String,
    val description: String,
    val parameters: Map<String, GeminiSchemaProperty> = emptyMap(),
    val requiredParameters: List<String> = emptyList()
)

data class GeminiFunctionCall(
    val name: String,
    val args: Map<String, String> = emptyMap(),
    val thoughtSignature: String? = null
)

sealed class GeminiPart {
    data class Text(
        val text: String,
        val isThought: Boolean = false,
        val thoughtSignature: String? = null
    ) : GeminiPart()

    data class InlineData(
        val mimeType: String,
        val base64Data: String,
        val thoughtSignature: String? = null
    ) : GeminiPart()

    data class FunctionCall(
        val functionCall: GeminiFunctionCall,
        val thoughtSignature: String? = functionCall.thoughtSignature
    ) : GeminiPart()

    data class FunctionResponse(
        val name: String,
        val response: Map<String, Any?>,
        val thoughtSignature: String? = null
    ) : GeminiPart()
}

data class GeminiContent(
    val role: String = "user",
    val parts: List<GeminiPart>
) {
    companion object {
        fun userText(text: String) = GeminiContent(
            role = "user",
            parts = listOf(GeminiPart.Text(text))
        )

        fun modelText(text: String, thoughtSignature: String? = null) = GeminiContent(
            role = "model",
            parts = listOf(GeminiPart.Text(text, isThought = false, thoughtSignature = thoughtSignature))
        )
    }
}

data class GeminiResponse(
    val text: String,
    val thinkingText: String = "",
    val functionCalls: List<GeminiFunctionCall> = emptyList(),
    val parts: List<GeminiPart> = emptyList(),
    val finishReason: String? = null,
    val modelName: String = "",
    val endpoint: GeminiEndpointType? = null,
    val usedEndpointFallback: Boolean = false,
    val totalTokensUsed: Int = 0
)

data class GeminiModelInfo(
    val id: String,
    val displayName: String = id,
    val description: String = "",
    val supportedMethods: List<String> = emptyList()
)

data class GeminiModelListResult(
    val models: List<GeminiModelInfo>,
    val endpoint: GeminiEndpointType,
    val httpCode: Int,
    val fromFallbackList: Boolean = false,
    val message: String = ""
)

data class GeminiConnectionTestResult(
    val slotLabel: String,
    val maskedKey: String,
    val isSuccess: Boolean,
    val endpoint: GeminiEndpointType,
    val usedEndpointFallback: Boolean = false,
    val modelName: String,
    val httpCode: Int,
    val message: String,
    val latencyMs: Long = 0L
)

/**
 * Pure OkHttp + Gson REST and SSE client for Gemini 2.x / 3.x across:
 * - Vertex AI Express Mode (`aiplatform.googleapis.com/v1/publishers/google/models/...`) for `AQ.` keys
 * - Google AI Studio (`generativelanguage.googleapis.com/v1beta/models/...`) for `AIza` keys & fallback
 *
 * Uses zero unmocked Android framework classes in core serialization/parsing so all protocol logic
 * executes cleanly in pure-JVM unit tests as well as on Android 9–16 devices.
 */
object GeminiRestClient {

    private const val TAG = "GeminiRestClient"
    const val MODEL_ID = "gemini-3.6-flash"
    const val FALLBACK_MODEL_ID = "gemini-3.5-flash-lite"
    val STANDARD_ALLOWED_MODELS = listOf(MODEL_ID, FALLBACK_MODEL_ID)
    private const val PROXY_REGION = "us-central1"
    private const val PROXY_FUNCTION_NAME = "geminiProxy"
    const val PROXY_MODEL_HEADER = "X-Shark-Model"
    private const val VERTEX_EXPRESS_BASE = "https://aiplatform.googleapis.com/v1/publishers/google/models"
    private const val AI_STUDIO_BASE = "https://generativelanguage.googleapis.com/v1beta/models"

    @Volatile
    var proxyUrlOverride: String? = null

    private val ENDPOINT_FALLBACK_HTTP_CODES = setOf(400, 401, 403, 404)
    private val preferredEndpointByKey = ConcurrentHashMap<String, GeminiEndpointType>()

    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectionPool(ConnectionPool(8, 5, TimeUnit.MINUTES))
            .dispatcher(Dispatcher().apply {
                maxRequests = 24
                maxRequestsPerHost = 12
            })
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    internal fun safeLogW(msg: String, t: Throwable? = null) {
        try {
            if (t != null) Log.w(TAG, msg, t) else Log.w(TAG, msg)
        } catch (_: Throwable) {
            // Pure-JVM unit test fallback
        }
    }

    internal fun safeLogD(msg: String) {
        try {
            Log.d(TAG, msg)
        } catch (_: Throwable) {
            // Pure-JVM unit test fallback
        }
    }

    fun sanitizeApiKey(apiKey: String?): String {
        return apiKey.orEmpty()
            .trim()
            .replace("\"", "")
            .replace("'", "")
            .trim()
    }

    fun sanitizeModelName(modelName: String): String {
        return modelName.trim()
            .removePrefix("publishers/google/models/")
            .removePrefix("models/")
            .removePrefix("/")
            .trim()
    }

    fun isVertexExpressKey(apiKey: String): Boolean {
        return sanitizeApiKey(apiKey).startsWith("AQ.")
    }

    internal fun recordEndpointSuccess(apiKey: String, endpoint: GeminiEndpointType) {
        val cleanKey = sanitizeApiKey(apiKey)
        if (cleanKey.isNotBlank()) {
            preferredEndpointByKey[cleanKey] = endpoint
        }
    }

    internal fun clearEndpointCacheForTesting() {
        preferredEndpointByKey.clear()
        proxyUrlOverride = null
    }

    /**
     * Resolves the Cloud Functions `geminiProxy` HTTPS URL from [proxyUrlOverride] or Firebase [projectId].
     */
    fun resolveProxyUrl(projectId: String?): String? {
        val override = proxyUrlOverride?.trim()
            ?.takeIf { it.startsWith("https://") || it.startsWith("http://") }
        if (override != null) return override
        val cleanProject = projectId?.trim().orEmpty()
        if (cleanProject.isBlank()) return null
        return "https://$PROXY_REGION-$cleanProject.cloudfunctions.net/$PROXY_FUNCTION_NAME"
    }

    internal fun mergeEndpointErrors(
        primary: GeminiApiException,
        fallback: GeminiApiException,
        modelName: String
    ): GeminiApiException {
        val primaryLabel = primary.endpoint?.displayName ?: "Primary"
        val fallbackLabel = fallback.endpoint?.displayName ?: "Fallback"
        val combinedMessage =
            "$primaryLabel (${primary.httpCode}): ${primary.message.orEmpty().take(120)} | " +
                "$fallbackLabel (${fallback.httpCode}): ${fallback.message.orEmpty().take(120)}"

        // Preserve 404 NOT_FOUND whenever EITHER endpoint recognized the key and reported
        // the model unavailable, so GeminiAiFactory can trigger controlled model fallback.
        val dominant = when {
            fallback.isModelNotFound && !primary.isModelNotFound -> fallback
            primary.isModelNotFound && !fallback.isModelNotFound -> primary
            fallback.isQuotaOrRateLimit || fallback.isOverloadOrTransient -> fallback
            else -> primary
        }

        return GeminiApiException(
            message = combinedMessage,
            httpCode = dominant.httpCode,
            apiStatus = dominant.apiStatus,
            endpoint = dominant.endpoint,
            modelName = modelName,
            usedEndpointFallback = true,
            rawBody = dominant.rawBody
        )
    }

    fun resolveEndpointsForKey(apiKey: String): List<GeminiEndpointType> {
        val cleanKey = sanitizeApiKey(apiKey)
        if (!isVertexExpressKey(cleanKey)) {
            return listOf(GeminiEndpointType.GOOGLE_AI_STUDIO)
        }
        val preferred = preferredEndpointByKey[cleanKey]
        return if (preferred == GeminiEndpointType.GOOGLE_AI_STUDIO) {
            listOf(GeminiEndpointType.GOOGLE_AI_STUDIO, GeminiEndpointType.VERTEX_AI_EXPRESS)
        } else {
            listOf(GeminiEndpointType.VERTEX_AI_EXPRESS, GeminiEndpointType.GOOGLE_AI_STUDIO)
        }
    }

    fun buildGenerateUrl(
        endpoint: GeminiEndpointType,
        modelName: String,
        streaming: Boolean
    ): String {
        val cleanModel = sanitizeModelName(modelName).ifBlank { MODEL_ID }
        val suffix = if (streaming) "streamGenerateContent?alt=sse" else "generateContent"
        return when (endpoint) {
            GeminiEndpointType.VERTEX_AI_EXPRESS -> "$VERTEX_EXPRESS_BASE/$cleanModel:$suffix"
            GeminiEndpointType.GOOGLE_AI_STUDIO -> "$AI_STUDIO_BASE/$cleanModel:$suffix"
            GeminiEndpointType.FIREBASE_PROXY ->
                proxyUrlOverride?.trim()?.takeIf { it.isNotBlank() } ?: "$AI_STUDIO_BASE/$cleanModel:$suffix"
        }
    }

    fun appendApiKeyQueryParam(baseUrl: String, apiKey: String): String {
        val cleanKey = sanitizeApiKey(apiKey)
        if (cleanKey.isBlank()) return baseUrl
        val separator = if (baseUrl.contains("?")) "&" else "?"
        val encoded = java.net.URLEncoder.encode(cleanKey, "UTF-8")
        return "$baseUrl${separator}key=$encoded"
    }

    fun buildListModelsUrl(pageSize: Int = 100): String {
        return "$AI_STUDIO_BASE?pageSize=$pageSize"
    }

    /**
     * Normalizes conversation history before dispatching to Gemini REST:
     * - Maps assistant/ai/model -> "model", everything else -> "user"
     * - Filters blank text parts & internal thought-only parts
     * - Drops empty turns
     * - Merges consecutive same-role turns
     * - Drops leading and trailing "model" turns so history starts and ends on "user"
     */
    fun normalizeContents(contents: List<GeminiContent>): List<GeminiContent> {
        val cleaned = mutableListOf<GeminiContent>()

        for (turn in contents) {
            val normalizedRole = when (turn.role.trim().lowercase()) {
                "model", "assistant", "ai" -> "model"
                else -> "user"
            }
            val validParts = turn.parts.filter { part ->
                when (part) {
                    is GeminiPart.Text -> !part.isThought && part.text.isNotBlank()
                    is GeminiPart.InlineData -> part.base64Data.isNotBlank()
                    is GeminiPart.FunctionCall -> part.functionCall.name.isNotBlank()
                    is GeminiPart.FunctionResponse -> part.name.isNotBlank()
                }
            }
            if (validParts.isEmpty()) continue

            val previous = cleaned.lastOrNull()
            if (previous != null && previous.role == normalizedRole) {
                cleaned[cleaned.lastIndex] = previous.copy(parts = previous.parts + validParts)
            } else {
                cleaned.add(GeminiContent(role = normalizedRole, parts = validParts))
            }
        }

        while (cleaned.isNotEmpty() && cleaned.first().role == "model") {
            cleaned.removeAt(0)
        }
        while (cleaned.isNotEmpty() && cleaned.last().role == "model") {
            cleaned.removeAt(cleaned.lastIndex)
        }

        return cleaned
    }

    /**
     * Builds deterministic Gemini REST request JSON using Gson tree objects.
     * Guarantees `frequencyPenalty` and `presencePenalty` are NEVER included.
     */
    fun buildRequestBody(
        contents: List<GeminiContent>,
        systemInstruction: String = "",
        generationConfig: GeminiGenerationConfig? = null,
        tools: List<GeminiFunctionDeclaration> = emptyList()
    ): String {
        val normalized = normalizeContents(contents)
        if (normalized.isEmpty()) {
            throw IllegalArgumentException("Gönderilecek geçerli bir kullanıcı içeriği bulunamadı.")
        }

        val root = JsonObject()

        val contentsArray = JsonArray()
        for (turn in normalized) {
            val turnObj = JsonObject()
            turnObj.addProperty("role", turn.role)

            val partsArray = JsonArray()
            for (part in turn.parts) {
                val partObj = JsonObject()
                when (part) {
                    is GeminiPart.Text -> {
                        partObj.addProperty("text", part.text)
                        if (!part.thoughtSignature.isNullOrBlank()) {
                            partObj.addProperty("thoughtSignature", part.thoughtSignature)
                        }
                    }

                    is GeminiPart.InlineData -> {
                        val inlineObj = JsonObject()
                        inlineObj.addProperty("mimeType", part.mimeType)
                        inlineObj.addProperty("data", part.base64Data)
                        partObj.add("inlineData", inlineObj)
                        if (!part.thoughtSignature.isNullOrBlank()) {
                            partObj.addProperty("thoughtSignature", part.thoughtSignature)
                        }
                    }

                    is GeminiPart.FunctionCall -> {
                        val fcObj = JsonObject()
                        fcObj.addProperty("name", part.functionCall.name)
                        val argsObj = JsonObject()
                        for ((k, v) in part.functionCall.args) {
                            argsObj.addProperty(k, v)
                        }
                        fcObj.add("args", argsObj)
                        partObj.add("functionCall", fcObj)

                        val sig = part.thoughtSignature ?: part.functionCall.thoughtSignature
                        if (!sig.isNullOrBlank()) {
                            partObj.addProperty("thoughtSignature", sig)
                        }
                    }

                    is GeminiPart.FunctionResponse -> {
                        val frObj = JsonObject()
                        frObj.addProperty("name", part.name)
                        frObj.add("response", gson.toJsonTree(part.response))
                        partObj.add("functionResponse", frObj)
                        if (!part.thoughtSignature.isNullOrBlank()) {
                            partObj.addProperty("thoughtSignature", part.thoughtSignature)
                        }
                    }
                }
                partsArray.add(partObj)
            }
            turnObj.add("parts", partsArray)
            contentsArray.add(turnObj)
        }
        root.add("contents", contentsArray)

        if (systemInstruction.isNotBlank()) {
            val sysObj = JsonObject()
            val sysParts = JsonArray()
            val textPart = JsonObject().apply {
                addProperty("text", systemInstruction.trim())
            }
            sysParts.add(textPart)
            sysObj.add("parts", sysParts)
            root.add("systemInstruction", sysObj)
        }

        if (tools.isNotEmpty()) {
            val toolsArray = JsonArray()
            val toolContainer = JsonObject()
            val declsArray = JsonArray()

            for (decl in tools) {
                val declObj = JsonObject()
                declObj.addProperty("name", decl.name)
                declObj.addProperty("description", decl.description)

                if (decl.parameters.isNotEmpty()) {
                    val paramsObj = JsonObject()
                    paramsObj.addProperty("type", "OBJECT")
                    val propsObj = JsonObject()
                    for ((paramName, propSpec) in decl.parameters) {
                        val pObj = JsonObject()
                        pObj.addProperty("type", propSpec.type.uppercase())
                        if (propSpec.description.isNotBlank()) {
                            pObj.addProperty("description", propSpec.description)
                        }
                        propsObj.add(paramName, pObj)
                    }
                    paramsObj.add("properties", propsObj)
                    if (decl.requiredParameters.isNotEmpty()) {
                        val reqArray = JsonArray()
                        decl.requiredParameters.forEach { reqArray.add(it) }
                        paramsObj.add("required", reqArray)
                    }
                    declObj.add("parameters", paramsObj)
                }

                declsArray.add(declObj)
            }

            toolContainer.add("functionDeclarations", declsArray)
            toolsArray.add(toolContainer)
            root.add("tools", toolsArray)
        }

        if (generationConfig != null) {
            val cfgObj = JsonObject()
            generationConfig.temperature?.let { cfgObj.addProperty("temperature", it) }
            generationConfig.topP?.let { cfgObj.addProperty("topP", it) }
            generationConfig.topK?.let { cfgObj.addProperty("topK", it) }
            generationConfig.maxOutputTokens?.let { cfgObj.addProperty("maxOutputTokens", it) }
            generationConfig.responseMimeType?.takeIf { it.isNotBlank() }?.let {
                cfgObj.addProperty("responseMimeType", it)
            }
            generationConfig.thinkingConfig?.let { tc ->
                val tcObj = JsonObject()
                tc.thinkingLevel?.takeIf { it.isNotBlank() }?.let {
                    tcObj.addProperty("thinkingLevel", it)
                }
                tc.thinkingBudget?.let {
                    tcObj.addProperty("thinkingBudget", it)
                }
                if (tcObj.size() > 0) {
                    cfgObj.add("thinkingConfig", tcObj)
                }
            }
            if (cfgObj.size() > 0) {
                root.add("generationConfig", cfgObj)
            }
        }

        return gson.toJson(root)
    }

    /**
     * Builds the JSON payload expected by the Firebase Cloud Functions `geminiProxy` endpoint.
     * Enforces the standard-mode model whitelist (`gemini-3.6-flash` / `gemini-3.5-flash-lite`).
     */
    fun buildProxyRequestBody(
        modelName: String = MODEL_ID,
        contents: List<GeminiContent>,
        systemInstruction: String = "",
        generationConfig: GeminiGenerationConfig? = null,
        tools: List<GeminiFunctionDeclaration> = emptyList(),
        stream: Boolean = false
    ): String {
        val cleanRequested = sanitizeModelName(modelName)
        val effectiveModel = if (cleanRequested in STANDARD_ALLOWED_MODELS) cleanRequested else MODEL_ID
        val baseJson = buildRequestBody(
            contents = contents,
            systemInstruction = systemInstruction,
            generationConfig = generationConfig,
            tools = tools
        )
        val root = JsonParser.parseString(baseJson).asJsonObject
        root.addProperty("model", effectiveModel)
        root.addProperty("stream", stream)
        return gson.toJson(root)
    }

    internal fun resolveLowLatencyConfig(
        cleanModel: String,
        base: GeminiGenerationConfig?
    ): GeminiGenerationConfig? {
        // Do not auto-inject thinkingConfig: Vertex AI Express v1 rejects thinkingLevel/thinkingBudget
        // on publisher models with generic HTTP 400 "Request contains an invalid argument."
        return base
    }

    internal fun isUnsupportedThinkingConfigError(httpCode: Int, rawBody: String): Boolean {
        if (httpCode != 400) return false
        val lower = rawBody.lowercase()
        return lower.contains("thinkingconfig") ||
            lower.contains("thinkinglevel") ||
            lower.contains("thinkingbudget") ||
            lower.contains("thinking_config") ||
            lower.contains("thinking_level") ||
            lower.contains("thinking_budget") ||
            lower.contains("invalid argument") ||
            lower.contains("invalid_argument")
    }

    /**
     * Parses a single `generateContent` or SSE JSON payload safely via Gson tree nodes.
     * - Ignores unknown Gemini 2.x/3.x fields cleanly.
     * - Excludes `thought == true` parts from [GeminiResponse.text] and places them in [GeminiResponse.thinkingText].
     * - Preserves `thoughtSignature` across text & functionCall parts.
     */
    fun parseGenerateContentJson(
        jsonString: String,
        modelName: String = "",
        endpoint: GeminiEndpointType? = null,
        usedEndpointFallback: Boolean = false
    ): GeminiResponse {
        val trimmed = jsonString.trim()
        if (trimmed.isEmpty()) {
            return GeminiResponse(
                text = "",
                modelName = modelName,
                endpoint = endpoint,
                usedEndpointFallback = usedEndpointFallback,
                totalTokensUsed = maxTokensUsed
            )
        }

        val parsedElem = JsonParser.parseString(trimmed)
        val rootObj = when {
            parsedElem.isJsonObject -> parsedElem.asJsonObject
            parsedElem.isJsonArray && parsedElem.asJsonArray.size() > 0 && parsedElem.asJsonArray[0].isJsonObject ->
                parsedElem.asJsonArray[0].asJsonObject
            else -> return GeminiResponse(
                text = "",
                modelName = modelName,
                endpoint = endpoint,
                usedEndpointFallback = usedEndpointFallback
            )
        }

        if (rootObj.has("error") && rootObj.get("error").isJsonObject) {
            val errObj = rootObj.getAsJsonObject("error")
            val code = errObj.get("code")?.takeIf { !it.isJsonNull }?.asInt ?: 0
            val status = errObj.getAsStringOrNull("status")
            val msg = errObj.getAsStringOrNull("message") ?: "Gemini API hatası ($code)"
            throw GeminiApiException(
                message = msg,
                httpCode = code,
                apiStatus = status,
                endpoint = endpoint,
                modelName = modelName,
                rawBody = trimmed,
                usedEndpointFallback = usedEndpointFallback
            )
        }

        val candidates = rootObj.getAsJsonArrayOrNull("candidates")
        if (candidates == null || candidates.size() == 0) {
            val blockReason = rootObj.getAsJsonObjectOrNull("promptFeedback")
                ?.getAsStringOrNull("blockReason")
            return GeminiResponse(
                text = "",
                finishReason = blockReason,
                modelName = modelName,
                endpoint = endpoint,
                usedEndpointFallback = usedEndpointFallback
            )
        }

        val firstCandidate = candidates[0].takeIf { it.isJsonObject }?.asJsonObject
            ?: return GeminiResponse(
                text = "",
                modelName = modelName,
                endpoint = endpoint,
                usedEndpointFallback = usedEndpointFallback
            )

        val finishReason = firstCandidate.getAsStringOrNull("finishReason")
            ?: firstCandidate.getAsStringOrNull("finish_reason")

        val contentObj = firstCandidate.getAsJsonObjectOrNull("content")
        val partsArray = contentObj?.getAsJsonArrayOrNull("parts")

        val textBuilder = StringBuilder()
        val thinkingBuilder = StringBuilder()
        val functionCalls = mutableListOf<GeminiFunctionCall>()
        val parsedParts = mutableListOf<GeminiPart>()

        if (partsArray != null) {
            for (partElem in partsArray) {
                if (!partElem.isJsonObject) continue
                val partObj = partElem.asJsonObject

                val isThought = partObj.getAsBooleanOrNull("thought") == true
                val thoughtSig = partObj.getAsStringOrNull("thoughtSignature")
                    ?: partObj.getAsStringOrNull("thought_signature")

                val rawText = partObj.getAsStringOrNull("text")
                if (rawText != null) {
                    if (isThought) {
                        thinkingBuilder.append(rawText)
                    } else {
                        textBuilder.append(rawText)
                    }
                    parsedParts.add(
                        GeminiPart.Text(
                            text = rawText,
                            isThought = isThought,
                            thoughtSignature = thoughtSig
                        )
                    )
                }

                val fcObj = partObj.getAsJsonObjectOrNull("functionCall")
                    ?: partObj.getAsJsonObjectOrNull("function_call")
                if (fcObj != null) {
                    val fcName = fcObj.getAsStringOrNull("name").orEmpty()
                    if (fcName.isNotBlank()) {
                        val argsMap = linkedMapOf<String, String>()
                        val argsObj = fcObj.getAsJsonObjectOrNull("args")
                        if (argsObj != null) {
                            for ((k, v) in argsObj.entrySet()) {
                                if (!v.isJsonNull) {
                                    argsMap[k] = if (v.isJsonPrimitive) v.asString else gson.toJson(v)
                                }
                            }
                        }
                        val effectiveSig = thoughtSig
                            ?: fcObj.getAsStringOrNull("thoughtSignature")
                            ?: fcObj.getAsStringOrNull("thought_signature")
                        val fc = GeminiFunctionCall(
                            name = fcName,
                            args = argsMap,
                            thoughtSignature = effectiveSig
                        )
                        functionCalls.add(fc)
                        parsedParts.add(GeminiPart.FunctionCall(fc, effectiveSig))
                    }
                }
            }
        }

        val usageObj = root.getAsJsonObjectOrNull("usageMetadata")
        val totalTok = usageObj?.get("totalTokenCount")?.takeIf { it.isJsonPrimitive }?.asInt
            ?: ((usageObj?.get("promptTokenCount")?.takeIf { it.isJsonPrimitive }?.asInt ?: 0) +
                (usageObj?.get("candidatesTokenCount")?.takeIf { it.isJsonPrimitive }?.asInt ?: 0))

        return GeminiResponse(
            text = textBuilder.toString(),
            thinkingText = thinkingBuilder.toString(),
            functionCalls = functionCalls,
            parts = parsedParts,
            finishReason = finishReason,
            modelName = modelName,
            endpoint = endpoint,
            usedEndpointFallback = usedEndpointFallback,
            totalTokensUsed = totalTok
        )
    }

    /**
     * Stateful SSE event accumulator supporting single-line `data: {...}`, back-to-back `data:` lines,
     * multi-line JSON SSE payloads, and `[DONE]` termination.
     */
    internal class SseStreamAccumulator(
        private val modelName: String,
        private val endpoint: GeminiEndpointType?,
        private val usedEndpointFallback: Boolean,
        private val onPartialText: (String) -> Unit,
        private val onThinkingText: ((String) -> Unit)? = null
    ) {
        private val eventBuffer = StringBuilder()
        private val accumulatedText = StringBuilder()
        private val accumulatedThinking = StringBuilder()
        private val collectedCalls = mutableListOf<GeminiFunctionCall>()
        private val collectedParts = mutableListOf<GeminiPart>()
        private var lastFinishReason: String? = null
        private var maxTokensUsed: Int = 0
        private var doneReached = false

        fun processLine(rawLine: String): Boolean {
            if (doneReached) return false
            val line = rawLine.trimEnd('\r')
            val trimmed = line.trim()

            if (trimmed.isEmpty()) {
                flushEvent()
                return true
            }

            if (trimmed.startsWith("data:")) {
                val payload = trimmed.substring(5).trimStart()
                if (payload == "[DONE]") {
                    flushEvent()
                    doneReached = true
                    return false
                }
                if (payload.isEmpty()) return true

                if (eventBuffer.isNotEmpty() &&
                    (payload.startsWith("{") || payload.startsWith("[")) &&
                    isLikelyCompleteJson(eventBuffer.toString())
                ) {
                    flushEvent()
                }

                if (eventBuffer.isNotEmpty()) {
                    eventBuffer.append('\n')
                }
                eventBuffer.append(payload)
                return true
            }

            return true
        }

        fun finish(): GeminiResponse {
            flushEvent()
            return GeminiResponse(
                text = accumulatedText.toString(),
                thinkingText = accumulatedThinking.toString(),
                functionCalls = collectedCalls.toList(),
                parts = collectedParts.toList(),
                finishReason = lastFinishReason,
                modelName = modelName,
                endpoint = endpoint,
                usedEndpointFallback = usedEndpointFallback
            )
        }

        private fun flushEvent() {
            val payload = eventBuffer.toString().trim()
            eventBuffer.setLength(0)
            if (payload.isEmpty() || payload == "[DONE]") return

            val chunk = runCatching {
                parseGenerateContentJson(
                    jsonString = payload,
                    modelName = modelName,
                    endpoint = endpoint,
                    usedEndpointFallback = usedEndpointFallback
                )
            }.getOrElse { err ->
                if (err is GeminiApiException) throw err
                safeLogW("Skipping malformed SSE chunk: ${err.message}")
                return
            }

            if (chunk.thinkingText.isNotEmpty()) {
                accumulatedThinking.append(chunk.thinkingText)
                onThinkingText?.invoke(accumulatedThinking.toString())
            }
            if (chunk.text.isNotEmpty()) {
                accumulatedText.append(chunk.text)
                onPartialText(accumulatedText.toString())
            }
            if (chunk.totalTokensUsed > maxTokensUsed) maxTokensUsed = chunk.totalTokensUsed
            if (chunk.functionCalls.isNotEmpty()) {
                collectedCalls.addAll(chunk.functionCalls)
            }
            if (chunk.parts.isNotEmpty()) {
                collectedParts.addAll(chunk.parts)
            }
            if (!chunk.finishReason.isNullOrBlank()) {
                lastFinishReason = chunk.finishReason
            }
        }

        private fun isLikelyCompleteJson(candidate: String): Boolean {
            val t = candidate.trim()
            return (t.startsWith("{") && t.endsWith("}")) ||
                (t.startsWith("[") && t.endsWith("]"))
        }
    }

    /**
     * Pure-JVM helper for parsing SSE lines synchronously in unit tests or offline buffers.
     */
    fun parseSseLines(
        lines: Sequence<String>,
        modelName: String = "",
        endpoint: GeminiEndpointType? = null,
        usedEndpointFallback: Boolean = false,
        onPartialText: (String) -> Unit = {},
        onThinkingText: ((String) -> Unit)? = null
    ): GeminiResponse {
        val accumulator = SseStreamAccumulator(
            modelName = modelName,
            endpoint = endpoint,
            usedEndpointFallback = usedEndpointFallback,
            onPartialText = onPartialText,
            onThinkingText = onThinkingText
        )
        for (line in lines) {
            if (!accumulator.processLine(line)) break
        }
        return accumulator.finish()
    }

    /**
     * Executes non-streaming `generateContent` with key-based endpoint selection & immediate fallback:
     * - `AQ.` key: tries Vertex AI Express first; on HTTP 400/401/403/404 falls back immediately (0ms) to AI Studio.
     * - `AIza` / other key: directly calls Google AI Studio.
     */
    suspend fun generateContent(
        apiKey: String,
        modelName: String,
        contents: List<GeminiContent>,
        systemInstruction: String = "",
        generationConfig: GeminiGenerationConfig? = null,
        tools: List<GeminiFunctionDeclaration> = emptyList()
    ): GeminiResponse = withContext(Dispatchers.IO) {
        val cleanKey = sanitizeApiKey(apiKey)
        if (cleanKey.isBlank() || cleanKey.equals("null", ignoreCase = true)) {
            throw GeminiApiException(
                message = "Gemini API anahtarı eksik veya boş.",
                httpCode = 401,
                apiStatus = "UNAUTHENTICATED",
                modelName = modelName
            )
        }

        val cleanModel = sanitizeModelName(modelName).ifBlank { MODEL_ID }
        val effectiveConfig = resolveLowLatencyConfig(cleanModel, generationConfig)
        val hasThinking = effectiveConfig?.thinkingConfig != null
        val primaryRequestJson = buildRequestBody(
            contents = contents,
            systemInstruction = systemInstruction,
            generationConfig = effectiveConfig,
            tools = tools
        )
        val fallbackRequestJson by lazy {
            buildRequestBody(
                contents = contents,
                systemInstruction = systemInstruction,
                generationConfig = effectiveConfig?.copy(thinkingConfig = null),
                tools = tools
            )
        }

        val endpoints = resolveEndpointsForKey(cleanKey)
        var firstEndpointError: GeminiApiException? = null
        var lastError: GeminiApiException? = null

        for ((idx, endpoint) in endpoints.withIndex()) {
            val isFallbackAttempt = idx > 0
            val url = buildGenerateUrl(endpoint, cleanModel, streaming = false)
            var stripThinkingAndRetry = false

            for (cfgAttempt in 0..1) {
                if (cfgAttempt == 1 && !stripThinkingAndRetry) break
                val bodyJson = if (cfgAttempt == 0) primaryRequestJson else fallbackRequestJson
                val request = Request.Builder()
                    .url(url)
                    .addHeader("x-goog-api-key", cleanKey)
                    .addHeader("Content-Type", "application/json")
                    .post(bodyJson.toRequestBody(jsonMediaType))
                    .build()

                try {
                    okHttpClient.newCall(request).execute().use { response ->
                        val rawBody = response.body?.string().orEmpty()
                        if (!response.isSuccessful) {
                            if (cfgAttempt == 0 && hasThinking && isUnsupportedThinkingConfigError(response.code, rawBody)) {
                                safeLogW("Endpoint $endpoint rejected thinkingConfig on $cleanModel; retrying same endpoint without thinkingConfig.")
                                stripThinkingAndRetry = true
                                return@use
                            }
                            val apiEx = buildHttpApiException(
                                httpCode = response.code,
                                rawBody = rawBody,
                                endpoint = endpoint,
                                modelName = cleanModel,
                                usedFallback = isFallbackAttempt
                            )
                            if (idx == 0) {
                                firstEndpointError = apiEx
                            }
                            val effectiveError = if (isFallbackAttempt && firstEndpointError != null) {
                                mergeEndpointErrors(firstEndpointError!!, apiEx, cleanModel)
                            } else {
                                apiEx
                            }
                            lastError = effectiveError
                            val canFallback = idx < endpoints.lastIndex && response.code in ENDPOINT_FALLBACK_HTTP_CODES
                            if (canFallback) {
                                safeLogW("Endpoint $endpoint HTTP ${response.code} on $cleanModel; falling back to ${endpoints[idx + 1]} immediately.")
                                return@use
                            }
                            throw effectiveError
                        }

                        val parsed = parseGenerateContentJson(
                            jsonString = rawBody,
                            modelName = cleanModel,
                            endpoint = endpoint,
                            usedEndpointFallback = isFallbackAttempt
                        )
                        recordEndpointSuccess(cleanKey, endpoint)
                        return@withContext parsed
                    }
                } catch (ce: CancellationException) {
                    throw ce
                } catch (apiEx: GeminiApiException) {
                    if (apiEx === lastError) {
                        throw apiEx
                    }
                    if (cfgAttempt == 0 && hasThinking && isUnsupportedThinkingConfigError(apiEx.httpCode, apiEx.rawBody)) {
                        safeLogW("Endpoint $endpoint rejected thinkingConfig on $cleanModel; retrying same endpoint without thinkingConfig.")
                        stripThinkingAndRetry = true
                        continue
                    }
                    if (idx == 0) {
                        firstEndpointError = apiEx
                    }
                    val effectiveError = if (isFallbackAttempt && firstEndpointError != null) {
                        mergeEndpointErrors(firstEndpointError!!, apiEx, cleanModel)
                    } else {
                        apiEx
                    }
                    lastError = effectiveError
                    val canFallback = idx < endpoints.lastIndex && apiEx.httpCode in ENDPOINT_FALLBACK_HTTP_CODES
                    if (!canFallback) {
                        throw effectiveError
                    }
                    break
                }
            }
        }

        throw lastError ?: GeminiApiException(
            message = "Gemini isteği tamamlanamadı ($cleanModel).",
            modelName = cleanModel
        )
    }

    /**
     * Executes streaming `streamGenerateContent?alt=sse` and invokes [onPartialText] with accumulated
     * non-thought text as each SSE event arrives.
     */
    suspend fun streamGenerateContent(
        apiKey: String,
        modelName: String,
        contents: List<GeminiContent>,
        systemInstruction: String = "",
        generationConfig: GeminiGenerationConfig? = null,
        tools: List<GeminiFunctionDeclaration> = emptyList(),
        onPartialText: (String) -> Unit = {},
        onThinkingText: ((String) -> Unit)? = null
    ): GeminiResponse = withContext(Dispatchers.IO) {
        val cleanKey = sanitizeApiKey(apiKey)
        if (cleanKey.isBlank() || cleanKey.equals("null", ignoreCase = true)) {
            throw GeminiApiException(
                message = "Gemini API anahtarı eksik veya boş.",
                httpCode = 401,
                apiStatus = "UNAUTHENTICATED",
                modelName = modelName
            )
        }

        val cleanModel = sanitizeModelName(modelName).ifBlank { MODEL_ID }
        val effectiveConfig = resolveLowLatencyConfig(cleanModel, generationConfig)
        val hasThinking = effectiveConfig?.thinkingConfig != null
        val primaryRequestJson = buildRequestBody(
            contents = contents,
            systemInstruction = systemInstruction,
            generationConfig = effectiveConfig,
            tools = tools
        )
        val fallbackRequestJson by lazy {
            buildRequestBody(
                contents = contents,
                systemInstruction = systemInstruction,
                generationConfig = effectiveConfig?.copy(thinkingConfig = null),
                tools = tools
            )
        }

        val endpoints = resolveEndpointsForKey(cleanKey)
        var firstEndpointError: GeminiApiException? = null
        var lastError: GeminiApiException? = null

        for ((idx, endpoint) in endpoints.withIndex()) {
            val isFallbackAttempt = idx > 0
            val url = buildGenerateUrl(endpoint, cleanModel, streaming = true)
            var stripThinkingAndRetry = false

            for (cfgAttempt in 0..1) {
                if (cfgAttempt == 1 && !stripThinkingAndRetry) break
                val bodyJson = if (cfgAttempt == 0) primaryRequestJson else fallbackRequestJson
                val request = Request.Builder()
                    .url(url)
                    .addHeader("x-goog-api-key", cleanKey)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Accept", "text/event-stream")
                    .post(bodyJson.toRequestBody(jsonMediaType))
                    .build()

                var emittedCharsCount = 0
                try {
                    okHttpClient.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            val rawBody = response.body?.string().orEmpty()
                            if (cfgAttempt == 0 && hasThinking && isUnsupportedThinkingConfigError(response.code, rawBody)) {
                                safeLogW("SSE Endpoint $endpoint rejected thinkingConfig on $cleanModel; retrying same endpoint without thinkingConfig.")
                                stripThinkingAndRetry = true
                                return@use
                            }
                            val apiEx = buildHttpApiException(
                                httpCode = response.code,
                                rawBody = rawBody,
                                endpoint = endpoint,
                                modelName = cleanModel,
                                usedFallback = isFallbackAttempt
                            )
                            if (idx == 0) {
                                firstEndpointError = apiEx
                            }
                            val effectiveError = if (isFallbackAttempt && firstEndpointError != null) {
                                mergeEndpointErrors(firstEndpointError!!, apiEx, cleanModel)
                            } else {
                                apiEx
                            }
                            lastError = effectiveError
                            val canFallback = idx < endpoints.lastIndex && response.code in ENDPOINT_FALLBACK_HTTP_CODES
                            if (canFallback) {
                                safeLogW("SSE Endpoint $endpoint HTTP ${response.code} on $cleanModel; falling back to ${endpoints[idx + 1]} immediately.")
                                return@use
                            }
                            throw effectiveError
                        }

                        val body = response.body ?: throw GeminiApiException(
                            message = "Boş akış yanıtı alındı ($cleanModel).",
                            httpCode = response.code,
                            endpoint = endpoint,
                            modelName = cleanModel,
                            usedEndpointFallback = isFallbackAttempt
                        )

                        val accumulator = SseStreamAccumulator(
                            modelName = cleanModel,
                            endpoint = endpoint,
                            usedEndpointFallback = isFallbackAttempt,
                            onPartialText = { partial ->
                                emittedCharsCount = partial.length
                                onPartialText(partial)
                            },
                            onThinkingText = onThinkingText
                        )

                        body.byteStream().use { inputStream ->
                            BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).use { reader ->
                                var line: String? = reader.readLine()
                                while (line != null) {
                                    if (!accumulator.processLine(line)) break
                                    line = reader.readLine()
                                }
                            }
                        }

                        val finished = accumulator.finish()
                        recordEndpointSuccess(cleanKey, endpoint)
                        return@withContext finished
                    }
                } catch (ce: CancellationException) {
                    throw ce
                } catch (apiEx: GeminiApiException) {
                    if (apiEx === lastError) {
                        throw apiEx
                    }
                    if (cfgAttempt == 0 && hasThinking && emittedCharsCount == 0 &&
                        isUnsupportedThinkingConfigError(apiEx.httpCode, apiEx.rawBody)
                    ) {
                        safeLogW("SSE Endpoint $endpoint rejected thinkingConfig on $cleanModel; retrying same endpoint without thinkingConfig.")
                        stripThinkingAndRetry = true
                        continue
                    }
                    if (idx == 0) {
                        firstEndpointError = apiEx
                    }
                    val effectiveError = if (isFallbackAttempt && firstEndpointError != null) {
                        mergeEndpointErrors(firstEndpointError!!, apiEx, cleanModel)
                    } else {
                        apiEx
                    }
                    lastError = effectiveError
                    val canFallback = idx < endpoints.lastIndex &&
                        emittedCharsCount == 0 &&
                        apiEx.httpCode in ENDPOINT_FALLBACK_HTTP_CODES
                    if (!canFallback) {
                        throw effectiveError
                    }
                    break
                }
            }
        }

        throw lastError ?: GeminiApiException(
            message = "Gemini SSE akışı tamamlanamadı ($cleanModel).",
            modelName = cleanModel
        )
    }

    /**
     * Executes a standard-mode synchronous Gemini request through the Firebase Cloud Functions
     * `geminiProxy` endpoint authenticated with a Firebase Auth ID token.
     */
    suspend fun generateContentViaProxy(
        proxyUrl: String,
        idToken: String,
        modelName: String = MODEL_ID,
        contents: List<GeminiContent>,
        systemInstruction: String = "",
        generationConfig: GeminiGenerationConfig? = null,
        tools: List<GeminiFunctionDeclaration> = emptyList()
    ): GeminiResponse = withContext(Dispatchers.IO) {
        val cleanUrl = proxyUrl.trim()
        val cleanToken = idToken.trim()
        if (cleanUrl.isBlank() || cleanToken.isBlank()) {
            throw GeminiApiException(
                message = "Firebase proxy URL veya kimlik doğrulama token'ı eksik.",
                httpCode = 401,
                apiStatus = "UNAUTHENTICATED",
                endpoint = GeminiEndpointType.FIREBASE_PROXY,
                modelName = modelName
            )
        }

        val cleanModel = sanitizeModelName(modelName).let {
            if (it in STANDARD_ALLOWED_MODELS) it else MODEL_ID
        }
        val effectiveConfig = resolveLowLatencyConfig(cleanModel, generationConfig)
        val bodyJson = buildProxyRequestBody(
            modelName = cleanModel,
            contents = contents,
            systemInstruction = systemInstruction,
            generationConfig = effectiveConfig,
            tools = tools,
            stream = false
        )

        val request = Request.Builder()
            .url(cleanUrl)
            .addHeader("Authorization", "Bearer $cleanToken")
            .addHeader("Content-Type", "application/json")
            .post(bodyJson.toRequestBody(jsonMediaType))
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            val rawBody = response.body?.string().orEmpty()
            val actualModel = response.header(PROXY_MODEL_HEADER)
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: cleanModel
            val usedFallbackModel = actualModel != cleanModel
            if (!response.isSuccessful) {
                throw buildHttpApiException(
                    httpCode = response.code,
                    rawBody = rawBody,
                    endpoint = GeminiEndpointType.FIREBASE_PROXY,
                    modelName = actualModel,
                    usedFallback = usedFallbackModel
                )
            }
            parseGenerateContentJson(
                jsonString = rawBody,
                modelName = actualModel,
                endpoint = GeminiEndpointType.FIREBASE_PROXY,
                usedEndpointFallback = usedFallbackModel
            )
        }
    }

    /**
     * Executes a standard-mode streaming SSE Gemini request through the Firebase Cloud Functions
     * `geminiProxy` endpoint authenticated with a Firebase Auth ID token.
     */
    suspend fun streamGenerateContentViaProxy(
        proxyUrl: String,
        idToken: String,
        modelName: String = MODEL_ID,
        contents: List<GeminiContent>,
        systemInstruction: String = "",
        generationConfig: GeminiGenerationConfig? = null,
        tools: List<GeminiFunctionDeclaration> = emptyList(),
        onPartialText: (String) -> Unit = {},
        onThinkingText: ((String) -> Unit)? = null
    ): GeminiResponse = withContext(Dispatchers.IO) {
        val cleanUrl = proxyUrl.trim()
        val cleanToken = idToken.trim()
        if (cleanUrl.isBlank() || cleanToken.isBlank()) {
            throw GeminiApiException(
                message = "Firebase proxy URL veya kimlik doğrulama token'ı eksik.",
                httpCode = 401,
                apiStatus = "UNAUTHENTICATED",
                endpoint = GeminiEndpointType.FIREBASE_PROXY,
                modelName = modelName
            )
        }

        val cleanModel = sanitizeModelName(modelName).let {
            if (it in STANDARD_ALLOWED_MODELS) it else MODEL_ID
        }
        val effectiveConfig = resolveLowLatencyConfig(cleanModel, generationConfig)
        val bodyJson = buildProxyRequestBody(
            modelName = cleanModel,
            contents = contents,
            systemInstruction = systemInstruction,
            generationConfig = effectiveConfig,
            tools = tools,
            stream = true
        )

        val request = Request.Builder()
            .url(cleanUrl)
            .addHeader("Authorization", "Bearer $cleanToken")
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "text/event-stream")
            .post(bodyJson.toRequestBody(jsonMediaType))
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            val actualModel = response.header(PROXY_MODEL_HEADER)
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: cleanModel
            val usedFallbackModel = actualModel != cleanModel
            if (!response.isSuccessful) {
                val rawBody = response.body?.string().orEmpty()
                throw buildHttpApiException(
                    httpCode = response.code,
                    rawBody = rawBody,
                    endpoint = GeminiEndpointType.FIREBASE_PROXY,
                    modelName = actualModel,
                    usedFallback = usedFallbackModel
                )
            }

            val body = response.body ?: throw GeminiApiException(
                message = "Boş akış yanıtı alındı ($actualModel).",
                httpCode = response.code,
                endpoint = GeminiEndpointType.FIREBASE_PROXY,
                modelName = actualModel,
                usedEndpointFallback = usedFallbackModel
            )

            val accumulator = SseStreamAccumulator(
                modelName = actualModel,
                endpoint = GeminiEndpointType.FIREBASE_PROXY,
                usedEndpointFallback = usedFallbackModel,
                onPartialText = onPartialText,
                onThinkingText = onThinkingText
            )

            body.byteStream().use { inputStream ->
                BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).use { reader ->
                    var line: String? = reader.readLine()
                    while (line != null) {
                        if (!accumulator.processLine(line)) break
                        line = reader.readLine()
                    }
                }
            }

            accumulator.finish()
        }
    }

    /**
     * Fetches selectable Gemini models for custom key configuration.
     * - Queries `GET https://generativelanguage.googleapis.com/v1beta/models?pageSize=100` with `x-goog-api-key`.
     * - If `AQ.` key fails `/models`, gracefully returns recommended Vertex Gemini models list (never empty).
     */
    suspend fun listModels(apiKey: String): GeminiModelListResult = withContext(Dispatchers.IO) {
        val cleanKey = sanitizeApiKey(apiKey)
        val isVertex = isVertexExpressKey(cleanKey)

        if (cleanKey.isBlank()) {
            return@withContext GeminiModelListResult(
                models = emptyList(),
                endpoint = GeminiEndpointType.GOOGLE_AI_STUDIO,
                httpCode = 400,
                fromFallbackList = false,
                message = "API anahtarı boş."
            )
        }

        val request = Request.Builder()
            .url(buildListModelsUrl(100))
            .addHeader("x-goog-api-key", cleanKey)
            .get()
            .build()

        runCatching {
            okHttpClient.newCall(request).execute().use { response ->
                val rawBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val apiEx = buildHttpApiException(
                        httpCode = response.code,
                        rawBody = rawBody,
                        endpoint = GeminiEndpointType.GOOGLE_AI_STUDIO,
                        modelName = "models"
                    )
                    if (isVertex) {
                        return@withContext GeminiModelListResult(
                            models = UserAiPreferences.KNOWN_VERTEX_GEMINI_MODELS.map {
                                GeminiModelInfo(id = it, displayName = it)
                            },
                            endpoint = GeminiEndpointType.VERTEX_AI_EXPRESS,
                            httpCode = response.code,
                            fromFallbackList = true,
                            message = "Vertex Express (/models HTTP ${response.code}); güncel Gemini modelleri yüklendi."
                        )
                    }
                    return@withContext GeminiModelListResult(
                        models = emptyList(),
                        endpoint = GeminiEndpointType.GOOGLE_AI_STUDIO,
                        httpCode = response.code,
                        fromFallbackList = false,
                        message = "HTTP ${response.code}: ${apiEx.message}"
                    )
                }

                val parsedModels = parseModelsListJson(rawBody)
                if (parsedModels.isEmpty() && isVertex) {
                    return@withContext GeminiModelListResult(
                        models = UserAiPreferences.KNOWN_VERTEX_GEMINI_MODELS.map {
                            GeminiModelInfo(id = it, displayName = it)
                        },
                        endpoint = GeminiEndpointType.VERTEX_AI_EXPRESS,
                        httpCode = response.code,
                        fromFallbackList = true,
                        message = "Önerilen güncel Vertex Gemini modelleri yüklendi."
                    )
                }

                GeminiModelListResult(
                    models = parsedModels,
                    endpoint = GeminiEndpointType.GOOGLE_AI_STUDIO,
                    httpCode = response.code,
                    fromFallbackList = false,
                    message = "${parsedModels.size} model Google AI Studio üzerinden yüklendi."
                )
            }
        }.getOrElse { err ->
            if (isVertex) {
                GeminiModelListResult(
                    models = UserAiPreferences.KNOWN_VERTEX_GEMINI_MODELS.map {
                        GeminiModelInfo(id = it, displayName = it)
                    },
                    endpoint = GeminiEndpointType.VERTEX_AI_EXPRESS,
                    httpCode = 0,
                    fromFallbackList = true,
                    message = "Ağ yanıtı alınamadı; önerilen Vertex Gemini modelleri yüklendi."
                )
            } else {
                GeminiModelListResult(
                    models = emptyList(),
                    endpoint = GeminiEndpointType.GOOGLE_AI_STUDIO,
                    httpCode = 0,
                    fromFallbackList = false,
                    message = err.localizedMessage ?: "Model listesi alınamadı."
                )
            }
        }
    }

    /**
     * Queries AI Studio's `/v1beta/models` (or Vertex Express model catalog for `AQ.` keys)
     * using ONLY the user-entered key(s) and returns sorted selectable Gemini models.
     */
    suspend fun fetchSupportedModels(apiKeysRaw: String): GeminiModelDiscoveryResult = withContext(Dispatchers.IO) {
        val userKeys = UserAiPreferences.parseGeminiKeyList(apiKeysRaw)
        if (userKeys.isEmpty()) {
            return@withContext GeminiModelDiscoveryResult(
                success = false,
                models = emptyList(),
                summary = "Model listelemek için önce kendi Gemini API anahtarınızı girin."
            )
        }

        var lastError: String? = null
        for ((idx, key) in userKeys.withIndex()) {
            val result = listModels(key)
            val filtered = result.models
                .filter { info ->
                    val lower = info.id.lowercase()
                    !lower.contains("embedding") &&
                        !lower.contains("aqa") &&
                        !lower.contains("imagen") &&
                        !lower.contains("veo") &&
                        !lower.contains("tts") &&
                        !lower.contains("robotics")
                }
                .map { info ->
                    GeminiModelOption(
                        id = info.id,
                        displayName = info.displayName.ifBlank { info.id },
                        description = info.description.take(100)
                    )
                }
                .distinctBy { it.id }
                .sortedWith(
                    compareByDescending<GeminiModelOption> { it.id.contains("3.8") }
                        .thenByDescending { it.id.contains("3.6") }
                        .thenByDescending { it.id.contains("3.") }
                        .thenByDescending { it.id.contains("2.5") }
                        .thenByDescending { it.id.contains("flash", ignoreCase = true) }
                        .thenBy { it.id }
                )

            if (filtered.isNotEmpty()) {
                return@withContext GeminiModelDiscoveryResult(
                    success = true,
                    models = filtered,
                    summary = result.message.ifBlank { "${filtered.size} desteklenen model bulundu." }
                )
            } else {
                val masked = ApiKeyRotator.maskApiKey(key, slotIndex = null)
                lastError = "Anahtar #${idx + 1} ($masked): ${result.message}"
            }
        }

        GeminiModelDiscoveryResult(
            success = false,
            models = emptyList(),
            summary = lastError ?: "Modeller listelenemedi."
        )
    }

    fun parseModelsListJson(rawJson: String): List<GeminiModelInfo> {
        if (rawJson.isBlank()) return emptyList()
        return runCatching {
            val root = JsonParser.parseString(rawJson).asJsonObject
            val arr = root.getAsJsonArrayOrNull("models") ?: return emptyList()
            arr.mapNotNull { elem ->
                if (!elem.isJsonObject) return@mapNotNull null
                val obj = elem.asJsonObject
                val fullName = obj.getAsStringOrNull("name").orEmpty()
                val id = sanitizeModelName(fullName)
                if (id.isBlank() || !id.contains("gemini", ignoreCase = true)) return@mapNotNull null

                val methods = obj.getAsJsonArrayOrNull("supportedGenerationMethods")
                    ?.mapNotNull { m -> m.takeIf { it.isJsonPrimitive }?.asString }
                    .orEmpty()

                if (methods.isNotEmpty() && methods.none { it.equals("generateContent", ignoreCase = true) }) {
                    return@mapNotNull null
                }

                GeminiModelInfo(
                    id = id,
                    displayName = obj.getAsStringOrNull("displayName")?.ifBlank { id } ?: id,
                    description = obj.getAsStringOrNull("description").orEmpty(),
                    supportedMethods = methods
                )
            }.distinctBy { it.id }
        }.getOrDefault(emptyList())
    }

    /**
     * Runs a lightweight connection health check returning masked key slot, endpoint,
     * model, HTTP code, latency, and human-readable status for both success and failure.
     */
    suspend fun testConnection(
        apiKey: String,
        modelName: String,
        slotLabel: String,
        maskedKey: String
    ): GeminiConnectionTestResult = withContext(Dispatchers.IO) {
        val cleanKey = sanitizeApiKey(apiKey)
        val cleanModel = sanitizeModelName(modelName).ifBlank { UserAiPreferences.BUILTIN_DEFAULT_MODEL }
        val primaryEndpoint = resolveEndpointsForKey(cleanKey).first()

        if (cleanKey.isBlank()) {
            return@withContext GeminiConnectionTestResult(
                slotLabel = slotLabel,
                maskedKey = maskedKey,
                isSuccess = false,
                endpoint = primaryEndpoint,
                modelName = cleanModel,
                httpCode = 0,
                message = "API anahtarı boş veya geçersiz.",
                latencyMs = 0L
            )
        }

        val startMs = System.currentTimeMillis()
        try {
            val resp = generateContent(
                apiKey = cleanKey,
                modelName = cleanModel,
                contents = listOf(GeminiContent.userText("Merhaba, sadece OK yaz.")),
                generationConfig = GeminiGenerationConfig(
                    temperature = 0f,
                    maxOutputTokens = 16
                )
            )
            val latency = System.currentTimeMillis() - startMs
            val usedEndpoint = resp.endpoint ?: primaryEndpoint
            val fallbackNote = if (resp.usedEndpointFallback) " (Vertex fallback → AI Studio)" else ""
            val snippet = resp.text.trim().replace("\n", " ").take(40).ifBlank { "Yanıt alındı" }

            GeminiConnectionTestResult(
                slotLabel = slotLabel,
                maskedKey = maskedKey,
                isSuccess = true,
                endpoint = usedEndpoint,
                usedEndpointFallback = resp.usedEndpointFallback,
                modelName = cleanModel,
                httpCode = 200,
                message = "HTTP 200 • Bağlantı başarılı$fallbackNote (\"$snippet\")",
                latencyMs = latency
            )
        } catch (apiEx: GeminiApiException) {
            val latency = System.currentTimeMillis() - startMs
            GeminiConnectionTestResult(
                slotLabel = slotLabel,
                maskedKey = maskedKey,
                isSuccess = false,
                endpoint = apiEx.endpoint ?: primaryEndpoint,
                usedEndpointFallback = apiEx.usedEndpointFallback,
                modelName = cleanModel,
                httpCode = apiEx.httpCode,
                message = buildString {
                    if (apiEx.httpCode > 0) append("HTTP ${apiEx.httpCode} • ")
                    if (apiEx.usedEndpointFallback) append("Vertex fallback → AI Studio • ")
                    append(apiEx.message ?: "Bilinmeyen API hatası")
                },
                latencyMs = latency
            )
        } catch (e: Throwable) {
            val latency = System.currentTimeMillis() - startMs
            val networkMsg = if (isNetworkUnreachableError(e)) {
                "İnternet/ağ bağlantısı kurulamadı (${e.javaClass.simpleName})."
            } else {
                e.localizedMessage ?: e.javaClass.simpleName
            }
            GeminiConnectionTestResult(
                slotLabel = slotLabel,
                maskedKey = maskedKey,
                isSuccess = false,
                endpoint = primaryEndpoint,
                modelName = cleanModel,
                httpCode = 0,
                message = networkMsg,
                latencyMs = latency
            )
        }
    }

    /**
     * Tests candidate Gemini keys via pure OkHttp REST:
     * - If [candidateUserKeysRaw] is non-blank, tests those keys with [candidateModelId] (or current custom selection).
     * - If blank, tests built-in keys strictly with `gemini-3.6-flash` (and Vertex AI Express + AI Studio routing).
     */
    suspend fun testApiConnection(
        context: Context? = null,
        candidateUserKeysRaw: String? = null,
        candidateModelId: String? = null
    ): AiProviderConnectionResult = withContext(Dispatchers.IO) {
        val parsedUser = candidateUserKeysRaw?.let { UserAiPreferences.parseGeminiKeyList(it) }.orEmpty()
        val usingCustomKeys = parsedUser.isNotEmpty()
        val candidateKeys = when {
            usingCustomKeys -> parsedUser
            candidateUserKeysRaw != null -> ApiKeyRotator.getBuildConfigKeys()
            else -> ApiKeyRotator.getAllEffectiveKeys(context)
        }

        if (candidateKeys.isEmpty()) {
            return@withContext AiProviderConnectionResult(
                success = false,
                summary = "Tanımlı Gemini API anahtarı bulunamadı.",
                details = listOf("Ayarlardan bir anahtar girin veya derleme anahtarlarını kontrol edin.")
            )
        }

        val perKeyDiagnostics = mutableListOf<String>()
        for ((idx, key) in candidateKeys.withIndex()) {
            val masked = ApiKeyRotator.maskApiKey(key, slotIndex = null)
            val modelToTest = if (usingCustomKeys) {
                UserAiPreferences.sanitizeProviderModelId(
                    candidateModelId ?: UserAiPreferences.getOrNull(context)?.getSelectedUserGeminiModel(),
                    MODEL_ID
                )
            } else {
                MODEL_ID
            }

            val result = testConnection(
                apiKey = key,
                modelName = modelToTest,
                slotLabel = "Anahtar #${idx + 1}",
                maskedKey = masked
            )

            if (result.isSuccess) {
                val successLine = "Anahtar #${idx + 1} ($masked • ${result.endpoint.displayName}): OK ($modelToTest, ${result.latencyMs}ms)"
                val details = if (perKeyDiagnostics.isNotEmpty()) {
                    perKeyDiagnostics + successLine
                } else {
                    listOf(successLine)
                }
                return@withContext AiProviderConnectionResult(
                    success = true,
                    summary = "Bağlantı başarılı ($modelToTest • ${result.endpoint.displayName} • Anahtar #${idx + 1} $masked)",
                    details = details
                )
            } else {
                perKeyDiagnostics.add(
                    "Anahtar #${idx + 1} ($masked • ${result.endpoint.displayName} • $modelToTest): ${result.message}"
                )
            }
        }

        AiProviderConnectionResult(
            success = false,
            summary = "Tüm Gemini anahtarları başarısız oldu (${candidateKeys.size} anahtar denendi).",
            details = perKeyDiagnostics
        )
    }

    fun extractCleanJsonString(raw: String): String {
        var cleaned = raw.trim()
        if (cleaned.startsWith("```")) {
            val firstNewline = cleaned.indexOf('\n')
            if (firstNewline != -1) {
                cleaned = cleaned.substring(firstNewline + 1)
            }
            if (cleaned.endsWith("```")) {
                cleaned = cleaned.substring(0, cleaned.length - 3)
            }
            cleaned = cleaned.trim()
        }
        if (cleaned.startsWith("{") || cleaned.startsWith("[")) {
            return cleaned
        }
        val firstObj = cleaned.indexOf('{')
        val firstArr = cleaned.indexOf('[')
        val useArr = firstArr != -1 && (firstObj == -1 || firstArr < firstObj)
        return if (useArr) {
            val endArr = cleaned.lastIndexOf(']')
            if (endArr > firstArr) cleaned.substring(firstArr, endArr + 1) else cleaned
        } else if (firstObj != -1) {
            val endObj = cleaned.lastIndexOf('}')
            if (endObj > firstObj) cleaned.substring(firstObj, endObj + 1) else cleaned
        } else {
            cleaned
        }
    }

    fun isNetworkUnreachableError(throwable: Throwable?): Boolean {
        if (throwable == null) return false
        if (throwable is ConnectException ||
            throwable is UnknownHostException ||
            throwable is SocketTimeoutException
        ) {
            return true
        }
        val full = "${throwable.javaClass.simpleName} ${throwable.message} ${throwable.cause?.message}".lowercase()
        return full.contains("enetunreach") ||
            full.contains("network is unreachable") ||
            full.contains("failed to connect") ||
            full.contains("unknownhostexception") ||
            full.contains("connectexception") ||
            full.contains("sockettimeoutexception") ||
            full.contains("software caused connection abort") ||
            full.contains("no address associated with hostname")
    }

    fun isTimeoutError(throwable: Throwable?): Boolean {
        if (throwable == null) return false
        if (throwable is SocketTimeoutException || throwable.cause is SocketTimeoutException) {
            return true
        }
        val full = "${throwable.javaClass.simpleName} ${throwable.message} ${throwable.cause?.message}".lowercase()
        return full.contains("sockettimeoutexception") ||
            full.contains("timed out") ||
            full.contains("timeout")
    }

    private fun buildHttpApiException(
        httpCode: Int,
        rawBody: String,
        endpoint: GeminiEndpointType,
        modelName: String,
        usedFallback: Boolean = false
    ): GeminiApiException {
        var parsedStatus: String? = null
        var parsedMsg: String? = null

        runCatching {
            val trimmed = rawBody.trim()
            if (trimmed.isNotEmpty()) {
                val elem = JsonParser.parseString(trimmed)
                val obj = when {
                    elem.isJsonObject -> elem.asJsonObject
                    elem.isJsonArray && elem.asJsonArray.size() > 0 && elem.asJsonArray[0].isJsonObject ->
                        elem.asJsonArray[0].asJsonObject
                    else -> null
                }
                val errObj = obj?.getAsJsonObjectOrNull("error")
                if (errObj != null) {
                    parsedStatus = errObj.getAsStringOrNull("status")
                    parsedMsg = errObj.getAsStringOrNull("message")
                }
            }
        }

        val finalMessage = parsedMsg?.takeIf { it.isNotBlank() }
            ?: "HTTP $httpCode (${endpoint.displayName}) hatası ($modelName)"

        return GeminiApiException(
            message = finalMessage,
            httpCode = httpCode,
            apiStatus = parsedStatus,
            endpoint = endpoint,
            modelName = modelName,
            rawBody = rawBody.take(1000),
            usedEndpointFallback = usedFallback
        )
    }

    private fun JsonObject.getAsStringOrNull(key: String): String? {
        val elem: JsonElement = this.get(key) ?: return null
        if (elem.isJsonNull || !elem.isJsonPrimitive) return null
        return elem.asString
    }

    private fun JsonObject.getAsBooleanOrNull(key: String): Boolean? {
        val elem: JsonElement = this.get(key) ?: return null
        if (elem.isJsonNull || !elem.isJsonPrimitive) return null
        return runCatching { elem.asBoolean }.getOrNull()
    }

    private fun JsonObject.getAsJsonObjectOrNull(key: String): JsonObject? {
        val elem: JsonElement = this.get(key) ?: return null
        return if (elem.isJsonObject) elem.asJsonObject else null
    }

    private fun JsonObject.getAsJsonArrayOrNull(key: String): JsonArray? {
        val elem: JsonElement = this.get(key) ?: return null
        return if (elem.isJsonArray) elem.asJsonArray else null
    }
}
