package net.hordeslop.studio.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI
import java.net.URLDecoder
import java.util.Base64

data class ParsedGenerationUrl(
    val sourceUrl: String,
    val prompt: String? = null,
    val negativePrompt: String? = null,
    val modelName: String? = null,
    val rawModelLabel: String? = null,
    val steps: Int? = null,
    val cfg: Float? = null,
    val clipSkip: Int? = null,
    val sampler: String? = null,
    val scheduler: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val seed: String? = null,
    val imageCount: Int? = null,
    val nsfw: Boolean? = null,
    val censorNsfw: Boolean? = null,
)

object GenerationUrlParser {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val SIZE_REGEX = Regex("""(\d+)\s*[xX×]\s*(\d+)""")
    private val URL_EXTRACT_REGEX = Regex("""https?://[^\s]+""", RegexOption.IGNORE_CASE)

    fun parse(rawInput: String, availableModels: List<HordeModel> = emptyList()): ParsedGenerationUrl? {
        val candidate = extractUrlCandidate(rawInput) ?: return null
        val uri = runCatching { URI(candidate) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") return null

        val params = LinkedHashMap<String, String>()
        uri.rawQuery?.let { parseKeyValues(it, params) }
        uri.rawFragment?.let { frag ->
            val qIdx = frag.indexOf('?')
            if (qIdx >= 0) {
                parseKeyValues(frag.substring(qIdx + 1), params)
            } else if (frag.contains('=')) {
                parseKeyValues(frag.trimStart('/'), params)
            }
        }

        // Unpack embedded JSON/base64 payload from share/params/state if present
        listOf("params", "share", "state", "data").forEach { blobKey ->
            params[blobKey]?.let { rawBlob ->
                decodeEmbeddedJsonParams(rawBlob, params)
            }
        }

        var rawPrompt = firstNonBlank(params, "prompt", "p")
        var rawNeg = firstNonBlank(params, "negative_prompt", "negativePrompt", "negative", "np")
        if (rawPrompt != null && rawNeg == null && rawPrompt.contains("###")) {
            val idx = rawPrompt.indexOf("###")
            val splitPos = rawPrompt.substring(0, idx).trim()
            val splitNeg = rawPrompt.substring(idx + 3).trim()
            rawPrompt = splitPos
            rawNeg = splitNeg
        }

        val rawModel = extractModelName(params)
        val matchedModel = rawModel?.let { rm ->
            availableModels.firstOrNull { it.name.equals(rm, ignoreCase = true) }?.name
                ?: availableModels.firstOrNull {
                    it.name.contains(rm, ignoreCase = true) || rm.contains(it.name, ignoreCase = true)
                }?.name
                ?: rm
        }

        val steps = firstNonBlank(params, "steps")?.toIntOrNull()?.coerceIn(1, 100)
        val cfg = firstNonBlank(params, "cfg_scale", "cfgScale", "cfg")?.toFloatOrNull()?.coerceIn(1f, 30f)
        val clipSkip = firstNonBlank(params, "clip_skip", "clipSkip")?.toIntOrNull()?.coerceIn(1, 12)
        val rawSampler = firstNonBlank(params, "sampler_name", "samplerName", "sampler")
        val (mappedSampler, impliedScheduler) = CivitaiImageParser.mapCivitaiSampler(rawSampler)
        val sampler = mappedSampler ?: rawSampler
        val karrasFlag = firstNonBlank(params, "karras")?.lowercase()?.toBooleanStrictOrNull()
        val scheduler = firstNonBlank(params, "scheduler")
            ?: impliedScheduler
            ?: if (karrasFlag == true) "karras" else null

        val sizeMatch = firstNonBlank(params, "size")?.let { SIZE_REGEX.find(it) }
        val parsedW = (firstNonBlank(params, "width")?.toIntOrNull() ?: sizeMatch?.groupValues?.getOrNull(1)?.toIntOrNull())
            ?.takeIf { it > 0 }?.let(SizeMathEngine::snapTo64)
        val parsedH = (firstNonBlank(params, "height")?.toIntOrNull() ?: sizeMatch?.groupValues?.getOrNull(2)?.toIntOrNull())
            ?.takeIf { it > 0 }?.let(SizeMathEngine::snapTo64)

        val seed = firstNonBlank(params, "seed")?.takeIf { it != "null" }
        val count = firstNonBlank(params, "n", "image_count", "count")?.toIntOrNull()?.coerceIn(1, 100)
        val nsfw = firstNonBlank(params, "nsfw")?.lowercase()?.toBooleanStrictOrNull()
        val censorNsfw = firstNonBlank(params, "censor_nsfw", "censorNsfw")?.lowercase()?.toBooleanStrictOrNull()

        val hasAnyGenerationField = listOfNotNull(
            rawPrompt,
            rawNeg,
            rawModel,
            steps,
            cfg,
            clipSkip,
            sampler,
            scheduler,
            parsedW,
            parsedH,
            seed,
            count,
        ).isNotEmpty()

        if (!hasAnyGenerationField) return null

        return ParsedGenerationUrl(
            sourceUrl = candidate,
            prompt = rawPrompt,
            negativePrompt = rawNeg,
            modelName = matchedModel,
            rawModelLabel = rawModel,
            steps = steps,
            cfg = cfg,
            clipSkip = clipSkip,
            sampler = sampler,
            scheduler = scheduler,
            width = parsedW,
            height = parsedH,
            seed = seed,
            imageCount = count,
            nsfw = nsfw,
            censorNsfw = censorNsfw,
        )
    }

    private fun extractUrlCandidate(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        return URL_EXTRACT_REGEX.find(trimmed)?.value ?: trimmed
    }

    private fun parseKeyValues(query: String, target: MutableMap<String, String>) {
        if (query.isBlank()) return
        query.split('&').forEach { pair ->
            if (pair.isBlank()) return@forEach
            val eqIdx = pair.indexOf('=')
            val rawKey = if (eqIdx >= 0) pair.substring(0, eqIdx) else pair
            val rawVal = if (eqIdx >= 0) pair.substring(eqIdx + 1) else ""
            val key = decodePart(rawKey).trim()
            val value = decodePart(rawVal).trim()
            if (key.isNotEmpty() && !target.containsKey(key)) {
                target[key] = value
            }
        }
    }

    private fun decodePart(raw: String): String =
        runCatching { URLDecoder.decode(raw, Charsets.UTF_8.name()) }.getOrDefault(raw)

    private fun decodeEmbeddedJsonParams(raw: String, target: MutableMap<String, String>) {
        val obj = parseJsonObjectCandidate(raw)
            ?: decodeBase64Candidate(raw)?.let(::parseJsonObjectCandidate)
            ?: return
        for ((k, elem) in obj) {
            when (elem) {
                is JsonObject -> {
                    if (k.equals("params", ignoreCase = true)) {
                        for ((pk, pv) in elem) {
                            val str = runCatching { pv.jsonPrimitive.contentOrNull }.getOrNull()
                            if (!str.isNullOrBlank() && !target.containsKey(pk)) {
                                target[pk] = str
                            }
                        }
                    }
                }
                is JsonArray -> {
                    if (k.equals("models", ignoreCase = true)) {
                        val firstModel = elem.firstOrNull()?.let {
                            runCatching { it.jsonPrimitive.contentOrNull }.getOrNull()
                        }
                        if (!firstModel.isNullOrBlank() && !target.containsKey("model")) {
                            target["model"] = firstModel
                        }
                    }
                }
                else -> {
                    val str = runCatching {
                        elem.jsonPrimitive.booleanOrNull?.toString()
                            ?: elem.jsonPrimitive.intOrNull?.toString()
                            ?: elem.jsonPrimitive.floatOrNull?.toString()
                            ?: elem.jsonPrimitive.contentOrNull
                    }.getOrNull()
                    if (!str.isNullOrBlank() && !target.containsKey(k)) {
                        target[k] = str
                    }
                }
            }
        }
    }

    private fun parseJsonObjectCandidate(raw: String): JsonObject? {
        val trimmed = raw.trim()
        if (!trimmed.startsWith("{")) return null
        return runCatching { json.parseToJsonElement(trimmed) as? JsonObject }.getOrNull()
    }

    private fun decodeBase64Candidate(raw: String): String? {
        val cleaned = raw.trim()
        if (cleaned.length < 4) return null
        val standard = runCatching { Base64.getDecoder().decode(cleaned) }.getOrNull()
            ?: runCatching { Base64.getUrlDecoder().decode(cleaned) }.getOrNull()
            ?: return null
        return standard.toString(Charsets.UTF_8)
    }

    private fun extractModelName(params: Map<String, String>): String? {
        val raw = firstNonBlank(params, "model", "models") ?: return null
        if (raw.startsWith("[")) {
            val arr = runCatching { json.parseToJsonElement(raw) as? JsonArray }.getOrNull()
            val first = arr?.firstOrNull()?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }
            if (!first.isNullOrBlank()) return first
        }
        return raw.split(',').firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun firstNonBlank(map: Map<String, String>, vararg keys: String): String? {
        for (k in keys) {
            val match = map[k]?.takeIf { it.isNotBlank() }
            if (match != null) return match
            val caseInsensitive = map.entries.firstOrNull { it.key.equals(k, ignoreCase = true) }?.value
                ?.takeIf { it.isNotBlank() }
            if (caseInsensitive != null) return caseInsensitive
        }
        return null
    }
}
