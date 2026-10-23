package io.github.winddrift.pangguai

import android.app.Application
import io.github.winddrift.pangguai.service.NotificationChannels

class PangguaiApp : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
        NotificationChannels.ensure(this)
    }
}
