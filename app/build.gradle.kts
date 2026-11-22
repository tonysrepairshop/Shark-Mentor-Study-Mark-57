import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    id("com.google.gms.google-services")
}

// ── Read local.properties for secrets (never committed to git) ────────────────
val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(localPropertiesFile.inputStream())
}
fun cleanPropValue(raw: String?): String =
    (raw ?: "")
        .replace("\r", "")
        .replace("\n", "")
        .replace("\"", "")
        .replace("'", "")
        .replace("\\", "")
        .trim()

val geminiApiKey: String = cleanPropValue(
    localProperties.getProperty("gemini.api.key")
        ?: localProperties.getProperty("GEMINI_API_KEY")
)
val geminiApiKeys: String = cleanPropValue(localProperties.getProperty("GEMINI_API_KEYS"))
val deepseekApiKey: String = cleanPropValue(localProperties.getProperty("DEEPSEEK_API_KEY"))
val kimiApiKey: String = cleanPropValue(localProperties.getProperty("KIMI_API_KEY"))

android {
    namespace = "com.example.sharkmentor"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.example.sharkmentor"
        minSdk = 28
        targetSdk = 35
        val ciRunNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 0
        versionCode = 100 + ciRunNumber
        versionName = if (ciRunNumber > 0) "1.0.$ciRunNumber" else "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters += listOf("arm64-v8a")
        }

        // Inject optional local.properties / CI override keys into BuildConfig.
        // Built-in default keys live exclusively in ApiKeyRotator's XOR-obfuscated slots.
        val keysList = LinkedHashSet<String>()
        for (k in geminiApiKeys.split(",", ";", " ", "\t", "\r", "\n")) {
            val cleaned = cleanPropValue(k)
            if (cleaned.isNotEmpty() && !cleaned.equals("null", ignoreCase = true)) {
                keysList.add(cleaned)
            }
        }
        if (geminiApiKey.isNotEmpty() && !geminiApiKey.equals("null", ignoreCase = true)) {
            keysList.add(geminiApiKey)
        }
        val orderedKeys = keysList.toList()
        val key1 = orderedKeys.getOrElse(0) { "" }
        val key2 = orderedKeys.getOrElse(1) { "" }
        val key3 = orderedKeys.getOrElse(2) { "" }
        val joinedGeminiKeys = orderedKeys.joinToString(",")
        val primaryGeminiKey = geminiApiKey.ifEmpty { key1 }

        buildConfigField("String", "GEMINI_MODEL", "\"gemini-3.6-flash\"")
        buildConfigField("String", "GEMINI_API_KEY", "\"$primaryGeminiKey\"")
        buildConfigField("String", "GEMINI_API_KEYS", "\"$joinedGeminiKeys\"")
        buildConfigField("String", "GEMINI_API_KEY_1", "\"$key1\"")
        buildConfigField("String", "GEMINI_API_KEY_2", "\"$key2\"")
        buildConfigField("String", "GEMINI_API_KEY_3", "\"$key3\"")
        buildConfigField("String", "DEEPSEEK_API_KEY", "\"$deepseekApiKey\"")
        buildConfigField("String", "KIMI_API_KEY", "\"$kimiApiKey\"")
    }

    signingConfigs {
        getByName("debug") {
            val ks = file("debug.keystore")
            if (ks.exists()) {
                storeFile = ks
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
        buildConfig = true          // required for BuildConfig.GEMINI_API_KEY
    }

    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
        resources {
            excludes += listOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/license.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/notice.txt",
                "META-INF/ASL2.0",
                "META-INF/*.kotlin_module"
            )
        }
    }
}

dependencies {
    // ── Compose BOM (pins all androidx.compose.* versions) ───────────────
    implementation(platform(libs.androidx.compose.bom))

    // ── Core Compose UI ───────────────────────────────────────────────────
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    // ── Material Icons Extended ───────────────────────────────────────────
    implementation(libs.androidx.compose.material.icons.extended)

    // ── Foundation (HorizontalPager / swipeable timer screens) ───────────
    implementation(libs.androidx.compose.foundation)

    // ── Activity ──────────────────────────────────────────────────────────
    implementation(libs.androidx.activity.compose)

    // ── Core & AppCompat ──────────────────────────────────────────────────
    implementation(libs.androidx.core)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.appcompat)

    // ── Lifecycle ─────────────────────────────────────────────────────────
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // ── Navigation Compose ────────────────────────────────────────────────
    implementation(libs.androidx.navigation.compose)

    // ── Kotlin Coroutines ─────────────────────────────────────────────────
    implementation(libs.kotlinx.coroutines.android)

    // ── Room Database ─────────────────────────────────────────────────────
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // ── Coil Compose ──────────────────────────────────────────────────────
    implementation(libs.coil.compose)

    // ── Unit Tests ────────────────────────────────────────────────────────
    testImplementation(libs.junit)

    // ── Instrumented Tests ────────────────────────────────────────────────
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)

    // ── Debug ─────────────────────────────────────────────────────────────
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Jetpack Glance (AppWidgets with Jetpack Compose)
    implementation("androidx.glance:glance-appwidget:1.1.0")
    implementation("androidx.glance:glance-material3:1.1.0")
    
    // Image Cropper (CanHub fork used in the code)
    implementation("com.github.CanHub:Android-Image-Cropper:4.5.0")

    // ── Google Play Services Core Base & Auth ─────────────────────────────
    implementation(libs.play.services.base)
    implementation("com.google.android.gms:play-services-auth:21.2.0")

    // ── Google Drive API (Backup/Restore) ─────────────────────────────────
    implementation("com.google.api-client:google-api-client-android:2.6.0")
    implementation("com.google.apis:google-api-services-drive:v3-rev20220815-2.0.0")
    implementation("com.google.http-client:google-http-client-gson:1.44.2")

    // ── Firebase (Offline-First Cloud Sync Engine) ────────────────────────
    implementation(platform("com.google.firebase:firebase-bom:33.10.0"))
    implementation("com.google.firebase:firebase-firestore-ktx")
    implementation("com.google.firebase:firebase-storage-ktx")
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-messaging-ktx")

    // ── WorkManager (Background Sync Scheduling) ──────────────────────────
    implementation("androidx.work:work-runtime-ktx:2.10.0")

    // ── Coroutines Play Services (for await() on Tasks) ───────────────────
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")

    // ── Gson & OkHttp Networking ─────────────────────────────────────────
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:okhttp-sse:4.12.0")

    // ── PDFBox Android & Offline ML Kit OCR ───────────────────────────────
    implementation(libs.pdfbox.android)
    implementation(libs.mlkit.text.recognition)
    // Override 4KB-aligned libimage_processing_util_jni.so pulled by ML Kit 16.0.1
    implementation("androidx.camera:camera-core:1.4.2")
}