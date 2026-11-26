package net.hordeslop.studio.ui.activities

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.hordeslop.studio.domain.ActivityDestination
import net.hordeslop.studio.ui.MainViewModel
import net.hordeslop.studio.ui.components.EXTRA_SINGLE_ACTIVITY_DEST
import net.hordeslop.studio.ui.components.HordeModeScaffold
import net.hordeslop.studio.ui.components.buildIntentForDestination
import net.hordeslop.studio.ui.components.launchActivityForDestination
import net.hordeslop.studio.ui.screens.FutureStubScreen
import net.hordeslop.studio.ui.screens.GalleryScreen
import net.hordeslop.studio.ui.screens.MainHubScreen
import net.hordeslop.studio.ui.screens.SettingsScreen
import net.hordeslop.studio.ui.screens.StudioScreen
import net.hordeslop.studio.ui.screens.UserScreen
import net.hordeslop.studio.ui.theme.HordecraftTheme

abstract class BaseHordeActivity : ComponentActivity() {
    protected abstract val destination: ActivityDestination
    protected val vm: MainViewModel by viewModels()

    protected fun applySingleActivityIntent(incoming: Intent?) {
        if (this !is MainActivity) return
        val routeKey = incoming?.getStringExtra(EXTRA_SINGLE_ACTIVITY_DEST) ?: return
        val target = ActivityDestination.entries.firstOrNull { it.routeKey == routeKey } ?: return
        vm.setSingleActivityDestination(target)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applySingleActivityIntent(intent)
        setContent {
            val settings by vm.settings.collectAsStateWithLifecycle()
            val studio by vm.studio.collectAsStateWithLifecycle()
            val history by vm.history.collectAsStateWithLifecycle()
            val savedImages by vm.savedImages.collectAsStateWithLifecycle()
            val activeJobs by vm.activeJobs.collectAsStateWithLifecycle()
            val pendingGalleryQuery by vm.pendingGallerySearchQuery.collectAsStateWithLifecycle()
            val singleActivityDest by vm.singleActivityDestination.collectAsStateWithLifecycle()

            LaunchedEffect(settings.useSeparateActivities) {
                if (this@BaseHordeActivity !is MainActivity && !settings.useSeparateActivities) {
                    vm.setSingleActivityDestination(destination)
                    startActivity(
                        Intent(this@BaseHordeActivity, MainActivity::class.java).apply {
                            putExtra(EXTRA_SINGLE_ACTIVITY_DEST, destination.routeKey)
                            addFlags(
                                Intent.FLAG_ACTIVITY_NEW_TASK or
                                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                    Intent.FLAG_ACTIVITY_SINGLE_TOP,
                            )
                        },
                    )
                    finishAndRemoveTask()
                } else if (
                    this@BaseHordeActivity is MainActivity &&
                    settings.useSeparateActivities &&
                    singleActivityDest != ActivityDestination.MAIN
                ) {
                    val target = singleActivityDest
                    vm.setSingleActivityDestination(ActivityDestination.MAIN)
                    startActivity(buildIntentForDestination(this@BaseHordeActivity, target))
                }
            }

            val activeDestination = if (this@BaseHordeActivity is MainActivity && !settings.useSeparateActivities) {
                singleActivityDest
            } else {
                destination
            }

            BackHandler(
                enabled = this@BaseHordeActivity is MainActivity &&
                    !settings.useSeparateActivities &&
                    activeDestination != ActivityDestination.MAIN,
            ) {
                vm.setSingleActivityDestination(ActivityDestination.MAIN)
            }

            HordecraftTheme {
                HordeModeScaffold(
                    currentDestination = activeDestination,
                    settings = settings,
                    userStats = studio.userStats,
                    activeJobs = activeJobs,
                    queuePaused = studio.queuePaused,
                    onDismissOnboarding = vm::completeOnboarding,
                ) {
                    when (activeDestination) {
                        ActivityDestination.MAIN -> MainHubScreen(
                            userStats = studio.userStats,
                            activeJobs = activeJobs,
                            savedImageCount = savedImages.size,
                            apiBaseUrl = settings.apiBaseUrl,
                            onOpenDestination = { dest ->
                                launchActivityForDestination(this@BaseHordeActivity, dest)
                            },
                        )

                        ActivityDestination.IMAGE_GEN -> StudioScreen(
                            vm = vm,
                            studio = studio,
                            settings = settings,
                            history = history,
                            jobs = activeJobs,
                        )
                        ActivityDestination.GALLERY -> GalleryScreen(
                            vm = vm,
                            images = savedImages,
                            settings = settings,
                            toastMessage = studio.toastMessage,
                            pendingSearchQuery = pendingGalleryQuery,
                        )
                        ActivityDestination.USER -> UserScreen(
                            stats = studio.userStats,
                            performance = studio.hordePerformance,
                            onRefreshStats = vm::refreshUserStats,
                        )
                        ActivityDestination.SETTINGS -> SettingsScreen(
                            vm = vm,
                            settings = settings,
                            toastMessage = studio.toastMessage,
                        )
                        ActivityDestination.MODELS_STUB -> FutureStubScreen(
                            title = "Models",
                            description = "Browse models and styles later.",
                        )
                        ActivityDestination.TEXT_GEN_STUB -> FutureStubScreen(
                            title = "Text",
                            description = "Text and chat generation coming later.",
                        )
                        ActivityDestination.ALCHEMIST_STUB -> FutureStubScreen(
                            title = "Alchemist",
                            description = "Image interrogation and post-processing coming later.",
                        )
                    }
                }
            }
        }
    }
}

class MainActivity : BaseHordeActivity() {
    override val destination = ActivityDestination.MAIN

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applySingleActivityIntent(intent)
    }
}

class ImageGenActivity : BaseHordeActivity() {
    override val destination = ActivityDestination.IMAGE_GEN
}

class GalleryActivity : BaseHordeActivity() {
    override val destination = ActivityDestination.GALLERY
}

class UserActivity : BaseHordeActivity() {
    override val destination = ActivityDestination.USER
}

class SettingsActivity : BaseHordeActivity() {
    override val destination = ActivityDestination.SETTINGS
}

class ModelsActivity : BaseHordeActivity() {
    override val destination = ActivityDestination.MODELS_STUB
}

class TextGenActivity : BaseHordeActivity() {
    override val destination = ActivityDestination.TEXT_GEN_STUB
}

class AlchemistActivity : BaseHordeActivity() {
    override val destination = ActivityDestination.ALCHEMIST_STUB
}
