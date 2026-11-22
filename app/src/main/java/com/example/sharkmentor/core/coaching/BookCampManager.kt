package com.example.sharkmentor.core.coaching

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.compose.runtime.Immutable
import com.example.sharkmentor.core.ai.UserAiPreferences
import com.example.sharkmentor.data.local.SharkMentorDatabase
import com.example.sharkmentor.data.local.entity.PlanEntity
import com.example.sharkmentor.data.local.entity.PlanFolderEntity
import com.example.sharkmentor.data.local.entity.ResourceEntity
import com.example.sharkmentor.data.local.entity.TopicGoalEntity
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

@Immutable
data class CampCatalogTemplate(
    val id: String,
    val title: String,
    val instructor: String,
    val subject: String,
    val totalDays: Int,
    /** Brand/Educator-specific keywords ONLY (never generic subject names like "kimya" or "fizik"). */
    val matchKeywords: List<String>,
    val defaultPlaylistId: String = "",
    val defaultVideoId: String = "",
    val dayTopics: List<String> = emptyList()
)

@Immutable
data class PrerequisiteGateResult(
    val effectiveTopic: String,
    val originalTopic: String,
    val isGatedByPrerequisite: Boolean,
    val missingPrerequisite: String? = null,
    val masteryLevel: Int? = null
)

@Immutable
data class ResolvedYouTubeVideo(
    val videoId: String,
    val title: String,
    val channelName: String = "",
    val durationLabel: String = ""
)

@Immutable
data class CampDayEpisode(
    val dayNumber: Int,
    val topicTitle: String,
    val videoId: String = "",
    val playlistId: String = "",
    val customUrl: String = "",
    val searchQuery: String = "",
    val isCompleted: Boolean = false
) {
    fun buildExternalYouTubeUrl(startSeconds: Int = 0): String {
        val timeSuffix = if (startSeconds > 0) "&t=${startSeconds}s" else ""
        if (customUrl.isNotBlank() && (customUrl.startsWith("http://") || customUrl.startsWith("https://"))) {
            if (videoId.isNotBlank() || playlistId.isNotBlank()) return customUrl
        }
        if (videoId.isNotBlank() && playlistId.isNotBlank()) {
            return "https://www.youtube.com/watch?v=$videoId&list=$playlistId$timeSuffix"
        }
        if (videoId.isNotBlank()) {
            return if (startSeconds > 0) "https://www.youtube.com/watch?v=$videoId&t=${startSeconds}s"
            else "https://www.youtube.com/watch?v=$videoId"
        }
        if (playlistId.isNotBlank()) {
            return "https://www.youtube.com/playlist?list=$playlistId&index=$dayNumber"
        }
        val q = Uri.encode(searchQuery.ifBlank { topicTitle })
        return "https://www.youtube.com/results?search_query=$q"
    }

    fun buildInAppEmbedUrl(adBlockerEnabled: Boolean = true, autoplay: Boolean = true): String {
        val host = if (adBlockerEnabled) "https://www.youtube-nocookie.com" else "https://www.youtube.com"
        val autoFlag = if (autoplay) "1" else "0"
        if (videoId.isNotBlank() && playlistId.isNotBlank()) {
            return "$host/embed/$videoId?list=$playlistId&autoplay=$autoFlag&rel=0&modestbranding=1&playsinline=1&iv_load_policy=3&enablejsapi=1"
        }
        if (videoId.isNotBlank()) {
            return "$host/embed/$videoId?autoplay=$autoFlag&rel=0&modestbranding=1&playsinline=1&iv_load_policy=3&enablejsapi=1"
        }
        if (playlistId.isNotBlank()) {
            val zeroIdx = (dayNumber - 1).coerceAtLeast(0)
            return "$host/embed/videoseries?list=$playlistId&index=$zeroIdx&autoplay=$autoFlag&rel=0&modestbranding=1&playsinline=1&iv_load_policy=3&enablejsapi=1"
        }
        val q = Uri.encode(searchQuery.ifBlank { topicTitle })
        return "https://m.youtube.com/results?search_query=$q"
    }

    fun buildMobileWatchUrl(startSeconds: Int = 0): String {
        if (videoId.isNotBlank()) {
            val timeParam = if (startSeconds > 0) "&t=${startSeconds}s" else ""
            return "https://m.youtube.com/watch?v=$videoId$timeParam"
        }
        return buildExternalYouTubeUrl(startSeconds)
    }
}

@Immutable
data class ResourceCampBinding(
    val resourceId: Long,
    val resourceTitle: String,
    val subject: String,
    val campTitle: String,
    val instructor: String,
    val totalDays: Int,
    val currentDay: Int = 1,
    val youtubeUrl: String = "",
    val videoId: String = "",
    val playlistId: String = "",
    val episodes: List<CampDayEpisode> = emptyList(),
    val isAutoMatched: Boolean = false
) {
    fun currentEpisode(): CampDayEpisode {
        val safeDay = currentDay.coerceIn(1, totalDays.coerceAtLeast(1))
        return episodes.firstOrNull { it.dayNumber == safeDay }
            ?: episodes.getOrNull(safeDay - 1)
            ?: CampDayEpisode(
                dayNumber = safeDay,
                topicTitle = "$subject • $safeDay. Gün",
                videoId = videoId,
                playlistId = playlistId,
                customUrl = youtubeUrl,
                searchQuery = "$instructor $subject $safeDay. gün"
            )
    }
}

/**
 * Triple-Hybrid Book Camp Discovery, Pedagogical Curriculum Ordering & Mastery Gatekeeping Engine:
 * 1. Strict Brand/Educator Camp Matching: matches book titles to their actual educator/publisher
 *    (e.g., "Ferrum Kimya" -> Ferrum, NEVER cross-assigning another teacher like Görkem Şahin).
 * 2. Canonical Pedagogical Curriculum Ordering: preserves lesson topic integrity
 *    (e.g., `Fonksiyonlar -> Polinomlar -> Limit -> Türev -> İntegral`), never scheduling `Türev` before `Fonksiyonlar`.
 * 3. Topic Mastery (`Konu Hakimiyeti`) Weighting & Prerequisite Foundation Gatekeeping:
 *    - Low-mastery topics (`currentLevel <= 2`) receive extra days/weight and persistent reinforcement.
 *    - Dependent advanced topics are gated if a connected prerequisite topic has low mastery (`currentLevel <= 2`).
 */
object BookCampManager {

    private const val PREFS_NAME = "shark_mark57_book_camps_prefs"
    private const val KEY_BINDINGS_JSON = "resource_camp_bindings_json"
    private val TR_LOCALE = Locale("tr")

    @Volatile
    private var isLoaded = false

    private val _bindingsByResourceId = MutableStateFlow<Map<Long, ResourceCampBinding>>(emptyMap())
    val bindingsByResourceId: StateFlow<Map<Long, ResourceCampBinding>> = _bindingsByResourceId.asStateFlow()

    // ── Canonical Ordered Curricula (Strict Pedagogical Order) ───────────────

    val tytMathTopics = listOf(
        "Temel Kavramlar",
        "Sayı Basamakları",
        "Bölme ve Bölünebilme",
        "EBOB - EKOK",
        "Rasyonel Sayılar",
        "Birinci Dereceden Denklemler",
        "Basit Eşitsizlikler",
        "Mutlak Değer",
        "Üslü Sayılar",
        "Köklü Sayılar",
        "Çarpanlara Ayırma",
        "Oran - Orantı",
        "Sayı ve Kesir Problemleri",
        "Yaş Problemleri",
        "Yüzde ve Kar-Zarar Problemleri",
        "Karışım ve Hareket Problemleri",
        "İşçi ve Rutin Olmayan Problemler",
        "Kümeler ve Kartezyen Çarpım",
        "Fonksiyonlar",
        "Polinomlar",
        "İkinci Dereceden Denklemler",
        "Permütasyon - Kombinasyon",
        "Binom ve Olasılık",
        "Veri ve İstatistik"
    )

    val aytMathTopics = listOf(
        "Fonksiyonlar ve Fonksiyon Uygulamaları",
        "Polinomlar ve Çarpanlara Ayırma",
        "İkinci Dereceden Denklemler ve Karmaşık Sayılar",
        "Parabol",
        "İkinci Dereceden Eşitsizlikler",
        "Trigonometri - 1 (Birim Çember ve Özdeşlikler)",
        "Trigonometri - 2 (Toplam-Fark ve Yarım Açı)",
        "Trigonometrik Denklemler",
        "Logaritma Fonksiyonu",
        "Logaritmalı Denklemler ve Eşitsizlikler",
        "Diziler (Aritmetik ve Geometrik)",
        "Limit ve Süreklilik",
        "Türev Tanımı ve Alma Kuralları",
        "Türevin Geometrik Yorumu",
        "Maksimum - Minimum Problemleri",
        "Belirsiz İntegral",
        "Belirli İntegral ve Alan Hesabı",
        "Çemberin Analitik İncelenmesi"
    )

    val geometryTopics = listOf(
        "Doğruda ve Üçgende Açılar",
        "Dik ve Özel Üçgenler",
        "İkizkenar ve Eşkenar Üçgen",
        "Üçgende Açıortay ve Kenarortay",
        "Üçgende Eşlik ve Benzerlik",
        "Üçgende Alan",
        "Çokgenler ve Genel Dörtgenler",
        "Yamuk ve Paralelkenar",
        "Eşkenar Dörtgen, Dikdörtgen ve Kare",
        "Çemberde Açı ve Uzunluk",
        "Dairede Alan",
        "Noktanın ve Doğrunun Analitiği",
        "Dönüşüm Geometrisi",
        "Katı Cisimler (Prizma, Piramit, Küre)"
    )

    val physicsTopics = listOf(
        "Fizik Bilimine Giriş ve Madde Özellikleri",
        "Vektörler",
        "Kuvvet ve Hareket (Doğrusal ve Bağıl Hareket)",
        "Newton'un Hareket Yasaları",
        "Bir ve İki Boyutta Sabit İvmeli Hareket (Atışlar)",
        "İş, Güç ve Enerji",
        "İtme ve Çizgisel Momentum",
        "Tork, Denge ve Kütle Merkezi",
        "Basit Makineler",
        "Basınç ve Kaldırma Kuvveti",
        "Isı, Sıcaklık ve Genleşme",
        "Elektrostatik ve Elektrik Akımı",
        "Elektrik Alan, Potansiyel ve Sığa",
        "Manyetizma ve Elektromanyetik İndüksiyon",
        "Alternatif Akım ve Transformatörler",
        "Çembersel Hareket ve Kepler",
        "Basit Harmonik Hareket",
        "Dalgalar ve Dalga Mekaniği",
        "Optik (Aydınlanma, Ayna, Mercek)",
        "Modern Fizik ve Atom Fiziği"
    )

    val chemistryTopics = listOf(
        "Kimya Bilimi",
        "Atom ve Periyodik Sistem",
        "Kimyasal Türler Arası Etkileşimler",
        "Maddenin Halleri",
        "Kimyanın Temel Kanunları ve Mol Kavramı",
        "Kimyasal Tepkimeler ve Hesaplamalar",
        "Karışımlar ve Ayırma Teknikleri",
        "Asitler, Bazlar ve Tuzlar",
        "Modern Atom Teorisi",
        "Gazlar",
        "Sıvı Çözeltiler ve Çözünürlük",
        "Kimyasal Tepkimelerde Enerji",
        "Kimyasal Tepkimelerde Hız",
        "Kimyasal Tepkimelerde Denge",
        "Asit-Baz ve Çözünürlük Dengesi (Kçç)",
        "Kimya ve Elektrik (Elektrokimya)",
        "Karbon Kimyasına Giriş ve Organik Kimya"
    )

    val biologyTopics = listOf(
        "Canlıların Ortak Özellikleri ve Temel Bileşenler",
        "Hücre ve Organeller",
        "Hücre Zarından Madde Geçişleri",
        "Canlıların Sınıflandırılması",
        "Hücre Bölünmeleri (Mitoz ve Mayoz)",
        "Eşeyli ve Eşeysiz Üreme",
        "Kalıtım ve Genetik",
        "Ekosistem ve Komünite-Popülasyon Ekolojisi",
        "Sinir Sistemi ve Endokrin Sistem",
        "Duyu Organları ve Destek-Hareket Sistemi",
        "Sindirim ve Dolaşım Sistemi",
        "Solunum ve Üriner (Boşaltım) Sistemi",
        "Genden Proteine (Nükleik Asitler ve Protein Sentezi)",
        "Canlılarda Enerji Dönüşümleri (Fotosentez ve Solunum)",
        "Bitki Biyolojisi"
    )

    val turkishTopics = listOf(
        "Sözcükte Anlam",
        "Cümlede Anlam",
        "Paragrafta Yapı, Konu ve Ana Düşünce",
        "Paragrafta Yardımcı Düşünce",
        "Ses Bilgisi",
        "Sözcükte Yapı ve Ekler",
        "Yazım Kuralları",
        "Noktalama İşaretleri",
        "İsim, Sıfat, Zamir ve Zarf",
        "Fiiller, Fiilimsiler ve Ek Fiil",
        "Cümlenin Ögeleri",
        "Fiilde Çatı ve Cümle Türleri",
        "Anlatım Bozuklukları"
    )

    /**
     * Explicit prerequisite dependency graph: Key = dependent topic keyword, Value = ordered list of
     * foundational prerequisite topic names that MUST NOT have low mastery (`currentLevel <= 2`) before
     * advancing to the dependent topic.
     */
    private val topicPrerequisites: List<Pair<List<String>, List<String>>> = listOf(
        // Matematik
        listOf("türev", "turev") to listOf("Fonksiyonlar", "Limit ve Süreklilik"),
        listOf("integral") to listOf("Fonksiyonlar", "Limit ve Süreklilik", "Türev"),
        listOf("limit") to listOf("Fonksiyonlar", "Polinomlar"),
        listOf("parabol") to listOf("Fonksiyonlar", "İkinci Dereceden Denklemler"),
        listOf("ikinci dereceden eşitsizlik", "eşitsizlik sistem") to listOf("Fonksiyonlar", "İkinci Dereceden Denklemler"),
        listOf("trigonometri") to listOf("Fonksiyonlar"),
        listOf("logaritma") to listOf("Fonksiyonlar", "Üslü Sayılar"),
        listOf("diziler") to listOf("Fonksiyonlar"),
        listOf("polinom") to listOf("Fonksiyonlar", "Çarpanlara Ayırma"),
        listOf("köklü", "koklu") to listOf("Üslü Sayılar"),
        listOf("problem") to listOf("Temel Kavramlar", "Birinci Dereceden Denklemler", "Oran - Orantı"),
        // Geometri
        listOf("üçgende benzerlik", "üçgende alan") to listOf("Doğruda ve Üçgende Açılar", "Dik ve Özel Üçgenler"),
        listOf("dörtgen", "yamuk", "paralelkenar", "kare") to listOf("Dik ve Özel Üçgenler", "Üçgende Eşlik ve Benzerlik"),
        listOf("dairede alan", "katı cisim") to listOf("Çemberde Açı ve Uzunluk"),
        // Kimya
        listOf("kimyasal türler", "etkileşim") to listOf("Atom ve Periyodik Sistem"),
        listOf("kimyasal hesaplama") to listOf("Kimyanın Temel Kanunları ve Mol Kavramı"),
        listOf("gazlar", "sıvı çözelti") to listOf("Maddenin Halleri", "Kimyanın Temel Kanunları ve Mol Kavramı"),
        listOf("tepkimelerde hız", "tepkimelerde denge") to listOf("Kimyasal Tepkimeler ve Hesaplamalar", "Kimyasal Tepkimelerde Enerji"),
        listOf("asit-baz ve çözünürlük dengesi", "kçç", "asit baz dengesi") to listOf("Asitler, Bazlar ve Tuzlar", "Kimyasal Tepkimelerde Denge"),
        listOf("elektrokimya", "kimya ve elektrik") to listOf("Modern Atom Teorisi", "Kimyasal Tepkimelerde Denge"),
        listOf("organik kimya", "karbon kimyası") to listOf("Atom ve Periyodik Sistem", "Kimyasal Türler Arası Etkileşimler"),
        // Fizik
        listOf("newton") to listOf("Vektörler", "Kuvvet ve Hareket"),
        listOf("atışlar", "sabit ivmeli") to listOf("Kuvvet ve Hareket", "Newton'un Hareket Yasaları"),
        listOf("iş, güç ve enerji", "iş güç enerji") to listOf("Kuvvet ve Hareket", "Newton'un Hareket Yasaları"),
        listOf("itme ve çizgisel momentum", "momentum") to listOf("Newton'un Hareket Yasaları", "İş, Güç ve Enerji"),
        listOf("çembersel hareket", "harmonik hareket") to listOf("Newton'un Hareket Yasaları"),
        listOf("elektrik alan", "sığa", "kondansatör") to listOf("Elektrostatik ve Elektrik Akımı"),
        listOf("manyetik alan", "indüksiyon", "alternatif akım") to listOf("Elektrostatik ve Elektrik Akımı", "Elektrik Alan, Potansiyel ve Sığa"),
        // Biyoloji
        listOf("madde geçiş") to listOf("Hücre ve Organeller"),
        listOf("hücre bölünme", "mitoz", "mayoz") to listOf("Hücre ve Organeller"),
        listOf("kalıtım", "genetik") to listOf("Hücre Bölünmeleri (Mitoz ve Mayoz)"),
        listOf("genden proteine", "protein sentezi") to listOf("Hücre ve Organeller", "Kalıtım ve Genetik"),
        listOf("fotosentez", "hücresel solunum", "enerji dönüşüm") to listOf("Canlıların Ortak Özellikleri ve Temel Bileşenler", "Hücre ve Organeller"),
        // Türkçe
        listOf("cümlenin ögeleri", "fiilde çatı", "cümle türleri") to listOf("Sözcükte Yapı ve Ekler", "Fiiller, Fiilimsiler ve Ek Fiil")
    )

    // ── Verified Camp Catalog (Brand-Accurate Match Keywords Only!) ──────────

    val BuiltInCampCatalog: List<CampCatalogTemplate> = listOf(
        // KIMYA
        CampCatalogTemplate(
            id = "ferrum_tyt_ayt_kimya",
            title = "Ferrum Kimya Video Ders Kampı",
            instructor = "Ferrum (Yakup Demir)",
            subject = "Kimya",
            totalDays = 36,
            matchKeywords = listOf("ferrum", "yakup demir"),
            dayTopics = expandTopicsToDays(chemistryTopics, 36)
        ),
        CampCatalogTemplate(
            id = "gorkem_sahin_kimya",
            title = "Görkem Şahin Kimya Video Ders Kampı",
            instructor = "Görkem Şahin",
            subject = "Kimya",
            totalDays = 36,
            matchKeywords = listOf("görkem şahin", "gorkem sahin", "görkem hoca", "benim hocam kimya"),
            dayTopics = expandTopicsToDays(chemistryTopics, 36)
        ),
        CampCatalogTemplate(
            id = "kimya_adasi_kamp",
            title = "Kimya Adası Video Ders Kampı",
            instructor = "Kimya Adası",
            subject = "Kimya",
            totalDays = 36,
            matchKeywords = listOf("kimya adası", "kimya adasi", "murat hoca kimya"),
            dayTopics = expandTopicsToDays(chemistryTopics, 36)
        ),
        CampCatalogTemplate(
            id = "paraksilen_kimya_kamp",
            title = "Paraksilen Kimya Video Ders Kampı",
            instructor = "Paraksilen Kimya",
            subject = "Kimya",
            totalDays = 34,
            matchKeywords = listOf("paraksilen"),
            dayTopics = expandTopicsToDays(chemistryTopics, 34)
        ),
        CampCatalogTemplate(
            id = "orbital_kimya_kamp",
            title = "Orbital Yayınları Kimya Konu & Soru Kampı",
            instructor = "Orbital Yayınları",
            subject = "Kimya",
            totalDays = 36,
            matchKeywords = listOf("orbital"),
            dayTopics = expandTopicsToDays(chemistryTopics, 36)
        ),
        CampCatalogTemplate(
            id = "aydin_kimya_kamp",
            title = "Aydın Yayınları Kimya Kampı",
            instructor = "Aydın Yayınları",
            subject = "Kimya",
            totalDays = 36,
            matchKeywords = listOf("aydın kimya", "aydin kimya", "aydın yayınları kimya"),
            dayTopics = expandTopicsToDays(chemistryTopics, 36)
        ),
        CampCatalogTemplate(
            id = "palme_kimya_kamp",
            title = "Palme Kimya Konu & Soru Kampı",
            instructor = "Palme Yayınları",
            subject = "Kimya",
            totalDays = 34,
            matchKeywords = listOf("palme kimya"),
            dayTopics = expandTopicsToDays(chemistryTopics, 34)
        ),
        CampCatalogTemplate(
            id = "miray_kimya_kamp",
            title = "Miray Yayınları Kimya Kampı",
            instructor = "Miray Yayınları",
            subject = "Kimya",
            totalDays = 34,
            matchKeywords = listOf("miray kimya", "miray yayınları"),
            dayTopics = expandTopicsToDays(chemistryTopics, 34)
        ),

        // MATEMATIK & GEOMETRI
        CampCatalogTemplate(
            id = "3d_tyt_mat_vdd",
            title = "3D TYT Matematik VDD Kampı",
            instructor = "Eyüp B.",
            subject = "TYT Matematik",
            totalDays = 45,
            matchKeywords = listOf("3d tyt", "3d vdd", "eyüp b", "eyup b", "3d matematik"),
            dayTopics = expandTopicsToDays(tytMathTopics, 45)
        ),
        CampCatalogTemplate(
            id = "3d_ayt_mat_vdd",
            title = "3D AYT Matematik VDD Kampı",
            instructor = "Eyüp B.",
            subject = "AYT Matematik",
            totalDays = 50,
            matchKeywords = listOf("3d ayt", "ayt vdd", "eyüp b ayt"),
            dayTopics = expandTopicsToDays(aytMathTopics, 50)
        ),
        CampCatalogTemplate(
            id = "mert_hoca_70_tyt",
            title = "70 Günde TYT Matematik Kampı",
            instructor = "Mert Hoca",
            subject = "TYT Matematik",
            totalDays = 70,
            matchKeywords = listOf("mert hoca", "70 günde tyt", "70 gunde tyt"),
            dayTopics = expandTopicsToDays(tytMathTopics, 70)
        ),
        CampCatalogTemplate(
            id = "mert_hoca_60_ayt",
            title = "60 Günde AYT Matematik Kampı",
            instructor = "Mert Hoca",
            subject = "AYT Matematik",
            totalDays = 60,
            matchKeywords = listOf("mert hoca ayt", "60 günde ayt", "60 gunde ayt"),
            dayTopics = expandTopicsToDays(aytMathTopics, 60)
        ),
        CampCatalogTemplate(
            id = "rehber_mat_49_tyt",
            title = "49 Günde TYT Matematik Kampı",
            instructor = "Rehber Matematik",
            subject = "TYT Matematik",
            totalDays = 49,
            matchKeywords = listOf("rehber matematik", "49 günde tyt", "49 gunde tyt"),
            dayTopics = expandTopicsToDays(tytMathTopics, 49)
        ),
        CampCatalogTemplate(
            id = "biyikli_mat_kamp",
            title = "Bıyıklı Matematik Video Ders Kampı",
            instructor = "Bıyıklı Matematik",
            subject = "Matematik",
            totalDays = 50,
            matchKeywords = listOf("bıyıklı matematik", "biyikli matematik", "bıyıklı"),
            dayTopics = expandTopicsToDays(tytMathTopics, 50)
        ),
        CampCatalogTemplate(
            id = "orijinal_mat_kamp",
            title = "Orijinal Matematik Soru & Konu Kampı",
            instructor = "Orijinal Yayınları",
            subject = "Matematik",
            totalDays = 40,
            matchKeywords = listOf("orijinal", "orjinal", "mikro orijinal"),
            dayTopics = expandTopicsToDays(tytMathTopics + aytMathTopics.take(10), 40)
        ),
        CampCatalogTemplate(
            id = "apotemi_fasikul_kamp",
            title = "Apotemi Fasikül Maraton Kampı",
            instructor = "Apotemi Yayınları",
            subject = "AYT Matematik",
            totalDays = 30,
            matchKeywords = listOf("apotemi"),
            dayTopics = expandTopicsToDays(aytMathTopics, 30)
        ),
        CampCatalogTemplate(
            id = "baris_yayinlari_mat",
            title = "Barış Yayınları Matematik Kampı",
            instructor = "Barış Çelenk",
            subject = "Matematik",
            totalDays = 36,
            matchKeywords = listOf("barış yayınları", "baris yayinlari", "barış çelenk"),
            dayTopics = expandTopicsToDays(aytMathTopics, 36)
        ),
        CampCatalogTemplate(
            id = "acil_mat_kamp",
            title = "Acil Matematik Konu & Soru Kampı",
            instructor = "Acil Yayınları",
            subject = "Matematik",
            totalDays = 40,
            matchKeywords = listOf("acil matematik", "acil yayınları", "matematiğin ilacı"),
            dayTopics = expandTopicsToDays(tytMathTopics, 40)
        ),
        CampCatalogTemplate(
            id = "345_mat_kamp",
            title = "345 (ÜçDörtBeş) Matematik Kampı",
            instructor = "ÜçDörtBeş Yayınları",
            subject = "Matematik",
            totalDays = 42,
            matchKeywords = listOf("345 matematik", "üçdörtbeş matematik", "ucdortbes matematik"),
            dayTopics = expandTopicsToDays(tytMathTopics, 42)
        ),
        CampCatalogTemplate(
            id = "bilgi_sarmal_mat",
            title = "Bilgi Sarmal Matematik Kampı",
            instructor = "Bilgi Sarmal Yayınları",
            subject = "Matematik",
            totalDays = 40,
            matchKeywords = listOf("bilgi sarmal matematik", "bilgi sarmal"),
            dayTopics = expandTopicsToDays(tytMathTopics, 40)
        ),
        CampCatalogTemplate(
            id = "kenan_kara_50_geo",
            title = "50 Günde TYT-AYT Geometri Kampı",
            instructor = "Kenan Kara",
            subject = "Geometri",
            totalDays = 50,
            matchKeywords = listOf("kenan kara"),
            dayTopics = expandTopicsToDays(geometryTopics, 50)
        ),
        CampCatalogTemplate(
            id = "merkeze_teget_geo",
            title = "Merkeze Teğet Geometri Kampı",
            instructor = "Merkeze Teğet",
            subject = "Geometri",
            totalDays = 40,
            matchKeywords = listOf("merkeze teğet", "merkeze teget"),
            dayTopics = expandTopicsToDays(geometryTopics, 40)
        ),

        // FIZIK
        CampCatalogTemplate(
            id = "ozcan_aykin_fizik",
            title = "Özcan Aykın Fizik Video Ders Kampı",
            instructor = "Özcan Aykın",
            subject = "Fizik",
            totalDays = 40,
            matchKeywords = listOf("özcan aykın", "ozcan aykin"),
            dayTopics = expandTopicsToDays(physicsTopics, 40)
        ),
        CampCatalogTemplate(
            id = "vip_fizik_50",
            title = "50 Günde VIP Fizik Kampı",
            instructor = "VIP Fizik",
            subject = "Fizik",
            totalDays = 50,
            matchKeywords = listOf("vip fizik"),
            dayTopics = expandTopicsToDays(physicsTopics, 50)
        ),
        CampCatalogTemplate(
            id = "altug_gunes_fizik",
            title = "Altuğ Güneş Fizik Video Ders Kampı",
            instructor = "Altuğ Güneş",
            subject = "Fizik",
            totalDays = 38,
            matchKeywords = listOf("altuğ güneş", "altug gunes"),
            dayTopics = expandTopicsToDays(physicsTopics, 38)
        ),
        CampCatalogTemplate(
            id = "ertan_sinan_fizik",
            title = "Ertan Sinan Şahin Fizik Kampı",
            instructor = "Ertan Sinan Şahin",
            subject = "Fizik",
            totalDays = 40,
            matchKeywords = listOf("ertan sinan"),
            dayTopics = expandTopicsToDays(physicsTopics, 40)
        ),
        CampCatalogTemplate(
            id = "fizikfinito_kamp",
            title = "Fizikfinito Video Ders Kampı",
            instructor = "Fizikfinito",
            subject = "Fizik",
            totalDays = 36,
            matchKeywords = listOf("fizikfinito"),
            dayTopics = expandTopicsToDays(physicsTopics, 36)
        ),

        // BIYOLOJI
        CampCatalogTemplate(
            id = "dr_biyoloji_kamp",
            title = "Dr. Biyoloji Video Ders Kampı",
            instructor = "Dr. Biyoloji",
            subject = "Biyoloji",
            totalDays = 30,
            matchKeywords = listOf("dr biyoloji", "dr. biyoloji"),
            dayTopics = expandTopicsToDays(biologyTopics, 30)
        ),
        CampCatalogTemplate(
            id = "biosem_biyoloji",
            title = "Biosem Biyoloji Video Ders Kampı",
            instructor = "Biosem (Semih Hoca)",
            subject = "Biyoloji",
            totalDays = 32,
            matchKeywords = listOf("biosem"),
            dayTopics = expandTopicsToDays(biologyTopics, 32)
        ),
        CampCatalogTemplate(
            id = "selin_hoca_biyoloji",
            title = "Selin Hoca Biyoloji Kampı",
            instructor = "Selin Hoca",
            subject = "Biyoloji",
            totalDays = 30,
            matchKeywords = listOf("selin hoca"),
            dayTopics = expandTopicsToDays(biologyTopics, 30)
        ),
        CampCatalogTemplate(
            id = "betul_biyoloji",
            title = "Betül Biyoloji Kampı",
            instructor = "Betül Biyoloji",
            subject = "Biyoloji",
            totalDays = 30,
            matchKeywords = listOf("betül biyoloji", "betul biyoloji"),
            dayTopics = expandTopicsToDays(biologyTopics, 30)
        ),
        CampCatalogTemplate(
            id = "biyotik_biyoloji",
            title = "Biyotik Yayınları Biyoloji Kampı",
            instructor = "Biyotik Yayınları",
            subject = "Biyoloji",
            totalDays = 30,
            matchKeywords = listOf("biyotik"),
            dayTopics = expandTopicsToDays(biologyTopics, 30)
        ),

        // TURKCE & SOSYAL & TUS
        CampCatalogTemplate(
            id = "rustu_hoca_49_turkce",
            title = "Rüştü Hoca Türkçe & Paragraf Kampı",
            instructor = "Rüştü Hoca",
            subject = "Türkçe",
            totalDays = 49,
            matchKeywords = listOf("rüştü hoca", "rustu hoca", "sıfır risk"),
            dayTopics = expandTopicsToDays(turkishTopics, 49)
        ),
        CampCatalogTemplate(
            id = "kadir_gumus_turkce",
            title = "Kadir Gümüş Türkçe & Edebiyat Kampı",
            instructor = "Kadir Gümüş",
            subject = "Türkçe",
            totalDays = 40,
            matchKeywords = listOf("kadir gümüş", "kadir gumus"),
            dayTopics = expandTopicsToDays(turkishTopics, 40)
        ),
        CampCatalogTemplate(
            id = "aker_kartal_turkce",
            title = "Aker Kartal Türkçe Kampı",
            instructor = "Aker Kartal",
            subject = "Türkçe",
            totalDays = 35,
            matchKeywords = listOf("aker kartal"),
            dayTopics = expandTopicsToDays(turkishTopics, 35)
        ),
        CampCatalogTemplate(
            id = "turkcenin_matematigi",
            title = "Türkçenin Matematiği Video Ders Kampı",
            instructor = "Türkçenin Matematiği",
            subject = "Türkçe",
            totalDays = 35,
            matchKeywords = listOf("türkçenin matematiği", "turkcenin matematigi"),
            dayTopics = expandTopicsToDays(turkishTopics, 35)
        ),
        CampCatalogTemplate(
            id = "cografyanin_kodlari",
            title = "Coğrafyanın Kodları Video Kampı",
            instructor = "Coğrafyanın Kodları",
            subject = "Coğrafya",
            totalDays = 25,
            matchKeywords = listOf("coğrafyanın kodları", "cografyanin kodlari", "haritalarla coğrafya"),
            dayTopics = (1..25).map { "Coğrafya Kampı • $it. Gün Konu & Harita" }
        ),
        CampCatalogTemplate(
            id = "ramazan_yetgin_tarih",
            title = "Ramazan Yetgin Tarih Video Ders Kampı",
            instructor = "Ramazan Yetgin",
            subject = "Tarih",
            totalDays = 35,
            matchKeywords = listOf("ramazan yetgin"),
            dayTopics = (1..35).map { "Tarih Kampı • $it. Gün Konu & Soru" }
        ),
        CampCatalogTemplate(
            id = "soner_ardic_tarih",
            title = "Soner Ardıç Tarih Video Ders Kampı",
            instructor = "Soner Ardıç",
            subject = "Tarih",
            totalDays = 30,
            matchKeywords = listOf("soner ardıç", "soner ardic"),
            dayTopics = (1..30).map { "Tarih Kampı • $it. Gün Konu & Soru" }
        ),
        CampCatalogTemplate(
            id = "tus_hizli_kamp",
            title = "TUS Temel & Klinik Kampı",
            instructor = "TUS Akademi",
            subject = "TUS",
            totalDays = 30,
            matchKeywords = listOf("tusdata", "tusem", "tustime", "tus dünyası"),
            dayTopics = (1..30).map { "TUS Kampı • $it. Gün Vaka & Spot Tekrar" }
        )
    )

    // ── Canonical Curriculum Ordering & Prerequisite Gatekeeping ─────────────

    fun canonicalCurriculumForSubject(subject: String, contextHint: String = ""): List<String> {
        val lower = "$subject $contextHint".lowercase(TR_LOCALE)
        return when {
            lower.contains("geo") -> geometryTopics
            lower.contains("fiz") -> physicsTopics
            lower.contains("kim") -> chemistryTopics
            lower.contains("biyo") -> biologyTopics
            lower.contains("türk") || lower.contains("turk") || lower.contains("paragraf") -> turkishTopics
            lower.contains("ayt") && lower.contains("mat") -> aytMathTopics
            lower.contains("mat") && (lower.contains("türev") || lower.contains("integral") || lower.contains("limit") || lower.contains("trigonometri") || lower.contains("logaritma")) ->
                tytMathTopics + aytMathTopics
            lower.contains("mat") -> tytMathTopics + aytMathTopics
            else -> emptyList()
        }
    }

    /**
     * Returns the 0-based canonical pedagogical order index for [topicName] within [subject].
     * Guarantees e.g. `Fonksiyonlar` < `Polinomlar` < `Limit` < `Türev` < `İntegral`.
     */
    fun canonicalTopicIndex(subject: String, topicName: String): Int {
        val cleanTopic = topicName.lowercase(TR_LOCALE)
            .replace(Regex("""^\d+[\.\)\-\s]+([Gg]ün[\s:\-]*)?"""), "")
            .trim()
        val curriculum = canonicalCurriculumForSubject(subject, cleanTopic)
        if (curriculum.isNotEmpty()) {
            // 1. Direct or keyword match against canonical list
            val directIdx = curriculum.indexOfFirst { canon ->
                val canonLower = canon.lowercase(TR_LOCALE)
                val coreCanon = canonLower.substringBefore(" (").trim()
                cleanTopic.contains(coreCanon) || coreCanon.contains(cleanTopic)
            }
            if (directIdx >= 0) return directIdx
        }

        // 2. Universal cross-curriculum keyword anchor fallback (guarantees Fonksiyonlar < Limit < Türev < İntegral)
        return when {
            cleanTopic.contains("temel kavram") -> 1
            cleanTopic.contains("sayı basamak") -> 2
            cleanTopic.contains("bölme") || cleanTopic.contains("bölünebilme") -> 3
            cleanTopic.contains("ebob") || cleanTopic.contains("ekok") -> 4
            cleanTopic.contains("rasyonel") -> 5
            cleanTopic.contains("birinci dereceden") -> 6
            cleanTopic.contains("basit eşitsizlik") -> 7
            cleanTopic.contains("mutlak değer") -> 8
            cleanTopic.contains("üslü") -> 9
            cleanTopic.contains("köklü") -> 10
            cleanTopic.contains("çarpanlara ayırma") -> 11
            cleanTopic.contains("oran") -> 12
            cleanTopic.contains("problem") -> 13
            cleanTopic.contains("küme") -> 14
            cleanTopic.contains("fonksiyon") -> 15
            cleanTopic.contains("polinom") -> 16
            cleanTopic.contains("ikinci dereceden denklem") || cleanTopic.contains("karmaşık") -> 17
            cleanTopic.contains("parabol") -> 18
            cleanTopic.contains("eşitsizlik") -> 19
            cleanTopic.contains("trigonometri") -> 20
            cleanTopic.contains("logaritma") -> 21
            cleanTopic.contains("dizi") -> 22
            cleanTopic.contains("permütasyon") || cleanTopic.contains("kombinasyon") || cleanTopic.contains("olasılık") -> 23
            cleanTopic.contains("limit") || cleanTopic.contains("süreklilik") -> 24
            cleanTopic.contains("türev") || cleanTopic.contains("turev") -> 25
            cleanTopic.contains("integral") -> 26
            // Kimya anchors
            cleanTopic.contains("kimya bilimi") -> 1
            cleanTopic.contains("atom") && !cleanTopic.contains("modern") -> 2
            cleanTopic.contains("periyodik") -> 3
            cleanTopic.contains("türler arası") || cleanTopic.contains("etkileşim") -> 4
            cleanTopic.contains("maddenin halleri") -> 5
            cleanTopic.contains("temel kanun") || cleanTopic.contains("mol") -> 6
            cleanTopic.contains("kimyasal tepkime") || cleanTopic.contains("hesaplama") -> 7
            cleanTopic.contains("karışım") -> 8
            cleanTopic.contains("asit") && !cleanTopic.contains("denge") -> 9
            cleanTopic.contains("modern atom") -> 10
            cleanTopic.contains("gazlar") -> 11
            cleanTopic.contains("sıvı çözelti") || cleanTopic.contains("çözünürlük") -> 12
            cleanTopic.contains("enerji") -> 13
            cleanTopic.contains("hız") -> 14
            cleanTopic.contains("denge") && !cleanTopic.contains("asit") -> 15
            cleanTopic.contains("asit-baz") || cleanTopic.contains("kçç") -> 16
            cleanTopic.contains("elektrokimya") || cleanTopic.contains("kimya ve elektrik") -> 17
            cleanTopic.contains("organik") || cleanTopic.contains("karbon") -> 18
            else -> 500
        }
    }

    /**
     * Sorts any list of topics for [subject] in strict pedagogical curriculum order so advanced
     * dependent topics (e.g. `Türev`) NEVER precede their foundational prerequisites (e.g. `Fonksiyonlar`).
     */
    fun sortTopicsByCurriculumOrder(subject: String, rawTopics: List<String>): List<String> {
        if (rawTopics.size <= 1) return rawTopics
        return rawTopics
            .withIndex()
            .sortedWith(
                compareBy<IndexedValue<String>> { canonicalTopicIndex(subject, it.value) }
                    .thenBy { it.index }
            )
            .map { it.value }
    }

    /**
     * Checks if [requestedTopic] has any prerequisite topic whose mastery in [studentTopicGoals]
     * is weak (`currentLevel in 0..2`). If a prerequisite foundation is NOT established ("zemin oturmamış"),
     * blocks advancing to [requestedTopic] and returns the prerequisite topic for reinforcement first.
     */
    fun enforcePrerequisiteFoundationGate(
        subject: String,
        requestedTopic: String,
        studentTopicGoals: List<TopicGoalEntity>
    ): PrerequisiteGateResult {
        val cleanRequested = requestedTopic.trim()
        if (cleanRequested.isBlank() || studentTopicGoals.isEmpty()) {
            return PrerequisiteGateResult(
                effectiveTopic = cleanRequested,
                originalTopic = cleanRequested,
                isGatedByPrerequisite = false
            )
        }

        val reqLower = cleanRequested.lowercase(TR_LOCALE)
        val subjectGoals = studentTopicGoals.filter { goal ->
            !goal.isDeleted && (
                goal.subject.contains(subject, ignoreCase = true) ||
                    subject.contains(goal.subject, ignoreCase = true) ||
                    (subject.contains("Mat", ignoreCase = true) && goal.subject.contains("Mat", ignoreCase = true))
                )
        }.ifEmpty { studentTopicGoals.filter { !it.isDeleted } }

        // 1. Check explicit prerequisite graph rules first
        for ((dependentKeywords, requiredPrereqs) in topicPrerequisites) {
            if (dependentKeywords.any { kw -> reqLower.contains(kw) }) {
                for (prereqName in requiredPrereqs) {
                    val prereqCore = prereqName.lowercase(TR_LOCALE).substringBefore(" (").trim()
                    val matchedWeakGoal = subjectGoals.firstOrNull { goal ->
                        val gTopic = goal.topic.lowercase(TR_LOCALE)
                        (gTopic.contains(prereqCore) || prereqCore.contains(gTopic)) &&
                            goal.currentLevel in 0..2
                    }
                    if (matchedWeakGoal != null) {
                        return PrerequisiteGateResult(
                            effectiveTopic = "${matchedWeakGoal.topic} (Temel Zemin Takviyesi • $cleanRequested Ön Koşulu)",
                            originalTopic = cleanRequested,
                            isGatedByPrerequisite = true,
                            missingPrerequisite = matchedWeakGoal.topic,
                            masteryLevel = matchedWeakGoal.currentLevel
                        )
                    }
                }
            }
        }

        // 2. Check if the requested topic itself is recorded in Konu Hakimiyeti
        val ownGoal = subjectGoals.firstOrNull { goal ->
            val gLower = goal.topic.lowercase(TR_LOCALE)
            reqLower.contains(gLower) || gLower.contains(reqLower.substringBefore(" (").trim())
        }

        return PrerequisiteGateResult(
            effectiveTopic = if (ownGoal != null && ownGoal.currentLevel in 0..2) {
                "$cleanRequested (Yoğun Pekiştirme • Hakimiyet ${ownGoal.currentLevel}/5)"
            } else {
                cleanRequested
            },
            originalTopic = cleanRequested,
            isGatedByPrerequisite = false,
            missingPrerequisite = null,
            masteryLevel = ownGoal?.currentLevel
        )
    }

    /**
     * Expands topics into [totalDays] daily episodes while:
     * 1. Sorting topics in canonical pedagogical sequence (`Fonksiyonlar -> Limit -> Türev -> İntegral`).
     * 2. Giving 2x–3x extra weight/days to topics with low mastery (`currentLevel <= 2`) in `Konu Hakimiyeti`.
     * 3. Enforcing prerequisite foundation gatekeeping so dependent topics do not start before weak prerequisites.
     */
    fun expandTopicsToDaysWithMastery(
        subject: String,
        topics: List<String>,
        totalDays: Int,
        studentTopicGoals: List<TopicGoalEntity> = emptyList()
    ): List<String> {
        val safeDays = totalDays.coerceAtLeast(1)
        if (topics.isEmpty()) return (1..safeDays).map { "$it. Gün Konu Anlatımı" }

        // 1. Canonical pedagogical sort
        val orderedTopics = sortTopicsByCurriculumOrder(subject, topics)

        if (studentTopicGoals.isEmpty()) {
            return expandTopicsToDays(orderedTopics, safeDays)
        }

        val subjectGoals = studentTopicGoals.filter { goal ->
            !goal.isDeleted && (
                goal.subject.contains(subject, ignoreCase = true) ||
                    subject.contains(goal.subject, ignoreCase = true) ||
                    (subject.contains("Mat", ignoreCase = true) && goal.subject.contains("Mat", ignoreCase = true))
                )
        }.ifEmpty { studentTopicGoals.filter { !it.isDeleted } }

        // 2. Build weighted sequence where weak topics (level 0..2) get extra reinforcement slots
        //    and any unestablished prerequisite is inserted before its dependent topic
        val weightedList = mutableListOf<String>()
        val reinforcedPrereqs = mutableSetOf<String>()

        for (rawTopic in orderedTopics) {
            val gate = enforcePrerequisiteFoundationGate(subject, rawTopic, subjectGoals)
            if (gate.isGatedByPrerequisite && gate.missingPrerequisite != null) {
                val prereqKey = gate.missingPrerequisite.lowercase(TR_LOCALE)
                if (reinforcedPrereqs.add(prereqKey)) {
                    weightedList.add("${gate.missingPrerequisite} (Temel Zemin Pekiştirme)")
                    weightedList.add("${gate.missingPrerequisite} (Ön Koşul Soru Tarama)")
                }
            }

            val topicLower = rawTopic.lowercase(TR_LOCALE).substringBefore(" (").trim()
            val matchedGoal = subjectGoals.firstOrNull { g ->
                val gLower = g.topic.lowercase(TR_LOCALE)
                topicLower.contains(gLower) || gLower.contains(topicLower)
            }
            val level = matchedGoal?.currentLevel

            when {
                level != null && level in 0..2 -> {
                    // Low mastery: don't let go of the topic — give 3 consecutive reinforcement steps
                    weightedList.add("$rawTopic (Temel Konu & Zemin)")
                    weightedList.add("$rawTopic (Pekiştirme Soru Çözümü)")
                    weightedList.add("$rawTopic (Eksik Kapatma Tarama)")
                }
                level == 3 -> {
                    weightedList.add("$rawTopic (Konu & Soru)")
                    weightedList.add("$rawTopic (Pekiştirme Testi)")
                }
                else -> {
                    weightedList.add(rawTopic)
                }
            }
        }

        return expandTopicsToDays(weightedList.ifEmpty { orderedTopics }, safeDays)
    }

    private fun expandTopicsToDays(topics: List<String>, totalDays: Int): List<String> {
        val safeDays = totalDays.coerceAtLeast(1)
        if (topics.isEmpty()) return (1..safeDays).map { "$it. Gün Konu Anlatımı" }
        return (0 until safeDays).map { idx ->
            val topicIdx = (idx * topics.size) / safeDays
            val topic = topics[topicIdx.coerceIn(0, topics.lastIndex)]
            val part = (idx % 2) + 1
            if (safeDays > topics.size * 1.3 && !topic.contains("Bölüm") && !topic.contains("Zemin") && !topic.contains("Pekiştirme")) {
                "$topic ($part. Bölüm)"
            } else {
                topic
            }
        }
    }

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun ensureLoaded(context: Context) {
        if (isLoaded) return
        synchronized(this) {
            if (isLoaded) return
            _bindingsByResourceId.value = loadBindingsFromPrefs(context.applicationContext)
            isLoaded = true
        }
    }

    /**
     * Extracts YouTube `(videoId, playlistId)` from any standard YouTube watch, short, embed, or playlist URL.
     */
    fun parseYouTubeUrlIds(rawUrl: String): Pair<String, String> {
        val trimmed = rawUrl.trim()
        if (trimmed.isBlank()) return "" to ""
        var videoId = ""
        var playlistId = ""

        runCatching {
            val listMatch = Regex("""[?&]list=([a-zA-Z0-9_-]+)""").find(trimmed)
            if (listMatch != null) {
                playlistId = listMatch.groupValues[1]
            }
            val vMatch = Regex("""(?:v=|youtu\.be/|embed/|shorts/|live/)([a-zA-Z0-9_-]{11})""").find(trimmed)
            if (vMatch != null) {
                videoId = vMatch.groupValues[1]
            }
        }
        return videoId to playlistId
    }

    /**
     * Matches [bookTitle] and [subject] strictly against brand/educator keywords in [BuiltInCampCatalog].
     * Never falls back to a different educator's brand if the book belongs to another publisher/teacher.
     */
    fun findMatchingBrandTemplate(bookTitle: String, subject: String): CampCatalogTemplate? {
        val hay = "$bookTitle $subject".lowercase(TR_LOCALE)
        return BuiltInCampCatalog.firstOrNull { tpl ->
            tpl.matchKeywords.any { kw -> hay.contains(kw.lowercase(TR_LOCALE)) }
        }
    }

    private fun safeLoadGoalsOffMainThread(context: Context): List<TopicGoalEntity> {
        val isMainThread = runCatching {
            android.os.Looper.getMainLooper()?.thread == Thread.currentThread()
        }.getOrDefault(false)
        if (isMainThread) return emptyList()
        return runCatching {
            SharkMentorDatabase.getDatabase(context.applicationContext).goalsDao().getAllTopicsRaw()
        }.getOrDefault(emptyList())
    }

    /**
     * Resolves either the user-saved [ResourceCampBinding] or a brand-accurate camp match for [resource]
     * with canonical curriculum ordering and topic mastery weighting.
     */
    fun resolveCampForResource(
        context: Context,
        resource: ResourceEntity,
        studentTopicGoals: List<TopicGoalEntity> = emptyList()
    ): ResourceCampBinding {
        ensureLoaded(context)
        val effectiveGoals = studentTopicGoals.ifEmpty { safeLoadGoalsOffMainThread(context) }

        _bindingsByResourceId.value[resource.id]?.let { saved ->
            val syncedCurrentDay = if (resource.totalUnits > 0 && resource.completedUnits > 0) {
                ((resource.completedUnits.toFloat() / resource.totalUnits.toFloat()) * saved.totalDays)
                    .toInt()
                    .plus(1)
                    .coerceIn(1, saved.totalDays)
            } else {
                saved.currentDay.coerceIn(1, saved.totalDays.coerceAtLeast(1))
            }
            return saved.copy(currentDay = syncedCurrentDay)
        }

        // Strict brand-only match (e.g. "Ferrum Kimya" -> Ferrum; unknown publisher -> keeps book's own title!)
        val matchedTemplate = findMatchingBrandTemplate(resource.title, resource.subject)

        val totalDays = matchedTemplate?.totalDays ?: (resource.totalUnits.takeIf { it in 5..90 } ?: 30)
        val cleanBookTitle = resource.title.trim().ifBlank { resource.subject }
        val campTitle = matchedTemplate?.title ?: "$cleanBookTitle Kampı"
        val instructor = matchedTemplate?.instructor ?: cleanBookTitle
        val baseTopics = canonicalCurriculumForSubject(resource.subject, resource.title)
            .ifEmpty { matchedTemplate?.dayTopics.orEmpty() }
        val orderedWeightedTopics = if (baseTopics.isNotEmpty()) {
            expandTopicsToDaysWithMastery(
                subject = resource.subject,
                topics = baseTopics,
                totalDays = totalDays,
                studentTopicGoals = effectiveGoals
            )
        } else {
            defaultTopicsForSubject(resource.subject, totalDays, effectiveGoals)
        }

        val currentDay = if (resource.totalUnits > 0 && resource.completedUnits > 0) {
            ((resource.completedUnits.toFloat() / resource.totalUnits.toFloat()) * totalDays)
                .toInt()
                .plus(1)
                .coerceIn(1, totalDays)
        } else 1

        val episodes = (1..totalDays).map { day ->
            val topic = orderedWeightedTopics.getOrElse(day - 1) { "${resource.subject} • $day. Gün" }
            val cleanTopicForSearch = topic.substringBefore(" (").trim()
            CampDayEpisode(
                dayNumber = day,
                topicTitle = topic,
                videoId = matchedTemplate?.defaultVideoId.orEmpty(),
                playlistId = matchedTemplate?.defaultPlaylistId.orEmpty(),
                searchQuery = "$instructor ${resource.subject} $cleanTopicForSearch konu anlatımı",
                isCompleted = day < currentDay
            )
        }

        return ResourceCampBinding(
            resourceId = resource.id,
            resourceTitle = resource.title,
            subject = resource.subject,
            campTitle = campTitle,
            instructor = instructor,
            totalDays = totalDays,
            currentDay = currentDay,
            episodes = episodes,
            isAutoMatched = true
        )
    }

    /**
     * Resolves a [CampDayEpisode] for any weekly plan box ([PlanEntity]) or Duolingo path node,
     * linking it to the student's matched library book camp (without cross-brand contamination)
     * and enforcing prerequisite foundation gatekeeping.
     */
    fun resolveCampEpisodeForTask(
        context: Context,
        subject: String,
        topic: String,
        taskTitle: String = "",
        dayHint: Int = 1,
        libraryResources: List<ResourceEntity> = emptyList(),
        preferredResource: ResourceEntity? = null,
        studentTopicGoals: List<TopicGoalEntity> = emptyList()
    ): Pair<String, CampDayEpisode> {
        ensureLoaded(context)
        val cleanSubj = subject.trim().ifBlank { "Matematik" }
        val rawTopic = topic.trim().ifBlank { taskTitle.trim() }.ifBlank { cleanSubj }

        val effectiveGoals = studentTopicGoals.ifEmpty { safeLoadGoalsOffMainThread(context) }

        // Enforce prerequisite foundation gate on the task topic
        val gateResult = enforcePrerequisiteFoundationGate(cleanSubj, rawTopic, effectiveGoals)
        val cleanTopic = gateResult.effectiveTopic

        val explicitDay = Regex("""(\d{1,2})\.\s*[Gg]ün""").find("$rawTopic $taskTitle")
            ?.groupValues?.getOrNull(1)?.toIntOrNull()

        // 1. Prefer explicitly selected resource or subject-matching library resource
        val targetResource = preferredResource ?: libraryResources.firstOrNull {
            it.subject.contains(cleanSubj, ignoreCase = true) || cleanSubj.contains(it.subject, ignoreCase = true)
        }
        val matchingBinding = targetResource?.let { resolveCampForResource(context, it, effectiveGoals) }
            ?: _bindingsByResourceId.value.values.firstOrNull {
                it.subject.contains(cleanSubj, ignoreCase = true) || cleanSubj.contains(it.subject, ignoreCase = true)
            }

        if (matchingBinding != null) {
            val coreTopic = cleanTopic.substringBefore(" (").trim()
            val matchedEpisode = if (explicitDay != null && !gateResult.isGatedByPrerequisite) {
                matchingBinding.episodes.firstOrNull { it.dayNumber == explicitDay }
            } else {
                matchingBinding.episodes.firstOrNull {
                    it.topicTitle.contains(coreTopic, ignoreCase = true) ||
                        coreTopic.contains(it.topicTitle.substringBefore(" ("), ignoreCase = true)
                }
            } ?: matchingBinding.currentEpisode()

            val refinedQuery = "${matchingBinding.instructor} $cleanSubj $coreTopic konu anlatımı"
            return matchingBinding.campTitle to matchedEpisode.copy(
                topicTitle = cleanTopic.ifBlank { matchedEpisode.topicTitle },
                searchQuery = refinedQuery
            )
        }

        // 2. Check if task title explicitly mentions a known brand/teacher
        val template = findMatchingBrandTemplate("$cleanTopic $taskTitle", cleanSubj)
        val dayNum = explicitDay ?: dayHint.coerceAtLeast(1)
        val coreTopic = cleanTopic.substringBefore(" (").trim()
        val campName = template?.let { "${it.title} (${it.instructor})" } ?: "$cleanSubj Konu Kampı"
        val query = if (template != null) {
            "${template.instructor} $cleanSubj $coreTopic konu anlatımı"
        } else {
            "$cleanSubj $coreTopic konu anlatımı soru çözümü"
        }

        return campName to CampDayEpisode(
            dayNumber = dayNum,
            topicTitle = cleanTopic,
            videoId = template?.defaultVideoId.orEmpty(),
            playlistId = template?.defaultPlaylistId.orEmpty(),
            searchQuery = query
        )
    }

    /**
     * Saves or updates a custom or catalog camp binding for [resource], enforcing canonical
     * curriculum ordering and topic mastery weighting.
     */
    fun saveResourceCampBinding(
        context: Context,
        resource: ResourceEntity,
        campTitle: String,
        instructor: String,
        totalDays: Int,
        currentDay: Int,
        youtubeUrl: String,
        customDayTopics: List<String> = emptyList()
    ): ResourceCampBinding {
        ensureLoaded(context)
        val safeDays = totalDays.coerceIn(1, 120)
        val safeCurrent = currentDay.coerceIn(1, safeDays)
        val (videoId, playlistId) = parseYouTubeUrlIds(youtubeUrl)
        val effectiveGoals = runCatching {
            SharkMentorDatabase.getDatabase(context.applicationContext).goalsDao().getAllTopicsRaw()
        }.getOrDefault(emptyList())

        val baseTopics = if (customDayTopics.isNotEmpty()) {
            expandTopicsToDaysWithMastery(
                subject = resource.subject,
                topics = customDayTopics,
                totalDays = safeDays,
                studentTopicGoals = effectiveGoals
            )
        } else {
            val tpl = BuiltInCampCatalog.firstOrNull {
                it.title.equals(campTitle.trim(), ignoreCase = true)
            }
            val canonical = canonicalCurriculumForSubject(resource.subject, resource.title)
            when {
                canonical.isNotEmpty() -> expandTopicsToDaysWithMastery(resource.subject, canonical, safeDays, effectiveGoals)
                tpl != null && tpl.dayTopics.isNotEmpty() -> expandTopicsToDaysWithMastery(resource.subject, tpl.dayTopics, safeDays, effectiveGoals)
                else -> defaultTopicsForSubject(resource.subject, safeDays, effectiveGoals)
            }
        }

        val cleanInstructor = instructor.trim().ifBlank { resource.title.trim().ifBlank { resource.subject } }
        val episodes = (1..safeDays).map { d ->
            val topic = baseTopics.getOrElse(d - 1) { "${resource.subject} • $d. Gün" }
            val coreTopic = topic.substringBefore(" (").trim()
            CampDayEpisode(
                dayNumber = d,
                topicTitle = topic,
                videoId = if (safeDays == 1 || playlistId.isBlank()) videoId else "",
                playlistId = playlistId,
                customUrl = youtubeUrl.trim(),
                searchQuery = "$cleanInstructor ${resource.subject} $coreTopic konu anlatımı",
                isCompleted = d < safeCurrent
            )
        }

        val binding = ResourceCampBinding(
            resourceId = resource.id,
            resourceTitle = resource.title,
            subject = resource.subject,
            campTitle = campTitle.trim().ifBlank { "${resource.title} Kampı" },
            instructor = cleanInstructor,
            totalDays = safeDays,
            currentDay = safeCurrent,
            youtubeUrl = youtubeUrl.trim(),
            videoId = videoId,
            playlistId = playlistId,
            episodes = episodes,
            isAutoMatched = false
        )

        val updated = _bindingsByResourceId.value.toMutableMap()
        updated[resource.id] = binding
        _bindingsByResourceId.value = updated
        persistBindingsToPrefs(context.applicationContext, updated)
        return binding
    }

    fun advanceCampDay(context: Context, resource: ResourceEntity): ResourceCampBinding {
        val current = resolveCampForResource(context, resource)
        val nextDay = (current.currentDay + 1).coerceIn(1, current.totalDays.coerceAtLeast(1))
        return saveResourceCampBinding(
            context = context,
            resource = resource,
            campTitle = current.campTitle,
            instructor = current.instructor,
            totalDays = current.totalDays,
            currentDay = nextDay,
            youtubeUrl = current.youtubeUrl,
            customDayTopics = current.episodes.map { it.topicTitle }
        )
    }

    /**
     * Uses Gemini AI (with canonical curriculum ordering and topic mastery post-processing)
     * to slice a book/camp into [totalDays] structured daily topics.
     */
    suspend fun generateDailyCampTopicsWithAi(
        context: Context,
        bookTitle: String,
        subject: String,
        instructor: String,
        totalDays: Int
    ): List<String> = withContext(Dispatchers.IO) {
        val safeDays = totalDays.coerceIn(3, 90)
        val effectiveGoals = runCatching {
            SharkMentorDatabase.getDatabase(context.applicationContext).goalsDao().getAllTopicsRaw()
        }.getOrDefault(emptyList())
        val compass = CoachingEngine.buildAiSystemCompassPrompt(context)
        val canonicalList = canonicalCurriculumForSubject(subject, bookTitle)
        val canonicalHint = if (canonicalList.isNotEmpty()) {
            "Dersin pedagojik konu sırası: ${canonicalList.joinToString(" -> ")}"
        } else ""

        val prompt = """
            Kaynak Kitap / Kamp: "$bookTitle" ($subject - Eğitmen/Yayın: $instructor)
            $canonicalHint
            Bu kampı tam $safeDays güne pedagojik müfredat sırasını kesinlikle bozmadan böl (Örn: Fonksiyonlar bitmeden asla Limit/Türev/İntegral yazma).
            Öğrencinin hakimiyet seviyesi düşük konularına daha fazla gün ayır.
            Her satırda sadece o günün konu başlığını yaz (1. Gün numaralandırması koymadan, sadece konu adı).
            Toplam $safeDays satır döndür. Açıklama veya emoji yazma.
        """.trimIndent()

        val aiLines = runCatching {
            UserAiPreferences.initialize(context)
            val res = com.example.sharkmentor.core.ai.GeminiAiFactory.generateContent(
                contents = listOf(com.example.sharkmentor.core.ai.GeminiContent.userText(prompt)),
                systemPrompt = compass,
                taskType = com.example.sharkmentor.core.ai.AiTaskType.LIGHT_INTERACTION
            )
            res.text
                .lines()
                .map { line ->
                    line.trim()
                        .replace(Regex("""^\d+[\.\)\-\s]+([Gg]ün[\s:\-]*)?"""), "")
                        .trim()
                }
                .filter { it.length >= 3 }
        }.getOrDefault(emptyList())

        if (aiLines.size >= 3) {
            return@withContext expandTopicsToDaysWithMastery(
                subject = subject,
                topics = aiLines,
                totalDays = safeDays,
                studentTopicGoals = effectiveGoals
            )
        }

        defaultTopicsForSubject(subject, safeDays, effectiveGoals)
    }

    /**
     * Distributes 7 consecutive camp days starting from [binding.currentDay] for a SINGLE selected
     * book into the student's active weekly plan folder in Room (`planDao()`), replacing any older
     * tasks of that same camp so multiple books are not chaotically tangled together.
     */
    suspend fun distributeCampToWeeklyPlan(
        context: Context,
        binding: ResourceCampBinding,
        targetQuestionsPerDay: Int = 35
    ): Int = withContext(Dispatchers.IO) {
        val db = SharkMentorDatabase.getDatabase(context.applicationContext)
        val planDao = db.planDao()
        val effectiveGoals = runCatching { db.goalsDao().getAllTopicsRaw() }.getOrDefault(emptyList())

        val existingFolders = planDao.getAllFoldersSync()
        val activeFolder = existingFolders.firstOrNull() ?: PlanFolderEntity(
            id = UUID.randomUUID().toString(),
            title = "Bu Haftanın Kamp Planı"
        ).also { planDao.insertFolder(it) }

        val startDay = binding.currentDay.coerceIn(1, binding.totalDays.coerceAtLeast(1))
        val newTasks = mutableListOf<PlanEntity>()

        for (dayOfWeek in 1..7) {
            val campDayNum = startDay + (dayOfWeek - 1)
            if (campDayNum > binding.totalDays) break
            val ep = binding.episodes.firstOrNull { it.dayNumber == campDayNum }
                ?: CampDayEpisode(
                    dayNumber = campDayNum,
                    topicTitle = "${binding.subject} • $campDayNum. Gün"
                )
            val gatedTopic = enforcePrerequisiteFoundationGate(
                subject = binding.subject,
                requestedTopic = ep.topicTitle,
                studentTopicGoals = effectiveGoals
            ).effectiveTopic

            val taskTopic = "${binding.campTitle} • ${ep.dayNumber}. Gün: $gatedTopic"
            val entity = PlanEntity(
                id = UUID.randomUUID().toString(),
                folderId = activeFolder.id,
                title = taskTopic,
                subject = binding.subject,
                topic = taskTopic,
                dayOfWeek = dayOfWeek,
                scheduledTime = "10:00 - 11:30",
                targetQuestions = targetQuestionsPerDay.coerceAtLeast(30),
                isCompleted = false,
                orderIndex = dayOfWeek
            )
            newTasks.add(entity)
        }
        if (newTasks.isNotEmpty()) {
            planDao.insertTasks(newTasks)
        }
        newTasks.size
    }

    private fun defaultTopicsForSubject(
        subject: String,
        totalDays: Int,
        studentTopicGoals: List<TopicGoalEntity> = emptyList()
    ): List<String> {
        val canonical = canonicalCurriculumForSubject(subject)
        return if (canonical.isNotEmpty()) {
            expandTopicsToDaysWithMastery(subject, canonical, totalDays, studentTopicGoals)
        } else {
            (1..totalDays.coerceAtLeast(1)).map { "$subject • $it. Gün Konu & Soru" }
        }
    }

    private fun loadBindingsFromPrefs(context: Context): Map<Long, ResourceCampBinding> {
        val raw = prefs(context).getString(KEY_BINDINGS_JSON, null) ?: return emptyMap()
        return runCatching {
            val arr = JSONArray(raw)
            val map = LinkedHashMap<Long, ResourceCampBinding>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val resId = obj.optLong("resourceId", -1L)
                if (resId <= 0L) continue
                val epsArr = obj.optJSONArray("episodes")
                val episodes = mutableListOf<CampDayEpisode>()
                if (epsArr != null) {
                    for (j in 0 until epsArr.length()) {
                        val eObj = epsArr.optJSONObject(j) ?: continue
                        episodes.add(
                            CampDayEpisode(
                                dayNumber = eObj.optInt("dayNumber", j + 1),
                                topicTitle = eObj.optString("topicTitle", ""),
                                videoId = eObj.optString("videoId", ""),
                                playlistId = eObj.optString("playlistId", ""),
                                customUrl = eObj.optString("customUrl", ""),
                                searchQuery = eObj.optString("searchQuery", ""),
                                isCompleted = eObj.optBoolean("isCompleted", false)
                            )
                        )
                    }
                }
                map[resId] = ResourceCampBinding(
                    resourceId = resId,
                    resourceTitle = obj.optString("resourceTitle", ""),
                    subject = obj.optString("subject", ""),
                    campTitle = obj.optString("campTitle", ""),
                    instructor = obj.optString("instructor", ""),
                    totalDays = obj.optInt("totalDays", 30),
                    currentDay = obj.optInt("currentDay", 1),
                    youtubeUrl = obj.optString("youtubeUrl", ""),
                    videoId = obj.optString("videoId", ""),
                    playlistId = obj.optString("playlistId", ""),
                    episodes = episodes,
                    isAutoMatched = obj.optBoolean("isAutoMatched", false)
                )
            }
            map
        }.getOrDefault(emptyMap())
    }

    private fun persistBindingsToPrefs(context: Context, bindings: Map<Long, ResourceCampBinding>) {
        val arr = JSONArray()
        bindings.values.forEach { b ->
            val obj = JSONObject()
            obj.put("resourceId", b.resourceId)
            obj.put("resourceTitle", b.resourceTitle)
            obj.put("subject", b.subject)
            obj.put("campTitle", b.campTitle)
            obj.put("instructor", b.instructor)
            obj.put("totalDays", b.totalDays)
            obj.put("currentDay", b.currentDay)
            obj.put("youtubeUrl", b.youtubeUrl)
            obj.put("videoId", b.videoId)
            obj.put("playlistId", b.playlistId)
            obj.put("isAutoMatched", b.isAutoMatched)
            val epsArr = JSONArray()
            b.episodes.forEach { ep ->
                val eObj = JSONObject()
                eObj.put("dayNumber", ep.dayNumber)
                eObj.put("topicTitle", ep.topicTitle)
                eObj.put("videoId", ep.videoId)
                eObj.put("playlistId", ep.playlistId)
                eObj.put("customUrl", ep.customUrl)
                eObj.put("searchQuery", ep.searchQuery)
                eObj.put("isCompleted", ep.isCompleted)
                epsArr.put(eObj)
            }
            obj.put("episodes", epsArr)
            arr.put(obj)
        }
        prefs(context).edit().putString(KEY_BINDINGS_JSON, arr.toString()).apply()
    }

    // ── Mark 60: Direct YouTube Video Auto-Resolution, Transcript Window & Ad-Blocker Filter ──

    private const val VIDEO_CACHE_PREFS = "shark_mark60_video_cache_prefs"
    private const val YT_DESKTOP_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"

    private data class TranscriptLine(val startSec: Int, val text: String)

    // Small bounded LRU cache (max 10 videos) to keep RAM usage minimal on low-end devices
    private val transcriptCache = object : LinkedHashMap<String, List<TranscriptLine>>(12, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<TranscriptLine>>?): Boolean = size > 10
    }

    private val AD_BLOCK_HOST_PATTERNS = listOf(
        "doubleclick.net",
        "googlesyndication.com",
        "googleadservices.com",
        "adservice.google",
        "googleads.g.doubleclick.net",
        "pagead2.googlesyndication.com",
        "tpc.googlesyndication.com",
        "fundingchoicesmessages.google.com",
        "s0.2mdn.net",
        "ad.doubleclick.net",
        "static.doubleclick.net",
        "ads.youtube.com",
        "google-analytics.com",
        "googletagmanager.com"
    )

    private val AD_BLOCK_PATH_PATTERNS = listOf(
        "/pagead/",
        "/api/stats/ads",
        "/get_midroll_info",
        "/ptracking",
        "/youtubei/v1/log_event",
        "adformat=",
        "&ad_type=",
        "?ad_type=",
        "&oad=",
        "/pcs/activeview",
        "/generate_204"
    )

    /**
     * Opera-style YouTube ad & tracker network classifier.
     * Blocks ad-serving hosts and YouTube ad/telemetry endpoints while NEVER blocking legitimate
     * video stream playback (`googlevideo.com/videoplayback`), thumbnails (`ytimg.com`), or embeds.
     */
    fun isAdOrTrackerUrl(rawUrl: String): Boolean {
        val lower = rawUrl.trim().lowercase(Locale.ROOT)
        if (lower.isBlank()) return false
        val host = runCatching { java.net.URI(lower).host.orEmpty() }.getOrDefault("")
        if (AD_BLOCK_HOST_PATTERNS.any { host.contains(it) }) return true
        // Never block main video playback media streams on googlevideo.com unless marked as an ad request
        if (host.contains("googlevideo.com")) {
            return lower.contains("&ctier=l") || lower.contains("oad=") || lower.contains("adformat=")
        }
        return AD_BLOCK_PATH_PATTERNS.any { lower.contains(it) }
    }

    /**
     * Extracts a mentioned playback timestamp (in seconds) from a student's question.
     * Supports formats like `"4.5 dakika"`, `"4,5 dk"`, `"4:30"`, `"1:04:15"`, `"4 dk 20 sn"`, `"12. dakikadaki"`.
     */
    fun extractMentionedTimestampSeconds(question: String): Int? {
        val text = question.trim().lowercase(TR_LOCALE)
        if (text.isBlank()) return null

        // 1. hh:mm:ss or mm:ss (e.g. "1:04:20" or "4:30")
        val hmsMatch = Regex("""\b(\d{1,2}):(\d{2})(?::(\d{2}))?\b""").find(text)
        if (hmsMatch != null) {
            val a = hmsMatch.groupValues[1].toIntOrNull() ?: 0
            val b = hmsMatch.groupValues[2].toIntOrNull() ?: 0
            val c = hmsMatch.groupValues[3].toIntOrNull()
            return if (c != null) (a * 3600) + (b * 60) + c else (a * 60) + b
        }

        // 2. "X dk Y sn" / "X dakika Y saniye"
        val minSecMatch = Regex("""\b(\d{1,3})\s*(?:dk|dakika)\.?\s*(\d{1,2})\s*(?:sn|saniye)\b""").find(text)
        if (minSecMatch != null) {
            val mins = minSecMatch.groupValues[1].toIntOrNull() ?: 0
            val secs = minSecMatch.groupValues[2].toIntOrNull() ?: 0
            return (mins * 60) + secs
        }

        // 3. Decimal or integer minutes: "4.5 dakikadaki", "4,5 dk", "12. dakikada"
        val decMinMatch = Regex("""\b(\d{1,3}(?:[.,]\d{1,2})?)\s*\.?\s*(?:dakika\w*|dk\w*)\b""").find(text)
        if (decMinMatch != null) {
            val rawNum = decMinMatch.groupValues[1].replace(',', '.')
            val minsFloat = rawNum.toDoubleOrNull()
            if (minsFloat != null && minsFloat >= 0.0) {
                return kotlin.math.round(minsFloat * 60.0).toInt()
            }
        }

        // 4. Standalone seconds: "95 saniye" / "45. sn"
        val secOnlyMatch = Regex("""\b(\d{1,4})\s*\.?\s*(?:saniye\w*|sn)\b""").find(text)
        if (secOnlyMatch != null) {
            return secOnlyMatch.groupValues[1].toIntOrNull()
        }

        return null
    }

    fun formatSecondsAsTimestamp(totalSeconds: Int): String {
        val safe = totalSeconds.coerceAtLeast(0)
        val h = safe / 3600
        val m = (safe % 3600) / 60
        val s = safe % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
    }

    /**
     * Resolves direct playable YouTube videos (real 11-char `videoId`s) for a camp episode on `Dispatchers.IO`,
     * avoiding YouTube search pages inside the studio player. Results are cached locally per query/playlist.
     */
    suspend fun resolveDirectVideosForEpisode(
        context: Context,
        campTitle: String,
        subject: String,
        episode: CampDayEpisode
    ): List<ResolvedYouTubeVideo> = withContext(Dispatchers.IO) {
        if (episode.videoId.length == 11) {
            return@withContext listOf(
                ResolvedYouTubeVideo(
                    videoId = episode.videoId,
                    title = episode.topicTitle.ifBlank { campTitle },
                    channelName = campTitle
                )
            )
        }

        val cleanQuery = episode.searchQuery.trim()
            .ifBlank { "$campTitle $subject ${episode.topicTitle.substringBefore(" (").trim()} konu anlatımı" }
        val cacheKey = if (episode.playlistId.isNotBlank()) {
            "pl_${episode.playlistId}_d${episode.dayNumber}"
        } else {
            "q_${cleanQuery.lowercase(TR_LOCALE).take(120)}"
        }

        // 1. Instant local cache check
        val cachePrefs = context.applicationContext.getSharedPreferences(VIDEO_CACHE_PREFS, Context.MODE_PRIVATE)
        val cachedJson = cachePrefs.getString(cacheKey, null)
        if (!cachedJson.isNullOrBlank()) {
            val cached = parseCachedResolvedVideos(cachedJson)
            if (cached.isNotEmpty()) return@withContext cached
        }

        // 2. Live resolution from YouTube Playlist or Search ytInitialData
        val resolved = mutableListOf<ResolvedYouTubeVideo>()
        if (episode.playlistId.isNotBlank()) {
            val plUrl = "https://www.youtube.com/playlist?list=${episode.playlistId}&hl=tr"
            val html = fetchHtmlQuietly(plUrl)
            if (!html.isNullOrBlank()) {
                val plVideos = extractPlaylistVideosFromHtml(html)
                if (plVideos.isNotEmpty()) {
                    val idx = (episode.dayNumber - 1).coerceIn(0, plVideos.lastIndex)
                    resolved.add(plVideos[idx])
                    plVideos.forEach { v -> if (resolved.size < 5 && resolved.none { it.videoId == v.videoId }) resolved.add(v) }
                }
            }
        }

        if (resolved.isEmpty()) {
            val encoded = java.net.URLEncoder.encode(cleanQuery, "UTF-8")
            val searchUrl = "https://www.youtube.com/results?search_query=$encoded&hl=tr&gl=TR"
            val html = fetchHtmlQuietly(searchUrl)
            if (!html.isNullOrBlank()) {
                val rawCandidates = extractSearchVideosFromHtml(html)
                val queryTokens = cleanQuery.lowercase(TR_LOCALE)
                    .split(Regex("""[\s•\-()/:]+"""))
                    .filter { it.length >= 3 && it !in setOf("konu", "anlatımı", "kampı", "ders", "video", "gün") }

                val scored = rawCandidates.sortedByDescending { cand ->
                    val hay = "${cand.title} ${cand.channelName}".lowercase(TR_LOCALE)
                    var score = queryTokens.count { hay.contains(it) } * 10
                    if (hay.contains("shorts") || cand.durationLabel.startsWith("0:")) score -= 25
                    if (hay.contains(episode.topicTitle.substringBefore(" (").trim().lowercase(TR_LOCALE))) score += 20
                    score
                }
                resolved.addAll(scored.take(5))
            }
        }

        if (resolved.isNotEmpty()) {
            runCatching {
                val arr = JSONArray()
                resolved.forEach { v ->
                    arr.put(
                        JSONObject().apply {
                            put("id", v.videoId)
                            put("t", v.title)
                            put("c", v.channelName)
                            put("d", v.durationLabel)
                        }
                    )
                }
                cachePrefs.edit().putString(cacheKey, arr.toString()).apply()
            }
        }

        resolved
    }

    /**
     * Fetches the spoken caption/transcript lines around [centerSeconds] (±[windowRadiusSeconds])
     * for [videoId] so Studio AI can directly answer questions like "4.5 dakikadaki formül neden öyle?".
     */
    suspend fun fetchYouTubeTranscriptWindow(
        videoId: String,
        centerSeconds: Int,
        windowRadiusSeconds: Int = 75
    ): String = withContext(Dispatchers.IO) {
        if (videoId.length != 11) return@withContext ""
        val lines = synchronized(transcriptCache) { transcriptCache[videoId] } ?: run {
            val fetched = fetchCaptionLinesForVideo(videoId)
            if (fetched.isNotEmpty()) {
                synchronized(transcriptCache) { transcriptCache[videoId] = fetched }
            }
            fetched
        }
        if (lines.isEmpty()) return@withContext ""

        val minSec = (centerSeconds - windowRadiusSeconds).coerceAtLeast(0)
        val maxSec = centerSeconds + windowRadiusSeconds
        val window = lines.filter { it.startSec in minSec..maxSec }
            .ifEmpty { lines.take(18) }

        window.joinToString("\n") { line ->
            "[${formatSecondsAsTimestamp(line.startSec)}] ${line.text}"
        }.take(2400)
    }

    private fun fetchCaptionLinesForVideo(videoId: String): List<TranscriptLine> {
        val watchHtml = fetchHtmlQuietly("https://www.youtube.com/watch?v=$videoId&hl=tr") ?: return emptyList()
        val tracksMatch = Regex(""""captionTracks":(\[[\s\S]*?\])""").find(watchHtml) ?: return emptyList()
        val tracksArr = runCatching { JSONArray(tracksMatch.groupValues[1]) }.getOrNull() ?: return emptyList()
        if (tracksArr.length() == 0) return emptyList()

        var chosenBaseUrl = ""
        for (i in 0 until tracksArr.length()) {
            val obj = tracksArr.optJSONObject(i) ?: continue
            val lang = obj.optString("languageCode", "")
            val url = obj.optString("baseUrl", "").replace("\\u0026", "&")
            if (url.isBlank()) continue
            if (lang.startsWith("tr", ignoreCase = true)) {
                chosenBaseUrl = url
                break
            }
            if (chosenBaseUrl.isBlank()) chosenBaseUrl = url
        }
        if (chosenBaseUrl.isBlank()) return emptyList()

        val xml = fetchHtmlQuietly(chosenBaseUrl) ?: return emptyList()
        val textTagRegex = Regex("""<text\s+start="([\d.]+)"[^>]*>([\s\S]*?)</text>""")
        val out = mutableListOf<TranscriptLine>()
        for (m in textTagRegex.findAll(xml)) {
            val sec = m.groupValues[1].toDoubleOrNull()?.toInt() ?: continue
            val cleanText = decodeHtmlEntities(m.groupValues[2])
                .replace(Regex("""<[^>]+>"""), " ")
                .replace(Regex("""\s+"""), " ")
                .trim()
            if (cleanText.isNotBlank()) {
                out.add(TranscriptLine(sec, cleanText))
            }
        }
        return out
    }

    private fun decodeHtmlEntities(raw: String): String =
        raw.replace("&amp;", "&")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&quot;", "\"")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&nbsp;", " ")

    private fun fetchHtmlQuietly(urlString: String): String? {
        return runCatching {
            val conn = (java.net.URL(urlString).openConnection() as java.net.HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 9_000
                readTimeout = 10_000
                setRequestProperty("User-Agent", YT_DESKTOP_UA)
                setRequestProperty("Accept-Language", "tr-TR,tr;q=0.9,en-US;q=0.8")
                setRequestProperty("Cookie", "CONSENT=YES+cb.20210328-17-p0.en+FX+917")
            }
            try {
                if (conn.responseCode in 200..299) {
                    conn.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                        val buf = CharArray(8192)
                        val sb = StringBuilder()
                        var total = 0
                        while (total < 1_400_000) {
                            val read = reader.read(buf)
                            if (read <= 0) break
                            sb.append(buf, 0, read)
                            total += read
                        }
                        sb.toString()
                    }
                } else null
            } finally {
                conn.disconnect()
            }
        }.getOrNull()
    }

    private fun extractSearchVideosFromHtml(html: String): List<ResolvedYouTubeVideo> {
        val out = mutableListOf<ResolvedYouTubeVideo>()
        val seen = HashSet<String>()
        val rendererRegex = Regex(""""videoRenderer":\{"videoId":"([a-zA-Z0-9_-]{11})"[\s\S]{0,2200}?(?="videoRenderer":|\}\]\}\})""")
        val titleRegex = Regex(""""title":\{"runs":\[\{"text":"([^"\\]{2,140})"""")
        val ownerRegex = Regex(""""(?:ownerText|longBylineText)":\{"runs":\[\{"text":"([^"\\]{2,80})"""")
        val lenRegex = Regex(""""lengthText":\{[^\}]{0,180}"simpleText":"([0-9:]{3,10})"""")

        for (match in rendererRegex.findAll(html)) {
            val vid = match.groupValues[1]
            if (!seen.add(vid)) continue
            val chunk = match.value
            val title = titleRegex.find(chunk)?.groupValues?.getOrNull(1)?.let { unescapeJsonString(it) } ?: "Ders Videosu"
            val channel = ownerRegex.find(chunk)?.groupValues?.getOrNull(1)?.let { unescapeJsonString(it) }.orEmpty()
            val duration = lenRegex.find(chunk)?.groupValues?.getOrNull(1).orEmpty()
            out.add(ResolvedYouTubeVideo(videoId = vid, title = title, channelName = channel, durationLabel = duration))
            if (out.size >= 12) break
        }

        // Ultra-safe regex fallback if YouTube slightly changed JSON field order
        if (out.isEmpty()) {
            val fallbackRegex = Regex(""""videoId":"([a-zA-Z0-9_-]{11})"""")
            for (m in fallbackRegex.findAll(html)) {
                val vid = m.groupValues[1]
                if (seen.add(vid)) {
                    out.add(ResolvedYouTubeVideo(videoId = vid, title = "Ders Videosu"))
                    if (out.size >= 5) break
                }
            }
        }
        return out
    }

    private fun extractPlaylistVideosFromHtml(html: String): List<ResolvedYouTubeVideo> {
        val out = mutableListOf<ResolvedYouTubeVideo>()
        val seen = HashSet<String>()
        val plRegex = Regex(""""playlistVideoRenderer":\{"videoId":"([a-zA-Z0-9_-]{11})"[\s\S]{0,1600}?(?="playlistVideoRenderer":|\}\]\}\})""")
        val titleRegex = Regex(""""title":\{"runs":\[\{"text":"([^"\\]{2,140})"""")
        val lenRegex = Regex(""""lengthText":\{[^\}]{0,180}"simpleText":"([0-9:]{3,10})"""")

        for (match in plRegex.findAll(html)) {
            val vid = match.groupValues[1]
            if (!seen.add(vid)) continue
            val chunk = match.value
            val title = titleRegex.find(chunk)?.groupValues?.getOrNull(1)?.let { unescapeJsonString(it) } ?: "Kamp Videosu"
            val duration = lenRegex.find(chunk)?.groupValues?.getOrNull(1).orEmpty()
            out.add(ResolvedYouTubeVideo(videoId = vid, title = title, durationLabel = duration))
            if (out.size >= 60) break
        }
        return out
    }

    private fun unescapeJsonString(raw: String): String =
        runCatching { JSONObject("""{"v":"$raw"}""").optString("v", raw) }.getOrDefault(raw)

    private fun parseCachedResolvedVideos(rawJson: String): List<ResolvedYouTubeVideo> {
        return runCatching {
            val arr = JSONArray(rawJson)
            List(arr.length()) { idx ->
                val obj = arr.getJSONObject(idx)
                ResolvedYouTubeVideo(
                    videoId = obj.optString("id", ""),
                    title = obj.optString("t", ""),
                    channelName = obj.optString("c", ""),
                    durationLabel = obj.optString("d", "")
                )
            }.filter { it.videoId.length == 11 }
        }.getOrDefault(emptyList())
    }
}
