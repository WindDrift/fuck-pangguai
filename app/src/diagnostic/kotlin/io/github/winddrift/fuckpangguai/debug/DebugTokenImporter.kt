package io.github.winddrift.fuckpangguai.debug

import androidx.compose.runtime.Composable

@Composable
@Suppress("UNUSED_PARAMETER")
fun DebugTokenImportSection(onToken: (String) -> Unit) {
    // The diagnostic build keeps release signing and storage but does not expose token import.
}
