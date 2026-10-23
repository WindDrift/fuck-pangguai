package io.github.winddrift.pangguai

import io.github.winddrift.pangguai.auth.AuthRepository
import io.github.winddrift.pangguai.net.QiekjApi
import io.github.winddrift.pangguai.net.RequestLogInterceptor
import io.github.winddrift.pangguai.net.SignInterceptor
import io.github.winddrift.pangguai.storage.DisclaimerStore
import io.github.winddrift.pangguai.storage.TokenStore
import io.github.winddrift.pangguai.device.DeviceRepository
import io.github.winddrift.pangguai.nfc.NfcTagStore
import io.github.winddrift.pangguai.scan.ScanController
import android.content.Context
import android.os.Build
import android.provider.Settings
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import io.github.winddrift.pangguai.core.AppConfig
import io.github.winddrift.pangguai.net.QiekjSigner
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

    val tokenStore = TokenStore(appContext)
    private val disclaimerStore = DisclaimerStore(appContext)

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
                tokenProvider = { tokenStore.currentToken.orEmpty() },
                deviceId = deviceId,
                phoneBrand = Build.BRAND.orEmpty(),
                androidVersion = Build.VERSION.RELEASE.orEmpty(),
            ),
        )
        .apply {
            if (BuildConfig.DEBUG) {
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

    private val authRepository = AuthRepository(api, tokenStore)

    val controller = AppController(
        scope = scope,
        authRepository = authRepository,
        disclaimerStore = disclaimerStore,
    )

    val scanController = ScanController(
        scope = scope,
        deviceRepository = DeviceRepository(api),
        nfcTagStore = NfcTagStore(appContext),
    )
}
