package io.github.winddrift.fuckpangguai.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun ChoiceChip(
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
