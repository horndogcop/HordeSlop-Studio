package net.hordecraft.android.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import net.hordecraft.android.data.local.AppSettings
import net.hordecraft.android.domain.ActivityDestination
import net.hordecraft.android.domain.SavedImage
import net.hordecraft.android.ui.MainViewModel
import net.hordecraft.android.ui.components.launchActivityForDestination
import net.hordecraft.android.ui.theme.HordeColors

@Composable
fun GalleryScreen(
    vm: MainViewModel,
    images: List<SavedImage>,
    settings: AppSettings,
    toastMessage: String?,
) {
    val context = LocalContext.current
    val config = LocalConfiguration.current
    val scope = rememberCoroutineScope()

    var searchQuery by remember { mutableStateOf("") }
    var requestIdFilter by remember { mutableStateOf("") }
    var showRequestFilterInput by remember { mutableStateOf(false) }
    var favoritesOnly by remember { mutableStateOf(false) }

    var viewerStartIndex by remember { mutableStateOf<Int?>(null) }
    var isFullscreen by remember { mutableStateOf(false) }
    var fullscreenHudVisible by remember { mutableStateOf(true) }

    val filtered = remember(images, searchQuery, requestIdFilter, favoritesOnly) {
        images.filter { img ->
            if (favoritesOnly && !img.isFavorite) return@filter false
            if (requestIdFilter.isNotBlank() && !img.requestId.contains(requestIdFilter.trim(), ignoreCase = true)) {
                return@filter false
            }
            if (searchQuery.isBlank()) return@filter true
            val q = searchQuery.lowercase()
            img.prompt.lowercase().contains(q) ||
                img.model.lowercase().contains(q) ||
                img.requestId.lowercase().contains(q) ||
                img.generationId.lowercase().contains(q) ||
                img.seed.contains(q)
        }
    }

    val columns = if (config.screenWidthDp >= 600) 3 else 2

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        toastMessage?.let { msg ->
            LaunchedEffect(msg) {
                kotlinx.coroutines.delay(2_400L)
                vm.clearToast()
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(HordeColors.TealContainer)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(msg, fontSize = 12.sp, color = HordeColors.TealSoft)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                singleLine = true,
                placeholder = { Text("Search gallery...", fontSize = 12.sp) },
                modifier = Modifier.weight(1f),
            )
            ToggleChipButton(
                text = "Request",
                selected = showRequestFilterInput || requestIdFilter.isNotBlank(),
                onClick = { showRequestFilterInput = !showRequestFilterInput },
            )
            IconButton(
                onClick = { favoritesOnly = !favoritesOnly },
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (favoritesOnly) Color(0x26F43F5E) else HordeColors.CardSurface)
                    .border(
                        1.dp,
                        if (favoritesOnly) HordeColors.RoseZone else HordeColors.BorderSubtle,
                        RoundedCornerShape(10.dp),
                    ),
            ) {
                Icon(
                    if (favoritesOnly) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = "Favorites",
                    tint = if (favoritesOnly) HordeColors.RoseZone else HordeColors.TextSecondary,
                )
            }
        }

        if (showRequestFilterInput || requestIdFilter.isNotBlank()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = requestIdFilter,
                    onValueChange = { requestIdFilter = it },
                    singleLine = true,
                    placeholder = { Text("Request ID...") },
                    modifier = Modifier.weight(1f),
                )
                if (requestIdFilter.isNotBlank()) {
                    TextButton(onClick = { requestIdFilter = "" }) { Text("Clear") }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Outlined.FolderOpen, null, tint = HordeColors.TealSoft, modifier = Modifier.size(14.dp))
                Text(settings.safFolderLabel, fontSize = 11.sp, color = HordeColors.TextMuted)
            }
            Text("${filtered.size} images", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = HordeColors.TextMuted)
        }

        if (filtered.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No saved WebP images yet", fontSize = 13.sp, color = HordeColors.TextMuted)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                contentPadding = PaddingValues(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                itemsIndexed(filtered, key = { _, item -> item.id }) { idx, img ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(HordeColors.CardSurface)
                            .border(1.dp, HordeColors.BorderSubtle, RoundedCornerShape(14.dp))
                            .clickable {
                                viewerStartIndex = idx
                                isFullscreen = settings.skipGalleryModalToFullscreen
                                fullscreenHudVisible = true
                            },
                    ) {
                        AsyncImage(
                            model = Uri.parse(img.fileUri),
                            contentDescription = img.model,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                        if (img.partialFromCancel) {
                            Text(
                                text = "Stopped early",
                                fontSize = 10.sp,
                                color = HordeColors.AmberText,
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(6.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color.Black.copy(alpha = 0.65f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                        IconButton(
                            onClick = { vm.toggleImageFavorite(img.id, img.isFavorite) },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp)
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.45f)),
                        ) {
                            Icon(
                                if (img.isFavorite) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder,
                                contentDescription = "Favorite",
                                tint = if (img.isFavorite) HordeColors.RoseZone else Color.White,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    viewerStartIndex?.let { startIndex ->
        if (filtered.isNotEmpty()) {
            val safeStart = startIndex.coerceIn(0, filtered.lastIndex)
            GalleryViewerModal(
                images = filtered,
                initialIndex = safeStart,
                isFullscreen = isFullscreen,
                fullscreenHudVisible = fullscreenHudVisible,
                onToggleFullscreen = {
                    isFullscreen = !isFullscreen
                    fullscreenHudVisible = true
                },
                onToggleHudVisibility = {
                    if (isFullscreen) fullscreenHudVisible = !fullscreenHudVisible
                },
                onToggleFavorite = { vm.toggleImageFavorite(it.id, it.isFavorite) },
                onReroll = { vm.rerollImage(it) },
                onUseSettings = { img ->
                    vm.applySavedImageParameters(img)
                    viewerStartIndex = null
                    launchActivityForDestination(context, ActivityDestination.IMAGE_GEN)
                },
                onSameRequest = { reqId ->
                    viewerStartIndex = null
                    isFullscreen = false
                    requestIdFilter = reqId
                    showRequestFilterInput = true
                },
                onCopyImage = { img -> vm.copyImage(img.fileUri) },
                onCopyText = vm::copyText,
                onShare = { img ->
                    scope.launch {
                        vm.buildShareIntent(img.fileUri)?.let { intent ->
                            context.startActivity(Intent.createChooser(intent, "Share WebP"))
                        }
                    }
                },
                onOpenIn = { img ->
                    scope.launch {
                        vm.buildOpenInIntent(img.fileUri)?.let { intent ->
                            context.startActivity(Intent.createChooser(intent, "Open in..."))
                        }
                    }
                },
                onDelete = { img, deleteFile ->
                    vm.deleteImage(img, deleteFile)
                    if (filtered.size <= 1) viewerStartIndex = null
                },
                onDismiss = {
                    viewerStartIndex = null
                    isFullscreen = false
                },
            )
        }
    }
}

@Composable
private fun GalleryViewerModal(
    images: List<SavedImage>,
    initialIndex: Int,
    isFullscreen: Boolean,
    fullscreenHudVisible: Boolean,
    onToggleFullscreen: () -> Unit,
    onToggleHudVisibility: () -> Unit,
    onToggleFavorite: (SavedImage) -> Unit,
    onReroll: (SavedImage) -> Unit,
    onUseSettings: (SavedImage) -> Unit,
    onSameRequest: (String) -> Unit,
    onCopyImage: (SavedImage) -> Unit,
    onCopyText: (String, String) -> Unit,
    onShare: (SavedImage) -> Unit,
    onOpenIn: (SavedImage) -> Unit,
    onDelete: (SavedImage, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(initialPage = initialIndex) { images.size }
    val activeImage = images.getOrNull(pagerState.currentPage) ?: return

    var showMoreMenu by remember { mutableStateOf(false) }
    var metadataSubMode by remember { mutableStateOf<String?>(null) } // "details" | "request" | "response"
    var deleteConfirmTarget by remember { mutableStateOf<SavedImage?>(null) }
    var deleteFileChecked by remember { mutableStateOf(true) }
    var jsonToSave by remember { mutableStateOf<String?>(null) }

    val saveJsonLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        val content = jsonToSave ?: return@rememberLauncherForActivityResult
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(content.toByteArray(Charsets.UTF_8))
                }
            }
        }
        jsonToSave = null
    }

    val showHud = !isFullscreen || fullscreenHudVisible

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(onClick = onToggleHudVisibility),
            ) { page ->
                val item = images[page]
                AsyncImage(
                    model = Uri.parse(item.fileUri),
                    contentDescription = item.prompt,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            if (showHud) {
                // Top Floating Overlay Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.85f), Color.Transparent)))
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)),
                    ) {
                        Icon(Icons.Outlined.Close, "Close", tint = Color.White)
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(
                            onClick = { onToggleFavorite(activeImage) },
                            modifier = Modifier.clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)),
                        ) {
                            Icon(
                                if (activeImage.isFavorite) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder,
                                "Favorite",
                                tint = if (activeImage.isFavorite) HordeColors.RoseZone else Color.White,
                            )
                        }
                        IconButton(
                            onClick = { onReroll(activeImage) },
                            modifier = Modifier.clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)),
                        ) {
                            Icon(Icons.Outlined.Refresh, "Reroll", tint = HordeColors.TealSoft)
                        }
                        IconButton(
                            onClick = { onUseSettings(activeImage) },
                            modifier = Modifier.clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)),
                        ) {
                            Icon(Icons.Outlined.Send, "Use settings", tint = Color.White)
                        }
                        IconButton(
                            onClick = onToggleFullscreen,
                            modifier = Modifier.clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)),
                        ) {
                            Icon(Icons.Outlined.Fullscreen, "Full screen", tint = Color.White)
                        }
                        Box {
                            IconButton(
                                onClick = { showMoreMenu = true },
                                modifier = Modifier.clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)),
                            ) {
                                Icon(Icons.Outlined.MoreHoriz, "More", tint = Color.White)
                            }
                            DropdownMenu(expanded = showMoreMenu, onDismissRequest = { showMoreMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text("Request info") },
                                    onClick = {
                                        showMoreMenu = false
                                        metadataSubMode = "request"
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Server response") },
                                    onClick = {
                                        showMoreMenu = false
                                        metadataSubMode = "response"
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Open in...") },
                                    leadingIcon = { Icon(Icons.Outlined.OpenInNew, null) },
                                    onClick = {
                                        showMoreMenu = false
                                        onOpenIn(activeImage)
                                    },
                                )
                            }
                        }
                    }
                }

                // Prev / Next Arrows
                if (images.size > 1) {
                    IconButton(
                        onClick = {
                            scope.launch {
                                val prev = if (pagerState.currentPage == 0) images.lastIndex else pagerState.currentPage - 1
                                pagerState.animateScrollToPage(prev)
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 8.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.55f)),
                    ) {
                        Icon(Icons.Outlined.ChevronLeft, "Previous", tint = Color.White)
                    }
                    IconButton(
                        onClick = {
                            scope.launch {
                                val next = (pagerState.currentPage + 1) % images.size
                                pagerState.animateScrollToPage(next)
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 8.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.55f)),
                    ) {
                        Icon(Icons.Outlined.ChevronRight, "Next", tint = Color.White)
                    }
                }

                // Bottom Overlay Info + Actions
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.92f))))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "${activeImage.requestId.take(8)} · ${activeImage.generationId.take(8)}",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = HordeColors.TextSecondary,
                        )
                        Text(
                            text = activeImage.model,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = HordeColors.TealSoft,
                        )
                    }
                    Text(
                        text = activeImage.prompt,
                        fontSize = 12.sp,
                        color = HordeColors.TextPrimary,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        OverlayPill("Same request") { onSameRequest(activeImage.requestId) }
                        OverlayPill("Details") { metadataSubMode = "details" }
                        OverlayPill("Copy") { onCopyImage(activeImage) }
                        OverlayPill("Share") { onShare(activeImage) }
                        OverlayPill("Delete", isDanger = true) {
                            deleteFileChecked = true
                            deleteConfirmTarget = activeImage
                        }
                    }
                }
            }
        }
    }

    metadataSubMode?.let { mode ->
        val jsonContent = when (mode) {
            "request" -> activeImage.requestJson
            "response" -> activeImage.statusMetadataJson
            else -> buildString {
                appendLine("Request ID: ${activeImage.requestId}")
                appendLine("Generation ID: ${activeImage.generationId}")
                appendLine("Model: ${activeImage.model}")
                appendLine("Seed: ${activeImage.seed}")
                appendLine("Size: ${activeImage.width}x${activeImage.height}")
                appendLine("Steps: ${activeImage.steps} · CFG: ${activeImage.cfg} · Clip Skip: ${activeImage.clipSkip}")
                appendLine("Sampler: ${activeImage.sampler} · Scheduler: ${activeImage.scheduler}")
                appendLine("Saved: ${activeImage.destinationLabel}")
                appendLine()
                appendLine("Prompt:")
                appendLine(activeImage.prompt)
                if (activeImage.negativePrompt.isNotBlank()) {
                    appendLine()
                    appendLine("Negative:")
                    appendLine(activeImage.negativePrompt)
                }
            }
        }
        AlertDialog(
            onDismissRequest = { metadataSubMode = null },
            containerColor = HordeColors.CardSurface,
            title = {
                Text(
                    text = when (mode) {
                        "request" -> "Request Info"
                        "response" -> "Server Response"
                        else -> "Details"
                    },
                    color = HordeColors.TextPrimary,
                )
            },
            text = {
                Box(
                    modifier = Modifier
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState())
                        .background(HordeColors.SubCardSurface, RoundedCornerShape(8.dp))
                        .padding(10.dp),
                ) {
                    Text(jsonContent, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = HordeColors.TextSecondary)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { onCopyText(mode, jsonContent) }) { Text("Copy") }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            jsonToSave = jsonContent
                            saveJsonLauncher.launch("hordecraft_${activeImage.generationId.take(8)}_$mode.json")
                        },
                    ) {
                        Text("Save")
                    }
                    TextButton(onClick = { metadataSubMode = null }) { Text("Close") }
                }
            },
        )
    }

    deleteConfirmTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteConfirmTarget = null },
            containerColor = HordeColors.CardSurface,
            title = { Text("Delete image?", color = HordeColors.TextPrimary) },
            text = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { deleteFileChecked = !deleteFileChecked },
                ) {
                    Checkbox(checked = deleteFileChecked, onCheckedChange = { deleteFileChecked = it })
                    Text("Also delete file from ${target.destinationLabel}", fontSize = 12.sp, color = HordeColors.TextSecondary)
                }
            },
            dismissButton = { OutlinedButton(onClick = { deleteConfirmTarget = null }) { Text("Cancel") } },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = HordeColors.RoseZone),
                    onClick = {
                        deleteConfirmTarget = null
                        onDelete(target, deleteFileChecked)
                    },
                ) {
                    Text("Delete", color = Color.White)
                }
            },
        )
    }
}

@Composable
private fun OverlayPill(
    label: String,
    isDanger: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isDanger) HordeColors.RoseZone.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.16f))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(label, fontSize = 11.sp, color = if (isDanger) HordeColors.RoseText else Color.White)
    }
}
