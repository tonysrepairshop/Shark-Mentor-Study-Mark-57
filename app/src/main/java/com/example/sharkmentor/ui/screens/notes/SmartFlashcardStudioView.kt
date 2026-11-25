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
import com.example.sharkmentor.core.flashcard.GeneratedNotePodcast
import com.example.sharkmentor.core.flashcard.NotePodcastEngine
import com.example.sharkmentor.core.flashcard.PodcastSegment
import com.example.sharkmentor.core.flashcard.SelectedFlashcardSources
import com.example.sharkmentor.core.flashcard.SmartFlashcard
import com.example.sharkmentor.core.flashcard.SmartFlashcardManager
import com.example.sharkmentor.core.flashcard.FlashcardDensity
import com.example.sharkmentor.core.flashcard.SmartFlashcardDeck
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Add
import androidx.compose.foundation.lazy.LazyRow
import android.widget.Toast
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
    val savedDecks by SmartFlashcardManager.savedDecks.collectAsStateWithLifecycle()
    val activeDeckId by SmartFlashcardManager.activeDeckId.collectAsStateWithLifecycle()
    var deckToRename by remember { mutableStateOf<SmartFlashcardDeck?>(null) }
    var deckRenameText by remember { mutableStateOf("") }
    var deckToDelete by remember { mutableStateOf<SmartFlashcardDeck?>(null) }
    var pendingDeckName by remember { mutableStateOf("") }

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

    DisposableEffect(Unit) {
        onDispose {
            if (!sessionFinished && sessionOutcomes.isNotEmpty()) {
                SmartFlashcardManager.flushSessionOutcomesInBackground(
                    context = context,
                    sessionResults = sessionOutcomes.values.toMap()
                )
            }
        }
    }

    LaunchedEffect(activeDeckId, savedDecks) {
        val current = savedDecks.firstOrNull { it.id == activeDeckId } ?: savedDecks.firstOrNull()
        if (current != null) {
            activeDeck = orderDeckByPriority(current.cards, topicStats)
            currentIndex = 0
            isBackVisible = false
            sessionFinished = false
        } else if (savedDecks.isEmpty()) {
            activeDeck = emptyList()
        }
    }

    fun launchDeckBuild(selection: SelectedFlashcardSources, forceRegenerate: Boolean, customName: String = "", density: FlashcardDensity = FlashcardDensity.STANDARD) {
        if (isGenerating) return
        if (!sessionFinished && sessionOutcomes.isNotEmpty()) {
            SmartFlashcardManager.flushSessionOutcomesInBackground(
                context = context,
                sessionResults = sessionOutcomes.values.toMap()
            )
        }
        scope.launch {
            isGenerating = true
            sessionOutcomes.clear()
            sessionFinished = false
            val result = SmartFlashcardManager.createNamedDeck(
                context = context,
                customName = customName,
                selection = selection,
                availableNotes = availableNotes,
                availablePdfs = availablePdfs,
                forceRegenerate = forceRegenerate,
                density = density
            )
            result.onSuccess { created ->
                activeDeck = orderDeckByPriority(created.cards, topicStats)
            }.onFailure { err ->
                Toast.makeText(
                    context,
                    err.message ?: "Flashcard serisi oluşturulamadı.",
                    Toast.LENGTH_LONG
                ).show()
            }
            currentIndex = 0
            isBackVisible = false
            isGenerating = false
        }
    }

    deckToRename?.let { target ->
        AlertDialog(
            onDismissRequest = { deckToRename = null },
            title = { Text("Seriyi Yeniden Adlandır", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = deckRenameText,
                    onValueChange = { deckRenameText = it },
                    singleLine = true,
                    label = { Text("Seri Adı") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        SmartFlashcardManager.renameDeck(context, target.id, deckRenameText)
                        deckToRename = null
                    }
                ) { Text("Kaydet", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { deckToRename = null }) { Text("İptal") }
            }
        )
    }

    deckToDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { deckToDelete = null },
            title = { Text("Flashcard Serisini Sil", fontWeight = FontWeight.Bold) },
            text = { Text("\"${target.name}\" serisi kalıcı olarak silinsin mi?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        SmartFlashcardManager.deleteDeck(context, target.id)
                        deckToDelete = null
                    }
                ) { Text("Sil", color = colorScheme.error, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { deckToDelete = null }) { Text("Vazgeç") }
            }
        )
    }

    if (showSourceSheet) {
        FlashcardSourcePickerSheet(
            initialSelection = SelectedFlashcardSources(),
            availableNotes = availableNotes,
            availablePdfs = availablePdfs,
            availableErrorSubjects = errorBankSubjects,
            onDismiss = { showSourceSheet = false },
            onConfirm = { newSel, forceAi, chosenDensity ->
                showSourceSheet = false
                val chosenName = pendingDeckName
                pendingDeckName = ""
                launchDeckBuild(newSel, forceAi, customName = chosenName, density = chosenDensity)
            }
        )
    }

    if (showPodcastSheet) {
        PodcastBetaBottomSheet(
            selection = savedSelection,
            availableNotes = availableNotes,
            availablePdfs = availablePdfs,
            onOpenSourcePicker = {
                showPodcastSheet = false
                showSourceSheet = true
            },
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
                    onClick = {
                        if (savedDecks.size >= SmartFlashcardManager.MAX_SAVED_DECKS) {
                            Toast.makeText(
                                context,
                                "En fazla 5 flashcard serisi oluşturabilirsiniz. Yeni seri eklemek için mevcut bir seriyi silin.",
                                Toast.LENGTH_LONG
                            ).show()
                        } else {
                            showSourceSheet = true
                        }
                    },
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
                            text = "+ Yeni Seri",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = colorScheme.onSurface
                        )
                    }
                }

                if (availableNotes.isNotEmpty() || availablePdfs.isNotEmpty()) {
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
                                tint = colorScheme.primary,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = "Podcast Özeti",
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

            if (savedDecks.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(savedDecks, key = { it.id }) { deckItem ->
                        val isSel = deckItem.id == activeDeckId || (activeDeckId == null && deckItem == savedDecks.first())
                        Surface(
                            onClick = { SmartFlashcardManager.selectDeck(deckItem.id) },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSel) colorScheme.primaryContainer else colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            border = BorderStroke(
                                1.dp,
                                if (isSel) colorScheme.primary else colorScheme.outlineVariant.copy(alpha = 0.45f)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 5.dp, bottom = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Column {
                                    Text(
                                        text = deckItem.name,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSel) colorScheme.onPrimaryContainer else colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${deckItem.cards.size} kart",
                                        fontSize = 10.sp,
                                        color = colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        deckToRename = deckItem
                                        deckRenameText = deckItem.name
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Yeniden Adlandır",
                                        tint = colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                                IconButton(
                                    onClick = { deckToDelete = deckItem },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Sil",
                                        tint = colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
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
    onConfirm: (SelectedFlashcardSources, Boolean, FlashcardDensity) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var selectedNotes by remember { mutableStateOf(initialSelection.noteIds) }
    var selectedPdfs by remember { mutableStateOf(initialSelection.pdfIds) }
    var selectedSubjects by remember { mutableStateOf(initialSelection.errorSubjects) }
    var selectedDensity by remember { mutableStateOf(FlashcardDensity.STANDARD) }

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

            // Smart Question Density Picker (Az / Standart / Çok)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Soru Yoğunluğu",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FlashcardDensity.entries.forEach { d ->
                        val active = selectedDensity == d
                        Surface(
                            onClick = { selectedDensity = d },
                            shape = RoundedCornerShape(10.dp),
                            color = if (active) colorScheme.primary else colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = BorderStroke(
                                1.dp,
                                if (active) colorScheme.primary else colorScheme.outlineVariant
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier.padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = d.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                    color = if (active) colorScheme.onPrimary else colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

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
                                true,
                                selectedDensity
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
                            false,
                            selectedDensity
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
 * True AI Note & PDF Podcast Summary Sheet.
 * Synthesizes a multi-chapter two-host conversational summary directly from the student's Notes & PDFs
 * via [NotePodcastEngine] (never reading flashcard Q&A verbatim) and speaks it with dual-host pitch modulation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PodcastBetaBottomSheet(
    selection: SelectedFlashcardSources,
    availableNotes: List<NoteEntity>,
    availablePdfs: List<ImportedPdfDocument>,
    onOpenSourcePicker: () -> Unit,
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
    var isGeneratingPodcast by remember { mutableStateOf(true) }
    var episode by remember { mutableStateOf<GeneratedNotePodcast?>(null) }

    val segments: List<PodcastSegment> = episode?.segments.orEmpty()
    val ttsHolder = remember { arrayOfNulls<TextToSpeech>(1) }
    val mainHandler = remember { android.os.Handler(android.os.Looper.getMainLooper()) }

    fun loadPodcast(forceRefresh: Boolean) {
        scope.launch {
            ttsHolder[0]?.stop()
            isPlaying = false
            isGeneratingPodcast = true
            val built = NotePodcastEngine.loadOrGeneratePodcast(
                context = context,
                selection = selection,
                availableNotes = availableNotes,
                availablePdfs = availablePdfs,
                forceRefresh = forceRefresh
            )
            episode = built
            activeTrackIdx = 0
            isGeneratingPodcast = false
        }
    }

    LaunchedEffect(selection.noteIds, selection.pdfIds) {
        loadPodcast(forceRefresh = false)
    }

    fun speakHostPart(index: Int, part: Char, rate: Float) {
        val engine = ttsHolder[0] ?: return
        val currentSegments = episode?.segments.orEmpty()
        if (!ttsReady || currentSegments.isEmpty()) return
        val safeIdx = index.coerceIn(0, currentSegments.lastIndex)
        val seg = currentSegments[safeIdx]
        val textToSpeak = if (part == 'a') seg.hostA else seg.hostB
        if (textToSpeak.isBlank()) {
            if (part == 'a' && seg.hostB.isNotBlank()) {
                speakHostPart(safeIdx, 'b', rate)
            } else {
                val nextIdx = safeIdx + 1
                if (nextIdx < currentSegments.size) speakHostPart(nextIdx, 'a', rate) else isPlaying = false
            }
            return
        }
        activeTrackIdx = safeIdx
        engine.stop()
        engine.setSpeechRate(rate)
        // Distinct host pitch applied strictly per utterance (Android TTS applies pitch globally)
        engine.setPitch(if (part == 'a') 1.07f else 0.90f)
        val params = Bundle()
        engine.speak(textToSpeak, TextToSpeech.QUEUE_FLUSH, params, "pod_${safeIdx}_${part}")
        isPlaying = true
    }

    fun speakSegment(index: Int, rate: Float) {
        speakHostPart(index, 'a', rate)
    }

    DisposableEffect(context) {
        var engine: TextToSpeech? = null
        engine = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                engine?.language = Locale("tr", "TR")
                engine?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onError(utteranceId: String?) {
                        mainHandler.post { isPlaying = false }
                    }
                    override fun onDone(utteranceId: String?) {
                        if (utteranceId == null) return
                        mainHandler.post {
                            val currentSegments = episode?.segments.orEmpty()
                            if (currentSegments.isEmpty() || !isPlaying) return@post
                            val safeIdx = activeTrackIdx.coerceIn(0, currentSegments.lastIndex)
                            val seg = currentSegments[safeIdx]
                            when {
                                utteranceId == "pod_${safeIdx}_a" && seg.hostB.isNotBlank() -> {
                                    mainHandler.postDelayed({
                                        if (isPlaying && activeTrackIdx == safeIdx) {
                                            speakHostPart(safeIdx, 'b', speechRate)
                                        }
                                    }, 260L)
                                }
                                utteranceId == "pod_${safeIdx}_b" || (utteranceId == "pod_${safeIdx}_a" && seg.hostB.isBlank()) -> {
                                    val nextIdx = safeIdx + 1
                                    if (nextIdx < currentSegments.size) {
                                        mainHandler.postDelayed({
                                            if (isPlaying) speakHostPart(nextIdx, 'a', speechRate)
                                        }, 380L)
                                    } else {
                                        isPlaying = false
                                    }
                                }
                            }
                        }
                    }
                })
                mainHandler.post { ttsReady = true }
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = (episode?.episodeTitle ?: "Not & PDF Podcast Özeti") + " • BETA",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!episode?.sourceSummaryLabel.isNullOrBlank()) {
                        Text(
                            text = episode?.sourceSummaryLabel.orEmpty(),
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = onOpenSourcePicker,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Tune, contentDescription = "Kaynak Seç", modifier = Modifier.size(18.dp))
                    }
                    IconButton(
                        onClick = { if (!isGeneratingPodcast) loadPodcast(forceRefresh = true) },
                        enabled = !isGeneratingPodcast,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Özeti Yenile", modifier = Modifier.size(18.dp))
                    }
                }
            }

            when {
                isGeneratingPodcast -> {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.5.dp,
                                color = colorScheme.primary
                            )
                            Text(
                                text = "Seçili not ve PDF'lerden podcast özeti hazırlanıyor...",
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.onSurface
                            )
                        }
                    }
                }

                segments.isEmpty() -> {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Özetlenecek metin içeren bir ders notu veya PDF seçin.",
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.onSurfaceVariant
                            )
                            OutlinedButton(
                                onClick = onOpenSourcePicker,
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Not / PDF Seç")
                            }
                        }
                    }
                }

                else -> {
                    val safeIdx = activeTrackIdx.coerceIn(0, segments.lastIndex)
                    val seg = segments[safeIdx]

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = colorScheme.surfaceVariant.copy(alpha = 0.42f),
                        border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 210.dp)
                                .verticalScroll(rememberScrollState())
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Bölüm ${safeIdx + 1}/${segments.size} • ${seg.title}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = colorScheme.primary
                            )
                            Text(
                                text = seg.hostA,
                                style = MaterialTheme.typography.bodySmall.copy(lineHeight = 19.sp),
                                fontWeight = FontWeight.Medium,
                                color = colorScheme.onSurface
                            )
                            if (seg.hostB.isNotBlank()) {
                                HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.35f))
                                Text(
                                    text = seg.hostB,
                                    style = MaterialTheme.typography.bodySmall.copy(lineHeight = 19.sp),
                                    color = colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    if (segments.size > 1) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 120.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(segments.size, key = { "pod_ch_$it" }) { idx ->
                                val itemSeg = segments[idx]
                                val isCur = idx == safeIdx
                                Surface(
                                    onClick = {
                                        activeTrackIdx = idx
                                        if (isPlaying) speakSegment(idx, speechRate)
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isCur) colorScheme.primary.copy(alpha = 0.14f) else colorScheme.surfaceVariant.copy(alpha = 0.25f)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "${idx + 1}. ${itemSeg.title}",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = if (isCur) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isCur) colorScheme.primary else colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (isCur && isPlaying) {
                                            Text(
                                                text = "Çalıyor",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = colorScheme.primary
                                            )
                                        }
                                    }
                                }
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
                                        if (isPlaying) speakSegment(activeTrackIdx, rate)
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
                                    speakSegment(prev, speechRate)
                                },
                                enabled = ttsReady && activeTrackIdx > 0
                            ) {
                                Icon(Icons.Default.SkipPrevious, contentDescription = "Önceki")
                            }
                            Surface(
                                onClick = {
                                    if (!ttsReady || segments.isEmpty()) return@Surface
                                    if (isPlaying) {
                                        ttsHolder[0]?.stop()
                                        isPlaying = false
                                    } else {
                                        speakSegment(activeTrackIdx, speechRate)
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
                                    val next = (activeTrackIdx + 1).coerceAtMost((segments.size - 1).coerceAtLeast(0))
                                    speakSegment(next, speechRate)
                                },
                                enabled = ttsReady && activeTrackIdx < segments.lastIndex
                            ) {
                                Icon(Icons.Default.SkipNext, contentDescription = "Sonraki")
                            }
                        }
                    }
                }
            }
        }
    }
}
