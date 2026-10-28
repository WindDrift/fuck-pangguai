package io.github.winddrift.fuckpangguai

import android.app.Application
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import io.github.winddrift.fuckpangguai.diagnostics.model.DiagFlow
import io.github.winddrift.fuckpangguai.diagnostics.model.DiagLevel
import io.github.winddrift.fuckpangguai.service.NotificationChannels
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class PangguaiApp : Application() {
    lateinit var graph: AppGraph
        private set

    private val imageHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    override fun onCreate() {
        super.onCreate()
        SingletonImageLoader.setSafe { context ->
            ImageLoader.Builder(context)
                .components {
                    add(OkHttpNetworkFetcherFactory(callFactory = { imageHttpClient }))
                }
                .build()
        }
        graph = AppGraph(this)
        graph.diagnostics.event(
            DiagLevel.Info,
            DiagFlow.App,
            "app_start",
            fields = mapOf(
                "version" to BuildConfig.VERSION_NAME,
                "buildType" to BuildConfig.BUILD_TYPE,
            ),
        )
        NotificationChannels.ensure(this)
    }
}
