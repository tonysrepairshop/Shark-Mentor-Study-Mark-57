package com.example.sharkmentor.ui.components

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.example.sharkmentor.core.data.ThemePalette

/**
 * Color bundle applied over ANY of the 5 core SharkMusic player skins
 * (Default, Modern Vinyl, Classic Vinyl, CD, Cassette) when the user
 * long-presses the bottom-left skin button to synchronize player colors
 * with the active application theme.
 *
 * Pack index:
 *  - 0 -> Original player skin colors (`null`)
 *  - 1 -> Dengeli (Balanced)
 *  - 2 -> Canlı (Vivid)
 *  - 3 -> Derin (Deep / Night)
 */
@Immutable
data class PlayerSyncPalette(
    val packIndex: Int,
    val packLabel: String,
    val cardGradient: List<Color>,
    val shellColor: Color,
    val shellBorder: Color,
    val accent: Color,
    val vinylDark: Color,
    val vinylLight: Color,
    val onCard: Color = Color.White,
    val playCircleColor: Color = Color.White.copy(alpha = 0.22f)
)

fun resolvePlayerSyncPalette(
    appPalette: ThemePalette,
    packIndex: Int
): PlayerSyncPalette? {
    if (packIndex !in 1..3) return null
    val idx = packIndex - 1
    return when (appPalette) {
        ThemePalette.OCEAN -> listOf(
            PlayerSyncPalette(
                packIndex = 1, packLabel = "Dengeli",
                cardGradient = listOf(Color(0xFF0F4C5C), Color(0xFF0A3242), Color(0xFF071F2C)),
                shellColor = Color(0xFF133E4F), shellBorder = Color(0xFF2EC4B6).copy(alpha = 0.45f),
                accent = Color(0xFF2EC4B6), vinylDark = Color(0xFF082836), vinylLight = Color(0xFF258EA6)
            ),
            PlayerSyncPalette(
                packIndex = 2, packLabel = "Canlı",
                cardGradient = listOf(Color(0xFF1AA7A1), Color(0xFF0E6F82), Color(0xFF08415C)),
                shellColor = Color(0xFF0F5E70), shellBorder = Color(0xFF72EFDD).copy(alpha = 0.55f),
                accent = Color(0xFF72EFDD), vinylDark = Color(0xFF0B4F6C), vinylLight = Color(0xFF38D9C5)
            ),
            PlayerSyncPalette(
                packIndex = 3, packLabel = "Derin",
                cardGradient = listOf(Color(0xFF0B1B2B), Color(0xFF071320), Color(0xFF040B14)),
                shellColor = Color(0xFF0E2236), shellBorder = Color(0xFF1F7A8C).copy(alpha = 0.5f),
                accent = Color(0xFF56CFE1), vinylDark = Color(0xFF06111C), vinylLight = Color(0xFF16425B)
            )
        )[idx]

        ThemePalette.SOOTY -> listOf(
            PlayerSyncPalette(
                packIndex = 1, packLabel = "Dengeli",
                cardGradient = listOf(Color(0xFF34353D), Color(0xFF23242A), Color(0xFF17181C)),
                shellColor = Color(0xFF2E2F37), shellBorder = Color(0xFF8E8EA0).copy(alpha = 0.45f),
                accent = Color(0xFFB8B8CC), vinylDark = Color(0xFF191A1F), vinylLight = Color(0xFF4B4D59)
            ),
            PlayerSyncPalette(
                packIndex = 2, packLabel = "Canlı",
                cardGradient = listOf(Color(0xFF525463), Color(0xFF383A47), Color(0xFF22232B)),
                shellColor = Color(0xFF454756), shellBorder = Color(0xFFD0D0E2).copy(alpha = 0.5f),
                accent = Color(0xFFE4E4F0), vinylDark = Color(0xFF262833), vinylLight = Color(0xFF72758A)
            ),
            PlayerSyncPalette(
                packIndex = 3, packLabel = "Derin",
                cardGradient = listOf(Color(0xFF18181B), Color(0xFF111114), Color(0xFF09090B)),
                shellColor = Color(0xFF1F1F24), shellBorder = Color(0xFF52525B).copy(alpha = 0.5f),
                accent = Color(0xFFA1A1AA), vinylDark = Color(0xFF0D0D10), vinylLight = Color(0xFF2E2E36)
            )
        )[idx]

        ThemePalette.COFFEE -> listOf(
            PlayerSyncPalette(
                packIndex = 1, packLabel = "Dengeli",
                cardGradient = listOf(Color(0xFF6B442A), Color(0xFF4A2D1B), Color(0xFF2E1B10)),
                shellColor = Color(0xFF5A3722), shellBorder = Color(0xFFD4A373).copy(alpha = 0.5f),
                accent = Color(0xFFD4A373), vinylDark = Color(0xFF2F1B10), vinylLight = Color(0xFF8D5B3A)
            ),
            PlayerSyncPalette(
                packIndex = 2, packLabel = "Canlı",
                cardGradient = listOf(Color(0xFFA47148), Color(0xFF7B4B2A), Color(0xFF4F2E18)),
                shellColor = Color(0xFF8B5A36), shellBorder = Color(0xFFF3D5B5).copy(alpha = 0.55f),
                accent = Color(0xFFF3D5B5), vinylDark = Color(0xFF52301A), vinylLight = Color(0xFFC38E63)
            ),
            PlayerSyncPalette(
                packIndex = 3, packLabel = "Derin",
                cardGradient = listOf(Color(0xFF2B201A), Color(0xFF1C1511), Color(0xFF120D0A)),
                shellColor = Color(0xFF2F231C), shellBorder = Color(0xFF8C6239).copy(alpha = 0.5f),
                accent = Color(0xFFE6BE94), vinylDark = Color(0xFF140E0B), vinylLight = Color(0xFF4A3426)
            )
        )[idx]

        ThemePalette.RAINY -> listOf(
            PlayerSyncPalette(
                packIndex = 1, packLabel = "Dengeli",
                cardGradient = listOf(Color(0xFF3A506B), Color(0xFF28384D), Color(0xFF1A2433)),
                shellColor = Color(0xFF2F4259), shellBorder = Color(0xFF8FB3D9).copy(alpha = 0.45f),
                accent = Color(0xFF8FB3D9), vinylDark = Color(0xFF1B2738), vinylLight = Color(0xFF567496)
            ),
            PlayerSyncPalette(
                packIndex = 2, packLabel = "Canlı",
                cardGradient = listOf(Color(0xFF5C82A6), Color(0xFF3E6185), Color(0xFF253E59)),
                shellColor = Color(0xFF496E91), shellBorder = Color(0xFFBFD9F2).copy(alpha = 0.55f),
                accent = Color(0xFFBFD9F2), vinylDark = Color(0xFF294361), vinylLight = Color(0xFF7BA3C9)
            ),
            PlayerSyncPalette(
                packIndex = 3, packLabel = "Derin",
                cardGradient = listOf(Color(0xFF1C232E), Color(0xFF141922), Color(0xFF0C1017)),
                shellColor = Color(0xFF212A38), shellBorder = Color(0xFF547291).copy(alpha = 0.5f),
                accent = Color(0xFF93B7DB), vinylDark = Color(0xFF0F141D), vinylLight = Color(0xFF2F3E52)
            )
        )[idx]

        ThemePalette.FOREST -> listOf(
            PlayerSyncPalette(
                packIndex = 1, packLabel = "Dengeli",
                cardGradient = listOf(Color(0xFF1F4E35), Color(0xFF153624), Color(0xFF0D2317)),
                shellColor = Color(0xFF1A422D), shellBorder = Color(0xFF52B788).copy(alpha = 0.45f),
                accent = Color(0xFF52B788), vinylDark = Color(0xFF0E291B), vinylLight = Color(0xFF2F7550)
            ),
            PlayerSyncPalette(
                packIndex = 2, packLabel = "Canlı",
                cardGradient = listOf(Color(0xFF2D7A53), Color(0xFF1F5B3D), Color(0xFF133A26)),
                shellColor = Color(0xFF246645), shellBorder = Color(0xFF95D5B2).copy(alpha = 0.55f),
                accent = Color(0xFF95D5B2), vinylDark = Color(0xFF16472E), vinylLight = Color(0xFF45A173)
            ),
            PlayerSyncPalette(
                packIndex = 3, packLabel = "Derin",
                cardGradient = listOf(Color(0xFF102118), Color(0xFF0B1710), Color(0xFF060E09)),
                shellColor = Color(0xFF14291E), shellBorder = Color(0xFF387A56).copy(alpha = 0.5f),
                accent = Color(0xFF74C69D), vinylDark = Color(0xFF08140D), vinylLight = Color(0xFF214A34)
            )
        )[idx]

        ThemePalette.DREAMS -> listOf(
            PlayerSyncPalette(
                packIndex = 1, packLabel = "Dengeli",
                cardGradient = listOf(Color(0xFF6E3557), Color(0xFF4D233D), Color(0xFF301527)),
                shellColor = Color(0xFF5C2B49), shellBorder = Color(0xFFF472B6).copy(alpha = 0.5f),
                accent = Color(0xFFF9A8D4), vinylDark = Color(0xFF36172B), vinylLight = Color(0xFF9E4B7C)
            ),
            PlayerSyncPalette(
                packIndex = 2, packLabel = "Canlı",
                cardGradient = listOf(Color(0xFFC2528B), Color(0xFF8F3566), Color(0xFF5C1E42)),
                shellColor = Color(0xFFA34174), shellBorder = Color(0xFFFBCFE8).copy(alpha = 0.6f),
                accent = Color(0xFFFBCFE8), vinylDark = Color(0xFF632047), vinylLight = Color(0xFFDF73AB)
            ),
            PlayerSyncPalette(
                packIndex = 3, packLabel = "Derin",
                cardGradient = listOf(Color(0xFF291823), Color(0xFF1C1018), Color(0xFF110A0F)),
                shellColor = Color(0xFF301C2A), shellBorder = Color(0xFF9D4EDD).copy(alpha = 0.45f),
                accent = Color(0xFFF472B6), vinylDark = Color(0xFF170D14), vinylLight = Color(0xFF4F2843)
            )
        )[idx]

        ThemePalette.SAKURA -> listOf(
            PlayerSyncPalette(
                packIndex = 1, packLabel = "Dengeli",
                cardGradient = listOf(Color(0xFF94475A), Color(0xFF6B3040), Color(0xFF451D28)),
                shellColor = Color(0xFF7D3B4C), shellBorder = Color(0xFFFDA4AF).copy(alpha = 0.55f),
                accent = Color(0xFFFDA4AF), vinylDark = Color(0xFF4A1F2B), vinylLight = Color(0xFFC2647A)
            ),
            PlayerSyncPalette(
                packIndex = 2, packLabel = "Canlı",
                cardGradient = listOf(Color(0xFFD6788E), Color(0xFFB5566E), Color(0xFF85374B)),
                shellColor = Color(0xFFC2637B), shellBorder = Color(0xFFFFE4E6).copy(alpha = 0.6f),
                accent = Color(0xFFFFE4E6), vinylDark = Color(0xFF8C394E), vinylLight = Color(0xFFEB98AC)
            ),
            PlayerSyncPalette(
                packIndex = 3, packLabel = "Derin",
                cardGradient = listOf(Color(0xFF2B191E), Color(0xFF1E1115), Color(0xFF120A0C)),
                shellColor = Color(0xFF361F25), shellBorder = Color(0xFFBE5A70).copy(alpha = 0.5f),
                accent = Color(0xFFFDA4AF), vinylDark = Color(0xFF190E11), vinylLight = Color(0xFF572B36)
            )
        )[idx]

        ThemePalette.AUTUMN -> listOf(
            PlayerSyncPalette(
                packIndex = 1, packLabel = "Dengeli",
                cardGradient = listOf(Color(0xFF8F3E16), Color(0xFF662A0E), Color(0xFF401A08)),
                shellColor = Color(0xFF7A3412), shellBorder = Color(0xFFFB923C).copy(alpha = 0.5f),
                accent = Color(0xFFFDBA74), vinylDark = Color(0xFF451C09), vinylLight = Color(0xFFB85421)
            ),
            PlayerSyncPalette(
                packIndex = 2, packLabel = "Canlı",
                cardGradient = listOf(Color(0xFFD96420), Color(0xFFB04614), Color(0xFF732B0B)),
                shellColor = Color(0xFFBF5319), shellBorder = Color(0xFFFED7AA).copy(alpha = 0.6f),
                accent = Color(0xFFFED7AA), vinylDark = Color(0xFF7D2F0D), vinylLight = Color(0xFFEA7D3B)
            ),
            PlayerSyncPalette(
                packIndex = 3, packLabel = "Derin",
                cardGradient = listOf(Color(0xFF291A12), Color(0xFF1C120C), Color(0xFF120B07)),
                shellColor = Color(0xFF332016), shellBorder = Color(0xFFEA580C).copy(alpha = 0.5f),
                accent = Color(0xFFFB923C), vinylDark = Color(0xFF170E09), vinylLight = Color(0xFF592B14)
            )
        )[idx]

        ThemePalette.GALATASARAY -> listOf(
            PlayerSyncPalette(
                packIndex = 1, packLabel = "Dengeli",
                cardGradient = listOf(Color(0xFF7D0924), Color(0xFF520618), Color(0xFF2B040D)),
                shellColor = Color(0xFF66071D), shellBorder = Color(0xFFFDB912).copy(alpha = 0.55f),
                accent = Color(0xFFFDB912), vinylDark = Color(0xFF330410), vinylLight = Color(0xFFA90432)
            ),
            PlayerSyncPalette(
                packIndex = 2, packLabel = "Canlı",
                cardGradient = listOf(Color(0xFFA90432), Color(0xFF7A0722), Color(0xFF4A1808)),
                shellColor = Color(0xFF8C0629), shellBorder = Color(0xFFFFD166).copy(alpha = 0.65f),
                accent = Color(0xFFFFD166), vinylDark = Color(0xFF59051A), vinylLight = Color(0xFFD62839)
            ),
            PlayerSyncPalette(
                packIndex = 3, packLabel = "Derin",
                cardGradient = listOf(Color(0xFF260910), Color(0xFF1A060B), Color(0xFF0E0306)),
                shellColor = Color(0xFF300C15), shellBorder = Color(0xFFFDB912).copy(alpha = 0.45f),
                accent = Color(0xFFFDB912), vinylDark = Color(0xFF140408), vinylLight = Color(0xFF591022)
            )
        )[idx]

        ThemePalette.BLUEBERRY -> listOf(
            PlayerSyncPalette(
                packIndex = 1, packLabel = "Dengeli",
                cardGradient = listOf(Color(0xFF4C248A), Color(0xFF32165E), Color(0xFF1E0D3B)),
                shellColor = Color(0xFF3F1D75), shellBorder = Color(0xFFC084FC).copy(alpha = 0.5f),
                accent = Color(0xFFC084FC), vinylDark = Color(0xFF220E45), vinylLight = Color(0xFF7C3AED)
            ),
            PlayerSyncPalette(
                packIndex = 2, packLabel = "Canlı",
                cardGradient = listOf(Color(0xFF7E3AF2), Color(0xFF5B21B6), Color(0xFF3B137D)),
                shellColor = Color(0xFF6B2AD6), shellBorder = Color(0xFFE9D5FF).copy(alpha = 0.6f),
                accent = Color(0xFFE9D5FF), vinylDark = Color(0xFF43168F), vinylLight = Color(0xFFA855F7)
            ),
            PlayerSyncPalette(
                packIndex = 3, packLabel = "Derin",
                cardGradient = listOf(Color(0xFF1D1336), Color(0xFF130C24), Color(0xFF0B0717)),
                shellColor = Color(0xFF231742), shellBorder = Color(0xFF9333EA).copy(alpha = 0.5f),
                accent = Color(0xFFD8B4FE), vinylDark = Color(0xFF0F091F), vinylLight = Color(0xFF3D2275)
            )
        )[idx]

        ThemePalette.CHRISTMAS -> listOf(
            PlayerSyncPalette(
                packIndex = 1, packLabel = "Dengeli",
                cardGradient = listOf(Color(0xFF1E4D38), Color(0xFF143626), Color(0xFF0C2218)),
                shellColor = Color(0xFF1A4230), shellBorder = Color(0xFFE63946).copy(alpha = 0.5f),
                accent = Color(0xFFF6D365), vinylDark = Color(0xFF0D261B), vinylLight = Color(0xFF2E7D5B)
            ),
            PlayerSyncPalette(
                packIndex = 2, packLabel = "Canlı",
                cardGradient = listOf(Color(0xFF2B6E50), Color(0xFF1E4D38), Color(0xFF5C1A22)),
                shellColor = Color(0xFF245C43), shellBorder = Color(0xFFFDE68A).copy(alpha = 0.6f),
                accent = Color(0xFFFDE68A), vinylDark = Color(0xFF163D2B), vinylLight = Color(0xFF3D966E)
            ),
            PlayerSyncPalette(
                packIndex = 3, packLabel = "Derin",
                cardGradient = listOf(Color(0xFF13241C), Color(0xFF0D1A14), Color(0xFF07100C)),
                shellColor = Color(0xFF172B22), shellBorder = Color(0xFF2E7D5B).copy(alpha = 0.55f),
                accent = Color(0xFFFCA5A5), vinylDark = Color(0xFF09140F), vinylLight = Color(0xFF224D3A)
            )
        )[idx]

        ThemePalette.PAPER -> listOf(
            PlayerSyncPalette(
                packIndex = 1, packLabel = "Dengeli",
                cardGradient = listOf(Color(0xFFF3EFE4), Color(0xFFE6DFCF), Color(0xFFD9CFBA)),
                shellColor = Color(0xFFEAE3D3), shellBorder = Color(0xFF8C7D65).copy(alpha = 0.45f),
                accent = Color(0xFF4A4235), vinylDark = Color(0xFF3B352B), vinylLight = Color(0xFF706554),
                onCard = Color(0xFF24201A), playCircleColor = Color(0xFF24201A).copy(alpha = 0.14f)
            ),
            PlayerSyncPalette(
                packIndex = 2, packLabel = "Canlı",
                cardGradient = listOf(Color(0xFFFAF6EC), Color(0xFFF0E7D5), Color(0xFFE2D4BC)),
                shellColor = Color(0xFFF2EBD9), shellBorder = Color(0xFFA68A64).copy(alpha = 0.55f),
                accent = Color(0xFF8B5E34), vinylDark = Color(0xFF4F3B26), vinylLight = Color(0xFF9C7B56),
                onCard = Color(0xFF2B241C), playCircleColor = Color(0xFF8B5E34).copy(alpha = 0.16f)
            ),
            PlayerSyncPalette(
                packIndex = 3, packLabel = "Derin",
                cardGradient = listOf(Color(0xFF3B362E), Color(0xFF2B2721), Color(0xFF1E1B17)),
                shellColor = Color(0xFF332F28), shellBorder = Color(0xFFD6CBB5).copy(alpha = 0.4f),
                accent = Color(0xFFE8DFCE), vinylDark = Color(0xFF1F1C18), vinylLight = Color(0xFF574F43),
                onCard = Color(0xFFF5F1E6), playCircleColor = Color(0xFFF5F1E6).copy(alpha = 0.20f)
            )
        )[idx]

        ThemePalette.OBSIDIAN -> listOf(
            PlayerSyncPalette(
                packIndex = 1, packLabel = "Dengeli",
                cardGradient = listOf(Color(0xFF1A1D24), Color(0xFF101217), Color(0xFF07080B)),
                shellColor = Color(0xFF181B22), shellBorder = Color(0xFFE2E8F0).copy(alpha = 0.35f),
                accent = Color(0xFFE2E8F0), vinylDark = Color(0xFF0B0C10), vinylLight = Color(0xFF333947)
            ),
            PlayerSyncPalette(
                packIndex = 2, packLabel = "Canlı",
                cardGradient = listOf(Color(0xFF272B36), Color(0xFF171A21), Color(0xFF0A0B0F)),
                shellColor = Color(0xFF21252F), shellBorder = Color(0xFF94A3B8).copy(alpha = 0.5f),
                accent = Color(0xFFFFFFFF), vinylDark = Color(0xFF111319), vinylLight = Color(0xFF475569)
            ),
            PlayerSyncPalette(
                packIndex = 3, packLabel = "Derin",
                cardGradient = listOf(Color(0xFF0B0B0D), Color(0xFF050507), Color(0xFF000000)),
                shellColor = Color(0xFF0F0F12), shellBorder = Color(0xFF475569).copy(alpha = 0.45f),
                accent = Color(0xFFCBD5E1), vinylDark = Color(0xFF030304), vinylLight = Color(0xFF1E2028)
            )
        )[idx]
    }
}
