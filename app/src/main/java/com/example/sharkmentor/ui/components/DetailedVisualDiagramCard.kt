package com.example.sharkmentor.ui.components

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.webkit.WebView
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

private val SVG_FENCE_REGEX = Regex("```(?:svg|xml|html)?\\s*(<svg[\\s\\S]*?</svg>)\\s*```", RegexOption.IGNORE_CASE)
private val RAW_SVG_REGEX = Regex("(<svg\\b[\\s\\S]*?</svg>)", RegexOption.IGNORE_CASE)
private val OPEN_SVG_FENCE_REGEX = Regex("```(?:svg|xml|html)?\\s*<svg[\\s\\S]*$", RegexOption.IGNORE_CASE)
private val OPEN_RAW_SVG_REGEX = Regex("<svg\\b[\\s\\S]*$", RegexOption.IGNORE_CASE)

private val VISUAL_SOLVE_KEYWORDS = listOf(
    "fonksiyon", "grafik", "parabol", "doğru", "dogru", "eğim", "egim", "teğet", "teget",
    "türev", "turev", "integral", "limit", "süreklilik", "sureklilik", "asimptot",
    "trigonometri", "sin(", "cos(", "tan(", "cot(", "sinx", "cosx", "birim çember", "birim cember",
    "üçgen", "ucgen", "çember", "cember", "daire", "açı", "aci", "çokgen", "cokgen",
    "dikdörtgen", "dikdortgen", "yamuk", "eşkenar", "eskenar", "ikizkenar", "paralelkenar",
    "prizma", "silindir", "koni", "küre", "kure", "piramit", "alan", "çevre", "cevre", "hacim",
    "analitik", "koordinat", "eksen", "apsis", "ordinat", "nokta", "kesişim", "kesisim", "tepe noktası",
    "vektör", "vektor", "kuvvet", "devre", "direnç", "direnc", "akım", "akim", "dalga",
    "optik", "mercek", "ayna", "hareket", "hız-zaman", "konum-zaman", "ivme", "atış", "atis",
    "küme", "kume", "venn", "molekül", "molekul", "lewis", "diyagram", "şekil", "sekil",
    "f(x)", "g(x)", "h(x)", "y =", "y=", "x²", "x^2", "x³", "x^3"
)

/**
 * Determines whether a `/detayli` question/solution warrants a visual SVG diagram or graph
 * (functions, geometry, analytic coordinates, calculus, trigonometry, physics/chemistry diagrams),
 * while avoiding forced drawings on pure verbal or simple arithmetic questions.
 */
fun requiresVisualDiagramForDetailedSolve(
    userPrompt: String,
    solutionText: String,
    hasImageAttachment: Boolean
): Boolean {
    val combined = "${userPrompt}\n${solutionText}".lowercase(java.util.Locale("tr", "TR"))
    if (VISUAL_SOLVE_KEYWORDS.any { combined.contains(it) }) return true
    if (hasImageAttachment) {
        // Attached problem images in solver mode almost always benefit from a diagram if math/physics notation appears
        val hasStemNotation = combined.contains("=") ||
            combined.contains("°") ||
            combined.contains("cm") ||
            combined.contains("√") ||
            combined.contains("π") ||
            combined.contains("Δ")
        if (hasStemNotation) return true
    }
    return false
}

/**
 * Masks an incomplete/in-progress `<svg` XML block during token streaming so raw SVG code never flashes in chat.
 */
fun maskInProgressSvgForStreaming(rawPartial: String): String {
    if (rawPartial.isBlank()) return rawPartial
    if (SVG_FENCE_REGEX.containsMatchIn(rawPartial) || RAW_SVG_REGEX.containsMatchIn(rawPartial)) {
        // Completed SVG block present — keep intact so UI renders DetailedVisualDiagramCard cleanly
        return rawPartial
    }
    if (OPEN_SVG_FENCE_REGEX.containsMatchIn(rawPartial)) {
        val cleaned = rawPartial.replace(OPEN_SVG_FENCE_REGEX, "").trim()
        return if (cleaned.isEmpty()) "📐 Grafik ve görsel diyagram çiziliyor..." else "$cleaned\n\n📐 Grafik ve görsel diyagram çiziliyor..."
    }
    if (OPEN_RAW_SVG_REGEX.containsMatchIn(rawPartial)) {
        val cleaned = rawPartial.replace(OPEN_RAW_SVG_REGEX, "").trim()
        return if (cleaned.isEmpty()) "📐 Grafik ve görsel diyagram çiziliyor..." else "$cleaned\n\n📐 Grafik ve görsel diyagram çiziliyor..."
    }
    return rawPartial
}

private fun ensureSvgViewBox(svg: String): String {
    val openTagEnd = svg.indexOf('>')
    if (openTagEnd <= 0) return svg
    val openTag = svg.substring(0, openTagEnd)
    return if (openTag.contains("viewBox", ignoreCase = true)) {
        svg
    } else {
        svg.substring(0, openTagEnd) + " viewBox=\"0 0 380 240\"" + svg.substring(openTagEnd)
    }
}

/**
 * Extracts an inline SVG geometry/function diagram (if present) from an AI response,
 * normalizes raw LaTeX/ASCII math & science symbols inside it, and returns `(svgMarkup, cleanedText)`.
 */
fun extractSvgDiagramAndCleanText(raw: String): Pair<String?, String> {
    if (raw.isBlank()) return null to raw
    val fenced = SVG_FENCE_REGEX.find(raw)
    if (fenced != null) {
        val svg = ensureSvgViewBox(normalizeMathSymbolsInSvg(fenced.groupValues[1].trim()))
        val cleaned = raw.replaceRange(fenced.range, "").trim()
        return svg to cleaned
    }
    val inline = RAW_SVG_REGEX.find(raw)
    if (inline != null) {
        val svg = ensureSvgViewBox(normalizeMathSymbolsInSvg(inline.groupValues[1].trim()))
        val cleaned = raw.replaceRange(inline.range, "").trim()
        return svg to cleaned
    }
    return null to raw
}

fun normalizeMathSymbolsInSvg(svg: String): String {
    return svg
        .replace("\\pi", "π")
        .replace("\\Delta", "Δ")
        .replace("\\delta", "δ")
        .replace("\\theta", "θ")
        .replace("\\alpha", "α")
        .replace("\\beta", "β")
        .replace("\\gamma", "γ")
        .replace("\\omega", "ω")
        .replace("\\Omega", "Ω")
        .replace("\\lambda", "λ")
        .replace("\\mu", "μ")
        .replace("\\sigma", "σ")
        .replace("\\times", "×")
        .replace("\\cdot", "·")
        .replace("\\sqrt", "√")
        .replace("\\infty", "∞")
        .replace("\\degree", "°")
        .replace("\\deg", "°")
        .replace("\\pm", "±")
        .replace("\\le", "≤")
        .replace("\\ge", "≥")
        .replace("\\neq", "≠")
        .replace("H_2O", "H₂O")
        .replace("CO_2", "CO₂")
        .replace("O_2", "O₂")
        .replace("H_2SO_4", "H₂SO₄")
        .replace("^2", "²")
        .replace("^3", "³")
}

/**
 * Renders an AI-generated geometry figure or function graph SVG cleanly inside a themed card
 * with a built-in SharkMentor CoreLab watermark and one-tap PNG Share support.
 */
@Composable
fun DetailedVisualDiagramCard(
    svgMarkup: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    val isLight = colorScheme.surface.luminance() > 0.5f
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    val bgHex = if (isLight) "#F8FAFC" else "#0F172A"
    val textHex = if (isLight) "#0F172A" else "#E2E8F0"
    val badgeBg = if (isLight) "rgba(15,23,42,0.08)" else "rgba(15,23,42,0.68)"
    val badgeText = if (isLight) "rgba(15,23,42,0.72)" else "rgba(226,232,240,0.82)"

    val htmlDoc = remember(svgMarkup, isLight) {
        """
        <!DOCTYPE html>
        <html lang="tr">
        <head>
        <meta charset="utf-8"/>
        <meta name="viewport" content="width=device-width,initial-scale=1"/>
        <style>
          * { box-sizing: border-box; }
          html, body {
            margin: 0; padding: 8px;
            background: $bgHex;
            color: $textHex;
            font-family: system-ui, -apple-system, sans-serif;
            display: flex;
            flex-direction: column;
            align-items: center;
            justify-content: center;
            min-height: 210px;
            overflow: hidden;
          }
          svg {
            width: 100%;
            max-width: 100%;
            height: auto;
            max-height: 250px;
            display: block;
          }
          .wm {
            align-self: flex-end;
            margin-top: 6px;
            font: 600 9.5px/1.2 monospace;
            letter-spacing: 0.4px;
            color: $badgeText;
            background: $badgeBg;
            padding: 3px 8px;
            border-radius: 999px;
          }
        </style>
        </head>
        <body>
          $svgMarkup
          <div class="wm">SharkMentor CoreLab</div>
        </body>
        </html>
        """.trimIndent()
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = colorScheme.surface,
        border = BorderStroke(1.dp, colorScheme.primary.copy(alpha = 0.35f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Görsel Çözüm Diyagramı • BETA",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.primary
                )
                Surface(
                    onClick = {
                        val wv = webViewRef
                        if (wv != null && wv.width > 0 && wv.height > 0) {
                            runCatching {
                                val bmp = Bitmap.createBitmap(wv.width, wv.height, Bitmap.Config.ARGB_8888)
                                val canvas = Canvas(bmp)
                                wv.draw(canvas)
                                val dir = File(context.cacheDir, "shared_diagrams").apply { mkdirs() }
                                val outFile = File(dir, "SharkMentor_CoreLab_${System.currentTimeMillis()}.png")
                                FileOutputStream(outFile).use { fos ->
                                    bmp.compress(Bitmap.CompressFormat.PNG, 100, fos)
                                }
                                bmp.recycle()
                                val uri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    outFile
                                )
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "image/png"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    putExtra(Intent.EXTRA_TEXT, "SharkMentor CoreLab • Detaylı Görsel Çözüm")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Görseli Paylaş"))
                            }.onFailure {
                                Toast.makeText(context, "Görsel paylaşılamadı", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    shape = RoundedCornerShape(8.dp),
                    color = colorScheme.primary.copy(alpha = 0.12f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Görseli Paylaş",
                            tint = colorScheme.primary,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Paylaş",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colorScheme.primary
                        )
                    }
                }
            }

            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = false
                        settings.loadWithOverviewMode = true
                        settings.useWideViewPort = false
                        isVerticalScrollBarEnabled = false
                        isHorizontalScrollBarEnabled = false
                        setBackgroundColor(android.graphics.Color.TRANSPARENT)
                        loadDataWithBaseURL(null, htmlDoc, "text/html", "UTF-8", null)
                        webViewRef = this
                    }
                },
                update = { wv ->
                    webViewRef = wv
                    if (wv.tag != htmlDoc) {
                        wv.tag = htmlDoc
                        wv.loadDataWithBaseURL(null, htmlDoc, "text/html", "UTF-8", null)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(236.dp)
                    .clip(RoundedCornerShape(bottomStart = 14.dp, bottomEnd = 14.dp))
            )
        }
    }
}
