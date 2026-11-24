package com.example.sharkmentor.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.example.sharkmentor.MainActivity
import com.example.sharkmentor.R
import com.example.sharkmentor.core.streak.WaterStreakMood
import com.example.sharkmentor.core.streak.WaterStreakMoodDecision
import com.example.sharkmentor.core.streak.WaterStreakMoodEngine
import com.example.sharkmentor.core.widget.DayFocusStatus
import com.example.sharkmentor.core.widget.RefreshWidgetAction
import com.example.sharkmentor.core.widget.WidgetSnapshot
import com.example.sharkmentor.core.widget.WidgetThemeTokens
import com.example.sharkmentor.core.widget.WidgetUpdateManager
import java.time.LocalDateTime

/**
 * Duolingo-style "Su Serisi" (Water Streak) Home Screen Widget.
 *
 * Displays ONLY the 14 hand-drawn expressive mascot artworks (`mascot_shark_*.webp`) paired with
 * real-time emotional Turkish copy, water-drop streak badge, 7-day aqua drop strip, and one-tap
 * focus launcher. Never uses the default 3D app launcher icon (`ic_app_icon_default`).
 */
class SharkStreakWidget : GlanceAppWidget() {

    companion object {
        private val SIZE_COMPACT = DpSize(140.dp, 120.dp)
        private val SIZE_MEDIUM = DpSize(220.dp, 140.dp)
        private val SIZE_EXPANDED = DpSize(280.dp, 190.dp)
    }

    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(SIZE_COMPACT, SIZE_MEDIUM, SIZE_EXPANDED)
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = WidgetUpdateManager.loadSnapshot(context)
        val now = LocalDateTime.now()
        val decision = WaterStreakMoodEngine.resolve(
            streakCount = snapshot.streakCount,
            isDoneToday = snapshot.isStreakDoneToday,
            todayMinutes = snapshot.todayFocusMinutes,
            hourOfDay = now.hour,
            dayOfYear = now.dayOfYear,
            daysSinceLastStudy = snapshot.daysSinceLastStudy
        )

        provideContent {
            GlanceTheme {
                WaterStreakWidgetContent(snapshot = snapshot, decision = decision)
            }
        }
    }
}

@Composable
private fun WaterStreakWidgetContent(
    snapshot: WidgetSnapshot,
    decision: WaterStreakMoodDecision
) {
    val context = LocalContext.current
    val size = LocalSize.current
    val isCompact = size.width < 195.dp
    val isExpanded = size.width >= 265.dp && size.height >= 175.dp

    val startFocusIntent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        putExtra("EXTRA_START_FOCUS", true)
        putExtra("EXTRA_TARGET_ROUTE", "home")
    }

    val doneToday = snapshot.isStreakDoneToday
    val cardBg = when (decision.mood) {
        WaterStreakMood.FURIOUS, WaterStreakMood.PANIC, WaterStreakMood.TANTRUM ->
            WidgetThemeTokens.WaterUrgentBg
        WaterStreakMood.SHOCKED, WaterStreakMood.CRYING, WaterStreakMood.EXHAUSTED ->
            WidgetThemeTokens.WaterWarningBg
        WaterStreakMood.ECSTATIC, WaterStreakMood.LOVE, WaterStreakMood.PROUD, WaterStreakMood.HAPPY ->
            WidgetThemeTokens.WaterCelebrationBg
        else -> WidgetThemeTokens.DeepOceanBg
    }

    val dropTint = when (decision.mood) {
        WaterStreakMood.FURIOUS, WaterStreakMood.PANIC, WaterStreakMood.TANTRUM ->
            WidgetThemeTokens.PauseRed
        WaterStreakMood.SHOCKED, WaterStreakMood.CRYING ->
            WidgetThemeTokens.AlertAmber
        else -> WidgetThemeTokens.WaterDropAqua
    }

    val statusPillBg = when {
        doneToday -> WidgetThemeTokens.WaterDropBg
        decision.mood in setOf(WaterStreakMood.FURIOUS, WaterStreakMood.PANIC, WaterStreakMood.TANTRUM) ->
            WidgetThemeTokens.PauseRedBg
        else -> WidgetThemeTokens.ActiveBlueBg
    }
    val statusPillTextColor = when {
        doneToday -> WidgetThemeTokens.WaterDropAqua
        decision.mood in setOf(WaterStreakMood.FURIOUS, WaterStreakMood.PANIC, WaterStreakMood.TANTRUM) ->
            WidgetThemeTokens.PauseRed
        else -> WidgetThemeTokens.IceBlue
    }

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(cardBg)
            .cornerRadius(24.dp)
            .padding(if (isCompact) 10.dp else 12.dp)
            .clickable(actionStartActivity(startFocusIntent))
    ) {
        if (isCompact) {
            CompactWaterStreakLayout(
                snapshot = snapshot,
                decision = decision,
                dropTint = dropTint
            )
        } else {
            FullWaterStreakLayout(
                snapshot = snapshot,
                decision = decision,
                doneToday = doneToday,
                dropTint = dropTint,
                statusPillBg = statusPillBg,
                statusPillTextColor = statusPillTextColor,
                isExpanded = isExpanded,
                startFocusIntent = startFocusIntent
            )
        }
    }
}

@Composable
private fun CompactWaterStreakLayout(
    snapshot: WidgetSnapshot,
    decision: WaterStreakMoodDecision,
    dropTint: androidx.glance.unit.ColorProvider
) {
    Column(
        modifier = GlanceModifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                provider = ImageProvider(R.drawable.ic_widget_water_drop),
                contentDescription = "Su Serisi",
                modifier = GlanceModifier.size(16.dp),
                colorFilter = ColorFilter.tint(dropTint)
            )
            Spacer(modifier = GlanceModifier.width(4.dp))
            Text(
                text = "${snapshot.streakCount} Gün",
                style = TextStyle(
                    color = WidgetThemeTokens.OffWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                ),
                maxLines = 1
            )
            Spacer(modifier = GlanceModifier.defaultWeight())
            Box(
                modifier = GlanceModifier
                    .size(22.dp)
                    .background(WidgetThemeTokens.ElevatedCardBg)
                    .cornerRadius(11.dp)
                    .clickable(actionRunCallback<RefreshWidgetAction>()),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    provider = ImageProvider(R.drawable.ic_widget_refresh),
                    contentDescription = "Yenile",
                    modifier = GlanceModifier.size(11.dp),
                    colorFilter = ColorFilter.tint(WidgetThemeTokens.WaterDropAqua)
                )
            }
        }

        Spacer(modifier = GlanceModifier.height(4.dp))

        Image(
            provider = ImageProvider(decision.mood.widgetDrawableRes),
            contentDescription = decision.mood.badgeLabel,
            contentScale = ContentScale.Fit,
            modifier = GlanceModifier
                .size(64.dp)
        )

        Spacer(modifier = GlanceModifier.height(3.dp))

        Text(
            text = decision.widgetHeadline,
            style = TextStyle(
                color = WidgetThemeTokens.OffWhite,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp
            ),
            maxLines = 1
        )
        Spacer(modifier = GlanceModifier.height(2.dp))
        Text(
            text = "Hedef: ${snapshot.streakCount.coerceAtMost(snapshot.streakGoalDays)}/${snapshot.streakGoalDays}g",
            style = TextStyle(
                color = WidgetThemeTokens.WaterDropAqua,
                fontWeight = FontWeight.Bold,
                fontSize = 9.sp
            ),
            maxLines = 1
        )
    }
}

@Composable
private fun FullWaterStreakLayout(
    snapshot: WidgetSnapshot,
    decision: WaterStreakMoodDecision,
    doneToday: Boolean,
    dropTint: androidx.glance.unit.ColorProvider,
    statusPillBg: androidx.glance.unit.ColorProvider,
    statusPillTextColor: androidx.glance.unit.ColorProvider,
    isExpanded: Boolean,
    startFocusIntent: Intent
) {
    val activeTier = com.example.sharkmentor.data.repository.StreakGoalTier.fromTargetDays(snapshot.streakGoalDays)
    val clampedGoalProgress = snapshot.streakCount.coerceAtMost(activeTier.targetDays)
    Column(modifier = GlanceModifier.fillMaxSize()) {
        // Header row: Water Drop + "Su Serisi" + Status Pill + Refresh
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                provider = ImageProvider(R.drawable.ic_widget_water_drop),
                contentDescription = "Su Serisi",
                modifier = GlanceModifier.size(16.dp),
                colorFilter = ColorFilter.tint(dropTint)
            )
            Spacer(modifier = GlanceModifier.width(5.dp))
            Text(
                text = "Su Serisi",
                style = TextStyle(
                    color = WidgetThemeTokens.OffWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                ),
                maxLines = 1
            )

            Spacer(modifier = GlanceModifier.defaultWeight())

            Box(
                modifier = GlanceModifier
                    .background(statusPillBg)
                    .cornerRadius(8.dp)
                    .padding(horizontal = 7.dp, vertical = 2.dp)
            ) {
                Text(
                    text = if (doneToday) "Damla Cepte 💧" else decision.mood.badgeLabel,
                    style = TextStyle(
                        color = statusPillTextColor,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1
                )
            }
            Spacer(modifier = GlanceModifier.width(6.dp))
            Box(
                modifier = GlanceModifier
                    .size(24.dp)
                    .background(WidgetThemeTokens.ElevatedCardBg)
                    .cornerRadius(12.dp)
                    .clickable(actionRunCallback<RefreshWidgetAction>()),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    provider = ImageProvider(R.drawable.ic_widget_refresh),
                    contentDescription = "Yenile",
                    modifier = GlanceModifier.size(12.dp),
                    colorFilter = ColorFilter.tint(WidgetThemeTokens.WaterDropAqua)
                )
            }
        }

        Spacer(modifier = GlanceModifier.height(6.dp))

        // Hero Row: Mascot Artwork + Streak Count & Emotional Duolingo Copy
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                provider = ImageProvider(decision.mood.widgetDrawableRes),
                contentDescription = decision.mood.badgeLabel,
                contentScale = ContentScale.Fit,
                modifier = GlanceModifier.size(if (isExpanded) 86.dp else 72.dp)
            )

            Spacer(modifier = GlanceModifier.width(10.dp))

            Column(modifier = GlanceModifier.defaultWeight()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${snapshot.streakCount} Gün",
                        style = TextStyle(
                            color = WidgetThemeTokens.OffWhite,
                            fontWeight = FontWeight.Bold,
                            fontSize = if (isExpanded) 23.sp else 20.sp
                        ),
                        maxLines = 1
                    )
                    if (snapshot.streakFreezeAvailable > 0) {
                        Spacer(modifier = GlanceModifier.width(6.dp))
                        Box(
                            modifier = GlanceModifier
                                .background(WidgetThemeTokens.ActiveBlueBg)
                                .cornerRadius(6.dp)
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "❄️${snapshot.streakFreezeAvailable}",
                                style = TextStyle(
                                    color = WidgetThemeTokens.IceBlue,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                maxLines = 1
                            )
                        }
                    }
                }

                Spacer(modifier = GlanceModifier.height(2.dp))

                Text(
                    text = decision.widgetHeadline,
                    style = TextStyle(
                        color = WidgetThemeTokens.OffWhite,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    ),
                    maxLines = 1
                )

                Spacer(modifier = GlanceModifier.height(2.dp))

                Text(
                    text = decision.widgetSubtitle,
                    style = TextStyle(
                        color = WidgetThemeTokens.MutedBlue,
                        fontSize = 10.sp
                    ),
                    maxLines = 2
                )

                Spacer(modifier = GlanceModifier.height(4.dp))

                // Active Duolingo-style Streak Goal progress & reward preview pill
                Box(
                    modifier = GlanceModifier
                        .background(WidgetThemeTokens.ElevatedCardBg)
                        .cornerRadius(7.dp)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "Hedef $clampedGoalProgress/${activeTier.targetDays}g • +${activeTier.leaguePointsReward}p +${activeTier.freezeReward}❄️",
                        style = TextStyle(
                            color = WidgetThemeTokens.WaterDropAqua,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        maxLines = 1
                    )
                }
            }
        }

        if (isExpanded && snapshot.last7Days.isNotEmpty()) {
            Spacer(modifier = GlanceModifier.height(8.dp))
            Row(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .background(WidgetThemeTokens.ElevatedCardBg)
                    .cornerRadius(12.dp)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                snapshot.last7Days.forEachIndexed { index, day ->
                    WaterDayMiniIndicator(
                        day = day,
                        modifier = GlanceModifier.defaultWeight()
                    )
                    if (index < snapshot.last7Days.lastIndex) {
                        Spacer(modifier = GlanceModifier.width(4.dp))
                    }
                }
            }
        }

        Spacer(modifier = GlanceModifier.defaultWeight())
        Spacer(modifier = GlanceModifier.height(6.dp))

        // Quick Focus CTA
        Box(
            modifier = GlanceModifier
                .fillMaxWidth()
                .background(
                    if (doneToday) WidgetThemeTokens.OceanTeal else WidgetThemeTokens.IceBlue
                )
                .cornerRadius(13.dp)
                .padding(vertical = 7.dp)
                .clickable(actionStartActivity(startFocusIntent)),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    provider = ImageProvider(R.drawable.ic_widget_water_drop),
                    contentDescription = null,
                    modifier = GlanceModifier.size(13.dp),
                    colorFilter = ColorFilter.tint(WidgetThemeTokens.DarkOnAccent)
                )
                Spacer(modifier = GlanceModifier.width(5.dp))
                Text(
                    text = if (doneToday) "Ekstra Odak Başlat" else "Hemen Odaklan",
                    style = TextStyle(
                        color = WidgetThemeTokens.DarkOnAccent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun WaterDayMiniIndicator(day: DayFocusStatus, modifier: GlanceModifier = GlanceModifier) {
    val pillColor = when {
        day.isCompleted -> WidgetThemeTokens.WaterDropAqua
        day.minutes > 0L -> WidgetThemeTokens.IceBlue
        else -> WidgetThemeTokens.TrackBg
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = GlanceModifier
                .fillMaxWidth()
                .height(8.dp)
                .background(pillColor)
                .cornerRadius(4.dp)
        ) {}
        Spacer(modifier = GlanceModifier.height(2.dp))
        Text(
            text = day.dayLabel,
            style = TextStyle(
                color = if (day.isToday) WidgetThemeTokens.OffWhite else WidgetThemeTokens.MutedBlue,
                fontSize = 9.sp,
                fontWeight = if (day.isToday) FontWeight.Bold else FontWeight.Normal
            ),
            maxLines = 1
        )
    }
}

class SharkStreakWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SharkStreakWidget()
}
