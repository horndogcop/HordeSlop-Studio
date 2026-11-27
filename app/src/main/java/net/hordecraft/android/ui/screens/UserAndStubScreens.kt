package net.hordeslop.studio.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import net.hordeslop.studio.domain.ActivityDestination
import net.hordeslop.studio.domain.AuthMode
import net.hordeslop.studio.domain.HordeJob
import net.hordeslop.studio.domain.HordePerformanceSummary
import net.hordeslop.studio.domain.JobStatus
import net.hordeslop.studio.domain.UserStatsSummary
import net.hordeslop.studio.ui.components.launchActivityForDestination
import net.hordeslop.studio.ui.theme.HordeColors

@Composable
fun MainHubScreen(
    userStats: UserStatsSummary,
    activeJobs: List<HordeJob>,
    savedImageCount: Int,
    apiBaseUrl: String,
    onOpenDestination: (ActivityDestination) -> Unit,
) {
    val isWide = LocalConfiguration.current.screenWidthDp >= 600
    val pendingJobs = activeJobs.count {
        it.status == JobStatus.LOCAL_QUEUED ||
            it.status == JobStatus.QUEUED ||
            it.status == JobStatus.PROCESSING ||
            it.status == JobStatus.PAUSED
    }
    val authSubtitle = when (userStats.authMode) {
        AuthMode.USER_KEY -> "${userStats.username ?: "Signed in"} · ${userStats.kudos.toInt()} kudos"
        AuthMode.SHARED_KEY -> "Shared key · ${userStats.sharedKeyRemainingKudos ?: 0} kudos"
        AuthMode.ANONYMOUS -> "Anonymous · Tap to inspect Horde stats"
    }
    val primaryCards = listOf(
        Triple(
            ActivityDestination.IMAGE_GEN,
            Icons.Outlined.Image,
            if (pendingJobs > 0) "$pendingJobs active in queue" else "Generate Images",
        ),
        Triple(
            ActivityDestination.GALLERY,
            Icons.Outlined.Collections,
            "$savedImageCount saved WebP image(s)",
        ),
        Triple(
            ActivityDestination.USER,
            Icons.Outlined.Public,
            authSubtitle,
        ),
        Triple(
            ActivityDestination.SETTINGS,
            Icons.Outlined.Settings,
            apiBaseUrl.removePrefix("https://").removeSuffix("/"),
        ),
    )
    val futureCards = listOf(
        Triple(ActivityDestination.MODELS_STUB, Icons.Outlined.Memory, "Model & style browser (Coming later)"),
        Triple(ActivityDestination.TEXT_GEN_STUB, Icons.Outlined.Description, "Text & chat generation (Coming later)"),
        Triple(ActivityDestination.ALCHEMIST_STUB, Icons.Outlined.AutoAwesome, "Interrogation & post-processing (Coming later)"),
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        StudioCard {
            Text("HordeSlop Studio", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = HordeColors.TextPrimary)
            Text(
                "Choose a workspace to open. Each screen can run in its own window or inside Home.",
                fontSize = 12.sp,
                color = HordeColors.TextMuted,
            )
            if (isWide) {
                primaryCards.chunked(2).forEach { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        rowItems.forEach { (dest, icon, subtitle) ->
                            Box(modifier = Modifier.weight(1f)) {
                                HubDestinationButton(
                                    title = dest.label,
                                    subtitle = subtitle,
                                    icon = icon,
                                    accent = dest == ActivityDestination.IMAGE_GEN,
                                    onClick = { onOpenDestination(dest) },
                                )
                            }
                        }
                        if (rowItems.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            } else {
                primaryCards.forEach { (dest, icon, subtitle) ->
                    HubDestinationButton(
                        title = dest.label,
                        subtitle = subtitle,
                        icon = icon,
                        accent = dest == ActivityDestination.IMAGE_GEN,
                        onClick = { onOpenDestination(dest) },
                    )
                }
            }
        }

        StudioCard {
            Text("More Tools", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = HordeColors.TextPrimary)
            futureCards.forEach { (dest, icon, subtitle) ->
                HubDestinationButton(
                    title = dest.label,
                    subtitle = subtitle,
                    icon = icon,
                    accent = false,
                    onClick = { onOpenDestination(dest) },
                )
            }
        }
    }
}

@Composable
private fun HubDestinationButton(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accent: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        color = if (accent) HordeColors.TealContainer else HordeColors.SubCardSurface,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                if (accent) HordeColors.TealPrimary else HordeColors.BorderSubtle,
                RoundedCornerShape(12.dp),
            )
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = if (accent) HordeColors.TealSoft else HordeColors.TextSecondary,
                modifier = Modifier.size(24.dp),
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = HordeColors.TextPrimary)
                Text(subtitle, fontSize = 11.sp, color = HordeColors.TextMuted)
            }
            Icon(Icons.Outlined.ChevronRight, null, tint = HordeColors.TextMuted, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
fun UserScreen(
    stats: UserStatsSummary,
    performance: HordePerformanceSummary?,
    onRefreshStats: () -> Unit,
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StudioCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.Dns, null, tint = HordeColors.TealSoft)
                    Text("Horde Status", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = HordeColors.TextPrimary)
                }
                IconButton(onClick = onRefreshStats, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Outlined.Refresh, "Refresh", tint = HordeColors.TextSecondary, modifier = Modifier.size(16.dp))
                }
            }

            if (performance == null) {
                Text(
                    text = "Loading Horde performance stats...",
                    fontSize = 12.sp,
                    color = HordeColors.TextMuted,
                )
            } else {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile(
                        "Image Workers",
                        "${performance.workerCount} (${performance.threadCount} threads)",
                        Modifier.weight(1f),
                    )
                    StatTile(
                        "Queued Image",
                        "${performance.queuedRequests} reqs",
                        Modifier.weight(1f),
                    )
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile(
                        "Queued Work",
                        "${String.format(Locale.getDefault(), "%,.0f", performance.queuedMegapixelsteps)} MPsteps",
                        Modifier.weight(1f),
                    )
                    StatTile(
                        "Speed (1m)",
                        "${String.format(Locale.getDefault(), "%,.0f", performance.pastMinuteMegapixelsteps)} MPsteps",
                        Modifier.weight(1f),
                    )
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile(
                        "Text Workers",
                        "${performance.textWorkerCount} (${performance.textThreadCount} threads)",
                        Modifier.weight(1f),
                    )
                    StatTile(
                        "Text Queue",
                        "${performance.queuedTextRequests} reqs · ${String.format(Locale.getDefault(), "%,.0f", performance.pastMinuteTokens)} tok/m",
                        Modifier.weight(1f),
                    )
                }
            }
        }

        StudioCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.Person, null, tint = HordeColors.TealSoft)
                    Text("Account", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = HordeColors.TextPrimary)
                }
                Text(
                    text = "API Key in Settings",
                    fontSize = 12.sp,
                    color = HordeColors.TealSoft,
                    modifier = Modifier.clickable {
                        launchActivityForDestination(context, ActivityDestination.SETTINGS)
                    },
                )
            }

            when (stats.authMode) {
                AuthMode.USER_KEY -> {
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
                            ) {
                                Text(stats.username ?: "AI Horde User", fontWeight = FontWeight.SemiBold, color = HordeColors.TextPrimary)
                                if (stats.trusted) {
                                    Text("Trusted", fontSize = 11.sp, color = HordeColors.TealSoft)
                                }
                            }
                            Text(
                                "${String.format(Locale.getDefault(), "%,.0f", stats.kudos)} Kudos",
                                fontSize = 15.sp,
                                fontFamily = FontFamily.Monospace,
                                color = HordeColors.TealSoft,
                            )
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatTile("Requests", "${stats.recordsRequests}", Modifier.weight(1f))
                        StatTile("Megapixels", "${String.format(Locale.getDefault(), "%.1f", stats.recordsMegapixels)} MP", Modifier.weight(1f))
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatTile("Workers", "${stats.workerCount}", Modifier.weight(1f))
                        StatTile("Contributed MP", String.format(Locale.getDefault(), "%.0f", stats.kudosEarnedWorkers), Modifier.weight(1f))
                    }
                }
                AuthMode.SHARED_KEY -> {
                    Surface(
                        color = HordeColors.SubCardSurface,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, HordeColors.BorderSubtle, RoundedCornerShape(10.dp)),
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Shared Key: ${stats.sharedKeyName ?: "Active"}", fontWeight = FontWeight.Medium, color = HordeColors.TextPrimary)
                            Text(
                                "${stats.sharedKeyRemainingKudos ?: 0} Kudos left",
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Monospace,
                                color = HordeColors.AmberText,
                            )
                            Text(
                                "Max steps: ${stats.sharedKeyMaxSteps ?: "-"} · Expiry: ${stats.sharedKeyExpiry ?: "None"}",
                                fontSize = 11.sp,
                                color = HordeColors.TextMuted,
                            )
                        }
                    }
                }
                AuthMode.ANONYMOUS -> {
                    Surface(
                        color = HordeColors.SubCardSurface,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, HordeColors.BorderSubtle, RoundedCornerShape(10.dp)),
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Anonymous", fontWeight = FontWeight.Medium, color = HordeColors.TextPrimary)
                            Text(
                                "Add an API key in Settings to track kudos, workers, and higher priority.",
                                fontSize = 12.sp,
                                color = HordeColors.TextMuted,
                            )
                        }
                    }
                }
            }
        }

        StudioCard {
            Text("Coming Later", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = HordeColors.TextPrimary)
            Text(
                "Shared keys, kudos transfers, worker controls, and teams will appear here later.",
                fontSize = 12.sp,
                color = HordeColors.TextMuted,
            )
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        color = HordeColors.SubCardSurface,
        shape = RoundedCornerShape(10.dp),
        modifier = modifier.border(1.dp, HordeColors.BorderSubtle, RoundedCornerShape(10.dp)),
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(label, fontSize = 11.sp, color = HordeColors.TextMuted)
            Text(value, fontSize = 13.sp, fontFamily = FontFamily.Monospace, color = HordeColors.TextPrimary)
        }
    }
}

@Composable
fun FutureStubScreen(
    title: String,
    description: String,
) {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Outlined.Schedule, null, tint = HordeColors.TealSoft, modifier = Modifier.size(36.dp))
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = HordeColors.TextPrimary)
            Text("Coming later", fontSize = 12.sp, color = HordeColors.AmberText)
            Text(description, fontSize = 13.sp, color = HordeColors.TextMuted, textAlign = TextAlign.Center)
            Spacer(Modifier.size(4.dp))
            Button(
                colors = ButtonDefaults.buttonColors(containerColor = HordeColors.TealPrimary),
                onClick = { launchActivityForDestination(context, ActivityDestination.IMAGE_GEN) },
            ) {
                Text("Back to Image", color = Color(0xFF020617), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
