package io.github.winddrift.fuckpangguai.history

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.winddrift.fuckpangguai.ui.animation.ContentFadeSpec
import io.github.winddrift.fuckpangguai.ui.animation.ContentSlideSpec
import io.github.winddrift.fuckpangguai.ui.animation.PageSlideSpec
import io.github.winddrift.fuckpangguai.ui.components.ConfirmDialog
import io.github.winddrift.fuckpangguai.ui.components.EmptyState
import io.github.winddrift.fuckpangguai.ui.components.KeyValueRow
import io.github.winddrift.fuckpangguai.ui.components.LocalSnackbar
import io.github.winddrift.fuckpangguai.ui.components.PhaseBadge
import io.github.winddrift.fuckpangguai.ui.components.PreferenceDivider
import io.github.winddrift.fuckpangguai.ui.components.SectionCard
import io.github.winddrift.fuckpangguai.ui.format.sessionPhaseLabel
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Filter
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.Recent
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType

@Composable
fun HistoryScreen(
    controller: HistoryController,
    accountId: String?,
    scrollBehavior: ScrollBehavior? = null,
    bottomContentPadding: Dp = 0.dp,
) {
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val colors = MiuixTheme.colorScheme
    val state by controller.state.collectAsStateWithLifecycle()
    var selectedId by remember { mutableStateOf<Long?>(null) }
    var pendingDeleteId by remember { mutableStateOf<Long?>(null) }
    var showFilters by rememberSaveable { mutableStateOf(false) }

    val selectedRecord = state.records.firstOrNull { it.id == selectedId }
    val pendingDelete = state.records.firstOrNull { it.id == pendingDeleteId }
    val visibleRecords = remember(state.records, state.filter) {
        state.records.applyHistoryFilter(state.filter, System.currentTimeMillis())
    }
    val summary = remember(visibleRecords) {
        visibleRecords.summarizeHistory()
    }
    val deviceOptions = remember(state.records) {
        state.records.map { it.deviceName }
            .distinct()
            .sortedWith(compareBy<String> { it.isBlank() }.thenBy { it })
    }
    val ticketShopOptions = remember(state.records) {
        state.records.mapNotNull { it.bill?.ticket?.shopName }
            .distinct()
            .sortedWith(compareBy<String> { it.isBlank() }.thenBy { it })
    }
    val countText = if (state.filter.hasActiveConstraints) {
        "显示 ${visibleRecords.size} / 共 ${state.records.size} 条"
    } else {
        "共 ${state.records.size} 条"
    }
    val firstLoadFailed = state.records.isEmpty() && state.error != null && !state.loading
    val showCount = state.records.isNotEmpty() ||
        (!state.loading && !firstLoadFailed)
    val showHeader = showCount ||
        state.syncing ||
        state.pendingBillCount > 0 ||
        (state.records.isNotEmpty() && state.error != null)

    LaunchedEffect(accountId) {
        controller.refresh(accountId)
    }

    LaunchedEffect(selectedId) {
        selectedId?.let(controller::ensureBill)
    }

    LaunchedEffect(selectedId, state.records) {
        if (selectedId != null && selectedRecord == null) selectedId = null
    }

    BackHandler(enabled = selectedId != null) { selectedId = null }

    PullToRefresh(
        isRefreshing = state.loading || state.syncing,
        onRefresh = { controller.refresh(accountId, includeOld = true) },
        topAppBarScrollBehavior = scrollBehavior,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            HistoryFilterHeader(
                filter = state.filter,
                onQueryChange = controller::setQuery,
                onOpenFilters = { showFilters = true },
                onClearFilters = controller::clearFilters,
                onRemoveChip = { chip ->
                    controller.updateFilter { it.removeChip(chip) }
                },
            )

            if (visibleRecords.isNotEmpty()) {
                HistorySummaryCard(
                    summary = summary,
                    modifier = Modifier.padding(start = 16.dp, top = 4.dp, end = 16.dp),
                )
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    top = 8.dp,
                    end = 16.dp,
                    bottom = 12.dp + bottomContentPadding,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (showHeader) item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (showCount) {
                            AnimatedContent(
                                targetState = countText,
                                transitionSpec = {
                                    (fadeIn(ContentFadeSpec) + slideInVertically(ContentSlideSpec) { it / 2 }) togetherWith
                                        (fadeOut(ContentFadeSpec) + slideOutVertically(ContentSlideSpec) { -it / 2 })
                                },
                                label = "historyCount",
                            ) { text ->
                                Text(
                                    text = text,
                                    style = MiuixTheme.textStyles.body1,
                                )
                            }
                        }
                        when {
                            state.syncing -> Text(
                                text = "正在同步账单…",
                                style = MiuixTheme.textStyles.footnote1,
                                color = colors.onSurfaceSecondary,
                            )
                            state.pendingBillCount > 0 -> Text(
                                text = "还有 ${state.pendingBillCount} 条账单待同步，下拉继续",
                                style = MiuixTheme.textStyles.footnote1,
                                color = colors.onSurfaceSecondary,
                            )
                        }
                        if (state.records.isNotEmpty()) {
                            state.error?.let { message ->
                                Text(
                                    text = message,
                                    style = MiuixTheme.textStyles.footnote1,
                                    color = colors.error,
                                )
                            }
                        }
                    }
                }

                when {
                    state.loading && state.records.isEmpty() -> item {
                        Text(
                            text = "正在读取记录…",
                            style = MiuixTheme.textStyles.body2,
                            color = colors.onSurfaceSecondary,
                        )
                    }

                    firstLoadFailed -> item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            EmptyState(
                                icon = MiuixIcons.Info,
                                title = "记录加载失败",
                                summary = state.error ?: "无法读取接水记录，请稍后重试。",
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Button(
                                onClick = { controller.refresh(accountId, includeOld = true) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColorsPrimary(),
                            ) {
                                Text("重新加载")
                            }
                        }
                    }

                    state.records.isEmpty() -> item {
                        EmptyState(
                            icon = MiuixIcons.Recent,
                            title = "还没有接水记录",
                            summary = "扫码接水后，这里会显示记录并自动匹配账单；下拉可刷新。",
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    visibleRecords.isEmpty() -> item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            EmptyState(
                                icon = MiuixIcons.Filter,
                                title = "没有符合条件的记录",
                                summary = "试试调整关键词或清除部分筛选条件。",
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Button(
                                onClick = controller::clearQueryAndFilters,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColorsPrimary(),
                            ) {
                                Text("清除搜索和筛选")
                            }
                        }
                    }
                }

                items(visibleRecords, key = { it.id }) { record ->
                    HistoryCard(
                        record = record,
                        syncing = state.syncingIds.contains(record.id),
                        modifier = Modifier.animateItem(
                            fadeInSpec = ContentFadeSpec,
                            placementSpec = PageSlideSpec,
                            fadeOutSpec = ContentFadeSpec,
                        ),
                        onClick = { selectedId = record.id },
                        onDelete = { pendingDeleteId = record.id },
                        onRetryBill = { controller.retryBill(record.id) },
                    )
                }

                if (state.pendingBillCount > 0 && !state.syncing) {
                    item {
                        TextButton(
                            text = "继续同步账单",
                            onClick = { controller.refresh(accountId, includeOld = true) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }

    HistoryFilterSheet(
        show = showFilters,
        filter = state.filter,
        deviceOptions = deviceOptions,
        ticketShopOptions = ticketShopOptions,
        onDismiss = { showFilters = false },
        onUpdate = controller::updateFilter,
        onReset = controller::clearFilters,
    )

    selectedRecord?.let { record ->
        OverlayBottomSheet(
            show = true,
            title = "记录详情",
            onDismissRequest = { selectedId = null },
        ) {
            Column(
                modifier = Modifier
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SectionCard(title = record.deviceName.ifBlank { "饮水机" }) {
                    KeyValueRow("时间", formatHistoryTime(record.updatedAt))
                    KeyValueRow("状态", sessionPhaseLabel(record.phase))
                    if (record.amount.isNotBlank()) {
                        KeyValueRow("用量", record.amount)
                    }
                    if (record.displayOrderNo().isNotBlank()) {
                        KeyValueRow("订单", record.displayOrderNo())
                    }
                    if (record.payOrderNo.isNotBlank()) {
                        KeyValueRow("结算订单", record.payOrderNo)
                    }
                    KeyValueRow("商品 ID", record.goodsId)
                    KeyValueRow("规格 ID", record.skuId)
                    if (record.error.isNotBlank()) {
                        Text(
                            text = record.error,
                            style = MiuixTheme.textStyles.body2,
                            color = colors.error,
                        )
                    }
                }

                SectionCard(title = "账单与优惠") {
                    HistoryBillBlock(
                        record = record,
                        syncing = state.syncingIds.contains(record.id),
                        showMetadata = true,
                        onRetryBill = { controller.retryBill(record.id) },
                    )
                }

                Button(
                    onClick = {
                        pendingDeleteId = record.id
                        selectedId = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColorsPrimary(
                        color = colors.error,
                        contentColor = colors.onError,
                    ),
                ) {
                    Text("删除记录")
                }
            }
        }
    }

    ConfirmDialog(
        show = pendingDelete != null,
        title = "删除这条记录？",
        summary = pendingDelete?.let { "${it.deviceName.ifBlank { "饮水机" }} · ${formatHistoryTime(it.updatedAt)}" },
        confirmText = "删除",
        destructive = true,
        onConfirm = {
            val record = pendingDelete ?: return@ConfirmDialog
            scope.launch {
                if (controller.delete(record)) {
                    snackbar.show("记录已删除")
                } else {
                    snackbar.show("删除失败", isError = true)
                }
            }
            pendingDeleteId = null
        },
        onDismiss = { pendingDeleteId = null },
    )
}

@Composable
private fun HistoryCard(
    record: HistoryRecord,
    syncing: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onRetryBill: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        insideMargin = PaddingValues(0.dp),
        pressFeedbackType = PressFeedbackType.Sink,
        onClick = onClick,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = record.deviceName.ifBlank { "饮水机" },
                        style = MiuixTheme.textStyles.body1,
                    )
                    Text(
                        text = formatHistoryTime(record.updatedAt),
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceSecondary,
                    )
                }
                IconButton(
                    onClick = onDelete,
                    backgroundColor = Color.Transparent,
                ) {
                    Icon(
                        imageVector = MiuixIcons.Delete,
                        contentDescription = "删除记录",
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PhaseBadge(record.phase)
                if (record.amount.isNotBlank()) {
                    Text(
                        text = "用量 ${record.amount}",
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceSecondary,
                    )
                }
            }

            if (record.displayOrderNo().isNotBlank()) {
                Text(
                    text = "订单 ${record.displayOrderNo()}",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary,
                )
            }

            HistoryBillBlock(
                record = record,
                syncing = syncing,
                showMetadata = false,
                onRetryBill = onRetryBill,
            )

            if (record.error.isNotBlank()) {
                Text(
                    text = record.error,
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun HistoryBillBlock(
    record: HistoryRecord,
    syncing: Boolean,
    showMetadata: Boolean,
    onRetryBill: () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    val bill = record.bill

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (bill == null) {
            Text(
                text = "账单",
                style = MiuixTheme.textStyles.footnote1,
                color = colors.onSurfaceSecondary,
            )
            Text(
                text = billPlaceholder(record, syncing),
                style = MiuixTheme.textStyles.body2,
                color = if (record.billState == BillState.ERROR) colors.error else colors.onSurfaceSecondary,
            )
            if (record.billError.isNotBlank() &&
                record.billState != BillState.ERROR &&
                record.billState != BillState.UNMATCHED
            ) {
                Text(
                    text = record.billError,
                    style = MiuixTheme.textStyles.footnote1,
                    color = colors.error,
                )
            }
            if (record.expectsBill() && !syncing) {
                TextButton(
                    text = "同步账单",
                    onClick = onRetryBill,
                )
            }
            return@Column
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "账单",
                    style = MiuixTheme.textStyles.footnote1,
                    color = colors.onSurfaceSecondary,
                )
                Text(
                    text = billOrderStatusLabel(bill.orderStatus),
                    style = MiuixTheme.textStyles.footnote1,
                    color = if (bill.orderStatus == 5) colors.error else colors.onSurfaceSecondary,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = billAmountLabel(bill.orderStatus),
                    style = MiuixTheme.textStyles.footnote1,
                    color = colors.onSurfaceSecondary,
                )
                Text(
                    text = bill.paidAmount.ifBlank { "—" }.let { "¥$it" },
                    style = MiuixTheme.textStyles.title3,
                    color = colors.primary,
                )
            }
        }

        if (bill.originalAmount.isNotBlank() ||
            bill.promotions.isNotEmpty() ||
            bill.paymentDiscountAmount.isNotBlank() ||
            bill.ticket != null
        ) {
            PreferenceDivider()
        }

        if (bill.originalAmount.isNotBlank()) {
            BillAmountRow("原价", "¥${bill.originalAmount}")
        }
        bill.promotions.forEach { promotion ->
            BillAmountRow(promotion.label, "-¥${promotion.amount}")
        }
        bill.ticket?.let { ticket ->
            TicketBlock(ticket, bill.ticketLogError)
        }
        if (bill.paymentDiscountAmount.isNotBlank()) {
            BillAmountRow("支付优惠", "-¥${bill.paymentDiscountAmount}")
        }
        if (bill.originalAmount.isNotBlank() || bill.promotions.isNotEmpty() ||
            bill.paymentDiscountAmount.isNotBlank() || bill.ticket != null
        ) {
            PreferenceDivider()
        }
        BillAmountRow(
            label = billAmountLabel(bill.orderStatus),
            value = "¥${bill.paidAmount.ifBlank { "—" }}",
            emphasized = true,
        )

        if (bill.ticket == null) {
            Text(
                text = "本单未使用通用小票抵扣。",
                style = MiuixTheme.textStyles.footnote1,
                color = colors.onSurfaceSecondary,
            )
        }

        if (record.billError.isNotBlank()) {
            Text(
                text = "刷新失败，显示上次账单：${record.billError}",
                style = MiuixTheme.textStyles.footnote1,
                color = colors.error,
            )
        }

        if (syncing) {
            Text(
                text = "正在同步账单…",
                style = MiuixTheme.textStyles.footnote1,
                color = colors.onSurfaceSecondary,
            )
        }

        if (showMetadata) {
            if (bill.orderNo.isNotBlank()) {
                KeyValueRow("账单订单", bill.orderNo)
            }
            if (bill.shopName.isNotBlank()) {
                KeyValueRow("店铺", bill.shopName)
            }
            if (bill.machineName.isNotBlank()) {
                KeyValueRow("账单设备", bill.machineName)
            }
            if (bill.payTypeName.isNotBlank()) {
                KeyValueRow("支付方式", bill.payTypeName)
            }
            if (bill.payTime.isNotBlank()) {
                KeyValueRow("支付时间", bill.payTime)
            }
        }

        if (!syncing) {
            TextButton(
                text = "刷新账单",
                onClick = onRetryBill,
            )
        }
    }
}

@Composable
private fun TicketBlock(ticket: BillTicket, ticketLogError: String) {
    val colors = MiuixTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surfaceContainer, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            text = "通用小票 · ${ticket.shopName.ifBlank { "当前店铺" }}",
            style = MiuixTheme.textStyles.body2,
        )
        Text(
            text = "消耗 ${ticket.points} 点 · 抵扣 ¥${ticket.deductionYuan}",
            style = MiuixTheme.textStyles.body2,
            color = colors.primary,
        )
        if (ticket.principalPoints > 0 || ticket.presentPoints > 0) {
            Text(
                text = "本金 ${ticket.principalPoints} 点 · 赠送 ${ticket.presentPoints} 点",
                style = MiuixTheme.textStyles.footnote1,
                color = colors.onSurfaceSecondary,
            )
        }
        if (ticket.matchedLog) {
            val meta = listOfNotNull(
                ticket.remark.takeIf { it.isNotBlank() },
                ticket.createdAt.takeIf { it.isNotBlank() },
            ).joinToString(" · ")
            if (meta.isNotBlank()) {
                Text(
                    text = meta,
                    style = MiuixTheme.textStyles.footnote1,
                    color = colors.onSurfaceSecondary,
                )
            }
            if (ticket.logPoints > 0 &&
                ticket.billPoints > 0 &&
                ticket.logPoints != ticket.billPoints
            ) {
                Text(
                    text = "账单抵扣 ${ticket.billPoints} 点，流水记录 ${ticket.logPoints} 点，请以账单为准。",
                    style = MiuixTheme.textStyles.footnote1,
                    color = colors.error,
                )
            }
        } else {
            Text(
                text = if (ticketLogError.isNotBlank()) {
                    "店铺流水获取失败，以账单抵扣为准。"
                } else {
                    "店铺流水未匹配，以账单抵扣为准。"
                },
                style = MiuixTheme.textStyles.footnote1,
                color = colors.onSurfaceSecondary,
            )
        }
    }
}

@Composable
private fun BillAmountRow(
    label: String,
    value: String,
    emphasized: Boolean = false,
) {
    val colors = MiuixTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MiuixTheme.textStyles.body2,
            color = colors.onSurfaceSecondary,
        )
        Text(
            text = value,
            style = if (emphasized) MiuixTheme.textStyles.body1 else MiuixTheme.textStyles.body2,
            color = if (emphasized) colors.primary else colors.onSurface,
        )
    }
}

private fun billAmountLabel(orderStatus: Int): String =
    if (orderStatus == 0 || orderStatus == 1) "需付" else "实付"

private fun billPlaceholder(record: HistoryRecord, syncing: Boolean): String {
    if (!record.expectsBill()) {
        return "本次未产生费用。"
    }
    if (syncing) return "正在同步账单…"
    return when (record.billState) {
        BillState.ERROR -> record.billError.ifBlank { "账单同步失败，可重试。" }
        BillState.UNMATCHED -> record.billError.ifBlank { "未找到对应账单，可重试。" }
        BillState.MATCHED -> "账单数据为空，可重试。"
        BillState.NONE -> if (record.isWithinBillWindow(System.currentTimeMillis())) {
            "账单尚未同步。"
        } else {
            "较早的记录，点开详情或重试后按需匹配账单。"
        }
    }
}
