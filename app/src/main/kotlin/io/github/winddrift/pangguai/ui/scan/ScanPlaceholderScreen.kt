package io.github.winddrift.pangguai.ui.scan

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun ScanPlaceholderScreen(onLogout: () -> Unit) {
    val colors = MiuixTheme.colorScheme
    Scaffold(
        topBar = { TopAppBar(title = "接水") },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.background)
                .padding(innerPadding)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Card(modifier = Modifier.padding(horizontal = 8.dp)) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "接水",
                        style = MiuixTheme.textStyles.title1,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "M0 工具链验证",
                        style = MiuixTheme.textStyles.body1,
                    )
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = { }) {
                        Text("开始扫码")
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onLogout) {
                        Text("退出登录")
                    }
                }
            }
        }
    }
}
