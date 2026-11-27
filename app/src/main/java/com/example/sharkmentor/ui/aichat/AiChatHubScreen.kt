package com.example.sharkmentor.ui.aichat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.sharkmentor.ui.screens.SharkAiScreen
import java.time.LocalTime

@Composable
fun AiChatHubScreen(
    modifier: Modifier = Modifier,
    viewModel: AiChatHubViewModel = viewModel(),
    onNavigateToPlans: (String) -> Unit = {}
) {
    SharkAiScreen(
        modifier = modifier,
        viewModel = viewModel,
        onNavigateToPlans = onNavigateToPlans
    )
}

@Composable
fun ActionChip(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    SuggestionChip(
        onClick = onClick,
        label = { Text(label, color = MaterialTheme.colorScheme.onSurface) },
        icon = { Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurface) },
        colors = SuggestionChipDefaults.suggestionChipColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
    )
}

@Composable
fun PersonalizedGreetingHeader(
    userName: String,
    selectedMode: AiPersona,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(context) {
        com.example.sharkmentor.core.ai.SharkTokenQuotaManager.ensureLoaded(context)
    }
    val quotaSnapshot by com.example.sharkmentor.core.ai.SharkTokenQuotaManager.snapshot.collectAsState()
    val isJarvisActive = quotaSnapshot.isUnlimitedUnlocked

    val firstName = remember(userName) {
        userName.trim().split(" ").firstOrNull()?.takeIf { it.isNotBlank() && !it.equals("Şampiyon", ignoreCase = true) }?.replaceFirstChar { it.uppercase() }
    }

    val currentHour = remember { LocalTime.now().hour }
    val (timeGreeting, invitationText) = remember(currentHour, firstName, selectedMode, isJarvisActive) {
        if (isJarvisActive) {
            val jarvisHeader = when (currentHour) {
                in 5..11 -> "Günaydın efendim. Hoş geldiniz."
                in 12..17 -> "Hoş geldiniz efendim, sistemler hazır."
                in 18..21 -> "İyi akşamlar efendim, sistemler hazır."
                else -> "İyi geceler efendim. Gece protokolü aktif."
            }
            val jarvisSub = when (selectedMode) {
                AiPersona.STUDY_PLANNER -> "Program ve etüt düzeninizi oluşturmak için hazırım efendim. Hangi derslerle başlayalım?"
                AiPersona.GOAL_SETTER -> "Hedef analizlerinizi ve net projeksiyonunuzu güncelleyebilirim efendim."
                AiPersona.DOPAMINE_COACH -> "Derin odak protokolünü başlatmaya hazırım efendim."
                AiPersona.ZEN_CALM -> "Zihninizi dinlendirmek için sakin bir nefes seansı hazırlayabilirim efendim."
                else -> "Bana doğrudan J.A.R.V.I.S. diye seslenebilirsiniz efendim. Soru, grafik veya plan — emrinizdeyim."
            }
            jarvisHeader to jarvisSub
        } else {
        val namePart = if (firstName != null) " $firstName" else ""
        when (currentHour) {
            in 6..11 -> {
                "Günaydın$namePart! 🌅" to when (selectedMode) {
                    AiPersona.STUDY_PLANNER -> "Güne zinde bir başlangıç yapalım. Bugünün ders ve etüt programını organize etmeye hazır mısın?"
                    AiPersona.GOAL_SETTER -> "Yeni bir günde hedeflerine bir adım daha yaklaşalım. Stratejik net ve sıralama hedeflerini gözden geçirelim mi?"
                    AiPersona.DOPAMINE_COACH -> "Sabah enerjini yüksek tut. Ertelemeyi bir kenara bırakıp bugünün ilk derin odak bloğunu başlatalım."
                    AiPersona.ZEN_CALM -> "Sakin ve berrak bir zihinle güne başlayalım. Derin bir nefes al, sınav kaygısını birlikte yönetelim."
                    else -> "Güne verimli başlamak ve hedeflerine ilerlemek için uygun bir zaman. Bugün hangi konuyu çalışalım?"
                }
            }
            in 12..17 -> {
                "Günün nasıl geçiyor$namePart? ⚡" to when (selectedMode) {
                    AiPersona.STUDY_PLANNER -> "Günün en yoğun çalışma saatlerindesin. Akşam etütlerini ve kalan ders hedeflerini planlayalım mı?"
                    AiPersona.GOAL_SETTER -> "Stratejik ilerlemen nasıl gidiyor? Deneme netlerini ve sıralama projeksiyonunu güncelleyelim."
                    AiPersona.DOPAMINE_COACH -> "Öğleden sonra odağını koru. Çalışma düzenini toparlayacak güçlü bir odak serisi kuralım."
                    AiPersona.ZEN_CALM -> "Kısa bir zihinsel nefes molası vermek ister misin? Zihnini tazeleyip çalışmana daha güçlü devam edelim."
                    else -> "Günün en verimli odaklanma saatlerindesin. Çözemediğin bir soru varsa görselini paylaş, adım adım inceleyelim."
                }
            }
            in 18..22 -> {
                "İyi akşamlar$namePart! 🎯" to when (selectedMode) {
                    AiPersona.STUDY_PLANNER -> "Akşam çalışma bloğunu en yüksek verimle tamamlayalım. Yarının planını da şimdiden kurgulayalım mı?"
                    AiPersona.GOAL_SETTER -> "Bugünkü çalışmalarının hedeflerine katkısını değerlendirelim. Net projeksiyonunu güncelleyelim mi?"
                    AiPersona.DOPAMINE_COACH -> "Günü güçlü bir disiplinle tamamlama zamanı. Son odak periyodunu verimli biçimde bitirelim."
                    AiPersona.ZEN_CALM -> "Günün yorgunluğunu geride bırak. Zihnini yatıştırıp sakin bir odaklanmaya geçelim."
                    else -> "Günü kaliteli bir odaklanmayla tamamlayalım. Takıldığın bir soru varsa birlikte inceleyebilir veya yarını planlayabiliriz."
                }
            }
            else -> {
                "Gece mesaisi mi$namePart? 🦉" to when (selectedMode) {
                    AiPersona.STUDY_PLANNER -> "Gece sessizliğinde plan yapmak zihni berraklaştırır. Yarın için net bir çalışma çizelgesi çıkaralım mı?"
                    AiPersona.GOAL_SETTER -> "Günün değerlendirmesini yapıp hedeflerini güncelleyelim mi? Sıralama simülasyonunu inceleyebiliriz."
                    AiPersona.DOPAMINE_COACH -> "Gecenin sessizliğini derin bir odaklanmayla değerlendir. Kısa ve verimli bir etüt sonrası dinlenmeye geçebilirsin."
                    AiPersona.ZEN_CALM -> "Uykudan önce zihnini rahatlatalım. Kaygıları kenara bırak ve sadece nefesine odaklan."
                    else -> "Sessiz çalışma ortamını verimli değerlendiriyorsun. Zorlandığın bir soru varsa adım adım birlikte çözelim."
                }
            }
        }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (isJarvisActive) {
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
            ) {
                Text(
                    text = "J.A.R.V.I.S. • ÇEVRİMİÇİ",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.1.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
        }
        Text(
            text = timeGreeting,
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = invitationText,
            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
            textAlign = TextAlign.Center
        )
        if (selectedMode == AiPersona.SHARK_COACH) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Özel araçlar için / yaz (/detayli, /simulasyon, /plan, /soru, /benzer, /hedef, /zen)",
                style = MaterialTheme.typography.labelSmall.copy(lineHeight = 17.sp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                textAlign = TextAlign.Center
            )
        }
    }
}

fun getFlashCardsForPersona(persona: AiPersona): List<QuickActionCard> {
    return when (persona) {
        AiPersona.STUDY_PLANNER -> listOf(
            QuickActionCard("p1", "Hızlı Plan Oluştur", "Ders programımı tara ve bu haftaya dengeli bir plan çıkar.", "Ders programımı tara ve bu haftaya hemen dengeli bir plan çıkar.", Icons.Default.PlayArrow, persona),
            QuickActionCard("p2", "Etüt Programı", "Ders bitimi sonrası için akşam saatlerine etüt ve soru çözümü planla.", "Ders bitimi 13:00 sonrası için akşam 22:30'a kadar yoğun etüt ve soru kampı planla.", Icons.Default.DateRange, persona),
            QuickActionCard("p3", "Hafta Sonu Planı", "Cumartesi ve Pazar günlerine eksik kapatma ve deneme odaklı soru hedefi dağıt.", "Cumartesi ve Pazar günlerine eksik kapatma ve deneme odaklı 300 soru hedefi dağıt.", Icons.Default.Star, persona),
            QuickActionCard("p4", "Eksik Kapatma Planı", "En çok hata yaptığım ilk 2 branşa odaklanan 4 günlük çalışma programı yaz.", "En çok hata yaptığım ilk 2 branşa odaklanan 4 günlük mikro çalışma programı yaz.", Icons.Default.Refresh, persona)
        )
        AiPersona.ZEN_CALM -> listOf(
            QuickActionCard("z1", "Stres Yönetimi", "Deneme öncesi stresi azaltacak zihin kontrol adımları ver.", "Deneme öncesi stresimi ve panik hissimi yatıştıracak zihin kontrol adımları ver.", Icons.Default.Star, persona),
            QuickActionCard("z2", "Odaklanma Teknikleri", "Ders çalışırken dikkatin dağılmasını önleyecek 3 pratik teknik öner.", "Ders çalışırken dikkatimin dağılmasını önleyecek 3 pratik teknik öner.", Icons.Default.PlayArrow, persona),
            QuickActionCard("z3", "Kısa Mola Rutini", "Etüt blokları arasına koyabileceğim 5 dakikalık dinlenme rutini sun.", "Zorlu etüt blokları arasına koyabileceğim 5 dakikalık zihin açıcı rutin sun.", Icons.Default.Refresh, persona),
            QuickActionCard("z4", "Süre Yönetimi", "Soruları yetiştirememe durumunda uygulanabilecek turlama tekniği nedir?", "Soruları yetiştiremeyeceğimi hissettiğim anda uygulayabileceğim taktik nedir?", Icons.Default.DateRange, persona)
        )
        AiPersona.GOAL_SETTER -> listOf(
            QuickActionCard("t1", "Sıralama Simülasyonu", "Hedeflediğim bölüm için mevcut netlerime göre gereken net dağılımını hesapla.", "Hedeflediğim üniversite ve bölüm için mevcut netlerime göre gereken net dağılımını hesapla.", Icons.Default.Star, persona),
            QuickActionCard("t2", "TYT-AYT Dengesi", "Mevcut netlerime göre TYT ile AYT arasındaki dengesizlikleri raporla.", "Mevcut netlerime göre TYT ile AYT arasındaki dengesizlikleri ve riskli dersleri raporla.", Icons.Default.DateRange, persona),
            QuickActionCard("t3", "Öncelikli Konular", "Sınava kalan süreye göre soru çıkma olasılığı en yüksek 3 kritik konuyu listele.", "Sınava kalan süreye göre soru çıkma olasılığı en yüksek 3 kritik konuyu listele.", Icons.Default.PlayArrow, persona),
            QuickActionCard("t4", "Net Açığı Analizi", "Son deneme verilerime göre hedefe ulaşmam için kapatmam gereken net açığını çıkar.", "Son deneme verilerime göre hedefe ulaşmam için kapatmam gereken net açığını çıkar.", Icons.Default.Refresh, persona)
        )
        AiPersona.DOPAMINE_COACH -> listOf(
            QuickActionCard("d1", "Hızlı Başlangıç", "Derse başlamayı kolaylaştıracak 5 dakikalık başlangıç görevi ver.", "İçimden ders çalışmak gelmiyor, 5 dakika kuralıyla beni derse başlat.", Icons.Default.PlayArrow, persona),
            QuickActionCard("d2", "Çalışma Serisi Takibi", "Bugünkü soru hedefimi tamamlamam için çalışma planı kurgula.", "Bugünkü soru hedefimi tamamlamam için beni motive et ve bir ödül kurgula.", Icons.Default.Star, persona),
            QuickActionCard("d3", "Dikkat Yönetimi", "Ders çalışırken sosyal medya ve bildirim dikkat dağınıklığını nasıl önlerim?", "Ders çalışırken sosyal medya dürtüsünü nasıl kırabilirim? Taktik ver.", Icons.Default.Refresh, persona),
            QuickActionCard("d4", "Günlük Öncelikler", "Bugünü verimli kapatmam için tamamlamam gereken en kritik 3 görevi belirle.", "Bugünü başarıyla kapatmam için yapmam gereken en kritik 3 görevi belirle.", Icons.Default.DateRange, persona)
        )
        else -> listOf(
            QuickActionCard("c1", "Kavram Kontrolü", "Zorlandığım bir branştan konuyu ölçecek 3 kritik soru sor.", "Zorlandığım bir branştan mantığımı ölçecek 3 kritik ve eleyici soru sor.", Icons.Default.Star, persona),
            QuickActionCard("c2", "Haftalık Değerlendirme", "Bu haftaki çalışma süremi analiz edip durum değerlendirmesi yap.", "Bu haftaki çalışma süremi ve lig puanımı analiz edip durum değerlendirmesi yap.", Icons.Default.DateRange, persona),
            QuickActionCard("c3", "Zaman Yönetimi", "Denemelerde zaman yönetimini nasıl optimize edebilirim?", "Denemelerde zaman yönetimini nasıl optimize edebilirim?", Icons.Default.PlayArrow, persona),
            QuickActionCard("c4", "Haftalık Strateji", "Kalan günler için en verimli çalışma stratejisini özetle.", "Kalan günler için en verimli çalışma stratejisini özetle.", Icons.Default.Refresh, persona)
        )
    }
}
