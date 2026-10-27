package io.github.winddrift.fuckpangguai.points

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.winddrift.fuckpangguai.account.StoredAccount
import io.github.winddrift.fuckpangguai.account.formatIntegral
import io.github.winddrift.fuckpangguai.ui.components.ConfirmDialog
import io.github.winddrift.fuckpangguai.ui.components.EmptyState
import io.github.winddrift.fuckpangguai.ui.components.KeyValueRow
import io.github.winddrift.fuckpangguai.ui.components.PreferenceDivider
import io.github.winddrift.fuckpangguai.ui.components.PreferenceGroup
import io.github.winddrift.fuckpangguai.ui.components.SectionCard
import io.github.winddrift.fuckpangguai.ui.components.StatusCard
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Promotions
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun PointsScreen(
    controller: PointsController,
    account: StoredAccount?,
    scrollBehavior: ScrollBehavior? = null,
    bottomContentPadding: Dp = 0.dp,
) {
    val context = LocalContext.current
    val state by controller.state.collectAsStateWithLifecycle()
    val colors = MiuixTheme.colorScheme
    val lifecycleOwner = LocalLifecycleOwner.current
    var showRiskDialog by remember { mutableStateOf(false) }

    LaunchedEffect(account?.localId) {
        controller.onScreenVisible()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                controller.onScreenVisible()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (account == null) {
        EmptyState(
            icon = MiuixIcons.Promotions,
            title = "登录后使用积分",
            summary = "登录账号后可查看积分、签到和自动积分任务。",
            modifier = Modifier.fillMaxSize(),
        )
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (scrollBehavior != null) {
                    Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
                } else {
                    Modifier
                },
            ),
        contentPadding = PaddingValues(
            start = 16.dp,
            top = 12.dp,
            end = 16.dp,
            bottom = 12.dp + bottomContentPadding,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            PointsBalanceCard(
                integral = state.integral,
                loading = state.loading,
                running = state.running,
                onRefresh = controller::refresh,
            )
        }

        item {
            SignInCard(
                state = state.signIn,
                running = state.running,
                onSignIn = controller::signInNow,
            )
        }

        item {
            PreferenceGroup(title = "自动积分") {
                SwitchPreference(
                    checked = state.autoEnabled,
                    onCheckedChange = { checked ->
                        if (checked && !state.riskAcknowledged) {
                            showRiskDialog = true
                        } else {
                            controller.setAutoEnabled(checked)
                        }
                    },
                    title = "自动积分",
                    summary = "启动 App 或进入积分页时自动签到并完成任务；默认关闭",
                )
                PreferenceDivider()
                SwitchPreference(
                    checked = state.runOnLaunch,
                    onCheckedChange = controller::setRunOnLaunch,
                    title = "启动 App 时检查",
                    summary = "总开关开启后，App 启动或回前台时检查今日是否已运行",
                )
                PreferenceDivider()
                SwitchPreference(
                    checked = state.experimentEnabled,
                    onCheckedChange = controller::setExperimentEnabled,
                    title = "直接任务实验",
                    summary = "仅尝试 taskType 7、无广告/H5/验证码且通知权限已开启的任务",
                )
            }
        }

        item {
            SectionCard(title = "运行状态") {
                KeyValueRow("当前状态", state.runStatus)
                state.lastRun?.let { last ->
                    KeyValueRow("上次触发", last.trigger.label)
                    KeyValueRow("签到", last.signIn)
                    KeyValueRow(
                        "任务",
                        "尝试 ${last.attempted} / 完成 ${last.completed} / 跳过 ${last.skipped}",
                    )
                    if (last.captchaBlocked > 0) {
                        KeyValueRow("验证码", "${last.captchaBlocked} 次")
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (state.autoEnabled) {
                            "今日自动运行会在后台检查；失败后仅可手动重跑。"
                        } else {
                            "自动运行已关闭；可随时手动运行一次。"
                        },
                        modifier = Modifier.weight(1f),
                        style = MiuixTheme.textStyles.body2,
                        color = colors.onSurfaceSecondary,
                    )
                    Button(
                        onClick = controller::runNow,
                        enabled = !state.running,
                        colors = ButtonDefaults.buttonColorsPrimary(),
                    ) {
                        Text(if (state.running) "运行中…" else "立即运行")
                    }
                }
                state.error?.let {
                    Text(
                        text = it,
                        style = MiuixTheme.textStyles.body2,
                        color = colors.error,
                    )
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("任务", style = MiuixTheme.textStyles.title3)
                if (state.loading) {
                    Text(
                        "加载中…",
                        style = MiuixTheme.textStyles.body2,
                        color = colors.onSurfaceSecondary,
                    )
                }
            }
        }

        if (state.error != null && !state.tasksLoaded) {
            item {
                StatusCard(
                    title = "积分数据加载失败",
                    message = state.error.orEmpty(),
                    isError = true,
                )
            }
        }

        if (state.tasks.isEmpty() && state.tasksLoaded) {
            item {
                EmptyState(
                    icon = MiuixIcons.Promotions,
                    title = "暂无积分任务",
                    summary = "当前账号没有可展示的任务，或任务列表为空。",
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        itemsIndexed(
            items = state.tasks,
            key = { index, task -> task.taskCode.ifBlank { "task-$index" } },
        ) { _, task ->
            PointsTaskCard(
                task = task,
                running = state.running,
                onComplete = { controller.completeTask(task.taskCode) },
                onOpenNotificationSettings = { openNotificationSettings(context) },
            )
        }

        item {
            SectionCard(title = "运行日志") {
                if (state.logs.isEmpty()) {
                    Text(
                        text = "暂无日志",
                        style = MiuixTheme.textStyles.body2,
                        color = colors.onSurfaceSecondary,
                    )
                } else {
                    state.logs.takeLast(10).forEach { entry ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(
                                text = formatLogTime(entry.at),
                                style = MiuixTheme.textStyles.footnote1,
                                color = colors.onSurfaceSecondary,
                            )
                            Text(
                                text = entry.message,
                                modifier = Modifier.weight(1f),
                                style = MiuixTheme.textStyles.body2,
                                color = when (entry.level) {
                                    PointsLogLevel.Error -> colors.error
                                    PointsLogLevel.Warning -> colors.primary
                                    else -> colors.onSurface
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    ConfirmDialog(
        show = showRiskDialog,
        title = "开启自动积分？",
        summary = "自动签到和任务可能违反服务条款并触发账号风控。程序不会绕过验证码、伪造广告回调或后台运行；失败后当天不会自动重试。",
        confirmText = "了解并开启",
        onConfirm = {
            controller.acknowledgeRisk()
            controller.setAutoEnabled(true)
        },
        onDismiss = { showRiskDialog = false },
    )
}

@Composable
private fun PointsBalanceCard(
    integral: String,
    loading: Boolean,
    running: Boolean,
    onRefresh: () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    SectionCard(title = "积分余额") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "${formatIntegral(integral)} 积分",
                    style = MiuixTheme.textStyles.title1,
                )
                Text(
                    text = "参考价值约 ¥${referenceYuan(integral)}（400 积分 ≈ 1 元）",
                    style = MiuixTheme.textStyles.body2,
                    color = colors.onSurfaceSecondary,
                )
            }
            TextButton(
                text = if (loading) "刷新中…" else "刷新",
                onClick = onRefresh,
                enabled = !loading && !running,
            )
        }
    }
}

@Composable
private fun SignInCard(
    state: SignInUiState,
    running: Boolean,
    onSignIn: () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    SectionCard(title = "签到") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = when (state) {
                    SignInUiState.Unknown -> "正在读取签到状态…"
                    SignInUiState.NoActivity -> "暂无签到活动"
                    SignInUiState.SignedIn -> "今日已签到"
                    SignInUiState.NotSignedIn -> "今日还未签到"
                    SignInUiState.SigningIn -> "签到中…"
                },
                style = MiuixTheme.textStyles.body1,
                color = if (state == SignInUiState.NoActivity) {
                    colors.onSurfaceSecondary
                } else {
                    colors.onSurface
                },
            )
            if (state == SignInUiState.NotSignedIn) {
                Button(
                    onClick = onSignIn,
                    enabled = !running,
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) {
                    Text("签到")
                }
            }
        }
    }
}

@Composable
private fun PointsTaskCard(
    task: PointsTaskUi,
    running: Boolean,
    onComplete: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    val reason = (task.decision as? PointsTaskDecision.Skipped)?.reason
    val actionable = !task.completed && task.decision is PointsTaskDecision.AutoEligible
    val statusText = when {
        task.completed -> "已完成"
        actionable && running -> "运行中…"
        actionable -> "可自动完成"
        reason != null -> reason.label
        else -> ""
    }

    SectionCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(task.title, style = MiuixTheme.textStyles.body1)
                if (task.awardNumber > 0) {
                    Text(
                        text = "+${task.awardNumber} 积分",
                        style = MiuixTheme.textStyles.body2,
                        color = colors.primary,
                    )
                }
            }
            if (actionable) {
                TextButton(
                    text = if (running) "运行中" else "完成",
                    onClick = onComplete,
                    enabled = !running,
                )
            } else if (reason == PointsSkipReason.NotificationDisabled) {
                TextButton(
                    text = "开启通知",
                    onClick = onOpenNotificationSettings,
                )
            }
        }
        Text(
            text = statusText,
            style = MiuixTheme.textStyles.footnote1,
            color = if (task.completed) colors.onSurfaceSecondary else colors.onSurfaceSecondary,
        )
    }
}

private fun referenceYuan(integral: String): String =
    runCatching {
        BigDecimal(integral.trim().ifBlank { "0" })
            .divide(BigDecimal(400), 2, RoundingMode.HALF_UP)
            .toPlainString()
    }.getOrDefault("--")

private fun formatLogTime(at: Long): String =
    Instant.ofEpochMilli(at)
        .atZone(ZoneId.systemDefault())
        .toLocalTime()
        .format(DateTimeFormatter.ofPattern("HH:mm:ss"))

private fun openNotificationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    context.startActivity(intent)
}
