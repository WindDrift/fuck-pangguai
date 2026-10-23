package io.github.winddrift.pangguai.debug

import android.content.ClipData
import android.content.ClipboardManager
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.winddrift.pangguai.nfc.NfcTagBinding
import io.github.winddrift.pangguai.nfc.NfcTagStore
import io.github.winddrift.pangguai.net.RequestLog
import io.github.winddrift.pangguai.storage.AppSettingsStore
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun DebugScreen(
    settingsStore: AppSettingsStore,
    nfcTagStore: NfcTagStore,
    onImportToken: (String) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val entries by RequestLog.entries.collectAsStateWithLifecycle()
    var mappings by remember { mutableStateOf<List<NfcTagBinding>>(emptyList()) }
    var dryRun by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        mappings = nfcTagStore.load()
        dryRun = settingsStore.isDryRun()
    }

    Scaffold(
        topBar = { TopAppBar(title = "诊断") },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Spacer(Modifier.height(4.dp))
            Text("仅用于调试；Release 不记录完整请求。", style = MiuixTheme.textStyles.footnote1)

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("演练模式", style = MiuixTheme.textStyles.body1)
                        Switch(
                            checked = dryRun,
                            onCheckedChange = { checked ->
                                dryRun = checked
                                scope.launch { settingsStore.setDryRun(checked) }
                            },
                        )
                    }
                    DebugTokenImportSection(onToken = onImportToken)
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("NFC 标签映射", style = MiuixTheme.textStyles.title3)
                    if (mappings.isEmpty()) {
                        Text("暂无绑定", style = MiuixTheme.textStyles.footnote1)
                    }
                    mappings.forEach { mapping ->
                        Text(
                            "${mapping.tagId} → ${mapping.sn}（${mapping.deviceName}）",
                            style = MiuixTheme.textStyles.footnote1,
                        )
                        Button(
                            onClick = {
                                scope.launch {
                                    nfcTagStore.remove(mapping.tagId)
                                    mappings = nfcTagStore.load()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("删除该映射")
                        }
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("最近请求（token/lat/lng 已脱敏）", style = MiuixTheme.textStyles.title3)
                    entries.take(10).forEach { entry ->
                        Text(
                            "${entry.method} ${entry.statusCode ?: "-"} " +
                                "${entry.durationMs}ms\n${entry.url}",
                            style = MiuixTheme.textStyles.footnote1,
                        )
                        if (entry.error != null) {
                            Text("error: ${entry.error}", style = MiuixTheme.textStyles.footnote1)
                        }
                    }
                    if (entries.isEmpty()) {
                        Text("暂无请求", style = MiuixTheme.textStyles.footnote1)
                    }
                    val latest = entries.firstOrNull()
                    Button(
                        onClick = {
                            latest?.let { entry ->
                                val clipboard = context.getSystemService(ClipboardManager::class.java)
                                clipboard?.setPrimaryClip(
                                    ClipData.newPlainText("curl", entry.toCurl()),
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = latest != null,
                    ) {
                        Text("复制最近请求 cURL")
                    }
                    Button(
                        onClick = { RequestLog.clear() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = entries.isNotEmpty(),
                    ) {
                        Text("清空请求日志")
                    }
                }
            }

            Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text("返回")
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
