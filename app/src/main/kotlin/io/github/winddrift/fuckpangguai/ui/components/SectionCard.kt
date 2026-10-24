package io.github.winddrift.fuckpangguai.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        insideMargin = PaddingValues(0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (title != null) {
                Text(title, style = MiuixTheme.textStyles.title3)
            }
            content()
        }
    }
}

@Composable
fun StatusCard(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
) {
    val colors = MiuixTheme.colorScheme
    SectionCard(modifier = modifier, title = null) {
        Text(
            text = title,
            style = MiuixTheme.textStyles.title3,
            color = if (isError) colors.error else colors.onSurface,
        )
        Text(
            text = message,
            style = MiuixTheme.textStyles.body2,
            color = if (isError) colors.error else colors.onSurfaceSecondary,
        )
    }
}

@Composable
fun KeyValueRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: androidx.compose.ui.graphics.Color = MiuixTheme.colorScheme.onSurface,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceSecondary,
        )
        Text(
            text = value,
            modifier = Modifier.weight(1f),
            style = MiuixTheme.textStyles.body2,
            color = valueColor,
            textAlign = TextAlign.End,
        )
    }
}
