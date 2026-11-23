package net.hordecraft.android.ui.components

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import net.hordecraft.android.BuildConfig
import net.hordecraft.android.data.local.AppSettings
import net.hordecraft.android.domain.ActivityDestination
import net.hordecraft.android.domain.AuthMode
import net.hordecraft.android.domain.HordeConstants
import net.hordecraft.android.domain.HordeJob
import net.hordecraft.android.domain.JobStatus
import net.hordecraft.android.domain.NavLayoutMode
import net.hordecraft.android.domain.UserStatsSummary
import net.hordecraft.android.ui.activities.AlchemistActivity
import net.hordecraft.android.ui.activities.GalleryActivity
import net.hordecraft.android.ui.activities.ImageGenActivity
import net.hordecraft.android.ui.activities.ModelsActivity
import net.hordecraft.android.ui.activities.SettingsActivity
import net.hordecraft.android.ui.activities.TextGenActivity
import net.hordecraft.android.ui.activities.UserActivity
import net.hordecraft.android.ui.theme.HordeColors

private data class NavEntry(
    val dest: ActivityDestination,
    val icon: ImageVector,
    val isMvpTab: Boolean,
)

private val NAV_ENTRIES = listOf(
    NavEntry(ActivityDestination.IMAGE_GEN, Icons.Outlined.Image, true),
    NavEntry(ActivityDestination.GALLERY, Icons.Outlined.Collections, true),
    NavEntry(ActivityDestination.USER, Icons.Outlined.Person, true),
    NavEntry(ActivityDestination.SETTINGS, Icons.Outlined.Settings, true),
    NavEntry(ActivityDestination.MODELS_STUB, Icons.Outlined.Memory, false),
    NavEntry(ActivityDestination.TEXT_GEN_STUB, Icons.Outlined.Description, false),
    NavEntry(ActivityDestination.ALCHEMIST_STUB, Icons.Outlined.AutoAwesome, false),
)

fun launchActivityForDestination(context: Context, destination: ActivityDestination) {
    val cls = when (destination) {
        ActivityDestination.IMAGE_GEN -> ImageGenActivity::class.java
        ActivityDestination.GALLERY -> GalleryActivity::class.java
        ActivityDestination.USER -> UserActivity::class.java
        ActivityDestination.SETTINGS -> SettingsActivity::class.java
        ActivityDestination.MODELS_STUB -> ModelsActivity::class.java
        ActivityDestination.TEXT_GEN_STUB -> TextGenActivity::class.java
        ActivityDestination.ALCHEMIST_STUB -> AlchemistActivity::class.java
    }
    val intent = Intent(context, cls).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
    context.startActivity(intent)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HordeModeScaffold(
    currentDestination: ActivityDestination,
    settings: AppSettings,
    userStats: UserStatsSummary,
    activeJobs: List<HordeJob>,
    queuePaused: Boolean,
    onDismissOnboarding: (String) -> Unit,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val config = LocalConfiguration.current
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(DrawerValue.Closed)

    val isWideScreen = config.screenWidthDp >= 600
    val useRail = settings.navLayout == NavLayoutMode.ADAPTIVE && isWideScreen
    val useBottomBar = settings.navLayout == NavLayoutMode.BOTTOM ||
        (settings.navLayout == NavLayoutMode.ADAPTIVE && !isWideScreen)

    val mvpEntries = remember { NAV_ENTRIES.filter { it.isMvpTab } }
    val activeCount = activeJobs.count {
        it.status == JobStatus.QUEUED || it.status == JobStatus.PROCESSING || it.status == JobStatus.PAUSED
    }

    val authLabel = when (userStats.authMode) {
        AuthMode.USER_KEY -> "${userStats.kudos.toInt()} kudos"
        AuthMode.SHARED_KEY -> "Shared key · ${userStats.sharedKeyRemainingKudos ?: 0} kudos"
        AuthMode.ANONYMOUS -> "Anonymous"
    }
    val cleanHost = settings.apiBaseUrl
        .removePrefix("https://")
        .removePrefix("http://")
        .removeSuffix("/")
        .removeSuffix("/api/v2")

    val innerContent: @Composable () -> Unit = {
        Scaffold(
            containerColor = HordeColors.BgCanvas,
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = HordeColors.BarSurface,
                        titleContentColor = HordeColors.TextPrimary,
                    ),
                    navigationIcon = {
                        if (settings.navLayout == NavLayoutMode.MENU) {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Outlined.Menu, contentDescription = "Menu", tint = HordeColors.TextSecondary)
                            }
                        }
                    },
                    title = {
                        Column {
                            Text(
                                text = currentDestination.label,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = "$authLabel · $cleanHost",
                                fontSize = 11.sp,
                                color = HordeColors.TextMuted,
                            )
                        }
                    },
                    actions = {
                        if (activeCount > 0) {
                            Text(
                                text = if (queuePaused) "Paused ($activeCount)" else "$activeCount active",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = HordeColors.TealSoft,
                                modifier = Modifier.padding(end = 8.dp),
                            )
                        }
                        if (settings.navLayout != NavLayoutMode.BOTTOM) {
                            IconButton(
                                onClick = { launchActivityForDestination(context, ActivityDestination.SETTINGS) },
                            ) {
                                Icon(
                                    Icons.Outlined.Settings,
                                    contentDescription = "Settings",
                                    tint = HordeColors.TextSecondary,
                                )
                            }
                        }
                    },
                )
            },
            bottomBar = {
                if (useBottomBar) {
                    NavigationBar(containerColor = HordeColors.BarSurface) {
                        mvpEntries.forEach { entry ->
                            val selected = entry.dest == currentDestination
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    if (!selected) launchActivityForDestination(context, entry.dest)
                                },
                                icon = {
                                    Icon(entry.icon, contentDescription = entry.dest.label)
                                },
                                label = { Text(entry.dest.label, fontSize = 11.sp) },
                            )
                        }
                    }
                }
            },
        ) { paddingValues ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
            ) {
                if (useRail) {
                    NavigationRail(
                        containerColor = androidx.compose.ui.graphics.Color(0xFF131821),
                        modifier = Modifier.fillMaxHeight(),
                    ) {
                        Spacer(Modifier.height(8.dp))
                        mvpEntries.forEach { entry ->
                            val selected = entry.dest == currentDestination
                            NavigationRailItem(
                                selected = selected,
                                onClick = {
                                    if (!selected) launchActivityForDestination(context, entry.dest)
                                },
                                icon = { Icon(entry.icon, contentDescription = entry.dest.label) },
                                label = { Text(entry.dest.label, fontSize = 10.sp) },
                            )
                        }
                    }
                }
                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    content()
                }
            }
        }
    }

    if (settings.navLayout == NavLayoutMode.MENU) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet(drawerContainerColor = HordeColors.BarSurface) {
                    Text(
                        text = "Hordecraft",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = HordeColors.TextPrimary,
                        modifier = Modifier.padding(16.dp),
                    )
                    HorizontalDivider(color = HordeColors.BorderSubtle)
                    NAV_ENTRIES.forEach { entry ->
                        NavigationDrawerItem(
                            selected = entry.dest == currentDestination,
                            onClick = {
                                scope.launch { drawerState.close() }
                                if (entry.dest != currentDestination) {
                                    launchActivityForDestination(context, entry.dest)
                                }
                            },
                            icon = { Icon(entry.icon, contentDescription = entry.dest.label) },
                            label = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(entry.dest.label)
                                    if (!entry.isMvpTab) {
                                        Text("Soon", fontSize = 11.sp, color = HordeColors.TextMuted)
                                    }
                                }
                            },
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }
            },
        ) {
            innerContent()
        }
    } else {
        innerContent()
    }

    if (!settings.onboardingCompleted) {
        FirstRunOnboardingDialog(onComplete = onDismissOnboarding)
    }
}

@Composable
private fun FirstRunOnboardingDialog(onComplete: (String) -> Unit) {
    var keyDraft by remember { mutableStateOf("") }
    val mode = AuthMode.inferFromKey(keyDraft)

    AlertDialog(
        onDismissRequest = { onComplete(HordeConstants.ANONYMOUS_KEY) },
        containerColor = HordeColors.CardSurface,
        title = { Text("Welcome to Hordecraft", color = HordeColors.TextPrimary, fontSize = 16.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(HordeColors.SubCardSurface)
                        .border(1.dp, HordeColors.BorderSubtle, RoundedCornerShape(10.dp))
                        .padding(10.dp),
                ) {
                    Text(
                        text = "All code in Hordecraft is 100% vibeslopped by AI (${BuildConfig.AI_ATTRIBUTION_MODEL}).",
                        fontSize = 12.sp,
                        color = HordeColors.TextSecondary,
                    )
                }

                OutlinedTextField(
                    value = keyDraft,
                    onValueChange = { keyDraft = it },
                    singleLine = true,
                    label = { Text("AI Horde API Key") },
                    placeholder = { Text("Paste key or leave blank") },
                    modifier = Modifier.fillMaxWidth(),
                )

                Text(
                    text = when {
                        keyDraft.isBlank() -> "Optional. Leave blank to start anonymously."
                        mode == AuthMode.SHARED_KEY -> "Shared key detected."
                        else -> "Personal API key."
                    },
                    fontSize = 11.sp,
                    color = HordeColors.TextMuted,
                )
            }
        },
        dismissButton = {
            OutlinedButton(onClick = { onComplete(HordeConstants.ANONYMOUS_KEY) }) {
                Text("Skip")
            }
        },
        confirmButton = {
            Button(
                colors = ButtonDefaults.buttonColors(containerColor = HordeColors.TealPrimary),
                onClick = { onComplete(keyDraft.trim().ifBlank { HordeConstants.ANONYMOUS_KEY }) },
            ) {
                Text(
                    text = if (keyDraft.isNotBlank()) "Save Key" else "Continue",
                    color = androidx.compose.ui.graphics.Color(0xFF020617),
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
    )
}
