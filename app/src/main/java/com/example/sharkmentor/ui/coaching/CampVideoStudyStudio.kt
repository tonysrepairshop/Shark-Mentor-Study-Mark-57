package com.example.sharkmentor.ui.coaching

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Headphones
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
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.sharkmentor.core.ai.MultiProviderAiClient
import com.example.sharkmentor.core.ai.UserAiPreferences
import com.example.sharkmentor.core.coaching.BookCampManager
import com.example.sharkmentor.core.coaching.BookVideoCandidate
import com.example.sharkmentor.core.coaching.CampDayEpisode
import com.example.sharkmentor.core.coaching.CoachingEngine
import com.example.sharkmentor.core.coaching.DossierAiChannel
import com.example.sharkmentor.core.coaching.ResourceCampBinding
import com.example.sharkmentor.core.coaching.StudentDossierManager
import com.example.sharkmentor.core.service.BackgroundPlayableWebView
import com.example.sharkmentor.core.service.CampVideoPlaybackController
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
import com.example.sharkmentor.ui.components.WellnessReminderBus
import com.example.sharkmentor.ui.components.WellnessReminderSmartwatchOverlay
import com.example.sharkmentor.ui.components.WellnessReminderType
import com.example.sharkmentor.ui.components.formatMathAsPlainText
import java.io.ByteArrayInputStream
import java.util.UUID
import kotlin.math.roundToInt
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
 * Mark 63 Redesigned Video Study Studio:
 * - Full-area distraction-free YouTube video player cleanly bounded below a slim header (NO cramped split-screen chat).
 * - True WebView Fullscreen (`WebChromeClient.onShowCustomView` / `onHideCustomView`) + immersive mode toggle.
 * - Always-on-top draggable Floating AI Orb (matching PdfReaderActivity physics) visible in BOTH normal & fullscreen modes.
 * - Tapping the AI Orb opens/closes a compact floating AI Chat / Quick Note / Episodes popup card right next to the orb.
 * - Auto-pauses video playback when a Posture Wellness Reminder fires; displays the smartwatch overlay seamlessly.
 */
@Composable
fun CampVideoStudyStudioDialog(
    campTitle: String,
    subject: String,
    initialEpisode: CampDayEpisode,
    allEpisodes: List<CampDayEpisode> = listOf(initialEpisode),
    resourceId: Long? = null,
    onEpisodeChanged: ((CampDayEpisode) -> Unit)? = null,
    onDeleteEpisode: ((CampDayEpisode) -> Unit)? = null,
    onMarkCompleted: ((CampDayEpisode) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    LaunchedEffect(initialEpisode.dayNumber, initialEpisode.videoId, campTitle) {
        CampVideoPlayerBridge.launch(
            context = context,
            request = CampVideoPlayerBridge.LaunchRequest(
                campTitle = campTitle,
                subject = subject,
                initialEpisode = initialEpisode,
                allEpisodes = allEpisodes,
                resourceId = resourceId,
                onEpisodeChanged = onEpisodeChanged,
                onDeleteEpisode = onDeleteEpisode,
                onMarkCompleted = onMarkCompleted
            )
        )
        onDismiss()
    }
}

@Composable
fun CampVideoStudyStudioContent(
    campTitle: String,
    subject: String,
    initialEpisode: CampDayEpisode,
    allEpisodes: List<CampDayEpisode> = listOf(initialEpisode),
    resourceId: Long? = null,
    onEpisodeChanged: ((CampDayEpisode) -> Unit)? = null,
    onDeleteEpisode: ((CampDayEpisode) -> Unit)? = null,
    onMarkCompleted: ((CampDayEpisode) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colorScheme = MaterialTheme.colorScheme
    val density = LocalDensity.current

    LaunchedEffect(context) {
        UserAiPreferences.initialize(context)
        StudentDossierManager.ensureLoaded(context)
    }
    val studioAiEnabled by UserAiPreferences.videoStudioAiEnabled.collectAsStateWithLifecycle()
    val adBlockerEnabled by UserAiPreferences.videoAdBlockerEnabled.collectAsStateWithLifecycle()
    val wellnessEvent by WellnessReminderBus.activeEvent.collectAsStateWithLifecycle()
    val bgPlaybackState by CampVideoPlaybackController.state.collectAsStateWithLifecycle()

    var episodeListState by remember(allEpisodes, initialEpisode) {
        mutableStateOf(allEpisodes.ifEmpty { listOf(initialEpisode) })
    }
    val safeEpisodes = episodeListState
    var activeEpisode by remember(initialEpisode.dayNumber, initialEpisode.videoId, initialEpisode.topicTitle) {
        mutableStateOf(initialEpisode)
    }
    var pendingDeleteStudioEpisode by remember { mutableStateOf<CampDayEpisode?>(null) }
    var isMinimizingToBackground by remember { mutableStateOf(false) }
    var sawSessionActive by remember { mutableStateOf(false) }

    LaunchedEffect(activeEpisode.dayNumber, activeEpisode.videoId, activeEpisode.topicTitle, campTitle, safeEpisodes) {
        CampVideoPlaybackController.registerStudioPayload(
            hostContext = context,
            campTitle = campTitle,
            subject = subject,
            activeEpisode = activeEpisode,
            allEpisodes = safeEpisodes,
            resourceId = resourceId
        )
        CampVideoPlaybackController.markRestoredToForeground(context)
        CampVideoPlaybackController.startOrUpdateSession(
            context = context,
            title = "${activeEpisode.dayNumber}. Ders • ${activeEpisode.topicTitle}",
            subtitle = campTitle.ifBlank { subject.ifBlank { "SharkPlayer" } },
            url = activeEpisode.buildInAppEmbedUrl(adBlockerEnabled = adBlockerEnabled, autoplay = true),
            isPlaying = true
        )
    }

    // Only dismiss if the media session was genuinely active and then explicitly stopped (e.g. "Kapat" in notification)
    LaunchedEffect(bgPlaybackState.isActive) {
        if (bgPlaybackState.isActive) {
            sawSessionActive = true
        } else if (sawSessionActive && !isMinimizingToBackground) {
            onDismiss()
        }
    }

    val isInPipMode by CampVideoPlaybackController.isInPipMode.collectAsStateWithLifecycle()

    DisposableEffect(Unit) {
        CampVideoPlaybackController.setStudioOpen(true)
        onDispose {
            CampVideoPlaybackController.setStudioOpen(false)
            val stillInPip = CampVideoPlaybackController.isInPipMode.value
            if (!isMinimizingToBackground && !stillInPip) {
                CampVideoPlaybackController.stopAndRelease(context)
            }
        }
    }

    // Floating AI Orb & Popup state (0 = AI Asistan, 1 = Hızlı Not, 2 = Bölümler)
    var isAiPopupOpen by remember { mutableStateOf(false) }
    var popupTab by remember(studioAiEnabled) { mutableIntStateOf(if (studioAiEnabled) 0 else 1) }

    // True Fullscreen state (supports both YouTube native fullscreen button AND our top-bar fullscreen toggle)
    var customFullscreenView by remember { mutableStateOf<View?>(null) }
    var customFullscreenCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }
    var manualFullscreen by remember { mutableStateOf(false) }
    val isInFullscreen = customFullscreenView != null || manualFullscreen || isInPipMode

    // Exit YouTube native custom fullscreen cleanly when entering PiP so the video surface returns into the WebView
    LaunchedEffect(isInPipMode) {
        if (isInPipMode) {
            if (customFullscreenView != null || customFullscreenCallback != null) {
                runCatching { customFullscreenCallback?.onCustomViewHidden() }
                customFullscreenView = null
                customFullscreenCallback = null
            }
            manualFullscreen = false
        }
    }


    // Direct WebView handle for programmatic pause on Posture Reminder & AI Popup Open
    var activeWebViewRef by remember { mutableStateOf<WebView?>(null) }
    var wasPlayingBeforeAiPopup by remember { mutableStateOf(false) }

    // Isolated live playback telemetry (StateFlow so ticks NEVER recompose root WebView or AI Popup!)
    val livePlaybackSecondsFlow = remember { kotlinx.coroutines.flow.MutableStateFlow(0) }
    val liveVideoTitleRef = remember { java.util.concurrent.atomic.AtomicReference(activeEpisode.topicTitle) }
    val liveActiveVideoIdRef = remember { java.util.concurrent.atomic.AtomicReference(activeEpisode.videoId) }

    // Auto-pause video when AI Popup opens (frees low-end GPU/MediaCodec AND prevents missing the lesson!)
    LaunchedEffect(isAiPopupOpen) {
        val wv = activeWebViewRef ?: return@LaunchedEffect
        if (isAiPopupOpen) {
            runCatching {
                wv.evaluateJavascript(
                    """
                    (function(){
                      try {
                        var v = document.querySelector('video');
                        var playing = false;
                        if (v) {
                          playing = !v.paused && !v.ended;
                          v.pause();
                          if (window.SharkStudioBridge && !isNaN(v.currentTime)) {
                            var dur = (isFinite(v.duration) && v.duration > 0) ? Math.floor(v.duration) : 0;
                            window.SharkStudioBridge.onTelemetry(Math.floor(v.currentTime), dur, false, document.title || '', window.location.href || '');
                          }
                        }
                        return playing ? '1' : '0';
                      } catch(e) { return '0'; }
                    })();
                    """.trimIndent()
                ) { res ->
                    wasPlayingBeforeAiPopup = res?.contains("1") == true
                }
            }
        } else if (wasPlayingBeforeAiPopup) {
            wasPlayingBeforeAiPopup = false
            runCatching {
                wv.evaluateJavascript(
                    "(function(){try{var v=document.querySelector('video');if(v&&v.paused){v.play();}}catch(e){}})();",
                    null
                )
            }
        }
    }

    // Auto-pause video when Posture Reminder fires (NEVER touches Focus Timer!)
    LaunchedEffect(wellnessEvent?.id) {
        val ev = wellnessEvent
        if (ev != null && ev.type == WellnessReminderType.POSTURE) {
            runCatching {
                activeWebViewRef?.evaluateJavascript(
                    "(function(){try{document.querySelectorAll('video').forEach(function(v){v.pause();});}catch(e){}})();",
                    null
                )
            }
        }
    }

    var isResolvingVideo by remember(activeEpisode.dayNumber, activeEpisode.videoId) {
        mutableStateOf(activeEpisode.videoId.length != 11)
    }
    var useMobileWatchFallback by remember(activeEpisode.dayNumber, activeEpisode.videoId) {
        mutableStateOf(false)
    }

    LaunchedEffect(activeEpisode.dayNumber, activeEpisode.videoId, activeEpisode.playlistId, activeEpisode.searchQuery) {
        livePlaybackSecondsFlow.value = 0
        if (activeEpisode.videoId.length == 11) {
            liveActiveVideoIdRef.set(activeEpisode.videoId)
            liveVideoTitleRef.set(activeEpisode.topicTitle)
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
            liveActiveVideoIdRef.set(best.videoId)
            liveVideoTitleRef.set(best.title.ifBlank { activeEpisode.topicTitle })
            onEpisodeChanged?.invoke(updated)
        }
        isResolvingVideo = false
    }

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

    fun exitFullscreen() {
        customFullscreenCallback?.onCustomViewHidden()
        customFullscreenView = null
        customFullscreenCallback = null
        manualFullscreen = false
    }

    fun selectEpisode(ep: CampDayEpisode) {
        activeEpisode = ep
        liveActiveVideoIdRef.set(ep.videoId)
        liveVideoTitleRef.set(ep.topicTitle)
        livePlaybackSecondsFlow.value = 0
        useMobileWatchFallback = false
        onEpisodeChanged?.invoke(ep)
    }

    fun activeVideoUrlWithTimestamp(targetSec: Int = livePlaybackSecondsFlow.value): String {
        val vid = liveActiveVideoIdRef.get().orEmpty().ifBlank { activeEpisode.videoId }
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
            val dossierBlock = if (StudentDossierManager.shouldAttachDossier(DossierAiChannel.COACHING_DIRECT)) {
                StudentDossierManager.buildAnonymizedDossierPrompt()
            } else {
                CoachingEngine.buildAiSystemCompassPrompt(context)
            }
            val mentionedSec = BookCampManager.extractMentionedTimestampSeconds(cleanQ)
            val effectiveSec = mentionedSec ?: livePlaybackSecondsFlow.value
            val formattedTime = BookCampManager.formatSecondsAsTimestamp(effectiveSec)
            val currentVid = liveActiveVideoIdRef.get().orEmpty().ifBlank { activeEpisode.videoId }
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
• Format: Soru veya formül çözerken mutlaka Adım 1, Adım 2 şeklinde numaralandır, matematik/fen sembollerini temiz yaz ve son satırda **Sonuç:** ile net cevabı ver.
                • Ders: $subject
                • Bölüm / Konu: ${activeEpisode.dayNumber}. Ders - ${activeEpisode.topicTitle}
                • Video Başlığı: ${liveVideoTitleRef.get().orEmpty().ifBlank { activeEpisode.topicTitle }}
                • Aktif Video Linki: $watchUrl
                • Odaklanılan Video Zamanı: $formattedTime (${effectiveSec}. saniye)

                $transcriptSection

                Öğrencinin sorusu: "$cleanQ"
                Öğrencinin belirttiği dakika/formül veya kavramı doğrudan, adım adım, sade ve eksiksiz açıkla.
            """.trimIndent()

            val systemInstruction = "$dossierBlock\n\n$STUDIO_MATH_FORMAT_RULES"

            var lastEmitMs = 0L
            val response = withContext(Dispatchers.IO) {
                runCatching {
                    MultiProviderAiClient.executeUnifiedTextPrompt(
                        context = context,
                        systemPrompt = systemInstruction,
                        userPrompt = fullPrompt,
                        temperature = 0.25f,
                        maxTokens = 4096,
                        onPartialText = { partial ->
                            val now = System.currentTimeMillis()
                            if (partial.isNotBlank() && (lastEmitMs == 0L || now - lastEmitMs >= 110L)) {
                                lastEmitMs = now
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

    pendingDeleteStudioEpisode?.let { epToDelete ->
        AlertDialog(
            onDismissRequest = { pendingDeleteStudioEpisode = null },
            containerColor = colorScheme.surface,
            title = {
                Text(
                    text = "Videoyu Sil",
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.onSurface
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "\"${epToDelete.topicTitle}\" oynatma listesinden kaldırılsın mı?",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colorScheme.onSurface
                    )
                    Text(
                        text = "Listeyi tekrardan içe aktararak videoyu yeniden ekleyebilirsiniz.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val updatedList = BookCampManager.removeEpisodeAndReindex(
                            episodes = episodeListState,
                            targetDayNumber = epToDelete.dayNumber,
                            targetVideoId = epToDelete.videoId
                        )
                        resourceId?.let { rId ->
                            BookCampManager.removeEpisodeFromBinding(context, rId, epToDelete)
                        }
                        onDeleteEpisode?.invoke(epToDelete)
                        pendingDeleteStudioEpisode = null
                        if (updatedList.isEmpty()) {
                            onDismiss()
                        } else {
                            episodeListState = updatedList
                            if (activeEpisode.dayNumber == epToDelete.dayNumber) {
                                val nextIdx = (epToDelete.dayNumber - 1).coerceIn(0, updatedList.lastIndex)
                                selectEpisode(updatedList[nextIdx])
                            } else {
                                val remapped = updatedList.firstOrNull {
                                    (activeEpisode.videoId.isNotBlank() && it.videoId == activeEpisode.videoId) ||
                                        it.topicTitle == activeEpisode.topicTitle
                                } ?: updatedList.first()
                                activeEpisode = remapped
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colorScheme.error,
                        contentColor = colorScheme.onError
                    )
                ) {
                    Text("Sil", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteStudioEpisode = null }) {
                    Text("Vazgeç")
                }
            }
        )
    }

    androidx.activity.compose.BackHandler {
        if (isInFullscreen) {
            exitFullscreen()
        } else if (isAiPopupOpen) {
            isAiPopupOpen = false
        } else {
            onDismiss()
        }
    }
    Box(modifier = Modifier.fillMaxSize()) {
        // Immersive system bars toggle when in true Fullscreen
        val dialogView = LocalView.current
        DisposableEffect(isInFullscreen) {
            val window = (dialogView.context as? Activity)?.window
            if (window != null) {
                val controller = WindowCompat.getInsetsController(window, dialogView)
                if (isInFullscreen) {
                    controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    controller.hide(WindowInsetsCompat.Type.systemBars())
                } else {
                    controller.show(WindowInsetsCompat.Type.systemBars())
                }
            }
            onDispose {
                val w = (dialogView.context as? Activity)?.window
                if (w != null) {
                    WindowCompat.getInsetsController(w, dialogView).show(WindowInsetsCompat.Type.systemBars())
                }
            }
        }

        BackHandler(enabled = isInFullscreen || isAiPopupOpen) {
            if (isAiPopupOpen) {
                isAiPopupOpen = false
            } else if (isInFullscreen) {
                exitFullscreen()
            }
        }

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = if (isInFullscreen) Color.Black else colorScheme.background,
            contentColor = colorScheme.onBackground
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val containerWidthPx = with(density) { maxWidth.toPx() }
                val containerHeightPx = with(density) { maxHeight.toPx() }
                val orbSizeDp = 54.dp
                val orbSizePx = with(density) { orbSizeDp.toPx() }
                val edgePadPx = with(density) { 12.dp.toPx() }

                var orbOffsetX by remember { mutableFloatStateOf(-1f) }
                var orbOffsetY by remember { mutableFloatStateOf(-1f) }

                // Initialize floating AI orb at comfortable bottom-right spot
                LaunchedEffect(containerWidthPx, containerHeightPx) {
                    if (containerWidthPx > orbSizePx && containerHeightPx > orbSizePx) {
                        if (orbOffsetX < 0f || orbOffsetY < 0f) {
                            orbOffsetX = (containerWidthPx - orbSizePx - edgePadPx).coerceAtLeast(edgePadPx)
                            orbOffsetY = (containerHeightPx - orbSizePx - with(density) { 84.dp.toPx() })
                                .coerceIn(edgePadPx, (containerHeightPx - orbSizePx - edgePadPx).coerceAtLeast(edgePadPx))
                        } else {
                            orbOffsetX = orbOffsetX.coerceIn(edgePadPx, (containerWidthPx - orbSizePx - edgePadPx).coerceAtLeast(edgePadPx))
                            orbOffsetY = orbOffsetY.coerceIn(edgePadPx, (containerHeightPx - orbSizePx - edgePadPx).coerceAtLeast(edgePadPx))
                        }
                    }
                }

                // ── LAYER 1: Main Full-Area Video Player Layout ──
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            if (!isInFullscreen) Modifier.statusBarsPadding().navigationBarsPadding()
                            else Modifier
                        )
                ) {
                    // Slim Top Bar (hidden cleanly in Fullscreen)
                    if (!isInFullscreen) {
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
                                        val isLightVidSurface = colorScheme.surface.luminance() > 0.5f
                                        val playerNavy = if (isLightVidSurface) Color(0xFF1E3A8A) else Color(0xFF60A5FA)
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = buildAnnotatedString {
                                                    withStyle(SpanStyle(color = colorScheme.onSurface, fontWeight = FontWeight.Bold)) {
                                                        append("Shark")
                                                    }
                                                    withStyle(
                                                        SpanStyle(
                                                            color = playerNavy,
                                                            fontFamily = FontFamily.SansSerif,
                                                            fontWeight = FontWeight.ExtraBold,
                                                            letterSpacing = 0.4.sp
                                                        )
                                                    ) {
                                                        append("Player")
                                                    }
                                                },
                                                style = MaterialTheme.typography.titleSmall,
                                                maxLines = 1
                                            )
                                            Text(
                                                text = "• ${activeEpisode.dayNumber}. Ders",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = colorScheme.onSurfaceVariant,
                                                maxLines = 1
                                            )
                                        }
                                        Text(
                                            text = "${activeEpisode.topicTitle} • $campTitle",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = colorScheme.primary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(6.dp))

                                // Compact Isolated Live Focus Timer Pill (ticks without recomposing WebView/AI!)
                                StudioFocusTimerPill(
                                    subject = subject,
                                    activeEpisode = activeEpisode
                                )

                                IconButton(
                                    onClick = { manualFullscreen = true },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Fullscreen,
                                        contentDescription = "Tam Ekran",
                                        tint = colorScheme.onSurface,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }

                        // Slim Controls Strip (Prev/Next + Episodes + AdBlock + Player Mode + Link + Done + YouTube)
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
                                    onClick = { if (currentIdx > 0) selectEpisode(safeEpisodes[currentIdx - 1]) },
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
                                    onClick = { if (currentIdx in 0 until safeEpisodes.lastIndex) selectEpisode(safeEpisodes[currentIdx + 1]) },
                                    enabled = currentIdx in 0 until safeEpisodes.lastIndex,
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Default.ChevronRight, contentDescription = "Sonraki Ders", modifier = Modifier.size(18.dp))
                                }

                                if (safeEpisodes.size > 1) {
                                    Surface(
                                        onClick = {
                                            popupTab = 2
                                            isAiPopupOpen = true
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
                                            Icon(Icons.Default.PlaylistPlay, contentDescription = "Bölümler", tint = colorScheme.primary, modifier = Modifier.size(13.dp))
                                            Text("Bölümler", style = MaterialTheme.typography.labelSmall, fontSize = 10.sp, color = colorScheme.onSurface)
                                        }
                                    }
                                }

                                // Ad Blocker toggle
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
                                    border = BorderStroke(1.dp, if (adBlockerEnabled) colorScheme.primary.copy(alpha = 0.5f) else colorScheme.outlineVariant)
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
                                            Icon(Icons.Default.SwapHoriz, contentDescription = "Oynatıcı", tint = colorScheme.primary, modifier = Modifier.size(12.dp))
                                            Text(if (useMobileWatchFallback) "Sade" else "Tam", style = MaterialTheme.typography.labelSmall, fontSize = 10.sp, color = colorScheme.onSurface)
                                        }
                                    }
                                }

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
                                                Intent(Intent.ACTION_VIEW, Uri.parse(activeVideoUrlWithTimestamp(livePlaybackSecondsFlow.value)))
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
                    }

                    // Full-Area Video Player Viewport (fills remaining screen cleanly!)
                    val playerUrl = remember(activeEpisode.videoId, activeEpisode.playlistId, activeEpisode.dayNumber, adBlockerEnabled, useMobileWatchFallback) {
                        if (useMobileWatchFallback && activeEpisode.videoId.length == 11) {
                            activeEpisode.buildMobileWatchUrl(livePlaybackSecondsFlow.value)
                        } else {
                            activeEpisode.buildInAppEmbedUrl(adBlockerEnabled = adBlockerEnabled, autoplay = true)
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
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
                                    modifier = Modifier.size(24.dp),
                                    color = colorScheme.primary,
                                    strokeWidth = 2.5.dp
                                )
                                Text(
                                    text = "Ders videosu hazırlanıyor...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.9f)
                                )
                            }
                        } else {
                            DistractionFreeYouTubeWebView(
                                url = playerUrl,
                                adBlockerEnabled = adBlockerEnabled,
                                onWebViewCreated = { activeWebViewRef = it },
                                onPlaybackTelemetry = { seconds, durationSeconds, isPlayingNow, title, currentUrl ->
                                    if (seconds >= 0) livePlaybackSecondsFlow.value = seconds
                                    if (title.isNotBlank() && !title.equals("YouTube", ignoreCase = true)) {
                                        liveVideoTitleRef.set(title.removeSuffix(" - YouTube").trim())
                                    }
                                    val (parsedVid, _) = BookCampManager.parseYouTubeUrlIds(currentUrl)
                                    if (parsedVid.length == 11) {
                                        liveActiveVideoIdRef.set(parsedVid)
                                    }
                                    CampVideoPlaybackController.onPlaybackTelemetry(
                                        context = context,
                                        isPlaying = isPlayingNow,
                                        positionSeconds = seconds.coerceAtLeast(0),
                                        durationSeconds = durationSeconds.coerceAtLeast(0),
                                        resolvedTitle = title
                                    )
                                },
                                onEmbedRestricted = {
                                    if (!useMobileWatchFallback && activeEpisode.videoId.length == 11) {
                                        useMobileWatchFallback = true
                                    }
                                },
                                onEnterCustomFullscreen = { view, callback ->
                                    customFullscreenView = view
                                    customFullscreenCallback = callback
                                },
                                onExitCustomFullscreen = {
                                    customFullscreenView = null
                                    customFullscreenCallback = null
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }

                // ── LAYER 2: Real WebView Native Fullscreen Custom View (fills 100% of screen!) ──
                customFullscreenView?.let { fsView ->
                    AndroidView(
                        factory = { ctx ->
                            FrameLayout(ctx).apply {
                                setBackgroundColor(android.graphics.Color.BLACK)
                                (fsView.parent as? ViewGroup)?.removeView(fsView)
                                addView(
                                    fsView,
                                    FrameLayout.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT
                                    )
                                )
                            }
                        },
                        update = { container ->
                            if (fsView.parent !== container) {
                                (fsView.parent as? ViewGroup)?.removeView(fsView)
                                container.removeAllViews()
                                container.addView(
                                    fsView,
                                    FrameLayout.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT
                                    )
                                )
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Discreet Exit Fullscreen pill (top-left) whenever in fullscreen
                if (isInFullscreen) {
                    Surface(
                        onClick = { exitFullscreen() },
                        shape = RoundedCornerShape(50),
                        color = Color.Black.copy(alpha = 0.72f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.28f)),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FullscreenExit,
                                contentDescription = "Tam Ekrandan Çık",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Çık",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }

                // ── LAYER 3: Zero-Lag Opaque Floating AI Chat / Note / Episodes Popup (auto-pauses video!) ──
                val popupAlignment = if (orbOffsetX > containerWidthPx * 0.45f) {
                    if (orbOffsetY > containerHeightPx * 0.45f) Alignment.BottomEnd else Alignment.TopEnd
                } else {
                    if (orbOffsetY > containerHeightPx * 0.45f) Alignment.BottomStart else Alignment.TopStart
                }

                if (isAiPopupOpen) {
                    Surface(
                        modifier = Modifier
                            .align(popupAlignment)
                            .imePadding()
                            .padding(
                                start = 12.dp,
                                end = 12.dp,
                                top = if (isInFullscreen) 56.dp else 64.dp,
                                bottom = 78.dp
                            )
                            .widthIn(min = 280.dp, max = 380.dp)
                            .fillMaxWidth(0.92f)
                            .heightIn(min = 240.dp, max = (maxHeight * 0.62f).coerceAtLeast(280.dp)),
                        shape = RoundedCornerShape(20.dp),
                        color = colorScheme.surface,
                        border = BorderStroke(1.2.dp, colorScheme.primary.copy(alpha = 0.45f)),
                        shadowElevation = 2.dp,
                        tonalElevation = 0.dp
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Compact Popup Header
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        tint = colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Stüdyo AI",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = colorScheme.onSurface
                                    )
                                    StudioLiveTimestampBadge(secondsFlow = livePlaybackSecondsFlow)
                                }
                                IconButton(
                                    onClick = { isAiPopupOpen = false },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Kapat", modifier = Modifier.size(17.dp))
                                }
                            }

                            // Mini Tab Selector (AI | Not | Bölümler)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val miniTabs = buildList {
                                    add(Triple(0, "SharkAI", Icons.Default.AutoAwesome))
                                    add(Triple(1, "Hızlı Not", Icons.Default.EditNote))
                                    if (safeEpisodes.size > 1) add(Triple(2, "Bölümler", Icons.Default.PlaylistPlay))
                                }
                                miniTabs.forEach { (id, label, icon) ->
                                    val sel = popupTab == id
                                    Surface(
                                        onClick = { popupTab = id },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (sel) colorScheme.primaryContainer else colorScheme.surfaceVariant,
                                        border = BorderStroke(1.dp, if (sel) colorScheme.primary else colorScheme.outlineVariant)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(vertical = 5.dp, horizontal = 5.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center
                                        ) {
                                            Icon(icon, contentDescription = null, tint = if (sel) colorScheme.primary else colorScheme.onSurfaceVariant, modifier = Modifier.size(13.dp))
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text(
                                                text = label,
                                                fontSize = 10.sp,
                                                fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium,
                                                color = if (sel) colorScheme.onPrimaryContainer else colorScheme.onSurfaceVariant,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }

                            // Popup Body Content
                            when (popupTab) {
                                0 -> {
                                    if (!studioAiEnabled) {
                                        Box(
                                            modifier = Modifier.weight(1f).fillMaxWidth(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Text("SharkPlayer • SharkAI Kapalı", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                                OutlinedButton(
                                                    onClick = { UserAiPreferences.setVideoStudioAiEnabled(context, true) },
                                                    shape = RoundedCornerShape(10.dp)
                                                ) {
                                                    Text("Stüdyo AI'yı Aç", fontSize = 12.sp)
                                                }
                                            }
                                        }
                                    } else {
                                        // Scrollable AI Answer Box
                                        Surface(
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxWidth(),
                                            shape = RoundedCornerShape(12.dp),
                                            color = colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                            border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.4f))
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .padding(10.dp)
                                            ) {
                                                when {
                                                    isAiLoading && aiAnswer.isBlank() -> {
                                                        Row(
                                                            modifier = Modifier.align(Alignment.Center),
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                        ) {
                                                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                                            Text("AI yanıt hazırlıyor...", style = MaterialTheme.typography.bodySmall)
                                                        }
                                                    }
                                                    aiAnswer.isBlank() -> {
                                                        Column(
                                                            modifier = Modifier.align(Alignment.Center),
                                                            horizontalAlignment = Alignment.CenterHorizontally,
                                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                                        ) {
                                                            Text(
                                                                text = "Video senin için duraklatıldı. Takıldığın dakikayı veya kavramı sorabilirsin.",
                                                                style = MaterialTheme.typography.bodySmall,
                                                                color = colorScheme.onSurfaceVariant
                                                            )
                                                            StudioExplainNowChip(
                                                                secondsFlow = livePlaybackSecondsFlow,
                                                                onExplain = { q ->
                                                                    aiQuestion = q
                                                                    triggerAiTutorQuestion(q)
                                                                }
                                                            )
                                                        }
                                                    }
                                                    else -> {
                                                        Column(
                                                            modifier = Modifier
                                                                .fillMaxSize()
                                                                .verticalScroll(rememberScrollState()),
                                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                                        ) {
                                                            SelectionContainer {
                                                                RichSolutionContent(
                                                                    text = aiAnswer,
                                                                    textColor = colorScheme.onSurface,
                                                                    accentColor = colorScheme.primary,
                                                                    formulaContainerColor = colorScheme.surface
                                                                )
                                                            }
                                                            if (!isAiLoading) {
                                                                TextButton(
                                                                    onClick = {
                                                                        if (!aiSavedToNotes) {
                                                                            saveTextToNotes(
                                                                                noteTitle = "$subject • ${activeEpisode.topicTitle} (AI)",
                                                                                bodyText = aiAnswer
                                                                            ) {
                                                                                aiSavedToNotes = true
                                                                                Toast.makeText(context, "Notlara kaydedildi", Toast.LENGTH_SHORT).show()
                                                                            }
                                                                        }
                                                                    },
                                                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                                                ) {
                                                                    Icon(
                                                                        imageVector = if (aiSavedToNotes) Icons.Default.CheckCircle else Icons.Default.EditNote,
                                                                        contentDescription = null,
                                                                        modifier = Modifier.size(14.dp)
                                                                    )
                                                                    Spacer(modifier = Modifier.width(4.dp))
                                                                    Text(if (aiSavedToNotes) "Kaydedildi" else "Nota Kaydet", fontSize = 11.sp)
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        MathSymbolKeyboardPanel(
                                            visible = showMathPanel,
                                            onInsertSymbol = { sym ->
                                                aiQuestion += sym
                                            }
                                        )

                                        // Compact Input Row
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
                                                placeholder = { Text("Dakika veya soru sor...", fontSize = 12.sp) },
                                                singleLine = true,
                                                shape = RoundedCornerShape(12.dp),
                                                modifier = Modifier.weight(1f)
                                            )
                                            Surface(
                                                onClick = {
                                                    val q = aiQuestion
                                                    aiQuestion = ""
                                                    triggerAiTutorQuestion(q)
                                                },
                                                shape = CircleShape,
                                                color = if (aiQuestion.isNotBlank() && !isAiLoading) colorScheme.primary else colorScheme.surfaceVariant,
                                                modifier = Modifier.size(38.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(
                                                        imageVector = Icons.AutoMirrored.Filled.Send,
                                                        contentDescription = "Gönder",
                                                        tint = if (aiQuestion.isNotBlank() && !isAiLoading) colorScheme.onPrimary else colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.size(17.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                1 -> {
                                    // Quick Note Tab
                                    OutlinedTextField(
                                        value = quickNoteText,
                                        onValueChange = {
                                            quickNoteText = it
                                            noteSavedSuccess = false
                                        },
                                        placeholder = { Text("Ders notunu hızlıca yaz...", fontSize = 12.sp) },
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxWidth()
                                    )
                                    Button(
                                        onClick = {
                                            saveTextToNotes(
                                                noteTitle = "$subject • ${activeEpisode.dayNumber}. Ders",
                                                bodyText = quickNoteText
                                            ) {
                                                noteSavedSuccess = true
                                                Toast.makeText(context, "Not kaydedildi", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        enabled = quickNoteText.isNotBlank(),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(if (noteSavedSuccess) "Kaydedildi" else "Notlara Kaydet", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                else -> {
                                    // Episodes Tab with individual video deletion
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
                                                    isAiPopupOpen = false
                                                },
                                                shape = RoundedCornerShape(10.dp),
                                                color = if (isCurrent) colorScheme.primaryContainer else colorScheme.surfaceVariant,
                                                border = BorderStroke(1.dp, if (isCurrent) colorScheme.primary else colorScheme.outlineVariant),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(start = 10.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = if (isCurrent) Icons.Default.PlayArrow else Icons.Default.PlayCircleOutline,
                                                        contentDescription = null,
                                                        tint = if (isCurrent) colorScheme.primary else colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            text = "${ep.dayNumber}. ${ep.topicTitle}",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                                            color = if (isCurrent) colorScheme.onPrimaryContainer else colorScheme.onSurface,
                                                            maxLines = 2,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                        if (ep.mappedTopic.isNotBlank() && !ep.mappedTopic.equals(ep.topicTitle, ignoreCase = true)) {
                                                            Text(
                                                                text = "Konu: ${ep.mappedTopic}",
                                                                fontSize = 10.sp,
                                                                color = colorScheme.primary,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                        }
                                                    }
                                                    if (safeEpisodes.size > 1) {
                                                        IconButton(
                                                            onClick = { pendingDeleteStudioEpisode = ep },
                                                            modifier = Modifier.size(28.dp)
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.DeleteOutline,
                                                                contentDescription = "Videoyu Sil",
                                                                tint = colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                                                                modifier = Modifier.size(16.dp)
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

                // ── LAYER 4: Lightweight Draggable Floating AI Orb (Normal + Fullscreen!) ──
                if (!isInPipMode && orbOffsetX >= 0f && orbOffsetY >= 0f) {
                    Surface(
                        shape = CircleShape,
                        color = if (isAiPopupOpen) colorScheme.primaryContainer else colorScheme.primary,
                        contentColor = if (isAiPopupOpen) colorScheme.primary else colorScheme.onPrimary,
                        border = BorderStroke(1.5.dp, colorScheme.onPrimary.copy(alpha = 0.55f)),
                        shadowElevation = 2.dp,
                        modifier = Modifier
                            .offset { IntOffset(orbOffsetX.roundToInt(), orbOffsetY.roundToInt()) }
                            .size(orbSizeDp)
                            .pointerInput(containerWidthPx, containerHeightPx) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    val maxX = (containerWidthPx - orbSizePx - edgePadPx).coerceAtLeast(edgePadPx)
                                    val maxY = (containerHeightPx - orbSizePx - edgePadPx).coerceAtLeast(edgePadPx)
                                    orbOffsetX = (orbOffsetX + dragAmount.x).coerceIn(edgePadPx, maxX)
                                    orbOffsetY = (orbOffsetY + dragAmount.y).coerceIn(edgePadPx, maxY)
                                }
                            }
                            .clickable {
                                isAiPopupOpen = !isAiPopupOpen
                            }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isAiPopupOpen) Icons.Default.Close else Icons.Default.AutoAwesome,
                                contentDescription = "Stüdyo AI",
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }

                // ── LAYER 5: Smartwatch Wellness Popup Overlay (visible inside Video Studio too!) ──
                WellnessReminderSmartwatchOverlay()
            }
        }
    }
}

@Composable
private fun StudioFocusTimerPill(
    subject: String,
    activeEpisode: CampDayEpisode
) {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    val timerState by FocusTimerService.timerStateFlow.collectAsStateWithLifecycle()
    val isFocusing = timerState.timerPhase == TimerPhase.FOCUSING
    val isPaused = timerState.timerPhase == TimerPhase.PAUSED
    val formattedTimer = FocusTimerService.formatMillis(timerState.displayMillis)

    Surface(
        onClick = {
            val cleanSubject = CoachingEngine.formatCleanLeagueSubject(subject).ifBlank { "Odak" }
            val focusLabel = CoachingEngine.buildFormattedFocusSubject(
                title = cleanSubject,
                subNote = "${activeEpisode.dayNumber}. Ders ${activeEpisode.topicTitle}".take(42)
            )
            when (timerState.timerPhase) {
                TimerPhase.IDLE -> {
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
        },
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

@Composable
private fun StudioLiveTimestampBadge(
    secondsFlow: kotlinx.coroutines.flow.StateFlow<Int>
) {
    val colorScheme = MaterialTheme.colorScheme
    val secs by secondsFlow.collectAsStateWithLifecycle()
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = colorScheme.primary.copy(alpha = 0.14f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Icon(Icons.Default.AccessTime, contentDescription = null, tint = colorScheme.primary, modifier = Modifier.size(11.dp))
            Text(
                text = BookCampManager.formatSecondsAsTimestamp(secs),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = colorScheme.primary
            )
        }
    }
}

@Composable
private fun StudioExplainNowChip(
    secondsFlow: kotlinx.coroutines.flow.StateFlow<Int>,
    onExplain: (String) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val secs by secondsFlow.collectAsStateWithLifecycle()
    val liveTimeLabel = BookCampManager.formatSecondsAsTimestamp(secs)
    Surface(
        onClick = {
            onExplain("$liveTimeLabel civarında anlatılan kısmı adım adım özetler misin?")
        },
        shape = RoundedCornerShape(10.dp),
        color = colorScheme.primary.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, colorScheme.primary.copy(alpha = 0.35f))
    ) {
        Text(
            text = "Şu anki ($liveTimeLabel) kısmı açıkla",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = colorScheme.primary,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

private const val STUDIO_BACKGROUND_PLAY_JS = """
(function() {
  if (window.__sharkBgKeepAliveInstalled) return;
  window.__sharkBgKeepAliveInstalled = true;
  window.__sharkUserPaused = false;
  try {
    Object.defineProperty(document, 'hidden', { configurable: true, get: function() { return false; } });
    Object.defineProperty(document, 'webkitHidden', { configurable: true, get: function() { return false; } });
    Object.defineProperty(document, 'visibilityState', { configurable: true, get: function() { return 'visible'; } });
    Object.defineProperty(document, 'webkitVisibilityState', { configurable: true, get: function() { return 'visible'; } });
    var blockVis = function(e) { e.stopImmediatePropagation(); };
    document.addEventListener('visibilitychange', blockVis, true);
    document.addEventListener('webkitvisibilitychange', blockVis, true);
    window.addEventListener('pagehide', blockVis, true);
    window.addEventListener('freeze', blockVis, true);
    window.addEventListener('blur', blockVis, true);
  } catch (e) {}
})();
"""

private const val STUDIO_TELEMETRY_JS = """
(function() {
  if (window.__sharkTelemetryInstalled) return;
  window.__sharkTelemetryInstalled = true;
  var boundVideo = null;
  function sendSnap() {
    try {
      var v = document.querySelector('video');
      if (v && v !== boundVideo) {
        boundVideo = v;
        v.addEventListener('play', sendSnap);
        v.addEventListener('pause', sendSnap);
        v.addEventListener('seeked', sendSnap);
        v.addEventListener('loadedmetadata', sendSnap);
      }
      var sec = (v && !isNaN(v.currentTime)) ? Math.floor(v.currentTime) : -1;
      var dur = (v && isFinite(v.duration) && v.duration > 0) ? Math.floor(v.duration) : 0;
      var playing = v ? (!v.paused && !v.ended) : false;
      var titleEl = document.querySelector('.ytp-title-link, h2.slim-video-information-title, .slim-video-metadata-header');
      var title = (titleEl && titleEl.innerText) ? titleEl.innerText : (document.title || '');
      if (window.SharkStudioBridge) {
        window.SharkStudioBridge.onTelemetry(sec, dur, playing, title, window.location.href || '');
      }
      var errEl = document.querySelector('.ytp-error-content-wrap-reason, .ytp-error');
      if (errEl && errEl.innerText && errEl.innerText.length > 4 && window.SharkStudioBridge) {
        window.SharkStudioBridge.onEmbedError();
      }
    } catch (e) {}
  }
  window.__sharkSendTelemetryNow = sendSnap;
  setTimeout(sendSnap, 1200);
  setInterval(sendSnap, 3500);
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
  function checkAndSkipAd() {
    try {
      var player = document.querySelector('.html5-video-player');
      var video = document.querySelector('video');
      var isAd = (player && (player.classList.contains('ad-showing') || player.classList.contains('ad-interrupting')));
      if (isAd && video) {
        video.muted = true;
        if (isFinite(video.duration) && video.duration > 0.5) {
          video.currentTime = video.duration - 0.1;
        }
        video.playbackRate = 16.0;
        var skipBtns = document.querySelectorAll('.ytp-ad-skip-button,.ytp-ad-skip-button-modern,.ytp-skip-ad-button,button[class*="skip-ad"]');
        for (var i = 0; i < skipBtns.length; i++) {
          skipBtns[i].click();
        }
      } else if (video && video.playbackRate > 4.0) {
        video.playbackRate = 1.0;
        video.muted = false;
      }
    } catch (e) {}
  }
  try {
    var target = document.querySelector('.html5-video-player') || document.body;
    if (target && typeof MutationObserver !== 'undefined') {
      var obs = new MutationObserver(checkAndSkipAd);
      obs.observe(target, { attributes: true, attributeFilter: ['class'] });
    }
  } catch (e) {}
  setInterval(checkAndSkipAd, 2500);
})();
"""

@SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
@Composable
private fun DistractionFreeYouTubeWebView(
    url: String,
    adBlockerEnabled: Boolean,
    onWebViewCreated: (WebView) -> Unit,
    onPlaybackTelemetry: (seconds: Int, durationSeconds: Int, isPlaying: Boolean, title: String, currentUrl: String) -> Unit,
    onEmbedRestricted: () -> Unit,
    onEnterCustomFullscreen: (View, WebChromeClient.CustomViewCallback?) -> Unit,
    onExitCustomFullscreen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentAdBlock by rememberUpdatedState(adBlockerEnabled)
    val currentOnTelemetry by rememberUpdatedState(onPlaybackTelemetry)
    val currentOnEmbedRestricted by rememberUpdatedState(onEmbedRestricted)
    val currentOnEnterFs by rememberUpdatedState(onEnterCustomFullscreen)
    val currentOnExitFs by rememberUpdatedState(onExitCustomFullscreen)

    val webView = remember(context) {
        CampVideoPlaybackController.acquireOrCreateWebView(context) { wv ->
            wv.layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            wv.setBackgroundColor(android.graphics.Color.BLACK)
            wv.settings.apply {
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
        }.apply {
            removeJavascriptInterface("SharkStudioBridge")
            addJavascriptInterface(
                object {
                    @JavascriptInterface
                    fun onTelemetry(seconds: Int, durationSeconds: Int, isPlaying: Boolean, title: String, currentUrl: String) {
                        post { currentOnTelemetry(seconds, durationSeconds, isPlaying, title, currentUrl) }
                    }

                    @JavascriptInterface
                    fun onEmbedError() {
                        post { currentOnEmbedRestricted() }
                    }
                },
                "SharkStudioBridge"
            )
            webChromeClient = object : WebChromeClient() {
                override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                    if (view != null) {
                        currentOnEnterFs(view, callback)
                    } else {
                        super.onShowCustomView(view, callback)
                    }
                }

                override fun onHideCustomView() {
                    currentOnExitFs()
                    super.onHideCustomView()
                }
            }
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

                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    view?.evaluateJavascript(STUDIO_BACKGROUND_PLAY_JS, null)
                }

                override fun onPageFinished(view: WebView?, loadedUrl: String?) {
                    super.onPageFinished(view, loadedUrl)
                    view?.evaluateJavascript(STUDIO_BACKGROUND_PLAY_JS, null)
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
        }.also { onWebViewCreated(it) }
    }

    AndroidView(
        factory = {
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView
        },
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
 * Mark 67 Top-3 Relevant Playlist Discovery, Real Absorption & Video Opt-Out Dialog:
 * - Strictly presents ONLY the Top 3 most relevant YouTube playlists (`#1 Önerilen`, `#2`, `#3`).
 * - Provides explicit "Video Desteği Olmasın (Sadece Kitap)" opt-out.
 * - Truly absorbs the selected playlist's real ordered videos (stripping intros/promos and mapping
 *   every lesson to its canonical curriculum topic) with transparent preview before saving.
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
    var candidatePlaylists by remember(initialBinding) { mutableStateOf(initialBinding.candidatePlaylists.take(3)) }
    var hasDirectBookPlaylist by remember(initialBinding) { mutableStateOf(initialBinding.hasDirectBookPlaylist) }
    var skippedIntroCount by remember(initialBinding) { mutableIntStateOf(initialBinding.skippedIntroCount) }

    val roleAssignments by com.example.sharkmentor.core.coaching.BookCoachingRoleManager.assignmentsByResourceId.collectAsStateWithLifecycle()
    val isVideoOptedOut = remember(roleAssignments, resource.id) {
        com.example.sharkmentor.core.coaching.BookCoachingRoleManager.isVideoDisabledFor(resource.id, roleAssignments)
    }

    var isFetchingPlaylist by remember { mutableStateOf(false) }
    var isAutoSearching by remember { mutableStateOf(false) }
    var selectingCandidateId by remember { mutableStateOf<String?>(null) }
    var isDistributing by remember { mutableStateOf(false) }
    var statusNotice by remember(initialBinding) {
        mutableStateOf(initialBinding.discoveryStatusMessage.takeIf { it.isNotBlank() })
    }

    val (parsedVid, parsedPlId) = remember(youtubeUrl) { BookCampManager.parseYouTubeUrlIds(youtubeUrl) }
    val hasValidMedia = !isVideoOptedOut && (loadedEpisodes.any { it.videoId.length == 11 } || parsedVid.length == 11 || parsedPlId.isNotBlank())

    // Automatically trigger Top-3 discovery on open if not opted out and no candidates cached yet
    LaunchedEffect(resource.id, isVideoOptedOut) {
        com.example.sharkmentor.core.coaching.BookCoachingRoleManager.ensureLoaded(context)
        if (!isVideoOptedOut && candidatePlaylists.isEmpty() && !initialBinding.videoCheckCompleted) {
            isAutoSearching = true
            val discovered = BookCampManager.discoverAndSyncRealCampForResource(
                context = context,
                resource = resource,
                forceRefresh = false
            )
            isAutoSearching = false
            candidatePlaylists = discovered.candidatePlaylists.take(3)
            hasDirectBookPlaylist = discovered.hasDirectBookPlaylist
            skippedIntroCount = discovered.skippedIntroCount
            if (discovered.discoveryStatusMessage.isNotBlank()) {
                statusNotice = discovered.discoveryStatusMessage
            }
            if (discovered.hasVerifiedVideoSupport) {
                campTitle = discovered.campTitle
                instructor = discovered.instructor
                youtubeUrl = discovered.youtubeUrl
                loadedEpisodes = discovered.episodes
                isFromRealPlaylist = discovered.isFromRealPlaylist
                currentDayText = discovered.currentDay.toString()
                onSaved(discovered)
            }
        }
    }

    fun buildAndSaveBinding(): ResourceCampBinding {
        val total = loadedEpisodes.size.coerceAtLeast(1)
        val cur = (currentDayText.toIntOrNull() ?: 1).coerceIn(1, total)
        com.example.sharkmentor.core.coaching.BookCoachingRoleManager.setVideoDisabled(context, resource, false)
        return BookCampManager.saveResourceCampBinding(
            context = context,
            resource = resource,
            campTitle = campTitle.ifBlank { resource.title },
            instructor = instructor,
            totalDays = total,
            currentDay = cur,
            youtubeUrl = youtubeUrl,
            realEpisodes = loadedEpisodes,
            isFromRealPlaylist = isFromRealPlaylist && loadedEpisodes.isNotEmpty(),
            hasDirectBookPlaylist = hasDirectBookPlaylist,
            discoveryStatusMessage = statusNotice.orEmpty(),
            candidatePlaylists = candidatePlaylists.take(3)
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = "Video Kampı & Oynatma Listesi",
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
                // Explicit "Video Olmasın (Sadece Kitap)" opt-out toggle
                Surface(
                    onClick = {
                        val nextDisabled = !isVideoOptedOut
                        com.example.sharkmentor.core.coaching.BookCoachingRoleManager.setVideoDisabled(
                            context = context,
                            resource = resource,
                            videoDisabled = nextDisabled
                        )
                        if (nextDisabled) {
                            loadedEpisodes = emptyList()
                            youtubeUrl = ""
                            isFromRealPlaylist = false
                            statusNotice = "Bu kitap için video desteği kapatıldı (Sadece Kitap)."
                        } else {
                            statusNotice = "Video desteği açıldı. En alakalı 3 listeyi arayabilir veya link ekleyebilirsin."
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    color = if (isVideoOptedOut) colorScheme.errorContainer.copy(alpha = 0.45f)
                    else colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(
                        1.dp,
                        if (isVideoOptedOut) colorScheme.error.copy(alpha = 0.5f) else colorScheme.outlineVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Video Desteği Olmasın (Sadece Kitap)",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isVideoOptedOut) colorScheme.error else colorScheme.onSurface
                            )
                            Text(
                                text = if (isVideoOptedOut) "Açık: Bu kitap için video aranmaz ve atanmaz."
                                else "Kapalı: İstersen video desteği olmadan ilerleyebilirsin.",
                                style = MaterialTheme.typography.labelSmall,
                                color = colorScheme.onSurfaceVariant
                            )
                        }
                        androidx.compose.material3.Switch(
                            checked = isVideoOptedOut,
                            onCheckedChange = { checked ->
                                com.example.sharkmentor.core.coaching.BookCoachingRoleManager.setVideoDisabled(
                                    context = context,
                                    resource = resource,
                                    videoDisabled = checked
                                )
                                if (checked) {
                                    loadedEpisodes = emptyList()
                                    youtubeUrl = ""
                                    isFromRealPlaylist = false
                                    statusNotice = "Bu kitap için video desteği kapatıldı (Sadece Kitap)."
                                }
                            }
                        )
                    }
                }

                if (!isVideoOptedOut) {
                    // 1. Top-3 Discovery button
                    OutlinedButton(
                        onClick = {
                            if (isAutoSearching || isFetchingPlaylist) return@OutlinedButton
                            isAutoSearching = true
                            statusNotice = "YouTube'da bu kitap için en alakalı 3 oynatma listesi aranıyor..."
                            scope.launch {
                                val discovered = BookCampManager.discoverAndSyncRealCampForResource(
                                    context = context,
                                    resource = resource,
                                    forceRefresh = true
                                )
                                isAutoSearching = false
                                candidatePlaylists = discovered.candidatePlaylists.take(3)
                                hasDirectBookPlaylist = discovered.hasDirectBookPlaylist
                                skippedIntroCount = discovered.skippedIntroCount
                                statusNotice = discovered.discoveryStatusMessage.ifBlank {
                                    if (discovered.hasVerifiedVideoSupport) {
                                        "${discovered.episodes.size} gerçek ders içe aktarıldı."
                                    } else {
                                        "Doğrudan liste bulunamadı; önerilen kamplardan seçebilir veya link ekleyebilirsin."
                                    }
                                }
                                if (discovered.hasVerifiedVideoSupport) {
                                    campTitle = discovered.campTitle
                                    instructor = discovered.instructor
                                    youtubeUrl = discovered.youtubeUrl
                                    loadedEpisodes = discovered.episodes
                                    isFromRealPlaylist = discovered.isFromRealPlaylist
                                    currentDayText = discovered.currentDay.toString()
                                    onSaved(discovered)
                                } else {
                                    loadedEpisodes = emptyList()
                                    isFromRealPlaylist = false
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
                        Text("En Alakalı 3 Oynatma Listesini Bul", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }

                    if (!statusNotice.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = colorScheme.primaryContainer.copy(alpha = 0.35f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = statusNotice!!,
                                style = MaterialTheme.typography.labelSmall,
                                color = colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                            )
                        }
                    }

                    // 2. Strictly Top-3 Most Relevant Discovered Candidates
                    if (candidatePlaylists.isNotEmpty()) {
                        Text(
                            text = if (hasDirectBookPlaylist) {
                                "En Alakalı ${candidatePlaylists.size} Oynatma Listesi (Seç & İçe Aktar)"
                            } else {
                                "${resource.subject.ifBlank { "Ders" }} İçin En Alakalı ${candidatePlaylists.size} Kamp Önerisi"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.primary
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            candidatePlaylists.take(3).forEachIndexed { idx, cand: BookVideoCandidate ->
                                val isActive = parsedPlId.isNotBlank() && parsedPlId == cand.playlistId
                                val isSelectingThis = selectingCandidateId == cand.playlistId
                                Surface(
                                    onClick = {
                                        if (selectingCandidateId != null) return@Surface
                                        selectingCandidateId = cand.playlistId
                                        statusNotice = "\"${cand.title}\" oynatma listesi içe aktarılıyor (gerçek ders sırası ve konular işleniyor)..."
                                        scope.launch {
                                            val selected = BookCampManager.selectCandidatePlaylistForResource(
                                                context = context,
                                                resource = resource,
                                                candidate = cand
                                            )
                                            selectingCandidateId = null
                                            candidatePlaylists = selected.candidatePlaylists.take(3)
                                            statusNotice = selected.discoveryStatusMessage
                                            if (selected.episodes.isNotEmpty() && selected.hasVerifiedVideoSupport) {
                                                campTitle = selected.campTitle
                                                instructor = selected.instructor
                                                youtubeUrl = selected.youtubeUrl
                                                loadedEpisodes = selected.episodes
                                                isFromRealPlaylist = selected.isFromRealPlaylist
                                                hasDirectBookPlaylist = selected.hasDirectBookPlaylist
                                                skippedIntroCount = selected.skippedIntroCount
                                                currentDayText = selected.currentDay.toString()
                                                onSaved(selected)
                                            }
                                        }
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isActive) colorScheme.primaryContainer.copy(alpha = 0.7f)
                                    else colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                    border = BorderStroke(
                                        1.dp,
                                        if (isActive || idx == 0) colorScheme.primary.copy(alpha = 0.65f)
                                        else colorScheme.outlineVariant.copy(alpha = 0.4f)
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = if (idx == 0) colorScheme.primary else colorScheme.surfaceVariant
                                                ) {
                                                    Text(
                                                        text = if (idx == 0) "#1 Önerilen" else "#${idx + 1}",
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (idx == 0) colorScheme.onPrimary else colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = colorScheme.surface
                                                ) {
                                                    Text(
                                                        text = if (cand.isDirectBookMatch) "Kitaba Özel" else "Ders Kampı",
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                            Text(
                                                text = cand.title,
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = colorScheme.onSurface,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            val meta = buildList {
                                                if (cand.channelName.isNotBlank()) add(cand.channelName)
                                                if (cand.videoCount > 0) add("${cand.videoCount} video")
                                            }.joinToString(" • ")
                                            if (meta.isNotBlank()) {
                                                Text(
                                                    text = meta,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                        if (isSelectingThis) {
                                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                        } else if (isActive) {
                                            Icon(Icons.Default.CheckCircle, contentDescription = "Seçili", tint = colorScheme.primary, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 3. Manual YouTube Playlist / Video Link + Import button
                    OutlinedTextField(
                        value = youtubeUrl,
                        onValueChange = { youtubeUrl = it },
                        label = { Text("Kendi YouTube Liste veya Video Linkin") },
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
                                        val cleanEp = campTitle.ifBlank { resource.title }
                                        loadedEpisodes = listOf(
                                            CampDayEpisode(
                                                dayNumber = 1,
                                                topicTitle = cleanEp,
                                                videoId = parsedVid,
                                                customUrl = youtubeUrl.trim(),
                                                mappedTopic = BookCampManager.mapVideoTitleToCurriculumTopic(resource.subject, cleanEp)
                                            )
                                        )
                                        isFromRealPlaylist = false
                                        skippedIntroCount = 0
                                        currentDayText = "1"
                                        statusNotice = "Tekil YouTube videosu bağlandı."
                                    } else {
                                        statusNotice = "Geçerli bir YouTube oynatma listesi veya video linki gir."
                                    }
                                    return@OutlinedButton
                                }
                                isFetchingPlaylist = true
                                statusNotice = "Oynatma listesi içe aktarılıyor (gerçek dersler ve konular eşleştiriliyor)..."
                                scope.launch {
                                    val absorbed = BookCampManager.absorbRealPlaylist(youtubeUrl, subject = resource.subject)
                                    isFetchingPlaylist = false
                                    if (absorbed.episodes.isNotEmpty()) {
                                        if (absorbed.playlistTitle.isNotBlank()) campTitle = absorbed.playlistTitle
                                        loadedEpisodes = absorbed.episodes
                                        isFromRealPlaylist = true
                                        skippedIntroCount = absorbed.skippedIntroCount
                                        currentDayText = (currentDayText.toIntOrNull() ?: 1).coerceIn(1, absorbed.episodes.size).toString()
                                        val skipNote = if (absorbed.skippedIntroCount > 0) " (${absorbed.skippedIntroCount} tanıtım elendi)" else ""
                                        statusNotice = "${absorbed.episodes.size} gerçek ders içe aktarıldı$skipNote."
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
                            Text("Listeyi İçe Aktar", fontSize = 11.sp)
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
                                        statusNotice = "$added ders esnek sürelerle haftalık plana eklendi."
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
                                label = { Text("Kaldığın Ders") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.width(104.dp)
                            )
                        }
                    }

                    // Real imported episode preview with mapped curriculum topics + individual video deletion
                    if (loadedEpisodes.isNotEmpty()) {
                        var showAllImportedEpisodes by remember { mutableStateOf(false) }
                        var pendingDeleteImportedEpisode by remember { mutableStateOf<CampDayEpisode?>(null) }

                        pendingDeleteImportedEpisode?.let { epToDelete ->
                            AlertDialog(
                                onDismissRequest = { pendingDeleteImportedEpisode = null },
                                containerColor = colorScheme.surface,
                                title = {
                                    Text(
                                        text = "Videoyu Sil",
                                        fontWeight = FontWeight.Bold,
                                        color = colorScheme.onSurface
                                    )
                                },
                                text = {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(
                                            text = "\"${epToDelete.topicTitle}\" oynatma listesinden kaldırılsın mı?",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = colorScheme.onSurface
                                        )
                                        Text(
                                            text = "Listeyi tekrardan içe aktararak videoyu yeniden ekleyebilirsiniz.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                confirmButton = {
                                    Button(
                                        onClick = {
                                            val nextEpisodes = BookCampManager.removeEpisodeAndReindex(
                                                episodes = loadedEpisodes,
                                                targetDayNumber = epToDelete.dayNumber,
                                            targetVideoId = epToDelete.videoId
                                            )
                                            loadedEpisodes = nextEpisodes
                                            val clampedDay = (currentDayText.toIntOrNull() ?: 1)
                                                .coerceIn(1, nextEpisodes.size.coerceAtLeast(1))
                                            currentDayText = clampedDay.toString()
                                            if (initialBinding.hasVerifiedVideoSupport) {
                                                BookCampManager.removeEpisodeFromBinding(context, resource.id, epToDelete)
                                                    ?.let { onSaved(it) }
                                            }
                                            pendingDeleteImportedEpisode = null
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = colorScheme.error,
                                            contentColor = colorScheme.onError
                                        )
                                    ) {
                                        Text("Sil", fontWeight = FontWeight.Bold)
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { pendingDeleteImportedEpisode = null }) {
                                        Text("Vazgeç")
                                    }
                                }
                            )
                        }

                        val selectedDay = (currentDayText.toIntOrNull() ?: 1).coerceIn(1, loadedEpisodes.size)
                        val startIdx = (selectedDay - 1).coerceAtLeast(0)
                        val window = if (showAllImportedEpisodes || loadedEpisodes.size <= 10) {
                            loadedEpisodes
                        } else {
                            loadedEpisodes.drop(startIdx).take(10)
                        }
                        HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.5f))
                        val skipTag = if (skippedIntroCount > 0) " • $skippedIntroCount tanıtım elendi" else ""
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "İçe Aktarılan Ders Sırası (${loadedEpisodes.size} Ders$skipTag)",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            if (loadedEpisodes.size > 10) {
                                TextButton(
                                    onClick = { showAllImportedEpisodes = !showAllImportedEpisodes },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                                ) {
                                    Text(
                                        text = if (showAllImportedEpisodes) "Daralt" else "Tümünü Gör",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
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
                                        .padding(start = 10.dp, end = 4.dp, top = 5.dp, bottom = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "${ep.dayNumber}. ${ep.topicTitle}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (isSelected) colorScheme.primary else colorScheme.onSurface,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (ep.mappedTopic.isNotBlank() && !ep.mappedTopic.equals(ep.topicTitle, ignoreCase = true)) {
                                            Text(
                                                text = "Konu: ${ep.mappedTopic}",
                                                fontSize = 10.sp,
                                                color = colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        if (isSelected) {
                                            Text(
                                                text = "Aktif",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = colorScheme.primary,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        if (loadedEpisodes.size > 1) {
                                            IconButton(
                                                onClick = { pendingDeleteImportedEpisode = ep },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.DeleteOutline,
                                                    contentDescription = "Videoyu Sil",
                                                    tint = colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (initialBinding.hasVerifiedVideoSupport) {
                        TextButton(
                            onClick = {
                                val cleared = BookCampManager.clearResourceVideoBinding(
                                    context = context,
                                    resource = resource,
                                    candidatePlaylists = candidatePlaylists.take(3)
                                )
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
                            Text("Video Bağlantısını Kaldır", color = colorScheme.error, fontSize = 12.sp)
                        }
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
                    enabled = hasValidMedia || isVideoOptedOut,
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
