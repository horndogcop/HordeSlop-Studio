package net.hordecraft.android.ui

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.hordecraft.android.HordecraftApp
import net.hordecraft.android.data.local.AppSettings
import net.hordecraft.android.domain.AttachedLora
import net.hordecraft.android.domain.AttachedTi
import net.hordecraft.android.domain.GenerateButtonPlacement
import net.hordecraft.android.domain.HordeConstants
import net.hordecraft.android.domain.HordeJob
import net.hordecraft.android.domain.HordeModel
import net.hordecraft.android.domain.HordeStyle
import net.hordecraft.android.domain.LauncherIconChoice
import net.hordecraft.android.domain.LauncherIconManager
import net.hordecraft.android.domain.LoraTriggerMode
import net.hordecraft.android.domain.MultiModelDispatchMode
import net.hordecraft.android.domain.NavLayoutMode
import net.hordecraft.android.domain.PromptRecord
import net.hordecraft.android.domain.PromptTarget
import net.hordecraft.android.domain.SavedImage
import net.hordecraft.android.domain.SizeMathEngine
import net.hordecraft.android.domain.StudioSubmissionSpec
import net.hordecraft.android.domain.StyleFieldLock
import net.hordecraft.android.domain.TemplatePrompts
import net.hordecraft.android.domain.TiInjectMode
import net.hordecraft.android.domain.UserStatsSummary
import net.hordecraft.android.service.HordeJobService

data class StudioUiState(
    val prompt: String = TemplatePrompts.pickRandomTemplatePrompt(),
    val negativePrompt: String = "blurry, watermark, oversaturated, plastic",
    val models: List<HordeModel> = HordeConstants.FALLBACK_SEED_MODELS,
    val selectedModels: List<String> = listOf("Juggernaut XL"),
    val dispatchMode: MultiModelDispatchMode = MultiModelDispatchMode.COMBINED,
    val styles: List<HordeStyle> = listOf(HordeStyle(id = "none", name = "Raw Prompt", source = "None")),
    val selectedStyleId: String = "none",
    val seed: String = "",
    val steps: Int = 30,
    val cfg: Float = 7.0f,
    val clipSkip: Int = 1,
    val width: Int = 1024,
    val height: Int = 1024,
    val sampler: String = "dpmpp_2m_sde",
    val scheduler: String = "karras",
    val showAllSamplers: Boolean = false,
    val nsfw: Boolean = false,
    val censorNsfw: Boolean = true,
    val imageCount: Int = 1,
    val loras: List<AttachedLora> = emptyList(),
    val tis: List<AttachedTi> = emptyList(),
    val queuePaused: Boolean = false,
    val civitaiResultsLoras: List<AttachedLora> = emptyList(),
    val civitaiResultsTis: List<AttachedTi> = emptyList(),
    val civitaiLoading: Boolean = false,
    val userStats: UserStatsSummary = UserStatsSummary(authMode = net.hordecraft.android.domain.AuthMode.ANONYMOUS),
    val loadingModels: Boolean = false,
    val toastMessage: String? = null,
) {
    val activeStyle: HordeStyle?
        get() = styles.firstOrNull { it.id == selectedStyleId }

    val primaryModel: HordeModel
        get() = models.firstOrNull { it.name == selectedModels.firstOrNull() } ?: models.first()

    fun isFieldLocked(lock: StyleFieldLock): Boolean =
        selectedStyleId != "none" && (activeStyle?.lockedFields?.contains(lock) == true)
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as HordecraftApp
    private val repo = app.container.repository

    val settings: StateFlow<AppSettings> = repo.settingsFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        AppSettings(),
    )

    val history: StateFlow<List<PromptRecord>> = repo.historyFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )

    val savedImages: StateFlow<List<SavedImage>> = repo.savedImagesFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )

    val activeJobs: StateFlow<List<HordeJob>> = repo.activeJobsFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )

    private val _studio = app.container.sharedStudioState
    val studio: StateFlow<StudioUiState> = _studio.asStateFlow()

    init {
        refreshCatalogs()
        refreshUserStats()
    }

    fun clearToast() {
        _studio.update { it.copy(toastMessage = null) }
    }

    fun showToast(message: String) {
        _studio.update { it.copy(toastMessage = message) }
    }

    fun refreshCatalogs() {
        viewModelScope.launch {
            _studio.update { it.copy(loadingModels = true) }
            val fetchedModels = runCatching { repo.fetchModels() }.getOrNull()
            val fetchedStyles = runCatching { repo.fetchStyles() }.getOrNull()
            _studio.update { state ->
                val nextModels = fetchedModels?.ifEmpty { state.models } ?: state.models
                val nextSelected = state.selectedModels.filter { sel -> nextModels.any { it.name == sel } }
                    .ifEmpty { listOf(nextModels.first().name) }
                state.copy(
                    models = nextModels,
                    selectedModels = nextSelected,
                    styles = fetchedStyles ?: state.styles,
                    loadingModels = false,
                )
            }
        }
    }

    fun refreshUserStats() {
        viewModelScope.launch {
            val summary = runCatching { repo.fetchUserStats() }.getOrNull() ?: return@launch
            _studio.update { it.copy(userStats = summary) }
        }
    }

    fun updatePrompt(value: String) = _studio.update { it.copy(prompt = value) }
    fun updateNegativePrompt(value: String) = _studio.update { it.copy(negativePrompt = value) }
    fun updateSeed(value: String) = _studio.update { it.copy(seed = value) }
    fun updateSteps(value: Int) = _studio.update { it.copy(steps = value.coerceIn(1, 100)) }
    fun updateCfg(value: Float) = _studio.update { it.copy(cfg = value.coerceIn(1f, 30f)) }
    fun updateClipSkip(value: Int) = _studio.update { it.copy(clipSkip = value.coerceIn(1, 12)) }
    fun updateSampler(value: String) = _studio.update { it.copy(sampler = value) }
    fun updateScheduler(value: String) = _studio.update { it.copy(scheduler = value) }
    fun toggleAllSamplers() = _studio.update { it.copy(showAllSamplers = !it.showAllSamplers) }
    fun updateNsfw(value: Boolean) = _studio.update { it.copy(nsfw = value) }
    fun updateCensorNsfw(value: Boolean) = _studio.update { it.copy(censorNsfw = value) }
    fun updateImageCount(value: Int) = _studio.update { it.copy(imageCount = value.coerceIn(1, 100)) }
    fun updateDispatchMode(mode: MultiModelDispatchMode) = _studio.update { it.copy(dispatchMode = mode) }

    fun updateSize(width: Int, height: Int) {
        _studio.update {
            it.copy(
                width = SizeMathEngine.snapTo64(width),
                height = SizeMathEngine.snapTo64(height),
            )
        }
    }

    fun toggleModelSelection(modelName: String) {
        _studio.update { state ->
            val exists = modelName in state.selectedModels
            val next = if (exists) {
                if (state.selectedModels.size > 1) state.selectedModels - modelName else state.selectedModels
            } else {
                state.selectedModels + modelName
            }
            state.copy(selectedModels = next)
        }
    }

    fun applyPrimaryModelDefaults(model: HordeModel) {
        _studio.update { state ->
            state.copy(
                steps = if (!state.isFieldLocked(StyleFieldLock.STEPS)) model.defaultSteps else state.steps,
                sampler = if (!state.isFieldLocked(StyleFieldLock.SAMPLER)) model.defaultSampler else state.sampler,
                scheduler = if (!state.isFieldLocked(StyleFieldLock.SCHEDULER)) model.defaultScheduler else state.scheduler,
                clipSkip = if (!state.isFieldLocked(StyleFieldLock.CLIP_SKIP)) model.defaultClipSkip else state.clipSkip,
                width = if (!state.isFieldLocked(StyleFieldLock.SIZE)) model.suggestedWidth else state.width,
                height = if (!state.isFieldLocked(StyleFieldLock.SIZE)) model.suggestedHeight else state.height,
            )
        }
    }

    fun saveDefaultsForModel(modelName: String) {
        val st = _studio.value
        viewModelScope.launch {
            repo.saveModelDefaults(modelName, st.steps, st.sampler, st.scheduler, st.clipSkip)
            refreshCatalogs()
            showToast("Saved defaults for $modelName")
        }
    }

    fun resetDefaultsForModel(modelName: String) {
        viewModelScope.launch {
            repo.resetModelDefaults(modelName)
            refreshCatalogs()
            showToast("Reset defaults for $modelName")
        }
    }

    fun selectStyle(style: HordeStyle) {
        _studio.update { state ->
            if (style.id == "none") {
                state.copy(selectedStyleId = "none")
            } else {
                val nextModels = style.model?.let { m ->
                    if (state.models.any { it.name == m }) listOf(m) else state.selectedModels
                } ?: state.selectedModels
                state.copy(
                    selectedStyleId = style.id,
                    selectedModels = nextModels,
                    steps = style.steps ?: state.steps,
                    cfg = style.cfg ?: state.cfg,
                    sampler = style.sampler ?: state.sampler,
                    scheduler = style.scheduler ?: state.scheduler,
                    clipSkip = style.clipSkip ?: state.clipSkip,
                    width = style.width ?: state.width,
                    height = style.height ?: state.height,
                )
            }
        }
    }

    fun resetAllParametersToDefaults() {
        _studio.update { state ->
            val base = state.primaryModel
            state.copy(
                selectedStyleId = "none",
                prompt = TemplatePrompts.pickRandomTemplatePrompt(exclude = state.prompt),
                negativePrompt = "",
                seed = "",
                steps = base.defaultSteps,
                cfg = 7.0f,
                clipSkip = base.defaultClipSkip,
                width = base.suggestedWidth,
                height = base.suggestedHeight,
                sampler = base.defaultSampler,
                scheduler = base.defaultScheduler,
                nsfw = false,
                censorNsfw = true,
                imageCount = 1,
                loras = emptyList(),
                tis = emptyList(),
                toastMessage = "Reset all fields",
            )
        }
    }

    fun applyHistoryRecord(record: PromptRecord) {
        _studio.update { state ->
            state.copy(
                prompt = record.prompt,
                negativePrompt = record.negativePrompt,
                selectedModels = if (record.model.isNotBlank()) listOf(record.model) else state.selectedModels,
                steps = record.steps,
                cfg = record.cfg,
                clipSkip = record.clipSkip,
                sampler = record.sampler,
                scheduler = record.scheduler,
            )
        }
    }

    fun applySavedImageParameters(image: SavedImage) {
        _studio.update { state ->
            state.copy(
                prompt = image.prompt,
                negativePrompt = image.negativePrompt,
                selectedModels = if (image.model.isNotBlank()) listOf(image.model) else state.selectedModels,
                seed = image.seed,
                steps = image.steps,
                cfg = image.cfg,
                clipSkip = image.clipSkip,
                sampler = image.sampler,
                scheduler = image.scheduler,
                width = image.width,
                height = image.height,
                selectedStyleId = "none",
            )
        }
    }

    fun upsertLora(lora: AttachedLora) {
        _studio.update { state ->
            val existingIdx = state.loras.indexOfFirst { it.modelId == lora.modelId }
            val nextList = if (existingIdx >= 0) {
                state.loras.toMutableList().apply { set(existingIdx, lora) }
            } else {
                if (state.loras.size >= HordeConstants.MAX_ATTACHED_LORAS) {
                    return@update state.copy(toastMessage = "Max ${HordeConstants.MAX_ATTACHED_LORAS} LoRAs")
                }
                state.loras + lora
            }
            state.copy(loras = nextList)
        }
    }

    fun removeLora(id: String) {
        _studio.update { it.copy(loras = it.loras.filterNot { l -> l.id == id }) }
    }

    fun moveLora(index: Int, delta: Int) {
        _studio.update { state ->
            val nextIdx = index + delta
            if (index !in state.loras.indices || nextIdx !in state.loras.indices) return@update state
            val mutable = state.loras.toMutableList()
            val item = mutable.removeAt(index)
            mutable.add(nextIdx, item)
            state.copy(loras = mutable)
        }
    }

    fun updateLoraItem(id: String, transform: (AttachedLora) -> AttachedLora) {
        _studio.update { state ->
            state.copy(loras = state.loras.map { if (it.id == id) transform(it) else it })
        }
    }

    fun attachTi(ti: AttachedTi) {
        _studio.update { state ->
            if (state.tis.any { it.modelId == ti.modelId }) return@update state
            if (state.tis.size >= HordeConstants.MAX_ATTACHED_TIS) {
                return@update state.copy(toastMessage = "Max ${HordeConstants.MAX_ATTACHED_TIS} embeddings")
            }
            state.copy(tis = state.tis + ti)
        }
    }

    fun removeTi(id: String) {
        _studio.update { it.copy(tis = it.tis.filterNot { t -> t.id == id }) }
    }

    fun updateTiItem(id: String, transform: (AttachedTi) -> AttachedTi) {
        _studio.update { state ->
            state.copy(tis = state.tis.map { if (it.id == id) transform(it) else it })
        }
    }

    fun searchCivitaiLoras(query: String, allModels: Boolean, groupIds: Set<String>) {
        viewModelScope.launch {
            _studio.update { it.copy(civitaiLoading = true) }
            val results = repo.searchCivitaiLoras(query, allModels, groupIds)
            _studio.update { it.copy(civitaiResultsLoras = results, civitaiLoading = false) }
        }
    }

    fun searchCivitaiTis(query: String, allModels: Boolean, groupIds: Set<String>) {
        viewModelScope.launch {
            _studio.update { it.copy(civitaiLoading = true) }
            val results = repo.searchCivitaiTis(query, allModels, groupIds)
            _studio.update { it.copy(civitaiResultsTis = results, civitaiLoading = false) }
        }
    }

    fun submitGenerate() {
        val st = _studio.value
        if (st.prompt.isBlank()) {
            showToast("Enter a prompt first")
            return
        }
        viewModelScope.launch {
            runCatching {
                repo.submitGeneration(
                    StudioSubmissionSpec(
                        prompt = st.prompt,
                        negativePrompt = st.negativePrompt,
                        selectedModels = st.selectedModels,
                        dispatchMode = st.dispatchMode,
                        activeStyle = st.activeStyle,
                        seed = st.seed,
                        steps = st.steps,
                        cfg = st.cfg,
                        clipSkip = st.clipSkip,
                        width = st.width,
                        height = st.height,
                        sampler = st.sampler,
                        scheduler = st.scheduler,
                        nsfw = st.nsfw,
                        censorNsfw = st.censorNsfw,
                        imageCount = st.imageCount,
                        loras = st.loras,
                        tis = st.tis,
                    ),
                )
            }.onSuccess { jobs ->
                HordeJobService.startPolling(app)
                showToast("Queued ${jobs.size} request(s)")
            }.onFailure { err ->
                showToast("Request failed: ${err.message ?: "Network error"}")
            }
        }
    }

    fun rerollImage(image: SavedImage) {
        viewModelScope.launch {
            runCatching { repo.enqueueReroll(image) }
                .onSuccess {
                    HordeJobService.startPolling(app)
                    showToast("Queued reroll")
                }
                .onFailure { showToast("Reroll failed: ${it.message}") }
        }
    }

    fun toggleQueuePause() {
        val nextPaused = !_studio.value.queuePaused
        _studio.update { it.copy(queuePaused = nextPaused) }
        if (nextPaused) {
            HordeJobService.sendPause(app)
        } else {
            HordeJobService.sendResume(app)
        }
    }

    fun cancelJob(jobId: String) {
        HordeJobService.cancelJob(app, jobId)
        showToast("Stopping request & saving finished images")
    }

    fun toggleHistoryFavorite(id: String, current: Boolean) {
        viewModelScope.launch { repo.toggleHistoryFavorite(id, !current) }
    }

    fun toggleImageFavorite(id: String, current: Boolean) {
        viewModelScope.launch { repo.toggleImageFavorite(id, !current) }
    }

    fun deleteImage(image: SavedImage, deleteFile: Boolean) {
        viewModelScope.launch {
            repo.deleteSavedImage(image, deleteFile)
            showToast(if (deleteFile) "Deleted image and file" else "Removed from gallery")
        }
    }

    fun copyText(label: String, text: String) {
        repo.storageManager.copyText(label, text)
        showToast("Copied $label")
    }

    fun copyImage(uriString: String) {
        viewModelScope.launch {
            repo.storageManager.copyImageToClipboard(uriString)
            showToast("Copied image")
        }
    }

    suspend fun buildShareIntent(uriString: String): Intent? = repo.storageManager.createShareIntent(uriString)
    suspend fun buildOpenInIntent(uriString: String): Intent? = repo.storageManager.createOpenInIntent(uriString)

    // Settings mutations
    fun saveHordeApiKey(key: String) {
        viewModelScope.launch {
            repo.settingsStore.setHordeApiKey(key)
            refreshUserStats()
        }
    }

    fun saveCivitaiApiKey(key: String) {
        viewModelScope.launch { repo.settingsStore.setCivitaiApiKey(key) }
    }

    fun saveApiBaseUrl(url: String) {
        viewModelScope.launch {
            repo.settingsStore.setApiBaseUrl(url)
            refreshCatalogs()
        }
    }

    fun setNavLayout(mode: NavLayoutMode) {
        viewModelScope.launch { repo.settingsStore.setNavLayout(mode) }
    }

    fun setGenerateButtonPlacement(placement: GenerateButtonPlacement) {
        viewModelScope.launch { repo.settingsStore.setGenerateButtonPlacement(placement) }
    }

    fun setShowModelPreviews(enabled: Boolean) {
        viewModelScope.launch { repo.settingsStore.setShowModelPreviews(enabled) }
    }

    fun setSkipGalleryModalToFullscreen(enabled: Boolean) {
        viewModelScope.launch { repo.settingsStore.setSkipGalleryModalToFullscreen(enabled) }
    }

    fun setLauncherIcon(icon: LauncherIconChoice) {
        viewModelScope.launch {
            repo.settingsStore.setLauncherIcon(icon)
            LauncherIconManager.applyLauncherIcon(app, icon)
        }
    }

    fun setStorageDownloads() {
        viewModelScope.launch { repo.settingsStore.setStorageDownloads() }
    }

    fun setStorageSafTree(uriString: String, label: String) {
        viewModelScope.launch { repo.settingsStore.setStorageSafTree(uriString, label) }
    }

    fun setAdvancedCollapsed(collapsed: Boolean) {
        viewModelScope.launch { repo.settingsStore.setAdvancedCollapsed(collapsed) }
    }

    fun completeOnboarding(keyInput: String) {
        viewModelScope.launch {
            repo.settingsStore.setHordeApiKey(keyInput)
            repo.settingsStore.setOnboardingCompleted(true)
            refreshUserStats()
        }
    }
}
