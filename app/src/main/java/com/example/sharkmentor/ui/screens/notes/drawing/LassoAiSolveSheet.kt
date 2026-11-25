package com.example.sharkmentor.ui.screens.notes.drawing

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sharkmentor.core.ai.GeminiAiFactory
import com.example.sharkmentor.core.ai.GeminiChatTurn
import com.example.sharkmentor.core.ai.LassoAiMeta
import com.example.sharkmentor.core.ai.NoteDocumentAiHelper
import com.example.sharkmentor.core.ai.SavedErrorBankRecord
import com.example.sharkmentor.core.util.ImageStorageManager
import com.example.sharkmentor.ui.components.RichSolutionContent
import com.example.sharkmentor.ui.components.formatMathAsPlainText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class LassoFollowUpEntry(
    val question: String,
    val answer: String
)

private val INITIAL_LASSO_PROMPT =
    "Seçtiğim bu alanı incele. Eğer bir test veya çalışma sorusuysa adım adım çöz ve doğru cevabı belirt; konu anlatımı veya not parçasıysa özetleyip açıkla."

private val LASSO_FOLLOWUP_SYSTEM_INSTRUCTION = """
Sen öğrencinin kementle seçtiği aynı görsel hakkında sorduğu takip sorusunu yanıtlayan uzman bir çalışma asistanısın.
1. Yapay selamlama, gereksiz giriş veya emoji kullanma; doğrudan soruya net yanıt ver.
2. Yalnızca seçilen görseldeki bilgilere ve önceki çözüme dayan; uydurma bilgi ekleme.
3. Adım adım, düzenli başlıklar ve temiz matematiksel gösterimle (önemli denklemleri ayrı satırda ${'$'}${'$'}...${'$'}${'$'} veya net formül olarak) açıkla.
""".trimIndent()

/**
 * Inline ModalBottomSheet launched directly from the Lasso tool in PDF Reader or Notes.
 *
 * - Keeps the PDF/Note open underneath with scroll position and drawings intact.
 * - Keeps the cropped region purely in RAM (0 Gallery clutter, 0 bytes on disk for non-test content).
 * - If Gemini detects a test question, automatically compresses (<= 140 KB app-private JPEG) and
 *   registers it into Error Bank with a 1-tap Undo ("Geri Al") that deletes both DB row and file.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LassoAiSolveSheet(
    cropBitmap: Bitmap,
    sourceLabel: String,
    onDismiss: () -> Unit,
    onAppendToNote: ((String) -> Unit)? = null,
    onSaveToNotes: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val colorScheme = MaterialTheme.colorScheme
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // RAM-only optimized copy owned by this sheet and recycled automatically on dismiss
    var workingBitmap by remember(cropBitmap) { mutableStateOf<Bitmap?>(null) }
    var isPreviewExpanded by remember(cropBitmap) { mutableStateOf(false) }

    var isStreaming by remember(cropBitmap) { mutableStateOf(true) }
    var isMetaPending by remember(cropBitmap) { mutableStateOf(false) }
    var mainSolutionText by remember(cropBitmap) { mutableStateOf("") }
    var detectedMeta by remember(cropBitmap) { mutableStateOf<LassoAiMeta?>(null) }
    var errorMessage by remember(cropBitmap) { mutableStateOf<String?>(null) }

    val followUps = remember(cropBitmap) { mutableStateListOf<LassoFollowUpEntry>() }
    var activeFollowUpQuestion by remember(cropBitmap) { mutableStateOf<String?>(null) }
    var activeFollowUpAnswer by remember(cropBitmap) { mutableStateOf("") }
    var followUpInput by remember(cropBitmap) { mutableStateOf("") }

    var hasAttemptedAutoSave by remember(cropBitmap) { mutableStateOf(false) }
    var isSavingErrorBank by remember(cropBitmap) { mutableStateOf(false) }
    var savedErrorRecord by remember(cropBitmap) { mutableStateOf<SavedErrorBankRecord?>(null) }
    var wasAutoSaveUndone by remember(cropBitmap) { mutableStateOf(false) }
    var errorBankNotice by remember(cropBitmap) { mutableStateOf<String?>(null) }
    var hasSavedSolutionToNote by remember(cropBitmap) { mutableStateOf(false) }

    var activeJob by remember(cropBitmap) { mutableStateOf<Job?>(null) }
    var retryCounter by remember(cropBitmap) { mutableIntStateOf(0) }

    DisposableEffect(cropBitmap) {
        onDispose {
            activeJob?.cancel()
            activeJob = null
            val owned = workingBitmap
            workingBitmap = null
            if (owned != null && !owned.isRecycled) {
                runCatching { owned.recycle() }
            }
        }
    }

    fun triggerErrorBankSave(targetSubject: String, targetTopic: String) {
        if (isSavingErrorBank || savedErrorRecord != null) return
        val bmp = workingBitmap?.takeIf { !it.isRecycled }
            ?: cropBitmap.takeIf { !it.isRecycled }
            ?: return
        isSavingErrorBank = true
        errorBankNotice = null
        coroutineScope.launch {
            val res = NoteDocumentAiHelper.saveLassoQuestionToErrorBank(
                context = context,
                cropBitmap = bmp,
                subject = targetSubject,
                topic = targetTopic
            )
            isSavingErrorBank = false
            res.onSuccess { record ->
                savedErrorRecord = record
                wasAutoSaveUndone = false
                errorBankNotice = null
            }.onFailure { err ->
                errorBankNotice = err.localizedMessage?.takeIf { it.isNotBlank() }
                    ?: "Hata Bankasına kaydedilemedi."
            }
        }
    }

    fun triggerErrorBankUndo() {
        val record = savedErrorRecord ?: return
        if (isSavingErrorBank) return
        isSavingErrorBank = true
        coroutineScope.launch {
            val res = NoteDocumentAiHelper.undoSaveLassoQuestionToErrorBank(context, record)
            isSavingErrorBank = false
            res.onSuccess {
                savedErrorRecord = null
                wasAutoSaveUndone = true
                errorBankNotice = "Geri alındı • Depolama temizlendi"
            }.onFailure {
                errorBankNotice = "Kayıt geri alınamadı"
            }
        }
    }

    // Auto-start initial Lasso solve immediately when sheet opens (or on retry)
    LaunchedEffect(cropBitmap, retryCounter) {
        activeJob?.cancel()
        isStreaming = true
        isMetaPending = true
        mainSolutionText = ""
        errorMessage = null
        activeFollowUpQuestion = null
        activeFollowUpAnswer = ""

        val job = launch {
            try {
                val prepared = workingBitmap?.takeIf { !it.isRecycled }
                    ?: ImageStorageManager.prepareOptimizedBitmapFromBitmap(
                        source = cropBitmap,
                        maxDimension = 1024,
                        enhanceContrast = false
                    ).also { workingBitmap = it }

                if (prepared == null || prepared.isRecycled) {
                    errorMessage = "Seçilen alan görseli hazırlanamadı."
                    isStreaming = false
                    isMetaPending = false
                    return@launch
                }

                var lastEmitMs = 0L
                val result = GeminiAiFactory.streamContentWithFailover(
                    context = context.applicationContext,
                    systemInstruction = NoteDocumentAiHelper.LASSO_AI_SYSTEM_INSTRUCTION,
                    userPrompt = INITIAL_LASSO_PROMPT,
                    bitmaps = listOf(prepared),
                    responseJson = false,
                    maxOutputTokens = 4096,
                    timeoutMs = 55_000L,
                    onPartialText = { partial ->
                        val now = System.currentTimeMillis()
                        if (lastEmitMs == 0L || now - lastEmitMs >= 50L) {
                            lastEmitMs = now
                            val parsed = NoteDocumentAiHelper.parseLassoAiStream(partial)
                            coroutineScope.launch(Dispatchers.Main.immediate) {
                                if (parsed.meta != null) detectedMeta = parsed.meta
                                isMetaPending = parsed.isMetaPending
                                if (parsed.displayText.isNotEmpty()) {
                                    mainSolutionText = parsed.displayText
                                }
                            }
                        }
                    }
                )

                if (result.isSuccess) {
                    val finalRaw = result.getOrThrow().text.trim()
                    val parsedFinal = NoteDocumentAiHelper.parseLassoAiStream(finalRaw)
                    withContext(Dispatchers.Main) {
                        detectedMeta = parsedFinal.meta
                        isMetaPending = false
                        mainSolutionText = parsedFinal.displayText.ifBlank { finalRaw }
                        isStreaming = false

                        // Auto-register into Error Bank ONCE if Gemini detected a test question
                        val meta = parsedFinal.meta
                        if (meta != null && meta.isTestQuestion && !hasAttemptedAutoSave) {
                            hasAttemptedAutoSave = true
                            triggerErrorBankSave(meta.subject, meta.topic)
                        }
                    }
                } else {
                    val err = result.exceptionOrNull()
                    if (err is CancellationException) throw err
                    withContext(Dispatchers.Main) {
                        isStreaming = false
                        isMetaPending = false
                        errorMessage = err?.localizedMessage?.takeIf { it.isNotBlank() }
                            ?: "Seçilen alan çözümlenemedi. Bağlantınızı kontrol edin."
                    }
                }
            } catch (ce: CancellationException) {
                withContext(Dispatchers.Main) {
                    isStreaming = false
                    isMetaPending = false
                }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) {
                    isStreaming = false
                    isMetaPending = false
                    errorMessage = t.localizedMessage?.takeIf { it.isNotBlank() }
                        ?: "Analiz sırasında beklenmeyen bir hata oluştu."
                }
            }
        }
        activeJob = job
    }

    fun sendFollowUp(questionText: String) {
        val cleanQuestion = questionText.trim()
        if (cleanQuestion.isEmpty() || isStreaming) return
        val prepared = workingBitmap?.takeIf { !it.isRecycled }
            ?: cropBitmap.takeIf { !it.isRecycled }
            ?: return

        followUpInput = ""
        errorMessage = null
        activeFollowUpQuestion = cleanQuestion
        activeFollowUpAnswer = ""
        isStreaming = true

        val history = buildList {
            if (mainSolutionText.isNotBlank()) {
                add(GeminiChatTurn(role = "user", text = INITIAL_LASSO_PROMPT))
                add(GeminiChatTurn(role = "model", text = mainSolutionText))
            }
            followUps.forEach { entry ->
                add(GeminiChatTurn(role = "user", text = entry.question))
                add(GeminiChatTurn(role = "model", text = entry.answer))
            }
        }

        activeJob?.cancel()
        activeJob = coroutineScope.launch {
            try {
                var lastEmitMs = 0L
                val result = GeminiAiFactory.streamContentWithFailover(
                    context = context.applicationContext,
                    systemInstruction = LASSO_FOLLOWUP_SYSTEM_INSTRUCTION,
                    userPrompt = cleanQuestion,
                    chatHistory = history,
                    bitmaps = listOf(prepared),
                    responseJson = false,
                    maxOutputTokens = 3072,
                    timeoutMs = 55_000L,
                    onPartialText = { partial ->
                        val now = System.currentTimeMillis()
                        if (lastEmitMs == 0L || now - lastEmitMs >= 50L) {
                            lastEmitMs = now
                            coroutineScope.launch(Dispatchers.Main.immediate) {
                                activeFollowUpAnswer = partial.trimStart()
                            }
                        }
                    }
                )

                if (result.isSuccess) {
                    val finalReply = result.getOrThrow().text.trim()
                    withContext(Dispatchers.Main) {
                        if (finalReply.isNotBlank()) {
                            followUps.add(LassoFollowUpEntry(cleanQuestion, finalReply))
                        }
                        activeFollowUpQuestion = null
                        activeFollowUpAnswer = ""
                        isStreaming = false
                    }
                } else {
                    val err = result.exceptionOrNull()
                    if (err is CancellationException) throw err
                    withContext(Dispatchers.Main) {
                        activeFollowUpQuestion = null
                        activeFollowUpAnswer = ""
                        isStreaming = false
                        errorMessage = err?.localizedMessage?.takeIf { it.isNotBlank() }
                            ?: "Ek soru yanıtlanamadı."
                    }
                }
            } catch (_: CancellationException) {
                withContext(Dispatchers.Main) {
                    val partial = activeFollowUpAnswer.trim()
                    val q = activeFollowUpQuestion
                    if (!q.isNullOrBlank() && partial.isNotBlank()) {
                        followUps.add(LassoFollowUpEntry(q, partial))
                    }
                    activeFollowUpQuestion = null
                    activeFollowUpAnswer = ""
                    isStreaming = false
                }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) {
                    activeFollowUpQuestion = null
                    activeFollowUpAnswer = ""
                    isStreaming = false
                    errorMessage = t.localizedMessage?.takeIf { it.isNotBlank() }
                        ?: "Ek soru sırasında hata oluştu."
                }
            }
        }
    }

    fun buildExportableText(): String = buildString {
        detectedMeta?.let { meta ->
            append("[${meta.subject} • ${meta.topic}]")
            if (!meta.answer.isNullOrBlank()) {
                append(" • Cevap: ${meta.answer}")
            }
            append("\n\n")
        }
        append(formatMathAsPlainText(mainSolutionText))
        if (followUps.isNotEmpty()) {
            followUps.forEach { entry ->
                append("\n\nSoru: ${entry.question}\n")
                append(formatMathAsPlainText(entry.answer))
            }
        }
    }.trim()

    ModalBottomSheet(
        onDismissRequest = {
            activeJob?.cancel()
            onDismiss()
        },
        sheetState = sheetState,
        containerColor = colorScheme.surface,
        contentColor = colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .padding(bottom = 22.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Compact Header: Crop Thumbnail + Title + Detected Subject/Answer + Close
            val previewBmp = workingBitmap?.takeIf { !it.isRecycled }
                ?: cropBitmap.takeIf { !it.isRecycled }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (previewBmp != null) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(colorScheme.surfaceVariant)
                            .border(
                                1.dp,
                                colorScheme.outlineVariant,
                                RoundedCornerShape(10.dp)
                            )
                            .clickable { isPreviewExpanded = !isPreviewExpanded },
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            bitmap = previewBmp.asImageBitmap(),
                            contentDescription = "Kement seçimi önizleme",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.matchParentSize()
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
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
                            text = "SharkAI • Kement Çözümü",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    val subtitle = buildString {
                        append(sourceLabel)
                        detectedMeta?.let { meta ->
                            append(" • ${meta.subject}")
                            if (meta.topic.isNotBlank() && !meta.topic.equals("Genel", ignoreCase = true)) {
                                append(" / ${meta.topic}")
                            }
                        }
                    }
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                detectedMeta?.answer?.let { ans ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "Cevap: $ans",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                IconButton(
                    onClick = {
                        activeJob?.cancel()
                        onDismiss()
                    },
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Kapat",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Expandable Crop Preview (tapping thumbnail toggles)
            if (isPreviewExpanded && previewBmp != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 90.dp, max = 200.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .border(1.dp, colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                        .clickable { isPreviewExpanded = false }
                        .padding(6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = previewBmp.asImageBitmap(),
                        contentDescription = "Seçilen alan tam önizleme",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Smart Error Bank Status Strip (compact, zero Gallery clutter)
            when {
                isSavingErrorBank -> {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = colorScheme.primaryContainer.copy(alpha = 0.45f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = colorScheme.primary
                            )
                            Text(
                                text = "Test sorusu Hata Bankasına sıkıştırılarak işleniyor...",
                                style = MaterialTheme.typography.labelSmall,
                                color = colorScheme.onSurface
                            )
                        }
                    }
                }

                savedErrorRecord != null -> {
                    val rec = savedErrorRecord!!
                    val sizeLabel = NoteDocumentAiHelper.formatCompactSizeKb(rec.sizeBytes)
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = colorScheme.primary.copy(alpha = 0.10f),
                        border = BorderStroke(1.dp, colorScheme.primary.copy(alpha = 0.25f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = colorScheme.primary,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = "Hata Bankasına işlendi: ${rec.subject} • ${rec.topic} ($sizeLabel)",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = colorScheme.primary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(
                                onClick = { triggerErrorBankUndo() },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Undo,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("Geri Al", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                !isStreaming && mainSolutionText.isNotBlank() -> {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = errorBankNotice ?: if (wasAutoSaveUndone) {
                                    "Geri alındı • Depolama temizlendi"
                                } else {
                                    "Test sorusu değil • Galeri ve depolama kullanılmadı"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(
                                onClick = {
                                    triggerErrorBankSave(
                                        targetSubject = detectedMeta?.subject ?: "Genel Yetenek / Diğer",
                                        targetTopic = detectedMeta?.topic ?: "Genel"
                                    )
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AddCircleOutline,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = if (wasAutoSaveUndone) "Tekrar Ekle" else "Hata Bankasına Ekle",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }

            // Error state with 1-tap retry
            if (errorMessage != null) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = colorScheme.errorContainer.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = errorMessage ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick = { retryCounter++ },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Tekrar Dene", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            // Main Solution + Follow-up Thread Area
            Surface(
                color = colorScheme.surfaceVariant.copy(alpha = 0.45f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, colorScheme.onSurface.copy(alpha = 0.08f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp, max = 340.dp)
            ) {
                val scrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(scrollState)
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (mainSolutionText.isBlank() && isStreaming) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(vertical = 12.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = colorScheme.primary
                            )
                            Text(
                                text = if (isMetaPending) {
                                    "Seçilen alan inceleniyor..."
                                } else {
                                    "Çözüm hazırlanıyor..."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = colorScheme.onSurfaceVariant
                            )
                        }
                    } else if (mainSolutionText.isNotBlank()) {
                        SelectionContainer {
                            RichSolutionContent(
                                text = mainSolutionText,
                                textColor = colorScheme.onSurface
                            )
                        }
                    }

                    followUps.forEach { entry ->
                        HorizontalDivider(color = colorScheme.onSurface.copy(alpha = 0.10f))
                        Text(
                            text = "Soru: ${entry.question}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.primary
                        )
                        SelectionContainer {
                            RichSolutionContent(
                                text = entry.answer,
                                textColor = colorScheme.onSurface,
                                fontSize = 14.sp,
                                lineHeight = 21.sp
                            )
                        }
                    }

                    if (!activeFollowUpQuestion.isNullOrBlank()) {
                        HorizontalDivider(color = colorScheme.onSurface.copy(alpha = 0.10f))
                        Text(
                            text = "Soru: $activeFollowUpQuestion",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.primary
                        )
                        if (activeFollowUpAnswer.isBlank()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp,
                                    color = colorScheme.primary
                                )
                                Text(
                                    text = "Yanıtlanıyor...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            SelectionContainer {
                                RichSolutionContent(
                                    text = activeFollowUpAnswer,
                                    textColor = colorScheme.onSurface,
                                    fontSize = 14.sp,
                                    lineHeight = 21.sp
                                )
                            }
                        }
                    }
                }
            }

            // Follow-up Input + Stop / Send Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = followUpInput,
                    onValueChange = { followUpInput = it },
                    placeholder = {
                        Text(
                            text = "Adımı aç veya ek soru sor...",
                            fontSize = 13.sp,
                            color = colorScheme.onSurfaceVariant
                        )
                    },
                    singleLine = true,
                    enabled = !isStreaming && mainSolutionText.isNotBlank(),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colorScheme.primary,
                        unfocusedBorderColor = colorScheme.onSurface.copy(alpha = 0.25f)
                    )
                )

                if (isStreaming) {
                    IconButton(
                        onClick = {
                            activeJob?.cancel()
                            isStreaming = false
                            isMetaPending = false
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = "Durdur",
                            tint = colorScheme.error
                        )
                    }
                } else {
                    IconButton(
                        onClick = { sendFollowUp(followUpInput) },
                        enabled = followUpInput.isNotBlank() && mainSolutionText.isNotBlank()
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Gönder",
                            tint = if (followUpInput.isNotBlank() && mainSolutionText.isNotBlank()) {
                                colorScheme.primary
                            } else {
                                colorScheme.onSurface.copy(alpha = 0.35f)
                            }
                        )
                    }
                }
            }

            // Compact Action Footer (Copy + Append/Save to Notes)
            if (mainSolutionText.isNotBlank() && !isStreaming) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            val exportText = buildExportableText()
                            if (exportText.isNotBlank()) {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                clipboard?.setPrimaryClip(ClipData.newPlainText("Shark AI Çözüm", exportText))
                                Toast.makeText(context, "Çözüm kopyalandı", Toast.LENGTH_SHORT).show()
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text("Kopyala", fontSize = 12.sp)
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    if (onAppendToNote != null) {
                        Button(
                            onClick = {
                                val exportText = buildExportableText()
                                if (exportText.isNotBlank()) {
                                    onAppendToNote(exportText)
                                    hasSavedSolutionToNote = true
                                    Toast.makeText(context, "Çözüm nota eklendi", Toast.LENGTH_SHORT).show()
                                }
                            },
                            enabled = !hasSavedSolutionToNote,
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colorScheme.primary,
                                contentColor = colorScheme.onPrimary
                            )
                        ) {
                            Text(
                                text = if (hasSavedSolutionToNote) "Nota Eklendi" else "Nota Ekle",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    } else if (onSaveToNotes != null) {
                        Button(
                            onClick = {
                                val exportText = buildExportableText()
                                if (exportText.isNotBlank()) {
                                    onSaveToNotes(exportText)
                                    hasSavedSolutionToNote = true
                                }
                            },
                            enabled = !hasSavedSolutionToNote,
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colorScheme.primary,
                                contentColor = colorScheme.onPrimary
                            )
                        ) {
                            Text(
                                text = if (hasSavedSolutionToNote) "Kaydedildi" else "Notlara Kaydet",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}
