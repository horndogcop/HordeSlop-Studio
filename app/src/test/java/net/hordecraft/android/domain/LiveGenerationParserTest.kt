package net.hordeslop.studio.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveGenerationParserTest {
    @Test
    fun parsesFullRequestParametersAndCheckSnapshot() {
        val raw = """
            {
              "prompt": "sunlit forest cottage ### ugly, blurry, lowres",
              "models": ["Juggernaut XL", "AlbedoBase XL"],
              "nsfw": true,
              "censor_nsfw": false,
              "shared": true,
              "r2": true,
              "trusted_workers": false,
              "slow_workers": true,
              "source_processing": "txt2img",
              "params": {
                "sampler_name": "k_dpmpp_2m",
                "scheduler": "karras",
                "cfg_scale": 6.5,
                "steps": 32,
                "clip_skip": 2,
                "width": 832,
                "height": 1216,
                "seed": "987654321",
                "n": 4,
                "hires_fix": true,
                "loras": [
                  { "name": "12345", "model": 0.8, "clip": 0.7 }
                ],
                "tis": [
                  { "name": "67890", "strength": 1.0, "inject_ti": "negprompt" }
                ]
              }
            }
        """.trimIndent()
        val obj = Json.parseToJsonElement(raw).jsonObject
        val check = GenerationStatusSnapshot(
            finished = 1,
            processing = 2,
            waiting = 1,
            done = false,
            faulted = false,
            isPossible = true,
            waitTimeSeconds = 18,
            queuePosition = 3,
            kudos = 14.5,
        )

        val parsed = LiveGenerationParser.parse(
            requestId = "req-abc-123",
            requestJson = obj,
            check = check,
            isTrackedLocally = true,
        )

        assertEquals("req-abc-123", parsed.requestId)
        assertEquals("sunlit forest cottage", parsed.prompt)
        assertEquals("ugly, blurry, lowres", parsed.negativePrompt)
        assertFalse(parsed.promptOmittedLegacy)
        assertEquals(listOf("Juggernaut XL", "AlbedoBase XL"), parsed.models)
        assertEquals(32, parsed.steps)
        assertEquals(6.5f, parsed.cfg)
        assertEquals(2, parsed.clipSkip)
        assertEquals("k_dpmpp_2m", parsed.sampler)
        assertEquals("karras", parsed.scheduler)
        assertEquals(832, parsed.width)
        assertEquals(1216, parsed.height)
        assertEquals("987654321", parsed.seed)
        assertEquals(4, parsed.imageCount)
        assertEquals(true, parsed.hiresFix)
        assertEquals(true, parsed.nsfw)
        assertEquals(false, parsed.censorNsfw)
        assertEquals(true, parsed.shared)
        assertEquals(true, parsed.r2)
        assertEquals(false, parsed.trustedWorkers)
        assertEquals(true, parsed.slowWorkers)
        assertEquals("txt2img", parsed.sourceProcessing)
        assertEquals(listOf("12345 (0.8)"), parsed.loraNames)
        assertEquals(listOf("67890 (1.0)"), parsed.tiNames)
        assertEquals(1, parsed.finished)
        assertEquals(2, parsed.processing)
        assertEquals(1, parsed.waiting)
        assertEquals(3, parsed.queuePosition)
        assertEquals(18, parsed.waitTimeSeconds)
        assertTrue(parsed.isTrackedLocally)
        assertEquals("Processing (1/4)", LiveGenerationParser.summarizeStatus(parsed))
        assertTrue(LiveGenerationParser.summarizeParams(parsed).contains("832x1216"))
        assertTrue(LiveGenerationParser.summarizeParams(parsed).contains("NSFW"))
    }

    @Test
    fun detectsLegacyOmittedPromptAndKarrasFallback() {
        val raw = """
            {
              "models": ["Deliberate"],
              "params": {
                "karras": true,
                "steps": 25,
                "width": 512,
                "height": 768
              }
            }
        """.trimIndent()
        val obj = Json.parseToJsonElement(raw).jsonObject
        val check = GenerationStatusSnapshot(
            waiting = 2,
            queuePosition = 7,
            waitTimeSeconds = 42,
        )

        val parsed = LiveGenerationParser.parse(
            requestId = "req-legacy",
            requestJson = obj,
            check = check,
        )

        assertTrue(parsed.promptOmittedLegacy)
        assertEquals("", parsed.prompt)
        assertEquals("", parsed.negativePrompt)
        assertEquals("karras", parsed.scheduler)
        assertEquals(2, parsed.imageCount)
        assertEquals("Queued #7 · ~42s", LiveGenerationParser.summarizeStatus(parsed))
    }

    @Test
    fun handlesNullRequestJsonWithErrorGracefully() {
        val check = GenerationStatusSnapshot(
            finished = 0,
            processing = 0,
            waiting = 1,
            isPossible = false,
            waitTimeSeconds = 0,
        )

        val parsed = LiveGenerationParser.parse(
            requestId = "req-err",
            requestJson = null,
            check = check,
            requestLookupError = "HTTP 404 submission error",
        )

        assertFalse(parsed.promptOmittedLegacy)
        assertTrue(parsed.models.isEmpty())
        assertNull(parsed.steps)
        assertEquals("{}", parsed.rawRequestJson)
        assertEquals("HTTP 404 submission error", parsed.requestLookupError)
        assertEquals("No eligible workers", LiveGenerationParser.summarizeStatus(parsed))
        assertEquals("Parameters unavailable", LiveGenerationParser.summarizeParams(parsed))
    }
}
