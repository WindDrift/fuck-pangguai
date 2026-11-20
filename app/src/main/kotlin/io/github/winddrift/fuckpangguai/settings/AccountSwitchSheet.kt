package io.github.winddrift.fuckpangguai.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.winddrift.fuckpangguai.account.StoredAccount
import io.github.winddrift.fuckpangguai.account.displayName
import io.github.winddrift.fuckpangguai.ui.components.AccountAvatar
import io.github.winddrift.fuckpangguai.ui.components.ConfirmDialog
import io.github.winddrift.fuckpangguai.ui.components.PreferenceDivider
import io.github.winddrift.fuckpangguai.ui.components.rememberLastNonNull
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun AccountSwitchSheet(
    show: Boolean,
    accounts: List<StoredAccount>,
    activeLocalId: String?,
    onDismiss: () -> Unit,
    onSwitch: (String) -> Unit,
    onAdd: () -> Unit,
    onReauth: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit,
) {
    var renameTarget by remember { mutableStateOf<StoredAccount?>(null) }
    val renameValue = rememberTextFieldState()
    var deleteTarget by remember { mutableStateOf<StoredAccount?>(null) }

    LaunchedEffect(show) {
        if (!show) {
            renameTarget = null
            deleteTarget = null
        }
    }

    OverlayBottomSheet(
        show = show,
        title = "切换账号",
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "点击有效账号直接切换；已退出或失效的账号需要重新登录。",
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceSecondary,
            )
            Column(
                modifier = Modifier
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                accounts.forEachIndexed { index, account ->
                    if (index > 0) PreferenceDivider()
                    AccountRow(
                        account = account,
                        active = account.localId == activeLocalId,
                        onSelect = {
                            when {
                                account.invalid -> {
                                    onReauth(account.localId)
                                    onDismiss()
                                }

                                account.localId != activeLocalId -> {
                                    onSwitch(account.localId)
                                    onDismiss()
                                }
                            }
                        },
                        onRename = {
                            renameTarget = account
                            renameValue.setTextAndPlaceCursorAtEnd(account.alias.ifBlank { account.nickname })
                        },
                        onDelete = { deleteTarget = account },
                    )
                }
            }
            Button(
                onClick = {
                    onAdd()
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColorsPrimary(),
            ) {
                Text("添加账号")
            }
        }
    }

    val target = rememberLastNonNull(renameTarget)
    OverlayDialog(
        show = renameTarget != null,
        title = "重命名账号",
        summary = "仅修改本机显示名称，不会同步到服务器。",
        onDismissRequest = { renameTarget = null },
    ) {
        target?.let {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TextField(
                    state = renameValue,
                    modifier = Modifier.fillMaxWidth(),
                    label = "显示名称",
                    lineLimits = TextFieldLineLimits.SingleLine,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    TextButton(
                        text = "取消",
                        onClick = { renameTarget = null },
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        onClick = {
                            onRename(target.localId, renameValue.text.toString())
                            renameTarget = null
                        },
                        modifier = Modifier.weight(1f),
                        enabled = renameValue.text.isNotBlank(),
                        colors = ButtonDefaults.buttonColorsPrimary(),
                    ) {
                        Text("保存")
                    }
                }
            }
        }
    }

    val deleting = deleteTarget
    ConfirmDialog(
        show = deleting != null,
        title = "删除账号？",
        summary = "该账号的 token、资料、接水历史与本机缓存都会被删除，无法撤销。",
        confirmText = "删除",
        destructive = true,
        onConfirm = {
            deleting?.let { onDelete(it.localId) }
            deleteTarget = null
        },
        onDismiss = { deleteTarget = null },
    )
}

@Composable
private fun AccountRow(
    account: StoredAccount,
    active: Boolean,
    onSelect: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    val statusText = when {
        account.loggedOut -> "已退出"
        account.invalid -> "已失效"
        active -> "当前"
        else -> null
    }
    ArrowPreference(
        title = displayName(account),
        summary = buildString {
            statusText?.let { append(it).append(" · ") }
            append(account.phoneMasked.ifBlank { "未同步手机号" })
        },
        onClick = onSelect,
        startAction = {
            AccountAvatar(
                name = displayName(account),
                avatarUrl = account.avatarUrl,
                size = 40.dp,
            )
            Spacer(Modifier.width(12.dp))
        },
        endActions = {
            TextButton(text = "改名", onClick = onRename)
            TextButton(text = "删除", onClick = onDelete)
        },
    )
}
