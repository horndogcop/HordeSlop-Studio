package net.hordeslop.studio.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull

object LiveGenerationParser {
    private val prettyJson = Json { prettyPrint = true }

    fun parse(
        requestId: String,
        requestJson: JsonObject?,
        check: GenerationStatusSnapshot? = null,
        isTrackedLocally: Boolean = false,
        requestLookupError: String? = null,
    ): LiveRemoteGeneration {
        val rawPrompt = requestJson?.stringOrNull("prompt")
        val promptOmittedLegacy = requestJson != null && rawPrompt == null
        val prompt: String
        val negativePrompt: String
        if (rawPrompt != null) {
            val splitIdx = rawPrompt.indexOf("###")
            if (splitIdx >= 0) {
                prompt = rawPrompt.substring(0, splitIdx).trim()
                negativePrompt = rawPrompt.substring(splitIdx + 3).trim()
            } else {
                prompt = rawPrompt.trim()
                negativePrompt = ""
            }
        } else {
            prompt = ""
            negativePrompt = ""
        }

        val models = requestJson?.arrayOrNull("models")
            ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim()?.ifBlank { null } }
            ?: emptyList()

        val paramsObj = requestJson?.objectOrNull("params")
        val steps = paramsObj?.intOrNull("steps")
        val cfg = paramsObj?.floatOrNull("cfg_scale")
        val clipSkip = paramsObj?.intOrNull("clip_skip")
        val sampler = paramsObj?.stringOrNull("sampler_name")
        val scheduler = paramsObj?.stringOrNull("scheduler")
            ?: if (paramsObj?.boolOrNull("karras") == true) "karras" else null
        val width = paramsObj?.intOrNull("width")
        val height = paramsObj?.intOrNull("height")
        val seed = paramsObj?.stringOrNull("seed")?.ifBlank { null }
        val hiresFix = paramsObj?.boolOrNull("hires_fix")

        val checkTotal = check?.let { it.finished + it.processing + it.waiting } ?: 0
        val imageCount = (paramsObj?.intOrNull("n") ?: checkTotal).coerceAtLeast(1)

        val loras = paramsObj?.arrayOrNull("loras")?.mapNotNull(::formatLoraEntry) ?: emptyList()
        val tis = paramsObj?.arrayOrNull("tis")?.mapNotNull(::formatTiEntry) ?: emptyList()

        val formattedJson = if (requestJson != null) {
            prettyJson.encodeToString(JsonObject.serializer(), requestJson)
        } else {
            "{}"
        }

        return LiveRemoteGeneration(
            requestId = requestId,
            prompt = prompt,
            negativePrompt = negativePrompt,
            promptOmittedLegacy = promptOmittedLegacy,
            models = models,
            steps = steps,
            cfg = cfg,
            clipSkip = clipSkip,
            sampler = sampler,
            scheduler = scheduler,
            width = width,
            height = height,
            seed = seed,
            imageCount = imageCount,
            hiresFix = hiresFix,
            nsfw = requestJson?.boolOrNull("nsfw"),
            censorNsfw = requestJson?.boolOrNull("censor_nsfw"),
            shared = requestJson?.boolOrNull("shared"),
            r2 = requestJson?.boolOrNull("r2"),
            trustedWorkers = requestJson?.boolOrNull("trusted_workers"),
            slowWorkers = requestJson?.boolOrNull("slow_workers"),
            sourceProcessing = requestJson?.stringOrNull("source_processing"),
            loraNames = loras,
            tiNames = tis,
            finished = check?.finished ?: 0,
            processing = check?.processing ?: 0,
            waiting = check?.waiting ?: 0,
            done = check?.done ?: false,
            faulted = check?.faulted ?: false,
            isPossible = check?.isPossible ?: true,
            queuePosition = check?.queuePosition ?: 0,
            waitTimeSeconds = check?.waitTimeSeconds ?: 0,
            kudos = check?.kudos ?: 0.0,
            isTrackedLocally = isTrackedLocally,
            rawRequestJson = formattedJson,
            requestLookupError = requestLookupError,
        )
    }

    fun summarizeStatus(item: LiveRemoteGeneration): String = when {
        item.faulted -> "Faulted"
        item.done -> "Done (${item.finished}/${item.imageCount})"
        !item.isPossible -> "No eligible workers"
        item.processing > 0 -> "Processing (${item.finished}/${item.imageCount})"
        item.queuePosition > 0 -> "Queued #${item.queuePosition} · ~${item.waitTimeSeconds}s"
        else -> "Waiting · ~${item.waitTimeSeconds}s"
    }

    fun summarizeParams(item: LiveRemoteGeneration): String {
        val parts = buildList {
            if (item.width != null && item.height != null) add("${item.width}x${item.height}")
            item.steps?.let { add("$it steps") }
            item.cfg?.let { add("CFG $it") }
            item.sampler?.let { add(it) }
            item.scheduler?.let { add(it) }
            if (item.imageCount > 1) add("${item.imageCount} imgs")
            if (item.loraNames.isNotEmpty()) add("${item.loraNames.size} LoRA")
            if (item.nsfw == true) add("NSFW")
        }
        return if (parts.isEmpty()) "Parameters unavailable" else parts.joinToString(" · ")
    }

    private fun formatLoraEntry(element: JsonElement): String? {
        val obj = element as? JsonObject
        if (obj != null) {
            val name = obj.stringOrNull("name") ?: return null
            val weight = obj.floatOrNull("model")
            return if (weight != null) "$name ($weight)" else name
        }
        return (element as? JsonPrimitive)?.contentOrNull?.trim()?.ifBlank { null }
    }

    private fun formatTiEntry(element: JsonElement): String? {
        val obj = element as? JsonObject
        if (obj != null) {
            val name = obj.stringOrNull("name") ?: return null
            val strength = obj.floatOrNull("strength")
            return if (strength != null) "$name ($strength)" else name
        }
        return (element as? JsonPrimitive)?.contentOrNull?.trim()?.ifBlank { null }
    }

    private fun JsonObject.objectOrNull(key: String): JsonObject? = this[key] as? JsonObject
    private fun JsonObject.arrayOrNull(key: String): JsonArray? = this[key] as? JsonArray
    private fun JsonObject.stringOrNull(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.intOrNull(key: String): Int? =
        (this[key] as? JsonPrimitive)?.intOrNull

    private fun JsonObject.floatOrNull(key: String): Float? =
        (this[key] as? JsonPrimitive)?.floatOrNull

    private fun JsonObject.boolOrNull(key: String): Boolean? =
        (this[key] as? JsonPrimitive)?.booleanOrNull
}
