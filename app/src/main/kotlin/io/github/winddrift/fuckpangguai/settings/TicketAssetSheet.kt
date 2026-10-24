package io.github.winddrift.fuckpangguai.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.winddrift.fuckpangguai.account.formatLogAmount
import io.github.winddrift.fuckpangguai.account.pointsToYuan
import io.github.winddrift.fuckpangguai.net.dto.ItemsList
import io.github.winddrift.fuckpangguai.net.dto.Ticket
import io.github.winddrift.fuckpangguai.net.dto.TicketLog
import io.github.winddrift.fuckpangguai.net.userMessage
import io.github.winddrift.fuckpangguai.ui.components.EmptyState
import io.github.winddrift.fuckpangguai.ui.components.PreferenceDivider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Recent
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun TicketAssetSheet(
    show: Boolean,
    tickets: List<Ticket>,
    ticketsComplete: Boolean,
    loadLogs: suspend (shopId: String, page: Int, pageSize: Int) -> ItemsList<TicketLog>,
    onDismiss: () -> Unit,
) {
    var selectedShop by remember { mutableStateOf<Ticket?>(null) }
    var logs by remember { mutableStateOf<List<TicketLog>>(emptyList()) }
    var page by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var hasMore by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val colors = MiuixTheme.colorScheme

    fun request(shop: Ticket, nextPage: Int) {
        if (loading) return
        scope.launch {
            loading = true
            error = null
            try {
                val result = loadLogs(shop.shopId, nextPage, LOG_PAGE_SIZE)
                logs = if (nextPage == 1) result.items else logs + result.items
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

    LaunchedEffect(show) {
        selectedShop = null
        logs = emptyList()
        page = 0
        hasMore = false
        error = null
    }

    LaunchedEffect(selectedShop) {
        val shop = selectedShop ?: return@LaunchedEffect
        logs = emptyList()
        page = 0
        hasMore = false
        error = null
        request(shop, 1)
    }

    BackHandler(enabled = show) {
        if (selectedShop != null) {
            selectedShop = null
        } else {
            onDismiss()
        }
    }

    OverlayBottomSheet(
        show = show,
        title = selectedShop?.let { "小票流水 · ${it.shopName}" } ?: "通用小票",
        startAction = if (selectedShop == null) {
            null
        } else {
            {
                IconButton(
                    onClick = { selectedShop = null },
                    backgroundColor = Color.Transparent,
                ) {
                    Icon(
                        imageVector = MiuixIcons.Back,
                        contentDescription = "返回小票列表",
                    )
                }
            }
        },
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
        ) {
            val shop = selectedShop
            if (shop == null) {
                if (tickets.isEmpty()) {
                    EmptyState(
                        icon = MiuixIcons.Recent,
                        title = "暂无通用小票",
                        summary = "当前账号还没有可展示的通用小票。",
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .heightIn(max = 420.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        tickets.forEachIndexed { index, ticket ->
                            if (index > 0) PreferenceDivider()
                            TicketRow(ticket = ticket, onClick = { selectedShop = ticket })
                        }
                        if (!ticketsComplete) {
                            Text(
                                text = "仅展示已加载的部分小票。",
                                style = MiuixTheme.textStyles.footnote1,
                                color = colors.onSurfaceSecondary,
                                modifier = Modifier.padding(vertical = 8.dp),
                            )
                        }
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    when {
                        logs.isEmpty() && loading -> Text(
                            text = "正在加载流水…",
                            style = MiuixTheme.textStyles.body2,
                            color = colors.onSurfaceSecondary,
                        )

                        logs.isEmpty() && error == null -> Text(
                            text = "暂无流水",
                            style = MiuixTheme.textStyles.body2,
                            color = colors.onSurfaceSecondary,
                        )
                    }
                    if (logs.isNotEmpty()) {
                        Column(
                            modifier = Modifier
                                .heightIn(max = 360.dp)
                                .verticalScroll(rememberScrollState()),
                        ) {
                            logs.forEachIndexed { index, log ->
                                if (index > 0) PreferenceDivider()
                                LogRow(log = log)
                            }
                        }
                    }
                    if (error != null) {
                        Text(
                            text = "流水加载失败：$error",
                            style = MiuixTheme.textStyles.footnote1,
                            color = colors.error,
                        )
                    }
                    if (hasMore) {
                        Button(
                            onClick = { request(shop, page + 1) },
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
    }
}

@Composable
private fun TicketRow(
    ticket: Ticket,
    onClick: () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = ticket.shopName.ifBlank { "未知店铺" },
                style = MiuixTheme.textStyles.body1,
            )
            Text(
                text = "本金 ¥${pointsToYuan(ticket.principalAmount)} · " +
                    "赠送 ¥${pointsToYuan(ticket.presentAmount)}",
                style = MiuixTheme.textStyles.footnote1,
                color = colors.onSurfaceSecondary,
            )
        }
        Text(
            text = "¥${pointsToYuan(ticket.totalAmount)}",
            style = MiuixTheme.textStyles.body1,
        )
    }
}

@Composable
private fun LogRow(log: TicketLog) {
    val colors = MiuixTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        val title = log.remark.ifBlank { "通用小票变动" }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = title,
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
            if (log.shopName.isNotBlank() && log.shopName != title) {
                Text(
                    text = log.shopName,
                    style = MiuixTheme.textStyles.footnote1,
                    color = colors.onSurfaceSecondary,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
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

private const val LOG_PAGE_SIZE = 15
