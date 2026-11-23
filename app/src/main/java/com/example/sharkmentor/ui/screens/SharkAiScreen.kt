package com.example.sharkmentor.ui.screens

import android.content.ContentResolver
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Brush
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.History
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.sharkmentor.core.ai.UserAiPreferences
import com.example.sharkmentor.data.repository.SharkAiEngine
import com.example.sharkmentor.ui.aichat.ActionChip
import com.example.sharkmentor.ui.aichat.AiPersona
import com.example.sharkmentor.ui.aichat.PersonalizedGreetingHeader
import com.example.sharkmentor.ui.aichat.getFlashCardsForPersona
import com.example.sharkmentor.ui.aichat.isPersonaAvailable
import com.example.sharkmentor.ui.components.MathSymbolCatalog
import com.example.sharkmentor.ui.components.MathSymbolKeyboardPanel
import com.example.sharkmentor.ui.components.MathSymbolToggleButton
import com.example.sharkmentor.ui.components.PendingAiPromptBus
import com.example.sharkmentor.ui.components.PulsingDotsIndicator
import com.example.sharkmentor.ui.components.RichChatText
import com.example.sharkmentor.ui.components.RichSolutionContent
import com.example.sharkmentor.ui.viewmodel.SharkAiHandoffManager
import com.example.sharkmentor.ui.viewmodel.SharkAiViewModel
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class SelectedAttachmentPreview(
    val uri: Uri,
    val displayName: String,
    val mimeType: String?,
    val thumbnail: ImageBitmap?
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SharkAiScreen(
    modifier: Modifier = Modifier,
    viewModel: SharkAiViewModel = viewModel(),
    onNavigateToPlans: (String) -> Unit = {}
) {
    val context = LocalContext.current
    remember { UserAiPreferences.initialize(context) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val chatSessions by viewModel.chatSessions.collectAsStateWithLifecycle()
    val activeChatSessionId by viewModel.activeChatSessionId.collectAsStateWithLifecycle()
    val inputText by viewModel.inputText.collectAsStateWithLifecycle()
    val selectedAttachmentUri by viewModel.selectedAttachmentUri.collectAsStateWithLifecycle()
    val attachmentPreview by viewModel.attachmentPreview.collectAsStateWithLifecycle()
    val lockedEngine by viewModel.lockedEngine.collectAsStateWithLifecycle()
    val hasAnyCustomApiKey by viewModel.hasAnyCustomApiKey.collectAsStateWithLifecycle()
    val customProviderGroups by viewModel.customProviderGroups.collectAsStateWithLifecycle()
    val activeModelDisplay by viewModel.activeModelDisplay.collectAsStateWithLifecycle()
    val isDiscoveringInlineModels by viewModel.isDiscoveringInlineModels.collectAsStateWithLifecycle()
    val pendingHandoff by SharkAiHandoffManager.pendingHandoff.collectAsStateWithLifecycle()
    val pendingDossierAction by viewModel.pendingDossierAction.collectAsStateWithLifecycle()
    val pendingPlanRebuildRequest by viewModel.pendingPlanRebuildRequest.collectAsStateWithLifecycle()
    val openInterviewRequested by com.example.sharkmentor.core.coaching.StudentDossierNavBus.openInterviewRequested.collectAsStateWithLifecycle()
    val studentDossier by com.example.sharkmentor.core.coaching.StudentDossierManager.dossier.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        com.example.sharkmentor.core.coaching.StudentDossierManager.ensureLoaded(context)
    }

    LaunchedEffect(pendingHandoff) {
        if (pendingHandoff != null) {
            viewModel.consumePendingHandoffIfAny()
        }
    }

    val pendingPrompt by PendingAiPromptBus.pendingPrompt.collectAsStateWithLifecycle()

    LaunchedEffect(pendingPrompt) {
        val prompt = pendingPrompt
        if (!prompt.isNullOrBlank()) {
            viewModel.onInputTextChanged(prompt)
            PendingAiPromptBus.pendingPrompt.value = null
        }
    }

    var showCustomModelSheet by remember { mutableStateOf(false) }
    var showAttachmentSheet by remember { mutableStateOf(false) }
    var showChatHistorySheet by rememberSaveable { mutableStateOf(false) }
    var showMathSymbolPanel by rememberSaveable { mutableStateOf(false) }
    var showDossierInterviewDialog by rememberSaveable { mutableStateOf(false) }
    var tempCameraUri by remember { mutableStateOf<Uri?>(null) }

    LaunchedEffect(openInterviewRequested) {
        if (openInterviewRequested) {
            com.example.sharkmentor.core.coaching.StudentDossierNavBus.consumeOpenInterview()
            showDossierInterviewDialog = true
        }
    }

    fun createTempPictureUri(ctx: Context): Uri {
        val tempFile = File.createTempFile("shark_ai_capture_", ".jpg", ctx.cacheDir).apply {
            createNewFile()
            deleteOnExit()
        }
        return FileProvider.getUriForFile(
            ctx,
            "${ctx.packageName}.fileprovider",
            tempFile
        )
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && tempCameraUri != null) {
            viewModel.onAttachmentSelected(tempCameraUri!!)
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            val uri = createTempPictureUri(context)
            tempCameraUri = uri
            cameraLauncher.launch(uri)
        } else {
            Toast.makeText(context, "Kamera izni yok", Toast.LENGTH_SHORT).show()
        }
    }

    val pickVisualMediaLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.onAttachmentSelected(uri)
        }
    }

    // SAF picker directly accepting both images and PDFs via OpenDocument
    val safPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.onAttachmentSelected(uri)
        }
    }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(activeChatSessionId) {
        if (uiState.messages.isEmpty()) {
            listState.scrollToItem(0)
        }
    }

    LaunchedEffect(uiState.messages.size, uiState.isLoading) {
        if (uiState.messages.isNotEmpty() || uiState.isLoading) {
            val count = 1 + uiState.messages.size + if (uiState.isLoading) 1 else 0
            if (count > 0) {
                listState.animateScrollToItem(count - 1)
            }
        } else {
            listState.scrollToItem(0)
        }
    }

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = Color.Transparent,
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                val activeSubRunnerCmd = when {
                    lockedEngine == SharkAiEngine.SOLVER_ENGINE -> "/soru"
                    lockedEngine == SharkAiEngine.TWIN_QUESTION_ENGINE -> "/benzer"
                    lockedEngine == SharkAiEngine.PLANNER_OR_ORGANIZER ||
                        uiState.currentPersona == AiPersona.STUDY_PLANNER -> "/plan"
                    uiState.currentPersona == AiPersona.GOAL_SETTER -> "/hedef"
                    uiState.currentPersona == AiPersona.ZEN_CALM -> "/zen"
                    uiState.currentPersona == AiPersona.DOPAMINE_COACH -> "/dopamin"
                    else -> null
                }

                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 7.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "Shark AI",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Shark AI",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (activeSubRunnerCmd != null) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                                    modifier = Modifier.clickable { viewModel.resetToDefaultPersona() }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = activeSubRunnerCmd,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Alt çalıştırıcıyı kapat",
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(
                                onClick = { showChatHistorySheet = true },
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)),
                                modifier = Modifier.heightIn(min = 32.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .heightIn(min = 32.dp)
                                        .padding(horizontal = 9.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.History,
                                        contentDescription = "Sohbetler",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "Sohbetler (${chatSessions.size})",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1
                                    )
                                }
                            }

                            Surface(
                                onClick = {
                                    viewModel.startNewChat()
                                    scope.launch { listState.scrollToItem(0) }
                                    Toast.makeText(context, "Yeni sohbet", Toast.LENGTH_SHORT).show()
                                },
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                                modifier = Modifier.heightIn(min = 32.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .heightIn(min = 32.dp)
                                        .padding(horizontal = 9.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Yeni Sohbet",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "Yeni",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
                .background(MaterialTheme.colorScheme.background)
                .imePadding()
        ) {
            if (uiState.currentPersona == AiPersona.GOAL_SETTER) {
                val wizardState by viewModel.netWizardState.collectAsStateWithLifecycle()
                val isRefreshingNet by viewModel.isRefreshingNetProjection.collectAsStateWithLifecycle()
                var isCardExpanded by remember { mutableStateOf(true) }

                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { isCardExpanded = !isCardExpanded }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.TrackChanges,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Stratejik Net Projeksiyonu",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (isRefreshingNet) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                } else {
                                    IconButton(
                                        onClick = { viewModel.refreshNetProjection() },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = "AI Analizini Güncelle",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                Icon(
                                    imageVector = if (isCardExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Genişlet",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .clickable { isCardExpanded = !isCardExpanded }
                                        .padding(4.dp)
                                )
                            }
                        }

                        AnimatedVisibility(visible = isCardExpanded) {
                            Column(
                                modifier = Modifier.padding(top = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val tyt = wizardState.tytProjection
                                val ayt = wizardState.aytProjection

                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.background.copy(alpha = 0.5f),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(8.dp)) {
                                            Text("TYT", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(top = 4.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Column(horizontalAlignment = Alignment.Start) {
                                                    Text("Mevcut Taban", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                                    Text("${tyt?.currentAverageNet ?: 0f} Net", fontSize = 13.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
                                                }
                                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                    Text("Sene Sonu Hedefi", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                                                    Text("${tyt?.targetNet ?: 0f} Net", fontSize = 13.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
                                                }
                                                Column(horizontalAlignment = Alignment.End) {
                                                    Text("Beklenen Projeksiyon", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                                    Text("${tyt?.projectedNet ?: 0f} Net", fontSize = 13.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
                                                }
                                            }
                                        }
                                    }

                                    Surface(
                                        color = MaterialTheme.colorScheme.background.copy(alpha = 0.5f),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(8.dp)) {
                                            Text("AYT", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.tertiary)
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(top = 4.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Column(horizontalAlignment = Alignment.Start) {
                                                    Text("Mevcut Taban", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                                    Text("${ayt?.currentAverageNet ?: 0f} Net", fontSize = 13.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
                                                }
                                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                    Text("Sene Sonu Hedefi", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                                                    Text("${ayt?.targetNet ?: 0f} Net", fontSize = 13.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
                                                }
                                                Column(horizontalAlignment = Alignment.End) {
                                                    Text("Beklenen Projeksiyon", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                                    Text("${ayt?.projectedNet ?: 0f} Net", fontSize = 13.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
                                                }
                                            }
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TextButton(
                                        onClick = {
                                            viewModel.sendMessage("Mevcut net projeksiyonuma göre açığı kapatmak için hangi konulara öncelik vermeliyim?")
                                        }
                                    ) {
                                        Text("Net Açığı Analizi", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item(key = uiState.currentPersona) {
                    AnimatedVisibility(
                        visible = uiState.messages.isEmpty(),
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        val currentUserName = remember { FirebaseAuth.getInstance().currentUser?.displayName.orEmpty() }
                        PersonalizedGreetingHeader(userName = currentUserName, selectedMode = uiState.currentPersona)
                    }
                }

                items(uiState.messages, key = { it.id }) { msg ->
                    when (msg.sender) {
                        "system" -> {
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                Surface(
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    RichChatText(
                                        text = msg.text,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                        "user" -> {
                            var showMenu by remember { mutableStateOf(false) }
                            val clipboardManager = LocalClipboardManager.current
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.CenterEnd
                            ) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 4.dp, bottomStart = 16.dp, bottomEnd = 16.dp),
                                    modifier = Modifier
                                        .widthIn(min = 60.dp, max = 680.dp)
                                        .fillMaxWidth(0.85f)
                                        .pointerInput(Unit) {
                                            detectTapGestures(onLongPress = { showMenu = true })
                                        }
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        if (msg.attachmentUri != null) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.15f))
                                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.PictureAsPdf,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onPrimary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = msg.attachmentName ?: "Ekli Dosya",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onPrimary,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(6.dp))
                                        }
                                        RichChatText(
                                            text = msg.text,
                                            textColor = MaterialTheme.colorScheme.onPrimary
                                        )
                                    }
                                }
                                DropdownMenu(
                                    expanded = showMenu,
                                    onDismissRequest = { showMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Kopyala") },
                                        onClick = {
                                            clipboardManager.setText(AnnotatedString(msg.text))
                                            Toast.makeText(context, "Kopyalandı", Toast.LENGTH_SHORT).show()
                                            showMenu = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Düzenle") },
                                        onClick = {
                                            viewModel.onInputTextChanged(msg.text)
                                            showMenu = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Sil") },
                                        onClick = {
                                            viewModel.deleteMessage(msg.id)
                                            showMenu = false
                                        }
                                    )
                                }
                            }
                        }
                        else -> {
                            var showMenu by remember { mutableStateOf(false) }
                            val clipboardManager = LocalClipboardManager.current
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .pointerInput(Unit) {
                                            detectTapGestures(onLongPress = { showMenu = true })
                                        }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.Top,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(32.dp)
                                                .background(
                                                    Brush.linearGradient(
                                                        listOf(
                                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                                                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)
                                                        )
                                                    ),
                                                    CircleShape
                                                )
                                                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.AutoAwesome,
                                                contentDescription = "AI",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(17.dp)
                                            )
                                        }
                                        Surface(
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxWidth(),
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                                            shape = RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp),
                                            tonalElevation = 2.dp
                                        ) {
                                            Column(modifier = Modifier.padding(14.dp)) {
                                                val (cleanBubbleText, slashQuickFillPayload) = remember(msg.text) {
                                                    com.example.sharkmentor.ui.aichat.SharkSlashCommandRegistry
                                                        .extractQuickFillPayload(msg.text)
                                                }
                                                // Header: Unified Shark AI brand & copy button
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(bottom = 6.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text(
                                                        text = "Shark AI",
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                    IconButton(
                                                        onClick = {
                                                            clipboardManager.setText(AnnotatedString(cleanBubbleText))
                                                            Toast.makeText(context, "Kopyalandı", Toast.LENGTH_SHORT).show()
                                                        },
                                                        modifier = Modifier.size(24.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.ContentCopy,
                                                            contentDescription = "Kopyala",
                                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                                            modifier = Modifier.size(14.dp)
                                                        )
                                                    }
                                                }

                                                HorizontalDivider(
                                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                                                    thickness = 1.dp,
                                                    modifier = Modifier.padding(bottom = 8.dp)
                                                )

                                                if (msg.isPlanCard) {
                                                    Surface(
                                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                                        shape = RoundedCornerShape(8.dp),
                                                        modifier = Modifier.fillMaxWidth()
                                                    ) {
                                                        Column(modifier = Modifier.padding(12.dp)) {
                                                            Text(
                                                                "Plan Takvime Eklendi",
                                                                fontSize = 12.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = MaterialTheme.colorScheme.onSurface
                                                            )
                                                            Spacer(modifier = Modifier.height(4.dp))
                                                            Text(
                                                                cleanBubbleText,
                                                                fontSize = 11.sp,
                                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                                                            )
                                                            Spacer(modifier = Modifier.height(8.dp))
                                                            Button(
                                                                onClick = { onNavigateToPlans(msg.planFolderId ?: "plans") },
                                                                shape = RoundedCornerShape(8.dp),
                                                                colors = ButtonDefaults.buttonColors(
                                                                    containerColor = MaterialTheme.colorScheme.primary,
                                                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                                                ),
                                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                                                modifier = Modifier.height(32.dp)
                                                            ) {
                                                                Text("Planlara Git", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                            }
                                                        }
                                                    }
                                                } else {
                                                    RichSolutionContent(
                                                        text = cleanBubbleText,
                                                        textColor = MaterialTheme.colorScheme.onSurface,
                                                        fontSize = 15.sp,
                                                        lineHeight = 23.sp
                                                    )
                                                }

                                                if (!slashQuickFillPayload.isNullOrBlank()) {
                                                    val cmdToken = slashQuickFillPayload.substringBefore(' ')
                                                    Spacer(modifier = Modifier.height(8.dp))
                                                    ActionChip(
                                                        icon = Icons.Default.AutoAwesome,
                                                        label = "$cmdToken ile doldur",
                                                        onClick = {
                                                            viewModel.onInputTextChanged(slashQuickFillPayload)
                                                        }
                                                    )
                                                }

                                                if (msg.text.contains("#ACTION:NAV_TARGETS")) {
                                                    Spacer(modifier = Modifier.height(8.dp))
                                                    ActionChip(
                                                        icon = Icons.Default.TrackChanges,
                                                        label = "Hedeflerime Git",
                                                        onClick = { onNavigateToPlans("goals") }
                                                    )
                                                }

                                                if (msg.text.contains("#ACTION:NAV_DESK")) {
                                                    Spacer(modifier = Modifier.height(8.dp))
                                                    ActionChip(
                                                        icon = Icons.Default.PlayArrow,
                                                        label = "Odak Oturumu Başlat",
                                                        onClick = { onNavigateToPlans("focus") }
                                                    )
                                                }

                                                if (msg.text.contains("#ACTION:NAV_PLANS")) {
                                                    Spacer(modifier = Modifier.height(8.dp))
                                                    ActionChip(
                                                        icon = Icons.Default.DateRange,
                                                        label = "Planlarıma Git",
                                                        onClick = { onNavigateToPlans("plans") }
                                                    )
                                                }

                                                if (msg.text.contains("#ACTION:NAV_RESOURCES")) {
                                                    Spacer(modifier = Modifier.height(8.dp))
                                                    ActionChip(
                                                        icon = Icons.AutoMirrored.Filled.LibraryBooks,
                                                        label = "Kaynaklarıma Git",
                                                        onClick = { onNavigateToPlans("resources") }
                                                    )
                                                }

                                                if (msg.text.contains("#ACTION:NAV_TOPICS")) {
                                                    Spacer(modifier = Modifier.height(8.dp))
                                                    ActionChip(
                                                        icon = Icons.AutoMirrored.Filled.MenuBook,
                                                        label = "Konularıma Git",
                                                        onClick = { onNavigateToPlans("topics") }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                                DropdownMenu(
                                    expanded = showMenu,
                                    onDismissRequest = { showMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Kopyala") },
                                        onClick = {
                                            clipboardManager.setText(AnnotatedString(msg.text))
                                            Toast.makeText(context, "Kopyalandı", Toast.LENGTH_SHORT).show()
                                            showMenu = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Düzenle") },
                                        onClick = {
                                            viewModel.onInputTextChanged(msg.text)
                                            showMenu = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Sil") },
                                        onClick = {
                                            viewModel.deleteMessage(msg.id)
                                            showMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                if (uiState.isLoading) {
                    item {
                        var statusText by remember { mutableStateOf("Düşünüyor...") }
                        LaunchedEffect(Unit) {
                            val phrases = listOf(
                                "Düşünüyor...",
                                "Analiz ediyor...",
                                "Araştırıyor...",
                                "Hazırlanıyor..."
                            )
                            var idx = 0
                            while (isActive) {
                                statusText = phrases[idx % phrases.size]
                                delay(1600)
                                idx++
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(
                                        Brush.linearGradient(
                                            listOf(
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                                                MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)
                                            )
                                        ),
                                        CircleShape
                                    )
                                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = "AI",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                                shape = RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp),
                                tonalElevation = 2.dp
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    PulsingDotsIndicator(tint = MaterialTheme.colorScheme.primary)
                                    Text(
                                        text = statusText,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = uiState.messages.isEmpty(),
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                key(uiState.currentPersona) {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(
                            items = getFlashCardsForPersona(uiState.currentPersona),
                            key = { "${uiState.currentPersona.name}_${it.id}" }
                        ) { card ->
                            Surface(
                                onClick = {
                                    if (!uiState.isLoading) {
                                        viewModel.sendMessage(card.promptPayload)
                                    }
                                },
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = card.icon,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = card.title,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Dynamic thumbnail preview strip directly above the input bar when a file is selected
            AnimatedVisibility(
                visible = selectedAttachmentUri != null && attachmentPreview != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                attachmentPreview?.let { preview ->
                    AttachmentThumbnailStrip(
                        preview = preview,
                        onCancelSelection = { viewModel.clearAttachment() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
            }

            // Antigravity-style `/` Slash Sub-Runner Autocomplete Menu (100% Theme-Synced & Low-End Smooth)
            val slashSuggestions = remember(inputText) {
                com.example.sharkmentor.ui.aichat.SharkSlashCommandRegistry.filterSuggestions(inputText)
            }
            AnimatedVisibility(
                visible = slashSuggestions.isNotEmpty(),
                enter = fadeIn(animationSpec = androidx.compose.animation.core.tween(110)) +
                    expandVertically(animationSpec = androidx.compose.animation.core.tween(130)),
                exit = fadeOut(animationSpec = androidx.compose.animation.core.tween(90)) +
                    shrinkVertically(animationSpec = androidx.compose.animation.core.tween(110))
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)),
                    tonalElevation = 2.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 260.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(vertical = 6.dp)
                    ) {
                        Text(
                            text = "ALT ÇALIŞTIRICILAR (/)",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
                        )
                        slashSuggestions.forEachIndexed { idx, cmd ->
                            val (isLocked, lockReason) = remember(cmd, uiState.isDopamineActive, uiState.isInstitutional) {
                                com.example.sharkmentor.ui.aichat.SharkSlashCommandRegistry.isCommandLocked(
                                    cmd = cmd,
                                    isDopamineActive = uiState.isDopamineActive,
                                    isInstitutional = uiState.isInstitutional
                                )
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (isLocked) {
                                            Toast.makeText(
                                                context,
                                                lockReason ?: "${cmd.title} şu an kilitli.",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        } else {
                                            viewModel.onInputTextChanged("${cmd.command} ")
                                        }
                                    }
                                    .alpha(if (isLocked) 0.58f else 1f)
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isLocked) {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    } else {
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = if (isLocked) Icons.Default.Lock else cmd.icon,
                                            contentDescription = cmd.title,
                                            tint = if (isLocked) {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            } else {
                                                MaterialTheme.colorScheme.onPrimaryContainer
                                            },
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(1.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = cmd.command,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isLocked) {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            } else {
                                                MaterialTheme.colorScheme.primary
                                            }
                                        )
                                        Text(
                                            text = cmd.title,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (isLocked) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.65f)
                                            ) {
                                                Text(
                                                    text = "Kilitli",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        text = if (isLocked && !lockReason.isNullOrBlank()) lockReason else cmd.subtitle,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            if (idx < slashSuggestions.lastIndex) {
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                                    modifier = Modifier.padding(horizontal = 12.dp)
                                )
                            }
                        }
                    }
                }
            }

            val isImeOpen = WindowInsets.isImeVisible

            MathSymbolKeyboardPanel(
                visible = showMathSymbolPanel,
                onInsertSymbol = { sym ->
                    viewModel.onInputTextChanged(MathSymbolCatalog.insertSymbol(inputText, sym))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            )

            // Antigravity / Claude Code / OpenCode Style Unified Composer (No + button, No Auto-Detect chip!)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = 14.dp,
                        end = 14.dp,
                        top = 4.dp,
                        bottom = if (isImeOpen) 4.dp else 8.dp
                    ),
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Row 1: Clean multi-line prompt field with `/` discovery hint
                    BasicTextField(
                        value = inputText,
                        onValueChange = { viewModel.onInputTextChanged(it) },
                        maxLines = 5,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                        textStyle = TextStyle(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 15.sp,
                            lineHeight = 21.sp
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        decorationBox = { innerTextField ->
                            if (inputText.isEmpty()) {
                                Text(
                                    text = "Mesaj yaz veya araçlar için / kullan...",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.58f),
                                    fontSize = 14.sp
                                )
                            }
                            innerTextField()
                        }
                    )

                    // Row 2: Subtle inner composer toolbar (Left: Clip + ∑ + Optional Custom Model Switcher | Right: Send)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            // Attachment Icon (Clip): Camera, Gallery, PDF
                            IconButton(
                                onClick = { showAttachmentSheet = true },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AttachFile,
                                    contentDescription = "Dosya / Görsel / PDF Ekle",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(19.dp)
                                )
                            }

                            // Math & Formula Symbol Panel Toggle (∑)
                            MathSymbolToggleButton(
                                expanded = showMathSymbolPanel,
                                onClick = { showMathSymbolPanel = !showMathSymbolPanel }
                            )

                            // Antigravity / OpenCode Inline Custom Model Switcher
                            // Shown ONLY when user has configured at least one custom API key in Settings!
                            if (hasAnyCustomApiKey) {
                                Surface(
                                    onClick = { showCustomModelSheet = true },
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
                                    border = BorderStroke(
                                        0.75.dp,
                                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
                                    ),
                                    modifier = Modifier.padding(start = 4.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        Text(
                                            text = activeModelDisplay,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.widthIn(max = 175.dp)
                                        )
                                        Icon(
                                            imageVector = Icons.Default.KeyboardArrowDown,
                                            contentDescription = "Model Değiştir",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }

                        val canSend = inputText.isNotBlank() || selectedAttachmentUri != null
                        IconButton(
                            onClick = {
                                if (uiState.isLoading) {
                                    viewModel.stopGeneration()
                                } else if (canSend) {
                                    viewModel.sendMessage(
                                        userInput = inputText,
                                        attachmentUri = selectedAttachmentUri,
                                        attachmentName = attachmentPreview?.displayName
                                    )
                                }
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = if (uiState.isLoading) {
                                    MaterialTheme.colorScheme.errorContainer
                                } else if (canSend) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHighest
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (uiState.isLoading) Icons.Default.Close else Icons.AutoMirrored.Filled.Send,
                                        contentDescription = "Gönder",
                                        tint = if (uiState.isLoading) {
                                            MaterialTheme.colorScheme.onErrorContainer
                                        } else if (canSend) {
                                            MaterialTheme.colorScheme.onPrimary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // OpenCode / Antigravity Brand-Grouped Custom Model Switcher Sheet (ONLY for user-added API keys!)
        if (showCustomModelSheet && hasAnyCustomApiKey) {
            val (currentActiveProv, currentActiveModelId) = remember(activeModelDisplay, customProviderGroups) {
                UserAiPreferences.getActiveProviderAndModel()
            }
            ModalBottomSheet(
                onDismissRequest = { showCustomModelSheet = false },
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 32.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Model Seç",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Yalnızca API anahtarı eklediğin sağlayıcılar listelenir",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (isDiscoveringInlineModels) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    customProviderGroups.forEach { group ->
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = group.companyName,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    IconButton(
                                        onClick = { viewModel.discoverModelsForCustomProvider(group.provider) },
                                        enabled = !isDiscoveringInlineModels,
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = "Modelleri Yenile",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }

                                group.models.forEach { option ->
                                    val isSelectedModel = currentActiveProv == group.provider &&
                                        currentActiveModelId.equals(option.id, ignoreCase = true)
                                    Surface(
                                        onClick = {
                                            viewModel.selectCustomModelInline(group.provider, option.id)
                                            showCustomModelSheet = false
                                        },
                                        shape = RoundedCornerShape(11.dp),
                                        color = if (isSelectedModel) {
                                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
                                        } else {
                                            MaterialTheme.colorScheme.surface.copy(alpha = 0.75f)
                                        },
                                        border = BorderStroke(
                                            1.dp,
                                            if (isSelectedModel) {
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                                            } else {
                                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                            }
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 9.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = option.displayName.ifBlank { option.id },
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = if (isSelectedModel) FontWeight.Bold else FontWeight.Medium,
                                                    color = if (isSelectedModel) {
                                                        MaterialTheme.colorScheme.onPrimaryContainer
                                                    } else {
                                                        MaterialTheme.colorScheme.onSurface
                                                    },
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                if (option.displayName.isNotBlank() && !option.displayName.equals(option.id, ignoreCase = true)) {
                                                    Text(
                                                        text = option.id,
                                                        fontSize = 11.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                            if (isSelectedModel) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = "Seçili",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(18.dp)
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

        if (showAttachmentSheet) {
            ModalBottomSheet(
                onDismissRequest = { showAttachmentSheet = false },
                containerColor = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 36.dp, top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Dosya Ekle",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    // 1. Kamera
                    Surface(
                        onClick = {
                            showAttachmentSheet = false
                            val permissionCheck = androidx.core.content.ContextCompat.checkSelfPermission(
                                context,
                                android.Manifest.permission.CAMERA
                            )
                            if (permissionCheck == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                val uri = createTempPictureUri(context)
                                tempCameraUri = uri
                                cameraLauncher.launch(uri)
                            } else {
                                cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                            }
                        },
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CameraAlt,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "Kamera",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Fotoğraf çek",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // 2. Galeri
                    Surface(
                        onClick = {
                            showAttachmentSheet = false
                            pickVisualMediaLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PhotoLibrary,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "Galeri",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Görsel seç",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // 3. PDF / Dosya
                    Surface(
                        onClick = {
                            showAttachmentSheet = false
                            safPickerLauncher.launch(arrayOf("application/pdf", "image/*"))
                        },
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PictureAsPdf,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "PDF / Belge",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Dosya seç",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showChatHistorySheet) {
            val dateFormatter = remember {
                SimpleDateFormat("dd MMM HH:mm", Locale("tr", "TR"))
            }
            ModalBottomSheet(
                onDismissRequest = { showChatHistorySheet = false },
                containerColor = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 32.dp, top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Sohbetler",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${chatSessions.size} oturum",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Button(
                            onClick = {
                                viewModel.startNewChat()
                                scope.launch { listState.scrollToItem(0) }
                                showChatHistorySheet = false
                                Toast.makeText(context, "Yeni sohbet", Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Yeni Sohbet",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Yeni Sohbet",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(chatSessions, key = { it.id }) { session ->
                            val isSelected = session.id == activeChatSessionId
                            val nonSystemMessages = remember(session.messages) {
                                session.messages.filter { it.sender != "system" }
                            }
                            val lastMessagePreview = remember(nonSystemMessages) {
                                nonSystemMessages.lastOrNull()?.text
                                    ?.replace(Regex("\\s+"), " ")
                                    ?.trim()
                                    ?.take(90)
                                    ?: "Mesaj yok"
                            }
                            val formattedDate = remember(session.updatedAtMillis) {
                                runCatching { dateFormatter.format(Date(session.updatedAtMillis)) }
                                    .getOrDefault("")
                            }

                            Surface(
                                onClick = {
                                    viewModel.selectChatSession(session.id)
                                    showChatHistorySheet = false
                                },
                                shape = RoundedCornerShape(16.dp),
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                },
                                border = BorderStroke(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)
                                    }
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (isSelected) {
                                                    MaterialTheme.colorScheme.primary
                                                } else {
                                                    MaterialTheme.colorScheme.surface
                                                }
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = session.currentPersona.iconRes,
                                            contentDescription = session.currentPersona.title,
                                            tint = if (isSelected) {
                                                MaterialTheme.colorScheme.onPrimary
                                            } else {
                                                MaterialTheme.colorScheme.primary
                                            },
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = session.title.ifBlank { "Yeni Sohbet" },
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f, fill = false)
                                            )
                                            if (isSelected) {
                                                Surface(
                                                    color = MaterialTheme.colorScheme.primary,
                                                    shape = RoundedCornerShape(8.dp)
                                                ) {
                                                    Text(
                                                        text = "Aktif",
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onPrimary,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }

                                        Text(
                                            text = lastMessagePreview,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Surface(
                                                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.14f),
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Text(
                                                    text = session.currentPersona.title,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.secondary,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                            Text(
                                                text = "${nonSystemMessages.size} mesaj",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            if (formattedDate.isNotBlank()) {
                                                Text(
                                                    text = "• $formattedDate",
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }

                                    IconButton(
                                        onClick = {
                                            viewModel.deleteChatSession(session.id)
                                            Toast.makeText(context, "Sohbet silindi", Toast.LENGTH_SHORT).show()
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DeleteOutline,
                                            contentDescription = "Sohbeti Sil",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Explicit confirmation dialog before mutating or syncing Student Dossier via Shark AI
        pendingDossierAction?.let { action ->
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { viewModel.cancelPendingDossierAction() },
                shape = RoundedCornerShape(20.dp),
                containerColor = MaterialTheme.colorScheme.surface,
                title = {
                    Text(
                        text = action.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                text = {
                    Text(
                        text = action.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                confirmButton = {
                    Button(
                        onClick = { viewModel.confirmPendingDossierAction() },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Onayla")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.cancelPendingDossierAction() }) {
                        Text("İptal")
                    }
                }
            )
        }

        // Pre-Plan Readiness Check-In Card when student requests building/rebuilding a plan via Shark AI
        pendingPlanRebuildRequest?.let {
            com.example.sharkmentor.ui.coaching.PlanReadinessCheckInDialog(
                title = "Plan Öncesi Güncellik Kontrolü",
                subtitle = "Yeni planının geçmiş eksiklerini öne alıp tamamladıklarını tekrar etmemesi için konu hakimiyetini ve kitaplarında çözdüğün noktaları son haline getir.",
                confirmButtonLabel = "Güncel, Planı Kurgula",
                onConfirmProceed = { viewModel.confirmPendingPlanRebuild() },
                onDismiss = { viewModel.cancelPendingPlanRebuild() }
            )
        }

        // 10-Question Interactive Student Dossier Interview ("Öğrenci Dosyası Oluşturma")
        if (showDossierInterviewDialog) {
            StudentDossierInterviewDialog(
                existingAnswers = studentDossier.interviewAnswers,
                onDismiss = { showDossierInterviewDialog = false },
                onComplete = { answers ->
                    showDossierInterviewDialog = false
                    viewModel.completeDossierInterview(answers)
                    Toast.makeText(context, "Öğrenci Dosyası oluşturuldu", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StudentDossierInterviewDialog(
    existingAnswers: Map<String, String>,
    onDismiss: () -> Unit,
    onComplete: (Map<String, String>) -> Unit
) {
    val questions = com.example.sharkmentor.core.coaching.StudentDossierManager.INTERVIEW_QUESTIONS
    var currentIndex by remember { mutableIntStateOf(0) }
    val answersMap = remember {
        androidx.compose.runtime.mutableStateMapOf<String, String>().apply {
            putAll(existingAnswers)
        }
    }
    val q = questions[currentIndex.coerceIn(0, questions.lastIndex)]
    val currentAnswer = answersMap[q.id].orEmpty()
    val progress = (currentIndex + 1).toFloat() / questions.size.toFloat()

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .widthIn(max = 520.dp)
                .imePadding(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
            tonalElevation = 4.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Öğrenci Dosyası • Soru ${currentIndex + 1}/${questions.size}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Koçluk Profilini Oluştur",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Kapat",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                androidx.compose.material3.LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                ) {
                    Text(
                        text = "Yanıtların cihazında şifreli saklanır; yapay zeka koçuna adın ve kimlik bilgilerin olmadan anonim aktarılır.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }

                Text(
                    text = q.prompt,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                if (q.quickOptions.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        q.quickOptions.forEach { opt ->
                            val isSelected = currentAnswer.equals(opt, ignoreCase = true)
                            Surface(
                                onClick = { answersMap[q.id] = opt },
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                },
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                                )
                            ) {
                                Text(
                                    text = opt,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = currentAnswer,
                    onValueChange = { answersMap[q.id] = it },
                    placeholder = {
                        Text(
                            text = q.hint,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    },
                    minLines = 2,
                    maxLines = 4,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (currentIndex > 0) {
                        TextButton(onClick = { currentIndex-- }) {
                            Text("Geri")
                        }
                    } else {
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (currentIndex < questions.lastIndex) {
                            TextButton(onClick = { currentIndex++ }) {
                                Text("Atla")
                            }
                            Button(
                                onClick = { currentIndex++ },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("İleri")
                            }
                        } else {
                            Button(
                                onClick = { onComplete(answersMap.toMap()) },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Dosyamı Tamamla")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AttachmentThumbnailStrip(
    preview: SelectedAttachmentPreview,
    onCancelSelection: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
        tonalElevation = 3.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (preview.thumbnail != null) {
                Image(
                    bitmap = preview.thumbnail,
                    contentDescription = preview.displayName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (preview.mimeType == "application/pdf") {
                            Icons.Default.PictureAsPdf
                        } else {
                            Icons.Default.Image
                        },
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = preview.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (preview.mimeType == "application/pdf") "PDF Belgesi" else "Görsel Eki",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(
                onClick = onCancelSelection,
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Seçimi İptal Et (X)",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

private fun loadAttachmentPreview(
    resolver: ContentResolver,
    uri: Uri
): SelectedAttachmentPreview {
    val mimeType = resolver.getType(uri)
    var displayName = uri.lastPathSegment ?: "Ekli Dosya"

    runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx != -1 && cursor.moveToFirst()) {
                val name = cursor.getString(idx)
                if (!name.isNullOrBlank()) {
                    displayName = name
                }
            }
        }
    }

    val isImage = mimeType?.startsWith("image/") == true ||
        displayName.endsWith(".png", ignoreCase = true) ||
        displayName.endsWith(".jpg", ignoreCase = true) ||
        displayName.endsWith(".jpeg", ignoreCase = true) ||
        displayName.endsWith(".webp", ignoreCase = true)

    val thumbnail: ImageBitmap? = if (isImage) {
        runCatching {
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOptions)
            }
            var sampleSize = 1
            while ((boundsOptions.outWidth / sampleSize) > 512 || (boundsOptions.outHeight / sampleSize) > 512) {
                sampleSize *= 2
            }
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize.coerceAtLeast(1)
            }
            resolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)?.asImageBitmap()
            }
        }.getOrNull()
    } else {
        null
    }

    return SelectedAttachmentPreview(
        uri = uri,
        displayName = displayName,
        mimeType = mimeType,
        thumbnail = thumbnail
    )
}

@Composable
private fun ModeSelectorCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    isSelected: Boolean,
    isAvailable: Boolean = true,
    restrictionReason: String? = null,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = when {
            isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            !isAvailable -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        },
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (isAvailable) 1f else 0.45f)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                    },
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = title,
                            tint = if (isSelected) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (!isAvailable) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Kilitli",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            } else if (isSelected) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Seçili",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}
