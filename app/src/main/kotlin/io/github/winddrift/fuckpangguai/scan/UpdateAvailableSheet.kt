package io.github.winddrift.fuckpangguai.scan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.winddrift.fuckpangguai.BuildConfig
import io.github.winddrift.fuckpangguai.core.openExternalUrl
import io.github.winddrift.fuckpangguai.ui.components.LocalSnackbar
import io.github.winddrift.fuckpangguai.update.UpdateState
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import io.github.winddrift.fuckpangguai.ui.components.rememberLastNonNull
import top.yukonga.miuix.kmp.window.WindowBottomSheet
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun UpdateAvailableSheet(
    show: Boolean,
    update: UpdateState.Available?,
    onDismiss: () -> Unit,
) {
    val sheetUpdate = rememberLastNonNull(update)

    val context = LocalContext.current
    val snackbar = LocalSnackbar.current
    val colors = MiuixTheme.colorScheme

    WindowBottomSheet(
        show = show && update != null,
        title = sheetUpdate?.let { "发现新版本 v${it.version}" }.orEmpty(),
        onDismissRequest = onDismiss,
    ) {
        sheetUpdate?.let { update ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "当前版本 ${BuildConfig.VERSION_NAME}（${BuildConfig.VERSION_CODE}）",
                style = MiuixTheme.textStyles.footnote1,
                color = colors.onSurfaceSecondary,
            )
            Text(
                text = update.notes.ifBlank { "建议升级到最新版本以获取修复与改进。" },
                modifier = Modifier
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState()),
                style = MiuixTheme.textStyles.body2,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(
                    text = "稍后",
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = {
                        val url = update.releaseUrl.ifBlank { RELEASES_URL }
                        if (!context.openExternalUrl(url)) {
                            snackbar.show("无法打开浏览器", isError = true)
                        }
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) {
                    Text("前往 Release")
                }
            }
        }
        }
    }
}

private const val RELEASES_URL = "https://github.com/WindDrift/fuck-pangguai/releases"
