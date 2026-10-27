package io.github.winddrift.fuckpangguai.about

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.winddrift.fuckpangguai.BuildConfig
import io.github.winddrift.fuckpangguai.R
import io.github.winddrift.fuckpangguai.core.openExternalUrl
import io.github.winddrift.fuckpangguai.ui.components.LocalSnackbar
import io.github.winddrift.fuckpangguai.ui.components.PreferenceDivider
import io.github.winddrift.fuckpangguai.ui.components.PreferenceGroup
import io.github.winddrift.fuckpangguai.ui.components.copyTextToClipboard
import io.github.winddrift.fuckpangguai.update.UpdateState
import io.github.winddrift.fuckpangguai.update.userMessage
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.Link
import top.yukonga.miuix.kmp.icon.extended.Lock
import top.yukonga.miuix.kmp.icon.extended.Report
import top.yukonga.miuix.kmp.icon.extended.Update
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun AboutScreen(
    updateState: UpdateState,
    onCheckUpdate: () -> Unit,
    onShowUpdate: () -> Unit,
    onOpenDebug: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val snackbar = LocalSnackbar.current
    val colors = MiuixTheme.colorScheme
    val dark = colors.surface.luminance() < 0.5f
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val listState = rememberLazyListState()

    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val heroHeight = 280.dp + statusBarTop
    val heroHeightPx = with(density) { heroHeight.toPx() }

    val progress by remember(heroHeightPx) {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) {
                1f
            } else {
                (listState.firstVisibleItemScrollOffset / heroHeightPx).coerceIn(0f, 1f)
            }
        }
    }
    val heroAlpha = (1f - progress * 1.4f).coerceIn(0f, 1f)
    val heroScale = 1f - progress * 0.08f
    val topBarProgress = ((progress - 0.55f) / 0.35f).coerceIn(0f, 1f)

    val appName = stringResource(R.string.app_name)
    val versionLabel = "${BuildConfig.VERSION_NAME}（${BuildConfig.VERSION_CODE}）"

    var showLicense by remember { mutableStateOf(false) }
    var showPrivacy by remember { mutableStateOf(false) }
    var versionTaps by remember { mutableIntStateOf(0) }

    fun openUrl(url: String) {
        if (!context.openExternalUrl(url)) {
            snackbar.show("无法打开浏览器", isError = true)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surface),
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                bottom = navBarBottom + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "heroSpacer") {
                Spacer(Modifier.height(heroHeight + 16.dp))
            }

            item(key = "developer") {
                Column {
                    SmallTitle("开发者")
                    DeveloperCard(onClick = { openUrl(GITHUB_PROFILE_URL) })
                }
            }

            item(key = "update") {
                PreferenceGroup(title = "更新") {
                    val checking = updateState == UpdateState.Checking
                    val summary = when (val state = updateState) {
                        UpdateState.Idle -> "检查 GitHub 最新版本"
                        UpdateState.Checking -> "正在检查更新…"
                        UpdateState.UpToDate -> "已是最新版本"
                        is UpdateState.Available -> "发现新版本 v${state.version}，点击查看"
                        is UpdateState.Failed -> "${state.error.userMessage()}，点击重试"
                    }
                    BasicComponent(
                        title = "检查更新",
                        summary = summary,
                        enabled = !checking,
                        endActions = {
                            if (checking) {
                                InfiniteProgressIndicator(Modifier.size(20.dp))
                            }
                        },
                        onClick = when (updateState) {
                            is UpdateState.Available -> onShowUpdate
                            UpdateState.Checking -> ({})
                            else -> onCheckUpdate
                        },
                    )
                }
            }

            item(key = "links") {
                PreferenceGroup(title = "链接") {
                    ArrowPreference(
                        title = "源代码",
                        summary = "github.com/WindDrift/fuck-pangguai",
                        startAction = {
                            Icon(
                                imageVector = MiuixIcons.Link,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 12.dp),
                            )
                        },
                        onClick = { openUrl(GITHUB_REPO_URL) },
                    )
                    PreferenceDivider()
                    ArrowPreference(
                        title = "发布版本",
                        summary = "查看最新 Release 与更新日志",
                        startAction = {
                            Icon(
                                imageVector = MiuixIcons.Update,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 12.dp),
                            )
                        },
                        onClick = { openUrl(RELEASES_URL) },
                    )
                    PreferenceDivider()
                    ArrowPreference(
                        title = "问题反馈",
                        summary = "在 GitHub Issues 报告问题",
                        startAction = {
                            Icon(
                                imageVector = MiuixIcons.Report,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 12.dp),
                            )
                        },
                        onClick = { openUrl(ISSUES_URL) },
                    )
                }
            }

            item(key = "thanks") {
                PreferenceGroup(title = "特别致谢") {
                    ArrowPreference(
                        title = "REA",
                        summary = "胖乖生活软件的逆向分析工作由其支持",
                        startAction = {
                            Icon(
                                imageVector = MiuixIcons.Link,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 12.dp),
                            )
                        },
                        onClick = { openUrl(REA_URL) },
                    )
                }
            }

            item(key = "license") {
                PreferenceGroup(title = "开源许可") {
                    ArrowPreference(
                        title = "开源许可与致谢",
                        summary = "GPL-3.0 · 主要开源组件",
                        startAction = {
                            Icon(
                                imageVector = MiuixIcons.Info,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 12.dp),
                            )
                        },
                        onClick = { showLicense = true },
                    )
                }
            }

            item(key = "privacy") {
                PreferenceGroup(title = "隐私") {
                    ArrowPreference(
                        title = "免责声明与隐私",
                        summary = "非官方说明、账号数据与使用风险",
                        startAction = {
                            Icon(
                                imageVector = MiuixIcons.Lock,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 12.dp),
                            )
                        },
                        onClick = { showPrivacy = true },
                    )
                }
            }

            item(key = "footnote") {
                Text(
                    text = "非官方客户端，与胖乖生活、学校及设备运营方无关。",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                    style = MiuixTheme.textStyles.footnote1,
                    color = colors.onSurfaceSecondary,
                )
            }
        }

        HeroOverlay(
            appName = appName,
            versionLabel = versionLabel,
            dark = dark,
            heroHeight = heroHeight,
            alpha = heroAlpha,
            scale = heroScale,
            onVersionTap = {
                versionTaps += 1
                if (versionTaps >= 7) {
                    versionTaps = 0
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onOpenDebug()
                }
            },
            onVersionLongPress = {
                val copied = copyTextToClipboard(
                    context,
                    "版本信息",
                    "$appName $versionLabel",
                )
                snackbar.show(
                    if (copied) "版本信息已复制" else "复制失败",
                    isError = !copied,
                )
            },
            modifier = Modifier.align(Alignment.TopCenter),
        )

        AboutTopBar(
            progress = topBarProgress,
            dark = dark,
            onBack = onBack,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }

    LicenseSheet(
        show = showLicense,
        onDismiss = { showLicense = false },
    )
    PrivacySheet(
        show = showPrivacy,
        onDismiss = { showPrivacy = false },
    )
}

@Composable
private fun HeroOverlay(
    appName: String,
    versionLabel: String,
    dark: Boolean,
    heroHeight: Dp,
    alpha: Float,
    scale: Float,
    onVersionTap: () -> Unit,
    onVersionLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MiuixTheme.colorScheme
    val versionModifier = if (alpha > 0.1f) {
        Modifier.pointerInput(Unit) {
            detectTapGestures(
                onTap = { onVersionTap() },
                onLongPress = { onVersionLongPress() },
            )
        }
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(heroHeight)
            .statusBarsPadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .graphicsLayer {
                    this.alpha = alpha
                    scaleX = scale
                    scaleY = scale
                }
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(
                        if (dark) {
                            Color.White.copy(alpha = 0.14f)
                        } else {
                            Color.White.copy(alpha = 0.6f)
                        },
                    )
                    .border(
                        width = 1.dp,
                        color = if (dark) {
                            Color.White.copy(alpha = 0.18f)
                        } else {
                            Color.White.copy(alpha = 0.8f)
                        },
                        shape = RoundedCornerShape(28.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_water_drop),
                    contentDescription = null,
                    modifier = Modifier.size(60.dp),
                )
            }
            Text(
                text = appName,
                modifier = Modifier.padding(top = 16.dp),
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                color = colors.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "非官方校园饮水机客户端",
                modifier = Modifier.padding(top = 4.dp),
                style = MiuixTheme.textStyles.body2,
                color = colors.onSurfaceSecondary,
            )
            Text(
                text = versionLabel,
                modifier = Modifier
                    .padding(top = 12.dp)
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (dark) {
                            Color.White.copy(alpha = 0.12f)
                        } else {
                            Color.White.copy(alpha = 0.55f)
                        },
                    )
                    .then(versionModifier)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                style = MiuixTheme.textStyles.footnote1,
                color = colors.onSurfaceSecondary,
            )
        }
    }
}

@Composable
private fun AboutTopBar(
    progress: Float,
    dark: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MiuixTheme.colorScheme
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surface.copy(alpha = progress))
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(44.dp),
                backgroundColor = if (progress < 0.5f) {
                    if (dark) {
                        Color.Black.copy(alpha = 0.28f)
                    } else {
                        Color.White.copy(alpha = 0.55f)
                    }
                } else {
                    Color.Transparent
                },
                cornerRadius = 22.dp,
            ) {
                Icon(
                    imageVector = MiuixIcons.Back,
                    contentDescription = "返回",
                    modifier = Modifier.size(24.dp),
                    tint = colors.onSurface,
                )
            }
            Text(
                text = "关于",
                modifier = Modifier
                    .padding(start = 8.dp)
                    .weight(1f),
                style = MiuixTheme.textStyles.title3,
                color = colors.onSurface.copy(alpha = progress),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
