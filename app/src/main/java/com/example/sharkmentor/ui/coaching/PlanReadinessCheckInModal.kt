package com.example.sharkmentor.ui.coaching

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.sharkmentor.core.coaching.CoachingEngine
import com.example.sharkmentor.core.coaching.PlanAuditSnapshot
import com.example.sharkmentor.core.coaching.PlanProgressAuditor
import com.example.sharkmentor.core.coaching.PlanReadinessNavBus
import com.example.sharkmentor.core.coaching.StudentDossierManager
import com.example.sharkmentor.core.util.GoogleServicesHelper
import com.example.sharkmentor.data.local.SharkMentorDatabase
import com.example.sharkmentor.data.local.entity.ResourceEntity
import com.example.sharkmentor.data.local.entity.TopicGoalEntity
import com.example.sharkmentor.service.sync.SyncWorker
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Interactive Pre-Plan & Official Sunday Check-In Notification Card.
 *
 * Displayed:
 * 1. Before rebuilding/rebalancing a weekly plan from any plan button (Coaching Path, Plan Screen, AI Planner).
 * 2. When the student asks Shark AI in chat to generate, rebuild, or refresh a study plan.
 * 3. When opened from the official Sunday week-end notification or Sunday Coaching Path banner.
 *
 * Allows instant inline updates to both Topic Mastery ("Konu Hakimiyeti") and Library Books
 * ("Kitap İlerlemesi") with full Room + Firestore + StudentDossier synchronization before
 * generating a flexible, cumulative, non-repetitive plan.
 */
@Composable
fun PlanReadinessCheckInDialog(
    title: String = "Plan Öncesi Güncellik Kontrolü",
    subtitle: String = "Yeni planın eksiklerini devredip tamamladıklarını tekrar etmeden en sağlıklı şekilde kurgulanması için konu hakimiyetini ve kitaplarında çözdüğün noktaları son haline getir.",
    confirmButtonLabel: String = "Güncel, Planı Kurgula",
    secondarySaveOnlyLabel: String? = null,
    onOpenGoalsOrLibrary: (() -> Unit)? = null,
    onSaveOnly: (() -> Unit)? = null,
    onConfirmProceed: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colorScheme = MaterialTheme.colorScheme
    val db = remember(context) { SharkMentorDatabase.getDatabase(context) }

    val topics by remember(db) { db.goalsDao().getAllTopicGoalsFlow() }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val resources by remember(db) { db.goalsDao().getAllResourcesFlow() }
        .collectAsStateWithLifecycle(initialValue = emptyList())

    var auditSnapshot by remember { mutableStateOf(PlanAuditSnapshot()) }
    var activeTab by remember { mutableIntStateOf(0) } // 0 = Konu Hakimiyeti, 1 = Kitap İlerlemesi
    var topicFilter by remember { mutableStateOf("") }
    var editingBook by remember { mutableStateOf<ResourceEntity?>(null) }
    var isFinalizing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        auditSnapshot = PlanProgressAuditor.captureFromDatabase(context)
    }

    val filteredTopics = remember(topics, topicFilter) {
        val q = topicFilter.trim()
        val base = topics.sortedWith(compareBy<TopicGoalEntity> { it.currentLevel }.thenBy { it.subject }.thenBy { it.topic })
        if (q.isBlank()) base else base.filter {
            it.topic.contains(q, ignoreCase = true) || it.subject.contains(q, ignoreCase = true)
        }
    }

    editingBook?.let { book ->
        var unitsInput by remember(book.id, book.completedUnits) {
            mutableStateOf(book.completedUnits.toString())
        }
        AlertDialog(
            onDismissRequest = { editingBook = null },
            shape = RoundedCornerShape(18.dp),
            containerColor = colorScheme.surface,
            title = {
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (book.totalUnits > 0) {
                            "Çözdüğün güncel ${book.unitType} sayısını gir (Toplam: ${book.totalUnits})"
                        } else {
                            "Çözdüğün güncel ${book.unitType} sayısını gir"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = unitsInput,
                        onValueChange = { unitsInput = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Çözülen ${book.unitType}") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val target = (unitsInput.toIntOrNull() ?: book.completedUnits).coerceAtLeast(0)
                        val clamped = if (book.totalUnits > 0) target.coerceIn(0, book.totalUnits) else target
                        editingBook = null
                        scope.launch(Dispatchers.IO) {
                            persistBookProgressUpdate(context, db, book, clamped)
                        }
                    },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Kaydet")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingBook = null }) {
                    Text("İptal")
                }
            }
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = true
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .widthIn(max = 540.dp)
                .imePadding(),
            shape = RoundedCornerShape(22.dp),
            color = colorScheme.surface,
            border = BorderStroke(1.dp, colorScheme.primary.copy(alpha = 0.35f)),
            tonalElevation = 4.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = colorScheme.primary.copy(alpha = 0.14f),
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.AutoFixHigh,
                                    contentDescription = null,
                                    tint = colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = colorScheme.onSurface
                            )
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(30.dp)) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Kapat",
                            tint = colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Live Plan Completion Audit Pill (shows Completed vs. Carry-Over tasks)
                if (auditSnapshot.hasAnyTasks) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = colorScheme.primaryContainer.copy(alpha = 0.42f),
                        border = BorderStroke(1.dp, colorScheme.primary.copy(alpha = 0.25f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Mevcut Plan Durumu (%${auditSnapshot.completionPercent})",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = colorScheme.primary
                                )
                                Text(
                                    text = "Tamamlanan: ${auditSnapshot.completedCount} görev • Eksik/Devreden: ${auditSnapshot.incompleteCount} görev",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colorScheme.onSurface
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // 2-Tab Selector: Konu Hakimiyeti | Kitap İlerlemesi
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        CheckInTabChip(
                            label = "Konu Hakimiyeti (${topics.size})",
                            icon = Icons.Default.Psychology,
                            selected = activeTab == 0,
                            onClick = { activeTab = 0 },
                            modifier = Modifier.weight(1f)
                        )
                        CheckInTabChip(
                            label = "Kitaplar (${resources.size})",
                            icon = Icons.AutoMirrored.Filled.MenuBook,
                            selected = activeTab == 1,
                            onClick = { activeTab = 1 },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Inline Quick-Update List
                if (activeTab == 0) {
                    if (topics.size > 6) {
                        OutlinedTextField(
                            value = topicFilter,
                            onValueChange = { topicFilter = it },
                            placeholder = { Text("Ders veya konu ara...", style = MaterialTheme.typography.bodySmall) },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            textStyle = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (filteredTopics.isEmpty()) {
                        EmptyCheckInSection(
                            text = if (topics.isEmpty()) {
                                "Henüz kayıtlı konu hakimiyeti bulunmuyor. İstersen doğrudan devam edebilir veya Hedefler bölümünden konu ekleyebilirsin."
                            } else {
                                "Aramanla eşleşen konu bulunamadı."
                            },
                            actionLabel = if (topics.isEmpty() && onOpenGoalsOrLibrary != null) "Konulara Git" else null,
                            onAction = onOpenGoalsOrLibrary
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 120.dp, max = 250.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(filteredTopics, key = { it.id }) { topic ->
                                QuickTopicMasteryRow(
                                    topic = topic,
                                    onSelectLevel = { newLevel ->
                                        scope.launch(Dispatchers.IO) {
                                            persistTopicMasteryUpdate(context, db, topic, newLevel)
                                        }
                                    }
                                )
                            }
                        }
                    }
                } else {
                    if (resources.isEmpty()) {
                        EmptyCheckInSection(
                            text = "Kütüphanende henüz kitap kaydı yok. İstersen doğrudan devam edebilir veya Kütüphane sekmesinden kaynak ekleyebilirsin.",
                            actionLabel = if (onOpenGoalsOrLibrary != null) "Kütüphaneye Git" else null,
                            onAction = onOpenGoalsOrLibrary
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 120.dp, max = 250.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(resources, key = { it.id }) { book ->
                                QuickBookProgressRow(
                                    book = book,
                                    onDelta = { delta ->
                                        val raw = (book.completedUnits + delta).coerceAtLeast(0)
                                        val clamped = if (book.totalUnits > 0) raw.coerceIn(0, book.totalUnits) else raw
                                        scope.launch(Dispatchers.IO) {
                                            persistBookProgressUpdate(context, db, book, clamped)
                                        }
                                    },
                                    onEditExact = { editingBook = book }
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.45f))

                // Footer Actions
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            if (isFinalizing) return@Button
                            isFinalizing = true
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    PlanReadinessNavBus.markCheckInCompletedToday(context)
                                    CoachingEngine.refreshTargetAndLedgerAsync(context)
                                    runCatching { StudentDossierManager.syncFromDatabase(context, force = true) }
                                    SyncWorker.enqueueImmediateSync(context)
                                }
                                isFinalizing = false
                                onConfirmProceed()
                            }
                        },
                        enabled = !isFinalizing,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (isFinalizing) "Veriler Eşitleniyor..." else confirmButtonLabel,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text("Vazgeç", color = colorScheme.onSurfaceVariant)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (onOpenGoalsOrLibrary != null) {
                                TextButton(onClick = {
                                    onDismiss()
                                    onOpenGoalsOrLibrary()
                                }) {
                                    Text("Detaylı Düzenle", fontSize = 12.sp)
                                }
                            }
                            if (secondarySaveOnlyLabel != null && onSaveOnly != null) {
                                OutlinedButton(
                                    onClick = {
                                        scope.launch {
                                            withContext(Dispatchers.IO) {
                                                PlanReadinessNavBus.markCheckInCompletedToday(context)
                                                runCatching { StudentDossierManager.syncFromDatabase(context, force = true) }
                                                SyncWorker.enqueueImmediateSync(context)
                                            }
                                            onSaveOnly()
                                        }
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(secondarySaveOnlyLabel, fontSize = 12.sp)
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
private fun CheckInTabChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(9.dp),
        color = if (selected) colorScheme.primary else colorScheme.surface.copy(alpha = 0f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) colorScheme.onPrimary else colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) colorScheme.onPrimary else colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun QuickTopicMasteryRow(
    topic: TopicGoalEntity,
    onSelectLevel: (Int) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = colorScheme.surfaceVariant.copy(alpha = 0.42f),
        border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = topic.subject,
                        style = MaterialTheme.typography.labelSmall,
                        color = colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = topic.topic,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = "${topic.currentLevel}/5",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.primary
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                (0..5).forEach { lvl ->
                    val isSelected = topic.currentLevel == lvl
                    Surface(
                        onClick = { onSelectLevel(lvl) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(7.dp),
                        color = if (isSelected) colorScheme.primary else colorScheme.surface,
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) colorScheme.primary else colorScheme.outlineVariant.copy(alpha = 0.4f)
                        )
                    ) {
                        Box(
                            modifier = Modifier.padding(vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "$lvl",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) colorScheme.onPrimary else colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickBookProgressRow(
    book: ResourceEntity,
    onDelta: (Int) -> Unit,
    onEditExact: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val progressLabel = if (book.totalUnits > 0) {
        "${book.completedUnits}/${book.totalUnits} ${book.unitType}"
    } else {
        "${book.completedUnits} ${book.unitType}"
    }
    Surface(
        onClick = onEditExact,
        shape = RoundedCornerShape(12.dp),
        color = colorScheme.surfaceVariant.copy(alpha = 0.42f),
        border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = book.subject,
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "$progressLabel • Düzenlemek için dokun",
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Surface(
                    onClick = { onDelta(-1) },
                    shape = CircleShape,
                    color = colorScheme.surface,
                    border = BorderStroke(1.dp, colorScheme.outlineVariant),
                    modifier = Modifier.size(28.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Remove,
                            contentDescription = "Azalt",
                            tint = colorScheme.onSurface,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
                Surface(
                    onClick = { onDelta(1) },
                    shape = CircleShape,
                    color = colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Artır",
                            tint = colorScheme.onPrimary,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
                Surface(
                    onClick = { onDelta(5) },
                    shape = RoundedCornerShape(8.dp),
                    color = colorScheme.primary.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, colorScheme.primary.copy(alpha = 0.35f))
                ) {
                    Text(
                        text = "+5",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyCheckInSection(
    text: String,
    actionLabel: String?,
    onAction: (() -> Unit)?
) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = colorScheme.surfaceVariant.copy(alpha = 0.35f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurfaceVariant
            )
            if (actionLabel != null && onAction != null) {
                OutlinedButton(
                    onClick = onAction,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(actionLabel, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

private suspend fun persistTopicMasteryUpdate(
    context: Context,
    db: SharkMentorDatabase,
    topic: TopicGoalEntity,
    newLevel: Int
) {
    val now = System.currentTimeMillis()
    val clamped = newLevel.coerceIn(0, 5)
    val updated = topic.copy(
        currentLevel = clamped,
        isSynced = false,
        updatedAtMillis = now
    )
    db.goalsDao().updateTopicGoal(updated)

    runCatching {
        val uid = GoogleServicesHelper.getCurrentUser(context = context)?.uid
        val firestore = GoogleServicesHelper.getFirebaseFirestore(context)
        if (!uid.isNullOrBlank() && firestore != null) {
            val docId = "${uid}_${updated.id}"
            val payload = hashMapOf<String, Any?>(
                "id" to docId,
                "localId" to updated.id,
                "studentId" to uid,
                "userId" to uid,
                "subject" to updated.subject,
                "topic" to updated.topic,
                "topicName" to updated.topic,
                "masteryLevel" to clamped,
                "targetMasteryScore" to updated.targetLevel,
                "isDeleted" to false,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            firestore.collection("topics").document(docId).set(payload, SetOptions.merge())
            firestore.collection("students").document(uid).collection("topics").document(docId)
                .set(payload, SetOptions.merge())
        }
    }
    StudentDossierManager.syncInBackground(context)
    SyncWorker.enqueueImmediateSync(context)
}

private suspend fun persistBookProgressUpdate(
    context: Context,
    db: SharkMentorDatabase,
    book: ResourceEntity,
    newCompletedUnits: Int
) {
    val now = System.currentTimeMillis()
    val updated = book.copy(
        completedUnits = newCompletedUnits,
        isSynced = false,
        updatedAtMillis = now,
        lastModifiedMillis = now
    )
    db.goalsDao().updateResource(updated)

    runCatching {
        val uid = GoogleServicesHelper.getCurrentUser(context = context)?.uid
        val firestore = GoogleServicesHelper.getFirebaseFirestore(context)
        if (!uid.isNullOrBlank() && firestore != null) {
            val docId = updated.cloudId?.takeIf { it.isNotBlank() } ?: "${uid}_${updated.id}"
            val isCompleted = updated.totalUnits > 0 && newCompletedUnits >= updated.totalUnits
            val payload = hashMapOf<String, Any?>(
                "id" to docId,
                "localId" to updated.id,
                "title" to updated.title,
                "subject" to updated.subject,
                "unitType" to updated.unitType,
                "currentUnits" to newCompletedUnits,
                "completedUnits" to newCompletedUnits,
                "totalUnits" to updated.totalUnits,
                "totalTarget" to updated.totalUnits,
                "isCompleted" to isCompleted,
                "completed" to isCompleted,
                "studentId" to uid,
                "userId" to uid,
                "isDeleted" to false,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            firestore.collection("resources").document(docId).set(payload, SetOptions.merge())
            firestore.collection("students").document(uid).collection("resources").document(docId)
                .set(payload, SetOptions.merge())
        }
    }
    StudentDossierManager.syncInBackground(context)
    SyncWorker.enqueueImmediateSync(context)
}
