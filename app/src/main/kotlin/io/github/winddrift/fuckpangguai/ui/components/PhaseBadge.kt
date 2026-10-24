package io.github.winddrift.fuckpangguai.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.winddrift.fuckpangguai.ui.animation.ColorSpringSpec
import io.github.winddrift.fuckpangguai.ui.animation.ContentFadeSpec
import io.github.winddrift.fuckpangguai.ui.animation.ContentSlideSpec
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
    val (targetBackground, targetForeground) = when (phase) {
        SessionPhase.USING -> colors.primaryContainer to colors.onPrimaryContainer
        SessionPhase.ENDING, SessionPhase.SETTLING -> {
            colors.secondaryContainer to colors.onSecondaryContainer
        }
        SessionPhase.PAY_UNKNOWN, SessionPhase.FAULT -> {
            colors.errorContainer to colors.onErrorContainer
        }
        else -> colors.secondaryContainer to colors.onSecondaryContainer
    }
    val background by animateColorAsState(
        targetValue = targetBackground,
        animationSpec = ColorSpringSpec,
        label = "phaseBadgeBackground",
    )
    val foreground by animateColorAsState(
        targetValue = targetForeground,
        animationSpec = ColorSpringSpec,
        label = "phaseBadgeForeground",
    )

    Row(
        modifier = modifier
            .background(background, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(foreground, CircleShape),
        )
        Spacer(Modifier.width(6.dp))
        AnimatedContent(
            targetState = sessionPhaseLabel(phase),
            transitionSpec = {
                (fadeIn(ContentFadeSpec) + slideInVertically(ContentSlideSpec) { it / 2 }) togetherWith
                    (fadeOut(ContentFadeSpec) + slideOutVertically(ContentSlideSpec) { -it / 2 })
            },
            label = "phaseBadgeLabel",
        ) { label ->
            Text(
                text = label,
                style = MiuixTheme.textStyles.footnote1,
                color = foreground,
            )
        }
    }
}
