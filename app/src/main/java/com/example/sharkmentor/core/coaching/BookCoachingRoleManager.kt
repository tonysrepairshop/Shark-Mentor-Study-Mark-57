package com.example.sharkmentor.core.coaching

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.compose.runtime.Immutable
import com.example.sharkmentor.core.util.GoogleServicesHelper
import com.example.sharkmentor.data.local.entity.ResourceEntity
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.security.MessageDigest
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Defines the explicit coaching role a student assigns to each library book per subject.
 *
 * - [CONCEPT_VIDEO]: Primary concept teaching / video camp source (e.g. Ferrum, Görkem Şahin).
 * - [PRIMARY_PRACTICE]: Main progression question bank solved in the same week right after concept study (e.g. 1. Aydın, 2. 3D).
 * - [REVISION_BOOK]: Spaced repetition (1-3-7 -> Monthly) review source (e.g. 1. Orbital, 2. Palme, 3. Apotemi).
 * - [MOCK_EXAM]: Branch or general mock exam source for consolidation.
 * - [PASSIVE]: Stored in library only; AI MUST exclude it from active coaching plans.
 */
enum class BookCoachingRole(
    val displayTitle: String,
    val shortBadge: String,
    val description: String
) {
    CONCEPT_VIDEO(
        displayTitle = "Konu Anlatım / Video Kaynağı",
        shortBadge = "Konu / Video",
        description = "Yeni konuları ilk kez öğrenirken ve ders videosu izlerken kullanılır."
    ),
    PRIMARY_PRACTICE(
        displayTitle = "İlerleme / Soru Kaynağı",
        shortBadge = "İlerleme",
        description = "Konuyu öğrendikten sonra aynı hafta içinde pekiştirme sorusu çözmek için kullanılır."
    ),
    REVISION_BOOK(
        displayTitle = "Tekrar Kaynağı",
        shortBadge = "Tekrar",
        description = "1-3-7 ve aylık aralıklı tekrarlarda konuyu kalıcı hafızaya almak için kullanılır."
    ),
    MOCK_EXAM(
        displayTitle = "Deneme Kaynağı",
        shortBadge = "Deneme",
        description = "Konu bütünlüğü ve hız kazanımı için branş/genel deneme bloğunda kullanılır."
    ),
    PASSIVE(
        displayTitle = "Pasif (Plana Dahil Etme)",
        shortBadge = "Pasif",
        description = "Kütüphanede kalır ancak yapay zeka aktif haftalık plana dahil etmez."
    )
}

@Immutable
data class BookCoachingAssignment(
    val resourceId: Long,
    val subject: String = "",
    val role: BookCoachingRole = BookCoachingRole.PRIMARY_PRACTICE,
    val priorityOrder: Int = 1,
    val videoDisabled: Boolean = false,
    val updatedAtMillis: Long = System.currentTimeMillis()
) {
    fun formattedBadge(): String {
        return when (role) {
            BookCoachingRole.PASSIVE -> role.shortBadge
            else -> "${role.shortBadge} #$priorityOrder"
        }
    }
}

/**
 * Per-UID isolated manager for student book coaching roles, subject priorities, and video opt-out flags.
 *
 * Syncs privately to `users/{uid}/book_roles/assignments` (owner-only under existing `firestore.rules`)
 * so assignments survive device changes while maintaining strict multi-user isolation.
 */
object BookCoachingRoleManager {

    private const val TAG = "BookCoachingRoleMgr"
    private const val PREFS_PREFIX = "shark_mark67_book_roles_"
    private const val KEY_ASSIGNMENTS_JSON = "assignments_json"
    private const val KEY_UPDATED_AT = "updated_at_millis"

    private val TR_LOCALE = Locale("tr")
    private val gson = Gson()
    private val bgScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var loadedUidKey: String? = null

    private val _assignmentsByResourceId = MutableStateFlow<Map<Long, BookCoachingAssignment>>(emptyMap())
    val assignmentsByResourceId: StateFlow<Map<Long, BookCoachingAssignment>> = _assignmentsByResourceId.asStateFlow()

    fun uidKeyFor(rawUid: String?): String {
        val clean = rawUid?.trim().orEmpty()
        if (clean.isBlank() || clean == "student_default") return "guest_local"
        val digest = MessageDigest.getInstance("SHA-256").digest(clean.toByteArray(Charsets.UTF_8))
        return digest.take(10).joinToString("") { "%02x".format(it) }
    }

    private fun currentUid(context: Context): String? {
        return runCatching { GoogleServicesHelper.getCurrentUser(context = context)?.uid }.getOrNull()
    }

    private fun prefs(context: Context, uidKey: String): SharedPreferences {
        return context.applicationContext.getSharedPreferences("$PREFS_PREFIX$uidKey", Context.MODE_PRIVATE)
    }

    fun ensureLoaded(context: Context) {
        val uidKey = uidKeyFor(currentUid(context))
        if (loadedUidKey == uidKey) return
        synchronized(this) {
            if (loadedUidKey == uidKey) return
            val rawJson = prefs(context, uidKey).getString(KEY_ASSIGNMENTS_JSON, null)
            _assignmentsByResourceId.value = parseAssignmentsJson(rawJson)
            loadedUidKey = uidKey
        }
    }

    fun clearInMemoryForLogout() {
        synchronized(this) {
            loadedUidKey = null
            _assignmentsByResourceId.value = emptyMap()
        }
    }

    fun setAssignment(
        context: Context,
        resource: ResourceEntity,
        role: BookCoachingRole,
        priorityOrder: Int = 1,
        videoDisabled: Boolean = false
    ): BookCoachingAssignment {
        ensureLoaded(context)
        val clampedPriority = priorityOrder.coerceIn(1, 10)
        val now = System.currentTimeMillis()
        val assignment = BookCoachingAssignment(
            resourceId = resource.id,
            subject = resource.subject.trim(),
            role = role,
            priorityOrder = clampedPriority,
            videoDisabled = videoDisabled || role == BookCoachingRole.PASSIVE,
            updatedAtMillis = now
        )
        val updated = _assignmentsByResourceId.value.toMutableMap().apply {
            put(resource.id, assignment)
        }
        persistLocal(context, updated, now)

        // If the student explicitly opted out of video for this book, immediately remove any active video binding
        if (videoDisabled) {
            runCatching {
                BookCampManager.clearResourceVideoBinding(
                    context = context,
                    resource = resource,
                    statusMessage = "Bu kitap için video desteği kapatıldı (Sadece Kitap)."
                )
            }
        }

        val appCtx = context.applicationContext
        bgScope.launch {
            pushToCloudIfSignedIn(appCtx)
            StudentDossierManager.syncInBackground(appCtx)
        }
        return assignment
    }

    fun setVideoDisabled(
        context: Context,
        resource: ResourceEntity,
        videoDisabled: Boolean
    ): BookCoachingAssignment {
        ensureLoaded(context)
        val current = resolveAssignment(
            resource = resource,
            assignments = _assignmentsByResourceId.value,
            hasVerifiedVideo = BookCampManager.bindingsByResourceId.value[resource.id]?.hasVerifiedVideoSupport == true
        )
        return setAssignment(
            context = context,
            resource = resource,
            role = current.role,
            priorityOrder = current.priorityOrder,
            videoDisabled = videoDisabled
        )
    }

    fun removeAssignment(context: Context, resourceId: Long) {
        ensureLoaded(context)
        if (!_assignmentsByResourceId.value.containsKey(resourceId)) return
        val updated = _assignmentsByResourceId.value.toMutableMap().apply { remove(resourceId) }
        persistLocal(context, updated, System.currentTimeMillis())
        val appCtx = context.applicationContext
        bgScope.launch {
            pushToCloudIfSignedIn(appCtx)
        }
    }

    private fun persistLocal(
        context: Context,
        map: Map<Long, BookCoachingAssignment>,
        updatedAtMillis: Long
    ) {
        val uidKey = uidKeyFor(currentUid(context))
        _assignmentsByResourceId.value = map
        loadedUidKey = uidKey
        prefs(context, uidKey).edit()
            .putString(KEY_ASSIGNMENTS_JSON, serializeAssignmentsJson(map))
            .putLong(KEY_UPDATED_AT, updatedAtMillis)
            .apply()
    }

    suspend fun pushToCloudIfSignedIn(context: Context): Boolean = withContext(Dispatchers.IO) {
        val uid = currentUid(context)
        if (uid.isNullOrBlank() || uid == "student_default") return@withContext false
        val firestore = GoogleServicesHelper.getFirebaseFirestore(context) ?: return@withContext false
        ensureLoaded(context)
        val current = _assignmentsByResourceId.value
        val uidKey = uidKeyFor(uid)
        val localUpdated = prefs(context, uidKey).getLong(KEY_UPDATED_AT, System.currentTimeMillis())
        return@withContext runCatching {
            val payload = hashMapOf<String, Any>(
                "assignmentsJson" to serializeAssignmentsJson(current),
                "updatedAtMillis" to localUpdated,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            firestore.collection("users").document(uid)
                .collection("book_roles").document("assignments")
                .set(payload, SetOptions.merge())
                .await()
            true
        }.getOrElse { e ->
            Log.w(TAG, "pushToCloudIfSignedIn failed: ${e.message}")
            false
        }
    }

    suspend fun pullFromCloudIfNewer(context: Context): Boolean = withContext(Dispatchers.IO) {
        val uid = currentUid(context)
        if (uid.isNullOrBlank() || uid == "student_default") return@withContext false
        val firestore = GoogleServicesHelper.getFirebaseFirestore(context) ?: return@withContext false
        ensureLoaded(context)
        val uidKey = uidKeyFor(uid)
        val localUpdated = prefs(context, uidKey).getLong(KEY_UPDATED_AT, 0L)
        return@withContext runCatching {
            val snap = firestore.collection("users").document(uid)
                .collection("book_roles").document("assignments")
                .get()
                .await()
            if (!snap.exists()) return@runCatching false
            val remoteUpdated = snap.getLong("updatedAtMillis") ?: 0L
            val remoteJson = snap.getString("assignmentsJson").orEmpty()
            if (remoteJson.isBlank()) return@runCatching false
            val remoteMap = parseAssignmentsJson(remoteJson)
            if (remoteUpdated > localUpdated || (_assignmentsByResourceId.value.isEmpty() && remoteMap.isNotEmpty())) {
                persistLocal(context, remoteMap, remoteUpdated.coerceAtLeast(System.currentTimeMillis()))
                true
            } else {
                false
            }
        }.getOrElse { e ->
            Log.w(TAG, "pullFromCloudIfNewer failed: ${e.message}")
            false
        }
    }

    // ── Pure Resolution, Filtering & Prompt Builders (100% Unit-Testable) ────

    fun resolveAssignment(
        resource: ResourceEntity,
        assignments: Map<Long, BookCoachingAssignment>,
        hasVerifiedVideo: Boolean = false
    ): BookCoachingAssignment {
        assignments[resource.id]?.let { existing ->
            return existing.copy(
                subject = existing.subject.ifBlank { resource.subject.trim() }
            )
        }
        val titleLower = resource.title.lowercase(TR_LOCALE)
        val inferredRole = when {
            titleLower.contains("deneme") || titleLower.contains("branş") || titleLower.contains("brans") ->
                BookCoachingRole.MOCK_EXAM
            titleLower.contains("video ders") || titleLower.contains("vdd") ||
                titleLower.contains("konu anlatım") || titleLower.contains("konu anlatim") ||
                titleLower.contains("ders defteri") || titleLower.contains("kamp") || hasVerifiedVideo ->
                BookCoachingRole.CONCEPT_VIDEO
            titleLower.contains("tekrar") || titleLower.contains("özet") || titleLower.contains("ozet") ->
                BookCoachingRole.REVISION_BOOK
            else -> BookCoachingRole.PRIMARY_PRACTICE
        }
        return BookCoachingAssignment(
            resourceId = resource.id,
            subject = resource.subject.trim(),
            role = inferredRole,
            priorityOrder = 1,
            videoDisabled = false,
            updatedAtMillis = 0L
        )
    }

    fun isVideoDisabledFor(
        resourceId: Long,
        assignments: Map<Long, BookCoachingAssignment> = _assignmentsByResourceId.value
    ): Boolean {
        val a = assignments[resourceId] ?: return false
        return a.videoDisabled || a.role == BookCoachingRole.PASSIVE
    }

    fun getActiveBooksForSubject(
        subject: String,
        resources: List<ResourceEntity>,
        assignments: Map<Long, BookCoachingAssignment>,
        verifiedVideoResourceIds: Set<Long> = emptySet()
    ): List<Pair<ResourceEntity, BookCoachingAssignment>> {
        val normSubj = PlanProgressAuditor.normalizeTrKey(subject)
        return resources
            .filter { !it.isDeleted }
            .filter { res ->
                val resSubj = PlanProgressAuditor.normalizeTrKey(res.subject)
                normSubj.isBlank() || resSubj == normSubj ||
                    resSubj.contains(normSubj) || normSubj.contains(resSubj)
            }
            .map { res ->
                res to resolveAssignment(res, assignments, res.id in verifiedVideoResourceIds)
            }
            .filter { (_, assign) -> assign.role != BookCoachingRole.PASSIVE }
            .sortedWith(compareBy<Pair<ResourceEntity, BookCoachingAssignment>> { it.second.role.ordinal }.thenBy { it.second.priorityOrder }.thenBy { it.first.id })
    }

    fun getBooksByRoleForSubject(
        subject: String,
        role: BookCoachingRole,
        resources: List<ResourceEntity>,
        assignments: Map<Long, BookCoachingAssignment>,
        verifiedVideoResourceIds: Set<Long> = emptySet()
    ): List<Pair<ResourceEntity, BookCoachingAssignment>> {
        return getActiveBooksForSubject(subject, resources, assignments, verifiedVideoResourceIds)
            .filter { (_, a) -> a.role == role }
            .sortedWith(compareBy<Pair<ResourceEntity, BookCoachingAssignment>> { it.second.priorityOrder }.thenBy { it.first.id })
    }

    fun getConceptBookForSubject(
        subject: String,
        resources: List<ResourceEntity>,
        assignments: Map<Long, BookCoachingAssignment>,
        verifiedVideoResourceIds: Set<Long> = emptySet()
    ): Pair<ResourceEntity, BookCoachingAssignment>? {
        return getBooksByRoleForSubject(subject, BookCoachingRole.CONCEPT_VIDEO, resources, assignments, verifiedVideoResourceIds)
            .firstOrNull()
    }

    fun getPrimaryPracticeBooksForSubject(
        subject: String,
        resources: List<ResourceEntity>,
        assignments: Map<Long, BookCoachingAssignment>,
        verifiedVideoResourceIds: Set<Long> = emptySet()
    ): List<Pair<ResourceEntity, BookCoachingAssignment>> {
        return getBooksByRoleForSubject(subject, BookCoachingRole.PRIMARY_PRACTICE, resources, assignments, verifiedVideoResourceIds)
    }

    fun getRevisionBooksForSubject(
        subject: String,
        resources: List<ResourceEntity>,
        assignments: Map<Long, BookCoachingAssignment>,
        verifiedVideoResourceIds: Set<Long> = emptySet()
    ): List<Pair<ResourceEntity, BookCoachingAssignment>> {
        return getBooksByRoleForSubject(subject, BookCoachingRole.REVISION_BOOK, resources, assignments, verifiedVideoResourceIds)
    }

    fun parseRoleFromNaturalLanguage(rawRole: String?): BookCoachingRole? {
        val text = rawRole?.lowercase(TR_LOCALE)?.trim().orEmpty()
        if (text.isBlank()) return null
        return when {
            text.contains("pasif") || text.contains("passive") || text.contains("kullanma") || text.contains("dahil etme") || text.contains("hariç") ->
                BookCoachingRole.PASSIVE
            text.contains("tekrar") || text.contains("revision") || text.contains("pekiştirme tekrar") || text.contains("1-3-7") ->
                BookCoachingRole.REVISION_BOOK
            text.contains("deneme") || text.contains("mock") || text.contains("branş") || text.contains("brans") ->
                BookCoachingRole.MOCK_EXAM
            text.contains("video") || text.contains("konu anlat") || text.contains("concept") || text.contains("vdd") || text.contains("ders kitabı") || text.contains("ders kitabi") ->
                BookCoachingRole.CONCEPT_VIDEO
            text.contains("ilerleme") || text.contains("birincil") || text.contains("primary") || text.contains("soru bank") || text.contains("ana kaynak") || text.contains("çözüm") ->
                BookCoachingRole.PRIMARY_PRACTICE
            else -> null
        }
    }

    fun parsePriorityFromNaturalLanguage(rawText: String?, explicitPriority: Int? = null): Int {
        if (explicitPriority != null && explicitPriority in 1..10) return explicitPriority
        val text = rawText?.lowercase(TR_LOCALE).orEmpty()
        val numMatch = Regex("""\b([1-9])\s*\.?\s*(?:öncelik|oncelik|sıra|sira|kaynak|kitap|ilerleme|tekrar)""").find(text)
        if (numMatch != null) {
            return numMatch.groupValues[1].toIntOrNull()?.coerceIn(1, 10) ?: 1
        }
        return when {
            text.contains("birinci") || text.contains("1.") || text.contains("ilk ") -> 1
            text.contains("ikinci") || text.contains("2.") -> 2
            text.contains("üçüncü") || text.contains("ucuncu") || text.contains("3.") -> 3
            text.contains("dördüncü") || text.contains("dorduncu") || text.contains("4.") -> 4
            text.contains("beşinci") || text.contains("besinci") || text.contains("5.") -> 5
            else -> 1
        }
    }

    /**
     * Builds the structured Turkish AI context detailing every subject's assigned books by Role & Priority,
     * plus explicitly excluded Passive books and Video-Disabled flags.
     */
    fun buildAiBookRolesPrompt(
        resources: List<ResourceEntity>,
        assignments: Map<Long, BookCoachingAssignment>,
        verifiedVideoResourceIds: Set<Long> = emptySet()
    ): String {
        val activeRes = resources.filter { !it.isDeleted }
        if (activeRes.isEmpty()) return ""

        val resolvedPairs = activeRes.map { res ->
            res to resolveAssignment(res, assignments, res.id in verifiedVideoResourceIds)
        }
        val activePairs = resolvedPairs.filter { it.second.role != BookCoachingRole.PASSIVE }
        val passivePairs = resolvedPairs.filter { it.second.role == BookCoachingRole.PASSIVE }

        val sb = StringBuilder()
        sb.appendLine("[ÖĞRENCİNİN DERS BAZLI KİTAP ROLLERİ VE ÖNCELİK SIRASI]")
        if (activePairs.isNotEmpty()) {
            activePairs.groupBy { it.first.subject.ifBlank { "Genel" } }.forEach { (subject, items) ->
                val concept = items.filter { it.second.role == BookCoachingRole.CONCEPT_VIDEO }
                    .sortedBy { it.second.priorityOrder }
                val primary = items.filter { it.second.role == BookCoachingRole.PRIMARY_PRACTICE }
                    .sortedBy { it.second.priorityOrder }
                val revision = items.filter { it.second.role == BookCoachingRole.REVISION_BOOK }
                    .sortedBy { it.second.priorityOrder }
                val mock = items.filter { it.second.role == BookCoachingRole.MOCK_EXAM }
                    .sortedBy { it.second.priorityOrder }

                val parts = mutableListOf<String>()
                if (concept.isNotEmpty()) {
                    val label = concept.joinToString(", ") { (r, a) ->
                        val noVid = if (a.videoDisabled) " [Videosuz]" else ""
                        "#${a.priorityOrder} ${r.title}$noVid"
                    }
                    parts.add("Konu/Video: $label")
                }
                if (primary.isNotEmpty()) {
                    val label = primary.joinToString(", ") { (r, a) -> "#${a.priorityOrder} ${r.title}" }
                    parts.add("İlerleme/Soru: $label")
                }
                if (revision.isNotEmpty()) {
                    val label = revision.joinToString(", ") { (r, a) -> "#${a.priorityOrder} ${r.title}" }
                    parts.add("Tekrar: $label")
                }
                if (mock.isNotEmpty()) {
                    val label = mock.joinToString(", ") { (r, a) -> "#${a.priorityOrder} ${r.title}" }
                    parts.add("Deneme: $label")
                }
                sb.appendLine("• $subject -> ${parts.joinToString(" | ")}")
            }
        }
        if (passivePairs.isNotEmpty()) {
            val ignored = passivePairs.take(15).joinToString(", ") { "${it.first.title} (${it.first.subject})" }
            sb.appendLine("• PASİF / PLANA DAHİL EDİLMEYECEK KİTAPLAR: $ignored")
        }
        sb.appendLine("• KRİTİK KİTAP ROLÜ KURALI:")
        sb.appendLine("  1. Bir konuyu ilk kez çalıştırırken o dersin #1 Konu/Video Kaynağını kullan; AYNI HAFTA içinde o konuyu pekiştirmek için #1 İlerleme/Soru Kaynağından soru çözümü ekle (asla tüm haftayı sadece video kitabıyla sorusuz doldurma!).")
        sb.appendLine("  2. Öğrenilen konunun 1-3-7 ve Aylık tekrarlarında o dersin Tekrar Kaynaklarını (#1, #2, #3 öncelik sırasıyla) kullan.")
        sb.appendLine("  3. PASİF işaretli kitapları aktif haftalık plana ASLA yazma; [Videosuz] işaretli kitaplara video görevi atama.")
        return sb.toString().trim()
    }

    internal fun serializeAssignmentsJson(map: Map<Long, BookCoachingAssignment>): String {
        return gson.toJson(map.values.toList())
    }

    internal fun parseAssignmentsJson(raw: String?): Map<Long, BookCoachingAssignment> {
        if (raw.isNullOrBlank()) return emptyMap()
        return runCatching {
            val type = object : TypeToken<List<BookCoachingAssignment>>() {}.type
            val list: List<BookCoachingAssignment> = gson.fromJson(raw, type) ?: emptyList()
            list.associateBy { it.resourceId }
        }.getOrDefault(emptyMap())
    }
}
