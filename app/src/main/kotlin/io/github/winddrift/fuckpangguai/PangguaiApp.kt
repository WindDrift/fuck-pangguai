package io.github.winddrift.fuckpangguai

import android.app.Application
import io.github.winddrift.fuckpangguai.service.NotificationChannels

class PangguaiApp : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
        NotificationChannels.ensure(this)
    }
}
