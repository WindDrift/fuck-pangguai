package io.github.winddrift.fuckpangguai.scan

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.view.HapticFeedbackConstants
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.winddrift.fuckpangguai.device.ResolvedDevice
import io.github.winddrift.fuckpangguai.nfc.NfcReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun ScanScreen(
    controller: ScanController,
    onDeviceReady: (ResolvedDevice) -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
    onLogout: () -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val state by controller.state.collectAsStateWithLifecycle()
    val colors = MiuixTheme.colorScheme
    val scope = rememberCoroutineScope()
    var manualInput by remember { mutableStateOf(false) }
    var manualText by remember { mutableStateOf("") }
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> hasCameraPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    LaunchedEffect(state) {
        if (state is ScanState.Resolving) {
            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val value = runCatching {
                withContext(Dispatchers.IO) { QrImageDecoder.decode(context, uri) }
            }.getOrNull()
            if (value.isNullOrBlank()) {
                controller.showMessage("相册图片里没有识别到二维码")
            } else {
                controller.onQr(value)
            }
        }
    }

    NfcReader(onPayload = controller::onNfc)

    Scaffold(
        topBar = { TopAppBar(title = "扫码接水") },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(colors.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                if (hasCameraPermission) {
                    QrCameraPreview(
                        onQr = { raw ->
                            // 已经有结果/正在解析时不再重复触发，避免同一张码反复请求；
                            // NeedsBind 例外：此时正需要扫设备码完成标签绑定。
                            if (state.acceptsCameraScan()) controller.onQr(raw)
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                    Text(
                        text = "将设备二维码放入取景框",
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(16.dp),
                        style = MiuixTheme.textStyles.footnote1,
                    )
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("需要相机权限才能扫码", style = MiuixTheme.textStyles.body1)
                        Button(
                            onClick = {
                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            },
                        ) {
                            Text("授权相机")
                        }
                    }
                }

                (state as? ScanState.Resolving)?.let { resolving ->
                    ResolvingOverlay(
                        source = resolving.source,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { galleryLauncher.launch("image/*") },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("从相册选择")
                }
                Button(
                    onClick = { manualInput = !manualInput },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (manualInput) "收起输入" else "手动输入")
                }
            }

            if (manualInput) {
                TextField(
                    value = manualText,
                    onValueChange = { manualText = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = "二维码内容或 NQT/SN/IMEI",
                )
                Button(
                    onClick = { controller.onQr(manualText) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = manualText.isNotBlank(),
                ) {
                    Text("识别")
                }
            }

            when (val current = state) {
                ScanState.Idle -> Unit
                is ScanState.Resolving -> Unit

                is ScanState.DeviceReady -> {
                    DeviceCard(
                        title = current.device.detail.name.ifBlank { "饮水机" },
                        lines = listOf(
                            "goodsId：${current.device.scan.id}",
                            "SN/IMEI：${mask(current.device.detail.imei)}",
                            "类别：${current.device.scan.categoryName}（${current.device.scan.categoryCode}）",
                            if (current.device.detail.isBluetooth == 1) {
                                "该设备支持 BLE；当前版本仅做探测，暂不解锁"
                            } else {
                                "在线设备，可进入下一步"
                            },
                            current.device.detail.deviceErrorCode?.let { "设备状态码：$it" },
                        ).filterNotNull(),
                        primaryText = "继续",
                        onPrimary = { onDeviceReady(current.device) },
                        secondaryText = "重新扫码",
                        onSecondary = controller::reset,
                    )
                }

                is ScanState.NeedsBind -> {
                    DeviceCard(
                        title = "读到新 NFC 标签",
                        lines = listOf(
                            "标签 ID：${current.tagId}",
                            "请扫描同一台设备上的二维码，确认后再绑定。",
                        ),
                        primaryText = "取消",
                        onPrimary = controller::reset,
                    )
                }

                is ScanState.ConfirmBind -> {
                    DeviceCard(
                        title = "绑定标签到设备？",
                        lines = listOf(
                            "标签 ID：${current.tagId}",
                            "设备：${current.device.detail.name.ifBlank { "饮水机" }}",
                            "SN：${current.device.sourceValue}",
                            "绑定后，碰一下该标签即可快速解析这台设备。",
                        ),
                        primaryText = "绑定并继续",
                        onPrimary = controller::confirmBind,
                        secondaryText = "跳过",
                        onSecondary = controller::skipBind,
                    )
                }

                is ScanState.Message -> {
                    DeviceCard(
                        title = "提示",
                        lines = listOf(current.text),
                        primaryText = "重新扫码",
                        onPrimary = controller::reset,
                        secondaryText = current.rawUrl?.let { "打开支付宝" },
                        onSecondary = current.rawUrl?.let { url ->
                            {
                                runCatching {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(url)),
                                    )
                                }
                            }
                        },
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onOpenHistory,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("记录")
                }
                Button(
                    onClick = onOpenSettings,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("设置")
                }
            }

            Spacer(Modifier.height(4.dp))
            Button(
                onClick = onLogout,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("退出登录")
            }
        }
    }
}

@Composable
private fun ResolvingOverlay(
    source: ScanSource,
    modifier: Modifier = Modifier,
) {
    val colors = MiuixTheme.colorScheme
    Box(
        modifier = modifier.background(Color.Black.copy(alpha = 0.55f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .background(colors.surface, RoundedCornerShape(24.dp))
                .padding(horizontal = 32.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SuccessMark()
            Text(
                text = if (source == ScanSource.Qr) "扫码成功" else "已读取标签",
                style = MiuixTheme.textStyles.title2,
                color = colors.onSurface,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LoadingRing(color = colors.primary)
                Text(
                    text = if (source == ScanSource.Qr) {
                        "正在获取设备信息…"
                    } else {
                        "正在解析设备…"
                    },
                    style = MiuixTheme.textStyles.body2,
                    color = colors.onSurfaceSecondary,
                )
            }
        }
    }
}

@Composable
private fun SuccessMark() {
    val colors = MiuixTheme.colorScheme
    val primary = colors.primary
    val onPrimary = colors.onPrimary
    Canvas(Modifier.size(56.dp)) {
        drawCircle(color = primary)
        val stroke = 4.dp.toPx()
        drawLine(
            color = onPrimary,
            start = Offset(size.width * 0.27f, size.height * 0.52f),
            end = Offset(size.width * 0.43f, size.height * 0.68f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = onPrimary,
            start = Offset(size.width * 0.43f, size.height * 0.68f),
            end = Offset(size.width * 0.74f, size.height * 0.32f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}

@Composable
private fun LoadingRing(color: Color) {
    val transition = rememberInfiniteTransition(label = "resolving")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 900, easing = LinearEasing)),
        label = "angle",
    )
    Canvas(Modifier.size(16.dp)) {
        drawArc(
            color = color,
            startAngle = angle,
            sweepAngle = 260f,
            useCenter = false,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
        )
    }
}

@Composable
private fun DeviceCard(
    title: String,
    lines: List<String>,
    primaryText: String,
    onPrimary: () -> Unit,
    secondaryText: String? = null,
    onSecondary: (() -> Unit)? = null,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MiuixTheme.textStyles.title2)
            lines.forEach { line ->
                Text(line, style = MiuixTheme.textStyles.body2)
            }
            Button(
                onClick = onPrimary,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(primaryText)
            }
            if (secondaryText != null && onSecondary != null) {
                Button(
                    onClick = onSecondary,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(secondaryText)
                }
            }
        }
    }
}

private fun mask(value: String): String = when {
    value.length <= 4 -> value
    else -> "${"*".repeat((value.length - 4).coerceAtMost(8))}${value.takeLast(4)}"
}
