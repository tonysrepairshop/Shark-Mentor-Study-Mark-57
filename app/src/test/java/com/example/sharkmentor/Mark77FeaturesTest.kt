package com.example.sharkmentor

import com.example.sharkmentor.data.dashboard.DashboardLayoutState
import com.example.sharkmentor.data.dashboard.DashboardWidgetSize
import com.example.sharkmentor.data.dashboard.DashboardWidgetType
import com.example.sharkmentor.ui.navigation.Screen
import com.example.sharkmentor.ui.screens.notes.drawing.AutoShapeRecognizer
import com.example.sharkmentor.ui.screens.notes.drawing.DrawingPoint
import com.example.sharkmentor.ui.screens.notes.drawing.HandwritingRecognitionHelpers
import com.example.sharkmentor.ui.screens.notes.drawing.ScribbleDetector
import com.example.sharkmentor.ui.screens.notes.drawing.ShapeKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class Mark77FeaturesTest {

    @Test
    fun bottomBarHasDedicatedPanelAndMasaTabsWithCleanSingleWordLabels() {
        val labels = Screen.bottomBarItems.map { it.label }
        assertEquals(listOf("Panel", "Masa", "Planlar", "Stüdyo", "Shark", "Hata"), labels)
        assertEquals(Screen.DashboardHome, Screen.bottomBarItems.first())
        assertTrue(labels.all { !it.contains(" ") && it.length <= 8 })
    }

    @Test
    fun dashboardLayoutSupportsResizeReorderToggleRowPackingAndSerialization() {
        var state = DashboardLayoutState()
        // Default has 4 consecutive SMALL tiles (ERROR_BANK, STUDIO_NOTES, LIBRARY_BOOKS, TODAY_PLAN)
        val rowsPhone = state.buildVisualRows(maxSmallPerRow = 2)
        assertTrue(rowsPhone.any { it.size == 2 && it.all { p -> p.size == DashboardWidgetSize.SMALL } })
        val rowsTablet = state.buildVisualRows(maxSmallPerRow = 3)
        assertTrue(rowsTablet.any { it.size == 3 })

        // Resize LIVE_TIMER to SMALL and move it up to index 0
        state = state.setSize(DashboardWidgetType.LIVE_TIMER, DashboardWidgetSize.SMALL)
        state = state.moveUp(DashboardWidgetType.LIVE_TIMER)
        assertEquals(DashboardWidgetType.LIVE_TIMER, state.visiblePlacements.first().type)
        assertEquals(DashboardWidgetSize.SMALL, state.visiblePlacements.first().size)

        // Hide QUICK_TOOLS
        state = state.toggleWidget(DashboardWidgetType.QUICK_TOOLS)
        assertTrue(state.hiddenPlacements.any { it.type == DashboardWidgetType.QUICK_TOOLS })

        // Round-trip serialize/deserialize
        val raw = DashboardLayoutState.serialize(state)
        val restored = DashboardLayoutState.deserialize(raw)
        assertEquals(state.placements, restored.placements)
    }

    @Test
    fun autoShapeRecognizerDetectsSparseAndOvershotCirclesWhileIgnoringSmallHandwriting() {
        // Fast sparse hand-drawn circle (only 10 raw points) -> uniform resampling makes it snap!
        val sparseCircle = (0..10).map { i ->
            val theta = (2.0 * PI * i) / 10.0
            DrawingPoint(
                x = 200f + (82f * cos(theta)).toFloat(),
                y = 200f + (78f * sin(theta)).toFloat()
            )
        }
        val sparseRes = AutoShapeRecognizer.recognize(sparseCircle)
        assertNotNull("Fast sparse hand-drawn circle must be recognized", sparseRes)
        assertEquals(ShapeKind.ELLIPSE, sparseRes!!.shapeKind)

        // Hand-drawn circle "O" with a natural 45-degree overshoot tail past start point
        val overshotCircle = (0..36).map { i ->
            val theta = (2.25 * PI * i) / 36.0
            val r = 84f + if (i > 32) (i - 32) * 3.5f else 0f
            DrawingPoint(
                x = 220f + (r * cos(theta)).toFloat(),
                y = 220f + (r * sin(theta)).toFloat()
            )
        }
        val overshootRes = AutoShapeRecognizer.recognize(overshotCircle)
        assertNotNull("Circle with natural overshoot tail must be recognized", overshootRes)
        assertEquals(ShapeKind.ELLIPSE, overshootRes!!.shapeKind)

        // Small handwritten letter "o" (~34px diameter) and short letter "l" (~55px) must NOT snap
        val smallHandwritingO = (0..20).map { i ->
            val theta = (2.0 * PI * i) / 20.0
            DrawingPoint(100f + (17f * cos(theta)).toFloat(), 100f + (17f * sin(theta)).toFloat())
        }
        assertNull(AutoShapeRecognizer.recognize(smallHandwritingO))

        val shortLetterL = (0..12).map { i ->
            DrawingPoint(100f, 100f + i * 4.5f)
        }
        assertNull(AutoShapeRecognizer.recognize(shortLetterL))
    }

    @Test
    fun scribbleDetectorProtectsCursiveHandwritingAndErasesDeliberateBackAndForthScribbles() {
        // Simulated connected cursive handwriting marching left-to-right ("mmmmmm" / "Merhaba")
        val cursiveWord = mutableListOf<DrawingPoint>()
        var x = 40f
        for (hump in 0 until 8) {
            cursiveWord.add(DrawingPoint(x, 150f))
            cursiveWord.add(DrawingPoint(x + 8f, 118f))
            cursiveWord.add(DrawingPoint(x + 16f, 150f))
            x += 18f
        }
        assertFalse(
            "Connected left-to-right handwriting must NEVER trigger Scribble-to-Erase",
            ScribbleDetector.isScribbleGesture(cursiveWord, durationMs = 750L)
        )

        // Deliberate dense back-and-forth scribble over the SAME word region (x oscillates 100 <-> 210)
        val deliberateScribble = listOf(
            DrawingPoint(100f, 120f),
            DrawingPoint(210f, 124f),
            DrawingPoint(105f, 134f),
            DrawingPoint(208f, 140f),
            DrawingPoint(102f, 150f),
            DrawingPoint(212f, 156f),
            DrawingPoint(106f, 166f),
            DrawingPoint(204f, 170f)
        )
        assertTrue(
            "Deliberate overlapping back-and-forth scribble MUST trigger Scribble-to-Erase",
            ScribbleDetector.isScribbleGesture(deliberateScribble, durationMs = 620L)
        )
    }

    @Test
    fun handwritingRecognitionHelpersAnalyzeCalibrationAndBuildFewShotPromptCleanly() {
        assertEquals(3, HandwritingRecognitionHelpers.CALIBRATION_PROMPTS.size)
        assertEquals("SHARK", HandwritingRecognitionHelpers.CALIBRATION_PROMPTS.first().guideText)

        val sampleStrokes = listOf(
            listOf(DrawingPoint(20f, 40f), DrawingPoint(24f, 90f)),
            listOf(DrawingPoint(40f, 42f), DrawingPoint(46f, 92f))
        )
        val profile = HandwritingRecognitionHelpers.analyzeCalibrationGeometry(
            strokeGroups = sampleStrokes,
            completedPrompts = listOf("SHARK", "Merhaba, bugün çalışıyorum.")
        )
        assertTrue(profile.isCalibrated)
        assertEquals(2, profile.sampleCount)
        assertTrue(profile.avgStrokeHeightPx >= 45f)

        val calibratedPrompt = HandwritingRecognitionHelpers.buildRecognitionPrompt(profile, hasReferenceSheet = true)
        assertTrue(calibratedPrompt.contains("Merhaba, bugün çalışıyorum."))
        assertTrue(calibratedPrompt.contains("kalibrasyon"))

        val uncalibratedPrompt = HandwritingRecognitionHelpers.buildRecognitionPrompt(profile, hasReferenceSheet = false)
        assertFalse(uncalibratedPrompt.contains("kalibrasyon örneğidir"))

        assertEquals(
            "Merhaba dünya",
            HandwritingRecognitionHelpers.cleanRecognizedHandwritingOutput("```text\n\"Merhaba dünya\"\n```")
        )
    }
}
