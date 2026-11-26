package com.example.sharkmentor.ui.screens.pdf

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.BorderColor
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.sharkmentor.core.ai.AiProviderChoice
import com.example.sharkmentor.core.ai.DeepSeekClient
import com.example.sharkmentor.core.ai.DocumentAiScope
import com.example.sharkmentor.core.ai.GeminiAiFactory
import com.example.sharkmentor.core.ai.MultiProviderAiClient
import com.example.sharkmentor.core.ai.NoteDocumentAiHelper
import com.example.sharkmentor.core.ai.UserAiPreferences
import com.example.sharkmentor.core.data.ThemePreferences
import com.example.sharkmentor.core.theme.ThemeManager
import com.example.sharkmentor.core.util.DocumentTextExtractor
import com.example.sharkmentor.core.util.ImageStorageManager
import com.example.sharkmentor.core.util.ImportedPdfFile
import com.example.sharkmentor.core.util.LazyPdfPageRenderer
import com.example.sharkmentor.core.util.PdfBitmapConverter
import com.example.sharkmentor.core.util.PdfLibraryManager
import com.example.sharkmentor.data.local.SharkMentorDatabase
import com.example.sharkmentor.data.local.entity.NoteEntity
import com.example.sharkmentor.data.repository.NoteRepositoryImpl
import com.example.sharkmentor.ui.components.FloatingFocusTimerOverlay
import com.example.sharkmentor.core.util.SharkStudioBackgroundHelper
import com.example.sharkmentor.ui.components.RichSolutionContent
import com.example.sharkmentor.ui.components.formatMathAsPlainText
import com.example.sharkmentor.ui.screens.notes.NoteDetailScreen
import com.example.sharkmentor.ui.screens.notes.NoteDetailViewModel
import com.example.sharkmentor.ui.screens.notes.drawing.DrawingCanvasOverlay
import com.example.sharkmentor.ui.screens.notes.drawing.DrawingController
import com.example.sharkmentor.ui.screens.notes.drawing.DrawingSerializer
import com.example.sharkmentor.ui.screens.notes.drawing.DrawingStudioBar
import com.example.sharkmentor.ui.screens.notes.drawing.DrawingTool
import com.example.sharkmentor.ui.screens.notes.drawing.LassoAiSolveSheet
import com.example.sharkmentor.ui.screens.notes.drawing.NoteDrawingPreferences
import com.example.sharkmentor.ui.theme.SharkMentorTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.roundToInt

private val pdfNotesPersistenceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

enum class PdfOpenMode {
    CHOOSER,
    PDF_READER,
    EDITABLE_NOTE
}

/**
 * Independent Activity for opening & annotating PDF documents without OOM or Files-app task lockups.
 * Uses on-demand [LazyPdfPageRenderer] (visible ±1 page with LruCache) and per-page JSON annotations.
 */
class PdfReaderActivity : ComponentActivity() {

    companion object {
        const val EXTRA_PDF_FILE_PATH = "extra_pdf_file_path"
        const val EXTRA_PDF_TITLE = "extra_pdf_title"
        const val EXTRA_SKIP_CHOOSER = "extra_skip_chooser"
        const val EXTRA_INITIAL_MODE = "extra_initial_mode"

        fun createIntent(
            context: Context,
            pdfFilePath: String,
            title: String = "PDF Belgesi",
            skipChooser: Boolean = true,
            openAsEditableNote: Boolean = false
        ): Intent {
            return Intent(context, PdfReaderActivity::class.java).apply {
                putExtra(EXTRA_PDF_FILE_PATH, pdfFilePath)
                putExtra(EXTRA_PDF_TITLE, title)
                putExtra(EXTRA_SKIP_CHOOSER, skipChooser)
                putExtra(
                    EXTRA_INITIAL_MODE,
                    if (openAsEditableNote) PdfOpenMode.EDITABLE_NOTE.name else PdfOpenMode.PDF_READER.name
                )
            }
        }
    }

    private var importedPdfState by mutableStateOf<ImportedPdfFile?>(null)
    private var isImporting by mutableStateOf(true)
    private var importError by mutableStateOf<String?>(null)
    private var activeMode by mutableStateOf(PdfOpenMode.CHOOSER)
    private var editableNoteText by mutableStateOf("")
    private var isExtractingText by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        processIncomingIntent(intent)

        setContent {
            val themePrefs = remember { ThemePreferences.initialize(this) }
            val themeMode by themePrefs.themeMode.collectAsStateWithLifecycle()
            val currentPalette by themePrefs.palette.collectAsStateWithLifecycle()
            val isZenModePref by themePrefs.isZenMode.collectAsStateWithLifecycle()
            val isZenMinimalist by ThemeManager.isZenMinimalist.collectAsStateWithLifecycle()
            val isZenMode = isZenModePref || isZenMinimalist

            val coroutineScope = rememberCoroutineScope()

            SharkMentorTheme(
                themeMode = themeMode,
                palette = currentPalette,
                isZenMode = isZenMode
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val pdfDoc = importedPdfState
                    when {
                        isImporting || isExtractingText -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                    Text(
                                        text = if (isExtractingText) "Metin çıkarılıyor..." else "PDF yükleniyor...",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                        importError != null || pdfDoc == null -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PictureAsPdf,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Text(
                                        text = importError ?: "PDF dosyası açılamadı.",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        textAlign = TextAlign.Center
                                    )
                                    Button(onClick = { finish() }) {
                                        Text("Kapat")
                                    }
                                }
                            }
                        }
                        activeMode == PdfOpenMode.CHOOSER -> {
                            PdfOpenChoiceScreen(
                                pdfFile = pdfDoc,
                                onOpenAsPdfReader = {
                                    activeMode = PdfOpenMode.PDF_READER
                                },
                                onOpenAsEditableNote = {
                                    isExtractingText = true
                                    coroutineScope.launch(Dispatchers.IO) {
                                        val cleanText = runCatching {
                                            DocumentTextExtractor.extractCleanText(
                                                context = this@PdfReaderActivity,
                                                uri = pdfDoc.localUri,
                                                displayName = pdfDoc.displayName,
                                                mimeType = "application/pdf"
                                            )
                                        }.getOrDefault("")
                                        withContext(Dispatchers.Main) {
                                            if (cleanText.isBlank()) {
                                                Toast.makeText(
                                                    this@PdfReaderActivity,
                                                    "Belgede metin bulunamadı",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                            editableNoteText = cleanText.take(30_000)
                                            isExtractingText = false
                                            activeMode = PdfOpenMode.EDITABLE_NOTE
                                        }
                                    }
                                },
                                onCancel = { finish() }
                            )
                        }
                        activeMode == PdfOpenMode.EDITABLE_NOTE -> {
                            NoteDetailScreen(
                                noteId = null,
                                initialTitle = pdfDoc.title,
                                initialContent = editableNoteText,
                                incomingFileUri = pdfDoc.localUri,
                                initialPdfPreview = null,
                                initialPdfPagePaths = emptyList(),
                                onDismiss = { finish() },
                                onSave = { _, _, _, _ ->
                                    Toast.makeText(
                                        this@PdfReaderActivity,
                                        "Not kaydedildi",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            )
                        }
                        activeMode == PdfOpenMode.PDF_READER -> {
                            PdfReaderScreen(
                                pdfFile = pdfDoc,
                                onBackClick = { finish() },
                                onSwitchToEditableNote = {
                                    isExtractingText = true
                                    coroutineScope.launch(Dispatchers.IO) {
                                        val cleanText = runCatching {
                                            DocumentTextExtractor.extractCleanText(
                                                context = this@PdfReaderActivity,
                                                uri = pdfDoc.localUri,
                                                displayName = pdfDoc.displayName,
                                                mimeType = "application/pdf"
                                            )
                                        }.getOrDefault("")
                                        withContext(Dispatchers.Main) {
                                            if (cleanText.isBlank()) {
                                                Toast.makeText(
                                                    this@PdfReaderActivity,
                                                    "Belgede metin bulunamadı",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                            editableNoteText = cleanText.take(30_000)
                                            isExtractingText = false
                                            activeMode = PdfOpenMode.EDITABLE_NOTE
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        processIncomingIntent(intent)
    }

    private fun processIncomingIntent(intent: Intent?) {
        if (intent == null) {
            isImporting = false
            importError = "Geçersiz PDF isteği."
            return
        }

        val skipChooser = intent.getBooleanExtra(EXTRA_SKIP_CHOOSER, false)
        val initialModeStr = intent.getStringExtra(EXTRA_INITIAL_MODE)
        val explicitPath = intent.getStringExtra(EXTRA_PDF_FILE_PATH)
        val explicitTitle = intent.getStringExtra(EXTRA_PDF_TITLE)

        isImporting = true
        importError = null

        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            try {
                // 1. Check if launched internally with an already-copied local file path
                if (!explicitPath.isNullOrBlank()) {
                    val file = File(explicitPath)
                    if (file.exists() && file.length() > 0L) {
                        val count = PdfBitmapConverter.readPageCountSafely(file)
                        val title = explicitTitle?.takeIf { it.isNotBlank() }
                            ?: file.name.substringBeforeLast('.', file.name)
                        val imported = ImportedPdfFile(
                            localFile = file,
                            localUri = Uri.fromFile(file),
                            displayName = file.name,
                            title = title,
                            pageCount = count
                        )
                        runCatching {
                            PdfLibraryManager.registerOrUpdatePdf(
                                context = this@PdfReaderActivity,
                                imported = imported,
                                titleOverride = explicitTitle?.takeIf { it.isNotBlank() }
                            )
                        }
                        val targetMode = if (skipChooser) {
                            runCatching { PdfOpenMode.valueOf(initialModeStr ?: "") }
                                .getOrDefault(PdfOpenMode.PDF_READER)
                        } else {
                            PdfOpenMode.CHOOSER
                        }

                        if (targetMode == PdfOpenMode.EDITABLE_NOTE) {
                            val extracted = runCatching {
                                DocumentTextExtractor.extractCleanText(
                                    context = this@PdfReaderActivity,
                                    uri = imported.localUri,
                                    displayName = imported.displayName,
                                    mimeType = "application/pdf"
                                )
                            }.getOrDefault("").take(30_000)
                            withContext(Dispatchers.Main) {
                                if (extracted.isBlank()) {
                                    Toast.makeText(
                                        this@PdfReaderActivity,
                                        "Belgede metin bulunamadı",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                                editableNoteText = extracted
                            }
                        }

                        withContext(Dispatchers.Main) {
                            importedPdfState = imported
                            activeMode = targetMode
                            isImporting = false
                        }
                        return@launch
                    }
                }

                // 2. External ACTION_VIEW or ACTION_SEND URI
                val incomingUri: Uri? = when (intent.action) {
                    Intent.ACTION_VIEW -> intent.data
                    Intent.ACTION_SEND -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(Intent.EXTRA_STREAM)
                        }
                    }
                    else -> intent.data
                }

                if (incomingUri == null) {
                    withContext(Dispatchers.Main) {
                        isImporting = false
                        importError = "PDF dosya bağlantısı (URI) bulunamadı."
                    }
                    return@launch
                }

                val copied = PdfBitmapConverter.copyUriToPrivatePdfsDir(this@PdfReaderActivity, incomingUri)
                if (copied != null && copied.pageCount > 0) {
                    runCatching {
                        PdfLibraryManager.registerOrUpdatePdf(
                            context = this@PdfReaderActivity,
                            imported = copied
                        )
                    }
                }
                withContext(Dispatchers.Main) {
                    if (copied != null && copied.pageCount > 0) {
                        importedPdfState = copied
                        activeMode = if (skipChooser) PdfOpenMode.PDF_READER else PdfOpenMode.CHOOSER
                        isImporting = false
                    } else {
                        isImporting = false
                        importError = "PDF belgesi okunamadı veya bozuk."
                    }
                }
            } catch (e: Exception) {
                Log.e("PdfReaderActivity", "Failed processing incoming PDF intent", e)
                withContext(Dispatchers.Main) {
                    isImporting = false
                    importError = "PDF yüklenirken hata oluştu: ${e.localizedMessage}"
                }
            }
        }
    }
}

/**
 * Modern selection dialog presented when opening a PDF:
 * 1. "PDF Olarak Aç" (Lazy PdfRenderer, Pinch-to-Zoom, Highlighter/Pen/Lasso Annotations, AI Assistant)
 * 2. "Not Olarak Aç (Düzenlenebilir)" (Clean text extraction via DocumentTextExtractor into Rich Note Editor)
 */
@Composable
fun PdfOpenChoiceDialog(
    title: String,
    pageCount: Int,
    onOpenAsPdfReader: () -> Unit,
    onOpenAsEditableNote: () -> Unit,
    onDismiss: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = colorScheme.surface,
            tonalElevation = 8.dp,
            shadowElevation = 12.dp,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 420.dp)
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = colorScheme.primaryContainer,
                        modifier = Modifier.size(46.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.PictureAsPdf,
                                contentDescription = null,
                                tint = colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (pageCount > 0) {
                            Text(
                                text = "$pageCount sayfa",
                                style = MaterialTheme.typography.labelMedium,
                                color = colorScheme.onSurface.copy(alpha = 0.65f)
                            )
                        }
                    }
                }

                // Option 1: PDF Olarak Aç
                Surface(
                    onClick = onOpenAsPdfReader,
                    shape = RoundedCornerShape(16.dp),
                    color = colorScheme.primaryContainer.copy(alpha = 0.55f),
                    border = BorderStroke(1.5.dp, colorScheme.primary)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = colorScheme.primary,
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.PictureAsPdf,
                                    contentDescription = null,
                                    tint = colorScheme.onPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "PDF Olarak Aç",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = colorScheme.onSurface
                            )
                            Text(
                                text = "Yakınlaştırma, çizim ve vurgu",
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.onSurface.copy(alpha = 0.72f),
                                lineHeight = 16.sp
                            )
                        }
                    }
                }

                // Option 2: Not Olarak Aç (Düzenlenebilir)
                Surface(
                    onClick = onOpenAsEditableNote,
                    shape = RoundedCornerShape(16.dp),
                    color = colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, colorScheme.onSurface.copy(alpha = 0.18f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = colorScheme.secondaryContainer,
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = null,
                                    tint = colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Not Olarak Aç (Düzenlenebilir)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = colorScheme.onSurface
                            )
                            Text(
                                text = "Metni düzenlenebilir nota aktar",
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.onSurface.copy(alpha = 0.72f),
                                lineHeight = 16.sp
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("İptal")
                    }
                }
            }
        }
    }
}

@Composable
private fun PdfOpenChoiceScreen(
    pdfFile: ImportedPdfFile,
    onOpenAsPdfReader: () -> Unit,
    onOpenAsEditableNote: () -> Unit,
    onCancel: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        PdfOpenChoiceDialog(
            title = pdfFile.title,
            pageCount = pdfFile.pageCount,
            onOpenAsPdfReader = onOpenAsPdfReader,
            onOpenAsEditableNote = onOpenAsEditableNote,
            onDismiss = onCancel
        )
    }
}

/**
 * Full-featured, OOM-safe PDF Reader with:
 * - Lazy on-demand [LazyPdfPageRenderer] (only visible ±1 pages in LruCache)
 * - Pinch-to-zoom (1.0x..4.0x), pan, double-tap zoom, and quick zoom controls
 * - Page indicator badge & quick scrubber slider
 * - Underline / Highlighter & full Drawing Studio per-page JSON annotations
 * - Integrated AI Note Assistant (Vision + Text analysis + Save as Note)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfReaderScreen(
    pdfFile: ImportedPdfFile,
    onBackClick: () -> Unit,
    onSwitchToEditableNote: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val colorScheme = MaterialTheme.colorScheme
    val surfaceColor = colorScheme.surface
    val onSurfaceColor = colorScheme.onSurface
    val primaryColor = colorScheme.primary
    val textMutedColor = onSurfaceColor.copy(alpha = 0.6f)

    val pdfIdentifier = pdfFile.localFile.absolutePath
    val lazyRenderer = remember(pdfIdentifier) {
        LazyPdfPageRenderer(pdfFile.localFile)
    }

    DisposableEffect(lazyRenderer) {
        onDispose {
            lazyRenderer.close()
        }
    }

    val totalPages = lazyRenderer.pageCount.coerceAtLeast(1)
    val initialPage = remember(pdfIdentifier, totalPages) {
        DrawingSerializer.loadPdfLastReadPage(context, pdfIdentifier)
            .coerceIn(0, (totalPages - 1).coerceAtLeast(0))
    }

    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialPage)

    val currentPageIndex by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val visibleItems = layoutInfo.visibleItemsInfo
            if (visibleItems.isEmpty()) {
                listState.firstVisibleItemIndex.coerceIn(0, totalPages - 1)
            } else {
                val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
                visibleItems.minByOrNull { item ->
                    kotlin.math.abs((item.offset + item.size / 2) - viewportCenter)
                }?.index?.coerceIn(0, totalPages - 1) ?: listState.firstVisibleItemIndex
            }
        }
    }

    // Persist last read page and trim LruCache around visible ±1 page
    LaunchedEffect(currentPageIndex) {
        lazyRenderer.trimAroundVisiblePage(currentPageIndex, keepRadius = 1)
        val appCtx = context.applicationContext
        pdfNotesPersistenceScope.launch {
            DrawingSerializer.savePdfLastReadPage(appCtx, pdfIdentifier, currentPageIndex)
            val annotatedCount = DrawingSerializer.getPdfAnnotatedPageCount(appCtx, pdfIdentifier)
            PdfLibraryManager.updateProgressAndAnnotations(
                context = appCtx,
                pdfFilePath = pdfIdentifier,
                lastReadPage = currentPageIndex,
                annotatedPageCount = annotatedCount
            )
        }
    }

    val pdfRenderMutex = remember(lazyRenderer) { Mutex() }

    // Per-page DrawingController cache so each page has its own annotations & 60-step Undo/Redo
    val pageControllers = remember(pdfIdentifier) {
        mutableStateMapOf<Int, DrawingController>()
    }

    // Track library persistence state
    var isFlushingDrawings by remember(pdfIdentifier) { mutableStateOf(false) }
    var isSavingPageNote by remember(pdfIdentifier) { mutableStateOf(false) }
    var isExportingPdf by remember(pdfIdentifier) { mutableStateOf(false) }
    var lastSavedTimestamp by remember(pdfIdentifier) { mutableStateOf(System.currentTimeMillis()) }

    // Drawing panel open state + floating circular pen button position
    var isDrawingPanelOpen by remember { mutableStateOf(false) }
    var penButtonOffset by remember { mutableStateOf(Offset.Unspecified) }
    var globalActiveTool by remember { mutableStateOf(DrawingTool.NONE) }
    var globalPenColor by remember { mutableStateOf(Color(0xFF1E88E5)) }
    var globalStrokeWidth by remember { mutableFloatStateOf(4.5f) }
    var globalAlpha by remember { mutableFloatStateOf(1.0f) }

    LaunchedEffect(context) {
        NoteDrawingPreferences.ensureLoaded(context)
    }
    val showFloatingFocusTimer by NoteDrawingPreferences.showFloatingFocusTimer.collectAsStateWithLifecycle()

    fun getOrCreatePageController(pageIdx: Int): DrawingController {
        return pageControllers.getOrPut(pageIdx) {
            val loadedStrokes = DrawingSerializer.loadPdfPageAnnotations(context, pdfIdentifier, pageIdx)
            val loadedTextBoxes = DrawingSerializer.loadPdfPageTextBoxes(context, pdfIdentifier, pageIdx)
            DrawingController(
                maxHistorySteps = 60,
                onStrokesChanged = { updatedStrokes ->
                    val appCtx = context.applicationContext
                    val snapshot = updatedStrokes.toList()
                    pdfNotesPersistenceScope.launch {
                        DrawingSerializer.savePdfPageAnnotations(
                            context = appCtx,
                            pdfIdentifier = pdfIdentifier,
                            pageIndex = pageIdx,
                            strokes = snapshot
                        )
                        val annotatedCount = DrawingSerializer.getPdfAnnotatedPageCount(appCtx, pdfIdentifier)
                        PdfLibraryManager.updateProgressAndAnnotations(
                            context = appCtx,
                            pdfFilePath = pdfIdentifier,
                            lastReadPage = currentPageIndex,
                            annotatedPageCount = annotatedCount
                        )
                    }
                },
                onTextBoxesChanged = { updatedBoxes ->
                    val appCtx = context.applicationContext
                    val snapshot = updatedBoxes.toList()
                    pdfNotesPersistenceScope.launch {
                        DrawingSerializer.savePdfPageTextBoxes(
                            context = appCtx,
                            pdfIdentifier = pdfIdentifier,
                            pageIndex = pageIdx,
                            textBoxes = snapshot
                        )
                        val annotatedCount = DrawingSerializer.getPdfAnnotatedPageCount(appCtx, pdfIdentifier)
                        PdfLibraryManager.updateProgressAndAnnotations(
                            context = appCtx,
                            pdfFilePath = pdfIdentifier,
                            lastReadPage = currentPageIndex,
                            annotatedPageCount = annotatedCount
                        )
                    }
                }
            ).apply {
                bindPreferencesContext(context)
                setInitialStrokes(loadedStrokes)
                setInitialTextBoxes(loadedTextBoxes)
                selectTool(if (isDrawingPanelOpen) globalActiveTool else DrawingTool.NONE)
                if (!globalActiveTool.isPenTool) {
                    penColor = globalPenColor
                    strokeWidth = globalStrokeWidth
                    alpha = globalAlpha
                }
            }
        }
    }

    val activePageController = getOrCreatePageController(currentPageIndex)

    // Keep global tool, pen package, eraser package, and preferences synchronized across all PDF pages
    LaunchedEffect(
        isDrawingPanelOpen,
        activePageController.activeTool,
        activePageController.lastSelectedPenTool,
        activePageController.lastSelectedEraserTool,
        activePageController.penColor,
        activePageController.strokeWidth,
        activePageController.alpha,
        currentPageIndex
    ) {
        val effectiveTool = if (isDrawingPanelOpen) activePageController.activeTool else DrawingTool.NONE
        globalActiveTool = effectiveTool
        globalPenColor = activePageController.penColor
        globalStrokeWidth = activePageController.strokeWidth
        globalAlpha = activePageController.alpha

        pageControllers.values.forEach { ctrl ->
            if (ctrl !== activePageController) {
                ctrl.updateLastSelectedPenTool(activePageController.lastSelectedPenTool)
                ctrl.updateLastSelectedEraserTool(activePageController.lastSelectedEraserTool, context)
                if (ctrl.activeTool != effectiveTool) {
                    ctrl.selectTool(effectiveTool)
                }
                ctrl.penColor = globalPenColor
                ctrl.strokeWidth = globalStrokeWidth
                ctrl.alpha = globalAlpha
                ctrl.syncFromNotePreferences()
            }
        }
    }

    // Pinch-to-Zoom & Pan state
    var zoomScale by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }

    var showMoreMenu by remember { mutableStateOf(false) }
    var showPageScrubber by remember { mutableStateOf(true) }

    // AI Assistant state (respects Notes AI & Drawing AI settings toggles)
    val aiPrefs = remember(context) { UserAiPreferences.initialize(context) }
    val notesAiEnabled by aiPrefs.notesAiEnabled.collectAsStateWithLifecycle()
    val drawingAiEnabled by aiPrefs.drawingAiEnabled.collectAsStateWithLifecycle()
    var showAiSheet by remember { mutableStateOf(false) }
    var showPdfSearchDialog by remember { mutableStateOf(false) }
    var pdfSearchQuery by remember { mutableStateOf("") }
    var pdfSearchResults by remember { mutableStateOf<List<Pair<Int, String>>>(emptyList()) }
    var isSearchingPdf by remember { mutableStateOf(false) }
    val pdfMusicEnabled by com.example.sharkmentor.core.audio.MusicPlayerHub.isEnabled.collectAsStateWithLifecycle()
    val pdfMusicMiniOpen by com.example.sharkmentor.core.audio.MusicPlayerHub.isFloatingMiniOpen.collectAsStateWithLifecycle()
    val pdfMusicPlayback by com.example.sharkmentor.core.audio.MusicPlayerHub.playbackState.collectAsStateWithLifecycle()
    val pdfMusicSkin by com.example.sharkmentor.core.audio.MusicPlayerHub.selectedSkin.collectAsStateWithLifecycle()
    var aiQueryText by remember { mutableStateOf("") }
    var aiScope by remember { mutableStateOf(DocumentAiScope.CURRENT_PAGE) }
    var aiResult by remember { mutableStateOf<String?>(null) }
    var isAiLoading by remember { mutableStateOf(false) }
    var aiError by remember { mutableStateOf<String?>(null) }
    var aiScopeNotice by remember { mutableStateOf<String?>(null) }
    var aiSavedAsNote by remember { mutableStateOf(false) }
    var aiJob by remember { mutableStateOf<Job?>(null) }
    var aiRequestGen by remember { mutableIntStateOf(0) }

    // Per-document fast text caches (PDFBox text layer ONLY; never runs slow ML Kit OCR)
    val pageTextCache = remember(pdfIdentifier) { mutableMapOf<Int, String>() }
    var fullTextCache by remember(pdfIdentifier) { mutableStateOf<String?>(null) }

    // Inline Lasso -> "AI'ya Sor" state (keeps PDF open & drawings intact)
    var lassoAiCropBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var lassoAiSourceLabel by remember { mutableStateOf("PDF") }

    fun flushAllPageAnnotationsToLibrary(showToast: Boolean = false) {
        if (isFlushingDrawings && showToast) return
        if (showToast) isFlushingDrawings = true
        val appCtx = context.applicationContext
        pageControllers.values.forEach { it.closeActiveTextBox() }
        val strokeSnapshots = pageControllers.entries.associate { (idx, ctrl) -> idx to ctrl.strokes.toList() }
        val boxSnapshots = pageControllers.entries.associate { (idx, ctrl) -> idx to ctrl.textBoxes.toList() }
        val pageSnap = currentPageIndex
        pdfNotesPersistenceScope.launch {
            runCatching {
                strokeSnapshots.forEach { (idx, strokes) ->
                    DrawingSerializer.savePdfPageAnnotations(
                        context = appCtx,
                        pdfIdentifier = pdfIdentifier,
                        pageIndex = idx,
                        strokes = strokes
                    )
                }
                boxSnapshots.forEach { (idx, boxes) ->
                    DrawingSerializer.savePdfPageTextBoxes(
                        context = appCtx,
                        pdfIdentifier = pdfIdentifier,
                        pageIndex = idx,
                        textBoxes = boxes
                    )
                }
                DrawingSerializer.savePdfLastReadPage(appCtx, pdfIdentifier, pageSnap)
                PdfLibraryManager.registerOrUpdatePdf(appCtx, pdfFile)
                val annotatedCount = DrawingSerializer.getPdfAnnotatedPageCount(appCtx, pdfIdentifier)
                PdfLibraryManager.updateProgressAndAnnotations(
                    context = appCtx,
                    pdfFilePath = pdfIdentifier,
                    lastReadPage = pageSnap,
                    annotatedPageCount = annotatedCount
                )
            }.onFailure { e ->
                Log.e("PdfReaderActivity", "Failed flushing PDF annotations", e)
            }
            if (showToast) {
                withContext(Dispatchers.Main) {
                    isFlushingDrawings = false
                    lastSavedTimestamp = System.currentTimeMillis()
                    Toast.makeText(
                        context,
                        "PDF ve tüm çizimler PDF'lerim'e kaydedildi",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    // Ensure document is registered in PDF'lerim library as soon as opened
    LaunchedEffect(pdfIdentifier) {
        val appCtx = context.applicationContext
        pdfNotesPersistenceScope.launch {
            runCatching { PdfLibraryManager.registerOrUpdatePdf(appCtx, pdfFile) }
        }
    }

    DisposableEffect(pdfIdentifier) {
        onDispose {
            flushAllPageAnnotationsToLibrary(showToast = false)
            aiJob?.cancel()
            aiJob = null
            lassoAiCropBitmap?.let { if (!it.isRecycled) runCatching { it.recycle() } }
            lassoAiCropBitmap = null
        }
    }

    fun cancelPdfAiAnalysis() {
        aiRequestGen++
        aiJob?.cancel()
        aiJob = null
        isAiLoading = false
    }

    /**
     * Saves the CURRENT page (including all user drawings baked into the image + any readable text)
     * as a real note inside `Notlarım` instead of creating an empty "X sayfa" stub.
     */
    fun saveCurrentPageAsNote() {
        if (isSavingPageNote) return
        isSavingPageNote = true
        val appCtx = context.applicationContext
        val pageIdx = currentPageIndex.coerceIn(0, (totalPages - 1).coerceAtLeast(0))
        val pageStrokes = pageControllers[pageIdx]?.strokes?.toList()
            ?: DrawingSerializer.loadPdfPageAnnotations(appCtx, pdfIdentifier, pageIdx)
        val baseTitle = pdfFile.title.ifBlank { pdfFile.displayName.substringBeforeLast('.', "PDF Belgesi") }

        pdfNotesPersistenceScope.launch {
            val savedOk = runCatching {
                val renderedBmp = PdfLibraryManager.renderAnnotatedPageToBitmap(
                    pdfFile = pdfFile.localFile,
                    pageIndex = pageIdx,
                    strokes = pageStrokes,
                    maxWidthPx = 1440
                )
                var imagePath: String? = null
                if (renderedBmp != null && !renderedBmp.isRecycled) {
                    try {
                        val cropsDir = File(appCtx.filesDir, "lasso_crops").apply {
                            if (!exists()) mkdirs()
                        }
                        val outFile = File(cropsDir, "pdf_page_${UUID.randomUUID()}.jpg")
                        FileOutputStream(outFile).use { fos ->
                            renderedBmp.compress(Bitmap.CompressFormat.JPEG, 86, fos)
                            fos.flush()
                        }
                        if (outFile.exists() && outFile.length() > 0L) {
                            imagePath = outFile.absolutePath
                        }
                    } finally {
                        renderedBmp.recycle()
                    }
                }

                val extractedPageText = pageTextCache[pageIdx]
                    ?: PdfBitmapConverter.extractPdfPageRangeTextFast(
                        context = appCtx,
                        pdfFile = pdfFile.localFile,
                        startPageZeroBased = pageIdx,
                        endPageZeroBased = pageIdx,
                        maxChars = 6_000
                    ).also { if (it.isNotBlank()) pageTextCache[pageIdx] = it }

                val noteContent = buildString {
                    if (!imagePath.isNullOrBlank()) {
                        append("[PDF_PAGE:")
                        append(imagePath)
                        append("]")
                    }
                    if (extractedPageText.isNotBlank()) {
                        if (isNotEmpty()) append("\n\n")
                        append(extractedPageText)
                    } else if (isEmpty()) {
                        append("$baseTitle • Sayfa ${pageIdx + 1}/$totalPages")
                    }
                }

                val now = System.currentTimeMillis()
                val note = NoteEntity(
                    id = UUID.randomUUID().toString(),
                    title = "$baseTitle — Sayfa ${pageIdx + 1}",
                    content = noteContent,
                    colorHex = "#F8FAFC",
                    isPinned = false,
                    createdAt = now,
                    updatedAt = now
                )
                val db = SharkMentorDatabase.getDatabase(appCtx)
                val repo = NoteRepositoryImpl(db.noteDao(), appCtx)
                repo.insertOrUpdateNote(note)
                true
            }.getOrElse { e ->
                Log.e("PdfReaderActivity", "Failed saving annotated PDF page as note", e)
                false
            }

            withContext(Dispatchers.Main) {
                isSavingPageNote = false
                Toast.makeText(
                    context,
                    if (savedOk) "Sayfa ${pageIdx + 1} (çizimlerle) Notlarım'a kaydedildi" else "Sayfa not olarak kaydedilemedi",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    /**
     * Exports a shareable PDF copy with all vector drawings baked in (original PDF untouched).
     */
    fun shareAnnotatedPdf() {
        if (isExportingPdf) return
        isExportingPdf = true
        val appCtx = context.applicationContext
        val snapshots = pageControllers.entries.associate { (idx, ctrl) -> idx to ctrl.strokes.toList() }
        coroutineScope.launch {
            val exportFile = withContext(Dispatchers.IO) {
                // Flush latest drawings first
                snapshots.forEach { (idx, strokes) ->
                    DrawingSerializer.savePdfPageAnnotations(appCtx, pdfIdentifier, idx, strokes)
                }
                PdfLibraryManager.exportAnnotatedPdfToCache(
                    context = appCtx,
                    pdfFile = pdfFile.localFile,
                    title = pdfFile.title
                )
            }
            isExportingPdf = false
            if (exportFile != null && exportFile.exists()) {
                runCatching {
                    val shareUri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        exportFile
                    )
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/pdf"
                        putExtra(Intent.EXTRA_STREAM, shareUri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "PDF Paylaş"))
                }.onFailure {
                    Toast.makeText(context, "PDF paylaşılamadı", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(context, "PDF hazırlanamadı", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun runPdfAiAnalysis(
        promptQuery: String,
        scope: DocumentAiScope = aiScope
    ) {
        aiJob?.cancel()
        val requestId = ++aiRequestGen
        val anchorPage = currentPageIndex.coerceIn(0, (totalPages - 1).coerceAtLeast(0))

        isAiLoading = true
        aiError = null
        aiScopeNotice = null
        aiResult = ""
        aiSavedAsNote = false

        val mainHandler = Handler(Looper.getMainLooper())
        aiJob = SharkStudioBackgroundHelper.backgroundScope.launch {
            val ownedBitmaps = mutableListOf<Bitmap>()
            try {
                UserAiPreferences.initialize(context.applicationContext)
                val selectedProvider = UserAiPreferences.getOrNull(context)?.getSelectedProvider()
                    ?: AiProviderChoice.AUTO

                var lastUiPostMs = 0L
                fun postThrottledPartial(partial: String) {
                    if (partial.isEmpty()) return
                    val now = System.currentTimeMillis()
                    if (lastUiPostMs == 0L || now - lastUiPostMs >= 50L) {
                        lastUiPostMs = now
                        mainHandler.post {
                            if (requestId == aiRequestGen) {
                                aiResult = partial
                                isAiLoading = false
                            }
                        }
                    }
                }

                val effectivePrompt = promptQuery.trim().ifBlank {
                    when (scope) {
                        DocumentAiScope.CURRENT_PAGE ->
                            "Bu sayfadaki ana konuları, önemli kavramları ve varsa soruları özetle."
                        DocumentAiScope.NEARBY_PAGES ->
                            "Seçilen sayfalardaki ana konuları ve önemli çalışma noktalarını özetle."
                        DocumentAiScope.WHOLE_DOC_TEXT ->
                            "Bu belgenin genel özetini çıkar ve en kritik çalışma noktalarını maddeler halinde listele."
                    }
                }

                // Fast text-only path when user explicitly picks WHOLE_DOC_TEXT (no ML Kit OCR!)
                var effectiveVisionScope: DocumentAiScope? = scope
                var wholeDocTextForAi = ""
                if (scope == DocumentAiScope.WHOLE_DOC_TEXT) {
                    val cachedFull = fullTextCache ?: PdfBitmapConverter.extractPdfTextOnlyFast(
                        context = context.applicationContext,
                        pdfFile = pdfFile.localFile,
                        maxPages = 25,
                        maxChars = 10_000
                    ).also { fullTextCache = it }

                    if (cachedFull.length >= 80) {
                        wholeDocTextForAi = cachedFull
                        effectiveVisionScope = null
                    } else {
                        effectiveVisionScope = DocumentAiScope.CURRENT_PAGE
                        withContext(Dispatchers.Main) {
                            if (requestId == aiRequestGen) {
                                aiScopeNotice =
                                    "Bu PDF taranmış görsel formatında; aktif sayfa (${anchorPage + 1}) görsel olarak analiz ediliyor."
                            }
                        }
                    }
                }

                var visionFailureMessage: String? = null
                if (effectiveVisionScope != null) {
                    val targetIndices = NoteDocumentAiHelper.selectPageIndicesForScope(
                        currentPage = anchorPage,
                        totalPages = totalPages,
                        scope = effectiveVisionScope
                    )
                    val targetMaxDim = if (targetIndices.size <= 1) 1024 else 800

                    // Render ONLY the 1-3 scoped pages and take compact owned copies (never touch LruCache bitmaps)
                    for (idx in targetIndices) {
                        val rendered = try {
                            pdfRenderMutex.withLock {
                                lazyRenderer.renderPage(
                                    pageIndex = idx,
                                    maxWidthPx = targetMaxDim,
                                    visiblePageHint = anchorPage
                                )
                            }
                        } catch (_: OutOfMemoryError) {
                            System.gc()
                            null
                        } catch (_: Throwable) {
                            null
                        }
                        if (rendered != null && !rendered.isRecycled) {
                            val compactCopy = ImageStorageManager.prepareOptimizedBitmapFromBitmap(
                                source = rendered,
                                maxDimension = targetMaxDim,
                                enhanceContrast = false
                            )
                            if (compactCopy != null && !compactCopy.isRecycled) {
                                ownedBitmaps.add(compactCopy)
                            }
                        }
                    }

                    // Fast cached PDFBox text layer for ONLY those scoped pages (strictly NO OCR!)
                    val scopedPageText = buildString {
                        for (idx in targetIndices) {
                            val cachedPageTxt = pageTextCache[idx] ?: PdfBitmapConverter.extractPdfPageRangeTextFast(
                                context = context.applicationContext,
                                pdfFile = pdfFile.localFile,
                                startPageZeroBased = idx,
                                endPageZeroBased = idx,
                                maxChars = 2_400
                            ).also { pageTextCache[idx] = it }
                            if (cachedPageTxt.isNotBlank()) {
                                if (isNotEmpty()) append("\n\n")
                                append(cachedPageTxt)
                            }
                        }
                    }.take(3_500)

                    if (ownedBitmaps.isNotEmpty()) {
                        val pageLabel = if (targetIndices.size == 1) {
                            "Sayfa ${targetIndices.first() + 1} / $totalPages"
                        } else {
                            "Sayfalar ${targetIndices.first() + 1}-${targetIndices.last() + 1} / $totalPages"
                        }
                        val visionSystem = NoteDetailViewModel.ISOLATED_NOTE_SYSTEM_INSTRUCTION.trim()
                        val visionPrompt = buildString {
                            append("[PDF BAŞLIĞI]: ${pdfFile.title}\n")
                            append("[ODAK]: $pageLabel\n")
                            if (scopedPageText.length >= 40) {
                                append("[SAYFA METİN KATMANI (Yardımcı Bağlam)]:\n")
                                append(scopedPageText)
                                append("\n\n")
                            }
                            append("[KULLANICI İSTEĞİ]: $effectivePrompt")
                        }

                        val visionResult = GeminiAiFactory.streamContentWithFailover(
                            context = context.applicationContext,
                            systemInstruction = visionSystem,
                            userPrompt = visionPrompt,
                            bitmaps = ownedBitmaps,
                            responseJson = false,
                            maxOutputTokens = 6144,
                            timeoutMs = 55_000L,
                            onPartialText = { partial ->
                                postThrottledPartial(partial.trimStart())
                            }
                        )

                        if (visionResult.isSuccess) {
                            val finalVisionText = visionResult.getOrThrow().text.trim()
                            if (finalVisionText.isNotBlank()) {
                                withContext(Dispatchers.Main) {
                                    if (requestId == aiRequestGen) {
                                        aiResult = finalVisionText
                                        aiError = null
                                        isAiLoading = false
                                    }
                                }
                                return@launch
                            }
                        } else {
                            val err = visionResult.exceptionOrNull()
                            if (err is CancellationException) throw err
                            visionFailureMessage = err?.localizedMessage
                            Log.w("PdfReaderAI", "Scoped vision analysis fallback: $visionFailureMessage")
                        }
                    }

                    // If vision failed but we have fast page text, fall through to text path
                    if (wholeDocTextForAi.isBlank() && scopedPageText.isNotBlank()) {
                        wholeDocTextForAi = scopedPageText
                    }
                }

                if (wholeDocTextForAi.isBlank()) {
                    withContext(Dispatchers.Main) {
                        if (requestId == aiRequestGen) {
                            aiError = visionFailureMessage
                                ?: "PDF sayfası analiz edilemedi. Lütfen internet bağlantınızı ve API ayarlarınızı kontrol edin."
                            isAiLoading = false
                        }
                    }
                    return@launch
                }

                val contextualPrompt = """
                    [PDF BAŞLIĞI]: ${pdfFile.title} (Aktif Sayfa ${anchorPage + 1} / $totalPages)
                    [PDF METNİ]:
                    $wholeDocTextForAi

                    [KULLANICI SORUSU / İSTEĞİ]:
                    $effectivePrompt
                """.trimIndent()

                val prefs = UserAiPreferences.getOrNull(context)
                val targetExternalProvider: AiProviderChoice? = when (selectedProvider) {
                    AiProviderChoice.GEMINI -> null
                    AiProviderChoice.CLAUDE ->
                        AiProviderChoice.CLAUDE.takeIf { MultiProviderAiClient.getClaudeApiKey(context).isNotBlank() }
                    AiProviderChoice.OPENAI ->
                        AiProviderChoice.OPENAI.takeIf { MultiProviderAiClient.getOpenAiApiKey(context).isNotBlank() }
                    AiProviderChoice.GROK ->
                        AiProviderChoice.GROK.takeIf { MultiProviderAiClient.getGrokApiKey(context).isNotBlank() }
                    AiProviderChoice.DEEPSEEK ->
                        AiProviderChoice.DEEPSEEK.takeIf { DeepSeekClient.getDeepSeekApiKey(context).isNotBlank() }
                    AiProviderChoice.KIMI ->
                        AiProviderChoice.KIMI.takeIf { DeepSeekClient.getKimiApiKey(context).isNotBlank() }
                    AiProviderChoice.AUTO -> {
                        val activeAuto = prefs?.getActiveProviderAndModel()?.first ?: AiProviderChoice.GEMINI
                        if (activeAuto != AiProviderChoice.GEMINI && MultiProviderAiClient.hasAnyExternalProviderKey(context)) {
                            activeAuto
                        } else {
                            null
                        }
                    }
                }

                if (targetExternalProvider != null) {
                    lastUiPostMs = 0L
                    try {
                        val streamed = MultiProviderAiClient.streamChatCompletion(
                            provider = targetExternalProvider,
                            systemPrompt = NoteDetailViewModel.ISOLATED_NOTE_SYSTEM_INSTRUCTION.trim(),
                            userPrompt = contextualPrompt,
                            temperature = 0.2f,
                            maxTokens = 4096,
                            context = context,
                            onAccumulatedChunk = { partial ->
                                postThrottledPartial(partial)
                            }
                        )
                        if (streamed.isNotBlank()) {
                            withContext(Dispatchers.Main) {
                                if (requestId == aiRequestGen) {
                                    aiResult = streamed.trim()
                                    aiError = null
                                    isAiLoading = false
                                }
                            }
                            return@launch
                        }
                    } catch (ce: CancellationException) {
                        throw ce
                    } catch (e: Exception) {
                        Log.w("PdfReaderAI", "${targetExternalProvider.shortLabel} fallback to Gemini: ${e.message}")
                    }
                }

                lastUiPostMs = 0L
                val geminiTextResult = GeminiAiFactory.streamContentWithFailover(
                    context = context.applicationContext,
                    systemInstruction = NoteDetailViewModel.ISOLATED_NOTE_SYSTEM_INSTRUCTION.trim(),
                    userPrompt = contextualPrompt,
                    responseJson = false,
                    maxOutputTokens = 6144,
                    timeoutMs = 55_000L,
                    onPartialText = { partial ->
                        postThrottledPartial(partial.trimStart())
                    }
                )

                if (geminiTextResult.isSuccess) {
                    val finalText = geminiTextResult.getOrThrow().text.trim()
                    withContext(Dispatchers.Main) {
                        if (requestId == aiRequestGen) {
                            if (finalText.isNotBlank()) {
                                aiResult = finalText
                                aiError = null
                            } else {
                                aiError = "AI boş yanıt döndürdü. Lütfen tekrar deneyin."
                            }
                            isAiLoading = false
                        }
                    }
                } else {
                    val err = geminiTextResult.exceptionOrNull()
                    if (err is CancellationException) throw err
                    withContext(Dispatchers.Main) {
                        if (requestId == aiRequestGen) {
                            aiError = err?.localizedMessage ?: visionFailureMessage ?: "AI analizi sırasında hata oluştu."
                            isAiLoading = false
                        }
                    }
                }
            } catch (e: Throwable) {
                if (e !is CancellationException) {
                    withContext(Dispatchers.Main) {
                        if (requestId == aiRequestGen) {
                            aiError = e.localizedMessage ?: "AI analizi sırasında hata oluştu."
                        }
                    }
                }
            } finally {
                ownedBitmaps.forEach { bmp ->
                    runCatching { if (!bmp.isRecycled) bmp.recycle() }
                }
                withContext(Dispatchers.Main) {
                    if (requestId == aiRequestGen) {
                        isAiLoading = false
                        if (!aiResult.isNullOrBlank()) {
                            SharkStudioBackgroundHelper.notifyTaskCompleted(
                                context = context,
                                notificationId = 3012,
                                title = "SharkAI PDF Asistanı Hazır",
                                body = "PDF sayfa ${currentPageIndex + 1} yanıtı tamamlandı."
                            )
                        }
                    }
                }
            }
        }
    }

    fun saveAiResultToNotes(summaryText: String) {
        if (summaryText.isBlank()) return
        coroutineScope.launch(Dispatchers.IO) {
            runCatching {
                val db = SharkMentorDatabase.getDatabase(context.applicationContext)
                val repo = NoteRepositoryImpl(db.noteDao(), context.applicationContext)
                val note = NoteEntity(
                    id = UUID.randomUUID().toString(),
                    title = "[PDF Notu] ${pdfFile.title} (S.${currentPageIndex + 1})",
                    content = summaryText.trim(),
                    colorHex = "#F8FAFC",
                    isPinned = false,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
                repo.insertOrUpdateNote(note)
                withContext(Dispatchers.Main) {
                    aiSavedAsNote = true
                    Toast.makeText(context, "Notlara kaydedildi", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    BackHandler {
        if (isDrawingPanelOpen || activePageController.activeTool != DrawingTool.NONE) {
            isDrawingPanelOpen = false
            activePageController.selectTool(DrawingTool.NONE)
            pageControllers.values.forEach { it.selectTool(DrawingTool.NONE) }
        } else if (zoomScale > 1.05f) {
            zoomScale = 1f
            panOffset = Offset.Zero
        } else {
            flushAllPageAnnotationsToLibrary(showToast = false)
            onBackClick()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1E293B))
            .navigationBarsPadding()
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown && keyEvent.isCtrlPressed && keyEvent.key == Key.S) {
                    flushAllPageAnnotationsToLibrary(showToast = true)
                    true
                } else {
                    false
                }
            }
    ) {
        // Top App Bar
        TopAppBar(
            expandedHeight = 50.dp,
            title = {
                Column {
                    Text(
                        text = pdfFile.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = onSurfaceColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "S.${currentPageIndex + 1}/$totalPages" +
                            if (zoomScale > 1.02f) " • %${(zoomScale * 100).roundToInt()}" else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = primaryColor,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            },
            navigationIcon = {
                IconButton(
                    onClick = {
                        flushAllPageAnnotationsToLibrary(showToast = false)
                        onBackClick()
                    }
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Geri",
                        tint = onSurfaceColor
                    )
                }
            },
            actions = {
                // Library auto-save / manual flush pill button
                Surface(
                    onClick = { flushAllPageAnnotationsToLibrary(showToast = true) },
                    enabled = !isFlushingDrawings,
                    shape = RoundedCornerShape(16.dp),
                    color = primaryColor.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.45f)),
                    modifier = Modifier.padding(end = 2.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (isFlushingDrawings) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(13.dp),
                                strokeWidth = 1.8.dp,
                                color = primaryColor
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "PDF'lerim'e Kaydet",
                                tint = primaryColor,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        Text(
                            text = if (isFlushingDrawings) "Kaydediliyor" else "Kaydedildi",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = primaryColor,
                            maxLines = 1
                        )
                    }
                }

                // Music button right to the LEFT of the AI icon
                if (pdfMusicEnabled) {
                    IconButton(
                        onClick = { com.example.sharkmentor.core.audio.MusicPlayerHub.toggleFloatingMini() },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = "SharkMusic",
                            tint = if (pdfMusicMiniOpen || pdfMusicPlayback.isPlaying) primaryColor else onSurfaceColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // AI Note Assistant button (controlled by Notes AI toggle in Settings)
                if (notesAiEnabled) {
                    IconButton(
                        onClick = { showAiSheet = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "SharkAI",
                            tint = primaryColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Search magnifying glass button right to the RIGHT of the AI icon
                IconButton(
                    onClick = { showPdfSearchDialog = true },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "PDF İçinde Ara",
                        tint = if (pdfSearchQuery.isNotBlank()) primaryColor else onSurfaceColor,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // More Menu
                Box {
                    IconButton(
                        onClick = { showMoreMenu = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Diğer Seçenekler",
                            tint = onSurfaceColor,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = showMoreMenu,
                        onDismissRequest = { showMoreMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Çizimleri Şimdi Kaydet") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = primaryColor,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            onClick = {
                                showMoreMenu = false
                                flushAllPageAnnotationsToLibrary(showToast = true)
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    if (isSavingPageNote) "Sayfa Kaydediliyor..."
                                    else "Bu Sayfayı Not Olarak Kaydet"
                                )
                            },
                            enabled = !isSavingPageNote,
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.NoteAdd,
                                    contentDescription = null,
                                    tint = primaryColor,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            onClick = {
                                showMoreMenu = false
                                saveCurrentPageAsNote()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Tüm PDF'i Metin Notuna Dönüştür") },
                            leadingIcon = {
                                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(20.dp))
                            },
                            onClick = {
                                showMoreMenu = false
                                flushAllPageAnnotationsToLibrary(showToast = false)
                                onSwitchToEditableNote()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(if (zoomScale > 1.05f) "Yakınlaştırmayı Sıfırla (%100)" else "Yakınlaştır (%200)") },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (zoomScale > 1.05f) Icons.Default.Remove else Icons.Default.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            onClick = {
                                showMoreMenu = false
                                if (zoomScale > 1.05f) {
                                    zoomScale = 1f
                                    panOffset = Offset.Zero
                                } else {
                                    zoomScale = 2f
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    if (showFloatingFocusTimer) "Odak Sayacını Gizle" else "Odak Sayacını Göster"
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Timer,
                                    contentDescription = null,
                                    tint = if (showFloatingFocusTimer) primaryColor else onSurfaceColor,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            onClick = {
                                showMoreMenu = false
                                NoteDrawingPreferences.setShowFloatingFocusTimer(context, !showFloatingFocusTimer)
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    if (isExportingPdf) "PDF Hazırlanıyor..."
                                    else "Çizimli PDF Paylaş"
                                )
                            },
                            enabled = !isExportingPdf,
                            leadingIcon = {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(20.dp))
                            },
                            onClick = {
                                showMoreMenu = false
                                shareAnnotatedPdf()
                            }
                        )
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = surfaceColor)
        )

        // Top-Docked Drawing Panel (1:1 identical to Notes Drawing Panel) flush directly below TopAppBar
        AnimatedVisibility(
            visible = isDrawingPanelOpen,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            DrawingStudioBar(
                controller = activePageController,
                isCompactMode = true,
                showNavigationButton = false,
                shape = RoundedCornerShape(0.dp),
                onCloseStudio = null,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Main PDF Viewport with Pinch-to-Zoom & Pan + Lazy On-Demand Page Rendering + Floating Circular Pen Button
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color(0xFF0F172A))
        ) {
            val density = LocalDensity.current
            val viewportWidthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
            val viewportHeightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)

            val zoomGestureModifier = if (!isDrawingPanelOpen && activePageController.activeTool == DrawingTool.NONE) {
                Modifier
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = {
                                if (zoomScale > 1.15f) {
                                    zoomScale = 1f
                                    panOffset = Offset.Zero
                                } else {
                                    zoomScale = 2.2f
                                }
                            },
                            onTap = {
                                showPageScrubber = !showPageScrubber
                            }
                        )
                    }
                    .pointerInput(zoomScale > 1.01f) {
                        if (zoomScale > 1.01f) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                val newScale = (zoomScale * zoom).coerceIn(1f, 4f)
                                zoomScale = newScale
                                if (newScale > 1.01f) {
                                    val maxX = (viewportWidthPx * (newScale - 1f)) * 0.5f
                                    val maxY = (viewportHeightPx * (newScale - 1f)) * 0.5f
                                    panOffset = Offset(
                                        x = (panOffset.x + pan.x).coerceIn(-maxX, maxX),
                                        y = (panOffset.y + pan.y).coerceIn(-maxY, maxY)
                                    )
                                } else {
                                    panOffset = Offset.Zero
                                }
                            }
                        }
                    }
            } else {
                Modifier
            }

            LazyColumn(
                state = listState,
                userScrollEnabled = !isDrawingPanelOpen && activePageController.activeTool == DrawingTool.NONE && zoomScale <= 1.05f,
                contentPadding = PaddingValues(
                    horizontal = 10.dp,
                    vertical = 12.dp
                ),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxSize()
                    .then(zoomGestureModifier)
                    .graphicsLayer {
                        scaleX = zoomScale
                        scaleY = zoomScale
                        translationX = panOffset.x
                        translationY = panOffset.y
                    }
            ) {
                items(
                    count = totalPages,
                    key = { pageIdx -> "pdf_page_$pageIdx" }
                ) { pageIdx ->
                    val pageController = getOrCreatePageController(pageIdx)
                    val isNearVisible = kotlin.math.abs(pageIdx - currentPageIndex) <= 1

                    LazyPdfPageCard(
                        pageIndex = pageIdx,
                        totalPages = totalPages,
                        isNearVisible = isNearVisible,
                        currentVisiblePage = currentPageIndex,
                        renderer = lazyRenderer,
                        renderMutex = pdfRenderMutex,
                        drawingController = pageController,
                        onTwoFingerScroll = if (zoomScale <= 1.05f) {
                            { dy, _ -> listState.dispatchRawDelta(-dy) }
                        } else {
                            null
                        },
                        onLassoAskAi = if (drawingAiEnabled) {
                            { cropBmp ->
                                lassoAiCropBitmap?.let { if (!it.isRecycled) runCatching { it.recycle() } }
                                lassoAiSourceLabel = "PDF • Sayfa ${pageIdx + 1}"
                                lassoAiCropBitmap = cropBmp
                            }
                        } else {
                            null
                        }
                    )
                }
            }

            // Draggable Floating Circular Pen Button (remains visible while top-docked panel is open;
            // single tap toggles drawing panel open/closed, locking annotations read-only on close)
            val penBtnSizeDp = 48.dp
            val penBtnSizePx = with(density) { penBtnSizeDp.toPx() }
            val btnMarginPx = with(density) { 14.dp.toPx() }
            val maxBtnX = (viewportWidthPx - penBtnSizePx - btnMarginPx).coerceAtLeast(btnMarginPx)
            val maxBtnY = (viewportHeightPx - penBtnSizePx - with(density) { 64.dp.toPx() }).coerceAtLeast(btnMarginPx)
            val resolvedBtnOffset = if (penButtonOffset == Offset.Unspecified) {
                Offset(maxBtnX, maxBtnY)
            } else {
                Offset(
                    x = penButtonOffset.x.coerceIn(btnMarginPx, maxBtnX),
                    y = penButtonOffset.y.coerceIn(btnMarginPx, maxBtnY)
                )
            }

            Surface(
                shape = CircleShape,
                color = if (isDrawingPanelOpen) primaryColor else surfaceColor.copy(alpha = 0.96f),
                tonalElevation = 8.dp,
                shadowElevation = 10.dp,
                border = BorderStroke(
                    width = 1.5.dp,
                    color = if (isDrawingPanelOpen) colorScheme.onPrimary.copy(alpha = 0.35f) else primaryColor.copy(alpha = 0.7f)
                ),
                modifier = Modifier
                    .zIndex(20f)
                    .size(penBtnSizeDp)
                    .offset { IntOffset(resolvedBtnOffset.x.roundToInt(), resolvedBtnOffset.y.roundToInt()) }
                    .pointerInput(viewportWidthPx, viewportHeightPx, isDrawingPanelOpen, currentPageIndex) {
                        val touchSlop = viewConfiguration.touchSlop
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            down.consume()
                            val startOffset = if (penButtonOffset == Offset.Unspecified) {
                                Offset(maxBtnX, maxBtnY)
                            } else {
                                penButtonOffset
                            }
                            var totalMoved = 0f
                            var dragged = false
                            var currentOffset = startOffset

                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id }
                                if (change == null || !change.pressed) break
                                val delta = change.positionChange()
                                totalMoved += delta.getDistance()
                                if (!dragged && totalMoved > touchSlop) {
                                    dragged = true
                                }
                                if (dragged) {
                                    change.consume()
                                    currentOffset = Offset(
                                        x = (currentOffset.x + delta.x).coerceIn(btnMarginPx, maxBtnX),
                                        y = (currentOffset.y + delta.y).coerceIn(btnMarginPx, maxBtnY)
                                    )
                                    penButtonOffset = currentOffset
                                }
                            }

                            if (!dragged) {
                                if (isDrawingPanelOpen) {
                                    isDrawingPanelOpen = false
                                    activePageController.selectTool(DrawingTool.NONE)
                                    pageControllers.values.forEach { it.selectTool(DrawingTool.NONE) }
                                } else {
                                    isDrawingPanelOpen = true
                                    activePageController.selectActiveOrLastPen()
                                }
                            }
                        }
                    }
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = if (isDrawingPanelOpen) "Çizim Panelini Kapat" else "Çizim Panelini Aç",
                        tint = if (isDrawingPanelOpen) colorScheme.onPrimary else primaryColor,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // Live-synced Draggable / Resizable Floating Focus Timer Overlay
            FloatingFocusTimerOverlay(
                viewportWidthPx = viewportWidthPx,
                viewportHeightPx = viewportHeightPx
            )

            // Floating Zoom Controls (Top-Right)
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp),
                shape = RoundedCornerShape(20.dp),
                color = surfaceColor.copy(alpha = 0.90f),
                tonalElevation = 4.dp,
                shadowElevation = 6.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    IconButton(
                        onClick = {
                            val next = (zoomScale - 0.25f).coerceAtLeast(1f)
                            zoomScale = next
                            if (next <= 1.01f) panOffset = Offset.Zero
                        },
                        enabled = zoomScale > 1.01f,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Remove,
                            contentDescription = "Uzaklaştır",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Text(
                        text = "%${(zoomScale * 100).roundToInt()}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = onSurfaceColor,
                        modifier = Modifier
                            .clickable {
                                zoomScale = if (zoomScale > 1.05f) 1f else 2f
                                panOffset = Offset.Zero
                            }
                            .padding(horizontal = 4.dp)
                    )
                    IconButton(
                        onClick = {
                            zoomScale = (zoomScale + 0.25f).coerceAtMost(4f)
                        },
                        enabled = zoomScale < 3.99f,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Yakınlaştır",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // Floating Bottom Page Indicator & Scrubber Bar
            androidx.compose.animation.AnimatedVisibility(
                visible = showPageScrubber && totalPages > 1,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = surfaceColor.copy(alpha = 0.94f),
                    tonalElevation = 6.dp,
                    shadowElevation = 8.dp,
                    border = BorderStroke(1.dp, onSurfaceColor.copy(alpha = 0.12f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 560.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        TextButton(
                            onClick = {
                                if (currentPageIndex > 0) {
                                    coroutineScope.launch {
                                        listState.scrollToItem(currentPageIndex - 1)
                                    }
                                }
                            },
                            enabled = currentPageIndex > 0,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("Önceki", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }

                        var sliderPage by remember(currentPageIndex) {
                            mutableFloatStateOf(currentPageIndex.toFloat())
                        }

                        Slider(
                            value = sliderPage,
                            onValueChange = { sliderPage = it },
                            onValueChangeFinished = {
                                val target = sliderPage.roundToInt().coerceIn(0, totalPages - 1)
                                coroutineScope.launch {
                                    listState.scrollToItem(target)
                                }
                            },
                            valueRange = 0f..(totalPages - 1).coerceAtLeast(1).toFloat(),
                            modifier = Modifier
                                .weight(1f)
                                .height(26.dp)
                        )

                        Text(
                            text = "${currentPageIndex + 1} / $totalPages",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = primaryColor
                        )

                        TextButton(
                            onClick = {
                                if (currentPageIndex < totalPages - 1) {
                                    coroutineScope.launch {
                                        listState.scrollToItem(currentPageIndex + 1)
                                    }
                                }
                            },
                            enabled = currentPageIndex < totalPages - 1,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("Sonraki", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }

    // AI Note Assistant ModalBottomSheet
    if (pdfMusicEnabled && pdfMusicMiniOpen) {
        com.example.sharkmentor.ui.components.DraggableMiniMusicPlayerWindow(
            playbackState = pdfMusicPlayback,
            selectedSkin = pdfMusicSkin,
            onSelectSkin = { skin -> com.example.sharkmentor.core.audio.MusicPlayerHub.setSkin(context, skin) },
            onMinimize = { com.example.sharkmentor.core.audio.MusicPlayerHub.setFloatingMiniOpen(false) },
            modifier = Modifier.fillMaxSize()
        )
    }

    if (showPdfSearchDialog) {
        AlertDialog(
            onDismissRequest = { showPdfSearchDialog = false },
            title = { Text("PDF İçinde Ara", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedTextField(
                            value = pdfSearchQuery,
                            onValueChange = { pdfSearchQuery = it },
                            placeholder = { Text("Kelime yaz...") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                val q = pdfSearchQuery.trim()
                                if (q.isBlank() || isSearchingPdf) return@Button
                                coroutineScope.launch {
                                    isSearchingPdf = true
                                    val found = kotlinx.coroutines.withContext(Dispatchers.IO) {
                                        val matches = mutableListOf<Pair<Int, String>>()
                                        val maxP = totalPages.coerceAtMost(80)
                                        for (pIdx in 0 until maxP) {
                                            val pageTxt = pageTextCache[pIdx] ?: runCatching {
                                                PdfBitmapConverter.extractPdfPageRangeTextFast(
                                                    context = context.applicationContext,
                                                    pdfFile = pdfFile.localFile,
                                                    startPageZeroBased = pIdx,
                                                    endPageZeroBased = pIdx,
                                                    maxChars = 6_000
                                                )
                                            }.getOrDefault("").also { pageTextCache[pIdx] = it }
                                            val hitIdx = pageTxt.indexOf(q, ignoreCase = true)
                                            if (hitIdx >= 0) {
                                                val s = (hitIdx - 28).coerceAtLeast(0)
                                                val e = (hitIdx + q.length + 52).coerceAtMost(pageTxt.length)
                                                matches.add(pIdx to pageTxt.substring(s, e).replace("\n", " ").trim())
                                            }
                                        }
                                        matches
                                    }
                                    pdfSearchResults = found
                                    isSearchingPdf = false
                                }
                            },
                            enabled = !isSearchingPdf && pdfSearchQuery.isNotBlank(),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Text(if (isSearchingPdf) "..." else "Ara", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    if (pdfSearchResults.isNotEmpty()) {
                        Text(
                            text = "${pdfSearchResults.size} sayfada bulundu (gitmek için dokun):",
                            style = MaterialTheme.typography.labelSmall,
                            color = primaryColor,
                            fontWeight = FontWeight.SemiBold
                        )
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 220.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            pdfSearchResults.take(25).forEach { (pageIdx, snippet) ->
                                Surface(
                                    onClick = {
                                        showPdfSearchDialog = false
                                        coroutineScope.launch {
                                            runCatching { listState.scrollToItem(pageIdx) }
                                        }
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "Sayfa ${pageIdx + 1}: ...$snippet...",
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(8.dp)
                                    )
                                }
                            }
                        }
                    } else if (!isSearchingPdf && pdfSearchQuery.isNotBlank()) {
                        Text(
                            text = "Ara butonuna basarak PDF içinde arayabilirsiniz.",
                            style = MaterialTheme.typography.labelSmall,
                            color = onSurfaceColor.copy(alpha = 0.65f)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPdfSearchDialog = false }) { Text("Kapat") }
            }
        )
    }

    if (showAiSheet && notesAiEnabled) {
        ModalBottomSheet(
            onDismissRequest = {
                cancelPdfAiAnalysis()
                showAiSheet = false
            },
            containerColor = surfaceColor,
            contentColor = onSurfaceColor
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = primaryColor
                    )
                    Text(
                        text = "SharkAI • PDF S.${currentPageIndex + 1}/$totalPages",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = onSurfaceColor
                    )
                }

                if (totalPages > 1) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DocumentAiScope.entries.forEach { scopeOption ->
                            FilterChip(
                                selected = aiScope == scopeOption,
                                onClick = { aiScope = scopeOption },
                                label = { Text(text = scopeOption.label, fontSize = 12.sp) }
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        "Sayfayı özetle",
                        "Kavramları açıkla",
                        "Soru-cevap",
                        "Önemli noktalar"
                    ).forEach { quickPrompt ->
                        SuggestionChip(
                            onClick = {
                                aiQueryText = quickPrompt
                                runPdfAiAnalysis(quickPrompt, aiScope)
                            },
                            label = {
                                Text(
                                    text = quickPrompt,
                                    color = onSurfaceColor,
                                    fontSize = 12.sp
                                )
                            }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = aiQueryText,
                        onValueChange = { aiQueryText = it },
                        placeholder = {
                            Text("Soru sor...", color = textMutedColor)
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = primaryColor,
                            unfocusedBorderColor = onSurfaceColor.copy(alpha = 0.3f),
                            focusedTextColor = onSurfaceColor,
                            unfocusedTextColor = onSurfaceColor,
                            cursorColor = primaryColor
                        )
                    )
                    if (isAiLoading) {
                        IconButton(onClick = { cancelPdfAiAnalysis() }) {
                            Icon(
                                imageVector = Icons.Default.Stop,
                                contentDescription = "Durdur",
                                tint = colorScheme.error
                            )
                        }
                    } else {
                        IconButton(onClick = { runPdfAiAnalysis(aiQueryText, aiScope) }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Analiz Et",
                                tint = primaryColor
                            )
                        }
                    }
                }

                if (!aiScopeNotice.isNullOrBlank()) {
                    Text(
                        text = aiScopeNotice ?: "",
                        color = textMutedColor,
                        style = MaterialTheme.typography.labelSmall
                    )
                }

                if (isAiLoading && aiResult.isNullOrBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = primaryColor,
                            strokeWidth = 2.dp
                        )
                        Text(
                            text = "Sayfa inceleniyor...",
                            style = MaterialTheme.typography.bodySmall,
                            color = textMutedColor
                        )
                    }
                }

                if (aiError != null) {
                    Text(
                        text = aiError ?: "",
                        color = colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                if (!aiResult.isNullOrBlank()) {
                    if (aiSavedAsNote) {
                        Surface(
                            color = primaryColor.copy(alpha = 0.10f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = primaryColor,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = "Notlara kaydedildi",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium,
                                    color = primaryColor
                                )
                            }
                        }
                    }

                    Surface(
                        color = colorScheme.background,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(14.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            SelectionContainer {
                                RichSolutionContent(
                                    text = aiResult ?: "",
                                    textColor = onSurfaceColor
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                Button(
                                    onClick = { saveAiResultToNotes(formatMathAsPlainText(aiResult ?: "")) },
                                    enabled = !aiSavedAsNote,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = primaryColor,
                                        contentColor = colorScheme.onPrimary
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = if (aiSavedAsNote) "Kaydedildi" else "Notlara Kaydet",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Inline Lasso -> "AI'ya Sor" Solve Sheet (opens over PDF without closing or losing drawings)
    lassoAiCropBitmap?.let { cropBmp ->
        LassoAiSolveSheet(
            cropBitmap = cropBmp,
            sourceLabel = lassoAiSourceLabel,
            onSaveToNotes = { solutionText ->
                saveAiResultToNotes(solutionText)
            },
            onDismiss = {
                lassoAiCropBitmap = null
                if (!cropBmp.isRecycled) {
                    runCatching { cropBmp.recycle() }
                }
            }
        )
    }
}

@Composable
private fun LazyPdfPageCard(
    pageIndex: Int,
    totalPages: Int,
    isNearVisible: Boolean,
    currentVisiblePage: Int,
    renderer: LazyPdfPageRenderer,
    renderMutex: Mutex,
    drawingController: DrawingController,
    onTwoFingerScroll: ((Float, Float) -> Unit)? = null,
    onLassoAskAi: ((Bitmap) -> Unit)? = null
) {
    var pageBitmap by remember(pageIndex) {
        mutableStateOf(renderer.getCachedPage(pageIndex))
    }
    var pageAspectRatio by remember(pageIndex) {
        mutableFloatStateOf(0.707f) // Standard A4 portrait aspect ratio (W / H)
    }
    var hasRenderError by remember(pageIndex) {
        mutableStateOf(false)
    }

    LaunchedEffect(pageIndex, isNearVisible, currentVisiblePage) {
        if (isNearVisible) {
            hasRenderError = false
            val bmp = try {
                renderMutex.withLock {
                    renderer.renderPage(
                        pageIndex = pageIndex,
                        maxWidthPx = 1120,
                        visiblePageHint = currentVisiblePage
                    )
                }
            } catch (_: OutOfMemoryError) {
                System.gc()
                try {
                    renderMutex.withLock {
                        renderer.renderPage(
                            pageIndex = pageIndex,
                            maxWidthPx = 720,
                            visiblePageHint = currentVisiblePage
                        )
                    }
                } catch (_: Throwable) {
                    hasRenderError = true
                    null
                }
            } catch (_: Throwable) {
                hasRenderError = true
                null
            }
            if (bmp != null && !bmp.isRecycled) {
                pageBitmap = bmp
                if (bmp.height > 0) {
                    pageAspectRatio = (bmp.width.toFloat() / bmp.height.toFloat()).coerceIn(0.35f, 2.5f)
                }
            } else if (bmp == null) {
                hasRenderError = true
            }
        } else {
            // Release reference when page scrolls far away so LruCache can reclaim memory
            pageBitmap = null
        }
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color.White,
        shadowElevation = 6.dp,
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 860.dp)
            .aspectRatio(pageAspectRatio)
            .clip(RoundedCornerShape(8.dp))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            val currentBmp = pageBitmap
            if (currentBmp != null && !currentBmp.isRecycled) {
                Image(
                    bitmap = currentBmp.asImageBitmap(),
                    contentDescription = "PDF Sayfa ${pageIndex + 1}",
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier.fillMaxSize()
                )
            } else if (hasRenderError) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFFF8FAFC))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Sayfa ${pageIndex + 1} yüklenemedi (Bellek koruma aktif)",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF475569),
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        strokeWidth = 2.5.dp
                    )
                }
            }

            // Per-page interactive Drawing & Annotation Canvas Overlay
            DrawingCanvasOverlay(
                controller = drawingController,
                modifier = Modifier.fillMaxSize(),
                onTwoFingerScroll = onTwoFingerScroll,
                onLassoAskAi = onLassoAskAi,
                directContentBitmapProvider = { pageBitmap?.takeIf { !it.isRecycled } }
            )

            // Subtle page number corner badge
            Surface(
                shape = RoundedCornerShape(bottomStart = 8.dp),
                color = Color(0xFF0F172A).copy(alpha = 0.65f),
                modifier = Modifier.align(Alignment.TopEnd)
            ) {
                Text(
                    text = "${pageIndex + 1} / $totalPages",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
    }
}
