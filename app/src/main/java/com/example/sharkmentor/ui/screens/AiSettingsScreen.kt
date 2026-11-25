package com.example.sharkmentor.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.sharkmentor.core.ai.AiProviderChoice
import com.example.sharkmentor.core.ai.AiProviderConnectionResult
import com.example.sharkmentor.core.ai.ApiKeyRotator
import com.example.sharkmentor.core.ai.GeminiModelOption
import com.example.sharkmentor.core.ai.GeminiRestClient
import com.example.sharkmentor.core.ai.MultiProviderAiClient
import com.example.sharkmentor.core.ai.UserAiPreferences
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AiSettingsScreen(
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val userAiPrefs = remember { UserAiPreferences.initialize(context) }

    // Saved StateFlows
    val savedGemini by userAiPrefs.userGeminiKeys.collectAsStateWithLifecycle()
    val savedGeminiModel by userAiPrefs.selectedUserGeminiModel.collectAsStateWithLifecycle()
    val savedGeminiList by userAiPrefs.discoveredGeminiModels.collectAsStateWithLifecycle()

    val savedDeepSeek by userAiPrefs.userDeepSeekKey.collectAsStateWithLifecycle()
    val savedDeepSeekModel by userAiPrefs.selectedDeepSeekModel.collectAsStateWithLifecycle()
    val savedDeepSeekList by userAiPrefs.discoveredDeepSeekModels.collectAsStateWithLifecycle()

    val savedKimi by userAiPrefs.userKimiKey.collectAsStateWithLifecycle()
    val savedKimiModel by userAiPrefs.selectedKimiModel.collectAsStateWithLifecycle()
    val savedKimiList by userAiPrefs.discoveredKimiModels.collectAsStateWithLifecycle()

    val savedClaude by userAiPrefs.userClaudeKey.collectAsStateWithLifecycle()
    val savedClaudeModel by userAiPrefs.selectedClaudeModel.collectAsStateWithLifecycle()
    val savedClaudeList by userAiPrefs.discoveredClaudeModels.collectAsStateWithLifecycle()

    val savedOpenAi by userAiPrefs.userOpenAiKey.collectAsStateWithLifecycle()
    val savedOpenAiModel by userAiPrefs.selectedOpenAiModel.collectAsStateWithLifecycle()
    val savedOpenAiList by userAiPrefs.discoveredOpenAiModels.collectAsStateWithLifecycle()

    val savedGrok by userAiPrefs.userGrokKey.collectAsStateWithLifecycle()
    val savedGrokModel by userAiPrefs.selectedGrokModel.collectAsStateWithLifecycle()
    val savedGrokList by userAiPrefs.discoveredGrokModels.collectAsStateWithLifecycle()

    val savedProvider by userAiPrefs.selectedAiProvider.collectAsStateWithLifecycle()
    val videoStudioAiEnabled by userAiPrefs.videoStudioAiEnabled.collectAsStateWithLifecycle()
    val videoAdBlockerEnabled by userAiPrefs.videoAdBlockerEnabled.collectAsStateWithLifecycle()
    val themePrefs = remember(context) { com.example.sharkmentor.core.data.ThemePreferences.initialize(context.applicationContext) }
    val videoPipEnabled by themePrefs.videoPipEnabled.collectAsStateWithLifecycle()

    // Editable local states
    var geminiKeysInput by remember(savedGemini) { mutableStateOf(savedGemini) }
    var selectedGeminiModel by remember(savedGeminiModel) { mutableStateOf(savedGeminiModel) }
    var discoveredGeminiModels by remember(savedGeminiList) { mutableStateOf(savedGeminiList) }

    var deepSeekInput by remember(savedDeepSeek) { mutableStateOf(savedDeepSeek) }
    var selectedDeepSeekModel by remember(savedDeepSeekModel) { mutableStateOf(savedDeepSeekModel) }
    var discoveredDeepSeekModels by remember(savedDeepSeekList) { mutableStateOf(savedDeepSeekList) }

    var kimiInput by remember(savedKimi) { mutableStateOf(savedKimi) }
    var selectedKimiModel by remember(savedKimiModel) { mutableStateOf(savedKimiModel) }
    var discoveredKimiModels by remember(savedKimiList) { mutableStateOf(savedKimiList) }

    var claudeInput by remember(savedClaude) { mutableStateOf(savedClaude) }
    var selectedClaudeModel by remember(savedClaudeModel) { mutableStateOf(savedClaudeModel) }
    var discoveredClaudeModels by remember(savedClaudeList) { mutableStateOf(savedClaudeList) }

    var openAiInput by remember(savedOpenAi) { mutableStateOf(savedOpenAi) }
    var selectedOpenAiModel by remember(savedOpenAiModel) { mutableStateOf(savedOpenAiModel) }
    var discoveredOpenAiModels by remember(savedOpenAiList) { mutableStateOf(savedOpenAiList) }

    var grokInput by remember(savedGrok) { mutableStateOf(savedGrok) }
    var selectedGrokModel by remember(savedGrokModel) { mutableStateOf(savedGrokModel) }
    var discoveredGrokModels by remember(savedGrokList) { mutableStateOf(savedGrokList) }

    var selectedProvider by remember(savedProvider) { mutableStateOf(savedProvider) }

    val hasCustomGeminiInput = remember(geminiKeysInput) {
        UserAiPreferences.parseGeminiKeyList(geminiKeysInput).isNotEmpty()
    }
    val hasBuiltInDeepSeek = remember { UserAiPreferences.hasBuiltInDeepSeekKey() }
    val hasBuiltInKimi = remember { UserAiPreferences.hasBuiltInKimiKey() }

    fun persistAll(showFeedback: Boolean = false) {
        userAiPrefs.saveSettings(
            geminiKeys = geminiKeysInput,
            deepSeekKey = deepSeekInput,
            kimiKey = kimiInput,
            claudeKey = claudeInput,
            openAiKey = openAiInput,
            provider = selectedProvider,
            selectedGeminiModel = if (hasCustomGeminiInput) selectedGeminiModel else UserAiPreferences.STANDARD_GEMINI_MODEL,
            discoveredGeminiModels = if (hasCustomGeminiInput) discoveredGeminiModels else emptyList(),
            selectedDeepSeekModel = selectedDeepSeekModel,
            discoveredDeepSeekModels = if (deepSeekInput.isNotBlank() || hasBuiltInDeepSeek) discoveredDeepSeekModels else emptyList(),
            selectedKimiModel = selectedKimiModel,
            discoveredKimiModels = if (kimiInput.isNotBlank() || hasBuiltInKimi) discoveredKimiModels else emptyList(),
            selectedClaudeModel = selectedClaudeModel,
            discoveredClaudeModels = if (claudeInput.isNotBlank()) discoveredClaudeModels else emptyList(),
            selectedOpenAiModel = selectedOpenAiModel,
            discoveredOpenAiModels = if (openAiInput.isNotBlank()) discoveredOpenAiModels else emptyList(),
            grokKey = grokInput,
            selectedGrokModel = selectedGrokModel,
            discoveredGrokModels = if (grokInput.isNotBlank()) discoveredGrokModels else emptyList()
        )
        ApiKeyRotator.invalidateAndReset()
        if (showFeedback) {
            scope.launch {
                snackbarHostState.showSnackbar("AI ve API ayarları kaydedildi")
            }
        }
    }

    // Compute live active preview for the top banner based on current editable fields
    val liveActivePair: Pair<AiProviderChoice, String> = remember(
        selectedProvider,
        geminiKeysInput,
        selectedGeminiModel,
        deepSeekInput,
        selectedDeepSeekModel,
        kimiInput,
        selectedKimiModel,
        claudeInput,
        selectedClaudeModel,
        openAiInput,
        selectedOpenAiModel,
        grokInput,
        selectedGrokModel
    ) {
        val effGeminiModel = if (hasCustomGeminiInput) selectedGeminiModel else UserAiPreferences.STANDARD_GEMINI_MODEL
        when (selectedProvider) {
            AiProviderChoice.GEMINI -> AiProviderChoice.GEMINI to effGeminiModel
            AiProviderChoice.DEEPSEEK -> AiProviderChoice.DEEPSEEK to selectedDeepSeekModel
            AiProviderChoice.KIMI -> AiProviderChoice.KIMI to selectedKimiModel
            AiProviderChoice.CLAUDE -> AiProviderChoice.CLAUDE to selectedClaudeModel
            AiProviderChoice.OPENAI -> AiProviderChoice.OPENAI to selectedOpenAiModel
            AiProviderChoice.GROK -> AiProviderChoice.GROK to selectedGrokModel
            AiProviderChoice.AUTO -> when {
                hasCustomGeminiInput -> AiProviderChoice.GEMINI to effGeminiModel
                claudeInput.isNotBlank() -> AiProviderChoice.CLAUDE to selectedClaudeModel
                openAiInput.isNotBlank() -> AiProviderChoice.OPENAI to selectedOpenAiModel
                grokInput.isNotBlank() -> AiProviderChoice.GROK to selectedGrokModel
                deepSeekInput.isNotBlank() -> AiProviderChoice.DEEPSEEK to selectedDeepSeekModel
                kimiInput.isNotBlank() -> AiProviderChoice.KIMI to selectedKimiModel
                else -> AiProviderChoice.GEMINI to UserAiPreferences.STANDARD_GEMINI_MODEL
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Yapay Zeka & API",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                },
                actions = {
                    TextButton(onClick = { persistAll(showFeedback = true) }) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Kaydet", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 760.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                val hasAnyCustomKeyInput = hasCustomGeminiInput ||
                    deepSeekInput.isNotBlank() ||
                    kimiInput.isNotBlank() ||
                    claudeInput.isNotBlank() ||
                    openAiInput.isNotBlank() ||
                    grokInput.isNotBlank()

                // Compact Unified Active Status & Transparent Policy Banner
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .padding(top = 2.dp)
                                .size(24.dp)
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = if (hasAnyCustomKeyInput) {
                                    "Aktif Model: ${liveActivePair.first.shortLabel} • ${liveActivePair.second}"
                                } else {
                                    "Aktif Model: Shark AI (Yerleşik)"
                                },
                                style = MaterialTheme.typography.titleSmall,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (!hasAnyCustomKeyInput) {
                                    "Tüm araçlar yerleşik Shark AI ile hazır çalışır. İstersen aşağıdan kendi API anahtarını ekleyip sohbet ekranından model değiştirebilirsin."
                                } else {
                                    "Planlama (/plan) ve video keşfi kararlılık için yerleşik Shark AI altyapısını kullanır; sohbet ve diğer tüm araçlar seçtiğin kendi API anahtarınla çalışır."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 12.5.sp,
                                lineHeight = 17.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Video Studio Controls
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Video Stüdyosu",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        AiModuleToggleRow(
                            title = "Video İçi AI Asistanı",
                            subtitle = "İzlenen ders ve dakikaya göre soru sorma",
                            checked = videoStudioAiEnabled,
                            onCheckedChange = { userAiPrefs.setVideoStudioAiEnabled(context, it) }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                        AiModuleToggleRow(
                            title = "Reklam Engelleyici",
                            subtitle = "Video Stüdyosu'nda reklamları engeller",
                            checked = videoAdBlockerEnabled,
                            onCheckedChange = { userAiPrefs.setVideoAdBlockerEnabled(context, it) }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                        AiModuleToggleRow(
                            title = "Yüzen Video Penceresi (PiP • BETA)",
                            subtitle = "Video oynarken uygulamadan çıkınca küçük pencerede devam eder",
                            checked = videoPipEnabled,
                            onCheckedChange = { themePrefs.setVideoPipEnabled(it) }
                        )
                    }
                }

                // Active Provider Choice (shown only when user configures at least one custom key)
                if (hasAnyCustomKeyInput) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Birincil Sağlayıcı",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                AiProviderChoice.entries.forEach { provider ->
                                    val isSelected = selectedProvider == provider
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            selectedProvider = provider
                                            persistAll(showFeedback = false)
                                        },
                                        label = {
                                            Text(
                                                text = provider.shortLabel,
                                                fontSize = 13.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                            )
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                Text(
                    text = "Özel API Anahtarları (İsteğe Bağlı)",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )

                // 1. Gemini (Google AI Studio)
                ProviderConfigCard(
                    provider = AiProviderChoice.GEMINI,
                    title = "Gemini (Google AI Studio)",
                    keyLabel = "Gemini API Key",
                    keyPlaceholder = "Birden fazla için virgülle ayır: AIza..., AIza...",
                    keyValue = geminiKeysInput,
                    hasConfiguredKey = hasCustomGeminiInput,
                    allowTestWithoutUserKey = true,
                    selectedModel = if (hasCustomGeminiInput) selectedGeminiModel else UserAiPreferences.STANDARD_GEMINI_MODEL,
                    discoveredModels = discoveredGeminiModels,
                    isPreferred = selectedProvider == AiProviderChoice.GEMINI,
                    searchPlaceholder = "Gemini modeli ara (örn. flash, pro, 2.5, 3.8)...",
                    onKeyChange = {
                        geminiKeysInput = it
                        if (UserAiPreferences.parseGeminiKeyList(it).isEmpty()) {
                            selectedGeminiModel = UserAiPreferences.STANDARD_GEMINI_MODEL
                            discoveredGeminiModels = emptyList()
                        }
                    },
                    onClearKey = {
                        geminiKeysInput = ""
                        selectedGeminiModel = UserAiPreferences.STANDARD_GEMINI_MODEL
                        discoveredGeminiModels = emptyList()
                    },
                    onSelectProvider = {
                        selectedProvider = AiProviderChoice.GEMINI
                        persistAll(showFeedback = false)
                    },
                    onSelectModel = { modelId ->
                        selectedGeminiModel = modelId
                        persistAll(showFeedback = false)
                    },
                    onModelsDiscovered = { models ->
                        discoveredGeminiModels = models
                        if (models.none { it.id == selectedGeminiModel }) {
                            selectedGeminiModel = models
                                .firstOrNull { it.id == UserAiPreferences.STANDARD_GEMINI_MODEL }
                                ?.id ?: models.first().id
                        }
                        persistAll(showFeedback = false)
                    },
                    onDiscover = { key -> GeminiRestClient.fetchSupportedModels(key) },
                    onTest = { key, modelId ->
                        GeminiRestClient.testApiConnection(
                            context = context,
                            candidateUserKeysRaw = key,
                            candidateModelId = if (UserAiPreferences.parseGeminiKeyList(key).isNotEmpty()) modelId else null
                        )
                    }
                )

                // 2. Claude (Anthropic)
                ProviderConfigCard(
                    provider = AiProviderChoice.CLAUDE,
                    title = "Claude (Anthropic)",
                    keyLabel = "Claude API Key",
                    keyPlaceholder = "sk-ant-...",
                    keyValue = claudeInput,
                    hasConfiguredKey = claudeInput.isNotBlank(),
                    allowTestWithoutUserKey = false,
                    selectedModel = selectedClaudeModel,
                    discoveredModels = discoveredClaudeModels,
                    isPreferred = selectedProvider == AiProviderChoice.CLAUDE,
                    searchPlaceholder = "Claude modeli ara (örn. sonnet, opus, haiku)...",
                    onKeyChange = {
                        claudeInput = it
                        if (it.isBlank()) {
                            selectedClaudeModel = UserAiPreferences.DEFAULT_CLAUDE_MODEL
                            discoveredClaudeModels = emptyList()
                        }
                    },
                    onClearKey = {
                        claudeInput = ""
                        selectedClaudeModel = UserAiPreferences.DEFAULT_CLAUDE_MODEL
                        discoveredClaudeModels = emptyList()
                    },
                    onSelectProvider = {
                        selectedProvider = AiProviderChoice.CLAUDE
                        persistAll(showFeedback = false)
                    },
                    onSelectModel = { modelId ->
                        selectedClaudeModel = modelId
                        persistAll(showFeedback = false)
                    },
                    onModelsDiscovered = { models ->
                        discoveredClaudeModels = models
                        if (models.none { it.id == selectedClaudeModel }) {
                            selectedClaudeModel = models
                                .firstOrNull { it.id.contains("sonnet", ignoreCase = true) }
                                ?.id ?: models.first().id
                        }
                        persistAll(showFeedback = false)
                    },
                    onDiscover = { key -> MultiProviderAiClient.fetchSupportedModels(AiProviderChoice.CLAUDE, key) },
                    onTest = { key, modelId ->
                        MultiProviderAiClient.testConnection(
                            provider = AiProviderChoice.CLAUDE,
                            context = context,
                            candidateKey = key,
                            candidateModelId = modelId
                        )
                    }
                )

                // 3. GPT (OpenAI)
                ProviderConfigCard(
                    provider = AiProviderChoice.OPENAI,
                    title = "GPT (OpenAI)",
                    keyLabel = "OpenAI API Key",
                    keyPlaceholder = "sk-proj-... veya sk-...",
                    keyValue = openAiInput,
                    hasConfiguredKey = openAiInput.isNotBlank(),
                    allowTestWithoutUserKey = false,
                    selectedModel = selectedOpenAiModel,
                    discoveredModels = discoveredOpenAiModels,
                    isPreferred = selectedProvider == AiProviderChoice.OPENAI,
                    searchPlaceholder = "GPT modeli ara (örn. gpt-5, gpt-4o, o3)...",
                    onKeyChange = {
                        openAiInput = it
                        if (it.isBlank()) {
                            selectedOpenAiModel = UserAiPreferences.DEFAULT_OPENAI_MODEL
                            discoveredOpenAiModels = emptyList()
                        }
                    },
                    onClearKey = {
                        openAiInput = ""
                        selectedOpenAiModel = UserAiPreferences.DEFAULT_OPENAI_MODEL
                        discoveredOpenAiModels = emptyList()
                    },
                    onSelectProvider = {
                        selectedProvider = AiProviderChoice.OPENAI
                        persistAll(showFeedback = false)
                    },
                    onSelectModel = { modelId ->
                        selectedOpenAiModel = modelId
                        persistAll(showFeedback = false)
                    },
                    onModelsDiscovered = { models ->
                        discoveredOpenAiModels = models
                        if (models.none { it.id == selectedOpenAiModel }) {
                            selectedOpenAiModel = models
                                .firstOrNull { it.id == UserAiPreferences.DEFAULT_OPENAI_MODEL }
                                ?.id ?: models.first().id
                        }
                        persistAll(showFeedback = false)
                    },
                    onDiscover = { key -> MultiProviderAiClient.fetchSupportedModels(AiProviderChoice.OPENAI, key) },
                    onTest = { key, modelId ->
                        MultiProviderAiClient.testConnection(
                            provider = AiProviderChoice.OPENAI,
                            context = context,
                            candidateKey = key,
                            candidateModelId = modelId
                        )
                    }
                )

                // 4. DeepSeek
                ProviderConfigCard(
                    provider = AiProviderChoice.DEEPSEEK,
                    title = "DeepSeek",
                    keyLabel = "DeepSeek API Key",
                    keyPlaceholder = "sk-...",
                    keyValue = deepSeekInput,
                    hasConfiguredKey = deepSeekInput.isNotBlank(),
                    allowTestWithoutUserKey = hasBuiltInDeepSeek,
                    selectedModel = selectedDeepSeekModel,
                    discoveredModels = discoveredDeepSeekModels,
                    isPreferred = selectedProvider == AiProviderChoice.DEEPSEEK,
                    searchPlaceholder = "DeepSeek modeli ara (chat, reasoner)...",
                    onKeyChange = {
                        deepSeekInput = it
                        if (it.isBlank() && !hasBuiltInDeepSeek) {
                            selectedDeepSeekModel = UserAiPreferences.DEFAULT_DEEPSEEK_MODEL
                            discoveredDeepSeekModels = emptyList()
                        }
                    },
                    onClearKey = {
                        deepSeekInput = ""
                        if (!hasBuiltInDeepSeek) {
                            selectedDeepSeekModel = UserAiPreferences.DEFAULT_DEEPSEEK_MODEL
                            discoveredDeepSeekModels = emptyList()
                        }
                    },
                    onSelectProvider = {
                        selectedProvider = AiProviderChoice.DEEPSEEK
                        persistAll(showFeedback = false)
                    },
                    onSelectModel = { modelId ->
                        selectedDeepSeekModel = modelId
                        persistAll(showFeedback = false)
                    },
                    onModelsDiscovered = { models ->
                        discoveredDeepSeekModels = models
                        if (models.none { it.id == selectedDeepSeekModel }) {
                            selectedDeepSeekModel = models
                                .firstOrNull { it.id == UserAiPreferences.DEFAULT_DEEPSEEK_MODEL }
                                ?.id ?: models.first().id
                        }
                        persistAll(showFeedback = false)
                    },
                    onDiscover = { key -> MultiProviderAiClient.fetchSupportedModels(AiProviderChoice.DEEPSEEK, key) },
                    onTest = { key, modelId ->
                        MultiProviderAiClient.testConnection(
                            provider = AiProviderChoice.DEEPSEEK,
                            context = context,
                            candidateKey = key,
                            candidateModelId = modelId
                        )
                    }
                )

                // 5. Kimi (Moonshot)
                ProviderConfigCard(
                    provider = AiProviderChoice.KIMI,
                    title = "Kimi (Moonshot)",
                    keyLabel = "Kimi API Key",
                    keyPlaceholder = "sk-...",
                    keyValue = kimiInput,
                    hasConfiguredKey = kimiInput.isNotBlank(),
                    allowTestWithoutUserKey = hasBuiltInKimi,
                    selectedModel = selectedKimiModel,
                    discoveredModels = discoveredKimiModels,
                    isPreferred = selectedProvider == AiProviderChoice.KIMI,
                    searchPlaceholder = "Kimi modeli ara (kimi-k2, moonshot)...",
                    onKeyChange = {
                        kimiInput = it
                        if (it.isBlank() && !hasBuiltInKimi) {
                            selectedKimiModel = UserAiPreferences.DEFAULT_KIMI_MODEL
                            discoveredKimiModels = emptyList()
                        }
                    },
                    onClearKey = {
                        kimiInput = ""
                        if (!hasBuiltInKimi) {
                            selectedKimiModel = UserAiPreferences.DEFAULT_KIMI_MODEL
                            discoveredKimiModels = emptyList()
                        }
                    },
                    onSelectProvider = {
                        selectedProvider = AiProviderChoice.KIMI
                        persistAll(showFeedback = false)
                    },
                    onSelectModel = { modelId ->
                        selectedKimiModel = modelId
                        persistAll(showFeedback = false)
                    },
                    onModelsDiscovered = { models ->
                        discoveredKimiModels = models
                        if (models.none { it.id == selectedKimiModel }) {
                            selectedKimiModel = models
                                .firstOrNull { it.id.contains("k2", ignoreCase = true) || it.id == UserAiPreferences.DEFAULT_KIMI_MODEL }
                                ?.id ?: models.first().id
                        }
                        persistAll(showFeedback = false)
                    },
                    onDiscover = { key -> MultiProviderAiClient.fetchSupportedModels(AiProviderChoice.KIMI, key) },
                    onTest = { key, modelId ->
                        MultiProviderAiClient.testConnection(
                            provider = AiProviderChoice.KIMI,
                            context = context,
                            candidateKey = key,
                            candidateModelId = modelId
                        )
                    }
                )

                // 6. Grok (xAI)
                ProviderConfigCard(
                    provider = AiProviderChoice.GROK,
                    title = "Grok (xAI)",
                    keyLabel = "xAI Grok API Key",
                    keyPlaceholder = "xai-...",
                    keyValue = grokInput,
                    hasConfiguredKey = grokInput.isNotBlank(),
                    allowTestWithoutUserKey = false,
                    selectedModel = selectedGrokModel,
                    discoveredModels = discoveredGrokModels,
                    isPreferred = selectedProvider == AiProviderChoice.GROK,
                    searchPlaceholder = "Grok modeli ara (grok-4, grok-3, mini)...",
                    onKeyChange = {
                        grokInput = it
                        if (it.isBlank()) {
                            selectedGrokModel = UserAiPreferences.DEFAULT_GROK_MODEL
                            discoveredGrokModels = emptyList()
                        }
                    },
                    onClearKey = {
                        grokInput = ""
                        selectedGrokModel = UserAiPreferences.DEFAULT_GROK_MODEL
                        discoveredGrokModels = emptyList()
                    },
                    onSelectProvider = {
                        selectedProvider = AiProviderChoice.GROK
                        persistAll(showFeedback = false)
                    },
                    onSelectModel = { modelId ->
                        selectedGrokModel = modelId
                        persistAll(showFeedback = false)
                    },
                    onModelsDiscovered = { models ->
                        discoveredGrokModels = models
                        if (models.none { it.id == selectedGrokModel }) {
                            selectedGrokModel = models
                                .firstOrNull { it.id == UserAiPreferences.DEFAULT_GROK_MODEL }
                                ?.id ?: models.first().id
                        }
                        persistAll(showFeedback = false)
                    },
                    onDiscover = { key -> MultiProviderAiClient.fetchSupportedModels(AiProviderChoice.GROK, key) },
                    onTest = { key, modelId ->
                        MultiProviderAiClient.testConnection(
                            provider = AiProviderChoice.GROK,
                            context = context,
                            candidateKey = key,
                            candidateModelId = modelId
                        )
                    }
                )

                // Save Button
                Button(
                    onClick = { persistAll(showFeedback = true) },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Ayarları Kaydet", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun ProviderConfigCard(
    provider: AiProviderChoice,
    title: String,
    keyLabel: String,
    keyPlaceholder: String,
    keyValue: String,
    hasConfiguredKey: Boolean,
    allowTestWithoutUserKey: Boolean,
    selectedModel: String,
    discoveredModels: List<GeminiModelOption>,
    isPreferred: Boolean,
    searchPlaceholder: String,
    onKeyChange: (String) -> Unit,
    onClearKey: () -> Unit,
    onSelectProvider: () -> Unit,
    onSelectModel: (String) -> Unit,
    onModelsDiscovered: (List<GeminiModelOption>) -> Unit,
    onDiscover: suspend (String) -> com.example.sharkmentor.core.ai.GeminiModelDiscoveryResult,
    onTest: suspend (String, String) -> AiProviderConnectionResult
) {
    val scope = rememberCoroutineScope()
    var showKey by remember { mutableStateOf(false) }
    var isDiscovering by remember { mutableStateOf(false) }
    var discoveryError by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var modelsExpanded by remember(discoveredModels.isNotEmpty()) { mutableStateOf(discoveredModels.isNotEmpty()) }
    var isTesting by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<AiProviderConnectionResult?>(null) }
    var lastAutoDiscoveredKey by remember { mutableStateOf(keyValue.trim()) }

    // Automatically discover models shortly after pasting/editing a new valid key (no initial-load burst)
    LaunchedEffect(keyValue) {
        val clean = keyValue.trim()
        if (clean.length < 16 || !hasConfiguredKey) {
            lastAutoDiscoveredKey = ""
            discoveryError = null
            searchQuery = ""
            return@LaunchedEffect
        }
        if (clean == lastAutoDiscoveredKey) {
            return@LaunchedEffect
        }
        delay(800L)
        lastAutoDiscoveredKey = clean
        isDiscovering = true
        discoveryError = null
        val discovery = onDiscover(keyValue)
        isDiscovering = false
        if (discovery.success && discovery.models.isNotEmpty()) {
            onModelsDiscovered(discovery.models)
            modelsExpanded = true
        } else if (discoveredModels.isEmpty()) {
            discoveryError = discovery.summary
        }
    }

    val filteredModels = remember(discoveredModels, searchQuery) {
        val q = searchQuery.trim()
        if (q.isBlank()) discoveredModels
        else discoveredModels.filter {
            it.id.contains(q, ignoreCase = true) ||
                it.displayName.contains(q, ignoreCase = true) ||
                it.description.contains(q, ignoreCase = true)
        }
    }

    var isCardExpanded by remember(provider) { mutableStateOf(hasConfiguredKey) }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            width = if (isPreferred) 1.5.dp else 1.dp,
            color = if (isPreferred) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Card Header (Tap to expand/collapse)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isCardExpanded = !isCardExpanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Key,
                        contentDescription = null,
                        tint = if (hasConfiguredKey || isPreferred) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(19.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleSmall,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = when {
                                hasConfiguredKey -> selectedModel
                                provider == AiProviderChoice.GEMINI -> "Yerleşik Shark AI hazır"
                                allowTestWithoutUserKey -> "Yerleşik • $selectedModel"
                                else -> "Anahtar eklenmedi"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 12.sp,
                            color = if (hasConfiguredKey) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (hasConfiguredKey || isPreferred) {
                        FilterChip(
                            selected = isPreferred,
                            onClick = onSelectProvider,
                            label = {
                                Text(
                                    text = if (isPreferred) "Aktif" else "Kullan",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                    }
                    Icon(
                        imageVector = if (isCardExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (isCardExpanded) "Daralt" else "Genişlet",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            if (isCardExpanded) {
                // Key Field
                AiKeyField(
                    value = keyValue,
                    onValueChange = {
                        onKeyChange(it)
                        testResult = null
                    },
                    label = keyLabel,
                    placeholder = keyPlaceholder,
                    isVisible = showKey,
                    onToggleVisibility = { showKey = !showKey },
                    onClear = {
                        onClearKey()
                        lastAutoDiscoveredKey = ""
                        discoveryError = null
                        testResult = null
                        searchQuery = ""
                        modelsExpanded = false
                    }
                )

                // Action Buttons: Fetch Models + Test Connection
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            if (isDiscovering) return@OutlinedButton
                            scope.launch {
                                isDiscovering = true
                                discoveryError = null
                                val res = onDiscover(keyValue)
                                isDiscovering = false
                                if (res.success && res.models.isNotEmpty()) {
                                    onModelsDiscovered(res.models)
                                    modelsExpanded = true
                                } else {
                                    discoveryError = res.summary
                                }
                            }
                        },
                        enabled = !isDiscovering && (hasConfiguredKey || (provider != AiProviderChoice.GEMINI && allowTestWithoutUserKey)),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 44.dp)
                    ) {
                        if (isDiscovering) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Taranıyor...", fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        } else {
                            Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(17.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Modelleri Getir", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            if (isTesting) return@OutlinedButton
                            scope.launch {
                                isTesting = true
                                testResult = null
                                val res = onTest(keyValue, selectedModel)
                                testResult = res
                                isTesting = false
                            }
                        },
                        enabled = !isTesting && (hasConfiguredKey || allowTestWithoutUserKey),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 44.dp)
                    ) {
                        if (isTesting) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Test Ediliyor...", fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(17.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Bağlantıyı Test Et", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        }
                    }
                }

                // Discovery Error Banner
                discoveryError?.let { errText ->
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.65f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = errText,
                            style = MaterialTheme.typography.bodyMedium,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                // Per-provider Connection Test Result Banner
                testResult?.let { result ->
                    val containerColor = if (result.success) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                    } else {
                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
                    }
                    val contentColor = if (result.success) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onErrorContainer
                    }
                    Surface(
                        color = containerColor,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = result.summary,
                                style = MaterialTheme.typography.titleSmall,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = contentColor
                            )
                            result.details.forEach { detailLine ->
                                Text(
                                    text = "• $detailLine",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp,
                                    color = contentColor.copy(alpha = 0.92f)
                                )
                            }
                        }
                    }
                }

                // Discovered Models Selector
                if ((hasConfiguredKey || (provider != AiProviderChoice.GEMINI && allowTestWithoutUserKey)) && discoveredModels.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { modelsExpanded = !modelsExpanded }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Desteklenen Modeller (${discoveredModels.size})",
                            style = MaterialTheme.typography.titleSmall,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Icon(
                            imageVector = if (modelsExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = if (modelsExpanded) "Gizle" else "Göster",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    if (modelsExpanded) {
                        if (discoveredModels.size > 6) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text(searchPlaceholder, fontSize = 14.sp) },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                trailingIcon = {
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { searchQuery = "" }) {
                                            Icon(Icons.Default.Close, contentDescription = "Temizle")
                                        }
                                    }
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            filteredModels.forEach { modelOpt ->
                                val isSelected = selectedModel == modelOpt.id
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                                    } else {
                                        MaterialTheme.colorScheme.surface
                                    },
                                    border = BorderStroke(
                                        width = if (isSelected) 1.8.dp else 1.dp,
                                        color = if (isSelected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.outlineVariant
                                        }
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onSelectModel(modelOpt.id)
                                            testResult = null
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        RadioButton(
                                            selected = isSelected,
                                            onClick = {
                                                onSelectModel(modelOpt.id)
                                                testResult = null
                                            }
                                        )
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            Text(
                                                text = modelOpt.displayName.ifBlank { modelOpt.id },
                                                style = MaterialTheme.typography.titleSmall,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = modelOpt.id,
                                                style = MaterialTheme.typography.bodySmall,
                                                fontSize = 12.5.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            if (modelOpt.description.isNotBlank()) {
                                                Text(
                                                    text = modelOpt.description,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontSize = 12.sp,
                                                    lineHeight = 16.sp,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = "Seçili",
                                                tint = MaterialTheme.colorScheme.primary,
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
        }
    }
}

@Composable
private fun AiKeyField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    isVisible: Boolean,
    onToggleVisibility: () -> Unit,
    onClear: () -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        placeholder = if (placeholder.isNotBlank()) {
            { Text(placeholder, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        } else null,
        textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
        singleLine = true,
        visualTransformation = if (isVisible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onToggleVisibility) {
                    Icon(
                        imageVector = if (isVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (isVisible) "Gizle" else "Göster",
                        modifier = Modifier.size(20.dp)
                    )
                }
                if (value.isNotEmpty()) {
                    IconButton(onClick = onClear) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Temizle",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        },
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun AiModuleToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}
