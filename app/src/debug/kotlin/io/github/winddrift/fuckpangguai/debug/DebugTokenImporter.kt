package io.github.winddrift.fuckpangguai.debug

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun DebugTokenImportSection(onToken: (String) -> Unit) {
    var token by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Debug：粘贴 token", style = MiuixTheme.textStyles.footnote1)
        TextField(
            value = token,
            onValueChange = { token = it },
            modifier = Modifier.fillMaxWidth(),
            label = "token",
        )
        Button(
            onClick = { onToken(token) },
            modifier = Modifier.fillMaxWidth(),
            enabled = token.isNotBlank(),
        ) {
            Text("导入 token")
        }
    }
}
