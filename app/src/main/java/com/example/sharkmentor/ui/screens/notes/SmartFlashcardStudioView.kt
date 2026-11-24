package com.example.sharkmentor.ui.screens.notes

import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.sharkmentor.core.ai.MultiProviderAiClient
import com.example.sharkmentor.core.flashcard.SelectedFlashcardSources
import com.example.sharkmentor.core.flashcard.SmartFlashcard
import com.example.sharkmentor.core.flashcard.SmartFlashcardManager
import com.example.sharkmentor.core.flashcard.orderDeckByPriority
import com.example.sharkmentor.core.util.ImportedPdfDocument
import com.example.sharkmentor.data.local.SharkMentorDatabase
import com.example.sharkmentor.data.local.entity.NoteEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartFlashcardStudioView(
    availableNotes: List<NoteEntity>,
    availablePdfs: List<ImportedPdfDocument>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colorScheme = MaterialTheme.colorScheme

    LaunchedEffect(Unit) {
        SmartFlashcardManager.ensureLoaded(context)
    }

    val savedSelection by SmartFlashcardManager.selectedSources.collectAsStateWithLifecycle()
    val topicStats by SmartFlashcardManager.topicStats.collectAsStateWithLifecycle()

    var errorBankSubjects by remember { mutableStateOf<List<String>>(emptyList()) }
    var activeDeck by remember { mutableStateOf<List<SmartFlashcard>>(emptyList()) }
    var currentIndex by remember { mutableIntStateOf(0) }
    var isBackVisible by remember { mutableStateOf(false) }
    var isGenerating by remember { mutableStateOf(false) }
    var showSourceSheet by remember { mutableStateOf(false) }
    var showPodcastSheet by remember { mutableStateOf(false) }
    var sessionFinished by remember { mutableStateOf(false) }
    val sessionOutcomes = remember { mutableStateMapOf<String, Pair<SmartFlashcard, Boolean>>() }

    LaunchedEffect(availableNotes.size, availablePdfs.size) {
        withContext(Dispatchers.IO) {
            val allErr = runCatching {
                SharkMentorDatabase.getDatabase(context).errorQuestionDao().getAllQuestionsRaw()
            }.getOrDefault(emptyList())
            errorBankSubjects = allErr
                .map { it.subject.trim() }
                .filter { it.isNotBlank() }
                .distinct()
                .sorted()
        }
        val sel = SmartFlashcardManager.selectedSources.value
        if (!sel.isEmpty && activeDeck.isEmpty() && !isGenerating) {
            isGenerating = true
            val loaded = SmartFlashcardManager.buildOrRefreshDeck(
                context = context,
                selection = sel,
                availableNotes = availableNotes,
                availablePdfs = availablePdfs,
                forceRegenerate = false
            )
            activeDeck = loaded
            currentIndex = 0
            isBackVisible = false
            sessionFinished = false
            isGenerating = false
        }
    }

    fun launchDeckBuild(selection: SelectedFlashcardSources, forceRegenerate: Boolean) {
        if (isGenerating) return
        scope.launch {
            isGenerating = true
            sessionOutcomes.clear()
            sessionFinished = false
            val deck = SmartFlashcardManager.buildOrRefreshDeck(
                context = context,
                selection = selection,
                availableNotes = availableNotes,
                availablePdfs = availablePdfs,
                forceRegenerate = forceRegenerate
            )
            activeDeck = deck
            currentIndex = 0
            isBackVisible = false
            isGenerating = false
        }
    }

    if (showSourceSheet) {
        FlashcardSourcePickerSheet(
            initialSelection = savedSelection,
            availableNotes = availableNotes,
            availablePdfs = availablePdfs,
            availableErrorSubjects = errorBankSubjects,
            onDismiss = { showSourceSheet = false },
            onConfirm = { newSel, forceAi ->
                showSourceSheet = false
                launchDeckBuild(newSel, forceAi)
            }
        )
    }

    if (showPodcastSheet) {
        PodcastBetaBottomSheet(
            cards = activeDeck,
            onDismiss = { showPodcastSheet = false }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 680.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Minimalist Top Control Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    onClick = { showSourceSheet = true },
                    shape = RoundedCornerShape(10.dp),
                    color = colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.45f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = if (savedSelection.isEmpty) {
                                "Kaynak Seç"
                            } else {
                                "Kaynaklar (${savedSelection.totalSelectedCount})"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = colorScheme.onSurface
                        )
                    }
                }

                if (activeDeck.isNotEmpty()) {
                    Surface(
                        onClick = { showPodcastSheet = true },
                        shape = RoundedCornerShape(10.dp),
                        color = colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.45f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Headphones,
                                contentDescription = null,
                                tint = colorScheme.onSurface,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = "Dinle",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = colorScheme.onSurface
                            )
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = colorScheme.primary.copy(alpha = 0.14f)
                            ) {
                                Text(
                                    text = "BETA",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                }
            }

            when {
                isGenerating -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(28.dp),
                                strokeWidth = 2.5.dp,
                                color = colorScheme.primary
                            )
                            Text(
                                text = "Kartlar hazırlanıyor...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                activeDeck.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = colorScheme.surface,
                            border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.45f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "Akıllı Bilgi Kartları",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = colorScheme.onSurface
                                )
                                Text(
                                    text = "Notlarından, PDF kaynaklarından ve Hata Bankandaki derslerden çalışma destesi oluştur. Zorlandığın konular sonraki oturumlarda otomatik öne alınır.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Button(
                                    onClick = { showSourceSheet = true },
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("Kaynak Seç", fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }

                sessionFinished -> {
                    val knownCount = sessionOutcomes.values.count { it.second }
                    val missedItems = sessionOutcomes.values.filter { !it.second }.map { it.first }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = colorScheme.surface,
                            border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.45f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(22.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = "Oturum Tamamlandı",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = colorScheme.onSurface
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    SummaryStatBox(
                                        label = "Yaptım",
                                        value = knownCount.toString(),
                                        modifier = Modifier.weight(1f)
                                    )
                                    SummaryStatBox(
                                        label = "Tekrar",
                                        value = missedItems.size.toString(),
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                if (missedItems.isNotEmpty()) {
                                    val weakTopics = missedItems
                                        .map { it.topic.ifBlank { it.sourceTitle } }
                                        .distinct()
                                        .take(4)
                                        .joinToString(", ")
                                    Text(
                                        text = "Öncelikli konular: $weakTopics",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colorScheme.onSurfaceVariant
                                    )
                                    Button(
                                        onClick = {
                                            activeDeck = orderDeckByPriority(missedItems, topicStats)
                                            sessionOutcomes.clear()
                                            currentIndex = 0
                                            isBackVisible = false
                                            sessionFinished = false
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Yapamadıklarımı Tekrarla (${missedItems.size})", fontWeight = FontWeight.SemiBold)
                                    }
                                }

                                OutlinedButton(
                                    onClick = {
                                        launchDeckBuild(savedSelection, forceRegenerate = false)
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Tüm Desteyi Öncelik Sırasıyla Aç")
                                }
                            }
                        }
                    }
                }

                else -> {
                    val safeIndex = currentIndex.coerceIn(0, activeDeck.lastIndex)
                    val card = activeDeck[safeIndex]
                    val progress = (safeIndex + 1).toFloat() / activeDeck.size.toFloat()

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Progress row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${safeIndex + 1} / ${activeDeck.size}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = colorScheme.onSurface
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (card.lastOutcomeKnown == false) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = colorScheme.errorContainer.copy(alpha = 0.65f)
                                    ) {
                                        Text(
                                            text = "Öncelikli",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = colorScheme.onErrorContainer,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = card.topic.ifBlank { card.sourceTitle },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(999.dp)),
                            color = colorScheme.primary,
                            trackColor = colorScheme.surfaceVariant
                        )

                        // Minimalist Tap-to-Flip Study Card
                        Surface(
                            onClick = { isBackVisible = !isBackVisible },
                            shape = RoundedCornerShape(18.dp),
                            color = colorScheme.surface,
                            border = BorderStroke(
                                1.dp,
                                if (isBackVisible) colorScheme.primary.copy(alpha = 0.5f)
                                else colorScheme.outlineVariant.copy(alpha = 0.55f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(20.dp)
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (isBackVisible) "CEVAP" else "SORU",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp,
                                        color = if (isBackVisible) colorScheme.primary else colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = card.sourceTitle,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                val imageToRender = if (isBackVisible) {
                                    card.solutionImageUri ?: card.questionImageUri
                                } else {
                                    card.questionImageUri
                                }

                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    if (!imageToRender.isNullOrBlank()) {
                                        AsyncImage(
                                            model = ImageRequest.Builder(context)
                                                .data(imageToRender)
                                                .crossfade(true)
                                                .build(),
                                            contentDescription = null,
                                            contentScale = ContentScale.Fit,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .heightIn(max = 260.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                        )
                                    }

                                    Text(
                                        text = if (isBackVisible) card.back else card.front,
                                        style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 24.sp),
                                        fontWeight = if (isBackVisible) FontWeight.Normal else FontWeight.SemiBold,
                                        color = colorScheme.onSurface,
                                        textAlign = TextAlign.Center
                                    )
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                Text(
                                    text = if (isBackVisible) "Ön yüze dönmek için dokun" else "Cevabı görmek için karta dokun",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        // Action Controls
                        if (!isBackVisible) {
                            Button(
                                onClick = { isBackVisible = true },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                            ) {
                                Text("Cevabı Göster", fontWeight = FontWeight.SemiBold)
                            }
                        } else {
                            fun submitOutcome(known: Boolean) {
                                scope.launch {
                                    val updatedCard = SmartFlashcardManager.recordCardOutcome(context, card, known)
                                    sessionOutcomes[updatedCard.id] = updatedCard to known
                                    val nextList = activeDeck.toMutableList().apply {
                                        set(safeIndex, updatedCard)
                                    }
                                    activeDeck = nextList
                                    if (safeIndex + 1 < activeDeck.size) {
                                        currentIndex = safeIndex + 1
                                        isBackVisible = false
                                    } else {
                                        SmartFlashcardManager.syncSessionOutcomesToDossierIfActive(
                                            context = context,
                                            sessionResults = sessionOutcomes.values.toMap()
                                        )
                                        sessionFinished = true
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { submitOutcome(known = false) },
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, colorScheme.outline),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                ) {
                                    Text(
                                        text = "Yapamadım",
                                        fontWeight = FontWeight.SemiBold,
                                        color = colorScheme.onSurface
                                    )
                                }
                                Button(
                                    onClick = { submitOutcome(known = true) },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                ) {
                                    Text("Yaptım", fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryStatBox(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = colorScheme.onSurface
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FlashcardSourcePickerSheet(
    initialSelection: SelectedFlashcardSources,
    availableNotes: List<NoteEntity>,
    availablePdfs: List<ImportedPdfDocument>,
    availableErrorSubjects: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (SelectedFlashcardSources, Boolean) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var selectedNotes by remember { mutableStateOf(initialSelection.noteIds) }
    var selectedPdfs by remember { mutableStateOf(initialSelection.pdfIds) }
    var selectedSubjects by remember { mutableStateOf(initialSelection.errorSubjects) }

    val totalSelected = selectedNotes.size + selectedPdfs.size + selectedSubjects.size

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colorScheme.surface,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Çalışma Kaynakları",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.onSurface
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Kapat", modifier = Modifier.size(18.dp))
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (availableNotes.isNotEmpty()) {
                    item {
                        SourceSectionHeader("NOTLARIM (${availableNotes.size})")
                    }
                    items(availableNotes, key = { "note_${it.id}" }) { note ->
                        val isChecked = note.id in selectedNotes
                        SelectableSourceRow(
                            title = note.title.ifBlank { "Başlıksız Not" },
                            selected = isChecked,
                            onClick = {
                                selectedNotes = if (isChecked) selectedNotes - note.id else selectedNotes + note.id
                            }
                        )
                    }
                }

                if (availablePdfs.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(6.dp))
                        SourceSectionHeader("PDF KAYNAKLARIM (${availablePdfs.size})")
                    }
                    items(availablePdfs, key = { "pdf_${it.id}" }) { pdf ->
                        val isChecked = pdf.id in selectedPdfs
                        SelectableSourceRow(
                            title = pdf.displayName.ifBlank { pdf.title }.ifBlank { "PDF" },
                            subtitle = if (pdf.pageCount > 0) "${pdf.pageCount} sayfa" else null,
                            selected = isChecked,
                            onClick = {
                                selectedPdfs = if (isChecked) selectedPdfs - pdf.id else selectedPdfs + pdf.id
                            }
                        )
                    }
                }

                if (availableErrorSubjects.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(6.dp))
                        SourceSectionHeader("HATA BANKASI DERSLERİ (${availableErrorSubjects.size})")
                    }
                    items(availableErrorSubjects, key = { "subj_$it" }) { subj ->
                        val isChecked = subj in selectedSubjects
                        SelectableSourceRow(
                            title = subj,
                            selected = isChecked,
                            onClick = {
                                selectedSubjects = if (isChecked) selectedSubjects - subj else selectedSubjects + subj
                            }
                        )
                    }
                }

                if (availableNotes.isEmpty() && availablePdfs.isEmpty() && availableErrorSubjects.isEmpty()) {
                    item {
                        Text(
                            text = "Henüz kayıtlı not, PDF veya hata bankası sorusu bulunmuyor.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 16.dp)
                        )
                    }
                }
            }

            HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.4f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (selectedNotes.isNotEmpty() || selectedPdfs.isNotEmpty()) {
                    TextButton(
                        onClick = {
                            onConfirm(
                                SelectedFlashcardSources(
                                    noteIds = selectedNotes,
                                    pdfIds = selectedPdfs,
                                    errorSubjects = selectedSubjects
                                ),
                                true
                            )
                        },
                        enabled = totalSelected > 0
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Yeniden Üret", fontSize = 12.sp)
                    }
                }
                Button(
                    onClick = {
                        onConfirm(
                            SelectedFlashcardSources(
                                noteIds = selectedNotes,
                                pdfIds = selectedPdfs,
                                errorSubjects = selectedSubjects
                            ),
                            false
                        )
                    },
                    enabled = totalSelected > 0,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = if (totalSelected > 0) "Başlat ($totalSelected)" else "Kaynak Seçin",
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun SourceSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.8.sp,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 4.dp)
    )
}

@Composable
private fun SelectableSourceRow(
    title: String,
    subtitle: String? = null,
    selected: Boolean,
    onClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (selected) colorScheme.primary.copy(alpha = 0.12f) else colorScheme.surfaceVariant.copy(alpha = 0.38f),
        border = BorderStroke(
            1.dp,
            if (selected) colorScheme.primary.copy(alpha = 0.6f) else colorScheme.outlineVariant.copy(alpha = 0.35f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = colorScheme.onSurfaceVariant
                    )
                }
            }
            if (selected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/**
 * Minimalist Offline Audio / Podcast Review Sheet (`BETA`).
 * Uses Android's built-in TTS with two-turn pitch/pause modulation + optional AI dialogue polish.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PodcastBetaBottomSheet(
    cards: List<SmartFlashcard>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colorScheme = MaterialTheme.colorScheme
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var ttsReady by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var activeTrackIdx by remember { mutableIntStateOf(0) }
    var speechRate by remember { mutableFloatStateOf(1.0f) }
    var isPolishingScript by remember { mutableStateOf(false) }
    var aiScriptSegments by remember { mutableStateOf<List<Pair<String, String>>?>(null) }

    val tracks: List<Pair<String, String>> = remember(cards, aiScriptSegments) {
        aiScriptSegments ?: cards.map { it.front to it.back }
    }

    val ttsHolder = remember { arrayOfNulls<TextToSpeech>(1) }

    fun speakTrack(index: Int, rate: Float) {
        val engine = ttsHolder[0] ?: return
        if (!ttsReady || tracks.isEmpty()) return
        val safeIdx = index.coerceIn(0, tracks.lastIndex)
        activeTrackIdx = safeIdx
        val (promptPart, answerPart) = tracks[safeIdx]
        engine.stop()
        engine.setSpeechRate(rate)
        engine.setPitch(1.05f)
        val params = Bundle()
        engine.speak(promptPart, TextToSpeech.QUEUE_FLUSH, params, "track_${safeIdx}_q")
        engine.playSilentUtterance(380L, TextToSpeech.QUEUE_ADD, "track_${safeIdx}_pause")
        engine.setPitch(0.96f)
        engine.speak(answerPart, TextToSpeech.QUEUE_ADD, params, "track_${safeIdx}_a")
        isPlaying = true
    }

    DisposableEffect(context) {
        var engine: TextToSpeech? = null
        engine = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                engine?.language = Locale("tr", "TR")
                engine?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onError(utteranceId: String?) {
                        isPlaying = false
                    }
                    override fun onDone(utteranceId: String?) {
                        if (utteranceId != null && utteranceId.endsWith("_a")) {
                            val nextIdx = activeTrackIdx + 1
                            if (nextIdx < tracks.size) {
                                speakTrack(nextIdx, speechRate)
                            } else {
                                isPlaying = false
                            }
                        }
                    }
                })
                ttsReady = true
            }
        }
        ttsHolder[0] = engine
        onDispose {
            runCatching {
                engine?.stop()
                engine?.shutdown()
            }
            ttsHolder[0] = null
        }
    }

    ModalBottomSheet(
        onDismissRequest = {
            runCatching { ttsHolder[0]?.stop() }
            onDismiss()
        },
        sheetState = sheetState,
        containerColor = colorScheme.surface,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 26.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Sesli Tekrar",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onSurface
                    )
                    Surface(
                        shape = RoundedCornerShape(5.dp),
                        color = colorScheme.primary.copy(alpha = 0.14f)
                    ) {
                        Text(
                            text = "BETA",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Text(
                    text = "Cihaz içi ses motoru",
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.onSurfaceVariant
                )
            }

            if (tracks.isNotEmpty()) {
                val safeIdx = activeTrackIdx.coerceIn(0, tracks.lastIndex)
                val (qText, aText) = tracks[safeIdx]
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = colorScheme.surfaceVariant.copy(alpha = 0.42f),
                    border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Bölüm ${safeIdx + 1} / ${tracks.size}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.primary
                        )
                        Text(
                            text = qText,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = aText,
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Playback + Speed row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(1.0f to "1.0x", 1.25f to "1.25x", 1.5f to "1.5x").forEach { (rate, label) ->
                        val selected = kotlin.math.abs(speechRate - rate) < 0.05f
                        Surface(
                            onClick = {
                                speechRate = rate
                                if (isPlaying) speakTrack(activeTrackIdx, rate)
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = if (selected) colorScheme.primary.copy(alpha = 0.16f) else colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            border = BorderStroke(
                                1.dp,
                                if (selected) colorScheme.primary else colorScheme.outlineVariant.copy(alpha = 0.35f)
                            )
                        ) {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (selected) colorScheme.primary else colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    IconButton(
                        onClick = {
                            val prev = (activeTrackIdx - 1).coerceAtLeast(0)
                            speakTrack(prev, speechRate)
                        },
                        enabled = ttsReady && activeTrackIdx > 0
                    ) {
                        Icon(Icons.Default.SkipPrevious, contentDescription = "Önceki")
                    }
                    Surface(
                        onClick = {
                            if (!ttsReady || tracks.isEmpty()) return@Surface
                            if (isPlaying) {
                                ttsHolder[0]?.stop()
                                isPlaying = false
                            } else {
                                speakTrack(activeTrackIdx, speechRate)
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = colorScheme.primary
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Duraklat" else "Oynat",
                                tint = colorScheme.onPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    IconButton(
                        onClick = {
                            val next = (activeTrackIdx + 1).coerceAtMost((tracks.size - 1).coerceAtLeast(0))
                            speakTrack(next, speechRate)
                        },
                        enabled = ttsReady && activeTrackIdx < tracks.lastIndex
                    ) {
                        Icon(Icons.Default.SkipNext, contentDescription = "Sonraki")
                    }
                }
            }

            if (aiScriptSegments == null && cards.isNotEmpty()) {
                OutlinedButton(
                    onClick = {
                        if (isPolishingScript) return@OutlinedButton
                        isPolishingScript = true
                        ttsHolder[0]?.stop()
                        isPlaying = false
                        scope.launch {
                            val rawJoined = cards.take(10).joinToString("\n") {
                                "Soru: ${it.front} | Cevap: ${it.back}"
                            }
                            val polished = withContext(Dispatchers.IO) {
                                runCatching {
                                    val resp = MultiProviderAiClient.executeUnifiedTextPrompt(
                                        context = context,
                                        systemPrompt = "Verilen kartları dinlemesi akıcı, doğal ve kısa soru-cevap anlatımına dönüştür. Yalnızca JSON dizisi döndür: [{\"front\":\"Kısa yönlendirici soru\",\"back\":\"Akıcı sözlü açıklama\"}]. Emoji kullanma.",
                                        userPrompt = rawJoined,
                                        temperature = 0.3f,
                                        maxTokens = 1600,
                                        responseJson = true,
                                        timeoutMs = 30_000L
                                    )
                                    com.example.sharkmentor.core.flashcard.parseAiFlashcardsJson(
                                        rawJson = resp,
                                        sourceType = com.example.sharkmentor.core.flashcard.FlashcardSourceType.NOTE,
                                        sourceId = "audio_script",
                                        sourceTitle = "Sesli Özet"
                                    ).map { it.front to it.back }
                                }.getOrDefault(emptyList())
                            }
                            if (polished.isNotEmpty()) {
                                aiScriptSegments = polished
                                activeTrackIdx = 0
                            }
                            isPolishingScript = false
                        }
                    },
                    enabled = !isPolishingScript,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (isPolishingScript) "Akıcı anlatım hazırlanıyor..." else "Yapay Zeka ile Akıcı Ses Metnine Dönüştür",
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}
