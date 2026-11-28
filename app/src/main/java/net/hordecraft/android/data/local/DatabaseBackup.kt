package net.hordeslop.studio.data.local

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.hordeslop.studio.domain.GenerateButtonPlacement
import net.hordeslop.studio.domain.HordeConstants
import net.hordeslop.studio.domain.LauncherIconChoice
import net.hordeslop.studio.domain.NavLayoutMode
import net.hordeslop.studio.domain.StorageMode
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

/**
 * Portable app settings included in a backup.
 *
 * Portability policy:
 * - INCLUDED: layout/UI prefs, gallery settings, style repo URLs, rate limit, generation button
 *   placement, model preview toggle, gallery overlays, queue/review flags.
 * - EXCLUDED (secrets): hordeApiKey, civitaiApiKey — never written to a backup.
 * - EXCLUDED (device-bound): safTreeUri, launcherIcon, useSeparateActivities, onboardingCompleted,
 *   aiNoticeDismissed, advancedCollapsed — omitted; safe defaults are used on restore.
 * - EXCLUDED (device-bound storage): storageMode, safFolderLabel, keepImagesInInternalStorage —
 *   device-specific, omitted.
 * - apiBaseUrl: included because it may point to a private/self-hosted instance the user intentionally
 *   configured.
 */
@Serializable
data class AppSettingsBackupDto(
    val apiBaseUrl: String = HordeConstants.DEFAULT_API_BASE_URL,
    val navLayout: String = NavLayoutMode.BOTTOM.storageKey,
    val generateButtonPlacement: String = GenerateButtonPlacement.SCROLLING.storageKey,
    val showModelPreviews: Boolean = true,
    val skipGalleryModalToFullscreen: Boolean = false,
    val autoClearCompletedQueue: Boolean = false,
    val showRawRequestReview: Boolean = false,
    val stylesRepoUrls: List<String> = listOf(HordeConstants.DEFAULT_STYLES_REPO_URL),
    val preventMediaIndexing: Boolean = false,
    val globalRateLimitSeconds: Int = 3,
    val galleryColumns: Int = 2,
    val showGalleryPromptOverlay: Boolean = true,
    val galleryPagedMode: Boolean = false,
)

@Serializable
data class DatabaseBackupBundle(
    val app: String = "hordeslop-studio",
    val formatVersion: Int = 2,
    val exportedAtMillis: Long = System.currentTimeMillis(),
    val appSettings: AppSettingsBackupDto? = null,
    val promptHistory: List<PromptHistoryBackupDto> = emptyList(),
    val modelDefaults: List<ModelDefaultsBackupDto> = emptyList(),
    val savedImages: List<SavedImageBackupDto> = emptyList(),
    val activeJobs: List<ActiveJobBackupDto> = emptyList(),
)

data class DatabaseSyncCounts(
    val promptHistoryCount: Int,
    val modelDefaultsCount: Int,
    val savedImagesCount: Int,
    val activeJobsCount: Int,
    /** True if a portable settings section was present in the backup and applied successfully. */
    val settingsApplied: Boolean = false,
) {
    val totalCount: Int
        get() = promptHistoryCount + modelDefaultsCount + savedImagesCount + activeJobsCount
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
        settings: AppSettings? = null,
        exportedAtMillis: Long = System.currentTimeMillis(),
    ): DatabaseBackupBundle = DatabaseBackupBundle(
        exportedAtMillis = exportedAtMillis,
        appSettings = settings?.let {
            AppSettingsBackupDto(
                apiBaseUrl = it.apiBaseUrl,
                navLayout = it.navLayout.storageKey,
                generateButtonPlacement = it.generateButtonPlacement.storageKey,
                showModelPreviews = it.showModelPreviews,
                skipGalleryModalToFullscreen = it.skipGalleryModalToFullscreen,
                autoClearCompletedQueue = it.autoClearCompletedQueue,
                showRawRequestReview = it.showRawRequestReview,
                stylesRepoUrls = it.stylesRepoUrls,
                preventMediaIndexing = it.preventMediaIndexing,
                globalRateLimitSeconds = it.globalRateLimitSeconds,
                galleryColumns = it.galleryColumns,
                showGalleryPromptOverlay = it.showGalleryPromptOverlay,
                galleryPagedMode = it.galleryPagedMode,
            )
        },
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
    )

    fun encodeJson(bundle: DatabaseBackupBundle): String = backupJson.encodeToString(bundle)

    fun decodeJson(rawJson: String): DatabaseBackupBundle {
        val parsed = backupJson.decodeFromString<DatabaseBackupBundle>(rawJson)
        require(parsed.formatVersion >= 1) { "Unsupported backup format version" }
        return parsed
    }

    /**
     * Convert a decoded [AppSettingsBackupDto] into an [AppSettings] patch that can be applied
     * via [SettingsDataStore]. Returns the target settings with portable fields merged over
     * [current]. Device-bound and secret fields in [current] are preserved unchanged.
     */
    fun mergeSettings(dto: AppSettingsBackupDto, current: AppSettings): AppSettings = current.copy(
        apiBaseUrl = HordeConstants.normalizeBaseUrl(dto.apiBaseUrl),
        navLayout = NavLayoutMode.fromKey(dto.navLayout),
        generateButtonPlacement = GenerateButtonPlacement.fromKey(dto.generateButtonPlacement),
        showModelPreviews = dto.showModelPreviews,
        skipGalleryModalToFullscreen = dto.skipGalleryModalToFullscreen,
        autoClearCompletedQueue = dto.autoClearCompletedQueue,
        showRawRequestReview = dto.showRawRequestReview,
        stylesRepoUrls = dto.stylesRepoUrls.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
            .ifEmpty { listOf(HordeConstants.DEFAULT_STYLES_REPO_URL) },
        preventMediaIndexing = dto.preventMediaIndexing,
        globalRateLimitSeconds = dto.globalRateLimitSeconds.coerceIn(1, 30),
        galleryColumns = dto.galleryColumns.coerceIn(2, 6),
        showGalleryPromptOverlay = dto.showGalleryPromptOverlay,
        galleryPagedMode = dto.galleryPagedMode,
    )

    /**
     * Validate a [DatabaseBackupBundle] before any writes are made. Throws [IllegalArgumentException]
     * describing the first problem found, so callers can surface a clear error without touching
     * Room or DataStore.
     *
     * Validation policy:
     * - Format version must be >= 1 (also checked in [decodeJson]).
     * - If appSettings is present, globalRateLimitSeconds must be in [1, 30], galleryColumns in
     *   [2, 6], and stylesRepoUrls must not be all blank (a fully blank list is replaced by the
     *   default, not an error — but a list that is not a list is a parse error caught earlier).
     * - Database entities must have non-blank primary keys (id / modelName).
     * - SAF-bound values (safTreeUri, storageMode=SAF) must not be present in the backup DTO
     *   (they are excluded at export time; presence would indicate a corrupt or hand-crafted file).
     */
    fun validateBackupBundle(bundle: DatabaseBackupBundle) {
        require(bundle.formatVersion >= 1) { "Unsupported backup format version: ${bundle.formatVersion}" }

        bundle.appSettings?.let { s ->
            require(s.globalRateLimitSeconds in 1..30) {
                "Invalid globalRateLimitSeconds in backup: ${s.globalRateLimitSeconds} (must be 1–30)"
            }
            require(s.galleryColumns in 2..6) {
                "Invalid galleryColumns in backup: ${s.galleryColumns} (must be 2–6)"
            }
        }

        bundle.promptHistory.forEachIndexed { i, e ->
            require(e.id.isNotBlank()) { "promptHistory[$i] has a blank id" }
        }
        bundle.modelDefaults.forEachIndexed { i, e ->
            require(e.modelName.isNotBlank()) { "modelDefaults[$i] has a blank modelName" }
        }
        bundle.savedImages.forEachIndexed { i, e ->
            require(e.id.isNotBlank()) { "savedImages[$i] has a blank id" }
        }
        bundle.activeJobs.forEachIndexed { i, e ->
            require(e.id.isNotBlank()) { "activeJobs[$i] has a blank id" }
        }
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
            val expectedAny = listOf("prompt_history", "model_defaults", "saved_images", "active_jobs")
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

            return DatabaseBackupBundle(
                promptHistory = promptHistory,
                modelDefaults = modelDefaults,
                savedImages = savedImages,
                activeJobs = activeJobs,
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
