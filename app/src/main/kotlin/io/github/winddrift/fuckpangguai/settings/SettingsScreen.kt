package io.github.winddrift.fuckpangguai.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.winddrift.fuckpangguai.BuildConfig
import io.github.winddrift.fuckpangguai.history.HistoryRepository
import io.github.winddrift.fuckpangguai.storage.AppSettingsStore
import io.github.winddrift.fuckpangguai.storage.AppThemeMode
import io.github.winddrift.fuckpangguai.storage.ThemeSettings
import io.github.winddrift.fuckpangguai.ui.components.ConfirmDialog
import io.github.winddrift.fuckpangguai.ui.components.LocalSnackbar
import io.github.winddrift.fuckpangguai.ui.components.PreferenceDivider
import io.github.winddrift.fuckpangguai.ui.components.PreferenceGroup
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Theme
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun SettingsScreen(
    settingsStore: AppSettingsStore,
    historyRepository: HistoryRepository,
    onOpenDebug: () -> Unit,
    onLogout: () -> Unit,
) {
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val colors = MiuixTheme.colorScheme
    val themeSettings by settingsStore.themeSettings
        .collectAsStateWithLifecycle(initialValue = ThemeSettings())

    var oneTap by remember { mutableStateOf(false) }
    var dryRun by remember { mutableStateOf(true) }
    var versionTaps by remember { mutableIntStateOf(0) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var showLogoutConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        oneTap = settingsStore.isOneTapEnabled()
        dryRun = settingsStore.isDryRun()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
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
                    title = "清空接水历史",
                    summary = "删除本机全部接水记录",
                    onClick = { showClearConfirm = true },
                )
            }
        }

        item {
            PreferenceGroup(title = "账号") {
                ArrowPreference(
                    title = "退出登录",
                    summary = "退出后需重新登录才能扫码接水",
                    onClick = { showLogoutConfirm = true },
                )
            }
        }

        item {
            PreferenceGroup(title = "关于") {
                BasicComponent(
                    title = "版本",
                    summary = "${BuildConfig.VERSION_NAME}（${BuildConfig.VERSION_CODE}）",
                    onClick = {
                        versionTaps += 1
                        if (versionTaps >= 7) {
                            versionTaps = 0
                            onOpenDebug()
                        }
                    },
                )
                PreferenceDivider()
                Text(
                    text = "非官方客户端，与胖乖生活、学校及设备运营方无关。",
                    modifier = Modifier.padding(16.dp),
                    style = MiuixTheme.textStyles.footnote1,
                    color = colors.onSurfaceSecondary,
                )
            }
        }
    }

    ConfirmDialog(
        show = showClearConfirm,
        title = "清空接水历史？",
        summary = "本机保存的全部接水记录将被删除，此操作无法撤销。",
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
        title = "退出登录？",
        summary = "退出后需要重新登录才能扫码接水。",
        confirmText = "退出",
        destructive = true,
        onConfirm = {
            showLogoutConfirm = false
            onLogout()
        },
        onDismiss = { showLogoutConfirm = false },
    )
}
