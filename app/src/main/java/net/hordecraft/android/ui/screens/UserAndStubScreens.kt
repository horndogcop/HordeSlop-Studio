package net.hordeslop.studio.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlinx.coroutines.delay
import net.hordeslop.studio.data.local.AppSettings
import net.hordeslop.studio.domain.ActivityDestination
import net.hordeslop.studio.domain.AuthMode
import net.hordeslop.studio.domain.HordeJob
import net.hordeslop.studio.domain.LiveGenerationParser
import net.hordeslop.studio.domain.LiveRemoteGeneration
import net.hordeslop.studio.domain.UserStatsSummary
import net.hordeslop.studio.ui.MainViewModel
import net.hordeslop.studio.ui.StudioUiState
import net.hordeslop.studio.ui.components.AiHordeWebViewPane
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
    val pendingJobs = activeJobs.count { it.status.isActiveInQueue }
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UserScreen(
    vm: MainViewModel,
    studio: StudioUiState,
    settings: AppSettings,
) {
    val context = LocalContext.current
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var inspectedItem by remember { mutableStateOf<LiveRemoteGeneration?>(null) }

    val stats = studio.userStats
    val performance = studio.hordePerformance
    val authMode = AuthMode.inferFromKey(settings.hordeApiKey)

    LaunchedEffect(settings.hordeApiKey) {
        vm.refreshLiveRemoteGenerations()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = HordeColors.BarSurface,
            contentColor = HordeColors.TealSoft,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                    color = HordeColors.TealPrimary,
                )
            },
        ) {
            listOf("Horde & Live", "aihorde.net").forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = {
                        Text(
                            text = title,
                            fontSize = 13.sp,
                            fontWeight = if (selectedTab == index) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (selectedTab == index) HordeColors.TealSoft else HordeColors.TextMuted,
                        )
                    },
                )
            }
        }

        studio.toastMessage?.let { msg ->
            LaunchedEffect(msg) {
                delay(2_800L)
                vm.clearToast()
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(HordeColors.TealContainer)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(msg, fontSize = 12.sp, color = HordeColors.TealSoft)
            }
        }

        if (selectedTab == 0) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
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
                        IconButton(onClick = vm::refreshUserStats, modifier = Modifier.size(30.dp)) {
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(Icons.Outlined.CloudQueue, null, tint = HordeColors.TealSoft)
                            Text(
                                "Live Client Generations",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = HordeColors.TextPrimary,
                            )
                        }
                        IconButton(
                            onClick = vm::refreshLiveRemoteGenerations,
                            enabled = !studio.liveRemoteLoading,
                            modifier = Modifier.size(30.dp),
                        ) {
                            Icon(
                                Icons.Outlined.Refresh,
                                "Refresh live generations",
                                tint = HordeColors.TextSecondary,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }

                    Text(
                        "Inspect ongoing image requests submitted under your personal API key across any AI Horde client.",
                        fontSize = 12.sp,
                        color = HordeColors.TextMuted,
                    )

                    if (studio.liveRemoteLoading) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = HordeColors.TealPrimary,
                            trackColor = HordeColors.SubCardSurface,
                        )
                    }

                    when {
                        authMode != AuthMode.USER_KEY -> {
                            Surface(
                                color = HordeColors.SubCardSurface,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, HordeColors.BorderSubtle, RoundedCornerShape(10.dp)),
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(
                                        "A personal AI Horde API key is required to list active generations across clients. Shared and anonymous keys are not exposed by /v2/find_user.",
                                        fontSize = 12.sp,
                                        color = HordeColors.TextMuted,
                                    )
                                    OutlinedButton(
                                        onClick = { launchActivityForDestination(context, ActivityDestination.SETTINGS) },
                                        shape = RoundedCornerShape(8.dp),
                                    ) {
                                        Text("Configure API Key", fontSize = 12.sp)
                                    }
                                }
                            }
                        }

                        studio.liveRemoteError != null -> {
                            Surface(
                                color = HordeColors.SubCardSurface,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, HordeColors.RoseZone.copy(alpha = 0.5f), RoundedCornerShape(10.dp)),
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Text(
                                        studio.liveRemoteError,
                                        fontSize = 12.sp,
                                        color = HordeColors.RoseText,
                                    )
                                    OutlinedButton(
                                        onClick = vm::refreshLiveRemoteGenerations,
                                        shape = RoundedCornerShape(8.dp),
                                    ) {
                                        Text("Retry", fontSize = 12.sp)
                                    }
                                }
                            }
                        }

                        !studio.liveRemoteLoading && studio.liveRemoteGenerations.isEmpty() -> {
                            Text(
                                "No active image requests found on Horde for this API key. Note that requests created via shared keys or styled endpoints may not appear.",
                                fontSize = 12.sp,
                                color = HordeColors.TextMuted,
                            )
                        }

                        else -> {
                            studio.liveRemoteGenerations.forEach { item ->
                                LiveRemoteGenerationCard(
                                    item = item,
                                    onInspect = { inspectedItem = item },
                                    onApply = {
                                        vm.applyLiveGenerationParameters(item)
                                    },
                                    onTrack = { vm.adoptRemoteGeneration(item) },
                                )
                            }
                        }
                    }

                    if (studio.liveRemoteTextIds.isNotEmpty() || studio.liveRemoteAlchemyIds.isNotEmpty()) {
                        Text(
                            "Other active requests: ${studio.liveRemoteTextIds.size} text · ${studio.liveRemoteAlchemyIds.size} alchemy",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = HordeColors.TextMuted,
                        )
                    }
                }
            }
        } else {
            AiHordeWebViewPane(
                onApplyParsedMetadata = { meta, msg ->
                    vm.applyParsedImageMetadata(meta, toastOverride = msg)
                    launchActivityForDestination(context, ActivityDestination.IMAGE_GEN)
                },
                onShowMessage = vm::showToast,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            )
        }
    }

    inspectedItem?.let { item ->
        LiveGenerationInspectDialog(
            item = item,
            onDismiss = { inspectedItem = null },
            onCopyJson = { vm.copyText("request JSON", item.rawRequestJson) },
            onCopyCurl = { vm.copyJsonRequestAsCurl(item.rawRequestJson) },
            onApply = {
                vm.applyLiveGenerationParameters(item)
                inspectedItem = null
            },
            onTrack = {
                vm.adoptRemoteGeneration(item)
                inspectedItem = item.copy(isTrackedLocally = true)
            },
            onCancelRequest = {
                vm.cancelRemoteGeneration(item.requestId)
                inspectedItem = null
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LiveRemoteGenerationCard(
    item: LiveRemoteGeneration,
    onInspect: () -> Unit,
    onApply: () -> Unit,
    onTrack: () -> Unit,
) {
    Surface(
        color = HordeColors.SubCardSurface,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, HordeColors.BorderSubtle, RoundedCornerShape(10.dp)),
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = item.requestId.take(12),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    color = HordeColors.TealSoft,
                )
                Text(
                    text = LiveGenerationParser.summarizeStatus(item),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = when {
                        item.faulted -> HordeColors.RoseText
                        !item.isPossible -> HordeColors.AmberText
                        item.done -> HordeColors.TealSoft
                        else -> HordeColors.TextSecondary
                    },
                )
            }

            Text(
                text = item.models.joinToString(", ").ifBlank { "Model unavailable" },
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = HordeColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            when {
                item.prompt.isNotBlank() -> Text(
                    text = item.prompt,
                    fontSize = 12.sp,
                    color = HordeColors.TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                item.promptOmittedLegacy -> Text(
                    text = "Prompt omitted by API (legacy request)",
                    fontSize = 11.sp,
                    fontStyle = FontStyle.Italic,
                    color = HordeColors.TextMuted,
                )
                item.requestLookupError != null -> Text(
                    text = item.requestLookupError,
                    fontSize = 11.sp,
                    color = HordeColors.AmberText,
                )
            }

            Text(
                text = LiveGenerationParser.summarizeParams(item),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = HordeColors.TextMuted,
            )

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                OutlinedButton(
                    onClick = onInspect,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text("Inspect", fontSize = 11.sp)
                }
                OutlinedButton(
                    onClick = onApply,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text("Apply to Studio", fontSize = 11.sp)
                }
                if (item.isTrackedLocally) {
                    TextButton(
                        onClick = {},
                        enabled = false,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Text("Tracked in Queue", fontSize = 11.sp, color = HordeColors.TealSoft)
                    }
                } else {
                    Button(
                        onClick = onTrack,
                        colors = ButtonDefaults.buttonColors(containerColor = HordeColors.TealPrimary),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        Text(
                            "Track in Queue",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF020617),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LiveGenerationInspectDialog(
    item: LiveRemoteGeneration,
    onDismiss: () -> Unit,
    onCopyJson: () -> Unit,
    onCopyCurl: () -> Unit,
    onApply: () -> Unit,
    onTrack: () -> Unit,
    onCancelRequest: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = HordeColors.CardSurface,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Live Request Details",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = HordeColors.TextPrimary,
                )
                Text(
                    text = item.requestId,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = HordeColors.TealSoft,
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (item.requestLookupError != null) {
                    Text(
                        text = "Request detail note: ${item.requestLookupError}",
                        fontSize = 11.sp,
                        color = HordeColors.AmberText,
                    )
                }

                DetailRow("Status", LiveGenerationParser.summarizeStatus(item))
                DetailRow("Models", item.models.joinToString(", ").ifBlank { "Unknown" })
                DetailRow(
                    "Prompt",
                    when {
                        item.prompt.isNotBlank() -> item.prompt
                        item.promptOmittedLegacy -> "(Omitted on legacy request)"
                        else -> "(Empty)"
                    },
                )
                if (item.negativePrompt.isNotBlank()) {
                    DetailRow("Negative Prompt", item.negativePrompt)
                }
                DetailRow(
                    "Sampling",
                    listOfNotNull(
                        item.width?.let { w -> item.height?.let { h -> "${w}x$h" } },
                        item.steps?.let { "$it steps" },
                        item.cfg?.let { "CFG $it" },
                        item.clipSkip?.let { "Clip Skip $it" },
                        item.sampler,
                        item.scheduler,
                        item.seed?.let { "Seed $it" },
                        "Count ${item.imageCount}",
                        item.hiresFix?.let { "Hires Fix: $it" },
                    ).joinToString(" · "),
                )
                val flags = listOfNotNull(
                    item.nsfw?.let { "nsfw=$it" },
                    item.censorNsfw?.let { "censor_nsfw=$it" },
                    item.shared?.let { "shared=$it" },
                    item.r2?.let { "r2=$it" },
                    item.trustedWorkers?.let { "trusted=$it" },
                    item.slowWorkers?.let { "slow=$it" },
                    item.sourceProcessing?.let { "source=$it" },
                ).joinToString(", ")
                if (flags.isNotBlank()) {
                    DetailRow("Flags", flags)
                }
                if (item.loraNames.isNotEmpty()) {
                    DetailRow("LoRAs", item.loraNames.joinToString(", "))
                }
                if (item.tiNames.isNotEmpty()) {
                    DetailRow("TIs", item.tiNames.joinToString(", "))
                }

                Text(
                    "Raw Request Payload",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = HordeColors.TextSecondary,
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(HordeColors.SubCardSurface, RoundedCornerShape(8.dp))
                        .border(1.dp, HordeColors.BorderSubtle, RoundedCornerShape(8.dp))
                        .horizontalScroll(rememberScrollState())
                        .padding(8.dp),
                ) {
                    Text(
                        text = item.rawRequestJson,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = HordeColors.TextSecondary,
                    )
                }

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    OutlinedButton(onClick = onCopyJson, shape = RoundedCornerShape(8.dp)) {
                        Text("Copy JSON", fontSize = 11.sp)
                    }
                    OutlinedButton(onClick = onCopyCurl, shape = RoundedCornerShape(8.dp)) {
                        Text("Copy cURL", fontSize = 11.sp)
                    }
                    OutlinedButton(onClick = onApply, shape = RoundedCornerShape(8.dp)) {
                        Text("Apply to Studio", fontSize = 11.sp)
                    }
                    if (!item.isTrackedLocally) {
                        Button(
                            onClick = onTrack,
                            colors = ButtonDefaults.buttonColors(containerColor = HordeColors.TealPrimary),
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Text("Track in Queue", fontSize = 11.sp, color = Color(0xFF020617), fontWeight = FontWeight.SemiBold)
                        }
                    }
                    if (!item.done && !item.faulted) {
                        TextButton(onClick = onCancelRequest) {
                            Text("Cancel Request", fontSize = 11.sp, color = HordeColors.RoseText)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = HordeColors.TealSoft)
            }
        },
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = HordeColors.TextMuted)
        Text(value, fontSize = 12.sp, color = HordeColors.TextPrimary)
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
