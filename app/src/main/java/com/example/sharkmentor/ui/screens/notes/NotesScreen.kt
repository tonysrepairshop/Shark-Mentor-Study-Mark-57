package com.example.sharkmentor.ui.screens.notes

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.sharkmentor.ImportedNotePayload
import com.example.sharkmentor.core.util.ImportedPdfDocument
import com.example.sharkmentor.core.util.PdfLibraryManager
import com.example.sharkmentor.data.local.SharkMentorDatabase
import com.example.sharkmentor.data.local.entity.NoteEntity
import com.example.sharkmentor.data.repository.NoteRepositoryImpl
import com.example.sharkmentor.data.sync.VaultBackupDialog
import com.example.sharkmentor.ui.screens.notes.drawing.DrawingSerializer
import com.example.sharkmentor.ui.screens.pdf.PdfReaderActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private enum class NotesLibraryTab {
    NOTES,
    PDFS,
    CARDS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesScreen(
    onBackClick: (() -> Unit)? = null,
    onOpenDrawer: (() -> Unit)? = null,
    showTopBar: Boolean = true,
    importedNote: ImportedNotePayload? = null,
    onImportedNoteConsumed: () -> Unit = {},
    onEditorStateChanged: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val colorScheme = MaterialTheme.colorScheme
    val bgColor = colorScheme.background
    val cardColor = colorScheme.surface
    val textColor = colorScheme.onSurface
    val textMutedColor = colorScheme.onSurface.copy(alpha = 0.6f)
    val primaryColor = colorScheme.primary

    val viewModel: NotesViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val db = SharkMentorDatabase.getDatabase(context)
                val repo = NoteRepositoryImpl(db.noteDao(), context)
                return NotesViewModel(repo) as T
            }
        }
    )

    val notes by viewModel.notes.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val pdfDocuments by PdfLibraryManager.documentsFlow.collectAsStateWithLifecycle()
    LaunchedEffect(context) {
        com.example.sharkmentor.core.simulation.SimulationLabEngine.ensureLoaded(context)
    }
    val savedSimulations by com.example.sharkmentor.core.simulation.SimulationLabEngine.savedSimulations.collectAsStateWithLifecycle()
    val filteredSimulations = remember(savedSimulations, searchQuery) {
        val q = searchQuery.trim()
        if (q.isBlank()) savedSimulations
        else savedSimulations.filter {
            it.title.contains(q, ignoreCase = true) ||
                it.subject.contains(q, ignoreCase = true) ||
                "simülasyon".contains(q, ignoreCase = true)
        }
    }
    var simToRename by remember { mutableStateOf<com.example.sharkmentor.core.simulation.InteractiveSimulation?>(null) }
    var simRenameText by remember { mutableStateOf("") }
    var simToDelete by remember { mutableStateOf<com.example.sharkmentor.core.simulation.InteractiveSimulation?>(null) }

    var activeTabName by rememberSaveable { mutableStateOf(NotesLibraryTab.NOTES.name) }
    val activeTab = remember(activeTabName) {
        runCatching { NotesLibraryTab.valueOf(activeTabName) }.getOrDefault(NotesLibraryTab.NOTES)
    }

    var selectedNoteId by remember { mutableStateOf<String?>(null) }
    var noteToDelete by remember { mutableStateOf<NoteEntity?>(null) }
    var pdfToDelete by remember { mutableStateOf<ImportedPdfDocument?>(null) }
    var pdfToRename by remember { mutableStateOf<ImportedPdfDocument?>(null) }
    var renameText by remember { mutableStateOf("") }
    var isImportingPdf by remember { mutableStateOf(false) }
    var sharingPdfId by remember { mutableStateOf<String?>(null) }

    var editorInitialTitle by remember { mutableStateOf("") }
    var editorInitialContent by remember { mutableStateOf("") }
    var editorIncomingUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var editorPdfPreview by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var editorPdfPagePaths by remember { mutableStateOf<List<String>>(emptyList()) }
    var showEditor by remember { mutableStateOf(false) }
    var editorSessionKey by remember { mutableIntStateOf(0) }
    var showVaultBackupDialog by remember { mutableStateOf(false) }

    // Ensure PDF library is loaded & refreshed whenever returning from PdfReaderActivity
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            PdfLibraryManager.ensureLoaded(context)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                coroutineScope.launch(Dispatchers.IO) {
                    PdfLibraryManager.refresh(context)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Automatically migrate any legacy `[PDF_FILE:...]` stub notes into `PDF'lerim`
    LaunchedEffect(notes) {
        val legacyStubs = notes.filter { isLegacyPdfStubNote(it) }
        if (legacyStubs.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                legacyStubs.forEach { stub ->
                    val path = extractPdfFilePathFromNote(stub.content)
                    if (!path.isNullOrBlank()) {
                        PdfLibraryManager.migrateLegacyPdfPath(
                            context = context,
                            localPdfPath = path,
                            title = stub.title,
                            isPinned = stub.isPinned
                        )
                    }
                }
            }
        }
    }

    fun openEditorSession(
        noteId: String? = null,
        initialTitle: String = "",
        initialContent: String = "",
        incomingUri: android.net.Uri? = null,
        pdfPreview: android.graphics.Bitmap? = null,
        pdfPagePaths: List<String> = emptyList()
    ) {
        selectedNoteId = noteId
        editorInitialTitle = initialTitle
        editorInitialContent = initialContent
        editorIncomingUri = incomingUri
        editorPdfPreview = pdfPreview
        editorPdfPagePaths = pdfPagePaths
        editorSessionKey++
        showEditor = true
    }

    fun closeEditorSession() {
        showEditor = false
        selectedNoteId = null
        editorInitialTitle = ""
        editorInitialContent = ""
        editorIncomingUri = null
        editorPdfPreview = null
        editorPdfPagePaths = emptyList()
    }

    fun openImportedPdf(doc: ImportedPdfDocument) {
        val file = File(doc.localFilePath)
        if (!file.exists() || file.length() <= 0L) {
            Toast.makeText(context, "PDF dosyası bulunamadı", Toast.LENGTH_SHORT).show()
            coroutineScope.launch(Dispatchers.IO) { PdfLibraryManager.refresh(context) }
            return
        }
        context.startActivity(
            PdfReaderActivity.createIntent(
                context = context,
                pdfFilePath = doc.localFilePath,
                title = doc.title,
                skipChooser = true
            )
        )
    }

    fun shareImportedPdf(doc: ImportedPdfDocument) {
        if (sharingPdfId != null) return
        sharingPdfId = doc.id
        coroutineScope.launch {
            val exportFile = withContext(Dispatchers.IO) {
                PdfLibraryManager.exportAnnotatedPdfToCache(
                    context = context,
                    pdfFile = File(doc.localFilePath),
                    title = doc.title
                )
            }
            sharingPdfId = null
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
                Toast.makeText(context, "PDF dosyası bulunamadı", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            isImportingPdf = true
            coroutineScope.launch {
                val importedDoc = withContext(Dispatchers.IO) {
                    PdfLibraryManager.importPdfFromUri(context, uri)
                }
                isImportingPdf = false
                if (importedDoc != null) {
                    activeTabName = NotesLibraryTab.PDFS.name
                    Toast.makeText(
                        context,
                        "PDF içe aktarıldı ve PDF'lerim'e kaydedildi",
                        Toast.LENGTH_SHORT
                    ).show()
                    openImportedPdf(importedDoc)
                } else {
                    Toast.makeText(context, "PDF dosyası içe aktarılamadı.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    LaunchedEffect(showEditor) {
        onEditorStateChanged(showEditor)
    }

    DisposableEffect(Unit) {
        onDispose {
            onEditorStateChanged(false)
        }
    }

    LaunchedEffect(importedNote) {
        if (importedNote != null) {
            openEditorSession(
                noteId = null,
                initialTitle = importedNote.title,
                initialContent = importedNote.content,
                incomingUri = importedNote.sourceUri,
                pdfPreview = importedNote.pdfPreviewBitmap,
                pdfPagePaths = importedNote.pdfPageImagePaths
            )
            onImportedNoteConsumed()
        }
    }

    if (showEditor) {
        key(editorSessionKey) {
            val detailViewModel: NoteDetailViewModel = viewModel(
                key = "note_detail_$editorSessionKey",
                factory = NoteDetailViewModel.Factory(context)
            )
            NoteDetailScreen(
                noteId = selectedNoteId,
                initialTitle = editorInitialTitle,
                initialContent = editorInitialContent,
                incomingFileUri = editorIncomingUri,
                initialPdfPreview = editorPdfPreview,
                initialPdfPagePaths = editorPdfPagePaths,
                viewModel = detailViewModel,
                onOpenDrawer = onOpenDrawer,
                onDismiss = { closeEditorSession() },
                onSave = { _, _, _, _ ->
                    closeEditorSession()
                }
            )
        }
    } else {
        val openNoteOrPdf: (NoteEntity) -> Unit = { note ->
            val linkedPdfPath = extractPdfFilePathFromNote(note.content)
            val linkedFile = linkedPdfPath?.let { File(it) }
            if (linkedFile != null && linkedFile.exists() && linkedFile.length() > 0L) {
                context.startActivity(
                    PdfReaderActivity.createIntent(
                        context = context,
                        pdfFilePath = linkedFile.absolutePath,
                        title = note.title.ifBlank { linkedFile.nameWithoutExtension },
                        skipChooser = true
                    )
                )
            } else {
                openEditorSession(
                    noteId = note.id,
                    initialTitle = "",
                    initialContent = "",
                    incomingUri = null,
                    pdfPreview = null,
                    pdfPagePaths = emptyList()
                )
            }
        }

        val visibleNotes = remember(notes) {
            notes.filterNot { isLegacyPdfStubNote(it) }
        }

        val filteredPdfs = remember(pdfDocuments, searchQuery) {
            val q = searchQuery.trim()
            if (q.isBlank()) {
                pdfDocuments
            } else {
                pdfDocuments.filter {
                    it.title.contains(q, ignoreCase = true) ||
                        it.displayName.contains(q, ignoreCase = true)
                }
            }
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(bgColor)
        ) {
            val screenWidth = maxWidth
            val gridColumns = when {
                screenWidth < 600.dp -> StaggeredGridCells.Fixed(2)
                screenWidth < 840.dp -> StaggeredGridCells.Fixed(3)
                screenWidth < 1150.dp -> StaggeredGridCells.Fixed(4)
                screenWidth < 1450.dp -> StaggeredGridCells.Fixed(5)
                else -> StaggeredGridCells.Fixed(6)
            }

            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (showTopBar) {
                    CenterAlignedTopAppBar(
                        expandedHeight = 50.dp,
                        title = { Text("Notlar", fontWeight = FontWeight.Bold, color = textColor) },
                        navigationIcon = {
                            if (onBackClick != null) {
                                IconButton(onClick = onBackClick) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri", tint = textColor)
                                }
                            }
                        },
                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                            containerColor = bgColor
                        )
                    )
                }

                // ── Notlarım | PDF'lerim | Kartlar Sub-Tab Switcher ──
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp, bottom = 2.dp, start = 16.dp, end = 16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NotesLibraryTabButton(
                        label = "Notlarım",
                        isSelected = activeTab == NotesLibraryTab.NOTES,
                        primaryColor = primaryColor,
                        mutedColor = textMutedColor,
                        onClick = { activeTabName = NotesLibraryTab.NOTES.name }
                    )
                    Text(
                        text = "|",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Light,
                        color = textMutedColor.copy(alpha = 0.45f),
                        modifier = Modifier.padding(horizontal = 14.dp)
                    )
                    NotesLibraryTabButton(
                        label = "PDF'lerim",
                        isSelected = activeTab == NotesLibraryTab.PDFS,
                        primaryColor = primaryColor,
                        mutedColor = textMutedColor,
                        onClick = { activeTabName = NotesLibraryTab.PDFS.name }
                    )
                    Text(
                        text = "|",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Light,
                        color = textMutedColor.copy(alpha = 0.45f),
                        modifier = Modifier.padding(horizontal = 14.dp)
                    )
                    NotesLibraryTabButton(
                        label = "Kartlar",
                        isSelected = activeTab == NotesLibraryTab.CARDS,
                        primaryColor = primaryColor,
                        mutedColor = textMutedColor,
                        onClick = { activeTabName = NotesLibraryTab.CARDS.name }
                    )
                }

                // ── Search Bar (only on Notes & PDFs) ──
                if (activeTab != NotesLibraryTab.CARDS) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { viewModel.updateSearchQuery(it) },
                            placeholder = {
                                Text(
                                    text = if (activeTab == NotesLibraryTab.NOTES) "Notlarda ara..." else "PDF'lerde ara...",
                                    color = textMutedColor
                                )
                            },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = textMutedColor) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 720.dp),
                            shape = RoundedCornerShape(24.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = primaryColor,
                                unfocusedBorderColor = colorScheme.outline.copy(alpha = 0.45f),
                                focusedTextColor = textColor,
                                unfocusedTextColor = textColor,
                                cursorColor = primaryColor
                            )
                        )
                    }
                }

                if (activeTab == NotesLibraryTab.NOTES) {
                    val pinnedNotes = visibleNotes.filter { it.isPinned }
                    val unpinnedNotes = visibleNotes.filter { !it.isPinned }
                    val isListEmpty = pinnedNotes.isEmpty() && unpinnedNotes.isEmpty() && filteredSimulations.isEmpty()

                    if (isListEmpty) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 32.dp, vertical = 48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = if (searchQuery.isNotBlank()) Icons.Default.Search else Icons.Outlined.PushPin,
                                    contentDescription = null,
                                    tint = textMutedColor.copy(alpha = 0.4f),
                                    modifier = Modifier.size(56.dp)
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                Text(
                                    text = if (searchQuery.isNotBlank()) "Eşleşen not bulunamadı" else "Henüz not yok",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = textColor
                                )
                            }
                        }
                    } else {
                        LazyVerticalStaggeredGrid(
                            columns = gridColumns,
                            contentPadding = PaddingValues(
                                start = if (screenWidth >= 840.dp) 24.dp else 16.dp,
                                end = if (screenWidth >= 840.dp) 24.dp else 16.dp,
                                top = 8.dp,
                                bottom = 88.dp
                            ),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalItemSpacing = 14.dp,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            if (pinnedNotes.isNotEmpty()) {
                                item(span = StaggeredGridItemSpan.FullLine) {
                                    Text(
                                        "Sabitlenenler (${pinnedNotes.size})",
                                        fontWeight = FontWeight.Bold,
                                        color = textColor,
                                        modifier = Modifier.padding(bottom = 8.dp, start = 8.dp)
                                    )
                                }
                                items(pinnedNotes, key = { it.id }) { note ->
                                    NoteCardItem(
                                        note = note,
                                        cardColor = cardColor,
                                        textColor = textColor,
                                        textMutedColor = textMutedColor,
                                        onClick = { openNoteOrPdf(note) },
                                        onDelete = { noteToDelete = note },
                                        onTogglePin = { viewModel.togglePin(note) }
                                    )
                                }
                            }
                            if (unpinnedNotes.isNotEmpty()) {
                                if (pinnedNotes.isNotEmpty()) {
                                    item(span = StaggeredGridItemSpan.FullLine) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            "Notlar",
                                            fontWeight = FontWeight.Bold,
                                            color = textColor,
                                            modifier = Modifier.padding(bottom = 8.dp, start = 8.dp)
                                        )
                                    }
                                }
                                items(unpinnedNotes, key = { it.id }) { note ->
                                    NoteCardItem(
                                        note = note,
                                        cardColor = cardColor,
                                        textColor = textColor,
                                        textMutedColor = textMutedColor,
                                        onClick = { openNoteOrPdf(note) },
                                        onDelete = { noteToDelete = note },
                                        onTogglePin = { viewModel.togglePin(note) }
                                    )
                                }
                            }
                            if (filteredSimulations.isNotEmpty()) {
                                items(filteredSimulations, key = { "sim_${it.id}" }) { sim ->
                                    Surface(
                                        onClick = {
                                            com.example.sharkmentor.core.simulation.SimulationLabEngine.openSimulationPreview(sim)
                                        },
                                        shape = RoundedCornerShape(16.dp),
                                        color = cardColor,
                                        border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.35f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(14.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = primaryColor.copy(alpha = 0.14f)
                                                ) {
                                                    Text(
                                                        text = "SİMÜLASYON",
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = primaryColor,
                                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                                    )
                                                }
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    IconButton(
                                                        onClick = {
                                                            coroutineScope.launch {
                                                                val res = com.example.sharkmentor.core.simulation.SimulationLabEngine
                                                                    .toggleCloudBackup(context, sim.id)
                                                                res.onSuccess { backedUp ->
                                                                    Toast.makeText(
                                                                        context,
                                                                        if (backedUp) "Buluta yedeklendi" else "Bulut yedeğinden çıkarıldı",
                                                                        Toast.LENGTH_SHORT
                                                                    ).show()
                                                                }.onFailure { err ->
                                                                    Toast.makeText(
                                                                        context,
                                                                        err.message ?: "Bulut yedekleme başarısız",
                                                                        Toast.LENGTH_LONG
                                                                    ).show()
                                                                }
                                                            }
                                                        },
                                                        modifier = Modifier.size(26.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = if (sim.isCloudSynced) Icons.Default.CloudDone else Icons.Default.CloudUpload,
                                                            contentDescription = if (sim.isCloudSynced) "Bulutta Yedekli" else "Buluta Yedekle",
                                                            tint = if (sim.isCloudSynced) primaryColor else textMutedColor,
                                                            modifier = Modifier.size(15.dp)
                                                        )
                                                    }
                                                    IconButton(
                                                        onClick = {
                                                            simToRename = sim
                                                            simRenameText = sim.title
                                                        },
                                                        modifier = Modifier.size(26.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Edit,
                                                            contentDescription = "Yeniden Adlandır",
                                                            tint = textMutedColor,
                                                            modifier = Modifier.size(15.dp)
                                                        )
                                                    }
                                                    IconButton(
                                                        onClick = { simToDelete = sim },
                                                        modifier = Modifier.size(26.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Delete,
                                                            contentDescription = "Sil",
                                                            tint = textMutedColor,
                                                            modifier = Modifier.size(15.dp)
                                                        )
                                                    }
                                                }
                                            }
                                            Text(
                                                text = sim.title,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = textColor,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = if (sim.subject.isNotBlank()) "${sim.subject} • Simülasyon (BETA)" else "İnteraktif Simülasyon (BETA)",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = textMutedColor,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else if (activeTab == NotesLibraryTab.PDFS) {
                    // ── PDF'lerim Tab (Identical staggered grid & card aesthetic) ──
                    val pinnedPdfs = filteredPdfs.filter { it.isPinned }
                    val unpinnedPdfs = filteredPdfs.filter { !it.isPinned }
                    val isPdfListEmpty = pinnedPdfs.isEmpty() && unpinnedPdfs.isEmpty()

                    if (isPdfListEmpty) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 32.dp, vertical = 48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = if (searchQuery.isNotBlank()) Icons.Default.Search else Icons.Default.PictureAsPdf,
                                    contentDescription = null,
                                    tint = textMutedColor.copy(alpha = 0.42f),
                                    modifier = Modifier.size(56.dp)
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                Text(
                                    text = if (searchQuery.isNotBlank()) "Eşleşen PDF bulunamadı" else "Henüz içe aktarılmış PDF yok",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = textColor,
                                    textAlign = TextAlign.Center
                                )
                                if (searchQuery.isBlank()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Sağ alttaki butondan PDF içe aktar. Orijinal dosyan korunur, tüm çizimlerin güvenle saklanır.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = textMutedColor,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    } else {
                        LazyVerticalStaggeredGrid(
                            columns = gridColumns,
                            contentPadding = PaddingValues(
                                start = if (screenWidth >= 840.dp) 24.dp else 16.dp,
                                end = if (screenWidth >= 840.dp) 24.dp else 16.dp,
                                top = 8.dp,
                                bottom = 88.dp
                            ),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalItemSpacing = 14.dp,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            if (pinnedPdfs.isNotEmpty()) {
                                item(span = StaggeredGridItemSpan.FullLine) {
                                    Text(
                                        "Sabitlenenler (${pinnedPdfs.size})",
                                        fontWeight = FontWeight.Bold,
                                        color = textColor,
                                        modifier = Modifier.padding(bottom = 8.dp, start = 8.dp)
                                    )
                                }
                                items(pinnedPdfs, key = { it.id }) { doc ->
                                    PdfCardItem(
                                        doc = doc,
                                        cardColor = cardColor,
                                        textColor = textColor,
                                        textMutedColor = textMutedColor,
                                        isSharing = sharingPdfId == doc.id,
                                        onClick = { openImportedPdf(doc) },
                                        onTogglePin = {
                                            coroutineScope.launch(Dispatchers.IO) {
                                                PdfLibraryManager.togglePin(context, doc.id)
                                            }
                                        },
                                        onRename = {
                                            renameText = doc.title
                                            pdfToRename = doc
                                        },
                                        onShare = { shareImportedPdf(doc) },
                                        onDelete = { pdfToDelete = doc }
                                    )
                                }
                            }
                            if (unpinnedPdfs.isNotEmpty()) {
                                if (pinnedPdfs.isNotEmpty()) {
                                    item(span = StaggeredGridItemSpan.FullLine) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            "PDF'lerim",
                                            fontWeight = FontWeight.Bold,
                                            color = textColor,
                                            modifier = Modifier.padding(bottom = 8.dp, start = 8.dp)
                                        )
                                    }
                                }
                                items(unpinnedPdfs, key = { it.id }) { doc ->
                                    PdfCardItem(
                                        doc = doc,
                                        cardColor = cardColor,
                                        textColor = textColor,
                                        textMutedColor = textMutedColor,
                                        isSharing = sharingPdfId == doc.id,
                                        onClick = { openImportedPdf(doc) },
                                        onTogglePin = {
                                            coroutineScope.launch(Dispatchers.IO) {
                                                PdfLibraryManager.togglePin(context, doc.id)
                                            }
                                        },
                                        onRename = {
                                            renameText = doc.title
                                            pdfToRename = doc
                                        },
                                        onShare = { shareImportedPdf(doc) },
                                        onDelete = { pdfToDelete = doc }
                                    )
                                }
                            }
                        }
                    }
                } else {
                    SmartFlashcardStudioView(
                        availableNotes = visibleNotes,
                        availablePdfs = pdfDocuments,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )
                }
            }

            // ── Single Contextual FAB: New Note on Notlarım, Import/Open PDF on PDF'lerim ──
            if (activeTab != NotesLibraryTab.CARDS) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(24.dp)
                ) {
                    if (activeTab == NotesLibraryTab.NOTES) {
                        FloatingActionButton(
                            onClick = { openEditorSession() },
                            containerColor = primaryColor,
                            contentColor = colorScheme.onPrimary
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Yeni Not")
                        }
                    } else if (activeTab == NotesLibraryTab.PDFS) {
                        FloatingActionButton(
                            onClick = {
                                if (!isImportingPdf) {
                                    pdfPickerLauncher.launch(arrayOf("application/pdf"))
                                }
                            },
                            containerColor = primaryColor,
                            contentColor = colorScheme.onPrimary
                        ) {
                            if (isImportingPdf) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp),
                                    strokeWidth = 2.4.dp,
                                    color = colorScheme.onPrimary
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.PictureAsPdf,
                                    contentDescription = "PDF İçe Aktar / Aç"
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (noteToDelete != null) {
        AlertDialog(
            onDismissRequest = { noteToDelete = null },
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
                        noteToDelete?.id?.let { idStr ->
                            viewModel.deleteNote(idStr)
                            if (idStr.isNotBlank()) {
                                DrawingSerializer.deleteNoteDrawing(context, idStr)
                            }
                        }
                        noteToDelete = null
                    }
                ) {
                    Text(
                        "Sil",
                        color = colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { noteToDelete = null }) {
                    Text("İptal")
                }
            }
        )
    }

    pdfToDelete?.let { doc ->
        AlertDialog(
            onDismissRequest = { pdfToDelete = null },
            title = {
                Text(
                    text = "PDF'i Kütüphaneden Kaldır",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text("\"${doc.title}\" ve üzerindeki çizimler uygulama kütüphanesinden silinecek. Cihazındaki orijinal PDF dosyasına dokunulmaz.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val targetId = doc.id
                        pdfToDelete = null
                        coroutineScope.launch(Dispatchers.IO) {
                            PdfLibraryManager.deleteDocument(context, targetId)
                        }
                    }
                ) {
                    Text(
                        "Sil",
                        color = colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { pdfToDelete = null }) {
                    Text("İptal")
                }
            }
        )
    }

    simToRename?.let { targetSim ->
        AlertDialog(
            onDismissRequest = { simToRename = null },
            title = { Text("Simülasyonu Yeniden Adlandır", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = simRenameText,
                    onValueChange = { simRenameText = it },
                    singleLine = true,
                    label = { Text("Simülasyon Adı") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        com.example.sharkmentor.core.simulation.SimulationLabEngine
                            .renameSavedSimulation(context, targetSim.id, simRenameText)
                        simToRename = null
                    }
                ) { Text("Kaydet", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { simToRename = null }) { Text("İptal") }
            }
        )
    }

    simToDelete?.let { targetSim ->
        AlertDialog(
            onDismissRequest = { simToDelete = null },
            title = { Text("Simülasyonu Sil", fontWeight = FontWeight.Bold) },
            text = { Text("\"${targetSim.title}\" notlardan kaldırılsın mı?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        com.example.sharkmentor.core.simulation.SimulationLabEngine
                            .deleteSavedSimulation(context, targetSim.id)
                        simToDelete = null
                    }
                ) { Text("Sil", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { simToDelete = null }) { Text("Vazgeç") }
            }
        )
    }

    pdfToRename?.let { doc ->
        AlertDialog(
            onDismissRequest = { pdfToRename = null },
            title = {
                Text(
                    text = "PDF Adını Düzenle",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    label = { Text("Başlık") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val newTitle = renameText.trim()
                        val targetId = doc.id
                        pdfToRename = null
                        if (newTitle.isNotBlank()) {
                            coroutineScope.launch(Dispatchers.IO) {
                                PdfLibraryManager.renameDocument(context, targetId, newTitle)
                            }
                        }
                    }
                ) {
                    Text("Kaydet", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { pdfToRename = null }) {
                    Text("İptal")
                }
            }
        )
    }

    if (showVaultBackupDialog) {
        VaultBackupDialog(
            onDismiss = { showVaultBackupDialog = false }
        )
    }
}

@Composable
private fun NotesLibraryTabButton(
    label: String,
    isSelected: Boolean,
    primaryColor: Color,
    mutedColor: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 6.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            fontSize = 15.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) primaryColor else mutedColor
        )
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .width(if (isSelected) 32.dp else 0.dp)
                .height(2.5.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(if (isSelected) primaryColor else Color.Transparent)
        )
    }
}

@Composable
private fun PdfCardItem(
    doc: ImportedPdfDocument,
    cardColor: Color,
    textColor: Color,
    textMutedColor: Color,
    isSharing: Boolean,
    onClick: () -> Unit,
    onTogglePin: () -> Unit,
    onRename: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val primaryColor = colorScheme.primary
    val primaryContainer = colorScheme.primaryContainer
    val onPrimaryContainer = colorScheme.onPrimaryContainer
    val sizeLabel = remember(doc.fileSizeBytes) { PdfLibraryManager.formatFileSize(doc.fileSizeBytes) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        border = if (doc.isPinned) BorderStroke(1.5.dp, Color(0xFFF59E0B).copy(alpha = 0.75f)) else null
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = doc.title.ifBlank { "PDF Belgesi" },
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = textColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onTogglePin,
                    modifier = Modifier
                        .size(30.dp)
                        .padding(start = 4.dp)
                ) {
                    if (doc.isPinned) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFFF59E0B).copy(alpha = 0.22f),
                            border = BorderStroke(1.dp, Color(0xFFF59E0B)),
                            modifier = Modifier.size(26.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.PushPin,
                                    contentDescription = "Sabitlendi",
                                    tint = Color(0xFFF59E0B),
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.PushPin,
                            contentDescription = "Sabitle",
                            tint = textMutedColor.copy(alpha = 0.45f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            if (!doc.thumbnailPath.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color.White,
                    border = BorderStroke(0.6.dp, colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 96.dp, max = 165.dp)
                ) {
                    AsyncImage(
                        model = File(doc.thumbnailPath),
                        contentDescription = doc.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(0.75f)
                            .clip(RoundedCornerShape(10.dp))
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            val pagesText = if (doc.pageCount > 0) {
                if (doc.lastReadPage > 0) "S. ${doc.lastReadPage + 1}/${doc.pageCount}" else "${doc.pageCount} sayfa"
            } else {
                "PDF"
            }
            val metaLine = if (sizeLabel.isNotBlank()) "$pagesText • $sizeLabel" else pagesText
            Text(
                text = metaLine,
                fontSize = 12.sp,
                color = textMutedColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (doc.annotatedPageCount > 0) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Draw,
                        contentDescription = null,
                        tint = primaryColor,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = "${doc.annotatedPageCount} çizimli sayfa",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = primaryColor,
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = primaryContainer.copy(alpha = 0.7f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PictureAsPdf,
                            contentDescription = null,
                            tint = onPrimaryContainer,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = "PDF",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = onPrimaryContainer
                        )
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                IconButton(
                    onClick = onRename,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Yeniden Adlandır",
                        tint = textMutedColor,
                        modifier = Modifier.size(17.dp)
                    )
                }
                IconButton(
                    onClick = onShare,
                    enabled = !isSharing,
                    modifier = Modifier.size(28.dp)
                ) {
                    if (isSharing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 1.8.dp,
                            color = primaryColor
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Paylaş",
                            tint = textMutedColor,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Sil",
                        tint = textMutedColor,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
        }
    }
}

private val NOTES_PDF_FILE_TAG_REGEX = Regex("\\[PDF_FILE:([^\\]]+)\\]")
private val NOTES_ALL_PDF_TAGS_REGEX = Regex("""\[PDF_(?:PAGE|FILE):[^\]]+\]\r?\n?""")
private val LEGACY_PAGE_COUNT_ONLY_REGEX = Regex("""^\d+\s*sayfa$""", RegexOption.IGNORE_CASE)

private fun extractPdfFilePathFromNote(rawContent: String): String? {
    return NOTES_PDF_FILE_TAG_REGEX.find(rawContent)
        ?.groupValues
        ?.getOrNull(1)
        ?.trim()
        ?.takeIf { it.isNotBlank() }
}

private fun isLegacyPdfStubNote(note: NoteEntity): Boolean {
    if (extractPdfFilePathFromNote(note.content) == null) return false
    val stripped = note.content.replace(NOTES_ALL_PDF_TAGS_REGEX, "").trim()
    return stripped.isBlank() || LEGACY_PAGE_COUNT_ONLY_REGEX.matches(stripped)
}

@Composable
fun NoteCardItem(
    note: NoteEntity,
    cardColor: Color,
    textColor: Color,
    textMutedColor: Color,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onTogglePin: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val accentColor = remember(note.colorHex, textColor, cardColor) {
        try {
            val tintHex = note.colorHex.substringBefore("|").trim()
            if (tintHex.isBlank() || tintHex.equals("#F8FAFC", ignoreCase = true) || tintHex.equals("#FFFFFF", ignoreCase = true)) {
                textColor
            } else {
                val parsed = Color(android.graphics.Color.parseColor(tintHex))
                if (kotlin.math.abs(parsed.luminance() - cardColor.luminance()) > 0.35f) {
                    parsed
                } else {
                    textColor
                }
            }
        } catch (_: Exception) {
            textColor
        }
    }

    val primaryColor = androidx.compose.material3.MaterialTheme.colorScheme.primary
    val primaryContainer = androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer
    val onPrimaryContainer = androidx.compose.material3.MaterialTheme.colorScheme.onPrimaryContainer
    val isPdfLinked = remember(note.content) { extractPdfFilePathFromNote(note.content) != null }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        border = if (note.isPinned) androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFFF59E0B).copy(alpha = 0.75f)) else null
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = note.title.ifBlank { "Başlıksız Not" },
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = accentColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onTogglePin,
                    modifier = Modifier
                        .size(30.dp)
                        .padding(start = 4.dp)
                ) {
                    if (note.isPinned) {
                        androidx.compose.material3.Surface(
                            shape = CircleShape,
                            color = Color(0xFFF59E0B).copy(alpha = 0.22f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF59E0B)),
                            modifier = Modifier.size(26.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.PushPin,
                                    contentDescription = "Sabitlendi",
                                    tint = Color(0xFFF59E0B),
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.PushPin,
                            contentDescription = "Sabitle",
                            tint = textMutedColor.copy(alpha = 0.45f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            val displayPreview = remember(note.content) {
                val withoutTags = note.content.replace(NOTES_ALL_PDF_TAGS_REGEX, "").trim()
                NoteRichTextHelper.toPlainText(withoutTags)
                    .lineSequence()
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .take(6)
                    .joinToString("\n")
                    .take(600)
                    .trim()
            }

            if (displayPreview.isNotBlank()) {
                Text(
                    text = displayPreview,
                    fontSize = 14.sp,
                    color = textColor,
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 20.sp
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isPdfLinked) {
                    androidx.compose.material3.Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = primaryContainer.copy(alpha = 0.7f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PictureAsPdf,
                                contentDescription = null,
                                tint = onPrimaryContainer,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = "PDF",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = onPrimaryContainer
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                IconButton(
                    onClick = {
                        val cleanShareText = note.content.replace(NOTES_ALL_PDF_TAGS_REGEX, "").trim()
                        val shareIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TITLE, note.title)
                            putExtra(Intent.EXTRA_TEXT, "${note.title}\n\n${NoteRichTextHelper.toPlainText(cleanShareText)}")
                            type = "text/plain"
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Notu Paylaş"))
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Paylaş",
                        tint = textMutedColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(
                    onClick = {
                        val cleanCopyText = note.content.replace(NOTES_ALL_PDF_TAGS_REGEX, "").trim()
                        clipboardManager.setText(AnnotatedString(NoteRichTextHelper.toPlainText(cleanCopyText)))
                        Toast.makeText(context, "Kopyalandı", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Kopyala",
                        tint = textMutedColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Sil",
                        tint = textMutedColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
