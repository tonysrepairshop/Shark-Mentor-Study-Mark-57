package com.example.sharkmentor.core.util

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.storage.FirebaseStorage

/**
 * Utility class to safely check Google Play Services availability
 * and prevent GMS broker exceptions on devices or emulators with missing/outdated GMS.
 */
object GoogleServicesHelper {
    private const val TAG = "GoogleServicesHelper"
    private const val FIRESTORE_CACHE_SIZE_BYTES = 50L * 1024L * 1024L // 50 MB bounded cache for low-end devices

    @Volatile
    private var isGmsBrokerAvailable = true

    @Volatile
    private var isGmsVerifiedAvailable = false

    @Volatile
    private var isFirestoreCacheConfigured = false

    /**
     * Checks if GMS package exists on the system.
     */
    private fun isGmsPackageInstalled(context: Context): Boolean {
        return try {
            val pm = context.packageManager
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo("com.google.android.gms", PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo("com.google.android.gms", 0)
            }
            info.applicationInfo?.enabled == true
        } catch (e: Throwable) {
            false
        }
    }

    /**
     * Attempts to resolve a valid Context from the provided parameter or FirebaseApp.
     */
    private fun resolveContext(context: Context?): Context? {
        if (context != null) {
            return context.applicationContext ?: context
        }
        return try {
            FirebaseApp.getInstance().applicationContext
        } catch (e: Throwable) {
            null
        }
    }

    /**
     * Checks if Google Play Services is installed, enabled, and up-to-date.
     * Result is cached after first verified success to eliminate repeated PackageManager IPC calls.
     */
    fun isGooglePlayServicesAvailable(context: Context? = null): Boolean {
        if (!isGmsBrokerAvailable) {
            return false
        }
        if (isGmsVerifiedAvailable) {
            return true
        }
        val targetContext = resolveContext(context) ?: return false

        if (!isGmsPackageInstalled(targetContext)) {
            Log.w(TAG, "com.google.android.gms package is not installed or disabled on this device.")
            isGmsBrokerAvailable = false
            return false
        }

        return try {
            val availability = GoogleApiAvailability.getInstance()
            val resultCode = availability.isGooglePlayServicesAvailable(targetContext)
            if (resultCode == ConnectionResult.SUCCESS) {
                isGmsVerifiedAvailable = true
                true
            } else {
                Log.w(TAG, "Google Play Services is not available. Result code: $resultCode")
                false
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "SecurityException checking Google Play Services: ${e.message}")
            isGmsBrokerAvailable = false
            false
        } catch (e: Throwable) {
            Log.w(TAG, "Error checking Google Play Services availability: ${e.message}")
            false
        }
    }

    /**
     * Safely gets the FirebaseAuth instance without throwing GMS SecurityException.
     */
    fun getFirebaseAuth(context: Context? = null): FirebaseAuth? {
        if (!isGmsBrokerAvailable) return null
        val targetContext = resolveContext(context)
        if (targetContext != null && !isGooglePlayServicesAvailable(targetContext)) {
            return null
        }
        return try {
            FirebaseAuth.getInstance()
        } catch (e: SecurityException) {
            Log.w(TAG, "GMS SecurityException getting FirebaseAuth: ${e.message}")
            isGmsBrokerAvailable = false
            null
        } catch (e: Throwable) {
            Log.w(TAG, "FirebaseAuth instance unavailable: ${e.message}")
            null
        }
    }

    /**
     * Safely gets the FirebaseFirestore instance without throwing GMS SecurityException.
     */
    fun getFirebaseFirestore(context: Context? = null): FirebaseFirestore? {
        if (!isGmsBrokerAvailable) return null
        val targetContext = resolveContext(context)
        if (targetContext != null && !isGooglePlayServicesAvailable(targetContext)) {
            return null
        }
        return try {
            val instance = FirebaseFirestore.getInstance()
            if (!isFirestoreCacheConfigured) {
                isFirestoreCacheConfigured = true
                runCatching {
                    instance.firestoreSettings = FirebaseFirestoreSettings.Builder()
                        .setLocalCacheSettings(
                            PersistentCacheSettings.newBuilder()
                                .setSizeBytes(FIRESTORE_CACHE_SIZE_BYTES)
                                .build()
                        )
                        .build()
                }
            }
            instance
        } catch (e: SecurityException) {
            Log.w(TAG, "GMS SecurityException getting FirebaseFirestore: ${e.message}")
            isGmsBrokerAvailable = false
            null
        } catch (e: Throwable) {
            Log.w(TAG, "FirebaseFirestore instance unavailable: ${e.message}")
            null
        }
    }

    /**
     * Safely gets the FirebaseStorage instance without throwing GMS SecurityException.
     */
    fun getFirebaseStorage(context: Context? = null, bucket: String? = null): FirebaseStorage? {
        if (!isGmsBrokerAvailable) return null
        val targetContext = resolveContext(context)
        if (targetContext != null && !isGooglePlayServicesAvailable(targetContext)) {
            return null
        }
        return try {
            if (bucket.isNullOrBlank()) {
                FirebaseStorage.getInstance()
            } else {
                FirebaseStorage.getInstance(bucket)
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "GMS SecurityException getting FirebaseStorage: ${e.message}")
            isGmsBrokerAvailable = false
            null
        } catch (e: Throwable) {
            Log.w(TAG, "FirebaseStorage instance unavailable: ${e.message}")
            null
        }
    }

    /**
     * Safely gets the FirebaseMessaging instance without throwing GMS SecurityException.
     */
    fun getFirebaseMessaging(context: Context? = null): FirebaseMessaging? {
        if (!isGmsBrokerAvailable) return null
        val targetContext = resolveContext(context)
        if (targetContext != null && !isGooglePlayServicesAvailable(targetContext)) {
            return null
        }
        return try {
            FirebaseMessaging.getInstance()
        } catch (e: SecurityException) {
            Log.w(TAG, "GMS SecurityException getting FirebaseMessaging: ${e.message}")
            isGmsBrokerAvailable = false
            null
        } catch (e: Throwable) {
            Log.w(TAG, "FirebaseMessaging instance unavailable: ${e.message}")
            null
        }
    }

    /**
     * Safely gets the current authenticated user without throwing GMS broker SecurityException.
     */
    fun getCurrentUser(auth: FirebaseAuth? = null, context: Context? = null): FirebaseUser? {
        if (!isGmsBrokerAvailable) return null
        return try {
            val authInstance = auth ?: getFirebaseAuth(context) ?: return null
            authInstance.currentUser
        } catch (e: SecurityException) {
            Log.w(TAG, "GMS SecurityException accessing currentUser: ${e.message}")
            isGmsBrokerAvailable = false
            null
        } catch (e: Throwable) {
            Log.w(TAG, "Error accessing currentUser: ${e.message}")
            null
        }
    }

    /**
     * Safely signs out the current user without throwing GMS SecurityException.
     */
    fun signOut(context: Context? = null) {
        try {
            getFirebaseAuth(context)?.signOut()
        } catch (e: SecurityException) {
            Log.w(TAG, "GMS SecurityException signing out: ${e.message}")
            isGmsBrokerAvailable = false
        } catch (e: Throwable) {
            Log.w(TAG, "Error signing out: ${e.message}")
        }
    }

    /**
     * Resets the broker status flag if GMS needs to be re-checked.
     */
    fun resetGmsStatus() {
        isGmsBrokerAvailable = true
        isGmsVerifiedAvailable = false
    }
}
