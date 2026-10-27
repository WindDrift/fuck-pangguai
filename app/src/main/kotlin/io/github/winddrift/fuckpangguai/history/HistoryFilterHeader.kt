package io.github.winddrift.fuckpangguai.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.winddrift.fuckpangguai.ui.components.MinimumTouchTarget
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Filter
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun HistoryFilterHeader(
    filter: HistoryFilter,
    onQueryChange: (String) -> Unit,
    onOpenFilters: () -> Unit,
    onClearFilters: () -> Unit,
    onRemoveChip: (ActiveFilterChip) -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            TextField(
                value = filter.query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f),
                label = "搜索记录",
                singleLine = true,
            )
            if (filter.query.isNotBlank()) {
                IconButton(
                    onClick = { onQueryChange("") },
                    backgroundColor = Color.Transparent,
                ) {
                    Icon(
                        imageVector = MiuixIcons.Close,
                        contentDescription = "清除搜索",
                    )
                }
            }
            Box {
                IconButton(
                    onClick = onOpenFilters,
                    backgroundColor = colors.surfaceContainer,
                ) {
                    Icon(
                        imageVector = MiuixIcons.Filter,
                        contentDescription = "筛选",
                    )
                }
                if (filter.activeCategoryCount > 0) {
                    Text(
                        text = filter.activeCategoryCount.toString(),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .background(colors.primary, CircleShape)
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                        style = MiuixTheme.textStyles.footnote1,
                        color = colors.onPrimary,
                    )
                }
            }
        }

        if (filter.hasActiveFilters) {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items(filter.chips(), key = { it.toString() }) { chip ->
                    FilterChip(
                        label = chip.label(filter),
                        onClick = { onRemoveChip(chip) },
                    )
                }
                item {
                    TextButton(
                        text = "清除筛选",
                        onClick = onClearFilters,
                    )
                }
            }
        }
    }
}

@Composable
internal fun HistoryChoiceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    MinimumTouchTarget(
        onClick = onClick,
        role = Role.RadioButton,
        selected = selected,
    ) {
        Card(
            insideMargin = PaddingValues(0.dp),
            colors = CardDefaults.defaultColors(
                color = if (selected) colors.primaryContainer else colors.surfaceContainer,
                contentColor = if (selected) colors.onPrimaryContainer else colors.onSurface,
            ),
        ) {
            Text(
                text = label,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                style = MiuixTheme.textStyles.footnote1,
            )
        }
    }
}

@Composable
private fun FilterChip(
    label: String,
    onClick: () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    MinimumTouchTarget(
        onClick = onClick,
        role = Role.Button,
    ) {
        Card(
            insideMargin = PaddingValues(0.dp),
            colors = CardDefaults.defaultColors(
                color = colors.surfaceContainer,
                contentColor = colors.onSurface,
            ),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = label,
                    style = MiuixTheme.textStyles.footnote1,
                )
                Icon(
                    imageVector = MiuixIcons.Close,
                    contentDescription = "移除筛选",
                    modifier = Modifier.size(12.dp),
                    tint = colors.onSurfaceSecondary,
                )
            }
        }
    }
}

private val chipDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MM-dd")

private fun ActiveFilterChip.label(filter: HistoryFilter): String = when (this) {
    is ActiveFilterChip.Time -> timeLabel(preset, startEpochDay, endEpochDay)
    is ActiveFilterChip.Device -> "设备：${value.ifBlank { "未命名设备" }}"
    is ActiveFilterChip.SessionResultChip -> value.label()
    is ActiveFilterChip.BillStateChip -> value.label()
    is ActiveFilterChip.PaymentGroupChip -> value.label()
    ActiveFilterChip.Exceptions -> "仅异常"
    is ActiveFilterChip.TicketUsageChip -> value.label()
    is ActiveFilterChip.TicketFlowChip -> value.label()
    is ActiveFilterChip.TicketShop -> "店铺：${value.ifBlank { "未注明" }}"
    is ActiveFilterChip.TicketCompositionChip -> value.label()
    ActiveFilterChip.PaidRange -> filter.paidAmount.rangeLabel("实付")
    ActiveFilterChip.TicketDeductionRange -> filter.ticketDeduction.rangeLabel("小票抵扣")
}

private fun timeLabel(
    preset: HistoryTimePreset,
    startEpochDay: Long?,
    endEpochDay: Long?,
): String {
    return when (preset) {
        HistoryTimePreset.ALL -> "全部时间"
        HistoryTimePreset.TODAY,
        HistoryTimePreset.LAST_7_DAYS,
        HistoryTimePreset.LAST_30_DAYS,
        HistoryTimePreset.LAST_90_DAYS,
        -> preset.label()
        HistoryTimePreset.CUSTOM -> {
            val start = startEpochDay?.let(::formatChipDate)
            val end = endEpochDay?.let(::formatChipDate)
            when {
                start != null && end != null -> "自定义 $start–$end"
                start != null -> "自定义 $start 起"
                end != null -> "自定义至 $end"
                else -> "自定义"
            }
        }
    }
}

private fun formatChipDate(epochDay: Long): String =
    LocalDate.ofEpochDay(epochDay).format(chipDateFormatter)

private fun AmountRange.rangeLabel(prefix: String): String {
    val min = minText.trim()
    val max = maxText.trim()
    return when {
        min.isNotBlank() && max.isNotBlank() -> "$prefix $min–$max"
        min.isNotBlank() -> "$prefix ≥$min"
        max.isNotBlank() -> "$prefix ≤$max"
        else -> prefix
    }
}
