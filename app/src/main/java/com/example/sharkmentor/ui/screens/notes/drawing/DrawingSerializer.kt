package com.example.sharkmentor.ui.screens.notes.drawing

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.UUID

/**
 * Sidecar JSON persistence for Note drawings and page-indexed PDF annotations.
 * Keeps Room schemas (NoteEntity) completely untouched while providing durable storage.
 */
object DrawingSerializer {

    private const val TAG = "DrawingSerializer"
    private const val NOTE_DRAWINGS_DIR = "note_drawings"
    private const val PDF_ANNOTATIONS_DIR = "pdf_annotations"
    private const val FORMAT_VERSION = 1

    // ── JSON Encoding / Decoding ─────────────────────────────────────────────

    fun strokesToJson(strokes: List<DrawingStroke>): String {
        return runCatching {
            val root = JSONObject()
            root.put("version", FORMAT_VERSION)
            root.put("updatedAt", System.currentTimeMillis())

            val strokesArray = JSONArray()
            for (stroke in strokes) {
                if (stroke.points.isEmpty()) continue
                val strokeObj = JSONObject().apply {
                    put("id", stroke.id)
                    put("tool", stroke.tool.name)
                    put("shapeKind", stroke.shapeKind.name)
                    put("colorArgb", stroke.colorArgb)
                    put("strokeWidth", stroke.strokeWidth.toDouble())
                    put("alpha", stroke.alpha.toDouble())
                    put("canvasWidth", stroke.canvasWidth.toDouble())
                    put("canvasHeight", stroke.canvasHeight.toDouble())
                    put("pressureSensitive", stroke.pressureSensitive)

                    val ptsArray = JSONArray()
                    for (pt in stroke.points) {
                        val ptObj = JSONObject()
                        ptObj.put("x", pt.x.toDouble())
                        ptObj.put("y", pt.y.toDouble())
                        if (kotlin.math.abs(pt.pressure - 1.0f) > 0.005f) {
                            ptObj.put("p", ((pt.pressure * 1000f).toInt() / 1000.0))
                        }
                        ptsArray.put(ptObj)
                    }
                    put("points", ptsArray)
                }
                strokesArray.put(strokeObj)
            }
            root.put("strokes", strokesArray)
            root.toString()
        }.getOrElse { e ->
            Log.e(TAG, "Failed to serialize strokes to JSON", e)
            "{\"version\":1,\"strokes\":[]}"
        }
    }

    fun strokesFromJson(jsonStr: String): List<DrawingStroke> {
        if (jsonStr.isBlank()) return emptyList()
        return runCatching {
            val root = JSONObject(jsonStr)
            val strokesArray = root.optJSONArray("strokes") ?: return@runCatching emptyList()
            val result = ArrayList<DrawingStroke>(strokesArray.length())

            for (i in 0 until strokesArray.length()) {
                val obj = strokesArray.optJSONObject(i) ?: continue
                val ptsArray = obj.optJSONArray("points") ?: continue
                val points = ArrayList<DrawingPoint>(ptsArray.length())
                for (pIdx in 0 until ptsArray.length()) {
                    val ptObj = ptsArray.optJSONObject(pIdx) ?: continue
                    val x = ptObj.optDouble("x", Double.NaN).toFloat()
                    val y = ptObj.optDouble("y", Double.NaN).toFloat()
                    val pressure = ptObj.optDouble("p", 1.0).toFloat().coerceIn(0.1f, 2.0f)
                    if (!x.isNaN() && !y.isNaN()) {
                        points.add(DrawingPoint(x, y, pressure))
                    }
                }
                if (points.isEmpty()) continue

                val toolName = obj.optString("tool", DrawingTool.FOUNTAIN_PEN.name)
                val tool = runCatching { DrawingTool.valueOf(toolName) }.getOrDefault(DrawingTool.FOUNTAIN_PEN)

                val shapeName = obj.optString("shapeKind", ShapeKind.NONE.name)
                val shapeKind = runCatching { ShapeKind.valueOf(shapeName) }.getOrDefault(ShapeKind.NONE)

                result.add(
                    DrawingStroke(
                        id = obj.optString("id").ifBlank { UUID.randomUUID().toString() },
                        tool = tool,
                        shapeKind = shapeKind,
                        colorArgb = obj.optInt("colorArgb", 0xFF1E88E5.toInt()),
                        strokeWidth = obj.optDouble("strokeWidth", 4.5).toFloat().coerceIn(0.5f, 80f),
                        alpha = obj.optDouble("alpha", 1.0).toFloat().coerceIn(0.05f, 1.0f),
                        points = points,
                        canvasWidth = obj.optDouble("canvasWidth", 1080.0).toFloat().coerceAtLeast(1f),
                        canvasHeight = obj.optDouble("canvasHeight", 1920.0).toFloat().coerceAtLeast(1f),
                        pressureSensitive = obj.optBoolean("pressureSensitive", true)
                    )
                )
            }
            result
        }.getOrElse { e ->
            Log.e(TAG, "Failed to parse strokes from JSON", e)
            emptyList()
        }
    }

    data class PageAnnotations(
        val strokes: List<DrawingStroke> = emptyList(),
        val textBoxes: List<DrawingTextBox> = emptyList()
    )

    private fun textBoxesToJsonArray(textBoxes: List<DrawingTextBox>): JSONArray {
        val arr = JSONArray()
        for (box in textBoxes) {
            val obj = JSONObject().apply {
                put("id", box.id)
                put("x", box.xFraction.toDouble())
                put("y", box.yFraction.toDouble())
                put("w", box.widthFraction.toDouble())
                put("content", box.taggedContent)
                put("font", box.baseFontName)
                put("sizeSp", box.baseFontSizeSp.toDouble())
                if (box.baseTextColorArgb != null) {
                    put("colorArgb", box.baseTextColorArgb)
                }
            }
            arr.put(obj)
        }
        return arr
    }

    private fun textBoxesFromJsonArray(arr: JSONArray?): List<DrawingTextBox> {
        if (arr == null) return emptyList()
        val out = ArrayList<DrawingTextBox>(arr.length())
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val x = obj.optDouble("x", 0.12).toFloat().coerceIn(0.01f, 0.90f)
            val y = obj.optDouble("y", 0.14).toFloat().coerceIn(0.01f, 0.92f)
            val w = obj.optDouble("w", 0.48).toFloat().coerceIn(0.20f, 0.95f)
            val colorArgb = if (obj.has("colorArgb") && !obj.isNull("colorArgb")) {
                obj.optInt("colorArgb")
            } else {
                null
            }
            out.add(
                DrawingTextBox(
                    id = obj.optString("id").ifBlank { UUID.randomUUID().toString() },
                    xFraction = x,
                    yFraction = y,
                    widthFraction = w,
                    taggedContent = obj.optString("content", ""),
                    baseFontName = obj.optString("font", "DEFAULT").ifBlank { "DEFAULT" },
                    baseFontSizeSp = obj.optDouble("sizeSp", 16.0).toFloat().coerceIn(10f, 40f),
                    baseTextColorArgb = colorArgb
                )
            )
        }
        return out
    }

    fun annotationsToJson(
        strokes: List<DrawingStroke>,
        textBoxes: List<DrawingTextBox> = emptyList()
    ): String {
        return runCatching {
            val root = JSONObject(strokesToJson(strokes))
            if (textBoxes.isNotEmpty()) {
                root.put("textBoxes", textBoxesToJsonArray(textBoxes))
            }
            root.toString()
        }.getOrElse { e ->
            Log.e(TAG, "Failed to serialize page annotations to JSON", e)
            "{\"version\":1,\"strokes\":[],\"textBoxes\":[]}"
        }
    }

    fun annotationsFromJson(jsonStr: String): PageAnnotations {
        if (jsonStr.isBlank()) return PageAnnotations()
        return runCatching {
            val strokes = strokesFromJson(jsonStr)
            val root = JSONObject(jsonStr)
            val boxes = textBoxesFromJsonArray(root.optJSONArray("textBoxes"))
            PageAnnotations(strokes = strokes, textBoxes = boxes)
        }.getOrElse { e ->
            Log.e(TAG, "Failed to parse page annotations from JSON", e)
            PageAnnotations()
        }
    }

    // ── Note Sidecar Storage (files/note_drawings/note_<id>.json & note_<id>_p<page>.json) ──

    private fun sanitizeNoteId(noteId: String): String =
        noteId.replace(Regex("[^a-zA-Z0-9._-]"), "_").ifBlank { "default" }

    private fun getNoteDrawingDir(context: Context): File =
        File(context.filesDir, NOTE_DRAWINGS_DIR).apply {
            if (!exists()) mkdirs()
        }

    private fun getNoteDrawingFile(context: Context, noteId: String): File {
        val dir = getNoteDrawingDir(context)
        val safeId = sanitizeNoteId(noteId)
        return File(dir, "note_${safeId}.json")
    }

    private fun getNotePageDrawingFile(context: Context, noteId: String, pageIndex: Int): File {
        if (pageIndex <= 0) return getNoteDrawingFile(context, noteId)
        val dir = getNoteDrawingDir(context)
        val safeId = sanitizeNoteId(noteId)
        return File(dir, "note_${safeId}_p${pageIndex}.json")
    }

    fun saveNoteDrawing(context: Context, noteId: String, strokes: List<DrawingStroke>) {
        saveNotePageDrawing(context, noteId, pageIndex = 0, strokes = strokes)
    }

    fun loadNoteDrawing(context: Context, noteId: String): List<DrawingStroke> {
        return loadNotePageDrawing(context, noteId, pageIndex = 0)
    }

    @Synchronized
    fun saveNotePageAnnotations(
        context: Context,
        noteId: String,
        pageIndex: Int,
        strokes: List<DrawingStroke>,
        textBoxes: List<DrawingTextBox>
    ) {
        if (noteId.isBlank() || pageIndex < 0) return
        runCatching {
            val file = getNotePageDrawingFile(context, noteId, pageIndex)
            if (strokes.isEmpty() && textBoxes.isEmpty()) {
                if (file.exists()) file.delete()
                return
            }
            val json = annotationsToJson(strokes, textBoxes)
            writeTextAtomically(file, json)
        }.onFailure { e ->
            Log.e(TAG, "Failed saving note annotations for $noteId page $pageIndex", e)
        }
    }

    @Synchronized
    fun loadNotePageAnnotations(
        context: Context,
        noteId: String,
        pageIndex: Int
    ): PageAnnotations {
        if (noteId.isBlank() || pageIndex < 0) return PageAnnotations()
        return runCatching {
            val file = getNotePageDrawingFile(context, noteId, pageIndex)
            if (!file.exists()) return PageAnnotations()
            annotationsFromJson(file.readText(Charsets.UTF_8))
        }.getOrElse { e ->
            Log.e(TAG, "Failed loading note annotations for $noteId page $pageIndex", e)
            PageAnnotations()
        }
    }

    @Synchronized
    fun saveNotePageDrawing(
        context: Context,
        noteId: String,
        pageIndex: Int,
        strokes: List<DrawingStroke>
    ) {
        if (noteId.isBlank() || pageIndex < 0) return
        val existingBoxes = loadNotePageAnnotations(context, noteId, pageIndex).textBoxes
        saveNotePageAnnotations(context, noteId, pageIndex, strokes, existingBoxes)
    }

    @Synchronized
    fun saveNotePageTextBoxes(
        context: Context,
        noteId: String,
        pageIndex: Int,
        textBoxes: List<DrawingTextBox>
    ) {
        if (noteId.isBlank() || pageIndex < 0) return
        val existingStrokes = loadNotePageAnnotations(context, noteId, pageIndex).strokes
        saveNotePageAnnotations(context, noteId, pageIndex, existingStrokes, textBoxes)
    }

    fun loadNotePageDrawing(
        context: Context,
        noteId: String,
        pageIndex: Int
    ): List<DrawingStroke> {
        return loadNotePageAnnotations(context, noteId, pageIndex).strokes
    }

    fun loadNotePageTextBoxes(
        context: Context,
        noteId: String,
        pageIndex: Int
    ): List<DrawingTextBox> {
        return loadNotePageAnnotations(context, noteId, pageIndex).textBoxes
    }

    fun deleteNotePageDrawingAndShift(
        context: Context,
        noteId: String,
        deletedPageIndex: Int,
        totalPagesBeforeDelete: Int
    ) {
        if (noteId.isBlank() || deletedPageIndex < 0 || totalPagesBeforeDelete <= 1) return
        runCatching {
            for (idx in deletedPageIndex until (totalPagesBeforeDelete - 1)) {
                val nextAnn = loadNotePageAnnotations(context, noteId, idx + 1)
                saveNotePageAnnotations(context, noteId, idx, nextAnn.strokes, nextAnn.textBoxes)
            }
            val lastFile = getNotePageDrawingFile(context, noteId, totalPagesBeforeDelete - 1)
            if (lastFile.exists()) lastFile.delete()
        }.onFailure { e ->
            Log.w(TAG, "Failed shifting note page drawings for $noteId", e)
        }
    }

    fun hasNoteDrawing(context: Context, noteId: String): Boolean {
        if (noteId.isBlank()) return false
        return runCatching {
            val dir = getNoteDrawingDir(context)
            val safeId = sanitizeNoteId(noteId)
            val primary = File(dir, "note_${safeId}.json")
            if (primary.exists() && primary.length() > 30L) return@runCatching true
            val prefix = "note_${safeId}_p"
            dir.listFiles()?.any {
                it.name.startsWith(prefix) && it.name.endsWith(".json") && it.length() > 30L
            } == true
        }.getOrDefault(false)
    }

    fun duplicateNoteDrawing(context: Context, sourceNoteId: String, targetNoteId: String) {
        if (sourceNoteId.isBlank() || targetNoteId.isBlank()) return
        runCatching {
            val dir = getNoteDrawingDir(context)
            val srcSafe = sanitizeNoteId(sourceNoteId)
            val dstSafe = sanitizeNoteId(targetNoteId)
            val srcPrimary = File(dir, "note_${srcSafe}.json")
            if (srcPrimary.exists()) {
                srcPrimary.copyTo(File(dir, "note_${dstSafe}.json"), overwrite = true)
            }
            val pagePrefix = "note_${srcSafe}_p"
            dir.listFiles()?.forEach { file ->
                if (file.name.startsWith(pagePrefix) && file.name.endsWith(".json")) {
                    val suffix = file.name.removePrefix("note_${srcSafe}")
                    file.copyTo(File(dir, "note_${dstSafe}${suffix}"), overwrite = true)
                }
            }
        }.onFailure { e ->
            Log.w(TAG, "Failed duplicating note drawing from $sourceNoteId to $targetNoteId", e)
        }
    }

    fun deleteNoteDrawing(context: Context, noteId: String) {
        if (noteId.isBlank()) return
        runCatching {
            val dir = getNoteDrawingDir(context)
            val safeId = sanitizeNoteId(noteId)
            val primary = File(dir, "note_${safeId}.json")
            if (primary.exists()) primary.delete()
            val pagePrefix = "note_${safeId}_p"
            dir.listFiles()?.forEach { file ->
                if (file.name.startsWith(pagePrefix) && file.name.endsWith(".json")) {
                    file.delete()
                }
            }
        }
    }

    // ── PDF Per-Page Annotation Sidecar Storage (files/pdf_annotations/) ─────

    fun computePdfDocKey(pdfIdentifier: String): String {
        return runCatching {
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(pdfIdentifier.trim().toByteArray(Charsets.UTF_8))
            hash.take(12).joinToString("") { "%02x".format(it) }
        }.getOrElse {
            pdfIdentifier.hashCode().toUInt().toString(16)
        }
    }

    private fun getPdfDocFile(context: Context, pdfIdentifier: String): File {
        val dir = File(context.filesDir, PDF_ANNOTATIONS_DIR).apply {
            if (!exists()) mkdirs()
        }
        val docKey = computePdfDocKey(pdfIdentifier)
        return File(dir, "pdf_${docKey}.json")
    }

    private fun writeTextAtomically(target: File, content: String) {
        val parent = target.parentFile
        if (parent != null && !parent.exists()) {
            parent.mkdirs()
        }
        val tempFile = File(parent ?: target.absoluteFile.parentFile, "${target.name}.tmp")
        tempFile.writeText(content, Charsets.UTF_8)
        if (!tempFile.renameTo(target)) {
            // Fallback on filesystems where rename over existing fails
            tempFile.copyTo(target, overwrite = true)
            runCatching { tempFile.delete() }
        }
    }

    /**
     * Saves annotations for a single page [pageIndex] of a PDF identified by [pdfIdentifier].
     * Stored atomically in a single structured per-document JSON file containing all annotated pages and metadata.
     */
    @Synchronized
    fun savePdfPageAnnotations(
        context: Context,
        pdfIdentifier: String,
        pageIndex: Int,
        strokes: List<DrawingStroke>
    ) {
        if (pdfIdentifier.isBlank() || pageIndex < 0) return
        runCatching {
            val file = getPdfDocFile(context, pdfIdentifier)
            val root = if (file.exists()) {
                runCatching { JSONObject(file.readText(Charsets.UTF_8)) }.getOrDefault(JSONObject())
            } else {
                JSONObject()
            }

            root.put("version", FORMAT_VERSION)
            root.put("pdfIdentifier", pdfIdentifier)
            root.put("updatedAt", System.currentTimeMillis())

            val pagesObj = root.optJSONObject("pages") ?: JSONObject()
            if (strokes.isEmpty()) {
                pagesObj.remove(pageIndex.toString())
            } else {
                val pageJson = JSONObject(strokesToJson(strokes))
                pagesObj.put(pageIndex.toString(), pageJson.optJSONArray("strokes") ?: JSONArray())
            }
            root.put("pages", pagesObj)
            writeTextAtomically(file, root.toString())
        }.onFailure { e ->
            Log.e(TAG, "Failed saving PDF annotations for page $pageIndex", e)
        }
    }

    @Synchronized
    fun loadPdfPageAnnotations(
        context: Context,
        pdfIdentifier: String,
        pageIndex: Int
    ): List<DrawingStroke> {
        if (pdfIdentifier.isBlank() || pageIndex < 0) return emptyList()
        return runCatching {
            val file = getPdfDocFile(context, pdfIdentifier)
            if (!file.exists()) return emptyList()
            val root = JSONObject(file.readText(Charsets.UTF_8))
            val pagesObj = root.optJSONObject("pages") ?: return emptyList()
            val strokesArray = pagesObj.optJSONArray(pageIndex.toString()) ?: return emptyList()
            val wrapper = JSONObject().apply {
                put("version", FORMAT_VERSION)
                put("strokes", strokesArray)
            }
            strokesFromJson(wrapper.toString())
        }.getOrElse { e ->
            Log.e(TAG, "Failed loading PDF annotations for page $pageIndex", e)
            emptyList()
        }
    }

    @Synchronized
    fun loadAllPdfAnnotations(
        context: Context,
        pdfIdentifier: String
    ): Map<Int, List<DrawingStroke>> {
        if (pdfIdentifier.isBlank()) return emptyMap()
        return runCatching {
            val file = getPdfDocFile(context, pdfIdentifier)
            if (!file.exists()) return emptyMap()
            val root = JSONObject(file.readText(Charsets.UTF_8))
            val pagesObj = root.optJSONObject("pages") ?: return emptyMap()
            val map = mutableMapOf<Int, List<DrawingStroke>>()
            val keys = pagesObj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val pageIdx = key.toIntOrNull() ?: continue
                val arr = pagesObj.optJSONArray(key) ?: continue
                val wrapper = JSONObject().apply {
                    put("version", FORMAT_VERSION)
                    put("strokes", arr)
                }
                val parsed = strokesFromJson(wrapper.toString())
                if (parsed.isNotEmpty()) {
                    map[pageIdx] = parsed
                }
            }
            map
        }.getOrDefault(emptyMap())
    }

    @Synchronized
    fun savePdfPageTextBoxes(
        context: Context,
        pdfIdentifier: String,
        pageIndex: Int,
        textBoxes: List<DrawingTextBox>
    ) {
        if (pdfIdentifier.isBlank() || pageIndex < 0) return
        runCatching {
            val file = getPdfDocFile(context, pdfIdentifier)
            val root = if (file.exists()) {
                runCatching { JSONObject(file.readText(Charsets.UTF_8)) }.getOrDefault(JSONObject())
            } else {
                JSONObject()
            }
            root.put("version", FORMAT_VERSION)
            root.put("pdfIdentifier", pdfIdentifier)
            root.put("updatedAt", System.currentTimeMillis())

            val boxesObj = root.optJSONObject("pageTextBoxes") ?: JSONObject()
            if (textBoxes.isEmpty()) {
                boxesObj.remove(pageIndex.toString())
            } else {
                boxesObj.put(pageIndex.toString(), textBoxesToJsonArray(textBoxes))
            }
            root.put("pageTextBoxes", boxesObj)
            writeTextAtomically(file, root.toString())
        }.onFailure { e ->
            Log.e(TAG, "Failed saving PDF text boxes for page $pageIndex", e)
        }
    }

    @Synchronized
    fun loadPdfPageTextBoxes(
        context: Context,
        pdfIdentifier: String,
        pageIndex: Int
    ): List<DrawingTextBox> {
        if (pdfIdentifier.isBlank() || pageIndex < 0) return emptyList()
        return runCatching {
            val file = getPdfDocFile(context, pdfIdentifier)
            if (!file.exists()) return emptyList()
            val root = JSONObject(file.readText(Charsets.UTF_8))
            val boxesObj = root.optJSONObject("pageTextBoxes") ?: return emptyList()
            textBoxesFromJsonArray(boxesObj.optJSONArray(pageIndex.toString()))
        }.getOrElse { e ->
            Log.e(TAG, "Failed loading PDF text boxes for page $pageIndex", e)
            emptyList()
        }
    }

    @Synchronized
    fun loadAllPdfTextBoxes(
        context: Context,
        pdfIdentifier: String
    ): Map<Int, List<DrawingTextBox>> {
        if (pdfIdentifier.isBlank()) return emptyMap()
        return runCatching {
            val file = getPdfDocFile(context, pdfIdentifier)
            if (!file.exists()) return emptyMap()
            val root = JSONObject(file.readText(Charsets.UTF_8))
            val boxesObj = root.optJSONObject("pageTextBoxes") ?: return emptyMap()
            val map = mutableMapOf<Int, List<DrawingTextBox>>()
            val keys = boxesObj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val pageIdx = key.toIntOrNull() ?: continue
                val parsed = textBoxesFromJsonArray(boxesObj.optJSONArray(key))
                if (parsed.isNotEmpty()) {
                    map[pageIdx] = parsed
                }
            }
            map
        }.getOrDefault(emptyMap())
    }

    @Synchronized
    fun getPdfAnnotatedPageCount(context: Context, pdfIdentifier: String): Int {
        if (pdfIdentifier.isBlank()) return 0
        return runCatching {
            val file = getPdfDocFile(context, pdfIdentifier)
            if (!file.exists()) return 0
            val root = JSONObject(file.readText(Charsets.UTF_8))
            val annotatedPages = mutableSetOf<String>()
            root.optJSONObject("pages")?.let { pagesObj ->
                val keys = pagesObj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val arr = pagesObj.optJSONArray(key)
                    if (arr != null && arr.length() > 0) annotatedPages.add(key)
                }
            }
            root.optJSONObject("pageTextBoxes")?.let { boxesObj ->
                val keys = boxesObj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val arr = boxesObj.optJSONArray(key)
                    if (arr != null && arr.length() > 0) annotatedPages.add(key)
                }
            }
            annotatedPages.size
        }.getOrDefault(0)
    }

    @Synchronized
    fun deletePdfData(context: Context, pdfIdentifier: String) {
        if (pdfIdentifier.isBlank()) return
        runCatching {
            val file = getPdfDocFile(context, pdfIdentifier)
            if (file.exists()) file.delete()
            val tmp = File(file.parentFile, "${file.name}.tmp")
            if (tmp.exists()) tmp.delete()
        }
    }

    @Synchronized
    fun savePdfLastReadPage(context: Context, pdfIdentifier: String, pageIndex: Int) {
        if (pdfIdentifier.isBlank() || pageIndex < 0) return
        runCatching {
            val file = getPdfDocFile(context, pdfIdentifier)
            val root = if (file.exists()) {
                runCatching { JSONObject(file.readText(Charsets.UTF_8)) }.getOrDefault(JSONObject())
            } else {
                JSONObject()
            }
            root.put("lastReadPage", pageIndex)
            root.put("updatedAt", System.currentTimeMillis())
            writeTextAtomically(file, root.toString())
        }
    }

    @Synchronized
    fun loadPdfLastReadPage(context: Context, pdfIdentifier: String): Int {
        if (pdfIdentifier.isBlank()) return 0
        return runCatching {
            val file = getPdfDocFile(context, pdfIdentifier)
            if (!file.exists()) return 0
            val root = JSONObject(file.readText(Charsets.UTF_8))
            root.optInt("lastReadPage", 0).coerceAtLeast(0)
        }.getOrDefault(0)
    }
}
