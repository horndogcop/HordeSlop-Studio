package net.hordeslop.studio.domain

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
        "bioluminescent tide pools along volcanic black sand beach at twilight, long exposure water reflections",
        "abandoned Art Deco train station concourse, arched iron glass roof, dramatic dusty sunbeams on marble floor",
        "cozy floating wooden bookshop moored on an Amsterdam canal in autumn, warm amber lamps inside, drizzle",
        "geothermal hot springs steaming under northern lights in Iceland, basalt cliffs, frost-covered moss",
        "sun-drenched Moroccan courtyard riad, geometric zellige tilework, citrus trees around a quiet stone fountain",
        "deep sea research submersible illuminating a sunken wooden shipwreck surrounded by coral and drifting silt",
        "hilltop medieval stone windmill overlooking golden lavender fields in Provence during golden hour",
        "vintage yellow tram climbing a steep cobbled street in Lisbon at dawn, pastel facades, soft coastal haze",
        "snowbound timber watchtower on a rocky mountain ridge under starlight, glowing hearth window",
        "craftsman copper and stained-glass lantern workshop at night, warm sparks, hanging amber filigree lamps",
        "misty bamboo grove in Arashiyama at dawn, stone pathway wet from morning dew, filtered jade sunlight",
        "terracotta rooftop terrace in Siena overlooking rolling Tuscan hills, warm late afternoon shadows",
        "retro-futuristic glass monorail gliding above a lush terraced eco-city at sunset, soft golden clouds",
        "quiet coastal tide-mill in Brittany at low tide, weathered granite walls, wooden boat resting on wet sand",
        "candlelit medieval scriptorium desk, illuminated parchment manuscript, brass inkwells and quill pens",
        "autumn birch forest beside a mirror-like glacial river in Patagonia, jagged snow-dusted peaks behind",
        "narrow Venetian canal at blue hour, fog drifting past weathered stucco palazzi and stone bridges",
        "subterranean crystal cavern lit by bioluminescent fungi, calm underground emerald pool",
        "traditional wooden boatbuilder workshop, curved cedar ribs, curled wood shavings, dusty skylight beams",
        "highland stone bothy surrounded by purple heather moorland under dramatic passing rain clouds",
        "neon-lit retro diner in the Mojave desert after rain, wet asphalt reflections, crimson dusk sky",
        "sunlit French bakery window at sunrise, crusty sourdough loaves, brass scales and marble counter",
        "weathered bronze sundial in an overgrown walled English rose garden, dappled afternoon sunlight",
        "orbital greenhouse dome overlooking Earth curvature, hydroponic greens illuminated by cool white LEDs",
        "mountain tea pavilion perched over terraced misty green hills in Yunnan at early morning",
        "grand spiral oak staircase inside a historic European university library, brass railings, soft lamps",
        "salt flat mirror desert in Bolivia under pastel twilight sky, crisp horizon reflection",
        "stone aqueduct spanning a cypress-lined Mediterranean gorge in warm late-evening light",
        "clockmaker workbench covered in tiny brass gears, jewelers loupe, warm focused desk lamp",
        "wooden fishing pier stretching into calm Baltic Sea fog at sunrise, muted slate and rose tones",
        "rooftop astronomical dome painted matte white on a desert plateau, Milky Way core rising overhead",
        "intricate paper-lantern night market alley in Taipei, rising steam from street food stalls",
        "moss-draped ancient redwood cathedral grove, shafts of morning fog and golden light filtering down",
        "restored 1960s silver Airstream trailer parked beside pine-ringed alpine lake at dusk",
        "sunlit ceramic courtyard kiln in Oaxaca, stacked clay pots, warm terracotta shadows and desert sky",
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
