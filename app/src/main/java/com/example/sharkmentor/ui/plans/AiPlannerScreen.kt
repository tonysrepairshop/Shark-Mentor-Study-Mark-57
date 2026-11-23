package com.example.sharkmentor.ui.plans

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.sharkmentor.ui.plans.model.PlanItem
import com.example.sharkmentor.ui.plans.viewmodel.AiStudyPlannerViewModel
import com.example.sharkmentor.ui.plans.viewmodel.PlannerUiState

// ─────────────────────────────────────────────────────────────────────────────
// Screen Entry Point
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiPlannerScreen(
    viewModel: AiStudyPlannerViewModel = viewModel()
) {
    val activePlans by viewModel.activePlans.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val isOrganizing by viewModel.isOrganizing.collectAsState()
    var userNote by remember { mutableStateOf("") }
    var selectedDay by remember { mutableStateOf(1) } // 1..7
    // "ORGANIZE" or "GENERATE" when awaiting pre-plan mastery/book readiness check-in
    var pendingPlannerAction by remember { mutableStateOf<String?>(null) }

    if (pendingPlannerAction != null) {
        val action = pendingPlannerAction
        com.example.sharkmentor.ui.coaching.PlanReadinessCheckInDialog(
            title = if (action == "ORGANIZE") "Planı Düzenlemeden Önce" else "Plan Oluşturmadan Önce",
            subtitle = "Yeni planının eksik görevlerini öne alıp tamamladıklarını tekrar etmemesi için konu hakimiyetini ve kitaplarında çözdüğün noktaları son haline getir.",
            confirmButtonLabel = if (action == "ORGANIZE") "Güncel, Planı Düzenle" else "Güncel, Planı Oluştur",
            onConfirmProceed = {
                pendingPlannerAction = null
                if (action == "ORGANIZE") {
                    viewModel.organizeWeeklyPlan()
                } else {
                    viewModel.generateWeeklyPlan(userNote)
                }
            },
            onDismiss = { pendingPlannerAction = null }
        )
    }

    // Group plans by dayOfWeek once per recomposition when activePlans changes
    val groupedByDay = remember(activePlans) {
        activePlans.groupBy { it.dayOfWeek }
    }
    val currentDayTasks = groupedByDay[selectedDay] ?: emptyList()

    Scaffold(
        containerColor = Color(0xFF070D14),
        topBar = {
            TopAppBar(
                expandedHeight = 50.dp,
                title = {
                    Text(
                        text = "Haftalık Plan",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                },
                actions = {
                    // ── "Planı Organize Et" magic-wand button ──
                    if (isOrganizing) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(36.dp)
                                .padding(end = 12.dp),
                            color = Color(0xFF2EC4B6),
                            strokeWidth = 2.dp
                        )
                    } else {
                        IconButton(onClick = { pendingPlannerAction = "ORGANIZE" }) {
                            Icon(
                                imageVector = Icons.Default.AutoFixHigh,
                                contentDescription = "Planı Organize Et",
                                tint = Color(0xFF2EC4B6)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0F1A28)
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // ── Day Selector Tab Row ──────────────────────────────────────
            DayTabRow(
                selectedDay = selectedDay,
                groupedByDay = groupedByDay,
                onDaySelected = { selectedDay = it }
            )

            // ── "Organize Et" success / error banner ─────────────────────
            if (uiState is PlannerUiState.Success) {
                val msg = (uiState as PlannerUiState.Success).summary
                if (msg.isNotBlank()) {
                    Surface(
                        color = Color(0xFF0B2A20),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = msg,
                            color = Color(0xFF2EC4B6),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            maxLines = 2
                        )
                    }
                }
            }

            // ── Consolidated Day Session Card ─────────────────────────────
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                if (currentDayTasks.isEmpty()) {
                    EmptyDayPlaceholder()
                } else {
                    DaySessionCard(
                        dayNumber = selectedDay,
                        tasks = currentDayTasks,
                        onToggle = { planId, currentStatus ->
                            viewModel.togglePlanCompletion(planId, currentStatus)
                        }
                    )
                }
            }

            // ── AI Planner Action Console ─────────────────────────────────
            AiPlannerConsole(
                uiState = uiState,
                userNote = userNote,
                onNoteChange = { userNote = it },
                onGeneratePlan = { pendingPlannerAction = "GENERATE" }
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Day Tab Row
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DayTabRow(
    selectedDay: Int,
    groupedByDay: Map<Int, List<PlanItem>>,
    onDaySelected: (Int) -> Unit
) {
    val dayLabels = listOf("Pazartesi", "Salı", "Çarşamba", "Perşembe", "Cuma", "Cumartesi", "Pazar")

    Surface(
        color = Color(0xFF0F1A28),
        modifier = Modifier.fillMaxWidth(),
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            dayLabels.forEachIndexed { index, name ->
                val dayNumber = index + 1
                val isSelected = selectedDay == dayNumber
                val hasPlans = groupedByDay.containsKey(dayNumber)
                val tabColor by animateColorAsState(
                    targetValue = if (isSelected) Color(0xFF2EC4B6) else Color.Transparent,
                    animationSpec = tween(200),
                    label = "tabColor"
                )

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .background(color = tabColor, shape = RoundedCornerShape(8.dp))
                            .clickable { onDaySelected(dayNumber) }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = name,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Color.Black else Color(0xFF94A3B8)
                        )
                    }
                    // Dot indicator when a day has plans
                    if (hasPlans) {
                        Spacer(modifier = Modifier.height(3.dp))
                        Box(
                            modifier = Modifier
                                .size(4.dp)
                                .background(
                                    color = if (isSelected) Color(0xFF2EC4B6) else Color(0xFF475569),
                                    shape = CircleShape
                                )
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Consolidated Day Session Card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DaySessionCard(
    dayNumber: Int,
    tasks: List<PlanItem>,
    onToggle: (String, Boolean) -> Unit
) {
    val dayFullName = getDayName(dayNumber)
    val completedCount = tasks.count { it.isCompleted }
    val totalCount = tasks.size
    val progressFraction = if (totalCount == 0) 0f else completedCount.toFloat() / totalCount
    val progressPercent = (progressFraction * 100).toInt()

    val animatedProgress by animateFloatAsState(
        targetValue = progressFraction,
        animationSpec = tween(600),
        label = "dayProgress"
    )

    Surface(
        color = Color(0xFF0F1A28),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ── Card Header ────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(Color(0xFF112233), Color(0xFF0F1A28))
                        ),
                        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                    )
                    .padding(16.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "$dayFullName Programı",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2EC4B6)
                        )
                        // Compact completion badge
                        Surface(
                            color = when {
                                progressPercent == 100 -> Color(0xFF0B2A20)
                                progressPercent >= 50 -> Color(0xFF1A2A1A)
                                else -> Color(0xFF1A1A2A)
                            },
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Text(
                                text = "$completedCount/$totalCount · %$progressPercent",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = when {
                                    progressPercent == 100 -> Color(0xFF4ADE80)
                                    progressPercent >= 50 -> Color(0xFF86EFAC)
                                    else -> Color(0xFF94A3B8)
                                },
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // ── Progress Bar ───────────────────────────────────────
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color(0xFF1E293B))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(animatedProgress)
                                .fillMaxHeight()
                                .background(
                                    brush = Brush.horizontalGradient(
                                        colors = listOf(Color(0xFF2EC4B6), Color(0xFF4ADE80))
                                    ),
                                    shape = RoundedCornerShape(3.dp)
                                )
                        )
                    }
                }
            }

            HorizontalDivider(color = Color(0xFF1E293B))

            // ── Task Checklist ─────────────────────────────────────────────
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(tasks, key = { it.id }) { task ->
                    SingleTaskRow(
                        task = task,
                        onToggle = { onToggle(task.id, task.isCompleted) }
                    )
                    HorizontalDivider(
                        color = Color(0xFF1E293B).copy(alpha = 0.5f),
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Single Task Row (compact checklist item inside DaySessionCard)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SingleTaskRow(
    task: PlanItem,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // ── Custom checkbox circle ────────────────────────────────────────
        val circleColor by animateColorAsState(
            targetValue = if (task.isCompleted) Color(0xFF2EC4B6) else Color.Transparent,
            animationSpec = tween(250),
            label = "checkCircleColor"
        )
        Box(
            modifier = Modifier
                .size(22.dp)
                .background(color = circleColor, shape = CircleShape)
                .border(
                    width = 1.5.dp,
                    color = if (task.isCompleted) Color(0xFF2EC4B6) else Color(0xFF475569),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (task.isCompleted) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // ── Task Content ──────────────────────────────────────────────────
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Subject + time badge
                Text(
                    text = task.subject,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (task.isCompleted) Color(0xFF475569) else Color(0xFF2EC4B6),
                    modifier = Modifier.weight(1f, fill = false),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.width(8.dp))
                // Time slot chip
                if (task.timeSlot.isNotBlank()) {
                    Surface(
                        color = Color(0xFF1E3A5F),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = task.timeSlot,
                            fontSize = 10.sp,
                            color = Color(0xFF93C5FD),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            // Topic
            Text(
                text = task.topic,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (task.isCompleted) Color(0xFF4B5563) else Color(0xFFE2E8F0),
                textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            // Strategy hint (if present)
            if (task.focusStrategy.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = task.focusStrategy,
                    fontSize = 11.sp,
                    color = Color(0xFF64748B),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // ── Question target badge ─────────────────────────────────────────
        Surface(
            color = Color(0xFF1A2233),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(
                text = "${if (task.targetQuestions > 0) task.targetQuestions else 30}S",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF94A3B8),
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Empty Day Placeholder
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EmptyDayPlaceholder() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.DateRange,
            contentDescription = null,
            tint = Color(0xFF64748B),
            modifier = Modifier.size(36.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Bu gün için plan bulunamadı",
            color = Color(0xFF64748B),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Aşağıdan yapay zeka ile plan oluşturabilirsin",
            color = Color(0xFF374151),
            fontSize = 12.sp
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// AI Planner Console (bottom sheet area)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AiPlannerConsole(
    uiState: PlannerUiState,
    userNote: String,
    onNoteChange: (String) -> Unit,
    onGeneratePlan: () -> Unit
) {
    Surface(
        color = Color(0xFF0F1A28),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = userNote,
                onValueChange = onNoteChange,
                placeholder = {
                    Text(
                        "Özel istek / Odak notu (Örn: Geometriye ağırlık ver)",
                        color = Color.Gray,
                        fontSize = 12.sp
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFF2EC4B6),
                    unfocusedBorderColor = Color(0xFF1E293B)
                ),
                maxLines = 2
            )

            Button(
                onClick = onGeneratePlan,
                enabled = uiState !is PlannerUiState.Loading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2EC4B6))
            ) {
                if (uiState is PlannerUiState.Loading) {
                    CircularProgressIndicator(
                        color = Color.Black,
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = "Yapay Zeka ile Haftalık Planı Oluştur",
                        color = Color.Black,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }

            if (uiState is PlannerUiState.Error) {
                Text(
                    text = (uiState as PlannerUiState.Error).message,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────

private fun getDayName(dayNumber: Int): String = when (dayNumber) {
    1 -> "Pazartesi"
    2 -> "Salı"
    3 -> "Çarşamba"
    4 -> "Perşembe"
    5 -> "Cuma"
    6 -> "Cumartesi"
    7 -> "Pazar"
    else -> "Gün $dayNumber"
}

// ─────────────────────────────────────────────────────────────────────────────
// Legacy PlanCardItem — kept for backward compatibility if referenced elsewhere
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun PlanCardItem(
    item: PlanItem,
    onToggle: () -> Unit
) {
    Surface(
        color = Color(0xFF112233),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .background(
                        color = if (item.isCompleted) Color(0xFF2EC4B6) else Color.Transparent,
                        shape = CircleShape
                    )
                    .border(
                        width = 1.5.dp,
                        color = if (item.isCompleted) Color(0xFF2EC4B6) else Color(0xFF475569),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (item.isCompleted) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${item.subject} • ${item.timeSlot}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2EC4B6)
                    )
                    Text(
                        text = "${if (item.targetQuestions > 0) item.targetQuestions else 30} Soru",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF94A3B8)
                    )
                }
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = item.topic,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (item.isCompleted) Color(0xFF64748B) else Color.White,
                    textDecoration = if (item.isCompleted) TextDecoration.LineThrough else TextDecoration.None
                )
                if (item.focusStrategy.isNotBlank()) {
                    Text(
                        text = item.focusStrategy,
                        fontSize = 11.sp,
                        color = Color(0xFF64748B)
                    )
                }
            }
        }
    }
}
