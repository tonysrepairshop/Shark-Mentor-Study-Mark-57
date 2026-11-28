package com.example.sharkmentor.ui.screens.notes.drawing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import com.example.sharkmentor.core.ai.GeminiAiFactory
import com.example.sharkmentor.core.ai.QuotaCheckResult
import com.example.sharkmentor.core.ai.QuotaUsageKind
import com.example.sharkmentor.core.ai.SharkTokenQuotaManager
import com.example.sharkmentor.core.ai.UserAiPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min

data class HandwritingCalibrationPrompt(
    val stepNumber: Int,
    val title: String,
    val guideText: String,
    val hint: String
)

data class HandwritingCalibrationProfile(
    val isCalibrated: Boolean = false,
    val sampleCount: Int = 0,
    val avgStrokeHeightPx: Float = 0f,
    val avgSlantDeg: Float = 0f,
    val calibratedPrompts: List<String> = emptyList(),
    val updatedAtMillis: Long = 0L
)

sealed class HandwritingRecognitionResult {
    data class Success(val text: String, val usedPersonalCalibration: Boolean) : HandwritingRecognitionResult()
    data class Failure(val message: String) : HandwritingRecognitionResult()
}

/**
 * Pure-Kotlin helpers & biometric profile analyzer for personal Turkish Handwriting-to-Text
 * calibration and output cleanup. Safe to unit-test directly on JVM.
 */
object HandwritingRecognitionHelpers {

    val CALIBRATION_PROMPTS: List<HandwritingCalibrationPrompt> = listOf(
        HandwritingCalibrationPrompt(
            stepNumber = 1,
            title = "Adım 1/3 • Kalem & Büyük Harf",
            guideText = "SHARK",
            hint = "Büyük harfle SHARK yaz (kalem basıncı ve yan tuş testi de aktif)."
        ),
        HandwritingCalibrationPrompt(
            stepNumber = 2,
            title = "Adım 2/3 • Doğal El Yazısı",
            guideText = "Merhaba, bugün çalışıyorum.",
            hint = "Günlük el yazınla bu cümleyi yaz; sistem harf akışını öğrensin."
        ),
        HandwritingCalibrationPrompt(
            stepNumber = 3,
            title = "Adım 3/3 • Türkçe & Matematik",
            guideText = "ÇğİıÖşÜ  f(x) = x² + 5",
            hint = "Özel Türkçe harfler ve matematik sembolleri için örnek yaz."
        )
    )

    fun analyzeCalibrationGeometry(
        strokeGroups: List<List<DrawingPoint>>,
        completedPrompts: List<String>
    ): HandwritingCalibrationProfile {
        val validStrokes = strokeGroups.filter { it.size >= 2 }
        if (validStrokes.isEmpty()) {
            return HandwritingCalibrationProfile(isCalibrated = false)
        }

        var heightSum = 0f
        var slantSum = 0f
        var slantSamples = 0

        for (pts in validStrokes) {
            var minY = Float.MAX_VALUE
            var maxY = -Float.MAX_VALUE
            for (p in pts) {
                if (p.y < minY) minY = p.y
                if (p.y > maxY) maxY = p.y
            }
            heightSum += (maxY - minY).coerceAtLeast(4f)

            val dx = pts.last().x - pts.first().x
            val dy = pts.last().y - pts.first().y
            if (abs(dy) >= 12f) {
                val slant = (atan2(dx.toDouble(), abs(dy).toDouble()) * 180.0 / Math.PI).toFloat()
                    .coerceIn(-45f, 45f)
                slantSum += slant
                slantSamples++
            }
        }

        return HandwritingCalibrationProfile(
            isCalibrated = completedPrompts.isNotEmpty(),
            sampleCount = validStrokes.size,
            avgStrokeHeightPx = heightSum / validStrokes.size.toFloat(),
            avgSlantDeg = if (slantSamples > 0) slantSum / slantSamples.toFloat() else 0f,
            calibratedPrompts = completedPrompts,
            updatedAtMillis = System.currentTimeMillis()
        )
    }

    fun cleanRecognizedHandwritingOutput(raw: String): String {
        return raw.trim()
            .removePrefix("```text")
            .removePrefix("```markdown")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
            .removeSurrounding("\"")
            .trim()
    }

    fun buildRecognitionPrompt(profile: HandwritingCalibrationProfile, hasReferenceSheet: Boolean): String {
        return buildString {
            appendLine("Görev: Verilen el yazısı görselini BİREBİR temiz Türkçe metne dönüştür.")
            if (hasReferenceSheet && profile.calibratedPrompts.isNotEmpty()) {
                appendLine("NOT: İlk görsel bu öğrencinin kendi el yazısı kalibrasyon örneğidir. O kalibrasyon örneğinde öğrencinin yazdığı gerçek metinler sırasıyla şunlardır:")
                profile.calibratedPrompts.forEachIndexed { idx, line ->
                    appendLine("${idx + 1}) $line")
                }
                appendLine("Öğrencinin harf stillerini ilk görselden öğren ve YALNIZCA İKİNCİ (son) görseldeki el yazısını metne çevir.")
            }
            appendLine("Kurallar:")
            appendLine("- Türkçe karakterleri (ç, ğ, ı, İ, ö, ş, ü) doğru koru.")
            appendLine("- Matematik ifadeleri varsa temiz Unicode sembollerle yaz (x², x³, √, π, Δ, °, ≤, ≥, ≠, →).")
            appendLine("- Asla yorum, başlık, tırnak işareti veya açıklama ekleme; SADECE okunan metni döndür.")
            appendLine("- Birden fazla satır varsa satır sonlarını koru.")
        }.trim()
    }
}

/**
 * Local per-device persistence for the student's personal handwriting calibration sheet & metrics.
 */
object HandwritingCalibrationStore {
    private const val PREFS_NAME = "shark_handwriting_calib_v1"
    private const val KEY_CALIBRATED = "is_calibrated"
    private const val KEY_SAMPLE_COUNT = "sample_count"
    private const val KEY_AVG_HEIGHT = "avg_height"
    private const val KEY_AVG_SLANT = "avg_slant"
    private const val KEY_PROMPTS = "prompts_joined"
    private const val KEY_UPDATED_AT = "updated_at"
    private const val SAMPLE_FILE_NAME = "shark_handwriting_calib_ref.png"

    private val _profile = MutableStateFlow(HandwritingCalibrationProfile())
    val profile: StateFlow<HandwritingCalibrationProfile> = _profile.asStateFlow()

    @Volatile
    private var loaded = false

    fun ensureLoaded(context: Context) {
        if (loaded) return
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val promptsRaw = prefs.getString(KEY_PROMPTS, "").orEmpty()
        val prompts = promptsRaw.split("||").map { it.trim() }.filter { it.isNotEmpty() }
        _profile.value = HandwritingCalibrationProfile(
            isCalibrated = prefs.getBoolean(KEY_CALIBRATED, false),
            sampleCount = prefs.getInt(KEY_SAMPLE_COUNT, 0),
            avgStrokeHeightPx = prefs.getFloat(KEY_AVG_HEIGHT, 0f),
            avgSlantDeg = prefs.getFloat(KEY_AVG_SLANT, 0f),
            calibratedPrompts = prompts,
            updatedAtMillis = prefs.getLong(KEY_UPDATED_AT, 0L)
        )
        loaded = true
    }

    fun saveCalibration(
        context: Context,
        stepStrokeLists: List<Pair<String, List<List<DrawingPoint>>>>
    ): HandwritingCalibrationProfile {
        val allGroups = stepStrokeLists.flatMap { it.second }
        val completedPrompts = stepStrokeLists.filter { it.second.isNotEmpty() }.map { it.first }
        val nextProfile = HandwritingRecognitionHelpers.analyzeCalibrationGeometry(allGroups, completedPrompts)

        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(KEY_CALIBRATED, nextProfile.isCalibrated)
            .putInt(KEY_SAMPLE_COUNT, nextProfile.sampleCount)
            .putFloat(KEY_AVG_HEIGHT, nextProfile.avgStrokeHeightPx)
            .putFloat(KEY_AVG_SLANT, nextProfile.avgSlantDeg)
            .putString(KEY_PROMPTS, nextProfile.calibratedPrompts.joinToString("||"))
            .putLong(KEY_UPDATED_AT, nextProfile.updatedAtMillis)
            .apply()
        _profile.value = nextProfile
        loaded = true

        // Render compact reference sheet PNG for few-shot visual calibration
        val nonEmptySteps = stepStrokeLists.filter { it.second.isNotEmpty() }
        if (nonEmptySteps.isNotEmpty()) {
            runCatching {
                val bmp = renderCalibrationReferenceSheet(nonEmptySteps)
                val outFile = File(context.applicationContext.filesDir, SAMPLE_FILE_NAME)
                FileOutputStream(outFile).use { fos ->
                    bmp.compress(Bitmap.CompressFormat.PNG, 90, fos)
                }
                if (!bmp.isRecycled) bmp.recycle()
            }
        }
        return nextProfile
    }

    fun loadReferenceBitmap(context: Context): Bitmap? {
        val file = File(context.applicationContext.filesDir, SAMPLE_FILE_NAME)
        if (!file.exists()) return null
        return runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()
    }

    private fun renderCalibrationReferenceSheet(
        steps: List<Pair<String, List<List<DrawingPoint>>>>
    ): Bitmap {
        val width = 720
        val rowHeight = 160
        val totalHeight = (steps.size * rowHeight).coerceAtLeast(160)
        val bmp = Bitmap.createBitmap(width, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.WHITE)

        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(15, 23, 42)
            style = Paint.Style.STROKE
            strokeWidth = 4.5f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(226, 232, 240)
            strokeWidth = 1.5f
        }

        steps.forEachIndexed { idx, (_, strokes) ->
            val top = idx * rowHeight
            if (idx > 0) {
                canvas.drawLine(16f, top.toFloat(), (width - 16).toFloat(), top.toFloat(), dividerPaint)
            }
            var minX = Float.MAX_VALUE
            var minY = Float.MAX_VALUE
            var maxX = -Float.MAX_VALUE
            var maxY = -Float.MAX_VALUE
            strokes.forEach { pts ->
                pts.forEach { p ->
                    if (p.x < minX) minX = p.x
                    if (p.y < minY) minY = p.y
                    if (p.x > maxX) maxX = p.x
                    if (p.y > maxY) maxY = p.y
                }
            }
            if (minX < maxX && minY < maxY) {
                val srcW = (maxX - minX).coerceAtLeast(1f)
                val srcH = (maxY - minY).coerceAtLeast(1f)
                val pad = 20f
                val availW = width - pad * 2f
                val availH = rowHeight - pad * 2f
                val scale = min(availW / srcW, availH / srcH).coerceAtMost(2.2f)
                val offsetX = pad + (availW - srcW * scale) * 0.5f - minX * scale
                val offsetY = top + pad + (availH - srcH * scale) * 0.5f - minY * scale

                val path = Path()
                strokes.forEach { pts ->
                    if (pts.size >= 2) {
                        path.reset()
                        path.moveTo(pts.first().x * scale + offsetX, pts.first().y * scale + offsetY)
                        for (i in 1 until pts.size) {
                            path.lineTo(pts[i].x * scale + offsetX, pts[i].y * scale + offsetY)
                        }
                        canvas.drawPath(path, strokePaint)
                    }
                }
            }
        }
        return bmp
    }
}

/**
 * Converts Lasso-selected handwritten strokes on Notes or PDF pages into clean typed Turkish text
 * using high-contrast vector rasterization + the student's personal calibration reference sheet.
 */
object HandwritingRecognitionEngine {

    fun renderStrokesToBitmap(
        strokes: List<DrawingStroke>,
        canvasWidth: Float,
        canvasHeight: Float
    ): Bitmap? {
        if (strokes.isEmpty()) return null
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE

        val scaledStrokes = strokes.map { st ->
            val sx = if (st.canvasWidth > 1f) canvasWidth / st.canvasWidth else 1f
            val sy = if (st.canvasHeight > 1f) canvasHeight / st.canvasHeight else 1f
            st.points.map { p ->
                val px = p.x * sx
                val py = p.y * sy
                if (px < minX) minX = px
                if (py < minY) minY = py
                if (px > maxX) maxX = px
                if (py > maxY) maxY = py
                DrawingPoint(px, py, p.pressure)
            } to st.strokeWidth
        }

        if (minX >= maxX || minY >= maxY) return null
        val srcW = (maxX - minX).coerceAtLeast(16f)
        val srcH = (maxY - minY).coerceAtLeast(16f)

        val targetMaxDim = 960f
        val pad = 42f
        val scale = min((targetMaxDim - pad * 2f) / srcW, (targetMaxDim - pad * 2f) / srcH)
            .coerceIn(0.6f, 2.5f)
        val outW = max(180, (srcW * scale + pad * 2f).toInt())
        val outH = max(120, (srcH * scale + pad * 2f).toInt())

        val bmp = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.WHITE)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(12, 18, 30)
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val path = Path()

        scaledStrokes.forEach { (pts, rawWidth) ->
            paint.strokeWidth = (rawWidth * scale).coerceIn(3.5f, 11f)
            if (pts.size == 1) {
                canvas.drawCircle(
                    (pts[0].x - minX) * scale + pad,
                    (pts[0].y - minY) * scale + pad,
                    paint.strokeWidth * 0.55f,
                    paint
                )
            } else if (pts.size >= 2) {
                path.reset()
                path.moveTo((pts[0].x - minX) * scale + pad, (pts[0].y - minY) * scale + pad)
                for (i in 1 until pts.size) {
                    path.lineTo((pts[i].x - minX) * scale + pad, (pts[i].y - minY) * scale + pad)
                }
                canvas.drawPath(path, paint)
            }
        }
        return bmp
    }

    suspend fun recognizeStrokesToText(
        context: Context,
        strokes: List<DrawingStroke>,
        canvasWidth: Float,
        canvasHeight: Float
    ): HandwritingRecognitionResult = withContext(Dispatchers.IO) {
        HandwritingCalibrationStore.ensureLoaded(context)
        val profile = HandwritingCalibrationStore.profile.value

        val isBuiltIn = !UserAiPreferences.hasCustomUserGeminiKeys(context)
        val quotaCheck = SharkTokenQuotaManager.checkCanConsume(
            context = context,
            kind = QuotaUsageKind.CHAT,
            isBuiltInKey = isBuiltIn
        )
        if (quotaCheck is QuotaCheckResult.Blocked) {
            return@withContext HandwritingRecognitionResult.Failure(quotaCheck.message)
        }

        val targetBitmap = renderStrokesToBitmap(strokes, canvasWidth, canvasHeight)
            ?: return@withContext HandwritingRecognitionResult.Failure("Metne çevrilecek yeterli el yazısı bulunamadı.")
        val refBitmap = if (profile.isCalibrated) HandwritingCalibrationStore.loadReferenceBitmap(context) else null

        val bitmaps = if (refBitmap != null) listOf(refBitmap, targetBitmap) else listOf(targetBitmap)
        val prompt = HandwritingRecognitionHelpers.buildRecognitionPrompt(
            profile = profile,
            hasReferenceSheet = refBitmap != null
        )

        try {
            val result = GeminiAiFactory.streamContentWithFailover(
                context = context,
                systemInstruction = "Sen yüksek doğruluklu Türkçe ve matematik el yazısı OCR motorusun. Çıktında yalnızca okunan düz metin bulunur.",
                userPrompt = prompt,
                bitmaps = bitmaps,
                temperature = 0.08f,
                maxOutputTokens = 1024,
                timeoutMs = 35_000L,
                onPartialText = {}
            )
            val raw = result.getOrNull()?.text.orEmpty()
            val cleaned = HandwritingRecognitionHelpers.cleanRecognizedHandwritingOutput(raw)
            if (cleaned.isBlank()) {
                val err = result.exceptionOrNull()?.localizedMessage ?: "El yazısı okunamadı, tekrar dene."
                HandwritingRecognitionResult.Failure(err)
            } else {
                HandwritingRecognitionResult.Success(
                    text = cleaned,
                    usedPersonalCalibration = refBitmap != null
                )
            }
        } catch (e: Exception) {
            HandwritingRecognitionResult.Failure(e.localizedMessage ?: "El yazısı dönüştürme başarısız oldu.")
        } finally {
            runCatching { if (!targetBitmap.isRecycled) targetBitmap.recycle() }
            runCatching { if (refBitmap != null && !refBitmap.isRecycled) refBitmap.recycle() }
        }
    }
}
