package io.github.winddrift.fuckpangguai.ui.components

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.winddrift.fuckpangguai.water.SessionPhase
import top.yukonga.miuix.kmp.basic.Text
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

    Column(modifier = modifier.fillMaxWidth()) {
        steps.forEachIndexed { index, step ->
            Row(modifier = Modifier.fillMaxWidth()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(dotColor(step.state, colors), CircleShape),
                    )
                    if (index != steps.lastIndex) {
                        Box(
                            modifier = Modifier
                                .width(2.dp)
                                .height(26.dp)
                                .background(colors.dividerLine),
                        )
                    }
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp, bottom = if (index == steps.lastIndex) 0.dp else 12.dp),
                ) {
                    Text(
                        text = step.label,
                        style = MiuixTheme.textStyles.body2,
                        color = textColor(step.state, colors),
                    )
                    when (step.state) {
                        TimelineState.Active -> Text(
                            text = "进行中",
                            style = MiuixTheme.textStyles.footnote1,
                            color = colors.primary,
                        )
                        TimelineState.Error -> Text(
                            text = "需要处理",
                            style = MiuixTheme.textStyles.footnote1,
                            color = colors.error,
                        )
                        else -> Unit
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

private fun dotColor(state: TimelineState, colors: top.yukonga.miuix.kmp.theme.Colors): Color =
    when (state) {
        TimelineState.Done -> colors.primary
        TimelineState.Active -> colors.primary
        TimelineState.Error -> colors.error
        TimelineState.Pending -> colors.dividerLine
    }

private fun textColor(state: TimelineState, colors: top.yukonga.miuix.kmp.theme.Colors): Color =
    when (state) {
        TimelineState.Done -> colors.onSurface
        TimelineState.Active -> colors.primary
        TimelineState.Error -> colors.error
        TimelineState.Pending -> colors.onSurfaceSecondary
    }
