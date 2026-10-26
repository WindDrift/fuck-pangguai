package io.github.winddrift.fuckpangguai.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.winddrift.fuckpangguai.AppController
import io.github.winddrift.fuckpangguai.AppState
import io.github.winddrift.fuckpangguai.account.AssetController
import io.github.winddrift.fuckpangguai.account.AuthTarget
import io.github.winddrift.fuckpangguai.auth.DisclaimerScreen
import io.github.winddrift.fuckpangguai.auth.LoginScreen
import io.github.winddrift.fuckpangguai.confirm.ConfirmController
import io.github.winddrift.fuckpangguai.confirm.ConfirmScreen
import io.github.winddrift.fuckpangguai.confirm.ConfirmState
import io.github.winddrift.fuckpangguai.confirm.LocationPhase
import io.github.winddrift.fuckpangguai.debug.DebugScreen
import io.github.winddrift.fuckpangguai.device.ResolvedDevice
import io.github.winddrift.fuckpangguai.history.HistoryController
import io.github.winddrift.fuckpangguai.history.HistoryRepository
import io.github.winddrift.fuckpangguai.history.HistoryScreen
import io.github.winddrift.fuckpangguai.nfc.NfcTagStore
import io.github.winddrift.fuckpangguai.points.PointsController
import io.github.winddrift.fuckpangguai.points.PointsScreen
import io.github.winddrift.fuckpangguai.scan.ScanController
import io.github.winddrift.fuckpangguai.scan.ScanScreen
import io.github.winddrift.fuckpangguai.settings.SettingsScreen
import io.github.winddrift.fuckpangguai.settings.TicketsScreen
import io.github.winddrift.fuckpangguai.storage.AppSettingsStore
import io.github.winddrift.fuckpangguai.storage.ThemeSettings
import io.github.winddrift.fuckpangguai.ticket.TicketRechargeController
import io.github.winddrift.fuckpangguai.ticket.TicketRechargeScreen
import io.github.winddrift.fuckpangguai.ui.animation.ContentFadeSpec
import io.github.winddrift.fuckpangguai.ui.animation.ContentSlideSpec
import io.github.winddrift.fuckpangguai.ui.animation.PageFadeSpec
import io.github.winddrift.fuckpangguai.ui.animation.PageSlideSpec
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
import top.yukonga.miuix.kmp.basic.FloatingNavigationBar
import top.yukonga.miuix.kmp.basic.FloatingNavigationBarItem
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
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
    historyController: HistoryController,
    settingsStore: AppSettingsStore,
    assetController: AssetController,
    pointsController: PointsController,
    ticketRechargeController: TicketRechargeController,
    nfcTagStore: NfcTagStore,
    onWarmUpLocation: () -> Unit = {},
) {
    val context = LocalContext.current
    val state by controller.state.collectAsStateWithLifecycle()
    val busy by controller.busy.collectAsStateWithLifecycle()
    val message by controller.message.collectAsStateWithLifecycle()
    val codeSent by controller.codeSent.collectAsStateWithLifecycle()
    val authTarget by controller.authTarget.collectAsStateWithLifecycle()
    val reauthPhone by controller.reauthPhone.collectAsStateWithLifecycle()
    val accounts by controller.accounts.collectAsStateWithLifecycle()
    val activeAccount by controller.activeAccount.collectAsStateWithLifecycle()
    val activeSession by waterSessionManager.session.collectAsStateWithLifecycle()
    val themeSettings by settingsStore.themeSettings
        .collectAsStateWithLifecycle(initialValue = ThemeSettings())
    val skipDeviceInfo by settingsStore.skipDeviceInfo
        .collectAsStateWithLifecycle(initialValue = false)
    // DataStore 首次读取是异步的；在拿到持久化值前按“不自动打开相机”处理，避免冷启动先拉起相机。
    val autoStartCamera by settingsStore.autoStartCamera
        .collectAsStateWithLifecycle(initialValue = false)
    var cameraSessionStarted by rememberSaveable { mutableStateOf(false) }

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

    val historyScrollBehavior = MiuixScrollBehavior()
    val pointsScrollBehavior = MiuixScrollBehavior()
    val settingsScrollBehavior = MiuixScrollBehavior()
    val debugScrollBehavior = MiuixScrollBehavior()
    val density = LocalDensity.current
    var floatingNavHeightPx by remember { mutableIntStateOf(0) }
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

    LaunchedEffect(Unit) {
        controller.authCompleted.collect {
            if (secondary is SecondaryPage.AccountAccess) {
                secondary = null
                tab = MainTab.Settings
            }
        }
    }

    LaunchedEffect(Unit) {
        controller.accountChanged.collect {
            scanController.reset()
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

    fun closeSecondary() {
        when (val page = secondary) {
            is SecondaryPage.Tickets -> {
                secondary = if (page.shopId.isBlank()) null else SecondaryPage.Tickets()
            }

            is SecondaryPage.TicketRecharge -> {
                secondary = SecondaryPage.Tickets(page.shopId, page.shopName)
            }

            is SecondaryPage.AccountAccess -> {
                controller.cancelAuth()
                secondary = null
            }

            else -> secondary = null
        }
    }

    BackHandler(
        enabled = activeSession != null ||
            secondary != null ||
            (state == AppState.Ready && tab != MainTab.Scan),
    ) {
        when {
            activeSession != null -> requestUsingExit()
            secondary != null -> closeSecondary()
            tab != MainTab.Scan -> tab = MainTab.Scan
        }
    }

    val session = activeSession
    val currentSecondary = secondary
    val destination = when {
        session != null -> RootDestination.Using(session)
        state == AppState.Loading -> RootDestination.Loading
        state == AppState.Disclaimer -> RootDestination.Disclaimer
        state == AppState.Login -> RootDestination.Login
        currentSecondary is SecondaryPage.Confirm -> RootDestination.Confirm(
            device = currentSecondary.device,
            state = confirmState,
        )
        currentSecondary is SecondaryPage.Debug -> RootDestination.Debug
        currentSecondary is SecondaryPage.AccountAccess ->
            RootDestination.AccountAccess(currentSecondary.target)

        currentSecondary is SecondaryPage.Tickets ->
            RootDestination.Tickets(currentSecondary.shopId, currentSecondary.shopName)

        currentSecondary is SecondaryPage.TicketRecharge ->
            RootDestination.TicketRecharge(currentSecondary.shopId, currentSecondary.shopName)

        tab == MainTab.History -> RootDestination.History
        tab == MainTab.Points -> RootDestination.Points
        tab == MainTab.Settings -> RootDestination.Settings
        else -> RootDestination.Scan
    }

    LaunchedEffect(destination) {
        if (destination !is RootDestination.History) {
            historyScrollBehavior.state.heightOffset = 0f
            historyScrollBehavior.state.contentOffset = 0f
        }
        if (destination !is RootDestination.Points) {
            pointsScrollBehavior.state.heightOffset = 0f
            pointsScrollBehavior.state.contentOffset = 0f
        }
        if (destination !is RootDestination.Settings) {
            settingsScrollBehavior.state.heightOffset = 0f
            settingsScrollBehavior.state.contentOffset = 0f
        }
        if (destination != RootDestination.Debug) {
            debugScrollBehavior.state.heightOffset = 0f
            debugScrollBehavior.state.contentOffset = 0f
        }
    }

    val topBarKind = when {
        session != null -> RootTopBar.Using
        state != AppState.Ready -> RootTopBar.None
        currentSecondary is SecondaryPage.Confirm -> RootTopBar.Confirm
        currentSecondary is SecondaryPage.Debug -> RootTopBar.Debug
        currentSecondary is SecondaryPage.AccountAccess -> RootTopBar.AccountAccess
        currentSecondary is SecondaryPage.Tickets -> RootTopBar.Tickets
        currentSecondary is SecondaryPage.TicketRecharge -> RootTopBar.TicketRecharge
        tab == MainTab.History -> RootTopBar.History
        tab == MainTab.Points -> RootTopBar.Points
        tab == MainTab.Settings -> RootTopBar.Settings
        else -> RootTopBar.None
    }
    val bottomBarKind = when {
        session != null -> RootBottomBar.Using(session)
        state == AppState.Ready && currentSecondary is SecondaryPage.Confirm -> RootBottomBar.Confirm
        state == AppState.Ready && currentSecondary == null -> {
            RootBottomBar.Navigation(floating = themeSettings.floatingNavBar)
        }
        else -> RootBottomBar.None
    }
    val accountAccessTitle = when (authTarget) {
        AuthTarget.AddAccount -> "添加账号"
        is AuthTarget.Reauth -> "重新登录"
        AuthTarget.None -> "登录"
    }
    val ticketTitle = (currentSecondary as? SecondaryPage.Tickets)
        ?.takeIf { it.shopId.isNotBlank() }
        ?.shopName
        ?.ifBlank { "小票流水" }
        ?: "通用小票"
    val floatingNavVisible = (bottomBarKind as? RootBottomBar.Navigation)?.floating == true
    val floatingNavClearance = if (floatingNavVisible && floatingNavHeightPx > 0) {
        // Miuix 将 floatingToolbar 放在距屏幕底部 4dp 的位置。
        with(density) { floatingNavHeightPx.toDp() + 4.dp }
    } else {
        0.dp
    }

    CompositionLocalProvider(LocalSnackbar provides snackbar) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MiuixTheme.colorScheme.surface,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = {
                SnackbarHost(
                    snackbarHostState,
                    modifier = Modifier
                        .then(
                            if (bottomBarKind == RootBottomBar.None) {
                                Modifier.navigationBarsPadding()
                            } else {
                                Modifier
                            },
                        )
                        .imePadding(),
                )
            },
            topBar = {
                AnimatedContent(
                    targetState = topBarKind,
                    transitionSpec = {
                        (fadeIn(PageFadeSpec) + slideInVertically(PageSlideSpec) { -it / 10 }) togetherWith
                            (fadeOut(PageFadeSpec) + slideOutVertically(PageSlideSpec) { -it / 10 })
                    },
                    label = "rootTopBar",
                ) { bar ->
                    when (bar) {
                        RootTopBar.None -> Unit
                        RootTopBar.Using -> AppTopBar("接水状态", onBack = ::requestUsingExit)
                        RootTopBar.Confirm -> AppTopBar("确认设备", onBack = ::closeSecondary)
                        RootTopBar.Debug -> AppTopBar(
                            "诊断",
                            onBack = ::closeSecondary,
                            scrollBehavior = debugScrollBehavior,
                        )
                        RootTopBar.AccountAccess -> AppTopBar(
                            accountAccessTitle,
                            onBack = ::closeSecondary,
                        )
                        RootTopBar.Tickets -> AppTopBar(
                            ticketTitle,
                            onBack = ::closeSecondary,
                        )

                        RootTopBar.TicketRecharge -> AppTopBar(
                            "充值通用小票",
                            onBack = ::closeSecondary,
                        )

                        RootTopBar.History -> AppTopBar(
                            "接水记录",
                            scrollBehavior = historyScrollBehavior,
                        )
                        RootTopBar.Points -> AppTopBar(
                            "积分",
                            scrollBehavior = pointsScrollBehavior,
                        )
                        RootTopBar.Settings -> AppTopBar(
                            "设置",
                            scrollBehavior = settingsScrollBehavior,
                        )
                    }
                }
            },
            bottomBar = {
                AnimatedContent(
                    targetState = bottomBarKind,
                    contentKey = { it.contentKey },
                    transitionSpec = {
                        (fadeIn(PageFadeSpec) + slideInVertically(PageSlideSpec) { it / 3 }) togetherWith
                            (fadeOut(PageFadeSpec) + slideOutVertically(PageSlideSpec) { it / 3 })
                    },
                    label = "rootBottomBar",
                ) { bar ->
                    when (bar) {
                        RootBottomBar.None -> Unit
                        is RootBottomBar.Using -> UsingActionBar(
                            session = bar.session,
                            onEndRequest = { showEndSessionDialog = true },
                            onDone = waterSessionManager::clear,
                            onOpenOriginal = ::openOriginalApp,
                        )
                        RootBottomBar.Confirm -> ConfirmActionBar(
                            state = confirmState,
                            onUnlock = { confirmController?.unlock() },
                            onRetryLocation = { confirmController?.retryLocation() },
                            onBack = { secondary = null },
                        )
                        is RootBottomBar.Navigation -> {
                            if (!bar.floating) {
                                MainNavigationBar(
                                    tab = tab,
                                    floating = false,
                                    onTabSelected = { tab = it },
                                )
                            }
                        }
                    }
                }
            },
            floatingToolbar = {
                AnimatedVisibility(
                    visible = floatingNavVisible,
                    enter = fadeIn(PageFadeSpec) + slideInVertically(PageSlideSpec) { it / 3 },
                    exit = fadeOut(PageFadeSpec) + slideOutVertically(PageSlideSpec) { it / 3 },
                ) {
                    Box(modifier = Modifier.onSizeChanged { floatingNavHeightPx = it.height }) {
                        MainNavigationBar(
                            tab = tab,
                            floating = true,
                            onTabSelected = { tab = it },
                        )
                    }
                }
            },
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                AnimatedContent(
                    targetState = destination,
                    contentKey = { it.contentKey },
                    transitionSpec = { rootTransition(initialState, targetState) },
                    modifier = Modifier.fillMaxSize(),
                    label = "rootContent",
                ) { dest ->
                    when (dest) {
                        is RootDestination.Using -> UsingScreen(session = dest.session)

                        RootDestination.Loading -> LoadingScreen()

                        RootDestination.Disclaimer -> DisclaimerScreen(
                            onAccept = controller::acceptDisclaimer,
                        )

                        RootDestination.Login -> LoginScreen(
                            busy = busy,
                            codeSent = codeSent,
                            onSendCode = controller::sendCode,
                            onLogin = controller::login,
                            onImportToken = controller::importToken,
                            accounts = accounts,
                            activeLocalId = activeAccount?.localId,
                            target = authTarget,
                            prefillPhone = reauthPhone,
                            onSelectAccount = { controller.switchAccount(it.localId) },
                            onRequestReauth = { controller.beginReauth(it.localId) },
                            onCancel = if (authTarget is AuthTarget.Reauth) {
                                { controller.cancelAuth() }
                            } else {
                                null
                            },
                        )

                        is RootDestination.Tickets -> TicketsScreen(
                            assetController = assetController,
                            shopId = dest.shopId,
                            shopName = dest.shopName,
                            onSelectShop = { ticket ->
                                secondary = SecondaryPage.Tickets(ticket.shopId, ticket.shopName)
                            },
                            onRecharge = { shopId, shopName ->
                                secondary = SecondaryPage.TicketRecharge(shopId, shopName)
                            },
                        )

                        is RootDestination.TicketRecharge -> TicketRechargeScreen(
                            controller = ticketRechargeController,
                            shopId = dest.shopId,
                            shopName = dest.shopName,
                            onClose = ::closeSecondary,
                        )

                        is RootDestination.AccountAccess -> LoginScreen(
                            busy = busy,
                            codeSent = codeSent,
                            onSendCode = controller::sendCode,
                            onLogin = controller::login,
                            onImportToken = controller::importToken,
                            accounts = accounts,
                            activeLocalId = activeAccount?.localId,
                            target = dest.target,
                            prefillPhone = reauthPhone,
                            onSelectAccount = { controller.switchAccount(it.localId) },
                            onRequestReauth = { controller.beginReauth(it.localId) },
                            onCancel = {
                                controller.cancelAuth()
                                secondary = null
                            },
                            topInsetHandled = true,
                        )

                        is RootDestination.Confirm -> ConfirmScreen(
                            device = dest.device,
                            state = dest.state,
                            onSelectSku = { confirmController?.selectSku(it) },
                            onSelectChannel = { confirmController?.selectChannel(it) },
                            onRetryLocation = { confirmController?.retryLocation() },
                            onBack = { secondary = null },
                        )

                        RootDestination.Debug -> DebugScreen(
                            settingsStore = settingsStore,
                            nfcTagStore = nfcTagStore,
                            scanController = scanController,
                            onImportToken = controller::importToken,
                            scrollBehavior = debugScrollBehavior,
                        )

                        RootDestination.History -> HistoryScreen(
                            controller = historyController,
                            accountId = activeAccount?.localId,
                            scrollBehavior = historyScrollBehavior,
                            bottomContentPadding = floatingNavClearance,
                        )

                        RootDestination.Points -> PointsScreen(
                            controller = pointsController,
                            account = activeAccount,
                            scrollBehavior = pointsScrollBehavior,
                            bottomContentPadding = floatingNavClearance,
                        )

                        RootDestination.Settings -> SettingsScreen(
                            settingsStore = settingsStore,
                            historyRepository = historyRepository,
                            assetController = assetController,
                            accounts = accounts,
                            activeAccount = activeAccount,
                            onOpenDebug = { secondary = SecondaryPage.Debug },
                            onOpenTickets = { secondary = SecondaryPage.Tickets() },
                            onAddAccount = {
                                controller.beginAddAccount()
                                secondary = SecondaryPage.AccountAccess(AuthTarget.AddAccount)
                            },
                            onSwitchAccount = controller::switchAccount,
                            onReauthAccount = { localId ->
                                controller.beginReauth(localId)
                                secondary = SecondaryPage.AccountAccess(AuthTarget.Reauth(localId))
                            },
                            onRenameAccount = controller::renameAccount,
                            onDeleteAccount = controller::deleteAccount,
                            onLogout = controller::logoutCurrent,
                            scrollBehavior = settingsScrollBehavior,
                            bottomContentPadding = floatingNavClearance,
                        )

                        RootDestination.Scan -> ScanScreen(
                            controller = scanController,
                            onDeviceReady = { secondary = SecondaryPage.Confirm(it) },
                            skipDeviceInfo = skipDeviceInfo,
                            autoStartCamera = autoStartCamera,
                            cameraSessionStarted = cameraSessionStarted,
                            onCameraStarted = { cameraSessionStarted = true },
                            bottomContentPadding = floatingNavClearance,
                            onWarmUpLocation = onWarmUpLocation,
                        )
                    }
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

private sealed interface RootDestination {
    val contentKey: Any
        get() = this

    val tabOrder: Int?
        get() = null

    data object Loading : RootDestination
    data object Disclaimer : RootDestination
    data object Login : RootDestination
    data object Scan : RootDestination {
        override val tabOrder: Int = 0
    }
    data object History : RootDestination {
        override val tabOrder: Int = 1
    }
    data object Points : RootDestination {
        override val tabOrder: Int = 2
    }
    data object Settings : RootDestination {
        override val tabOrder: Int = 3
    }
    data object Debug : RootDestination
    data class AccountAccess(val target: AuthTarget) : RootDestination {
        override val contentKey: Any = "AccountAccess"
    }

    data class Tickets(val shopId: String, val shopName: String) : RootDestination {
        override val contentKey: Any = "Tickets-$shopId"
    }

    data class TicketRecharge(val shopId: String, val shopName: String) : RootDestination {
        override val contentKey: Any = "TicketRecharge-$shopId"
    }

    data class Confirm(val device: ResolvedDevice, val state: ConfirmState) : RootDestination {
        override val contentKey: Any = "Confirm"
    }
    data class Using(val session: WaterSession) : RootDestination {
        override val contentKey: Any = "Using"
    }
}

private sealed interface RootTopBar {
    data object None : RootTopBar
    data object Using : RootTopBar
    data object Confirm : RootTopBar
    data object Debug : RootTopBar
    data object AccountAccess : RootTopBar
    data object Tickets : RootTopBar
    data object TicketRecharge : RootTopBar
    data object History : RootTopBar
    data object Points : RootTopBar
    data object Settings : RootTopBar
}

private sealed interface RootBottomBar {
    val contentKey: Any
        get() = this

    data object None : RootBottomBar
    data class Using(val session: WaterSession) : RootBottomBar {
        override val contentKey: Any = "Using"
    }
    data object Confirm : RootBottomBar
    data class Navigation(val floating: Boolean) : RootBottomBar {
        override val contentKey: Any = "Navigation-$floating"
    }
}

private fun rootTransition(
    initial: RootDestination,
    target: RootDestination,
): ContentTransform {
    if (target is RootDestination.Using || initial is RootDestination.Using) {
        return (fadeIn(PageFadeSpec) + slideInVertically(PageSlideSpec) { it / 5 }) togetherWith
            (fadeOut(PageFadeSpec) + slideOutVertically(PageSlideSpec) { -it / 8 })
    }
    if ((initial is RootDestination.TicketRecharge && target is RootDestination.Tickets) ||
        (initial is RootDestination.Tickets && target is RootDestination.TicketRecharge)
    ) {
        return if (target is RootDestination.TicketRecharge) {
            (fadeIn(PageFadeSpec) + slideInHorizontally(PageSlideSpec) { it / 4 }) togetherWith
                (fadeOut(PageFadeSpec) + slideOutHorizontally(PageSlideSpec) { -it / 10 })
        } else {
            (fadeIn(PageFadeSpec) + slideInHorizontally(PageSlideSpec) { -it / 10 }) togetherWith
                (fadeOut(PageFadeSpec) + slideOutHorizontally(PageSlideSpec) { it / 4 })
        }
    }
    if (initial is RootDestination.Tickets && target is RootDestination.Tickets) {
        val forward = initial.shopId.isBlank() && target.shopId.isNotBlank()
        return if (forward) {
            (fadeIn(PageFadeSpec) + slideInHorizontally(PageSlideSpec) { it / 4 }) togetherWith
                (fadeOut(PageFadeSpec) + slideOutHorizontally(PageSlideSpec) { -it / 10 })
        } else {
            (fadeIn(PageFadeSpec) + slideInHorizontally(PageSlideSpec) { -it / 10 }) togetherWith
                (fadeOut(PageFadeSpec) + slideOutHorizontally(PageSlideSpec) { it / 4 })
        }
    }
    if (target is RootDestination.Confirm ||
        target is RootDestination.Debug ||
        target is RootDestination.AccountAccess ||
        target is RootDestination.Tickets ||
        target is RootDestination.TicketRecharge
    ) {
        return (fadeIn(PageFadeSpec) + slideInHorizontally(PageSlideSpec) { it / 4 }) togetherWith
            (fadeOut(PageFadeSpec) + slideOutHorizontally(PageSlideSpec) { -it / 10 })
    }
    if (initial is RootDestination.Confirm ||
        initial is RootDestination.Debug ||
        initial is RootDestination.AccountAccess ||
        initial is RootDestination.Tickets ||
        initial is RootDestination.TicketRecharge
    ) {
        return (fadeIn(PageFadeSpec) + slideInHorizontally(PageSlideSpec) { -it / 10 }) togetherWith
            (fadeOut(PageFadeSpec) + slideOutHorizontally(PageSlideSpec) { it / 4 })
    }
    val initialOrder = initial.tabOrder
    val targetOrder = target.tabOrder
    if (initialOrder != null && targetOrder != null && initialOrder != targetOrder) {
        val direction = if (targetOrder > initialOrder) 1 else -1
        return (fadeIn(PageFadeSpec) + slideInHorizontally(PageSlideSpec) { it / 6 * direction }) togetherWith
            (fadeOut(PageFadeSpec) + slideOutHorizontally(PageSlideSpec) { -it / 10 * direction })
    }
    return fadeIn(PageFadeSpec) togetherWith fadeOut(PageFadeSpec)
}

@Composable
private fun MainNavigationBar(
    tab: MainTab,
    floating: Boolean,
    onTabSelected: (MainTab) -> Unit,
) {
    if (floating) {
        FloatingNavigationBar(
            color = Color.Transparent,
            showDivider = true,
            shadowElevation = 0.dp,
        ) {
            MainTab.entries.forEach { item ->
                FloatingNavigationBarItem(
                    selected = tab == item,
                    onClick = { onTabSelected(item) },
                    icon = item.icon,
                    label = item.label,
                )
            }
        }
    } else {
        NavigationBar(mode = NavigationBarDisplayMode.IconWithSelectedLabel) {
            MainTab.entries.forEach { item ->
                NavigationBarItem(
                    selected = tab == item,
                    onClick = { onTabSelected(item) },
                    icon = item.icon,
                    label = item.label,
                )
            }
        }
    }
}

@Composable
private fun ConfirmActionBar(
    state: ConfirmState,
    onUnlock: () -> Unit,
    onRetryLocation: () -> Unit,
    onBack: () -> Unit,
) {
    BottomActionBar {
        AnimatedContent(
            targetState = state,
            contentKey = { it.actionKey },
            transitionSpec = {
                (fadeIn(ContentFadeSpec) + slideInVertically(ContentSlideSpec) { it / 2 }) togetherWith
                    (fadeOut(ContentFadeSpec) + slideOutVertically(ContentSlideSpec) { -it / 2 })
            },
            label = "confirmActionBar",
        ) { current ->
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                when (current) {
                    is ConfirmState.Ready -> {
                        val ready = current.value
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
                            onClick = if (ready.locationPhase == LocationPhase.Failed && !ready.dryRun) {
                                onRetryLocation
                            } else {
                                onUnlock
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = ready.dryRun || ready.locationPhase != LocationPhase.Fetching,
                            colors = ButtonDefaults.buttonColorsPrimary(),
                        ) {
                            Text(
                                when {
                                    ready.dryRun -> "演练解锁"
                                    ready.locationPhase == LocationPhase.Fetching -> "正在获取定位…"
                                    ready.locationPhase == LocationPhase.Failed -> "重试定位"
                                    else -> "开始使用"
                                },
                            )
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
        AnimatedContent(
            targetState = session,
            contentKey = { it.actionKey },
            transitionSpec = {
                (fadeIn(ContentFadeSpec) + slideInVertically(ContentSlideSpec) { it / 2 }) togetherWith
                    (fadeOut(ContentFadeSpec) + slideOutVertically(ContentSlideSpec) { -it / 2 })
            },
            label = "usingActionBar",
        ) { current ->
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                when {
                    current.phase in SessionPhase.TERMINAL -> {
                        if (current.phase == SessionPhase.PAY_UNKNOWN || current.phase == SessionPhase.FAULT) {
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

                    current.phase == SessionPhase.USING && current.supportAppUserFinish -> {
                        Button(
                            onClick = onEndRequest,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColorsPrimary(),
                        ) {
                            Text("结束使用")
                        }
                    }

                    current.phase == SessionPhase.USING -> {
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
                            Text("${sessionPhaseLabel(current.phase)}…")
                        }
                    }
                }
            }
        }
    }
}

private val ConfirmState.actionKey: Int
    get() = when (this) {
        ConfirmState.Loading -> 0
        ConfirmState.Unlocking -> 1
        is ConfirmState.Ready -> 2
        else -> 3
    }

private val WaterSession.actionKey: Int
    get() = when {
        phase in SessionPhase.TERMINAL -> 0
        phase == SessionPhase.USING && supportAppUserFinish -> 1
        phase == SessionPhase.USING -> 2
        else -> 3
    }

private const val ORIGINAL_PACKAGE = "com.qiekj.user"
