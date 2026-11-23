package net.hordecraft.android.data.repository

import android.content.Context
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.put
import net.hordecraft.android.data.local.ActiveJobDao
import net.hordecraft.android.data.local.ActiveJobEntity
import net.hordecraft.android.data.local.AppSettings
import net.hordecraft.android.data.local.ModelDefaultsDao
import net.hordecraft.android.data.local.ModelDefaultsEntity
import net.hordecraft.android.data.local.PromptHistoryDao
import net.hordecraft.android.data.local.PromptHistoryEntity
import net.hordecraft.android.data.local.SavedImageDao
import net.hordecraft.android.data.local.SavedImageEntity
import net.hordecraft.android.data.local.SettingsDataStore
import net.hordecraft.android.data.local.WebpStorageManager
import net.hordecraft.android.data.remote.CivitaiApiService
import net.hordecraft.android.data.remote.CivitaiModelDto
import net.hordecraft.android.data.remote.GeneratedImageDto
import net.hordecraft.android.data.remote.GenerationCheckDto
import net.hordecraft.android.data.remote.GenerationStatusDto
import net.hordecraft.android.data.remote.HordeApiService
import net.hordecraft.android.data.remote.ModelReferenceRepository
import net.hordecraft.android.domain.AttachedLora
import net.hordecraft.android.domain.AttachedTi
import net.hordecraft.android.domain.AuthMode
import net.hordecraft.android.domain.CivitaiPreview
import net.hordecraft.android.domain.CivitaiVersion
import net.hordecraft.android.domain.GenerationChunk
import net.hordecraft.android.domain.HordeConstants
import net.hordecraft.android.domain.HordeJob
import net.hordecraft.android.domain.HordeModel
import net.hordecraft.android.domain.HordeStyle
import net.hordecraft.android.domain.JobStatus
import net.hordecraft.android.domain.PromptRecord
import net.hordecraft.android.domain.SavedImage
import net.hordecraft.android.domain.StudioSubmissionSpec
import net.hordecraft.android.domain.StyleContractEngine
import net.hordecraft.android.domain.TiInjectMode
import net.hordecraft.android.domain.UserStatsSummary
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.UUID

class HordecraftRepository(
    private val context: Context,
    val settingsStore: SettingsDataStore,
    private val promptHistoryDao: PromptHistoryDao,
    private val modelDefaultsDao: ModelDefaultsDao,
    private val savedImageDao: SavedImageDao,
    private val activeJobDao: ActiveJobDao,
    private val hordeApi: HordeApiService,
    private val civitaiApi: CivitaiApiService,
    private val modelRefRepo: ModelReferenceRepository,
    val storageManager: WebpStorageManager,
    private val downloadOkHttpClient: OkHttpClient,
    private val json: Json,
) {
    val settingsFlow: Flow<AppSettings> = settingsStore.settings

    val historyFlow: Flow<List<PromptRecord>> = promptHistoryDao.observeAll().map { list ->
        list.map { e ->
            PromptRecord(
                id = e.id,
                title = e.title,
                prompt = e.prompt,
                negativePrompt = e.negativePrompt,
                model = e.model,
                steps = e.steps,
                cfg = e.cfg,
                clipSkip = e.clipSkip,
                sampler = e.sampler,
                scheduler = e.scheduler,
                timestampMillis = e.timestampMillis,
                isFavorite = e.isFavorite,
            )
        }
    }

    val modelDefaultsFlow: Flow<Map<String, ModelDefaultsEntity>> =
        modelDefaultsDao.observeAll().map { list -> list.associateBy { it.modelName } }

    val savedImagesFlow: Flow<List<SavedImage>> = savedImageDao.observeAll().map { list ->
        list.map { e ->
            SavedImage(
                id = e.id,
                requestId = e.requestId,
                generationId = e.generationId,
                fileUri = e.fileUri,
                prompt = e.prompt,
                negativePrompt = e.negativePrompt,
                model = e.model,
                seed = e.seed,
                steps = e.steps,
                cfg = e.cfg,
                clipSkip = e.clipSkip,
                sampler = e.sampler,
                scheduler = e.scheduler,
                width = e.width,
                height = e.height,
                destinationLabel = e.destinationLabel,
                isFavorite = e.isFavorite,
                partialFromCancel = e.partialFromCancel,
                requestJson = e.requestJson,
                statusMetadataJson = e.statusMetadataJson,
                createdAtMillis = e.createdAtMillis,
            )
        }
    }

    val activeJobsFlow: Flow<List<HordeJob>> = activeJobDao.observeAll().map { list ->
        list.map { e ->
            HordeJob(
                id = e.id,
                requestId = e.requestId,
                prompt = e.prompt,
                negativePrompt = e.negativePrompt,
                models = e.modelsJoined.split("||").filter { it.isNotBlank() },
                status = runCatching { JobStatus.valueOf(e.status) }.getOrDefault(JobStatus.QUEUED),
                waitTimeSeconds = e.waitTimeSeconds,
                queuePosition = e.queuePosition,
                finished = e.finished,
                processing = e.processing,
                total = e.total,
                submittedPayloadJson = e.submittedPayloadJson,
                errorMessage = e.errorMessage,
            )
        }
    }

    suspend fun fetchModels(): List<HordeModel> {
        val savedDefaults = modelDefaultsFlow.first()
        val refMap = modelRefRepo.loadModelReference()
        val live = runCatching { hordeApi.getModels("image") }.getOrDefault(emptyList())
        return modelRefRepo.mergeModels(live, refMap, savedDefaults)
    }

    suspend fun fetchStyles(): List<HordeStyle> {
        val rawStyle = HordeStyle(id = "none", name = "Raw Prompt", source = "None")
        val apiStyles = runCatching { hordeApi.getImageStyles(page = 1) }
            .getOrDefault(emptyList())
            .map(modelRefRepo::mapApiStyle)
        val merged = apiStyles.ifEmpty { modelRefRepo.loadFallbackGitHubStyles() }
        return listOf(rawStyle) + merged.sortedBy { it.name.lowercase() }
    }

    suspend fun fetchUserStats(): UserStatsSummary {
        val key = settingsStore.settings.first().hordeApiKey
        return when (val mode = AuthMode.inferFromKey(key)) {
            AuthMode.ANONYMOUS -> UserStatsSummary(authMode = AuthMode.ANONYMOUS)
            AuthMode.SHARED_KEY -> {
                val dto = hordeApi.getSharedKey(key)
                UserStatsSummary(
                    authMode = AuthMode.SHARED_KEY,
                    sharedKeyName = dto.name,
                    sharedKeyRemainingKudos = (dto.kudos - dto.utilized).coerceAtLeast(0L),
                    sharedKeyMaxPixels = dto.maxImagePixels,
                    sharedKeyMaxSteps = dto.maxImageSteps,
                    sharedKeyExpiry = dto.expiry,
                )
            }
            AuthMode.USER_KEY -> {
                val dto = hordeApi.findUser()
                UserStatsSummary(
                    authMode = AuthMode.USER_KEY,
                    username = dto.username,
                    trusted = dto.trusted,
                    kudos = dto.kudos,
                    recordsRequests = dto.records?.usage?.requests ?: 0L,
                    recordsMegapixels = (dto.records?.usage?.megapixelsteps ?: 0.0) / 30.0,
                    workerCount = dto.workerCount,
                    kudosEarnedWorkers = dto.records?.contribution?.megapixelsteps ?: 0.0,
                )
            }
        }
    }

    suspend fun searchCivitaiLoras(
        query: String,
        allModels: Boolean,
        selectedGroupIds: Set<String>,
    ): List<AttachedLora> {
        val apiKey = settingsStore.settings.first().civitaiApiKey
        val auth = apiKey.takeIf { it.isNotBlank() }?.let { "Bearer $it" }
        val trimmed = query.trim()
        val baseModels = if (allModels || selectedGroupIds.isEmpty()) {
            null
        } else {
            HordeConstants.CIVITAI_LORA_GROUPS
                .filter { it.id in selectedGroupIds }
                .flatMap { it.apiValues }
                .distinct()
                .ifEmpty { null }
        }

        val pinned = mutableListOf<AttachedLora>()
        val numericId = trimmed.toLongOrNull()
        if (numericId != null) {
            runCatching {
                val dto = civitaiApi.getModelById(auth, numericId)
                if (dto.type.equals("LORA", ignoreCase = true) ||
                    dto.type.equals("LoCon", ignoreCase = true) ||
                    dto.type.equals("DoRA", ignoreCase = true)
                ) {
                    mapCivitaiLoraDto(dto)?.let(pinned::add)
                }
            }
            if (pinned.isEmpty()) {
                runCatching {
                    val v = civitaiApi.getModelVersionById(auth, numericId)
                    if (v.model?.type.equals("LORA", ignoreCase = true) ||
                        v.model?.type.equals("LoCon", ignoreCase = true) ||
                        v.model?.type.equals("DoRA", ignoreCase = true)
                    ) {
                        val parentId = v.modelId ?: numericId
                        val parentDto = runCatching { civitaiApi.getModelById(auth, parentId) }.getOrNull()
                        if (parentDto != null) {
                            mapCivitaiLoraDto(parentDto, preferredVersionId = v.id)?.let(pinned::add)
                        }
                    }
                }
            }
        }

        val searched = runCatching {
            civitaiApi.searchModels(
                authHeader = auth,
                query = trimmed.ifEmpty { null },
                type = "LORA",
                baseModels = baseModels,
            ).items.mapNotNull(::mapCivitaiLoraDto)
        }.getOrDefault(emptyList())

        val combined = LinkedHashMap<Long, AttachedLora>()
        pinned.forEach { combined[it.modelId] = it }
        searched.forEach { combined.putIfAbsent(it.modelId, it) }
        return combined.values.toList()
    }

    suspend fun searchCivitaiTis(
        query: String,
        allModels: Boolean,
        selectedGroupIds: Set<String>,
    ): List<AttachedTi> {
        val apiKey = settingsStore.settings.first().civitaiApiKey
        val auth = apiKey.takeIf { it.isNotBlank() }?.let { "Bearer $it" }
        val trimmed = query.trim()
        val allowedTiBases = HordeConstants.CIVITAI_TI_GROUPS.flatMap { it.apiValues }
        val baseModels = if (allModels || selectedGroupIds.isEmpty()) {
            null
        } else {
            HordeConstants.CIVITAI_TI_GROUPS
                .filter { it.id in selectedGroupIds }
                .flatMap { it.apiValues }
                .distinct()
                .ifEmpty { null }
        }

        val pinned = mutableListOf<AttachedTi>()
        val numericId = trimmed.toLongOrNull()
        if (numericId != null) {
            runCatching {
                val dto = civitaiApi.getModelById(auth, numericId)
                if (dto.type.equals("TextualInversion", ignoreCase = true)) {
                    mapCivitaiTiDto(dto, allowedTiBases)?.let(pinned::add)
                }
            }
            if (pinned.isEmpty()) {
                runCatching {
                    val v = civitaiApi.getModelVersionById(auth, numericId)
                    if (v.model?.type.equals("TextualInversion", ignoreCase = true)) {
                        val parentId = v.modelId ?: numericId
                        val parentDto = runCatching { civitaiApi.getModelById(auth, parentId) }.getOrNull()
                        if (parentDto != null) {
                            mapCivitaiTiDto(parentDto, allowedTiBases, preferredVersionId = v.id)?.let(pinned::add)
                        }
                    }
                }
            }
        }

        val searched = runCatching {
            civitaiApi.searchModels(
                authHeader = auth,
                query = trimmed.ifEmpty { null },
                type = "TextualInversion",
                baseModels = baseModels ?: allowedTiBases,
            ).items.mapNotNull { mapCivitaiTiDto(it, allowedTiBases) }
        }.getOrDefault(emptyList())

        val combined = LinkedHashMap<Long, AttachedTi>()
        pinned.forEach { combined[it.modelId] = it }
        searched.forEach { combined.putIfAbsent(it.modelId, it) }
        return combined.values.toList()
    }

    private fun mapCivitaiLoraDto(dto: CivitaiModelDto, preferredVersionId: Long? = null): AttachedLora? {
        val versions = dto.modelVersions.map {
            CivitaiVersion(
                id = it.id,
                name = "${it.name} (${it.baseModel})",
                baseModel = it.baseModel,
                trainedWords = it.trainedWords.filter { w -> w.isNotBlank() },
            )
        }
        val activeVersionDto = preferredVersionId?.let { vid ->
            dto.modelVersions.firstOrNull { it.id == vid }
        } ?: dto.modelVersions.firstOrNull() ?: return null

        val previews = activeVersionDto.images.mapNotNull { img ->
            if (img.url.isBlank()) return@mapNotNull null
            val mature = (img.nsfwLevel ?: 0) > 2 ||
                img.nsfw.equals("Soft", true) ||
                img.nsfw.equals("Mature", true) ||
                img.nsfw.equals("X", true) ||
                dto.nsfw
            CivitaiPreview(
                url = img.url,
                isMature = mature,
                width = img.width,
                height = img.height,
            )
        }
        return AttachedLora(
            id = "lora_${dto.id}_${activeVersionDto.id}",
            modelId = dto.id,
            name = dto.name,
            versionName = activeVersionDto.name,
            modelVersionId = activeVersionDto.id,
            strengthModel = 0.75f,
            strengthClip = 0.75f,
            baseModel = activeVersionDto.baseModel,
            trainedWords = activeVersionDto.trainedWords.filter { it.isNotBlank() },
            versions = versions,
            previews = previews,
        )
    }

    private fun mapCivitaiTiDto(
        dto: CivitaiModelDto,
        allowedBases: List<String>,
        preferredVersionId: Long? = null,
    ): AttachedTi? {
        val activeVersion = preferredVersionId?.let { vid ->
            dto.modelVersions.firstOrNull { it.id == vid }
        } ?: dto.modelVersions.firstOrNull { v ->
            allowedBases.any { b -> b.equals(v.baseModel, ignoreCase = true) }
        } ?: dto.modelVersions.firstOrNull() ?: return null

        return AttachedTi(
            id = "ti_${dto.id}_${activeVersion.id}",
            modelId = dto.id,
            modelVersionId = activeVersion.id,
            name = dto.name,
            versionLabel = "${activeVersion.name} (${activeVersion.baseModel})",
            strength = 1.0f,
            injectTi = TiInjectMode.NEGPROMPT,
            baseModel = activeVersion.baseModel,
        )
    }

    suspend fun saveModelDefaults(
        modelName: String,
        steps: Int,
        sampler: String,
        scheduler: String,
        clipSkip: Int,
    ) {
        modelDefaultsDao.upsert(
            ModelDefaultsEntity(
                modelName = modelName,
                steps = steps,
                sampler = sampler,
                scheduler = scheduler,
                clipSkip = clipSkip,
            ),
        )
    }

    suspend fun resetModelDefaults(modelName: String) {
        modelDefaultsDao.deleteByModelName(modelName)
    }

    suspend fun submitGeneration(spec: StudioSubmissionSpec): List<ActiveJobEntity> {
        promptHistoryDao.upsert(
            PromptHistoryEntity(
                id = "h_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}",
                title = null,
                prompt = spec.prompt,
                negativePrompt = spec.negativePrompt,
                model = spec.selectedModels.firstOrNull().orEmpty(),
                steps = spec.steps,
                cfg = spec.cfg,
                clipSkip = spec.clipSkip,
                sampler = spec.sampler,
                scheduler = spec.scheduler,
                timestampMillis = System.currentTimeMillis(),
                isFavorite = false,
            ),
        )

        val chunks: List<GenerationChunk> = StyleContractEngine.buildSubmissionChunks(spec)
        val created = mutableListOf<ActiveJobEntity>()
        for (chunk in chunks) {
            val resp = hordeApi.submitAsync(chunk.payload)
            val entity = ActiveJobEntity(
                id = resp.id,
                requestId = resp.id,
                prompt = spec.prompt,
                negativePrompt = spec.negativePrompt,
                modelsJoined = chunk.models.joinToString("||"),
                status = JobStatus.QUEUED.name,
                waitTimeSeconds = 15,
                queuePosition = 0,
                finished = 0,
                processing = 0,
                total = chunk.count,
                seed = spec.seed,
                steps = spec.steps,
                cfg = spec.cfg,
                clipSkip = spec.clipSkip,
                sampler = spec.sampler,
                scheduler = spec.scheduler,
                width = spec.width,
                height = spec.height,
                submittedPayloadJson = chunk.payloadJsonString,
                errorMessage = null,
                createdAtMillis = System.currentTimeMillis(),
            )
            activeJobDao.upsert(entity)
            created += entity
        }
        return created
    }

    suspend fun enqueueReroll(image: SavedImage): ActiveJobEntity {
        val parsedReq = runCatching {
            json.parseToJsonElement(image.requestJson).jsonObject
        }.getOrNull()

        val cleanedParams = buildJsonObject {
            val existingParams = parsedReq?.get("params") as? JsonObject
            existingParams?.forEach { (k, v) ->
                if (k != "seed") put(k, v)
            }
            put("n", 1)
        }
        val payload = buildJsonObject {
            if (parsedReq != null) {
                parsedReq.forEach { (k, v) ->
                    if (k == "params") put("params", cleanedParams) else put(k, v)
                }
            } else {
                val combined = if (image.negativePrompt.isBlank()) image.prompt
                else "${image.prompt} ### ${image.negativePrompt}"
                put("prompt", combined)
                put("models", kotlinx.serialization.json.JsonArray(listOf(kotlinx.serialization.json.JsonPrimitive(image.model))))
                put("params", cleanedParams)
            }
        }
        val payloadStr = Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), payload)
        val resp = hordeApi.submitAsync(payload)
        val entity = ActiveJobEntity(
            id = resp.id,
            requestId = resp.id,
            prompt = image.prompt,
            negativePrompt = image.negativePrompt,
            modelsJoined = image.model,
            status = JobStatus.QUEUED.name,
            waitTimeSeconds = 12,
            queuePosition = 0,
            finished = 0,
            processing = 0,
            total = 1,
            seed = "",
            steps = image.steps,
            cfg = image.cfg,
            clipSkip = image.clipSkip,
            sampler = image.sampler,
            scheduler = image.scheduler,
            width = image.width,
            height = image.height,
            submittedPayloadJson = payloadStr,
            errorMessage = null,
            createdAtMillis = System.currentTimeMillis(),
        )
        activeJobDao.upsert(entity)
        return entity
    }

    suspend fun checkJobProgress(jobId: String): GenerationCheckDto = hordeApi.checkGeneration(jobId)

    suspend fun fetchFullStatusAndSave(job: ActiveJobEntity, partialFromCancel: Boolean = false): Int {
        val statusDto: GenerationStatusDto = hordeApi.getGenerationStatus(job.id)
        return persistGenerationsFromStatus(job, statusDto, partialFromCancel)
    }

    suspend fun cancelJobWithPartialRecovery(jobId: String): Int {
        val job = activeJobDao.findById(jobId) ?: return 0
        val statusDto = hordeApi.cancelGeneration(jobId)
        val savedCount = persistGenerationsFromStatus(job, statusDto, partialFromCancel = true)
        activeJobDao.upsert(
            job.copy(
                status = if (savedCount > 0) JobStatus.CANCELLED_PARTIAL.name else JobStatus.FAILED.name,
                finished = savedCount,
                processing = 0,
                waitTimeSeconds = 0,
                errorMessage = if (savedCount == 0) "Cancelled" else null,
            ),
        )
        return savedCount
    }

    suspend fun persistGenerationsFromStatus(
        job: ActiveJobEntity,
        statusDto: GenerationStatusDto,
        partialFromCancel: Boolean,
    ): Int {
        val settings = settingsStore.settings.first()
        var newlySaved = 0
        for (gen: GeneratedImageDto in statusDto.generations) {
            if (gen.img.isBlank()) continue
            val existing = savedImageDao.findByGenerationId(gen.id)
            if (existing != null) continue

            val rawBytes = downloadGenerationBytes(gen.img) ?: continue
            val stored = storageManager.saveGeneratedWebp(
                rawBytes = rawBytes,
                generationId = gen.id,
                requestId = job.requestId,
                prompt = job.prompt,
                negativePrompt = job.negativePrompt,
                model = gen.model.ifBlank { job.modelsJoined.split("||").firstOrNull().orEmpty() },
                seed = gen.seed,
                steps = job.steps,
                cfg = job.cfg,
                clipSkip = job.clipSkip,
                sampler = job.sampler,
                scheduler = job.scheduler,
                width = job.width,
                height = job.height,
                settings = settings,
            )

            val statusMeta = json.encodeToString(GeneratedImageDto.serializer(), gen)
            savedImageDao.upsert(
                SavedImageEntity(
                    id = "img_${gen.id}",
                    requestId = job.requestId,
                    generationId = gen.id,
                    fileUri = stored.uriString,
                    prompt = job.prompt,
                    negativePrompt = job.negativePrompt,
                    model = gen.model.ifBlank { job.modelsJoined.split("||").firstOrNull().orEmpty() },
                    seed = gen.seed,
                    steps = job.steps,
                    cfg = job.cfg,
                    clipSkip = job.clipSkip,
                    sampler = job.sampler,
                    scheduler = job.scheduler,
                    width = job.width,
                    height = job.height,
                    destinationLabel = stored.destinationLabel,
                    isFavorite = false,
                    partialFromCancel = partialFromCancel,
                    requestJson = job.submittedPayloadJson,
                    statusMetadataJson = statusMeta,
                    createdAtMillis = System.currentTimeMillis(),
                ),
            )
            newlySaved++
        }
        return newlySaved
    }

    private suspend fun downloadGenerationBytes(imgField: String): ByteArray? = withContext(Dispatchers.IO) {
        if (imgField.startsWith("http://") || imgField.startsWith("https://")) {
            val req = Request.Builder().url(imgField).build()
            downloadOkHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                return@withContext resp.body?.bytes()
            }
        }
        runCatching { Base64.decode(imgField, Base64.DEFAULT) }.getOrNull()
    }

    suspend fun toggleHistoryFavorite(id: String, fav: Boolean) = promptHistoryDao.setFavorite(id, fav)
    suspend fun toggleImageFavorite(id: String, fav: Boolean) = savedImageDao.setFavorite(id, fav)

    suspend fun deleteSavedImage(image: SavedImage, deleteBackingFile: Boolean) {
        savedImageDao.deleteById(image.id)
        if (deleteBackingFile) {
            storageManager.deleteBackedFile(image.fileUri)
        }
    }

    suspend fun pauseQueue() = activeJobDao.pauseAllActive()
    suspend fun resumeQueue() = activeJobDao.resumeAllPaused()

    suspend fun getPendingJobs(): List<ActiveJobEntity> = activeJobDao.getPendingOrActive()

    suspend fun updateJobRecord(job: ActiveJobEntity) = activeJobDao.upsert(job)
}
