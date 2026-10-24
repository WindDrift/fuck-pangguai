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
    BackHandler(
        enabled = state is ScanState.DeviceReady ||
            state is ScanState.NeedsBind ||
            state is ScanState.ConfirmBind ||
            state is ScanState.Message,
    ) {
        onReset()
    }

    Box(modifier = modifier.fillMaxSize()) {
        when (state) {
            is ScanState.DeviceReady -> OverlayBottomSheet(
                show = true,
                title = "设备信息",
                onDismissRequest = onReset,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    DeviceInfoCard(device = state.device)
                    if (state.device.detail.isBluetooth == 1) {
                        Text(
                            text = "在线设备可继续；BLE 设备当前仅支持探测。",
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onSurfaceSecondary,
                        )
                    }
                    Button(
                        onClick = { onContinue(state.device) },
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

            is ScanState.NeedsBind -> Card(
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
                    KeyValueRow("标签 ID", state.tagId)
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

            is ScanState.ConfirmBind -> OverlayBottomSheet(
                show = true,
                title = "绑定标签到设备？",
                onDismissRequest = onReset,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionCard(title = state.device.detail.name.ifBlank { "饮水机" }) {
                        KeyValueRow("标签 ID", state.tagId)
                        KeyValueRow("SN", state.device.sourceValue)
                        Text(
                            "绑定后，碰一下该标签即可快速解析这台设备。",
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceSecondary,
                        )
                    }
                    Button(
                        onClick = onConfirmBind,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColorsPrimary(),
                    ) {
                        Text("绑定并继续")
                    }
                    TextButton(
                        text = "跳过",
                        onClick = onSkipBind,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            is ScanState.Message -> OverlayDialog(
                show = true,
                title = "提示",
                summary = state.text,
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
                    state.rawUrl?.let { url ->
                        TextButton(
                            text = "打开支付宝",
                            onClick = { onOpenAlipay(url) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            else -> Unit
        }
    }
}
