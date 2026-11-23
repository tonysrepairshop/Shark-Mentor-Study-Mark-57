package com.example.sharkmentor.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.example.sharkmentor.core.ai.AiProviderChoice
import com.example.sharkmentor.core.ai.DeepSeekClient
import com.example.sharkmentor.core.ai.GeminiAiFactory
import com.example.sharkmentor.core.ai.GeminiChatTurn
import com.example.sharkmentor.core.ai.GeminiFunctionCall
import com.example.sharkmentor.core.ai.GeminiFunctionDeclaration
import com.example.sharkmentor.core.ai.GeminiSchemaProperty
import com.example.sharkmentor.core.ai.MultiProviderAiClient
import com.example.sharkmentor.core.ai.UserAiPreferences
import com.example.sharkmentor.core.util.ImageStorageManager
import com.example.sharkmentor.core.util.PdfBitmapConverter
import com.example.sharkmentor.data.local.SharkMentorDatabase
import com.example.sharkmentor.data.local.entity.ErrorQuestionEntity
import com.example.sharkmentor.data.local.entity.PlanEntity
import com.example.sharkmentor.data.local.entity.TargetEntity
import com.example.sharkmentor.data.local.entity.TargetPeriod
import com.example.sharkmentor.data.local.entity.TargetType
import com.example.sharkmentor.data.local.entity.TrainingFolderEntity
import com.example.sharkmentor.data.local.entity.TrainingQuestionEntity
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.Locale
import java.util.UUID

enum class SharkAiCapability(
    val id: String,
    val title: String,
    val subtitle: String
) {
    PROBLEM_SOLVER(
        id = "problem_solver",
        title = "Soru Çözümü",
        subtitle = "Adım adım detaylı soru çözümü"
    ),
    TWIN_QUESTION(
        id = "twin_question",
        title = "Benzer Soru",
        subtitle = "Yüklenen soruya benzer yeni soru"
    ),
    ADD_TO_STUDY_PLAN(
        id = "add_to_study_plan",
        title = "Plana Ekle",
        subtitle = "Çalışma programına entegre et"
    )
}

/**
 * The 4 Standalone Specialist AI Engines in SharkMentor.
 */
enum class SharkAiEngine(
    val id: String,
    val displayName: String,
    val autoBadgeText: String,
    val lockedBadgeText: String
) {
    SOLVER_ENGINE(
        id = "solver_engine",
        displayName = "Soru Çözümü",
        autoBadgeText = "Mod: Soru Çözümü",
        lockedBadgeText = "Mod: Soru Çözümü"
    ),
    TWIN_QUESTION_ENGINE(
        id = "twin_question_engine",
        displayName = "Benzer Soru",
        autoBadgeText = "Mod: Benzer Soru",
        lockedBadgeText = "Mod: Benzer Soru"
    ),
    PLANNER_OR_ORGANIZER(
        id = "planner_or_organizer",
        displayName = "Plana Ekle",
        autoBadgeText = "Mod: Plana Ekle",
        lockedBadgeText = "Mod: Plana Ekle"
    ),
    CONVERSATIONAL_PEER(
        id = "conversational_peer",
        displayName = "Sohbet",
        autoBadgeText = "Mod: Sohbet",
        lockedBadgeText = "Mod: Sohbet"
    );

    companion object {
        fun fromCapability(capability: SharkAiCapability?): SharkAiEngine? = when (capability) {
            SharkAiCapability.PROBLEM_SOLVER -> SOLVER_ENGINE
            SharkAiCapability.TWIN_QUESTION -> TWIN_QUESTION_ENGINE
            SharkAiCapability.ADD_TO_STUDY_PLAN -> PLANNER_OR_ORGANIZER
            null -> null
        }
    }
}

/**
 * Automatic Mode Detection (`AutoIntentClassifier`):
 * Inspects user text and attachment state before calling the API without appending fake text prefixes.
 */
object AutoIntentClassifier {
    private val TWIN_KEYWORDS = listOf(
        "ikiz",
        "benzer soru",
        "benzerini üret",
        "yeni soru üret",
        "benzer bir soru",
        "paralel soru",
        "izomorfik"
    )

    private val SOLVER_KEYWORDS = listOf(
        "çöz",
        "soru",
        "cevabı ne",
        "cevabi ne",
        "anlamadım",
        "anlamadim",
        "nasıl bulunur",
        "nasil bulunur",
        "kaçtır",
        "kactir",
        "işlemi",
        "şıkkı",
        "formülü"
    )

    private val PLANNER_KEYWORDS = listOf(
        "plan",
        "program",
        "takvim",
        "hata bankası",
        "hata bankasi",
        "hata kumbarası",
        "hata kumbarasi",
        "klasörle",
        "klasorle",
        "ekle",
        "tüm konular",
        "tum konular",
        "müfredat",
        "mufredat",
        "konu hakimiyeti",
        "konusunu",
        "konularım",
        "konularim",
        "seviyeye",
        "seviyemi",
        "kütüphane",
        "kutuphane",
        "kaynak",
        "soru bankası",
        "soru bankasi",
        "deneme kitabı",
        "deneme kitabi",
        "kitabını",
        "kitabini",
        "kitabım",
        "kitabim",
        "kitabı",
        "kitabi",
        "kitapta",
        "sayfaya kadar",
        "sayfadayım",
        "sayfadayim",
        "sayfasına",
        "sayfasina",
        "teste kadar",
        "testteyim",
        "testine",
        "işaretle",
        "isaretle",
        "güncelle",
        "guncelle",
        "hedef ekle",
        "hedefi güncelle",
        "hedefimi"
    )

    private val AUTONOMOUS_TOOL_KEYWORDS = listOf(
        "haftalık plan",
        "haftalik plan",
        "çalışma programı",
        "calisma programi",
        "plana ekle",
        "tüm konular",
        "tum konular",
        "müfredat",
        "mufredat",
        "konu hakimiyeti",
        "konu ekle",
        "sayfaya kadar",
        "sayfadayım",
        "sayfadayim",
        "teste kadar",
        "testteyim",
        "işaretle",
        "isaretle",
        "kitap ekle",
        "kaynak ekle",
        "kaynakları",
        "soru bankası",
        "soru bankasi",
        "kütüphane",
        "kutuphane",
        "hedef ekle",
        "hedefleri ekle",
        "hedefi güncelle"
    )

    private val MATH_SYMBOL_REGEX = Regex("([=\\+\\^√∫∑π÷×]|\\d+\\s*[\\+\\-\\*/=]\\s*\\d+)")

    fun isComplexAutonomousActionIntent(text: String): Boolean {
        val normalized = text.lowercase(Locale("tr")).trim()
        return AUTONOMOUS_TOOL_KEYWORDS.any { normalized.contains(it) }
    }

    fun classify(text: String, hasAttachment: Boolean = false): SharkAiEngine {
        val normalized = text.lowercase(Locale("tr")).trim()

        // Rule A (Twin Question)
        if (TWIN_KEYWORDS.any { normalized.contains(it) }) {
            return SharkAiEngine.TWIN_QUESTION_ENGINE
        }

        // Rule B (Planner / Autonomous Tool / Topic Mastery / Resource / Target Organizer)
        val isSolverSpecific = normalized.contains("soruyu çöz") ||
            normalized.contains("soruyu coz") ||
            normalized.contains("adım adım çöz") ||
            normalized.contains("adim adim coz") ||
            normalized.contains("cevabı ne") ||
            normalized.contains("cevabi ne") ||
            normalized.contains("kaçıncı") ||
            normalized.contains("hangi şık")
        if (!hasAttachment && !isSolverSpecific && PLANNER_KEYWORDS.any { normalized.contains(it) }) {
            return SharkAiEngine.PLANNER_OR_ORGANIZER
        }
        if (hasAttachment && (
                normalized.contains("plana ekle") ||
                normalized.contains("müfredat") ||
                normalized.contains("tüm konular") ||
                normalized.contains("haftalık plan") ||
                normalized.contains("klasörle")
            )
        ) {
            return SharkAiEngine.PLANNER_OR_ORGANIZER
        }

        // Rule C (Problem Solver / Scanner)
        if (SOLVER_KEYWORDS.any { normalized.contains(it) } ||
            MATH_SYMBOL_REGEX.containsMatchIn(normalized)
        ) {
            return SharkAiEngine.SOLVER_ENGINE
        }

        // Rule D (Default - Conversational Peer)
        return SharkAiEngine.CONVERSATIONAL_PEER
    }
}

data class AiRouterResult(
    val text: String,
    val executedToolNotes: List<String> = emptyList(),
    val createdPlanFolderId: String? = null,
    val engineUsed: SharkAiEngine = SharkAiEngine.CONVERSATIONAL_PEER
)

class AiRouterRepository(
    private val context: Context,
    private val database: SharkMentorDatabase = SharkMentorDatabase.getDatabase(context.applicationContext)
) {

    init {
        UserAiPreferences.initialize(context)
    }

    companion object {
        const val SOLE_GEMINI_MODEL = "gemini-3.6-flash"
        private val ACTION_BLOCK_REGEX = Regex("#ACTION:(\\{.*?\\})#", setOf(RegexOption.DOT_MATCHES_ALL))

        const val MANDATORY_PERSONA_RULES = """
KESİN DAVRANIŞ VE ÜSLUP KURALLARI (ASLA İHLAL ETME):
1. SIFIR YALAKALIK & GİRİŞ YASAĞI: "Harika bir soru!", "Çok güzel düşünmüşsün!", "Merhaba! Sana bugün nasıl yardımcı olabilirim?" gibi yapay övgüleri ve kurumsal giriş cümlelerini KESİNLİKLE KULLANMA. Doğrudan konuya veya çözüme gir.
2. ORANTILI UZUNLUK:
   - Kullanıcı yalnızca "Merhaba", "Selam", "Nasılsın" gibi kısa bir selam verirse MAKSİMUM 1 KISA CÜMLE ile yanıt ver (Örn: "Selam! Bugün hangi derse veya soruya odaklanıyoruz?"). Selamlamaya asla paragraflarca yanıt yazma.
   - Konuyu net, anlaşılır ve öğrencinin ihtiyacına orantılı derinlikte açıkla.
3. SOHBET KANCASI (KAPIYI KAPATMA):
   - Yanıtlarını "Başarılar dilerim, başka sorun varsa sorabilirsin" gibi sohbeti bitiren klişelerle BİTİRME.
   - Bunun yerine öğrenciyi akışta tutan tek cümlelik doğal bir takip sorusu (kanca) sor (Örn: "Bu adımda kafana yatmayan bir yer var mı, yoksa benzer bir ikiz soruyla pekiştirelim mi?").
"""

        const val SOLVER_ENGINE_SYSTEM_PROMPT = """
Sen YKS/LGS düzeyinde uzman, pedagojik ve analitik bir Soru Çözüm Motorusun.
Görevin görseldeki veya metindeki soruyu hatasız okumak ve doğrudan anlaşılır işlem adımlarıyla çözmektir.

ANLAŞILIRLIK VE MATEMATİKSEL YAZIM KURALLARI (ÇOK ÖNEMLİ):
1. TELEFON EKRANI İÇİN TEMİZ SEMBOLLER:
   - Kesinlikle karmaşık LaTeX kodları (\frac, \sqrt, \times, \cdot, \Delta, \pm vb.) YAZMA! Bunlar telefonda bozuk kod olarak görünür.
   - Doğrudan temiz Unicode sembolleri kullan:
     * Kesirler: a/b veya (a / b)
     * Karekök: √x veya √(ifade)
     * Çarpma ve bölme: · veya × ve ÷
     * Üs ve indisler: ², ³, ⁴, ⁿ, ⁻¹, x₁, x₂
     * Bağıntılar: =, ≠, ≈, ≤, ≥, ±, Δ, π, °
2. BİÇİMLENDİRME DÜZENİ:
   **SORUNUN ÖZETİ & VERİLENLER:**
   • Verilenleri ve sorulanı 1-2 maddede özetle.

   **ADIM ADIM ÇÖZÜM:**
   • **1. Adım:** İlk işlem ve mantığı
   • **2. Adım:** Devam eden işlemler
   • **3. Adım:** Sadeleştirme veya son işlem

   **DOĞRU CEVAP & SONUÇ:**
   • **Doğru Seçenek: [Şık ve Değer]** (Örn: **Doğru Cevap: C (24)**)
   • 1 cümlelik pratik kontrol veya kilit ipucu.
$MANDATORY_PERSONA_RULES
"""

        const val TWIN_QUESTION_ENGINE_SYSTEM_PROMPT = """
Sen ÖSYM / MEB tarzı Özgün İkiz Soru Üretim Motorusun.
Kullanıcının gönderdiği sorunun veya konunun kazanımını ve çözüm algoritmasını birebir koruyarak, sayıları ve hikayeyi değiştirip yepyeni, özgün bir çoktan seçmeli soru (A, B, C, D, E şıklı) üret.

ANLAŞILIRLIK VE BİÇİMLENDİRME KURALLARI:
1. TELEFON EKRANI İÇİN TEMİZ SEMBOLLER:
   - Kesinlikle LaTeX kodları (\frac, \sqrt, \cdot vb.) KULLANMA. Temiz semboller (√, ², ³, ×, ÷, ±, Δ, π, a/b) kullan.
2. DÜZEN VE FORMAT:
   **ÖZGÜN İKİZ SORU:**
   [Soru metni ve hikayesi]
   A) ...
   B) ...
   C) ...
   D) ...
   E) ...

   **ÇÖZÜM ALGORİTMASI:**
   • **1. Adım:** ...
   • **2. Adım:** ...

   **DOĞRU CEVAP:**
   • **[Şık ve Değeri]** (Örn: **Doğru Cevap: B**)
$MANDATORY_PERSONA_RULES
"""
        const val TWIN_QUESTION_SYSTEM_PROMPT = TWIN_QUESTION_ENGINE_SYSTEM_PROMPT

        const val AUTONOMOUS_PLANNER_SYSTEM_PROMPT = """
Sen SharkMentor Otonom Programlayıcı, Kütüphane ve Konu Hakimiyeti Motorusun.
Öğrencinin haftalık çalışma planını hazırlar, takvime görev ekler, mevcut kaynak/kitap ilerlemesini günceller, yeni kaynak ekler ve TYT/AYT konularını eksiksiz olarak konu hakimiyetine işlersin.

KRİTİK DOĞRULUK VE ATAMA KURALLARI (KESİNLİKLE UYULACAK):
1. SAYFA / TEST SAYISI UYDURMA YASAĞI: Öğrenci kaynağın toplam sayfa veya test sayısını AÇIKÇA söylemediyse `INSERT_RESOURCE` ve `UPDATE_RESOURCE_PROGRESS` içinde `"totalUnits":0` yaz (ASLA kafandan 40, 48, 100, 250 gibi varsayılan toplam sayı uydurma!). Sadece öğrencinin belirttiği sayıyı yaz.
2. DOĞRU DERS VE KONU EŞLEŞTİRME: Kaynak ve konu eklerken asla yanlış derse/sınava atama yapma. Mevcut kütüphane güncellemesinde bağlamdaki `[MEVCUT KÜTÜPHANE KAYNAKLARI]` listesinden doğru `resourceId`, tam `title` ve doğru `subject` bilgisini birebir al; bir dersin kitabını yanlışlıkla başka bir dersin kitabına işleme. Konularda AYT konularını (Türev, İntegral, Limit, Trigonometri, Logaritma, Diziler, Atışlar, Organik Kimya, Sistemler, Bitki Biyolojisi, Divan/Tanzimat) `AYT ...`, TYT konularını (Sözcükte Anlam, Problemler, EBOB-EKOK, Optik, Basınç, Mol, Mitoz) `TYT ...` dersine işle.
3. Sohbet metninde öğrenciye hitaben 2-4 cümlelik net bir Türkçe açıklama/özet yaz ve mesajının EN SONUNA ilgili JSON eylem bloğunu (#ACTION:{...}#) ekle:

HAFTALIK 7 GÜNLÜK PLAN OLUŞTURMA (ESNEK SÜRELİ, 7 GÜNE VE ÇOKLU DERSE DENGELİ; ASLA '09:00 - 10:30' GİBİ KATI SAAT YAZMA):
• KÜMÜLATİF TEMEL, KİTAP ROLLERİ & 1-3-7 TEKRAR KURALI: Bağlamdaki `[MEVCUT PLAN & GÖREV DENETİMİ]`, `[ÖĞRENCİNİN DERS BAZLI KİTAP ROLLERİ VE ÖNCELİK SIRASI]` ve `[SİSTEM YAPAY ZEKA KOÇLUK DOSYASI]` bölümlerini mutlaka incele. Önceki plandan EKSİK görevleri ilk günlere devret. Yeni konuyu #1 Konu/Video kaynağından verip AYNI HAFTA #1 İlerleme/Soru kaynağından soru çözdür. Vadesi gelen 1-3-7/Aylık tekrarları Tekrar Kaynağı + Hata Bankası ile ekle. PASİF kitapları plana ASLA alma; tamamlanan/öğrenilmiş konuları sıfırdan konu anlatımı gibi tekrar tekrar yazma.
#ACTION:{"type":"CREATE_WEEKLY_PLAN","title":"Haftalık Çalışma Programı","summary":"Strateji özeti","schedule":[{"dayOfWeek":1,"timeSlot":"Esnek • 90 dk","subject":"AYT Kimya","topic":"Ferrum AYT Kimya: Modern Atom Teorisi [Konu Anlatım]","targetQuestions":25}]}#

TEKİL GÖREV EKLEME:
#ACTION:{"type":"INSERT_PLAN_TASK","day":1,"time":"Esnek • 90 dk","subject":"Fizik","topic":"Kuvvet ve Hareket","questions":30}#

TOPLU KONU HAKİMİYETİ EKLEME (Örn: "TYT tüm konular", "AYT tüm konular" veya müfredat listesi istendiğinde ASLA tek boş klasör açma; preset veya topics dizisiyle tüm alt konuları gönder):
#ACTION:{"type":"INSERT_TOPICS_BATCH","exam":"TYT","preset":"TYT_ALL","topics":[{"subject":"TYT Türkçe","topic":"Sözcükte Anlam","targetLevel":5},{"subject":"TYT Matematik","topic":"Temel Kavramlar ve Sayılar","targetLevel":5}]}#

TEKİL KONU HEDEFİ EKLEME / GÜNCELLEME (Önce mevcut konu listesini kontrol et, doğru ders ve doğru alt konuyu seç):
#ACTION:{"type":"INSERT_TOPIC","subject":"TYT Biyoloji","topic":"Kalıtımın Genel İlkeleri ve Soy Ağaçları","currentLevel":1,"targetLevel":5}#
#ACTION:{"type":"UPDATE_TOPIC_PROGRESS","subject":"AYT Matematik","topic":"Türev ve Uygulamaları","currentLevel":4}#

MEVCUT KAYNAK / KİTAP İLERLEMESİ GÜNCELLEME (YENİ KİTAP AÇMA, listedeki resourceId/title/subject ile eşleşen mevcut kitabı güncelle; öğrenci toplam sayfa/test belirtmediyse totalUnits ekleme!):
#ACTION:{"type":"UPDATE_RESOURCE_PROGRESS","resourceId":0,"title":"Kitap Adı","subject":"Ders","completedUnits":45,"unitType":"Sayfa","isIncremental":false}#

KİTAP KOÇLUK ROLÜ & ÖNCELİK SIRASI ATAMA (role: CONCEPT_VIDEO | PRIMARY_PRACTICE | REVISION_BOOK | MOCK_EXAM | PASSIVE):
#ACTION:{"type":"SET_BOOK_ROLE","resourceId":0,"title":"Orbital AYT Kimya","subject":"AYT Kimya","role":"REVISION_BOOK","priorityOrder":1,"videoDisabled":false}#

YENİ KAYNAK / KİTAP EKLEME (Yalnızca kütüphanede henüz yoksa; toplam test/sayfa söylenmediyse mutlaka "totalUnits":0 yaz; rol/öncelik belirtildiyse role & priorityOrder ekle!):
#ACTION:{"type":"INSERT_RESOURCE","title":"3D AYT Matematik Soru Bankası","subject":"AYT Matematik","totalUnits":0,"completedUnits":0,"unitType":"Test","role":"PRIMARY_PRACTICE","priorityOrder":1,"videoDisabled":false}#

TEKİL VEYA TOPLU HEDEF EKLEME:
#ACTION:{"type":"INSERT_TARGET","title":"TYT Türkçe 35+ Net","description":"Haftalık deneme ve paragraf rutini","type":"SHORT_TERM","period":"WEEKLY","targetQuestions":150,"priority":"HIGH"}#
#ACTION:{"type":"INSERT_TARGETS_BATCH","targets":[{"title":"TYT Türkçe 35+ Net","description":"Haftalık paragraf rutini","type":"SHORT_TERM","period":"WEEKLY","targetQuestions":150,"priority":"HIGH"}]}#

MEVCUT HEDEF İLERLEMESİNİ GÜNCELLEME:
#ACTION:{"type":"UPDATE_TARGET_PROGRESS","targetId":0,"title":"TYT Türkçe 35+ Net","solvedQuestions":60,"isCompleted":false,"isIncremental":false}#
$MANDATORY_PERSONA_RULES
"""

        const val CONVERSATIONAL_PEER_SYSTEM_PROMPT =
            "Sen SharkMentor Akran Koç Motorusun. Öğrenciyle doğal, hızlı, samimi ve analitik bir akran mentör gibi konuşursun.\n$MANDATORY_PERSONA_RULES"
    }

    private val errorQuestionDao = database.errorQuestionDao()
    private val trainingFolderDao = database.trainingFolderDao()
    private val trainingQuestionDao = database.trainingQuestionDao()
    private val planDao = database.planDao()
    private val planRepositoryImpl = PlanRepositoryImpl(database.planDao(), database.pendingDeletionDao())
    private val targetRepository = PlanRepository(
        database.targetDao(),
        database.sessionDao(),
        database.planFolderDao()
    )

    val organizeErrorBankDeclaration: GeminiFunctionDeclaration = GeminiFunctionDeclaration(
        name = "organizeErrorBank",
        description = "Organizes the student's Error Bank (Hata Kumbarası) by classifying untagged questions by subject/topic, creating a training folder, or saving an attached question into the error bank.",
        parameters = mapOf(
            "subject" to GeminiSchemaProperty(
                type = "string",
                description = "Subject name such as Matematik, Fizik, Kimya, Biyoloji, Türkçe, Tarih, Coğrafya"
            ),
            "topic" to GeminiSchemaProperty(
                type = "string",
                description = "Sub-topic name such as Türev, İntegral, Optik, Paragraf"
            ),
            "folderTitle" to GeminiSchemaProperty(
                type = "string",
                description = "Title for the organized training folder"
            )
        ),
        requiredParameters = listOf("subject", "topic")
    )

    val addStudyPlanItemDeclaration: GeminiFunctionDeclaration = GeminiFunctionDeclaration(
        name = "addStudyPlanItem",
        description = "Adds a new study task or session item directly into the student's active Study Plan (Çalışma Planı).",
        parameters = mapOf(
            "subject" to GeminiSchemaProperty(
                type = "string",
                description = "Subject name (e.g. Matematik, Fizik, Türkçe)"
            ),
            "topic" to GeminiSchemaProperty(
                type = "string",
                description = "Topic or study focus (e.g. Türev Uygulamaları, Paragraf Rutini)"
            ),
            "dayOfWeek" to GeminiSchemaProperty(
                type = "integer",
                description = "Day of week from 1 (Monday / Pazartesi) to 7 (Sunday / Pazar)"
            ),
            "scheduledTime" to GeminiSchemaProperty(
                type = "string",
                description = "Flexible study duration label (e.g. 'Esnek • 90 dk' or 'Esnek • 60 dk', never rigid clock ranges)"
            ),
            "targetQuestions" to GeminiSchemaProperty(
                type = "integer",
                description = "Target number of questions to solve (minimum 20, default 30)"
            )
        ),
        requiredParameters = listOf("subject", "topic", "dayOfWeek")
    )

    val autonomousSharkTools: List<GeminiFunctionDeclaration> = listOf(
        organizeErrorBankDeclaration,
        addStudyPlanItemDeclaration
    )

    /**
     * Standalone Engine 1: `SolverEngine` (Soru Çözücü Modülü)
     */
    suspend fun executeSolverEngine(
        userPrompt: String,
        contextInstruction: String = "",
        attachmentUri: Uri? = null,
        onPartialText: (String) -> Unit = {}
    ): Result<AiRouterResult> {
        val cleanPrompt = userPrompt.trim().ifBlank {
            "Görseldeki veya belgedeki soruyu hatasız oku ve adım adım çöz."
        }
        val fullSystemInstruction = buildString {
            appendLine(SOLVER_ENGINE_SYSTEM_PROMPT)
            if (contextInstruction.isNotBlank()) {
                appendLine(contextInstruction)
            }
        }.trim()

        return streamEnginePipeline(
            activeEngine = SharkAiEngine.SOLVER_ENGINE,
            prompt = cleanPrompt,
            systemInstruction = fullSystemInstruction,
            attachmentUri = attachmentUri,
            overrideMaxTokens = 4096,
            onPartialText = onPartialText
        )
    }

    /**
     * Standalone Engine 2: `TwinQuestionEngine` (İkiz Soru Üretici Modülü)
     */
    suspend fun executeTwinQuestionEngine(
        userPrompt: String,
        contextInstruction: String = "",
        attachmentUri: Uri? = null,
        onPartialText: (String) -> Unit = {}
    ): Result<AiRouterResult> {
        val cleanPrompt = userPrompt.trim().ifBlank {
            "Paylaşılan sorunun kazanımını ve çözüm algoritmasını birebir koruyarak ÖSYM tarzı özgün bir ikiz soru (A-E şıklı) üret ve çözümünü ekle."
        }
        val fullSystemInstruction = buildString {
            appendLine(TWIN_QUESTION_ENGINE_SYSTEM_PROMPT)
            if (contextInstruction.isNotBlank()) {
                appendLine(contextInstruction)
            }
        }.trim()

        return streamEnginePipeline(
            activeEngine = SharkAiEngine.TWIN_QUESTION_ENGINE,
            prompt = cleanPrompt,
            systemInstruction = fullSystemInstruction,
            attachmentUri = attachmentUri,
            overrideMaxTokens = 4096,
            onPartialText = onPartialText
        )
    }

    /**
     * Standalone Engine 3: `AutonomousPlannerEngine` (Programlayıcı & Klasörleyici)
     */
    suspend fun executeAutonomousPlannerEngine(
        userPrompt: String,
        contextInstruction: String = "",
        attachmentUri: Uri? = null,
        onPartialText: (String) -> Unit = {}
    ): Result<AiRouterResult> {
        val cleanPrompt = userPrompt.trim().ifBlank {
            "Bu konuyu çalışma planıma ekle veya hata kumbaramı organize et."
        }
        val fullSystemInstruction = buildString {
            appendLine(AUTONOMOUS_PLANNER_SYSTEM_PROMPT)
            if (contextInstruction.isNotBlank()) {
                appendLine(contextInstruction)
            }
        }.trim()

        return streamEnginePipeline(
            activeEngine = SharkAiEngine.PLANNER_OR_ORGANIZER,
            prompt = cleanPrompt,
            systemInstruction = fullSystemInstruction,
            attachmentUri = attachmentUri,
            overrideMaxTokens = 6144,
            onPartialText = onPartialText
        )
    }

    /**
     * Standalone Engine 4: `ConversationalPeerEngine` (Akran Koç Modülü)
     */
    suspend fun executeConversationalPeerEngine(
        userPrompt: String,
        contextInstruction: String = "",
        attachmentUri: Uri? = null,
        onPartialText: (String) -> Unit = {}
    ): Result<AiRouterResult> {
        val fullSystemInstruction = buildString {
            appendLine(CONVERSATIONAL_PEER_SYSTEM_PROMPT)
            if (contextInstruction.isNotBlank()) {
                appendLine(contextInstruction)
            }
        }.trim()

        return streamEnginePipeline(
            activeEngine = SharkAiEngine.CONVERSATIONAL_PEER,
            prompt = userPrompt,
            systemInstruction = fullSystemInstruction,
            attachmentUri = attachmentUri,
            overrideMaxTokens = null,
            onPartialText = onPartialText
        )
    }

    suspend fun solveProblemStepByStep(
        userPrompt: String,
        systemInstruction: String,
        attachmentUri: Uri? = null,
        onPartialText: (String) -> Unit = {}
    ): Result<AiRouterResult> = executeSolverEngine(
        userPrompt = userPrompt,
        contextInstruction = systemInstruction,
        attachmentUri = attachmentUri,
        onPartialText = onPartialText
    )

    suspend fun generateTwinQuestion(
        userPrompt: String,
        systemInstruction: String,
        attachmentUri: Uri? = null,
        onPartialText: (String) -> Unit = {}
    ): Result<AiRouterResult> = executeTwinQuestionEngine(
        userPrompt = userPrompt,
        contextInstruction = systemInstruction,
        attachmentUri = attachmentUri,
        onPartialText = onPartialText
    )

    suspend fun routeMessage(
        prompt: String,
        systemInstruction: String,
        attachmentUri: Uri? = null,
        enableTools: Boolean = false,
        activeEngine: SharkAiEngine? = null,
        onPartialText: (String) -> Unit = {}
    ): Result<AiRouterResult> {
        val resolvedEngine = activeEngine ?: if (enableTools && attachmentUri == null) {
            AutoIntentClassifier.classify(prompt, hasAttachment = false)
        } else {
            AutoIntentClassifier.classify(prompt, hasAttachment = attachmentUri != null)
        }
        return when (resolvedEngine) {
            SharkAiEngine.SOLVER_ENGINE -> executeSolverEngine(
                userPrompt = prompt,
                contextInstruction = systemInstruction,
                attachmentUri = attachmentUri,
                onPartialText = onPartialText
            )
            SharkAiEngine.TWIN_QUESTION_ENGINE -> executeTwinQuestionEngine(
                userPrompt = prompt,
                contextInstruction = systemInstruction,
                attachmentUri = attachmentUri,
                onPartialText = onPartialText
            )
            SharkAiEngine.PLANNER_OR_ORGANIZER -> executeAutonomousPlannerEngine(
                userPrompt = prompt,
                contextInstruction = systemInstruction,
                attachmentUri = attachmentUri,
                onPartialText = onPartialText
            )
            SharkAiEngine.CONVERSATIONAL_PEER -> executeConversationalPeerEngine(
                userPrompt = prompt,
                contextInstruction = systemInstruction,
                attachmentUri = attachmentUri,
                onPartialText = onPartialText
            )
        }
    }

    /**
     * Dedicated Vision & OCR pipeline powered by Google Gemini Vision (`gemini-3.6-flash` REST).
     */
    suspend fun executeGeminiVision(
        prompt: String,
        systemInstruction: String,
        attachmentUri: Uri,
        activeEngine: SharkAiEngine = SharkAiEngine.SOLVER_ENGINE,
        onPartialText: (String) -> Unit = {}
    ): Result<AiRouterResult> = withContext(Dispatchers.IO) {
        val attachments = resolveMultimodalAttachment(attachmentUri)
        val extraDecodedBitmaps = mutableListOf<Bitmap>()

        try {
            val enrichedSystemInstruction = systemInstruction.trim()

            val visionBitmaps = mutableListOf<Bitmap>()
            visionBitmaps.addAll(attachments.bitmaps)

            val inlineFiles = mutableListOf<Pair<String, ByteArray>>()
            if (visionBitmaps.isEmpty() && attachments.rawBytes != null) {
                val decoded = runCatching {
                    BitmapFactory.decodeByteArray(attachments.rawBytes, 0, attachments.rawBytes.size)
                }.getOrNull()
                if (decoded != null) {
                    extraDecodedBitmaps.add(decoded)
                    visionBitmaps.add(decoded)
                } else {
                    inlineFiles.add(attachments.mimeType to attachments.rawBytes)
                }
            }

            if (visionBitmaps.isEmpty() && inlineFiles.isEmpty()) {
                return@withContext Result.failure(IllegalStateException("Eklenen dosya veya görsel okunamadı."))
            }

            val maxTokens = 6144
            val prefs = UserAiPreferences.getOrNull(context)
            val selectedProvider = prefs?.getSelectedProvider() ?: AiProviderChoice.AUTO
            val cleanVisionPrompt = prompt.trim().ifBlank { "Eklenen görseli veya belgeyi incele." }

            fun isVisionCapableProviderConfigured(p: AiProviderChoice): Boolean = when (p) {
                AiProviderChoice.CLAUDE -> MultiProviderAiClient.getClaudeApiKey(context).isNotBlank()
                AiProviderChoice.OPENAI -> MultiProviderAiClient.getOpenAiApiKey(context).isNotBlank()
                AiProviderChoice.GROK -> MultiProviderAiClient.getGrokApiKey(context).isNotBlank()
                AiProviderChoice.KIMI -> DeepSeekClient.getKimiApiKey(context).isNotBlank()
                else -> false
            }

            // If user explicitly chose Claude/OpenAI/Grok/Kimi (or AUTO resolves to one) and we have an image bitmap, honor it first
            val autoActiveProvider = prefs?.getActiveProviderAndModel()?.first ?: AiProviderChoice.GEMINI
            val primaryExternalVision = when {
                selectedProvider in listOf(AiProviderChoice.CLAUDE, AiProviderChoice.OPENAI, AiProviderChoice.GROK, AiProviderChoice.KIMI) &&
                    isVisionCapableProviderConfigured(selectedProvider) -> selectedProvider
                selectedProvider == AiProviderChoice.AUTO &&
                    autoActiveProvider in listOf(AiProviderChoice.CLAUDE, AiProviderChoice.OPENAI, AiProviderChoice.GROK) &&
                    isVisionCapableProviderConfigured(autoActiveProvider) -> autoActiveProvider
                else -> null
            }

            var externalVisionError: Throwable? = null
            if (primaryExternalVision != null && visionBitmaps.isNotEmpty()) {
                val base64Img = extractBase64FromAttachment(attachments)
                if (base64Img != null) {
                    try {
                        val extText = MultiProviderAiClient.streamChatCompletion(
                            provider = primaryExternalVision,
                            systemPrompt = enrichedSystemInstruction,
                            userPrompt = cleanVisionPrompt,
                            temperature = 0.2f,
                            maxTokens = maxTokens,
                            imageBase64 = base64Img,
                            imageMimeType = attachments.mimeType,
                            context = context,
                            onAccumulatedChunk = onPartialText
                        )
                        if (extText.isNotBlank()) {
                            val parsedActionNotes = parseInlineActionNotes(extText)
                            return@withContext Result.success(
                                AiRouterResult(
                                    text = extText,
                                    executedToolNotes = parsedActionNotes,
                                    createdPlanFolderId = null,
                                    engineUsed = activeEngine
                                )
                            )
                        }
                    } catch (ce: CancellationException) {
                        throw ce
                    } catch (e: Throwable) {
                        externalVisionError = e
                        Log.w("SharkAiVision", "${primaryExternalVision.shortLabel} vision failed (${e.message}), trying Gemini...")
                    }
                }
            }

            var lastEmitted = ""
            val geminiResult = GeminiAiFactory.streamContentWithFailover(
                context = context,
                systemInstruction = enrichedSystemInstruction,
                userPrompt = cleanVisionPrompt,
                bitmaps = visionBitmaps,
                inlineFiles = inlineFiles,
                tools = emptyList(),
                responseJson = false,
                temperature = 0.1f,
                maxOutputTokens = maxTokens,
                topP = 0.8f,
                timeoutMs = 75_000L,
                onPartialText = { partial ->
                    lastEmitted = partial
                    onPartialText(partial)
                }
            )

            if (geminiResult.isSuccess) {
                val finalText = geminiResult.getOrThrow().text.trim().ifBlank {
                    "Görsel incelendi ve çözüldü."
                }
                val parsedActionNotes = parseInlineActionNotes(finalText)
                if (finalText != lastEmitted) {
                    onPartialText(finalText)
                }
                return@withContext Result.success(
                    AiRouterResult(
                        text = finalText,
                        executedToolNotes = parsedActionNotes,
                        createdPlanFolderId = null,
                        engineUsed = activeEngine
                    )
                )
            }

            val lastException = geminiResult.exceptionOrNull()
            if (lastException is CancellationException) throw lastException

            // Seamless fallback to any other configured vision-capable external provider (Claude, OpenAI, Grok, Kimi)
            val fallbackVisionProviders = listOf(
                AiProviderChoice.CLAUDE,
                AiProviderChoice.OPENAI,
                AiProviderChoice.GROK,
                AiProviderChoice.KIMI
            ).filter { it != primaryExternalVision && isVisionCapableProviderConfigured(it) }

            if (fallbackVisionProviders.isNotEmpty() && visionBitmaps.isNotEmpty()) {
                val base64Img = extractBase64FromAttachment(attachments)
                if (base64Img != null) {
                    for (fallbackProvider in fallbackVisionProviders) {
                        try {
                            val fallbackText = MultiProviderAiClient.streamChatCompletion(
                                provider = fallbackProvider,
                                systemPrompt = enrichedSystemInstruction,
                                userPrompt = cleanVisionPrompt,
                                temperature = 0.2f,
                                maxTokens = maxTokens,
                                imageBase64 = base64Img,
                                imageMimeType = attachments.mimeType,
                                context = context,
                                onAccumulatedChunk = onPartialText
                            )
                            if (fallbackText.isNotBlank()) {
                                val parsedActionNotes = parseInlineActionNotes(fallbackText)
                                return@withContext Result.success(
                                    AiRouterResult(
                                        text = fallbackText,
                                        executedToolNotes = parsedActionNotes,
                                        createdPlanFolderId = null,
                                        engineUsed = activeEngine
                                    )
                                )
                            }
                        } catch (ce: CancellationException) {
                            throw ce
                        } catch (fe: Throwable) {
                            Log.w("SharkAiVision", "${fallbackProvider.shortLabel} Vision fallback failed: ${fe.message}", fe)
                        }
                    }
                }
            }

            Result.failure(
                externalVisionError ?: lastException ?: IllegalStateException("Vision modelleri yanıt vermedi.")
            )
        } finally {
            attachments.bitmaps.forEach { runCatching { if (!it.isRecycled) it.recycle() } }
            extraDecodedBitmaps.forEach { runCatching { if (!it.isRecycled) it.recycle() } }
        }
    }

    /**
     * Parses inline `#ACTION:{...}#` tags for both Text and Vision pipelines.
     */
    private suspend fun parseInlineActionNotes(resultText: String): List<String> {
        val parsedActionNotes = mutableListOf<String>()
        for (match in ACTION_BLOCK_REGEX.findAll(resultText)) {
            val jsonPayload = match.groupValues[1]
            runCatching {
                val jsonObj = Gson().fromJson(jsonPayload, JsonObject::class.java)
                val actionType = jsonObj.get("type")?.asString?.uppercase(Locale.ROOT)
                when (actionType) {
                    "INSERT_TASK", "INSERT_PLAN_TASK" -> {
                        val subj = jsonObj.get("subject")?.asString ?: "Ders"
                        val top = jsonObj.get("topic")?.asString ?: "Çalışma"
                        val qCount = jsonObj.get("questions")?.asInt ?: 30
                        parsedActionNotes.add("Plan görevi hazırlandı: $subj • $top ($qCount Soru)")
                    }
                    "INSERT_TOPICS_BATCH" -> {
                        parsedActionNotes.add("Konu hakimiyeti listesi işlendi.")
                    }
                    "UPDATE_RESOURCE_PROGRESS" -> {
                        parsedActionNotes.add("Kütüphane kaynak ilerlemesi güncellendi.")
                    }
                    "INSERT_TARGETS_BATCH", "UPDATE_TARGET_PROGRESS" -> {
                        parsedActionNotes.add("Hedef ilerlemesi güncellendi.")
                    }
                }
            }
        }
        return parsedActionNotes
    }

    /**
     * Dedicated Text/Chat streaming pipeline across all providers
     * (`Gemini`, `DeepSeek`, `Kimi`, `Claude`, `GPT (OpenAI)`, `Grok (xAI)` and `AUTO`).
     */
    suspend fun executeTextStream(
        activeEngine: SharkAiEngine = SharkAiEngine.CONVERSATIONAL_PEER,
        prompt: String,
        systemInstruction: String,
        chatHistory: List<com.example.sharkmentor.core.ai.OpenAiChatMessage> = emptyList(),
        onPartialText: (String) -> Unit = {}
    ): Result<AiRouterResult> = withContext(Dispatchers.IO) {
        val enrichedSystemInstruction = systemInstruction.trim()

        val prefs = UserAiPreferences.getOrNull(context)
        val selectedProvider = prefs?.getSelectedProvider() ?: AiProviderChoice.AUTO
        val hasAnyExternalKeys = MultiProviderAiClient.hasAnyExternalProviderKey(context)
        val geminiHistory = chatHistory.takeLast(6).map { msg ->
            GeminiChatTurn(role = msg.role, text = msg.content)
        }

        val isAutonomousActionRequest = activeEngine == SharkAiEngine.PLANNER_OR_ORGANIZER ||
            AutoIntentClassifier.isComplexAutonomousActionIntent(prompt)

        fun hasKeyForProvider(provider: AiProviderChoice): Boolean = when (provider) {
            AiProviderChoice.CLAUDE -> MultiProviderAiClient.getClaudeApiKey(context).isNotBlank()
            AiProviderChoice.OPENAI -> MultiProviderAiClient.getOpenAiApiKey(context).isNotBlank()
            AiProviderChoice.GROK -> MultiProviderAiClient.getGrokApiKey(context).isNotBlank()
            AiProviderChoice.DEEPSEEK -> prefs?.getUserDeepSeekKey()?.isNotBlank() == true
            AiProviderChoice.KIMI -> prefs?.getUserKimiKey()?.isNotBlank() == true
            AiProviderChoice.GEMINI -> (prefs?.getParsedUserGeminiKeys()?.isNotEmpty() == true)
            AiProviderChoice.AUTO -> true
        }

        suspend fun runSpecificExternalProvider(provider: AiProviderChoice): Result<AiRouterResult> {
            return try {
                val maxTokens = 4096
                val resultText = MultiProviderAiClient.streamChatCompletion(
                    provider = provider,
                    systemPrompt = enrichedSystemInstruction,
                    userPrompt = prompt,
                    chatHistory = chatHistory,
                    temperature = 0.3f,
                    maxTokens = maxTokens,
                    context = context,
                    onAccumulatedChunk = onPartialText
                )
                val parsedActionNotes = parseInlineActionNotes(resultText)
                Result.success(
                    AiRouterResult(
                        text = resultText,
                        executedToolNotes = parsedActionNotes,
                        createdPlanFolderId = null,
                        engineUsed = activeEngine
                    )
                )
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

        suspend fun runAnyExternalProvider(): Result<AiRouterResult> {
            return try {
                val preferKimi = DeepSeekClient.isLongContextPrompt(prompt, enrichedSystemInstruction)
                val resultText = DeepSeekClient.streamChatCompletion(
                    systemPrompt = enrichedSystemInstruction,
                    userPrompt = prompt,
                    chatHistory = chatHistory,
                    temperature = 0.3f,
                    maxTokens = 4096,
                    preferKimiLongContext = preferKimi,
                    context = context,
                    onAccumulatedChunk = onPartialText
                )
                val parsedActionNotes = parseInlineActionNotes(resultText)
                Result.success(
                    AiRouterResult(
                        text = resultText,
                        executedToolNotes = parsedActionNotes,
                        createdPlanFolderId = null,
                        engineUsed = activeEngine
                    )
                )
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

        // Dedicated Real Coaching Planner (/plan) strictly uses built-in Shark AI keys (gemini-3.6-flash / 3.5-flash-lite)
        // for reliability and low latency; all other chat, Agent, Solver, Twin, Zen & Dopamine flows honor the user's custom key.
        if (activeEngine == SharkAiEngine.PLANNER_OR_ORGANIZER) {
            return@withContext streamGeminiTextFallback(
                activeEngine = activeEngine,
                prompt = prompt,
                systemInstruction = enrichedSystemInstruction,
                chatHistory = geminiHistory,
                overrideMaxTokens = 6144,
                forceInlineActionMode = true,
                forceBuiltInOnly = true,
                onPartialText = onPartialText
            )
        }

        when (selectedProvider) {
            AiProviderChoice.GEMINI -> {
                return@withContext streamGeminiTextFallback(
                    activeEngine = activeEngine,
                    prompt = prompt,
                    systemInstruction = enrichedSystemInstruction,
                    chatHistory = geminiHistory,
                    overrideMaxTokens = 6144,
                    forceInlineActionMode = isAutonomousActionRequest,
                    onPartialText = onPartialText
                )
            }

            AiProviderChoice.CLAUDE,
            AiProviderChoice.OPENAI,
            AiProviderChoice.GROK,
            AiProviderChoice.DEEPSEEK,
            AiProviderChoice.KIMI -> {
                var explicitError: Throwable? = null
                if (hasKeyForProvider(selectedProvider)) {
                    val providerAttempt = runSpecificExternalProvider(selectedProvider)
                    if (providerAttempt.isSuccess) return@withContext providerAttempt
                    explicitError = providerAttempt.exceptionOrNull()
                    Log.w(
                        "AiRouterRepository",
                        "Selected provider $selectedProvider failed (${explicitError?.message}), falling back to Gemini..."
                    )
                }
                val geminiFallback = streamGeminiTextFallback(
                    activeEngine = activeEngine,
                    prompt = prompt,
                    systemInstruction = enrichedSystemInstruction,
                    chatHistory = geminiHistory,
                    overrideMaxTokens = 6144,
                    forceInlineActionMode = isAutonomousActionRequest,
                    onPartialText = onPartialText
                )
                return@withContext if (geminiFallback.isSuccess || explicitError == null) {
                    geminiFallback
                } else {
                    Result.failure(explicitError)
                }
            }

            AiProviderChoice.AUTO -> {
                val activeAutoProvider = prefs?.getActiveProviderAndModel()?.first ?: AiProviderChoice.GEMINI

                if (isAutonomousActionRequest && activeAutoProvider == AiProviderChoice.GEMINI) {
                    val geminiActionAttempt = streamGeminiTextFallback(
                        activeEngine = activeEngine,
                        prompt = prompt,
                        systemInstruction = enrichedSystemInstruction,
                        chatHistory = geminiHistory,
                        overrideMaxTokens = 6144,
                        forceInlineActionMode = true,
                        onPartialText = onPartialText
                    )
                    if (geminiActionAttempt.isSuccess || !hasAnyExternalKeys) {
                        return@withContext geminiActionAttempt
                    }
                    Log.w("AiRouterRepository", "Gemini autonomous action call failed, falling back to configured external provider...")
                    val extFallback = runAnyExternalProvider()
                    return@withContext if (extFallback.isSuccess) extFallback else geminiActionAttempt
                }

                if (activeAutoProvider != AiProviderChoice.GEMINI && hasKeyForProvider(activeAutoProvider)) {
                    val autoExtAttempt = runSpecificExternalProvider(activeAutoProvider)
                    if (autoExtAttempt.isSuccess) return@withContext autoExtAttempt
                    Log.w(
                        "AiRouterRepository",
                        "Auto active provider $activeAutoProvider failed (${autoExtAttempt.exceptionOrNull()?.message}), falling back to Gemini..."
                    )
                    val geminiFallback = streamGeminiTextFallback(
                        activeEngine = activeEngine,
                        prompt = prompt,
                        systemInstruction = enrichedSystemInstruction,
                        chatHistory = geminiHistory,
                        onPartialText = onPartialText
                    )
                    return@withContext if (geminiFallback.isSuccess) geminiFallback else autoExtAttempt
                }

                val geminiAttempt = streamGeminiTextFallback(
                    activeEngine = activeEngine,
                    prompt = prompt,
                    systemInstruction = enrichedSystemInstruction,
                    chatHistory = geminiHistory,
                    onPartialText = onPartialText
                )
                if (geminiAttempt.isSuccess || !hasAnyExternalKeys) {
                    return@withContext geminiAttempt
                }
                val extFallback = runAnyExternalProvider()
                return@withContext if (extFallback.isSuccess) extFallback else geminiAttempt
            }
        }
    }

    /**
     * Core hybrid router: Routes Multimodal/Vision to Gemini Vision, Text-only to DeepSeek/Kimi/Gemini.
     */
    private suspend fun streamEnginePipeline(
        activeEngine: SharkAiEngine,
        prompt: String,
        systemInstruction: String,
        attachmentUri: Uri? = null,
        chatHistory: List<com.example.sharkmentor.core.ai.OpenAiChatMessage> = emptyList(),
        overrideMaxTokens: Int? = null,
        onPartialText: (String) -> Unit = {}
    ): Result<AiRouterResult> {
        val channel = when (activeEngine) {
            SharkAiEngine.PLANNER_OR_ORGANIZER ->
                com.example.sharkmentor.core.coaching.DossierAiChannel.PLAN_ADD_AI
            SharkAiEngine.CONVERSATIONAL_PEER ->
                com.example.sharkmentor.core.coaching.DossierAiChannel.AUTO_CHAT
            SharkAiEngine.SOLVER_ENGINE,
            SharkAiEngine.TWIN_QUESTION_ENGINE ->
                com.example.sharkmentor.core.coaching.DossierAiChannel.OTHER_NON_COACHING
        }
        val dossierHeader = runCatching {
            com.example.sharkmentor.core.coaching.StudentDossierManager.ensureLoaded(context)
            if (com.example.sharkmentor.core.coaching.StudentDossierManager.shouldAttachDossier(channel, prompt)) {
                com.example.sharkmentor.core.coaching.StudentDossierManager.buildAnonymizedDossierPrompt()
            } else if (channel == com.example.sharkmentor.core.coaching.DossierAiChannel.PLAN_ADD_AI ||
                (channel == com.example.sharkmentor.core.coaching.DossierAiChannel.AUTO_CHAT &&
                    com.example.sharkmentor.core.coaching.StudentDossierManager.isCoachingPlanOrZenIntent(prompt))
            ) {
                com.example.sharkmentor.core.coaching.CoachingEngine.buildAiSystemCompassPrompt(context)
            } else {
                ""
            }
        }.getOrDefault("")
        val ledgerAndRolesHeader = if (
            channel != com.example.sharkmentor.core.coaching.DossierAiChannel.OTHER_NON_COACHING &&
            !systemInstruction.contains("[SİSTEM YAPAY ZEKA KOÇLUK DOSYASI")
        ) {
            runCatching {
                com.example.sharkmentor.core.coaching.BookCampManager.ensureLoaded(context)
                com.example.sharkmentor.core.coaching.BookCoachingRoleManager.ensureLoaded(context)
                com.example.sharkmentor.core.coaching.AiCoachLedgerManager.ensureLoaded(context)
                val resources = database.goalsDao().getAllResourcesRaw()
                val verifiedIds = com.example.sharkmentor.core.coaching.BookCampManager.bindingsByResourceId.value
                    .filterValues { it.hasVerifiedVideoSupport }.keys
                val rolesBlock = if (!systemInstruction.contains("[ÖĞRENCİNİN DERS BAZLI KİTAP ROLLERİ")) {
                    com.example.sharkmentor.core.coaching.BookCoachingRoleManager.buildAiBookRolesPrompt(
                        resources = resources,
                        assignments = com.example.sharkmentor.core.coaching.BookCoachingRoleManager.assignmentsByResourceId.value,
                        verifiedVideoResourceIds = verifiedIds
                    )
                } else ""
                val ledgerBlock = if (
                    channel == com.example.sharkmentor.core.coaching.DossierAiChannel.PLAN_ADD_AI ||
                    com.example.sharkmentor.core.coaching.StudentDossierManager.isCoachingPlanOrZenIntent(prompt)
                ) {
                    com.example.sharkmentor.core.coaching.AiCoachLedgerManager.buildAiLedgerPrompt(
                        com.example.sharkmentor.core.coaching.AiCoachLedgerManager.ledger.value
                    )
                } else ""
                listOf(rolesBlock, ledgerBlock).filter { it.isNotBlank() }.joinToString("\n\n")
            }.getOrDefault("")
        } else ""
        val combinedHeaders = listOf(
            if (dossierHeader.isNotBlank() &&
                !systemInstruction.contains("[ANONİM ÖĞRENCİ DOSYASI") &&
                !systemInstruction.contains("[ÖĞRENCİ HEDEF PUSULASI")
            ) dossierHeader else "",
            ledgerAndRolesHeader
        ).filter { it.isNotBlank() }.joinToString("\n\n")
        val enrichedInstruction = if (combinedHeaders.isNotBlank()) {
            "$combinedHeaders\n\n$systemInstruction"
        } else {
            systemInstruction
        }
        return if (attachmentUri != null) {
            executeGeminiVision(
                prompt = prompt,
                systemInstruction = enrichedInstruction,
                attachmentUri = attachmentUri,
                activeEngine = activeEngine,
                onPartialText = onPartialText
            )
        } else {
            executeTextStream(
                activeEngine = activeEngine,
                prompt = prompt,
                systemInstruction = enrichedInstruction,
                chatHistory = chatHistory,
                onPartialText = onPartialText
            )
        }
    }

    /**
     * Gemini (`gemini-3.6-flash`) streaming execution pipeline for complex autonomous action generation and critical fallback.
     */
    private suspend fun streamGeminiTextFallback(
        activeEngine: SharkAiEngine,
        prompt: String,
        systemInstruction: String,
        chatHistory: List<GeminiChatTurn> = emptyList(),
        overrideMaxTokens: Int? = null,
        forceInlineActionMode: Boolean = false,
        forceBuiltInOnly: Boolean = false,
        onPartialText: (String) -> Unit = {}
    ): Result<AiRouterResult> = withContext(Dispatchers.IO) {
        val shouldAttachTools = !forceInlineActionMode && activeEngine == SharkAiEngine.PLANNER_OR_ORGANIZER
        val effectiveMaxTokens = overrideMaxTokens ?: 8192
        var lastEmitted = ""
        val trackedPartialCallback: (String) -> Unit = { partial ->
            lastEmitted = partial
            onPartialText(partial)
        }

        var geminiResult = GeminiAiFactory.streamContentWithFailover(
            context = context,
            systemInstruction = systemInstruction,
            userPrompt = prompt,
            chatHistory = chatHistory,
            tools = if (shouldAttachTools) autonomousSharkTools else emptyList(),
            responseJson = false,
            temperature = 0.35f,
            maxOutputTokens = effectiveMaxTokens,
            topP = 0.85f,
            timeoutMs = 75_000L,
            forceBuiltInOnly = forceBuiltInOnly,
            onPartialText = trackedPartialCallback
        )

        val err = geminiResult.exceptionOrNull()
        if (err is CancellationException) throw err

        val errMsg = err?.message.orEmpty().lowercase(Locale.ROOT)
        val isToolSchemaError = errMsg.contains("400") ||
            errMsg.contains("invalid") ||
            errMsg.contains("schema") ||
            errMsg.contains("tool")

        if (geminiResult.isFailure && shouldAttachTools && isToolSchemaError) {
            Log.w("AiRouterRepository", "Tool schema error (${err?.message}); retrying without tools...")
            lastEmitted = ""
            geminiResult = GeminiAiFactory.streamContentWithFailover(
                context = context,
                systemInstruction = systemInstruction,
                userPrompt = prompt,
                chatHistory = chatHistory,
                tools = emptyList(),
                responseJson = false,
                temperature = 0.35f,
                maxOutputTokens = effectiveMaxTokens,
                topP = 0.85f,
                timeoutMs = 75_000L,
                forceBuiltInOnly = forceBuiltInOnly,
                onPartialText = trackedPartialCallback
            )
        }

        if (geminiResult.isFailure) {
            val finalErr = geminiResult.exceptionOrNull()
            if (finalErr is CancellationException) throw finalErr
            return@withContext Result.failure(
                finalErr ?: IllegalStateException("Tüm AI modelleri ve yedek anahtarlar yanıt vermedi.")
            )
        }

        val response = geminiResult.getOrThrow()
        val toolNotes = mutableListOf<String>()
        var createdFolderId: String? = null

        if (response.functionCalls.isNotEmpty()) {
            for (call: GeminiFunctionCall in response.functionCalls) {
                val executionResult = executeToolCall(
                    call.name,
                    call.args,
                    null
                )
                if (executionResult.note.isNotBlank()) {
                    toolNotes.add(executionResult.note)
                }
                if (executionResult.planFolderId != null) {
                    createdFolderId = executionResult.planFolderId
                }
            }
        }

        val finalText = buildString {
            val modelText = response.text.trim()
            if (modelText.isNotBlank()) {
                append(modelText)
            }
            if (toolNotes.isNotEmpty()) {
                if (isNotEmpty()) append("\n\n")
                append(toolNotes.joinToString("\n"))
            }
        }.ifBlank {
            "İşleminiz tamamlandı."
        }

        val inlineNotes = parseInlineActionNotes(finalText)
        toolNotes.addAll(inlineNotes)

        if (finalText != lastEmitted) {
            onPartialText(finalText)
        }

        Result.success(
            AiRouterResult(
                text = finalText,
                executedToolNotes = toolNotes,
                createdPlanFolderId = createdFolderId,
                engineUsed = activeEngine
            )
        )
    }

    private data class MultimodalPayload(
        val bitmaps: List<Bitmap>,
        val rawBytes: ByteArray?,
        val mimeType: String
    )

    private suspend fun resolveMultimodalAttachment(uri: Uri): MultimodalPayload = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val mimeType = resolver.getType(uri) ?: inferMimeType(uri)

        val isPdf = mimeType.equals("application/pdf", ignoreCase = true) ||
            uri.toString().endsWith(".pdf", ignoreCase = true)

        if (isPdf) {
            val bitmaps = runCatching {
                PdfBitmapConverter.convertPdfUriToBitmaps(context, uri, maxPages = 3)
            }.getOrDefault(emptyList())
            val rawBytes = if (bitmaps.isEmpty()) {
                runCatching { resolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
            } else null
            MultimodalPayload(
                bitmaps = bitmaps,
                rawBytes = rawBytes,
                mimeType = mimeType
            )
        } else {
            val optimized = ImageStorageManager.prepareOptimizedBitmapForAi(
                context = context,
                uri = uri,
                enhanceContrast = true
            )
            MultimodalPayload(
                bitmaps = if (optimized != null) listOf(optimized) else emptyList(),
                rawBytes = null,
                mimeType = "image/jpeg"
            )
        }
    }

    private fun extractBase64FromAttachment(payload: MultimodalPayload): String? {
        val bmp = payload.bitmaps.firstOrNull()
        if (bmp != null && !bmp.isRecycled) {
            val stream = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.JPEG, 85, stream)
            return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
        }
        val bytes = payload.rawBytes
        if (bytes != null) {
            return Base64.encodeToString(bytes, Base64.NO_WRAP)
        }
        return null
    }

    private data class ToolExecutionOutcome(
        val note: String,
        val planFolderId: String? = null
    )

    private suspend fun executeToolCall(
        callName: String,
        args: Map<String, String>,
        attachmentUri: Uri?
    ): ToolExecutionOutcome = withContext(Dispatchers.IO) {
        when (callName) {
            "organizeErrorBank" -> {
                val subject = args["subject"]?.ifBlank { "Matematik" } ?: "Matematik"
                val topic = args["topic"]?.ifBlank { "Genel" } ?: "Genel"
                val folderTitle = args["folderTitle"]?.takeIf { it.isNotBlank() }
                    ?: "$subject - $topic Hata Klasörü"

                val folderId = UUID.randomUUID().toString()
                trainingFolderDao.insertFolder(
                    TrainingFolderEntity(
                        id = folderId,
                        title = folderTitle
                    )
                )

                val untagged = errorQuestionDao.getUntaggedQuestions(limit = 10)
                untagged.filter { it.subject.equals(subject, ignoreCase = true) || it.subject.isBlank() }
                    .forEach { q ->
                        errorQuestionDao.updateAiTopic(q.id, topic)
                    }

                if (attachmentUri != null) {
                    val savedPath = runCatching {
                        ImageStorageManager.processAndSaveImage(context, attachmentUri, isQuestion = true)
                    }.getOrDefault(attachmentUri.toString())

                    errorQuestionDao.insertQuestion(
                        ErrorQuestionEntity(
                            subject = subject,
                            questionImageUri = savedPath,
                            topic = topic,
                            aiTopic = topic
                        )
                    )
                    trainingQuestionDao.insertQuestion(
                        TrainingQuestionEntity(
                            folderId = folderId,
                            questionText = "$subject - $topic",
                            imageUri = savedPath
                        )
                    )
                }

                ToolExecutionOutcome(
                    note = "Hata Kumbarası organize edildi: **$folderTitle** ($subject • $topic)"
                )
            }

            "addStudyPlanItem" -> {
                val subject = args["subject"]?.ifBlank { "Matematik" } ?: "Matematik"
                val topic = args["topic"]?.ifBlank { "Soru Çözümü" } ?: "Soru Çözümü"
                val dayOfWeek = args["dayOfWeek"]?.toIntOrNull()?.coerceIn(1, 7) ?: 1
                val rawTime = args["scheduledTime"]?.trim().orEmpty()
                val scheduledTime = if (rawTime.isBlank() || Regex("""\d{1,2}:\d{2}\s*[-–—]\s*\d{1,2}:\d{2}""").containsMatchIn(rawTime)) {
                    "Esnek • 90 dk"
                } else {
                    rawTime
                }
                val targetQuestions = (args["targetQuestions"]?.toIntOrNull() ?: 30).coerceAtLeast(20)

                val dayName = dayOfWeekToTurkishName(dayOfWeek)

                val planEntity = PlanEntity(
                    id = UUID.randomUUID().toString(),
                    folderId = "",
                    title = "$subject - $topic",
                    subject = subject,
                    topic = topic,
                    dayOfWeek = dayOfWeek,
                    scheduledTime = scheduledTime,
                    targetQuestions = targetQuestions,
                    orderIndex = 0
                )

                val folderId = planRepositoryImpl.saveGeneratedPlan(
                    folderTitle = "Shark AI Çalışma Planı",
                    tasks = listOf(planEntity)
                )

                targetRepository.addTarget(
                    target = TargetEntity(
                        title = "$dayName • $subject",
                        description = "$topic ($targetQuestions Soru)",
                        type = TargetType.SHORT_TERM,
                        period = TargetPeriod.DAILY,
                        deadlineMillis = System.currentTimeMillis() + (dayOfWeek - 1) * 86_400_000L,
                        targetQuestions = targetQuestions,
                        priority = "HIGH",
                        category = subject
                    ),
                    tasks = listOf("$topic ($targetQuestions Soru)")
                )

                com.example.sharkmentor.core.coaching.StudentDossierManager.syncInBackground(context)

                ToolExecutionOutcome(
                    note = "Çalışma Planına Eklendi: **$dayName ($scheduledTime)** — $subject: $topic ($targetQuestions Soru) #ACTION:NAV_PLANS",
                    planFolderId = folderId
                )
            }

            else -> ToolExecutionOutcome(note = "")
        }
    }

    private fun dayOfWeekToTurkishName(day: Int): String = when (day) {
        1 -> "Pazartesi"
        2 -> "Salı"
        3 -> "Çarşamba"
        4 -> "Perşembe"
        5 -> "Cuma"
        6 -> "Cumartesi"
        7 -> "Pazar"
        else -> "Pazartesi"
    }

    private fun inferMimeType(uri: Uri): String {
        val path = uri.toString().lowercase()
        return when {
            path.endsWith(".pdf") -> "application/pdf"
            path.endsWith(".png") -> "image/png"
            path.endsWith(".webp") -> "image/webp"
            else -> "image/jpeg"
        }
    }
}
