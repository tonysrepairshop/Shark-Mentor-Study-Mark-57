package com.example.sharkmentor.ui.coaching

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.sharkmentor.core.ai.MultiProviderAiClient
import com.example.sharkmentor.core.ai.UserAiPreferences
import com.example.sharkmentor.core.coaching.BookCampManager
import com.example.sharkmentor.core.coaching.CampDayEpisode
import com.example.sharkmentor.core.coaching.CoachingEngine
import com.example.sharkmentor.core.coaching.ResourceCampBinding
import com.example.sharkmentor.core.service.FocusTimerService
import com.example.sharkmentor.core.timer.TimerMode
import com.example.sharkmentor.core.timer.TimerPhase
import com.example.sharkmentor.data.local.SharkMentorDatabase
import com.example.sharkmentor.data.local.entity.NoteEntity
import com.example.sharkmentor.data.local.entity.ResourceEntity
import com.example.sharkmentor.ui.components.MathSymbolCatalog
import com.example.sharkmentor.ui.components.MathSymbolKeyboardPanel
import com.example.sharkmentor.ui.components.MathSymbolToggleButton
import com.example.sharkmentor.ui.components.RichSolutionContent
import com.example.sharkmentor.ui.components.formatMathAsPlainText
import java.io.ByteArrayInputStream
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val STUDIO_MATH_FORMAT_RULES = """
ANLAŞILIRLIK VE MATEMATİKSEL YAZIM KURALLARI:
- Karmaşık ham LaTeX kodları veya karışık paragraf yığınları yazma; adım adım, ferah ve net açıkla.
- Önemli formülleri ve denklemleri ayrı satırda temiz matematik sembolleriyle (², ³, √, ·, ×, ÷, ±, Δ, π, ≤, ≥, ≠, ∫, ∑) yaz.
- Çözüm veya açıklamayı **1. Adım:**, **2. Adım:**, **Sonuç:** başlıklarıyla düzenli ayır.
- Gereksiz giriş cümleleri veya yapay selamlamalar kullanma; doğrudan soruya odaklan.
"""

/**
 * Balanced Split-Screen In-App Video Study Studio (Mark 62):
 * - Direct exact-episode playback (never restarts at playlist intro video #1).
 * - Screen-height bounded, collapsible video viewport with clipToBounds() so the AI Tutor canvas
 *   ALWAYS has spacious visibility and bottom-docked input across all phone screen sizes & IME states.
 * - Real playlist episode selector tab ("Bölümler") for 1-tap switching between real lessons.
 * - Opera-style toggleable Video Ad Blocker + live timestamp & caption transcript AI context.
 */
@Composable
fun CampVideoStudyStudioDialog(
    campTitle: String,
    subject: String,
    initialEpisode: CampDayEpisode,
    allEpisodes: List<CampDayEpisode> = listOf(initialEpisode),
    onEpisodeChanged: ((CampDayEpisode) -> Unit)? = null,
    onMarkCompleted: ((CampDayEpisode) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colorScheme = MaterialTheme.colorScheme
    val screenHeightDp = LocalConfiguration.current.screenHeightDp

    LaunchedEffect(context) {
        UserAiPreferences.initialize(context)
    }
    val studioAiEnabled by UserAiPreferences.videoStudioAiEnabled.collectAsStateWithLifecycle()
    val adBlockerEnabled by UserAiPreferences.videoAdBlockerEnabled.collectAsStateWithLifecycle()

    val safeEpisodes = remember(allEpisodes, initialEpisode) {
        allEpisodes.ifEmpty { listOf(initialEpisode) }
    }
    var activeEpisode by remember(initialEpisode.dayNumber, initialEpisode.videoId, initialEpisode.topicTitle) {
        mutableStateOf(initialEpisode)
    }
    // 0 = AI Asistan, 1 = Hızlı Not, 2 = Bölümler (when playlist has >1 episodes)
    var activeTab by remember(studioAiEnabled) { mutableIntStateOf(if (studioAiEnabled) 0 else 1) }
    var isVideoCollapsed by remember { mutableStateOf(false) }

    var isResolvingVideo by remember(activeEpisode.dayNumber, activeEpisode.videoId) {
        mutableStateOf(activeEpisode.videoId.length != 11)
    }
    var useMobileWatchFallback by remember(activeEpisode.dayNumber, activeEpisode.videoId) {
        mutableStateOf(false)
    }

    // Live playback telemetry from WebView JS Bridge
    var livePlaybackSeconds by remember { mutableIntStateOf(0) }
    var liveVideoTitle by remember(activeEpisode.dayNumber, activeEpisode.topicTitle) {
        mutableStateOf(activeEpisode.topicTitle)
    }
    var liveActiveVideoId by remember(activeEpisode.dayNumber, activeEpisode.videoId) {
        mutableStateOf(activeEpisode.videoId)
    }

    LaunchedEffect(activeEpisode.dayNumber, activeEpisode.videoId, activeEpisode.playlistId, activeEpisode.searchQuery) {
        livePlaybackSeconds = 0
        if (activeEpisode.videoId.length == 11) {
            liveActiveVideoId = activeEpisode.videoId
            liveVideoTitle = activeEpisode.topicTitle
            isResolvingVideo = false
            return@LaunchedEffect
        }
        isResolvingVideo = true
        val candidates = BookCampManager.resolveDirectVideosForEpisode(
            context = context,
            campTitle = campTitle,
            subject = subject,
            episode = activeEpisode
        )
        val best = candidates.firstOrNull()
        if (best != null && best.videoId.length == 11) {
            val updated = activeEpisode.copy(
                videoId = best.videoId,
                customUrl = "https://www.youtube.com/watch?v=${best.videoId}"
            )
            activeEpisode = updated
            liveActiveVideoId = best.videoId
            liveVideoTitle = best.title.ifBlank { activeEpisode.topicTitle }
            onEpisodeChanged?.invoke(updated)
        }
        isResolvingVideo = false
    }

    // Live Focus Timer state
    val timerState by FocusTimerService.timerStateFlow.collectAsStateWithLifecycle()
    val isFocusing = timerState.timerPhase == TimerPhase.FOCUSING
    val isPaused = timerState.timerPhase == TimerPhase.PAUSED
    val formattedTimer = FocusTimerService.formatMillis(timerState.displayMillis)

    // AI Tutor state
    var aiQuestion by remember { mutableStateOf("") }
    var aiAnswer by remember { mutableStateOf("") }
    var isAiLoading by remember { mutableStateOf(false) }
    var aiSavedToNotes by remember { mutableStateOf(false) }
    var showMathPanel by remember { mutableStateOf(false) }

    // Quick Note state
    var quickNoteText by remember { mutableStateOf("") }
    var noteSavedSuccess by remember { mutableStateOf(false) }

    // Custom video link paste state
    var showPasteLinkDialog by remember { mutableStateOf(false) }
    var customUrlInput by remember { mutableStateOf(activeEpisode.customUrl) }

    fun selectEpisode(ep: CampDayEpisode) {
        activeEpisode = ep
        liveActiveVideoId = ep.videoId
        liveVideoTitle = ep.topicTitle
        livePlaybackSeconds = 0
        useMobileWatchFallback = false
        onEpisodeChanged?.invoke(ep)
    }

    fun activeVideoUrlWithTimestamp(targetSec: Int = livePlaybackSeconds): String {
        val vid = liveActiveVideoId.ifBlank { activeEpisode.videoId }
        return if (vid.length == 11) {
            if (targetSec > 0) "https://www.youtube.com/watch?v=$vid&t=${targetSec}s"
            else "https://www.youtube.com/watch?v=$vid"
        } else {
            activeEpisode.buildExternalYouTubeUrl(targetSec)
        }
    }

    fun triggerAiTutorQuestion(questionText: String) {
        if (!studioAiEnabled) return
        val cleanQ = questionText.trim()
        if (cleanQ.isBlank() || isAiLoading) return
        isAiLoading = true
        aiAnswer = ""
        aiSavedToNotes = false
        showMathPanel = false
        scope.launch {
            val compass = CoachingEngine.buildAiSystemCompassPrompt(context)
            val mentionedSec = BookCampManager.extractMentionedTimestampSeconds(cleanQ)
            val effectiveSec = mentionedSec ?: livePlaybackSeconds
            val formattedTime = BookCampManager.formatSecondsAsTimestamp(effectiveSec)
            val currentVid = liveActiveVideoId.ifBlank { activeEpisode.videoId }
            val watchUrl = activeVideoUrlWithTimestamp(effectiveSec)

            val transcriptExcerpt = if (currentVid.length == 11) {
                BookCampManager.fetchYouTubeTranscriptWindow(
                    videoId = currentVid,
                    centerSeconds = effectiveSec,
                    windowRadiusSeconds = 75
                )
            } else ""

            val transcriptSection = if (transcriptExcerpt.isNotBlank()) {
                """
                [VİDEONUN $formattedTime CİVARINDAKİ KONUŞMA METNİ / ALTYAZI]
                $transcriptExcerpt
                """.trimIndent()
            } else {
                "[NOT: Videoda otomatik altyazı verisi bulunamadı; konu, başlık, zaman damgası ve pedagojik müfredat bilgisiyle net açıkla.]"
            }

            val fullPrompt = """
                [AKTİF İZLENEN DERS VİDEOSU]
                • Kamp / Liste: $campTitle
                • Ders: $subject
                • Bölüm / Konu: ${activeEpisode.dayNumber}. Ders - ${activeEpisode.topicTitle}
                • Video Başlığı: ${liveVideoTitle.ifBlank { activeEpisode.topicTitle }}
                • Aktif Video Linki: $watchUrl
                • Odaklanılan Video Zamanı: $formattedTime (${effectiveSec}. saniye)

                $transcriptSection

                Öğrencinin sorusu: "$cleanQ"
                Öğrencinin belirttiği dakika/formül veya kavramı doğrudan, adım adım, sade ve eksiksiz açıkla.
            """.trimIndent()

            val systemInstruction = "$compass\n\n$STUDIO_MATH_FORMAT_RULES"

            val response = withContext(Dispatchers.IO) {
                runCatching {
                    MultiProviderAiClient.executeUnifiedTextPrompt(
                        context = context,
                        systemPrompt = systemInstruction,
                        userPrompt = fullPrompt,
                        temperature = 0.25f,
                        maxTokens = 4096,
                        onPartialText = { partial ->
                            if (partial.isNotBlank()) {
                                aiAnswer = partial
                            }
                        }
                    )
                }
            }
            isAiLoading = false
            val finalOutput = response.getOrNull()?.trim().orEmpty()
            if (finalOutput.isNotBlank()) {
                aiAnswer = finalOutput
            } else if (aiAnswer.isBlank()) {
                aiAnswer = "Bağlantı kurulamadı. İnternet bağlantınızı veya Ayarlar > Yapay Zeka & API bölümünü kontrol edin."
            }
        }
    }

    fun saveTextToNotes(noteTitle: String, bodyText: String, onDone: () -> Unit) {
        val cleanBody = formatMathAsPlainText(bodyText).trim()
        if (cleanBody.isBlank()) return
        scope.launch(Dispatchers.IO) {
            val note = NoteEntity(
                id = UUID.randomUUID().toString(),
                title = noteTitle,
                content = cleanBody,
                updatedAt = System.currentTimeMillis()
            )
            SharkMentorDatabase.getDatabase(context).noteDao().upsertNote(note)
            withContext(Dispatchers.Main) {
                onDone()
            }
        }
    }

    fun toggleFocusTimerFromStudio() {
        val cleanSubject = CoachingEngine.formatCleanLeagueSubject(subject).ifBlank { "Odak" }
        val focusLabel = CoachingEngine.buildFormattedFocusSubject(
            title = cleanSubject,
            subNote = "${activeEpisode.dayNumber}. Ders ${activeEpisode.topicTitle}".take(42)
        )
        when (timerState.timerPhase) {
            TimerPhase.IDLE -> {
                CoachingEngine.selectFocusTitle(context, cleanSubject)
                CoachingEngine.setFocusSubNote(context, "${activeEpisode.dayNumber}. Ders ${activeEpisode.topicTitle}".take(42))
                CoachingEngine.updateLivePresenceOfflineFirst(context, true, cleanSubject)
                val intent = Intent(context, FocusTimerService::class.java).apply {
                    action = FocusTimerService.ACTION_START
                    putExtra(FocusTimerService.EXTRA_MODE, TimerMode.STOPWATCH.name)
                    putExtra(FocusTimerService.EXTRA_SUBJECT, focusLabel)
                }
                runCatching { context.startForegroundService(intent) }
            }
            TimerPhase.FOCUSING -> {
                runCatching {
                    context.startService(Intent(context, FocusTimerService::class.java).apply {
                        action = FocusTimerService.ACTION_PAUSE
                    })
                }
            }
            TimerPhase.PAUSED -> {
                runCatching {
                    context.startService(Intent(context, FocusTimerService::class.java).apply {
                        action = FocusTimerService.ACTION_RESUME
                    })
                }
            }
            TimerPhase.MANDATORY_BREAK -> {}
        }
    }

    if (showPasteLinkDialog) {
        AlertDialog(
            onDismissRequest = { showPasteLinkDialog = false },
            title = { Text("YouTube Video Bağlantısı", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = customUrlInput,
                    onValueChange = { customUrlInput = it },
                    label = { Text("YouTube Linki") },
                    placeholder = { Text("https://youtu.be/...") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val (vId, pId) = BookCampManager.parseYouTubeUrlIds(customUrlInput)
                        val updated = activeEpisode.copy(
                            videoId = vId.ifBlank { activeEpisode.videoId },
                            playlistId = pId.ifBlank { activeEpisode.playlistId },
                            customUrl = customUrlInput.trim()
                        )
                        selectEpisode(updated)
                        showPasteLinkDialog = false
                    }
                ) {
                    Text("Uygula")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPasteLinkDialog = false }) {
                    Text("İptal")
                }
            }
        )
    }

    // Bounded responsive video height so AI Assistant always gets generous, balanced screen real estate
    val videoHeightDp = remember(screenHeightDp, isVideoCollapsed) {
        if (isVideoCollapsed) {
            64.dp
        } else {
            (screenHeightDp * 0.29f).dp.coerceIn(156.dp, 218.dp)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = colorScheme.background,
            contentColor = colorScheme.onBackground
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .imePadding()
            ) {
                // ── 1. Slim Studio Top Bar (Title + Focus Timer + Close) ──
                Surface(
                    color = colorScheme.surface,
                    tonalElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Kapat",
                                    tint = colorScheme.onSurface,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${activeEpisode.dayNumber}. Ders • ${activeEpisode.topicTitle}",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = campTitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        // Compact Live Focus Timer Pill
                        Surface(
                            onClick = { toggleFocusTimerFromStudio() },
                            shape = RoundedCornerShape(50),
                            color = if (isFocusing) colorScheme.primaryContainer else colorScheme.surfaceVariant,
                            border = BorderStroke(
                                1.dp,
                                if (isFocusing) colorScheme.primary else colorScheme.outlineVariant
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = if (isFocusing) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isFocusing) "Duraklat" else "Odak Başlat",
                                    tint = if (isFocusing) colorScheme.primary else colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = if (isFocusing || isPaused) formattedTimer else "Odak",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isFocusing) colorScheme.onPrimaryContainer else colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // ── 2. Bounded, Clipped & Collapsible Video Player Viewport ──
                val playerUrl = remember(activeEpisode.videoId, activeEpisode.playlistId, activeEpisode.dayNumber, adBlockerEnabled, useMobileWatchFallback) {
                    if (useMobileWatchFallback && activeEpisode.videoId.length == 11) {
                        activeEpisode.buildMobileWatchUrl(livePlaybackSeconds)
                    } else {
                        activeEpisode.buildInAppEmbedUrl(adBlockerEnabled = adBlockerEnabled, autoplay = true)
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(videoHeightDp)
                        .clipToBounds()
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    if (isResolvingVideo) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = colorScheme.primary,
                                strokeWidth = 2.dp
                            )
                            Text(
                                text = "Ders videosu hazırlanıyor...",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.9f)
                            )
                        }
                    } else {
                        DistractionFreeYouTubeWebView(
                            url = playerUrl,
                            adBlockerEnabled = adBlockerEnabled,
                            onPlaybackTelemetry = { seconds, title, currentUrl ->
                                if (seconds >= 0) livePlaybackSeconds = seconds
                                if (title.isNotBlank() && !title.equals("YouTube", ignoreCase = true)) {
                                    liveVideoTitle = title.removeSuffix(" - YouTube").trim()
                                }
                                val (parsedVid, _) = BookCampManager.parseYouTubeUrlIds(currentUrl)
                                if (parsedVid.length == 11) {
                                    liveActiveVideoId = parsedVid
                                }
                            },
                            onEmbedRestricted = {
                                if (!useMobileWatchFallback && activeEpisode.videoId.length == 11) {
                                    useMobileWatchFallback = true
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                // ── 3. Slim Controls Strip (Prev/Next Episode + Collapse + AdBlock + Mode + Done + YouTube) ──
                Surface(
                    color = colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val currentIdx = safeEpisodes.indexOfFirst { it.dayNumber == activeEpisode.dayNumber }
                        IconButton(
                            onClick = {
                                if (currentIdx > 0) selectEpisode(safeEpisodes[currentIdx - 1])
                            },
                            enabled = currentIdx > 0,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.ChevronLeft, contentDescription = "Önceki Ders", modifier = Modifier.size(18.dp))
                        }
                        Text(
                            text = "${activeEpisode.dayNumber}/${safeEpisodes.size}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onSurface
                        )
                        IconButton(
                            onClick = {
                                if (currentIdx in 0 until safeEpisodes.lastIndex) selectEpisode(safeEpisodes[currentIdx + 1])
                            },
                            enabled = currentIdx in 0 until safeEpisodes.lastIndex,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.ChevronRight, contentDescription = "Sonraki Ders", modifier = Modifier.size(18.dp))
                        }

                        // Collapse / Expand video player toggle for maximum AI reading comfort
                        Surface(
                            onClick = { isVideoCollapsed = !isVideoCollapsed },
                            shape = RoundedCornerShape(8.dp),
                            color = colorScheme.surface,
                            border = BorderStroke(1.dp, colorScheme.outlineVariant)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(
                                    imageVector = if (isVideoCollapsed) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                                    contentDescription = if (isVideoCollapsed) "Videoyu Büyüt" else "Videoyu Küçült",
                                    tint = colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = if (isVideoCollapsed) "Büyüt" else "Küçült",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 10.sp,
                                    color = colorScheme.onSurface
                                )
                            }
                        }

                        // Ad Blocker quick toggle
                        Surface(
                            onClick = {
                                val nextVal = !adBlockerEnabled
                                UserAiPreferences.setVideoAdBlockerEnabled(context, nextVal)
                                Toast.makeText(
                                    context,
                                    if (nextVal) "Reklam engelleyici açıldı" else "Reklam engelleyici kapatıldı",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = if (adBlockerEnabled) colorScheme.primary.copy(alpha = 0.14f) else colorScheme.surface,
                            border = BorderStroke(
                                1.dp,
                                if (adBlockerEnabled) colorScheme.primary.copy(alpha = 0.5f) else colorScheme.outlineVariant
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Shield,
                                    contentDescription = "Reklam Engelleyici",
                                    tint = if (adBlockerEnabled) colorScheme.primary else colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = if (adBlockerEnabled) "Reklamsız" else "Reklam",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (adBlockerEnabled) colorScheme.primary else colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Embed <-> Mobile Watch mode switch
                        if (activeEpisode.videoId.length == 11) {
                            Surface(
                                onClick = { useMobileWatchFallback = !useMobileWatchFallback },
                                shape = RoundedCornerShape(8.dp),
                                color = colorScheme.surface,
                                border = BorderStroke(1.dp, colorScheme.outlineVariant)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SwapHoriz,
                                        contentDescription = "Oynatıcı",
                                        tint = colorScheme.primary,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        text = if (useMobileWatchFallback) "Sade" else "Tam",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 10.sp,
                                        color = colorScheme.onSurface
                                    )
                                }
                            }
                        }

                        // Paste custom link
                        Surface(
                            onClick = {
                                customUrlInput = activeVideoUrlWithTimestamp(0)
                                showPasteLinkDialog = true
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = colorScheme.surface,
                            border = BorderStroke(1.dp, colorScheme.outlineVariant)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(Icons.Default.Link, contentDescription = "Link", tint = colorScheme.primary, modifier = Modifier.size(12.dp))
                                Text("Link", style = MaterialTheme.typography.labelSmall, fontSize = 10.sp, color = colorScheme.onSurface)
                            }
                        }

                        if (onMarkCompleted != null) {
                            Surface(
                                onClick = { onMarkCompleted(activeEpisode) },
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF10B981).copy(alpha = 0.14f),
                                border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.45f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = "Tamamla", tint = Color(0xFF10B981), modifier = Modifier.size(12.dp))
                                    Text("Bitti", style = MaterialTheme.typography.labelSmall, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF10B981))
                                }
                            }
                        }

                        Surface(
                            onClick = {
                                runCatching {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(activeVideoUrlWithTimestamp(livePlaybackSeconds)))
                                    )
                                }
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = colorScheme.primary,
                            contentColor = colorScheme.onPrimary
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "YouTube", modifier = Modifier.size(12.dp))
                                Text("YouTube", style = MaterialTheme.typography.labelSmall, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // ── 4. Spacious Lower Workspace (AI Asistan | Hızlı Not | Bölümler) ──
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Compact Tab Row
                    val tabItems = remember(safeEpisodes.size) {
                        buildList {
                            add(Triple(0, "AI Asistan", Icons.Default.AutoAwesome))
                            add(Triple(1, "Hızlı Not", Icons.Default.EditNote))
                            if (safeEpisodes.size > 1) {
                                add(Triple(2, "Bölümler (${safeEpisodes.size})", Icons.Default.PlaylistPlay))
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        tabItems.forEach { (tabId, label, icon) ->
                            val selected = activeTab == tabId
                            Surface(
                                onClick = { activeTab = tabId },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                color = if (selected) colorScheme.primaryContainer else colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                border = BorderStroke(
                                    1.dp,
                                    if (selected) colorScheme.primary else colorScheme.outlineVariant.copy(alpha = 0.4f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 7.dp, horizontal = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        tint = if (selected) colorScheme.primary else colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (selected) colorScheme.onPrimaryContainer else colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    when (activeTab) {
                        0 -> {
                            if (!studioAiEnabled) {
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth(),
                                    shape = RoundedCornerShape(14.dp),
                                    color = colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                    border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.45f))
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(18.dp),
                                        verticalArrangement = Arrangement.Center,
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = "Video Stüdyosu AI Asistanı Kapalı",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = "Ayarlar > Yapay Zeka, API & Video Stüdyosu bölümünden dilediğin zaman açabilirsin.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                        OutlinedButton(
                                            onClick = { UserAiPreferences.setVideoStudioAiEnabled(context, true) },
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Text("Stüdyo AI'yı Şimdi Aç")
                                        }
                                    }
                                }
                            } else {
                                val liveTimeLabel = BookCampManager.formatSecondsAsTimestamp(livePlaybackSeconds)

                                // ── Spacious AI Assistant Response Canvas (Always Prominent!) ──
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth()
                                        .heightIn(min = 120.dp),
                                    shape = RoundedCornerShape(14.dp),
                                    color = colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                    border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.4f))
                                ) {
                                    if (aiAnswer.isBlank() && !isAiLoading) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .verticalScroll(rememberScrollState())
                                                .padding(14.dp),
                                            verticalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Text(
                                                text = "İzlediğin video ve aktif süre ($liveTimeLabel) AI asistanına bağlı. Anlamadığın formülü veya dakikayı doğrudan sorabilirsin.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = colorScheme.onSurfaceVariant
                                            )

                                            // Live Timestamp Chip + Concise Quick Prompts inside empty canvas
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .horizontalScroll(rememberScrollState()),
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Surface(
                                                    onClick = {
                                                        aiQuestion = if (livePlaybackSeconds > 0) {
                                                            "$liveTimeLabel dakikasındaki formülü/adımı neden öyle yaptık? "
                                                        } else {
                                                            "4.5 dakikadaki formül neden öyle? "
                                                        }
                                                    },
                                                    shape = RoundedCornerShape(9.dp),
                                                    color = colorScheme.primary.copy(alpha = 0.14f),
                                                    border = BorderStroke(1.dp, colorScheme.primary.copy(alpha = 0.45f))
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.AccessTime,
                                                            contentDescription = null,
                                                            tint = colorScheme.primary,
                                                            modifier = Modifier.size(13.dp)
                                                        )
                                                        Text(
                                                            text = if (livePlaybackSeconds > 0) "Şu Anı Sor ($liveTimeLabel)" else "Dakikayı Sor",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            fontWeight = FontWeight.Bold,
                                                            color = colorScheme.primary
                                                        )
                                                    }
                                                }
                                                listOf(
                                                    "Bu dersteki formülleri adım adım çıkar",
                                                    "Konunun püf noktalarını özetle",
                                                    "ÖSYM bu konudan nasıl sorar?"
                                                ).forEach { chipPrompt ->
                                                    Surface(
                                                        onClick = {
                                                            aiQuestion = chipPrompt
                                                            triggerAiTutorQuestion(chipPrompt)
                                                        },
                                                        shape = RoundedCornerShape(9.dp),
                                                        color = colorScheme.surface,
                                                        border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.5f))
                                                    ) {
                                                        Text(
                                                            text = chipPrompt,
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = colorScheme.onSurface,
                                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    } else {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(12.dp)
                                                .verticalScroll(rememberScrollState()),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            if (isAiLoading && aiAnswer.isBlank()) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                                    Text(
                                                        text = "Video bağlamı ve formüller analiz ediliyor...",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            } else {
                                                SelectionContainer {
                                                    RichSolutionContent(
                                                        text = aiAnswer,
                                                        textColor = colorScheme.onSurface,
                                                        fontSize = 14.sp,
                                                        lineHeight = 22.sp
                                                    )
                                                }
                                            }
                                            if (aiAnswer.isNotBlank() && !isAiLoading) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    TextButton(
                                                        onClick = {
                                                            aiAnswer = ""
                                                            aiQuestion = if (livePlaybackSeconds > 0) {
                                                                "$liveTimeLabel dakikasındaki adım neden öyle? "
                                                            } else ""
                                                        }
                                                    ) {
                                                        Text("Yeni Soru")
                                                    }
                                                    TextButton(
                                                        onClick = {
                                                            saveTextToNotes(
                                                                noteTitle = "$campTitle • ${activeEpisode.dayNumber}. Ders (${activeEpisode.topicTitle})",
                                                                bodyText = aiAnswer,
                                                                onDone = { aiSavedToNotes = true }
                                                            )
                                                        },
                                                        enabled = !aiSavedToNotes
                                                    ) {
                                                        Icon(
                                                            imageVector = if (aiSavedToNotes) Icons.Default.CheckCircle else Icons.Default.EditNote,
                                                            contentDescription = null,
                                                            modifier = Modifier.size(15.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text(if (aiSavedToNotes) "Kaydedildi" else "Notlara Kaydet")
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                // Collapsible ∑ Math Symbol Panel docked above input bar
                                MathSymbolKeyboardPanel(
                                    visible = showMathPanel,
                                    onInsertSymbol = { sym ->
                                        aiQuestion = MathSymbolCatalog.insertSymbol(aiQuestion, sym)
                                    }
                                )

                                // Bottom-docked Question Input Bar (Always visible & balanced)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    MathSymbolToggleButton(
                                        expanded = showMathPanel,
                                        onClick = { showMathPanel = !showMathPanel }
                                    )
                                    OutlinedTextField(
                                        value = aiQuestion,
                                        onValueChange = { aiQuestion = it },
                                        placeholder = {
                                            Text(
                                                text = "Örn: 4.5 dakikadaki formül neden öyle?",
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                fontSize = 13.sp
                                            )
                                        },
                                        singleLine = true,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.weight(1f)
                                    )
                                    Button(
                                        onClick = { triggerAiTutorQuestion(aiQuestion) },
                                        enabled = aiQuestion.isNotBlank() && !isAiLoading,
                                        shape = RoundedCornerShape(12.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp)
                                    ) {
                                        if (isAiLoading) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(18.dp),
                                                color = colorScheme.onPrimary,
                                                strokeWidth = 2.dp
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.Send,
                                                contentDescription = "Sor",
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        1 -> {
                            // Tab 2: Quick Note Taking while watching
                            OutlinedTextField(
                                value = quickNoteText,
                                onValueChange = {
                                    quickNoteText = it
                                    noteSavedSuccess = false
                                },
                                placeholder = {
                                    Text("${activeEpisode.dayNumber}. Ders (${activeEpisode.topicTitle}) notlarını buraya yaz...")
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            val stamp = "[${BookCampManager.formatSecondsAsTimestamp(livePlaybackSeconds)}] "
                                            quickNoteText = if (quickNoteText.isBlank()) stamp else "$quickNoteText\n$stamp"
                                        },
                                        shape = RoundedCornerShape(9.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Icon(Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Zaman Ekle", fontSize = 11.sp)
                                    }
                                    if (noteSavedSuccess) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = "Kaydedildi",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = colorScheme.primary,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }

                                Button(
                                    onClick = {
                                        saveTextToNotes(
                                            noteTitle = "$campTitle • ${activeEpisode.dayNumber}. Ders (${activeEpisode.topicTitle})",
                                            bodyText = quickNoteText,
                                            onDone = { noteSavedSuccess = true }
                                        )
                                    },
                                    enabled = quickNoteText.isNotBlank(),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("Notlara Kaydet", fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        else -> {
                            // Tab 3: Real Playlist Episodes Selector (1-tap exact video switch)
                            LazyColumn(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(safeEpisodes, key = { "${it.dayNumber}_${it.videoId}" }) { ep ->
                                    val isCurrent = ep.dayNumber == activeEpisode.dayNumber
                                    Surface(
                                        onClick = {
                                            selectEpisode(ep)
                                            if (studioAiEnabled) activeTab = 0
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (isCurrent) colorScheme.primaryContainer else colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                        border = BorderStroke(
                                            1.dp,
                                            if (isCurrent) colorScheme.primary else colorScheme.outlineVariant.copy(alpha = 0.4f)
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 9.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (isCurrent) Icons.Default.PlayArrow else Icons.Default.PlayCircleOutline,
                                                contentDescription = null,
                                                tint = if (isCurrent) colorScheme.primary else colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "${ep.dayNumber}. Ders • ${ep.topicTitle}",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                                    color = if (isCurrent) colorScheme.onPrimaryContainer else colorScheme.onSurface,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            if (isCurrent) {
                                                Text(
                                                    text = "Oynuyor",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = colorScheme.primary
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private const val STUDIO_TELEMETRY_JS = """
(function() {
  if (window.__sharkTelemetryInstalled) return;
  window.__sharkTelemetryInstalled = true;
  setInterval(function() {
    try {
      var v = document.querySelector('video');
      var sec = (v && !isNaN(v.currentTime)) ? Math.floor(v.currentTime) : -1;
      var titleEl = document.querySelector('.ytp-title-link, h2.slim-video-information-title, .slim-video-metadata-header');
      var title = (titleEl && titleEl.innerText) ? titleEl.innerText : (document.title || '');
      if (window.SharkStudioBridge) {
        window.SharkStudioBridge.onTelemetry(sec, title, window.location.href || '');
      }
      var errEl = document.querySelector('.ytp-error-content-wrap-reason, .ytp-error');
      if (errEl && errEl.innerText && errEl.innerText.length > 4 && window.SharkStudioBridge) {
        window.SharkStudioBridge.onEmbedError();
      }
    } catch (e) {}
  }, 1800);
})();
"""

private const val STUDIO_AD_BLOCKER_JS = """
(function() {
  if (!document.getElementById('shark-adblock-style')) {
    var st = document.createElement('style');
    st.id = 'shark-adblock-style';
    st.textContent = '.ytp-ad-module,.ytp-ad-overlay-container,.ytp-ad-text-overlay,.ytp-ad-player-overlay,ytm-promoted-sparkles-web-renderer,ytm-companion-ad-renderer,ad-slot-renderer,.ad-container,.video-ads{display:none!important;visibility:hidden!important;opacity:0!important;pointer-events:none!important;}';
    (document.head || document.documentElement).appendChild(st);
  }
  if (window.__sharkAdSkipperInstalled) return;
  window.__sharkAdSkipperInstalled = true;
  setInterval(function() {
    try {
      var player = document.querySelector('.html5-video-player');
      var video = document.querySelector('video');
      var isAd = (player && (player.classList.contains('ad-showing') || player.classList.contains('ad-interrupting'))) || !!document.querySelector('.ytp-ad-player-overlay-layout,.ytp-ad-preview-container');
      if (isAd && video) {
        video.muted = true;
        if (isFinite(video.duration) && video.duration > 0.5) {
          video.currentTime = video.duration - 0.1;
        }
        video.playbackRate = 16.0;
      } else if (video && video.playbackRate > 4.0) {
        video.playbackRate = 1.0;
        video.muted = false;
      }
      var skipBtns = document.querySelectorAll('.ytp-ad-skip-button,.ytp-ad-skip-button-modern,.ytp-skip-ad-button,button[class*="skip-ad"]');
      for (var i = 0; i < skipBtns.length; i++) {
        skipBtns[i].click();
      }
    } catch (e) {}
  }, 600);
})();
"""

@SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
@Composable
private fun DistractionFreeYouTubeWebView(
    url: String,
    adBlockerEnabled: Boolean,
    onPlaybackTelemetry: (seconds: Int, title: String, currentUrl: String) -> Unit,
    onEmbedRestricted: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentAdBlock by rememberUpdatedState(adBlockerEnabled)
    val currentOnTelemetry by rememberUpdatedState(onPlaybackTelemetry)
    val currentOnEmbedRestricted by rememberUpdatedState(onEmbedRestricted)

    val webView = remember(context) {
        WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(android.graphics.Color.BLACK)
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                mediaPlaybackRequiresUserGesture = false
                loadWithOverviewMode = true
                useWideViewPort = true
                cacheMode = WebSettings.LOAD_DEFAULT
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                setSupportZoom(false)
                builtInZoomControls = false
                displayZoomControls = false
                setGeolocationEnabled(false)
            }
            addJavascriptInterface(
                object {
                    @JavascriptInterface
                    fun onTelemetry(seconds: Int, title: String, currentUrl: String) {
                        post { currentOnTelemetry(seconds, title, currentUrl) }
                    }

                    @JavascriptInterface
                    fun onEmbedError() {
                        post { currentOnEmbedRestricted() }
                    }
                },
                "SharkStudioBridge"
            )
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?
                ): WebResourceResponse? {
                    if (currentAdBlock) {
                        val reqUrl = request?.url?.toString().orEmpty()
                        if (BookCampManager.isAdOrTrackerUrl(reqUrl)) {
                            return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
                        }
                    }
                    return super.shouldInterceptRequest(view, request)
                }

                override fun onPageFinished(view: WebView?, loadedUrl: String?) {
                    super.onPageFinished(view, loadedUrl)
                    view?.evaluateJavascript(STUDIO_TELEMETRY_JS, null)
                    if (currentAdBlock) {
                        view?.evaluateJavascript(STUDIO_AD_BLOCKER_JS, null)
                    }
                }

                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {
                    val host = request?.url?.host?.lowercase().orEmpty()
                    val allowed = host.contains("youtube.com") ||
                        host.contains("youtube-nocookie.com") ||
                        host.contains("youtu.be") ||
                        host.contains("googlevideo.com") ||
                        host.contains("ytimg.com")
                    return !allowed
                }
            }
        }
    }

    DisposableEffect(webView) {
        onDispose {
            runCatching {
                webView.removeJavascriptInterface("SharkStudioBridge")
                webView.stopLoading()
                webView.loadUrl("about:blank")
                webView.clearHistory()
                webView.destroy()
            }
        }
    }

    AndroidView(
        factory = { webView },
        update = { view ->
            if (view.tag != url) {
                view.tag = url
                val headers = mapOf("Referer" to "https://www.youtube-nocookie.com/")
                view.loadUrl(url, headers)
            }
        },
        modifier = modifier
    )
}

/**
 * Mark 62 Real-Playlist Video Binding Dialog:
 * - Searches YouTube for genuinely relevant top-ranked playlists ("YouTube'da Bul") without fabricating fake camps.
 * - Absorbs REAL YouTube playlist videos (stripping intro/promo videos, keeping exact real order, count & videoIds).
 * - Lets students jump directly to any real lesson (e.g. Lesson 4) or detach video support cleanly.
 */
@Composable
fun ResourceCampConfigDialog(
    resource: ResourceEntity,
    initialBinding: ResourceCampBinding,
    onSaved: (ResourceCampBinding) -> Unit,
    onOpenStudio: (ResourceCampBinding) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colorScheme = MaterialTheme.colorScheme

    var campTitle by remember(initialBinding) { mutableStateOf(initialBinding.campTitle) }
    var instructor by remember(initialBinding) { mutableStateOf(initialBinding.instructor) }
    var currentDayText by remember(initialBinding) { mutableStateOf(initialBinding.currentDay.toString()) }
    var youtubeUrl by remember(initialBinding) { mutableStateOf(initialBinding.youtubeUrl) }
    var loadedEpisodes by remember(initialBinding) { mutableStateOf(initialBinding.episodes) }
    var isFromRealPlaylist by remember(initialBinding) { mutableStateOf(initialBinding.isFromRealPlaylist) }

    var isFetchingPlaylist by remember { mutableStateOf(false) }
    var isAutoSearching by remember { mutableStateOf(false) }
    var isDistributing by remember { mutableStateOf(false) }
    var statusNotice by remember { mutableStateOf<String?>(null) }

    val (parsedVid, parsedPlId) = remember(youtubeUrl) { BookCampManager.parseYouTubeUrlIds(youtubeUrl) }
    val hasValidMedia = loadedEpisodes.any { it.videoId.length == 11 } || parsedVid.length == 11 || parsedPlId.isNotBlank()

    fun buildAndSaveBinding(): ResourceCampBinding {
        val total = loadedEpisodes.size.coerceAtLeast(1)
        val cur = (currentDayText.toIntOrNull() ?: 1).coerceIn(1, total)
        return BookCampManager.saveResourceCampBinding(
            context = context,
            resource = resource,
            campTitle = campTitle.ifBlank { resource.title },
            instructor = instructor,
            totalDays = total,
            currentDay = cur,
            youtubeUrl = youtubeUrl,
            realEpisodes = loadedEpisodes,
            isFromRealPlaylist = isFromRealPlaylist && loadedEpisodes.isNotEmpty()
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = "Video Oynatma Listesi Bağlantısı",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${resource.title} • ${resource.subject}",
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.primary
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 1. Auto-search YouTube strictly for this book
                OutlinedButton(
                    onClick = {
                        if (isAutoSearching || isFetchingPlaylist) return@OutlinedButton
                        isAutoSearching = true
                        statusNotice = "YouTube'da bu kitaba uygun liste aranıyor..."
                        scope.launch {
                            val discovered = BookCampManager.discoverAndSyncRealCampForResource(
                                context = context,
                                resource = resource,
                                forceRefresh = true
                            )
                            isAutoSearching = false
                            if (discovered.hasVerifiedVideoSupport) {
                                campTitle = discovered.campTitle
                                instructor = discovered.instructor
                                youtubeUrl = discovered.youtubeUrl
                                loadedEpisodes = discovered.episodes
                                isFromRealPlaylist = discovered.isFromRealPlaylist
                                currentDayText = discovered.currentDay.toString()
                                statusNotice = "Gerçek liste bulundu: ${discovered.episodes.size} ders videosu (tanıtımlar filtrelendi)."
                                onSaved(discovered)
                            } else {
                                loadedEpisodes = emptyList()
                                isFromRealPlaylist = false
                                statusNotice = "Bu kitap için YouTube ilk sıralarında alakalı video listesi bulunamadı. İstersen aşağıya kendi YouTube linkini yapıştırabilirsin."
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    if (isAutoSearching) {
                        CircularProgressIndicator(modifier = Modifier.size(15.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("YouTube'da Bu Kitap İçin Ara", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }

                // 2. Manual YouTube Playlist / Video Link + Fetch Real Videos button
                OutlinedTextField(
                    value = youtubeUrl,
                    onValueChange = { youtubeUrl = it },
                    label = { Text("YouTube Oynatma Listesi veya Video Linki") },
                    placeholder = { Text("https://youtube.com/playlist?list=...") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            if (isFetchingPlaylist || parsedPlId.isBlank()) {
                                if (parsedVid.length == 11) {
                                    loadedEpisodes = listOf(
                                        CampDayEpisode(
                                            dayNumber = 1,
                                            topicTitle = campTitle.ifBlank { resource.title },
                                            videoId = parsedVid,
                                            customUrl = youtubeUrl.trim()
                                        )
                                    )
                                    isFromRealPlaylist = false
                                    currentDayText = "1"
                                    statusNotice = "Tekil YouTube videosu bağlandı."
                                } else {
                                    statusNotice = "Geçerli bir YouTube oynatma listesi veya video linki gir."
                                }
                                return@OutlinedButton
                            }
                            isFetchingPlaylist = true
                            statusNotice = "Oynatma listesindeki gerçek ders videoları çekiliyor..."
                            scope.launch {
                                val (plTitle, eps) = BookCampManager.fetchRealPlaylistEpisodes(youtubeUrl)
                                isFetchingPlaylist = false
                                if (eps.isNotEmpty()) {
                                    if (plTitle.isNotBlank()) campTitle = plTitle
                                    loadedEpisodes = eps
                                    isFromRealPlaylist = true
                                    currentDayText = (currentDayText.toIntOrNull() ?: 1).coerceIn(1, eps.size).toString()
                                    statusNotice = "${eps.size} gerçek ders videosu sırasıyla çekildi (tanıtımlar atlandı)."
                                } else {
                                    statusNotice = "Oynatma listesi okunamadı. Bağlantının herkese açık olduğundan emin ol."
                                }
                            }
                        },
                        enabled = !isFetchingPlaylist && youtubeUrl.isNotBlank(),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        if (isFetchingPlaylist) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.PlaylistPlay, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                        Spacer(modifier = Modifier.width(5.dp))
                        Text("Listeyi Çek", fontSize = 11.sp)
                    }

                    if (hasValidMedia && loadedEpisodes.isNotEmpty()) {
                        OutlinedButton(
                            onClick = {
                                if (isDistributing) return@OutlinedButton
                                isDistributing = true
                                val saved = buildAndSaveBinding()
                                onSaved(saved)
                                scope.launch {
                                    val added = BookCampManager.distributeCampToWeeklyPlan(context, saved)
                                    isDistributing = false
                                    statusNotice = "$added ders haftalık plana eklendi."
                                    Toast.makeText(context, "$added ders haftalık plana eklendi", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(5.dp))
                            Text("Plana Dağıt", fontSize = 11.sp)
                        }
                    }
                }

                if (hasValidMedia) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = campTitle,
                            onValueChange = { campTitle = it },
                            label = { Text("Liste Adı") },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = currentDayText,
                            onValueChange = { currentDayText = it.filter { c -> c.isDigit() } },
                            label = { Text("Aktif Ders") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.width(96.dp)
                        )
                    }
                }

                if (!statusNotice.isNullOrBlank()) {
                    Text(
                        text = statusNotice!!,
                        style = MaterialTheme.typography.labelSmall,
                        color = colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Real episode preview (tapping selects the exact episode to start from)
                if (loadedEpisodes.isNotEmpty()) {
                    val selectedDay = (currentDayText.toIntOrNull() ?: 1).coerceIn(1, loadedEpisodes.size)
                    val startIdx = (selectedDay - 1).coerceAtLeast(0)
                    val window = loadedEpisodes.drop(startIdx).take(6)
                    HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Text(
                        text = "Gerçek Liste Sırası (${loadedEpisodes.size} Ders • Seçmek için dokun)",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onSurfaceVariant
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        window.forEach { ep ->
                            val isSelected = ep.dayNumber == selectedDay
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isSelected) colorScheme.primaryContainer.copy(alpha = 0.6f)
                                        else colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                    )
                                    .clickable { currentDayText = ep.dayNumber.toString() }
                                    .padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${ep.dayNumber}. ${ep.topicTitle}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isSelected) colorScheme.primary else colorScheme.onSurface,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                if (isSelected) {
                                    Text(
                                        text = "Seçili",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                if (initialBinding.hasVerifiedVideoSupport) {
                    TextButton(
                        onClick = {
                            val cleared = BookCampManager.clearResourceVideoBinding(context, resource)
                            onSaved(cleared)
                            onDismiss()
                        },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Icon(
                            Icons.Default.DeleteOutline,
                            contentDescription = null,
                            tint = colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Video Desteğini Kaldır", color = colorScheme.error, fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (hasValidMedia) {
                    OutlinedButton(
                        onClick = {
                            val saved = buildAndSaveBinding()
                            onSaved(saved)
                            onOpenStudio(saved)
                        },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.PlayCircleOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("İzle")
                    }
                }

                Button(
                    onClick = {
                        if (hasValidMedia) {
                            val saved = buildAndSaveBinding()
                            onSaved(saved)
                        }
                        onDismiss()
                    },
                    enabled = hasValidMedia,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Kaydet")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Kapat")
            }
        }
    )
}
