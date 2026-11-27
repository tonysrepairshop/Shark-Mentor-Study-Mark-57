package com.example.sharkmentor.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.AnimatedContent
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ControlPointDuplicate
import androidx.compose.material3.OutlinedButton
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import com.example.sharkmentor.ui.components.AiDisclaimerText
import com.example.sharkmentor.ui.components.AiSolutionVisualCard
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.sharkmentor.core.fsrs.FsrsRating
import com.example.sharkmentor.ui.theme.ScoreGreen
import com.example.sharkmentor.ui.theme.ScoreRed
import com.example.sharkmentor.ui.errorbank.components.ErrorTrainingFloatingAiOverlay
import com.example.sharkmentor.ui.viewmodel.ErrorBankViewModel
import com.example.sharkmentor.ui.viewmodel.TrainingUiState
import com.example.sharkmentor.ui.viewmodel.QuestionInteractionState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ErrorTrainingScreen(
    viewModel: ErrorBankViewModel = viewModel(),
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val feedbackMessage by viewModel.feedbackMessage.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    
    val interactionStates by viewModel.interactionStates.collectAsStateWithLifecycle()
    val isTwinGenerating by viewModel.isTwinGenerating.collectAsStateWithLifecycle()
    val selectedOptionIndex by viewModel.selectedOptionIndex.collectAsStateWithLifecycle()
    val isOptionChecked by viewModel.isOptionChecked.collectAsStateWithLifecycle()
    val isAnswerRevealed by viewModel.isAnswerRevealed.collectAsStateWithLifecycle()

    val bankUiState by viewModel.bankUiState.collectAsStateWithLifecycle()
    val selectedSubject = bankUiState.selectedSubject
    val selectedTopic = bankUiState.selectedTopic

    LaunchedEffect(Unit) {
        viewModel.initializeSession(
            subject = selectedSubject,
            topic = selectedTopic
        )
    }

    LaunchedEffect(feedbackMessage) {
        feedbackMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearFeedbackMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                expandedHeight = 50.dp,
                title = {
                    val activeState = uiState as? TrainingUiState.Active
                    val resolvedTitle = when {
                        !viewModel.targetSubject.isNullOrBlank() && !viewModel.targetTopic.isNullOrBlank() ->
                            "Pratik: ${viewModel.targetSubject} • ${viewModel.targetTopic}"
                        !viewModel.targetTopic.isNullOrBlank() -> "Pratik: ${viewModel.targetTopic}"
                        !viewModel.targetSubject.isNullOrBlank() -> "Pratik: ${viewModel.targetSubject}"
                        !selectedSubject.isNullOrBlank() -> "Pratik: $selectedSubject"
                        else -> "Pratik: Genel"
                    }
                    Text(
                        text = resolvedTitle,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        ),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                },
                actions = {
                    val activeState = uiState as? TrainingUiState.Active
                    if (activeState != null) {
                        Text("Kalan: ${activeState.remainingCount}", modifier = Modifier.padding(end = 16.dp), style = MaterialTheme.typography.titleSmall)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.Close, contentDescription = "Kapat")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            when (val state = uiState) {
                is TrainingUiState.Loading -> {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
                is TrainingUiState.Error -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                        Text(text = state.message, color = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel.loadQuestions() }) {
                            Text("Yeniden Dene")
                        }
                    }
                }
                is TrainingUiState.Completed -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Havuzda Soru Bulunamadı",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Veritabanında kayıtlı soru yok veya tamamlandı.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(onClick = onNavigateBack) {
                            Text("Hata Kumbarasına Dön")
                        }
                    }
                }
                is TrainingUiState.Active -> {
                    val interactionState = interactionStates[state.currentQuestion.id] ?: QuestionInteractionState()
                    var forcedWrongBySolution by remember(state.currentQuestion.id) { mutableStateOf(false) }
                    Box(modifier = Modifier.fillMaxSize()) {
                        TrainingCardContent(
                            state = state,
                            isAnswerRevealed = isAnswerRevealed,
                            interactionState = interactionState,
                            isTwinGenerating = isTwinGenerating,
                            selectedOptionIndex = selectedOptionIndex,
                            isOptionChecked = isOptionChecked,
                            forcedWrongBySolution = forcedWrongBySolution,
                            viewModel = viewModel
                        )
                        ErrorTrainingFloatingAiOverlay(
                            question = state.currentQuestion,
                            isMarkedWrongBySolution = forcedWrongBySolution,
                            onMarkedWrongBySolution = { forcedWrongBySolution = true },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TrainingCardContent(
    state: TrainingUiState.Active,
    isAnswerRevealed: Boolean,
    interactionState: QuestionInteractionState,
    isTwinGenerating: Boolean,
    selectedOptionIndex: Int?,
    isOptionChecked: Boolean,
    forcedWrongBySolution: Boolean = false,
    viewModel: ErrorBankViewModel
) {
    val currentQuestion = state.currentQuestion
    val parsedTwinData = state.parsedTwinData
    val selectedSubject = viewModel.bankUiState.collectAsStateWithLifecycle().value.selectedSubject
    val isGeneralTraining = viewModel.targetSubject.isNullOrBlank() && selectedSubject.isNullOrBlank()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        if (isGeneralTraining && currentQuestion.subject.isNotBlank()) {
            Surface(
                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(MaterialTheme.colorScheme.primary, androidx.compose.foundation.shape.CircleShape)
                    )
                    Text(
                        text = currentQuestion.subject.uppercase(java.util.Locale("tr")),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            letterSpacing = 0.5.sp
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
        
        // Mode Banner
        if (parsedTwinData != null) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Science, contentDescription = "Twin Mode")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Benzer Soru", fontWeight = FontWeight.Bold)
                }
            }
        } else {
            // Standard Mode Actions
            QuestionAssistBar(
                questionId = currentQuestion.id,
                questionText = currentQuestion.questionImageUri,
                subject = currentQuestion.subject,
                interactionState = interactionState,
                onSocraticHintClick = { viewModel.requestSocraticHint() },
                onAiSolutionClick = { viewModel.requestAiSolution(currentQuestion.id, currentQuestion.questionImageUri, currentQuestion.subject) },
                onRetryAiSolution = { viewModel.retrySolveCurrentQuestion() },
                onGenerateTwinQuestion = { viewModel.generateTwinQuestionOnDemand() }
            )

            AnimatedVisibility(visible = interactionState.socraticHint != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Text(
                        text = interactionState.socraticHint ?: "",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        if (parsedTwinData != null) {
            // Render Twin Question Text and Options
            Text(
                text = parsedTwinData.questionText,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold
            )

            parsedTwinData.options.forEachIndexed { index, option ->
                val isSelected = selectedOptionIndex == index
                val isCorrectOption = index == parsedTwinData.correctIndex
                
                val borderColor = when {
                    !isOptionChecked && isSelected -> MaterialTheme.colorScheme.primary
                    isOptionChecked && isCorrectOption -> ScoreGreen
                    isOptionChecked && isSelected && !isCorrectOption -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.surfaceVariant
                }
                
                val containerColor = when {
                    isOptionChecked && isCorrectOption -> ScoreGreen.copy(alpha = 0.1f)
                    isOptionChecked && isSelected && !isCorrectOption -> MaterialTheme.colorScheme.errorContainer
                    else -> MaterialTheme.colorScheme.surface
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clickable(enabled = !isOptionChecked) { viewModel.selectOption(index) },
                    border = BorderStroke(1.dp, borderColor),
                    colors = CardDefaults.cardColors(containerColor = containerColor)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = option,
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (isOptionChecked) {
                            if (isCorrectOption) {
                                Icon(Icons.Default.CheckCircle, contentDescription = "Correct", tint = ScoreGreen)
                            } else if (isSelected && !isCorrectOption) {
                                Icon(Icons.Default.Cancel, contentDescription = "Wrong", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(visible = isOptionChecked) {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    shape = MaterialTheme.shapes.medium
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Açıklama:", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onTertiaryContainer)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(parsedTwinData.explanation, color = MaterialTheme.colorScheme.onTertiaryContainer)
                    }
                }
            }
        } else {
            // Main Image Viewer
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(if (isAnswerRevealed) currentQuestion.solutionImageUri else currentQuestion.questionImageUri)
                    .crossfade(true)
                    .build(),
                contentDescription = if (isAnswerRevealed) "Çözüm Fotoğrafı" else "Soru Fotoğrafı",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().height(400.dp).padding(16.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Actions (Bottom Bar equivalent)
        if (isTwinGenerating) {
            Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (parsedTwinData != null) {
            // Twin Question Bottom Bar
            if (!isOptionChecked) {
                Button(
                    onClick = { viewModel.verifyTwinAnswer() },
                    enabled = selectedOptionIndex != null,
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Cevabı Kontrol Et")
                }
            } else {
                val isCorrect = selectedOptionIndex == parsedTwinData.correctIndex
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    if (isCorrect && !forcedWrongBySolution) {
                        RatingButton("İyi", MaterialTheme.colorScheme.primary) {
                            viewModel.onRatingSelected(FsrsRating.GOOD)
                        }
                        RatingButton("Kolay", ScoreGreen) {
                            viewModel.onRatingSelected(FsrsRating.EASY)
                        }
                    } else {
                        RatingButton("Tekrar", ScoreRed) {
                            viewModel.onRatingSelected(FsrsRating.AGAIN)
                        }
                    }
                }
            }
        } else {
            // Standard Question Bottom Bar
            if (!isAnswerRevealed && currentQuestion.solutionImageUri != null) {
                Button(
                    onClick = { viewModel.toggleAnswerReveal() },
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Çözümü Göster")
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    if (forcedWrongBySolution) {
                        RatingButton("Yanlış (Tekrar)", ScoreRed) {
                            viewModel.onRatingSelected(FsrsRating.AGAIN)
                        }
                    } else {
                        RatingButton("Tekrar", ScoreRed) {
                            viewModel.onRatingSelected(FsrsRating.AGAIN)
                        }
                        RatingButton("Zor", androidx.compose.ui.graphics.Color(0xFFFFA000)) {
                            viewModel.onRatingSelected(FsrsRating.HARD)
                        }
                        RatingButton("İyi", MaterialTheme.colorScheme.primary) {
                            viewModel.onRatingSelected(FsrsRating.GOOD)
                        }
                        RatingButton("Kolay", ScoreGreen) {
                            viewModel.onRatingSelected(FsrsRating.EASY)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RatingButton(label: String, color: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = color),
        modifier = Modifier.padding(horizontal = 4.dp)
    ) {
        Text(label, fontWeight = FontWeight.Bold, color = androidx.compose.ui.graphics.Color.White)
    }
}

@Composable
fun QuestionAssistBar(
    questionId: Long,
    questionText: String,
    subject: String,
    interactionState: QuestionInteractionState,
    onSocraticHintClick: () -> Unit,
    onAiSolutionClick: () -> Unit,
    onRetryAiSolution: () -> Unit,
    onGenerateTwinQuestion: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // AI Solution Box if unlocked
        AnimatedVisibility(
            visible = interactionState.cachedAiSolution != null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            if (interactionState.cachedAiSolution != null) {
                Column {
                    AiSolutionVisualCard(
                        rawSolutionText = interactionState.cachedAiSolution!!,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Not: Tam çözüm görüntülendiği için bu soru 'Yapılamadı' olarak işaretlendi.",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        // AI Error Recovery Card — shows when AI call failed or timed out
        AnimatedVisibility(
            visible = interactionState.isAiError,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "AI Çözüm Alınamadı",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Zaman aşımı veya bağlantı hatası.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.75f)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    FilledTonalButton(
                        onClick = onRetryAiSolution,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        )
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Yeniden Dene", maxLines = 1)
                    }
                }
            }
        }

    }
}
