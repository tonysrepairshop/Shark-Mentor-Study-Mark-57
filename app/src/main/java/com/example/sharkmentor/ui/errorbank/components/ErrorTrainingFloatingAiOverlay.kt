package com.example.sharkmentor.ui.errorbank.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ControlPointDuplicate
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sharkmentor.core.ai.GeminiAiFactory
import com.example.sharkmentor.core.ai.QuotaCheckResult
import com.example.sharkmentor.core.ai.QuotaUsageKind
import com.example.sharkmentor.core.ai.SharkTokenQuotaManager
import com.example.sharkmentor.core.ai.UserAiPreferences
import com.example.sharkmentor.data.local.entity.ErrorQuestionEntity
import com.example.sharkmentor.ui.components.DetailedVisualDiagramCard
import com.example.sharkmentor.ui.components.RichSolutionContent
import com.example.sharkmentor.ui.components.extractSvgDiagramAndCleanText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

private data class TrainingChatTurn(
    val id: Long,
    val isUser: Boolean,
    val badge: String,
    val text: String
)

private fun loadQuestionBitmapSafely(context: Context, uriString: String): Bitmap? {
    if (uriString.isBlank()) return null
    return runCatching {
        val parsed = Uri.parse(uriString)
        val raw = when {
            parsed.scheme == "content" || parsed.scheme == "file" -> {
                context.contentResolver.openInputStream(parsed)?.use { BitmapFactory.decodeStream(it) }
            }
            else -> {
                val f = File(uriString)
                if (f.exists()) BitmapFactory.decodeFile(f.absolutePath) else null
            }
        } ?: return null
        val maxDim = 1280
        val w = raw.width
        val h = raw.height
        if (w <= maxDim && h <= maxDim) {
            raw
        } else {
            val scale = maxDim.toFloat() / maxOf(w, h).toFloat()
            val scaled = Bitmap.createScaledBitmap(raw, (w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1), true)
            if (scaled != raw) raw.recycle()
            scaled
        }
    }.getOrNull()
}

/**
 * Draggable SharkPlayer-style floating SharkAI Orb + expandable in-training Chat Window
 * for Error Bank Training. Replaces static Hint / Twin / Solver buttons.
 *
 * - Socratic Hint: guides without ever revealing the final answer.
 * - Step-by-Step Solution: marks the question WRONG (FsrsRating.AGAIN) and solves with math/physics filters.
 * - Twin Question: generates an ÖSYM-style twin question and can draw an inline SVG visual diagram.
 */
@Composable
fun ErrorTrainingFloatingAiOverlay(
    question: ErrorQuestionEntity,
    isMarkedWrongBySolution: Boolean,
    onMarkedWrongBySolution: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val colorScheme = MaterialTheme.colorScheme

    var isChatOpen by remember { mutableStateOf(false) }
    var inputText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    val chatTurns = remember(question.id) { mutableStateListOf<TrainingChatTurn>() }
    val scrollState = rememberScrollState()

    LaunchedEffect(question.id) {
        isChatOpen = false
        inputText = ""
        isLoading = false
    }

    LaunchedEffect(chatTurns.size, chatTurns.lastOrNull()?.text?.length) {
        if (chatTurns.isNotEmpty()) {
            runCatching { scrollState.animateScrollTo(scrollState.maxValue) }
        }
    }

    fun sendTrainingAiRequest(
        modeBadge: String,
        userLabel: String,
        systemDirective: String,
        userPrompt: String,
        isSolutionMode: Boolean = false
    ) {
        if (isLoading) return
        if (isSolutionMode) {
            onMarkedWrongBySolution()
        }

        val isBuiltIn = !UserAiPreferences.hasCustomUserGeminiKeys(context)
        val check = SharkTokenQuotaManager.checkCanConsume(
            context = context,
            kind = if (isSolutionMode) QuotaUsageKind.VISUAL_QUESTION else QuotaUsageKind.CHAT,
            isBuiltInKey = isBuiltIn
        )
        if (check is QuotaCheckResult.Blocked) {
            chatTurns.add(TrainingChatTurn(System.nanoTime(), isUser = false, badge = "Kota", text = check.message))
            return
        }
        if (isSolutionMode && isBuiltIn) {
            SharkTokenQuotaManager.recordUsage(context, 0L, QuotaUsageKind.VISUAL_QUESTION, isBuiltInKey = true)
        }

        chatTurns.add(TrainingChatTurn(System.nanoTime(), isUser = true, badge = modeBadge, text = userLabel))
        val aiMsgId = System.nanoTime() + 1L
        chatTurns.add(TrainingChatTurn(aiMsgId, isUser = false, badge = modeBadge, text = ""))
        isLoading = true

        scope.launch {
            val bmp = withContext(Dispatchers.IO) {
                loadQuestionBitmapSafely(context, question.questionImageUri)
            }
            try {
                val isJarvisActive = SharkTokenQuotaManager.isUnlimitedUnlocked(context)
                val jarvisDirective = SharkTokenQuotaManager.buildJarvisPersonaDirective(isJarvisActive)
                val effectiveSystemDirective = if (jarvisDirective.isNotBlank()) {
                    "$systemDirective\n\n$jarvisDirective"
                } else {
                    systemDirective
                }
                val res = GeminiAiFactory.streamContentWithFailover(
                    context = context,
                    systemInstruction = effectiveSystemDirective,
                    userPrompt = userPrompt,
                    bitmaps = if (bmp != null) listOf(bmp) else emptyList(),
                    temperature = 0.25f,
                    maxOutputTokens = 8192,
                    timeoutMs = 75_000L,
                    onPartialText = { partial ->
                        val idx = chatTurns.indexOfFirst { it.id == aiMsgId }
                        if (idx >= 0) {
                            chatTurns[idx] = chatTurns[idx].copy(
                                text = com.example.sharkmentor.ui.components.maskInProgressSvgForStreaming(partial)
                            )
                        }
                    }
                )
                val finalText = res.getOrNull()?.text?.trim().orEmpty()
                val errText = res.exceptionOrNull()?.localizedMessage
                val idx = chatTurns.indexOfFirst { it.id == aiMsgId }
                if (idx >= 0) {
                    chatTurns[idx] = chatTurns[idx].copy(
                        text = finalText.ifBlank { errText ?: "Yanıt alınamadı, tekrar dene." }
                    )
                }
            } finally {
                runCatching { if (bmp != null && !bmp.isRecycled) bmp.recycle() }
                isLoading = false
            }
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val orbSizePx = with(density) { 54.dp.toPx() }
        val padPx = with(density) { 14.dp.toPx() }
        val maxW = with(density) { maxWidth.toPx() }
        val maxH = with(density) { maxHeight.toPx() }

        var orbX by remember(maxW) { mutableFloatStateOf((maxW - orbSizePx - padPx).coerceAtLeast(padPx)) }
        var orbY by remember(maxH) { mutableFloatStateOf((maxH * 0.62f).coerceIn(padPx, (maxH - orbSizePx - padPx).coerceAtLeast(padPx))) }

        // Draggable Floating SharkAI Orb (like SharkPlayer!)
        if (!isChatOpen) {
            Surface(
                onClick = { isChatOpen = true },
                shape = CircleShape,
                color = colorScheme.primary,
                contentColor = colorScheme.onPrimary,
                shadowElevation = 8.dp,
                border = BorderStroke(1.5.dp, colorScheme.onPrimary.copy(alpha = 0.35f)),
                modifier = Modifier
                    .offset { IntOffset(orbX.roundToInt(), orbY.roundToInt()) }
                    .size(54.dp)
                    .pointerInput(maxW, maxH) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            orbX = (orbX + dragAmount.x).coerceIn(padPx, (maxW - orbSizePx - padPx).coerceAtLeast(padPx))
                            orbY = (orbY + dragAmount.y).coerceIn(padPx, (maxH - orbSizePx - padPx).coerceAtLeast(padPx))
                        }
                    }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "SharkAI Antrenman Asistanı",
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        // Expandable Floating SharkAI Training Chat Window
        AnimatedVisibility(
            visible = isChatOpen,
            enter = fadeIn() + scaleIn(initialScale = 0.92f),
            exit = fadeOut() + scaleOut(targetScale = 0.92f),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 12.dp, vertical = 12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = colorScheme.surface,
                border = BorderStroke(1.dp, colorScheme.primary.copy(alpha = 0.4f)),
                shadowElevation = 14.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 260.dp, max = 500.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Column {
                                Text(
                                    text = "SharkAI • Antrenman Asistanı",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = colorScheme.onSurface
                                )
                                Text(
                                    text = "${question.subject} • ${question.topic}",
                                    fontSize = 10.5.sp,
                                    color = colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        IconButton(onClick = { isChatOpen = false }, modifier = Modifier.size(30.dp)) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Kapat",
                                tint = colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    if (isMarkedWrongBySolution) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFEF4444).copy(alpha = 0.13f),
                            border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.45f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Bu soru çözüme gönderildiği için antrenmanda yanlış (Tekrar) olarak işaretlendi.",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFFDC2626),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }

                    // 3 Quick Mode Chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        QuickAiActionChip(
                            label = "Sokratik İpucu",
                            icon = Icons.Default.Lightbulb,
                            enabled = !isLoading,
                            onClick = {
                                sendTrainingAiRequest(
                                    modeBadge = "Sokratik İpucu",
                                    userLabel = "Cevabı söylemeden bana yol gösterici bir ipucu ver.",
                                    systemDirective = "Sen Sokratik bir YKS/LGS öğretmenisin. Görseldeki soruyu incele. KESİNLİKLE doğru şıkkı, nihai sayıyı veya doğrudan cevabı SÖYLEME! Sadece öğrenciyi çözüme götürecek kilit formülü, dikkat etmesi gereken noktayı veya ilk adımı 2-3 kısa maddede temiz Unicode sembollerle (π, Δ, √, ², ³, °) sezdir.",
                                    userPrompt = "Ders: ${question.subject}, Konu: ${question.topic}. Bu soru için cevabı vermeden Sokratik ipucu ver."
                                )
                            },
                            modifier = Modifier.weight(1f)
                        )
                        QuickAiActionChip(
                            label = "Soru Çözümü",
                            icon = Icons.Default.Psychology,
                            enabled = !isLoading,
                            isDangerTint = true,
                            onClick = {
                                sendTrainingAiRequest(
                                    modeBadge = "Adım Adım Çözüm",
                                    userLabel = "Bu soruyu adım adım detaylı çöz.",
                                    systemDirective = "Sen matematik ve fizik uzmanı bir YKS soru çözücüsün. Kesinlikle ham LaTeX kodu (\\frac, \\sqrt) yazma; temiz Unicode semboller (π, Δ, θ, ω, √, ², ³, °, ·, ×, →) kullan. Adım 1:, Adım 2:, Adım 3: şeklinde net anlat ve son satırda **Sonuç:** ile cevabı ver. Asla 'Bekle!' veya 'Durun!' gibi sesli düşünme cümlesi yazma.",
                                    userPrompt = "Ders: ${question.subject}, Konu: ${question.topic}. Görseldeki soruyu adım adım eksiksiz çöz.",
                                    isSolutionMode = true
                                )
                            },
                            modifier = Modifier.weight(1f)
                        )
                        QuickAiActionChip(
                            label = "Benzer Soru",
                            icon = Icons.Default.ControlPointDuplicate,
                            enabled = !isLoading,
                            onClick = {
                                sendTrainingAiRequest(
                                    modeBadge = "Benzer Soru",
                                    userLabel = "Aynı kazanımdan görselli özgün benzer soru üret.",
                                    systemDirective = "Sen ÖSYM tarzı özgün ikiz soru üreticisisin. Görseldeki sorunun kazanımını koruyarak sayıları/senaryoyu değiştirip yeni bir A-E şıklı soru üret. Eğer soru geometri şekli, fonksiyon grafiği veya fizik kuvvet/devre diyagramı gerektiriyorsa ÖNCE temiz bir ```svg ... ``` bloğu (viewBox=\"0 0 360 220\") çiz, ardından soruyu, şıkları ve en altta kısa çözümü Adım 1:, **Sonuç:** formatında ver.",
                                    userPrompt = "Ders: ${question.subject}, Konu: ${question.topic}. Görseldeki sorunun kazanımına uygun yeni bir benzer soru (gerekiyorsa SVG şekilli) üret."
                                )
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.4f))

                    // Messages Area
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .heightIn(min = 120.dp, max = 270.dp)
                            .verticalScroll(scrollState),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (chatTurns.isEmpty()) {
                            Text(
                                text = "Yukarıdan Sokratik İpucu, Adım Adım Çözüm (soruyu yanlış sayar) veya Görselli Benzer Soru seçebilir, ya da aşağıdan doğrudan sorunu yazabilirsin.",
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.onSurfaceVariant,
                                lineHeight = 18.sp
                            )
                        } else {
                            chatTurns.forEach { turn ->
                                if (turn.isUser) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = colorScheme.primary.copy(alpha = 0.14f)
                                        ) {
                                            Text(
                                                text = turn.text,
                                                fontSize = 12.5.sp,
                                                color = colorScheme.onSurface,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                                            )
                                        }
                                    }
                                } else {
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = colorScheme.surfaceVariant.copy(alpha = 0.42f),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            if (turn.text.isBlank() && isLoading) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                                    Text("SharkAI düşünüyor...", fontSize = 12.sp, color = colorScheme.onSurfaceVariant)
                                                }
                                            } else {
                                                val (svgMarkup, cleanText) = remember(turn.text) {
                                                    extractSvgDiagramAndCleanText(turn.text)
                                                }
                                                if (svgMarkup != null) {
                                                    DetailedVisualDiagramCard(
                                                        svgMarkup = svgMarkup,
                                                        modifier = Modifier.padding(bottom = 8.dp)
                                                    )
                                                }
                                                if (cleanText.isNotBlank()) {
                                                    RichSolutionContent(
                                                        text = cleanText,
                                                        textColor = colorScheme.onSurface,
                                                        fontSize = 13.5.sp,
                                                        lineHeight = 20.sp
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Bottom Free-Text Input
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            placeholder = { Text("Bu soruyla ilgili sor...", fontSize = 12.5.sp) },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f)
                        )
                        Surface(
                            onClick = {
                                val q = inputText.trim()
                                if (q.isNotBlank() && !isLoading) {
                                    inputText = ""
                                    sendTrainingAiRequest(
                                        modeBadge = "Sohbet",
                                        userLabel = q,
                                        systemDirective = "Sen YKS/LGS matematik ve fizik koçusun. Görseldeki soruyu dikkate alarak öğrencinin sorduğu kısma Adım 1:, Adım 2: formatında ve temiz Unicode sembollerle (π, Δ, θ, √, ², ³, °) net cevap ver.",
                                        userPrompt = "Ders: ${question.subject}, Konu: ${question.topic}. Öğrenci sorusu: $q"
                                    )
                                }
                            },
                            shape = CircleShape,
                            color = colorScheme.primary,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "Gönder",
                                    tint = colorScheme.onPrimary,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickAiActionChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    isDangerTint: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val tint = if (isDangerTint) Color(0xFFDC2626) else colorScheme.primary
    Surface(
        onClick = { if (enabled) onClick() },
        shape = RoundedCornerShape(10.dp),
        color = tint.copy(alpha = 0.11f),
        border = BorderStroke(1.dp, tint.copy(alpha = 0.35f)),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(13.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = tint,
                maxLines = 1
            )
        }
    }
}
