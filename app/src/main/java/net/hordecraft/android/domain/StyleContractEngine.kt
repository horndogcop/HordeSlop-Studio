package net.hordeslop.studio.domain

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import net.hordeslop.studio.domain.HordeConstants

data class StudioSubmissionSpec(
    val prompt: String,
    val negativePrompt: String,
    val selectedModels: List<String>,
    val dispatchMode: MultiModelDispatchMode,
    val activeStyle: HordeStyle?,
    val activeStyles: List<HordeStyle> = listOfNotNull(activeStyle),
    val seed: String,
    val steps: Int,
    val cfg: Float,
    val clipSkip: Int,
    val width: Int,
    val height: Int,
    val sampler: String,
    val scheduler: String,
    val nsfw: Boolean,
    val censorNsfw: Boolean,
    val imageCount: Int,
    val loras: List<AttachedLora>,
    val tis: List<AttachedTi>,
    val hiresFix: Boolean = false,
)

data class GenerationChunk(
    val models: List<String>,
    val count: Int,
    val payload: JsonObject,
    val payloadJsonString: String,
)

object StyleContractEngine {
    private val prettyJson = Json { prettyPrint = true }

    fun buildSubmissionChunks(spec: StudioSubmissionSpec): List<GenerationChunk> {
        val safeCount = spec.imageCount.coerceAtLeast(1)
        val perGroupCounts = mutableListOf<Int>()
        var remaining = safeCount
        while (remaining > 0) {
            val next = minOf(HordeConstants.MAX_IMAGES_PER_REQUEST, remaining)
            perGroupCounts += next
            remaining -= next
        }

        val modelGroups = if (spec.selectedModels.size > 1 && spec.dispatchMode == MultiModelDispatchMode.FAN_OUT) {
            spec.selectedModels.map { listOf(it) }
        } else {
            listOf(spec.selectedModels)
        }

        val chunks = mutableListOf<GenerationChunk>()
        for (groupModels in modelGroups) {
            for (chunkN in perGroupCounts) {
                val payload = buildSinglePayload(spec, groupModels, chunkN)
                chunks += GenerationChunk(
                    models = groupModels,
                    count = chunkN,
                    payload = payload,
                    payloadJsonString = prettyJson.encodeToString(JsonObject.serializer(), payload),
                )
            }
        }
        return chunks
    }

    fun buildSinglePayload(
        spec: StudioSubmissionSpec,
        chunkModels: List<String>,
        chunkN: Int,
    ): JsonObject {
        val effectiveStyles = spec.activeStyles.filter { it.id != "none" }
        val style = effectiveStyles.firstOrNull() ?: spec.activeStyle?.takeIf { it.id != "none" }
        val serverStyle = effectiveStyles.firstOrNull { it.inApi || it.source == "Styles API" }
            ?: style?.takeIf { it.inApi || it.source == "Styles API" }
        val isServerStyle = serverStyle != null
        val locked = if (isServerStyle) {
            buildSet {
                effectiveStyles.filter { it.inApi || it.source == "Styles API" }.forEach { addAll(it.lockedFields) }
                style?.let { addAll(it.lockedFields) }
            }
        } else {
            emptySet()
        }

        val params = buildJsonObject {
            put("n", chunkN.coerceIn(1, HordeConstants.MAX_IMAGES_PER_REQUEST))

            if (!isServerStyle || serverStyle == null) {
                put("steps", spec.steps)
                put("cfg_scale", spec.cfg)
                put("clip_skip", spec.clipSkip)
                put("sampler_name", spec.sampler)
                put("scheduler", spec.scheduler)
                put("width", SizeMathEngine.snapTo64(spec.width))
                put("height", SizeMathEngine.snapTo64(spec.height))
            } else {
                if (StyleFieldLock.STEPS !in locked && spec.steps != serverStyle.steps) {
                    put("steps", spec.steps)
                }
                if (StyleFieldLock.CFG !in locked && spec.cfg != serverStyle.cfg) {
                    put("cfg_scale", spec.cfg)
                }
                if (StyleFieldLock.CLIP_SKIP !in locked && spec.clipSkip != serverStyle.clipSkip) {
                    put("clip_skip", spec.clipSkip)
                }
                if (StyleFieldLock.SAMPLER !in locked && spec.sampler != serverStyle.sampler) {
                    put("sampler_name", spec.sampler)
                }
                if (StyleFieldLock.SCHEDULER !in locked && spec.scheduler != serverStyle.scheduler) {
                    put("scheduler", spec.scheduler)
                }
                if (StyleFieldLock.SIZE !in locked && (spec.width != serverStyle.width || spec.height != serverStyle.height)) {
                    put("width", SizeMathEngine.snapTo64(spec.width))
                    put("height", SizeMathEngine.snapTo64(spec.height))
                }
            }

            if (spec.seed.isNotBlank()) {
                put("seed", spec.seed.trim())
            }

            if (spec.hiresFix) {
                put("hires_fix", true)
            }

            if (spec.loras.isNotEmpty()) {
                val loraArray: JsonArray = buildJsonArray {
                    for (lora in spec.loras.take(HordeConstants.MAX_ATTACHED_LORAS)) {
                        val trigger = TemplatePrompts.resolveLoraApiTrigger(
                            useApi = lora.useApiTrigger,
                            mode = lora.triggerMode,
                            selectedTriggers = lora.selectedTriggers.ifEmpty {
                                lora.trainedWords.firstOrNull()?.let(::listOf) ?: emptyList()
                            },
                        )
                        add(
                            buildJsonObject {
                                put("name", lora.modelVersionId.toString())
                                put("is_version", true)
                                put("model", lora.strengthModel)
                                put("clip", lora.strengthClip)
                                if (trigger != null) {
                                    put("inject_trigger", trigger)
                                    if (lora.triggerTarget == PromptTarget.NEGPROMPT) {
                                        put("inject_trigger_target", PromptTarget.NEGPROMPT.apiValue)
                                    }
                                }
                            },
                        )
                    }
                }
                put("loras", loraArray)
            }

            val apiTis = spec.tis
                .filter { it.injectTi != TiInjectMode.NONE }
                .take(HordeConstants.MAX_ATTACHED_TIS)
            if (apiTis.isNotEmpty()) {
                val tiArray: JsonArray = buildJsonArray {
                    for (ti in apiTis) {
                        add(
                            buildJsonObject {
                                put("name", ti.modelId.toString())
                                put("strength", ti.strength)
                                put("inject_ti", ti.injectTi.apiValue)
                            },
                        )
                    }
                }
                put("tis", tiArray)
            }
        }

        val clientStyles = effectiveStyles.filter { !it.inApi && it.source != "Styles API" }
        val combinedPrompt = if (clientStyles.isNotEmpty()) {
            var curPos = spec.prompt
            var curNeg = spec.negativePrompt
            for (cs in clientStyles) {
                val expanded = expandClientStylePrompt(cs, curPos, curNeg)
                if (expanded.contains("###")) {
                    val parts = expanded.split("###", limit = 2)
                    curPos = parts[0].trim()
                    curNeg = parts[1].trim()
                } else {
                    curPos = expanded.trim()
                }
            }
            if (curNeg.isBlank()) curPos else "$curPos ### $curNeg"
        } else if (style != null && !isServerStyle) {
            expandClientStylePrompt(style, spec.prompt, spec.negativePrompt)
        } else if (spec.negativePrompt.isBlank()) {
            spec.prompt.trim()
        } else {
            "${spec.prompt.trim()} ### ${spec.negativePrompt.trim()}"
        }

        return buildJsonObject {
            put("prompt", combinedPrompt)
            if (isServerStyle && serverStyle != null) {
                put("style", serverStyle.id)
            }
            if (!isServerStyle || StyleFieldLock.MODEL !in locked) {
                if (chunkModels.isNotEmpty()) {
                    put(
                        "models",
                        JsonArray(chunkModels.map { JsonPrimitive(it) }),
                    )
                }
            }
            put("nsfw", spec.nsfw)
            put("censor_nsfw", spec.censorNsfw)
            put("params", params)
        }
    }

    fun expandClientStylePrompt(style: HordeStyle, positive: String, negative: String): String {
        val raw = style.rawPromptTemplate ?: "${style.promptTemplate}###${style.negativeTemplate}"
        val cleanPos = positive.trim()
        val cleanNeg = negative.trim()
        if (raw.contains("###")) {
            val parts = raw.split("###", limit = 2)
            val expandedPos = parts.getOrElse(0) { "{p}" }.replace("{p}", cleanPos).trim()
            val negTpl = parts.getOrElse(1) { "{np}" }
            val expandedNeg = negTpl.replace("{np}", cleanNeg)
                .trim()
                .trimStart(',')
                .trimEnd(',')
                .trim()
            return if (expandedNeg.isBlank()) expandedPos else "$expandedPos ### $expandedNeg"
        }
        val withoutNp = raw.replace("{np}", "").replace("{p}", cleanPos).trim().trimEnd(',').trim()
        return if (cleanNeg.isBlank()) withoutNp else "$withoutNp ### $cleanNeg"
    }

    private val I2I_KEYS = setOf("source_image", "source_mask", "extra_source_images", "source_processing")

    fun sanitizePayloadForCurl(payload: JsonObject): JsonObject =
        JsonObject(payload.filterKeys { it !in I2I_KEYS })

    fun buildCurlCommand(
        apiBaseUrl: String,
        apiKey: String,
        payload: JsonObject,
        maskApiKey: Boolean = true,
    ): String {
        val cleanPayload = sanitizePayloadForCurl(payload)
        val endpoint = "${HordeConstants.normalizeBaseUrl(apiBaseUrl)}generate/async"
        val resolvedKey = when {
            apiKey.isBlank() -> HordeConstants.ANONYMOUS_KEY
            maskApiKey && apiKey != HordeConstants.ANONYMOUS_KEY -> "YOUR_HORDE_API_KEY"
            else -> apiKey.trim()
        }
        val bodyJson = prettyJson.encodeToString(JsonObject.serializer(), cleanPayload)
        fun escapeSingleQuotes(s: String): String = s.replace("'", "'\"'\"'")
        return buildString {
            appendLine("curl -X POST '${escapeSingleQuotes(endpoint)}' \\")
            appendLine("  -H 'Content-Type: application/json' \\")
            appendLine("  -H 'apikey: ${escapeSingleQuotes(resolvedKey)}' \\")
            appendLine("  -H 'Client-Agent: ${escapeSingleQuotes(HordeConstants.CLIENT_AGENT)}' \\")
            append("  -d '${escapeSingleQuotes(bodyJson)}'")
        }
    }
}
