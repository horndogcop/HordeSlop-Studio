package net.hordecraft.android.domain

import java.util.Locale
import kotlin.random.Random

object TemplatePrompts {
    val PRESETS: List<String> = listOf(
        "brutalist cliffside observatory at dusk, weathered concrete, mist over ocean, 35mm architectural photography",
        "sunlit botanical greenhouse interior, spiral iron staircase, morning condensation on glass panels",
        "rainy neon alleyway in Kyoto at midnight, wet cobblestone reflections, warm lantern glow, candid street photo",
        "ancient stone library carved inside a mossy canyon cliff, shafts of golden afternoon sunlight",
        "minimalist Nordic wooden cabin beside a still fjord lake, low drifting fog, crisp autumn light",
        "vintage brass astronomical astrolabe resting on dark oak desk beside weathered star charts, studio chiaroscuro",
        "solitary red lighthouse on jagged basalt rocks, crashing Atlantic spray under stormy silver sky",
        "desert wind-carved sandstone arch under the Milky Way, subtle campsite lantern on the horizon",
        "quiet Japanese pottery workshop, wet clay wheel, shelves of glazed ceramic bowls in soft window light",
        "high-altitude alpine train crossing a stone viaduct over pine valleys at sunrise, crisp mountain air",
    )

    private val PLAIN_TRIGGER_MODEL_REGEX = Regex("anima|krea|turbo", RegexOption.IGNORE_CASE)

    fun pickRandomTemplatePrompt(exclude: String? = null, random: Random = Random.Default): String {
        val pool = if (exclude != null && PRESETS.size > 1) {
            PRESETS.filter { it != exclude }
        } else {
            PRESETS
        }
        return pool[random.nextInt(pool.size)]
    }

    fun shouldUsePlainTrigger(selectedModels: List<String>, loraBaseModel: String): Boolean {
        if (PLAIN_TRIGGER_MODEL_REGEX.containsMatchIn(loraBaseModel)) return true
        return selectedModels.any { PLAIN_TRIGGER_MODEL_REGEX.containsMatchIn(it) }
    }

    fun resolveLoraApiTrigger(
        useApi: Boolean,
        mode: LoraTriggerMode,
        selectedTriggers: List<String>,
    ): String? {
        if (!useApi) return null
        return when (mode) {
            LoraTriggerMode.ALL -> "all"
            LoraTriggerMode.RANDOM -> "random"
            LoraTriggerMode.CUSTOM -> selectedTriggers.takeIf { it.isNotEmpty() }?.joinToString(", ")
        }
    }

    fun resolveLoraPromptInsertTokens(
        trainedWords: List<String>,
        mode: LoraTriggerMode,
        selectedTriggers: List<String>,
        plainTrigger: Boolean,
        random: Random = Random.Default,
    ): List<String> {
        if (trainedWords.isEmpty()) return emptyList()
        fun formatWord(w: String): String = if (plainTrigger) w else "($w:1.0)"
        return when (mode) {
            LoraTriggerMode.ALL -> trainedWords.map(::formatWord)
            LoraTriggerMode.RANDOM -> listOf(formatWord(trainedWords[random.nextInt(trainedWords.size)]))
            LoraTriggerMode.CUSTOM -> {
                val active = selectedTriggers.ifEmpty { listOf(trainedWords.first()) }
                active.map(::formatWord)
            }
        }
    }

    fun appendToken(current: String, token: String): String {
        val trimmed = current.trim()
        if (trimmed.isEmpty()) return token
        if (trimmed.contains(token)) return current
        return "$trimmed, $token"
    }

    fun formatEmbeddingSnippet(modelId: Long, weight: Float): String =
        "(embedding:$modelId:${String.format(Locale.US, "%.2f", weight)})"
}
