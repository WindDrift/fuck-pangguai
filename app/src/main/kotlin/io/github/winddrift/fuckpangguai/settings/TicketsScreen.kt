package io.github.winddrift.fuckpangguai.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.winddrift.fuckpangguai.account.AssetController
import io.github.winddrift.fuckpangguai.account.formatLogAmount
import io.github.winddrift.fuckpangguai.account.pointsToYuan
import io.github.winddrift.fuckpangguai.net.dto.ItemsList
import io.github.winddrift.fuckpangguai.net.dto.Ticket
import io.github.winddrift.fuckpangguai.net.dto.TicketLog
import io.github.winddrift.fuckpangguai.net.userMessage
import io.github.winddrift.fuckpangguai.ui.components.EmptyState
import io.github.winddrift.fuckpangguai.ui.components.SectionCard
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.icon.extended.Promotions
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun TicketsScreen(
    assetController: AssetController,
    shopId: String,
    shopName: String,
    onSelectShop: (Ticket) -> Unit,
    onRecharge: (String, String) -> Unit,
) {
    if (shopId.isBlank()) {
        TicketShopList(
            assetController = assetController,
            onSelectShop = onSelectShop,
            onRecharge = onRecharge,
        )
    } else {
        TicketLogList(
            assetController = assetController,
            shopId = shopId,
            shopName = shopName,
            onRecharge = onRecharge,
        )
    }
}

@Composable
private fun TicketShopList(
    assetController: AssetController,
    onSelectShop: (Ticket) -> Unit,
    onRecharge: (String, String) -> Unit,
) {
    val state by assetController.state.collectAsStateWithLifecycle()
    val colors = MiuixTheme.colorScheme
    val tickets = state.snapshot?.tickets.orEmpty()
    val scope = rememberCoroutineScope()
    var refundTarget by remember { mutableStateOf<Ticket?>(null) }
    var refundSubmitting by remember { mutableStateOf(false) }
    var refundError by remember { mutableStateOf<String?>(null) }
    var refundNotice by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        assetController.refresh()
    }

    fun submitRefund(ticket: Ticket, alipayAccount: String, name: String) {
        scope.launch {
            refundSubmitting = true
            refundError = null
            try {
                assetController.applyRefund(ticket.shopId, alipayAccount, name)
                refundTarget = null
                refundNotice = "退款申请已提交，请等待店铺审核；可在官方 App 的退款记录中查看进度。"
                assetController.refresh()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                refundError = throwable.userMessage()
            } finally {
                refundSubmitting = false
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (state.snapshot != null) {
            item {
                Text(
                    text = "按店铺汇总，共 ${tickets.size} 家店铺",
                    style = MiuixTheme.textStyles.footnote1,
                    color = colors.onSurfaceSecondary,
                )
            }
        }

        refundNotice?.let { notice ->
            item {
                Text(
                    text = notice,
                    style = MiuixTheme.textStyles.footnote1,
                    color = colors.primary,
                )
            }
        }

        when {
            state.snapshot == null && state.loading -> item {
                Text(
                    text = "正在加载通用小票…",
                    style = MiuixTheme.textStyles.body2,
                    color = colors.onSurfaceSecondary,
                )
            }

            state.snapshot == null && state.error != null -> item {
                RefreshError(
                    message = state.error.orEmpty(),
                    onRetry = assetController::refresh,
                )
            }

            state.snapshot == null -> item {
                Text(
                    text = "正在加载通用小票…",
                    style = MiuixTheme.textStyles.body2,
                    color = colors.onSurfaceSecondary,
                )
            }

            state.loading && tickets.isEmpty() -> item {
                Text(
                    text = "正在加载通用小票…",
                    style = MiuixTheme.textStyles.body2,
                    color = colors.onSurfaceSecondary,
                )
            }

            tickets.isEmpty() -> item {
                EmptyState(
                    icon = MiuixIcons.Promotions,
                    title = "暂无通用小票",
                    summary = "当前账号还没有可展示的通用小票。",
                )
            }

            else -> {
                itemsIndexed(
                    items = tickets,
                    key = { index, ticket ->
                        ticket.shopId.ifBlank { ticket.id }.ifBlank { "shop-$index" }
                    },
                ) { _, ticket ->
                    TicketShopCard(
                        ticket = ticket,
                        onClick = { onSelectShop(ticket) },
                        onRecharge = { onRecharge(ticket.shopId, ticket.shopName) },
                        onRefund = {
                            refundError = null
                            refundNotice = null
                            refundTarget = ticket
                        },
                    )
                }
                if (state.snapshot?.ticketsComplete == false) {
                    item {
                        Text(
                            text = "仅展示已加载的部分小票。",
                            style = MiuixTheme.textStyles.footnote1,
                            color = colors.onSurfaceSecondary,
                        )
                    }
                }
            }
        }

        if (state.snapshot != null && state.error != null) {
            item {
                Text(
                    text = "刷新失败，已保留上次数据：${state.error}",
                    style = MiuixTheme.textStyles.footnote1,
                    color = colors.error,
                )
            }
        }
    }

    val target = refundTarget
    TicketRefundSheet(
        ticket = target,
        submitting = refundSubmitting,
        error = refundError,
        onDismiss = {
            if (!refundSubmitting) {
                refundTarget = null
                refundError = null
            }
        },
        onSubmit = { account, name ->
            target?.let { submitRefund(it, account, name) }
        },
    )
}

@Composable
private fun TicketShopCard(
    ticket: Ticket,
    onClick: () -> Unit,
    onRecharge: () -> Unit,
    onRefund: () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    Card(insideMargin = PaddingValues(0.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = ticket.shopId.isNotBlank(), onClick = onClick),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = ticket.shopName.ifBlank { "未知店铺" },
                        style = MiuixTheme.textStyles.title3,
                    )
                    Text(
                        text = "本金 ¥${pointsToYuan(ticket.principalAmount)} · " +
                            "赠送 ¥${pointsToYuan(ticket.presentAmount)}",
                        style = MiuixTheme.textStyles.footnote1,
                        color = colors.onSurfaceSecondary,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "¥${pointsToYuan(ticket.totalAmount)}",
                        style = MiuixTheme.textStyles.body1,
                    )
                    Text(
                        text = "${ticket.totalAmount} 点",
                        style = MiuixTheme.textStyles.footnote1,
                        color = colors.onSurfaceSecondary,
                    )
                }
                TextButton(
                    text = "充值",
                    onClick = onRecharge,
                    enabled = ticket.shopId.isNotBlank(),
                )
                Icon(
                    imageVector = MiuixIcons.ChevronForward,
                    contentDescription = null,
                    tint = colors.onSurfaceSecondary,
                )
            }

            if (ticket.hasProcessingRefund || ticket.isAllowRefund != 0) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = when {
                            ticket.hasProcessingRefund -> "退款申请审核中"
                            ticket.isRefundNeedFile == 1 -> "支持退款至支付宝（需上传材料）"
                            else -> "支持退款至支付宝"
                        },
                        style = MiuixTheme.textStyles.footnote1,
                        color = colors.onSurfaceSecondary,
                    )
                    if (!ticket.hasProcessingRefund) {
                        TextButton(
                            text = "退款",
                            onClick = onRefund,
                            enabled = ticket.shopId.isNotBlank(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TicketRefundSheet(
    ticket: Ticket?,
    submitting: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSubmit: (String, String) -> Unit,
) {
    if (ticket == null) return
    val colors = MiuixTheme.colorScheme
    var account by remember(ticket.shopId) { mutableStateOf("") }
    var name by remember(ticket.shopId) { mutableStateOf("") }
    val needsFile = ticket.isRefundNeedFile == 1
    val canSubmit = !submitting && !needsFile && account.isNotBlank() && name.isNotBlank()

    OverlayBottomSheet(
        show = true,
        title = "退款至支付宝",
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "${ticket.shopName.ifBlank { "该店铺" }} · 可退本金 ¥${pointsToYuan(ticket.principalAmount)}",
                style = MiuixTheme.textStyles.body1,
            )
            Text(
                text = "退款将退回支付宝。请填写支付宝账号（手机号）以及该支付宝账号的真实实名姓名；" +
                    "姓名与账号不一致时店铺会驳回申请。赠送金额不退。",
                style = MiuixTheme.textStyles.footnote1,
                color = colors.onSurfaceSecondary,
            )
            if (needsFile) {
                Text(
                    text = "该店铺退款需要上传材料" +
                        (ticket.uploadFileRemark.takeIf { it.isNotBlank() }?.let { "：$it" } ?: "") +
                        "。当前版本暂不支持上传，请在官方 App 提交。",
                    style = MiuixTheme.textStyles.footnote1,
                    color = colors.error,
                )
            }
            TextField(
                value = account,
                onValueChange = { account = it },
                modifier = Modifier.fillMaxWidth(),
                label = "支付宝账号（手机号）",
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Phone,
                    imeAction = ImeAction.Next,
                ),
            )
            TextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                label = "支付宝实名姓名",
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Done,
                ),
            )
            error?.let {
                Text(
                    text = it,
                    style = MiuixTheme.textStyles.footnote1,
                    color = colors.error,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    text = "取消",
                    onClick = onDismiss,
                    enabled = !submitting,
                )
                Button(
                    onClick = { onSubmit(account.trim(), name.trim()) },
                    enabled = canSubmit,
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) {
                    Text(if (submitting) "提交中…" else "提交退款")
                }
            }
        }
    }
}

@Composable
private fun TicketLogList(
    assetController: AssetController,
    shopId: String,
    shopName: String,
    onRecharge: (String, String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val colors = MiuixTheme.colorScheme
    val assetState by assetController.state.collectAsStateWithLifecycle()
    val refreshedAt = assetState.snapshot?.fetchedAt
    var logs by remember(shopId) { mutableStateOf<List<TicketLog>>(emptyList()) }
    var total by remember(shopId) { mutableIntStateOf(0) }
    var page by remember(shopId) { mutableIntStateOf(0) }
    var loading by remember(shopId) { mutableStateOf(false) }
    var error by remember(shopId) { mutableStateOf<String?>(null) }
    var hasMore by remember(shopId) { mutableStateOf(false) }

    fun load(nextPage: Int) {
        if (loading) return
        scope.launch {
            loading = true
            error = null
            try {
                val result = loadLogs(assetController, shopId, nextPage)
                logs = if (nextPage == 1) result.items else logs + result.items
                total = result.total
                page = nextPage
                hasMore = logs.size < result.total && result.items.isNotEmpty()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                error = throwable.userMessage()
            } finally {
                loading = false
            }
        }
    }

    fun retry() {
        logs = emptyList()
        total = 0
        page = 0
        hasMore = false
        error = null
        load(1)
    }

    LaunchedEffect(shopId, refreshedAt) {
        logs = emptyList()
        total = 0
        page = 0
        hasMore = false
        error = null
        load(1)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SectionCard(title = "小票流水") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = when {
                            total > 0 -> "共 $total 条流水"
                            loading -> "正在加载流水…"
                            error != null -> "流水加载失败"
                            else -> "暂无流水"
                        },
                        style = MiuixTheme.textStyles.footnote1,
                        color = colors.onSurfaceSecondary,
                    )
                    TextButton(
                        text = "充值",
                        onClick = { onRecharge(shopId, shopName) },
                    )
                }
            }
        }

        if (logs.isNotEmpty()) {
            itemsIndexed(
                items = logs,
                key = { index, log -> log.stableKey(index) },
            ) { _, log ->
                TicketLogCard(log = log)
            }
        }

        if (error != null) {
            item {
                RefreshError(
                    message = error.orEmpty(),
                    onRetry = ::retry,
                )
            }
        }

        if (hasMore) {
            item {
                Button(
                    onClick = { load(page + 1) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !loading,
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) {
                    Text(if (loading) "加载中…" else "加载更多")
                }
            }
        }
    }
}

@Composable
private fun TicketLogCard(log: TicketLog) {
    val colors = MiuixTheme.colorScheme
    Card(insideMargin = PaddingValues(0.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = log.remark.ifBlank { "通用小票变动" },
                    style = MiuixTheme.textStyles.body1,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = formatLogAmount(log.type, log.amount),
                    style = MiuixTheme.textStyles.body1,
                    color = if (log.type == 100) colors.primary else colors.onSurface,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = log.shopName,
                    style = MiuixTheme.textStyles.footnote1,
                    color = colors.onSurfaceSecondary,
                )
                Text(
                    text = log.createdAt,
                    style = MiuixTheme.textStyles.footnote1,
                    color = colors.onSurfaceSecondary,
                )
            }
            if (log.afterAmount > 0) {
                Text(
                    text = "变动后余额 ¥${pointsToYuan(log.afterAmount)}",
                    style = MiuixTheme.textStyles.footnote1,
                    color = colors.onSurfaceSecondary,
                )
            }
        }
    }
}

@Composable
private fun RefreshError(
    message: String,
    onRetry: () -> Unit,
) {
    SectionCard(title = "加载失败") {
        Text(
            text = message,
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.error,
        )
        Button(
            onClick = onRetry,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColorsPrimary(),
        ) {
            Text("重试")
        }
    }
}

private fun TicketLog.stableKey(index: Int): String =
    if (id != 0) {
        "log-$shopId-$id"
    } else {
        "log-$shopId-$createdAt-$type-$subType-$amount-$index"
    }

private suspend fun loadLogs(
    assetController: AssetController,
    shopId: String,
    page: Int,
): ItemsList<TicketLog> = assetController.loadLogs(shopId, page)
