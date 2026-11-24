package com.example.sharkmentor.ui.screens.leaguechat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.sharkmentor.core.service.FocusTimerService
import com.example.sharkmentor.core.timer.TimerMode
import com.example.sharkmentor.core.timer.TimerPhase
import com.example.sharkmentor.core.util.SessionManager
import com.example.sharkmentor.data.focus.FocusSessionManager
import kotlinx.coroutines.delay

/**
 * Full-screen overlay shown over chat screens whenever the user has an active focus session,
 * preventing distraction until the focus session completes or is ended.
 */
@Composable
fun FocusChatLockOverlay(
    onReturnToStudy: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val focusSessionManager = remember(context) { FocusSessionManager(context.applicationContext) }

    val timerState by FocusTimerService.timerStateFlow.collectAsStateWithLifecycle()
    val sessionManagerRunning by FocusSessionManager.isRunningFlow.collectAsStateWithLifecycle()
    val sessionTimerRunning by SessionManager.isTimerRunning.collectAsStateWithLifecycle()

    LaunchedEffect(timerState.timerPhase) {
        focusSessionManager.refreshActiveState()
    }

    val isTimerFocusing = timerState.timerPhase == TimerPhase.FOCUSING &&
        timerState.currentMode != TimerMode.BREAK

    val isFocusLocked = isTimerFocusing || (
        (sessionManagerRunning || sessionTimerRunning) &&
            timerState.timerPhase != TimerPhase.PAUSED &&
            timerState.timerPhase != TimerPhase.MANDATORY_BREAK &&
            timerState.currentMode != TimerMode.BREAK
        )

    var managerRemainingMs by remember { mutableLongStateOf(focusSessionManager.getRemainingMillis()) }
    LaunchedEffect(isFocusLocked) {
        while (isFocusLocked) {
            focusSessionManager.refreshActiveState()
            managerRemainingMs = focusSessionManager.getRemainingMillis()
            delay(1_000L)
        }
    }

    if (!isFocusLocked) return

    val activeTimeText = if (isTimerFocusing && (timerState.displayMillis > 0L || timerState.timeElapsedMillis > 0L)) {
        FocusTimerService.formatMillis(timerState.displayMillis)
    } else if (managerRemainingMs > 0L) {
        FocusTimerService.formatMillis(managerRemainingMs)
    } else {
        "Aktif Seans"
    }

    val activeSubject = remember(isFocusLocked, timerState.currentMode, timerState.activeSubject) {
        timerState.activeSubject.takeIf { it.isNotBlank() && !it.equals("Genel", ignoreCase = true) }
            ?: focusSessionManager.getActiveSubject().takeIf { it.isNotBlank() && !it.equals("Genel", ignoreCase = true) }
            ?: timerState.currentMode.displayName
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.94f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            )
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Sohbet Kilitli",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(34.dp)
                    )
                }

                Text(
                    text = "Odak Modundasın — Masada Kal",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "Aktif bir odak seansı devam ederken grup sohbeti ve mesajlaşma kilitlenir. Mola saatine kadar çalışmanıza devam edin.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Timer,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "$activeSubject • $activeTimeText",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Button(
                    onClick = onReturnToStudy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Timer,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Çalışmaya Dön", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
