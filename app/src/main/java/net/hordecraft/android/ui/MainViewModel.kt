package net.hordeslop.studio.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.hordeslop.studio.HordecraftApp
import net.hordeslop.studio.data.local.AppSettings
import net.hordeslop.studio.domain.ActivityDestination
import net.hordeslop.studio.domain.AttachedLora
import net.hordeslop.studio.domain.AttachedTi
import net.hordeslop.studio.domain.GenerateButtonPlacement
import net.hordeslop.studio.domain.HordeConstants
import net.hordeslop.studio.domain.HordeJob
import net.hordeslop.studio.domain.HordeModel
import net.hordeslop.studio.domain.HordePerformanceSummary
import net.hordeslop.studio.domain.HordeStyle
import net.hordeslop.studio.domain.LauncherIconChoice
import net.hordeslop.studio.domain.LauncherIconManager
import net.hordeslop.studio.domain.LoraTriggerMode
import net.hordeslop.studio.domain.MultiModelDispatchMode
import net.hordeslop.studio.domain.NavLayoutMode
import net.hordeslop.studio.domain.ParsedImageMetadata
import net.hordeslop.studio.domain.PromptRecord
import net.hordeslop.studio.domain.PromptTarget
import net.hordeslop.studio.domain.SavedImage
import net.hordeslop.studio.domain.SizeMathEngine
import net.hordeslop.studio.domain.StudioSubmissionSpec
import net.hordeslop.studio.domain.StyleFieldLock
import net.hordeslop.studio.domain.TemplatePrompts
import net.hordeslop.studio.domain.TiInjectMode
import net.hordeslop.studio.domain.UserStatsSummary
import net.hordeslop.studio.service.HordeJobService
import net.hordeslop.studio.domain.StyleContractEngine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

data class StudioUiState(
    val prompt: String = TemplatePrompts.pickRandomTemplatePrompt(),
    val negativePrompt: String = "blurry, watermark, oversaturated, plastic",
    val models: List<HordeModel> = HordeConstants.FALLBACK_SEED_MODELS,
    val selectedModels: List<String> = listOf("Juggernaut XL"),
    val multiModelEnabled: Boolean = false,
    val dispatchMode: MultiModelDispatchMode = MultiModelDispatchMode.COMBINED,
    val styles: List<HordeStyle> = listOf(HordeStyle(id = "none", name = "Raw Prompt", source = "None")),
    val selectedStyleId: String = "none",
    val selectedStyleIds: List<String> = listOf("none"),
    val multiStyleEnabled: Boolean = false,
    val seed: String = "",
    val steps: Int = 30,
    val cfg: Float = 7.0f,
    val clipSkip: Int = 1,
    val width: Int = 1024,
    val height: Int = 1024,
    val sampler: String = "k_euler",
    val scheduler: String = "normal",
    val showAllSamplers: Boolean = false,
    val recommendedSamplers: List<String> = HordeConstants.RECOMMENDED_SAMPLERS,
    val allSamplers: List<String> = (HordeConstants.RECOMMENDED_SAMPLERS + HordeConstants.ADVANCED_SAMPLERS).distinct(),
    val nsfw: Boolean = false,
    val censorNsfw: Boolean = true,
    val imageCount: Int = 1,
    val loras: List<AttachedLora> = emptyList(),
    val tis: List<AttachedTi> = emptyList(),
    val queuePaused: Boolean = false,
    val civitaiResultsLoras: List<AttachedLora> = emptyList(),
    val civitaiResultsTis: List<AttachedTi> = emptyList(),
    val civitaiLoading: Boolean = false,
    val userStats: UserStatsSummary = UserStatsSummary(authMode = net.hordeslop.studio.domain.AuthMode.ANONYMOUS),
    val hordePerformance: HordePerformanceSummary? = null,
    val loadingModels: Boolean = false,
    val toastMessage: String? = null,
    val clipSkipInAdvanced: Boolean = true,
    val hiresFix: Boolean = false,
) {
    val activeStyles: List<HordeStyle>
        get() = selectedStyleIds
            .mapNotNull { id -> styles.firstOrNull { it.id == id } }
            .ifEmpty { listOfNotNull(styles.firstOrNull { it.id == selectedStyleId }) }

    val activeStyle: HordeStyle?
        get() = activeStyles.firstOrNull()

    val primaryModel: HordeModel
        get() = models.firstOrNull { it.name == selectedModels.firstOrNull() } ?: models.first()

    fun isFieldLocked(lock: StyleFieldLock): Boolean =
        activeStyles.any { it.id != "none" && lock in it.lockedFields }

    fun withRecomputedClipSkipLocation(): StudioUiState =
        copy(clipSkipInAdvanced = shouldMoveClipSkipToAdvanced(primaryModel, clipSkip, activeStyle))

    companion object {
        fun shouldMoveClipSkipToAdvanced(
            model: HordeModel,
            clipSkip: Int,
            activeStyle: HordeStyle? = null,
        ): Boolean {
            val isPony = model.name.contains("pony", ignoreCase = true) ||
                model.baseline.contains("pony", ignoreCase = true)
            val styleClip = activeStyle?.takeIf { it.id != "none" }?.clipSkip
            val defaultSkip = when {
                styleClip != null -> styleClip
                isPony -> 2
                else -> model.defaultClipSkip
            }
            return clipSkip == defaultSkip
        }
    }
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

    val pendingGallerySearchQuery: StateFlow<String?> = app.container.pendingGallerySearchQuery.asStateFlow()
    val singleActivityDestination: StateFlow<ActivityDestination> = app.container.singleActivityDestination.asStateFlow()

    init {
        refreshCatalogs(force = false)
        refreshUserStats()
        refreshQueueTracking()
    }

    fun clearToast() {
        _studio.update { it.copy(toastMessage = null) }
    }

    fun showToast(message: String) {
        _studio.update { it.copy(toastMessage = message) }
    }

    fun refreshCatalogs(force: Boolean = true) {
        if (!force && app.container.catalogsLoadedOnce) return
        viewModelScope.launch {
            _studio.update { it.copy(loadingModels = true) }
            val (fetchedModels, fetchedStyles, fetchedSamplers) = coroutineScope {
                val modelsDeferred = async { runCatching { repo.fetchModels() }.getOrNull() }
                val stylesDeferred = async { runCatching { repo.fetchStyles() }.getOrNull() }
                val samplersDeferred = async { runCatching { repo.fetchSamplers() }.getOrNull() }
                Triple(modelsDeferred.await(), stylesDeferred.await(), samplersDeferred.await())
            }
            if (!fetchedModels.isNullOrEmpty() || (fetchedStyles?.size ?: 0) > 1) {
                app.container.catalogsLoadedOnce = true
            }
            _studio.update { state ->
                val nextModels = fetchedModels?.ifEmpty { state.models } ?: state.models
                val nextSelected = state.selectedModels.filter { sel -> nextModels.any { it.name == sel } }
                    .ifEmpty { listOf(nextModels.first().name) }
                state.copy(
                    models = nextModels,
                    selectedModels = nextSelected,
                    styles = fetchedStyles ?: state.styles,
                    recommendedSamplers = fetchedSamplers?.first ?: state.recommendedSamplers,
                    allSamplers = fetchedSamplers?.second ?: state.allSamplers,
                    loadingModels = false,
                ).withRecomputedClipSkipLocation()
            }
        }
    }

    fun refreshUserStats() {
        viewModelScope.launch {
            coroutineScope {
                val userDeferred = async { runCatching { repo.fetchUserStats() }.getOrNull() }
                val perfDeferred = async { runCatching { repo.fetchPerformance() }.getOrNull() }
                val summary = userDeferred.await()
                val perf = perfDeferred.await()
                _studio.update {
                    it.copy(
                        userStats = summary ?: it.userStats,
                        hordePerformance = perf ?: it.hordePerformance,
                    )
                }
            }
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
    fun updateHiresFix(value: Boolean) = _studio.update { it.copy(hiresFix = value) }
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

    fun setMultiModelEnabled(enabled: Boolean) {
        _studio.update { state ->
            val trimmed = if (!enabled && state.selectedModels.size > 1) {
                listOf(state.selectedModels.first())
            } else {
                state.selectedModels
            }
            state.copy(multiModelEnabled = enabled, selectedModels = trimmed)
        }
    }

    fun toggleModelSelection(modelName: String) {
        _studio.update { state ->
            if (!state.multiModelEnabled) {
                return@update state.copy(selectedModels = listOf(modelName)).withRecomputedClipSkipLocation()
            }
            val exists = modelName in state.selectedModels
            val next = if (exists) {
                if (state.selectedModels.size > 1) state.selectedModels - modelName else state.selectedModels
            } else {
                state.selectedModels + modelName
            }
            state.copy(selectedModels = next).withRecomputedClipSkipLocation()
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
            ).withRecomputedClipSkipLocation()
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

    fun setMultiStyleEnabled(enabled: Boolean) {
        _studio.update { state ->
            val trimmed = if (!enabled && state.selectedStyleIds.size > 1) {
                listOf(state.selectedStyleIds.first())
            } else {
                state.selectedStyleIds
            }
            state.copy(
                multiStyleEnabled = enabled,
                selectedStyleId = trimmed.firstOrNull() ?: "none",
                selectedStyleIds = trimmed,
            )
        }
    }

    fun toggleStyleSelection(style: HordeStyle) {
        _studio.update { state ->
            if (!state.multiStyleEnabled) {
                return@update applySingleStyleToState(state, style)
            }
            if (style.id == "none") {
                return@update applyRawPromptStyleToState(state)
            }
            val currentIds = state.selectedStyleIds.filter { it != "none" }
            val adding = style.id !in currentIds
            val nextIds = if (adding) {
                currentIds + style.id
            } else {
                (currentIds - style.id).ifEmpty { listOf("none") }
            }
            state.copy(
                selectedStyleId = nextIds.firstOrNull() ?: "none",
                selectedStyleIds = nextIds,
                steps = if (adding) style.steps ?: state.steps else state.steps,
                cfg = if (adding) style.cfg ?: state.cfg else state.cfg,
                sampler = if (adding) style.sampler ?: state.sampler else state.sampler,
                scheduler = if (adding) style.scheduler ?: state.scheduler else state.scheduler,
                clipSkip = if (adding) style.clipSkip ?: state.clipSkip else state.clipSkip,
                width = if (adding) style.width ?: state.width else state.width,
                height = if (adding) style.height ?: state.height else state.height,
            ).withRecomputedClipSkipLocation()
        }
    }

    private fun applyRawPromptStyleToState(state: StudioUiState): StudioUiState {
        val sdName = state.models.firstOrNull { it.name.equals("stable_diffusion", ignoreCase = true) || it.name.equals("Stable Diffusion", ignoreCase = true) }?.name
            ?: "stable_diffusion"
        val targetModel = state.models.firstOrNull { it.name == sdName }
            ?: HordeModel(
                name = "stable_diffusion",
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
                suggestedHeight = 512,
            )
        val updatedModels = if (targetModel.name !in state.models.map { it.name }) state.models + targetModel else state.models
        return state.copy(
            models = updatedModels,
            selectedStyleId = "none",
            selectedStyleIds = listOf("none"),
            selectedModels = listOf(targetModel.name),
            width = 512,
            height = 512,
            steps = targetModel.defaultSteps,
            clipSkip = targetModel.defaultClipSkip,
            sampler = targetModel.defaultSampler,
            scheduler = targetModel.defaultScheduler,
        ).withRecomputedClipSkipLocation()
    }

    private fun applySingleStyleToState(state: StudioUiState, style: HordeStyle): StudioUiState {
        if (style.id == "none") {
            return applyRawPromptStyleToState(state)
        }
        val nextModels = style.model?.let { m ->
            state.models.firstOrNull { it.name.equals(m, ignoreCase = true) }?.name?.let(::listOf)
                ?: state.selectedModels
        } ?: state.selectedModels
        return state.copy(
            selectedStyleId = style.id,
            selectedStyleIds = listOf(style.id),
            selectedModels = nextModels,
            steps = style.steps ?: state.steps,
            cfg = style.cfg ?: state.cfg,
            sampler = style.sampler ?: state.sampler,
            scheduler = style.scheduler ?: state.scheduler,
            clipSkip = style.clipSkip ?: state.clipSkip,
            width = style.width ?: state.width,
            height = style.height ?: state.height,
        ).withRecomputedClipSkipLocation()
    }

    fun selectStyle(style: HordeStyle) {
        _studio.update { state -> applySingleStyleToState(state, style) }
    }

    fun resetAllParametersToDefaults() {
        _studio.update { state ->
            val updated = applyRawPromptStyleToState(state)
            updated.copy(
                prompt = TemplatePrompts.pickRandomTemplatePrompt(exclude = state.prompt),
                negativePrompt = "",
                seed = "",
                nsfw = false,
                censorNsfw = true,
                imageCount = 1,
                loras = emptyList(),
                tis = emptyList(),
                toastMessage = "Reset all fields",
            )
        }
    }

    fun resetPromptToTemplate() {
        _studio.update { state ->
            state.copy(
                prompt = TemplatePrompts.pickRandomTemplatePrompt(exclude = state.prompt),
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
            ).withRecomputedClipSkipLocation()
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
                selectedStyleIds = listOf("none"),
            ).withRecomputedClipSkipLocation()
        }
    }

    fun applyParsedImageMetadata(meta: ParsedImageMetadata, toastOverride: String? = null) {
        val message = toastOverride ?: "Applied metadata from image to Studio"
        _studio.update { state ->
            val nextModels = if (!meta.model.isNullOrBlank()) {
                val matched = state.models.firstOrNull { it.name.equals(meta.model, ignoreCase = true) }?.name
                listOf(matched ?: meta.model)
            } else {
                state.selectedModels
            }
            state.copy(
                prompt = meta.prompt.ifBlank { state.prompt },
                negativePrompt = meta.negativePrompt,
                selectedModels = nextModels,
                seed = meta.seed ?: state.seed,
                steps = meta.steps ?: state.steps,
                cfg = meta.cfg ?: state.cfg,
                clipSkip = meta.clipSkip ?: state.clipSkip,
                sampler = meta.sampler?.let { s ->
                    state.allSamplers.firstOrNull { it.equals(s, ignoreCase = true) } ?: s
                } ?: state.sampler,
                scheduler = meta.scheduler?.let { sc ->
                    HordeConstants.OFFICIAL_SCHEDULERS.firstOrNull { it.equals(sc, ignoreCase = true) } ?: sc
                } ?: state.scheduler,
                width = meta.width ?: state.width,
                height = meta.height ?: state.height,
                selectedStyleId = "none",
                selectedStyleIds = listOf("none"),
                toastMessage = message,
            ).withRecomputedClipSkipLocation()
        }
        showToast(message)
    }

    fun importExternalImage(uri: Uri, meta: ParsedImageMetadata) {
        viewModelScope.launch {
            runCatching {
                repo.importExternalImage(uri, meta)
            }.onSuccess {
                showToast("Added image to gallery")
            }.onFailure { err ->
                showToast("Import failed: ${err.message ?: "Unable to save image"}")
            }
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

    fun buildCurrentStudioSpec(st: StudioUiState = _studio.value): StudioSubmissionSpec =
        StudioSubmissionSpec(
            prompt = st.prompt,
            negativePrompt = st.negativePrompt,
            selectedModels = st.selectedModels,
            dispatchMode = st.dispatchMode,
            activeStyle = st.activeStyle,
            activeStyles = st.activeStyles,
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
            hiresFix = st.hiresFix,
        )

    fun copyCurrentRequestAsCurl() {
        val st = _studio.value
        val chunks = StyleContractEngine.buildSubmissionChunks(buildCurrentStudioSpec(st))
        val firstPayload = chunks.firstOrNull()?.payload ?: return
        val s = settings.value
        val curl = StyleContractEngine.buildCurlCommand(s.apiBaseUrl, s.hordeApiKey, firstPayload)
        repo.storageManager.copyText("curl command", curl)
        showToast("Copied curl command")
    }

    fun copyJsonRequestAsCurl(requestJsonString: String) {
        val parsed = runCatching { Json.parseToJsonElement(requestJsonString).jsonObject }.getOrNull()
        if (parsed == null) {
            showToast("Request JSON unavailable")
            return
        }
        val s = settings.value
        val curl = StyleContractEngine.buildCurlCommand(s.apiBaseUrl, s.hordeApiKey, parsed)
        repo.storageManager.copyText("curl command", curl)
        showToast("Copied curl command")
    }

    fun prepareRawSubmissionPayloads(): List<String> =
        StyleContractEngine.buildSubmissionChunks(buildCurrentStudioSpec())
            .map { it.payloadJsonString }

    fun submitRawGenerationPayloads(rawJsons: List<String>) {
        val st = _studio.value
        viewModelScope.launch {
            runCatching {
                repo.submitRawGenerationPayloads(buildCurrentStudioSpec(st), rawJsons)
            }.onSuccess { jobs ->
                HordeJobService.startPolling(app)
                showToast("Queued ${jobs.size} request(s)")
            }.onFailure { err ->
                showToast("Request failed: ${err.message ?: "Invalid JSON or network error"}")
            }
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
                repo.submitGeneration(buildCurrentStudioSpec(st))
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

    fun dismissJob(jobId: String) {
        viewModelScope.launch { repo.dismissJob(jobId) }
    }

    fun clearCompletedJobs() {
        viewModelScope.launch { repo.clearCompletedJobs() }
    }

    fun refreshQueueTracking() {
        viewModelScope.launch {
            runCatching { repo.syncDismissedJobs() }
            val hasVisiblePending = repo.getTrackedJobsForPolling().any {
                !it.dismissed && net.hordeslop.studio.domain.JobStatus.fromStorage(it.status).isRunnable
            }
            if (hasVisiblePending) {
                HordeJobService.startPolling(app)
            }
        }
    }

    fun openRequestInGallery(requestId: String) {
        app.container.pendingGallerySearchQuery.value = requestId
    }

    fun clearPendingGallerySearchQuery() {
        app.container.pendingGallerySearchQuery.value = null
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

    fun setImageAsWallpaper(uriString: String, whichFlags: Int) {
        viewModelScope.launch {
            val ok = repo.storageManager.setAsWallpaper(uriString, whichFlags)
            showToast(if (ok) "Wallpaper updated" else "Failed to set wallpaper")
        }
    }

    // Settings mutations
    fun saveHordeApiKey(key: String) {
        viewModelScope.launch {
            repo.settingsStore.setHordeApiKey(key)
            refreshUserStats()
            refreshCatalogs(force = true)
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

    fun setAutoClearCompletedQueue(enabled: Boolean) {
        viewModelScope.launch { repo.settingsStore.setAutoClearCompletedQueue(enabled) }
    }

    fun setSingleActivityDestination(destination: ActivityDestination) {
        app.container.singleActivityDestination.value = destination
    }

    fun setUseSeparateActivities(enabled: Boolean) {
        viewModelScope.launch {
            repo.settingsStore.setUseSeparateActivities(enabled)
            app.container.currentSettings.set(
                app.container.currentSettings.get().copy(useSeparateActivities = enabled),
            )
            if (!enabled) {
                app.container.singleActivityDestination.value = ActivityDestination.SETTINGS
            }
        }
    }

    fun setShowRawRequestReview(enabled: Boolean) {
        viewModelScope.launch { repo.settingsStore.setShowRawRequestReview(enabled) }
    }

    fun setShowGalleryPromptOverlay(enabled: Boolean) {
        viewModelScope.launch { repo.settingsStore.setShowGalleryPromptOverlay(enabled) }
    }

    fun setGalleryColumns(columns: Int) {
        viewModelScope.launch { repo.settingsStore.setGalleryColumns(columns) }
    }

    fun setGalleryPagedMode(enabled: Boolean) {
        viewModelScope.launch { repo.settingsStore.setGalleryPagedMode(enabled) }
    }

    fun setGlobalRateLimitSeconds(seconds: Int) {
        val clamped = seconds.coerceIn(1, 30)
        app.container.currentSettings.set(
            app.container.currentSettings.get().copy(globalRateLimitSeconds = clamped),
        )
        viewModelScope.launch { repo.settingsStore.setGlobalRateLimitSeconds(clamped) }
    }

    fun saveStylesRepoUrls(urls: List<String>) {
        viewModelScope.launch {
            repo.settingsStore.setStylesRepoUrls(urls)
            refreshCatalogs(force = true)
        }
    }

    fun resetStylesRepoUrls() {
        viewModelScope.launch {
            repo.settingsStore.resetStylesRepoUrls()
            refreshCatalogs(force = true)
        }
    }

    fun setLauncherIcon(icon: LauncherIconChoice) {
        viewModelScope.launch {
            repo.settingsStore.setLauncherIcon(icon)
            LauncherIconManager.applyLauncherIcon(app, icon)
        }
    }

    fun setStorageDownloads() {
        viewModelScope.launch {
            repo.settingsStore.setStorageDownloads()
            repo.syncNoMediaFiles()
        }
    }

    fun setStorageSafTree(uriString: String, label: String) {
        viewModelScope.launch {
            repo.settingsStore.setStorageSafTree(uriString, label)
            repo.syncNoMediaFiles()
        }
    }

    fun setKeepImagesInInternalStorage(enabled: Boolean) {
        viewModelScope.launch { repo.settingsStore.setKeepImagesInInternalStorage(enabled) }
    }

    fun setPreventMediaIndexing(enabled: Boolean) {
        viewModelScope.launch {
            repo.settingsStore.setPreventMediaIndexing(enabled)
            repo.syncNoMediaFiles()
        }
    }

    fun moveImageToExternalStorage(image: SavedImage) {
        viewModelScope.launch {
            val dest = repo.moveImageToExternalStorage(image)
            showToast(if (dest != null) "Moved to $dest" else "Failed to move image")
        }
    }

    fun moveAllInternalImagesToExternal() {
        viewModelScope.launch {
            val count = repo.moveAllInternalImagesToExternalStorage()
            showToast(if (count > 0) "Moved $count image(s) to external storage" else "No internal images to move")
        }
    }

    fun setAdvancedCollapsed(collapsed: Boolean) {
        viewModelScope.launch { repo.settingsStore.setAdvancedCollapsed(collapsed) }
    }

    fun setAiNoticeDismissed(dismissed: Boolean) {
        viewModelScope.launch { repo.settingsStore.setAiNoticeDismissed(dismissed) }
    }

    fun exportDatabase(uri: Uri) {
        viewModelScope.launch {
            runCatching { repo.exportDatabaseToUri(uri) }
                .onSuccess { counts ->
                    showToast(
                        "Exported ${counts.promptHistoryCount} prompts, ${counts.savedImagesCount} images, ${counts.modelDefaultsCount} model defaults",
                    )
                }
                .onFailure { err ->
                    showToast("Export failed: ${err.message ?: "Unable to write file"}")
                }
        }
    }

    fun importDatabase(uri: Uri, replaceExisting: Boolean) {
        viewModelScope.launch {
            runCatching { repo.importDatabaseFromUri(uri, replaceExisting) }
                .onSuccess { counts ->
                    refreshCatalogs(force = true)
                    refreshQueueTracking()
                    showToast(
                        "Imported ${counts.promptHistoryCount} prompts, ${counts.savedImagesCount} images, ${counts.modelDefaultsCount} model defaults",
                    )
                }
                .onFailure { err ->
                    showToast("Import failed: ${err.message ?: "Invalid backup file"}")
                }
        }
    }

    fun completeOnboarding(keyInput: String) {
        viewModelScope.launch {
            repo.settingsStore.setHordeApiKey(keyInput)
            repo.settingsStore.setOnboardingCompleted(true)
            refreshUserStats()
        }
    }
}
