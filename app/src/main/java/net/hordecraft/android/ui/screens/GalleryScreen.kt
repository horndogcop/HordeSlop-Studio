package net.hordeslop.studio.ui.screens

import android.app.WallpaperManager
import android.content.Intent
import android.net.Uri
import android.os.Build
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
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.FullscreenExit
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Wallpaper
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
import net.hordeslop.studio.data.local.AppSettings
import net.hordeslop.studio.domain.ActivityDestination
import net.hordeslop.studio.domain.GallerySearchFilter
import net.hordeslop.studio.domain.SavedImage
import net.hordeslop.studio.ui.MainViewModel
import net.hordeslop.studio.ui.components.launchActivityForDestination
import net.hordeslop.studio.ui.theme.HordeColors

@Composable
fun GalleryScreen(
    vm: MainViewModel,
    images: List<SavedImage>,
    settings: AppSettings,
    toastMessage: String?,
    pendingSearchQuery: String? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var searchQuery by remember { mutableStateOf("") }
    var favoritesOnly by remember { mutableStateOf(false) }

    var viewerStartIndex by remember { mutableStateOf<Int?>(null) }
    var viewerImagesSnapshot by remember { mutableStateOf<List<SavedImage>?>(null) }
    var isFullscreen by remember { mutableStateOf(false) }
    var fullscreenHudVisible by remember { mutableStateOf(false) }
    var columnsMenuExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(pendingSearchQuery) {
        if (!pendingSearchQuery.isNullOrBlank()) {
            searchQuery = pendingSearchQuery
            viewerStartIndex = null
            viewerImagesSnapshot = null
            isFullscreen = false
            vm.clearPendingGallerySearchQuery()
        }
    }

    val filtered = remember(images, searchQuery, favoritesOnly) {
        images.filter { img ->
            if (favoritesOnly && !img.isFavorite) return@filter false
            GallerySearchFilter.matches(img, searchQuery)
        }
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
                Text("${filtered.size} images", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = HordeColors.TextMuted)
            }
        }

        if (filtered.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = if (searchQuery.isNotBlank() || favoritesOnly) "No matching images" else "No saved WebP images yet",
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
                                viewerImagesSnapshot = filtered
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
                                Text(
                                    text = img.prompt.trim(),
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    lineHeight = 13.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
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
            }
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
        val currentViewerList = activeViewerImages ?: filtered
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
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
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
                            .systemBarsPadding()
                            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.85f), Color.Transparent)))
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
                            .systemBarsPadding()
                            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.92f))))
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
