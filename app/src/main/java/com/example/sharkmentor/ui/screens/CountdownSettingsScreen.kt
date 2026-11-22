package com.example.sharkmentor.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.sharkmentor.core.util.GoogleServicesHelper
import com.example.sharkmentor.data.local.preferences.UserPreferences
import com.example.sharkmentor.data.repository.CountdownRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CountdownSettingsScreen(
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val countdownRepo = remember(context) { CountdownRepository.getInstance(context) }
    val userPrefs = remember(context) { UserPreferences.getInstance(context) }
    val countdownState by countdownRepo.stateFlow.collectAsStateWithLifecycle()
    val isSignedIn = remember(context) {
        GoogleServicesHelper.getCurrentUser(context = context)?.takeUnless { it.isAnonymous } != null
    }

    // Pull latest cloud countdown immediately upon opening screen so cross-device edits show live
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            runCatching { countdownRepo.pullAndMergeFromCloud() }
        }
    }

    val resolvedSavedTitle = countdownState.title.ifBlank {
        userPrefs.studentTargetExam?.takeIf { it.isNotBlank() } ?: ""
    }
    var targetTitle by remember(resolvedSavedTitle) { mutableStateOf(resolvedSavedTitle) }
    val selectedMillis = countdownState.targetDateMillis
    var showDatePicker by remember { mutableStateOf(false) }

    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = if (selectedMillis > 0L) selectedMillis else System.currentTimeMillis()
    )

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val millis = datePickerState.selectedDateMillis
                        if (millis != null) {
                            val cleanTitle = targetTitle.trim().ifBlank { CountdownRepository.DEFAULT_TITLE }
                            countdownRepo.saveCountdown(cleanTitle, millis)
                            Toast.makeText(context, "Hedef sınav tarihi kaydedildi.", Toast.LENGTH_SHORT).show()
                        }
                        showDatePicker = false
                    }
                ) {
                    Text("Kaydet")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Vazgeç")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                expandedHeight = 50.dp,
                title = { Text("Hedef Sınav Tarihi Belirle") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Özel Hedef & Sınav Tarihi",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                if (isSignedIn) {
                    val isPending = countdownState.isPendingSync
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = if (isPending) {
                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                        } else {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                        },
                        border = BorderStroke(
                            1.dp,
                            if (isPending) MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f)
                            else MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = if (isPending) Icons.Default.CloudSync else Icons.Default.CloudDone,
                                contentDescription = null,
                                tint = if (isPending) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = if (isPending) "Eşitleniyor" else "Bulutla Senkron",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isPending) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = targetTitle,
                onValueChange = { targetTitle = it },
                label = { Text("Hedef / Sınav Adı") },
                placeholder = { Text("Örn: YKS 2027, Final Sınavı, LGS") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = { showDatePicker = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.CalendarMonth, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Sınav Tarihini Takvimden Seç", fontWeight = FontWeight.Bold)
            }

            if (selectedMillis > 0L && targetTitle.trim() != countdownState.title.trim() && targetTitle.isNotBlank()) {
                OutlinedButton(
                    onClick = {
                        countdownRepo.saveCountdown(targetTitle.trim(), selectedMillis)
                        Toast.makeText(context, "Hedef başlığı güncellendi.", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Başlığı Kaydet", fontWeight = FontWeight.SemiBold)
                }
            }

            if (selectedMillis > 0L) {
                val formattedDate = SimpleDateFormat("dd MMMM yyyy", Locale("tr")).format(Date(selectedMillis))
                val diffDays = (selectedMillis - System.currentTimeMillis()) / (24 * 3600 * 1000)

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Mevcut Hedef: ${targetTitle.ifBlank { CountdownRepository.DEFAULT_TITLE }}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Tarih: $formattedDate (${if (diffDays >= 0) "$diffDays Gün Kaldı" else "Geçmiş Tarih"})",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                TextButton(
                    onClick = {
                        countdownRepo.clearCountdown()
                        targetTitle = ""
                        Toast.makeText(context, "Geri sayım temizlendi.", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.align(Alignment.End),
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Geri Sayımı Kaldır")
                }
            }
        }
    }
}
