package com.example.sharkmentor.core.coaching

import com.example.sharkmentor.data.local.entity.ResourceEntity
import java.util.Locale

enum class LibraryStatusFilter(val label: String) {
    ALL("Tümü"),
    IN_PROGRESS("Devam Eden"),
    COMPLETED("Tamamlanan")
}

enum class LibrarySortMode(val label: String) {
    DEFAULT("Varsayılan"),
    PROGRESS_DESC("İlerleme %"),
    TITLE_ASC("İsim A-Z")
}

data class LibraryFilterState(
    val query: String = "",
    val status: LibraryStatusFilter = LibraryStatusFilter.ALL,
    val selectedSubjects: Set<String> = emptySet(),
    val selectedRoles: Set<BookCoachingRole> = emptySet(),
    val sortMode: LibrarySortMode = LibrarySortMode.DEFAULT
) {
    val activeFilterCount: Int
        get() = (if (status != LibraryStatusFilter.ALL) 1 else 0) +
            selectedSubjects.size +
            selectedRoles.size +
            (if (sortMode != LibrarySortMode.DEFAULT) 1 else 0)

    val hasAnyFilterOrSort: Boolean
        get() = activeFilterCount > 0 || query.isNotBlank()
}

/**
 * Pure-Kotlin dynamic book filtering engine for the student's Library (Kütüphane).
 *
 * - Subject chips are generated strictly from subjects actually present in [resources]
 *   (so if the student only added Biyoloji & Kimya books, Matematik is never shown).
 * - Book type (role) chips are generated strictly from [BookCoachingRole] values actually present
 *   in [resources] (narrowed to selected subjects when active, so dead empty chips are hidden).
 * - Multi-select uses OR within each dimension (e.g. Kimya OR Biyoloji) and AND across dimensions
 *   (Search AND Status AND Subject AND Book Type).
 */
object LibraryFilterEngine {

    private val trLocale = Locale("tr", "TR")

    fun normalizeSubject(raw: String): String =
        raw.trim().replace(Regex("\\s+"), " ")

    fun resolveSubjectForBook(
        resource: ResourceEntity,
        assignments: Map<Long, BookCoachingAssignment>
    ): String {
        val assigned = assignments[resource.id]?.subject?.let { normalizeSubject(it) }.orEmpty()
        if (assigned.isNotBlank()) return assigned
        return normalizeSubject(resource.subject)
    }

    fun resolveRoleForBook(
        resource: ResourceEntity,
        assignments: Map<Long, BookCoachingAssignment>,
        verifiedVideoResourceIds: Set<Long> = emptySet()
    ): BookCoachingRole {
        return BookCoachingRoleManager.resolveAssignment(
            resource = resource,
            assignments = assignments,
            hasVerifiedVideo = resource.id in verifiedVideoResourceIds
        ).role
    }

    /**
     * Returns distinct, non-blank subjects actually present in the student's library, sorted alphabetically.
     */
    fun availableSubjects(
        resources: List<ResourceEntity>,
        assignments: Map<Long, BookCoachingAssignment> = emptyMap()
    ): List<String> {
        val canonicalByLower = linkedMapOf<String, String>()
        for (res in resources) {
            val subject = resolveSubjectForBook(res, assignments)
            if (subject.isBlank()) continue
            val key = subject.lowercase(trLocale)
            if (key !in canonicalByLower) {
                canonicalByLower[key] = subject
            }
        }
        return canonicalByLower.values.sortedBy { it.lowercase(trLocale) }
    }

    /**
     * Returns distinct [BookCoachingRole] values actually present in the library
     * (optionally scoped to [selectedSubjects] if non-empty), ordered by [BookCoachingRole.ordinal].
     */
    fun availableRoles(
        resources: List<ResourceEntity>,
        assignments: Map<Long, BookCoachingAssignment> = emptyMap(),
        selectedSubjects: Set<String> = emptySet(),
        verifiedVideoResourceIds: Set<Long> = emptySet()
    ): List<BookCoachingRole> {
        val subjectKeys = selectedSubjects.map { normalizeSubject(it).lowercase(trLocale) }.toSet()
        val scoped = if (subjectKeys.isEmpty()) {
            resources
        } else {
            resources.filter { res ->
                resolveSubjectForBook(res, assignments).lowercase(trLocale) in subjectKeys
            }
        }
        val present = scoped.map { res ->
            resolveRoleForBook(res, assignments, verifiedVideoResourceIds)
        }.toSet()
        return BookCoachingRole.entries.filter { it in present }
    }

    /**
     * Prunes any selected subject/role that no longer exists in the library (e.g. after deleting a book).
     */
    fun pruneStaleSelections(
        state: LibraryFilterState,
        resources: List<ResourceEntity>,
        assignments: Map<Long, BookCoachingAssignment> = emptyMap(),
        verifiedVideoResourceIds: Set<Long> = emptySet()
    ): LibraryFilterState {
        val validSubjectKeys = availableSubjects(resources, assignments)
            .map { it.lowercase(trLocale) }
            .toSet()
        val keptSubjects = state.selectedSubjects
            .filter { normalizeSubject(it).lowercase(trLocale) in validSubjectKeys }
            .toSet()

        val validRoles = availableRoles(
            resources = resources,
            assignments = assignments,
            selectedSubjects = keptSubjects,
            verifiedVideoResourceIds = verifiedVideoResourceIds
        ).toSet()
        val keptRoles = state.selectedRoles.filter { it in validRoles }.toSet()

        return if (keptSubjects == state.selectedSubjects && keptRoles == state.selectedRoles) {
            state
        } else {
            state.copy(selectedSubjects = keptSubjects, selectedRoles = keptRoles)
        }
    }

    fun filterAndSort(
        resources: List<ResourceEntity>,
        assignments: Map<Long, BookCoachingAssignment> = emptyMap(),
        state: LibraryFilterState,
        verifiedVideoResourceIds: Set<Long> = emptySet()
    ): List<ResourceEntity> {
        val q = state.query.trim()
        val subjectKeys = state.selectedSubjects.map { normalizeSubject(it).lowercase(trLocale) }.toSet()

        val filtered = resources.filter { res ->
            val resolvedSubject = resolveSubjectForBook(res, assignments)
            val resolvedRole = resolveRoleForBook(res, assignments, verifiedVideoResourceIds)

            val matchesQuery = q.isEmpty() ||
                res.title.contains(q, ignoreCase = true) ||
                resolvedSubject.contains(q, ignoreCase = true) ||
                resolvedRole.displayTitle.contains(q, ignoreCase = true) ||
                resolvedRole.shortBadge.contains(q, ignoreCase = true)

            val isCompleted = res.totalUnits > 0 && res.completedUnits >= res.totalUnits
            val matchesStatus = when (state.status) {
                LibraryStatusFilter.ALL -> true
                LibraryStatusFilter.IN_PROGRESS -> !isCompleted
                LibraryStatusFilter.COMPLETED -> isCompleted
            }

            val matchesSubject = subjectKeys.isEmpty() ||
                resolvedSubject.lowercase(trLocale) in subjectKeys

            val matchesRole = state.selectedRoles.isEmpty() ||
                resolvedRole in state.selectedRoles

            matchesQuery && matchesStatus && matchesSubject && matchesRole
        }

        return when (state.sortMode) {
            LibrarySortMode.DEFAULT -> filtered
            LibrarySortMode.PROGRESS_DESC -> filtered.sortedByDescending { r ->
                if (r.totalUnits > 0) r.completedUnits.toFloat() / r.totalUnits.toFloat() else 0f
            }
            LibrarySortMode.TITLE_ASC -> filtered.sortedBy { it.title.lowercase(trLocale) }
        }
    }
}
