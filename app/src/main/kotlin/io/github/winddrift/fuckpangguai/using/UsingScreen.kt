package io.github.winddrift.fuckpangguai.using

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import io.github.winddrift.fuckpangguai.ui.animation.ContentFadeSpec
import io.github.winddrift.fuckpangguai.ui.animation.ContentSlideSpec
import io.github.winddrift.fuckpangguai.ui.components.AmountText
import io.github.winddrift.fuckpangguai.ui.components.KeyValueRow
import io.github.winddrift.fuckpangguai.ui.components.PhaseBadge
import io.github.winddrift.fuckpangguai.ui.components.SectionCard
import io.github.winddrift.fuckpangguai.ui.components.SessionTimeline
import io.github.winddrift.fuckpangguai.water.SessionPhase
import io.github.winddrift.fuckpangguai.water.WaterSession
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun UsingScreen(session: WaterSession) {
    val context = LocalContext.current
    val colors = MiuixTheme.colorScheme
    val terminal = session.phase in SessionPhase.TERMINAL
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionCard(title = null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PhaseBadge(session.phase)
                Text(
                    text = session.deviceName,
                    style = MiuixTheme.textStyles.body2,
                    color = colors.onSurfaceSecondary,
                )
            }
            Text(
                text = "本次用量",
                style = MiuixTheme.textStyles.footnote1,
                color = colors.onSurfaceSecondary,
            )
            AmountText(
                amount = session.amount,
                style = MiuixTheme.textStyles.title1,
                color = colors.primary,
            )
            AnimatedContent(
                targetState = terminal,
                transitionSpec = {
                    (fadeIn(ContentFadeSpec) + slideInVertically(ContentSlideSpec) { it / 3 }) togetherWith
                        (fadeOut(ContentFadeSpec) + slideOutVertically(ContentSlideSpec) { -it / 3 })
                },
                label = "sessionTerminal",
            ) { ended ->
                Text(
                    text = if (ended) "本次接水已结束" else "接水进行中",
                    style = MiuixTheme.textStyles.footnote1,
                    color = colors.onSurfaceSecondary,
                )
            }
        }

        SectionCard(title = "进度") {
            SessionTimeline(phase = session.phase)
        }

        SectionCard(title = "本次详情") {
            KeyValueRow("设备", session.deviceName)
            if (session.orderNo.isNotBlank()) {
                KeyValueRow("解锁订单", session.orderNo)
            }
            if (session.payOrderNo.isNotBlank()) {
                KeyValueRow("结算订单", session.payOrderNo)
            }
            if (session.error.isNotBlank()) {
                Text(
                    text = session.error,
                    style = MiuixTheme.textStyles.body2,
                    color = colors.error,
                )
            }
            Text(
                text = if (terminal) {
                    "本次接水已结束。请在原 App 核对账单/支付状态。"
                } else {
                    "接水结束后会自动生成结算单；也可以直接按设备上的结算键。"
                },
                style = MiuixTheme.textStyles.body2,
                color = colors.onSurfaceSecondary,
            )
            if (!terminal && session.phase == SessionPhase.USING && !session.supportAppUserFinish) {
                Text(
                    text = "该设备不支持 App 内结束，请在设备上结束。",
                    style = MiuixTheme.textStyles.body2,
                    color = colors.error,
                )
            }
            if (session.phase == SessionPhase.ENDING || session.phase == SessionPhase.SETTLING) {
                Text(
                    text = "正在结算/结束中，请稍候，不要重复操作。",
                    style = MiuixTheme.textStyles.body2,
                    color = colors.onSurfaceSecondary,
                )
            }
        }
    }
}
