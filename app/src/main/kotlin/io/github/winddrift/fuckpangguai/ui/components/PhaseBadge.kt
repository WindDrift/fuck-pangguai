package io.github.winddrift.fuckpangguai.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.winddrift.fuckpangguai.ui.format.sessionPhaseLabel
import io.github.winddrift.fuckpangguai.water.SessionPhase
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun PhaseBadge(
    phase: String,
    modifier: Modifier = Modifier,
) {
    val colors = MiuixTheme.colorScheme
    val (background, foreground) = when (phase) {
        SessionPhase.USING -> colors.primaryContainer to colors.onPrimaryContainer
        SessionPhase.ENDING, SessionPhase.SETTLING -> {
            colors.secondaryContainer to colors.onSecondaryContainer
        }
        SessionPhase.PAY_UNKNOWN, SessionPhase.FAULT -> {
            colors.errorContainer to colors.onErrorContainer
        }
        else -> colors.secondaryContainer to colors.onSecondaryContainer
    }

    Row(
        modifier = modifier
            .background(background, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .size(6.dp)
                .background(foreground, CircleShape),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = sessionPhaseLabel(phase),
            style = MiuixTheme.textStyles.footnote1,
            color = foreground,
        )
    }
}
