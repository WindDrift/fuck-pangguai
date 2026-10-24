package io.github.winddrift.fuckpangguai.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun AccountAvatar(
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
) {
    val colors = MiuixTheme.colorScheme
    val initial = name.trim().firstOrNull()?.toString() ?: "?"
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(colors.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initial,
            style = MiuixTheme.textStyles.title3,
            color = colors.onSurface,
        )
    }
}
