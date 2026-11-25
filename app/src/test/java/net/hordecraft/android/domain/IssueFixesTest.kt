package net.hordeslop.studio.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import net.hordeslop.studio.data.remote.ModelReferenceRepository
import net.hordeslop.studio.data.repository.HordecraftRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IssueFixesTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun launcherIconComponentNamesResolveAgainstManifestNamespace() {
        assertEquals(
            "net.hordeslop.studio.LauncherRobot",
            LauncherIconManager.componentClassName(LauncherIconChoice.ROBOT_PLACEHOLDER),
        )
        assertEquals(
            "net.hordeslop.studio.LauncherHaidra",
            LauncherIconManager.componentClassName(LauncherIconChoice.HORDE_HAIDRA),
        )
    }

    @Test
    fun resolveLockedFieldsRespectsParameterPolicy() {
        assertEquals(
            emptySet<StyleFieldLock>(),
            ModelReferenceRepository.resolveLockedFields(null, hasModels = true),
        )

        val overrideAll = json.parseToJsonElement("""{"override":"all"}""").jsonObject
        assertEquals(
            emptySet<StyleFieldLock>(),
            ModelReferenceRepository.resolveLockedFields(overrideAll, hasModels = true),
        )

        val listedPartial = json.parseToJsonElement(
            """{"override":"listed","overridable":["height","width","steps","sampler_name"]}""",
        ).jsonObject
        val locks = ModelReferenceRepository.resolveLockedFields(listedPartial, hasModels = true)
        assertFalse(StyleFieldLock.STEPS in locks)
        assertFalse(StyleFieldLock.SAMPLER in locks)
        assertFalse(StyleFieldLock.SIZE in locks)
        assertTrue(StyleFieldLock.MODEL in locks)
        assertTrue(StyleFieldLock.CFG in locks)
        assertTrue(StyleFieldLock.SCHEDULER in locks)
        assertTrue(StyleFieldLock.CLIP_SKIP in locks)

        val noModelsLocks = ModelReferenceRepository.resolveLockedFields(listedPartial, hasModels = false)
        assertFalse(StyleFieldLock.MODEL in noModelsLocks)
    }

    @Test
    fun normalizesStylesRepoAndSiblingUrls() {
        assertEquals(
            "https://raw.githubusercontent.com/Haidra-Org/AI-Horde-Styles/main/styles.json",
            ModelReferenceRepository.normalizeStylesRepoUrl("https://github.com/Haidra-Org/AI-Horde-Styles"),
        )
        assertEquals(
            "https://raw.githubusercontent.com/Haidra-Org/AI-Horde-Styles/dev/custom/styles.json",
            ModelReferenceRepository.normalizeStylesRepoUrl("https://github.com/Haidra-Org/AI-Horde-Styles/tree/dev/custom"),
        )
        assertEquals(
            "https://raw.githubusercontent.com/Owner/Repo/main/sub/my_styles.json",
            ModelReferenceRepository.normalizeStylesRepoUrl("https://github.com/Owner/Repo/blob/main/sub/my_styles.json"),
        )
        assertEquals(
            "https://example.com/custom/categories.json",
            ModelReferenceRepository.siblingJsonUrl("https://example.com/custom/styles.json", "categories.json"),
        )
    }

    @Test
    fun matchesCurrentUserCreatorHandlesDiscriminatorAndCase() {
        assertTrue(ModelReferenceRepository.matchesCurrentUserCreator("Alice#1234", "alice#1234"))
        assertTrue(ModelReferenceRepository.matchesCurrentUserCreator("Alice", "alice#1234"))
        assertTrue(ModelReferenceRepository.matchesCurrentUserCreator("Alice#1234", "alice"))
        assertFalse(ModelReferenceRepository.matchesCurrentUserCreator("Alice#1234", "alice#9999"))
        assertFalse(ModelReferenceRepository.matchesCurrentUserCreator("Bob#1234", "Alice#1234"))
        assertFalse(ModelReferenceRepository.matchesCurrentUserCreator(null, "Alice#1234"))
    }

    @Test
    fun officialStylesRepoDetectionExcludesThirdPartyRepos() {
        assertTrue(ModelReferenceRepository.isOfficialStylesRepoUrl(HordeConstants.DEFAULT_STYLES_REPO_URL))
        assertTrue(ModelReferenceRepository.isOfficialStylesRepoUrl("https://github.com/Haidra-Org/AI-Horde-Styles"))
        assertFalse(ModelReferenceRepository.isOfficialStylesRepoUrl("https://github.com/someone/custom-styles"))
        assertFalse(ModelReferenceRepository.isOfficialStylesRepoUrl("https://example.com/styles.json"))
    }

    @Test
    fun apiStyleUsesOnlyOwnExamplesAndNeverPreviewRepo() {
        val repo = ModelReferenceRepository(okhttp3.OkHttpClient(), json)
        val noExamplesDto = net.hordeslop.studio.data.remote.HordeStyleDto(
            id = "api-1",
            name = "Watercolor",
            examples = emptyList(),
        )
        val mappedNoExamples = repo.mapApiStyle(noExamplesDto)
        assertTrue(mappedNoExamples.previewImageUrls.isEmpty())
        assertEquals(null, mappedNoExamples.previewImageUrl)

        val withExamplesDto = net.hordeslop.studio.data.remote.HordeStyleDto(
            id = "api-2",
            name = "Painterly",
            examples = listOf(
                net.hordeslop.studio.data.remote.StyleExampleDto(id = "1", url = "https://example.com/secondary.webp", primary = false),
                net.hordeslop.studio.data.remote.StyleExampleDto(id = "2", url = "https://example.com/primary.webp", primary = true),
            ),
        )
        val mappedWithExamples = repo.mapApiStyle(withExamplesDto)
        assertEquals(
            listOf("https://example.com/primary.webp", "https://example.com/secondary.webp"),
            mappedWithExamples.previewImageUrls,
        )
        assertEquals("https://example.com/primary.webp", mappedWithExamples.previewImageUrl)
    }

    @Test
    fun mergeAndSortStylesPinsUserStylesAfterRawPromptWithoutLeakingRepoPreviewsToApi() {
        val raw = HordeStyle(id = "none", name = "Raw Prompt", source = "None")
        val repoStyles = listOf(
            HordeStyle(
                id = "Watercolor",
                name = "Watercolor",
                source = "Styles repo",
                categories = listOf("Artistic"),
                previewImageUrl = "https://example.com/wc.webp",
                previewImageUrls = listOf("https://example.com/wc.webp"),
                inRepo = true,
            ),
            HordeStyle(
                id = "Anime",
                name = "Anime",
                source = "Styles repo",
                categories = listOf("Anime"),
                inRepo = true,
            ),
        )
        val apiStyles = listOf(
            HordeStyle(
                id = "api-wc",
                name = "Watercolor",
                source = "Styles API",
                categories = listOf("Paint"),
                inApi = true,
            ),
            HordeStyle(
                id = "api-user",
                name = "Zebra Custom",
                source = "Styles API",
                isUserCreated = true,
                inApi = true,
            ),
        )

        val merged = HordecraftRepository.mergeAndSortStyles(raw, repoStyles, apiStyles)
        assertEquals(listOf("Raw Prompt", "Zebra Custom", "Anime", "Watercolor", "Watercolor"), merged.map { it.name })

        val apiWatercolor = merged.first { it.inApi && it.name == "Watercolor" }
        assertTrue(apiWatercolor.inApi)
        assertFalse(apiWatercolor.inRepo)
        assertTrue(apiWatercolor.previewImageUrls.isEmpty())

        val repoWatercolor = merged.first { it.inRepo && it.name == "Watercolor" }
        assertTrue(repoWatercolor.inRepo)
        assertFalse(repoWatercolor.inApi)
        assertEquals(listOf("https://example.com/wc.webp"), repoWatercolor.previewImageUrls)
    }

    @Test
    fun serverStyleFlagAppliesToMergedApiAndRepoStyles() {
        val mergedStyle = HordeStyle(
            id = "api-wc",
            name = "Watercolor",
            source = "Styles API",
            inApi = true,
            inRepo = true,
            steps = 30,
        )
        val spec = StudioSubmissionSpec(
            prompt = "cat",
            negativePrompt = "",
            selectedModels = listOf("Deliberate"),
            dispatchMode = MultiModelDispatchMode.COMBINED,
            activeStyle = mergedStyle,
            seed = "",
            steps = 30,
            cfg = 7f,
            clipSkip = 1,
            width = 512,
            height = 512,
            sampler = "k_euler",
            scheduler = "karras",
            nsfw = false,
            censorNsfw = true,
            imageCount = 1,
            loras = emptyList(),
            tis = emptyList(),
        )
        val payload = StyleContractEngine.buildSinglePayload(spec, listOf("Deliberate"), 1)
        assertEquals("api-wc", payload["style"]?.toString()?.trim('"'))
    }

    @Test
    fun dismissedJobResurfacesOnlyWhenFinishedCountIncreases() {
        assertFalse(HordecraftRepository.shouldResurfaceDismissedJob(dismissedAtFinishedCount = 2, apiFinishedCount = 2))
        assertFalse(HordecraftRepository.shouldResurfaceDismissedJob(dismissedAtFinishedCount = 2, apiFinishedCount = 1))
        assertTrue(HordecraftRepository.shouldResurfaceDismissedJob(dismissedAtFinishedCount = 2, apiFinishedCount = 3))
    }

    @Test
    fun destinationUserLabelIsHorde() {
        assertEquals("Horde", ActivityDestination.USER.label)
    }

    @Test
    fun defaultSamplerAndSchedulerAreKEulerAndNormal() {
        val state = net.hordeslop.studio.ui.StudioUiState()
        assertEquals("k_euler", state.sampler)
        assertEquals("normal", state.scheduler)
        assertTrue(HordeConstants.FALLBACK_SEED_MODELS.all { it.defaultSampler == "k_euler" })
        assertTrue(HordeConstants.FALLBACK_SEED_MODELS.all { it.defaultScheduler == "normal" })
    }

    @Test
    fun databaseBackupCodecRoundTripsAllTables() {
        val bundle = net.hordeslop.studio.data.local.DatabaseBackupCodec.buildBundle(
            promptHistory = listOf(
                net.hordeslop.studio.data.local.PromptHistoryEntity(
                    id = "ph1",
                    title = "Castle",
                    prompt = "stone castle",
                    negativePrompt = "blurry",
                    model = "Deliberate",
                    steps = 28,
                    cfg = 7.0f,
                    clipSkip = 1,
                    sampler = "k_euler",
                    scheduler = "normal",
                    timestampMillis = 123456L,
                    isFavorite = true,
                ),
            ),
            modelDefaults = listOf(
                net.hordeslop.studio.data.local.ModelDefaultsEntity(
                    modelName = "Juggernaut XL",
                    steps = 30,
                    sampler = "k_euler",
                    scheduler = "normal",
                    clipSkip = 1,
                ),
            ),
            savedImages = listOf(
                net.hordeslop.studio.data.local.SavedImageEntity(
                    id = "img1",
                    requestId = "req1",
                    generationId = "gen1",
                    fileUri = "content://example/img1.webp",
                    prompt = "stone castle",
                    negativePrompt = "",
                    model = "Deliberate",
                    seed = "42",
                    steps = 28,
                    cfg = 7.0f,
                    clipSkip = 1,
                    sampler = "k_euler",
                    scheduler = "normal",
                    width = 512,
                    height = 768,
                    destinationLabel = "Downloads/HordeSlop Studio",
                    isFavorite = false,
                    partialFromCancel = false,
                    requestJson = "{}",
                    statusMetadataJson = "{}",
                    createdAtMillis = 999L,
                ),
            ),
            activeJobs = emptyList(),
            exportedAtMillis = 5555L,
        )
        val encoded = net.hordeslop.studio.data.local.DatabaseBackupCodec.encodeJson(bundle)
        val decoded = net.hordeslop.studio.data.local.DatabaseBackupCodec.decodeJson(encoded)
        assertEquals(1, decoded.promptHistory.size)
        assertEquals("ph1", decoded.promptHistory.first().id)
        assertEquals(1, decoded.modelDefaults.size)
        assertEquals("Juggernaut XL", decoded.modelDefaults.first().modelName)
        assertEquals(1, decoded.savedImages.size)
        assertEquals("req1", decoded.savedImages.first().requestId)
        assertEquals(5555L, decoded.exportedAtMillis)
        assertTrue(
            net.hordeslop.studio.data.local.DatabaseBackupCodec.isSqliteDatabaseBytes(
                "SQLite format 3\u0000rest".toByteArray(Charsets.US_ASCII),
            ),
        )
        assertFalse(
            net.hordeslop.studio.data.local.DatabaseBackupCodec.isSqliteDatabaseBytes(
                encoded.toByteArray(Charsets.UTF_8),
            ),
        )
    }

    @Test
    fun clipSkipMovesToAdvancedForNonPonyWithNonDefaultValue() {
        val sdxlModel = HordeModel(
            name = "Juggernaut XL",
            baseline = "SDXL",
            workers = 5,
            queued = 0,
            etaSeconds = 5,
            defaultSteps = 30,
            defaultSampler = "k_euler",
            defaultScheduler = "normal",
            defaultClipSkip = 1,
            suggestedMinSteps = 18,
            suggestedMaxSteps = 45,
            suggestedMinCfg = 3.5f,
            suggestedMaxCfg = 9.5f,
            suggestedWidth = 1024,
            suggestedHeight = 1024,
        )
        val ponyModel = sdxlModel.copy(name = "Pony Diffusion V6 XL", baseline = "Pony")
        assertFalse(net.hordeslop.studio.ui.StudioUiState.shouldMoveClipSkipToAdvanced(sdxlModel, 1))
        assertTrue(net.hordeslop.studio.ui.StudioUiState.shouldMoveClipSkipToAdvanced(sdxlModel, 2))
        assertFalse(net.hordeslop.studio.ui.StudioUiState.shouldMoveClipSkipToAdvanced(ponyModel, 2))
    }

    @Test
    fun useSeparateActivitiesDefaultsToTrue() {
        val defaults = net.hordeslop.studio.data.local.AppSettings()
        assertTrue(defaults.useSeparateActivities)
    }
}
