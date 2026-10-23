package io.github.winddrift.fuckpangguai.debug

import androidx.compose.runtime.Composable

@Composable
@Suppress("UNUSED_PARAMETER")
fun DebugTokenImportSection(onToken: (String) -> Unit) {
    // Release builds intentionally do not expose token import.
}
