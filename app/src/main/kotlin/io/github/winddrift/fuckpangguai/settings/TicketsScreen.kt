package io.github.winddrift.fuckpangguai.settings

import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.icon.extended.Promotions
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun TicketsScreen(
    assetController: AssetController,
    shopId: String,
    onSelectShop: (Ticket) -> Unit,
) {
    if (shopId.isBlank()) {
        TicketShopList(
            assetController = assetController,
            onSelectShop = onSelectShop,
        )
    } else {
        TicketLogList(
            assetController = assetController,
            shopId = shopId,
        )
    }
}

@Composable
private fun TicketShopList(
    assetController: AssetController,
    onSelectShop: (Ticket) -> Unit,
) {
    val state by assetController.state.collectAsStateWithLifecycle()
    val colors = MiuixTheme.colorScheme
    val tickets = state.snapshot?.tickets.orEmpty()

    LaunchedEffect(Unit) {
        assetController.refresh()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                text = "按店铺汇总，共 ${tickets.size} 家店铺",
                style = MiuixTheme.textStyles.footnote1,
                color = colors.onSurfaceSecondary,
            )
        }

        when {
            state.loading && state.snapshot == null -> item {
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
                items(tickets) { ticket ->
                    TicketShopCard(ticket = ticket, onClick = { onSelectShop(ticket) })
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
                if (state.error != null) {
                    item {
                        Text(
                            text = "刷新失败，已保留上次数据：${state.error}",
                            style = MiuixTheme.textStyles.footnote1,
                            color = colors.error,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TicketShopCard(
    ticket: Ticket,
    onClick: () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    Card(insideMargin = PaddingValues(0.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = ticket.shopId.isNotBlank(), onClick = onClick)
                .padding(16.dp),
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
            Icon(
                imageVector = MiuixIcons.ChevronForward,
                contentDescription = null,
                tint = colors.onSurfaceSecondary,
            )
        }
    }
}

@Composable
private fun TicketLogList(
    assetController: AssetController,
    shopId: String,
) {
    val scope = rememberCoroutineScope()
    val colors = MiuixTheme.colorScheme
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

    LaunchedEffect(shopId) {
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
                Text(
                    text = when {
                        total > 0 -> "共 $total 条流水"
                        loading -> "正在加载流水…"
                        else -> "暂无流水"
                    },
                    style = MiuixTheme.textStyles.footnote1,
                    color = colors.onSurfaceSecondary,
                )
            }
        }

        if (logs.isNotEmpty()) {
            items(logs) { log ->
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

private suspend fun loadLogs(
    assetController: AssetController,
    shopId: String,
    page: Int,
): ItemsList<TicketLog> = assetController.loadLogs(shopId, page)
