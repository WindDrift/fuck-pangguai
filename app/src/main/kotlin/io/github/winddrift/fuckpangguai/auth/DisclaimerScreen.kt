package io.github.winddrift.fuckpangguai.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.winddrift.fuckpangguai.ui.components.SectionCard
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.Lock
import top.yukonga.miuix.kmp.icon.extended.Report
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun DisclaimerScreen(onAccept: () -> Unit) {
    val colors = MiuixTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding(),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(colors.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = MiuixIcons.Info,
                        contentDescription = null,
                        modifier = Modifier.size(30.dp),
                        tint = colors.onPrimaryContainer,
                    )
                }
                Text("使用说明", style = MiuixTheme.textStyles.title2)
                Text(
                    "请先了解这些信息，再开始使用。",
                    style = MiuixTheme.textStyles.body2,
                    color = colors.onSurfaceSecondary,
                )
            }

            SectionCard(title = null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = MiuixIcons.Info,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = colors.primary,
                    )
                    Text("这是一个非官方客户端", style = MiuixTheme.textStyles.title3)
                }
                Text(
                    "本应用与胖乖生活、学校及饮水机运营方均无关联。它直接调用官方接口完成扫码、解锁和支付状态查询，不会绕过定位、实名或风控校验。",
                    style = MiuixTheme.textStyles.body1,
                )
            }

            SectionCard(title = null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = MiuixIcons.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = colors.primary,
                    )
                    Text("账号与数据", style = MiuixTheme.textStyles.title3)
                }
                Text(
                    "使用本应用可能需要使用你的手机号登录，并会在本地保存登录凭证和接水记录。登录凭证使用 Android Keystore 加密；本应用不包含广告、推送、统计 SDK。",
                    style = MiuixTheme.textStyles.body1,
                )
            }

            SectionCard(title = null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = MiuixIcons.Report,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = colors.error,
                    )
                    Text("使用风险", style = MiuixTheme.textStyles.title3)
                }
                Text(
                    "请确认你有权使用相关账号和设备。因账号风控、设备故障、支付失败或违反平台规则产生的后果由使用者自行承担。",
                    style = MiuixTheme.textStyles.body1,
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            Button(
                onClick = onAccept,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColorsPrimary(),
            ) {
                Text("我已了解并同意")
            }
        }
    }
}
