package com.example.sharkmentor.data.focus

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.sharkmentor.core.util.GoogleServicesHelper
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FocusSessionManager(private val context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("sharkmentor_focus_session", Context.MODE_PRIVATE)

    init {
        _isRunningFlow.value = computeActiveStateWithoutWrite()
    }

    companion object {
        private const val KEY_IS_FOCUS_ACTIVE = "is_focus_active"
        private const val KEY_SESSION_END_TIMESTAMP = "session_end_timestamp"
        private const val KEY_SELECTED_SUBJECT = "session_subject"

        private val _isRunningFlow = MutableStateFlow(false)
        val isRunningFlow: StateFlow<Boolean> = _isRunningFlow.asStateFlow()
    }

    val isRunning: Boolean
        get() = isSessionActivelyRunning()

    fun getActiveSubject(): String =
        prefs.getString(KEY_SELECTED_SUBJECT, "Genel") ?: "Genel"

    fun getRemainingMillis(): Long {
        val endTimestamp = prefs.getLong(KEY_SESSION_END_TIMESTAMP, 0L)
        return (endTimestamp - System.currentTimeMillis()).coerceAtLeast(0L)
    }

    fun refreshActiveState(): Boolean = isSessionActivelyRunning()

    private fun computeActiveStateWithoutWrite(): Boolean {
        val isActive = prefs.getBoolean(KEY_IS_FOCUS_ACTIVE, false)
        val endTimestamp = prefs.getLong(KEY_SESSION_END_TIMESTAMP, 0L)
        return isActive && System.currentTimeMillis() < endTimestamp
    }

    fun startSession(durationMinutes: Int, subject: String = "Genel") {
        val endTimestamp = System.currentTimeMillis() + (durationMinutes * 60 * 1000L)
        prefs.edit()
            .putBoolean(KEY_IS_FOCUS_ACTIVE, true)
            .putLong(KEY_SESSION_END_TIMESTAMP, endTimestamp)
            .putString(KEY_SELECTED_SUBJECT, subject)
            .apply()
        _isRunningFlow.value = true

        // Live Presence Sync
        try {
            val uid = GoogleServicesHelper.getCurrentUser(context = context)?.uid
            if (uid != null) {
                val db = GoogleServicesHelper.getFirebaseFirestore(context)
                if (db != null) {
                    val cleanLeagueSubject = com.example.sharkmentor.core.coaching.CoachingEngine
                        .formatCleanLeagueSubject(subject).ifBlank { "Odak" }
                    val updates = hashMapOf<String, Any>(
                        "isStudying" to true,
                        "activeSubject" to cleanLeagueSubject,
                        "sessionStartTime" to FieldValue.serverTimestamp()
                    )
                    db.collection("students").document(uid)
                        .update(updates)
                        .addOnFailureListener {
                            try {
                                db.collection("students").document(uid)
                                    .set(updates, SetOptions.merge())
                            } catch (e: Throwable) {
                                Log.w("FocusSessionManager", "Presence sync fallback failed: ${e.message}")
                            }
                        }
                }
            }
        } catch (e: Throwable) {
            Log.w("FocusSessionManager", "Failed to start presence sync: ${e.message}")
        }
    }

    fun endSession() {
        prefs.edit()
            .putBoolean(KEY_IS_FOCUS_ACTIVE, false)
            .putLong(KEY_SESSION_END_TIMESTAMP, 0L)
            .apply()
        _isRunningFlow.value = false

        // Live Presence Sync Stop
        try {
            val uid = GoogleServicesHelper.getCurrentUser(context = context)?.uid
            if (uid != null) {
                val db = GoogleServicesHelper.getFirebaseFirestore(context)
                if (db != null) {
                    val updates = hashMapOf<String, Any>(
                        "isStudying" to false,
                        "activeSubject" to FieldValue.delete(),
                        "sessionStartTime" to FieldValue.delete()
                    )
                    db.collection("students").document(uid)
                        .update(updates)
                        .addOnFailureListener { e ->
                            Log.e("PresenceSync", "Failed to clear studying presence", e)
                        }
                }
            }
        } catch (e: Throwable) {
            Log.w("FocusSessionManager", "Failed to stop presence sync: ${e.message}")
        }
    }

    fun isSessionActivelyRunning(): Boolean {
        val isActive = prefs.getBoolean(KEY_IS_FOCUS_ACTIVE, false)
        val endTimestamp = prefs.getLong(KEY_SESSION_END_TIMESTAMP, 0L)

        // Auto-expire if past the end timestamp
        return if (isActive && System.currentTimeMillis() < endTimestamp) {
            _isRunningFlow.value = true
            true
        } else {
            if (isActive) {
                endSession() // Clean up expired state
            } else {
                _isRunningFlow.value = false
            }
            false
        }
    }
}
