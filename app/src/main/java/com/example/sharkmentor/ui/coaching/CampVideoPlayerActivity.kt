package com.example.sharkmentor.ui.coaching

import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.sharkmentor.core.coaching.CampDayEpisode
import com.example.sharkmentor.core.data.ThemePreferences
import com.example.sharkmentor.core.service.CampVideoPlaybackController
import com.example.sharkmentor.ui.theme.SharkMentorTheme

/**
 * In-memory bridge passing the active video studio session & callbacks to the standalone
 * [CampVideoPlayerActivity] so PiP runs in its own clean Activity window with ZERO
 * ModalNavigationDrawer, NavHost, or Dialog interference.
 */
object CampVideoPlayerBridge {
    data class LaunchRequest(
        val campTitle: String,
        val subject: String,
        val initialEpisode: CampDayEpisode,
        val allEpisodes: List<CampDayEpisode>,
        val resourceId: Long? = null,
        val onEpisodeChanged: ((CampDayEpisode) -> Unit)? = null,
        val onDeleteEpisode: ((CampDayEpisode) -> Unit)? = null,
        val onMarkCompleted: ((CampDayEpisode) -> Unit)? = null
    )

    @Volatile
    var activeRequest: LaunchRequest? = null

    fun launch(context: Context, request: LaunchRequest) {
        activeRequest = request
        val intent = Intent(context, CampVideoPlayerActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        context.startActivity(intent)
    }
}

/**
 * Dedicated standalone Activity for the Camp Video Study Studio & native Android 16:9 PiP.
 * Because this Activity hosts ONLY the video player (no ModalNavigationDrawer, no NavHost,
 * no Dialog wrapper), entering/exiting PiP works cleanly every time and leaves MainActivity
 * completely free to use in parallel.
 */
class CampVideoPlayerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val req = CampVideoPlayerBridge.activeRequest
            ?: CampVideoPlaybackController.peekRestorablePayload()?.let { p ->
                CampVideoPlayerBridge.LaunchRequest(
                    campTitle = p.campTitle,
                    subject = p.subject,
                    initialEpisode = p.activeEpisode,
                    allEpisodes = p.allEpisodes,
                    resourceId = p.resourceId
                )
            }

        if (req == null) {
            finish()
            return
        }

        setContent {
            val themePrefs = remember { ThemePreferences.initialize(this) }
            val themeMode by themePrefs.themeMode.collectAsStateWithLifecycle()
            val palette by themePrefs.palette.collectAsStateWithLifecycle()
            val isZenMode by themePrefs.isZenMode.collectAsStateWithLifecycle()

            SharkMentorTheme(
                themeMode = themeMode,
                palette = palette,
                isZenMode = isZenMode
            ) {
                CampVideoStudyStudioContent(
                    campTitle = req.campTitle,
                    subject = req.subject,
                    initialEpisode = req.initialEpisode,
                    allEpisodes = req.allEpisodes,
                    resourceId = req.resourceId,
                    onEpisodeChanged = req.onEpisodeChanged,
                    onDeleteEpisode = req.onDeleteEpisode,
                    onMarkCompleted = req.onMarkCompleted,
                    onDismiss = {
                        CampVideoPlaybackController.stopAndRelease(this)
                        finish()
                    }
                )
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val pipEnabled = ThemePreferences.initialize(applicationContext).videoPipEnabled.value
            val isActive = CampVideoPlaybackController.state.value.isActive
            if (pipEnabled && isActive) {
                runCatching {
                    val params = PictureInPictureParams.Builder()
                        .setAspectRatio(Rational(16, 9))
                        .build()
                    enterPictureInPictureMode(params)
                }
            }
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        CampVideoPlaybackController.setPipMode(isInPictureInPictureMode)
    }

    override fun onDestroy() {
        CampVideoPlaybackController.setPipMode(false)
        CampVideoPlaybackController.setStudioOpen(false)
        if (isFinishing) {
            CampVideoPlaybackController.stopAndRelease(applicationContext)
            CampVideoPlayerBridge.activeRequest = null
        }
        super.onDestroy()
    }
}
