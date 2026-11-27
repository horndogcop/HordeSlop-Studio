package net.hordeslop.studio.domain

import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StyleContractEngineTest {
    @Test
    fun splitsOverTwentyImagesIntoChunksAndHandlesFanOut() {
        val spec = StudioSubmissionSpec(
            prompt = "lighthouse in mist",
            negativePrompt = "blurry",
            selectedModels = listOf("ModelA", "ModelB"),
            dispatchMode = MultiModelDispatchMode.FAN_OUT,
            activeStyle = null,
            seed = "",
            steps = 25,
            cfg = 7f,
            clipSkip = 1,
            width = 1024,
            height = 1024,
            sampler = "k_euler",
            scheduler = "karras",
            nsfw = false,
            censorNsfw = true,
            imageCount = 45,
            loras = emptyList(),
            tis = emptyList(),
        )
        val chunks = StyleContractEngine.buildSubmissionChunks(spec)
        // 45 splits into [20, 20, 5] across 2 models = 6 requests
        assertEquals(6, chunks.size)
        assertEquals(listOf(20, 20, 5, 20, 20, 5), chunks.map { it.count })
    }

    @Test
    fun styleSubmissionOmitsLockedAndUnchangedParameters() {
        val style = HordeStyle(
            id = "style-1",
            name = "Test Style",
            source = "Styles API",
            model = "Deliberate",
            steps = 30,
            cfg = 7.0f,
            sampler = "k_dpmpp_2m",
            scheduler = "karras",
            clipSkip = 1,
            lockedFields = setOf(StyleFieldLock.MODEL, StyleFieldLock.SAMPLER),
        )
        val spec = StudioSubmissionSpec(
            prompt = "test",
            negativePrompt = "",
            selectedModels = listOf("Deliberate"),
            dispatchMode = MultiModelDispatchMode.COMBINED,
            activeStyle = style,
            seed = "",
            steps = 30, // unchanged
            cfg = 8.5f, // changed & unlocked
            clipSkip = 1, // unchanged
            width = 512,
            height = 512,
            sampler = "k_dpmpp_2m", // locked
            scheduler = "karras", // unchanged
            nsfw = false,
            censorNsfw = true,
            imageCount = 1,
            loras = emptyList(),
            tis = emptyList(),
        )
        val payload = StyleContractEngine.buildSinglePayload(spec, listOf("Deliberate"), 1)
        assertFalse(payload.containsKey("models"))
        assertEquals("style-1", payload["style"]?.jsonPrimitive?.content)
        val params = payload["params"]!!.jsonObject
        assertFalse(params.containsKey("steps"))
        assertFalse(params.containsKey("sampler_name"))
        assertTrue(params.containsKey("cfg_scale"))
    }

    @Test
    fun loraApiTriggersUsePlainAllAndRandomWithoutStarsAndPlainTokensOnTurbo() {
        assertEquals("all", TemplatePrompts.resolveLoraApiTrigger(true, LoraTriggerMode.ALL, emptyList()))
        assertEquals("random", TemplatePrompts.resolveLoraApiTrigger(true, LoraTriggerMode.RANDOM, emptyList()))
        assertEquals("a, b", TemplatePrompts.resolveLoraApiTrigger(true, LoraTriggerMode.CUSTOM, listOf("a", "b")))
        assertNull(TemplatePrompts.resolveLoraApiTrigger(false, LoraTriggerMode.ALL, emptyList()))

        assertTrue(TemplatePrompts.shouldUsePlainTrigger(listOf("Anima XL"), "SDXL"))
        assertTrue(TemplatePrompts.shouldUsePlainTrigger(listOf("Juggernaut XL"), "Krea 2"))
        assertFalse(TemplatePrompts.shouldUsePlainTrigger(listOf("Deliberate"), "SD 1.5"))

        val turboTokens = TemplatePrompts.resolveLoraPromptInsertTokens(
            trainedWords = listOf("sharp focus"),
            mode = LoraTriggerMode.CUSTOM,
            selectedTriggers = listOf("sharp focus"),
            plainTrigger = true,
        )
        assertEquals(listOf("sharp focus"), turboTokens)

        val stdTokens = TemplatePrompts.resolveLoraPromptInsertTokens(
            trainedWords = listOf("sharp focus"),
            mode = LoraTriggerMode.CUSTOM,
            selectedTriggers = listOf("sharp focus"),
            plainTrigger = false,
        )
        assertEquals(listOf("(sharp focus:1.0)"), stdTokens)
    }

    @Test
    fun preservesUiLoraOrderAndEmbeddingModelId() {
        val l1 = AttachedLora("1", 10L, "First", "v1", 101L, useApiTrigger = true, triggerMode = LoraTriggerMode.ALL)
        val l2 = AttachedLora("2", 20L, "Second", "v2", 202L)
        val ti = AttachedTi("ti1", 7808L, 9208L, "EasyNegative", "v2", strength = 0.85f, injectTi = TiInjectMode.NEGPROMPT)
        val spec = StudioSubmissionSpec(
            prompt = "test",
            negativePrompt = "",
            selectedModels = listOf("Juggernaut XL"),
            dispatchMode = MultiModelDispatchMode.COMBINED,
            activeStyle = null,
            seed = "custom_seed_abc",
            steps = 25,
            cfg = 7f,
            clipSkip = 1,
            width = 1024,
            height = 1024,
            sampler = "k_euler",
            scheduler = "karras",
            nsfw = false,
            censorNsfw = true,
            imageCount = 1,
            loras = listOf(l1, l2),
            tis = listOf(ti),
        )
        val payload = StyleContractEngine.buildSinglePayload(spec, listOf("Juggernaut XL"), 1)
        val params = payload["params"]!!.jsonObject
        assertEquals("custom_seed_abc", params["seed"]?.jsonPrimitive?.content)
        val lorasJson = params["loras"]!!.jsonArray
        assertEquals("101", lorasJson[0].jsonObject["name"]?.jsonPrimitive?.content)
        assertEquals("all", lorasJson[0].jsonObject["inject_trigger"]?.jsonPrimitive?.content)
        assertEquals("202", lorasJson[1].jsonObject["name"]?.jsonPrimitive?.content)
        val tisJson = params["tis"]!!.jsonArray
        assertEquals("7808", tisJson[0].jsonObject["name"]?.jsonPrimitive?.content)
        assertEquals("negprompt", tisJson[0].jsonObject["inject_ti"]?.jsonPrimitive?.content)
    }

    @Test
    fun expandsRepoStylesClientSideWithoutServerStyleKey() {
        val repoStyle = HordeStyle(
            id = "krea2",
            name = "krea2",
            source = "AI-Horde-Styles",
            model = "Krea 2",
            promptTemplate = "analog photo of {p}",
            negativeTemplate = "{np}, lowres",
            steps = 20,
            cfg = 4.5f,
            sampler = "k_euler",
            scheduler = "simple",
            rawPromptTemplate = "analog photo of {p}###{np}, lowres",
        )
        val spec = StudioSubmissionSpec(
            prompt = "lighthouse",
            negativePrompt = "blurry",
            selectedModels = listOf("Krea 2"),
            dispatchMode = MultiModelDispatchMode.COMBINED,
            activeStyle = repoStyle,
            seed = "",
            steps = 20,
            cfg = 4.5f,
            clipSkip = 1,
            width = 1024,
            height = 1024,
            sampler = "k_euler",
            scheduler = "simple",
            nsfw = false,
            censorNsfw = true,
            imageCount = 1,
            loras = emptyList(),
            tis = emptyList(),
        )
        val payload = StyleContractEngine.buildSinglePayload(spec, listOf("Krea 2"), 1)
        assertFalse(payload.containsKey("style"))
        assertEquals("analog photo of lighthouse ### blurry, lowres", payload["prompt"]?.jsonPrimitive?.content)
        assertTrue(payload["params"]!!.jsonObject.containsKey("steps"))
    }

    @Test
    fun embeddingFiltersIncludePonyIllustriousNoobAiAndFullSamplerFallback() {
        val tiIds = HordeConstants.CIVITAI_TI_GROUPS.map { it.id }.toSet()
        assertTrue("pony" in tiIds)
        assertTrue("illustrious" in tiIds)
        assertTrue("noobai" in tiIds)

        val allSamplers = (HordeConstants.RECOMMENDED_SAMPLERS + HordeConstants.ADVANCED_SAMPLERS).distinct()
        assertTrue(allSamplers.size >= 40)
    }

    @Test
    fun multiStyleQueuesSeparateRequestPerStyle() {
        val s1 = HordeStyle(
            id = "s1",
            name = "Style One",
            source = "AI-Horde-Styles",
            promptTemplate = "watercolor painting of {p}",
            negativeTemplate = "{np}, signature",
            rawPromptTemplate = "watercolor painting of {p}###{np}, signature",
        )
        val s2 = HordeStyle(
            id = "s2",
            name = "Style Two",
            source = "AI-Horde-Styles",
            promptTemplate = "{p}, cinematic lighting",
            negativeTemplate = "{np}, extra limbs",
            rawPromptTemplate = "{p}, cinematic lighting###{np}, extra limbs",
        )
        val api1 = HordeStyle(
            id = "api-style-1",
            name = "API One",
            source = "Styles API",
            inApi = true,
            steps = 25,
        )
        val api2 = HordeStyle(
            id = "api-style-2",
            name = "API Two",
            source = "Styles API",
            inApi = true,
            steps = 35,
        )

        val clientSpec = StudioSubmissionSpec(
            prompt = "fox in forest",
            negativePrompt = "blurry",
            selectedModels = listOf("Deliberate"),
            dispatchMode = MultiModelDispatchMode.COMBINED,
            activeStyle = s1,
            activeStyles = listOf(s1, s2),
            seed = "",
            steps = 28,
            cfg = 7f,
            clipSkip = 1,
            width = 512,
            height = 512,
            sampler = "k_euler",
            scheduler = "normal",
            nsfw = false,
            censorNsfw = true,
            imageCount = 1,
            loras = emptyList(),
            tis = emptyList(),
        )
        val clientChunks = StyleContractEngine.buildSubmissionChunks(clientSpec)
        assertEquals(2, clientChunks.size)
        assertEquals("watercolor painting of fox in forest ### blurry, signature", clientChunks[0].payload["prompt"]?.jsonPrimitive?.content)
        assertEquals("fox in forest, cinematic lighting ### blurry, extra limbs", clientChunks[1].payload["prompt"]?.jsonPrimitive?.content)

        val apiChunks = StyleContractEngine.buildSubmissionChunks(
            clientSpec.copy(activeStyle = api1, activeStyles = listOf(api1, api2)),
        )
        assertEquals(2, apiChunks.size)
        assertEquals("api-style-1", apiChunks[0].payload["style"]?.jsonPrimitive?.content)
        assertEquals("api-style-2", apiChunks[1].payload["style"]?.jsonPrimitive?.content)
    }
}
