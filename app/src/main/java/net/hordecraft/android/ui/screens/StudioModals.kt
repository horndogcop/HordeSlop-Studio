package net.hordecraft.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import net.hordecraft.android.domain.AttachedLora
import net.hordecraft.android.domain.AttachedTi
import net.hordecraft.android.domain.HordeConstants
import net.hordecraft.android.domain.HordeModel
import net.hordecraft.android.domain.HordeStyle
import net.hordecraft.android.domain.MultiModelDispatchMode
import net.hordecraft.android.domain.PromptRecord
import net.hordecraft.android.domain.SizeMathEngine
import net.hordecraft.android.domain.SliderMath
import net.hordecraft.android.ui.theme.HordeColors

@Composable
fun HistoryFavoritesDialog(
    mode: String,
    records: List<PromptRecord>,
    onSelect: (PromptRecord) -> Unit,
    onToggleFavorite: (String, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val filtered = records.filter { rec ->
        if (mode == "favorites" && !rec.isFavorite) return@filter false
        if (query.isBlank()) return@filter true
        val q = query.lowercase()
        rec.prompt.lowercase().contains(q) || rec.model.lowercase().contains(q)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = HordeColors.CardSurface,
        title = {
            Text(
                text = if (mode == "favorites") "Favorite Prompts" else "Prompt History",
                color = HordeColors.TextPrimary,
                fontSize = 16.sp,
            )
        },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    placeholder = { Text("Search prompts...") },
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(filtered, key = { it.id }) { rec ->
                        Surface(
                            color = HordeColors.SubCardSurface,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, HordeColors.BorderSubtle, RoundedCornerShape(10.dp))
                                .clickable { onSelect(rec) },
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = rec.prompt,
                                        fontSize = 12.sp,
                                        color = HordeColors.TextPrimary,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = "${rec.model} · ${rec.steps} steps",
                                        fontSize = 11.sp,
                                        color = HordeColors.TextMuted,
                                    )
                                }
                                IconButton(
                                    onClick = { onToggleFavorite(rec.id, rec.isFavorite) },
                                    modifier = Modifier.size(30.dp),
                                ) {
                                    Icon(
                                        if (rec.isFavorite) Icons.Outlined.Star else Icons.Outlined.StarBorder,
                                        contentDescription = null,
                                        tint = if (rec.isFavorite) HordeColors.AmberZone else HordeColors.TextMuted,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}

@Composable
fun FullscreenPromptDialog(
    prompt: String,
    onChange: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = HordeColors.CardSurface,
        title = { Text("Edit Prompt", color = HordeColors.TextPrimary, fontSize = 16.sp) },
        text = {
            OutlinedTextField(
                value = prompt,
                onValueChange = onChange,
                minLines = 10,
                maxLines = 16,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            Button(
                colors = ButtonDefaults.buttonColors(containerColor = HordeColors.TealPrimary),
                onClick = onDismiss,
            ) {
                Text("Done", color = Color(0xFF020617))
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SizePickerDialog(
    width: Int,
    height: Int,
    onApply: (Int, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var draftW by remember { mutableStateOf(width.toString()) }
    var draftH by remember { mutableStateOf(height.toString()) }
    var showPresets by remember { mutableStateOf(false) }
    var wRatio by remember { mutableStateOf(1) }
    var hRatio by remember { mutableStateOf(1) }
    var targetMp by remember { mutableDoubleStateOf(1.0) }

    val parsedW = SizeMathEngine.snapTo64(draftW.toIntOrNull() ?: width)
    val parsedH = SizeMathEngine.snapTo64(draftH.toIntOrNull() ?: height)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = HordeColors.CardSurface,
        title = { Text("Image Size", color = HordeColors.TextPrimary, fontSize = 16.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = draftW,
                        onValueChange = { draftW = it },
                        label = { Text("Width") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = {
                            val oldW = draftW
                            draftW = draftH
                            draftH = oldW
                        },
                    ) {
                        Icon(Icons.Outlined.SwapHoriz, "Swap dimensions", tint = HordeColors.TealSoft)
                    }
                    OutlinedTextField(
                        value = draftH,
                        onValueChange = { draftH = it },
                        label = { Text("Height") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                }

                Text(
                    text = "64px snapped: $parsedW × $parsedH · ${SizeMathEngine.formatAspectRatioLabel(parsedW, parsedH)} · ${SizeMathEngine.formatMegapixels(parsedW, parsedH)}",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = HordeColors.TealSoft,
                )

                TextButton(onClick = { showPresets = !showPresets }) {
                    Text(if (showPresets) "Hide aspect & megapixel presets" else "Pick aspect ratio & megapixels")
                }

                if (showPresets) {
                    Text("Aspect Ratio", fontSize = 11.sp, color = HordeColors.TextMuted)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        SizeMathEngine.ASPECT_RATIO_PRESETS.forEach { preset ->
                            ToggleChipButton(
                                text = preset.label,
                                selected = wRatio == preset.wRatio && hRatio == preset.hRatio,
                                onClick = {
                                    wRatio = preset.wRatio
                                    hRatio = preset.hRatio
                                    val (w, h) = SizeMathEngine.computeNearestSizeForRatioAndMp(wRatio, hRatio, targetMp)
                                    draftW = w.toString()
                                    draftH = h.toString()
                                },
                            )
                        }
                    }

                    Text("Target Resolution", fontSize = 11.sp, color = HordeColors.TextMuted)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        SizeMathEngine.MP_PRESETS.forEach { mp ->
                            ToggleChipButton(
                                text = mp.label,
                                selected = targetMp == mp.megapixels,
                                onClick = {
                                    targetMp = mp.megapixels
                                    val (w, h) = SizeMathEngine.computeNearestSizeForRatioAndMp(wRatio, hRatio, targetMp)
                                    draftW = w.toString()
                                    draftH = h.toString()
                                },
                            )
                        }
                    }
                }
            }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Cancel") } },
        confirmButton = {
            Button(
                colors = ButtonDefaults.buttonColors(containerColor = HordeColors.TealPrimary),
                onClick = { onApply(parsedW, parsedH) },
            ) {
                Text("Apply", color = Color(0xFF020617), fontWeight = FontWeight.SemiBold)
            }
        },
    )
}

@Composable
fun ModelPickerDialog(
    models: List<HordeModel>,
    selectedModels: List<String>,
    dispatchMode: MultiModelDispatchMode,
    showModelPreviews: Boolean,
    currentSteps: Int,
    currentSampler: String,
    currentScheduler: String,
    currentClipSkip: Int,
    onToggleModel: (String) -> Unit,
    onChangeDispatchMode: (MultiModelDispatchMode) -> Unit,
    onTogglePreviews: (Boolean) -> Unit,
    onSaveDefaults: (String) -> Unit,
    onResetDefaults: (String) -> Unit,
    onApplyModel: (HordeModel) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val filtered = models.filter {
        query.isBlank() || it.name.contains(query, ignoreCase = true) || it.baseline.contains(query, ignoreCase = true)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = HordeColors.CardSurface,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Select Models", color = HordeColors.TextPrimary, fontSize = 16.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Previews", fontSize = 11.sp, color = HordeColors.TextMuted)
                    Checkbox(checked = showModelPreviews, onCheckedChange = onTogglePreviews)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 460.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    placeholder = { Text("Search models...") },
                    modifier = Modifier.fillMaxWidth(),
                )

                // Shown ONLY when multiple models are selected
                if (selectedModels.size > 1) {
                    Surface(
                        color = HordeColors.SubCardSurface,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Multiple models", fontSize = 11.sp, color = HordeColors.TextMuted)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                MultiModelDispatchMode.entries.forEach { mode ->
                                    ToggleChipButton(
                                        text = mode.label,
                                        selected = dispatchMode == mode,
                                        onClick = { onChangeDispatchMode(mode) },
                                    )
                                }
                            }
                        }
                    }
                }

                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(filtered, key = { it.name }) { model ->
                        val selected = model.name in selectedModels
                        Surface(
                            color = HordeColors.SubCardSurface,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    1.dp,
                                    if (selected) HordeColors.TealPrimary else HordeColors.BorderSubtle,
                                    RoundedCornerShape(12.dp),
                                )
                                .clickable { onToggleModel(model.name) },
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(model.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = HordeColors.TextPrimary)
                                        Text(
                                            "${model.baseline} · ${model.workers} workers · ${model.etaSeconds}s ETA",
                                            fontSize = 11.sp,
                                            color = HordeColors.TextMuted,
                                        )
                                    }
                                    Checkbox(checked = selected, onCheckedChange = { onToggleModel(model.name) })
                                }
                                if (showModelPreviews && model.previewImageUrls.isNotEmpty()) {
                                    AsyncImage(
                                        model = model.previewImageUrls.first(),
                                        contentDescription = model.name,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(100.dp)
                                            .clip(RoundedCornerShape(8.dp)),
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = "Use defaults",
                                        fontSize = 11.sp,
                                        color = HordeColors.TealSoft,
                                        modifier = Modifier.clickable { onApplyModel(model) },
                                    )
                                    Text(
                                        text = "Save ($currentSteps / $currentSampler)",
                                        fontSize = 11.sp,
                                        color = HordeColors.TextSecondary,
                                        modifier = Modifier.clickable { onSaveDefaults(model.name) },
                                    )
                                    Text(
                                        text = "Reset",
                                        fontSize = 11.sp,
                                        color = HordeColors.TextMuted,
                                        modifier = Modifier.clickable { onResetDefaults(model.name) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

@Composable
fun StylesPickerDialog(
    styles: List<HordeStyle>,
    selectedStyleId: String,
    onSelect: (HordeStyle) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val filtered = styles.filter {
        query.isBlank() || it.name.contains(query, ignoreCase = true) || (it.model?.contains(query, ignoreCase = true) == true)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = HordeColors.CardSurface,
        title = { Text("Select Style", color = HordeColors.TextPrimary, fontSize = 16.sp) },
        text = {
            Column(modifier = Modifier.heightIn(max = 440.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    placeholder = { Text("Search styles...") },
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(filtered, key = { it.id }) { style ->
                        val active = style.id == selectedStyleId
                        Surface(
                            color = HordeColors.SubCardSurface,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, if (active) HordeColors.TealPrimary else HordeColors.BorderSubtle, RoundedCornerShape(10.dp))
                                .clickable { onSelect(style) },
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (style.previewImageUrl != null && style.id != "none") {
                                    AsyncImage(
                                        model = style.previewImageUrl,
                                        contentDescription = style.name,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(54.dp)
                                            .clip(RoundedCornerShape(8.dp)),
                                    )
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(style.name, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = HordeColors.TextPrimary)
                                    Text(
                                        text = listOfNotNull(style.source, style.model).joinToString(" · "),
                                        fontSize = 11.sp,
                                        color = HordeColors.TextMuted,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
fun CivitaiSearchDialog(
    tab: String,
    loras: List<AttachedLora>,
    tis: List<AttachedTi>,
    loading: Boolean,
    onSearchLoras: (String, Boolean, Set<String>) -> Unit,
    onSearchTis: (String, Boolean, Set<String>) -> Unit,
    onSelectLora: (AttachedLora) -> Unit,
    onSelectTi: (AttachedTi) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var allModels by remember { mutableStateOf(true) }
    var selectedGroupIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var dropdownOpen by remember { mutableStateOf(false) }

    val groups = if (tab == "lora") HordeConstants.CIVITAI_LORA_GROUPS else HordeConstants.CIVITAI_TI_GROUPS

    LaunchedEffect(tab) {
        if (tab == "lora") onSearchLoras(query, allModels, selectedGroupIds)
        else onSearchTis(query, allModels, selectedGroupIds)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = HordeColors.CardSurface,
        title = {
            Text(
                text = if (tab == "lora") "Find LoRA" else "Find Embedding",
                color = HordeColors.TextPrimary,
                fontSize = 16.sp,
            )
        },
        text = {
            Column(modifier = Modifier.heightIn(max = 460.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        placeholder = { Text("Name or ID...") },
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        onClick = {
                            if (tab == "lora") onSearchLoras(query, allModels, selectedGroupIds)
                            else onSearchTis(query, allModels, selectedGroupIds)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = HordeColors.TealPrimary),
                    ) {
                        Text("Go", color = Color(0xFF020617), fontWeight = FontWeight.SemiBold)
                    }
                }

                // Multi-select base model filter dropdown with "All models" bypass checkbox
                Box {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(HordeColors.SubCardSurface)
                            .border(1.dp, HordeColors.BorderSubtle, RoundedCornerShape(10.dp))
                            .clickable { dropdownOpen = true }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if (allModels) "All models" else "${selectedGroupIds.size} model filter(s)",
                            fontSize = 12.sp,
                            color = HordeColors.TextSecondary,
                        )
                        Icon(Icons.Outlined.ArrowDropDown, null, tint = HordeColors.TextMuted)
                    }
                    DropdownMenu(expanded = dropdownOpen, onDismissRequest = { dropdownOpen = false }) {
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = allModels,
                                        onCheckedChange = { checked ->
                                            allModels = checked
                                            if (checked) selectedGroupIds = emptySet()
                                        },
                                    )
                                    Text("All models (disable filter)")
                                }
                            },
                            onClick = {
                                val next = !allModels
                                allModels = next
                                if (next) selectedGroupIds = emptySet()
                            },
                        )
                        HorizontalDivider()
                        groups.forEach { g ->
                            val checked = g.id in selectedGroupIds
                            DropdownMenuItem(
                                enabled = !allModels,
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(
                                            checked = checked && !allModels,
                                            enabled = !allModels,
                                            onCheckedChange = {
                                                if (!allModels) {
                                                    selectedGroupIds = if (checked) selectedGroupIds - g.id else selectedGroupIds + g.id
                                                }
                                            },
                                        )
                                        Text(g.label, fontSize = 12.sp)
                                    }
                                },
                                onClick = {
                                    if (!allModels) {
                                        selectedGroupIds = if (checked) selectedGroupIds - g.id else selectedGroupIds + g.id
                                    }
                                },
                            )
                        }
                    }
                }

                if (loading) {
                    Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (tab == "lora") {
                            items(loras, key = { it.modelId }) { item ->
                                Surface(
                                    color = HordeColors.SubCardSurface,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(1.dp, HordeColors.BorderSubtle, RoundedCornerShape(10.dp))
                                        .clickable { onSelectLora(item) },
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text(item.name, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = HordeColors.TextPrimary)
                                        Text(
                                            "${item.baseModel} · #${item.modelId} · v${item.modelVersionId}",
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = HordeColors.TextMuted,
                                        )
                                    }
                                }
                            }
                        } else {
                            items(tis, key = { it.modelId }) { item ->
                                Surface(
                                    color = HordeColors.SubCardSurface,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(1.dp, HordeColors.BorderSubtle, RoundedCornerShape(10.dp))
                                        .clickable { onSelectTi(item) },
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text(item.name, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = HordeColors.TextPrimary)
                                        Text(
                                            "${item.baseModel} · #${item.modelId}",
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = HordeColors.TextMuted,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
fun LoraDetailDialog(
    lora: AttachedLora,
    selectedModels: List<String>,
    onAttachOrSave: (AttachedLora) -> Unit,
    onInsertPrompt: (List<String>) -> Unit,
    onInsertNegative: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember(lora) { mutableStateOf(lora) }
    var includeMaturePreviews by remember { mutableStateOf(false) }
    var versionMenuOpen by remember { mutableStateOf(false) }

    val visiblePreviews = draft.previews.filter { includeMaturePreviews || !it.isMature }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = HordeColors.CardSurface,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(draft.name, fontSize = 15.sp, color = HordeColors.TextPrimary, modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, null) }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Previews", fontSize = 12.sp, color = HordeColors.TextMuted)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = includeMaturePreviews, onCheckedChange = { includeMaturePreviews = it })
                        Text("Include mature previews", fontSize = 11.sp, color = HordeColors.TextSecondary)
                    }
                }

                if (visiblePreviews.isNotEmpty()) {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        visiblePreviews.forEach { p ->
                            AsyncImage(
                                model = p.url,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(width = 120.dp, height = 150.dp)
                                    .clip(RoundedCornerShape(10.dp)),
                            )
                        }
                    }
                }

                // Version selector
                if (draft.versions.isNotEmpty()) {
                    Box {
                        OutlinedButton(onClick = { versionMenuOpen = true }, modifier = Modifier.fillMaxWidth()) {
                            Text("Version: ${draft.versionName} (${draft.baseModel})", fontSize = 12.sp)
                        }
                        DropdownMenu(expanded = versionMenuOpen, onDismissRequest = { versionMenuOpen = false }) {
                            draft.versions.forEach { v ->
                                DropdownMenuItem(
                                    text = { Text("${v.name} · v${v.id}", fontSize = 12.sp) },
                                    onClick = {
                                        draft = draft.copy(
                                            modelVersionId = v.id,
                                            versionName = v.name,
                                            baseModel = v.baseModel,
                                            trainedWords = v.trainedWords.ifEmpty { draft.trainedWords },
                                        )
                                        versionMenuOpen = false
                                    },
                                )
                            }
                        }
                    }
                }

                LogFloatSliderRow(
                    label = "Model weight",
                    value = draft.strengthModel,
                    sliderPosition = SliderMath.strengthToSlider(draft.strengthModel),
                    onSliderChange = { pos -> draft = draft.copy(strengthModel = SliderMath.sliderToStrength(pos)) },
                    onManualChange = { v -> draft = draft.copy(strengthModel = v) },
                    decimals = 2,
                    zone = SliderMath.strengthZone(draft.strengthModel),
                    enabled = true,
                )

                LogFloatSliderRow(
                    label = "Clip weight",
                    value = draft.strengthClip,
                    sliderPosition = SliderMath.strengthToSlider(draft.strengthClip),
                    onSliderChange = { pos -> draft = draft.copy(strengthClip = SliderMath.sliderToStrength(pos)) },
                    onManualChange = { v -> draft = draft.copy(strengthClip = v) },
                    decimals = 2,
                    zone = SliderMath.strengthZone(draft.strengthClip),
                    enabled = true,
                )

                if (draft.trainedWords.isNotEmpty()) {
                    LoraTriggerControls(
                        lora = draft,
                        selectedModels = selectedModels,
                        onUpdateLora = { transform -> draft = transform(draft) },
                        onInsertPrompt = onInsertPrompt,
                        onInsertNegative = onInsertNegative,
                    )
                }
            }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Cancel") } },
        confirmButton = {
            Button(
                colors = ButtonDefaults.buttonColors(containerColor = HordeColors.TealPrimary),
                onClick = { onAttachOrSave(draft) },
            ) {
                Text("Add LoRA", color = Color(0xFF020617), fontWeight = FontWeight.SemiBold)
            }
        },
    )
}
