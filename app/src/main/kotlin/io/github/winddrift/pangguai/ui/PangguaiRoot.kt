package io.github.winddrift.pangguai.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.winddrift.pangguai.AppController
import io.github.winddrift.pangguai.AppState
import io.github.winddrift.pangguai.auth.DisclaimerScreen
import io.github.winddrift.pangguai.auth.LoginScreen
import io.github.winddrift.pangguai.scan.ScanController
import io.github.winddrift.pangguai.scan.ScanScreen
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun PangguaiRoot(controller: AppController, scanController: ScanController) {
    val state by controller.state.collectAsStateWithLifecycle()
    val busy by controller.busy.collectAsStateWithLifecycle()
    val message by controller.message.collectAsStateWithLifecycle()
    val codeSent by controller.codeSent.collectAsStateWithLifecycle()

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
        AppState.Ready -> ScanScreen(controller = scanController, onLogout = controller::logout)
    }
}

@Composable
private fun LoadingScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("加载中…", style = MiuixTheme.textStyles.body1)
    }
}
