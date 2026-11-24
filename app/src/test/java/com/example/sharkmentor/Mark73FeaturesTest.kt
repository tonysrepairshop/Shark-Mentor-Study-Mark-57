package com.example.sharkmentor

import com.example.sharkmentor.core.ai.SimulationStudioEngine
import com.example.sharkmentor.core.audio.MusicPlaybackState
import com.example.sharkmentor.core.audio.MusicQueueItem
import com.example.sharkmentor.core.service.CampVideoPlaybackState
import com.example.sharkmentor.data.topbar.TopBarModuleConfig
import com.example.sharkmentor.data.topbar.TopBarModuleId
import com.example.sharkmentor.data.topbar.TopBarModuleSize
import com.example.sharkmentor.data.topbar.TopBarSettings
import com.example.sharkmentor.data.topbar.defaultTopBarModules
import com.example.sharkmentor.data.topbar.moveTopBarModule
import com.example.sharkmentor.data.topbar.parseTopBarModulesJson
import com.example.sharkmentor.data.topbar.resizeTopBarModule
import com.example.sharkmentor.data.topbar.serializeTopBarModulesJson
import com.example.sharkmentor.data.topbar.toggleTopBarModule
import com.example.sharkmentor.ui.aichat.SharkSlashCommand
import com.example.sharkmentor.ui.aichat.SharkSlashCommandRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Mark73FeaturesTest {

    @Test
    fun topBarModulesSupportReorderToggleResizeAndJsonRoundTrip() {
        val initial = defaultTopBarModules()
        assertEquals(5, initial.size)
        assertEquals(TopBarModuleId.WEATHER, initial[0].id)
        assertEquals(TopBarModuleId.MUSIC, initial[2].id)

        // Move MUSIC to index 0, disable COUNTDOWN, resize WEATHER to LARGE
        val moved = moveTopBarModule(initial, fromIndex = 2, toIndex = 0)
        assertEquals(TopBarModuleId.MUSIC, moved[0].id)

        val toggled = toggleTopBarModule(moved, TopBarModuleId.COUNTDOWN, enabled = false)
        assertFalse(TopBarSettings(modules = toggled).isModuleEnabled(TopBarModuleId.COUNTDOWN))
        assertTrue(TopBarSettings(modules = toggled).isModuleEnabled(TopBarModuleId.MUSIC))

        val resized = resizeTopBarModule(toggled, TopBarModuleId.WEATHER, TopBarModuleSize.LARGE)
        assertEquals(TopBarModuleSize.LARGE, resized.first { it.id == TopBarModuleId.WEATHER }.size)

        // Round-trip JSON persistence via Gson
        val json = serializeTopBarModulesJson(resized)
        val restored = parseTopBarModulesJson(json)
        assertEquals(resized, restored)

        // Forward compatibility: partial older JSON appends any missing modules cleanly
        val partialJson = """[{"id":"text_bar","enabled":true,"size":"SMALL"}]"""
        val upgraded = parseTopBarModulesJson(partialJson)
        assertEquals(5, upgraded.size)
        assertEquals(TopBarModuleId.TEXT_BAR, upgraded[0].id)
        assertEquals(TopBarModuleSize.SMALL, upgraded[0].size)
        assertTrue(upgraded.any { it.id == TopBarModuleId.MUSIC })
    }

    @Test
    fun musicPlaybackStateCarriesDynamicQueueItemsCleanly() {
        val state = MusicPlaybackState(
            hasActiveSession = true,
            isPlaying = true,
            title = "Current Track",
            queueItems = listOf(
                MusicQueueItem(queueId = 101L, title = "Next Track A", subtitle = "Artist A"),
                MusicQueueItem(queueId = 102L, title = "Next Track B", subtitle = "Artist B")
            )
        )
        assertEquals(2, state.queueItems.size)
        assertEquals(101L, state.queueItems[0].queueId)
        assertEquals("Next Track B", state.queueItems[1].title)

        val emptyQueueState = MusicPlaybackState(hasActiveSession = true, isPlaying = true)
        assertTrue(emptyQueueState.queueItems.isEmpty())
    }

    @Test
    fun simulationSanitizerStripsExternalCdnsRejectsWebglOnlyAndInjectsErrorBridge() {
        val rawAiOutput = """
            ```html
            <!DOCTYPE html>
            <html>
            <head>
              <title>Test Dalga</title>
              <script src="https://cdn.example.com/heavy.js"></script>
              <link rel="stylesheet" href="https://cdn.example.com/style.css" />
            </head>
            <body>
              <canvas id="c" width="300" height="200"></canvas>
              <script>
                const ctx = document.getElementById('c').getContext('2d');
                ctx.fillStyle = '#38BDF8';
                ctx.fillRect(0, 0, 100, 100);
              </script>
            </body>
            </html>
            ```
        """.trimIndent()

        val safe = SimulationStudioEngine.extractSafeSimulationHtml(rawAiOutput)
        assertNotNull("Valid 2D canvas HTML must pass sanitization", safe)
        assertFalse("External CDN script tags must be stripped", safe!!.contains("cdn.example.com/heavy.js"))
        assertFalse("External CDN stylesheet links must be stripped", safe.contains("cdn.example.com/style.css"))
        assertTrue("JS error reporting bridge must be injected", safe.contains("SharkSimBridge"))
        assertTrue("2D canvas context must remain intact", safe.contains("getContext('2d')"))

        val webglOnly = """
            <!DOCTYPE html>
            <html><body><canvas id="gl"></canvas><script>
            const gl = document.getElementById('gl').getContext('webgl2');
            gl.clearColor(0,0,0,1); gl.clear(gl.COLOR_BUFFER_BIT);
            </script></body></html>
        """.trimIndent()
        assertNull("Heavy WebGL-only payload without 2D canvas must be rejected", SimulationStudioEngine.extractSafeSimulationHtml(webglOnly))

        // Built-in offline presets must be non-empty, valid HTML5 2D Canvas, and 100% offline
        val presets = SimulationStudioEngine.builtInPresets()
        assertEquals(3, presets.size)
        presets.forEach { preset ->
            assertTrue("Preset ${preset.id} must have <canvas>", preset.htmlCode.contains("<canvas"))
            assertTrue("Preset ${preset.id} must use 2D context", preset.htmlCode.contains("getContext('2d')"))
            assertFalse("Preset ${preset.id} must have zero external http links", preset.htmlCode.contains("http://") || preset.htmlCode.contains("https://"))
        }

        // Chat marker extraction strips raw #SIM_OPEN tag cleanly
        val msg = "Simülasyon hazır.\n${SimulationStudioEngine.SIMULATION_OPEN_TAG}sim_12345"
        val (cleanText, simId) = SimulationStudioEngine.extractSimulationOpenTag(msg)
        assertEquals("Simülasyon hazır.", cleanText)
        assertEquals("sim_12345", simId)
    }

    @Test
    fun simulationSlashCommandParsesAliasesAndStaysUnlocked() {
        val suggestions = SharkSlashCommandRegistry.filterSuggestions("/sim")
        assertEquals(listOf(SharkSlashCommand.SIMULASYON), suggestions)

        val parsedFull = SharkSlashCommandRegistry.parseLeadingCommand("/simulasyon DNA replikasyonu")
        assertNotNull(parsedFull)
        assertEquals(SharkSlashCommand.SIMULASYON, parsedFull!!.command)
        assertEquals("DNA replikasyonu", parsedFull.argumentText)

        val parsedAlias = SharkSlashCommandRegistry.parseLeadingCommand("/sim Doppler etkisi")
        assertNotNull(parsedAlias)
        assertEquals(SharkSlashCommand.SIMULASYON, parsedAlias!!.command)
        assertEquals("Doppler etkisi", parsedAlias.argumentText)

        val (locked, _) = SharkSlashCommandRegistry.isCommandLocked(
            cmd = SharkSlashCommand.SIMULASYON,
            isDopamineActive = false,
            isInstitutional = true
        )
        assertFalse("/simulasyon must remain unlocked for all students", locked)
    }

    @Test
    fun campVideoPlaybackStateStartsInactiveToPreventEagerServiceLaunchLag() {
        val state = CampVideoPlaybackState()
        assertFalse(state.isActive)
        assertFalse(state.isPlaying)
        assertEquals(0, state.positionSec)
        assertEquals(0, state.durationSec)
    }
}
