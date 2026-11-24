package net.hordecraft.android

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import net.hordecraft.android.data.local.HordecraftDatabase
import net.hordecraft.android.data.local.SettingsDataStore
import net.hordecraft.android.data.local.WebpStorageManager
import net.hordecraft.android.data.remote.CivitaiApiService
import net.hordecraft.android.data.remote.HordeApiService
import net.hordecraft.android.data.remote.HordeRateLimiterInterceptor
import net.hordecraft.android.data.remote.ModelReferenceRepository
import net.hordecraft.android.data.repository.HordecraftRepository
import net.hordecraft.android.domain.HordeConstants
import net.hordecraft.android.service.NotificationChannels
import net.hordecraft.android.ui.StudioUiState
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

    init {
        appScope.launch {
            settingsStore.settings.collect { s ->
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
    @Volatile
    var catalogsLoadedOnce: Boolean = false

    val repository = HordecraftRepository(
        context = context,
        settingsStore = settingsStore,
        promptHistoryDao = database.promptHistoryDao(),
        modelDefaultsDao = database.modelDefaultsDao(),
        savedImageDao = database.savedImageDao(),
        activeJobDao = database.activeJobDao(),
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
