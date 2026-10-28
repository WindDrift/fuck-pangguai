package io.github.winddrift.fuckpangguai

import io.github.winddrift.fuckpangguai.account.AccountRepository
import io.github.winddrift.fuckpangguai.account.AccountStore
import io.github.winddrift.fuckpangguai.account.AssetController
import io.github.winddrift.fuckpangguai.account.AssetRepository
import io.github.winddrift.fuckpangguai.account.AssetSnapshotStore
import io.github.winddrift.fuckpangguai.net.QiekjApi
import io.github.winddrift.fuckpangguai.net.RequestLogInterceptor
import io.github.winddrift.fuckpangguai.net.SessionExpiryInterceptor
import io.github.winddrift.fuckpangguai.net.SessionExpiryNotifier
import io.github.winddrift.fuckpangguai.net.SignInterceptor
import io.github.winddrift.fuckpangguai.storage.DisclaimerStore
import io.github.winddrift.fuckpangguai.ticket.AccountStoreTicketSource
import io.github.winddrift.fuckpangguai.ticket.DataStorePendingRechargeStore
import io.github.winddrift.fuckpangguai.ticket.QiekjTicketRechargeRepository
import io.github.winddrift.fuckpangguai.ticket.TicketRechargeController
import io.github.winddrift.fuckpangguai.device.DeviceRepository
import io.github.winddrift.fuckpangguai.nfc.NfcTagStore
import io.github.winddrift.fuckpangguai.points.AccountStorePointsSource
import io.github.winddrift.fuckpangguai.points.DataStorePointsRunStore
import io.github.winddrift.fuckpangguai.points.PointsController
import io.github.winddrift.fuckpangguai.points.QiekjPointsRepository
import io.github.winddrift.fuckpangguai.scan.ScanController
import io.github.winddrift.fuckpangguai.water.WaterRepository
import io.github.winddrift.fuckpangguai.water.WaterSessionManager
import io.github.winddrift.fuckpangguai.water.WaterSessionStore
import io.github.winddrift.fuckpangguai.location.LocationProvider
import io.github.winddrift.fuckpangguai.storage.AppSettingsStore
import io.github.winddrift.fuckpangguai.storage.DeviceConfigStore
import io.github.winddrift.fuckpangguai.storage.PlanCacheStore
import io.github.winddrift.fuckpangguai.update.GithubReleaseSource
import io.github.winddrift.fuckpangguai.update.UpdateController
import io.github.winddrift.fuckpangguai.confirm.ConfirmController
import io.github.winddrift.fuckpangguai.confirm.NoopUnlockTimingSink
import io.github.winddrift.fuckpangguai.confirm.UnlockTimingLog
import io.github.winddrift.fuckpangguai.diagnostics.CrashRecorder
import io.github.winddrift.fuckpangguai.diagnostics.DiagSettingsStore
import io.github.winddrift.fuckpangguai.diagnostics.Diagnostics
import io.github.winddrift.fuckpangguai.history.BillRepository
import io.github.winddrift.fuckpangguai.history.HistoryController
import io.github.winddrift.fuckpangguai.history.HistoryRepository
import io.github.winddrift.fuckpangguai.net.DiagnosticNetworkInterceptor
import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import io.github.winddrift.fuckpangguai.core.AppConfig
import io.github.winddrift.fuckpangguai.net.QiekjSigner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

class AppGraph(context: Context) {

    val appContext: Context = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val diagSettingsStore = DiagSettingsStore(appContext)
    val diagnostics = Diagnostics(
        context = appContext,
        settingsStore = diagSettingsStore,
        appVersion = BuildConfig.VERSION_NAME,
        buildType = BuildConfig.BUILD_TYPE,
        scope = scope,
        previewLimit = if (BuildConfig.DIAGNOSTIC_LOGGING) 16 * 1024 else 4 * 1024,
    )

    val diagnosticsController = io.github.winddrift.fuckpangguai.diagnostics.DiagnosticsController(
        context = appContext,
        diagnostics = diagnostics,
        settingsStore = diagSettingsStore,
        scope = scope,
    )

    val accountStore = AccountStore(appContext)
    val assetSnapshotStore = AssetSnapshotStore(appContext)
    private val pendingRechargeStore = DataStorePendingRechargeStore(appContext)
    val historyRepository = HistoryRepository(appContext, accountStore)
    private val disclaimerStore = DisclaimerStore(appContext)
    private val sessionExpiryNotifier = SessionExpiryNotifier()

    val deviceId: String = run {
        val androidId = Settings.Secure.getString(
            appContext.contentResolver,
            Settings.Secure.ANDROID_ID,
        ).orEmpty()
        QiekjSigner.sha256Hex("pangguai:$androidId")
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(
            SignInterceptor(
                tokenProvider = { accountStore.currentToken.orEmpty() },
                deviceId = deviceId,
                phoneBrand = Build.BRAND.orEmpty(),
                androidVersion = Build.VERSION.RELEASE.orEmpty(),
            ),
        )
        .addInterceptor(SessionExpiryInterceptor(sessionExpiryNotifier))
        .addInterceptor(
            DiagnosticNetworkInterceptor(
                sink = diagnostics,
                largePreview = BuildConfig.DIAGNOSTIC_LOGGING,
            ),
        )
        .apply {
            if (BuildConfig.DEBUG || BuildConfig.DIAGNOSTIC_LOGGING) {
                addInterceptor(RequestLogInterceptor())
            }
        }
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(AppConfig.API_BASE)
        .client(httpClient)
        .addConverterFactory(
            json.asConverterFactory("application/x-www-form-urlencoded".toMediaType()),
        )
        .build()

    val api: QiekjApi = retrofit.create(QiekjApi::class.java)
    private val planCacheStore = PlanCacheStore(appContext)
    val deviceRepository = DeviceRepository(
        api = api,
        planCache = planCacheStore,
        accountIdProvider = accountStore::activeLocalId,
        diagnostics = diagnostics,
    )
    val accountRepository = AccountRepository(
        api = api,
        accountStore = accountStore,
        historyRepository = historyRepository,
        onAccountCleared = { localId ->
            assetSnapshotStore.delete(localId)
            deviceRepository.clearAccountCache(localId)
            pendingRechargeStore.clear(localId)
        },
    )

    val waterRepository = WaterRepository(api)
    val nfcTagStore = NfcTagStore(appContext)
    val settingsStore = AppSettingsStore(appContext)
    val updateController = UpdateController(
        scope = scope,
        source = GithubReleaseSource(),
        currentVersion = BuildConfig.VERSION_NAME,
        diagnostics = diagnostics,
    )
    val assetRepository = AssetRepository(
        api = api,
        accountStore = accountStore,
        accountRepository = accountRepository,
    )
    val assetController = AssetController(
        scope = scope,
        accountStore = accountStore,
        assetRepository = assetRepository,
        snapshotStore = assetSnapshotStore,
        settingsStore = settingsStore,
    )
    val historyController = HistoryController(
        scope = scope,
        historyRepository = historyRepository,
        billRepository = BillRepository(api),
        diagnostics = diagnostics,
    )
    val ticketRechargeController = TicketRechargeController(
        scope = scope,
        dataSource = QiekjTicketRechargeRepository(api),
        pendingStore = pendingRechargeStore,
        accountSource = AccountStoreTicketSource(accountStore),
        onPaid = assetController::refresh,
        diagnostics = diagnostics,
    )

    val pointsController = PointsController(
        scope = scope,
        accountSource = AccountStorePointsSource(accountStore),
        dataSource = QiekjPointsRepository(api, deviceId),
        runStore = DataStorePointsRunStore(appContext),
        settings = settingsStore,
        notificationEnabled = {
            NotificationManagerCompat.from(appContext).areNotificationsEnabled()
        },
        onPointsChanged = assetController::refresh,
        diagnostics = diagnostics,
    )
    val deviceConfigStore = DeviceConfigStore(appContext)
    val waterSessionStore = WaterSessionStore(appContext)
    val waterSessionManager = WaterSessionManager(
        context = appContext,
        scope = scope,
        store = waterSessionStore,
        waterRepository = waterRepository,
        accountStore = accountStore,
        diagnostics = diagnostics,
    )

    init {
        CrashRecorder(
            appVersion = BuildConfig.VERSION_NAME,
            buildType = BuildConfig.BUILD_TYPE,
            traceProvider = { diagnostics.currentTrace() },
            onCrash = diagnostics::recordCrashSync,
        ).install()
        diagnostics.start { waterSessionStore.load()?.traceId }
    }

    val controller = AppController(
        scope = scope,
        accountRepository = accountRepository,
        disclaimerStore = disclaimerStore,
        sessionExpiryNotifier = sessionExpiryNotifier,
        sessionPhaseProvider = { waterSessionStore.session.value?.phase },
        diagnostics = diagnostics,
        clearDiagnostics = { diagnostics.clear() },
    )

    val scanController = ScanController(
        scope = scope,
        deviceRepository = deviceRepository,
        nfcTagStore = nfcTagStore,
        diagnostics = diagnostics,
    )

    val locationProvider = LocationProvider(appContext)

    fun newConfirmController(): ConfirmController = ConfirmController(
        scope = scope,
        deviceRepository = deviceRepository,
        waterRepository = waterRepository,
        locationProvider = locationProvider,
        settingsStore = settingsStore,
        configStore = deviceConfigStore,
        timingSink = if (BuildConfig.DEBUG) UnlockTimingLog else NoopUnlockTimingSink,
        diagnostics = diagnostics,
    )
}
