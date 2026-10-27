package io.github.winddrift.fuckpangguai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.winddrift.fuckpangguai.storage.ThemeSettings
import io.github.winddrift.fuckpangguai.ui.PangguaiRoot
import io.github.winddrift.fuckpangguai.ui.theme.PangguaiTheme
import io.github.winddrift.fuckpangguai.ui.theme.resolveDark

class MainActivity : ComponentActivity() {
    override fun onStart() {
        super.onStart()
        (application as PangguaiApp).graph.pointsController.onAppForeground()
    }

    override fun onStop() {
        (application as PangguaiApp).graph.pointsController.onAppBackground()
        super.onStop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val graph = (application as PangguaiApp).graph
        setContent {
            val themeSettings by graph.settingsStore.themeSettings
                .collectAsStateWithLifecycle(initialValue = ThemeSettings())
            val dark = themeSettings.mode.resolveDark(isSystemInDarkTheme())
            val view = LocalView.current

            SideEffect {
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }

            PangguaiTheme(themeSettings) {
                PangguaiRoot(
                    controller = graph.controller,
                    scanController = graph.scanController,
                    newConfirmController = graph::newConfirmController,
                    waterSessionManager = graph.waterSessionManager,
                    historyRepository = graph.historyRepository,
                    historyController = graph.historyController,
                    settingsStore = graph.settingsStore,
                    assetController = graph.assetController,
                    pointsController = graph.pointsController,
                    ticketRechargeController = graph.ticketRechargeController,
                    nfcTagStore = graph.nfcTagStore,
                    updateController = graph.updateController,
                    onWarmUpLocation = graph.locationProvider::warmUp,
                )
            }
        }
    }
}
