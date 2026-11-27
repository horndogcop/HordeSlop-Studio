package net.hordeslop.studio.data.repository

import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.hordeslop.studio.data.local.ActiveJobEntity
import net.hordeslop.studio.data.local.PromptHistoryEntity
import net.hordeslop.studio.domain.GenerationChunk
import net.hordeslop.studio.domain.JobStatus
import net.hordeslop.studio.domain.StudioSubmissionSpec
import net.hordeslop.studio.domain.StyleContractEngine

data class PlannedSubmission(
    val jobs: List<ActiveJobEntity>,
    val historyEntries: List<PromptHistoryEntity>,
)

private data class HistoryDedupeKey(
    val title: String?,
    val prompt: String,
    val negativePrompt: String,
    val modelSummary: String,
    val steps: Int,
    val cfg: Float,
    val clipSkip: Int,
    val sampler: String,
    val scheduler: String,
)

object SubmissionQueueCoordinator {

    fun planStudioSubmission(
        spec: StudioSubmissionSpec,
        nowMillis: Long = System.currentTimeMillis(),
        idGenerator: () -> String = { UUID.randomUUID().toString() },
    ): PlannedSubmission {
        val chunks: List<GenerationChunk> = StyleContractEngine.buildSubmissionChunks(spec)
        val jobs = mutableListOf<ActiveJobEntity>()
        val historyEntries = mutableListOf<PromptHistoryEntity>()
        val seenHistoryKeys = HashSet<HistoryDedupeKey>()

        chunks.forEachIndexed { idx, chunk ->
            val rawPrompt = chunk.payload["prompt"]?.jsonPrimitive?.contentOrNull
            val (resolvedPrompt, resolvedNegative) = splitCombinedPrompt(
                rawCombined = rawPrompt,
                fallbackPrompt = spec.prompt,
                fallbackNegative = spec.negativePrompt,
            )
            val effectiveModels = chunk.models.filter { it.isNotBlank() }
                .ifEmpty { spec.selectedModels.filter { it.isNotBlank() } }
            val resolvedSteps = chunk.steps ?: spec.steps
            val resolvedCfg = chunk.cfg ?: spec.cfg
            val resolvedClipSkip = chunk.clipSkip ?: spec.clipSkip
            val resolvedSampler = chunk.sampler ?: spec.sampler
            val resolvedScheduler = chunk.scheduler ?: spec.scheduler
            val resolvedWidth = chunk.width ?: spec.width
            val resolvedHeight = chunk.height ?: spec.height

            jobs += ActiveJobEntity(
                id = "local_${idGenerator()}",
                requestId = "",
                prompt = resolvedPrompt,
                negativePrompt = resolvedNegative,
                modelsJoined = effectiveModels.joinToString("||"),
                status = JobStatus.LOCAL_QUEUED.name,
                waitTimeSeconds = 0,
                queuePosition = 0,
                finished = 0,
                processing = 0,
                total = chunk.count,
                seed = spec.seed,
                steps = resolvedSteps,
                cfg = resolvedCfg,
                clipSkip = resolvedClipSkip,
                sampler = resolvedSampler,
                scheduler = resolvedScheduler,
                width = resolvedWidth,
                height = resolvedHeight,
                submittedPayloadJson = chunk.payloadJsonString,
                errorMessage = null,
                createdAtMillis = nowMillis + idx,
            )

            val modelSummary = effectiveModels.joinToString(", ")
            val key = HistoryDedupeKey(
                title = chunk.styleName,
                prompt = resolvedPrompt,
                negativePrompt = resolvedNegative,
                modelSummary = modelSummary,
                steps = resolvedSteps,
                cfg = resolvedCfg,
                clipSkip = resolvedClipSkip,
                sampler = resolvedSampler,
                scheduler = resolvedScheduler,
            )
            if (seenHistoryKeys.add(key)) {
                historyEntries += PromptHistoryEntity(
                    id = "h_${nowMillis}_${idGenerator().take(8)}",
                    title = chunk.styleName,
                    prompt = resolvedPrompt,
                    negativePrompt = resolvedNegative,
                    model = modelSummary,
                    steps = resolvedSteps,
                    cfg = resolvedCfg,
                    clipSkip = resolvedClipSkip,
                    sampler = resolvedSampler,
                    scheduler = resolvedScheduler,
                    timestampMillis = nowMillis + historyEntries.size,
                    isFavorite = false,
                )
            }
        }

        return PlannedSubmission(jobs = jobs, historyEntries = historyEntries)
    }

    fun planRawSubmission(
        spec: StudioSubmissionSpec,
        rawPayloadJsons: List<String>,
        json: Json,
        nowMillis: Long = System.currentTimeMillis(),
        idGenerator: () -> String = { UUID.randomUUID().toString() },
    ): PlannedSubmission {
        require(rawPayloadJsons.isNotEmpty()) { "No generation payloads provided" }

        val parsedPayloads = rawPayloadJsons.map { raw ->
            StyleContractEngine.normalizeCensorNsfwPayload(json.parseToJsonElement(raw).jsonObject)
        }
        val jobs = mutableListOf<ActiveJobEntity>()
        val historyEntries = mutableListOf<PromptHistoryEntity>()
        val seenHistoryKeys = HashSet<HistoryDedupeKey>()

        parsedPayloads.forEachIndexed { idx, payload ->
            val modelsFromPayload = payload["models"]?.let { elem ->
                runCatching { elem.jsonArray.mapNotNull { it.jsonPrimitive.contentOrNull } }.getOrNull()
            }?.filter { it.isNotBlank() }?.takeIf { it.isNotEmpty() }
                ?: spec.selectedModels.filter { it.isNotBlank() }

            val paramsObj = payload["params"]?.let { runCatching { it.jsonObject }.getOrNull() }
            val countFromPayload = (paramsObj?.get("n")?.jsonPrimitive?.intOrNull ?: 1).coerceAtLeast(1)
            val rawPrompt = payload["prompt"]?.jsonPrimitive?.contentOrNull
            val (resolvedPrompt, resolvedNegative) = splitCombinedPrompt(
                rawCombined = rawPrompt,
                fallbackPrompt = spec.prompt,
                fallbackNegative = spec.negativePrompt,
            )
            val resolvedSeed = paramsObj?.get("seed")?.jsonPrimitive?.contentOrNull ?: spec.seed
            val resolvedSteps = paramsObj?.get("steps")?.jsonPrimitive?.intOrNull ?: spec.steps
            val resolvedCfg = paramsObj?.get("cfg_scale")?.jsonPrimitive?.floatOrNull ?: spec.cfg
            val resolvedClipSkip = paramsObj?.get("clip_skip")?.jsonPrimitive?.intOrNull ?: spec.clipSkip
            val resolvedSampler = paramsObj?.get("sampler_name")?.jsonPrimitive?.contentOrNull ?: spec.sampler
            val resolvedScheduler = paramsObj?.get("scheduler")?.jsonPrimitive?.contentOrNull ?: spec.scheduler
            val resolvedWidth = paramsObj?.get("width")?.jsonPrimitive?.intOrNull ?: spec.width
            val resolvedHeight = paramsObj?.get("height")?.jsonPrimitive?.intOrNull ?: spec.height
            val styleIdOrName = payload["style"]?.jsonPrimitive?.contentOrNull?.ifBlank { null }

            jobs += ActiveJobEntity(
                id = "local_${idGenerator()}",
                requestId = "",
                prompt = resolvedPrompt,
                negativePrompt = resolvedNegative,
                modelsJoined = modelsFromPayload.joinToString("||"),
                status = JobStatus.LOCAL_QUEUED.name,
                waitTimeSeconds = 0,
                queuePosition = 0,
                finished = 0,
                processing = 0,
                total = countFromPayload,
                seed = resolvedSeed,
                steps = resolvedSteps,
                cfg = resolvedCfg,
                clipSkip = resolvedClipSkip,
                sampler = resolvedSampler,
                scheduler = resolvedScheduler,
                width = resolvedWidth,
                height = resolvedHeight,
                submittedPayloadJson = json.encodeToString(JsonObject.serializer(), payload),
                errorMessage = null,
                createdAtMillis = nowMillis + idx,
            )

            val modelSummary = modelsFromPayload.joinToString(", ")
            val key = HistoryDedupeKey(
                title = styleIdOrName,
                prompt = resolvedPrompt,
                negativePrompt = resolvedNegative,
                modelSummary = modelSummary,
                steps = resolvedSteps,
                cfg = resolvedCfg,
                clipSkip = resolvedClipSkip,
                sampler = resolvedSampler,
                scheduler = resolvedScheduler,
            )
            if (seenHistoryKeys.add(key)) {
                historyEntries += PromptHistoryEntity(
                    id = "h_${nowMillis}_${idGenerator().take(8)}",
                    title = styleIdOrName,
                    prompt = resolvedPrompt,
                    negativePrompt = resolvedNegative,
                    model = modelSummary,
                    steps = resolvedSteps,
                    cfg = resolvedCfg,
                    clipSkip = resolvedClipSkip,
                    sampler = resolvedSampler,
                    scheduler = resolvedScheduler,
                    timestampMillis = nowMillis + historyEntries.size,
                    isFavorite = false,
                )
            }
        }

        return PlannedSubmission(jobs = jobs, historyEntries = historyEntries)
    }

    suspend fun persistAtomically(
        plan: PlannedSubmission,
        runInTransaction: suspend (suspend () -> Unit) -> Unit,
        insertJobs: suspend (List<ActiveJobEntity>) -> Unit,
        insertHistory: suspend (List<PromptHistoryEntity>) -> Unit,
    ): List<ActiveJobEntity> {
        require(plan.jobs.isNotEmpty()) { "Cannot persist submission without queued jobs" }
        runInTransaction {
            insertJobs(plan.jobs)
            if (plan.historyEntries.isNotEmpty()) {
                insertHistory(plan.historyEntries)
            }
        }
        return plan.jobs
    }

    private fun splitCombinedPrompt(
        rawCombined: String?,
        fallbackPrompt: String,
        fallbackNegative: String,
    ): Pair<String, String> {
        if (rawCombined == null) {
            return fallbackPrompt to fallbackNegative
        }
        val delimIdx = rawCombined.indexOf("###")
        return if (delimIdx >= 0) {
            val pos = rawCombined.substring(0, delimIdx).trim().ifEmpty { fallbackPrompt }
            val neg = rawCombined.substring(delimIdx + 3).trim()
            pos to neg
        } else {
            val pos = rawCombined.trim().ifEmpty { fallbackPrompt }
            pos to ""
        }
    }
}
