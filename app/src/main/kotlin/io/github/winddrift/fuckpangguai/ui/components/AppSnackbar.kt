package io.github.winddrift.fuckpangguai.ui.components

import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.SnackbarDuration
import top.yukonga.miuix.kmp.basic.SnackbarHostState

@Stable
class AppSnackbarState(
    private val host: SnackbarHostState,
    private val scope: CoroutineScope,
) {
    fun show(message: String, isError: Boolean = false) {
        scope.launch {
            host.showSnackbar(
                message = message,
                withDismissAction = isError,
                duration = if (isError) SnackbarDuration.Long else SnackbarDuration.Short,
            )
        }
    }
}

val LocalSnackbar = staticCompositionLocalOf<AppSnackbarState> {
    error("AppSnackbarState is not provided")
}
