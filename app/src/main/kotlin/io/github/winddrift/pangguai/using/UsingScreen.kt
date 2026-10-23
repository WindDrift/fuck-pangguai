package io.github.winddrift.pangguai.using

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import io.github.winddrift.pangguai.water.SessionPhase
import io.github.winddrift.pangguai.water.WaterSession
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun UsingScreen(
    session: WaterSession,
    onEnd: () -> Unit,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
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

    Scaffold(
        topBar = { TopAppBar(title = "接水状态") },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.height(4.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(phaseLabel(session.phase), style = MiuixTheme.textStyles.title2)
                    Text(session.deviceName, style = MiuixTheme.textStyles.body1)
                    if (session.amount.isNotBlank()) {
                        Text("当前用量：${session.amount}", style = MiuixTheme.textStyles.body2)
                    }
                    if (session.orderNo.isNotBlank()) {
                        Text("解锁订单：${session.orderNo}", style = MiuixTheme.textStyles.footnote1)
                    }
                    if (session.payOrderNo.isNotBlank()) {
                        Text("结算订单：${session.payOrderNo}", style = MiuixTheme.textStyles.footnote1)
                    }
                    if (session.error.isNotBlank()) {
                        Text(session.error, style = MiuixTheme.textStyles.body2)
                    }
                    Text(
                        if (terminal) {
                            "本次接水已结束。请在原 App 核对账单/支付状态。"
                        } else {
                            "接水结束后会自动生成结算单；也可以直接按设备上的结算键。"
                        },
                        style = MiuixTheme.textStyles.body2,
                    )
                }
            }

            if (!terminal) {
                if (session.phase == SessionPhase.USING && session.supportAppUserFinish) {
                    Button(onClick = onEnd, modifier = Modifier.fillMaxWidth()) {
                        Text("结束使用")
                    }
                } else if (session.phase == SessionPhase.USING) {
                    Text(
                        "该设备不支持 App 内结束，请在设备上结束。",
                        style = MiuixTheme.textStyles.body2,
                    )
                } else {
                    Text(
                        "正在结算/结束中，请稍候，不要重复操作。",
                        style = MiuixTheme.textStyles.body2,
                    )
                }
            }

            if (session.phase == SessionPhase.PAY_UNKNOWN || session.phase == SessionPhase.FAULT) {
                Button(
                    onClick = {
                        runCatching {
                            val intent = context.packageManager
                                .getLaunchIntentForPackage(ORIGINAL_PACKAGE)
                            if (intent != null) {
                                context.startActivity(intent)
                            } else {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW)
                                        .setPackage(ORIGINAL_PACKAGE),
                                )
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("打开胖乖生活")
                }
            }

            if (terminal) {
                Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
                    Text("完成")
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun phaseLabel(phase: String): String = when (phase) {
    SessionPhase.USING -> "接水中"
    SessionPhase.ENDING -> "正在结束"
    SessionPhase.SETTLING -> "正在结算"
    SessionPhase.PAID -> "已支付"
    SessionPhase.FINISHED_NO_CHARGE -> "已结束（无费用）"
    SessionPhase.PAY_UNKNOWN -> "支付状态待确认"
    SessionPhase.FAULT -> "设备异常"
    else -> phase
}

private const val ORIGINAL_PACKAGE = "com.qiekj.user"
