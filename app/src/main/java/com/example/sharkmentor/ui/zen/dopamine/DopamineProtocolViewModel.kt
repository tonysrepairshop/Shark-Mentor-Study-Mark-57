package com.example.sharkmentor.ui.zen.dopamine

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sharkmentor.core.ai.GeminiAiFactory
import com.example.sharkmentor.core.util.GoogleServicesHelper
import com.example.sharkmentor.ui.zen.dopamine.model.DopamineProtocolState
import com.example.sharkmentor.ui.zen.dopamine.model.ReflectionMessage
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest

/**
 * ViewModel for the Bio-Shield & Dopamine Protocol.
 *
 * Responsibilities:
 *  - Mirror Firestore state in [protocolState] via a real-time listener.
 *  - Hash PIN client-side (SHA-256) before persisting; plain PIN never leaves the device.
 *  - Enforce the 24-hour cooling-off window on unlock requests.
 *  - Drive the nightly Gemini 3.6 Flash reflection companion.
 */
class DopamineProtocolViewModel : ViewModel() {

    private val firestore get() = GoogleServicesHelper.getFirebaseFirestore()
    private val auth get() = GoogleServicesHelper.getFirebaseAuth()
    private var protocolListener: ListenerRegistration? = null

    private val _protocolState = MutableStateFlow(DopamineProtocolState())
    val protocolState: StateFlow<DopamineProtocolState> = _protocolState.asStateFlow()

    private val _reflectionMessages = MutableStateFlow<List<ReflectionMessage>>(emptyList())
    val reflectionMessages: StateFlow<List<ReflectionMessage>> = _reflectionMessages.asStateFlow()

    private val _isAiThinking = MutableStateFlow(false)
    val isAiThinking: StateFlow<Boolean> = _isAiThinking.asStateFlow()

    private val reflectionSystemPrompt = """
        Sen SharkMentor Dopamin Orucu'nun Akşam Refleksiyon Koçusun.
        Kişilik ve Ton:
        - Empatik, yargılamayan, sakin ve analitik.
        - Nasihat veya beylik sözler verme; kullanıcıya gün içinde iradesini zorlayan tetikleyicileri
          (sosyal medya, erteleme, zihinsel sis) nazikçe fark ettir.
        - Kullanıcının söylediklerini özetle ve yarın için tek bir odaklanma hedefi belirlemesini iste.
        - Gizlilik Garantisi: Bu konuşma tamamen şifreli ve gizlidir.
    """.trimIndent()

    init {
        listenToProtocolState()
    }

    // ── Private helpers ─────────────────────────────────────────────────────────

    /** SHA-256 hex digest of [pin]. Plain PIN never reaches Firestore. */
    private fun hashPin(pin: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(pin.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun protocolDocRef(uid: String) =
        firestore?.collection("students")?.document(uid)
            ?.collection("meta")?.document("dopamineProtocol")

    // ── Firestore listener ──────────────────────────────────────────────────────

    private fun listenToProtocolState() {
        val currentUser = runCatching { FirebaseAuth.getInstance().currentUser }.getOrNull()
            ?: GoogleServicesHelper.getCurrentUser(auth)
        val uid = currentUser?.uid
        if (currentUser == null || uid == null) return
        runCatching {
            protocolListener?.remove()
            protocolListener = protocolDocRef(uid)?.addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener

                val isActive = snapshot.getBoolean("isActive") ?: false
                val activatedAt = snapshot.getLong("activatedAt") ?: 0L
                val expiresAt = snapshot.getLong("expiresAt") ?: 0L
                val hashedPin = snapshot.getString("hashedPin") ?: ""
                val unlockRequestedAt = snapshot.getLong("unlockRequestedAt")

                val isCooling = unlockRequestedAt != null &&
                        (System.currentTimeMillis() - unlockRequestedAt) < 24L * 60 * 60 * 1000

                // Cooling-off window expired → auto-deactivate
                if (unlockRequestedAt != null && !isCooling) {
                    deactivateImmediate()
                    return@addSnapshotListener
                }

                val activeState = isActive && System.currentTimeMillis() < expiresAt
                try {
                    val appContext = FirebaseApp.getInstance().applicationContext
                    val prefs = appContext.getSharedPreferences("shark_dopamine_prefs", Context.MODE_PRIVATE)
                    prefs.edit().putBoolean("is_active", activeState).apply()
                } catch (_: Exception) {}

                _protocolState.value = DopamineProtocolState(
                    isActive = activeState,
                    activatedAt = activatedAt,
                    expiresAt = expiresAt,
                    hashedPin = hashedPin,
                    unlockRequestedAt = unlockRequestedAt,
                    is24hCoolingOff = isCooling
                )
            }
        }
    }

    // ── Public API ──────────────────────────────────────────────────────────────

    /**
     * Activates the 30-day protocol with a SHA-256 hashed PIN.
     * Called after the user completes the KVKK terms + PIN-setup flow.
     */
    fun activateProtocol(pin: String) {
        val uid = GoogleServicesHelper.getCurrentUser(auth)?.uid ?: return
        val docRef = protocolDocRef(uid) ?: return
        val now = System.currentTimeMillis()
        val data = mapOf(
            "isActive" to true,
            "activatedAt" to now,
            "expiresAt" to (now + 30L * 24 * 60 * 60 * 1000),
            "hashedPin" to hashPin(pin),
            "unlockRequestedAt" to null
        )
        try {
            val appContext = FirebaseApp.getInstance().applicationContext
            val prefs = appContext.getSharedPreferences("shark_dopamine_prefs", Context.MODE_PRIVATE)
            prefs.edit().putBoolean("is_active", true).apply()
            
            val manager = com.example.sharkmentor.domain.engine.DopamineFastManager(appContext, prefs)
            manager.startFast(30 * 24)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            docRef.set(data).await()
        }
    }

    /**
     * Validates the PIN and, if correct, writes the current timestamp as
     * [unlockRequestedAt] to start the 24-hour cooling-off countdown.
     * [onResult] receives (success, message) on the calling coroutine.
     */
    fun requestUnlockWithPin(pin: String, onResult: (Boolean, String) -> Unit) {
        if (hashPin(pin) != _protocolState.value.hashedPin) {
            onResult(false, "Geçersiz PIN kodu.")
            return
        }
        val uid = GoogleServicesHelper.getCurrentUser(auth)?.uid ?: return
        val docRef = protocolDocRef(uid) ?: return
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            docRef.update("unlockRequestedAt", System.currentTimeMillis()).await()
            onResult(true, "Kilit açma talebi alındı. 24 saatlik soğuma süresi başladı.")
        }
    }

    /**
     * Cancels a pending unlock request (user changed their mind within the 24h window).
     */
    fun cancelUnlockRequest() {
        val uid = GoogleServicesHelper.getCurrentUser(auth)?.uid ?: return
        val docRef = protocolDocRef(uid) ?: return
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            docRef.update("unlockRequestedAt", null).await()
        }
    }

    /** Called automatically by the Firestore listener when the cooling-off window expires. */
    private fun deactivateImmediate() {
        val uid = GoogleServicesHelper.getCurrentUser(auth)?.uid ?: return
        val docRef = protocolDocRef(uid) ?: return
        try {
            val appContext = FirebaseApp.getInstance().applicationContext
            val prefs = appContext.getSharedPreferences("shark_dopamine_prefs", Context.MODE_PRIVATE)
            prefs.edit().putBoolean("is_active", false).apply()

            val manager = com.example.sharkmentor.domain.engine.DopamineFastManager(appContext, prefs)
            manager.forfeitFast()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            docRef.update(
                mapOf(
                    "isActive" to false,
                    "unlockRequestedAt" to null
                )
            ).await()
        }
    }

    // ── Evening reflection companion ────────────────────────────────────────────

    /**
     * Appends [userText] as a user message, then queries AI and appends the reply.
     * The full conversation history is sent as context for each turn.
     */
    fun sendReflectionMessage(userText: String) {
        if (userText.isBlank()) return
        val userMsg = ReflectionMessage(sender = "user", text = userText.trim())
        _reflectionMessages.value = _reflectionMessages.value + userMsg

        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _isAiThinking.value = true
            try {
                val history = _reflectionMessages.value.joinToString("\n") { msg ->
                    "${if (msg.sender == "user") "Öğrenci" else "Koç"}: ${msg.text}"
                }
                val appContext = runCatching { FirebaseApp.getInstance().applicationContext }.getOrNull()
                val rawText = if (appContext != null) {
                    com.example.sharkmentor.core.ai.MultiProviderAiClient.executeUnifiedTextPrompt(
                        context = appContext,
                        systemPrompt = reflectionSystemPrompt,
                        userPrompt = history,
                        temperature = 0.45f,
                        maxTokens = 1024,
                        timeoutMs = 45_000L
                    )
                } else {
                    GeminiAiFactory.generateContentWithFailover(
                        context = null,
                        systemInstruction = reflectionSystemPrompt,
                        userPrompt = history,
                        responseJson = false,
                        temperature = 0.45f,
                        maxOutputTokens = 1024,
                        timeoutMs = 45_000L
                    ).getOrThrow().text
                }
                val reply = rawText.trim().ifBlank { "Seni dinliyorum, devam et." }
                _reflectionMessages.value = _reflectionMessages.value +
                        ReflectionMessage(sender = "ai", text = reply)
            } catch (e: Exception) {
                Log.w("DopamineProtocolVM", "Reflection AI error: ${e.message}", e)
                _reflectionMessages.value = _reflectionMessages.value +
                        ReflectionMessage(
                            sender = "ai",
                            text = "Bağlantıda küçük bir aksaklık oldu, ama buradayım. Devam edebilirsin."
                        )
            } finally {
                _isAiThinking.value = false
            }
        }
    }

    override fun onCleared() {
        protocolListener?.remove()
        protocolListener = null
        super.onCleared()
    }
}

