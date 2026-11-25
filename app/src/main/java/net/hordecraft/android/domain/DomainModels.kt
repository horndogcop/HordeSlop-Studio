package net.hordeslop.studio.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

enum class ActivityDestination(val label: String, val routeKey: String) {
    MAIN("Home", "main"),
    IMAGE_GEN("Image", "image_gen"),
    GALLERY("Gallery", "gallery"),
    USER("Horde", "account"),
    SETTINGS("Settings", "settings"),
    MODELS_STUB("Models", "models_stub"),
    TEXT_GEN_STUB("Text", "text_gen_stub"),
    ALCHEMIST_STUB("Alchemist", "alchemist_stub"),
}

enum class NavLayoutMode(val storageKey: String, val label: String) {
    BOTTOM("bottom", "Bottom Bar"),
    MENU("menu", "Menu Drawer"),
    ADAPTIVE("adaptive", "Adaptive"),
    ;

    companion object {
        fun fromKey(key: String?): NavLayoutMode =
            entries.firstOrNull { it.storageKey == key } ?: BOTTOM
    }
}

enum class AuthMode {
    ANONYMOUS,
    USER_KEY,
    SHARED_KEY,
    ;

    companion object {
        private val GUID_REGEX =
            Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$", RegexOption.IGNORE_CASE)

        fun inferFromKey(key: String): AuthMode {
            val trimmed = key.trim()
            return when {
                trimmed.isEmpty() || trimmed == "0000000000" -> ANONYMOUS
                GUID_REGEX.matches(trimmed) -> SHARED_KEY
                else -> USER_KEY
            }
        }
    }
}

enum class MultiModelDispatchMode(val storageKey: String, val label: String) {
    COMBINED("combined", "Any selected model"),
    FAN_OUT("fan_out", "One request per model"),
    ;

    companion object {
        fun fromKey(key: String?): MultiModelDispatchMode =
            entries.firstOrNull { it.storageKey == key } ?: COMBINED
    }
}

enum class LauncherIconChoice(val storageKey: String, val aliasClassSuffix: String) {
    ROBOT_PLACEHOLDER("robot_placeholder", ".LauncherRobot"),
    HORDE_HAIDRA("horde_haidra", ".LauncherHaidra"),
    ;

    companion object {
        fun fromKey(key: String?): LauncherIconChoice =
            entries.firstOrNull { it.storageKey == key } ?: ROBOT_PLACEHOLDER
    }
}

enum class GenerateButtonPlacement(val storageKey: String, val label: String) {
    SCROLLING("scrolling", "Inline"),
    TOP("top", "Pin Top"),
    BOTTOM("bottom", "Pin Bottom"),
    ;

    companion object {
        fun fromKey(key: String?): GenerateButtonPlacement =
            entries.firstOrNull { it.storageKey == key } ?: SCROLLING
    }
}

enum class StorageMode(val storageKey: String) {
    DOWNLOADS("downloads"),
    SAF("saf"),
    ;

    companion object {
        fun fromKey(key: String?): StorageMode =
            entries.firstOrNull { it.storageKey == key } ?: DOWNLOADS
    }
}

enum class SliderZone {
    NORMAL,
    YELLOW,
    RED,
}

enum class LoraTriggerMode {
    CUSTOM,
    ALL,
    RANDOM,
}

enum class PromptTarget(val apiValue: String) {
    PROMPT("prompt"),
    NEGPROMPT("negprompt"),
}

enum class TiInjectMode(val apiValue: String) {
    NONE("none"),
    PROMPT("prompt"),
    NEGPROMPT("negprompt"),
}

enum class StyleFieldLock {
    MODEL,
    STEPS,
    CFG,
    SAMPLER,
    SCHEDULER,
    CLIP_SKIP,
    SIZE,
}

data class HordeModel(
    val name: String,
    val baseline: String,
    val workers: Int,
    val queued: Int,
    val etaSeconds: Int,
    val defaultSteps: Int,
    val defaultSampler: String,
    val defaultScheduler: String,
    val defaultClipSkip: Int,
    val suggestedMinSteps: Int,
    val suggestedMaxSteps: Int,
    val suggestedMinCfg: Float,
    val suggestedMaxCfg: Float,
    val suggestedWidth: Int,
    val suggestedHeight: Int,
    val previewImageUrls: List<String> = emptyList(),
    val showcaseText: String = "",
)

data class HordeStyle(
    val id: String,
    val name: String,
    val source: String,
    val model: String? = null,
    val promptTemplate: String = "{p}",
    val negativeTemplate: String = "{np}",
    val steps: Int? = null,
    val cfg: Float? = null,
    val sampler: String? = null,
    val scheduler: String? = null,
    val clipSkip: Int? = null,
    val width: Int? = null,
    val height: Int? = null,
    val lockedFields: Set<StyleFieldLock> = emptySet(),
    val previewImageUrl: String? = null,
    val previewImageUrls: List<String> = emptyList(),
    val rawPromptTemplate: String? = null,
    val categories: List<String> = emptyList(),
    val creator: String? = null,
    val isUserCreated: Boolean = false,
    val inApi: Boolean = false,
    val inRepo: Boolean = false,
)

@Serializable
data class CivitaiVersion(
    val id: Long,
    val name: String,
    val baseModel: String,
    val trainedWords: List<String> = emptyList(),
)

@Serializable
data class CivitaiPreview(
    val url: String,
    val isMature: Boolean,
    val width: Int? = null,
    val height: Int? = null,
)

@Serializable
data class AttachedLora(
    val id: String,
    val modelId: Long,
    val name: String,
    val versionName: String,
    val modelVersionId: Long,
    val strengthModel: Float = 0.75f,
    val strengthClip: Float = 0.75f,
    val baseModel: String = "SDXL 1.0",
    val trainedWords: List<String> = emptyList(),
    val useApiTrigger: Boolean = false,
    val triggerTarget: PromptTarget = PromptTarget.PROMPT,
    val triggerMode: LoraTriggerMode = LoraTriggerMode.CUSTOM,
    val selectedTriggers: List<String> = emptyList(),
    val versions: List<CivitaiVersion> = emptyList(),
    val previews: List<CivitaiPreview> = emptyList(),
)

@Serializable
data class AttachedTi(
    val id: String,
    val modelId: Long,
    val modelVersionId: Long,
    val name: String,
    val versionLabel: String,
    val strength: Float = 1.0f,
    val injectTi: TiInjectMode = TiInjectMode.NEGPROMPT,
    val lastTarget: PromptTarget = PromptTarget.NEGPROMPT,
    val baseModel: String = "SD 1.5",
)

data class CivitaiBaseModelGroup(
    val id: String,
    val label: String,
    val apiValues: List<String>,
)

data class HordeJob(
    val id: String,
    val requestId: String,
    val prompt: String,
    val negativePrompt: String,
    val models: List<String>,
    val status: JobStatus,
    val waitTimeSeconds: Int,
    val queuePosition: Int,
    val finished: Int,
    val processing: Int,
    val total: Int,
    val submittedPayloadJson: String,
    val errorMessage: String? = null,
)

enum class JobStatus {
    QUEUED,
    PROCESSING,
    PAUSED,
    COMPLETED,
    CANCELLED_PARTIAL,
    FAILED,
}

data class SavedImage(
    val id: String,
    val requestId: String,
    val generationId: String,
    val fileUri: String,
    val prompt: String,
    val negativePrompt: String,
    val model: String,
    val seed: String,
    val steps: Int,
    val cfg: Float,
    val clipSkip: Int,
    val sampler: String,
    val scheduler: String,
    val width: Int,
    val height: Int,
    val destinationLabel: String,
    val isFavorite: Boolean,
    val partialFromCancel: Boolean,
    val requestJson: String,
    val statusMetadataJson: String,
    val createdAtMillis: Long,
)

data class PromptRecord(
    val id: String,
    val title: String?,
    val prompt: String,
    val negativePrompt: String,
    val model: String,
    val steps: Int,
    val cfg: Float,
    val clipSkip: Int,
    val sampler: String,
    val scheduler: String,
    val timestampMillis: Long,
    val isFavorite: Boolean,
)

data class UserStatsSummary(
    val authMode: AuthMode,
    val username: String? = null,
    val trusted: Boolean = false,
    val kudos: Double = 0.0,
    val recordsRequests: Long = 0,
    val recordsMegapixels: Double = 0.0,
    val workerCount: Int = 0,
    val kudosEarnedWorkers: Double = 0.0,
    val sharedKeyName: String? = null,
    val sharedKeyRemainingKudos: Long? = null,
    val sharedKeyMaxPixels: Long? = null,
    val sharedKeyMaxSteps: Int? = null,
    val sharedKeyExpiry: String? = null,
)

data class HordePerformanceSummary(
    val workerCount: Int = 0,
    val threadCount: Int = 0,
    val queuedRequests: Int = 0,
    val queuedMegapixelsteps: Double = 0.0,
    val pastMinuteMegapixelsteps: Double = 0.0,
    val textWorkerCount: Int = 0,
    val textThreadCount: Int = 0,
    val queuedTextRequests: Int = 0,
    val queuedTokens: Double = 0.0,
    val pastMinuteTokens: Double = 0.0,
)

data class OssCredit(
    val name: String,
    val license: String,
    val url: String,
)

data class NotificationChannelSpec(
    val id: String,
    val name: String,
    val importanceLabel: String,
    val activeInMvp: Boolean,
)

object HordeConstants {
    const val DEFAULT_API_BASE_URL = "https://aihorde.net/api/v2/"
    const val DEFAULT_STYLES_REPO_URL =
        "https://raw.githubusercontent.com/Haidra-Org/AI-Horde-Styles/main/styles.json"
    const val DEFAULT_STYLES_PREVIEWS_URL =
        "https://raw.githubusercontent.com/amiantos/AI-Horde-Styles-Previews/main/previews.json"
    const val ANONYMOUS_KEY = "0000000000"
    const val HAIDRA_LOGO_URL = "https://haidra.net/content/images/2024/11/haidra-transparent.png"
    const val APP_REPO_URL = "https://github.com/horndogcop/hordeslop-studio"
    const val CLIENT_AGENT = "HordeSlopStudio:0.1.0:horndogcop/hordeslop-studio"
    const val MAX_IMAGES_PER_REQUEST = 20
    const val MAX_ATTACHED_LORAS = 5
    const val MAX_ATTACHED_TIS = 5

    val OFFICIAL_SCHEDULERS = listOf(
        "karras",
        "normal",
        "exponential",
        "sgm_uniform",
        "simple",
        "ddim_uniform",
        "align_your_steps",
        "beta",
        "gits",
        "kl_optimal",
        "linear_quadratic",
    )

    val RECOMMENDED_SAMPLERS = listOf(
        "k_euler_a",
        "k_euler",
        "k_dpmpp_2m",
        "dpmpp_2m_sde",
        "k_dpmpp_sde",
        "DDIM",
        "lcm",
        "uni_pc",
    )

    val ADVANCED_SAMPLERS = listOf(
        "ddpm",
        "deis",
        "dpmpp_2m_cfg_pp",
        "dpmpp_2m_sde_heun",
        "dpmpp_2s_ancestral_cfg_pp",
        "dpmpp_3m_sde",
        "dpmsolver",
        "er_sde",
        "euler_ancestral_cfg_pp",
        "euler_cfg_pp",
        "exp_heun_2_x0",
        "exp_heun_2_x0_sde",
        "gradient_estimation",
        "gradient_estimation_cfg_pp",
        "heunpp2",
        "ipndm",
        "ipndm_v",
        "k_dpm_2",
        "k_dpm_2_a",
        "k_dpm_adaptive",
        "k_dpm_fast",
        "k_dpmpp_2s_a",
        "k_heun",
        "k_lms",
        "res_multistep",
        "res_multistep_ancestral",
        "res_multistep_ancestral_cfg_pp",
        "res_multistep_cfg_pp",
        "sa_solver",
        "sa_solver_pece",
        "seeds_2",
        "seeds_3",
        "uni_pc_bh2",
    )

    val CIVITAI_LORA_GROUPS = listOf(
        CivitaiBaseModelGroup(
            id = "sdxl_all",
            label = "SDXL (All + Turbo/Hyper/Lightning)",
            apiValues = listOf("SDXL 1.0", "SDXL 0.9", "SDXL", "SDXL 1.0 LCM", "SDXL Turbo", "SDXL Lightning", "SDXL Hyper"),
        ),
        CivitaiBaseModelGroup("pony", "Pony", listOf("Pony")),
        CivitaiBaseModelGroup("illustrious", "Illustrious", listOf("Illustrious")),
        CivitaiBaseModelGroup("noobai", "NoobAI", listOf("NoobAI")),
        CivitaiBaseModelGroup(
            id = "sd1x_all",
            label = "SD 1.x (1.5, 1.4, LCM, Hyper)",
            apiValues = listOf("SD 1.5", "SD 1.4", "SD 1.5 LCM", "SD 1.5 Hyper", "SD1"),
        ),
        CivitaiBaseModelGroup("flux1_s", "Flux.1 S", listOf("Flux.1 S")),
        CivitaiBaseModelGroup("flux1_d", "Flux.1 D", listOf("Flux.1 D")),
        CivitaiBaseModelGroup("qwen", "Qwen (Qwen Image 1)", listOf("Qwen")),
        CivitaiBaseModelGroup("moss", "Moss", listOf("MOSS Image", "MossImage")),
        CivitaiBaseModelGroup("krea2", "Krea2", listOf("Krea 2", "Krea 2 Turbo", "Krea2")),
        CivitaiBaseModelGroup("z_image", "z-image", listOf("ZImageBase")),
        CivitaiBaseModelGroup("z_image_turbo", "Z-Image-Turbo", listOf("ZImageTurbo")),
    )

    val CIVITAI_TI_GROUPS = listOf(
        CivitaiBaseModelGroup(
            id = "sd15",
            label = "SD 1.5",
            apiValues = listOf("SD 1.5", "SD 1.4", "SD 1.5 LCM", "SD 1.5 Hyper", "SD1"),
        ),
        CivitaiBaseModelGroup(
            id = "sd21",
            label = "SD 2.1",
            apiValues = listOf("SD 2.1", "SD 2.1 768", "SD 2.1 Unclip", "SD 2.0", "SD 2.0 768"),
        ),
        CivitaiBaseModelGroup(
            id = "sdxl",
            label = "SDXL",
            apiValues = listOf("SDXL 1.0", "SDXL 0.9", "SDXL", "SDXL 1.0 LCM", "SDXL Turbo", "SDXL Lightning", "SDXL Hyper"),
        ),
        CivitaiBaseModelGroup("pony", "Pony", listOf("Pony")),
        CivitaiBaseModelGroup("illustrious", "Illustrious", listOf("Illustrious")),
        CivitaiBaseModelGroup("noobai", "NoobAI", listOf("NoobAI")),
    )

    val OSS_CREDITS = listOf(
        OssCredit("Jetpack Compose & Material 3", "Apache-2.0", "https://developer.android.com/jetpack/compose"),
        OssCredit("Kotlin Coroutines & Serialization", "Apache-2.0", "https://github.com/Kotlin/kotlinx.serialization"),
        OssCredit("OkHttp & Retrofit", "Apache-2.0", "https://square.github.io/okhttp/"),
        OssCredit("AndroidX Room & DataStore", "Apache-2.0", "https://developer.android.com/jetpack/androidx/releases/room"),
        OssCredit("AndroidX DocumentFile & ExifInterface", "Apache-2.0", "https://developer.android.com/jetpack/androidx/releases/exifinterface"),
        OssCredit("Coil Image Loader", "Apache-2.0", "https://coil-kt.github.io/coil/"),
    )

    val NOTIFICATION_CHANNELS = listOf(
        NotificationChannelSpec("hordeslop_ongoing", "Generation Progress", "Low (Silent)", true),
        NotificationChannelSpec("hordeslop_results", "Finished & Failed Images", "Default", true),
        NotificationChannelSpec("hordeslop_outside", "Other App Checks", "Low (Later)", false),
        NotificationChannelSpec("hordeslop_queue", "Queue & Rate Alerts", "Default", true),
    )

    fun normalizeBaseUrl(rawUrl: String): String {
        val trimmed = rawUrl.trim()
        if (trimmed.isEmpty()) return DEFAULT_API_BASE_URL
        val withScheme =
            if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed
            else "https://$trimmed"
        return if (withScheme.endsWith("/")) withScheme else "$withScheme/"
    }

    val FALLBACK_SEED_MODELS = listOf(
        HordeModel(
            name = "Deliberate",
            baseline = "SD 1.5",
            workers = 0,
            queued = 0,
            etaSeconds = 0,
            defaultSteps = 28,
            defaultSampler = "k_euler",
            defaultScheduler = "normal",
            defaultClipSkip = 1,
            suggestedMinSteps = 20,
            suggestedMaxSteps = 40,
            suggestedMinCfg = 4f,
            suggestedMaxCfg = 10f,
            suggestedWidth = 512,
            suggestedHeight = 768,
        ),
        HordeModel(
            name = "Juggernaut XL",
            baseline = "SDXL",
            workers = 0,
            queued = 0,
            etaSeconds = 0,
            defaultSteps = 30,
            defaultSampler = "k_euler",
            defaultScheduler = "normal",
            defaultClipSkip = 1,
            suggestedMinSteps = 25,
            suggestedMaxSteps = 45,
            suggestedMinCfg = 3.5f,
            suggestedMaxCfg = 9f,
            suggestedWidth = 1024,
            suggestedHeight = 1024,
        ),
    )
}

typealias JsonMap = JsonObject
