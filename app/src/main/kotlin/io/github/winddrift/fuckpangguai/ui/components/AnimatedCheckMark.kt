package io.github.winddrift.fuckpangguai.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun AnimatedCheckMark(
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
) {
    val colors = MiuixTheme.colorScheme
    val progress = androidx.compose.runtime.remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 380, easing = FastOutSlowInEasing),
        )
    }

    Canvas(modifier = modifier.size(size)) {
        val value = progress.value
        val radius = this.size.minDimension / 2f * (0.86f + 0.14f * value)
        drawCircle(color = colors.primary, radius = radius)

        val stroke = 4.dp.toPx()
        val firstStart = Offset(this.size.width * 0.27f, this.size.height * 0.52f)
        val firstEnd = Offset(this.size.width * 0.43f, this.size.height * 0.68f)
        val secondEnd = Offset(this.size.width * 0.74f, this.size.height * 0.32f)

        if (value <= 0.42f) {
            drawLine(
                color = colors.onPrimary,
                start = firstStart,
                end = lerp(firstStart, firstEnd, value / 0.42f),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        } else {
            drawLine(
                color = colors.onPrimary,
                start = firstStart,
                end = firstEnd,
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = colors.onPrimary,
                start = firstEnd,
                end = lerp(firstEnd, secondEnd, (value - 0.42f) / 0.58f),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
    }
}
