package io.github.winddrift.pangguai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.winddrift.pangguai.ui.PangguaiRoot
import io.github.winddrift.pangguai.ui.theme.PangguaiTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val graph = (application as PangguaiApp).graph
        setContent {
            PangguaiTheme {
                PangguaiRoot(
                    controller = graph.controller,
                    scanController = graph.scanController,
                    newConfirmController = graph::newConfirmController,
                    waterSessionManager = graph.waterSessionManager,
                    historyRepository = graph.historyRepository,
                    settingsStore = graph.settingsStore,
                    nfcTagStore = graph.nfcTagStore,
                )
            }
        }
    }
}
