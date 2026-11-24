package com.example.sharkmentor.ui.components

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.sharkmentor.core.ai.InteractiveSimulation
import com.example.sharkmentor.core.ai.SimulationStudioEngine
import kotlinx.coroutines.launch
import java.io.ByteArrayInputStream

private class SimulationJsBridge(
    private val onErrorCaptured: (String) -> Unit
) {
    @JavascriptInterface
    fun onJsError(message: String?) {
        val clean = message?.trim().orEmpty()
        if (clean.isNotBlank()) {
            onErrorCaptured(clean)
        }
    }
}

/**
 * Full-screen sandboxed 2D Interactive Simulation Studio player.
 * Runs 100% offline (`blockNetworkLoads = true`, `allowFileAccess = false`) and listens for runtime
 * JS exceptions to offer 1-tap AI Auto-Repair without leaving the simulation.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun InteractiveSimulationDialog(
    simulation: InteractiveSimulation,
    onSimulationUpdated: (InteractiveSimulation) -> Unit = {},
    onDelete: ((InteractiveSimulation) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colorScheme = MaterialTheme.colorScheme

    var activeSimulation by remember(simulation.id) { mutableStateOf(simulation) }
    var reloadNonce by remember(simulation.id) { mutableIntStateOf(0) }
    var capturedJsError by remember(simulation.id) { mutableStateOf<String?>(null) }
    var isRepairing by remember { mutableStateOf(false) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            webViewRef?.let { wv ->
                runCatching {
                    wv.stopLoading()
                    wv.loadUrl("about:blank")
                    wv.removeAllViews()
                    wv.destroy()
                }
            }
            webViewRef = null
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Clean Minimal Header
                Surface(
                    color = colorScheme.surface,
                    tonalElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Science,
                            contentDescription = null,
                            tint = colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = activeSimulation.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${activeSimulation.subject} • 2D Çevrimdışı Stüdyo",
                                fontSize = 11.sp,
                                color = colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }

                        IconButton(
                            onClick = {
                                capturedJsError = null
                                reloadNonce += 1
                            },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Yeniden Başlat",
                                tint = colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(19.dp)
                            )
                        }

                        if (!activeSimulation.isBuiltIn && onDelete != null) {
                            IconButton(
                                onClick = {
                                    onDelete(activeSimulation)
                                    onDismiss()
                                },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Simülasyonu Sil",
                                    tint = colorScheme.error,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Kapat",
                                tint = colorScheme.onSurface,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // Runtime JS Error Banner + 1-Tap AI Auto-Repair
                if (!capturedJsError.isNullOrBlank()) {
                    Surface(
                        color = Color(0xFF422006),
                        border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.5f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = Color(0xFFFBBF24),
                                modifier = Modifier.size(18.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Simülasyon hatası algılandı",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFEF3C7)
                                )
                                Text(
                                    text = capturedJsError.orEmpty(),
                                    fontSize = 10.sp,
                                    color = Color(0xFFFDE68A),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Button(
                                onClick = {
                                    val err = capturedJsError ?: return@Button
                                    isRepairing = true
                                    scope.launch {
                                        val res = SimulationStudioEngine.repairSimulationWithAi(
                                            context = context,
                                            simulation = activeSimulation,
                                            jsErrorMessage = err
                                        )
                                        isRepairing = false
                                        res.onSuccess { repaired ->
                                            activeSimulation = repaired
                                            capturedJsError = null
                                            reloadNonce += 1
                                            onSimulationUpdated(repaired)
                                            Toast.makeText(context, "Simülasyon onarıldı.", Toast.LENGTH_SHORT).show()
                                        }.onFailure { e ->
                                            Toast.makeText(
                                                context,
                                                e.message ?: "Onarım tamamlanamadı.",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                },
                                enabled = !isRepairing,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                if (isRepairing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        strokeWidth = 2.dp,
                                        color = colorScheme.onPrimary
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.AutoFixHigh,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Otomatik Onar", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // Sandboxed Offline 2D Canvas WebView
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(Color(0xFF0B1220))
                ) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            WebView(ctx).apply {
                                setBackgroundColor(android.graphics.Color.parseColor("#0B1220"))
                                settings.apply {
                                    javaScriptEnabled = true
                                    domStorageEnabled = false
                                    allowFileAccess = false
                                    allowContentAccess = false
                                    blockNetworkLoads = true
                                    loadsImagesAutomatically = false
                                    cacheMode = WebSettings.LOAD_NO_CACHE
                                    useWideViewPort = true
                                    loadWithOverviewMode = true
                                    setSupportZoom(false)
                                }
                                addJavascriptInterface(
                                    SimulationJsBridge { errMsg ->
                                        post {
                                            if (capturedJsError == null) {
                                                capturedJsError = errMsg
                                            }
                                        }
                                    },
                                    "SharkSimBridge"
                                )
                                webViewClient = object : WebViewClient() {
                                    override fun shouldInterceptRequest(
                                        view: WebView?,
                                        request: WebResourceRequest?
                                    ): WebResourceResponse? {
                                        val host = request?.url?.host.orEmpty()
                                        // Block every external host strictly so simulation stays 100% offline & instant
                                        return if (host.isNotBlank() && host != "sharkmentor.local") {
                                            WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))
                                        } else {
                                            null
                                        }
                                    }
                                }
                                webViewRef = this
                            }
                        },
                        update = { wv ->
                            val renderKey = "${activeSimulation.id}_${activeSimulation.htmlCode.hashCode()}_$reloadNonce"
                            if (wv.tag != renderKey) {
                                wv.tag = renderKey
                                wv.loadDataWithBaseURL(
                                    "https://sharkmentor.local/",
                                    activeSimulation.htmlCode,
                                    "text/html",
                                    "UTF-8",
                                    null
                                )
                            }
                        }
                    )
                }
            }
        }
    }
}

/**
 * Clean bottom sheet listing saved offline AI simulations, instant built-in 2D presets,
 * and a quick 1-line topic generator right inside Shark AI.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedSimulationsSheet(
    isGenerating: Boolean = false,
    onOpenSimulation: (InteractiveSimulation) -> Unit,
    onGenerateTopic: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(context) {
        SimulationStudioEngine.ensureLoaded(context)
    }

    val savedList by SimulationStudioEngine.savedSimulations.collectAsStateWithLifecycle()
    val builtInPresets = remember { SimulationStudioEngine.builtInPresets() }
    var topicInput by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colorScheme.surface,
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .padding(bottom = 26.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Science,
                        contentDescription = null,
                        tint = colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = "2D Simülasyon Stüdyosu",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onSurface
                        )
                        Text(
                            text = "Hafif, çevrimdışı, interaktif görsel laboratuvar",
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Kapat", modifier = Modifier.size(18.dp))
                }
            }

            // Quick Topic Generator
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = topicInput,
                    onValueChange = { topicInput = it },
                    placeholder = {
                        Text(
                            "Örn: DNA replikasyonu, Doppler etkisi, RLC devre...",
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = {
                        val clean = topicInput.trim()
                        if (clean.isNotBlank()) {
                            onDismiss()
                            onGenerateTopic(clean)
                        }
                    },
                    enabled = topicInput.isNotBlank() && !isGenerating,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp)
                ) {
                    if (isGenerating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = colorScheme.onPrimary
                        )
                    } else {
                        Text("Üret", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }

            HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.45f))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (savedList.isNotEmpty()) {
                    item(key = "hdr_saved") {
                        Text(
                            text = "Kaydedilen Simülasyonlarım (${savedList.size})",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.primary
                        )
                    }
                    items(savedList, key = { it.id }) { sim ->
                        SimulationListCard(
                            simulation = sim,
                            onOpen = {
                                onDismiss()
                                onOpenSimulation(sim)
                            },
                            onDelete = {
                                SimulationStudioEngine.deleteSimulation(context, sim.id)
                            }
                        )
                    }
                }

                item(key = "hdr_presets") {
                    Text(
                        text = "Hazır İnteraktif Örnekler",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.primary,
                        modifier = Modifier.padding(top = if (savedList.isNotEmpty()) 6.dp else 0.dp)
                    )
                }

                items(builtInPresets, key = { it.id }) { preset ->
                    SimulationListCard(
                        simulation = preset,
                        onOpen = {
                            onDismiss()
                            onOpenSimulation(preset)
                        },
                        onDelete = null
                    )
                }
            }
        }
    }
}

@Composable
private fun SimulationListCard(
    simulation: InteractiveSimulation,
    onOpen: () -> Unit,
    onDelete: (() -> Unit)?
) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        onClick = onOpen,
        shape = RoundedCornerShape(14.dp),
        color = colorScheme.surfaceVariant.copy(alpha = 0.42f),
        border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = simulation.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = colorScheme.primary.copy(alpha = 0.14f)
                    ) {
                        Text(
                            text = simulation.subject,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                if (simulation.summary.isNotBlank()) {
                    Text(
                        text = simulation.summary,
                        style = MaterialTheme.typography.labelSmall,
                        color = colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (onDelete != null) {
                IconButton(onClick = onDelete, modifier = Modifier.size(30.dp)) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Sil",
                        tint = colorScheme.error.copy(alpha = 0.8f),
                        modifier = Modifier.size(17.dp)
                    )
                }
            }

            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "Başlat",
                tint = colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
