package io.github.winddrift.fuckpangguai.debug

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun DebugTokenImportSection(onToken: (String) -> Unit) {
    val token = rememberTextFieldState()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Debug：粘贴 token", style = MiuixTheme.textStyles.footnote1)
        TextField(
            state = token,
            modifier = Modifier.fillMaxWidth(),
            label = "token",
        )
        Button(
            onClick = { onToken(token.text.toString()) },
            modifier = Modifier.fillMaxWidth(),
            enabled = token.text.isNotBlank(),
        ) {
            Text("导入 token")
        }
    }
}
