package com.example.sharkmentor.ui.coaching

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircleOutline
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.example.sharkmentor.core.ai.ApiKeyRotator
import com.example.sharkmentor.core.ai.GeminiRestClient
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
import java.io.ByteArrayInputStream
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Split-Screen In-App Camp Video Study Studio:
 * - Upper half: Distraction-free YouTube embed/mobile player + direct "YouTube'da Aç" link + live synced Focus Timer.
 * - Lower half: Instant Contextual AI Tutor ("AI'ya Sor") & Quick Note Taking ("Hızlı Not") saved directly to Notes.
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

    var activeEpisode by remember(initialEpisode) { mutableStateOf(initialEpisode) }
    var activeTab by remember { mutableIntStateOf(0) } // 0 = AI'ya Sor, 1 = Hızlı Not

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

    // Quick Note state
    var quickNoteText by remember { mutableStateOf("") }
    var noteSavedSuccess by remember { mutableStateOf(false) }

    // Custom video link paste state
    var showPasteLinkDialog by remember { mutableStateOf(false) }
    var customUrlInput by remember { mutableStateOf(activeEpisode.customUrl) }

    fun triggerAiTutorQuestion(questionText: String) {
        val cleanQ = questionText.trim()
        if (cleanQ.isBlank() || isAiLoading) return
        isAiLoading = true
        aiSavedToNotes = false
        scope.launch {
            val compass = CoachingEngine.buildAiSystemCompassPrompt(context)
            val fullPrompt = """
                $compass
                [AKTİF KAMP VİDEOSU BAĞLAMI]
                • Kamp: $campTitle
                • Ders: $subject
                • Gün / Konu: ${activeEpisode.dayNumber}. Gün - ${activeEpisode.topicTitle}

                Öğrencinin videoyu izlerken sorduğu soru: "$cleanQ"
                Doğrudan, adım adım, net ve anlaşılır şekilde açıkla. Gereksiz giriş cümleleri kullanma.
            """.trimIndent()

            val response = withContext(Dispatchers.IO) {
                runCatching {
                    UserAiPreferences.initialize(context)
                    com.example.sharkmentor.core.ai.GeminiAiFactory.generateContent(
                        contents = listOf(com.example.sharkmentor.core.ai.GeminiContent.userText(fullPrompt)),
                        systemPrompt = compass,
                        taskType = com.example.sharkmentor.core.ai.AiTaskType.LIGHT_INTERACTION
                    ).text
                }
            }
            isAiLoading = false
            aiAnswer = response.getOrElse {
                "Bağlantı kurulamadı. Lütfen internet bağlantınızı veya AI ayarlarınızı kontrol edin."
            }
        }
    }

    fun saveTextToNotes(noteTitle: String, bodyText: String, onDone: () -> Unit) {
        val cleanBody = bodyText.trim()
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

                // ── Upper Half: Embedded Distraction-Free Video Player ────────
                val embedUrl = remember(activeEpisode) { activeEpisode.buildInAppEmbedUrl() }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .background(Color.Black)
                ) {
                    DistractionFreeYouTubeWebView(
                        url = embedUrl,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // ── Action Strip Directly Underneath Video (Day Nav + Link + YouTube Redirect) ──
                Surface(
                    color = colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Day episode navigation
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            val currentIdx = allEpisodes.indexOfFirst { it.dayNumber == activeEpisode.dayNumber }
                            IconButton(
                                onClick = {
                                    if (currentIdx > 0) {
                                        val prev = allEpisodes[currentIdx - 1]
                                        activeEpisode = prev
                                        onEpisodeChanged?.invoke(prev)
                                    }
                                },
                                enabled = currentIdx > 0,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ChevronLeft,
                                    contentDescription = "Önceki Gün",
                                    modifier = Modifier.size(18.dp)
                                )
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
                                        onEpisodeChanged?.invoke(next)
                                    }
                                },
                                enabled = currentIdx in 0 until allEpisodes.lastIndex,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = "Sonraki Gün",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // Paste direct video link button
                            Surface(
                                onClick = {
                                    customUrlInput = activeEpisode.customUrl
                                    showPasteLinkDialog = true
                                },
                                shape = RoundedCornerShape(8.dp),
                                color = colorScheme.surface,
                                border = BorderStroke(1.dp, colorScheme.outlineVariant)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Link,
                                        contentDescription = "Link Yapıştır",
                                        tint = colorScheme.primary,
                                        modifier = Modifier.size(14.dp)
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
                                    color = colorScheme.primary.copy(alpha = 0.14f),
                                    border = BorderStroke(1.dp, colorScheme.primary.copy(alpha = 0.4f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Günü Tamamla",
                                            tint = colorScheme.primary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = "Bitti",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = colorScheme.primary
                                        )
                                    }
                                }
                            }

                            // Direct YouTube external link button
                            Surface(
                                onClick = {
                                    runCatching {
                                        val extIntent = Intent(
                                            Intent.ACTION_VIEW,
                                            Uri.parse(activeEpisode.buildExternalYouTubeUrl())
                                        )
                                        context.startActivity(extIntent)
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                color = colorScheme.primary,
                                contentColor = colorScheme.onPrimary
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                        contentDescription = "YouTube'da Aç",
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = "YouTube'da Aç",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                // ── Lower Half: AI Tutor & Quick Notes Split Panel ────────────
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Tab Switcher: AI'ya Sor | Hızlı Not Al
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
                        // Quick AI Prompt Chips
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                "Bu konuyu özetle",
                                "Temel formüller ve püf noktalar",
                                "ÖSYM bu konudan nasıl sorar?",
                                "Örnek soru ve çözümü"
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

                        // Question input row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = aiQuestion,
                                onValueChange = { aiQuestion = it },
                                placeholder = { Text("Videoda takıldığın yeri sor...") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = { triggerAiTutorQuestion(aiQuestion) },
                                enabled = aiQuestion.isNotBlank() && !isAiLoading,
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)
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

                        // AI Response Box
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
                                        text = "${activeEpisode.topicTitle} videosunu izlerken anlamadığın adımı veya kavramı aşağıdan anında sorabilirsin.",
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
                                    Text(
                                        text = if (isAiLoading && aiAnswer.isBlank()) "Yanıt hazırlanıyor..." else aiAnswer,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = colorScheme.onSurface
                                    )
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
                    } else {
                        // Tab 2: Quick Note Taking while watching
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
                            if (noteSavedSuccess) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Notlar sekmesine kaydedildi",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            } else {
                                Spacer(modifier = Modifier.width(4.dp))
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

/**
 * Lightweight, low-end device friendly YouTube WebView that blocks ad/tracker third-party domains,
 * uses youtube-nocookie embeds when a video/playlist ID is present, and destroys itself cleanly on dispose.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun DistractionFreeYouTubeWebView(
    url: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
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
            }
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                private val blockedAdHosts = listOf(
                    "doubleclick.net",
                    "googlesyndication.com",
                    "googleadservices.com",
                    "adservice.google"
                )

                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?
                ): WebResourceResponse? {
                    val host = request?.url?.host?.lowercase().orEmpty()
                    if (blockedAdHosts.any { host.contains(it) }) {
                        return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
                    }
                    return super.shouldInterceptRequest(view, request)
                }

                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {
                    val target = request?.url?.toString().orEmpty()
                    val host = request?.url?.host?.lowercase().orEmpty()
                    val allowed = host.contains("youtube.com") ||
                        host.contains("youtube-nocookie.com") ||
                        host.contains("youtu.be") ||
                        host.contains("googlevideo.com") ||
                        host.contains("ytimg.com")
                    return if (allowed) {
                        false
                    } else {
                        true
                    }
                }
            }
        }
    }

    DisposableEffect(webView) {
        onDispose {
            runCatching {
                webView.stopLoading()
                webView.loadUrl("about:blank")
                webView.destroy()
            }
        }
    }

    AndroidView(
        factory = { webView },
        update = { view ->
            if (view.url != url) {
                view.loadUrl(url)
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
