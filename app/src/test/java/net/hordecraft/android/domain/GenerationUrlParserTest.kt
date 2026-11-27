package net.hordeslop.studio.domain

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GenerationUrlParserTest {

    @Test
    fun testParseAiHordeUrl() {
        val url = Uri.parse("https://aihorde.net/?prompt=retro%20robot%20painting&model=Deliberate&steps=25&cfg_scale=7.5&sampler_name=k_euler&seed=55555&size=512x768")
        val result = GenerationUrlParser.parse(url)

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
    }

    @Test
    fun testParseArtBotUrlWithHashAndNegative() {
        val url = Uri.parse("https://tinybots.net/artbot?prompt=neon%20city%20skyline###blurry,%20lowres&model=ICBINP&steps=30&cfg_scale=6.0&sampler=k_dpmpp_2m&karras=true")
        val result = GenerationUrlParser.parse(url)

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
    fun testParseNonMetadataUrl() {
        val url = Uri.parse("https://aihorde.net/news")
        val result = GenerationUrlParser.parse(url)

        assertFalse(result.hasMetadata)
        assertEquals("", result.metadata.prompt)
    }

    @Test
    fun testParseFragmentParameters() {
        val url = Uri.parse("https://aihorde.net/#create?prompt=space%20explorer&model=SDXL_base")
        val result = GenerationUrlParser.parse(url)

        assertTrue(result.hasMetadata)
        assertEquals("space explorer", result.metadata.prompt)
        assertEquals("SDXL_base", result.metadata.model)
    }
}
