package com.example.sharkmentor.core.badges

import android.content.Context
import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoGraph
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sharkmentor.core.util.GoogleServicesHelper
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Calendar
import java.util.Locale

enum class BadgeTier(
    val level: Int,
    val label: String,
    val shortLabel: String,
    val emoji: String = "",
    val primaryColorHex: Long,
    val secondaryColorHex: Long
) {
    BRONZE(1, "Bronz", "Bronz", "", 0xFFB45309, 0xFFF59E0B),
    SILVER(2, "Gümüş", "Gümüş", "", 0xFF64748B, 0xFFCBD5E1),
    GOLD(3, "Altın", "Altın", "", 0xFFD97706, 0xFFFDE047),
    MEGALODON(4, "Megalodon", "Megalodon", "", 0xFF0284C7, 0xFF38BDF8)
}

@Immutable
data class BadgeProgressInfo(
    val badge: SharkBadgeDef,
    val count: Int,
    val unlockedTier: BadgeTier?,
    val nextTier: BadgeTier?,
    val currentThreshold: Int,
    val nextTarget: Int,
    val progressFraction: Float
)

@Immutable
data class SharkBadgeDef(
    val id: String,
    val title: String,
    val emoji: String,
    val description: String,
    val conditionLabel: String,
    val thresholds: List<Int>, // [BRONZE, SILVER, GOLD, MEGALODON]
    val primaryColorHex: Long,
    val secondaryColorHex: Long,
    val icon: ImageVector,
    val category: BadgeCategory = BadgeCategory.MENTORSHIP,
    val symbolText: String = ""
) {
    fun progressFor(rawCount: Int, isLegacyUnlocked: Boolean = false): BadgeProgressInfo {
        val t = if (thresholds.size >= 4) thresholds else listOf(15, 45, 100, 220)
        val effectiveCount = if (isLegacyUnlocked && rawCount < t[0]) t[0] else rawCount.coerceAtLeast(0)

        return when {
            effectiveCount >= t[3] -> BadgeProgressInfo(
                badge = this,
                count = effectiveCount,
                unlockedTier = BadgeTier.MEGALODON,
                nextTier = null,
                currentThreshold = t[3],
                nextTarget = t[3],
                progressFraction = 1f
            )
            effectiveCount >= t[2] -> BadgeProgressInfo(
                badge = this,
                count = effectiveCount,
                unlockedTier = BadgeTier.GOLD,
                nextTier = BadgeTier.MEGALODON,
                currentThreshold = t[2],
                nextTarget = t[3],
                progressFraction = ((effectiveCount - t[2]).toFloat() / (t[3] - t[2]).coerceAtLeast(1)).coerceIn(0f, 1f)
            )
            effectiveCount >= t[1] -> BadgeProgressInfo(
                badge = this,
                count = effectiveCount,
                unlockedTier = BadgeTier.SILVER,
                nextTier = BadgeTier.GOLD,
                currentThreshold = t[1],
                nextTarget = t[2],
                progressFraction = ((effectiveCount - t[1]).toFloat() / (t[2] - t[1]).coerceAtLeast(1)).coerceIn(0f, 1f)
            )
            effectiveCount >= t[0] -> BadgeProgressInfo(
                badge = this,
                count = effectiveCount,
                unlockedTier = BadgeTier.BRONZE,
                nextTier = BadgeTier.SILVER,
                currentThreshold = t[0],
                nextTarget = t[1],
                progressFraction = ((effectiveCount - t[0]).toFloat() / (t[1] - t[0]).coerceAtLeast(1)).coerceIn(0f, 1f)
            )
            else -> BadgeProgressInfo(
                badge = this,
                count = effectiveCount,
                unlockedTier = null,
                nextTier = BadgeTier.BRONZE,
                currentThreshold = 0,
                nextTarget = t[0],
                progressFraction = (effectiveCount.toFloat() / t[0].coerceAtLeast(1)).coerceIn(0f, 1f)
            )
        }
    }
}

object SharkBadgeCatalog {

    const val MAX_SHOWCASE_BADGES = 5
    const val MAX_WEEKLY_BONUS_SECONDS = 9000L // 150 dakika

    val DEFAULT_TOPIC_THRESHOLDS: List<Int> = listOf(15, 45, 100, 220)
    val MENTOR_CORE_THRESHOLDS: List<Int> = listOf(5, 25, 75, 200)
    val MENTOR_MASTER_THRESHOLDS: List<Int> = listOf(10, 40, 120, 300)

    private const val PREFS_NAME = "shark_badges_prefs"
    private const val KEY_SHOWCASE_IDS = "showcase_badge_ids"
    private const val KEY_UNLOCKED_IDS = "unlocked_badge_ids"
    private const val KEY_MENTOR_POINTS = "mentor_points"
    private const val KEY_BADGE_PROGRESS = "badge_progress_counts"
    private const val KEY_WEEKLY_BONUS_PREFIX = "weekly_bonus_secs_"
    private const val KEY_BLOCKED_UIDS = "blocked_user_ids"
    private const val KEY_BLOCKED_USER_NAMES_JSON = "blocked_user_names_json"

    // ─── 12 Base Backward-Compatible Badge IDs ───────────────────────────────
    const val BADGE_ILK_SORU = "ilk_soru"
    const val BADGE_ILK_COZUM = "ilk_cozum"
    const val BADGE_HIZLI_KOPEKBALIGI = "hizli_kopekbaligi"
    const val BADGE_CAN_SIMIDI = "can_simidi"
    const val BADGE_SORU_CANAVARI = "soru_canavari"
    const val BADGE_SORU_AVCISI = "soru_avcisi"
    const val BADGE_MATEMATIK_CANAVARI = "matematik_canavari"
    const val BADGE_FIZIK_KASIFI = "fizik_kasifi"
    const val BADGE_KIMYA_SIMYACISI = "kimya_simyacisi"
    const val BADGE_BIYOLOJI_UZMANI = "biyoloji_uzmani"
    const val BADGE_PARAGRAF_AVCISI = "paragraf_avcisi"
    const val BADGE_SOSYAL_BILGE = "sosyal_bilge"

    // ─── Additional Mentorship / Speed / Focus Badge IDs (4) ─────────────────
    const val BADGE_HATA_BANKASI_USTASI = "hata_bankasi_ustasi"
    const val BADGE_SERI_MENTOR = "seri_mentor"
    const val BADGE_ODAK_SAMPIYONU = "odak_sampiyonu"
    const val BADGE_MEGALODON_ONDERI = "megalodon_onderi"

    // ─── Mathematics & Geometry Sub-Topic Badge IDs (15) ─────────────────────
    const val BADGE_MAT_TUREV = "mat_turev"
    const val BADGE_MAT_INTEGRAL = "mat_integral"
    const val BADGE_MAT_LIMIT = "mat_limit"
    const val BADGE_MAT_TRIGONOMETRI = "mat_trigonometri"
    const val BADGE_MAT_FONKSIYONLAR = "mat_fonksiyonlar"
    const val BADGE_MAT_POLINOM_IKINCI_DERECE = "mat_polinom_ikinci_derece"
    const val BADGE_MAT_LOGARITMA_DIZILER = "mat_logaritma_diziler"
    const val BADGE_MAT_OLASILIK_PKB = "mat_olasilik_pkb"
    const val BADGE_MAT_PROBLEMLER = "mat_problemler"
    const val BADGE_MAT_SAYILAR_CEBIR = "mat_sayilar_cebir"
    const val BADGE_GEO_UCGENLER = "geo_ucgenler"
    const val BADGE_GEO_COKGEN_DORTGEN = "geo_cokgen_dortgen"
    const val BADGE_GEO_CEMBER_DAIRE = "geo_cember_daire"
    const val BADGE_GEO_ANALITIK = "geo_analitik"
    const val BADGE_GEO_KATI_CISIMLER = "geo_kati_cisimler"

    // ─── Physics Sub-Topic Badge IDs (9) ─────────────────────────────────────
    const val BADGE_FIZ_KUVVET_HAREKET = "fiz_kuvvet_hareket"
    const val BADGE_FIZ_IS_ENERJI_MOMENTUM = "fiz_is_enerji_momentum"
    const val BADGE_FIZ_ELEKTRIK_MANYETIZMA = "fiz_elektrik_manyetizma"
    const val BADGE_FIZ_DALGALAR_OPTIK = "fiz_dalgalar_optik"
    const val BADGE_FIZ_CEMBERSEL_HARMONIK = "fiz_cembersel_harmonik"
    const val BADGE_FIZ_MODERN_FIZIK = "fiz_modern_fizik"
    const val BADGE_FIZ_MADDE_ISI_BASINC = "fiz_madde_isi_basinc"
    const val BADGE_FIZ_TORK_DENGE = "fiz_tork_denge"
    const val BADGE_FIZ_ATOM_RADYOAKTIVITE = "fiz_atom_radyoaktivite"

    // ─── Chemistry Sub-Topic Badge IDs (8) ───────────────────────────────────
    const val BADGE_KIM_ATOM_PERIYODIK = "kim_atom_periyodik"
    const val BADGE_KIM_MOL_HESAPLAMA = "kim_mol_hesaplama"
    const val BADGE_KIM_GAZLAR_COZELTILER = "kim_gazlar_cozeltiler"
    const val BADGE_KIM_ENERJI_HIZ = "kim_enerji_hiz"
    const val BADGE_KIM_DENGE_ASIT_BAZ = "kim_denge_asit_baz"
    const val BADGE_KIM_ELEKTROKIMYA = "kim_elektrokimya"
    const val BADGE_KIM_ORGANIK = "kim_organik"
    const val BADGE_KIM_ETKILESIMLER_MADDE = "kim_etkilesimler_madde"

    // ─── Biology Sub-Topic Badge IDs (7) ─────────────────────────────────────
    const val BADGE_BIYO_HUCRE_CANLILAR = "biyo_hucre_canlilar"
    const val BADGE_BIYO_INSAN_FIZYOLOJISI = "biyo_insan_fizyolojisi"
    const val BADGE_BIYO_ENDOKRIN_SINIR = "biyo_endokrin_sinir"
    const val BADGE_BIYO_KALITIM_EVRIM = "biyo_kalitim_evrim"
    const val BADGE_BIYO_GENDEN_PROTEINE = "biyo_genden_proteine"
    const val BADGE_BIYO_BITKI = "biyo_bitki"
    const val BADGE_BIYO_EKOLOJI_ENERJI = "biyo_ekoloji_enerji"

    // ─── Turkish & Literature Sub-Topic Badge IDs (6) ────────────────────────
    const val BADGE_TUR_PARAGRAF_ANLAM = "tur_paragraf_anlam"
    const val BADGE_TUR_SOZCUK_CUMLE = "tur_sozcuk_cumle"
    const val BADGE_TUR_DIL_BILGISI = "tur_dil_bilgisi"
    const val BADGE_TUR_YAZIM_NOKTALAMA = "tur_yazim_noktalama"
    const val BADGE_EDB_SIIR_DONEMLER = "edb_siir_donemler"
    const val BADGE_EDB_ROMAN_HIKAYE = "edb_roman_hikaye"

    // ─── Social Sciences & Foreign Language Sub-Topic Badge IDs (5) ──────────
    const val BADGE_TAR_OSMANLI_ISLAM = "tar_osmanli_islam"
    const val BADGE_TAR_INKILAP_CAGDAS = "tar_inkilap_cagdas"
    const val BADGE_COG_DOGAL_TURKIYE = "cog_dogal_turkiye"
    const val BADGE_FEL_FELSEFE_MANTIK = "fel_felsefe_mantik"
    const val BADGE_DIL_INGILIZCE_DIN = "dil_ingilizce_din"

    val ALL_BADGES: List<SharkBadgeDef> = listOf(
        // ═══════════════════════════════════════════════════════════════════════
        // 1. MENTORSHIP, SPEED & FOCUS BADGES (10)
        // ═══════════════════════════════════════════════════════════════════════
        SharkBadgeDef(
            id = BADGE_ILK_SORU,
            title = "İlk Soru",
            emoji = "",
            description = "Hata Bankası'ndan sohbete soru paylaşarak dayanışmayı başlatan öncü.",
            conditionLabel = "Sohbette 5 soru paylaş (5 / 25 / 75 / 200)",
            thresholds = MENTOR_CORE_THRESHOLDS,
            primaryColorHex = 0xFF0284C7,
            secondaryColorHex = 0xFF38BDF8,
            icon = Icons.Default.NoteAdd,
            category = BadgeCategory.MENTORSHIP,
            symbolText = "Q+"
        ),
        SharkBadgeDef(
            id = BADGE_ILK_COZUM,
            title = "İlk Çözüm",
            emoji = "",
            description = "Arkadaşının sorusuna onaylı çözüm üreterek Mentor katkısını veren yıldız.",
            conditionLabel = "5 onaylı çözüm üret (5 / 25 / 75 / 200)",
            thresholds = MENTOR_CORE_THRESHOLDS,
            primaryColorHex = 0xFF059669,
            secondaryColorHex = 0xFF34D399,
            icon = Icons.Default.CheckCircle,
            category = BadgeCategory.MENTORSHIP,
            symbolText = "✓"
        ),
        SharkBadgeDef(
            id = BADGE_HIZLI_KOPEKBALIGI,
            title = "Hızlı Köpekbalığı",
            emoji = "",
            description = "Soru paylaşıldıktan sonraki ilk 0–15 dakika içinde onaylı çözüm gönderen hız uzmanı.",
            conditionLabel = "0–15 dk altın pencerede 5 onaylı çözüm (5 / 25 / 75 / 200)",
            thresholds = MENTOR_CORE_THRESHOLDS,
            primaryColorHex = 0xFFD97706,
            secondaryColorHex = 0xFFFBBF24,
            icon = Icons.Default.Bolt,
            category = BadgeCategory.MENTORSHIP,
            symbolText = ""
        ),
        SharkBadgeDef(
            id = BADGE_CAN_SIMIDI,
            title = "Can Simidi",
            emoji = "",
            description = "60–105 dk aralığında süre bitmeden imdada yetişip soruyu kurtaran güvenilir mentor.",
            conditionLabel = "60–105 dk arası 5 onaylı çözüm (5 / 25 / 75 / 200)",
            thresholds = MENTOR_CORE_THRESHOLDS,
            primaryColorHex = 0xFF0D9488,
            secondaryColorHex = 0xFF2DD4BF,
            icon = Icons.Default.Speed,
            category = BadgeCategory.MENTORSHIP,
            symbolText = ""
        ),
        SharkBadgeDef(
            id = BADGE_SORU_CANAVARI,
            title = "Soru Canavarı",
            emoji = "",
            description = "Çalışma gruplarında yüksek tempoda soru paylaşımı ve tartışma başlatan dinamo.",
            conditionLabel = "Toplam 10 soru paylaşımı yap (10 / 40 / 120 / 300)",
            thresholds = MENTOR_MASTER_THRESHOLDS,
            primaryColorHex = 0xFFDC2626,
            secondaryColorHex = 0xFFF87171,
            icon = Icons.Rounded.WaterDrop,
            category = BadgeCategory.MENTORSHIP,
            symbolText = ""
        ),
        SharkBadgeDef(
            id = BADGE_SORU_AVCISI,
            title = "Soru Avcısı",
            emoji = "",
            description = "Zorlu soru akışında düzinelerce onaylı çözümle topluluğun bel kemiği olan usta.",
            conditionLabel = "Toplam 10 onaylı çözüm üret (10 / 40 / 120 / 300)",
            thresholds = MENTOR_MASTER_THRESHOLDS,
            primaryColorHex = 0xFFB45309,
            secondaryColorHex = 0xFFFDE047,
            icon = Icons.Default.TrackChanges,
            category = BadgeCategory.MENTORSHIP,
            symbolText = ""
        ),
        SharkBadgeDef(
            id = BADGE_HATA_BANKASI_USTASI,
            title = "Hata Bankası Ustası",
            emoji = "",
            description = "FSRS aralıklı tekrar algoritmasıyla yanlışlarını kalıcı öğrenmeye dönüştüren stratejist.",
            conditionLabel = "Hata Bankası'nda 10 soru doğru çöz (10 / 40 / 120 / 300)",
            thresholds = MENTOR_MASTER_THRESHOLDS,
            primaryColorHex = 0xFF2563EB,
            secondaryColorHex = 0xFF60A5FA,
            icon = Icons.Default.Psychology,
            category = BadgeCategory.MENTORSHIP,
            symbolText = "FSRS"
        ),
        SharkBadgeDef(
            id = BADGE_SERI_MENTOR,
            title = "Seri Mentor",
            emoji = "",
            description = "Farklı ders ve konularda kesintisiz doğru çözümlerle akranlarına rehberlik eden mentor.",
            conditionLabel = "Farklı konularda 5 onaylı çözüm üret (5 / 25 / 75 / 200)",
            thresholds = MENTOR_CORE_THRESHOLDS,
            primaryColorHex = 0xFF7C3AED,
            secondaryColorHex = 0xFFC084FC,
            icon = Icons.Default.Star,
            category = BadgeCategory.MENTORSHIP,
            symbolText = "∞"
        ),
        SharkBadgeDef(
            id = BADGE_ODAK_SAMPIYONU,
            title = "Odak Şampiyonu",
            emoji = "",
            description = "Derin odaklanma ve soru çözümü disiplinini birleştirerek ligde zirveye oynayan şampiyon.",
            conditionLabel = "Odak ve soru tekrarında 10 hedef tamamla (10 / 40 / 120 / 300)",
            thresholds = MENTOR_MASTER_THRESHOLDS,
            primaryColorHex = 0xFF0F766E,
            secondaryColorHex = 0xFF34D399,
            icon = Icons.Default.Speed,
            category = BadgeCategory.MENTORSHIP,
            symbolText = ""
        ),
        SharkBadgeDef(
            id = BADGE_MEGALODON_ONDERI,
            title = "Megalodon Önderi",
            emoji = "",
            description = "Hem soru paylaşımı hem çözüm onaylarıyla Shark Lig'in zirvesine hükmeden efsane.",
            conditionLabel = "Toplulukta 10 mentor etkileşimi tamamla (10 / 40 / 120 / 300)",
            thresholds = MENTOR_MASTER_THRESHOLDS,
            primaryColorHex = 0xFF0369A1,
            secondaryColorHex = 0xFF38BDF8,
            icon = Icons.Default.AutoGraph,
            category = BadgeCategory.MENTORSHIP,
            symbolText = ""
        ),

        // ═══════════════════════════════════════════════════════════════════════
        // 2. MATHEMATICS & GEOMETRY BADGES (1 Main + 15 Sub-Topic = 16)
        // ═══════════════════════════════════════════════════════════════════════
        SharkBadgeDef(
            id = BADGE_MATEMATIK_CANAVARI,
            title = "Matematik Canavarı",
            emoji = "",
            description = "Matematik ve Geometri sorularında adım adım kusursuz çözümler üreten analitik zihin.",
            conditionLabel = "Matematik & Geometri'den 15 soru doğru çöz (15 / 45 / 100 / 220)",
            thresholds = DEFAULT_TOPIC_THRESHOLDS,
            primaryColorHex = 0xFF4F46E5,
            secondaryColorHex = 0xFF818CF8,
            icon = Icons.Default.AutoGraph,
            category = BadgeCategory.MATH_GEO,
            symbolText = "∑"
        ),
        SharkBadgeDef(
            id = BADGE_MAT_TUREV,
            title = "Türev Ustası",
            emoji = "",
            description = "Teğet eğimi, ekstremum noktaları ve maksimum-minimum problemlerini ustalıkla çözen analist.",
            conditionLabel = "Türev'den 15 soru doğru çöz (15 / 45 / 100 / 220)",
            thresholds = DEFAULT_TOPIC_THRESHOLDS,
            primaryColorHex = 0xFF4338CA,
            secondaryColorHex = 0xFF6366F1,
            icon = Icons.Default.AutoGraph,
            category = BadgeCategory.MATH_GEO,
            symbolText = "f'(x)"
        ),
        SharkBadgeDef(
            id = BADGE_MAT_INTEGRAL,
            title = "İntegral Ustası",
            emoji = "",
            description = "Belirsiz/belirli integral, değişken değiştirme ve eğri altı alan sorularının hakimi.",
            conditionLabel = "İntegral'den 15 soru doğru çöz (15 / 45 / 100 / 220)",
            thresholds = DEFAULT_TOPIC_THRESHOLDS,
            primaryColorHex = 0xFF3730A3,
            secondaryColorHex = 0xFF818CF8,
            icon = Icons.Default.AutoGraph,
            category = BadgeCategory.MATH_GEO,
            symbolText = "∫"
        ),
        SharkBadgeDef(
            id = BADGE_MAT_LIMIT,
            title = "Limit & Süreklilik",
            emoji = "",
            description = "Sağdan-soldan yaklaşım, belirsizlikler ve parçalı fonksiyon sürekliliğinde keskin göz.",
            conditionLabel = "Limit ve Süreklilik'ten 15 soru doğru çöz (15 / 45 / 100 / 220)",
            thresholds = DEFAULT_TOPIC_THRESHOLDS,
            primaryColorHex = 0xFF1D4ED8,
            secondaryColorHex = 0xFF60A5FA,
            icon = Icons.Default.AutoGraph,
            category = BadgeCategory.MATH_GEO,
            symbolText = "lim"
        ),
        SharkBadgeDef(
            id = BADGE_MAT_TRIGONOMETRI,
            title = "Trigonometri",
            emoji = "",
            description = "Birim çember, toplam-fark, yarım açı ve trigonometrik denklemleri ezbere değil mantıkla çözen.",
            conditionLabel = "Trigonometri'den 15 soru doğru çöz (15 / 45 / 100 / 220)",
            thresholds = DEFAULT_TOPIC_THRESHOLDS,
            primaryColorHex = 0xFF2563EB,
            secondaryColorHex = 0xFF38BDF8,
            icon = Icons.Default.AutoGraph,
            category = BadgeCategory.MATH_GEO,
            symbolText = "sin θ"
        ),
        SharkBadgeDef(
            id = BADGE_MAT_FONKSIYONLAR,
            title = "Fonksiyonlar",
            emoji = "",
            description = "Bileşke, ters fonksiyon, grafik dönüşümleri ve tanım-görüntü kümesi sorularının uzmanı.",
            conditionLabel = "Fonksiyonlar'dan 15 soru doğru çöz (15 / 45 / 100 / 220)",
            thresholds = DEFAULT_TOPIC_THRESHOLDS,
            primaryColorHex = 0xFF4F46E5,
            secondaryColorHex = 0xFFA5B4FC,
            icon = Icons.Default.AutoGraph,
            category = BadgeCategory.MATH_GEO,
            symbolText = "f(x)"
        ),
        SharkBadgeDef(
            id = BADGE_MAT_POLINOM_IKINCI_DERECE,
            title = "Polinom & Parabol",
            emoji = "",
            description = "Polinom bölmesi, çarpanlara ayırma, 2. derece denklemler, eşitsizlikler ve parabol tepe noktası.",
            conditionLabel = "Polinom, Parabol ve 2. Derece'den 15 soru doğru çöz (15 / 45 / 100 / 220)",
            thresholds = DEFAULT_TOPIC_THRESHOLDS,
            primaryColorHex = 0xFF6D28D9,
            secondaryColorHex = 0xFFA78BFA,
            icon = Icons.Default.AutoGraph,
            category = BadgeCategory.MATH_GEO,
            symbolText = "x²"
        ),
        SharkBadgeDef(
            id = BADGE_MAT_LOGARITMA_DIZILER,
            title = "Logaritma & Diziler",
            emoji = "",
            description = "Üstel-logaritmik denklemler ile aritmetik ve geometrik dizilerde kuralları anında kuran.",
            conditionLabel = "Logaritma ve Diziler'den 15 soru doğru çöz (15 / 45 / 100 / 220)",
            thresholds = DEFAULT_TOPIC_THRESHOLDS,
            primaryColorHex = 0xFF5B21B6,
            secondaryColorHex = 0xFFC084FC,
            icon = Icons.Default.AutoGraph,
            category = BadgeCategory.MATH_GEO,
            symbolText = "log"
        ),
        SharkBadgeDef(
            id = BADGE_MAT_OLASILIK_PKB,
            title = "PKB & Olasılık",
            emoji = "",
            description = "Permütasyon, kombinasyon, binom açılımı ve koşullu olasılık senaryolarını hatasız sayan.",
            conditionLabel = "PKB ve Olasılık'tan 15 soru doğru çöz (15 / 45 / 100 / 220)",
            thresholds = DEFAULT_TOPIC_THRESHOLDS,
            primaryColorHex = 0xFF7E22CE,
            secondaryColorHex = 0xFFD8B4FE,
            icon = Icons.Default.AutoGraph,
            category = BadgeCategory.MATH_GEO,
            symbolText = "P(A)"
        ),
        SharkBadgeDef(
            id = BADGE_MAT_PROBLEMLER,
            title = "Problemler Fatihi",
            emoji = "",
            description = "Sayı-kesir, hız-hareket, yüzde-kar-zarar ve işçi-havuz problemlerinde denklem kurma ustası.",
            conditionLabel = "Problemler'den 15 soru doğru çöz (15 / 45 / 100 / 220)",
            thresholds = DEFAULT_TOPIC_THRESHOLDS,
            primaryColorHex = 0xFF0284C7,
            secondaryColorHex = 0xFF38BDF8,
            icon = Icons.Default.AutoGraph,
            category = BadgeCategory.MATH_GEO,
            symbolText = "%Δ"
        ),
        SharkBadgeDef(
            id = BADGE_MAT_SAYILAR_CEBIR,
            title = "Sayılar & Cebir",
            emoji = "",
            description = "Temel kavramlar, bölünebilme, EBOB-EKOK, üslü-köklü sayılar, mutlak değer ve kümeler.",
            conditionLabel = "Sayılar, Kümeler ve Cebir'den 15 soru doğru çöz (15 / 45 / 100 / 220)",
            thresholds = DEFAULT_TOPIC_THRESHOLDS,
            primaryColorHex = 0xFF1E40AF,
            secondaryColorHex = 0xFF60A5FA,
            icon = Icons.Default.AutoGraph,
            category = BadgeCategory.MATH_GEO,
            symbolText = "√x"
        ),
        SharkBadgeDef(
            id = BADGE_GEO_UCGENLER,
            title = "Üçgenler Uzmanı",
            emoji = "",
            description = "Dik üçgen, benzerlik, açıortay-kenarortay ve üçgende alan sorularında yardımcı doğruyu gören.",
            conditionLabel = "Üçgenler'den 15 soru doğru çöz (15 / 45 / 100 / 220)",
            thresholds = DEFAULT_TOPIC_THRESHOLDS,
            primaryColorHex = 0xFF4338CA,
            secondaryColorHex = 0xFF818CF8,
            icon = Icons.Default.AutoGraph,
            category = BadgeCategory.MATH_GEO,
            symbolText = "△"
        ),
        SharkBadgeDef(
            id = BADGE_GEO_COKGEN_DORTGEN,
            title = "Çokgen & Dörtgenler",
            emoji = "",
            description = "Çokgenler, yamuk, paralelkenar, eşkenar dörtgen, dikdörtgen ve kare özelliklerinin hakimi.",
            conditionLabel = "Çokgenler ve Dörtgenler'den 15 soru doğru çöz (15 / 45 / 100 / 220)",
            thresholds = DEFAULT_TOPIC_THRESHOLDS,
            primaryColorHex = 0xFF3B82F6,
            secondaryColorHex = 0xFF93C5FD,
            icon = Icons.Default.AutoGraph,
            category = BadgeCategory.MATH_GEO,
            symbolText = "▱"
        ),
        SharkBadgeDef(
            id = BADGE_GEO_CEMBER_DAIRE,
            title = "Çember & Daire",
            emoji = "",
            description = "Çemberde açı, kiriş-teğet uzunluk bağıntıları ve dairede dilim alanı sorularının ustası.",
            conditionLabel = "Çember ve Daire'den 15 soru doğru çöz (15 / 45 / 100 / 220)",
            thresholds = DEFAULT_TOPIC_THRESHOLDS,
            primaryColorHex = 0xFF0369A1,
            secondaryColorHex = 0xFF38BDF8,
            icon = Icons.Default.AutoGraph,
            category = BadgeCategory.MATH_GEO,
            symbolText = "πr²"
        ),
        SharkBadgeDef(
            id = BADGE_GEO_ANALITIK,
            title = "Analitik Geometri",
            emoji = "",
            description = "Nokta ve doğru analitiği, eğim, dönüşüm geometrisi ve çemberin analitik incelenmesi.",
            conditionLabel = "Analitik Geometri'den 15 soru doğru çöz (15 / 45 / 100 / 220)",
            thresholds = DEFAULT_TOPIC_THRESHOLDS,
            primaryColorHex = 0xFF4F46E5,
            secondaryColorHex = 0xFF60A5FA,
            icon = Icons.Default.AutoGraph,
            category = BadgeCategory.MATH_GEO,
            symbolText = "(x,y)"
        ),
        SharkBadgeDef(
            id = BADGE_GEO_KATI_CISIMLER,
            title = "Katı Cisimler",
            emoji = "",
            description = "Prizma, piramit, silindir, koni ve kürede yüzey alanı ile hacim hesaplarının mimarı.",
            conditionLabel = "Katı Cisimler'den 15 soru doğru çöz (15 / 45 / 100 / 220)",
            thresholds = DEFAULT_TOPIC_THRESHOLDS,
            primaryColorHex = 0xFF312E81,
            secondaryColorHex = 0xFF818CF8,
            icon = Icons.Default.AutoGraph,
            category = BadgeCategory.MATH_GEO,
            symbolText = "⬡"
        ),

        // ═══════════════════════════════════════════════════════════════════════
        // 3. PHYSICS BADGES (1 Main + 9 Sub-Topic = 10)
        // ═══════════════════════════════════════════════════════════════════════
        SharkBadgeDef(
            id = BADGE_FIZIK_KASIFI,
            title = "Fizik Kâşifi",
            emoji = "",
            description = "Kuvvet, hareket, elektrik ve modern fizik sorularını doğa yasalarıyla çözen kâşif.",
            conditionLabel = "Fizik'ten 15 soru doğru çöz (15 / 45 / 100 / 220)",
            thresholds = DEFAULT_TOPIC_THRESHOLDS,
            primaryColorHex = 0xFF0369A1,
            secondaryColorHex = 0xFF38BDF8,
            icon = Icons.Default.Star,
            category = BadgeCategory.PHYSICS,
            symbolText = "F⃗"
        ),
        SharkBadgeDef(
            id = BADGE_FIZ_KUVVET_HAREKET,
            title = "Kuvvet & Hareket",
            emoji = "",
            description = "Vektörler, bağıl hareket, Newton'un hareket yasaları ve bir/iki boyutta sabit ivmeli hareket.",
            conditionLabel = "Kuvvet ve Hareket'ten 15 soru doğru çöz (15 / 45 / 100 / 220)",
            thresholds = DEFAULT_TOPIC_THRESHOLDS,
            primaryColorHex = 0xFF0284C7,
            secondaryColorHex = 0xFF38BDF8,
            icon = Icons.Default.Star,
            category = BadgeCategory.PHYSICS,
            symbolText = "F=ma"
        ),
        SharkBadgeDef(
            id = BADGE_FIZ_IS_ENERJI_MOMENTUM,
            title = "Enerji & Momentum",
            emoji = "",
            description = "İş-güç-enerji korunumu, itme, çizgisel momentum ve esnek/esnek olmayan çarpışmalar.",
            conditionLabel = "Enerji ve Momentum'dan 15 soru doğru çöz (15 / 45 / 100 / 220)",
            thresholds = DEFAULT_TOPIC_THRESHOLDS,
            primaryColorHex = 0xFF0369A1,
            secondaryColorHex = 0xFF60A5FA,
            icon = Icons.Default.Star,
            category = BadgeCategory.PHYSICS,
            symbolText = "p⃗"
        ),
        SharkBadgeDef(
            id = BADGE_FIZ_ELEKTRIK_MANYETIZMA,
            title = "Elektrik & Manyetizma",
            emoji = "",
            description = "Elektrostatik, elektrik alan/potansiyel, sığaçlar, devreler, manyetik alan, indüksiyon ve alternatif akım.",
            conditionLabel = "Elektrik ve Manyetizma'dan 15 soru doğru çöz (15 / 45 / 100 / 220)",
            thresholds = DEFAULT_TOPIC_THRESHOLDS,
            primaryColorHex = 0xFF1D4ED8,
            secondaryColorHex = 0xFF38BDF8,
            icon = Icons.Default.Bolt,
            category = BadgeCategory.PHYSICS,
            symbolText = "Ω"
        ),
        SharkBadgeDef(
            id = BADGE_FIZ_DALGALAR_OPTIK,
            title = "Dalgalar & Optik",
            emoji = "",
            description = "Yay-su-ses dalgaları, kırınım-girişim, Doppler olayı, aydınlanma, ayna, mercek ve kırılma.",
            conditionLabel = "Dalgalar ve Optik'ten 15 soru doğru çöz (15 / 45 / 100 / 220)",
            thresholds = DEFAULT_TOPIC_THRESHOLDS,
            primaryColorHex = 0xFF0E7490,
            secondaryColorHex = 0xFF22D3EE,
            icon = Icons.Default.Star,
            category = BadgeCategory.PHYSICS,
            symbolText = "λ"
        ),
        SharkBadgeDef(
            id = BADGE_FIZ_CEMBERSEL_HARMONIK,
            title = "Çembersel & Harmonik",
            emoji = "",
            description = "Düzgün çembersel hareket, açısal momentum, kütle çekim, Kepler ve basit harmonik hareket.",
            conditionLabel = "Çembersel ve Harmonik Hareket'ten 15 soru doğru çöz (15 / 45 / 100 / 220)",
            thresholds = DEFAULT_TOPIC_THRESHOLDS,
            primaryColorHex = 0xFF1E40AF,
            secondaryColorHex = 0xFF60A5FA,
            icon = Icons.Default.Star,
            category = BadgeCategory.PHYSICS,
            symbolText = "ω"
        ),
        SharkBadgeDef(
            id = BADGE_FIZ_MODERN_FIZIK,
            title = "Modern Fizik & Kuantum",
            emoji = "",
            description = "Özel görelilik, kara cisim ışıması, fotoelektrik olay, Compton saçılması ve de Broglie dalga boyu.",
            conditionLabel = "Modern Fizik'ten 15 soru doğru çöz (15 / 45 / 100 / 220)",
            thresholds = DEFAULT_TOPIC_THRESHOLDS,
            primaryColorHex = 0xFF3730A3,
            secondaryColorHex = 0xFF38BDF8,
            icon = Icons.Default.Star,
            category = BadgeCategory.PHYSICS,
            symbolText = "hν"
        ),
        SharkBadgeDef(
            id = BADGE_FIZ_MADDE_ISI_BASINC,
            title = "Madde, Isı & Basınç",
            emoji = "",
            description = "Madde ve özellikleri, ısı-sıcaklık-genleşme, katı-sıvı-gaz basıncı ve Arşimet kaldırma kuvveti.",
            conditionLabel = "Madde, Isı ve Basınç'tan 15 soru doğru çöz (15 / 45 / 100 / 220)",
            thresholds = DEFAULT_TOPIC_THRESHOLDS,
            primaryColorHex = 0xFF0284C7,
            secondaryColorHex = 0xFF67E8F9,
            icon = Icons.Default.Star,
            category = BadgeCategory.PHYSICS,
            symbolText = "ΔT"
        ),
        SharkBadgeDef(
            id = BADGE_FIZ_TORK_DENGE,
            title = "Tork & Denge",
            emoji = "",
            description = "Kesişen kuvvetlerin dengesi, tork, ağırlık/kütle merkezi ve basit makinelerde kuvvet kazancı.",
            conditionLabel = "Tork, Denge ve Basit Makineler'den 15 soru doğru çöz (15 / 45 / 100 / 220)",
            thresholds = DEFAULT_TOPIC_THRESHOLDS,
            primaryColorHex = 0xFF0369A1,
            secondaryColorHex = 0xFF7DD3FC,
            icon = Icons.Default.Star,
            category = BadgeCategory.PHYSICS,
            symbolText = "τ⃗"
        ),
        SharkBadgeDef(
            id = BADGE_FIZ_ATOM_RADYOAKTIVITE,
            title = "Atom & Radyoaktivite",
            emoji = "",
            description = "Bohr atom modeli, enerji seviyeleri, standart model, radyoaktif bozunma ve nükleer fizik.",
            conditionLabel = "Atom Fiziği ve Radyoaktivite'den 15 soru doğru çöz (15 / 45 / 100 / 220)",
            thresholds = DEFAULT_TOPIC_THRESHOLDS,
            primaryColorHex = 0xFF1E3A8A,
            secondaryColorHex = 0xFF38BDF8,
            icon = Icons.Default.Star,
            category = BadgeCategory.PHYSICS,
            symbolText = "αβγ"
        ),

        // ═══════════════════════════════════════════════════════════════════════
        // 4. CHEMISTRY BADGES (1 Main + 8 Sub-Topic = 9)
        // ═══════════════════════════════════════════════════════════════════════
        SharkBadgeDef(
            id = BADGE_KIMYA_SIMYACISI,
            title = "Kimya Simyacısı",
            emoji = "",
            description = "Tepkime, mol, denge ve organik kimya sorularında denklemleri kusursuz kuran simyacı.",
            conditionLabel = "Kimya'dan 15 soru doğru çöz (15 / 45 / 100 / 220)",
            thresholds = DEFAULT_TOPIC_THRESHOLDS,
            primaryColorHex = 0xFF7C3AED,
            secondaryColorHex = 0xFFA78BFA,
            icon = Icons.Default.Science,
            category = BadgeCategory.CHEMISTRY,
            symbolText = ""
        ),
        SharkBadgeDef(
            id = BADGE_KIM_ATOM_PERIYODIK,
            title = "Atom & Periyodik Sistem",
            emoji = "",
            description = "Modern atom teorisi, kuantum sayıları, elektron dizilimi ve periyodik özellik değişimleri.",
            conditionLabel = "Atom ve Periyodik Sistem'den 15 soru doğru çöz (15 / 45 / 100 / 220)",
            thresholds = DEFAULT_TOPIC_THRESHOLDS,
            primaryColorHex = 0xFF6D28D9,
            secondaryColorHex = 0xFFA78BFA,
            icon = Icons.Default.Science,
            category = BadgeCategory.CHEMISTRY,
            symbolText = "e⁻"
        ),
        SharkBadgeDef(
            id = BADGE_KIM_MOL_HESAPLAMA,
            title = "Mol & Kimyasal Hesap",
            emoji = "",
            description = "Kimyanın temel kanunları, Avogadro sayısı, mol kavramı ve tepkime verim/artan madde hesapları.",
            conditionLabel = "Mol ve Kimyasal Hesaplamalar'dan 15 soru doğru çöz (15 / 45 / 100 / 220)",
            thresholds = DEFAULT_TOPIC_THRESHOLDS,
            primaryColorHex = 0xFF7C3AED,
            secondaryColorHex = 0xFFC084FC,
            icon = Icons.Default.Science,
            category = BadgeCategory.CHEMISTRY,
            symbolText = "mol"
        ),
        SharkBadgeDef(
            id = BADGE_KIM_GAZLAR_COZELTILER,
            title = "Gazlar & Çözeltiler",
            emoji = "",
            description = "İdeal gaz denklemi, kinetik teori, kısmi basınç, sıvı çözeltiler, molarite ve koligatif özellikler.",
            conditionLabel = "Gazlar ve Sıvı Çözeltiler'den 15 soru doğru çöz (15 / 45 / 100 / 220)",
            thresholds = DEFAULT_TOPIC_THRESHOLDS,
            primaryColorHex = 0xFF5B21B6,
            secondaryColorHex = 0xFFA78BFA,
            icon = Icons.Default.Science,
            category = BadgeCategory.CHEMISTRY,
            symbolText = "PV"
        ),
        SharkBadgeDef(
            id = BADGE_KIM_ENERJI_HIZ,
            title = "Tepkime Enerjisi & Hız",
            emoji = "",
            description = "Oluşum entalpisi, Hess yasası, bağ enerjileri, çarpışma teorisi ve tepkime hız bağıntısı.",
            conditionLabel = "Tepkimelerde Enerji ve Hız'dan 15 soru doğru çöz (15 / 45 / 100 / 220)",
            thresholds = DEFAULT_TOPIC_THRESHOLDS,
            primaryColorHex = 0xFF9333EA,
            secondaryColorHex = 0xFFD8B4FE,
            icon = Icons.Default.Science,
            category = BadgeCategory.CHEMISTRY,
            symbolText = "ΔH"
        ),
        SharkBadgeDef(
            id = BADGE_KIM_DENGE_ASIT_BAZ,
            title = "Denge & Asit-Baz",
            emoji = "",
            description = "Kimyasal denge, Le Chatelier ilkesi, sulu çözelti dengeleri, pH/pOH, titrasyon ve Kçç.",
            conditionLabel = "Kimyasal Denge ve Asit-Baz'dan 15 soru doğru çöz (15 / 45 / 100 / 220)",
            thresholds = DEFAULT_TOPIC_THRESHOLDS,
            primaryColorHex = 0xFF7E22CE,
            secondaryColorHex = 0xFFC084FC,
            icon = Icons.Default.Science,
            category = BadgeCategory.CHEMISTRY,
            symbolText = "pH"
        ),
        SharkBadgeDef(
            id = BADGE_KIM_ELEKTROKIMYA,
            title = "Elektrokimya & Redoks",
            emoji = "",
            description = "İndirgenme-yükseltgenme tepkimeleri, aktiflik, galvanik piller, Nernst denklemi ve elektroliz.",
            conditionLabel = "Elektrokimya'dan 15 soru doğru çöz (15 / 45 / 100 / 220)",
            thresholds = DEFAULT_TOPIC_THRESHOLDS,
            primaryColorHex = 0xFF6B21A8,
            secondaryColorHex = 0xFFA855F7,
            icon = Icons.Default.Science,
            category = BadgeCategory.CHEMISTRY,
            symbolText = "E°"
        ),
        SharkBadgeDef(
            id = BADGE_KIM_ORGANIK,
            title = "Organik Kimya",
            emoji = "",
            description = "Hibritleşme, hidrokarbonlar, fonksiyonel gruplar, izomerlik ve aromatik bileşikler.",
            conditionLabel = "Organik Kimya'dan 15 soru doğru çöz (15 / 45 / 100 / 220)",
            thresholds = DEFAULT_TOPIC_THRESHOLDS,
            primaryColorHex = 0xFF4C1D95,
            secondaryColorHex = 0xFFA78BFA,
            icon = Icons.Default.Science,
            category = BadgeCategory.CHEMISTRY,
            symbolText = "C₆H₆"
        ),
        SharkBadgeDef(
            id = BADGE_KIM_ETKILESIMLER_MADDE,
            title = "Türler Arası Etkileşim",
            emoji = "",
            description = "Güçlü/zayıf etkileşimler, Lewis yapısı, maddenin halleri, karışımlar ve ayırma yöntemleri.",
            conditionLabel = "Türler Arası Etkileşim ve Madde'den 15 soru doğru çöz (15 / 45 / 100 / 220)",
            thresholds = DEFAULT_TOPIC_THRESHOLDS,
            primaryColorHex = 0xFF8B5CF6,
            secondaryColorHex = 0xFFC4B5FD,
            icon = Icons.Default.Science,
            category = BadgeCategory.CHEMISTRY,
            symbolText = "H₂O"
        ),

        // ═══════════════════════════════════════════════════════════════════════
        // 5. BIOLOGY BADGES (1 Main + 7 Sub-Topic = 8)
        // ═══════════════════════════════════════════════════════════════════════
        SharkBadgeDef(
            id = BADGE_BIYOLOJI_UZMANI,
            title = "Biyoloji Uzmanı",
            emoji = "",
            description = "Sistemler, genetik ve ekoloji sorularında mekanizmaları berrak biçimde açıklayan uzman.",
            conditionLabel = "Biyoloji'den 15 soru doğru çöz (15 / 45 / 100 / 220)",
            thresholds = DEFAULT_TOPIC_THRESHOLDS,
            primaryColorHex = 0xFF15803D,
            secondaryColorHex = 0xFF4ADE80,
            icon = Icons.Default.Psychology,
            category = BadgeCategory.BIOLOGY,
            symbolText = "DNA"
        ),
        SharkBadgeDef(
            id = BADGE_BIYO_HUCRE_CANLILAR,
            title = "Hücre & Canlılar",
            emoji = "",
            description = "Yaşam bilimi biyoloji, organik bileşikler, hücre zarı madde geçişleri, organeller ve sınıflandırma.",
            conditionLabel = "Hücre ve Sınıflandırma'dan 15 soru doğru çöz (15 / 45 / 100 / 220)",
            thresholds = DEFAULT_TOPIC_THRESHOLDS,
            primaryColorHex = 0xFF166534,
            secondaryColorHex = 0xFF4ADE80,
            icon = Icons.Default.Psychology,
            category = BadgeCategory.BIOLOGY,
            symbolText = "µm"
        ),
        SharkBadgeDef(
            id = BADGE_BIYO_INSAN_FIZYOLOJISI,
            title = "İnsan Fizyolojisi",
            emoji = "",
            description = "Sindirim, dolaşım, bağışıklık, solunum, üriner/boşaltım ve destek-hareket sistemleri.",
            conditionLabel = "İnsan Fizyolojisi ve Sistemler'den 15 soru doğru çöz (15 / 45 / 100 / 220)",
            thresholds = DEFAULT_TOPIC_THRESHOLDS,
            primaryColorHex = 0xFF15803D,
            secondaryColorHex = 0xFF86EFAC,
            icon = Icons.Default.Psychology,
            category = BadgeCategory.BIOLOGY,
            symbolText = "O₂"
        ),
        SharkBadgeDef(
            id = BADGE_BIYO_ENDOKRIN_SINIR,
            title = "Sinir & Endokrin",
            emoji = "",
            description = "Sinir sistemi, impuls iletimi, beyin bölümleri, endokrin hormonlar ve duyu organları.",
            conditionLabel = "Sinir, Endokrin ve Duyu'dan 15 soru doğru çöz (15 / 45 / 100 / 220)",
            thresholds = DEFAULT_TOPIC_THRESHOLDS,
            primaryColorHex = 0xFF047857,
            secondaryColorHex = 0xFF34D399,
            icon = Icons.Default.Psychology,
            category = BadgeCategory.BIOLOGY,
            symbolText = "mV"
        ),
        SharkBadgeDef(
            id = BADGE_BIYO_KALITIM_EVRIM,
            title = "Kalıtım & Bölünme",
            emoji = "",
            description = "Mitoz-mayoz hücre bölünmeleri, Mendel genetiği, eşeye bağlı kalıtım ve soyağacı analizi.",
            conditionLabel = "Kalıtım ve Hücre Bölünmeleri'nden 15 soru doğru çöz (15 / 45 / 100 / 220)",
            thresholds = DEFAULT_TOPIC_THRESHOLDS,
            primaryColorHex = 0xFF059669,
            secondaryColorHex = 0xFF6EE7B7,
            icon = Icons.Default.Psychology,
            category = BadgeCategory.BIOLOGY,
            symbolText = "Aa"
        ),
        SharkBadgeDef(
            id = BADGE_BIYO_GENDEN_PROTEINE,
            title = "Genden Proteine",
            emoji = "",
            description = "DNA replikasyonu, genetik şifre, transkripsiyon, translasyon, protein sentezi ve biyoteknoloji.",
            conditionLabel = "Genden Proteine'den 15 soru doğru çöz (15 / 45 / 100 / 220)",
            thresholds = DEFAULT_TOPIC_THRESHOLDS,
            primaryColorHex = 0xFF0D9488,
            secondaryColorHex = 0xFF5EEAD4,
            icon = Icons.Default.Psychology,
            category = BadgeCategory.BIOLOGY,
            symbolText = "RNA"
        ),
        SharkBadgeDef(
            id = BADGE_BIYO_BITKI,
            title = "Bitki Biyolojisi",
            emoji = "",
            description = "Bitkisel dokular, hormonlar, tropizma-nasti hareketleri, madde taşınması ve bitkilerde üreme.",
            conditionLabel = "Bitki Biyolojisi'den 15 soru doğru çöz (15 / 45 / 100 / 220)",
            thresholds = DEFAULT_TOPIC_THRESHOLDS,
            primaryColorHex = 0xFF16A34A,
            secondaryColorHex = 0xFF4ADE80,
            icon = Icons.Default.Psychology,
            category = BadgeCategory.BIOLOGY,
            symbolText = "CO₂"
        ),
        SharkBadgeDef(
            id = BADGE_BIYO_EKOLOJI_ENERJI,
            title = "Fotosentez & Ekoloji",
            emoji = "",
            description = "ATP, fotosentez, kemosentez, hücresel solunum, komünite-popülasyon ve ekosistem ekolojisi.",
            conditionLabel = "Fotosentez, Solunum ve Ekoloji'den 15 soru doğru çöz (15 / 45 / 100 / 220)",
            thresholds = DEFAULT_TOPIC_THRESHOLDS,
            primaryColorHex = 0xFF14532D,
            secondaryColorHex = 0xFF4ADE80,
            icon = Icons.Default.Psychology,
            category = BadgeCategory.BIOLOGY,
            symbolText = "ATP"
        ),

        // ═══════════════════════════════════════════════════════════════════════
        // 6. TURKISH & LITERATURE BADGES (1 Main + 6 Sub-Topic = 7)
        // ═══════════════════════════════════════════════════════════════════════
        SharkBadgeDef(
            id = BADGE_PARAGRAF_AVCISI,
            title = "Paragraf Avcısı",
            emoji = "",
            description = "Türkçe, Paragraf ve Edebiyat sorularında ana düşünceyi saniyeler içinde yakalayan okuyucu.",
            conditionLabel = "Türkçe ve Edebiyat'tan 15 soru doğru çöz (15 / 45 / 100 / 220)",
            thresholds = DEFAULT_TOPIC_THRESHOLDS,
            primaryColorHex = 0xFFBE185D,
            secondaryColorHex = 0xFFF472B6,
            icon = Icons.Default.School,
            category = BadgeCategory.TURKISH_LIT,
            symbolText = "¶"
        ),
        SharkBadgeDef(
            id = BADGE_TUR_PARAGRAF_ANLAM,
            title = "Paragraf Analisti",
            emoji = "",
            description = "Paragrafta ana düşünce, yardımcı düşünce, yapı, akış bozan cümle ve anlatım biçimleri.",
            conditionLabel = "Paragrafta Anlam'dan 15 soru doğru çöz (15 / 45 / 100 / 220)",
            thresholds = DEFAULT_TOPIC_THRESHOLDS,
            primaryColorHex = 0xFF9D174D,
            secondaryColorHex = 0xFFF472B6,
            icon = Icons.Default.School,
            category = BadgeCategory.TURKISH_LIT,
            symbolText = "¶+"
        ),
        SharkBadgeDef(
            id = BADGE_TUR_SOZCUK_CUMLE,
            title = "Sözcük & Cümle Anlam",
            emoji = "",
            description = "Çok anlamlılık, deyimler, cümlede anlam ilişkileri, kesin yargı ve cümle yorumu.",
            conditionLabel = "Sözcük ve Cümlede Anlam'dan 15 soru doğru çöz (15 / 45 / 100 / 220)",
            thresholds = DEFAULT_TOPIC_THRESHOLDS,
            primaryColorHex = 0xFFBE185D,
            secondaryColorHex = 0xFFF9A8D4,
            icon = Icons.Default.School,
            category = BadgeCategory.TURKISH_LIT,
            symbolText = "“A”"
        ),
        SharkBadgeDef(
            id = BADGE_TUR_DIL_BILGISI,
            title = "Dil Bilgisi Uzmanı",
            emoji = "",
            description = "Ses bilgisi, sözcükte yapı/ekler, sözcük türleri, fiilimsiler, cümlenin ögeleri ve anlatım bozukluğu.",
            conditionLabel = "Dil Bilgisi'nden 15 soru doğru çöz (15 / 45 / 100 / 220)",
            thresholds = DEFAULT_TOPIC_THRESHOLDS,
            primaryColorHex = 0xFFDB2777,
            secondaryColorHex = 0xFFF472B6,
            icon = Icons.Default.School,
            category = BadgeCategory.TURKISH_LIT,
            symbolText = "Ek"
        ),
        SharkBadgeDef(
            id = BADGE_TUR_YAZIM_NOKTALAMA,
            title = "Yazım & Noktalama",
            emoji = "",
            description = "TDK yazım kuralları, birleşik/ayrı yazımlar, büyük harfler ve noktalama işaretleri.",
            conditionLabel = "Yazım ve Noktalama'dan 15 soru doğru çöz (15 / 45 / 100 / 220)",
            thresholds = DEFAULT_TOPIC_THRESHOLDS,
            primaryColorHex = 0xFFE11D48,
            secondaryColorHex = 0xFFFB7185,
            icon = Icons.Default.School,
            category = BadgeCategory.TURKISH_LIT,
            symbolText = "?!;"
        ),
        SharkBadgeDef(
            id = BADGE_EDB_SIIR_DONEMLER,
            title = "Şiir & Dönemler",
            emoji = "",
            description = "Şiir bilgisi, söz sanatları, İslamiyet öncesi, Halk, Divan, Tanzimat, Servetifünun ve Milli Edebiyat.",
            conditionLabel = "Şiir ve Edebi Dönemler'den 15 soru doğru çöz (15 / 45 / 100 / 220)",
            thresholds = DEFAULT_TOPIC_THRESHOLDS,
            primaryColorHex = 0xFF831843,
            secondaryColorHex = 0xFFF472B6,
            icon = Icons.Default.School,
            category = BadgeCategory.TURKISH_LIT,
            symbolText = "ABAB"
        ),
        SharkBadgeDef(
            id = BADGE_EDB_ROMAN_HIKAYE,
            title = "Roman & Hikâye",
            emoji = "",
            description = "Cumhuriyet dönemi roman-hikâye, yazar-eser eşleştirmeleri, tiyatro ve edebi akımlar.",
            conditionLabel = "Roman, Hikâye ve Eserler'den 15 soru doğru çöz (15 / 45 / 100 / 220)",
            thresholds = DEFAULT_TOPIC_THRESHOLDS,
            primaryColorHex = 0xFF9F1239,
            secondaryColorHex = 0xFFFDA4AF,
            icon = Icons.Default.School,
            category = BadgeCategory.TURKISH_LIT,
            symbolText = "§"
        ),

        // ═══════════════════════════════════════════════════════════════════════
        // 7. SOCIAL SCIENCES & LANGUAGE BADGES (1 Main + 5 Sub-Topic = 6)
        // ═══════════════════════════════════════════════════════════════════════
        SharkBadgeDef(
            id = BADGE_SOSYAL_BILGE,
            title = "Sosyal Bilge",
            emoji = "",
            description = "Tarih, Coğrafya, Felsefe, Din Kültürü ve Yabancı Dil sorularını derinlemesine yorumlayan bilge.",
            conditionLabel = "Sosyal Bilimler'den 15 soru doğru çöz (15 / 45 / 100 / 220)",
            thresholds = DEFAULT_TOPIC_THRESHOLDS,
            primaryColorHex = 0xFF0F766E,
            secondaryColorHex = 0xFF5EEAD4,
            icon = Icons.Default.Public,
            category = BadgeCategory.SOCIAL_LANG,
            symbolText = ""
        ),
        SharkBadgeDef(
            id = BADGE_TAR_OSMANLI_ISLAM,
            title = "Türk & Osmanlı Tarihi",
            emoji = "",
            description = "İlk ve Orta Çağlarda Türk Dünyası, Türk-İslam devletleri ve Osmanlı kuruluş-yükseliş-dağılma.",
            conditionLabel = "Türk ve Osmanlı Tarihi'nden 15 soru doğru çöz (15 / 45 / 100 / 220)",
            thresholds = DEFAULT_TOPIC_THRESHOLDS,
            primaryColorHex = 0xFFB45309,
            secondaryColorHex = 0xFFFBBF24,
            icon = Icons.Default.Public,
            category = BadgeCategory.SOCIAL_LANG,
            symbolText = "1453"
        ),
        SharkBadgeDef(
            id = BADGE_TAR_INKILAP_CAGDAS,
            title = "İnkılap & Çağdaş Tarih",
            emoji = "",
            description = "Milli Mücadele, Atatürk ilke ve inkılapları, Türk dış politikası ve 20. yüzyıl dünya tarihi.",
            conditionLabel = "İnkılap ve Çağdaş Tarih'ten 15 soru doğru çöz (15 / 45 / 100 / 220)",
            thresholds = DEFAULT_TOPIC_THRESHOLDS,
            primaryColorHex = 0xFFD97706,
            secondaryColorHex = 0xFFFDE047,
            icon = Icons.Default.Public,
            category = BadgeCategory.SOCIAL_LANG,
            symbolText = "1923"
        ),
        SharkBadgeDef(
            id = BADGE_COG_DOGAL_TURKIYE,
            title = "Coğrafya & Harita",
            emoji = "",
            description = "İklim, iç-dış kuvvetler, nüfus-yerleşme, ekonomik faaliyetler, bölge ve Türkiye coğrafyası.",
            conditionLabel = "Coğrafya'dan 15 soru doğru çöz (15 / 45 / 100 / 220)",
            thresholds = DEFAULT_TOPIC_THRESHOLDS,
            primaryColorHex = 0xFF0F766E,
            secondaryColorHex = 0xFF2DD4BF,
            icon = Icons.Default.Public,
            category = BadgeCategory.SOCIAL_LANG,
            symbolText = "36°N"
        ),
        SharkBadgeDef(
            id = BADGE_FEL_FELSEFE_MANTIK,
            title = "Felsefe & Mantık",
            emoji = "",
            description = "Bilgi-varlık-ahlak felsefesi, psikoloji, sosyoloji ve klasik/sembolik mantık çıkarımları.",
            conditionLabel = "Felsefe Grubu ve Mantık'tan 15 soru doğru çöz (15 / 45 / 100 / 220)",
            thresholds = DEFAULT_TOPIC_THRESHOLDS,
            primaryColorHex = 0xFF115E59,
            secondaryColorHex = 0xFF5EEAD4,
            icon = Icons.Default.Public,
            category = BadgeCategory.SOCIAL_LANG,
            symbolText = "Φ"
        ),
        SharkBadgeDef(
            id = BADGE_DIL_INGILIZCE_DIN,
            title = "YDT İngilizce & Din",
            emoji = "",
            description = "Yabancı Dil (İngilizce kelime, gramer, okuma) ve Din Kültürü kavram/yorum soruları.",
            conditionLabel = "Yabancı Dil ve Din Kültürü'nden 15 soru doğru çöz (15 / 45 / 100 / 220)",
            thresholds = DEFAULT_TOPIC_THRESHOLDS,
            primaryColorHex = 0xFF0D9488,
            secondaryColorHex = 0xFF99F6E4,
            icon = Icons.Default.Public,
            category = BadgeCategory.SOCIAL_LANG,
            symbolText = "EN"
        )
    )

    private val BADGE_MAP: Map<String, SharkBadgeDef> = ALL_BADGES.associateBy { it.id }

    fun findById(id: String?): SharkBadgeDef? {
        if (id.isNullOrBlank()) return null
        val normalizedId = id.trim().substringBefore(":")
        return BADGE_MAP[normalizedId]
    }

    fun badgesByCategory(category: BadgeCategory): List<SharkBadgeDef> =
        ALL_BADGES.filter { it.category == category }

    fun normalizeTurkish(raw: String): String =
        raw.trim()
            .lowercase(Locale("tr"))
            .replace('ı', 'i')
            .replace('ğ', 'g')
            .replace('ü', 'u')
            .replace('ş', 's')
            .replace('ö', 'o')
            .replace('ç', 'c')
            .replace('â', 'a')
            .replace('î', 'i')
            .replace('û', 'u')

    fun subjectBadgeIdFor(subject: String): String? {
        val lower = normalizeTurkish(subject)
        return when {
            lower.contains("mat") ||
                lower.contains("turev") ||
                lower.contains("integral") ||
                lower.contains("geometri") ||
                lower.contains("geo") ||
                lower.contains("sayisal") -> BADGE_MATEMATIK_CANAVARI

            lower.contains("fizik") || lower.contains("fiz") -> BADGE_FIZIK_KASIFI
            lower.contains("kimya") || lower.contains("kim") -> BADGE_KIMYA_SIMYACISI
            lower.contains("biyoloji") || lower.contains("biyo") -> BADGE_BIYOLOJI_UZMANI
            lower.contains("turkce") ||
                lower.contains("paragraf") ||
                lower.contains("edebiyat") ||
                lower.contains("dil bilgisi") ||
                lower.contains("sozel") -> BADGE_PARAGRAF_AVCISI

            lower.contains("tarih") ||
                lower.contains("cografya") ||
                lower.contains("felsefe") ||
                lower.contains("mantik") ||
                lower.contains("psikoloji") ||
                lower.contains("sosyoloji") ||
                lower.contains("din") ||
                lower.contains("sosyal") ||
                lower.contains("inkilap") ||
                lower.contains("ingilizce") ||
                lower.contains("ydt") ||
                lower.contains("yabanci dil") -> BADGE_SOSYAL_BILGE

            else -> null
        }
    }

    private fun mainSubjectBadgeForCategory(category: BadgeCategory): String? =
        when (category) {
            BadgeCategory.MATH_GEO -> BADGE_MATEMATIK_CANAVARI
            BadgeCategory.PHYSICS -> BADGE_FIZIK_KASIFI
            BadgeCategory.CHEMISTRY -> BADGE_KIMYA_SIMYACISI
            BadgeCategory.BIOLOGY -> BADGE_BIYOLOJI_UZMANI
            BadgeCategory.TURKISH_LIT -> BADGE_PARAGRAF_AVCISI
            BadgeCategory.SOCIAL_LANG -> BADGE_SOSYAL_BILGE
            BadgeCategory.MENTORSHIP -> null
        }

    /**
     * Matches both specific sub-topic badge(s) AND the parent subject badge by normalizing
     * Turkish characters across [subject] and [topic] (e.g. `topic = "Türev Uygulamaları"`
     * returns `listOf("mat_turev", "matematik_canavari")`).
     */
    fun topicAndSubjectBadgeIdsFor(subject: String, topic: String): List<String> {
        val normSubject = normalizeTurkish(subject)
        val normTopic = normalizeTurkish(topic)
        val combined = "$normSubject $normTopic".trim()
        if (combined.isBlank()) return emptyList()

        val matchedTopicIds = mutableListOf<String>()
        fun matchIf(badgeId: String, vararg keywords: String) {
            if (keywords.any { kw -> combined.contains(kw) }) {
                matchedTopicIds.add(badgeId)
            }
        }

        // ── Matematik & Geometri (15) ──
        matchIf(BADGE_MAT_TUREV, "turev", "ekstremum", "teget", "maksimum minimum", "l'hospital")
        matchIf(BADGE_MAT_INTEGRAL, "integral", "riemann", "egri altinda", "belirli integral", "belirsiz integral")
        matchIf(BADGE_MAT_LIMIT, "limit", "sureklilik", "belirsizlik")
        matchIf(BADGE_MAT_TRIGONOMETRI, "trigonometri", "birim cember", "sinus", "kosinus", "tanjant", "kotanjant", "yarim aci", "toplam fark")
        matchIf(BADGE_MAT_FONKSIYONLAR, "fonksiyon", "bileske", "ters fonksiyon", "tanim kumesi")
        matchIf(BADGE_MAT_POLINOM_IKINCI_DERECE, "polinom", "parabol", "ikinci derece", "2. derece", "carpanlara ayirma", "esitsizlik", "diskriminant", "karmasik sayi")
        matchIf(BADGE_MAT_LOGARITMA_DIZILER, "logaritma", "dizi", "aritmetik dizi", "geometrik dizi", "ustel")
        matchIf(BADGE_MAT_OLASILIK_PKB, "olasilik", "permutasyon", "kombinasyon", "binom", "pkb", "sayma")
        matchIf(BADGE_MAT_PROBLEMLER, "problem", "yas problem", "hiz problem", "hareket problem", "yuzde", "kar zarar", "karisim problem", "isci", "sayi kesir")
        matchIf(BADGE_MAT_SAYILAR_CEBIR, "temel kavram", "sayilar", "bolunebilme", "ebob", "ekok", "rasyonel", "uslu", "koklu", "mutlak deger", "oran oranti", "kume", "mantik")
        matchIf(BADGE_GEO_UCGENLER, "ucgen", "pisagor", "oklid", "aciortay", "kenarortay", "benzerlik", "dogruda aci")
        matchIf(BADGE_GEO_COKGEN_DORTGEN, "cokgen", "dortgen", "yamuk", "paralelkenar", "eskenar dortgen", "dikdortgen", "kare", "deltoid")
        matchIf(BADGE_GEO_CEMBER_DAIRE, "cember", "daire", "kiris", "yay", "cemberde aci", "dairede alan")
        matchIf(BADGE_GEO_ANALITIK, "analitik", "dogrunun analitigi", "noktanin analitigi", "donusum geometrisi", "cemberin analitigi")
        matchIf(BADGE_GEO_KATI_CISIMLER, "kati cisim", "prizma", "piramit", "silindir", "koni", "kure", "uzay geometri")

        // ── Fizik (9) ──
        matchIf(BADGE_FIZ_KUVVET_HAREKET, "kuvvet", "hareket", "vektor", "bagil hareket", "newton", "atis", "serbest dusme", "egik atis", "ivme", "surtunme")
        matchIf(BADGE_FIZ_IS_ENERJI_MOMENTUM, "is guc", "mekanik enerji", "kinetik enerji", "potansiyel enerji", "itme", "momentum", "carpisma")
        matchIf(BADGE_FIZ_ELEKTRIK_MANYETIZMA, "elektrik", "manyetizma", "elektrostatik", "kondansator", "sigac", "induksiyon", "alternatif akim", "transformator", "ohm", "devre")
        matchIf(BADGE_FIZ_DALGALAR_OPTIK, "dalga", "optik", "aydinlanma", "ayna", "mercek", "kirilma", "prizma", "girisim", "kirinim", "doppler")
        matchIf(BADGE_FIZ_CEMBERSEL_HARMONIK, "cembersel", "basit harmonik", "acisal momentum", "eylemsizlik", "kepler", "kutle cekim", "sarkac")
        matchIf(BADGE_FIZ_MODERN_FIZIK, "modern fizik", "fotoelektrik", "compton", "de broglie", "kara cisim", "ozel gorelilik", "relativite", "kuantum")
        matchIf(BADGE_FIZ_MADDE_ISI_BASINC, "isi ve sicaklik", "isi sicaklik", "genlesme", "basinc", "kaldirma kuvveti", "madde ve ozellik", "adezyon", "kohezyon")
        matchIf(BADGE_FIZ_TORK_DENGE, "tork", "denge", "kutle merkezi", "agirlik merkezi", "basit makine", "kaldirac", "makara")
        matchIf(BADGE_FIZ_ATOM_RADYOAKTIVITE, "atom fizigi", "radyoaktivite", "bohr", "buyuk patlama", "nukleer", "alfa", "beta", "gama")

        // ── Kimya (8) ──
        matchIf(BADGE_KIM_ATOM_PERIYODIK, "periyodik", "atom model", "modern atom", "orbital", "elektron dizilim", "iyonlasma enerjisi")
        matchIf(BADGE_KIM_MOL_HESAPLAMA, "mol", "kimyasal hesap", "avogadro", "temel kanun", "sabit oran", "katli oran")
        matchIf(BADGE_KIM_GAZLAR_COZELTILER, "gazlar", "ideal gaz", "kismi basinc", "sivi cozelti", "cozunurluk", "molarite", "koligatif")
        matchIf(BADGE_KIM_ENERJI_HIZ, "tepkimelerde enerji", "entalpi", "hess", "tepkime hiz", "aktiflesme enerjisi")
        matchIf(BADGE_KIM_DENGE_ASIT_BAZ, "kimyasal denge", "asit", "baz", "titrasyon", "tampon", "hidroliz", "kcc", "cozunme cokelme", "ph")
        matchIf(BADGE_KIM_ELEKTROKIMYA, "elektrokimya", "kimya ve elektrik", "redoks", "indirgenme", "yukseltgenme", "aktiflik", "pil", "elektroliz", "faraday")
        matchIf(BADGE_KIM_ORGANIK, "organik", "karbon kimyasi", "hidrokarbon", "alkan", "alken", "alkin", "alkol", "eter", "aldehit", "keton", "karboksilik", "ester", "aromatik", "izomer")
        matchIf(BADGE_KIM_ETKILESIMLER_MADDE, "turler arasi", "etkilesim", "iyonik bag", "kovalent bag", "hidrojen bagi", "maddenin halleri", "karisim")

        // ── Biyoloji (7) ──
        matchIf(BADGE_BIYO_HUCRE_CANLILAR, "hucre", "organel", "madde gecis", "siniflandirma", "canlilarin ortak", "inorganik", "enzim")
        matchIf(BADGE_BIYO_INSAN_FIZYOLOJISI, "insan fizyolojisi", "sindirim", "dolasim", "solunum sistemi", "bosaltim", "uriner", "destek ve hareket", "bagisiklik", "ureme sistemi")
        matchIf(BADGE_BIYO_ENDOKRIN_SINIR, "sinir sistemi", "endokrin", "hormon", "duyu organ", "impuls", "noron")
        matchIf(BADGE_BIYO_KALITIM_EVRIM, "kalitim", "genetik", "mitoz", "mayoz", "hucre bolunme", "mendel", "soyagaci", "kan grubu")
        matchIf(BADGE_BIYO_GENDEN_PROTEINE, "genden proteine", "nukleik asit", "dna", "rna", "protein sentezi", "transkripsiyon", "translasyon", "biyoteknoloji", "kodon")
        matchIf(BADGE_BIYO_BITKI, "bitki biyolojisi", "bitkisel doku", "bitkisel hormon", "tropizma", "ksilem", "floem", "stoma")
        matchIf(BADGE_BIYO_EKOLOJI_ENERJI, "ekoloji", "ekosistem", "komunite", "populasyon", "fotosentez", "kemosentez", "hucresel solunum", "fermantasyon", "atp", "besin zinciri")

        // ── Türkçe & Edebiyat (6) ──
        matchIf(BADGE_TUR_PARAGRAF_ANLAM, "paragraf", "ana dusunce", "yardimci dusunce", "anlatim bicim", "dusunceyi gelistirme")
        matchIf(BADGE_TUR_SOZCUK_CUMLE, "sozcukte anlam", "cumlede anlam", "deyim", "atasozu", "cumle yorum")
        matchIf(BADGE_TUR_DIL_BILGISI, "dil bilgisi", "ses bilgisi", "sozcukte yapi", "isim", "sifat", "zamir", "zarf", "edat", "baglac", "fiil", "fiilimsi", "cumlenin oge", "cumle turleri", "anlatim bozuk")
        matchIf(BADGE_TUR_YAZIM_NOKTALAMA, "yazim", "imla", "noktalama")
        matchIf(BADGE_EDB_SIIR_DONEMLER, "siir", "divan edebiyat", "halk edebiyat", "tanzimat", "servetifunun", "fecriati", "milli edebiyat", "soz sanat", "edebi sanat")
        matchIf(BADGE_EDB_ROMAN_HIKAYE, "roman", "hikaye", "oyku", "tiyatro", "cumhuriyet donemi", "edebi akim", "yazar eser")

        // ── Sosyal & Dil (5) ──
        matchIf(BADGE_TAR_OSMANLI_ISLAM, "osmanli", "ilk turk", "islamiyet", "selcuklu", "beylik", "orta cag", "ilk cag", "tarih ve zaman")
        matchIf(BADGE_TAR_INKILAP_CAGDAS, "inkilap", "kurtulus savasi", "milli mucadele", "ataturk", "cagdas turk", "dunya savasi", "soguk savas", "lozan")
        matchIf(BADGE_COG_DOGAL_TURKIYE, "cografya", "iklim", "harita", "nufus", "yerlesme", "ic kuvvet", "dis kuvvet", "dogal afet", "bolge", "ekonomik faaliyet")
        matchIf(BADGE_FEL_FELSEFE_MANTIK, "felsefe", "psikoloji", "sosyoloji", "mantik", "epistemoloji", "ontoloji", "etik", "estetik")
        matchIf(BADGE_DIL_INGILIZCE_DIN, "ingilizce", "ydt", "yabanci dil", "grammar", "vocabulary", "reading", "din kulturu", "ahlak bilgisi", "ibadet", "vahiy")

        val mainSubjectId = subjectBadgeIdFor(subject)
            ?: subjectBadgeIdFor(combined)
            ?: matchedTopicIds.firstOrNull()?.let { BADGE_MAP[it]?.category }?.let { mainSubjectBadgeForCategory(it) }

        val result = mutableListOf<String>()
        result.addAll(matchedTopicIds)
        if (mainSubjectId != null) {
            result.add(mainSubjectId)
        }
        return result.distinct()
    }

    fun currentIsoWeekKey(nowMillis: Long = System.currentTimeMillis()): String {
        val cal = Calendar.getInstance(Locale.US).apply {
            firstDayOfWeek = Calendar.MONDAY
            minimalDaysInFirstWeek = 4
            timeInMillis = nowMillis
        }
        val week = cal.get(Calendar.WEEK_OF_YEAR)
        val month = cal.get(Calendar.MONTH)
        var year = cal.get(Calendar.YEAR)
        if (month == Calendar.JANUARY && week >= 52) {
            year -= 1
        } else if (month == Calendar.DECEMBER && week == 1) {
            year += 1
        }
        return String.format(Locale.US, "%04d-W%02d", year, week)
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getShowcaseBadgeIds(context: Context): List<String> {
        val raw = prefs(context).getString(KEY_SHOWCASE_IDS, "").orEmpty()
        if (raw.isBlank()) return emptyList()
        return raw.split(",")
            .map { it.trim().substringBefore(":") }
            .filter { it.isNotBlank() && BADGE_MAP.containsKey(it) }
            .distinct()
            .take(MAX_SHOWCASE_BADGES)
    }

    fun saveShowcaseBadgeIds(context: Context, badgeIds: List<String>): List<String> {
        val cleaned = badgeIds
            .map { it.trim().substringBefore(":") }
            .filter { it.isNotBlank() && BADGE_MAP.containsKey(it) }
            .distinct()
            .take(MAX_SHOWCASE_BADGES)
        prefs(context).edit()
            .putString(KEY_SHOWCASE_IDS, cleaned.joinToString(","))
            .apply()
        return cleaned
    }

    fun getUnlockedBadgeIds(context: Context): Set<String> {
        val stored = prefs(context).getStringSet(KEY_UNLOCKED_IDS, emptySet()).orEmpty()
        val fromProgress = getBadgeProgressCounts(context)
            .filter { (id, count) ->
                val def = BADGE_MAP[id]
                def != null && count >= (def.thresholds.firstOrNull() ?: 1)
            }
            .keys
        return (stored.map { it.substringBefore(":") }.filter { BADGE_MAP.containsKey(it) } + fromProgress).toSet()
    }

    fun getBadgeProgressCounts(context: Context): Map<String, Int> {
        val raw = prefs(context).getString(KEY_BADGE_PROGRESS, "").orEmpty()
        return parseCountMap(raw).filterKeys { BADGE_MAP.containsKey(it) }
    }

    private fun saveBadgeProgressCounts(context: Context, map: Map<String, Int>) {
        val clean = map.filter { (k, v) -> BADGE_MAP.containsKey(k) && v > 0 }
        prefs(context).edit()
            .putString(KEY_BADGE_PROGRESS, serializeCountMap(clean))
            .apply()
    }

    fun parseRemoteProgressMap(raw: Any?): Map<String, Int> {
        val map = raw as? Map<*, *> ?: return emptyMap()
        val result = mutableMapOf<String, Int>()
        map.forEach { (k, v) ->
            val key = k?.toString()?.trim().orEmpty()
            val count = (v as? Number)?.toInt() ?: v?.toString()?.toIntOrNull()
            if (BADGE_MAP.containsKey(key) && count != null && count > 0) {
                result[key] = count
            }
        }
        return result
    }

    fun mergeBadgeProgress(context: Context, remote: Map<String, Int>): Map<String, Int> {
        val local = getBadgeProgressCounts(context).toMutableMap()
        val unlockedNow = getUnlockedBadgeIds(context).toMutableSet()
        remote.forEach { (id, remoteCount) ->
            if (!BADGE_MAP.containsKey(id)) return@forEach
            val nextCount = maxOf(local[id] ?: 0, remoteCount)
            if (nextCount > 0) {
                local[id] = nextCount
                val firstThreshold = BADGE_MAP[id]?.thresholds?.firstOrNull() ?: 1
                if (nextCount >= firstThreshold) {
                    unlockedNow.add(id)
                }
            }
        }
        saveBadgeProgressCounts(context, local)
        prefs(context).edit().putStringSet(KEY_UNLOCKED_IDS, unlockedNow).apply()
        autoPopulateShowcaseIfRoom(context, unlockedNow)
        return local
    }

    fun incrementBadgeProgressLocally(
        context: Context,
        badgeId: String,
        delta: Int = 1
    ): Map<String, Int> {
        val def = BADGE_MAP[badgeId] ?: return getBadgeProgressCounts(context)
        val currentMap = getBadgeProgressCounts(context).toMutableMap()
        val nextCount = ((currentMap[badgeId] ?: 0) + delta).coerceAtLeast(0)
        currentMap[badgeId] = nextCount
        saveBadgeProgressCounts(context, currentMap)
        if (nextCount >= (def.thresholds.firstOrNull() ?: 1)) {
            unlockBadge(context, badgeId)
        }
        return currentMap
    }

    fun unlockBadge(context: Context, badgeId: String): Set<String> {
        val cleanId = badgeId.trim().substringBefore(":")
        val def = BADGE_MAP[cleanId] ?: return getUnlockedBadgeIds(context)
        val current = getUnlockedBadgeIds(context).toMutableSet()
        val added = current.add(cleanId)
        val counts = getBadgeProgressCounts(context).toMutableMap()
        val minThresh = def.thresholds.firstOrNull() ?: 1
        if ((counts[cleanId] ?: 0) < minThresh) {
            counts[cleanId] = minThresh
            saveBadgeProgressCounts(context, counts)
        }
        if (added) {
            prefs(context).edit().putStringSet(KEY_UNLOCKED_IDS, current).apply()
            autoPopulateShowcaseIfRoom(context, listOf(cleanId))
        }
        return current
    }

    fun mergeUnlockedBadges(context: Context, remoteUnlocked: Collection<String>): Set<String> {
        val cleanRemote = remoteUnlocked.map { it.trim().substringBefore(":") }.filter { BADGE_MAP.containsKey(it) }
        val merged = (getUnlockedBadgeIds(context) + cleanRemote).toSet()
        prefs(context).edit().putStringSet(KEY_UNLOCKED_IDS, merged).apply()
        return merged
    }

    private fun autoPopulateShowcaseIfRoom(context: Context, candidateIds: Collection<String>) {
        val currentShowcase = getShowcaseBadgeIds(context).toMutableList()
        var changed = false
        for (id in candidateIds) {
            if (currentShowcase.size >= MAX_SHOWCASE_BADGES) break
            if (BADGE_MAP.containsKey(id) && !currentShowcase.contains(id)) {
                currentShowcase.add(id)
                changed = true
            }
        }
        if (changed) {
            saveShowcaseBadgeIds(context, currentShowcase)
        }
    }

    fun getMentorPoints(context: Context): Int =
        prefs(context).getInt(KEY_MENTOR_POINTS, 0).coerceAtLeast(0)

    fun saveMentorPoints(context: Context, points: Int): Int {
        val safe = points.coerceAtLeast(0)
        prefs(context).edit().putInt(KEY_MENTOR_POINTS, safe).apply()
        return safe
    }

    fun addMentorPoints(context: Context, delta: Int): Int {
        val next = (getMentorPoints(context) + delta).coerceAtLeast(0)
        prefs(context).edit().putInt(KEY_MENTOR_POINTS, next).apply()
        return next
    }

    fun getWeeklyBonusSeconds(
        context: Context,
        weekKey: String = currentIsoWeekKey()
    ): Long = prefs(context).getLong("$KEY_WEEKLY_BONUS_PREFIX$weekKey", 0L).coerceAtLeast(0L)

    fun saveWeeklyBonusSeconds(
        context: Context,
        seconds: Long,
        weekKey: String = currentIsoWeekKey()
    ): Long {
        val safe = seconds.coerceAtLeast(0L)
        prefs(context).edit().putLong("$KEY_WEEKLY_BONUS_PREFIX$weekKey", safe).apply()
        return safe
    }

    fun addWeeklyBonusSeconds(
        context: Context,
        deltaSeconds: Long,
        weekKey: String = currentIsoWeekKey()
    ): Long {
        val next = (getWeeklyBonusSeconds(context, weekKey) + deltaSeconds).coerceAtLeast(0L)
        prefs(context).edit().putLong("$KEY_WEEKLY_BONUS_PREFIX$weekKey", next).apply()
        return next
    }

    fun parseWeeklyBonusForWeek(rawField: Any?, weekKey: String): Long {
        val map = rawField as? Map<*, *> ?: return 0L
        return ((map[weekKey] as? Number)?.toLong() ?: map[weekKey]?.toString()?.toLongOrNull() ?: 0L)
            .coerceAtLeast(0L)
    }

    fun recordQuestionSharedLocally(
        context: Context,
        @Suppress("UNUSED_PARAMETER") createdAtMillis: Long = System.currentTimeMillis()
    ): Set<String> {
        incrementBadgeProgressLocally(context, BADGE_ILK_SORU, 1)
        incrementBadgeProgressLocally(context, BADGE_SORU_CANAVARI, 1)
        incrementBadgeProgressLocally(context, BADGE_MEGALODON_ONDERI, 1)
        return getUnlockedBadgeIds(context)
    }

    /**
     * Records a correctly solved question for a given [subject] and [topic] (e.g. from ErrorBank FSRS
     * review with GOOD/EASY rating), incrementing all matching sub-topic and main subject badges,
     * and syncing updated progress to Firestore when signed in.
     */
    fun recordTopicQuestionSolvedLocally(
        context: Context,
        subject: String,
        topic: String,
        syncToCloudIfLoggedIn: Boolean = true
    ): Set<String> {
        val matchedIds = topicAndSubjectBadgeIdsFor(subject, topic)
        matchedIds.forEach { badgeId ->
            incrementBadgeProgressLocally(context, badgeId, 1)
        }
        incrementBadgeProgressLocally(context, BADGE_HATA_BANKASI_USTASI, 1)
        incrementBadgeProgressLocally(context, BADGE_ODAK_SAMPIYONU, 1)

        if (syncToCloudIfLoggedIn) {
            val uid = runCatching { GoogleServicesHelper.getCurrentUser(context = context)?.uid }.getOrNull()
            if (!uid.isNullOrBlank() && uid != "local_student" && uid != "student_default") {
                syncShowcaseAndBadgesToCloud(context, uid)
            }
        }
        return getUnlockedBadgeIds(context)
    }

    fun recordApprovedSolutionLocally(
        context: Context,
        subject: String,
        solveElapsedMs: Long,
        @Suppress("UNUSED_PARAMETER") createdAtMillis: Long = System.currentTimeMillis(),
        topic: String = ""
    ): Set<String> {
        incrementBadgeProgressLocally(context, BADGE_ILK_COZUM, 1)
        incrementBadgeProgressLocally(context, BADGE_SORU_AVCISI, 1)
        incrementBadgeProgressLocally(context, BADGE_SERI_MENTOR, 1)
        incrementBadgeProgressLocally(context, BADGE_MEGALODON_ONDERI, 1)

        if (solveElapsedMs in 0L..(15L * 60L * 1_000L)) {
            incrementBadgeProgressLocally(context, BADGE_HIZLI_KOPEKBALIGI, 1)
        }
        if (solveElapsedMs in (60L * 60L * 1_000L)..(105L * 60L * 1_000L)) {
            incrementBadgeProgressLocally(context, BADGE_CAN_SIMIDI, 1)
        }

        val matchedTopicAndSubjectIds = topicAndSubjectBadgeIdsFor(subject, topic)
        if (matchedTopicAndSubjectIds.isNotEmpty()) {
            matchedTopicAndSubjectIds.forEach { badgeId ->
                incrementBadgeProgressLocally(context, badgeId, 1)
            }
        } else {
            subjectBadgeIdFor(subject)?.let { subjBadgeId ->
                incrementBadgeProgressLocally(context, subjBadgeId, 1)
            }
        }
        return getUnlockedBadgeIds(context)
    }

    fun evaluateAutomaticUnlocks(
        context: Context,
        approvedSolutions: Int,
        badgeProgress: Map<String, Int> = emptyMap()
    ): Set<String> {
        if (badgeProgress.isNotEmpty()) {
            mergeBadgeProgress(context, badgeProgress)
        }
        if (approvedSolutions > 0) {
            val current = getBadgeProgressCounts(context).toMutableMap()
            var changed = false
            if ((current[BADGE_ILK_COZUM] ?: 0) < approvedSolutions) {
                current[BADGE_ILK_COZUM] = approvedSolutions
                changed = true
            }
            if ((current[BADGE_SORU_AVCISI] ?: 0) < approvedSolutions) {
                current[BADGE_SORU_AVCISI] = approvedSolutions
                changed = true
            }
            if (changed) {
                saveBadgeProgressCounts(context, current)
                mergeBadgeProgress(context, current)
            }
        }
        return getUnlockedBadgeIds(context)
    }

    private val badgeSyncScope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO
    )
    private var pendingBadgeSyncJob: kotlinx.coroutines.Job? = null
    @Volatile
    private var lastBadgePayloadHashByUid: Pair<String, String>? = null

    fun syncShowcaseAndBadgesToCloud(
        context: Context,
        uid: String,
        showcaseIds: List<String> = getShowcaseBadgeIds(context),
        unlockedIds: Set<String> = getUnlockedBadgeIds(context),
        mentorPoints: Int = getMentorPoints(context),
        badgeProgress: Map<String, Int> = getBadgeProgressCounts(context),
        weeklyBonusSeconds: Long = getWeeklyBonusSeconds(context),
        weekKey: String = currentIsoWeekKey(),
        forceImmediate: Boolean = false
    ) {
        if (uid.isBlank() || uid == "local_student" || uid == "student_default") return
        val appCtx = context.applicationContext
        val cleanShowcase = showcaseIds
            .map { it.trim().substringBefore(":") }
            .filter { BADGE_MAP.containsKey(it) }
            .distinct()
            .take(MAX_SHOWCASE_BADGES)
        val cleanUnlocked = unlockedIds
            .map { it.trim().substringBefore(":") }
            .filter { BADGE_MAP.containsKey(it) }
            .distinct()
            .sorted()
        val cleanProgress = badgeProgress
            .filter { (k, v) -> BADGE_MAP.containsKey(k) && v > 0 }
            .toSortedMap()
        val safeBonus = weeklyBonusSeconds.coerceAtLeast(0L)

        val payloadSignature = "$cleanShowcase|$cleanUnlocked|$mentorPoints|$cleanProgress|$weekKey:$safeBonus"
        val prev = lastBadgePayloadHashByUid
        if (!forceImmediate && prev != null && prev.first == uid && prev.second == payloadSignature) {
            return
        }

        pendingBadgeSyncJob?.cancel()
        val delayMs = if (forceImmediate) 0L else 8_000L
        pendingBadgeSyncJob = badgeSyncScope.launch {
            if (delayMs > 0L) {
                kotlinx.coroutines.delay(delayMs)
            }
            val db = GoogleServicesHelper.getFirebaseFirestore(appCtx) ?: return@launch
            val studentUpdate = hashMapOf<String, Any>(
                "showcaseBadges" to cleanShowcase,
                "showcaseBadgeIds" to cleanShowcase,
                "unlockedBadges" to cleanUnlocked,
                "badgeProgress" to cleanProgress,
                "mentorPoints" to mentorPoints,
                "weeklyBonusByWeek" to mapOf(weekKey to safeBonus)
            )
            val studentOk = runCatching {
                db.collection("students").document(uid)
                    .set(studentUpdate, SetOptions.merge())
                    .await()
                true
            }.onFailure {
                Log.w("SharkBadgeCatalog", "Failed syncing badges to students/$uid: ${it.message}")
            }.getOrDefault(false)

            val leagueUpdate = hashMapOf<String, Any>(
                "showcaseBadges" to cleanShowcase,
                "showcaseBadgeIds" to cleanShowcase,
                "badgeProgress" to cleanProgress,
                "mentorPoints" to mentorPoints,
                "weeklyBonusByWeek" to mapOf(weekKey to safeBonus)
            )
            runCatching {
                db.collection("institutions").document("GLOBAL_INDIVIDUAL")
                    .collection("weeklyLeague").document(uid)
                    .set(leagueUpdate, SetOptions.merge())
                    .await()
            }
            if (studentOk) {
                lastBadgePayloadHashByUid = uid to payloadSignature
            }
        }
    }

    // ─── Moderation: Block & Report Helpers ──────────────────────────────────

    fun getBlockedUserIds(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_BLOCKED_UIDS, emptySet()).orEmpty()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toSet()

    fun getBlockedUsersMap(context: Context): Map<String, String> {
        val raw = prefs(context).getString(KEY_BLOCKED_USER_NAMES_JSON, "").orEmpty()
        val parsed = runCatching {
            if (raw.isBlank()) {
                emptyMap()
            } else {
                val obj = org.json.JSONObject(raw)
                buildMap {
                    val keys = obj.keys()
                    while (keys.hasNext()) {
                        val k = keys.next().trim()
                        val v = obj.optString(k, "").trim()
                        if (k.isNotBlank() && v.isNotBlank()) put(k, v)
                    }
                }
            }
        }.getOrDefault(emptyMap())
        return getBlockedUserIds(context).associateWith { uid -> parsed[uid].orEmpty() }
    }

    fun saveBlockedUserName(context: Context, uid: String, name: String) {
        val cleanUid = uid.trim()
        val cleanName = name.trim()
        if (cleanUid.isBlank()) return
        val existing = runCatching {
            val raw = prefs(context).getString(KEY_BLOCKED_USER_NAMES_JSON, "").orEmpty()
            if (raw.isBlank()) org.json.JSONObject() else org.json.JSONObject(raw)
        }.getOrElse { org.json.JSONObject() }
        if (cleanName.isNotBlank()) {
            existing.put(cleanUid, cleanName)
        } else {
            existing.remove(cleanUid)
        }
        prefs(context).edit().putString(KEY_BLOCKED_USER_NAMES_JSON, existing.toString()).apply()
    }

    fun mergeBlockedUserIds(context: Context, remoteBlocked: Collection<String>): Set<String> {
        val merged = (getBlockedUserIds(context) + remoteBlocked.map { it.trim() }.filter { it.isNotBlank() }).toSet()
        prefs(context).edit().putStringSet(KEY_BLOCKED_UIDS, merged).apply()
        return merged
    }

    suspend fun setUserBlocked(
        context: Context,
        myUid: String,
        targetUid: String,
        blocked: Boolean,
        targetName: String = ""
    ): Set<String> {
        val cleanTarget = targetUid.trim()
        if (cleanTarget.isBlank() || cleanTarget == myUid) return getBlockedUserIds(context)
        val updated = getBlockedUserIds(context).toMutableSet().apply {
            if (blocked) add(cleanTarget) else remove(cleanTarget)
        }
        prefs(context).edit().putStringSet(KEY_BLOCKED_UIDS, updated).apply()
        if (blocked && targetName.isNotBlank()) {
            saveBlockedUserName(context, cleanTarget, targetName)
        } else if (!blocked) {
            saveBlockedUserName(context, cleanTarget, "")
        }

        val db = GoogleServicesHelper.getFirebaseFirestore(context)
        if (db != null && myUid.isNotBlank() && myUid != "local_student" && myUid != "student_default") {
            runCatching {
                val fieldUpdate = if (blocked) {
                    FieldValue.arrayUnion(cleanTarget)
                } else {
                    FieldValue.arrayRemove(cleanTarget)
                }
                db.collection("students").document(myUid)
                    .set(mapOf("blockedUserIds" to fieldUpdate), SetOptions.merge())
                    .await()

                val blockDocRef = db.collection("user_blocks")
                    .document(myUid)
                    .collection("blocked")
                    .document(cleanTarget)
                if (blocked) {
                    val now = System.currentTimeMillis()
                    val blockPayload = hashMapOf<String, Any>(
                        "blockerUid" to myUid,
                        "blockedUid" to cleanTarget,
                        "createdAtMillis" to now,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                    if (targetName.isNotBlank()) {
                        blockPayload["blockedName"] = targetName.trim()
                    }
                    blockDocRef.set(blockPayload, SetOptions.merge()).await()
                } else {
                    blockDocRef.delete().await()
                }
            }.onFailure {
                Log.w("SharkBadgeCatalog", "Blocked users cloud sync warning: ${it.message}")
            }
        }
        return updated
    }

    suspend fun submitUserReport(
        context: Context,
        reporterUid: String,
        reporterName: String,
        targetUid: String,
        targetName: String,
        reason: String,
        details: String,
        source: String
    ): Boolean {
        val db = GoogleServicesHelper.getFirebaseFirestore(context) ?: return false
        if (reporterUid.isBlank() || reporterUid == "local_student" || reporterUid == "student_default") {
            return false
        }
        val cleanTarget = targetUid.trim()
        if (cleanTarget.isBlank()) return false
        return runCatching {
            val now = System.currentTimeMillis()
            val payload = hashMapOf<String, Any>(
                "reporterUid" to reporterUid,
                "reporterName" to reporterName.ifBlank { "Öğrenci" },
                "reportedUid" to cleanTarget,
                "reportedName" to targetName.ifBlank { "Öğrenci" },
                "reason" to reason.ifBlank { "Uygunsuz davranış" },
                "details" to details.trim(),
                "source" to source,
                "status" to "OPEN",
                "createdAtMillis" to now,
                "createdAt" to FieldValue.serverTimestamp()
            )
            db.collection("moderation_reports").add(payload).await()
            true
        }.getOrElse {
            Log.w("SharkBadgeCatalog", "submitUserReport failed: ${it.message}")
            false
        }
    }

    private fun parseCountMap(raw: String): Map<String, Int> {
        if (raw.isBlank()) return emptyMap()
        return raw.split(";").mapNotNull { entry ->
            val parts = entry.split("=")
            if (parts.size == 2) {
                val key = parts[0].trim()
                val count = parts[1].trim().toIntOrNull()
                if (key.isNotBlank() && count != null) key to count else null
            } else null
        }.toMap()
    }

    private fun serializeCountMap(map: Map<String, Int>): String =
        map.entries.joinToString(";") { "${it.key}=${it.value}" }
}

@Composable
fun SharkBadgeChip(
    badge: SharkBadgeDef,
    tier: BadgeTier? = null,
    compact: Boolean = false,
    modifier: Modifier = Modifier
) {
    val activeTier = tier ?: BadgeTier.BRONZE
    val tierColor = Color(activeTier.primaryColorHex)
    val startColor = Color(badge.primaryColorHex)
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(if (compact) 10.dp else 12.dp),
        color = startColor.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, tierColor.copy(alpha = 0.52f))
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = if (compact) 7.dp else 10.dp,
                vertical = if (compact) 3.dp else 5.dp
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 6.dp)
        ) {
            SharkBadgeSymbolIcon(
                badge = badge,
                size = if (compact) 18.dp else 22.dp,
                iconSize = if (compact) 11.dp else 13.dp,
                tier = activeTier
            )
            Text(
                text = if (compact) badge.title else "${badge.title} (${activeTier.shortLabel})",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = if (compact) 10.sp else 11.sp
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SharkBadgeShowcaseRow(
    badgeIds: List<String>,
    compact: Boolean = false,
    badgeProgress: Map<String, Int> = emptyMap(),
    modifier: Modifier = Modifier
) {
    val badges = badgeIds
        .mapNotNull { SharkBadgeCatalog.findById(it) }
        .distinctBy { it.id }
        .take(SharkBadgeCatalog.MAX_SHOWCASE_BADGES)
    if (badges.isEmpty()) return
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 6.dp),
        verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 6.dp)
    ) {
        badges.forEach { badge ->
            val progressInfo = badge.progressFor(
                rawCount = badgeProgress[badge.id] ?: 0,
                isLegacyUnlocked = true
            )
            SharkBadgeChip(
                badge = badge,
                tier = progressInfo.unlockedTier ?: BadgeTier.BRONZE,
                compact = compact
            )
        }
    }
}
