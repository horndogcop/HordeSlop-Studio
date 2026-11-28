package net.hordeslop.studio.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
    fun clipSkipMovesToAdvancedWhenDefaultAndStaysRegularWhenNonDefault() {
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
        val styledClip = HordeStyle(id = "style-1", name = "Styled", source = "Styles API", clipSkip = 2)
        assertTrue(net.hordeslop.studio.ui.StudioUiState.shouldMoveClipSkipToAdvanced(sdxlModel, 1))
        assertFalse(net.hordeslop.studio.ui.StudioUiState.shouldMoveClipSkipToAdvanced(sdxlModel, 2))
        assertTrue(net.hordeslop.studio.ui.StudioUiState.shouldMoveClipSkipToAdvanced(ponyModel, 2))
        assertFalse(net.hordeslop.studio.ui.StudioUiState.shouldMoveClipSkipToAdvanced(ponyModel, 1))
        assertTrue(net.hordeslop.studio.ui.StudioUiState.shouldMoveClipSkipToAdvanced(sdxlModel, 2, styledClip))
    }

    @Test
    fun useSeparateActivitiesDefaultsToTrue() {
        val defaults = net.hordeslop.studio.data.local.AppSettings()
        assertTrue(defaults.useSeparateActivities)
    }

    @Test
    fun gallerySearchNegativePrefixMatchesOnlyAfterDelimiter() {
        val img = SavedImage(
            id = "1",
            requestId = "req-123",
            generationId = "gen-456",
            fileUri = "content://img/1.webp",
            prompt = "stone castle under moonlight",
            negativePrompt = "blurry watermark",
            model = "Juggernaut XL",
            seed = "998877",
            steps = 30,
            cfg = 7f,
            clipSkip = 1,
            sampler = "k_euler",
            scheduler = "normal",
            width = 1024,
            height = 1024,
            destinationLabel = "Downloads",
            isFavorite = false,
            partialFromCancel = false,
            requestJson = "{}",
            statusMetadataJson = "{}",
            createdAtMillis = 1L,
        )
        assertTrue(GallerySearchFilter.matches(img, "castle"))
        assertFalse(GallerySearchFilter.matches(img, "blurry"))
        assertTrue(GallerySearchFilter.matches(img, "### blurry"))
        assertTrue(GallerySearchFilter.matches(img, "castle ### watermark"))
        assertFalse(GallerySearchFilter.matches(img, "castle ### nonexistent"))
        assertFalse(GallerySearchFilter.matches(img, "forest ### blurry"))
    }

    @Test
    fun curlExportStripsI2iAttachmentsAndMasksApiKey() {
        val raw = kotlinx.serialization.json.buildJsonObject {
            put("prompt", kotlinx.serialization.json.JsonPrimitive("sunset mountain"))
            put("source_image", kotlinx.serialization.json.JsonPrimitive("BASE64_DATA"))
            put("source_mask", kotlinx.serialization.json.JsonPrimitive("MASK_DATA"))
            put("source_processing", kotlinx.serialization.json.JsonPrimitive("img2img"))
            put("nsfw", kotlinx.serialization.json.JsonPrimitive(false))
        }
        val curl = StyleContractEngine.buildCurlCommand(
            apiBaseUrl = "https://aihorde.net/api/v2",
            apiKey = "secret-user-key-123",
            payload = raw,
        )
        assertTrue(curl.startsWith("curl -X POST 'https://aihorde.net/api/v2/generate/async'"))
        assertTrue(curl.contains("YOUR_HORDE_API_KEY"))
        assertFalse(curl.contains("secret-user-key-123"))
        assertFalse(curl.contains("source_image"))
        assertFalse(curl.contains("BASE64_DATA"))
        assertFalse(curl.contains("source_mask"))
        assertTrue(curl.contains("sunset mountain"))
    }

    @Test
    fun showRawRequestReviewDefaultsToFalse() {
        val defaults = net.hordeslop.studio.data.local.AppSettings()
        assertFalse(defaults.showRawRequestReview)
    }

    @Test
    fun hiresFixPayloadSerializationTest() {
        val spec = StudioSubmissionSpec(
            prompt = "cat",
            negativePrompt = "",
            selectedModels = listOf("Deliberate"),
            dispatchMode = MultiModelDispatchMode.COMBINED,
            activeStyle = null,
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
            hiresFix = true,
        )
        val chunks = StyleContractEngine.buildSubmissionChunks(spec)
        val params = chunks.first().payload["params"]?.jsonObject
        assertTrue(params?.get("hires_fix")?.jsonPrimitive?.booleanOrNull == true)
    }

    @Test
    fun globalRateLimitDefaultsAndMappingTest() {
        val defaults = net.hordeslop.studio.data.local.AppSettings()
        assertEquals(3, defaults.globalRateLimitSeconds)
        assertEquals(1_100L, net.hordeslop.studio.data.remote.HordeRateLimiterInterceptor.effectiveMinIntervalMs(1))
        assertEquals(3_000L, net.hordeslop.studio.data.remote.HordeRateLimiterInterceptor.effectiveMinIntervalMs(3))
        assertEquals(30_000L, net.hordeslop.studio.data.remote.HordeRateLimiterInterceptor.effectiveMinIntervalMs(30))
    }

    @Test
    fun fallbackFailoverOnlyAppliesToDefaultServerAndRewritesToStableHorde() {
        val interceptorClass = net.hordeslop.studio.data.remote.HordeRateLimiterInterceptor
        assertTrue(interceptorClass.isFallbackEligible(""))
        assertTrue(interceptorClass.isFallbackEligible("https://aihorde.net/api/v2"))
        assertTrue(interceptorClass.isFallbackEligible("https://aihorde.net/api/v2/"))
        assertFalse(interceptorClass.isFallbackEligible("https://custom.example.com/api/v2/"))
        assertFalse(interceptorClass.isFallbackEligible("https://stablehorde.net/api/v2/"))

        assertTrue(interceptorClass.shouldRetryWithFallback(500))
        assertTrue(interceptorClass.shouldRetryWithFallback(503))
        assertTrue(interceptorClass.shouldRetryWithFallback(408))
        assertFalse(interceptorClass.shouldRetryWithFallback(404))
        assertFalse(interceptorClass.shouldRetryWithFallback(429))

        val originalUrl = okhttp3.HttpUrl.Builder()
            .scheme("https")
            .host("aihorde.net")
            .addPathSegments("api/v2/generate/async")
            .build()
        val rewritten = interceptorClass.rewriteBaseUrl(originalUrl, HordeConstants.FALLBACK_API_BASE_URL)
        assertEquals("https://stablehorde.net/api/v2/generate/async", rewritten.toString())
    }

    @Test
    fun viewerSnapshotAnchorsCurrentImageWhenNewGenerationsArrive() {
        fun makeImg(id: String) = SavedImage(
            id = id,
            requestId = "req-$id",
            generationId = "gen-$id",
            fileUri = "file://$id.webp",
            prompt = "prompt $id",
            negativePrompt = "",
            model = "model",
            seed = "123",
            steps = 20,
            cfg = 7f,
            clipSkip = 1,
            sampler = "k_euler",
            scheduler = "karras",
            width = 512,
            height = 512,
            destinationLabel = "App Storage",
            isFavorite = false,
            partialFromCancel = false,
            requestJson = "{}",
            statusMetadataJson = "{}",
            createdAtMillis = 1000L,
        )

        val initialImages = listOf(makeImg("1"), makeImg("2"), makeImg("3"))
        var snapshot: List<SavedImage>? = initialImages
        val activeIndex = 1

        val updatedImages = listOf(makeImg("4")) + initialImages

        val latestById = updatedImages.associateBy { it.id }
        val anchoredViewerList = snapshot?.mapNotNull { snapImg ->
            latestById[snapImg.id] ?: snapImg
        }

        assertEquals(3, anchoredViewerList?.size)
        assertEquals("1", anchoredViewerList?.get(0)?.id)
        assertEquals("2", anchoredViewerList?.get(activeIndex)?.id)
        assertEquals("3", anchoredViewerList?.get(2)?.id)

        snapshot = null
        assertEquals(4, (snapshot ?: updatedImages).size)
    }

    @Test
    fun structuredExamplePromptsLoadFromTextFile() {
        val parsed = TemplatePrompts.parseStructuredPrompts(
            """
            # Comment line
            [Section]
            first prompt
            
            second prompt
            """.trimIndent(),
        )
        assertEquals(listOf("first prompt", "second prompt"), parsed)
        assertEquals(100, TemplatePrompts.PRESETS.size)
        assertEquals(100, TemplatePrompts.PRESETS.distinct().size)
    }

    @Test
    fun localQueuedJobStatusExistsAndDefaultsCreatedAt() {
        val job = HordeJob(
            id = "local_123",
            requestId = "",
            prompt = "test prompt",
            negativePrompt = "",
            models = listOf("Deliberate"),
            status = JobStatus.LOCAL_QUEUED,
            waitTimeSeconds = 0,
            queuePosition = 0,
            finished = 0,
            processing = 0,
            total = 1,
            submittedPayloadJson = "{}",
            createdAtMillis = 42_000L,
        )
        assertEquals(JobStatus.LOCAL_QUEUED, job.status)
        assertEquals("", job.requestId)
        assertEquals(42_000L, job.createdAtMillis)
    }

    @Test
    fun galleryTimestampFormatterFormatsUtcAndHandlesUnknown() {
        val utc = java.util.TimeZone.getTimeZone("UTC")
        assertEquals("Unknown", GalleryTimestampFormatter.formatShort(0L, utc, java.util.Locale.US))
        assertEquals("Unknown", GalleryTimestampFormatter.formatFull(-5L, utc, java.util.Locale.US))
        // 2026-04-12 14:32:10 UTC
        val sampleMillis = 1_776_004_330_000L
        assertEquals(
            "Apr 12, 2026 · 14:32",
            GalleryTimestampFormatter.formatShort(sampleMillis, utc, java.util.Locale.US),
        )
        assertEquals(
            "2026-04-12 14:32:10 UTC",
            GalleryTimestampFormatter.formatFull(sampleMillis, utc, java.util.Locale.US),
        )
    }

    @Test
    fun galleryFilterStateDefaultsToCompleteAndControlsFavoritesAndQueues() {
        val defaultFilter = GalleryFilterState()
        assertTrue(defaultFilter.isDefault)
        assertTrue(defaultFilter.includesCompleteGenerations)
        assertFalse(defaultFilter.isAllSelected)
        assertFalse(defaultFilter.isAllQueuedSelected)

        // Switching to "All queued images" excludes Complete -> favorites must disable
        val queuedOnly = defaultFilter
            .toggleCategory(GalleryStatusCategory.COMPLETE_GENERATIONS)
            .toggleAllQueued()
        assertFalse(queuedOnly.includesCompleteGenerations)
        assertTrue(queuedOnly.isAllQueuedSelected)
        assertFalse(queuedOnly.isAllSelected)

        fun job(status: JobStatus, reqId: String = "req-1", err: String? = null) = HordeJob(
            id = "${status.name}_$reqId",
            requestId = reqId,
            prompt = "cyberpunk skyline",
            negativePrompt = "blurry",
            models = listOf("Juggernaut XL"),
            status = status,
            waitTimeSeconds = 0,
            queuePosition = 0,
            finished = 0,
            processing = 0,
            total = 1,
            submittedPayloadJson = "{}",
            errorMessage = err,
            createdAtMillis = 1234L,
        )

        val localJob = job(JobStatus.LOCAL_QUEUED, reqId = "")
        val localPausedJob = job(JobStatus.PAUSED, reqId = "")
        val serverQueuedJob = job(JobStatus.QUEUED, reqId = "srv-1")
        val serverProcessingJob = job(JobStatus.PROCESSING, reqId = "srv-2")
        val serverPausedJob = job(JobStatus.PAUSED, reqId = "srv-3")
        val failedJob = job(JobStatus.FAILED, reqId = "", err = "HTTP 400: bad params")
        val completedJob = job(JobStatus.COMPLETED, reqId = "srv-4")

        assertTrue(queuedOnly.matchesJob(localJob))
        assertTrue(queuedOnly.matchesJob(localPausedJob))
        assertTrue(queuedOnly.matchesJob(serverQueuedJob))
        assertTrue(queuedOnly.matchesJob(serverProcessingJob))
        assertTrue(queuedOnly.matchesJob(serverPausedJob))
        assertFalse(queuedOnly.matchesJob(failedJob))
        assertFalse(queuedOnly.matchesJob(completedJob))

        val errorsOnly = GalleryFilterState(setOf(GalleryStatusCategory.ERRORS))
        assertFalse(errorsOnly.includesCompleteGenerations)
        assertTrue(errorsOnly.matchesJob(failedJob))
        assertFalse(errorsOnly.matchesJob(localJob))

        val allImages = GalleryFilterState().toggleAll()
        assertTrue(allImages.isAllSelected)
        assertTrue(allImages.includesCompleteGenerations)
        assertTrue(allImages.isAllQueuedSelected)
        assertTrue(allImages.matchesJob(localJob))
        assertTrue(allImages.matchesJob(serverProcessingJob))
        assertTrue(allImages.matchesJob(failedJob))

        // Search filter on jobs supports prompt, ### negative, model, and error text
        assertTrue(GallerySearchFilter.matchesJob(failedJob, "cyberpunk ### blurry"))
        assertFalse(GallerySearchFilter.matchesJob(failedJob, "cyberpunk ### sharp"))
        assertTrue(GallerySearchFilter.matchesJob(failedJob, "bad params"))
    }

    @Test
    fun generatedImageDtoBlocksCensoredNsfwAndCsamWithoutTreatingOkAsBlocked() {
        val ok = net.hordeslop.studio.data.remote.GeneratedImageDto(
            id = "g1",
            img = "data",
            censored = false,
            state = "ok",
        )
        assertEquals(null, ok.galleryBlockReason())

        val stateCensored = net.hordeslop.studio.data.remote.GeneratedImageDto(
            id = "g2",
            img = "placeholder",
            censored = false,
            state = "censored",
        )
        assertEquals("Censored NSFW", stateCensored.galleryBlockReason())

        val stateCsam = net.hordeslop.studio.data.remote.GeneratedImageDto(
            id = "g3",
            img = "placeholder",
            censored = false,
            state = "csam",
        )
        assertEquals("CSAM blocked", stateCsam.galleryBlockReason())

        val metaNsfw = net.hordeslop.studio.data.remote.GeneratedImageDto(
            id = "g4",
            img = "placeholder",
            censored = false,
            state = "ok",
            genMetadata = listOf(
                net.hordeslop.studio.data.remote.GenerationMetadataStableDto(
                    type = "censorship",
                    value = "nsfw",
                ),
            ),
        )
        assertEquals("Censored NSFW", metaNsfw.galleryBlockReason())

        val metaCsam = net.hordeslop.studio.data.remote.GeneratedImageDto(
            id = "g5",
            img = "placeholder",
            censored = true,
            state = "ok",
            genMetadata = listOf(
                net.hordeslop.studio.data.remote.GenerationMetadataStableDto(
                    type = "censorship",
                    value = "csam",
                ),
            ),
        )
        assertEquals("CSAM blocked", metaCsam.galleryBlockReason())

        // censored flag alone is not enough without state/metadata
        val censoredFlagOnly = net.hordeslop.studio.data.remote.GeneratedImageDto(
            id = "g6",
            img = "maybe-real",
            censored = true,
            state = "ok",
        )
        assertEquals(null, censoredFlagOnly.galleryBlockReason())

        // missing / empty metadata
        val missingMeta = net.hordeslop.studio.data.remote.GeneratedImageDto(
            id = "g7",
            img = "data",
            state = "ok",
            genMetadata = emptyList(),
        )
        assertEquals(null, missingMeta.galleryBlockReason())

        // malformed / unexpected metadata values are ignored
        val weirdMeta = net.hordeslop.studio.data.remote.GeneratedImageDto(
            id = "g8",
            img = "data",
            state = "ok",
            genMetadata = listOf(
                net.hordeslop.studio.data.remote.GenerationMetadataStableDto(
                    type = "other",
                    value = "nsfw",
                ),
                net.hordeslop.studio.data.remote.GenerationMetadataStableDto(
                    type = "censorship",
                    value = "unknown",
                ),
            ),
        )
        assertEquals(null, weirdMeta.galleryBlockReason())

        // case-insensitive state and metadata
        val upperCsam = net.hordeslop.studio.data.remote.GeneratedImageDto(
            id = "g9",
            img = "placeholder",
            state = "CSAM",
            genMetadata = listOf(
                net.hordeslop.studio.data.remote.GenerationMetadataStableDto(
                    type = "Censorship",
                    value = "NSFW",
                ),
            ),
        )
        assertEquals("CSAM blocked", upperCsam.galleryBlockReason())
    }

    @Test
    fun persistGenerationsResultFormatsBlockCountsAndPrecedence() {
        val none = net.hordeslop.studio.data.repository.HordecraftRepository.PersistGenerationsResult(
            savedCount = 2,
        )
        assertEquals(null, none.errorMessage())
        assertEquals(false, none.hasBlocks)
        assertEquals(false, none.allBlocked)

        val mixed = net.hordeslop.studio.data.repository.HordecraftRepository.PersistGenerationsResult(
            savedCount = 1,
            censoredCount = 2,
            csamCount = 1,
        )
        assertEquals(true, mixed.hasBlocks)
        assertEquals(false, mixed.allBlocked)
        assertEquals(3, mixed.blockedCount)
        assertEquals("CSAM blocked; Censored NSFW (2)", mixed.errorMessage())

        val onlyCensored = net.hordeslop.studio.data.repository.HordecraftRepository.PersistGenerationsResult(
            savedCount = 0,
            censoredCount = 1,
        )
        assertEquals(true, onlyCensored.allBlocked)
        assertEquals("Censored NSFW", onlyCensored.errorMessage())

        val multiCsam = net.hordeslop.studio.data.repository.HordecraftRepository.PersistGenerationsResult(
            savedCount = 0,
            csamCount = 3,
        )
        assertEquals("CSAM blocked (3)", multiCsam.errorMessage())
    }

    // ── Issue 53: settings backup ─────────────────────────────────────────────

    @Test
    fun settingsBackupRoundTripsPortableFields() {
        val dto = net.hordeslop.studio.data.local.AppSettingsBackupDto(
            apiBaseUrl = "https://example.com/api/v2/",
            navLayout = "menu",
            generateButtonPlacement = "bottom",
            showModelPreviews = false,
            skipGalleryModalToFullscreen = true,
            autoClearCompletedQueue = true,
            showRawRequestReview = true,
            stylesRepoUrls = listOf("https://styles.example.com/styles.json"),
            preventMediaIndexing = true,
            globalRateLimitSeconds = 10,
            galleryColumns = 4,
            showGalleryPromptOverlay = false,
            galleryPagedMode = true,
        )
        val bundle = net.hordeslop.studio.data.local.DatabaseBackupCodec.buildBundle(
            promptHistory = emptyList(),
            modelDefaults = emptyList(),
            savedImages = emptyList(),
            activeJobs = emptyList(),
            settings = net.hordeslop.studio.data.local.AppSettings(
                apiBaseUrl = "https://example.com/api/v2/",
                navLayout = net.hordeslop.studio.domain.NavLayoutMode.MENU,
                generateButtonPlacement = net.hordeslop.studio.domain.GenerateButtonPlacement.BOTTOM,
                showModelPreviews = false,
                skipGalleryModalToFullscreen = true,
                autoClearCompletedQueue = true,
                showRawRequestReview = true,
                stylesRepoUrls = listOf("https://styles.example.com/styles.json"),
                preventMediaIndexing = true,
                globalRateLimitSeconds = 10,
                galleryColumns = 4,
                showGalleryPromptOverlay = false,
                galleryPagedMode = true,
                // secrets and device-bound fields — should NOT appear in appSettings dto
                hordeApiKey = "super_secret_key",
                civitaiApiKey = "civ_secret",
                safTreeUri = "content://com.example/tree/primary%3AHordeSlop",
                storageMode = net.hordeslop.studio.domain.StorageMode.SAF,
                launcherIcon = net.hordeslop.studio.domain.LauncherIconChoice.HORDE_HAIDRA,
            ),
        )

        val encoded = net.hordeslop.studio.data.local.DatabaseBackupCodec.encodeJson(bundle)
        val decoded = net.hordeslop.studio.data.local.DatabaseBackupCodec.decodeJson(encoded)
        val settings = decoded.appSettings
        assertFalse("appSettings must be present in format-2 bundle", settings == null)
        requireNotNull(settings)

        // Portable fields round-trip
        assertEquals("https://example.com/api/v2/", settings.apiBaseUrl)
        assertEquals("menu", settings.navLayout)
        assertEquals("bottom", settings.generateButtonPlacement)
        assertEquals(false, settings.showModelPreviews)
        assertEquals(true, settings.skipGalleryModalToFullscreen)
        assertEquals(true, settings.autoClearCompletedQueue)
        assertEquals(true, settings.showRawRequestReview)
        assertEquals(listOf("https://styles.example.com/styles.json"), settings.stylesRepoUrls)
        assertEquals(true, settings.preventMediaIndexing)
        assertEquals(10, settings.globalRateLimitSeconds)
        assertEquals(4, settings.galleryColumns)
        assertEquals(false, settings.showGalleryPromptOverlay)
        assertEquals(true, settings.galleryPagedMode)

        // Secrets must NOT appear anywhere in the JSON output
        assertFalse("API key must not be in backup JSON", encoded.contains("super_secret_key"))
        assertFalse("Civitai key must not be in backup JSON", encoded.contains("civ_secret"))

        // Device-bound SAF URI must NOT appear
        assertFalse("SAF tree URI must not be in backup JSON", encoded.contains("primary%3AHordeSlop"))
    }

    @Test
    fun settingsBackupSecretsAbsentFromJson() {
        val bundle = net.hordeslop.studio.data.local.DatabaseBackupCodec.buildBundle(
            promptHistory = emptyList(),
            modelDefaults = emptyList(),
            savedImages = emptyList(),
            activeJobs = emptyList(),
            settings = net.hordeslop.studio.data.local.AppSettings(
                hordeApiKey = "0000111122223333",
                civitaiApiKey = "civ_abc123",
            ),
        )
        val json = net.hordeslop.studio.data.local.DatabaseBackupCodec.encodeJson(bundle)
        assertFalse("Horde key must not appear in JSON", json.contains("0000111122223333"))
        assertFalse("Civitai key must not appear in JSON", json.contains("civ_abc123"))
    }

    @Test
    fun settingsBackupMergePreservesSecrets() {
        val dto = net.hordeslop.studio.data.local.AppSettingsBackupDto(
            navLayout = "menu",
            galleryColumns = 3,
        )
        val current = net.hordeslop.studio.data.local.AppSettings(
            hordeApiKey = "my_real_key",
            civitaiApiKey = "my_civ_key",
            safTreeUri = "content://com.example/tree/Primary",
            storageMode = net.hordeslop.studio.domain.StorageMode.SAF,
        )
        val merged = net.hordeslop.studio.data.local.DatabaseBackupCodec.mergeSettings(dto, current)

        // Secrets preserved unchanged
        assertEquals("my_real_key", merged.hordeApiKey)
        assertEquals("my_civ_key", merged.civitaiApiKey)

        // Device-bound storage preserved unchanged
        assertEquals("content://com.example/tree/Primary", merged.safTreeUri)
        assertEquals(net.hordeslop.studio.domain.StorageMode.SAF, merged.storageMode)

        // Portable fields updated
        assertEquals(net.hordeslop.studio.domain.NavLayoutMode.MENU, merged.navLayout)
        assertEquals(3, merged.galleryColumns)
    }

    @Test
    fun settingsBackupV1BundleDecodesWithoutSettings() {
        // A v1 backup (no appSettings field) should decode cleanly with appSettings == null
        val v1Json = """
            {
              "app": "hordeslop-studio",
              "formatVersion": 1,
              "exportedAtMillis": 1000,
              "promptHistory": [],
              "modelDefaults": [],
              "savedImages": [],
              "activeJobs": []
            }
        """.trimIndent()
        val bundle = net.hordeslop.studio.data.local.DatabaseBackupCodec.decodeJson(v1Json)
        assertEquals(1, bundle.formatVersion)
        assertEquals(null, bundle.appSettings)
    }

    @Test
    fun settingsBackupUnknownFieldsAreIgnoredOnImport() {
        // A future backup with extra unknown fields should not throw
        val futureJson = """
            {
              "app": "hordeslop-studio",
              "formatVersion": 2,
              "exportedAtMillis": 2000,
              "appSettings": {
                "navLayout": "bottom",
                "unknownFutureField": true,
                "galleryColumns": 2
              },
              "promptHistory": [],
              "modelDefaults": [],
              "savedImages": [],
              "activeJobs": []
            }
        """.trimIndent()
        val bundle = net.hordeslop.studio.data.local.DatabaseBackupCodec.decodeJson(futureJson)
        assertEquals(2, bundle.formatVersion)
        assertEquals("bottom", bundle.appSettings?.navLayout)
        assertEquals(2, bundle.appSettings?.galleryColumns)
    }

    @Test
    fun settingsBackupInvalidStylesUrlsAreFilteredOnMerge() {
        val dto = net.hordeslop.studio.data.local.AppSettingsBackupDto(
            stylesRepoUrls = listOf("  ", "", "https://valid.example.com/styles.json", "  "),
        )
        val current = net.hordeslop.studio.data.local.AppSettings()
        val merged = net.hordeslop.studio.data.local.DatabaseBackupCodec.mergeSettings(dto, current)
        assertEquals(listOf("https://valid.example.com/styles.json"), merged.stylesRepoUrls)
    }

    @Test
    fun settingsBackupEmptyStylesUrlsFallsBackToDefault() {
        val dto = net.hordeslop.studio.data.local.AppSettingsBackupDto(
            stylesRepoUrls = listOf("  ", ""),
        )
        val current = net.hordeslop.studio.data.local.AppSettings()
        val merged = net.hordeslop.studio.data.local.DatabaseBackupCodec.mergeSettings(dto, current)
        assertEquals(
            listOf(net.hordeslop.studio.domain.HordeConstants.DEFAULT_STYLES_REPO_URL),
            merged.stylesRepoUrls,
        )
    }
}
