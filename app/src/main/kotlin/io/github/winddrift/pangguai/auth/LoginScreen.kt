package io.github.winddrift.pangguai.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.winddrift.pangguai.AppMessage
import io.github.winddrift.pangguai.debug.DebugTokenImportSection
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun LoginScreen(
    busy: Boolean,
    message: AppMessage?,
    codeSent: Boolean,
    onSendCode: (String) -> Unit,
    onLogin: (String, String) -> Unit,
    onImportToken: (String) -> Unit,
) {
    var phone by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }

    Scaffold(
        topBar = { TopAppBar(title = "登录") },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.height(16.dp))
            Text("手机号登录", style = MiuixTheme.textStyles.title2)
            Text(
                "使用胖乖生活账号登录；本应用只保存加密后的 token。",
                style = MiuixTheme.textStyles.body1,
            )
            TextField(
                value = phone,
                onValueChange = { phone = it },
                modifier = Modifier.fillMaxWidth(),
                label = "手机号",
            )
            TextField(
                value = code,
                onValueChange = { code = it },
                modifier = Modifier.fillMaxWidth(),
                label = "验证码",
            )
            Button(
                onClick = { onSendCode(phone) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy,
            ) {
                Text(if (codeSent) "重新发送验证码" else "发送验证码")
            }
            Button(
                onClick = { onLogin(phone, code) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy,
            ) {
                Text(if (busy) "处理中…" else "登录")
            }
            message?.let {
                Text(
                    text = it.text,
                    style = MiuixTheme.textStyles.footnote1,
                )
            }
            Spacer(Modifier.height(12.dp))
            DebugTokenImportSection(onToken = onImportToken)
            Spacer(Modifier.height(24.dp))
        }
    }
}
