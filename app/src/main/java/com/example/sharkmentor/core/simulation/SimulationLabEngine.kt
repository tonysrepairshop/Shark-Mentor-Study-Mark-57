package com.example.sharkmentor.core.simulation

import android.content.Context
import androidx.compose.runtime.Immutable
import com.example.sharkmentor.core.ai.MultiProviderAiClient
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.Locale

@Immutable
data class InteractiveSimulation(
    val id: String,
    val title: String,
    val subject: String,
    val description: String,
    val htmlContent: String,
    val isBuiltIn: Boolean = false,
    val isCloudSynced: Boolean = false,
    val createdAtMillis: Long = System.currentTimeMillis()
)

/**
 * Pure JVM-testable sanitizer & validator for AI-generated interactive HTML5 simulations.
 * Strips markdown fences, removes forbidden external CDN script/stylesheet tags (zero network hangs),
 * injects a runtime `window.onerror` safety overlay, and verifies interactive visual elements exist.
 */
fun sanitizeAndValidateSimulationHtml(rawResponse: String): String? {
    var html = rawResponse.trim()
    val fenceRegex = Regex("""```(?:html|HTML)?\s*([\s\S]*?)```""")
    fenceRegex.find(html)?.let { match ->
        html = match.groupValues[1].trim()
    }
    val docStart = html.indexOf("<!DOCTYPE", ignoreCase = true)
        .takeIf { it >= 0 }
        ?: html.indexOf("<html", ignoreCase = true).takeIf { it >= 0 }
    if (docStart != null) {
        val docEnd = html.lastIndexOf("</html>", ignoreCase = true)
        html = if (docEnd > docStart) {
            html.substring(docStart, docEnd + 7)
        } else {
            html.substring(docStart) + "\n</body></html>"
        }
    } else if (html.contains("<canvas", ignoreCase = true) || html.contains("<svg", ignoreCase = true)) {
        // Auto-wrap valid HTML5 visual fragments that omitted <!DOCTYPE html>
        html = """<!DOCTYPE html><html lang="tr"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><style>body{margin:0;padding:12px;background:#0b1220;color:#e2e8f0;font-family:system-ui,sans-serif;}canvas,svg{max-width:100%;display:block;margin:0 auto;}</style></head><body>$html</body></html>"""
    } else {
        return null
    }

    // Strip external CDN scripts/links so offline & sandboxed WebView never stalls or breaks
    html = html.replace(
        Regex("""<script\b[^>]*\bsrc\s*=\s*["']https?://[^"']*["'][^>]*>\s*</script>""", RegexOption.IGNORE_CASE),
        ""
    )
    html = html.replace(
        Regex("""<link\b[^>]*\bhref\s*=\s*["']https?://[^"']*["'][^>]*>""", RegexOption.IGNORE_CASE),
        ""
    )

    val hasVisual = html.contains("<canvas", ignoreCase = true) || html.contains("<svg", ignoreCase = true)
    val hasScript = html.contains("<script", ignoreCase = true)
    if (!hasVisual) return null
    if (!hasScript && !html.contains("<svg", ignoreCase = true)) return null

    val errorGuard = """
        <script>
        (function(){
          window.addEventListener('error', function(e){
            var box = document.getElementById('shark-sim-err');
            if(!box){
              box = document.createElement('div');
              box.id = 'shark-sim-err';
              box.style.cssText = 'position:fixed;bottom:8px;left:8px;right:8px;background:#7f1d1d;color:#fecaca;padding:8px 12px;border-radius:10px;font:12px system-ui,sans-serif;z-index:9999;';
              document.body.appendChild(box);
            }
            box.textContent = 'Simülasyon çalışma uyarısı: ' + (e.message || 'Yenile düğmesini deneyin.');
          });
        })();
        (function(){
          function fmtSci(str){
            if(typeof str !== 'string') return str;
            return str
              .replace(/\\pi\b/g,'π').replace(/\\Delta\b/g,'Δ').replace(/\\delta\b/g,'δ')
              .replace(/\\theta\b/g,'θ').replace(/\\omega\b/g,'ω').replace(/\\Omega\b/g,'Ω')
              .replace(/\\alpha\b/g,'α').replace(/\\beta\b/g,'β').replace(/\\gamma\b/g,'γ')
              .replace(/\\lambda\b/g,'λ').replace(/\\mu\b/g,'μ').replace(/\\sigma\b/g,'σ')
              .replace(/\\times\b/g,'×').replace(/\\cdot\b/g,'·').replace(/\\sqrt\b/g,'√')
              .replace(/\\infty\b/g,'∞').replace(/\\degree\b|\\deg\b/g,'°').replace(/\\pm\b/g,'±')
              .replace(/\\le\b|\\leq\b/g,'≤').replace(/\\ge\b|\\geq\b/g,'≥').replace(/\\neq\b/g,'≠')
              .replace(/\\rightarrow\b|\\to\b/g,'→')
              .replace(/H_2O/g,'H₂O').replace(/CO_2/g,'CO₂').replace(/O_2/g,'O₂').replace(/N_2/g,'N₂')
              .replace(/H_2SO_4/g,'H₂SO₄').replace(/NH_3/g,'NH₃').replace(/CH_4/g,'CH₄')
              .replace(/\^2\b/g,'²').replace(/\^3\b/g,'³').replace(/\^-1\b/g,'⁻¹').replace(/\^-2\b/g,'⁻²');
          }
          var origFill = CanvasRenderingContext2D && CanvasRenderingContext2D.prototype.fillText;
          if(origFill){
            CanvasRenderingContext2D.prototype.fillText = function(t,x,y,m){
              return m !== undefined ? origFill.call(this, fmtSci(String(t)), x, y, m) : origFill.call(this, fmtSci(String(t)), x, y);
            };
          }
          function walk(node){
            if(!node) return;
            if(node.nodeType === 3 && node.nodeValue){
              var v = fmtSci(node.nodeValue);
              if(v !== node.nodeValue) node.nodeValue = v;
            } else if(node.nodeType === 1 && node.tagName !== 'SCRIPT' && node.tagName !== 'STYLE'){
              for(var i=0;i<node.childNodes.length;i++) walk(node.childNodes[i]);
            }
          }
          document.addEventListener('DOMContentLoaded', function(){ walk(document.body); });
          setTimeout(function(){ walk(document.body); }, 180);
        })();
        </script>
        <div id="shark-corelab-badge" style="position:fixed;right:10px;bottom:8px;z-index:9998;pointer-events:none;font:600 10px/1.2 system-ui,-apple-system,sans-serif;letter-spacing:0.4px;color:rgba(226,232,240,0.78);background:rgba(15,23,42,0.58);border:1px solid rgba(148,163,184,0.28);backdrop-filter:blur(6px);padding:4px 9px;border-radius:999px;">SharkAI CoreLab</div>
    """.trimIndent()

    val bodyClose = html.lastIndexOf("</body>", ignoreCase = true)
    return if (bodyClose > 0) {
        html.substring(0, bodyClose) + "\n" + errorGuard + "\n" + html.substring(bodyClose)
    } else {
        html + "\n" + errorGuard
    }
}

/**
 * Local-first Interactive Simulation Lab Engine for Physics, Chemistry, Biology & Medicine.
 * Ships with 4 verified 60-FPS offline HTML5 Canvas demos and generates custom self-contained
 * interactive simulations on demand via [MultiProviderAiClient].
 */
object SimulationLabEngine {

    private const val STORAGE_FILE = "shark_simulations_v1.json"
    const val MAX_CLOUD_SIMULATIONS = 5

    fun inferSubjectFromTopic(topic: String, hint: String = ""): String {
        val h = hint.trim()
        if (h.isNotBlank() && !h.equals("Fen Bilimleri", ignoreCase = true)) return h
        val lower = topic.lowercase(Locale("tr", "TR"))
        return when {
            listOf("kalp", "damar", "kan", "böbrek", "nefron", "sinir", "nöron", "akciğer", "kas", "ekg", "tansiyon", "hormon", "anatomi", "tıp", "beyin").any { it in lower } -> "Tıp / Biyoloji"
            listOf("hücre", "mitoz", "mayoz", "dna", "rna", "enzim", "fotosentez", "solunum", "genetik", "ekosistem", "biyoloji", "organel", "protein", "osmoz").any { it in lower } -> "Biyoloji"
            listOf("asit", "baz", "titrasyon", "ph", "mol", "atom", "orbital", "periyodik", "tepkime", "denge", "gaz", "çözelti", "pil", "elektroliz", "kimya", "molekül").any { it in lower } -> "Kimya"
            listOf("atış", "ivme", "kuvvet", "newton", "enerji", "momentum", "dalga", "optik", "mercek", "ayna", "ohm", "devre", "elektrik", "manyetik", "basınç", "kaldırma", "tork", "sarkaç", "fizik", "vektör", "hız").any { it in lower } -> "Fizik"
            listOf("türev", "integral", "parabol", "trigonometri", "fonksiyon", "limit", "olasılık", "geometri", "çember", "üçgen", "logaritma", "matris", "matematik").any { it in lower } -> "Matematik"
            listOf("deprem", "iklim", "harita", "mevsim", "dünya", "ay", "güneş", "gezegen", "yörünge", "coğrafya", "astronomi").any { it in lower } -> "Coğrafya / Astronomi"
            else -> ""
        }
    }
    private val gson = Gson()
    private val lock = Any()

    @Volatile
    private var loaded = false

    private val _savedSimulations = MutableStateFlow<List<InteractiveSimulation>>(emptyList())
    val savedSimulations: StateFlow<List<InteractiveSimulation>> = _savedSimulations.asStateFlow()

    // Ephemeral previews generated from AI `/simulasyon` before user taps "Simülasyonu Kaydet"
    private val previewCache = mutableMapOf<String, InteractiveSimulation>()

    private val _activePreviewSimulation = MutableStateFlow<InteractiveSimulation?>(null)
    val activePreviewSimulation: StateFlow<InteractiveSimulation?> = _activePreviewSimulation.asStateFlow()

    fun openSimulationPreview(sim: InteractiveSimulation) {
        previewCache[sim.id] = sim
        _activePreviewSimulation.value = sim
    }

    fun openSimulationById(context: Context, id: String): Boolean {
        ensureLoaded(context)
        val found = previewCache[id]
            ?: _savedSimulations.value.firstOrNull { it.id == id }
            ?: builtInSimulations.firstOrNull { it.id == id }
            ?: return false
        _activePreviewSimulation.value = found
        return true
    }

    fun closeSimulationPreview() {
        _activePreviewSimulation.value = null
    }

    fun isSavedInNotes(id: String): Boolean = _savedSimulations.value.any { it.id == id }

    fun saveSimulationToNotes(context: Context, sim: InteractiveSimulation) {
        ensureLoaded(context)
        val savedItem = sim.copy(isBuiltIn = false)
        previewCache[savedItem.id] = savedItem
        _savedSimulations.value = (listOf(savedItem) + _savedSimulations.value.filterNot { it.id == savedItem.id }).take(30)
        persist(context)
    }

    fun renameSavedSimulation(context: Context, id: String, newTitle: String) {
        val clean = newTitle.trim().take(80)
        if (clean.isBlank()) return
        ensureLoaded(context)
        _savedSimulations.value = _savedSimulations.value.map {
            if (it.id == id) it.copy(title = clean) else it
        }
        previewCache[id]?.let { previewCache[id] = it.copy(title = clean) }
        _activePreviewSimulation.value?.let { cur ->
            if (cur.id == id) _activePreviewSimulation.value = cur.copy(title = clean)
        }
        persist(context)
    }

    val builtInSimulations: List<InteractiveSimulation> by lazy {
        listOf(
            InteractiveSimulation(
                id = "builtin_projectile",
                title = "Eğik Atış Hareketi",
                subject = "Fizik",
                description = "İlk hız, açı ve yerçekimini değiştirerek yörünge, menzil ve maksimum yüksekliği gözlemle.",
                htmlContent = BUILTIN_PROJECTILE_HTML,
                isBuiltIn = true
            ),
            InteractiveSimulation(
                id = "builtin_ohm",
                title = "Ohm Yasası & Elektrik Devresi",
                subject = "Fizik",
                description = "Voltaj ve direnç değiştikçe elektron akış hızı, akım (I) ve ampul parlaklığını canlı gör.",
                htmlContent = BUILTIN_OHM_HTML,
                isBuiltIn = true
            ),
            InteractiveSimulation(
                id = "builtin_titration",
                title = "Asit-Baz Titrasyonu & pH",
                subject = "Kimya",
                description = "Baz ekledikçe fenolftalein renk değişimini ve pH eşdeğerlik eğrisini adım adım izle.",
                htmlContent = BUILTIN_TITRATION_HTML,
                isBuiltIn = true
            ),
            InteractiveSimulation(
                id = "builtin_mitosis",
                title = "Mitoz Hücre Bölünmesi",
                subject = "Biyoloji",
                description = "İnterfazdan sitokineze kromozom ve iğ ipliği hareketlerini adım adım veya otomatik incele.",
                htmlContent = BUILTIN_MITOSIS_HTML,
                isBuiltIn = true
            )
        )
    }

    fun ensureLoaded(context: Context) {
        if (loaded) return
        synchronized(lock) {
            if (loaded) return
            val file = File(context.applicationContext.filesDir, STORAGE_FILE)
            if (file.exists()) {
                runCatching {
                    val type = object : TypeToken<List<InteractiveSimulation>>() {}.type
                    gson.fromJson<List<InteractiveSimulation>>(file.readText(Charsets.UTF_8), type)
                }.getOrNull()?.let { list ->
                    _savedSimulations.value = list.filter { !it.isBuiltIn && it.htmlContent.isNotBlank() }
                }
            }
            loaded = true
        }
    }

    private fun persist(context: Context) {
        synchronized(lock) {
            runCatching {
                val file = File(context.applicationContext.filesDir, STORAGE_FILE)
                file.writeText(gson.toJson(_savedSimulations.value.take(20)), Charsets.UTF_8)
            }
        }
    }

    fun deleteSavedSimulation(context: Context, id: String) {
        ensureLoaded(context)
        val removed = _savedSimulations.value.firstOrNull { it.id == id }
        _savedSimulations.value = _savedSimulations.value.filterNot { it.id == id }
        persist(context)
        if (removed?.isCloudSynced == true) {
            runCatching {
                val auth = com.example.sharkmentor.core.util.GoogleServicesHelper.getFirebaseAuth(context)
                val uid = com.example.sharkmentor.core.util.GoogleServicesHelper.getCurrentUser(auth, context)?.uid
                if (!uid.isNullOrBlank()) {
                    val db = runCatching { com.google.firebase.firestore.FirebaseFirestore.getInstance() }.getOrNull()
                    db?.collection("users")?.document(uid)?.collection("simulations")?.document(id)?.delete()
                }
            }
        }
    }

    /**
     * Toggles Firebase cloud backup for a saved simulation under `users/{uid}/simulations/{simId}`
     * (uses existing user subcollection rule — zero changes to firestore.rules!). Enforces max 5 selected.
     */
    suspend fun toggleCloudBackup(context: Context, simId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        ensureLoaded(context)
        val target = _savedSimulations.value.firstOrNull { it.id == simId }
            ?: return@withContext Result.failure(IllegalStateException("Simülasyon bulunamadı."))
        val auth = com.example.sharkmentor.core.util.GoogleServicesHelper.getFirebaseAuth(context)
        val uid = com.example.sharkmentor.core.util.GoogleServicesHelper.getCurrentUser(auth, context)?.uid
            ?: return@withContext Result.failure(IllegalStateException("Bulut yedekleme için önce giriş yapmalısınız."))
        val db = runCatching { com.google.firebase.firestore.FirebaseFirestore.getInstance() }.getOrNull()
            ?: return@withContext Result.failure(IllegalStateException("Bulut servisi şu an kullanılamıyor."))

        val enableBackup = !target.isCloudSynced
        if (enableBackup) {
            val currentSyncedCount = _savedSimulations.value.count { it.isCloudSynced }
            if (currentSyncedCount >= MAX_CLOUD_SIMULATIONS) {
                return@withContext Result.failure(
                    IllegalStateException("En fazla $MAX_CLOUD_SIMULATIONS simülasyonu buluta yedekleyebilirsiniz. Yeni seçim için mevcut bir yedeği kaldırın.")
                )
            }
            val payload = hashMapOf(
                "id" to target.id,
                "title" to target.title,
                "subject" to target.subject,
                "description" to target.description,
                "htmlContent" to target.htmlContent,
                "createdAtMillis" to target.createdAtMillis,
                "updatedAtMillis" to System.currentTimeMillis()
            )
            runCatching {
                db.collection("users").document(uid)
                    .collection("simulations").document(target.id)
                    .set(payload, com.google.firebase.firestore.SetOptions.merge())
                    .await()
            }.getOrElse { err ->
                return@withContext Result.failure(err)
            }
        } else {
            runCatching {
                db.collection("users").document(uid)
                    .collection("simulations").document(target.id)
                    .delete()
                    .await()
            }
        }

        _savedSimulations.value = _savedSimulations.value.map {
            if (it.id == simId) it.copy(isCloudSynced = enableBackup) else it
        }
        persist(context)
        Result.success(enableBackup)
    }

    /**
     * Pulls cloud-backed simulations (up to 5) from `users/{uid}/simulations` and merges into local storage.
     */
    suspend fun pullAndMergeCloudSimulations(context: Context) = withContext(Dispatchers.IO) {
        ensureLoaded(context)
        runCatching {
            val auth = com.example.sharkmentor.core.util.GoogleServicesHelper.getFirebaseAuth(context)
            val uid = com.example.sharkmentor.core.util.GoogleServicesHelper.getCurrentUser(auth, context)?.uid ?: return@runCatching
            val db = runCatching { com.google.firebase.firestore.FirebaseFirestore.getInstance() }.getOrNull() ?: return@runCatching
            val snap = db.collection("users").document(uid).collection("simulations").get().await()
            if (snap.isEmpty) return@runCatching

            val cloudSims = snap.documents.mapNotNull { doc ->
                val id = doc.getString("id")?.takeIf { it.isNotBlank() } ?: doc.id
                val title = doc.getString("title")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val html = doc.getString("htmlContent")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                InteractiveSimulation(
                    id = id,
                    title = title,
                    subject = doc.getString("subject").orEmpty(),
                    description = doc.getString("description").orEmpty(),
                    htmlContent = html,
                    isBuiltIn = false,
                    isCloudSynced = true,
                    createdAtMillis = doc.getLong("createdAtMillis") ?: System.currentTimeMillis()
                )
            }.take(MAX_CLOUD_SIMULATIONS)

            if (cloudSims.isNotEmpty()) {
                val mergedById = LinkedHashMap<String, InteractiveSimulation>()
                _savedSimulations.value.forEach { mergedById[it.id] = it }
                cloudSims.forEach { cs -> mergedById[cs.id] = cs }
                _savedSimulations.value = mergedById.values.sortedByDescending { it.createdAtMillis }.take(30)
                persist(context)
            }
        }
    }

    suspend fun generateSimulation(
        context: Context,
        topicPrompt: String,
        subjectHint: String = "",
        saveImmediately: Boolean = false
    ): Result<InteractiveSimulation> = withContext(Dispatchers.IO) {
        val cleanTopic = topicPrompt.trim()
        if (cleanTopic.length < 3) {
            return@withContext Result.failure(IllegalArgumentException("Lütfen simüle edilecek konuyu yazın."))
        }
        ensureLoaded(context)

        val systemPrompt = """
Sen Fizik, Kimya, Biyoloji ve Tıp konuları için mobil/tablet ekranlara uygun, hatasız çalışan, tek dosyalık interaktif HTML5 + 2D Canvas eğitim simülasyonu geliştiren uzman bir mühendissin.

KESİN KURALLAR:
1. Yalnızca geçerli, tek dosyalık eksiksiz bir `<!DOCTYPE html>` belgesi döndür. Başına veya sonuna açıklama metni yazma.
2. ASLA harici CDN, harici `<script src="...">`, harici `<link href="...">` veya harici resim URL'si kullanma! Her şey %100 çevrimdışı çalışacak şekilde saf HTML + CSS + vanilla JavaScript + 2D `<canvas>` (veya `<svg>`) ile yazılmalıdır.
3. Tasarım: koyu bilimsel tema (`background:#0B1220; color:#E2E8F0; font-family:system-ui,sans-serif; margin:0; padding:12px;`), vurgu rengi `#38BDF8`, responsive yapı (`width:100%`, dikey ve yatay ekranlara uyumlu).
4. En üstte kısa Türkçe başlık, ortada canlı 2D `<canvas>` görselleştirmesi (net Türkçe etiketler, oklar, hareketli parçacıklar/organeller/vektörler), altta ise 2 ile 4 adet dokunmatik uyumlu kaydırıcı (`<input type="range">`) veya adım/oynat-duraklat düğmesi ve canlı sayısal ölçüm kutuları bulunsun.
5. Kod hatasız olsun: tüm DOM elementleri `DOMContentLoaded` veya script sonunda güvenle bağlansın, `requestAnimationFrame` hafif ve 60 FPS akıcı çalışsın, sıfıra bölme veya tanımsız değişken olmasın.
""".trimIndent()

        val userPrompt = """
Ders / Alan: ${inferSubjectFromTopic(cleanTopic, subjectHint).ifBlank { "Otomatik Belirle" }}
Konu: $cleanTopic

Bu konuyu öğrencinin dokunarak ve parametreleri değiştirerek sezgisel şekilde kavrayabileceği, görsel açıdan zengin, net Türkçe etiketli bir interaktif HTML5 Canvas simülasyonu oluştur.
""".trimIndent()

        val raw = runCatching {
            MultiProviderAiClient.executeUnifiedTextPrompt(
                context = context.applicationContext,
                systemPrompt = systemPrompt,
                userPrompt = "$userPrompt\nKodu özlü ve kompakt tut (~90-140 satır), mutlaka eksiksiz </html> ile bitir.",
                temperature = 0.25f,
                maxTokens = 8192,
                responseJson = false,
                timeoutMs = 60_000L
            )
        }.getOrElse { err ->
            return@withContext Result.failure(err)
        }

        var validatedHtml = sanitizeAndValidateSimulationHtml(raw)
        if (validatedHtml == null) {
            // Compact second attempt in case the first response truncated or returned prose
            val retryRaw = runCatching {
                MultiProviderAiClient.executeUnifiedTextPrompt(
                    context = context.applicationContext,
                    systemPrompt = "Sadece tek dosyalık kısa ve çalışan bir <!DOCTYPE html> + <canvas> + <script> simülasyonu döndür. Hiç açıklama yazma, harici link kullanma.",
                    userPrompt = "Konu: $cleanTopic ($subjectHint). Türkçe etiketli, 2 kaydırıcılı (range input), koyu arka planlı (#0b1220) kısa interaktif HTML5 Canvas simülasyonu yaz.",
                    temperature = 0.3f,
                    maxTokens = 6144,
                    responseJson = false,
                    timeoutMs = 50_000L
                )
            }.getOrDefault("")
            validatedHtml = sanitizeAndValidateSimulationHtml(retryRaw)
        }

        if (validatedHtml == null) {
            val diag = if (raw.isBlank()) "Model boş yanıt döndürdü" else "Model HTML/Canvas üretmedi (${raw.length} karakter)"
            return@withContext Result.failure(
                IllegalStateException("Simülasyon oluşturulamadı ($diag). Lütfen farklı bir model veya daha kısa konu başlığı ile tekrar deneyin.")
            )
        }

        val id = "sim_" + sha256Short("${cleanTopic.lowercase(Locale("tr", "TR"))}_${System.currentTimeMillis()}")
        val sim = InteractiveSimulation(
            id = id,
            title = cleanTopic.take(64),
            subject = inferSubjectFromTopic(cleanTopic, subjectHint),
            description = "Yapay zeka tarafından üretilen interaktif simülasyon",
            htmlContent = validatedHtml,
            isBuiltIn = false,
            createdAtMillis = System.currentTimeMillis()
        )
        previewCache[sim.id] = sim
        if (saveImmediately) {
            saveSimulationToNotes(context, sim)
        }
        Result.success(sim)
    }

    private fun sha256Short(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.take(6).joinToString("") { "%02x".format(it) }
    }

    private val BUILTIN_PROJECTILE_HTML = """
<!DOCTYPE html><html lang="tr"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<style>
*{box-sizing:border-box}body{margin:0;padding:12px;background:#0b1220;color:#e2e8f0;font-family:system-ui,-apple-system,sans-serif}
.top{display:flex;justify-content:space-between;align-items:center;margin-bottom:8px}
h3{margin:0;font-size:15px;color:#38bdf8}.stats{display:grid;grid-template-columns:repeat(3,1fr);gap:6px;margin:8px 0}
.stat{background:#152036;border:1px solid #243454;border-radius:10px;padding:6px 8px;text-align:center}
.stat small{display:block;font-size:10px;color:#94a3b8}.stat b{font-size:13px;color:#f8fafc}
canvas{width:100%;height:230px;display:block;background:#0f172a;border:1px solid #1e293b;border-radius:14px}
.ctrls{display:grid;gap:8px;margin-top:10px}.row{display:flex;align-items:center;gap:8px;font-size:12px}
.row label{width:96px;color:#cbd5e1}.row input{flex:1;accent-color:#38bdf8}.row span{width:54px;text-align:right;font-weight:700;color:#38bdf8}
.btns{display:flex;gap:8px;margin-top:8px}button{flex:1;padding:9px;border:none;border-radius:10px;font-weight:700;cursor:pointer;font-size:12px}
.play{background:#38bdf8;color:#082f49}.rst{background:#1e293b;color:#e2e8f0}
</style></head><body>
<div class="top"><h3>Eğik Atış Hareketi (2B Kinematik)</h3></div>
<canvas id="c"></canvas>
<div class="stats">
  <div class="stat"><small>Maks. Yükseklik</small><b id="hmax">0 m</b></div>
  <div class="stat"><small>Menzil (X)</small><b id="rng">0 m</b></div>
  <div class="stat"><small>Uçuş Süresi</small><b id="tf">0 sn</b></div>
</div>
<div class="ctrls">
  <div class="row"><label>İlk Hız (v₀)</label><input id="v0" type="range" min="10" max="60" value="32"><span id="v0v">32 m/s</span></div>
  <div class="row"><label>Atış Açısı (θ)</label><input id="ang" type="range" min="15" max="80" value="45"><span id="angv">45°</span></div>
  <div class="row"><label>Yerçekimi (g)</label><input id="g" type="range" min="2" max="20" step="0.5" value="9.8"><span id="gv">9.8 m/s²</span></div>
</div>
<div class="btns"><button class="play" id="btnPlay">Fırlat / Duraklat</button><button class="rst" id="btnRst">Sıfırla</button></div>
<script>
const c=document.getElementById('c'),ctx=c.getContext('2d');
const v0El=document.getElementById('v0'),angEl=document.getElementById('ang'),gEl=document.getElementById('g');
let t=0,running=true,last=performance.now();
function resize(){const dpr=window.devicePixelRatio||1;c.width=c.clientWidth*dpr;c.height=c.clientHeight*dpr;ctx.setTransform(dpr,0,0,dpr,0,0);}
window.addEventListener('resize',resize);resize();
function params(){
  const v0=+v0El.value,ang=+angEl.value*Math.PI/180,g=+gEl.value;
  document.getElementById('v0v').textContent=v0+' m/s';
  document.getElementById('angv').textContent=angEl.value+'°';
  document.getElementById('gv').textContent=g.toFixed(1)+' m/s²';
  const tf=2*v0*Math.sin(ang)/g,hmax=(v0*Math.sin(ang))**2/(2*g),rng=v0*Math.cos(ang)*tf;
  document.getElementById('hmax').textContent=hmax.toFixed(1)+' m';
  document.getElementById('rng').textContent=rng.toFixed(1)+' m';
  document.getElementById('tf').textContent=tf.toFixed(2)+' sn';
  return {v0,ang,g,tf,hmax,rng};
}
[v0El,angEl,gEl].forEach(el=>el.addEventListener('input',()=>{t=0;running=true;}));
document.getElementById('btnPlay').onclick=()=>{running=!running;};
document.getElementById('btnRst').onclick=()=>{t=0;running=true;};
function loop(now){
  const dt=Math.min((now-last)/1000,0.05);last=now;
  const {v0,ang,g,tf,hmax,rng}=params();
  if(running){t+=dt;if(t>tf)t=0;}
  const w=c.clientWidth,h=c.clientHeight,pad=24;
  ctx.clearRect(0,0,w,h);
  const maxX=Math.max(rng*1.12,40),maxY=Math.max(hmax*1.25,20);
  const sx=(w-pad*2)/maxX,sy=(h-pad*2)/maxY,s=Math.min(sx,sy);
  const ox=pad,oy=h-pad;
  ctx.strokeStyle='#334155';ctx.lineWidth=1;ctx.beginPath();ctx.moveTo(ox,oy);ctx.lineTo(w-pad,oy);ctx.moveTo(ox,oy);ctx.lineTo(ox,pad);ctx.stroke();
  ctx.strokeStyle='rgba(56,189,248,0.45)';ctx.setLineDash([4,4]);ctx.beginPath();
  for(let i=0;i<=60;i++){const tt=tf*(i/60),x=v0*Math.cos(ang)*tt,y=v0*Math.sin(ang)*tt-0.5*g*tt*tt;const px=ox+x*s,py=oy-y*s;if(i===0)ctx.moveTo(px,py);else ctx.lineTo(px,py);}
  ctx.stroke();ctx.setLineDash([]);
  const cx=v0*Math.cos(ang)*t,cy=Math.max(0,v0*Math.sin(ang)*t-0.5*g*t*t);
  const px=ox+cx*s,py=oy-cy*s;
  ctx.fillStyle='#38bdf8';ctx.beginPath();ctx.arc(px,py,7,0,Math.PI*2);ctx.fill();
  ctx.fillStyle='#f8fafc';ctx.font='11px system-ui';ctx.fillText('x='+cx.toFixed(1)+'m, y='+cy.toFixed(1)+'m',Math.min(px+10,w-115),Math.max(py-10,18));
  requestAnimationFrame(loop);
}
requestAnimationFrame(loop);
</script></body></html>
""".trimIndent()

    private val BUILTIN_OHM_HTML = """
<!DOCTYPE html><html lang="tr"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<style>
*{box-sizing:border-box}body{margin:0;padding:12px;background:#0b1220;color:#e2e8f0;font-family:system-ui,sans-serif}
h3{margin:0 0 8px;font-size:15px;color:#38bdf8}canvas{width:100%;height:220px;display:block;background:#0f172a;border:1px solid #1e293b;border-radius:14px}
.stats{display:grid;grid-template-columns:repeat(3,1fr);gap:6px;margin:8px 0}
.stat{background:#152036;border:1px solid #243454;border-radius:10px;padding:6px;text-align:center}
.stat small{display:block;font-size:10px;color:#94a3b8}.stat b{font-size:13px;color:#f8fafc}
.row{display:flex;align-items:center;gap:8px;font-size:12px;margin-top:8px}
.row label{width:95px;color:#cbd5e1}.row input{flex:1;accent-color:#38bdf8}.row span{width:54px;text-align:right;font-weight:700;color:#38bdf8}
</style></head><body>
<h3>Ohm Yasası (V = I · R) Canlı Devre</h3>
<canvas id="c"></canvas>
<div class="stats">
  <div class="stat"><small>Akım (I = V/R)</small><b id="iv">0 A</b></div>
  <div class="stat"><small>Güç (P = V·I)</small><b id="pv">0 W</b></div>
  <div class="stat"><small>Ampul Parlaklığı</small><b id="bv">Orta</b></div>
</div>
<div class="row"><label>Voltaj (V)</label><input id="v" type="range" min="1" max="24" value="12"><span id="vv">12 V</span></div>
<div class="row"><label>Direnç (R)</label><input id="r" type="range" min="2" max="40" value="8"><span id="rv">8 Ω</span></div>
<script>
const c=document.getElementById('c'),ctx=c.getContext('2d');
const vEl=document.getElementById('v'),rEl=document.getElementById('r');
let phase=0,last=performance.now();
function resize(){const dpr=window.devicePixelRatio||1;c.width=c.clientWidth*dpr;c.height=c.clientHeight*dpr;ctx.setTransform(dpr,0,0,dpr,0,0);}
window.addEventListener('resize',resize);resize();
function loop(now){
  const dt=Math.min((now-last)/1000,0.05);last=now;
  const V=+vEl.value,R=+rEl.value,I=V/R,P=V*I;
  document.getElementById('vv').textContent=V+' V';document.getElementById('rv').textContent=R+' Ω';
  document.getElementById('iv').textContent=I.toFixed(2)+' A';document.getElementById('pv').textContent=P.toFixed(1)+' W';
  document.getElementById('bv').textContent=P>45?'Çok Parlak':P>15?'Parlak':P>5?'Orta':'Loş';
  phase=(phase+dt*Math.min(I*35,160))%100;
  const w=c.clientWidth,h=c.clientHeight,x1=50,x2=w-50,y1=42,y2=h-42;
  ctx.clearRect(0,0,w,h);
  ctx.strokeStyle='#475569';ctx.lineWidth=4;ctx.strokeRect(x1,y1,x2-x1,y2-y1);
  // Pil (sol kenar)
  const my=(y1+y2)/2;ctx.fillStyle='#0f172a';ctx.fillRect(x1-14,my-22,28,44);
  ctx.strokeStyle='#38bdf8';ctx.lineWidth=4;ctx.beginPath();ctx.moveTo(x1-14,my-10);ctx.lineTo(x1+14,my-10);ctx.stroke();
  ctx.strokeStyle='#f87171';ctx.lineWidth=3;ctx.beginPath();ctx.moveTo(x1-8,my+10);ctx.lineTo(x1+8,my+10);ctx.stroke();
  ctx.fillStyle='#e2e8f0';ctx.font='bold 11px system-ui';ctx.fillText(V+'V',x1-36,my+4);
  // Ampul (üst kenar)
  const mx=(x1+x2)/2,glow=Math.min(1,P/55);
  const grad=ctx.createRadialGradient(mx,y1,4,mx,y1,26+glow*24);
  grad.addColorStop(0,'rgba(250,204,21,'+(0.3+glow*0.7)+')');grad.addColorStop(1,'rgba(250,204,21,0)');
  ctx.fillStyle=grad;ctx.beginPath();ctx.arc(mx,y1,48,0,Math.PI*2);ctx.fill();
  ctx.fillStyle='rgba(253,224,71,'+(0.25+glow*0.75)+')';ctx.strokeStyle='#fde047';ctx.lineWidth=2;
  ctx.beginPath();ctx.arc(mx,y1,14,0,Math.PI*2);ctx.fill();ctx.stroke();
  // Direnç (alt kenar)
  ctx.fillStyle='#1e293b';ctx.strokeStyle='#f59e0b';ctx.lineWidth=2;ctx.fillRect(mx-28,y2-10,56,20);ctx.strokeRect(mx-28,y2-10,56,20);
  ctx.fillStyle='#fbbf24';ctx.font='bold 11px system-ui';ctx.fillText(R+' Ω',mx-13,y2+4);
  // Elektron akışı
  const perim=2*((x2-x1)+(y2-y1)),n=18;ctx.fillStyle='#38bdf8';
  for(let i=0;i<n;i++){
    let d=((i/n)*perim+(phase/100)*perim)%perim,ex,ey;
    const bw=x2-x1,bh=y2-y1;
    if(d<bw){ex=x1+d;ey=y1;}else if(d<bw+bh){ex=x2;ey=y1+(d-bw);}
    else if(d<2*bw+bh){ex=x2-(d-bw-bh);ey=y2;}else{ex=x1;ey=y2-(d-2*bw-bh);}
    ctx.beginPath();ctx.arc(ex,ey,4,0,Math.PI*2);ctx.fill();
  }
  requestAnimationFrame(loop);
}
requestAnimationFrame(loop);
</script></body></html>
""".trimIndent()

    private val BUILTIN_TITRATION_HTML = """
<!DOCTYPE html><html lang="tr"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<style>
*{box-sizing:border-box}body{margin:0;padding:12px;background:#0b1220;color:#e2e8f0;font-family:system-ui,sans-serif}
h3{margin:0 0 8px;font-size:15px;color:#38bdf8}canvas{width:100%;height:230px;display:block;background:#0f172a;border:1px solid #1e293b;border-radius:14px}
.stats{display:grid;grid-template-columns:repeat(3,1fr);gap:6px;margin:8px 0}
.stat{background:#152036;border:1px solid #243454;border-radius:10px;padding:6px;text-align:center}
.stat small{display:block;font-size:10px;color:#94a3b8}.stat b{font-size:13px;color:#f8fafc}
.row{display:flex;align-items:center;gap:8px;font-size:12px;margin-top:8px}
.row label{width:110px;color:#cbd5e1}.row input{flex:1;accent-color:#ec4899}.row span{width:54px;text-align:right;font-weight:700;color:#f472b6}
</style></head><body>
<h3>Asit-Baz Titrasyonu (25 mL 0.1M HCl + 0.1M NaOH)</h3>
<canvas id="c"></canvas>
<div class="stats">
  <div class="stat"><small>Anlık pH</small><b id="phv">1.00</b></div>
  <div class="stat"><small>Ortam</small><b id="env">Asidik</b></div>
  <div class="stat"><small>Fenolftalein</small><b id="ind">Renksiz</b></div>
</div>
<div class="row"><label>Eklenen NaOH</label><input id="vb" type="range" min="0" max="50" step="0.5" value="0"><span id="vbv">0.0 mL</span></div>
<script>
const c=document.getElementById('c'),ctx=c.getContext('2d'),vbEl=document.getElementById('vb');
function calcPh(vb){
  const va=25,ca=0.1,cb=0.1,na=va*ca,nb=vb*cb,tot=va+vb;
  if(Math.abs(na-nb)<1e-6)return 7.0;
  if(na>nb){const h=(na-nb)/tot;return -Math.log10(h);}
  const oh=(nb-na)/tot;return 14+Math.log10(oh);
}
function draw(){
  const dpr=window.devicePixelRatio||1;c.width=c.clientWidth*dpr;c.height=c.clientHeight*dpr;ctx.setTransform(dpr,0,0,dpr,0,0);
  const vb=+vbEl.value,ph=calcPh(vb);
  document.getElementById('vbv').textContent=vb.toFixed(1)+' mL';
  document.getElementById('phv').textContent=ph.toFixed(2);
  document.getElementById('env').textContent=ph<6.8?'Asidik':ph>7.2?'Bazik':'Nötr (Eşdeğer)';
  document.getElementById('ind').textContent=ph<8.2?'Renksiz':ph<9.6?'Açık Pembe':'Koyu Pembe';
  const w=c.clientWidth,h=c.clientHeight;ctx.clearRect(0,0,w,h);
  // Sol: Beher
  const bx=18,by=45,bw=84,bh=145;
  const fillH=55+(vb/50)*75;
  const pinkAlpha=ph<8.0?0.08:Math.min(0.85,(ph-8.0)/3.0);
  ctx.fillStyle=ph<8.0?'rgba(148,163,184,0.22)':'rgba(236,72,153,'+pinkAlpha+')';
  ctx.fillRect(bx+3,by+bh-fillH,bw-6,fillH-3);
  ctx.strokeStyle='#94a3b8';ctx.lineWidth=2.5;ctx.strokeRect(bx,by,bw,bh);
  ctx.fillStyle='#cbd5e1';ctx.font='11px system-ui';ctx.fillText('Beher',bx+25,by+bh+16);
  // Sağ: pH Eğrisi
  const gx=128,gy=20,gw=w-gx-18,gh=h-46;
  ctx.strokeStyle='#334155';ctx.lineWidth=1;ctx.strokeRect(gx,gy,gw,gh);
  ctx.strokeStyle='rgba(56,189,248,0.25)';ctx.beginPath();ctx.moveTo(gx,gy+gh/2);ctx.lineTo(gx+gw,gy+gh/2);ctx.stroke();
  ctx.strokeStyle='#38bdf8';ctx.lineWidth=2.5;ctx.beginPath();
  for(let v=0;v<=50;v+=0.5){
    const p=calcPh(v),px=gx+(v/50)*gw,py=gy+gh-(p/14)*gh;
    if(v===0)ctx.moveTo(px,py);else ctx.lineTo(px,py);
  }
  ctx.stroke();
  const px=gx+(vb/50)*gw,py=gy+gh-(ph/14)*gh;
  ctx.fillStyle='#f472b6';ctx.beginPath();ctx.arc(px,py,6,0,Math.PI*2);ctx.fill();
  ctx.fillStyle='#94a3b8';ctx.font='10px system-ui';ctx.fillText('pH 14',gx-28,gy+8);ctx.fillText('pH 7',gx-24,gy+gh/2+3);ctx.fillText('0 mL',gx,gy+gh+14);ctx.fillText('25 mL',gx+gw/2-14,gy+gh+14);ctx.fillText('50 mL',gx+gw-26,gy+gh+14);
}
vbEl.addEventListener('input',draw);window.addEventListener('resize',draw);draw();
</script></body></html>
""".trimIndent()

    private val BUILTIN_MITOSIS_HTML = """
<!DOCTYPE html><html lang="tr"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<style>
*{box-sizing:border-box}body{margin:0;padding:12px;background:#0b1220;color:#e2e8f0;font-family:system-ui,sans-serif}
h3{margin:0 0 8px;font-size:15px;color:#38bdf8}canvas{width:100%;height:220px;display:block;background:#0f172a;border:1px solid #1e293b;border-radius:14px}
.note{background:#152036;border:1px solid #243454;border-radius:10px;padding:8px 10px;margin-top:8px;font-size:12px;line-height:1.45;color:#cbd5e1}
.note b{color:#38bdf8}.steps{display:flex;flex-wrap:wrap;gap:6px;margin-top:8px}
button{padding:7px 10px;border-radius:9px;border:1px solid #334155;background:#1e293b;color:#e2e8f0;font-size:11px;font-weight:700;cursor:pointer}
button.act{background:#38bdf8;color:#082f49;border-color:#38bdf8}
</style></head><body>
<h3>Mitoz Hücre Bölünmesi Evreleri</h3>
<canvas id="c"></canvas>
<div class="note" id="note"></div>
<div class="steps" id="steps"></div>
<script>
const phases=[
  {name:'İnterfaz',desc:'DNA eşlenir (S evresi), organel sayısı artar ve hücre bölünmeye hazırlanır. Kromatin iplikler gevşek haldedir.'},
  {name:'Profaz',desc:'Kromatin iplikler kısalıp kalınlaşarak kromozomları oluşturur. Çekirdek zarı erir, iğ iplikleri oluşmaya başlar.'},
  {name:'Metafaz',desc:'Kromozomlar hücrenin ekvator düzleminde tek sıra halinde dizilir. Kromozomların en net görüntülendiği evredir (karyotip).'},
  {name:'Anafaz',desc:'Kardeş kromatitler sentromer ayrılmasıyla zıt kutuplara çekilir. Geçici olarak kromozom sayısı iki katına çıkar.'},
  {name:'Telofaz',desc:'Kutuplarda yeni çekirdek zarları oluşur, iğ iplikleri kaybolur ve kromozomlar tekrar kromatin ipliğe dönüşür.'},
  {name:'Sitokinez',desc:'Sitoplazma boğumlanarak (hayvan hücresi) iki genetik olarak özdeş yavru hücre oluşur.'}
];
let cur=0,t=0;
const c=document.getElementById('c'),ctx=c.getContext('2d'),stepsEl=document.getElementById('steps');
phases.forEach((p,i)=>{
  const b=document.createElement('button');b.textContent=(i+1)+'. '+p.name;
  b.onclick=()=>{cur=i;renderBtns();};stepsEl.appendChild(b);
});
function renderBtns(){
  [...stepsEl.children].forEach((b,i)=>b.className=i===cur?'act':'');
  document.getElementById('note').innerHTML='<b>'+phases[cur].name+':</b> '+phases[cur].desc;
}
renderBtns();
function resize(){const dpr=window.devicePixelRatio||1;c.width=c.clientWidth*dpr;c.height=c.clientHeight*dpr;ctx.setTransform(dpr,0,0,dpr,0,0);}
window.addEventListener('resize',resize);resize();
function drawChr(x,y,col,single){
  ctx.strokeStyle=col;ctx.lineWidth=4;ctx.lineCap='round';
  ctx.beginPath();ctx.moveTo(x-5,y-10);ctx.lineTo(x+5,y+10);ctx.stroke();
  if(!single){ctx.beginPath();ctx.moveTo(x+5,y-10);ctx.lineTo(x-5,y+10);ctx.stroke();}
}
function loop(){
  t+=0.04;const w=c.clientWidth,h=c.clientHeight,cx=w/2,cy=h/2;
  ctx.clearRect(0,0,w,h);
  const cols=['#38bdf8','#f472b6','#fbbf24','#4ade80'];
  if(cur===5){
    // Sitokinez: iki hücre
    [-68,68].forEach(dx=>{
      ctx.strokeStyle='#38bdf8';ctx.lineWidth=3;ctx.fillStyle='rgba(56,189,248,0.10)';
      ctx.beginPath();ctx.arc(cx+dx,cy,54,0,Math.PI*2);ctx.fill();ctx.stroke();
      ctx.strokeStyle='#94a3b8';ctx.lineWidth=1.5;ctx.beginPath();ctx.arc(cx+dx,cy,24,0,Math.PI*2);ctx.stroke();
      cols.forEach((col,i)=>drawChr(cx+dx+(i-1.5)*9,cy+Math.sin(t+i)*3,col,true));
    });
  } else {
    const rx=cur>=3?112:88,ry=cur>=3?68:76;
    ctx.strokeStyle='#38bdf8';ctx.lineWidth=3;ctx.fillStyle='rgba(56,189,248,0.08)';
    ctx.beginPath();ctx.ellipse(cx,cy,rx,ry,0,0,Math.PI*2);ctx.fill();ctx.stroke();
    if(cur===0){
      ctx.strokeStyle='#94a3b8';ctx.lineWidth=2;ctx.beginPath();ctx.arc(cx,cy,38,0,Math.PI*2);ctx.stroke();
      ctx.strokeStyle='#38bdf8';ctx.lineWidth=1.5;
      for(let i=0;i<6;i++){ctx.beginPath();ctx.arc(cx+Math.cos(i+t*0.3)*14,cy+Math.sin(i*2)*12,10,0,Math.PI*1.4);ctx.stroke();}
    } else {
      // Sentrozomlar & İğ iplikleri
      const pxL=cx-rx+18,pxR=cx+rx-18;
      ctx.fillStyle='#fbbf24';ctx.beginPath();ctx.arc(pxL,cy,5,0,Math.PI*2);ctx.arc(pxR,cy,5,0,Math.PI*2);ctx.fill();
      if(cur>=1&&cur<=3){
        ctx.strokeStyle='rgba(251,191,36,0.28)';ctx.lineWidth=1;
        [-32,-12,12,32].forEach(dy=>{
          ctx.beginPath();ctx.moveTo(pxL,cy);ctx.quadraticCurveTo(cx,cy+dy*1.4,pxR,cy);ctx.stroke();
        });
      }
      if(cur===1){cols.forEach((col,i)=>drawChr(cx+Math.cos(i*1.6+t*0.2)*26,cy+Math.sin(i*1.9)*26,col,false));}
      else if(cur===2){cols.forEach((col,i)=>drawChr(cx,cy+(i-1.5)*24,col,false));}
      else if(cur===3){
        const sep=34+Math.sin(t)*6;
        cols.forEach((col,i)=>{drawChr(cx-sep,cy+(i-1.5)*18,col,true);drawChr(cx+sep,cy+(i-1.5)*18,col,true);});
      } else if(cur===4){
        [-52,52].forEach(dx=>{
          ctx.strokeStyle='#94a3b8';ctx.setLineDash([4,3]);ctx.beginPath();ctx.arc(cx+dx,cy,26,0,Math.PI*2);ctx.stroke();ctx.setLineDash([]);
          cols.forEach((col,i)=>drawChr(cx+dx+(i-1.5)*7,cy,col,true));
        });
      }
    }
  }
  requestAnimationFrame(loop);
}
requestAnimationFrame(loop);
</script></body></html>
""".trimIndent()
}
