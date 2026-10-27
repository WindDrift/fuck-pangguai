package io.github.winddrift.fuckpangguai.history

import android.app.DatePickerDialog
import android.view.ContextThemeWrapper
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.ZoneId
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.RadioButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.theme.MiuixTheme
import io.github.winddrift.fuckpangguai.ui.components.SectionCard

@Composable
internal fun HistoryFilterSheet(
    show: Boolean,
    filter: HistoryFilter,
    deviceOptions: List<String>,
    ticketShopOptions: List<String>,
    onDismiss: () -> Unit,
    onUpdate: ((HistoryFilter) -> HistoryFilter) -> Unit,
    onReset: () -> Unit,
) {
    if (!show) return
    val context = LocalContext.current
    val colors = MiuixTheme.colorScheme
    val dark = colors.surface.luminance() < 0.5f
    var dateDialog by remember { mutableStateOf<DatePickerDialog?>(null) }
    DisposableEffect(Unit) {
        onDispose { dateDialog?.dismiss() }
    }

    fun openDatePicker(forStart: Boolean) {
        val today = LocalDate.now()
        val initialDay = (if (forStart) filter.customStartEpochDay else filter.customEndEpochDay)
            ?.let(LocalDate::ofEpochDay)
            ?: today
        val dialogContext = ContextThemeWrapper(
            context,
            if (dark) {
                android.R.style.Theme_DeviceDefault_Dialog_Alert
            } else {
                android.R.style.Theme_DeviceDefault_Light_Dialog_Alert
            },
        )
        val dialog = DatePickerDialog(
            dialogContext,
            { _, year, month, day ->
                val picked = LocalDate.of(year, month + 1, day).toEpochDay()
                onUpdate { current ->
                    if (forStart) {
                        val end = current.customEndEpochDay
                        current.copy(
                            timePreset = HistoryTimePreset.CUSTOM,
                            customStartEpochDay = picked,
                            customEndEpochDay = if (end != null && end < picked) picked else end,
                        )
                    } else {
                        val start = current.customStartEpochDay
                        current.copy(
                            timePreset = HistoryTimePreset.CUSTOM,
                            customStartEpochDay = if (start != null && start > picked) picked else start,
                            customEndEpochDay = picked,
                        )
                    }
                }
            },
            initialDay.year,
            initialDay.monthValue - 1,
            initialDay.dayOfMonth,
        )
        val maxDay = if (forStart) {
            filter.customEndEpochDay ?: today.toEpochDay()
        } else {
            today.toEpochDay()
        }
        dialog.datePicker.maxDate = endOfDayMillis(maxDay)
        if (!forStart) {
            filter.customStartEpochDay?.let {
                dialog.datePicker.minDate = startOfDayMillis(it)
            }
        }
        dialog.setOnDismissListener { dateDialog = null }
        dateDialog = dialog
        dialog.show()
    }

    OverlayBottomSheet(
        show = true,
        title = "筛选",
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "修改立即生效，只影响列表展示",
                    modifier = Modifier.weight(1f),
                    style = MiuixTheme.textStyles.footnote1,
                    color = colors.onSurfaceSecondary,
                )
                TextButton(text = "重置", onClick = onReset)
                TextButton(text = "完成", onClick = onDismiss)
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp),
                contentPadding = PaddingValues(bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    TimeFilterSection(
                        filter = filter,
                        onUpdate = onUpdate,
                        onPickStart = { openDatePicker(true) },
                        onPickEnd = { openDatePicker(false) },
                    )
                }
                item {
                    FilterSection(title = "设备") {
                        MultiSelectContent(
                            selected = filter.devices,
                            options = deviceOptions,
                            labelFor = { it.ifBlank { "未命名设备" } },
                            emptyText = "暂无设备记录",
                            onChange = { devices ->
                                onUpdate { it.copy(devices = devices) }
                            },
                        )
                    }
                }
                item {
                    FilterSection(title = "接水结果") {
                        SessionResult.entries.forEach { result ->
                            CheckboxRow(
                                label = result.label(),
                                checked = result in filter.sessionResults,
                                onCheckedChange = { checked ->
                                    onUpdate {
                                        it.copy(
                                            sessionResults = it.sessionResults.toggle(
                                                result,
                                                checked,
                                            ),
                                        )
                                    }
                                },
                            )
                        }
                    }
                }
                item {
                    FilterSection(title = "账单匹配") {
                        billStateFilterOrder.forEach { state ->
                            CheckboxRow(
                                label = state.label(),
                                checked = state in filter.billStates,
                                onCheckedChange = { checked ->
                                    onUpdate {
                                        it.copy(
                                            billStates = it.billStates.toggle(state, checked),
                                        )
                                    }
                                },
                            )
                        }
                    }
                }
                item {
                    FilterSection(title = "账单支付状态") {
                        BillPaymentGroup.entries.forEach { group ->
                            CheckboxRow(
                                label = group.label(),
                                checked = group in filter.paymentGroups,
                                onCheckedChange = { checked ->
                                    onUpdate {
                                        it.copy(
                                            paymentGroups = it.paymentGroups.toggle(
                                                group,
                                                checked,
                                            ),
                                        )
                                    }
                                },
                            )
                        }
                    }
                }
                item {
                    FilterSection(title = "实付金额（元）") {
                        AmountRangeFields(
                            range = filter.paidAmount,
                            minLabel = "最低",
                            maxLabel = "最高",
                            onRangeChange = { range ->
                                onUpdate { it.copy(paidAmount = range) }
                            },
                        )
                    }
                }
                item {
                    FilterSection(title = "异常/失败") {
                        CheckboxRow(
                            label = "仅看异常或失败",
                            summary = "会话错误、账单同步失败或小票流水失败",
                            checked = filter.exceptionsOnly,
                            onCheckedChange = { checked ->
                                onUpdate { it.copy(exceptionsOnly = checked) }
                            },
                        )
                    }
                }
                item {
                    TicketFilterSection(
                        filter = filter,
                        ticketShopOptions = ticketShopOptions,
                        onUpdate = onUpdate,
                    )
                }
            }
        }
    }
}

@Composable
private fun TimeFilterSection(
    filter: HistoryFilter,
    onUpdate: ((HistoryFilter) -> HistoryFilter) -> Unit,
    onPickStart: () -> Unit,
    onPickEnd: () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    FilterSection(title = "时间范围") {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items(HistoryTimePreset.entries) { preset ->
                HistoryChoiceChip(
                    label = preset.label(),
                    selected = filter.timePreset == preset,
                    onClick = {
                        onUpdate {
                            it.copy(
                                timePreset = preset,
                                customStartEpochDay = if (preset == HistoryTimePreset.CUSTOM) {
                                    it.customStartEpochDay
                                } else {
                                    null
                                },
                                customEndEpochDay = if (preset == HistoryTimePreset.CUSTOM) {
                                    it.customEndEpochDay
                                } else {
                                    null
                                },
                            )
                        }
                    },
                )
            }
        }

        if (filter.timePreset == HistoryTimePreset.CUSTOM) {
            DateRow(
                label = "开始日期",
                epochDay = filter.customStartEpochDay,
                onPick = onPickStart,
                onClear = {
                    onUpdate { it.copy(customStartEpochDay = null) }
                },
            )
            DateRow(
                label = "结束日期",
                epochDay = filter.customEndEpochDay,
                onPick = onPickEnd,
                onClear = {
                    onUpdate { it.copy(customEndEpochDay = null) }
                },
            )
            val start = filter.customStartEpochDay
            val end = filter.customEndEpochDay
            if (start != null && end != null && start > end) {
                Text(
                    text = "开始日期不能晚于结束日期。",
                    style = MiuixTheme.textStyles.footnote1,
                    color = colors.error,
                )
            }
        }
        Text(
            text = "按列表显示的记录时间筛选。",
            style = MiuixTheme.textStyles.footnote1,
            color = colors.onSurfaceSecondary,
        )
    }
}

@Composable
private fun DateRow(
    label: String,
    epochDay: Long?,
    onPick: () -> Unit,
    onClear: () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MiuixTheme.textStyles.body2,
            )
            Text(
                text = epochDay?.let { formatDate(it) } ?: "未设置",
                style = MiuixTheme.textStyles.footnote1,
                color = colors.onSurfaceSecondary,
            )
        }
        TextButton(text = "选择", onClick = onPick)
        if (epochDay != null) {
            TextButton(text = "清除", onClick = onClear)
        }
    }
}

@Composable
private fun TicketFilterSection(
    filter: HistoryFilter,
    ticketShopOptions: List<String>,
    onUpdate: ((HistoryFilter) -> HistoryFilter) -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    val showTicketDetails = filter.ticketUsage != TicketUsage.UNUSED
    FilterSection(title = "通用小票") {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items(TicketUsage.entries) { usage ->
                HistoryChoiceChip(
                    label = usage.label(),
                    selected = filter.ticketUsage == usage,
                    onClick = {
                        onUpdate { current ->
                            if (usage == TicketUsage.UNUSED) {
                                current.copy(
                                    ticketUsage = usage,
                                    ticketLogMatches = emptySet(),
                                    ticketShops = emptySet(),
                                    ticketComposition = TicketComposition.ALL,
                                    ticketDeduction = AmountRange(),
                                )
                            } else {
                                current.copy(ticketUsage = usage)
                            }
                        }
                    },
                )
            }
        }
        Text(
            text = "“未用小票”只包含已匹配账单且没有小票抵扣的记录；无账单或未匹配请用“账单匹配”筛选。",
            style = MiuixTheme.textStyles.footnote1,
            color = colors.onSurfaceSecondary,
        )

        if (showTicketDetails) {
            Text(
                text = "流水匹配",
                style = MiuixTheme.textStyles.body2,
            )
            TicketLogMatch.entries.forEach { match ->
                CheckboxRow(
                    label = match.label(),
                    checked = match in filter.ticketLogMatches,
                    onCheckedChange = { checked ->
                        onUpdate {
                            it.copy(
                                ticketLogMatches = it.ticketLogMatches.toggle(match, checked),
                            )
                        }
                    },
                )
            }

            Text(
                text = "小票店铺",
                style = MiuixTheme.textStyles.body2,
            )
            if (ticketShopOptions.isEmpty() && filter.ticketShops.isEmpty()) {
                Text(
                    text = "暂无可筛选的小票店铺",
                    style = MiuixTheme.textStyles.footnote1,
                    color = colors.onSurfaceSecondary,
                )
            } else {
                MultiSelectContent(
                    selected = filter.ticketShops,
                    options = ticketShopOptions,
                    labelFor = { it.ifBlank { "未注明店铺" } },
                    emptyText = "暂无可筛选的小票店铺",
                    onChange = { shops ->
                        onUpdate { it.copy(ticketShops = shops) }
                    },
                )
            }

            Text(
                text = "本金/赠送构成",
                style = MiuixTheme.textStyles.body2,
            )
            TicketComposition.entries.forEach { composition ->
                RadioRow(
                    label = composition.label(),
                    summary = composition.summary(),
                    selected = filter.ticketComposition == composition,
                    onClick = {
                        onUpdate { it.copy(ticketComposition = composition) }
                    },
                )
            }

            Text(
                text = "小票抵扣金额（元）",
                style = MiuixTheme.textStyles.body2,
            )
            AmountRangeFields(
                range = filter.ticketDeduction,
                minLabel = "最低",
                maxLabel = "最高",
                onRangeChange = { range ->
                    onUpdate { it.copy(ticketDeduction = range) }
                },
            )
        }
    }
}

@Composable
private fun FilterSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    SectionCard(title = title) {
        content()
    }
}

@Composable
private fun MultiSelectContent(
    selected: Set<String>,
    options: List<String>,
    labelFor: (String) -> String,
    emptyText: String,
    onChange: (Set<String>) -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    val allOptions = remember(options, selected) {
        (options + selected)
            .distinct()
            .sortedWith(compareBy<String> { it.isBlank() }.thenBy { it })
    }
    var query by remember { mutableStateOf("") }
    val visible = remember(query, allOptions) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            allOptions
        } else {
            allOptions.filter { labelFor(it).contains(trimmed, ignoreCase = true) }
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = "已选 ${selected.size} 个",
            modifier = Modifier.weight(1f),
            style = MiuixTheme.textStyles.footnote1,
            color = colors.onSurfaceSecondary,
        )
        TextButton(
            text = "全选可见",
            onClick = { onChange(selected + visible) },
            enabled = visible.isNotEmpty(),
        )
        TextButton(
            text = "清除",
            onClick = { onChange(emptySet()) },
            enabled = selected.isNotEmpty(),
        )
    }

    if (allOptions.size > 8) {
        TextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            label = "搜索选项",
            singleLine = true,
        )
    }

    if (visible.isEmpty()) {
        Text(
            text = emptyText,
            style = MiuixTheme.textStyles.footnote1,
            color = colors.onSurfaceSecondary,
        )
    } else {
        visible.forEach { value ->
            CheckboxRow(
                label = labelFor(value),
                checked = value in selected,
                onCheckedChange = { checked ->
                    onChange(if (checked) selected + value else selected - value)
                },
            )
        }
    }
}

@Composable
private fun CheckboxRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    summary: String? = null,
) {
    val colors = MiuixTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Checkbox(
            state = ToggleableState(checked),
            onClick = null,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MiuixTheme.textStyles.body2,
            )
            if (summary != null) {
                Text(
                    text = summary,
                    style = MiuixTheme.textStyles.footnote1,
                    color = colors.onSurfaceSecondary,
                )
            }
        }
    }
}

@Composable
private fun RadioRow(
    label: String,
    summary: String?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MiuixTheme.textStyles.body2,
            )
            if (summary != null) {
                Text(
                    text = summary,
                    style = MiuixTheme.textStyles.footnote1,
                    color = colors.onSurfaceSecondary,
                )
            }
        }
    }
}

@Composable
private fun AmountRangeFields(
    range: AmountRange,
    minLabel: String,
    maxLabel: String,
    onRangeChange: (AmountRange) -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TextField(
            value = range.minText,
            onValueChange = { onRangeChange(range.copy(minText = it)) },
            modifier = Modifier.weight(1f),
            label = minLabel,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
        TextField(
            value = range.maxText,
            onValueChange = { onRangeChange(range.copy(maxText = it)) },
            modifier = Modifier.weight(1f),
            label = maxLabel,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
    }
    if (range.hasInvalidInput) {
        Text(
            text = "金额格式无效；无效的一端暂不参与筛选。",
            style = MiuixTheme.textStyles.footnote1,
            color = colors.error,
        )
    }
    if (range.isInverted) {
        Text(
            text = "最低值不能大于最高值。",
            style = MiuixTheme.textStyles.footnote1,
            color = colors.error,
        )
    }
}

private fun <T> Set<T>.toggle(value: T, checked: Boolean): Set<T> =
    if (checked) this + value else this - value

private fun formatDate(epochDay: Long): String =
    LocalDate.ofEpochDay(epochDay).toString()

private fun startOfDayMillis(epochDay: Long): Long =
    LocalDate.ofEpochDay(epochDay)
        .atStartOfDay(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()

private fun endOfDayMillis(epochDay: Long): Long =
    LocalDate.ofEpochDay(epochDay)
        .atTime(23, 59, 59)
        .atZone(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()
