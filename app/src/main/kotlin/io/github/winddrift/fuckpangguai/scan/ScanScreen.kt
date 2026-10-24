package io.github.winddrift.fuckpangguai.scan

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.nfc.NfcAdapter
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.winddrift.fuckpangguai.device.ResolvedDevice
import io.github.winddrift.fuckpangguai.nfc.NfcReader
import io.github.winddrift.fuckpangguai.ui.animation.ColorSpringSpec
import io.github.winddrift.fuckpangguai.ui.animation.ContentFadeSpec
import io.github.winddrift.fuckpangguai.ui.components.AnimatedCheckMark
import io.github.winddrift.fuckpangguai.ui.components.AppSnackbarState
import io.github.winddrift.fuckpangguai.ui.components.LocalSnackbar
import io.github.winddrift.fuckpangguai.ui.components.ScanFrameOverlay
import io.github.winddrift.fuckpangguai.ui.components.TorchIcon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.Photos
import top.yukonga.miuix.kmp.icon.extended.Scan
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun ScanScreen(
    controller: ScanController,
    onDeviceReady: (ResolvedDevice) -> Unit,
    bottomContentPadding: Dp = 0.dp,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val snackbar = LocalSnackbar.current
    val colors = MiuixTheme.colorScheme
    val scope = rememberCoroutineScope()
    val state by controller.state.collectAsStateWithLifecycle()
    val cameraState = rememberQrCameraState()
    val torchOn by cameraState.torchOn.collectAsStateWithLifecycle()
    val flashAvailable by cameraState.flashAvailable.collectAsStateWithLifecycle()
    val hasNfc = remember(context) { NfcAdapter.getDefaultAdapter(context) != null }
    val torchTint by animateColorAsState(
        targetValue = if (torchOn) colors.primary else Color.White,
        animationSpec = ColorSpringSpec,
        label = "torchTint",
    )
    var resolvingSource by remember { mutableStateOf(ScanSource.Qr) }

    LaunchedEffect(state) {
        (state as? ScanState.Resolving)?.let { resolvingSource = it.source }
    }

    var manualInput by rememberSaveable { mutableStateOf(false) }
    var manualText by rememberSaveable { mutableStateOf("") }
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

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasCameraPermission =
                    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
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
                snackbar.show("相册图片里没有识别到二维码", isError = true)
            } else {
                controller.onQr(value)
            }
        }
    }

    NfcReader(onPayload = controller::onNfc)

    BackHandler(enabled = manualInput) { manualInput = false }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        if (hasCameraPermission) {
            QrCameraPreview(
                cameraState = cameraState,
                onQr = { raw ->
                    if (state.acceptsCameraScan()) controller.onQr(raw)
                },
                modifier = Modifier.fillMaxSize(),
            )
            ScanFrameOverlay(modifier = Modifier.fillMaxSize())
        }

        ScanHeader(modifier = Modifier.align(Alignment.TopCenter))

        if (!hasCameraPermission) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 32.dp, vertical = 96.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector = MiuixIcons.Info,
                    contentDescription = null,
                    modifier = Modifier.size(44.dp),
                    tint = Color.White.copy(alpha = 0.8f),
                )
                Text(
                    text = "需要相机权限才能扫码",
                    style = MiuixTheme.textStyles.title3,
                    color = Color.White,
                )
                Text(
                    text = "相册、手动输入与 NFC 仍然可用。",
                    style = MiuixTheme.textStyles.footnote1,
                    color = Color.White.copy(alpha = 0.7f),
                )
                Button(
                    onClick = {
                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    },
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) {
                    Text("授权相机")
                }
                TextButton(
                    text = "去系统设置",
                    onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.fromParts("package", context.packageName, null),
                                ),
                            )
                        }
                    },
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(
                    start = 24.dp,
                    top = 20.dp,
                    end = 24.dp,
                    bottom = 20.dp + bottomContentPadding,
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (hasNfc) {
                NfcHintPill()
            }
            Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                ScanToolButton(
                    icon = MiuixIcons.Photos,
                    contentDescription = "从相册选择",
                    onClick = { galleryLauncher.launch("image/*") },
                )
                if (hasCameraPermission && flashAvailable) {
                    ScanToolButton(
                        icon = TorchIcon,
                        contentDescription = if (torchOn) "关闭手电筒" else "打开手电筒",
                        tint = torchTint,
                        onClick = cameraState::toggleTorch,
                    )
                }
                ScanToolButton(
                    icon = MiuixIcons.Edit,
                    contentDescription = "手动输入",
                    onClick = { manualInput = true },
                )
            }
        }

        AnimatedVisibility(
            visible = state is ScanState.Resolving,
            modifier = Modifier.fillMaxSize(),
            enter = fadeIn(ContentFadeSpec) + scaleIn(ContentFadeSpec, initialScale = 0.94f),
            exit = fadeOut(ContentFadeSpec) + scaleOut(ContentFadeSpec, targetScale = 0.96f),
        ) {
            ResolvingOverlay(
                source = resolvingSource,
                modifier = Modifier.fillMaxSize(),
            )
        }

        ScanStateOverlays(
            state = state,
            onContinue = onDeviceReady,
            onConfirmBind = controller::confirmBind,
            onSkipBind = controller::skipBind,
            onReset = controller::reset,
            onOpenAlipay = { url -> openAlipay(context, url, snackbar) },
            modifier = Modifier.fillMaxSize(),
        )

        if (manualInput) {
            OverlayBottomSheet(
                show = true,
                title = "手动输入",
                onDismissRequest = { manualInput = false },
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    TextField(
                        value = manualText,
                        onValueChange = { manualText = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = "二维码内容或 NQT/SN/IMEI",
                        singleLine = true,
                    )
                    Button(
                        onClick = {
                            controller.onQr(manualText)
                            manualInput = false
                            manualText = ""
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = manualText.isNotBlank(),
                        colors = ButtonDefaults.buttonColorsPrimary(),
                    ) {
                        Text("识别")
                    }
                }
            }
        }
    }
}

@Composable
private fun ScanHeader(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(Color.Black.copy(alpha = 0.62f), Color.Transparent),
                ),
            )
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = "扫码接水",
                style = MiuixTheme.textStyles.title2,
                color = Color.White,
            )
            Text(
                text = "对准设备上的二维码，或碰一碰 NFC 标签",
                style = MiuixTheme.textStyles.footnote1,
                color = Color.White.copy(alpha = 0.72f),
            )
        }
    }
}

@Composable
private fun NfcHintPill() {
    Row(
        modifier = Modifier
            .background(
                color = Color.Black.copy(alpha = 0.38f),
                shape = RoundedCornerShape(50),
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = MiuixIcons.Scan,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = Color.White.copy(alpha = 0.85f),
        )
        Text(
            text = "碰一碰 NFC 标签也可识别",
            style = MiuixTheme.textStyles.footnote1,
            color = Color.White.copy(alpha = 0.85f),
        )
    }
}

@Composable
private fun ScanToolButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    tint: Color = Color.White,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(52.dp),
        backgroundColor = Color.Black.copy(alpha = 0.4f),
        cornerRadius = 26.dp,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(24.dp),
            tint = tint,
        )
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
        Card(
            modifier = Modifier.padding(horizontal = 32.dp),
            insideMargin = PaddingValues(0.dp),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 32.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AnimatedCheckMark()
                Text(
                    text = if (source == ScanSource.Qr) "扫码成功" else "已读取标签",
                    style = MiuixTheme.textStyles.title3,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    InfiniteProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = colors.primary,
                    )
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
}

private fun openAlipay(
    context: android.content.Context,
    url: String,
    snackbar: AppSnackbarState,
) {
    val ok = runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }.isSuccess
    if (!ok) {
        snackbar.show("无法打开支付宝", isError = true)
    }
}
