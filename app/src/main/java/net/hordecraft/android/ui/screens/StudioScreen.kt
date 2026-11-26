package net.hordeslop.studio.ui.screens

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import net.hordeslop.studio.data.local.AppSettings
import net.hordeslop.studio.domain.ActivityDestination
import net.hordeslop.studio.domain.AttachedLora
import net.hordeslop.studio.domain.GenerateButtonPlacement
import net.hordeslop.studio.domain.HordeConstants
import net.hordeslop.studio.domain.HordeJob
import net.hordeslop.studio.domain.JobStatus
import net.hordeslop.studio.domain.LoraTriggerMode
import net.hordeslop.studio.domain.PromptRecord
import net.hordeslop.studio.domain.PromptTarget
import net.hordeslop.studio.domain.SizeMathEngine
import net.hordeslop.studio.domain.SliderMath
import net.hordeslop.studio.domain.SliderZone
import net.hordeslop.studio.domain.StyleFieldLock
import net.hordeslop.studio.domain.TemplatePrompts
import net.hordeslop.studio.domain.TiInjectMode
import net.hordeslop.studio.ui.MainViewModel
import net.hordeslop.studio.ui.StudioUiState
import net.hordeslop.studio.ui.components.launchActivityForDestination
import net.hordeslop.studio.ui.theme.HordeColors
import net.hordeslop.studio.ui.theme.sliderZoneColor
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StudioScreen(
    vm: MainViewModel,
    studio: StudioUiState,
    settings: AppSettings,
    history: List<PromptRecord>,
    jobs: List<HordeJob>,
) {
    val context = LocalContext.current
    val config = LocalConfiguration.current
    val isWideLayout = config.screenWidthDp >= 600
    val isTablet = config.screenWidthDp >= 720

    var promptCollapsed by remember { mutableStateOf(false) }
    var modelsCollapsed by remember { mutableStateOf(false) }
    var samplingCollapsed by remember { mutableStateOf(false) }
    var loraSectionCollapsed by remember { mutableStateOf(true) }
    var collapsedLoraIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var collapsedTiIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    var showHistoryModalMode by remember { mutableStateOf<String?>(null) } // "history" | "favorites" | null
    var showCivitaiImageModal by remember { mutableStateOf(false) }
    var showFullscreenPrompt by remember { mutableStateOf(false) }
    var showModelModal by remember { mutableStateOf(false) }
    var showStylesModal by remember { mutableStateOf(false) }
    var showSizeModal by remember { mutableStateOf(false) }
    var civitaiSearchTab by remember { mutableStateOf<String?>(null) } // "lora" | "ti" | null
    var loraDetailTarget by remember { mutableStateOf<AttachedLora?>(null) }
    var pendingRawReviewPayloads by remember { mutableStateOf<List<String>?>(null) }
    var viewOnlyRawJsonPayloads by remember { mutableStateOf<List<String>?>(null) }

    val primaryModel = studio.primaryModel
    val activeStyle = studio.activeStyle
    val activeNonEmptyStyles = studio.activeStyles.filter { it.id != "none" }
    val isMultiStyle = studio.multiStyleEnabled && activeNonEmptyStyles.size > 1
    val enabledParamAlpha = if (isMultiStyle) 0.90f else 1.0f

    LaunchedEffect(Unit) {
        vm.refreshQueueTracking()
    }

    val renderGenerateBlock: @Composable (Boolean) -> Unit = { isPinnedTop ->
        GenerateActionBar(
            imageCount = studio.imageCount,
            onChangeCount = vm::updateImageCount,
            onResetAll = vm::resetAllParametersToDefaults,
            onViewJson = { viewOnlyRawJsonPayloads = vm.prepareRawSubmissionPayloads() },
            onGenerate = {
                if (studio.prompt.isBlank()) {
                    vm.showToast("Enter a prompt first")
                } else if (settings.showRawRequestReview) {
                    pendingRawReviewPayloads = vm.prepareRawSubmissionPayloads()
                } else {
                    vm.submitGenerate()
                }
            },
            isWideLayout = isWideLayout,
            isPinnedTop = isPinnedTop,
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (settings.generateButtonPlacement == GenerateButtonPlacement.TOP) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(HordeColors.BarSurface)
                    .border(1.dp, HordeColors.BorderSubtle)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                renderGenerateBlock(true)
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            studio.toastMessage?.let { msg ->
                LaunchedEffect(msg) {
                    kotlinx.coroutines.delay(2_400L)
                    vm.clearToast()
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(HordeColors.TealContainer)
                        .border(1.dp, HordeColors.TealPrimary.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text(msg, fontSize = 12.sp, color = HordeColors.TealSoft)
                }
            }

            // 1. Prompt Section (Favorites & History above Positive Prompt, inline actions, faded neg when CFG=1)
            StudioCard {
                CollapsibleSectionHeader(
                    title = "Prompt",
                    collapsed = promptCollapsed,
                    onToggle = { promptCollapsed = !promptCollapsed },
                    trailing = {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            SmallHeaderChip(
                                label = "Reset",
                                icon = { Icon(Icons.Outlined.RestartAlt, null, modifier = Modifier.size(14.dp), tint = HordeColors.TextSecondary) },
                                onClick = vm::resetPromptToTemplate,
                            )
                            SmallHeaderChip(
                                label = "Favorites",
                                icon = { Icon(Icons.Outlined.StarBorder, null, modifier = Modifier.size(14.dp), tint = HordeColors.AmberText) },
                                onClick = { showHistoryModalMode = "favorites" },
                            )
                            SmallHeaderChip(
                                label = "History",
                                icon = { Icon(Icons.Outlined.History, null, modifier = Modifier.size(14.dp), tint = HordeColors.TextSecondary) },
                                onClick = { showHistoryModalMode = "history" },
                            )
                            SmallHeaderChip(
                                label = "Civitai",
                                icon = { Icon(Icons.Outlined.CloudDownload, null, modifier = Modifier.size(14.dp), tint = HordeColors.TealSoft) },
                                onClick = { showCivitaiImageModal = true },
                            )
                        }
                    },
                )
                if (!promptCollapsed) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        OutlinedTextField(
                            value = studio.prompt,
                            onValueChange = vm::updatePrompt,
                            minLines = 3,
                            maxLines = 5,
                            placeholder = { Text("Describe image...", fontSize = 13.sp) },
                            modifier = Modifier.weight(1f),
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            TinySquareAction(Icons.Outlined.Fullscreen, "Expand") { showFullscreenPrompt = true }
                            TinySquareAction(Icons.Outlined.ContentCopy, "Copy") {
                                vm.copyText("prompt", studio.prompt)
                            }
                            TinySquareAction(Icons.Outlined.ContentPaste, "Paste") {
                                pasteClipboardText(context)?.let(vm::updatePrompt)
                            }
                            TinySquareAction(Icons.Outlined.Clear, "Clear") { vm.updatePrompt("") }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .alpha(if (studio.cfg <= 1f) 0.45f else 1f),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        OutlinedTextField(
                            value = studio.negativePrompt,
                            onValueChange = vm::updateNegativePrompt,
                            minLines = 2,
                            maxLines = 3,
                            label = { Text("Negative", fontSize = 12.sp) },
                            modifier = Modifier.weight(1f),
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            TinySquareAction(Icons.Outlined.ContentCopy, "Copy") {
                                vm.copyText("negative", studio.negativePrompt)
                            }
                            TinySquareAction(Icons.Outlined.ContentPaste, "Paste") {
                                pasteClipboardText(context)?.let(vm::updateNegativePrompt)
                            }
                            TinySquareAction(Icons.Outlined.Clear, "Clear") { vm.updateNegativePrompt("") }
                        }
                    }
                }
            }

            // 2. Styles & Models + Selected Size Readout
            StudioCard {
                CollapsibleSectionHeader(
                    title = "Style & Models",
                    collapsed = modelsCollapsed,
                    onToggle = { modelsCollapsed = !modelsCollapsed },
                )
                if (!modelsCollapsed) {
                    PickerRowButton(
                        label = "Style",
                        value = when {
                            activeNonEmptyStyles.size > 1 -> "${activeNonEmptyStyles.first().name} +${activeNonEmptyStyles.size - 1}"
                            activeNonEmptyStyles.size == 1 -> activeNonEmptyStyles.first().name
                            else -> activeStyle?.name ?: "Raw Prompt"
                        },
                        locked = false,
                        onClick = { showStylesModal = true },
                    )
                    Box(modifier = Modifier.alpha(if (!studio.isFieldLocked(StyleFieldLock.MODEL)) enabledParamAlpha else 1f)) {
                        PickerRowButton(
                            label = "Models",
                            value = if (studio.selectedModels.size == 1) studio.selectedModels.first()
                            else "${studio.selectedModels.first()} +${studio.selectedModels.size - 1}",
                            locked = studio.isFieldLocked(StyleFieldLock.MODEL),
                            onClick = { showModelModal = true },
                        )
                    }
                    Box(modifier = Modifier.alpha(if (!studio.isFieldLocked(StyleFieldLock.SIZE)) enabledParamAlpha else 1f)) {
                        PickerRowButton(
                            label = "Size",
                            value = "${studio.width} × ${studio.height} · ${SizeMathEngine.formatAspectRatioLabel(studio.width, studio.height)} · ${SizeMathEngine.formatMegapixels(studio.width, studio.height)}",
                            locked = studio.isFieldLocked(StyleFieldLock.SIZE),
                            onClick = { if (!studio.isFieldLocked(StyleFieldLock.SIZE)) showSizeModal = true },
                        )
                    }
                }
            }

            // 3. Sampling & Collapsible Advanced
            StudioCard {
                CollapsibleSectionHeader(
                    title = "Sampling",
                    collapsed = samplingCollapsed,
                    onToggle = { samplingCollapsed = !samplingCollapsed },
                )
                if (!samplingCollapsed) {
                    // Steps (Logarithmic 1..50, mid half 10..25, manual up to 100)
                    val stepsZone = SliderMath.stepsZone(studio.steps, primaryModel.suggestedMinSteps, primaryModel.suggestedMaxSteps)
                    // CFG (Logarithmic 1..20, first 2/3 1..6)
                    val cfgZone = SliderMath.cfgZone(studio.cfg, primaryModel.suggestedMinCfg, primaryModel.suggestedMaxCfg)
                    val renderStepsSlider: @Composable () -> Unit = {
                        Box(modifier = Modifier.alpha(if (!studio.isFieldLocked(StyleFieldLock.STEPS)) enabledParamAlpha else 1f)) {
                            LogIntSliderRow(
                                label = "Steps",
                                value = studio.steps,
                                sliderPosition = SliderMath.stepsToSlider(studio.steps),
                                onSliderChange = { pos -> vm.updateSteps(SliderMath.sliderToSteps(pos)) },
                                onManualChange = vm::updateSteps,
                                minVal = 1,
                                maxVal = SliderMath.STEPS_MANUAL_MAX,
                                zone = stepsZone,
                                enabled = !studio.isFieldLocked(StyleFieldLock.STEPS),
                            )
                        }
                    }
                    val renderCfgSlider: @Composable () -> Unit = {
                        Box(modifier = Modifier.alpha(if (!studio.isFieldLocked(StyleFieldLock.CFG)) enabledParamAlpha else 1f)) {
                            LogFloatSliderRow(
                                label = "CFG Scale",
                                value = studio.cfg,
                                sliderPosition = SliderMath.cfgToSlider(studio.cfg),
                                onSliderChange = { pos -> vm.updateCfg(SliderMath.sliderToCfg(pos)) },
                                onManualChange = vm::updateCfg,
                                decimals = 1,
                                zone = cfgZone,
                                enabled = !studio.isFieldLocked(StyleFieldLock.CFG),
                            )
                        }
                    }
                    if (isWideLayout) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Box(modifier = Modifier.weight(1f)) { renderStepsSlider() }
                            Box(modifier = Modifier.weight(1f)) { renderCfgSlider() }
                        }
                    } else {
                        renderStepsSlider()
                        renderCfgSlider()
                    }

                    // Clip Skip (1..12 slider + numeric input)
                    val clipZone = SliderMath.clipSkipZone(studio.clipSkip)
                    val renderClipSkipSlider: @Composable () -> Unit = {
                        Box(modifier = Modifier.alpha(if (!studio.isFieldLocked(StyleFieldLock.CLIP_SKIP)) enabledParamAlpha else 1f)) {
                            LinearIntSliderRow(
                                label = "Clip Skip",
                                value = studio.clipSkip,
                                minVal = SliderMath.CLIP_SKIP_MIN,
                                maxVal = SliderMath.CLIP_SKIP_MAX,
                                onValueChange = vm::updateClipSkip,
                                zone = clipZone,
                                enabled = !studio.isFieldLocked(StyleFieldLock.CLIP_SKIP),
                            )
                        }
                    }
                    if (!studio.clipSkipInAdvanced) {
                        renderClipSkipSlider()
                    }

                    // Sampler Dropdown
                    Box(modifier = Modifier.alpha(if (!studio.isFieldLocked(StyleFieldLock.SAMPLER)) enabledParamAlpha else 1f)) {
                        DropdownFieldRow(
                            label = "Sampler",
                            selected = studio.sampler,
                            options = if (studio.showAllSamplers) {
                                studio.allSamplers
                            } else {
                                studio.recommendedSamplers
                            },
                            enabled = !studio.isFieldLocked(StyleFieldLock.SAMPLER),
                            onSelect = vm::updateSampler,
                            trailingHeaderAction = {
                                Text(
                                    text = if (studio.showAllSamplers) "Recommended" else "All samplers",
                                    fontSize = 11.sp,
                                    color = HordeColors.TealSoft,
                                    modifier = Modifier.clickable { vm.toggleAllSamplers() },
                                )
                            },
                        )
                    }

                    HorizontalDivider(color = HordeColors.BorderSubtle)

                    // Collapsible Advanced Section (persisted state, zero extra padding)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { vm.setAdvancedCollapsed(!settings.advancedCollapsed) },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Advanced", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = HordeColors.TextPrimary)
                        Icon(
                            if (settings.advancedCollapsed) Icons.Outlined.ExpandMore else Icons.Outlined.ExpandLess,
                            contentDescription = null,
                            tint = HordeColors.TextSecondary,
                        )
                    }

                    if (!settings.advancedCollapsed) {
                        if (isWideLayout) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.Bottom,
                            ) {
                                OutlinedTextField(
                                    value = studio.seed,
                                    onValueChange = vm::updateSeed,
                                    singleLine = true,
                                    label = { Text("Seed", fontSize = 12.sp) },
                                    placeholder = { Text("Blank = random") },
                                    modifier = Modifier.weight(1f),
                                )
                                Box(modifier = Modifier.weight(1f).alpha(if (!studio.isFieldLocked(StyleFieldLock.SCHEDULER)) enabledParamAlpha else 1f)) {
                                    DropdownFieldRow(
                                        label = "Scheduler",
                                        selected = studio.scheduler,
                                        options = HordeConstants.OFFICIAL_SCHEDULERS,
                                        enabled = !studio.isFieldLocked(StyleFieldLock.SCHEDULER),
                                        onSelect = vm::updateScheduler,
                                    )
                                }
                            }
                        } else {
                            OutlinedTextField(
                                value = studio.seed,
                                onValueChange = vm::updateSeed,
                                singleLine = true,
                                label = { Text("Seed", fontSize = 12.sp) },
                                placeholder = { Text("Blank = random") },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Box(modifier = Modifier.alpha(if (!studio.isFieldLocked(StyleFieldLock.SCHEDULER)) enabledParamAlpha else 1f)) {
                                DropdownFieldRow(
                                    label = "Scheduler",
                                    selected = studio.scheduler,
                                    options = HordeConstants.OFFICIAL_SCHEDULERS,
                                    enabled = !studio.isFieldLocked(StyleFieldLock.SCHEDULER),
                                    onSelect = vm::updateScheduler,
                                )
                            }
                        }

                        if (studio.clipSkipInAdvanced) {
                            renderClipSkipSlider()
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { vm.updateNsfw(!studio.nsfw) },
                            ) {
                                Checkbox(checked = studio.nsfw, onCheckedChange = vm::updateNsfw)
                                Text("Allow NSFW", fontSize = 12.sp, color = HordeColors.TextSecondary)
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { vm.updateCensorNsfw(!studio.censorNsfw) },
                            ) {
                                Checkbox(checked = studio.censorNsfw, onCheckedChange = vm::updateCensorNsfw)
                                Text("Censor NSFW", fontSize = 12.sp, color = HordeColors.TextSecondary)
                            }
                        }
                        val supportsHiresFix = remember(studio.primaryModel) {
                            val b = studio.primaryModel.baseline.lowercase()
                            val n = studio.primaryModel.name.lowercase()
                            b.contains("sd 1") || b.contains("sd 2") || b.contains("sdxl") ||
                                b.contains("pony") || b.contains("illustrious") || b.contains("noob") ||
                                n.contains("sd 1") || n.contains("sd 2") || n.contains("sdxl") ||
                                n.contains("pony") || n.contains("illustrious") || n.contains("noob")
                        }
                        if (supportsHiresFix) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { vm.updateHiresFix(!studio.hiresFix) },
                            ) {
                                Checkbox(checked = studio.hiresFix, onCheckedChange = vm::updateHiresFix)
                                Text("Hires-Fix", fontSize = 12.sp, color = HordeColors.TextSecondary)
                            }
                        }
                        OutlinedButton(
                            onClick = vm::copyCurrentRequestAsCurl,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Outlined.ContentCopy, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Copy API request as curl", fontSize = 12.sp)
                        }
                    }
                }
            }

            // 4. Ordered LoRAs & Embeddings
            StudioCard {
                CollapsibleSectionHeader(
                    title = "LoRAs & Embeddings (${studio.loras.size}/${HordeConstants.MAX_ATTACHED_LORAS} · ${studio.tis.size}/${HordeConstants.MAX_ATTACHED_TIS})",
                    collapsed = loraSectionCollapsed,
                    onToggle = { loraSectionCollapsed = !loraSectionCollapsed },
                )
                if (!loraSectionCollapsed) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { civitaiSearchTab = "lora" },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Outlined.Add, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Add LoRA", fontSize = 12.sp)
                        }
                        OutlinedButton(
                            onClick = { civitaiSearchTab = "ti" },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Outlined.Add, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Add Embedding", fontSize = 12.sp)
                        }
                    }

                    // Ordered LoRAs
                    studio.loras.forEachIndexed { idx, lora ->
                        val collapsed = lora.id in collapsedLoraIds
                        Surface(
                            color = HordeColors.SubCardSurface,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, HordeColors.BorderSubtle, RoundedCornerShape(12.dp)),
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        text = lora.name,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = HordeColors.TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable {
                                                collapsedLoraIds = if (collapsed) collapsedLoraIds - lora.id else collapsedLoraIds + lora.id
                                            },
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(onClick = { vm.moveLora(idx, -1) }, enabled = idx > 0, modifier = Modifier.size(28.dp)) {
                                            Icon(Icons.Outlined.ArrowUpward, "Up", modifier = Modifier.size(15.dp))
                                        }
                                        IconButton(onClick = { vm.moveLora(idx, 1) }, enabled = idx < studio.loras.lastIndex, modifier = Modifier.size(28.dp)) {
                                            Icon(Icons.Outlined.ArrowDownward, "Down", modifier = Modifier.size(15.dp))
                                        }
                                        IconButton(
                                            onClick = {
                                                collapsedLoraIds = if (collapsed) collapsedLoraIds - lora.id else collapsedLoraIds + lora.id
                                            },
                                            modifier = Modifier.size(28.dp),
                                        ) {
                                            Icon(if (collapsed) Icons.Outlined.ExpandMore else Icons.Outlined.ExpandLess, null, modifier = Modifier.size(16.dp))
                                        }
                                        IconButton(onClick = { vm.removeLora(lora.id) }, modifier = Modifier.size(28.dp)) {
                                            Icon(Icons.Outlined.Clear, "Remove", tint = HordeColors.RoseText, modifier = Modifier.size(15.dp))
                                        }
                                    }
                                }

                                if (!collapsed) {
                                    if (isTablet) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                            Box(Modifier.weight(1f)) {
                                                LogFloatSliderRow(
                                                    label = "Model",
                                                    value = lora.strengthModel,
                                                    sliderPosition = SliderMath.strengthToSlider(lora.strengthModel),
                                                    onSliderChange = { pos ->
                                                        vm.updateLoraItem(lora.id) { it.copy(strengthModel = SliderMath.sliderToStrength(pos)) }
                                                    },
                                                    onManualChange = { v ->
                                                        vm.updateLoraItem(lora.id) { it.copy(strengthModel = v) }
                                                    },
                                                    decimals = 2,
                                                    zone = SliderMath.strengthZone(lora.strengthModel),
                                                    enabled = true,
                                                )
                                            }
                                            Box(Modifier.weight(1f)) {
                                                LogFloatSliderRow(
                                                    label = "Clip",
                                                    value = lora.strengthClip,
                                                    sliderPosition = SliderMath.strengthToSlider(lora.strengthClip),
                                                    onSliderChange = { pos ->
                                                        vm.updateLoraItem(lora.id) { it.copy(strengthClip = SliderMath.sliderToStrength(pos)) }
                                                    },
                                                    onManualChange = { v ->
                                                        vm.updateLoraItem(lora.id) { it.copy(strengthClip = v) }
                                                    },
                                                    decimals = 2,
                                                    zone = SliderMath.strengthZone(lora.strengthClip),
                                                    enabled = true,
                                                )
                                            }
                                        }
                                    } else {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(
                                                text = "Model ${String.format(Locale.US, "%.2f", lora.strengthModel)} · Clip ${String.format(Locale.US, "%.2f", lora.strengthClip)} · v${lora.modelVersionId}",
                                                fontSize = 11.sp,
                                                fontFamily = FontFamily.Monospace,
                                                color = HordeColors.TextMuted,
                                            )
                                            Text(
                                                text = "Edit",
                                                fontSize = 12.sp,
                                                color = HordeColors.TealSoft,
                                                modifier = Modifier.clickable { loraDetailTarget = lora },
                                            )
                                        }
                                    }

                                    if (lora.trainedWords.isNotEmpty()) {
                                        LoraTriggerControls(
                                            lora = lora,
                                            selectedModels = studio.selectedModels,
                                            onUpdateLora = { transform -> vm.updateLoraItem(lora.id, transform) },
                                            onInsertPrompt = { tokens ->
                                                vm.updatePrompt(TemplatePrompts.appendToken(studio.prompt, tokens.joinToString(", ")))
                                                vm.showToast("Added triggers to prompt")
                                            },
                                            onInsertNegative = { tokens ->
                                                vm.updateNegativePrompt(TemplatePrompts.appendToken(studio.negativePrompt, tokens.joinToString(", ")))
                                                vm.showToast("Added triggers to negative")
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Selected Embeddings / TIs
                    studio.tis.forEach { ti ->
                        val collapsed = ti.id in collapsedTiIds
                        val snippet = TemplatePrompts.formatEmbeddingSnippet(ti.modelId, ti.strength)
                        Surface(
                            color = HordeColors.SubCardSurface,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, HordeColors.BorderSubtle, RoundedCornerShape(12.dp)),
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        text = ti.name,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = HordeColors.TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable {
                                                collapsedTiIds = if (collapsed) collapsedTiIds - ti.id else collapsedTiIds + ti.id
                                            },
                                    )
                                    Row {
                                        IconButton(
                                            onClick = {
                                                collapsedTiIds = if (collapsed) collapsedTiIds - ti.id else collapsedTiIds + ti.id
                                            },
                                            modifier = Modifier.size(28.dp),
                                        ) {
                                            Icon(if (collapsed) Icons.Outlined.ExpandMore else Icons.Outlined.ExpandLess, null, modifier = Modifier.size(16.dp))
                                        }
                                        IconButton(onClick = { vm.removeTi(ti.id) }, modifier = Modifier.size(28.dp)) {
                                            Icon(Icons.Outlined.Clear, "Remove", tint = HordeColors.RoseText, modifier = Modifier.size(15.dp))
                                        }
                                    }
                                }

                                if (!collapsed) {
                                    Text(
                                        text = snippet,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = HordeColors.TealSoft,
                                    )
                                    LogFloatSliderRow(
                                        label = "Weight",
                                        value = ti.strength,
                                        sliderPosition = SliderMath.strengthToSlider(ti.strength),
                                        onSliderChange = { pos ->
                                            vm.updateTiItem(ti.id) { it.copy(strength = SliderMath.sliderToStrength(pos)) }
                                        },
                                        onManualChange = { v ->
                                            vm.updateTiItem(ti.id) { it.copy(strength = v) }
                                        },
                                        decimals = 2,
                                        zone = SliderMath.strengthZone(ti.strength),
                                        enabled = true,
                                    )

                                    val apiActive = ti.injectTi != TiInjectMode.NONE
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        ToggleChipButton(
                                            text = "API",
                                            selected = apiActive,
                                            onClick = {
                                                vm.updateTiItem(ti.id) {
                                                    if (it.injectTi == TiInjectMode.NONE) {
                                                        it.copy(
                                                            injectTi = if (it.lastTarget == PromptTarget.PROMPT) TiInjectMode.PROMPT else TiInjectMode.NEGPROMPT,
                                                        )
                                                    } else {
                                                        it.copy(injectTi = TiInjectMode.NONE)
                                                    }
                                                }
                                            },
                                        )
                                        ToggleChipButton(
                                            text = if (apiActive) "Prompt" else "+ Prompt",
                                            selected = apiActive && ti.injectTi == TiInjectMode.PROMPT,
                                            onClick = {
                                                if (apiActive) {
                                                    vm.updateTiItem(ti.id) { it.copy(injectTi = TiInjectMode.PROMPT, lastTarget = PromptTarget.PROMPT) }
                                                } else {
                                                    vm.updatePrompt(TemplatePrompts.appendToken(studio.prompt, snippet))
                                                    vm.showToast("Added embedding to prompt")
                                                }
                                            },
                                        )
                                        ToggleChipButton(
                                            text = if (apiActive) "Neg" else "+ Neg",
                                            selected = apiActive && ti.injectTi == TiInjectMode.NEGPROMPT,
                                            onClick = {
                                                if (apiActive) {
                                                    vm.updateTiItem(ti.id) { it.copy(injectTi = TiInjectMode.NEGPROMPT, lastTarget = PromptTarget.NEGPROMPT) }
                                                } else {
                                                    vm.updateNegativePrompt(TemplatePrompts.appendToken(studio.negativePrompt, snippet))
                                                    vm.showToast("Added embedding to negative")
                                                }
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 5. Generate Bar Below Sampling & LoRAs when Inline/Scrolling
            if (settings.generateButtonPlacement == GenerateButtonPlacement.SCROLLING) {
                renderGenerateBlock(false)
            }

            // 6. Active & Recent Jobs Queue
            if (jobs.isNotEmpty()) {
                val hasActive = jobs.any {
                    it.status == JobStatus.QUEUED || it.status == JobStatus.PROCESSING || it.status == JobStatus.PAUSED
                }
                val hasClearable = jobs.any {
                    it.status == JobStatus.COMPLETED || it.status == JobStatus.CANCELLED_PARTIAL || it.status == JobStatus.FAILED
                }
                var queueCollapsed by rememberSaveable { mutableStateOf(false) }
                StudioCard {
                    CollapsibleSectionHeader(
                        title = "Queue (${jobs.size})",
                        collapsed = queueCollapsed,
                        onToggle = { queueCollapsed = !queueCollapsed },
                        trailing = {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                if (hasClearable) {
                                    OutlinedButton(onClick = vm::clearCompletedJobs) {
                                        Text("Clear done", fontSize = 11.sp)
                                    }
                                }
                                if (hasActive) {
                                    OutlinedButton(onClick = vm::toggleQueuePause) {
                                        Icon(
                                            if (studio.queuePaused) Icons.Outlined.PlayArrow else Icons.Outlined.Pause,
                                            null,
                                            modifier = Modifier.size(16.dp),
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(if (studio.queuePaused) "Resume" else "Pause", fontSize = 11.sp)
                                    }
                                }
                            }
                        },
                    )
                    if (!queueCollapsed) {
                        jobs.take(12).forEach { job ->
                            val isActive = job.status == JobStatus.QUEUED ||
                                job.status == JobStatus.PROCESSING ||
                                job.status == JobStatus.PAUSED
                            val statusText = when (job.status) {
                                JobStatus.QUEUED -> "Queued"
                                JobStatus.PROCESSING -> if (job.waitTimeSeconds > 0) "Wait ~${job.waitTimeSeconds}s · #${job.queuePosition}" else "Processing"
                                JobStatus.PAUSED -> "Paused"
                                JobStatus.COMPLETED -> "Completed"
                                JobStatus.CANCELLED_PARTIAL -> "Partial"
                                JobStatus.FAILED -> job.errorMessage ?: "Failed"
                            }
                            Surface(
                                color = HordeColors.SubCardSurface,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, HordeColors.BorderSubtle, RoundedCornerShape(10.dp)),
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "${job.models.joinToString(", ")} · ${job.finished}/${job.total}",
                                                fontSize = 12.sp,
                                                color = HordeColors.TextPrimary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Text(
                                                text = statusText,
                                                fontSize = 11.sp,
                                                color = HordeColors.TextMuted,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            if (job.finished > 0) {
                                                Row(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(HordeColors.TealContainer)
                                                        .clickable {
                                                            vm.openRequestInGallery(job.requestId)
                                                            launchActivityForDestination(context, ActivityDestination.GALLERY)
                                                        }
                                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                ) {
                                                    Icon(
                                                        Icons.Outlined.Collections,
                                                        contentDescription = "View in gallery",
                                                        tint = HordeColors.TealSoft,
                                                        modifier = Modifier.size(14.dp),
                                                    )
                                                    Text("View", fontSize = 11.sp, color = HordeColors.TealSoft)
                                                }
                                            }
                                            if (isActive) {
                                                IconButton(onClick = { vm.cancelJob(job.id) }, modifier = Modifier.size(28.dp)) {
                                                    Icon(Icons.Outlined.Stop, "Cancel", tint = HordeColors.RoseText, modifier = Modifier.size(16.dp))
                                                }
                                            } else {
                                                IconButton(onClick = { vm.dismissJob(job.id) }, modifier = Modifier.size(28.dp)) {
                                                    Icon(Icons.Outlined.DeleteOutline, "Clear", tint = HordeColors.TextSecondary, modifier = Modifier.size(16.dp))
                                                }
                                            }
                                        }
                                    }
                                    if (job.status == JobStatus.QUEUED || job.status == JobStatus.PROCESSING) {
                                        val progress = if (job.total > 0) job.finished.toFloat() / job.total else 0f
                                        LinearProgressIndicator(
                                            progress = { progress.coerceIn(0f, 1f) },
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (settings.generateButtonPlacement == GenerateButtonPlacement.BOTTOM) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(HordeColors.BarSurface)
                    .border(1.dp, HordeColors.BorderSubtle)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                renderGenerateBlock(false)
            }
        }
    }

    // Modals
    showHistoryModalMode?.let { mode ->
        HistoryFavoritesDialog(
            mode = mode,
            records = history,
            onSelect = {
                vm.applyHistoryRecord(it)
                showHistoryModalMode = null
            },
            onToggleFavorite = vm::toggleHistoryFavorite,
            onDismiss = { showHistoryModalMode = null },
        )
    }

    if (showCivitaiImageModal) {
        CivitaiImageImportDialog(
            onFetch = vm::fetchCivitaiImageMetadata,
            onApply = { meta, applyAll ->
                vm.applyCivitaiMetadata(meta, applyAll)
                showCivitaiImageModal = false
            },
            onDismiss = { showCivitaiImageModal = false },
        )
    }

    if (showFullscreenPrompt) {
        FullscreenPromptDialog(
            prompt = studio.prompt,
            onChange = vm::updatePrompt,
            onDismiss = { showFullscreenPrompt = false },
        )
    }

    if (showSizeModal) {
        SizePickerDialog(
            width = studio.width,
            height = studio.height,
            onApply = { w, h ->
                vm.updateSize(w, h)
                showSizeModal = false
            },
            onDismiss = { showSizeModal = false },
        )
    }

    if (showModelModal) {
        ModelPickerDialog(
            models = studio.models,
            selectedModels = studio.selectedModels,
            multiModelEnabled = studio.multiModelEnabled,
            dispatchMode = studio.dispatchMode,
            showModelPreviews = settings.showModelPreviews,
            currentSteps = studio.steps,
            currentSampler = studio.sampler,
            currentScheduler = studio.scheduler,
            currentClipSkip = studio.clipSkip,
            onToggleMultiModel = vm::setMultiModelEnabled,
            onToggleModel = vm::toggleModelSelection,
            onChangeDispatchMode = vm::updateDispatchMode,
            onTogglePreviews = vm::setShowModelPreviews,
            onSaveDefaults = vm::saveDefaultsForModel,
            onResetDefaults = vm::resetDefaultsForModel,
            onApplyModel = { m ->
                vm.applyPrimaryModelDefaults(m)
                showModelModal = false
            },
            onDismiss = { showModelModal = false },
        )
    }

    if (showStylesModal) {
        StylesPickerDialog(
            styles = studio.styles,
            selectedStyleIds = studio.selectedStyleIds,
            multiStyleEnabled = studio.multiStyleEnabled,
            onToggleMultiStyle = vm::setMultiStyleEnabled,
            onSelect = {
                vm.toggleStyleSelection(it)
                if (!studio.multiStyleEnabled) {
                    showStylesModal = false
                }
            },
            onDismiss = { showStylesModal = false },
        )
    }

    civitaiSearchTab?.let { tab ->
        CivitaiSearchDialog(
            tab = tab,
            loras = studio.civitaiResultsLoras,
            tis = studio.civitaiResultsTis,
            loading = studio.civitaiLoading,
            onSearchLoras = vm::searchCivitaiLoras,
            onSearchTis = vm::searchCivitaiTis,
            onSelectLora = { item ->
                civitaiSearchTab = null
                loraDetailTarget = item
            },
            onSelectTi = { item ->
                vm.attachTi(item)
                civitaiSearchTab = null
            },
            onDismiss = { civitaiSearchTab = null },
        )
    }

    loraDetailTarget?.let { target ->
        LoraDetailDialog(
            lora = target,
            selectedModels = studio.selectedModels,
            onAttachOrSave = { updated ->
                vm.upsertLora(updated)
                loraDetailTarget = null
            },
            onInsertPrompt = { tokens ->
                vm.updatePrompt(TemplatePrompts.appendToken(studio.prompt, tokens.joinToString(", ")))
                vm.showToast("Added triggers to prompt")
            },
            onInsertNegative = { tokens ->
                vm.updateNegativePrompt(TemplatePrompts.appendToken(studio.negativePrompt, tokens.joinToString(", ")))
                vm.showToast("Added triggers to negative")
            },
            onDismiss = { loraDetailTarget = null },
        )
    }

    pendingRawReviewPayloads?.let { initialChunks ->
        RawRequestReviewDialog(
            initialPayloads = initialChunks,
            viewOnly = false,
            onCopyJson = { text -> vm.copyText("request JSON", text) },
            onCopyCurl = vm::copyJsonRequestAsCurl,
            onConfirmSend = { editedChunks ->
                pendingRawReviewPayloads = null
                vm.submitRawGenerationPayloads(editedChunks)
            },
            onDismiss = { pendingRawReviewPayloads = null },
        )
    }

    viewOnlyRawJsonPayloads?.let { currentChunks ->
        RawRequestReviewDialog(
            initialPayloads = currentChunks,
            viewOnly = true,
            onCopyJson = { text -> vm.copyText("request JSON", text) },
            onCopyCurl = vm::copyJsonRequestAsCurl,
            onConfirmSend = {},
            onDismiss = { viewOnlyRawJsonPayloads = null },
        )
    }
}

@Composable
private fun RawRequestReviewDialog(
    initialPayloads: List<String>,
    viewOnly: Boolean,
    onCopyJson: (String) -> Unit,
    onCopyCurl: (String) -> Unit,
    onConfirmSend: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var drafts by remember(initialPayloads) { mutableStateOf(initialPayloads) }
    var activeIdx by remember(initialPayloads) { mutableStateOf(0) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val safeIdx = activeIdx.coerceIn(0, (drafts.size - 1).coerceAtLeast(0))
    val currentText = drafts.getOrElse(safeIdx) { "{}" }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = HordeColors.CardSurface,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = if (viewOnly) "Current Request JSON" else "Review API Request JSON",
                    color = HordeColors.TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                if (drafts.size > 1) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        drafts.indices.forEach { idx ->
                            ToggleChipButton(
                                text = "Request ${idx + 1}",
                                selected = idx == safeIdx,
                                onClick = { activeIdx = idx },
                            )
                        }
                    }
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = currentText,
                    onValueChange = { next ->
                        if (!viewOnly) {
                            errorMessage = null
                            drafts = drafts.toMutableList().apply { set(safeIdx, next) }
                        }
                    },
                    readOnly = viewOnly,
                    minLines = 10,
                    maxLines = 16,
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = HordeColors.TextPrimary,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                errorMessage?.let { err ->
                    Text(err, fontSize = 11.sp, color = HordeColors.RoseText)
                }
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = { onCopyJson(currentText) }) { Text("Copy") }
                OutlinedButton(onClick = { onCopyCurl(currentText) }) { Text("curl") }
                if (!viewOnly) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                }
            }
        },
        confirmButton = {
            if (viewOnly) {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = HordeColors.TealPrimary),
                    onClick = onDismiss,
                ) {
                    Text("Close", color = Color(0xFF020617), fontWeight = FontWeight.SemiBold)
                }
            } else {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = HordeColors.TealPrimary),
                    onClick = {
                        val invalidIdx = drafts.indexOfFirst { raw ->
                            runCatching {
                                kotlinx.serialization.json.Json.parseToJsonElement(raw) as kotlinx.serialization.json.JsonObject
                            }.isFailure
                        }
                        if (invalidIdx >= 0) {
                            activeIdx = invalidIdx
                            errorMessage = "Request ${invalidIdx + 1} must be valid JSON object"
                        } else {
                            onConfirmSend(drafts)
                        }
                    },
                ) {
                    Text("Send", color = Color(0xFF020617), fontWeight = FontWeight.SemiBold)
                }
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LoraTriggerControls(
    lora: AttachedLora,
    selectedModels: List<String>,
    onUpdateLora: ((AttachedLora) -> AttachedLora) -> Unit,
    onInsertPrompt: (List<String>) -> Unit,
    onInsertNegative: (List<String>) -> Unit,
) {
    val useApi = lora.useApiTrigger
    val picked = lora.selectedTriggers.ifEmpty { lora.trainedWords.firstOrNull()?.let(::listOf) ?: emptyList() }
    val plain = TemplatePrompts.shouldUsePlainTrigger(selectedModels, lora.baseModel)

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Triggers", fontSize = 11.sp, color = HordeColors.TextMuted)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                ToggleChipButton(
                    text = "API",
                    selected = useApi,
                    onClick = { onUpdateLora { it.copy(useApiTrigger = !useApi) } },
                )
                ToggleChipButton(
                    text = if (useApi) "Prompt" else "+ Prompt",
                    selected = useApi && lora.triggerTarget == PromptTarget.PROMPT,
                    onClick = {
                        if (useApi) {
                            onUpdateLora { it.copy(triggerTarget = PromptTarget.PROMPT) }
                        } else {
                            val tokens = TemplatePrompts.resolveLoraPromptInsertTokens(
                                lora.trainedWords,
                                lora.triggerMode,
                                picked,
                                plain,
                            )
                            if (tokens.isNotEmpty()) onInsertPrompt(tokens)
                        }
                    },
                )
                ToggleChipButton(
                    text = if (useApi) "Neg" else "+ Neg",
                    selected = useApi && lora.triggerTarget == PromptTarget.NEGPROMPT,
                    onClick = {
                        if (useApi) {
                            onUpdateLora { it.copy(triggerTarget = PromptTarget.NEGPROMPT) }
                        } else {
                            val tokens = TemplatePrompts.resolveLoraPromptInsertTokens(
                                lora.trainedWords,
                                lora.triggerMode,
                                picked,
                                plain,
                            )
                            if (tokens.isNotEmpty()) onInsertNegative(tokens)
                        }
                    },
                )
            }
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            ToggleChipButton(
                text = "all",
                italic = true,
                selected = lora.triggerMode == LoraTriggerMode.ALL,
                onClick = { onUpdateLora { it.copy(triggerMode = LoraTriggerMode.ALL) } },
            )
            ToggleChipButton(
                text = "random",
                italic = true,
                selected = lora.triggerMode == LoraTriggerMode.RANDOM,
                onClick = { onUpdateLora { it.copy(triggerMode = LoraTriggerMode.RANDOM) } },
            )
            lora.trainedWords.forEach { kw ->
                val selected = lora.triggerMode == LoraTriggerMode.CUSTOM && kw in picked
                ToggleChipButton(
                    text = kw,
                    selected = selected,
                    onClick = {
                        onUpdateLora { cur ->
                            val currentPicked = cur.selectedTriggers.ifEmpty {
                                cur.trainedWords.firstOrNull()?.let(::listOf) ?: emptyList()
                            }
                            val next = if (cur.triggerMode != LoraTriggerMode.CUSTOM) {
                                listOf(kw)
                            } else if (kw in currentPicked) {
                                currentPicked - kw
                            } else {
                                currentPicked + kw
                            }
                            cur.copy(triggerMode = LoraTriggerMode.CUSTOM, selectedTriggers = next)
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun GenerateActionBar(
    imageCount: Int,
    onChangeCount: (Int) -> Unit,
    onResetAll: () -> Unit,
    onViewJson: () -> Unit,
    onGenerate: () -> Unit,
    isWideLayout: Boolean,
    isPinnedTop: Boolean,
) {
    val genLabel = if (imageCount > 1) "Generate ($imageCount)" else "Generate"
    val countBox: @Composable (Modifier) -> Unit = { mod ->
        var textDraft by remember(imageCount) { mutableStateOf(imageCount.toString()) }
        Row(
            modifier = mod
                .height(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(HordeColors.CardSurface)
                .border(1.dp, HordeColors.BorderStrong, RoundedCornerShape(12.dp)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = { onChangeCount((imageCount - 1).coerceAtLeast(1)) },
                modifier = Modifier.size(36.dp),
            ) {
                Icon(Icons.Outlined.ChevronLeft, "Decrease count", tint = HordeColors.TextPrimary)
            }
            androidx.compose.foundation.text.BasicTextField(
                value = textDraft,
                onValueChange = { raw ->
                    textDraft = raw
                    raw.toIntOrNull()?.let { onChangeCount(it.coerceIn(1, 100)) }
                },
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(
                    color = HordeColors.TextPrimary,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                ),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(38.dp),
            )
            IconButton(
                onClick = { onChangeCount((imageCount + 1).coerceAtMost(100)) },
                modifier = Modifier.size(36.dp),
            ) {
                Icon(Icons.Outlined.ChevronRight, "Increase count", tint = HordeColors.TextPrimary)
            }
        }
    }

    val trashBtn: @Composable () -> Unit = {
        IconButton(
            onClick = onResetAll,
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(HordeColors.CardSurface)
                .border(1.dp, HordeColors.BorderStrong, RoundedCornerShape(12.dp)),
        ) {
            Icon(Icons.Outlined.DeleteOutline, "Reset all fields", tint = HordeColors.TextSecondary)
        }
    }

    val viewJsonBtn: @Composable () -> Unit = {
        IconButton(
            onClick = onViewJson,
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(HordeColors.CardSurface)
                .border(1.dp, HordeColors.BorderStrong, RoundedCornerShape(12.dp)),
        ) {
            Icon(Icons.Outlined.Code, "View request JSON", tint = HordeColors.TextSecondary)
        }
    }

    val generateBtn: @Composable (Modifier) -> Unit = { mod ->
        Button(
            onClick = onGenerate,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = HordeColors.TealPrimary),
            modifier = mod.height(44.dp),
        ) {
            Text(genLabel, color = Color(0xFF020617), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        }
    }

    if (isWideLayout) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            countBox(Modifier)
            generateBtn(Modifier.weight(1f))
            viewJsonBtn()
            trashBtn()
        }
    } else if (isPinnedTop) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                countBox(Modifier.weight(1f))
                trashBtn()
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                generateBtn(Modifier.weight(1f))
                viewJsonBtn()
            }
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                generateBtn(Modifier.weight(1f))
                viewJsonBtn()
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                countBox(Modifier.weight(1f))
                trashBtn()
            }
        }
    }
}

@Composable
fun StudioCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Surface(
        color = HordeColors.CardSurface,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, HordeColors.BorderSubtle, RoundedCornerShape(16.dp)),
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
    }
}

@Composable
private fun CollapsibleSectionHeader(
    title: String,
    collapsed: Boolean,
    onToggle: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = HordeColors.TextPrimary)
            Icon(
                if (collapsed) Icons.Outlined.ExpandMore else Icons.Outlined.ExpandLess,
                contentDescription = null,
                tint = HordeColors.TextMuted,
                modifier = Modifier.size(18.dp),
            )
        }
        trailing?.invoke()
    }
}

@Composable
private fun SmallHeaderChip(
    label: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(HordeColors.ElevatedButton)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        icon()
        Text(label, fontSize = 11.sp, color = HordeColors.TextPrimary)
    }
}

@Composable
private fun TinySquareAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(30.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(HordeColors.ElevatedButton),
    ) {
        Icon(icon, contentDescription = description, tint = HordeColors.TextSecondary, modifier = Modifier.size(15.dp))
    }
}

@Composable
private fun PickerRowButton(
    label: String,
    value: String,
    locked: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(HordeColors.SubCardSurface)
            .border(1.dp, HordeColors.BorderSubtle, RoundedCornerShape(10.dp))
            .clickable(enabled = !locked, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 12.sp, color = HordeColors.TextMuted)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = value,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = if (locked) HordeColors.TextMuted else HordeColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!locked) {
                Icon(Icons.Outlined.ChevronRight, null, tint = HordeColors.TextMuted, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun CompactSliderInputLayout(
    label: String,
    draft: String,
    onDraftChange: (String) -> Unit,
    keyboardType: KeyboardType,
    inputWidthDp: androidx.compose.ui.unit.Dp,
    accent: Color,
    enabled: Boolean,
    sliderContent: @Composable (Modifier) -> Unit,
) {
    val numberInput: @Composable () -> Unit = {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .width(inputWidthDp)
                .height(34.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(HordeColors.SubCardSurface)
                .border(1.dp, accent.copy(alpha = if (enabled) 0.65f else 0.3f), RoundedCornerShape(8.dp))
                .padding(horizontal = 6.dp, vertical = 4.dp),
        ) {
            BasicTextField(
                value = draft,
                onValueChange = onDraftChange,
                enabled = enabled,
                singleLine = true,
                cursorBrush = SolidColor(accent),
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = if (enabled) accent else HordeColors.TextMuted,
                    textAlign = TextAlign.Center,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val inlineWithSlider = maxWidth >= 195.dp
        if (inlineWithSlider) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(label, fontSize = 12.sp, color = HordeColors.TextSecondary)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    sliderContent(Modifier.weight(1f))
                    numberInput()
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(label, fontSize = 12.sp, color = HordeColors.TextSecondary)
                    numberInput()
                }
                sliderContent(Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
fun LogIntSliderRow(
    label: String,
    value: Int,
    sliderPosition: Float,
    onSliderChange: (Float) -> Unit,
    onManualChange: (Int) -> Unit,
    minVal: Int,
    maxVal: Int,
    zone: SliderZone,
    enabled: Boolean,
) {
    val accent = sliderZoneColor(zone)
    var draft by remember(value) { mutableStateOf(value.toString()) }
    CompactSliderInputLayout(
        label = label,
        draft = draft,
        onDraftChange = { raw ->
            draft = raw
            raw.toIntOrNull()?.let { onManualChange(it.coerceIn(minVal, maxVal)) }
        },
        keyboardType = KeyboardType.Number,
        inputWidthDp = 56.dp,
        accent = accent,
        enabled = enabled,
    ) { mod ->
        Slider(
            value = sliderPosition,
            onValueChange = onSliderChange,
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = accent,
                activeTrackColor = accent,
            ),
            modifier = mod,
        )
    }
}

@Composable
fun LogFloatSliderRow(
    label: String,
    value: Float,
    sliderPosition: Float,
    onSliderChange: (Float) -> Unit,
    onManualChange: (Float) -> Unit,
    decimals: Int,
    zone: SliderZone,
    enabled: Boolean,
) {
    val accent = sliderZoneColor(zone)
    var draft by remember(value) { mutableStateOf(String.format(Locale.US, "%.${decimals}f", value)) }
    CompactSliderInputLayout(
        label = label,
        draft = draft,
        onDraftChange = { raw ->
            draft = raw
            raw.toFloatOrNull()?.let(onManualChange)
        },
        keyboardType = KeyboardType.Decimal,
        inputWidthDp = 62.dp,
        accent = accent,
        enabled = enabled,
    ) { mod ->
        Slider(
            value = sliderPosition,
            onValueChange = onSliderChange,
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = accent,
                activeTrackColor = accent,
            ),
            modifier = mod,
        )
    }
}

@Composable
fun LinearIntSliderRow(
    label: String,
    value: Int,
    minVal: Int,
    maxVal: Int,
    onValueChange: (Int) -> Unit,
    zone: SliderZone,
    enabled: Boolean,
) {
    val accent = sliderZoneColor(zone)
    var draft by remember(value) { mutableStateOf(value.toString()) }
    CompactSliderInputLayout(
        label = label,
        draft = draft,
        onDraftChange = { raw ->
            draft = raw
            raw.toIntOrNull()?.let { onValueChange(it.coerceIn(minVal, maxVal)) }
        },
        keyboardType = KeyboardType.Number,
        inputWidthDp = 56.dp,
        accent = accent,
        enabled = enabled,
    ) { mod ->
        Slider(
            value = value.toFloat(),
            valueRange = minVal.toFloat()..maxVal.toFloat(),
            steps = (maxVal - minVal - 1).coerceAtLeast(0),
            onValueChange = { onValueChange(it.roundToInt()) },
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = accent,
                activeTrackColor = accent,
            ),
            modifier = mod,
        )
    }
}

@Composable
private fun DropdownFieldRow(
    label: String,
    selected: String,
    options: List<String>,
    enabled: Boolean,
    onSelect: (String) -> Unit,
    trailingHeaderAction: (@Composable () -> Unit)? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, fontSize = 12.sp, color = HordeColors.TextSecondary)
            trailingHeaderAction?.invoke()
        }
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(HordeColors.SubCardSurface)
                    .border(1.dp, HordeColors.BorderStrong, RoundedCornerShape(10.dp))
                    .clickable(enabled = enabled) { expanded = true }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(selected, fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = HordeColors.TextPrimary)
                Icon(Icons.Outlined.ExpandMore, null, tint = HordeColors.TextMuted, modifier = Modifier.size(16.dp))
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { opt ->
                    DropdownMenuItem(
                        text = { Text(opt, fontFamily = FontFamily.Monospace, fontSize = 12.sp) },
                        onClick = {
                            onSelect(opt)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
fun ToggleChipButton(
    text: String,
    selected: Boolean,
    italic: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) HordeColors.TealContainer else HordeColors.ElevatedButton)
            .border(
                1.dp,
                if (selected) HordeColors.TealPrimary.copy(alpha = 0.6f) else HordeColors.BorderSubtle,
                RoundedCornerShape(8.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 5.dp),
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
            color = if (selected) HordeColors.TealSoft else HordeColors.TextSecondary,
        )
    }
}

private fun pasteClipboardText(context: Context): String? {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    return cm.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
}
