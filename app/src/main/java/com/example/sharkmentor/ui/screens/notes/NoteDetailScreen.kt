package com.example.sharkmentor.ui.screens.notes

import android.app.Activity
import android.content.ContentResolver
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.core.view.WindowCompat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BorderColor
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatColorReset
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VerticalAlignBottom
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material.icons.outlined.PushPin
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
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.sharkmentor.core.ai.DocumentAiScope
import com.example.sharkmentor.core.ai.UserAiPreferences
import com.example.sharkmentor.core.util.DocumentTextExtractor
import com.example.sharkmentor.core.util.NoteStyleParser
import com.example.sharkmentor.core.util.PaperPattern
import com.example.sharkmentor.core.util.PaperTint
import com.example.sharkmentor.core.util.PdfBitmapConverter
import com.example.sharkmentor.core.util.PdfExporter
import com.example.sharkmentor.data.local.entity.NoteEntity
import com.example.sharkmentor.ui.components.FloatingFocusTimerOverlay
import com.example.sharkmentor.ui.components.RichSolutionContent
import com.example.sharkmentor.ui.components.formatMathAsPlainText
import com.example.sharkmentor.ui.screens.notes.drawing.DrawingCanvasOverlay
import com.example.sharkmentor.ui.screens.notes.drawing.DrawingController
import com.example.sharkmentor.ui.screens.notes.drawing.DrawingSerializer
import com.example.sharkmentor.ui.screens.notes.drawing.DrawingTextBoxFormattingPanel
import com.example.sharkmentor.ui.screens.notes.drawing.DrawingTool
import com.example.sharkmentor.ui.screens.notes.drawing.EraserPackagePickerPanel
import com.example.sharkmentor.ui.screens.notes.drawing.LassoAiSolveSheet
import com.example.sharkmentor.ui.screens.notes.drawing.NoteDrawingPreferences
import com.example.sharkmentor.ui.screens.notes.drawing.PenCustomColorDialog
import com.example.sharkmentor.ui.screens.notes.drawing.PenLassoActionsPanel
import com.example.sharkmentor.ui.screens.notes.drawing.PenPackagePickerPanel
import com.example.sharkmentor.ui.screens.notes.drawing.PenRedoGlyph
import com.example.sharkmentor.ui.screens.notes.drawing.PenShapePickerPanel
import com.example.sharkmentor.ui.screens.notes.drawing.PenStrokeSettingsPanel
import com.example.sharkmentor.ui.screens.notes.drawing.PenToolGlyph
import com.example.sharkmentor.ui.screens.notes.drawing.PenUndoGlyph
import com.example.sharkmentor.ui.screens.notes.drawing.StudioToolButton
import com.example.sharkmentor.ui.screens.pdf.PdfReaderActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

private val PDF_PAGE_TAG_REGEX = Regex("\\[PDF_PAGE:([^\\]]+)\\]")
private val PDF_FILE_TAG_REGEX = Regex("\\[PDF_FILE:([^\\]]+)\\]")
private val ALL_PDF_TAGS_REGEX = Regex("""\[PDF_(?:PAGE|FILE):[^\]]+\]\r?\n?""")

private fun extractPdfPagePathsFromContent(rawContent: String): List<String> {
    return PDF_PAGE_TAG_REGEX.findAll(rawContent)
        .map { it.groupValues[1].trim() }
        .filter { it.isNotBlank() && File(it).exists() }
        .distinct()
        .toList()
}

private fun extractPdfFilePathFromContent(rawContent: String): String? {
    return PDF_FILE_TAG_REGEX.find(rawContent)
        ?.groupValues
        ?.getOrNull(1)
        ?.trim()
        ?.takeIf { it.isNotBlank() && File(it).exists() }
}

private fun stripPdfPageTags(rawContent: String): String {
    return rawContent
        .replace(ALL_PDF_TAGS_REGEX, "")
        .trim()
}

private fun composeContentWithPdfPageTags(
    userText: String,
    pagePaths: List<String>,
    pdfFilePath: String? = null
): String {
    val cleanUserText = stripPdfPageTags(userText)
    val tags = buildList {
        if (!pdfFilePath.isNullOrBlank()) {
            add("[PDF_FILE:$pdfFilePath]")
        }
        pagePaths.forEach { path ->
            add("[PDF_PAGE:$path]")
        }
    }
    if (tags.isEmpty()) return cleanUserText
    val tagsHeader = tags.joinToString("\n")
    return if (cleanUserText.isBlank()) {
        tagsHeader
    } else {
        "$tagsHeader\n\n$cleanUserText"
    }
}

val NoteTextRed = Color(0xFFE53935)
val NoteTextBlue = Color(0xFF1E88E5)
val NoteTextGreen = Color(0xFF43A047)
val NoteTextOrange = Color(0xFFFB8C00)
val NoteTextPurple = Color(0xFF8E24AA)
val NoteHighlightYellow = Color(0xFFFFEB3B)

enum class ActiveColorPanel {
    NONE, PEN, HIGHLIGHT, FONT, FONT_SIZE
}

enum class ActivePenPanel {
    NONE, SETTINGS, SHAPES, LASSO, PEN_PACKAGES, ERASER_PACKAGES, TEXT_BOX
}

private data class NoteSearchMatch(
    val pageIndex: Int,
    val start: Int,
    val end: Int
)

enum class ToolbarDockState {
    BOTTOM, TOP, LEFT, RIGHT, FLOATING
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NoteDetailScreen(
    modifier: Modifier = Modifier,
    noteId: String? = null,
    initialNote: NoteEntity? = null,
    initialTitle: String = initialNote?.title ?: "",
    initialContent: String = initialNote?.content ?: "",
    incomingFileUri: Uri? = null,
    initialPdfPreview: Bitmap? = null,
    initialPdfPagePaths: List<String> = emptyList(),
    viewModel: NoteDetailViewModel = viewModel(
        factory = NoteDetailViewModel.Factory(LocalContext.current)
    ),
    onColorSelected: (Color) -> Unit = {},
    onBulletToggled: () -> Unit = {},
    onStyleToggled: (SpanStyle) -> Unit = {},
    onIsolatedNoteAiClick: (title: String, content: String) -> Unit = { _, _ -> },
    onOpenDrawer: (() -> Unit)? = null,
    onDismiss: () -> Unit = {},
    onSave: (title: String, content: String, colorHex: String, isPinned: Boolean) -> Unit = { _, _, _, _ -> }
) {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    val bgColor = colorScheme.background
    val surfaceColor = colorScheme.surface
    val onSurfaceColor = colorScheme.onSurface
    val primaryColor = colorScheme.primary
    val textMutedColor = colorScheme.onSurface.copy(alpha = 0.6f)

    val vmNoteId by viewModel.noteId.collectAsStateWithLifecycle()
    val vmTitle by viewModel.noteTitle.collectAsStateWithLifecycle()
    val vmContent by viewModel.noteContent.collectAsStateWithLifecycle()
    val isPinned by viewModel.isPinned.collectAsStateWithLifecycle()
    val colorHex by viewModel.colorHex.collectAsStateWithLifecycle()
    val aiResult by viewModel.aiResult.collectAsStateWithLifecycle()
    val isAiLoading by viewModel.isAiLoading.collectAsStateWithLifecycle()
    val aiError by viewModel.aiError.collectAsStateWithLifecycle()
    val aiSavedNewNoteId by viewModel.aiSavedNewNoteId.collectAsStateWithLifecycle()
    val aiScopeNotice by viewModel.aiScopeNotice.collectAsStateWithLifecycle()
    val isVmNoteReady by viewModel.isNoteReady.collectAsStateWithLifecycle()

    val activeNoteId = noteId ?: initialNote?.id
    val drawingScope = rememberCoroutineScope()
    var activePenPanel by remember { mutableStateOf(ActivePenPanel.NONE) }
    var preservedPdfFileTag by remember(activeNoteId, initialNote?.id, initialContent) {
        mutableStateOf(extractPdfFilePathFromContent(initialNote?.content ?: initialContent))
    }
    var localPdfFilePath by remember(activeNoteId, initialNote?.id, initialContent, incomingFileUri) {
        mutableStateOf(
            extractPdfFilePathFromContent(initialNote?.content ?: initialContent)
                ?: incomingFileUri
                    ?.takeIf { it.scheme == ContentResolver.SCHEME_FILE }
                    ?.path
                    ?.takeIf { it.endsWith(".pdf", ignoreCase = true) && File(it).exists() }
        )
    }

    val keyboardController = LocalSoftwareKeyboardController.current

    val initialCleanRaw = remember(activeNoteId, initialContent) {
        stripPdfPageTags(initialNote?.content ?: initialContent)
    }

    fun buildSentinelAwarePageValue(
        pageIndex: Int,
        parsed: AnnotatedString,
        cursorAtEnd: Boolean = true
    ): TextFieldValue {
        if (pageIndex <= 0) {
            val pos = if (cursorAtEnd) parsed.text.length else 0
            return TextFieldValue(annotatedString = parsed, selection = TextRange(pos))
        }
        val withSentinel = if (parsed.text.startsWith(NoteRichTextHelper.PAGE_SENTINEL)) {
            parsed
        } else {
            val shiftedSpans = parsed.spanStyles.map { r ->
                AnnotatedString.Range(r.item, r.start + 1, r.end + 1)
            }
            AnnotatedString(
                text = NoteRichTextHelper.PAGE_SENTINEL + parsed.text,
                spanStyles = shiftedSpans
            )
        }
        val pos = if (cursorAtEnd) withSentinel.text.length else 1
        return TextFieldValue(annotatedString = withSentinel, selection = TextRange(pos))
    }

    val pageFieldValues = remember(activeNoteId) {
        val rawPages = NoteDetailViewModel.splitContentIntoPages(initialCleanRaw)
        mutableStateListOf<TextFieldValue>().apply {
            rawPages.forEachIndexed { idx, pageRaw ->
                val parsed = NoteRichTextHelper.parseTaggedText(pageRaw)
                add(buildSentinelAwarePageValue(idx, parsed, cursorAtEnd = true))
            }
            if (isEmpty()) {
                add(TextFieldValue(annotatedString = AnnotatedString(""), selection = TextRange(0)))
            }
        }
    }

    var activePageIndex by remember(activeNoteId) { mutableIntStateOf(0) }
    val safeActivePageIndex = activePageIndex.coerceIn(0, (pageFieldValues.size - 1).coerceAtLeast(0))
    var pageChangeTick by remember { mutableIntStateOf(0) }
    var pendingDeletePageIndex by remember { mutableStateOf<Int?>(null) }

    // Non-snapshot HashMaps avoid mutating Compose state during composition (prevents crash on low-RAM Android 9)
    val pageDrawingControllers = remember(vmNoteId) { HashMap<Int, DrawingController>() }
    val pageFocusRequesters = remember(vmNoteId) { HashMap<Int, FocusRequester>() }

    fun getOrCreatePageFocusRequester(pageIdx: Int): FocusRequester {
        return pageFocusRequesters.getOrPut(pageIdx) { FocusRequester() }
    }

    fun getOrCreatePageDrawingController(pageIdx: Int): DrawingController {
        return pageDrawingControllers.getOrPut(pageIdx) {
            val template = pageDrawingControllers[safeActivePageIndex]
            DrawingController(
                maxHistorySteps = 60,
                onStrokesChanged = { updatedStrokes ->
                    viewModel.saveNotePageDrawingImmediately(context, vmNoteId, pageIdx, updatedStrokes)
                },
                onTextBoxesChanged = { updatedBoxes ->
                    viewModel.saveNotePageTextBoxesImmediately(context, vmNoteId, pageIdx, updatedBoxes)
                }
            ).also { ctrl ->
                ctrl.bindPreferencesContext(context)
                if (template != null) {
                    ctrl.updateLastSelectedPenTool(template.lastSelectedPenTool)
                    ctrl.updateLastSelectedEraserTool(template.lastSelectedEraserTool, context)
                    ctrl.selectTool(template.activeTool)
                    ctrl.penColor = template.penColor
                    ctrl.strokeWidth = template.strokeWidth
                    ctrl.alpha = template.alpha
                    ctrl.eraserRadius = template.eraserRadius
                    ctrl.pressureSensitivityEnabled = template.pressureSensitivityEnabled
                }
            }
        }
    }

    val drawingController = getOrCreatePageDrawingController(safeActivePageIndex)

    // Keep tool/color/stroke/eraser/pressure synchronized across all page controllers
    LaunchedEffect(
        drawingController.activeTool,
        drawingController.lastSelectedPenTool,
        drawingController.lastSelectedEraserTool,
        drawingController.penColor,
        drawingController.strokeWidth,
        drawingController.alpha,
        drawingController.eraserRadius,
        drawingController.pressureSensitivityEnabled,
        pageFieldValues.size
    ) {
        pageDrawingControllers.values.forEach { ctrl ->
            if (ctrl !== drawingController) {
                ctrl.updateLastSelectedPenTool(drawingController.lastSelectedPenTool)
                ctrl.updateLastSelectedEraserTool(drawingController.lastSelectedEraserTool, context)
                ctrl.selectTool(drawingController.activeTool)
                ctrl.penColor = drawingController.penColor
                ctrl.strokeWidth = drawingController.strokeWidth
                ctrl.alpha = drawingController.alpha
                ctrl.eraserRadius = drawingController.eraserRadius
                ctrl.pressureSensitivityEnabled = drawingController.pressureSensitivityEnabled
            }
        }
    }

    LaunchedEffect(vmNoteId, pageFieldValues.size) {
        if (vmNoteId.isNotBlank()) {
            val totalPages = pageFieldValues.size
            for (idx in 0 until totalPages) {
                val ctrl = getOrCreatePageDrawingController(idx)
                val loaded = withContext(Dispatchers.IO) {
                    DrawingSerializer.loadNotePageAnnotations(context, vmNoteId, idx)
                }
                ctrl.setInitialStrokes(loaded.strokes)
                ctrl.setInitialTextBoxes(loaded.textBoxes)
            }
        }
    }

    val isDark = colorScheme.background.luminance() < 0.5f
    val currentPattern = remember(colorHex) {
        NoteStyleParser.getPattern(colorHex)
    }
    val currentTintHex = remember(colorHex) {
        NoteStyleParser.getTintHex(colorHex)
    }
    val currentTintTitle = remember(currentTintHex) {
        com.example.sharkmentor.core.util.PaperTint.Options.find {
            it.hex.equals(currentTintHex, ignoreCase = true)
        }?.title ?: "Varsayılan"
    }
    val effectiveBgColor = remember(currentTintHex, isDark, colorScheme.background) {
        com.example.sharkmentor.core.util.PaperTint.parseColor(currentTintHex, isDark) ?: colorScheme.background
    }

    val isLightPaper = remember(effectiveBgColor) {
        val r = effectiveBgColor.red
        val g = effectiveBgColor.green
        val b = effectiveBgColor.blue
        (0.299f * r + 0.587f * g + 0.114f * b) > 0.5f
    }

    val noteTextColor = if (isLightPaper) Color(0xFF0F172A) else Color(0xFFF8FAFC)
    val noteMutedTextColor = if (isLightPaper) Color(0xFF64748B) else Color(0xFF94A3B8)
    val notePatternColor = if (isLightPaper) Color(0xFF0F172A).copy(alpha = 0.08f) else Color(0xFFFFFFFF).copy(alpha = 0.08f)
    val noteMarginLineColor = Color(0xFFEF4444).copy(alpha = if (isLightPaper) 0.35f else 0.25f)
    val effectivePaperAccent = if (kotlin.math.abs(primaryColor.luminance() - effectiveBgColor.luminance()) > 0.25f) {
        primaryColor
    } else {
        noteTextColor
    }

    val isLightHeaderSurface = surfaceColor.luminance() > 0.5f
    val view = LocalView.current
    DisposableEffect(isLightHeaderSurface) {
        val window = (view.context as? Activity)?.window
        if (window != null) {
            val insetsController = WindowCompat.getInsetsController(window, view)
            val prevLightStatus = insetsController.isAppearanceLightStatusBars
            insetsController.isAppearanceLightStatusBars = isLightHeaderSurface
            onDispose {
                insetsController.isAppearanceLightStatusBars = prevLightStatus
            }
        } else {
            onDispose {}
        }
    }

    var showMoreMenu by remember { mutableStateOf(false) }
    var showPageStyleDialog by remember { mutableStateOf(false) }
    var showStatsDialog by remember { mutableStateOf(false) }
    var isNavigationOverviewMode by rememberSaveable(activeNoteId) { mutableStateOf(false) }

    var pdfPreviewBitmap by remember(activeNoteId, initialNote?.id, incomingFileUri, initialPdfPreview) {
        mutableStateOf(initialPdfPreview)
    }

    var pdfPagePaths by remember(activeNoteId, initialNote?.id, incomingFileUri, initialPdfPagePaths) {
        val fromTags = extractPdfPagePathsFromContent(initialNote?.content ?: initialContent)
        mutableStateOf((initialPdfPagePaths + fromTags).distinct())
    }
    var activePdfPageIndex by remember { mutableStateOf(0) }
    var isPdfPreviewExpanded by remember { mutableStateOf(true) }

    val noteFieldValue: TextFieldValue = pageFieldValues.getOrElse(safeActivePageIndex) {
        TextFieldValue(annotatedString = AnnotatedString(""), selection = TextRange(0))
    }

    fun updateActivePageFieldValue(updated: TextFieldValue) {
        val idx = activePageIndex.coerceIn(0, (pageFieldValues.size - 1).coerceAtLeast(0))
        if (idx in pageFieldValues.indices) {
            pageFieldValues[idx] = updated
            pageChangeTick++
        }
    }

    fun populatePagesFromCleanText(cleanText: String) {
        val rawPages = NoteDetailViewModel.splitContentIntoPages(cleanText)
        pageFieldValues.clear()
        rawPages.forEachIndexed { idx, pageRaw ->
            val parsed = NoteRichTextHelper.parseTaggedText(pageRaw)
            pageFieldValues.add(buildSentinelAwarePageValue(idx, parsed, cursorAtEnd = true))
        }
        if (pageFieldValues.isEmpty()) {
            pageFieldValues.add(TextFieldValue(annotatedString = AnnotatedString(""), selection = TextRange(0)))
        }
        if (activePageIndex >= pageFieldValues.size) {
            activePageIndex = (pageFieldValues.size - 1).coerceAtLeast(0)
        }
        pageChangeTick++
    }

    fun buildJoinedTaggedText(pagesSnapshot: List<AnnotatedString> = pageFieldValues.map { it.annotatedString }): String {
        val taggedPages = pagesSnapshot.map { annotated ->
            NoteRichTextHelper.toTaggedString(annotated)
        }
        return NoteDetailViewModel.joinPagesToContent(taggedPages)
    }

    fun buildJoinedPlainText(): String {
        return pageFieldValues.joinToString(separator = "\n\n") {
            NoteRichTextHelper.stripPageSentinel(it.text)
        }
    }

    fun performDeleteNotePage(targetIdx: Int) {
        if (pageFieldValues.size <= 1 || targetIdx !in pageFieldValues.indices) return
        val oldTotalCount = pageFieldValues.size
        pageFieldValues.removeAt(targetIdx)
        for (i in targetIdx until (oldTotalCount - 1)) {
            val nextCtrl = pageDrawingControllers[i + 1]
            if (nextCtrl != null) {
                val curCtrl = getOrCreatePageDrawingController(i)
                curCtrl.setInitialStrokes(nextCtrl.strokes.toList())
                curCtrl.setInitialTextBoxes(nextCtrl.textBoxes.toList())
            }
        }
        pageDrawingControllers.remove(oldTotalCount - 1)
        pageFocusRequesters.remove(oldTotalCount - 1)
        if (activePageIndex >= pageFieldValues.size) {
            activePageIndex = (pageFieldValues.size - 1).coerceAtLeast(0)
        }
        pageChangeTick++
        val snapshot = pageFieldValues.map { it.annotatedString }
        val pathsCopy = pdfPagePaths.toList()
        val fileTagCopy = preservedPdfFileTag
        val noteIdCopy = vmNoteId
        drawingScope.launch(Dispatchers.Default) {
            val taggedText = buildJoinedTaggedText(snapshot)
            val fullContent = composeContentWithPdfPageTags(taggedText, pathsCopy, fileTagCopy)
            withContext(Dispatchers.Main) {
                viewModel.onContentChanged(fullContent)
                viewModel.saveImmediately(overrideContent = fullContent)
            }
            withContext(Dispatchers.IO) {
                DrawingSerializer.deleteNotePageDrawingAndShift(
                    context = context,
                    noteId = noteIdCopy,
                    deletedPageIndex = targetIdx,
                    totalPagesBeforeDelete = oldTotalCount
                )
            }
        }
    }

    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    var isNoteLoaded by remember(activeNoteId) { mutableStateOf(false) }
    var isAutoPaginating by remember(activeNoteId) { mutableStateOf(false) }

    var isBoldActive by remember { mutableStateOf(false) }
    var isItalicActive by remember { mutableStateOf(false) }
    var isUnderlineActive by remember { mutableStateOf(false) }
    var isBulletActive by remember { mutableStateOf(false) }
    var isNumberedActive by remember { mutableStateOf(false) }
    var isCheckboxActive by remember { mutableStateOf(false) }
    var isCheckboxChecked by remember { mutableStateOf(false) }

    var selectedTextColor by remember { mutableStateOf<Color?>(null) }
    var selectedHighlightColor by remember { mutableStateOf<Color?>(null) }
    var selectedFontSizeSp by remember { mutableFloatStateOf(NoteRichTextHelper.DEFAULT_FONT_SIZE_SP) }
    var selectedFontOption by remember { mutableStateOf(NoteFontOption.DEFAULT) }

    // Page plane zoom & in-note search state
    var noteZoomScale by rememberSaveable(activeNoteId) { mutableFloatStateOf(1.0f) }
    var isSearchBarVisible by remember(activeNoteId) { mutableStateOf(false) }
    var searchQuery by remember(activeNoteId) { mutableStateOf("") }
    var currentSearchMatchIndex by remember(activeNoteId) { mutableIntStateOf(0) }
    val searchFocusRequester = remember { FocusRequester() }
    var lastTypedGrowthPage by remember { mutableIntStateOf(-1) }

    // Observe NoteDrawingPreferences for Floating Focus Timer menu state
    LaunchedEffect(context) {
        NoteDrawingPreferences.ensureLoaded(context)
    }
    val showFloatingFocusTimer by NoteDrawingPreferences.showFloatingFocusTimer.collectAsStateWithLifecycle()

    // Real foreground active time spent in this note (wall-clock based; zero per-second recompositions!)
    val lifecycleOwner = LocalLifecycleOwner.current
    var storedNoteSeconds by remember(vmNoteId) {
        mutableLongStateOf(if (vmNoteId.isNotBlank()) NoteTimeTracker.getTotalSeconds(context, vmNoteId) else 0L)
    }
    val sessionResumeWallMsRef = remember(vmNoteId) {
        longArrayOf(android.os.SystemClock.elapsedRealtime())
    }

    fun flushNoteSessionTime() {
        val startMs = sessionResumeWallMsRef[0]
        if (startMs <= 0L) return
        val nowMs = android.os.SystemClock.elapsedRealtime()
        val elapsedSec = ((nowMs - startMs) / 1000L).coerceAtLeast(0L)
        sessionResumeWallMsRef[0] = nowMs
        if (vmNoteId.isNotBlank() && elapsedSec > 0L) {
            storedNoteSeconds = NoteTimeTracker.addSeconds(context, vmNoteId, elapsedSec)
        }
    }

    LaunchedEffect(vmNoteId) {
        if (vmNoteId.isNotBlank()) {
            storedNoteSeconds = NoteTimeTracker.getTotalSeconds(context, vmNoteId)
            sessionResumeWallMsRef[0] = android.os.SystemClock.elapsedRealtime()
        }
    }

    DisposableEffect(lifecycleOwner, vmNoteId) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    sessionResumeWallMsRef[0] = android.os.SystemClock.elapsedRealtime()
                }
                Lifecycle.Event.ON_PAUSE -> {
                    flushNoteSessionTime()
                    sessionResumeWallMsRef[0] = 0L
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            flushNoteSessionTime()
        }
    }

    val totalNoteSpentSeconds = storedNoteSeconds

    var activeColorPanel by remember { mutableStateOf(ActiveColorPanel.NONE) }
    var showCustomColorDialog by remember { mutableStateOf(false) }
    var customColorIsHighlight by remember { mutableStateOf(false) }

    var userCustomColors by remember { mutableStateOf(listOf<Color>()) }

    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current

    var dockState by rememberSaveable { mutableStateOf(ToolbarDockState.BOTTOM) }
    var isToolbarCollapsed by rememberSaveable { mutableStateOf(false) }
    var highlightIntensity by rememberSaveable { mutableFloatStateOf(0.60f) }
    var dockedY by rememberSaveable { mutableFloatStateOf(160f) }

    var floatingOffset by remember { mutableStateOf(Offset(40f, 320f)) }
    var isDragging by remember { mutableStateOf(false) }
    var dragPosition by remember { mutableStateOf(Offset.Zero) }
    var candidateBorder by remember { mutableStateOf<ToolbarDockState?>(null) }

    LaunchedEffect(drawingController.hasActiveLasso, drawingController.activeTool) {
        if (drawingController.activeTool == DrawingTool.LASSO && drawingController.hasActiveLasso) {
            activePenPanel = ActivePenPanel.LASSO
        } else if (drawingController.activeTool == DrawingTool.TEXT_BOX) {
            activePenPanel = ActivePenPanel.TEXT_BOX
        } else if (activePenPanel == ActivePenPanel.TEXT_BOX) {
            activePenPanel = ActivePenPanel.NONE
        }
    }

    val togglePenMode: () -> Unit = {
        if (drawingController.isDrawingActive) {
            drawingController.selectTool(DrawingTool.NONE)
            activePenPanel = ActivePenPanel.NONE
        } else {
            keyboardController?.hide()
            drawingController.selectActiveOrLastPen()
            isToolbarCollapsed = false
            activeColorPanel = ActiveColorPanel.NONE
        }
    }

    LaunchedEffect(noteId, initialNote?.id) {
        activePdfPageIndex = 0
        if (!noteId.isNullOrBlank()) {
            pdfPreviewBitmap = null
            pdfPagePaths = emptyList()
            preservedPdfFileTag = null
            localPdfFilePath = null
            isNoteLoaded = false
            viewModel.loadNoteById(noteId)
        } else if (initialNote != null || initialContent.isNotBlank() || initialTitle.isNotBlank()) {
            val rawContent = initialNote?.content ?: initialContent
            val extractedPaths = (initialPdfPagePaths + extractPdfPagePathsFromContent(rawContent)).distinct()
            pdfPagePaths = extractedPaths
            pdfPreviewBitmap = initialPdfPreview
            val extractedPdfFile = extractPdfFilePathFromContent(rawContent)
            preservedPdfFileTag = extractedPdfFile
            localPdfFilePath = extractedPdfFile
                ?: incomingFileUri
                    ?.takeIf { it.scheme == ContentResolver.SCHEME_FILE }
                    ?.path
                    ?.takeIf { it.endsWith(".pdf", ignoreCase = true) && File(it).exists() }
            val cleanDisplayRaw = stripPdfPageTags(rawContent)
            val persistedContent = composeContentWithPdfPageTags(cleanDisplayRaw, extractedPaths, preservedPdfFileTag)
            viewModel.initializeNote(
                initialNote = initialNote?.copy(content = persistedContent),
                fallbackTitle = initialTitle,
                fallbackContent = persistedContent
            )
            populatePagesFromCleanText(cleanDisplayRaw)
            isNoteLoaded = true
        } else {
            pdfPagePaths = initialPdfPagePaths.distinct()
            pdfPreviewBitmap = initialPdfPreview
            preservedPdfFileTag = null
            localPdfFilePath = incomingFileUri
                ?.takeIf { it.scheme == ContentResolver.SCHEME_FILE }
                ?.path
                ?.takeIf { it.endsWith(".pdf", ignoreCase = true) && File(it).exists() }
            viewModel.initializeEmptyNote()
            populatePagesFromCleanText("")
            isNoteLoaded = true
        }
    }

    LaunchedEffect(vmNoteId, vmContent, isVmNoteReady) {
        if (!noteId.isNullOrBlank() && (vmNoteId != noteId || !isVmNoteReady)) {
            return@LaunchedEffect
        }
        val extractedPaths = extractPdfPagePathsFromContent(vmContent)
        if (!noteId.isNullOrBlank()) {
            pdfPagePaths = extractedPaths
        } else if (extractedPaths.isNotEmpty() && pdfPagePaths != extractedPaths) {
            pdfPagePaths = extractedPaths
        }
        val extractedPdfFile = extractPdfFilePathFromContent(vmContent)
        if (extractedPdfFile != null) {
            preservedPdfFileTag = extractedPdfFile
            localPdfFilePath = extractedPdfFile
        } else if (!noteId.isNullOrBlank()) {
            preservedPdfFileTag = null
            localPdfFilePath = null
        }
        if (!isNoteLoaded) {
            val cleanVmRaw = stripPdfPageTags(vmContent)
            populatePagesFromCleanText(cleanVmRaw)
            isNoteLoaded = true
        }
    }

    LaunchedEffect(pageFieldValues.size, pageChangeTick) {
        if (!isNoteLoaded) return@LaunchedEffect
        kotlinx.coroutines.delay(750L)
        val pagesSnapshot = pageFieldValues.map { it.annotatedString }
        val pathsCopy = pdfPagePaths.toList()
        val fileTagCopy = preservedPdfFileTag
        val fullContent = withContext(Dispatchers.Default) {
            val taggedText = buildJoinedTaggedText(pagesSnapshot)
            composeContentWithPdfPageTags(taggedText, pathsCopy, fileTagCopy)
        }
        viewModel.onContentChanged(fullContent)
    }

    // Track last text seen by cursor-sync so typing / pressing Enter in bullet lists NEVER resets user-picked color/font/size
    var lastCursorSyncText by remember { mutableStateOf("") }
    var lastCursorSyncPage by remember { mutableIntStateOf(-1) }
    var styleLockedByUserPick by remember { mutableStateOf(false) }

    LaunchedEffect(noteFieldValue.selection, noteFieldValue.text, safeActivePageIndex) {
        val textChanged = noteFieldValue.text != lastCursorSyncText || safeActivePageIndex != lastCursorSyncPage
        lastCursorSyncText = noteFieldValue.text
        lastCursorSyncPage = safeActivePageIndex

        if (noteFieldValue.selection.collapsed) {
            val styles = NoteRichTextHelper.getStylesAtCursor(noteFieldValue)
            // Always keep line-level list indicators in sync
            isBulletActive = styles.isBullet
            isNumberedActive = styles.isNumbered
            isCheckboxActive = styles.isCheckbox
            isCheckboxChecked = styles.isCheckboxChecked

            // Only update character-level typing styles when user taps/navigates cursor on UNCHANGED text
            if (!textChanged && !styleLockedByUserPick) {
                isBoldActive = styles.isBold
                isItalicActive = styles.isItalic
                isUnderlineActive = styles.isUnderline
                selectedTextColor = styles.textColor
                selectedHighlightColor = styles.highlightColor
                if (styles.fontSizeSp != null) selectedFontSizeSp = styles.fontSizeSp
                if (styles.fontOption != null) selectedFontOption = styles.fontOption
            } else if (!textChanged) {
                // Next cursor move after explicit pick unlocks normal inspection
                styleLockedByUserPick = false
            }
        }
    }

    val titleAccentColor = remember(currentTintHex, onSurfaceColor) {
        try {
            if (!currentTintHex.isNullOrBlank() && !currentTintHex.equals("#F8FAFC", ignoreCase = true) && !currentTintHex.equals("#FFFFFF", ignoreCase = true)) {
                Color(android.graphics.Color.parseColor(currentTintHex))
            } else {
                onSurfaceColor
            }
        } catch (_: Exception) {
            onSurfaceColor
        }
    }

    val effectiveTitleColor = if (isLightPaper) {
        Color(0xFF0F172A)
    } else if (kotlin.math.abs(titleAccentColor.luminance() - effectiveBgColor.luminance()) > 0.35f) {
        titleAccentColor
    } else {
        noteTextColor
    }

    var chars by remember(activeNoteId) { mutableIntStateOf(0) }
    var lineCount by remember(activeNoteId) { mutableIntStateOf(1) }
    var words by remember(activeNoteId) { mutableIntStateOf(0) }

    LaunchedEffect(pageFieldValues.size, pageChangeTick, isNoteLoaded) {
        if (!isNoteLoaded) return@LaunchedEffect
        kotlinx.coroutines.delay(600L)
        val rawPageTexts = pageFieldValues.map { it.text }
        val (computedChars, computedLines, computedWords) = withContext(Dispatchers.Default) {
            val joined = rawPageTexts.joinToString(separator = "\n\n") {
                NoteRichTextHelper.stripPageSentinel(it)
            }
            val cCount = joined.length
            val lCount = if (joined.isEmpty()) 1 else joined.count { it == '\n' } + 1
            val wCount = if (joined.isBlank()) {
                0
            } else if (joined.length > 20_000) {
                joined.count { it == ' ' || it == '\n' } + 1
            } else {
                var count = 0
                var inWord = false
                for (i in 0 until joined.length) {
                    val isWs = joined[i].isWhitespace()
                    if (!isWs && !inWord) {
                        inWord = true
                        count++
                    } else if (isWs) {
                        inWord = false
                    }
                }
                count
            }
            Triple(cCount, lCount, wCount)
        }
        chars = computedChars
        lineCount = computedLines
        words = computedWords
    }

    val aiPrefs = remember(context) { UserAiPreferences.initialize(context) }
    val notesAiEnabled by aiPrefs.notesAiEnabled.collectAsStateWithLifecycle()
    val drawingAiEnabled by aiPrefs.drawingAiEnabled.collectAsStateWithLifecycle()
    var showAiSheet by remember { mutableStateOf(false) }
    val topBarPrefs = remember(context) { com.example.sharkmentor.data.topbar.TopBarPreferences.getInstance(context) }
    val topBarSettings by topBarPrefs.settings.collectAsStateWithLifecycle()
    val showNoteMusicButton = topBarSettings.isModuleEnabled(com.example.sharkmentor.data.topbar.TopBarModuleId.MUSIC)
    val noteMusicState by com.example.sharkmentor.core.audio.MusicPlayerHub.playbackState.collectAsStateWithLifecycle()
    var showNoteMusicSheet by remember { mutableStateOf(false) }
    LaunchedEffect(context) {
        com.example.sharkmentor.core.audio.MusicPlayerHub.ensureInitialized(context)
    }
    var aiQueryText by remember { mutableStateOf("") }
    var aiScope by remember { mutableStateOf(DocumentAiScope.CURRENT_PAGE) }
    var lassoAiCropBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var lassoAiSourceLabel by remember { mutableStateOf("Not") }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            lassoAiCropBitmap?.let { if (!it.isRecycled) runCatching { it.recycle() } }
            lassoAiCropBitmap = null
        }
    }

    LaunchedEffect(incomingFileUri) {
        if (incomingFileUri != null) {
            withContext(Dispatchers.IO) {
                val resolver = context.contentResolver
                val displayName = resolveFileDisplayName(resolver, incomingFileUri)
                val mimeType = resolver.getType(incomingFileUri)
                val isPdf = mimeType == "application/pdf" || displayName.endsWith(".pdf", ignoreCase = true)

                val directLocalFile = if (incomingFileUri.scheme == ContentResolver.SCHEME_FILE) {
                    incomingFileUri.path?.let { File(it) }?.takeIf { it.exists() && it.length() > 0L }
                } else {
                    null
                }

                if (isPdf && directLocalFile != null && (initialTitle.isNotBlank() || initialContent.isNotBlank())) {
                    withContext(Dispatchers.Main) {
                        localPdfFilePath = directLocalFile.absolutePath
                    }
                    return@withContext
                }

                var copiedPdfPath: String? = directLocalFile?.takeIf { isPdf }?.absolutePath
                var effectiveUri = incomingFileUri

                if (isPdf && copiedPdfPath == null) {
                    val copied = PdfBitmapConverter.copyUriToPrivatePdfsDir(context, incomingFileUri)
                    if (copied != null) {
                        copiedPdfPath = copied.localFile.absolutePath
                        effectiveUri = copied.localUri
                    }
                }

                val resolvedTitle = displayName
                    .substringBeforeLast('.')
                    .ifBlank { "İçe Aktarılan Not" }

                val rawContent: String = DocumentTextExtractor.extractCleanText(
                    context = context,
                    uri = effectiveUri,
                    displayName = displayName,
                    mimeType = mimeType
                )

                val safeContent = if (rawContent.length > 30_000) {
                    rawContent.take(30_000)
                } else {
                    rawContent
                }

                withContext(Dispatchers.Main) {
                    if (copiedPdfPath != null) {
                        localPdfFilePath = copiedPdfPath
                    }
                    if (vmTitle.isBlank()) {
                        viewModel.onTitleChanged(resolvedTitle)
                    }
                    if (safeContent.isNotBlank() && buildJoinedPlainText().isBlank()) {
                        populatePagesFromCleanText(safeContent)
                        viewModel.onContentChanged(
                            composeContentWithPdfPageTags(safeContent, pdfPagePaths, preservedPdfFileTag)
                        )
                    }
                }
            }
        }
    }

    val noteListState = rememberLazyListState()

    fun mergeOrDeletePageIntoPrevious(pageIndex: Int) {
        if (pageIndex <= 0 || pageIndex !in pageFieldValues.indices) return
        val prevIdx = pageIndex - 1
        val prevTfv = pageFieldValues[prevIdx]
        val curTfv = pageFieldValues[pageIndex]
        val curCtrl = pageDrawingControllers[pageIndex]
        val curHasInk = curCtrl?.hasAnyAnnotations == true

        // Strip sentinel from current page while shifting its SpanStyles accurately
        val curRawText = curTfv.text
        val sentinelOffset = if (curRawText.startsWith(NoteRichTextHelper.PAGE_SENTINEL)) 1 else 0
        val cleanCurText = NoteRichTextHelper.stripPageSentinel(curRawText)
        val cleanCurSpans = if (cleanCurText.isEmpty()) {
            emptyList()
        } else {
            curTfv.annotatedString.spanStyles.mapNotNull { r ->
                val ns = (r.start - sentinelOffset).coerceIn(0, cleanCurText.length)
                val ne = (r.end - sentinelOffset).coerceIn(0, cleanCurText.length)
                if (ne > ns) AnnotatedString.Range(r.item, ns, ne) else null
            }
        }

        val junctionPos = prevTfv.text.length
        if (cleanCurText.isNotEmpty()) {
            val mergedText = prevTfv.text + cleanCurText
            val shiftedCurSpans = cleanCurSpans.map { r ->
                AnnotatedString.Range(r.item, r.start + junctionPos, r.end + junctionPos)
            }
            val mergedSpans = NoteRichTextHelper.coalesceSpans(
                prevTfv.annotatedString.spanStyles + shiftedCurSpans,
                mergedText.length
            )
            pageFieldValues[prevIdx] = TextFieldValue(
                annotatedString = AnnotatedString(mergedText, mergedSpans),
                selection = TextRange(junctionPos)
            )
        } else {
            pageFieldValues[prevIdx] = prevTfv.copy(selection = TextRange(junctionPos))
        }

        if (!curHasInk && pageFieldValues.size > 1) {
            performDeleteNotePage(pageIndex)
        } else if (pageIndex in pageFieldValues.indices) {
            // Page has ink drawings; keep empty page with sentinel so drawings aren't lost
            pageFieldValues[pageIndex] = TextFieldValue(
                annotatedString = AnnotatedString(NoteRichTextHelper.PAGE_SENTINEL),
                selection = TextRange(1)
            )
            pageChangeTick++
        }

        activePageIndex = prevIdx
        val pageOffset = 1 +
            (if (localPdfFilePath != null) 1 else 0) +
            (if (pdfPagePaths.isNotEmpty() || pdfPreviewBitmap != null) 1 else 0)
        drawingScope.launch {
            kotlinx.coroutines.delay(35L)
            runCatching {
                noteListState.animateScrollToItem(pageOffset + prevIdx)
            }
            if (!drawingController.isDrawingActive) {
                runCatching {
                    getOrCreatePageFocusRequester(prevIdx).requestFocus()
                }
            }
        }
    }

    val onBoldToggled: () -> Unit = {
        styleLockedByUserPick = true
        if (!noteFieldValue.selection.collapsed) {
            updateActivePageFieldValue(
                NoteRichTextHelper.toggleStyleOnSelection(noteFieldValue, NoteRichTextHelper.StyleType.BOLD)
            )
        }
        isBoldActive = !isBoldActive
    }

    val onItalicToggled: () -> Unit = {
        styleLockedByUserPick = true
        if (!noteFieldValue.selection.collapsed) {
            updateActivePageFieldValue(
                NoteRichTextHelper.toggleStyleOnSelection(noteFieldValue, NoteRichTextHelper.StyleType.ITALIC)
            )
        }
        isItalicActive = !isItalicActive
    }

    val onUnderlineToggled: () -> Unit = {
        styleLockedByUserPick = true
        if (!noteFieldValue.selection.collapsed) {
            updateActivePageFieldValue(
                NoteRichTextHelper.toggleStyleOnSelection(noteFieldValue, NoteRichTextHelper.StyleType.UNDERLINE)
            )
        }
        isUnderlineActive = !isUnderlineActive
    }

    val handleColorSelected: (Color) -> Unit = { color ->
        styleLockedByUserPick = true
        selectedTextColor = color
        if (!noteFieldValue.selection.collapsed) {
            updateActivePageFieldValue(NoteRichTextHelper.applyColorToSelection(noteFieldValue, color))
        }
        onColorSelected(color)
    }

    val handleHighlightSelected: (Color?) -> Unit = { highlightColor ->
        styleLockedByUserPick = true
        val effectiveHighlight = highlightColor?.copy(alpha = highlightIntensity)
        selectedHighlightColor = effectiveHighlight
        if (!noteFieldValue.selection.collapsed) {
            updateActivePageFieldValue(NoteRichTextHelper.applyHighlightToSelection(noteFieldValue, effectiveHighlight))
        }
    }

    val handleHighlightIntensityChanged: (Float) -> Unit = { newIntensity ->
        styleLockedByUserPick = true
        highlightIntensity = newIntensity
        if (selectedHighlightColor != null) {
            val updated = selectedHighlightColor!!.copy(alpha = newIntensity)
            selectedHighlightColor = updated
            if (!noteFieldValue.selection.collapsed) {
                updateActivePageFieldValue(NoteRichTextHelper.applyHighlightToSelection(noteFieldValue, updated))
            }
        }
    }

    val handleFontSizeSelected: (Float) -> Unit = { sizeSp ->
        styleLockedByUserPick = true
        val clamped = sizeSp.coerceIn(NoteRichTextHelper.MIN_FONT_SIZE_SP, NoteRichTextHelper.MAX_FONT_SIZE_SP)
        selectedFontSizeSp = clamped
        if (!noteFieldValue.selection.collapsed) {
            updateActivePageFieldValue(NoteRichTextHelper.applyFontSizeToSelection(noteFieldValue, clamped))
        }
    }

    val handleFontOptionSelected: (NoteFontOption) -> Unit = { fontOpt ->
        styleLockedByUserPick = true
        selectedFontOption = fontOpt
        if (!noteFieldValue.selection.collapsed) {
            updateActivePageFieldValue(NoteRichTextHelper.applyFontFamilyToSelection(noteFieldValue, fontOpt))
        }
    }

    val handleBulletToggled: () -> Unit = {
        updateActivePageFieldValue(NoteRichTextHelper.toggleLinePrefix(noteFieldValue, "• "))
        isBulletActive = !isBulletActive
        isNumberedActive = false
        isCheckboxActive = false
        onBulletToggled()
    }

    val handleNumberedToggled: () -> Unit = {
        updateActivePageFieldValue(NoteRichTextHelper.toggleLinePrefix(noteFieldValue, "1. "))
        isNumberedActive = !isNumberedActive
        isBulletActive = false
        isCheckboxActive = false
    }

    val handleCheckboxToggled: () -> Unit = {
        val updated = NoteRichTextHelper.toggleCheckboxAtOffset(noteFieldValue, noteFieldValue.selection.start)
        updateActivePageFieldValue(updated)
        val styles = NoteRichTextHelper.getStylesAtCursor(updated)
        isCheckboxActive = styles.isCheckbox
        isCheckboxChecked = styles.isCheckboxChecked
        isBulletActive = false
        isNumberedActive = false
    }

    // In-note search matches across all pages (case-insensitive)
    val searchMatches = remember(searchQuery, pageFieldValues.size, pageChangeTick) {
        val q = searchQuery.trim()
        if (q.isEmpty()) {
            emptyList()
        } else {
            val out = mutableListOf<NoteSearchMatch>()
            val lowerQ = q.lowercase(Locale.getDefault())
            pageFieldValues.forEachIndexed { pageIdx, tfv ->
                val lowerText = tfv.text.lowercase(Locale.getDefault())
                var from = 0
                while (from <= lowerText.length - lowerQ.length) {
                    val found = lowerText.indexOf(lowerQ, from)
                    if (found == -1) break
                    out.add(NoteSearchMatch(pageIdx, found, found + q.length))
                    from = found + lowerQ.length.coerceAtLeast(1)
                }
            }
            out
        }
    }

    fun jumpToSearchMatch(matchIndex: Int) {
        if (searchMatches.isEmpty()) return
        val safeIdx = ((matchIndex % searchMatches.size) + searchMatches.size) % searchMatches.size
        currentSearchMatchIndex = safeIdx
        val match = searchMatches[safeIdx]
        isNavigationOverviewMode = false
        activePageIndex = match.pageIndex
        if (match.pageIndex in pageFieldValues.indices) {
            val currentTfv = pageFieldValues[match.pageIndex]
            val start = match.start.coerceIn(0, currentTfv.text.length)
            val end = match.end.coerceIn(start, currentTfv.text.length)
            pageFieldValues[match.pageIndex] = currentTfv.copy(selection = TextRange(start, end))
        }
        val pageOffset = 1 +
            (if (localPdfFilePath != null) 1 else 0) +
            (if (pdfPagePaths.isNotEmpty() || pdfPreviewBitmap != null) 1 else 0)
        drawingScope.launch {
            runCatching {
                noteListState.animateScrollToItem(pageOffset + match.pageIndex)
            }
        }
    }

    LaunchedEffect(searchQuery) {
        currentSearchMatchIndex = 0
        if (searchQuery.isNotBlank() && searchMatches.isNotEmpty()) {
            jumpToSearchMatch(0)
        }
    }

    LaunchedEffect(isSearchBarVisible) {
        if (isSearchBarVisible) {
            runCatching { searchFocusRequester.requestFocus() }
        }
    }

    val saveCurrentNote: () -> Unit = {
        flushNoteSessionTime()
        val taggedText = buildJoinedTaggedText()
        val fullContent = composeContentWithPdfPageTags(taggedText, pdfPagePaths, preservedPdfFileTag)
        val anyAnnotations = pageDrawingControllers.values.any { it.hasAnyAnnotations }
        val effectiveTitle = if (vmTitle.isBlank() && fullContent.isBlank() && anyAnnotations) {
            "Çizimli Not"
        } else {
            vmTitle
        }
        viewModel.saveImmediately(
            overrideTitle = effectiveTitle,
            overrideContent = fullContent
        )
        pageDrawingControllers.forEach { (idx, ctrl) ->
            ctrl.closeActiveTextBox()
            viewModel.saveNotePageAnnotationsImmediately(
                context = context,
                targetNoteId = vmNoteId,
                pageIndex = idx,
                strokes = ctrl.strokes,
                textBoxes = ctrl.textBoxes
            )
        }
    }

    BackHandler {
        when {
            isSearchBarVisible -> {
                isSearchBarVisible = false
                searchQuery = ""
            }
            isNavigationOverviewMode -> {
                isNavigationOverviewMode = false
            }
            drawingController.isDrawingActive -> {
                drawingController.selectTool(DrawingTool.NONE)
                activePenPanel = ActivePenPanel.NONE
            }
            else -> {
                saveCurrentNote()
                onSave(vmTitle, buildJoinedTaggedText(), colorHex, isPinned)
                onDismiss()
            }
        }
    }

    val isImeOpen = WindowInsets.isImeVisible
    val imeBottom = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
    val navBarsBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val effectiveBottomPadding = if (imeBottom > 0.dp) imeBottom else navBarsBottom

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(bgColor)
            .padding(bottom = effectiveBottomPadding)
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when {
                        keyEvent.isCtrlPressed && keyEvent.key == Key.S -> {
                            saveCurrentNote()
                            Toast.makeText(context, "Not kaydedildi", Toast.LENGTH_SHORT).show()
                            true
                        }
                        keyEvent.isCtrlPressed && keyEvent.key == Key.F -> {
                            isNavigationOverviewMode = false
                            isSearchBarVisible = true
                            true
                        }
                        keyEvent.key == Key.Escape && isSearchBarVisible -> {
                            isSearchBarVisible = false
                            searchQuery = ""
                            true
                        }
                        else -> false
                    }
                } else {
                    false
                }
            }
    ) {
        TopAppBar(
            expandedHeight = 50.dp,
            title = {
                Column(
                    modifier = Modifier.width(IntrinsicSize.Max),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "SharkMentor",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = onSurfaceColor
                    )
                    Text(
                        text = if (pageFieldValues.size > 1) {
                            "SAYFA ${safeActivePageIndex + 1} / ${pageFieldValues.size}"
                        } else {
                            "S T U D Y"
                        },
                        fontSize = 9.sp,
                        letterSpacing = if (pageFieldValues.size > 1) 1.5.sp else 3.sp,
                        color = colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                    )
                }
            },
            navigationIcon = {
                IconButton(
                    onClick = {
                        saveCurrentNote()
                        onSave(vmTitle, buildJoinedTaggedText(), colorHex, isPinned)
                        onDismiss()
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
                if (showNoteMusicButton) {
                    com.example.sharkmentor.ui.components.TopBarMusicPill(
                        playbackState = noteMusicState,
                        showBubble = !topBarSettings.hideBubbles,
                        scaleFactor = 0.92f,
                        compactIconOnly = true,
                        onClick = { showNoteMusicSheet = true },
                        modifier = Modifier.padding(end = 2.dp)
                    )
                }
                if (notesAiEnabled) {
                    IconButton(
                        onClick = {
                            onIsolatedNoteAiClick(vmTitle, buildJoinedPlainText())
                            showAiSheet = true
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "Not AI Asistanı",
                            tint = primaryColor
                        )
                    }
                }
                IconButton(
                    onClick = {
                        val taggedText = buildJoinedTaggedText()
                        val fullContent = composeContentWithPdfPageTags(taggedText, pdfPagePaths, preservedPdfFileTag)
                        viewModel.onContentChanged(fullContent)
                        viewModel.onPinnedChanged(!isPinned)
                    }
                ) {
                    if (isPinned) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFFF59E0B).copy(alpha = 0.22f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF59E0B)),
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.PushPin,
                                    contentDescription = "Sabitlendi",
                                    tint = Color(0xFFF59E0B),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.PushPin,
                            contentDescription = "Sabitle",
                            tint = onSurfaceColor.copy(alpha = 0.6f)
                        )
                    }
                }
                Box {
                    IconButton(onClick = { showMoreMenu = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Diğer İşlemler",
                            tint = onSurfaceColor
                        )
                    }

                    DropdownMenu(
                        expanded = showMoreMenu,
                        onDismissRequest = { showMoreMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Not İçinde Ara") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null,
                                    tint = primaryColor,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            onClick = {
                                showMoreMenu = false
                                isNavigationOverviewMode = false
                                isSearchBarVisible = true
                            }
                        )

                        DropdownMenuItem(
                            text = { Text("Notu Kaydet") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = primaryColor,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            onClick = {
                                showMoreMenu = false
                                saveCurrentNote()
                                Toast.makeText(context, "Not kaydedildi", Toast.LENGTH_SHORT).show()
                            }
                        )

                        DropdownMenuItem(
                            text = { Text("Yeni Sayfa Ekle (${currentPattern.title})") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = null,
                                    tint = primaryColor,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            onClick = {
                                showMoreMenu = false
                                val newPageIdx = pageFieldValues.size
                                pageFieldValues.add(
                                    TextFieldValue(
                                        annotatedString = AnnotatedString(""),
                                        selection = TextRange(0)
                                    )
                                )
                                getOrCreatePageDrawingController(newPageIdx)
                                activePageIndex = newPageIdx
                                isNavigationOverviewMode = false
                                pageChangeTick++
                                val taggedText = buildJoinedTaggedText()
                                val fullContent = composeContentWithPdfPageTags(taggedText, pdfPagePaths, preservedPdfFileTag)
                                viewModel.onContentChanged(fullContent)
                                Toast.makeText(
                                    context,
                                    "Sayfa ${newPageIdx + 1} eklendi (${currentPattern.title} • $currentTintTitle)",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        )

                        DropdownMenuItem(
                            text = { Text(if (isNavigationOverviewMode) "Normal Görünüme Dön" else "Gezinti Modu") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Apps,
                                    contentDescription = null,
                                    tint = primaryColor,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            onClick = {
                                showMoreMenu = false
                                keyboardController?.hide()
                                isNavigationOverviewMode = !isNavigationOverviewMode
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
                            text = { Text("Yakınlaştır (%${(noteZoomScale * 100).roundToInt()})") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.ZoomIn,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            onClick = {
                                showMoreMenu = false
                                noteZoomScale = (noteZoomScale + 0.15f).coerceAtMost(2.0f)
                            }
                        )

                        DropdownMenuItem(
                            text = { Text(if (noteZoomScale > 0.78f) "Uzaklaştır" else "Yakınlaştırmayı Sıfırla") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.ZoomOut,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            onClick = {
                                showMoreMenu = false
                                noteZoomScale = if (noteZoomScale > 0.78f) {
                                    (noteZoomScale - 0.15f).coerceAtLeast(0.75f)
                                } else {
                                    1.0f
                                }
                            }
                        )

                        DropdownMenuItem(
                            text = { Text("PDF Olarak Dışa Aktar") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.PictureAsPdf,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            onClick = {
                                showMoreMenu = false
                                saveCurrentNote()
                                val exportRes = PdfExporter.exportNoteToPdf(
                                    context = context,
                                    title = vmTitle,
                                    rawContent = buildJoinedPlainText()
                                )
                                exportRes.onSuccess { res ->
                                    Toast.makeText(context, "PDF kaydedildi: ${res.fileName}", Toast.LENGTH_SHORT).show()
                                    PdfExporter.openOrSharePdf(context, res.fileUri, res.fileName)
                                }.onFailure { err ->
                                    Toast.makeText(context, "PDF oluşturulamadı: ${err.localizedMessage}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )

                        DropdownMenuItem(
                            text = { Text("Sayfa Düzeni") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Layers,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            onClick = {
                                showMoreMenu = false
                                showPageStyleDialog = true
                            }
                        )

                        DropdownMenuItem(
                            text = { Text("Not İstatistikleri") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            onClick = {
                                showMoreMenu = false
                                flushNoteSessionTime()
                                showStatsDialog = true
                            }
                        )

                        DropdownMenuItem(
                            text = { Text("Kopyasını Oluştur") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            onClick = {
                                showMoreMenu = false
                                val sourceId = vmNoteId
                                saveCurrentNote()
                                viewModel.duplicateCurrentNote { newNoteId ->
                                    drawingScope.launch(Dispatchers.IO) {
                                        DrawingSerializer.duplicateNoteDrawing(context, sourceId, newNoteId)
                                    }
                                    Toast.makeText(context, "Notun kopyası oluşturuldu", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )

                        DropdownMenuItem(
                            text = { Text("Metin Olarak Paylaş") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            onClick = {
                                showMoreMenu = false
                                val sendIntent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(Intent.EXTRA_TITLE, vmTitle)
                                    putExtra(Intent.EXTRA_SUBJECT, vmTitle)
                                    putExtra(Intent.EXTRA_TEXT, "${vmTitle}\n\n${buildJoinedPlainText().trim()}")
                                    type = "text/plain"
                                }
                                val chooser = Intent.createChooser(sendIntent, "Notu Paylaş")
                                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(chooser)
                            }
                        )

                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "Notu Sil",
                                    color = MaterialTheme.colorScheme.error
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            onClick = {
                                showMoreMenu = false
                                showDeleteConfirmDialog = true
                            }
                        )
                    }
                }
                if (onOpenDrawer != null) {
                    IconButton(
                        onClick = {
                            saveCurrentNote()
                            onOpenDrawer()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "Yan Panel",
                            tint = onSurfaceColor
                        )
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = surfaceColor,
                titleContentColor = onSurfaceColor,
                navigationIconContentColor = onSurfaceColor,
                actionIconContentColor = onSurfaceColor
            )
        )

        // In-note Search Bar (opened via 3-dot menu or Ctrl+F)
        AnimatedVisibility(
            visible = isSearchBarVisible,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Surface(
                color = surfaceColor,
                tonalElevation = 4.dp,
                shadowElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = primaryColor,
                        modifier = Modifier.size(18.dp)
                    )
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        singleLine = true,
                        textStyle = TextStyle(
                            color = onSurfaceColor,
                            fontSize = 14.sp
                        ),
                        cursorBrush = SolidColor(primaryColor),
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(onSurfaceColor.copy(alpha = 0.06f))
                            .padding(horizontal = 10.dp, vertical = 7.dp)
                            .focusRequester(searchFocusRequester),
                        decorationBox = { inner ->
                            Box {
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        text = "Not içinde ara...",
                                        color = textMutedColor,
                                        fontSize = 14.sp
                                    )
                                }
                                inner()
                            }
                        }
                    )
                    Text(
                        text = if (searchMatches.isEmpty()) {
                            "0/0"
                        } else {
                            "${(currentSearchMatchIndex % searchMatches.size) + 1}/${searchMatches.size}"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (searchMatches.isEmpty() && searchQuery.isNotBlank()) {
                            colorScheme.error
                        } else {
                            primaryColor
                        }
                    )
                    IconButton(
                        onClick = { jumpToSearchMatch(currentSearchMatchIndex - 1) },
                        enabled = searchMatches.isNotEmpty(),
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowUp,
                            contentDescription = "Önceki eşleşme",
                            tint = if (searchMatches.isNotEmpty()) onSurfaceColor else onSurfaceColor.copy(alpha = 0.3f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = { jumpToSearchMatch(currentSearchMatchIndex + 1) },
                        enabled = searchMatches.isNotEmpty(),
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Sonraki eşleşme",
                            tint = if (searchMatches.isNotEmpty()) onSurfaceColor else onSurfaceColor.copy(alpha = 0.3f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = {
                            isSearchBarVisible = false
                            searchQuery = ""
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Aramayı Kapat",
                            tint = onSurfaceColor.copy(alpha = 0.7f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        // Movable Toolbar & Editor Area
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            val editorViewportHeightDp = maxHeight
            val screenWidthPx = constraints.maxWidth.toFloat()
            val screenHeightPx = constraints.maxHeight.toFloat()
            val snapThresholdPx = with(density) { 90.dp.toPx() }
            val bubbleSizePx = with(density) { 46.dp.toPx() }

            LaunchedEffect(screenWidthPx, screenHeightPx) {
                if (dockState == ToolbarDockState.FLOATING) {
                    val maxFloatX = (screenWidthPx - with(density) { 80.dp.toPx() }).coerceAtLeast(0f)
                    val maxFloatY = (screenHeightPx - with(density) { 60.dp.toPx() }).coerceAtLeast(0f)
                    if (floatingOffset.x > maxFloatX || floatingOffset.y > maxFloatY) {
                        floatingOffset = Offset(
                            floatingOffset.x.coerceIn(0f, maxFloatX),
                            floatingOffset.y.coerceIn(0f, maxFloatY)
                        )
                    }
                }
            }

            fun calculateCandidateBorder(pos: Offset, itemW: Float, itemH: Float): ToolbarDockState? {
                val centerX = (pos.x + itemW / 2f).coerceIn(0f, screenWidthPx)
                val centerY = (pos.y + itemH / 2f).coerceIn(0f, screenHeightPx)

                val distLeft = centerX
                val distRight = (screenWidthPx - centerX).coerceAtLeast(0f)
                val distTop = centerY
                val distBottom = (screenHeightPx - centerY).coerceAtLeast(0f)

                val minDist = minOf(distLeft, distRight, distTop, distBottom)
                return if (minDist <= snapThresholdPx) {
                    when (minDist) {
                        distLeft -> ToolbarDockState.LEFT
                        distRight -> ToolbarDockState.RIGHT
                        distTop -> ToolbarDockState.TOP
                        distBottom -> ToolbarDockState.BOTTOM
                        else -> null
                    }
                } else null
            }

            fun startExpandedToolbarDrag(touchOffset: Offset) {
                activeColorPanel = ActiveColorPanel.NONE
                activePenPanel = ActivePenPanel.NONE
                val isVert = dockState == ToolbarDockState.LEFT || dockState == ToolbarDockState.RIGHT
                val curW = if (isVert) with(density) { 54.dp.toPx() } else if (dockState == ToolbarDockState.FLOATING) with(density) { 350.dp.toPx() } else minOf(screenWidthPx, with(density) { 760.dp.toPx() })
                val curH = if (isVert) with(density) { 410.dp.toPx() } else with(density) { 56.dp.toPx() }

                val maxVertY = (screenHeightPx - curH).coerceAtLeast(0f)
                val initialPos = when (dockState) {
                    ToolbarDockState.BOTTOM -> Offset((screenWidthPx - curW) / 2f, screenHeightPx - curH)
                    ToolbarDockState.TOP -> Offset((screenWidthPx - curW) / 2f, 0f)
                    ToolbarDockState.LEFT -> Offset(0f, dockedY.coerceIn(0f, maxVertY))
                    ToolbarDockState.RIGHT -> Offset(screenWidthPx - curW, dockedY.coerceIn(0f, maxVertY))
                    ToolbarDockState.FLOATING -> floatingOffset
                }
                dragPosition = initialPos
                isDragging = true
                candidateBorder = null
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }

            fun continueExpandedToolbarDrag(dragAmount: Offset) {
                val isVert = candidateBorder == ToolbarDockState.LEFT || candidateBorder == ToolbarDockState.RIGHT ||
                    (candidateBorder == null && (dockState == ToolbarDockState.LEFT || dockState == ToolbarDockState.RIGHT))
                val curW = if (isVert) with(density) { 54.dp.toPx() } else if (dockState == ToolbarDockState.FLOATING) with(density) { 350.dp.toPx() } else screenWidthPx
                val curH = if (isVert) with(density) { 410.dp.toPx() } else with(density) { 56.dp.toPx() }

                dragPosition = Offset(
                    (dragPosition.x + dragAmount.x).coerceIn(0f, (screenWidthPx - curW).coerceAtLeast(0f)),
                    (dragPosition.y + dragAmount.y).coerceIn(0f, (screenHeightPx - curH).coerceAtLeast(0f))
                )
                val newCandidate = calculateCandidateBorder(dragPosition, curW, curH)
                if (newCandidate != candidateBorder && newCandidate != null) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
                candidateBorder = newCandidate
            }

            fun finishExpandedToolbarDrag() {
                isDragging = false
                if (candidateBorder != null) {
                    dockState = candidateBorder!!
                    if (dockState == ToolbarDockState.LEFT || dockState == ToolbarDockState.RIGHT) {
                        dockedY = dragPosition.y
                    }
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                } else {
                    dockState = ToolbarDockState.FLOATING
                    val isVert = dockState == ToolbarDockState.LEFT || dockState == ToolbarDockState.RIGHT
                    val curW = if (isVert) with(density) { 54.dp.toPx() } else with(density) { 350.dp.toPx() }
                    val curH = if (isVert) with(density) { 410.dp.toPx() } else with(density) { 56.dp.toPx() }
                    floatingOffset = Offset(
                        dragPosition.x.coerceIn(8f, (screenWidthPx - curW - 8f).coerceAtLeast(8f)),
                        dragPosition.y.coerceIn(8f, (screenHeightPx - curH - 8f).coerceAtLeast(8f))
                    )
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
                candidateBorder = null
            }

            val toolbarDragModifier = Modifier.pointerInput(dockState, screenWidthPx, screenHeightPx, dockedY) {
                detectDragGestures(
                    onDragStart = { offset -> startExpandedToolbarDrag(offset) },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        continueExpandedToolbarDrag(dragAmount)
                    },
                    onDragEnd = { finishExpandedToolbarDrag() },
                    onDragCancel = {
                        isDragging = false
                        candidateBorder = null
                    }
                )
            }

            val bubblePointerModifier = Modifier.pointerInput(dockState, screenWidthPx, screenHeightPx, dockedY) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var totalDragDistance = 0f
                    var isDragStarted = false
                    val touchSlop = viewConfiguration.touchSlop

                    val bubbleW = bubbleSizePx
                    val maxBubbleY = (screenHeightPx - bubbleW - with(density) { 8.dp.toPx() }).coerceAtLeast(0f)
                    val effectiveInitialY = dockedY.coerceIn(with(density) { 8.dp.toPx() }, maxBubbleY)
                    val initialPos = when (dockState) {
                        ToolbarDockState.BOTTOM -> Offset((screenWidthPx - bubbleW) / 2f, screenHeightPx - bubbleW - with(density) { 12.dp.toPx() })
                        ToolbarDockState.TOP -> Offset((screenWidthPx - bubbleW) / 2f, with(density) { 12.dp.toPx() })
                        ToolbarDockState.LEFT -> Offset(with(density) { 8.dp.toPx() }, effectiveInitialY)
                        ToolbarDockState.RIGHT -> Offset(screenWidthPx - bubbleW - with(density) { 8.dp.toPx() }, effectiveInitialY)
                        ToolbarDockState.FLOATING -> floatingOffset
                    }

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id }
                        if (change == null || !change.pressed) {
                            if (!isDragStarted) {
                                isToolbarCollapsed = false
                                isDragging = false
                                candidateBorder = null
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            } else {
                                isDragging = false
                                if (candidateBorder != null) {
                                    dockState = candidateBorder!!
                                    if (dockState == ToolbarDockState.LEFT || dockState == ToolbarDockState.RIGHT) {
                                        dockedY = dragPosition.y
                                    }
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                } else {
                                    dockState = ToolbarDockState.FLOATING
                                    floatingOffset = dragPosition
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                                candidateBorder = null
                            }
                            break
                        }

                        val dragAmount = change.positionChange()
                        totalDragDistance += dragAmount.getDistance()

                        if (!isDragStarted && totalDragDistance > touchSlop) {
                            isDragStarted = true
                            isDragging = true
                            dragPosition = initialPos
                            candidateBorder = null
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }

                        if (isDragStarted) {
                            change.consume()
                            dragPosition = Offset(
                                (dragPosition.x + dragAmount.x).coerceIn(0f, (screenWidthPx - bubbleW).coerceAtLeast(0f)),
                                (dragPosition.y + dragAmount.y).coerceIn(0f, (screenHeightPx - bubbleW).coerceAtLeast(0f))
                            )
                            val newCandidate = calculateCandidateBorder(dragPosition, bubbleW, bubbleW)
                            if (newCandidate != candidateBorder && newCandidate != null) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                            candidateBorder = newCandidate
                        }
                    }
                }
            }

            // Main Editor Area Background
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(effectiveBgColor)
                    .pointerInput(drawingController.isDrawingActive) {
                        if (!drawingController.isDrawingActive) return@pointerInput
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                val unconsumedTouches = event.changes.filter {
                                    it.pressed && !it.isConsumed && it.type == PointerType.Touch
                                }
                                if (unconsumedTouches.size >= 2) {
                                    var sumDy = 0f
                                    for (ptr in unconsumedTouches) {
                                        sumDy += ptr.positionChange().y
                                        ptr.consume()
                                    }
                                    val avgDy = sumDy / unconsumedTouches.size.toFloat()
                                    if (avgDy != 0f) {
                                        noteListState.dispatchRawDelta(-avgDy)
                                    }
                                }
                            }
                        }
                    }
                    .drawBehind {
                    val patternColor = notePatternColor
                    val marginLineColor = noteMarginLineColor

                    when (currentPattern) {
                        PaperPattern.PLAIN -> {}
                        PaperPattern.RULED -> {
                            val lineSpacingPx = 28.dp.toPx()
                            val marginXPx = 32.dp.toPx()

                            drawLine(
                                color = marginLineColor,
                                start = Offset(marginXPx, 0f),
                                end = Offset(marginXPx, size.height),
                                strokeWidth = 1.dp.toPx()
                            )

                            var y = lineSpacingPx
                            while (y < size.height) {
                                drawLine(
                                    color = patternColor,
                                    start = Offset(0f, y),
                                    end = Offset(size.width, y),
                                    strokeWidth = 1.dp.toPx()
                                )
                                y += lineSpacingPx
                            }
                        }
                        PaperPattern.GRID -> {
                            val gridSizePx = 24.dp.toPx()
                            var y = gridSizePx
                            while (y < size.height) {
                                drawLine(
                                    color = patternColor,
                                    start = Offset(0f, y),
                                    end = Offset(size.width, y),
                                    strokeWidth = 0.75.dp.toPx()
                                )
                                y += gridSizePx
                            }
                            var x = gridSizePx
                            while (x < size.width) {
                                drawLine(
                                    color = patternColor,
                                    start = Offset(x, 0f),
                                    end = Offset(x, size.height),
                                    strokeWidth = 0.75.dp.toPx()
                                )
                                x += gridSizePx
                            }
                        }
                        PaperPattern.DOTTED -> {
                            val dotSpacingPx = 22.dp.toPx()
                            val dotRadius = 1.25.dp.toPx()
                            var y = dotSpacingPx
                            while (y < size.height) {
                                var x = dotSpacingPx
                                while (x < size.width) {
                                    drawCircle(
                                        color = patternColor,
                                        radius = dotRadius,
                                        center = Offset(x, y)
                                    )
                                    x += dotSpacingPx
                                }
                                y += dotSpacingPx
                            }
                        }
                    }
                }
        ) {
            val editorContentPadding: PaddingValues = remember(dockState, isToolbarCollapsed) {
                when {
                    isToolbarCollapsed -> PaddingValues(start = 0.dp, end = 0.dp, top = 8.dp, bottom = 24.dp)
                    dockState == ToolbarDockState.BOTTOM -> PaddingValues(start = 0.dp, end = 0.dp, top = 8.dp, bottom = 72.dp)
                    dockState == ToolbarDockState.TOP -> PaddingValues(start = 0.dp, end = 0.dp, top = 64.dp, bottom = 24.dp)
                    dockState == ToolbarDockState.LEFT -> PaddingValues(start = 60.dp, end = 0.dp, top = 8.dp, bottom = 24.dp)
                    dockState == ToolbarDockState.RIGHT -> PaddingValues(start = 0.dp, end = 60.dp, top = 8.dp, bottom = 24.dp)
                    else -> PaddingValues(start = 0.dp, end = 0.dp, top = 8.dp, bottom = 24.dp)
                }
            }

            if (isNavigationOverviewMode) {
                val pageStartIndex = 1 +
                    (if (localPdfFilePath != null) 1 else 0) +
                    (if (pdfPagePaths.isNotEmpty() || pdfPreviewBitmap != null) 1 else 0)

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(colorScheme.background.copy(alpha = 0.94f))
                        .padding(editorContentPadding)
                ) {
                    Surface(
                        color = surfaceColor,
                        tonalElevation = 2.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Apps,
                                    contentDescription = null,
                                    tint = primaryColor,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Gezinti Modu • ${pageFieldValues.size} Sayfa",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = onSurfaceColor
                                )
                            }
                            TextButton(
                                onClick = { isNavigationOverviewMode = false },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text("Normal Görünüm", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }

                    HorizontalDivider(color = onSurfaceColor.copy(alpha = 0.12f))

                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 148.dp),
                        contentPadding = PaddingValues(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        gridItemsIndexed(
                            items = pageFieldValues,
                            key = { idx, _ -> "overview_page_$idx" }
                        ) { pageIndex, pageTfv ->
                            val isPageActive = pageIndex == safeActivePageIndex
                            val pageCtrl = getOrCreatePageDrawingController(pageIndex)
                            val strokeCount = pageCtrl.strokes.size

                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = effectiveBgColor,
                                tonalElevation = 3.dp,
                                shadowElevation = 4.dp,
                                border = androidx.compose.foundation.BorderStroke(
                                    width = if (isPageActive) 2.dp else 1.dp,
                                    color = if (isPageActive) effectivePaperAccent else noteTextColor.copy(alpha = 0.18f)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(210.dp)
                                    .clickable {
                                        activePageIndex = pageIndex
                                        isNavigationOverviewMode = false
                                        drawingScope.launch {
                                            kotlinx.coroutines.delay(70L)
                                            runCatching {
                                                noteListState.animateScrollToItem(pageStartIndex + pageIndex)
                                            }
                                        }
                                    }
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(10.dp)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(bottom = 26.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        if (pageIndex == 0 && vmTitle.isNotBlank()) {
                                            Text(
                                                text = vmTitle,
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = effectiveTitleColor,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Text(
                                            text = pageTfv.text.trim().ifBlank { "Boş sayfa" },
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontSize = 11.sp,
                                                lineHeight = 15.sp
                                            ),
                                            color = if (pageTfv.text.isBlank()) noteMutedTextColor else noteTextColor,
                                            maxLines = 7,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        val annotationCount = strokeCount + pageCtrl.textBoxes.size
                                        if (annotationCount > 0) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = effectivePaperAccent.copy(alpha = 0.12f)
                                            ) {
                                                Text(
                                                    text = "$annotationCount öğe",
                                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                                    color = effectivePaperAccent,
                                                    fontWeight = FontWeight.SemiBold,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }

                                    if (pageFieldValues.size > 1) {
                                        IconButton(
                                            onClick = {
                                                val hasContent = pageTfv.text.isNotBlank() || pageCtrl.hasAnyAnnotations
                                                if (hasContent) {
                                                    pendingDeletePageIndex = pageIndex
                                                } else {
                                                    performDeleteNotePage(pageIndex)
                                                }
                                            },
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .size(22.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Sayfayı Sil",
                                                tint = noteMutedTextColor.copy(alpha = 0.8f),
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isPageActive) {
                                            effectivePaperAccent.copy(alpha = 0.16f)
                                        } else {
                                            noteTextColor.copy(alpha = 0.08f)
                                        },
                                        modifier = Modifier.align(Alignment.BottomCenter)
                                    ) {
                                        Text(
                                            text = "Sayfa ${pageIndex + 1}",
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                            fontWeight = FontWeight.Bold,
                                            color = if (isPageActive) effectivePaperAccent else noteMutedTextColor,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }
                        }

                        item(key = "overview_add_page") {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = surfaceColor.copy(alpha = 0.72f),
                                border = androidx.compose.foundation.BorderStroke(
                                    width = 1.2.dp,
                                    color = primaryColor.copy(alpha = 0.4f)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(210.dp)
                                    .clickable {
                                        val newPageIdx = pageFieldValues.size
                                        pageFieldValues.add(
                                            TextFieldValue(
                                                annotatedString = AnnotatedString(""),
                                                selection = TextRange(0)
                                            )
                                        )
                                        getOrCreatePageDrawingController(newPageIdx)
                                        activePageIndex = newPageIdx
                                        isNavigationOverviewMode = false
                                        pageChangeTick++
                                        val taggedText = buildJoinedTaggedText()
                                        val fullContent = composeContentWithPdfPageTags(taggedText, pdfPagePaths, preservedPdfFileTag)
                                        viewModel.onContentChanged(fullContent)
                                        drawingScope.launch {
                                            kotlinx.coroutines.delay(80L)
                                            runCatching {
                                                noteListState.animateScrollToItem(pageStartIndex + newPageIdx)
                                            }
                                        }
                                    }
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = primaryColor.copy(alpha = 0.14f),
                                        modifier = Modifier.size(38.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.Add,
                                                contentDescription = "Yeni Sayfa Ekle",
                                                tint = primaryColor,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Yeni Sayfa Ekle",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = primaryColor
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    state = noteListState,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = noteZoomScale
                            scaleY = noteZoomScale
                            transformOrigin = TransformOrigin(0.5f, 0f)
                        },
                    userScrollEnabled = !drawingController.isDrawingActive,
                    contentPadding = editorContentPadding
                ) {
                    item(key = "note_title_header") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp)
                        ) {
                            BasicTextField(
                                value = vmTitle,
                                onValueChange = { viewModel.onTitleChanged(it) },
                                textStyle = TextStyle(
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = effectiveTitleColor
                                ),
                                cursorBrush = SolidColor(effectivePaperAccent),
                                modifier = Modifier.fillMaxWidth(),
                                decorationBox = { innerTextField ->
                                    if (vmTitle.isEmpty()) {
                                        Text(
                                            text = "Başlık",
                                            fontSize = 24.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = noteMutedTextColor
                                        )
                                    }
                                    innerTextField()
                                }
                            )

                            Spacer(modifier = Modifier.height(6.dp))
                            HorizontalDivider(color = noteTextColor.copy(alpha = 0.12f))
                            Spacer(modifier = Modifier.height(2.dp))
                        }
                    }

                    if (localPdfFilePath != null) {
                        item(key = "pdf_reader_launcher_card") {
                            val safePdfPath = localPdfFilePath ?: ""
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = surfaceColor,
                                tonalElevation = 2.dp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp)
                                    .padding(bottom = 12.dp)
                                    .border(
                                        width = 1.dp,
                                        color = primaryColor.copy(alpha = 0.3f),
                                        shape = RoundedCornerShape(14.dp)
                                    )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Surface(
                                            shape = CircleShape,
                                            color = Color(0xFFEF4444).copy(alpha = 0.15f),
                                            modifier = Modifier.size(34.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = Icons.Default.PictureAsPdf,
                                                    contentDescription = null,
                                                    tint = Color(0xFFEF4444),
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                        Text(
                                            text = "PDF Belgesi",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = onSurfaceColor
                                        )
                                    }
                                    TextButton(
                                        onClick = {
                                            context.startActivity(
                                                PdfReaderActivity.createIntent(
                                                    context = context,
                                                    pdfFilePath = safePdfPath,
                                                    title = vmTitle.ifBlank { "PDF Belgesi" },
                                                    skipChooser = true
                                                )
                                            )
                                        }
                                    ) {
                                        Text(
                                            text = "Okuyucuda Aç",
                                            color = primaryColor,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (pdfPagePaths.isNotEmpty()) {
                        item(key = "pdf_document_viewer") {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = surfaceColor,
                                tonalElevation = 2.dp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp)
                                    .padding(bottom = 14.dp)
                                    .border(
                                        width = 1.dp,
                                        color = onSurfaceColor.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(14.dp)
                                    )
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Surface(
                                                shape = CircleShape,
                                                color = primaryColor.copy(alpha = 0.12f),
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Text(
                                                        text = "PDF",
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = primaryColor
                                                    )
                                                }
                                            }
                                            Column {
                                                Text(
                                                    text = "PDF Belgesi",
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = onSurfaceColor
                                                )
                                                Text(
                                                    text = "Sayfa ${activePdfPageIndex + 1} / ${pdfPagePaths.size}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = textMutedColor
                                                )
                                            }
                                        }

                                        TextButton(
                                            onClick = { isPdfPreviewExpanded = !isPdfPreviewExpanded }
                                        ) {
                                            Text(
                                                text = if (isPdfPreviewExpanded) "Gizle" else "Önizle",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = primaryColor,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }

                                    if (isPdfPreviewExpanded) {
                                        Spacer(modifier = Modifier.height(10.dp))

                                        val safePageIndex = activePdfPageIndex.coerceIn(0, pdfPagePaths.lastIndex)
                                        val currentPath = pdfPagePaths[safePageIndex]

                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Color.White)
                                                .border(0.5.dp, onSurfaceColor.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                                        ) {
                                            AsyncImage(
                                                model = File(currentPath),
                                                contentDescription = "PDF Sayfa ${safePageIndex + 1}",
                                                contentScale = ContentScale.FillWidth,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(10.dp))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            OutlinedButton(
                                                onClick = {
                                                    if (activePdfPageIndex > 0) activePdfPageIndex--
                                                },
                                                enabled = activePdfPageIndex > 0,
                                                shape = RoundedCornerShape(8.dp),
                                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                            ) {
                                                Text("Önceki", fontSize = 12.sp)
                                            }

                                            Text(
                                                text = "${activePdfPageIndex + 1} / ${pdfPagePaths.size}",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = onSurfaceColor
                                            )

                                            OutlinedButton(
                                                onClick = {
                                                    if (activePdfPageIndex < pdfPagePaths.lastIndex) activePdfPageIndex++
                                                },
                                                enabled = activePdfPageIndex < pdfPagePaths.lastIndex,
                                                shape = RoundedCornerShape(8.dp),
                                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                            ) {
                                                Text("Sonraki", fontSize = 12.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else if (pdfPreviewBitmap != null) {
                        item(key = "pdf_preview_single") {
                            pdfPreviewBitmap?.let { previewBmp ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = surfaceColor,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp)
                                        .padding(bottom = 12.dp)
                                        .border(
                                            width = 1.dp,
                                            color = onSurfaceColor.copy(alpha = 0.15f),
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text(
                                            text = "1. Sayfa",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = primaryColor,
                                            modifier = Modifier.padding(bottom = 6.dp)
                                        )
                                        Image(
                                            bitmap = previewBmp.asImageBitmap(),
                                            contentDescription = "PDF İlk Sayfa Önizleme",
                                            contentScale = ContentScale.FillWidth,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                        )
                                    }
                                }
                            }
                        }
                    }

                    itemsIndexed(
                        items = pageFieldValues,
                        key = { idx, _ -> "note_page_$idx" }
                    ) { pageIndex, pageTfv ->
                        val pageCtrl = getOrCreatePageDrawingController(pageIndex)
                        val pageFocusRequester = getOrCreatePageFocusRequester(pageIndex)
                        val isPageActive = pageIndex == safeActivePageIndex
                        val bodyTextStyle = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = 16.sp,
                            lineHeight = 24.sp,
                            color = noteTextColor
                        )
                        val minBodyHeight = (editorViewportHeightDp - 110.dp).coerceAtLeast(420.dp)
                        val pageBodyCapacityPx = with(density) { (minBodyHeight - 48.dp).toPx() }
                        val pageMatches = remember(searchMatches, pageIndex) {
                            searchMatches.filter { it.pageIndex == pageIndex }
                        }
                        val activeMatchOnPage = remember(searchMatches, currentSearchMatchIndex, pageIndex) {
                            searchMatches.getOrNull(currentSearchMatchIndex)?.takeIf { it.pageIndex == pageIndex }
                        }
                        val searchHighlightTransformation = remember(isSearchBarVisible, searchQuery, pageMatches, activeMatchOnPage) {
                            if (!isSearchBarVisible || searchQuery.isBlank() || pageMatches.isEmpty()) {
                                androidx.compose.ui.text.input.VisualTransformation.None
                            } else {
                                androidx.compose.ui.text.input.VisualTransformation { original ->
                                    val spans = original.spanStyles.toMutableList()
                                    val len = original.text.length
                                    pageMatches.forEach { m ->
                                        val s = m.start.coerceIn(0, len)
                                        val e = m.end.coerceIn(s, len)
                                        if (e > s) {
                                            val isCurrent = activeMatchOnPage == m
                                            spans.add(
                                                AnnotatedString.Range(
                                                    SpanStyle(
                                                        background = if (isCurrent) {
                                                            Color(0xFFFF9800).copy(alpha = 0.72f)
                                                        } else {
                                                            Color(0xFFFFD54F).copy(alpha = 0.45f)
                                                        }
                                                    ),
                                                    s,
                                                    e
                                                )
                                            )
                                        }
                                    }
                                    androidx.compose.ui.text.input.TransformedText(
                                        AnnotatedString(original.text, spans),
                                        androidx.compose.ui.text.input.OffsetMapping.Identity
                                    )
                                }
                            }
                        }

                        Column(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (pageIndex > 0) {
                                HorizontalDivider(
                                    color = noteTextColor.copy(alpha = 0.14f),
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                )
                            }

                            if (isPageActive && isCheckboxActive) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 2.dp),
                                    horizontalArrangement = Arrangement.Start,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = effectivePaperAccent.copy(alpha = 0.12f),
                                        modifier = Modifier.clickable { handleCheckboxToggled() }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (isCheckboxChecked) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                                                contentDescription = "Kutucuğu Değiştir",
                                                tint = effectivePaperAccent,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Text(
                                                text = if (isCheckboxChecked) "Tamamlandı" else "İşaretle",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = effectivePaperAccent
                                            )
                                        }
                                    }
                                }
                            }

                            // Normal-size full-width Paper Sheet strictly inheriting user's selected PaperPattern and PaperTint
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = minBodyHeight)
                                    .background(effectiveBgColor)
                                    .drawBehind {
                                        val patternColor = notePatternColor
                                        val marginLineColor = noteMarginLineColor

                                        when (currentPattern) {
                                            PaperPattern.PLAIN -> {}
                                            PaperPattern.RULED -> {
                                                val lineSpacingPx = 28.dp.toPx()
                                                val marginXPx = 32.dp.toPx()

                                                drawLine(
                                                    color = marginLineColor,
                                                    start = Offset(marginXPx, 0f),
                                                    end = Offset(marginXPx, size.height),
                                                    strokeWidth = 1.dp.toPx()
                                                )

                                                var y = lineSpacingPx
                                                while (y < size.height) {
                                                    drawLine(
                                                        color = patternColor,
                                                        start = Offset(0f, y),
                                                        end = Offset(size.width, y),
                                                        strokeWidth = 1.dp.toPx()
                                                    )
                                                    y += lineSpacingPx
                                                }
                                            }
                                            PaperPattern.GRID -> {
                                                val gridSizePx = 24.dp.toPx()
                                                var y = gridSizePx
                                                while (y < size.height) {
                                                    drawLine(
                                                        color = patternColor,
                                                        start = Offset(0f, y),
                                                        end = Offset(size.width, y),
                                                        strokeWidth = 0.75.dp.toPx()
                                                    )
                                                    y += gridSizePx
                                                }
                                                var x = gridSizePx
                                                while (x < size.width) {
                                                    drawLine(
                                                        color = patternColor,
                                                        start = Offset(x, 0f),
                                                        end = Offset(x, size.height),
                                                        strokeWidth = 0.75.dp.toPx()
                                                    )
                                                    x += gridSizePx
                                                }
                                            }
                                            PaperPattern.DOTTED -> {
                                                val dotSpacingPx = 22.dp.toPx()
                                                val dotRadius = 1.25.dp.toPx()
                                                var y = dotSpacingPx
                                                while (y < size.height) {
                                                    var x = dotSpacingPx
                                                    while (x < size.width) {
                                                        drawCircle(
                                                            color = patternColor,
                                                            radius = dotRadius,
                                                            center = Offset(x, y)
                                                        )
                                                        x += dotSpacingPx
                                                    }
                                                    y += dotSpacingPx
                                                }
                                            }
                                        }
                                    }
                                    .clickable(
                                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                        indication = null,
                                        enabled = !pageCtrl.isDrawingActive
                                    ) {
                                        activePageIndex = pageIndex
                                        runCatching { pageFocusRequester.requestFocus() }
                                        keyboardController?.show()
                                    }
                            ) {
                                BasicTextField(
                                    value = pageTfv,
                                    onValueChange = { updatedValue ->
                                        if (activePageIndex != pageIndex) {
                                            activePageIndex = pageIndex
                                        }
                                        // Soft-keyboard Backspace at start of Page 2+: user deleted leading PAGE_SENTINEL
                                        if (pageIndex > 0 &&
                                            pageTfv.text.startsWith(NoteRichTextHelper.PAGE_SENTINEL) &&
                                            !updatedValue.text.startsWith(NoteRichTextHelper.PAGE_SENTINEL)
                                        ) {
                                            if (updatedValue.text.length < pageTfv.text.length && pageTfv.selection.collapsed) {
                                                mergeOrDeletePageIntoPrevious(pageIndex)
                                                return@BasicTextField
                                            }
                                        }

                                        val normalizedUpdated = if (pageIndex > 0 && !updatedValue.text.startsWith(NoteRichTextHelper.PAGE_SENTINEL)) {
                                            val clean = NoteRichTextHelper.stripPageSentinel(updatedValue.text)
                                            val newText = NoteRichTextHelper.PAGE_SENTINEL + clean
                                            val newStart = (updatedValue.selection.start + 1).coerceIn(1, newText.length)
                                            val newEnd = (updatedValue.selection.end + 1).coerceIn(1, newText.length)
                                            updatedValue.copy(
                                                annotatedString = AnnotatedString(newText, updatedValue.annotatedString.spanStyles.mapNotNull { r ->
                                                    val s = (r.start + 1).coerceIn(1, newText.length)
                                                    val e = (r.end + 1).coerceIn(1, newText.length)
                                                    if (e > s) AnnotatedString.Range(r.item, s, e) else null
                                                }),
                                                selection = TextRange(newStart, newEnd)
                                            )
                                        } else {
                                            updatedValue
                                        }

                                        val grew = normalizedUpdated.text.length > pageTfv.text.length
                                        val enterHandled = NoteRichTextHelper.handleEnterOnListItem(
                                            oldValue = pageTfv,
                                            newValue = normalizedUpdated,
                                            isBoldActive = isBoldActive,
                                            isItalicActive = isItalicActive,
                                            isUnderlineActive = isUnderlineActive,
                                            activeTextColor = selectedTextColor,
                                            activeHighlightColor = selectedHighlightColor,
                                            activeFontSizeSp = selectedFontSizeSp,
                                            activeFontOption = selectedFontOption
                                        )
                                        val rawNextTfv = if (enterHandled != null) {
                                            enterHandled
                                        } else {
                                            NoteRichTextHelper.handleTextChange(
                                                oldValue = pageTfv,
                                                newValue = normalizedUpdated,
                                                isBoldActive = isBoldActive,
                                                isItalicActive = isItalicActive,
                                                isUnderlineActive = isUnderlineActive,
                                                activeTextColor = selectedTextColor,
                                                activeHighlightColor = selectedHighlightColor,
                                                activeFontSizeSp = selectedFontSizeSp,
                                                activeFontOption = selectedFontOption
                                            )
                                        }
                                        val nextTfv = if (pageIndex > 0 && !rawNextTfv.text.startsWith(NoteRichTextHelper.PAGE_SENTINEL)) {
                                            buildSentinelAwarePageValue(pageIndex, rawNextTfv.annotatedString, cursorAtEnd = false).copy(
                                                selection = TextRange(
                                                    (rawNextTfv.selection.start + 1).coerceIn(1, rawNextTfv.text.length + 1),
                                                    (rawNextTfv.selection.end + 1).coerceIn(1, rawNextTfv.text.length + 1)
                                                )
                                            )
                                        } else {
                                            rawNextTfv
                                        }
                                        if (pageIndex in pageFieldValues.indices) {
                                            pageFieldValues[pageIndex] = nextTfv
                                            pageChangeTick++
                                            if (grew) {
                                                lastTypedGrowthPage = pageIndex
                                            }
                                        }
                                    },
                                    visualTransformation = searchHighlightTransformation,
                                    onTextLayout = { layout ->
                                        if (isPageActive) {
                                            textLayoutResult = layout
                                        }
                                        // Auto-pagination 1 line before reaching bottom of page (deferred safely out of layout pass)
                                        if (isNoteLoaded && isPageActive && !isAutoPaginating && lastTypedGrowthPage == pageIndex && layout.lineCount >= 2) {
                                            val lastLineIdx = layout.lineCount - 1
                                            val lineH = (layout.getLineBottom(lastLineIdx) - layout.getLineTop(lastLineIdx))
                                                .coerceAtLeast(with(density) { 20.dp.toPx() })
                                            val triggerBottomPx = (pageBodyCapacityPx - lineH).coerceAtLeast(lineH * 6f)
                                            val cursorPos = pageTfv.selection.end.coerceIn(0, pageTfv.text.length)
                                            val cursorLine = layout.getLineForOffset(cursorPos)
                                            if (layout.getLineBottom(lastLineIdx) >= triggerBottomPx && cursorLine >= lastLineIdx) {
                                                lastTypedGrowthPage = -1
                                                isAutoPaginating = true
                                                val sourcePageText = pageTfv.text
                                                val bulletSnap = isBulletActive
                                                val numberedSnap = isNumberedActive
                                                val checkboxSnap = isCheckboxActive
                                                val nextPageIdx = pageIndex + 1
                                                val pageStartOffset = 1 +
                                                    (if (localPdfFilePath != null) 1 else 0) +
                                                    (if (pdfPagePaths.isNotEmpty() || pdfPreviewBitmap != null) 1 else 0)
                                                drawingScope.launch {
                                                    try {
                                                        // If Enter just appended an empty list prefix at bottom of prev page, move it cleanly to Page N+1
                                                        var carriedPrefix = NoteRichTextHelper.extractTrailingListContinuationPrefix(
                                                            prevPageText = sourcePageText,
                                                            isBulletActive = bulletSnap,
                                                            isNumberedActive = numberedSnap,
                                                            isCheckboxActive = checkboxSnap
                                                        )
                                                        val emptyTrailingPrefixMatch = Regex("""\n(• |\d+\.\s|\[ \] )$""").find(sourcePageText)
                                                        if (emptyTrailingPrefixMatch != null && pageIndex in pageFieldValues.indices) {
                                                            carriedPrefix = emptyTrailingPrefixMatch.groupValues[1]
                                                            val trimmedText = sourcePageText.substring(0, emptyTrailingPrefixMatch.range.first)
                                                            val trimmedSpans = pageFieldValues[pageIndex].annotatedString.spanStyles.mapNotNull { r ->
                                                                val s = r.start.coerceIn(0, trimmedText.length)
                                                                val e = r.end.coerceIn(0, trimmedText.length)
                                                                if (e > s) AnnotatedString.Range(r.item, s, e) else null
                                                            }
                                                            pageFieldValues[pageIndex] = TextFieldValue(
                                                                annotatedString = AnnotatedString(trimmedText, trimmedSpans),
                                                                selection = TextRange(trimmedText.length)
                                                            )
                                                        }

                                                        if (nextPageIdx >= pageFieldValues.size) {
                                                            val seedText = NoteRichTextHelper.PAGE_SENTINEL + carriedPrefix
                                                            pageFieldValues.add(
                                                                TextFieldValue(
                                                                    annotatedString = AnnotatedString(seedText),
                                                                    selection = TextRange(seedText.length)
                                                                )
                                                            )
                                                            getOrCreatePageDrawingController(nextPageIdx)
                                                        }
                                                        activePageIndex = nextPageIdx
                                                        if (carriedPrefix.startsWith("• ")) {
                                                            isBulletActive = true
                                                            isNumberedActive = false
                                                            isCheckboxActive = false
                                                        } else if (Regex("""^\d+\.\s""").containsMatchIn(carriedPrefix)) {
                                                            isNumberedActive = true
                                                            isBulletActive = false
                                                            isCheckboxActive = false
                                                        } else if (carriedPrefix.startsWith("[ ] ")) {
                                                            isCheckboxActive = true
                                                            isBulletActive = false
                                                            isNumberedActive = false
                                                        }
                                                        pageChangeTick++
                                                        kotlinx.coroutines.delay(45L)
                                                        runCatching {
                                                            noteListState.animateScrollToItem(pageStartOffset + nextPageIdx)
                                                        }
                                                        if (!drawingController.isDrawingActive) {
                                                            runCatching {
                                                                getOrCreatePageFocusRequester(nextPageIdx).requestFocus()
                                                            }
                                                        }
                                                    } finally {
                                                        isAutoPaginating = false
                                                    }
                                                }
                                            }
                                        }
                                    },
                                    textStyle = bodyTextStyle,
                                    cursorBrush = SolidColor(effectivePaperAccent),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .defaultMinSize(minHeight = minBodyHeight)
                                        .padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 40.dp)
                                        .focusRequester(pageFocusRequester)
                                        .onPreviewKeyEvent { keyEvent ->
                                            if (pageIndex > 0 &&
                                                keyEvent.type == KeyEventType.KeyDown &&
                                                (keyEvent.key == Key.Backspace ||
                                                    keyEvent.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_DEL) &&
                                                pageTfv.selection.collapsed &&
                                                pageTfv.selection.start <= 1
                                            ) {
                                                mergeOrDeletePageIntoPrevious(pageIndex)
                                                true
                                            } else {
                                                false
                                            }
                                        }
                                        .onFocusChanged { focusState ->
                                            if (focusState.isFocused && activePageIndex != pageIndex) {
                                                activePageIndex = pageIndex
                                            }
                                        },
                                    decorationBox = { innerTextField ->
                                        Box(modifier = Modifier.fillMaxWidth()) {
                                            if (NoteRichTextHelper.stripPageSentinel(pageTfv.text).isEmpty()) {
                                                Text(
                                                    text = if (pageIndex == 0) {
                                                        "Notunuzu yazın..."
                                                    } else {
                                                        "${pageIndex + 1}. sayfaya notunuzu yazın..."
                                                    },
                                                    fontSize = 16.sp,
                                                    color = noteMutedTextColor
                                                )
                                            }
                                            innerTextField()
                                        }
                                    }
                                )

                                // Kalem & Çizim Stüdyosu Katmanı — yalnızca çizim aktifken veya çizim varsa mount edilir (3GB RAM optimizasyonu)
                                if (pageCtrl.isDrawingActive || pageCtrl.hasAnyAnnotations) {
                                    DrawingCanvasOverlay(
                                        controller = pageCtrl,
                                        modifier = Modifier.matchParentSize(),
                                        defaultTextBoxColor = noteTextColor,
                                        onTwoFingerScroll = { dy, _ -> noteListState.dispatchRawDelta(-dy) },
                                        onLassoAskAi = if (drawingAiEnabled) {
                                            { cropBmp ->
                                                lassoAiCropBitmap?.let { if (!it.isRecycled) runCatching { it.recycle() } }
                                                lassoAiSourceLabel = "Not • Sayfa ${pageIndex + 1}"
                                                lassoAiCropBitmap = cropBmp
                                            }
                                        } else {
                                            null
                                        }
                                    )
                                }

                                // Sayfa altında tam ortada sayfa numarası
                                Text(
                                    text = if (pageFieldValues.size > 1) {
                                        "Sayfa ${pageIndex + 1} / ${pageFieldValues.size}"
                                    } else {
                                        "Sayfa ${pageIndex + 1}"
                                    },
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = if (isPageActive) FontWeight.Bold else FontWeight.Medium,
                                        letterSpacing = 0.5.sp
                                    ),
                                    color = if (isPageActive) {
                                        effectivePaperAccent.copy(alpha = 0.85f)
                                    } else {
                                        noteMutedTextColor.copy(alpha = 0.80f)
                                    },
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .padding(bottom = 10.dp)
                                )
                            }
                        }
                    }

                    item(key = "note_add_page_footer") {
                        Spacer(modifier = Modifier.height(14.dp))

                        // Prominent "+ Yeni Sayfa Ekle" Button strictly inheriting user's selected Page Layout
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = effectiveBgColor,
                            border = androidx.compose.foundation.BorderStroke(
                                width = 1.2.dp,
                                color = effectivePaperAccent.copy(alpha = 0.45f)
                            ),
                            shadowElevation = 2.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp)
                                .clickable {
                                    val newPageIdx = pageFieldValues.size
                                    pageFieldValues.add(
                                        TextFieldValue(
                                            annotatedString = AnnotatedString(NoteRichTextHelper.PAGE_SENTINEL),
                                            selection = TextRange(1)
                                        )
                                    )
                                    getOrCreatePageDrawingController(newPageIdx)
                                    activePageIndex = newPageIdx
                                    pageChangeTick++
                                    val snapshot = pageFieldValues.map { it.annotatedString }
                                    val pathsCopy = pdfPagePaths.toList()
                                    val fileTagCopy = preservedPdfFileTag
                                    drawingScope.launch {
                                        val fullContent = withContext(Dispatchers.Default) {
                                            composeContentWithPdfPageTags(buildJoinedTaggedText(snapshot), pathsCopy, fileTagCopy)
                                        }
                                        viewModel.onContentChanged(fullContent)
                                        kotlinx.coroutines.delay(65L)
                                        runCatching {
                                            val targetItem = (noteListState.layoutInfo.totalItemsCount - 2).coerceAtLeast(0)
                                            noteListState.animateScrollToItem(targetItem)
                                        }
                                        if (!drawingController.isDrawingActive) {
                                            runCatching {
                                                getOrCreatePageFocusRequester(newPageIdx).requestFocus()
                                                keyboardController?.show()
                                            }
                                        }
                                    }
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = effectivePaperAccent.copy(alpha = 0.15f),
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.Add,
                                                contentDescription = "Yeni Sayfa Ekle",
                                                tint = effectivePaperAccent,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                    Column {
                                        Text(
                                            text = "Yeni Sayfa Ekle",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = noteTextColor
                                        )
                                        Text(
                                            text = "${currentPattern.title} • $currentTintTitle düzeninde açılır",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = noteMutedTextColor
                                        )
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = effectivePaperAccent.copy(alpha = 0.12f)
                                ) {
                                    Text(
                                        text = "Sayfa ${pageFieldValues.size + 1}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = effectivePaperAccent,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Sayfa ${safeActivePageIndex + 1} / ${pageFieldValues.size}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = noteMutedTextColor
                            )
                            Text(
                                text = "$words kelime | $chars karakter",
                                style = MaterialTheme.typography.bodySmall,
                                color = noteMutedTextColor
                            )
                        }
                    }
                }
            }
        }

            // Real-time Border Glow when dragging near an edge
            if (isDragging && candidateBorder != null) {
                when (candidateBorder) {
                    ToolbarDockState.BOTTOM -> {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .height(4.dp)
                                .background(primaryColor)
                        )
                    }
                    ToolbarDockState.TOP -> {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .fillMaxWidth()
                                .height(4.dp)
                                .background(primaryColor)
                        )
                    }
                    ToolbarDockState.LEFT -> {
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .width(4.dp)
                                .fillMaxHeight()
                                .background(primaryColor)
                        )
                    }
                    ToolbarDockState.RIGHT -> {
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .width(4.dp)
                                .fillMaxHeight()
                                .background(primaryColor)
                        )
                    }
                    else -> {}
                }
            }

            // Toolbar / Collapsed Bubble UI
            val verticalToolbarW = with(density) { 54.dp.toPx() }
            val verticalToolbarH = with(density) { 410.dp.toPx() }
            val maxVerticalY = (screenHeightPx - verticalToolbarH).coerceAtLeast(0f)
            val effectiveVerticalY = dockedY.coerceIn(0f, maxVerticalY)

            val maxBubbleY = (screenHeightPx - bubbleSizePx - with(density) { 8.dp.toPx() }).coerceAtLeast(0f)
            val effectiveBubbleY = dockedY.coerceIn(with(density) { 8.dp.toPx() }, maxBubbleY)

            if (isToolbarCollapsed) {
                val bubbleModifier = if (isDragging) {
                    Modifier
                        .offset { IntOffset(dragPosition.x.roundToInt(), dragPosition.y.roundToInt()) }
                        .graphicsLayer {
                            scaleX = 1.08f
                            scaleY = 1.08f
                            shadowElevation = 14.dp.toPx()
                        }
                        .then(bubblePointerModifier)
                } else {
                    when (dockState) {
                        ToolbarDockState.BOTTOM -> Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp)
                        ToolbarDockState.TOP -> Modifier.align(Alignment.TopCenter).padding(top = 12.dp)
                        ToolbarDockState.LEFT -> Modifier.offset {
                            IntOffset(with(density) { 8.dp.toPx() }.roundToInt(), effectiveBubbleY.roundToInt())
                        }
                        ToolbarDockState.RIGHT -> Modifier.offset {
                            IntOffset((screenWidthPx - bubbleSizePx - with(density) { 8.dp.toPx() }).roundToInt(), effectiveBubbleY.roundToInt())
                        }
                        ToolbarDockState.FLOATING -> Modifier.offset {
                            IntOffset(floatingOffset.x.roundToInt(), floatingOffset.y.roundToInt())
                        }
                    }.then(bubblePointerModifier)
                }

                CollapsedToolbarBubble(
                    isPenModeActive = drawingController.isDrawingActive,
                    modifier = bubbleModifier,
                    onExpand = { isToolbarCollapsed = false }
                )
            } else {
                val isVertNow = if (isDragging) {
                    candidateBorder == ToolbarDockState.LEFT || candidateBorder == ToolbarDockState.RIGHT ||
                        (candidateBorder == null && (dockState == ToolbarDockState.LEFT || dockState == ToolbarDockState.RIGHT))
                } else {
                    dockState == ToolbarDockState.LEFT || dockState == ToolbarDockState.RIGHT
                }

                val currentToolbarModifier = if (isDragging) {
                    Modifier
                        .offset { IntOffset(dragPosition.x.roundToInt(), dragPosition.y.roundToInt()) }
                        .then(
                            if (isVertNow) Modifier.width(54.dp)
                            else if (dockState == ToolbarDockState.FLOATING) Modifier.widthIn(max = 360.dp)
                            else Modifier.widthIn(max = 760.dp).fillMaxWidth()
                        )
                        .graphicsLayer {
                            alpha = 0.92f
                            scaleX = 0.98f
                            scaleY = 0.98f
                            shadowElevation = 14.dp.toPx()
                        }
                } else {
                    when (dockState) {
                        ToolbarDockState.BOTTOM -> Modifier.align(Alignment.BottomCenter).widthIn(max = 760.dp).fillMaxWidth()
                        ToolbarDockState.TOP -> Modifier.align(Alignment.TopCenter).widthIn(max = 760.dp).fillMaxWidth()
                        ToolbarDockState.LEFT -> Modifier
                            .offset { IntOffset(0, effectiveVerticalY.roundToInt()) }
                            .width(54.dp)
                        ToolbarDockState.RIGHT -> Modifier
                            .offset { IntOffset((screenWidthPx - verticalToolbarW).roundToInt(), effectiveVerticalY.roundToInt()) }
                            .width(54.dp)
                        ToolbarDockState.FLOATING -> Modifier
                            .offset { IntOffset(floatingOffset.x.roundToInt(), floatingOffset.y.roundToInt()) }
                            .widthIn(max = 360.dp)
                    }
                }

                RichTextFormattingToolbar(
                    modifier = currentToolbarModifier,
                    dockState = if (isVertNow) (if (candidateBorder == ToolbarDockState.RIGHT || (candidateBorder == null && dockState == ToolbarDockState.RIGHT)) ToolbarDockState.RIGHT else ToolbarDockState.LEFT) else dockState,
                    dragModifier = toolbarDragModifier,
                    drawingController = drawingController,
                    onCollapse = { isToolbarCollapsed = true },
                    highlightIntensity = highlightIntensity,
                    onHighlightIntensityChanged = handleHighlightIntensityChanged,
                    selectedColor = selectedTextColor,
                    selectedHighlightColor = selectedHighlightColor,
                    selectedFontSizeSp = selectedFontSizeSp,
                    selectedFontOption = selectedFontOption,
                    isBoldActive = isBoldActive,
                    isItalicActive = isItalicActive,
                    isUnderlineActive = isUnderlineActive,
                    isBulletActive = isBulletActive,
                    isNumberedActive = isNumberedActive,
                    isCheckboxActive = isCheckboxActive,
                    isCheckboxChecked = isCheckboxChecked,
                    activeColorPanel = activeColorPanel,
                    activePenPanel = activePenPanel,
                    customColors = userCustomColors,
                    onSelectTextMode = {
                        drawingController.selectTool(DrawingTool.NONE)
                        activePenPanel = ActivePenPanel.NONE
                    },
                    onSelectPenMode = {
                        keyboardController?.hide()
                        if (!drawingController.isDrawingActive) {
                            drawingController.selectActiveOrLastPen()
                        }
                        activeColorPanel = ActiveColorPanel.NONE
                    },
                    onOpenPenPackages = {
                        keyboardController?.hide()
                        activeColorPanel = ActiveColorPanel.NONE
                        if (!drawingController.isDrawingActive || !drawingController.activeTool.isPenTool) {
                            drawingController.selectActiveOrLastPen()
                            activePenPanel = ActivePenPanel.PEN_PACKAGES
                        } else {
                            activePenPanel = if (activePenPanel == ActivePenPanel.PEN_PACKAGES) {
                                ActivePenPanel.NONE
                            } else {
                                ActivePenPanel.PEN_PACKAGES
                            }
                        }
                    },
                    onOpenEraserPackages = {
                        keyboardController?.hide()
                        activeColorPanel = ActiveColorPanel.NONE
                        if (!drawingController.isDrawingActive || !drawingController.activeTool.isEraserTool) {
                            drawingController.selectActiveOrLastEraser()
                            activePenPanel = ActivePenPanel.ERASER_PACKAGES
                        } else {
                            activePenPanel = if (activePenPanel == ActivePenPanel.ERASER_PACKAGES) {
                                ActivePenPanel.NONE
                            } else {
                                ActivePenPanel.ERASER_PACKAGES
                            }
                        }
                    },
                    onToggleColorPanel = { panel ->
                        activeColorPanel = if (panel == ActiveColorPanel.NONE || activeColorPanel == panel) {
                            ActiveColorPanel.NONE
                        } else {
                            panel
                        }
                    },
                    onTogglePenPanel = { panel ->
                        activePenPanel = if (panel == ActivePenPanel.NONE || activePenPanel == panel) {
                            ActivePenPanel.NONE
                        } else {
                            panel
                        }
                    },
                    onColorSelected = handleColorSelected,
                    onHighlightSelected = handleHighlightSelected,
                    onFontSizeSelected = handleFontSizeSelected,
                    onFontOptionSelected = handleFontOptionSelected,
                    onOpenCustomColorPicker = { isHighlight ->
                        customColorIsHighlight = isHighlight
                        showCustomColorDialog = true
                    },
                    onBulletToggled = handleBulletToggled,
                    onNumberedToggled = handleNumberedToggled,
                    onCheckboxToggled = handleCheckboxToggled,
                    onBoldToggled = onBoldToggled,
                    onItalicToggled = onItalicToggled,
                    onUnderlineToggled = onUnderlineToggled
                )

                val showSideTextPanel = !drawingController.isDrawingActive && activeColorPanel != ActiveColorPanel.NONE
                val showSidePenPanel = drawingController.isDrawingActive && activePenPanel != ActivePenPanel.NONE
                if ((dockState == ToolbarDockState.LEFT || dockState == ToolbarDockState.RIGHT) && (showSideTextPanel || showSidePenPanel) && !isDragging) {
                    val panelX = if (dockState == ToolbarDockState.LEFT) with(density) { 60.dp.toPx() } else (screenWidthPx - with(density) { 330.dp.toPx() }).coerceAtLeast(0f)
                    val panelY = effectiveVerticalY.coerceIn(0f, (screenHeightPx - with(density) { 260.dp.toPx() }).coerceAtLeast(0f))
                    Box(
                        modifier = Modifier
                            .offset { IntOffset(panelX.roundToInt(), panelY.roundToInt()) }
                            .widthIn(max = 320.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = surfaceColor,
                            shadowElevation = 10.dp,
                            tonalElevation = 6.dp,
                            border = androidx.compose.foundation.BorderStroke(1.dp, onSurfaceColor.copy(alpha = 0.15f))
                        ) {
                            if (showSideTextPanel) {
                                FormattingColorPanel(
                                    activeColorPanel = activeColorPanel,
                                    selectedColor = selectedTextColor,
                                    selectedHighlightColor = selectedHighlightColor,
                                    selectedFontSizeSp = selectedFontSizeSp,
                                    selectedFontOption = selectedFontOption,
                                    highlightIntensity = highlightIntensity,
                                    onHighlightIntensityChanged = handleHighlightIntensityChanged,
                                    customColors = userCustomColors,
                                    onToggleColorPanel = { panel ->
                                        activeColorPanel = if (activeColorPanel == panel) ActiveColorPanel.NONE else panel
                                    },
                                    onColorSelected = handleColorSelected,
                                    onHighlightSelected = handleHighlightSelected,
                                    onFontSizeSelected = handleFontSizeSelected,
                                    onFontOptionSelected = handleFontOptionSelected,
                                    onOpenCustomColorPicker = { isHighlight ->
                                        customColorIsHighlight = isHighlight
                                        showCustomColorDialog = true
                                    },
                                    primaryColor = primaryColor,
                                    onSurfaceColor = onSurfaceColor,
                                    surfaceColor = surfaceColor,
                                    colorScheme = colorScheme
                                )
                            } else {
                                when (activePenPanel) {
                                    ActivePenPanel.PEN_PACKAGES -> PenPackagePickerPanel(
                                        controller = drawingController,
                                        onDismiss = { activePenPanel = ActivePenPanel.NONE }
                                    )
                                    ActivePenPanel.ERASER_PACKAGES -> EraserPackagePickerPanel(
                                        controller = drawingController,
                                        onDismiss = { activePenPanel = ActivePenPanel.NONE }
                                    )
                                    ActivePenPanel.SETTINGS -> PenStrokeSettingsPanel(
                                        controller = drawingController,
                                        onDismiss = { activePenPanel = ActivePenPanel.NONE }
                                    )
                                    ActivePenPanel.SHAPES -> PenShapePickerPanel(
                                        controller = drawingController,
                                        onDismiss = { activePenPanel = ActivePenPanel.NONE }
                                    )
                                    ActivePenPanel.LASSO -> PenLassoActionsPanel(
                                        controller = drawingController,
                                        onDismiss = { activePenPanel = ActivePenPanel.NONE }
                                    )
                                    ActivePenPanel.TEXT_BOX -> DrawingTextBoxFormattingPanel(
                                        controller = drawingController
                                    )
                                    ActivePenPanel.NONE -> {}
                                }
                            }
                        }
                    }
                }
            }

            // Compact floating Zoom pill (visible only when page plane is zoomed)
            if (kotlin.math.abs(noteZoomScale - 1.0f) > 0.02f) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = surfaceColor.copy(alpha = 0.92f),
                    tonalElevation = 4.dp,
                    shadowElevation = 6.dp,
                    border = androidx.compose.foundation.BorderStroke(1.dp, primaryColor.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        IconButton(
                            onClick = { noteZoomScale = (noteZoomScale - 0.15f).coerceAtLeast(0.75f) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = "Uzaklaştır", modifier = Modifier.size(14.dp))
                        }
                        Text(
                            text = "%${(noteZoomScale * 100).roundToInt()}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = primaryColor,
                            modifier = Modifier
                                .clickable { noteZoomScale = 1.0f }
                                .padding(horizontal = 4.dp)
                        )
                        IconButton(
                            onClick = { noteZoomScale = (noteZoomScale + 0.15f).coerceAtMost(2.0f) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Yakınlaştır", modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }

            // Live-synced Draggable / Resizable Floating Focus Timer Overlay
            FloatingFocusTimerOverlay(
                viewportWidthPx = screenWidthPx,
                viewportHeightPx = screenHeightPx
            )
        }
    }

    if (pendingDeletePageIndex != null) {
        val targetDelIdx = pendingDeletePageIndex!!
        AlertDialog(
            onDismissRequest = { pendingDeletePageIndex = null },
            title = {
                Text(
                    text = "${targetDelIdx + 1}. Sayfayı Sil",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text("Bu sayfadaki metin ve çizimler silinecek. Devam etmek istiyor musunuz?")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDeletePageIndex = null
                        performDeleteNotePage(targetDelIdx)
                    }
                ) {
                    Text(
                        "Sayfayı Sil",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeletePageIndex = null }) {
                    Text("İptal")
                }
            }
        )
    }

    if (showCustomColorDialog) {
        CustomColorPickerDialog(
            initialColor = if (customColorIsHighlight) (selectedHighlightColor ?: Color(0xFFFFEB3B)) else (selectedTextColor ?: primaryColor),
            isHighlightMode = customColorIsHighlight,
            highlightIntensity = highlightIntensity,
            onDismissRequest = { showCustomColorDialog = false },
            onColorSelected = { chosenColor ->
                if (!userCustomColors.contains(chosenColor)) {
                    userCustomColors = (listOf(chosenColor) + userCustomColors).take(8)
                }
                if (customColorIsHighlight) {
                    handleHighlightSelected(chosenColor)
                } else {
                    handleColorSelected(chosenColor)
                }
                showCustomColorDialog = false
            }
        )
    }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = {
                Text(
                    text = "Notu Sil",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text("Bu not kalıcı olarak silinecek.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val idToDelete = vmNoteId
                        viewModel.deleteNote {
                            if (idToDelete.isNotBlank()) {
                                DrawingSerializer.deleteNoteDrawing(context, idToDelete)
                                NoteTimeTracker.clearNote(context, idToDelete)
                            }
                            showDeleteConfirmDialog = false
                            onDismiss()
                        }
                    }
                ) {
                    Text(
                        "Sil",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("İptal")
                }
            }
        )
    }

    if (showPageStyleDialog) {
        PageStyleDialog(
            currentPattern = currentPattern,
            currentTintHex = currentTintHex,
            onPatternSelected = { newPattern ->
                viewModel.setPaperPattern(newPattern)
            },
            onTintSelected = { newTintHex ->
                viewModel.setPaperTint(newTintHex)
            },
            onDismissRequest = { showPageStyleDialog = false }
        )
    }

    if (showStatsDialog) {
        NoteStatsDialog(
            words = words,
            chars = chars,
            lines = lineCount,
            spentSeconds = totalNoteSpentSeconds,
            onDismissRequest = { showStatsDialog = false }
        )
    }

    if (showNoteMusicSheet) {
        com.example.sharkmentor.ui.components.MusicPlayerModalSheet(
            onDismiss = { showNoteMusicSheet = false }
        )
    }

    if (showAiSheet && notesAiEnabled) {
        val hasMultiplePages = pdfPagePaths.size > 1 || pageFieldValues.size > 1
        val aiHeaderTitle = when {
            pdfPagePaths.isNotEmpty() -> "Not Asistanı • PDF S.${activePdfPageIndex + 1}"
            pageFieldValues.size > 1 -> "Not Asistanı • S.${activePageIndex + 1}"
            else -> "Not Asistanı"
        }

        ModalBottomSheet(
            onDismissRequest = {
                viewModel.cancelAiAnalysis()
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
                        text = aiHeaderTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = onSurfaceColor
                    )
                }

                if (hasMultiplePages) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DocumentAiScope.entries.forEach { scopeOption ->
                            val chipLabel = if (scopeOption == DocumentAiScope.WHOLE_DOC_TEXT && pdfPagePaths.isEmpty()) {
                                "Tüm Not"
                            } else {
                                scopeOption.label
                            }
                            FilterChip(
                                selected = aiScope == scopeOption,
                                onClick = { aiScope = scopeOption },
                                label = { Text(text = chipLabel, fontSize = 12.sp) }
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
                        "Özet çıkar",
                        "Kavramları açıkla",
                        "Soru-cevap",
                        "Önemli noktalar"
                    ).forEach { quickPrompt ->
                        SuggestionChip(
                            onClick = {
                                aiQueryText = quickPrompt
                                viewModel.analyzeNoteWithAi(
                                    query = quickPrompt,
                                    scope = aiScope,
                                    activePdfPageIndex = activePdfPageIndex,
                                    activeNotePageIndex = activePageIndex
                                )
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
                            Text(
                                text = "Soru sor...",
                                color = textMutedColor
                            )
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
                        IconButton(onClick = { viewModel.cancelAiAnalysis() }) {
                            Icon(
                                imageVector = Icons.Default.Stop,
                                contentDescription = "Durdur",
                                tint = colorScheme.error
                            )
                        }
                    } else {
                        IconButton(
                            onClick = {
                                viewModel.analyzeNoteWithAi(
                                    query = aiQueryText,
                                    scope = aiScope,
                                    activePdfPageIndex = activePdfPageIndex,
                                    activeNotePageIndex = activePageIndex
                                )
                            }
                        ) {
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
                            text = "Analiz ediliyor...",
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
                    if (aiSavedNewNoteId != null) {
                        Surface(
                            color = primaryColor.copy(alpha = 0.08f),
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
                                    text = "Yeni not olarak kaydedildi",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium,
                                    color = primaryColor
                                )
                            }
                        }
                    }

                    Surface(
                        color = colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, onSurfaceColor.copy(alpha = 0.10f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 380.dp)
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
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        val exportRes = PdfExporter.exportNoteToPdf(
                                            context = context,
                                            title = "[Özet] $vmTitle",
                                            rawContent = formatMathAsPlainText(aiResult ?: "")
                                        )
                                        exportRes.onSuccess { res ->
                                            Toast.makeText(context, "PDF kaydedildi: ${res.fileName}", Toast.LENGTH_SHORT).show()
                                            PdfExporter.openOrSharePdf(context, res.fileUri, res.fileName)
                                        }.onFailure { err ->
                                            Toast.makeText(context, "PDF oluşturulamadı: ${err.localizedMessage}", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PictureAsPdf,
                                        contentDescription = null,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("PDF", fontSize = 12.sp)
                                }

                                OutlinedButton(
                                    onClick = {
                                        viewModel.saveAiSummaryAsNewNote(formatMathAsPlainText(aiResult ?: ""))
                                    },
                                    enabled = aiSavedNewNoteId == null,
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = if (aiSavedNewNoteId != null) "Kaydedildi" else "Yeni Not",
                                        fontSize = 12.sp
                                    )
                                }

                                Button(
                                    onClick = {
                                        val appended = viewModel.appendAiResultToNote()
                                        val cleanAppended = stripPdfPageTags(appended)
                                        populatePagesFromCleanText(cleanAppended)
                                        showAiSheet = false
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = primaryColor,
                                        contentColor = colorScheme.onPrimary
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = "Nota Ekle",
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

    // Inline Lasso -> "AI'ya Sor" Solve Sheet (keeps Note open & zero Gallery clutter)
    lassoAiCropBitmap?.let { cropBmp ->
        LassoAiSolveSheet(
            cropBitmap = cropBmp,
            sourceLabel = lassoAiSourceLabel,
            onAppendToNote = { solutionText ->
                val appended = viewModel.appendAiResultToNote(solutionText)
                val cleanAppended = stripPdfPageTags(appended)
                populatePagesFromCleanText(cleanAppended)
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
private fun DragHandleView(modifier: Modifier = Modifier) {
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    Column(
        modifier = modifier.padding(horizontal = 4.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.5.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(width = 16.dp, height = 2.5.dp)
                .clip(CircleShape)
                .background(onSurfaceColor.copy(alpha = 0.45f))
        )
        Box(
            modifier = Modifier
                .size(width = 16.dp, height = 2.5.dp)
                .clip(CircleShape)
                .background(onSurfaceColor.copy(alpha = 0.45f))
        )
    }
}

@Composable
private fun VerticalDragHandleView(modifier: Modifier = Modifier) {
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    Row(
        modifier = modifier.padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(width = 2.5.dp, height = 16.dp)
                .clip(CircleShape)
                .background(onSurfaceColor.copy(alpha = 0.45f))
        )
        Box(
            modifier = Modifier
                .size(width = 2.5.dp, height = 16.dp)
                .clip(CircleShape)
                .background(onSurfaceColor.copy(alpha = 0.45f))
        )
    }
}

@Composable
fun CollapsedToolbarBubble(
    isPenModeActive: Boolean = false,
    modifier: Modifier = Modifier,
    onExpand: () -> Unit = {}
) {
    val colorScheme = MaterialTheme.colorScheme
    val primaryColor = colorScheme.primary
    val surfaceColor = colorScheme.surface

    Surface(
        modifier = modifier.size(46.dp),
        shape = CircleShape,
        color = surfaceColor,
        border = androidx.compose.foundation.BorderStroke(1.5.dp, primaryColor),
        shadowElevation = 8.dp,
        tonalElevation = 6.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null,
                    onClick = onExpand
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isPenModeActive) Icons.Default.Edit else Icons.Default.BorderColor,
                contentDescription = "Araç Çubuğunu Aç",
                tint = primaryColor,
                modifier = Modifier.size(20.dp)
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(5.dp)
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(primaryColor)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun RichTextFormattingToolbar(
    drawingController: DrawingController,
    selectedColor: Color?,
    selectedHighlightColor: Color?,
    selectedFontSizeSp: Float = NoteRichTextHelper.DEFAULT_FONT_SIZE_SP,
    selectedFontOption: NoteFontOption = NoteFontOption.DEFAULT,
    isBoldActive: Boolean,
    isItalicActive: Boolean,
    isUnderlineActive: Boolean,
    isBulletActive: Boolean,
    isNumberedActive: Boolean,
    isCheckboxActive: Boolean,
    isCheckboxChecked: Boolean,
    activeColorPanel: ActiveColorPanel,
    activePenPanel: ActivePenPanel,
    customColors: List<Color>,
    dockState: ToolbarDockState = ToolbarDockState.BOTTOM,
    highlightIntensity: Float = 0.60f,
    onHighlightIntensityChanged: (Float) -> Unit = {},
    onCollapse: () -> Unit = {},
    onSelectTextMode: () -> Unit = {},
    onSelectPenMode: () -> Unit = {},
    onOpenPenPackages: () -> Unit = {},
    onOpenEraserPackages: () -> Unit = {},
    onToggleColorPanel: (ActiveColorPanel) -> Unit,
    onTogglePenPanel: (ActivePenPanel) -> Unit,
    onColorSelected: (Color) -> Unit,
    onHighlightSelected: (Color?) -> Unit,
    onFontSizeSelected: (Float) -> Unit = {},
    onFontOptionSelected: (NoteFontOption) -> Unit = {},
    onOpenCustomColorPicker: (isHighlight: Boolean) -> Unit,
    onBulletToggled: () -> Unit,
    onNumberedToggled: () -> Unit,
    onCheckboxToggled: () -> Unit,
    onBoldToggled: () -> Unit,
    onItalicToggled: () -> Unit,
    onUnderlineToggled: () -> Unit,
    modifier: Modifier = Modifier,
    dragModifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val onSurfaceColor = colorScheme.onSurface
    val primaryColor = colorScheme.primary
    val surfaceColor = colorScheme.surface

    val isVertical = dockState == ToolbarDockState.LEFT || dockState == ToolbarDockState.RIGHT
    val isPenMode = drawingController.isDrawingActive
    val activeDrawTool = drawingController.activeTool
    val currentPenTool = if (activeDrawTool.isPenTool) activeDrawTool else drawingController.lastSelectedPenTool
    val currentEraserTool = if (activeDrawTool.isEraserTool) activeDrawTool else drawingController.lastSelectedEraserTool
    val haptic = LocalHapticFeedback.current

    val shape = when (dockState) {
        ToolbarDockState.BOTTOM -> RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
        ToolbarDockState.TOP -> RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)
        ToolbarDockState.LEFT -> RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp)
        ToolbarDockState.RIGHT -> RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)
        ToolbarDockState.FLOATING -> RoundedCornerShape(20.dp)
    }

    val border = when (dockState) {
        ToolbarDockState.FLOATING -> androidx.compose.foundation.BorderStroke(1.dp, primaryColor.copy(alpha = 0.4f))
        ToolbarDockState.BOTTOM,
        ToolbarDockState.TOP,
        ToolbarDockState.LEFT,
        ToolbarDockState.RIGHT -> androidx.compose.foundation.BorderStroke(1.dp, onSurfaceColor.copy(alpha = 0.14f))
    }

    Surface(
        modifier = modifier,
        shape = shape,
        color = surfaceColor,
        tonalElevation = if (dockState == ToolbarDockState.BOTTOM) 6.dp else 8.dp,
        shadowElevation = if (dockState == ToolbarDockState.BOTTOM) 8.dp else 10.dp,
        border = border
    ) {
        if (isVertical) {
            // VERTICAL TOOLBAR STRIP (LEFT & RIGHT)
            Column(
                modifier = Modifier
                    .width(54.dp)
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 6.dp, horizontal = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Surface(
                    color = Color.Transparent,
                    modifier = Modifier.then(dragModifier)
                ) {
                    VerticalDragHandleView()
                }

                Surface(
                    shape = CircleShape,
                    color = Color.Transparent,
                    modifier = Modifier.size(30.dp)
                ) {
                    IconButton(
                        onClick = onCollapse,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Küçült",
                            tint = onSurfaceColor.copy(alpha = 0.65f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // Vertical concise mode switcher ("Metin" <-> "Kalem", long-press opens Pen Packages)
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isPenMode) primaryColor.copy(alpha = 0.18f) else onSurfaceColor.copy(alpha = 0.08f),
                    border = androidx.compose.foundation.BorderStroke(
                        width = 1.dp,
                        color = if (isPenMode) primaryColor else onSurfaceColor.copy(alpha = 0.2f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .combinedClickable(
                            onClick = {
                                if (isPenMode) onSelectTextMode() else onSelectPenMode()
                            },
                            onLongClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onOpenPenPackages()
                            }
                        )
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 4.dp, horizontal = 2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        Icon(
                            imageVector = if (isPenMode) Icons.Default.Edit else Icons.Default.BorderColor,
                            contentDescription = if (isPenMode) "Metin Moduna Geç" else "Kalem Moduna Geç",
                            tint = if (isPenMode) primaryColor else onSurfaceColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = if (isPenMode) "Kalem" else "Metin",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isPenMode) primaryColor else onSurfaceColor
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp), color = onSurfaceColor.copy(alpha = 0.18f))

                if (!isPenMode) {
                    ToolbarColorSymbolButton(
                        contentDescription = "Yazı Rengi",
                        currentColor = selectedColor,
                        isPanelOpen = activeColorPanel == ActiveColorPanel.PEN,
                        isHighlight = false,
                        isVertical = true,
                        onClick = { onToggleColorPanel(ActiveColorPanel.PEN) },
                        primaryColor = primaryColor,
                        onSurfaceColor = onSurfaceColor,
                        surfaceColor = surfaceColor
                    )

                    ToolbarColorSymbolButton(
                        contentDescription = "Vurgu Rengi",
                        currentColor = selectedHighlightColor,
                        isPanelOpen = activeColorPanel == ActiveColorPanel.HIGHLIGHT,
                        isHighlight = true,
                        isVertical = true,
                        onClick = { onToggleColorPanel(ActiveColorPanel.HIGHLIGHT) },
                        primaryColor = primaryColor,
                        onSurfaceColor = onSurfaceColor,
                        surfaceColor = surfaceColor
                    )

                    // Font selector button (vertical)
                    Surface(
                        onClick = { onToggleColorPanel(ActiveColorPanel.FONT) },
                        shape = RoundedCornerShape(8.dp),
                        color = if (activeColorPanel == ActiveColorPanel.FONT) primaryColor.copy(alpha = 0.16f) else Color.Transparent,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (activeColorPanel == ActiveColorPanel.FONT) primaryColor else onSurfaceColor.copy(alpha = 0.22f)
                        ),
                        modifier = Modifier.size(34.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "Aa",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = selectedFontOption.resolveFontFamily(),
                                color = if (activeColorPanel == ActiveColorPanel.FONT) primaryColor else onSurfaceColor
                            )
                        }
                    }

                    // Punto font size button (vertical)
                    Surface(
                        onClick = { onToggleColorPanel(ActiveColorPanel.FONT_SIZE) },
                        shape = RoundedCornerShape(8.dp),
                        color = if (activeColorPanel == ActiveColorPanel.FONT_SIZE) primaryColor.copy(alpha = 0.16f) else Color.Transparent,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (activeColorPanel == ActiveColorPanel.FONT_SIZE) primaryColor else onSurfaceColor.copy(alpha = 0.22f)
                        ),
                        modifier = Modifier.size(34.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "${selectedFontSizeSp.roundToInt()}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (activeColorPanel == ActiveColorPanel.FONT_SIZE) primaryColor else onSurfaceColor
                            )
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp), color = onSurfaceColor.copy(alpha = 0.18f))

                    ToolbarToggleButton(
                        icon = Icons.AutoMirrored.Filled.FormatListBulleted,
                        contentDescription = "Madde İşaretli Liste",
                        isActive = isBulletActive,
                        onClick = onBulletToggled,
                        primaryColor = primaryColor,
                        onSurfaceColor = onSurfaceColor
                    )

                    ToolbarToggleButton(
                        icon = Icons.Default.FormatListNumbered,
                        contentDescription = "Numaralı Liste",
                        isActive = isNumberedActive,
                        onClick = onNumberedToggled,
                        primaryColor = primaryColor,
                        onSurfaceColor = onSurfaceColor
                    )

                    ToolbarToggleButton(
                        icon = if (isCheckboxChecked) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                        contentDescription = "Onay Kutusu",
                        isActive = isCheckboxActive || isCheckboxChecked,
                        onClick = onCheckboxToggled,
                        primaryColor = if (isCheckboxChecked) Color(0xFF10B981) else primaryColor,
                        onSurfaceColor = onSurfaceColor
                    )

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp), color = onSurfaceColor.copy(alpha = 0.18f))

                    ToolbarToggleButton(
                        icon = Icons.Default.FormatBold,
                        contentDescription = "Kalın",
                        isActive = isBoldActive,
                        onClick = onBoldToggled,
                        primaryColor = primaryColor,
                        onSurfaceColor = onSurfaceColor
                    )

                    ToolbarToggleButton(
                        icon = Icons.Default.FormatItalic,
                        contentDescription = "İtalik",
                        isActive = isItalicActive,
                        onClick = onItalicToggled,
                        primaryColor = primaryColor,
                        onSurfaceColor = onSurfaceColor
                    )

                    ToolbarToggleButton(
                        icon = Icons.Default.FormatUnderlined,
                        contentDescription = "Altı Çizili",
                        isActive = isUnderlineActive,
                        onClick = onUnderlineToggled,
                        primaryColor = primaryColor,
                        onSurfaceColor = onSurfaceColor
                    )
                } else {
                    PenVerticalToolButton(
                        isSelected = activeDrawTool.isPenTool || activePenPanel == ActivePenPanel.PEN_PACKAGES,
                        accentColor = if (currentPenTool == DrawingTool.HIGHLIGHTER) Color(0xFFFBC02D) else drawingController.penColor,
                        onSurfaceColor = onSurfaceColor,
                        primaryColor = primaryColor,
                        onClick = {
                            if (activeDrawTool.isPenTool) {
                                onTogglePenPanel(ActivePenPanel.PEN_PACKAGES)
                            } else {
                                onTogglePenPanel(ActivePenPanel.NONE)
                                drawingController.selectActiveOrLastPen()
                            }
                        },
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onOpenPenPackages()
                        }
                    ) { tint -> PenToolGlyph(tool = currentPenTool, tint = tint) }

                    // Unified Eraser (tap selects/toggles; long-press opens Eraser picker)
                    PenVerticalToolButton(
                        isSelected = activeDrawTool.isEraserTool || activePenPanel == ActivePenPanel.ERASER_PACKAGES,
                        accentColor = Color(0xFFEF4444),
                        onSurfaceColor = onSurfaceColor,
                        primaryColor = primaryColor,
                        onClick = {
                            if (activeDrawTool.isEraserTool) {
                                onTogglePenPanel(ActivePenPanel.ERASER_PACKAGES)
                            } else {
                                onTogglePenPanel(ActivePenPanel.NONE)
                                drawingController.selectActiveOrLastEraser()
                            }
                        },
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onOpenEraserPackages()
                        }
                    ) { tint -> PenToolGlyph(tool = currentEraserTool, tint = tint) }

                    PenVerticalToolButton(
                        isSelected = activeDrawTool.isShapeTool || activePenPanel == ActivePenPanel.SHAPES,
                        accentColor = primaryColor,
                        onSurfaceColor = onSurfaceColor,
                        primaryColor = primaryColor,
                        onClick = {
                            if (!activeDrawTool.isShapeTool) {
                                drawingController.selectTool(DrawingTool.SHAPE_RECTANGLE)
                            }
                            onTogglePenPanel(ActivePenPanel.SHAPES)
                        }
                    ) { tint -> PenToolGlyph(tool = DrawingTool.SHAPE_RECTANGLE, tint = tint) }

                    PenVerticalToolButton(
                        isSelected = activeDrawTool == DrawingTool.LASSO,
                        accentColor = Color(0xFF0288D1),
                        onSurfaceColor = onSurfaceColor,
                        primaryColor = primaryColor,
                        onClick = {
                            if (activeDrawTool != DrawingTool.LASSO) {
                                onTogglePenPanel(ActivePenPanel.NONE)
                                drawingController.selectTool(DrawingTool.LASSO)
                            } else {
                                onTogglePenPanel(ActivePenPanel.LASSO)
                            }
                        }
                    ) { tint -> PenToolGlyph(tool = DrawingTool.LASSO, tint = tint) }

                    // Text Box tool button (vertical)
                    PenVerticalToolButton(
                        isSelected = activeDrawTool == DrawingTool.TEXT_BOX,
                        accentColor = primaryColor,
                        onSurfaceColor = onSurfaceColor,
                        primaryColor = primaryColor,
                        onClick = {
                            if (activeDrawTool == DrawingTool.TEXT_BOX) {
                                drawingController.addTextBox()
                            } else {
                                drawingController.selectTool(DrawingTool.TEXT_BOX)
                                onTogglePenPanel(ActivePenPanel.TEXT_BOX)
                            }
                        }
                    ) { tint -> PenToolGlyph(tool = DrawingTool.TEXT_BOX, tint = tint) }

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp), color = onSurfaceColor.copy(alpha = 0.18f))

                    IconButton(
                        onClick = { onTogglePenPanel(ActivePenPanel.SETTINGS) },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (activePenPanel == ActivePenPanel.SETTINGS) Icons.Default.Tune else Icons.Default.Palette,
                                contentDescription = "Kalem Ayarları",
                                tint = if (activePenPanel == ActivePenPanel.SETTINGS) primaryColor else onSurfaceColor,
                                modifier = Modifier.size(18.dp)
                            )
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(drawingController.penColor)
                                    .border(1.dp, surfaceColor, CircleShape)
                            )
                        }
                    }

                    IconButton(
                        onClick = { drawingController.undo() },
                        enabled = drawingController.canUndo,
                        modifier = Modifier.size(32.dp)
                    ) {
                        PenUndoGlyph(
                            tint = if (drawingController.canUndo) onSurfaceColor else onSurfaceColor.copy(alpha = 0.3f)
                        )
                    }

                    IconButton(
                        onClick = { drawingController.redo() },
                        enabled = drawingController.canRedo,
                        modifier = Modifier.size(32.dp)
                    ) {
                        PenRedoGlyph(
                            tint = if (drawingController.canRedo) onSurfaceColor else onSurfaceColor.copy(alpha = 0.3f)
                        )
                    }

                    if (drawingController.hasAnyAnnotations) {
                        IconButton(
                            onClick = { drawingController.clearAll() },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Çizimleri Temizle",
                                tint = colorScheme.error.copy(alpha = 0.8f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        } else {
            // HORIZONTAL TOOLBAR (BOTTOM, TOP, FLOATING)
            Column(modifier = Modifier.fillMaxWidth()) {
                if (dockState == ToolbarDockState.BOTTOM) {
                    HorizontalToolbarSubPanels(
                        isPenMode = isPenMode,
                        drawingController = drawingController,
                        activeColorPanel = activeColorPanel,
                        activePenPanel = activePenPanel,
                        selectedColor = selectedColor,
                        selectedHighlightColor = selectedHighlightColor,
                        selectedFontSizeSp = selectedFontSizeSp,
                        selectedFontOption = selectedFontOption,
                        highlightIntensity = highlightIntensity,
                        onHighlightIntensityChanged = onHighlightIntensityChanged,
                        customColors = customColors,
                        onToggleColorPanel = onToggleColorPanel,
                        onTogglePenPanel = onTogglePenPanel,
                        onColorSelected = onColorSelected,
                        onHighlightSelected = onHighlightSelected,
                        onFontSizeSelected = onFontSizeSelected,
                        onFontOptionSelected = onFontOptionSelected,
                        onOpenCustomColorPicker = onOpenCustomColorPicker,
                        primaryColor = primaryColor,
                        onSurfaceColor = onSurfaceColor,
                        surfaceColor = surfaceColor,
                        colorScheme = colorScheme,
                        expandFromTop = false
                    )
                }

                // Main horizontal toolbar row (compact vertical padding — zero excess bottom whitespace)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    // Pinned Left Cluster: Drag Handle + Collapse + Metin/Kalem Toggle
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Surface(
                            color = Color.Transparent,
                            modifier = Modifier.then(dragModifier)
                        ) {
                            DragHandleView()
                        }

                        Surface(
                            shape = CircleShape,
                            color = Color.Transparent,
                            modifier = Modifier.size(26.dp)
                        ) {
                            IconButton(
                                onClick = onCollapse,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Küçült",
                                    tint = onSurfaceColor.copy(alpha = 0.6f),
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }

                        // Compact segmented switch: Metin | Kalem
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = onSurfaceColor.copy(alpha = 0.06f),
                            border = androidx.compose.foundation.BorderStroke(0.8.dp, onSurfaceColor.copy(alpha = 0.14f))
                        ) {
                            Row(
                                modifier = Modifier.padding(2.dp),
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                ToolbarModeChip(
                                    label = "Metin",
                                    isSelected = !isPenMode,
                                    primaryColor = primaryColor,
                                    onSurfaceColor = onSurfaceColor,
                                    onClick = onSelectTextMode
                                )
                                ToolbarModeChip(
                                    label = "Kalem",
                                    isSelected = isPenMode,
                                    primaryColor = primaryColor,
                                    onSurfaceColor = onSurfaceColor,
                                    onClick = onSelectPenMode,
                                    onLongClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onOpenPenPackages()
                                    }
                                )
                            }
                        }
                    }

                    VerticalDivider(modifier = Modifier.height(22.dp), color = onSurfaceColor.copy(alpha = 0.18f))

                    // Scrollable Tools Area
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        if (!isPenMode) {
                            // Universal "T" Text Color symbol (no "Yazı" text, no color circle — symbol itself syncs to selected color)
                            ToolbarColorSymbolButton(
                                contentDescription = "Yazı Rengi",
                                currentColor = selectedColor,
                                isPanelOpen = activeColorPanel == ActiveColorPanel.PEN,
                                isHighlight = false,
                                onClick = { onToggleColorPanel(ActiveColorPanel.PEN) },
                                primaryColor = primaryColor,
                                onSurfaceColor = onSurfaceColor,
                                surfaceColor = surfaceColor
                            )

                            // Highlight icon (symbol untouched, no "Vurgu" text, synced color tint)
                            ToolbarColorSymbolButton(
                                contentDescription = "Vurgu Rengi",
                                currentColor = selectedHighlightColor,
                                isPanelOpen = activeColorPanel == ActiveColorPanel.HIGHLIGHT,
                                isHighlight = true,
                                onClick = { onToggleColorPanel(ActiveColorPanel.HIGHLIGHT) },
                                primaryColor = primaryColor,
                                onSurfaceColor = onSurfaceColor,
                                surfaceColor = surfaceColor
                            )

                            // Font Family Selector chip
                            Surface(
                                onClick = { onToggleColorPanel(ActiveColorPanel.FONT) },
                                shape = RoundedCornerShape(9.dp),
                                color = if (activeColorPanel == ActiveColorPanel.FONT) primaryColor.copy(alpha = 0.14f) else Color.Transparent,
                                border = androidx.compose.foundation.BorderStroke(
                                    width = if (activeColorPanel == ActiveColorPanel.FONT) 1.5.dp else 1.dp,
                                    color = if (activeColorPanel == ActiveColorPanel.FONT) primaryColor else onSurfaceColor.copy(alpha = 0.22f)
                                ),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                ) {
                                    Text(
                                        text = selectedFontOption.shortLabel,
                                        fontSize = 11.5.sp,
                                        fontFamily = selectedFontOption.resolveFontFamily(),
                                        fontWeight = if (activeColorPanel == ActiveColorPanel.FONT) FontWeight.Bold else FontWeight.SemiBold,
                                        color = if (activeColorPanel == ActiveColorPanel.FONT) primaryColor else onSurfaceColor
                                    )
                                }
                            }

                            // Punto Font Size Selector chip
                            Surface(
                                onClick = { onToggleColorPanel(ActiveColorPanel.FONT_SIZE) },
                                shape = RoundedCornerShape(9.dp),
                                color = if (activeColorPanel == ActiveColorPanel.FONT_SIZE) primaryColor.copy(alpha = 0.14f) else Color.Transparent,
                                border = androidx.compose.foundation.BorderStroke(
                                    width = if (activeColorPanel == ActiveColorPanel.FONT_SIZE) 1.5.dp else 1.dp,
                                    color = if (activeColorPanel == ActiveColorPanel.FONT_SIZE) primaryColor else onSurfaceColor.copy(alpha = 0.22f)
                                ),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                ) {
                                    Text(
                                        text = "${selectedFontSizeSp.roundToInt()}pt",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (activeColorPanel == ActiveColorPanel.FONT_SIZE) primaryColor else onSurfaceColor
                                    )
                                }
                            }

                            VerticalDivider(modifier = Modifier.height(20.dp), color = onSurfaceColor.copy(alpha = 0.16f))

                            ToolbarToggleButton(
                                icon = Icons.AutoMirrored.Filled.FormatListBulleted,
                                contentDescription = "Madde İşaretli Liste",
                                isActive = isBulletActive,
                                onClick = onBulletToggled,
                                primaryColor = primaryColor,
                                onSurfaceColor = onSurfaceColor
                            )

                            ToolbarToggleButton(
                                icon = Icons.Default.FormatListNumbered,
                                contentDescription = "Numaralı Liste",
                                isActive = isNumberedActive,
                                onClick = onNumberedToggled,
                                primaryColor = primaryColor,
                                onSurfaceColor = onSurfaceColor
                            )

                            ToolbarToggleButton(
                                icon = if (isCheckboxChecked) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                                contentDescription = "Onay Kutusu",
                                isActive = isCheckboxActive || isCheckboxChecked,
                                onClick = onCheckboxToggled,
                                primaryColor = if (isCheckboxChecked) Color(0xFF10B981) else primaryColor,
                                onSurfaceColor = onSurfaceColor
                            )

                            VerticalDivider(modifier = Modifier.height(20.dp), color = onSurfaceColor.copy(alpha = 0.16f))

                            ToolbarToggleButton(
                                icon = Icons.Default.FormatBold,
                                contentDescription = "Kalın",
                                isActive = isBoldActive,
                                onClick = onBoldToggled,
                                primaryColor = primaryColor,
                                onSurfaceColor = onSurfaceColor
                            )

                            ToolbarToggleButton(
                                icon = Icons.Default.FormatItalic,
                                contentDescription = "İtalik",
                                isActive = isItalicActive,
                                onClick = onItalicToggled,
                                primaryColor = primaryColor,
                                onSurfaceColor = onSurfaceColor
                            )

                            ToolbarToggleButton(
                                icon = Icons.Default.FormatUnderlined,
                                contentDescription = "Altı Çizili",
                                isActive = isUnderlineActive,
                                onClick = onUnderlineToggled,
                                primaryColor = primaryColor,
                                onSurfaceColor = onSurfaceColor
                            )
                        } else {
                            // Icon-only dynamic Pen button (no redundant text label; long-press opens Pen Packages)
                            StudioToolButton(
                                label = null,
                                isSelected = activeDrawTool.isPenTool || activePenPanel == ActivePenPanel.PEN_PACKAGES,
                                accentColor = if (currentPenTool == DrawingTool.HIGHLIGHTER) Color(0xFFFBC02D) else drawingController.penColor,
                                onClick = {
                                    if (activeDrawTool.isPenTool) {
                                        onTogglePenPanel(ActivePenPanel.PEN_PACKAGES)
                                    } else {
                                        onTogglePenPanel(ActivePenPanel.NONE)
                                        drawingController.selectActiveOrLastPen()
                                    }
                                },
                                onLongClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onOpenPenPackages()
                                }
                            ) { tint -> PenToolGlyph(tool = currentPenTool, tint = tint) }

                            // Unified Icon-only Eraser button (tap selects/toggles; long-press opens Stroke/Area Eraser picker)
                            StudioToolButton(
                                label = null,
                                isSelected = activeDrawTool.isEraserTool || activePenPanel == ActivePenPanel.ERASER_PACKAGES,
                                accentColor = Color(0xFFEF4444),
                                onClick = {
                                    if (activeDrawTool.isEraserTool) {
                                        onTogglePenPanel(ActivePenPanel.ERASER_PACKAGES)
                                    } else {
                                        onTogglePenPanel(ActivePenPanel.NONE)
                                        drawingController.selectActiveOrLastEraser()
                                    }
                                },
                                onLongClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onOpenEraserPackages()
                                }
                            ) { tint -> PenToolGlyph(tool = currentEraserTool, tint = tint) }

                            StudioToolButton(
                                label = "Şekil",
                                isSelected = activeDrawTool.isShapeTool || activePenPanel == ActivePenPanel.SHAPES,
                                accentColor = primaryColor,
                                onClick = {
                                    onTogglePenPanel(ActivePenPanel.SHAPES)
                                    if (!activeDrawTool.isShapeTool) {
                                        drawingController.selectTool(DrawingTool.SHAPE_RECTANGLE)
                                    }
                                }
                            ) { tint -> PenToolGlyph(tool = DrawingTool.SHAPE_RECTANGLE, tint = tint) }

                            StudioToolButton(
                                label = "Kement",
                                isSelected = activeDrawTool == DrawingTool.LASSO,
                                accentColor = Color(0xFF0288D1),
                                onClick = {
                                    onTogglePenPanel(ActivePenPanel.NONE)
                                    drawingController.selectTool(DrawingTool.LASSO)
                                }
                            ) { tint -> PenToolGlyph(tool = DrawingTool.LASSO, tint = tint) }

                            // Text Box tool button (rich-text boxes inside drawing mode)
                            StudioToolButton(
                                label = null,
                                isSelected = activeDrawTool == DrawingTool.TEXT_BOX,
                                accentColor = primaryColor,
                                onClick = {
                                    if (activeDrawTool == DrawingTool.TEXT_BOX) {
                                        drawingController.addTextBox()
                                    } else {
                                        drawingController.selectTool(DrawingTool.TEXT_BOX)
                                        onTogglePenPanel(ActivePenPanel.TEXT_BOX)
                                    }
                                }
                            ) { tint -> PenToolGlyph(tool = DrawingTool.TEXT_BOX, tint = tint) }

                            VerticalDivider(modifier = Modifier.height(20.dp), color = onSurfaceColor.copy(alpha = 0.16f))

                            IconButton(
                                onClick = { onTogglePenPanel(ActivePenPanel.SETTINGS) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (activePenPanel == ActivePenPanel.SETTINGS) Icons.Default.Tune else Icons.Default.Palette,
                                        contentDescription = "Kalem Ayarları",
                                        tint = if (activePenPanel == ActivePenPanel.SETTINGS) primaryColor else onSurfaceColor,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(drawingController.penColor)
                                            .border(1.dp, surfaceColor, CircleShape)
                                    )
                                }
                            }

                            IconButton(
                                onClick = { drawingController.undo() },
                                enabled = drawingController.canUndo,
                                modifier = Modifier.size(30.dp)
                            ) {
                                PenUndoGlyph(
                                    tint = if (drawingController.canUndo) onSurfaceColor else onSurfaceColor.copy(alpha = 0.3f)
                                )
                            }

                            IconButton(
                                onClick = { drawingController.redo() },
                                enabled = drawingController.canRedo,
                                modifier = Modifier.size(30.dp)
                            ) {
                                PenRedoGlyph(
                                    tint = if (drawingController.canRedo) onSurfaceColor else onSurfaceColor.copy(alpha = 0.3f)
                                )
                            }

                            if (drawingController.hasAnyAnnotations) {
                                IconButton(
                                    onClick = { drawingController.clearAll() },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Çizimleri Temizle",
                                        tint = colorScheme.error.copy(alpha = 0.8f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                if (dockState == ToolbarDockState.TOP || dockState == ToolbarDockState.FLOATING) {
                    HorizontalToolbarSubPanels(
                        isPenMode = isPenMode,
                        drawingController = drawingController,
                        activeColorPanel = activeColorPanel,
                        activePenPanel = activePenPanel,
                        selectedColor = selectedColor,
                        selectedHighlightColor = selectedHighlightColor,
                        selectedFontSizeSp = selectedFontSizeSp,
                        selectedFontOption = selectedFontOption,
                        highlightIntensity = highlightIntensity,
                        onHighlightIntensityChanged = onHighlightIntensityChanged,
                        customColors = customColors,
                        onToggleColorPanel = onToggleColorPanel,
                        onTogglePenPanel = onTogglePenPanel,
                        onColorSelected = onColorSelected,
                        onHighlightSelected = onHighlightSelected,
                        onFontSizeSelected = onFontSizeSelected,
                        onFontOptionSelected = onFontOptionSelected,
                        onOpenCustomColorPicker = onOpenCustomColorPicker,
                        primaryColor = primaryColor,
                        onSurfaceColor = onSurfaceColor,
                        surfaceColor = surfaceColor,
                        colorScheme = colorScheme,
                        expandFromTop = true
                    )
                }
            }
        }
    }
}

@Composable
private fun HorizontalToolbarSubPanels(
    isPenMode: Boolean,
    drawingController: DrawingController,
    activeColorPanel: ActiveColorPanel,
    activePenPanel: ActivePenPanel,
    selectedColor: Color?,
    selectedHighlightColor: Color?,
    selectedFontSizeSp: Float,
    selectedFontOption: NoteFontOption,
    highlightIntensity: Float,
    onHighlightIntensityChanged: (Float) -> Unit,
    customColors: List<Color>,
    onToggleColorPanel: (ActiveColorPanel) -> Unit,
    onTogglePenPanel: (ActivePenPanel) -> Unit,
    onColorSelected: (Color) -> Unit,
    onHighlightSelected: (Color?) -> Unit,
    onFontSizeSelected: (Float) -> Unit,
    onFontOptionSelected: (NoteFontOption) -> Unit,
    onOpenCustomColorPicker: (isHighlight: Boolean) -> Unit,
    primaryColor: Color,
    onSurfaceColor: Color,
    surfaceColor: Color,
    colorScheme: androidx.compose.material3.ColorScheme,
    expandFromTop: Boolean
) {
    val expandOrigin = if (expandFromTop) Alignment.Top else Alignment.Bottom
    if (!isPenMode) {
        if (expandFromTop && activeColorPanel != ActiveColorPanel.NONE) {
            HorizontalDivider(color = onSurfaceColor.copy(alpha = 0.12f))
        }
        AnimatedVisibility(
            visible = activeColorPanel != ActiveColorPanel.NONE,
            enter = expandVertically(expandFrom = expandOrigin) + fadeIn(),
            exit = shrinkVertically(shrinkTowards = expandOrigin) + fadeOut()
        ) {
            FormattingColorPanel(
                activeColorPanel = activeColorPanel,
                selectedColor = selectedColor,
                selectedHighlightColor = selectedHighlightColor,
                selectedFontSizeSp = selectedFontSizeSp,
                selectedFontOption = selectedFontOption,
                highlightIntensity = highlightIntensity,
                onHighlightIntensityChanged = onHighlightIntensityChanged,
                customColors = customColors,
                onToggleColorPanel = onToggleColorPanel,
                onColorSelected = onColorSelected,
                onHighlightSelected = onHighlightSelected,
                onFontSizeSelected = onFontSizeSelected,
                onFontOptionSelected = onFontOptionSelected,
                onOpenCustomColorPicker = onOpenCustomColorPicker,
                primaryColor = primaryColor,
                onSurfaceColor = onSurfaceColor,
                surfaceColor = surfaceColor,
                colorScheme = colorScheme
            )
        }
        if (!expandFromTop && activeColorPanel != ActiveColorPanel.NONE) {
            HorizontalDivider(color = onSurfaceColor.copy(alpha = 0.12f))
        }
    } else {
        val showPenPackages = activePenPanel == ActivePenPanel.PEN_PACKAGES
        val showEraserPackages = activePenPanel == ActivePenPanel.ERASER_PACKAGES
        val showShapes = activePenPanel == ActivePenPanel.SHAPES || drawingController.activeTool.isShapeTool
        val showLasso = drawingController.activeTool == DrawingTool.LASSO
        val showTextBoxPanel = drawingController.activeTool == DrawingTool.TEXT_BOX || activePenPanel == ActivePenPanel.TEXT_BOX
        val showSettings = activePenPanel == ActivePenPanel.SETTINGS

        AnimatedVisibility(
            visible = showPenPackages,
            enter = expandVertically(expandFrom = expandOrigin) + fadeIn(),
            exit = shrinkVertically(shrinkTowards = expandOrigin) + fadeOut()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (expandFromTop) HorizontalDivider(color = onSurfaceColor.copy(alpha = 0.10f))
                PenPackagePickerPanel(
                    controller = drawingController,
                    onDismiss = { onTogglePenPanel(ActivePenPanel.NONE) }
                )
                if (!expandFromTop) HorizontalDivider(color = onSurfaceColor.copy(alpha = 0.10f))
            }
        }

        AnimatedVisibility(
            visible = showEraserPackages,
            enter = expandVertically(expandFrom = expandOrigin) + fadeIn(),
            exit = shrinkVertically(shrinkTowards = expandOrigin) + fadeOut()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (expandFromTop) HorizontalDivider(color = onSurfaceColor.copy(alpha = 0.10f))
                EraserPackagePickerPanel(
                    controller = drawingController,
                    onDismiss = { onTogglePenPanel(ActivePenPanel.NONE) }
                )
                if (!expandFromTop) HorizontalDivider(color = onSurfaceColor.copy(alpha = 0.10f))
            }
        }

        AnimatedVisibility(
            visible = showShapes,
            enter = expandVertically(expandFrom = expandOrigin) + fadeIn(),
            exit = shrinkVertically(shrinkTowards = expandOrigin) + fadeOut()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (expandFromTop) HorizontalDivider(color = onSurfaceColor.copy(alpha = 0.10f))
                PenShapePickerPanel(controller = drawingController)
                if (!expandFromTop) HorizontalDivider(color = onSurfaceColor.copy(alpha = 0.10f))
            }
        }

        AnimatedVisibility(
            visible = showLasso,
            enter = expandVertically(expandFrom = expandOrigin) + fadeIn(),
            exit = shrinkVertically(shrinkTowards = expandOrigin) + fadeOut()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (expandFromTop) HorizontalDivider(color = onSurfaceColor.copy(alpha = 0.10f))
                PenLassoActionsPanel(controller = drawingController)
                if (!expandFromTop) HorizontalDivider(color = onSurfaceColor.copy(alpha = 0.10f))
            }
        }

        AnimatedVisibility(
            visible = showTextBoxPanel,
            enter = expandVertically(expandFrom = expandOrigin) + fadeIn(),
            exit = shrinkVertically(shrinkTowards = expandOrigin) + fadeOut()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (expandFromTop) HorizontalDivider(color = onSurfaceColor.copy(alpha = 0.10f))
                DrawingTextBoxFormattingPanel(controller = drawingController)
                if (!expandFromTop) HorizontalDivider(color = onSurfaceColor.copy(alpha = 0.10f))
            }
        }

        AnimatedVisibility(
            visible = showSettings,
            enter = expandVertically(expandFrom = expandOrigin) + fadeIn(),
            exit = shrinkVertically(shrinkTowards = expandOrigin) + fadeOut()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (expandFromTop) HorizontalDivider(color = onSurfaceColor.copy(alpha = 0.10f))
                PenStrokeSettingsPanel(
                    controller = drawingController,
                    onDismiss = { onTogglePenPanel(ActivePenPanel.NONE) }
                )
                if (!expandFromTop) HorizontalDivider(color = onSurfaceColor.copy(alpha = 0.10f))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ToolbarModeChip(
    label: String,
    isSelected: Boolean,
    primaryColor: Color,
    onSurfaceColor: Color,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) primaryColor else Color.Transparent,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else onSurfaceColor.copy(alpha = 0.8f),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PenVerticalToolButton(
    isSelected: Boolean,
    accentColor: Color,
    onSurfaceColor: Color,
    primaryColor: Color,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    iconContent: @Composable (tint: Color) -> Unit
) {
    val surfaceColor = MaterialTheme.colorScheme.surface
    val effectiveAccent = if (kotlin.math.abs(accentColor.luminance() - surfaceColor.luminance()) < 0.22f) {
        primaryColor
    } else {
        accentColor
    }
    val bgColor = if (isSelected) effectiveAccent.copy(alpha = 0.18f) else Color.Transparent
    val borderColor = if (isSelected) effectiveAccent else Color.Transparent
    val contentTint = if (isSelected) effectiveAccent else onSurfaceColor.copy(alpha = 0.8f)

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = bgColor,
        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, borderColor) else null,
        modifier = Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(8.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            iconContent(contentTint)
            if (onLongClick != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(3.dp)
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(contentTint.copy(alpha = 0.7f))
                )
            }
        }
    }
}

@Composable
private fun FormattingColorPanel(
    activeColorPanel: ActiveColorPanel,
    selectedColor: Color?,
    selectedHighlightColor: Color?,
    selectedFontSizeSp: Float = NoteRichTextHelper.DEFAULT_FONT_SIZE_SP,
    selectedFontOption: NoteFontOption = NoteFontOption.DEFAULT,
    highlightIntensity: Float,
    onHighlightIntensityChanged: (Float) -> Unit,
    customColors: List<Color>,
    onToggleColorPanel: (ActiveColorPanel) -> Unit,
    onColorSelected: (Color) -> Unit,
    onHighlightSelected: (Color?) -> Unit,
    onFontSizeSelected: (Float) -> Unit = {},
    onFontOptionSelected: (NoteFontOption) -> Unit = {},
    onOpenCustomColorPicker: (isHighlight: Boolean) -> Unit,
    primaryColor: Color,
    onSurfaceColor: Color,
    surfaceColor: Color,
    colorScheme: androidx.compose.material3.ColorScheme
) {
    Surface(
        color = colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = when (activeColorPanel) {
                        ActiveColorPanel.PEN -> "Yazı Rengi"
                        ActiveColorPanel.HIGHLIGHT -> "Vurgu Rengi"
                        ActiveColorPanel.FONT -> "Yazı Tipi"
                        ActiveColorPanel.FONT_SIZE -> "Punto Boyutu"
                        ActiveColorPanel.NONE -> ""
                    },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = onSurfaceColor
                )
                IconButton(
                    onClick = { onToggleColorPanel(ActiveColorPanel.NONE) },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Paneli Kapat",
                        tint = onSurfaceColor.copy(alpha = 0.6f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (activeColorPanel == ActiveColorPanel.PEN) {
                    // 1. Her temanın kendine özel 3 dinamik rengi
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = primaryColor.copy(alpha = 0.12f),
                        border = androidx.compose.foundation.BorderStroke(0.5.dp, primaryColor.copy(alpha = 0.35f)),
                        modifier = Modifier.padding(end = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Text(
                                text = "Tema:",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = primaryColor
                            )
                            listOf(colorScheme.primary, colorScheme.secondary, colorScheme.tertiary).forEach { themeColor ->
                                val isSelected = selectedColor == themeColor
                                ColorCircleSwatch(
                                    color = themeColor,
                                    isSelected = isSelected,
                                    primaryColor = primaryColor,
                                    borderColor = onSurfaceColor.copy(alpha = 0.3f),
                                    onClick = { onColorSelected(themeColor) }
                                )
                            }
                        }
                    }

                    VerticalDivider(modifier = Modifier.height(20.dp), color = onSurfaceColor.copy(alpha = 0.2f))

                    // 2. Ana Yazı Renkleri
                    val isDefaultSelected = selectedColor == null || selectedColor == onSurfaceColor
                    ColorCircleSwatch(
                        color = onSurfaceColor,
                        isSelected = isDefaultSelected,
                        primaryColor = primaryColor,
                        checkTint = surfaceColor,
                        borderColor = onSurfaceColor.copy(alpha = 0.3f),
                        onClick = { onColorSelected(onSurfaceColor) }
                    )

                    NoteRichTextHelper.TextColorPalette.forEach { (_, color) ->
                        val isSelected = selectedColor == color
                        ColorCircleSwatch(
                            color = color,
                            isSelected = isSelected,
                            primaryColor = primaryColor,
                            checkTint = if (color.luminance() > 0.6f) Color(0xFF0F172A) else Color.White,
                            borderColor = onSurfaceColor.copy(alpha = 0.3f),
                            onClick = { onColorSelected(color) }
                        )
                    }

                    // Custom Colors if any
                    if (customColors.isNotEmpty()) {
                        VerticalDivider(modifier = Modifier.height(20.dp), color = onSurfaceColor.copy(alpha = 0.2f))
                        customColors.forEach { cColor ->
                            val isSelected = selectedColor == cColor
                            ColorCircleSwatch(
                                color = cColor,
                                isSelected = isSelected,
                                primaryColor = primaryColor,
                                checkTint = if (cColor.luminance() > 0.6f) Color(0xFF0F172A) else Color.White,
                                borderColor = onSurfaceColor.copy(alpha = 0.3f),
                                onClick = { onColorSelected(cColor) }
                            )
                        }
                    }

                    VerticalDivider(modifier = Modifier.height(20.dp), color = onSurfaceColor.copy(alpha = 0.2f))

                    // 3. Renk Oluşturma Sistemi Butonu
                    OutlinedButton(
                        onClick = { onOpenCustomColorPicker(false) },
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Palette,
                            contentDescription = "Özel Renk Oluştur",
                            tint = primaryColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "+ Özel",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = primaryColor
                        )
                    }
                } else if (activeColorPanel == ActiveColorPanel.HIGHLIGHT) {
                    // Vurguyu kaldır (Sıfırla)
                    val isNoneSelected = selectedHighlightColor == null
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(26.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (isNoneSelected) primaryColor.copy(alpha = 0.15f) else Color.Transparent
                            )
                            .border(
                                width = if (isNoneSelected) 1.5.dp else 1.dp,
                                color = if (isNoneSelected) primaryColor else onSurfaceColor.copy(alpha = 0.35f),
                                shape = RoundedCornerShape(6.dp)
                            )
                            .clickable { onHighlightSelected(null) }
                    ) {
                        Icon(
                            imageVector = Icons.Default.FormatColorReset,
                            contentDescription = "Vurguyu Kaldır",
                            tint = if (isNoneSelected) primaryColor else onSurfaceColor.copy(alpha = 0.6f),
                            modifier = Modifier.size(15.dp)
                        )
                    }

                    VerticalDivider(modifier = Modifier.height(20.dp), color = onSurfaceColor.copy(alpha = 0.2f))

                    // 1. Her temanın 3 dinamik vurgu rengi
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = primaryColor.copy(alpha = 0.12f),
                        border = androidx.compose.foundation.BorderStroke(0.5.dp, primaryColor.copy(alpha = 0.35f)),
                        modifier = Modifier.padding(end = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Text(
                                text = "Tema:",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = primaryColor
                            )
                            listOf(
                                colorScheme.primary,
                                colorScheme.secondary,
                                colorScheme.tertiary
                            ).forEach { themeBase ->
                                val themeHighlight = themeBase.copy(alpha = highlightIntensity)
                                val isSelected = selectedHighlightColor != null &&
                                    selectedHighlightColor.red == themeBase.red &&
                                    selectedHighlightColor.green == themeBase.green &&
                                    selectedHighlightColor.blue == themeBase.blue
                                HighlightBoxSwatch(
                                    color = themeHighlight,
                                    isSelected = isSelected,
                                    primaryColor = primaryColor,
                                    borderColor = onSurfaceColor.copy(alpha = 0.3f),
                                    onClick = {
                                        if (isSelected) onHighlightSelected(null)
                                        else onHighlightSelected(themeHighlight)
                                    }
                                )
                            }
                        }
                    }

                    VerticalDivider(modifier = Modifier.height(20.dp), color = onSurfaceColor.copy(alpha = 0.2f))

                    // 2. Ana Vurgu Renkleri
                    NoteRichTextHelper.HighlightPalette.forEach { option ->
                        val effectiveColor = option.color.copy(alpha = highlightIntensity)
                        val isSelected = selectedHighlightColor != null &&
                            selectedHighlightColor.red == option.color.red &&
                            selectedHighlightColor.green == option.color.green &&
                            selectedHighlightColor.blue == option.color.blue
                        HighlightBoxSwatch(
                            color = effectiveColor,
                            isSelected = isSelected,
                            primaryColor = primaryColor,
                            borderColor = onSurfaceColor.copy(alpha = 0.3f),
                            onClick = {
                                if (isSelected) onHighlightSelected(null)
                                else onHighlightSelected(effectiveColor)
                            }
                        )
                    }

                    // Custom Highlight Colors
                    if (customColors.isNotEmpty()) {
                        VerticalDivider(modifier = Modifier.height(20.dp), color = onSurfaceColor.copy(alpha = 0.2f))
                        customColors.forEach { cColor ->
                            val hl = cColor.copy(alpha = highlightIntensity)
                            val isSelected = selectedHighlightColor != null &&
                                selectedHighlightColor.red == cColor.red &&
                                selectedHighlightColor.green == cColor.green &&
                                selectedHighlightColor.blue == cColor.blue
                            HighlightBoxSwatch(
                                color = hl,
                                isSelected = isSelected,
                                primaryColor = primaryColor,
                                borderColor = onSurfaceColor.copy(alpha = 0.3f),
                                onClick = {
                                    if (isSelected) onHighlightSelected(null)
                                    else onHighlightSelected(hl)
                                }
                            )
                        }
                    }

                    VerticalDivider(modifier = Modifier.height(20.dp), color = onSurfaceColor.copy(alpha = 0.2f))

                    // 3. Özel Vurgu Oluşturma Butonu
                    OutlinedButton(
                        onClick = { onOpenCustomColorPicker(true) },
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Palette,
                            contentDescription = "Özel Vurgu Oluştur",
                            tint = primaryColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "+ Özel",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = primaryColor
                        )
                    }
                } else if (activeColorPanel == ActiveColorPanel.FONT) {
                    NoteFontOption.entries.forEach { fontOpt ->
                        val isSelected = selectedFontOption == fontOpt
                        Surface(
                            onClick = { onFontOptionSelected(fontOpt) },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) primaryColor.copy(alpha = 0.16f) else surfaceColor,
                            border = androidx.compose.foundation.BorderStroke(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) primaryColor else onSurfaceColor.copy(alpha = 0.2f)
                            )
                        ) {
                            Text(
                                text = fontOpt.displayName,
                                fontSize = 12.5.sp,
                                fontFamily = fontOpt.resolveFontFamily(),
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) primaryColor else onSurfaceColor,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                } else if (activeColorPanel == ActiveColorPanel.FONT_SIZE) {
                    Surface(
                        onClick = { onFontSizeSelected((selectedFontSizeSp - 2f).coerceAtLeast(NoteRichTextHelper.MIN_FONT_SIZE_SP)) },
                        shape = RoundedCornerShape(8.dp),
                        color = surfaceColor,
                        border = androidx.compose.foundation.BorderStroke(1.dp, onSurfaceColor.copy(alpha = 0.2f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Remove,
                            contentDescription = "Küçült",
                            tint = onSurfaceColor,
                            modifier = Modifier.padding(6.dp).size(15.dp)
                        )
                    }

                    NoteRichTextHelper.FontSizePresets.forEach { pt ->
                        val isSelected = selectedFontSizeSp.roundToInt() == pt
                        Surface(
                            onClick = { onFontSizeSelected(pt.toFloat()) },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) primaryColor.copy(alpha = 0.16f) else surfaceColor,
                            border = androidx.compose.foundation.BorderStroke(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) primaryColor else onSurfaceColor.copy(alpha = 0.2f)
                            )
                        ) {
                            Text(
                                text = "$pt",
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) primaryColor else onSurfaceColor,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }

                    Surface(
                        onClick = { onFontSizeSelected((selectedFontSizeSp + 2f).coerceAtMost(NoteRichTextHelper.MAX_FONT_SIZE_SP)) },
                        shape = RoundedCornerShape(8.dp),
                        color = surfaceColor,
                        border = androidx.compose.foundation.BorderStroke(1.dp, onSurfaceColor.copy(alpha = 0.2f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Büyüt",
                            tint = onSurfaceColor,
                            modifier = Modifier.padding(6.dp).size(15.dp)
                        )
                    }
                }
            }

            // Vurgu Sertliği (Intensity / Opacity) Ayarı
            if (activeColorPanel == ActiveColorPanel.HIGHLIGHT) {
                HorizontalDivider(color = onSurfaceColor.copy(alpha = 0.08f), modifier = Modifier.padding(vertical = 2.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Sertlik:",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = onSurfaceColor.copy(alpha = 0.8f)
                    )

                    listOf(
                        "Hafif" to 0.30f,
                        "Dengeli" to 0.60f,
                        "Belirgin" to 0.85f
                    ).forEach { (label, presetVal) ->
                        val isSelected = kotlin.math.abs(highlightIntensity - presetVal) < 0.08f
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) primaryColor.copy(alpha = 0.16f) else surfaceColor,
                            border = androidx.compose.foundation.BorderStroke(
                                width = if (isSelected) 1.5.dp else 0.5.dp,
                                color = if (isSelected) primaryColor else onSurfaceColor.copy(alpha = 0.25f)
                            ),
                            modifier = Modifier.clickable {
                                onHighlightIntensityChanged(presetVal)
                            }
                        ) {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) primaryColor else onSurfaceColor.copy(alpha = 0.85f),
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Slider(
                        value = highlightIntensity,
                        onValueChange = onHighlightIntensityChanged,
                        valueRange = 0.20f..0.95f,
                        modifier = Modifier
                            .weight(1f)
                            .height(26.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = primaryColor,
                            activeTrackColor = primaryColor
                        )
                    )

                    Text(
                        text = "%${(highlightIntensity * 100).toInt()}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = primaryColor
                    )
                }
            }
        }
    }
}

@Composable
private fun ToolbarColorSymbolButton(
    contentDescription: String,
    currentColor: Color?,
    isHighlight: Boolean = false,
    isPanelOpen: Boolean,
    isVertical: Boolean = false,
    onClick: () -> Unit,
    primaryColor: Color,
    onSurfaceColor: Color,
    surfaceColor: Color = MaterialTheme.colorScheme.surface
) {
    val activeColorSolid = currentColor?.takeIf { it != Color.Transparent }?.copy(alpha = 1f)
    val lowContrast = activeColorSolid != null &&
        kotlin.math.abs(activeColorSolid.luminance() - surfaceColor.luminance()) < 0.18f

    val activeBorder = when {
        isPanelOpen -> primaryColor
        activeColorSolid != null -> activeColorSolid.copy(alpha = 0.65f)
        else -> onSurfaceColor.copy(alpha = 0.22f)
    }
    val activeBg = when {
        isPanelOpen -> primaryColor.copy(alpha = 0.14f)
        lowContrast -> onSurfaceColor.copy(alpha = 0.14f)
        isHighlight && activeColorSolid != null -> activeColorSolid.copy(alpha = 0.16f)
        else -> Color.Transparent
    }

    Surface(
        shape = RoundedCornerShape(9.dp),
        color = activeBg,
        border = androidx.compose.foundation.BorderStroke(if (isPanelOpen) 1.5.dp else 1.dp, activeBorder),
        modifier = (if (isVertical) Modifier.size(34.dp) else Modifier.size(width = 34.dp, height = 32.dp))
            .clickable(onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            if (!isHighlight) {
                // Universal typographic "T" symbol synchronized directly to selected text color
                Text(
                    text = "T",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Serif,
                    color = activeColorSolid ?: onSurfaceColor
                )
            } else {
                // Highlight icon kept untouched, synchronized directly to selected highlight color
                Icon(
                    imageVector = Icons.Default.BorderColor,
                    contentDescription = contentDescription,
                    tint = activeColorSolid ?: onSurfaceColor.copy(alpha = 0.85f),
                    modifier = Modifier.size(17.dp)
                )
            }
        }
    }
}

@Composable
private fun ColorCircleSwatch(
    color: Color,
    isSelected: Boolean,
    primaryColor: Color,
    checkTint: Color = Color.White,
    borderColor: Color,
    onClick: () -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(color)
            .border(
                width = if (isSelected) 2.5.dp else 1.dp,
                color = if (isSelected) primaryColor else borderColor,
                shape = CircleShape
            )
            .clickable(onClick = onClick)
    ) {
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = checkTint,
                modifier = Modifier.size(13.dp)
            )
        }
    }
}

@Composable
private fun HighlightBoxSwatch(
    color: Color,
    isSelected: Boolean,
    primaryColor: Color,
    borderColor: Color,
    onClick: () -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(24.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(color)
            .border(
                width = if (isSelected) 2.5.dp else 1.dp,
                color = if (isSelected) primaryColor else borderColor,
                shape = RoundedCornerShape(6.dp)
            )
            .clickable(onClick = onClick)
    ) {
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = Color(0xFF212121),
                modifier = Modifier.size(13.dp)
            )
        }
    }
}

@Composable
private fun ToolbarToggleButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    isActive: Boolean,
    onClick: () -> Unit,
    primaryColor: Color,
    onSurfaceColor: Color
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isActive) primaryColor.copy(alpha = 0.18f) else Color.Transparent,
        border = if (isActive) androidx.compose.foundation.BorderStroke(1.dp, primaryColor.copy(alpha = 0.5f)) else null,
        modifier = Modifier.size(34.dp)
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier.fillMaxSize()
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (isActive) primaryColor else onSurfaceColor.copy(alpha = 0.75f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
fun CustomColorPickerDialog(
    initialColor: Color,
    isHighlightMode: Boolean = false,
    highlightIntensity: Float = 0.60f,
    onDismissRequest: () -> Unit,
    onColorSelected: (Color) -> Unit
) {
    PenCustomColorDialog(
        initialColor = initialColor,
        onDismiss = onDismissRequest,
        onColorSelected = onColorSelected,
        title = if (isHighlightMode) "Özel Vurgu Rengi Oluştur" else "Özel Renk Oluştur",
        confirmLabel = "Bu Rengi Seç",
        isHighlightMode = isHighlightMode,
        highlightIntensity = highlightIntensity
    )
}

@Composable
fun PageStyleDialog(
    currentPattern: PaperPattern,
    currentTintHex: String,
    onPatternSelected: (PaperPattern) -> Unit,
    onTintSelected: (String) -> Unit,
    onDismissRequest: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(
                text = "Sayfa Düzeni",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. Kağıt Deseni
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Kağıt Deseni",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        PaperPattern.entries.forEach { pattern ->
                            val isSelected = pattern == currentPattern
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                border = androidx.compose.foundation.BorderStroke(
                                    width = if (isSelected) 1.5.dp else 0.5.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { onPatternSelected(pattern) }
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 2.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = pattern.title,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))

                // 2. Kağıt Rengi
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Kağıt Rengi",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PaperTint.Options.forEach { tint ->
                            val isSelected = currentTintHex.equals(tint.hex, ignoreCase = true)
                            val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
                            val displayColor = if (tint.hex.equals("#FFFFFF", ignoreCase = true)) {
                                Color.White
                            } else if (tint.hex.equals("#F8FAFC", ignoreCase = true)) {
                                if (isDark) tint.darkColor else tint.lightColor
                            } else {
                                tint.lightColor
                            }

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.clickable { onTintSelected(tint.hex) }
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(displayColor)
                                        .border(
                                            width = if (isSelected) 2.5.dp else 1.dp,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                                            shape = CircleShape
                                        )
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = if (displayColor.luminance() > 0.5f) MaterialTheme.colorScheme.primary else Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = tint.title,
                                    fontSize = 10.sp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Tamam")
            }
        }
    )
}

@Composable
fun NoteStatsDialog(
    words: Int,
    chars: Int,
    lines: Int,
    spentSeconds: Long = 0L,
    onDismissRequest: () -> Unit
) {
    val readMinutes = if (words == 0) 0 else (words / 200).coerceAtLeast(1)
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(
                text = "Not İstatistikleri",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatRow(label = "Kelime Sayısı", value = "$words")
                StatRow(label = "Karakter Sayısı", value = "$chars")
                StatRow(label = "Satır Sayısı", value = "$lines")
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                StatRow(
                    label = "Notta Geçirilen Süre",
                    value = NoteTimeTracker.formatDuration(spentSeconds)
                )
                StatRow(
                    label = "Tahmini Okuma Süresi",
                    value = if (readMinutes == 0) "< 1 dk" else "~$readMinutes dk"
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Kapat")
            }
        }
    )
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun resolveFileDisplayName(resolver: ContentResolver, uri: Uri): String {
    if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
        runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx != -1 && cursor.moveToFirst()) {
                    val name = cursor.getString(idx)
                    if (!name.isNullOrBlank()) return name
                }
            }
        }
    }
    return uri.lastPathSegment ?: "İçe Aktarılan Not"
}
