package com.example.sharkmentor.ui.aichat

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.sharkmentor.core.coaching.PlanProgressAuditor
import com.example.sharkmentor.data.repository.SharkAiEngine
import java.util.Locale

/**
 * Mark 69 Antigravity-style `/` Slash Sub-Runner registry for Shark AI.
 *
 * Dedicated specialized tools (`/plan`, `/hedef`, `/soru`, `/benzer`, `/zen`, `/dopamin`)
 * are invoked cleanly via `/` commands, while default `Shark AI` remains a full-access
 * In-App Agent (konu hakimiyeti/çizelgesi, kütüphane kitap yönetimi, hata bankası düzenleme,
 * öğrenci dosyası ve genel sohbet için `/` gerektirmez).
 */
enum class SharkSlashCommand(
    val command: String,
    val title: String,
    val subtitle: String,
    val examplePrompt: String,
    val icon: ImageVector,
    val persona: AiPersona,
    val engine: SharkAiEngine,
    val aliases: List<String> = emptyList()
) {
    PLAN(
        command = "/plan",
        title = "Plan Ekleme",
        subtitle = "Haftalık veya günlük ders çalışma programı oluştur ve güncelle",
        examplePrompt = "Sayısal için eksiklerimi öne alan haftalık çalışma planı hazırla",
        icon = Icons.Default.DateRange,
        persona = AiPersona.STUDY_PLANNER,
        engine = SharkAiEngine.PLANNER_OR_ORGANIZER,
        aliases = listOf("/planla", "/planekle", "/program")
    ),
    HEDEF(
        command = "/hedef",
        title = "Hedef Analizi",
        subtitle = "Stratejik net analizi ve sıralama projeksiyonu",
        examplePrompt = "Mevcut netlerime göre ilk 10 bin için ders bazlı hedef analizi yap",
        icon = Icons.AutoMirrored.Filled.TrendingUp,
        persona = AiPersona.GOAL_SETTER,
        engine = SharkAiEngine.CONVERSATIONAL_PEER,
        aliases = listOf("/netanaliz", "/analiz")
    ),
    SORU(
        command = "/soru",
        title = "Soru Çözücü",
        subtitle = "Görsel veya metin soruyu adım adım hatasız çöz",
        examplePrompt = "Bu soruyu adım adım mantığıyla açıkla",
        icon = Icons.Default.AutoAwesome,
        persona = AiPersona.SHARK_COACH,
        engine = SharkAiEngine.SOLVER_ENGINE,
        aliases = listOf("/coz", "/çöz", "/sorucoz")
    ),
    BENZER(
        command = "/benzer",
        title = "Benzer Soru Oluşturucu",
        subtitle = "ÖSYM tarzında özgün ikiz pekiştirme sorusu üret",
        examplePrompt = "Modern Atom Teorisi kuantum sayıları konusunda ÖSYM tarzı benzer soru üret",
        icon = Icons.AutoMirrored.Filled.MenuBook,
        persona = AiPersona.SHARK_COACH,
        engine = SharkAiEngine.TWIN_QUESTION_ENGINE,
        aliases = listOf("/ikiz", "/benzersoru")
    ),
    ZEN(
        command = "/zen",
        title = "Zen Koçu",
        subtitle = "Sınav kaygısı, nefes egzersizi ve zihinsel odak desteği",
        examplePrompt = "Deneme öncesi heyecanım arttı, kısa bir odaklanma ve nefes rehberi ver",
        icon = Icons.Default.SelfImprovement,
        persona = AiPersona.ZEN_CALM,
        engine = SharkAiEngine.CONVERSATIONAL_PEER,
        aliases = listOf("/odak", "/stres")
    ),
    DOPAMIN(
        command = "/dopamin",
        title = "Dopamin Koçu",
        subtitle = "Odaklanma, erteleme ve dopamin orucu protokolü",
        examplePrompt = "Telefon dikkatimi dağıtıyor, bugünkü odak protokolümü toparlayalım",
        icon = Icons.Default.Bolt,
        persona = AiPersona.DOPAMINE_COACH,
        engine = SharkAiEngine.CONVERSATIONAL_PEER,
        aliases = listOf("/dopamine", "/aliskanlik")
    )
}

data class ParsedSlashInvocation(
    val command: SharkSlashCommand,
    val argumentText: String
)

object SharkSlashCommandRegistry {

    const val SLASH_QUICKFILL_TAG = "#SLASH_QUICKFILL:"
    private val TR_LOCALE: Locale = Locale.forLanguageTag("tr-TR")

    private fun normalize(raw: String): String = raw
        .lowercase(TR_LOCALE)
        .replace('ç', 'c')
        .replace('ğ', 'g')
        .replace('ı', 'i')
        .replace('İ', 'i')
        .replace('ö', 'o')
        .replace('ş', 's')
        .replace('ü', 'u')
        .trim()

    /**
     * Returns live `/` autocomplete suggestions while the user is typing a leading command token
     * (before typing a space/newline). Returns empty list for regular messages.
     */
    fun filterSuggestions(input: String): List<SharkSlashCommand> {
        val trimmedStart = input.trimStart()
        if (!trimmedStart.startsWith("/")) return emptyList()
        if (trimmedStart.any { it.isWhitespace() }) return emptyList()

        val query = normalize(trimmedStart.removePrefix("/"))
        if (query.isEmpty()) return SharkSlashCommand.entries

        return SharkSlashCommand.entries.filter { cmd ->
            val cmdNorm = normalize(cmd.command.removePrefix("/"))
            val titleNorm = normalize(cmd.title)
            val aliasHit = cmd.aliases.any { normalize(it.removePrefix("/")).startsWith(query) }
            cmdNorm.startsWith(query) || titleNorm.contains(query) || aliasHit
        }
    }

    /**
     * Parses a leading `/command [argument]` message into a [ParsedSlashInvocation].
     * Returns null if the message does not start with a recognized slash command.
     */
    fun parseLeadingCommand(input: String): ParsedSlashInvocation? {
        val trimmed = input.trim()
        if (!trimmed.startsWith("/")) return null
        val firstSpace = trimmed.indexOfFirst { it.isWhitespace() }
        val token = if (firstSpace >= 0) trimmed.substring(0, firstSpace) else trimmed
        val rest = if (firstSpace >= 0) trimmed.substring(firstSpace + 1).trim() else ""
        val normToken = normalize(token)

        val matched = SharkSlashCommand.entries.firstOrNull { cmd ->
            normalize(cmd.command) == normToken || cmd.aliases.any { normalize(it) == normToken }
        } ?: return null

        return ParsedSlashInvocation(command = matched, argumentText = rest)
    }

    /**
     * Evaluates whether [cmd] is currently locked for the student (e.g. `/dopamin` when Dopamine Detox
     * is inactive, or `/plan` when linked to an institutional coach).
     */
    fun isCommandLocked(
        cmd: SharkSlashCommand,
        isDopamineActive: Boolean,
        isInstitutional: Boolean
    ): Pair<Boolean, String?> {
        val (available, reason) = isPersonaAvailable(
            persona = cmd.persona,
            isDopamineActive = isDopamineActive,
            isInstitutional = isInstitutional
        )
        return (!available) to reason
    }

    /**
     * Detects when a user writes a request WITHOUT `/` that specifically requires one of the
     * dedicated sub-runners (`/plan`, `/benzer`, `/zen`, `/dopamin`, `/hedef`).
     *
     * CRITICAL AGENT INVARIANT:
     * General chat AND all In-App Agent data operations (konu takip çizelgesi, konu hakimiyeti,
     * ders klasörleri, kütüphaneye kitap ekleme/güncelleme, hata bankası filtreleme/düzenleme,
     * öğrenci dosyası) MUST ALWAYS return `null` so `Shark AI` executes them directly without `/`.
     */
    fun detectMissingSlashCommandSuggestion(
        plainText: String,
        hasAttachment: Boolean = false
    ): SharkSlashCommand? {
        val trimmed = plainText.trim()
        if (trimmed.isBlank() || trimmed.startsWith("/")) return null
        val norm = normalize(trimmed)

        // 1. Dedicated Study Plan creation / rebuild -> requires /plan
        // (PlanProgressAuditor already strictly excludes konu takip çizelgesi, hakimiyet, kitap & hata bankası!)
        if (PlanProgressAuditor.isPlanRebuildOrCreateIntent(trimmed)) {
            return SharkSlashCommand.PLAN
        }

        // 2. Dedicated Twin / Similar ÖSYM Question generation -> requires /benzer
        val asksTwinQuestion = (
            norm.contains("benzer soru") ||
                norm.contains("ikiz soru") ||
                norm.contains("muadil soru")
            ) && (
            norm.contains("uret") ||
                norm.contains("olustur") ||
                norm.contains("yaz") ||
                norm.contains("hazirla") ||
                norm.contains("ver") ||
                norm.contains("cikar")
            )
        if (asksTwinQuestion) return SharkSlashCommand.BENZER

        // 3. Dedicated Dopamine Coach mode -> requires /dopamin
        val asksDopamineCoach = norm.contains("dopamin koc") ||
            norm.contains("dopamin protokol") ||
            (norm.contains("dopamin oruc") && (norm.contains("baslat") || norm.contains("ac") || norm.contains("mod")))
        if (asksDopamineCoach) return SharkSlashCommand.DOPAMIN

        // 4. Dedicated Zen Coach mode -> requires /zen
        val asksZenCoach = norm.contains("zen koc") ||
            norm.contains("nefes egzersizi yaptir") ||
            (norm.contains("sinav kaygi") && (norm.contains("mod") || norm.contains("nefes") || norm.contains("rahatla")))
        if (asksZenCoach) return SharkSlashCommand.ZEN

        // 5. Dedicated Net & Rank Projection Analysis -> requires /hedef
        // Never intercept simple goal/topic/book CRUD requests!
        val asksDedicatedNetProjection = (
            norm.contains("siralama projeksiyon") ||
                norm.contains("net analizi yap") ||
                norm.contains("hedef analizi yap") ||
                norm.contains("hedef analizi mod")
            ) && !norm.contains("konu takip") && !norm.contains("kitap")
        if (asksDedicatedNetProjection) return SharkSlashCommand.HEDEF

        // Note: Attaching a question image directly works seamlessly without nagging,
        // and general in-app Agent actions (konu takip çizelgesi, kitap, hata bankası, öğrenci dosyası)
        // run directly in default Shark AI without requiring a slash command.
        if (hasAttachment) return null
        return null
    }

    /**
     * Builds a polite, clear Turkish guidance reply explaining that the requested specialized tool
     * requires its `/` slash command to run cleanly without conflicts, and appends a `#SLASH_QUICKFILL:`
     * tag so the chat UI renders a 1-tap theme-synced quick-fill action chip.
     */
    fun buildSlashRequiredGuidanceMessage(
        cmd: SharkSlashCommand,
        originalText: String,
        isLocked: Boolean = false,
        lockReason: String? = null
    ): String {
        val cleanOrig = originalText.trim().replace("\n", " ").take(240)
        if (isLocked) {
            return buildString {
                append("**${cmd.title}** (`${cmd.command}`) şu an kilitli durumda.")
                if (!lockReason.isNullOrBlank()) {
                    append("\n\n$lockReason")
                }
                append("\n\nKilit açıldığında mesajının başına **`${cmd.command}`** ekleyerek bu aracı doğrudan başlatabilirsin.")
            }
        }
        val filledExample = cleanOrig.ifBlank { cmd.examplePrompt }
        return buildString {
            append("Bu isteğin özel **${cmd.title}** aracını gerektiriyor. İşlemi başlatabilmem için mesajının başına **`${cmd.command}`** komutunu eklemen gerekiyor.")
            append("\n\nÖrnek kullanım:\n`${cmd.command} $filledExample`")
            append("\n\n`${cmd.command}` komutu olmadan bu araç güvenlik ve kararlılık nedeniyle otomatik tetiklenmez; aşağıdaki butona dokunarak komutu tek tıkla yazma alanına yerleştirebilirsin.")
            append("\n$SLASH_QUICKFILL_TAG${cmd.command} $cleanOrig")
        }
    }

    /**
     * Splits an AI message into `(cleanDisplayText, quickFillCommandOrNull)` so raw `#SLASH_QUICKFILL:`
     * markers are never shown to the user.
     */
    fun extractQuickFillPayload(messageText: String): Pair<String, String?> {
        val idx = messageText.indexOf(SLASH_QUICKFILL_TAG)
        if (idx < 0) return messageText to null
        val display = messageText.substring(0, idx).trimEnd()
        val payload = messageText.substring(idx + SLASH_QUICKFILL_TAG.length)
            .lineSequence()
            .firstOrNull()
            ?.trim()
            ?.takeIf { it.startsWith("/") }
        return display to payload
    }
}
