package com.example.sharkmentor.ui.components

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

object FirstTimeTutorialPrefs {
    private const val PREFS_NAME = "shark_first_time_tutorial_prefs"
    private const val KEY_ELIGIBLE = "key_eligible"
    private const val KEY_COMPLETED = "key_completed"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun markEligibleForNewUserOrGuest(context: Context) {
        val p = prefs(context)
        if (!p.getBoolean(KEY_COMPLETED, false)) {
            p.edit().putBoolean(KEY_ELIGIBLE, true).apply()
        }
    }

    fun shouldShowTutorial(context: Context): Boolean {
        val p = prefs(context)
        return p.getBoolean(KEY_ELIGIBLE, false) && !p.getBoolean(KEY_COMPLETED, false)
    }

    fun markCompleted(context: Context) {
        prefs(context).edit()
            .putBoolean(KEY_COMPLETED, true)
            .putBoolean(KEY_ELIGIBLE, false)
            .apply()
    }
}

private data class TutorialStep(
    val title: String,
    val description: String,
    val icon: ImageVector
)

@Composable
fun FirstTimeTutorialOverlay(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var currentStep by remember { mutableIntStateOf(0) }

    val steps = remember {
        listOf(
            TutorialStep(
                title = "Kapsamlı Destek: Kronometre ve Koçluk Yolu",
                description = "Ayarlar > Kapsamlı Destek bölümünden bu özelliği açarak ana ekrana Kronometre ve adım adım ilerleyen Koçluk Yolu geçiş barını ekleyebilirsin.",
                icon = Icons.Default.Route
            ),
            TutorialStep(
                title = "Çalışma Saati ve Odağı Başlat Penceresi",
                description = "Günlük çalışma saatini ve başlıklarını Ayarlar'dan düzenleyebilir; odak sayacını başlatırken açılan pencereden başlığını seçip Odağı Başlat ile oturumu başlatabilirsin.",
                icon = Icons.Default.Flag
            ),
            TutorialStep(
                title = "Kitap Kampları ve Bölünmüş Video Stüdyosu",
                description = "Kütüphanedeki kitaplarını video kamplarıyla eşleştirip haftalık plana dağıtabilir; videoyu uygulama içinde izlerken alttan AI'ya soru sorup not alabilirsin.",
                icon = Icons.Default.PlayCircleOutline
            ),
            TutorialStep(
                title = "Kalem, Silgi ve Metin Kutusu",
                description = "Notlar ve PDF araç çubuğunda kalem veya silgi simgesine uzun basarak uç ve çizgi/alan silgisi seçebilir, çizim üzerine metin kutusu ekleyebilirsin.",
                icon = Icons.Default.Edit
            ),
            TutorialStep(
                title = "Kement ile Anında AI Çözüm",
                description = "Notlarda veya PDF üzerinde kementle bir soruyu çevreleyip doğrudan AI'ya çözdürebilir ya da ekran görüntüsü kırpabilirsin.",
                icon = Icons.Default.Star
            ),
            TutorialStep(
                title = "Yüzen Odak Sayacı ve Kısayollar",
                description = "Not Özellikleri'nden açılan taşınabilir odak sayacıyla çalışırken süreni izleyebilir; klavyede Ctrl+F ile arama, Ctrl+S ile kayıt yapabilirsin.",
                icon = Icons.Default.PlayArrow
            ),
            TutorialStep(
                title = "Üst Bar, Hızlı DM ve Kişiselleştirme",
                description = "Üst bar ayarlarından motivasyon sözleri ve animasyon seçebilir, DM simgesinden mesajlarına, Profil'den hedef sınavına hızla ulaşabilirsin.",
                icon = Icons.Default.Email
            )
        )
    }

    val finishTutorial = {
        FirstTimeTutorialPrefs.markCompleted(context)
        onDismiss()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.62f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .widthIn(max = 420.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                tonalElevation = 6.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Keşfet • ${currentStep + 1} / ${steps.size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    AnimatedContent(
                        targetState = currentStep,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "first_time_tutorial_step"
                    ) { stepIndex ->
                        val step = steps[stepIndex]
                        Column {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ) {
                                Icon(
                                    imageVector = step.icon,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .padding(10.dp)
                                        .size(22.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Text(
                                text = step.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = step.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(22.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            steps.indices.forEach { index ->
                                val isSelected = index == currentStep
                                Box(
                                    modifier = Modifier
                                        .size(width = if (isSelected) 18.dp else 7.dp, height = 7.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isSelected) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.outlineVariant
                                            }
                                        )
                                )
                            }
                        }

                        Button(
                            onClick = {
                                if (currentStep < steps.lastIndex) {
                                    currentStep++
                                } else {
                                    finishTutorial()
                                }
                            },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = if (currentStep < steps.lastIndex) "İleri" else "Başla",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Unobtrusive close action tucked quietly underneath the card
            Text(
                text = "Rehberi kapat",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.34f),
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = finishTutorial)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
    }
}
