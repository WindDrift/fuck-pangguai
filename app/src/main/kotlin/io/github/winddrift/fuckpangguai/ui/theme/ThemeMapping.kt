package io.github.winddrift.fuckpangguai.ui.theme

import io.github.winddrift.fuckpangguai.storage.AppThemeMode
import top.yukonga.miuix.kmp.theme.ColorSchemeMode

fun AppThemeMode.resolveDark(systemDark: Boolean): Boolean = when (this) {
    AppThemeMode.System -> systemDark
    AppThemeMode.Light -> false
    AppThemeMode.Dark -> true
}

fun AppThemeMode.toColorSchemeMode(dynamicColor: Boolean): ColorSchemeMode = when (this) {
    AppThemeMode.System -> if (dynamicColor) ColorSchemeMode.MonetSystem else ColorSchemeMode.System
    AppThemeMode.Light -> if (dynamicColor) ColorSchemeMode.MonetLight else ColorSchemeMode.Light
    AppThemeMode.Dark -> if (dynamicColor) ColorSchemeMode.MonetDark else ColorSchemeMode.Dark
}
