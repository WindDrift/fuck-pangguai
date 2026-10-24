package io.github.winddrift.fuckpangguai.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun AccountAvatar(
    name: String,
    avatarUrl: String = "",
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
) {
    if (!avatarUrl.startsWith("https://")) {
        AvatarFallback(name = name, modifier = modifier.size(size))
        return
    }
    SubcomposeAsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(avatarUrl)
            .crossfade(true)
            .build(),
        contentDescription = null,
        modifier = modifier
            .size(size)
            .clip(CircleShape),
        contentScale = ContentScale.Crop,
        loading = {
            AvatarFallback(name = name, modifier = Modifier.fillMaxSize())
        },
        error = {
            AvatarFallback(name = name, modifier = Modifier.fillMaxSize())
        },
    )
}

@Composable
private fun AvatarFallback(
    name: String,
    modifier: Modifier = Modifier,
) {
    val colors = MiuixTheme.colorScheme
    val initial = name.trim().firstOrNull()?.toString() ?: "?"
    Box(
        modifier = modifier
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
