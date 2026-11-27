package net.hordeslop.studio.domain

import android.content.Context
import java.io.File
import java.util.Locale
import kotlin.random.Random

object TemplatePrompts {
    private const val ASSET_FILE_NAME = "example_prompts.txt"

    @Volatile
    private var cachedPresets: List<String>? = null

    val PRESETS: List<String>
        get() = cachedPresets ?: loadFromFallbackSources().also { cachedPresets = it }

    fun loadFromContext(context: Context) {
        val loaded = runCatching {
            context.assets.open(ASSET_FILE_NAME).bufferedReader(Charsets.UTF_8).use { reader ->
                parseStructuredPrompts(reader.readText())
            }
        }.getOrNull()
        if (!loaded.isNullOrEmpty()) {
            cachedPresets = loaded
        }
    }

    fun parseStructuredPrompts(rawText: String): List<String> =
        rawText.lineSequence()
            .map { it.trim() }
            .filter { line ->
                line.isNotEmpty() &&
                    !line.startsWith("#") &&
                    !(line.startsWith("[") && line.endsWith("]"))
            }
            .toList()

    private fun loadFromFallbackSources(): List<String> {
        val candidatePaths = listOf(
            "src/main/assets/$ASSET_FILE_NAME",
            "app/src/main/assets/$ASSET_FILE_NAME",
            ".repos/HordeSlop-Studio/app/src/main/assets/$ASSET_FILE_NAME",
        )
        for (path in candidatePaths) {
            val file = File(path)
            if (file.isFile) {
                val parsed = runCatching { parseStructuredPrompts(file.readText(Charsets.UTF_8)) }.getOrNull()
                if (!parsed.isNullOrEmpty()) return parsed
            }
        }
        val stream = TemplatePrompts::class.java.classLoader?.getResourceAsStream(ASSET_FILE_NAME)
        if (stream != null) {
            val parsed = runCatching {
                stream.bufferedReader(Charsets.UTF_8).use { parseStructuredPrompts(it.readText()) }
            }.getOrNull()
            if (!parsed.isNullOrEmpty()) return parsed
        }
        return listOf("brutalist cliffside observatory at dusk, weathered concrete, mist over ocean, 35mm architectural photography")
    }

    private val PLAIN_TRIGGER_MODEL_REGEX = Regex("anima|krea|turbo", RegexOption.IGNORE_CASE)

    fun pickRandomTemplatePrompt(exclude: String? = null, random: Random = Random.Default): String {
        val all = PRESETS
        val pool = if (exclude != null && all.size > 1) {
            all.filter { it != exclude }
        } else {
            all
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
