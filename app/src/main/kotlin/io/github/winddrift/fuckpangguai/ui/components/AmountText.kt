package io.github.winddrift.fuckpangguai.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import top.yukonga.miuix.kmp.basic.Text
import java.util.Locale

private val amountPattern = Regex("""^\s*(\d+(?:\.\d+)?)(.*)$""")

@Composable
fun AmountText(
    amount: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
) {
    val match = amountPattern.find(amount)
    if (match == null) {
        Text(
            text = amount.ifBlank { "—" },
            modifier = modifier,
            style = style,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        return
    }

    val numeric = match.groupValues[1]
    val suffix = match.groupValues[2]
    val target = numeric.toFloatOrNull() ?: 0f
    val decimals = numeric.substringAfter('.', missingDelimiterValue = "").length
    val animated by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = 350),
        label = "amount",
    )
    Text(
        text = String.format(Locale.US, "%.${decimals}f", animated) + suffix,
        modifier = modifier,
        style = style,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}
