package com.example.sharkmentor.core.timer

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Lightweight event bus used when a Home Screen widget (e.g. Duolingo-style SharkStreakWidget)
 * asks to start a focus session. Instead of blindly starting a hardcoded "Genel Odak" timer in
 * the background, MainActivity navigates to HomeScreen and triggers the Focus Mode + Topic/Tag
 * picker cleanly (or reveals the already-running timer without conflicts).
 */
object FocusLaunchNavBus {
    private val _openFocusLaunchPickerRequested = MutableStateFlow(false)
    val openFocusLaunchPickerRequested: StateFlow<Boolean> = _openFocusLaunchPickerRequested.asStateFlow()

    fun requestOpenFocusLaunchPicker() {
        _openFocusLaunchPickerRequested.value = true
    }

    fun consumeOpenFocusLaunchPicker() {
        _openFocusLaunchPickerRequested.value = false
    }
}
