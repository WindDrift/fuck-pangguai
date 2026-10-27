package io.github.winddrift.fuckpangguai.confirm

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.winddrift.fuckpangguai.device.ResolvedDevice
import io.github.winddrift.fuckpangguai.net.dto.Sku
import io.github.winddrift.fuckpangguai.ui.animation.ColorSpringSpec
import io.github.winddrift.fuckpangguai.ui.animation.ContentFadeSpec
import io.github.winddrift.fuckpangguai.ui.animation.ContentSlideSpec
import io.github.winddrift.fuckpangguai.ui.components.DeviceInfoCard
import io.github.winddrift.fuckpangguai.ui.components.KeyValueRow
import io.github.winddrift.fuckpangguai.ui.components.SectionCard
import io.github.winddrift.fuckpangguai.ui.components.StatusCard
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.RadioButton
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType

@Composable
fun ConfirmScreen(
    device: ResolvedDevice,
    state: ConfirmState,
    onSelectSku: (Int) -> Unit,
    onSelectChannel: (Int) -> Unit,
    onRetryLocation: () -> Unit,
    onBack: () -> Unit,
) {
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        onRetryLocation()
    }

    AnimatedContent(
        targetState = state,
        contentKey = { it.stageKey },
        transitionSpec = {
            (fadeIn(ContentFadeSpec) + slideInVertically(ContentSlideSpec) { it / 12 }) togetherWith
                (fadeOut(ContentFadeSpec) + slideOutVertically(ContentSlideSpec) { -it / 12 })
        },
        modifier = Modifier.fillMaxSize(),
        label = "confirmState",
    ) { current ->
        when (current) {
            ConfirmState.Loading, ConfirmState.Unlocking -> {
                CenteredProgress(
                    text = if (current == ConfirmState.Loading) {
                        "正在加载设备与套餐…"
                    } else {
                        "已提交解锁请求，等待设备响应…"
                    },
                )
            }

            ConfirmState.NeedsLocationPermission -> {
                ScrollColumn {
                    StatusCard(
                        title = "需要定位权限",
                        message = "解锁请求需要携带经纬度并参与风控签名。请允许定位；本应用不会在后台定位。",
                    )
                    Button(
                        onClick = {
                            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColorsPrimary(),
                    ) {
                        Text("授予定位权限")
                    }
                    TextButton(
                        text = "返回扫码",
                        onClick = onBack,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            is ConfirmState.Blocked -> {
                ScrollColumn {
                    StatusCard(
                        title = "无法继续",
                        message = current.message,
                        isError = true,
                    )
                }
            }

            is ConfirmState.Error -> {
                ScrollColumn {
                    StatusCard(
                        title = "操作失败",
                        message = current.message,
                        isError = true,
                    )
                }
            }

            is ConfirmState.DryRun -> {
                val info = current.info
                ScrollColumn {
                    SectionCard(title = "演练模式：未真实解锁") {
                        KeyValueRow("设备", info.deviceName.ifBlank { "饮水机" })
                        KeyValueRow("goodsId", info.goodsId)
                        KeyValueRow("套餐", "${info.skuName}（${info.skuId}）")
                        KeyValueRow("支付方式", info.channelName)
                        KeyValueRow(
                            "lat/lng",
                            if (info.lat.isBlank() || info.lng.isBlank()) {
                                "未获取"
                            } else {
                                "${info.lat}, ${info.lng}"
                            },
                        )
                        KeyValueRow("payType", info.payType.ifBlank { "空" })
                        Text(
                            "关闭演练模式后才能真实调用 water/unlock。",
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.error,
                        )
                    }
                }
            }

            is ConfirmState.Unlocked -> {
                CenteredProgress(text = "已解锁，正在进入接水…")
            }

            is ConfirmState.Ready -> {
                val ready = current.value
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item { DeviceInfoCard(device = device) }

                    item {
                        SectionCard(title = null) {
                            KeyValueRow(
                                label = "定位",
                                value = when (ready.locationPhase) {
                                    LocationPhase.Fetching -> "正在获取定位…"
                                    LocationPhase.Ready -> "已获取"
                                    LocationPhase.Failed -> "获取失败，可重试"
                                },
                            )
                            KeyValueRow(
                                label = "一键解锁",
                                value = if (ready.oneTap && ready.confirmed) {
                                    "已开启（该设备已确认）"
                                } else {
                                    "未生效（首次确认或设置关闭）"
                                },
                            )
                            if (ready.dryRun) {
                                Text(
                                    "当前为 Debug 演练模式，不会真实解锁。",
                                    style = MiuixTheme.textStyles.footnote1,
                                    color = MiuixTheme.colorScheme.error,
                                )
                            }
                        }
                    }

                    item { SmallTitle("套餐") }
                    itemsIndexed(ready.skus) { index, sku ->
                        SelectionCard(
                            selected = index == ready.selectedSkuIndex,
                            title = sku.name,
                            summary = skuPrice(sku),
                            onClick = { onSelectSku(index) },
                        )
                    }

                    item { SmallTitle("支付方式（后付费）") }
                    itemsIndexed(ready.channels) { index, channel ->
                        SelectionCard(
                            selected = index == ready.selectedChannelIndex,
                            title = channel.methodName,
                            summary = null,
                            onClick = { onSelectChannel(index) },
                        )
                    }

                    item {
                        Text(
                            "解锁即代表同意按所选套餐与支付方式结算。",
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onSurfaceSecondary,
                        )
                    }
                }
            }
        }
    }
}

private val ConfirmState.stageKey: Int
    get() = when (this) {
        ConfirmState.Loading -> 0
        ConfirmState.NeedsLocationPermission -> 1
        is ConfirmState.Ready -> 2
        is ConfirmState.Blocked -> 3
        ConfirmState.Unlocking -> 4
        is ConfirmState.DryRun -> 5
        is ConfirmState.Unlocked -> 6
        is ConfirmState.Error -> 7
    }

@Composable
private fun CenteredProgress(text: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            InfiniteProgressIndicator(
                modifier = Modifier.size(30.dp),
                color = MiuixTheme.colorScheme.primary,
            )
            Text(text, style = MiuixTheme.textStyles.body1)
        }
    }
}

@Composable
private fun ScrollColumn(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
private fun SelectionCard(
    selected: Boolean,
    title: String,
    summary: String?,
    onClick: () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    val containerColor by animateColorAsState(
        targetValue = if (selected) colors.primaryContainer else colors.surfaceContainer,
        animationSpec = ColorSpringSpec,
        label = "selectionContainer",
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) colors.onPrimaryContainer else colors.onSurface,
        animationSpec = ColorSpringSpec,
        label = "selectionContent",
    )
    val summaryColor by animateColorAsState(
        targetValue = if (selected) {
            colors.onPrimaryContainer.copy(alpha = 0.72f)
        } else {
            colors.onSurfaceSecondary
        },
        animationSpec = ColorSpringSpec,
        label = "selectionSummary",
    )
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                role = Role.RadioButton
                this.selected = selected
            },
        insideMargin = PaddingValues(0.dp),
        colors = CardDefaults.defaultColors(
            color = containerColor,
            contentColor = contentColor,
        ),
        pressFeedbackType = PressFeedbackType.Sink,
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MiuixTheme.textStyles.body1)
                if (summary != null) {
                    Text(
                        summary,
                        style = MiuixTheme.textStyles.footnote1,
                        color = summaryColor,
                    )
                }
            }
            RadioButton(selected = selected, onClick = null)
        }
    }
}

private fun skuPrice(sku: Sku): String {
    val price = sku.discountPrice.ifBlank { sku.price }
    val unit = sku.unit.takeIf { it.isNotBlank() }?.let { "/$it" }.orEmpty()
    return "¥$price$unit"
}
