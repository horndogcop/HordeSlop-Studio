package net.hordeslop.studio.domain

import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GenerationUrlParserTest {

    @Test
    fun parsesAiHordeQueryUrl() {
        val result = GenerationUrlParser.parseString(
            "https://aihorde.net/?prompt=retro%20robot%20painting&model=Deliberate&steps=25&cfg_scale=7.5&sampler_name=k_euler&seed=55555&size=512x768&clip_skip=2",
        )
        assertNotNull(result)
        result!!
        assertTrue(result.hasMetadata)
        assertEquals("AI Horde", result.metadata.generatorSource)
        assertEquals("retro robot painting", result.metadata.prompt)
        assertEquals("Deliberate", result.metadata.model)
        assertEquals(25, result.metadata.steps)
        assertEquals(7.5f, result.metadata.cfg ?: 0f, 0.01f)
        assertEquals("k_euler", result.metadata.sampler)
        assertEquals("55555", result.metadata.seed)
        assertEquals(512, result.metadata.width)
        assertEquals(768, result.metadata.height)
        assertEquals(2, result.metadata.clipSkip)
    }

    @Test
    fun parsesArtBotUrlWithHashNegativeAndKarras() {
        val result = GenerationUrlParser.parseString(
            "https://tinybots.net/artbot?prompt=neon%20city%20skyline###blurry,%20lowres&model=ICBINP&steps=30&cfg_scale=6.0&sampler=k_dpmpp_2m&karras=true",
        )
        assertNotNull(result)
        result!!
        assertTrue(result.hasMetadata)
        assertEquals("ArtBot", result.metadata.generatorSource)
        assertEquals("neon city skyline", result.metadata.prompt)
        assertEquals("blurry, lowres", result.metadata.negativePrompt)
        assertEquals("ICBINP", result.metadata.model)
        assertEquals(30, result.metadata.steps)
        assertEquals(6.0f, result.metadata.cfg ?: 0f, 0.01f)
        assertEquals("k_dpmpp_2m", result.metadata.sampler)
        assertEquals("karras", result.metadata.scheduler)
    }

    @Test
    fun parsesStableUiUrlsAndBase64Payloads() {
        val payload = """
            {
              "prompt": "alpine cabin at dawn ### fog, low quality",
              "models": ["Juggernaut XL"],
              "params": {
                "steps": 28,
                "cfg_scale": 5.5,
                "sampler_name": "k_dpmpp_2m",
                "karras": true,
                "seed": "987654",
                "width": 1024,
                "height": 1024,
                "clip_skip": 2,
                "loras": [{"name": "detail_tweaker", "model": 0.65}]
              }
            }
        """.trimIndent()
        val encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.toByteArray(Charsets.UTF_8))

        val stableUiHosted = GenerationUrlParser.parseString(
            "https://aqualxx.github.io/stable-ui/?request=$encoded",
        )
        assertNotNull(stableUiHosted)
        stableUiHosted!!
        assertTrue(stableUiHosted.hasMetadata)
        assertEquals("StableUI", stableUiHosted.metadata.generatorSource)
        assertEquals("alpine cabin at dawn", stableUiHosted.metadata.prompt)
        assertEquals("fog, low quality", stableUiHosted.metadata.negativePrompt)
        assertEquals("Juggernaut XL", stableUiHosted.metadata.model)
        assertEquals(28, stableUiHosted.metadata.steps)
        assertEquals(5.5f, stableUiHosted.metadata.cfg ?: 0f, 0.01f)
        assertEquals("k_dpmpp_2m", stableUiHosted.metadata.sampler)
        assertEquals("karras", stableUiHosted.metadata.scheduler)
        assertEquals("987654", stableUiHosted.metadata.seed)
        assertEquals(1024, stableUiHosted.metadata.width)
        assertEquals(1024, stableUiHosted.metadata.height)
        assertEquals(2, stableUiHosted.metadata.clipSkip)
        assertEquals(listOf("detail_tweaker (0.65)"), stableUiHosted.metadata.loras)

        val stableUiOrg = GenerationUrlParser.parseString(
            "https://stableui.org/?prompt=cozy%20library&steps=20&width=768&height=512",
        )
        assertNotNull(stableUiOrg)
        assertTrue(stableUiOrg!!.hasMetadata)
        assertEquals("StableUI", stableUiOrg.metadata.generatorSource)
        assertEquals("cozy library", stableUiOrg.metadata.prompt)
    }

    @Test
    fun nonMetadataLinksReportHasMetadataFalseAndFragmentParamsParse() {
        val news = GenerationUrlParser.parseString("https://aihorde.net/news")
        assertNotNull(news)
        assertFalse(news!!.hasMetadata)
        assertEquals("", news.metadata.prompt)

        val workers = GenerationUrlParser.parseString("https://aqualxx.github.io/stable-ui/?tab=workers")
        assertNotNull(workers)
        assertFalse(workers!!.hasMetadata)

        val fragment = GenerationUrlParser.parseString("https://aihorde.net/#create?prompt=space%20explorer&model=SDXL_base")
        assertNotNull(fragment)
        assertTrue(fragment!!.hasMetadata)
        assertEquals("space explorer", fragment.metadata.prompt)
        assertEquals("SDXL_base", fragment.metadata.model)
    }

    @Test
    fun extractsUrlFromSharedTextAndRejectsPlainPrompts() {
        val extracted = GenerationUrlParser.parseString("Look at this: https://aihorde.net/?prompt=golden%20cat&steps=22")
        assertNotNull(extracted)
        assertTrue(extracted!!.hasMetadata)
        assertEquals("golden cat", extracted.metadata.prompt)
        assertEquals(22, extracted.metadata.steps)

        assertNull(GenerationUrlParser.parseString("just a regular prompt ### blurry"))
        assertNull(GenerationUrlParser.parseString(""))
        assertNull(GenerationUrlParser.parseString("ftp://aihorde.net/?prompt=golden%20cat"))
    }

    @Test
    fun parsesPaddedAndUnpaddedBase64JsonPayloads() {
        val jsonPayload = """{"prompt":"cyberpunk alley","params":{"steps":35,"cfg_scale":7.0}}"""
        val rawBytes = jsonPayload.toByteArray(Charsets.UTF_8)

        // Padded standard Base64
        val padded = Base64.getEncoder().encodeToString(rawBytes)
        val resPadded = GenerationUrlParser.parseString("https://aihorde.net/?share=$padded")
        assertNotNull(resPadded)
        assertTrue(resPadded!!.hasMetadata)
        assertEquals("cyberpunk alley", resPadded.metadata.prompt)
        assertEquals(35, resPadded.metadata.steps)

        // Unpadded URL-safe Base64
        val unpadded = Base64.getUrlEncoder().withoutPadding().encodeToString(rawBytes)
        val resUnpadded = GenerationUrlParser.parseString("https://aihorde.net/?share=$unpadded")
        assertNotNull(resUnpadded)
        assertTrue(resUnpadded!!.hasMetadata)
        assertEquals("cyberpunk alley", resUnpadded.metadata.prompt)
        assertEquals(35, resUnpadded.metadata.steps)
    }

    @Test
    fun parsesUrlEncodedJsonWithNestedPercentSequences() {
        // Contains % character inside prompt: "oil painting 100% detailed"
        val directUrlEncoded = "https://aihorde.net/?share=%7B%22prompt%22%3A%22oil%20painting%20100%25%20detailed%22%2C%22model%22%3A%22Deliberate%22%7D"
        val resDirect = GenerationUrlParser.parseString(directUrlEncoded)
        assertNotNull(resDirect)
        assertTrue(resDirect!!.hasMetadata)
        assertEquals("oil painting 100% detailed", resDirect.metadata.prompt)
        assertEquals("Deliberate", resDirect.metadata.model)

        // Double encoded: %257B = {
        val doubleEncoded = "https://aihorde.net/?share=%257B%2522prompt%2522%253A%2522double%2520encoded%2522%257D"
        val resDouble = GenerationUrlParser.parseString(doubleEncoded)
        assertNotNull(resDouble)
        assertTrue(resDouble!!.hasMetadata)
        assertEquals("double encoded", resDouble.metadata.prompt)
    }

    @Test
    fun handlesMalformedInputsGracefullyWithoutThrowing() {
        // Incomplete percent sequence
        val badPercent = GenerationUrlParser.parseString("https://aihorde.net/?prompt=hello%2")
        assertNotNull(badPercent)
        assertTrue(badPercent!!.hasMetadata)
        assertEquals("hello%2", badPercent.metadata.prompt)

        // Malformed base64 (length % 4 == 1)
        val badLength = GenerationUrlParser.parseString("https://aihorde.net/?share=abcde")
        assertNotNull(badLength)
        assertFalse(badLength!!.hasMetadata)

        // Base64 of non-JSON plain text: should not treat as metadata
        val plainTextBase64 = Base64.getEncoder().encodeToString("just some random text".toByteArray())
        val resPlain = GenerationUrlParser.parseString("https://aihorde.net/?share=$plainTextBase64")
        assertNotNull(resPlain)
        assertFalse(resPlain!!.hasMetadata)

        // Malformed JSON inside valid Base64
        val malformedJsonBase64 = Base64.getEncoder().encodeToString("{not valid json".toByteArray())
        val resMalformed = GenerationUrlParser.parseString("https://aihorde.net/?share=$malformedJsonBase64")
        assertNotNull(resMalformed)
        assertFalse(resMalformed!!.hasMetadata)
    }

    @Test
    fun normalNonMetadataPagesOnSupportedHostsReportHasMetadataFalseForBrowserFallback() {
        val pages = listOf(
            "https://aihorde.net/faq",
            "https://aihorde.net/news",
            "https://tinybots.net/artbot/about",
            "https://tinybots.net/artbot/privacy",
            "https://stableui.org/docs",
            "https://aqualxx.github.io/stable-ui/workers",
        )
        for (page in pages) {
            val result = GenerationUrlParser.parseString(page)
            assertNotNull("Failed for $page", result)
            assertFalse("Expected hasMetadata=false for $page", result!!.hasMetadata)
        }
    }

    @Test
    fun parsesVariousFragmentFormatsConsistently() {
        // Bare fragment with parameters
        val bare = GenerationUrlParser.parseString("https://aihorde.net/#prompt=mystic%20forest&steps=30&model=Deliberate")
        assertNotNull(bare)
        assertTrue(bare!!.hasMetadata)
        assertEquals("mystic forest", bare.metadata.prompt)
        assertEquals(30, bare.metadata.steps)
        assertEquals("Deliberate", bare.metadata.model)

        // Chained hash fragment
        val chained = GenerationUrlParser.parseString("https://tinybots.net/artbot/#create#prompt=steampunk%20airship&steps=20")
        assertNotNull(chained)
        assertTrue(chained!!.hasMetadata)
        assertEquals("steampunk airship", chained.metadata.prompt)
        assertEquals(20, chained.metadata.steps)

        // Percent-encoded query in fragment
        val encodedFragment = GenerationUrlParser.parseString("https://aihorde.net/#%70%72%6F%6D%70%74%3D%65%6E%63%6F%64%65%64%20%76%61%6C%75%65")
        assertNotNull(encodedFragment)
        assertTrue(encodedFragment!!.hasMetadata)
        assertEquals("encoded value", encodedFragment.metadata.prompt)
    }

    private fun createPackedStudioLink(jsonPayload: String): String {
        val deflater = java.util.zip.Deflater(java.util.zip.Deflater.DEFAULT_COMPRESSION, true)
        val input = jsonPayload.toByteArray(Charsets.UTF_8)
        deflater.setInput(input)
        deflater.finish()
        val buffer = ByteArray(1024)
        val baos = java.io.ByteArrayOutputStream()
        while (!deflater.finished()) {
            val count = deflater.deflate(buffer)
            baos.write(buffer, 0, count)
        }
        deflater.end()
        return Base64.getUrlEncoder().withoutPadding().encodeToString(baos.toByteArray())
    }

    @Test
    fun parsesAiHordeFrontpageStudioLinkWithPackedR() {
        val payload = """
            {
              "v": 1,
              "request": {
                "prompt": "futuristic flying car ### cartoon, blurry",
                "models": ["AlbedoBase XL (SDXL)"],
                "params": {
                  "steps": 30,
                  "cfg_scale": 6.5,
                  "sampler_name": "k_dpmpp_2m",
                  "karras": true,
                  "seed": "424242",
                  "width": 1024,
                  "height": 1024,
                  "clip_skip": 1,
                  "loras": [{"name": "cyberpunk_style", "model": 0.8}]
                }
              },
              "style": "Sci-Fi",
              "overridesStyleLoras": true
            }
        """.trimIndent()
        val packedR = createPackedStudioLink(payload)
        val url = "https://aihorde.net/generate/image?r=$packedR"

        val result = GenerationUrlParser.parseString(url)
        assertNotNull(result)
        result!!
        assertTrue(result.hasMetadata)
        assertEquals("AI Horde", result.metadata.generatorSource)
        assertEquals("futuristic flying car", result.metadata.prompt)
        assertEquals("cartoon, blurry", result.metadata.negativePrompt)
        assertEquals("AlbedoBase XL (SDXL)", result.metadata.model)
        assertEquals(30, result.metadata.steps)
        assertEquals(6.5f, result.metadata.cfg ?: 0f, 0.01f)
        assertEquals("k_dpmpp_2m", result.metadata.sampler)
        assertEquals("karras", result.metadata.scheduler)
        assertEquals("424242", result.metadata.seed)
        assertEquals(1024, result.metadata.width)
        assertEquals(1024, result.metadata.height)
        assertEquals(1, result.metadata.clipSkip)
        assertEquals(listOf("cyberpunk_style (0.8)"), result.metadata.loras)
        assertEquals("curated:Sci-Fi", result.metadata.rawFields["styleKey"])
        assertEquals("Sci-Fi", result.metadata.rawFields["style"])
        assertEquals("true", result.metadata.rawFields["overridesStyleLoras"])
    }

    @Test
    fun parsesAiHordeFrontpageStudioLinkWithOpenOverridesAndTitle() {
        val payload = """{"v":1,"request":{"prompt":"ancient ruins","models":["DefaultModel"]},"style":"Fantasy"}"""
        val packedR = createPackedStudioLink(payload)
        val url = "https://aihorde.net/generate/image?r=$packedR&model=OverriddenModel&styleId=12345&title=Ancient+Wonders"

        val result = GenerationUrlParser.parseString(url)
        assertNotNull(result)
        result!!
        assertTrue(result.hasMetadata)
        assertEquals("ancient ruins", result.metadata.prompt)
        assertEquals("OverriddenModel", result.metadata.model)
        assertEquals("native:12345", result.metadata.rawFields["styleKey"])
        assertEquals("Ancient Wonders", result.metadata.rawFields["title"])
    }

    @Test
    fun parsesAiHordeSimpleModelAndStyleLinkWithoutR() {
        val url = "https://aihorde.net/generate/image?model=Deliberate&style=Anime"
        val result = GenerationUrlParser.parseString(url)
        assertNotNull(result)
        result!!
        assertTrue(result.hasMetadata)
        assertEquals("AI Horde", result.metadata.generatorSource)
        assertEquals("Deliberate", result.metadata.model)
        assertEquals("curated:Anime", result.metadata.rawFields["styleKey"])
        assertEquals("Anime", result.metadata.rawFields["style"])
    }
}


