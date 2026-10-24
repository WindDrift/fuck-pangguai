package io.github.winddrift.fuckpangguai.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.winddrift.fuckpangguai.device.ResolvedDevice
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun DeviceInfoCard(
    device: ResolvedDevice,
    modifier: Modifier = Modifier,
) {
    val detail = device.detail
    SectionCard(
        modifier = modifier,
        title = detail.name.ifBlank { "饮水机" },
    ) {
        KeyValueRow("goodsId", device.scan.id)
        KeyValueRow("SN/IMEI", maskSn(detail.imei))
        KeyValueRow("类别", "${device.scan.categoryName}（${device.scan.categoryCode}）")
        KeyValueRow(
            label = "设备状态",
            value = detail.deviceErrorCode?.takeIf { it.isNotBlank() } ?: "正常",
        )
        if (detail.isBluetooth == 1) {
            Text(
                text = "该设备支持 BLE；当前版本仅做探测，暂不解锁。",
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.error,
            )
        }
    }
}

fun maskSn(value: String): String = when {
    value.length <= 4 -> value
    else -> "${"*".repeat((value.length - 4).coerceAtMost(8))}${value.takeLast(4)}"
}
