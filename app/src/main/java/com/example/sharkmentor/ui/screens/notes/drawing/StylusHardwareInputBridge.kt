package com.example.sharkmentor.ui.screens.notes.drawing

import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicInteger

enum class StylusHardwareClickType {
    SINGLE_TAP,
    DOUBLE_TAP
}

data class StylusHardwareClickEvent(
    val type: StylusHardwareClickType,
    val signalDescription: String,
    val timestampMs: Long = SystemClock.elapsedRealtime()
)

/**
 * Low-level Android stylus hardware button bridge supporting BOTH:
 * - Samsung S-Pen / S-Pen Pro (`MotionEvent.BUTTON_STYLUS_PRIMARY`, `BUTTON_STYLUS_SECONDARY`,
 *   `ACTION_BUTTON_PRESS`/`RELEASE` on hover & touch, `TOOL_TYPE_ERASER`, and Air Actions key events)
 * - Honor Magic-Pencil 2/3 & Android 14+ universal styluses (`KEYCODE_STYLUS_BUTTON_PRIMARY/SECONDARY/
 *   TERTIARY/TAIL`, `SOURCE_BLUETOOTH_STYLUS`, and OEM capacitive barrel double-tap key events)
 *
 * Why this exists: Jetpack Compose's `PointerButtons.isSecondaryPressed` only checks mouse right-click
 * (`MotionEvent.BUTTON_SECONDARY = 0x02`) and ignores `BUTTON_STYLUS_PRIMARY (0x20)`, hover button
 * events, and hardware stylus `KeyEvent`s. Hooking the raw Android Window/Activity dispatch layer
 * guarantees reliable detection on real tablets and phones.
 */
object StylusHardwareInputBridge {

    // Raw Android SDK bitmasks & keycodes kept as explicit constants for API 26..35 compatibility
    const val MASK_BUTTON_SECONDARY = 0x02        // MotionEvent.BUTTON_SECONDARY
    const val MASK_BUTTON_TERTIARY = 0x04         // MotionEvent.BUTTON_TERTIARY
    const val MASK_BUTTON_STYLUS_PRIMARY = 0x20   // MotionEvent.BUTTON_STYLUS_PRIMARY (32)
    const val MASK_BUTTON_STYLUS_SECONDARY = 0x40 // MotionEvent.BUTTON_STYLUS_SECONDARY (64)
    const val MASK_ALL_STYLUS_BUTTONS =
        MASK_BUTTON_STYLUS_PRIMARY or MASK_BUTTON_STYLUS_SECONDARY or MASK_BUTTON_SECONDARY or MASK_BUTTON_TERTIARY

    const val KEYCODE_STYLUS_PRIMARY = 308        // KeyEvent.KEYCODE_STYLUS_BUTTON_PRIMARY (API 34)
    const val KEYCODE_STYLUS_SECONDARY = 309      // KeyEvent.KEYCODE_STYLUS_BUTTON_SECONDARY (API 34)
    const val KEYCODE_STYLUS_TERTIARY = 310       // KeyEvent.KEYCODE_STYLUS_BUTTON_TERTIARY (API 34)
    const val KEYCODE_STYLUS_TAIL = 311           // KeyEvent.KEYCODE_STYLUS_BUTTON_TAIL (API 34)

    private const val SOURCE_BLUETOOTH_STYLUS_MASK = 0x0000C002 // InputDevice.SOURCE_BLUETOOTH_STYLUS
    private const val DOUBLE_TAP_WINDOW_MS = 400L
    private const val SINGLE_TAP_DEBOUNCE_MS = 340L
    private const val MAX_TAP_HOLD_MS = 450L

    private val mainHandler: Handler by lazy { Handler(Looper.getMainLooper()) }
    private val activeListenerCount = AtomicInteger(0)

    private val _isStylusButtonHeld = MutableStateFlow(false)
    val isStylusButtonHeld: StateFlow<Boolean> = _isStylusButtonHeld.asStateFlow()

    private val _isStylusDetected = MutableStateFlow(false)
    val isStylusDetected: StateFlow<Boolean> = _isStylusDetected.asStateFlow()

    private val _lastSignalLabel = MutableStateFlow("")
    val lastSignalLabel: StateFlow<String> = _lastSignalLabel.asStateFlow()

    private val _clickEvents = MutableSharedFlow<StylusHardwareClickEvent>(extraBufferCapacity = 16)
    val clickEvents: SharedFlow<StylusHardwareClickEvent> = _clickEvents.asSharedFlow()

    @Volatile
    private var motionButtonDown = false
    @Volatile
    private var keyButtonDown = false
    @Volatile
    private var pressDownMs = 0L
    @Volatile
    private var lastTapReleaseMs = 0L
    @Volatile
    private var touchedWhileHeld = false
    private var pendingSingleTapRunnable: Runnable? = null

    fun registerActiveListener() {
        activeListenerCount.incrementAndGet()
    }

    fun unregisterActiveListener() {
        val remaining = activeListenerCount.decrementAndGet().coerceAtLeast(0)
        if (remaining == 0) {
            activeListenerCount.set(0)
            resetTransientState()
        }
    }

    fun hasActiveListener(): Boolean = activeListenerCount.get() > 0

    fun resetTransientState() {
        cancelPendingSingleTap()
        motionButtonDown = false
        keyButtonDown = false
        touchedWhileHeld = false
        _isStylusButtonHeld.value = false
    }

    fun clearDiagnosticLabel() {
        _lastSignalLabel.value = ""
    }

    /**
     * Pure JVM-testable predicate checking whether raw Android [MotionEvent] button bitmasks
     * contain ANY stylus barrel button (`BUTTON_STYLUS_PRIMARY`, `BUTTON_STYLUS_SECONDARY`,
     * or mapped secondary/tertiary button).
     */
    fun isStylusMotionButtonPressed(buttonState: Int, actionButton: Int = 0): Boolean {
        return ((buttonState or actionButton) and MASK_ALL_STYLUS_BUTTONS) != 0
    }

    /**
     * Pure JVM-testable predicate checking whether an Android [KeyEvent] keyCode represents
     * a stylus side/barrel/tail button on Android 14+ (Samsung S-Pen Pro, Honor Magic-Pencil, etc.).
     */
    fun isStylusHardwareKeyCode(keyCode: Int, isFromStylusSource: Boolean): Boolean {
        if (keyCode in KEYCODE_STYLUS_PRIMARY..KEYCODE_STYLUS_TAIL) return true
        // Ignore standard system/navigation/volume/text keys even if a hybrid input device reports them
        val isReservedSystemKey = keyCode == KeyEvent.KEYCODE_BACK ||
            keyCode == KeyEvent.KEYCODE_HOME ||
            keyCode == KeyEvent.KEYCODE_VOLUME_UP ||
            keyCode == KeyEvent.KEYCODE_VOLUME_DOWN ||
            keyCode == KeyEvent.KEYCODE_VOLUME_MUTE ||
            keyCode == KeyEvent.KEYCODE_POWER ||
            keyCode == KeyEvent.KEYCODE_MENU ||
            keyCode == KeyEvent.KEYCODE_APP_SWITCH ||
            keyCode == KeyEvent.KEYCODE_ENTER ||
            keyCode == KeyEvent.KEYCODE_DEL
        if (isReservedSystemKey) return false

        // OEM Bluetooth stylus double-tap / barrel keycodes (Honor / Huawei / Samsung Air Actions)
        val isOemStylusKey = keyCode in 148..154 || // F18..F24
            keyCode == KeyEvent.KEYCODE_STEM_PRIMARY ||
            keyCode == KeyEvent.KEYCODE_STEM_1 ||
            keyCode == KeyEvent.KEYCODE_BUTTON_THUMBL ||
            keyCode == KeyEvent.KEYCODE_BUTTON_THUMBR
        return isFromStylusSource && (isOemStylusKey || keyCode >= 260)
    }

    /**
     * Pure JVM-testable check determining whether a stylus hardware keyCode is natively
     * a dedicated secondary/double-tap/tail gesture (e.g. Honor Magic-Pencil capacitive double-tap).
     */
    fun isNativeSecondaryOrDoubleTapKey(keyCode: Int, isBluetoothStylus: Boolean): Boolean {
        if (keyCode == KEYCODE_STYLUS_SECONDARY || keyCode == KEYCODE_STYLUS_TAIL) return true
        if (isBluetoothStylus && keyCode == KEYCODE_STYLUS_PRIMARY) return true
        return false
    }

    /**
     * Inspects every raw [MotionEvent] from Activity/Dialog `dispatchTouchEvent` and
     * `dispatchGenericMotionEvent` (which includes hover + `ACTION_BUTTON_PRESS`/`RELEASE`!).
     */
    fun onRawMotionEvent(event: MotionEvent?) {
        if (event == null || !hasActiveListener()) return
        if (!NoteDrawingPreferences.stylusButtonsEnabled.value) {
            if (_isStylusButtonHeld.value) resetTransientState()
            return
        }

        val action = event.actionMasked
        val pointerCount = event.pointerCount
        val toolType = if (pointerCount > 0) event.getToolType(0) else MotionEvent.TOOL_TYPE_UNKNOWN
        val isStylusTool = toolType == MotionEvent.TOOL_TYPE_STYLUS || toolType == MotionEvent.TOOL_TYPE_ERASER
        val isStylusSource = event.isFromSource(InputDevice.SOURCE_STYLUS) ||
            (event.source and SOURCE_BLUETOOTH_STYLUS_MASK) == SOURCE_BLUETOOTH_STYLUS_MASK

        if (isStylusTool || isStylusSource) {
            _isStylusDetected.value = true
        }

        // Only evaluate buttonState when event is from a stylus OR carries an explicit stylus button flag
        val btnState = event.buttonState
        val actionBtn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            (action == MotionEvent.ACTION_BUTTON_PRESS || action == MotionEvent.ACTION_BUTTON_RELEASE)
        ) {
            event.actionButton
        } else {
            0
        }

        val hasExplicitStylusBit = ((btnState or actionBtn) and (MASK_BUTTON_STYLUS_PRIMARY or MASK_BUTTON_STYLUS_SECONDARY)) != 0
        val relevantStylusEvent = isStylusTool || isStylusSource || hasExplicitStylusBit
        if (!relevantStylusEvent) return

        val pressedNow = when (action) {
            MotionEvent.ACTION_BUTTON_PRESS -> isStylusMotionButtonPressed(btnState, actionBtn)
            MotionEvent.ACTION_BUTTON_RELEASE -> isStylusMotionButtonPressed(btnState and actionBtn.inv(), 0)
            MotionEvent.ACTION_CANCEL -> false
            else -> isStylusMotionButtonPressed(btnState, 0)
        }

        val isTouchingGlass = action == MotionEvent.ACTION_DOWN ||
            action == MotionEvent.ACTION_MOVE ||
            action == MotionEvent.ACTION_POINTER_DOWN
        if (pressedNow && isTouchingGlass) {
            touchedWhileHeld = true
        }

        val wasHeld = motionButtonDown
        if (pressedNow && !wasHeld) {
            motionButtonDown = true
            pressDownMs = SystemClock.elapsedRealtime()
            touchedWhileHeld = isTouchingGlass
            val maskHex = "0x${(btnState or actionBtn).toString(16)}"
            _lastSignalLabel.value = "Yan Tuş Basılı ($maskHex)"
            updateHeldState()
        } else if (!pressedNow && wasHeld) {
            motionButtonDown = false
            updateHeldState()
            val now = SystemClock.elapsedRealtime()
            val holdDuration = now - pressDownMs
            val maskHex = "0x${(actionBtn.takeIf { it != 0 } ?: MASK_BUTTON_STYLUS_PRIMARY).toString(16)}"
            if (!touchedWhileHeld && holdDuration <= MAX_TAP_HOLD_MS) {
                handleQuickButtonRelease(now, "Kalem Yan Tuşu ($maskHex)", forceDoubleTap = false)
            } else {
                _lastSignalLabel.value = "Yan Tuş Bırakıldı ($maskHex)"
            }
            touchedWhileHeld = false
        }
    }

    /**
     * Inspects every raw [KeyEvent] from Activity/Dialog `dispatchKeyEvent`.
     * Returns `true` ONLY when a real stylus hardware button event was handled while a drawing
     * canvas or calibration pad is active, so standard navigation/system keys are never affected.
     */
    fun onRawKeyEvent(event: KeyEvent?): Boolean {
        if (event == null || !hasActiveListener()) return false
        if (!NoteDrawingPreferences.stylusButtonsEnabled.value) return false

        val keyCode = event.keyCode
        val deviceSources = event.device?.sources ?: event.source
        val isBluetoothStylus = (deviceSources and SOURCE_BLUETOOTH_STYLUS_MASK) == SOURCE_BLUETOOTH_STYLUS_MASK ||
            (event.source and SOURCE_BLUETOOTH_STYLUS_MASK) == SOURCE_BLUETOOTH_STYLUS_MASK
        val isStylusSource = isBluetoothStylus ||
            event.isFromSource(InputDevice.SOURCE_STYLUS) ||
            (deviceSources and InputDevice.SOURCE_STYLUS) == InputDevice.SOURCE_STYLUS

        if (!isStylusHardwareKeyCode(keyCode, isStylusSource)) return false

        _isStylusDetected.value = true
        val now = SystemClock.elapsedRealtime()
        val label = "Kalem Tuşu (Key $keyCode)"

        when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                if (event.repeatCount == 0) {
                    keyButtonDown = true
                    pressDownMs = now
                    touchedWhileHeld = false
                    _lastSignalLabel.value = "$label Basılı"
                    updateHeldState()
                }
                return true
            }
            KeyEvent.ACTION_UP -> {
                keyButtonDown = false
                updateHeldState()
                val holdDuration = now - pressDownMs
                val treatAsDoubleTap = isNativeSecondaryOrDoubleTapKey(keyCode, isBluetoothStylus)
                if (!touchedWhileHeld && holdDuration <= MAX_TAP_HOLD_MS) {
                    handleQuickButtonRelease(now, label, forceDoubleTap = treatAsDoubleTap)
                } else {
                    _lastSignalLabel.value = "$label Algılandı"
                }
                touchedWhileHeld = false
                return true
            }
        }
        return false
    }

    private fun updateHeldState() {
        _isStylusButtonHeld.value = motionButtonDown || keyButtonDown
    }

    private fun handleQuickButtonRelease(
        nowMs: Long,
        signalSource: String,
        forceDoubleTap: Boolean
    ) {
        if (forceDoubleTap) {
            cancelPendingSingleTap()
            lastTapReleaseMs = 0L
            _lastSignalLabel.value = "Çift Tıklama Algılandı • $signalSource"
            _clickEvents.tryEmit(
                StylusHardwareClickEvent(
                    type = StylusHardwareClickType.DOUBLE_TAP,
                    signalDescription = signalSource,
                    timestampMs = nowMs
                )
            )
            return
        }

        if (lastTapReleaseMs > 0L && (nowMs - lastTapReleaseMs) in 35L..DOUBLE_TAP_WINDOW_MS) {
            cancelPendingSingleTap()
            lastTapReleaseMs = 0L
            _lastSignalLabel.value = "Çift Tıklama Algılandı • $signalSource"
            _clickEvents.tryEmit(
                StylusHardwareClickEvent(
                    type = StylusHardwareClickType.DOUBLE_TAP,
                    signalDescription = signalSource,
                    timestampMs = nowMs
                )
            )
        } else {
            lastTapReleaseMs = nowMs
            cancelPendingSingleTap()
            val task = Runnable {
                lastTapReleaseMs = 0L
                _lastSignalLabel.value = "Tek Tıklama Algılandı • $signalSource"
                _clickEvents.tryEmit(
                    StylusHardwareClickEvent(
                        type = StylusHardwareClickType.SINGLE_TAP,
                        signalDescription = signalSource,
                        timestampMs = SystemClock.elapsedRealtime()
                    )
                )
            }
            pendingSingleTapRunnable = task
            mainHandler.postDelayed(task, SINGLE_TAP_DEBOUNCE_MS)
        }
    }

    private fun cancelPendingSingleTap() {
        pendingSingleTapRunnable?.let { mainHandler.removeCallbacks(it) }
        pendingSingleTapRunnable = null
    }
}
