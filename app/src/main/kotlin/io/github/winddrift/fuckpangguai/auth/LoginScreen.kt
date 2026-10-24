package io.github.winddrift.fuckpangguai.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.winddrift.fuckpangguai.BuildConfig
import io.github.winddrift.fuckpangguai.R
import io.github.winddrift.fuckpangguai.debug.DebugTokenImportSection
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun LoginScreen(
    busy: Boolean,
    codeSent: Boolean,
    onSendCode: (String) -> Unit,
    onLogin: (String, String) -> Unit,
    onImportToken: (String) -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    var phone by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .background(colors.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_water_drop),
                    contentDescription = null,
                    modifier = Modifier.size(52.dp),
                    tint = Color.Unspecified,
                )
            }
            Text(
                text = stringResource(R.string.app_name),
                style = MiuixTheme.textStyles.title2,
            )
            Text(
                text = "扫码接水，快一点。",
                style = MiuixTheme.textStyles.body1,
                color = colors.onSurfaceSecondary,
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            insideMargin = PaddingValues(0.dp),
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "手机号登录",
                    style = MiuixTheme.textStyles.title3,
                )
                Text(
                    text = "使用胖乖生活账号登录；本应用只保存加密后的 token。",
                    style = MiuixTheme.textStyles.body2,
                    color = colors.onSurfaceSecondary,
                )
                TextField(
                    value = phone,
                    onValueChange = { phone = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = "手机号",
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                )
                TextField(
                    value = code,
                    onValueChange = { code = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = "验证码",
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(
                        text = if (codeSent) "重新发送验证码" else "发送验证码",
                        onClick = { onSendCode(phone) },
                        enabled = !busy,
                    )
                }
                Button(
                    onClick = { onLogin(phone, code) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !busy,
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) {
                    Text(if (busy) "处理中…" else "登录")
                }
            }
        }

        if (BuildConfig.DEBUG) {
            DebugTokenImportSection(onToken = onImportToken)
        }
    }
}
