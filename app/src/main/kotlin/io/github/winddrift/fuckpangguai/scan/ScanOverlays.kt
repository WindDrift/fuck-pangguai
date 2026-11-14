package io.github.winddrift.fuckpangguai.scan

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.winddrift.fuckpangguai.device.ResolvedDevice
import io.github.winddrift.fuckpangguai.ui.components.DeviceInfoCard
import io.github.winddrift.fuckpangguai.ui.components.KeyValueRow
import io.github.winddrift.fuckpangguai.ui.components.SectionCard
import io.github.winddrift.fuckpangguai.ui.components.rememberLastNonNull
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun ScanStateOverlays(
    state: ScanState,
    onContinue: (ResolvedDevice) -> Unit,
    onConfirmBind: () -> Unit,
    onSkipBind: () -> Unit,
    onReset: () -> Unit,
    onOpenAlipay: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val needsBind = state as? ScanState.NeedsBind
    val deviceReady = rememberLastNonNull(state as? ScanState.DeviceReady)
    val confirmBind = rememberLastNonNull(state as? ScanState.ConfirmBind)
    val message = rememberLastNonNull(state as? ScanState.Message)

    BackHandler(enabled = needsBind != null) {
        onReset()
    }

    Box(modifier = modifier.fillMaxSize()) {
        OverlayBottomSheet(
            show = deviceReady != null && state is ScanState.DeviceReady,
            title = "设备信息",
            onDismissRequest = onReset,
        ) {
            deviceReady?.let { ready ->
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    DeviceInfoCard(device = ready.device)
                    if (ready.device.detail.isBluetooth == 1) {
                        Text(
                            text = "在线设备可继续；BLE 设备当前仅支持探测。",
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onSurfaceSecondary,
                        )
                    }
                    Button(
                        onClick = { onContinue(ready.device) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColorsPrimary(),
                    ) {
                        Text("继续")
                    }
                    TextButton(
                        text = "重新扫码",
                        onClick = onReset,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        needsBind?.let { bind ->
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 112.dp)
                    .fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("读到新 NFC 标签", style = MiuixTheme.textStyles.title3)
                    KeyValueRow("标签 ID", bind.tagId)
                    Text(
                        "请扫描同一台设备上的二维码，确认后再绑定。",
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceSecondary,
                    )
                    TextButton(
                        text = "取消",
                        onClick = onReset,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        OverlayBottomSheet(
            show = confirmBind != null && state is ScanState.ConfirmBind,
            title = "绑定标签到设备？",
            onDismissRequest = { if (state !is ScanState.ConfirmBind || !state.binding) onReset() },
        ) {
            confirmBind?.let { bind ->
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SectionCard(title = bind.device.detail.name.ifBlank { "饮水机" }) {
                        KeyValueRow("标签 ID", bind.tagId)
                        KeyValueRow("SN", bind.device.sourceValue)
                        Text(
                            if (bind.binding) {
                                "正在写入标签映射，请稍候…"
                            } else {
                                "绑定后，碰一下该标签即可快速解析这台设备。"
                            },
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceSecondary,
                        )
                    }
                    Button(
                        onClick = onConfirmBind,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !bind.binding,
                        colors = ButtonDefaults.buttonColorsPrimary(),
                    ) {
                        Text(if (bind.binding) "正在绑定…" else "绑定并继续")
                    }
                    TextButton(
                        text = "跳过",
                        onClick = onSkipBind,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !bind.binding,
                    )
                }
            }
        }

        OverlayDialog(
            show = message != null && state is ScanState.Message,
            title = "提示",
            summary = message?.text,
            onDismissRequest = onReset,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onReset,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) {
                    Text("重新扫码")
                }
                message?.rawUrl?.let { url ->
                    TextButton(
                        text = "打开支付宝",
                        onClick = { onOpenAlipay(url) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
