package com.example.sharkmentor.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class MathSymbolItem(
    val label: String,
    val insertText: String = label
)

data class MathSymbolCategory(
    val title: String,
    val symbols: List<MathSymbolItem>
)

object MathSymbolCatalog {
    val categories: List<MathSymbolCategory> = listOf(
        MathSymbolCategory(
            title = "Temel",
            symbols = listOf(
                MathSymbolItem("="),
                MathSymbolItem("≠"),
                MathSymbolItem("≈"),
                MathSymbolItem("±"),
                MathSymbolItem("×"),
                MathSymbolItem("÷"),
                MathSymbolItem("·"),
                MathSymbolItem("≤"),
                MathSymbolItem("≥"),
                MathSymbolItem("<"),
                MathSymbolItem(">"),
                MathSymbolItem("a/b", "/"),
                MathSymbolItem("() ", "()"),
                MathSymbolItem("|x|", "|x|"),
                MathSymbolItem("%")
            )
        ),
        MathSymbolCategory(
            title = "Üs & Kök",
            symbols = listOf(
                MathSymbolItem("x²", "²"),
                MathSymbolItem("x³", "³"),
                MathSymbolItem("xⁿ", "ⁿ"),
                MathSymbolItem("x⁻¹", "⁻¹"),
                MathSymbolItem("√(", "√("),
                MathSymbolItem("∛(", "∛("),
                MathSymbolItem("x₁", "x₁"),
                MathSymbolItem("x₂", "x₂"),
                MathSymbolItem("xₙ", "xₙ"),
                MathSymbolItem("10ⁿ", "10ⁿ"),
                MathSymbolItem("eˣ", "eˣ"),
                MathSymbolItem("^", "^")
            )
        ),
        MathSymbolCategory(
            title = "Kalkülüs",
            symbols = listOf(
                MathSymbolItem("f(x)", "f(x)"),
                MathSymbolItem("f'(x)", "f'(x)"),
                MathSymbolItem("dy/dx", "dy/dx"),
                MathSymbolItem("lim", "lim(x→"),
                MathSymbolItem("∫", "∫"),
                MathSymbolItem("∑", "∑"),
                MathSymbolItem("Δ", "Δ"),
                MathSymbolItem("∞", "∞"),
                MathSymbolItem("→", "→"),
                MathSymbolItem("log", "log("),
                MathSymbolItem("ln", "ln("),
                MathSymbolItem("sin", "sin("),
                MathSymbolItem("cos", "cos("),
                MathSymbolItem("tan", "tan(")
            )
        ),
        MathSymbolCategory(
            title = "Geometri & Küme",
            symbols = listOf(
                MathSymbolItem("π"),
                MathSymbolItem("θ"),
                MathSymbolItem("α"),
                MathSymbolItem("β"),
                MathSymbolItem("°"),
                MathSymbolItem("∠"),
                MathSymbolItem("△"),
                MathSymbolItem("⊥"),
                MathSymbolItem("∥"),
                MathSymbolItem("∈"),
                MathSymbolItem("∉"),
                MathSymbolItem("⊂"),
                MathSymbolItem("∪"),
                MathSymbolItem("∩"),
                MathSymbolItem("∅"),
                MathSymbolItem("ℝ")
            )
        )
    )

    /**
     * Pure helper appending [symbol] cleanly to [current] without double spacing.
     */
    fun insertSymbol(current: String, symbol: String): String {
        if (symbol.isEmpty()) return current
        val needsSpaceBefore = current.isNotEmpty() &&
            !current.endsWith(" ") &&
            !current.endsWith("(") &&
            symbol in setOf("=", "≠", "≈", "±", "×", "÷", "≤", "≥", "→")
        val prefix = if (needsSpaceBefore) "$current " else current
        val suffix = if (symbol in setOf("=", "≠", "≈", "≤", "≥", "→")) " " else ""
        return "$prefix$symbol$suffix"
    }
}

/**
 * Compact `∑` toggle button used next to AI chat inputs (Video Study Studio AI & Shark AI).
 */
@Composable
fun MathSymbolToggleButton(
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (expanded) colorScheme.primary else colorScheme.surfaceVariant.copy(alpha = 0.75f),
        border = BorderStroke(
            1.dp,
            if (expanded) colorScheme.primary else colorScheme.outlineVariant.copy(alpha = 0.55f)
        ),
        modifier = modifier.size(34.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = "∑",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Serif,
                color = if (expanded) colorScheme.onPrimary else colorScheme.primary
            )
        }
    }
}

/**
 * Theme-aware collapsible Mathematics & Formula Symbol Panel shared across Shark AI and Video Study Studio AI.
 */
@Composable
fun MathSymbolKeyboardPanel(
    visible: Boolean,
    onInsertSymbol: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    var selectedCategoryIdx by remember { mutableIntStateOf(0) }
    val categories = MathSymbolCatalog.categories
    val activeCategory = categories[selectedCategoryIdx.coerceIn(0, categories.lastIndex)]

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = colorScheme.surfaceVariant.copy(alpha = 0.55f),
            border = BorderStroke(1.dp, colorScheme.primary.copy(alpha = 0.28f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 7.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Category pill row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    categories.forEachIndexed { idx, cat ->
                        val selected = idx == selectedCategoryIdx
                        Surface(
                            onClick = { selectedCategoryIdx = idx },
                            shape = RoundedCornerShape(8.dp),
                            color = if (selected) colorScheme.primary else colorScheme.surface,
                            border = BorderStroke(
                                1.dp,
                                if (selected) colorScheme.primary else colorScheme.outlineVariant.copy(alpha = 0.45f)
                            )
                        ) {
                            Text(
                                text = cat.title,
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 11.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                color = if (selected) colorScheme.onPrimary else colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                // Symbols horizontal strip (single-tap insert)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    activeCategory.symbols.forEach { item ->
                        Surface(
                            onClick = { onInsertSymbol(item.insertText) },
                            shape = RoundedCornerShape(8.dp),
                            color = colorScheme.surface,
                            border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.55f)),
                            modifier = Modifier.heightIn(min = 32.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = item.label,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    fontFamily = FontFamily.Serif,
                                    color = colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
