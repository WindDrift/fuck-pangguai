package io.github.winddrift.fuckpangguai.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.anim.SinOutEasing
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun ScanFrameOverlay(
    modifier: Modifier = Modifier,
    hint: String = "将设备二维码放入取景框",
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val frameSize = (minOf(maxWidth, maxHeight) * 0.68f)
            .coerceAtMost(320.dp)
            .coerceAtLeast(200.dp)
        val cornerLength = 26.dp
        val strokeWidth = 3.dp
        val scrim = Color.Black.copy(alpha = 0.42f)
        val scanLine = rememberInfiniteTransition(label = "scanLine")
        val scanProgress by scanLine.animateFloat(
            initialValue = 0.08f,
            targetValue = 0.92f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1700, easing = SinOutEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "scanLineProgress",
        )

        Canvas(Modifier.fillMaxSize()) {
            val side = frameSize.toPx()
            val left = (size.width - side) / 2f
            val top = (size.height - side) / 2f

            drawRect(scrim, topLeft = Offset.Zero, size = Size(size.width, top))
            drawRect(
                scrim,
                topLeft = Offset(0f, top + side),
                size = Size(size.width, size.height - top - side),
            )
            drawRect(scrim, topLeft = Offset(0f, top), size = Size(left, side))
            drawRect(
                scrim,
                topLeft = Offset(left + side, top),
                size = Size(size.width - left - side, side),
            )

            val corner = cornerLength.toPx()
            val stroke = strokeWidth.toPx()
            val color = Color.White.copy(alpha = 0.92f)

            fun cornerLines(x: Float, y: Float, dx: Float, dy: Float) {
                drawLine(
                    color = color,
                    start = Offset(x, y + dy * corner),
                    end = Offset(x, y),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = color,
                    start = Offset(x, y),
                    end = Offset(x + dx * corner, y),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
            }

            cornerLines(left, top, 1f, 1f)
            cornerLines(left + side, top, -1f, 1f)
            cornerLines(left, top + side, 1f, -1f)
            cornerLines(left + side, top + side, -1f, -1f)

            val lineY = top + side * scanProgress
            drawLine(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.White.copy(alpha = 0.85f),
                        Color.Transparent,
                    ),
                    startX = left,
                    endX = left + side,
                ),
                start = Offset(left + side * 0.06f, lineY),
                end = Offset(left + side * 0.94f, lineY),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }

        Text(
            text = hint,
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = frameSize / 2 + 24.dp)
                .padding(horizontal = 16.dp),
            style = MiuixTheme.textStyles.footnote1,
            color = Color.White.copy(alpha = 0.72f),
        )
    }
}
