package io.github.winddrift.fuckpangguai.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.winddrift.fuckpangguai.AppController
import io.github.winddrift.fuckpangguai.AppState
import io.github.winddrift.fuckpangguai.auth.DisclaimerScreen
import io.github.winddrift.fuckpangguai.auth.LoginScreen
import io.github.winddrift.fuckpangguai.confirm.ConfirmController
import io.github.winddrift.fuckpangguai.confirm.ConfirmScreen
import io.github.winddrift.fuckpangguai.confirm.ConfirmState
import io.github.winddrift.fuckpangguai.debug.DebugScreen
import io.github.winddrift.fuckpangguai.history.HistoryRepository
import io.github.winddrift.fuckpangguai.history.HistoryScreen
import io.github.winddrift.fuckpangguai.nfc.NfcTagStore
import io.github.winddrift.fuckpangguai.scan.ScanController
import io.github.winddrift.fuckpangguai.scan.ScanScreen
import io.github.winddrift.fuckpangguai.settings.SettingsScreen
import io.github.winddrift.fuckpangguai.storage.AppSettingsStore
import io.github.winddrift.fuckpangguai.ui.components.AppSnackbarState
import io.github.winddrift.fuckpangguai.ui.components.AppTopBar
import io.github.winddrift.fuckpangguai.ui.components.BottomActionBar
import io.github.winddrift.fuckpangguai.ui.components.ConfirmDialog
import io.github.winddrift.fuckpangguai.ui.components.LocalSnackbar
import io.github.winddrift.fuckpangguai.ui.format.sessionPhaseLabel
import io.github.winddrift.fuckpangguai.ui.navigation.MainTab
import io.github.winddrift.fuckpangguai.ui.navigation.SecondaryPage
import io.github.winddrift.fuckpangguai.ui.navigation.icon
import io.github.winddrift.fuckpangguai.using.UsingScreen
import io.github.winddrift.fuckpangguai.water.SessionPhase
import io.github.winddrift.fuckpangguai.water.WaterSession
import io.github.winddrift.fuckpangguai.water.WaterSessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarDisplayMode
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
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
    val context = LocalContext.current
    val state by controller.state.collectAsStateWithLifecycle()
    val busy by controller.busy.collectAsStateWithLifecycle()
    val message by controller.message.collectAsStateWithLifecycle()
    val codeSent by controller.codeSent.collectAsStateWithLifecycle()
    val activeSession by waterSessionManager.session.collectAsStateWithLifecycle()

    var tab by rememberSaveable { mutableStateOf(MainTab.Scan) }
    var secondary by remember { mutableStateOf<SecondaryPage?>(null) }
    var showEndSessionDialog by remember { mutableStateOf(false) }

    val confirmDevice = (secondary as? SecondaryPage.Confirm)?.device
    val confirmController = remember(confirmDevice) {
        confirmDevice?.let { newConfirmController() }
    }
    val confirmStateFlow = remember(confirmController) {
        confirmController?.state ?: MutableStateFlow<ConfirmState>(ConfirmState.Loading)
    }
    val confirmState by confirmStateFlow.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val snackbar = remember(snackbarHostState, scope) {
        AppSnackbarState(snackbarHostState, scope)
    }

    LaunchedEffect(message) {
        message?.let {
            snackbar.show(it.text, it.isError)
            controller.clearMessage()
        }
    }

    LaunchedEffect(state) {
        if (state != AppState.Ready) {
            secondary = null
            tab = MainTab.Scan
            showEndSessionDialog = false
        }
    }

    LaunchedEffect(confirmDevice, confirmController) {
        if (confirmDevice != null) {
            confirmController?.start(confirmDevice)
        }
    }

    LaunchedEffect(confirmState, secondary) {
        val current = confirmState
        if (secondary is SecondaryPage.Confirm && current is ConfirmState.Unlocked) {
            secondary = null
            tab = MainTab.Scan
            waterSessionManager.start(current.session)
        }
    }

    LaunchedEffect(activeSession) {
        if (activeSession == null) {
            showEndSessionDialog = false
        }
    }

    fun openOriginalApp() {
        val launched = runCatching {
            val intent = context.packageManager.getLaunchIntentForPackage(ORIGINAL_PACKAGE)
            if (intent != null) {
                context.startActivity(intent)
                true
            } else {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW).setPackage(ORIGINAL_PACKAGE),
                )
                true
            }
        }.getOrDefault(false)
        if (!launched) {
            snackbar.show("未安装胖乖生活", isError = true)
        }
    }

    fun requestUsingExit() {
        val session = activeSession ?: return
        when {
            session.phase in SessionPhase.TERMINAL -> waterSessionManager.clear()
            session.phase == SessionPhase.USING && session.supportAppUserFinish -> {
                showEndSessionDialog = true
            }
            session.phase == SessionPhase.USING -> snackbar.show("请在设备上结束接水")
            else -> snackbar.show("正在结算/结束中，请稍候")
        }
    }

    BackHandler(
        enabled = activeSession != null ||
            secondary != null ||
            (state == AppState.Ready && tab != MainTab.Scan),
    ) {
        when {
            activeSession != null -> requestUsingExit()
            secondary != null -> secondary = null
            tab != MainTab.Scan -> tab = MainTab.Scan
        }
    }

    val session = activeSession
    val currentSecondary = secondary

    CompositionLocalProvider(LocalSnackbar provides snackbar) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MiuixTheme.colorScheme.surface,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                when {
                    session != null -> AppTopBar("接水状态", onBack = ::requestUsingExit)
                    state == AppState.Ready && currentSecondary is SecondaryPage.Confirm -> {
                        AppTopBar("确认设备", onBack = { secondary = null })
                    }
                    state == AppState.Ready && currentSecondary is SecondaryPage.Debug -> {
                        AppTopBar("诊断", onBack = { secondary = null })
                    }
                    state == AppState.Ready && tab == MainTab.History -> {
                        AppTopBar("接水记录")
                    }
                    state == AppState.Ready && tab == MainTab.Settings -> {
                        AppTopBar("设置")
                    }
                }
            },
            bottomBar = {
                when {
                    session != null -> UsingActionBar(
                        session = session,
                        onEndRequest = { showEndSessionDialog = true },
                        onDone = waterSessionManager::clear,
                        onOpenOriginal = ::openOriginalApp,
                    )

                    state == AppState.Ready && currentSecondary is SecondaryPage.Confirm -> {
                        ConfirmActionBar(
                            state = confirmState,
                            onUnlock = { confirmController?.unlock() },
                            onBack = { secondary = null },
                        )
                    }

                    state == AppState.Ready && currentSecondary == null -> {
                        NavigationBar(mode = NavigationBarDisplayMode.IconAndText) {
                            MainTab.entries.forEach { item ->
                                NavigationBarItem(
                                    selected = tab == item,
                                    onClick = { tab = item },
                                    icon = item.icon,
                                    label = item.label,
                                )
                            }
                        }
                    }
                }
            },
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                when {
                    session != null -> UsingScreen(
                        session = session,
                        onEnd = waterSessionManager::end,
                        onDone = waterSessionManager::clear,
                    )

                    state == AppState.Loading -> LoadingScreen()

                    state == AppState.Disclaimer -> DisclaimerScreen(
                        onAccept = controller::acceptDisclaimer,
                    )

                    state == AppState.Login -> LoginScreen(
                        busy = busy,
                        codeSent = codeSent,
                        onSendCode = controller::sendCode,
                        onLogin = controller::login,
                        onImportToken = controller::importToken,
                    )

                    state == AppState.Ready && currentSecondary is SecondaryPage.Confirm -> {
                        ConfirmScreen(
                            device = currentSecondary.device,
                            state = confirmState,
                            onSelectSku = { confirmController?.selectSku(it) },
                            onSelectChannel = { confirmController?.selectChannel(it) },
                            onRetryLocation = { confirmController?.retryLocation() },
                            onBack = { secondary = null },
                        )
                    }

                    state == AppState.Ready && currentSecondary is SecondaryPage.Debug -> {
                        DebugScreen(
                            settingsStore = settingsStore,
                            nfcTagStore = nfcTagStore,
                            scanController = scanController,
                            onImportToken = controller::importToken,
                        )
                    }

                    state == AppState.Ready && tab == MainTab.History -> {
                        HistoryScreen(repository = historyRepository)
                    }

                    state == AppState.Ready && tab == MainTab.Settings -> {
                        SettingsScreen(
                            settingsStore = settingsStore,
                            historyRepository = historyRepository,
                            onOpenDebug = { secondary = SecondaryPage.Debug },
                            onLogout = {
                                secondary = null
                                tab = MainTab.Scan
                                scanController.reset()
                                controller.logout()
                            },
                        )
                    }

                    state == AppState.Ready -> ScanScreen(
                        controller = scanController,
                        onDeviceReady = { secondary = SecondaryPage.Confirm(it) },
                    )
                }

                ConfirmDialog(
                    show = showEndSessionDialog,
                    title = "结束接水？",
                    summary = "结束后将按当前用量结算，无法撤销。",
                    confirmText = "结束使用",
                    destructive = true,
                    onConfirm = waterSessionManager::end,
                    onDismiss = { showEndSessionDialog = false },
                )
            }
        }
    }
}

@Composable
private fun ConfirmActionBar(
    state: ConfirmState,
    onUnlock: () -> Unit,
    onBack: () -> Unit,
) {
    BottomActionBar {
        when (state) {
            is ConfirmState.Ready -> {
                val ready = state.value
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = ready.sku.name,
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceSecondary,
                    )
                    Text(
                        text = "¥${ready.sku.discountPrice.ifBlank { ready.sku.price }}",
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.primary,
                    )
                }
                Button(
                    onClick = onUnlock,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) {
                    Text(if (ready.dryRun) "演练解锁" else "开始使用")
                }
            }

            ConfirmState.Unlocking -> Button(
                onClick = {},
                modifier = Modifier.fillMaxWidth(),
                enabled = false,
            ) {
                Text("正在解锁…")
            }

            ConfirmState.Loading -> Button(
                onClick = {},
                modifier = Modifier.fillMaxWidth(),
                enabled = false,
            ) {
                Text("正在加载…")
            }

            else -> TextButton(
                text = "返回扫码",
                onClick = onBack,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun UsingActionBar(
    session: WaterSession,
    onEndRequest: () -> Unit,
    onDone: () -> Unit,
    onOpenOriginal: () -> Unit,
) {
    BottomActionBar {
        when {
            session.phase in SessionPhase.TERMINAL -> {
                if (session.phase == SessionPhase.PAY_UNKNOWN || session.phase == SessionPhase.FAULT) {
                    TextButton(
                        text = "打开胖乖生活",
                        onClick = onOpenOriginal,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Button(
                    onClick = onDone,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) {
                    Text("完成")
                }
            }

            session.phase == SessionPhase.USING && session.supportAppUserFinish -> {
                Button(
                    onClick = onEndRequest,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) {
                    Text("结束使用")
                }
            }

            session.phase == SessionPhase.USING -> {
                Button(
                    onClick = {},
                    modifier = Modifier.fillMaxWidth(),
                    enabled = false,
                ) {
                    Text("请在设备上结束")
                }
            }

            else -> {
                Button(
                    onClick = {},
                    modifier = Modifier.fillMaxWidth(),
                    enabled = false,
                ) {
                    Text("${sessionPhaseLabel(session.phase)}…")
                }
            }
        }
    }
}

private const val ORIGINAL_PACKAGE = "com.qiekj.user"
