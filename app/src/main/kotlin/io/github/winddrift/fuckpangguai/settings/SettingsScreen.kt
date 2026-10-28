package io.github.winddrift.fuckpangguai.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.winddrift.fuckpangguai.account.AssetController
import io.github.winddrift.fuckpangguai.account.StoredAccount
import io.github.winddrift.fuckpangguai.history.HistoryRepository
import io.github.winddrift.fuckpangguai.history.toCsv
import io.github.winddrift.fuckpangguai.nfc.NfcAvailability
import io.github.winddrift.fuckpangguai.nfc.rememberNfcAvailability
import io.github.winddrift.fuckpangguai.storage.AppSettingsStore
import io.github.winddrift.fuckpangguai.storage.AppThemeMode
import io.github.winddrift.fuckpangguai.storage.ThemeSettings
import io.github.winddrift.fuckpangguai.ui.components.ConfirmDialog
import io.github.winddrift.fuckpangguai.ui.components.LocalSnackbar
import io.github.winddrift.fuckpangguai.ui.components.PreferenceDivider
import io.github.winddrift.fuckpangguai.ui.components.PreferenceGroup
import io.github.winddrift.fuckpangguai.water.formatConversionRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.icon.extended.Import
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.Promotions
import top.yukonga.miuix.kmp.icon.extended.SearchDevice
import top.yukonga.miuix.kmp.icon.extended.Theme
import top.yukonga.miuix.kmp.icon.extended.Tune
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference

@Composable
fun SettingsScreen(
    settingsStore: AppSettingsStore,
    historyRepository: HistoryRepository,
    assetController: AssetController,
    accounts: List<StoredAccount>,
    activeAccount: StoredAccount?,
    onOpenAbout: () -> Unit,
    onOpenNfcTags: () -> Unit,
    onOpenTickets: () -> Unit,
    onAddAccount: () -> Unit,
    onSwitchAccount: (String) -> Unit,
    onReauthAccount: (String) -> Unit,
    onRenameAccount: (String, String) -> Unit,
    onDeleteAccount: (String) -> Unit,
    onLogout: (Boolean) -> Unit,
    onHistoryImported: () -> Unit,
    scrollBehavior: ScrollBehavior? = null,
    bottomContentPadding: Dp = 0.dp,
) {
    val context = LocalContext.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val nfc = rememberNfcAvailability()
    val themeSettings by settingsStore.themeSettings
        .collectAsStateWithLifecycle(initialValue = ThemeSettings())
    val skipDeviceInfo by settingsStore.skipDeviceInfo
        .collectAsStateWithLifecycle(initialValue = false)
    val autoStartCamera by settingsStore.autoStartCamera
        .collectAsStateWithLifecycle(initialValue = true)
    val waterConversion by settingsStore.waterConversion
        .collectAsStateWithLifecycle(initialValue = null)
    val assetState by assetController.state.collectAsStateWithLifecycle()
    val assetMatchesActive = assetState.accountId == activeAccount?.localId
    var showSwitchSheet by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        assetController.refresh()
    }

    var oneTap by remember { mutableStateOf(false) }
    var dryRun by remember { mutableStateOf(true) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var showLogoutConfirm by remember { mutableStateOf(false) }
    var showLogoutOptions by remember { mutableStateOf(false) }
    var showWaterConversion by remember { mutableStateOf(false) }
    var logoutRecordCount by remember { mutableStateOf(0) }
    var logoutExported by remember { mutableStateOf(false) }
    var pendingCsv by remember { mutableStateOf<String?>(null) }
    var pendingExportForLogout by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        oneTap = settingsStore.isOneTapEnabled()
        dryRun = settingsStore.isDryRun()
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri ->
        val csv = pendingCsv
        val forLogout = pendingExportForLogout
        pendingCsv = null
        pendingExportForLogout = false
        if (uri == null || csv == null) {
            if (forLogout) showLogoutOptions = true
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        output.write(("\uFEFF" + csv).toByteArray(Charsets.UTF_8))
                    } ?: error("无法打开输出文件")
                }.isSuccess
            }
            snackbar.show(
                if (ok) "CSV 已导出" else "CSV 导出失败",
                isError = !ok,
            )
            if (forLogout) {
                logoutExported = ok
                showLogoutOptions = true
            }
        }
    }

    fun exportCsv(returnToLogout: Boolean = false) {
        scope.launch {
            val records = runCatching { historyRepository.list() }.getOrElse {
                snackbar.show("读取记录失败", isError = true)
                if (returnToLogout) showLogoutOptions = true
                return@launch
            }
            if (records.isEmpty()) {
                snackbar.show("还没有可导出的记录")
                if (returnToLogout) showLogoutOptions = true
                return@launch
            }
            pendingCsv = records.toCsv()
            pendingExportForLogout = returnToLogout
            exportLauncher.launch("pangguai-history.csv")
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val csv = withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
                        input.readBytes()
                    } ?: error("无法读取文件")
                    if (bytes.size > MAX_IMPORT_BYTES) error("文件超过 8 MB，无法导入")
                    String(bytes, Charsets.UTF_8)
                }
            }
            csv.onFailure { throwable ->
                snackbar.show(throwable.message ?: "读取文件失败", isError = true)
            }
            csv.onSuccess { content ->
                runCatching { historyRepository.importCsv(content) }
                    .onSuccess { result ->
                        when {
                            result.formatError -> snackbar.show(
                                "无法识别该 CSV 文件",
                                isError = true,
                            )

                            result.inserted == 0 &&
                                result.duplicates == 0 &&
                                result.invalid == 0 -> snackbar.show("CSV 中没有可导入的记录")

                            else -> {
                                snackbar.show(
                                    "导入完成：新增 ${result.inserted} 条，" +
                                        "跳过重复 ${result.duplicates} 条，" +
                                        "失败 ${result.invalid} 条",
                                    isError = result.inserted == 0 && result.invalid > 0,
                                )
                                if (result.inserted > 0) onHistoryImported()
                            }
                        }
                    }
                    .onFailure { throwable ->
                        snackbar.show(throwable.message ?: "导入失败", isError = true)
                    }
            }
        }
    }

    fun requestLogout() {
        scope.launch {
            val count = runCatching { historyRepository.count() }.getOrElse {
                snackbar.show("读取接水记录失败", isError = true)
                return@launch
            }
            if (count > 0) {
                logoutRecordCount = count
                logoutExported = false
                showLogoutOptions = true
            } else {
                showLogoutConfirm = true
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (scrollBehavior != null) {
                    Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
                } else {
                    Modifier
                },
            ),
        contentPadding = PaddingValues(
            start = 16.dp,
            top = 12.dp,
            end = 16.dp,
            bottom = 12.dp + bottomContentPadding,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            AccountAssetCard(
                account = activeAccount,
                snapshot = assetState.snapshot.takeIf { assetMatchesActive },
                loading = assetState.loading,
                error = assetState.error.takeIf { assetMatchesActive },
                hidden = assetState.hidden,
                onRefresh = assetController::refresh,
                onToggleHidden = assetController::toggleHidden,
                onSwitchAccount = { showSwitchSheet = true },
                onReauth = activeAccount?.let { account ->
                    { onReauthAccount(account.localId) }
                },
            )
        }

        item {
            PreferenceGroup(title = "外观") {
                OverlayDropdownPreference(
                    items = listOf("跟随系统", "浅色", "深色"),
                    selectedIndex = themeSettings.mode.ordinal,
                    title = "主题",
                    summary = "选择浅色或深色外观",
                    startAction = {
                        Icon(
                            imageVector = MiuixIcons.Theme,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 12.dp),
                        )
                    },
                    onSelectedIndexChange = { index ->
                        scope.launch {
                            settingsStore.setThemeMode(AppThemeMode.entries[index])
                        }
                    },
                )
                PreferenceDivider()
                SwitchPreference(
                    checked = themeSettings.dynamicColor,
                    onCheckedChange = { enabled ->
                        scope.launch { settingsStore.setDynamicColor(enabled) }
                    },
                    title = "动态取色",
                    summary = "跟随系统壁纸（Monet），关闭时使用 Miuix 默认蓝",
                )
                PreferenceDivider()
                SwitchPreference(
                    checked = themeSettings.floatingNavBar,
                    onCheckedChange = { enabled ->
                        scope.launch { settingsStore.setFloatingNavBar(enabled) }
                    },
                    title = "悬浮底栏",
                    summary = "底部导航以悬浮胶囊显示",
                )
            }
        }

        item {
            PreferenceGroup(title = "使用") {
                SwitchPreference(
                    checked = oneTap,
                    onCheckedChange = { checked ->
                        oneTap = checked
                        scope.launch { settingsStore.setOneTapEnabled(checked) }
                    },
                    title = "一键解锁",
                    summary = "同一设备首次确认后，后续扫码直接解锁",
                )
                PreferenceDivider()
                SwitchPreference(
                    checked = skipDeviceInfo,
                    onCheckedChange = { enabled ->
                        scope.launch { settingsStore.setSkipDeviceInfo(enabled) }
                    },
                    title = "跳过设备信息",
                    summary = "识别成功后直接进入确认设备页，可配合一键解锁实现扫码即开",
                )
                PreferenceDivider()
                ArrowPreference(
                    title = "水量换算",
                    summary = waterConversion?.let(::formatConversionRule)
                        ?: "未设置，用量保持原样",
                    startAction = {
                        Icon(
                            imageVector = MiuixIcons.Tune,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 12.dp),
                        )
                    },
                    onClick = { showWaterConversion = true },
                )
                if (nfc != NfcAvailability.Unsupported) {
                    PreferenceDivider()
                    ArrowPreference(
                        title = "管理 NFC 标签",
                        summary = if (nfc == NfcAvailability.Disabled) {
                            "NFC 未开启，仍可管理已绑定标签"
                        } else {
                            "查看和删除本机的标签绑定"
                        },
                        startAction = {
                            Icon(
                                imageVector = MiuixIcons.SearchDevice,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 12.dp),
                            )
                        },
                        onClick = onOpenNfcTags,
                    )
                }
                PreferenceDivider()
                SwitchPreference(
                    checked = !autoStartCamera,
                    onCheckedChange = { notAuto ->
                        scope.launch { settingsStore.setAutoStartCamera(!notAuto) }
                    },
                    title = "不自动打开相机",
                    summary = "打开后进入扫码页先显示待机界面，不自动启动相机，可提升冷启动速度；推荐同时开启系统 NFC",
                )
                PreferenceDivider()
                SwitchPreference(
                    checked = dryRun,
                    onCheckedChange = { checked ->
                        dryRun = checked
                        scope.launch { settingsStore.setDryRun(checked) }
                    },
                    title = "演练模式",
                    summary = "只展示请求，不真实调用 water/unlock",
                )
            }
        }

        item {
            PreferenceGroup(title = "数据") {
                ArrowPreference(
                    title = "导出 CSV",
                    summary = "将当前账号的全部接水记录保存为 CSV 文件",
                    startAction = {
                        Icon(
                            imageVector = MiuixIcons.Download,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 12.dp),
                        )
                    },
                    onClick = { exportCsv() },
                )
                PreferenceDivider()
                ArrowPreference(
                    title = "导入 CSV",
                    summary = "从本应用导出的 CSV 恢复接水记录，重复订单号会自动跳过",
                    startAction = {
                        Icon(
                            imageVector = MiuixIcons.Import,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 12.dp),
                        )
                    },
                    onClick = {
                        importLauncher.launch(
                            arrayOf(
                                "text/*",
                                "application/csv",
                                "application/vnd.ms-excel",
                            ),
                        )
                    },
                )
                PreferenceDivider()
                ArrowPreference(
                    title = "清空接水历史",
                    summary = "删除当前账号在本机的全部接水记录",
                    startAction = {
                        Icon(
                            imageVector = MiuixIcons.Delete,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 12.dp),
                        )
                    },
                    onClick = { showClearConfirm = true },
                )
            }
        }

        item {
            PreferenceGroup(title = "账号") {
                ArrowPreference(
                    title = "通用小票明细",
                    summary = "按店铺查看通用小票，点进店铺查看流水",
                    startAction = {
                        Icon(
                            imageVector = MiuixIcons.Promotions,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 12.dp),
                        )
                    },
                    onClick = onOpenTickets,
                )
                PreferenceDivider()
                ArrowPreference(
                    title = "添加账号",
                    summary = "登录另一个胖乖生活账号，当前账号会保留",
                    onClick = onAddAccount,
                )
                PreferenceDivider()
                ArrowPreference(
                    title = "退出当前账号",
                    summary = "退出前可保留或导出接水记录；若还有其他账号会自动切换",
                    onClick = ::requestLogout,
                )
            }
        }

        item {
            PreferenceGroup(title = "其他") {
                ArrowPreference(
                    title = "关于",
                    summary = "版本、开发者、开源许可与更新",
                    startAction = {
                        Icon(
                            imageVector = MiuixIcons.Info,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 12.dp),
                        )
                    },
                    onClick = onOpenAbout,
                )
            }
        }
    }

    ConfirmDialog(
        show = showClearConfirm,
        title = "清空接水历史？",
        summary = "当前账号在本机保存的全部接水记录将被删除，此操作无法撤销。",
        confirmText = "清空",
        destructive = true,
        onConfirm = {
            scope.launch {
                runCatching { historyRepository.clear() }
                    .onSuccess { snackbar.show("接水历史已清空") }
                    .onFailure { snackbar.show("清空失败", isError = true) }
            }
            showClearConfirm = false
        },
        onDismiss = { showClearConfirm = false },
    )

    ConfirmDialog(
        show = showLogoutConfirm,
        title = "退出当前账号？",
        summary = "本机保存的该账号 token、资料与资产缓存会被删除；若还有其他有效账号会自动切换。",
        confirmText = "退出",
        destructive = true,
        onConfirm = {
            showLogoutConfirm = false
            onLogout(false)
        },
        onDismiss = { showLogoutConfirm = false },
    )

    LogoutOptionsDialog(
        show = showLogoutOptions,
        recordCount = logoutRecordCount,
        exported = logoutExported,
        onKeepAndLogout = {
            showLogoutOptions = false
            onLogout(true)
        },
        onExport = {
            showLogoutOptions = false
            exportCsv(returnToLogout = true)
        },
        onDeleteAndLogout = {
            showLogoutOptions = false
            onLogout(false)
        },
        onDismiss = { showLogoutOptions = false },
    )

    AccountSwitchSheet(
        show = showSwitchSheet,
        accounts = accounts,
        activeLocalId = activeAccount?.localId,
        onDismiss = { showSwitchSheet = false },
        onSwitch = onSwitchAccount,
        onAdd = onAddAccount,
        onReauth = onReauthAccount,
        onRename = onRenameAccount,
        onDelete = onDeleteAccount,
    )

    WaterConversionSheet(
        show = showWaterConversion,
        conversion = waterConversion,
        onDismiss = { showWaterConversion = false },
        onSave = { conversion ->
            showWaterConversion = false
            scope.launch {
                runCatching { settingsStore.setWaterConversion(conversion) }
                    .onSuccess { snackbar.show("水量换算已保存") }
                    .onFailure { snackbar.show("保存失败，请重试", isError = true) }
            }
        },
        onClear = {
            showWaterConversion = false
            scope.launch {
                runCatching { settingsStore.setWaterConversion(null) }
                    .onSuccess { snackbar.show("已恢复原始用量显示") }
                    .onFailure { snackbar.show("清除失败，请重试", isError = true) }
            }
        },
    )
}

private const val MAX_IMPORT_BYTES = 8 * 1024 * 1024
