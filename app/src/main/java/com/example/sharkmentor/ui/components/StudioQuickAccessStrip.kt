package com.example.sharkmentor.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.sharkmentor.core.data.ThemePalette
import com.example.sharkmentor.core.timer.TimerPhase
import com.example.sharkmentor.data.local.SharkMentorDatabase
import com.example.sharkmentor.ui.navigation.Screen
import com.example.sharkmentor.ui.theme.SharkMentorTheme

enum class StudioToolIcon {
    GRAPHIC_SOLVE,
    SIMULATION_LAB,
    ERROR_BANK,
    PDF_LASSO,
    BOOKS,
    NET_WIZARD,
    PACE_KEEP,
    ZEN_ROOM,
    AI_CHAT,
    AI_GOAL,
    PLANS,
    HISTORY,
    LEAGUE,
    APP_BLOCKER,
}

data class StudioStripItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val route: String,
    val aiPrompt: String? = null,
    val icon: StudioToolIcon,
    val badgeCount: Int = 0,
)

enum class StudioShowcaseCategory(
    val id: String,
    val title: String,
    val subtitle: String,
) {
    AI_LAB(
        id = "ai_lab",
        title = "Yapay Zeka & Görsel Çözüm",
        subtitle = "Grafik çizimi, simülasyonlar ve Sokratik mentor"
    ),
    STUDIO_MEMORY(
        id = "studio_memory",
        title = "Not, PDF & Hafıza Stüdyosu",
        subtitle = "Kementle soru kesme, çizim ve FSRS aralıklı tekrar"
    ),
    PLANNING_ANALYTICS(
        id = "planning_analytics",
        title = "Kaynaklar, Plan & Net Analizi",
        subtitle = "Kitap koçu, net projeksiyonu ve çalışma takibi"
    ),
    DISCIPLINE_SOCIAL(
        id = "discipline_social",
        title = "Odak Disiplini, Zihin & Lig",
        subtitle = "Sınav temposu, nefes odası ve rekabet ligi"
    ),
}

data class StudioShowcaseModule(
    val id: String,
    val category: StudioShowcaseCategory,
    val title: String,
    val badge: String,
    val description: String,
    val route: String,
    val aiPrompt: String? = null,
    val icon: StudioToolIcon,
)

/**
 * Pure-JVM visibility rule: hides the quick-access strip automatically whenever
 * the user is actively focusing ([TimerPhase.FOCUSING]) for zero distractions.
 */
fun shouldShowStudioQuickAccessStrip(timerPhase: TimerPhase): Boolean =
    timerPhase != TimerPhase.FOCUSING

/**
 * Pure-JVM formatter for the live FSRS due-count badge on the Error Bank quick card.
 */
fun formatErrorBankDueSubtitle(dueCount: Int): String =
    if (dueCount > 0) "$dueCount tekrar" else "1-3-7 Hazır"

object StudioQuickAccessCatalog {

    const val PROMPT_DETAILED_VISUAL = "/detayli "
    const val PROMPT_SIMULATION = "/simulasyon "
    const val PROMPT_GOAL_ANALYSIS = "/hedef "
    const val ROUTE_ZEN_ROOM = "zen_room"
    const val ROUTE_APP_BLOCKER = "app_blocker"

    fun buildStripItems(dueErrorCount: Int): List<StudioStripItem> {
        val safeDueCount = dueErrorCount.coerceAtLeast(0)
        return listOf(
            StudioStripItem(
                id = "detailed_visual_solve",
                title = "Grafikli Çöz",
                subtitle = "/detayli • SVG",
                route = Screen.Shark.route,
                aiPrompt = PROMPT_DETAILED_VISUAL,
                icon = StudioToolIcon.GRAPHIC_SOLVE,
            ),
            StudioStripItem(
                id = "simulation_lab",
                title = "Simülasyon Lab",
                subtitle = "/simulasyon • Lab",
                route = Screen.Shark.route,
                aiPrompt = PROMPT_SIMULATION,
                icon = StudioToolIcon.SIMULATION_LAB,
            ),
            StudioStripItem(
                id = "error_bank",
                title = "Hata Bankası",
                subtitle = formatErrorBankDueSubtitle(safeDueCount),
                route = Screen.ErrorBank.route,
                aiPrompt = null,
                icon = StudioToolIcon.ERROR_BANK,
                badgeCount = safeDueCount,
            ),
            StudioStripItem(
                id = "pdf_lasso_ai",
                title = "PDF & Kement AI",
                subtitle = "Stüdyo & Notlar",
                route = Screen.Notes.route,
                aiPrompt = null,
                icon = StudioToolIcon.PDF_LASSO,
            ),
            StudioStripItem(
                id = "my_books",
                title = "Kitaplarım",
                subtitle = "Kitap Koçu & Kamp",
                route = Screen.Resources.route,
                aiPrompt = null,
                icon = StudioToolIcon.BOOKS,
            ),
            StudioStripItem(
                id = "net_wizard",
                title = "Net Sihirbazı",
                subtitle = "Analiz & Tahmin",
                route = Screen.DetailedAnalytics.route,
                aiPrompt = null,
                icon = StudioToolIcon.NET_WIZARD,
            ),
            StudioStripItem(
                id = "pace_keep",
                title = "PACE / KEEP",
                subtitle = "Sınav Sayacı",
                route = Screen.PaceKeep.route,
                aiPrompt = null,
                icon = StudioToolIcon.PACE_KEEP,
            ),
            StudioStripItem(
                id = "zen_room",
                title = "Zen Odası",
                subtitle = "Nefes & Ambiyans",
                route = ROUTE_ZEN_ROOM,
                aiPrompt = null,
                icon = StudioToolIcon.ZEN_ROOM,
            ),
        )
    }

    fun buildShowcaseModules(dueErrorCount: Int): List<StudioShowcaseModule> {
        val safeDueCount = dueErrorCount.coerceAtLeast(0)
        return listOf(
            // 1. Yapay Zeka & Görsel Çözüm
            StudioShowcaseModule(
                id = "showcase_detailed_visual",
                category = StudioShowcaseCategory.AI_LAB,
                title = "Grafikli AI Çözücü",
                badge = "/detayli",
                description = "Geometri şekilleri ve fonksiyon grafiklerini SVG ile çizerek adım adım çözer.",
                route = Screen.Shark.route,
                aiPrompt = PROMPT_DETAILED_VISUAL,
                icon = StudioToolIcon.GRAPHIC_SOLVE,
            ),
            StudioShowcaseModule(
                id = "showcase_simulation_lab",
                category = StudioShowcaseCategory.AI_LAB,
                title = "Simülasyon Lab",
                badge = "/simulasyon",
                description = "Fizik, Kimya, Biyoloji ve Tıp konuları için interaktif deney sahnesi oluşturur.",
                route = Screen.Shark.route,
                aiPrompt = PROMPT_SIMULATION,
                icon = StudioToolIcon.SIMULATION_LAB,
            ),
            StudioShowcaseModule(
                id = "showcase_socratic_ai",
                category = StudioShowcaseCategory.AI_LAB,
                title = "Sokratik SharkAI",
                badge = "Çoklu AI",
                description = "Görsel soru çözümü, ikiz ÖSYM sorusu üretimi (/benzer) ve akıllı konu sohbeti.",
                route = Screen.Shark.route,
                aiPrompt = null,
                icon = StudioToolIcon.AI_CHAT,
            ),
            StudioShowcaseModule(
                id = "showcase_ai_goal",
                category = StudioShowcaseCategory.AI_LAB,
                title = "AI Hedef & Sıralama",
                badge = "/hedef",
                description = "Mevcut netlerine göre ders bazlı stratejik net analizi ve sıralama projeksiyonu.",
                route = Screen.Shark.route,
                aiPrompt = PROMPT_GOAL_ANALYSIS,
                icon = StudioToolIcon.AI_GOAL,
            ),

            // 2. Not, PDF & Hafıza Stüdyosu
            StudioShowcaseModule(
                id = "showcase_pdf_lasso",
                category = StudioShowcaseCategory.STUDIO_MEMORY,
                title = "PDF & Kement Stüdyo",
                badge = "SharkStudio",
                description = "PDF ve notlar üzerinde stylusla çizim yap, kementle soru kırpıp AI'a çözdür.",
                route = Screen.Notes.route,
                aiPrompt = null,
                icon = StudioToolIcon.PDF_LASSO,
            ),
            StudioShowcaseModule(
                id = "showcase_error_bank",
                category = StudioShowcaseCategory.STUDIO_MEMORY,
                title = "Hata Bankası (FSRS)",
                badge = formatErrorBankDueSubtitle(safeDueCount),
                description = "1-3-7 gün bilimsel aralıklı tekrar motoruyla yanlış soruları kalıcı pekiştir.",
                route = Screen.ErrorBank.route,
                aiPrompt = null,
                icon = StudioToolIcon.ERROR_BANK,
            ),

            // 3. Kaynaklar, Plan & Net Analizi
            StudioShowcaseModule(
                id = "showcase_books_coach",
                category = StudioShowcaseCategory.PLANNING_ANALYTICS,
                title = "Kitaplarım & Kitap Koçu",
                badge = "Kütüphane",
                description = "Dinamik ders/tür filtreleme, kitap rol ataması ve kamp video çalışma stüdyosu.",
                route = Screen.Resources.route,
                aiPrompt = null,
                icon = StudioToolIcon.BOOKS,
            ),
            StudioShowcaseModule(
                id = "showcase_net_wizard",
                category = StudioShowcaseCategory.PLANNING_ANALYTICS,
                title = "Net Sihirbazı & Analiz",
                badge = "Canlı Grafik",
                description = "Haftalık odak dağılımı, ders yetkinlik puanları ve AI çalışma önerileri.",
                route = Screen.DetailedAnalytics.route,
                aiPrompt = null,
                icon = StudioToolIcon.NET_WIZARD,
            ),
            StudioShowcaseModule(
                id = "showcase_plans",
                category = StudioShowcaseCategory.PLANNING_ANALYTICS,
                title = "Planlar & AI Programcı",
                badge = "Takvim",
                description = "Günlük hedefler, esnek etüt blokları ve otomatik haftalık çalışma planı.",
                route = Screen.Dashboard.route,
                aiPrompt = null,
                icon = StudioToolIcon.PLANS,
            ),
            StudioShowcaseModule(
                id = "showcase_history",
                category = StudioShowcaseCategory.PLANNING_ANALYTICS,
                title = "Odak Geçmişi",
                badge = "Kayıtlar",
                description = "Tüm tamamlanan etüt seansları, AFK dikkat puanı ve süre geçmişi.",
                route = Screen.History.route,
                aiPrompt = null,
                icon = StudioToolIcon.HISTORY,
            ),

            // 4. Odak Disiplini, Zihin & Lig
            StudioShowcaseModule(
                id = "showcase_pace_keep",
                category = StudioShowcaseCategory.DISCIPLINE_SOCIAL,
                title = "PACE / KEEP Sayaç",
                badge = "Tempo",
                description = "Dokunsal geri bildirimli yüksek disiplin sınav ve soru hız sayacı.",
                route = Screen.PaceKeep.route,
                aiPrompt = null,
                icon = StudioToolIcon.PACE_KEEP,
            ),
            StudioShowcaseModule(
                id = "showcase_zen_room",
                category = StudioShowcaseCategory.DISCIPLINE_SOCIAL,
                title = "Zen Odası & Dopamin",
                badge = "4-7-8 Nefes",
                description = "Rehberli nefes egzersizleri, 11 doğa ambiyansı ve dopamin sıfırlama protokolü.",
                route = ROUTE_ZEN_ROOM,
                aiPrompt = null,
                icon = StudioToolIcon.ZEN_ROOM,
            ),
            StudioShowcaseModule(
                id = "showcase_shark_league",
                category = StudioShowcaseCategory.DISCIPLINE_SOCIAL,
                title = "Shark Ligi & Gruplar",
                badge = "Rekabet",
                description = "Seri puanı sıralaması, kodlu çalışma grupları ve arkadaşlarla soru paylaşımı.",
                route = Screen.SharkLeague.route,
                aiPrompt = null,
                icon = StudioToolIcon.LEAGUE,
            ),
            StudioShowcaseModule(
                id = "showcase_app_blocker",
                category = StudioShowcaseCategory.DISCIPLINE_SOCIAL,
                title = "Uygulama Engelleyici",
                badge = "Kalkan",
                description = "Odak bloğu sırasında dikkat dağıtan uygulamaları otomatik kilitler.",
                route = ROUTE_APP_BLOCKER,
                aiPrompt = null,
                icon = StudioToolIcon.APP_BLOCKER,
            ),
        )
    }

    fun groupShowcaseByCategory(dueErrorCount: Int): Map<StudioShowcaseCategory, List<StudioShowcaseModule>> {
        val all = buildShowcaseModules(dueErrorCount)
        return StudioShowcaseCategory.entries.associateWith { category ->
            all.filter { it.category == category }
        }
    }
}

private fun studioIconVector(icon: StudioToolIcon): ImageVector = when (icon) {
    StudioToolIcon.GRAPHIC_SOLVE -> Icons.Default.AutoFixHigh
    StudioToolIcon.SIMULATION_LAB -> Icons.Default.Science
    StudioToolIcon.ERROR_BANK -> Icons.Default.FitnessCenter
    StudioToolIcon.PDF_LASSO -> Icons.Default.Draw
    StudioToolIcon.BOOKS -> Icons.Default.LibraryBooks
    StudioToolIcon.NET_WIZARD -> Icons.Default.BarChart
    StudioToolIcon.PACE_KEEP -> Icons.Default.Speed
    StudioToolIcon.ZEN_ROOM -> Icons.Default.SelfImprovement
    StudioToolIcon.AI_CHAT -> Icons.Default.Psychology
    StudioToolIcon.AI_GOAL -> Icons.AutoMirrored.Filled.TrendingUp
    StudioToolIcon.PLANS -> Icons.Default.CalendarMonth
    StudioToolIcon.HISTORY -> Icons.Default.History
    StudioToolIcon.LEAGUE -> Icons.Default.EmojiEvents
    StudioToolIcon.APP_BLOCKER -> Icons.Default.Shield
}

private fun dispatchStudioAction(
    route: String,
    aiPrompt: String?,
    onNavigateToRoute: (String) -> Unit,
    onLaunchSharkAiWithPrompt: (String) -> Unit,
) {
    if (!aiPrompt.isNullOrBlank()) {
        PendingAiPromptBus.pendingPrompt.value = aiPrompt
        onLaunchSharkAiWithPrompt(aiPrompt)
    } else {
        onNavigateToRoute(route)
    }
}

/**
 * Compact horizontal "Shark Araçları" quick-access strip placed directly under
 * the Water Streak strip on the classic Focus Desk. Collects live FSRS due count
 * from Room [ErrorQuestionDao.getDueCountFlow] and offers a 2-column categorized
 * showcase ModalBottomSheet via the "Tümünü Gör" button.
 */
@Composable
fun StudioQuickAccessStrip(
    onNavigateToRoute: (String) -> Unit,
    onLaunchSharkAiWithPrompt: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val dueErrorCountFlow = remember(context) {
        SharkMentorDatabase.getDatabase(context)
            .errorQuestionDao()
            .getDueCountFlow()
    }
    val dueErrorCount by dueErrorCountFlow.collectAsStateWithLifecycle(initialValue = 0)

    StudioQuickAccessStripContent(
        dueErrorCount = dueErrorCount,
        onNavigateToRoute = onNavigateToRoute,
        onLaunchSharkAiWithPrompt = onLaunchSharkAiWithPrompt,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudioQuickAccessStripContent(
    dueErrorCount: Int,
    onNavigateToRoute: (String) -> Unit,
    onLaunchSharkAiWithPrompt: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val items = remember(dueErrorCount) {
        StudioQuickAccessCatalog.buildStripItems(dueErrorCount)
    }
    var showAllToolsSheet by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = colorScheme.primary,
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = "Shark Araçları",
                    style = MaterialTheme.typography.labelMedium,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.onSurfaceVariant
                )
            }

            Surface(
                onClick = { showAllToolsSheet = true },
                shape = RoundedCornerShape(999.dp),
                color = colorScheme.primary.copy(alpha = 0.12f),
                border = BorderStroke(0.8.dp, colorScheme.primary.copy(alpha = 0.30f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Apps,
                        contentDescription = null,
                        tint = colorScheme.primary,
                        modifier = Modifier.size(11.dp)
                    )
                    Text(
                        text = "Tümünü Gör",
                        fontSize = 10.5.sp,
                        lineHeight = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.primary
                    )
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = colorScheme.primary,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(5.dp))

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 2.dp)
        ) {
            items(
                items = items,
                key = { it.id }
            ) { item ->
                val isErrorDueHighlighted = item.id == "error_bank" && item.badgeCount > 0
                StudioQuickAccessCard(
                    item = item,
                    highlighted = isErrorDueHighlighted,
                    onClick = {
                        dispatchStudioAction(
                            route = item.route,
                            aiPrompt = item.aiPrompt,
                            onNavigateToRoute = onNavigateToRoute,
                            onLaunchSharkAiWithPrompt = onLaunchSharkAiWithPrompt
                        )
                    }
                )
            }
        }
    }

    if (showAllToolsSheet) {
        StudioAllToolsShowcaseSheet(
            dueErrorCount = dueErrorCount,
            onDismiss = { showAllToolsSheet = false },
            onSelectModule = { module ->
                showAllToolsSheet = false
                dispatchStudioAction(
                    route = module.route,
                    aiPrompt = module.aiPrompt,
                    onNavigateToRoute = onNavigateToRoute,
                    onLaunchSharkAiWithPrompt = onLaunchSharkAiWithPrompt
                )
            }
        )
    }
}

@Composable
private fun StudioQuickAccessCard(
    item: StudioStripItem,
    highlighted: Boolean,
    onClick: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val containerColor = if (highlighted) {
        colorScheme.primaryContainer.copy(alpha = 0.72f)
    } else {
        colorScheme.surfaceVariant.copy(alpha = 0.56f)
    }
    val borderColor = if (highlighted) {
        colorScheme.primary.copy(alpha = 0.45f)
    } else {
        colorScheme.outlineVariant.copy(alpha = 0.55f)
    }
    val iconContainerColor = if (highlighted) {
        colorScheme.primary
    } else {
        colorScheme.primary.copy(alpha = 0.14f)
    }
    val iconTint = if (highlighted) {
        colorScheme.onPrimary
    } else {
        colorScheme.primary
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier.heightIn(min = 44.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(iconContainerColor, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = studioIconVector(item.icon),
                    contentDescription = item.title,
                    tint = iconTint,
                    modifier = Modifier.size(15.dp)
                )
            }

            Column(
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = item.title,
                    fontSize = 11.5.sp,
                    lineHeight = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (highlighted) colorScheme.onPrimaryContainer else colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = item.subtitle,
                    fontSize = 10.sp,
                    lineHeight = 12.sp,
                    fontWeight = if (highlighted) FontWeight.Bold else FontWeight.Medium,
                    color = if (highlighted) colorScheme.primary else colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StudioAllToolsShowcaseSheet(
    dueErrorCount: Int,
    onDismiss: () -> Unit,
    onSelectModule: (StudioShowcaseModule) -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val groupedModules = remember(dueErrorCount) {
        StudioQuickAccessCatalog.groupShowcaseByCategory(dueErrorCount)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 18.dp)
                .padding(bottom = 20.dp)
        ) {
            // Showcase Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .background(colorScheme.primaryContainer, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "SharkMentor Süper Güçler",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Tüm çalışma modülleri • Hemen açmak için bir karta dokun",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.5.sp,
                            color = colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Kapat",
                        tint = colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.55f))
            Spacer(modifier = Modifier.height(12.dp))

            // Categorized 2-Column Showcase Grid
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                groupedModules.forEach { (category, modules) ->
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(3.dp)
                                    .height(18.dp)
                                    .background(colorScheme.primary, RoundedCornerShape(2.dp))
                            )
                            Column {
                                Text(
                                    text = category.title,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = colorScheme.onSurface
                                )
                                Text(
                                    text = category.subtitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 10.5.sp,
                                    color = colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        modules.chunked(2).forEach { rowModules ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(IntrinsicSize.Max),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                rowModules.forEach { module ->
                                    StudioShowcaseModuleCard(
                                        module = module,
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight(),
                                        onClick = { onSelectModule(module) }
                                    )
                                }
                                if (rowModules.size == 1) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
            }
        }
    }
}

@Composable
private fun StudioShowcaseModuleCard(
    module: StudioShowcaseModule,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = colorScheme.surfaceContainerHigh.copy(alpha = 0.72f),
        border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.55f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .background(colorScheme.primary.copy(alpha = 0.14f), RoundedCornerShape(9.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = studioIconVector(module.icon),
                            contentDescription = module.title,
                            tint = colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = colorScheme.primaryContainer.copy(alpha = 0.78f)
                    ) {
                        Text(
                            text = module.badge,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onPrimaryContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Text(
                    text = module.title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = module.description,
                    fontSize = 11.sp,
                    lineHeight = 14.5.sp,
                    color = colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "Aç",
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.primary
                )
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = colorScheme.primary,
                    modifier = Modifier.size(13.dp)
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun StudioQuickAccessStripPreview() {
    SharkMentorTheme(palette = ThemePalette.OCEAN) {
        Surface(color = MaterialTheme.colorScheme.background) {
            StudioQuickAccessStripContent(
                dueErrorCount = 4,
                onNavigateToRoute = {},
                onLaunchSharkAiWithPrompt = {},
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}
