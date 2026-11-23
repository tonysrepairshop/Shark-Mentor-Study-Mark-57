package com.example.sharkmentor.ui.zen

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.sharkmentor.core.ai.GeminiAiFactory
import com.example.sharkmentor.core.ai.UserAiPreferences
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ChatMessage(
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

class AiZenCoachViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("shark_ai_zen_coach_prefs", Context.MODE_PRIVATE)
    private val PREF_LAST_QUESTION_TIME = "last_question_timestamp"
    private val COOLDOWN_DURATION_MS = 60 * 60 * 1000L // 60 Minutes

    private val _messages = MutableStateFlow<List<ChatMessage>>(
        listOf(
            ChatMessage(
                text = "Merhaba, ben Shark Zen Koçun. Sınav yolculuğunda zaman zaman yorulmak veya kaygılanmak doğaldır. Zihnini zorlayan konuları buraya yazabilirsin; seni dinliyorum.",
                isUser = false
            )
        )
    )
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _remainingCooldownSeconds = MutableStateFlow(0L)
    val remainingCooldownSeconds: StateFlow<Long> = _remainingCooldownSeconds.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private var tickerJob: Job? = null

    // Empathetic & Grounded Zen Coach Prompt
    private val zenCoachSystemPrompt = """
        Sen SharkMentor platformunun Empatik Zen Koçusun.
        
        MİSYONUN:
        Öğrenciler sınav hazırlığı sürecinde yoğun stres, yetersizlik hissi, tükenmişlik ve kaygı yaşayabilir. Bu alan onların duygularını rahatça paylaşıp zihinsel yüklerini hafifletebileceği güvenli bir alandır.

        DAVRANIŞ KURALLARI:
        1. Asla didaktik, sorgulayıcı veya ders veren bir tonda olma (Sokratik sorgulama yapma).
        2. Öğrencinin hislerini içtenlikle onayla ve anla ("Böyle hissetmen çok doğal", "Büyük bir emek veriyorsun ve bu ağırlık normal").
        3. Samimi, şefkatli, sakinleştirici ve umut veren bir dil kullan.
        4. Cevapların kısa, ferahlatıcı ve net olsun (Maksimum 3-4 cümle). Uzun paragraflarla öğrenciyi yorma.
        5. Öğrenciye bir nefes aldır, omzundaki yükü hafiflet ve masaya sakin bir zihinle dönmesi için nazik bir cesaret ver.
    """.trimIndent()

    init {
        UserAiPreferences.initialize(application)
        checkAndStartCooldownTicker()
    }

    private fun checkAndStartCooldownTicker() {
        tickerJob?.cancel()
        tickerJob = viewModelScope.launch {
            while (true) {
                val lastTime = prefs.getLong(PREF_LAST_QUESTION_TIME, 0L)
                val elapsed = System.currentTimeMillis() - lastTime
                val remaining = COOLDOWN_DURATION_MS - elapsed

                if (remaining > 0) {
                    _remainingCooldownSeconds.value = remaining / 1000
                } else {
                    _remainingCooldownSeconds.value = 0
                }
                delay(1000)
            }
        }
    }

    fun sendQuestion(questionText: String) {
        if (questionText.isBlank() || _remainingCooldownSeconds.value > 0 || _isLoading.value) return

        // 1. Record User Message
        val updatedList = _messages.value.toMutableList().apply {
            add(ChatMessage(text = questionText.trim(), isUser = true))
        }
        _messages.value = updatedList

        // 2. Persist Cooldown
        prefs.edit().putLong(PREF_LAST_QUESTION_TIME, System.currentTimeMillis()).apply()
        checkAndStartCooldownTicker()

        // 3. Query AI model (selectively enriched with anonymized Student Dossier when consented)
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _isLoading.value = true
            try {
                val appCtx = getApplication<Application>()
                com.example.sharkmentor.core.coaching.StudentDossierManager.ensureLoaded(appCtx)
                val dossierBlock = if (
                    com.example.sharkmentor.core.coaching.StudentDossierManager.shouldAttachDossier(
                        com.example.sharkmentor.core.coaching.DossierAiChannel.ZEN_COACH,
                        questionText
                    )
                ) {
                    com.example.sharkmentor.core.coaching.StudentDossierManager.buildAnonymizedDossierPrompt()
                } else ""
                val effectiveSystemPrompt = if (dossierBlock.isNotBlank()) {
                    "$dossierBlock\n\n$zenCoachSystemPrompt"
                } else {
                    zenCoachSystemPrompt
                }
                val result = GeminiAiFactory.generateContentWithFailover(
                    context = appCtx,
                    systemInstruction = effectiveSystemPrompt,
                    userPrompt = questionText.trim(),
                    responseJson = false,
                    temperature = 0.45f,
                    maxOutputTokens = 1024,
                    timeoutMs = 45_000L
                )
                val aiReply = result.getOrThrow().text.takeIf { it.isNotBlank() }
                    ?: "Derin bir nefes al. Yanındayım, bu his geçici ve sen düşündüğünden daha güçlüsün."
                _messages.value = _messages.value + ChatMessage(text = aiReply, isUser = false)
            } catch (e: Exception) {
                Log.w("AiZenCoachVM", "Zen Coach Gemini error: ${e.message}", e)
                _messages.value = _messages.value + ChatMessage(
                    text = "Şu an bağlantı kurulamadı ama unutma: Yalnız değilsin. Birkaç yudum su al ve derin bir nefesle devam et.",
                    isUser = false
                )
            } finally {
                _isLoading.value = false
            }
        }
    }
}

