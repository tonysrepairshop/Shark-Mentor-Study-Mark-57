package com.example.sharkmentor.core.ai

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Represents a lightweight, self-contained 2D HTML5 Canvas + SVG interactive simulation.
 * Stored 100% locally on-device (`files/ai_simulations_v1.json`, 0 Firestore reads/writes).
 */
data class InteractiveSimulation(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val subject: String = "Fen & Matematik",
    val summary: String = "",
    val htmlCode: String,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val isBuiltIn: Boolean = false
)

/**
 * Offline-first 2D Interactive Simulation Studio engine for Shark AI (`/simulasyon`).
 *
 * Design guarantees:
 * - Zero heavy 3D/WebGL/CDN dependencies -> runs smoothly at 60fps even on entry-level phones.
 * - Zero Firestore quota impact -> pure local JSON persistence in app filesDir.
 * - Built-in JS error bridge (`SharkSimBridge.onJsError`) -> enables 1-tap AI auto-repair.
 */
object SimulationStudioEngine {

    const val SIMULATION_OPEN_TAG = "#SIM_OPEN:"
    private const val CACHE_FILE_NAME = "ai_simulations_v1.json"
    private const val MAX_SAVED_SIMULATIONS = 30

    private val gson = Gson()
    private val fileLock = Any()

    @Volatile
    private var loadedFromDisk = false

    private val _savedSimulations = MutableStateFlow<List<InteractiveSimulation>>(emptyList())
    val savedSimulations: StateFlow<List<InteractiveSimulation>> = _savedSimulations.asStateFlow()

    fun ensureLoaded(context: Context) {
        if (loadedFromDisk) return
        synchronized(fileLock) {
            if (loadedFromDisk) return
            val file = File(context.applicationContext.filesDir, CACHE_FILE_NAME)
            val parsed = if (file.exists()) {
                runCatching {
                    val type = object : TypeToken<List<InteractiveSimulation>>() {}.type
                    gson.fromJson<List<InteractiveSimulation>>(file.readText(), type)
                        ?.filter { it.id.isNotBlank() && it.htmlCode.isNotBlank() }
                        .orEmpty()
                }.getOrDefault(emptyList())
            } else {
                emptyList()
            }
            _savedSimulations.value = parsed.sortedByDescending { it.createdAtMillis }
            loadedFromDisk = true
        }
    }

    fun findById(context: Context, id: String): InteractiveSimulation? {
        ensureLoaded(context)
        return _savedSimulations.value.firstOrNull { it.id == id }
            ?: builtInPresets().firstOrNull { it.id == id }
    }

    fun saveSimulation(context: Context, simulation: InteractiveSimulation): InteractiveSimulation {
        ensureLoaded(context)
        val cleanSim = simulation.copy(isBuiltIn = false)
        synchronized(fileLock) {
            val updated = (_savedSimulations.value.filterNot { it.id == cleanSim.id } + cleanSim)
                .sortedByDescending { it.createdAtMillis }
                .take(MAX_SAVED_SIMULATIONS)
            _savedSimulations.value = updated
            writeCacheLocked(context, updated)
        }
        return cleanSim
    }

    fun deleteSimulation(context: Context, id: String) {
        ensureLoaded(context)
        synchronized(fileLock) {
            val updated = _savedSimulations.value.filterNot { it.id == id }
            _savedSimulations.value = updated
            writeCacheLocked(context, updated)
        }
    }

    private fun writeCacheLocked(context: Context, list: List<InteractiveSimulation>) {
        runCatching {
            val file = File(context.applicationContext.filesDir, CACHE_FILE_NAME)
            file.writeText(gson.toJson(list))
        }
    }

    /**
     * Extracts `#SIM_OPEN:<id>` marker from a Shark AI chat message so raw tags are never displayed.
     */
    fun extractSimulationOpenTag(messageText: String): Pair<String, String?> {
        val idx = messageText.indexOf(SIMULATION_OPEN_TAG)
        if (idx < 0) return messageText to null
        val display = messageText.substring(0, idx).trimEnd()
        val simId = messageText.substring(idx + SIMULATION_OPEN_TAG.length)
            .lineSequence()
            .firstOrNull()
            ?.trim()
            ?.takeIf { it.isNotBlank() }
        return display to simId
    }

    /**
     * Pure JVM-testable HTML sanitizer and offline wrapper.
     * - Strips markdown code fences.
     * - Rejects/strips external network `<script src="http...">` and `<link href="http...">` dependencies.
     * - Rejects heavy WebGL/Three.js payloads if present without 2D canvas fallback.
     * - Injects mobile viewport + `SharkSimBridge.onJsError` reporter so 1-tap auto-repair works.
     */
    fun extractSafeSimulationHtml(rawResponse: String): String? {
        if (rawResponse.isBlank()) return null
        var cleaned = rawResponse.trim()

        // Strip markdown fenced code block if present
        val fenceRegex = Regex("```(?:html|HTML)?\\s*([\\s\\S]*?)```")
        val fencedMatch = fenceRegex.find(cleaned)
        if (fencedMatch != null) {
            cleaned = fencedMatch.groupValues[1].trim()
        }

        // Extract from <!DOCTYPE or <html through </html> if surrounded by prose
        val docStart = Regex("(?i)<!doctype\\s+html|<html[\\s>]").find(cleaned)?.range?.first
        val docEnd = Regex("(?i)</html>").find(cleaned)?.range?.last
        if (docStart != null && docEnd != null && docEnd > docStart) {
            cleaned = cleaned.substring(docStart, docEnd + 1)
        }

        // Must contain actual interactive visual markup (canvas, svg, or script)
        val lower = cleaned.lowercase()
        val hasVisualElement = lower.contains("<canvas") || lower.contains("<svg") || lower.contains("<script")
        if (!hasVisualElement || cleaned.length < 120) return null

        // Strip any external CDN/network script or stylesheet tags to guarantee 100% offline execution
        cleaned = cleaned
            .replace(Regex("(?is)<script[^>]*\\bsrc\\s*=\\s*[\"']https?://[^\"']*[\"'][^>]*>\\s*</script>"), "")
            .replace(Regex("(?is)<link[^>]*\\bhref\\s*=\\s*[\"']https?://[^\"']*[\"'][^>]*/?>"), "")

        // Reject pure WebGL-only scripts that fail or lag on low-end WebViews
        val postLower = cleaned.lowercase()
        if ((postLower.contains("getcontext('webgl") || postLower.contains("getcontext(\"webgl")) &&
            !postLower.contains("getcontext('2d") && !postLower.contains("getcontext(\"2d")
        ) {
            return null
        }

        val errorHookScript = """
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
            <script>
            (function(){
              function report(msg){
                try {
                  if (window.SharkSimBridge && typeof window.SharkSimBridge.onJsError === 'function') {
                    window.SharkSimBridge.onJsError(String(msg || 'Bilinmeyen JS hatası'));
                  }
                } catch(e){}
              }
              window.onerror = function(message, source, lineno, colno) {
                report(message + ' (satır ' + lineno + ':' + colno + ')');
                return false;
              };
              window.addEventListener('unhandledrejection', function(ev) {
                report('Promise hatası: ' + (ev && ev.reason ? ev.reason : 'bilinmiyor'));
              });
            })();
            </script>
        """.trimIndent()

        return if (cleaned.contains("<head", ignoreCase = true)) {
            cleaned.replaceFirst(Regex("(?i)<head([^>]*)>"), "<head$1>\n$errorHookScript\n")
        } else if (cleaned.contains("<html", ignoreCase = true)) {
            cleaned.replaceFirst(Regex("(?i)<html([^>]*)>"), "<html$1><head>\n$errorHookScript\n</head>")
        } else {
            """
                <!DOCTYPE html>
                <html lang="tr">
                <head>
                <meta charset="UTF-8">
                $errorHookScript
                </head>
                <body style="margin:0;background:#0F172A;color:#F8FAFC;font-family:system-ui,-apple-system,sans-serif;">
                $cleaned
                </body>
                </html>
            """.trimIndent()
        }
    }

    /**
     * Generates a lightweight, single-file offline 2D HTML5 Canvas + SVG interactive simulation
     * for any requested topic (Physics, Biology, Medicine, Chemistry, Mathematics).
     */
    suspend fun generateSimulationWithAi(
        context: Context,
        topicPrompt: String
    ): Result<InteractiveSimulation> = withContext(Dispatchers.IO) {
        val cleanTopic = topicPrompt.trim()
        if (cleanTopic.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Simülasyon konusu boş olamaz."))
        }

        val systemPrompt = """
            Sen SharkMentor 2D İnteraktif Bilim Simülasyonu Mühendisisin.
            Öğrencinin istediği konu için TEK DOSYALIK, tamamen çevrimdışı (offline) çalışan, ultra akıcı 2D HTML5 Canvas (veya SVG) interaktif simülasyon üret.

            KESİN KURALLAR:
            1. YALNIZCA saf HTML + CSS + vanilla JavaScript + HTML5 2D Canvas (`getContext('2d')`) veya inline SVG kullan.
            2. HİÇBİR harici CDN, harici `<script src="...">`, Three.js, Chart.js, WebGL veya internet isteği KULLANMA.
            3. Mobil ve tablet ekranlara tam duyarlı olsun: koyu modern laboratuvar arka planı (`#0B1220`), net kontrastlı etiketler, taşma yok (`box-sizing: border-box; overflow-x: hidden`).
            4. Mutlaka:
               - Canlı hareketli/etkileşimli 2D `<canvas>` sahnesi (`devicePixelRatio` uyumlu ve `ResizeObserver` veya `resize` destekli)
               - Anlık sayısal göstergeler (formül sonuçları, pH, hız, voltaj, oran vb.)
               - Konuya özel en az 2-3 etkileşimli kaydırıcı (`<input type="range">`) + `Başlat/Duraklat` ve `Sıfırla` butonları
               - Tüm başlıklar, birimler ve kısa bilimsel açıklama kutusu TÜRKÇE olsun.
            5. Yanıtı SADECE tek bir geçerli `<!DOCTYPE html>...</html>` belgesi olarak döndür; ekstra açıklama ekleme.
        """.trimIndent()

        val userPrompt = "Şu konu için etkileşimli, bilimsel olarak doğru ve görsel açıdan anlaşılır 2D HTML5 Canvas simülasyonu üret: $cleanTopic"

        runCatching {
            val raw = MultiProviderAiClient.executeUnifiedTextPrompt(
                context = context,
                systemPrompt = systemPrompt,
                userPrompt = userPrompt,
                temperature = 0.25f,
                maxTokens = 6144,
                responseJson = false,
                timeoutMs = 55_000L
            )
            val safeHtml = extractSafeSimulationHtml(raw)
                ?: throw IllegalStateException("Üretilen simülasyon kodu doğrulanamadı. Lütfen tekrar dene.")

            val title = inferSimulationTitle(cleanTopic, safeHtml)
            val subject = inferSubjectFromTopic(cleanTopic)
            val sim = InteractiveSimulation(
                id = "sim_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}",
                title = title,
                subject = subject,
                summary = cleanTopic.take(120),
                htmlCode = safeHtml,
                createdAtMillis = System.currentTimeMillis(),
                isBuiltIn = false
            )
            saveSimulation(context, sim)
        }
    }

    /**
     * 1-tap AI auto-repair: feeds the exact runtime JS error + current HTML back to AI,
     * sanitizes the repaired output, and updates the locally saved simulation.
     */
    suspend fun repairSimulationWithAi(
        context: Context,
        simulation: InteractiveSimulation,
        jsErrorMessage: String
    ): Result<InteractiveSimulation> = withContext(Dispatchers.IO) {
        val systemPrompt = """
            Sen SharkMentor 2D HTML5 Canvas Simülasyon Onarım Mühendisisin.
            Verilen tek dosyalık HTML5 Canvas simülasyonundaki JavaScript hatasını düzelt ve çalışan tam HTML dosyasını döndür.
            Harici kütüphane/CDN/WebGL kullanma. YALNIZCA düzeltilmiş tam `<!DOCTYPE html>...</html>` kodunu ver.
        """.trimIndent()

        val userPrompt = buildString {
            appendLine("Konu: ${simulation.title}")
            appendLine("Hata Mesajı: $jsErrorMessage")
            appendLine("\nMevcut HTML kodu:")
            appendLine(simulation.htmlCode.take(14_000))
        }

        runCatching {
            val raw = MultiProviderAiClient.executeUnifiedTextPrompt(
                context = context,
                systemPrompt = systemPrompt,
                userPrompt = userPrompt,
                temperature = 0.15f,
                maxTokens = 6144,
                responseJson = false,
                timeoutMs = 55_000L
            )
            val safeHtml = extractSafeSimulationHtml(raw)
                ?: throw IllegalStateException("Onarılan simülasyon kodu doğrulanamadı.")

            val updated = simulation.copy(
                htmlCode = safeHtml,
                createdAtMillis = System.currentTimeMillis(),
                isBuiltIn = false
            )
            saveSimulation(context, updated)
        }
    }

    private fun inferSimulationTitle(topic: String, html: String): String {
        val titleMatch = Regex("(?is)<title>([^<]{2,64})</title>").find(html)
            ?.groupValues?.getOrNull(1)?.trim()
        if (!titleMatch.isNullOrBlank() && !titleMatch.equals("Document", ignoreCase = true)) {
            return titleMatch
        }
        return topic.trim().replaceFirstChar { it.uppercase() }.take(46)
    }

    private fun inferSubjectFromTopic(topic: String): String {
        val norm = topic.lowercase()
        return when {
            norm.contains("sinaps") || norm.contains("nöron") || norm.contains("kalp") ||
                norm.contains("dna") || norm.contains("hücre") || norm.contains("mitoz") ||
                norm.contains("mayoz") || norm.contains("enzim") || norm.contains("solunum") ||
                norm.contains("fotosentez") || norm.contains("tıp") || norm.contains("biyoloji") -> "Biyoloji / Tıp"
            norm.contains("asit") || norm.contains("baz") || norm.contains("titrasyon") ||
                norm.contains("ph") || norm.contains("mol") || norm.contains("orbital") ||
                norm.contains("gaz") || norm.contains("kimya") || norm.contains("denge") -> "Kimya"
            norm.contains("türev") || norm.contains("integral") || norm.contains("parabol") ||
                norm.contains("trigonometri") || norm.contains("vektör") || norm.contains("fonksiyon") ||
                norm.contains("geometri") || norm.contains("matematik") -> "Matematik"
            else -> "Fizik"
        }
    }

    /**
     * 3 verified, instant, zero-latency offline 2D Canvas simulations shipped directly in-code
     * (0 KB binary bloat, 60fps smooth on any device).
     */
    fun builtInPresets(): List<InteractiveSimulation> = listOf(
        InteractiveSimulation(
            id = "preset_projectile_2d",
            title = "Eğik Atış Hareketi",
            subject = "Fizik",
            summary = "İlk hız, atış açısı ve yerçekimi ivmesinin yörünge, maksimum yükseklik ve menzile etkisi.",
            htmlCode = PRESET_PROJECTILE_HTML,
            createdAtMillis = 3L,
            isBuiltIn = true
        ),
        InteractiveSimulation(
            id = "preset_synapse_2d",
            title = "Sinaps & Aksiyon Potansiyeli",
            subject = "Biyoloji / Tıp",
            summary = "Ya hep ya hiç yasası, miyelin kılıf iletim hızı ve sinaptik aralıkta nörotransmitter salınımı.",
            htmlCode = PRESET_SYNAPSE_HTML,
            createdAtMillis = 2L,
            isBuiltIn = true
        ),
        InteractiveSimulation(
            id = "preset_titration_2d",
            title = "Asit-Baz Titrasyonu & pH Eğrisi",
            subject = "Kimya",
            summary = "Kuvvetli asit-baz titrasyonunda indikatör renk değişimi ve eşdeğerlik noktası analizi.",
            htmlCode = PRESET_TITRATION_HTML,
            createdAtMillis = 1L,
            isBuiltIn = true
        )
    )

    private val PRESET_PROJECTILE_HTML = """
        <!DOCTYPE html>
        <html lang="tr">
        <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no">
        <title>Eğik Atış Hareketi</title>
        <style>
          *{box-sizing:border-box;margin:0;padding:0}
          body{background:#0B1220;color:#F8FAFC;font-family:system-ui,-apple-system,sans-serif;padding:12px;display:flex;flex-direction:column;gap:10px;min-height:100vh}
          .stats{display:grid;grid-template-columns:repeat(3,1fr);gap:8px}
          .card{background:#131E33;border:1px solid #243554;border-radius:12px;padding:8px 10px;text-align:center}
          .k{font-size:11px;color:#94A3B8}
          .v{font-size:15px;font-weight:700;color:#38BDF8;margin-top:2px}
          canvas{width:100%;height:260px;background:#0F172A;border:1px solid #243554;border-radius:14px;display:block}
          .controls{background:#131E33;border:1px solid #243554;border-radius:14px;padding:12px;display:flex;flex-direction:column;gap:10px}
          .row{display:flex;flex-direction:column;gap:4px}
          .lbl{display:flex;justify-content:space-between;font-size:12px;color:#CBD5E1}
          input[type=range]{width:100%;accent-color:#38BDF8}
          .btns{display:flex;gap:8px}
          button{flex:1;padding:10px;border-radius:10px;border:none;font-weight:700;font-size:13px;cursor:pointer}
          .primary{background:#38BDF8;color:#082F49}
          .ghost{background:#1E293B;color:#E2E8F0;border:1px solid #334155}
        </style>
        </head>
        <body>
          <div class="stats">
            <div class="card"><div class="k">Maks. Yükseklik</div><div class="v" id="hmax">0 m</div></div>
            <div class="card"><div class="k">Menzil (X)</div><div class="v" id="range">0 m</div></div>
            <div class="card"><div class="k">Uçuş Süresi</div><div class="v" id="tflight">0 s</div></div>
          </div>
          <canvas id="cv"></canvas>
          <div class="controls">
            <div class="row"><div class="lbl"><span>İlk Hız (v₀)</span><strong id="vLbl">35 m/s</strong></div><input id="v0" type="range" min="10" max="60" value="35"></div>
            <div class="row"><div class="lbl"><span>Atış Açısı (θ)</span><strong id="aLbl">45°</strong></div><input id="ang" type="range" min="15" max="80" value="45"></div>
            <div class="row"><div class="lbl"><span>Yerçekimi (g)</span><strong id="gLbl">9.8 m/s²</strong></div><input id="grav" type="range" min="1.6" max="20" step="0.2" value="9.8"></div>
            <div class="btns">
              <button class="primary" id="playBtn">Başlat</button>
              <button class="ghost" id="resetBtn">Sıfırla</button>
            </div>
          </div>
        <script>
          const cv=document.getElementById('cv'), ctx=cv.getContext('2d');
          const v0El=document.getElementById('v0'), angEl=document.getElementById('ang'), gEl=document.getElementById('grav');
          let t=0, playing=true, trail=[];
          function resize(){
            const dpr=window.devicePixelRatio||1;
            cv.width=cv.clientWidth*dpr; cv.height=cv.clientHeight*dpr;
            ctx.setTransform(dpr,0,0,dpr,0,0);
          }
          window.addEventListener('resize',resize); resize();
          function params(){
            const v0=+v0El.value, a=+angEl.value*Math.PI/180, g=+gEl.value;
            const vx=v0*Math.cos(a), vy=v0*Math.sin(a);
            const tf=2*vy/g, hm=(vy*vy)/(2*g), r=vx*tf;
            document.getElementById('vLbl').textContent=v0+' m/s';
            document.getElementById('aLbl').textContent=angEl.value+'°';
            document.getElementById('gLbl').textContent=g.toFixed(1)+' m/s²';
            document.getElementById('hmax').textContent=hm.toFixed(1)+' m';
            document.getElementById('range').textContent=r.toFixed(1)+' m';
            document.getElementById('tflight').textContent=tf.toFixed(2)+' s';
            return {v0,a,g,vx,vy,tf,hm,r};
          }
          function reset(){ t=0; trail=[]; }
          [v0El,angEl,gEl].forEach(el=>el.addEventListener('input',reset));
          document.getElementById('resetBtn').onclick=()=>{ reset(); playing=true; document.getElementById('playBtn').textContent='Duraklat'; };
          document.getElementById('playBtn').onclick=()=>{ playing=!playing; document.getElementById('playBtn').textContent=playing?'Duraklat':'Başlat'; };
          document.getElementById('playBtn').textContent='Duraklat';
          let last=performance.now();
          function loop(now){
            const dt=Math.min(0.05,(now-last)/1000); last=now;
            const p=params();
            if(playing){ t+=dt; if(t>p.tf){ t=p.tf; } }
            const w=cv.clientWidth, h=cv.clientHeight, pad=26;
            const maxW=Math.max(120,p.r*1.12), maxH=Math.max(55,p.hm*1.32);
            const sx=(w-pad*2)/maxW, sy=(h-pad*2)/maxH;
            const toX=x=>pad+x*sx, toY=y=>h-pad-y*sy;
            ctx.clearRect(0,0,w,h);
            // Grid
            ctx.strokeStyle='#1E293B'; ctx.lineWidth=1;
            for(let i=1;i<=4;i++){ const gy=pad+(h-pad*2)*i/4; ctx.beginPath(); ctx.moveTo(pad,gy); ctx.lineTo(w-pad,gy); ctx.stroke(); }
            // Ground
            ctx.strokeStyle='#475569'; ctx.lineWidth=2; ctx.beginPath(); ctx.moveTo(pad,h-pad); ctx.lineTo(w-pad,h-pad); ctx.stroke();
            // Full ghost parabola
            ctx.strokeStyle='rgba(56,189,248,0.28)'; ctx.setLineDash([5,5]); ctx.beginPath();
            for(let i=0;i<=60;i++){ const ti=p.tf*i/60, px=p.vx*ti, py=Math.max(0,p.vy*ti-0.5*p.g*ti*ti); i===0?ctx.moveTo(toX(px),toY(py)):ctx.lineTo(toX(px),toY(py)); }
            ctx.stroke(); ctx.setLineDash([]);
            // Current ball
            const x=p.vx*t, y=Math.max(0,p.vy*t-0.5*p.g*t*t);
            const bx=toX(x), by=toY(y);
            if(playing && t<p.tf){ if(trail.length===0||Math.hypot(trail[trail.length-1].x-bx,trail[trail.length-1].y-by)>6) trail.push({x:bx,y:by}); }
            ctx.fillStyle='#38BDF8';
            trail.forEach(pt=>{ ctx.beginPath(); ctx.arc(pt.x,pt.y,2.4,0,Math.PI*2); ctx.fill(); });
            // Velocity vectors
            const vyNow=p.vy-p.g*t;
            ctx.strokeStyle='#22C55E'; ctx.lineWidth=2; ctx.beginPath(); ctx.moveTo(bx,by); ctx.lineTo(bx+p.vx*1.1,by); ctx.stroke();
            ctx.strokeStyle='#F97316'; ctx.lineWidth=2; ctx.beginPath(); ctx.moveTo(bx,by); ctx.lineTo(bx,by-vyNow*1.1); ctx.stroke();
            // Projectile sphere
            ctx.fillStyle='#F8FAFC'; ctx.beginPath(); ctx.arc(bx,by,7,0,Math.PI*2); ctx.fill();
            ctx.strokeStyle='#38BDF8'; ctx.lineWidth=2.5; ctx.stroke();
            // Legend
            ctx.font='11px system-ui'; ctx.fillStyle='#22C55E'; ctx.fillText('Vx: '+p.vx.toFixed(1)+' m/s',pad,16);
            ctx.fillStyle='#F97316'; ctx.fillText('Vy: '+vyNow.toFixed(1)+' m/s',pad+108,16);
            if(playing && t>=p.tf){ setTimeout(()=>{ if(t>=p.tf){ reset(); } },700); }
            requestAnimationFrame(loop);
          }
          requestAnimationFrame(loop);
        </script>
        </body>
        </html>
    """.trimIndent()

    private val PRESET_SYNAPSE_HTML = """
        <!DOCTYPE html>
        <html lang="tr">
        <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no">
        <title>Sinaps ve Aksiyon Potansiyeli</title>
        <style>
          *{box-sizing:border-box;margin:0;padding:0}
          body{background:#0B1220;color:#F8FAFC;font-family:system-ui,-apple-system,sans-serif;padding:12px;display:flex;flex-direction:column;gap:10px;min-height:100vh}
          .stats{display:grid;grid-template-columns:repeat(3,1fr);gap:8px}
          .card{background:#131E33;border:1px solid #243554;border-radius:12px;padding:8px 10px;text-align:center}
          .k{font-size:11px;color:#94A3B8}
          .v{font-size:14px;font-weight:700;color:#A78BFA;margin-top:2px}
          canvas{width:100%;height:260px;background:#0F172A;border:1px solid #243554;border-radius:14px;display:block}
          .controls{background:#131E33;border:1px solid #243554;border-radius:14px;padding:12px;display:flex;flex-direction:column;gap:10px}
          .row{display:flex;flex-direction:column;gap:4px}
          .lbl{display:flex;justify-content:space-between;font-size:12px;color:#CBD5E1}
          input[type=range]{width:100%;accent-color:#A78BFA}
          .btns{display:flex;gap:8px}
          button{flex:1;padding:10px;border-radius:10px;border:none;font-weight:700;font-size:13px;cursor:pointer}
          .primary{background:#A78BFA;color:#1E1B4B}
          .ghost{background:#1E293B;color:#E2E8F0;border:1px solid #334155}
        </style>
        </head>
        <body>
          <div class="stats">
            <div class="card"><div class="k">Zar Potansiyeli</div><div class="v" id="mv">-70 mV</div></div>
            <div class="card"><div class="k">İletim Hızı</div><div class="v" id="spd">120 m/s</div></div>
            <div class="card"><div class="k">Durum</div><div class="v" id="st">Dinlenme</div></div>
          </div>
          <canvas id="cv"></canvas>
          <div class="controls">
            <div class="row"><div class="lbl"><span>Uyarı Şiddeti (Eşik: -55 mV)</span><strong id="stimLbl">70%</strong></div><input id="stim" type="range" min="10" max="100" value="70"></div>
            <div class="row"><div class="lbl"><span>Miyelin Kılıf (Saltatorik İletim)</span><strong id="myeLbl">Var</strong></div><input id="mye" type="range" min="0" max="1" step="1" value="1"></div>
            <div class="btns">
              <button class="primary" id="fireBtn">Uyarı Gönder</button>
              <button class="ghost" id="pauseBtn">Duraklat</button>
            </div>
          </div>
        <script>
          const cv=document.getElementById('cv'), ctx=cv.getContext('2d');
          const stimEl=document.getElementById('stim'), myeEl=document.getElementById('mye');
          let pulse=-1, playing=true, ntParticles=[], curve=[];
          function resize(){
            const dpr=window.devicePixelRatio||1;
            cv.width=cv.clientWidth*dpr; cv.height=cv.clientHeight*dpr;
            ctx.setTransform(dpr,0,0,dpr,0,0);
          }
          window.addEventListener('resize',resize); resize();
          function fire(){
            const s=+stimEl.value;
            if(s<45){
              document.getElementById('st').textContent='Eşik Altı';
              document.getElementById('mv').textContent='-61 mV';
              return;
            }
            pulse=0; ntParticles=[];
          }
          document.getElementById('fireBtn').onclick=fire;
          document.getElementById('pauseBtn').onclick=()=>{ playing=!playing; document.getElementById('pauseBtn').textContent=playing?'Duraklat':'Devam'; };
          setTimeout(fire,300);
          let last=performance.now();
          function loop(now){
            const dt=Math.min(0.05,(now-last)/1000); last=now;
            const myelinated=+myeEl.value===1;
            document.getElementById('stimLbl').textContent=stimEl.value+'%';
            document.getElementById('myeLbl').textContent=myelinated?'Var (Hızlı)':'Yok (Yavaş)';
            document.getElementById('spd').textContent=myelinated?'120 m/s':'12 m/s';
            if(playing && pulse>=0){
              pulse += dt*(myelinated?0.75:0.28);
              if(pulse>=0.76 && ntParticles.length<12){
                for(let i=0;i<12;i++){ ntParticles.push({x:0, y:(Math.random()-0.5)*34, vx:45+Math.random()*35}); }
              }
              if(pulse>1.35){ pulse=0; ntParticles=[]; }
            }
            const w=cv.clientWidth, h=cv.clientHeight;
            ctx.clearRect(0,0,w,h);
            const axonY=h*0.38, axonStart=24, axonEnd=w*0.72, postX=w*0.84;
            // Axon tube
            ctx.strokeStyle='#334155'; ctx.lineWidth=14; ctx.lineCap='round';
            ctx.beginPath(); ctx.moveTo(axonStart,axonY); ctx.lineTo(axonEnd,axonY); ctx.stroke();
            // Myelin sheaths
            if(myelinated){
              const segW=(axonEnd-axonStart-24)/4;
              for(let i=0;i<4;i++){
                const sx=axonStart+8+i*segW;
                ctx.fillStyle='#1E3A5F'; ctx.strokeStyle='#38BDF8'; ctx.lineWidth=1.5;
                ctx.beginPath(); ctx.roundRect(sx,axonY-14,segW-10,28,8); ctx.fill(); ctx.stroke();
              }
            }
            // Presynaptic bulb & postsynaptic dendrite
            ctx.fillStyle='#1E293B'; ctx.strokeStyle='#64748B'; ctx.lineWidth=2;
            ctx.beginPath(); ctx.arc(axonEnd+8,axonY,22,0,Math.PI*2); ctx.fill(); ctx.stroke();
            ctx.beginPath(); ctx.roundRect(postX,axonY-30,w-postX-16,60,14); ctx.fill(); ctx.stroke();
            ctx.font='10px system-ui'; ctx.fillStyle='#94A3B8';
            ctx.fillText('Akson',axonStart,axonY-22);
            ctx.fillText('Sinaps',axonEnd+26,axonY-26);
            // Depolarization Na+ wave
            let voltage=-70;
            if(pulse>=0 && pulse<=0.82){
              const px=axonStart+(axonEnd-axonStart)*(pulse/0.82);
              const grad=ctx.createRadialGradient(px,axonY,2,px,axonY,28);
              grad.addColorStop(0,'rgba(250,204,21,0.95)');
              grad.addColorStop(1,'rgba(250,204,21,0)');
              ctx.fillStyle=grad; ctx.beginPath(); ctx.arc(px,axonY,28,0,Math.PI*2); ctx.fill();
              ctx.fillStyle='#FEF08A'; ctx.font='bold 10px system-ui'; ctx.fillText('Na⁺',px-9,axonY+4);
              voltage = Math.round(-70 + 110*Math.sin(Math.min(1,pulse/0.82)*Math.PI));
              document.getElementById('st').textContent='Depolarizasyon';
            } else if(pulse>0.82){
              voltage = -80;
              document.getElementById('st').textContent='Sinaptik İletim';
            }
            document.getElementById('mv').textContent=(voltage>0?'+':'')+voltage+' mV';
            // Neurotransmitters crossing cleft
            ctx.fillStyle='#34D399';
            ntParticles.forEach(pt=>{
              if(playing) pt.x=Math.min(postX-axonEnd-28, pt.x+pt.vx*dt);
              ctx.beginPath(); ctx.arc(axonEnd+26+pt.x, axonY+pt.y, 3.2, 0, Math.PI*2); ctx.fill();
            });
            // Oscilloscope curve at bottom
            if(playing){ curve.push(voltage); if(curve.length>140) curve.shift(); }
            const oy=h*0.80, oh=42;
            ctx.strokeStyle='#1E293B'; ctx.lineWidth=1; ctx.beginPath(); ctx.moveTo(20,oy); ctx.lineTo(w-20,oy); ctx.stroke();
            ctx.strokeStyle='#A78BFA'; ctx.lineWidth=2; ctx.beginPath();
            curve.forEach((v,i)=>{
              const cx=20+(w-40)*(i/140), cy=oy-((v+70)/110)*oh;
              i===0?ctx.moveTo(cx,cy):ctx.lineTo(cx,cy);
            });
            ctx.stroke();
            ctx.fillStyle='#94A3B8'; ctx.font='10px system-ui'; ctx.fillText('Aksiyon Potansiyeli Eğrisi (-70 mV → +40 mV)',20,h-10);
            requestAnimationFrame(loop);
          }
          requestAnimationFrame(loop);
        </script>
        </body>
        </html>
    """.trimIndent()

    private val PRESET_TITRATION_HTML = """
        <!DOCTYPE html>
        <html lang="tr">
        <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no">
        <title>Asit-Baz Titrasyonu</title>
        <style>
          *{box-sizing:border-box;margin:0;padding:0}
          body{background:#0B1220;color:#F8FAFC;font-family:system-ui,-apple-system,sans-serif;padding:12px;display:flex;flex-direction:column;gap:10px;min-height:100vh}
          .stats{display:grid;grid-template-columns:repeat(3,1fr);gap:8px}
          .card{background:#131E33;border:1px solid #243554;border-radius:12px;padding:8px 10px;text-align:center}
          .k{font-size:11px;color:#94A3B8}
          .v{font-size:15px;font-weight:700;color:#F472B6;margin-top:2px}
          canvas{width:100%;height:260px;background:#0F172A;border:1px solid #243554;border-radius:14px;display:block}
          .controls{background:#131E33;border:1px solid #243554;border-radius:14px;padding:12px;display:flex;flex-direction:column;gap:10px}
          .row{display:flex;flex-direction:column;gap:4px}
          .lbl{display:flex;justify-content:space-between;font-size:12px;color:#CBD5E1}
          input[type=range]{width:100%;accent-color:#F472B6}
          .btns{display:flex;gap:8px}
          button{flex:1;padding:10px;border-radius:10px;border:none;font-weight:700;font-size:13px;cursor:pointer}
          .primary{background:#F472B6;color:#4A044E}
          .ghost{background:#1E293B;color:#E2E8F0;border:1px solid #334155}
        </style>
        </head>
        <body>
          <div class="stats">
            <div class="card"><div class="k">Anlık pH</div><div class="v" id="phVal">1.00</div></div>
            <div class="card"><div class="k">Eklenen NaOH</div><div class="v" id="vbVal">0.0 mL</div></div>
            <div class="card"><div class="k">Ortam</div><div class="v" id="envVal">Asidik</div></div>
          </div>
          <canvas id="cv"></canvas>
          <div class="controls">
            <div class="row"><div class="lbl"><span>Eklenen Baz Hacmi (0.10 M NaOH)</span><strong id="vLbl">0.0 mL</strong></div><input id="vb" type="range" min="0" max="50" step="0.5" value="0"></div>
            <div class="row"><div class="lbl"><span>Başlangıç HCl Derişimi (25 mL)</span><strong id="mLbl">0.10 M</strong></div><input id="ma" type="range" min="0.05" max="0.18" step="0.01" value="0.10"></div>
            <div class="btns">
              <button class="primary" id="autoBtn">Otomatik Damlat</button>
              <button class="ghost" id="resetBtn">Sıfırla</button>
            </div>
          </div>
        <script>
          const cv=document.getElementById('cv'), ctx=cv.getContext('2d');
          const vbEl=document.getElementById('vb'), maEl=document.getElementById('ma');
          let autoPlay=false, dropY=0;
          function resize(){
            const dpr=window.devicePixelRatio||1;
            cv.width=cv.clientWidth*dpr; cv.height=cv.clientHeight*dpr;
            ctx.setTransform(dpr,0,0,dpr,0,0);
          }
          window.addEventListener('resize',resize); resize();
          function calcPh(vb, ma){
            const va=25, mb=0.10, na=va*ma, nb=vb*mb, tot=va+vb;
            if(Math.abs(na-nb)<1e-6) return 7.0;
            if(na>nb){ const h=(na-nb)/tot; return Math.max(0.5,-Math.log10(h)); }
            const oh=(nb-na)/tot; return Math.min(13.5,14+Math.log10(oh));
          }
          function indicatorColor(ph){
            if(ph<6.4) return 'rgba(239,68,68,0.58)';
            if(ph<7.8) return 'rgba(16,185,129,0.62)';
            if(ph<8.6) return 'rgba(244,114,182,0.55)';
            return 'rgba(219,39,119,0.82)';
          }
          document.getElementById('autoBtn').onclick=()=>{ autoPlay=!autoPlay; document.getElementById('autoBtn').textContent=autoPlay?'Duraklat':'Otomatik Damlat'; };
          document.getElementById('resetBtn').onclick=()=>{ autoPlay=false; vbEl.value=0; document.getElementById('autoBtn').textContent='Otomatik Damlat'; };
          let last=performance.now();
          function loop(now){
            const dt=Math.min(0.05,(now-last)/1000); last=now;
            if(autoPlay){
              let v=+vbEl.value+dt*4.5;
              if(v>=50){ v=50; autoPlay=false; document.getElementById('autoBtn').textContent='Otomatik Damlat'; }
              vbEl.value=v.toFixed(1);
              dropY=(dropY+dt*120)%48;
            }
            const vb=+vbEl.value, ma=+maEl.value, ph=calcPh(vb,ma);
            document.getElementById('vLbl').textContent=vb.toFixed(1)+' mL';
            document.getElementById('mLbl').textContent=ma.toFixed(2)+' M';
            document.getElementById('phVal').textContent=ph.toFixed(2);
            document.getElementById('vbVal').textContent=vb.toFixed(1)+' mL';
            document.getElementById('envVal').textContent=ph<6.8?'Asidik':(ph<=7.2?'Nötr (Eşdeğer)':'Bazik');
            const w=cv.clientWidth, h=cv.clientHeight;
            ctx.clearRect(0,0,w,h);
            // Left: Erlenmeyer Flask + Burette
            const fx=w*0.22, fy=h*0.80;
            ctx.strokeStyle='#64748B'; ctx.lineWidth=2;
            ctx.strokeRect(fx-8,18,16,74); // burette
            ctx.fillStyle='rgba(56,189,248,0.45)'; ctx.fillRect(fx-6,18+(vb/50)*66,12,Math.max(4,70-(vb/50)*66));
            if(autoPlay){ ctx.fillStyle='#38BDF8'; ctx.beginPath(); ctx.arc(fx,96+dropY,3,0,Math.PI*2); ctx.fill(); }
            // Flask liquid
            ctx.fillStyle=indicatorColor(ph);
            ctx.beginPath(); ctx.moveTo(fx-34,fy); ctx.lineTo(fx+34,fy); ctx.lineTo(fx+22,fy-38); ctx.lineTo(fx-22,fy-38); ctx.closePath(); ctx.fill();
            // Flask glass outline
            ctx.strokeStyle='#CBD5E1'; ctx.lineWidth=2;
            ctx.beginPath(); ctx.moveTo(fx-10,fy-74); ctx.lineTo(fx-10,fy-54); ctx.lineTo(fx-38,fy+2); ctx.lineTo(fx+38,fy+2); ctx.lineTo(fx+10,fy-54); ctx.lineTo(fx+10,fy-74); ctx.stroke();
            ctx.fillStyle='#94A3B8'; ctx.font='10px system-ui'; ctx.fillText('Erlen (25 mL HCl)',fx-42,h-12);
            // Right: Live pH Titration Curve
            const gx=w*0.46, gy=24, gw=w*0.48, gh=h-56;
            ctx.strokeStyle='#334155'; ctx.lineWidth=1.5;
            ctx.beginPath(); ctx.moveTo(gx,gy); ctx.lineTo(gx,gy+gh); ctx.lineTo(gx+gw,gy+gh); ctx.stroke();
            // pH 7 dashed line
            const y7=gy+gh-(7/14)*gh;
            ctx.strokeStyle='rgba(148,163,184,0.35)'; ctx.setLineDash([4,4]);
            ctx.beginPath(); ctx.moveTo(gx,y7); ctx.lineTo(gx+gw,y7); ctx.stroke(); ctx.setLineDash([]);
            ctx.fillStyle='#94A3B8'; ctx.font='10px system-ui'; ctx.fillText('pH 7',gx+4,y7-4);
            // Curve
            ctx.strokeStyle='#F472B6'; ctx.lineWidth=2.5; ctx.beginPath();
            for(let v=0;v<=50;v+=0.5){
              const cp=calcPh(v,ma), cx=gx+(v/50)*gw, cy=gy+gh-(cp/14)*gh;
              v===0?ctx.moveTo(cx,cy):ctx.lineTo(cx,cy);
            }
            ctx.stroke();
            // Current point
            const curX=gx+(vb/50)*gw, curY=gy+gh-(ph/14)*gh;
            ctx.fillStyle='#F8FAFC'; ctx.beginPath(); ctx.arc(curX,curY,5.5,0,Math.PI*2); ctx.fill();
            ctx.strokeStyle='#F472B6'; ctx.lineWidth=2.5; ctx.stroke();
            ctx.fillStyle='#94A3B8'; ctx.fillText('Baz Hacmi (mL)',gx+gw*0.32,h-12);
            requestAnimationFrame(loop);
          }
          requestAnimationFrame(loop);
        </script>
        </body>
        </html>
    """.trimIndent()
}
