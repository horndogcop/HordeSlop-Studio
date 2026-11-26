package net.hordeslop.studio.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import net.hordeslop.studio.domain.GenerateButtonPlacement
import net.hordeslop.studio.domain.HordeConstants
import net.hordeslop.studio.domain.LauncherIconChoice
import net.hordeslop.studio.domain.NavLayoutMode
import net.hordeslop.studio.domain.StorageMode

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "hordeslop_studio_prefs")

data class AppSettings(
    val hordeApiKey: String = HordeConstants.ANONYMOUS_KEY,
    val civitaiApiKey: String = "",
    val apiBaseUrl: String = HordeConstants.DEFAULT_API_BASE_URL,
    val navLayout: NavLayoutMode = NavLayoutMode.BOTTOM,
    val generateButtonPlacement: GenerateButtonPlacement = GenerateButtonPlacement.SCROLLING,
    val showModelPreviews: Boolean = true,
    val skipGalleryModalToFullscreen: Boolean = false,
    val autoClearCompletedQueue: Boolean = false,
    val useSeparateActivities: Boolean = true,
    val showRawRequestReview: Boolean = false,
    val stylesRepoUrls: List<String> = listOf(HordeConstants.DEFAULT_STYLES_REPO_URL),
    val launcherIcon: LauncherIconChoice = LauncherIconChoice.ROBOT_PLACEHOLDER,
    val storageMode: StorageMode = StorageMode.DOWNLOADS,
    val safTreeUri: String? = null,
    val safFolderLabel: String = "Downloads/HordeSlop Studio",
    val keepImagesInInternalStorage: Boolean = false,
    val preventMediaIndexing: Boolean = false,
    val advancedCollapsed: Boolean = true,
    val onboardingCompleted: Boolean = false,
    val aiNoticeDismissed: Boolean = false,
    val globalRateLimitSeconds: Int = 3,
    val galleryColumns: Int = 2,
    val showGalleryPromptOverlay: Boolean = true,
)

class SettingsDataStore(private val context: Context) {
    private object Keys {
        val HORDE_API_KEY = stringPreferencesKey("horde_api_key")
        val CIVITAI_API_KEY = stringPreferencesKey("civitai_api_key")
        val API_BASE_URL = stringPreferencesKey("api_base_url")
        val NAV_LAYOUT = stringPreferencesKey("nav_layout")
        val GENERATE_BUTTON_PLACEMENT = stringPreferencesKey("generate_button_placement")
        val SHOW_MODEL_PREVIEWS = booleanPreferencesKey("show_model_previews")
        val SKIP_GALLERY_TO_FULLSCREEN = booleanPreferencesKey("skip_gallery_to_fullscreen")
        val AUTO_CLEAR_COMPLETED_QUEUE = booleanPreferencesKey("auto_clear_completed_queue")
        val USE_SEPARATE_ACTIVITIES = booleanPreferencesKey("use_separate_activities")
        val SHOW_RAW_REQUEST_REVIEW = booleanPreferencesKey("show_raw_request_review")
        val STYLES_REPO_URLS = stringPreferencesKey("styles_repo_urls")
        val LAUNCHER_ICON = stringPreferencesKey("launcher_icon")
        val STORAGE_MODE = stringPreferencesKey("storage_mode")
        val SAF_TREE_URI = stringPreferencesKey("saf_tree_uri")
        val SAF_FOLDER_LABEL = stringPreferencesKey("saf_folder_label")
        val KEEP_IMAGES_INTERNAL = booleanPreferencesKey("keep_images_internal")
        val PREVENT_MEDIA_INDEXING = booleanPreferencesKey("prevent_media_indexing")
        val ADVANCED_COLLAPSED = booleanPreferencesKey("advanced_collapsed")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val AI_NOTICE_DISMISSED = booleanPreferencesKey("ai_notice_dismissed")
        val GLOBAL_RATE_LIMIT_SECONDS = intPreferencesKey("global_rate_limit_seconds")
        val GALLERY_COLUMNS = intPreferencesKey("gallery_columns")
        val SHOW_GALLERY_PROMPT_OVERLAY = booleanPreferencesKey("show_gallery_prompt_overlay")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        val rawRepos = prefs[Keys.STYLES_REPO_URLS]
        val parsedRepos = if (rawRepos == null) {
            listOf(HordeConstants.DEFAULT_STYLES_REPO_URL)
        } else {
            rawRepos.split('\n')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
        }
        AppSettings(
            hordeApiKey = prefs[Keys.HORDE_API_KEY]?.ifBlank { HordeConstants.ANONYMOUS_KEY }
                ?: HordeConstants.ANONYMOUS_KEY,
            civitaiApiKey = prefs[Keys.CIVITAI_API_KEY] ?: "",
            apiBaseUrl = HordeConstants.normalizeBaseUrl(
                prefs[Keys.API_BASE_URL] ?: HordeConstants.DEFAULT_API_BASE_URL,
            ),
            navLayout = NavLayoutMode.fromKey(prefs[Keys.NAV_LAYOUT]),
            generateButtonPlacement = GenerateButtonPlacement.fromKey(prefs[Keys.GENERATE_BUTTON_PLACEMENT]),
            showModelPreviews = prefs[Keys.SHOW_MODEL_PREVIEWS] ?: true,
            skipGalleryModalToFullscreen = prefs[Keys.SKIP_GALLERY_TO_FULLSCREEN] ?: false,
            autoClearCompletedQueue = prefs[Keys.AUTO_CLEAR_COMPLETED_QUEUE] ?: false,
            useSeparateActivities = prefs[Keys.USE_SEPARATE_ACTIVITIES] ?: true,
            showRawRequestReview = prefs[Keys.SHOW_RAW_REQUEST_REVIEW] ?: false,
            stylesRepoUrls = parsedRepos,
            launcherIcon = LauncherIconChoice.fromKey(prefs[Keys.LAUNCHER_ICON]),
            storageMode = StorageMode.fromKey(prefs[Keys.STORAGE_MODE]),
            safTreeUri = prefs[Keys.SAF_TREE_URI],
            safFolderLabel = prefs[Keys.SAF_FOLDER_LABEL] ?: "Downloads/HordeSlop Studio",
            keepImagesInInternalStorage = prefs[Keys.KEEP_IMAGES_INTERNAL] ?: false,
            preventMediaIndexing = prefs[Keys.PREVENT_MEDIA_INDEXING] ?: false,
            advancedCollapsed = prefs[Keys.ADVANCED_COLLAPSED] ?: true,
            onboardingCompleted = prefs[Keys.ONBOARDING_COMPLETED] ?: false,
            aiNoticeDismissed = prefs[Keys.AI_NOTICE_DISMISSED] ?: false,
            globalRateLimitSeconds = (prefs[Keys.GLOBAL_RATE_LIMIT_SECONDS] ?: 3).coerceIn(1, 30),
            galleryColumns = (prefs[Keys.GALLERY_COLUMNS] ?: 2).coerceIn(2, 6),
            showGalleryPromptOverlay = prefs[Keys.SHOW_GALLERY_PROMPT_OVERLAY] ?: true,
        )
    }

    suspend fun setHordeApiKey(key: String) {
        val clean = key.trim().ifEmpty { HordeConstants.ANONYMOUS_KEY }
        context.dataStore.edit { it[Keys.HORDE_API_KEY] = clean }
    }

    suspend fun setCivitaiApiKey(key: String) {
        context.dataStore.edit { it[Keys.CIVITAI_API_KEY] = key.trim() }
    }

    suspend fun setApiBaseUrl(url: String) {
        context.dataStore.edit { it[Keys.API_BASE_URL] = HordeConstants.normalizeBaseUrl(url) }
    }

    suspend fun setNavLayout(mode: NavLayoutMode) {
        context.dataStore.edit { it[Keys.NAV_LAYOUT] = mode.storageKey }
    }

    suspend fun setGenerateButtonPlacement(placement: GenerateButtonPlacement) {
        context.dataStore.edit { it[Keys.GENERATE_BUTTON_PLACEMENT] = placement.storageKey }
    }

    suspend fun setShowModelPreviews(enabled: Boolean) {
        context.dataStore.edit { it[Keys.SHOW_MODEL_PREVIEWS] = enabled }
    }

    suspend fun setSkipGalleryModalToFullscreen(enabled: Boolean) {
        context.dataStore.edit { it[Keys.SKIP_GALLERY_TO_FULLSCREEN] = enabled }
    }

    suspend fun setAutoClearCompletedQueue(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_CLEAR_COMPLETED_QUEUE] = enabled }
    }

    suspend fun setUseSeparateActivities(enabled: Boolean) {
        context.dataStore.edit { it[Keys.USE_SEPARATE_ACTIVITIES] = enabled }
    }

    suspend fun setShowRawRequestReview(enabled: Boolean) {
        context.dataStore.edit { it[Keys.SHOW_RAW_REQUEST_REVIEW] = enabled }
    }

    suspend fun setStylesRepoUrls(urls: List<String>) {
        val clean = urls.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        context.dataStore.edit { it[Keys.STYLES_REPO_URLS] = clean.joinToString("\n") }
    }

    suspend fun resetStylesRepoUrls() {
        context.dataStore.edit { it.remove(Keys.STYLES_REPO_URLS) }
    }

    suspend fun setLauncherIcon(icon: LauncherIconChoice) {
        context.dataStore.edit { it[Keys.LAUNCHER_ICON] = icon.storageKey }
    }

    suspend fun setStorageDownloads() {
        context.dataStore.edit {
            it[Keys.STORAGE_MODE] = StorageMode.DOWNLOADS.storageKey
            it[Keys.SAF_FOLDER_LABEL] = "Downloads/HordeSlop Studio"
            it[Keys.KEEP_IMAGES_INTERNAL] = false
        }
    }

    suspend fun setStorageSafTree(uriString: String, displayName: String) {
        context.dataStore.edit {
            it[Keys.STORAGE_MODE] = StorageMode.SAF.storageKey
            it[Keys.SAF_TREE_URI] = uriString
            it[Keys.SAF_FOLDER_LABEL] = displayName
            it[Keys.KEEP_IMAGES_INTERNAL] = false
        }
    }

    suspend fun setKeepImagesInInternalStorage(enabled: Boolean) {
        context.dataStore.edit { it[Keys.KEEP_IMAGES_INTERNAL] = enabled }
    }

    suspend fun setPreventMediaIndexing(enabled: Boolean) {
        context.dataStore.edit { it[Keys.PREVENT_MEDIA_INDEXING] = enabled }
    }

    suspend fun setAdvancedCollapsed(collapsed: Boolean) {
        context.dataStore.edit { it[Keys.ADVANCED_COLLAPSED] = collapsed }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { it[Keys.ONBOARDING_COMPLETED] = completed }
    }

    suspend fun setAiNoticeDismissed(dismissed: Boolean) {
        context.dataStore.edit { it[Keys.AI_NOTICE_DISMISSED] = dismissed }
    }

    suspend fun setShowGalleryPromptOverlay(enabled: Boolean) {
        context.dataStore.edit { it[Keys.SHOW_GALLERY_PROMPT_OVERLAY] = enabled }
    }

    suspend fun setGalleryColumns(columns: Int) {
        context.dataStore.edit { it[Keys.GALLERY_COLUMNS] = columns.coerceIn(2, 6) }
    }

    suspend fun setGlobalRateLimitSeconds(seconds: Int) {
        context.dataStore.edit { it[Keys.GLOBAL_RATE_LIMIT_SECONDS] = seconds.coerceIn(1, 30) }
    }
}
