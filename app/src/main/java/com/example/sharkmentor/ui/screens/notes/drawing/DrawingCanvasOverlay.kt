package com.example.sharkmentor.ui.screens.notes.drawing

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.zIndex
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.StickyNote2
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.isTertiaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.sharkmentor.ui.screens.notes.NoteFontOption
import com.example.sharkmentor.ui.screens.notes.NoteRichTextHelper
import com.example.sharkmentor.ui.screens.notes.drawing.DrawingScreenshotHelper.findActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

private val QUICK_LASSO_COLORS = listOf(
    Color(0xFF0F172A),
    Color(0xFFE53935),
    Color(0xFF1E88E5),
    Color(0xFF43A047),
    Color(0xFFFB8C00),
    Color(0xFF8E24AA),
    Color(0xFFFFEB3B),
    Color(0xFF00ACC1)
)

/**
 * Interactive drawing & annotation overlay that works both over the Rich Note Editor
 * and over individual PDF Reader pages.
 *
 * Supports:
 * - Freehand pens with automatic Scribble-to-Erase (zigzag >= 3 sharp reversals)
 * - Free-region Lasso selection & pixel-accurate screenshot cropping (Direct bitmap or LayoutCoordinates PixelCopy)
 * - Pass-through when [controller.activeTool] == [DrawingTool.NONE]
 */
@Composable
fun DrawingCanvasOverlay(
    controller: DrawingController,
    modifier: Modifier = Modifier,
    onTwoFingerScroll: ((deltaY: Float, deltaX: Float) -> Unit)? = null,
    onLassoAskAi: ((Bitmap) -> Unit)? = null,
    directContentBitmapProvider: (() -> Bitmap?)? = null,
    defaultTextBoxColor: Color = Color(0xFF0F172A)
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val window = remember(context) { context.findActivity()?.window }

    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var canvasCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val latestDirectBitmapProvider by rememberUpdatedState(directContentBitmapProvider)

    val inProgressPoints = remember { mutableStateListOf<DrawingPoint>() }
    var snappedLiveShape by remember { mutableStateOf<RecognizedShape?>(null) }
    var eraserCursorPos by remember { mutableStateOf<Offset?>(null) }
    var isMovingLassoSelection by remember { mutableStateOf(false) }
    var showLassoColorRow by remember { mutableStateOf(false) }

    var isCapturingScreenshot by remember { mutableStateOf(false) }
    var capturedCrop by remember { mutableStateOf<CapturedCropResult?>(null) }
    var usePolygonCrop by remember { mutableStateOf(true) }
    var isSavingCrop by remember { mutableStateOf(false) }
    var feedbackMessage by remember { mutableStateOf<String?>(null) }
    var dialogStatusMessage by remember { mutableStateOf<String?>(null) }
    var pendingGalleryBitmap by remember { mutableStateOf<Bitmap?>(null) }

    fun performGallerySave(targetBitmap: Bitmap) {
        if (isSavingCrop) return
        isSavingCrop = true
        coroutineScope.launch {
            val result = DrawingScreenshotHelper.saveBitmapToGallery(context, targetBitmap)
            isSavingCrop = false
            val msg = if (result.isSuccess) {
                "Galeriye kaydedildi"
            } else {
                val detail = result.exceptionOrNull()?.localizedMessage?.takeIf { it.isNotBlank() }
                if (detail != null) "Galeriye kaydedilemedi: $detail" else "Galeriye kaydedilemedi"
            }
            dialogStatusMessage = msg
            feedbackMessage = msg
        }
    }

    val galleryPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        val pending = pendingGalleryBitmap
        pendingGalleryBitmap = null
        if (granted && pending != null) {
            performGallerySave(pending)
        } else {
            val msg = "Galeriye kaydetmek için depolama izni gerekiyor"
            dialogStatusMessage = msg
            feedbackMessage = msg
        }
    }

    // Auto-clear subtle toast/feedback pill
    LaunchedEffect(feedbackMessage) {
        if (feedbackMessage != null) {
            delay(2200L)
            feedbackMessage = null
        }
    }

    fun triggerLassoScreenshot() {
        val selBounds = controller.cropRegionBounds()
        if (selBounds == null || isCapturingScreenshot) return

        val polygonSnapshot = controller.lassoPolygon.toList()
        val strokesSnapshot = controller.strokes.toList()
        val directBitmap = latestDirectBitmapProvider?.invoke()?.takeIf { !it.isRecycled }
        val currentSize = canvasSize
        val win = window
        val coords = canvasCoordinates

        if (directBitmap == null && (win == null || coords == null || !coords.isAttached)) {
            feedbackMessage = "Ekran bölgesi henüz hazır değil"
            return
        }

        coroutineScope.launch {
            isCapturingScreenshot = true
            try {
                val result = if (directBitmap != null && currentSize.width > 1 && currentSize.height > 1) {
                    DrawingScreenshotHelper.captureDirectBitmapRegion(
                        sourceBitmap = directBitmap,
                        strokes = strokesSnapshot,
                        canvasWidth = currentSize.width.toFloat(),
                        canvasHeight = currentSize.height.toFloat(),
                        localBounds = selBounds,
                        lassoPolygon = polygonSnapshot,
                        pressureSensitivityEnabled = controller.pressureSensitivityEnabled
                    )
                } else {
                    // Wait 2 UI frames so dashed lasso border & action bar disappear before PixelCopy
                    withFrameNanos { }
                    withFrameNanos { }
                    delay(40L)

                    val liveCoords = canvasCoordinates
                    if (win != null && liveCoords != null && liveCoords.isAttached) {
                        DrawingScreenshotHelper.captureLassoRegion(
                            window = win,
                            canvasCoordinates = liveCoords,
                            localBounds = selBounds,
                            lassoPolygon = polygonSnapshot
                        )
                    } else {
                        null
                    }
                }

                if (result != null) {
                    dialogStatusMessage = null
                    usePolygonCrop = result.polygonBitmap != null
                    capturedCrop = result
                } else {
                    feedbackMessage = "Ekran görüntüsü alınamadı"
                }
            } finally {
                isCapturingScreenshot = false
            }
        }
    }

    fun triggerLassoAskAi() {
        val askCallback = onLassoAskAi ?: return
        val selBounds = controller.cropRegionBounds()
        if (selBounds == null || isCapturingScreenshot) return

        val strokesSnapshot = controller.strokes.toList()
        val directBitmap = latestDirectBitmapProvider?.invoke()?.takeIf { !it.isRecycled }
        val currentSize = canvasSize
        val win = window
        val coords = canvasCoordinates

        if (directBitmap == null && (win == null || coords == null || !coords.isAttached)) {
            feedbackMessage = "Ekran bölgesi henüz hazır değil"
            return
        }

        coroutineScope.launch {
            isCapturingScreenshot = true
            try {
                // Pass empty polygon so capture produces a clean rectangular crop (best for OCR/math)
                val result = if (directBitmap != null && currentSize.width > 1 && currentSize.height > 1) {
                    DrawingScreenshotHelper.captureDirectBitmapRegion(
                        sourceBitmap = directBitmap,
                        strokes = strokesSnapshot,
                        canvasWidth = currentSize.width.toFloat(),
                        canvasHeight = currentSize.height.toFloat(),
                        localBounds = selBounds,
                        lassoPolygon = emptyList(),
                        pressureSensitivityEnabled = controller.pressureSensitivityEnabled
                    )
                } else {
                    // Hide dashed lasso border & floating bar cleanly for 2 frames before PixelCopy
                    withFrameNanos { }
                    withFrameNanos { }
                    delay(35L)

                    val liveCoords = canvasCoordinates
                    if (win != null && liveCoords != null && liveCoords.isAttached) {
                        DrawingScreenshotHelper.captureLassoRegion(
                            window = win,
                            canvasCoordinates = liveCoords,
                            localBounds = selBounds,
                            lassoPolygon = emptyList()
                        )
                    } else {
                        null
                    }
                }

                if (result != null) {
                    result.polygonBitmap?.let { if (!it.isRecycled) it.recycle() }
                    controller.clearSelection()
                    askCallback(result.rectBitmap)
                } else {
                    feedbackMessage = "Seçili alan yakalanamadı"
                }
            } finally {
                isCapturingScreenshot = false
            }
        }
    }

    // Listen to toolbar screenshot triggers
    LaunchedEffect(controller.screenshotRequestCounter) {
        if (controller.screenshotRequestCounter > 0) {
            triggerLassoScreenshot()
        }
    }

    // Listen to toolbar direct Ask AI triggers
    LaunchedEffect(controller.askAiRequestCounter) {
        if (controller.askAiRequestCounter > 0 && onLassoAskAi != null) {
            triggerLassoAskAi()
        }
    }

    val prefPressureEnabled by NoteDrawingPreferences.pressureSensitivityEnabled.collectAsStateWithLifecycle()
    val prefPressureStrength by NoteDrawingPreferences.pressureSensitivityStrength.collectAsStateWithLifecycle()
    val prefScribbleToErase by NoteDrawingPreferences.scribbleToEraseEnabled.collectAsStateWithLifecycle()
    val prefAutoShape by NoteDrawingPreferences.autoShapeRecognitionEnabled.collectAsStateWithLifecycle()
    val prefStylusOnly by NoteDrawingPreferences.stylusOnlyModeEnabled.collectAsStateWithLifecycle()
    val prefLastEraser by NoteDrawingPreferences.lastSelectedEraserTool.collectAsStateWithLifecycle()
    val prefStylusButtonsEnabled by NoteDrawingPreferences.stylusButtonsEnabled.collectAsStateWithLifecycle()
    val prefStylusHoldAction by NoteDrawingPreferences.stylusHoldAction.collectAsStateWithLifecycle()
    val prefStylusSingleTapAction by NoteDrawingPreferences.stylusSingleTapAction.collectAsStateWithLifecycle()
    val prefStylusDoubleTapAction by NoteDrawingPreferences.stylusDoubleTapAction.collectAsStateWithLifecycle()

    LaunchedEffect(controller, context) {
        controller.bindPreferencesContext(context)
    }

    LaunchedEffect(
        controller,
        prefPressureEnabled,
        prefPressureStrength,
        prefScribbleToErase,
        prefAutoShape,
        prefStylusOnly,
        prefLastEraser
    ) {
        controller.syncFromNotePreferences()
    }

    // Adapt strokes if loaded from a different canvas width (e.g. orientation change or PDF resize)
    LaunchedEffect(canvasSize.width, controller.strokes.size) {
        val currentW = canvasSize.width.toFloat()
        val currentH = canvasSize.height.toFloat()
        if (currentW > 10f && controller.strokes.isNotEmpty()) {
            val needsScale = controller.strokes.any {
                it.canvasWidth > 10f && abs(it.canvasWidth - currentW) > 4f
            }
            if (needsScale) {
                val rescaled = controller.strokes.map { stroke ->
                    if (stroke.canvasWidth > 10f && abs(stroke.canvasWidth - currentW) > 4f) {
                        val scale = currentW / stroke.canvasWidth
                        stroke.copy(
                            strokeWidth = (stroke.strokeWidth * scale).coerceIn(1f, 60f),
                            points = stroke.points.map { pt ->
                                DrawingPoint(pt.x * scale, pt.y * scale, pt.pressure)
                            },
                            canvasWidth = currentW,
                            canvasHeight = if (currentH > 10f) currentH else stroke.canvasHeight * scale
                        )
                    } else {
                        stroke
                    }
                }
                controller.setInitialStrokes(rescaled)
            }
        }
    }

    LaunchedEffect(controller.hasSelection) {
        if (!controller.hasSelection) {
            showLassoColorRow = false
        }
    }

    var lastStylusActivityMs by remember { mutableLongStateOf(0L) }
    val currentOnTwoFingerScroll by rememberUpdatedState(onTwoFingerScroll)

    val activeTool = controller.activeTool
    val stylusTrackerModifier = if (activeTool == DrawingTool.NONE || activeTool == DrawingTool.TEXT_BOX) {
        Modifier
    } else {
        Modifier.pointerInput(activeTool) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val hasStylus = event.changes.any {
                        it.type == PointerType.Stylus || it.type == PointerType.Eraser
                    }
                    if (hasStylus) {
                        lastStylusActivityMs = SystemClock.elapsedRealtime()
                    }
                }
            }
        }
    }

    val pointerModifier = if (activeTool == DrawingTool.NONE) {
        Modifier
    } else if (activeTool == DrawingTool.TEXT_BOX) {
        Modifier.pointerInput(activeTool, canvasSize, onTwoFingerScroll != null) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val startPos = down.position
                var movedTooFar = false
                var multiTouchScroll = false
                while (true) {
                    val event = awaitPointerEvent()
                    val pressed = event.changes.filter { it.pressed }
                    if (pressed.size >= 2) {
                        multiTouchScroll = true
                        val scrollCb = currentOnTwoFingerScroll
                        if (scrollCb != null) {
                            runTwoFingerScrollLoop(event, scrollCb)
                        }
                        break
                    }
                    val change = event.changes.firstOrNull { it.id == down.id }
                    if (change == null || !change.pressed) break
                    if ((change.position - startPos).getDistance() > 24f) {
                        movedTooFar = true
                    }
                }
                if (!multiTouchScroll && !movedTooFar && canvasSize.width > 10 && canvasSize.height > 10) {
                    if (controller.activeTextBoxId != null) {
                        // Outside tap cleanly commits & closes editor — NEVER deletes non-empty text
                        controller.closeActiveTextBox()
                    } else {
                        val tapXf = (startPos.x / canvasSize.width.toFloat()).coerceIn(0f, 1f)
                        val tapYf = (startPos.y / canvasSize.height.toFloat()).coerceIn(0f, 1f)
                        val hitBox = controller.textBoxes.lastOrNull { box ->
                            tapXf in (box.xFraction - 0.02f)..(box.xFraction + box.widthFraction + 0.02f) &&
                                tapYf in (box.yFraction - 0.02f)..(box.yFraction + 0.14f)
                        }
                        if (hitBox != null) {
                            controller.selectTextBox(hitBox.id)
                        } else {
                            val xf = tapXf.coerceIn(0.03f, 0.36f)
                            val yf = tapYf.coerceIn(0.03f, 0.82f)
                            controller.addTextBox(
                                xFraction = xf,
                                yFraction = yf,
                                widthFraction = 0.62f,
                                defaultColorArgb = defaultTextBoxColor.toArgb()
                            )
                        }
                    }
                }
            }
        }
    } else {
        Modifier.pointerInput(
            activeTool,
            controller.penColor,
            controller.strokeWidth,
            controller.alpha,
            controller.eraserRadius,
            controller.stylusOnlyMode,
            controller.smartPalmRejectionEnabled,
            controller.pressureSensitivityEnabled,
            controller.pressureSensitivityStrength,
            controller.scribbleToEraseEnabled,
            controller.autoShapeRecognitionEnabled,
            prefStylusButtonsEnabled,
            prefStylusHoldAction,
            prefLastEraser,
            onTwoFingerScroll != null
        ) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                if (down.type == PointerType.Stylus || down.type == PointerType.Eraser) {
                    lastStylusActivityMs = SystemClock.elapsedRealtime()
                }

                val shouldRejectTouch = down.type == PointerType.Touch && (
                    controller.stylusOnlyMode ||
                        (controller.smartPalmRejectionEnabled &&
                            SystemClock.elapsedRealtime() - lastStylusActivityMs < 1500L)
                    )

                var activeDown: PointerInputChange = down
                if (shouldRejectTouch) {
                    down.consume()
                    var promotedStylus: PointerInputChange? = null
                    while (true) {
                        val event = awaitPointerEvent()
                        val pressedChanges = event.changes.filter { it.pressed }
                        if (pressedChanges.isEmpty()) break

                        val stylusPressed = pressedChanges.firstOrNull {
                            it.type == PointerType.Stylus || it.type == PointerType.Eraser
                        }
                        if (stylusPressed != null) {
                            lastStylusActivityMs = SystemClock.elapsedRealtime()
                            stylusPressed.consume()
                            promotedStylus = stylusPressed
                            break
                        }

                        val touchPressedCount = pressedChanges.count { it.type == PointerType.Touch }
                        val scrollCallback = currentOnTwoFingerScroll
                        if (touchPressedCount >= 2 && scrollCallback != null) {
                            runTwoFingerScrollLoop(event, scrollCallback)
                            break
                        }

                        event.changes.forEach { it.consume() }
                    }

                    if (promotedStylus == null) {
                        return@awaitEachGesture
                    }
                    activeDown = promotedStylus
                } else {
                    down.consume()
                }

                val stylusSideButtonHeld = prefStylusButtonsEnabled &&
                    activeDown.type == PointerType.Stylus &&
                    (currentEvent.buttons.isSecondaryPressed || currentEvent.buttons.isTertiaryPressed)

                val effectiveTool = when {
                    activeDown.type == PointerType.Eraser -> prefLastEraser
                    stylusSideButtonHeld && prefStylusHoldAction == StylusHoldAction.ERASER -> prefLastEraser
                    stylusSideButtonHeld && prefStylusHoldAction == StylusHoldAction.LASSO -> DrawingTool.LASSO
                    stylusSideButtonHeld && prefStylusHoldAction == StylusHoldAction.PEN -> DrawingTool.BALLPOINT_PEN
                    else -> activeTool
                }

                val gestureStartMs = SystemClock.elapsedRealtime()
                var lastSampleTimeMs = gestureStartMs
                val startX = activeDown.position.x
                val startY = activeDown.position.y
                val initialPressure = resolvePointerPressure(
                    change = activeDown,
                    prevPoint = null,
                    prevTimeMs = gestureStartMs,
                    nowTimeMs = gestureStartMs,
                    enabled = controller.pressureSensitivityEnabled,
                    strength = controller.pressureSensitivityStrength
                )
                val startPoint = DrawingPoint(startX, startY, initialPressure)

                inProgressPoints.clear()
                snappedLiveShape = null
                var snappedBaseShape: RecognizedShape? = null
                var snapAnchorPos = Offset(startX, startY)
                var holdAnchorPos = Offset(startX, startY)
                var holdAnchorMs = gestureStartMs
                var lastPointerPos = Offset(startX, startY)
                val canHoldSnap = controller.autoShapeRecognitionEnabled &&
                    effectiveTool.isPenTool &&
                    effectiveTool.supportsAutoShape &&
                    effectiveTool != DrawingTool.UNDERLINE_MARKER
                var cancelledByMultiTouch = false

                when {
                    effectiveTool.isEraserTool -> {
                        controller.beginEraserGesture()
                        eraserCursorPos = activeDown.position
                        if (effectiveTool == DrawingTool.STROKE_ERASER) {
                            controller.eraseStrokeAt(startX, startY, controller.eraserRadius)
                        } else {
                            controller.eraseAreaAt(startX, startY, controller.eraserRadius)
                        }
                    }
                    effectiveTool == DrawingTool.LASSO -> {
                        val selBounds = controller.cropRegionBounds()?.inflate(18f)
                        if (selBounds != null && selBounds.contains(startX, startY)) {
                            isMovingLassoSelection = true
                            controller.beginMoveSelection()
                        } else {
                            isMovingLassoSelection = false
                            controller.clearSelection()
                            inProgressPoints.add(startPoint)
                        }
                    }
                    effectiveTool.isShapeTool || effectiveTool == DrawingTool.UNDERLINE_MARKER -> {
                        inProgressPoints.add(startPoint)
                        inProgressPoints.add(startPoint)
                    }
                    else -> {
                        // Freehand pen / highlighter / technical pen
                        inProgressPoints.add(startPoint)
                    }
                }

                while (true) {
                    val event = if (canHoldSnap && snappedBaseShape == null && inProgressPoints.size >= 6) {
                        withTimeoutOrNull(140L) { awaitPointerEvent() }
                    } else {
                        awaitPointerEvent()
                    }
                    if (event == null) {
                        // Pointer stayed still: check if hold-to-snap threshold (~420ms) reached
                        val nowHoldMs = SystemClock.elapsedRealtime()
                        if (canHoldSnap && snappedBaseShape == null && (nowHoldMs - holdAnchorMs) >= 420L) {
                            val recognized = AutoShapeRecognizer.recognize(inProgressPoints.toList())
                            if (recognized != null) {
                                snappedBaseShape = recognized
                                snappedLiveShape = recognized
                                snapAnchorPos = lastPointerPos
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                feedbackMessage = "${recognized.labelTr} kilitlendi • Sürükleyerek boyutlandır"
                            } else {
                                // Prevent repeated futile checks while resting motionless
                                holdAnchorMs = nowHoldMs + 10_000L
                            }
                        }
                        continue
                    }
                    val pressedChanges = event.changes.filter { it.pressed }
                    val pressedCount = pressedChanges.size

                    val hasStylusInEvent = event.changes.any {
                        it.type == PointerType.Stylus || it.type == PointerType.Eraser
                    }
                    if (hasStylusInEvent) {
                        lastStylusActivityMs = SystemClock.elapsedRealtime()
                    }

                    if (activeDown.type == PointerType.Touch &&
                        controller.smartPalmRejectionEnabled &&
                        inProgressPoints.size <= 6 &&
                        hasStylusInEvent
                    ) {
                        cancelledByMultiTouch = true
                        inProgressPoints.clear()
                        eraserCursorPos = null
                        if (effectiveTool.isEraserTool) {
                            controller.endEraserGesture()
                        } else if (isMovingLassoSelection) {
                            isMovingLassoSelection = false
                        }
                        event.changes.forEach { it.consume() }
                        break
                    }

                    if (activeDown.type == PointerType.Touch &&
                        pressedCount >= 2 &&
                        inProgressPoints.size <= 6 &&
                        !isMovingLassoSelection
                    ) {
                        // Multi-touch gesture (e.g. 2-finger scroll or pinch zoom): abort stroke preview
                        cancelledByMultiTouch = true
                        inProgressPoints.clear()
                        eraserCursorPos = null
                        if (effectiveTool.isEraserTool) {
                            controller.endEraserGesture()
                        }
                        val scrollCallback = currentOnTwoFingerScroll
                        if (scrollCallback != null) {
                            runTwoFingerScrollLoop(event, scrollCallback)
                        }
                        break
                    }

                    val change = event.changes.firstOrNull { it.id == activeDown.id }
                    if (change == null || !change.pressed) {
                        break
                    }

                    val pos = change.position
                    val delta = change.positionChange()
                    val nowMs = SystemClock.elapsedRealtime()
                    change.consume()

                    when {
                        effectiveTool.isEraserTool -> {
                            eraserCursorPos = pos
                            if (effectiveTool == DrawingTool.STROKE_ERASER) {
                                controller.eraseStrokeAt(pos.x, pos.y, controller.eraserRadius)
                            } else {
                                controller.eraseAreaAt(pos.x, pos.y, controller.eraserRadius)
                            }
                        }
                        effectiveTool == DrawingTool.LASSO -> {
                            if (isMovingLassoSelection) {
                                controller.moveSelectionBy(delta.x, delta.y)
                            } else {
                                val last = inProgressPoints.lastOrNull()
                                val current = DrawingPoint(pos.x, pos.y, 1.0f)
                                if (last == null || last.distanceTo(current) >= 3f) {
                                    inProgressPoints.add(current)
                                }
                            }
                        }
                        effectiveTool == DrawingTool.UNDERLINE_MARKER -> {
                            val first = inProgressPoints.firstOrNull() ?: startPoint
                            // Snap to clean horizontal underline unless dragged steeply (> 25 deg)
                            val dx = abs(pos.x - first.x)
                            val dy = abs(pos.y - first.y)
                            val endPt = if (dx > dy * 2.0f) {
                                DrawingPoint(pos.x, first.y, 1.0f)
                            } else {
                                DrawingPoint(pos.x, pos.y, 1.0f)
                            }
                            if (inProgressPoints.size >= 2) {
                                inProgressPoints[1] = endPt
                            } else {
                                inProgressPoints.add(endPt)
                            }
                        }
                        effectiveTool.isShapeTool -> {
                            val current = DrawingPoint(pos.x, pos.y, 1.0f)
                            if (inProgressPoints.size >= 2) {
                                inProgressPoints[1] = current
                            } else {
                                inProgressPoints.add(current)
                            }
                        }
                        else -> {
                            lastPointerPos = pos
                            val lockedBase = snappedBaseShape
                            if (lockedBase != null) {
                                snappedLiveShape = AutoShapeRecognizer.resizeRecognizedShape(
                                    baseShape = lockedBase,
                                    anchorX = snapAnchorPos.x,
                                    anchorY = snapAnchorPos.y,
                                    currentX = pos.x,
                                    currentY = pos.y
                                )
                            } else {
                                val last = inProgressPoints.lastOrNull()
                                val pointPressure = resolvePointerPressure(
                                    change = change,
                                    prevPoint = last,
                                    prevTimeMs = lastSampleTimeMs,
                                    nowTimeMs = nowMs,
                                    enabled = controller.pressureSensitivityEnabled,
                                    strength = controller.pressureSensitivityStrength
                                )
                                val current = DrawingPoint(pos.x, pos.y, pointPressure)
                                if (last == null || last.distanceTo(current) >= 2.2f) {
                                    inProgressPoints.add(current)
                                    lastSampleTimeMs = nowMs
                                }
                                if (hypot(pos.x - holdAnchorPos.x, pos.y - holdAnchorPos.y) > 10f) {
                                    holdAnchorPos = pos
                                    holdAnchorMs = nowMs
                                } else if (canHoldSnap && inProgressPoints.size >= 6 && (nowMs - holdAnchorMs) >= 420L) {
                                    val recognized = AutoShapeRecognizer.recognize(inProgressPoints.toList())
                                    if (recognized != null) {
                                        snappedBaseShape = recognized
                                        snappedLiveShape = recognized
                                        snapAnchorPos = pos
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        feedbackMessage = "${recognized.labelTr} kilitlendi • Sürükleyerek boyutlandır"
                                    } else {
                                        holdAnchorMs = nowMs + 10_000L
                                    }
                                }
                            }
                        }
                    }
                }

                // Gesture finished
                eraserCursorPos = null
                if (!cancelledByMultiTouch) {
                    when {
                        effectiveTool.isEraserTool -> {
                            controller.endEraserGesture()
                        }
                        effectiveTool == DrawingTool.LASSO -> {
                            if (isMovingLassoSelection) {
                                controller.endMoveSelection()
                                isMovingLassoSelection = false
                            } else if (inProgressPoints.size >= 3) {
                                controller.selectByLassoPolygon(inProgressPoints.toList())
                            }
                            inProgressPoints.clear()
                        }
                        effectiveTool.isShapeTool || effectiveTool == DrawingTool.UNDERLINE_MARKER -> {
                            if (inProgressPoints.size >= 2) {
                                val p0 = inProgressPoints.first()
                                val p1 = inProgressPoints.last()
                                if (p0.distanceTo(p1) >= 4f) {
                                    controller.addStroke(
                                        DrawingStroke(
                                            tool = effectiveTool,
                                            shapeKind = effectiveTool.toShapeKind(),
                                            colorArgb = controller.penColor.toArgb(),
                                            strokeWidth = controller.strokeWidth,
                                            alpha = controller.alpha,
                                            points = listOf(p0, p1),
                                            canvasWidth = canvasSize.width.toFloat().coerceAtLeast(1f),
                                            canvasHeight = canvasSize.height.toFloat().coerceAtLeast(1f),
                                            pressureSensitive = false
                                        )
                                    )
                                }
                            }
                            inProgressPoints.clear()
                        }
                        effectiveTool.isPenTool -> {
                            if (inProgressPoints.isNotEmpty()) {
                                val gestureDurationMs = SystemClock.elapsedRealtime() - gestureStartMs
                                val rawPoints = inProgressPoints.toList()
                                val isFreehandPen = effectiveTool.isPenTool &&
                                    effectiveTool != DrawingTool.UNDERLINE_MARKER

                                val erasedByScribble = if (
                                    controller.scribbleToEraseEnabled &&
                                    isFreehandPen &&
                                    controller.strokes.isNotEmpty() &&
                                    ScribbleDetector.isScribbleGesture(rawPoints, gestureDurationMs)
                                ) {
                                    controller.eraseStrokesByScribble(rawPoints)
                                } else {
                                    0
                                }

                                if (erasedByScribble > 0) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    feedbackMessage = "Karalama ile $erasedByScribble çizim silindi"
                                } else {
                                    val recognizedShape = snappedLiveShape
                                    snappedLiveShape = null

                                    if (recognizedShape != null) {
                                        controller.addStroke(
                                            DrawingStroke(
                                                tool = effectiveTool,
                                                shapeKind = recognizedShape.shapeKind,
                                                colorArgb = controller.penColor.toArgb(),
                                                strokeWidth = controller.strokeWidth,
                                                alpha = controller.alpha,
                                                points = recognizedShape.points,
                                                canvasWidth = canvasSize.width.toFloat().coerceAtLeast(1f),
                                                canvasHeight = canvasSize.height.toFloat().coerceAtLeast(1f),
                                                pressureSensitive = false
                                            )
                                        )
                                    } else {
                                        val finalPoints = if (rawPoints.size == 1) {
                                            val p = rawPoints.first()
                                            listOf(p, DrawingPoint(p.x + 0.5f, p.y + 0.5f, p.pressure))
                                        } else {
                                            rawPoints
                                        }
                                        controller.addStroke(
                                            DrawingStroke(
                                                tool = effectiveTool,
                                                shapeKind = ShapeKind.NONE,
                                                colorArgb = controller.penColor.toArgb(),
                                                strokeWidth = controller.strokeWidth,
                                                alpha = controller.alpha,
                                                points = finalPoints,
                                                canvasWidth = canvasSize.width.toFloat().coerceAtLeast(1f),
                                                canvasHeight = canvasSize.height.toFloat().coerceAtLeast(1f),
                                                pressureSensitive = controller.pressureSensitivityEnabled
                                            )
                                        )
                                    }
                                }
                            }
                            inProgressPoints.clear()
                        }
                        else -> {
                            inProgressPoints.clear()
                        }
                    }
                }
            }
        }
    }

    val stylusDoubleTapModifier = if (
        prefStylusButtonsEnabled &&
        (prefStylusSingleTapAction != StylusSingleTapAction.NONE || prefStylusDoubleTapAction != StylusDoubleTapAction.NONE)
    ) {
        Modifier.pointerInput(prefStylusButtonsEnabled, prefStylusSingleTapAction, prefStylusDoubleTapAction, prefLastEraser) {
            var prevBtnPressed = false
            var pressDownMs = 0L
            var lastTapReleaseMs = 0L
            var touchedWhileHeld = false
            var pendingSingleTapJob: Job? = null

            fun dispatchSingleTap() {
                if (prefStylusSingleTapAction == StylusSingleTapAction.NONE) return
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                when (prefStylusSingleTapAction) {
                    StylusSingleTapAction.PEN -> {
                        controller.selectTool(DrawingTool.BALLPOINT_PEN)
                        feedbackMessage = "Kalem seçildi"
                    }
                    StylusSingleTapAction.ERASER -> {
                        controller.selectTool(prefLastEraser)
                        feedbackMessage = "Silgi seçildi"
                    }
                    StylusSingleTapAction.LASSO -> {
                        controller.selectTool(DrawingTool.LASSO)
                        feedbackMessage = "Kement seçildi"
                    }
                    StylusSingleTapAction.UNDO -> {
                        if (controller.canUndo) {
                            controller.undo()
                            feedbackMessage = "Geri alındı"
                        }
                    }
                    StylusSingleTapAction.NONE -> Unit
                }
            }

            fun dispatchDoubleTap() {
                if (prefStylusDoubleTapAction == StylusDoubleTapAction.NONE) return
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                when (prefStylusDoubleTapAction) {
                    StylusDoubleTapAction.LASSO -> {
                        controller.selectTool(DrawingTool.LASSO)
                        feedbackMessage = "Kement seçildi"
                    }
                    StylusDoubleTapAction.ERASER -> {
                        val nextTool = if (controller.activeTool.isEraserTool) DrawingTool.BALLPOINT_PEN else prefLastEraser
                        controller.selectTool(nextTool)
                        feedbackMessage = if (nextTool.isEraserTool) "Silgi seçildi" else "Kalem seçildi"
                    }
                    StylusDoubleTapAction.PEN -> {
                        controller.selectTool(DrawingTool.BALLPOINT_PEN)
                        feedbackMessage = "Kalem seçildi"
                    }
                    StylusDoubleTapAction.UNDO -> {
                        if (controller.canUndo) {
                            controller.undo()
                            feedbackMessage = "Geri alındı"
                        }
                    }
                    StylusDoubleTapAction.NONE -> Unit
                }
            }

            awaitPointerEventScope {
                while (true) {
                    val ev = awaitPointerEvent(PointerEventPass.Initial)
                    val isStylusOrHover = ev.changes.any { it.type == PointerType.Stylus || it.type == PointerType.Eraser }
                    val anyTouchDown = ev.changes.any { it.pressed }
                    val btnPressed = ev.buttons.isSecondaryPressed || ev.buttons.isTertiaryPressed
                    if (btnPressed && anyTouchDown) {
                        touchedWhileHeld = true
                    }
                    if (isStylusOrHover && btnPressed && !prevBtnPressed) {
                        pressDownMs = SystemClock.elapsedRealtime()
                        touchedWhileHeld = anyTouchDown
                    } else if (!btnPressed && prevBtnPressed) {
                        val now = SystemClock.elapsedRealtime()
                        val pressDur = now - pressDownMs
                        if (!touchedWhileHeld && pressDur in 10L..350L) {
                            if (now - lastTapReleaseMs in 30L..340L && prefStylusDoubleTapAction != StylusDoubleTapAction.NONE) {
                                pendingSingleTapJob?.cancel()
                                pendingSingleTapJob = null
                                lastTapReleaseMs = 0L
                                dispatchDoubleTap()
                            } else {
                                lastTapReleaseMs = now
                                pendingSingleTapJob?.cancel()
                                if (prefStylusSingleTapAction != StylusSingleTapAction.NONE) {
                                    if (prefStylusDoubleTapAction == StylusDoubleTapAction.NONE) {
                                        dispatchSingleTap()
                                    } else {
                                        pendingSingleTapJob = coroutineScope.launch {
                                            delay(340L)
                                            dispatchSingleTap()
                                        }
                                    }
                                }
                            }
                        }
                        touchedWhileHeld = false
                    }
                    prevBtnPressed = btnPressed
                }
            }
        }
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .then(stylusTrackerModifier)
            .onSizeChanged { canvasSize = it }
            .onGloballyPositioned { coords -> canvasCoordinates = coords }
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .then(stylusDoubleTapModifier)
                .then(pointerModifier)
        ) {
            val selectedIds = controller.selectedStrokeIds

            // 1. Draw all committed strokes
            for (stroke in controller.strokes) {
                val isSelected = !isCapturingScreenshot && stroke.id in selectedIds
                if (isSelected) {
                    // Draw subtle selection halo behind selected stroke
                    drawStrokeElement(
                        stroke = stroke.copy(
                            colorArgb = Color(0xFF00B0FF).toArgb(),
                            strokeWidth = stroke.strokeWidth + 6f,
                            alpha = 0.28f
                        ),
                        pressureSensitivityEnabled = controller.pressureSensitivityEnabled && stroke.pressureSensitive
                    )
                }
                drawStrokeElement(
                    stroke = stroke,
                    pressureSensitivityEnabled = controller.pressureSensitivityEnabled && stroke.pressureSensitive
                )
            }

            // 2. Draw active in-progress stroke / shape / lasso
            if (inProgressPoints.isNotEmpty() && !isCapturingScreenshot) {
                if (activeTool == DrawingTool.LASSO) {
                    drawLassoLoop(inProgressPoints)
                } else if (snappedLiveShape != null) {
                    val locked = snappedLiveShape!!
                    val previewStroke = DrawingStroke(
                        id = "preview_snapped",
                        tool = activeTool,
                        shapeKind = locked.shapeKind,
                        colorArgb = controller.penColor.toArgb(),
                        strokeWidth = controller.strokeWidth,
                        alpha = controller.alpha,
                        points = locked.points,
                        pressureSensitive = false
                    )
                    drawStrokeElement(
                        stroke = previewStroke,
                        pressureSensitivityEnabled = false
                    )
                } else if (activeTool.isPenTool || activeTool.isShapeTool) {
                    val previewStroke = DrawingStroke(
                        id = "preview",
                        tool = activeTool,
                        shapeKind = activeTool.toShapeKind(),
                        colorArgb = controller.penColor.toArgb(),
                        strokeWidth = controller.strokeWidth,
                        alpha = controller.alpha,
                        points = inProgressPoints.toList(),
                        pressureSensitive = controller.pressureSensitivityEnabled
                    )
                    drawStrokeElement(
                        stroke = previewStroke,
                        pressureSensitivityEnabled = controller.pressureSensitivityEnabled
                    )
                }
            }

            // 3. Draw Lasso selection bounding box (and free-region polygon contour) when not capturing
            if (!isCapturingScreenshot) {
                if (controller.lassoPolygon.size >= 3) {
                    drawLassoLoop(controller.lassoPolygon)
                }

                val selBounds = controller.cropRegionBounds()
                if (selBounds != null) {
                    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
                    val topLeft = Offset(selBounds.left, selBounds.top)
                    val rectSize = Size(selBounds.width, selBounds.height)

                    if (controller.hasSelection || controller.lassoPolygon.size < 3) {
                        drawRect(
                            color = Color(0xFF0288D1).copy(alpha = 0.08f),
                            topLeft = topLeft,
                            size = rectSize
                        )
                    }
                    drawRect(
                        color = Color(0xFF0288D1),
                        topLeft = topLeft,
                        size = rectSize,
                        style = Stroke(width = 2.dp.toPx(), pathEffect = dashEffect)
                    )

                    val handleRadius = 4.5.dp.toPx()
                    val corners = listOf(
                        Offset(selBounds.left, selBounds.top),
                        Offset(selBounds.right, selBounds.top),
                        Offset(selBounds.right, selBounds.bottom),
                        Offset(selBounds.left, selBounds.bottom)
                    )
                    for (corner in corners) {
                        drawCircle(color = Color.White, radius = handleRadius + 1.5f, center = corner)
                        drawCircle(color = Color(0xFF0288D1), radius = handleRadius, center = corner)
                    }
                }
            }

            // 4. Draw Eraser cursor circle
            eraserCursorPos?.let { cursor ->
                val r = controller.eraserRadius
                drawCircle(
                    color = Color(0xFFEF4444).copy(alpha = 0.18f),
                    radius = r,
                    center = cursor
                )
                drawCircle(
                    color = Color(0xFFEF4444).copy(alpha = 0.75f),
                    radius = r,
                    center = cursor,
                    style = Stroke(width = 1.5.dp.toPx())
                )
            }
        }

        // 5. Rich-Text Boxes Layer (GoodNotes-style: editable in TEXT_BOX mode, cleanly visible & preserved always)
        if (controller.textBoxes.isNotEmpty() && canvasSize.width > 0 && canvasSize.height > 0) {
            val isTextBoxToolActive = activeTool == DrawingTool.TEXT_BOX && !isCapturingScreenshot
            val colorScheme = MaterialTheme.colorScheme
            val primaryColor = colorScheme.primary
            val surfaceColor = colorScheme.surface
            val canvasW = canvasSize.width.toFloat().coerceAtLeast(1f)
            val canvasH = canvasSize.height.toFloat().coerceAtLeast(1f)

            controller.textBoxes.forEach { box ->
                val isSelected = isTextBoxToolActive && controller.activeTextBoxId == box.id
                if (!isSelected && box.taggedContent.isBlank() && !isTextBoxToolActive) {
                    return@forEach
                }

                val offsetX = (box.xFraction * canvasW).roundToInt()
                val offsetY = (box.yFraction * canvasH).roundToInt()
                val boxWidthDp = with(density) { (box.widthFraction * canvasW).toDp().coerceAtLeast(136.dp) }
                val baseFont = NoteFontOption.fromName(box.baseFontName).resolveFontFamily()
                val baseSizeSp = box.baseFontSizeSp.coerceIn(
                    NoteRichTextHelper.MIN_FONT_SIZE_SP,
                    NoteRichTextHelper.MAX_FONT_SIZE_SP
                ).sp
                val baseTextColor = box.baseTextColorArgb?.let { Color(it) } ?: defaultTextBoxColor

                if (isSelected) {
                    val focusRequester = remember(box.id) { FocusRequester() }
                    LaunchedEffect(box.id) {
                        runCatching { focusRequester.requestFocus() }
                    }
                    Surface(
                        modifier = Modifier
                            .zIndex(14f)
                            .offset { IntOffset(offsetX, offsetY) }
                            .width(boxWidthDp),
                        shape = RoundedCornerShape(14.dp),
                        color = surfaceColor.copy(alpha = 0.96f),
                        shadowElevation = 8.dp,
                        border = BorderStroke(1.5.dp, primaryColor)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            // GoodNotes-style header: Drag Handle + A-/A+ + Explicit Trash + Safe "Tamam" Save Pill
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(primaryColor.copy(alpha = 0.10f))
                                    .pointerInput(box.id, canvasW, canvasH) {
                                        detectDragGestures { change, dragAmount ->
                                            change.consume()
                                            val current = controller.textBoxes.firstOrNull { it.id == box.id } ?: return@detectDragGestures
                                            controller.moveTextBox(
                                                id = box.id,
                                                xFraction = current.xFraction + (dragAmount.x / canvasW),
                                                yFraction = current.yFraction + (dragAmount.y / canvasH)
                                            )
                                        }
                                    }
                                    .padding(horizontal = 8.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DragIndicator,
                                        contentDescription = "Taşı",
                                        tint = primaryColor,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Taşı",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = primaryColor.copy(alpha = 0.85f)
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    // Quick Font Size Stepper (A- / A+)
                                    Surface(
                                        onClick = { controller.stepTextBoxFontSize(-2f) },
                                        shape = RoundedCornerShape(6.dp),
                                        color = surfaceColor.copy(alpha = 0.9f),
                                        border = BorderStroke(0.8.dp, colorScheme.outlineVariant)
                                    ) {
                                        Text(
                                            text = "A−",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = colorScheme.onSurface,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                        )
                                    }
                                    Surface(
                                        onClick = { controller.stepTextBoxFontSize(2f) },
                                        shape = RoundedCornerShape(6.dp),
                                        color = surfaceColor.copy(alpha = 0.9f),
                                        border = BorderStroke(0.8.dp, colorScheme.outlineVariant)
                                    ) {
                                        Text(
                                            text = "A+",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = colorScheme.onSurface,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                        )
                                    }

                                    // Explicit Trash button (ONLY this deletes the box — never confused with Close!)
                                    Surface(
                                        onClick = { controller.deleteTextBox(box.id) },
                                        shape = RoundedCornerShape(6.dp),
                                        color = colorScheme.errorContainer.copy(alpha = 0.85f)
                                    ) {
                                        Box(
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 3.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Metin kutusunu sil",
                                                tint = colorScheme.error,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }

                                    // Explicit Save & Close pill ("Tamam") — commits & preserves text cleanly
                                    Surface(
                                        onClick = { controller.closeActiveTextBox() },
                                        shape = RoundedCornerShape(8.dp),
                                        color = primaryColor
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Kaydet ve Kapat",
                                                tint = colorScheme.onPrimary,
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Text(
                                                text = "Tamam",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = colorScheme.onPrimary
                                            )
                                        }
                                    }
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 42.dp)
                                    .padding(horizontal = 12.dp, vertical = 9.dp)
                            ) {
                                BasicTextField(
                                    value = controller.activeTextBoxFieldValue,
                                    onValueChange = { controller.onActiveTextBoxValueChange(it) },
                                    textStyle = TextStyle(
                                        color = baseTextColor,
                                        fontSize = baseSizeSp,
                                        fontFamily = baseFont,
                                        lineHeight = (box.baseFontSizeSp * 1.38f).sp
                                    ),
                                    cursorBrush = SolidColor(primaryColor),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .focusRequester(focusRequester),
                                    decorationBox = { innerTextField ->
                                        Box(modifier = Modifier.fillMaxWidth()) {
                                            if (controller.activeTextBoxFieldValue.text.isEmpty()) {
                                                Text(
                                                    text = "Notunuzu yazın...",
                                                    color = baseTextColor.copy(alpha = 0.42f),
                                                    fontSize = baseSizeSp,
                                                    fontFamily = baseFont
                                                )
                                            }
                                            innerTextField()
                                        }
                                    }
                                )
                            }

                            // Bottom-right horizontal width resize pill handle
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 10.dp, end = 6.dp, bottom = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${box.baseFontSizeSp.roundToInt()} pt",
                                    fontSize = 9.sp,
                                    color = colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(primaryColor.copy(alpha = 0.16f))
                                        .border(0.8.dp, primaryColor.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                        .pointerInput(box.id, canvasW) {
                                            detectDragGestures { change, dragAmount ->
                                                change.consume()
                                                val current = controller.textBoxes.firstOrNull { it.id == box.id } ?: return@detectDragGestures
                                                controller.resizeTextBox(
                                                    id = box.id,
                                                    widthFraction = current.widthFraction + (dragAmount.x / canvasW)
                                                )
                                            }
                                        }
                                        .padding(horizontal = 8.dp, vertical = 2.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "↔",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = primaryColor
                                    )
                                }
                            }
                        }
                    }
                } else {
                    val parsed = remember(box.taggedContent) {
                        NoteRichTextHelper.parseTaggedText(box.taggedContent)
                    }
                    val boxModifier = Modifier
                        .zIndex(if (isTextBoxToolActive) 7f else 2f)
                        .offset { IntOffset(offsetX, offsetY) }
                        .width(boxWidthDp)
                        .then(
                            if (isTextBoxToolActive) {
                                Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(surfaceColor.copy(alpha = 0.72f))
                                    .border(
                                        1.dp,
                                        primaryColor.copy(alpha = 0.48f),
                                        RoundedCornerShape(10.dp)
                                    )
                                    .pointerInput(box.id, canvasW, canvasH) {
                                        detectDragGestures { change, dragAmount ->
                                            change.consume()
                                            val current = controller.textBoxes.firstOrNull { it.id == box.id } ?: return@detectDragGestures
                                            controller.moveTextBox(
                                                id = box.id,
                                                xFraction = current.xFraction + (dragAmount.x / canvasW),
                                                yFraction = current.yFraction + (dragAmount.y / canvasH)
                                            )
                                        }
                                    }
                                    .clickable { controller.selectTextBox(box.id) }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            } else {
                                Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            }
                        )

                    Box(modifier = boxModifier) {
                        if (parsed.text.isEmpty() && isTextBoxToolActive) {
                            Text(
                                text = "Dokun ve yaz...",
                                color = baseTextColor.copy(alpha = 0.45f),
                                fontSize = baseSizeSp,
                                fontFamily = baseFont
                            )
                        } else if (parsed.text.isNotEmpty()) {
                            Text(
                                text = parsed,
                                style = TextStyle(
                                    color = baseTextColor,
                                    fontSize = baseSizeSp,
                                    fontFamily = baseFont,
                                    lineHeight = (box.baseFontSizeSp * 1.38f).sp
                                )
                            )
                        }
                    }
                }
            }
        }

        // Floating Contextual Action Bar above/below Lasso Selection (strokes OR free region)
        val selBounds = controller.cropRegionBounds()
        if (selBounds != null && canvasSize.width > 0 && !isCapturingScreenshot) {
            val maxBarWidthDp = with(density) { (canvasSize.width.toFloat() - 16f).coerceAtLeast(160f).toDp() }
            val estBarWidthPx = with(density) {
                if (controller.hasSelection) 330.dp.toPx() else 210.dp.toPx()
            }.coerceAtMost((canvasSize.width - 16f).coerceAtLeast(120f))
            val barHeightPx = with(density) { if (showLassoColorRow) 84.dp.toPx() else 46.dp.toPx() }
            val targetX = (selBounds.centerX - estBarWidthPx * 0.5f)
                .coerceIn(8f, (canvasSize.width - estBarWidthPx - 8f).coerceAtLeast(8f))
            val aboveY = selBounds.top - barHeightPx - with(density) { 10.dp.toPx() }
            val targetY = if (aboveY >= 8f) {
                aboveY
            } else {
                (selBounds.bottom + with(density) { 10.dp.toPx() })
                    .coerceAtMost((canvasSize.height - barHeightPx - 8f).coerceAtLeast(8f))
            }

            Surface(
                modifier = Modifier
                    .zIndex(10f)
                    .offset { IntOffset(targetX.roundToInt(), targetY.roundToInt()) }
                    .widthIn(max = maxBarWidthDp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {},
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                shadowElevation = 10.dp,
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (onLassoAskAi != null) {
                            LassoActionChip(
                                label = "AI'ya Sor",
                                icon = Icons.Default.AutoAwesome,
                                isHighlighted = true,
                                onClick = { triggerLassoAskAi() }
                            )
                        }
                        LassoActionChip(
                            label = "Kırp / SS",
                            icon = Icons.Default.Crop,
                            isHighlighted = onLassoAskAi == null,
                            onClick = { triggerLassoScreenshot() }
                        )
                        if (controller.hasSelection) {
                            LassoActionChip(
                                label = "Kes",
                                onClick = { controller.cutSelected() }
                            )
                            LassoActionChip(
                                label = "Kopyala",
                                onClick = { controller.copySelected() }
                            )
                        }
                        if (controller.hasClipboard) {
                            LassoActionChip(
                                label = "Yapıştır",
                                onClick = { controller.pasteClipboard() }
                            )
                        }
                        if (controller.hasSelection) {
                            IconButton(
                                onClick = { showLassoColorRow = !showLassoColorRow },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Palette,
                                    contentDescription = "Renk Değiştir",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                            IconButton(
                                onClick = { controller.deleteSelected() },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Seçimi Sil",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        }
                        IconButton(
                            onClick = { controller.clearSelection() },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Seçimi Kapat",
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    if (showLassoColorRow && controller.hasSelection) {
                        Row(
                            modifier = Modifier
                                .padding(top = 4.dp, bottom = 2.dp)
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            QUICK_LASSO_COLORS.forEach { swatch ->
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .clip(CircleShape)
                                        .background(swatch)
                                        .border(
                                            1.dp,
                                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                                            CircleShape
                                        )
                                        .clickable {
                                            controller.recolorSelected(swatch)
                                        }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Subtle Feedback Pill (Scribble-to-Erase / Screenshot Saved)
        AnimatedVisibility(
            visible = feedbackMessage != null && !isCapturingScreenshot,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 12.dp)
        ) {
            feedbackMessage?.let { msg ->
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.inverseSurface,
                    shadowElevation = 6.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoFixHigh,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.inversePrimary,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = msg,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.inverseOnSurface
                        )
                    }
                }
            }
        }

        // Cropped Screenshot Preview & Export Dialog
        capturedCrop?.let { crop ->
            val activeBitmap = if (usePolygonCrop && crop.polygonBitmap != null) {
                crop.polygonBitmap
            } else {
                crop.rectBitmap
            }
            val dismissPreview = {
                capturedCrop = null
                dialogStatusMessage = null
                pendingGalleryBitmap = null
            }

            Dialog(onDismissRequest = { if (!isSavingCrop) dismissPreview() }) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp,
                    shadowElevation = 14.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "Kırpılan Alan",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${activeBitmap.width} × ${activeBitmap.height} px",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                                )
                            }
                            IconButton(
                                onClick = { if (!isSavingCrop) dismissPreview() },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Kapat"
                                )
                            }
                        }

                        if (crop.polygonBitmap != null) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                FilterChip(
                                    selected = usePolygonCrop,
                                    onClick = { usePolygonCrop = true },
                                    label = { Text("Serbest Kement", fontSize = 12.sp) }
                                )
                                FilterChip(
                                    selected = !usePolygonCrop,
                                    onClick = { usePolygonCrop = false },
                                    label = { Text("Dikdörtgen", fontSize = 12.sp) }
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 120.dp, max = 280.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.outlineVariant,
                                    RoundedCornerShape(12.dp)
                                )
                                .padding(8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                bitmap = activeBitmap.asImageBitmap(),
                                contentDescription = "Kırpılan ekran görüntüsü",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        dialogStatusMessage?.let { status ->
                            Text(
                                text = status,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        if (onLassoAskAi != null) {
                            Button(
                                onClick = {
                                    if (isSavingCrop) return@Button
                                    val aiCopy = runCatching {
                                        crop.rectBitmap.copy(Bitmap.Config.ARGB_8888, false)
                                    }.getOrNull()
                                    dismissPreview()
                                    controller.clearSelection()
                                    if (aiCopy != null) {
                                        onLassoAskAi(aiCopy)
                                    } else {
                                        feedbackMessage = "Görsel AI için hazırlanamadı"
                                    }
                                },
                                enabled = !isSavingCrop,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("AI'ya Sor (Galeriye Kaydetmeden)", fontWeight = FontWeight.SemiBold)
                            }
                        }

                        Button(
                            onClick = {
                                if (isSavingCrop) return@Button
                                isSavingCrop = true
                                coroutineScope.launch {
                                    val result = DrawingScreenshotHelper.saveCropToNotes(context, activeBitmap)
                                    isSavingCrop = false
                                    if (result.isSuccess) {
                                        feedbackMessage = "Notlara eklendi"
                                        dismissPreview()
                                    } else {
                                        val detail = result.exceptionOrNull()?.localizedMessage?.takeIf { it.isNotBlank() }
                                        dialogStatusMessage = if (detail != null) "Not kaydedilemedi: $detail" else "Not kaydedilemedi"
                                    }
                                }
                            },
                            enabled = !isSavingCrop,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (isSavingCrop) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            } else {
                                Icon(
                                    imageVector = Icons.Default.StickyNote2,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text("Notlara Ekle", fontWeight = FontWeight.SemiBold)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    if (isSavingCrop) return@OutlinedButton
                                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                                        ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.WRITE_EXTERNAL_STORAGE
                                        ) != PackageManager.PERMISSION_GRANTED
                                    ) {
                                        pendingGalleryBitmap = activeBitmap
                                        galleryPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                                    } else {
                                        performGallerySave(activeBitmap)
                                    }
                                },
                                enabled = !isSavingCrop,
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PhotoLibrary,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Galeriye Kaydet", fontSize = 12.sp)
                            }

                            OutlinedButton(
                                onClick = {
                                    if (isSavingCrop) return@OutlinedButton
                                    coroutineScope.launch {
                                        val result = DrawingScreenshotHelper.shareBitmap(context, activeBitmap)
                                        if (result.isFailure) {
                                            dialogStatusMessage = "Paylaşım başlatılamadı"
                                        }
                                    }
                                },
                                enabled = !isSavingCrop,
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Paylaş", fontSize = 12.sp)
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(
                                onClick = { if (!isSavingCrop) dismissPreview() },
                                enabled = !isSavingCrop
                            ) {
                                Text("Kapat")
                            }
                        }
                    }
                }
            }
        }
    }
}

private suspend fun AwaitPointerEventScope.runTwoFingerScrollLoop(
    initialEvent: PointerEvent,
    onTwoFingerScroll: (deltaY: Float, deltaX: Float) -> Unit
) {
    var scrollEvent = initialEvent
    while (true) {
        val activePointers = scrollEvent.changes.filter { it.pressed }
        if (activePointers.isEmpty()) break
        if (activePointers.size >= 2) {
            var sumDx = 0f
            var sumDy = 0f
            for (ptr in activePointers) {
                val delta = ptr.positionChange()
                sumDx += delta.x
                sumDy += delta.y
                ptr.consume()
            }
            val count = activePointers.size.toFloat()
            val avgDx = sumDx / count
            val avgDy = sumDy / count
            if (avgDy != 0f || avgDx != 0f) {
                onTwoFingerScroll(avgDy, avgDx)
            }
        } else {
            activePointers.forEach { it.consume() }
        }
        scrollEvent = awaitPointerEvent()
    }
}

@Composable
private fun LassoActionChip(
    label: String,
    icon: ImageVector? = null,
    isHighlighted: Boolean = false,
    onClick: () -> Unit
) {
    val bgColor = if (isHighlighted) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
    }
    val fgColor = if (isHighlighted) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onPrimaryContainer
    }
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = bgColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = fgColor,
                    modifier = Modifier.size(13.dp)
                )
            }
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = fgColor
            )
        }
    }
}

/**
 * Normalizes stylus or touch pressure from [PointerInputChange]:
 * - [PointerType.Stylus]: directly reads high-precision hardware pressure in `0.05f..1.5f`
 *   (falls back to `0.65f` if device reports `0.0f`).
 * - [PointerType.Touch]: uses hardware touch pressure when varied, or falls back to
 *   velocity-based natural taper when the touchscreen reports a constant `1.0f`.
 */
internal fun resolvePointerPressure(
    change: PointerInputChange,
    prevPoint: DrawingPoint?,
    prevTimeMs: Long,
    nowTimeMs: Long,
    enabled: Boolean,
    strength: Float = 1.15f
): Float {
    if (!enabled) return 1.0f
    val gain = strength.coerceIn(0.5f, 1.8f)
    fun modulate(base: Float): Float {
        return (0.85f + (base - 0.85f) * gain).coerceIn(0.04f, 1.95f)
    }

    val raw = change.pressure
    if (change.type == PointerType.Stylus) {
        val norm = if (raw <= 0.001f) 0.72f else raw.coerceIn(0.02f, 1.40f)
        val curved = modulate((norm.pow(0.70f) * 1.38f).coerceIn(0.04f, 1.88f))
        val prevP = prevPoint?.pressure ?: curved
        return (prevP * 0.16f + curved * 0.84f).coerceIn(0.04f, 1.95f)
    }

    // Touch or other pointer types
    if (raw > 0.01f && abs(raw - 1.0f) > 0.015f) {
        val curved = modulate((raw.coerceIn(0.05f, 1.40f).pow(0.75f) * 1.25f).coerceIn(0.08f, 1.65f))
        val prevP = prevPoint?.pressure ?: curved
        return (prevP * 0.25f + curved * 0.75f).coerceIn(0.08f, 1.85f)
    }

    // Velocity-based natural taper when touch reports constant 1.0f
    if (prevPoint == null) return 0.85f
    val dist = kotlin.math.hypot(change.position.x - prevPoint.x, change.position.y - prevPoint.y)
    val dt = (nowTimeMs - prevTimeMs).coerceAtLeast(4L).toFloat()
    val speedPxPerMs = dist / dt
    val velocityPressure = modulate((1.42f - (speedPxPerMs * 0.28f)).coerceIn(0.20f, 1.50f))
    return (prevPoint.pressure * 0.35f + velocityPressure * 0.65f).coerceIn(0.14f, 1.85f)
}

/**
 * Renders a single [DrawingStroke] (either freehand pen/highlighter or geometric shape) onto [DrawScope].
 */
fun DrawScope.drawStrokeElement(
    stroke: DrawingStroke,
    pressureSensitivityEnabled: Boolean = stroke.pressureSensitive
) {
    val pts = stroke.points
    if (pts.isEmpty()) return

    val paintColor = if (stroke.tool == DrawingTool.PENCIL) {
        Color(stroke.colorArgb).copy(alpha = (stroke.alpha * 0.78f).coerceIn(0.15f, 0.92f))
    } else {
        stroke.composeColor
    }
    val widthPx = stroke.strokeWidth.coerceAtLeast(1f)
    val cap = when (stroke.tool) {
        DrawingTool.HIGHLIGHTER -> StrokeCap.Square
        DrawingTool.TECHNICAL_PEN -> StrokeCap.Round
        else -> StrokeCap.Round
    }
    val join = when (stroke.tool) {
        DrawingTool.TECHNICAL_PEN -> StrokeJoin.Miter
        else -> StrokeJoin.Round
    }

    if (stroke.shapeKind != ShapeKind.NONE && pts.size >= 2) {
        val start = Offset(pts.first().x, pts.first().y)
        val end = Offset(pts.last().x, pts.last().y)
        val left = min(start.x, end.x)
        val right = max(start.x, end.x)
        val top = min(start.y, end.y)
        val bottom = max(start.y, end.y)
        val w = (right - left).coerceAtLeast(1f)
        val h = (bottom - top).coerceAtLeast(1f)

        when (stroke.shapeKind) {
            ShapeKind.LINE -> {
                drawLine(
                    color = paintColor,
                    start = start,
                    end = end,
                    strokeWidth = widthPx,
                    cap = cap
                )
            }
            ShapeKind.ARROW -> {
                drawLine(
                    color = paintColor,
                    start = start,
                    end = end,
                    strokeWidth = widthPx,
                    cap = StrokeCap.Round
                )
                val angle = atan2((end.y - start.y).toDouble(), (end.x - start.x).toDouble())
                val headLength = (widthPx * 4.2f).coerceIn(18f, 48f)
                val wingAngle = PI / 6.0
                val wing1 = Offset(
                    x = (end.x - headLength * cos(angle - wingAngle)).toFloat(),
                    y = (end.y - headLength * sin(angle - wingAngle)).toFloat()
                )
                val wing2 = Offset(
                    x = (end.x - headLength * cos(angle + wingAngle)).toFloat(),
                    y = (end.y - headLength * sin(angle + wingAngle)).toFloat()
                )
                val arrowHeadPath = Path().apply {
                    moveTo(wing1.x, wing1.y)
                    lineTo(end.x, end.y)
                    lineTo(wing2.x, wing2.y)
                }
                drawPath(
                    path = arrowHeadPath,
                    color = paintColor,
                    style = Stroke(width = widthPx, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }
            ShapeKind.RECTANGLE -> {
                drawRect(
                    color = paintColor,
                    topLeft = Offset(left, top),
                    size = Size(w, h),
                    style = Stroke(width = widthPx, cap = cap, join = join)
                )
            }
            ShapeKind.ELLIPSE -> {
                drawOval(
                    color = paintColor,
                    topLeft = Offset(left, top),
                    size = Size(w, h),
                    style = Stroke(width = widthPx, cap = cap, join = join)
                )
            }
            ShapeKind.TRIANGLE -> {
                val triPath = Path().apply {
                    if (pts.size >= 3) {
                        moveTo(pts[0].x, pts[0].y)
                        lineTo(pts[1].x, pts[1].y)
                        lineTo(pts[2].x, pts[2].y)
                    } else {
                        moveTo((left + right) * 0.5f, top)
                        lineTo(right, bottom)
                        lineTo(left, bottom)
                    }
                    close()
                }
                drawPath(
                    path = triPath,
                    color = paintColor,
                    style = Stroke(width = widthPx, cap = cap, join = StrokeJoin.Round)
                )
            }
            ShapeKind.NONE -> {}
        }
        return
    }

    // Freehand stroke rendering with smooth quadratic Bezier curves
    if (pts.size == 1) {
        val p0 = pts[0]
        val pFactor = if (pressureSensitivityEnabled && stroke.pressureSensitive) {
            when (stroke.tool) {
                DrawingTool.BRUSH_PEN -> (0.14f + 2.05f * p0.pressure.coerceIn(0.04f, 1.95f)).coerceIn(0.14f, 2.85f)
                DrawingTool.BALLPOINT_PEN -> (0.84f + 0.26f * p0.pressure.coerceIn(0.04f, 1.95f)).coerceIn(0.75f, 1.32f)
                DrawingTool.PENCIL -> (0.55f + 0.72f * p0.pressure.coerceIn(0.04f, 1.95f)).coerceIn(0.45f, 1.65f)
                else -> (0.16f + 1.50f * p0.pressure.coerceIn(0.04f, 1.95f)).coerceIn(0.16f, 2.40f)
            }
        } else {
            1.0f
        }
        drawCircle(
            color = paintColor,
            radius = (widthPx * pFactor * 0.5f).coerceAtLeast(0.55f),
            center = Offset(p0.x, p0.y)
        )
        return
    }

    val isPressurePen = pressureSensitivityEnabled &&
        stroke.pressureSensitive &&
        (stroke.tool == DrawingTool.FOUNTAIN_PEN ||
            stroke.tool == DrawingTool.TECHNICAL_PEN ||
            stroke.tool == DrawingTool.BRUSH_PEN ||
            stroke.tool == DrawingTool.PENCIL ||
            stroke.tool == DrawingTool.BALLPOINT_PEN)

    if (isPressurePen) {
        var prevAnchorX = pts[0].x
        var prevAnchorY = pts[0].y
        val segPath = Path()
        val isBrush = stroke.tool == DrawingTool.BRUSH_PEN
        val isPencil = stroke.tool == DrawingTool.PENCIL
        val isBallpoint = stroke.tool == DrawingTool.BALLPOINT_PEN
        val pencilGrainColor = if (isPencil) {
            Color(stroke.colorArgb).copy(alpha = (stroke.alpha * 0.26f).coerceIn(0.06f, 0.34f))
        } else {
            Color.Transparent
        }

        for (i in 1 until pts.size) {
            val prev = pts[i - 1]
            val curr = pts[i]
            val endX = if (i == pts.lastIndex) curr.x else (prev.x + curr.x) * 0.5f
            val endY = if (i == pts.lastIndex) curr.y else (prev.y + curr.y) * 0.5f

            val pPrev2 = pts[(i - 2).coerceAtLeast(0)].pressure.coerceIn(0.04f, 1.95f)
            val pPrev = prev.pressure.coerceIn(0.04f, 1.95f)
            val pCurr = curr.pressure.coerceIn(0.04f, 1.95f)
            val smoothedPressure = pPrev2 * 0.20f + pPrev * 0.45f + pCurr * 0.35f

            val edgeTaper = when {
                isBallpoint -> 1.0f
                isBrush && pts.size >= 5 && i == 1 -> 0.55f
                isBrush && pts.size >= 5 && i == pts.lastIndex -> 0.48f
                pts.size >= 5 && i == 1 -> 0.78f
                pts.size >= 5 && i == pts.lastIndex -> 0.74f
                else -> 1.0f
            }

            val widthI = when {
                isBrush -> (widthPx * (0.12f + 2.05f * smoothedPressure) * edgeTaper)
                    .coerceIn(max(0.55f, widthPx * 0.12f), widthPx * 2.85f)
                isBallpoint -> (widthPx * (0.84f + 0.26f * smoothedPressure))
                    .coerceIn(max(0.75f, widthPx * 0.76f), widthPx * 1.32f)
                isPencil -> (widthPx * (0.55f + 0.72f * smoothedPressure) * edgeTaper)
                    .coerceIn(max(0.55f, widthPx * 0.45f), widthPx * 1.65f)
                else -> (widthPx * (0.14f + 1.52f * smoothedPressure) * edgeTaper)
                    .coerceIn(max(0.45f, widthPx * 0.14f), widthPx * 2.45f)
            }

            val segColor = if (isPencil) {
                val pencilAlpha = (stroke.alpha * (0.52f + 0.36f * smoothedPressure)).coerceIn(0.18f, 0.90f)
                Color(stroke.colorArgb).copy(alpha = pencilAlpha)
            } else {
                paintColor
            }

            segPath.reset()
            segPath.moveTo(prevAnchorX, prevAnchorY)
            segPath.quadraticTo(prev.x, prev.y, endX, endY)
            drawPath(
                path = segPath,
                color = segColor,
                style = Stroke(
                    width = widthI,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
            if (isPencil) {
                // Subtle offset graphite texture pass for authentic pencil feel
                segPath.reset()
                segPath.moveTo(prevAnchorX + 0.75f, prevAnchorY + 0.55f)
                segPath.quadraticTo(prev.x + 0.75f, prev.y + 0.55f, endX + 0.75f, endY + 0.55f)
                drawPath(
                    path = segPath,
                    color = pencilGrainColor,
                    style = Stroke(
                        width = (widthI * 0.65f).coerceAtLeast(0.6f),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }
            prevAnchorX = endX
            prevAnchorY = endY
        }
        return
    }

    // Highlighter or constant-width stroke
    val effectiveColor = if (pressureSensitivityEnabled && stroke.pressureSensitive && stroke.tool == DrawingTool.HIGHLIGHTER) {
        var sumP = 0f
        for (pt in pts) sumP += pt.pressure.coerceIn(0.2f, 1.4f)
        val avgP = sumP / pts.size.toFloat()
        val modulatedAlpha = (stroke.alpha * (0.78f + 0.26f * avgP)).coerceIn(0.08f, 0.85f)
        Color(stroke.colorArgb).copy(alpha = modulatedAlpha)
    } else {
        paintColor
    }

    val path = Path().apply {
        moveTo(pts[0].x, pts[0].y)
        for (i in 1 until pts.size) {
            val prev = pts[i - 1]
            val curr = pts[i]
            val midX = (prev.x + curr.x) * 0.5f
            val midY = (prev.y + curr.y) * 0.5f
            quadraticTo(prev.x, prev.y, midX, midY)
        }
        val last = pts.last()
        lineTo(last.x, last.y)
    }

    drawPath(
        path = path,
        color = effectiveColor,
        style = Stroke(
            width = widthPx,
            cap = cap,
            join = join
        )
    )
    if (stroke.tool == DrawingTool.PENCIL) {
        val grainPath = Path().apply {
            moveTo(pts[0].x + 0.75f, pts[0].y + 0.55f)
            for (i in 1 until pts.size) {
                val prev = pts[i - 1]
                val curr = pts[i]
                val midX = (prev.x + curr.x) * 0.5f + 0.75f
                val midY = (prev.y + curr.y) * 0.5f + 0.55f
                quadraticTo(prev.x + 0.75f, prev.y + 0.55f, midX, midY)
            }
            val last = pts.last()
            lineTo(last.x + 0.75f, last.y + 0.55f)
        }
        drawPath(
            path = grainPath,
            color = Color(stroke.colorArgb).copy(alpha = (stroke.alpha * 0.24f).coerceIn(0.06f, 0.30f)),
            style = Stroke(
                width = (widthPx * 0.65f).coerceAtLeast(0.6f),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
    }
}

private fun DrawScope.drawLassoLoop(points: List<DrawingPoint>) {
    if (points.size < 2) return
    val path = Path().apply {
        moveTo(points[0].x, points[0].y)
        for (i in 1 until points.size) {
            lineTo(points[i].x, points[i].y)
        }
        close()
    }
    drawPath(
        path = path,
        color = Color(0xFF0288D1).copy(alpha = 0.10f)
    )
    drawPath(
        path = path,
        color = Color(0xFF0288D1),
        style = Stroke(
            width = 2.dp.toPx(),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f), 0f)
        )
    )
}
