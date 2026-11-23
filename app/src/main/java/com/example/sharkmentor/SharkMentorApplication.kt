package com.example.sharkmentor

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.sharkmentor.core.ai.UserAiPreferences
import com.example.sharkmentor.core.blocker.BlockerPreferences
import com.example.sharkmentor.core.data.ThemePreferences
import com.example.sharkmentor.core.theme.ThemeManager
import com.example.sharkmentor.core.util.AppIconManager
import com.example.sharkmentor.core.util.AvatarHelper
import com.example.sharkmentor.core.util.GoogleServicesHelper
import com.example.sharkmentor.core.util.SessionManager
import com.example.sharkmentor.data.local.preferences.UserPreferences
import com.example.sharkmentor.service.notification.NotificationChannels
import com.example.sharkmentor.service.sync.SyncWorker
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SharkMentorApplication : Application() {

    private val appScope = CoroutineScope(Dispatchers.IO)
    private var startedActivityCount = 0

    override fun onCreate() {
        super.onCreate()

        // ── Ultra-Light Main Thread Init Only (Prevents Cold-Start ANR on 3GB Android 9 Devices) ──
        ThemePreferences.initialize(this)
        ThemeManager.init(this)
        SessionManager.init(this)
        UserPreferences.getInstance(this)

        // Apply any deferred launcher icon switch cleanly when user leaves the app
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) {
                startedActivityCount++
            }
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) {
                startedActivityCount = (startedActivityCount - 1).coerceAtLeast(0)
                if (startedActivityCount == 0 && !activity.isChangingConfigurations) {
                    appScope.launch {
                        runCatching { AppIconManager.applyPendingIconIfNeeded(applicationContext) }
                    }
                }
            }
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })

        // ── Offload All Heavy Disk/IPC/Firebase Startup Tasks to Background Thread ────
        appScope.launch {
            runCatching { UserAiPreferences.initialize(applicationContext) }
            runCatching { AppIconManager.init(applicationContext) }
            runCatching { AvatarHelper.init(applicationContext) }
            runCatching { BlockerPreferences.initialize(applicationContext) }
            runCatching { NotificationChannels.registerAll(applicationContext) }

            // Pre-warm SharedPreferences-backed engines off main thread
            runCatching { com.example.sharkmentor.core.coaching.CoachingEngine.ensureInitialized(applicationContext) }
            runCatching { com.example.sharkmentor.core.coaching.BookCampManager.ensureLoaded(applicationContext) }
            runCatching { com.example.sharkmentor.core.coaching.StudentDossierManager.ensureLoaded(applicationContext) }
            runCatching { com.example.sharkmentor.core.coaching.StudentDossierManager.syncIfDueBackground(applicationContext) }
            runCatching { com.example.sharkmentor.ui.screens.notes.drawing.NoteDrawingPreferences.ensureLoaded(applicationContext) }
            runCatching { com.example.sharkmentor.data.topbar.TopBarPreferences.getInstance(applicationContext) }
            runCatching { com.example.sharkmentor.data.repository.CountdownRepository.getInstance(applicationContext) }

            runCatching {
                com.example.sharkmentor.core.util.PdfLibraryManager.init(applicationContext)
            }.onFailure { e ->
                Log.w("SharkMentorApp", "PdfLibraryManager init bypassed: ${e.message}")
            }

            // 2. Initialize Firebase with persistent local disk cache (NEVER clearPersistence on launch — saves 90%+ Firestore reads!)
            try {
                FirebaseApp.initializeApp(applicationContext)
                if (GoogleServicesHelper.isGooglePlayServicesAvailable(applicationContext)) {
                    GoogleServicesHelper.getFirebaseFirestore(applicationContext)
                    Log.d("SharkMentorApp", "Firebase initialized with persistent local cache preserved.")
                } else {
                    Log.w("SharkMentorApp", "Google Play Services unavailable. Skipping Firestore init.")
                }
            } catch (e: Throwable) {
                Log.w("SharkMentorApp", "Firebase init bypassed: ${e.message}")
            }

            // 3. Recover any orphaned focus session from an unexpected kill/crash before periodic sync runs
            try {
                val isTimerActive = com.example.sharkmentor.core.service.FocusTimerService.timerStateFlow.value.timerPhase !=
                    com.example.sharkmentor.core.timer.TimerPhase.IDLE
                com.example.sharkmentor.core.timer.FocusSessionRecorder.recoverOrphanedSessionIfNeeded(
                    context = applicationContext,
                    isTimerCurrentlyActive = isTimerActive
                )
            } catch (e: Throwable) {
                Log.w("SharkMentorApp", "Orphaned focus session recovery bypassed: ${e.message}")
            }

            // 4. Enqueue periodic cloud sync (quota-safe 6-hour cadence) & Duolingo Water Streak worker (2-hour widget/notification cadence)
            try {
                SyncWorker.enqueuePeriodicSync(applicationContext)
            } catch (e: Throwable) {
                Log.w("SharkMentorApp", "Periodic sync enqueue bypassed: ${e.message}")
            }
            try {
                com.example.sharkmentor.worker.StreakReminderWorker.schedule(applicationContext)
            } catch (e: Throwable) {
                Log.w("SharkMentorApp", "Water streak worker schedule bypassed: ${e.message}")
            }
            try {
                com.example.sharkmentor.worker.StudentDossierSyncWorker.schedule(applicationContext)
            } catch (e: Throwable) {
                Log.w("SharkMentorApp", "Student dossier worker schedule bypassed: ${e.message}")
            }
            try {
                com.example.sharkmentor.worker.WeeklyMasteryCheckInWorker.schedule(applicationContext)
            } catch (e: Throwable) {
                Log.w("SharkMentorApp", "Sunday weekly check-in worker schedule bypassed: ${e.message}")
            }
        }
    }

    companion object {

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE focus_sessions_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        modeName TEXT NOT NULL,
                        durationSeconds INTEGER NOT NULL,
                        completedAt INTEGER NOT NULL,
                        afkScore REAL NOT NULL DEFAULT 10.0,
                        phoneLiftCount INTEGER NOT NULL DEFAULT 0,
                        screenOnMinutes INTEGER NOT NULL DEFAULT 0,
                        isSynced INTEGER NOT NULL DEFAULT 0,
                        lastModifiedMillis INTEGER NOT NULL DEFAULT 0,
                        cloudId TEXT,
                        institutionId TEXT,
                        assignedCoachId TEXT
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT INTO focus_sessions_new (id, modeName, durationSeconds, completedAt, afkScore, phoneLiftCount, screenOnMinutes)
                    SELECT id, modeName, durationMinutes * 60, completedAt, CAST(afkScore AS REAL), phoneLiftCount, 0
                    FROM focus_sessions
                """.trimIndent())
                db.execSQL("DROP TABLE focus_sessions")
                db.execSQL("ALTER TABLE focus_sessions_new RENAME TO focus_sessions")
            }
        }
    }
}
