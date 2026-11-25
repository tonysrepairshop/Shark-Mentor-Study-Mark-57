package com.example.sharkmentor

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.sharkmentor.core.data.ThemePreferences
import com.example.sharkmentor.core.theme.ThemeManager
import com.example.sharkmentor.core.util.GoogleServicesHelper
import com.example.sharkmentor.core.util.PdfBitmapConverter
import com.example.sharkmentor.ui.MainScreen
import com.example.sharkmentor.ui.auth.AuthScreen
import com.example.sharkmentor.ui.theme.SharkMentorTheme
import com.example.sharkmentor.ui.components.SpecialMemberWelcomeGate
import com.google.firebase.firestore.SetOptions
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.util.zip.Inflater
import java.util.zip.ZipInputStream

data class ImportedNotePayload(
    val title: String,
    val content: String,
    val sourceUri: Uri? = null,
    val mimeType: String? = null,
    val pdfPreviewBitmap: Bitmap? = null,
    val pdfPageCount: Int = 0,
    val pdfPageImagePaths: List<String> = emptyList(),
    val localPdfPath: String? = null
)

/**
 * Single-activity entry point for SharkMentor.
 */
class MainActivity : ComponentActivity() {

    private var importedNoteState by mutableStateOf<ImportedNotePayload?>(null)
    private var pendingPdfChoiceState by mutableStateOf<com.example.sharkmentor.core.util.ImportedPdfFile?>(null)
    private var targetRouteState by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        splashScreen.setKeepOnScreenCondition { false }
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        val openReflection = intent.getBooleanExtra("EXTRA_OPEN_REFLECTION", false)

        handleIncomingIntent(intent)

        setContent {
            val themePrefs = remember { ThemePreferences.initialize(this) }
            val themeMode by themePrefs.themeMode.collectAsStateWithLifecycle()
            val currentPalette by themePrefs.palette.collectAsStateWithLifecycle()
            val isZenModePref by themePrefs.isZenMode.collectAsStateWithLifecycle()
            val isZenMinimalist by ThemeManager.isZenMinimalist.collectAsStateWithLifecycle()
            val isZenMode = isZenModePref || isZenMinimalist

            val userPrefs = remember { com.example.sharkmentor.data.local.preferences.UserPreferences.getInstance(this) }
            var isLoggedIn by remember { mutableStateOf(userPrefs.isOnboarded) }
            androidx.compose.runtime.LaunchedEffect(Unit) {
                if (!isLoggedIn) {
                    val hasFirebaseUser = withContext(Dispatchers.IO) {
                        runCatching {
                            val auth = GoogleServicesHelper.getFirebaseAuth(this@MainActivity)
                            GoogleServicesHelper.getCurrentUser(auth, this@MainActivity) != null
                        }.getOrDefault(false)
                    }
                    if (hasFirebaseUser) isLoggedIn = true
                }
            }

            SharkMentorTheme(
                themeMode = themeMode,
                palette = currentPalette,
                isZenMode = isZenMode
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (isLoggedIn) {
                        MainScreen(
                            openReflectionDialog = openReflection,
                            importedNote = importedNoteState,
                            onImportedNoteConsumed = { importedNoteState = null },
                            targetRoute = targetRouteState,
                            onTargetRouteConsumed = { targetRouteState = null }
                        )
                        SpecialMemberWelcomeGate()
                    } else {
                        AuthScreen(
                            onAuthSuccess = {
                                lifecycleScope.launch(Dispatchers.IO) {
                                    runCatching { registerFcmToken() }
                                    runCatching { com.example.sharkmentor.core.notification.SocialNotificationMonitor.start(this@MainActivity) }
                                    runCatching {
                                        com.example.sharkmentor.data.repository.StreakRepository(this@MainActivity)
                                            .pullAndMergeFromCloud()
                                    }
                                    runCatching {
                                        com.example.sharkmentor.data.repository.CountdownRepository.getInstance(this@MainActivity)
                                            .pullAndMergeFromCloud()
                                    }
                                    runCatching {
                                        com.example.sharkmentor.data.sync.TopicCloudSyncManager
                                            .pullAndMergeFromCloud(this@MainActivity, forceServer = true)
                                    }
                                    runCatching {
                                        com.example.sharkmentor.core.coaching.BookCoachingRoleManager.pullFromCloudIfNewer(this@MainActivity)
                                        com.example.sharkmentor.core.coaching.AiCoachLedgerManager.pullFromCloudIfNewer(this@MainActivity)
                                    }
                                    runCatching {
                                        com.example.sharkmentor.core.widget.WidgetUpdateManager.updateAllWidgets(this@MainActivity)
                                    }
                                    runCatching {
                                        com.example.sharkmentor.service.sync.SyncWorker.enqueueForceFullSync(this@MainActivity)
                                    }
                                }
                                isLoggedIn = true
                            },
                            onGuestContinue = {
                                userPrefs.completeOnboardingAsIndividual("Misafir Öğrenci")
                                com.example.sharkmentor.ui.components.FirstTimeTutorialPrefs.markEligibleForNewUserOrGuest(this@MainActivity)
                                isLoggedIn = true
                            }
                        )
                    }

                    val pdfChoice = pendingPdfChoiceState
                    if (pdfChoice != null) {
                        com.example.sharkmentor.ui.screens.pdf.PdfOpenChoiceDialog(
                            title = pdfChoice.title,
                            pageCount = pdfChoice.pageCount,
                            onOpenAsPdfReader = {
                                pendingPdfChoiceState = null
                                startActivity(
                                    com.example.sharkmentor.ui.screens.pdf.PdfReaderActivity.createIntent(
                                        context = this@MainActivity,
                                        pdfFilePath = pdfChoice.localFile.absolutePath,
                                        title = pdfChoice.title,
                                        skipChooser = true
                                    )
                                )
                            },
                            onOpenAsEditableNote = {
                                val chosen = pdfChoice
                                pendingPdfChoiceState = null
                                lifecycleScope.launch(Dispatchers.IO) {
                                    val cleanText = runCatching {
                                        com.example.sharkmentor.core.util.DocumentTextExtractor.extractCleanText(
                                            context = this@MainActivity,
                                            uri = chosen.localUri,
                                            displayName = chosen.displayName,
                                            mimeType = "application/pdf"
                                        )
                                    }.getOrDefault("").take(30_000)
                                    withContext(Dispatchers.Main) {
                                        importedNoteState = ImportedNotePayload(
                                            title = chosen.title,
                                            content = cleanText,
                                            sourceUri = chosen.localUri,
                                            mimeType = "application/pdf",
                                            pdfPageCount = chosen.pageCount,
                                            localPdfPath = chosen.localFile.absolutePath
                                        )
                                    }
                                }
                            },
                            onDismiss = {
                                pendingPdfChoiceState = null
                            }
                        )
                    }
                }
            }
        }

        lifecycleScope.launch(Dispatchers.IO) {
            runCatching {
                if (GoogleServicesHelper.getCurrentUser(null, this@MainActivity) != null) {
                    registerFcmToken()
                    com.example.sharkmentor.core.notification.SocialNotificationMonitor.start(this@MainActivity)
                }
            }.onFailure { e ->
                Log.w("MainActivity", "Error checking current user: ${e.message}")
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val studioOpen = com.example.sharkmentor.core.service.CampVideoPlaybackController.isStudioOpen.value
            val pipEnabled = ThemePreferences.initialize(applicationContext).videoPipEnabled.value
            if (studioOpen && pipEnabled) {
                runCatching {
                    val params = android.app.PictureInPictureParams.Builder()
                        .setAspectRatio(android.util.Rational(16, 9))
                        .build()
                    enterPictureInPictureMode(params)
                }
            }
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: android.content.res.Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        com.example.sharkmentor.core.service.CampVideoPlaybackController.setPipMode(isInPictureInPictureMode)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return

        val restoreCampVideo = intent.getBooleanExtra("EXTRA_RESTORE_CAMP_VIDEO", false)
        if (restoreCampVideo) {
            intent.removeExtra("EXTRA_RESTORE_CAMP_VIDEO")
            com.example.sharkmentor.core.service.CampVideoPlaybackController.requestRestoreStudio()
        }
        val requestedRoute = intent.getStringExtra("EXTRA_TARGET_ROUTE")?.trim()?.takeIf { it.isNotEmpty() }
        val shouldStartFocus = intent.getBooleanExtra("EXTRA_START_FOCUS", false)
        val openWeeklyCheckIn = intent.getBooleanExtra(
            com.example.sharkmentor.worker.WeeklyMasteryCheckInWorker.EXTRA_OPEN_WEEKLY_CHECKIN,
            false
        )
        if (openWeeklyCheckIn) {
            intent.removeExtra(com.example.sharkmentor.worker.WeeklyMasteryCheckInWorker.EXTRA_OPEN_WEEKLY_CHECKIN)
            com.example.sharkmentor.core.coaching.PlanReadinessNavBus.requestOpenSundayCheckIn()
        }
        if (requestedRoute != null) {
            targetRouteState = requestedRoute
            intent.removeExtra("EXTRA_TARGET_ROUTE")
        }
        if (shouldStartFocus) {
            if (targetRouteState == null) {
                targetRouteState = "home"
            }
            intent.removeExtra("EXTRA_START_FOCUS")
            // Route cleanly into HomeScreen so the student picks timer mode + focus tag/label
            // (or jumps straight to their active running timer with zero mode conflicts).
            com.example.sharkmentor.core.timer.FocusLaunchNavBus.requestOpenFocusLaunchPicker()
        }

        when (intent.action) {
            Intent.ACTION_VIEW -> {
                val uri = intent.data ?: return
                processIncomingUri(uri, intent.type)
            }
            Intent.ACTION_SEND -> {
                val streamUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM)
                }

                if (streamUri != null) {
                    processIncomingUri(streamUri, intent.type)
                } else {
                    val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
                    if (!sharedText.isNullOrBlank()) {
                        val sharedTitle = intent.getStringExtra(Intent.EXTRA_SUBJECT)
                            ?: intent.getStringExtra(Intent.EXTRA_TITLE)
                            ?: "Paylaşılan Not"
                        val safeText = if (sharedText.length > 15_000) {
                            sharedText.take(15_000) + "\n\n[Not: İçerik 15.000 karakterle sınırlandırıldı.]"
                        } else sharedText
                        importedNoteState = ImportedNotePayload(
                            title = sharedTitle,
                            content = safeText,
                            sourceUri = null,
                            mimeType = "text/plain"
                        )
                    }
                }
            }
        }
    }

    private fun processIncomingUri(uri: Uri, fallbackMimeType: String?) {
        lifecycleScope.launch(Dispatchers.IO) {
            if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
                runCatching {
                    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }
            val resolver = contentResolver
            val mimeType = resolver.getType(uri) ?: fallbackMimeType
            val displayName = resolveDisplayName(resolver, uri)
            val isPdf = mimeType == "application/pdf" || displayName.endsWith(".pdf", ignoreCase = true)

            if (isPdf) {
                val copiedPdf = PdfBitmapConverter.copyUriToPrivatePdfsDir(this@MainActivity, uri)
                if (copiedPdf != null) {
                    runCatching {
                        com.example.sharkmentor.core.util.PdfLibraryManager.registerOrUpdatePdf(
                            context = this@MainActivity,
                            imported = copiedPdf
                        )
                    }
                }
                withContext(Dispatchers.Main) {
                    if (copiedPdf != null) {
                        pendingPdfChoiceState = copiedPdf
                    }
                }
            } else {
                val payload = readUriToNotePayload(uri, mimeType)
                withContext(Dispatchers.Main) {
                    importedNoteState = payload
                }
            }
        }
    }

    private fun readUriToNotePayload(uri: Uri, fallbackMimeType: String?): ImportedNotePayload {
        val resolver = contentResolver
        val mimeType = resolver.getType(uri) ?: fallbackMimeType
        val displayName = resolveDisplayName(resolver, uri)
        val noteTitle = displayName.substringBeforeLast('.', displayName).ifBlank { "İçe Aktarılan Not" }
        val isPdf = mimeType == "application/pdf" || displayName.endsWith(".pdf", ignoreCase = true)

        var pdfPageCount = 0
        var localPdfPath: String? = null
        var effectiveUri = uri

        if (isPdf) {
            runCatching {
                val copied = PdfBitmapConverter.copyUriToPrivatePdfsDir(this, uri)
                if (copied != null) {
                    pdfPageCount = copied.pageCount
                    localPdfPath = copied.localFile.absolutePath
                    effectiveUri = copied.localUri
                }
            }.onFailure { e ->
                Log.w("MainActivity", "Failed to copy PDF to private storage for $uri: ${e.message}")
            }
        }

        val extractedContent = runCatching {
            com.example.sharkmentor.core.util.DocumentTextExtractor.extractCleanText(
                context = this,
                uri = effectiveUri,
                displayName = displayName,
                mimeType = mimeType
            )
        }.getOrElse { e ->
            Log.w("MainActivity", "Failed to read incoming URI $uri: ${e.message}")
            ""
        }

        val safeContent = if (extractedContent.length > 30_000) {
            extractedContent.take(30_000)
        } else {
            extractedContent
        }

        return ImportedNotePayload(
            title = noteTitle,
            content = safeContent,
            sourceUri = effectiveUri,
            mimeType = if (isPdf) "application/pdf" else mimeType,
            pdfPreviewBitmap = null,
            pdfPageCount = pdfPageCount,
            pdfPageImagePaths = emptyList(),
            localPdfPath = localPdfPath
        )
    }

    private fun extractPdfPlainText(pdfBytes: ByteArray): String {
        return PdfBitmapConverter.extractPdfPlainTextFromBytes(pdfBytes)
    }

    private fun extractDocxText(inputStream: java.io.InputStream, displayName: String): String {
        return runCatching {
            ZipInputStream(inputStream).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (entry.name == "word/document.xml") {
                        val xml = BufferedReader(InputStreamReader(zip, Charsets.UTF_8)).readText()
                        return@use xml
                            .replace("</w:p>", "\n")
                            .replace("<w:tab/>", "\t")
                            .replace(Regex("<[^>]+>"), "")
                            .trim()
                    }
                    entry = zip.nextEntry
                }
                "[DOCX Dosyası: $displayName]"
            }
        }.getOrDefault("[DOCX Dosyası: $displayName]")
    }

    private fun resolveDisplayName(resolver: ContentResolver, uri: Uri): String {
        if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
            runCatching {
                resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1 && cursor.moveToFirst()) {
                        val name = cursor.getString(nameIndex)
                        if (!name.isNullOrBlank()) return name
                    }
                }
            }
        }
        return uri.lastPathSegment ?: "İçe Aktarılan Not"
    }

    private fun registerFcmToken() {
        if (!GoogleServicesHelper.isGooglePlayServicesAvailable(this)) {
            Log.w("FCM", "Google Play Services unavailable. Skipping FCM token registration.")
            return
        }
        try {
            val messaging = GoogleServicesHelper.getFirebaseMessaging(this) ?: return
            messaging.token
                .addOnSuccessListener { token ->
                    val currentUserId = GoogleServicesHelper.getCurrentUser(null, this)?.uid ?: return@addOnSuccessListener
                    GoogleServicesHelper.getFirebaseFirestore(this)?.collection("students")?.document(currentUserId)
                        ?.set(hashMapOf("fcmToken" to token), SetOptions.merge())
                        ?.addOnSuccessListener {
                            Log.d("FCM", "Token Firestore'a mühürlendi: $token")
                        }
                        ?.addOnFailureListener { e ->
                            Log.w("FCM", "Failed to save FCM token to Firestore: ${e.message}")
                        }
                }
                .addOnFailureListener { e ->
                    Log.w("FCM", "Failed to retrieve FCM token: ${e.message}")
                }
        } catch (e: Throwable) {
            Log.w("FCM", "Exception during FCM token retrieval: ${e.message}")
        }
    }
}
