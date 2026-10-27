package io.github.winddrift.fuckpangguai.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.winddrift.fuckpangguai.BuildConfig
import io.github.winddrift.fuckpangguai.R
import io.github.winddrift.fuckpangguai.account.AuthTarget
import io.github.winddrift.fuckpangguai.account.StoredAccount
import io.github.winddrift.fuckpangguai.account.displayName
import io.github.winddrift.fuckpangguai.debug.DebugTokenImportSection
import io.github.winddrift.fuckpangguai.ui.components.AccountAvatar
import io.github.winddrift.fuckpangguai.ui.components.PreferenceDivider
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
    accounts: List<StoredAccount> = emptyList(),
    activeLocalId: String? = null,
    target: AuthTarget = AuthTarget.None,
    prefillPhone: String = "",
    onSelectAccount: (StoredAccount) -> Unit = {},
    onRequestReauth: (StoredAccount) -> Unit = {},
    onCancel: (() -> Unit)? = null,
    topInsetHandled: Boolean = false,
) {
    val colors = MiuixTheme.colorScheme
    val focusManager = LocalFocusManager.current
    var phone by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(prefillPhone) {
        if (prefillPhone.isNotBlank()) phone = prefillPhone
    }

    val title = when (target) {
        AuthTarget.AddAccount -> "添加账号"
        is AuthTarget.Reauth -> "重新登录"
        AuthTarget.None -> stringResource(R.string.app_name)
    }
    val subtitle = when (target) {
        AuthTarget.AddAccount -> "登录另一个胖乖生活账号，当前账号会保留。"
        is AuthTarget.Reauth -> "该账号的登录已失效，请重新验证手机号。"
        AuthTarget.None -> "扫码接水，快一点。"
    }

    val windowInsets = if (topInsetHandled) {
        WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
    } else {
        WindowInsets.safeDrawing
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(windowInsets)
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
                text = title,
                style = MiuixTheme.textStyles.title2,
            )
            Text(
                text = subtitle,
                style = MiuixTheme.textStyles.body1,
                color = colors.onSurfaceSecondary,
            )
        }

        if (accounts.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                insideMargin = PaddingValues(0.dp),
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "已保存账号",
                        style = MiuixTheme.textStyles.title3,
                    )
                    Text(
                        text = "点击有效账号直接切换；失效账号需要重新登录。",
                        style = MiuixTheme.textStyles.footnote1,
                        color = colors.onSurfaceSecondary,
                        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                    )
                    accounts.forEachIndexed { index, account ->
                        if (index > 0) PreferenceDivider()
                        SavedAccountRow(
                            account = account,
                            active = account.localId == activeLocalId,
                            onSelect = {
                                if (account.localId != activeLocalId) {
                                    if (account.invalid) {
                                        onRequestReauth(account)
                                    } else {
                                        onSelectAccount(account)
                                    }
                                }
                            },
                        )
                    }
                }
            }
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
                    text = if (target is AuthTarget.Reauth) "验证码重新登录" else "手机号登录",
                    style = MiuixTheme.textStyles.title3,
                )
                Text(
                    text = "账号 token 与手机号仅加密保存在本机。",
                    style = MiuixTheme.textStyles.body2,
                    color = colors.onSurfaceSecondary,
                )
                TextField(
                    value = phone,
                    onValueChange = { phone = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = "手机号",
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Phone,
                        imeAction = ImeAction.Next,
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Down) },
                    ),
                )
                TextField(
                    value = code,
                    onValueChange = { code = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = "验证码",
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { if (!busy) onLogin(phone, code) },
                    ),
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
                    Text(
                        when {
                            busy -> "处理中…"
                            target is AuthTarget.AddAccount -> "登录并添加"
                            target is AuthTarget.Reauth -> "重新登录"
                            else -> "登录"
                        },
                    )
                }
                if (onCancel != null) {
                    TextButton(
                        text = "取消",
                        onClick = onCancel,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !busy,
                    )
                }
            }
        }

        if (BuildConfig.DEBUG) {
            DebugTokenImportSection(onToken = onImportToken)
        }
    }
}

@Composable
private fun SavedAccountRow(
    account: StoredAccount,
    active: Boolean,
    onSelect: () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AccountAvatar(
            name = displayName(account),
            avatarUrl = account.avatarUrl,
            size = 40.dp,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = displayName(account),
                style = MiuixTheme.textStyles.body1,
            )
            Text(
                text = account.phoneMasked.ifBlank { "未同步手机号" },
                style = MiuixTheme.textStyles.footnote1,
                color = colors.onSurfaceSecondary,
            )
        }
        when {
            account.invalid -> Text(
                text = "已失效",
                style = MiuixTheme.textStyles.footnote1,
                color = colors.error,
            )

            active -> Text(
                text = "当前",
                style = MiuixTheme.textStyles.footnote1,
                color = colors.primary,
            )
        }
    }
}
