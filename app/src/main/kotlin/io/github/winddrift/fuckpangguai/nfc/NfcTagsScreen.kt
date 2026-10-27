package io.github.winddrift.fuckpangguai.nfc

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.winddrift.fuckpangguai.ui.components.ConfirmDialog
import io.github.winddrift.fuckpangguai.ui.components.KeyValueRow
import io.github.winddrift.fuckpangguai.ui.components.LocalSnackbar
import io.github.winddrift.fuckpangguai.ui.components.PreferenceGroup
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun NfcTagsScreen(
    nfcTagStore: NfcTagStore,
    onScan: () -> Unit,
    scrollBehavior: ScrollBehavior? = null,
) {
    val context = LocalContext.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val nfc = rememberNfcAvailability()
    val colors = MiuixTheme.colorScheme
    var mappings by remember { mutableStateOf<List<NfcTagBinding>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<NfcTagBinding?>(null) }

    suspend fun loadMappings() {
        mappings = runCatching { nfcTagStore.load() }
            .getOrElse {
                snackbar.show("读取 NFC 绑定失败", isError = true)
                emptyList()
            }
            .sortedByDescending { it.updatedAt }
    }

    LaunchedEffect(Unit) {
        loadMappings()
        loaded = true
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .then(
                if (scrollBehavior != null) {
                    Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
                } else {
                    Modifier
                },
            ),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when (nfc) {
            NfcAvailability.Disabled -> item {
                NfcStatusCard(
                    title = "NFC 未开启",
                    summary = "已绑定的标签仍可查看和删除；开启 NFC 后才能在扫码页读取标签。",
                    actionText = "去系统设置",
                    onAction = {
                        if (!openNfcSettings(context)) {
                            snackbar.show("无法打开 NFC 设置", isError = true)
                        }
                    },
                )
            }

            NfcAvailability.Unsupported -> item {
                NfcStatusCard(
                    title = "设备不支持 NFC",
                    summary = "当前设备没有 NFC 硬件，无法读取或新增标签绑定；已绑定的标签仍可管理。",
                )
            }

            NfcAvailability.Enabled -> item {
                Text(
                    text = "碰一下标签即可识别已绑定的设备；在扫码页识别新设备后确认绑定。",
                    style = MiuixTheme.textStyles.footnote1,
                    color = colors.onSurfaceSecondary,
                )
            }
        }

        when {
            !loaded -> item {
                PreferenceGroup(title = "已绑定标签") {
                    BasicComponent(
                        title = "正在读取绑定…",
                    )
                }
            }

            mappings.isEmpty() -> {
                item {
                    PreferenceGroup(title = "已绑定标签") {
                        BasicComponent(
                            title = "暂无绑定",
                            summary = "在扫码页碰一下 NFC 标签，再扫描设备码即可绑定。",
                        )
                    }
                }
                item {
                    Button(
                        onClick = onScan,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColorsPrimary(),
                    ) {
                        Text("去扫码绑定")
                    }
                }
            }

            else -> {
                item {
                    Text(
                        text = "共 ${mappings.size} 个绑定",
                        style = MiuixTheme.textStyles.footnote1,
                        color = colors.onSurfaceSecondary,
                    )
                }
                items(mappings, key = { it.tagId }) { binding ->
                    BindingCard(
                        binding = binding,
                        onDelete = { pendingDelete = binding },
                    )
                }
            }
        }
    }

    ConfirmDialog(
        show = pendingDelete != null,
        title = "删除该 NFC 绑定？",
        summary = pendingDelete?.let { "${it.tagId} → ${it.sn}" },
        confirmText = "删除",
        destructive = true,
        onConfirm = {
            val binding = pendingDelete ?: return@ConfirmDialog
            scope.launch {
                runCatching { nfcTagStore.remove(binding.tagId) }
                    .onSuccess {
                        loadMappings()
                        snackbar.show("映射已删除")
                    }
                    .onFailure { snackbar.show("删除失败", isError = true) }
            }
        },
        onDismiss = { pendingDelete = null },
    )
}

@Composable
private fun NfcStatusCard(
    title: String,
    summary: String,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val colors = MiuixTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(0.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text = title, style = MiuixTheme.textStyles.title3)
            Text(
                text = summary,
                style = MiuixTheme.textStyles.body2,
                color = colors.onSurfaceSecondary,
            )
            if (actionText != null && onAction != null) {
                Button(
                    onClick = onAction,
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) {
                    Text(actionText)
                }
            }
        }
    }
}

@Composable
private fun BindingCard(
    binding: NfcTagBinding,
    onDelete: () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(0.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = binding.deviceName.ifBlank { "饮水机" },
                        style = MiuixTheme.textStyles.title3,
                    )
                    Text(
                        text = binding.tagId,
                        style = MiuixTheme.textStyles.footnote1,
                        color = colors.onSurfaceSecondary,
                    )
                }
                IconButton(
                    onClick = onDelete,
                    backgroundColor = Color.Transparent,
                ) {
                    Icon(
                        imageVector = MiuixIcons.Delete,
                        contentDescription = "删除该绑定",
                        tint = colors.error,
                    )
                }
            }
            HorizontalDivider()
            KeyValueRow("SN", binding.sn)
            KeyValueRow("商品 ID", binding.goodsId)
            KeyValueRow("绑定时间", formatBindingTime(binding.updatedAt))
        }
    }
}

private fun formatBindingTime(epochMillis: Long): String {
    if (epochMillis <= 0L) return "未知"
    return runCatching {
        Instant.ofEpochMilli(epochMillis)
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
    }.getOrDefault("未知")
}
