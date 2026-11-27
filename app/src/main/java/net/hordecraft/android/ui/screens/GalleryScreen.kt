package net.hordeslop.studio.ui.screens

import android.app.WallpaperManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
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
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
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
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.FullscreenExit
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.PauseCircleOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import net.hordeslop.studio.data.local.AppSettings
import net.hordeslop.studio.domain.ActivityDestination
import net.hordeslop.studio.domain.GalleryFilterState
import net.hordeslop.studio.domain.GallerySearchFilter
import net.hordeslop.studio.domain.GalleryStatusCategory
import net.hordeslop.studio.domain.GalleryTimestampFormatter
import net.hordeslop.studio.domain.HordeJob
import net.hordeslop.studio.domain.JobStatus
import net.hordeslop.studio.domain.SavedImage
import net.hordeslop.studio.ui.MainViewModel
import net.hordeslop.studio.ui.components.launchActivityForDestination
import net.hordeslop.studio.ui.theme.HordeColors

private sealed interface GalleryGridEntry {
    val stableKey: String
    val createdAtMillis: Long

    data class Completed(val image: SavedImage) : GalleryGridEntry {
        override val stableKey: String = "img:${image.id}"
        override val createdAtMillis: Long = image.createdAtMillis
    }

    data class JobItem(val job: HordeJob) : GalleryGridEntry {
        override val stableKey: String = "job:${job.id}"
        override val createdAtMillis: Long = job.createdAtMillis
    }
}

@Composable
fun GalleryScreen(
    vm: MainViewModel,
    images: List<SavedImage>,
    settings: AppSettings,
    toastMessage: String?,
    pendingSearchQuery: String? = null,
    jobs: List<HordeJob> = emptyList(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var searchQuery by remember { mutableStateOf("") }
    var favoritesOnly by remember { mutableStateOf(false) }
    var filterState by remember { mutableStateOf(GalleryFilterState()) }
    var filterMenuExpanded by remember { mutableStateOf(false) }

    var viewerStartIndex by remember { mutableStateOf<Int?>(null) }
    var viewerImagesSnapshot by remember { mutableStateOf<List<SavedImage>?>(null) }
    var inspectedJobId by remember { mutableStateOf<String?>(null) }
    var isFullscreen by remember { mutableStateOf(false) }
    var fullscreenHudVisible by remember { mutableStateOf(false) }
    var columnsMenuExpanded by remember { mutableStateOf(false) }

    val favoritesEnabled = filterState.includesCompleteGenerations

    LaunchedEffect(pendingSearchQuery) {
        if (!pendingSearchQuery.isNullOrBlank()) {
            searchQuery = pendingSearchQuery
            if (!filterState.includesCompleteGenerations) {
                filterState = filterState.toggleCategory(GalleryStatusCategory.COMPLETE_GENERATIONS)
            }
            viewerStartIndex = null
            viewerImagesSnapshot = null
            inspectedJobId = null
            isFullscreen = false
            vm.clearPendingGallerySearchQuery()
        }
    }

    val filteredImages = remember(images, searchQuery, favoritesOnly, filterState) {
        if (!filterState.includesCompleteGenerations) {
            emptyList()
        } else {
            images.filter { img ->
                if (favoritesOnly && !img.isFavorite) return@filter false
                GallerySearchFilter.matches(img, searchQuery)
            }
        }
    }

    val filteredJobs = remember(jobs, searchQuery, favoritesOnly, filterState, favoritesEnabled) {
        if (favoritesEnabled && favoritesOnly) {
            emptyList()
        } else {
            jobs.filter { job ->
                filterState.matchesJob(job) && GallerySearchFilter.matchesJob(job, searchQuery)
            }
        }
    }

    val gridEntries = remember(filteredImages, filteredJobs) {
        (filteredImages.map { GalleryGridEntry.Completed(it) } +
            filteredJobs.map { GalleryGridEntry.JobItem(it) })
            .sortedByDescending { it.createdAtMillis }
    }

    val columns = settings.galleryColumns.coerceIn(2, 6)

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
                placeholder = { Text("Search prompt (### negative), model, or ID...", fontSize = 12.sp) },
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Outlined.Clear, "Clear search", tint = HordeColors.TextSecondary)
                        }
                    }
                },
                modifier = Modifier.weight(1f),
            )
            Box {
                val filterActive = !filterState.isDefault
                IconButton(
                    onClick = { filterMenuExpanded = true },
                    modifier = Modifier
                        .testTag("gallery_filter_button")
                        .size(42.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (filterActive) HordeColors.TealContainer else HordeColors.CardSurface)
                        .border(
                            1.dp,
                            if (filterActive) HordeColors.TealSoft else HordeColors.BorderSubtle,
                            RoundedCornerShape(10.dp),
                        ),
                ) {
                    Icon(
                        Icons.Outlined.FilterList,
                        contentDescription = "Filter generations",
                        tint = if (filterActive) HordeColors.TealSoft else HordeColors.TextSecondary,
                    )
                }
                DropdownMenu(
                    expanded = filterMenuExpanded,
                    onDismissRequest = { filterMenuExpanded = false },
                ) {
                    FilterCheckboxMenuItem(
                        label = "All images",
                        checked = filterState.isAllSelected,
                        onToggle = { filterState = filterState.toggleAll() },
                    )
                    FilterCheckboxMenuItem(
                        label = "All queued images",
                        checked = filterState.isAllQueuedSelected,
                        onToggle = { filterState = filterState.toggleAllQueued() },
                    )
                    HorizontalDivider(color = HordeColors.BorderSubtle)
                    FilterCheckboxMenuItem(
                        label = "Locally queued",
                        checked = GalleryStatusCategory.LOCALLY_QUEUED in filterState.selectedCategories,
                        onToggle = { filterState = filterState.toggleCategory(GalleryStatusCategory.LOCALLY_QUEUED) },
                    )
                    FilterCheckboxMenuItem(
                        label = "Sent to server",
                        checked = GalleryStatusCategory.SENT_TO_SERVER in filterState.selectedCategories,
                        onToggle = { filterState = filterState.toggleCategory(GalleryStatusCategory.SENT_TO_SERVER) },
                    )
                    FilterCheckboxMenuItem(
                        label = "Errors",
                        checked = GalleryStatusCategory.ERRORS in filterState.selectedCategories,
                        onToggle = { filterState = filterState.toggleCategory(GalleryStatusCategory.ERRORS) },
                    )
                    FilterCheckboxMenuItem(
                        label = "Complete generations",
                        checked = GalleryStatusCategory.COMPLETE_GENERATIONS in filterState.selectedCategories,
                        onToggle = { filterState = filterState.toggleCategory(GalleryStatusCategory.COMPLETE_GENERATIONS) },
                    )
                }
            }
            IconButton(
                enabled = favoritesEnabled,
                onClick = { favoritesOnly = !favoritesOnly },
                modifier = Modifier
                    .testTag("gallery_favorites_button")
                    .size(42.dp)
                    .alpha(if (favoritesEnabled) 1f else 0.38f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (favoritesEnabled && favoritesOnly) Color(0x26F43F5E) else HordeColors.CardSurface)
                    .border(
                        1.dp,
                        if (favoritesEnabled && favoritesOnly) HordeColors.RoseZone else HordeColors.BorderSubtle,
                        RoundedCornerShape(10.dp),
                    ),
            ) {
                Icon(
                    if (favoritesEnabled && favoritesOnly) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = "Favorites",
                    tint = if (favoritesEnabled && favoritesOnly) HordeColors.RoseZone else HordeColors.TextSecondary,
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Outlined.FolderOpen, null, tint = HordeColors.TealSoft, modifier = Modifier.size(14.dp))
                Text(
                    settings.safFolderLabel,
                    fontSize = 11.sp,
                    color = HordeColors.TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box {
                    Text(
                        text = "${columns} cols",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = HordeColors.TealSoft,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(HordeColors.CardSurface)
                            .border(1.dp, HordeColors.BorderSubtle, RoundedCornerShape(6.dp))
                            .clickable { columnsMenuExpanded = true }
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                    DropdownMenu(
                        expanded = columnsMenuExpanded,
                        onDismissRequest = { columnsMenuExpanded = false },
                    ) {
                        (2..6).forEach { count ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "$count per row",
                                        fontWeight = if (count == columns) FontWeight.SemiBold else FontWeight.Normal,
                                        color = if (count == columns) HordeColors.TealSoft else HordeColors.TextPrimary,
                                    )
                                },
                                onClick = {
                                    vm.setGalleryColumns(count)
                                    columnsMenuExpanded = false
                                },
                            )
                        }
                    }
                }
                val countLabel = if (filterState.isDefault) {
                    "${gridEntries.size} images"
                } else {
                    "${gridEntries.size} items"
                }
                Text(countLabel, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = HordeColors.TextMuted)
            }
        }

        if (gridEntries.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = when {
                        filterState.selectedCategories.isEmpty() -> "No filter categories selected"
                        !filterState.isDefault || searchQuery.isNotBlank() || (favoritesEnabled && favoritesOnly) ->
                            "No matching items"
                        else -> "No saved WebP images yet"
                    },
                    fontSize = 13.sp,
                    color = HordeColors.TextMuted,
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                contentPadding = PaddingValues(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                itemsIndexed(gridEntries, key = { _, entry -> entry.stableKey }) { _, entry ->
                    when (entry) {
                        is GalleryGridEntry.Completed -> {
                            val img = entry.image
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(HordeColors.CardSurface)
                                    .border(1.dp, HordeColors.BorderSubtle, RoundedCornerShape(14.dp))
                                    .clickable {
                                        val imgIdx = filteredImages.indexOfFirst { it.id == img.id }.coerceAtLeast(0)
                                        viewerStartIndex = imgIdx
                                        viewerImagesSnapshot = filteredImages
                                        isFullscreen = settings.skipGalleryModalToFullscreen
                                        fullscreenHudVisible = false
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
                                            .align(Alignment.TopStart)
                                            .padding(6.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(Color.Black.copy(alpha = 0.65f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp),
                                    )
                                }
                                if (settings.showGalleryPromptOverlay && img.prompt.isNotBlank()) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .fillMaxWidth()
                                            .background(
                                                Brush.verticalGradient(
                                                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.80f)),
                                                ),
                                            )
                                            .padding(horizontal = 8.dp, vertical = 6.dp),
                                    ) {
                                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            Text(
                                                text = img.prompt.trim(),
                                                color = Color.White,
                                                fontSize = 10.sp,
                                                lineHeight = 13.sp,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            if (img.createdAtMillis > 0L) {
                                                Text(
                                                    text = GalleryTimestampFormatter.formatShort(img.createdAtMillis),
                                                    color = Color.White.copy(alpha = 0.72f),
                                                    fontSize = 9.sp,
                                                    fontFamily = FontFamily.Monospace,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            }
                                        }
                                    }
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

                        is GalleryGridEntry.JobItem -> {
                            GalleryJobGridCard(
                                job = entry.job,
                                onClick = { inspectedJobId = entry.job.id },
                            )
                        }
                    }
                }
            }
        }
    }

    inspectedJobId?.let { targetJobId ->
        val currentJob = jobs.firstOrNull { it.id == targetJobId }
        if (currentJob == null) {
            inspectedJobId = null
        } else {
            GalleryJobDetailDialog(
                job = currentJob,
                onDismiss = { inspectedJobId = null },
                onCancel = {
                    vm.cancelJob(currentJob.id)
                    inspectedJobId = null
                },
                onRemoveJob = {
                    vm.dismissJob(currentJob.id)
                    inspectedJobId = null
                },
                onCopyText = vm::copyText,
                onCopyCurl = vm::copyJsonRequestAsCurl,
                onViewCompleted = { reqId ->
                    inspectedJobId = null
                    searchQuery = reqId
                    if (!filterState.includesCompleteGenerations) {
                        filterState = filterState.toggleCategory(GalleryStatusCategory.COMPLETE_GENERATIONS)
                    }
                },
            )
        }
    }

    val activeViewerImages = remember(viewerImagesSnapshot, images) {
        viewerImagesSnapshot?.let { snapshot ->
            val latestById = images.associateBy { it.id }
            snapshot.mapNotNull { snapImg ->
                latestById[snapImg.id] ?: snapImg
            }
        }
    }

    viewerStartIndex?.let { startIndex ->
        val currentViewerList = activeViewerImages ?: filteredImages
        if (currentViewerList.isNotEmpty()) {
            val safeStart = startIndex.coerceIn(0, currentViewerList.lastIndex)
            GalleryViewerModal(
                images = currentViewerList,
                initialIndex = safeStart,
                isFullscreen = isFullscreen,
                fullscreenHudVisible = fullscreenHudVisible,
                onEnterFullscreen = {
                    isFullscreen = true
                    fullscreenHudVisible = false
                },
                onExitFullscreen = {
                    isFullscreen = false
                    fullscreenHudVisible = false
                },
                onToggleHudVisibility = {
                    fullscreenHudVisible = !fullscreenHudVisible
                },
                onToggleFavorite = { img ->
                    vm.toggleImageFavorite(img.id, img.isFavorite)
                    viewerImagesSnapshot = viewerImagesSnapshot?.map {
                        if (it.id == img.id) it.copy(isFavorite = !img.isFavorite) else it
                    }
                },
                onReroll = { vm.rerollImage(it) },
                onUseSettings = { img ->
                    vm.applySavedImageParameters(img)
                    viewerStartIndex = null
                    viewerImagesSnapshot = null
                    isFullscreen = false
                    launchActivityForDestination(context, ActivityDestination.IMAGE_GEN)
                },
                onSameRequest = { reqId ->
                    viewerStartIndex = null
                    viewerImagesSnapshot = null
                    isFullscreen = false
                    searchQuery = reqId
                },
                onCopyImage = { img -> vm.copyImage(img.fileUri) },
                onCopyText = vm::copyText,
                onCopyCurl = vm::copyJsonRequestAsCurl,
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
                onSetWallpaper = { img, flags ->
                    vm.setImageAsWallpaper(img.fileUri, flags)
                },
                onMoveToExternal = { img ->
                    vm.moveImageToExternalStorage(img)
                },
                onDelete = { img, deleteFile ->
                    vm.deleteImage(img, deleteFile)
                    val remaining = viewerImagesSnapshot?.filter { it.id != img.id } ?: emptyList()
                    viewerImagesSnapshot = remaining
                    if (remaining.isEmpty()) {
                        viewerStartIndex = null
                        viewerImagesSnapshot = null
                        isFullscreen = false
                    }
                },
                onDismiss = {
                    viewerStartIndex = null
                    viewerImagesSnapshot = null
                    isFullscreen = false
                    fullscreenHudVisible = false
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
    onEnterFullscreen: () -> Unit,
    onExitFullscreen: () -> Unit,
    onToggleHudVisibility: () -> Unit,
    onToggleFavorite: (SavedImage) -> Unit,
    onReroll: (SavedImage) -> Unit,
    onUseSettings: (SavedImage) -> Unit,
    onSameRequest: (String) -> Unit,
    onCopyImage: (SavedImage) -> Unit,
    onCopyText: (String, String) -> Unit,
    onCopyCurl: (String) -> Unit,
    onShare: (SavedImage) -> Unit,
    onOpenIn: (SavedImage) -> Unit,
    onSetWallpaper: (SavedImage, Int) -> Unit,
    onMoveToExternal: (SavedImage) -> Unit,
    onDelete: (SavedImage, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val config = LocalConfiguration.current
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(initialPage = initialIndex) { images.size }
    val activeImage = images.getOrNull(pagerState.currentPage) ?: return

    var showMoreMenu by remember { mutableStateOf(false) }
    var wallpaperTargetImage by remember { mutableStateOf<SavedImage?>(null) }
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

    @Composable
    fun TopActionBar(
        fullscreenMode: Boolean,
        modifier: Modifier = Modifier,
    ) {
        Row(
            modifier = modifier.fillMaxWidth(),
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
                    Icon(Icons.AutoMirrored.Outlined.Send, "Use settings", tint = Color.White)
                }
                IconButton(
                    onClick = if (fullscreenMode) onExitFullscreen else onEnterFullscreen,
                    modifier = Modifier.clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)),
                ) {
                    Icon(
                        if (fullscreenMode) Icons.Outlined.FullscreenExit else Icons.Outlined.Fullscreen,
                        if (fullscreenMode) "Exit full screen" else "Full screen",
                        tint = Color.White,
                    )
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
                            leadingIcon = { Icon(Icons.AutoMirrored.Outlined.OpenInNew, null) },
                            onClick = {
                                showMoreMenu = false
                                onOpenIn(activeImage)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Set as wallpaper") },
                            leadingIcon = { Icon(Icons.Outlined.Wallpaper, null) },
                            onClick = {
                                showMoreMenu = false
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                    wallpaperTargetImage = activeImage
                                } else {
                                    onSetWallpaper(activeImage, WallpaperManager.FLAG_SYSTEM)
                                }
                            },
                        )
                        if (activeImage.destinationLabel == "App Storage/images") {
                            DropdownMenuItem(
                                text = { Text("Move to external storage") },
                                leadingIcon = { Icon(Icons.Outlined.FolderOpen, null) },
                                onClick = {
                                    showMoreMenu = false
                                    onMoveToExternal(activeImage)
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun BottomMetadataSection(modifier: Modifier = Modifier) {
        Column(
            modifier = modifier.fillMaxWidth(),
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
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = "Generated: ${GalleryTimestampFormatter.formatShort(activeImage.createdAtMillis)}",
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = HordeColors.TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
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
                if (activeImage.destinationLabel == "App Storage/images") {
                    OverlayPill("Save to external") { onMoveToExternal(activeImage) }
                }
                OverlayPill("Delete", isDanger = true) {
                    deleteFileChecked = true
                    deleteConfirmTarget = activeImage
                }
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        val view = LocalView.current
        val dialogWindow = (view.parent as? DialogWindowProvider)?.window
        DisposableEffect(dialogWindow, view, isFullscreen) {
            if (dialogWindow != null) {
                WindowCompat.setDecorFitsSystemWindows(dialogWindow, false)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val lp = dialogWindow.attributes
                    if (lp.layoutInDisplayCutoutMode != WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES) {
                        lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                        dialogWindow.attributes = lp
                    }
                }
                val controller = WindowInsetsControllerCompat(dialogWindow, view)
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                if (isFullscreen) {
                    controller.hide(WindowInsetsCompat.Type.systemBars())
                } else {
                    controller.show(WindowInsetsCompat.Type.systemBars())
                }
                onDispose {
                    controller.show(WindowInsetsCompat.Type.systemBars())
                }
            } else {
                onDispose {}
            }
        }

        if (isFullscreen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                ) { page ->
                    val item = images[page]
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable(onClick = onToggleHudVisibility),
                        contentAlignment = Alignment.Center,
                    ) {
                        AsyncImage(
                            model = Uri.parse(item.fileUri),
                            contentDescription = item.prompt,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }

                if (fullscreenHudVisible) {
                    TopActionBar(
                        fullscreenMode = true,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.85f), Color.Transparent)))
                            .statusBarsPadding()
                            .displayCutoutPadding()
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                    )

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

                    BottomMetadataSection(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.92f))))
                            .navigationBarsPadding()
                            .displayCutoutPadding()
                            .padding(12.dp),
                    )
                }
            }
        } else {
            val maxModalHeight = (config.screenHeightDp * 0.88f).dp
            val maxPagerHeight = (config.screenHeightDp * 0.56f).coerceAtLeast(180f).dp
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.72f))
                    .systemBarsPadding()
                    .clickable(onClick = onDismiss)
                    .padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = HordeColors.CardSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 620.dp)
                        .heightIn(max = maxModalHeight)
                        .wrapContentHeight()
                        .border(1.dp, HordeColors.BorderStrong, RoundedCornerShape(20.dp))
                        .clickable(enabled = false) {},
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TopActionBar(fullscreenMode = false)

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f, fill = false)
                                .heightIn(min = 160.dp, max = maxPagerHeight)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.Black),
                        ) {
                            HorizontalPager(
                                state = pagerState,
                                modifier = Modifier.fillMaxSize(),
                            ) { page ->
                                val item = images[page]
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clickable(onClick = onEnterFullscreen),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    AsyncImage(
                                        model = Uri.parse(item.fileUri),
                                        contentDescription = item.prompt,
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                            }
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
                                        .padding(start = 6.dp)
                                        .size(34.dp)
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
                                        .padding(end = 6.dp)
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.55f)),
                                ) {
                                    Icon(Icons.Outlined.ChevronRight, "Next", tint = Color.White)
                                }
                            }
                        }

                        BottomMetadataSection()
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
                appendLine("Generated: ${GalleryTimestampFormatter.formatFull(activeImage.createdAtMillis)}")
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
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = { onCopyText(mode, jsonContent) }) { Text("Copy") }
                    if (mode == "request") {
                        OutlinedButton(onClick = { onCopyCurl(jsonContent) }) { Text("curl") }
                    }
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            jsonToSave = jsonContent
                            saveJsonLauncher.launch("hordeslop_${activeImage.generationId.take(8)}_$mode.json")
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

    wallpaperTargetImage?.let { target ->
        AlertDialog(
            onDismissRequest = { wallpaperTargetImage = null },
            containerColor = HordeColors.CardSurface,
            title = { Text("Set as wallpaper", color = HordeColors.TextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            wallpaperTargetImage = null
                            onSetWallpaper(target, WallpaperManager.FLAG_SYSTEM)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Home screen")
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        OutlinedButton(
                            onClick = {
                                wallpaperTargetImage = null
                                onSetWallpaper(target, WallpaperManager.FLAG_LOCK)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Lock screen")
                        }
                        Button(
                            colors = ButtonDefaults.buttonColors(containerColor = HordeColors.TealPrimary),
                            onClick = {
                                wallpaperTargetImage = null
                                onSetWallpaper(target, WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Home & lock screens", color = Color(0xFF020617), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { wallpaperTargetImage = null }) { Text("Cancel") } },
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

@Composable
private fun FilterCheckboxMenuItem(
    label: String,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    DropdownMenuItem(
        text = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Checkbox(
                    checked = checked,
                    onCheckedChange = null,
                )
                Text(
                    text = label,
                    fontSize = 13.sp,
                    color = HordeColors.TextPrimary,
                )
            }
        },
        onClick = onToggle,
    )
}

@Composable
private fun GalleryJobGridCard(
    job: HordeJob,
    onClick: () -> Unit,
) {
    val isError = job.status == JobStatus.FAILED
    val isLocal = job.status == JobStatus.LOCAL_QUEUED ||
        (job.status == JobStatus.PAUSED && job.requestId.isBlank())
    val accentColor = when {
        isError -> HordeColors.RoseZone
        isLocal -> HordeColors.AmberText
        job.status == JobStatus.PAUSED -> HordeColors.TextSecondary
        else -> HordeColors.TealSoft
    }
    val statusBadge = when (job.status) {
        JobStatus.LOCAL_QUEUED -> "Local"
        JobStatus.PAUSED -> if (job.requestId.isBlank()) "Paused (local)" else "Paused"
        JobStatus.QUEUED -> "Sent"
        JobStatus.PROCESSING -> if (job.waitTimeSeconds > 0) "~${job.waitTimeSeconds}s" else "Running"
        JobStatus.FAILED -> "Error"
        JobStatus.COMPLETED -> "Done"
        JobStatus.CANCELLED_PARTIAL -> "Partial"
    }
    val statusIcon = when {
        isError -> Icons.Outlined.ErrorOutline
        job.status == JobStatus.PAUSED -> Icons.Outlined.PauseCircleOutline
        isLocal -> Icons.Outlined.Schedule
        else -> Icons.Outlined.CloudQueue
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(14.dp))
            .background(HordeColors.CardSurface)
            .border(1.dp, accentColor.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(8.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(accentColor.copy(alpha = 0.18f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Icon(statusIcon, contentDescription = null, tint = accentColor, modifier = Modifier.size(12.dp))
                    Text(
                        text = statusBadge,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = accentColor,
                        maxLines = 1,
                    )
                }
                Text(
                    text = "${job.finished}/${job.total}",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = HordeColors.TextSecondary,
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = job.prompt.ifBlank { "(No prompt)" },
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    color = HordeColors.TextPrimary,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                if (isError && !job.errorMessage.isNullOrBlank()) {
                    Text(
                        text = job.errorMessage,
                        fontSize = 10.sp,
                        color = HordeColors.RoseText,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (job.status == JobStatus.LOCAL_QUEUED ||
                    job.status == JobStatus.QUEUED ||
                    job.status == JobStatus.PROCESSING
                ) {
                    val progress = if (job.total > 0) job.finished.toFloat() / job.total else 0f
                    LinearProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        color = accentColor,
                        trackColor = HordeColors.SubCardSurface,
                        modifier = Modifier.fillMaxWidth().height(3.dp),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = job.models.firstOrNull().orEmpty(),
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        color = HordeColors.TealSoft,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = GalleryTimestampFormatter.formatShort(job.createdAtMillis),
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        color = HordeColors.TextMuted,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun GalleryJobDetailDialog(
    job: HordeJob,
    onDismiss: () -> Unit,
    onCancel: () -> Unit,
    onRemoveJob: () -> Unit,
    onCopyText: (String, String) -> Unit,
    onCopyCurl: (String) -> Unit,
    onViewCompleted: (String) -> Unit,
) {
    val isActive = job.status == JobStatus.LOCAL_QUEUED ||
        job.status == JobStatus.QUEUED ||
        job.status == JobStatus.PROCESSING ||
        job.status == JobStatus.PAUSED
    val statusLabel = when (job.status) {
        JobStatus.LOCAL_QUEUED -> "Queued locally (awaiting dispatch)"
        JobStatus.QUEUED -> "Sent to server (queued)"
        JobStatus.PROCESSING -> "Processing (#${job.queuePosition}, ~${job.waitTimeSeconds}s)"
        JobStatus.PAUSED -> if (job.requestId.isBlank()) "Paused (local)" else "Paused (server)"
        JobStatus.COMPLETED -> "Completed"
        JobStatus.CANCELLED_PARTIAL -> "Stopped early (${job.finished}/${job.total})"
        JobStatus.FAILED -> "Error"
    }
    val detailsText = buildString {
        appendLine("Status: $statusLabel")
        appendLine("Request ID: ${job.requestId.ifBlank { "Pending local dispatch" }}")
        appendLine("Models: ${job.models.joinToString(", ")}")
        appendLine("Progress: ${job.finished}/${job.total} finished (${job.processing} processing)")
        appendLine("Queued at: ${GalleryTimestampFormatter.formatFull(job.createdAtMillis)}")
        if (!job.errorMessage.isNullOrBlank()) {
            appendLine()
            appendLine("Error:")
            appendLine(job.errorMessage)
        }
        appendLine()
        appendLine("Prompt:")
        appendLine(job.prompt)
        if (job.negativePrompt.isNotBlank()) {
            appendLine()
            appendLine("Negative:")
            appendLine(job.negativePrompt)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = HordeColors.CardSurface,
        title = {
            Text(
                text = if (job.status == JobStatus.FAILED) "Generation Error" else "Queued Generation",
                color = HordeColors.TextPrimary,
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(HordeColors.SubCardSurface, RoundedCornerShape(8.dp))
                        .padding(10.dp),
                ) {
                    Text(
                        text = detailsText,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = HordeColors.TextSecondary,
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                ) {
                    OutlinedButton(onClick = { onCopyText("request JSON", job.submittedPayloadJson) }) {
                        Text("Copy JSON", fontSize = 11.sp)
                    }
                    OutlinedButton(onClick = { onCopyCurl(job.submittedPayloadJson) }) {
                        Text("curl", fontSize = 11.sp)
                    }
                    if (job.finished > 0 && job.requestId.isNotBlank()) {
                        OutlinedButton(onClick = { onViewCompleted(job.requestId) }) {
                            Text("View saved (${job.finished})", fontSize = 11.sp)
                        }
                    }
                }
            }
        },
        dismissButton = {
            if (isActive) {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = HordeColors.RoseZone),
                    onClick = onCancel,
                ) {
                    Text("Cancel job", color = Color.White)
                }
            } else {
                OutlinedButton(onClick = onRemoveJob) {
                    Text("Dismiss")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
    )
}
