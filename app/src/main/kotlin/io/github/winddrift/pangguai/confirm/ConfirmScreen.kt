package io.github.winddrift.pangguai.confirm

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.winddrift.pangguai.device.ResolvedDevice
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun ConfirmScreen(
    device: ResolvedDevice,
    controller: ConfirmController,
    onBack: () -> Unit,
) {
    val state by controller.state.collectAsStateWithLifecycle()
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        controller.retryLocation()
    }

    LaunchedEffect(device) {
        controller.start(device)
    }

    Scaffold(
        topBar = { TopAppBar(title = "确认设备") },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.height(4.dp))

            when (val current = state) {
                ConfirmState.Loading -> Text("正在加载设备与套餐…")
                ConfirmState.NeedsLocationPermission -> {
                    InfoCard(
                        title = "需要定位权限",
                        lines = listOf(
                            "解锁请求需要携带经纬度并参与风控签名。",
                            "请允许定位；本应用不会在后台定位。",
                        ),
                    )
                    Button(
                        onClick = {
                            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("授予定位权限")
                    }
                    Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                        Text("返回扫码")
                    }
                }

                is ConfirmState.Blocked -> {
                    InfoCard(title = "无法继续", lines = listOf(current.message))
                    Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                        Text("返回扫码")
                    }
                }

                is ConfirmState.Error -> {
                    InfoCard(title = "操作失败", lines = listOf(current.message))
                    Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                        Text("返回扫码")
                    }
                }

                ConfirmState.Unlocking -> {
                    InfoCard(title = "正在解锁", lines = listOf("已提交解锁请求，等待设备响应…"))
                }

                is ConfirmState.DryRun -> {
                    InfoCard(
                        title = "演练模式：未真实解锁",
                        lines = listOf(
                            "设备：${current.info.deviceName}",
                            "goodsId：${current.info.goodsId}",
                            "套餐：${current.info.skuName}（${current.info.skuId}）",
                            "支付方式：${current.info.channelName}",
                            "lat/lng：${current.info.lat}, ${current.info.lng}",
                            "payType：${current.info.payType.ifBlank { "空" }}",
                            "关闭演练模式后才能真实调用 water/unlock。",
                        ),
                    )
                    Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                        Text("返回扫码")
                    }
                }

                is ConfirmState.Unlocked -> {
                    val session = current.session
                    InfoCard(
                        title = "已解锁",
                        lines = listOf(
                            "设备：${session.device.detail.name.ifBlank { "饮水机" }}",
                            "套餐：${session.sku.name}",
                            "支付方式：${session.channel.methodName}",
                            "unlock orderNo：${session.unlock.orderNo}",
                            "msgId：${session.unlock.msgId}",
                            "下一步：接入前台服务，持续轮询接水状态与结算。",
                        ),
                    )
                    Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                        Text("完成")
                    }
                }

                is ConfirmState.Ready -> {
                    val ready = current.value
                    InfoCard(
                        title = ready.device.detail.name.ifBlank { "饮水机" },
                        lines = listOfNotNull(
                            "goodsId：${ready.device.scan.id}",
                            "SN/IMEI：${mask(ready.device.detail.imei)}",
                            "类别：${ready.device.scan.categoryName}（${ready.device.scan.categoryCode}）",
                            "定位：已获取",
                            if (ready.oneTap && ready.confirmed) {
                                "一键解锁：已开启（该设备已确认）"
                            } else {
                                "一键解锁：未生效（首次确认或设置关闭）"
                            },
                            if (ready.dryRun) "当前为 Debug 演练模式，不会真实解锁" else null,
                        ),
                    )

                    Text("套餐", style = MiuixTheme.textStyles.title3)
                    ready.skus.forEachIndexed { index, sku ->
                        val selected = index == ready.selectedSkuIndex
                        Button(
                            onClick = { controller.selectSku(index) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                "${if (selected) "✓ " else ""}${sku.name}  " +
                                    "¥${sku.discountPrice.ifBlank { sku.price }}" +
                                    sku.unit.takeIf { it.isNotBlank() }?.let { "/$it" }.orEmpty(),
                            )
                        }
                    }

                    Text("支付方式（后付费）", style = MiuixTheme.textStyles.title3)
                    ready.channels.forEachIndexed { index, channel ->
                        val selected = index == ready.selectedChannelIndex
                        Button(
                            onClick = { controller.selectChannel(index) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("${if (selected) "✓ " else ""}${channel.methodName}")
                        }
                    }

                    Button(
                        onClick = controller::unlock,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (ready.dryRun) "演练解锁" else "开始使用")
                    }
                    Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                        Text("返回扫码")
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun InfoCard(title: String, lines: List<String>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MiuixTheme.textStyles.title2)
            lines.forEach { line ->
                Text(line, style = MiuixTheme.textStyles.body2)
            }
        }
    }
}

private fun mask(value: String): String = when {
    value.length <= 4 -> value
    else -> "${"*".repeat((value.length - 4).coerceAtMost(8))}${value.takeLast(4)}"
}
