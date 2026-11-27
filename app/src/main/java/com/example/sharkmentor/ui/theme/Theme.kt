package com.example.sharkmentor.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.example.sharkmentor.core.data.AppThemeMode
import com.example.sharkmentor.core.data.BlueLightFilterMode
import com.example.sharkmentor.core.data.CircadianScheduleMode
import com.example.sharkmentor.core.data.ThemePalette
import com.example.sharkmentor.core.data.ThemePreferences
import com.example.sharkmentor.ui.components.SpecialMemberConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.LocalTime

// ── Natural Dark Palettes (full M3 token coverage — zero default purple fallback) ──

// 1. SOOTY (Charcoal)
private val SootyDarkScheme = darkColorScheme(
    primary = Color(0xFFA6AAB2),
    onPrimary = Color(0xFF121315),
    primaryContainer = Color(0xFF2D3035),
    onPrimaryContainer = Color(0xFFECEEF1),
    secondary = Color(0xFFCFD3DA),
    onSecondary = Color(0xFF191B1E),
    secondaryContainer = Color(0xFF272A2E),
    onSecondaryContainer = Color(0xFFE3E6EA),
    tertiary = Color(0xFFB5BAC2),
    onTertiary = Color(0xFF17191C),
    tertiaryContainer = Color(0xFF24272B),
    onTertiaryContainer = Color(0xFFE0E3E8),
    background = Color(0xFF121315),
    onBackground = Color(0xFFE6E8EC),
    surface = Color(0xFF1A1B1E),
    onSurface = Color(0xFFE6E8EC),
    surfaceVariant = Color(0xFF24262A),
    onSurfaceVariant = Color(0xFFA4A8B0),
    surfaceTint = Color(0xFFA6AAB2),
    surfaceBright = Color(0xFF33363C),
    surfaceDim = Color(0xFF101113),
    surfaceContainerLowest = Color(0xFF0E0F11),
    surfaceContainerLow = Color(0xFF16171A),
    surfaceContainer = Color(0xFF1C1E22),
    surfaceContainerHigh = Color(0xFF23252A),
    surfaceContainerHighest = Color(0xFF2B2E34),
    outline = Color(0xFF4C5058),
    outlineVariant = Color(0xFF32353B),
    error = Color(0xFFF87171),
    onError = Color(0xFF2A0909),
    errorContainer = Color(0xFF451414),
    onErrorContainer = Color(0xFFFECACA)
)

// 2. COFFEE (Espresso)
private val CoffeeDarkScheme = darkColorScheme(
    primary = Color(0xFFD4A373),
    onPrimary = Color(0xFF1C1410),
    primaryContainer = Color(0xFF4A3424),
    onPrimaryContainer = Color(0xFFFDF0E2),
    secondary = Color(0xFFE6CCB2),
    onSecondary = Color(0xFF241913),
    secondaryContainer = Color(0xFF3D2E26),
    onSecondaryContainer = Color(0xFFFAEDCD),
    tertiary = Color(0xFFC89566),
    onTertiary = Color(0xFF1E140E),
    tertiaryContainer = Color(0xFF3B2B20),
    onTertiaryContainer = Color(0xFFF5E1CC),
    background = Color(0xFF1C1816),
    onBackground = Color(0xFFFAEDCD),
    surface = Color(0xFF261F1C),
    onSurface = Color(0xFFFAEDCD),
    surfaceVariant = Color(0xFF332924),
    onSurfaceVariant = Color(0xFFC9B0A0),
    surfaceTint = Color(0xFFD4A373),
    surfaceBright = Color(0xFF473B35),
    surfaceDim = Color(0xFF171311),
    surfaceContainerLowest = Color(0xFF161210),
    surfaceContainerLow = Color(0xFF211B18),
    surfaceContainer = Color(0xFF2B2320),
    surfaceContainerHigh = Color(0xFF342A26),
    surfaceContainerHighest = Color(0xFF3F342F),
    outline = Color(0xFF665347),
    outlineVariant = Color(0xFF443730),
    error = Color(0xFFF87171),
    onError = Color(0xFF2A0909),
    errorContainer = Color(0xFF471616),
    onErrorContainer = Color(0xFFFECACA)
)

// 3. OCEAN (Derin Deniz Turkuazı)
private val OceanDarkScheme = darkColorScheme(
    primary = Color(0xFF2EC4B6),
    onPrimary = Color(0xFF061E1D),
    primaryContainer = Color(0xFF12464B),
    onPrimaryContainer = Color(0xFFD2FAF6),
    secondary = Color(0xFF8BE5DD),
    onSecondary = Color(0xFF0A2229),
    secondaryContainer = Color(0xFF18394A),
    onSecondaryContainer = Color(0xFFCBF3F0),
    tertiary = Color(0xFF5BC0EB),
    onTertiary = Color(0xFF08202D),
    tertiaryContainer = Color(0xFF14384E),
    onTertiaryContainer = Color(0xFFD8F3FF),
    background = Color(0xFF0B1B2B),
    onBackground = Color(0xFFF1F8FC),
    surface = Color(0xFF12283C),
    onSurface = Color(0xFFF1F8FC),
    surfaceVariant = Color(0xFF19354D),
    onSurfaceVariant = Color(0xFF96D4E4),
    surfaceTint = Color(0xFF2EC4B6),
    surfaceBright = Color(0xFF294E6E),
    surfaceDim = Color(0xFF091624),
    surfaceContainerLowest = Color(0xFF071320),
    surfaceContainerLow = Color(0xFF0F2234),
    surfaceContainer = Color(0xFF142D43),
    surfaceContainerHigh = Color(0xFF1B3852),
    surfaceContainerHighest = Color(0xFF224360),
    outline = Color(0xFF396580),
    outlineVariant = Color(0xFF224259),
    error = Color(0xFFF87171),
    onError = Color(0xFF2A0909),
    errorContainer = Color(0xFF451519),
    onErrorContainer = Color(0xFFFECACA)
)

// 4. RAINY (Fırtına Grisi)
private val RainyDarkScheme = darkColorScheme(
    primary = Color(0xFF7FA4C4),
    onPrimary = Color(0xFF101923),
    primaryContainer = Color(0xFF2B4158),
    onPrimaryContainer = Color(0xFFE3EFFA),
    secondary = Color(0xFFB5C7D9),
    onSecondary = Color(0xFF18222C),
    secondaryContainer = Color(0xFF293442),
    onSecondaryContainer = Color(0xFFDCE6F0),
    tertiary = Color(0xFF8CA8C7),
    onTertiary = Color(0xFF121C27),
    tertiaryContainer = Color(0xFF283546),
    onTertiaryContainer = Color(0xFFE0ECF7),
    background = Color(0xFF161B24),
    onBackground = Color(0xFFF1F5F9),
    surface = Color(0xFF1F2633),
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = Color(0xFF2A3343),
    onSurfaceVariant = Color(0xFF9AA8BC),
    surfaceTint = Color(0xFF7FA4C4),
    surfaceBright = Color(0xFF3C485C),
    surfaceDim = Color(0xFF121720),
    surfaceContainerLowest = Color(0xFF11151D),
    surfaceContainerLow = Color(0xFF1B212D),
    surfaceContainer = Color(0xFF222936),
    surfaceContainerHigh = Color(0xFF2B3444),
    surfaceContainerHighest = Color(0xFF343F52),
    outline = Color(0xFF4E5E75),
    outlineVariant = Color(0xFF333E50),
    error = Color(0xFFF87171),
    onError = Color(0xFF2A0909),
    errorContainer = Color(0xFF451519),
    onErrorContainer = Color(0xFFFECACA)
)

// 5. FOREST (Sakin Çam Ormanı)
private val ForestDarkScheme = darkColorScheme(
    primary = Color(0xFF52B788),
    onPrimary = Color(0xFF081C12),
    primaryContainer = Color(0xFF1C4330),
    onPrimaryContainer = Color(0xFFD8F3E4),
    secondary = Color(0xFFA7DCB8),
    onSecondary = Color(0xFF0E2417),
    secondaryContainer = Color(0xFF1E3628),
    onSecondaryContainer = Color(0xFFD9F0E1),
    tertiary = Color(0xFF74C69D),
    onTertiary = Color(0xFF0B2115),
    tertiaryContainer = Color(0xFF1B3A2A),
    onTertiaryContainer = Color(0xFFDDF5E8),
    background = Color(0xFF0D1912),
    onBackground = Color(0xFFE8F5E9),
    surface = Color(0xFF14241B),
    onSurface = Color(0xFFE8F5E9),
    surfaceVariant = Color(0xFF1D3326),
    onSurfaceVariant = Color(0xFFA3C9A8),
    surfaceTint = Color(0xFF52B788),
    surfaceBright = Color(0xFF2D4C3A),
    surfaceDim = Color(0xFF0A140E),
    surfaceContainerLowest = Color(0xFF08120C),
    surfaceContainerLow = Color(0xFF111F17),
    surfaceContainer = Color(0xFF16281E),
    surfaceContainerHigh = Color(0xFF1E3528),
    surfaceContainerHighest = Color(0xFF264232),
    outline = Color(0xFF3F6650),
    outlineVariant = Color(0xFF274233),
    error = Color(0xFFF87171),
    onError = Color(0xFF2A0909),
    errorContainer = Color(0xFF451616),
    onErrorContainer = Color(0xFFFECACA)
)

// 6. DREAMS (Dusty Rose)
private val DreamsDarkScheme = darkColorScheme(
    primary = Color(0xFFF472B6),
    onPrimary = Color(0xFF240B19),
    primaryContainer = Color(0xFF592241),
    onPrimaryContainer = Color(0xFFFCE7F3),
    secondary = Color(0xFFF5B8D8),
    onSecondary = Color(0xFF27111E),
    secondaryContainer = Color(0xFF402536),
    onSecondaryContainer = Color(0xFFFCE7F3),
    tertiary = Color(0xFFE88CB5),
    onTertiary = Color(0xFF240D18),
    tertiaryContainer = Color(0xFF472133),
    onTertiaryContainer = Color(0xFFFBE1EC),
    background = Color(0xFF1A1318),
    onBackground = Color(0xFFFCE7F3),
    surface = Color(0xFF261A22),
    onSurface = Color(0xFFFCE7F3),
    surfaceVariant = Color(0xFF34232E),
    onSurfaceVariant = Color(0xFFD8B4C8),
    surfaceTint = Color(0xFFF472B6),
    surfaceBright = Color(0xFF493341),
    surfaceDim = Color(0xFF160F14),
    surfaceContainerLowest = Color(0xFF140E12),
    surfaceContainerLow = Color(0xFF20161D),
    surfaceContainer = Color(0xFF281B24),
    surfaceContainerHigh = Color(0xFF33232E),
    surfaceContainerHighest = Color(0xFF3F2C39),
    outline = Color(0xFF6B4A5F),
    outlineVariant = Color(0xFF47303F),
    error = Color(0xFFF87171),
    onError = Color(0xFF2A0909),
    errorContainer = Color(0xFF4A151B),
    onErrorContainer = Color(0xFFFECACA)
)

// 7. SAKURA (Cherry Blossom)
private val SakuraDarkScheme = darkColorScheme(
    primary = Color(0xFFFDA4AF),
    onPrimary = Color(0xFF290F14),
    primaryContainer = Color(0xFF5D2630),
    onPrimaryContainer = Color(0xFFFFE4E6),
    secondary = Color(0xFFFAC2CA),
    onSecondary = Color(0xFF2A1418),
    secondaryContainer = Color(0xFF45272D),
    onSecondaryContainer = Color(0xFFFFE4E6),
    tertiary = Color(0xFFF9A8D4),
    onTertiary = Color(0xFF2A111F),
    tertiaryContainer = Color(0xFF4B2438),
    onTertiaryContainer = Color(0xFFFFE4F2),
    background = Color(0xFF1E1517),
    onBackground = Color(0xFFFFE4E6),
    surface = Color(0xFF2A1D21),
    onSurface = Color(0xFFFFE4E6),
    surfaceVariant = Color(0xFF39272C),
    onSurfaceVariant = Color(0xFFE2B2B8),
    surfaceTint = Color(0xFFFDA4AF),
    surfaceBright = Color(0xFF4E373E),
    surfaceDim = Color(0xFF191114),
    surfaceContainerLowest = Color(0xFF171012),
    surfaceContainerLow = Color(0xFF24181B),
    surfaceContainer = Color(0xFF2D1E23),
    surfaceContainerHigh = Color(0xFF38262C),
    surfaceContainerHighest = Color(0xFF442F36),
    outline = Color(0xFF734F57),
    outlineVariant = Color(0xFF4D343A),
    error = Color(0xFFF87171),
    onError = Color(0xFF2A0909),
    errorContainer = Color(0xFF4D161A),
    onErrorContainer = Color(0xFFFECACA)
)

// 8. AUTUMN (Amber Orange)
private val AutumnDarkScheme = darkColorScheme(
    primary = Color(0xFFFB923C),
    onPrimary = Color(0xFF261205),
    primaryContainer = Color(0xFF5C2B10),
    onPrimaryContainer = Color(0xFFFFEDD5),
    secondary = Color(0xFFFCD34D),
    onSecondary = Color(0xFF261B06),
    secondaryContainer = Color(0xFF463119),
    onSecondaryContainer = Color(0xFFFEF3C7),
    tertiary = Color(0xFFE58E4F),
    onTertiary = Color(0xFF241307),
    tertiaryContainer = Color(0xFF472B19),
    onTertiaryContainer = Color(0xFFFDE6D2),
    background = Color(0xFF181310),
    onBackground = Color(0xFFFDF2DC),
    surface = Color(0xFF241B15),
    onSurface = Color(0xFFFDF2DC),
    surfaceVariant = Color(0xFF33251D),
    onSurfaceVariant = Color(0xFFD6B090),
    surfaceTint = Color(0xFFFB923C),
    surfaceBright = Color(0xFF49362B),
    surfaceDim = Color(0xFF140F0C),
    surfaceContainerLowest = Color(0xFF120E0B),
    surfaceContainerLow = Color(0xFF1E1611),
    surfaceContainer = Color(0xFF261C16),
    surfaceContainerHigh = Color(0xFF32251D),
    surfaceContainerHighest = Color(0xFF3E2E24),
    outline = Color(0xFF6B503E),
    outlineVariant = Color(0xFF473529),
    error = Color(0xFFF87171),
    onError = Color(0xFF2A0909),
    errorContainer = Color(0xFF4A1514),
    onErrorContainer = Color(0xFFFECACA)
)

// 9. GALATASARAY (Sarı & Kırmızı Özel)
private val GalatasarayDarkScheme = darkColorScheme(
    primary = Color(0xFFFDB912),
    onPrimary = Color(0xFF2B1900),
    primaryContainer = Color(0xFFA90432),
    onPrimaryContainer = Color(0xFFFFE5EA),
    secondary = Color(0xFFD91E36),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFF5E0B1B),
    onSecondaryContainer = Color(0xFFFFD9DF),
    tertiary = Color(0xFFFFD56B),
    onTertiary = Color(0xFF2B1900),
    tertiaryContainer = Color(0xFF4D3600),
    onTertiaryContainer = Color(0xFFFFE8A6),
    background = Color(0xFF16080B),
    onBackground = Color(0xFFFFF8F6),
    surface = Color(0xFF220D12),
    onSurface = Color(0xFFFFF8F6),
    surfaceVariant = Color(0xFF33141B),
    onSurfaceVariant = Color(0xFFE4C4C9),
    surfaceTint = Color(0xFFFDB912),
    surfaceBright = Color(0xFF491E28),
    surfaceDim = Color(0xFF120609),
    surfaceContainerLowest = Color(0xFF100507),
    surfaceContainerLow = Color(0xFF1C0A0E),
    surfaceContainer = Color(0xFF270F15),
    surfaceContainerHigh = Color(0xFF32141C),
    surfaceContainerHighest = Color(0xFF3E1922),
    outline = Color(0xFF7D535B),
    outlineVariant = Color(0xFF4F2A32),
    error = Color(0xFFF87171),
    onError = Color(0xFF2A0909),
    errorContainer = Color(0xFF4A1514),
    onErrorContainer = Color(0xFFFECACA)
)

// ── Natural Light Palettes (dedicated per-theme containers — purple belongs ONLY to Blueberry) ──

private val SootyLightScheme = lightColorScheme(
    primary = Color(0xFF45484F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE5E7EA),
    onPrimaryContainer = Color(0xFF1A1C1F),
    secondary = Color(0xFF5D6169),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEBEDF0),
    onSecondaryContainer = Color(0xFF22252A),
    tertiary = Color(0xFF6E727A),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE8EAED),
    onTertiaryContainer = Color(0xFF24272B),
    background = Color(0xFFF7F8F9),
    onBackground = Color(0xFF141618),
    surface = Color.White,
    onSurface = Color(0xFF141618),
    surfaceVariant = Color(0xFFEBEDEF),
    onSurfaceVariant = Color(0xFF52565E),
    surfaceTint = Color(0xFF45484F),
    surfaceBright = Color.White,
    surfaceDim = Color(0xFFDCE0E4),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF3F4F6),
    surfaceContainer = Color(0xFFECEEF0),
    surfaceContainerHigh = Color(0xFFE4E6E9),
    surfaceContainerHighest = Color(0xFFDCDFE3),
    outline = Color(0xFF9499A1),
    outlineVariant = Color(0xFFD4D7DC),
    error = Color(0xFFDC2626),
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D)
)

private val CoffeeLightScheme = lightColorScheme(
    primary = Color(0xFF8C5830),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF5E4D3),
    onPrimaryContainer = Color(0xFF331D0C),
    secondary = Color(0xFF6E4E3B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF2E6DC),
    onSecondaryContainer = Color(0xFF2D1D14),
    tertiary = Color(0xFFA66F42),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF6E7D8),
    onTertiaryContainer = Color(0xFF3A220F),
    background = Color(0xFFFAF7F4),
    onBackground = Color(0xFF1E1713),
    surface = Color.White,
    onSurface = Color(0xFF1E1713),
    surfaceVariant = Color(0xFFF0E8E1),
    onSurfaceVariant = Color(0xFF635247),
    surfaceTint = Color(0xFF8C5830),
    surfaceBright = Color.White,
    surfaceDim = Color(0xFFE5D9CE),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF7F2ED),
    surfaceContainer = Color(0xFFF2EAE3),
    surfaceContainerHigh = Color(0xFFEBE0D7),
    surfaceContainerHighest = Color(0xFFE2D4C8),
    outline = Color(0xFFAD9685),
    outlineVariant = Color(0xFFDFD0C4),
    error = Color(0xFFDC2626),
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D)
)

private val OceanLightScheme = lightColorScheme(
    primary = Color(0xFF0F8B80),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD2F4F0),
    onPrimaryContainer = Color(0xFF063330),
    secondary = Color(0xFF1E607A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD8EDF5),
    onSecondaryContainer = Color(0xFF0B2B38),
    tertiary = Color(0xFF0284C7),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD6EFFC),
    onTertiaryContainer = Color(0xFF082F49),
    background = Color(0xFFF4FAFA),
    onBackground = Color(0xFF0B1B2B),
    surface = Color.White,
    onSurface = Color(0xFF0B1B2B),
    surfaceVariant = Color(0xFFE4F1F4),
    onSurfaceVariant = Color(0xFF415A6B),
    surfaceTint = Color(0xFF0F8B80),
    surfaceBright = Color.White,
    surfaceDim = Color(0xFFD7E6EB),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF0F7F8),
    surfaceContainer = Color(0xFFE8F2F4),
    surfaceContainerHigh = Color(0xFFDFECF0),
    surfaceContainerHighest = Color(0xFFD3E4EA),
    outline = Color(0xFF8AA6B5),
    outlineVariant = Color(0xFFCADEE6),
    error = Color(0xFFDC2626),
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D)
)

private val RainyLightScheme = lightColorScheme(
    primary = Color(0xFF3B6287),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE8F5),
    onPrimaryContainer = Color(0xFF132638),
    secondary = Color(0xFF526A80),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE3ECF5),
    onSecondaryContainer = Color(0xFF1D2B38),
    tertiary = Color(0xFF4F7B9E),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE0ECF7),
    onTertiaryContainer = Color(0xFF182D40),
    background = Color(0xFFF5F8FB),
    onBackground = Color(0xFF141B24),
    surface = Color.White,
    onSurface = Color(0xFF141B24),
    surfaceVariant = Color(0xFFE6EDF5),
    onSurfaceVariant = Color(0xFF4B5A6B),
    surfaceTint = Color(0xFF3B6287),
    surfaceBright = Color.White,
    surfaceDim = Color(0xFFD9E2EC),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF1F5F9),
    surfaceContainer = Color(0xFFEAF0F6),
    surfaceContainerHigh = Color(0xFFE1E9F2),
    surfaceContainerHighest = Color(0xFFD6E0EC),
    outline = Color(0xFF8FA0B3),
    outlineVariant = Color(0xFFCFDCE8),
    error = Color(0xFFDC2626),
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D)
)

private val ForestLightScheme = lightColorScheme(
    primary = Color(0xFF266644),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD5EEDF),
    onPrimaryContainer = Color(0xFF0B2918),
    secondary = Color(0xFF3F7356),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDFEFE5),
    onSecondaryContainer = Color(0xFF153021),
    tertiary = Color(0xFF4E8A63),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE2F2E7),
    onTertiaryContainer = Color(0xFF163320),
    background = Color(0xFFF4F9F5),
    onBackground = Color(0xFF0F1F15),
    surface = Color.White,
    onSurface = Color(0xFF0F1F15),
    surfaceVariant = Color(0xFFE4EFE7),
    onSurfaceVariant = Color(0xFF455E4E),
    surfaceTint = Color(0xFF266644),
    surfaceBright = Color.White,
    surfaceDim = Color(0xFFD8E6DD),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF0F6F2),
    surfaceContainer = Color(0xFFE8F1EB),
    surfaceContainerHigh = Color(0xFFDFEAE3),
    surfaceContainerHighest = Color(0xFFD4E3D9),
    outline = Color(0xFF8AA493),
    outlineVariant = Color(0xFFCCDDD2),
    error = Color(0xFFDC2626),
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D)
)

private val DreamsLightScheme = lightColorScheme(
    primary = Color(0xFFB83276),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFCE2EF),
    onPrimaryContainer = Color(0xFF420C28),
    secondary = Color(0xFF8C4A6B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF7E5EE),
    onSecondaryContainer = Color(0xFF381627),
    tertiary = Color(0xFFB5507A),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFBE2EC),
    onTertiaryContainer = Color(0xFF401427),
    background = Color(0xFFFDF6F9),
    onBackground = Color(0xFF211319),
    surface = Color.White,
    onSurface = Color(0xFF211319),
    surfaceVariant = Color(0xFFF6E6EE),
    onSurfaceVariant = Color(0xFF6B4C5B),
    surfaceTint = Color(0xFFB83276),
    surfaceBright = Color.White,
    surfaceDim = Color(0xFFECD5DF),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFCF2F5),
    surfaceContainer = Color(0xFFF7E8EE),
    surfaceContainerHigh = Color(0xFFF1DCE5),
    surfaceContainerHighest = Color(0xFFEAD0DC),
    outline = Color(0xFFB590A1),
    outlineVariant = Color(0xFFE6CDD8),
    error = Color(0xFFDC2626),
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D)
)

private val SakuraLightScheme = lightColorScheme(
    primary = Color(0xFFC74B62),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE3E8),
    onPrimaryContainer = Color(0xFF47101B),
    secondary = Color(0xFF9E5362),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFCE7EB),
    onSecondaryContainer = Color(0xFF3D171F),
    tertiary = Color(0xFFD66079),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE5EB),
    onTertiaryContainer = Color(0xFF471420),
    background = Color(0xFFFFF7F8),
    onBackground = Color(0xFF241418),
    surface = Color.White,
    onSurface = Color(0xFF241418),
    surfaceVariant = Color(0xFFF9E8EB),
    onSurfaceVariant = Color(0xFF6E4E55),
    surfaceTint = Color(0xFFC74B62),
    surfaceBright = Color.White,
    surfaceDim = Color(0xFFEFD8DD),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFCF2F4),
    surfaceContainer = Color(0xFFF8EAED),
    surfaceContainerHigh = Color(0xFFF3DFE3),
    surfaceContainerHighest = Color(0xFFECD2D8),
    outline = Color(0xFFBA929B),
    outlineVariant = Color(0xFFEBD0D6),
    error = Color(0xFFDC2626),
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D)
)

private val AutumnLightScheme = lightColorScheme(
    primary = Color(0xFFC2410C),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFEDD5),
    onPrimaryContainer = Color(0xFF431407),
    secondary = Color(0xFF9A5518),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFAEBDC),
    onSecondaryContainer = Color(0xFF3B1F06),
    tertiary = Color(0xFFB45309),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFEF3C7),
    onTertiaryContainer = Color(0xFF451A03),
    background = Color(0xFFFDF8F3),
    onBackground = Color(0xFF21160E),
    surface = Color.White,
    onSurface = Color(0xFF21160E),
    surfaceVariant = Color(0xFFF5EAE0),
    onSurfaceVariant = Color(0xFF6B5342),
    surfaceTint = Color(0xFFC2410C),
    surfaceBright = Color.White,
    surfaceDim = Color(0xFFE8DACD),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFAF3EC),
    surfaceContainer = Color(0xFFF5ECE3),
    surfaceContainerHigh = Color(0xFFEEE2D7),
    surfaceContainerHighest = Color(0xFFE6D6C8),
    outline = Color(0xFFB59884),
    outlineVariant = Color(0xFFE6D4C5),
    error = Color(0xFFDC2626),
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D)
)

private val GalatasarayLightScheme = lightColorScheme(
    primary = Color(0xFFA90432),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFE6A1),
    onPrimaryContainer = Color(0xFF3B2200),
    secondary = Color(0xFFC98F00),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFF0C2),
    onSecondaryContainer = Color(0xFF3B2800),
    tertiary = Color(0xFF8C142B),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFD9DF),
    onTertiaryContainer = Color(0xFF470614),
    background = Color(0xFFFFFBF7),
    onBackground = Color(0xFF1F1215),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1F1215),
    surfaceVariant = Color(0xFFFCEEEF),
    onSurfaceVariant = Color(0xFF5E4348),
    surfaceTint = Color(0xFFA90432),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFF2DCE0),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFF6F4),
    surfaceContainer = Color(0xFFFDF0F0),
    surfaceContainerHigh = Color(0xFFF9E6E7),
    surfaceContainerHighest = Color(0xFFF4DCDF),
    outline = Color(0xFF9C7B81),
    outlineVariant = Color(0xFFE7CFD3),
    error = Color(0xFFDC2626),
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D)
)

// 10. BLUEBERRY (Yaban Mersini — dedicated purple/lavender palette)
private val BlueberryDarkScheme = darkColorScheme(
    background = Color(0xFF0E0B1E),
    onBackground = Color(0xFFF5F3FF),
    surface = Color(0xFF171230),
    onSurface = Color(0xFFF5F3FF),
    surfaceVariant = Color(0xFF241B47),
    onSurfaceVariant = Color(0xFFC4B5FD),
    primary = Color(0xFFA855F7),
    onPrimary = Color(0xFF1B0536),
    primaryContainer = Color(0xFF4C1D95),
    onPrimaryContainer = Color(0xFFF3E8FF),
    secondary = Color(0xFF7C3AED),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFF2E1065),
    onSecondaryContainer = Color(0xFFE9D5FF),
    tertiary = Color(0xFFC084FC),
    onTertiary = Color(0xFF240742),
    tertiaryContainer = Color(0xFF3E1870),
    onTertiaryContainer = Color(0xFFF3E8FF),
    surfaceTint = Color(0xFFA855F7),
    surfaceBright = Color(0xFF382A6B),
    surfaceDim = Color(0xFF0A0817),
    surfaceContainerLowest = Color(0xFF090714),
    surfaceContainerLow = Color(0xFF14102A),
    surfaceContainer = Color(0xFF1B1538),
    surfaceContainerHigh = Color(0xFF251D4A),
    surfaceContainerHighest = Color(0xFF30265E),
    outline = Color(0xFF6B57A8),
    outlineVariant = Color(0xFF3B2C66),
    error = Color(0xFFF87171),
    onError = Color(0xFF2A0909),
    errorContainer = Color(0xFF45141A),
    onErrorContainer = Color(0xFFFECACA)
)

private val BlueberryLightScheme = lightColorScheme(
    background = Color(0xFFFAF8FF),
    onBackground = Color(0xFF1E1B4B),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1E1B4B),
    surfaceVariant = Color(0xFFEDE9FE),
    onSurfaceVariant = Color(0xFF4C1D95),
    primary = Color(0xFF7E22CE),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDDD6FE),
    onPrimaryContainer = Color(0xFF2E1065),
    secondary = Color(0xFF312E81),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE0E7FF),
    onSecondaryContainer = Color(0xFF1E1B4B),
    tertiary = Color(0xFF9333EA),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF3E8FF),
    onTertiaryContainer = Color(0xFF3B0764),
    surfaceTint = Color(0xFF7E22CE),
    surfaceBright = Color(0xFFFCFAFF),
    surfaceDim = Color(0xFFE0D7F8),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF5F2FF),
    surfaceContainer = Color(0xFFEEE8FF),
    surfaceContainerHigh = Color(0xFFE5DCFE),
    surfaceContainerHighest = Color(0xFFDBD0FD),
    outline = Color(0xFF9E87D9),
    outlineVariant = Color(0xFFC4B5FD),
    error = Color(0xFFDC2626),
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D)
)

// ── Zen Pure Focus Monochrome Scheme ────────────────────────────────────────

private val ZenMonochromeScheme = darkColorScheme(
    primary = Color.White,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF262626),
    onPrimaryContainer = Color.White,
    secondary = Color(0xFFBDBDBD),
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF1F1F1F),
    onSecondaryContainer = Color(0xFFE5E5E5),
    tertiary = Color(0xFF9E9E9E),
    onTertiary = Color.Black,
    tertiaryContainer = Color(0xFF1C1C1C),
    onTertiaryContainer = Color(0xFFD4D4D4),
    background = Color.Black,
    onBackground = Color.White,
    surface = Color(0xFF0E0E0E),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF1A1A1A),
    onSurfaceVariant = Color(0xFFA8A8A8),
    surfaceTint = Color.White,
    surfaceBright = Color(0xFF2E2E2E),
    surfaceDim = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0E0E0E),
    surfaceContainer = Color(0xFF141414),
    surfaceContainerHigh = Color(0xFF1D1D1D),
    surfaceContainerHighest = Color(0xFF262626),
    outline = Color(0xFF525252),
    outlineVariant = Color(0xFF2E2E2E),
    error = Color(0xFFEF4444),
    onError = Color.White,
    errorContainer = Color(0xFF3B1212),
    onErrorContainer = Color(0xFFFCA5A5)
)

// 11. CHRISTMAS (Çam Yeşili & Altın)
private val ChristmasDarkScheme = darkColorScheme(
    background = Color(0xFF0E1713),
    onBackground = Color(0xFFF4F7F5),
    surface = Color(0xFF15231D),
    onSurface = Color(0xFFF4F7F5),
    surfaceVariant = Color(0xFF1F322A),
    onSurfaceVariant = Color(0xFFB8C9C0),
    primary = Color(0xFFE5B869),
    onPrimary = Color(0xFF241904),
    primaryContainer = Color(0xFF1E4635),
    onPrimaryContainer = Color(0xFFE4F4EC),
    secondary = Color(0xFFD65A5A),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFF472022),
    onSecondaryContainer = Color(0xFFFCE4E4),
    tertiary = Color(0xFF6BCB97),
    onTertiary = Color(0xFF092417),
    tertiaryContainer = Color(0xFF1B3B2D),
    onTertiaryContainer = Color(0xFFD9F3E6),
    surfaceTint = Color(0xFFE5B869),
    surfaceBright = Color(0xFF2C453B),
    surfaceDim = Color(0xFF0A120F),
    surfaceContainerLowest = Color(0xFF09100D),
    surfaceContainerLow = Color(0xFF121E19),
    surfaceContainer = Color(0xFF182821),
    surfaceContainerHigh = Color(0xFF21352C),
    surfaceContainerHighest = Color(0xFF2A4237),
    outline = Color(0xFF4E6B5E),
    outlineVariant = Color(0xFF2E473C),
    error = Color(0xFFF87171),
    onError = Color(0xFF2A0909),
    errorContainer = Color(0xFF471616),
    onErrorContainer = Color(0xFFFECACA)
)

private val ChristmasLightScheme = lightColorScheme(
    background = Color(0xFFFAFBF9),
    onBackground = Color(0xFF13221B),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF13221B),
    surfaceVariant = Color(0xFFE6F0EA),
    onSurfaceVariant = Color(0xFF465C51),
    primary = Color(0xFF1B5E3E),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD4EADF),
    onPrimaryContainer = Color(0xFF0C2E1E),
    secondary = Color(0xFFB33939),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF9E2E2),
    onSecondaryContainer = Color(0xFF471212),
    tertiary = Color(0xFFA67C2E),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF6ECD5),
    onTertiaryContainer = Color(0xFF3D2B07),
    surfaceTint = Color(0xFF1B5E3E),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFD6E5DC),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF1F7F4),
    surfaceContainer = Color(0xFFE8F2EC),
    surfaceContainerHigh = Color(0xFFDDECE3),
    surfaceContainerHighest = Color(0xFFD1E4D9),
    outline = Color(0xFF83A091),
    outlineVariant = Color(0xFFC5D6CD),
    error = Color(0xFFDC2626),
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D)
)

// 12. PAPER (Sıcak Kağıt & Açık Kahve Kutucuklar)
private val PaperLightScheme = lightColorScheme(
    background = Color(0xFFFAF6EE),
    onBackground = Color(0xFF231D17),
    surface = Color(0xFFF6F0E4),
    onSurface = Color(0xFF231D17),
    surfaceVariant = Color(0xFFE9DEC9),
    onSurfaceVariant = Color(0xFF5D4D3E),
    primary = Color(0xFF6B4C33),
    onPrimary = Color(0xFFFFFBF4),
    primaryContainer = Color(0xFFEADBC6),
    onPrimaryContainer = Color(0xFF2E1E10),
    secondary = Color(0xFF7D6248),
    onSecondary = Color(0xFFFFFBF4),
    secondaryContainer = Color(0xFFEDE0CE),
    onSecondaryContainer = Color(0xFF332518),
    tertiary = Color(0xFF8C6B4A),
    onTertiary = Color(0xFFFFFBF4),
    tertiaryContainer = Color(0xFFF1E5D3),
    onTertiaryContainer = Color(0xFF3A2816),
    surfaceTint = Color(0xFF6B4C33),
    surfaceBright = Color(0xFFFFFDF8),
    surfaceDim = Color(0xFFE0D2BC),
    surfaceContainerLowest = Color(0xFFFFFDF8),
    surfaceContainerLow = Color(0xFFF3E9D8),
    surfaceContainer = Color(0xFFECE0CC),
    surfaceContainerHigh = Color(0xFFE4D5BC),
    surfaceContainerHighest = Color(0xFFDBC9AC),
    outline = Color(0xFFA9937A),
    outlineVariant = Color(0xFFD9C9B2),
    error = Color(0xFFDC2626),
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D)
)

private val PaperDarkScheme = darkColorScheme(
    background = Color(0xFF161411),
    onBackground = Color(0xFFEDE6D8),
    surface = Color(0xFF1E1B17),
    onSurface = Color(0xFFEDE6D8),
    surfaceVariant = Color(0xFF29251F),
    onSurfaceVariant = Color(0xFFB3AA9B),
    primary = Color(0xFFD6C7AE),
    onPrimary = Color(0xFF1A1713),
    primaryContainer = Color(0xFF3A342A),
    onPrimaryContainer = Color(0xFFF2EAD9),
    secondary = Color(0xFFB5A790),
    onSecondary = Color(0xFF1E1B16),
    secondaryContainer = Color(0xFF302B23),
    onSecondaryContainer = Color(0xFFE8DFD1),
    tertiary = Color(0xFFC49E6C),
    onTertiary = Color(0xFF23180B),
    tertiaryContainer = Color(0xFF3B2E1E),
    onTertiaryContainer = Color(0xFFF3E2CA),
    surfaceTint = Color(0xFFD6C7AE),
    surfaceBright = Color(0xFF3F3930),
    surfaceDim = Color(0xFF12100E),
    surfaceContainerLowest = Color(0xFF110F0D),
    surfaceContainerLow = Color(0xFF1A1714),
    surfaceContainer = Color(0xFF221E1A),
    surfaceContainerHigh = Color(0xFF2C2721),
    surfaceContainerHighest = Color(0xFF363029),
    outline = Color(0xFF6B6357),
    outlineVariant = Color(0xFF3B362E),
    error = Color(0xFFF87171),
    onError = Color(0xFF2A0909),
    errorContainer = Color(0xFF471616),
    onErrorContainer = Color(0xFFFECACA)
)

// 13. OBSIDIAN (Saf Monokrom Taş & Gümüş)
private val ObsidianDarkScheme = darkColorScheme(
    background = Color(0xFF000000),
    onBackground = Color(0xFFF4F4F4),
    surface = Color(0xFF090909),
    onSurface = Color(0xFFF4F4F4),
    surfaceVariant = Color(0xFF141414),
    onSurfaceVariant = Color(0xFFA3A3A3),
    primary = Color(0xFFE5E5E5),
    onPrimary = Color(0xFF050505),
    primaryContainer = Color(0xFF212121),
    onPrimaryContainer = Color(0xFFF5F5F5),
    secondary = Color(0xFFA3A3A3),
    onSecondary = Color(0xFF0A0A0A),
    secondaryContainer = Color(0xFF1A1A1A),
    onSecondaryContainer = Color(0xFFE5E5E5),
    tertiary = Color(0xFFD4D4D4),
    onTertiary = Color(0xFF0C0C0C),
    tertiaryContainer = Color(0xFF1F1F1F),
    onTertiaryContainer = Color(0xFFEDEDED),
    surfaceTint = Color(0xFFE5E5E5),
    surfaceBright = Color(0xFF2A2A2A),
    surfaceDim = Color(0xFF000000),
    surfaceContainerLowest = Color(0xFF040404),
    surfaceContainerLow = Color(0xFF0D0D0D),
    surfaceContainer = Color(0xFF141414),
    surfaceContainerHigh = Color(0xFF1C1C1C),
    surfaceContainerHighest = Color(0xFF252525),
    outline = Color(0xFF4A4A4A),
    outlineVariant = Color(0xFF262626),
    error = Color(0xFFF87171),
    onError = Color(0xFF2A0909),
    errorContainer = Color(0xFF451414),
    onErrorContainer = Color(0xFFFECACA)
)

private val ObsidianLightScheme = lightColorScheme(
    background = Color(0xFFFAFAFA),
    onBackground = Color(0xFF0F0F0F),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0F0F0F),
    surfaceVariant = Color(0xFFEEEEEE),
    onSurfaceVariant = Color(0xFF525252),
    primary = Color(0xFF171717),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE5E5E5),
    onPrimaryContainer = Color(0xFF0A0A0A),
    secondary = Color(0xFF525252),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFECECEC),
    onSecondaryContainer = Color(0xFF1A1A1A),
    tertiary = Color(0xFF404040),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE8E8E8),
    onTertiaryContainer = Color(0xFF171717),
    surfaceTint = Color(0xFF171717),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFDDDDDD),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF5F5F5),
    surfaceContainer = Color(0xFFEFEFEF),
    surfaceContainerHigh = Color(0xFFE7E7E7),
    surfaceContainerHighest = Color(0xFFDFDFDF),
    outline = Color(0xFFA3A3A3),
    outlineVariant = Color(0xFFE0E0E0),
    error = Color(0xFFDC2626),
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D)
)

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

internal fun paletteSchemeFor(palette: ThemePalette, isDark: Boolean): ColorScheme {
    return if (isDark) {
        when (palette) {
            ThemePalette.SOOTY -> SootyDarkScheme
            ThemePalette.COFFEE -> CoffeeDarkScheme
            ThemePalette.OCEAN -> OceanDarkScheme
            ThemePalette.RAINY -> RainyDarkScheme
            ThemePalette.FOREST -> ForestDarkScheme
            ThemePalette.DREAMS -> DreamsDarkScheme
            ThemePalette.SAKURA -> SakuraDarkScheme
            ThemePalette.AUTUMN -> AutumnDarkScheme
            ThemePalette.GALATASARAY -> GalatasarayDarkScheme
            ThemePalette.CHRISTMAS -> ChristmasDarkScheme
            ThemePalette.BLUEBERRY -> BlueberryDarkScheme
            ThemePalette.PAPER -> PaperDarkScheme
            ThemePalette.OBSIDIAN -> ObsidianDarkScheme
        }
    } else {
        when (palette) {
            ThemePalette.SOOTY -> SootyLightScheme
            ThemePalette.COFFEE -> CoffeeLightScheme
            ThemePalette.OCEAN -> OceanLightScheme
            ThemePalette.RAINY -> RainyLightScheme
            ThemePalette.FOREST -> ForestLightScheme
            ThemePalette.DREAMS -> DreamsLightScheme
            ThemePalette.SAKURA -> SakuraLightScheme
            ThemePalette.AUTUMN -> AutumnLightScheme
            ThemePalette.GALATASARAY -> GalatasarayLightScheme
            ThemePalette.BLUEBERRY -> BlueberryLightScheme
            ThemePalette.CHRISTMAS -> ChristmasLightScheme
            ThemePalette.PAPER -> PaperLightScheme
            ThemePalette.OBSIDIAN -> ObsidianLightScheme
        }
    }
}

private val LocalBlueLightApplied = compositionLocalOf { false }
private val EyeComfortAmberTint = Color(0xFFFF8C1A)

@Composable
fun SharkMentorTheme(
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    palette: ThemePalette = ThemePalette.OCEAN,
    isZenMode: Boolean = false,
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    val context = LocalContext.current
    val themePrefs = remember(context, view.isInEditMode) {
        if (view.isInEditMode) null
        else runCatching { ThemePreferences.initialize(context.applicationContext) }.getOrNull()
    }

    val circadianScheduleMode by remember(themePrefs) {
        themePrefs?.circadianScheduleMode ?: MutableStateFlow(CircadianScheduleMode.LOCATION_SUNSET)
    }.collectAsState()
    val circadianStartMin by remember(themePrefs) {
        themePrefs?.circadianStartMinute ?: MutableStateFlow(20 * 60)
    }.collectAsState()
    val circadianEndMin by remember(themePrefs) {
        themePrefs?.circadianEndMinute ?: MutableStateFlow(7 * 60)
    }.collectAsState()

    val blueLightMode by remember(themePrefs) {
        themePrefs?.blueLightMode ?: MutableStateFlow(BlueLightFilterMode.OFF)
    }.collectAsState()
    val blueLightIntensity by remember(themePrefs) {
        themePrefs?.blueLightIntensity ?: MutableStateFlow(0.22f)
    }.collectAsState()
    val blueLightStartMin by remember(themePrefs) {
        themePrefs?.blueLightStartMinute ?: MutableStateFlow(21 * 60)
    }.collectAsState()
    val blueLightEndMin by remember(themePrefs) {
        themePrefs?.blueLightEndMinute ?: MutableStateFlow(7 * 60)
    }.collectAsState()

    val needsClockTick = themeMode == AppThemeMode.CIRCADIAN ||
        blueLightMode == BlueLightFilterMode.LOCATION_SUNSET ||
        blueLightMode == BlueLightFilterMode.CUSTOM_TIME

    val nowMinuteOfDay by produceState(
        initialValue = LocalTime.now().let { it.hour * 60 + it.minute },
        key1 = needsClockTick
    ) {
        if (needsClockTick) {
            while (true) {
                value = LocalTime.now().let { it.hour * 60 + it.minute }
                delay(30_000L)
            }
        }
    }

    val circadianNight = remember(
        themePrefs,
        themeMode,
        circadianScheduleMode,
        circadianStartMin,
        circadianEndMin,
        nowMinuteOfDay
    ) {
        themePrefs?.isCircadianNightNow(nowMinuteOfDay)
            ?: com.example.sharkmentor.core.data.isCircadianNightHour(nowMinuteOfDay / 60)
    }

    val isBlueLightActive = remember(
        themePrefs,
        blueLightMode,
        blueLightStartMin,
        blueLightEndMin,
        nowMinuteOfDay
    ) {
        themePrefs?.isBlueLightFilterActiveNow(nowMinuteOfDay) ?: (blueLightMode == BlueLightFilterMode.MANUAL_ON)
    }

    val isDark = when (themeMode) {
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
        AppThemeMode.DARK -> true
        AppThemeMode.LIGHT -> false
        AppThemeMode.CIRCADIAN -> circadianNight
    }

    val basePalette = if (palette == ThemePalette.GALATASARAY && !SpecialMemberConfig.isEmreBey()) ThemePalette.OCEAN else palette
    val effectivePalette = basePalette
    val effectiveIsDark = isDark || isZenMode
    val finalColors = if (isZenMode) {
        ZenMonochromeScheme
    } else {
        paletteSchemeFor(palette = effectivePalette, isDark = isDark)
    }

    val statusBarBg = finalColors.background
    val navBarBg    = finalColors.surface

    if (!view.isInEditMode) {
        SideEffect {
            val window = view.context.findActivity()?.window ?: return@SideEffect
            @Suppress("DEPRECATION")
            window.statusBarColor = statusBarBg.toArgb()
            @Suppress("DEPRECATION")
            window.navigationBarColor = navBarBg.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars     = !effectiveIsDark
                isAppearanceLightNavigationBars = !effectiveIsDark
            }
        }
    }

    val alreadyFiltered = LocalBlueLightApplied.current
    val effectiveFilterAlpha = if (!alreadyFiltered && isBlueLightActive) {
        blueLightIntensity.coerceIn(0.08f, 0.48f)
    } else {
        0f
    }

    MaterialTheme(
        colorScheme = finalColors,
        typography = Typography
    ) {
        if (alreadyFiltered) {
            content()
        } else {
            CompositionLocalProvider(LocalBlueLightApplied provides true) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .drawWithContent {
                            drawContent()
                            if (effectiveFilterAlpha > 0f) {
                                drawRect(color = EyeComfortAmberTint.copy(alpha = effectiveFilterAlpha))
                            }
                        }
                ) {
                    content()
                }
            }
        }
    }
}
