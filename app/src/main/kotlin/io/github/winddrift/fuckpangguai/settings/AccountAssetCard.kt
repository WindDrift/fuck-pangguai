package io.github.winddrift.fuckpangguai.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.winddrift.fuckpangguai.account.AssetSnapshot
import io.github.winddrift.fuckpangguai.account.StoredAccount
import io.github.winddrift.fuckpangguai.account.displayName
import io.github.winddrift.fuckpangguai.account.formatBalance
import io.github.winddrift.fuckpangguai.account.formatIntegral
import io.github.winddrift.fuckpangguai.account.pointsToYuan
import io.github.winddrift.fuckpangguai.ui.components.AccountAvatar
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Hide
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Replace
import top.yukonga.miuix.kmp.icon.extended.Show
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun AccountAssetCard(
    account: StoredAccount?,
    snapshot: AssetSnapshot?,
    loading: Boolean,
    error: String?,
    hidden: Boolean,
    onRefresh: () -> Unit,
    onToggleHidden: () -> Unit,
    onSwitchAccount: () -> Unit,
    onReauth: (() -> Unit)?,
) {
    val colors = MiuixTheme.colorScheme

    Card(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(0.dp),
    ) {
        if (account == null) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "未登录",
                    style = MiuixTheme.textStyles.title3,
                )
                Text(
                    text = "登录后可查看账号资料与资产。",
                    style = MiuixTheme.textStyles.body2,
                    color = colors.onSurfaceSecondary,
                )
            }
            return@Card
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AccountAvatar(
                    name = displayName(account),
                    avatarUrl = account.avatarUrl,
                    size = 52.dp,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = displayName(account),
                        style = MiuixTheme.textStyles.title3,
                    )
                    Text(
                        text = account.phoneMasked.ifBlank { "未同步手机号" },
                        style = MiuixTheme.textStyles.footnote1,
                        color = colors.onSurfaceSecondary,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                    IconButton(
                        onClick = {
                            if (!loading && !account.invalid) onRefresh()
                        },
                        backgroundColor = Color.Transparent,
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Refresh,
                            contentDescription = "刷新账号资产",
                        )
                    }
                    IconButton(
                        onClick = onToggleHidden,
                        backgroundColor = Color.Transparent,
                    ) {
                        Icon(
                            imageVector = if (hidden) MiuixIcons.Show else MiuixIcons.Hide,
                            contentDescription = if (hidden) "显示资产" else "隐藏资产",
                        )
                    }
                    IconButton(
                        onClick = onSwitchAccount,
                        backgroundColor = Color.Transparent,
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Replace,
                            contentDescription = "切换账号",
                        )
                    }
                }
            }

            if (account.invalid) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "登录已失效",
                        style = MiuixTheme.textStyles.body2,
                        color = colors.error,
                    )
                    if (onReauth != null) {
                        TextButton(text = "重新登录", onClick = onReauth)
                    }
                }
            }

            if (snapshot == null) {
                Text(
                    text = if (loading) "正在加载资产…" else "暂无资产数据",
                    style = MiuixTheme.textStyles.body2,
                    color = colors.onSurfaceSecondary,
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    AssetColumn(
                        label = "账户余额",
                        value = if (hidden) "••••" else "${formatBalance(snapshot.balance)} 元",
                        modifier = Modifier.weight(1f),
                    )
                    AssetColumn(
                        label = "通用小票",
                        value = if (hidden) "••••" else "${snapshot.tokenCoin} 点",
                        subValue = if (hidden) null else "≈ ¥${pointsToYuan(snapshot.tokenCoin)}",
                        modifier = Modifier.weight(1f),
                    )
                    AssetColumn(
                        label = "积分",
                        value = if (hidden) "••••" else formatIntegral(snapshot.integral),
                        modifier = Modifier.weight(1f),
                    )
                }
                Text(
                    text = buildString {
                        append("本金 ¥")
                        append(if (hidden) "••••" else pointsToYuan(snapshot.principalAmount))
                        append(" · 赠送 ¥")
                        append(if (hidden) "••••" else pointsToYuan(snapshot.presentAmount))
                        if (!snapshot.ticketsComplete) append("（部分）")
                    },
                    style = MiuixTheme.textStyles.footnote1,
                    color = colors.onSurfaceSecondary,
                )
            }

            if (error != null) {
                Text(
                    text = if (snapshot != null) "刷新失败，已保留可用数据：$error" else "刷新失败：$error",
                    style = MiuixTheme.textStyles.footnote1,
                    color = colors.error,
                )
            }
        }
    }
}

@Composable
private fun AssetColumn(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    subValue: String? = null,
) {
    val colors = MiuixTheme.colorScheme
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.footnote1,
            color = colors.onSurfaceSecondary,
        )
        Text(
            text = value,
            style = MiuixTheme.textStyles.body1,
        )
        if (subValue != null) {
            Text(
                text = subValue,
                style = MiuixTheme.textStyles.footnote1,
                color = colors.onSurfaceSecondary,
            )
        }
    }
}
