package io.github.winddrift.fuckpangguai.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import io.github.winddrift.fuckpangguai.ui.animation.ColorSpringSpec
import io.github.winddrift.fuckpangguai.ui.animation.ContentFadeSpec
import io.github.winddrift.fuckpangguai.ui.animation.SizeSpringSpec
import io.github.winddrift.fuckpangguai.water.SessionPhase
import top.yukonga.miuix.kmp.anim.SinOutEasing
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.Colors
import top.yukonga.miuix.kmp.theme.MiuixTheme

private enum class TimelineState { Done, Active, Pending, Error }

@Composable
fun SessionTimeline(
    phase: String,
    modifier: Modifier = Modifier,
) {
    val colors = MiuixTheme.colorScheme
    val steps = listOf(
        TimelineStep("解锁设备", TimelineState.Done),
        TimelineStep("接水", usingState(phase)),
        TimelineStep("结算", settleState(phase)),
        TimelineStep("支付", payState(phase)),
    )
    val pulse = rememberInfiniteTransition(label = "timelinePulse")
    val activeScale by pulse.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = SinOutEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "timelineActiveScale",
    )

    Column(modifier = modifier.fillMaxWidth()) {
        steps.forEachIndexed { index, step ->
            Row(modifier = Modifier.fillMaxWidth()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val dotColor by animateColorAsState(
                        targetValue = dotColor(step.state, colors),
                        animationSpec = ColorSpringSpec,
                        label = "timelineDot$index",
                    )
                    val scale = if (step.state == TimelineState.Active) activeScale else 1f
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                            }
                            .background(dotColor, CircleShape),
                    )
                    if (index != steps.lastIndex) {
                        val connectorColor by animateColorAsState(
                            targetValue = if (step.state == TimelineState.Done) {
                                colors.primary
                            } else {
                                colors.dividerLine
                            },
                            animationSpec = ColorSpringSpec,
                            label = "timelineConnector$index",
                        )
                        Box(
                            modifier = Modifier
                                .width(2.dp)
                                .height(26.dp)
                                .background(connectorColor),
                        )
                    }
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp, bottom = if (index == steps.lastIndex) 0.dp else 12.dp),
                ) {
                    val textColor by animateColorAsState(
                        targetValue = textColor(step.state, colors),
                        animationSpec = ColorSpringSpec,
                        label = "timelineText$index",
                    )
                    Text(
                        text = step.label,
                        style = MiuixTheme.textStyles.body2,
                        color = textColor,
                    )
                    AnimatedVisibility(
                        visible = step.state == TimelineState.Active || step.state == TimelineState.Error,
                        enter = fadeIn(ContentFadeSpec) + expandVertically(SizeSpringSpec),
                        exit = fadeOut(ContentFadeSpec) + shrinkVertically(SizeSpringSpec),
                    ) {
                        Text(
                            text = if (step.state == TimelineState.Error) "需要处理" else "进行中",
                            style = MiuixTheme.textStyles.footnote1,
                            color = textColor,
                        )
                    }
                }
            }
        }
    }
}

private data class TimelineStep(val label: String, val state: TimelineState)

private fun usingState(phase: String): TimelineState = when (phase) {
    SessionPhase.USING -> TimelineState.Active
    SessionPhase.ENDING, SessionPhase.SETTLING -> TimelineState.Done
    in SessionPhase.TERMINAL -> TimelineState.Done
    else -> TimelineState.Pending
}

private fun settleState(phase: String): TimelineState = when (phase) {
    SessionPhase.USING -> TimelineState.Pending
    SessionPhase.ENDING, SessionPhase.SETTLING -> TimelineState.Active
    SessionPhase.FAULT -> TimelineState.Error
    SessionPhase.PAID, SessionPhase.FINISHED_NO_CHARGE, SessionPhase.PAY_UNKNOWN -> TimelineState.Done
    else -> TimelineState.Pending
}

private fun payState(phase: String): TimelineState = when (phase) {
    SessionPhase.PAID, SessionPhase.FINISHED_NO_CHARGE -> TimelineState.Done
    SessionPhase.PAY_UNKNOWN, SessionPhase.FAULT -> TimelineState.Error
    else -> TimelineState.Pending
}

private fun dotColor(state: TimelineState, colors: Colors): Color =
    when (state) {
        TimelineState.Done -> colors.primary
        TimelineState.Active -> colors.primary
        TimelineState.Error -> colors.error
        TimelineState.Pending -> colors.dividerLine
    }

private fun textColor(state: TimelineState, colors: Colors): Color =
    when (state) {
        TimelineState.Done -> colors.onSurface
        TimelineState.Active -> colors.primary
        TimelineState.Error -> colors.error
        TimelineState.Pending -> colors.onSurfaceSecondary
    }
