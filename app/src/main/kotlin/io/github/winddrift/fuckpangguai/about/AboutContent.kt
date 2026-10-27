package io.github.winddrift.fuckpangguai.about

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.winddrift.fuckpangguai.R
import io.github.winddrift.fuckpangguai.auth.DisclaimerSections
import io.github.winddrift.fuckpangguai.core.openExternalUrl
import io.github.winddrift.fuckpangguai.ui.components.LocalSnackbar
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal const val GITHUB_PROFILE_URL = "https://github.com/WindDrift"
internal const val GITHUB_REPO_URL = "https://github.com/WindDrift/fuck-pangguai"
internal const val RELEASES_URL = "https://github.com/WindDrift/fuck-pangguai/releases"
internal const val ISSUES_URL = "https://github.com/WindDrift/fuck-pangguai/issues"
internal const val LICENSE_URL = "https://github.com/WindDrift/fuck-pangguai/blob/master/LICENSE"
internal const val PRIVACY_URL = "https://github.com/WindDrift/fuck-pangguai/blob/master/docs/privacy.md"
internal const val REA_URL = "https://github.com/morluto/rea"

@Composable
internal fun DeveloperCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MiuixTheme.colorScheme
    Card(
        modifier = modifier.fillMaxWidth(),
        insideMargin = PaddingValues(0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(R.drawable.developer_avatar),
                contentDescription = "开发者头像",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 14.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = "浮笙-WindDrift",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurface,
                )
                Text(
                    text = "@WindDrift",
                    fontSize = 14.sp,
                    color = colors.onSurfaceSecondary,
                )
            }
            Icon(
                imageVector = MiuixIcons.ChevronForward,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = colors.onSurfaceSecondary,
            )
        }
    }
}

@Composable
internal fun LicenseSheet(
    show: Boolean,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val snackbar = LocalSnackbar.current
    val colors = MiuixTheme.colorScheme

    OverlayBottomSheet(
        show = show,
        title = "开源许可与致谢",
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 500.dp)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("GPL-3.0", style = MiuixTheme.textStyles.title3)
            Text(
                "本项目以 GNU GPL v3.0 发布。你可以自由使用、修改和分发；分发衍生作品时须同样以 GPL-3.0 开源并提供源代码。许可证不授予官方服务、商标、账号或数据的访问权。",
                style = MiuixTheme.textStyles.body2,
                color = colors.onSurfaceSecondary,
            )
            Text("主要开源组件", style = MiuixTheme.textStyles.title3)
            Text(
                "Miuix（Apache-2.0）\n" +
                    "OkHttp / Retrofit（Apache-2.0）\n" +
                    "CameraX（Apache-2.0）\n" +
                    "ZXing（Apache-2.0）\n" +
                    "Coil（Apache-2.0）\n" +
                    "kotlinx.serialization（Apache-2.0）\n" +
                    "AndroidX（Apache-2.0）",
                style = MiuixTheme.textStyles.body2,
                color = colors.onSurfaceSecondary,
            )
            Text("第三方 SDK", style = MiuixTheme.textStyles.title3)
            Text(
                "支付宝 SDK（闭源，仅用于通用小票充值）",
                style = MiuixTheme.textStyles.body2,
                color = colors.onSurfaceSecondary,
            )
            Text("图形效果", style = MiuixTheme.textStyles.title3)
            Text(
                "OS3 流体背景改编自 compose-miuix-ui 的实现（Apache-2.0）。",
                style = MiuixTheme.textStyles.body2,
                color = colors.onSurfaceSecondary,
            )
            Text("特别致谢", style = MiuixTheme.textStyles.title3)
            Text(
                "REA：胖乖生活软件的逆向分析工作由其支持。",
                style = MiuixTheme.textStyles.body2,
                color = colors.onSurfaceSecondary,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(
                    text = "关闭",
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = {
                        if (!context.openExternalUrl(LICENSE_URL)) {
                            snackbar.show("无法打开浏览器", isError = true)
                        }
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) {
                    Text("完整许可证")
                }
            }
        }
    }
}

@Composable
internal fun PrivacySheet(
    show: Boolean,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val snackbar = LocalSnackbar.current

    OverlayBottomSheet(
        show = show,
        title = "免责声明与隐私",
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 520.dp)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            DisclaimerSections()
            TextButton(
                text = "查看完整隐私说明",
                onClick = {
                    if (!context.openExternalUrl(PRIVACY_URL)) {
                        snackbar.show("无法打开浏览器", isError = true)
                    }
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
