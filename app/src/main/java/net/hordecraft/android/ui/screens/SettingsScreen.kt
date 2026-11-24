package net.hordeslop.studio.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.Balance
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import coil.compose.AsyncImage
import net.hordeslop.studio.BuildConfig
import net.hordeslop.studio.data.local.AppSettings
import net.hordeslop.studio.domain.ActivityAnimationMode
import net.hordeslop.studio.domain.AuthMode
import net.hordeslop.studio.domain.GenerateButtonPlacement
import net.hordeslop.studio.domain.HordeConstants
import net.hordeslop.studio.domain.LauncherIconChoice
import net.hordeslop.studio.domain.NavLayoutMode
import net.hordeslop.studio.ui.MainViewModel
import net.hordeslop.studio.ui.theme.HordeColors

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    vm: MainViewModel,
    settings: AppSettings,
    toastMessage: String? = null,
) {
    val context = LocalContext.current

    var draftHordeKey by remember(settings.hordeApiKey) { mutableStateOf(settings.hordeApiKey) }
    var showHordeKey by remember { mutableStateOf(false) }
    var draftCivitaiKey by remember(settings.civitaiApiKey) { mutableStateOf(settings.civitaiApiKey) }
    var showCivitaiKey by remember { mutableStateOf(false) }

    val isOfficialEndpoint = HordeConstants.normalizeBaseUrl(settings.apiBaseUrl) == HordeConstants.DEFAULT_API_BASE_URL
    var endpointModeOther by remember(settings.apiBaseUrl) { mutableStateOf(!isOfficialEndpoint) }
    var draftEndpointUrl by remember(settings.apiBaseUrl) { mutableStateOf(settings.apiBaseUrl) }

    var showLauncherIconModal by remember { mutableStateOf(false) }
    var showOssCreditsModal by remember { mutableStateOf(false) }
    var showPrivacyLegalModal by remember { mutableStateOf(false) }

    var editingRepoIndex by remember { mutableStateOf<Int?>(null) } // -1 for add, >=0 for edit
    var draftRepoUrl by remember { mutableStateOf("") }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }
    var replaceExistingOnImport by remember { mutableStateOf(false) }

    val safFolderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { treeUri ->
        if (treeUri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            val display = DocumentFile.fromTreeUri(context, treeUri)?.name ?: "Selected Folder"
            vm.setStorageSafTree(treeUri.toString(), display)
        }
    }

    val exportDbLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            vm.exportDatabase(uri)
        }
    }

    val importDbLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            replaceExistingOnImport = false
            pendingImportUri = uri
        }
    }

    fun openExternalUrl(url: String) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }

    val currentAuthMode = AuthMode.inferFromKey(settings.hordeApiKey)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        toastMessage?.let { msg ->
            LaunchedEffect(msg) {
                kotlinx.coroutines.delay(2_800L)
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

        // Top of Settings: AI Notice
        Surface(
            color = Color(0xFF18202B),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, HordeColors.AmberZone.copy(alpha = 0.35f), RoundedCornerShape(14.dp)),
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Outlined.Info, null, tint = HordeColors.AmberText, modifier = Modifier.size(16.dp))
                    Text("Built with AI", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = HordeColors.AmberText)
                }
                Text(
                    "All code in HordeSlop Studio is 100% vibeslopped by AI (${BuildConfig.AI_ATTRIBUTION_MODEL}).",
                    fontSize = 12.sp,
                    color = HordeColors.TextSecondary,
                )
            }
        }

        // AI Horde section: Horde API Key + Server URL (#89)
        StudioCard {
            Text("AI Horde", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = HordeColors.TextPrimary)

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("API Key", fontSize = 12.sp, color = HordeColors.TextSecondary)
                Text(
                    text = when (currentAuthMode) {
                        AuthMode.USER_KEY -> "Personal Key"
                        AuthMode.SHARED_KEY -> "Shared Key"
                        AuthMode.ANONYMOUS -> "Anonymous"
                    },
                    fontSize = 12.sp,
                    color = HordeColors.TealSoft,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = draftHordeKey,
                    onValueChange = { draftHordeKey = it },
                    singleLine = true,
                    visualTransformation = if (showHordeKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showHordeKey = !showHordeKey }) {
                            Icon(if (showHordeKey) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, null)
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = { vm.saveHordeApiKey(draftHordeKey) },
                    colors = ButtonDefaults.buttonColors(containerColor = HordeColors.TealPrimary),
                ) {
                    Text("Save", color = Color(0xFF020617), fontWeight = FontWeight.SemiBold)
                }
            }

            HorizontalDivider(color = HordeColors.BorderSubtle)

            Text("Server", fontSize = 12.sp, color = HordeColors.TextSecondary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ToggleChipButton(
                    text = "aihorde.net",
                    selected = !endpointModeOther,
                    onClick = {
                        endpointModeOther = false
                        draftEndpointUrl = HordeConstants.DEFAULT_API_BASE_URL
                        vm.saveApiBaseUrl(HordeConstants.DEFAULT_API_BASE_URL)
                    },
                )
                ToggleChipButton(
                    text = "Other",
                    selected = endpointModeOther,
                    onClick = {
                        endpointModeOther = true
                        if (draftEndpointUrl.isBlank()) draftEndpointUrl = HordeConstants.DEFAULT_API_BASE_URL
                    },
                )
            }
            if (endpointModeOther) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = draftEndpointUrl,
                        onValueChange = { draftEndpointUrl = it },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        onClick = { vm.saveApiBaseUrl(draftEndpointUrl) },
                        colors = ButtonDefaults.buttonColors(containerColor = HordeColors.TealPrimary),
                    ) {
                        Text("Apply", color = Color(0xFF020617), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        // Preferences (#90: Navigation, Civitai key, Storage location, Styles Repos, Auto-clear, App Icon, Generate Button, Previews, Full-screen)
        StudioCard {
            Text("Preferences", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = HordeColors.TextPrimary)

            // App Icon Switcher
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(HordeColors.SubCardSurface)
                    .border(1.dp, HordeColors.BorderSubtle, RoundedCornerShape(10.dp))
                    .clickable { showLauncherIconModal = true }
                    .padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (settings.launcherIcon == LauncherIconChoice.ROBOT_PLACEHOLDER) {
                        Icon(Icons.Outlined.Android, null, tint = HordeColors.TealSoft)
                    } else {
                        AsyncImage(
                            model = HordeConstants.HAIDRA_LOGO_URL,
                            contentDescription = "Haidra Logo",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Column {
                        Text("App Icon", fontSize = 13.sp, color = HordeColors.TextPrimary)
                        Text(
                            if (settings.launcherIcon == LauncherIconChoice.ROBOT_PLACEHOLDER) "Robot (Default)" else "Haidra Logo",
                            fontSize = 11.sp,
                            color = HordeColors.TextMuted,
                        )
                    }
                }
                Text("Change", fontSize = 12.sp, color = HordeColors.TealSoft)
            }

            // Navigation layout
            Text("Navigation", fontSize = 12.sp, color = HordeColors.TextSecondary)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                NavLayoutMode.entries.forEach { mode ->
                    Box(modifier = Modifier.weight(1f)) {
                        ToggleChipButton(
                            text = mode.label,
                            selected = settings.navLayout == mode,
                            onClick = { vm.setNavLayout(mode) },
                        )
                    }
                }
            }

            // Screen transition animation
            Text("Screen Transition", fontSize = 12.sp, color = HordeColors.TextSecondary)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ActivityAnimationMode.entries.forEach { animMode ->
                    ToggleChipButton(
                        text = animMode.label,
                        selected = settings.activityAnimation == animMode,
                        onClick = { vm.setActivityAnimation(animMode) },
                    )
                }
            }

            // Generate Button Placement
            Text("Generate Button", fontSize = 12.sp, color = HordeColors.TextSecondary)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                GenerateButtonPlacement.entries.forEach { placement ->
                    ToggleChipButton(
                        text = placement.label,
                        selected = settings.generateButtonPlacement == placement,
                        onClick = { vm.setGenerateButtonPlacement(placement) },
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { vm.setShowModelPreviews(!settings.showModelPreviews) },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Model previews", fontSize = 13.sp, color = HordeColors.TextPrimary)
                    Text("Show images when picking models", fontSize = 11.sp, color = HordeColors.TextMuted)
                }
                Checkbox(checked = settings.showModelPreviews, onCheckedChange = vm::setShowModelPreviews)
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { vm.setSkipGalleryModalToFullscreen(!settings.skipGalleryModalToFullscreen) },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Full-screen gallery", fontSize = 13.sp, color = HordeColors.TextPrimary)
                    Text("Open tapped images in full screen", fontSize = 11.sp, color = HordeColors.TextMuted)
                }
                Checkbox(
                    checked = settings.skipGalleryModalToFullscreen,
                    onCheckedChange = vm::setSkipGalleryModalToFullscreen,
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { vm.setAutoClearCompletedQueue(!settings.autoClearCompletedQueue) },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Auto-clear completed requests", fontSize = 13.sp, color = HordeColors.TextPrimary)
                    Text("Automatically clear finished items from Queue", fontSize = 11.sp, color = HordeColors.TextMuted)
                }
                Checkbox(
                    checked = settings.autoClearCompletedQueue,
                    onCheckedChange = vm::setAutoClearCompletedQueue,
                )
            }

            HorizontalDivider(color = HordeColors.BorderSubtle)

            // Civitai API key inside Preferences (#90)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Civitai API Key (Optional)", fontSize = 12.sp, color = HordeColors.TextSecondary)
                Text(if (settings.civitaiApiKey.isNotBlank()) "Saved" else "None", fontSize = 11.sp, color = HordeColors.TextMuted)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = draftCivitaiKey,
                    onValueChange = { draftCivitaiKey = it },
                    singleLine = true,
                    placeholder = { Text("Civitai key...") },
                    visualTransformation = if (showCivitaiKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showCivitaiKey = !showCivitaiKey }) {
                            Icon(if (showCivitaiKey) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, null)
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = { vm.saveCivitaiApiKey(draftCivitaiKey) },
                    colors = ButtonDefaults.buttonColors(containerColor = HordeColors.TealPrimary),
                ) {
                    Text("Save", color = Color(0xFF020617), fontWeight = FontWeight.SemiBold)
                }
            }



            HorizontalDivider(color = HordeColors.BorderSubtle)

            // Styles Repos (#83, #84)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Styles Repos (${settings.stylesRepoUrls.size})", fontSize = 12.sp, color = HordeColors.TextSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        onClick = { vm.resetStylesRepoUrls() },
                    ) {
                        Icon(Icons.Outlined.RestartAlt, null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.size(3.dp))
                        Text("Reset", fontSize = 11.sp)
                    }
                    TextButton(
                        onClick = {
                            draftRepoUrl = ""
                            editingRepoIndex = -1
                        },
                    ) {
                        Icon(Icons.Outlined.Add, null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.size(3.dp))
                        Text("Add", fontSize = 11.sp)
                    }
                }
            }
            if (settings.stylesRepoUrls.isEmpty()) {
                Text("No styles repos configured.", fontSize = 11.sp, color = HordeColors.TextMuted)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    settings.stylesRepoUrls.forEachIndexed { idx, repoUrl ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(HordeColors.SubCardSurface)
                                .border(1.dp, HordeColors.BorderSubtle, RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = repoUrl,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = HordeColors.TextPrimary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(
                                onClick = {
                                    draftRepoUrl = repoUrl
                                    editingRepoIndex = idx
                                },
                                modifier = Modifier.size(28.dp),
                            ) {
                                Icon(Icons.Outlined.Edit, "Edit repo", tint = HordeColors.TextSecondary, modifier = Modifier.size(15.dp))
                            }
                            IconButton(
                                onClick = {
                                    val updated = settings.stylesRepoUrls.toMutableList().apply { removeAt(idx) }
                                    vm.saveStylesRepoUrls(updated)
                                },
                                modifier = Modifier.size(28.dp),
                            ) {
                                Icon(Icons.Outlined.DeleteOutline, "Remove repo", tint = HordeColors.RoseText, modifier = Modifier.size(15.dp))
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = HordeColors.BorderSubtle)

            // Storage location inside Preferences (#90)
            Text("Storage Location", fontSize = 12.sp, color = HordeColors.TextSecondary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { vm.setStorageDownloads() },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Downloads/HordeSlop Studio", fontSize = 11.sp)
                }
                Button(
                    onClick = { safFolderLauncher.launch(null) },
                    colors = ButtonDefaults.buttonColors(containerColor = HordeColors.ElevatedButton),
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Choose Folder", fontSize = 11.sp, color = HordeColors.TextPrimary)
                }
            }
            Text("Saving to: ${settings.safFolderLabel}", fontSize = 11.sp, color = HordeColors.TextMuted)

            HorizontalDivider(color = HordeColors.BorderSubtle)

            Text("Database", fontSize = 12.sp, color = HordeColors.TextSecondary)
            Text(
                "Back up or restore prompt history, favorites, model defaults, and gallery metadata.",
                fontSize = 11.sp,
                color = HordeColors.TextMuted,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { exportDbLauncher.launch("hordeslop-studio-database.json") },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Export", fontSize = 12.sp)
                }
                Button(
                    onClick = { importDbLauncher.launch(arrayOf("application/json", "*/*")) },
                    colors = ButtonDefaults.buttonColors(containerColor = HordeColors.ElevatedButton),
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Import", fontSize = 12.sp, color = HordeColors.TextPrimary)
                }
            }
        }

        // Notifications
        StudioCard {
            Text("Notifications", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = HordeColors.TextPrimary)
            HordeConstants.NOTIFICATION_CHANNELS.forEach { ch ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(HordeColors.SubCardSurface)
                        .clickable {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                runCatching {
                                    context.startActivity(
                                        Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                            .putExtra(Settings.EXTRA_CHANNEL_ID, ch.id),
                                    )
                                }
                            }
                        }
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(ch.name, fontSize = 12.sp, color = HordeColors.TextPrimary)
                        Text(ch.importanceLabel, fontSize = 11.sp, color = HordeColors.TextMuted)
                    }
                    if (!ch.activeInMvp) {
                        Text("Later", fontSize = 11.sp, color = HordeColors.TextMuted)
                    }
                }
            }
        }

        // Open Source Licenses & Privacy / Legal (Modals)
        StudioCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(HordeColors.SubCardSurface)
                    .clickable { showOssCreditsModal = true }
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.Balance, null, tint = HordeColors.TealSoft)
                    Column {
                        Text("Open Source Licenses", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = HordeColors.TextPrimary)
                        Text("AGPL-3.0-or-later", fontSize = 11.sp, color = HordeColors.TextMuted)
                    }
                }
                Text("View", fontSize = 12.sp, color = HordeColors.TealSoft)
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(HordeColors.SubCardSurface)
                    .clickable { showPrivacyLegalModal = true }
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("Privacy & Legal", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = HordeColors.TextPrimary)
                    Text("App privacy, AI Horde terms & links", fontSize = 11.sp, color = HordeColors.TextMuted)
                }
                Text("View", fontSize = 12.sp, color = HordeColors.TealSoft)
            }
        }
    }

    // Add/Edit Styles Repo Modal
    editingRepoIndex?.let { targetIdx ->
        AlertDialog(
            onDismissRequest = { editingRepoIndex = null },
            containerColor = HordeColors.CardSurface,
            title = { Text(if (targetIdx < 0) "Add Styles Repo" else "Edit Styles Repo", color = HordeColors.TextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Enter a GitHub repository URL or raw styles.json URL.",
                        fontSize = 12.sp,
                        color = HordeColors.TextSecondary,
                    )
                    OutlinedTextField(
                        value = draftRepoUrl,
                        onValueChange = { draftRepoUrl = it },
                        singleLine = true,
                        placeholder = { Text("https://github.com/...") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            dismissButton = { OutlinedButton(onClick = { editingRepoIndex = null }) { Text("Cancel") } },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = HordeColors.TealPrimary),
                    onClick = {
                        val trimmed = draftRepoUrl.trim()
                        if (trimmed.isNotEmpty()) {
                            val list = settings.stylesRepoUrls.toMutableList()
                            if (targetIdx < 0) {
                                list += trimmed
                            } else if (targetIdx in list.indices) {
                                list[targetIdx] = trimmed
                            }
                            vm.saveStylesRepoUrls(list)
                        }
                        editingRepoIndex = null
                    },
                ) {
                    Text("Save", color = Color(0xFF020617), fontWeight = FontWeight.SemiBold)
                }
            },
        )
    }

    // Import Database Confirmation Modal
    pendingImportUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingImportUri = null },
            containerColor = HordeColors.CardSurface,
            title = { Text("Import Database", color = HordeColors.TextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Restore history, favorites, model defaults, and gallery records from the selected backup.",
                        fontSize = 12.sp,
                        color = HordeColors.TextSecondary,
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { replaceExistingOnImport = !replaceExistingOnImport },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = replaceExistingOnImport,
                            onCheckedChange = { replaceExistingOnImport = it },
                        )
                        Column(Modifier.weight(1f)) {
                            Text("Replace existing data", fontSize = 12.sp, color = HordeColors.TextPrimary)
                            Text(
                                "Unchecked merges backup entries with existing records.",
                                fontSize = 11.sp,
                                color = HordeColors.TextMuted,
                            )
                        }
                    }
                }
            },
            dismissButton = { OutlinedButton(onClick = { pendingImportUri = null }) { Text("Cancel") } },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = HordeColors.TealPrimary),
                    onClick = {
                        vm.importDatabase(uri, replaceExistingOnImport)
                        pendingImportUri = null
                    },
                ) {
                    Text("Import", color = Color(0xFF020617), fontWeight = FontWeight.SemiBold)
                }
            },
        )
    }

    // Launcher Icon Modal
    if (showLauncherIconModal) {
        AlertDialog(
            onDismissRequest = { showLauncherIconModal = false },
            containerColor = HordeColors.CardSurface,
            title = { Text("App Icon", color = HordeColors.TextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(
                        color = HordeColors.SubCardSurface,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, HordeColors.AmberZone.copy(alpha = 0.35f), RoundedCornerShape(10.dp)),
                    ) {
                        Text(
                            "HordeSlop Studio is not affiliated with AI Horde or Haidra.",
                            fontSize = 12.sp,
                            color = HordeColors.AmberText,
                            modifier = Modifier.padding(10.dp),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(
                            color = HordeColors.SubCardSurface,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .border(
                                    1.dp,
                                    if (settings.launcherIcon == LauncherIconChoice.ROBOT_PLACEHOLDER) HordeColors.TealPrimary else HordeColors.BorderSubtle,
                                    RoundedCornerShape(12.dp),
                                )
                                .clickable {
                                    vm.setLauncherIcon(LauncherIconChoice.ROBOT_PLACEHOLDER)
                                    showLauncherIconModal = false
                                },
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Icon(Icons.Outlined.Android, null, tint = HordeColors.TealSoft, modifier = Modifier.size(32.dp))
                                Text("Robot", fontSize = 12.sp, color = HordeColors.TextPrimary)
                                Text("Default", fontSize = 10.sp, color = HordeColors.TextMuted)
                            }
                        }

                        Surface(
                            color = HordeColors.SubCardSurface,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .border(
                                    1.dp,
                                    if (settings.launcherIcon == LauncherIconChoice.HORDE_HAIDRA) HordeColors.TealPrimary else HordeColors.BorderSubtle,
                                    RoundedCornerShape(12.dp),
                                )
                                .clickable {
                                    vm.setLauncherIcon(LauncherIconChoice.HORDE_HAIDRA)
                                    showLauncherIconModal = false
                                },
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                AsyncImage(
                                    model = HordeConstants.HAIDRA_LOGO_URL,
                                    contentDescription = "Haidra Logo",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.size(32.dp),
                                )
                                Text("Haidra", fontSize = 12.sp, color = HordeColors.TextPrimary)
                                Text("Logo", fontSize = 10.sp, color = HordeColors.TextMuted)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showLauncherIconModal = false }) { Text("Close") } },
        )
    }

    // Open Source Licenses Modal
    if (showOssCreditsModal) {
        AlertDialog(
            onDismissRequest = { showOssCreditsModal = false },
            containerColor = HordeColors.CardSurface,
            title = { Text("Open Source Licenses", color = HordeColors.TextPrimary) },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 380.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Licensed under AGPL-3.0-or-later.", fontSize = 12.sp, color = HordeColors.TextSecondary)
                    HordeConstants.OSS_CREDITS.forEach { lib ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { openExternalUrl(lib.url) }
                                .padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(lib.name, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = HordeColors.TextPrimary)
                                Text(lib.license, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = HordeColors.TextMuted)
                            }
                            Icon(Icons.Outlined.OpenInNew, null, tint = HordeColors.TextMuted, modifier = Modifier.size(16.dp))
                        }
                        HorizontalDivider(color = HordeColors.BorderSubtle)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showOssCreditsModal = false }) { Text("Close") } },
        )
    }

    // Unified Privacy & Legal Modal
    if (showPrivacyLegalModal) {
        AlertDialog(
            onDismissRequest = { showPrivacyLegalModal = false },
            containerColor = HordeColors.CardSurface,
            title = { Text("Privacy & Legal", color = HordeColors.TextPrimary) },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Surface(
                        color = HordeColors.SubCardSurface,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            "Nothing is saved or collected by the developer; prompts, keys, and images stay on your device and the server you use.",
                            fontSize = 12.sp,
                            color = HordeColors.TextSecondary,
                            modifier = Modifier.padding(10.dp),
                        )
                    }

                    Surface(
                        color = HordeColors.SubCardSurface,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("AI Horde Privacy Policy", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = HordeColors.TextPrimary)
                            Text(
                                "Last updated 18 Mar 2024. Explains personal data handling, API key authentication, temporary prompt/image transit through volunteer workers, and privacy rights.",
                                fontSize = 11.sp,
                                color = HordeColors.TextMuted,
                            )
                            Text(
                                "aihorde.net/privacy",
                                fontSize = 11.sp,
                                color = HordeColors.TealSoft,
                                modifier = Modifier.clickable { openExternalUrl("https://aihorde.net/privacy") },
                            )
                        }
                    }

                    Surface(
                        color = HordeColors.SubCardSurface,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("AI Horde Terms and Conditions", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = HordeColors.TextPrimary)
                            Text(
                                "Last updated 07 Jan 2023. Sets rules for using AI Horde services, prohibited content restrictions, and volunteer network availability without warranty.",
                                fontSize = 11.sp,
                                color = HordeColors.TextMuted,
                            )
                            Text(
                                "aihorde.net/terms",
                                fontSize = 11.sp,
                                color = HordeColors.TealSoft,
                                modifier = Modifier.clickable { openExternalUrl("https://aihorde.net/terms") },
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { openExternalUrl("https://aihorde.net") }) { Text("aihorde.net", fontSize = 11.sp) }
                        OutlinedButton(onClick = { openExternalUrl("https://haidra.net") }) { Text("haidra.net", fontSize = 11.sp) }
                        OutlinedButton(onClick = { openExternalUrl(BuildConfig.REPO_URL) }) { Text("Source Code", fontSize = 11.sp) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showPrivacyLegalModal = false }) { Text("Close") } },
        )
    }
}
