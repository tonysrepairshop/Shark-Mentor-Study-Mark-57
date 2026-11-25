package com.example.sharkmentor.core.util

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.annotation.DrawableRes
import com.example.sharkmentor.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class SharkAppIcon(
    val key: String,
    val title: String,
    val aliasClass: String,
    @DrawableRes val iconRes: Int,
    @DrawableRes val previewDrawableRes: Int,
    @DrawableRes val notificationSmallIconRes: Int
) {
    ORIGINAL(
        key = "original",
        title = "Klasik Shark",
        aliasClass = ".OriginalAlias",
        iconRes = R.mipmap.ic_launcher_original,
        previewDrawableRes = R.drawable.ic_app_icon_default,
        notificationSmallIconRes = R.drawable.ic_notification_shark
    ),
    MAKO(
        key = "mako",
        title = "Neşeli Shark",
        aliasClass = ".MakoAlias",
        iconRes = R.mipmap.ic_launcher_mako,
        previewDrawableRes = R.drawable.ic_app_icon_happy,
        notificationSmallIconRes = R.drawable.ic_notification_shark
    ),
    HAMMERHEAD(
        key = "hammerhead",
        title = "Efsane Shark",
        aliasClass = ".HammerheadAlias",
        iconRes = R.mipmap.ic_launcher_hammerhead,
        previewDrawableRes = R.drawable.ic_app_icon_ecstatic,
        notificationSmallIconRes = R.drawable.ic_notification_shark
    ),
    KAPLAN(
        key = "kaplan",
        title = "Patron Shark",
        aliasClass = ".KaplanAlias",
        iconRes = R.mipmap.ic_launcher_kaplan,
        previewDrawableRes = R.drawable.ic_app_icon_proud,
        notificationSmallIconRes = R.drawable.ic_notification_shark
    ),
    GREAT_WHITE(
        key = "great_white",
        title = "Sevgi Shark",
        aliasClass = ".GreatWhiteAlias",
        iconRes = R.mipmap.ic_launcher_greatwhite,
        previewDrawableRes = R.drawable.ic_app_icon_love,
        notificationSmallIconRes = R.drawable.ic_notification_shark
    ),
    MEGALODON(
        key = "megalodon",
        title = "Hırslı Shark",
        aliasClass = ".MegalodonAlias",
        iconRes = R.mipmap.ic_launcher_megalodon,
        previewDrawableRes = R.drawable.ic_app_icon_furious,
        notificationSmallIconRes = R.drawable.ic_notification_shark
    );

    companion object {
        fun fromKey(key: String?): SharkAppIcon = when (key?.lowercase()?.trim()) {
            "original", "camgoz", "spiny", "default", "klasik" -> ORIGINAL
            "mako", "happy", "neseli" -> MAKO
            "greatwhite", "great_white", "buyuk_beyaz", "love", "sevgi" -> GREAT_WHITE
            "cekicbas", "hammerhead", "ecstatic", "efsane" -> HAMMERHEAD
            "kaplan", "tiger", "proud", "patron" -> KAPLAN
            "megalodon", "furious", "hirsli" -> MEGALODON
            else -> entries.firstOrNull { it.key == key } ?: ORIGINAL
        }
    }
}

object AppIconManager {
    private const val TAG = "AppIconManager"
    private const val PREFS_NAME = "app_icon_preferences"
    private const val KEY_ACTIVE_ICON = "active_shark_icon"
    private const val KEY_PENDING_ICON = "pending_shark_icon"
    private val LEGACY_ALIAS_CLASSES = listOf(".CamgozAlias")

    @Volatile
    private var isInitialized = false

    private val _activeIconFlow = MutableStateFlow(SharkAppIcon.ORIGINAL)
    val activeIconFlow: StateFlow<SharkAppIcon> = _activeIconFlow.asStateFlow()

    fun init(context: Context) {
        val active = readPersistedIcon(context)
        _activeIconFlow.value = active
        isInitialized = true
    }

    private fun readPersistedIcon(context: Context): SharkAppIcon {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return SharkAppIcon.fromKey(prefs.getString(KEY_ACTIVE_ICON, SharkAppIcon.ORIGINAL.key))
    }

    fun getActiveIcon(context: Context): SharkAppIcon {
        val icon = readPersistedIcon(context)
        if (!isInitialized || _activeIconFlow.value != icon) {
            _activeIconFlow.value = icon
            isInitialized = true
        }
        return icon
    }

    /**
     * Persists the user's chosen icon immediately, updates [activeIconFlow] for live UI sync
     * (Splash, Auth, About, Settings), and queues the PackageManager alias switch to run when
     * the app moves into the background. Applying alias toggles synchronously in the foreground
     * causes Android 12–16 / One UI ActivityManager to terminate the foreground process.
     */
    fun setActiveIcon(context: Context, targetIcon: SharkAppIcon) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_ACTIVE_ICON, targetIcon.key)
            .putString(KEY_PENDING_ICON, targetIcon.key)
            .apply()
        _activeIconFlow.value = targetIcon
        isInitialized = true
    }

    /**
     * Applies any pending launcher alias change safely once no activities are visible.
     * Enables the target alias BEFORE disabling old aliases so the launcher always sees at least
     * one valid entry point, and checks manifest registration before touching any component.
     */
    @Synchronized
    fun applyPendingIconIfNeeded(context: Context) {
        val appCtx = context.applicationContext
        val prefs = appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val pendingKey = prefs.getString(KEY_PENDING_ICON, null) ?: return
        val targetIcon = SharkAppIcon.fromKey(pendingKey)

        val packageManager = appCtx.packageManager
        val packageName = appCtx.packageName

        fun componentExists(component: ComponentName): Boolean {
            return runCatching {
                @Suppress("DEPRECATION")
                packageManager.getActivityInfo(component, PackageManager.MATCH_DISABLED_COMPONENTS)
                true
            }.getOrDefault(false)
        }

        val targetComponent = ComponentName(packageName, "$packageName${targetIcon.aliasClass}")
        if (!componentExists(targetComponent)) {
            Log.w(TAG, "Target alias missing in manifest: ${targetComponent.className}")
            prefs.edit().remove(KEY_PENDING_ICON).apply()
            return
        }

        runCatching {
            // 1. Keep MainActivity enabled permanently for intents, notifications & widgets
            val mainComponent = ComponentName(packageName, "$packageName.MainActivity")
            if (componentExists(mainComponent) &&
                packageManager.getComponentEnabledSetting(mainComponent) == PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            ) {
                packageManager.setComponentEnabledSetting(
                    mainComponent,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
                )
            }

            // 2. Enable the new alias FIRST
            val targetState = packageManager.getComponentEnabledSetting(targetComponent)
            val targetAlreadyActive = targetState == PackageManager.COMPONENT_ENABLED_STATE_ENABLED ||
                (targetState == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT && targetIcon == SharkAppIcon.ORIGINAL)
            if (!targetAlreadyActive) {
                packageManager.setComponentEnabledSetting(
                    targetComponent,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
                )
            }

            // 3. Disable all other active aliases (including legacy aliases)
            val allOtherAliases = SharkAppIcon.entries
                .filter { it != targetIcon }
                .map { it.aliasClass to (it == SharkAppIcon.ORIGINAL) } +
                LEGACY_ALIAS_CLASSES.map { it to false }

            allOtherAliases.forEach { (aliasClass, isDefaultEnabled) ->
                val other = ComponentName(packageName, "$packageName$aliasClass")
                if (!componentExists(other)) return@forEach
                val currentState = packageManager.getComponentEnabledSetting(other)
                val otherIsActive = currentState == PackageManager.COMPONENT_ENABLED_STATE_ENABLED ||
                    (currentState == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT && isDefaultEnabled)
                if (otherIsActive) {
                    packageManager.setComponentEnabledSetting(
                        other,
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP
                    )
                }
            }

            prefs.edit().remove(KEY_PENDING_ICON).apply()
            Log.d(TAG, "Applied launcher icon alias cleanly: ${targetIcon.aliasClass}")
        }.onFailure { err ->
            Log.w(TAG, "Deferred icon switch failed safely: ${err.message}")
            prefs.edit().remove(KEY_PENDING_ICON).apply()
        }
    }
}
