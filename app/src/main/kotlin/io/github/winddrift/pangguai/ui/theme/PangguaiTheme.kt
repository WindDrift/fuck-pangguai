package io.github.winddrift.pangguai.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

@Composable
fun PangguaiTheme(content: @Composable () -> Unit) {
    MiuixTheme(
        if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme(),
    ) {
        content()
    }
}
