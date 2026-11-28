package com.example.sharkmentor.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StickyNote2
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sharkmentor.core.dashboard.HomeDashboardEngine
import com.example.sharkmentor.core.dashboard.HomeDashboardSnapshot
import com.example.sharkmentor.core.dashboard.HomeLandingTab
import com.example.sharkmentor.ui.navigation.Screen

/**
 * Compact segmented switcher placed at the top of HomeScreen allowing instant 1-tap switching
 * between the Study OS Dashboard (`Genel Bakış`) and the dedicated Focus Desk (`Odak Masası`).
 */
@Composable
fun HomeLandingTabSwitcher(
    activeTab: HomeLandingTab,
    isTimerRunning: Boolean,
    activeTimerLabel: String,
    onSelectTab: (HomeLandingTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.55f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            HomeLandingTab.entries.forEach { tab ->
                val selected = tab == activeTab
                val icon = if (tab == HomeLandingTab.DASHBOARD) Icons.Default.Dashboard else Icons.Default.Timer
                val label = if (tab == HomeLandingTab.FOCUS_DESK && isTimerRunning && activeTimerLabel.isNotBlank()) {
                    "Odak • $activeTimerLabel"
                } else {
                    tab.label
                }
                Surface(
                    onClick = { onSelectTab(tab) },
                    shape = RoundedCornerShape(10.dp),
                    color = if (selected) colorScheme.primary else Color.Transparent,
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = if (selected) colorScheme.onPrimary else colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = label,
                            fontSize = 12.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = if (selected) colorScheme.onPrimary else colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

private data class DiscoveryToolPill(
    val label: String,
    val icon: ImageVector,
    val route: String
)

/**
 * Mark 76 Study OS Command Center Dashboard.
 * Surfaces SharkMentor's real superpowers (Visual AI Solver, Simulation Lab, FSRS Error Bank,
 * SharkStudio PDF & Lasso AI, Library Coaching, Plans, and hidden tools) on the opening screen
 * while keeping the Focus Timer & Music Deck 1 tap away.
 */
@Composable
fun HomeStudyOsDashboard(
    snapshot: HomeDashboardSnapshot,
    isTimerRunning: Boolean,
    activeTimerLabel: String,
    activeTimerSubject: String,
    musicPlayerEnabled: Boolean,
    isMusicPlaying: Boolean,
    onStartOrOpenFocus: () -> Unit,
    onEditDailyGoal: () -> Unit,
    onOpenStreakDialog: () -> Unit,
    onOpenMusicDesk: () -> Unit,
    onLaunchSharkAi: () -> Unit,
    onLaunchSharkAiWithPrompt: (String) -> Unit,
    onNavigateToRoute: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val animatedFocusProgress by animateFloatAsState(
        targetValue = snapshot.focusProgressFraction,
        animationSpec = tween(durationMillis = 650),
        label = "dashboard_focus_ring"
    )

    val discoveryTools = remember {
        listOf(
            DiscoveryToolPill("Detaylı Analiz", Icons.Default.BarChart, Screen.DetailedAnalytics.route),
            DiscoveryToolPill("Shark Lig", Icons.Default.Star, Screen.SharkLeague.route),
            DiscoveryToolPill("PACE / KEEP", Icons.Default.Speed, Screen.PaceKeep.route),
            DiscoveryToolPill("Zen Odası", Icons.Default.SelfImprovement, "zen_room"),
            DiscoveryToolPill("Ders Programı", Icons.Default.Event, Screen.Schedule.route),
            DiscoveryToolPill("Geçmiş", Icons.Default.History, Screen.History.route)
        )
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. HERO COMMAND CARD (Greeting + Live Focus Ring + Water Streak + 1-Tap Start Focus & Music)
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = colorScheme.surfaceContainer,
            border = BorderStroke(1.dp, colorScheme.primary.copy(alpha = 0.25f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                colorScheme.primaryContainer.copy(alpha = 0.42f),
                                colorScheme.surfaceContainer.copy(alpha = 0.96f)
                            )
                        )
                    )
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(
                                onClick = onOpenStreakDialog,
                                shape = RoundedCornerShape(999.dp),
                                color = colorScheme.primary.copy(alpha = 0.14f),
                                border = BorderStroke(1.dp, colorScheme.primary.copy(alpha = 0.35f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.WaterDrop,
                                        contentDescription = null,
                                        tint = colorScheme.primary,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        text = "${snapshot.streakDays} gün seri",
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colorScheme.primary
                                    )
                                }
                            }
                            if (isTimerRunning) {
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = colorScheme.tertiaryContainer
                                ) {
                                    Text(
                                        text = "CANLI • $activeTimerLabel",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colorScheme.onTertiaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }

                        Text(
                            text = snapshot.greetingTitle,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (isTimerRunning && activeTimerSubject.isNotBlank()) {
                                "Aktif oturum: $activeTimerSubject"
                            } else {
                                snapshot.greetingSubtitle
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Interactive Focus Progress Ring (taps open Daily Goal Config)
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onEditDailyGoal),
                        contentAlignment = Alignment.Center
                    ) {
                        val trackColor = colorScheme.outlineVariant.copy(alpha = 0.45f)
                        val progressColor = colorScheme.primary
                        Canvas(modifier = Modifier.fillMaxSize().padding(4.dp)) {
                            drawArc(
                                color = trackColor,
                                startAngle = -90f,
                                sweepAngle = 360f,
                                useCenter = false,
                                style = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Round)
                            )
                            drawArc(
                                color = progressColor,
                                startAngle = -90f,
                                sweepAngle = (360f * animatedFocusProgress).coerceAtLeast(if (snapshot.todayFocusMinutes > 0) 14f else 0f),
                                useCenter = false,
                                style = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Round)
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = HomeDashboardEngine.formatDurationCompact(snapshot.todayFocusMinutes),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = colorScheme.onSurface,
                                maxLines = 1
                            )
                            Text(
                                text = "/ ${HomeDashboardEngine.formatDurationCompact(snapshot.todayTargetMinutes)}",
                                fontSize = 9.sp,
                                color = colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }
                }

                // Hero Primary & Secondary Action Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onStartOrOpenFocus,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colorScheme.primary,
                            contentColor = colorScheme.onPrimary
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = if (isTimerRunning) Icons.Default.Timer else Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (isTimerRunning) "Aktif Sayaca Geç" else "Odağı Başlat",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }

                    if (musicPlayerEnabled) {
                        Surface(
                            onClick = onOpenMusicDesk,
                            shape = RoundedCornerShape(14.dp),
                            color = if (isMusicPlaying) colorScheme.secondaryContainer else colorScheme.surfaceContainerHigh,
                            border = BorderStroke(
                                1.dp,
                                if (isMusicPlaying) colorScheme.secondary.copy(alpha = 0.5f)
                                else colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MusicNote,
                                    contentDescription = "Müzik & SharkMix",
                                    tint = if (isMusicPlaying) colorScheme.onSecondaryContainer else colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = if (isMusicPlaying) "Çalıyor" else "Müzik",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isMusicPlaying) colorScheme.onSecondaryContainer else colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2. SHARK AI SPOTLIGHT CARD — Immediately showcases Visual Solver (/detayli), Simulation Lab & Planner!
        Surface(
            onClick = onLaunchSharkAi,
            shape = RoundedCornerShape(20.dp),
            color = colorScheme.primaryContainer.copy(alpha = 0.52f),
            border = BorderStroke(1.dp, colorScheme.primary.copy(alpha = 0.35f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = colorScheme.primary,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = colorScheme.onPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "SharkAI • Görsel Çözücü & Simülasyon",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Fonksiyon grafiği çizen çözücü, interaktif fizik-kimya labı ve otonom koç.",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.5.sp,
                            color = colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    AiQuickCommandPill(
                        label = "Grafikli Çöz",
                        badge = "/detayli",
                        onClick = { onLaunchSharkAiWithPrompt("/detayli ") },
                        modifier = Modifier.weight(1f)
                    )
                    AiQuickCommandPill(
                        label = "Simülasyon",
                        badge = "/simulasyon",
                        onClick = { onLaunchSharkAiWithPrompt("/simulasyon ") },
                        modifier = Modifier.weight(1f)
                    )
                    AiQuickCommandPill(
                        label = "Plan Kur",
                        badge = "/plan",
                        onClick = { onLaunchSharkAiWithPrompt("/plan ") },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // 3. 2x2 LIVE BENTO FEATURE GRID (Error Bank, SharkStudio, Library Coach, Today's Plan)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Max),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            DashboardBentoCard(
                icon = Icons.Default.FitnessCenter,
                badge = snapshot.errorCardBadge,
                title = snapshot.errorCardTitle,
                subtitle = snapshot.errorCardSubtitle,
                accentColor = if (snapshot.errorCardHighlighted) colorScheme.tertiary else colorScheme.primary,
                highlighted = snapshot.errorCardHighlighted,
                onClick = { onNavigateToRoute(Screen.ErrorBank.route) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
            DashboardBentoCard(
                icon = Icons.Default.StickyNote2,
                badge = snapshot.studioCardBadge,
                title = snapshot.studioCardTitle,
                subtitle = snapshot.studioCardSubtitle,
                accentColor = colorScheme.secondary,
                highlighted = false,
                onClick = { onNavigateToRoute(Screen.Notes.route) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Max),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            DashboardBentoCard(
                icon = Icons.Default.LibraryBooks,
                badge = snapshot.libraryCardBadge,
                title = snapshot.libraryCardTitle,
                subtitle = snapshot.libraryCardSubtitle,
                accentColor = colorScheme.primary,
                highlighted = false,
                progressFraction = snapshot.libraryProgressFraction,
                onClick = { onNavigateToRoute(Screen.Resources.route) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
            DashboardBentoCard(
                icon = Icons.Default.CalendarMonth,
                badge = snapshot.planCardBadge,
                title = snapshot.planCardTitle,
                subtitle = snapshot.planCardSubtitle,
                accentColor = colorScheme.tertiary,
                highlighted = false,
                onClick = { onNavigateToRoute(snapshot.planCardRoute) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
        }

        // 4. DISCOVERY STRIP — Surfacing the hidden gems in 1 clean scrollable row
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier.padding(horizontal = 2.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Bolt,
                    contentDescription = null,
                    tint = colorScheme.primary,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "Hızlı Araçlar & Modüller",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.onSurfaceVariant
                )
            }

            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 2.dp)
            ) {
                items(discoveryTools, key = { it.route }) { tool ->
                    Surface(
                        onClick = { onNavigateToRoute(tool.route) },
                        shape = RoundedCornerShape(12.dp),
                        color = colorScheme.surfaceContainerLow,
                        border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = tool.icon,
                                contentDescription = null,
                                tint = colorScheme.primary,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = tool.label,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colorScheme.onSurface,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AiQuickCommandPill(
    label: String,
    badge: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(11.dp),
        color = colorScheme.surface.copy(alpha = 0.92f),
        border = BorderStroke(1.dp, colorScheme.primary.copy(alpha = 0.25f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 7.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = badge,
                fontSize = 9.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = colorScheme.primary,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun DashboardBentoCard(
    icon: ImageVector,
    badge: String,
    title: String,
    subtitle: String,
    accentColor: Color,
    highlighted: Boolean,
    progressFraction: Float? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = if (highlighted) colorScheme.tertiaryContainer.copy(alpha = 0.45f) else colorScheme.surfaceContainerLow,
        border = BorderStroke(
            1.dp,
            if (highlighted) accentColor.copy(alpha = 0.55f) else colorScheme.outlineVariant.copy(alpha = 0.52f)
        ),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = accentColor.copy(alpha = 0.14f),
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = accentColor.copy(alpha = 0.14f)
                    ) {
                        Text(
                            text = badge,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = accentColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        color = colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (progressFraction != null) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { progressFraction.coerceIn(0f, 1f) },
                    color = accentColor,
                    trackColor = colorScheme.outlineVariant.copy(alpha = 0.35f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(999.dp))
                )
            }
        }
    }
}
