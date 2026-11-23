package net.hordecraft.android.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.hordecraft.android.data.local.ModelDefaultsEntity
import net.hordecraft.android.domain.HordeConstants
import net.hordecraft.android.domain.HordeModel
import net.hordecraft.android.domain.HordeStyle
import net.hordecraft.android.domain.StyleFieldLock
import okhttp3.OkHttpClient
import okhttp3.Request

private const val MODEL_REF_URL =
    "https://raw.githubusercontent.com/Haidra-Org/AI-Horde-image-model-reference/main/stable_diffusion.json"
private const val STYLES_REPO_URL =
    "https://raw.githubusercontent.com/Haidra-Org/AI-Horde-Styles/main/styles.json"
private const val STYLE_PREVIEWS_BASE =
    "https://raw.githubusercontent.com/Haidra-Org/AI-Horde-Styles-Previews/main/"

data class ModelRefEntry(
    val baseline: String = "SD 1.5",
    val description: String = "",
    val showcases: List<String> = emptyList(),
    val defaultSteps: Int = 28,
    val defaultSampler: String = "k_dpmpp_2m",
    val defaultScheduler: String = "karras",
    val defaultClipSkip: Int = 1,
    val suggestedMinSteps: Int = 15,
    val suggestedMaxSteps: Int = 45,
    val suggestedMinCfg: Float = 3.5f,
    val suggestedMaxCfg: Float = 9.5f,
    val suggestedWidth: Int = 512,
    val suggestedHeight: Int = 512,
)

class ModelReferenceRepository(
    private val rawOkHttpClient: OkHttpClient,
    private val json: Json,
) {
    private var cachedRef: Map<String, ModelRefEntry>? = null

    suspend fun loadModelReference(): Map<String, ModelRefEntry> = withContext(Dispatchers.IO) {
        cachedRef?.let { return@withContext it }
        val result = mutableMapOf<String, ModelRefEntry>()
        runCatching {
            val req = Request.Builder().url(MODEL_REF_URL).build()
            rawOkHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use
                val body = resp.body?.string().orEmpty()
                val root = json.parseToJsonElement(body).jsonObject
                for ((modelName, elem) in root) {
                    val obj = elem.jsonObject
                    val baseline = obj["baseline"]?.jsonPrimitive?.contentOrNull ?: "SD 1.5"
                    val isXlLike = baseline.contains("xl", ignoreCase = true) ||
                        baseline.contains("flux", ignoreCase = true) ||
                        baseline.contains("pony", ignoreCase = true) ||
                        baseline.contains("illustrious", ignoreCase = true)
                    val isSchnell = modelName.contains("schnell", ignoreCase = true) ||
                        modelName.contains("turbo", ignoreCase = true) ||
                        modelName.contains("lightning", ignoreCase = true)

                    val showcases = (obj["showcases"] as? JsonArray)
                        ?.mapNotNull { it.jsonPrimitive.contentOrNull }
                        ?: emptyList()
                    val configObj = obj["config"] as? JsonObject
                    val minSize = obj["min_cfg_scale"]?.jsonPrimitive?.floatOrNull
                        ?: if (isSchnell) 1f else 3.5f
                    val maxSize = obj["max_cfg_scale"]?.jsonPrimitive?.floatOrNull
                        ?: if (isSchnell) 4f else 9.5f
                    val defDim = if (isXlLike) 1024 else 512
                    val defSteps = when {
                        isSchnell -> 6
                        isXlLike -> 30
                        else -> 28
                    }
                    result[modelName] = ModelRefEntry(
                        baseline = baseline,
                        description = obj["description"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                        showcases = showcases,
                        defaultSteps = defSteps,
                        defaultSampler = configObj?.get("sampler")?.jsonPrimitive?.contentOrNull
                            ?: if (isSchnell) "k_euler" else "dpmpp_2m_sde",
                        defaultScheduler = if (isSchnell) "simple" else "karras",
                        defaultClipSkip = 1,
                        suggestedMinSteps = if (isSchnell) 4 else 18,
                        suggestedMaxSteps = if (isSchnell) 10 else 45,
                        suggestedMinCfg = minSize,
                        suggestedMaxCfg = maxSize,
                        suggestedWidth = defDim,
                        suggestedHeight = defDim,
                    )
                }
            }
        }
        cachedRef = result
        result
    }

    fun mergeModels(
        activeModels: List<ActiveModelDto>,
        referenceMap: Map<String, ModelRefEntry>,
        savedDefaults: Map<String, ModelDefaultsEntity>,
    ): List<HordeModel> {
        val list = if (activeModels.isEmpty()) {
            HordeConstants.FALLBACK_SEED_MODELS
        } else {
            activeModels.filter { it.type == "image" && it.name.isNotBlank() }.map { dto ->
                val ref = referenceMap[dto.name]
                val isXl = dto.name.contains("XL", ignoreCase = true) ||
                    (ref?.baseline?.contains("XL", ignoreCase = true) == true)
                val isFlux = dto.name.contains("Flux", ignoreCase = true) ||
                    (ref?.baseline?.contains("Flux", ignoreCase = true) == true)
                val defWidth = ref?.suggestedWidth ?: if (isXl || isFlux) 1024 else 512
                val defHeight = ref?.suggestedHeight ?: if (isXl || isFlux) 1024 else 512
                HordeModel(
                    name = dto.name,
                    baseline = ref?.baseline ?: when {
                        isFlux -> "Flux.1"
                        isXl -> "SDXL"
                        else -> "SD 1.5"
                    },
                    workers = dto.count,
                    queued = dto.queued.toInt(),
                    etaSeconds = dto.eta,
                    defaultSteps = ref?.defaultSteps ?: if (isXl) 30 else 26,
                    defaultSampler = ref?.defaultSampler ?: "k_dpmpp_2m",
                    defaultScheduler = ref?.defaultScheduler ?: "karras",
                    defaultClipSkip = ref?.defaultClipSkip ?: 1,
                    suggestedMinSteps = ref?.suggestedMinSteps ?: 18,
                    suggestedMaxSteps = ref?.suggestedMaxSteps ?: 45,
                    suggestedMinCfg = ref?.suggestedMinCfg ?: 3.5f,
                    suggestedMaxCfg = ref?.suggestedMaxCfg ?: 9.5f,
                    suggestedWidth = defWidth,
                    suggestedHeight = defHeight,
                    previewImageUrls = ref?.showcases.orEmpty(),
                    showcaseText = ref?.description?.take(120).orEmpty(),
                )
            }
        }

        return list.map { model ->
            val saved = savedDefaults[model.name] ?: return@map model
            model.copy(
                defaultSteps = saved.steps,
                defaultSampler = saved.sampler,
                defaultScheduler = saved.scheduler,
                defaultClipSkip = saved.clipSkip,
            )
        }.sortedByDescending { it.workers }
    }

    suspend fun loadFallbackGitHubStyles(): List<HordeStyle> = withContext(Dispatchers.IO) {
        val result = mutableListOf<HordeStyle>()
        runCatching {
            val req = Request.Builder().url(STYLES_REPO_URL).build()
            rawOkHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use
                val body = resp.body?.string().orEmpty()
                val root = json.parseToJsonElement(body).jsonObject
                for ((styleName, elem) in root) {
                    val obj = elem.jsonObject
                    val rawPrompt = obj["prompt"]?.jsonPrimitive?.contentOrNull ?: "{p}###{np}"
                    val parts = rawPrompt.split("###", limit = 2)
                    val locks = mutableSetOf<StyleFieldLock>()
                    val modelName = (obj["models"] as? JsonArray)
                        ?.firstOrNull()?.jsonPrimitive?.contentOrNull
                    if (modelName != null) locks += StyleFieldLock.MODEL

                    val steps = obj["steps"]?.jsonPrimitive?.intOrNull?.also { locks += StyleFieldLock.STEPS }
                    val cfg = obj["cfg_scale"]?.jsonPrimitive?.floatOrNull?.also { locks += StyleFieldLock.CFG }
                    val sampler = obj["sampler_name"]?.jsonPrimitive?.contentOrNull?.also { locks += StyleFieldLock.SAMPLER }
                    val scheduler = obj["scheduler"]?.jsonPrimitive?.contentOrNull?.also { locks += StyleFieldLock.SCHEDULER }
                    val clipSkip = obj["clip_skip"]?.jsonPrimitive?.intOrNull?.also { locks += StyleFieldLock.CLIP_SKIP }
                    val w = obj["width"]?.jsonPrimitive?.intOrNull
                    val h = obj["height"]?.jsonPrimitive?.intOrNull
                    if (w != null && h != null) locks += StyleFieldLock.SIZE

                    val previewSlug = styleName.replace(" ", "%20") + ".webp"
                    result += HordeStyle(
                        id = styleName,
                        name = styleName,
                        source = "AI-Horde-Styles",
                        model = modelName,
                        promptTemplate = parts.getOrElse(0) { "{p}" }.trim(),
                        negativeTemplate = parts.getOrElse(1) { "{np}" }.trim(),
                        steps = steps,
                        cfg = cfg,
                        sampler = sampler,
                        scheduler = scheduler,
                        clipSkip = clipSkip,
                        width = w,
                        height = h,
                        lockedFields = locks,
                        previewImageUrl = "$STYLE_PREVIEWS_BASE$previewSlug",
                    )
                }
            }
        }
        result
    }

    fun mapApiStyle(dto: HordeStyleDto): HordeStyle {
        val parts = dto.prompt.split("###", limit = 2)
        val params = dto.params
        val locks = mutableSetOf<StyleFieldLock>()
        val modelName = dto.models.firstOrNull()?.also { locks += StyleFieldLock.MODEL }

        val steps = params?.get("steps")?.jsonPrimitiveOrNull()?.intOrNull?.also { locks += StyleFieldLock.STEPS }
        val cfg = params?.get("cfg_scale")?.jsonPrimitiveOrNull()?.floatOrNull?.also { locks += StyleFieldLock.CFG }
        val sampler = params?.get("sampler_name")?.jsonPrimitiveOrNull()?.contentOrNull?.also { locks += StyleFieldLock.SAMPLER }
        val scheduler = params?.get("scheduler")?.jsonPrimitiveOrNull()?.contentOrNull?.also { locks += StyleFieldLock.SCHEDULER }
        val clipSkip = params?.get("clip_skip")?.jsonPrimitiveOrNull()?.intOrNull?.also { locks += StyleFieldLock.CLIP_SKIP }
        val w = params?.get("width")?.jsonPrimitiveOrNull()?.intOrNull
        val h = params?.get("height")?.jsonPrimitiveOrNull()?.intOrNull
        if (w != null && h != null) locks += StyleFieldLock.SIZE

        val slug = dto.name.replace(" ", "%20") + ".webp"
        return HordeStyle(
            id = dto.id.ifBlank { dto.name },
            name = dto.name,
            source = "Styles API",
            model = modelName,
            promptTemplate = parts.getOrElse(0) { "{p}" }.trim(),
            negativeTemplate = parts.getOrElse(1) { "{np}" }.trim(),
            steps = steps,
            cfg = cfg,
            sampler = sampler,
            scheduler = scheduler,
            clipSkip = clipSkip,
            width = w,
            height = h,
            lockedFields = locks,
            previewImageUrl = "$STYLE_PREVIEWS_BASE$slug",
        )
    }

    private fun JsonElement.jsonPrimitiveOrNull() = runCatching { this.jsonPrimitive }.getOrNull()
}
