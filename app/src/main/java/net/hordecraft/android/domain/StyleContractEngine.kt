package net.hordecraft.android.domain

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

data class StudioSubmissionSpec(
    val prompt: String,
    val negativePrompt: String,
    val selectedModels: List<String>,
    val dispatchMode: MultiModelDispatchMode,
    val activeStyle: HordeStyle?,
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
        val style = spec.activeStyle?.takeIf { it.id != "none" }
        val locked = style?.lockedFields ?: emptySet()

        val params = buildJsonObject {
            put("n", chunkN.coerceIn(1, HordeConstants.MAX_IMAGES_PER_REQUEST))

            if (style == null) {
                put("steps", spec.steps)
                put("cfg_scale", spec.cfg)
                put("clip_skip", spec.clipSkip)
                put("sampler_name", spec.sampler)
                put("scheduler", spec.scheduler)
                put("width", SizeMathEngine.snapTo64(spec.width))
                put("height", SizeMathEngine.snapTo64(spec.height))
            } else {
                if (StyleFieldLock.STEPS !in locked && spec.steps != style.steps) {
                    put("steps", spec.steps)
                }
                if (StyleFieldLock.CFG !in locked && spec.cfg != style.cfg) {
                    put("cfg_scale", spec.cfg)
                }
                if (StyleFieldLock.CLIP_SKIP !in locked && spec.clipSkip != style.clipSkip) {
                    put("clip_skip", spec.clipSkip)
                }
                if (StyleFieldLock.SAMPLER !in locked && spec.sampler != style.sampler) {
                    put("sampler_name", spec.sampler)
                }
                if (StyleFieldLock.SCHEDULER !in locked && spec.scheduler != style.scheduler) {
                    put("scheduler", spec.scheduler)
                }
                if (StyleFieldLock.SIZE !in locked && (spec.width != style.width || spec.height != style.height)) {
                    put("width", SizeMathEngine.snapTo64(spec.width))
                    put("height", SizeMathEngine.snapTo64(spec.height))
                }
            }

            if (spec.seed.isNotBlank()) {
                put("seed", spec.seed.trim())
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

        val combinedPrompt = if (spec.negativePrompt.isBlank()) {
            spec.prompt.trim()
        } else {
            "${spec.prompt.trim()} ### ${spec.negativePrompt.trim()}"
        }

        return buildJsonObject {
            put("prompt", combinedPrompt)
            if (style != null) {
                put("style", style.id)
            }
            if (style == null || StyleFieldLock.MODEL !in locked) {
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
}
