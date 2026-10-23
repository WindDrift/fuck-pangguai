package io.github.winddrift.fuckpangguai.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.winddrift.fuckpangguai.BuildConfig
import io.github.winddrift.fuckpangguai.history.HistoryRepository
import io.github.winddrift.fuckpangguai.storage.AppSettingsStore
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun SettingsScreen(
    settingsStore: AppSettingsStore,
    historyRepository: HistoryRepository,
    onBack: () -> Unit,
    onOpenDebug: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var oneTap by remember { mutableStateOf(false) }
    var dryRun by remember { mutableStateOf(true) }
    var versionTaps by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        oneTap = settingsStore.isOneTapEnabled()
        dryRun = settingsStore.isDryRun()
    }

    Scaffold(
        topBar = { TopAppBar(title = "设置") },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.height(4.dp))

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("一键解锁", style = MiuixTheme.textStyles.body1)
                            Text(
                                "同一设备首次确认后，后续扫码直接解锁",
                                style = MiuixTheme.textStyles.footnote1,
                            )
                        }
                        Switch(
                            checked = oneTap,
                            onCheckedChange = { checked ->
                                oneTap = checked
                                scope.launch { settingsStore.setOneTapEnabled(checked) }
                            },
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("演练模式", style = MiuixTheme.textStyles.body1)
                            Text(
                                "只展示请求，不真实调用 water/unlock",
                                style = MiuixTheme.textStyles.footnote1,
                            )
                        }
                        Switch(
                            checked = dryRun,
                            onCheckedChange = { checked ->
                                dryRun = checked
                                scope.launch { settingsStore.setDryRun(checked) }
                            },
                        )
                    }
                }
            }

            Button(
                onClick = {
                    scope.launch {
                        historyRepository.clear()
                        message = "历史记录已清空"
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("清空接水历史")
            }

            message?.let { Text(it, style = MiuixTheme.textStyles.footnote1) }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("关于", style = MiuixTheme.textStyles.title3)
                    Text(
                        "非官方客户端，与胖乖生活、学校及设备运营方无关。",
                        style = MiuixTheme.textStyles.body2,
                    )
                    Text(
                        "版本 ${BuildConfig.VERSION_NAME}（${BuildConfig.VERSION_CODE}）",
                        modifier = Modifier.clickable {
                            versionTaps += 1
                            if (versionTaps >= 7) {
                                versionTaps = 0
                                onOpenDebug()
                            }
                        },
                        style = MiuixTheme.textStyles.footnote1,
                    )
                }
            }

            Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text("返回")
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
