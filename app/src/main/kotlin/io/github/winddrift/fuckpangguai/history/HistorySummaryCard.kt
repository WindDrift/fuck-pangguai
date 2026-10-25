package io.github.winddrift.fuckpangguai.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.winddrift.fuckpangguai.ui.components.PreferenceDivider
import io.github.winddrift.fuckpangguai.ui.components.SectionCard
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun HistorySummaryCard(
    summary: HistorySummary,
    modifier: Modifier = Modifier,
) {
    val colors = MiuixTheme.colorScheme
    SectionCard(
        modifier = modifier,
        title = "筛选结果统计",
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SummaryMetric(
                title = "总花费",
                note = "按原价",
                value = formatSummaryMoney(summary.totalSpend),
                detail = "已支付且已匹配 ${summary.spendCoverage}/${summary.visibleCount} 笔",
                valueColor = if (summary.totalSpend == null) {
                    colors.onSurfaceSecondary
                } else {
                    colors.primary
                },
                modifier = Modifier.weight(1f),
            )
            SummaryMetric(
                title = "总用量",
                note = "未标单位",
                value = formatSummaryUsage(summary.totalUsage),
                detail = "可解析 ${summary.usageCoverage}/${summary.visibleCount} 笔",
                valueColor = if (summary.totalUsage == null) {
                    colors.onSurfaceSecondary
                } else {
                    colors.onSurface
                },
                modifier = Modifier.weight(1f),
            )
        }

        PreferenceDivider()

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "平均单价",
                    style = MiuixTheme.textStyles.footnote1,
                    color = colors.onSurfaceSecondary,
                )
                Text(
                    text = formatSummaryAverage(summary.usagePerYuan),
                    style = MiuixTheme.textStyles.body1,
                    color = if (summary.usagePerYuan == null) {
                        colors.onSurfaceSecondary
                    } else {
                        colors.primary
                    },
                )
            }
            Text(
                text = "同口径 ${summary.averageCoverage}/${summary.visibleCount} 笔",
                style = MiuixTheme.textStyles.footnote1,
                color = colors.onSurfaceSecondary,
            )
        }

        Text(
            text = "总花费按已支付且已匹配账单的原价，排除退款；总用量只汇总纯数字，未标单位。",
            style = MiuixTheme.textStyles.footnote1,
            color = colors.onSurfaceSecondary,
        )
    }
}

@Composable
private fun SummaryMetric(
    title: String,
    note: String,
    value: String,
    detail: String,
    valueColor: Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceSecondary,
            )
            Text(
                text = note,
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceSecondary,
            )
        }
        Text(
            text = value,
            style = MiuixTheme.textStyles.title3,
            color = valueColor,
        )
        Text(
            text = detail,
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceSecondary,
        )
    }
}
