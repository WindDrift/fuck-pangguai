package io.github.winddrift.fuckpangguai.debug

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.winddrift.fuckpangguai.BuildConfig
import io.github.winddrift.fuckpangguai.net.RequestLog
import io.github.winddrift.fuckpangguai.net.dto.GoodsDetail
import io.github.winddrift.fuckpangguai.nfc.NfcTagBinding
import io.github.winddrift.fuckpangguai.nfc.NfcTagStore
import io.github.winddrift.fuckpangguai.scan.ScanController
import io.github.winddrift.fuckpangguai.scan.ScanState
import io.github.winddrift.fuckpangguai.storage.AppSettingsStore
import io.github.winddrift.fuckpangguai.ui.components.ConfirmDialog
import io.github.winddrift.fuckpangguai.ui.components.KeyValueRow
import io.github.winddrift.fuckpangguai.ui.components.LocalSnackbar
import io.github.winddrift.fuckpangguai.ui.components.PreferenceDivider
import io.github.winddrift.fuckpangguai.ui.components.PreferenceGroup
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Copy
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun DebugScreen(
    settingsStore: AppSettingsStore,
    nfcTagStore: NfcTagStore,
    scanController: ScanController,
    onImportToken: (String) -> Unit,
) {
    val context = LocalContext.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val entries by RequestLog.entries.collectAsStateWithLifecycle()
    val scanState by scanController.state.collectAsStateWithLifecycle()
    var mappings by remember { mutableStateOf<List<NfcTagBinding>>(emptyList()) }
    var dryRun by remember { mutableStateOf(true) }
    var pendingMapping by remember { mutableStateOf<NfcTagBinding?>(null) }

    LaunchedEffect(Unit) {
        mappings = nfcTagStore.load()
        dryRun = settingsStore.isDryRun()
    }

    fun copyToClipboard(label: String, text: String) {
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        clipboard?.setPrimaryClip(ClipData.newPlainText(label, text))
        snackbar.show("$label 已复制")
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                text = "仅用于调试；Release 不记录完整请求。",
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceSecondary,
            )
        }

        item {
            PreferenceGroup(title = "调试") {
                SwitchPreference(
                    checked = dryRun,
                    onCheckedChange = { checked ->
                        dryRun = checked
                        scope.launch { settingsStore.setDryRun(checked) }
                    },
                    title = "演练模式",
                    summary = "只展示请求，不真实调用 water/unlock",
                )
                if (BuildConfig.DEBUG) {
                    PreferenceDivider()
                    Column(modifier = Modifier.padding(16.dp)) {
                        DebugTokenImportSection(onToken = onImportToken)
                    }
                }
            }
        }

        item {
            val ready = scanState as? ScanState.DeviceReady
            PreferenceGroup(title = "设备探测") {
                if (ready == null) {
                    BasicComponent(
                        title = "暂无设备",
                        summary = "先在扫码页识别一台设备，再回到这里查看详情。",
                    )
                } else {
                    val detail = ready.device.detail
                    BasicComponent(
                        title = detail.name.ifBlank { "饮水机" },
                        summary = "类别 ${ready.device.scan.categoryCode}",
                    )
                    PreferenceDivider()
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        KeyValueRow("goodsId", ready.device.scan.id)
                        KeyValueRow("isBluetooth", detail.isBluetooth.toString())
                        KeyValueRow("deviceErrorCode", detail.deviceErrorCode ?: "无")
                        KeyValueRow("payment", detail.payment)
                        KeyValueRow("payModel", detail.payModel.toString())
                        KeyValueRow("tradeMain", detail.tradeMain.toString())
                        KeyValueRow("orgId", detail.orgId)
                        KeyValueRow("shopId", detail.shopId)
                        KeyValueRow("payTypes", detail.payTypes.joinToString())
                        KeyValueRow("extAttr", detail.extAttr)
                    }
                    PreferenceDivider()
                    BasicComponent(
                        title = "复制设备 JSON",
                        summary = "包含当前设备的完整探测字段",
                        endActions = {
                            Icon(
                                imageVector = MiuixIcons.Copy,
                                contentDescription = null,
                                tint = MiuixTheme.colorScheme.onSurfaceSecondary,
                            )
                        },
                        onClick = {
                            copyToClipboard(
                                "设备 JSON",
                                Json.encodeToString(GoodsDetail.serializer(), detail),
                            )
                        },
                    )
                }
            }
        }

        item {
            PreferenceGroup(title = "NFC 标签映射") {
                if (mappings.isEmpty()) {
                    BasicComponent(
                        title = "暂无绑定",
                        summary = "在扫码页碰一下 NFC 标签，再扫描设备码即可绑定。",
                    )
                } else {
                    mappings.forEachIndexed { index, mapping ->
                        if (index > 0) PreferenceDivider()
                        BasicComponent(
                            title = mapping.tagId,
                            summary = "${mapping.sn} · ${mapping.deviceName.ifBlank { "饮水机" }}",
                            endActions = {
                                IconButton(
                                    onClick = { pendingMapping = mapping },
                                    backgroundColor = Color.Transparent,
                                ) {
                                    Icon(
                                        imageVector = MiuixIcons.Delete,
                                        contentDescription = "删除该映射",
                                    )
                                }
                            },
                        )
                    }
                }
            }
        }

        item {
            PreferenceGroup(title = "最近请求") {
                if (entries.isEmpty()) {
                    BasicComponent(
                        title = "暂无请求",
                        summary = "token / lat / lng 会自动脱敏。",
                    )
                } else {
                    entries.take(10).forEachIndexed { index, entry ->
                        if (index > 0) PreferenceDivider()
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Text(
                                text = "${entry.method} ${entry.statusCode ?: "-"} · ${entry.durationMs}ms",
                                style = MiuixTheme.textStyles.body2,
                            )
                            Text(
                                text = entry.url,
                                style = MiuixTheme.textStyles.footnote1,
                                color = MiuixTheme.colorScheme.onSurfaceSecondary,
                            )
                            entry.error?.let { error ->
                                Text(
                                    text = "error: $error",
                                    style = MiuixTheme.textStyles.footnote1,
                                    color = MiuixTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                    PreferenceDivider()
                    val latest = entries.firstOrNull()
                    BasicComponent(
                        title = "复制最近请求 cURL",
                        enabled = latest != null,
                        endActions = {
                            Icon(
                                imageVector = MiuixIcons.Copy,
                                contentDescription = null,
                                tint = MiuixTheme.colorScheme.onSurfaceSecondary,
                            )
                        },
                        onClick = latest?.let { entry ->
                            { copyToClipboard("cURL", entry.toCurl()) }
                        },
                    )
                    PreferenceDivider()
                    BasicComponent(
                        title = "清空请求日志",
                        enabled = entries.isNotEmpty(),
                        onClick = {
                            RequestLog.clear()
                            snackbar.show("请求日志已清空")
                        },
                    )
                }
            }
        }
    }

    ConfirmDialog(
        show = pendingMapping != null,
        title = "删除该 NFC 映射？",
        summary = pendingMapping?.let { "${it.tagId} → ${it.sn}" },
        confirmText = "删除",
        destructive = true,
        onConfirm = {
            val mapping = pendingMapping ?: return@ConfirmDialog
            scope.launch {
                runCatching { nfcTagStore.remove(mapping.tagId) }
                    .onSuccess {
                        mappings = nfcTagStore.load()
                        snackbar.show("映射已删除")
                    }
                    .onFailure { snackbar.show("删除失败", isError = true) }
            }
            pendingMapping = null
        },
        onDismiss = { pendingMapping = null },
    )
}
