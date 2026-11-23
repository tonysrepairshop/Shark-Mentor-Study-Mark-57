package com.example.sharkmentor.domain.util

import com.example.sharkmentor.data.local.entity.PlanEntity
import com.example.sharkmentor.data.local.entity.TargetEntity
import com.example.sharkmentor.data.local.entity.TargetWithTasks
import com.example.sharkmentor.data.local.entity.TaskEntity
import java.util.Calendar
import java.util.Locale

object PlanOrganizer {

    fun organizePlans(tasks: List<PlanEntity>): List<PlanEntity> {
        if (tasks.isEmpty()) return emptyList()

        return tasks
            .groupBy { it.dayOfWeek }
            .flatMap { (day, dailyTasks) ->
                dailyTasks
                    .sortedWith(
                        compareBy<PlanEntity> { it.scheduledTime }
                            .thenBy { it.subject }
                    )
                    .mapIndexed { index, plan ->
                        plan.copy(dayOfWeek = day, orderIndex = index)
                    }
            }
    }

    // Weight rank: lower number = earlier in the day
    fun getSubjectPriority(subject: String, topic: String): Int {
        val combined = "$subject $topic".lowercase(Locale.ROOT)
        return when {
            // 1. Morning Daily Routines
            combined.contains("paragraf") || combined.contains("problem") || combined.contains("rutin") -> 0
            // 2. High Cognitive Load (Heavy Math & Physics)
            combined.contains("matematik") || combined.contains("fizik") || combined.contains("geometri") -> 1
            // 3. Medium Sciences
            combined.contains("kimya") || combined.contains("biyoloji") -> 2
            // 4. Verbal & Social
            combined.contains("türkçe") || combined.contains("tarih") || combined.contains("coğrafya") || combined.contains("felsefe") -> 3
            // 5. Evening Revisions & Branch Mock Exams
            combined.contains("deneme") || combined.contains("tekrar") || combined.contains("soru çözümü") -> 4
            else -> 5
        }
    }

    /**
     * CRITICAL RULE:
     * Never change an entity's assigned day.
     * Sort solely WITHIN each day's task list based on cognitive priority and question counts.
     */
    fun organizeTargetWithTasks(targetsWithTasks: List<TargetWithTasks>): List<TargetWithTasks> {
        if (targetsWithTasks.isEmpty()) return emptyList()

        return targetsWithTasks.map { targetWithTasks ->
            val target = targetWithTasks.target
            val tasks = targetWithTasks.tasks

            val isHeavy = target.targetQuestions > 30 || 
                getSubjectPriority(target.category ?: "", target.title) <= 1

            val updatedTarget = target.copy(
                priority = if (isHeavy) "HIGH" else "NORMAL",
                isSynced = false,
                lastModifiedMillis = System.currentTimeMillis()
            )

            val organizedTasks = tasks
                .sortedWith(
                    compareBy<TaskEntity> { it.scheduledTime }
                        .thenBy { getSubjectPriority(target.category ?: "", it.title) }
                )
                .mapIndexed { index, task ->
                    task.copy(
                        orderIndex = index,
                        isSynced = false
                    )
                }

            TargetWithTasks(
                target = updatedTarget,
                tasks = organizedTasks
            )
        }
    }



    fun getDayOfWeekFromTarget(target: TargetEntity): Int {
        val title = target.title
        val daysMap = mapOf(
            "Pazartesi" to 1, "Salı" to 2, "Çarşamba" to 3,
            "Perşembe" to 4, "Cuma" to 5, "Cumartesi" to 6, "Pazar" to 7
        )
        for ((day, idx) in daysMap) {
            if (title.contains(day, ignoreCase = true)) return idx
        }
        val cal = Calendar.getInstance()
        cal.timeInMillis = if (target.deadlineMillis > 0) target.deadlineMillis else target.createdAt
        return when (cal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> 1
            Calendar.TUESDAY -> 2
            Calendar.WEDNESDAY -> 3
            Calendar.THURSDAY -> 4
            Calendar.FRIDAY -> 5
            Calendar.SATURDAY -> 6
            Calendar.SUNDAY -> 7
            else -> 1
        }
    }
}

object PlanAssignmentEngine {
    fun normalizeDay(rawDay: Any?): Int {
        return when (rawDay) {
            is Int -> when (rawDay) {
                in 1..7 -> rawDay
                0 -> 1 // Sunday to Monday fallback
                else -> ((rawDay % 7) + 1).coerceIn(1, 7)
            }
            is String -> {
                val lower = rawDay.trim().lowercase(java.util.Locale("tr"))
                when {
                    // IMPORTANT: Match Cumartesi BEFORE Cuma!
                    lower.contains("cumartesi") || lower.contains("sat") -> 6
                    lower.contains("cuma") || lower.contains("fri") -> 5
                    lower.contains("pazartesi") || lower.contains("mon") -> 1
                    lower.contains("salı") || lower.contains("sali") || lower.contains("tue") -> 2
                    lower.contains("çarşamba") || lower.contains("carsamba") || lower.contains("wed") -> 3
                    lower.contains("perşembe") || lower.contains("persembe") || lower.contains("thu") -> 4
                    lower.contains("pazar") || lower.contains("sun") -> 7
                    else -> 1
                }
            }
            else -> 1
        }
    }

    fun bindTasksToFolder(
        folderId: String,
        rawTasks: List<PlanEntity>
    ): List<PlanEntity> {
        require(folderId.isNotBlank()) { "Target folderId cannot be blank" }

        return rawTasks.mapIndexed { index, task ->
            task.copy(
                id = if (task.id.isBlank()) java.util.UUID.randomUUID().toString() else task.id,
                folderId = folderId,
                dayOfWeek = normalizeDay(task.dayOfWeek),
                scheduledTime = if (task.scheduledTime.isBlank()) "Esnek • 90 dk" else task.scheduledTime,
                orderIndex = index
            )
        }
    }
}
