package io.github.winddrift.fuckpangguai.history

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.winddrift.fuckpangguai.ui.animation.ContentFadeSpec
import io.github.winddrift.fuckpangguai.ui.animation.ContentSlideSpec
import io.github.winddrift.fuckpangguai.ui.animation.PageSlideSpec
import io.github.winddrift.fuckpangguai.ui.components.ConfirmDialog
import io.github.winddrift.fuckpangguai.ui.components.EmptyState
import io.github.winddrift.fuckpangguai.ui.components.KeyValueRow
import io.github.winddrift.fuckpangguai.ui.components.LocalSnackbar
import io.github.winddrift.fuckpangguai.ui.components.PhaseBadge
import io.github.winddrift.fuckpangguai.ui.components.SectionCard
import io.github.winddrift.fuckpangguai.ui.format.sessionPhaseLabel
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Recent
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType

@Composable
fun HistoryScreen(
    repository: HistoryRepository,
) {
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val colors = MiuixTheme.colorScheme
    var records by remember { mutableStateOf<List<HistoryRecord>>(emptyList()) }
    var refreshing by remember { mutableStateOf(false) }
    var selectedRecord by remember { mutableStateOf<HistoryRecord?>(null) }
    var pendingDelete by remember { mutableStateOf<HistoryRecord?>(null) }

    fun refresh() {
        scope.launch {
            refreshing = true
            runCatching { repository.list() }
                .onSuccess { records = it }
                .onFailure { snackbar.show("读取记录失败", isError = true) }
            refreshing = false
        }
    }

    LaunchedEffect(Unit) { refresh() }

    BackHandler(enabled = selectedRecord != null) { selectedRecord = null }

    PullToRefresh(
        isRefreshing = refreshing,
        onRefresh = ::refresh,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                AnimatedContent(
                    targetState = records.size,
                    transitionSpec = {
                        (fadeIn(ContentFadeSpec) + slideInVertically(ContentSlideSpec) { it / 2 }) togetherWith
                            (fadeOut(ContentFadeSpec) + slideOutVertically(ContentSlideSpec) { -it / 2 })
                    },
                    label = "historyCount",
                ) { count ->
                    Text(
                        text = "共 $count 条",
                        style = MiuixTheme.textStyles.body1,
                    )
                }
            }

            item {
                AnimatedVisibility(
                    visible = records.isEmpty(),
                    enter = fadeIn(ContentFadeSpec),
                    exit = fadeOut(ContentFadeSpec),
                ) {
                    EmptyState(
                        icon = MiuixIcons.Recent,
                        title = "还没有接水记录",
                        summary = "扫码接水后，这里会显示记录；下拉可刷新。",
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            items(records, key = { it.id }) { record ->
                HistoryCard(
                    record = record,
                    modifier = Modifier.animateItem(
                        fadeInSpec = ContentFadeSpec,
                        placementSpec = PageSlideSpec,
                        fadeOutSpec = ContentFadeSpec,
                    ),
                    onClick = { selectedRecord = record },
                    onDelete = { pendingDelete = record },
                )
            }
        }
    }

    selectedRecord?.let { record ->
        OverlayBottomSheet(
            show = true,
            title = "记录详情",
            onDismissRequest = { selectedRecord = null },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionCard(title = record.deviceName.ifBlank { "饮水机" }) {
                    KeyValueRow("时间", formatHistoryTime(record.updatedAt))
                    KeyValueRow("状态", sessionPhaseLabel(record.phase))
                    if (record.amount.isNotBlank()) {
                        KeyValueRow("用量", record.amount)
                    }
                    if (record.orderNo.isNotBlank()) {
                        KeyValueRow("订单", record.orderNo)
                    }
                    if (record.payOrderNo.isNotBlank()) {
                        KeyValueRow("结算订单", record.payOrderNo)
                    }
                    KeyValueRow("goodsId", record.goodsId)
                    KeyValueRow("skuId", record.skuId)
                    if (record.error.isNotBlank()) {
                        Text(
                            text = record.error,
                            style = MiuixTheme.textStyles.body2,
                            color = colors.error,
                        )
                    }
                }
                Button(
                    onClick = {
                        pendingDelete = record
                        selectedRecord = null
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
                runCatching { repository.delete(record.id) }
                    .onSuccess {
                        refresh()
                        snackbar.show("记录已删除")
                    }
                    .onFailure { snackbar.show("删除失败", isError = true) }
            }
            pendingDelete = null
        },
        onDismiss = { pendingDelete = null },
    )
}

@Composable
private fun HistoryCard(
    record: HistoryRecord,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        insideMargin = PaddingValues(0.dp),
        pressFeedbackType = PressFeedbackType.Sink,
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 14.dp, bottom = 14.dp, end = 6.dp),
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PhaseBadge(record.phase)
                    if (record.amount.isNotBlank()) {
                        Text(
                            text = record.amount,
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onSurfaceSecondary,
                        )
                    }
                }
                if (record.orderNo.isNotBlank()) {
                    Text(
                        text = "订单 ${record.orderNo}",
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceSecondary,
                    )
                }
                if (record.error.isNotBlank()) {
                    Text(
                        text = record.error,
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.error,
                    )
                }
            }
            IconButton(
                onClick = onDelete,
                backgroundColor = androidx.compose.ui.graphics.Color.Transparent,
            ) {
                Icon(
                    imageVector = MiuixIcons.Delete,
                    contentDescription = "删除记录",
                )
            }
        }
    }
}
