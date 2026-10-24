package io.github.winddrift.fuckpangguai.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import io.github.winddrift.fuckpangguai.storage.ThemeSettings
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

@Composable
fun PangguaiTheme(
    settings: ThemeSettings = ThemeSettings(),
    content: @Composable () -> Unit,
) {
    val mode = settings.mode.toColorSchemeMode(settings.dynamicColor)
    val controller = remember(mode) { ThemeController(mode) }
    MiuixTheme(controller = controller) {
        content()
    }
}
