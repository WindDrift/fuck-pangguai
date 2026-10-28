package io.github.winddrift.fuckpangguai.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun LogoutOptionsDialog(
    show: Boolean,
    recordCount: Int,
    exported: Boolean,
    onKeepAndLogout: () -> Unit,
    onExport: () -> Unit,
    onDeleteAndLogout: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    BackHandler(enabled = show) { onDismiss() }
    OverlayDialog(
        show = show,
        title = "退出当前账号？",
        summary = buildString {
            append("当前账号有 $recordCount 条接水记录。")
            if (exported) {
                append("CSV 已导出，请选择退出方式。")
            } else {
                append("退出前可以保留记录、先导出 CSV，或删除记录。")
                append("保留记录后账号会显示为已退出，重新登录同一手机号后仍可查看。")
            }
        },
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = onKeepAndLogout,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColorsPrimary(),
            ) {
                Text("保留记录并退出")
            }
            TextButton(
                text = if (exported) "再次导出 CSV" else "先导出 CSV",
                onClick = onExport,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = onDeleteAndLogout,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColorsPrimary(
                    color = colors.error,
                    contentColor = colors.onError,
                ),
            ) {
                Text("删除记录并退出")
            }
            TextButton(
                text = "取消",
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
