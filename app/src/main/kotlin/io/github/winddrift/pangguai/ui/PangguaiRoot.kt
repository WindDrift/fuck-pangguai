package io.github.winddrift.pangguai.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.winddrift.pangguai.AppController
import io.github.winddrift.pangguai.AppState
import io.github.winddrift.pangguai.auth.DisclaimerScreen
import io.github.winddrift.pangguai.auth.LoginScreen
import io.github.winddrift.pangguai.confirm.ConfirmController
import io.github.winddrift.pangguai.confirm.ConfirmScreen
import io.github.winddrift.pangguai.device.ResolvedDevice
import io.github.winddrift.pangguai.debug.DebugScreen
import io.github.winddrift.pangguai.history.HistoryRepository
import io.github.winddrift.pangguai.history.HistoryScreen
import io.github.winddrift.pangguai.nfc.NfcTagStore
import io.github.winddrift.pangguai.settings.SettingsScreen
import io.github.winddrift.pangguai.storage.AppSettingsStore
import io.github.winddrift.pangguai.scan.ScanController
import io.github.winddrift.pangguai.scan.ScanScreen
import io.github.winddrift.pangguai.using.UsingScreen
import io.github.winddrift.pangguai.water.WaterSessionManager
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun PangguaiRoot(
    controller: AppController,
    scanController: ScanController,
    newConfirmController: () -> ConfirmController,
    waterSessionManager: WaterSessionManager,
    historyRepository: HistoryRepository,
    settingsStore: AppSettingsStore,
    nfcTagStore: NfcTagStore,
) {
    val state by controller.state.collectAsStateWithLifecycle()
    val busy by controller.busy.collectAsStateWithLifecycle()
    val message by controller.message.collectAsStateWithLifecycle()
    val codeSent by controller.codeSent.collectAsStateWithLifecycle()
    val activeSession by waterSessionManager.session.collectAsStateWithLifecycle()

    var activeDevice by remember { mutableStateOf<ResolvedDevice?>(null) }
    var overlay by remember { mutableStateOf(Overlay.None) }

    if (activeSession != null) {
        UsingScreen(
            session = activeSession!!,
            onEnd = waterSessionManager::end,
            onDone = waterSessionManager::clear,
        )
        return
    }

    when (state) {
        AppState.Loading -> LoadingScreen()
        AppState.Disclaimer -> DisclaimerScreen(onAccept = controller::acceptDisclaimer)
        AppState.Login -> LoginScreen(
            busy = busy,
            message = message,
            codeSent = codeSent,
            onSendCode = controller::sendCode,
            onLogin = controller::login,
            onImportToken = controller::importToken,
        )
        AppState.Ready -> {
            val device = activeDevice
            when {
                device != null -> ConfirmScreen(
                    device = device,
                    controller = remember(device) { newConfirmController() },
                    onUnlocked = { unlocked ->
                        activeDevice = null
                        overlay = Overlay.None
                        waterSessionManager.start(unlocked)
                    },
                    onBack = { activeDevice = null },
                )

                overlay == Overlay.History -> HistoryScreen(
                    repository = historyRepository,
                    onBack = { overlay = Overlay.None },
                )

                overlay == Overlay.Settings -> SettingsScreen(
                    settingsStore = settingsStore,
                    historyRepository = historyRepository,
                    onBack = { overlay = Overlay.None },
                    onOpenDebug = { overlay = Overlay.Debug },
                )

                overlay == Overlay.Debug -> DebugScreen(
                    settingsStore = settingsStore,
                    nfcTagStore = nfcTagStore,
                    scanController = scanController,
                    onImportToken = controller::importToken,
                    onBack = { overlay = Overlay.Settings },
                )

                else -> ScanScreen(
                    controller = scanController,
                    onDeviceReady = { activeDevice = it },
                    onOpenHistory = { overlay = Overlay.History },
                    onOpenSettings = { overlay = Overlay.Settings },
                    onLogout = {
                        activeDevice = null
                        overlay = Overlay.None
                        controller.logout()
                    },
                )
            }
        }
    }
}

private enum class Overlay { None, History, Settings, Debug }

@Composable
private fun LoadingScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("加载中…", style = MiuixTheme.textStyles.body1)
    }
}
