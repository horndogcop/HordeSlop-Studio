package net.hordeslop.studio

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import net.hordeslop.studio.data.local.AppSettings
import net.hordeslop.studio.data.local.HordecraftDatabase
import net.hordeslop.studio.data.local.SettingsDataStore
import net.hordeslop.studio.data.local.WebpStorageManager
import net.hordeslop.studio.data.remote.CivitaiApiService
import net.hordeslop.studio.data.remote.HordeApiService
import net.hordeslop.studio.data.remote.HordeRateLimiterInterceptor
import net.hordeslop.studio.data.remote.ModelReferenceRepository
import net.hordeslop.studio.data.repository.HordecraftRepository
import net.hordeslop.studio.domain.ActivityDestination
import net.hordeslop.studio.domain.HordeConstants
import net.hordeslop.studio.service.NotificationChannels
import net.hordeslop.studio.ui.StudioUiState
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class AppContainer(context: Application) {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    val settingsStore = SettingsDataStore(context)
    val database = HordecraftDatabase.build(context)
    val storageManager = WebpStorageManager(context)

    private val currentBaseUrl = AtomicReference(HordeConstants.DEFAULT_API_BASE_URL)
    private val currentApiKey = AtomicReference(HordeConstants.ANONYMOUS_KEY)
    val currentSettings = AtomicReference(AppSettings())

    init {
        appScope.launch {
            settingsStore.settings.collect { s ->
                currentSettings.set(s)
                currentBaseUrl.set(s.apiBaseUrl)
                currentApiKey.set(s.hordeApiKey)
            }
        }
    }

    private val hordeOkHttp = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .addInterceptor(
            HordeRateLimiterInterceptor(
                baseUrlProvider = { currentBaseUrl.get() },
                apiKeyProvider = { currentApiKey.get() },
            ),
        )
        .build()

    private val rawOkHttp = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json".toMediaType()

    private val hordeRetrofit = Retrofit.Builder()
        .baseUrl(HordeConstants.DEFAULT_API_BASE_URL)
        .client(hordeOkHttp)
        .addConverterFactory(json.asConverterFactory(jsonMediaType))
        .build()

    private val civitaiRetrofit = Retrofit.Builder()
        .baseUrl("https://civitai.com/")
        .client(rawOkHttp)
        .addConverterFactory(json.asConverterFactory(jsonMediaType))
        .build()

    val hordeApi: HordeApiService = hordeRetrofit.create(HordeApiService::class.java)
    val civitaiApi: CivitaiApiService = civitaiRetrofit.create(CivitaiApiService::class.java)
    val modelReferenceRepo = ModelReferenceRepository(rawOkHttp, json)
    val sharedStudioState = MutableStateFlow(StudioUiState())
    val pendingGallerySearchQuery = MutableStateFlow<String?>(null)
    val singleActivityDestination = MutableStateFlow(ActivityDestination.MAIN)
    @Volatile
    var catalogsLoadedOnce: Boolean = false

    val repository = HordecraftRepository(
        context = context,
        database = database,
        settingsStore = settingsStore,
        promptHistoryDao = database.promptHistoryDao(),
        modelDefaultsDao = database.modelDefaultsDao(),
        savedImageDao = database.savedImageDao(),
        activeJobDao = database.activeJobDao(),
        favoriteLoraDao = database.favoriteLoraDao(),
        favoriteTiDao = database.favoriteTiDao(),
        hordeApi = hordeApi,
        civitaiApi = civitaiApi,
        modelRefRepo = modelReferenceRepo,
        storageManager = storageManager,
        downloadOkHttpClient = rawOkHttp,
        json = json,
    )
}

class HordecraftApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        NotificationChannels.createAll(this)
        container = AppContainer(this)
    }
}
