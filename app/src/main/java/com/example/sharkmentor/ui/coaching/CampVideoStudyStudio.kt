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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircleOutline
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
import androidx.compose.ui.graphics.Color
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
import com.example.sharkmentor.core.coaching.ResolvedYouTubeVideo
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
 * Split-Screen In-App Camp Video Study Studio (Mark 60):
 * - Direct YouTube video auto-resolution (plays the day's matching lesson directly — never a search page).
 * - Opera-style toggleable Video Ad Blocker (network request filter + player ad-skipper JS, synced with Settings).
 * - Live active video URL, title, playback timestamp (`mm:ss`) & caption transcript context passed to Studio AI.
 * - Unified AI provider/key routing (uses user's selected API key if set, otherwise built-in fixed Gemini key).
 * - Structured math/formula rendering (`RichSolutionContent`) + interactive `∑` Math Symbol Panel.
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

    LaunchedEffect(context) {
        UserAiPreferences.initialize(context)
    }
    val studioAiEnabled by UserAiPreferences.videoStudioAiEnabled.collectAsStateWithLifecycle()
    val adBlockerEnabled by UserAiPreferences.videoAdBlockerEnabled.collectAsStateWithLifecycle()

    var activeEpisode by remember(initialEpisode) { mutableStateOf(initialEpisode) }
    var activeTab by remember(studioAiEnabled) { mutableIntStateOf(if (studioAiEnabled) 0 else 1) } // 0 = AI'ya Sor, 1 = Hızlı Not

    // Direct video resolution state (never falls back to YouTube search page if resolvable)
    var resolvedCandidates by remember { mutableStateOf<List<ResolvedYouTubeVideo>>(emptyList()) }
    var isResolvingVideo by remember { mutableStateOf(activeEpisode.videoId.length != 11) }
    var useMobileWatchFallback by remember(activeEpisode.dayNumber, activeEpisode.videoId) { mutableStateOf(false) }

    // Live playback telemetry from WebView JS Bridge
    var livePlaybackSeconds by remember { mutableIntStateOf(0) }
    var liveVideoTitle by remember { mutableStateOf(activeEpisode.topicTitle) }
    var liveActiveVideoId by remember(activeEpisode.videoId) { mutableStateOf(activeEpisode.videoId) }

    LaunchedEffect(activeEpisode.dayNumber, activeEpisode.videoId, activeEpisode.playlistId, activeEpisode.searchQuery) {
        livePlaybackSeconds = 0
        if (activeEpisode.videoId.length == 11) {
            liveActiveVideoId = activeEpisode.videoId
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
        resolvedCandidates = candidates
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
                • Kamp: $campTitle
                • Ders: $subject
                • Gün / Konu: ${activeEpisode.dayNumber}. Gün - ${activeEpisode.topicTitle}
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
        val focusLabel = CoachingEngine.buildFormattedFocusSubject(
            title = subject.ifBlank { "Kamp" },
            subNote = "${activeEpisode.dayNumber}. Gün ${activeEpisode.topicTitle}".take(42)
        )
        when (timerState.timerPhase) {
            TimerPhase.IDLE -> {
                CoachingEngine.selectFocusTitle(context, subject.ifBlank { "Kamp" })
                CoachingEngine.setFocusSubNote(context, "${activeEpisode.dayNumber}. Gün ${activeEpisode.topicTitle}".take(42))
                CoachingEngine.updateLivePresenceOfflineFirst(context, true, focusLabel)
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
            title = { Text("YouTube Video veya Liste Bağlantısı", fontWeight = FontWeight.Bold) },
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
                        activeEpisode = updated
                        if (vId.length == 11) liveActiveVideoId = vId
                        useMobileWatchFallback = false
                        onEpisodeChanged?.invoke(updated)
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

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = colorScheme.background,
            contentColor = colorScheme.onBackground
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // ── Top Studio Header (Camp Info + Live Focus Timer + Close) ──
                Surface(
                    color = colorScheme.surface,
                    tonalElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Kapat",
                                    tint = colorScheme.onSurface
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = campTitle,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${activeEpisode.dayNumber}. Gün • ${activeEpisode.topicTitle}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        // Live Synced Focus Timer Pill
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
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = if (isFocusing) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isFocusing) "Duraklat" else "Odak Başlat",
                                    tint = if (isFocusing) colorScheme.primary else colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = if (isFocusing || isPaused) formattedTimer else "Odak Başlat",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isFocusing) colorScheme.onPrimaryContainer else colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // ── Upper Half: Direct Auto-Playing Video Player (with Opera-Style Ad Blocker) ──
                val playerUrl = remember(activeEpisode, adBlockerEnabled, useMobileWatchFallback) {
                    if (useMobileWatchFallback && activeEpisode.videoId.length == 11) {
                        activeEpisode.buildMobileWatchUrl(livePlaybackSeconds)
                    } else {
                        activeEpisode.buildInAppEmbedUrl(adBlockerEnabled = adBlockerEnabled, autoplay = true)
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    if (isResolvingVideo) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(28.dp),
                                color = colorScheme.primary,
                                strokeWidth = 2.5.dp
                            )
                            Text(
                                text = "Ders videosu doğrudan açılıyor...",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.85f)
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

                // ── Action Strip Directly Underneath Video (Day Nav + AdBlock + Player Mode + Link + YouTube) ──
                Surface(
                    color = colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Day episode navigation
                        val currentIdx = allEpisodes.indexOfFirst { it.dayNumber == activeEpisode.dayNumber }
                        IconButton(
                            onClick = {
                                if (currentIdx > 0) {
                                    val prev = allEpisodes[currentIdx - 1]
                                    activeEpisode = prev
                                    useMobileWatchFallback = false
                                    onEpisodeChanged?.invoke(prev)
                                }
                            },
                            enabled = currentIdx > 0,
                            modifier = Modifier.size(26.dp)
                        ) {
                            Icon(Icons.Default.ChevronLeft, contentDescription = "Önceki Gün", modifier = Modifier.size(18.dp))
                        }
                        Text(
                            text = "${activeEpisode.dayNumber}. Gün",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onSurface
                        )
                        IconButton(
                            onClick = {
                                if (currentIdx in 0 until allEpisodes.lastIndex) {
                                    val next = allEpisodes[currentIdx + 1]
                                    activeEpisode = next
                                    useMobileWatchFallback = false
                                    onEpisodeChanged?.invoke(next)
                                }
                            },
                            enabled = currentIdx in 0 until allEpisodes.lastIndex,
                            modifier = Modifier.size(26.dp)
                        ) {
                            Icon(Icons.Default.ChevronRight, contentDescription = "Sonraki Gün", modifier = Modifier.size(18.dp))
                        }

                        // Opera-Style Ad Blocker quick toggle pill (synced with Settings)
                        Surface(
                            onClick = {
                                val nextVal = !adBlockerEnabled
                                UserAiPreferences.setVideoAdBlockerEnabled(context, nextVal)
                                Toast.makeText(
                                    context,
                                    if (nextVal) "Video reklam engelleyici açıldı" else "Video reklam engelleyici kapatıldı",
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
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Shield,
                                    contentDescription = "Reklam Engelleyici",
                                    tint = if (adBlockerEnabled) colorScheme.primary else colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = if (adBlockerEnabled) "Reklamsız" else "Reklam Açık",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (adBlockerEnabled) colorScheme.primary else colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Alternate Player toggle (Embed <-> Mobile Watch fallback if video forbids embed)
                        if (activeEpisode.videoId.length == 11) {
                            Surface(
                                onClick = { useMobileWatchFallback = !useMobileWatchFallback },
                                shape = RoundedCornerShape(8.dp),
                                color = colorScheme.surface,
                                border = BorderStroke(1.dp, colorScheme.outlineVariant)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SwapHoriz,
                                        contentDescription = "Oynatıcı Değiştir",
                                        tint = colorScheme.primary,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = if (useMobileWatchFallback) "Sade Mod" else "Tam Mod",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colorScheme.onSurface
                                    )
                                }
                            }
                        }

                        // Paste direct video link button
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
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Link,
                                    contentDescription = "Link Yapıştır",
                                    tint = colorScheme.primary,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = "Link",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colorScheme.onSurface
                                )
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
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Günü Tamamla",
                                        tint = Color(0xFF10B981),
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = "Bitti",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF10B981)
                                    )
                                }
                            }
                        }

                        // Direct YouTube external link button (jumps to current second)
                        Surface(
                            onClick = {
                                runCatching {
                                    val extIntent = Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse(activeVideoUrlWithTimestamp(livePlaybackSeconds))
                                    )
                                    context.startActivity(extIntent)
                                }
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = colorScheme.primary,
                            contentColor = colorScheme.onPrimary
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                    contentDescription = "YouTube'da Aç",
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = "YouTube",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // Optional quick alternative direct videos strip (when multiple matching lessons resolved)
                if (resolvedCandidates.size > 1) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp, vertical = 5.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        resolvedCandidates.forEachIndexed { idx, cand ->
                            val selected = cand.videoId == liveActiveVideoId
                            Surface(
                                onClick = {
                                    val updated = activeEpisode.copy(
                                        videoId = cand.videoId,
                                        customUrl = "https://www.youtube.com/watch?v=${cand.videoId}"
                                    )
                                    activeEpisode = updated
                                    liveActiveVideoId = cand.videoId
                                    liveVideoTitle = cand.title
                                    livePlaybackSeconds = 0
                                    useMobileWatchFallback = false
                                    onEpisodeChanged?.invoke(updated)
                                },
                                shape = RoundedCornerShape(8.dp),
                                color = if (selected) colorScheme.primaryContainer else colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                border = BorderStroke(
                                    1.dp,
                                    if (selected) colorScheme.primary else colorScheme.outlineVariant.copy(alpha = 0.4f)
                                )
                            ) {
                                Text(
                                    text = "${idx + 1}. ${cand.title.take(30)}${if (cand.durationLabel.isNotBlank()) " (${cand.durationLabel})" else ""}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 10.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selected) colorScheme.onPrimaryContainer else colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }

                // ── Lower Half: AI Tutor (with Math Panel & Live Timestamp Context) & Quick Notes ──
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Tab Switcher: AI'ya Anlık Sor | Hızlı Not Al
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val tabs = listOf(
                            "AI'ya Anlık Sor" to Icons.Default.AutoAwesome,
                            "Hızlı Not Al" to Icons.Default.EditNote
                        )
                        tabs.forEachIndexed { index, (label, icon) ->
                            val selected = activeTab == index
                            Surface(
                                onClick = { activeTab = index },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                color = if (selected) colorScheme.primaryContainer else colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                border = BorderStroke(
                                    1.dp,
                                    if (selected) colorScheme.primary else colorScheme.outlineVariant.copy(alpha = 0.4f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        tint = if (selected) colorScheme.primary else colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (selected) colorScheme.onPrimaryContainer else colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    if (activeTab == 0) {
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
                                        .padding(20.dp),
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
                                        text = "Ayarlar > Yapay Zeka & API bölümünden Video Stüdyosu AI özelliğini dilediğin zaman açabilirsin.",
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
                            // Live Video Timestamp Quick-Ask Chip + Contextual Prompt Chips
                            val liveTimeLabel = BookCampManager.formatSecondsAsTimestamp(livePlaybackSeconds)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    onClick = {
                                        val stampPrefix = if (livePlaybackSeconds > 0) {
                                            "$liveTimeLabel dakikasındaki formülü/adımı neden öyle yaptık? "
                                        } else {
                                            "4.5 dakikadaki formül neden öyle? "
                                        }
                                        aiQuestion = stampPrefix
                                    },
                                    shape = RoundedCornerShape(8.dp),
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
                                            text = if (livePlaybackSeconds > 0) "Şu Anı Sor ($liveTimeLabel)" else "Dakikadaki Adımı Sor",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = colorScheme.primary
                                        )
                                    }
                                }

                                listOf(
                                    "Bu videodaki formülleri adım adım çıkar",
                                    "Konunun kritik püf noktalarını özetle",
                                    "ÖSYM bu konudan nasıl sorar?",
                                    "Örnek soru ve adım adım çözümü"
                                ).forEach { chipPrompt ->
                                    Surface(
                                        onClick = {
                                            aiQuestion = chipPrompt
                                            triggerAiTutorQuestion(chipPrompt)
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        color = colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                        border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.4f))
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

                            // Collapsible Mathematics & Formula Symbol Panel (∑)
                            MathSymbolKeyboardPanel(
                                visible = showMathPanel,
                                onInsertSymbol = { sym ->
                                    aiQuestion = MathSymbolCatalog.insertSymbol(aiQuestion, sym)
                                }
                            )

                            // Question input row with ∑ Math Symbol Toggle
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
                                            overflow = TextOverflow.Ellipsis
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

                            // Structured Math & Step-by-Step AI Response Box
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                color = colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.35f))
                            ) {
                                if (aiAnswer.isBlank() && !isAiLoading) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "İzlediğin video ve aktif saniye (${liveTimeLabel}) AI'ya otomatik bağlanır. Anlamadığın formülü veya dakikayı doğrudan sorabilirsin.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colorScheme.onSurfaceVariant
                                        )
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
                                                horizontalArrangement = Arrangement.End
                                            ) {
                                                TextButton(
                                                    onClick = {
                                                        saveTextToNotes(
                                                            noteTitle = "$campTitle • ${activeEpisode.dayNumber}. Gün (${activeEpisode.topicTitle})",
                                                            bodyText = aiAnswer,
                                                            onDone = { aiSavedToNotes = true }
                                                        )
                                                    },
                                                    enabled = !aiSavedToNotes
                                                ) {
                                                    Icon(
                                                        imageVector = if (aiSavedToNotes) Icons.Default.CheckCircle else Icons.Default.EditNote,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(if (aiSavedToNotes) "Notlara Kaydedildi" else "Notlara Kaydet")
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // Tab 2: Quick Note Taking while watching (with timestamp stamp button)
                        OutlinedTextField(
                            value = quickNoteText,
                            onValueChange = {
                                quickNoteText = it
                                noteSavedSuccess = false
                            },
                            placeholder = {
                                Text("${activeEpisode.dayNumber}. Gün (${activeEpisode.topicTitle}) ders notlarını buraya yaz...")
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
                                        noteTitle = "$campTitle • ${activeEpisode.dayNumber}. Gün (${activeEpisode.topicTitle})",
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

/**
 * Lightweight, low-end device friendly YouTube WebView:
 * - Opera-style toggleable network + DOM/player ad-blocker (`adBlockerEnabled`)
 * - Live playback telemetry JS bridge (`SharkStudioBridge`) reporting current second, title, and URL to AI
 * - Cleanly stops, unloads, and destroys itself on dispose to protect low-RAM devices.
 */
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
 * Dialog for configuring a library book's Camp Binding (Catalog selection, YouTube playlist/video link,
 * AI daily topic slicing, and 1-tap distribution into the weekly plan).
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
    var totalDaysText by remember(initialBinding) { mutableStateOf(initialBinding.totalDays.toString()) }
    var currentDayText by remember(initialBinding) { mutableStateOf(initialBinding.currentDay.toString()) }
    var youtubeUrl by remember(initialBinding) { mutableStateOf(initialBinding.youtubeUrl) }
    var customTopics by remember(initialBinding) {
        mutableStateOf(initialBinding.episodes.map { it.topicTitle })
    }
    var isAiSlicing by remember { mutableStateOf(false) }
    var isDistributing by remember { mutableStateOf(false) }
    var statusNotice by remember { mutableStateOf<String?>(null) }

    val matchingCatalog = remember(resource.title, resource.subject) {
        val tr = java.util.Locale("tr")
        val titleLower = resource.title.lowercase(tr)
        val subjLower = resource.subject.lowercase(tr)
        val matched = BookCampManager.BuiltInCampCatalog.filter { tpl ->
            val tplSubj = tpl.subject.lowercase(tr)
            tplSubj.contains(subjLower) ||
                subjLower.contains(tplSubj.substringAfter(" ")) ||
                tpl.matchKeywords.any { kw -> titleLower.contains(kw.lowercase(tr)) }
        }.sortedByDescending { tpl ->
            tpl.matchKeywords.any { kw -> titleLower.contains(kw.lowercase(tr)) }
        }
        matched.ifEmpty { BookCampManager.BuiltInCampCatalog.take(6) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = "Kitap Kampı & Günlük Planlayıcı",
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
                Text(
                    text = "Hazır Kamp Kataloğundan Seç",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    matchingCatalog.forEach { tpl ->
                        val isSelected = campTitle.equals(tpl.title, ignoreCase = true)
                        Surface(
                            onClick = {
                                campTitle = tpl.title
                                instructor = tpl.instructor
                                totalDaysText = tpl.totalDays.toString()
                                customTopics = tpl.dayTopics
                                statusNotice = "${tpl.title} (${tpl.totalDays} Gün) seçildi."
                            },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) colorScheme.primaryContainer else colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) colorScheme.primary else colorScheme.outlineVariant
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                            ) {
                                Text(
                                    text = tpl.title,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) colorScheme.onPrimaryContainer else colorScheme.onSurface
                                )
                                Text(
                                    text = "${tpl.instructor} • ${tpl.totalDays} Gün",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colorScheme.primary
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = campTitle,
                    onValueChange = { campTitle = it },
                    label = { Text("Kamp Adı") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = instructor,
                        onValueChange = { instructor = it },
                        label = { Text("Eğitmen / Kanal") },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = totalDaysText,
                        onValueChange = { totalDaysText = it.filter { c -> c.isDigit() } },
                        label = { Text("Toplam Gün") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.width(100.dp)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = currentDayText,
                        onValueChange = { currentDayText = it.filter { c -> c.isDigit() } },
                        label = { Text("Aktif Gün") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.width(105.dp)
                    )
                    OutlinedTextField(
                        value = youtubeUrl,
                        onValueChange = { youtubeUrl = it },
                        label = { Text("YouTube Playlist / Video Linki (Opsiyonel)") },
                        placeholder = { Text("https://youtube.com/playlist?list=...") },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    )
                }

                // AI Daily Slicer + Distribute to Weekly Plan buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            if (isAiSlicing) return@OutlinedButton
                            isAiSlicing = true
                            val days = (totalDaysText.toIntOrNull() ?: 30).coerceIn(3, 90)
                            scope.launch {
                                val sliced = BookCampManager.generateDailyCampTopicsWithAi(
                                    context = context,
                                    bookTitle = campTitle.ifBlank { resource.title },
                                    subject = resource.subject,
                                    instructor = instructor,
                                    totalDays = days
                                )
                                customTopics = sliced
                                isAiSlicing = false
                                statusNotice = "Kamp $days günlük konulara ayrıldı."
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        if (isAiSlicing) {
                            CircularProgressIndicator(modifier = Modifier.size(15.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(15.dp))
                        }
                        Spacer(modifier = Modifier.width(5.dp))
                        Text("AI ile Günlere Böl", fontSize = 11.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            if (isDistributing) return@OutlinedButton
                            isDistributing = true
                            val days = (totalDaysText.toIntOrNull() ?: 30).coerceIn(1, 120)
                            val curDay = (currentDayText.toIntOrNull() ?: 1).coerceIn(1, days)
                            val saved = BookCampManager.saveResourceCampBinding(
                                context = context,
                                resource = resource,
                                campTitle = campTitle,
                                instructor = instructor,
                                totalDays = days,
                                currentDay = curDay,
                                youtubeUrl = youtubeUrl,
                                customDayTopics = customTopics
                            )
                            onSaved(saved)
                            scope.launch {
                                val added = BookCampManager.distributeCampToWeeklyPlan(context, saved)
                                isDistributing = false
                                statusNotice = "$added kamp günü haftalık plana eklendi."
                                Toast.makeText(context, "$added kamp günü haftalık plana eklendi", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(5.dp))
                        Text("Plana Dağıt (7 Gün)", fontSize = 11.sp)
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

                // Preview of upcoming 5 camp days
                val previewDays = (totalDaysText.toIntOrNull() ?: 30).coerceIn(1, 120)
                val startDay = (currentDayText.toIntOrNull() ?: 1).coerceIn(1, previewDays)
                HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.5f))
                Text(
                    text = "Yaklaşan Kamp Günleri",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.onSurfaceVariant
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (d in startDay..minOf(previewDays, startDay + 4)) {
                        val topic = customTopics.getOrElse(d - 1) { "${resource.subject} • $d. Gün" }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                .clickable { currentDayText = d.toString() }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "$d. Gün • $topic",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (d == startDay) colorScheme.primary else colorScheme.onSurface,
                                fontWeight = if (d == startDay) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            if (d == startDay) {
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
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        val days = (totalDaysText.toIntOrNull() ?: 30).coerceIn(1, 120)
                        val curDay = (currentDayText.toIntOrNull() ?: 1).coerceIn(1, days)
                        val saved = BookCampManager.saveResourceCampBinding(
                            context = context,
                            resource = resource,
                            campTitle = campTitle,
                            instructor = instructor,
                            totalDays = days,
                            currentDay = curDay,
                            youtubeUrl = youtubeUrl,
                            customDayTopics = customTopics
                        )
                        onSaved(saved)
                        onOpenStudio(saved)
                    },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.PlayCircleOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Stüdyoda İzle")
                }

                Button(
                    onClick = {
                        val days = (totalDaysText.toIntOrNull() ?: 30).coerceIn(1, 120)
                        val curDay = (currentDayText.toIntOrNull() ?: 1).coerceIn(1, days)
                        val saved = BookCampManager.saveResourceCampBinding(
                            context = context,
                            resource = resource,
                            campTitle = campTitle,
                            instructor = instructor,
                            totalDays = days,
                            currentDay = curDay,
                            youtubeUrl = youtubeUrl,
                            customDayTopics = customTopics
                        )
                        onSaved(saved)
                        onDismiss()
                    },
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
