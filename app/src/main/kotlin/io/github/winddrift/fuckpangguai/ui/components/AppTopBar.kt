package io.github.winddrift.fuckpangguai.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back

@Composable
fun AppTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = title,
        navigationIcon = {
            if (onBack != null) {
                IconButton(
                    onClick = onBack,
                    backgroundColor = Color.Transparent,
                ) {
                    Icon(
                        imageVector = MiuixIcons.Back,
                        contentDescription = "返回",
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        },
        actions = actions,
    )
}
