package io.github.winddrift.fuckpangguai.auth

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun DisclaimerScreen(onAccept: () -> Unit) {
    Scaffold(
        topBar = { TopAppBar(title = "使用说明") },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("这是一个非官方客户端", style = MiuixTheme.textStyles.title2)
            Text(
                "本应用与胖乖生活、学校及饮水机运营方均无关联。它直接调用官方接口完成扫码、解锁和支付状态查询，不会绕过定位、实名或风控校验。",
                style = MiuixTheme.textStyles.body1,
            )
            Text(
                "使用本应用可能需要使用你的手机号登录，并会在本地保存登录凭证和接水记录。登录凭证使用 Android Keystore 加密；本应用不包含广告、推送、统计 SDK。",
                style = MiuixTheme.textStyles.body1,
            )
            Text(
                "请确认你有权使用相关账号和设备。因账号风控、设备故障、支付失败或违反平台规则产生的后果由使用者自行承担。",
                style = MiuixTheme.textStyles.body1,
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onAccept,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("我已了解并同意")
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
