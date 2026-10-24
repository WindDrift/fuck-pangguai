package io.github.winddrift.fuckpangguai.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import io.github.winddrift.fuckpangguai.R
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun LoadingScreen() {
    val colors = MiuixTheme.colorScheme
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_water_drop),
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = Color.Unspecified,
            )
            InfiniteProgressIndicator(
                modifier = Modifier.size(28.dp),
                color = colors.primary,
            )
            Text("加载中…", style = MiuixTheme.textStyles.body1)
        }
    }
}
