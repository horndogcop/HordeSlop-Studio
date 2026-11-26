package net.hordeslop.studio.data.local

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class PromptHistoryBackupDto(
    val id: String,
    val title: String? = null,
    val prompt: String,
    val negativePrompt: String = "",
    val model: String = "",
    val steps: Int = 30,
    val cfg: Float = 7.0f,
    val clipSkip: Int = 1,
    val sampler: String = "k_euler",
    val scheduler: String = "normal",
    val timestampMillis: Long = 0L,
    val isFavorite: Boolean = false,
)

@Serializable
data class ModelDefaultsBackupDto(
    val modelName: String,
    val steps: Int = 30,
    val sampler: String = "k_euler",
    val scheduler: String = "normal",
    val clipSkip: Int = 1,
)

@Serializable
data class SavedImageBackupDto(
    val id: String,
    val requestId: String,
    val generationId: String,
    val fileUri: String,
    val prompt: String,
    val negativePrompt: String = "",
    val model: String = "",
    val seed: String = "",
    val steps: Int = 30,
    val cfg: Float = 7.0f,
    val clipSkip: Int = 1,
    val sampler: String = "k_euler",
    val scheduler: String = "normal",
    val width: Int = 1024,
    val height: Int = 1024,
    val destinationLabel: String = "",
    val isFavorite: Boolean = false,
    val partialFromCancel: Boolean = false,
    val requestJson: String = "{}",
    val statusMetadataJson: String = "{}",
    val createdAtMillis: Long = 0L,
)

@Serializable
data class ActiveJobBackupDto(
    val id: String,
    val requestId: String,
    val prompt: String,
    val negativePrompt: String = "",
    val modelsJoined: String = "",
    val status: String = "COMPLETED",
    val waitTimeSeconds: Int = 0,
    val queuePosition: Int = 0,
    val finished: Int = 0,
    val processing: Int = 0,
    val total: Int = 0,
    val seed: String = "",
    val steps: Int = 30,
    val cfg: Float = 7.0f,
    val clipSkip: Int = 1,
    val sampler: String = "k_euler",
    val scheduler: String = "normal",
    val width: Int = 1024,
    val height: Int = 1024,
    val submittedPayloadJson: String = "{}",
    val errorMessage: String? = null,
    val createdAtMillis: Long = 0L,
    val dismissed: Boolean = false,
    val dismissedAtFinishedCount: Int = 0,
)

@Serializable
data class FavoriteLoraBackupDto(
    val modelVersionId: Long,
    val modelId: Long,
    val name: String,
    val versionName: String = "",
    val strengthModel: Float = 0.75f,
    val strengthClip: Float = 0.75f,
    val baseModel: String = "SDXL 1.0",
    val trainedWordsJson: String = "[]",
    val useApiTrigger: Boolean = false,
    val triggerTarget: String = "PROMPT",
    val triggerMode: String = "CUSTOM",
    val selectedTriggersJson: String = "[]",
    val createdAtMillis: Long = 0L,
)

@Serializable
data class FavoriteTiBackupDto(
    val modelVersionId: Long,
    val modelId: Long,
    val name: String,
    val versionLabel: String = "",
    val strength: Float = 1.0f,
    val injectTi: String = "NEGPROMPT",
    val lastTarget: String = "NEGPROMPT",
    val baseModel: String = "SD 1.5",
    val createdAtMillis: Long = 0L,
)

@Serializable
data class DatabaseBackupBundle(
    val app: String = "hordeslop-studio",
    val formatVersion: Int = 1,
    val exportedAtMillis: Long = System.currentTimeMillis(),
    val promptHistory: List<PromptHistoryBackupDto> = emptyList(),
    val modelDefaults: List<ModelDefaultsBackupDto> = emptyList(),
    val savedImages: List<SavedImageBackupDto> = emptyList(),
    val activeJobs: List<ActiveJobBackupDto> = emptyList(),
    val favoriteLoras: List<FavoriteLoraBackupDto> = emptyList(),
    val favoriteTis: List<FavoriteTiBackupDto> = emptyList(),
)

data class DatabaseSyncCounts(
    val promptHistoryCount: Int,
    val modelDefaultsCount: Int,
    val savedImagesCount: Int,
    val activeJobsCount: Int,
    val favoriteLorasCount: Int = 0,
    val favoriteTisCount: Int = 0,
) {
    val totalCount: Int
        get() = promptHistoryCount + modelDefaultsCount + savedImagesCount + activeJobsCount +
            favoriteLorasCount + favoriteTisCount
}

object DatabaseBackupCodec {
    private val SQLITE_MAGIC = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)
    private val backupJson = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    fun buildBundle(
        promptHistory: List<PromptHistoryEntity>,
        modelDefaults: List<ModelDefaultsEntity>,
        savedImages: List<SavedImageEntity>,
        activeJobs: List<ActiveJobEntity>,
        favoriteLoras: List<FavoriteLoraEntity> = emptyList(),
        favoriteTis: List<FavoriteTiEntity> = emptyList(),
        exportedAtMillis: Long = System.currentTimeMillis(),
    ): DatabaseBackupBundle = DatabaseBackupBundle(
        exportedAtMillis = exportedAtMillis,
        promptHistory = promptHistory.map {
            PromptHistoryBackupDto(
                id = it.id,
                title = it.title,
                prompt = it.prompt,
                negativePrompt = it.negativePrompt,
                model = it.model,
                steps = it.steps,
                cfg = it.cfg,
                clipSkip = it.clipSkip,
                sampler = it.sampler,
                scheduler = it.scheduler,
                timestampMillis = it.timestampMillis,
                isFavorite = it.isFavorite,
            )
        },
        modelDefaults = modelDefaults.map {
            ModelDefaultsBackupDto(
                modelName = it.modelName,
                steps = it.steps,
                sampler = it.sampler,
                scheduler = it.scheduler,
                clipSkip = it.clipSkip,
            )
        },
        savedImages = savedImages.map {
            SavedImageBackupDto(
                id = it.id,
                requestId = it.requestId,
                generationId = it.generationId,
                fileUri = it.fileUri,
                prompt = it.prompt,
                negativePrompt = it.negativePrompt,
                model = it.model,
                seed = it.seed,
                steps = it.steps,
                cfg = it.cfg,
                clipSkip = it.clipSkip,
                sampler = it.sampler,
                scheduler = it.scheduler,
                width = it.width,
                height = it.height,
                destinationLabel = it.destinationLabel,
                isFavorite = it.isFavorite,
                partialFromCancel = it.partialFromCancel,
                requestJson = it.requestJson,
                statusMetadataJson = it.statusMetadataJson,
                createdAtMillis = it.createdAtMillis,
            )
        },
        activeJobs = activeJobs.map {
            ActiveJobBackupDto(
                id = it.id,
                requestId = it.requestId,
                prompt = it.prompt,
                negativePrompt = it.negativePrompt,
                modelsJoined = it.modelsJoined,
                status = it.status,
                waitTimeSeconds = it.waitTimeSeconds,
                queuePosition = it.queuePosition,
                finished = it.finished,
                processing = it.processing,
                total = it.total,
                seed = it.seed,
                steps = it.steps,
                cfg = it.cfg,
                clipSkip = it.clipSkip,
                sampler = it.sampler,
                scheduler = it.scheduler,
                width = it.width,
                height = it.height,
                submittedPayloadJson = it.submittedPayloadJson,
                errorMessage = it.errorMessage,
                createdAtMillis = it.createdAtMillis,
                dismissed = it.dismissed,
                dismissedAtFinishedCount = it.dismissedAtFinishedCount,
            )
        },
        favoriteLoras = favoriteLoras.map {
            FavoriteLoraBackupDto(
                modelVersionId = it.modelVersionId,
                modelId = it.modelId,
                name = it.name,
                versionName = it.versionName,
                strengthModel = it.strengthModel,
                strengthClip = it.strengthClip,
                baseModel = it.baseModel,
                trainedWordsJson = it.trainedWordsJson,
                useApiTrigger = it.useApiTrigger,
                triggerTarget = it.triggerTarget,
                triggerMode = it.triggerMode,
                selectedTriggersJson = it.selectedTriggersJson,
                createdAtMillis = it.createdAtMillis,
            )
        },
        favoriteTis = favoriteTis.map {
            FavoriteTiBackupDto(
                modelVersionId = it.modelVersionId,
                modelId = it.modelId,
                name = it.name,
                versionLabel = it.versionLabel,
                strength = it.strength,
                injectTi = it.injectTi,
                lastTarget = it.lastTarget,
                baseModel = it.baseModel,
                createdAtMillis = it.createdAtMillis,
            )
        },
    )

    fun encodeJson(bundle: DatabaseBackupBundle): String = backupJson.encodeToString(bundle)

    fun decodeJson(rawJson: String): DatabaseBackupBundle {
        val parsed = backupJson.decodeFromString<DatabaseBackupBundle>(rawJson)
        require(parsed.formatVersion >= 1) { "Unsupported backup format version" }
        return parsed
    }

    fun isSqliteDatabaseBytes(bytes: ByteArray): Boolean {
        if (bytes.size < SQLITE_MAGIC.size) return false
        for (i in SQLITE_MAGIC.indices) {
            if (bytes[i] != SQLITE_MAGIC[i]) return false
        }
        return true
    }

    fun readSqliteFile(dbFile: File): DatabaseBackupBundle {
        val db = SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        db.use { sqlite ->
            val tables = mutableSetOf<String>()
            sqlite.rawQuery("SELECT name FROM sqlite_master WHERE type='table'", null).use { c ->
                while (c.moveToNext()) {
                    tables += c.getString(0)
                }
            }
            val expectedAny = listOf(
                "prompt_history",
                "model_defaults",
                "saved_images",
                "active_jobs",
                "favorite_loras",
                "favorite_tis",
            )
            require(expectedAny.any { it in tables }) { "SQLite file is not a HordeSlop Studio database" }

            val promptHistory = if ("prompt_history" in tables) {
                buildList {
                    sqlite.rawQuery("SELECT * FROM prompt_history", null).use { c ->
                        while (c.moveToNext()) {
                            add(
                                PromptHistoryBackupDto(
                                    id = c.str("id"),
                                    title = c.nullableStr("title"),
                                    prompt = c.str("prompt"),
                                    negativePrompt = c.str("negativePrompt"),
                                    model = c.str("model"),
                                    steps = c.int("steps", 30),
                                    cfg = c.flt("cfg", 7.0f),
                                    clipSkip = c.int("clipSkip", 1),
                                    sampler = c.str("sampler", "k_euler"),
                                    scheduler = c.str("scheduler", "normal"),
                                    timestampMillis = c.lng("timestampMillis", 0L),
                                    isFavorite = c.bool("isFavorite", false),
                                ),
                            )
                        }
                    }
                }
            } else {
                emptyList()
            }

            val modelDefaults = if ("model_defaults" in tables) {
                buildList {
                    sqlite.rawQuery("SELECT * FROM model_defaults", null).use { c ->
                        while (c.moveToNext()) {
                            add(
                                ModelDefaultsBackupDto(
                                    modelName = c.str("modelName"),
                                    steps = c.int("steps", 30),
                                    sampler = c.str("sampler", "k_euler"),
                                    scheduler = c.str("scheduler", "normal"),
                                    clipSkip = c.int("clipSkip", 1),
                                ),
                            )
                        }
                    }
                }
            } else {
                emptyList()
            }

            val savedImages = if ("saved_images" in tables) {
                buildList {
                    sqlite.rawQuery("SELECT * FROM saved_images", null).use { c ->
                        while (c.moveToNext()) {
                            add(
                                SavedImageBackupDto(
                                    id = c.str("id"),
                                    requestId = c.str("requestId"),
                                    generationId = c.str("generationId"),
                                    fileUri = c.str("fileUri"),
                                    prompt = c.str("prompt"),
                                    negativePrompt = c.str("negativePrompt"),
                                    model = c.str("model"),
                                    seed = c.str("seed"),
                                    steps = c.int("steps", 30),
                                    cfg = c.flt("cfg", 7.0f),
                                    clipSkip = c.int("clipSkip", 1),
                                    sampler = c.str("sampler", "k_euler"),
                                    scheduler = c.str("scheduler", "normal"),
                                    width = c.int("width", 1024),
                                    height = c.int("height", 1024),
                                    destinationLabel = c.str("destinationLabel"),
                                    isFavorite = c.bool("isFavorite", false),
                                    partialFromCancel = c.bool("partialFromCancel", false),
                                    requestJson = c.str("requestJson", "{}"),
                                    statusMetadataJson = c.str("statusMetadataJson", "{}"),
                                    createdAtMillis = c.lng("createdAtMillis", 0L),
                                ),
                            )
                        }
                    }
                }
            } else {
                emptyList()
            }

            val activeJobs = if ("active_jobs" in tables) {
                buildList {
                    sqlite.rawQuery("SELECT * FROM active_jobs", null).use { c ->
                        while (c.moveToNext()) {
                            add(
                                ActiveJobBackupDto(
                                    id = c.str("id"),
                                    requestId = c.str("requestId"),
                                    prompt = c.str("prompt"),
                                    negativePrompt = c.str("negativePrompt"),
                                    modelsJoined = c.str("modelsJoined"),
                                    status = c.str("status", "COMPLETED"),
                                    waitTimeSeconds = c.int("waitTimeSeconds", 0),
                                    queuePosition = c.int("queuePosition", 0),
                                    finished = c.int("finished", 0),
                                    processing = c.int("processing", 0),
                                    total = c.int("total", 0),
                                    seed = c.str("seed"),
                                    steps = c.int("steps", 30),
                                    cfg = c.flt("cfg", 7.0f),
                                    clipSkip = c.int("clipSkip", 1),
                                    sampler = c.str("sampler", "k_euler"),
                                    scheduler = c.str("scheduler", "normal"),
                                    width = c.int("width", 1024),
                                    height = c.int("height", 1024),
                                    submittedPayloadJson = c.str("submittedPayloadJson", "{}"),
                                    errorMessage = c.nullableStr("errorMessage"),
                                    createdAtMillis = c.lng("createdAtMillis", 0L),
                                    dismissed = c.bool("dismissed", false),
                                    dismissedAtFinishedCount = c.int("dismissedAtFinishedCount", 0),
                                ),
                            )
                        }
                    }
                }
            } else {
                emptyList()
            }

            val favoriteLoras = if ("favorite_loras" in tables) {
                buildList {
                    sqlite.rawQuery("SELECT * FROM favorite_loras", null).use { c ->
                        while (c.moveToNext()) {
                            add(
                                FavoriteLoraBackupDto(
                                    modelVersionId = c.lng("modelVersionId"),
                                    modelId = c.lng("modelId"),
                                    name = c.str("name"),
                                    versionName = c.str("versionName"),
                                    strengthModel = c.flt("strengthModel", 0.75f),
                                    strengthClip = c.flt("strengthClip", 0.75f),
                                    baseModel = c.str("baseModel", "SDXL 1.0"),
                                    trainedWordsJson = c.str("trainedWordsJson", "[]"),
                                    useApiTrigger = c.bool("useApiTrigger", false),
                                    triggerTarget = c.str("triggerTarget", "PROMPT"),
                                    triggerMode = c.str("triggerMode", "CUSTOM"),
                                    selectedTriggersJson = c.str("selectedTriggersJson", "[]"),
                                    createdAtMillis = c.lng("createdAtMillis", 0L),
                                ),
                            )
                        }
                    }
                }
            } else {
                emptyList()
            }

            val favoriteTis = if ("favorite_tis" in tables) {
                buildList {
                    sqlite.rawQuery("SELECT * FROM favorite_tis", null).use { c ->
                        while (c.moveToNext()) {
                            add(
                                FavoriteTiBackupDto(
                                    modelVersionId = c.lng("modelVersionId"),
                                    modelId = c.lng("modelId"),
                                    name = c.str("name"),
                                    versionLabel = c.str("versionLabel"),
                                    strength = c.flt("strength", 1.0f),
                                    injectTi = c.str("injectTi", "NEGPROMPT"),
                                    lastTarget = c.str("lastTarget", "NEGPROMPT"),
                                    baseModel = c.str("baseModel", "SD 1.5"),
                                    createdAtMillis = c.lng("createdAtMillis", 0L),
                                ),
                            )
                        }
                    }
                }
            } else {
                emptyList()
            }

            return DatabaseBackupBundle(
                promptHistory = promptHistory,
                modelDefaults = modelDefaults,
                savedImages = savedImages,
                activeJobs = activeJobs,
                favoriteLoras = favoriteLoras,
                favoriteTis = favoriteTis,
            )
        }
    }

    fun PromptHistoryBackupDto.toEntity() = PromptHistoryEntity(
        id = id,
        title = title,
        prompt = prompt,
        negativePrompt = negativePrompt,
        model = model,
        steps = steps,
        cfg = cfg,
        clipSkip = clipSkip,
        sampler = sampler,
        scheduler = scheduler,
        timestampMillis = timestampMillis,
        isFavorite = isFavorite,
    )

    fun ModelDefaultsBackupDto.toEntity() = ModelDefaultsEntity(
        modelName = modelName,
        steps = steps,
        sampler = sampler,
        scheduler = scheduler,
        clipSkip = clipSkip,
    )

    fun SavedImageBackupDto.toEntity() = SavedImageEntity(
        id = id,
        requestId = requestId,
        generationId = generationId,
        fileUri = fileUri,
        prompt = prompt,
        negativePrompt = negativePrompt,
        model = model,
        seed = seed,
        steps = steps,
        cfg = cfg,
        clipSkip = clipSkip,
        sampler = sampler,
        scheduler = scheduler,
        width = width,
        height = height,
        destinationLabel = destinationLabel,
        isFavorite = isFavorite,
        partialFromCancel = partialFromCancel,
        requestJson = requestJson,
        statusMetadataJson = statusMetadataJson,
        createdAtMillis = createdAtMillis,
    )

    fun ActiveJobBackupDto.toEntity() = ActiveJobEntity(
        id = id,
        requestId = requestId,
        prompt = prompt,
        negativePrompt = negativePrompt,
        modelsJoined = modelsJoined,
        status = status,
        waitTimeSeconds = waitTimeSeconds,
        queuePosition = queuePosition,
        finished = finished,
        processing = processing,
        total = total,
        seed = seed,
        steps = steps,
        cfg = cfg,
        clipSkip = clipSkip,
        sampler = sampler,
        scheduler = scheduler,
        width = width,
        height = height,
        submittedPayloadJson = submittedPayloadJson,
        errorMessage = errorMessage,
        createdAtMillis = createdAtMillis,
        dismissed = dismissed,
        dismissedAtFinishedCount = dismissedAtFinishedCount,
    )

    fun FavoriteLoraBackupDto.toEntity() = FavoriteLoraEntity(
        modelVersionId = modelVersionId,
        modelId = modelId,
        name = name,
        versionName = versionName,
        strengthModel = strengthModel,
        strengthClip = strengthClip,
        baseModel = baseModel,
        trainedWordsJson = trainedWordsJson,
        useApiTrigger = useApiTrigger,
        triggerTarget = triggerTarget,
        triggerMode = triggerMode,
        selectedTriggersJson = selectedTriggersJson,
        createdAtMillis = createdAtMillis,
    )

    fun FavoriteTiBackupDto.toEntity() = FavoriteTiEntity(
        modelVersionId = modelVersionId,
        modelId = modelId,
        name = name,
        versionLabel = versionLabel,
        strength = strength,
        injectTi = injectTi,
        lastTarget = lastTarget,
        baseModel = baseModel,
        createdAtMillis = createdAtMillis,
    )

    private fun Cursor.str(col: String, fallback: String = ""): String {
        val idx = getColumnIndex(col)
        return if (idx >= 0 && !isNull(idx)) getString(idx) ?: fallback else fallback
    }

    private fun Cursor.nullableStr(col: String): String? {
        val idx = getColumnIndex(col)
        return if (idx >= 0 && !isNull(idx)) getString(idx) else null
    }

    private fun Cursor.int(col: String, fallback: Int = 0): Int {
        val idx = getColumnIndex(col)
        return if (idx >= 0 && !isNull(idx)) getInt(idx) else fallback
    }

    private fun Cursor.lng(col: String, fallback: Long = 0L): Long {
        val idx = getColumnIndex(col)
        return if (idx >= 0 && !isNull(idx)) getLong(idx) else fallback
    }

    private fun Cursor.flt(col: String, fallback: Float = 0f): Float {
        val idx = getColumnIndex(col)
        return if (idx >= 0 && !isNull(idx)) getFloat(idx) else fallback
    }

    private fun Cursor.bool(col: String, fallback: Boolean = false): Boolean {
        val idx = getColumnIndex(col)
        return if (idx >= 0 && !isNull(idx)) getInt(idx) != 0 else fallback
    }
}
