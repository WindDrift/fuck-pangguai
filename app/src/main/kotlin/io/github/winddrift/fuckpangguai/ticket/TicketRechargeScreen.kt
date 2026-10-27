package io.github.winddrift.fuckpangguai.ticket

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.winddrift.fuckpangguai.account.pointsToYuan
import io.github.winddrift.fuckpangguai.net.dto.TicketReward
import io.github.winddrift.fuckpangguai.ui.components.BottomActionBar
import io.github.winddrift.fuckpangguai.ui.components.ConfirmDialog
import io.github.winddrift.fuckpangguai.ui.components.LocalSnackbar
import io.github.winddrift.fuckpangguai.ui.components.SectionCard
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun TicketRechargeScreen(
    controller: TicketRechargeController,
    shopId: String,
    shopName: String,
    onClose: () -> Unit,
) {
    val state by controller.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = LocalSnackbar.current
    var showConfirm by remember { mutableStateOf(false) }
    val colors = MiuixTheme.colorScheme

    LaunchedEffect(shopId, shopName) {
        controller.start(shopId, shopName)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                ShopCard(
                    state = state,
                    fallbackShopName = shopName,
                )
            }

            when (state.stage) {
                RechargeStage.Idle -> item {
                    SectionCard(title = "充值") {
                        Text(
                            text = "账号已切换，请返回后重新进入充值页。",
                            style = MiuixTheme.textStyles.body2,
                            color = colors.onSurfaceSecondary,
                        )
                    }
                }

                RechargeStage.LoadingGoods -> item {
                    SectionCard(title = "充值通用小票") {
                        Text(
                            text = "正在加载充值面额…",
                            style = MiuixTheme.textStyles.body2,
                            color = colors.onSurfaceSecondary,
                        )
                    }
                }

                RechargeStage.Error -> item {
                    SectionCard(title = "加载失败") {
                        Text(
                            text = state.message ?: "无法加载充值信息",
                            style = MiuixTheme.textStyles.body2,
                            color = colors.error,
                        )
                    }
                }

                RechargeStage.GoodsReady,
                RechargeStage.CreatingOrder -> {
                    val goods = state.goods
                    val rewards = goods?.shopTokenCoinRewards.orEmpty()
                    if (state.pending != null) {
                        item {
                            PendingBanner(
                                pending = state.pending!!,
                                onQuery = controller::retryQuery,
                            )
                        }
                    }
                    if (state.message != null) {
                        item {
                            MessageCard(message = state.message.orEmpty())
                        }
                    }
                    item {
                        Text(
                            text = "选择充值面额",
                            style = MiuixTheme.textStyles.title3,
                        )
                    }
                    items(rewards.chunked(3)) { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            row.forEach { reward ->
                                RewardCard(
                                    reward = reward,
                                    selected = reward.id == state.selectedRewardId,
                                    enabled = state.stage == RechargeStage.GoodsReady,
                                    onClick = { controller.selectReward(reward.id) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            repeat(3 - row.size) {
                                Column(modifier = Modifier.weight(1f)) {}
                            }
                        }
                    }
                    item {
                        Text(
                            text = "1 元 = 100 点，通用小票可在本店铺消费时抵扣。",
                            style = MiuixTheme.textStyles.footnote1,
                            color = colors.onSurfaceSecondary,
                        )
                    }
                }

                RechargeStage.OrderReady,
                RechargeStage.PreparingPay,
                RechargeStage.WaitingSdk,
                RechargeStage.Verifying -> item {
                    OrderCard(state = state)
                }

                RechargeStage.Paid -> item {
                    ResultCard(
                        title = "支付成功",
                        summary = "通用小票已到账，可在本店铺流水与余额中查看。",
                        error = false,
                        state = state,
                    )
                }

                RechargeStage.Cancelled -> item {
                    ResultCard(
                        title = "未完成支付",
                        summary = state.message ?: "本次充值未完成，未产生扣款。",
                        error = true,
                        state = state,
                    )
                }

                RechargeStage.Unknown -> item {
                    ResultCard(
                        title = "支付结果确认中",
                        summary = state.message
                            ?: "暂时无法确认支付结果。如已扣款，请勿重复支付，可稍后重新查询。",
                        error = true,
                        state = state,
                    )
                }

                RechargeStage.Expired -> item {
                    ResultCard(
                        title = "订单已关闭",
                        summary = state.message ?: "订单已超时关闭，请重新下单。",
                        error = true,
                        state = state,
                    )
                }
            }

            item {
                Text(
                    text = "支付由支付宝处理；客户端仅在服务端确认订单后显示成功。",
                    style = MiuixTheme.textStyles.footnote1,
                    color = colors.onSurfaceSecondary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding(),
                )
            }
        }

        BottomActionBar {
            when (state.stage) {
                RechargeStage.Idle,
                RechargeStage.Error -> {
                    Button(
                        onClick = onClose,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColorsPrimary(),
                    ) {
                        Text("返回")
                    }
                }

                RechargeStage.LoadingGoods -> DisabledAction("正在加载…")

                RechargeStage.GoodsReady -> {
                    Button(
                        onClick = { showConfirm = true },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = state.canConfirm,
                        colors = ButtonDefaults.buttonColorsPrimary(),
                    ) {
                        Text("确认充值")
                    }
                }

                RechargeStage.CreatingOrder -> DisabledAction("正在创建订单…")

                RechargeStage.OrderReady -> {
                    Button(
                        onClick = {
                            val activity = context.findActivity()
                            if (activity == null) {
                                snackbar.show("无法启动支付宝，请稍后重试", isError = true)
                            } else {
                                controller.pay(AlipaySdkGateway(activity))
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = state.canPay,
                        colors = ButtonDefaults.buttonColorsPrimary(),
                    ) {
                        Text("立即支付 ¥${state.price}")
                    }
                }

                RechargeStage.PreparingPay -> DisabledAction("正在准备支付…")
                RechargeStage.WaitingSdk -> DisabledAction("正在等待支付宝…")
                RechargeStage.Verifying -> DisabledAction("正在核验支付结果…")

                RechargeStage.Paid -> {
                    Button(
                        onClick = onClose,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColorsPrimary(),
                    ) {
                        Text("查看流水")
                    }
                }

                RechargeStage.Cancelled -> ResultActions(
                    primaryText = "重新选择",
                    onPrimary = controller::startOver,
                    onClose = onClose,
                )

                RechargeStage.Unknown -> ResultActions(
                    primaryText = "重新查询",
                    onPrimary = controller::retryQuery,
                    onClose = onClose,
                )

                RechargeStage.Expired -> ResultActions(
                    primaryText = "重新下单",
                    onPrimary = controller::startOver,
                    onClose = onClose,
                )
            }
        }
    }

    ConfirmDialog(
        show = showConfirm,
        title = "确认充值",
        summary = buildString {
            append("店铺：")
            append(state.goods?.shopName?.ifBlank { shopName } ?: shopName)
            append("\n面额：¥")
            append(state.selectedReward?.cashValue.orEmpty())
            append("，到账 ")
            append(state.selectedReward?.reach ?: 0)
            append(" 点")
            val bonus = state.selectedReward?.reward ?: 0
            if (bonus > 0) {
                append("，赠送 ¥")
                append(pointsToYuan(bonus))
            }
            append("\n支付方式：支付宝")
        },
        confirmText = "确认充值",
        onConfirm = controller::confirmCreateOrder,
        onDismiss = { showConfirm = false },
    )
}

@Composable
private fun ShopCard(
    state: TicketRechargeState,
    fallbackShopName: String,
) {
    val colors = MiuixTheme.colorScheme
    val goods = state.goods
    val balance = goods?.userTokenCoinDTO
    val refundText = goods?.let {
        it.refundRemark.ifBlank {
            if (it.isAllowRefund == 0) "此商家暂不支持退款" else ""
        }
    }.orEmpty()

    SectionCard(title = goods?.shopName?.ifBlank { fallbackShopName } ?: fallbackShopName) {
        Text(
            text = if (balance != null) {
                "当前小票余额 ¥${pointsToYuan(balance.totalAmount)}"
            } else {
                "当前小票余额未知"
            },
            style = MiuixTheme.textStyles.body1,
        )
        if (balance != null && (balance.principalAmount != "0" || balance.presentAmount != "0")) {
            Text(
                text = "本金 ¥${balance.principalAmount} · 赠送 ¥${balance.presentAmount}",
                style = MiuixTheme.textStyles.footnote1,
                color = colors.onSurfaceSecondary,
            )
        }
        if (refundText.isNotBlank()) {
            Text(
                text = refundText,
                style = MiuixTheme.textStyles.footnote1,
                color = colors.onSurfaceSecondary,
            )
        }
    }
}

@Composable
private fun PendingBanner(
    pending: PendingRecharge,
    onQuery: () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    SectionCard(title = "待确认的充值订单") {
        Text(
            text = "有一笔待确认的充值订单（¥${pending.price}）。",
            style = MiuixTheme.textStyles.body2,
        )
        Text(
            text = "请先查询状态，如已扣款请勿重复支付。",
            style = MiuixTheme.textStyles.footnote1,
            color = colors.onSurfaceSecondary,
        )
        TextButton(
            text = "查询状态",
            onClick = onQuery,
        )
    }
}

@Composable
private fun MessageCard(message: String) {
    val colors = MiuixTheme.colorScheme
    Card(insideMargin = PaddingValues(0.dp)) {
        Text(
            text = message,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            style = MiuixTheme.textStyles.body2,
            color = colors.error,
        )
    }
}

@Composable
private fun RewardCard(
    reward: TicketReward,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MiuixTheme.colorScheme
    Card(
        modifier = modifier,
        insideMargin = PaddingValues(0.dp),
        colors = CardDefaults.defaultColors(
            color = if (selected) colors.primaryContainer else colors.surfaceContainer,
            contentColor = if (selected) colors.onPrimaryContainer else colors.onSurface,
        ),
        onClick = { if (enabled) onClick() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "¥${reward.cashValue}",
                style = MiuixTheme.textStyles.title3,
                textAlign = TextAlign.Center,
            )
            if (reward.reward > 0) {
                Text(
                    text = "送 ¥${pointsToYuan(reward.reward)}",
                    style = MiuixTheme.textStyles.footnote1,
                    color = if (selected) colors.onPrimaryContainer else colors.primary,
                    textAlign = TextAlign.Center,
                )
            }
            Text(
                text = "到账 ${reward.reach} 点",
                style = MiuixTheme.textStyles.footnote1,
                color = if (selected) {
                    colors.onPrimaryContainer.copy(alpha = 0.72f)
                } else {
                    colors.onSurfaceSecondary
                },
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun OrderCard(state: TicketRechargeState) {
    val colors = MiuixTheme.colorScheme
    SectionCard(title = "订单信息") {
        Text(
            text = state.shopName.ifBlank { "通用小票充值" },
            style = MiuixTheme.textStyles.body1,
        )
        Text(
            text = "充值面额：¥${state.price}",
            style = MiuixTheme.textStyles.body2,
        )
        Text(
            text = "到账点数：${state.points} 点",
            style = MiuixTheme.textStyles.body2,
        )
        Text(
            text = "支付方式：支付宝",
            style = MiuixTheme.textStyles.body2,
            color = colors.onSurfaceSecondary,
        )
        when (state.stage) {
            RechargeStage.PreparingPay -> Text(
                text = "正在准备支付…",
                style = MiuixTheme.textStyles.footnote1,
                color = colors.onSurfaceSecondary,
            )

            RechargeStage.WaitingSdk -> Text(
                text = "正在等待支付宝…",
                style = MiuixTheme.textStyles.footnote1,
                color = colors.onSurfaceSecondary,
            )

            RechargeStage.Verifying -> Text(
                text = "正在核验支付结果，请勿重复支付。",
                style = MiuixTheme.textStyles.footnote1,
                color = colors.primary,
            )

            else -> Unit
        }
        if (state.message != null) {
            Text(
                text = state.message.orEmpty(),
                style = MiuixTheme.textStyles.footnote1,
                color = colors.error,
            )
        }
    }
}

@Composable
private fun ResultCard(
    title: String,
    summary: String,
    error: Boolean,
    state: TicketRechargeState,
) {
    val colors = MiuixTheme.colorScheme
    SectionCard(title = title) {
        Text(
            text = summary,
            style = MiuixTheme.textStyles.body2,
            color = if (error) colors.error else colors.onSurface,
        )
        if (state.price.isNotBlank()) {
            Text(
                text = "订单金额 ¥${state.price} · 到账 ${state.points} 点",
                style = MiuixTheme.textStyles.footnote1,
                color = colors.onSurfaceSecondary,
            )
        }
    }
}

@Composable
private fun ResultActions(
    primaryText: String,
    onPrimary: () -> Unit,
    onClose: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(
            onClick = onPrimary,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColorsPrimary(),
        ) {
            Text(primaryText)
        }
        TextButton(
            text = "返回",
            onClick = onClose,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun DisabledAction(text: String) {
    Button(
        onClick = {},
        modifier = Modifier.fillMaxWidth(),
        enabled = false,
    ) {
        Text(text)
    }
}
