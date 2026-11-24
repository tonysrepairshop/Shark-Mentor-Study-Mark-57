package com.example.sharkmentor.ui.screens.pacekeep

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import com.example.sharkmentor.core.widget.WidgetUpdateManager
import com.example.sharkmentor.data.local.SharkMentorDatabase
import com.example.sharkmentor.data.local.entity.FocusSessionEntity
import com.example.sharkmentor.data.local.preferences.UserPreferences
import com.example.sharkmentor.service.sync.SyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private val PacePianoBlackBg = Color(0xFF000000)
private val PaceCardBg = Color(0xFF060606)
private val PaceBorderOffWhite = Color(0x5CF2EFE9)
private val PaceOffWhite = Color(0xFFF2EFE9)
private val PaceSecondaryText = Color(0xB3F2EFE9)

class PaceKeepBridge(context: Context) {

    private val appContext = context.applicationContext
    private val bridgeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @JavascriptInterface
    fun recordFocusSession(durationSeconds: Long, @Suppress("UNUSED_PARAMETER") sourceMode: String) {
        if (durationSeconds <= 0L) return

        bridgeScope.launch {
            runCatching {
                val institutionId = UserPreferences.getInstance(appContext).institutionId
                val db = SharkMentorDatabase.getDatabase(appContext)
                db.sessionDao().insertSession(
                    FocusSessionEntity(
                        modeName = "PACE/KEEP",
                        durationSeconds = durationSeconds,
                        afkScore = 10.0f,
                        phoneLiftCount = 0,
                        screenOnMinutes = 0,
                        institutionId = institutionId,
                        isSynced = false
                    )
                )
                WidgetUpdateManager.updateAllWidgets(appContext)
                if (isDeviceOnline(appContext)) {
                    SyncWorker.enqueueImmediateSync(appContext)
                }
            }
        }
    }

    @JavascriptInterface
    fun vibrate(durationMs: Long) {
        val safeDuration = durationMs.coerceIn(15L, 800L)
        runCatching {
            val effect = VibrationEffect.createOneShot(safeDuration, VibrationEffect.DEFAULT_AMPLITUDE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                appContext.getSystemService(VibratorManager::class.java)?.defaultVibrator?.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                (appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)?.vibrate(effect)
            }
        }
    }

    private fun isDeviceOnline(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PaceKeepScreen(
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val bridge = remember(context) { PaceKeepBridge(context) }

    val webView = remember(context) {
        WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(android.graphics.Color.BLACK)
            overScrollMode = View.OVER_SCROLL_NEVER
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            addJavascriptInterface(bridge, "PaceKeepBridge")
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val url = request?.url?.toString().orEmpty()
                    return !url.startsWith("file:///android_asset/")
                }
            }
            loadUrl("file:///android_asset/pace_keep.html")
        }
    }

    val flushAndExit: () -> Unit = remember(webView, onBackClick) {
        {
            runCatching {
                webView.evaluateJavascript("window.flushAllPaceKeepSessions && window.flushAllPaceKeepSessions();") {
                    onBackClick()
                }
            }.onFailure {
                onBackClick()
            }
        }
    }

    BackHandler(onBack = flushAndExit)

    DisposableEffect(view) {
        val activity = view.context.findActivity()
        val window = activity?.window
        val previousStatusColor = window?.statusBarColor
        val previousNavColor = window?.navigationBarColor
        val insetsController = if (window != null) WindowCompat.getInsetsController(window, view) else null
        val prevLightStatus = insetsController?.isAppearanceLightStatusBars
        val prevLightNav = insetsController?.isAppearanceLightNavigationBars

        if (window != null) {
            @Suppress("DEPRECATION")
            window.statusBarColor = android.graphics.Color.BLACK
            @Suppress("DEPRECATION")
            window.navigationBarColor = android.graphics.Color.BLACK
            insetsController?.isAppearanceLightStatusBars = false
            insetsController?.isAppearanceLightNavigationBars = false
        }

        onDispose {
            if (window != null) {
                if (previousStatusColor != null) {
                    @Suppress("DEPRECATION")
                    window.statusBarColor = previousStatusColor
                }
                if (previousNavColor != null) {
                    @Suppress("DEPRECATION")
                    window.navigationBarColor = previousNavColor
                }
                if (prevLightStatus != null) {
                    insetsController?.isAppearanceLightStatusBars = prevLightStatus
                }
                if (prevLightNav != null) {
                    insetsController?.isAppearanceLightNavigationBars = prevLightNav
                }
            }
        }
    }

    DisposableEffect(webView) {
        onDispose {
            runCatching {
                webView.evaluateJavascript("window.flushAllPaceKeepSessions && window.flushAllPaceKeepSessions();", null)
                webView.stopLoading()
                webView.removeJavascriptInterface("PaceKeepBridge")
                webView.destroy()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PacePianoBlackBg)
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(PacePianoBlackBg)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(PaceCardBg)
                    .border(1.dp, PaceBorderOffWhite, RoundedCornerShape(10.dp))
                    .clickable(onClick = flushAndExit)
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Geri",
                    tint = PaceOffWhite,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Geri",
                    color = PaceOffWhite,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(PaceCardBg)
                    .border(1.dp, PaceBorderOffWhite, RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(
                    text = "PACE / KEEP",
                    color = PaceSecondaryText,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.0.sp
                )
            }
        }

        AndroidView(
            factory = { webView },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
