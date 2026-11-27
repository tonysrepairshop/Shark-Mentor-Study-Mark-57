package com.example.sharkmentor

import com.example.sharkmentor.core.ai.SharkTokenQuotaManager
import com.example.sharkmentor.core.audio.MusicPlaybackState
import com.example.sharkmentor.core.audio.MusicQueueItem
import com.example.sharkmentor.data.topbar.TopBarModule
import com.example.sharkmentor.data.topbar.TopBarSettings
import com.example.sharkmentor.ui.screens.notes.drawing.StylusHardwareInputBridge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Mark75FeaturesTest {

    @Test
    fun stylusHardwareBridgeDetectsSamsungSPenAndHonorMagicPencilButtonsWithoutFalsePositives() {
        // Samsung S-Pen barrel buttons (BUTTON_STYLUS_PRIMARY = 0x20, BUTTON_STYLUS_SECONDARY = 0x40)
        assertTrue(StylusHardwareInputBridge.isStylusMotionButtonPressed(StylusHardwareInputBridge.MASK_BUTTON_STYLUS_PRIMARY))
        assertTrue(StylusHardwareInputBridge.isStylusMotionButtonPressed(StylusHardwareInputBridge.MASK_BUTTON_STYLUS_SECONDARY))
        assertTrue(StylusHardwareInputBridge.isStylusMotionButtonPressed(0, StylusHardwareInputBridge.MASK_BUTTON_STYLUS_PRIMARY))
        // Primary finger/tip touch bit (0x01) must NEVER trigger barrel button
        assertFalse(StylusHardwareInputBridge.isStylusMotionButtonPressed(0x01))
        assertFalse(StylusHardwareInputBridge.isStylusMotionButtonPressed(0))

        // Android 14+ & Honor Magic-Pencil / Samsung S-Pen Pro hardware stylus keycodes
        assertTrue(StylusHardwareInputBridge.isStylusHardwareKeyCode(StylusHardwareInputBridge.KEYCODE_STYLUS_PRIMARY, isFromStylusSource = false))
        assertTrue(StylusHardwareInputBridge.isStylusHardwareKeyCode(StylusHardwareInputBridge.KEYCODE_STYLUS_SECONDARY, isFromStylusSource = false))
        assertTrue(StylusHardwareInputBridge.isStylusHardwareKeyCode(StylusHardwareInputBridge.KEYCODE_STYLUS_TERTIARY, isFromStylusSource = false))
        assertTrue(StylusHardwareInputBridge.isStylusHardwareKeyCode(StylusHardwareInputBridge.KEYCODE_STYLUS_TAIL, isFromStylusSource = false))
        // Honor/OEM Bluetooth stylus F20 (150) keycode with stylus source
        assertTrue(StylusHardwareInputBridge.isStylusHardwareKeyCode(150, isFromStylusSource = true))
        assertFalse(StylusHardwareInputBridge.isStylusHardwareKeyCode(150, isFromStylusSource = false))

        // Standard Back (4), Volume Up (24), Volume Down (25) must NEVER be captured as stylus button
        assertFalse(StylusHardwareInputBridge.isStylusHardwareKeyCode(4, isFromStylusSource = true))
        assertFalse(StylusHardwareInputBridge.isStylusHardwareKeyCode(24, isFromStylusSource = true))
        assertFalse(StylusHardwareInputBridge.isStylusHardwareKeyCode(25, isFromStylusSource = true))

        // Bluetooth capacitive double-tap / secondary key mapping
        assertTrue(StylusHardwareInputBridge.isNativeSecondaryOrDoubleTapKey(StylusHardwareInputBridge.KEYCODE_STYLUS_SECONDARY, isBluetoothStylus = false))
        assertTrue(StylusHardwareInputBridge.isNativeSecondaryOrDoubleTapKey(StylusHardwareInputBridge.KEYCODE_STYLUS_TAIL, isBluetoothStylus = false))
        assertTrue(StylusHardwareInputBridge.isNativeSecondaryOrDoubleTapKey(StylusHardwareInputBridge.KEYCODE_STYLUS_PRIMARY, isBluetoothStylus = true))
        assertFalse(StylusHardwareInputBridge.isNativeSecondaryOrDoubleTapKey(StylusHardwareInputBridge.KEYCODE_STYLUS_PRIMARY, isBluetoothStylus = false))
    }

    @Test
    fun musicPlaybackStateCarriesStructuredTappableQueueItems() {
        val items = listOf(
            MusicQueueItem(queueId = 42L, title = "Interstellar Main Theme", subtitle = "Hans Zimmer"),
            MusicQueueItem(queueId = 43L, title = "Time", subtitle = "Hans Zimmer")
        )
        val state = MusicPlaybackState(
            hasActiveSession = true,
            isPlaying = true,
            title = "Cornfield Chase",
            queueTitles = items.map { it.title },
            queueItems = items
        )
        assertEquals(2, state.queueItems.size)
        assertEquals(42L, state.queueItems[0].queueId)
        assertEquals("Time", state.queueTitles[1])
    }

    @Test
    fun appLogoRemainsPermanentlyFixedAndCannotBeHiddenInTopBar() {
        val normalized = TopBarModule.normalizeOrder(
            listOf(TopBarModule.WEATHER, TopBarModule.MUSIC, TopBarModule.BRANDING)
        )
        assertEquals(TopBarModule.BRANDING, normalized.first())
        assertEquals(TopBarModule.DEFAULT_ORDER.size, normalized.size)

        // Even if BRANDING appears in a legacy hiddenModules set, isModuleVisible MUST stay true
        val settings = TopBarSettings(hiddenModules = setOf(TopBarModule.BRANDING, TopBarModule.COUNTDOWN))
        assertTrue(settings.isModuleVisible(TopBarModule.BRANDING))
        assertFalse(settings.isModuleVisible(TopBarModule.COUNTDOWN))
    }

    @Test
    fun tokenEstimatorReturnsSafeNonZeroFallbackForBuiltInQuotaRecording() {
        val estimated = SharkTokenQuotaManager.estimateTokens("Soru metni", "Detaylı çözüm")
        assertTrue("Estimated tokens must be >= 64", estimated >= 64L)
        assertEquals("80.0K", SharkTokenQuotaManager.formatCompactTokens(80_000L))
    }
}
