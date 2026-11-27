package com.example.sharkmentor.core.ai

import android.content.Context
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

enum class SharkSubscriptionTier(
    val id: String,
    val displayName: String,
    val fiveHourTokenLimit: Long,
    val weeklyTokenLimit: Long,
    val hourlyVisualQuestionLimit: Int,
    val hourlySimulationLimit: Int
) {
    FREE(
        id = "free",
        displayName = "Standart (Sabit Model)",
        fiveHourTokenLimit = 80_000L,
        weeklyTokenLimit = 450_000L,
        hourlyVisualQuestionLimit = 2,
        hourlySimulationLimit = 2
    ),
    PRO(
        id = "pro",
        displayName = "Shark Pro",
        fiveHourTokenLimit = 250_000L,
        weeklyTokenLimit = 1_500_000L,
        hourlyVisualQuestionLimit = 10,
        hourlySimulationLimit = 8
    ),
    ULTRA(
        id = "ultra",
        displayName = "Shark Ultra",
        fiveHourTokenLimit = 750_000L,
        weeklyTokenLimit = 5_000_000L,
        hourlyVisualQuestionLimit = 30,
        hourlySimulationLimit = 20
    );

    companion object {
        fun fromId(raw: String?): SharkSubscriptionTier =
            entries.firstOrNull { it.id.equals(raw, ignoreCase = true) || it.name.equals(raw, ignoreCase = true) }
                ?: FREE
    }
}

enum class QuotaUsageKind(val id: String) {
    CHAT("chat"),
    VISUAL_QUESTION("visual_question"),
    SIMULATION("simulation");

    companion object {
        fun fromId(raw: String?): QuotaUsageKind =
            entries.firstOrNull { it.id.equals(raw, ignoreCase = true) } ?: CHAT
    }
}

data class QuotaUsageEntry(
    val timestampMs: Long,
    val tokens: Long,
    val kind: QuotaUsageKind
)

@Immutable
data class SharkQuotaSnapshot(
    val tier: SharkSubscriptionTier = SharkSubscriptionTier.FREE,
    val used5hTokens: Long = 0L,
    val limit5hTokens: Long = SharkSubscriptionTier.FREE.fiveHourTokenLimit,
    val reset5hInMs: Long = 0L,
    val usedWeeklyTokens: Long = 0L,
    val limitWeeklyTokens: Long = SharkSubscriptionTier.FREE.weeklyTokenLimit,
    val resetWeeklyInMs: Long = 0L,
    val usedHourlyVisual: Int = 0,
    val limitHourlyVisual: Int = SharkSubscriptionTier.FREE.hourlyVisualQuestionLimit,
    val resetVisualInMs: Long = 0L,
    val usedHourlySimulation: Int = 0,
    val limitHourlySimulation: Int = SharkSubscriptionTier.FREE.hourlySimulationLimit,
    val resetSimulationInMs: Long = 0L,
    val bonusTokens: Long = 0L,
    val isUnlimitedUnlocked: Boolean = false
) {
    val fiveHourProgress: Float
        get() = if (limit5hTokens > 0L) (used5hTokens.toFloat() / limit5hTokens.toFloat()).coerceIn(0f, 1f) else 0f

    val weeklyProgress: Float
        get() = if (limitWeeklyTokens > 0L) (usedWeeklyTokens.toFloat() / limitWeeklyTokens.toFloat()).coerceIn(0f, 1f) else 0f
}

sealed class QuotaCheckResult {
    data object Allowed : QuotaCheckResult()
    data class Blocked(val message: String, val retryAfterMs: Long) : QuotaCheckResult()
}

/**
 * Rolling-window token & hourly feature quota manager for SharkMentor's built-in fixed API key.
 * NEVER restricts users who supply their own custom API keys or unlock unlimited token access.
 */
object SharkTokenQuotaManager {

    const val UNLIMITED_ACCESS_CODE = "JARVIS"

    private const val PREFS_NAME = "shark_token_quota_v1"
    private const val KEY_ENTRIES = "usage_entries_json"
    private const val KEY_TIER = "subscription_tier"
    private const val KEY_BONUS_TOKENS = "bonus_tokens"
    private const val KEY_UNLIMITED_UNLOCKED = "unlimited_token_unlocked"

    const val ONE_HOUR_MS = 60L * 60L * 1000L
    const val FIVE_HOURS_MS = 5L * ONE_HOUR_MS
    const val SEVEN_DAYS_MS = 7L * 24L * ONE_HOUR_MS

    @Volatile
    private var loaded = false
    private val lock = Any()
    private val entries = mutableListOf<QuotaUsageEntry>()
    private var currentTier: SharkSubscriptionTier = SharkSubscriptionTier.FREE
    private var bonusTokens: Long = 0L
    private var unlimitedUnlocked: Boolean = false

    private val _snapshot = MutableStateFlow(SharkQuotaSnapshot())
    val snapshot: StateFlow<SharkQuotaSnapshot> = _snapshot.asStateFlow()

    /**
     * Turkish-locale-safe matcher for the personal unlimited-token code (`JARVIS`).
     * Handles ASCII and Turkish dotted/dotless I variants (`jarvis`, `JARVİS`, `Jarvis`, `JARVIS`).
     */
    fun isUnlimitedTokenCode(rawInput: String?): Boolean {
        if (rawInput.isNullOrBlank()) return false
        val normalized = rawInput.trim()
            .replace('İ', 'I')
            .replace('ı', 'I')
            .uppercase(java.util.Locale.ROOT)
        return normalized == UNLIMITED_ACCESS_CODE
    }

    /**
     * Permanently lifts local token & hourly feature limits on this device without enabling Campus mode
     * or altering any app branding, logos, profile, or Firestore state.
     */
    fun unlockUnlimitedAccess(context: Context) {
        ensureLoaded(context)
        synchronized(lock) {
            unlimitedUnlocked = true
            runCatching {
                context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean(KEY_UNLIMITED_UNLOCKED, true)
                    .apply()
            }
            refreshSnapshotLocked(System.currentTimeMillis())
        }
    }

    fun isUnlimitedUnlocked(context: Context): Boolean {
        ensureLoaded(context)
        synchronized(lock) {
            return unlimitedUnlocked
        }
    }

    fun ensureLoaded(context: Context) {
        if (loaded) {
            refreshSnapshot()
            return
        }
        synchronized(lock) {
            if (loaded) {
                refreshSnapshotLocked(System.currentTimeMillis())
                return
            }
            runCatching {
                val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                currentTier = SharkSubscriptionTier.fromId(prefs.getString(KEY_TIER, SharkSubscriptionTier.FREE.id))
                bonusTokens = prefs.getLong(KEY_BONUS_TOKENS, 0L).coerceAtLeast(0L)
                unlimitedUnlocked = prefs.getBoolean(KEY_UNLIMITED_UNLOCKED, false)
                val rawJson = prefs.getString(KEY_ENTRIES, null)
                entries.clear()
                if (!rawJson.isNullOrBlank()) {
                    val arr = JSONArray(rawJson)
                    for (i in 0 until arr.length()) {
                        val obj = arr.optJSONObject(i) ?: continue
                        val ts = obj.optLong("t", 0L)
                        val tok = obj.optLong("k", 0L).coerceAtLeast(0L)
                        val kind = QuotaUsageKind.fromId(obj.optString("m", QuotaUsageKind.CHAT.id))
                        if (ts > 0L) {
                            entries.add(QuotaUsageEntry(ts, tok, kind))
                        }
                    }
                }
            }
            loaded = true
            refreshSnapshotLocked(System.currentTimeMillis())
        }
    }

    fun refreshSnapshot(nowMs: Long = System.currentTimeMillis()): SharkQuotaSnapshot {
        synchronized(lock) {
            return refreshSnapshotLocked(nowMs)
        }
    }

    fun checkCanConsume(
        context: Context,
        kind: QuotaUsageKind,
        isBuiltInKey: Boolean,
        nowMs: Long = System.currentTimeMillis()
    ): QuotaCheckResult {
        if (!isBuiltInKey) return QuotaCheckResult.Allowed
        ensureLoaded(context)
        synchronized(lock) {
            val snap = refreshSnapshotLocked(nowMs)
            return evaluateCheck(snap, kind)
        }
    }

    internal fun evaluateCheck(
        snap: SharkQuotaSnapshot,
        kind: QuotaUsageKind
    ): QuotaCheckResult {
        if (snap.isUnlimitedUnlocked) return QuotaCheckResult.Allowed
        if (kind == QuotaUsageKind.VISUAL_QUESTION && snap.usedHourlyVisual >= snap.limitHourlyVisual) {
            val waitLabel = formatRemainingDuration(snap.resetVisualInMs)
            return QuotaCheckResult.Blocked(
                message = "Sabit modelde saatlik görsel soru limitine (${snap.limitHourlyVisual}/${snap.limitHourlyVisual}) ulaştın. Yenilenmesine: $waitLabel. (Kendi API anahtarınla limitsiz devam edebilirsin.)",
                retryAfterMs = snap.resetVisualInMs
            )
        }
        if (kind == QuotaUsageKind.SIMULATION && snap.usedHourlySimulation >= snap.limitHourlySimulation) {
            val waitLabel = formatRemainingDuration(snap.resetSimulationInMs)
            return QuotaCheckResult.Blocked(
                message = "Sabit modelde saatlik simülasyon limitine (${snap.limitHourlySimulation}/${snap.limitHourlySimulation}) ulaştın. Yenilenmesine: $waitLabel. (Kendi API anahtarınla limitsiz devam edebilirsin.)",
                retryAfterMs = snap.resetSimulationInMs
            )
        }
        if (snap.used5hTokens >= snap.limit5hTokens && snap.bonusTokens <= 0L) {
            val waitLabel = formatRemainingDuration(snap.reset5hInMs)
            return QuotaCheckResult.Blocked(
                message = "Sabit modelde 5 saatlik token kotan (${formatCompactTokens(snap.limit5hTokens)}) doldu. Yenilenmesine: $waitLabel. (Kendi API anahtarınla limitsiz devam edebilirsin.)",
                retryAfterMs = snap.reset5hInMs
            )
        }
        if (snap.usedWeeklyTokens >= snap.limitWeeklyTokens && snap.bonusTokens <= 0L) {
            val waitLabel = formatRemainingDuration(snap.resetWeeklyInMs)
            return QuotaCheckResult.Blocked(
                message = "Sabit modelde haftalık token kotan (${formatCompactTokens(snap.limitWeeklyTokens)}) doldu. Yenilenmesine: $waitLabel. (Kendi API anahtarınla limitsiz devam edebilirsin.)",
                retryAfterMs = snap.resetWeeklyInMs
            )
        }
        return QuotaCheckResult.Allowed
    }

    fun recordUsage(
        context: Context,
        tokens: Long,
        kind: QuotaUsageKind,
        isBuiltInKey: Boolean,
        nowMs: Long = System.currentTimeMillis()
    ) {
        if (!isBuiltInKey) return
        ensureLoaded(context)
        val safeTokens = tokens.coerceAtLeast(0L)
        if (safeTokens == 0L && kind == QuotaUsageKind.CHAT) return
        synchronized(lock) {
            if (unlimitedUnlocked) return
            val beforeSnap = refreshSnapshotLocked(nowMs)
            val overBaseLimit = beforeSnap.used5hTokens >= beforeSnap.limit5hTokens ||
                beforeSnap.usedWeeklyTokens >= beforeSnap.limitWeeklyTokens
            if (overBaseLimit && bonusTokens > 0L && safeTokens > 0L) {
                bonusTokens = (bonusTokens - safeTokens).coerceAtLeast(0L)
            }
            entries.add(QuotaUsageEntry(timestampMs = nowMs, tokens = safeTokens, kind = kind))
            pruneOldEntriesLocked(nowMs)
            persistLocked(context)
            refreshSnapshotLocked(nowMs)
        }
    }

    fun estimateTokens(prompt: String, response: String): Long {
        val totalChars = (prompt.length + response.length).coerceAtLeast(16)
        return (totalChars / 4L).coerceAtLeast(64L)
    }

    fun formatCompactTokens(tokens: Long): String {
        return when {
            tokens >= 1_000_000L -> String.format("%.2fM", tokens / 1_000_000.0)
            tokens >= 1_000L -> String.format("%.1fK", tokens / 1_000.0)
            else -> tokens.toString()
        }
    }

    fun formatRemainingDuration(ms: Long): String {
        if (ms <= 0L) return "Hazır"
        val totalMin = ((ms + 59_999L) / 60_000L).coerceAtLeast(1L)
        val days = totalMin / (24L * 60L)
        val hours = (totalMin % (24L * 60L)) / 60L
        val mins = totalMin % 60L
        return when {
            days > 0L -> "${days}g ${hours}sa"
            hours > 0L -> "${hours}sa ${mins}dk"
            else -> "${mins}dk"
        }
    }

    private fun pruneOldEntriesLocked(nowMs: Long) {
        val cutoff = nowMs - SEVEN_DAYS_MS
        entries.removeAll { it.timestampMs < cutoff }
    }

    private fun refreshSnapshotLocked(nowMs: Long): SharkQuotaSnapshot {
        pruneOldEntriesLocked(nowMs)
        val cutoff1h = nowMs - ONE_HOUR_MS
        val cutoff5h = nowMs - FIVE_HOURS_MS
        val cutoff7d = nowMs - SEVEN_DAYS_MS

        var used5h = 0L
        var oldest5h = Long.MAX_VALUE
        var used7d = 0L
        var oldest7d = Long.MAX_VALUE
        var usedVis1h = 0
        var oldestVis1h = Long.MAX_VALUE
        var usedSim1h = 0
        var oldestSim1h = Long.MAX_VALUE

        for (e in entries) {
            if (e.timestampMs >= cutoff7d) {
                used7d += e.tokens
                if (e.tokens > 0L && e.timestampMs < oldest7d) oldest7d = e.timestampMs
            }
            if (e.timestampMs >= cutoff5h) {
                used5h += e.tokens
                if (e.tokens > 0L && e.timestampMs < oldest5h) oldest5h = e.timestampMs
            }
            if (e.timestampMs >= cutoff1h) {
                if (e.kind == QuotaUsageKind.VISUAL_QUESTION) {
                    usedVis1h += 1
                    if (e.timestampMs < oldestVis1h) oldestVis1h = e.timestampMs
                } else if (e.kind == QuotaUsageKind.SIMULATION) {
                    usedSim1h += 1
                    if (e.timestampMs < oldestSim1h) oldestSim1h = e.timestampMs
                }
            }
        }

        val snap = SharkQuotaSnapshot(
            tier = currentTier,
            used5hTokens = used5h,
            limit5hTokens = currentTier.fiveHourTokenLimit,
            reset5hInMs = if (oldest5h < Long.MAX_VALUE) (oldest5h + FIVE_HOURS_MS - nowMs).coerceAtLeast(0L) else 0L,
            usedWeeklyTokens = used7d,
            limitWeeklyTokens = currentTier.weeklyTokenLimit,
            resetWeeklyInMs = if (oldest7d < Long.MAX_VALUE) (oldest7d + SEVEN_DAYS_MS - nowMs).coerceAtLeast(0L) else 0L,
            usedHourlyVisual = usedVis1h,
            limitHourlyVisual = currentTier.hourlyVisualQuestionLimit,
            resetVisualInMs = if (oldestVis1h < Long.MAX_VALUE) (oldestVis1h + ONE_HOUR_MS - nowMs).coerceAtLeast(0L) else 0L,
            usedHourlySimulation = usedSim1h,
            limitHourlySimulation = currentTier.hourlySimulationLimit,
            resetSimulationInMs = if (oldestSim1h < Long.MAX_VALUE) (oldestSim1h + ONE_HOUR_MS - nowMs).coerceAtLeast(0L) else 0L,
            bonusTokens = bonusTokens,
            isUnlimitedUnlocked = unlimitedUnlocked
        )
        _snapshot.value = snap
        return snap
    }

    private fun persistLocked(context: Context) {
        runCatching {
            val arr = JSONArray()
            entries.forEach { e ->
                arr.put(
                    JSONObject().apply {
                        put("t", e.timestampMs)
                        put("k", e.tokens)
                        put("m", e.kind.id)
                    }
                )
            }
            context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_TIER, currentTier.id)
                .putLong(KEY_BONUS_TOKENS, bonusTokens)
                .putBoolean(KEY_UNLIMITED_UNLOCKED, unlimitedUnlocked)
                .putString(KEY_ENTRIES, arr.toString())
                .apply()
        }
    }
}
